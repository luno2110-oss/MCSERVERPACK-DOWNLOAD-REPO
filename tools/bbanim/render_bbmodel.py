"""Vorschau-Renderer für Blockbench-Animationen (.bbmodel, Format 5): spielt eine Animation wie Blockbench ab und
schreibt Einzelbilder (PNG) oder ein Video. Würfel flach schattiert mit Kontur, Knochen-Hierarchie mit Drehpunkten,
Keyframes linear oder catmullrom, Drehung in Blockbench-Reihenfolge (ZYX). Die .bbmodel speichert Werte wie three.js
(erst der Bedrock-Export spiegelt x und y), deshalb hier ohne Spiegelung.

Aufruf: python3 tools/bbanim/render_bbmodel.py <datei.bbmodel> <animation> <ordner> [fps]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

# Farben je Knochen (Name enthält ...), sonst grau
PALETTE = [("head", "E6B48C"), ("ball", "F4F4F4"), ("arm2", "E6B48C"), ("arm", "3A7BD5"), ("torso", "3A7BD5"),
           ("leg2", "26324F"), ("lag2", "26324F"), ("leg", "33467A")]


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def rot_matrix(rx, ry, rz):
    """Euler in Grad, Reihenfolge wie three.js 'ZYX' (R = Rz * Ry * Rx)."""
    x, y, z = map(math.radians, (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def mat4(R=None, t=(0, 0, 0)):
    M = np.eye(4)
    if R is not None:
        M[:3, :3] = R
    M[:3, 3] = t
    return M


class Model:
    def __init__(self, data):
        self.d = data
        self.groups = {g["uuid"]: g for g in data.get("groups", [])}
        self.elements = {e["uuid"]: e for e in data["elements"]}
        self.parent = {}
        self.elem_bone = {}

        def walk(node, parent):
            if isinstance(node, str):
                self.elem_bone[node] = parent
                return
            uid = node["uuid"]
            self.parent[uid] = parent
            for c in node.get("children", []):
                walk(c, uid)

        for n in data["outliner"]:
            walk(n, None)

    def anim(self, name):
        for a in self.d["animations"]:
            if a["name"] == name:
                return a
        raise KeyError(name)


def interp_channel(kfs, t):
    """kfs: Liste (time, [x,y,z], interpolation), sortiert."""
    if not kfs:
        return None
    if t <= kfs[0][0]:
        return np.array(kfs[0][1])
    if t >= kfs[-1][0]:
        return np.array(kfs[-1][1])
    for i in range(len(kfs) - 1):
        t0, v0, ip0 = kfs[i]
        t1, v1, ip1 = kfs[i + 1]
        if t0 <= t <= t1:
            a = (t - t0) / max(1e-9, t1 - t0)
            v0, v1 = np.array(v0), np.array(v1)
            if ip0 == "step":
                return v0
            if ip0 == "catmullrom" or ip1 == "catmullrom":
                vm = np.array(kfs[i - 1][1]) if i > 0 else v0
                vp = np.array(kfs[i + 2][1]) if i + 2 < len(kfs) else v1
                a2, a3 = a * a, a * a * a
                return 0.5 * ((2 * v0) + (-vm + v1) * a + (2 * vm - 5 * v0 + 4 * v1 - vp) * a2 + (-vm + 3 * v0 - 3 * v1 + vp) * a3)
            return v0 + (v1 - v0) * a
    return np.array(kfs[-1][1])


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return 0.0


def channels(anim):
    """{bone_uuid: {'rotation': [...], 'position': [...], 'scale': [...]}}"""
    out = {}
    for uid, an in (anim.get("animators") or {}).items():
        ch = {}
        for k in an.get("keyframes", []):
            if k["channel"] not in ("rotation", "position", "scale"):
                continue
            dp = k["data_points"][0]
            ch.setdefault(k["channel"], []).append((k["time"], [num(dp.get(c)) for c in "xyz"],
                                                    k.get("interpolation", "linear")))
        for c in ch:
            ch[c].sort(key=lambda x: x[0])
        out[uid] = ch
    return out


def bone_matrices(model, chans, t):
    cache = {}

    def world(uid):
        if uid is None:
            return np.eye(4)
        if uid in cache:
            return cache[uid]
        g = model.groups[uid]
        o = np.array(g.get("origin", [0, 0, 0]), float)
        rest = g.get("rotation", [0, 0, 0])
        ch = chans.get(uid, {})
        r = interp_channel(ch.get("rotation", []), t)
        p = interp_channel(ch.get("position", []), t)
        s = interp_channel(ch.get("scale", []), t)
        rx, ry, rz = rest[0], rest[1], rest[2]
        if r is not None:
            rx, ry, rz = rx + r[0], ry + r[1], rz + r[2]
        pos = np.zeros(3) if p is None else np.array(p, float)
        S = np.eye(4)
        if s is not None:
            S = np.diag([s[0] or 1, s[1] or 1, s[2] or 1, 1])
        M = world(model.parent.get(uid)) @ mat4(t=o + pos) @ mat4(rot_matrix(rx, ry, rz)) @ S @ mat4(t=-o)
        cache[uid] = M
        return M

    return {uid: world(uid) for uid in model.groups}


FACES = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]


def color_of(name):
    n = (name or "").lower()
    for key, c in PALETTE:
        if key in n:
            return hexrgb(c)
    return (150, 150, 150)


def render(model, chans, t, view, size=(480, 360), center=(0, 18, 0), dist=90, follow=None):
    W, H = size
    img = Image.new("RGB", size, (28, 34, 48))
    dr = ImageDraw.Draw(img)
    mats = bone_matrices(model, chans, t)
    yaw, pitch = view
    cy, sy, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    c = np.array(center, float)
    if follow is not None:
        c = c + follow
    eye = c + dist * np.array([sy * cp, sp, cy * cp])
    fwd = (c - eye) / np.linalg.norm(c - eye)
    right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    f = 520.0

    def proj(p):
        v = p - eye
        z = v @ fwd
        return (W / 2 + f * (v @ right) / z, H / 2 - f * (v @ up) / z, z)

    # Boden
    for gx in range(-64, 65, 8):
        a, b = proj(np.array([gx, 0, -64.0]) + [c[0], 0, c[2]]), proj(np.array([gx, 0, 64.0]) + [c[0], 0, c[2]])
        if a[2] > 1 and b[2] > 1:
            dr.line([a[:2], b[:2]], fill=(44, 58, 52))
        a, b = proj(np.array([-64, 0, gx]) + [c[0], 0, c[2]]), proj(np.array([64, 0, gx]) + [c[0], 0, c[2]])
        if a[2] > 1 and b[2] > 1:
            dr.line([a[:2], b[:2]], fill=(44, 58, 52))
    light = np.array([-0.4, 0.8, 0.45]); light /= np.linalg.norm(light)
    polys = []
    for uid, e in model.elements.items():
        bone = model.elem_bone.get(uid)
        if bone is None:
            continue
        M = mats[bone]
        fr, to = np.array(e["from"], float), np.array(e["to"], float)
        corners = np.array([[x, y, z, 1] for x in (fr[0], to[0]) for y in (fr[1], to[1]) for z in (fr[2], to[2])])
        W4 = (M @ corners.T).T[:, :3]
        base = color_of(model.groups[bone].get("name"))
        for fc in FACES:
            pts = W4[list(fc)]
            n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
            if np.linalg.norm(n) < 1e-9:
                continue
            n /= np.linalg.norm(n)
            ctr = pts.mean(axis=0)
            if (ctr - eye) @ n >= 0:
                n = -n
            shade = 0.55 + 0.45 * max(0, n @ light)
            pp = [proj(p) for p in pts]
            if min(q[2] for q in pp) < 1:
                continue
            col = tuple(int(min(255, ch * shade)) for ch in base)
            polys.append((np.mean([q[2] for q in pp]), [(q[0], q[1]) for q in pp], col))
    for _, pts, col in sorted(polys, key=lambda x: -x[0]):
        dr.polygon(pts, fill=col, outline=(14, 16, 24))
    return img


def main():
    path, name, out = sys.argv[1], sys.argv[2], sys.argv[3]
    fps = float(sys.argv[4]) if len(sys.argv) > 4 else 24
    model = Model(json.load(open(path)))
    a = model.anim(name)
    ch = channels(a)
    os.makedirs(out, exist_ok=True)
    n = int(math.ceil(a["length"] * fps)) + 1
    views = [(90, 12), (35, 18)]
    for i in range(n):
        t = i / fps
        frames = [render(model, ch, t, v) for v in views]
        W = sum(f.width for f in frames)
        im = Image.new("RGB", (W, frames[0].height))
        x = 0
        for fimg in frames:
            im.paste(fimg, (x, 0)); x += fimg.width
        ImageDraw.Draw(im).text((8, 8), "%s  t=%.2fs" % (name, t), fill=(240, 240, 240))
        im.save(os.path.join(out, "f%04d.png" % i))
    print(n, "Bilder")


if __name__ == "__main__":
    main()
