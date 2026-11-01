"""Items der Events (Quest-Event-Kapitel): Candy Corn (orange und selten lila) als Item-Modelle
kuby:event/candy_corn und kuby:event/candy_corn_purple. Bild aus den Quest-Referenzen (tools/assets/quests/art_candy.png).
"""
import colorsys
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
    tdir = os.path.join(out, "assets/kuby/textures/item/event")
    mdir = os.path.join(out, "assets/kuby/models/item/event")
    idir = os.path.join(out, "assets/kuby/items/event")
    for d in (tdir, mdir, idir):
        os.makedirs(d, exist_ok=True)
    for name, img in (("candy_corn", tex), ("candy_corn_purple", _purple(tex))):
        img.save(os.path.join(tdir, name + ".png"))
        with open(os.path.join(mdir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/event/" + name}}, f)
        with open(os.path.join(idir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/event/" + name}}, f)


if __name__ == "__main__":
    import sys
    build(sys.argv[1])
