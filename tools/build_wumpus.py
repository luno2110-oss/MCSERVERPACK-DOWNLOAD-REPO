#!/usr/bin/env python3
"""Discord-Statue: Wumpus als Item-Modell aus Quadern (kuby:deco/wumpus), nach der Referenz (Aufnahme 06.52.59 aus
„Referenzen2“): großer Kopf mit Ohren, leicht zur Seite geneigt, Gesicht mit schwarzen Augen und heller Schnauze,
Körper mit hellem Bauch, ein Arm seitlich ausgestreckt, der andere hängt, zwei kurze Beine. Knapp ein Block hoch.

Koordinaten in Modell-Einheiten (16 = 1 Block), Füße bei y = 0, Mitte x = z = 8. Vorne = Norden (kleines z), wie bei
den Hüten: das ItemDisplay dreht um 180 Grad, die Vorderseite zeigt dann in Blickrichtung der Anzeige.

Textur 32 x 32 (1 Texel = 1/2 UV-Einheit): Gesicht, Bauch und Ohr-Innenseite als Bilder, sonst Farbfelder.
Aufruf: python3 tools/build_wumpus.py [<pack>] [<vorschau.png>]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = 32

BASE = "7B8FE2"       # Fell
SIDE = "6E80D6"       # etwas dunkler (Beine, Unterseiten)
LIGHT = "B9C3EE"      # Schnauze, Bauch, Ohr innen
DARK = "4E5BB8"       # Nasenlöcher, Rand der Schnauze
EYE = "17181F"


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


class Atlas:
    """Textur mit Bildern (Gesicht usw.) und einfarbigen Feldern; uv() gibt UV in 0..16 zurück."""

    def __init__(self):
        self.img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
        self.spots = {}
        self.x, self.y, self.row_h = 0, 0, 0

    def add(self, name, rows, palette):
        h, w = len(rows), len(rows[0])
        if self.x + w > TEX:
            self.x, self.y, self.row_h = 0, self.y + self.row_h, 0
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                self.img.putpixel((self.x + i, self.y + j), rgb(palette[ch]))
        self.spots[name] = (self.x, self.y, w, h)
        self.x += w
        self.row_h = max(self.row_h, h)

    def solid(self, name, color):
        self.add(name, ["cc", "cc"], {"c": color})

    def uv(self, name, mirror=False):
        x, y, w, h = self.spots[name]
        k = 16 / TEX
        if name.startswith("solid:"):
            # Mitte des Feldes, damit beim Filtern nichts Fremdes hineinläuft
            return [(x + 0.5) * k, (y + 0.5) * k, (x + 1.5) * k, (y + 1.5) * k]
        u0, u1 = x * k, (x + w) * k
        return [u1, y * k, u0, (y + h) * k] if mirror else [u0, y * k, u1, (y + h) * k]


def atlas():
    a = Atlas()
    pal = {".": BASE, "l": LIGHT, "d": DARK, "e": EYE}
    # Gesicht 12 x 7 (Vorderseite des Kopfes): Augen schwarz, Schnauze hell mit Nasenlöchern
    a.add("face", [
        "............",
        "..e......e..",
        "..e......e..",
        "...llllll...",
        "...ldlldl...",
        "....llll....",
        "............",
    ], pal)
    # Bauch 6 x 5 (Vorderseite des Körpers)
    a.add("belly", [
        "......",
        ".llll.",
        ".llll.",
        "..ll..",
        "......",
    ], pal)
    # Ohr innen 3 x 5
    a.add("ear", [
        "...",
        ".l.",
        ".l.",
        ".l.",
        "...",
    ], pal)
    for name, col in (("base", BASE), ("side", SIDE), ("light", LIGHT)):
        a.solid("solid:" + name, col)
    return a


class Model:
    def __init__(self, at):
        self.at = at
        self.elements = []

    def box(self, f, t, color="base", front=None, rot=None, front_mirror=False):
        fs = {}
        for d in ("north", "south", "east", "west", "up", "down"):
            name = "solid:" + (color if d != "down" else "side")
            uv = self.at.uv(name)
            if d == "north" and front:
                uv = self.at.uv(front, front_mirror)
            fs[d] = {"texture": "#t", "uv": [round(v, 4) for v in uv]}
        el = {"from": [round(v, 3) for v in f], "to": [round(v, 3) for v in t], "faces": fs}
        if rot:
            axis, angle, origin = rot
            el["rotation"] = {"axis": axis, "angle": angle, "origin": list(origin)}
        self.elements.append(el)


def model(at):
    m = Model(at)
    head_rot = ("z", 22.5, (8, 7.5, 8))
    # Beine
    m.box((5.75, 0, 7.25), (7.25, 3, 8.75), "side")
    m.box((8.75, 0, 7.25), (10.25, 3, 8.75), "side")
    # Körper mit Bauch
    m.box((5, 3, 6), (11, 7.5, 10), "base", front="belly")
    # Arm rechts (vom Betrachter; Modell-x klein, die Anzeige spiegelt): hängt herunter
    m.box((3, 3.5, 7), (5, 7.5, 9), "base")
    # Arm links (vom Betrachter): winkt seitlich, leicht nach unten
    m.box((11, 5.5, 7), (15, 7.5, 9), "base", rot=("z", -22.5, (11, 6.5, 8)))
    # Kopf, Gesicht vorne (Norden), zur Seite geneigt
    m.box((2, 7.5, 4.5), (14, 14.5, 11.5), "base", front="face", rot=head_rot)
    # Ohren mit heller Innenseite
    m.box((-1, 9, 6), (2, 14, 9), "base", front="ear", rot=head_rot)
    m.box((14, 9, 6), (17, 14, 9), "base", front="ear", rot=head_rot, front_mirror=True)
    return {
        "textures": {"t": "kuby:item/deco/wumpus", "particle": "kuby:item/deco/wumpus"},
        "elements": m.elements,
        "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}},
    }


def write(out):
    at = atlas()
    tdir = os.path.join(out, "assets/kuby/textures/item/deco")
    mdir = os.path.join(out, "assets/kuby/models/item/deco")
    idir = os.path.join(out, "assets/kuby/items/deco")
    for d in (tdir, mdir, idir):
        os.makedirs(d, exist_ok=True)
    at.img.save(os.path.join(tdir, "wumpus.png"))
    with open(os.path.join(mdir, "wumpus.json"), "w", encoding="utf-8") as f:
        json.dump(model(at), f, separators=(",", ":"))
    with open(os.path.join(idir, "wumpus.json"), "w", encoding="utf-8") as f:
        json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/deco/wumpus"}}, f)


def preview(out, path, w=480, h=600):
    """Rendert die Statue von vorne leicht von oben (wie in der Referenz) mit Sockel-Andeutung."""
    sys.path.insert(0, os.path.join(HERE, "preview3d"))
    import r3d  # noqa: E402
    if out not in r3d.ROOTS:
        r3d.ROOTS.insert(0, out)
    target = np.array([0.0, 0.55, 0.0])
    cam = target + np.array([0.0, 0.35, 2.6])
    rs = r3d.Raster(w, h, cam, target, 40, bg=(0.45, 0.45, 0.47))
    quads = r3d.model_quads("kuby:item/deco/wumpus")
    # ItemDisplay: Ry(pi) · Modell (-0.5 zentriert), Füße auf y = 0 (Anzeige bei Boden + 0.5)
    r3d.draw_quads(rs, r3d.tr(0, 0.5, 0) @ r3d.ry(math.pi) @ r3d.tr(-0.5, -0.5, -0.5), quads)
    img = (np.clip(rs.img, 0, 1) * 255).astype(np.uint8)
    Image.fromarray(img).save(path)


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(HERE), "resourcepack/KubyUI")
    write(target)
    if len(sys.argv) > 2:
        preview(target, sys.argv[2])
    print("ok wumpus")
