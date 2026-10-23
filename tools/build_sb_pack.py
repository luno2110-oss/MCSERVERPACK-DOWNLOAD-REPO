"""Scoreboard-Grafik im Pack KubyUI (Schrift kubyplugins:sb), doppelte Aufloesung: 2 Texel je GUI-Pixel.

Masse nach der Referenz (Chaos SMP): Sidebar 116 px breit, Rahmen 125 px (6 px links vom Text bis zum
Bildschirmrand), 6 px Rand mit Goldverlauf von oben nach unten. Logo: grosse Goldbuchstaben 14 x 12 px,
darunter kleine Silberbuchstaben 9 x 8 px auf der oberen Rahmenkante, Figuren links und rechts.
Wird von build_ui_pack.py aufgerufen. Muss zu de.kuby.kubyscoreboard.SbUi passen.
"""
import json
import math
import os
import shutil

from PIL import Image

S = 2                  # Texel je GUI-Pixel
SB_W = 116             # Breite der Sidebar (= Titelbreite)
FRAME_W = SB_W + 9     # Rahmen von 6 px links vom Text bis 1 px hinter den Standard-Hintergrund
BORDER = 12            # Randbreite in Texeln (6 px)
NOMINAL_H = 156        # Rahmenhoehe bei 15 Zeilen, fuer den Goldverlauf
MAX_K = 15             # Vanilla zeigt hoechstens 15 Zeilen
INNER_W = FRAME_W - 12  # Innenbreite (113 px), hier liegt die Deko unten
A_TITLE = 21           # zusaetzliche Abdunklung innen (Titelzeile hat schon 40 % Hintergrund)
A_LINE = 55            # Zeilen haben nur 30 %, so wirkt die Flaeche gleichmaessig

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


def stamp(rows, palette, cell=S):
    """Pixel-Art aus Zeichen; jedes Zeichen ist cell x cell Texel gross."""
    w = max(len(r) for r in rows)
    img = new(w * cell, len(rows) * cell)
    px = img.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in palette:
                for sy in range(cell):
                    for sx in range(cell):
                        px[x * cell + sx, y * cell + sy] = palette[c]
    return img


def outlined(img, color=(18, 12, 6, 255), pad=1):
    """Fuegt eine 1-Texel-Kontur um alle deckenden Pixel hinzu (8 Nachbarn)."""
    out = new(img.width + 2 * pad, img.height + 2 * pad)
    out.alpha_composite(img, (pad, pad))
    src = out.copy().load()
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            if src[x, y][3] >= 128:
                continue
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < w and 0 <= yy < h and src[xx, yy][3] >= 128:
                        px[x, y] = color
                        break
                else:
                    continue
                break
    return out


def edge_shade(img, dark=0.78, light=1.12):
    """Rechte und untere Kanten dunkler, obere Kante heller (Licht von oben links)."""
    src = img.copy().load()
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            if src[x, y][3] < 128:
                continue
            below = y + 1 >= h or src[x, y + 1][3] < 128
            right = x + 1 >= w or src[x + 1, y][3] < 128
            above = y == 0 or src[x, y - 1][3] < 128
            if below or right:
                px[x, y] = shade(src[x, y], dark)
            elif above:
                px[x, y] = shade(src[x, y], light)
    return img


def segment(img, x0, y0, x1, y1, width, color_fn):
    """Zeichnet eine Strecke mit Breite; color_fn(s, r, length) gibt die Farbe (s entlang, r quer) oder None."""
    px = img.load()
    L = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / L, (y1 - y0) / L
    pad = width + 2
    for y in range(int(min(y0, y1) - pad), int(max(y0, y1) + pad) + 1):
        for x in range(int(min(x0, x1) - pad), int(max(x0, x1) + pad) + 1):
            if not (0 <= x < img.width and 0 <= y < img.height):
                continue
            cx, cy = x + 0.5 - x0, y + 0.5 - y0
            s = cx * ux + cy * uy
            r = -cx * uy + cy * ux
            if 0 <= s <= L and abs(r) <= width / 2:
                c = color_fn(s, r, L)
                if c:
                    px[x, y] = c


# =====================================================================================
# Rahmen
# =====================================================================================
GOLD_TOP, GOLD_BOT = rgb("C47A0E"), rgb("F6D640")
HL_TOP, HL_BOT = rgb("E2A030"), rgb("FFEE80")
OR_TOP, OR_BOT = rgb("824200"), rgb("BA8812")
BROWN = [rgb("683404"), rgb("5C2E08"), rgb("4E280A"), rgb("3E2008")]
DARK = rgb("28160A")
BLACK = rgb("120800")


def band(b, g):
    gold = mix(GOLD_TOP, GOLD_BOT, g)
    return [BLACK, mix(HL_TOP, HL_BOT, g), mix(gold, mix(HL_TOP, HL_BOT, g), 0.4), gold, gold,
            mix(OR_TOP, OR_BOT, g), BROWN[0], BROWN[1], BROWN[2], BROWN[3], DARK, BLACK][b]


def frame_piece(rel_top, h_gui, top, bottom, inner_alpha):
    """Ein Stueck Rahmen ab rel_top (GUI-Pixel unter der Rahmenoberkante). Unterkante: Profil gespiegelt."""
    w, h = FRAME_W * S, h_gui * S
    img = new(w, h)
    px = img.load()
    R = 5
    for y in range(h):
        g = (rel_top + (y + 0.5) / S) / NOMINAL_H
        for x in range(w):
            dx = min(x, w - 1 - x)
            dt = y if top else 10 ** 6
            db = (h - 1 - y) if bottom else 10 ** 6
            dy = min(dt, db)
            if dx < R and dy < R:
                d = R - math.hypot(R - (dx + 0.5), R - (dy + 0.5))
                if d < 0:
                    continue
            else:
                d = min(dx, dy)
            b = int(d)
            if b >= BORDER:
                px[x, y] = (0, 0, 0, inner_alpha)
                continue
            if db <= dx and db < dt:
                b = BORDER - 1 - b
            px[x, y] = band(b, g)
    return img


# =====================================================================================
# Logo-Schrift: grosse Goldbuchstaben (14 x 12 px) und kleine Silberbuchstaben (9 x 8 px)
# =====================================================================================
BIG = {
    "K": ["XXXXX....XXXXX", "XXXXX...XXXXX.", "XXXXX..XXXXX..", "XXXXX.XXXXX...", "XXXXXXXXXX....",
          "XXXXXXXXX.....", "XXXXXXXXX.....", "XXXXXXXXXX....", "XXXXX.XXXXX...", "XXXXX..XXXXX..",
          "XXXXX...XXXXX.", "XXXXX....XXXXX"],
    "U": ["XXXXX....XXXXX"] * 9 + ["XXXXXXXXXXXXXX", "XXXXXXXXXXXXXX", ".XXXXXXXXXXXX."],
    "B": ["XXXXXXXXXXXX..", "XXXXXXXXXXXXX.", "XXXXX....XXXXX", "XXXXX....XXXXX", "XXXXXXXXXXXXX.",
          "XXXXXXXXXXXX..", "XXXXXXXXXXXXX.", "XXXXX....XXXXX", "XXXXX....XXXXX", "XXXXXXXXXXXXXX",
          "XXXXXXXXXXXXX.", "XXXXXXXXXXXX.."],
    "Y": ["XXXXX....XXXXX", "XXXXX....XXXXX", "XXXXX....XXXXX", "XXXXXX..XXXXXX", ".XXXXXXXXXXXX.",
          "..XXXXXXXXXX..", "....XXXXXX....", "....XXXXXX....", "....XXXXXX....", "....XXXXXX....",
          "....XXXXXX....", "....XXXXXX...."],
}
SMALL = {
    "S": [".XXXXXXXX", "XXXXXXXXX", "XXX......", "XXXXXXXX.", ".XXXXXXXX", "......XXX", "XXXXXXXXX", "XXXXXXXX."],
    "M": ["XXX.....XXX", "XXXX...XXXX", "XXXXX.XXXXX", "XXXXXXXXXXX", "XXX.XXX.XXX", "XXX..X..XXX",
          "XXX.....XXX", "XXX.....XXX"],
    "P": ["XXXXXXXX.", "XXXXXXXXX", "XXX...XXX", "XXXXXXXXX", "XXXXXXXX.", "XXX......", "XXX......", "XXX......"],
    "U": ["XXX...XXX"] * 6 + ["XXXXXXXXX", ".XXXXXXX."],
    "R": ["XXXXXXXX.", "XXXXXXXXX", "XXX...XXX", "XXXXXXXXX", "XXXXXXXX.", "XXX.XXXX.", "XXX..XXXX", "XXX...XXX"],
    "V": ["XXX...XXX", "XXX...XXX", "XXX...XXX", "XXXX.XXXX", ".XXX.XXX.", ".XXXXXXX.", "..XXXXX..", "...XXX..."],
    "I": ["XXX"] * 8,
    "A": [".XXXXXXX.", "XXXXXXXXX", "XXX...XXX", "XXX...XXX", "XXXXXXXXX", "XXXXXXXXX", "XXX...XXX", "XXX...XXX"],
    "L": ["XXX....."] * 6 + ["XXXXXXXX", "XXXXXXXX"],
    "H": ["XXX...XXX"] * 3 + ["XXXXXXXXX"] * 2 + ["XXX...XXX"] * 3,
    "B": ["XXXXXXXX.", "XXXXXXXXX", "XXX...XXX", "XXXXXXXX.", "XXXXXXXXX", "XXX...XXX", "XXXXXXXXX", "XXXXXXXX."],
}

BIG_BODY = ([rgb("FFF8B4")] + [rgb("FFE85A")] * 3 + [rgb("FDD832")] * 6 + [rgb("FFEC78")] * 2
            + [rgb("F7C422")] * 6 + [rgb("ECA816")] * 4 + [rgb("D68C0E")] * 2)
BIG_EXTR = [rgb("BA680A"), rgb("964C04"), rgb("743602")]
SMALL_BODY = ([rgb("FFFFFF")] + [rgb("F0F0F4")] * 4 + [rgb("D6D8DE")] * 2 + [rgb("A8AAB4")] * 4
              + [rgb("BEC0C8")] * 2 + [rgb("E8EAEE")] * 3)
SMALL_EXTR = [rgb("6E7078"), rgb("4E5058")]


def word_mask(word, font, height):
    wc = sum(len(font[c][0]) for c in word) + (len(word) - 1)
    mask = set()
    x0 = 0
    for c in word:
        for y, row in enumerate(font[c]):
            for x, v in enumerate(row):
                if v == "X":
                    for sy in range(S):
                        for sx in range(S):
                            mask.add(((x0 + x) * S + sx, y * S + sy))
        x0 += len(font[c][0]) + 1
    # Aeussere Ecken um 1 Texel abrunden
    corners = set()
    for (x, y) in mask:
        up, down = (x, y - 1) in mask, (x, y + 1) in mask
        left, right = (x - 1, y) in mask, (x + 1, y) in mask
        if (not up and not left) or (not up and not right) or (not down and not left) or (not down and not right):
            corners.add((x, y))
    return mask - corners, wc * S, height * S


def block_word(word, font, height, body, extr, outline):
    """Buchstaben mit Verlauf, 3D-Kante nach unten und Kontur."""
    mask, w, h = word_mask(word, font, height)
    depth = len(extr)
    img = new(w + 2, h + depth + 2)
    px = img.load()
    solid = set(mask)
    ext = {}
    for (x, y) in mask:
        for d in range(1, depth + 1):
            if (x, y + d) not in mask and (x, y + d) not in ext:
                ext[(x, y + d)] = d
    solid |= set(ext)
    for (x, y) in solid:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                p = (x + dx, y + dy)
                if p not in solid:
                    px[p[0] + 1, p[1] + 1] = outline
    for (x, y), d in ext.items():
        px[x + 1, y + 1] = extr[d - 1]
    for (x, y) in mask:
        c = body[min(y, len(body) - 1)]
        if (x, y - 1) not in mask and y > 0:
            c = mix(c, (255, 255, 230), 0.45)
        px[x + 1, y + 1] = c
    return img


# =====================================================================================
# Figuren (Pixel-Art 1 px je Zeichen, mit feiner Kontur) und Werkzeuge
# =====================================================================================
FIGHTER = [
    "....HHHHHHHH....",
    "....HHHHHHHH..SS",
    "....HSSSSSSH..SS",
    "....SSSSSSSS..TT",
    "....SEKSSKES..TT",
    "....SSSssSSS..TT",
    "....SSMMMMSS..TT",
    "....SSSSSSSS.TTT",
    "...TTTTTTTTTTTT.",
    "..TTTTTTTTTTTT..",
    ".TTTTTTTTTTTT...",
    ".TT.TTTTTTTT....",
    ".TT.TTTTTTTT....",
    ".TT.TTTTTTTT....",
    ".SS.TTTTTTTT....",
    ".SS.tttttttt....",
    "....PPPPPPPP....",
    "....PPPPPPPP....",
    "....PPP..PPP....",
    "....PPP..PPP....",
    "....PPP..PPP....",
    "....PPP..PPP....",
    "....BBB..BBB....",
    "...BBBB..BBBB...",
]
ARCHER = [
    "....HHHHHHHH....",
    "...HHHHHHHHHH...",
    "...HHSSSSSSHH...",
    "...HSSSSSSSSH...",
    "...HSEKSSKESH...",
    "...HSSSssSSSH...",
    "...HSSSMMSSSH...",
    "...HSSSSSSSSH...",
    "...HTTTTTTTTH...",
    "SSTTTTTTTTTTTT..",
    "SSTTTTTTTTTTTTT.",
    "....TTTTTTTT.TT.",
    "....TTTTTTTT.TT.",
    "....TTTTTTTT.SS.",
    "....TTTTTTTT.SS.",
    "....tttttttt....",
    "....PPPPPPPP....",
    "....PPPPPPPP....",
    "....PPP..PPP....",
    "....PPP..PPP....",
    "....PPP..PPP....",
    "....PPP..PPP....",
    "....BBB..BBB....",
    "...BBBB..BBBB...",
]
SKIN = rgb("D49A74")


def figure(rows, hair, shirt, pants, boots=rgb("4A4A50"), hat=None):
    pal = {"H": hair, "S": SKIN, "s": shade(SKIN, 0.85), "E": rgb("FFFFFF"), "K": rgb("3C3C8C"),
           "M": rgb("8A4A34"), "T": shirt, "t": shade(shirt, 0.75), "P": pants, "B": boots}
    if hat:
        # Strohhut: Krone ueber dem Kopf, Krempe eine Zeile breiter als der Kopf
        rows = ["....AAAAAAAA" + rows[0][12:], "..aaaaaaaaaaaa" + rows[1][14:]] + rows[2:]
        pal.update({"A": hat, "a": shade(hat, 0.8)})
    img = stamp(rows, pal)
    return outlined(edge_shade(img))


def sword(layer, hx, hy, ang, length=22):
    ux, uy = math.cos(ang), math.sin(ang)
    segment(layer, hx - ux * 3, hy - uy * 3, hx + ux * 4, hy + uy * 4, 2.2, lambda s, r, L: rgb("6E4420") if r < 0 else rgb("8C5A2C"))
    gx, gy = hx + ux * 4, hy + uy * 4
    segment(layer, gx + uy * 4, gy - ux * 4, gx - uy * 4, gy + ux * 4, 2.2, lambda s, r, L: rgb("5A5A66"))

    def blade(s, r, L):
        half = 1.6 if s < L - 4 else 1.6 * (L - s) / 4
        if abs(r) > half:
            return None
        return rgb("F4F6FA") if r < -0.2 else (rgb("C8CCD6") if r < 0.8 else rgb("9AA0AE"))
    segment(layer, gx + ux * 1, gy + uy * 1, gx + ux * length, gy + uy * length, 3.4, blade)


def bow(layer, hx, hy, radius=17):
    cx = hx + 7
    top, bot = None, None
    px = layer.load()
    for i in range(0, 241):
        a = math.radians(125 + i * 110 / 240)
        x, y = cx + radius * math.cos(a), hy + radius * math.sin(a)
        for dx in (0, 1):
            for dy in (0, 1):
                xx, yy = int(x) + dx, int(y) + dy
                if 0 <= xx < layer.width and 0 <= yy < layer.height:
                    px[xx, yy] = rgb("9C6430") if dx == 0 else rgb("6E4420")
        if i == 0:
            top = (x + 1, y)
        bot = (x + 1, y)
    segment(layer, top[0], top[1], bot[0], bot[1], 1.0, lambda s, r, L: rgb("E8E8E8"))


def hoe(layer, hx, hy, ang, length=24):
    ux, uy = math.cos(ang), math.sin(ang)
    segment(layer, hx - ux * 3, hy - uy * 3, hx + ux * length, hy + uy * length, 2.2,
            lambda s, r, L: rgb("7A4E24") if r < 0 else rgb("9C6834"))
    ex, ey = hx + ux * length, hy + uy * length
    # Klinge quer zum Stiel, haengt nach unten
    segment(layer, ex - ux * 1, ey - uy * 1, ex - uy * 8 - ux * 1, ey + ux * 8 - uy * 1, 3.6,
            lambda s, r, L: rgb("E4E8F0") if r > 0.4 else rgb("9AA0AE"))


def pickaxe(layer, hx, hy, ang, length=22):
    ux, uy = math.cos(ang), math.sin(ang)
    segment(layer, hx - ux * 3, hy - uy * 3, hx + ux * length, hy + uy * length, 2.2,
            lambda s, r, L: rgb("7A4E24") if r < 0 else rgb("9C6834"))
    ex, ey = hx + ux * length, hy + uy * length
    px = layer.load()
    for i in range(-10, 11):
        t = i / 10
        x = ex + (-uy) * t * 9 - ux * (1 - t * t) * 3
        y = ey + ux * t * 9 - uy * (1 - t * t) * 3
        for dx in (0, 1):
            for dy in (0, 1):
                xx, yy = int(x) + dx, int(y) + dy
                if 0 <= xx < layer.width and 0 <= yy < layer.height:
                    px[xx, yy] = rgb("6FE0F0") if dy == 0 else rgb("2FA8C0")


def arrow(layer, tx, ty, hx, hy):
    """Pfeil von der Feder (tx, ty) zur Spitze (hx, hy)."""
    def col(s, r, L):
        if s < 9:
            lim = 0.7 + (9 - s) * 0.45
            if abs(r) <= lim:
                return rgb("F07070") if abs(r) > 1.2 else (rgb("D83838") if r < 0 else rgb("F4D8D8"))
            return None
        if s > L - 6:
            if abs(r) <= (L - s) * 0.55 + 0.4:
                return rgb("D8DCE4") if r < 0 else rgb("8E94A2")
            return None
        if abs(r) <= 1.0:
            return rgb("8C5E30") if r < 0 else rgb("5E3C1C")
        return None
    segment(layer, tx, ty, hx, hy, 6, col)


# =====================================================================================
# Deko unten (Spawner, Goldbarren, Truhe, Heuballen, Smaragde, liegender Spieler)
# =====================================================================================
def ingot():
    rows = ["..LLLLLLLLLLLLLLLL..", ".LLWWWWWWWWWWWWWWLL.", "LLLLLLLLLLLLLLLLLLLL",
            "GGGGGGGGGGGGGGGGGGGG", "GGGWWGGGGGGGGGGGGGGG", "GGGGGGGGGGGGGGGGGGGG",
            "GGGGGGGGGGGGGGGGGGGG", "DDDDDDDDDDDDDDDDDDDD"]
    return outlined(stamp(rows, {"L": rgb("FFE88C"), "W": rgb("FFF8D0"), "G": rgb("F6C428"), "D": rgb("B47808")}, 1),
                    rgb("5A3600"))


def spawner(size=26):
    img = new(size, size)
    px = img.load()
    for y in range(size):
        for x in range(size):
            bar_x, bar_y = x % 8 < 2, y % 8 < 2
            edge = x < 2 or y < 2 or x >= size - 2 or y >= size - 2
            if edge or bar_x or bar_y:
                light = (x % 8 == 0 and not edge) or (y % 8 == 0 and not edge) or x == 0 or y == 0
                px[x, y] = rgb("5C6C84") if light else rgb("34404E")
            else:
                px[x, y] = rgb("10141C")
    for (x, y, c) in [(12, 10, "FFB040"), (13, 10, "FFB040"), (11, 11, "FF8A20"), (12, 11, "FFE070"),
                      (13, 11, "FFE070"), (14, 11, "FF8A20"), (11, 12, "FF8A20"), (12, 12, "FFD040"),
                      (13, 12, "FFD040"), (14, 12, "FF8A20"), (12, 13, "E0601C"), (13, 13, "E0601C"),
                      (5, 4, "7A5AC8"), (20, 19, "7A5AC8"), (4, 20, "5A86C8"), (21, 4, "5A86C8")]:
        px[x, y] = rgb(c)
    return outlined(img, rgb("080A10"))


def chest():
    rows = ["WWWWWWWWWWWWWWWWWWWWWWWWWW", "WLLLLLLLLLLLLLLLLLLLLLLLLW", "WLPPPPPPPPPPPPPPPPPPPPPPLW",
            "WLPPPPPPPPPPPPPPPPPPPPPPLW", "WLPPPPPPPPPPPPPPPPPPPPPPLW", "WLPPPPPPPPPPPPPPPPPPPPPPLW",
            "WLPPPPPPPPPPPPPPPPPPPPPPLW", "DDDDDDDDDDDGGGGDDDDDDDDDDD", "DDDDDDDDDDDGYYGDDDDDDDDDDD",
            "WLPPPPPPPPPGYYGPPPPPPPPPLW", "WLPPPPPPPPPGGGGPPPPPPPPPLW", "WLPPPPPPPPPPPPPPPPPPPPPPLW",
            "WLppppppppppppppppppppppLW", "WLPPPPPPPPPPPPPPPPPPPPPPLW", "WLPPPPPPPPPPPPPPPPPPPPPPLW",
            "WLppppppppppppppppppppppLW", "WLPPPPPPPPPPPPPPPPPPPPPPLW", "WLPPPPPPPPPPPPPPPPPPPPPPLW",
            "WLLLLLLLLLLLLLLLLLLLLLLLLW", "WWWWWWWWWWWWWWWWWWWWWWWWWW"]
    pal = {"W": rgb("6A4220"), "L": rgb("B8803E"), "P": rgb("A06A30"), "p": rgb("825424"), "D": rgb("3A2410"),
           "G": rgb("C89A2A"), "Y": rgb("FFE070")}
    return outlined(stamp(rows, pal, 1), rgb("20140A"))


def hay():
    img = new(26, 22)
    px = img.load()
    for y in range(22):
        for x in range(26):
            band_ = 4 <= y <= 6 or 15 <= y <= 17
            if band_:
                px[x, y] = rgb("8A5A20") if y in (4, 15) else rgb("6E4418")
            else:
                px[x, y] = rgb("E6C048") if (x * 7 + y * 3) % 5 else rgb("C49A30")
                if y == 0:
                    px[x, y] = rgb("F6DC78")
    return outlined(img, rgb("3A280C"))


def emerald():
    rows = ["......OOOO......", ".....OLLGGO.....", "....OLLGGGGO....", "...OLLGGGGGGO...", "...OLGGGMGGGO...",
            "..OLGGGMMGGGDO..", "..OLGGGMMGGGDO..", "..OGGGGMMGGGDO..", "..OGGGGMMGGDDO..", "...OGGGMGGGDO...",
            "...OGGGGGGDDO...", "....OGGGGDDO....", ".....OGDDDO.....", "......OOOO......"]
    return stamp(rows, {"O": rgb("0E4A1E"), "L": rgb("B4FFC0"), "G": rgb("2CC05A"), "M": rgb("7CEA98"),
                        "D": rgb("168038")}, 1)


def dead_player():
    rows = [
        "..............HHSSSSHH",
        "BBPPPPPPRRRRRRHSSSSSSH",
        "BBPPPPPPRRRRRRSSSSSSSH",
        "BBPPPPPPRRRRRRSSMSSSSH",
        "BBPPPPPPRRRRRRSSMSSSSH",
        "BBPPPPPPRRRRRRSSSSSSSH",
        "BBppppppRRRRRRHSSSSSSH",
        "..............HHSSSSHH",
    ]
    pal = {"H": rgb("5A3A22"), "S": SKIN, "M": rgb("8A4A34"), "R": rgb("D82838"), "P": rgb("3C3E96"),
           "p": rgb("2C2E76"), "B": rgb("4A4A50")}
    img = stamp(rows, pal)
    px = img.load()
    for (ex, ey) in ((36, 4), (36, 10)):   # X-Augen, 3 x 3 Texel
        for d in (-1, 0, 1):
            px[ex + d, ey + d] = rgb("1A1010")
            px[ex + d, ey - d] = rgb("1A1010")
    return outlined(edge_shade(img))


def place_bottom(canvas, img, x, bottom):
    canvas.alpha_composite(img, (x, bottom - img.height))


def deco(mode):
    w, h = INNER_W * S, 17 * S
    img = new(w, h)
    if mode == "smp":
        sp = spawner()
        place_bottom(img, sp, 0, h)
        place_bottom(img, ingot(), 2, h - sp.height + 2)
        place_bottom(img, ingot(), sp.width - 1, h)
        place_bottom(img, ingot(), sp.width + 19, h)
        place_bottom(img, ingot(), sp.width + 9, h - 8)
        dp = dead_player()
        place_bottom(img, dp, w - dp.width, h)
    elif mode == "survival":
        ch = chest()
        place_bottom(img, ch, 0, h)
        place_bottom(img, ingot(), ch.width - 1, h)
        place_bottom(img, ingot(), ch.width + 8, h - 8)
        hb = hay()
        place_bottom(img, hb, w - hb.width, h)
        place_bottom(img, outlined(emerald(), rgb("0E3A18")), w - hb.width - 18, h)
    else:
        place_bottom(img, ingot(), 0, h)
        place_bottom(img, ingot(), 21, h)
        place_bottom(img, ingot(), 10, h - 8)
        e = outlined(emerald(), rgb("0E3A18"))
        place_bottom(img, e, w - e.width, h)
        place_bottom(img, e, w - 2 * e.width + 3, h)
    img.load()[w - 1, h - 1] = (0, 0, 0, 1)   # Vorschub = volle Breite
    return img


# =====================================================================================
# Logo je Modus (volle Rahmenbreite, 43 px hoch, Zeile 0 = 28 px ueber der Rahmenoberkante).
# Nicht hoeher: bei 1080p und GUI-Groesse 4 wuerde der Bildschirmrand sonst die Spitze abschneiden.
# =====================================================================================
LOGO_H = 43
LOGO_TOP = 28


def ry(rel):
    """GUI-Pixel relativ zur Rahmenoberkante -> Texelzeile im Logo."""
    return round((rel + LOGO_TOP) * S)


def logo(word, figures, arrows=False):
    w, h = FRAME_W * S, LOGO_H * S
    img = new(w, h)
    # Figuren links und rechts, stehen auf der Rahmenoberkante
    lf, rf, tools = figures
    lpos = (2, ry(0) - lf.height + 1)
    rpos = (w - rf.width - 2, ry(0) - rf.height + 1)
    tl = new(w, h)
    tools(tl, lpos, rpos)
    img.alpha_composite(_tool_outline(tl))   # Werkzeuge hinter den Haenden
    img.alpha_composite(lf, lpos)
    img.alpha_composite(rf, rpos)
    if arrows:
        # Gekreuzte Pfeile hinter dem kleinen Schriftzug (wie in der Referenz)
        ar = new(w, h)
        arrow(ar, 60, ry(12), 114, ry(-1))
        arrow(ar, w - 60, ry(12), w - 114, ry(-1))
        img.alpha_composite(_tool_outline(ar))
    big = block_word("KUBY", BIG, 12, BIG_BODY, BIG_EXTR, rgb("180C00"))
    img.alpha_composite(big, ((w - big.width) // 2, ry(0) - big.height))
    small = block_word(word, SMALL, 8, SMALL_BODY, SMALL_EXTR, rgb("101014"))
    img.alpha_composite(small, ((w - small.width) // 2, ry(-2)))
    img.load()[w - 1, h - 1] = (0, 0, 0, 1)
    return img


def _tool_outline(layer):
    out = outlined(layer, rgb("140C06"))
    return out.crop((1, 1, layer.width + 1, layer.height + 1))


def tools_smp(layer, lpos, rpos):
    sword(layer, lpos[0] + 29, lpos[1] + 4, math.radians(-16), length=20)
    bow(layer, rpos[0] + 1, rpos[1] + 20)


def tools_survival(layer, lpos, rpos):
    hoe(layer, lpos[0] + 29, lpos[1] + 4, math.radians(-18), length=20)
    pickaxe(layer, rpos[0] + 2, rpos[1] + 19, math.radians(-150), length=18)


def tools_none(layer, lpos, rpos):
    pass


# =====================================================================================
# Zeilen-Icons 16 x 16 Texel (8 px), Vorschub immer 9
# =====================================================================================
ICON_ROWS = {
    "coin": (["................", "................", "......OOOOO.....", "....OOLLLLLOO...", "...OLLYYYYYLLO..",
              "...OYYYYYYYYYO..", "...ODYYYYYYYDO..", "...OODDDDDDDOO..", "...OLLLLLLLLLO..", "...ODYYYYYYYDO..",
              "...OODDDDDDDOO..", "...OLLLLLLLLLO..", "...ODYYYYYYYDO..", "...OODDDDDDDOO..", "....OOOOOOOOO...",
              "................"],
             {"O": "5A3600", "L": "FFF0A0", "Y": "F6C428", "D": "B47808"}),
    "gem": (["................", "......OOOO......", ".....OLLGGO.....", "....OLLGGGGO....", "...OLLGGGGGGO...",
             "...OLGGGMGGGO...", "..OLGGGMMGGGDO..", "..OLGGGMMGGGDO..", "..OGGGGMMGGGDO..", "..OGGGGMMGGDDO..",
             "...OGGGMGGGDO...", "...OGGGGGGDDO...", "....OGGGGDDO....", ".....OGDDDO.....", "......OOOO......",
             "................"],
            {"O": "0E4A1E", "L": "B4FFC0", "G": "2CC05A", "M": "7CEA98", "D": "168038"}),
    "xp": (["................", ".....OOOOO......", "....OLLYYYO.....", "....OLYYYYO.....", "....OYYYYDO.....",
            "....OYYYDDO.....", ".....OOOOO......", "..OOOOO..OOOOO..", ".OLLGGGOOLLAAAO.", ".OLGGGGOOLAAAAO.",
            ".OGGGGDOOAAAADO.", ".OGGGDDOOAAADDO.", "..OOOOO..OOOOO..", "................", "................",
            "................"],
           {"O": "1E4A0A", "L": "F4FFB0", "Y": "E6F23A", "D": "6E9A10", "G": "4CD830", "A": "A6EA2A"}),
    "trophy": (["................", "...OOOOOOOOOO...", "OOOLLYYYYYYDDOOO", "OYOLYYYYYYYYDOYO", "OYOLYYYYYYYYDOYO",
                ".OYOLYYYYYYDOYO.", "..OOOYYYYYYOOO..", "....OYYYYYDO....", ".....OYYYDO.....", "......OYDO......",
                "......OYDO......", ".....OYYYDO.....", "....OOOOOOOO....", "....OLYYYYDO....", "....OOOOOOOO....",
                "................"],
               {"O": "5A3600", "L": "FFF0A0", "Y": "F6C428", "D": "B47808"}),
    "sword": (["............OOO.", "...........OWWO.", "..........OWWGO.", ".........OWWGO..", "........OWWGO...",
               ".......OWWGO....", "......OWWGO.....", "..OO.OWWGO......", "..OHOWWGO.......", "...OHHGO........",
               "...OOHHO........", "..OBOOHHO.......", ".OBO..OOO.......", "OBO.............", "OO..............",
               "................"],
              {"O": "1E1E26", "W": "F4F6FA", "G": "A0A6B4", "H": "5A5A66", "B": "8C5A2C"}),
    "skull": (["................", "....OOOOOOOO....", "...OWWWWWWWWO...", "..OWWWWWWWWWSO..", "..OWWWWWWWWWSO..",
               "..OWKKKWWKKKSO..", "..OWKKKWWKKKSO..", "..OWKKKWWKKKSO..", "..OWWWWKKWWWSO..", "...OWWWKKWWSO...",
               "...OOWWWWWSOO...", "....OWKWKWKO....", "....OWWWWWSO....", ".....OOOOOO.....", "................",
               "................"],
              {"O": "2A2620", "W": "EEE8D8", "S": "B8B09C", "K": "201A18"}),
    "clock": (["................", "..OOOOOOOOOOOO..", "..OBBBBBBBBBBO..", "..OOOOOOOOOOOO..", "...OGCCCCCCGO...",
               "....OGCCCCGO....", ".....OGCCGO.....", "......OGCO......", "......OCGO......", ".....OGGCGO.....",
               "....OGGCCCGO....", "...OGCCCCCCGO...", "..OOOOOOOOOOOO..", "..OBBBBBBBBBBO..", "..OOOOOOOOOOOO..",
               "................"],
              {"O": "3A2410", "B": "A06A30", "G": "D8F4FF", "C": "3CC8E6"}),
    "people": (["................", "..OOOO....OOOO..", ".OHHHHO..OHHHHO.", ".OSSSSO..OSSSSO.", ".OSKSKO..OSKSKO.",
                ".OSSSSO..OSSSSO.", "..OOOO....OOOO..", ".OTTTTO..OUUUUO.", "OTTTTTTOOUUUUUUO", "OTTTTTTOOUUUUUUO",
                "OSTTTTSOOSUUUUSO", ".OTTTTO..OUUUUO.", ".OPPPPO..OPPPPO.", ".OPOOPO..OPOOPO.", ".OOOOOO..OOOOOO.",
                "................"],
               {"O": "1E1A16", "H": "5A3A22", "S": "D49A74", "K": "2A2A50", "T": "2CA8A8", "U": "4CB04C", "P": "3C3E96"}),
    "rank": (["................", "................", ".O.....OO.....O.", "OLO...OLLO...OLO", "OYO...OYYO...OYO",
              "OYYO.OYYYYO.OYYO", "OYYYOYYYYYYOYYYO", "OYYYYYYYYYYYYYYO", "OYYYYYYYYYYYYYYO", "OYRRYYYBBYYYGGYO",
              "OYRRYYYBBYYYGGYO", "OYYYYYYYYYYYYYYO", "ODDDDDDDDDDDDDDO", "OOOOOOOOOOOOOOOO", "................",
              "................"],
             {"O": "5A3000", "L": "FFF0A0", "Y": "F6B828", "D": "B46E08", "R": "E0283C", "B": "3C8CE0", "G": "2CC05A"}),
    "team": (["..OO............", ".OKKO...........", ".OKKOOOOOOOOOO..", "..OWORRRRRRRRRO.", "..OWORLLRRRRRRRO",
              "..OWORRRRRRRRRRO", "..OWORRRRRRRRRO.", "..OWORRRRRRRRDO.", "..OWORRRRRRDDO..", "..OWOODDDDDDO...",
              "..OWO.OOOOOO....", "..OWO...........", "..OWO...........", "..OWO...........", "..OWO...........",
              "..OOO..........."],
             {"O": "1E1A16", "K": "F6C428", "W": "A8A8B4", "R": "D82838", "L": "F06070", "D": "8A1420"}),
    "claim": (["..OO............", ".OKKO...........", ".OKKOOOOOOOO....", "..OWOGGGGGGGO...", "..OWOGLLGGGGGO..",
               "..OWOGGGGGGGGO..", "..OWOGGGGGGDO...", "..OWOODDDDDO....", "..OWO.OOOOO.....", "..OWO...........",
               "..OWO...........", "OOOOOOOOOOOOOOOO", "OLLLLLLLLLLLLLLO", "ODDDDDDDDDDDDDDO", "OOOOOOOOOOOOOOOO",
               "................"],
              {"O": "1E1A16", "K": "F6C428", "W": "A8A8B4", "G": "4CD860", "L": "B4FFC0", "D": "1E8A34"}),
    "token": (["................", ".....OOOOOO.....", "...OOLLPPPPOO...", "..OLLPPPPPPPPO..", "..OLPPPWWPPPPO..",
               ".OLPPPWWWWPPPDO.", ".OPPWWWWWWWWPDO.", ".OPPPWWWWWWPPDO.", ".OPPPPWWWWPPPDO.", ".OPPPWWPPWWPPDO.",
               "..OPPWPPPPWPDO..", "..OPPPPPPPPDDO..", "...OODDDDDDOO...", ".....OOOOOO.....", "................",
               "................"],
              {"O": "2A0E46", "L": "D8B0FF", "P": "9C4CE0", "W": "FFF0A0", "D": "5A2490"}),
    "level": (["................", ".......OO.......", "......OLLO......", ".....OLGGGO.....", "....OLGGGGGO....",
               "...OLGGGGGGGO...", "..OLGGGGGGGGGO..", ".OOOOOGGGGOOOOO.", ".....OGGGGO.....", ".....OGGGDO.....",
               ".....OGGGDO.....", ".....OGGGDO.....", ".....OGGDDO.....", ".....OOOOOO.....", "................",
               "................"],
              {"O": "1E4A0A", "L": "D8FFB0", "G": "6CDC3A", "D": "3E9A1E"}),
    "job": (["................", "....OOOOOOO.....", "..OOCCCCCCCOO...", ".OCCLLOOOOCCCO..", "OCLOO.OBO..OOCO.",
             "OCO...OBO....OO.", "OO....OBO.......", "......OBO.......", "......OBO.......", "......OBO.......",
             "......OBO.......", "......OBO.......", "......OBO.......", "......ODO.......", "......OOO.......",
             "................"],
            {"O": "1A1E22", "C": "6FE0F0", "L": "D4FCFF", "B": "9C6834", "D": "6E4420"}),
    "arrow": (["................", "................", "................", "........OO......", "........OWO.....",
               "OOOOOOOOOWWO....", "OWWWWWWWWWWWO...", "OWWWWWWWWWWWWO..", "OWWWWWWWWWWWO...", "OOOOOOOOOWWO....",
               "........OWO.....", "........OO......", "................", "................", "................",
               "................"],
              {"O": "2A2A2A", "W": "F4F4F4"}),
}
ICON_ORDER = ["coin", "gem", "xp", "trophy", "sword", "skull", "clock", "people", "rank", "team", "claim", "token",
              "level", "job", "arrow"]


def icon(name):
    rows, pal = ICON_ROWS[name]
    img = stamp(rows, {k: rgb(v) for k, v in pal.items()}, 1)
    full = new(16, 16)
    full.alpha_composite(img, (0, 0))
    px = full.load()
    if not any(px[15, y][3] for y in range(16)):
        px[15, 15] = (0, 0, 0, 1)
    return full


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

    fighter = lambda hair, shirt, pants, hat=None: figure(FIGHTER, rgb(hair), rgb(shirt), rgb(pants), hat=hat)
    archer = lambda hair, shirt, pants: figure(ARCHER, rgb(hair), rgb(shirt), rgb(pants))
    logos = {
        "smp": logo("SMP", (fighter("3B2616", "00A8A8", "3C3896"), archer("D2702A", "3CAA46", "6E4B2D"), tools_smp), arrows=True),
        "survival": logo("SURVIVAL", (fighter("3B2616", "C03232", "3C5096", hat=rgb("DCBE5A")),
                                      archer("6E4420", "7A7A86", "5A3A22"), tools_survival)),
        "hub": logo("HUB", (fighter("3B2616", "2E5BD8", "3C3896"), archer("D2702A", "8A3CC8", "6E4B2D"), tools_none)),
    }
    for i, mode in enumerate(["smp", "survival", "hub"]):
        save(logos[mode], "logo_" + mode, LOGO_H, LOGO_H - 1, chr(0xE050 + i))
    for i, mode in enumerate(["smp", "survival", "hub"]):
        save(deco(mode), "deco_" + mode, 17, 16, chr(0xE060 + i))
    for i, name in enumerate(ICON_ORDER):
        save(icon(name), "sb_" + name, 8, 7, chr(0xE010 + i))

    with open(os.path.join(font, "sb.json"), "w", encoding="utf-8") as f:
        json.dump({"providers": providers}, f, ensure_ascii=True, indent=1)
    print("ok scoreboard", len(providers) - 1, "Zeichen")
