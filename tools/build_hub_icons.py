"""Eigene Icons für das Hauptmenü (/menu), One-Block, Insel und Suche.

Die meisten Icons zeichnet hub_pixel_icons.py mit der Icon-Datenbank (tools/icon_db: Rampen der Referenzen, pixkit).
Hier bleiben die Grundlagen, die auch andere Skripte nutzen (Art: Zeichenfläche aus Bereichen mit gerechneter
Schattierung, Farben, trim) und vier alte Zeichnungen, die der Besitzer behalten wollte: Börse und Hut unverändert,
Pfote und Markt mit stärkerer Kontur (strong).

Ergebnis: tools/assets/hub/art_<name>.png (32 x 32 für Kacheln, 16 x 16 für Knöpfe). build_jewelshop.SETS nimmt den
Ordner mit dem Präfix hb_ auf, im Kern stehen die Bilder als KitData.Sprite.HB_<NAME>.

Aufruf: python3 tools/build_hub_icons.py  (schreibt die PNGs neu; build_ui_pack.py ruft build() auf)
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


# ---------------------------------------------------------------- Alte Zeichnungen, die bleiben (Wunsch des Besitzers)
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


def strong(fn):
    """Altes Icon mit stärkerer Kontur: Umriss im Fast-Schwarz der Referenzen (#060401) statt getönt und geschlossen
    (auch die Diagonal-Ecken), Funkeln und Details bleiben unberührt."""
    def draw():
        a = fn()
        img = a.render()
        px = img.load()
        w, h = img.size
        rid = a.rid
        body = {(x, y) for y in range(h) for x in range(w) if rid[y][x] and a.regions[rid[y][x]][2]}
        keep = {(x, y) for x, y, _c, _a in a.details + a.after}
        ring = {(x + dx, y + dy) for (x, y) in body for dx in (-1, 0, 1) for dy in (-1, 0, 1)} - body
        for (x, y) in ring:
            if 0 <= x < w and 0 <= y < h and not rid[y][x] and (x, y) not in keep:
                px[x, y] = (6, 4, 1, 255)
        return _Img(img)
    return draw


class _Img:
    def __init__(self, img):
        self.img = img

    def render(self):
        return self.img


# ---------------------------------------------------------------- Liste aller Kacheln
# Neue Zeichnungen aus hub_pixel_icons.py (Icon-Datenbank), dazu die vier alten: Börse und Hut unverändert,
# Pfote und Markt mit stärkerer Kontur
_KEEP = {"boerse": i_boerse, "kosmetik": i_kosmetik, "haustiere": strong(i_haustiere), "markt": strong(i_markt)}
_ORDER = ["menu", "angeln", "jobs", "grundstuecke", "heiraten", "bank", "duelle", "turnier", "versicherung", "shop",
          "markt", "wirtschaft", "boerse", "tagespot", "duo_rtp", "spielzeit", "titel", "kosmetik", "haustiere", "insel",
          "generator", "groesse", "mitglieder", "oneblock", "ob_glueck", "ob_cooldown", "ob_tempo", "ob_xp", "ob_geld",
          "ob_beute", "s_wildnis", "s_homes", "s_enderchest", "alert", "alert_s", "c_bloecke", "c_essen", "c_redstone",
          "c_deko", "c_suche", "c_daily", "trade", "is_wachstum", "is_spawn", "is_limits", "is_pokal", "is_besuch",
          "is_biom"]


def tiles():
    import hub_pixel_icons as px
    return [(n, _KEEP.get(n) or px.TILES[n]) for n in _ORDER]


def trim(img):
    box = img.getchannel("A").getbbox()
    return img.crop(box) if box else img


def build():
    os.makedirs(OUT, exist_ok=True)
    names = []
    for name, fn in tiles():
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
