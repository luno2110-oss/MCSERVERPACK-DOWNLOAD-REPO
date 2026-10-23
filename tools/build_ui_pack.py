"""Erzeugt das Resource Pack "KubyUI" (Schriftart kuby:ui) mit Rahmen, Buttons und Icons fuer die Casino-Hilfe.

Aufruf: python3 tools/build_ui_pack.py <ausgabe-ordner>
Die Zeichen und Masse muessen zu de.kuby.kubyplugins.ui.Ui passen.
"""
import json
import math
import os
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "resourcepack/KubyUI"
TEX = os.path.join(OUT, "assets/kuby/textures/ui")
FONT = os.path.join(OUT, "assets/kuby/font")
os.makedirs(TEX, exist_ok=True)
os.makedirs(FONT, exist_ok=True)

PANEL_W = 300
MAX_ROWS = 63


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


T = (0, 0, 0, 0)

# Farben (an der Referenz orientiert)
P_BORDER = hexc("C93048")
P_FILL = hexc("2A0B11", 236)
P_SHINE = hexc("3A121A", 236)
B_BORDER = hexc("9A9AA6")
B_FILL = hexc("18181E")
B_SHINE = hexc("26262E")
R_BORDER = hexc("FF7085")
R_FILL = hexc("B8233A")
R_SHINE = hexc("D23A52")
GOLD = hexc("F2C230")
GOLD_D = hexc("A8761A")
GOLD_DD = hexc("6B4A0E")
GOLD_L = hexc("FFE07A")
WHITE = hexc("F2F2F2")
GREY = hexc("8E8E9A")
DARK = hexc("1B1B22")
RED = hexc("D8283E")
RED_D = hexc("7A1020")
GREEN = hexc("2FA84A")
BLACK = hexc("16161A")


def save(img, name):
    img.save(os.path.join(TEX, name))


# ---------- Kasten-Streifen (je 9 px hoch, eine Textzeile) ----------
def strip(kind):
    img = Image.new("RGBA", (PANEL_W, 9), T)
    px = img.load()
    for y in range(9):
        for x in range(PANEL_W):
            px[x, y] = P_FILL
        px[0, y] = P_BORDER
        px[PANEL_W - 1, y] = P_BORDER
    if kind == "top":
        for x in range(PANEL_W):
            px[x, 0] = P_BORDER if 2 <= x <= PANEL_W - 3 else T
            if 2 <= x <= PANEL_W - 3:
                px[x, 1] = P_SHINE
        for x in (0, PANEL_W - 1):
            px[x, 1] = T
        px[1, 1] = P_BORDER
        px[PANEL_W - 2, 1] = P_BORDER
    if kind == "bottom":
        for x in range(PANEL_W):
            px[x, 8] = P_BORDER if 2 <= x <= PANEL_W - 3 else T
        for x in (0, PANEL_W - 1):
            px[x, 7] = T
        px[1, 7] = P_BORDER
        px[PANEL_W - 2, 7] = P_BORDER
    return img


save(strip("top"), "panel_top.png")
save(strip("mid"), "panel_mid.png")
save(strip("bottom"), "panel_bottom.png")


# ---------- Buttons 100x20: aeusserer Ring durchsichtig, damit der Hover-Rand von Minecraft sichtbar bleibt ----------
BUTTON_W = 98


def rounded_box(w, h, border, fill, shine=None):
    """Kasten mit 1 px Rand und runden Ecken (Radius 2)."""
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    for y in range(h):
        for x in range(w):
            corner = (x in (0, w - 1) and y in (0, 1, h - 2, h - 1)) or (y in (0, h - 1) and x in (0, 1, w - 2, w - 1))
            if corner:
                continue
            edge = x in (0, w - 1) or y in (0, h - 1)
            px[x, y] = border if edge else fill
    for x, y in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        px[x, y] = border
    if shine is not None:
        for x in range(2, w - 2):
            px[x, 1] = shine
    return img


def button(border, fill, shine):
    return rounded_box(BUTTON_W, 20, border, fill, shine)


save(button(B_BORDER, B_FILL, B_SHINE), "button_dark.png")
save(button(R_BORDER, R_FILL, R_SHINE), "button_red.png")


# ---------- Titelleiste 160x15 ----------
def titlebar():
    w, h = 160, 15
    img = rounded_box(w, h, GOLD, hexc("141418", 225))
    px = img.load()
    for x in range(2, w - 2):
        px[x, 1] = GOLD_DD
        px[x, h - 2] = GOLD_DD
    # Smaragd-Akzente links und rechts
    for cx in (6, w - 7):
        for dy, row in enumerate(["..G..", ".GLG.", "GLLLG", ".GLG.", "..G.."]):
            for dx, c in enumerate(row):
                if c == "G":
                    px[cx - 2 + dx, 5 + dy] = GREEN
                elif c == "L":
                    px[cx - 2 + dx, 5 + dy] = hexc("7CE38F")
    return img


save(titlebar(), "titlebar.png")


# ---------- Kleine Icons aus Zeichen-Mustern ----------
def pattern(rows, palette):
    h = len(rows)
    w = max(len(r) for r in rows)
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in palette:
                px[x, y] = palette[c]
    return img


save(pattern([
    ".BBBBBBBBBBB.",
    "BDDDDDDDDDDDB",
    "BDDDDDDDDDDDB",
    "BDDDDDDWWDDDB",
    "BDDDDDWWDDDDB",
    "BDDDDWWDDDDDB",
    "BDDDWWDDDDDDB",
    "BDDDDWWDDDDDB",
    "BDDDDDWWDDDDB",
    "BDDDDDDWWDDDB",
    "BDDDDDDDDDDDB",
    "BDDDDDDDDDDDB",
    ".BBBBBBBBBBB.",
], {"B": GREY, "D": DARK, "W": WHITE}), "back.png")

save(pattern([
    "......KKKKKKKKK.",
    "......KRRRRRRRK.",
    "......KRWRRRRRK.",
    "......KRRRRRRRK.",
    "......KRRRWRRRK.",
    "......KRRRRRRRK.",
    ".GGGGGGGGGRRWRK.",
    ".GWWWWWWWGRRRRK.",
    ".GWRWWWRWGKKKKK.",
    ".GWWWWWWWG......",
    ".GWWWRWWWG......",
    ".GWWWWWWWG......",
    ".GWRWWWRWG......",
    ".GWWWWWWWG......",
    ".GGGGGGGGG......",
    "................",
], {"K": RED_D, "R": RED, "W": WHITE, "G": GREY}), "dice.png")

save(pattern([
    "..DDDDD..",
    ".DLGGGGD.",
    "DLGCCCGGD",
    "DGCGGGCGD",
    "DGCGGGGGD",
    "DGCGGGCGD",
    "DGGCCCGGD",
    ".DGGGGGD.",
    "..DDDDD..",
], {"D": GOLD_D, "G": GOLD, "L": GOLD_L, "C": GOLD_DD}), "coin.png")

save(pattern([
    "LRR",
    "RRR",
    "RRD",
], {"L": hexc("FF6B80"), "R": hexc("E0364F"), "D": hexc("A0182C")}), "bullet.png")

SUITS = {
    "suit_club": (["..XXX..", "..XXX..", "XXXXXXX", "XXXXXXX", "XX.X.XX", "...X...", "..XXX.."], hexc("3DBB54")),
    "suit_diamond": (["...X...", "..XXX..", ".XXXXX.", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."], hexc("3CC8E0")),
    "suit_heart": ([".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X...", "......."], hexc("E0364F")),
    "suit_star": (["...X...", "...X...", "XXXXXXX", ".XXXXX.", "..XXX..", ".XX.XX.", ".X...X."], GOLD),
    "suit_seven": (["XXXXXXX", ".....XX", "....XX.", "...XX..", "..XX...", "..XX...", "..XX..."], hexc("C81E32")),
}
for name, (rows, color) in SUITS.items():
    save(pattern(rows, {"X": color}), name + ".png")


# ---------- Spiel-Icons 16x16 ----------
def icon_cards():
    return pattern([
        "................",
        "...GGGGGGG......",
        "...GRWWWWG......",
        "...GWWWWWGGGGGG.",
        "...GWWRWWGKWWWG.",
        "...GWRRRWGWWKWG.",
        "...GRRRRRGWKKKG.",
        "...GWRRRWGKKKKK.",
        "...GWWRWWGWWKWG.",
        "...GWWWWWGWKKKG.",
        "...GWWWWRGWWWWG.",
        "...GGGGGGGWWWWG.",
        ".........GWWWKG.",
        ".........GGGGGG.",
        "................",
        "................",
    ], {"G": GREY, "W": WHITE, "R": RED, "K": BLACK})


def icon_roulette():
    img = Image.new("RGBA", (16, 16), T)
    px = img.load()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= 7.6:
                if d >= 6.8:
                    px[x, y] = GOLD_D
                elif d <= 2.0:
                    px[x, y] = GREEN if d > 1.0 else GOLD
                else:
                    ang = (math.degrees(math.atan2(y - cy, x - cx)) + 360) % 360
                    px[x, y] = RED if int(ang // 30) % 2 == 0 else BLACK
    return img


def icon_slots():
    return pattern([
        "................",
        "..RRRRRRRRRR..K.",
        "..RLLLLLLLLR..K.",
        "..RWWWWWWWWR..K.",
        "..RW7W7W7WWR.KK.",
        "..RW7W7W7WWRK...",
        "..RWWWWWWWWRK...",
        "..RLLLLLLLLR....",
        "..RRRRRRRRRR....",
        "..RDDDDDDDDR....",
        "..RDGGGGGGDR....",
        "..RDDDDDDDDR....",
        "..RRRRRRRRRR....",
        "..KK......KK....",
        "................",
        "................",
    ], {"R": RED, "L": hexc("FF6B80"), "W": WHITE, "7": hexc("C81E32"), "D": RED_D, "G": GOLD, "K": GREY})


def icon_crash():
    return pattern([
        "................",
        "..........RRRRR.",
        "............RRR.",
        "...........RRRR.",
        "..........RR..R.",
        ".G.......RR.....",
        ".G......RR......",
        ".G.....RR.......",
        ".GR...RR........",
        ".GRR.RR.........",
        ".G.RRR..........",
        ".G..R...........",
        ".G..............",
        ".GGGGGGGGGGGGGG.",
        "................",
        "................",
    ], {"R": RED, "G": GREY})


def icon_poker():
    return pattern([
        "................",
        "....RRRRRRR.....",
        "...RWRRRRRWR....",
        "...RRRRRRRRR....",
        "...DRWRRRWRD....",
        "....DDDDDDD.....",
        "....BBBBBBB.....",
        "...BWBBBBBWB....",
        "...BBBBBBBBB....",
        "...KBWBBBWBK....",
        "....KKKKKKK.....",
        "....GGGGGGG.....",
        "...GWGGGGGWG....",
        "...GGGGGGGGG....",
        "...DGWGGGWGD....",
        "....DDDDDDD.....",
    ], {"R": RED, "W": WHITE, "D": RED_D, "B": hexc("2E5BD8"), "K": hexc("1C3A8A"), "G": hexc("2FA84A")})


def icon_trophy():
    return pattern([
        "................",
        "...GGGGGGGGGG...",
        ".GGGLGGGGGGGGGG.",
        ".G.GLGGGGGGGG.G.",
        ".G.GLGGGGGGGG.G.",
        "..GGGGGGGGGGGG..",
        "....GGGGGGGG....",
        ".....DGGGGD.....",
        "......DGGD......",
        ".......GG.......",
        ".......GG.......",
        ".....DDDDDD.....",
        "....GGGGGGGG....",
        "....GGGGGGGG....",
        "................",
        "................",
    ], {"G": GOLD, "L": GOLD_L, "D": GOLD_D})


def icon_music():
    return pattern([
        "................",
        "......WWWWWWWW..",
        "......WRRRRRRW..",
        "......W......W..",
        "......W......W..",
        "......W......W..",
        "......W......W..",
        "......W......W..",
        "......W....WWW..",
        "...WWWW...WWWW..",
        "..WWWWW...WWWW..",
        "..WWWW.....WW...",
        "...WW...........",
        "................",
        "................",
        "................",
    ], {"W": WHITE, "R": RED})


ICONS16 = {
    "icon_cards": icon_cards(), "icon_roulette": icon_roulette(), "icon_slots": icon_slots(),
    "icon_crash": icon_crash(), "icon_poker": icon_poker(), "icon_trophy": icon_trophy(),
    "icon_music": icon_music(),
}
for name, img in ICONS16.items():
    save(img, name + ".png")

# ---------- Schrift ----------
providers = []
neg = {chr(0xF801 + i): -(1 << i) for i in range(10)}
pos = {chr(0xF811 + i): (1 << i) for i in range(10)}
providers.append({"type": "space", "advances": {**neg, **pos}})


def bitmap(file, height, ascent, char):
    providers.append({"type": "bitmap", "file": "kuby:ui/" + file, "height": height, "ascent": ascent, "chars": [char]})


bitmap("panel_top.png", 9, 8, "")
for k in range(1, MAX_ROWS + 1):
    bitmap("panel_mid.png", 9, 8 - 9 * k, chr(0xE100 + k))
    bitmap("panel_bottom.png", 9, 8 - 9 * k, chr(0xE140 + k))
bitmap("back.png", 13, -4, "")
bitmap("dice.png", 16, -3, "")
for i, name in enumerate(["icon_cards", "icon_roulette", "icon_slots", "icon_crash", "icon_poker", "icon_trophy", "icon_music"]):
    bitmap(name + ".png", 16, -3, chr(0xE003 + i))
bitmap("bullet.png", 3, 5, "")
for i, name in enumerate(["suit_club", "suit_diamond", "suit_heart", "suit_star", "suit_seven"]):
    bitmap(name + ".png", 7, 7, chr(0xE011 + i))
bitmap("coin.png", 9, 8, "")
bitmap("titlebar.png", 15, 11, "")
bitmap("button_dark.png", 20, 13, "")
bitmap("button_red.png", 20, 13, "")

with open(os.path.join(FONT, "ui.json"), "w", encoding="utf-8") as f:
    json.dump({"providers": providers}, f, ensure_ascii=True, indent=1)

with open(os.path.join(OUT, "pack.mcmeta"), "w", encoding="utf-8") as f:
    json.dump({"pack": {
        "description": "KubyUI: Casino-Hilfe und Icons",
        "pack_format": 64,
        "supported_formats": {"min_inclusive": 46, "max_inclusive": 1000},
        "min_format": 46,
        "max_format": 1000,
    }}, f, indent=2)
print("ok", OUT)


# =====================================================================================
# Scoreboard (Schrift kubyplugins:sb): goldener Rahmen, Logos je Modus, Deko, Zeilen-Icons.
# Muss zu de.kuby.kubyscoreboard.SbUi passen.
# =====================================================================================
SB_TEX = os.path.join(OUT, "assets/kubyplugins/textures/sb")
SB_FONT = os.path.join(OUT, "assets/kubyplugins/font")
os.makedirs(SB_TEX, exist_ok=True)
os.makedirs(SB_FONT, exist_ok=True)

SB_W = 140            # Inhaltsbreite der Sidebar
SB_FRAME_W = SB_W + 4  # Rahmen ragt links und rechts 2 px über (deckt den Standard-Hintergrund ab)
SB_MAX_ROWS = 40

F_OUT = hexc("1E1405")
F_GOLD = hexc("F2C230")
F_GOLD_L = hexc("FFE07A")
F_SHADE = hexc("8A5E10")
F_IN = hexc("121216", 200)


def sb_save(img, name):
    img.save(os.path.join(SB_TEX, name))


def frame_full(w, h):
    """Rahmen mit 3 px Rand (Kontur, Gold, Schatten) und runden Ecken."""
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    corner = [["T", "T", "O"], ["T", "O", "G"], ["O", "G", "S"]]
    col = {"O": F_OUT, "G": F_GOLD, "S": F_SHADE}
    for y in range(h):
        for x in range(w):
            dx = min(x, w - 1 - x)
            dy = min(y, h - 1 - y)
            if dx < 3 and dy < 3:
                c = corner[dy][dx]
                if c != "T":
                    px[x, y] = col[c]
                continue
            d = min(dx, dy)
            if d == 0:
                px[x, y] = F_OUT
            elif d == 1:
                px[x, y] = F_GOLD_L if y == 1 else F_GOLD
            elif d == 2:
                px[x, y] = F_SHADE
            else:
                px[x, y] = F_IN
    return img


full = frame_full(SB_FRAME_W, 9 * 6)
sb_save(full.crop((0, 0, SB_FRAME_W, 9)), "frame_top.png")
sb_save(full.crop((0, 18, SB_FRAME_W, 27)), "frame_mid.png")

# Unterkante mit Deko, die nach unten über den Rahmen ragt (16 px hoch).
bottom = Image.new("RGBA", (SB_FRAME_W, 16), T)
bottom.alpha_composite(full.crop((0, 9 * 6 - 9, SB_FRAME_W, 9 * 6)), (0, 0))
coins = pattern([
    "......DDDD..........",
    ".....DYYYYD.........",
    ".....DGGGGD..DDDD...",
    "......DDDD..DYYYYD..",
    "..DDDD.DDDD.DGGGGD..",
    ".DYYYYDYYYYD.DDDD...",
    ".DGGGGDGGGGDDYYYYD..",
    "..DDDD.DDDD.DGGGGD..",
    "DDDDDDDDDDDDDDDDDDD.",
    "DYYYYYYYYYYYYYYYYYD.",
    "DGGGGGGGGGGGGGGGGGD.",
    ".DDDDDDDDDDDDDDDDD..",
], {"D": GOLD_DD, "Y": GOLD_L, "G": GOLD})
gems = pattern([
    "....EEEE.......",
    "...ELLEEE......",
    "..ELLEEEEE.....",
    "..EEEEEEED..EE.",
    "...EEEEED..ELEE",
    "....EEED..EEEED",
    ".....ED....EED.",
    "............D..",
], {"E": hexc("2FBF5A"), "L": hexc("9CF5B4"), "D": hexc("167A35")})
bottom.alpha_composite(coins, (2, 4))
bottom.alpha_composite(gems, (SB_FRAME_W - 18, 7))
sb_save(bottom, "frame_bottom.png")

# ---------- Logos ----------
BIG = {
    "K": ["XX...XX", "XX..XX.", "XX.XX..", "XXXX...", "XXX....", "XXXX...", "XX.XX..", "XX..XX.", "XX...XX"],
    "U": ["XX...XX", "XX...XX", "XX...XX", "XX...XX", "XX...XX", "XX...XX", "XX...XX", "XXX.XXX", ".XXXXX."],
    "B": ["XXXXXX.", "XX...XX", "XX...XX", "XX..XX.", "XXXXX..", "XX..XX.", "XX...XX", "XX...XX", "XXXXXX."],
    "Y": ["XX...XX", "XX...XX", ".XX.XX.", "..XXX..", "..XXX..", "..XXX..", "..XXX..", "..XXX..", "..XXX.."],
}
SMALL = {
    "S": [".XXXX", "X....", ".XXX.", "....X", "XXXX."], "M": ["X...X", "XX.XX", "X.X.X", "X...X", "X...X"],
    "P": ["XXXX.", "X...X", "XXXX.", "X....", "X...."], "U": ["X...X", "X...X", "X...X", "X...X", ".XXX."],
    "R": ["XXXX.", "X...X", "XXXX.", "X..X.", "X...X"], "V": ["X...X", "X...X", "X...X", ".X.X.", "..X.."],
    "I": ["XXX", ".X.", ".X.", ".X.", "XXX"], "A": [".XXX.", "X...X", "XXXXX", "X...X", "X...X"],
    "L": ["X....", "X....", "X....", "X....", "XXXXX"], "H": ["X...X", "X...X", "XXXXX", "X...X", "X...X"],
    "B": ["XXXX.", "X...X", "XXXX.", "X...X", "XXXX."],
}


def big_word(word, scale=2):
    gw = sum(len(BIG[c][0]) + 1 for c in word) - 1
    mask = Image.new("L", (gw * scale, 9 * scale), 0)
    m = mask.load()
    x0 = 0
    for c in word:
        for y, row in enumerate(BIG[c]):
            for x, v in enumerate(row):
                if v == "X":
                    for sy in range(scale):
                        for sx in range(scale):
                            m[(x0 + x) * scale + sx, y * scale + sy] = 255
        x0 += len(BIG[c][0]) + 1
    w, h = mask.size
    img = Image.new("RGBA", (w + 3, h + 3), T)
    px = img.load()
    # Kontur (1 px) und Schlagschatten (1 px nach unten rechts)
    for y in range(h):
        for x in range(w):
            if m[x, y]:
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1), (2, 2), (1, 2), (2, 1)):
                    xx, yy = x + 1 + dx, y + 1 + dy
                    if 0 <= xx < w + 3 and 0 <= yy < h + 3:
                        px[xx, yy] = F_OUT
    grad = [F_GOLD_L, F_GOLD_L, F_GOLD_L, F_GOLD, F_GOLD, F_GOLD, F_GOLD, F_GOLD, hexc("E0A82A"), hexc("E0A82A"),
            hexc("C8891A"), hexc("C8891A"), hexc("C8891A"), hexc("B07418"), hexc("B07418"), hexc("9C6512"), hexc("9C6512"), hexc("8A5A10")]
    for y in range(h):
        for x in range(w):
            if m[x, y]:
                px[x + 1, y + 1] = grad[min(y, len(grad) - 1)]
    return img


def small_word(word, color):
    gw = sum(len(SMALL[c][0]) + 1 for c in word) - 1
    img = Image.new("RGBA", (gw, 5), T)
    px = img.load()
    x0 = 0
    for c in word:
        for y, row in enumerate(SMALL[c]):
            for x, v in enumerate(row):
                if v == "X":
                    px[x0 + x, y] = color
        x0 += len(SMALL[c][0]) + 1
    return img


def ribbon(word, fill, light, dark):
    text = small_word(word, WHITE)
    w = text.width + 14
    h = 9
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    for y in range(h):
        for x in range(w):
            notch = (x < 3 and abs(y - 4) <= 1 - x) or (x > w - 4 and abs(y - 4) <= x - (w - 2))
            if notch:
                continue
            edge = y in (0, h - 1) or x in (0, w - 1)
            px[x, y] = F_OUT if edge else (light if y == 1 else fill)
    for x in range(1, w - 1):
        px[x, h - 2] = dark
    for x in (0, 1, 2):
        for y in range(h):
            if px[x, y][3] and (x == 0 or (y in (0, h - 1))):
                px[x, y] = F_OUT
    shadow = small_word(word, dark)
    img.alpha_composite(shadow, (8, 3))
    img.alpha_composite(text, (7, 2))
    return img


SWORD = pattern([
    "......WW",
    ".....WGW",
    "....WGW.",
    "...WGW..",
    "B.WGW...",
    ".BBW....",
    "..BB....",
    ".B..B...",
], {"W": WHITE, "G": GREY, "B": hexc("7A4A20")})
PICK = pattern([
    "..CCCC..",
    ".C....C.",
    "C..BB..C",
    "...BB...",
    "...BB...",
    "...BB...",
    "...BB...",
    "...BB...",
], {"C": hexc("5FD4E8"), "B": hexc("7A4A20")})
STAR = pattern(["...Y...", "...Y...", ".YYYYY.", "YYYYYYY", ".YYYYY.", "..Y.Y..", ".Y...Y."], {"Y": GOLD_L})


def logo(word, ribbon_img, deco=None):
    top = big_word("KUBY")
    w = max(top.width, ribbon_img.width) + 24
    h = top.height + ribbon_img.height - 3
    img = Image.new("RGBA", (w, h), T)
    if deco == "swords":
        sw = SWORD.resize((16, 16), Image.NEAREST)
        img.alpha_composite(sw.transpose(Image.FLIP_LEFT_RIGHT), (1, h - 17))
        img.alpha_composite(sw, (w - 17, h - 17))
    elif deco == "tools":
        p = PICK.resize((16, 16), Image.NEAREST)
        img.alpha_composite(p.rotate(30), (0, h - 17))
        img.alpha_composite(p.rotate(-30), (w - 16, h - 17))
    elif deco == "stars":
        big = STAR.resize((14, 14), Image.NEAREST)
        img.alpha_composite(big, (3, h - 15))
        img.alpha_composite(big, (w - 17, h - 15))
    img.alpha_composite(top, ((w - top.width) // 2, 0))
    img.alpha_composite(ribbon_img, ((w - ribbon_img.width) // 2, top.height - 3))
    return img


LOGOS = {
    "logo_smp": logo("SMP", ribbon("SMP", hexc("C8283E"), hexc("E8485E"), hexc("7A1020")), "swords"),
    "logo_survival": logo("SURVIVAL", ribbon("SURVIVAL", hexc("2FA84A"), hexc("5FD47A"), hexc("1A6A2E")), "tools"),
    "logo_hub": logo("HUB", ribbon("HUB", hexc("2E5BD8"), hexc("5A84F0"), hexc("1C3A8A")), "stars"),
}
for name, img in LOGOS.items():
    # Letzte Spalte minimal deckend, damit der Vorschub genau der Bildbreite entspricht (sauber zentrierbar).
    img.load()[img.width - 1, img.height - 1] = (0, 0, 0, 1)
    sb_save(img, name + ".png")

# ---------- Zeilen-Icons 8x8 (Vorschub immer 9, damit die Texte buendig stehen) ----------
def sb_icon(rows, palette):
    img = pattern(rows, palette)
    if img.width < 8:
        canvas = Image.new("RGBA", (8, 8), T)
        canvas.alpha_composite(img, (0, 0))
        img = canvas
    px = img.load()
    if not any(px[7, y][3] for y in range(img.height)):
        px[7, 7] = (0, 0, 0, 1)
    return img


SB_ICONS = [
    ("sb_coin", ["..DDDD..", ".DYYYYD.", ".DGGGGD.", "..DDDD..", ".DYYYYD.", ".DGGGGD.", "..DDDDD.", "........"],
     {"D": GOLD_DD, "Y": GOLD_L, "G": GOLD}),
    ("sb_gem", ["..EEEE..", ".ELLEEE.", "ELLEEEEE", "EEEEEEED", ".EEEEED.", "..EEED..", "...ED...", "........"],
     {"E": hexc("2FBF5A"), "L": hexc("9CF5B4"), "D": hexc("167A35")}),
    ("sb_xp", ["..GGGG..", ".GLLGGG.", "GLLYGGGG", "GLYYYGGG", "GGYYYGGD", ".GGGGGD.", "..GGDD..", "........"],
     {"G": hexc("7CE34A"), "L": hexc("D8FF9A"), "Y": hexc("F2F27A"), "D": hexc("3E8A1E")}),
    ("sb_trophy", ["YYYYYYYY", "Y.YLYY.Y", "Y.YLYY.Y", ".YYYYYY.", "..YYYY..", "...YY...", "..DDDD..", ".YYYYYY."],
     {"Y": GOLD, "L": GOLD_L, "D": GOLD_D}),
    ("sb_sword", [".....WWW", "....WGGW", "...WGGW.", "B.WGGW..", ".BWGW...", "..BB....", ".B..B...", "B.......",],
     {"W": WHITE, "G": GREY, "B": hexc("7A4A20")}),
    ("sb_skull", [".WWWWWW.", "WWWWWWWW", "WKKWWKKW", "WKKWWKKW", "WWWKKWWW", ".WWWWWW.", ".W.WW.W.", "........"],
     {"W": hexc("ECECEC"), "K": BLACK}),
    ("sb_clock", ["BBBBBBBB", ".BWWWWB.", "..BSSB..", "...BB...", "..B..B..", ".BWSSWB.", "BBBBBBBB", "........"],
     {"B": hexc("7A4A20"), "W": hexc("CFEFFF"), "S": hexc("4FB4FF")}),
    ("sb_people", ["..PP....", ".PSSP.PP", ".PSSPPSS", "..PP.PSS", ".BBBB.PP", "BBBBBBBB", "BBBBBBBB", "........"],
     {"P": hexc("6A4A2A"), "S": hexc("E8B890"), "B": hexc("3C8CE0")}),
    ("sb_rank", ["Y..Y..Y.", "YY.YY.YY", "YYYYYYYY", "YRYYBYYG", "YYYYYYYY", "DDDDDDDD", "........", "........"],
     {"Y": GOLD, "D": GOLD_D, "R": RED, "B": hexc("3C8CE0"), "G": hexc("2FBF5A")}),
    ("sb_team", ["G.......", "GRRRRRR.", "GRWWRRR.", "GRRRRRR.", "GRRRRR..", "GRRRR...", "G.......", "G......."],
     {"G": GREY, "R": RED, "W": WHITE}),
    ("sb_claim", ["G.......", "GYYYYY..", "GYYYYYY.", "GYYYYY..", "G.......", "G.......", "DDDDDDDD", "........"],
     {"G": GREY, "Y": hexc("5FD47A"), "D": hexc("7A4A20")}),
    ("sb_token", ["..PPPP..", ".PLLPPP.", "PLPWWWPP", "PPPPWPPP", "PPPPWPPD", ".PPPWPD.", "..PPDD..", "........"],
     {"P": hexc("9C4CE0"), "L": hexc("D3A8FF"), "W": WHITE, "D": hexc("5A2490")}),
    ("sb_level", ["...GG...", "..GGGG..", ".GGGGGG.", "GGGGGGGG", "...GG...", "...GG...", "...GG...", "........"],
     {"G": hexc("7CE34A")}),
    ("sb_job", ["..CCCC..", ".C....C.", "C..BB..C", "...BB...", "...BB...", "...BB...", "...BB...", "........"],
     {"C": hexc("5FD4E8"), "B": hexc("7A4A20")}),
    ("sb_arrow", ["........", "....W...", "....WW..", "WWWWWWW.", "WWWWWWWW", "....WW..", "....W...", "........"],
     {"W": WHITE}),
]
SB_ICON_CHARS = {}
for i, (name, rows, pal) in enumerate(SB_ICONS):
    sb_save(sb_icon(rows, pal), name + ".png")
    SB_ICON_CHARS[name] = chr(0xE010 + i)

# ---------- Schrift kubyplugins:sb ----------
sb_providers = [{"type": "space", "advances": {**neg, **pos}}]


def sb_bitmap(file, height, ascent, char):
    sb_providers.append({"type": "bitmap", "file": "kubyplugins:sb/" + file, "height": height, "ascent": ascent, "chars": [char]})


sb_bitmap("frame_top.png", 9, 8, "")
for k in range(1, SB_MAX_ROWS + 1):
    sb_bitmap("frame_mid.png", 9, 8 - 9 * k, chr(0xE100 + k))
    sb_bitmap("frame_bottom.png", 16, 8 - 9 * k, chr(0xE140 + k))
for i, name in enumerate(["logo_smp", "logo_survival", "logo_hub"]):
    h = LOGOS[name].height
    # Unterkante des Logos 2 px unter der Titelzeile, damit das Band über dem oberen Rahmen liegt.
    sb_bitmap(name + ".png", h, h - 2, chr(0xE050 + i))
for name, _, _ in SB_ICONS:
    sb_bitmap(name + ".png", 8, 7, SB_ICON_CHARS[name])

with open(os.path.join(SB_FONT, "sb.json"), "w", encoding="utf-8") as f:
    json.dump({"providers": sb_providers}, f, ensure_ascii=True, indent=1)
print("ok scoreboard")
