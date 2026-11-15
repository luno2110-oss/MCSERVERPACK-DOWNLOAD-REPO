"""Skins für Figuren (Mannequins mit Textur aus dem Pack, z. B. kuby:entity/casino/croupier).

64 x 64, Casino-Figuren mit schlanken Armen (3 px), Priester und Bankier (kuby:entity/survival/...) mit breiten
Armen (4 px). Jedes Körperteil wird je Seite aus Zeilen-Mustern gemalt. Eigene Zeichnungen im Stil der Referenz
(Croupier: rosa Haare, Dienstmädchen-Kleid).
"""
import os

from PIL import Image

T = (0, 0, 0, 0)


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# Lage der Flächen im 64x64-Skin: Teil -> Seite -> (x, y, Breite, Höhe)
def part_uv(x, y, w, h, d):
    """Würfel mit Breite w, Höhe h, Tiefe d ab (x, y): Flächen wie im Minecraft-Skin."""
    return {
        "top": (x + d, y, w, d), "bottom": (x + d + w, y, w, d),
        "right": (x, y + d, d, h), "front": (x + d, y + d, w, h),
        "left": (x + d + w, y + d, d, h), "back": (x + d + w + d, y + d, w, h),
    }


UV = {
    "head": part_uv(0, 0, 8, 8, 8), "hat": part_uv(32, 0, 8, 8, 8),
    "body": part_uv(16, 16, 8, 12, 4), "jacket": part_uv(16, 32, 8, 12, 4),
    "rarm": part_uv(40, 16, 3, 12, 4), "rsleeve": part_uv(40, 32, 3, 12, 4),
    "larm": part_uv(32, 48, 3, 12, 4), "lsleeve": part_uv(48, 48, 3, 12, 4),
    "rleg": part_uv(0, 16, 4, 12, 4), "rpants": part_uv(0, 32, 4, 12, 4),
    "lleg": part_uv(16, 48, 4, 12, 4), "lpants": part_uv(0, 48, 4, 12, 4),
    # breite Arme (klassisches Modell)
    "rarm4": part_uv(40, 16, 4, 12, 4), "rsleeve4": part_uv(40, 32, 4, 12, 4),
    "larm4": part_uv(32, 48, 4, 12, 4), "lsleeve4": part_uv(48, 48, 4, 12, 4),
}


class Skin:
    def __init__(self, palette):
        self.img = Image.new("RGBA", (64, 64), T)
        self.pal = {k: rgb(v) if isinstance(v, str) else v for k, v in palette.items()}

    def paint(self, part, side, rows):
        """rows: Liste von Strings, ein Zeichen je Pixel ('.' = durchsichtig/unverändert)."""
        x0, y0, w, h = UV[part][side]
        for y, row in enumerate(rows[:h]):
            for x, ch in enumerate(row[:w]):
                if ch == " " or ch == ".":
                    continue
                self.img.putpixel((x0 + x, y0 + y), self.pal[ch])

    def fill(self, part, side, ch):
        x0, y0, w, h = UV[part][side]
        for y in range(h):
            for x in range(w):
                self.img.putpixel((x0 + x, y0 + y), self.pal[ch])

    def column(self, part, side, rows):
        """Ein Zeichen je Zeile, über die ganze Breite."""
        x0, y0, w, h = UV[part][side]
        for y, ch in enumerate(rows[:h]):
            if ch == ".":
                continue
            for x in range(w):
                self.img.putpixel((x0 + x, y0 + y), self.pal[ch])


def croupier():
    s = Skin({
        "H": "F2A2BC", "h": "D97C9C", "d": "B8607E",       # Haare hell, Schatten, dunkel
        "S": "FBE6DE", "s": "EECBC0",                       # Haut
        "E": "3B2638", "e": "8C4F6E", "B": "F5A6AE", "m": "CF7C82",
        "K": "2A2532", "k": "1C1922", "g": "3C3646",        # Kleid
        "W": "F6F4F2", "w": "D9D6DC",                       # Schürze, Kragen
        "R": "D22B44", "r": "9E1C30",                       # Schleife
        "L": "F2F0F6", "l": "D6D2DE",                       # Strümpfe
        "F": "1A171E", "f": "3A3542",                       # Schuhe
    })
    # Kopf
    s.fill("head", "top", "H")
    s.fill("head", "bottom", "s")
    s.paint("head", "front", [
        "HHHHHHHH",
        "HHHHHHHH",
        "HHhHHhHH",
        "HSSSSSSH",
        "SEESSEES",
        "SeESSEeS",
        "SBSmmSBS",
        "sSSSSSSs",
    ])
    for side in ("right", "left"):
        s.paint("head", side, ["HHHHHHHH", "HHHHHHHH", "HHHHHHHh", "HHHHHHhh", "HHHHHHhS", "hHHHHHhS", "hhHHHhhs",
                               "hhhHhhhs"] if side == "right" else
                ["HHHHHHHH", "HHHHHHHH", "hHHHHHHH", "hhHHHHHH", "ShHHHHHH", "ShHHHHHh", "shhHHHhh", "shhhHhhh"])
    s.paint("head", "back", ["HHHHHHHH", "HHHHHHHH", "HHHhHHHH", "HHhHHhHH", "HHHHHHHH", "hHHHHHHh", "hHhHHhHh",
                             "hhHHHHhh"])
    # Haarband (Hut-Schicht vorn oben) und Pony
    s.paint("hat", "front", [
        "WwWwWwWw",
        "WWWWWWWW",
        "HHh..hHH",
        "Hh....hH",
        "H......H",
        "h......h",
        "h......h",
        "d......d",
    ])
    s.paint("hat", "top", ["........", "........", "........", "........", "........", "........", "WWWWWWWW",
                           "wWwWwWwW"])
    s.paint("hat", "right", ["........", "........", "......Hh", ".....hHh", "....hHHh", "....HHhd", "....hHhd",
                             ".....hdd"])
    s.paint("hat", "left", ["........", "........", "hH......", "hHh.....", "hHHh....", "dhHH....", "dhHh....",
                            "ddh....."])
    s.paint("hat", "back", ["........", "........", "........", "........", "HhHHhHHh", "hHhHHhHh", "dhHhhHhd",
                            "ddhHHhdd"])
    # Körper
    s.paint("body", "front", [
        "KWWRRWWK",
        "KKWrrWKK",
        "KKKKKKKK",
        "KWWWWWWK",
        "KWWWWWWK",
        "KWwWWwWK",
        "kKKRRKKk",
        "KWWWWWWK",
        "KWWWWWWK",
        "KWwWWwWK",
        "KWWWWWWK",
        "WkWkWkWk",
    ])
    s.paint("body", "back", [
        "KKKKKKKK",
        "KKKKKKKK",
        "KKKKKKKK",
        "KKKgKKKK",
        "KKKKKKKK",
        "KWWKKWWK",
        "kKWRRWKk",
        "KWWKKWWK",
        "KKKKKKKK",
        "KKgKKgKK",
        "KKKKKKKK",
        "WkWkWkWk",
    ])
    for side in ("right", "left"):
        s.column("body", side, "KKKKKKkKKKKW")
    s.fill("body", "top", "K")
    s.fill("body", "bottom", "K")
    # langes Haar hinten über dem Kleid (Jacken-Schicht)
    s.paint("jacket", "back", [
        "HhHHhHHh",
        "hHHhHHhH",
        "HhHHHhHh",
        "dHhHHhHd",
        ".dhHHhd.",
        "..dhhd..",
    ])
    s.paint("jacket", "front", ["W......W", "........"])
    # Arme: Ärmel schwarz mit weißer Manschette, Hände
    for arm in ("rarm", "larm"):
        for side in ("front", "back", "right", "left"):
            s.column(arm, side, "KKKKKKKKWWSS")
        s.fill(arm, "top", "K")
        s.fill(arm, "bottom", "S")
    for sl in ("rsleeve", "lsleeve"):
        for side in ("front", "back", "right", "left"):
            s.column(sl, side, "gK.........")
    # Beine: Rock, Strümpfe, Schuhe
    for leg in ("rleg", "lleg"):
        for side in ("front", "back", "right", "left"):
            s.column(leg, side, "KKKkWLLLlLFF")
        s.fill(leg, "top", "K")
        s.fill(leg, "bottom", "F")
    # Schürze vorn über beide Beine, Rüschen am Saum, Glanz auf den Schuhen
    s.paint("rleg", "front", ["KWWW", "KWWW", "KWwW", "kWWW", "WkWk"])
    s.paint("lleg", "front", ["WWWK", "WWWK", "WwWK", "WWWk", "WkWk"])
    for leg in ("rleg", "lleg"):
        for side in ("back", "right", "left"):
            s.paint(leg, side, ["", "", "", "", "WkWk"])
    for p in ("rpants", "lpants"):
        s.paint(p, "front", ["", "", "", "", "", "", "", "", "", "", "ffff", ""])
    return s.img


def mia():
    """DJ Mia wie im Clip: lange blonde Haare über den Schultern, weißes Shirt, hellgraue Strickjacke, helle Jeans
    mit Rissen an den Knien, helle Schuhe."""
    s = Skin({
        "H": "F0D48A", "h": "D6B464", "d": "B8944A",       # Haare hell, Schatten, dunkel
        "S": "F7DECB", "s": "EAC6AE",                       # Haut
        "W": "F6F4F2", "E": "7C7888", "e": "4A4656",        # Augen
        "B": "F2BCAC", "m": "E6B4A4",                       # Wangen, Mund
        "T": "F1EFEC", "t": "DCD8D4",                       # Shirt
        "C": "D2D2D8", "c": "B6B6C0", "g": "9C9CA8",        # Strickjacke
        "J": "8EAABC", "j": "7090A6", "k": "AEC6D4",        # Jeans
        "F": "DCDCE0", "f": "B4B4BC",                       # Schuhe
    })
    s.fill("head", "top", "H")
    s.fill("head", "bottom", "s")
    s.paint("head", "front", [
        "HHHHHHHH",
        "HHHhHHHH",
        "HHhHHhHH",
        "HhSSSShH",
        "hWESSEWh",
        "hWeSSeWh",
        "hBSSSSBh",
        "hSSmmSSh",
    ])
    for side in ("right", "left"):
        s.paint("head", side, ["HHHHHHHH", "HHHHHHHH", "HHHhHHHh", "HHhHHHhh", "HHHHHhHS", "hHHHHHhS", "hHhHHhhs",
                               "hhHHHhhs"] if side == "right" else
                ["HHHHHHHH", "HHHHHHHH", "hHHHhHHH", "hhHHHhHH", "ShHhHHHH", "ShHHHHHh", "shhHHhHh", "shhHHHhh"])
    s.paint("head", "back", ["HHHHHHHH", "HHhHHHHH", "HHHHHhHH", "HhHHHHHH", "HHHHhHHH", "hHHHHHHh", "HhHHHHhH",
                             "hHHhhHHh"])
    # Haar-Schicht: Scheitel und Strähnen seitlich
    s.paint("hat", "front", [
        "HHHHHHHH",
        "HHHhHHHH",
        "HH....hH",
        "H......H",
        "h......h",
        "H......H",
        "h......h",
        "H......H",
    ])
    s.paint("hat", "right", ["........", "HHHHHHHH", "HhHHHHhH", "hHHHHhHH", "HHhHHHHh", "HhHHhHHH", "hHHHHhHh",
                             "HHhHHHhH"])
    s.paint("hat", "left", ["........", "HHHHHHHH", "HhHHHHhH", "HHhHHHHh", "hHHHHhHH", "HHHhHHhH", "HhHHHHHh",
                            "hHHhHHHH"])
    s.paint("hat", "back", ["HHHHHHHH", "HHhHHHHH", "HHHHHhHH", "hHHHHHHh", "HHHhHHHH", "HhHHHHhH", "HHHHHHHH",
                            "hHhHHhHh"])
    # Körper: weißes Shirt, Ränder der Strickjacke
    s.paint("body", "front", [
        "CTTTTTTC",
        "CTTTTTTC",
        "CTTTTTTC",
        "CTtTTtTC",
        "CTTTTTTC",
        "CTTTTTTC",
        "cTTTTTTc",
        "CTTtTTTC",
        "CTTTTTTC",
        "CTTTTTTC",
        "cTtTTtTc",
        "CTTTTTTC",
    ])
    s.fill("body", "back", "C")
    s.paint("body", "back", ["", "", "", "", "", "", "", "", "", "", "cCcCcCcC", "CCCCCCCC"])
    for side in ("right", "left"):
        s.column("body", side, "CCCCCCcCCCcC")
    s.fill("body", "top", "C")
    s.fill("body", "bottom", "T")
    # lange Haare vorn über die Schultern und hinten den Rücken hinunter (Jacken-Schicht)
    s.paint("jacket", "front", [
        "HH....HH",
        "hH....Hh",
        "Hh....hH",
        "hH....Hh",
        ".H....h.",
        ".h....H.",
        ".H....h.",
        ".d....d.",
    ])
    s.paint("jacket", "back", [
        "HhHHhHHh",
        "hHHhHHhH",
        "HhHHHhHh",
        "dHhHHhHd",
        "HdhHHhdH",
        ".dHhhHd.",
        "..dhhd..",
    ])
    for side in ("right", "left"):
        s.paint("jacket", side, ["hHHh", "Hd.H", "d..h", "...."])
    # Arme: Strickjacke mit Bündchen, Hände
    for arm in ("rarm", "larm"):
        for side in ("front", "back", "right", "left"):
            s.column(arm, side, "CCCcCCCCcgSS")
        s.fill(arm, "top", "C")
        s.fill(arm, "bottom", "S")
    for sl in ("rsleeve", "lsleeve"):
        for side in ("front", "back", "right", "left"):
            s.column(sl, side, "c...........")
    # Beine: helle Jeans mit Rissen an den Knien, helle Schuhe
    for leg in ("rleg", "lleg"):
        for side in ("front", "back", "right", "left"):
            s.column(leg, side, "JJJJJJJJJjFF")
        s.fill(leg, "top", "J")
        s.fill(leg, "bottom", "f")
    s.paint("rleg", "front", ["JjJJ", "JJJJ", "JJkJ", "JJJJ", "JjJJ", "kSSJ", "JsSk", "JJJJ", "JkJJ", "JJjJ", "FFFF", "fFFf"])
    s.paint("lleg", "front", ["JJjJ", "JJJJ", "JkJJ", "JJJJ", "JJjJ", "JSSk", "kSsJ", "JJJJ", "JJkJ", "JjJJ", "FFFF", "fFFf"])
    for p in ("rpants", "lpants"):
        s.paint(p, "front", ["", "", "", "", "", "", "", "", "", "", "", "ffff"])
    return s.img


def priester():
    """Priester: graue Haare, schwarze Soutane mit weißem Kollar, cremefarbene Stola mit goldenen Kreuzen."""
    s = Skin({
        "G": "C8C8CC", "g": "A4A4AC", "d": "84848E",       # Haare
        "S": "F2D2B8", "s": "DDB89C", "o": "C89C80",       # Haut
        "E": "3A4A6A", "m": "B47C6C",                       # Augen, Mund
        "K": "1E1E24", "k": "141418", "n": "2E2E36",       # Soutane
        "W": "F6F6F2", "w": "D8D8D4",                       # Kollar
        "C": "F2EAD2", "c": "D8CCAE",                       # Stola
        "Y": "E8B830", "y": "B8901C",                       # Gold
        "F": "101014", "f": "34343C",                       # Schuhe
    })
    s.fill("head", "top", "G")
    s.paint("head", "top", ["gGGgGGgG", "GGgGGGGg", "GgGGGgGG", "GGGgGGGG", "gGGGGgGG", "GGgGGGGg", "GGGGgGGG", "dgGGGGgd"])
    s.fill("head", "bottom", "s")
    s.paint("head", "front", [
        "GGgGGgGG",
        "gSSSSSSg",
        "SggSSggS",
        "SWESSEWS",
        "SSSSSSSS",
        "SSSooSSS",
        "SSmmmmSS",
        "sSSSSSSs",
    ])
    s.paint("head", "right", ["GGgGGGGg", "gGGGGGgS", "dGGGGgSS", "dgGGGSSS", "dgGGgSoS", "dgGgSSSS", "ddgSSSSs", "dddssSss"])
    s.paint("head", "left", ["gGGGGgGG", "SgGGGGGg", "SSgGGGGd", "SSSGGGgd", "SoSgGGgd", "SSSSgGgd", "sSSSSgdd", "sssSsddd"])
    s.paint("head", "back", ["GGgGGGgG", "GgGGgGGG", "GGGGGGgG", "gGGgGGGG", "GGGGGGGg", "dgGGgGGd", "ddgGGgdd", "sddggdds"])
    # Körper: Soutane mit Knopfleiste, Kollar oben, Stola vorn
    s.paint("body", "front", [
        "KCKWWKCK",
        "KCKwwKCK",
        "KCKnnKCK",
        "KCKKKKCK",
        "KCKnnKCK",
        "KCKKKKCK",
        "KCKnnKCK",
        "kCKKKKCk",
        "KCKnnKCK",
        "KYKKKKYK",
        "YYYKKYYY",
        "KYKKKKYK",
    ])
    s.paint("body", "back", [
        "KKKKKKKK",
        "KKKnnKKK",
        "KKKKKKKK",
        "KKKKKKKK",
        "KKKKKKKK",
        "KKKKKKKK",
        "kKKKKKKk",
        "KKKKKKKK",
        "KKKKKKKK",
        "KKKKKKKK",
        "KKKKKKKK",
        "KKKKKKKK",
    ])
    for side in ("right", "left"):
        s.column("body", side, "KKKKKKkKKKKK")
    s.fill("body", "top", "K")
    s.paint("body", "top", ["KKKKKKKK", "KKKKKKKK", "KKKWWKKK", "KKKKKKKK"])
    s.fill("body", "bottom", "K")
    # Arme: schwarze Ärmel, weiße Manschette, Hände
    for arm in ("rarm4", "larm4"):
        for side in ("front", "back", "right", "left"):
            s.column(arm, side, "KKKKKKKKKWSS")
        s.fill(arm, "top", "K")
        s.fill(arm, "bottom", "S")
    for sl in ("rsleeve4", "lsleeve4"):
        for side in ("front", "back", "right", "left"):
            s.column(sl, side, "nK..........")
    # Beine: Soutane bis zu den Knöcheln, schwarze Schuhe
    for leg in ("rleg", "lleg"):
        for side in ("front", "back", "right", "left"):
            s.column(leg, side, "KKKKKKKKKkFF")
        s.fill(leg, "top", "K")
        s.fill(leg, "bottom", "F")
    s.paint("rleg", "front", ["", "", "", "", "", "", "", "", "", "", "", "Ffff"])
    s.paint("lleg", "front", ["", "", "", "", "", "", "", "", "", "", "", "ffff"])
    return s.img


def bankier():
    """Bankier: dunkle, nach hinten gekämmte Haare, Schnurrbart, dunkelblauer Anzug, weißes Hemd, rote Krawatte,
    goldene Uhrkette."""
    s = Skin({
        "H": "4A3222", "h": "3A2618", "d": "2A1A10",       # Haare
        "S": "F0CCAE", "s": "DAB092", "o": "C49478",       # Haut
        "E": "3A2A1E", "M": "4A3222", "m": "B47C6C",       # Augen, Bart, Mund
        "N": "1E2A4A", "n": "16203A", "b": "2A3A60",       # Anzug
        "W": "F4F4F0", "w": "D6D6D2",                       # Hemd
        "R": "C8283A", "r": "8E1A28",                       # Krawatte
        "Y": "F0C840",                                      # Uhrkette
        "F": "1A1410", "f": "4A3A30",                       # Schuhe
    })
    s.fill("head", "top", "H")
    s.paint("head", "top", ["hHHhHHHh", "HHhHHhHH", "HHHHHHHH", "HhHHhHHH", "HHHHHHhH", "HHhHHHHH", "hHHHHhHH", "dhHHHHhd"])
    s.fill("head", "bottom", "s")
    s.paint("head", "front", [
        "HHHHHHHH",
        "HHHHHHhH",
        "SddSSddS",
        "SWESSEWS",
        "SSSSSSSS",
        "SSSooSSS",
        "SSMMMMSS",
        "sSSmmSSs",
    ])
    s.paint("head", "right", ["HHHHHHHh", "HHHHHHhh", "dHHHHhSS", "dhHHhSSS", "dhHHSSoS", "dhHhSSSS", "ddhSSSSs", "dddssSss"])
    s.paint("head", "left", ["hHHHHHHH", "hhHHHHHH", "SShHHHHd", "SSShHHhd", "SoSSHHhd", "SSSShHhd", "sSSSShdd", "sssSsddd"])
    s.paint("head", "back", ["HHHHHHHH", "HhHHHhHH", "HHHhHHHH", "HHHHHHHH", "hHHHHHHh", "dhHHHHhd", "ddhHHhdd", "sddhhdds"])
    s.paint("body", "front", [
        "NNWRRWNN",
        "NNWrRWNN",
        "NbNRRNbN",
        "NNNRRNNN",
        "NNbRrbNN",
        "NNNrRNNN",
        "NYYNNNNN",
        "nNNYNNNn",
        "NNNNNNNN",
        "NbNNNNbN",
        "NNNNNNNN",
        "nNNNNNNn",
    ])
    s.paint("body", "back", [
        "NNNNNNNN",
        "NNNNNNNN",
        "NNNNNNNN",
        "NNNNNNNN",
        "NNNNNNNN",
        "NNNNNNNN",
        "nNNNNNNn",
        "NNNNNNNN",
        "NNNbbNNN",
        "NNNNNNNN",
        "NNNNNNNN",
        "nNNNNNNn",
    ])
    for side in ("right", "left"):
        s.column("body", side, "NNNNNNnNNNNn")
    s.fill("body", "top", "N")
    s.paint("body", "top", ["NNNNNNNN", "NNNNNNNN", "NNWRRWNN", "NNNNNNNN"])
    s.fill("body", "bottom", "N")
    for arm in ("rarm4", "larm4"):
        for side in ("front", "back", "right", "left"):
            s.column(arm, side, "NNNNNNNNNWSS")
        s.fill(arm, "top", "N")
        s.fill(arm, "bottom", "S")
    for sl in ("rsleeve4", "lsleeve4"):
        for side in ("front", "back", "right", "left"):
            s.column(sl, side, "bN..........")
    for leg in ("rleg", "lleg"):
        for side in ("front", "back", "right", "left"):
            s.column(leg, side, "NNNNNNNNNnFF")
        s.fill(leg, "top", "N")
        s.fill(leg, "bottom", "F")
    s.paint("rleg", "front", ["", "", "", "", "", "", "", "", "", "", "", "Ffff"])
    s.paint("lleg", "front", ["", "", "", "", "", "", "", "", "", "", "", "ffff"])
    return s.img


def dealer():
    """Dealer (Mann): schwarzer Smoking mit Satin-Revers, weinrote Weste, rote Fliege, goldenes Einstecktuch,
    goldene Knöpfe und Manschettenknöpfe, schwarze, nach hinten gegelte Haare, Lackschuhe. Breite Arme."""
    s = Skin({
        "K": "1C1816", "h": "3A322C", "d": "100C0A",       # Haare (Glanz oben links)
        "S": "EEC6A4", "s": "D6AA88", "o": "BE906E",       # Haut
        "W": "F4F4F0", "w": "D4D2D0", "e": "2E3A48", "m": "A8685A", "t": "CFA486",
        "B": "1C1A22", "b": "2E2A36", "k": "0C0A10", "L": "3A3644",   # Smoking, Glanz, Schatten, Satin-Revers
        "V": "6E1424", "v": "4E0E1A",                      # Weste weinrot
        "R": "C42032", "r": "861422",                      # Fliege
        "Y": "F0C24A", "y": "B0841E",                      # Gold
        "G": "0C0A0E", "g": "4A4652",                      # Lackschuhe
    })
    s.fill("head", "top", "K")
    s.paint("head", "top", ["dKKKKKKd", "KhhKKKKK", "KhKKKKKK", "KKKKKhKK", "KKKKKKKK", "KKKKKKKK", "KKKKKKKK", "dKKKKKKd"])
    s.fill("head", "bottom", "s")
    s.paint("head", "front", [
        "KhhKKKKK",
        "KKKKKKKd",
        "KSSSSSSK",
        "SKKSSKKS",
        "SWeSSeWS",
        "SSSooSSS",
        "sSSmmSSs",
        "ssttttss",
    ])
    s.paint("head", "right", ["KKKKKKKK", "KKKKKKKK", "dKKKKKSS", "dKKKSSSS", "dKKSoSSS", "ddKSSSSS", "ddSSSSSs", "ddsssSss"])
    s.paint("head", "left", ["KKKKKKKK", "KKKKKKKK", "SSKKKKKd", "SSSSKKKd", "SSSoSKKd", "SSSSSKdd", "sSSSSSdd", "sssSsssd"])
    s.paint("head", "back", ["KKKKKKKK", "KKhKKKKK", "KKKKKKKK", "KKKKKKKK", "KKKKKKKK", "dKKKKKKd", "ddKKKKdd", "sddddds s"])
    s.paint("body", "front", [
        "BLWWWWLB",
        "BLRrrRLB",
        "bBLWWLBY",
        "BBLVVLBy",
        "BBBLVLBB",
        "BBBLVLBB",
        "BBBBLBBB",
        "BkBBYBBB",
        "BBBBBBBB",
        "BBBBYBBB",
        "kBBBBBBk",
        "kkBBBBkk",
    ])
    s.paint("body", "back", [
        "bBBBBBBB",
        "BBBBBBBB",
        "BBBBBBBB",
        "BBBBBBBB",
        "BBBBBBBB",
        "BBBBBBBB",
        "BBBBBBBB",
        "BBBBBBBB",
        "BBBkkBBB",
        "BBBkkBBB",
        "kBBkkBBk",
        "kkBkkBkk",
    ])
    for side in ("right", "left"):
        s.column("body", side, "BBBBBBBBBBkk")
    s.fill("body", "top", "B")
    s.paint("body", "top", ["BBBBBBBB", "BLWWWWLB", "BLWWWWLB", "BBBBBBBB"])
    s.fill("body", "bottom", "k")
    for arm in ("rarm4", "larm4"):
        for side in ("front", "back", "right", "left"):
            s.column(arm, side, "BBBBBBBBkWSS")
        s.fill(arm, "top", "B")
        s.fill(arm, "bottom", "S")
    # Licht oben links: rechter Arm (Bildseite links) mit Glanzkante, Manschettenknöpfe gold, nur außen
    s.paint("rarm4", "front", ["bBBB", "bBBB", "bBBB"])
    s.paint("rarm4", "right", ["", "", "", "", "", "", "", "", "", ".Y.."])
    s.paint("larm4", "left", ["", "", "", "", "", "", "", "", "", "..Y."])
    for leg in ("rleg", "lleg"):
        for side in ("front", "back", "right", "left"):
            s.column(leg, side, "BBBBBBBBBBGG")
        s.fill(leg, "top", "B")
        s.fill(leg, "bottom", "G")
    # Satinstreifen an der Außennaht, Glanz auf den Lackschuhen
    s.paint("rleg", "right", [".L..", ".L..", ".L..", ".L..", ".L..", ".L..", ".L..", ".L..", ".L..", ".L..", "", "g..."])
    s.paint("lleg", "left", ["..L.", "..L.", "..L.", "..L.", "..L.", "..L.", "..L.", "..L.", "..L.", "..L.", "", "...g"])
    s.paint("rleg", "front", ["", "", "", "", "", "", "", "", "", "", "", "gGGG"])
    s.paint("lleg", "front", ["", "", "", "", "", "", "", "", "", "", "", "GGgG"])
    return s.img


def kassierer():
    """Kassierer wie im alten Casino: grüner Augenschirm, weißes Hemd, schwarze Weste mit goldener Uhrkette,
    rote Ärmelhalter, Fliege, kurzer Schnurrbart. Breite Arme."""
    s = Skin({
        "H": "5A3A22", "h": "7A5232", "d": "3E2614",
        "S": "F0CCAE", "s": "DAB092", "o": "C49478",
        "W": "F4F4F0", "w": "D6D6D2", "e": "3A2A1E", "M": "5A3A22", "m": "B47C6C",
        "N": "1E1C24", "n": "121016", "b": "34303C",       # Weste
        "R": "C8283A", "r": "8E1A28",                       # Ärmelhalter, Fliege
        "Y": "F0C840", "y": "B08A20",                       # Gold
        "V": "2E8A4A", "v": "1E6234", "u": "5AC07A",        # grüner Augenschirm (durchscheinend wirkend)
        "P": "2A2830", "p": "1A1820", "F": "1A1410", "f": "4A3A30",
    })
    s.fill("head", "top", "H")
    s.paint("head", "top", ["dHHHHHHd", "HhHHHHHH", "HHHHhHHH", "HHHHHHHH", "HHHHHHHH", "HhHHHHHH", "HHHHHHHH", "dHHHHHHd"])
    s.fill("head", "bottom", "s")
    s.paint("head", "front", [
        "HHHHHHHH",
        "HSSSSSSH",
        "SHHSSHHS",
        "SWeSSeWS",
        "SSSooSSS",
        "SMMMMMMS",
        "sSSmmSSs",
        "ssSSSSss",
    ])
    s.paint("head", "right", ["HHHHHHHH", "HHHHHHSS", "dHHHHSSS", "dHHSoSSS", "dHHSSSSS", "ddHSSSSS", "ddSSSSSs", "ddsssSss"])
    s.paint("head", "left", ["HHHHHHHH", "SSHHHHHH", "SSSHHHHd", "SSSoSHHd", "SSSSSHHd", "SSSSSHdd", "sSSSSSdd", "sssSsssd"])
    s.paint("head", "back", ["HHHHHHHH", "HhHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "dHHHHHHd", "ddHHHHdd", "sddddds s"])
    # Augenschirm auf der Hut-Ebene: grünes Band um den Kopf, vorne der Schirm
    s.paint("hat", "front", ["uVVVVVVV", "vvvvvvvv"])
    for side in ("right", "left", "back"):
        s.paint("hat", side, ["VVVVVVVV", "vvvvvvvv"])
    s.paint("body", "front", [
        "NNWRRWNN",
        "NNWrrWNN",
        "bNNWWNNN",
        "NNNWWNNN",
        "NNNWWNNN",
        "NYYWWNNN",
        "NNyYWNNN",
        "NNNWWNNN",
        "NNNWWNyN",
        "PPPPPPPP",
        "PPPyPPPP",
        "pPPPPPPp",
    ])
    s.paint("body", "back", ["NNNNNNNN"] * 8 + ["NNNbbNNN", "PPPPPPPP", "PPPPPPPP", "pPPPPPPp"])
    for side in ("right", "left"):
        s.column("body", side, "NNNNNNNNNPPp")
    s.fill("body", "top", "N")
    s.paint("body", "top", ["NNNNNNNN", "NNWWWWNN", "NNWWWWNN", "NNNNNNNN"])
    s.fill("body", "bottom", "P")
    for arm in ("rarm4", "larm4"):
        for side in ("front", "back", "right", "left"):
            s.column(arm, side, "WWWRrWWWWwSS")
        s.fill(arm, "top", "W")
        s.fill(arm, "bottom", "S")
    for leg in ("rleg", "lleg"):
        for side in ("front", "back", "right", "left"):
            s.column(leg, side, "PPPPPPPPPpFF")
        s.fill(leg, "top", "P")
        s.fill(leg, "bottom", "F")
    s.paint("rleg", "front", ["", "", "", "", "", "", "", "", "", "", "", "Ffff"])
    s.paint("lleg", "front", ["", "", "", "", "", "", "", "", "", "", "", "ffff"])
    return s.img


# Name -> (Malfunktion, Ordner unter kuby:entity/)
SKINS = {"croupier": (croupier, "casino"), "mia": (mia, "casino"), "dealer": (dealer, "casino"),
         "kassierer": (kassierer, "casino"),
         "priester": (priester, "survival"), "bankier": (bankier, "survival")}


# ---------------------------------------------------------------------------------------------------------------------
# Gelenkfigur (Rig) aus einem Skin: Item-Modelle je Körperteil, Gelenk jeweils in der Modellmitte (8, 8, 8).
# Die Item-Anzeige dreht das Modell um 180° (Ry(pi)): darum zeigt die Vorderseite im Modell nach Norden und die rechte
# Körperseite der Figur liegt im Modell bei +X (Osten). Maße wie im Minecraft-Skin (1 px = 1/16 Block).
# ---------------------------------------------------------------------------------------------------------------------
RIG_PARTS = {
    # Teil: (Skin-Würfel (u, v, w, h, d), Zeilen von-bis, Element von/bis relativ zum Gelenk in px, Deckel oben/unten)
    "head": ((0, 0, 8, 8, 8), (0, 8), (-4, 0, -4), (4, 8, 4), True, True),
    "torso": ((16, 16, 8, 12, 4), (0, 12), (-4, 0, -2), (4, 12, 2), True, True),
    "rleg": ((0, 16, 4, 12, 4), (0, 12), (0, -12, -2), (4, 0, 2), True, True),
    "lleg": ((16, 48, 4, 12, 4), (0, 12), (-4, -12, -2), (0, 0, 2), True, True),
    "rarm_up": ((40, 16, 4, 12, 4), (0, 6), (-2, -5, -2), (2, 1, 2), True, False),
    "rarm_lo": ((40, 16, 4, 12, 4), (6, 12), (-2, -6, -2), (2, 0, 2), False, True),
    "larm_up": ((32, 48, 4, 12, 4), (0, 6), (-2, -5, -2), (2, 1, 2), True, False),
    "larm_lo": ((32, 48, 4, 12, 4), (6, 12), (-2, -6, -2), (2, 0, 2), False, True),
}


def rig_part_model(tex, part):
    (u, v, w, h, d), (r0, r1), lo, hi, cap_top, cap_bottom = RIG_PARTS[part]

    def uv(x, y, ww, hh):
        return [x / 4, y / 4, (x + ww) / 4, (y + hh) / 4]

    rows = r1 - r0
    side_y = v + d + r0
    faces = {
        "north": {"texture": "#skin", "uv": uv(u + d, side_y, w, rows)},           # Vorderseite
        "south": {"texture": "#skin", "uv": uv(u + 2 * d + w, side_y, w, rows)},   # Rücken
        "east": {"texture": "#skin", "uv": uv(u, side_y, d, rows)},                # rechte Körperseite
        "west": {"texture": "#skin", "uv": uv(u + d + w, side_y, d, rows)},        # linke Körperseite
    }
    faces["up"] = {"texture": "#skin", "uv": uv(u + d, v, w, d) if cap_top else uv(u + d, side_y, w, 1)}
    faces["down"] = ({"texture": "#skin", "uv": uv(u + d + w, v, w, d)} if cap_bottom
                     else {"texture": "#skin", "uv": uv(u + d, side_y + rows - 1, w, 1)})
    return {"textures": {"skin": tex, "particle": tex},
            "elements": [{"from": [8 + lo[0], 8 + lo[1], 8 + lo[2]], "to": [8 + hi[0], 8 + hi[1], 8 + hi[2]],
                          "faces": faces}]}


def dealer_hd():
    """Dealer in doppelter Auflösung (128 x 128) für die Gelenkfigur: gleiche Aufteilung wie der 64er-Skin, jede
    Fläche mit 2 x 2 Pixeln je Skin-Pixel. Grundlage ist der 64er-Skin (hochskaliert), darauf die feinen Teile."""
    base = dealer().resize((128, 128), Image.NEAREST)
    pal = {k: rgb(v) for k, v in {
        "K": "1C1816", "h": "3A322C", "d": "100C0A",
        "S": "EEC6A4", "s": "D6AA88", "o": "BE906E", "t": "CFA486", "e": "2E3A48", "W": "F4F4F0", "Z": "CFCCC8",
        "m": "A8685A", "B": "1C1A22", "b": "2E2A36", "k": "0C0A10", "L": "3A3644", "l": "4E4A5C",
        "V": "6E1424", "v": "4E0E1A", "R": "C42032", "r": "861422", "Y": "F0C24A", "y": "B0841E",
        "G": "0C0A0E", "g": "4A4652", "P": "F0C24A", "p": "B0841E"}.items()}

    def put(x0, y0, rows):
        for y, row in enumerate(rows):
            assert len(row) == len(rows[0]), (x0, y0, row)
            for x, ch in enumerate(row):
                if ch != ".":
                    base.putpixel((x0 + x, y0 + y), pal[ch])

    # Kopf vorne (16 x 16)
    put(16, 16, [
        "KKKKKKKKKKKKKKKK",
        "KKhhhKKKKKKKKKKK",
        "KKKhhhKKKKKKKKKd",
        "KKKKKKKKKKKKKKKd",
        "KSSSSSSSSSSSSSSK",
        "KSSSSSSSSSSSSSSK",
        "SSKKKKSSSSKKKKSS",
        "SSSSSSSSSSSSSSSS",
        "SSWWeeSSSSeeWWSS",
        "SSWWeeSSSSeeWWSS",
        "SSSSSSSooSSSSSSS",
        "SSSSSSSooSSSSSSS",
        "SSSSSSooooSSSSSS",
        "SSSSStmmmmtSSSSS",
        "sSSSSSSSSSSSSSSs",
        "sstttttttttttss.".replace(".", "s"),
    ])
    # Oberkörper vorne (16 x 24): Satin-Revers, Fliege, Weste mit Knopf, Einstecktuch, ein Knopf, Taschenklappen
    put(40, 40, [
        "bBlLWWWWWWWWLlBB",
        "bBlLWWZWWZWWLlBB",
        "bBBlLRRrrRRLlBBB",
        "bBBlLrRrrRrLlBBB",
        "bBBBlLVWWVLlBBBB",
        "bBBBlLVWWVLlBPPB",
        "bBBBBlLVVLlBBpPB",
        "bBBBBlLVvLlBBBBB",
        "bBBBBBlVYVlBBBBB",
        "bBBBBBBlVlBBBBBB",
        "bBBBBBBBlBBBBBBB",
        "bBBBBBBBBBBBBBBB",
        "bBBBBBByYBBBBBBB",
        "bBBBBBBkBBBBBBBB",
        "bBBBBBkBkBBBBBBB",
        "bBBBBkBBBkBBBBBB",
        "bBBBkBBBBBkBBBBB",
        "bbbbbBBBBBBbbbbB",
        "BkkkkBBBBBBkkkkB",
        "bBBBBBBBBBBBBBBB",
        "bBBBBBBBBBBBBBBB",
        "bBBBBBBBBBBBBBBB",
        "kBBBBBBBBBBBBBBk",
        "kkBBBBBBBBBBBBkk",
    ])
    # Rücken (16 x 24): Mittelnaht und Schlitz
    put(64, 40, ["BBBBBBBBBBBBBBBB"] + ["BBBBBBBkBBBBBBBB"] * 15 + ["BBBBBBkBkBBBBBBB"] * 6
        + ["kBBBBBkBkBBBBBBk", "kkBBBBkBkBBBBBkk"])
    # Ärmel: vorne/hinten/innen/außen 8 x 24; außen mit vier Ärmelknöpfen und Manschettenknopf
    sleeve = ["bBBBBBBB"] * 11 + ["bBBkBBBB"] + ["bBBBBBBk"] * 5 + ["kkkkkkkk", "WWWWWWWW", "ZZWWWWZZ",
              "SSSSSSSS", "SSSSSSSS", "sSSSSSSs", "ssSsSsSs"]
    outer = ["BBBBBBBB"] * 11 + ["BBBkBBBB"] + ["BBBBBBBB"] * 2 + ["BBByByBB", "BByByBBB", "BBBBBBBB",
             "kkkkkkkk", "WWWYYWWW", "ZZWyyWZZ", "SSSSSSSS", "SSSSSSSS", "sSSSSSSs", "ssSsSsSs"]
    inner = ["BBBBBBBB"] * 17 + ["kkkkkkkk", "WWWWWWWW", "ZZZZZZZZ", "sSSSSSSs", "sSSSSSSs", "ssssssss", "ssssssss"]
    # rechter Arm (64er: 40,16): außen = rechte Seite (80,40), vorne (88,40), innen (96,40), hinten (104,40)
    put(80, 40, outer)
    put(88, 40, sleeve)
    put(96, 40, inner)
    put(104, 40, [r.replace("b", "B") for r in sleeve])
    # linker Arm (64er: 32,48): innen = rechte Seite (64,104), vorne (72,104), außen (80,104), hinten (88,104)
    put(64, 104, inner)
    put(72, 104, sleeve)
    put(80, 104, outer)
    put(88, 104, [r.replace("b", "B") for r in sleeve])
    # Hosenbeine: Bügelfalte vorne, Satinstreifen außen, Lackschuhe mit Glanz
    front = ["BBBbBBBB"] * 19 + ["kkkkkkkk", "GgGGGGGG", "GggGGGGG", "GGGGGGGG", "GGGGGGGG"]
    stripe = ["BBBlLBBB"] * 19 + ["kkkkkkkk", "GGGGGGGG", "GGGGGGGG", "GGGGGGGG", "GGGGGGGG"]
    plain = ["BBBBBBBB"] * 19 + ["kkkkkkkk", "GGGGGGGG", "GGGGGGGG", "GGGGGGGG", "GGGGGGGG"]
    put(0, 40, stripe)        # rechtes Bein außen
    put(8, 40, front)
    put(16, 40, plain)
    put(24, 40, plain)
    put(32, 104, plain)       # linkes Bein innen
    put(40, 104, front)       # linkes Bein vorne
    put(48, 104, stripe)      # linkes Bein außen
    put(56, 104, plain)       # linkes Bein hinten
    return base


RIGS = {"dealer": dealer_hd}


def build_rigs(out, skins):
    import json
    tex_dir = os.path.join(out, "assets/kuby/textures/block/casino")
    models = os.path.join(out, "assets/kuby/models/item/casino")
    items = os.path.join(out, "assets/kuby/items/casino")
    for d in (tex_dir, models, items):
        os.makedirs(d, exist_ok=True)
    for name, fn in RIGS.items():
        fn().save(os.path.join(tex_dir, "rig_" + name + ".png"))
        for part in RIG_PARTS:
            mid = "rig_" + name + "_" + part
            with open(os.path.join(models, mid + ".json"), "w", encoding="utf-8") as fh:
                json.dump(rig_part_model("kuby:block/casino/rig_" + name, part), fh)
            with open(os.path.join(items, mid + ".json"), "w", encoding="utf-8") as fh:
                json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/" + mid}}, fh)


def build(out):
    made = {}
    for name, (fn, folder) in SKINS.items():
        d = os.path.join(out, "assets/kuby/textures/entity", folder)
        os.makedirs(d, exist_ok=True)
        made[name] = fn()
        made[name].save(os.path.join(d, name + ".png"))
    build_rigs(out, made)
