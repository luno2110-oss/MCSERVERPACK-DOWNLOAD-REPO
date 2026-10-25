#!/usr/bin/env python3
"""Kuby-Logo (goldenes K, 20 x 20) für den Knopf neben dem Titel jedes Server-Dialogs (Minecraft zeichnet dort ein
Warn-Symbol; der Chaos-Server hat es durch sein goldenes C ersetzt). Leicht schräg, mit Lichtkante oben links und
dunkler Kante unten rechts wie dort.

logo(state): "normal", "highlighted" (heller, Maus darüber) oder "disabled" (grau).
"""
from PIL import Image

W = H = 20

# Fläche des K (x von links, y von oben), Schrägung: oben 2 px weiter rechts als unten
_STEM = [(y, 3, 6) for y in range(3, 17)]
_ARM = {3: (11, 14), 4: (10, 13), 5: (9, 12), 6: (8, 11), 7: (7, 10), 8: (7, 9), 9: (7, 9), 10: (7, 10),
        11: (8, 11), 12: (9, 12), 13: (10, 13), 14: (11, 14), 15: (11, 14), 16: (11, 14)}


def _face():
    pts = set()
    for y, a, b in _STEM:
        for x in range(a, b + 1):
            pts.add((x, y))
    for y, (a, b) in _ARM.items():
        for x in range(a, b + 1):
            pts.add((x, y))
    # Schrägung (kursiv): je 6 Zeilen nach oben 1 px nach rechts
    return {(x + (16 - y) // 6, y) for x, y in pts}


GOLD = [(255, 236, 128), (255, 218, 50), (255, 199, 21), (251, 177, 2), (248, 150, 8), (240, 116, 2)]
LIGHT = (254, 254, 194)
EDGE = (235, 101, 1)
DEPTH = [(179, 67, 0), (137, 40, 0)]
OUTLINE = (76, 41, 19)


def logo(state="normal"):
    face = _face()
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    # Tiefe: zwei Pixel nach rechts unten
    for d, col in ((2, DEPTH[1]), (1, DEPTH[0])):
        for x, y in face:
            q = (x + d, y + d)
            if q not in face and 0 <= q[0] < W and 0 <= q[1] < H:
                px[q] = col + (255,)
    # dünner dunkler Rand außen (unten/rechts der Tiefe)
    filled = {(x, y) for x in range(W) for y in range(H) if px[x, y][3]} | face
    for x, y in list(filled):
        for q in ((x + 1, y), (x, y + 1)):
            if q not in filled and 0 <= q[0] < W and 0 <= q[1] < H:
                px[q] = OUTLINE + (255,)
    ys = [y for _, y in face]
    top, bottom = min(ys), max(ys)
    for x, y in face:
        t = (y - top) / max(1, bottom - top)
        col = GOLD[min(len(GOLD) - 1, int(t * len(GOLD)))]
        if (x, y - 1) not in face or ((x - 1, y) not in face and t < 0.6):
            col = LIGHT if t < 0.4 else GOLD[0]
        elif (x, y + 1) not in face:
            col = EDGE
        px[x, y] = col + (255,)
    if state == "highlighted":
        for x in range(W):
            for y in range(H):
                r, g, b, a = px[x, y]
                if a:
                    px[x, y] = (min(255, r + 30), min(255, g + 30), min(255, b + 40), a)
    elif state == "disabled":
        for x in range(W):
            for y in range(H):
                r, g, b, a = px[x, y]
                if a:
                    v = int(0.3 * r + 0.59 * g + 0.11 * b) // 2 + 40
                    px[x, y] = (v, v, v, a)
    return img


if __name__ == "__main__":
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else "logo.png"
    logo().resize((W * 16, H * 16), Image.NEAREST).save(out)
