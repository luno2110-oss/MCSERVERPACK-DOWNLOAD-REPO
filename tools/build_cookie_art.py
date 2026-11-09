"""Grafiken des Cookie Clickers (eigene Zeichnungen im Stil der Menü-Icons, build_hub_icons.Art):
großer Keks, Glückskeks, 14 Gebäude und die Symbole der Menü-Bereiche (32 px, Sprites ck_<name>), dazu die Items im
Spiel (write_models): kuby:cookie/big (großer Keks über der Station, 32 px), kuby:cookie/gold (Glückskeks) und
kuby:cookie/regen (Kekse aus dem Keksregen).

Ergebnis: tools/assets/cookie/art_<name>.png
"""
import json
import math
import os

from build_hub_icons import Art, trim

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "assets", "cookie")

DOUGH = "D8913E"
DOUGH_L = "F2BE6A"
CHOC = "4A2614"
STEEL = "9AA6B8"
WOODC = "A8693A"
WOODD = "7A4824"


def P():
    return Art(premium=True)


def cookie(a, x0, y0, x1, y1, dough=DOUGH, chips=CHOC, light=DOUGH_L):
    """Keks: Teig mit hellem Rand oben, dunklen Schokostücken (2 x 2 mit Glanzpunkt)."""
    a.ellipse(x0, y0, x1, y1, dough)
    w, h = x1 - x0, y1 - y0
    # Glanz: heller Bogen oben links am Rand (statt einer Fläche, die wie ein Riss wirkt)
    import math as _m
    cx, cy, rx, ry = (x0 + x1) / 2, (y0 + y1) / 2, w / 2 - 2.2, h / 2 - 2.2
    for deg in range(195, 265, 7):
        r = _m.radians(deg)
        a.dot(round(cx + rx * _m.cos(r)), round(cy + ry * _m.sin(r)), light)
    for deg in range(205, 250, 11):
        r = _m.radians(deg)
        a.dot(round(cx + (rx - 2) * _m.cos(r)), round(cy + (ry - 2) * _m.sin(r)), light, 150)
    s = max(1, round(w / 12))
    for fx, fy in ((0.28, 0.28), (0.62, 0.22), (0.44, 0.52), (0.74, 0.58), (0.24, 0.66), (0.52, 0.8)):
        cx, cy = round(x0 + w * fx), round(y0 + h * fy)
        a.rect(cx, cy, cx + s, cy + s, chips, mode="flat", edge=False)
        if s > 1:
            a.dot(cx, cy, "8A5230")


def coin(a, x, y, r, col="FFD24A", rim="E8A82A"):
    a.ellipse(x - r, y - r, x + r, y + r, rim)
    a.ellipse(x - r + 2, y - r + 2, x + r - 2, y + r - 2, col)


# ---------------------------------------------------------------- Kekse

def c_cookie():
    a = P()
    cookie(a, 1, 1, 30, 30)
    a.dots([(8, 5), (9, 5), (7, 6)], "FFE8B8")
    return a


def c_gold():
    a = P()
    cookie(a, 1, 1, 30, 30, dough="F2B42A", chips="A8641E", light="FFE070")
    a.dots([(8, 5), (9, 5), (7, 6)], "FFF8D0")
    a.sparkle(27, 4, 2, "FFF8D0")
    return a


def item_cookie(n, gold=False):
    a = Art(n, premium=True)
    s = n / 32
    if gold:
        cookie(a, round(1 * s), round(1 * s), round(30 * s), round(30 * s), dough="F2B42A", chips="A8641E", light="FFE070")
    else:
        cookie(a, round(1 * s), round(1 * s), round(30 * s), round(30 * s))
    return a


# ---------------------------------------------------------------- Gebäude

def b_loeffel():
    a = P()
    a.line([(4, 29), (17, 14)], WOODC, 3)
    a.ellipse(14, 2, 29, 17, "C88A4A")
    a.ellipse(17, 5, 26, 14, "8A5428", mode="soft")
    a.ellipse(19, 7, 24, 11, DOUGH, mode="soft", edge=False)
    a.dot(21, 8, CHOC)
    a.rect(3, 28, 5, 30, WOODD)
    return a


def b_schuessel():
    a = P()
    a.poly([(1, 11), (30, 11), (26, 24), (20, 28), (11, 28), (5, 24)], "E8EEF4")
    a.rect(1, 15, 30, 17, "4A90E0", mode="soft")
    a.ellipse(2, 7, 29, 15, "C8D4E0")
    a.ellipse(5, 8, 26, 14, DOUGH_L, mode="soft")
    a.dots([(10, 10), (17, 11), (21, 9)], CHOC)
    a.line([(22, 0), (17, 11)], STEEL, 2)
    a.ellipse(19, 0, 25, 4, "D8E0EA")
    return a


def b_blech():
    a = P()
    a.poly([(1, 15), (16, 7), (31, 15), (16, 24)], "B8C2D2")
    a.poly([(1, 15), (16, 24), (16, 28), (1, 19)], "7A8494")
    a.poly([(16, 24), (31, 15), (31, 19), (16, 28)], "5E6878")
    for cx, cy in ((10, 14), (16, 11), (22, 14), (16, 17)):
        a.ellipse(cx - 3, cy - 2, cx + 3, cy + 2, DOUGH)
        a.dot(cx - 1, cy - 1, CHOC)
        a.dot(cx + 1, cy, CHOC)
    return a


def b_ofen():
    a = P()
    a.poly([(2, 30), (2, 13), (8, 5), (24, 5), (30, 13), (30, 30)], "A8A8B4")
    for (x, y) in ((5, 15), (12, 8), (20, 8), (25, 16), (5, 24), (26, 25)):
        a.rect(x, y, x + 3, y + 2, "8A8A96", mode="soft", edge=False)
    a.ellipse(8, 13, 24, 30, "2A1A14")
    a.rect(8, 21, 24, 30, "2A1A14", edge=False)
    a.ellipse(10, 18, 22, 31, "FF7A1E", mode="soft", edge=False)
    a.ellipse(12, 22, 20, 31, "FFE070", mode="flat", edge=False)
    a.rect(13, 0, 19, 5, "8A8A96")
    return a


def b_lehrling():
    a = P()
    a.ellipse(4, 1, 28, 14, "F8F4EC")
    a.ellipse(0, 5, 13, 16, "F8F4EC")
    a.ellipse(19, 5, 31, 16, "F8F4EC")
    a.rect(6, 12, 26, 18, "E0DCD2")
    a.rect(6, 18, 26, 30, "F0C49A")
    a.rect(9, 22, 11, 24, "3A2A1E", mode="flat")
    a.rect(20, 22, 22, 24, "3A2A1E", mode="flat")
    a.dots([(9, 22), (20, 22)], "FFFFFF")
    a.line([(13, 27), (18, 27)], "C8604A", 1, mode="flat", edge=False)
    a.dots([(7, 25), (24, 25)], "F2A0A0")
    return a


def b_muehle():
    a = P()
    a.poly([(9, 31), (11, 13), (21, 13), (23, 31)], "D8C09A")
    a.poly([(9, 13), (16, 6), (23, 13)], "C8503A")
    a.rect(14, 23, 18, 31, WOODD)
    a.rect(12, 16, 14, 18, "6AA8E8", mode="flat")
    import math as _m
    for ang in (45, 135, 225, 315):
        r = _m.radians(ang)
        a.line([(16, 11), (16 + 15 * _m.cos(r), 11 + 15 * _m.sin(r))], "F4F0E8", 3)
    a.ellipse(14, 9, 18, 13, WOODC)
    return a


def b_zucker():
    a = P()
    for x, top, c in ((3, 6, "8AD050"), (9, 1, "6CC048"), (15, 4, "8AD050"), (21, 2, "6CC048")):
        a.rect(x, top, x + 4, 30, c)
        for y in range(top + 5, 29, 6):
            a.rect(x, y, x + 4, y, "3E8A2C", mode="flat", edge=False)
    a.ellipse(18, 19, 31, 31, "F8F8FF")
    a.dots([(21, 22), (25, 21), (23, 25), (27, 26)], "FFFFFF")
    a.dots([(22, 27), (26, 24)], "C8D4E8")
    return a


def b_schoko():
    a = P()
    a.poly([(2, 18), (30, 18), (26, 30), (6, 30)], "C8D0DC")
    a.ellipse(2, 15, 30, 21, "6A3418")
    a.rect(14, 5, 18, 17, "B8C0CC")
    a.ellipse(8, 2, 24, 8, "D0D8E4")
    a.poly([(9, 6), (11, 6), (12, 17), (8, 17)], "7A4020", mode="soft")
    a.poly([(21, 6), (23, 6), (24, 17), (20, 17)], "7A4020", mode="soft")
    a.ellipse(11, 3, 21, 7, "6A3418", mode="soft", edge=False)
    a.dots([(15, 3), (16, 3), (4, 19)], "F0D8C0")
    return a


def b_fabrik():
    a = P()
    a.poly([(1, 30), (1, 16), (8, 11), (8, 16), (15, 11), (15, 16), (22, 11), (22, 30)], "C8603A")
    a.rect(23, 3, 29, 30, "8A4A2A")
    a.rect(22, 3, 30, 5, "6A6A74")
    for x in (4, 11, 17):
        a.rect(x, 20, x + 3, 24, "FFE070", mode="flat")
        a.dot(x, 20, "FFF8C0")
    a.rect(1, 27, 22, 30, "6A6A74")
    a.ellipse(21, 0, 27, 3, "E0E0E8", mode="soft", edge=False)
    return a


def b_zeppelin():
    a = P()
    a.ellipse(1, 3, 29, 19, "E8503E")
    a.ellipse(4, 5, 20, 11, "FF8070", mode="soft", edge=False)
    a.rect(1, 10, 29, 11, "B8302A", mode="flat", edge=False)
    a.poly([(25, 5), (31, 1), (31, 20), (25, 16)], "B8302A")
    a.rect(10, 21, 22, 26, WOODC)
    a.line([(11, 18), (11, 21)], "3A2A1E", 1, mode="flat", edge=False)
    a.line([(21, 18), (21, 21)], "3A2A1E", 1, mode="flat", edge=False)
    a.ellipse(12, 25, 20, 31, DOUGH)
    a.dot(15, 27, CHOC)
    a.dot(17, 29, CHOC)
    return a


def b_bank():
    a = P()
    a.rect(2, 4, 30, 30, "8E9AAE")
    a.rect(4, 6, 28, 28, "6E7A8E")
    a.ellipse(8, 9, 24, 25, "C8D2E0")
    a.ellipse(11, 12, 21, 22, "5E6878")
    import math as _m
    for ang in range(0, 360, 45):
        r = _m.radians(ang)
        a.dot(round(16 + 7 * _m.cos(r)), round(17 + 7 * _m.sin(r)), "F4F8FC")
    a.rect(25, 13, 27, 21, "FFD24A")
    a.ellipse(13, 14, 19, 20, DOUGH)
    a.dot(15, 16, CHOC)
    return a


def b_zauber():
    a = P()
    a.ellipse(3, 12, 29, 31, "3A3A48")
    a.ellipse(3, 10, 29, 17, "52525E")
    a.ellipse(6, 11, 26, 16, "A64AF0", mode="soft", edge=False)
    a.ellipse(9, 1, 16, 8, "C86AF0", mode="soft")
    a.ellipse(18, 0, 23, 5, "E0A8FF", mode="soft")
    a.ellipse(12, 5, 19, 12, DOUGH)
    a.dot(14, 7, CHOC)
    a.rect(1, 21, 3, 24, "52525E")
    a.rect(29, 21, 31, 24, "52525E")
    return a


def b_zeitofen():
    a = P()
    a.rect(4, 0, 27, 4, "F2B42A")
    a.rect(4, 27, 27, 31, "F2B42A")
    a.rect(5, 5, 7, 26, "C8841E")
    a.rect(24, 5, 26, 26, "C8841E")
    a.poly([(9, 5), (22, 5), (17, 15), (17, 16), (22, 26), (9, 26), (14, 16), (14, 15)], "CFE8F8")
    a.ellipse(10, 6, 21, 12, DOUGH, mode="soft", edge=False)
    a.poly([(15, 19), (21, 25), (10, 25)], DOUGH, mode="soft", edge=False)
    a.dots([(15, 13), (15, 17), (16, 21)], CHOC)
    a.dots([(11, 7), (11, 22)], "FFFFFF")
    return a


def b_sterne():
    a = P()
    a.ellipse(0, 0, 31, 31, "2A2066")
    a.ellipse(3, 3, 28, 28, "4A3AA8", mode="soft", edge=False)
    cookie(a, 8, 8, 23, 23)
    for x, y in ((5, 7), (25, 5), (24, 26), (6, 24), (27, 16)):
        a.dot(x, y, "FFFFFF")
    a.sparkle(26, 5, 1, "E0F4FF")
    return a


BUILDINGS = [("loeffel", b_loeffel), ("schuessel", b_schuessel), ("blech", b_blech), ("ofen", b_ofen), ("lehrling", b_lehrling),
             ("muehle", b_muehle), ("zucker", b_zucker), ("schoko", b_schoko), ("fabrik", b_fabrik), ("zeppelin", b_zeppelin),
             ("bank", b_bank), ("zauber", b_zauber), ("zeitofen", b_zeitofen), ("sterne", b_sterne)]


# ---------------------------------------------------------------- Menü-Bereiche

def m_gebaeude():
    a = P()
    a.rect(4, 14, 28, 30, "F0E0C0")
    a.poly([(1, 16), (16, 2), (31, 16)], "C8503A")
    a.rect(23, 4, 26, 11, "8A4A2A")
    a.rect(13, 21, 19, 30, WOODC)
    a.dot(17, 26, "FFD24A")
    a.rect(6, 18, 10, 22, "6AA8E8")
    a.rect(22, 18, 26, 22, "6AA8E8")
    a.ellipse(11, 8, 21, 16, DOUGH)
    a.dots([(14, 10), (17, 12)], CHOC)
    return a


def m_upgrades():
    a = P()
    a.poly([(16, 1), (30, 15), (21, 15), (21, 30), (11, 30), (11, 15), (2, 15)], "4CC850")
    a.poly([(16, 6), (24, 14), (18, 14), (18, 27), (14, 27), (14, 14), (8, 14)], "A8F09A", mode="soft", edge=False)
    return a


def m_erfolge():
    a = P()
    a.ellipse(2, 3, 11, 13, "E8A82A")
    a.ellipse(21, 3, 30, 13, "E8A82A")
    a.poly([(7, 2), (25, 2), (23, 15), (16, 20), (9, 15)], "FFD24A")
    a.rect(14, 20, 18, 24, "E8A82A")
    a.rect(8, 24, 24, 30, "8A5428")
    a.rect(11, 26, 21, 28, "FFD24A", mode="flat")
    a.poly([(16, 5), (18, 9), (22, 9), (19, 12), (20, 16), (16, 13), (12, 16), (13, 12), (10, 9), (14, 9)], "FFF0A0", mode="soft", edge=False)
    return a


def m_aufstieg():
    a = P()
    a.poly([(2, 27), (2, 8), (9, 15), (16, 3), (23, 15), (30, 8), (30, 27)], "FFD24A")
    a.rect(2, 23, 30, 29, "E8A82A")
    for x, c in ((8, "E84848"), (16, "4ED8E8"), (24, "3AD86A")):
        a.rect(x - 2, 24, x + 1, 27, c)
    a.ellipse(14, 1, 18, 5, "FFF8D0")
    a.ellipse(0, 6, 4, 10, "FFF8D0")
    a.ellipse(28, 6, 32, 10, "FFF8D0")
    return a


def m_rangliste():
    a = P()
    a.rect(11, 10, 21, 30, "FFD24A")
    a.rect(1, 16, 11, 30, "D8E0EA")
    a.rect(21, 20, 31, 30, "D88A4A")
    a.rect(14, 14, 17, 18, "E8A82A", mode="flat")
    a.rect(4, 20, 7, 24, "A8B0C0", mode="flat")
    a.rect(24, 23, 27, 27, "A8603A", mode="flat")
    a.ellipse(11, 0, 21, 9, DOUGH)
    a.dots([(14, 3), (17, 5)], CHOC)
    return a


def m_statistik():
    a = P()
    a.rect(1, 2, 30, 30, "F4F0E8")
    for x, hgt, c in ((5, 16, "4ED8E8"), (11, 10, "4CC850"), (17, 20, "FFD24A"), (23, 7, "E8503E")):
        a.rect(x, 27 - hgt, x + 4, 27, c)
    a.rect(3, 27, 28, 28, "6A6A78", mode="flat", edge=False)
    return a


def m_hilfe():
    a = P()
    a.rect(3, 2, 29, 30, "C8503A")
    a.rect(7, 4, 28, 28, "F4F0E8")
    a.rect(3, 2, 7, 30, "8A3020")
    a.ellipse(11, 7, 24, 18, "FFD24A")
    a.ellipse(14, 10, 21, 15, "F4F0E8", mode="flat", edge=False)
    a.rect(16, 15, 19, 20, "FFD24A")
    a.rect(16, 22, 19, 25, "FFD24A")
    return a


def m_regen():
    a = P()
    a.ellipse(2, 4, 19, 15, "E8EEF8")
    a.ellipse(11, 1, 30, 14, "F8FAFF")
    a.rect(5, 9, 27, 15, "E8EEF8", edge=False)
    for cx, cy in ((7, 22), (16, 27), (25, 21)):
        a.ellipse(cx - 4, cy - 4, cx + 4, cy + 4, DOUGH)
        a.dot(cx - 1, cy - 1, CHOC)
        a.dot(cx + 1, cy + 1, CHOC)
    return a


def m_klick():
    a = P()
    cookie(a, 8, 8, 31, 31)
    a.poly([(1, 1), (1, 16), (5, 12), (8, 19), (11, 18), (8, 11), (13, 11)], "F8F8FF")
    for x, y in ((14, 2), (2, 19)):
        a.line([(x, y), (x + 3, y + 3)], "FFD24A", 1, mode="flat", edge=False)
    return a


MENU = [("gebaeude", m_gebaeude), ("upgrades", m_upgrades), ("erfolge", m_erfolge), ("aufstieg", m_aufstieg),
        ("rangliste", m_rangliste), ("statistik", m_statistik), ("hilfe", m_hilfe), ("regen", m_regen), ("klick", m_klick)]


def build():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith(".png"):
            os.remove(os.path.join(OUT, f))
    names = []
    # Neu gezeichnet mit der Icon-Datenbank (cookie_pixel_icons.py), die alten Zeichnungen hier bleiben nur als Vorlage
    import cookie_pixel_icons as cp
    for name, fn in cp.ICONS.items():
        trim(fn().render()).save(os.path.join(OUT, "art_%s.png" % name))
        names.append(name)
    return names


def _item(size, gold=False):
    """Keks als Item-Bild (Station, Glückskeks, Keksregen) im neuen Stil."""
    import cookie_pixel_icons as cp
    from hub_pixel_icons import layer
    if size == 32:
        return (cp.c_gold() if gold else cp.c_cookie()).render()
    pix = cp.cookie_pix(8, 8, 6.6, chunks=[(-6, -7, "c"), (5, -4, "c"), (-4, 4, "c"), (5, 6, "c")])
    return layer(pix, 16)


def write_models(out):
    tdir = os.path.join(out, "assets/kuby/textures/item/cookie")
    mdir = os.path.join(out, "assets/kuby/models/item/cookie")
    idir = os.path.join(out, "assets/kuby/items/cookie")
    for d in (tdir, mdir, idir):
        os.makedirs(d, exist_ok=True)
    for name, img in (("big", _item(32)), ("gold", _item(32, True)), ("regen", _item(16))):
        img.save(os.path.join(tdir, name + ".png"))
        with open(os.path.join(mdir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/cookie/" + name}}, f)
        with open(os.path.join(idir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/cookie/" + name}}, f)


if __name__ == "__main__":
    print("ok Cookie-Grafiken:", ", ".join(build()))
