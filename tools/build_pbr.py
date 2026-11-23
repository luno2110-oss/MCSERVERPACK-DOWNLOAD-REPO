#!/usr/bin/env python3
"""PBR-Karten für Shader (v2.07, Wunsch des Besitzers: „unsere Sachen auch für Shader sichtbar machen“).

Shaderpacks mit LabPBR (Complementary, BSL, Bliss, Rethinking Voxels … mit Material-Format „LabPBR“) lesen zu jeder
Textur im Block-Atlas eine Spiegel-Karte <name>_s.png: Rot = Glätte, Grün = Reflexion (230 und mehr = Metall, 231 =
Gold), Alpha = Leuchten (0 bis 254; 255 heißt „leuchtet nicht“). Ohne Shader und mit Shadern ohne LabPBR ändert sich
nichts.

Hier: Casino-Gold glänzt als Metall, die eingeschalteten Teile (Lämpchen, Neon, Plinko-Fächer und Knöpfe im
leuchtenden Zustand) leuchten selbst, auch nachts. Aufruf über build_ui_pack.py nach den Casino-Texturen.
"""
import glob
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)

# Glätte, Reflexion (231 = Gold, 230 = Eisen, sonst F0 * 255), Leuchten (255 = aus)
GOLD = (200, 231, 255)
LIT_GOLD = (210, 231, 230)


def spec(src, smooth, f0, emit, by_light=False):
    """Spiegel-Karte in Größe der Textur; leere Pixel bleiben leer. by_light: Leuchten nach Helligkeit (Kern heller)."""
    im = Image.open(src).convert("RGBA")
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    pi, po = im.load(), out.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = pi[x, y]
            if a == 0:
                continue
            e = emit
            if by_light and emit < 255:
                lum = (r * 299 + g * 587 + b * 114) / 1000
                e = int(max(40, min(254, 60 + lum * 0.75)))
            po[x, y] = (smooth, f0, 0, e)
    return out


def write(pack):
    base = os.path.join(pack, "assets/kuby/textures/block/casino")
    if not os.path.isdir(base):
        return 0
    n = 0
    for f in sorted(glob.glob(os.path.join(base, "*.png"))):
        name = os.path.basename(f)[:-4]
        if name.endswith("_s") or name.endswith("_n"):
            continue
        if name in ("hd_gold", "gold", "chip_gold", "chipside_gold"):
            img = spec(f, *GOLD)
        elif name == "hd_gold_lit":
            img = spec(f, *LIT_GOLD, by_light=True)
        elif name.endswith("_lit"):
            img = spec(f, 120, 20, 200, by_light=True)
        elif name.startswith("hd_neon_"):
            img = spec(f, 150, 20, 120, by_light=True)   # Neon-Röhren glimmen auch ausgeschaltet etwas
        else:
            continue
        img.save(os.path.join(base, name + "_s.png"))
        n += 1
    return n


if __name__ == "__main__":
    print("ok PBR:", write(sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "resourcepack/KubyUI")), "Spiegel-Karten")
