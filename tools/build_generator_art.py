"""Grafiken der Skyblock-Generatoren im Look eines Monster-Spawners: Käfig aus Gitterstäben, dunkles Inneres mit
Glühen in der Farbe der Art und das Symbol der Art schwebend im Käfig. Je Qualität eigene Stäbe (Normal: dunkles Eisen
wie der Vanilla-Spawner, Silber, Gold, Diamant) mit Nieten, Funkeln und Glanz.

Ergebnis:
- tools/assets/generators/art_<art>[_<qualität>].png: 32-px-Bilder für die Dialoge (Sprites gn_...)
- im Pack (write_models): 3D-Item-Modelle kuby:generator/<art>[_<qualität>] (Käfig-Würfel mit Innenseiten wie der
  Spawner, Symbol als gekreuzte Flächen in der Mitte), Texturen cage_<qualität> und core_<art>, dazu flache Items
  kuby:generator_core/<art> (das Symbol, das sich im Spiel im Spawner-Block dreht)
"""
import json
import math
import os

from PIL import Image, ImageDraw

from build_hub_icons import Art, GOLD, GOLD_D, WOOD, WOOD_D, mix, rgb, light, dark, INK

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "assets", "generators")

QUALITIES = ("normal", "silber", "gold", "diamant")
# Qualität -> (Stab, hell, dunkel, Niete)
CAGE = {
    "normal": ("4A5366", "8E9AB2", "232833", "B4BED2"),
    "silber": ("A9B3C3", "F0F4FA", "5C6676", "FFFFFF"),
    "gold": ("DDA42A", "FFE58C", "86560F", "FFF4B8"),
    "diamant": ("3FCFDF", "D2FFFF", "16707E", "FFFFFF"),
}
# Art -> Farbe des Glühens im Käfig
GLOW = {"geld": "F8D35A", "xp": "8CF06A", "erz": "FF9A3A", "holz": "9AD860", "farm": "FFE070", "mob": "C86AF0",
        "nether": "FF5A4A", "edelstein": "5AE6F0", "lohe": "FFB02A", "netherit": "B07AE8", "ender": "3AD8A8",
        "shulker": "F08AD8"}


# ---------------------------------------------------------------- Symbole (16 px)

def core_geld(n=16):
    a = Art(n)
    # drei Münzen gestapelt, vorne eine große mit Prägung
    a.ellipse(1, 9, 9, 13, GOLD_D)
    a.ellipse(1, 7, 9, 11, GOLD)
    a.ellipse(4, 2, 15, 13, GOLD)
    a.ellipse(6, 4, 13, 11, "FFDC62", mode="soft", edge=False)
    a.line([(9, 5), (9, 10)], "B57A1E", 1, mode="flat", edge=False)
    a.line([(8, 6), (10, 6)], "B57A1E", 1, mode="flat", edge=False)
    a.line([(8, 9), (10, 9)], "B57A1E", 1, mode="flat", edge=False)
    a.dots([(6, 4), (7, 3)], "FFF8D0")
    return a


def core_xp(n=16):
    a = Art(n)
    a.ellipse(2, 2, 13, 13, "6AD83E")
    a.ellipse(4, 4, 11, 11, "B8FF6A", mode="soft", edge=False)
    a.ellipse(6, 6, 9, 9, "F2FFC8", mode="flat", edge=False)
    a.dots([(4, 4), (5, 3)], "FFFFFF")
    a.sparkle(13, 2, 1, "E0FFB8")
    return a


def core_erz(n=16):
    a = Art(n)
    a.poly([(2, 6), (6, 2), (12, 3), (14, 8), (11, 13), (4, 13), (1, 10)], "8C8C96")
    for (x, y), c in (((5, 5), "F0B48A"), ((9, 4), "FFE070"), ((10, 9), "6AF0E8"), ((5, 10), "F0B48A")):
        a.rect(x, y, x + 1, y + 1, c, mode="flat")
    a.dots([(6, 5), (10, 4), (11, 9)], "FFFFFF")
    return a


def core_holz(n=16):
    a = Art(n)
    a.rect(2, 3, 13, 13, WOOD)
    a.ellipse(2, 1, 13, 6, "D8B070")
    a.ellipse(5, 2, 10, 5, "B8894A", mode="flat", edge=False)
    a.dots([(7, 3), (8, 3)], "E8C890")
    a.line([(5, 8), (5, 12)], WOOD_D, 1, mode="flat", edge=False)
    a.line([(10, 7), (10, 11)], WOOD_D, 1, mode="flat", edge=False)
    a.sparkle(14, 1, 1, "E8FFC0")
    return a


def core_farm(n=16):
    a = Art(n)
    # Weizengarbe: drei Halme, unten zusammengebunden, oben Ähren
    for p, q in (((7, 14), (3, 5)), ((8, 14), (8, 4)), ((9, 14), (13, 5))):
        a.line([p, q], "B8A030", 1, mode="flat")
    a.ellipse(1, 1, 5, 7, "F2C443")
    a.ellipse(6, 0, 10, 6, "F2C443")
    a.ellipse(11, 1, 15, 7, "F2C443")
    a.rect(5, 10, 11, 11, "A0522D", mode="flat")
    a.dots([(7, 1), (2, 2), (12, 2)], "FFF0B0")
    return a


def core_mob(n=16):
    a = Art(n)
    a.rect(2, 2, 13, 11, "E8E4DA")
    a.rect(4, 11, 11, 14, "D2CEC4")
    a.rect(4, 5, 6, 7, "1A1A20", mode="flat", edge=False)
    a.rect(9, 5, 11, 7, "1A1A20", mode="flat", edge=False)
    a.dots([(7, 9), (8, 9)], "6A6A70")
    a.dots([(5, 12), (7, 12), (9, 12)], "8A8A90")
    a.dots([(4, 5)], "C86AF0")
    a.dots([(9, 5)], "C86AF0")
    return a


def core_nether(n=16):
    a = Art(n)
    a.rect(1, 8, 14, 14, "8A2A2E")
    a.dots([(3, 10), (8, 12), (12, 10), (5, 13)], "C8484A")
    a.poly([(4, 9), (6, 2), (8, 9)], "F4F0E8")
    a.poly([(8, 9), (11, 3), (13, 9)], "E8E0D8")
    a.dots([(6, 4), (11, 5)], "FFFFFF")
    a.sparkle(2, 3, 1, "FFB0A0")
    return a


def core_edelstein(n=16):
    a = Art(n)
    a.poly([(2, 6), (5, 2), (11, 2), (14, 6), (8, 14)], "3FD8E0")
    a.poly([(5, 6), (8, 2), (11, 6), (8, 12)], "A8FFFF", mode="soft", edge=False)
    a.line([(2, 6), (14, 6)], "1E9AA8", 1, mode="flat", edge=False)
    a.dots([(5, 3), (6, 3)], "FFFFFF")
    a.sparkle(14, 1, 1)
    return a


# ---------------------------------------------------------------- Stufen-Generatoren (Nether- und End-Insel)

def core_lohe(n=16):
    a = Art(n)
    # Lohenrute (schräg wie das Item) vor einer Flamme, Glut-Funken
    a.poly([(6, 0), (10, 3), (11, 8), (8, 11), (3, 11), (1, 7), (3, 3)], "D8481E")
    a.poly([(6, 3), (9, 6), (8, 9), (4, 9), (3, 6)], "FF9A2A", mode="soft", edge=False)
    a.dots([(6, 6), (5, 7)], "FFE070")
    a.line([(3, 14), (14, 3)], "F2B030", 3, mode="full")
    a.line([(4, 12), (12, 4)], "FFE68A", 1, mode="flat", edge=False)
    a.dots([(6, 12), (9, 9), (12, 6)], "C8781A")
    a.dots([(13, 9), (12, 12)], "FFC23A")
    a.sparkle(14, 0, 1, "FFE8A0")
    return a


def core_netherit(n=16):
    a = Art(n)
    # Netherit-Barren: Trapez mit heller Oberseite, violetter Schimmer
    a.poly([(1, 9), (4, 5), (15, 5), (12, 9)], "6E6468")
    a.poly([(1, 9), (12, 9), (12, 13), (1, 13)], "3E3640")
    a.poly([(12, 9), (15, 5), (15, 9), (12, 13)], "2A242C")
    a.line([(5, 6), (13, 6)], "9A8E98", 1, mode="flat", edge=False)
    a.dots([(5, 6), (6, 6)], "D8C8E0")
    a.dots([(3, 11), (8, 12)], "6A4E7A")
    a.sparkle(13, 2, 1, "E0C8FF")
    return a


def core_ender(n=16):
    a = Art(n)
    # Enderperle: dunkle Kugel mit grünem Kern
    a.ellipse(2, 2, 13, 13, "1E5E56")
    a.ellipse(4, 4, 11, 11, "2E9A84", mode="soft", edge=False)
    a.ellipse(6, 6, 9, 9, "8CF0C8", mode="flat", edge=False)
    a.dots([(7, 7)], "E8FFF4")
    a.dots([(4, 4), (5, 3)], "B8FFE8")
    a.sparkle(13, 2, 1, "C8FFE8")
    return a


def core_shulker(n=16):
    a = Art(n)
    # Shulker: lila Schale, Deckel leicht offen, Kopf schaut heraus
    a.rect(2, 9, 13, 14, "8A5A9A")
    a.rect(4, 7, 11, 9, "D8E070", mode="flat")
    a.dots([(6, 8), (9, 8)], "1A1A20")
    a.rect(1, 2, 14, 6, "A874B8")
    a.line([(2, 3), (13, 3)], "C898D4", 1, mode="flat", edge=False)
    a.line([(3, 11), (12, 11)], "6E4680", 1, mode="flat", edge=False)
    a.dots([(3, 3), (4, 3)], "F0D8F8")
    a.sparkle(14, 0, 1, "F8D8FF")
    return a


CORES = {"geld": core_geld, "xp": core_xp, "erz": core_erz, "holz": core_holz, "farm": core_farm, "mob": core_mob,
         "nether": core_nether, "edelstein": core_edelstein, "lohe": core_lohe, "netherit": core_netherit, "ender": core_ender,
         "shulker": core_shulker}


# ---------------------------------------------------------------- Käfig-Textur (16 px, für das 3D-Modell)

def cage_texture(q):
    """Gitter wie beim Spawner: Rahmen mit Fase, Stäbe bei 5 und 10, Verstrebungen an den Kreuzungen, Nieten."""
    base, hi, lo, cap = (rgb(c) for c in CAGE[q])
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def put(x, y, c):
        px[x, y] = c + (255,)

    for i in range(16):
        put(i, 0, hi)
        put(0, i, mix(hi, base, 0.4))
        put(i, 15, lo)
        put(15, i, mix(lo, base, 0.4))
    for i in range(1, 15):
        for b in (5, 10):
            put(b, i, base)
            put(i, b, mix(base, hi, 0.35))
    # Verstrebungen: kleine Kreuze an den vier Kreuzungen, Ecken innen verstärkt
    for cx in (5, 10):
        for cy in (5, 10):
            put(cx, cy, hi)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                put(cx + dx, cy + dy, mix(base, hi, 0.5))
    # Eckwinkel innen (L-Form) und Schatten unter den Querstäben: wirkt wie geschmiedet
    for cx, cy, sx, sy in ((1, 1, 1, 1), (14, 1, -1, 1), (1, 14, 1, -1), (14, 14, -1, -1)):
        put(cx, cy, base)
        put(cx + sx, cy, mix(base, lo, 0.3))
        put(cx, cy + sy, mix(base, lo, 0.3))
    for b in (5, 10):
        for i in (2, 3, 7, 8, 12, 13):
            put(i, b + 1, mix(lo, base, 0.35))
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        put(x, y, cap)
    for x, y in ((5, 0), (10, 0), (0, 5), (0, 10), (15, 5), (15, 10), (5, 15), (10, 15)):
        put(x, y, mix(cap, base, 0.3))
    return img


# ---------------------------------------------------------------- Dialog-Bild (32 px): Käfig in Iso-Ansicht

T, R, F, L = (16, 2), (28, 8), (16, 14), (4, 8)
L2, F2, R2, K = (4, 22), (16, 28), (28, 22), (16, 16)
H = 14


def _lerp(p, q, t):
    return (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)


def _add(p, d):
    return (p[0] + d[0], p[1] + d[1])


def _sub(p, q):
    return (p[0] - q[0], p[1] - q[1])


def _face_lines(a, b, c):
    """Stäbe einer Fläche a-b-(a+c): je zwei Stäbe in beide Richtungen (Drittel), dazu der Rahmen."""
    ab, ac = _sub(b, a), c
    lines = []
    for t in (1 / 3, 2 / 3):
        p = _add(a, (ab[0] * t, ab[1] * t))
        lines.append((p, _add(p, ac)))
        p = _add(a, (ac[0] * t, ac[1] * t))
        lines.append((p, _add(p, ab)))
    return lines


def _line(img, p, q, col, alpha=255):
    d = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(d).line([(round(p[0]), round(p[1])), (round(q[0]), round(q[1]))], fill=col + (alpha,), width=1)
    img.alpha_composite(d)


def art(kind, q):
    base, hi, lo, cap = (rgb(c) for c in CAGE[q])
    glow = rgb(GLOW[kind])
    W = 32
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    # Schatten am Boden
    sh = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse((4, 25, 28, 31), fill=(0, 0, 0, 80))
    img.alpha_composite(sh)
    sil = [T, R, R2, F2, L2, L]
    mask = Image.new("L", (W, W), 0)
    ImageDraw.Draw(mask).polygon(sil, fill=255)
    # Inneres: dunkel, nach unten dunkler, Glühen der Art von der Mitte
    inner = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    ip = inner.load()
    mp = mask.load()
    for y in range(W):
        for x in range(W):
            if mp[x, y] < 128:
                continue
            d = math.hypot((x - 16) / 1.0, (y - 15.5) / 0.95)
            g = max(0.0, 1 - d / 12.5) ** 1.6
            c = mix(mix((22, 24, 34), (10, 11, 16), y / W), glow, min(0.85, g * 0.95))
            ip[x, y] = c + (255,)
    img.alpha_composite(inner)
    # Hinten: nur die Kanten, dunkel und vom Glühen gefärbt
    back = mix(mix(base, (14, 15, 22), 0.4), glow, 0.3)
    down = (0, H)
    for p, qq in ((T, K), (K, L2), (K, R2)):
        _line(img, p, qq, back, 220)
    # Innengitter der vorderen Flächen (dezent), darüber das Symbol
    bars = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    for (a, b, c), col in (((L, F, down), base), ((F, R, down), mix(base, lo, 0.45)), ((L, T, _sub(F, L)), mix(base, hi, 0.4))):
        for p, qq in _face_lines(a, b, c):
            _line(bars, p, qq, col, 150)
    img.alpha_composite(bars)
    core = CORES[kind](16).render()
    img.alpha_composite(core, (8, 8))
    cmask = core.split()[3].point(lambda v: 255 if v > 0 else 0)
    cm = cmask.load()
    # Rahmen: Außenkanten doppelt (innen eine zweite Linie), vordere Kanten einfach; über dem Symbol durchscheinend
    front = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    for p, qq, col in ((T, R, hi), (T, L, hi), (L, L2, base), (R, R2, lo), (L2, F2, lo), (F2, R2, lo)):
        _line(front, p, qq, col)
    for (p, qq), col in ((((16, 3), (26, 8)), mix(hi, base, 0.4)), (((16, 3), (6, 8)), mix(hi, base, 0.4)),
                         (((5, 9), (5, 21)), mix(base, lo, 0.3)), (((27, 9), (27, 21)), mix(base, lo, 0.6)),
                         (((5, 22), (16, 27.5)), mix(base, lo, 0.5)), (((16, 27.5), (27, 22)), mix(base, lo, 0.7))):
        _line(front, p, qq, col)
    for p, qq, col in ((L, F, hi), (F, R, mix(hi, base, 0.5)), (F, F2, base)):
        _line(front, p, qq, col)
    fp = front.load()
    for y in range(W):
        for x in range(W):
            r, g, b, a = fp[x, y]
            if a and 0 <= x - 8 < 16 and 0 <= y - 8 < 16 and cm[x - 8, y - 8]:
                fp[x, y] = (r, g, b, 110)
    img.alpha_composite(front)
    # Licht, das durch die Stäbe fällt: Stäbe nahe der Mitte nehmen die Farbe des Glühens an
    px = img.load()
    for y in range(W):
        for x in range(W):
            if mp[x, y] < 128:
                continue
            d = math.hypot(x - 16, y - 15.5)
            if d < 9 and fp[x, y][3] == 255:
                px[x, y] = mix(px[x, y][:3], glow, 0.35 * (1 - d / 9)) + (255,)
    # Umriss fast schwarz, leicht in der Stabfarbe
    out = mix(base, INK, 0.88)
    sp = mask.load()
    edge = []
    for y in range(W):
        for x in range(W):
            if sp[x, y] >= 128:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < W and 0 <= yy < W and sp[xx, yy] >= 128:
                    edge.append((x, y))
                    break
    for x, y in edge:
        px[x, y] = out + (255,)
    # Nieten an den Ecken
    for x, y in (T, R, L, L2, R2, F2):
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            xx, yy = x + dx, y + dy
            if 0 <= xx < W and 0 <= yy < W and sp[xx, yy] >= 128:
                px[xx, yy] = (cap if (dx, dy) == (0, 0) else mix(cap, base, 0.45)) + (255,)
    # Funken über dem Käfig (wie die Flammen am Spawner) und Funkeln je Qualität
    for x, y, a in ((9, 1, 200), (23, 0, 160), (27, 4, 120)):
        if px[x, y][3] == 0:
            px[x, y] = glow + (a,)
    extra = {"normal": [], "silber": [(30, 12, 1)], "gold": [(30, 11, 2), (2, 4, 1)], "diamant": [(30, 10, 2), (2, 3, 1), (29, 27, 1)]}[q]
    sparkle_col = {"normal": "FFFFFF", "silber": "FFFFFF", "gold": "FFF2B0", "diamant": "C8FFFF"}[q]
    for x, y, s in extra:
        sc = rgb(sparkle_col)
        px[x, y] = (255, 255, 255, 255)
        for i in range(1, s + 1):
            for dx, dy in ((i, 0), (-i, 0), (0, i), (0, -i)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < W and 0 <= yy < W:
                    px[xx, yy] = sc + (255 if i < s else 170,)
    return img


def sprite_name(kind, q):
    return kind if q == "normal" else kind + "_" + q


def build():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith(".png"):
            os.remove(os.path.join(OUT, f))
    for kind in CORES:
        for q in QUALITIES:
            art(kind, q).save(os.path.join(OUT, "art_%s.png" % sprite_name(kind, q)))
    return list(CORES)


# ---------------------------------------------------------------- Item-Modelle im Pack

def _cube(texture, inner=False):
    eps = 0.002
    if inner:
        frm, to, uv = [16 - eps, eps, eps], [eps, 16 - eps, 16 - eps], [16, 0, 0, 16]
    else:
        frm, to, uv = [0, 0, 0], [16, 16, 16], [0, 0, 16, 16]
    return {"from": frm, "to": to, "faces": {f: {"uv": uv, "texture": texture} for f in
                                             ("north", "east", "south", "west", "up", "down")}}


def _plane(angle):
    return {"from": [2, 2, 8], "to": [14, 14, 8], "shade": False, "light_emission": 15,
            "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": angle},
            "faces": {"north": {"uv": [16, 0, 0, 16], "texture": "#core"}, "south": {"uv": [0, 0, 16, 16], "texture": "#core"}}}


def write_models(out):
    tdir = os.path.join(out, "assets/kuby/textures/item/generator")
    mdir = os.path.join(out, "assets/kuby/models/item/generator")
    idir = os.path.join(out, "assets/kuby/items/generator")
    cmdir = os.path.join(out, "assets/kuby/models/item/generator_core")
    cidir = os.path.join(out, "assets/kuby/items/generator_core")
    for d in (tdir, mdir, idir, cmdir, cidir):
        os.makedirs(d, exist_ok=True)
    for q in QUALITIES:
        cage_texture(q).save(os.path.join(tdir, "cage_%s.png" % q))
    names = []
    for kind, fn in CORES.items():
        fn(16).render().save(os.path.join(tdir, "core_%s.png" % kind))
        with open(os.path.join(cmdir, kind + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/generator/core_" + kind}}, f)
        with open(os.path.join(cidir, kind + ".json"), "w", encoding="utf-8") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/generator_core/" + kind}}, f)
        for q in QUALITIES:
            name = sprite_name(kind, q)
            model = {"parent": "minecraft:block/block",
                     "textures": {"cage": "kuby:item/generator/cage_" + q, "core": "kuby:item/generator/core_" + kind,
                                  "particle": "kuby:item/generator/cage_" + q},
                     "elements": [_plane(45), _plane(-45), _cube("#cage"), _cube("#cage", inner=True)]}
            with open(os.path.join(mdir, name + ".json"), "w", encoding="utf-8") as f:
                json.dump(model, f)
            with open(os.path.join(idir, name + ".json"), "w", encoding="utf-8") as f:
                json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/generator/" + name}}, f)
            names.append(name)
    return names


if __name__ == "__main__":
    print("ok Generator-Grafiken:", ", ".join(build()))
