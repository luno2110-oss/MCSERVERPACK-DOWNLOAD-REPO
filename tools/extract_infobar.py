"""Schneidet die Bausteine der Info-Leiste (/bossbar) aus dem nativen Referenzbild n_39 (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_infobar.py <ordner-mit-n_39.png> tools/assets/infobar
Die n_39.png ist eine Aufnahme aus dem Release "Neue-referenzen" (wie bei extract_playtime.py). Danach
tools/build_ui_pack.py (Kit für den Dialog und die Leiste oben im Pack).

Das Bild zeigt den Dialog: Kopf, „Design“ mit Standard / Leiste ausblenden und den vier Designs Wald, Kristall, Krone,
Frost (je eine Vorschau der Leiste: Geld und Job), darunter die Elemente mit Pfeilen, Symbol, Name, Text und Auge.

Ausgabe (Präfix ib_ im Kit):
  box_<name>.png       Kopf, Bereich, Design-Knopf an/aus, Knöpfe gold (Standard) und grau (ausblenden)
  art_<name>.png       Schild im Kopf, Symbole der Knöpfe, Pfeile, Augen, Symbole der Elemente
  art_cap_l_<d>.png    linkes und rechtes Ende der Leiste je Design (mit Blättern, Kristallen, Laternen, Eis)
  art_cap_r_<d>.png
  strip_body_<d>.png   Mittelstück der Leiste (18 hoch, jede Spalte gleich), als Füllung beliebiger Länge
  art_hud_<name>.png   Münzen, Juwel und Angel in der Leiste
  art.json             Lage der Grafiken; je Design: Zeilen über und unter dem Mittelstück, Breite der Enden
"""
import json
import os
import sys
from collections import Counter

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jewelshop import art  # noqa: E402
from extract_support import nine_clean  # noqa: E402

BG = (0x07, 0x1B, 0x28)
# Designs: Oberkante des Mittelstücks, Zeilen darüber / darunter (Blätter, Kristalle, Laternen, Eis), Breite der Enden;
# Geld-Leiste ab x = 80, Job-Leiste bis rechts (Ende der Job-Leiste = rechtes Ende)
DESIGNS = {
    "wald": {"top": 89, "up": 2, "down": 2, "cap": 10, "job_right": 367},
    "kristall": {"top": 116, "up": 2, "down": 1, "cap": 12, "job_right": 375},
    "krone": {"top": 143, "up": 2, "down": 1, "cap": 12, "job_right": 375},
    "frost": {"top": 170, "up": 2, "down": 4, "cap": 12, "job_right": 375},
}
BODY_H = 18
BAR_LEFT = 80
# Zeilen der Elemente (Name, Oberkante der Schrift) in der Liste
ROW0, ROW_DY = 218, 18
ELEMENTS = ("time", "coins", "jewels", "job", "place", "quest", "side")


def load(ref):
    return np.array(Image.open(os.path.join(ref, "n_39.png")).convert("RGB")).astype(int)


def transparent(r, bg=BG, tol=10):
    """Bild mit Alpha: Pixel in der Farbe des Bereichs werden durchsichtig."""
    out = np.zeros((r.shape[0], r.shape[1], 4), dtype=np.uint8)
    out[..., :3] = r
    out[..., 3] = np.where(np.abs(r - np.array(bg)).sum(axis=2) > tol, 255, 0)
    return Image.fromarray(out, "RGBA")


def main():
    ref, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    a = load(ref)
    boxes = {
        "head": (0, 0, 390, 52), "panel": (0, 54, 390, 142),
        "btn_on": (10, 89, 64, 20), "btn_off": (10, 116, 64, 20),
        "gold": (177, 62, 78, 20), "gray": (259, 62, 123, 20),
    }
    for name, (x, y, w, h) in boxes.items():
        nine_clean(a, x, y, w, h).save(os.path.join(out, "box_%s.png" % name))

    arts = {"sign": (8, 4, 46, 48), "refresh": (184, 65, 200, 79), "hide": (266, 65, 282, 79)}
    for i, el in enumerate(ELEMENTS):
        y = ROW0 + ROW_DY * i
        arts["el_" + el] = (42, y - 5, 60, y + 12)
    # Pfeile: oben in Zeile 0 gesperrt, unten in der letzten; Augen: Zeile 0 aus, Zeile 1 an
    last = ROW0 + ROW_DY * (len(ELEMENTS) - 1)
    arts.update({
        "up_off": (10, ROW0 - 3, 21, ROW0 + 10), "up_on": (10, ROW0 + ROW_DY - 3, 21, ROW0 + ROW_DY + 10),
        "down_on": (26, ROW0 - 3, 37, ROW0 + 10), "down_off": (26, last - 3, 37, last + 10),
        "eye_off": (364, ROW0 - 4, 384, ROW0 + 11), "eye_on": (364, ROW0 + ROW_DY - 4, 384, ROW0 + ROW_DY + 11),
    })
    pos = {}
    for name, rect in arts.items():
        img, xy = art(a, *rect)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height}

    # Designs: Enden mit Verzierung, Mittelstück als Füllung (257 Spalten: 256 Längen und die Endspalte)
    design = {}
    for name, d in DESIGNS.items():
        top, up, down, cap = d["top"], d["up"], d["down"], d["cap"]
        y0, y1 = top - up, top + BODY_H + down
        cols = [tuple(map(tuple, a[top:top + BODY_H, x])) for x in range(BAR_LEFT, BAR_LEFT + 100)]
        body = np.array(Counter(cols).most_common(1)[0][0])
        left = a[y0:y1, BAR_LEFT:BAR_LEFT + cap]
        right = a[y0:y1, d["job_right"] + 1 - cap:d["job_right"] + 1]
        transparent(left).save(os.path.join(out, "art_cap_l_%s.png" % name))
        transparent(right).save(os.path.join(out, "art_cap_r_%s.png" % name))
        strip = np.zeros((BODY_H, 257, 4), dtype=np.uint8)
        strip[..., :3] = body[:, None, :]
        strip[..., 3] = 255
        Image.fromarray(strip, "RGBA").save(os.path.join(out, "strip_body_%s.png" % name))
        design[name] = {"up": up, "down": down, "cap": cap}

    # Symbole in der Leiste (aus Kristall: dunkles Mittelstück, alles andere ist Symbol)
    k = DESIGNS["kristall"]
    kbody = np.array(Counter([tuple(map(tuple, a[k["top"]:k["top"] + BODY_H, x]))
                              for x in range(BAR_LEFT, BAR_LEFT + 100)]).most_common(1)[0][0])
    for name, (x0, x1) in {"coin": (93, 103), "gem": (147, 155), "rod": (195, 205)}.items():
        r = a[k["top"]:k["top"] + BODY_H, x0:x1]
        mask = np.abs(r - kbody[:, None, :]).sum(axis=2) > 10
        ys, xs = np.where(mask)
        img = np.zeros((BODY_H, x1 - x0, 4), dtype=np.uint8)
        img[..., :3] = r
        img[..., 3] = np.where(mask, 255, 0)
        crop = Image.fromarray(img, "RGBA").crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
        crop.save(os.path.join(out, "art_hud_%s.png" % name))
        pos["hud_" + name] = {"x": int(x0 + xs.min()), "y": int(ys.min()), "w": crop.width, "h": crop.height}
    with open(os.path.join(out, "art.json"), "w", encoding="utf-8") as f:
        json.dump({"art": pos, "design": design}, f, indent=1, sort_keys=True)
    print("fertig", len(boxes), "Kästen,", len(arts), "Grafiken,", len(DESIGNS), "Designs")


if __name__ == "__main__":
    main()
