"""Zeichen-Engine für die Fisch-Icons (16 x 16, Seitenansicht, Kopf links) im Stil der Icon-Datenbank.

Ablauf je Fisch: die handgezeichnete Vorlage (tools/fish_shapes.py) wird mittig gesetzt, dann kommen 1 px Kontur
(fast schwarz, in der Rückenfarbe getönt), Schattierung aus Rampen (Rücken dunkel, Bauch hell, Oberkante 1 Stufe
heller, Unterkante 1 Stufe dunkler, Licht oben links; Lichter wärmer, Schatten kühler), Kiemendeckel, Auge mit
Glanzpunkt, Flossen (am Körper dunkler) und das Muster der Art.

Genutzt von tools/build_fish.py (Arten, Farben, Muster).
"""
import colorsys

from PIL import Image

T = (0, 0, 0, 0)
N = 16


def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def hexc(c):
    return "%02X%02X%02X" % tuple(c[:3])


def _hls(c):
    return colorsys.rgb_to_hls(c[0] / 255, c[1] / 255, c[2] / 255)


def _rgb(h, l, s):
    r, g, b = colorsys.hls_to_rgb(h % 1.0, max(0.0, min(1.0, l)), max(0.0, min(1.0, s)))
    return (round(r * 255), round(g * 255), round(b * 255))


def _toward(h, target, k):
    d = ((target - h + 0.5) % 1.0) - 0.5
    return h + d * k


def light(c, k=0.08):
    """Eine Stufe heller: wärmer (Richtung Gelb), etwas blasser."""
    h, l, s = _hls(c)
    if s > 0.08:
        h = _toward(h, 1 / 6, 0.12 * k / 0.08)
    return _rgb(h, l + k, s * (1 - 0.15 * k / 0.08) if l > 0.6 else s)


def dark(c, k=0.08):
    """Eine Stufe dunkler: kühler (Richtung Blau/Lila), satter."""
    h, l, s = _hls(c)
    if s > 0.08:
        h = _toward(h, 0.68, 0.10 * k / 0.08)
    return _rgb(h, l - k, min(1.0, s * 1.08 + 0.02))


def mix(a, b, f):
    return tuple(round(x + (y - x) * f) for x, y in zip(a[:3], b[:3]))


def ink(c):
    """Konturfarbe: fast schwarz, leicht in der Farbe getönt."""
    return mix(dark(c, 0.2), (6, 5, 12), 0.8)


# ---------------------------------------------------------------------------------------------------------------------
# Handgezeichnete Vorlagen (16 x 16, Kopf links). Zeichen:
#   b Körper (Rücken/Bauch nach Höhe)   B Körper dunkel (Rückenfarbe)   L Körper hell (Bauchfarbe)
#   f Flosse   t Schwanzflosse   F Flosse dunkel   p Brustflosse (auf dem Körper)
#   w Glanzpunkt im Auge   e Pupille   i Iris (Augenfarbe der Art)   g Kiemendeckel   m Maul (Konturfarbe)
#   x Extra (Bartfäden, Zähne, Laterne ...) mit Kontur   X Extra ohne Kontur   k Konturfarbe innen   h Glanz auf dem Körper
#   1..4 Akzentfarben der Art (acc1..acc4), mit Kontur
# ---------------------------------------------------------------------------------------------------------------------
BODYISH = set("bBLpwei gmhk1234".replace(" ", ""))


def from_grid(rows, pal, pattern=None, silhouette=False, center=True):
    rows = [r.ljust(N, ".")[:N] for r in rows] + ["." * N] * (N - len(rows))
    g = [list(r) for r in rows[:N]]
    if center:
        cells = [(x, y) for y in range(N) for x in range(N) if g[y][x] != "."]
        if cells:
            x0 = min(c[0] for c in cells); x1 = max(c[0] for c in cells)
            y0 = min(c[1] for c in cells); y1 = max(c[1] for c in cells)
            dx = (N - 1 - x1 - x0) // 2
            dy = (N - 1 - y1 - y0) // 2
            ng = [["."] * N for _ in range(N)]
            for (x, y) in cells:
                if 0 <= x + dx < N and 0 <= y + dy < N:
                    ng[y + dy][x + dx] = g[y][x]
            g = ng
    back, bodyc, belly, fin = pal["back"], pal["body"], pal["belly"], pal["fin"]
    img = Image.new("RGBA", (N, N), T)
    px = img.load()
    filled = {(x, y) for y in range(N) for x in range(N) if g[y][x] != "."}
    lined = {(x, y) for (x, y) in filled if g[y][x] != "X"}
    span = {}
    for x in range(N):
        ys = [y for y in range(N) if g[y][x] in BODYISH]
        if ys:
            span[x] = (min(ys), max(ys))
    xs = sorted(span)
    bx0, bx1 = (xs[0], xs[-1]) if xs else (0, N - 1)
    oc = pal.get("ink", ink(back))
    eyec = pal.get("eye")
    accs = {str(k): pal.get("acc%d" % k, fin) for k in range(1, 5)}
    for (x, y) in filled:
        ch = g[y][x]
        if silhouette:
            px[x, y] = pal.get("sil", (34, 40, 54)) + (255,)
            continue
        if ch in "bBLgph":
            y0, y1 = span[x]
            v = (y - y0) / max(1, y1 - y0)
            t = (x - bx0) / max(1, bx1 - bx0)
            c = back if (ch == "B" or (ch != "L" and v < 0.36)) else bodyc if (ch != "L" and v < 0.68) else belly
            if pattern:
                pc = pattern(x, y, v, t, c, x - bx0)
                if pc is not None:
                    c = pc
            if ch == "h":
                c = light(c, 0.14)
            elif y == y0 and y1 - y0 >= 2 and t < 0.8:
                c = light(c, 0.07)
            elif y == y1 and y1 - y0 >= 3:
                c = dark(c, 0.09)
            if ch == "g":
                c = dark(c, 0.11)
            elif ch == "p":
                c = pal.get("pect", dark(mix(c, fin, 0.45), 0.05))
            px[x, y] = c + (255,)
        elif ch in "ftF":
            c = fin
            touch = any(0 <= x + dx < N and 0 <= y + dy < N and g[y + dy][x + dx] in "bBL"
                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if ch == "F" or touch:
                c = dark(fin, 0.08)
            elif y > 0 and g[y - 1][x] == "." and y < 8:
                c = light(fin, 0.07)
            if ch == "t" and pal.get("tail"):
                c = pal["tail"] if not touch else dark(pal["tail"], 0.08)
            if pattern and pal.get("fin_pattern"):
                pc = pattern(x, y, -1, (x - bx0) / max(1, bx1 - bx0), c, x - bx0)
                if pc is not None:
                    c = pc
            px[x, y] = c + (255,)
        elif ch == "w":
            px[x, y] = (246, 246, 236, 255)
        elif ch == "e":
            px[x, y] = pal.get("pupil", (20, 18, 28)) + (255,)
        elif ch == "i":
            px[x, y] = (eyec or (214, 196, 120)) + (255,)
        elif ch in "mk":
            px[x, y] = oc + (255,)
        elif ch in "xX":
            px[x, y] = pal.get("extra", dark(fin, 0.1)) + (255,)
        elif ch in accs:
            px[x, y] = accs[ch] + (255,)
    for y in range(N):
        for x in range(N):
            if (x, y) in filled:
                continue
            if any((x + dx, y + dy) in lined for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = (pal.get("sil_ink", (14, 16, 24)) if silhouette else oc) + (255,)
    return img
