"""Grafiken der Event-Presets (Weihnachten, Silvester, Valentinstag, Ostern, Sommerfest): je Preset ein Kopf-Bild
(32 px, Quest-Menü und Kachel), ein Reiter-Symbol (16 px), das Symbol der Währung (12 px) und die Sammel-Items als
Item-Texturen (16 px, normal und selten). Halloween nutzt die Grafiken aus den Quest-Referenzen.

Zeichnung mit build_hub_icons.Art (Umriss, Licht, Schatten). Ergebnis:
  tools/assets/events/art_<preset>_head.png, _icon.png, _coin.png   (Sprites ev_<preset>_…, über build_jewelshop.SETS)
  tools/assets/events/item_<item>.png                                (Item-Texturen, build_event_items.py)
"""
import os

from build_hub_icons import Art, GOLD, GOLD_D, WOOD, trim

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "assets", "events")


def S(v, n):
    """Koordinate einer 16er-Zeichnung auf Größe n."""
    return round(v * n / 16)


def heart(a, x0, y0, x1, y1, col, mode="full"):
    """Herz im Rechteck (x0, y0) bis (x1, y1): zwei runde Bögen oben mit Kerbe, Spitze unten."""
    w, h = x1 - x0, y1 - y0
    a.ellipse(x0, y0, x0 + w * 0.56, y0 + h * 0.6, col, mode)
    a.ellipse(x1 - w * 0.56, y0, x1, y0 + h * 0.6, col, mode)
    a.poly([(x0 + 0.2, y0 + h * 0.36), (x1 - 0.2, y0 + h * 0.36), ((x0 + x1) / 2, y1)], col, mode)
    mid = (x0 + x1) / 2
    a.erase(lambda d: d.rectangle((round(mid - 0.6), round(y0), round(mid - 0.4), round(y0 + max(1, h * 0.08))), fill=255))


# ------------------------------------------------------------------ Sammel-Items (16 px Item, 12 px Währung)
def gingerbread(n, body="C8783A", icing="FFF4EA"):
    a = Art(n)
    a.ellipse(S(5, n), S(0.5, n), S(11, n), S(6, n), body)
    a.rect(S(1.5, n), S(6, n), S(14.5, n), S(8.5, n), body)
    a.ellipse(S(4, n), S(5, n), S(12, n), S(12, n), body)
    a.poly([(S(4.5, n), S(10, n)), (S(7.5, n), S(10, n)), (S(6.5, n), S(15.5, n)), (S(3, n), S(15.5, n))], body)
    a.poly([(S(8.5, n), S(10, n)), (S(11.5, n), S(10, n)), (S(13, n), S(15.5, n)), (S(9.5, n), S(15.5, n))], body)
    a.dots([(S(6.6, n), S(2.6, n)), (S(9.4, n), S(2.6, n))], "3A2010")
    a.dots([(S(8, n), S(7.6, n)), (S(8, n), S(9.8, n))], icing)
    if n >= 14:
        a.dots([(7, 4), (8, 4), (9, 4)], icing)
        a.dots([(2, 7), (13, 7), (4, 14), (12, 14)], icing)
    return a


def clover(n, leaf="4AC860", stem="2E8A3A"):
    a = Art(n)
    a.line([(S(8, n), S(9, n)), (S(11, n), S(15, n))], stem, max(1, S(1.5, n)))
    r = 3.6 * n / 16
    for cx, cy in ((5, 5), (11, 5), (5, 10.5), (11, 10.5)):
        heart_x, heart_y = cx * n / 16, cy * n / 16
        a.ellipse(heart_x - r, heart_y - r, heart_x + r, heart_y + r, leaf)
    a.dots([(S(4, n), S(3.5, n)), (S(10, n), S(3.5, n))], "C8FFC0")
    return a


def heart_item(n, col="FF5A8A", shine="FFD0E0"):
    a = Art(n)
    heart(a, S(0.5, n), S(1.5, n), S(15.5, n), S(15, n), col)
    a.dots([(S(3.5, n), S(4, n)), (S(4, n), S(3.5, n))], shine)
    return a


def crystal_heart(n):
    a = heart_item(n, "8AE8FF", "FFFFFF")
    a.line([(S(8, n), S(4, n)), (S(8, n), S(13, n))], "5AB8E8", 1, mode="flat", edge=False)
    a.line([(S(3, n), S(6.5, n)), (S(13, n), S(6.5, n))], "C8F6FF", 1, mode="flat", edge=False)
    return a


def egg(n, shell="7AC0F0", band="FF7AA8", dots="FFE070"):
    a = Art(n)
    a.ellipse(S(2.5, n), S(0.5, n), S(13.5, n), S(15.5, n), shell)
    a.rect(S(3, n), S(7, n), S(13, n), S(9.4, n), band, mode="soft", edge=False)
    a.dots([(S(5, n), S(4, n)), (S(10, n), S(4.5, n)), (S(7.5, n), S(12.5, n)), (S(11, n), S(12, n))], dots)
    a.dots([(S(5, n), S(2.5, n)), (S(4.5, n), S(3, n))], "FFFFFF")
    return a


def shell(n, col="FF9E7A", rib="E0705A"):
    import math
    a = Art(n)
    cx, cy, r = 8 * n / 16, 10.5 * n / 16, 7.6 * n / 16
    pts = [(cx + math.cos(math.radians(t)) * r, cy - math.sin(math.radians(t)) * r * 1.25) for t in range(0, 181, 15)]
    a.poly([(cx, 14.5 * n / 16)] + pts, col)
    a.poly([(S(4.5, n), S(12.5, n)), (S(11.5, n), S(12.5, n)), (S(10.5, n), S(15.5, n)), (S(5.5, n), S(15.5, n))], col)
    for t in (25, 55, 90, 125, 155):
        x = cx + math.cos(math.radians(t)) * r * 0.92
        y = cy - math.sin(math.radians(t)) * r * 1.15
        a.line([(cx, 13.5 * n / 16), (x, y)], rib, 1, mode="flat", edge=False)
    a.dots([(S(5, n), S(3.5, n))], "FFE0D0")
    return a


def pearl(n):
    a = Art(n)
    a.poly([(S(8, n), S(15.5, n)), (S(1, n), S(9, n)), (S(15, n), S(9, n))], "F4A8C0")
    a.ellipse(S(3.5, n), S(1.5, n), S(12.5, n), S(10.5, n), "F4F0FF")
    a.dots([(S(5.5, n), S(3.5, n)), (S(6, n), S(4, n)), (S(5.5, n), S(4, n))], "FFFFFF")
    return a


# ------------------------------------------------------------------ Köpfe (32 px)
def head_weihnachten():
    a = Art()
    a.ground_shadow(3, 27, 29, 31)
    a.rect(14, 25, 17, 29, WOOD)
    a.poly([(16, 4), (24, 13), (8, 13)], "2E9A4A")
    a.poly([(16, 8), (26, 19), (6, 19)], "2A8C44")
    a.poly([(16, 13), (28, 25), (4, 25)], "26803E")
    a.dots([(15, 7), (14, 8), (13, 12), (11, 17), (9, 18), (8, 23), (7, 24)], "7AD890")
    for (x, y, col) in ((12, 11, "E84848"), (19, 12, "48A8F8"), (10, 17, "FFD84A"), (21, 17, "E84848"), (15, 16, "FFFFFF"),
                        (8, 23, "48A8F8"), (16, 22, "FFD84A"), (23, 23, "E84848"), (19, 21, "FFFFFF")):
        a.ellipse(x - 1, y - 1, x + 1, y + 1, col)
    a.poly([(16, 0), (17, 2), (19, 2), (17.5, 3.5), (18, 6), (16, 4.5), (14, 6), (14.5, 3.5), (13, 2), (15, 2)], "FFE04A")
    # Geschenk
    a.rect(22, 24, 30, 30, "E84848")
    a.rect(25, 24, 26, 30, GOLD, mode="soft")
    a.rect(22, 26, 30, 27, GOLD, mode="soft")
    a.sparkle(4, 4, 2, "E0F4FF")
    a.sparkle(28, 6, 1, "E0F4FF")
    return a


def head_silvester():
    a = Art()
    cx, cy = 16, 11
    import math
    cols = ["FFE04A", "FF7AA8", "7AE8FF", "9EF07A"]
    for k in range(12):
        ang = k * math.pi / 6
        x1, y1 = cx + math.cos(ang) * 10, cy + math.sin(ang) * 9
        col = cols[k % 4]
        a.line([(cx + math.cos(ang) * 4, cy + math.sin(ang) * 3.5), (x1, y1)], col, 1, mode="flat", edge=False)
        a.dot(round(x1 + math.cos(ang)), round(y1 + math.sin(ang)), "FFFFFF")
    a.sparkle(cx, cy, 2, "FFF6C8")
    # Rakete
    a.ground_shadow(10, 28, 22, 31)
    a.line([(16, 26), (16, 30)], WOOD, 1, mode="soft")
    a.rect(13, 19, 18, 26, "E84848")
    a.rect(13, 21, 18, 22, "FFFFFF", mode="soft", edge=False)
    a.poly([(13, 19), (15.5, 15), (18, 19)], GOLD)
    a.dots([(11, 28), (20, 29), (12, 30)], "FFB040")
    return a


def head_valentinstag():
    a = Art()
    a.ground_shadow(6, 27, 26, 31)
    a.line([(2, 26), (29, 4)], "C8A070", 2, mode="soft")
    heart(a, 4, 5, 28, 28, "F0406E")
    a.ellipse(8, 9, 13, 13, "FF8AAA", mode="soft", edge=False)
    a.dots([(9, 9), (10, 9), (9, 10)], "FFFFFF")
    a.line([(18, 15), (29, 4)], "C8A070", 2, mode="soft")
    a.poly([(27, 2), (31, 1), (30, 5)], "E0E6EE")
    a.poly([(1, 24), (5, 24), (2, 29)], "FF8AAA")
    a.sparkle(28, 22, 2, "FFD0E0")
    return a


def head_ostern():
    a = Art()
    a.ground_shadow(3, 27, 29, 31)
    # Henkel hinten
    a.ellipse(5, 3, 27, 25, "A8743E")
    a.erase(lambda d: d.ellipse((8, 6, 24, 25), fill=255))
    # Eier
    a.ellipse(6, 10, 13, 19, "FF8AB0")
    a.ellipse(12, 8, 19, 18, "7AC0F0")
    a.ellipse(18, 10, 25, 19, "FFE070")
    a.ellipse(9, 13, 15, 20, "9EE07A")
    a.ellipse(16, 13, 22, 20, "C890FF")
    a.dots([(8, 12), (14, 10), (20, 12)], "FFFFFF")
    a.dots([(10, 17), (11, 17), (17, 16), (18, 16)], "FFFFFF")
    # Korb
    a.poly([(3, 17), (29, 17), (26, 29), (6, 29)], "C8883E")
    for y in (20, 23, 26):
        a.line([(5, y), (27, y)], "9A6024", 1, mode="flat", edge=False)
    for x in (9, 13, 17, 21, 25):
        a.line([(x, 18), (x - 1, 28)], "E0A458", 1, mode="flat", edge=False)
    a.rect(2, 16, 30, 18, "DDA050")
    a.sparkle(28, 5, 2, "FFF6C8")
    return a


def head_sommer():
    a = Art()
    import math
    for k in range(12):
        ang = k * math.pi / 6 + 0.26
        a.poly([(16 + math.cos(ang - 0.18) * 10, 15 + math.sin(ang - 0.18) * 10), (16 + math.cos(ang) * 15, 15 + math.sin(ang) * 15),
                (16 + math.cos(ang + 0.18) * 10, 15 + math.sin(ang + 0.18) * 10)], "FFB030", mode="soft")
    a.ellipse(6, 5, 26, 25, "FFD84A")
    # Sonnenbrille
    a.rect(8, 11, 24, 12, "2A2A34", mode="flat")
    a.ellipse(8, 11, 15, 17, "2A2A34", mode="flat")
    a.ellipse(17, 11, 24, 17, "2A2A34", mode="flat")
    a.dots([(10, 13), (11, 13), (19, 13), (20, 13)], "7AD8F0")
    # Lächeln
    a.dots([(12, 20), (13, 21), (14, 21), (15, 21), (16, 21), (17, 21), (18, 21), (19, 20)], "C8661E")
    a.dots([(9, 7), (10, 6)], "FFF4C0")
    return a


# ------------------------------------------------------------------ Reiter-Symbole (16 px)
def icon_weihnachten():
    a = Art(16)
    a.rect(2, 7, 13, 15, "E84848")
    a.rect(1, 5, 14, 8, "F05A5A")
    a.rect(7, 5, 8, 15, GOLD, mode="soft")
    a.ellipse(3, 1, 7, 5, GOLD)
    a.ellipse(8, 1, 12, 5, GOLD)
    a.erase(lambda d: d.point((5, 3), fill=255))
    a.erase(lambda d: d.point((10, 3), fill=255))
    return a


def icon_silvester():
    a = Art(16)
    a.line([(8, 11), (8, 15)], WOOD, 1, mode="soft")
    a.rect(6, 5, 9, 11, "E84848")
    a.rect(6, 7, 9, 7, "FFFFFF", mode="flat", edge=False)
    a.poly([(6, 5), (7.5, 1), (9, 5)], GOLD)
    a.sparkle(13, 3, 1, "FFE070")
    a.sparkle(2, 6, 1, "7AE8FF")
    return a


def icon_valentinstag():
    a = heart_item(16, "F0406E")
    return a


def icon_ostern():
    return egg(16, "FF9EC0", "7AC0F0", "FFFFFF")


def icon_sommer():
    a = Art(16)
    import math
    for k in range(8):
        ang = k * math.pi / 4
        a.line([(8 + math.cos(ang) * 5, 8 + math.sin(ang) * 5), (8 + math.cos(ang) * 7.5, 8 + math.sin(ang) * 7.5)], "FFB030", 1,
               mode="flat")
    a.ellipse(3, 3, 13, 13, "FFD84A")
    a.dots([(5, 5), (6, 5)], "FFF4C0")
    return a


PRESETS = {
    "weihnachten": (head_weihnachten, icon_weihnachten, lambda n: gingerbread(n)),
    "silvester": (head_silvester, icon_silvester, lambda n: clover(n)),
    "valentinstag": (head_valentinstag, icon_valentinstag, lambda n: heart_item(n)),
    "ostern": (head_ostern, icon_ostern, lambda n: egg(n)),
    "sommer": (head_sommer, icon_sommer, lambda n: shell(n)),
}

# Sammel-Items: Name des Item-Modells (kuby:event/<name>) -> Zeichnung (16 px)
ITEMS = {
    "lebkuchen": lambda: gingerbread(16),
    "lebkuchen_gold": lambda: gingerbread(16, "F2C443", "FFFFFF"),
    "kleeblatt": lambda: clover(16),
    "kleeblatt_gold": lambda: clover(16, "F6C343", "C8862A"),
    "herz": lambda: heart_item(16),
    "herz_kristall": lambda: crystal_heart(16),
    "osterei": lambda: egg(16),
    "osterei_gold": lambda: egg(16, "F6C343", "FFF0A0", "FFFFFF"),
    "muschel": lambda: shell(16),
    "perle": lambda: pearl(16),
}


def build():
    os.makedirs(OUT, exist_ok=True)
    for name, (head, icon, coin) in PRESETS.items():
        trim(head().render()).save(os.path.join(OUT, "art_%s_head.png" % name))
        trim(icon().render()).save(os.path.join(OUT, "art_%s_icon.png" % name))
        trim(coin(12).render()).save(os.path.join(OUT, "art_%s_coin.png" % name))
    for name, fn in ITEMS.items():
        fn().render().save(os.path.join(OUT, "item_%s.png" % name))
    return list(PRESETS) + list(ITEMS)


if __name__ == "__main__":
    print("ok Event-Grafiken:", ", ".join(build()))
