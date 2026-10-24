"""Dialog-Kit im Pack KubyUI (Schriften kuby:k/...): Grafik für Menüs wie in den Referenz-Clips (Angeln, Casino).

So funktioniert es (Gegenstück: de.kuby.kubyplugins.ui.Canvas):
- Ein Dialog-Körper ist mehrzeiliger Text mit 9 px Zeilenhöhe. Jedes Element wird waagrecht über Leerzeichen-Zeichen
  und senkrecht über die Schrift (ascent) pixelgenau gesetzt. Jede Schrift gibt es darum in vielen Höhen-Verschiebungen.
- Minecraft zeichnet die Zeichen einer Zeile nach Textur sortiert, nicht in Reihenfolge. Was übereinander liegt, muss
  darum in verschiedenen Zeilen stehen: Hintergründe in früheren Zeilen (weiter nach unten verschoben), Text in seiner
  eigenen Zeile. Das ordnet Canvas automatisch.
- Ein Schriftzeichen darf höchstens 256 px groß sein; Kästen werden aus Stücken (Ecke, Mittelstück, Ecke) gebaut.
  Überlappende Stücke haben im Überlapp dieselben Pixel, die Reihenfolge ist dort egal.

Schriften (kuby:k/...):
  t<s>   s = 0..8     Text (Vanilla-Glyphen) und Symbole, s px tiefer als normal
  b<s>   s = 0..B_MAX Hintergrund-Stücke (Kästen, Knöpfe, Balken), s px tiefer
  i<s>   s = I_MIN..I_MAX  Icons (16 und 32 px), s px tiefer (negativ = höher)

Aufruf: python3 tools/build_kit.py <pack-ordner> <vanilla-ordner>
(<vanilla-ordner> enthält assets/minecraft/... aus dem Release "vanilla-assets"; nur für die Breiten-Tabelle.)
Erzeugt die Schriften, Texturen und de/kuby/kubyplugins/ui/KitData.java.
"""
import json
import os
import shutil
import sys

from PIL import Image

import build_title

T = (0, 0, 0, 0)
HERE = os.path.dirname(os.path.abspath(__file__))
JAVA_OUT = os.path.join(HERE, "..", "KubyCore-v2.0.14", "KubyPlugins", "src", "main", "java", "de", "kuby",
                        "kubyplugins", "ui", "KitData.java")

T_SHIFTS = range(0, 9)
B_MAX = 53
B_SHIFTS = range(0, B_MAX + 1)
I_MIN, I_MAX = -24, 8
I_SHIFTS = range(I_MIN, I_MAX + 1)

SEG = 32           # Breite der Mittelstücke
NARROW = (16, 8, 4, 2, 1)   # schmale Mittelstücke für kurze Knöpfe und Balken
CAP = 8            # Breite der Ecken-Stücke
ROW = 9            # Höhe der Kasten-Zeilen
BTN_H = 22         # Höhe der Knöpfe


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, f):
    f = max(0.0, min(1.0, f))
    return tuple(round(a + (b - a) * f) for a, b in zip(c1[:3], c2[:3])) + (255,)


def lighten(c, f):
    return mix(c, (255, 255, 255, 255), f)


def darken(c, f):
    return mix(c, (0, 0, 0, 255), f)


# =====================================================================================================================
# Stile (Farben aus den Clips gemessen, 4K-Aufnahme bei GUI-Größe 3)
# =====================================================================================================================
K = rgb("05060C")      # schwarzer Außenrand

# Kästen: 1 px schwarz, 2 px Rand (hell, mittel), Füllung
BOXES = {
    "navy": ("2DAEF9", "15689C", "061B28"),      # Angeln: Standard (Kopf, Profil, Kacheln)
    "green": ("3FD55C", "237D35", "06200D"),     # Fischhändler, "Alles verkaufen"
    "gold": ("F8C337", "977224", "281C06"),      # Tagesaufträge, Bestenliste, "Fische des Tages"
    "purple": ("B05AF9", "673293", "1C0B29"),    # Bonus, "Nur die seltenen"
    "teal": ("2FCCC0", "1F7A73", "091F23"),      # aktiver Köder
    "gray": ("8A8E96", "44474E", "141519"),      # neutral
    "red": ("FF4D5E", "9A1F2C", "2A0A10"),       # Warnung
}

# Casino-Kästen (Roulette- und Musik-Dialog im Clip): eigene Ecken, Rand von außen nach innen ("edge"), Füllung N.
# Zeichen im Eckmuster: K schwarz, L/D Rand hell/dunkel, H Glanz in der Ecke, N Füllung.
CORNER_CASINO_HEAD = [      # roter Kopf: Goldrand hell außen, orange innen, schwarze Fase innen an der Ecke
    "...KKKK",
    "..KKLLL",
    ".KLLLDD",
    ".KLDKKN",
    "KLLKKNN",
    "KLDKNNN",
    "KLDNNNN",
]
CORNER_GOLD_FRAME = [       # Filz und dunkle Kästen: Goldrand, innen schwarze Linie, Glanz in der Ecke
    "...KKKK",
    "..KHHLL",
    ".KHHLDD",
    "KHHLDNN",
    "KHLDDNN",
    "KLDNNNN",
    "KLDNNNN",
]
CORNER_FIELD = [            # Farbfelder (Rot, Grün · 0, Schwarz): olivbrauner Rand
    "...KKKK",
    "..KKLLL",
    ".KLLLDD",
    "KKLNNNN",
    "KLLNNNN",
    "KLDNNNN",
    "KLDNNNN",
]
CASINO_BOXES = {
    # Kopf: unten (letzte 6 Zeilen) sind hell und dunkel vertauscht, das wirkt wie eine Fase
    "casino": dict(corner=CORNER_CASINO_HEAD, straight=6, edge="KLD",
                   colors={"L": "FFF25A", "D": "E49E0A", "N": "6E0008"},
                   bottom={"L": "DE9508", "D": "F0CB2C"}),
    "felt": dict(corner=CORNER_GOLD_FRAME, straight=5, edge="KLD",
                 colors={"L": "F8C412", "D": "0A1A08", "N": "015426"},
                 glow={"tl": "F6E592", "tr": "F2D560", "bl": "EEC23A", "br": "C88600"}),
    "casino_dark": dict(corner=CORNER_GOLD_FRAME, straight=5, edge="KLD",
                        colors={"L": "F6BE12", "D": "120C08", "N": "1B1511"},
                        glow={"tl": "F6E592", "tr": "F2D560", "bl": "EEC23A", "br": "C88600"}),
    "field_red": dict(corner=CORNER_FIELD, straight=5, edge="KLD",
                      colors={"L": "6C5A2C", "D": "473716", "N": "901826"}, first="A91F31", last="8B1A24"),
    "field_green": dict(corner=CORNER_FIELD, straight=5, edge="KLD",
                        colors={"L": "6C5A2C", "D": "433813", "N": "15723B"}, first="1E8749", last="17703A"),
    "field_black": dict(corner=CORNER_FIELD, straight=5, edge="KLD",
                        colors={"L": "665628", "D": "47381C", "N": "222126"}, first="2A292E", last="232024"),
}
ALL_BOXES = list(BOXES) + list(CASINO_BOXES)

# Knöpfe 22 px: (Rand, Füllung oben hell, Füllung, Schatten 1, Schatten 2) oder Umriss-Knopf (Rand, Füllung)
BUTTONS = {
    "teal": ("fill", "33D0B5", "2AB9AA", "22998E", "1C7972", "145D5C"),
    "green": ("fill", "4BD466", "3CB750", "2D9D44", "1F7A33", "0E5F1F"),
    "purple": ("fill", "B060F6", "974FDD", "8442BA", "6A3496", "562A77"),
    "gold": ("fill", "FFD75A", "F7C53C", "E2A82A", "B47E16", "8A5E0C"),
    "red": ("fill", "FF6474", "E8384A", "C42536", "961A28", "6E101C"),
    "dark": ("dark", "868A90", "121316"),
    "gold_line": ("line", "FAC642", "241C07"),
    "brown_line": ("line", "CB843E", "1C1205"),
    "teal_line": ("line", "2ECEC1", "0A2226"),
    "purple_line": ("line", "AF56F8", "1B0E26"),
    "green_line": ("line", "3FD55C", "0A2410"),
    "red_line": ("line", "FF4D5E", "2A0A10"),
    "navy_line": ("line", "2DAEF9", "061B28"),
}

# Kleine Knöpfe 16 px (Casino: Reiter, Zähler, "Wählen", "Letzte Einsätze wiederholen"):
# Rand L, innen D (oben/seitlich), Db (unten), Füllung N, optional top2 = zweite Glanzzeile oben, side = D seitlich
BUTTONS16 = {
    "s_dark": dict(L="5A5A62", D="3C3D44", Db="3C3D44", N="0F1013"),
    "s_gold": dict(L="F4C13E", D="A37E2A", Db="9E7F2C", N="281E07"),
    "s_gold_on": dict(L="F6C03D", D="CD9C31", top2="C29734", Db="816712", N="A47F26", side="B4861E"),
    "s_gold_gray": dict(L="FAC23E", D="B3842D", Db="A08029", N="232327"),
    "s_green": dict(L="24AC5F", D="158C46", top2="1A8849", Db="0A5E28", N="19733D", side="177033"),
}
SMALL_H = 16
CORNER_SMALL = [
    "...KK",
    ".KKLL",
    ".KLDD",
    "KLDNN",
    "KLDNN",
]


def draw_small(w, h, style):
    spec = BUTTONS16[style]
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    for y in range(h):
        for x in range(w):
            ch = corner_px(CORNER_SMALL, x, y, w, h)
            if ch is None:
                d = min(x, y, w - 1 - x, h - 1 - y)
                ch = "KLD"[d] if d < 3 else "N"
            if ch == ".":
                continue
            if ch == "K":
                px[x, y] = K
                continue
            col = spec[ch]
            if ch == "D":
                if y >= h - 3:
                    col = spec["Db"]
                elif y > 2 and "side" in spec:
                    col = spec["side"]
            elif ch == "N" and y == 3 and "top2" in spec:
                col = spec["top2"]
            px[x, y] = rgb(col)
    return img


# Fortschrittsbalken: Füllfarben (oben hell, Mitte, unten)
BAR_FILLS = {
    "gold": ("FFE07A", "F2C230", "C8901A"),
    "cyan": ("9BE6FF", "4FB8F0", "2A88C8"),
    "green": ("A6F5A0", "55D65A", "2E9A36"),
    "purple": ("E0B0FF", "B060F6", "7A3AB0"),
    "red": ("FFA0A8", "F04656", "A82030"),
}
BAR_H = 7

CORNER_BOX = [
    "....KKK",
    "..KKCCC",
    ".KCCCMM",
    ".KCMMNN",
    "KCCMNNN",
    "KCMNNNN",
    "KCMNNNN",
]
CORNER_BTN = [
    "....KK",
    "..KKBB",
    ".KBBff",
    ".KBfff",
    "KBffff",
    "KBffff",
]
CORNER_DARK = [
    "....KK",
    "..KKBB",
    ".KLKff",
    ".KKfff",
    "KBffff",
    "KBffff",
]


def corner_px(pattern, x, y, w, h):
    """Zeichen des Eckmusters an (x, y) in einem w x h Rechteck, None wenn außerhalb der Ecken."""
    n = len(pattern)
    cx = x if x < n else (w - 1 - x if x >= w - n else None)
    cy = y if y < n else (h - 1 - y if y >= h - n else None)
    if cx is None or cy is None:
        return None
    return pattern[cy][cx]


def draw_box(w, h, style):
    if style in CASINO_BOXES:
        return draw_casino_box(w, h, CASINO_BOXES[style])
    c, m, f = (rgb(v) for v in BOXES[style])
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    for y in range(h):
        for x in range(w):
            ch = corner_px(CORNER_BOX, x, y, w, h)
            if ch is None:
                d = min(x, y, w - 1 - x, h - 1 - y)
                ch = "KCM"[d] if d < 3 else "N"
            px[x, y] = {"K": K, "C": c, "M": m, "N": f, ".": T}[ch]
    return img


def draw_casino_box(w, h, spec):
    corner = spec["corner"]
    edge = spec["edge"]
    n = len(corner)
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    for y in range(h):
        for x in range(w):
            ch = corner_px(corner, x, y, w, h)
            if ch is None:
                d = min(x, y, w - 1 - x, h - 1 - y)
                ch = edge[d] if d < len(edge) else "N"
            if ch == ".":
                continue
            if ch == "K":
                px[x, y] = K
                continue
            col = spec["colors"].get(ch)
            if ch == "H":
                key = ("t" if y < h / 2 else "b") + ("l" if x < w / 2 else "r")
                col = spec["glow"][key]
            if "bottom" in spec and y >= h - (n - 1) and ch in spec["bottom"]:
                col = spec["bottom"][ch]
            if ch == "N":
                if "first" in spec and y == len(edge):
                    col = spec["first"]
                elif "last" in spec and y == h - 1 - len(edge):
                    col = spec["last"]
            px[x, y] = rgb(col)
    return img


def box_straight(style):
    """Erste Zeile, ab der die Kasten-Ecke gerade ist (so weit dürfen Mittelzeilen überlappen)."""
    return CASINO_BOXES[style]["straight"] if style in CASINO_BOXES else 5


def button_rows(style, h):
    """Farbe je Zeile für Rand und Füllung eines Knopfs der Höhe h."""
    spec = BUTTONS[style]
    kind = spec[0]
    border = rgb(spec[1])
    if kind == "fill":
        top, fill, sh1, sh2 = (rgb(v) for v in spec[2:])
        rows = []
        for y in range(h):
            if y in (2, 4):
                rows.append(mix(top, fill, 0.45))
            elif y == 3:
                rows.append(top)
            elif y == h - 4:
                rows.append(sh1)
            elif y == h - 3:
                rows.append(sh2)
            else:
                rows.append(fill)
        return border, rows
    fill = rgb(spec[2])
    rows = []
    for y in range(h):
        if y <= 3:
            rows.append(lighten(fill, 0.04))
        elif y >= h - 5:
            rows.append(darken(fill, 0.08))
        else:
            rows.append(fill)
    return border, rows


def draw_button(w, h, style):
    border, rows = button_rows(style, h)
    pattern = CORNER_DARK if BUTTONS[style][0] == "dark" else CORNER_BTN
    light = lighten(border, 0.35)
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    for y in range(h):
        for x in range(w):
            ch = corner_px(pattern, x, y, w, h)
            if ch is None:
                d = min(x, y, w - 1 - x, h - 1 - y)
                ch = "KB"[d] if d < 2 else "f"
            px[x, y] = {"K": K, "B": border, "L": light, "f": rows[y], ".": T}[ch]
    return img


def draw_bar_bg(w):
    img = Image.new("RGBA", (w, BAR_H), T)
    px = img.load()
    hi, body, lo = rgb("3C3E44"), rgb("232428"), rgb("1A1B1E")
    for y in range(BAR_H):
        for x in range(w):
            edge = x == 0 or x == w - 1 or y == 0 or y == BAR_H - 1
            corner = (x in (0, w - 1)) and (y in (0, BAR_H - 1))
            if corner:
                continue
            if edge:
                px[x, y] = K
            else:
                px[x, y] = hi if y == 1 else (lo if y == BAR_H - 2 else body)
    return img


def draw_bar_fill(w, name):
    top, mid, bot = (rgb(v) for v in BAR_FILLS[name])
    img = Image.new("RGBA", (w, BAR_H - 2), T)
    px = img.load()
    for y in range(BAR_H - 2):
        for x in range(w):
            px[x, y] = top if y == 0 else (bot if y == BAR_H - 3 else mid)
    return img


# =====================================================================================================================
# Kleine Symbole in den Textschriften (weiß, werden mit der Textfarbe eingefärbt). Überschreiben Vanilla-Zeichen
# oder ergänzen fehlende (✦ ✓ ✗ ✱ ➜ ...). Oberste Zeile = Oberkante von Großbuchstaben.
# =====================================================================================================================
MINI = {
    "✦": ["...X...", "...X...", "..XXX..", "XXXXXXX", "..XXX..", "...X...", "...X..."],      # ✦
    "✓": [".......", "......X", ".....XX", "X...XX.", "XX.XX..", ".XXX...", "..X...."],      # ✓
    "✗": ["X....X", "XX..XX", ".XXXX.", "..XX..", ".XXXX.", "XX..XX", "X....X"],               # ✗
    "✱": ["...X...", "X..X..X", ".X.X.X.", "..XXX..", ".X.X.X.", "X..X..X", "...X..."],      # ✱
    "➜": ["...X...", "...XX..", "XXXXXX.", "XXXXXXX", "XXXXXX.", "...XX..", "...X..."],      # ➜
    "⤨": [".....X.", "XX..XXX", "..XX.X.", "...X...", "..XX.X.", "XX..XXX", ".....X."],      # ⤨ Mischen
    "↻": ["..XXX..", ".X...XX", "X....XX", "X......", "X.....X", ".X...X.", "..XXX.."],      # ↻
    "✿": ["..X.X..", ".XXXXX.", "XXX.XXX", ".X...X.", "XXX.XXX", ".XXXXX.", "..X.X.."],      # ✿
    "▸": ["X...", "XX..", "XXX.", "XXXX", "XXX.", "XX..", "X..."],                             # ▸
    "◂": ["...X", "..XX", ".XXX", "XXXX", ".XXX", "..XX", "...X"],                             # ◂
    "☘": [".XX.XX.", "XXXXXXX", ".XXXXX.", "XXXXXXX", ".XX.XX.", "...X...", "..X...."],      # ☘
    "♈": ["XX...XX", "X.X.X.X", "...X...", "...X...", "...X...", "...X...", "...X..."],      # ♈
    "›": ["", "X...", ".X..", "..X.", ".X..", "X..."],                                         # ›
    "‹": ["", "...X", "..X.", ".X..", "..X.", "...X"],                                         # ‹
    "⏭": ["X...X..X", "XX..XX.X", "XXX.XXXX", "XXXXXXXX", "XXX.XXXX", "XX..XX.X", "X...X..X"],  # ⏭
    # Small-Caps-Umlaute (Canvas.caps): ᴀ, ᴏ, ᴜ mit Punkten
    "\ue0f0": [".X.X.", ".....", ".XXX.", "X...X", "XXXXX", "X...X", "X...X"],
    "\ue0f1": [".X.X.", ".....", ".XXX.", "X...X", "X...X", "X...X", ".XXX."],
    "\ue0f2": [".X.X.", ".....", "X...X", "X...X", "X...X", "X...X", ".XXX."],
}
MINI_ORDER = list(MINI)


def mini_sheet():
    cell_w, cell_h = 9, 9
    img = Image.new("RGBA", (cell_w * len(MINI_ORDER), cell_h), T)
    for i, ch in enumerate(MINI_ORDER):
        for y, row in enumerate(MINI[ch]):
            for x, c in enumerate(row):
                if c == "X":
                    img.putpixel((i * cell_w + x, y), (255, 255, 255, 255))
    return img, cell_w, cell_h


# =====================================================================================================================
# Breiten der Vanilla-Schrift (für Canvas.width)
# =====================================================================================================================
def vanilla_providers(vroot):
    with open(os.path.join(vroot, "assets/minecraft/font/include/default.json"), encoding="utf-8") as f:
        return json.load(f)["providers"]


def bitmap_advances(img, rows, height):
    """Vorschub je Zeichen wie BitmapProvider: rechteste deckende Spalte + 1, skaliert, + 1."""
    img = img.convert("RGBA")
    cw = img.width // len(rows[0])
    ch = img.height // len(rows)
    scale = height / ch
    out = {}
    a = img.getchannel("A").load()
    for r, row in enumerate(rows):
        for c, char in enumerate(row):
            if char == "\u0000":
                continue
            w = 0
            for x in range(cw - 1, -1, -1):
                if any(a[c * cw + x, r * ch + y] for y in range(ch)):
                    w = x + 1
                    break
            out[char] = int(0.5 + w * scale) + 1
    return out


def text_widths(vroot):
    widths = {" ": 4, "‌": 0}
    for p in vanilla_providers(vroot):
        path = os.path.join(vroot, "assets", "minecraft", "textures", p["file"].split(":", 1)[1])
        adv = bitmap_advances(Image.open(path), p["chars"], p.get("height", 8))
        for k, v in adv.items():
            widths.setdefault(k, v)
    img, cw, _ = mini_sheet()
    for i, chx in enumerate(MINI_ORDER):
        w = max((len(r.rstrip(".")) for r in MINI[chx]), default=0)
        widths[chx] = w + 1
    return widths


# =====================================================================================================================
# Hintergrund-Stücke
# =====================================================================================================================
class Sheet:
    """Zeichen-Raster mit gleich großen Zellen; ein Provider je Schrift."""

    def __init__(self, name, cell_w, cell_h, first_char):
        self.name, self.cw, self.ch, self.first = name, cell_w, cell_h, first_char
        self.cells = []          # (key, image)

    def add(self, key, img):
        assert img.width <= self.cw and img.height <= self.ch, (key, img.size)
        self.cells.append((key, img))
        return chr(self.first + len(self.cells) - 1)

    def char(self, key):
        for i, (k, _) in enumerate(self.cells):
            if k == key:
                return chr(self.first + i)
        raise KeyError(key)

    def advance(self, key):
        for k, img in self.cells:
            if k == key:
                bbox = img.getchannel("A").getbbox()
                return (bbox[2] if bbox else 0) + 1
        raise KeyError(key)

    def render(self, cols=8):
        rows = (len(self.cells) + cols - 1) // cols
        img = Image.new("RGBA", (self.cw * cols, self.ch * rows), T)
        chars = []
        for r in range(rows):
            line = ""
            for c in range(cols):
                i = r * cols + c
                if i < len(self.cells):
                    img.alpha_composite(self.cells[i][1], (c * self.cw, r * self.ch))
                    line += chr(self.first + i)
                else:
                    line += "\u0000"
            chars.append(line)
        return img, chars


def build_pieces():
    caps9 = Sheet("caps9", CAP, ROW, 0xE400)
    segs9 = Sheet("segs9", SEG, ROW, 0xE500)
    caps22 = Sheet("caps22", CAP, BTN_H, 0xE600)
    segs22 = Sheet("segs22", SEG, BTN_H, 0xE700)
    misc = Sheet("misc", 24, BTN_H, 0xE780)
    caps16 = Sheet("caps16", CAP, SMALL_H, 0xE800)
    segs16 = Sheet("segs16", SEG, SMALL_H, 0xE880)

    # Kästen: Zeilen oben/mitte/unten aus einem Beispielkasten 48 x 27
    for style in ALL_BOXES:
        full = draw_box(CAP + SEG + CAP, 3 * ROW, style)
        for r, row in enumerate(("top", "mid", "bot")):
            y = r * ROW
            caps9.add(("box", style, row, "l"), full.crop((0, y, CAP, y + ROW)))
            segs9.add(("box", style, row, "m"), full.crop((CAP, y, CAP + SEG, y + ROW)))
            caps9.add(("box", style, row, "r"), full.crop((CAP + SEG, y, 2 * CAP + SEG, y + ROW)))
    # Knöpfe 22 px; schmale Mittelstücke (16, 8, 4, 2, 1) für kurze Knöpfe
    for style in BUTTONS:
        full = draw_button(CAP + SEG + CAP, BTN_H, style)
        caps22.add(("btn", style, "l"), full.crop((0, 0, CAP, BTN_H)))
        segs22.add(("btn", style, "m"), full.crop((CAP, 0, CAP + SEG, BTN_H)))
        caps22.add(("btn", style, "r"), full.crop((CAP + SEG, 0, 2 * CAP + SEG, BTN_H)))
        for w in NARROW:
            (segs22 if w > CAP else caps22).add(("btn", style, w), full.crop((CAP, 0, CAP + w, BTN_H)))
    # Kleine Knöpfe 16 px
    for style in BUTTONS16:
        full = draw_small(CAP + SEG + CAP, SMALL_H, style)
        caps16.add(("small", style, "l"), full.crop((0, 0, CAP, SMALL_H)))
        segs16.add(("small", style, "m"), full.crop((CAP, 0, CAP + SEG, SMALL_H)))
        caps16.add(("small", style, "r"), full.crop((CAP + SEG, 0, 2 * CAP + SEG, SMALL_H)))
        for w in NARROW:
            (segs16 if w > CAP else caps16).add(("small", style, w), full.crop((CAP, 0, CAP + w, SMALL_H)))
    # Balken: Hintergrund (Ecken + Mittelstücke) und Füllung in Breiten 1, 2, 4, 8, 16, 32
    bg = draw_bar_bg(CAP + SEG + CAP)
    caps9.add(("bar", "l"), bg.crop((0, 0, CAP, BAR_H)))
    segs9.add(("bar", "m"), bg.crop((CAP, 0, CAP + SEG, BAR_H)))
    caps9.add(("bar", "r"), bg.crop((CAP + SEG, 0, 2 * CAP + SEG, BAR_H)))
    for w in NARROW:
        (segs9 if w > CAP else caps9).add(("bar", w), bg.crop((CAP, 0, CAP + w, BAR_H)))
    for name in BAR_FILLS:
        for w in (1, 2, 4, 8):
            caps9.add(("fill", name, w), draw_bar_fill(w, name))
        for w in (16, 32):
            segs9.add(("fill", name, w), draw_bar_fill(w, name))
    # Zurück-Knopf 20 x 20 mit Pfeil
    back = draw_button(20, 20, "dark")
    for y, row in enumerate(["...X", "..X.", ".X..", "X...", ".X..", "..X.", "...X"]):
        for x, c in enumerate(row):
            if c == "X":
                back.putpixel((8 + x, 6 + y), rgb("E6E6EA"))
    misc.add(("back",), back)
    return [caps9, segs9, caps22, segs22, misc, caps16, segs16]


# =====================================================================================================================
# Icons: Vanilla-Item-Texturen (direkt referenziert) und eigene Bilder
# =====================================================================================================================
# Name -> Vanilla-Textur (minecraft:item/...) ; je Icon gibt es 16 px (1x) und 32 px (2x)
VANILLA_ICONS = {
    "rod": "item/fishing_rod.png", "book": "item/enchanted_book.png", "knowledge_book": "item/knowledge_book.png",
    "emerald": "item/emerald.png", "gold_helmet": "item/golden_helmet.png",
    "cod": "item/cod.png", "salmon": "item/salmon.png", "tropical": "item/tropical_fish.png",
    "puffer": "item/pufferfish.png", "gold_ingot": "item/gold_ingot.png", "gold_nugget": "item/gold_nugget.png",
    "diamond": "item/diamond.png", "amethyst": "item/amethyst_shard.png", "bundle": "item/bundle.png",
    "paper": "item/paper.png", "map": "item/map.png", "compass": "item/compass_00.png",
    "clock": "item/clock_00.png", "nautilus": "item/nautilus_shell.png", "heart_sea": "item/heart_of_the_sea.png",
    "prismarine": "item/prismarine_crystals.png", "string": "item/string.png", "spider_eye": "item/spider_eye.png",
    "glow_ink": "item/glow_ink_sac.png", "ink": "item/ink_sac.png", "experience": "item/experience_bottle.png",
    "music_disc": "item/music_disc_cat.png", "disc_13": "item/music_disc_13.png", "name_tag": "item/name_tag.png",
    "totem": "item/totem_of_undying.png", "star": "item/nether_star.png", "writable": "item/writable_book.png",
    "barrier": "item/barrier.png", "iron_ingot": "item/iron_ingot.png", "hopper": "item/hopper.png",
    "sunflower": "block/sunflower_front.png", "lily": "block/lily_pad.png",
    "rabbit_foot": "item/rabbit_foot.png", "feather": "item/feather.png", "book_plain": "item/book.png",
}


def build_icons(vroot):
    """Liefert [(name, file, size)]; size 16 oder 32. Eigene Icons kommen später dazu (Fische usw.)."""
    icons = []
    for name, path in VANILLA_ICONS.items():
        if vroot and not os.path.exists(os.path.join(vroot, "assets/minecraft/textures", path)):
            print("WARNUNG: Vanilla-Textur fehlt", path)
            continue
        icons.append((name, "minecraft:" + path))
    return icons


def icon_advances(vroot, file):
    """Vorschub bei 16 px und 32 px: rechteste deckende Spalte + 1, skaliert, + 1."""
    ns, p = file.split(":", 1)
    img = Image.open(os.path.join(vroot, "assets", ns, "textures", p)).convert("RGBA")
    img = img.crop((0, 0, img.width, img.width))           # animierte Texturen: erstes Bild
    bbox = img.getchannel("A").getbbox()
    w = bbox[2] if bbox else 0
    return int(0.5 + w * 16 / img.width) + 1, int(0.5 + w * 32 / img.width) + 1


# =====================================================================================================================
# Schriften schreiben
# =====================================================================================================================
def write_font(fdir, name, providers):
    path = os.path.join(fdir, name + ".json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"providers": providers}, f, ensure_ascii=True, separators=(",", ":"))


TITLE_GOLD, TITLE_WHITE = 0xEE00, 0xEF00
SMALL_FIRST, SMALL_CELL = 0xED00, 20
BIG_FIRST = 0xED80          # eigene 32-px-Icons: ED80 + i (32 px), EDC0 + i (16 px)


def build(out, spaces, vroot, own_icons=(), small_icons=(), big_icons=()):
    """own_icons: [(name, Bild 16 x 16)] aus build_fish.py, build_angel_icons.py und build_casino.py.
    small_icons: [(name, Bild bis 20 x 20)] in Originalgröße (Chips, Sterne, Noten).
    big_icons: [(name, Bild 32 x 32)] in Originalgröße (Roulette-Rad, Jukebox, Schallplatte)."""
    tex = os.path.join(out, "assets/kuby/textures/k")
    fdir = os.path.join(out, "assets/kuby/font/k")
    shutil.rmtree(tex, ignore_errors=True)
    shutil.rmtree(fdir, ignore_errors=True)
    os.makedirs(tex)
    os.makedirs(fdir)
    space_prov = {"type": "space", "advances": dict(spaces)}
    text_space = {"type": "space", "advances": {**spaces, " ": 4, "‌": 0}}

    # ---- Textschriften ----
    mimg, mcw, mch = mini_sheet()
    mimg.save(os.path.join(tex, "mini.png"))
    vprov = vanilla_providers(vroot)
    for s in T_SHIFTS:
        providers = [text_space, {"type": "bitmap", "file": "kuby:k/mini.png", "height": mch, "ascent": 7 - s,
                                  "chars": ["".join(MINI_ORDER)]}]
        for p in vprov:
            q = dict(p)
            base = p["ascent"]
            q["ascent"] = base - s
            providers.append(q)
        write_font(fdir, "t%d" % s, providers)

    # ---- Hintergrund ----
    sheets = build_pieces()
    sheet_files = []
    for sh in sheets:
        img, chars = sh.render()
        img.save(os.path.join(tex, sh.name + ".png"))
        sheet_files.append((sh, chars))
    for s in B_SHIFTS:
        providers = [space_prov]
        for sh, chars in sheet_files:
            asc = 7 - s
            if asc > sh.ch:
                continue
            providers.append({"type": "bitmap", "file": "kuby:k/%s.png" % sh.name, "height": sh.ch, "ascent": asc,
                              "chars": chars})
        write_font(fdir, "b%d" % s, providers)

    # ---- Icons ----
    # Vanilla-Texturen einzeln referenziert, eigene Icons in einem Bogen (ein Provider je Schrift und Größe)
    icons = build_icons(vroot)
    icon_chars = {}
    icon_adv = {}
    for i, (name, file) in enumerate(icons):
        icon_chars[name] = (chr(0xE900 + 2 * i), chr(0xE901 + 2 * i))    # 16 px, 32 px
        icon_adv[name] = icon_advances(vroot, file)
    cols = 16
    rows = max(1, (len(own_icons) + cols - 1) // cols)
    own_sheet = Image.new("RGBA", (16 * cols, 16 * rows), T)
    own16 = []
    own32 = []
    for i, (name, img) in enumerate(own_icons):
        own_sheet.alpha_composite(img, ((i % cols) * 16, (i // cols) * 16))
        icon_chars[name] = (chr(0xEA00 + i), chr(0xEC00 + i))
        bbox = img.getchannel("A").getbbox()
        w = bbox[2] if bbox else 0
        icon_adv[name] = (w + 1, 2 * w + 1)
    for r in range(rows):
        own16.append("".join(chr(0xEA00 + r * cols + c) if r * cols + c < len(own_icons) else "\u0000"
                             for c in range(cols)))
        own32.append("".join(chr(0xEC00 + r * cols + c) if r * cols + c < len(own_icons) else "\u0000"
                             for c in range(cols)))
    if own_icons:
        own_sheet.save(os.path.join(tex, "icons.png"))
    # kleine Icons (12 px Zellen) und große eigene Icons (32 px)
    small_chars = []
    small_data = []
    if small_icons:
        scols = 16
        srows = (len(small_icons) + scols - 1) // scols
        simg = Image.new("RGBA", (SMALL_CELL * scols, SMALL_CELL * srows), T)
        for i, (name, img) in enumerate(small_icons):
            assert img.width <= SMALL_CELL and img.height <= SMALL_CELL, name
            simg.alpha_composite(img, ((i % scols) * SMALL_CELL, (i // scols) * SMALL_CELL))
            bbox = img.getchannel("A").getbbox()
            small_data.append((name, chr(SMALL_FIRST + i), (bbox[2] if bbox else 0) + 1,
                               bbox[3] if bbox else 0))
        for r in range(srows):
            small_chars.append("".join(chr(SMALL_FIRST + r * scols + c) if r * scols + c < len(small_icons)
                                       else "\u0000" for c in range(scols)))
        simg.save(os.path.join(tex, "small.png"))
    if big_icons:
        bimg = Image.new("RGBA", (32 * len(big_icons), 32), T)
        for i, (name, img) in enumerate(big_icons):
            bimg.alpha_composite(img, (32 * i, 0))
        bimg.save(os.path.join(tex, "icons32.png"))
    # Titelschrift
    gimg, title_adv, tcols, trows = build_title.sheet(build_title.GOLD)
    wimg, _, _, _ = build_title.sheet(build_title.WHITE)
    gimg.save(os.path.join(tex, "title_gold.png"))
    wimg.save(os.path.join(tex, "title_white.png"))
    title_gold_chars = build_title.chars(TITLE_GOLD, tcols, trows)
    title_white_chars = build_title.chars(TITLE_WHITE, tcols, trows)
    for s in I_SHIFTS:
        providers = [space_prov]
        asc = 7 - s
        for name, file in icons:
            c16, c32 = icon_chars[name]
            if asc <= 16:
                providers.append({"type": "bitmap", "file": file, "height": 16, "ascent": asc, "chars": [c16]})
            if asc <= 32:
                providers.append({"type": "bitmap", "file": file, "height": 32, "ascent": asc, "chars": [c32]})
        if own_icons:
            if asc <= 16:
                providers.append({"type": "bitmap", "file": "kuby:k/icons.png", "height": 16, "ascent": asc,
                                  "chars": own16})
            if asc <= 32:
                providers.append({"type": "bitmap", "file": "kuby:k/icons.png", "height": 32, "ascent": asc,
                                  "chars": own32})
        if small_icons and asc <= SMALL_CELL:
            providers.append({"type": "bitmap", "file": "kuby:k/small.png", "height": SMALL_CELL, "ascent": asc,
                              "chars": small_chars})
        if big_icons:
            if asc <= 32:
                providers.append({"type": "bitmap", "file": "kuby:k/icons32.png", "height": 32, "ascent": asc,
                                  "chars": ["".join(chr(BIG_FIRST + i) for i in range(len(big_icons)))]})
            if asc <= 16:
                providers.append({"type": "bitmap", "file": "kuby:k/icons32.png", "height": 16, "ascent": asc,
                                  "chars": ["".join(chr(BIG_FIRST + 0x40 + i) for i in range(len(big_icons)))]})
        if asc <= build_title.CELL_H:
            providers.append({"type": "bitmap", "file": "kuby:k/title_gold.png", "height": build_title.CELL_H,
                              "ascent": asc, "chars": title_gold_chars})
            providers.append({"type": "bitmap", "file": "kuby:k/title_white.png", "height": build_title.CELL_H,
                              "ascent": asc, "chars": title_white_chars})
        write_font(fdir, "i%d" % s, providers)
    icons = icons + [(name, None) for name, _ in own_icons]
    for i, (name, img) in enumerate(big_icons):
        icon_chars[name] = (chr(BIG_FIRST + 0x40 + i), chr(BIG_FIRST + i))
        bbox = img.getchannel("A").getbbox()
        w = bbox[2] if bbox else 0
        icon_adv[name] = (int(0.5 + w / 2) + 1, w + 1)
    icons = icons + [(name, None) for name, _ in big_icons]

    # Titelschrift auch als eigene Schrift kuby:title (Hologramme, Titel): Versal-Unterkante auf der Grundlinie
    title_font = {"providers": [{"type": "space", "advances": {" ": build_title.SPACE_ADV}},
                                {"type": "bitmap", "file": "kuby:k/title_gold.png", "height": build_title.CELL_H,
                                 "ascent": 15, "chars": title_gold_chars},
                                {"type": "bitmap", "file": "kuby:k/title_white.png", "height": build_title.CELL_H,
                                 "ascent": 15, "chars": title_white_chars}]}
    with open(os.path.join(out, "assets/kuby/font/title.json"), "w", encoding="utf-8") as f:
        json.dump(title_font, f, ensure_ascii=True, separators=(",", ":"))

    widths = text_widths(vroot)
    write_java(sheets, widths, icons, icon_chars, icon_adv, small_data, title_adv)
    n_fonts = len(T_SHIFTS) + len(B_SHIFTS) + len(I_SHIFTS)
    print("ok kit: %d Schriften, %d Stücke, %d Icons, %d Textzeichen" % (
        n_fonts, sum(len(s.cells) for s in sheets), len(icons), len(widths)))


# =====================================================================================================================
# Java-Daten
# =====================================================================================================================
def jchar(c):
    return "'\\u%04x'" % ord(c)


def jstr(s):
    """Java-String; Unicode-Escapes werden vor dem Lesen ersetzt, darum " und \\ nie als \\u-Escape."""
    out = []
    for c in s:
        if c == '"':
            out.append('\\"')
        elif c == "\\":
            out.append("\\\\")
        elif 0x20 <= ord(c) < 0x7F:
            out.append(c)
        else:
            out.append("\\u%04x" % ord(c))
    return '"' + "".join(out) + '"'


def write_java(sheets, widths, icons, icon_chars, icon_adv, small_data=(), title_adv=()):
    by = {}
    for sh in sheets:
        for i, (key, img) in enumerate(sh.cells):
            bbox = img.getchannel("A").getbbox()
            by[key] = (chr(sh.first + i), (bbox[2] if bbox else 0) + 1, img.width, img.height)

    L = []
    L.append("package de.kuby.kubyplugins.ui;")
    L.append("")
    L.append("// ERZEUGT von tools/build_kit.py - nicht von Hand ändern.")
    L.append("")
    L.append("/** Zeichen, Vorschübe und Schrift-Verschiebungen des Dialog-Kits (Schriften kuby:k/...). */")
    L.append("public final class KitData {")
    L.append("    private KitData() {")
    L.append("    }")
    L.append("")
    L.append("    public static final int T_MAX = %d;" % max(T_SHIFTS))
    L.append("    public static final int B_MAX = %d;" % B_MAX)
    L.append("    public static final int I_MIN = %d;" % I_MIN)
    L.append("    public static final int I_MAX = %d;" % I_MAX)
    L.append("    public static final int SEG = %d;" % SEG)
    L.append("    public static final int CAP = %d;" % CAP)
    L.append("    public static final int ROW = %d;" % ROW)
    L.append("    public static final int BTN_H = %d;" % BTN_H)
    L.append("    public static final int SMALL_H = %d;" % SMALL_H)
    L.append("    public static final int BAR_H = %d;" % BAR_H)
    L.append("")
    L.append("    /** Ein Hintergrund-Stück: Zeichen, Vorschub (Breite + 1), sichtbare Breite und Höhe. */")
    L.append("    public record Piece(char c, int advance, int width, int height) {}")
    L.append("")

    def piece(key):
        c, adv, w, h = by[key]
        return "new Piece(%s, %d, %d, %d)" % (jchar(c), adv, w, h)

    L.append("    /** Kasten-Stile (Rahmen aus Zeilen zu 9 px). */")
    L.append("    public enum Box {")
    rows = []
    for style in ALL_BOXES:
        parts = []
        for row in ("top", "mid", "bot"):
            for side, kind in (("l", "l"), ("m", "m"), ("r", "r")):
                parts.append(piece(("box", style, row, side)))
        rows.append("        %s(%d, %s)" % (style.upper(), box_straight(style), ",\n                ".join(parts)))
    L.append(",\n".join(rows) + ";")
    L.append("")
    L.append("        /** [Zeile oben/mitte/unten][links/mitte/rechts] */")
    L.append("        public final Piece[][] p;")
    L.append("        /** Ab dieser Zeile ist die Ecke gerade: so weit dürfen Mittelzeilen in die obere und untere ragen. */")
    L.append("        public final int straight;")
    L.append("")
    L.append("        Box(int straight, Piece... all) {")
    L.append("            this.straight = straight;")
    L.append("            p = new Piece[][]{{all[0], all[1], all[2]}, {all[3], all[4], all[5]}, {all[6], all[7], all[8]}};")
    L.append("        }")
    L.append("    }")
    L.append("")
    L.append("    /** Knopf-Stile (22 px hoch). */")
    L.append("    public enum Button {")
    rows = []
    for style in BUTTONS:
        narrow = ", ".join(piece(("btn", style, w)) for w in NARROW)
        rows.append("        %s(%s, %s, %s,\n                %s)" % (
            style.upper(), piece(("btn", style, "l")), piece(("btn", style, "m")), piece(("btn", style, "r")), narrow))
    L.append(",\n".join(rows) + ";")
    L.append("")
    L.append("        public final Piece l, m, r;")
    L.append("        /** Schmale Mittelstücke 16, 8, 4, 2, 1 px. */")
    L.append("        public final Piece[] narrow;")
    L.append("")
    L.append("        Button(Piece l, Piece m, Piece r, Piece... narrow) {")
    L.append("            this.l = l;")
    L.append("            this.m = m;")
    L.append("            this.r = r;")
    L.append("            this.narrow = narrow;")
    L.append("        }")
    L.append("    }")
    L.append("")
    L.append("    /** Kleine Knöpfe (16 px hoch). */")
    L.append("    public enum Small {")
    rows = []
    for style in BUTTONS16:
        narrow = ", ".join(piece(("small", style, w)) for w in NARROW)
        rows.append("        %s(%s, %s, %s,\n                %s)" % (
            style[2:].upper(), piece(("small", style, "l")), piece(("small", style, "m")),
            piece(("small", style, "r")), narrow))
    L.append(",\n".join(rows) + ";")
    L.append("")
    L.append("        public final Piece l, m, r;")
    L.append("        public final Piece[] narrow;")
    L.append("")
    L.append("        Small(Piece l, Piece m, Piece r, Piece... narrow) {")
    L.append("            this.l = l;")
    L.append("            this.m = m;")
    L.append("            this.r = r;")
    L.append("            this.narrow = narrow;")
    L.append("        }")
    L.append("    }")
    L.append("")
    L.append("    /** Balken-Füllfarben; Stücke in den Breiten 1, 2, 4, 8, 16, 32. */")
    L.append("    public enum Fill {")
    rows = []
    for name in BAR_FILLS:
        rows.append("        %s(%s)" % (name.upper(), ", ".join(piece(("fill", name, w)) for w in (1, 2, 4, 8, 16, 32))))
    L.append(",\n".join(rows) + ";")
    L.append("")
    L.append("        public final Piece[] p;")
    L.append("")
    L.append("        Fill(Piece... p) {")
    L.append("            this.p = p;")
    L.append("        }")
    L.append("    }")
    L.append("")
    L.append("    public static final Piece BAR_L = %s;" % piece(("bar", "l")))
    L.append("    public static final Piece BAR_M = %s;" % piece(("bar", "m")))
    L.append("    public static final Piece BAR_R = %s;" % piece(("bar", "r")))
    L.append("    public static final Piece[] BAR_NARROW = {%s};" % ", ".join(piece(("bar", w)) for w in NARROW))
    L.append("    public static final Piece BACK = %s;" % piece(("back",)))
    L.append("")
    L.append("    /** Icons: Zeichen und Vorschub für 16 px und 32 px (Schriften kuby:k/i<s>). */")
    L.append("    public enum Icon {")
    rows = []
    for name, _ in icons:
        c16, c32 = icon_chars[name]
        a16, a32 = icon_adv[name]
        rows.append("        %s(%s, %d, %s, %d)" % (name.upper(), jchar(c16), a16, jchar(c32), a32))
    L.append(",\n".join(rows) + ";")
    L.append("")
    L.append("        public final char c16, c32;")
    L.append("        public final int adv16, adv32;")
    L.append("")
    L.append("        Icon(char c16, int adv16, char c32, int adv32) {")
    L.append("            this.c16 = c16;")
    L.append("            this.adv16 = adv16;")
    L.append("            this.c32 = c32;")
    L.append("            this.adv32 = adv32;")
    L.append("        }")
    L.append("    }")
    L.append("")
    L.append("    /** Icons in Originalgröße (Zellen 20 x 20, links oben): Zeichen, Vorschub, sichtbare Höhe. */")
    L.append("    public enum SmallIcon {")
    rows = []
    for name, c, adv, hgt in small_data:
        rows.append("        %s(%s, %d, %d)" % (name.upper(), jchar(c), adv, hgt))
    L.append(",\n".join(rows) + ";" if rows else "        ;")
    L.append("")
    L.append("        public final char c;")
    L.append("        public final int advance, height;")
    L.append("")
    L.append("        SmallIcon(char c, int advance, int height) {")
    L.append("            this.c = c;")
    L.append("            this.advance = advance;")
    L.append("            this.height = height;")
    L.append("        }")
    L.append("    }")
    L.append("")
    L.append("    public static final int SMALL_CELL = %d;" % SMALL_CELL)
    L.append("")
    L.append("    /** Titelschrift (Schriften kuby:k/i<s> und kuby:title): Zeichen in TITLE_CHARS, Glyphe = Start + Index. */")
    L.append("    public static final String TITLE_CHARS = %s;" % jstr("".join(build_title.ORDER)))
    L.append("    public static final String TITLE_ADV = %s;" % jstr("".join(chr(0x30 + a) for a in title_adv)))
    L.append("    public static final char TITLE_GOLD = %s, TITLE_WHITE = %s;" % (jchar(chr(TITLE_GOLD)),
                                                                              jchar(chr(TITLE_WHITE))))
    L.append("    public static final int TITLE_H = %d, TITLE_TOP = %d, TITLE_CAP = %d;" % (
        build_title.CELL_H, build_title.TOP, build_title.CAP_H))
    L.append("")
    # nur Zeichen aus der Basis-Ebene (ein Java-char); sonst stimmt die Sortierung für die Binärsuche nicht
    chars = sorted(c for c in widths if ord(c) <= 0xFFFF)
    L.append("    /** Textzeichen der Schriften kuby:k/t<s> (sortiert) und ihr Vorschub ohne Fett. */")
    chunk = 120
    parts = [jstr("".join(chars[i:i + chunk])) for i in range(0, len(chars), chunk)]
    L.append("    static final String TEXT_CHARS = " + "\n            + ".join(parts) + ";")
    vals = "".join(chr(0x30 + widths[c]) for c in chars)
    parts = ['"' + vals[i:i + chunk].replace("\\", "\\\\").replace('"', '\\"') + '"' for i in range(0, len(vals), chunk)]
    L.append("    static final String TEXT_ADV = " + "\n            + ".join(parts) + ";")
    L.append("}")
    L.append("")
    os.makedirs(os.path.dirname(JAVA_OUT), exist_ok=True)
    with open(JAVA_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(L))


if __name__ == "__main__":
    out_dir = sys.argv[1]
    vanilla = sys.argv[2]
    neg = {chr(0xF801 + i): -(1 << i) for i in range(10)}
    pos = {chr(0xF811 + i): (1 << i) for i in range(10)}
    build(out_dir, {**neg, **pos}, vanilla)
