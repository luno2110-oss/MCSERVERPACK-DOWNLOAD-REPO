#!/usr/bin/env python3
"""Blockbench-Modelle (.bbmodel) als Item-Modelle für den Pack (Möbel, Deko, Statuen).

Item-Modelle von Minecraft kennen je Element nur eine Drehung um eine Achse mit -45, -22,5, 0, 22,5 oder 45 Grad.
Blockbench erlaubt mehr (90/180 Grad, mehrere Achsen, Gruppen mit eigener Drehung, freie Winkel). Darum:

1. Gesamt-Transformation je Element bestimmen: Element-Drehung um seinen Ursprung, dann alle Gruppen (Knochen)
   von innen nach außen (Reihenfolge wie in Blockbench: Euler ZYX), dazu eine äußere Ausrichtung des ganzen
   Modells (Drehung um y in 90-Grad-Schritten, Verschiebung).
2. Die Drehung M zerlegen in Q (90-Grad-Drehung, wird in die Geometrie eingerechnet: Quader bleibt achsenparallel,
   Seiten und Textur-Lage werden umgeordnet) und r (eine Achse, erlaubter Winkel, bleibt als Element-Drehung).
   Gewählt wird das Paar mit der kleinsten Abweichung; was übrig bleibt (z. B. 50 statt 45 Grad), ist die einzige
   Näherung.
3. UVs von Blockbench-Pixeln (je Textur eigene UV-Größe) auf 0..16, Seiten-Drehung und Größenzuschlag (inflate)
   übernehmen. Texturen aus dem Modell (eingebettet) werden als eigene Texturen angemeldet.

Ergebnis: build_furniture.Model (Elemente, Texturen) wie die gezeichneten Möbel.
"""
import base64
import io
import itertools
import json
import math

import numpy as np
from PIL import Image

DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
        "down": (0, -1, 0)}
# Richtung, in der u bzw. v auf der Seite wächst (wie die Standard-UVs von Minecraft)
UAX = {"north": (-1, 0, 0), "south": (1, 0, 0), "east": (0, 0, -1), "west": (0, 0, 1), "up": (1, 0, 0),
       "down": (1, 0, 0)}
VAX = {"north": (0, -1, 0), "south": (0, -1, 0), "east": (0, -1, 0), "west": (0, -1, 0), "up": (0, 0, 1),
       "down": (0, 0, -1)}
ANGLES = (-45.0, -22.5, 22.5, 45.0)


def rot_axis(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def euler_zyx(r):
    """Blockbench-Drehung [x, y, z] in Grad (THREE.js, Reihenfolge ZYX): R = Rz · Ry · Rx."""
    return rot_axis("z", r[2]) @ rot_axis("y", r[1]) @ rot_axis("x", r[0])


def _q24():
    out = []
    for perm in itertools.permutations(range(3)):
        for signs in itertools.product((1, -1), repeat=3):
            m = np.zeros((3, 3))
            for i in range(3):
                m[i, perm[i]] = signs[i]
            if round(np.linalg.det(m)) == 1:
                out.append(m)
    return out


Q24 = _q24()
RESID = [(None, 0.0, np.eye(3))] + [(ax, a, rot_axis(ax, a)) for ax in "xyz" for a in ANGLES]


def decompose(m):
    """M ≈ r · Q: Q 90-Grad-Drehung, r eine Achse mit erlaubtem Winkel. Gibt (Q, Achse, Winkel, Fehler)."""
    best = None
    for q in Q24:
        for ax, a, r in RESID:
            err = float(np.linalg.norm(r @ q - m))
            if best is None or err < best[3] - 1e-9:
                best = (q, ax, a, err)
    return best


def _vec(v):
    return tuple(int(round(x)) for x in v)


def _face_name(d):
    for k, v in DIRS.items():
        if v == d:
            return k
    raise ValueError(d)


def _tex_axes(face, k):
    """Welt-Richtung von u und v auf einer Seite bei Textur-Drehung k (0, 90, 180, 270)."""
    U, V = np.array(UAX[face]), np.array(VAX[face])
    return {0: (U, V), 90: (V, -U), 180: (-U, -V), 270: (-V, U)}[k % 360]


def _new_rotation(face, k, q):
    """Seite nach Q und Textur-Drehung so, dass die Textur dem gedrehten Quader folgt."""
    a, b = _tex_axes(face, k)
    a2, b2 = q @ a, q @ b
    nf = _face_name(_vec(q @ np.array(DIRS[face])))
    for k2 in (0, 90, 180, 270):
        u, v = _tex_axes(nf, k2)
        if np.allclose(u, a2) and np.allclose(v, b2):
            return nf, k2
    raise ValueError("keine passende Textur-Drehung")


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def textures(bb):
    """[(Name, Bild, UV-Breite, UV-Höhe)] aus den eingebetteten Texturen."""
    res = bb.get("resolution", {"width": 16, "height": 16})
    # Java-Block-Format: alle UVs in der Projekt-Auflösung (eigene UV-Größe je Textur gibt es dort nicht)
    java = bb.get("meta", {}).get("model_format") == "java_block"
    out = []
    for t in bb.get("textures", []):
        src = t.get("source", "")
        if not src.startswith("data:"):
            raise ValueError("Textur %s ist nicht im Modell eingebettet" % t.get("name"))
        img = Image.open(io.BytesIO(base64.b64decode(src.split(",", 1)[1]))).convert("RGBA")
        uw = res["width"] if java else (t.get("uv_width") or res["width"])
        uh = res["height"] if java else (t.get("uv_height") or res["height"])
        out.append((t.get("name", "tex"), img, float(uw), float(uh)))
    return out


def _groups(bb):
    """Gruppe je UUID (Ursprung, Drehung, sichtbar) und Eltern-Kette je Element-UUID."""
    gdata = {g["uuid"]: g for g in bb.get("groups", []) if isinstance(g, dict)}
    parents = {}

    def walk(node, chain):
        if isinstance(node, str):
            parents[node] = list(chain)
            return
        g = dict(gdata.get(node.get("uuid"), {}))
        for k in ("origin", "rotation", "visibility", "export"):
            if k in node and k not in g:
                g[k] = node[k]
        for c in node.get("children", []):
            walk(c, chain + [g])

    for n in bb.get("outliner", []):
        walk(n, [])
    return parents


def elements(bb, outer_rot_y=0, outer_center=(8, 0, 8), offset=(0, 0, 0), tex_keys=None, max_err=0.45):
    """Java-Elemente (Liste von dicts) des Modells. tex_keys: Textur-Index -> Schlüssel im Modell ("#0" …).
    outer_rot_y: ganzes Modell um y drehen (Vielfache von 90), um outer_center; offset danach verschieben."""
    tx = textures(bb)
    parents = _groups(bb)
    g_outer = rot_axis("y", outer_rot_y)
    c0 = np.array(outer_center, dtype=float)
    off = np.array(offset, dtype=float)
    out, worst = [], 0.0
    for el in bb.get("elements", []):
        if el.get("type", "cube") != "cube" or el.get("export") is False or el.get("visibility") is False:
            continue
        chain = parents.get(el["uuid"], [])
        if any(g.get("visibility") is False or g.get("export") is False for g in chain):
            continue
        inf = float(el.get("inflate", 0))
        f = np.array(el["from"], dtype=float) - inf
        t = np.array(el["to"], dtype=float) + inf
        o_e = np.array(el.get("origin", [0, 0, 0]), dtype=float)

        # affine Gesamt-Transformation T(p) = M p + c
        def apply(p, chain=chain, el=el):
            p = np.array(p, dtype=float)
            p = o_e + euler_zyx(el.get("rotation", [0, 0, 0])) @ (p - o_e)
            for g in reversed(chain):
                go = np.array(g.get("origin", [0, 0, 0]), dtype=float)
                p = go + euler_zyx(g.get("rotation", [0, 0, 0])) @ (p - go)
            p = c0 + g_outer @ (p - c0)
            return p + off

        m = euler_zyx(el.get("rotation", [0, 0, 0]))
        for g in reversed(chain):
            m = euler_zyx(g.get("rotation", [0, 0, 0])) @ m
        m = g_outer @ m
        q, ax, ang, err = decompose(m)
        worst = max(worst, err)
        o = apply(o_e)
        corners = np.array([[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])])
        baked = (q @ (corners - o_e).T).T + o
        nf, nt = baked.min(axis=0), baked.max(axis=0)
        faces = {}
        for name, fc in el.get("faces", {}).items():
            ti = fc.get("texture")
            if ti is None or ti is False:
                continue
            ti = int(ti)
            _n, _img, uw, uh = tx[ti]
            uv = fc.get("uv", [0, 0, 0, 0])
            juv = [uv[0] * 16 / uw, uv[1] * 16 / uh, uv[2] * 16 / uw, uv[3] * 16 / uh]
            nfn, k2 = _new_rotation(name, int(fc.get("rotation", 0)), q)
            face = {"uv": [round(x, 4) for x in juv], "texture": tex_keys[ti] if tex_keys else "#%d" % ti}
            if k2:
                face["rotation"] = k2
            faces[nfn] = face
        if not faces:
            continue
        je = {"from": [round(float(x), 4) for x in nf], "to": [round(float(x), 4) for x in nt], "faces": faces}
        if ax is not None:
            je["rotation"] = {"axis": ax, "angle": ang, "origin": [round(float(x), 4) for x in o]}
        if el.get("shade") is False:
            je["shade"] = False
        out.append(je)
    if worst > max_err:
        raise ValueError("Drehungen weichen zu stark ab (%.2f)" % worst)
    return out, worst


def bounds(els):
    pts = np.array([e["from"] for e in els] + [e["to"] for e in els], dtype=float)
    return pts.min(axis=0), pts.max(axis=0)
