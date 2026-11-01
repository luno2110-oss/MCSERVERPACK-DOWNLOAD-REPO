"""Eigene Icons für die Menü-Dialoge (12.10): Blöcke ohne flaches Item-Bild als kleine 3D-Würfel wie im Inventar
(Gras, Truhe, Bett, Werkbank ...) und ein Banner.

Die Würfel werden aus den Vanilla-Texturen gerechnet: Oberseite hell, linke Seite (vorn) 80 %, rechte Seite 60 %
Helligkeit, wie Minecraft Blöcke im Inventar zeigt. Reihenfolge = ICONS; neue Einträge hinten anhängen.
"""
import os

from PIL import Image

T = (0, 0, 0, 0)


def _tex(vroot, path):
    return Image.open(os.path.join(vroot, "assets/minecraft/textures", path)).convert("RGBA")


def _tint(img, hexcol):
    r, g, b = int(hexcol[0:2], 16), int(hexcol[2:4], 16), int(hexcol[4:6], 16)
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            px[x, y] = (p[0] * r // 255, p[1] * g // 255, p[2] * b // 255, p[3])
    return out


def _over(base, top):
    out = base.copy()
    out.alpha_composite(top)
    return out


def _square(img):
    """Textur auf 16 x 16 (Animationen: erstes Bild)."""
    if img.height > img.width:
        img = img.crop((0, 0, img.width, img.width))
    return img.resize((16, 16), Image.NEAREST)


def _sample(tex, u, v, f):
    x = min(tex.width - 1, max(0, int(u * tex.width)))
    y = min(tex.height - 1, max(0, int(v * tex.height)))
    p = tex.getpixel((x, y))
    if p[3] == 0:
        return None
    return (round(p[0] * f), round(p[1] * f), round(p[2] * f), 255)


def cube(top, left, right, height=1.0):
    """Würfel (oder flacher Block mit height < 1) im Inventar-Blick, 16 x 16. Gerechnet in 4-facher Größe, dann je
    4 x 4 Feld die häufigste Farbe (bleibt pixelig, ohne Treppen an den Kanten)."""
    n = 64
    s = n / 16
    big = Image.new("RGBA", (n, n), T)
    px = big.load()
    # Ecken (in 16er-Einheiten): Oberseite (0,4) (8,0) (16,4) (8,8); Seiten bis 8 * height nach unten
    drop = 4 * (1 - height) * 2      # flacher Block: Oberseite tiefer
    hh = 8 * height
    faces = [
        # (Ursprung, Achse u, Achse v, Textur, Helligkeit)
        ((0, 4 + drop), (8, -4), (8, 4), top, 1.0),
        ((0, 4 + drop), (8, 4), (0, hh), left, 0.8),
        ((8, 8 + drop), (8, -4), (0, hh), right, 0.6),
    ]
    for y in range(n):
        for x in range(n):
            X, Y = (x + 0.5) / s, (y + 0.5) / s
            for (ox, oy), (ax, ay), (bx, by), tex, f in faces:
                det = ax * by - ay * bx
                dx, dy = X - ox, Y - oy
                u = (dx * by - dy * bx) / det
                v = (ax * dy - ay * dx) / det
                if 0 <= u < 1 and 0 <= v < 1:
                    vv = v if tex is top else (1 - height) + v * height
                    c = _sample(tex, u, vv, f)
                    if c:
                        px[x, y] = c
                    break
    out = Image.new("RGBA", (16, 16), T)
    op = out.load()
    for y in range(16):
        for x in range(16):
            counts = {}
            for yy in range(4):
                for xx in range(4):
                    c = px[x * 4 + xx, y * 4 + yy]
                    counts[c] = counts.get(c, 0) + 1
            best = max(counts.items(), key=lambda kv: kv[1])[0]
            # Kante: nur füllen, wenn mindestens die Hälfte des Felds belegt ist
            filled = 16 - counts.get(T, 0)
            if filled >= 8:
                if best == T:
                    best = max(((c, k) for c, k in counts.items() if c != T), key=lambda kv: kv[1])[0]
                op[x, y] = best
    return out


def _chest(vroot, name):
    t = _tex(vroot, "entity/chest/%s.png" % name)
    # Deckel oben (14 x 14 ab 14,0), Deckel vorn (14 x 5 ab 14,14), Unterteil vorn (14 x 10 ab 14,33), Seiten links
    top = t.crop((14, 0, 28, 14))
    front = Image.new("RGBA", (14, 15), T)
    front.paste(t.crop((14, 14, 28, 19)), (0, 0))
    front.paste(t.crop((14, 33, 28, 43)), (0, 5))
    side = Image.new("RGBA", (14, 15), T)
    side.paste(t.crop((0, 14, 14, 19)), (0, 0))
    side.paste(t.crop((0, 33, 14, 43)), (0, 5))
    # Schloss vorn mittig (2 x 4 ab 1,1)
    lock = t.crop((1, 1, 3, 5))
    front.alpha_composite(lock, (6, 3))
    return cube(top.resize((16, 16), Image.NEAREST), front.resize((16, 16), Image.NEAREST),
                side.resize((16, 16), Image.NEAREST), 0.875)


def _bed(vroot):
    wool = _square(_tex(vroot, "block/red_wool.png"))
    pillow = _square(_tex(vroot, "block/white_wool.png"))
    planks = _square(_tex(vroot, "block/oak_planks.png"))
    top = wool.copy()
    top.paste(pillow.crop((0, 0, 16, 5)), (0, 0))
    side = Image.new("RGBA", (16, 16), T)
    side.paste(wool.crop((0, 0, 16, 9)), (0, 7))
    side.paste(planks.crop((0, 0, 16, 3)), (0, 13))
    return cube(top, side, side, 0.5625)


def _banner():
    """Weißes Banner mit Stange (flach)."""
    img = Image.new("RGBA", (16, 16), T)
    px = img.load()
    pole, pole_d = (138, 104, 62, 255), (96, 70, 40, 255)
    for x in range(3, 13):
        px[x, 1] = pole if x % 2 else pole_d
    for y in range(0, 16):
        px[7, y] = pole_d if y > 13 else px[7, y]
    cloth, shade, edge = (238, 238, 238, 255), (200, 200, 204, 255), (120, 120, 128, 255)
    for y in range(2, 14):
        for x in range(4, 12):
            c = cloth if x < 10 else shade
            if x == 4 or x == 11:
                c = edge
            px[x, y] = c
    for x in range(4, 12):
        px[x, 14] = edge if x in (4, 11) else shade
    # blaues K
    blue = (60, 140, 230, 255)
    for x, y in ((6, 5), (6, 6), (6, 7), (6, 8), (6, 9), (7, 7), (8, 6), (8, 5), (8, 8), (8, 9)):
        px[x, y] = blue
    return img


def icons(vroot):
    """[(name, Bild 16 x 16)]; Namen = KitData.Icon in Kleinbuchstaben."""
    tx = lambda p: _square(_tex(vroot, p))  # noqa: E731
    grass_side = _over(tx("block/grass_block_side.png"), _tint(tx("block/grass_block_side_overlay.png"), "91BD59"))
    grass_top = _tint(tx("block/grass_block_top.png"), "91BD59")
    out = [
        ("grass_block", cube(grass_top, grass_side, grass_side)),
        ("chest_block", _chest(vroot, "normal")),
        ("ender_chest", _chest(vroot, "ender")),
        ("red_bed", _bed(vroot)),
        ("white_banner", _banner()),
        ("gold_block", cube(tx("block/gold_block.png"), tx("block/gold_block.png"), tx("block/gold_block.png"))),
        ("diamond_block", cube(tx("block/diamond_block.png"), tx("block/diamond_block.png"), tx("block/diamond_block.png"))),
        ("emerald_block", cube(tx("block/emerald_block.png"), tx("block/emerald_block.png"), tx("block/emerald_block.png"))),
        ("crafting_table", cube(tx("block/crafting_table_top.png"), tx("block/crafting_table_front.png"),
                                tx("block/crafting_table_side.png"))),
        ("bookshelf", cube(tx("block/oak_planks.png"), tx("block/bookshelf.png"), tx("block/bookshelf.png"))),
        ("hay_block", cube(tx("block/hay_block_top.png"), tx("block/hay_block_side.png"), tx("block/hay_block_side.png"))),
        ("oak_log", cube(tx("block/oak_log_top.png"), tx("block/oak_log.png"), tx("block/oak_log.png"))),
        ("tnt", cube(tx("block/tnt_top.png"), tx("block/tnt_side.png"), tx("block/tnt_side.png"))),
        ("enchanting_table", cube(tx("block/enchanting_table_top.png"), tx("block/enchanting_table_side.png"),
                                  tx("block/enchanting_table_side.png"), 0.75)),
        ("barrel", cube(tx("block/barrel_top.png"), tx("block/barrel_side.png"), tx("block/barrel_side.png"))),
        ("scaffolding", cube(tx("block/scaffolding_top.png"), tx("block/scaffolding_side.png"),
                             tx("block/scaffolding_side.png"))),
        ("spawner", cube(tx("block/spawner.png"), tx("block/spawner.png"), tx("block/spawner.png"))),
        ("smithing_table", cube(tx("block/smithing_table_top.png"), tx("block/smithing_table_front.png"),
                                tx("block/smithing_table_side.png"))),
        ("jukebox_block", cube(tx("block/jukebox_top.png"), tx("block/jukebox_side.png"), tx("block/jukebox_side.png"))),
        ("bricks", cube(tx("block/bricks.png"), tx("block/bricks.png"), tx("block/bricks.png"))),
        ("cobblestone", cube(tx("block/cobblestone.png"), tx("block/cobblestone.png"), tx("block/cobblestone.png"))),
        ("iron_block", cube(tx("block/iron_block.png"), tx("block/iron_block.png"), tx("block/iron_block.png"))),
        # Halloween-Event
        ("carved_pumpkin", cube(tx("block/pumpkin_top.png"), tx("block/carved_pumpkin.png"), tx("block/pumpkin_side.png"))),
        ("jack_o_lantern", cube(tx("block/pumpkin_top.png"), tx("block/jack_o_lantern.png"), tx("block/pumpkin_side.png"))),
        ("pumpkin", cube(tx("block/pumpkin_top.png"), tx("block/pumpkin_side.png"), tx("block/pumpkin_side.png"))),
        # Weihnachten
        ("snow_block", cube(tx("block/snow.png"), tx("block/snow.png"), tx("block/snow.png"))),
    ]
    return out


if __name__ == "__main__":
    import sys
    res = icons(sys.argv[1])
    sheet = Image.new("RGBA", (16 * len(res), 16), T)
    for i, (_, im) in enumerate(res):
        sheet.alpha_composite(im, (16 * i, 0))
    sheet.resize((sheet.width * 6, 96), Image.NEAREST).save(sys.argv[2])
