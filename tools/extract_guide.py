"""Schneidet die Bausteine des Guides (SMP-Hilfe) aus dem nativen Referenzbild (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_guide.py <ordner-mit-n_guide.png> tools/assets/guide
n_guide.png ist der Dialog aus dem Release "Referenzen2" (4K-Aufnahme 06.50.40, Seite 1/12), auf GUI-Pixel verkleinert
(Ursprung 1332,201, Faktor 3, Pixel 3i+1). Danach tools/build_ui_pack.py.

Ausgabe wie bei extract_jewelshop.py: box_<stil>.png (9-Slice), art_<name>.png (Grafiken), art.json (Lage).
Icons der Navigation: art_nav<NN>.png in der Reihenfolge der Referenz (Übersicht … Regeln & Support).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jewelshop import art, load, nine_auto  # noqa: E402

NAV_Y, NAV_STEP, NAV_H, NAV_W = 58, 18, 17, 127


def main():
    ref, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    g = load(ref, "n_guide")
    boxes = {
        "head": (0, 0, 390, 51), "content": (134, 54, 256, 222),
        "nav_on": (0, NAV_Y, NAV_W, NAV_H), "nav": (0, NAV_Y + NAV_STEP, NAV_W, NAV_H),
    }
    for name, (x, y, w, h) in boxes.items():
        nine_auto(g, x, y, w, h).save(os.path.join(out, "box_%s.png" % name))
    arts = {"book": (6, 6, 46, 47)}
    for i in range(12):
        y = NAV_Y + NAV_STEP * i
        arts["nav%02d" % i] = (5, y + 4, 17, y + NAV_H - 3)
    pos = {}
    for name, rect in arts.items():
        img, xy = art(g, *rect)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height}
    with open(os.path.join(out, "art.json"), "w", encoding="utf-8") as f:
        json.dump(pos, f, indent=1, sort_keys=True)
    print("fertig", len(boxes), "Kästen,", len(arts), "Grafiken")


if __name__ == "__main__":
    main()
