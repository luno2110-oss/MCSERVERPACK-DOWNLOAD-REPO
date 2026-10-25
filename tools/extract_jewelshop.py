"""Schneidet die Bausteine des Juwelenshops (SMP) aus den nativen Referenzbildern (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_jewelshop.py <ordner-mit-n_js1..5.png> tools/assets/jewelshop
Die n_js*.png sind die Dialoge aus dem Release "Referenzen2" (4K-Aufnahmen 06.51.51, 06.51.55, 06.51.59, 06.52.03,
06.52.08), auf GUI-Pixel verkleinert (Ursprung 1332,201, Faktor 3, Pixel 3i+1). Danach tools/build_ui_pack.py.

Ausgabe:
  box_<stil>.png      15 x 15 (9-Slice, Ecken 7 x 7), außerhalb der Rundung transparent
  pill_<stil>.png     24 x 14 Mengen-Knopf ohne Zahl
  art_<name>.png      Grafiken mit transparentem Hintergrund (auf die sichtbaren Pixel zugeschnitten)
  art.json            linke obere Ecke jeder Grafik im Referenzbild (zum Nachmessen)
"""
import json
import os
import sys
from collections import Counter

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_settings import is_black  # noqa: E402


def load(ref, name):
    return np.array(Image.open(os.path.join(ref, name + ".png")).convert("RGB")).astype(int)


def mode_color(r):
    return np.array(Counter(map(tuple, r.reshape(-1, 3))).most_common(1)[0][0])


def nine_auto(a, x, y, w, h, n=7, mirror_bottom=False):
    """9-Slice eines Kastens (x, y, w, h): Mittelspalte und -zeile automatisch dort, wo innen nur Füllung ist.
    mirror_bottom: untere Zeilen = obere gespiegelt (wenn innen etwas bis nah an die Unterkante reicht, z. B. die
    Knöpfe in den Karten der Übersicht)."""
    box = a[y:y + h, x:x + w]
    fill = mode_color(box[n:h - n, n:w - n])

    def score_col(c):
        return int((np.abs(box[n:h - n, c] - fill).sum(axis=1) < 6).sum())

    def score_row(r):
        return int((np.abs(box[r, n:w - n] - fill).sum(axis=1) < 6).sum())

    # Spalte: innen nur Füllung, Rand oben/unten wie die Mehrheit der Spalten (gerade Kante)
    cols = list(range(n, w - n))
    edge = Counter(tuple(map(tuple, box[:n, c])) + tuple(map(tuple, box[h - n:, c])) for c in cols).most_common(1)[0][0]
    mx = max(cols, key=lambda c: (score_col(c), tuple(map(tuple, box[:n, c])) + tuple(map(tuple, box[h - n:, c])) == edge))
    rows = list(range(n, h - n)) or [n]
    edge_r = Counter(tuple(map(tuple, box[r, :n])) + tuple(map(tuple, box[r, w - n:])) for r in rows).most_common(1)[0][0]
    my = max(rows, key=lambda r: (score_row(r), tuple(map(tuple, box[r, :n])) + tuple(map(tuple, box[r, w - n:])) == edge_r))
    xs = list(range(n)) + [mx] + list(range(w - n, w))
    ys = list(range(n)) + [my] + list(range(h - n, h))
    size = 2 * n + 1
    out = np.zeros((size, size, 4), dtype=np.uint8)

    for j, yy in enumerate(ys):
        for i, xx in enumerate(xs):
            out[j, i, :3] = box[yy, xx]
            out[j, i, 3] = 255
    if mirror_bottom:
        for j in range(n):
            out[size - 1 - j] = out[j]
    for j in range(n):
        for i in range(n):
            if is_black(out[j, i]):
                break
            for (yy, xx) in ((j, i), (j, size - 1 - i), (size - 1 - j, i), (size - 1 - j, size - 1 - i)):
                out[yy, xx, 3] = 0
    return Image.fromarray(out, "RGBA")


def art(a, x0, y0, x1, y1, tol=12):
    """Grafik im Rechteck [x0, x1) x [y0, y1): Hintergrund (häufigste Farbe) wird transparent, zugeschnitten.
    Gibt (Bild, (x, y)) zurück."""
    r = a[y0:y1, x0:x1]
    bg = mode_color(r)
    alpha = np.where(np.abs(r - bg).sum(axis=2) > tol, 255, 0).astype(np.uint8)
    ys, xs = np.where(alpha > 0)
    bx0, by0, bx1, by1 = xs.min(), ys.min(), xs.max() + 1, ys.max() + 1
    out = np.zeros((by1 - by0, bx1 - bx0, 4), dtype=np.uint8)
    out[..., :3] = r[by0:by1, bx0:bx1]
    out[..., 3] = alpha[by0:by1, bx0:bx1]
    return Image.fromarray(out, "RGBA"), (int(x0 + bx0), int(y0 + by0))


def pill(a, x, y, text_colors, w=24, h=14):
    """Mengen-Knopf ohne Zahl: Schrift (und Schatten) durch die Farbe derselben Zeile am linken Innenrand ersetzen;
    außerhalb der Rundung transparent."""
    r = a[y:y + h, x:x + w].copy()
    for yy in range(h):
        ref = r[yy, 5].copy()
        for xx in range(4, w - 4):
            if any(np.abs(r[yy, xx] - np.array(c)).sum() < 12 for c in text_colors):
                r[yy, xx] = ref
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[..., :3] = r
    out[..., 3] = 255
    # außen: Pixel in der Farbe des Hintergrunds (Ecke oben links), von beiden Rändern her bis zum ersten anderen
    outside = r[0, 0]
    for j in range(h):
        for i in range(w // 2):
            if np.abs(r[j, i] - outside).sum() > 12:
                break
            out[j, i, 3] = 0
        for i in range(w - 1, w // 2, -1):
            if np.abs(r[j, i] - outside).sum() > 12:
                break
            out[j, i, 3] = 0
    return Image.fromarray(out, "RGBA")


def hexrgb(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def main():
    ref, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    js = {i: load(ref, "n_js%d" % i) for i in range(1, 6)}
    boxes = {
        "gold": (1, (0, 50, 192, 80)), "blue": (1, (198, 50, 192, 80)), "red": (1, (0, 140, 192, 80)),
        "violet": (1, (198, 140, 192, 80)), "green": (1, (0, 234, 192, 30)), "store": (1, (198, 234, 192, 30)),
        "head": (3, (0, 0, 390, 46)),
        "vip": (2, (0, 49, 390, 50)), "platin": (2, (0, 103, 390, 50)), "devil": (2, (0, 157, 390, 50)),
        "legend": (2, (0, 211, 390, 50)), "gray": (2, (270, 72, 110, 18)), "pass": (3, (0, 49, 390, 50)),
        "legendary": (4, (0, 49, 390, 50)), "cyborg": (4, (0, 157, 390, 50)),
    }
    cards = {"gold", "blue", "red", "violet"}   # Knopf 4 px über der Unterkante
    for name, (i, (x, y, w, h)) in boxes.items():
        nine_auto(js[i], x, y, w, h, mirror_bottom=name in cards).save(os.path.join(out, "box_%s.png" % name))
    dark, light, shadow = hexrgb("201400"), hexrgb("D1D2D5"), hexrgb("343435")
    pill(js[4], 84, 81, [dark]).save(os.path.join(out, "pill_legendary.png"))
    pill(js[4], 84, 135, [hexrgb("0D2728"), hexrgb("0A2020"), dark]).save(os.path.join(out, "pill_auran.png"))
    pill(js[4], 84, 189, [hexrgb("002722"), hexrgb("00201B"), dark]).save(os.path.join(out, "pill_cyborg.png"))
    pill(js[4], 111, 81, [light, shadow]).save(os.path.join(out, "pill_off.png"))
    arts = {
        "title": (1, (44, 5, 180, 27)), "emerald_big": (1, (4, 4, 44, 43)), "emerald": (1, (302, 8, 326, 30)),
        "crown": (1, (4, 54, 60, 106)), "ticket": (1, (202, 54, 258, 106)), "crate": (1, (4, 144, 60, 196)),
        "chest": (1, (202, 144, 258, 196)), "gift": (1, (4, 237, 29, 261)), "cart": (1, (202, 237, 227, 261)),
        "back": (2, (6, 6, 36, 36)),
        "rank_vip": (2, (5, 54, 47, 94)), "rank_platin": (2, (5, 108, 47, 148)), "rank_devil": (2, (5, 162, 47, 202)),
        "rank_legend": (2, (5, 216, 47, 256)), "emerald_price": (2, (298, 54, 318, 72)),
        "pass": (3, (5, 54, 47, 94)),
        "key_legendary": (4, (5, 54, 47, 94)), "key_auran": (4, (5, 108, 47, 148)), "key_cyborg": (4, (5, 162, 47, 202)),
        "pack_starter": (5, (6, 56, 120, 103)), "pack_profi": (5, (138, 56, 252, 103)), "pack_ultimate": (5, (270, 56, 384, 103)),
    }
    pos = {}
    for name, (i, rect) in arts.items():
        img, xy = art(js[i], *rect)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": i}
    with open(os.path.join(out, "art.json"), "w", encoding="utf-8") as f:
        json.dump(pos, f, indent=1, sort_keys=True)
    print("fertig", len(boxes), "Kästen,", len(arts), "Grafiken")


if __name__ == "__main__":
    main()
