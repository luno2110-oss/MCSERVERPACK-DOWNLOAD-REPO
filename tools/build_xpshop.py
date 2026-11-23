#!/usr/bin/env python3
"""XP-Shop (SMP und Skyblock): Schriftzug „XP-SHOP“ für den Kopf des Fensters.

Gleiche Buchstabenform wie der Juwelenshop-Schriftzug (tools/assets/jewelshop/art_title.png, 15 Zeilen, Strich 4 px):
S, H, O, P aus „SHOP“ übernommen, X und Bindestrich neu im selben Raster (Farbe je Zeile vom Stamm des H, linke
Kante wie die linke Stammkante, 1 px dunkle Kontur). „XP-“ wird danach in die XP-Farbe (Lindgrün, Rampe aus den
Referenzen: Lichter gelblicher, Schatten kühler) umgefärbt, „SHOP“ bleibt weiß wie beim Juwelenshop.
Sprite xs_title (über build_ui_pack in den Pack).
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TITLE = os.path.join(HERE, "assets", "jewelshop", "art_title.png")

# Spalten der weißen Buchstaben im Juwelenshop-Schriftzug (mit Kontur links/rechts)
S, H, O, P = (83, 94), (94, 106), (105, 117), (117, 129)

X = ["XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX", ".XXXXXXXX.", "..XXXXXX..", "...XXXX...", "...XXXX...",
     "...XXXX...", "..XXXXXX..", ".XXXXXXXX.", "XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX"]
DASH = [".....", ".....", ".....", ".....", ".....", ".....", "XXXXX", "XXXXX", "XXXXX", ".....", ".....", ".....",
        ".....", ".....", "....."]

# Lindgrün dunkel -> hell (Referenz-Rampe gruen, oben um zwei gelblichere Lichter ergänzt)
LIME = ["#3B530C", "#4F6910", "#618411", "#78AE13", "#8CC811", "#A8DC2A", "#C8EE55"]


def _hex(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def _glyph(mask, src):
    w, h = len(mask[0]) + 2, src.height
    g = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sp = src.load()
    filled = {(i + 1, j + 1) for j, row in enumerate(mask) for i, ch in enumerate(row) if ch == "X"}
    for (x, y) in filled:
        left = (x - 1, y) not in filled
        g.putpixel((x, y), sp[H[0] + 1, y] if left else sp[H[0] + 2, y])
    ink = (2, 1, 1, 255)
    for (x, y) in list(filled):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in filled and 0 <= q[0] < w and 0 <= q[1] < h and g.getpixel(q)[3] == 0:
                    g.putpixel(q, ink)
    return g


def _lime(im):
    """Helle Buchstaben-Pixel nach Helligkeit auf die Lindgrün-Rampe legen (Kontur bleibt)."""
    out = im.copy()
    px = out.load()
    ramp = [_hex(h) for h in LIME]
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if p[3] == 0:
                continue
            lum = (p[0] * 299 + p[1] * 587 + p[2] * 114) / 1000
            if lum < 40:
                continue
            t = max(0.0, min(1.0, (lum - 60) / 190))
            px[x, y] = ramp[min(len(ramp) - 1, int(t * len(ramp)))][:3] + (p[3],)
    return out


def _join(parts):
    w = sum(p.width for p in parts) - (len(parts) - 1)
    out = Image.new("RGBA", (w, parts[0].height), (0, 0, 0, 0))
    x = 0
    for p in parts:
        for yy in range(p.height):
            for xx in range(p.width):
                c = p.getpixel((xx, yy))
                if c[3] == 0:
                    continue
                cur = out.getpixel((x + xx, yy))
                if cur[3] and sum(c[:3]) < 40 and sum(cur[:3]) >= 40:
                    continue
                out.putpixel((x + xx, yy), c)
        x += p.width - 1
    return out


def title():
    src = Image.open(TITLE).convert("RGBA")
    crop = lambda c: src.crop((c[0], 0, c[1], src.height))  # noqa: E731
    xp = _lime(_join([_glyph(X, src), crop(P), _glyph(DASH, src)]))
    return _join([xp, crop(S), crop(H), crop(O), crop(P)])


# Casino-Gold (Chip-Shop): dunkel -> hell, Lichter gelblicher
GOLD = ["#5E3E0C", "#8A5A10", "#B27A14", "#D99A1C", "#F2C230", "#FADB5C", "#FFF0A0"]

I = ["XXXX"] * 15


def _recolor(im, ramp_hex, span=190):
    out = im.copy()
    px = out.load()
    ramp = [_hex(h) for h in ramp_hex]
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if p[3] == 0:
                continue
            lum = (p[0] * 299 + p[1] * 587 + p[2] * 114) / 1000
            if lum < 40:
                continue
            t = max(0.0, min(1.0, (lum - 60) / span))
            px[x, y] = ramp[min(len(ramp) - 1, int(t * len(ramp)))][:3] + (p[3],)
    return out


def _c_from_o(src):
    """C aus dem O: rechter Stamm in der Mitte offen, Schnittkanten mit Kontur."""
    o = src.crop((O[0], 0, O[1], src.height)).copy()
    px = o.load()
    w, h = o.size
    bright = lambda c: c[3] > 0 and sum(c[:3]) >= 40  # noqa: E731
    cols = [x for x in range(w) if any(bright(px[x, y]) for y in range(h))]
    right = sorted(cols)[-5:]
    for y in range(5, 11):
        for x in right + [max(right) + 1]:
            if 0 <= x < w:
                px[x, y] = (0, 0, 0, 0)
    ink = (2, 1, 1, 255)
    for y in range(h):
        for x in range(w):
            if px[x, y][3] != 0:
                continue
            if any(0 <= x + dx < w and 0 <= y + dy < h and bright(px[x + dx, y + dy])
                   for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                px[x, y] = ink
    return o


def chip_title():
    """Schriftzug CHIP-SHOP: „CHIP-“ in Casino-Gold, „SHOP“ weiß wie beim Juwelenshop."""
    src = Image.open(TITLE).convert("RGBA")
    crop = lambda c: src.crop((c[0], 0, c[1], src.height))  # noqa: E731
    chip = _recolor(_join([_c_from_o(src), crop(H), _glyph(I, src), crop(P), _glyph(DASH, src)]), GOLD, span=245)
    return _join([chip, crop(S), crop(H), crop(O), crop(P)])


def sprites():
    if not os.path.isfile(TITLE):
        return []
    return [("xs_title", title()), ("xs_chip_title", chip_title())]


if __name__ == "__main__":
    import sys
    title().save(sys.argv[1] if len(sys.argv) > 1 else "xs_title.png")
