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


SWORD = ["................",
         ".............222",
         "............2771",
         "...........27571",
         "..........27571.",
         ".........27561..",
         "........27561...",
         "..22...26561....",
         "..232.26561.....",
         "...2416461......",
         "...244361.......",
         "....2321........",
         "...BC1221.......",
         "..BDA.1121......",
         "22CA....11......",
         "231.............",
         "111............."]


def sword(mat, dx=0, dy=0, flip=False):
    n = len(RAMPS[mat])
    hp, wp = {}, {}
    for (x, y), c in grid(SWORD[1:], 0, 0).items():
        for ex in (0, 1):
            for ey in (0, 1):
                X, Y = 2 * x + ex + dx, 2 * y + ey + dy
                if flip:
                    X = 31 - X
                if c in STICK:
                    wp[(X, Y)] = ("stiel", STICK[c])
                else:
                    hp[(X, Y)] = (mat, round(1 + (int(c) - 1) * (n - 2) / 6))
    for d in (hp, wp):
        keys = set(d)
        for (x, y), (m, k) in list(d.items()):
            if (x, y - 1) not in keys:
                d[(x, y)] = (m, min(len(RAMPS[m]) - 1, k + 1))
            elif (x, y + 1) not in keys:
                d[(x, y)] = (m, max(0, k - 1))
    return stack(layer(wp), layer(hp))


def i_duelle():
    return Img(stack(sword("eisen", flip=True), sword("tuerkis")))


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
    for x in (5, 11, 17, 23):
        a.rect(x, 12, x + 3, 24, "silber", form="cyl", lo=1, hi=-2)
    a.rect(1, 25, 30, 27, "silber", lo=2, hi=-2)
    a.rect(0, 28, 31, 30, "silber", lo=1, hi=-3)
    a.ellipse(12, 2, 19, 8, "gold", form="round", lo=0, hi=-2, gloss="auto")
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
    big = Icon()
    # große Insel vorn links: Schnee mit Fichte
    big.poly([(1, 19), (21, 19), (17, 24), (13, 30), (9, 30), (5, 24)], "erde", lo=1, hi=-1)
    big.poly([(6, 24), (10, 24), (9, 28)], "eisen", lo=2, hi=4, sep=False)
    big.ellipse(0, 15, 22, 22, "glas", form="round", lo=3, hi=-1)
    big.poly([(11, 3), (16, 10), (14, 10), (18, 15), (4, 15), (8, 10), (6, 10)], "smaragd", lo=0, hi=4)
    big.rect(10, 15, 12, 18, "holz", lo=1, hi=-3)
    big.px([(11, 3), (10, 4), (11, 4), (12, 4), (8, 10), (9, 10), (6, 10), (4, 15), (5, 15), (14, 10)], "glas", -1)
    b = big.render()
    small = Icon()
    # kleine Insel hinten rechts: Sand mit Kaktus
    small.poly([(19, 10), (31, 10), (28, 14), (25, 18), (23, 14)], "erde", lo=2, hi=-1)
    small.ellipse(18, 7, 31, 12, "papier", form="round", lo=5, hi=-1)
    small.rect(24, 1, 26, 9, "gras", form="cyl", lo=1, hi=-3)
    small.rect(21, 3, 22, 6, "gras", form="cyl", lo=1, hi=-3)
    small.rect(28, 2, 29, 5, "gras", form="cyl", lo=1, hi=-3)
    small.px([(23, 6), (27, 5)], "gras", 3)
    return Img(stack(small.render(), b))


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
    g = Icon()
    g.ellipse(1, 9, 20, 28, "gold", form="round", lo=0, hi=-2)
    g.cut_ellipse(5, 13, 16, 24)
    g.px([(3, 13), (4, 12)], "gold", -1)
    s = Icon()
    s.ellipse(12, 11, 31, 30, "silber", form="round", lo=0, hi=-2)
    s.cut_ellipse(16, 15, 27, 26)
    gem = Icon()
    gem.poly([(7, 7), (10, 3), (14, 7), (10, 11)], "rosa", lo=2, hi=-1, gloss=[(9, 5)])
    gi, si = g.render(), s.render()
    out = stack(si, gi)
    # unten liegt das Silber oben: verschlungen, beide Ringe geschlossen
    sp = si.load()
    op = out.load()
    for y in range(20, 32):
        for x in range(10, 22):
            if sp[x, y][3]:
                op[x, y] = sp[x, y]
    return Img(stack(out, gem.render()))


def i_turnier():
    a = Icon()
    # Griff: nach unten schmaler, zwei Ringe (Muster), unten ein Knauf
    a.poly([(12, 17), (19, 17), (17, 28), (14, 28)], "gold", form="cyl", lo=1, hi=-2)
    a.rect(13, 28, 18, 30, "gold", lo=1, hi=-3)
    a.rect(13, 21, 18, 22, "gold", lo=3, hi=-1, sep=True)
    # Schale mit Rand und Zacken-Band
    a.poly([(7, 11), (24, 11), (21, 17), (10, 17)], "gold", form="cyl", lo=1, hi=-2)
    a.rect(6, 10, 25, 12, "gold", lo=3, hi=-1)
    a.px([(10, 14), (13, 14), (16, 14), (19, 14)], "gold", 7)
    a.px([(11, 15), (14, 15), (17, 15), (20, 15)], "gold", 2)
    bowl = a.render()
    f = Icon()
    # Flamme: drei Zungen, die mittlere am höchsten, nach rechts geweht (Bruch der Symmetrie)
    f.poly([(8, 10), (9, 5), (11, 7), (13, 1), (16, 5), (18, 0), (20, 4), (22, 2), (23, 6), (23, 10)], "feuer", form="round", lo=2, hi=-2)
    f.poly([(11, 10), (13, 5), (15, 7), (18, 3), (20, 7), (20, 10)], "feuer", form="round", lo=6, hi=-1, sep=False)
    f.px([(16, 8), (17, 7), (15, 9), (16, 9), (17, 9)], "feuer", -1)
    return Img(stack(f.render(), bowl))


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


def i_tagespot():
    """Tagespot (erster Entwurf): runder Eisentopf voller Goldmünzen, Kleeblatt oben rechts."""
    a = Icon()
    a.ellipse(3, 12, 28, 30, "eisen", form="round", lo=0, hi=-3)
    a.ellipse(4, 9, 27, 15, "eisen", form="round", lo=1, hi=-2)
    a.ellipse(6, 8, 25, 14, "gold", form="round", lo=3, hi=-1, sep=False)
    for x, y in ((8, 5), (14, 3), (20, 5)):
        coin(a, x, y, x + 6, y + 6, motif=False)
    a.px([(10, 6), (16, 4)], "gold", -1)
    a.rect(2, 13, 4, 15, "eisen", lo=1, hi=-2)
    a.rect(27, 13, 29, 15, "eisen", lo=1, hi=-2)
    for cx, cy in ((24, 2), (27, 2), (24, 5), (27, 5)):
        a.ellipse(cx - 1, cy - 1, cx + 1, cy + 1, "gras", form="round", lo=2, hi=-1)
    return a


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


def xp_core(a, cx, cy, r_in, r_ring):
    """Kern einer Erfahrungskugel: helles Gelb, außen ein dunkelgrüner Ring."""
    for y in range(32):
        for x in range(32):
            r = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r <= r_in:
                a.px([(x, y)], "gold", 8 if r <= r_in * 0.5 else 7)
            elif r <= r_ring:
                a.px([(x, y)], "gras", 2)


def ob_xp():
    a = Icon()
    a.ellipse(2, 2, 25, 25, "gras", form="round", lo=0, hi=-1)
    a.ellipse(21, 19, 29, 27, "gras", form="round", lo=1, hi=-1)
    a.ellipse(23, 1, 29, 7, "gras", form="round", lo=2, hi=-1)
    a.px([(7, 6), (8, 5), (6, 7)], "gras", -1)
    xp_core(a, 12.5, 12.5, 4.6, 5.7)
    a.px([(10, 10), (11, 10), (10, 11)], "papier", -1)
    a.px([(14, 15), (15, 14), (15, 15)], "gold", 6)
    for (x, y), c in grid([".rr.", "rYyr", "ryyr", ".rr."], 24, 22).items():
        a.px([(x, y)], *{"r": ("gras", 2), "Y": ("papier", -1), "y": ("gold", 7)}[c])
    for (x, y), c in grid([".r.", "rYr", ".r."], 25, 3).items():
        a.px([(x, y)], *{"r": ("gras", 2), "Y": ("gold", 8)}[c])
    return a


def i_spielzeit():
    top_w = [16, 18, 18, 18, 16, 14, 12, 10, 8, 6, 4]
    glass_w = top_w + [4, 4] + top_w[::-1]
    glass = disc(glass_w, 7, 4)
    a = Icon()
    a.rect(5, 1, 26, 3, "holz", lo=1, hi=-2)
    a.rect(5, 28, 26, 30, "holz", lo=1, hi=-2)
    a.add(glass, "glas", form="cyl", lo=2, hi=-1)
    # Sand: oben ein Rest über dem Hals, unten ein Hügel, dazwischen ein Faden
    sand_top = {(x, y) for (x, y) in glass if 11 <= y <= 14}
    sand_bot = {(x, y) for (x, y) in glass if y >= 22 and abs(x + 0.5 - 16) <= (y - 19) * 1.6}
    a.add(sand_top, "gold", form="cyl", lo=4, hi=-2, sep=False)
    a.add(sand_bot, "gold", form="cyl", lo=3, hi=-2, sep=False)
    a.px([(15, y) for y in range(15, 22)], "gold", 6)
    a.px([(9, 5), (9, 6), (10, 7), (9, 24), (9, 25)], "glas", -1)
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


ARROW = ["....A....",
         "...AAA...",
         "..AAAAA..",
         ".AAAAAAA.",
         "AAAAAAAAA",
         "..AAAAA..",
         "..AAAAA..",
         "..AAAAA..",
         "..AAAAA..",
         "..AAAAA..",
         "..AAAAA..",
         "..AAAAA.."]


def arrow(ox, oy):
    pts = set(grid(ARROW, ox, oy))
    pix = {p: ("gold", k) for p, k in edge_tone(pts, 5, 2, 8, up=2, left=2, down=-2, right=-2).items()}
    for y in range(oy + 6, oy + 11):
        pix[(ox + 3, y)] = ("gold", 7)
    return layer(pix)


def is_wachstum():
    a = Icon()
    a.line([(10, 30), (10, 22), (11, 16), (11, 12)], 2, "gras", lo=0, hi=3)
    a.poly([(10, 19), (3, 17), (0, 12), (2, 10), (7, 11), (10, 15)], "gras", lo=2, hi=-1)
    a.poly([(12, 14), (14, 8), (19, 5), (22, 6), (20, 11), (13, 16)], "gras", lo=2, hi=-1)
    a.px([(4, 13), (5, 13), (6, 14)], "gras", -1)
    a.px([(15, 9), (16, 8), (17, 8)], "gras", -1)
    a.px([(10, 29), (11, 29), (10, 30), (11, 30)], "gras", 0)
    return Img(stack(a.render(), arrow(22, 9)))


def c_essen():
    """Essen: ein saftiger roter Apfel (zwei Rundungen oben, Stiel, Blatt), großer Glanz oben links, Gegenlicht unten
    rechts."""
    ap = Icon()
    body = disc([10, 16, 20, 22, 24, 24, 26, 26, 26, 26, 26, 26, 26, 24, 24, 22, 20, 18, 14, 10], 3, 8)
    lobes = disc([6, 10, 12], 5, 5) | disc([6, 10, 12], 17, 5)
    ap.add(body | lobes, "rot", form="round", lo=1, hi=-1)
    ap.poly([(15, 9), (17, 9), (16, 11)], "rot", lo=0, hi=2, sep=False)
    ap.rect(15, 1, 16, 8, "stiel", lo=1, hi=3)
    ap.poly([(17, 4), (21, 1), (26, 1), (23, 5), (18, 6)], "gras", lo=2, hi=-2)
    ap.px([(19, 4), (20, 3), (21, 3)], "gras", -1)
    # saftiger Glanz: Fleck oben links (hellste Stufe und Creme), dazu ein Lichtpunkt; Gegenlicht am Rand unten rechts
    ap.px([(8, 12), (9, 12), (8, 13), (8, 14), (9, 11)], "papier", -1)
    ap.px([(10, 11), (9, 13), (7, 15)], "rot", -1)
    ap.px([(12, 10)], "papier", -2)
    ap.px([(25, 22), (26, 21), (24, 24)], "rot", 7)
    return ap


def c_redstone():
    pal = {"i": "#CFCACA", "w": "#B9965C", "W": "#8B6E40", "g": "#85C675", "G": "#6EAC5F", "s": "#5E9A4F", "S": "#4F8B41",
           "L": "#878787", "m": "#737373", "d": "#5A5A5A", "k": "#484848"}
    top = ["iwwiwwi",
           "wgggGGw",
           "wgGGGsw",
           "igGGGsi",
           "wGGGssw",
           "wGssSSw",
           "iwwiwwi"]
    side = ["iwwiwwi",
            "WWWWWWW",
            "LLmLLLm",
            "LmLLmLL",
            "mLLdLLm",
            "LLmLLdL",
            "dkddkdd"]
    return Img(iso_block(top, side, side, pal, shade=(1.0, 0.84, 0.62)))


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
def i_generator():
    """Generator: genau die Grafik der Skyblock-Generatoren im Spiel (Edelstein-Generator im goldenen Käfig aus
    tools/build_generator_art.py), damit Menü und Generatoren gleich aussehen."""
    import build_generator_art as gen
    return Img(gen.art("edelstein", "gold"))


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


# Zombie wie das Vanilla-3D-Modell (Kopf, Körper, Arme nach vorn, Beine), um die Y-Achse 45 Grad nach links gedreht und
# 10 Grad von oben, gerendert mit der Vanilla-Textur und auf Rampen-Stufen gerastert (Vorderseiten hell, Seiten dunkel,
# Augen schwarz). Buchstaben: a-f Haut (zombie 0-5), g-j Hemd (hemd 0-3), k-n Hose (hose 0-3), o-w Schuhe (eisen 0-8).
ZOMBIE = [".............ffeefffcccc........",
          ".............ffffffccccc........",
          ".............ffffffccccc........",
          ".............ffffffccccc........",
          ".............faafaaccccc........",
          ".............ffffffccccc........",
          "...........fbeeefffccbbb........",
          ".......ffffcccbgbbbbbgjjj.......",
          ".......fffccccbfffffccchh.......",
          ".......fffccccbffcccccchh.......",
          ".......fffccccbffcccccchh.......",
          "...............ffcccccch........",
          "...............ggggggg..........",
          "...............jjjjjcc..........",
          "...............jjjjjhc..........",
          "...............njjjjhc..........",
          "...............nnnnjll..........",
          "...............nnnnnll..........",
          "...............nnnnnll..........",
          "...............nnnnnll..........",
          "...............nnnnnll..........",
          "...............nnnnnll..........",
          "...............nnnnnll..........",
          "...............nnnnnll..........",
          "...............nnnnnll..........",
          "...............nnnnnll..........",
          "...............nnnnnlr..........",
          "...............tttttrr..........",
          ".................tttrr.........."]


def is_spawn():
    """Spawnrate: Minecraft-Zombie wie das 3D-Modell, 45 Grad nach links gedreht (Vorderseite links, Seite rechts),
    Arme nach vorn gestreckt, Vanilla-Farben auf Rampen-Stufen."""
    mats = {}
    for mat, letters in (("zombie", "abcdef"), ("hemd", "ghij"), ("hose", "klmn"), ("eisen", "opqrstuvw")):
        for k, ch in enumerate(letters):
            mats[ch] = (mat, k)
    return Img(layer({p: mats[c] for p, c in grid(ZOMBIE, 0, 1).items()}))


def ring_rows(d):
    """Zeilenbreiten einer runden Scheibe mit Durchmesser d (pixelgenau, achsensymmetrisch)."""
    r = d / 2
    return [2 * round(math.sqrt(max(0.0, r * r - (j + 0.5 - r) ** 2))) for j in range(d)]


def is_limits():
    """Limits: deutsches Verkehrszeichen „Durchfahrt verboten“ (Zeichen 250): breiter roter Ring um eine weiße Fläche.
    Flach und streng symmetrisch wie das echte Schild; nur ein kleiner Glanz oben links bricht die Symmetrie."""
    outer = disc(ring_rows(28), 2, 2)
    inner = disc(ring_rows(18), 7, 7)
    ring = outer - inner
    pix = {p: ("rot", 5) for p in ring}
    for p in inner:
        pix[p] = ("silber", 7)
    N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
    # Ring: 1 px heller Außenrand, 1 px dunkler Innenrand (ringsum gleich, damit der Ring überall gleich dick wirkt)
    for (x, y) in ring:
        if any((x + dx, y + dy) not in outer for dx, dy in N4):
            pix[(x, y)] = ("rot", 6)
        elif any((x + dx, y + dy) in inner for dx, dy in N4):
            pix[(x, y)] = ("rot", 3)
    for (x, y) in inner:
        if any((x + dx, y + dy) not in inner for dx, dy in N4):
            pix[(x, y)] = ("silber", 5)
    for q in ((8, 6), (9, 5), (10, 5), (7, 7)):
        pix[q] = ("rot", 8)
    return Img(layer(pix))


def is_besuch():
    a = Icon()
    a.poly([(14, 2), (17, 2), (17, 27), (15.5, 31), (14, 27)], "holz", form="cyl", lo=0, hi=-3)
    a.poly([(4, 5), (24, 5), (29, 10), (24, 15), (4, 15)], "papier", lo=3, hi=-1)
    a.poly([(27, 17), (8, 17), (3, 22), (8, 27), (27, 27)], "papier", lo=2, hi=-2)
    for y, x0, x1 in ((9, 7, 20), (11, 7, 17), (21, 11, 24), (23, 14, 24)):
        a.px([(x, y) for x in range(x0, x1 + 1)], "papier", 2)
    return a


# ---------------------------------------------------------------------------------------------------------- Knöpfe (16 px)
def s_wildnis():
    a = Icon(16)
    a.rect(7, 12, 8, 15, "holz", form="cyl", lo=1, hi=-2)
    a.poly([(8, 0), (14, 8), (2, 8)], "gras", lo=1, hi=-1)
    a.poly([(8, 4), (15, 13), (1, 13)], "gras", lo=0, hi=-2)
    return a


def s_homes():
    """Bett (16 px) wie in Minecraft: weißes Kissen, rote Decke mit Falte, Holzrahmen mit Füßen."""
    rows = ["..PPPP.........",
            ".PPPPPPBBBBBBBB",
            ".PPPPPPBBBBBBBB",
            ".ppppppBBBBBBBB",
            "WWWWWWWbbbbbbbb",
            "WWWWWWWWWWWWWWW",
            "wwwwwwwwwwwwwww",
            "ww...........ww"]
    col = {"P": ("papier", 9), "p": ("papier", 6), "B": ("bett", 4), "b": ("bett", 1), "W": ("planke", 4),
           "w": ("planke", 1)}
    pix = {p: col[c] for p, c in grid(rows, 0, 4).items()}
    for x in range(7, 15):
        pix[(x, 5)] = ("bett", 5)
    pix[(2, 4)] = ("papier", 9)
    pix[(2, 5)] = ("papier", 9)
    for y in (6, 7):
        pix[(10, y)] = ("bett", 2)
    pix[(0, 8)] = ("planke", 5)
    return Img(layer(pix, 16))


def s_enderchest():
    """Enderkiste im Vanilla-Look (16 px): dunkles Obsidian-Grün, heller Deckel-Rand, Fuge, goldenes Schloss."""
    face = iso_map((8, 1), (1, 4.5), (15, 4.5), (8, 8), 7, 16)
    pix = {}
    for p, (f, u, v) in face.items():
        if f == "top":
            k = 6 if (u < 0.12 or v < 0.12) else 4 if (u > 0.88 or v > 0.88) else 3
        else:
            lid = v < 0.36
            k = (5 if lid else 2) if f == "left" else (3 if lid else 1)
            if 0.36 <= v < 0.5:
                k = 0
            if v < 0.14:
                k += 2
        pix[p] = ("ender", min(7, k))
    for q, k in (((4, 8), 7), ((5, 8), 6), ((4, 9), 5), ((5, 9), 4), ((4, 10), 4), ((5, 10), 3)):
        pix[q] = ("gold", k)
    return Img(layer(pix, 16))


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


# ---------------------------------------------------------------------------------------------------------- Upgrade-Stufen
# Upgrades haben 3 Stufen (Level 1-10, 11-15, 16-20). Stufe 1 ist das normale Icon, Stufe 2 bekommt kleine goldene
# Funkel-Sterne um das Motiv, Stufe 3 dieselben Sterne in Lila. Sterne ohne Kontur (Effekte), abwechselnd 5 und 3 px.
UPGRADE_ICONS = ["groesse", "mitglieder", "generator", "is_wachstum", "is_spawn", "is_limits", "ob_glueck", "ob_cooldown",
                 "ob_tempo", "ob_xp", "ob_geld", "ob_beute"]
STAR5 = ["..t..", "..a..", "taCat", "..a..", "..t.."]
STAR3 = [".a.", "aCa", ".a."]
STAR_COL = {2: ("gold", {"C": 8, "a": 6, "t": 4}), 3: ("lila", {"C": 9, "a": 7, "t": 5})}
# Stufe 4 (Level 21-50, nur mit Juwelen): jeder Stern in einer anderen Regenbogen-Farbe (Kern hell, Arme satt)
RAINBOW = [("rot", {"C": 8, "a": 6, "t": 4}), ("feuer", {"C": 8, "a": 6, "t": 4}), ("gold", {"C": 8, "a": 6, "t": 4}),
           ("smaragd", {"C": 9, "a": 7, "t": 5}), ("blau", {"C": 9, "a": 6, "t": 4}), ("lila", {"C": 9, "a": 7, "t": 5})]


# Sterne je Stufe: eigene Anordnung und mehr Sterne, je höher die Stufe (nicht dieselben Stellen in neuer Farbe).
# Anzahl, Drehung des Musters (Grad) und wie viele große Sterne.
STAR_LAYOUT = {2: (4, 20, 1), 3: (5, 75, 2), 4: (7, 50, 3)}


def star_spots(img, level, min_dist=7):
    """Stellen für die Sterne einer Stufe: freie Plätze (1 px Abstand zur Form, höchstens 8 px entfernt) rund um das
    Motiv; die Zielrichtungen sind gleichmäßig verteilt und je Stufe anders gedreht, große Sterne nur, wo Platz ist."""
    n, phase, n_big = STAR_LAYOUT[min(4, level)]
    a = img.getchannel("A")
    px = a.load()
    W, Hh = img.size
    filled = {(x, y) for y in range(Hh) for x in range(W) if px[x, y]}
    x0, y0, x1, y1 = a.getbbox()
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2

    def free(cx, cy, r):
        return all(0 <= cx + dx < W and 0 <= cy + dy < Hh and (cx + dx, cy + dy) not in filled
                   for dx in range(-r, r + 1) for dy in range(-r, r + 1))
    cands = []
    for cy in range(1, Hh - 1):
        for cx in range(1, W - 1):
            if free(cx, cy, 2):
                d = min(abs(cx - x) + abs(cy - y) for x, y in filled)
                if d <= 8:
                    cands.append((cx, cy, d, free(cx, cy, 3), math.atan2(cy - my, cx - mx)))
    chosen = []
    for k in range(n):
        target = math.radians(phase) + k * 2 * math.pi / n
        best = None
        for cx, cy, d, big_ok, ang in cands:
            if any(abs(cx - x) + abs(cy - y) < min_dist for x, y, _ in chosen):
                continue
            da = abs((ang - target + math.pi) % (2 * math.pi) - math.pi)
            score = da * 6 + d * 0.35
            if best is None or score < best[0]:
                best = (score, cx, cy, big_ok)
        if best and best[0] < 9:
            big = best[3] and sum(1 for c in chosen if c[2]) < n_big and k % 2 == 0
            chosen.append((best[1], best[2], big))
    return chosen


def tier(img, level):
    """Icon einer Upgrade-Stufe (1 bis 4) auf einer 32-px-Kachel: ab Stufe 2 Funkel-Sterne um das Motiv (2 gold,
    3 lila, 4 Regenbogen: jeder Stern eine andere Farbe), je Stufe anders angeordnet und mehr Sterne."""
    if level < 2:
        return img
    out = img.copy()
    px = out.load()
    spots = star_spots(img, level)
    if len(spots) < 3:
        # Icon füllt die Kachel: Sterne als Glanz auf die Ecken des Motivs, je Stufe andere Ecken
        x0, y0, x1, y1 = img.getchannel("A").getbbox()
        corners = [(x0 + 3, y0 + 3, True), (x1 - 4, y0 + 2, False), (x1 - 3, y1 - 4, False), (x0 + 2, y1 - 4, False),
                   ((x0 + x1) // 2, y0 + 2, False)]
        shift = {2: 0, 3: 1, 4: 2}[min(4, level)]
        extra = STAR_LAYOUT[min(4, level)][0] - 1
        spots += [corners[(shift + i) % len(corners)] for i in range(extra)][:max(0, extra - len(spots))]
    for k, (cx, cy, big) in enumerate(spots):
        mat, idx = RAINBOW[k % len(RAINBOW)] if level >= 4 else STAR_COL[level]
        rows = STAR5 if big else STAR3
        h = len(rows) // 2
        for (x, y), c in grid(rows, cx - h, cy - h).items():
            if 0 <= x < img.width and 0 <= y < img.height:
                px[x, y] = rgb(RAMPS[mat][idx[c]]) + (255,)
    return out


def centered(img, size=32):
    """Icon beschnitten und mittig auf eine Kachel gesetzt (wie die Dialoge es mit (32 - Breite) / 2 setzen)."""
    box = img.getchannel("A").getbbox()
    c = img.crop(box)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.alpha_composite(c, ((size - c.width) // 2, (size - c.height) // 2))
    return out


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
