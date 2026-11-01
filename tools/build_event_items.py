"""Sammel-Items der Event-Presets als Item-Modelle kuby:event/<name>: Candy Corn (Halloween, orange und selten lila, Bild aus
den Quest-Referenzen tools/assets/quests/art_candy.png) und die Items der übrigen Presets aus build_event_art.py
(tools/assets/events/item_<name>.png: Lebkuchen, Kleeblatt, Herz, Osterei, Muschel und ihre seltenen Sorten).
"""
import colorsys
import glob
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))


def _purple(img):
    """Orange/Gelb auf Lila/Flieder drehen, Weiß und Schatten bleiben."""
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.25:
                h = (h + 0.72) % 1.0
                r2, g2, b2 = colorsys.hsv_to_rgb(h, min(1, s * 0.9), v)
                px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return out


def build(out):
    src = Image.open(os.path.join(HERE, "assets/quests/art_candy.png")).convert("RGBA")
    tex = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    tex.alpha_composite(src, ((16 - src.width) // 2, (16 - src.height) // 2))
    items = [("candy_corn", tex), ("candy_corn_purple", _purple(tex))]
    for f in sorted(glob.glob(os.path.join(HERE, "assets/events/item_*.png"))):
        items.append((os.path.basename(f)[5:-4], Image.open(f).convert("RGBA")))
    # Skyblock-Generatoren als eigene Gruppe: kuby:generator/<art>
    gen = [(os.path.basename(f)[5:-4], Image.open(f).convert("RGBA"))
           for f in sorted(glob.glob(os.path.join(HERE, "assets/generators/item_*.png")))]
    for group, entries in (("event", items), ("generator", gen)):
        write_group(out, group, entries)


def write_group(out, group, items):
    tdir = os.path.join(out, "assets/kuby/textures/item/" + group)
    mdir = os.path.join(out, "assets/kuby/models/item/" + group)
    idir = os.path.join(out, "assets/kuby/items/" + group)
    for d in (tdir, mdir, idir):
        os.makedirs(d, exist_ok=True)
    for name, img in items:
        img.save(os.path.join(tdir, name + ".png"))
        with open(os.path.join(mdir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/" + group + "/" + name}}, f)
        with open(os.path.join(idir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/" + group + "/" + name}}, f)


if __name__ == "__main__":
    import sys
    build(sys.argv[1])
