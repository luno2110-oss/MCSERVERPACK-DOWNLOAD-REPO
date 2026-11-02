"""Hub-Icons (Hauptmenü, One-Block, Insel, Suche, Hinweise) im Stil der Referenzen, gezeichnet mit der Icon-Datenbank:
Formen und Schattierung aus tools/icon_db/pixkit.py, Farben nur aus den Referenz-Rampen (tools/icon_db/materials.py),
Regeln in tools/icon_db/STYLE.md und CLAUDE.md (1 px Kontur im warmen Fast-Schwarz, Licht oben links, Lichter wärmer,
Schatten kühler, Muster gleichmäßig und einmal gebrochen, Form symmetrisch, Licht und ein Detail asymmetrisch).

Werkzeuge (Jobs, Tempo) im Minecraft-Look: Umriss und Farbaufteilung der Vanilla-Items in doppelter Größe (TOOLS), mit
Rampen-Farben und 1-px-Licht an den Kanten.

build_hub_icons.py nimmt TILES (Name -> Funktion mit render()) und schreibt tools/assets/hub/art_<name>.png.
"""
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "icon_db"))
from materials import OUTLINE, RAMPS, rgb  # noqa: E402
from pixkit import Icon  # noqa: E402


# ---------------------------------------------------------------------------------------------------------- Hilfen
class Img:
    """Fertiges Bild mit der Schnittstelle render()."""

    def __init__(self, img):
        self.img = img

    def render(self):
        return self.img


def layer(pix, size=32):
    """{(x, y): (Material, Stufe)} -> Bild mit eigener Kontur (vorn liegende Teile bekommen so ihre Trennlinie)."""
    a = Icon(size)
    for (x, y), (m, k) in pix.items():
        a.px([(x, y)], m, k)
    return a.render()


def stack(*imgs):
    out = Image.new("RGBA", imgs[0].size, (0, 0, 0, 0))
    for im in imgs:
        out.alpha_composite(im)
    return out


def edge_tone(mask, base, lo, hi, up=2, left=1, down=-2, right=-1):
    """Stufe je Pixel aus den freien Nachbarn: oben/links Licht, unten/rechts Schatten."""
    out = {}
    for (x, y) in mask:
        t = base
        if (x, y - 1) not in mask:
            t += up
        if (x - 1, y) not in mask:
            t += left
        if (x, y + 1) not in mask:
            t += down
        if (x + 1, y) not in mask:
            t += right
        out[(x, y)] = max(lo, min(hi, t))
    return out


def grid(rows, ox, oy):
    """ASCII-Raster -> {(x, y): Zeichen} (Punkt = leer)."""
    return {(ox + i, oy + j): c for j, r in enumerate(rows) for i, c in enumerate(r) if c != "."}


def disc(widths, x0, y0):
    """Runde Scheibe aus Zeilenbreiten (pixelgenauer Kreis, symmetrisch), linke obere Ecke (x0, y0)."""
    w = max(widths)
    return {(x0 + (w - n) // 2 + i, y0 + j) for j, n in enumerate(widths) for i in range(n)}


def emboss(a, pts, mat, light, dark):
    """Erhabenes Motiv: Motiv hell, eine Kante unten rechts dunkel."""
    s = set(pts)
    a.px(pts, mat, light)
    a.px([(x + 1, y + 1) for x, y in pts if (x + 1, y + 1) not in s], mat, dark)


def star(cx, cy):
    """Stern 9 x 7 (symmetrisch)."""
    rows = ["....X....", "...XXX...", "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "..XX.XX..", ".XX...XX."]
    return [(cx - 4 + i, cy - 3 + j) for j, r in enumerate(rows) for i, c in enumerate(r) if c == "X"]


def coin(a, x0, y0, x1, y1, motif=True):
    a.ellipse(x0, y0, x1, y1, "gold", form="round", lo=0, hi=-2, gloss="auto")
    if x1 - x0 < 10:
        return
    a.ellipse(x0 + 4, y0 + 4, x1 - 4, y1 - 4, "gold", form="round", lo=2, hi=-3, sep=False, rim=False)
    cx, cy = (x0 + x1) / 2 + 0.5, (y0 + y1) / 2 + 0.5
    r = (x1 - x0) / 2 - 3.4
    for t in range(0, 360, 3):
        rr = math.radians(t)
        x, y = round(cx - 0.5 + r * math.cos(rr)), round(cy - 0.5 + r * math.sin(rr))
        a.px([(x, y)], "gold", 1 if math.cos(rr) + math.sin(rr) < 0 else 6)
    if motif:
        emboss(a, star(round(cx - 0.5), round(cy)), "gold", 7, 2)


def bolt(ox, oy):
    """Blitz 9 x 13 aus Gold, Zickzack in gleichmäßigen Stufen."""
    rows = ["....#####",
            "...#####.",
            "...####..",
            "..####...",
            "..#######",
            ".#######.",
            "....####.",
            "...####..",
            "...###...",
            "..###....",
            "..##.....",
            ".##......",
            ".#......."]
    pts = set(grid(rows, ox, oy))
    return layer({p: ("gold", k) for p, k in edge_tone(pts, 5, 2, 8).items()})


def iso_map(T, L, R, B, H, size=32):
    """Pixelgenauer Iso-Würfel (2:1): Pixel -> (Seite, u, v) mit Oberseite T-R-B-L und Seiten der Höhe H."""
    def inv(o, a, b, x, y):
        det = a[0] * b[1] - a[1] * b[0]
        dx, dy = x - o[0], y - o[1]
        return (dx * b[1] - dy * b[0]) / det, (a[0] * dy - a[1] * dx) / det

    face = {}
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
    return face


def iso_block(top, side_l, side_r, pal, outline=OUTLINE, shade=(1.0, 0.86, 0.64), size=32):
    """Iso-Würfel aus 7 x 7 Texturen (Zeichen -> Farbe): jeder Texel ist oben eine 4 x 2-Raute und seitlich 2 x 2 px.
    Licht: oben hell, links mittel, rechts dunkel, nach unten etwas dunkler; helle Kanten oben, dunkle Mittelnaht."""
    face = iso_map((16, 2), (2, 9), (30, 9), (16, 16), 14, size)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    tex = {"top": top, "left": side_l, "right": side_r}
    for (x, y), (f, u, v) in face.items():
        t = tex[f]
        n = len(t)
        r, g, b = rgb(pal[t[min(n - 1, int(v * n))][min(n - 1, int(u * n))]])
        k = shade[0] if f == "top" else shade[1] if f == "left" else shade[2]
        if f == "top":
            k *= 1.04 - 0.08 * (u + v) / 2
            if u < 0.08 or v < 0.08:
                k *= 1.16
        else:
            k *= 1.0 - 0.16 * v
            if f == "right" and u < 0.07:
                k *= 0.78
            if f == "left" and u > 0.93:
                k *= 0.92
            if v < 0.07:
                k *= 1.12
        px[x, y] = (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), 255)
    oc = rgb(outline) + (255,)
    for y in range(size):
        for x in range(size):
            if (x, y) not in face and any((x + dx, y + dy) in face for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = oc
    return img


# ---------------------------------------------------------------------------------------------------------- Münzen
def i_wirtschaft():
    a = Icon()
    for y in (24, 20, 16):
        a.ellipse(3, y, 20, y + 6, "gold", form="cyl", lo=0, hi=-3)
    a.ellipse(3, 12, 20, 18, "gold", form="round", lo=3, hi=-2)
    for y in (25, 21):
        a.ellipse(17, y, 29, y + 5, "gold", form="cyl", lo=0, hi=-3)
    a.ellipse(17, 18, 29, 23, "gold", form="round", lo=3, hi=-2)
    coin(a, 12, 0, 30, 18)
    return a


def i_ob_geld():
    a = Icon()
    coin(a, 2, 2, 25, 25)
    # Plus-Zeichen (grün) unten rechts: der Bonus
    a.rect(23, 18, 26, 29, "smaragd", lo=2, hi=-2)
    a.rect(19, 22, 30, 25, "smaragd", lo=2, hi=-2)
    return a


# ---------------------------------------------------------------------------------------------------------- Werkzeuge
# Vanilla-Werkzeuge (16 px): Ziffern = Helligkeitsrang des Kopfs (1 dunkel .. 5 hell), A-D = Stiel (dunkel .. hell)
TOOLS = {
    "pickaxe": ["................",
                "................",
                "......22222.....",
                ".....2543342BC..",
                "......211133DA..",
                "..........B431..",
                ".........BCA341.",
                "........BDA.131.",
                ".......BCA..131.",
                "......BDA...141.",
                ".....BCA....151.",
                "....BDA......1..",
                "...BCA..........",
                "..BDA...........",
                "..AA............",
                "................"],
    "shovel": ["................",
               "................",
               "...........221..",
               "..........25531.",
               ".........254351.",
               "........2543451.",
               ".........B3451..",
               "........BDA51...",
               ".......BDA.1....",
               "......BCA.......",
               ".....BDA........",
               "....BCA.........",
               "..BBDA..........",
               "..BDA...........",
               "...AA...........",
               "................"],
}
STICK = {"A": 1, "B": 2, "C": 3, "D": 4}


def tool(kind, mat, dx=0, dy=0, flip=False):
    """Werkzeug im Minecraft-Look: Vanilla-Umriss in doppelter Größe, Kopf in Stufen der Rampe, Stiel wie der
    Vanilla-Stock, dazu 1 px Licht an jeder Oberkante und Schatten an jeder Unterkante. -> (Kopf, Stiel)."""
    n = len(RAMPS[mat])
    hp, wp = {}, {}
    for (x, y), c in grid(TOOLS[kind], 0, 0).items():
        for ex in (0, 1):
            for ey in (0, 1):
                X, Y = 2 * x + ex + dx, 2 * y + ey + dy
                if flip:
                    X = 31 - X
                if c in STICK:
                    wp[(X, Y)] = ("stiel", STICK[c])
                else:
                    hp[(X, Y)] = (mat, round(1 + (int(c) - 1) * (n - 2) / 4))
    for d in (hp, wp):
        keys = set(d)
        for (x, y), (m, k) in list(d.items()):
            if (x, y - 1) not in keys:
                d[(x, y)] = (m, min(len(RAMPS[m]) - 1, k + 1))
            elif (x, y + 1) not in keys:
                d[(x, y)] = (m, max(0, k - 1))
    return layer(hp), layer(wp)


def i_jobs():
    """Jobs: Diamant-Spitzhacke vorn, Eisenschaufel gekreuzt dahinter."""
    sh, sw = tool("shovel", "silber", dx=-1, dy=2, flip=True)
    ph, pw = tool("pickaxe", "tuerkis", dx=1, dy=-1)
    return Img(stack(sw, sh, pw, ph))


def i_ob_tempo():
    """Tempo: Diamant-Spitzhacke mit goldenem Blitz."""
    ph, pw = tool("pickaxe", "tuerkis", dx=-2, dy=-1)
    return Img(stack(pw, ph, bolt(21, 17)))


def i_duelle():
    a = Icon()
    for flip in (False, True):
        def P(x, y):
            return (31 - x, y) if flip else (x, y)
        a.poly([P(3, 2), P(7, 2), P(22, 17), P(19, 20)], "silber", lo=1, hi=-1)
        a.line([P(17, 23), P(24, 16)], 3, "gold", lo=1, hi=-2)
        a.line([P(22, 21), P(28, 27)], 3, "holz", lo=1, hi=-2)
        if flip:
            a.rect(2, 27, 4, 29, "gold", lo=2, hi=-2)
        else:
            a.rect(27, 27, 29, 29, "gold", lo=2, hi=-2)
    a.px([(4, 3), (5, 3)], "silber", -1)
    return a


# ---------------------------------------------------------------------------------------------------------- Kompass, Uhr
def i_menu():
    a = Icon()
    a.rect(14, 0, 17, 3, "gold", lo=1, hi=-2)
    a.ellipse(1, 2, 30, 31, "gold", form="round", lo=0, hi=-2)
    a.ellipse(5, 6, 26, 27, "papier", form="round", lo=4, hi=-1, sep=True)
    # Nadel: rot nach Nordost, silber nach Südwest (Symmetrie gebrochen durch die Neigung)
    a.poly([(16, 16), (22, 9), (17, 17)], "rot", lo=2, hi=-1, sep=False)
    a.poly([(16, 16), (22, 9), (15, 15)], "rot", lo=3, hi=-1, sep=False)
    a.poly([(15, 17), (9, 24), (16, 18)], "silber", lo=1, hi=-2, sep=False)
    a.poly([(15, 17), (9, 24), (14, 16)], "silber", lo=3, hi=-1, sep=False)
    a.px([(15, 16), (16, 17), (15, 17), (16, 16)], "gold", 3)
    for x, y in ((15, 8), (16, 8), (24, 16), (24, 17), (15, 25), (16, 25), (7, 16), (7, 17)):
        a.px([(x, y)], "papier", 2)
    a.px([(8, 7), (9, 6)], "gold", -1)
    return a


def i_ob_cooldown():
    a = Icon()
    a.rect(13, 0, 18, 3, "silber", lo=1, hi=-2)
    a.rect(24, 4, 27, 7, "silber", lo=1, hi=-2)
    a.ellipse(2, 3, 29, 30, "silber", form="round", lo=0, hi=-2)
    a.ellipse(6, 7, 25, 26, "papier", form="round", lo=5, hi=-1)
    # Restzeit als blauer Kreissektor (Viertel)
    a.poly([(16, 16), (16, 8), (19, 8), (22, 10), (24, 13), (24, 16)], "blau", lo=4, hi=-2, sep=False)
    a.line([(16, 16), (16, 9)], 2, "rot", lo=2, hi=-2, sep=False)
    a.px([(15, 16), (16, 16)], "eisen", 2)
    a.px([(9, 8), (10, 7)], "silber", -1)
    return a


def c_daily():
    a = Icon()
    a.rect(4, 4, 27, 29, "papier", lo=4, hi=-1)
    a.rect(4, 4, 27, 10, "rot", lo=1, hi=-2)
    a.rect(8, 1, 10, 7, "eisen", lo=2, hi=-2)
    a.rect(21, 1, 23, 7, "eisen", lo=2, hi=-2)
    # Raster der Tage (Muster), ein Tag abgehakt (Bruch)
    for gx in (8, 14, 20):
        for gy in (14, 20):
            a.rect(gx, gy, gx + 3, gy + 3, "papier", lo=2, hi=5, sep=False)
    a.line([(19, 21), (21, 24), (26, 16)], 2, "smaragd", lo=3, hi=-2)
    return a


# ---------------------------------------------------------------------------------------------------------- Gebäude
def i_bank():
    a = Icon()
    a.poly([(16, 1), (30, 9), (2, 9)], "silber", lo=1, hi=-2)
    a.rect(2, 9, 29, 11, "silber", lo=2, hi=-2)
    for x in (4, 10, 18, 24):
        a.rect(x, 12, x + 3, 24, "silber", form="cyl", lo=1, hi=-2)
    a.rect(1, 25, 30, 27, "silber", lo=2, hi=-2)
    a.rect(0, 28, 31, 30, "silber", lo=1, hi=-3)
    coin(a, 12, 2, 19, 8, motif=False)
    a.px([(15, 13), (16, 13)], "gold", 5)
    # Tür zwischen den Säulen (Bruch im Säulen-Muster)
    a.rect(14, 15, 17, 24, "holz", lo=1, hi=-3)
    return a


def i_insel():
    a = Icon()
    a.poly([(3, 16), (29, 16), (23, 22), (18, 30), (14, 30), (9, 22)], "erde", lo=1, hi=-1)
    a.poly([(9, 22), (13, 22), (12, 27)], "eisen", lo=2, hi=4, sep=False)
    a.poly([(19, 21), (23, 21), (19, 26)], "eisen", lo=2, hi=4, sep=False)
    a.ellipse(2, 12, 30, 19, "gras", form="round", lo=1, hi=-1)
    a.rect(21, 5, 22, 14, "holz", form="cyl", lo=1, hi=-2)
    a.ellipse(16, 0, 27, 9, "gras", form="round", lo=0, hi=-2)
    a.rect(6, 10, 10, 13, "holz", lo=2, hi=-2)
    a.px([(8, 11)], "gold", -2)
    return a


def is_biom():
    a = Icon()
    a.poly([(3, 17), (29, 17), (23, 23), (17, 31), (15, 31), (9, 23)], "erde", lo=1, hi=-1)
    a.rect(2, 14, 15, 18, "gras", lo=1, hi=-1)
    a.rect(16, 14, 29, 18, "papier", lo=5, hi=-1)
    a.rect(8, 7, 9, 14, "holz", form="cyl", lo=1, hi=-2)
    a.ellipse(1, 0, 15, 9, "rosa", form="round", lo=1, hi=-1)
    a.rect(21, 4, 24, 14, "gras", form="cyl", lo=0, hi=-3)
    a.rect(18, 7, 20, 10, "gras", form="cyl", lo=0, hi=-3)
    a.rect(25, 8, 27, 11, "gras", form="cyl", lo=0, hi=-3)
    return a


def i_grundstuecke():
    a = Icon()
    # flache Iso-Platte mit Gras, Zaunpfosten an den Ecken, Fahne
    a.poly([(16, 12), (30, 19), (16, 26), (2, 19)], "gras", lo=1, hi=-1)
    a.poly([(2, 19), (16, 26), (16, 30), (2, 23)], "erde", lo=2, hi=-1)
    a.poly([(16, 26), (30, 19), (30, 23), (16, 30)], "erde", lo=0, hi=4)
    for x, y in ((3, 15), (28, 15), (15, 9)):
        a.rect(x, y, x + 1, y + 5, "holz", form="cyl", lo=1, hi=-2)
    a.rect(15, 1, 16, 18, "eisen", form="cyl", lo=2, hi=-2)
    a.poly([(17, 1), (26, 4), (17, 8)], "rot", lo=2, hi=-1)
    return a


# ---------------------------------------------------------------------------------------------------------- Edles
def i_heiraten():
    a = Icon()
    a.ellipse(1, 9, 20, 28, "gold", form="round", lo=0, hi=-2)
    a.cut_ellipse(5, 13, 16, 24)
    a.ellipse(12, 11, 31, 30, "silber", form="round", lo=0, hi=-2)
    a.cut_ellipse(16, 15, 27, 26)
    # Herz-Diamant oben auf dem goldenen Ring
    a.poly([(7, 7), (10, 3), (14, 7), (10, 11)], "rosa", lo=2, hi=-1, gloss=[(9, 5)])
    a.px([(3, 13), (4, 12)], "gold", -1)
    return a


def i_turnier():
    a = Icon()
    a.poly([(2, 8), (9, 16), (16, 5), (23, 16), (30, 8), (27, 25), (5, 25)], "gold", lo=1, hi=-2)
    a.rect(5, 23, 27, 28, "gold", lo=1, hi=-3)
    for x, mat in ((8, "rot"), (15, "smaragd"), (22, "blau")):
        a.ellipse(x, 23, x + 3, 26, mat, form="round", lo=2, hi=-1, gloss=[(x + 1, 24)])
    for x, y in ((2, 8), (16, 5), (30, 8)):
        a.ellipse(x - 2, y - 3, x + 1, y, "gold", form="round", lo=3, hi=-1)
    a.px([(6, 13), (7, 14)], "gold", -1)
    return a


def is_pokal():
    a = Icon()
    a.ellipse(1, 5, 10, 15, "gold", form="round", lo=0, hi=-3)
    a.ellipse(21, 5, 30, 15, "gold", form="round", lo=0, hi=-3)
    a.cut_ellipse(4, 8, 7, 12)
    a.cut_ellipse(24, 8, 27, 12)
    a.poly([(6, 2), (25, 2), (24, 11), (20, 17), (11, 17), (7, 11)], "gold", form="cyl", lo=0, hi=-2)
    a.rect(13, 17, 18, 21, "gold", form="cyl", lo=0, hi=-3)
    a.rect(9, 22, 22, 24, "gold", lo=1, hi=-3)
    a.rect(7, 25, 24, 29, "holz", lo=2, hi=-2)
    a.rect(12, 26, 19, 27, "gold", lo=4, hi=-3)
    emboss(a, star(15, 8), "gold", -2, 2)
    a.px([(8, 4), (9, 4), (8, 5)], "gold", -1)
    return a


def i_versicherung():
    a = Icon()
    a.poly([(4, 3), (16, 0), (27, 3), (27, 15), (22, 24), (16, 30), (10, 24), (4, 15)], "silber", lo=0, hi=-2)
    a.poly([(8, 6), (16, 4), (23, 6), (23, 15), (19, 22), (16, 26), (12, 22), (8, 15)], "blau", form="round", lo=2, hi=-2)
    a.poly([(16, 4), (23, 6), (23, 15), (19, 22), (16, 26)], "blau", lo=1, hi=5, sep=False, rim=False)
    a.line([(11, 14), (14, 18), (21, 10)], 3, "silber", lo=5, hi=-1)
    a.px([(10, 7), (11, 7), (9, 8)], "blau", -1)
    return a


def i_titel():
    a = Icon()
    # Banner mit eingeschnittenen Enden, Stern in der Mitte
    a.poly([(0, 11), (6, 11), (6, 23), (0, 23), (3, 17)], "rot", lo=0, hi=-3)
    a.poly([(31, 11), (25, 11), (25, 23), (31, 23), (28, 17)], "rot", lo=0, hi=-3)
    a.rect(4, 8, 27, 21, "rot", lo=1, hi=-2)
    a.rect(4, 8, 27, 9, "gold", lo=3, hi=-2)
    a.rect(4, 20, 27, 21, "gold", lo=1, hi=-3)
    emboss(a, star(15, 15), "gold", -2, 1)
    return a


def c_deko():
    a = Icon()
    a.poly([(8, 18), (24, 18), (22, 30), (10, 30)], "holz", lo=2, hi=-2)
    a.rect(6, 16, 26, 19, "holz", lo=3, hi=-1)
    a.line([(16, 16), (16, 8)], 2, "gras", lo=1, hi=4)
    a.ellipse(18, 10, 24, 14, "gras", form="round", lo=1, hi=-2)
    for cx, cy in ((16, 2), (12, 6), (20, 6), (16, 10)):
        a.ellipse(cx - 3, cy - 2, cx + 3, cy + 3, "rosa", form="round", lo=2, hi=-1)
    a.ellipse(14, 5, 18, 8, "gold", form="round", lo=4, hi=-1, sep=False)
    return a


# ---------------------------------------------------------------------------------------------------------- Leute
def head(a, x, y, hair, shirt, eyes="blau"):
    a.rect(x, y + 13, x + 13, y + 20, shirt, lo=1, hi=-2)
    a.rect(x + 1, y, x + 12, y + 12, "papier", lo=5, hi=-1)
    a.rect(x + 1, y, x + 12, y + 3, hair, lo=1, hi=-2)
    a.px([(x + 1, y + 4), (x + 12, y + 4)], hair, 2)
    a.px([(x + 3, y + 7), (x + 4, y + 7), (x + 9, y + 7), (x + 10, y + 7)], eyes, 2)
    a.px([(x + 3, y + 6), (x + 9, y + 6)], "papier", -1)
    a.px([(x + 5, y + 10), (x + 6, y + 10), (x + 7, y + 10), (x + 8, y + 10)], "papier", 3)


def i_mitglieder():
    a = Icon()
    head(a, 16, 3, "holz", "gras", "smaragd")
    head(a, 2, 9, "erde", "blau")
    return a


# ---------------------------------------------------------------------------------------------------------- Schätze
def ob_beute():
    a = Icon()
    a.poly([(4, 4), (27, 4), (29, 12), (2, 12)], "holz", lo=0, hi=-3)
    a.ellipse(4, 8, 27, 18, "gold", form="round", lo=2, hi=-1)
    a.ellipse(7, 6, 14, 12, "gold", form="round", lo=3, hi=-1)
    a.ellipse(16, 5, 24, 11, "gold", form="round", lo=3, hi=-1)
    a.rect(2, 13, 29, 29, "holz", lo=1, hi=-3)
    for x in (9, 16, 23):
        a.px([(x, y) for y in range(19, 29)], "holz", 1)
    a.px([(16, y) for y in range(22, 26)], "holz", 4)
    a.rect(2, 16, 29, 18, "eisen", lo=2, hi=-2)
    a.rect(2, 13, 4, 29, "eisen", lo=2, hi=-2)
    a.rect(27, 13, 29, 29, "eisen", lo=2, hi=-2)
    a.rect(13, 16, 18, 22, "gold", lo=2, hi=-2)
    a.px([(15, 18), (16, 18), (15, 19), (16, 19)], "holz", 0)
    a.ellipse(19, 6, 24, 11, "smaragd", form="round", lo=2, hi=-1, gloss=[(20, 7)])
    a.ellipse(7, 7, 11, 11, "rot", form="round", lo=2, hi=-1, gloss=[(8, 8)])
    return a


def mini_clover(ox, oy):
    """Kleines Kleeblatt 9 x 9: vier Blätter mit Kerbe, die Lücken werden zur Kontur (klare Trennlinien)."""
    rows = [".oo...oo.",
            "oooo.oooo",
            "oooo.oooo",
            ".ooo.ooo.",
            "....+....",
            ".ooo.ooo.",
            "oooo.oooo",
            "oooo.oooo",
            ".oo...oo."]
    cells = grid(rows, ox, oy)
    pts = {p for p, c in cells.items() if c == "o"}
    pix = {}
    for (x, y) in pts:
        i, j = x - ox, y - oy
        # Blatt oben links am hellsten, unten rechts am dunkelsten (Licht oben links)
        k = 5 if (i < 4 and j < 4) else 3 if (i > 4 and j > 4) else 4
        if (x, y - 1) not in pts:
            k += 2
        elif (x, y + 1) not in pts:
            k -= 1
        pix[(x, y)] = ("gras", max(1, min(8, k)))
    pix[(ox + 4, oy + 4)] = ("gras", 2)
    return pix


def i_tagespot():
    """Tagespot: der alte dunkle Kessel (Füße, runder Bauch, breiter Rand) voller Gold, Kleeblatt für das Glück."""
    a = Icon()
    a.ellipse(6, 6, 26, 18, "gold", form="round", lo=2, hi=-1)
    for x, y in ((8, 6), (14, 3), (20, 6)):
        a.ellipse(x, y, x + 6, y + 6, "gold", form="round", lo=0, hi=-2, gloss="auto")
    a.rect(5, 26, 8, 30, "kessel", lo=0, hi=2)
    a.rect(23, 26, 26, 30, "kessel", lo=0, hi=2)
    a.ellipse(4, 13, 27, 30, "kessel", form="round", lo=0, hi=-1)
    a.ellipse(3, 11, 28, 17, "kessel", form="round", lo=1, hi=-1)
    a.ellipse(6, 12, 25, 16, "gold", form="round", lo=3, hi=-1, sep=False)
    a.px([(10, 13), (16, 12), (21, 13)], "gold", -1)
    a.px([(7, 19), (7, 20), (8, 18)], "kessel", -1)
    return Img(stack(a.render(), layer(mini_clover(22, 0))))


def clover(cx, cy, R=8.6):
    """Vierblättriges Kleeblatt um die Pixel-Ecke (cx, cy): vier Herzblätter (je eine kleine Kuppel, Licht oben links),
    Trennlinien genau auf den Diagonalen, Mittelrippe an jeder Kerbe, Stiel nach rechts unten."""
    leaves, lines = {}, set()
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            if r > R + 0.5:
                continue
            if abs(abs(dx) - abs(dy)) < 0.01:
                if r < R - 2.2:
                    lines.add((x, y))
                continue
            if abs(dx) < -dy:
                leaf, phi = 0, math.degrees(math.atan2(dx, -dy))
            elif dx > abs(dy):
                leaf, phi = 1, math.degrees(math.atan2(dy, dx))
            elif dy > abs(dx):
                leaf, phi = 2, math.degrees(math.atan2(-dx, dy))
            else:
                leaf, phi = 3, math.degrees(math.atan2(-dy, -dx))
            lim = R
            if abs(phi) < 7:
                lim -= 2.4                     # Herz-Kerbe
            if abs(phi) > 32:
                lim -= (abs(phi) - 32) * 0.33  # Lücke zwischen den Blättern
            if r <= lim:
                leaves[(x, y)] = leaf
    pix = {}
    for i in range(4):
        part = {p for p, l in leaves.items() if l == i}
        lx = sum(p[0] for p in part) / len(part)
        ly = sum(p[1] for p in part) / len(part)
        bias = 1 if (lx < cx and ly < cy) else (-1 if (lx > cx and ly > cy) else 0)
        for p, k in edge_tone(part, 4, 1, 8, up=2, left=2, down=-2, right=-2).items():
            pix[p] = ("gras", max(1, min(8, k + bias)))
    for p in lines:
        pix[p] = ("gras", 0)
    for (x, y), l in leaves.items():
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        if (l in (0, 2) and abs(dx) < 0.6 and 3.2 < abs(dy) < 5.6) or (l in (1, 3) and abs(dy) < 0.6 and 3.2 < abs(dx) < 5.6):
            pix[(x, y)] = ("gras", 1)
    for x, y in ((cx + 1, cy + 6), (cx + 2, cy + 7), (cx + 2, cy + 8), (cx + 3, cy + 9), (cx + 4, cy + 10)):
        pix[(x, y)] = ("gras", 3)
        pix[(x + 1, y)] = ("gras", 1)
    return layer(pix)


def ob_glueck():
    """Glück: goldenes Medaillon, darauf das Kleeblatt mit eigener schwarzer Kontur."""
    a = Icon()
    a.ellipse(1, 1, 30, 30, "gold", form="round", lo=0, hi=-2)
    a.ellipse(4, 4, 27, 27, "gold", form="round", lo=3, hi=-2, sep=False, rim=False)
    a.px([(7, 5), (8, 4), (6, 6)], "gold", -1)
    return Img(stack(a.render(), clover(15, 14)))


def ob_xp():
    """XP-Bonus: Erfahrungskugel mit klarem Kern (dunkelgrüner Ring, helles Gelb, Glanz oben links)."""
    a = Icon()
    a.ellipse(2, 2, 25, 25, "gras", form="round", lo=0, hi=-1)
    a.ellipse(21, 19, 29, 27, "gras", form="round", lo=1, hi=-1, gloss="auto")
    a.ellipse(23, 1, 29, 7, "gras", form="round", lo=2, hi=-1)
    a.px([(7, 6), (8, 5), (6, 7)], "gras", -1)
    cx, cy = 12.5, 12.5
    for y in range(32):
        for x in range(32):
            r = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r <= 4.6:
                a.px([(x, y)], "gold", 7 if r > 2.4 else 8)
            elif r <= 5.7:
                a.px([(x, y)], "gras", 2)
    a.px([(10, 10), (11, 10), (10, 11)], "papier", -1)
    a.px([(14, 15), (15, 14), (15, 15)], "gold", 6)
    return a


def i_spielzeit():
    a = Icon()
    a.rect(5, 1, 26, 5, "holz", lo=1, hi=-2)
    a.rect(5, 26, 26, 30, "holz", lo=1, hi=-2)
    a.rect(6, 6, 8, 25, "holz", form="cyl", lo=0, hi=-3)
    a.rect(23, 6, 25, 25, "holz", form="cyl", lo=0, hi=-3)
    a.poly([(10, 6), (21, 6), (17, 15), (17, 16), (21, 25), (10, 25), (14, 16), (14, 15)], "glas", lo=2, hi=-1, sep=False)
    a.poly([(11, 8), (20, 8), (16, 14), (15, 14)], "gold", lo=4, hi=-2, sep=False)
    a.poly([(15, 19), (16, 19), (20, 24), (11, 24)], "gold", lo=3, hi=-2, sep=False)
    a.px([(15, 15), (15, 16), (15, 17), (15, 18)], "gold", 5)
    a.px([(11, 7), (12, 7), (11, 22)], "glas", -1)
    return a


# ---------------------------------------------------------------------------------------------------------- Handel
def i_shop():
    a = Icon()
    a.poly([(4, 10), (27, 10), (29, 30), (2, 30)], "feuer", lo=3, hi=-3)
    a.ellipse(9, 2, 22, 15, "holz", form="round", lo=1, hi=-2)
    a.cut_ellipse(12, 5, 19, 13)
    a.poly([(4, 10), (27, 10), (27, 12), (4, 12)], "feuer", lo=6, hi=-1, sep=False)
    a.poly([(16, 15), (21, 20), (16, 26), (11, 20)], "smaragd", lo=2, hi=-1, gloss=[(14, 19), (15, 18)])
    return a


def i_trade():
    a = Icon()
    a.rect(1, 1, 30, 30, "eisen", lo=0, hi=3)
    a.rect(3, 3, 28, 28, "eisen", lo=0, hi=1, sep=False, rim=False)
    for x, y0, y1, up in ((6, 16, 23, False), (11, 12, 20, True), (16, 15, 22, False), (21, 8, 16, True), (25, 6, 13, True)):
        a.rect(x, y0, x + 2, y1, "smaragd" if up else "rot", lo=3, hi=-2, sep=False)
    a.line([(5, 22), (11, 16), (16, 19), (21, 11), (27, 6)], 1, "gold", lo=5, hi=-1, sep=False)
    a.px([(24, 4), (25, 4), (26, 4), (26, 5), (26, 6)], "gold", -1)
    return a


def i_duo_rtp():
    """Duo-RTP: symmetrischer Iso-Würfel (gleich hohe Seiten, klare Kanten, weiche Ecken), Augen 1/2/3, Blitz."""
    face = iso_map((14, 6), (2, 12), (26, 12), (14, 18), 12)
    pix = {}
    base = {"top": 6, "left": 4, "right": 2}
    for p, (f, u, v) in face.items():
        k = base[f]
        if f == "top" and (u < 0.09 or v < 0.09 or u > 0.91 or v > 0.91):
            k = 7
        if f == "left" and (v < 0.09 or u > 0.92):
            k = 5
        if f == "right" and v < 0.09:
            k = 3
        if f == "right" and u < 0.09:
            k = 1
        if f != "top" and v > 0.92:
            k -= 1
        pix[p] = ("rot", k)

    def eye(cx, cy, rows, k_hi, k_lo):
        for (x, y), _ in grid(rows, cx - len(rows[0]) // 2, cy - len(rows) // 2).items():
            pix[(x, y)] = ("silber", k_hi if y < cy + len(rows) // 2 else k_lo)
    TOP = [".###.", "#####", ".###."]
    LEFT = ["##.", "###", ".##"]
    RIGHT = [".##", "###", "##."]
    eye(14, 12, TOP, 7, 5)
    eye(5, 17, LEFT, 6, 4)
    eye(10, 24, LEFT, 6, 4)
    eye(17, 19, RIGHT, 4, 2)
    eye(20, 21, RIGHT, 4, 2)
    eye(23, 22, RIGHT, 4, 2)
    for q in ((13, 6), (14, 6), (2, 12), (25, 12), (2, 23), (25, 23), (13, 29), (14, 29)):
        pix.pop(q, None)
    return Img(stack(layer(pix), bolt(22, 0)))


# ---------------------------------------------------------------------------------------------------------- Natur
def i_angeln():
    a = Icon()
    a.line([(3, 29), (24, 3)], 3, "holz", lo=1, hi=-2)
    a.rect(3, 23, 7, 27, "eisen", lo=2, hi=-2)
    a.line([(24, 3), (27, 13)], 1, "silber", lo=5, hi=-1, sep=False)
    a.ellipse(18, 14, 30, 22, "blau", form="round", lo=2, hi=-1, gloss="auto")
    a.poly([(18, 18), (13, 14), (13, 22)], "blau", lo=2, hi=-2)
    a.px([(26, 17)], "eisen", 0)
    return a


def is_wachstum():
    a = Icon()
    a.rect(2, 21, 21, 30, "erde", lo=1, hi=-1)
    a.rect(2, 19, 21, 22, "gras", lo=1, hi=-1)
    a.line([(11, 19), (11, 9)], 2, "gras", lo=0, hi=4)
    a.poly([(11, 13), (3, 10), (1, 5), (7, 5), (11, 10)], "gras", lo=2, hi=-1)
    a.poly([(12, 10), (16, 3), (22, 2), (21, 8), (13, 13)], "gras", lo=2, hi=-1)
    a.poly([(26, 1), (31, 8), (28, 8), (28, 20), (24, 20), (24, 8), (21, 8)], "gold", lo=2, hi=-1)
    return a


def c_essen():
    a = Icon()
    a.ellipse(1, 11, 23, 28, "papier", form="round", lo=4, hi=-1)          # Fettrand
    a.ellipse(3, 13, 21, 26, "rot", form="round", lo=0, hi=4, sep=False)   # Fleisch
    a.px([(7, 17), (8, 17), (9, 18), (13, 21), (14, 21), (15, 22), (10, 23)], "rot", 5)   # Marmorierung
    a.rect(19, 18, 26, 21, "papier", form="cyl", lo=6, hi=-1)               # Knochen
    a.ellipse(24, 16, 30, 21, "papier", form="round", lo=6, hi=-1)
    a.ellipse(13, 1, 27, 15, "rot", form="round", lo=1, hi=-1, gloss="auto")
    a.rect(20, 0, 21, 3, "holz", lo=1, hi=-2)
    a.ellipse(22, 0, 26, 3, "gras", form="round", lo=2, hi=-1)
    return a


def c_redstone():
    a = Icon()
    a.poly([(1, 25), (4, 20), (9, 17), (15, 18), (19, 21), (22, 26), (18, 29), (6, 29)], "rot", lo=0, hi=-2)
    a.poly([(6, 22), (10, 19), (14, 20), (16, 23), (11, 25)], "rot", lo=3, hi=-1, sep=False)
    for x, y in ((5, 21), (11, 19), (15, 22), (8, 26), (17, 26)):
        a.px([(x, y)], "rot", -1)
    a.rect(21, 9, 24, 29, "holz", form="cyl", lo=1, hi=-2)
    a.ellipse(19, 2, 26, 10, "rot", form="round", lo=2, hi=-1, gloss="auto")
    a.px([(20, 0), (25, 1), (18, 5)], "rot", -2)
    return a


def c_bloecke():
    a = Icon()

    # drei Würfel: Ziegel hinten, Stein links, Gras vorn rechts
    def cube(x, y, s, top, left, right):
        a.poly([(x + s, y), (x + 2 * s, y + s // 2), (x + s, y + s), (x, y + s // 2)], top[0], lo=top[1], hi=top[2])
        a.poly([(x, y + s // 2), (x + s, y + s), (x + s, y + 2 * s), (x, y + s + s // 2)], left[0], lo=left[1], hi=left[2])
        a.poly([(x + s, y + s), (x + 2 * s, y + s // 2), (x + 2 * s, y + s + s // 2), (x + s, y + 2 * s)], right[0],
               lo=right[1], hi=right[2])
    cube(9, 1, 7, ("rot", 4, -1), ("rot", 2, 5), ("rot", 0, 3))
    cube(1, 12, 7, ("eisen", 5, -1), ("eisen", 3, 6), ("eisen", 1, 4))
    cube(16, 12, 7, ("gras", 4, -1), ("erde", 3, -1), ("erde", 1, 4))
    a.px([(11, 9), (12, 9), (13, 9), (18, 13), (19, 13)], "rot", 1)
    return a


def c_suche():
    a = Icon()
    a.line([(19, 19), (29, 29)], 5, "holz", lo=1, hi=-2)
    a.ellipse(1, 1, 23, 23, "silber", form="round", lo=0, hi=-2)
    a.ellipse(5, 5, 19, 19, "glas", form="round", lo=2, hi=-1, sep=True)
    a.px([(8, 8), (9, 7), (10, 7), (8, 9)], "glas", -1)
    return a


# ---------------------------------------------------------------------------------------------------------- Skyblock
def i_generator(cage="gold", core="tuerkis", glow="feuer"):
    """Generator: Spawner-Käfig als Iso-Würfel wie die Generator-Grafiken im Spiel: 2-px-Rahmen, dünne Gitterstäbe
    in Dritteln (Muster), dunkles Inneres, nur die Felder an der Mitte glühen, schwebender Edelstein vorn."""
    face = iso_map((16, 2), (2, 9), (30, 9), (16, 16), 14)
    S = set(face)
    N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
    ring1 = {p for p in S if any((p[0] + dx, p[1] + dy) not in S for dx, dy in N4)}
    ring2 = {p for p in S - ring1 if any((p[0] + dx, p[1] + dy) in ring1 for dx, dy in N4)}
    seam = {p for p in S if any((p[0] + dx, p[1] + dy) in S and face[(p[0] + dx, p[1] + dy)][0] != face[p][0]
                                for dx, dy in N4)}
    cn, gn = len(RAMPS[cage]), len(RAMPS[glow])

    def third(t):
        return int(t * 3 - 1e-9)
    pix = {}
    for p, (f, u, v) in face.items():
        x, y = p
        bar = False
        for dx, dy in ((1, 0), (0, 1)):
            q = (x + dx, y + dy)
            if q in face and face[q][0] == f and (third(face[q][1]) != third(u) or third(face[q][2]) != third(v)):
                bar = True
        side = {"top": 0, "left": 1, "right": 2}[f]
        if p in ring1 or p in ring2 or p in seam:
            k = (cn - 2, cn // 2, 2)[side]
            if p in ring1 and f == "top":
                k = cn - 1
            if p in seam and f == "left":
                k = cn // 2 + 1
            if p in seam and f == "right":
                k = 1
            pix[p] = (cage, k)
        elif bar:
            pix[p] = (cage, (cn // 2, 3, 2)[side])
        else:
            near = {"top": (2, 2), "left": (2, 0), "right": (0, 0)}[f]
            dist = abs(third(u) - near[0]) + abs(third(v) - near[1])
            if dist == 0:
                pix[p] = (glow, gn - 2 - side)
            elif dist == 1:
                pix[p] = (glow, gn - 5 - side)
            else:
                pix[p] = ("kessel", 1 if f == "top" else 0)
    rows = ["..9988..",
            ".998876.",
            "88776655",
            ".766544.",
            "..5543..",
            "...43..."]
    gem = {p: (core, int(c)) for p, c in grid(rows, 12, 13).items()}
    return Img(stack(layer(pix), layer(gem)))


def i_groesse():
    a = Icon()
    a.rect(9, 9, 22, 22, "gras", lo=1, hi=-1)
    for poly in ([(16, 0), (21, 6), (11, 6)], [(16, 31), (21, 25), (11, 25)], [(0, 16), (6, 11), (6, 21)],
                 [(31, 16), (25, 11), (25, 21)]):
        a.poly(poly, "gold", lo=2, hi=-1)
    return a


def i_oneblock():
    """One-Block: symmetrischer Grasblock aus 7 x 7-Texturen in Rampen-Farben, dunkle Kontur."""
    pal = {"g": RAMPS["gras"][4], "G": RAMPS["gras"][6], "d": RAMPS["gras"][3], "f": RAMPS["gras"][3],
           "F": RAMPS["gras"][5], "D": RAMPS["holz"][3], "e": RAMPS["holz"][4], "k": RAMPS["holz"][2]}
    top = ["gGgggdg", "gggGggg", "dgggggG", "gggdggg", "gGgggGg", "ggggdgg", "Gggggg" + "g"]
    side = ["FFFFFFF", "fffffff", "fDffDff", "DDfDDkf", "DkDDeDD", "eDDkDDD", "DDeDDkD"]
    return Img(iso_block(top, side, side, pal, shade=(1.0, 0.84, 0.62)))


def is_spawn():
    a = Icon()
    a.ellipse(17, 2, 29, 18, "feuer", form="round", lo=2, hi=-3)
    a.px([(21, 6), (25, 10), (22, 13), (26, 6)], "rot", 1)
    a.ellipse(3, 8, 20, 30, "tuerkis", form="round", lo=1, hi=-1, gloss="auto")
    for x, y, w in ((7, 13, 3), (13, 19, 3), (7, 23, 2)):
        a.ellipse(x, y, x + w, y + w, "tuerkis", form="round", lo=0, hi=2, sep=False, rim=False)
    return a


def is_limits():
    a = Icon()
    a.rect(2, 3, 27, 9, "eisen", lo=1, hi=-2)
    a.rect(5, 5, 24, 7, "eisen", lo=0, hi=1, sep=False, rim=False)
    a.poly([(4, 10), (25, 10), (20, 19), (9, 19)], "eisen", lo=2, hi=-2)
    a.rect(11, 19, 18, 23, "eisen", lo=1, hi=-2)
    a.rect(13, 23, 16, 27, "eisen", lo=1, hi=4)
    a.ellipse(18, 18, 31, 31, "rot", form="round", lo=1, hi=-1)
    a.rect(21, 23, 28, 26, "silber", lo=5, hi=-1, sep=False)
    return a


def is_besuch():
    a = Icon()
    a.rect(14, 2, 17, 30, "holz", form="cyl", lo=0, hi=-3)
    a.poly([(4, 5), (24, 5), (29, 10), (24, 15), (4, 15)], "papier", lo=3, hi=-1)
    a.poly([(27, 17), (8, 17), (3, 22), (8, 27), (27, 27)], "papier", lo=2, hi=-2)
    for y, x0, x1 in ((9, 7, 20), (11, 7, 17), (21, 11, 24), (23, 14, 24)):
        a.px([(x, y) for x in range(x0, x1 + 1)], "papier", 2)
    a.rect(11, 29, 20, 31, "gras", lo=2, hi=-1)
    return a


# ---------------------------------------------------------------------------------------------------------- Knöpfe (16 px)
def s_wildnis():
    a = Icon(16)
    a.rect(7, 12, 8, 15, "holz", form="cyl", lo=1, hi=-2)
    a.poly([(8, 0), (14, 8), (2, 8)], "gras", lo=1, hi=-1)
    a.poly([(8, 4), (15, 13), (1, 13)], "gras", lo=0, hi=-2)
    return a


def s_homes():
    a = Icon(16)
    a.rect(3, 7, 12, 14, "papier", lo=4, hi=-1)
    a.poly([(8, 1), (15, 8), (1, 8)], "rot", lo=1, hi=-1)
    a.rect(7, 10, 9, 14, "holz", lo=1, hi=-2)
    return a


def s_enderchest():
    a = Icon(16)
    a.rect(1, 3, 14, 14, "tuerkis", lo=0, hi=3)
    a.rect(1, 3, 14, 7, "tuerkis", lo=1, hi=4)
    a.rect(6, 6, 9, 9, "smaragd", lo=3, hi=-1, gloss=[(7, 7)])
    return a


def s_alert():
    """Hinweis „Belohnung abholbar“ an Kacheln: rundes rotes Abzeichen mit weißem Ausrufezeichen (18 px)."""
    a = Icon(18)
    a.add(disc([6, 10, 12, 14, 14, 16, 16, 16, 16, 16, 16, 14, 14, 12, 10, 6], 1, 1), "rot", form="round", lo=0, hi=-1)
    a.rect(8, 4, 9, 10, "silber", lo=5, hi=-1, sep=False)
    a.rect(8, 12, 9, 13, "silber", lo=5, hi=-1, sep=False)
    a.px([(5, 4), (4, 5)], "rot", -1)
    return a


def s_alert_small():
    """Dasselbe Abzeichen klein (12 px) für Reiter und Karten."""
    a = Icon(12)
    a.add(disc([4, 8, 8, 10, 10, 10, 10, 8, 8, 4], 1, 1), "rot", form="round", lo=1, hi=-1)
    a.rect(5, 3, 6, 6, "silber", lo=5, hi=-1, sep=False)
    a.rect(5, 8, 6, 8, "silber", lo=5, hi=-1, sep=False)
    return a


# Name -> Zeichnung (Börse, Kosmetik, Haustiere und Markt bleiben die alten aus build_hub_icons.py)
TILES = {"menu": i_menu, "angeln": i_angeln, "jobs": i_jobs, "grundstuecke": i_grundstuecke, "heiraten": i_heiraten,
         "bank": i_bank, "duelle": i_duelle, "turnier": i_turnier, "versicherung": i_versicherung, "shop": i_shop,
         "wirtschaft": i_wirtschaft, "tagespot": i_tagespot, "duo_rtp": i_duo_rtp, "spielzeit": i_spielzeit,
         "titel": i_titel, "insel": i_insel, "generator": i_generator, "groesse": i_groesse, "mitglieder": i_mitglieder,
         "oneblock": i_oneblock, "ob_glueck": ob_glueck, "ob_cooldown": i_ob_cooldown, "ob_tempo": i_ob_tempo,
         "ob_xp": ob_xp, "ob_geld": i_ob_geld, "ob_beute": ob_beute, "s_wildnis": s_wildnis, "s_homes": s_homes,
         "s_enderchest": s_enderchest, "alert": s_alert, "alert_s": s_alert_small, "c_bloecke": c_bloecke,
         "c_essen": c_essen, "c_redstone": c_redstone, "c_deko": c_deko, "c_suche": c_suche, "c_daily": c_daily,
         "trade": i_trade, "is_wachstum": is_wachstum, "is_spawn": is_spawn, "is_limits": is_limits,
         "is_pokal": is_pokal, "is_besuch": is_besuch, "is_biom": is_biom}
