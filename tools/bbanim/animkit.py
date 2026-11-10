"""Baukasten zum Polieren von Blockbench-Animationen (.bbmodel, Format 5).

Schlüsselposen werden in Bildern (24 fps) gesetzt, catmullrom zwischen den Posen. Der Wurzel-Knochen (full_body) wird
pro Bild gelöst: Standfuß bleibt an seinem Punkt am Boden (kein Rutschen, kein Einsinken), Flugphasen folgen einer
Wurfparabel. Treffer-Stopps (Hit-Stop) werden als Zeitversatz eingefügt, die Werte bleiben in den Haltebildern stehen.
Die Ballbahn wird am Ende an den tatsächlichen Fußpunkt im Kontaktbild gelegt.
"""
import copy
import json
import math
import uuid as uuidlib

import numpy as np

import render_bbmodel as R

FPS = 24
ALIAS = {"hipR": "right2", "hipL": "left2", "kneeR": "right_leg2", "kneeL": "left_lag2", "shR": "right", "shL": "left",
         "elR": "right_arm2", "elL": "left_arm2", "spine": "middle_body", "chest": "torso2", "head": "head",
         "legs": "legs", "root": "full_body", "ball": "ball"}
GRAVITY = 205.0  # Einheiten/s², 1 Block ~ 21 Einheiten bei diesem Modell


def sec(f):
    return round(f / FPS, 5)


class Clip:
    def __init__(self, model, name, frames):
        self.model, self.name, self.frames = model, name, frames
        self.uid = {g["name"]: u for u, g in model.groups.items()}
        self.keys = {}  # (bone, channel) -> {frame: [value, interp]}
        self.phases = []
        self.holds = []  # (frame, n)
        self.markers = []
        self.root_start = np.zeros(3)
        self.root_track = None

    # ---- Posen ----------------------------------------------------------------------------------------------------
    def key(self, bone, frame, channel, value, ip="catmullrom"):
        bone = ALIAS.get(bone, bone)
        self.keys.setdefault((bone, channel), {})[frame] = [tuple(float(v) for v in value), ip]

    def pose(self, frame, ip="catmullrom", **bones):
        for b, v in bones.items():
            if len(v) == 1:
                v = (v[0], 0, 0)
            self.key(b, frame, "rotation", v, ip)

    def pos(self, bone, frame, value, ip="catmullrom"):
        self.key(bone, frame, "position", value, ip)

    def scale(self, bone, frame, value, ip="linear"):
        self.key(bone, frame, "scale", value, ip)

    # ---- Auswertung -----------------------------------------------------------------------------------------------
    def chans(self, keys=None, timefn=sec, skip_root_pos=False):
        keys = self.keys if keys is None else keys
        out = {}
        for (bone, ch), fr in keys.items():
            if skip_root_pos and bone == "full_body" and ch == "position":
                continue
            lst = sorted((timefn(f), list(v), ip) for f, (v, ip) in fr.items())
            out.setdefault(self.uid[bone], {})[ch] = lst
        return out

    def value(self, bone, channel, frame):
        bone = ALIAS.get(bone, bone)
        fr = self.keys.get((bone, channel))
        if not fr:
            return None
        lst = sorted((sec(f), list(v), ip) for f, (v, ip) in fr.items())
        return R.interp_channel(lst, sec(frame))

    def _cubes(self, mats, bones):
        pts = []
        for eu, e in self.model.elements.items():
            b = self.model.elem_bone.get(eu)
            if b is None or self.model.groups[b]["name"] not in bones:
                continue
            fr, to = np.array(e["from"], float), np.array(e["to"], float)
            c = np.array([[x, y, z, 1] for x in (fr[0], to[0]) for y in (fr[1], to[1]) for z in (fr[2], to[2])])
            pts.append((mats[b] @ c.T).T[:, :3])
        return np.vstack(pts)

    BODY = ("right_leg", "right_leg2", "left_leg", "left_lag2", "torso1", "torso2", "head", "right_arm",
            "right_arm2", "left_arm", "left_arm2")

    def rel(self, frame):
        """Fußpunkte und tiefster Körperpunkt relativ zur Wurzel (Wurzel ohne Verschiebung)."""
        ch = self.chans(skip_root_pos=True)
        mats = R.bone_matrices(self.model, ch, sec(frame))
        out = {}
        for side, bone in (("R", "right_leg2"), ("L", "left_lag2")):
            p = self._cubes(mats, (bone,))
            bottom = p[np.argsort(p[:, 1])[:4]]
            out[side] = {"min": p[:, 1].min(), "xz": bottom.mean(axis=0), "pts": p}
        out["body"] = self._cubes(mats, self.BODY)[:, 1].min()
        return out

    # ---- Wurzel lösen ---------------------------------------------------------------------------------------------
    def phase(self, a, b, kind, **o):
        """kind: 'L'/'R' Standfuß, 'LR' beide (xz am Fuß o['anchor']), 'air' Flug (o['land'], o['drift'] oder
        Restgeschwindigkeit * o['carry']), 'body' tiefster Punkt am Boden mit o['drift'] linear."""
        self.phases.append((a, b, kind, o))

    def solve_root(self):
        n = self.frames
        rel = [self.rel(f) for f in range(n + 1)]
        root = [None] * (n + 1)
        cur = self.root_start.copy()

        def ground_y(f, mode):
            if mode == "L" or mode == "R":
                return -rel[f][mode]["min"]
            if mode == "LR":
                return -min(rel[f]["L"]["min"], rel[f]["R"]["min"])
            return -rel[f]["body"]

        for a, b, kind, o in sorted(self.phases, key=lambda p: p[0]):
            if root[a] is not None:
                cur = root[a].copy()
            if kind in ("L", "R", "LR", "body"):
                ymode = o.get("y", kind)
                foot = o.get("anchor", {"L": "L", "R": "R", "LR": "L"}.get(kind))
                anchor = cur + rel[a][foot]["xz"] if foot else None
                d = np.array(o.get("drift", (0, 0, 0)), float)
                for f in range(a, b + 1):
                    s = (f - a) / max(1, b - a)
                    p = anchor - rel[f][foot]["xz"] if foot else cur + d * s
                    root[f] = np.array([p[0], ground_y(f, ymode), p[2]])
            elif kind == "air":
                if "drift" in o:
                    d = np.array(o["drift"], float)
                else:
                    v = root[a] - root[a - 1] if a > 0 and root[a - 1] is not None else np.zeros(3)
                    d = v * (b - a) * o.get("carry", 1.0)
                land = o.get("land", "LR")
                ya = cur[1]
                yb = ground_y(b, land) + o.get("land_y", 0.0)
                T = (b - a) / FPS
                g = o.get("g", GRAVITY)
                v0 = (yb - ya + g * T * T / 2) / T
                for f in range(a, b + 1):
                    t = (f - a) / FPS
                    s = (f - a) / max(1, b - a)
                    root[f] = np.array([cur[0] + d[0] * s, ya + v0 * t - g * t * t / 2, cur[2] + d[2] * s])
            cur = root[b].copy()
        last = None
        for f in range(n + 1):
            if root[f] is None:
                root[f] = last.copy() if last is not None else self.root_start.copy()
            last = root[f]
        self.root_track = root
        return root

    def foot_world(self, frame, side):
        r = self.rel(frame)[side]
        return r["pts"] + self.root_track[frame]

    # ---- Ball ----------------------------------------------------------------------------------------------------
    def ball_rest(self):
        bu = self.uid["ball"]
        for eu, e in self.model.elements.items():
            if self.model.elem_bone.get(eu) == bu:
                return (np.array(e["from"], float) + np.array(e["to"], float)) / 2
        return np.zeros(3)

    def ball_at(self, frame, center, ip="linear"):
        self.pos("ball", frame, np.asarray(center, float) - self.ball_rest(), ip)

    # ---- Ausgabe -------------------------------------------------------------------------------------------------
    def remap(self, f):
        return f + sum(n for h, n in self.holds if f > h)

    def final_keys(self):
        keys = copy.deepcopy(self.keys)
        if self.root_track is not None:
            keys[("full_body", "position")] = {f: [tuple(p), "linear"] for f, p in enumerate(self.root_track)}
        out = {}
        for (bone, ch), fr in keys.items():
            lst = sorted((sec(f), list(v), ip) for f, (v, ip) in fr.items())
            nf = {}
            for f, (v, ip) in fr.items():
                nf[self.remap(f)] = [v, ip]
            for h, n in self.holds:
                if len(fr) < 2 and h not in fr:
                    continue
                v = tuple(R.interp_channel(lst, sec(h)))
                nf[h] = [v, "linear"]
                nf[h + n] = [v, "linear"]
            out[(bone, ch)] = nf
        return out

    def length_frames(self):
        return self.remap(self.frames + 1) - 1

    def to_json(self):
        anim = {"uuid": str(uuidlib.uuid4()), "name": self.name, "loop": "once", "override": False,
                "length": round(self.length_frames() / FPS, 5), "snapping": FPS, "selected": False,
                "group_name": "", "scope": 0, "anim_time_update": "", "blend_weight": "", "start_delay": "",
                "loop_delay": "", "animators": {}}
        if self.markers:
            anim["markers"] = [{"color": c, "time": sec(self.remap(f))} for f, c in self.markers]
        for (bone, ch), fr in sorted(self.final_keys().items()):
            u = self.uid[bone]
            an = anim["animators"].setdefault(u, {"name": bone, "type": "bone", "rotation_global": False,
                                                  "quaternion_interpolation": False, "keyframes": []})
            for f in sorted(fr):
                v, ip = fr[f]
                an["keyframes"].append({"channel": ch, "data_points": [{a: fmt(x) for a, x in zip("xyz", v)}],
                                        "uuid": str(uuidlib.uuid4()), "time": sec(f), "color": -1,
                                        "interpolation": ip})
        return anim


def fmt(x):
    s = ("%.3f" % x).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def ballistic(p0, v0, frames, g=GRAVITY):
    """Ballpunkte für Bilder 0..frames ab p0 mit Startgeschwindigkeit v0 (Einheiten/s)."""
    p0, v0 = np.asarray(p0, float), np.asarray(v0, float)
    out = []
    for f in range(frames + 1):
        t = f / FPS
        out.append(p0 + v0 * t + np.array([0, -g * t * t / 2, 0]))
    return out


def load(path):
    return json.load(open(path))
