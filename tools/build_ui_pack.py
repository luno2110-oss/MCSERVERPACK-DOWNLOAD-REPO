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

# Aufzählungspunkt 3 x 3, unten 2 leere Zeilen: Minecraft verlangt ascent <= height (sonst fällt die ganze Schrift weg)
save(pattern([
    "LRR",
    "RRR",
    "RRD",
    "...",
    "...",
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
bitmap("bullet.png", 5, 5, "")
for i, name in enumerate(["suit_club", "suit_diamond", "suit_heart", "suit_star", "suit_seven"]):
    bitmap(name + ".png", 7, 7, chr(0xE011 + i))
bitmap("coin.png", 9, 8, "")
bitmap("titlebar.png", 15, 11, "")
bitmap("button_dark.png", 20, 13, "")
bitmap("button_red.png", 20, 13, "")

# ---------- Icons für die Seitenköpfe der Survival-Hilfe (8 x 8, doppelt groß angezeigt) ----------
# Neue Muster hier, dazu Icons aus dem Scoreboard (kubyplugins:sb). Muss zu de.kuby.kubyplugins.ui.Ui.Icon passen.
HELP_ICONS = {
    "book": ([".RRRRRR.", "RRRRRRWw", "RYYYYRWw", "RRRRRRWw", "RRRRRRWw", "RRYYRRWw", "RRRRRRWw", ".rrrrrr."],
             {"R": hexc("B83030"), "r": hexc("7A1C1C"), "Y": hexc("F2C230"), "W": hexc("F4F0E4"), "w": hexc("C8C0B0")}),
    "scroll": (["..pppp..", ".pWWWWp.", ".pWkkWp.", ".pWWWWp.", ".pWkkWp.", ".pWWRRp.", "..ppRR..", "........"],
               {"p": hexc("C8A870"), "W": hexc("F4E8C8"), "k": hexc("8A7050"), "R": hexc("D83030")}),
    "map": (["pppppppp", "pGGGBBBp", "pGGRGBBp", "pBGGGGBp", "pBBGGRGp", "pBBBGGGp", "pppppppp", "........"],
            {"p": hexc("C8A870"), "G": hexc("6CB43C"), "B": hexc("4C8CD8"), "R": hexc("D83030")}),
    "pickaxe": (["..cCCCc.", ".c..N..c", "c...N...", "....N...", "....N...", "....N...", "....n...", "....n..."],
                {"C": hexc("8CE8F4"), "c": hexc("2FA8C0"), "N": hexc("9C6834"), "n": hexc("6E4420")}),
    "stall": (["RWRWRWRW", "RWRWRWRW", ".b....b.", ".bYYYYb.", ".bOOOOb.", ".bbbbbb.", ".b....b.", "........"],
              {"R": hexc("D83040"), "W": hexc("F4F4F4"), "b": hexc("8A5A2C"), "Y": hexc("F2C230"), "O": hexc("E08A28")}),
    "leaf": (["..oYYo..", ".oGGgYo.", "oGLGGgYo", "oGGLGgYo", "oYGgLGYo", "oYgGGGYo", ".oYYYYo.", "..oooo.."],
             {"o": hexc("A8680E"), "Y": hexc("F4C232"), "G": hexc("4CC040"), "g": hexc("2E8028"), "L": hexc("A8F070")}),
    "paw": ([".w.ww.w.", ".w.ww.w.", "........", "..wwww..", ".wwwwww.", ".wwwwww.", "..w..w..", "........"],
            {"w": hexc("E8C8A8")}),
    "bed": (["........", "........", "WW.....b", "WWRRRRRb", "RRRRRRRb", "rrrrrrrb", "b......b", "........"],
            {"W": hexc("F4F4F4"), "R": hexc("D83040"), "r": hexc("962030"), "b": hexc("8A5A2C")}),
    "check": (["WWWWWW..", "WWWWWWG.", "WWWWWGG.", "WGWWGGW.", "WGGGGWW.", "WWGGWWW.", "WWWWWWW.", "........"],
              {"W": hexc("F4F4F4"), "G": hexc("3CB43C")}),
    "gear": (["...gg...", ".gGGGGg.", ".GGddGG.", "gGd..dGg", "gGd..dGg", ".GGddGG.", ".gGGGGg.", "...gg..."],
             {"g": hexc("7A7A84"), "G": hexc("B4B4BE"), "d": hexc("50505A")}),
    "rings": (["..P.....", ".YYY.YYY", "Y..YY..Y", "Y..YY..Y", "Y..YY..Y", ".YYY.YYY", "........", "........"],
              {"P": hexc("F060A0"), "Y": hexc("F2C230")}),
    "chair": ([".b......", ".b......", ".b......", ".bRRRR..", ".bbbbbb.", ".b....b.", ".b....b.", "........"],
              {"b": hexc("8A5A2C"), "R": hexc("D83040")}),
    "lifebuoy": (["..RWWR..", ".RWWWWR.", "RW....WR", "WW....WW", "WW....WW", "RW....WR", ".RWWWWR.", "..RWWR.."],
                 {"R": hexc("D83040"), "W": hexc("F4F4F4")}),
    "discord": ([".BBBBBB.", "BBBBBBWB", "BBBBBWWB", "BWBBWWBB", "BWWWWBBB", "BBWWBBBB", ".BBBBBB.", "..B....."],
                {"B": hexc("4C78F0"), "W": hexc("F4F4F4")}),
    "hat": (["..PPPP..", "..PPPP..", "..PPPP..", "..YYYY..", "..PPPP..", "pPPPPPPp", ".pppppp.", "........"],
            {"P": hexc("8A3CC8"), "p": hexc("5A2090"), "Y": hexc("F2C230")}),
    "chest": ([".bbbbbb.", "bBBBBBBb", "bBBBBBBb", "dddYYddd", "bBBYYBBb", "bBBBBBBb", "bBBBBBBb", ".dddddd."],
              {"b": hexc("6A4220"), "B": hexc("B8803E"), "d": hexc("3A2410"), "Y": hexc("F2C230")}),
    "chart": (["WWWWWWWW", "W.....GW", "W.....GW", "W..Y..GW", "W..Y..GW", "WR.Y..GW", "WR.Y..GW", "WWWWWWWW"],
              {"W": hexc("F4F4F4"), "G": hexc("3CB43C"), "Y": hexc("F2C230"), "R": hexc("D83040")}),
    "bar": (["........", "bbbbbbbb", "bWWWWWWb", "bWYWGWRb", "bWWWWWWb", "bbbbbbbb", "........", "........"],
            {"b": hexc("8A5A2C"), "W": hexc("F4F0E4"), "Y": hexc("F2C230"), "G": hexc("3CB43C"), "R": hexc("D83040")}),
}
# Reihenfolge = Zeichen ab U+E200
HELP_ICON_ORDER = ["book", "scroll", "map", "pickaxe", "stall", "leaf", "paw", "bed", "check", "gear", "rings", "chair",
                   "lifebuoy", "discord", "hat", "chest", "chart", "bar",
                   "sb:spawn", "sb:claim", "sb:job", "sb:coin", "sb:token", "sb:level", "sb:team", "sb:gem", "sb:skull",
                   "sb:people", "sb:clock", "sb:compass", "sb:rank"]
for i, name in enumerate(HELP_ICON_ORDER):
    if name.startswith("sb:"):
        file = "kubyplugins:sb/sb_" + name[3:] + ".png"
    else:
        rows, pal = HELP_ICONS[name]
        img = Image.new("RGBA", (8, 8), T)
        img.alpha_composite(pattern(rows, pal), (0, 0))
        if not any(img.getpixel((7, y))[3] for y in range(8)):
            img.putpixel((7, 7), (0, 0, 0, 1))          # Vorschub immer 17
        save(img, "help_" + name + ".png")
        file = "kuby:ui/help_" + name + ".png"
    providers.append({"type": "bitmap", "file": file, "height": 16, "ascent": -3, "chars": [chr(0xE200 + i)]})


# ---------- Farbthemen der Survival-Hilfe: Rahmen, Aufzählungspunkt und Aktions-Button je Farbe ----------
# Eigene Schrift je Thema (kuby:<name>) mit denselben Zeichen wie kuby:ui. Muss zu Ui.Theme passen.
THEMES = {
    "green": ("3CC85A", "0C2414", "123A1E", "5FE07A"),
    "gold": ("E2B030", "241C08", "382C0E", "F2C84A"),
    "gray": ("74747E", "18181E", "26262E", "D2D2DA"),
    "orange": ("C87830", "26160A", "3A2410", "F0A050"),
    "blue": ("3C9CE8", "0A1E30", "102C46", "64C0FF"),
    "red": ("D83C50", "260A10", "3A1018", "FF6478"),
    "pink": ("E05AA0", "260C1E", "3A142C", "FF7AC0"),
}


def theme_strip(kind, border, fill, shine):
    img = Image.new("RGBA", (PANEL_W, 9), T)
    px = img.load()
    for y in range(9):
        for x in range(PANEL_W):
            px[x, y] = fill
        px[0, y] = border
        px[PANEL_W - 1, y] = border
    if kind == "top":
        for x in range(PANEL_W):
            px[x, 0] = border if 2 <= x <= PANEL_W - 3 else T
            if 2 <= x <= PANEL_W - 3:
                px[x, 1] = shine
        for x in (0, PANEL_W - 1):
            px[x, 1] = T
        px[1, 1] = border
        px[PANEL_W - 2, 1] = border
    if kind == "bottom":
        for x in range(PANEL_W):
            px[x, 8] = border if 2 <= x <= PANEL_W - 3 else T
        for x in (0, PANEL_W - 1):
            px[x, 7] = T
        px[1, 7] = border
        px[PANEL_W - 2, 7] = border
    return img


for theme, (b, f, sh, accent) in THEMES.items():
    tdir = os.path.join(TEX, "theme_" + theme)
    os.makedirs(tdir, exist_ok=True)
    border, fill, shine, acc = hexc(b), hexc(f, 236), hexc(sh, 236), hexc(accent)
    for kind in ("top", "mid", "bottom"):
        theme_strip(kind, border, fill, shine).save(os.path.join(tdir, "panel_" + kind + ".png"))
    pattern(["LAA", "AAA", "AAd", "...", "..."], {"L": tuple(min(255, c + 60) for c in acc[:3]) + (255,),
                                                "A": acc, "d": tuple(c * 6 // 10 for c in acc[:3]) + (255,)}
            ).save(os.path.join(tdir, "bullet.png"))
    light = tuple(min(255, c + 70) for c in border[:3]) + (255,)
    dark = tuple(c * 7 // 10 for c in border[:3]) + (255,)
    rounded_box(BUTTON_W, 20, light, border, tuple(min(255, c + 35) for c in border[:3]) + (255,)
                ).save(os.path.join(tdir, "button_action.png"))
    tp = [{"type": "space", "advances": {**neg, **pos}}]

    def tb(file, height, ascent, char):
        tp.append({"type": "bitmap", "file": "kuby:ui/theme_" + theme + "/" + file, "height": height, "ascent": ascent,
                   "chars": [char]})
    tb("panel_top.png", 9, 8, "")
    for k in range(1, MAX_ROWS + 1):
        tb("panel_mid.png", 9, 8 - 9 * k, chr(0xE100 + k))
        tb("panel_bottom.png", 9, 8 - 9 * k, chr(0xE140 + k))
    tb("bullet.png", 5, 5, "")
    tb("button_action.png", 20, 13, "")
    with open(os.path.join(FONT, theme + ".json"), "w", encoding="utf-8") as fh:
        json.dump({"providers": tp}, fh, ensure_ascii=True, indent=1)


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



# Scoreboard-Grafik (Schrift kubyplugins:sb) in doppelter Aufloesung, siehe build_sb_pack.py.
import build_sb_pack  # noqa: E402

build_sb_pack.build(OUT, {**neg, **pos})


# ---------- Prüfung: Fehler in einer Schrift-Datei lassen Minecraft die ganze Schrift verwerfen ----------
import check_fonts  # noqa: E402

errors = check_fonts.check_fonts(OUT)
for e in errors:
    print("FEHLER " + e)
if errors:
    sys.exit(1)
print("Schriften geprüft: ok")
