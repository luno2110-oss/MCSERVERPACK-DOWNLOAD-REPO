"""Schneidet die Bausteine des Einstellungen-Menüs aus den nativen Referenzbildern (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_settings.py <ordner-mit-n_*.png> tools/assets/settings
Die n_*.png sind die Dialoge aus dem Release "Referenzen2" (4K-Aufnahmen 06.49.45 ... 06.50.20), auf GUI-Pixel
verkleinert (Ursprung 1332,201, Faktor 3). Danach tools/build_ui_pack.py laufen lassen.

Ausgabe nach tools/assets/settings/:
  box_<stil>.png      15 x 15: Ecken 7 x 7, Mitte 1 px (9-Slice), außerhalb der Rundung transparent
  pill_<farbe>_on.png, pill_off.png   40 x 14 Schalter (mit Text "AN"/"AUS")
  pill_on_<farbe>_blank.png, pill_off_blank.png   dieselben ohne Text (für "ON"/"OFF")
  small_<stil>.png    15 x 16: Mehrfach-Schalter (16 hoch), Ecken 7 breit
  icon_*.png          Icons mit transparentem Hintergrund
"""
import os
import sys

import numpy as np
from PIL import Image

REF = sys.argv[1] if __name__ == "__main__" else "."
OUT = sys.argv[2] if __name__ == "__main__" else "."


def load(name):
    return np.array(Image.open(os.path.join(REF, name)).convert("RGB")).astype(int)


def is_black(c):
    return int(c[0]) + int(c[1]) + int(c[2]) < 30


def nine(a, x0, y0, x1, y1, mx, my, n=7):
    """15 x 15 aus dem Rechteck [x0..x1] x [y0..y1] (inklusive): Ecken n x n, Mittelspalte mx, Mittelzeile my
    (relativ, an Stellen ohne Inhalt)."""
    w, h = x1 - x0 + 1, y1 - y0 + 1
    out = np.zeros((2 * n + 1, 2 * n + 1, 4), dtype=np.uint8)
    xs = list(range(n)) + [mx] + list(range(w - n, w))
    ys = list(range(n)) + [my] + list(range(h - n, h))
    for j, yy in enumerate(ys):
        for i, xx in enumerate(xs):
            out[j, i, :3] = a[y0 + yy, x0 + xx]
            out[j, i, 3] = 255
    # außerhalb der Rundung transparent: Maske aus der Ecke oben links (bis zum ersten schwarzen Pixel je Zeile),
    # gespiegelt auf alle vier Ecken (die Rundung ist symmetrisch)
    size = 2 * n + 1
    for j in range(n):
        for i in range(n):
            if is_black(out[j, i]):
                break
            for (yy, xx) in ((j, i), (j, size - 1 - i), (size - 1 - j, i), (size - 1 - j, size - 1 - i)):
                out[yy, xx, 3] = 0
    return Image.fromarray(out, "RGBA")


def small(a, x0, y0, x1, y1, n=7):
    """Mehrfach-Schalter: volle Höhe, Ecken n breit, 1 px Mitte."""
    w = x1 - x0 + 1
    h = y1 - y0 + 1
    xs = list(range(n)) + [n] + list(range(w - n, w))   # Spalte n: vor der Schrift
    out = np.zeros((h, 2 * n + 1, 4), dtype=np.uint8)
    for j in range(h):
        for i, xx in enumerate(xs):
            out[j, i, :3] = a[y0 + j, x0 + xx]
            out[j, i, 3] = 255
    for j in range(h):
        for i in range(n):
            if is_black(out[j, i]):
                break
            out[j, i, 3] = 0
            out[j, 2 * n - i, 3] = 0
    return Image.fromarray(out, "RGBA")


def sprite(a, x0, y0, x1, y1, bg, tol=10):
    r = a[y0:y1 + 1, x0:x1 + 1]
    out = np.zeros(r.shape[:2] + (4,), dtype=np.uint8)
    out[..., :3] = r
    diff = np.abs(r - np.array(bg)).sum(axis=2)
    out[..., 3] = np.where(diff > tol, 255, 0)
    return Image.fromarray(out, "RGBA")


def pill(a, x0, y0, bg):
    """Schalter 40 x 14 an (x0, y0); außerhalb der Pille transparent (Kachel-Füllung bg)."""
    im = sprite(a, x0, y0, x0 + 39, y0 + 13, bg, tol=4)
    return im


def blank(im, text_rgb, fill_rgb):
    """Text (dunkle oder graue Schrift) durch die Füllfarbe ersetzen."""
    arr = np.array(im)
    m = (np.abs(arr[..., :3].astype(int) - np.array(text_rgb)).sum(axis=2) < 6) & (arr[..., 3] > 0)
    arr[m, :3] = fill_rgb
    return Image.fromarray(arr, "RGBA")


def main():
    os.makedirs(OUT, exist_ok=True)
    SHOTS = {
        "blue": ("n_064945.png", 0),
        "orange": ("n_064949.png", 1),
        "gold": ("n_064952.png", 2),
        "teal": ("n_064954.png", 3),
        "violet": ("n_065020.png", 4),
    }
    FILL = {"blue": (10, 26, 40), "orange": (39, 27, 5), "gold": (39, 30, 10), "teal": (7, 32, 31), "violet": (21, 16, 40)}
    PILL_FILL = {"blue": (37, 100, 150), "orange": (151, 104, 19), "gold": (151, 119, 36), "teal": (28, 125, 113),
                 "violet": (82, 64, 150)}
    TEXT_DARK = (28, 18, 45)

    for name, (f, tab) in SHOTS.items():
        a = load(f)
        # Inhaltskasten (Umriss-Stil) und aktiver Reiter (gefüllter Stil)
        bottom = 230 if name != "violet" else 176
        nine(a, 0, 90, 389, bottom, 195, 52).save(os.path.join(OUT, "box_%s.png" % name))
        tx = tab * 79
        # Mittelspalte 7: gleich hinter der Ecke, dort steht nie Schrift (fette Beschriftungen reichen bis Spalte 8)
        nine(a, tx, 54, tx + 73, 87, 7, 17).save(os.path.join(OUT, "boxf_%s.png" % name))
        p = pill(a, 146, 122, FILL[name])
        p.save(os.path.join(OUT, "pill_on_%s.png" % name))
        blank(p, TEXT_DARK, PILL_FILL[name]).save(os.path.join(OUT, "pill_on_%s_blank.png" % name))

    a = load("n_064945.png")
    nine(a, 0, 0, 389, 50, 195, 25).save(os.path.join(OUT, "box_head.png"))
    nine(a, 79, 54, 152, 87, 7, 17).save(os.path.join(OUT, "box_gray.png"))
    nine(a, 0, 243, 191, 269, 30, 13).save(os.path.join(OUT, "box_button.png"))
    off = pill(a, 146, 149, (21, 22, 24))
    off.save(os.path.join(OUT, "pill_off.png"))
    # Text "AUS" ist grau: die hellsten Pixel innerhalb der Pille (ohne Knopf) ermitteln
    arr = np.array(off).astype(int)
    print("AUS-Farben:", sorted({tuple(arr[y, x, :3]) for y in range(3, 11) for x in range(14, 36)})[:12])

    # Mehrfach-Schalter (16 hoch): türkis gefüllt (Actionbar) und grau (Auto-TPA "Aus")
    t = load("n_064954.png")
    small(t, 313, 121, 376, 136).save(os.path.join(OUT, "small_teal.png"))
    o = load("n_064949.png")
    small(o, 313, 175, 376, 190).save(os.path.join(OUT, "small_gray.png"))

    # Icons
    a = load("n_064945.png")
    sprite(a, 10, 9, 40, 39, (21, 16, 40)).save(os.path.join(OUT, "icon_gear_big.png"))
    sprite(a, 277, 18, 290, 32, (21, 16, 40)).save(os.path.join(OUT, "icon_check.png"))
    GRAY = (21, 22, 24)
    # Reiter-Icons: inaktiv (grauer Reiter) und aktiv (gefüllter Reiter) vereinigt
    TAB_ICONS = {"chat": ((29, 57, 44, 71), "n_064949.png", "n_064945.png", "blue"),
                 "envelope": ((108, 58, 123, 70), "n_064945.png", "n_064949.png", "orange"),
                 "bell": ((188, 57, 201, 72), "n_064945.png", "n_064952.png", "gold"),
                 "eye": ((266, 59, 281, 69), "n_064945.png", "n_064954.png", "teal"),
                 "gear": ((345, 57, 360, 72), "n_064945.png", "n_065020.png", "violet")}
    for name, (box, inactive, active, col) in TAB_ICONS.items():
        # Randpixel sind halbtransparent gemischt: aus dem grauen Reiter geschnitten passt es in vier von fünf Reitern
        # genau (Vereinigung mit dem aktiven Reiter ergab mehr Abweichungen), im aktiven fehlen 2-3 Kantenpixel
        sprite(load(inactive), *box, GRAY).save(os.path.join(OUT, "icon_%s.png" % name))
    sprite(a, 10, 248, 25, 263, GRAY).save(os.path.join(OUT, "icon_gear_button.png"))
    sprite(a, 9, 97, 24, 111, (10, 26, 40)).save(os.path.join(OUT, "icon_chat_section.png"))
    print("fertig", sorted(os.listdir(OUT)))


if __name__ == "__main__":
    main()
