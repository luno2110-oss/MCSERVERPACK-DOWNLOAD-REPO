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


# Premium-Schattierung wie die Referenz-Icons: Licht warm (gelblich-weiß), Schatten kühl (violett-dunkel), satte Farben
WARM = (255, 244, 206)
COOL = (46, 24, 72)


def saturate(c, f):
    import colorsys
    h, s, v = colorsys.rgb_to_hsv(*(x / 255 for x in c))
    r, g, b = colorsys.hsv_to_rgb(h, min(1.0, s * f), v)
    return round(r * 255), round(g * 255), round(b * 255)


def hl(c, f):
    return mix(c, WARM, f)


def sh(c, f):
    return mix(c, COOL, f)


class Art:
    """Zeichenfläche aus Bereichen: jede Form ist ein Bereich mit Grundfarbe; spätere Formen liegen oben.
    render() schattiert jeden Bereich (Kanten oben/links hell, unten/rechts dunkel, leichter Verlauf) und legt einen
    dunklen Umriss um alles."""

    def __init__(self, size=32, premium=False):
        self.premium = premium
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
        if self.premium:
            return self._render_premium()
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

        # Fläche: Verlauf von oben hell nach unten dunkel, Kanten oben/links hell, unten/rechts dunkel (wie die
        # Referenz-Grafiken), Schatten unter Teilen, die darüber liegen (später gezeichnete Bereiche)
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
                c = mix(light(base, 0.16), dark(base, 0.22), t)
                if mode == "full":
                    if not same(x, y - 1, r):
                        c = light(base, 0.46)
                    elif not same(x - 1, y, r):
                        c = light(base, 0.24)
                    if not same(x, y + 1, r):
                        c = dark(base, 0.42)
                    elif not same(x + 1, y, r):
                        c = dark(base, 0.28)
                    above = rid[y - 1][x] if y > 0 else 0
                    if above > r and same(x, y + 1, r):
                        c = dark(c, 0.28)
                px[x, y] = c + (255,)
        # Glanzpunkt oben links in größeren Flächen
        cells = {}
        for y in range(h):
            for x in range(w):
                r = rid[y][x]
                if r and self.regions[r][1] == "full":
                    cells.setdefault(r, []).append((x, y))
        for r, pts in cells.items():
            if len(pts) < 30:
                continue
            base = self.regions[r][0]
            inner = [(x, y) for x, y in pts if all(same(x + dx, y + dy, r) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (-1, -1)))]
            if not inner:
                continue
            gx, gy = min(inner, key=lambda q: (q[1] + q[0] * 0.8))
            for dx, dy, f in ((0, 0, 0.62), (1, 0, 0.45), (0, 1, 0.38)):
                if same(gx + dx, gy + dy, r):
                    px[gx + dx, gy + dy] = light(base, f) + (255,)
        # Umriss: fast schwarz, leicht in der Farbe des Nachbarn
        out = []
        for y in range(h):
            for x in range(w):
                if rid[y][x]:
                    continue
                for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < w and 0 <= yy < h and rid[yy][xx] and self.regions[rid[yy][xx]][2]:
                        out.append((x, y, mix(self.regions[rid[yy][xx]][0], INK, 0.9)))
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


    def _render_premium(self):
        """Wie die Referenz-Icons: satte Grundfarbe, oben/links warmes Licht, unten/rechts kühler Schatten, Glanzfleck,
        dunkle Trennlinie dort, wo ein Teil über einem anderen liegt, und eine fast schwarze Outline außen."""
        w, h = self.w, self.h
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        if self.shadow:
            x0, y0, x1, y1, a = self.shadow
            m = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            ImageDraw.Draw(m).ellipse((x0, y0, x1, y1), fill=(0, 0, 0, a))
            img.alpha_composite(m)
        px = img.load()
        rid = self.rid
        span = {}
        for y in range(h):
            for x in range(w):
                r = rid[y][x]
                if r:
                    lo, hi = span.get(r, (y, y))
                    span[r] = (min(lo, y), max(hi, y))

        def at(x, y):
            return rid[y][x] if 0 <= x < w and 0 <= y < h else 0

        base_of = {r: saturate(v[0], 1.18) for r, v in self.regions.items()}
        for y in range(h):
            for x in range(w):
                r = rid[y][x]
                if not r:
                    continue
                base, mode, edge = base_of[r], self.regions[r][1], self.regions[r][2]
                if mode == "flat":
                    px[x, y] = base + (255,)
                    continue
                lo, hi_ = span[r]
                t = (y - lo) / max(1, hi_ - lo)
                c = mix(hl(base, 0.22), sh(base, 0.30), t)
                if mode == "full":
                    if at(x, y - 1) != r:
                        c = hl(base, 0.62)
                    elif at(x - 1, y) != r:
                        c = hl(base, 0.34)
                    if at(x, y + 1) != r:
                        c = sh(dark(base, 0.25), 0.45)
                    elif at(x + 1, y) != r:
                        c = sh(base, 0.42)
                    # Trennlinie: ein späteres Teil mit Kante liegt direkt daneben
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        o = at(x + dx, y + dy)
                        if o > r and self.regions[o][2] and self.regions[o][1] != "flat":
                            c = mix(sh(base, 0.5), INK, 0.62)
                            break
                px[x, y] = c + (255,)
        # Glanzfleck oben links in größeren Flächen
        cells = {}
        for y in range(h):
            for x in range(w):
                r = rid[y][x]
                if r and self.regions[r][1] == "full":
                    cells.setdefault(r, []).append((x, y))
        for r, pts in cells.items():
            if len(pts) < 24:
                continue
            base = base_of[r]
            inner = [(x, y) for x, y in pts if all(at(x + dx, y + dy) == r for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (-1, -1), (1, 1)))]
            if not inner:
                continue
            gx, gy = min(inner, key=lambda q: (q[1] + q[0] * 0.8))
            for dx, dy, f in ((0, 0, 0.85), (1, 0, 0.6), (0, 1, 0.5), (2, 0, 0.35)):
                if at(gx + dx, gy + dy) == r:
                    px[gx + dx, gy + dy] = hl(base, f) + (255,)
        # Outline außen: fast schwarz
        out = []
        for y in range(h):
            for x in range(w):
                if rid[y][x]:
                    continue
                for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0)):
                    o = at(x + dx, y + dy)
                    if o and self.regions[o][2]:
                        out.append((x, y, mix(sh(base_of[o], 0.6), INK, 0.88)))
                        break
        for x, y, c in out:
            px[x, y] = c + (255,)
        for x, y, c, a in self.details + self.after:
            if 0 <= x < w and 0 <= y < h:
                if a >= 255:
                    px[x, y] = c + (255,)
                else:
                    o = px[x, y]
                    px[x, y] = (c + (a,)) if o[3] == 0 else (mix(o[:3], c, a / 255) + (255,))
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


def i_insel():
    a = Art()
    a.poly([(3, 15), (29, 15), (23, 21), (17, 29), (15, 29), (9, 21)], "8A5A32")
    a.poly([(9, 19), (14, 19), (13, 24)], "7A7A86", mode="soft", edge=False)
    a.poly([(19, 19), (24, 18), (19, 25)], "7A7A86", mode="soft", edge=False)
    a.ellipse(2, 11, 30, 19, "6CC048")
    a.dots([(6, 14), (10, 13), (14, 15), (24, 14), (27, 15)], "A2E070")
    a.rect(21, 5, 22, 14, WOOD)
    a.ellipse(16, 0, 27, 9, "3AA850")
    a.dots([(18, 2), (19, 2), (17, 3)], "8AE090")
    a.rect(6, 10, 10, 13, "B07A3A")
    a.rect(6, 10, 10, 10, "8A5A2A", mode="flat", edge=False)
    a.dot(8, 11, GOLD)
    a.ellipse(26, 23, 30, 26, "8A5A32")
    a.sparkle(4, 4, 2, "E0F4FF")
    a.sparkle(29, 30, 1, "E0F4FF")
    return a


def i_generator():
    a = Art()
    a.ground_shadow(4, 27, 28, 31)
    a.rect(6, 6, 25, 25, "8A8A94")
    for (x, y) in ((9, 9), (15, 12), (20, 8), (11, 18), (19, 19)):
        a.rect(x, y, x + 2, y + 1, "6A6A74", mode="flat", edge=False)
    a.rect(12, 9, 14, 11, "F2C443", mode="soft", edge=False)
    a.rect(18, 14, 20, 16, "62D6E0", mode="soft", edge=False)
    a.rect(8, 20, 10, 22, "E84848", mode="soft", edge=False)
    a.ellipse(0, 12, 6, 20, "FF8A1E")
    a.dots([(2, 14), (2, 15)], "FFE070")
    a.ellipse(26, 12, 31, 20, "3A8AE8")
    a.dots([(28, 14), (28, 15)], "C8E8FF")
    a.sparkle(26, 3, 2)
    return a


def i_groesse():
    a = Art()
    a.rect(8, 8, 23, 23, "6CC048")
    a.dots([(11, 11), (15, 14), (19, 12), (12, 19), (20, 20)], "A2E070")
    for poly in ([(16, 0), (21, 5), (11, 5)], [(16, 31), (21, 26), (11, 26)], [(0, 16), (5, 11), (5, 21)], [(31, 16), (26, 11), (26, 21)]):
        a.poly(poly, "FFD84A")
    a.sparkle(27, 4, 1)
    return a


def i_mitglieder():
    a = Art()
    a.ground_shadow(3, 27, 29, 31)
    # hinten: zweiter Kopf
    a.rect(15, 4, 28, 17, "E8B48A")
    a.rect(15, 4, 28, 8, "C88A3A")
    a.dots([(18, 11), (19, 11), (24, 11), (25, 11)], "3A5AC8")
    a.rect(14, 18, 29, 27, "3AA850")
    # vorn: erster Kopf
    a.rect(3, 8, 16, 21, "D8A07A")
    a.rect(3, 8, 16, 11, "5A3A1E")
    a.dots([(6, 15), (7, 15), (12, 15), (13, 15)], "FFFFFF")
    a.dots([(7, 15), (13, 15)], "3A2A6A")
    a.rect(2, 22, 17, 30, "3A8AE8")
    a.sparkle(29, 2, 1)
    return a


class _ImgArt:
    """Fertiges Bild als Kachel (gleiche Schnittstelle wie Art: render())."""

    def __init__(self, img):
        self.img = img

    def render(self):
        return self.img


def iso_block(top, side_l, side_r, pal, outline="1A1108", shade=(1.0, 0.86, 0.64), size=32):
    """Pixelgenauer Iso-Würfel (2:1): Oberseite und zwei Seiten aus 7 x 7 Texturen (Zeichen -> Farbe), jeder
    Texel ist oben eine 4 x 2-Raute und seitlich 2 px breit und 2 px hoch. Licht: oben hell, links mittel, rechts
    dunkel, nach unten etwas dunkler; helle Kanten oben, dunkle Mittelnaht, dunkle Kontur."""
    T, L, R, B = (16, 2), (2, 9), (30, 9), (16, 16)
    H = 14
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    face = {}

    def inv(o, a, b, x, y):
        # (x, y) = o + u * a + v * b  ->  (u, v)
        det = a[0] * b[1] - a[1] * b[0]
        dx, dy = x - o[0], y - o[1]
        return (dx * b[1] - dy * b[0]) / det, (a[0] * dy - a[1] * dx) / det

    for y in range(size):
        for x in range(size):
            cx, cy = x + 0.5, y + 0.5
            u, v = inv(T, (R[0] - T[0], R[1] - T[1]), (L[0] - T[0], L[1] - T[1]), cx, cy)
            if 0 <= u < 1 and 0 <= v < 1:
                face[(x, y)] = ("top", u, v)
                continue
            u, v = inv(L, (B[0] - L[0], B[1] - L[1]), (0, H), cx, cy)
            if 0 <= u < 1 and 0 <= v < 1:
                face[(x, y)] = ("left", u, v)
                continue
            u, v = inv(B, (R[0] - B[0], R[1] - B[1]), (0, H), cx, cy)
            if 0 <= u < 1 and 0 <= v < 1:
                face[(x, y)] = ("right", u, v)
    tex = {"top": top, "left": side_l, "right": side_r}
    for (x, y), (f, u, v) in face.items():
        t = tex[f]
        n = len(t)
        ch = t[min(n - 1, int(v * n))][min(n - 1, int(u * n))]
        r, g, b = rgb(pal[ch])
        k = shade[0] if f == "top" else shade[1] if f == "left" else shade[2]
        if f == "top":
            k *= 1.04 - 0.08 * (u + v) / 2          # hinten etwas heller
            if u < 0.08 or v < 0.08:
                k *= 1.16                           # helle Kanten hinten oben
        else:
            k *= 1.0 - 0.16 * v                     # nach unten dunkler (Schatten)
            if f == "right" and u < 0.07:
                k *= 0.78                           # Mittelnaht
            if f == "left" and u > 0.93:
                k *= 0.92
            if v < 0.07:
                k *= 1.12                           # Kante unter dem Gras
        px[x, y] = (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), 255)
    # Kontur außen
    oc = rgb(outline)
    edge = []
    for y in range(size):
        for x in range(size):
            if (x, y) in face:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + dy) in face:
                    edge.append((x, y))
                    break
    for (x, y) in edge:
        px[x, y] = oc + (255,)
    return img


def i_oneblock():
    """One-Block: symmetrischer Grasblock, klare 7 x 7-Texturen, Licht und Schatten je Seite, dunkle Kontur."""
    pal = {"g": "62BE3E", "G": "84DA58", "d": "4DA22F", "h": "B0F282", "f": "56AE34", "F": "6CC646",
           "D": "8F5D35", "e": "AC7748", "k": "6A4224"}
    top = ["gGgggdg",
           "gggGggg",
           "dgggggG",
           "gggdggg",
           "gGgggGg",
           "ggggdgg",
           "Gggggg" + "g"]
    side = ["FFFFFFF",
            "fffffff",
            "fDffDff",
            "DDfDDkf",
            "DkDDeDD",
            "eDDkDDD",
            "DDeDDkD"]
    img = iso_block(top, side, side, pal, shade=(1.0, 0.84, 0.62))
    # kleines Funkeln oben rechts (wie die anderen Premium-Icons)
    px = img.load()
    for (x, y, c) in ((28, 1, "FFFFFF"), (28, 0, "FFF6C8"), (28, 2, "FFF6C8"), (27, 1, "FFF6C8"), (29, 1, "FFF6C8")):
        if px[x, y][3] == 0:
            px[x, y] = rgb(c) + (255,)
    return _ImgArt(img)


def ob_glueck():
    """Glück: goldenes Medaillon mit vierblättrigem Kleeblatt."""
    a = Art(premium=True)
    a.ellipse(1, 1, 30, 30, "E8A82A")
    a.ellipse(4, 4, 27, 27, "FFD24A")
    for cx, cy in ((12, 11), (19, 11), (12, 18), (19, 18)):
        a.ellipse(cx - 5, cy - 5, cx + 4, cy + 4, "3CC650")
    a.ellipse(13, 12, 18, 17, "2E9E40")
    a.line([(16, 18), (20, 25)], "2E8A38", 2)
    a.dots([(9, 8), (17, 8), (9, 15), (17, 15)], "A8F0A0")
    a.dots([(6, 7), (7, 6)], "FFF8D8")
    return a


def ob_cooldown():
    """Cooldown: Sanduhr mit Holzrahmen und Säulen, goldener Sand."""
    a = Art(premium=True)
    a.rect(3, 1, 28, 5, "A86A34")
    a.rect(3, 26, 28, 30, "A86A34")
    a.rect(4, 6, 6, 25, "8A5428")
    a.rect(25, 6, 27, 25, "8A5428")
    a.poly([(8, 6), (23, 6), (18, 15), (18, 16), (23, 25), (8, 25), (13, 16), (13, 15)], "CFE8F8")
    a.poly([(10, 8), (21, 8), (16, 14)], "FFC93A", mode="soft", edge=False)
    a.poly([(16, 19), (21, 24), (10, 24)], "FFC93A", mode="soft", edge=False)
    a.rect(15, 15, 16, 19, "FFC93A", mode="flat", edge=False)
    a.dots([(10, 7), (10, 9), (11, 23)], "FFFFFF")
    a.dots([(5, 2), (6, 2)], "E8B880")
    return a


def ob_tempo():
    """Tempo: Diamant-Spitzhacke mit Blitz."""
    a = Art(premium=True)
    a.line([(5, 29), (21, 10)], "8A5A2E", 3)
    a.poly([(7, 6), (14, 2), (23, 3), (30, 9), (31, 18), (25, 11), (17, 7)], "4ED8E8")
    a.dots([(13, 3), (15, 3), (23, 4)], "E0FFFF")
    a.rect(4, 27, 6, 29, "6A4222")
    a.poly([(26, 17), (20, 25), (24, 25), (21, 31), (29, 22), (25, 22), (28, 17)], "FFD83A")
    return a


def ob_xp():
    """XP-Bonus: große Erfahrungskugel mit Ringen und kleinen Kugeln."""
    a = Art(premium=True)
    a.ellipse(3, 3, 26, 26, "4CC832")
    a.ellipse(6, 6, 23, 23, "8CF04A")
    a.ellipse(10, 10, 19, 19, "D8FF8A", mode="soft")
    a.ellipse(12, 12, 16, 16, "FFFFE8", mode="flat", edge=False)
    a.ellipse(22, 20, 30, 28, "4CC832")
    a.ellipse(24, 22, 28, 26, "B8FF6A", mode="soft", edge=False)
    a.ellipse(24, 1, 30, 7, "4CC832")
    a.dot(26, 3, "E8FFC0")
    a.dots([(8, 8), (9, 7)], "FFFFFF")
    return a


def ob_geld():
    """Geld-Bonus: Münzstapel mit geprägter Münze davor."""
    a = Art(premium=True)
    for y in (24, 20, 16, 12):
        a.ellipse(2, y, 17, y + 6, "E8A82A")
    a.ellipse(2, 9, 17, 15, "FFD24A")
    for y in (25, 21):
        a.ellipse(17, y, 30, y + 6, "E8A82A")
    a.ellipse(17, 18, 30, 24, "FFD24A")
    a.ellipse(12, 2, 30, 20, "E8A82A")
    a.ellipse(15, 5, 27, 17, "FFD24A")
    a.rect(20, 7, 22, 15, "C8841E", mode="flat", edge=False)
    a.rect(18, 8, 24, 9, "C8841E", mode="flat", edge=False)
    a.rect(18, 13, 24, 14, "C8841E", mode="flat", edge=False)
    a.dots([(16, 7), (17, 6)], "FFF8D8")
    return a


def ob_beute():
    """Doppelte Beute: offene Schatztruhe voller Gold mit Edelsteinen."""
    a = Art(premium=True)
    a.poly([(4, 4), (27, 4), (29, 13), (2, 13)], "7A4824")
    a.rect(3, 13, 28, 29, "9A5E2E")
    a.ellipse(4, 9, 27, 18, "FFD24A")
    a.ellipse(7, 7, 14, 13, "FFE070")
    a.ellipse(16, 6, 24, 12, "FFE070")
    a.rect(3, 18, 28, 20, "8A94A4")
    a.rect(14, 18, 17, 24, "C8D2E0")
    a.rect(3, 13, 5, 29, "8A94A4")
    a.rect(26, 13, 28, 29, "8A94A4")
    a.ellipse(20, 8, 25, 13, "3AD86A")
    a.dot(21, 9, "D8FFE0")
    a.ellipse(8, 9, 12, 13, "E84848")
    a.dot(9, 10, "FFC8C8")
    a.dot(15, 21, "3A3A44")
    return a


# ---------------------------------------------------------------- Insel-Extras (premium)
def is_wachstum():
    """Wachstum: Keimling mit zwei Blättern auf einem Grasblock, goldener Pfeil nach oben."""
    a = Art(premium=True)
    a.rect(3, 21, 22, 30, "9A6438")
    a.rect(3, 19, 22, 23, "62C040")
    a.dots([(5, 24), (9, 25), (14, 24), (19, 25)], "58B83C")
    a.dots([(6, 27), (12, 28), (18, 27)], "7A4C2A")
    a.line([(12, 19), (12, 9)], "3E9A30", 2)
    a.poly([(12, 13), (4, 10), (2, 5), (8, 5), (12, 10)], "5CD04A")
    a.poly([(13, 10), (17, 3), (23, 2), (22, 8), (14, 13)], "48B83A")
    a.dots([(5, 7), (19, 4)], "C8FFA0")
    a.poly([(26, 2), (31, 9), (28, 9), (28, 20), (24, 20), (24, 9), (21, 9)], "FFD24A")
    a.dots([(26, 4), (25, 10), (25, 11)], "FFF6C8")
    return a


def is_spawn():
    """Spawnrate: zwei Spawn-Eier (vorn groß, hinten klein) mit Flecken."""
    a = Art(premium=True)
    a.ellipse(17, 2, 29, 18, "D86A4A")
    a.dots([(21, 6), (25, 10), (22, 13)], "8A2E2A")
    a.ellipse(3, 8, 20, 30, "48B8A8")
    a.ellipse(7, 12, 11, 16, "2E6E5A", mode="soft", edge=False)
    a.ellipse(13, 18, 17, 22, "2E6E5A", mode="soft", edge=False)
    a.ellipse(7, 23, 10, 26, "2E6E5A", mode="soft", edge=False)
    a.dots([(7, 10), (8, 10)], "D8FFF4")
    a.sparkle(28, 25, 2, "FFF6C8")
    return a


def is_limits():
    """Limits: Trichter mit rotem Grenze-Zeichen."""
    a = Art(premium=True)
    a.rect(2, 3, 27, 9, "6E727C")
    a.rect(5, 5, 24, 7, "34363C", mode="flat", edge=False)
    a.poly([(4, 10), (25, 10), (20, 19), (9, 19)], "8A8E98")
    a.rect(11, 19, 18, 23, "6E727C")
    a.rect(13, 23, 16, 27, "5A5E66")
    a.dots([(4, 4), (5, 4), (6, 11), (7, 11)], "C8CCD6")
    a.ellipse(19, 18, 31, 30, "E83A3A")
    a.rect(21, 23, 29, 25, "FFFFFF", mode="flat", edge=False)
    return a


def is_pokal():
    """Pokal: goldener Pokal mit Henkeln, Sockel und Stern."""
    a = Art(premium=True)
    a.ellipse(1, 5, 9, 15, "E8A82A")
    a.erase(lambda d: d.ellipse((4, 8, 7, 12), fill=255))
    a.ellipse(22, 5, 30, 15, "E8A82A")
    a.erase(lambda d: d.ellipse((24, 8, 27, 12), fill=255))
    a.poly([(6, 2), (25, 2), (24, 10), (20, 17), (11, 17), (7, 10)], "FFD24A")
    a.rect(13, 17, 18, 22, "E8A82A")
    a.rect(8, 22, 23, 25, "C8841E")
    a.rect(6, 26, 25, 30, "8A5A2E")
    a.poly([(15, 5), (17, 9), (21, 9), (18, 12), (19, 15), (15, 13), (12, 15), (13, 12), (10, 9), (14, 9)], "FFF6C8", mode="soft")
    a.dots([(8, 4), (9, 4)], "FFFFFF")
    return a


def is_besuch():
    """Besuchen: Wegweiser aus Holz mit zwei Pfeilschildern."""
    a = Art(premium=True)
    a.rect(14, 2, 17, 30, "8A5A2E")
    a.poly([(4, 5), (24, 5), (29, 10), (24, 15), (4, 15)], "D8A060")
    a.poly([(27, 17), (8, 17), (3, 22), (8, 27), (27, 27)], "C08848")
    a.line([(7, 9), (20, 9)], "8A5A2E", 1, mode="flat", edge=False)
    a.line([(7, 11), (17, 11)], "8A5A2E", 1, mode="flat", edge=False)
    a.line([(11, 21), (24, 21)], "7A4C26", 1, mode="flat", edge=False)
    a.line([(14, 23), (24, 23)], "7A4C26", 1, mode="flat", edge=False)
    a.rect(12, 29, 19, 31, "6CC048")
    a.dots([(5, 6), (6, 6)], "FFE8C0")
    return a


def is_biom():
    """Biom-Insel: schwebende Insel, links Gras mit Kirschbaum, rechts Sand mit Kaktus."""
    a = Art(premium=True)
    a.poly([(3, 18), (29, 18), (23, 24), (17, 31), (14, 31), (8, 24)], "8A5A32")
    a.poly([(10, 22), (14, 22), (13, 27)], "7A7A86", mode="soft", edge=False)
    a.rect(2, 15, 15, 19, "62C040")
    a.rect(16, 15, 29, 19, "F2D88A")
    a.dots([(18, 17), (23, 16), (27, 18)], "D8B86A")
    a.rect(8, 8, 9, 15, "6A3A2A")
    a.ellipse(1, 0, 15, 10, "F28AB8")
    a.dots([(4, 2), (6, 1), (10, 3)], "FFD0E4")
    a.rect(21, 5, 24, 15, "4A9A3A")
    a.rect(18, 8, 20, 11, "4A9A3A")
    a.rect(25, 9, 27, 12, "4A9A3A")
    a.dots([(22, 6), (22, 9), (23, 12)], "8AD870")
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


def s_alert():
    """Rotes Ausrufezeichen (Hinweis „Belohnung abholbar“): leicht schräg und gerundet, dunkle Umrandung, Glanz."""
    a = Art(20)
    a.ellipse(8, 1, 15, 7, "F03A3A")
    a.poly([(8, 4), (15, 4), (12, 13), (9, 13)], "F03A3A")
    a.ellipse(6, 14, 11, 19, "F03A3A")
    a.dots([(10, 3), (11, 2), (10, 4), (9, 6)], "FFB8B0")
    a.dot(8, 15, "FFB8B0")
    return a


def s_alert_small():
    a = Art(14)
    a.ellipse(5, 1, 10, 5, "F03A3A")
    a.poly([(5, 3), (10, 3), (8, 9), (6, 9)], "F03A3A")
    a.ellipse(4, 10, 7, 13, "F03A3A")
    a.dots([(7, 2), (6, 4)], "FFB8B0")
    return a


def c_bloecke():
    a = Art()
    a.ground_shadow(3, 27, 29, 31)
    # Steinblock hinten
    a.poly([(12, 6), (21, 2), (30, 6), (21, 10)], "B8BEC8")
    a.poly([(12, 6), (21, 10), (21, 19), (12, 15)], "8E949E")
    a.poly([(21, 10), (30, 6), (30, 15), (21, 19)], "727882")
    # Ziegelblock vorn
    a.poly([(2, 13), (12, 8), (22, 13), (12, 18)], "D86A4A")
    a.poly([(2, 13), (12, 18), (12, 29), (2, 24)], "B04A30")
    a.poly([(12, 18), (22, 13), (22, 24), (12, 29)], "8E3A24")
    for y in (17, 21, 25):
        a.line([(3, y - 3), (11, y + 1)], "E8D8C8", 1, mode="flat", edge=False)
        a.line([(13, y + 1), (21, y - 3)], "C8B8A8", 1, mode="flat", edge=False)
    a.sparkle(28, 23, 1)
    return a


def c_essen():
    a = Art()
    a.ground_shadow(3, 26, 29, 31)
    # Steak
    a.ellipse(2, 9, 22, 26, "A8542E")
    a.ellipse(5, 12, 19, 23, "C8683A", mode="soft", edge=False)
    a.line([(8, 15), (15, 20)], "F0D8C0", 2, mode="soft", edge=False)
    a.ellipse(16, 18, 23, 25, "F4ECDC")
    # Apfel
    a.ellipse(17, 5, 30, 18, "E83A3A")
    a.rect(23, 1, 24, 6, WOOD)
    a.ellipse(24, 1, 29, 5, "4AC860")
    a.sparkle(4, 4, 1)
    return a


def c_redstone():
    a = Art()
    a.ground_shadow(3, 26, 29, 31)
    a.ellipse(2, 18, 20, 28, "C81E1E")
    for x, y in ((5, 20), (9, 22), (13, 20), (16, 23), (8, 25)):
        a.dot(x, y, "FF7A6A")
    # Fackel
    a.rect(21, 11, 24, 28, WOOD)
    a.ellipse(19, 3, 26, 12, "E83A3A")
    a.ellipse(21, 5, 24, 9, "FFB0A0", mode="soft", edge=False)
    a.sparkle(28, 3, 2, "FFB0A0")
    return a


def c_deko():
    a = Art()
    a.ground_shadow(6, 27, 26, 31)
    a.poly([(8, 19), (24, 19), (22, 29), (10, 29)], "C8683A")
    a.rect(7, 17, 25, 20, "D87A48")
    a.line([(16, 17), (16, 8)], "3AA850", 2, mode="soft")
    a.ellipse(9, 9, 16, 14, "4AC860")
    for cx, cy in ((16, 2), (20, 6), (16, 9), (12, 6)):
        a.ellipse(cx - 3, cy - 3, cx + 3, cy + 3, "FF7AA8")
    a.ellipse(14, 4, 18, 8, "FFE070")
    a.sparkle(27, 10, 2, "FFD0E0")
    return a


def c_suche():
    a = Art()
    a.ground_shadow(4, 27, 28, 31)
    a.line([(18, 18), (27, 27)], WOOD, 4)
    a.ellipse(2, 2, 22, 22, GOLD)
    a.ellipse(5, 5, 19, 19, "9AD8FF", mode="soft")
    a.dots([(8, 8), (9, 7), (8, 9), (10, 7)], "FFFFFF")
    a.sparkle(27, 4, 2)
    return a


def c_daily():
    a = Art()
    a.ground_shadow(5, 27, 27, 31)
    a.ellipse(1, 1, 9, 9, GOLD)
    a.ellipse(23, 1, 31, 9, GOLD)
    a.ellipse(3, 4, 29, 30, GOLD)
    a.ellipse(6, 7, 26, 27, "F4EEDC", mode="soft")
    a.line([(16, 17), (16, 10)], "3A3A48", 2, mode="flat", edge=False)
    a.line([(16, 17), (21, 20)], "3A3A48", 2, mode="flat", edge=False)
    a.poly([(16, 21), (20, 25), (16, 29), (12, 25)], "3AD86A")
    a.dots([(15, 23)], "C8FFD8")
    a.sparkle(28, 26, 1)
    return a


def i_trade():
    """Logo der Trading-App: dunkle Kachel mit grünen und roten Kerzen und Pfeil nach oben."""
    a = Art()
    a.ground_shadow(3, 27, 29, 31)
    a.poly([(4, 1), (27, 1), (30, 4), (30, 25), (27, 28), (4, 28), (1, 25), (1, 4)], "24324A")
    for x, y0, y1, w0, w1, col in ((6, 14, 21, 12, 23, "F2545B"), (12, 11, 18, 9, 20, "3AD86A"), (18, 13, 19, 11, 21, "F2545B"),
                                   (24, 6, 14, 4, 16, "3AD86A")):
        a.line([(x + 1, w0), (x + 1, w1)], "9AA8BC", 1, mode="flat", edge=False)
        a.rect(x, y0, x + 2, y1, col, mode="soft", edge=False)
    a.line([(4, 20), (10, 13), (15, 16), (26, 4)], "FFE070", 1, mode="flat", edge=False)
    a.dots([(24, 4), (25, 4), (26, 4), (26, 5), (26, 6)], "FFE070")
    a.sparkle(29, 27, 1)
    return a


TILES = [("menu", i_menu), ("angeln", i_angeln), ("jobs", i_jobs), ("grundstuecke", i_grundstuecke),
         ("heiraten", i_heiraten), ("bank", i_bank), ("duelle", i_duelle), ("turnier", i_turnier),
         ("versicherung", i_versicherung), ("shop", i_shop), ("markt", i_markt), ("wirtschaft", i_wirtschaft),
         ("boerse", i_boerse), ("tagespot", i_tagespot), ("duo_rtp", i_duo_rtp), ("spielzeit", i_spielzeit),
         ("titel", i_titel), ("kosmetik", i_kosmetik), ("haustiere", i_haustiere), ("insel", i_insel), ("generator", i_generator),
         ("groesse", i_groesse), ("mitglieder", i_mitglieder), ("oneblock", i_oneblock),
         ("ob_glueck", ob_glueck), ("ob_cooldown", ob_cooldown), ("ob_tempo", ob_tempo), ("ob_xp", ob_xp), ("ob_geld", ob_geld),
         ("ob_beute", ob_beute),
         ("s_wildnis", s_wildnis), ("s_homes", s_homes), ("s_enderchest", s_enderchest),
         ("alert", s_alert), ("alert_s", s_alert_small),
         ("c_bloecke", c_bloecke), ("c_essen", c_essen), ("c_redstone", c_redstone), ("c_deko", c_deko), ("c_suche", c_suche),
         ("c_daily", c_daily), ("trade", i_trade),
         ("is_wachstum", is_wachstum), ("is_spawn", is_spawn), ("is_limits", is_limits), ("is_pokal", is_pokal),
         ("is_besuch", is_besuch), ("is_biom", is_biom)]


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
