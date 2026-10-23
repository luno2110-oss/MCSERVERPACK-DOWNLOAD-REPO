"""Scoreboard-Grafik im Pack KubyUI (Schrift kubyplugins:sb), Aufloesung wie die Referenz: 1 Texel je GUI-Pixel.

Masse und Stil nach der Referenz (Chaos SMP), im Bild Pixel fuer Pixel nachgemessen:
- Sidebar 116 px breit, Rahmen 125 px, Rand 6 px (schwarz, 2 Gold, 2 Braun, schwarz), Gold von Orange (oben)
  ueber Hellgelb zu dunklerem Gold (unten), Unterkante gespiegelt, innen ein weicher Schatten.
- Holzranken winden sich links und rechts um den Rahmen nach oben (vorne ueber dem Rand, hinten verdeckt),
  ab und zu 2-3 Blaetter, alles mit Schatten. Links ragen sie bis 5 px ueber den Rahmen (Texturrand M).
- Logo: eckige Buchstaben 13 x 12 px, oben dunkleres Gold, unten helles Gelb, helle Kanten oben und links,
  2 px braune 3D-Kante, schwarzer Rand, Schlagschatten auf den Rahmen. Darunter Silberbuchstaben 10 x 8 px.
- Icons mit farbigen statt schwarzen Umrissen.
Eigene Zeichnungen im Stil der Referenz. Wird von build_ui_pack.py aufgerufen, muss zu
de.kuby.kubyscoreboard.SbUi passen.
"""
import json
import math
import os
import shutil

from PIL import Image

SB_W = 116             # Breite der Sidebar (= Titelbreite)
FRAME_W = SB_W + 9     # Rahmen von 6 px links vom Text bis zum Bildschirmrand
M = 5                  # zusaetzlicher Texturrand links fuer Ranken und Blaetter
TEX_W = FRAME_W + M
BORDER = 6             # Randbreite
NOMINAL_H = 156        # Rahmenhoehe bei 15 Zeilen, fuer den Goldverlauf
MAX_K = 15             # Vanilla zeigt hoechstens 15 Zeilen
A_TITLE = 21           # zusaetzliche Abdunklung innen (Titelzeile hat schon 40 % Hintergrund)
A_LINE = 55            # Zeilen haben nur 30 %, so wirkt die Flaeche gleichmaessig
TOP_EXT = 4            # Oberteil ragt 4 px ueber den Rahmen (Blaetter an den Ecken)
BOTTOM_EXT = 3         # Unterteil ragt 3 px unter den Rahmen
LOGO_TOP = 17          # Logo: 17 px ueber der Rahmenoberkante bis 15 px darunter
LOGO_H = LOGO_TOP + 15

T = (0, 0, 0, 0)


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, f):
    f = max(0.0, min(1.0, f))
    return tuple(round(a + (b - a) * f) for a, b in zip(c1[:3], c2[:3])) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c[:3]) + (c[3],)


def new(w, h):
    return Image.new("RGBA", (w, h), T)


def stamp(rows, palette):
    """Pixel-Art aus Zeichen, ein Zeichen = ein Pixel."""
    w = max(len(r) for r in rows)
    img = new(w, len(rows))
    px = img.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in palette:
                px[x, y] = palette[c] if isinstance(palette[c], tuple) else rgb(palette[c])
    return img


def ramp(keys, f):
    """Farbverlauf ueber Stuetzstellen [(pos, farbe), ...]."""
    f = max(0.0, min(1.0, f))
    for (p0, c0), (p1, c1) in zip(keys, keys[1:]):
        if f <= p1:
            return mix(c0, c1, (f - p0) / (p1 - p0) if p1 > p0 else 0)
    return keys[-1][1]


# =====================================================================================
# Rahmen
# =====================================================================================
GOLD = [(0.0, rgb("C17304")), (0.12, rgb("B87A14")), (0.45, rgb("C89E1E")), (0.65, rgb("DEB622")),
        (0.9, rgb("B89E1E")), (0.96, rgb("7A6A18")), (1.0, rgb("645812"))]
BROWN = [(0.0, rgb("391700")), (0.12, rgb("6A3804")), (0.45, rgb("623800")), (0.65, rgb("5C2904")),
         (0.9, rgb("461D02")), (1.0, rgb("301400"))]
OUT_BLACK = rgb("190E03")
IN_BLACK = rgb("090100")


def band(b, g):
    gold = ramp(GOLD, g)
    brown = ramp(BROWN, g)
    return [OUT_BLACK, shade(gold, 1.08), gold, brown, shade(brown, 0.82), IN_BLACK][b]


def frame_piece(rel_top, h, top, bottom, inner_alpha, ext_top=0, ext_bottom=0):
    """Ein Stueck Rahmen ab rel_top (Pixel unter der Rahmenoberkante), mit Ranken.

    Die Textur hat links M px Rand und oben/unten ext_top/ext_bottom px fuer Blaetter, die ueberstehen.
    Unterkante: Gold innen (Licht von oben)."""
    w = FRAME_W
    img = new(TEX_W, ext_top + h + ext_bottom)
    px = img.load()
    for y in range(h):
        g = (rel_top + y + 0.5) / NOMINAL_H
        for x in range(w):
            dx = min(x, w - 1 - x)
            dt = y if top else 10 ** 6
            db = (h - 1 - y) if bottom else 10 ** 6
            dy = min(dt, db)
            if dx == 0 and dy == 0:
                continue                       # abgerundete Ecke
            d = min(dx, dy)
            if d >= BORDER:
                a = inner_alpha
                # weicher Innenschatten unter der Oberkante und rechts der linken Kante
                if top and dt - BORDER < 3:
                    a += (70, 40, 18)[dt - BORDER]
                if x - BORDER < 3:
                    a += (50, 26, 10)[x - BORDER]
                px[M + x, ext_top + y] = (0, 0, 0, min(a, 200))
                continue
            b = d
            if db <= dx and db < dt and b not in (0, BORDER - 1):
                b = BORDER - 1 - b             # Unterkante gespiegelt
            px[M + x, ext_top + y] = band(b, g)
    vines(img, rel_top - ext_top, top, (rel_top + h) if bottom else None)
    return img


# =====================================================================================
# Holzranken
# =====================================================================================
VINE_P = 18.0          # Ganghoehe der Windung
VINE_A = 3.5           # Ausschlag
VINE_C = 1.5           # Mitte (Rahmenkoordinaten, 0 = Aussenkante)
VINE_YC = 9.0          # hier geht die Ranke oben in den oberen Rand ueber
VINE_TH0 = math.asin((2.0 - VINE_C) / VINE_A)
WOOD = rgb("7E5228")
LEAF = {"l": rgb("B4E46A"), "L": rgb("62B83A"), "d": rgb("2E7A24"), "s": rgb("5E3C1C")}
LEAF_ROWS = [".lLL.", "lLLLd", ".LLdd", "..dd."]   # ein Blatt, zeigt nach links
# Bueschel: Blattpositionen (linke obere Ecke) und Stielpixel relativ zum Ankerpunkt an der Ranke
CLUSTER2 = ([(-5, -4), (-5, 1)], [(-1, -1), (-1, 1)])
CLUSTER3 = ([(-5, -5), (-5, 0), (-2, 3)], [(-1, -2), (-1, 0), (0, 2)])


def helix(y):
    th = 2 * math.pi * (VINE_YC - y) / VINE_P + VINE_TH0
    return VINE_C + VINE_A * math.sin(th), math.cos(th) > 0


def vine_path(y0, y1, bottom):
    """Abtastpunkte (x, y, vorne) der linken Ranke in Rahmenkoordinaten zwischen y0 und y1."""
    pts = []
    x = 16.0
    while x >= 2.0:                                   # oben: am oberen Rand entlang zur Ecke
        pts.append((x, 2.5 + 0.6 * math.sin(x * 0.8), True))
        x -= 0.1
    y = 2.5
    while y < VINE_YC:                                # Ecke: senkrecht nach unten
        pts.append((2.0, y, True))
        y += 0.1
    end = (bottom - 4.0) if bottom is not None else y1 + 2
    y = max(VINE_YC, y0 - 2)
    while y <= min(end, y1 + 2):                      # Windung an der Seite
        hx, front = helix(y)
        pts.append((hx, y, front))
        y += 0.08
    if bottom is not None:                            # unten: zur Ecke und am unteren Rand entlang
        hx, _ = helix(end)
        by = bottom - 3.5
        for i in range(21):
            t = i / 20
            pts.append((hx + (2.0 - hx) * t, end + (by - end) * t, True))
        x = 2.0
        while x <= 15.0:
            pts.append((x, by + 0.5 * math.sin(x * 0.8), True))
            x += 0.1
    return [p for p in pts if y0 - 3 <= p[1] <= y1 + 3]


def vine_leaves(bottom):
    """Blattbueschel (Bueschel, Ankerpunkt x, y, gespiegelt) der linken Ranke."""
    out = [(CLUSTER3, 17.0, 2.5, True), (CLUSTER2, 0.0, 6.0, False)]
    m = 1
    while True:
        y = VINE_YC + (VINE_TH0 / (2 * math.pi) + m) * VINE_P
        if y > NOMINAL_H + 60 or (bottom is not None and y > bottom - 12):
            break
        if m % 2 == 0:
            hx, _ = helix(y)
            out.append((CLUSTER3 if m % 4 == 0 else CLUSTER2, hx - 0.5, y, False))
        m += 1
    if bottom is not None:
        out.append((CLUSTER2, 16.0, bottom - 3.5, True))
    return out


def rnd(v):
    return int(math.floor(v + 0.5))


def vines(img, rel0, top, bottom):
    """Zeichnet beide Ranken in ein Rahmenstueck. rel0 = Rahmen-y der ersten Texturzeile."""
    w, h = img.size
    px = img.load()
    base = img.copy().load()
    y0, y1 = rel0, rel0 + h
    front, back, leaf = set(), set(), {}

    def to_tex(x, y, right):
        fx = (FRAME_W - 1 - x) if right else x
        return M + fx, y - rel0

    for right in (False, True):
        for (x, y, f) in vine_path(y0, y1, bottom):
            for dx in (0, 1):
                for dy in (0, 1):
                    tx, ty = to_tex(math.floor(x - 0.5) + dx, math.floor(y - 0.5) + dy, right)
                    if 0 <= tx < w and 0 <= ty < h:
                        (front if f else back).add((tx, ty))
        for ((leaves, stems), ax, ay, mirror) in vine_leaves(bottom):
            flip = mirror != right                     # rechte Seite zeigt nach innen
            sgn = -1 if flip else 1

            def put(lx, ly, color):
                tx, ty = to_tex(rnd(ax) + lx, rnd(ay) + ly, right)
                if 0 <= tx < w and 0 <= ty < h:
                    leaf[(tx, ty)] = color
            for (sx, sy) in stems:
                put(sgn * sx, sy, LEAF["s"])
            for (lx, ly) in leaves:
                for r, row in enumerate(LEAF_ROWS):
                    for c, ch in enumerate(row):
                        if ch != ".":
                            cx = lx + c if not flip else -(lx + c)
                            put(cx, ly + r, LEAF[ch])
    back = {p for p in back - front if base[p][3] < 255}   # hinten nur sichtbar, wo kein Rand ist
    wood = front | back
    solid = wood | set(leaf)
    for (x, y) in front | set(leaf):                       # Schatten nach unten rechts
        q = (x + 1, y + 1)
        if q in solid or not (0 <= q[0] < w and 0 <= q[1] < h):
            continue
        c = base[q]
        px[q] = shade(c, 0.58) if c[3] == 255 else (0, 0, 0, min(255, c[3] + 95))
    for (x, y) in wood:
        c = WOOD
        if (x, y - 1) not in wood:
            c = shade(WOOD, 1.32)
        elif (x + 1, y) not in wood or (x, y + 1) not in wood:
            c = shade(WOOD, 0.66)
        if (x, y) in back:
            c = shade(c, 0.62)
        px[x, y] = c
    for p, c in leaf.items():
        px[p] = c


# =====================================================================================
# Logo-Schrift (Stil wie "CHAOS SMP"), mit 3 px 3D-Kante, damit sie heraussticht
# =====================================================================================
BIG = {
    "K": ["XXXXX...XXXXX", "XXXXX..XXXXX.", "XXXXX.XXXXX..", "XXXXXXXXXX...", "XXXXXXXXX....", "XXXXXXXX.....",
          "XXXXXXXX.....", "XXXXXXXXX....", "XXXXXXXXXX...", "XXXXX.XXXXX..", "XXXXX..XXXXX.", "XXXXX...XXXXX"],
    "U": ["XXXXX...XXXXX"] * 9 + ["XXXXXXXXXXXXX"] * 3,
    "B": ["XXXXXXXXXXXX.", "XXXXXXXXXXXXX", "XXXXX...XXXXX", "XXXXX...XXXXX", "XXXXXXXXXXXX.", "XXXXXXXXXXXX.",
          "XXXXXXXXXXXXX", "XXXXX...XXXXX", "XXXXX...XXXXX", "XXXXX...XXXXX", "XXXXXXXXXXXXX", "XXXXXXXXXXXX."],
    "Y": ["XXXXX...XXXXX", "XXXXX...XXXXX", "XXXXX...XXXXX", "XXXXXX.XXXXXX", "XXXXXXXXXXXXX", ".XXXXXXXXXXX.",
          "...XXXXXXX...", "....XXXXX....", "....XXXXX....", "....XXXXX....", "....XXXXX....", "....XXXXX...."],
}
SMALL = {
    "S": [".XXXXXXXXX", "XXXXXXXXXX", "XXXX......", "XXXXXXXXX.", ".XXXXXXXXX", "......XXXX", "XXXXXXXXXX",
          "XXXXXXXXX."],
    "M": ["XXX.....XXX", "XXXX...XXXX", "XXXXX.XXXXX", "XXXXXXXXXXX", "XXX.XXX.XXX", "XXX..X..XXX", "XXX.....XXX",
          "XXX.....XXX"],
    "P": ["XXXXXXXXX.", "XXXXXXXXXX", "XXX....XXX", "XXXXXXXXXX", "XXXXXXXXX.", "XXX.......", "XXX.......",
          "XXX......."],
    "U": ["XXX....XXX"] * 6 + ["XXXXXXXXXX", ".XXXXXXXX."],
    "R": ["XXXXXXXXX.", "XXXXXXXXXX", "XXX....XXX", "XXXXXXXXXX", "XXXXXXXXX.", "XXX..XXXX.", "XXX...XXXX",
          "XXX....XXX"],
    "V": ["XXX....XXX", "XXX....XXX", "XXX....XXX", "XXXX..XXXX", ".XXX..XXX.", ".XXXXXXXX.", "..XXXXXX..",
          "...XXXX..."],
    "I": ["XXX"] * 8,
    "A": [".XXXXXXXX.", "XXXXXXXXXX", "XXX....XXX", "XXX....XXX", "XXXXXXXXXX", "XXXXXXXXXX", "XXX....XXX",
          "XXX....XXX"],
    "L": ["XXX......"] * 6 + ["XXXXXXXXX"] * 2,
    "H": ["XXX....XXX"] * 3 + ["XXXXXXXXXX"] * 2 + ["XXX....XXX"] * 3,
    "B": ["XXXXXXXXX.", "XXXXXXXXXX", "XXX....XXX", "XXXXXXXXX.", "XXXXXXXXXX", "XXX....XXX", "XXXXXXXXXX",
          "XXXXXXXXX."],
}
GOLD_PAL = {"up": rgb("E4B104"), "lo": rgb("F7DD2D"), "top_up": rgb("FAD830"), "top_lo": rgb("FFF4BE"),
            "left_up": rgb("F5CC24"), "left_lo": rgb("FFF0AA"), "cream": rgb("FFF6D4"),
            "ext": [rgb("8A3604"), rgb("702A02"), rgb("561E00")], "out": rgb("100600")}
SILVER_PAL = {"up": rgb("9C9B9B"), "lo": rgb("C9C7C8"), "top_up": rgb("C9C7C8"), "top_lo": rgb("F9F7F5"),
              "left_up": rgb("BEBEC0"), "left_lo": rgb("F9F7F5"), "cream": rgb("FFFFFF"),
              "ext": [rgb("6A6A6E"), rgb("545458"), rgb("404044")], "out": rgb("0C0C0E")}


def block_word(word, font, pal):
    """Buchstaben mit zweifarbigem Koerper, hellen Kanten, 3D-Kante (Tiefe = Laenge von pal["ext"]) und Rand.
    Aeussere Ecken sind um 1 px abgerundet, Zaehler werden dunkel gefuellt."""
    height = len(font[word[0]])
    depth = len(pal["ext"])
    letters, x0 = [], 0
    for c in word:
        letters.append({(x0 + x, y) for y, row in enumerate(font[c]) for x, v in enumerate(row) if v == "X"})
        x0 += len(font[c][0]) + 1
    w, h = x0 - 1 + 2, height + depth + 2
    body = set()
    for cells in letters:
        corners = set()
        for (x, y) in cells:
            for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
                if (x + dx, y) not in cells and (x, y + dy) not in cells and (x + dx, y + dy) not in cells:
                    corners.add((x, y))
        body |= {(p[0] + 1, p[1] + 1) for p in cells - corners}
    ext = {}
    for (x, y) in body:
        for d in range(1, depth + 1):
            if (x, y + d) not in body and (x, y + d) not in ext:
                ext[(x, y + d)] = d
    solid = body | set(ext)
    # Eingeschlossene Zaehler fuellen
    outside, stack = set(), [(0, 0)]
    while stack:
        p = stack.pop()
        if p in outside or p in solid or not (-1 <= p[0] <= w and -1 <= p[1] <= h):
            continue
        outside.add(p)
        stack.extend(((p[0] + 1, p[1]), (p[0] - 1, p[1]), (p[0], p[1] + 1), (p[0], p[1] - 1)))
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if (x, y) in solid:
                continue
            near = any((x + dx, y + dy) in solid for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            if near or (x, y) not in outside:
                px[x, y] = pal["out"]
    for (x, y), d in ext.items():
        px[x, y] = pal["ext"][d - 1]
    for (x, y) in body:
        r = y - 1
        upper = r < height // 2
        c = pal["up"] if upper else pal["lo"]
        if (x, y - 1) not in body:
            run = sum(1 for k in range(1, 4) if (x - k, y) in body and (x - k, y - 1) not in body)
            c = pal["cream"] if (r == 0 and run < 3) else (pal["top_up"] if upper else pal["top_lo"])
        elif (x - 1, y) not in body:
            c = pal["left_up"] if upper else pal["left_lo"]
        px[x, y] = c
    return img


def drop_shadow(img, strength=(135, 75)):
    """Weicher Schatten nach unten rechts unter allen deckenden Pixeln."""
    src = img.load()
    w, h = img.size
    solid = {(x, y) for y in range(h) for x in range(w) if src[x, y][3] > 200}
    sh = new(w, h)
    px = sh.load()
    for (x, y) in solid:
        for (dx, dy, a) in ((1, 1, strength[0]), (0, 2, strength[0]), (1, 2, strength[0]), (2, 2, strength[0]),
                            (1, 3, strength[1]), (2, 3, strength[1]), (3, 3, strength[1]), (2, 4, strength[1])):
            p = (x + dx, y + dy)
            if p in solid or not (0 <= p[0] < w and 0 <= p[1] < h):
                continue
            if px[p][3] < a:
                px[p] = (10, 4, 0, a)
    sh.alpha_composite(img)
    return sh


# =====================================================================================
# Logo je Modus (Rahmenbreite + Rand M; Zeile LOGO_TOP = Rahmenoberkante)
# =====================================================================================
ARROW = {
    # Pfeil steckt schraeg im oberen Rand links neben dem kleinen Schriftzug (Rahmenkoordinaten, y relativ zur
    # Rahmenoberkante): Spitze schaut heraus, Schaft 45 Grad nach unten aussen, Feder als Faecher.
    "head": [(43, 0, "s"), (44, 0, "W"), (42, 1, "s"), (43, 1, "W"), (44, 1, "s"), (42, 2, "s"), (43, 2, "s")],
    "shaft": [(42, 3), (41, 4), (40, 5), (39, 6), (38, 7), (37, 8)],
    "feather": [(33, 9, "R"), (34, 9, "R"), (35, 9, "R"), (36, 9, "O"),
                (32, 10, "P"), (33, 10, "P"), (34, 10, "R"), (35, 10, "C"), (36, 10, "R"),
                (34, 11, "C"), (35, 11, "R"), (36, 11, "P"),
                (35, 12, "P"), (36, 12, "P"),
                (35, 13, "P")],
}
ARROW_COL = {"s": rgb("AEB2BC"), "W": rgb("F2F2F6"), "b": rgb("A0604A"), "R": rgb("AF3737"), "P": rgb("F06E6E"),
             "C": rgb("FAD8BE"), "O": rgb("D88C64")}


def stuck_arrows():
    """Beide steckenden Pfeile (rechts gespiegelt) mit schwarzem Rand."""
    layer = new(TEX_W, LOGO_H)
    px = layer.load()
    pts = []
    for (x, y, c) in ARROW["head"] + [(x, y, "b") for (x, y) in ARROW["shaft"]] + ARROW["feather"]:
        pts.append((x, y, c))
        pts.append((FRAME_W - 1 - x, y, c))
    for (x, y, c) in pts:
        px[M + x, ry(y)] = ARROW_COL[c]
    out = new(TEX_W, LOGO_H)
    op = out.load()
    for y in range(LOGO_H):
        for x in range(TEX_W):
            if px[x, y][3]:
                continue
            if any(0 <= x + dx < TEX_W and 0 <= y + dy < LOGO_H and px[x + dx, y + dy][3]
                   for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                op[x, y] = rgb("0F0804")
    out.alpha_composite(layer)
    return out


def ry(rel):
    return rel + LOGO_TOP


def logo(word, arrows=False):
    w, h = TEX_W, LOGO_H
    img = new(w, h)
    if arrows:
        img.alpha_composite(stuck_arrows())
    text = new(w, h)
    big = block_word("KUBY", BIG, GOLD_PAL)
    text.alpha_composite(big, (M + (FRAME_W - big.width) // 2, ry(0) - big.height + 1))
    small = block_word(word, SMALL, SILVER_PAL)
    text.alpha_composite(small, (M + (FRAME_W - small.width) // 2, ry(-2)))
    img.alpha_composite(drop_shadow(text))
    img.load()[w - 1, h - 1] = (0, 0, 0, 1)
    return img


# =====================================================================================
# Zeilen-Icons 8 x 8 (Vorschub 9), weich mit farbigen Umrissen
# =====================================================================================
ICONS = {
    "coin": (["........", "yYYy....", "oOOd....", "yYYy.yYy", "oOOd.oOd", "yYYy.yYy", "oOOd.oOd", "dddd.ddd"],
             {"y": "F6C850", "Y": "FFE89A", "o": "E08A28", "O": "F0A838", "d": "9A4A14"}),
    "gem": (["...gg...", "..gLLg..", ".gLLMGg.", ".gLMMGd.", ".gLMMGd.", ".gMMGGd.", "..gGGd..", "...dd..."],
            {"g": "3C8A1E", "L": "C8F078", "M": "96D246", "G": "5AAA28", "d": "245A12"}),
    "xp": (["...yy...", "..yYYy..", "..yYYy..", "ll.yy.LL", "lL.gg.LL", "..gGGg..", "..gGGg..", "...gg..."],
           {"y": "C8AA14", "Y": "FAE63C", "l": "88B828", "L": "BEE646", "g": "1E8C28", "G": "3CC83C"}),
    "trophy": (["oWYYYYYo", "oWYYYYOo", ".oWYYOo.", "..oYOo..", "...YO...", "...YO...", "..WYYO..", ".oOOOOo."],
               {"W": "FFF4BE", "Y": "F4D068", "O": "D49A30", "o": "8E5A14"}),
    "sword": (["......sW", ".....sWs", "....sWs.", "...sWs..", "g.sWs...", ".gGs....", ".bGg....", "b..g...."],
              {"W": "EEEEF2", "s": "9AA0AC", "G": "6A6A78", "g": "4A4A56", "b": "7A4A28"}),
    "skull": ([".wWWWWw.", "wWWWWWWs", "WWWWWWWs", "WkkWWkks", "WWWkWWWs", ".sWWWWs.", ".WsWsWs.", "..ssss.."],
              {"W": "F0E8DA", "w": "DCD2C2", "s": "AAA090", "k": "463A34"}),
    "clock": ([".bBBBBb.", "..gCCg..", "..gCCg..", "...cg...", "...gc...", "..gccg..", "..cccc..", ".bBBBBb."],
              {"B": "9A6230", "b": "643C1E", "g": "C8EAF4", "C": "8CE4F4", "c": "28A8C8"}),
    "people": (["..hh..hh", ".hhhhhhh", ".SSh.SSh", ".SS..SS.", "TTTTUUUU", "TTTTUUUU", "TTTTUUUU", ".tt..uu."],
               {"h": "5A3A22", "S": "E0A387", "T": "2CA8D8", "t": "1C78A8", "U": "4CC04C", "u": "2C8A2C"}),
    "rank": (["........", "Y..YR..Y", "YG.GG.GO", "YGGGGGGO", "GGGRGGGO", "OOOOOOOD", "DDDDDDDD", "........"],
             {"Y": "FFE478", "G": "F6B42A", "O": "E08418", "D": "9A4A0E", "R": "E8283C"}),
    "team": (["L.......", "PRRRRRRr", "PRLLRRr.", "PRRRRr..", "PRRRRRr.", "Prrrrrrr", "P.......", "p......."],
             {"L": "D8DCE4", "P": "5A7088", "p": "34404E", "R": "D83440", "r": "961C28"}),
    "claim": (["....R.c.", "...RRRc.", "..RRRRRr", ".RRRRRRr", "..WWWWw.", "..WgWdw.", "..WWWdw.", "..ssss.."],
              {"R": "D23C3C", "r": "8C2222", "c": "5A5A64", "W": "F0EAE0", "w": "B8B0A4", "g": "78C8DC",
               "d": "7A5028", "s": "6A6A70"}),
    "token": (["..oYYo..", ".oYllYo.", "oYlDDYYo", "oYDooDYo", "oYDooDYo", "oYYDDYYo", ".oYYYYo.", "..oooo.."],
              {"o": "A8680E", "Y": "F4C232", "l": "FFEC8C", "D": "D8961E"}),
    "level": (["...ll...", "...lY...", "lllYYYYo", ".lYYYYo.", "..YYYo..", ".YYoYYo.", ".Yo..oo.", "........"],
              {"l": "FFF08C", "Y": "F8C428", "o": "C46A12"}),
    "job": ([".......B", "......Bw", ".....B.w", "....B..w", "...B...w", "..B....j", ".dB.....", "dd......"],
            {"B": "A06A34", "d": "4A3020", "w": "E0E0E0", "j": "9AA0AC"}),
    "arrow": (["........", "....O...", "....OO..", "OOOOOOO.", "OOOOOOOO", "ddddddd.", "....dd..", "....d..."],
              {"O": "FAA028", "d": "C46414"}),
    "compass": (["..oYYo..", ".oYddYo.", "oYdWkdYo", "oYdkWdYo", "oYdRkdYo", "oYddRdYo", ".oYddYo.", "..oooo.."],
                {"o": "8E5A10", "Y": "F2BE34", "d": "1E2A3A", "W": "F0F0F0", "k": "3C5A78", "R": "E03C3C"}),
    "spawn": (["...cc...", "..cCCc..", "..cCCc..", "...cc...", ".gGGGGg.", "gGGGGGGg", "gggggggg", ".dddddd."],
              {"c": "3CAAB4", "C": "A0F0F0", "g": "8C8C96", "G": "C4C4CE", "d": "5A5A64"}),
}
ICON_ORDER = ["coin", "gem", "xp", "trophy", "sword", "skull", "clock", "people", "rank", "team", "claim", "token",
              "level", "job", "arrow", "spawn", "compass"]


def icon(name):
    rows, pal = ICONS[name]
    img = new(8, 8)
    img.alpha_composite(stamp(rows, pal), (0, 0))
    px = img.load()
    if not any(px[7, y][3] for y in range(8)):
        px[7, 7] = (0, 0, 0, 1)
    return img


# =====================================================================================
# Survival-Anzeige oben (Bossbar-Platz): Holzleisten mit Cremefeld, Pfosten mit Blaettern und Blume
# Muss zu de.kuby.kubysurvival.hud.SurvivalHud passen.
# =====================================================================================
HUD_H = 18                     # Leiste: Rand, Holz, Cremefeld (11), Holz, Rand
HUD_DOWN = 5                   # ganze Leiste 5 px tiefer (Abstand zum oberen Bildschirmrand)
HUD_TEXT_SHIFT = 4 + HUD_DOWN  # Text tiefer als normal, damit er mittig im Cremefeld sitzt
HUD_COLUMN = [rgb("3A2210"), rgb("D2924E"), rgb("AA6A34"), rgb("70421E"), rgb("E6DABE")] + [rgb("F8F0DA")] * 10 + \
             [rgb("70421E"), rgb("965C2C"), rgb("3A2210")]
FLOWER = {"W": rgb("FAFAF4"), "Y": rgb("F0C83C")}


def hud_tile(width):
    img = new(width, HUD_H)
    px = img.load()
    for y, c in enumerate(HUD_COLUMN):
        for x in range(width):
            px[x, y] = c
    return img


def hud_cap():
    """Linkes Ende: Holzpfosten (Spalten 5-8) mit Blaettern und einer Blume. Rechts = gespiegelt."""
    w, h = 12, 20
    img = new(w, h)
    px = img.load()
    post = [rgb("3A2210"), rgb("C8884A"), rgb("9A5E2C"), rgb("5E3618")]
    for y in range(h):
        for i, c in enumerate(post):
            px[5 + i, y] = rgb("3A2210") if y in (0, h - 1) else c
    leaves = {}

    def leaf(x0, y0, mirror):
        for r, row in enumerate(LEAF_ROWS):
            for c, ch in enumerate(row):
                if ch != ".":
                    x = x0 + (len(row) - 1 - c if mirror else c)
                    if 0 <= x < w and 0 <= y0 + r < h:
                        leaves[(x, y0 + r)] = LEAF[ch]
    leaf(0, 0, False)
    leaf(1, 3, False)
    leaf(0, 8, False)
    leaf(0, 14, False)
    leaf(7, 0, True)
    leaf(7, 16, True)
    for (x, y), c in leaves.items():               # Schatten unten rechts
        q = (x + 1, y + 1)
        if q not in leaves and 0 <= q[0] < w and 0 <= q[1] < h:
            base = px[q]
            px[q] = shade(base, 0.6) if base[3] == 255 else (0, 0, 0, 90)
    for p, c in leaves.items():
        px[p] = c
    for (x, y, ch) in ((3, 8, "W"), (2, 9, "W"), (3, 9, "Y"), (4, 9, "W"), (3, 10, "W")):
        px[x, y] = FLOWER[ch]
    return img


def build_hud(out, spaces):
    tex = os.path.join(out, "assets/kubyplugins/textures/hud")
    font = os.path.join(out, "assets/kubyplugins/font")
    shutil.rmtree(tex, ignore_errors=True)
    os.makedirs(tex, exist_ok=True)
    providers = [{"type": "space", "advances": dict(spaces, **{" ": 4})}]
    # Minecraft-Schrift (ASCII), tiefer gesetzt. Zeilen 2-7 von ascii.png sind die Zeichen 0x21-0x7E.
    empty = "\u0000" * 16
    rows = [empty] * 2
    for r in range(2, 8):
        rows.append("".join("\u0000" if r * 16 + c in (0x20, 0x7F) else chr(r * 16 + c) for c in range(16)))
    rows += [empty] * 8
    providers.append({"type": "bitmap", "file": "minecraft:font/ascii.png", "height": 8,
                      "ascent": 7 - HUD_TEXT_SHIFT, "chars": rows})

    def save(img, name, height, ascent, char):
        img.save(os.path.join(tex, name + ".png"))
        providers.append({"type": "bitmap", "file": "kubyplugins:hud/" + name + ".png", "height": height,
                          "ascent": ascent, "chars": [char]})

    # Leiste oben bei y = 1 + HUD_DOWN (Bossbar-Titel steht bei y = 3): ascent 3 + 7 - 1 - HUD_DOWN
    for i, width in enumerate((1, 2, 4, 8, 16, 32, 64)):
        save(hud_tile(width), "tile_%d" % width, HUD_H, 9 - HUD_DOWN, chr(0xE040 + i))
    cap = hud_cap()
    save(cap, "cap_left", cap.height, 10 - HUD_DOWN, chr(0xE001))
    save(cap.transpose(Image.FLIP_LEFT_RIGHT), "cap_right", cap.height, 10 - HUD_DOWN, chr(0xE002))
    dot = new(2, 2)
    dot.paste(rgb("8C7860"), (0, 0, 2, 2))
    save(dot, "dot", 2, 7 - HUD_TEXT_SHIFT - 3, chr(0xE003))
    # Icons aus dem Scoreboard, 1 px ueber dem Text
    for i, name in enumerate(ICON_ORDER):
        providers.append({"type": "bitmap", "file": "kubyplugins:sb/sb_" + name + ".png", "height": 8,
                          "ascent": 7 - HUD_TEXT_SHIFT + 1, "chars": [chr(0xE010 + i)]})
    with open(os.path.join(font, "hud.json"), "w", encoding="utf-8") as f:
        json.dump({"providers": providers}, f, ensure_ascii=True, indent=1)
    # Den weissen Bossbar-Balken unsichtbar machen (die Anzeige nutzt nur den Titel)
    bar = os.path.join(out, "assets/minecraft/textures/gui/sprites/boss_bar")
    os.makedirs(bar, exist_ok=True)
    for name in ("white_background", "white_progress"):
        new(182, 5).save(os.path.join(bar, name + ".png"))
    print("ok hud")


# =====================================================================================
# Bauen
# =====================================================================================
def build(out, spaces):
    tex = os.path.join(out, "assets/kubyplugins/textures/sb")
    font = os.path.join(out, "assets/kubyplugins/font")
    shutil.rmtree(tex, ignore_errors=True)
    os.makedirs(tex, exist_ok=True)
    os.makedirs(font, exist_ok=True)
    providers = [{"type": "space", "advances": spaces}]

    def save(img, name, height, ascent, char):
        img.save(os.path.join(tex, name + ".png"))
        providers.append({"type": "bitmap", "file": "kubyplugins:sb/" + name + ".png", "height": height,
                          "ascent": ascent, "chars": [char]})

    # Rahmen: Oberteil (Blaetter, 6 px Rand, Titelzeile), je Zeile ein Mittelstueck, Unterteil (2 Zeilen + Rand)
    top = frame_piece(0, 15, True, False, A_TITLE, ext_top=TOP_EXT)
    save(top, "frame_top", top.height, 14 + TOP_EXT, chr(0xE100))
    for k in range(1, MAX_K + 1):
        save(frame_piece(6 + 9 * k, 9, False, False, A_LINE), "frame_mid_%d" % k, 9, 8 - 9 * k, chr(0xE100 + k))
        bot = frame_piece(6 + 9 * k, 24, False, True, A_LINE, ext_bottom=BOTTOM_EXT)
        save(bot, "frame_bottom_%d" % k, bot.height, 8 - 9 * k, chr(0xE140 + k))

    for i, (mode, word) in enumerate([("smp", "SMP"), ("survival", "SURVIVAL"), ("hub", "HUB")]):
        save(logo(word, arrows=(mode == "smp")), "logo_" + mode, LOGO_H, LOGO_H - 1, chr(0xE050 + i))
    for i, name in enumerate(ICON_ORDER):
        save(icon(name), "sb_" + name, 8, 7, chr(0xE010 + i))

    with open(os.path.join(font, "sb.json"), "w", encoding="utf-8") as f:
        json.dump({"providers": providers}, f, ensure_ascii=True, indent=1)
    print("ok scoreboard", len(providers) - 1, "Zeichen")
    build_hud(out, spaces)
