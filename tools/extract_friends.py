"""Schneidet die Bausteine des Freunde-Dialogs (/friends) aus den nativen Referenzbildern (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_friends.py <ordner-mit-n_57..n_60.png> tools/assets/friends
Die n_XX.png sind Aufnahmen aus dem Release "Neue-referenzen" (wie bei extract_playtime.py). Danach
tools/build_ui_pack.py.

Bilder: 57 Freunde (leer), 58 Hinzufügen (Eingabefeld darunter), 59 Anfragen/Erhalten (leer), 60 Anfragen/Gesendet.

Ausgabe (Präfix fr_ im Kit):
  box_head, box_tab_on, box_tab_off (Reiter), box_sub_on, box_sub_off (Unter-Reiter), box_card (grauer Kasten),
  box_btn (türkiser Knopf)
  art_logo (Freunde-Paar im Kopf), art_people (Personen), art_mail (Brief), art_plus (Plus), art_add (Kopf mit Plus),
  art_letter (Brief mit Herz), art_plane (Papierflieger); Lage in art.json
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jewelshop import art, nine_auto  # noqa: E402


def load(ref, i):
    return np.array(Image.open(os.path.join(ref, "n_%02d.png" % i)).convert("RGB")).astype(int)


def main():
    ref, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    im = {i: load(ref, i) for i in range(57, 61)}
    boxes = {
        "head": (57, (0, 0, 390, 51)),
        "tab_on": (57, (0, 55, 126, 22)), "tab_off": (57, (132, 55, 126, 22)),
        "sub_on": (59, (0, 81, 136, 20)), "sub_off": (59, (142, 81, 136, 20)),
        "card": (57, (0, 90, 390, 78)), "btn": (57, (62, 135, 136, 20)),
    }
    for name, (i, (x, y, w, h)) in boxes.items():
        nine_auto(im[i], x, y, w, h).save(os.path.join(out, "box_%s.png" % name))
    arts = {
        "logo": (57, (5, 5, 46, 46)), "people": (57, (286, 8, 308, 30)),
        # Reiter-Symbole aus dunklen (nicht gewählten) Reitern
        "people_tab": (58, (6, 58, 26, 74)), "mail": (57, (138, 58, 160, 74)), "plus": (57, (270, 58, 290, 74)),
        "add": (57, (8, 96, 56, 162)), "letter": (59, (8, 114, 56, 156)), "plane": (58, (6, 84, 34, 111)),
    }
    pos = {}
    for name, (i, rect) in arts.items():
        img, xy = art(im[i], *rect)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": i}
    with open(os.path.join(out, "art.json"), "w", encoding="utf-8") as f:
        json.dump(pos, f, indent=1, sort_keys=True)
    print("fertig", len(boxes), "Kästen,", len(arts), "Grafiken")


if __name__ == "__main__":
    main()
