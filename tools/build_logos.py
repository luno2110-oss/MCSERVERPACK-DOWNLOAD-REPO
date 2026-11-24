#!/usr/bin/env python3
"""Schriftzüge der Casino-Automaten als Bild (v2.07): „PLINKO“ und „GLÜCKSRAD“ waren Text-Anzeigen in der Titelschrift
(kuby:title). Manche Clients (Mods, die Schriften anders vermessen) setzen solche Texte schief – auf einem Bild des
Besitzers stand „PLINKO“ nach rechts verrutscht neben seiner Tafel. Als Bild (item/generated, quadratisch, Schriftzug
mittig) sitzen sie in jedem Client gleich.

Gezeichnet aus derselben Titelschrift (textures/k/title_white.png, Zellen 18 x 23, Vorschub = Glyphenbreite + 1),
jeder Buchstabe in seiner Farbe (wie CrashScreen.logo: Weiß × Farbe). Aufruf über build_ui_pack.py nach dem Kit.
"""
import json
import os
import re
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
KIT = os.path.join(REPO, "KubyCore-v2.0.14/KubyPlugins/src/main/java/de/kuby/kubyplugins/ui/KitData.java")

CASINO_SRC = os.path.join(REPO, "KubyCore-v2.0.14/KubyPlugins/src/main/java/de/kuby/kubyplugins/casino")
# Name: (Text, Klasse mit den Farben je Buchstabe in LOGO)
LOGOS = {
    "pl_logo": ("PLINKO", "PlinkoBoard"),
    "wh_logo": ("GLÜCKSRAD", "WheelStage"),
    "cr_logo": ("CRASH", "CrashScreen"),
    "rr_logo": ("PFERDERENNEN", "RaceBoard"),
}


def colors_of(cls):
    """Farben aus „int[] LOGO = {…}“ der Java-Klasse (eine Quelle für Text und Bild)."""
    with open(os.path.join(CASINO_SRC, cls + ".java"), encoding="utf-8") as fh:
        m = re.search(r"int\[\] LOGO = \{([^}]*)\}", fh.read())
    return [int(x.strip(), 16) for x in m.group(1).replace("0x", "").split(",") if x.strip()]
CELL_W, CELL_H, COLS = 18, 23, 16


def title_chars():
    with open(KIT, encoding="utf-8") as fh:
        src = fh.read()
    m = re.search(r'TITLE_CHARS = "((?:[^"\\]|\\.)*)"', src)
    return m.group(1).encode("ascii").decode("unicode_escape")


def logo(sheet, chars, text, colors):
    glyphs = []
    k = 0
    for ch in text.upper():
        i = chars.index(ch) if ch in chars else chars.index("?")
        cell = sheet.crop(((i % COLS) * CELL_W, (i // COLS) * CELL_H, (i % COLS + 1) * CELL_W, (i // COLS + 1) * CELL_H))
        bbox = cell.getchannel("A").getbbox()
        w = bbox[2] if bbox else 0
        col = colors[min(k, len(colors) - 1)]
        if ch.strip():
            k += 1
        tint = Image.new("RGBA", cell.size, ((col >> 16) & 255, (col >> 8) & 255, col & 255, 255))
        px, pt = cell.load(), tint.load()
        out = Image.new("RGBA", cell.size, (0, 0, 0, 0))
        po = out.load()
        for y in range(cell.height):
            for x in range(cell.width):
                r, g, b, a = px[x, y]
                if a:
                    tr, tg, tb, _ = pt[x, y]
                    po[x, y] = (r * tr // 255, g * tg // 255, b * tb // 255, a)
        glyphs.append((out, w))
    width = sum(w + 1 for _, w in glyphs) - 1
    img = Image.new("RGBA", (width, CELL_H), (0, 0, 0, 0))
    x = 0
    for g, w in glyphs:
        img.alpha_composite(g.crop((0, 0, max(1, w), CELL_H)), (x, 0))
        x += w + 1
    # quadratisch, Schriftzug mittig (Breite des Bilds = Breite des Schriftzugs + 2 Rand)
    side = width + 2
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.alpha_composite(img, (1, (side - CELL_H) // 2))
    return sq


def write(pack):
    a = os.path.join(pack, "assets/kuby")
    sheet = Image.open(os.path.join(a, "textures/k/title_white.png")).convert("RGBA")
    chars = title_chars()
    for d in ("textures/item/casino", "models/item/casino", "items/casino"):
        os.makedirs(os.path.join(a, d), exist_ok=True)
    for name, (text, cls) in LOGOS.items():
        logo(sheet, chars, text, colors_of(cls)).save(os.path.join(a, "textures/item/casino", name + ".png"))
        with open(os.path.join(a, "models/item/casino", name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/casino/" + name}}, fh)
        with open(os.path.join(a, "items/casino", name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/" + name}}, fh)
    return len(LOGOS)


if __name__ == "__main__":
    print("ok Schriftzüge:", write(sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "resourcepack/KubyUI")))
