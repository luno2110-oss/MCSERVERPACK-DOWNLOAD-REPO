#!/usr/bin/env python3
"""Skypass (Skyblock): eigener Stil des Battle-Pass-Dialogs, aus den Battle-Pass-Bausteinen abgeleitet.

Gleiche Formen wie der Battlepass (tools/assets/battlepass), andere Welt:
- Violett wird Himmelblau (Kopf, Reiter, Stufen, Aufgaben, Kaufen), die Free-Reihe wird Wiesen-Türkis.
- Ticket: himmelblau statt gold, mit einer Wolke statt des Sterns, Funkeln weiß-türkis.
- Schriftzug SKYPASS in der Buchstabenform des Battle-Pass-Schriftzugs (S, P, A aus dem Original, K und Y neu im
  selben Raster), von Wolkenweiß oben nach Himmelblau unten.
- Menü-Icon (32 px): kleines Ticket mit Wolke für das Hauptmenü.

Gold (Premium), Grün (abgeholt), Grau und die Truhen bleiben die des Battlepass.
Ergebnis: Boxen sp_<name>, Sprites sp_<name> (über build_battlepass.boxes()/sprites() in den Pack).
"""
import colorsys
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BP = os.path.join(HERE, "assets", "battlepass")

# Kästen aus dem Battlepass, die die Pass-Farbe tragen (violett, Free blau)
SKY_BOXES = ("head", "level", "tab", "tab_on", "pill_on", "pill_cur", "quest_day", "buy_box")
RENAMED = {"btn_violet": "btn_sky"}


def _load(name):
    return Image.open(os.path.join(BP, name + ".png")).convert("RGBA")


def _hsv(p):
    return colorsys.rgb_to_hsv(p[0] / 255, p[1] / 255, p[2] / 255)


def _rgb(h, s, v, a):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, max(0.0, min(1.0, s)), max(0.0, min(1.0, v)))
    return (round(r * 255), round(g * 255), round(b * 255), a)


def violet_to_sky(im):
    """Violett/Lila (Farbton 225-320°) wird Himmelblau (198°); helle Stufen etwas türkiser (wärmeres Licht),
    dunkle etwas tiefer blau (kühlerer Schatten)."""
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if p[3] == 0:
                continue
            h, s, v = _hsv(p)
            if s < 0.08 or not (225 / 360 <= h <= 320 / 360):
                continue
            nh = (204 - 10 * v) / 360
            px[x, y] = _rgb(nh, s * 1.02, v, p[3])
    return out


def blue_to_meadow(im):
    """Blau der Free-Reihe (190-235°) wird Wiesen-Türkis (165°)."""
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if p[3] == 0:
                continue
            h, s, v = _hsv(p)
            if s < 0.08 or not (190 / 360 <= h <= 240 / 360):
                continue
            px[x, y] = _rgb((168 - 8 * v) / 360, s, v, p[3])
    return out


def gold_to_sky(im, star_from=280):
    """Goldenes Ticket wird himmelblau: Orange (Schatten) tiefes Blau, Gelb (Licht) helles Himmelblau.
    Magenta (Stern, Funkeln) wird blasses Weiß-Türkis."""
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if p[3] == 0:
                continue
            h, s, v = _hsv(p)
            hd = h * 360
            if s >= 0.15 and 10 <= hd <= 62:
                t = max(0.0, min(1.0, (hd - 15) / 40))
                px[x, y] = _rgb((208 - 18 * t) / 360, s * (0.97 - 0.22 * t), v * (0.97 - 0.05 * t), p[3])
            elif s >= 0.08 and (hd >= star_from or hd <= 8) and v > 0.3:
                px[x, y] = _rgb(186 / 360, s * 0.30, min(1.0, v * 1.02), p[3])
    return out


# Wolke statt des Sterns (O Kontur getönt, W weiß, M hell, L Schatten), Licht oben links
CLOUD = [
    "....OOO.....",
    "..OOWWWOOO..",
    ".OWWWWWWMWO.",
    "OWWWWWWWWMMO",
    "OWWWWWWWMMLO",
    ".OMMLLMLLLO.",
    "..OOOOOOOO..",
]
CLOUD_COL = {"O": (0x1B, 0x4C, 0x6E, 255), "W": (0xF4, 0xFA, 0xFB, 255), "M": (0xD3, 0xEA, 0xF4, 255),
             "L": (0xA6, 0xC9, 0xDE, 255)}


def ticket():
    """Skypass-Ticket 38 x 37: Battle-Pass-Ticket in Himmelblau, Wolke statt Stern."""
    src = _load("art_ticket")
    out = gold_to_sky(src)
    px, sp = out.load(), src.load()
    # Stern (magenta im Original) im Ticket mit Ticket-Blau füllen: Farbe des nächsten Ticket-Pixels in der Zeile
    for y in range(8, 23):
        for x in range(14, 29):
            h, s, v = _hsv(sp[x, y])
            if sp[x, y][3] and s > 0.3 and (h * 360 >= 280 or h * 360 <= 8):
                for d in range(1, 8):
                    found = None
                    for xx in (x - d, x + d):
                        if 0 <= xx < src.width:
                            hh, ss, vv = _hsv(sp[xx, y])
                            if sp[xx, y][3] and ss > 0.3 and 10 <= hh * 360 <= 62:
                                found = px[xx, y]
                                break
                    if found:
                        px[x, y] = found
                        break
    for j, row in enumerate(CLOUD):
        for i, ch in enumerate(row):
            if ch != ".":
                px[16 + i, 12 + j] = CLOUD_COL[ch]
    return out


# Buchstaben K und Y im Raster des Schriftzugs (12 Zeilen, Strich 3 px), X = Füllung
K = ["XXX...XXX", "XXX..XXX.", "XXX.XXX..", "XXXXXX...", "XXXXX....", "XXXXX....", "XXXXXX...", "XXX.XXX..",
     "XXX..XXX.", "XXX...XXX", "XXX...XXX", "XXX...XXX"]
Y = ["XXX...XXX", "XXX...XXX", "XXX...XXX", "XXXX.XXXX", ".XXXXXXX.", "..XXXXX..", "...XXX...", "...XXX...",
     "...XXX...", "...XXX...", "...XXX...", "...XXX..."]


def _glyph(mask, title):
    """Buchstabe aus Maske: Farbe je Zeile vom Stamm des T (Spalte 26/25 im Original), linke Kante wie die
    linke Stammkante, dunkle 1-px-Kontur rundum."""
    w, h = len(mask[0]) + 2, 15
    g = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    tp = title.load()
    ink = (2, 1, 1, 255)
    filled = set()
    for j, row in enumerate(mask):
        for i, ch in enumerate(row):
            if ch == "X":
                filled.add((i + 1, j + 1))
    for (x, y) in filled:
        left = (x - 1, y) not in filled
        col = tp[25, y] if left else tp[26 if y > 3 else 27, y]
        g.putpixel((x, y), col)
    for (x, y) in list(filled):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in filled and 0 <= q[0] < w and 0 <= q[1] < h and g.getpixel(q)[3] == 0:
                    g.putpixel(q, ink)
    return g


def title():
    """Schriftzug SKYPASS: S, P, A aus dem Battle-Pass-Schriftzug, K und Y neu; danach Violett zu Himmelblau."""
    src = _load("art_title")
    # Spalten der Original-Buchstaben (mit Kontur links/rechts)
    S, P, A = (87, 98), (65, 76), (76, 87)
    parts = [src.crop((S[0], 0, S[1], 15)), _glyph(K, src), _glyph(Y, src), src.crop((P[0], 0, P[1], 15)),
             src.crop((A[0], 0, A[1], 15)), src.crop((S[0], 0, S[1], 15)), src.crop((S[0], 0, S[1], 15))]
    w = sum(p.width for p in parts) - (len(parts) - 1)
    out = Image.new("RGBA", (w, 15), (0, 0, 0, 0))
    x = 0
    for p in parts:
        # gemeinsame Konturspalte: dunkle Pixel überschreiben keine Füllung
        for yy in range(15):
            for xx in range(p.width):
                c = p.getpixel((xx, yy))
                if c[3] == 0:
                    continue
                cur = out.getpixel((x + xx, yy))
                if cur[3] and sum(c[:3]) < 40 and sum(cur[:3]) >= 40:
                    continue
                out.putpixel((x + xx, yy), c)
        x += p.width - 1
    return violet_to_sky(out)


def icon32():
    """Menü-Icon 32 x 32: das Ticket ohne den Rand links (Ticket und Funkeln liegen in x 6-37, y 0-31)."""
    t = ticket()
    box = t.getbbox()
    x0 = max(0, min(box[0], box[2] - 32))
    y0 = max(0, min(box[1], box[3] - 32))
    return t.crop((x0, y0, x0 + 32, y0 + 32))


def boxes():
    if not os.path.isdir(BP):
        return {}
    out = {}
    for n in SKY_BOXES:
        out["sp_" + n] = violet_to_sky(_load("box_" + n))
    for old, new in RENAMED.items():
        out["sp_" + new] = violet_to_sky(_load("box_" + old))
    out["sp_free"] = blue_to_meadow(_load("box_free"))
    return out


def sprites():
    if not os.path.isdir(BP):
        return []
    return [("sp_freepass", gold_to_sky(_load("art_freepass"), star_from=225)), ("sp_icon", icon32()),
            ("sp_island", ticket()), ("sp_play", violet_to_sky(_load("art_play"))),
            ("sp_tab_scroll", violet_to_sky(_load("art_tab_scroll"))), ("sp_title", title())]


def fill_sky():
    """Füllfarben des Fortschrittsbalkens (wie bp_violet, in Himmelblau)."""
    out = []
    for hx in ("C7B6FC", "AFA0DE", "8479A8"):
        p = tuple(int(hx[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
        q = violet_to_sky(Image.new("RGBA", (1, 1), p)).getpixel((0, 0))
        out.append("%02X%02X%02X" % q[:3])
    return tuple(out)


if __name__ == "__main__":
    import sys
    dst = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(dst, exist_ok=True)
    for name, im in sprites():
        im.save(os.path.join(dst, name + ".png"))
    for name, im in boxes().items():
        im.save(os.path.join(dst, name + ".png"))
    print("fill", fill_sky())
