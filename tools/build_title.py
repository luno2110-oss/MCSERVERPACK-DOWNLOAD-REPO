"""Große Titelschrift (Casino-Dialoge, Hologramme): dicke Pixel-Großbuchstaben mit dunkler Kontur und Verlauf.

Jede Glyphe ist ein Raster 6 x 8 (M, W: 7) mit 2 px breiten Strichen. Gezeichnet wird es doppelt so groß und um
1 px nach rechts unten verdickt: Striche 5 px, Lücken 1 px (die füllt die Kontur), Großbuchstaben 13 x 17 px.
Umlaut-Punkte liegen über der Versalhöhe. Zwei Fassungen:
  gold   fertig gefärbt (hellgelb oben, gelb, orange unten, Glanz an Oberkanten), Textfarbe weiß
  white  hell/grau, wird mit der Textfarbe eingefärbt (z. B. Verlauf über mehrere Buchstaben)

Zelle: 18 x 23 px, Zeile 0 = Kontur über den Umlaut-Punkten, Zeile 5 = Oberkante der Großbuchstaben.
"""
from PIL import Image

T = (0, 0, 0, 0)
CELL_W, CELL_H = 18, 23
TOP = 5                   # Zeile der Versal-Oberkante in der Zelle
CAP_H = 17

G = {
    "A": [".XXXX.", "XXXXXX", "XX..XX", "XX..XX", "XXXXXX", "XXXXXX", "XX..XX", "XX..XX"],
    "B": ["XXXXX.", "XXXXXX", "XX..XX", "XXXXX.", "XXXXXX", "XX..XX", "XXXXXX", "XXXXX."],
    "C": [".XXXXX", "XXXXXX", "XX....", "XX....", "XX....", "XX....", "XXXXXX", ".XXXXX"],
    "D": ["XXXXX.", "XXXXXX", "XX..XX", "XX..XX", "XX..XX", "XX..XX", "XXXXXX", "XXXXX."],
    "E": ["XXXXXX", "XXXXXX", "XX....", "XXXX..", "XXXX..", "XX....", "XXXXXX", "XXXXXX"],
    "F": ["XXXXXX", "XXXXXX", "XX....", "XXXX..", "XXXX..", "XX....", "XX....", "XX...."],
    "G": [".XXXXX", "XXXXXX", "XX....", "XX.XXX", "XX.XXX", "XX..XX", "XXXXXX", ".XXXXX"],
    "H": ["XX..XX", "XX..XX", "XX..XX", "XXXXXX", "XXXXXX", "XX..XX", "XX..XX", "XX..XX"],
    "I": ["XX"] * 8,
    "J": ["....XX", "....XX", "....XX", "....XX", "....XX", "XX..XX", "XXXXXX", ".XXXX."],
    "K": ["XX..XX", "XX.XXX", "XXXXX.", "XXXX..", "XXXX..", "XXXXX.", "XX.XXX", "XX..XX"],
    "L": ["XX....", "XX....", "XX....", "XX....", "XX....", "XX....", "XXXXXX", "XXXXXX"],
    "M": ["XX...XX", "XXX.XXX", "XXXXXXX", "XX.X.XX", "XX...XX", "XX...XX", "XX...XX", "XX...XX"],
    "N": ["XX..XX", "XXX.XX", "XXXXXX", "XXXXXX", "XX.XXX", "XX..XX", "XX..XX", "XX..XX"],
    "O": [".XXXX.", "XXXXXX", "XX..XX", "XX..XX", "XX..XX", "XX..XX", "XXXXXX", ".XXXX."],
    "P": ["XXXXX.", "XXXXXX", "XX..XX", "XX..XX", "XXXXXX", "XXXXX.", "XX....", "XX...."],
    "Q": [".XXXX.", "XXXXXX", "XX..XX", "XX..XX", "XX..XX", "XX.XXX", "XXXXX.", ".XX.XX"],
    "R": ["XXXXX.", "XXXXXX", "XX..XX", "XX..XX", "XXXXX.", "XXXXXX", "XX..XX", "XX..XX"],
    "S": [".XXXXX", "XXXXXX", "XX....", "XXXXX.", ".XXXXX", "....XX", "XXXXXX", "XXXXX."],
    "T": ["XXXXXX", "XXXXXX", "..XX..", "..XX..", "..XX..", "..XX..", "..XX..", "..XX.."],
    "U": ["XX..XX", "XX..XX", "XX..XX", "XX..XX", "XX..XX", "XX..XX", "XXXXXX", ".XXXX."],
    "V": ["XX..XX", "XX..XX", "XX..XX", "XX..XX", "XX..XX", "XXXXXX", ".XXXX.", "..XX.."],
    "W": ["XX...XX", "XX...XX", "XX...XX", "XX.X.XX", "XXXXXXX", "XXXXXXX", "XXX.XXX", "XX...XX"],
    "X": ["XX..XX", "XX..XX", "XXXXXX", ".XXXX.", ".XXXX.", "XXXXXX", "XX..XX", "XX..XX"],
    "Y": ["XX..XX", "XX..XX", "XX..XX", "XXXXXX", ".XXXX.", "..XX..", "..XX..", "..XX.."],
    "Z": ["XXXXXX", "XXXXXX", "...XXX", "..XXX.", ".XXX..", "XXX...", "XXXXXX", "XXXXXX"],
    "0": [".XXXX.", "XXXXXX", "XX..XX", "XX..XX", "XX..XX", "XX..XX", "XXXXXX", ".XXXX."],
    "1": ["..XX..", ".XXX..", "XXXX..", "..XX..", "..XX..", "..XX..", "XXXXXX", "XXXXXX"],
    "2": [".XXXX.", "XXXXXX", "XX..XX", "...XXX", ".XXXX.", "XXX...", "XXXXXX", "XXXXXX"],
    "3": ["XXXXX.", "XXXXXX", "....XX", ".XXXX.", ".XXXXX", "....XX", "XXXXXX", "XXXXX."],
    "4": ["...XXX", "..XXXX", ".XX.XX", "XX..XX", "XXXXXX", "XXXXXX", "....XX", "....XX"],
    "5": ["XXXXXX", "XXXXXX", "XX....", "XXXXX.", "XXXXXX", "....XX", "XXXXXX", "XXXXX."],
    "6": [".XXXX.", "XXXXX.", "XX....", "XXXXX.", "XXXXXX", "XX..XX", "XXXXXX", ".XXXX."],
    "7": ["XXXXXX", "XXXXXX", "....XX", "...XXX", "..XXX.", "..XX..", "..XX..", "..XX.."],
    "8": [".XXXX.", "XXXXXX", "XX..XX", ".XXXX.", "XXXXXX", "XX..XX", "XXXXXX", ".XXXX."],
    "9": [".XXXX.", "XXXXXX", "XX..XX", "XXXXXX", ".XXXXX", "....XX", ".XXXXX", ".XXXX."],
    "!": ["XX", "XX", "XX", "XX", "XX", "..", "XX", "XX"],
    "?": [".XXXX.", "XXXXXX", "XX..XX", "...XXX", "..XXX.", "......", "..XX..", "..XX.."],
    ".": ["..", "..", "..", "..", "..", "..", "XX", "XX"],
    ",": ["..", "..", "..", "..", "..", "XX", "XX", "X."],
    ":": ["..", "XX", "XX", "..", "..", "XX", "XX", ".."],
    "-": ["....", "....", "....", "XXXX", "XXXX", "....", "....", "...."],
    "+": ["......", "..XX..", "..XX..", "XXXXXX", "XXXXXX", "..XX..", "..XX..", "......"],
    "/": ["....XX", "...XXX", "...XX.", "..XX..", "..XX..", ".XX...", "XXX...", "XX...."],
    "%": ["XX..XX", "XX.XXX", "...XX.", "..XX..", "..XX..", ".XX...", "XXX.XX", "XX..XX"],
    "'": ["XX", "XX", "X.", "..", "..", "..", "..", ".."],
    "&": [".XXX..", "XX.XX.", "XX.XX.", ".XXX..", "XXXX.X", "XX.XXX", "XXXXX.", ".XXXXX"],
    "#": [".X..X.", "XXXXXX", "XXXXXX", ".X..X.", ".X..X.", "XXXXXX", "XXXXXX", ".X..X."],
    "$": ["..XX..", ".XXXXX", "XXX...", "XXXXX.", ".XXXXX", "...XXX", "XXXXX.", "..XX.."],
    "✦": ["...X...", "...X...", "..XXX..", "XXXXXXX", "XXXXXXX", "..XXX..", "...X...", "...X..."],
    "★": ["...X...", "..XXX..", "XXXXXXX", ".XXXXX.", "..XXX..", ".XX.XX.", ".X...X.", "......."],
}
# Umlaute: Grundbuchstabe + Punkte (Zeile -2 im Raster, über der Versalhöhe)
UMLAUT = {"Ä": "A", "Ö": "O", "Ü": "U"}
SPACE_ADV = 8

ORDER = list(G) + list(UMLAUT) + [" "]

OUTLINE = (14, 3, 0, 255)
GOLD = {
    "hi1": (0xF8, 0xF5, 0x94), "hi2": (0xFF, 0xF2, 0x8E),            # Glanz (erste, zweite Zeile unter einer Kante)
    "hi1o": (0xF6, 0xE4, 0x5A), "hi2o": (0xFF, 0xDA, 0x4E),          # Glanz im orangen Teil
    "rows": [(0xFA, 0xD2, 0x0B)] * 11 + [(0xFA, 0xAE, 0x02)] * 4 + [(0xF9, 0x93, 0x00)] * 2,
}
WHITE = {
    "hi1": (0xFF, 0xFF, 0xFF), "hi2": (0xFA, 0xFA, 0xFA),
    "hi1o": (0xF0, 0xF0, 0xF0), "hi2o": (0xE6, 0xE6, 0xE6),
    "rows": [(0xEC, 0xEC, 0xEC)] * 11 + [(0xCC, 0xCC, 0xCC)] * 4 + [(0xB0, 0xB0, 0xB0)] * 2,
}


def base_rows(ch):
    """Raster mit Zeile -2 (Punkte) als Liste von 10 Zeilen."""
    if ch in UMLAUT:
        g = G[UMLAUT[ch]]
        w = len(g[0])
        dots = "." * w
        dots = dots[:1] + "X" + dots[2:w - 2] + "X" + dots[w - 1:] if w >= 5 else dots
        return [dots, "." * w] + g
    if ch == " ":
        return None
    g = G[ch]
    w = len(g[0])
    return ["." * w, "." * w] + g


def fill_mask(rows):
    """Doppelt groß und 1 px nach rechts unten verdickt. Koordinaten relativ zur Zelle (ohne Kontur-Rand)."""
    w = len(rows[0])
    filled = set()
    for by, row in enumerate(rows):
        for bx, c in enumerate(row):
            if c != "X":
                continue
            for dy in range(3):
                for dx in range(3):
                    filled.add((2 * bx + dx, 2 * by + dy))
    return filled, 2 * w + 1


def render(ch, pal):
    img = Image.new("RGBA", (CELL_W, CELL_H), T)
    rows = base_rows(ch)
    if rows is None:
        return img
    filled, _ = fill_mask(rows)
    # Zelle: x + 1 (Kontur links), y: Rasterzeile -2 beginnt bei Zellzeile 1  -> Versal-Oberkante TOP = 5
    ox, oy = 1, 1
    px = img.load()
    for (x, y) in filled:
        cx, cy = x + ox, y + oy
        cap_row = cy - TOP
        above1 = (x, y - 1) in filled
        above2 = (x, y - 2) in filled
        orange = cap_row >= 11
        if not above1:
            col = pal["hi1o" if orange else "hi1"]
        elif not above2:
            col = pal["hi2o" if orange else "hi2"]
        else:
            col = pal["rows"][max(0, min(CAP_H - 1, cap_row))]
        px[cx, cy] = col + (255,)
    for (x, y) in filled:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in filled:
                    qx, qy = q[0] + ox, q[1] + oy
                    if 0 <= qx < CELL_W and 0 <= qy < CELL_H:
                        px[qx, qy] = OUTLINE
    return img


def advance(img, ch):
    if ch == " ":
        return SPACE_ADV
    bbox = img.getchannel("A").getbbox()
    return (bbox[2] if bbox else 0) + 1


def sheet(pal, cols=16):
    """Bild mit allen Glyphen in ORDER (Zellen 18 x 23) und Vorschübe."""
    rows = (len(ORDER) + cols - 1) // cols
    img = Image.new("RGBA", (CELL_W * cols, CELL_H * rows), T)
    adv = []
    for i, ch in enumerate(ORDER):
        g = render(ch, pal)
        img.alpha_composite(g, ((i % cols) * CELL_W, (i // cols) * CELL_H))
        adv.append(advance(g, ch))
    return img, adv, cols, rows


def space_advances(first):
    """Leerzeichen der Titelschrift als Space-Provider-Eintrag."""
    return {chr(first + ORDER.index(" ")): SPACE_ADV}


def chars(first, cols, rows):
    out = []
    for r in range(rows):
        line = ""
        for c in range(cols):
            i = r * cols + c
            line += chr(first + i) if i < len(ORDER) and ORDER[i] != " " else "\u0000"
        out.append(line)
    return out
