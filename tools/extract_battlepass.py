"""Schneidet die Bausteine des Battle-Pass-Dialogs aus den nativen Referenzbildern (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_battlepass.py <ordner-mit-n_bp1..3.png> tools/assets/battlepass [<vanilla-assets>]
Die n_bp*.png sind die Dialoge aus dem Release "Referenzen2" (4K-Aufnahmen 06.50.50 Belohnungen, 06.50.54 Premium,
06.50.58 Aufgaben), auf GUI-Pixel verkleinert (Ursprung 1332,201, Faktor 3, Pixel 3i+1). Danach
tools/build_ui_pack.py.

Ausgabe wie bei extract_jewelshop.py: box_<stil>.png (9-Slice), art_<name>.png (Grafiken), art.json (Lage), dazu
icon_key.png (goldener Schlüssel als 16-px-Icon). Der Smaragd in den Knöpfen ist js_emerald_price (Juwelenshop).
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jewelshop import art, load, nine_auto  # noqa: E402


def check_overlay(a, x0, y0, x1, y1):
    r = a[y0:y1, x0:x1]
    green = (r[..., 1] > 170) & (r[..., 0] < 140) & (r[..., 2] < 140)
    dark = r.sum(axis=2) < 30
    near = np.zeros_like(green)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            near |= np.roll(np.roll(green, dy, axis=0), dx, axis=1)
    keep = green | (dark & near)
    ys, xs = np.where(keep)
    bx0, by0, bx1, by1 = xs.min(), ys.min(), xs.max() + 1, ys.max() + 1
    out = np.zeros((by1 - by0, bx1 - bx0, 4), dtype=np.uint8)
    out[..., :3] = r[by0:by1, bx0:bx1]
    out[..., 3] = np.where(keep[by0:by1, bx0:bx1], 255, 0)
    return Image.fromarray(out, "RGBA"), (int(x0 + bx0), int(y0 + by0))


def check_by_diff(a, slot_x, slot_y, texture, tint, region):
    """Haken über dem abgeholten Feld: alles, was vom nachgebildeten Untergrund (Feld-Füllung + Item-Textur 2fach,
    mit tint multipliziert) abweicht. region = (x0, y0, x1, y1) im Referenzbild."""
    x0, y0, x1, y1 = region
    fill = a[slot_y + 20, slot_x + 3]
    under = np.zeros((y1 - y0, x1 - x0, 3), dtype=float)
    under[:] = fill
    t = np.array(Image.open(texture).convert("RGBA").crop((0, 0, 16, 16)).resize((32, 32), Image.NEAREST)).astype(float)
    k = np.array(tint, dtype=float) / 255
    ix, iy = slot_x + 7, slot_y + 7
    for yy in range(y0, y1):
        for xx in range(x0, x1):
            u, v = xx - ix, yy - iy
            if 0 <= u < 32 and 0 <= v < 32 and t[v, u, 3] > 128:
                under[yy - y0, xx - x0] = np.floor(t[v, u, :3] * k + 0.5)
    r = a[y0:y1, x0:x1]
    keep = np.abs(r - under).sum(axis=2) > 6
    ys, xs = np.where(keep)
    bx0, by0, bx1, by1 = xs.min(), ys.min(), xs.max() + 1, ys.max() + 1
    out = np.zeros((by1 - by0, bx1 - bx0, 4), dtype=np.uint8)
    out[..., :3] = r[by0:by1, bx0:bx1]
    out[..., 3] = np.where(keep[by0:by1, bx0:bx1], 255, 0)
    return Image.fromarray(out, "RGBA"), (int(x0 + bx0), int(y0 + by0))


def undim(a, x, y, size, tint, scale=2):
    """Item-Icon aus einem abgedunkelten Feld zurückrechnen: im Spiel wird die Textur mit der Textfarbe tint
    multipliziert und 2fach gezeichnet. Hintergrund = Farbe der Ecke oben links. Ergebnis: Textur 16 x 16."""
    r = a[y:y + size, x:x + size][::scale, ::scale]
    bg = a[y, x]
    k = np.array(tint, dtype=float) / 255
    out = np.zeros((r.shape[0], r.shape[1], 4), dtype=np.uint8)
    out[..., :3] = np.clip(np.round(r / k), 0, 255)
    out[..., 3] = np.where(np.abs(r - bg).sum(axis=2) > 4, 255, 0)
    return Image.fromarray(out, "RGBA")


def main():
    ref, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    bp = {i: load(ref, "n_bp%d" % i) for i in range(1, 4)}
    boxes = {
        "head": (1, (0, 0, 390, 48)),
        "tab_on": (1, (0, 54, 110, 22)), "tab": (1, (114, 54, 83, 22)), "tab_gold": (1, (201, 54, 74, 22)),
        "tab_gold_on": (2, (190, 54, 81, 22)), "gray": (1, (293, 13, 90, 22)),
        "level": (1, (0, 81, 390, 33)), "free": (1, (0, 117, 390, 60)), "prem": (1, (0, 207, 390, 60)),
        "slot": (1, (49, 124, 46, 46)), "slot_item": (1, (193, 124, 46, 46)), "slot_green": (1, (97, 124, 46, 46)), "slot_gold": (1, (49, 214, 46, 46)),
        "pill_on": (1, (104, 181, 32, 22)), "pill_cur": (1, (200, 181, 32, 22)), "pill_off": (1, (248, 181, 32, 22)),
        "quest_day": (3, (0, 81, 390, 69)), "quest_week": (3, (0, 153, 390, 96)),
        "prem_box": (2, (0, 81, 390, 78)), "buy_box": (2, (0, 162, 390, 87)),
        "btn_gold_on": (2, (66, 132, 162, 22)), "btn_gray": (2, (7, 200, 30, 22)), "btn_violet": (2, (75, 200, 80, 22)),
        "btn_green": (2, (159, 200, 30, 22)), "btn_red_on": (2, (272, 200, 111, 22)),
    }
    # Reihen voller Felder: Mittelspalte in der Lücke zwischen zwei Feldern, Mittelzeile neben der Beschriftung
    mids = {"free": (96, 33), "prem": (96, 33)}
    for name, (i, (x, y, w, h)) in boxes.items():
        nine_auto(bp[i], x, y, w, h, mid=mids.get(name)).save(os.path.join(out, "box_%s.png" % name))
    arts = {
        "title": (1, (46, 6, 200, 28)), "ticket": (1, (4, 4, 47, 45)), "freepass": (1, (296, 16, 318, 31)),
        "tab_chest": (2, (3, 57, 25, 74)), "tab_scroll": (1, (117, 57, 140, 74)), "tab_crown": (1, (204, 57, 227, 74)),
        "check_big": (1, (285, 86, 309, 110)), "free_chest": (1, (5, 121, 46, 150)), "prem_lock": (1, (5, 211, 46, 240)),
        "lock": (1, (125, 240, 139, 257)), "play": (2, (79, 203, 98, 219)), "crown": (2, (8, 90, 58, 146)),
    }
    pos = {}
    for name, (i, rect) in arts.items():
        img, xy = art(bp[i], *rect)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": i}
    # Haken unten rechts im abgeholten Feld (Stufe 2, Erfahrungsfläschchen, Farbe #A2A2A2): mit Vanilla-Texturen
    # (3. Argument) genau als Abweichung vom Untergrund, sonst grüne Pixel und ihr dunkler Rand
    vanilla = sys.argv[3] if len(sys.argv) > 3 else None
    if vanilla:
        img, xy = check_by_diff(bp[1], 97, 124, os.path.join(vanilla, "assets/minecraft/textures/item/experience_bottle.png"),
                                (0xA2, 0xA2, 0xA2), (119, 147, 139, 166))
    else:
        img, xy = check_overlay(bp[1], 121, 149, 140, 166)
    img.save(os.path.join(out, "art_check.png"))
    pos["check"] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": 1}
    # goldener Schlüssel (Stufe 4, nicht erreicht: Farbe #767880) als 16-px-Icon
    undim(bp[1], 200, 131, 32, (0x76, 0x78, 0x80)).save(os.path.join(out, "icon_key.png"))
    with open(os.path.join(out, "art.json"), "w", encoding="utf-8") as f:
        json.dump(pos, f, indent=1, sort_keys=True)
    print("fertig", len(boxes), "Kästen,", len(arts), "Grafiken")


if __name__ == "__main__":
    main()
