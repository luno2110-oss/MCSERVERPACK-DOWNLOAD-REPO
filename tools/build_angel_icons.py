"""Eigene Icons für das Angeln (16 x 16, Vanilla-Stil): Köder, Upgrades, Menü-Icons.

Gezeichnet aus einfachen Formen mit Schattierung und dunklerem Rand. Reihenfolge = ICONS, muss zu den
Namen in KitData.Icon passen (build_kit.py liest die Liste). Köder bekommen auch Item-Texturen und -Modelle.
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
    return tuple(max(0, min(255, round(v * f))) for v in c[:3]) + (255,)


class Pix:
    """16 x 16 Zeichenfläche mit Ebenen: jede Form hat eine Farbe, am Ende Rand und Licht."""

    def __init__(self):
        self.c = {}
        self.group = {}

    def put(self, x, y, col, g=0):
        if 0 <= x < 16 and 0 <= y < 16:
            self.c[(x, y)] = col
            self.group[(x, y)] = g

    def disk(self, cx, cy, r, col, g=0):
        for y in range(16):
            for x in range(16):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.put(x, y, col, g)

    def ring(self, cx, cy, r0, r1, col, g=0):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if r0 <= d <= r1:
                    self.put(x, y, col, g)

    def arc(self, cx, cy, r0, r1, a0, a1, col, g=0):
        """Ringstück zwischen den Winkeln a0..a1 (Grad, 0 = rechts, 90 = unten)."""
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                d = math.hypot(dx, dy)
                a = math.degrees(math.atan2(dy, dx)) % 360
                if r0 <= d <= r1 and (a0 <= a <= a1 if a0 <= a1 else (a >= a0 or a <= a1)):
                    self.put(x, y, col, g)

    def rect(self, x0, y0, x1, y1, col, g=0):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, col, g)

    def line(self, x0, y0, x1, y1, col, w=1.0, g=0):
        for y in range(16):
            for x in range(16):
                px, py = x + 0.5, y + 0.5
                dx, dy = x1 - x0, y1 - y0
                L2 = dx * dx + dy * dy
                t = 0 if L2 == 0 else max(0, min(1, ((px - x0) * dx + (py - y0) * dy) / L2))
                if math.hypot(px - (x0 + t * dx), py - (y0 + t * dy)) <= w / 2:
                    self.put(x, y, col, g)

    def poly(self, pts, col, g=0):
        for y in range(16):
            for x in range(16):
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
                    self.put(x, y, col, g)

    def image(self, outline=True, light=True):
        img = Image.new("RGBA", (16, 16), T)
        px = img.load()
        for (x, y), col in self.c.items():
            c = col
            if outline:
                g = self.group[(x, y)]
                n = [(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
                if any(q not in self.c or self.group[q] != g for q in n):
                    lit = (x - 1, y) not in self.c or (x, y - 1) not in self.c
                    c = shade(col, 0.95 if (lit and light) else 0.68)
            px[x, y] = c
        return img


def worm():
    p = Pix()
    pts = [(3.0, 13.5), (5.5, 11.5), (5.0, 8.5), (7.5, 6.5), (10.5, 7.5), (12.0, 4.5), (11.5, 2.5)]
    for (a, b), (c, d) in zip(pts, pts[1:]):
        p.line(a, b, c, d, rgb("E87888"), 2.3)
    for i, (a, b) in enumerate(pts[1:-1]):
        p.put(int(a), int(b), rgb("C8505E"))
    p.put(11, 2, rgb("F2A0AA"))
    return p.image()


def lure(body, accent, sparkle=None, glow=None):
    p = Pix()
    p.poly([(4, 4), (9, 3), (12, 7), (10, 11), (5, 9)], rgb(body))
    p.poly([(5, 5), (8, 4.5), (9, 6), (6, 7)], mix(rgb(body), rgb("FFFFFF"), 0.45), g=1)
    p.line(10.5, 10.5, 12.5, 13.5, rgb("8A8E96"), 1.0, g=2)
    p.line(12.5, 13.5, 13.5, 12.0, rgb("8A8E96"), 1.0, g=2)
    p.line(2.0, 2.5, 4.5, 4.5, rgb("C8C8C8"), 0.9, g=3)
    p.disk(6.5, 8.0, 1.0, rgb(accent), g=4)
    img = p.image()
    if sparkle:
        for x, y in ((3, 10), (13, 3), (2, 7)):
            img.putpixel((x, y), rgb(sparkle))
    if glow:
        img.putpixel((11, 7), rgb(glow))
        img.putpixel((12, 6), rgb(glow))
    return img


def hook():
    p = Pix()
    gold = rgb("F2C23A")
    p.line(10.5, 3.5, 10.5, 10.0, gold, 1.7)
    p.arc(7.5, 10.0, 2.0, 3.7, 350, 190, gold)
    p.line(4.5, 10.0, 4.5, 7.0, gold, 1.6)
    p.line(4.5, 7.0, 6.2, 8.7, rgb("FFE27A"), 1.0, g=1)
    p.ring(10.5, 2.5, 0.7, 1.7, rgb("C8901A"), g=2)
    return p.image()


def reel(col, rim):
    p = Pix()
    p.disk(8, 8, 6.2, rgb(rim))
    p.disk(8, 8, 4.6, rgb(col), g=1)
    p.disk(8, 8, 1.5, rgb(rim), g=2)
    for a in range(0, 360, 90):
        r = math.radians(a + 45)
        p.line(8 + math.cos(r) * 1.5, 8 + math.sin(r) * 1.5, 8 + math.cos(r) * 4.3, 8 + math.sin(r) * 4.3, rgb(rim), 0.9, g=2)
    p.line(13.0, 8.0, 15.0, 8.0, rgb("8A8E96"), 1.2, g=3)
    return p.image()


def eye_orb():
    p = Pix()
    p.disk(8, 8, 6.0, rgb("2A9AD8"))
    p.disk(8, 8, 4.0, rgb("4AD8F0"), g=1)
    p.disk(8, 8, 1.8, rgb("0E2A4A"), g=2)
    img = p.image()
    img.putpixel((6, 5), rgb("FFFFFF"))
    img.putpixel((5, 6), rgb("C8F4FF"))
    return img


def funnel():
    p = Pix()
    p.poly([(2, 3), (14, 3), (10, 9), (6, 9)], rgb("8A8E96"))
    p.rect(6, 9, 9, 13, rgb("6E727A"), g=1)
    p.rect(3, 3, 12, 4, rgb("4A4E56"), g=2)
    p.put(7, 6, rgb("F2C23A"))
    p.put(8, 6, rgb("F2C23A"))
    return p.image()


def two_fish():
    p = Pix()
    p.poly([(1, 11), (5, 8), (9, 10.5), (5, 13)], rgb("A8C8E0"))
    p.poly([(9, 10.5), (12, 8.5), (12, 12.5)], rgb("7AA0C0"), g=1)
    p.poly([(4, 4.5), (8, 2), (12, 4.5), (8, 7)], rgb("F2C23A"), g=2)
    p.poly([(12, 4.5), (15, 2.5), (15, 6.5)], rgb("C8901A"), g=3)
    img = p.image()
    img.putpixel((3, 10), rgb("141414"))
    img.putpixel((6, 4), rgb("141414"))
    return img


def fish_book():
    p = Pix()
    p.poly([(2, 4), (11, 1), (14, 11), (5, 14)], rgb("2E4E9A"))
    p.poly([(5, 14), (14, 11), (14, 12.5), (5.5, 15.5)], rgb("E8E2D0"), g=1)
    p.line(2.5, 4.5, 5.5, 14.5, rgb("1E2E6A"), 1.2, g=2)
    p.poly([(6, 8), (9, 6.5), (11, 8), (8.5, 9.5)], rgb("8AD8F0"), g=3)
    p.poly([(11, 8), (12.5, 6.5), (12.5, 9)], rgb("8AD8F0"), g=3)
    return p.image()


def scroll():
    p = Pix()
    p.rect(3, 3, 12, 12, rgb("F2E2B8"))
    p.rect(2, 2, 13, 3, rgb("C8A870"), g=1)
    p.rect(2, 12, 13, 13, rgb("C8A870"), g=1)
    for y in (5, 7, 9):
        p.line(4.5, y + 0.5, 11.0, y + 0.5, rgb("8A7050"), 0.8, g=2)
    p.rect(9, 10, 11, 14, rgb("D83030"), g=3)
    return p.image()


def bait_bag():
    p = Pix()
    p.poly([(4, 6), (12, 6), (14, 13), (2, 13)], rgb("A8743A"))
    p.rect(3, 13, 13, 14, rgb("8A5A2C"), g=1)
    p.rect(5, 4, 11, 6, rgb("8A5A2C"), g=2)
    p.line(6.0, 4.0, 10.0, 2.5, rgb("E8C878"), 0.9, g=3)
    p.disk(8, 9.5, 1.6, rgb("E87888"), g=4)
    return p.image()


def chest():
    p = Pix()
    p.rect(2, 5, 13, 13, rgb("B8803E"))
    p.rect(2, 4, 13, 7, rgb("8A5A2C"), g=1)
    p.rect(7, 6, 8, 9, rgb("F2C23A"), g=2)
    p.rect(2, 10, 13, 10, rgb("6A4220"), g=3)
    return p.image()


def sun_hour():
    p = Pix()
    for a in range(0, 360, 45):
        r = math.radians(a)
        p.line(8 + math.cos(r) * 4.5, 8 + math.sin(r) * 4.5, 8 + math.cos(r) * 7, 8 + math.sin(r) * 7, rgb("F2C23A"), 1.2, g=1)
    p.disk(8, 8, 4.2, rgb("FFD84A"))
    p.disk(7, 7, 1.6, rgb("FFF2B0"), g=2)
    return p.image()


def buoy():
    p = Pix()
    p.disk(8, 9, 5.0, rgb("F04A3A"))
    p.rect(3, 8, 13, 9, rgb("F4F4F4"), g=1)
    p.rect(7, 1, 8, 4, rgb("6E727A"), g=2)
    p.rect(2, 13, 13, 14, rgb("2A88C8"), g=3)
    return p.image()


def coin_stack():
    p = Pix()
    for i, y in enumerate((11.5, 8.5, 5.5)):
        p.disk(8, y, 4.5, rgb("F2C23A") if i % 2 == 0 else rgb("E2A82A"), g=i)
    img = p.image()
    for x in range(5, 11):
        img.putpixel((x, 5), rgb("FFE27A"))
    return img


ICONS = [
    ("worm", worm), ("lure_glitter", lambda: lure("C8D8E8", "4AD8F0", sparkle="FFFFFF")),
    ("lure_night", lambda: lure("3A2A6A", "A84AF0", glow="7AF0E8")), ("lure_gold", lambda: lure("F2C23A", "D83030", sparkle="FFF2A0")),
    ("hook_gold", hook), ("reel", lambda: reel("6E7E92", "3A4656")), ("eye_orb", eye_orb),
    ("reel_big", lambda: reel("F2C23A", "8A5A1E")), ("funnel", funnel), ("two_fish", two_fish),
    ("fish_book", fish_book), ("scroll", scroll), ("bait_bag", bait_bag), ("chest", chest),
    ("sun_hour", sun_hour), ("buoy", buoy), ("coin_stack", coin_stack),
]
BAIT_ITEMS = {"worm": "regenwurm", "lure_glitter": "glitzerkoeder", "lure_night": "nachtkoeder", "lure_gold": "goldkoeder"}


def build(out):
    """Gibt [(name, bild)] zurück und legt Item-Texturen und -Modelle für die Köder an."""
    tex = os.path.join(out, "assets/kuby/textures/item/angeln")
    models = os.path.join(out, "assets/kuby/models/item/angeln")
    items = os.path.join(out, "assets/kuby/items/angeln")
    for d in (tex, models, items):
        os.makedirs(d, exist_ok=True)
    result = []
    for name, fn in ICONS:
        img = fn()
        result.append((name, img))
        img.save(os.path.join(tex, name + ".png"))
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/angeln/" + name}}, fh)
        with open(os.path.join(items, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/angeln/" + name}}, fh)
    return result
