#!/usr/bin/env python3
"""Abfragen der Icon-Datenbank (icons.json aus build_icon_db.py) beim Zeichnen neuer Icons.

Im Code:
    import sys; sys.path.insert(0, "tools/icon_db"); import refpal
    refpal.ramp("gold", 5)          -> 5 Hex-Farben dunkel -> hell aus der häufigsten Referenz-Rampe
    refpal.ramps("gruen")           -> alle Referenz-Rampen der Familie (häufigste zuerst) mit Herkunft
    refpal.outline()                -> typische Konturfarben (fast schwarz, getönt)
    refpal.find("emerald")          -> Messwerte der Referenz-Icons, deren Name passt
    refpal.nearest_ramp("#4DA22F")  -> Referenz-Rampe, die diese Farbe enthält oder ihr am nächsten kommt

Auf der Kommandozeile:
    python3 tools/icon_db/refpal.py ramp gold 5
    python3 tools/icon_db/refpal.py ramps blau
    python3 tools/icon_db/refpal.py find crown
    python3 tools/icon_db/refpal.py near "#4DA22F"
Familien: rot, orange, braun, gold, gruen, tuerkis, blau, lila, rosa, grau.
"""
import json
import os
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
_DB = None


def db():
    global _DB
    if _DB is None:
        with open(os.path.join(HERE, "icons.json"), encoding="utf-8") as f:
            _DB = json.load(f)
    return _DB


def _rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ramps(family, min_len=4, kinds=("icon", "moebel", "pet")):
    """Alle Rampen einer Familie: [(Hex-Liste, Pixel, Icon)], häufigste und längste zuerst, ohne Doppelte."""
    out, seen = [], set()
    for key, a in db().items():
        if a["kind"] not in kinds:
            continue
        for f, r in a["ramps"].items():
            if f.split("_")[0] != family or len(r["hex"]) < min_len:
                continue
            sig = tuple(r["hex"])
            if sig in seen:
                continue
            seen.add(sig)
            out.append((r["hex"], sum(r["px"]), key))
    out.sort(key=lambda t: (-min(len(t[0]), 6), -t[1]))
    return out


def ramp(family, n=5, index=0):
    """n Farben (dunkel -> hell) aus der index-ten Referenz-Rampe, gleichmäßig ausgewählt."""
    rs = ramps(family, min_len=max(3, n))
    if not rs:
        rs = ramps(family, min_len=3)
    if not rs:
        raise KeyError("keine Rampe für " + family)
    hx = rs[min(index, len(rs) - 1)][0]
    if len(hx) <= n:
        return list(hx)
    return [hx[round(i * (len(hx) - 1) / (n - 1))] for i in range(n)]


def outline(k=6):
    """Häufigste Konturfarben der Referenz-Icons."""
    c = Counter()
    for a in db().values():
        if a["kind"] == "ui":
            continue
        for h, n in a["outline_colors"]:
            c[h] += n
    return [h for h, _ in c.most_common(k)]


def find(name):
    """Referenz-Icons, deren Schlüssel name enthält (Messwerte ohne die lange Palette)."""
    out = {}
    for key, a in db().items():
        if name.lower() in key.lower():
            b = dict(a)
            b["palette"] = b["palette"][-12:]
            out[key] = b
    return out


def nearest_ramp(hexcolor):
    """Rampe mit der Farbe, die hexcolor am nächsten ist: (Abstand, Hex-Liste, Icon)."""
    t = _rgb(hexcolor)
    best = None
    for key, a in db().items():
        if a["kind"] == "ui":
            continue
        for r in a["ramps"].values():
            if len(r["hex"]) < 3:
                continue
            for h in r["hex"]:
                d = sum((x - y) ** 2 for x, y in zip(_rgb(h), t))
                if best is None or d < best[0]:
                    best = (d, r["hex"], key)
    return best


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "ramp":
        print(" ".join(ramp(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 5)))
    elif cmd == "ramps":
        for hx, px, key in ramps(sys.argv[2])[:12]:
            print("%-60s %5d px  %s" % (" ".join(hx), px, key))
    elif cmd == "find":
        print(json.dumps(find(sys.argv[2]), ensure_ascii=False, indent=1)[:4000])
    elif cmd == "near":
        d, hx, key = nearest_ramp(sys.argv[2])
        print(" ".join(hx), " <- ", key)
    elif cmd == "outline":
        print(" ".join(outline()))
    else:
        print(__doc__)
