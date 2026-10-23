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
            px[x, 0] = P_BORDER
            if 1 <= x <= PANEL_W - 2:
                px[x, 1] = P_SHINE
        px[0, 0] = T
        px[PANEL_W - 1, 0] = T
    if kind == "bottom":
        for x in range(PANEL_W):
            px[x, 8] = P_BORDER
        px[0, 8] = T
        px[PANEL_W - 1, 8] = T
    return img


save(strip("top"), "panel_top.png")
save(strip("mid"), "panel_mid.png")
save(strip("bottom"), "panel_bottom.png")


# ---------- Buttons 100x20: aeusserer Ring durchsichtig, damit der Hover-Rand von Minecraft sichtbar bleibt ----------
def button(border, fill, shine):
    w, h = 100, 20
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            edge = x in (1, w - 2) or y in (1, h - 2)
            px[x, y] = border if edge else fill
    for x in range(2, w - 2):
        px[x, 2] = shine
    # Rechte Spalte deckend lassen, damit die Breite stimmt (Ring-Ecke halbtransparent).
    px[w - 1, h // 2] = (0, 0, 0, 1)
    return img


save(button(B_BORDER, B_FILL, B_SHINE), "button_dark.png")
save(button(R_BORDER, R_FILL, R_SHINE), "button_red.png")


# ---------- Titelleiste 160x15 ----------
def titlebar():
    w, h = 160, 15
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    fill = hexc("141418", 225)
    for y in range(h):
        for x in range(w):
            edge_x = x in (0, w - 1)
            edge_y = y in (0, h - 1)
            if edge_x and edge_y:
                continue
            px[x, y] = GOLD if (edge_x or edge_y) else fill
    for x in range(1, w - 1):
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
