#!/usr/bin/env python3
"""Große Kategorie-Titel im Stil der GUI-Referenzen (SUPPORT, QUESTS, JUWELENSHOP, SPIELERWARPS, BATTLE PASS).

Buchstaben 15 px hoch, Striche 4 px, Lücken 2 px, runde Ecken abgeschrägt (wie die Referenz-Titel). Gezeichnet wie in
den Referenzen: Oberkanten 2 px hell (pastell), Körper satt, unten ein dunklerer Streifen (Quests: Gelb -> Orange),
1 px fast schwarze Kontur, darunter ein dunkler Schatten im Farbton.

Je Farbe ein Bild assets/kuby/textures/k/head_<farbe>.png (Raster 16 x 4 Zellen à 18 x 22 px, Zeile 4 = Versal-Oberkante,
darüber Platz für Umlaut-Punkte). Schriften kuby:k/h<s> (s = -15..8, Oberkante der Zelle = Zeile * 9 + s) mit je einer
Quelle pro Farbe: Zeichen E000 + Farbe * 0x80 + Nummer. Für den Code: ui/HeadlineData.java (Zeichen, Vorschübe, Farben).

Aufruf: python3 tools/build_headline.py
"""
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
PACK = os.path.join(REPO, "resourcepack/KubyUI")
JAVA = os.path.join(REPO, "KubyCore-v2.0.14/KubyPlugins/src/main/java/de/kuby/kubyplugins/ui/HeadlineData.java")

CELL_W, CELL_H, TOP = 18, 22, 4          # TOP: Zeile der Versal-Oberkante in der Zelle
COLS, ROWS = 16, 4

G = {
    "A": ["..XXXXXX..", ".XXXXXXXX.", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXXXXXXXX",
          "XXXXXXXXXX", "XXXXXXXXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX"],
    "B": ["XXXXXXXX..", "XXXXXXXXX.", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXXXXXXX.", "XXXXXXXXX.",
          "XXXXXXXXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXXXXXXXX", "XXXXXXXXXX", "XXXXXXXXX.", "XXXXXXXX.."],
    "C": ["..XXXXXXXX", ".XXXXXXXXX", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX......", "XXXX......", "XXXX......", "XXXX......",
          "XXXX......", "XXXX......", "XXXX......", "XXXXXXXXXX", "XXXXXXXXXX", ".XXXXXXXXX", "..XXXXXXXX"],
    "D": ["XXXXXXXX..", "XXXXXXXXX.", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX",
          "XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXXXXXXXX", "XXXXXXXXXX", "XXXXXXXXX.", "XXXXXXXX.."],
    "E": ["XXXXXXXXX"] * 4 + ["XXXX....."] * 2 + ["XXXXXXX.."] * 3 + ["XXXX....."] * 2 + ["XXXXXXXXX"] * 4,
    "F": ["XXXXXXXXX"] * 4 + ["XXXX....."] * 2 + ["XXXXXXX.."] * 3 + ["XXXX....."] * 6,
    "G": ["..XXXXXXXX", ".XXXXXXXXX", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX......", "XXXX......", "XXXX.XXXXX", "XXXX.XXXXX",
          "XXXX.XXXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXXXXXXXX", "XXXXXXXXXX", ".XXXXXXXXX", "..XXXXXXXX"],
    "H": ["XXXX..XXXX"] * 6 + ["XXXXXXXXXX"] * 3 + ["XXXX..XXXX"] * 6,
    "I": ["XXXX"] * 15,
    "J": ["......XXXX"] * 9 + ["XXXX..XXXX"] * 2 + ["XXXXXXXXXX"] * 2 + [".XXXXXXXXX", "..XXXXXXX."],
    "K": ["XXXX..XXXX", "XXXX..XXXX", "XXXX.XXXX.", "XXXX.XXXX.", "XXXXXXXX..", "XXXXXXX...", "XXXXXX....", "XXXXXX....",
          "XXXXXX....", "XXXXXXX...", "XXXXXXXX..", "XXXX.XXXX.", "XXXX.XXXX.", "XXXX..XXXX", "XXXX..XXXX"],
    "L": ["XXXX...."] * 11 + ["XXXXXXXX"] * 4,
    "M": ["XXXX.....XXXX", "XXXXX...XXXXX", "XXXXXX.XXXXXX", "XXXXXXXXXXXXX", "XXXXXXXXXXXXX", "XXXX.XXX.XXXX",
          "XXXX..X..XXXX"] + ["XXXX.....XXXX"] * 8,
    "N": ["XXXX...XXXX", "XXXXX..XXXX", "XXXXXX.XXXX", "XXXXXXXXXXX", "XXXXXXXXXXX", "XXXX.XXXXXX", "XXXX..XXXXX"]
         + ["XXXX...XXXX"] * 8,
    "O": ["..XXXXXX..", ".XXXXXXXX.", "XXXXXXXXXX", "XXXXXXXXXX"] + ["XXXX..XXXX"] * 7 + ["XXXXXXXXXX", "XXXXXXXXXX", ".XXXXXXXX.", "..XXXXXX.."],
    "P": ["XXXXXXXX..", "XXXXXXXXX.", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXXXXXXXX",
          "XXXXXXXXX.", "XXXXXXXX.."] + ["XXXX......"] * 5,
    "Q": ["..XXXXXX...", ".XXXXXXXX..", "XXXXXXXXXX.", "XXXXXXXXXX."] + ["XXXX..XXXX."] * 6
         + ["XXXX.XXXXX.", "XXXXXXXXXX.", "XXXXXXXXXXX", ".XXXXXXXXXX", "..XXXXX.XXX"],
    "R": ["XXXXXXXX..", "XXXXXXXXX.", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXXXXXXX.",
          "XXXXXXXX..", "XXXXXXXXX.", "XXXX.XXXXX"] + ["XXXX..XXXX"] * 4,
    "S": ["..XXXXXXX.", ".XXXXXXXXX", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX..XXXX", "XXXX......", "XXXXXXXX..", ".XXXXXXXXX",
          "..XXXXXXXX", "......XXXX", "XXXX..XXXX", "XXXXXXXXXX", "XXXXXXXXXX", "XXXXXXXXX.", ".XXXXXXX.."],
    "T": ["XXXXXXXXXX"] * 4 + ["...XXXX..."] * 11,
    "U": ["XXXX..XXXX"] * 11 + ["XXXXXXXXXX", "XXXXXXXXXX", ".XXXXXXXX.", "..XXXXXX.."],
    "V": ["XXXX..XXXX"] * 9 + [".XXXXXXXX.", ".XXXXXXXX.", "..XXXXXX..", "..XXXXXX..", "...XXXX...", "...XXXX..."],
    "W": ["XXXX.....XXXX"] * 6 + ["XXXX..X..XXXX", "XXXX.XXX.XXXX"] + ["XXXXXXXXXXXXX"] * 4
         + ["XXXXXX.XXXXXX", "XXXXX...XXXXX", "XXXX.....XXXX"],
    "X": ["XXXX..XXXX"] * 3 + [".XXXXXXXX."] * 2 + ["..XXXXXX.."] * 5 + [".XXXXXXXX."] * 2 + ["XXXX..XXXX"] * 3,
    "Y": ["XXXX..XXXX"] * 5 + ["XXXXXXXXXX", ".XXXXXXXX.", ".XXXXXXXX."] + ["...XXXX..."] * 7,
    "Z": ["XXXXXXXXXX"] * 4 + [".....XXXXX", "....XXXXX."] + ["...XXXXX.."] * 3 + ["..XXXXX...", ".XXXXX...."] + ["XXXXXXXXXX"] * 4,
    "0": ["..XXXXXX..", ".XXXXXXXX.", "XXXXXXXXXX", "XXXXXXXXXX"] + ["XXXX..XXXX"] * 7 + ["XXXXXXXXXX", "XXXXXXXXXX", ".XXXXXXXX.", "..XXXXXX.."],
    "1": ["...XXXX...", "..XXXXX...", ".XXXXXX...", ".XXXXXX..."] + ["...XXXX..."] * 7 + ["XXXXXXXXXX"] * 4,
    "2": ["..XXXXXX..", ".XXXXXXXX.", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX..XXXX", "......XXXX", "....XXXXXX", "..XXXXXXX.",
          ".XXXXXXX..", "XXXXXX....", "XXXX......"] + ["XXXXXXXXXX"] * 4,
    "3": ["XXXXXXXX..", "XXXXXXXXX.", "XXXXXXXXXX", "XXXXXXXXXX", "......XXXX", "......XXXX", "..XXXXXXX.", "..XXXXXXX.",
          "..XXXXXXXX", "......XXXX", "......XXXX", "XXXXXXXXXX", "XXXXXXXXXX", "XXXXXXXXX.", "XXXXXXXX.."],
    "4": ["XXXX..XXXX"] * 7 + ["XXXXXXXXXX"] * 4 + ["......XXXX"] * 4,
    "5": ["XXXXXXXXXX"] * 4 + ["XXXX......"] * 2 + ["XXXXXXXX..", "XXXXXXXXX.", "XXXXXXXXXX", "......XXXX", "......XXXX",
                                                   "XXXXXXXXXX", "XXXXXXXXXX", "XXXXXXXXX.", "XXXXXXXX.."],
    "6": ["..XXXXXXXX", ".XXXXXXXXX", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX......", "XXXX......", "XXXXXXXX..", "XXXXXXXXX.",
          "XXXXXXXXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXXXXXXXX", "XXXXXXXXXX", ".XXXXXXXX.", "..XXXXXX.."],
    "7": ["XXXXXXXXXX"] * 4 + ["......XXXX"] * 2 + [".....XXXX."] * 2 + ["....XXXX.."] * 2 + ["...XXXX..."] * 5,
    "8": ["..XXXXXX..", ".XXXXXXXX.", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX..XXXX", "XXXX..XXXX", ".XXXXXXXX.", ".XXXXXXXX.",
          "XXXXXXXXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXXXXXXXX", "XXXXXXXXXX", ".XXXXXXXX.", "..XXXXXX.."],
    "9": ["..XXXXXX..", ".XXXXXXXX.", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX..XXXX", "XXXX..XXXX", "XXXXXXXXXX", ".XXXXXXXXX",
          "..XXXXXXXX", "......XXXX", "......XXXX", "XXXXXXXXXX", "XXXXXXXXXX", "XXXXXXXXX.", "XXXXXXXX.."],
    "-": ["......"] * 6 + ["XXXXXX"] * 3 + ["......"] * 6,
    ".": ["...."] * 11 + ["XXXX"] * 4,
    ",": ["...."] * 11 + ["XXXX"] * 3 + ["XX.."],
    ":": ["...."] * 3 + ["XXXX"] * 4 + ["...."] * 3 + ["XXXX"] * 4 + ["...."],
    "!": ["XXXX"] * 10 + ["...."] + ["XXXX"] * 4,
    "?": ["..XXXXXX..", ".XXXXXXXX.", "XXXXXXXXXX", "XXXXXXXXXX", "XXXX..XXXX", "......XXXX", "....XXXXX.", "...XXXXX..",
          "...XXXX...", "...XXXX...", ".........."] + ["...XXXX..."] * 4,
    "'": ["XXXX"] * 5 + ["...."] * 10,
    "+": [".........."] * 3 + ["...XXXX..."] * 3 + ["XXXXXXXXXX"] * 3 + ["...XXXX..."] * 3 + [".........."] * 3,
    "/": ["......XXXX", "......XXXX", ".....XXXX.", ".....XXXX.", "....XXXX..", "....XXXX..", "...XXXX...", "...XXXX...",
          "..XXXX....", "..XXXX....", ".XXXX.....", ".XXXX.....", "XXXX......", "XXXX......", "XXXX......"],
    "#": ["..XX..XX.."] * 3 + ["XXXXXXXXXX"] * 3 + ["..XX..XX.."] * 3 + ["XXXXXXXXXX"] * 3 + ["..XX..XX.."] * 3,
}
UMLAUT = {"Ä": "A", "Ö": "O", "Ü": "U"}
ORDER = list(G) + list(UMLAUT)
SPACE_ADV = 7
assert len(ORDER) < 0x80

# Farben: hi = Glanz an Oberkanten, bands = Körperfarbe je Zeile (15), outline, shadow
def bands(*spec):
    out = []
    for n, col in spec:
        out += [col] * n
    assert len(out) == 15, len(out)
    return out


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


PALETTES = [
    # Name, Glanz, Körper je Zeile, Kontur, Schatten
    ("gold", "FBF930", bands((4, "FBF930"), (4, "FBCD01"), (4, "FBA101"), (3, "FB7700")), "010100", "412212"),
    ("white", "FFFFFF", bands((10, "FBFBFB"), (5, "D5D4D4")), "010101", "121014"),
    ("purple", "E0A2F8", bands((13, "8F29FA"), (2, "7A1FE0")), "0A0516", "230C4C"),
    ("cyan", "64F9E6", bands((13, "01E6C3"), (2, "00D2B2")), "000101", "0E1418"),
    ("green", "61F853", bands((13, "01CE18"), (2, "00B418")), "000000", "09140A"),
    ("sky", "B8E6FF", bands((13, "3DA9F7"), (2, "2A8AE0")), "020A14", "0A1E33"),
    ("red", "FFB0A8", bands((13, "F2443A"), (2, "CC2E28")), "120202", "3A0B0B"),
    ("pink", "FFC4EA", bands((13, "F25AC2"), (2, "D23FA2")), "140410", "3C0C30"),
    ("orange", "FFD58A", bands((5, "FFB03A"), (8, "FB8A1E"), (2, "E06A10")), "120700", "3A1C08"),
    ("blue", "BCC2FF", bands((13, "5B63F5"), (2, "4048D8")), "04051A", "141640"),
    ("gray", "F0F4F8", bands((13, "AEB9C6"), (2, "8E9AA8")), "05070A", "1A1E24"),
]


def mask_of(ch):
    if ch in UMLAUT:
        base = G[UMLAUT[ch]]
        return base, True
    return G[ch], False


def render(ch, pal):
    _, hi, rows, outline, shadow = pal
    hi, outline, shadow = hx(hi), hx(outline), hx(shadow)
    rows = [hx(c) for c in rows]
    m, uml = mask_of(ch)
    w = len(m[0])
    body = set()
    for y, row in enumerate(m):
        for x, c in enumerate(row):
            if c == "X":
                body.add((x + 1, y + TOP))
    if uml:
        for dx in (1, w - 3):
            for yy in (1, 2):
                for xx in (dx, dx + 1):
                    body.add((xx + 1, yy))
    img = Image.new("RGBA", (CELL_W, CELL_H), (0, 0, 0, 0))
    px = img.load()
    # Kontur (8 Nachbarn) und Schatten (eine Zeile unter Körper und Kontur)
    edge = set()
    for (x, y) in body:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in body:
                    edge.add(q)
    shade = set()
    for (x, y) in body | edge:
        q = (x, y + 1)
        if q not in body and q not in edge:
            shade.add(q)
    for (x, y) in shade:
        if 0 <= x < CELL_W and 0 <= y < CELL_H:
            px[x, y] = shadow + (255,)
    for (x, y) in edge:
        if 0 <= x < CELL_W and 0 <= y < CELL_H:
            px[x, y] = outline + (255,)
    for (x, y) in body:
        r = y - TOP
        col = rows[max(0, min(14, r))]
        above1 = (x, y - 1) in body
        above2 = (x, y - 2) in body
        if not above1:
            col = hi if r <= 4 else rows[max(0, r - 4)] if rows[max(0, r - 4)] != col else hi
        elif not above2 and r <= 4:
            col = hi
        if r < 0:      # Umlaut-Punkte
            col = hi if (x, y - 1) not in body else rows[0]
        px[x, y] = col + (255,)
    return img


def advance(img):
    bbox = img.getchannel("A").getbbox()
    return (bbox[2] if bbox else 0) + 1


def main():
    tex = os.path.join(PACK, "assets/kuby/textures/k")
    fdir = os.path.join(PACK, "assets/kuby/font/k")
    os.makedirs(tex, exist_ok=True)
    advs = None
    for pi, pal in enumerate(PALETTES):
        sheet = Image.new("RGBA", (COLS * CELL_W, ROWS * CELL_H), (0, 0, 0, 0))
        a = []
        for i, ch in enumerate(ORDER):
            img = render(ch, pal)
            sheet.paste(img, ((i % COLS) * CELL_W, (i // COLS) * CELL_H))
            a.append(advance(img))
        sheet.save(os.path.join(tex, "head_%s.png" % pal[0]), optimize=True)
        advs = advs or a
    with open(os.path.join(fdir, "i0.json"), encoding="utf-8") as f:
        space = [p for p in json.load(f)["providers"] if p["type"] == "space"]
    for s in range(7 - CELL_H, 9):
        providers = list(space)
        for pi, pal in enumerate(PALETTES):
            first = 0xE000 + pi * 0x80
            grid = []
            for r in range(ROWS):
                grid.append("".join(chr(first + r * COLS + c) if r * COLS + c < len(ORDER) else "\u0000" for c in range(COLS)))
            providers.append({"type": "bitmap", "file": "kuby:k/head_%s.png" % pal[0], "height": CELL_H, "ascent": 7 - s, "chars": grid})
        with open(os.path.join(fdir, "h%d.json" % s), "w", encoding="utf-8") as f:
            json.dump({"providers": providers}, f, ensure_ascii=True, separators=(",", ":"))
    # Daten für den Code
    with open(JAVA, "w", encoding="utf-8") as f:
        f.write("package de.kuby.kubyplugins.ui;\n\n")
        f.write("/** Erzeugt von tools/build_headline.py - nicht von Hand ändern. */\n")
        f.write("final class HeadlineData {\n\n    private HeadlineData() {\n    }\n\n")
        f.write("    static final int CELL_H = %d, TOP = %d, SPACE = %d, MIN_SHIFT = %d;\n" % (CELL_H, TOP, SPACE_ADV, 7 - CELL_H))
        f.write("    static final String CHARS = \"%s\";\n" % "".join("\\u%04X" % ord(c) if ord(c) > 127 else c for c in ORDER))
        f.write("    static final int[] ADV = {%s};\n" % ", ".join(str(x) for x in advs))
        f.write("    static final String[] PALETTES = {%s};\n" % ", ".join('"%s"' % p[0] for p in PALETTES))
        f.write("}\n")
    print("ok Titel:", len(ORDER), "Zeichen,", len(PALETTES), "Farben, Schriften k/h%d..k/h8" % (7 - CELL_H))


if __name__ == "__main__":
    main()
