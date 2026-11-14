#!/usr/bin/env python3
"""Angeln aus dem Angel-Shop (/angeln): 14 Ruten, jede in ihrem Material.

Grundlage ist die Vanilla-Angel (16 x 16, eingeholt und ausgeworfen): der Stock (vier Holztöne) bekommt die Rampe des
Materials, die Rolle eine passende Farbe. Rainbow (Regenbogen-Verlauf entlang des Stocks), Void (Obsidian mit
violettem Funkeln) und Göttlich (weiß-goldener Stock, goldene Schnur, Glanzpunkte) sind eigene Varianten.

Schreibt Texturen, Modelle (parent item/handheld_rod) und Item-Definitionen (eingeholt / ausgeworfen) und gibt die
Icons [(name, bild)] für das Dialog-Kit zurück (Namen angel_<id>, in KitData als ANGEL_<ID>).
Reihenfolge = ROD_IDS = Rod.java.
"""
import colorsys
import json
import os

from PIL import Image

ROD_IDS = ["holz", "stein", "kupfer", "eisen", "gold", "lapis", "diamant", "smaragd", "quarz", "amethyst", "netherite",
           "rainbow", "void", "goettlich", "goldfieber", "sturm", "kosmos"]

# Vanilla-Holztöne des Stocks (dunkel -> hell) und der Rolle
WOOD = ["281e0b", "493615", "684e1e", "896727"]
REEL = ["353535", "969696", "b4b3b3"]
LINE = ["444444", "646464", "747474"]


def h(c):
    c = c.lstrip("#")
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16))


# Stock (dunkel, mittel-dunkel, mittel, hell) und Rolle (dunkel, mittel, hell) je Material
MAT = {
    "holz": (WOOD, REEL),
    "stein": (["2e2e2e", "555555", "7a7a7a", "9e9e9e"], REEL),
    "kupfer": (["5a2a18", "9a4a2a", "c66c45", "e9956a"], ["5a2a18", "c66c45", "e9956a"]),
    "eisen": (["3e3e3e", "8a8a8a", "c4c4c4", "ececec"], ["3e3e3e", "a8a8a8", "dcdcdc"]),
    "gold": (["7e3000", "c86600", "f49d02", "f8de06"], ["7e3000", "f49d02", "faf44a"]),
    "lapis": (["0c1747", "1c3a96", "2f5ed2", "6a96f0"], ["7e3000", "f49d02", "faf44a"]),
    "diamant": (["0c4a48", "16908c", "40d8cf", "a6fbf2"], ["0c4a48", "40d8cf", "a6fbf2"]),
    "smaragd": (["01471a", "008029", "01b427", "21f250"], ["7e3000", "f49d02", "faf44a"]),
    "quarz": (["6e625a", "b3a79e", "e1d8cf", "fffaf4"], ["6e625a", "e1d8cf", "fffaf4"]),
    "amethyst": (["3b2163", "6e4aa8", "a07ae0", "d2b4ff"], ["3b2163", "a07ae0", "d2b4ff"]),
    "netherite": (["17131a", "2f282f", "4a434a", "6d666d"], ["7e3000", "f49d02", "faf44a"]),
    "void": (["06030b", "1e1430", "3b2754", "5a3c7e"], ["2b0178", "973ae0", "f0d8ff"]),
    "goettlich": (["e08001", "f8de06", "fff7b0", "ffffff"], ["c86600", "f8de06", "ffffff"]),
    # Angelpass: Goldfieber (Rot-Gold), Sturm (Gewitterblau, gelbe Schnur), Kosmos (Nachtviolett mit Sternen)
    "goldfieber": (["5a0a12", "a01828", "e0a020", "ffe070"], ["7e3000", "f49d02", "faf44a"]),
    "sturm": (["1a2236", "34466e", "6a86c0", "b8d0ff"], ["2a2a3a", "f2e24a", "ffffa0"]),
    "kosmos": (["0a0820", "1e1a54", "3c3aa0", "7c7ef0"], ["1a0a40", "c080ff", "ffffff"]),
}


def recolor(src, rid):
    im = src.copy()
    px = im.load()
    W, H = im.size
    if rid == "rainbow":
        # Verlauf entlang des Stocks (unten links rot -> oben rechts violett)
        for y in range(H):
            for x in range(W):
                r, g, b, a = px[x, y]
                hexc = "%02x%02x%02x" % (r, g, b)
                if a and hexc in WOOD:
                    k = WOOD.index(hexc)
                    t = ((x - y) + 14) / 28
                    hue = (0.0 + 0.82 * t) % 1.0
                    light = [0.32, 0.45, 0.58, 0.72][k]
                    rr, gg, bb = colorsys.hls_to_rgb(hue, light, 0.9)
                    px[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)
                elif a and hexc in REEL:
                    px[x, y] = h(["4a4a6a", "dcdcff", "ffffff"][REEL.index(hexc)]) + (a,)
                elif a and hexc in LINE:
                    px[x, y] = h(["c86ef0", "ff8ad8", "8ad8ff"][LINE.index(hexc)]) + (a,)
        return im
    wood, reel = MAT[rid]
    for y in range(H):
        for x in range(W):
            r, g, b, a = px[x, y]
            if not a:
                continue
            hexc = "%02x%02x%02x" % (r, g, b)
            if hexc in WOOD:
                px[x, y] = h(wood[WOOD.index(hexc)]) + (a,)
            elif hexc in REEL:
                px[x, y] = h(reel[REEL.index(hexc)]) + (a,)
            elif rid == "goettlich" and hexc in LINE:
                px[x, y] = h(["c86600", "f49d02", "f8de06"][LINE.index(hexc)]) + (a,)
            elif rid == "sturm" and hexc in LINE:
                px[x, y] = h(["c8b820", "f2e24a", "ffff90"][LINE.index(hexc)]) + (a,)
            elif rid == "goldfieber" and hexc in LINE:
                px[x, y] = h(["a01828", "e0a020", "ffe070"][LINE.index(hexc)]) + (a,)
            elif rid == "kosmos" and hexc in LINE:
                px[x, y] = h(["6a4ad0", "a080ff", "e0d0ff"][LINE.index(hexc)]) + (a,)
            elif rid == "void" and hexc in LINE:
                px[x, y] = h(["4b02bb", "7216d1", "973ae0"][LINE.index(hexc)]) + (a,)
    if rid == "void":
        # violettes Funkeln am Stock (bewusst nicht gleichmäßig)
        for (x, y) in ((11, 4), (8, 7), (5, 10)):
            if px[x, y][3]:
                px[x, y] = (240, 160, 255, 255)
    if rid == "kosmos":
        # Sterne am Stock (ungleich verteilt) und ein Glanz an der Spitze
        for (x, y) in ((12, 3), (9, 6), (4, 11)):
            if px[x, y][3]:
                px[x, y] = (255, 255, 255, 255)
    if rid == "sturm":
        for (x, y) in ((10, 5), (6, 9)):
            if px[x, y][3]:
                px[x, y] = (255, 240, 120, 255)
    if rid == "goldfieber":
        for (x, y) in ((12, 2), (7, 7)):
            if px[x, y][3]:
                px[x, y] = (255, 250, 200, 255)
    if rid == "goettlich":
        # Glanzpunkte: Spitze und einmal in der Mitte
        for (x, y) in ((12, 2), (9, 5)):
            if px[x, y][3]:
                px[x, y] = (255, 255, 255, 255)
    return im


def build(out, vanilla):
    tex = os.path.join(out, "assets/kuby/textures/item/angeln")
    models = os.path.join(out, "assets/kuby/models/item/angeln")
    items = os.path.join(out, "assets/kuby/items/angeln")
    for d in (tex, models, items):
        os.makedirs(d, exist_ok=True)
    base = Image.open(os.path.join(vanilla, "assets/minecraft/textures/item/fishing_rod.png")).convert("RGBA")
    cast = Image.open(os.path.join(vanilla, "assets/minecraft/textures/item/fishing_rod_cast.png")).convert("RGBA")
    icons = []
    for rid in ROD_IDS:
        a = recolor(base, rid)
        b = recolor(cast, rid)
        a.save(os.path.join(tex, "rod_%s.png" % rid))
        b.save(os.path.join(tex, "rod_%s_cast.png" % rid))
        for suffix in ("", "_cast"):
            with open(os.path.join(models, "rod_%s%s.json" % (rid, suffix)), "w", encoding="utf-8") as f:
                json.dump({"parent": "minecraft:item/handheld_rod", "textures": {"layer0": "kuby:item/angeln/rod_%s%s" % (rid, suffix)}}, f)
        with open(os.path.join(items, "rod_%s.json" % rid), "w", encoding="utf-8") as f:
            json.dump({"model": {"type": "minecraft:condition", "property": "minecraft:fishing_rod/cast",
                                 "on_false": {"type": "minecraft:model", "model": "kuby:item/angeln/rod_%s" % rid},
                                 "on_true": {"type": "minecraft:model", "model": "kuby:item/angeln/rod_%s_cast" % rid}}}, f)
        icons.append(("angel_" + rid, a))
    return icons


if __name__ == "__main__":
    import sys
    o = sys.argv[1] if len(sys.argv) > 1 else "resourcepack/KubyUI"
    v = os.environ.get("KUBY_VANILLA", "vanilla")
    print(len(build(o, v)), "Angeln")
