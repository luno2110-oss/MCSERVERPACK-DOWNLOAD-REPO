"""Schneidet die Bausteine des Regel-Dialogs (/regeln) aus den nativen Referenzbildern (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_rules.py <ordner-mit-n_40..n_52.png> tools/assets/rules
Die n_XX.png sind Aufnahmen aus dem Release "Neue-referenzen" in Zeitfolge, auf GUI-Pixel verkleinert (Ursprung
1332,201, Faktor 3, Pixel 3i+1), wie bei extract_playtime.py. Danach tools/build_ui_pack.py.

Bilder: 40 Übersicht (Kopf, „So spielen wir“, 12 Knöpfe), 41-52 die Paragraphen 1 bis 12.

Ausgabe (Präfix rg_ im Kit):
  box_<farbe>.png       15 x 15 (9-Slice) je Farbe: grün, rot, gold, braun, grau, lila, blau, türkis
  box_nav.png           dunkler Knopf unten („‹ Vorheriger“, „Nächster ›“)
  box_all_<farbe>.png   Knopf „Alle Regeln“ je Farbe
  art_book.png          Regelbuch im Kopf der Übersicht
  art_back.png          Zurück-Knopf im Kopf eines Paragraphen
  art_p<n>.png          Symbol je Paragraph (aus den Knöpfen der Übersicht)
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jewelshop import art, mode_color, nine_auto  # noqa: E402
from extract_playtime import pill  # noqa: E402

# Farbe je Paragraph (1 bis 12) und das Bild mit dem Paragraphen, aus dem der Kasten kommt
COLORS = ["green", "red", "gold", "brown", "red", "gray", "purple", "blue", "green", "teal", "red", "gold"]
# Höhe des ersten Inhaltskastens je Bild und Lage der Knöpfe darunter (10 px unter dem letzten Kasten)
FIRST_BOX_H = {41: 115, 42: 142, 43: 133, 44: 124, 46: 115, 47: 142, 48: 142, 50: 115}
NAV_Y = {41: 179, 42: 260, 46: 179, 47: 206, 48: 206, 50: 179, 43: 278, 44: 305}
GRID_X, GRID_Y, GRID_W, GRID_H, GRID_DY = (0, 198), 135, 192, 25, 27


def load(ref, i):
    return np.array(Image.open(os.path.join(ref, "n_%02d.png" % i)).convert("RGB")).astype(int)


def main():
    ref, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    im = {i: load(ref, i) for i in range(40, 53)}

    # Kästen je Farbe aus dem ersten Inhaltskasten des Paragraphen
    done = set()
    for n, color in enumerate(COLORS, start=1):
        i = 40 + n
        if color in done or i not in FIRST_BOX_H:
            continue
        nine_auto(im[i], 0, 54, 390, FIRST_BOX_H[i]).save(os.path.join(out, "box_%s.png" % color))
        done.add(color)
        # „Alle Regeln“ in der Mitte unter dem Kasten (82 x 20), Mitte in der Lücke zwischen den Wörtern
        nine_auto(im[i], 154, NAV_Y[i], 82, 20).save(os.path.join(out, "box_all_%s.png" % color))
    # dunkler Knopf: „‹ Schlussbestimmungen“ unter Paragraph 1
    nine_auto(im[41], 0, 179, 129, 20).save(os.path.join(out, "box_nav.png"))

    # Grafiken
    img, _ = art(im[40], 5, 8, 44, 48)
    img.save(os.path.join(out, "art_book.png"))
    pill(im[42], 9, 18, 22, 22).save(os.path.join(out, "art_back.png"))
    for n in range(1, 13):
        col, row = (n - 1) % 2, (n - 1) // 2
        x, y = GRID_X[col], GRID_Y + GRID_DY * row
        # Symbol links im Knopf in einem festen 8 x 8-Feld (x + 10, y + 8; im Kopf eines Paragraphen bei 37, 17)
        cell = im[40][y + 8:y + 16, x + 10:x + 18]
        bg = mode_color(im[40][y + 4:y + 21, x + 4:x + 9])
        rgba = np.zeros((8, 8, 4), dtype=np.uint8)
        rgba[..., :3] = cell
        rgba[..., 3] = np.where(np.abs(cell - bg).sum(axis=2) > 12, 255, 0)
        Image.fromarray(rgba, "RGBA").save(os.path.join(out, "art_p%d.png" % n))
    print("fertig", len(done) * 2 + 1, "Kästen, 14 Grafiken")


if __name__ == "__main__":
    main()
