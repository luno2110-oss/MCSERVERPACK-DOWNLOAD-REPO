"""Schneidet die Bausteine des Survival-Juwelenshops aus den nativen Referenzbildern (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_jewelshop_survival.py <ordner-mit-n_00..n_33.png> tools/assets/jewelshop_survival
Die n_XX.png sind die 34 Aufnahmen aus dem Release "Referenz2" (04.38.11 bis 04.40.47) in Zeitfolge, auf GUI-Pixel
verkleinert (Ursprung 1332,201, Faktor 3, Pixel 3i+1). Danach tools/build_ui_pack.py.

Bilder: 00 Übersicht, 01 Farmpass, 02 Crates, 03-25 Möbel (7 Reiter, je Seite 8 Karten), 26-32 Pets, 33 Tokens.

Ausgabe (Präfix jss_ im Kit):
  box_<stil>.png      15 x 15 (9-Slice, Ecken 7 x 7)
  pill_<stil>.png     24 x 14 Mengen-Knopf ohne Zahl
  art_<name>.png      Grafiken mit transparentem Hintergrund (zugeschnitten); Lage in art.json
  art_rank_<rang>     Kronen der Survival-Ränge (Silberkrone aus tools/assets/jewelshop, umgefärbt)
  f_<id>.png, p_<id>  Möbel- und Pet-Bilder 30 x 30 (feste Stelle in der Karte: x + 32, y + 8)
"""
import colorsys
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jewelshop import art, hexrgb, mode_color, nine_auto, pill  # noqa: E402
import jewelshop_survival_data as data  # noqa: E402

CARD_W, CARD_H, CARD_DX, CARD_DY, CARD_Y = 94, 97, 98, 99, 64
SLOT_X, SLOT_Y, SLOT = 32, 8, 30


def load(ref, i):
    return np.array(Image.open(os.path.join(ref, "n_%02d.png" % i)).convert("RGB")).astype(int)


def slot(a, x0, y0, tol=10):
    """Bild im festen 30-px-Feld einer Karte: Füllfarbe der Karte wird transparent."""
    r = a[y0 + SLOT_Y:y0 + SLOT_Y + SLOT, x0 + SLOT_X:x0 + SLOT_X + SLOT]
    bg = a[y0 + 20, x0 + 6]          # Füllung links neben dem Feld
    alpha = np.where(np.abs(r - bg).sum(axis=2) > tol, 255, 0).astype(np.uint8)
    out = np.zeros((SLOT, SLOT, 4), dtype=np.uint8)
    out[..., :3] = r
    out[..., 3] = alpha
    return Image.fromarray(out, "RGBA")


def recolor(img, metal, gem):
    """Krone umfärben: Metall (Sättigung unter 0,45, das Silber ist leicht bläulich) bekommt Farbton, Sättigung und Helligkeit metal = (h, +s, ×v), die
    Edelsteine den Farbton gem."""
    a = np.array(img.convert("RGBA")).astype(float) / 255
    out = a.copy()
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            r, g, b, al = a[y, x]
            if al == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r, g, b)
            if s < 0.45:
                out[y, x, :3] = colorsys.hsv_to_rgb(metal[0], min(1, s * 0.5 + metal[1]), v * metal[2])
            else:
                out[y, x, :3] = colorsys.hsv_to_rgb(gem, s, v)
    return Image.fromarray((out * 255).astype(np.uint8), "RGBA")


def main():
    ref, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    im = {i: load(ref, i) for i in range(34)}

    # ---------------- Kästen ----------------
    def card(x, y):
        return (x, y, 126, 99)

    boxes = {
        # Übersicht: Kopf, sechs Karten (Knopf 2 px über der Unterkante), Knöpfe in den Karten, drei Knöpfe unten
        "head": (0, (0, 0, 390, 46), False),
        "c_gold": (0, card(0, 50), True), "c_farm": (0, card(132, 50), True), "c_crate": (0, card(264, 50), True),
        "c_red": (0, card(0, 158), True), "c_brown": (0, card(132, 158), True), "c_pink": (0, card(264, 158), True),
        "cb_gold": (0, (6, 129, 114, 18), False), "cb_farm": (0, (138, 129, 114, 18), False),
        "cb_crate": (0, (270, 129, 114, 18), False), "cb_red": (0, (6, 237, 114, 18), False),
        "cb_brown": (0, (138, 237, 114, 18), False), "cb_pink": (0, (270, 237, 114, 18), False),
        "b_gift": (0, (0, 261, 126, 27), False), "b_token": (0, (132, 261, 126, 27), False),
        "b_store": (0, (264, 261, 126, 27), False),
        # Farmpass
        "fp_list": (1, (0, 50, 192, 82), False), "fp_store": (1, (0, 135, 192, 72), False),
        "fp_storebtn": (1, (6, 185, 180, 18), False), "fp_pets": (1, (198, 135, 192, 27), False),
        "fp_menu": (1, (198, 180, 192, 27), False), "fp_abo": (1, (0, 216, 192, 27), False),
        # Crates: kurzer Kopf, Zeilen, grauer Knopf
        "head_short": (2, (0, 0, 390, 38), False),
        "r_epic": (2, (0, 41, 390, 48), False), "r_farm": (2, (0, 149, 390, 48), False),
        "gray": (2, (270, 63, 110, 18), False),
        # Möbel und Pets: Reiter, Karten, Knopf in der Karte
        "tab_brown": (3, (19, 42, 52, 20), False), "tab_off": (3, (75, 42, 48, 20), False),
        "tab_pink": (26, (23, 42, 38, 20), False),
        "k_furniture": (3, (1, 64, CARD_W, CARD_H), True), "kb": (3, (5, 141, 86, 18), False),
        "k_common": (26, (1, 64, CARD_W, CARD_H), True), "k_rare": (26, (197, 163, CARD_W, CARD_H), True),
        "k_epic": (28, (99, 163, CARD_W, CARD_H), True), "k_legendary": (30, (295, 64, CARD_W, CARD_H), True),
        "k_mythic": (32, (1, 64, CARD_W, CARD_H), True),
        # Tokens
        "t_blue": (33, (0, 44, 126, 157), True), "t_violet": (33, (132, 44, 126, 157), True),
        "t_gold": (33, (264, 44, 126, 157), True),
    }
    for name, (i, (x, y, w, h), mirror) in boxes.items():
        nine_auto(im[i], x, y, w, h, mirror_bottom=mirror).save(os.path.join(out, "box_%s.png" % name))

    dark, light, shadow = hexrgb("201400"), hexrgb("D1D2D5"), hexrgb("343435")
    pill(im[2], 84, 72, [dark, hexrgb("1B0E28")]).save(os.path.join(out, "pill_violet.png"))
    pill(im[2], 84, 180, [dark, hexrgb("0A210F")]).save(os.path.join(out, "pill_green.png"))
    pill(im[2], 111, 72, [light, shadow]).save(os.path.join(out, "pill_off.png"))

    # ---------------- Grafiken ----------------
    arts = {
        "title": (0, (44, 5, 180, 27)), "emerald_big": (0, (4, 4, 44, 43)), "emerald": (0, (302, 8, 326, 30)),
        # Rechtecke innerhalb der 3-px-Ränder der Kästen (Karte 126 x 99, Bild bis 1 px über den Umlaut-Punkten des
        # Titels bei y + 56)
        "ov_ranks": (0, (4, 54, 122, 105)), "ov_farm": (0, (136, 54, 254, 105)), "ov_crate": (0, (268, 54, 386, 105)),
        "ov_packs": (0, (4, 162, 122, 213)), "ov_furniture": (0, (136, 162, 254, 213)),
        "ov_pets": (0, (268, 162, 386, 213)),
        # Icons links neben Text: Rechteck endet vor dem Text (Knöpfe unten: Text ab x + 29)
        "gift": (0, (3, 264, 29, 285)), "token": (0, (135, 264, 161, 285)), "cart": (0, (267, 264, 293, 285)),
        "back": (1, (6, 6, 36, 36)), "fp_icon": (1, (38, 3, 76, 43)), "fp_cart": (1, (3, 138, 29, 161)),
        "fp_egg": (1, (201, 138, 227, 159)), "fp_leaf": (1, (201, 183, 227, 204)), "fp_cart2": (1, (3, 219, 29, 240)),
        "key_epic": (2, (5, 45, 47, 86)), "key_farm": (2, (5, 153, 47, 194)),
        # Smaragd vor Preisen 14 x 16 (aus der ersten Möbel-Karte; in den Crate-Zeilen deckt der Knopf die letzte Zeile)
        "emerald_price": (3, (24, 120, 45, 141)),
        # Tokens: Kopf-Münze vor „Deine Tokens“ (x 241), Stapel über dem Titel (y 95)
        "tok_coin": (33, (215, 3, 241, 35)), "tok_25": (33, (4, 48, 122, 95)), "tok_75": (33, (136, 48, 254, 95)),
        "tok_250": (33, (268, 48, 386, 95)),
    }
    pos = {}
    for name, (i, rect) in arts.items():
        img, xy = art(im[i], *rect)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": i}

    # ---------------- Möbel und Pets ----------------
    n = 0
    for tab in data.FURNITURE:
        for k, (fid, _name, _price) in enumerate(tab["items"]):
            page, idx = divmod(k, 8)
            a = im[tab["first"] + page]
            x0, y0 = 1 + CARD_DX * (idx % 4), CARD_Y + CARD_DY * (idx // 4)
            slot(a, x0, y0).save(os.path.join(out, "f_%s.png" % fid))
            n += 1
    for k, (pid, _name, _rar, _price) in enumerate(data.PETS):
        page, idx = divmod(k, 8)
        a = im[data.PETS_FIRST + page]
        x0, y0 = 1 + CARD_DX * (idx % 4), CARD_Y + CARD_DY * (idx // 4)
        slot(a, x0, y0).save(os.path.join(out, "p_%s.png" % pid))
        n += 1
    # Ränge in Survival (Platin, Diamant, Smaragd, Netherit): die Silberkrone aus dem SMP-Satz, umgefärbt
    crown = Image.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets/jewelshop/art_rank_platin.png"))
    crowns = {"platin": None, "diamant": ((0.52, 0.30, 1.0), 0.55), "smaragd": ((0.36, 0.35, 0.95), 0.38),
              "netherit": ((0.07, 0.10, 0.42), 0.78)}
    for name, how in crowns.items():
        img = crown.convert("RGBA") if how is None else recolor(crown, *how)
        img.save(os.path.join(out, "art_rank_%s.png" % name))
    with open(os.path.join(out, "art.json"), "w", encoding="utf-8") as f:
        json.dump(pos, f, indent=1, sort_keys=True)
    print("fertig", len(boxes), "Kästen,", len(arts), "Grafiken,", n, "Möbel/Pets")


if __name__ == "__main__":
    main()
