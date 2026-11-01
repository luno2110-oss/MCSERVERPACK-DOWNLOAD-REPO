"""Schneidet die Bausteine der Spieler-Warps (/sw) aus den nativen Referenzbildern (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_pwarps.py <ordner-mit-n_69..n_78.png> tools/assets/pwarps
Die n_XX.png sind Aufnahmen aus dem Release "Neue-referenzen" (wie bei extract_playtime.py). Danach
tools/build_ui_pack.py.

Bilder: 69/70/71 Alle (Seiten 1 bis 3), 72 Mobfarm (leer), 73 Shops, 74 Bücher, 75 Spiele (leer), 76 Parkour (leer),
77 Meine Warps, 78 Suchen.

Ausgabe (Präfix pw_ im Kit):
  box_<name>.png   Kopf (groß, klein), Knöpfe Meine Warps / Suchen / Sortieren, Chips aus und je Kategorie an, Karten
                   (grün, lila; rot, orange, blau umgefärbt aus grün), leere Karte, Anpinnen, Teleport (grün, lila, ...),
                   Blättern, Plätze (frei, gesperrt), „Dir fehlen“
  art_<name>.png   Logo, Schriftzug, Münze, Symbole der Knöpfe und Chips, Laden, Bücherregal, Zeilen-Symbole, Stern,
                   Karte (leer), Portal (hell, gedämpft), Pfeile
  art.json         Lage der Grafiken
"""
import colorsys
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jewelshop import art  # noqa: E402
from extract_playtime import pill  # noqa: E402
from extract_support import clean_corners, nine_clean  # noqa: E402

CHIPS = {"alles": (9, 54), "mobfarm": (66, 66), "shops": (135, 55), "buecher": (193, 61), "spiele": (257, 54),
         "parkour": (314, 66)}
# Bild, auf dem die Kategorie gewählt ist
CHIP_ON = {"alles": 69, "mobfarm": 72, "shops": 73, "buecher": 74, "spiele": 75, "parkour": 76}
# Kartenfarben der Kategorien ohne Vorlage: aus der grünen Karte umgefärbt (Farbton in Grad, Sättigung x)
RECOLOR = {"red": (352, 1.0), "orange": (24, 1.0), "blue": (205, 1.0)}


def load(ref, i):
    return np.array(Image.open(os.path.join(ref, "n_%02d.png" % i)).convert("RGB")).astype(int)


def recolor(img, hue_deg, sat):
    """Färbt eine grüne Vorlage um: gesättigte Pixel bekommen den neuen Farbton, Helligkeit bleibt."""
    a = np.array(img).astype(float)
    out = a.copy()
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            r, g, b, al = a[y, x] / 255.0
            if al == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r, g, b)
            if s > 0.15 and abs(h * 360 - 130) < 40:
                r, g, b = colorsys.hls_to_rgb(hue_deg / 360.0, l, min(1.0, s * sat))
                out[y, x, :3] = (r * 255, g * 255, b * 255)
    return Image.fromarray(out.round().astype(np.uint8), "RGBA")


def profile_map(src, dst):
    """Farbtabelle von einem Kasten auf einen gleich geformten anderen: verglichen werden saubere Spalten und Zeilen am
    rechten Rand (dort steht weder Symbol noch Text), Pixel für Pixel an derselben Stelle."""
    table = {}
    h = min(src.shape[0], dst.shape[0])
    for k in range(2, 6):
        cs, cd = src.shape[1] - k, dst.shape[1] - k
        for y in range(h):
            table.setdefault(tuple(src[y, cs]), []).append(tuple(dst[y, cd]))
    for x in range(1, 7):
        for y in (0, 1, 2, src.shape[0] - 3, src.shape[0] - 2, src.shape[0] - 1):
            table.setdefault(tuple(src[y, src.shape[1] - x]), []).append(tuple(dst[y, dst.shape[1] - x]))
    out = {}
    for c, lst in table.items():
        out[c] = max(set(lst), key=lst.count)
    return out


def recolor_template(template, mapping):
    """Färbt eine 9-Slice-Vorlage nach der Farbtabelle um. Farben, die nicht in der Tabelle stehen (Mischpixel in den
    Ecken), bekommen die Farbe des nächsten Pixels mit bekannter Farbe (bei Gleichstand das weiter innen)."""
    a = np.array(template).astype(int)
    h, w = a.shape[:2]
    out = a.copy()
    known = [(y, x) for y in range(h) for x in range(w) if a[y, x, 3] and tuple(a[y, x, :3]) in mapping]
    cy, cx = (h - 1) / 2, (w - 1) / 2
    for y in range(h):
        for x in range(w):
            if a[y, x, 3] == 0:
                continue
            c = tuple(a[y, x, :3])
            if c not in mapping:
                ky, kx = min(known, key=lambda p: ((p[0] - y) ** 2 + (p[1] - x) ** 2,
                                                   (p[0] - cy) ** 2 + (p[1] - cx) ** 2))
                c = tuple(a[ky, kx, :3])
            out[y, x, :3] = mapping[c]
    return Image.fromarray(out.astype(np.uint8), "RGBA")


# Kleine Knöpfe (18 hoch) sind innen fast ganz mit Symbol und Text gefüllt; sie bekommen die Form des sauberen grauen
# Knopfs „Beliebt zuerst“ und ihre eigenen Farben
SHAPED = ("btn_mine", "btn_search", "chip_off", "pin", "page_off", "page_on")
# Knöpfe mit sauberer rechter Seite (Text mittig): ihre eigene rechte Seite, gespiegelt
OWN_RIGHT = ("tp_green", "tp_purple")


def mirrored_template(a, x, y, w, h, n=7):
    """9-Slice aus der rechten Seite (dort steht nichts), nach links gespiegelt; Mitte: die häufigste ganze Spalte."""
    box = a[y:y + h, x:x + w]
    cols = [tuple(map(tuple, box[:, c])) for c in range(n, w - n)]
    best = max(set(cols), key=cols.count)
    mx = n + cols.index(best)
    my = n
    xs = [w - 1 - i for i in range(n)][::-1]
    xs = xs[::-1] + [mx] + xs
    ys = list(range(n)) + [my] + list(range(h - n, h))
    out = np.zeros((2 * n + 1, 2 * n + 1, 4), dtype=np.uint8)
    for j, yy in enumerate(ys):
        for i, xx in enumerate(xs):
            out[j, i, :3] = box[yy, xx]
            out[j, i, 3] = 255
    return clean_corners(Image.fromarray(out, "RGBA"), n)


def art_on(a, x0, y0, x1, y1, box, tol=14):
    """Grafik auf einem Kasten: durchsichtig ist alles, was eine Farbe des leeren Kastens hat (Füllung, Glanzzeilen,
    Kante; gesammelt aus den Spalten rechts im Kasten box = (x, y, w, h), wo nichts steht)."""
    bx, by, bw, bh = box
    # ohne den dunklen Umriss (der steckt auch in den Grafiken): nur Zeilen und Spalten innerhalb der Kante
    bgs = {tuple(a[yy, xx]) for yy in range(by + 2, by + bh - 2) for xx in range(bx + bw - 7, bx + bw - 3)}
    bgs = {c for c in bgs if sum(c) > 40}
    bgs = np.array(list(bgs))
    r = a[y0:y1, x0:x1]
    d = np.abs(r[:, :, None, :] - bgs[None, None, :, :]).sum(axis=3).min(axis=2)
    alpha = np.where(d > tol, 255, 0).astype(np.uint8)
    ys, xs = np.where(alpha > 0)
    bx0, by0, bx1, by1 = xs.min(), ys.min(), xs.max() + 1, ys.max() + 1
    out = np.zeros((by1 - by0, bx1 - bx0, 4), dtype=np.uint8)
    out[..., :3] = r[by0:by1, bx0:bx1]
    out[..., 3] = alpha[by0:by1, bx0:bx1]
    return Image.fromarray(out, "RGBA"), (int(x0 + bx0), int(y0 + by0))


def main():
    ref, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    im = {i: load(ref, i) for i in range(69, 79)}
    boxes = {
        "head": (69, (0, 0, 390, 46)), "head_small": (77, (0, 0, 390, 38)),
        "btn_mine": (69, (50, 52, 99, 18)), "btn_search": (69, (153, 52, 70, 18)), "btn_sort": (69, (227, 52, 113, 18)),
        "chip_off": (69, (66, 79, 66, 18)),
        "card_green": (69, (0, 106, 390, 57)), "card_purple": (71, (0, 169, 390, 57)), "card_empty": (72, (0, 106, 390, 67)),
        "pin": (69, (233, 124, 80, 18)), "tp_green": (69, (317, 124, 65, 18)), "tp_purple": (71, (317, 187, 65, 18)),
        "page_off": (69, (162, 286, 18, 18)), "page_on": (69, (210, 286, 18, 18)),
        "slot_free": (77, (0, 43, 390, 57)), "slot_locked": (77, (0, 106, 390, 47)), "missing": (77, (266, 62, 116, 16)),
    }
    for name, (x, w) in CHIPS.items():
        boxes["chip_" + name] = (CHIP_ON[name], (x, 79, w, 18))
    gx, gy, gw, gh = boxes["btn_sort"][1]
    gray = im[69][gy:gy + gh, gx:gx + gw]
    gray_tpl = mirrored_template(im[69], gx, gy, gw, gh)
    for name, (i, (x, y, w, h)) in boxes.items():
        if name in ("btn_sort", "page_off", "missing"):
            # gleiche graue Form: die saubere Vorlage selbst
            gray_tpl.save(os.path.join(out, "box_%s.png" % name))
        elif name in OWN_RIGHT:
            mirrored_template(im[i], x, y, w, h).save(os.path.join(out, "box_%s.png" % name))
        elif name in SHAPED or name.startswith("chip_"):
            region = im[i][y:y + h, x:x + w]
            recolor_template(gray_tpl, profile_map(gray, region)).save(os.path.join(out, "box_%s.png" % name))
        else:
            nine_clean(im[i], x, y, w, h).save(os.path.join(out, "box_%s.png" % name))
    green = Image.open(os.path.join(out, "box_card_green.png")).convert("RGBA")
    # lila Karte: Form der grünen, Farben der lila Karte (deren Ecken sind zu stark mit dem Hintergrund vermischt)
    gr = im[69][106:106 + 57, 0:390]
    pu = im[71][169:169 + 57, 0:390]
    recolor_template(green, profile_map(gr, pu)).save(os.path.join(out, "box_card_purple.png"))
    tp = Image.open(os.path.join(out, "box_tp_green.png")).convert("RGBA")
    for name, (hue, sat) in RECOLOR.items():
        recolor(green, hue, sat).save(os.path.join(out, "box_card_%s.png" % name))
        recolor(tp, hue, sat).save(os.path.join(out, "box_tp_%s.png" % name))

    arts = {
        "logo": (69, (4, 3, 45, 44)), "title": (69, (46, 6, 184, 27)), "coin": (69, (305, 8, 326, 30)),
        "i_mine": (69, (53, 55, 70, 67)), "i_search": (69, (156, 55, 173, 67)), "i_sort": (69, (230, 55, 247, 67)),
        "stall": (69, (3, 108, 46, 160)), "shelf": (71, (3, 171, 46, 223)),
        "r_house": (69, (46, 124, 58, 135)), "r_pin": (69, (110, 187, 122, 199)), "r_people": (69, (46, 144, 58, 155)),
        "star": (69, (238, 126, 253, 140)), "map": (72, (4, 108, 62, 170)),
        "portal": (77, (4, 45, 40, 90)), "portal_off": (77, (4, 108, 40, 150)), "coin_small": (77, (288, 49, 308, 61)),
        "arrow_l": (69, (165, 289, 178, 301)), "arrow_r": (69, (213, 289, 226, 301)),
        "arrow_l_on": (70, (165, 289, 178, 301)), "arrow_r_off": (71, (213, 235, 226, 247)),
    }
    # Chip-Symbole aus einem Bild, auf dem der Chip nicht gewählt ist (innen gleichmäßig dunkel)
    for name, (x, w) in CHIPS.items():
        arts["c_" + name] = (72 if name == "alles" else 69, (x + 3, 82, x + 19, 94))
    pos = {}
    # Zurück-Knopf im kleinen Kopf (Meine Warps) wie in Support: grauer Knopf mit Pfeil
    pill(im[77], 8, 9, 22, 22).save(os.path.join(out, "art_back.png"))
    pos["back"] = {"x": 8, "y": 9, "w": 22, "h": 22, "bild": 77}
    # Symbole auf Knöpfen und Kästen: gegen alle Farben des leeren Kastens
    on_box = {"i_mine": (50, 52, 99, 18), "i_search": (153, 52, 70, 18), "i_sort": (227, 52, 113, 18),
              "arrow_l": (162, 286, 18, 18), "arrow_r": (210, 286, 18, 18),
              "arrow_l_on": (162, 286, 18, 18), "arrow_r_off": (210, 232, 18, 18), "logo": (0, 0, 390, 46),
              "title": (0, 0, 390, 46), "coin": (0, 0, 390, 46), "portal": (0, 43, 390, 57),
              "portal_off": (0, 106, 390, 47), "stall": (0, 106, 390, 57),
              "shelf": (0, 169, 390, 57), "map": (0, 106, 390, 67), "star": (233, 124, 80, 18)}
    for name, (i, rect) in arts.items():
        if name in on_box:
            img, xy = art_on(im[i], *rect, on_box[name])
        else:
            img, xy = art(im[i], *rect)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": i}
    with open(os.path.join(out, "art.json"), "w", encoding="utf-8") as f:
        json.dump(pos, f, indent=1, sort_keys=True)
    print("fertig", len(boxes) + 2 * len(RECOLOR), "Kästen,", len(arts), "Grafiken")


if __name__ == "__main__":
    main()
