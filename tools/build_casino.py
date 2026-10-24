"""Casino-Grafik: Poker-Chips, Roulette-Rad, Jukebox, Schallplatte und kleine Symbole für die Dialoge,
Chip-Items für Kasse und Tische, Tooltip-Rahmen "casino".

Eigene Zeichnungen im Stil der Referenz-Clips (Draufsicht-Chips mit sechs Randflecken, Gold-Rad mit roten und
schwarzen Fächern). Aufruf aus build_ui_pack.py: build(out) -> (icons16, small, big).
"""
import json
import math
import os

from PIL import Image

T = (0, 0, 0, 0)


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, f):
    f = max(0.0, min(1.0, f))
    return tuple(round(a + (b - a) * f) for a, b in zip(c1[:3], c2[:3])) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


OUT = rgb("0C0606")      # dunkle Kontur


class Pix:
    """Zeichenfläche beliebiger Größe; Formen in Ebenen, am Ende Kontur (8er-Nachbarschaft) nach außen."""

    def __init__(self, w, h=None):
        self.w, self.h = w, h or w
        self.c = {}

    def put(self, x, y, col):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.c[(x, y)] = col

    def each(self):
        for y in range(self.h):
            for x in range(self.w):
                yield x, y

    def disk(self, cx, cy, r, col):
        for x, y in self.each():
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                self.put(x, y, col)

    def ring(self, cx, cy, r0, r1, col):
        for x, y in self.each():
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r0 <= d <= r1:
                self.put(x, y, col)

    def sector(self, cx, cy, r0, r1, a0, a1, col):
        """Ringstück a0..a1 in Grad (0 = rechts, 90 = unten)."""
        for x, y in self.each():
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            a = math.degrees(math.atan2(dy, dx)) % 360
            inside = (a0 <= a < a1) if a0 <= a1 else (a >= a0 or a < a1)
            if r0 <= d <= r1 and inside:
                self.put(x, y, col)

    def rect(self, x0, y0, x1, y1, col):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, col)

    def line(self, x0, y0, x1, y1, col, w=1.0):
        for x, y in self.each():
            px, py = x + 0.5, y + 0.5
            dx, dy = x1 - x0, y1 - y0
            L2 = dx * dx + dy * dy
            t = 0 if L2 == 0 else max(0, min(1, ((px - x0) * dx + (py - y0) * dy) / L2))
            if math.hypot(px - (x0 + t * dx), py - (y0 + t * dy)) <= w / 2:
                self.put(x, y, col)

    def poly(self, pts, col):
        for x, y in self.each():
            px, py = x + 0.5, y + 0.5
            inside = False
            j = len(pts) - 1
            for i in range(len(pts)):
                xi, yi = pts[i]
                xj, yj = pts[j]
                if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi + 1e-9) + xi:
                    inside = not inside
                j = i
            if inside:
                self.put(x, y, col)

    def image(self, outline=OUT):
        img = Image.new("RGBA", (self.w, self.h), T)
        px = img.load()
        for (x, y), col in self.c.items():
            px[x, y] = col
        if outline:
            for x, y in self.each():
                if (x, y) in self.c:
                    continue
                if any((x + dx, y + dy) in self.c for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                    px[x, y] = outline
        return img


# =====================================================================================================================
# Poker-Chips (Draufsicht): Rand mit sechs Flecken, dunkler Innenring mit Glanz oben links, helle Mitte
# =====================================================================================================================
CHIPS = {
    # name: (Grundfarbe, Flecken, Innenring)
    "white": ("E6E6E2", "A9A9A6", "C2C2BE"),
    "red": ("F20C1E", "FFFFFF", "8C0208"),
    "blue": ("1F5BEF", "FFFFFF", "0A2A8C"),
    "green": ("12A332", "FFFFFF", "065C16"),
    "black": ("2E2D31", "F4C21A", "131215"),
    "purple": ("9124E6", "FFFFFF", "4E0C8A"),
    "gold": ("F8C51E", "B4141E", "A8740A"),
    "pink": ("F0508C", "FFFFFF", "8C1A44"),
}


def chip(name, size=16):
    base, spot, inner = (rgb(v) for v in CHIPS[name])
    s = size / 16
    c = size / 2
    p = Pix(size)
    p.disk(c, c, 7.0 * s, base)
    for a in (30, 90, 150, 210, 270, 330):
        p.sector(c, c, 4.9 * s, 7.1 * s, a - 14, a + 14, spot)
    p.ring(c, c, 4.0 * s, 4.9 * s, inner)
    p.disk(c, c, 4.0 * s, mix(base, rgb("FFFFFF"), 0.06))
    # Glanz oben links am Innenring, Schatten unten rechts am Rand
    p.sector(c, c, 4.0 * s, 4.9 * s, 190, 260, mix(inner, rgb("FFFFFF"), 0.55))
    p.sector(c, c, 6.2 * s, 7.1 * s, 20, 160, shade(base, 0.72) if name != "white" else rgb("B8B8B4"))
    for a in (30, 90, 150):
        p.sector(c, c, 6.2 * s, 7.1 * s, a - 14, a + 14, shade(spot, 0.82))
    return p.image()


def chip_small(name, r=4.4):
    """Kleiner Chip (für Zeilen-Symbole, ca. 9 x 9)."""
    base, spot, inner = (rgb(v) for v in CHIPS[name])
    size = 11
    c = size / 2
    p = Pix(size)
    p.disk(c, c, r, base)
    for a in (30, 90, 150, 210, 270, 330):
        p.sector(c, c, r - 1.3, r + 0.1, a - 16, a + 16, spot)
    p.disk(c, c, r - 2.2, mix(base, rgb("FFFFFF"), 0.12))
    img = p.image()
    return img.crop(img.getchannel("A").getbbox())


def star_small():
    p = Pix(11)
    pts = []
    for i in range(10):
        a = math.radians(-90 + i * 36)
        r = 5.2 if i % 2 == 0 else 2.3
        pts.append((5.5 + r * math.cos(a), 5.7 + r * math.sin(a)))
    p.poly(pts, rgb("F6C21C"))
    p.poly([(5.5, 1.2), (6.6, 4.4), (5.5, 5.6), (4.4, 4.4)], rgb("FFE88A"))
    img = p.image()
    return img.crop(img.getchannel("A").getbbox())


def coin_small():
    p = Pix(10)
    p.disk(5, 5, 4.2, rgb("F2B814"))
    p.disk(5, 5, 2.8, rgb("FAD24A"))
    p.put(4, 3, rgb("FFF0A0"))
    p.put(3, 4, rgb("FFF0A0"))
    img = p.image()
    return img.crop(img.getchannel("A").getbbox())


def note_small(col="B4B0A8"):
    p = Pix(9)
    c = rgb(col)
    p.rect(2, 1, 7, 1, c)
    p.rect(2, 1, 2, 6, c)
    p.rect(7, 1, 7, 5, c)
    p.rect(0, 6, 2, 7, c)
    p.rect(5, 5, 7, 6, c)
    img = p.image(outline=None)
    return img.crop(img.getchannel("A").getbbox())


def mute_small():
    p = Pix(10)
    g = rgb("8C8A86")
    p.rect(0, 3, 1, 6, g)
    p.poly([(1.5, 3), (4.5, 0.5), (4.5, 9.5), (1.5, 7)], g)
    r = rgb("E0302E")
    for i in range(4):
        p.put(6 + i, 3 + i, r)
        p.put(9 - i, 3 + i, r)
    img = p.image(outline=None)
    return img.crop(img.getchannel("A").getbbox())


# =====================================================================================================================
# Große Icons 32 x 32
# =====================================================================================================================
def roulette_wheel():
    """Roulette-Kessel leicht von oben: Goldrand, rote und schwarze Fächer, grüne Null unten, goldene Spindel."""
    p = Pix(32)
    cx, cy = 16, 15
    gold, gold_l, gold_d = rgb("F4C21C"), rgb("FFE870"), rgb("C0820E")
    # Kessel-Unterseite (Holz) schaut unten heraus
    p.disk(cx, cy + 2.6, 13.8, rgb("5E2C0C"))
    p.sector(cx, cy + 2.6, 11.5, 13.8, 20, 160, rgb("8A4618"))
    p.disk(cx, cy, 14.0, gold)
    p.sector(cx, cy, 12.4, 14.0, 190, 300, gold_l)
    p.sector(cx, cy, 12.4, 14.0, 10, 150, gold_d)
    p.ring(cx, cy, 11.3, 12.4, rgb("4A2008"))
    n = 16
    for i in range(n):
        a0 = (i * 360 / n + 90 - 360 / n / 2) % 360
        col = rgb("E21A1A") if i % 2 else rgb("181012")
        if i == 0:
            col = rgb("18A030")
        p.sector(cx, cy, 7.4, 11.3, a0, (a0 + 360 / n) % 360, col)
    for i in range(n):
        a = math.radians(i * 360 / n + 90 - 360 / n / 2)
        p.line(cx + 7.6 * math.cos(a), cy + 7.6 * math.sin(a), cx + 11.2 * math.cos(a), cy + 11.2 * math.sin(a),
               rgb("D8A020"), 0.6)
    p.ring(cx, cy, 6.6, 7.4, rgb("D8A020"))
    p.disk(cx, cy, 6.6, rgb("8C4A1C"))
    p.sector(cx, cy, 4.0, 6.6, 180, 300, rgb("A8622A"))
    # Spindel mit Kreuz
    p.rect(15, 4, 16, 14, gold)
    p.rect(15, 4, 15, 13, gold_l)
    p.rect(14, 3, 17, 4, gold_l)
    p.rect(11, 14, 20, 15, gold)
    p.disk(cx, cy, 3.0, gold)
    p.disk(cx - 0.7, cy - 0.8, 1.2, gold_l)
    # Kugel
    p.rect(22, 19, 23, 20, rgb("F4F4F4"))
    p.put(22, 19, rgb("FFFFFF"))
    return p.image()


def jukebox():
    """Jukebox im 50er-Stil: Goldbogen mit roten Strahlen, Anzeige, bunte Leuchtröhren, Gitter, Goldsockel."""
    p = Pix(32)
    gold, gold_l, gold_d = rgb("F4C21C"), rgb("FFE870"), rgb("B87A0C")
    # Bogen
    p.disk(15.99, 12.0, 10.4, gold)
    p.rect(5, 12, 26, 27, gold)
    p.disk(15.99, 12.0, 8.6, rgb("C8281A"))
    for i in range(7):
        a0 = 180 + i * 180 / 7
        p.sector(15.99, 12.0, 2.5, 8.6, a0, a0 + 180 / 14, rgb("F8902A"))
    p.rect(15, 3, 16, 10, gold_l)
    p.sector(15.99, 12.0, 8.6, 10.4, 190, 260, gold_l)
    # Anzeige
    p.rect(9, 10, 22, 13, rgb("2A1206"))
    p.rect(10, 11, 21, 12, rgb("D0D0D8"))
    for x in (11, 13, 18, 20):
        p.put(x, 11, rgb("F8F8F8"))
    p.rect(15, 11, 16, 12, rgb("E02020"))
    # Leuchtröhren links und rechts
    for x, col in ((6, "E040C0"), (7, "F4F4F4"), (8, "60C8F0"), (9, "F08A30")):
        p.rect(x, 15, x, 23, rgb(col))
    for x, col in ((22, "3A7AE8"), (23, "F4F4F4"), (24, "60C8F0"), (25, "E040C0")):
        p.rect(x, 15, x, 23, rgb(col))
    # Mittelteil mit Gitter
    p.rect(11, 15, 20, 24, rgb("5A2408"))
    for y in range(16, 24):
        for x in range(12, 20):
            if (x + y) % 3 == 0 or (x - y) % 3 == 0:
                p.put(x, y, rgb("D89A20"))
    p.rect(11, 14, 20, 14, gold_l)
    # Sockel und Füße
    p.rect(5, 24, 26, 27, gold)
    p.rect(5, 24, 26, 24, gold_l)
    p.rect(5, 27, 26, 27, gold_d)
    p.rect(6, 28, 9, 29, rgb("6A3010"))
    p.rect(22, 28, 25, 29, rgb("6A3010"))
    return p.image()


def vinyl():
    p = Pix(32)
    c = 15.99
    p.disk(c, c, 14.2, rgb("121214"))
    for r in (12.8, 10.9, 9.0):
        p.ring(c, c, r - 0.4, r + 0.2, rgb("26262A"))
    # Glanz oben links und unten rechts
    p.sector(c, c, 6.0, 13.6, 212, 238, rgb("8C8C92"))
    p.sector(c, c, 8.0, 12.6, 218, 232, rgb("D8D8DC"))
    p.sector(c, c, 6.0, 13.6, 32, 58, rgb("6A6A70"))
    p.sector(c, c, 8.5, 12.6, 38, 52, rgb("B8B8BE"))
    p.disk(c, c, 5.6, rgb("D81C24"))
    p.disk(c, c, 4.2, rgb("F03234"))
    p.sector(c, c, 2.0, 4.2, 200, 260, rgb("FF7070"))
    p.rect(15, 15, 16, 16, rgb("E8E8E8"))
    p.put(16, 16, rgb("1A1A1A"))
    return p.image()


# =====================================================================================================================
# Bauen
# =====================================================================================================================
ICONS16 = [("chip_" + n, (lambda n=n: chip(n))) for n in CHIPS]
SMALL = [("s_chip_" + n, (lambda n=n: chip_small(n))) for n in ("red", "blue", "gold", "green")] + [
    ("c_" + n, (lambda n=n: chip(n, 19))) for n in CHIPS] + [
    ("s_star", star_small), ("s_coin", coin_small), ("s_note", note_small), ("s_note_gold", lambda: note_small("F6D27A")),
    ("s_mute", mute_small)]
BIG = [("roulette_wheel", roulette_wheel), ("jukebox", jukebox), ("vinyl", vinyl)]


# =====================================================================================================================
# Tooltip-Rahmen (für alle Tooltips, wie im Clip): fast schwarzer Grund, lila Linie, goldene Eck-Knoten
# =====================================================================================================================
TIP_BG = (6, 4, 8, 250)
TIP_LINE_TOP, TIP_LINE_BOTTOM = rgb("4C2A42"), rgb("3A1E30")
# Eck-Knoten oben links (x, y im 100 x 100-Bild; Grund beginnt bei 8, Linie bei 9). Andere Ecken gespiegelt.
TIP_KNOT = {
    (11, 6): "G",
    (7, 7): "G", (8, 7): "G", (9, 7): "G", (11, 7): "G", (12, 7): "G", (13, 7): "G", (14, 7): "G",
    (7, 8): "G", (8, 8): "d", (9, 8): "g", (14, 8): "g",
    (7, 9): "G", (8, 9): "g", (9, 9): "g", (14, 9): "g",
    (6, 11): "G", (7, 11): "G",
    (7, 12): "G", (7, 13): "G",
    (7, 14): "g", (8, 14): "g", (9, 14): "g",
}
TIP_GOLD = {"G": rgb("E6AA44"), "g": rgb("C27E22"), "d": rgb("4D1E0C")}


def tooltip_sprites():
    bg = Image.new("RGBA", (100, 100), T)
    for y in range(8, 92):
        for x in range(8, 92):
            corner = (x in (8, 91)) and (y in (8, 91))
            if not corner:
                bg.putpixel((x, y), TIP_BG)
    frame = Image.new("RGBA", (100, 100), T)
    for i in range(9, 91):
        f = (i - 9) / 81
        side = mix(TIP_LINE_TOP, TIP_LINE_BOTTOM, f)
        frame.putpixel((i, 9), TIP_LINE_TOP)
        frame.putpixel((i, 90), TIP_LINE_BOTTOM)
        frame.putpixel((9, i), side)
        frame.putpixel((90, i), side)
    for (x, y), k in TIP_KNOT.items():
        for mx, my in ((x, y), (99 - x, y), (x, 99 - y), (99 - x, 99 - y)):
            col = TIP_GOLD[k]
            if mx > 50 and my > 50 and k == "G":
                col = TIP_GOLD["g"]
            frame.putpixel((mx, my), col)
    return bg, frame


def write_tooltip(out):
    d = os.path.join(out, "assets/minecraft/textures/gui/sprites/tooltip")
    os.makedirs(d, exist_ok=True)
    bg, frame = tooltip_sprites()
    bg.save(os.path.join(d, "background.png"))
    frame.save(os.path.join(d, "frame.png"))
    with open(os.path.join(d, "background.png.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": 100, "height": 100, "border": 9}}}, f)
    # Rand 16, damit die Eck-Knoten ganz in den Ecken liegen und nicht mit den Kanten gekachelt werden
    with open(os.path.join(d, "frame.png.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": 100, "height": 100, "border": 16,
                                       "stretch_inner": True}}}, f)


# =====================================================================================================================
# Chip-Items für Kasse und Tische (16 x 16): Stapel (Kaufen) und einzelne Chips (Auszahlen)
# =====================================================================================================================
ITEM_CHIPS = {
    # name: (Grundfarbe, Schatten, Flecken, Oberseite)
    "white": ("ECEAF6", "AAA4CC", "8E86C0", "F8F8FF"),
    "pink": ("D82070", "8A1048", "FFFFFF", "E8508C"),
    "blue": ("3AA8F2", "1E62B6", "FFFFFF", "6CC4FA"),
    "dark": ("3E2E5E", "221836", "FFFFFF", "5A4682"),
    "green": ("2FB872", "17724A", "FFFFFF", "5AD898"),
    "red": ("E42828", "8A1010", "FFFFFF", "F05454"),
    "black": ("2E2D33", "151418", "F4C21A", "46454C"),
    "purple": ("9030E0", "561890", "FFFFFF", "AA58F0"),
    "gold": ("F4C21C", "B07A0C", "B4141E", "FFE070"),
}


def chip_stack(name, layers=4):
    """Chip-Stapel leicht von oben: Seiten mit Streifen, oben die Chip-Fläche mit Randflecken."""
    base, dark, spot, top = (rgb(v) for v in ITEM_CHIPS[name])
    p = Pix(16)
    white = rgb("FFFFFF")
    bottom = 14
    for i in range(layers):
        yb = bottom - 2 * i
        for x in range(3, 13):
            p.put(x, yb, dark)
            p.put(x, yb - 1, base)
        for x in ((5, 6) if i % 2 == 0 else (9, 10)):
            p.put(x, yb - 1, spot)
        p.put(3, yb - 1, mix(base, white, 0.3))
        p.put(12, yb - 1, shade(base, 0.8))
    cx, ty = 7.99, bottom - 2 * layers - 1.0

    def ell(rx, ry, col):
        for yy in range(16):
            for xx in range(16):
                dx, dy = (xx + 0.5 - cx) / rx, (yy + 0.5 - ty) / ry
                if dx * dx + dy * dy <= 1.0:
                    p.put(xx, yy, col)

    ell(5.0, 2.9, base)
    ell(3.1, 1.6, top)
    for xx, yy in ((cx - 4.3, ty), (cx + 4.3, ty), (cx - 0.5, ty - 2.3), (cx - 0.5, ty + 2.2)):
        p.put(int(xx), int(yy), spot)
    p.put(7, int(ty), mix(top, white, 0.6))
    p.put(8, int(ty), mix(top, white, 0.35))
    return p.image(outline=rgb("1A1020"))


def chip_single(name):
    base, dark, spot, top = (rgb(v) for v in ITEM_CHIPS[name])
    p = Pix(16)
    c = 7.99
    # Rand (Dicke) unten
    for yy in range(16):
        for xx in range(16):
            dx, dy = (xx + 0.5 - c) / 5.6, (yy + 0.5 - 9.0) / 5.2
            if dx * dx + dy * dy <= 1.0:
                p.put(xx, yy, dark)
    for yy in range(16):
        for xx in range(16):
            dx, dy = (xx + 0.5 - c) / 5.6, (yy + 0.5 - 8.0) / 5.2
            d = dx * dx + dy * dy
            if d <= 1.0:
                p.put(xx, yy, base if d > 0.42 else top)
    for a in (30, 90, 150, 210, 270, 330):
        r = math.radians(a)
        p.put(int(c + 4.6 * math.cos(r)), int(8.0 + 4.3 * math.sin(r)), spot)
    p.put(6, 6, mix(top, rgb("FFFFFF"), 0.55))
    return p.image(outline=rgb("1A1020"))


ITEMS = [("stack_" + n, (lambda n=n: chip_stack(n))) for n in ITEM_CHIPS] + [
    ("chip_" + n, (lambda n=n: chip_single(n))) for n in ITEM_CHIPS]


def write_items(out):
    tex = os.path.join(out, "assets/kuby/textures/item/casino")
    models = os.path.join(out, "assets/kuby/models/item/casino")
    items = os.path.join(out, "assets/kuby/items/casino")
    for d in (tex, models, items):
        os.makedirs(d, exist_ok=True)
    for name, fn in ITEMS:
        fn().save(os.path.join(tex, name + ".png"))
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/casino/" + name}}, fh)
        with open(os.path.join(items, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/" + name}}, fh)


# Slot-Markierung in Truhen (wie im Clip): goldene Eck-Klammern statt weißer Fläche
def slot_highlight():
    img = Image.new("RGBA", (24, 24), T)
    dark, line, hi, lo = rgb("3A2434"), rgb("A85C18"), rgb("E6B23A"), rgb("C47E24")
    for y in range(2, 22):
        for x in (2, 21):
            img.putpixel((x, y), dark)
    for y in range(3, 21):
        img.putpixel((3, y), line)
        img.putpixel((20, y), line)
    for xs in (range(2, 9), range(15, 22)):
        for x in xs:
            img.putpixel((x, 2), dark)
            img.putpixel((x, 21), dark)
        for x in xs:
            if 3 <= x <= 20:
                img.putpixel((x, 3), hi)
                img.putpixel((x, 20), lo)
    return img


def write_slot_highlight(out):
    d = os.path.join(out, "assets/minecraft/textures/gui/sprites/container")
    os.makedirs(d, exist_ok=True)
    slot_highlight().save(os.path.join(d, "slot_highlight_front.png"))
    with open(os.path.join(d, "slot_highlight_front.png.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": 24, "height": 24, "border": 8}}}, f)


# =====================================================================================================================
# Spielkarten (32 x 32, Karte 24 x 30 mittig) und Materialien für Tische (Würfel-Modelle für Item-Anzeigen)
# =====================================================================================================================
RANKS = {
    "A": [".XXX.", "XX.XX", "XX.XX", "XXXXX", "XX.XX", "XX.XX", "XX.XX"],
    "2": ["XXXX.", "...XX", "...XX", ".XXX.", "XX...", "XX...", "XXXXX"],
    "3": ["XXXX.", "...XX", "...XX", ".XXX.", "...XX", "...XX", "XXXX."],
    "4": ["XX.XX", "XX.XX", "XX.XX", "XXXXX", "...XX", "...XX", "...XX"],
    "5": ["XXXXX", "XX...", "XXXX.", "...XX", "...XX", "...XX", "XXXX."],
    "6": [".XXX.", "XX...", "XX...", "XXXX.", "XX.XX", "XX.XX", ".XXX."],
    "7": ["XXXXX", "...XX", "..XX.", "..XX.", ".XX..", ".XX..", ".XX.."],
    "8": [".XXX.", "XX.XX", "XX.XX", ".XXX.", "XX.XX", "XX.XX", ".XXX."],
    "9": [".XXX.", "XX.XX", "XX.XX", ".XXXX", "...XX", "...XX", ".XXX."],
    "10": ["X.XXX", "X.X.X", "X.X.X", "X.X.X", "X.X.X", "X.X.X", "X.XXX"],
    "J": ["..XXX", "...XX", "...XX", "...XX", "XX.XX", "XX.XX", ".XXX."],
    "Q": [".XXX.", "XX.XX", "XX.XX", "XX.XX", "XX.XX", ".XXX.", "...XX"],
    "K": ["XX.XX", "XX.XX", "XXXX.", "XXX..", "XXXX.", "XX.XX", "XX.XX"],
}
SUITS_SMALL = {
    "h": [".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."],
    "d": ["...X...", "..XXX..", ".XXXXX.", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."],
    "s": ["...X...", "..XXX..", ".XXXXX.", "XXXXXXX", "XXXXXXX", "...X...", "..XXX.."],
    "c": ["..XXX..", "..XXX..", "XX.X.XX", "XXXXXXX", "XX.X.XX", "...X...", "..XXX.."],
}
CARD_RED, CARD_BLACK = rgb("D0202C"), rgb("1C1A22")


def suit_big(suit, size=12):
    """Großes Farbzeichen aus Formen (12 x 12)."""
    p = Pix(size)
    c = size / 2
    col = CARD_RED if suit in "hd" else CARD_BLACK
    if suit == "h":
        p.disk(c - 2.6, 4.2, 3.0, col)
        p.disk(c + 2.6, 4.2, 3.0, col)
        p.poly([(0.6, 5.0), (size - 0.6, 5.0), (c, size - 0.4)], col)
    elif suit == "d":
        p.poly([(c, 0.0), (size - 2.0, c), (c, size), (2.0, c)], col)
    elif suit == "s":
        p.poly([(c, 0.2), (size - 0.4, 6.4), (0.4, 6.4)], col)
        p.disk(c - 2.6, 6.8, 2.9, col)
        p.disk(c + 2.6, 6.8, 2.9, col)
        p.poly([(c - 0.6, 7.0), (c + 0.6, 7.0), (c + 2.6, size - 0.2), (c - 2.6, size - 0.2)], col)
    else:
        p.disk(c, 2.8, 2.5, col)
        p.disk(c - 3.3, 6.6, 2.5, col)
        p.disk(c + 3.3, 6.6, 2.5, col)
        p.rect(int(c) - 1, 4, int(c), 8, col)
        p.poly([(c - 0.8, 7.0), (c + 0.8, 7.0), (c + 2.8, size - 0.2), (c - 2.8, size - 0.2)], col)
    return p.image(outline=None)


def card(rank, suit):
    img = Image.new("RGBA", (32, 32), T)
    px = img.load()
    x0, y0, x1, y1 = 4, 1, 27, 30
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            corner = (x in (x0, x1)) and (y in (y0, y1))
            if corner:
                continue
            edge = x in (x0, x1) or y in (y0, y1)
            px[x, y] = rgb("B8B4C0") if edge else rgb("FAF9F4")
    col = CARD_RED if suit in "hd" else CARD_BLACK
    glyph = RANKS[rank]
    for gy, row in enumerate(glyph):
        for gx, ch in enumerate(row):
            if ch == "X":
                px[6 + gx, 3 + gy] = col
    for gy, row in enumerate(SUITS_SMALL[suit]):
        for gx, ch in enumerate(row):
            if ch == "X":
                px[13 + gx, 3 + gy] = col
    big = suit_big(suit)
    img.alpha_composite(big, (16 - 6, 15))
    return img


def card_back():
    img = Image.new("RGBA", (32, 32), T)
    px = img.load()
    x0, y0, x1, y1 = 4, 1, 27, 30
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if (x in (x0, x1)) and (y in (y0, y1)):
                continue
            edge = x in (x0, x1) or y in (y0, y1)
            inner = x in (x0 + 2, x1 - 2) or y in (y0 + 2, y1 - 2)
            if edge:
                px[x, y] = rgb("F4F0E8")
            elif x in (x0 + 1, x1 - 1) or y in (y0 + 1, y1 - 1):
                px[x, y] = rgb("F4F0E8")
            elif inner:
                px[x, y] = rgb("E8B830")
            else:
                diamond = (x + y) % 4 == 0 or (x - y) % 4 == 0
                px[x, y] = rgb("A81E2C") if diamond else rgb("7A1020")
    return img


SUIT_IDS = "hdsc"
CARD_RANKS = ["A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"]

# Materialien: Würfel mit flacher Farbe (Tisch, Polster, Gold); Name -> (Farbe, Muster)
MATERIALS = {
    "felt": ("52672C", "flat"),
    "rim": ("6E4126", "rim"),
    "wood": ("4A2E16", "planks"),
    "gold": ("F8D23E", "gold"),
    "cushion": ("2F6440", "cushion"),
    "frame": ("C88C2A", "frame"),
    "black": ("1E1C22", "flat"),
    "rack": ("7A4A22", "planks"),
    "screen": ("0C0C10", "flat"),
    "bar": ("62B21A", "flat"),
    "white": ("F2F0EC", "flat"),
}


def material(name):
    base, kind = MATERIALS[name]
    b = rgb(base)
    img = Image.new("RGBA", (16, 16), b)
    px = img.load()
    for y in range(16):
        for x in range(16):
            c = b
            if kind == "rim":
                c = mix(b, rgb("FFFFFF"), 0.06) if y < 3 else (shade(b, 0.92) if y > 12 else b)
            elif kind == "planks":
                c = shade(b, 0.82) if y % 4 == 3 else (mix(b, rgb("FFFFFF"), 0.04) if (x * 7 + y * 3) % 11 == 0 else b)
            elif kind == "gold":
                c = mix(b, rgb("FFF4B0"), 0.35) if (x + y) % 9 == 0 or y == 0 else (shade(b, 0.9) if y == 15 else b)
            elif kind == "cushion":
                c = mix(b, rgb("FFFFFF"), 0.08) if x in (0, 15) or y in (0, 15) else b
            elif kind == "frame":
                c = mix(b, rgb("FFE090"), 0.3) if x == 0 or y == 0 else (shade(b, 0.8) if x == 15 or y == 15 else b)
            px[x, y] = c
    return img


def write_cards_and_materials(out):
    tex = os.path.join(out, "assets/kuby/textures/item/casino")
    models = os.path.join(out, "assets/kuby/models/item/casino")
    items = os.path.join(out, "assets/kuby/items/casino")
    btex = os.path.join(out, "assets/kuby/textures/block/casino")
    for d in (tex, models, items, btex):
        os.makedirs(d, exist_ok=True)

    def item(name, model):
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump(model, fh)
        with open(os.path.join(items, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/" + name}}, fh)

    for s in SUIT_IDS:
        for r in CARD_RANKS:
            name = "card_" + r.lower() + s
            card(r, s).save(os.path.join(tex, name + ".png"))
            item(name, {"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/casino/" + name}})
    card_back().save(os.path.join(tex, "card_back.png"))
    item("card_back", {"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/casino/card_back"}})
    faces = {d: {"texture": "#all", "uv": [0, 0, 16, 16]} for d in ("north", "south", "east", "west", "up", "down")}
    for name in MATERIALS:
        material(name).save(os.path.join(btex, name + ".png"))
        item("m_" + name, {"textures": {"all": "kuby:block/casino/" + name, "particle": "kuby:block/casino/" + name},
                           "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]})


def build(out):
    """Gibt (icons16, small, big) als [(name, bild)] zurück; schreibt Tooltip-Rahmen, Slot-Markierung, Chip-Items,
    Spielkarten und Tisch-Materialien."""
    if out:
        write_tooltip(out)
        write_slot_highlight(out)
        write_items(out)
        write_cards_and_materials(out)
    return ([(n, f()) for n, f in ICONS16], [(n, f()) for n, f in SMALL], [(n, f()) for n, f in BIG])
