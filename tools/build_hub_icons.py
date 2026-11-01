"""Eigene Icons für das Hauptmenü (/menu): Pixel-Art im Stil der Referenz-Grafiken (dunkler Umriss, Licht oben links,
Schatten unten rechts, Funkeln). Gezeichnet aus einfachen Formen, die Schattierung rechnet das Skript.

Ergebnis: tools/assets/hub/art_<name>.png (32 x 32 für Kacheln, 16 x 16 für Knöpfe). build_jewelshop.SETS nimmt den
Ordner mit dem Präfix hb_ auf, im Kern stehen die Bilder als KitData.Sprite.HB_<NAME>.

Aufruf: python3 tools/build_hub_icons.py  (schreibt die PNGs neu; build_ui_pack.py liest sie nur)
"""
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "assets", "hub")
INK = (18, 13, 24)


def rgb(h):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def mix(a, b, f):
    return tuple(round(a[i] + (b[i] - a[i]) * f) for i in range(3))


def light(c, f):
    return mix(c, (255, 255, 255), f)


def dark(c, f):
    return mix(c, (0, 0, 0), f)


class Art:
    """Zeichenfläche aus Bereichen: jede Form ist ein Bereich mit Grundfarbe; spätere Formen liegen oben.
    render() schattiert jeden Bereich (Kanten oben/links hell, unten/rechts dunkel, leichter Verlauf) und legt einen
    dunklen Umriss um alles."""

    def __init__(self, size=32):
        self.w = self.h = size
        self.rid = [[0] * size for _ in range(size)]
        self.regions = {}
        self.details = []
        self.after = []
        self.shadow = None

    def _region(self, col, mode, edge):
        rid = len(self.regions) + 1
        self.regions[rid] = (rgb(col) if isinstance(col, str) else col, mode, edge)
        return rid

    def _paint(self, draw_fn, col, mode="full", edge=True):
        m = Image.new("L", (self.w, self.h), 0)
        draw_fn(ImageDraw.Draw(m))
        rid = self._region(col, mode, edge) if col is not None else 0
        px = m.load()
        for y in range(self.h):
            for x in range(self.w):
                if px[x, y] > 127:
                    self.rid[y][x] = rid
        return rid

    # Formen (col=None radiert)
    def poly(self, pts, col, mode="full", edge=True):
        return self._paint(lambda d: d.polygon(pts, fill=255), col, mode, edge)

    def rect(self, x0, y0, x1, y1, col, mode="full", edge=True):
        return self._paint(lambda d: d.rectangle((x0, y0, x1, y1), fill=255), col, mode, edge)

    def ellipse(self, x0, y0, x1, y1, col, mode="full", edge=True):
        return self._paint(lambda d: d.ellipse((x0, y0, x1, y1), fill=255), col, mode, edge)

    def line(self, pts, col, width=1, mode="soft", edge=True):
        return self._paint(lambda d: d.line(pts, fill=255, width=width), col, mode, edge)

    def pixels(self, pts, col, mode="flat"):
        rid = self._region(rgb(col), mode, True)
        for x, y in pts:
            if 0 <= x < self.w and 0 <= y < self.h:
                self.rid[y][x] = rid
        return rid

    def erase(self, fn):
        self._paint(fn, None)

    # Einzelne Pixel nach der Schattierung (Glanzpunkte, Augen, Text)
    def dot(self, x, y, col, a=255):
        self.details.append((x, y, rgb(col) if isinstance(col, str) else col, a))

    def dots(self, pts, col, a=255):
        for x, y in pts:
            self.dot(x, y, col, a)

    def sparkle(self, x, y, size=1, col="FFF6C8"):
        """Funkeln (ohne Umriss): Kreuz mit hellem Kern."""
        c = rgb(col)
        self.after.append((x, y, (255, 255, 255), 255))
        for i in range(1, size + 1):
            a = 255 if i < size else 170
            for dx, dy in ((i, 0), (-i, 0), (0, i), (0, -i)):
                self.after.append((x + dx, y + dy, c, a))

    def ground_shadow(self, x0, y0, x1, y1, a=70):
        self.shadow = (x0, y0, x1, y1, a)

    def render(self):
        w, h = self.w, self.h
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        if self.shadow:
            x0, y0, x1, y1, a = self.shadow
            m = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            ImageDraw.Draw(m).ellipse((x0, y0, x1, y1), fill=(0, 0, 0, a))
            img.alpha_composite(m)
        px = img.load()
        rid = self.rid
        # Ausdehnung je Bereich (für den Verlauf)
        span = {}
        for y in range(h):
            for x in range(w):
                r = rid[y][x]
                if r:
                    lo, hi = span.get(r, (y, y))
                    span[r] = (min(lo, y), max(hi, y))

        def same(x, y, r):
            return 0 <= x < w and 0 <= y < h and rid[y][x] == r

        for y in range(h):
            for x in range(w):
                r = rid[y][x]
                if not r:
                    continue
                base, mode, _edge = self.regions[r]
                if mode == "flat":
                    px[x, y] = base + (255,)
                    continue
                lo, hi = span[r]
                t = (y - lo) / max(1, hi - lo)
                c = mix(light(base, 0.10), dark(base, 0.14), t)
                if mode == "full":
                    if not same(x, y - 1, r):
                        c = light(base, 0.36)
                    elif not same(x - 1, y, r):
                        c = light(base, 0.18)
                    if not same(x, y + 1, r):
                        c = dark(base, 0.32)
                    elif not same(x + 1, y, r):
                        c = dark(base, 0.20)
                px[x, y] = c + (255,)
        # Umriss: leere Pixel neben einem Bereich
        out = []
        for y in range(h):
            for x in range(w):
                if rid[y][x]:
                    continue
                for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < w and 0 <= yy < h and rid[yy][xx] and self.regions[rid[yy][xx]][2]:
                        out.append((x, y, mix(self.regions[rid[yy][xx]][0], INK, 0.82)))
                        break
        for x, y, c in out:
            px[x, y] = c + (255,)
        for x, y, c, a in self.details + self.after:
            if 0 <= x < w and 0 <= y < h:
                if a >= 255:
                    px[x, y] = c + (255,)
                else:
                    o = px[x, y]
                    if o[3] == 0:
                        px[x, y] = c + (a,)
                    else:
                        px[x, y] = mix(o[:3], c, a / 255) + (255,)
        return img


# ---------------------------------------------------------------- Farben
GOLD = "F6C343"
GOLD_D = "C8862A"
WOOD = "A8743E"
WOOD_D = "7A4E26"
IRON = "D6DCE4"
STONE = "E9DFC6"


def coin(a, cx, cy, r=3):
    a.ellipse(cx - r, cy - r, cx + r, cy + r, GOLD)
    a.dot(cx - 1, cy - 1, "FFF4C0")


# ---------------------------------------------------------------- Kacheln (32 px)
def i_menu():
    a = Art()
    a.ellipse(2, 2, 29, 29, GOLD)
    a.ellipse(5, 5, 26, 26, "F4EEDC", mode="soft")
    a.dots([(16, 6), (16, 25), (6, 16), (25, 16)], "8A6A3A")
    a.poly([(16, 7), (18, 15), (14, 15)], "E8483E", mode="soft")
    a.poly([(14, 16), (18, 16), (16, 24)], "4A78D8", mode="soft")
    a.ellipse(14, 14, 17, 17, GOLD)
    a.dot(15, 15, "FFF4C0")
    a.sparkle(26, 4, 2)
    return a


def i_angeln():
    a = Art()
    a.ground_shadow(9, 27, 31, 31)
    a.line([(3, 29), (23, 4)], WOOD, 2, mode="soft")
    a.ellipse(6, 21, 11, 26, "9AA4B4")
    a.dot(8, 23, "E8EEF6")
    a.line([(24, 4), (26, 15)], "E8EEF6", 1, mode="flat", edge=False)
    # Fisch
    a.poly([(11, 22), (15, 18), (15, 27)], "2E8FB8")
    a.ellipse(13, 15, 30, 28, "48C0E0")
    a.poly([(20, 15), (24, 12), (25, 16)], "2E8FB8")
    a.ellipse(17, 21, 27, 26, "BDEFF8", mode="soft", edge=False)
    a.dots([(26, 18), (27, 18)], "FFFFFF")
    a.dot(27, 19, "18202A")
    a.dot(26, 16, "D0D6DE")
    a.sparkle(6, 5, 2, "C8F4FF")
    return a


def i_jobs():
    a = Art()
    a.ground_shadow(4, 26, 28, 31)
    # Axt (hinten): Stiel von rechts unten nach links oben, Klinge links
    a.line([(27, 29), (10, 9)], WOOD, 3)
    a.poly([(3, 2), (8, 4), (12, 8), (13, 10), (9, 14), (4, 15), (2, 10)], IRON)
    a.line([(3, 3), (2, 9)], "FFFFFF", 1, mode="flat", edge=False)
    a.dots([(9, 9), (10, 10)], "8A96A8")
    # Spitzhacke (vorn): Stiel von links unten nach rechts oben, Kopf als Bogen
    a.line([(4, 29), (21, 8)], WOOD, 3)
    a.poly([(10, 3), (15, 2), (21, 3), (26, 6), (29, 11), (30, 17), (27, 15), (24, 10), (20, 7), (15, 5), (10, 5)],
           "62D6E0")
    a.dots([(15, 3), (18, 3), (21, 4), (24, 6)], "D8FAFF")
    a.dots([(6, 26), (7, 25), (25, 26), (24, 25)], WOOD_D)
    a.sparkle(28, 24, 2)
    return a


def i_grundstuecke():
    a = Art()
    a.poly([(2, 17), (16, 24), (16, 30), (2, 23)], "8A5A32")
    a.poly([(16, 24), (30, 17), (30, 23), (16, 30)], "6E4626")
    a.poly([(2, 17), (16, 10), (30, 17), (16, 24)], "6CC048")
    a.dots([(9, 16), (12, 19), (20, 15), (23, 19), (16, 21), (6, 18)], "A2E070")
    a.dots([(7, 21), (11, 25), (21, 25), (25, 21)], "5A3A1E")
    # Zaunpfähle an den Ecken
    for x, y in ((3, 13), (28, 13), (15, 6)):
        a.rect(x, y, x + 1, y + 4, "D9B07A", mode="soft")
    # Fahne
    a.rect(16, 2, 16, 17, "E8DCC0", mode="soft")
    a.poly([(17, 2), (26, 5), (17, 9)], "E84848")
    a.dots([(18, 4), (19, 4)], "FFB0A8")
    a.sparkle(6, 6, 1)
    return a


def i_heiraten():
    a = Art()
    a.ground_shadow(4, 26, 28, 31)
    a.ellipse(2, 11, 19, 28, GOLD)
    a.erase(lambda d: d.ellipse((6, 15, 15, 24), fill=255))
    a.ellipse(12, 11, 29, 28, "F2D06A")
    a.erase(lambda d: d.ellipse((16, 15, 25, 24), fill=255))
    # Stein
    a.poly([(17, 8), (20, 5), (24, 5), (27, 8), (22, 13)], "9EE8FF")
    a.dots([(20, 6), (21, 6), (19, 7)], "FFFFFF")
    # Herz
    a.ellipse(3, 1, 7, 5, "FF5A8A")
    a.ellipse(6, 1, 10, 5, "FF5A8A")
    a.poly([(3, 4), (10, 4), (6, 9)], "FF5A8A", mode="soft")
    a.dot(5, 2, "FFC8D8")
    a.sparkle(29, 3, 2)
    return a


def i_bank():
    a = Art()
    a.ground_shadow(2, 27, 30, 31)
    a.rect(2, 26, 29, 29, "CFC3A6")
    a.rect(4, 24, 27, 26, STONE)
    for x in (5, 11, 18, 24):
        a.rect(x, 13, x + 2, 23, "F4EEDD")
    a.rect(3, 10, 28, 12, STONE)
    a.poly([(2, 10), (16, 2), (29, 10)], "E2D4B0")
    coin(a, 16, 7, 2)
    a.sparkle(28, 3, 2)
    return a


def sword(a, tip, hilt, blade="E4EAF2", guard=GOLD, grip="7A4E26"):
    """Schwert von hilt (Griffansatz) bis tip: Klinge mit Mittelgrat, Parierstange, Griff und Knauf."""
    (tx, ty), (hx, hy) = tip, hilt
    dx, dy = tx - hx, ty - hy
    n = (dx * dx + dy * dy) ** 0.5
    ux, uy = dx / n, dy / n
    px, py = -uy, ux

    def pt(t, s):
        return round(hx + ux * t + px * s), round(hy + uy * t + py * s)

    a.poly([pt(0, -2.2), pt(n - 4, -1.6), pt(n, 0), pt(n - 4, 1.6), pt(0, 2.2)], blade)
    a.line([pt(1, 0), pt(n - 4, 0)], dark(rgb(blade), 0.22), 1, mode="flat", edge=False)
    a.poly([pt(-1, -5.5), pt(1, -5.5), pt(1, 5.5), pt(-1, 5.5)], guard)
    a.poly([pt(-5, -1.2), pt(-1, -1.2), pt(-1, 1.2), pt(-5, 1.2)], grip)
    x, y = pt(-6.5, 0)
    a.ellipse(x - 1.5, y - 1.5, x + 1.5, y + 1.5, guard)


def i_duelle():
    a = Art()
    a.ground_shadow(4, 27, 28, 31)
    sword(a, (28, 3), (9, 22))
    sword(a, (3, 3), (22, 22), blade="F2E6E2", guard="E85A4A", grip="5A3420")
    a.sparkle(15, 12, 3, "FFE070")
    return a


def i_turnier():
    a = Art()
    a.ground_shadow(3, 26, 29, 31)
    a.rect(4, 24, 27, 28, "8A3AC8")
    a.dots([(6, 25), (7, 25)], "C890FF")
    a.poly([(4, 22), (4, 8), (10, 15), (16, 5), (22, 15), (28, 8), (28, 22)], GOLD)
    a.rect(4, 19, 28, 23, GOLD_D)
    a.ellipse(14, 18, 18, 22, "E84848")
    a.ellipse(7, 19, 10, 22, "48A8E8")
    a.ellipse(22, 19, 25, 22, "48D888")
    for x, y in ((4, 7), (16, 4), (28, 7)):
        a.ellipse(x - 1, y - 1, x + 1, y + 1, "FFF0A0")
    a.sparkle(10, 3, 2)
    return a


def i_versicherung():
    a = Art()
    a.ground_shadow(5, 27, 27, 31)
    a.poly([(4, 3), (16, 1), (28, 3), (28, 15), (16, 29), (4, 15)], GOLD)
    a.poly([(7, 5), (16, 4), (25, 5), (25, 15), (16, 25), (7, 15)], "3A78D8")
    a.poly([(16, 4), (25, 5), (25, 15), (16, 25)], "2C5EB4", mode="soft", edge=False)
    a.line([(10, 14), (14, 18), (22, 9)], "FFFFFF", 3, mode="flat", edge=False)
    a.sparkle(28, 22, 2)
    return a


def i_shop():
    a = Art()
    a.ground_shadow(4, 27, 28, 31)
    a.ellipse(9, 3, 23, 15, "8A4A2A")
    a.erase(lambda d: d.ellipse((11, 5, 21, 15), fill=255))
    a.poly([(5, 11), (27, 11), (29, 29), (3, 29)], "F07A3A")
    a.rect(5, 11, 27, 13, "FFB070", mode="soft")
    # Smaragd auf der Tasche
    a.poly([(16, 15), (21, 19), (16, 26), (11, 19)], "3AD86A")
    a.dots([(15, 17), (14, 18), (15, 18)], "C8FFD8")
    a.sparkle(27, 4, 2)
    return a


def i_markt():
    a = Art()
    a.ground_shadow(2, 27, 30, 31)
    a.rect(4, 11, 5, 28, WOOD_D)
    a.rect(26, 11, 27, 28, WOOD_D)
    a.rect(2, 19, 29, 28, WOOD)
    for x in (9, 16, 23):
        a.line([(x, 21), (x, 27)], WOOD_D, 1, mode="flat", edge=False)
    # Markise: Trapez in 6 Bahnen, abwechselnd rot und weiß, unten Bögen
    bands = 6
    for i in range(bands):
        t0, t1 = 5 + i * 21 / bands, 5 + (i + 1) * 21 / bands
        b0, b1 = 1 + i * 29 / bands, 1 + (i + 1) * 29 / bands
        col = "E84848" if i % 2 == 0 else "F4F0E8"
        a.poly([(round(t0), 3), (round(t1) - 1, 3), (round(b1) - 1, 10), (round(b0), 10)], col, mode="soft")
        a.ellipse(round(b0), 8, round(b1) - 1, 12, col)
    # Waren
    a.ellipse(5, 15, 10, 20, "E83A3A")
    a.ellipse(9, 14, 14, 19, "F05A3A")
    a.ellipse(17, 14, 25, 20, "58B848")
    a.dots([(6, 16), (10, 15)], "FFC0B0")
    a.dots([(19, 15), (21, 16), (23, 17)], "2E7A2A")
    a.sparkle(28, 15, 1)
    return a


def i_wirtschaft():
    a = Art()
    a.ground_shadow(2, 27, 30, 31)
    for x, top in ((3, 18), (12, 9), (21, 14)):
        for y in range(27, top - 1, -3):
            a.ellipse(x, y - 2, x + 8, y + 2, GOLD)
            a.dots([(x + 2, y - 1), (x + 3, y - 1)], "FFF4C0")
    # Krone über dem höchsten Stapel
    a.poly([(11, 7), (11, 2), (13, 4), (16, 0), (19, 4), (21, 2), (21, 7)], "FFD84A")
    a.dot(16, 4, "E84848")
    a.sparkle(28, 6, 2)
    return a


def i_boerse():
    a = Art()
    a.ground_shadow(3, 27, 29, 31)
    a.rect(8, 22, 9, 28, "6A7486")
    a.rect(22, 22, 23, 28, "6A7486")
    a.rect(1, 3, 30, 23, "4A5468")
    a.rect(3, 5, 28, 21, "182638", mode="flat")
    for y in (9, 13, 17):
        a.line([(4, y), (27, y)], "24364E", 1, mode="flat", edge=False)
    # Kerzen
    for x, y0, y1, col in ((5, 14, 19, "E84848"), (9, 12, 17, "3AD86A"), (13, 13, 18, "E84848"), (17, 9, 15, "3AD86A"),
                           (21, 10, 13, "E84848"), (25, 6, 11, "3AD86A")):
        a.line([(x + 1, y0 - 1), (x + 1, y1 + 1)], "8A96AA", 1, mode="flat", edge=False)
        a.rect(x, y0, x + 2, y1, col, mode="soft", edge=False)
    # Kurs-Linie mit Pfeil
    a.line([(4, 18), (10, 14), (14, 16), (20, 10), (26, 6)], "FFE070", 1, mode="flat", edge=False)
    a.dots([(24, 5), (25, 5), (26, 5), (26, 6), (26, 7)], "FFE070")
    a.sparkle(29, 26, 1)
    return a


def i_tagespot():
    a = Art()
    a.ground_shadow(3, 27, 29, 31)
    # Münzberg
    a.ellipse(6, 6, 26, 18, GOLD)
    for x, y in ((9, 9), (14, 7), (19, 8), (12, 12), (17, 11), (22, 12)):
        a.dots([(x, y), (x + 1, y)], "FFF4C0")
        a.dot(x, y + 1, GOLD_D)
    # Kessel
    a.ellipse(4, 13, 28, 30, "3C3848")
    a.ellipse(3, 11, 29, 17, "56506A")
    a.erase(lambda d: d.rectangle((0, 0, 1, 1), fill=255))
    a.ellipse(6, 12, 26, 16, GOLD, mode="soft")
    a.rect(4, 28, 7, 30, "3C3848")
    a.rect(25, 28, 28, 30, "3C3848")
    # Kleeblatt
    for x, y in ((24, 2), (27, 4), (24, 6), (21, 4)):
        a.ellipse(x - 2, y - 2, x + 1, y + 1, "4AC860")
    a.dot(24, 4, "A8F0A0")
    a.sparkle(5, 4, 2)
    return a


def i_duo_rtp():
    a = Art()
    a.ground_shadow(3, 26, 29, 31)
    # Würfel (iso)
    a.poly([(3, 11), (16, 17), (16, 30), (3, 24)], "D83838")
    a.poly([(16, 17), (29, 11), (29, 24), (16, 30)], "A82828")
    a.poly([(3, 11), (16, 4), (29, 11), (16, 17)], "F05050")
    a.dots([(15, 10), (16, 10), (15, 11), (16, 11)], "FFFFFF")
    for x, y in ((6, 15), (12, 23), (9, 19)):
        a.dots([(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)], "F8F0F0")
    for x, y in ((19, 22), (25, 16), (19, 16), (25, 22)):
        a.dots([(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)], "E8D8D8")
    # Blitz
    a.poly([(25, 0), (21, 6), (24, 6), (22, 10), (28, 4), (25, 4), (27, 0)], "FFE04A")
    a.sparkle(4, 4, 2, "C8E8FF")
    return a


def i_spielzeit():
    a = Art()
    a.ground_shadow(6, 27, 26, 31)
    a.rect(6, 2, 25, 4, GOLD)
    a.rect(6, 26, 25, 28, GOLD)
    a.rect(7, 5, 8, 25, WOOD)
    a.rect(23, 5, 24, 25, WOOD)
    a.poly([(10, 5), (21, 5), (21, 9), (17, 15), (17, 16), (21, 21), (21, 25), (10, 25), (10, 21), (14, 16), (14, 15),
            (10, 9)], "CDEFFF", mode="soft")
    a.poly([(12, 8), (19, 8), (16, 13), (15, 13)], "F2B544", mode="soft", edge=False)
    a.poly([(15, 19), (16, 19), (20, 23), (20, 25), (11, 25), (11, 23)], "F2B544", mode="soft", edge=False)
    a.line([(15, 14), (15, 22)], "F2B544", 1, mode="flat", edge=False)
    a.dots([(11, 6), (11, 7), (11, 21), (11, 22)], "FFFFFF")
    a.sparkle(28, 9, 2)
    return a


def i_titel():
    a = Art()
    a.ground_shadow(3, 26, 29, 31)
    a.poly([(1, 14), (7, 14), (7, 24), (1, 24), (4, 19)], "A82E5A")
    a.poly([(30, 14), (24, 14), (24, 24), (30, 24), (27, 19)], "A82E5A")
    a.rect(5, 10, 26, 21, "E8487E")
    a.rect(5, 10, 26, 11, GOLD, mode="soft")
    a.rect(5, 20, 26, 21, GOLD_D, mode="soft")
    a.poly([(15, 12), (16, 12), (17, 14), (19, 15), (17, 16), (18, 19), (16, 17), (14, 19), (14, 16), (12, 15), (14, 14)],
           "FFE070")
    a.sparkle(26, 4, 2)
    a.sparkle(5, 4, 1)
    return a


def i_kosmetik():
    a = Art()
    a.ground_shadow(3, 26, 29, 31)
    a.ellipse(2, 21, 29, 28, "4A3A62")
    a.rect(8, 4, 23, 24, "3A2C50")
    a.ellipse(8, 2, 23, 6, "52406E")
    a.rect(8, 17, 23, 21, "E8487E")
    a.dots([(10, 18), (11, 18)], "FFB0C8")
    a.dots([(10, 7), (10, 8), (10, 9), (10, 10)], "6A5A88")
    a.sparkle(26, 8, 2, "FFD0F0")
    a.sparkle(4, 13, 1, "FFD0F0")
    return a


def i_haustiere():
    a = Art()
    a.ground_shadow(6, 27, 26, 31)
    a.ellipse(2, 9, 8, 16, "E89A58")
    a.ellipse(8, 3, 15, 11, "E89A58")
    a.ellipse(17, 3, 24, 11, "E89A58")
    a.ellipse(23, 9, 29, 16, "E89A58")
    a.poly([(16, 13), (21, 15), (25, 20), (25, 25), (21, 28), (16, 26), (11, 28), (7, 25), (7, 20), (11, 15)], "E89A58")
    a.ellipse(12, 18, 20, 25, "FFB8C8", mode="soft", edge=False)
    a.dots([(4, 11), (10, 5), (19, 5), (25, 11)], "FFE0C0")
    a.sparkle(28, 26, 2, "FFD0E0")
    return a


# ---------------------------------------------------------------- Knöpfe (16 px)
def s_wildnis():
    a = Art(16)
    a.rect(7, 12, 8, 15, WOOD)
    a.poly([(8, 0), (14, 8), (2, 8)], "3AA850")
    a.poly([(8, 4), (15, 13), (1, 13)], "2E9444")
    a.dots([(7, 3), (6, 8), (5, 9)], "8AE090")
    return a


def s_homes():
    a = Art(16)
    a.rect(3, 8, 12, 14, "F0E2C4")
    a.poly([(0, 8), (7, 1), (8, 1), (15, 8)], "E05040")
    a.rect(6, 10, 8, 14, "8A5A32")
    a.rect(10, 9, 11, 10, "8ACCF0", mode="flat", edge=False)
    return a


def s_enderchest():
    a = Art(16)
    a.rect(1, 3, 14, 14, "1E3A36")
    a.rect(1, 3, 14, 7, "28504A")
    a.rect(6, 7, 9, 10, "48E0A8", mode="soft")
    a.dots([(7, 8), (8, 8)], "C8FFE8")
    a.dots([(3, 5), (4, 5)], "5A8A84")
    return a


TILES = [("menu", i_menu), ("angeln", i_angeln), ("jobs", i_jobs), ("grundstuecke", i_grundstuecke),
         ("heiraten", i_heiraten), ("bank", i_bank), ("duelle", i_duelle), ("turnier", i_turnier),
         ("versicherung", i_versicherung), ("shop", i_shop), ("markt", i_markt), ("wirtschaft", i_wirtschaft),
         ("boerse", i_boerse), ("tagespot", i_tagespot), ("duo_rtp", i_duo_rtp), ("spielzeit", i_spielzeit),
         ("titel", i_titel), ("kosmetik", i_kosmetik), ("haustiere", i_haustiere),
         ("s_wildnis", s_wildnis), ("s_homes", s_homes), ("s_enderchest", s_enderchest)]


def trim(img):
    box = img.getchannel("A").getbbox()
    return img.crop(box) if box else img


def build():
    os.makedirs(OUT, exist_ok=True)
    names = []
    for name, fn in TILES:
        img = trim(fn().render())
        img.save(os.path.join(OUT, "art_%s.png" % name))
        names.append(name)
    # Spieler-Warps: Portal aus den Referenzen, auf 32 px Höhe gekürzt (oben und unten nur Funkeln)
    src = os.path.join(HERE, "assets", "pwarps", "art_portal.png")
    if os.path.exists(src):
        p = Image.open(src).convert("RGBA")
        p.crop((0, 2, p.width, p.height - 2)).save(os.path.join(OUT, "art_portal.png"))
        names.append("portal")
    return names


if __name__ == "__main__":
    print("ok Menü-Icons:", ", ".join(build()))
