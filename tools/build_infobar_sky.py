#!/usr/bin/env python3
"""Info-Leiste für Skyblock: vier eigene Rahmen (Insel, Wolke, Nether, End) und Symbole.

Gleicher Aufbau wie die Survival-Leiste (tools/assets/infobar, extract_infobar.py): je Design ein Mittelstück
(strip_body_<d>.png, 18 hoch, jede Spalte gleich), linkes und rechtes Ende (art_cap_l/r_<d>.png, cap breit, mit
Verzierung up px über und down px unter dem Mittelstück), dazu art.json mit den Maßen. Das rechte Ende ist das
gespiegelte linke, Licht bleibt dabei oben links (Licht- und Schattenzeichen werden beim Spiegeln getauscht).

Symbole: art_hud_<name>.png (8 x 8, in der Leiste oben, aus den Scoreboard-Symbolen) und art_el_<name>.png
(Element-Liste in /bossbar, 12 x 12).

Ausgabe: tools/assets/infobar_sky (Präfix ibs_ im Dialog-Kit, build_jewelshop.SETS; Leisten-Zeichen in
build_sb_pack.build_infobar). Aufruf: python3 tools/build_infobar_sky.py
"""
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "assets", "infobar_sky")
DESIGNS = ("insel", "wolke", "nether", "ende")


def h(c):
    c = c.lstrip("#")
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255)


# ---------------------------------------------------------------------------------------------------------------------
# Insel: Grasblock-Pfosten mit schwebender Steinunterseite, Blume oben. Dunkle Erde innen, Gras oben, Erde unten.
# ---------------------------------------------------------------------------------------------------------------------
INSEL = {
    "dark": True, "up": 4, "down": 5,
    "body": ["1B2A0E", "86C339", "4F6910", "15100A", "2A2017", "2A2017", "2A2017", "2A2017", "2A2017", "2A2017",
             "2A2017", "2A2017", "2A2017", "2A2017", "2A2017", "7B633D", "52422A", "241608"],
    "pal": {"O": "1B2A0E", "o": "241608", "L": "A2DE1B", "G": "78AE13", "g": "4B670D", "h": "917C52",
            "D": "7B633D", "d": "52422A", "k": "52422A", "e": "362616", "T": "A4A9AF", "S": "7C8187",
            "s": "4F535A", "z": "393E44", "r": "E52220", "R": "FA4B2D", "y": "F6BB57", "v": "5E8927"},
    "swap": {"h": "k", "k": "h"},
    "cap": [
        "..R.........",
        ".ryr........",
        "..v....L.G..",
        "OOOOOOOOOO..",
        "OLLLLLLLLO##",
        "OLGGGGGGGO##",
        "OGGGGGgGGO##",
        "OgGggGgggO##",
        "ohgDdgDdgo##",
        "ohDDdDDgko##",
        "ohDDDDDDko##",
        "oDDDeDDDko##",
        "ohDDDDDDko##",
        "oDTSDDDDko##",
        "ohSsDDDDko##",
        "oDDDDDDDko##",
        "ohDDDDeDko##",
        "oDDDDDDDko##",
        "ohDDDDDDko##",
        "oDDDDDDDko##",
        "ohdddddddo##",
        "oddddddddo##",
        "oTTSSSSszo..",
        ".oSSSSszo...",
        "..osSszo....",
        "...oszo.....",
        "....oo......",
    ],
}

# ---------------------------------------------------------------------------------------------------------------------
# Wolke: helle Himmelsleiste, am Ende ein Wolkenballen mit kleinem goldenen Funkeln.
# ---------------------------------------------------------------------------------------------------------------------
WOLKE = {
    "dark": False, "up": 4, "down": 3,
    "body": ["2D3846", "FFFFFF", "B8E0F7", "7BB1F0", "E4F3FC", "E4F3FC", "E4F3FC", "E4F3FC", "E4F3FC", "E4F3FC",
             "E4F3FC", "E4F3FC", "E4F3FC", "E4F3FC", "E4F3FC", "96C0F3", "4C7FC0", "2D3846"],
    "pal": {"O": "2D3846", "W": "FFFFFF", "w": "E9F3FB", "b": "C9DCF0", "B": "96B4D8", "n": "6E8CB8",
            "Y": "FFE27A", "y": "F6BB57"},
    "swap": {},
    "cap": None,  # aus Kreisen, siehe cloud_rows()
}

# ---------------------------------------------------------------------------------------------------------------------
# Nether: Schwarzstein-Säule mit Goldnieten und Flamme oben, Magma tropft unten. Innen dunkel, Glut am unteren Rand.
# ---------------------------------------------------------------------------------------------------------------------
NETHER = {
    "dark": True, "up": 7, "down": 3,
    "body": ["1A0606", "9B362D", "6B271F", "120707", "1F0D0B", "1F0D0B", "1F0D0B", "1F0D0B", "1F0D0B", "1F0D0B",
             "1F0D0B", "1F0D0B", "1F0D0B", "1F0D0B", "3A140C", "EC7301", "A63001", "1A0606"],
    "pal": {"O": "120E14", "K": "1E1A20", "k": "35303A", "M": "4F4A55", "m": "6A6470", "Y": "FFE27A",
            "y": "F6BB57", "a": "EC7301", "A": "AC3200", "r": "823200", "G": "FFD24A", "g": "C67218"},
    "swap": {"m": "k", "k": "m"},
    "cap": [
        "....y.......",
        "...yY.......",
        "...YYa......",
        "..aYYaA.....",
        "..AaYaA.....",
        ".OOAaAOOO...",
        ".OmMMMMkO...",
        "OOGOOOOGOO##",
        "OmMMMMMMkO##",
        "OmMmMMMMkO##",
        "OmMMMMMMkO##",
        "OKkKKkKKKO##",
        "OmMMMMMMkO##",
        "OmMMMmMMkO##",
        "OmMMMMMMkO##",
        "OKKkKKKkKO##",
        "OmMMMMMMkO##",
        "OmMMMMMMkO##",
        "OmMMmMMMkO##",
        "OKkKKKKkKO##",
        "OmMMMMMMkO##",
        "OmMMMMMMkO##",
        "OaMMMMMMaO##",
        "OOGOOOOGOO##",
        "..OaOOaO##..",
        "...r..A.....",
        "......r.....",
        "............",
    ],
}

# ---------------------------------------------------------------------------------------------------------------------
# Ende: Purpur-Säule mit Enderauge oben, Endstein-Sockel. Innen fast schwarz-violett, oben Endstein, unten Purpur.
# ---------------------------------------------------------------------------------------------------------------------
ENDE = {
    "dark": True, "up": 7, "down": 3,
    "body": ["120B18", "E2E4A8", "B5B47A", "0C0712", "170E1F", "170E1F", "170E1F", "170E1F", "170E1F", "170E1F",
             "170E1F", "170E1F", "170E1F", "170E1F", "24152E", "A77BA7", "73487B", "120B18"],
    "pal": {"O": "120B18", "P": "C9A0C9", "p": "A77BA7", "q": "8D5599", "Q": "5C3D62", "E": "E2E4A8",
            "e": "B5B47A", "f": "8A8A5A", "c": "3FD3B8", "C": "1E8C7A", "x": "0A2A26", "W": "E8FFF8"},
    "swap": {"P": "q", "q": "P"},
    "cap": [
        "...OOOO.....",
        "..OcWcCO....",
        ".OcWcCCxO...",
        ".OcCxxCxO...",
        ".OCCxCCxO...",
        "..OCCxxO....",
        ".OOOOOOOO...",
        "OEEEEEEEfO##",
        "OeEEEEEefO##",
        "OOOOOOOOOO##",
        "OPppppppqO##",
        "OPpQppQpqO##",
        "OPpQppQpqO##",
        "OPpQppQpqO##",
        "OPpQppQpqO##",
        "OPpQppQpqO##",
        "OPpQppQpqO##",
        "OPpQppQpqO##",
        "OPpQppQpqO##",
        "OPppppppqO##",
        "OOOOOOOOOO##",
        "OEEEEEEEfO##",
        "OeEEEEEEfO##",
        "OeeeeeeefO##",
        "OOOOOOOOOO##",
        "..O....O....",
        "............",
        "............",
    ],
}


def cloud_rows(width=12, up=4, down=3, right=False):
    """Wolkenballen aus drei Kreisen, Licht von oben links: Weiß oben, Blau unten rechts, 1 px Kontur."""
    circles = ((5.0, 6.2, 4.7), (3.5, 12.8, 3.8), (5.4, 18.8, 4.6), (6.0, 12.6, 3.4))
    if right:
        # rechtes Ende: Kreise gespiegelt, Licht bleibt oben links
        circles = tuple((width - cx, cy, r) for cx, cy, r in circles)
    hgt = up + 18 + down
    lx, ly = -0.55, -0.83

    def cover(x, y):
        # Licht des Kreises, in dem der Punkt am tiefsten liegt (gibt die Falten zwischen den Ballen)
        best, depth = None, 2.0
        for cx, cy, r in circles:
            dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
            q = dx * dx + dy * dy
            if q <= 1.0 and q < depth:
                best, depth = dx * lx + dy * ly, q
        return best

    grid = [["." for _ in range(width)] for _ in range(hgt)]
    for y in range(hgt):
        for x in range(width):
            v = cover(x, y)
            if v is None:
                continue
            grid[y][x] = "W" if v > 0.3 else "w" if v > -0.2 else "b" if v > -0.6 else "B"
    for y in range(hgt):
        for x in range(width):
            if grid[y][x] == ".":
                continue
            edge = False
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if (nx < 0) if right else (nx >= width):
                    continue  # zur Mitte hin geht es ins Mittelstück über
                if not (0 <= nx < width and 0 <= ny < hgt) or cover(nx, ny) is None:
                    edge = True
            if edge:
                grid[y][x] = "O"
    # rechts vom Wolkenballen geht das Mittelstück weiter
    for y in range(up, up + 18):
        for x in (range(width) if right else range(width - 1, -1, -1)):
            if grid[y][x] != ".":
                break
            grid[y][x] = "#"
    # goldenes Funkeln oben außen
    sx = width - 2 if right else 1
    for x, y, c in ((sx, 0, "y"), (sx - 1, 1, "y"), (sx, 1, "Y"), (sx + 1, 1, "y"), (sx, 2, "y")):
        if grid[y][x] == ".":
            grid[y][x] = c
    return ["".join(r) for r in grid]


WOLKE["cap"] = cloud_rows()
WOLKE["cap_r"] = cloud_rows(right=True)

ART = {"insel": INSEL, "wolke": WOLKE, "nether": NETHER, "ende": ENDE}


def cap_image(d, mirror=False):
    rows = d["cap"]
    cap = len(rows[0])
    up = d["up"]
    hgt = up + 18 + d["down"]
    rows = (rows + ["." * cap] * hgt)[:hgt]
    im = Image.new("RGBA", (cap, hgt), (0, 0, 0, 0))
    pal = {k: h(v) for k, v in d["pal"].items()}
    body = [h(c) for c in d["body"]]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch == "#":
                r = y - up
                if 0 <= r < 18:
                    im.putpixel((x, y), body[r])
                continue
            if mirror:
                ch = d["swap"].get(ch, ch)
            im.putpixel((x, y), pal[ch])
    if mirror:
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    return im


def mirror_cap(d):
    """Rechtes Ende: gespiegelt, Licht- und Schattenzeichen getauscht (oder eigens gezeichnet: cap_r)."""
    if d.get("cap_r"):
        own = dict(d)
        own["cap"] = d["cap_r"]
        return cap_image(own)
    swapped = dict(d)
    swapped["cap"] = ["".join(d["swap"].get(c, c) for c in row) for row in d["cap"]]
    swapped["swap"] = {}
    return cap_image(swapped, mirror=True)


def body_image(d, width=257):
    im = Image.new("RGBA", (width, 18), (0, 0, 0, 0))
    for y, c in enumerate(d["body"]):
        for x in range(width):
            im.putpixel((x, y), h(c))
    return im


# ---------------------------------------------------------------------------------------------------------------------
# Symbole
# ---------------------------------------------------------------------------------------------------------------------
def stamp(rows, pal):
    im = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                im.putpixel((x, y), h(pal[ch]))
    return im


# 8 x 8 für die Leiste oben (wie die Scoreboard-Symbole; Insel und Generator aus build_sb_pack.SKY_ICONS)
HUD = {
    "island": (["..gGGg..", ".gGLLGg.", "gGGGGGGg", "dDDDDDDd", ".sSSSSs.", "..sSSs..", "...ss...", "........"],
               {"g": "3C8A1E", "G": "6CC048", "L": "A8E070", "d": "6A4222", "D": "9A6438", "s": "6A6A74", "S": "9A9AA4"}),
    "generator": (["oOOOOOOo", "o.k..k.o", "oKkYYkKo", "o.kYYk.o", "oKkYYkKo", "o.k..k.o", "oOOOOOOo", "........"],
                  {"o": "2A2E38", "O": "6E7A90", "k": "3C4352", "K": "8E9AB2", "Y": "FFD24A"}),
    "xp": (["..oooo..", ".oLGGgo.", "oLGYGGgo", "oGYWYGgo", "oGGYGggo", "ogGGggdo", ".oggddo.", "..oooo.."],
           {"o": "1F3A0A", "L": "D8FF7A", "G": "8CE03A", "g": "4FA21C", "d": "2F6E12", "Y": "E8FF9A", "W": "FFFFFF"}),
}

# 12 x 12 für die Element-Liste in /bossbar
EL = {
    "island": ([
        "....OOOO....",
        "..OOgGLgOO..",
        ".OgGGLLGGgO.",
        "OgGGGGGGGGgO",
        "OdDhDDDDDDdO",
        "OdDDDDeDDDdO",
        ".OddDDDDddO.",
        "..OsSSSSsO..",
        "...OsSSsO...",
        "....OssO....",
        ".....OO.....",
        "............"],
        {"O": "1F1A12", "g": "3C8A1E", "G": "6CC048", "L": "A8E070", "d": "6A4222", "D": "9A6438", "h": "B8844E",
         "e": "4A2E16", "s": "5A5A64", "S": "9A9AA4"}),
    "xp": ([
        "....OOOO....",
        "..OOLGGgOO..",
        ".OLGGGGGggO.",
        ".OGGYGGGggO.",
        "OLGYWYGGgggO",
        "OGGGYGGGgggO",
        "OGGGGGGggddO",
        "OgGGGGgggddO",
        ".OggggggddO.",
        ".OgggggdddO.",
        "..OOddddOO..",
        "....OOOO...."],
        {"O": "1F3A0A", "L": "D8FF7A", "G": "8CE03A", "g": "4FA21C", "d": "2F6E12", "Y": "E8FF9A", "W": "FFFFFF"}),
    "gen": ([
        "OOOOOOOOOOOO",
        "OGOkKOOKkOGO",
        "OOk.kYYk.kOO",
        "Ok.kYwwYk.kO",
        "OK.kYwwYk.KO",
        "Ok.kyYYyk.kO",
        "OK.kyYYyk.KO",
        "Ok..kyyk..kO",
        "OOk.kkkk.kOO",
        "OGOkKOOKkOGO",
        "OOOOOOOOOOOO",
        "............"],
        {"O": "23262E", "K": "8E9AB2", "k": "4A5266", "G": "FFD24A", "Y": "FFB43A", "y": "D8761A",
         "w": "FFF2B0"}),
}


def build(out=OUT):
    os.makedirs(out, exist_ok=True)
    meta = {"design": {}, "art": {}}
    for name in DESIGNS:
        d = ART[name]
        cap = len(d["cap"][0])
        for row in d["cap"]:
            assert len(row) == cap, (name, row)
        body_image(d).save(os.path.join(out, "strip_body_%s.png" % name))
        cap_image(d).save(os.path.join(out, "art_cap_l_%s.png" % name))
        mirror_cap(d).save(os.path.join(out, "art_cap_r_%s.png" % name))
        meta["design"][name] = {"cap": cap, "up": d["up"], "down": d["down"], "dark": d["dark"]}
    for name, (rows, pal) in HUD.items():
        stamp(rows, pal).save(os.path.join(out, "art_hud_%s.png" % name))
        meta["art"]["hud_" + name] = {"w": 8, "h": 8, "y": 0}
    for name, (rows, pal) in EL.items():
        stamp(rows, pal).save(os.path.join(out, "art_el_%s.png" % name))
    with open(os.path.join(out, "art.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=1, sort_keys=True)
    return meta


if __name__ == "__main__":
    m = build()
    print("ok", len(m["design"]), "Designs")
