"""Scoreboard-Grafik im Pack KubyUI (Schrift kubyplugins:sb), Aufloesung wie die Referenz: 1 Texel je GUI-Pixel.

Masse und Stil nach der Referenz (Chaos SMP), im Bild Pixel fuer Pixel nachgemessen:
- Sidebar 116 px breit, Rahmen 125 px, Rand 6 px (schwarz, 2 Gold, 2 Braun, schwarz), Gold von Orange (oben)
  ueber Hellgelb zu dunklerem Gold (unten), Unterkante gespiegelt, innen ein weicher Schatten.
- Logo: Buchstaben 13 x 12 px, oben dunkleres Gold, unten helles Gelb, helle Kanten oben und links,
  2 px braune 3D-Kante, schwarzer Rand, Schlagschatten auf den Rahmen. Darunter Silberbuchstaben 10 x 8 px.
- Figuren ohne schwarzen Rand, nur weich schattiert. Icons mit farbigen statt schwarzen Umrissen.
Eigene Zeichnungen im Stil der Referenz. Wird von build_ui_pack.py aufgerufen, muss zu
de.kuby.kubyscoreboard.SbUi passen.
"""
import json
import os
import shutil

from PIL import Image

SB_W = 116             # Breite der Sidebar (= Titelbreite)
FRAME_W = SB_W + 9     # Rahmen von 6 px links vom Text bis zum Bildschirmrand
BORDER = 6             # Randbreite
NOMINAL_H = 156        # Rahmenhoehe bei 15 Zeilen, fuer den Goldverlauf
MAX_K = 15             # Vanilla zeigt hoechstens 15 Zeilen
INNER_W = FRAME_W - 12  # Innenbreite (113 px), hier liegt die Deko unten
A_TITLE = 21           # zusaetzliche Abdunklung innen (Titelzeile hat schon 40 % Hintergrund)
A_LINE = 55            # Zeilen haben nur 30 %, so wirkt die Flaeche gleichmaessig
LOGO_H = 43            # Logo: 28 px ueber der Rahmenoberkante bis 15 px darunter
LOGO_TOP = 28
DECO_H = 22            # Deko unten, steht auf dem unteren Rand

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


def frame_piece(rel_top, h, top, bottom, inner_alpha):
    """Ein Stueck Rahmen ab rel_top (Pixel unter der Rahmenoberkante). Unterkante: Gold innen (Licht von oben)."""
    w = FRAME_W
    img = new(w, h)
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
                px[x, y] = (0, 0, 0, min(a, 200))
                continue
            b = d
            if db <= dx and db < dt and b not in (0, BORDER - 1):
                b = BORDER - 1 - b             # Unterkante gespiegelt
            px[x, y] = band(b, g)
    return img


# =====================================================================================
# Logo-Schrift (Stil wie "CHAOS SMP")
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
            "ext": [rgb("7B2E02"), rgb("682802")], "out": rgb("100600")}
SILVER_PAL = {"up": rgb("9C9B9B"), "lo": rgb("C9C7C8"), "top_up": rgb("C9C7C8"), "top_lo": rgb("F9F7F5"),
              "left_up": rgb("BEBEC0"), "left_lo": rgb("F9F7F5"), "cream": rgb("FFFFFF"),
              "ext": [rgb("5E5E5E"), rgb("4E4E4E")], "out": rgb("0C0C0E")}


def block_word(word, font, pal):
    """Buchstaben mit zweifarbigem Koerper, hellen Kanten, 2 px 3D-Kante und Rand. Zaehler werden dunkel gefuellt."""
    height = len(font[word[0]])
    letters, x0 = [], 0
    for c in word:
        cells = {(x0 + x, y) for y, row in enumerate(font[c]) for x, v in enumerate(row) if v == "X"}
        letters.append(cells)
        x0 += len(font[c][0]) + 1
    w, h = x0 - 1 + 2, height + 2 + 2
    body = {}
    for cells in letters:
        # aeussere Ecken abrunden (nur echte Ecken, Diagonalen bleiben voll)
        corners = set()
        for (x, y) in cells:
            for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
                if (x + dx, y) not in cells and (x, y + dy) not in cells and (x + dx, y + dy) not in cells:
                    corners.add((x, y))
        for p in cells - corners:
            body[(p[0] + 1, p[1] + 1)] = True
    ext = {}
    for (x, y) in body:
        for d in (1, 2):
            if (x, y + d) not in body and (x, y + d) not in ext:
                ext[(x, y + d)] = d
    solid = set(body) | set(ext)
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


def drop_shadow(img, strength=(105, 55)):
    """Weicher Schatten nach unten rechts unter allen deckenden Pixeln."""
    src = img.load()
    w, h = img.size
    solid = {(x, y) for y in range(h) for x in range(w) if src[x, y][3] > 200}
    sh = new(w, h)
    px = sh.load()
    for (x, y) in solid:
        for (dx, dy, a) in ((1, 1, strength[0]), (0, 2, strength[0]), (1, 2, strength[0]), (2, 2, strength[1]),
                            (1, 3, strength[1]), (2, 3, strength[1])):
            p = (x + dx, y + dy)
            if p in solid or not (0 <= p[0] < w and 0 <= p[1] < h):
                continue
            if px[p][3] < a:
                px[p] = (10, 4, 0, a)
    sh.alpha_composite(img)
    return sh


# =====================================================================================
# Figuren: weich schattiert, ohne schwarzen Rand
# =====================================================================================
def figure(rows, colors):
    """Teile: Buchstabe = Teil. Kanten je Teil: links/oben heller, rechts/unten dunkler."""
    h, w = len(rows), max(len(r) for r in rows)
    part = {(x, y): c for y, r in enumerate(rows) for x, c in enumerate(r) if c in colors}
    img = new(w, h)
    px = img.load()
    for (x, y), c in part.items():
        base = rgb(colors[c])
        if c in "EKMX":
            px[x, y] = base
            continue
        same = lambda dx, dy: part.get((x + dx, y + dy)) == c
        f = 1.0
        if not same(1, 0):
            f = 0.78
        elif not same(0, 1):
            f = 0.86
        elif not same(-1, 0):
            f = 1.12
        elif not same(0, -1):
            f = 1.1
        px[x, y] = shade(base, f)
    return img


FIGHTER = [
    "..HHHHHHHHHH......",
    ".HHHHHHHHHHHH.....",
    ".HHHHHHHHHHHH.....",
    ".HHSSSSSSSSHH.....",
    ".HSSSSSSSSSSH..SS.",
    ".SSEESSSSEESS..SS.",
    ".SSEKSSSSEKSS..TT.",
    ".SSSSSSSSSSSS..TT.",
    ".SSSSSMMSSSSS..TT.",
    "..SSSSSSSSSS..TTT.",
    "...TTTTTTTTTTTTT..",
    "..TTTTTTTTTTTTT...",
    ".TTTTTTTTTTTT.....",
    ".TT.TTTTTTTT......",
    ".TT.TTTTTTTT......",
    ".SS.TTTTTTTT......",
    ".SS.TTTTTTTT......",
    "....PPPPPPPP......",
    "....PPPPPPPP......",
    "....PPPPPPPP......",
    "....PPP..PPP......",
    "....PPP..PPP......",
    "....PPP..PPP......",
    "....BBB..BBB......",
    "...BBBB..BBBB.....",
]
ARCHER = [
    "...HHHHHHHHHH...",
    "..HHHHHHHHHHHH..",
    "..HHHHHHHHHHHHH.",
    "..HHSSSSSSSSHHH.",
    "..HSSSSSSSSSSHHH",
    "..SSEESSSSEESHH.",
    "..SSKESSSSKESHH.",
    "..SSSSSSSSSSSHH.",
    "..SSSSSMMSSSS.H.",
    "...SSSSSSSSSS...",
    ".....TTTTTTTT...",
    "SSTTTTTTTTTTTT..",
    "SSTTTTTTTTTTTTT.",
    ".....TTTTTTTT.TT",
    ".....TTTTTTTT.TT",
    ".....TTTTTTTT.SS",
    ".....TTTTTTTT.SS",
    ".....PPPPPPPP...",
    ".....PPPPPPPP...",
    ".....PPPPPPPP...",
    ".....PPP..PPP...",
    ".....PPP..PPP...",
    ".....PPP..PPP...",
    ".....BBB..BBB...",
    "....BBBB..BBBB..",
]
SKIN = "E0A387"
FACE = {"S": SKIN, "E": "F8F8F8", "K": "1A1A24", "M": "9A5446"}

SWORD = stamp(["........gW", ".......gWg", "......gWg.", ".....gWg..", "....gWg...", "...gWg....",
               "d.gWg.....", ".dGg......", ".NGd......", "N..d......"],
              {"W": "F0F0F4", "g": "9AA0AC", "G": "6A6A78", "d": "3E3E48", "N": "6E4424"})
BOW = stamp(["...bw", "..b.w", ".b..w", ".b..w", "b...w", "b...w", "b...w", "b...w", "b...w", ".b..w",
             ".b..w", "..b.w", "...bw"], {"b": "8C5A2A", "w": "D8D8D8"})
HOE = stamp(["......GGGG", "......Gg..", ".......N..", "......N...", ".....N....", "....N.....", "...N......",
             "..N.......", ".N........", "N........."], {"G": "C8CCD6", "g": "8A90A0", "N": "8A5C2C"})
PICK = stamp(["..cCCCc...", ".c.....c..", "c...N...c.", "....N.....", "....N.....", "....N.....", "....N.....",
              "....N.....", "....N.....", "....N....."], {"C": "6FE0F0", "c": "2FA8C0", "N": "8A5C2C"})


def person(rows, hair, shirt, pants, boots="454552", hat=None):
    cols = dict(FACE, H=hair, T=shirt, P=pants, B=boots)
    if hat:
        rows = ["." + "A" * 12 + rows[0][13:], "A" * 15 + rows[1][15:]] + rows[2:]
        cols["A"] = hat
    return figure(rows, cols)


# =====================================================================================
# Deko unten
# =====================================================================================
def ingot():
    return stamp(["#############", "#YYYYYYYYWWY#", "#GGGGGGGGLLG#", "#ddddddddddd#", "#############"],
                 {"#": "0E0A02", "Y": "F2D040", "W": "FFF4C0", "G": "D8AE22", "L": "F0D890", "d": "9E7410"})


def spawner():
    img = new(16, 16)
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = rgb("0A0C12")
            elif (x - 1) % 5 == 0 or (y - 1) % 5 == 0:
                px[x, y] = rgb("5E7894") if (y - 1) % 5 == 0 and y < 15 else rgb("3C4E66")
            else:
                px[x, y] = rgb("12161F")
    # Mob im Kaefig mit roten Augen
    mob = [".mmmm.", "mmmmmm", "mRmmRm", "mmmmmm", ".mmmm.", ".m..m."]
    for y, row in enumerate(mob):
        for x, c in enumerate(row):
            if c != ".":
                px[5 + x, 5 + y] = rgb("E8284C") if c == "R" else rgb("34465E")
    return img


def chest():
    return stamp(["##############", "#LLLLLLLLLLLL#", "#LPPPPPPPPPPL#", "#LPPPPPPPPPPL#", "#LPPPPPPPPPPL#",
                  "#DDDDDGGDDDDD#", "#LPPPPYYPPPPL#", "#LPPPPPPPPPPL#", "#LppppppppppL#", "#LPPPPPPPPPPL#",
                  "#LLLLLLLLLLLL#", "##############"],
                 {"#": "1E1208", "L": "B8803E", "P": "9C6630", "p": "7E5024", "D": "3A2410", "G": "C89A2A",
                  "Y": "FFE070"})


def hay():
    rows = ["##############"]
    for y in range(10):
        if y in (2, 7):
            rows.append("#" + "B" * 12 + "#")
        else:
            rows.append("#" + "".join("y" if (x + y) % 3 == 0 else "Y" for x in range(12)) + "#")
    rows.append("##############")
    return stamp(rows, {"#": "2A1E08", "Y": "E2BC46", "y": "C49A30", "B": "7A5018"})


def emerald():
    return stamp(["...gg...", "..gLLg..", ".gLLMGg.", ".gLMMGd.", ".gLMMGd.", ".gMMGGd.", "..gGGd..", "...dd..."],
                 {"g": "3C8A1E", "L": "C8F078", "M": "96D246", "G": "5AAA28", "d": "245A12"})


def dead_player():
    rows = [
        "..................SSSSSSHH",
        "...........rrrrrrSSSSSSSSH",
        "BBDDDDDDDDRRRRRRRSSXSXSSSH",
        "BBDDDDDDDDRRRRRRRSSSXSSSSH",
        "BBDDDDDDDDRRRRRRRSSXSXSSSH",
        "BBDDDDDDDDRRRRRRRSSSSSSSSH",
        "BBDDDDDDDDRRRRRRRSSXSXSSSH",
        "BBddddddddRRRRRRRSSSXSSSSH",
        "...........rrrrrrSSXSXSSSH",
        "..................SSSSSSHH",
    ]
    return figure(rows, {"S": "C8946E", "H": "4A3020", "X": "2A1A12", "R": "C82838", "r": "8E1A26",
                         "D": "38383E", "d": "2A2A30", "B": "5E5E68"})


def deco(mode):
    w, h = INNER_W, DECO_H
    img = new(w, h)
    put = lambda sprite, x, bottom: img.alpha_composite(sprite, (x, bottom - sprite.height))
    if mode == "smp":
        put(spawner(), 0, h)
        put(ingot(), 1, h - 15)
        put(ingot(), 15, h)
        put(ingot(), 27, h)
        put(ingot(), 21, h - 4)
        put(dead_player(), w - 27, h)
    elif mode == "survival":
        put(chest(), 0, h)
        put(ingot(), 13, h)
        put(ingot(), 19, h - 4)
        put(hay(), w - 14, h)
        put(emerald(), w - 24, h)
    else:
        put(ingot(), 0, h)
        put(ingot(), 12, h)
        put(ingot(), 6, h - 4)
        put(emerald(), w - 8, h)
        put(emerald(), w - 17, h)
    img.load()[w - 1, h - 1] = (0, 0, 0, 1)   # Vorschub = volle Breite
    return img


# =====================================================================================
# Logo je Modus (volle Rahmenbreite; Zeile LOGO_TOP = Rahmenoberkante)
# =====================================================================================
def line_points(x0, y0, x1, y1):
    pts, dx, dy = [], abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        pts.append((x0, y0))
        if (x0, y0) == (x1, y1):
            return pts
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def arrow(tail, head):
    """Pfeil von der Feder zur Spitze, mit dunklem Rand."""
    layer = new(FRAME_W, LOGO_H)
    px = layer.load()
    pts = line_points(*tail, *head)
    for i, (x, y) in enumerate(pts):
        px[x, y] = rgb("9A6440")
    sx = 1 if head[0] > tail[0] else -1
    for (x, y) in pts[:4]:                              # Federn
        px[x - sx, y] = rgb("E8606A")
        px[x, y + 1] = rgb("B8343E")
    px[pts[0][0] - sx, pts[0][1] + 1] = rgb("FFB0B4")
    hx, hy = head                                       # Spitze
    for (x, y, c) in ((hx, hy, "F4F4F8"), (hx - sx, hy, "C8CCD4"), (hx, hy + 1, "C8CCD4"), (hx + sx, hy - 1, "FFFFFF")):
        px[x, y] = rgb(c)
    out = new(FRAME_W, LOGO_H)
    op = out.load()
    for y in range(LOGO_H):
        for x in range(FRAME_W):
            if px[x, y][3]:
                continue
            if any(0 <= x + dx < FRAME_W and 0 <= y + dy < LOGO_H and px[x + dx, y + dy][3]
                   for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                op[x, y] = rgb("1A0C04")
    out.alpha_composite(layer)
    return out


def ry(rel):
    return rel + LOGO_TOP


def logo(word, left, right, arrows=False):
    w, h = FRAME_W, LOGO_H
    img = new(w, h)
    lf, l_tool, l_tool_at = left
    rf, r_tool, r_tool_at = right
    lpos = (1, ry(0) - lf.height)
    rpos = (w - rf.width - 1, ry(0) - rf.height)
    if l_tool:
        img.alpha_composite(l_tool, (lpos[0] + l_tool_at[0], lpos[1] + l_tool_at[1]))
    if r_tool:
        img.alpha_composite(r_tool, (rpos[0] + r_tool_at[0], rpos[1] + r_tool_at[1]))
    img.alpha_composite(lf, lpos)
    img.alpha_composite(rf, rpos)
    if arrows:
        img.alpha_composite(arrow((30, ry(11)), (46, ry(0))))
        img.alpha_composite(arrow((w - 31, ry(11)), (w - 47, ry(0))))
    text = new(w, h)
    big = block_word("KUBY", BIG, GOLD_PAL)
    text.alpha_composite(big, ((w - big.width) // 2, ry(0) - big.height + 1))
    small = block_word(word, SMALL, SILVER_PAL)
    text.alpha_composite(small, ((w - small.width) // 2, ry(-2)))
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
    "claim": (["L.......", "PGGGGGg.", "PGLGGg..", "PGGGGGg.", "Pggggg..", "P.......", "MMMMMMMM", "dddddddd"],
              {"L": "D8DCE4", "P": "5A7088", "G": "5AD04A", "g": "2E8A28", "M": "6CB43C", "d": "6E4A24"}),
    "token": (["..pPPp..", ".pLPPPd.", "pPPWPPPd", "pPWWWPPd", "pPPWPPPd", "pPPPPPPd", ".pPPPPd.", "..dddd.."],
              {"p": "7A38B8", "P": "A85AE6", "L": "D8B0FF", "W": "FFF0A0", "d": "4A1E80"}),
    "level": (["...gg...", "..gLGg..", ".gLGGGg.", "gLGGGGGd", "...GG...", "...GGd..", "...GGd..", "...ddd.."],
              {"g": "3E9A1E", "L": "C8FF8C", "G": "6CDC3A", "d": "2A6A12"}),
    "job": (["..cCCCc.", ".c..N..c", "c...N...", "....N...", "....N...", "....N...", "....n...", "....n..."],
            {"C": "8CE8F4", "c": "2FA8C0", "N": "9C6834", "n": "6E4420"}),
    "arrow": (["........", "....W...", "....WW..", "WWWWWWW.", "wwwwwwww", "....ww..", "....w...", "........"],
              {"W": "F4F4F4", "w": "B8B8C0"}),
}
ICON_ORDER = ["coin", "gem", "xp", "trophy", "sword", "skull", "clock", "people", "rank", "team", "claim", "token",
              "level", "job", "arrow"]


def icon(name):
    rows, pal = ICONS[name]
    img = new(8, 8)
    img.alpha_composite(stamp(rows, pal), (0, 0))
    px = img.load()
    if not any(px[7, y][3] for y in range(8)):
        px[7, 7] = (0, 0, 0, 1)
    return img


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

    # Rahmen: Oberteil (6 px Rand + Titelzeile), je Zeile ein Mittelstueck, Unterteil (2 Zeilen + Rand)
    save(frame_piece(0, 15, True, False, A_TITLE), "frame_top", 15, 14, chr(0xE100))
    for k in range(1, MAX_K + 1):
        save(frame_piece(6 + 9 * k, 9, False, False, A_LINE), "frame_mid_%d" % k, 9, 8 - 9 * k, chr(0xE100 + k))
        save(frame_piece(6 + 9 * k, 24, False, True, A_LINE), "frame_bottom_%d" % k, 24, 8 - 9 * k, chr(0xE140 + k))

    fighter = lambda hair, shirt, pants, hat=None: person(FIGHTER, hair, shirt, pants, hat=hat)
    archer = lambda hair, shirt, pants: person(ARCHER, hair, shirt, pants)
    logos = {
        "smp": logo("SMP", (fighter("2C3060", "2CA8E0", "4A5078"), SWORD, (14, -3)),
                    (archer("E08A34", "4CC050", "4A4A56"), BOW, (-4, 5)), arrows=True),
        "survival": logo("SURVIVAL", (fighter("4A3020", "C83C3C", "3C5096", hat="E2C25C"), HOE, (15, -3)),
                         (archer("6E4420", "8A8A96", "5A3A22"), PICK, (-3, 2))),
        "hub": logo("HUB", (fighter("2C3060", "3C64DC", "4A5078"), None, (0, 0)),
                    (archer("E08A34", "9C48D0", "4A4A56"), None, (0, 0))),
    }
    for i, mode in enumerate(["smp", "survival", "hub"]):
        save(logos[mode], "logo_" + mode, LOGO_H, LOGO_H - 1, chr(0xE050 + i))
    for i, mode in enumerate(["smp", "survival", "hub"]):
        save(deco(mode), "deco_" + mode, DECO_H, DECO_H - 1, chr(0xE060 + i))
    for i, name in enumerate(ICON_ORDER):
        save(icon(name), "sb_" + name, 8, 7, chr(0xE010 + i))

    with open(os.path.join(font, "sb.json"), "w", encoding="utf-8") as f:
        json.dump({"providers": providers}, f, ensure_ascii=True, indent=1)
    print("ok scoreboard", len(providers) - 1, "Zeichen")
