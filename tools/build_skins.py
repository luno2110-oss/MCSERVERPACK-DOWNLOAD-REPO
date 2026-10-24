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


# Name -> (Malfunktion, Ordner unter kuby:entity/)
SKINS = {"croupier": (croupier, "casino"), "mia": (mia, "casino"),
         "priester": (priester, "survival"), "bankier": (bankier, "survival")}


def build(out):
    for name, (fn, folder) in SKINS.items():
        d = os.path.join(out, "assets/kuby/textures/entity", folder)
        os.makedirs(d, exist_ok=True)
        fn().save(os.path.join(d, name + ".png"))
