#!/usr/bin/env python3
"""Z-Fighting-Suche (v2.04): findet Flächen, die in derselben Ebene liegen, in dieselbe Richtung zeigen und sich
überlappen. Die Grafikkarte kann dort nicht entscheiden, welche vorne liegt, und das Bild flackert je nach Blickwinkel
zwischen beiden Texturen („Texturen mischen sich und flackern“). Bei drehenden Teilen (Roulette-Rotor) flackert es
sogar, wenn man stillsteht.

Rechnet wie r3d.py (gleiche Modelle, gleiche Transformationen), prüft aber nur Geometrie:
  * Ebene gleich: Normalen fast parallel (cos > 0.9995) und Abstand unter EPS Blöcken (Tiefenpuffer-Genauigkeit
    in 10–20 Blöcken Entfernung liegt bei rund 0,0002–0,0005)
  * überlappende Fläche über 2 mm² (konvexe Vierecke, Sutherland-Hodgman)
Doppelte Flächen (gleiche Lage, Textur und Abbildung) flackern unsichtbar und werden nur gezählt, nicht gemeldet.

Aufruf: KUBY_VANILLA=<vanilla-assets> zfight.py szene.json [eps=0.0006] [max=40]
Szene aus run.sh (z. B. tools/preview3d/run.sh roulette /tmp/r.json). Nur zum Entwickeln.
"""
import json
import math
import os
import sys
from collections import defaultdict

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r3d  # noqa: E402


def world_quads(e):
    t = e["type"]
    pos = np.array(e["pos"], dtype=float)
    base = r3d.tr(*pos) @ r3d.ry(math.radians(-e.get("yaw", 0))) @ r3d.rx(math.radians(e.get("pitch", 0))) @ r3d.transformation(e)
    if t == "ItemDisplay":
        it = e.get("item")
        if not it:
            return []
        key = it.get("model") or it.get("material")
        mid = r3d.item_model(key)
        m = base @ r3d.ry(math.pi) @ r3d.tr(-0.5, -0.5, -0.5)
        label = key
    elif t == "BlockDisplay":
        b = e.get("block")
        if not b:
            return []
        mid, bx, by = r3d.block_model(b["material"], b.get("state"))
        if mid is None:
            mid = "minecraft:block/" + b["material"].split(":")[1]
        m = base @ r3d.tr(0.5, 0.5, 0.5) @ r3d.ry(math.radians(-by)) @ r3d.rx(math.radians(-bx)) @ r3d.tr(-0.5, -0.5, -0.5)
        label = b["material"]
    else:
        return []
    out = []
    try:
        quads = r3d.model_quads(mid)
    except Exception:
        return []
    for k, (pts, uvs, tid) in enumerate(quads):
        p = r3d.apply(m, np.array(pts, dtype=float) / 16.0)
        n = np.cross(p[1] - p[0], p[3] - p[0])
        ln = np.linalg.norm(n)
        if ln < 1e-12:
            continue
        n /= ln
        out.append({"p": p, "n": n, "d": float(n @ p[0]), "tex": tid, "uv": tuple(map(tuple, uvs)), "label": label, "el": k})
    return out


def clip(poly, a, b):
    """Sutherland-Hodgman: Teil von poly links der Kante a->b (2D)."""
    out = []
    for i in range(len(poly)):
        cur, prev = poly[i], poly[i - 1]
        cin = (b[0] - a[0]) * (cur[1] - a[1]) - (b[1] - a[1]) * (cur[0] - a[0]) >= 0
        pin = (b[0] - a[0]) * (prev[1] - a[1]) - (b[1] - a[1]) * (prev[0] - a[0]) >= 0
        if cin != pin:
            dx, dy = cur[0] - prev[0], cur[1] - prev[1]
            ex, ey = b[0] - a[0], b[1] - a[1]
            den = ex * dy - ey * dx
            if abs(den) > 1e-15:
                s = (ex * (a[1] - prev[1]) - ey * (a[0] - prev[0])) / den
                out.append((prev[0] + s * dx, prev[1] + s * dy))
        if cin:
            out.append(cur)
    return out


def area(poly):
    return 0.5 * sum(poly[i - 1][0] * poly[i][1] - poly[i][0] * poly[i - 1][1] for i in range(len(poly)))


def overlap(q1, q2):
    n = q1["n"]
    u = np.cross(n, [0, 1, 0] if abs(n[1]) < 0.9 else [1, 0, 0])
    u /= np.linalg.norm(u)
    v = np.cross(n, u)
    p1 = [(float(p @ u), float(p @ v)) for p in q1["p"]]
    p2 = [(float(p @ u), float(p @ v)) for p in q2["p"]]
    if area(p1) < 0:
        p1 = p1[::-1]
    if area(p2) < 0:
        p2 = p2[::-1]
    poly = p2
    for i in range(len(p1)):
        poly = clip(poly, p1[i - 1], p1[i])
        if not poly:
            return 0.0
    return abs(area(poly))


def main():
    src = sys.argv[1]
    opts = dict(a.split("=", 1) for a in sys.argv[2:])
    eps = float(opts.get("eps", 0.0006))
    mx = int(opts.get("max", 40))
    ents = json.load(open(src, encoding="utf-8"))
    quads = []
    for i, e in enumerate(ents):
        for q in world_quads(e):
            q["ent"] = i
            q["role"] = ",".join(str(v) for v in (e.get("pdc") or {}).values())
            q["pos"] = e["pos"]
            quads.append(q)
    buckets = defaultdict(list)
    for qi, q in enumerate(quads):
        key = tuple(np.round(q["n"] * 200).astype(int))
        buckets[key].append(qi)
    hits, same = [], 0
    for key, idx in buckets.items():
        idx.sort(key=lambda i: quads[i]["d"])
        for a in range(len(idx)):
            qa = quads[idx[a]]
            for b in range(a + 1, len(idx)):
                qb = quads[idx[b]]
                if qb["d"] - qa["d"] > eps:
                    break
                if qa["n"] @ qb["n"] < 0.9995:
                    continue
                ar = overlap(qa, qb)
                if ar < 2e-6:
                    continue
                # unsichtbar nur, wenn beide Flächen gleich liegen und gleich abgebildet sind (doppelte Fläche)
                if qa["tex"] == qb["tex"] and qa["uv"] == qb["uv"] and np.abs(qa["p"] - qb["p"]).max() < 1e-4:
                    same += 1
                    continue
                hits.append((ar, qa, qb))
    hits.sort(key=lambda h: -h[0])
    print("%s: %d Flächen, %d Z-Fighting-Paare (dazu %d unsichtbare gleiche)" % (os.path.basename(src), len(quads), len(hits), same))
    if opts.get("group"):
        g = defaultdict(lambda: [0, 0.0, None])
        for ar, qa, qb in hits:
            a, b = (qa["label"] + "[" + qa["role"] + "]", qb["label"] + "[" + qb["role"] + "]")
            k = tuple(sorted((a, b)))
            g[k][0] += 1
            g[k][1] += ar
            if g[k][2] is None:
                g[k][2] = (qa["p"].mean(axis=0) + qb["p"].mean(axis=0)) / 2
        for k, (n, ar, c) in sorted(g.items(), key=lambda kv: -kv[1][1])[:mx]:
            print("  %4dx %8.1f cm²  %s <-> %s  z.B. bei (%.2f %.2f %.2f)" % (n, ar * 1e4, k[0], k[1], c[0], c[1], c[2]))
        return len(hits)
    for ar, qa, qb in hits[:mx]:
        c = (qa["p"].mean(axis=0) + qb["p"].mean(axis=0)) / 2
        print("  %7.1f cm²  %-28s #%-3d el%-3d  <->  %-28s #%-3d el%-3d  bei (%.2f %.2f %.2f) n=(%.2f %.2f %.2f) %s"
              % (ar * 1e4, qa["label"][-28:], qa["ent"], qa["el"], qb["label"][-28:], qb["ent"], qb["el"], c[0], c[1], c[2],
                 qa["n"][0], qa["n"][1], qa["n"][2], "gleiche Textur" if qa["tex"] == qb["tex"] else ""))
    return len(hits)


if __name__ == "__main__":
    main()
