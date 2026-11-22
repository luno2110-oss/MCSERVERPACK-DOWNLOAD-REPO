#!/usr/bin/env python3
"""Plinko-Grafik (v2.05, eigener Kuby-Stil nach tools/icon_db/STYLE.md und dem Lieblingsstil des Besitzers:
Tiefe durch Ebenen, Glühen aus dem Inneren, gedeckte tiefe Farben, Nieten an den Ecken).

Schreibt in das Pack (Ordner assets/kuby):
  * Würfel-Materialien m_<name> (textures/block/casino/<name>.png), wie in build_casino_px.MATERIALS:
      hd_plinko_dark  Rückwand außen (tiefes Pflaume)
      hd_plinko       Innenfeld, glüht zur Mitte hin auf (Ringe in großen Pixeln)
      hd_peg / hd_peg_lit   Stift ruhig (Silber) / getroffen (rosa-weiß)
      hd_pl0 .. hd_pl6 (+ _lit)  Fächer nach Gewinnstufe: Mitte gedeckt violett → Rand heißes Magenta
      hd_pl_btn / hd_pl_btn_lit  Abwurf-Knopf
  * pl_ball: Kugel als flaches Bild (16 x 16), wird im Spiel immer zur Kamera gedreht (sieht rund aus)
  * Icon plinko_big (32 x 32) für den Dialog-Kopf, über build_casino.build() in den großen Icons

Aufruf: python3 tools/build_plinko.py [pack] [--sheet bild.png]
"""
import json
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_casino as bc  # noqa: E402

rgb = bc.rgb
T = (0, 0, 0, 0)
OUT = rgb("1A1218")


def ramp(*hexes):
    return [rgb(h) for h in hexes]


PLUM = ramp("120A1E", "1A1030", "241642", "301E56", "3E2A6C")
SILVER = ramp("5A5E70", "8E95A8", "C4CAD8", "EEF1F6", "FFFFFF")
GLOW = ramp("B0207A", "E84AA6", "FF8ACB", "FFD2EC", "FFFFFF")
GOLD = ramp("7A4A0C", "B8740A", "E8A412", "F8CC2A", "FCEB7A")
# Gewinnstufen der Fächer: 0 = Mitte (kleinster Faktor) … 6 = Rand (größter). Farbtöne laufen über Violett, Blau,
# Türkis, Gold, Orange und Rot zu Magenta; je Stufe vier Töne, gedeckt statt grell.
TIERS = [
    ramp("2A2140", "3A2E58", "4E3E72", "62508C"),
    ramp("1A2A6A", "24409A", "3058C0", "4E78DA"),
    ramp("0C4A5A", "106A7A", "18909A", "3EB8B4"),
    ramp("6A4A08", "9A6E10", "C8961A", "E8B83A"),
    ramp("7A3006", "A8480C", "D2661A", "EE8A3A"),
    ramp("6A0A16", "9A1222", "C81E30", "E8404A"),
    ramp("6A0A4A", "9A1068", "C81E88", "EE48AA"),
]


def lighter(c, f):
    return tuple(min(255, int(v + (255 - v) * f)) for v in c[:3]) + (255,)


def mat(fn):
    img = Image.new("RGBA", (16, 16), T)
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = fn(x, y)
    return img


def mat_ramp(r):
    """Wie build_casino_px.mat_ramp: Oberkante hell, obere Hälfte Mitte, untere dunkel (Licht von oben)."""
    return mat(lambda x, y: r[3] if y == 0 else r[2] if y < 6 else r[1])


def mat_lit(r):
    """Leuchtend: eine Stufe heller, Kern fast weiß (Licht aus dem Inneren)."""
    hi = [r[2], r[3], lighter(r[3], 0.45), lighter(r[3], 0.8)]
    return mat(lambda x, y: hi[3] if 5 <= x <= 10 and 5 <= y <= 10 and (x, y) not in ((5, 5), (10, 5), (5, 10), (10, 10))
               else hi[2] if 3 <= x <= 12 and 3 <= y <= 12 else hi[1] if y > 0 else hi[2])


def board_inner():
    """Innenfeld: glüht zur Mitte (etwas unter der Mitte, wo die Kugeln dicht fallen) in vier Ringen auf."""
    def f(x, y):
        d = math.hypot((x - 7.5) / 8.0, (y - 8.5) / 8.0)
        return PLUM[4] if d < 0.32 else PLUM[3] if d < 0.62 else PLUM[2] if d < 0.92 else PLUM[1]
    return mat(f)


def materials():
    m = {
        "hd_plinko_dark": lambda: mat(lambda x, y: PLUM[1] if y == 0 else PLUM[0]),
        "hd_plinko": board_inner,
        "hd_peg": lambda: mat_ramp(SILVER[1:]),
        "hd_peg_lit": lambda: mat_lit(GLOW),
        "hd_pl_btn": lambda: mat_ramp(TIERS[6]),
        "hd_pl_btn_lit": lambda: mat_lit(TIERS[6]),
    }
    for i, r in enumerate(TIERS):
        m["hd_pl%d" % i] = (lambda r=r: mat_ramp(r))
        m["hd_pl%d_lit" % i] = (lambda r=r: mat_lit(r))
    return m


def ball():
    """Kugel 16 x 16: Kugel-Licht von oben links in fünf Stufen (Rampe Magenta-Rosa), 1-px-Kontur, Glanzpunkt."""
    img = Image.new("RGBA", (16, 16), T)
    px = img.load()
    rp = ramp("6A0A4A", "B0187A", "E8409E", "FF7EC2", "FFC8E6")
    cx = cy = 7.5
    r = 6.2
    lx, ly, lz = -0.55, -0.62, 0.56
    inside = set()
    for y in range(16):
        for x in range(16):
            dx, dy = (x - cx) / r, (y - cy) / r
            if dx * dx + dy * dy <= 1.0:
                inside.add((x, y))
    for x, y in inside:
        dx, dy = (x - cx) / r, (y - cy) / r
        dz = math.sqrt(max(0.0, 1 - dx * dx - dy * dy))
        lam = max(0.0, dx * lx + dy * ly + dz * lz)
        i = 4 if lam > 0.93 else 3 if lam > 0.72 else 2 if lam > 0.42 else 1 if lam > 0.15 else 0
        px[x, y] = rp[i]
    # 1-px-Kontur außen (fast schwarz, leicht getönt)
    for y in range(16):
        for x in range(16):
            if (x, y) in inside:
                continue
            if any((x + ax, y + ay) in inside for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUT
    # Glanzpunkt oben links (zwei Pixel)
    px[5, 4] = rgb("FFFFFF")
    px[4, 5] = rgb("FFF2FA")
    return img


def icon():
    """Dialog-Icon 32 x 32: dunkles Brett mit Goldrahmen, Stift-Dreieck, Kugel, Fächer in den Gewinnfarben."""
    p = bc.Pix(32)
    for y in range(2, 30):
        for x in range(3, 29):
            corner = (x in (3, 28)) and (y in (2, 29))
            if corner:
                continue
            edge = x in (3, 28) or y in (2, 29)
            d = ((x - 15.5) / 8.5) ** 2 + ((y - 15) / 7.5) ** 2
            p.put(x, y, GOLD[2] if edge and y < 16 else GOLD[1] if edge else PLUM[3] if d < 0.55 else PLUM[2] if d < 1.25
                  else PLUM[1])
    # Nieten an den Ecken
    for x, y in ((4, 3), (27, 3), (4, 28), (27, 28)):
        p.put(x, y, GOLD[4] if y == 3 else GOLD[3])
    # Stifte: 5 Reihen (2, 3, 4, 5, 6 Stifte), Abstand 4 px
    for row in range(5):
        n = row + 2
        y = 6 + row * 3
        for j in range(n):
            x = int(round(16 - (n - 1) * 2 + j * 4)) - 1
            p.put(x, y, SILVER[3] if j != n - 1 or row != 2 else SILVER[2])
    # Fächer unten: Rand heiß, Mitte gedeckt (symmetrisch)
    cols = [6, 4, 2, 0, 2, 4, 6]
    for k, t in enumerate(cols):
        x0 = 6 + k * 3
        for x in range(x0, x0 + 2):
            p.put(x, 23, TIERS[t][3])
            p.put(x, 24, TIERS[t][2])
            p.put(x, 25, TIERS[t][1])
    # Kugel (3 x 3 mit Licht oben links) zwischen den Stiften, bewusst nicht mittig
    bx, by = 18, 13
    for dx, dy in ((0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (0, 2), (1, 2), (2, 2)):
        p.put(bx + dx, by + dy, rgb("E8409E"))
    p.put(bx, by, rgb("FFC8E6"))
    p.put(bx + 1, by, rgb("FF7EC2"))
    p.put(bx, by + 1, rgb("FF7EC2"))
    p.put(bx + 2, by + 2, rgb("B0187A"))
    return p.image(outline=OUT)


def write(pack):
    a = os.path.join(pack, "assets/kuby")
    tex = os.path.join(a, "textures/block/casino")
    itex = os.path.join(a, "textures/item/casino")
    models = os.path.join(a, "models/item/casino")
    items = os.path.join(a, "items/casino")
    for d in (tex, itex, models, items):
        os.makedirs(d, exist_ok=True)

    def item(name, model):
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump(model, fh)
        with open(os.path.join(items, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/" + name}}, fh)

    B = "kuby:block/casino/"
    n = 0
    for name, fn in materials().items():
        fn().save(os.path.join(tex, name + ".png"))
        faces = {d: {"texture": "#all", "uv": [0, 0, 16, 16]} for d in ("north", "south", "east", "west", "up", "down")}
        item("m_" + name, {"textures": {"all": B + name, "particle": B + name},
                           "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]})
        n += 1
    ball().save(os.path.join(itex, "pl_ball.png"))
    item("pl_ball", {"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/casino/pl_ball"}})
    return n + 1


def sheet(path):
    """Kontaktbogen 8-fach: Materialien, Kugel, Icon."""
    mats = list(materials().items())
    W = 10 + len(mats) * 70
    out = Image.new("RGBA", (max(W, 700), 400), (30, 28, 36, 255))
    for i, (_, fn) in enumerate(mats):
        out.alpha_composite(fn().resize((64, 64), Image.NEAREST), (10 + i * 70, 10))
    out.alpha_composite(ball().resize((128, 128), Image.NEAREST), (10, 100))
    out.alpha_composite(icon().resize((256, 256), Image.NEAREST), (160, 100))
    small = icon()
    out.alpha_composite(small, (440, 100))
    out.alpha_composite(ball(), (440, 150))
    out.save(path)


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--sheet":
        sheet(args[1])
    else:
        print(write(args[0] if args else os.path.join(os.path.dirname(HERE), "resourcepack/KubyUI")), "Plinko-Modelle")
