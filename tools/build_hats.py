#!/usr/bin/env python3
"""Hüte für die Kosmetik: Item-Modelle aus Quadern (kuby:hat/<id>) und Icons (32 x 32, gerendert mit preview3d/r3d).

Jeder Hut hat eine eigene Textur (32 x 32) aus Farbfeldern zu 8 x 8 Pixeln (leichtes Rauschen, dunklerer Rand,
heller oben). Koordinaten in Modell-Einheiten (16 = 1 Block): Kopf oben bei y = 8, Mitte x = z = 8, Kopf mit
Hut-Schicht 8,5 breit (Spieler-Modell 0,9375). Vorne = Norden (kleines z), so zeigt der Hut in Blickrichtung.
Das Plugin setzt die Anzeige auf den Spieler (Mitfahrer) und dreht sie mit Kopf-Gier und -Neigung.
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))

CELL = 8          # Pixel je Farbfeld
GRID = 4          # 4 x 4 Felder je Textur (32 x 32)


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


class Hat:
    """Ein Hut: Farben (Name -> Hex) und Quader."""

    def __init__(self, hid, name, colors, glow=False, seed=1):
        self.id, self.name, self.glow, self.seed = hid, name, glow, seed
        self.colors = dict(colors)
        self.cells = {c: i for i, c in enumerate(self.colors)}
        assert len(self.cells) <= GRID * GRID, hid
        self.elements = []

    def uv(self, color):
        i = self.cells[color]
        cx, cy = i % GRID, i // GRID
        s = 16 / GRID
        return [cx * s, cy * s, cx * s + s, cy * s + s]

    def box(self, x0, y0, z0, x1, y1, z1, color, top=None, rot=None, faces=None):
        """Quader von (x0, y0, z0) bis (x1, y1, z1); top = andere Farbe oben; rot = (Achse, Winkel, Ursprung)."""
        fs = {}
        for d in faces or ("north", "south", "east", "west", "up", "down"):
            c = top if (d == "up" and top) else color
            fs[d] = {"texture": "#t", "uv": self.uv(c)}
        el = {"from": [round(v, 3) for v in (x0, y0, z0)], "to": [round(v, 3) for v in (x1, y1, z1)], "faces": fs}
        if rot:
            axis, angle, origin = rot
            el["rotation"] = {"axis": axis, "angle": angle, "origin": list(origin)}
        self.elements.append(el)
        return self

    def cube(self, cx, cy, cz, hx, hy, hz, color, **kw):
        """Quader um eine Mitte mit halben Kantenlängen."""
        return self.box(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, color, **kw)

    def ring(self, y0, y1, half, thick, color, top=None, cz=8.0):
        """Viereckiger Ring um die Kopfmitte (außen 2 * half breit, Wandstärke thick)."""
        a, b = 8 - half, 8 + half
        z0, z1 = cz - half, cz + half
        self.box(a, y0, z0, b, y1, z0 + thick, color, top=top)
        self.box(a, y0, z1 - thick, b, y1, z1, color, top=top)
        self.box(a, y0, z0 + thick, a + thick, y1, z1 - thick, color, top=top)
        self.box(b - thick, y0, z0 + thick, b, y1, z1 - thick, color, top=top)
        return self

    def model(self):
        return {"textures": {"t": "kuby:item/hat/" + self.id, "particle": "kuby:item/hat/" + self.id},
                "elements": self.elements}

    def texture(self):
        rnd = random.Random(self.seed * 7919 + len(self.id))
        img = Image.new("RGBA", (CELL * GRID, CELL * GRID), (0, 0, 0, 0))
        px = img.load()
        for name, i in self.cells.items():
            base = np.array(hexrgb(self.colors[name]), dtype=float)
            metal = name.startswith("gold") or name.startswith("metal") or name.startswith("gem")
            soft = name.startswith("fur") or name.startswith("wool") or name.startswith("straw")
            cx, cy = (i % GRID) * CELL, (i // GRID) * CELL
            for y in range(CELL):
                for x in range(CELL):
                    k = 1.0 + rnd.uniform(-0.05, 0.05) * (1.8 if soft else 1.0)
                    k *= 1.04 - 0.012 * y                       # Licht von oben
                    if x in (0, CELL - 1) or y in (0, CELL - 1):
                        k *= 0.9                                 # Kante
                    if metal and (x + y) % 5 == 0 and 0 < x < CELL - 1 and 0 < y < CELL - 1:
                        k *= 1.18                                # Glanzpunkte
                    if name.startswith("straw") and (x + 2 * y) % 4 == 0:
                        k *= 0.88                                # Flechtmuster
                    c = np.clip(base * k, 0, 255).astype(int)
                    px[cx + x, cy + y] = (int(c[0]), int(c[1]), int(c[2]), 255)
        return img


# =====================================================================================================================
# Die Hüte
# =====================================================================================================================

def hats():
    out = []

    h = Hat("zylinder", "Zylinder", {"felt": "#1E1E26", "brim": "#2A2A34", "band": "#A8202C", "top": "#26262F"})
    h.box(2, 5.5, 2, 14, 6.5, 14, "brim")
    h.box(3.5, 6.5, 3.5, 12.5, 15.5, 12.5, "felt", top="top")
    h.box(3.35, 6.5, 3.35, 12.65, 8.2, 12.65, "band", faces=("north", "south", "east", "west"))
    out.append(h)

    h = Hat("krone", "Krone", {"gold": "#F2C230", "golddark": "#C9971C", "gemred": "#E0303C", "gemblue": "#3C78E8",
                               "gemgreen": "#2FBF5A", "velvet": "#8E1E2E"}, seed=3)
    h.ring(6.5, 9.5, 4.75, 1.0, "gold", top="golddark")
    h.box(4.0, 6.5, 4.0, 12.0, 8.0, 12.0, "velvet")
    for (x, z) in ((3.25, 3.25), (11.75, 3.25), (3.25, 11.75), (11.75, 11.75)):
        h.box(x, 9.5, z, x + 1, 11.5, z + 1, "gold")
    for (cx, cz) in ((8, 3.75), (8, 12.25), (3.75, 8), (12.25, 8)):
        hx, hz = (0.75, 0.5) if cz in (3.75, 12.25) else (0.5, 0.75)
        h.cube(cx, 11.0, cz, hx, 1.5, hz, "gold")
        h.cube(cx, 12.85, cz, 0.45, 0.35, 0.45, "gemred")
    h.box(7.2, 7.4, 2.95, 8.8, 8.9, 3.25, "gemred")
    h.box(7.2, 7.4, 12.75, 8.8, 8.9, 13.05, "gemblue")
    h.box(2.95, 7.4, 7.2, 3.25, 8.9, 8.8, "gemgreen")
    h.box(12.75, 7.4, 7.2, 13.05, 8.9, 8.8, "gemgreen")
    out.append(h)

    h = Hat("partyhut", "Partyhut", {"pink": "#FF5FA8", "cyan": "#38D4E8", "yellow": "#FFD43A", "white": "#F8F8F8",
                                     "purple": "#9B5CFF"}, seed=5)
    tilt = ("z", -22.5, (8, 7, 8))
    for i, (half, col) in enumerate(((4.0, "pink"), (3.2, "cyan"), (2.4, "yellow"), (1.6, "purple"), (0.9, "pink"))):
        y0 = 7 + i * 2.2
        h.box(8 - half + 1, y0, 8 - half, 8 + half + 1, y0 + 2.2, 8 + half, col, rot=tilt)
    h.cube(9, 19.2, 8, 1.3, 1.2, 1.3, "white", rot=tilt)
    out.append(h)

    h = Hat("hexenhut", "Hexenhut", {"felt": "#2B1B3A", "brim": "#241630", "band": "#6A2C91", "gold": "#E8B830",
                                     "top": "#33213F"}, seed=7)
    h.box(1, 6.5, 1, 15, 7.5, 15, "brim")
    h.box(3.5, 7.5, 3.5, 12.5, 10.5, 12.5, "felt")
    h.box(3.35, 7.5, 3.35, 12.65, 9.2, 12.65, "band", faces=("north", "south", "east", "west"))
    h.box(7.0, 7.6, 3.1, 9.0, 9.1, 3.35, "gold")
    h.box(4.5, 10.5, 4.6, 11.5, 13.0, 11.6, "felt")
    h.box(5.5, 13.0, 5.9, 10.5, 15.5, 10.9, "felt")
    h.box(6.4, 15.5, 7.4, 9.6, 17.6, 10.6, "felt")
    h.box(7.1, 17.6, 9.0, 8.9, 19.0, 10.8, "felt", top="top")
    h.box(7.5, 18.3, 10.8, 8.5, 19.0, 12.6, "felt")
    out.append(h)

    h = Hat("cowboyhut", "Cowboyhut", {"leather": "#8B5A2B", "brim": "#7A4E24", "band": "#3B2414", "top": "#9A6634"},
            seed=11)
    h.box(1.5, 6.0, 2.0, 14.5, 7.0, 14.0, "brim")
    h.box(1.5, 7.0, 3.0, 2.5, 8.2, 13.0, "brim")
    h.box(13.5, 7.0, 3.0, 14.5, 8.2, 13.0, "brim")
    h.box(3.75, 7.0, 4.0, 12.25, 11.5, 12.0, "leather", top="top")
    h.box(3.6, 7.0, 3.85, 12.4, 8.2, 12.15, "band", faces=("north", "south", "east", "west"))
    h.box(3.75, 11.5, 4.5, 7.4, 12.4, 11.5, "leather", top="top")
    h.box(8.6, 11.5, 4.5, 12.25, 12.4, 11.5, "leather", top="top")
    out.append(h)

    h = Hat("wikinger", "Wikingerhelm", {"metal": "#9AA0A8", "metaldark": "#6E737A", "bone": "#EDE3C8",
                                         "bonetip": "#B9AE8E", "rivet": "#C8CDD3"}, seed=13)
    h.box(3.5, 5.5, 3.5, 12.5, 10.0, 12.5, "metal")
    h.box(4.5, 10.0, 4.5, 11.5, 11.0, 11.5, "metal")
    h.box(3.35, 5.5, 3.35, 12.65, 6.6, 12.65, "metaldark", faces=("north", "south", "east", "west", "up"))
    h.box(7.4, 3.2, 3.0, 8.6, 6.6, 3.5, "metaldark")
    h.box(7.5, 6.6, 3.3, 8.5, 11.2, 12.7, "metaldark", faces=("north", "south", "up"))
    for x in (5.0, 10.5):
        h.box(x, 7.5, 3.3, x + 0.6, 8.1, 3.5, "rivet")
    h.box(1.5, 8.0, 7.0, 3.5, 10.0, 9.0, "bone")
    h.box(0.6, 10.0, 7.2, 2.2, 12.5, 8.8, "bone")
    h.box(0.2, 12.5, 7.5, 1.4, 14.0, 8.5, "bonetip")
    h.box(12.5, 8.0, 7.0, 14.5, 10.0, 9.0, "bone")
    h.box(13.8, 10.0, 7.2, 15.4, 12.5, 8.8, "bone")
    h.box(14.6, 12.5, 7.5, 15.8, 14.0, 8.5, "bonetip")
    out.append(h)

    h = Hat("heiligenschein", "Heiligenschein", {"gold": "#FFE97A", "goldlight": "#FFF6C4"}, glow=True, seed=17)
    h.ring(11.5, 12.3, 4.6, 1.1, "gold", top="goldlight")
    out.append(h)

    h = Hat("kochmuetze", "Kochmütze", {"wool": "#F2F2F2", "woolshade": "#E2E2E6", "top": "#FFFFFF"}, seed=19)
    h.box(3.5, 5.5, 3.5, 12.5, 9.0, 12.5, "woolshade")
    h.box(2.6, 9.0, 2.6, 13.4, 13.5, 13.4, "wool", top="top")
    h.box(3.6, 13.5, 3.6, 12.4, 14.4, 12.4, "wool", top="top")
    out.append(h)

    h = Hat("strohhut", "Strohhut", {"straw": "#E3C46B", "strawdark": "#CDAA4E", "band": "#C0392B"}, seed=23)
    h.box(1.5, 6.0, 1.5, 14.5, 7.0, 14.5, "straw")
    h.box(3.75, 7.0, 3.75, 12.25, 10.5, 12.25, "strawdark", top="straw")
    h.box(3.6, 7.0, 3.6, 12.4, 8.3, 12.4, "band", faces=("north", "south", "east", "west"))
    out.append(h)

    h = Hat("weihnachtsmuetze", "Weihnachtsmütze", {"fur": "#F5F5F5", "red": "#C8222C", "reddark": "#A81A24"}, seed=29)
    h.box(3.35, 5.5, 3.35, 12.65, 7.5, 12.65, "fur")
    h.box(3.75, 7.5, 3.75, 12.25, 10.0, 12.25, "red")
    h.box(4.5, 10.0, 4.9, 11.5, 12.0, 11.9, "red")
    h.box(5.5, 12.0, 6.4, 10.5, 13.4, 11.4, "reddark")
    h.box(6.6, 12.4, 11.4, 9.4, 13.4, 14.0, "reddark")
    h.box(6.4, 11.0, 14.0, 9.6, 13.6, 16.6, "fur")
    out.append(h)

    h = Hat("blumenkranz", "Blumenkranz", {"leaf": "#4E8A3A", "leafdark": "#3C6E2C", "red": "#E23B3B",
                                           "yellow": "#F6D02F", "pink": "#F38BC2", "white": "#F4F4F4",
                                           "blue": "#5A8CF0", "orange": "#F39A2E", "center": "#F6D02F"}, seed=31)
    h.ring(7.2, 8.4, 4.7, 1.3, "leafdark", top="leaf")
    for (x, z, c) in ((8, 3.3, "red"), (4.6, 3.6, "yellow"), (11.4, 3.6, "pink"), (3.3, 7.0, "white"),
                      (12.7, 7.0, "blue"), (3.6, 11.4, "orange"), (12.4, 11.4, "red"), (8, 12.7, "yellow"),
                      (3.3, 9.6, "pink"), (12.7, 9.6, "white")):
        h.cube(x, 8.9, z, 0.85, 0.7, 0.85, c)
    out.append(h)

    h = Hat("piratenhut", "Piratenhut", {"felt": "#1C1C22", "feltlight": "#26262E", "gold": "#D4A72C",
                                         "white": "#F2F2F2"}, seed=37)
    h.box(3.5, 6.0, 3.5, 12.5, 10.0, 12.5, "felt")
    h.box(2.0, 8.2, 2.4, 14.0, 12.2, 3.4, "feltlight")
    h.box(2.0, 12.2, 2.4, 14.0, 12.7, 3.4, "gold")
    h.box(1.4, 7.8, 3.4, 2.4, 11.2, 12.6, "feltlight")
    h.box(13.6, 7.8, 3.4, 14.6, 11.2, 12.6, "feltlight")
    h.box(2.4, 7.8, 12.6, 13.6, 11.6, 13.6, "feltlight")
    h.box(6.9, 9.4, 2.2, 9.1, 11.2, 2.4, "white")
    h.box(5.9, 8.6, 2.25, 10.1, 9.0, 2.4, "white")
    out.append(h)

    h = Hat("bauhelm", "Bauhelm", {"plastic": "#F4C20D", "plasticlight": "#FFD84A", "lamp": "#DADADA"}, seed=41)
    h.box(3.4, 6.0, 3.4, 12.6, 10.0, 12.6, "plastic")
    h.box(4.4, 10.0, 4.4, 11.6, 11.0, 11.6, "plastic", top="plasticlight")
    h.box(7.5, 10.0, 3.6, 8.5, 11.5, 12.4, "plasticlight")
    h.box(3.4, 6.0, 1.6, 12.6, 6.8, 3.4, "plastic")
    h.box(7.0, 7.4, 3.1, 9.0, 9.0, 3.4, "lamp")
    out.append(h)

    h = Hat("teufelshoerner", "Teufelshörner", {"red": "#B3202A", "reddark": "#7E141C", "tip": "#4A0A10"}, seed=43)
    for side in (-1, 1):
        cx = 8 + side * 2.6
        h.cube(cx, 8.0, 7.0, 1.1, 1.2, 1.1, "reddark")
        h.cube(cx + side * 0.6, 10.0, 7.2, 0.85, 1.0, 0.85, "red")
        h.cube(cx + side * 1.1, 11.6, 7.5, 0.6, 0.8, 0.6, "red")
        h.cube(cx + side * 1.4, 12.8, 7.8, 0.35, 0.6, 0.35, "tip")
    out.append(h)

    h = Hat("bommelmuetze", "Bommelmütze", {"wool": "#2E6FD8", "woolcuff": "#F0F0F0", "woolpom": "#F8F8F8",
                                            "woolstripe": "#E8483C"}, seed=47)
    h.box(3.4, 5.5, 3.4, 12.6, 10.5, 12.6, "wool")
    h.box(3.3, 5.5, 3.3, 12.7, 7.2, 12.7, "woolcuff", faces=("north", "south", "east", "west", "up"))
    h.box(3.38, 8.4, 3.38, 12.62, 9.2, 12.62, "woolstripe", faces=("north", "south", "east", "west"))
    h.box(4.4, 10.5, 4.4, 11.6, 11.5, 11.6, "wool")
    h.box(6.5, 11.5, 6.5, 9.5, 14.2, 9.5, "woolpom")
    out.append(h)

    h = Hat("katzenohren", "Katzenohren", {"fur": "#2A2A2E", "pink": "#F2A0B8", "band": "#3A3A40"}, seed=53)
    h.box(3.2, 7.4, 6.6, 12.8, 8.2, 7.8, "band")
    for cx in (5.2, 10.8):
        h.box(cx - 1.6, 7.2, 6.8, cx + 1.6, 10.4, 7.8, "fur", rot=("z", 45, (cx, 8.8, 7.3)))
        h.box(cx - 0.9, 7.9, 6.65, cx + 0.9, 9.7, 6.8, "pink", rot=("z", 45, (cx, 8.8, 7.3)))
    out.append(h)
    return out


# =====================================================================================================================
# Schreiben und Icons
# =====================================================================================================================

def write(out):
    """Schreibt Modelle, Texturen und Item-Definitionen. Gibt die Hüte zurück."""
    hs = hats()
    tdir = os.path.join(out, "assets/kuby/textures/item/hat")
    mdir = os.path.join(out, "assets/kuby/models/item/hat")
    idir = os.path.join(out, "assets/kuby/items/hat")
    for d in (tdir, mdir, idir):
        os.makedirs(d, exist_ok=True)
    for h in hs:
        h.texture().save(os.path.join(tdir, h.id + ".png"))
        with open(os.path.join(mdir, h.id + ".json"), "w", encoding="utf-8") as f:
            json.dump(h.model(), f, separators=(",", ":"))
        with open(os.path.join(idir, h.id + ".json"), "w", encoding="utf-8") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/hat/" + h.id}}, f)
    return hs


def bounds(h):
    pts = []
    for el in h.elements:
        f, t = el["from"], el["to"]
        for x in (f[0], t[0]):
            for y in (f[1], t[1]):
                for z in (f[2], t[2]):
                    pts.append((x, y, z))
    a = np.array(pts) / 16.0 - 0.5
    return a.min(axis=0), a.max(axis=0)


def icon(h, out, size=32, scale=4):
    """Rendert den Hut schräg von vorne oben (wie ein Item im Inventar) und gibt ein 32 x 32-Bild zurück."""
    sys.path.insert(0, os.path.join(HERE, "preview3d"))
    import r3d  # noqa: E402

    if out not in r3d.ROOTS:
        r3d.ROOTS.insert(0, out)

    lo, hi = bounds(h)
    # Anzeige-Ursprung = Modellmitte; ItemDisplay dreht um 180 Grad (Vorderseite nach +Z)
    c = (lo + hi) / 2
    c = np.array([-c[0], c[1], -c[2]])
    r = float(np.linalg.norm(hi - lo)) / 2
    d = np.array([0.62, 0.48, 1.0])
    d /= np.linalg.norm(d)
    fov = 24
    dist = r / math.sin(math.radians(fov / 2)) * 1.02
    cam = c + d * dist
    W = size * scale
    bg = (1.0, 0.0, 1.0)
    rs = r3d.Raster(W, W, cam, c, fov, bg=bg)
    quads = r3d.model_quads("kuby:item/hat/" + h.id)
    r3d.draw_quads(rs, r3d.ry(math.pi) @ r3d.tr(-0.5, -0.5, -0.5), quads)
    img = np.clip(rs.img, 0, 1)
    mask = np.isfinite(rs.z)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = out.load()
    for y in range(size):
        for x in range(size):
            m = mask[y * scale:(y + 1) * scale, x * scale:(x + 1) * scale]
            cov = m.mean()
            if cov < 0.45:
                continue
            col = img[y * scale:(y + 1) * scale, x * scale:(x + 1) * scale][m].mean(axis=0)
            px[x, y] = tuple(int(v * 255) for v in col) + (255,)
    # dunkler Umriss wie bei Item-Icons
    a = np.array(out)[..., 3] > 0
    edge = np.zeros_like(a)
    edge[1:, :] |= a[:-1, :]
    edge[:-1, :] |= a[1:, :]
    edge[:, 1:] |= a[:, :-1]
    edge[:, :-1] |= a[:, 1:]
    edge &= ~a
    arr = np.array(out)
    arr[edge] = (24, 20, 28, 255)
    return Image.fromarray(arr)


# Partikel-Icons für Spuren und Effekte: (Partikel, Textur, Färbung oder None)
PARTICLES = [
    ("flame", "flame", None), ("soul_fire_flame", "soul_fire_flame", None), ("heart", "heart", None),
    ("note", "note", (0.45, 0.95, 0.45)), ("happy_villager", "glint", None), ("snowflake", "generic_4", (0.85, 0.95, 1.0)),
    ("end_rod", "glitter_3", (1.0, 1.0, 0.9)), ("enchant", "sga_c", (0.75, 0.85, 1.0)),
    ("totem_of_undying", "glitter_3", (0.95, 0.85, 0.25)), ("dragon_breath", "generic_5", (0.85, 0.35, 0.95)),
    ("portal", "generic_5", (0.6, 0.3, 0.95)), ("cherry_leaves", "cherry_1", None), ("flash", "flash", (1.0, 1.0, 0.85)),
    ("soul", "soul_3", None), ("explosion", "explosion_4", (0.95, 0.75, 0.45)), ("witch", "spell_3", (0.7, 0.3, 0.9)),
]


def particle_icons(vroot):
    """[(name, Bild 16 x 16)]: drei Partikel schräg hintereinander wie eine Spur."""
    out = []
    for pid, tex, tint in PARTICLES:
        path = os.path.join(vroot, "assets/minecraft/textures/particle", tex + ".png")
        if not os.path.exists(path):
            print("WARNUNG: Partikel-Textur fehlt", tex)
            continue
        src = Image.open(path).convert("RGBA")
        if tint:
            a = np.array(src, dtype=float)
            a[..., :3] = np.clip(a[..., :3] * np.array(tint), 0, 255)
            src = Image.fromarray(a.astype(np.uint8))
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for size, (x, y), alpha in ((5, (0, 0), 0.55), (7, (3, 3), 0.8), (10, (6, 6), 1.0)):
            p = src.resize((size, size), Image.NEAREST if src.width <= size else Image.BOX)
            if alpha < 1:
                a = np.array(p)
                a[..., 3] = (a[..., 3] * alpha).astype(np.uint8)
                p = Image.fromarray(a)
            img.alpha_composite(p, (x, y))
        out.append(("p_" + pid, img))
    return out


def build(out):
    """Für build_ui_pack: schreibt alles und gibt [(icon-name, Bild 32 x 32)] zurück."""
    hs = write(out)
    return [("hat_" + h.id, icon(h, out)) for h in hs]


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(HERE), "resourcepack/KubyUI")
    icons = build(target)
    sheet = Image.new("RGBA", (36 * len(icons), 36), (40, 44, 60, 255))
    for i, (n, im) in enumerate(icons):
        sheet.alpha_composite(im, (36 * i + 2, 2))
    if len(sys.argv) > 2:
        sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(sys.argv[2])
    print(len(icons), "Hüte")
