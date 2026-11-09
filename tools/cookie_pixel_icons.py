"""Cookie-Clicker-Icons im Stil der Referenzen (Icon-Datenbank: pixkit, Rampen aus materials.py, 1 px Kontur fast schwarz,
Licht oben links, Lichter wärmer, Schatten kühler, Form symmetrisch und Licht/Detail bewusst asymmetrisch).
Nach dem Feedback des Besitzers: natürlicher, weniger spitz, klar erkennbare Formen.

build_cookie_art.py nimmt ICONS (Name -> Funktion mit render()) und schreibt tools/assets/cookie/art_<name>.png.
"""
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "icon_db"))
from materials import OUTLINE, RAMPS, rgb  # noqa: E402
from pixkit import Icon  # noqa: E402
from hub_pixel_icons import Img, layer, grid, disc, ring_rows, stack  # noqa: E402,F401


# ---------------------------------------------------------------------------------------------------------- Keks
def cookie_mask(cx, cy, r, bumps=((0.4, 0.7), (1.9, -0.6), (3.3, 0.5), (4.6, -0.7), (5.6, 0.4))):
    """Runde, leicht unregelmäßige Keksform (kein perfekter Kreis): Radius schwankt sanft mit dem Winkel."""
    out = set()
    for y in range(int(cy - r - 2), int(cy + r + 3)):
        for x in range(int(cx - r - 2), int(cx + r + 3)):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            a = math.atan2(dy, dx) % (2 * math.pi)
            rr = r
            for ang, amp in bumps:
                d = min(abs(a - ang), 2 * math.pi - abs(a - ang))
                rr += amp * max(0.0, 1 - d / 0.7)
            if dx * dx + dy * dy <= rr * rr:
                out.add((x, y))
    return out


# Schoko-Stücke (Pixel relativ, Stufe: oben links Licht, unten rechts Schatten); verschiedene Formen, nicht gleich
CHUNKS = {
    "a": [((0, 0), 4), ((1, 0), 3), ((2, 0), 2), ((0, 1), 3), ((1, 1), 2), ((2, 1), 1), ((1, 2), 1)],
    "b": [((1, 0), 4), ((2, 0), 3), ((0, 1), 3), ((1, 1), 2), ((2, 1), 1), ((0, 2), 2), ((1, 2), 1)],
    "c": [((0, 0), 4), ((1, 0), 3), ((0, 1), 2), ((1, 1), 1)],
    "d": [((0, 0), 4), ((1, 0), 3), ((2, 0), 3), ((3, 0), 2), ((0, 1), 2), ((1, 1), 2), ((2, 1), 1), ((3, 1), 1)],
}


def cookie_pix(cx, cy, r, mat="keks", chunk_mat="schoko", chunks=None, shift=0, bumps=None):
    """Keks als Pixel {(x, y): (Material, Stufe)}: flach wie ein echter Keks (kein Ball). Mitte golden hell, zum Rand
    hin dunkler gebacken, Licht oben links (heller Saum), Schatten unten rechts; Schoko-Stücke ungleich verteilt,
    eines sitzt am Rand."""
    m = cookie_mask(cx, cy, r) if bumps is None else cookie_mask(cx, cy, r, bumps)
    n = len(RAMPS[mat])
    pix = {}
    for (x, y) in m:
        dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
        d = math.hypot(dx, dy)
        k = n - 3 if d < 0.55 else n - 4 if d < 0.8 else n - 5
        k += 1 if (dx + dy) < -0.55 else -1 if (dx + dy) > 0.7 else 0
        edge = any((x + ex, y + ey) not in m for ex, ey in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            k = n - 2 if (dx + dy) < -0.3 else n - 6 if (dx + dy) > 0.3 else n - 5
        pix[(x, y)] = (mat, max(0, min(n - 1, k)))
    s = r / 13.0
    if chunks is None:
        chunks = [(-7, -6, "a"), (1.5, -9, "c"), (5, -3, "d"), (-9.5, 1.5, "b"), (-2, 1, "a"), (4, 6, "c"), (-5, 6.5, "d"), (9.5, 2, "c")]
    cm = len(RAMPS[chunk_mat])
    for ox, oy, kind in chunks:
        bx, by = int(round(cx + ox * s)), int(round(cy + oy * s))
        for (ddx, ddy), k in CHUNKS[kind]:
            q = (bx + ddx, by + ddy)
            if q in m:
                pix[q] = (chunk_mat, max(0, min(cm - 1, k + shift)))
    return pix


def c_cookie():
    pix = cookie_pix(16, 16, 13.4)
    for q in ((8, 6), (9, 6), (7, 7)):
        pix[q] = ("keks", 7)   # Glanz oben links (ein Mal, bricht die Symmetrie)
    return Img(layer(pix))


def c_gold():
    pix = cookie_pix(15.5, 16.5, 12.8, mat="gold", chunk_mat="gold", shift=-2)
    for q in ((8, 7), (9, 7), (8, 8)):
        pix[q] = ("gold", 8)
    img = layer(pix)
    # Funkeln oben rechts und ein kleines unten links (ohne Kontur, wie bei den Generatoren)
    px = img.load()
    for (x, y), c in (((27, 3), "#FFFFFF"), ((26, 3), RAMPS["gold"][7]), ((28, 3), RAMPS["gold"][7]), ((27, 2), RAMPS["gold"][7]),
                      ((27, 4), RAMPS["gold"][7]), ((29, 3), RAMPS["gold"][6]), ((25, 3), RAMPS["gold"][6]), ((27, 1), RAMPS["gold"][6]),
                      ((27, 5), RAMPS["gold"][6]), ((4, 26), RAMPS["gold"][7])):
        if px[x, y][3] == 0:
            px[x, y] = rgb(c) + (255,)
    return Img(img)


# ---------------------------------------------------------------------------------------------------------- Hilfen
def mini(a, x0, y0, x1, y1, chips=((0.3, 0.3), (0.65, 0.55))):
    """Kleiner Keks als Teil eines Icons (gewölbt, 1 bis 2 Schoko-Pixel)."""
    a.ellipse(x0, y0, x1, y1, "keks", form="round", lo=2, hi=-2)
    w, h = x1 - x0, y1 - y0
    a.px([(x0 + round(w * fx), y0 + round(h * fy)) for fx, fy in chips], "schoko", 1)


def sparkle(img, x, y, col="#FFFFFF", arm=None):
    """Funkeln ohne Kontur (nur auf leere Pixel)."""
    px = img.load()
    arm = arm or col
    for (dx, dy), c in (((0, 0), col), ((1, 0), arm), ((-1, 0), arm), ((0, 1), arm), ((0, -1), arm)):
        if 0 <= x + dx < img.width and 0 <= y + dy < img.height and px[x + dx, y + dy][3] == 0:
            px[x + dx, y + dy] = rgb(c) + (255,)
    return img


# ---------------------------------------------------------------------------------------------------------- Gebäude
def b_loeffel():
    """Kochlöffel: Holzlöffel schräg, in der Kelle ein Klecks Keksteig."""
    a = Icon()
    a.line([(5, 28), (18, 15)], 3, "holz", form="cyl", lo=2, hi=-2)
    a.ellipse(15, 3, 29, 17, "holz", form="round", lo=2, hi=-2)
    a.ellipse(18, 6, 26, 14, "holz", form="round", lo=0, hi=3, sep=False)
    a.ellipse(19, 6, 26, 12, "keks", form="round", lo=3, hi=-1)
    a.px([(21, 8), (24, 10)], "schoko", 1)
    a.px([(20, 7)], "keks", -1)
    a.px([(17, 6), (16, 8)], "holz", -1)
    return a


def b_schuessel():
    """Rührschüssel: blaue Schüssel mit hellem Rand, darin ein Berg Teig mit Schoko, ein Kochlöffel steckt drin."""
    a = Icon()
    a.line([(19, 2), (24, 13)], 3, "holz", form="cyl", lo=2, hi=-2)
    a.ellipse(5, 7, 27, 19, "keks", form="round", lo=3, hi=-1)
    a.px([(10, 11), (15, 9), (19, 13), (13, 14)], "schoko", 1)
    a.px([(9, 10), (10, 9)], "keks", -1)
    a.poly([(2, 15), (30, 15), (27, 24), (22, 28), (10, 28), (5, 24)], "blau", form="cyl", lo=1, hi=-2)
    a.rect(1, 14, 30, 16, "silber", lo=2, hi=-1)
    a.rect(11, 28, 21, 29, "blau", lo=0, hi=3)
    a.px([(6, 19), (7, 21)], "blau", -1)
    return a


def b_blech():
    """Backblech von schräg oben: dunkles Blech mit hellem Rand, sechs Kekse in zwei Reihen (einer etwas verrutscht)."""
    a = Icon()
    a.poly([(16, 5), (31, 13), (16, 21), (1, 13)], "eisen", lo=2, hi=-3)
    a.poly([(16, 21), (31, 13), (31, 15), (16, 23)], "eisen", lo=0, hi=2)
    a.poly([(1, 13), (16, 21), (16, 23), (1, 15)], "eisen", lo=1, hi=3)
    a.poly([(16, 7), (28, 13), (16, 19), (4, 13)], "eisen", lo=3, hi=4, rim=False, sep=False)
    for (x, y) in ((10, 9), (16, 12), (22, 15), (6, 12), (12, 15), (18, 18)):
        a.ellipse(x - 3, y - 2, x + 2, y + 1, "keks", form="round", lo=3, hi=-1)
        a.px([(x - 1, y - 1)], "schoko", 1)
    a.px([(17, 18)], "schoko", 2)
    return a


def b_ofen():
    """Steinofen: gemauerte Kuppel mit Ziegelfugen, rund gewölbte Öffnung mit Glut, darin ein Blech mit Keksen, Schornstein."""
    a = Icon()
    a.rect(22, 1, 26, 8, "kessel", form="cyl", lo=1, hi=-1)
    a.ellipse(2, 5, 29, 27, "kessel", form="round", lo=1, hi=-1)
    a.rect(1, 22, 30, 30, "kessel", lo=1, hi=-2)
    # Fugen: Reihen gleichmäßig, Stöße versetzt (eine Fuge fehlt)
    for y in (10, 15, 20, 25):
        for x in range(3, 29):
            if (x, y) not in a.parts[1].mask and (x, y) not in a.parts[2].mask:
                continue
            a.px([(x, y)], "kessel", 0)
    for y, xs in ((12, (8, 16, 24)), (17, (5, 12, 20, 27)), (22, (8, 24)), (27, (5, 12, 20, 27))):
        for x in xs:
            a.px([(x, y - 1), (x, y)], "kessel", 0)
    a.ellipse(8, 14, 23, 33, "feuer", form="round", lo=2, hi=-1)
    a.cut_rect(0, 31, 31, 31)
    a.rect(9, 25, 22, 26, "eisen", lo=2, hi=-2)
    for x in (10, 15, 19):
        a.ellipse(x, 22, x + 3, 25, "keks", form="round", lo=3, hi=-1)
    a.px([(11, 23), (16, 23)], "schoko", 1)
    a.px([(12, 17), (19, 18), (15, 16)], "feuer", -1)
    a.px([(6, 9), (7, 8)], "kessel", -1)
    return a


def b_lehrling():
    """Bäckerlehrling: Kopf mit Kochmütze (bauschig, drei Wölbungen), helles Gesicht mit Augen und Lächeln, rotes Halstuch."""
    a = Icon()
    a.ellipse(3, 1, 14, 11, "papier", form="round", lo=6, hi=-1)
    a.ellipse(10, 0, 22, 10, "papier", form="round", lo=6, hi=-1)
    a.ellipse(18, 1, 29, 11, "papier", form="round", lo=6, hi=-1)
    a.rect(6, 8, 25, 12, "papier", lo=5, hi=-2)
    a.rect(7, 13, 24, 25, "haut", lo=3, hi=-1)
    a.rect(7, 13, 24, 14, "schoko", lo=3, hi=5)
    a.rect(5, 26, 26, 30, "rot", lo=2, hi=-2)
    a.poly([(13, 26), (18, 26), (15.5, 30)], "rot", lo=4, hi=-1)
    for ex in (11, 19):
        a.px([(ex, 17), (ex + 1, 17), (ex, 18), (ex + 1, 18)], None, 0)
        a.px([(ex, 17)], "papier", -1)
    a.px([(13, 22), (14, 23), (15, 23), (16, 23), (17, 23), (18, 22)], "haut", 0)
    a.px([(9, 20), (10, 20), (21, 20), (22, 20)], "rot", 7)
    a.px([(8, 15), (9, 15)], "haut", -1)
    return a


def b_muehle():
    """Windmühle: Holzturm (schmaler nach oben), rotes Spitzdach, vier Flügel mit Segeltuch, leicht gedreht."""
    a = Icon()
    a.poly([(10, 31), (22, 31), (20, 15), (12, 15)], "planke", lo=1, hi=-2)
    a.poly([(9, 16), (16, 8), (23, 16)], "rot", lo=2, hi=-2)
    a.rect(14, 25, 17, 31, "holz", lo=1, hi=-2)
    a.rect(13, 19, 15, 21, "blau", lo=3, hi=-1)
    cx, cy = 16, 11.5
    for ang in (25, 115, 205, 295):
        r = math.radians(ang)
        ux, uy = math.cos(r), math.sin(r)
        vx, vy = -uy, ux
        pts = [(cx + ux * 2 + vx * 0.5, cy + uy * 2 + vy * 0.5), (cx + ux * 11 + vx * 0.5, cy + uy * 11 + vy * 0.5),
               (cx + ux * 11 + vx * 4, cy + uy * 11 + vy * 4), (cx + ux * 3.5 + vx * 4, cy + uy * 3.5 + vy * 4)]
        a.poly(pts, "papier", lo=5, hi=-1)
        a.line([(cx, cy), (cx + ux * 11.5, cy + uy * 11.5)], 1, "holz", lo=1, hi=-2)
    a.ellipse(14, 10, 17, 13, "holz", form="round", lo=1, hi=-1)
    return a


def b_zucker():
    """Zuckerrohrfeld: drei Rohre mit Knoten in gleichem Abstand (einer versetzt), Blätter, davor ein Haufen Zucker."""
    a = Icon()
    for x, top in ((5, 4), (12, 1), (19, 6)):
        a.rect(x, top, x + 3, 28, "gras", form="cyl", lo=2, hi=-2)
    for x, top, knots in ((5, 4, (10, 16, 22)), (12, 1, (8, 14, 21)), (19, 6, (12, 18, 24))):
        a.px([(x, k) for k in knots] + [(x + 1, k) for k in knots] + [(x + 2, k) for k in knots] + [(x + 3, k) for k in knots], "gras", 1)
    a.poly([(9, 9), (11, 6), (10, 11)], "gras", lo=3, hi=-1)
    a.poly([(16, 15), (18, 12), (17, 17)], "gras", lo=3, hi=-1)
    a.poly([(14, 31), (22, 21), (31, 31)], "silber", lo=3, hi=-1)
    a.px([(21, 23), (20, 25), (22, 24)], "silber", -1)
    a.px([(17, 29), (25, 28), (28, 30)], "silber", 4)
    return a


def b_schoko():
    """Schokobrunnen: drei silberne Schalen übereinander auf einer Säule, Milchschokolade läuft über die Ränder."""
    a = Icon()
    a.rect(15, 4, 17, 27, "silber", form="cyl", lo=2, hi=-1)
    a.ellipse(2, 23, 29, 31, "silber", form="round", lo=2, hi=-1)
    a.ellipse(5, 23, 26, 28, "schoko", form="round", lo=3, hi=-1, sep=False)
    a.ellipse(6, 15, 25, 21, "silber", form="round", lo=2, hi=-1)
    a.ellipse(8, 15, 23, 19, "schoko", form="round", lo=3, hi=-1, sep=False)
    a.ellipse(9, 7, 22, 12, "silber", form="round", lo=2, hi=-1)
    a.ellipse(11, 7, 20, 10, "schoko", form="round", lo=3, hi=-1, sep=False)
    a.ellipse(13, 1, 18, 6, "schoko", form="round", lo=3, hi=-1)
    # Vorhänge aus Schokolade über die Ränder (links länger als rechts)
    a.rect(9, 11, 10, 16, "schoko", lo=3, hi=-1, sep=False)
    a.rect(21, 11, 22, 14, "schoko", lo=2, hi=4, sep=False)
    a.rect(6, 20, 7, 25, "schoko", lo=3, hi=-1, sep=False)
    a.rect(24, 20, 25, 23, "schoko", lo=2, hi=4, sep=False)
    a.px([(14, 2), (10, 8), (7, 16), (4, 24)], "silber", -1)
    return a


def b_fabrik():
    """Keksfabrik: Ziegelhalle mit Sägezahndach, Schornstein mit Dampf, gelbe Fenster, Keks-Schild über dem Tor."""
    a = Icon()
    a.rect(23, 3, 27, 16, "rot", form="cyl", lo=1, hi=-2)
    a.ellipse(21, 0, 28, 5, "silber", form="round", lo=4, hi=-1)
    a.poly([(1, 16), (8, 9), (8, 16), (15, 9), (15, 16), (22, 9), (22, 16), (30, 16), (30, 31), (1, 31)], "rot", lo=1, hi=-2)
    for x in (3, 10, 17):
        a.rect(x, 18, x + 4, 22, "gold", lo=4, hi=-2)
    a.rect(24, 18, 28, 22, "gold", lo=3, hi=-3)
    a.rect(10, 25, 21, 31, "holz", lo=1, hi=-2)
    a.ellipse(13, 23, 18, 28, "keks", form="round", lo=3, hi=-1)
    a.px([(14, 25), (16, 26)], "schoko", 1)
    a.px([(3, 18), (10, 18)], "gold", -1)
    return a


def b_zeppelin():
    """Lieferzeppelin: rote Hülle mit Streifen, kleines Leitwerk hinten, darunter eine Gondel mit Keksen."""
    a = Icon()
    a.poly([(24, 3), (30, 1), (30, 7), (26, 7)], "rot", lo=1, hi=-3)
    a.poly([(24, 15), (30, 17), (30, 11), (26, 11)], "rot", lo=0, hi=-4)
    a.ellipse(1, 2, 28, 16, "rot", form="round", lo=1, hi=-1)
    a.px([(x, 9) for x in range(4, 26)], "rot", 2)
    a.line([(9, 15), (11, 21)], 1, "holz", lo=1, hi=-2)
    a.line([(19, 15), (17, 21)], 1, "holz", lo=1, hi=-2)
    a.rect(7, 21, 21, 29, "holz", lo=1, hi=-2)
    a.px([(x, 25) for x in range(8, 21)], "holz", 1)
    for x in (9, 15):
        a.ellipse(x, 17, x + 4, 21, "keks", form="round", lo=3, hi=-1)
        a.px([(x + 1, 18)], "schoko", 1)
    a.px([(6, 5), (7, 4), (8, 4)], "rot", -1)
    return a


def b_bank():
    """Keksbank: Tresor aus Stahl, runde Tür mit Keks als Wappen, Nieten in gleichem Abstand (eine fehlt), Griff."""
    a = Icon()
    a.rect(1, 2, 30, 29, "eisen", lo=1, hi=-2)
    a.ellipse(5, 5, 26, 26, "eisen", form="round", lo=2, hi=-1)
    a.ellipse(8, 8, 23, 23, "eisen", form="round", lo=1, hi=-3)
    mini(a, 11, 11, 20, 20, chips=((0.3, 0.3), (0.7, 0.4), (0.45, 0.75)))
    for x, y in ((3, 4), (28, 4), (3, 27)):
        a.px([(x, y)], "eisen", -1)
    a.px([(28, 27)], "eisen", 2)
    a.rect(27, 13, 29, 18, "gold", lo=3, hi=-1)
    a.rect(1, 30, 6, 31, "eisen", lo=1, hi=3)
    a.rect(25, 30, 30, 31, "eisen", lo=1, hi=3)
    return a


def b_zauber():
    """Zauberküche: Kessel mit lila Gebräu, Blasen, darüber schwebt ein Keks, Funken."""
    a = Icon()
    a.ellipse(3, 13, 28, 31, "kessel", form="round", lo=0, hi=-1)
    a.ellipse(3, 11, 28, 17, "kessel", form="round", lo=1, hi=-1)
    a.ellipse(5, 12, 26, 16, "lila", form="round", lo=2, hi=-2, sep=False)
    a.ellipse(19, 8, 23, 12, "lila", form="round", lo=4, hi=-1)
    a.ellipse(8, 9, 11, 12, "lila", form="round", lo=4, hi=-1)
    mini(a, 11, 0, 20, 9)
    a.rect(1, 14, 3, 16, "kessel", lo=1, hi=-1)
    a.rect(28, 14, 30, 16, "kessel", lo=1, hi=-1)
    a.px([(9, 10), (20, 9)], "lila", -1)
    a.px([(7, 18), (8, 17)], "kessel", -1)
    return a


def b_zeitofen():
    """Zeitofen: Sanduhr mit Holzrahmen, statt Sand rieselt Keksteig, unten liegt schon ein fertiger Keks."""
    top_w = [16, 18, 18, 18, 16, 14, 12, 10, 8, 6, 4]
    glass = disc(top_w + [4, 4] + top_w[::-1], 7, 4)
    a = Icon()
    a.rect(5, 1, 26, 3, "holz", lo=1, hi=-2)
    a.rect(5, 28, 26, 30, "holz", lo=1, hi=-2)
    a.rect(5, 4, 6, 27, "holz", form="cyl", lo=1, hi=-2)
    a.rect(25, 4, 26, 27, "holz", form="cyl", lo=1, hi=-2)
    a.add(glass, "glas", form="cyl", lo=2, hi=-1)
    a.add({(x, y) for (x, y) in glass if 10 <= y <= 14}, "keks", form="cyl", lo=3, hi=-1, sep=False)
    a.px([(15, y) for y in range(15, 22)], "keks", 5)
    a.ellipse(10, 22, 21, 27, "keks", form="round", lo=3, hi=-1)
    a.px([(12, 24), (16, 23), (18, 25)], "schoko", 1)
    a.px([(9, 6), (9, 7), (10, 8)], "glas", -1)
    return a


def b_sterne():
    """Sternen-Bäckerei: Keks als Planet mit Ring, drumherum Sterne (Funkeln), ein kleiner Mond."""
    a = Icon()
    a.ellipse(1, 13, 30, 22, "gold", form="round", lo=3, hi=-2)
    a.cut_ellipse(4, 15, 27, 20)
    pix_cookie = cookie_pix(15.5, 15.5, 9.4, chunks=[(-5, -5, "c"), (3, -6, "c"), (-6, 2, "a"), (2, 1, "d"), (-1, 6, "c")])
    for q, (m, k) in pix_cookie.items():
        a.px([q], m, k)
    a.ellipse(3, 13, 28, 22, "gold", form="round", lo=4, hi=-1)
    a.cut_ellipse(4, 15, 27, 20)
    a.cut({q for q in a.parts[-1].mask if q[1] < 17 and (q[0] - 15.5) ** 2 + ((q[1] - 15.5) * 1.0) ** 2 < 9.4 ** 2})
    a.ellipse(25, 2, 29, 6, "silber", form="round", lo=3, hi=-1)
    a.px([(26, 3)], "silber", -1)
    return a


def m_gebaeude():
    """Gebäude: kleine Bäckerei (Fachwerk hell, rotes Dach, Holztür, Schaufenster), Keks-Schild am Giebel."""
    a = Icon()
    a.rect(4, 14, 27, 30, "papier", lo=5, hi=-2)
    a.poly([(1, 15), (15.5, 3), (30, 15)], "rot", lo=2, hi=-2)
    a.rect(22, 4, 25, 11, "rot", form="cyl", lo=1, hi=-2)
    a.rect(13, 21, 18, 30, "holz", lo=1, hi=-2)
    a.rect(6, 18, 11, 23, "blau", lo=3, hi=-1)
    a.rect(20, 18, 25, 23, "blau", lo=3, hi=-1)
    mini(a, 12, 8, 19, 15)
    a.px([(17, 26)], "gold", -1)
    a.px([(7, 19), (21, 19)], "blau", -1)
    return a


def m_upgrades():
    """Upgrades: kräftiger grüner Pfeil nach oben (wie im Spiel), heller Saum links, ein Keks am Fuß."""
    a = Icon()
    a.poly([(15.5, 1), (29, 15), (21, 15), (21, 28), (10, 28), (10, 15), (2, 15)], "smaragd", lo=2, hi=-2)
    mini(a, 11, 19, 20, 28)
    a.px([(14, 3), (13, 4), (12, 5)], "smaragd", -1)
    return a


def m_erfolge():
    """Erfolge: goldener Pokal mit Henkeln, Keks als Wappen, Holzsockel."""
    a = Icon()
    a.ellipse(1, 5, 10, 15, "gold", form="round", lo=0, hi=-3)
    a.ellipse(21, 5, 30, 15, "gold", form="round", lo=0, hi=-3)
    a.cut_ellipse(4, 8, 7, 12)
    a.cut_ellipse(24, 8, 27, 12)
    a.poly([(6, 2), (25, 2), (24, 11), (20, 17), (11, 17), (7, 11)], "gold", form="cyl", lo=0, hi=-2)
    a.rect(13, 17, 18, 21, "gold", form="cyl", lo=0, hi=-3)
    a.rect(9, 22, 22, 24, "gold", lo=1, hi=-3)
    a.rect(7, 25, 24, 29, "holz", lo=2, hi=-2)
    mini(a, 11, 4, 20, 12)
    a.px([(8, 4), (9, 4), (8, 5)], "gold", -1)
    return a


def m_aufstieg():
    """Aufstieg: goldene Krone mit fünf Zacken (Kugeln an den Spitzen), Steine in der Mitte, ein Keks als mittlerer Stein."""
    a = Icon()
    a.poly([(2, 10), (8, 17), (11, 8), (15.5, 15), (20, 8), (23, 17), (29, 10), (27, 25), (4, 25)], "gold", lo=1, hi=-2)
    a.rect(4, 24, 27, 29, "gold", lo=1, hi=-3)
    for x, y in ((2, 9), (11, 7), (20, 7), (29, 9)):
        a.ellipse(x - 2, y - 2, x + 1, y + 1, "gold", form="round", lo=3, hi=-1)
    a.ellipse(13, 3, 18, 8, "gold", form="round", lo=3, hi=-1)
    a.ellipse(6, 25, 9, 28, "rot", form="round", lo=3, hi=-1)
    mini(a, 13, 24, 18, 29, chips=((0.5, 0.5),))
    a.ellipse(22, 25, 25, 28, "blau", form="round", lo=3, hi=-1)
    a.px([(5, 13), (6, 14)], "gold", -1)
    return a


def m_rangliste():
    """Rangliste: Treppchen 1 (Gold, Mitte), 2 (Silber, links), 3 (Holz, rechts) mit Ziffern, oben auf Platz 1 ein Keks."""
    a = Icon()
    a.rect(11, 12, 20, 31, "gold", lo=1, hi=-2)
    a.rect(1, 18, 10, 31, "silber", lo=1, hi=-2)
    a.rect(21, 22, 30, 31, "holz", lo=2, hi=-2)
    digits = {1: [".X.", "XX.", ".X.", ".X.", "XXX"], 2: ["XX.", "..X", ".X.", "X..", "XXX"], 3: ["XX.", "..X", ".X.", "..X", "XX."]}
    for n, (x0, y0, mat) in {1: (14, 15, "gold"), 2: (4, 21, "silber"), 3: (24, 25, "holz")}.items():
        for (x, y), c in grid(digits[n], x0, y0).items():
            a.px([(x, y)], mat, 0)
    mini(a, 10, 1, 21, 11, chips=((0.3, 0.35), (0.7, 0.4), (0.45, 0.75)))
    return a


def m_statistik():
    """Statistik: helles Blatt mit vier Balken in klaren Farben (steigend, einer fällt aus der Reihe), Grundlinie."""
    a = Icon()
    a.rect(2, 2, 29, 29, "silber", lo=4, hi=-1)
    for x, top, mat in ((5, 18, "keks"), (11, 12, "smaragd"), (17, 15, "rot"), (23, 6, "gold")):
        a.rect(x, top, x + 4, 25, mat, form="cyl", lo=2, hi=-2)
    a.px([(x, 26) for x in range(4, 28)], "silber", 1)
    a.px([(23, 6), (24, 6)], "gold", -1)
    return a


def m_hilfe():
    """Hilfe: rotes Buch mit hellen Seiten, auf dem Deckel ein goldenes Fragezeichen."""
    a = Icon()
    a.rect(6, 4, 29, 29, "papier", lo=4, hi=-2)
    a.rect(3, 2, 26, 27, "rot", lo=1, hi=-2)
    a.rect(3, 2, 5, 27, "rot", lo=0, hi=2)
    q = ["..XXXX..", ".XX..XX.", ".....XX.", "....XX..", "...XX...", "...XX...", "........", "...XX..."]
    for (x, y), c in grid(q, 11, 8).items():
        a.px([(x, y)], "gold", 6 if y < 11 else 5)
    a.px([(7, 4), (8, 4), (7, 5)], "rot", -1)
    return a


def m_regen():
    """Keksregen: dicke Wolke (hell oben, kühl unten), darunter fallen drei Kekse (versetzt, verschieden groß)."""
    a = Icon()
    a.ellipse(1, 6, 13, 16, "silber", form="round", lo=3, hi=-1)
    a.ellipse(8, 1, 22, 15, "silber", form="round", lo=3, hi=-1)
    a.ellipse(18, 5, 30, 16, "silber", form="round", lo=3, hi=-1)
    a.rect(4, 12, 27, 16, "silber", lo=3, hi=-2)
    mini(a, 3, 19, 10, 26)
    mini(a, 13, 23, 21, 31, chips=((0.3, 0.3), (0.7, 0.5), (0.4, 0.75)))
    mini(a, 23, 18, 29, 24, chips=((0.4, 0.4),))
    a.px([(11, 3), (12, 3)], "silber", -1)
    return a


def m_klick():
    """Klicken: großer Keks, davor der Mauszeiger (weiß, dunkle Kontur), kleine Klick-Striche."""
    a = Icon()
    pix = cookie_pix(19, 18, 11.5, chunks=[(-6, -6, "c"), (2, -8, "c"), (5, -2, "d"), (-2, 2, "a"), (4, 6, "c"), (-7, 5, "c")])
    for q, (m, k) in pix.items():
        a.px([q], m, k)
    arrow = ["X..........", "XX.........", "XWX........", "XWWX.......", "XWWWX......", "XWWWWX.....", "XWWWWWX....",
             "XWWWWWWX...", "XWWWWWWWX..", "XWWWWXXXXX.", "XWWXWX.....", "XWX.XWX....", "XX..XWX....", "X....XWX...", ".....XWX...",
             "......XX..."]
    for (x, y), c in grid(arrow, 2, 4).items():
        a.px([(x, y)], None if c == "X" else "papier", 0 if c == "X" else (8 if y < 10 else 7))
    for pts in (((1, 1), (2, 2)), ((6, 0), (6, 1)), ((0, 6), (1, 6))):
        a.px(list(pts), "gold", 6)
    return a


ICONS = {"cookie": c_cookie, "gold": c_gold, "b_loeffel": b_loeffel, "b_schuessel": b_schuessel, "b_blech": b_blech, "b_ofen": b_ofen,
         "b_lehrling": b_lehrling, "b_muehle": b_muehle, "b_zucker": b_zucker, "b_schoko": b_schoko, "b_fabrik": b_fabrik,
         "b_zeppelin": b_zeppelin, "b_bank": b_bank, "b_zauber": b_zauber, "b_zeitofen": b_zeitofen, "b_sterne": b_sterne,
         "m_gebaeude": m_gebaeude, "m_upgrades": m_upgrades, "m_erfolge": m_erfolge, "m_aufstieg": m_aufstieg,
         "m_rangliste": m_rangliste, "m_statistik": m_statistik, "m_hilfe": m_hilfe, "m_regen": m_regen, "m_klick": m_klick}

if __name__ == "__main__":
    from pixkit import sheet
    sheet([(n, f().render()) for n, f in ICONS.items()], sys.argv[1], scale=4, cols=7)
