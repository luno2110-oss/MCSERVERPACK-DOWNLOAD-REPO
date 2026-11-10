"""Partikel-Sprites der Rematch-Mod (Effekte beim Ballkontakt, Ballspur, Staub).

Stil wie die Generator-Grafiken des Besitzers (tools/icon_db/STYLE.md, Lieblingsstil): Licht von innen, weißer Kern,
nach außen über blasses Gelb zu Gold und warmem Orange, weicher Alpha-Rand (Licht-Effekt, daher weiche Verläufe
erlaubt). Farben aus den Gold-Rampen der Referenzen. Pixel-Raster bleibt sichtbar (keine Unschärfe über 1 px).

Aufruf: python3 tools/fx_sprites.py <ordner textures/particle>
"""
import math
import os
import sys

from PIL import Image

# Gold-Rampe der Referenzen (dunkel -> hell), plus weißer Kern
GOLD = ["#CC8904", "#F7AD02", "#F7CA05", "#F8E407", "#F9F020", "#FAF340", "#F9F45D", "#FAF584"]
CORE = "#FFFDF0"
# warmer Außenrand für Glühen (Gold zu Orange, kippt auf dunklem Grund nicht ins Grüne)
WARM = ["#EA9501", "#F7AD02", "#F9C803", "#FAD42B", "#FAE560", "#FAF695"]
STAUB = ["#5A5240", "#7A6E55", "#9C8F72", "#BDB293"]
SPUR = ["#3F91E5", "#7BB1F0", "#A7D3F6", "#E8F6FF"]


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ramp(cols, t):
    """t 0..1 -> Farbe aus der Rampe (stufig, keine Zwischenfarben)."""
    i = min(len(cols) - 1, max(0, int(round(t * (len(cols) - 1)))))
    return hexrgb(cols[i])


def put(im, x, y, rgb, a):
    if 0 <= x < im.width and 0 <= y < im.height and a > 0:
        im.putpixel((x, y), rgb + (int(max(0, min(1, a)) * 255),))


def stern(n=32):
    """Vierzackiger Stern-Blitz mit Glühen: Zacken lang und spitz (oben/unten länger), Kern weiß."""
    im = Image.new("RGBA", (n, n))
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            dx, dy = (x - c) / c, (y - c) / c
            r = math.hypot(dx, dy)
            # Zacken: Abstand zu den Achsen, oben/unten etwas länger (Symmetrie bewusst gebrochen)
            arm = max(math.exp(-abs(dx) * 9) * (1 - abs(dy) / 1.05), math.exp(-abs(dy) * 9) * (1 - abs(dx) / 0.85))
            glow = max(0, 1 - r / 0.75) ** 2.2
            v = max(arm, glow)
            if v < 0.08:
                continue
            if v > 0.55:
                col = hexrgb(CORE)
            else:
                col = ramp(WARM, v / 0.55)
            a = min(1.0, v ** 0.55)
            put(im, x, y, col, a)
    return im


def ring(n=32):
    """Schockwellen-Ring: heller Rand innen, weicher Ausklang nach außen, oben links etwas heller."""
    im = Image.new("RGBA", (n, n))
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            dx, dy = (x - c) / c, (y - c) / c
            r = math.hypot(dx, dy)
            d = r - 0.82
            v = math.exp(-(d / 0.07) ** 2) if d < 0 else math.exp(-(d / 0.12) ** 2)
            if v < 0.1:
                continue
            light = 0.12 * (-(dx + dy) / 1.4)  # Licht oben links
            col = hexrgb(CORE) if v + light > 0.85 else ramp(WARM[1:], min(1, v + light))
            put(im, x, y, col, v * 0.95)
    return im


def funke(n=8):
    """Kleiner Funke: weißer Kern, gelbes Kreuz, goldener Rand (wie das Funkeln über den Generatoren)."""
    im = Image.new("RGBA", (n, n))
    pts = {(3, 3): CORE, (4, 4): CORE, (3, 4): CORE, (4, 3): GOLD[6],
           (3, 2): GOLD[5], (4, 2): GOLD[4], (3, 5): GOLD[4], (4, 5): GOLD[3],
           (2, 3): GOLD[5], (2, 4): GOLD[4], (5, 3): GOLD[4], (5, 4): GOLD[3],
           (3, 1): GOLD[2], (4, 6): GOLD[1], (1, 3): GOLD[2], (6, 4): GOLD[1]}
    for (x, y), col in pts.items():
        a = 1.0 if col == CORE else 0.85 if col in GOLD[4:] else 0.55
        put(im, x, y, hexrgb(col), a)
    return im


def staub(n=16):
    """Staubwolke: drei überlappende Ballen, oben links heller, unten kühler/dunkler, weicher Rand."""
    im = Image.new("RGBA", (n, n))
    blobs = [(6.5, 8.5, 4.6), (10.0, 9.0, 4.0), (8.0, 5.8, 3.8)]
    for y in range(n):
        for x in range(n):
            v = max(max(0, 1 - math.hypot(x - bx, y - by) / br) for bx, by, br in blobs)
            if v < 0.05:
                continue
            shade = v * 0.7 + 0.3 * (1 - y / n) + 0.1 * (1 - x / n)
            put(im, x, y, ramp(STAUB, min(1, shade)), min(0.85, v * 1.3))
    return im


def spur(n=8):
    """Ballspur: runder Lichtpunkt, kühl (Himmelblau) mit weißem Kern."""
    im = Image.new("RGBA", (n, n))
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            r = math.hypot(x - c, y - c) / (n / 2)
            v = max(0, 1 - r) ** 1.4
            if v < 0.06:
                continue
            col = hexrgb("#FFFFFF") if v > 0.7 else ramp(SPUR[1:], v / 0.7)
            put(im, x, y, col, min(1, v * 1.5))
    return im


def main(out):
    os.makedirs(out, exist_ok=True)
    for name, fn in (("stern", stern), ("ring", ring), ("funke", funke), ("staub", staub), ("spur", spur)):
        fn().save(os.path.join(out, name + ".png"))
    print("Sprites geschrieben:", out)


if __name__ == "__main__":
    main(sys.argv[1])
