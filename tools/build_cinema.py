#!/usr/bin/env python3
"""Grafik für die Kino-Engine (de.kuby.kubyplugins.cinema) und das Heilige Schwert.

Erzeugt im Pack (Standard resourcepack/KubyUI):
  Schrift kuby:cine       Kino-Balken (oben: Bossbar-Titel, unten: Actionbar), Blitzbild, Farbstimmungen
  Schrift kuby:cine_card  Titelschrift klein (Titelkarte am Ende einer Szene)
  Modelle kuby:cine/...   Schwert (im Stein und in der Hand), Stein (normal und glühend), leer
  Modelle kuby:fx/...     Druckwellen-Ring, Himmelskuppel, Wolken, Bodenrisse, Strahl, Stern, Glühen, Funkeln
  Item kuby:weapons/heiliges_schwert  Waffe: im Inventar ein Pixel-Icon, in der Hand das 3D-Modell

Breiten der Glyphen müssen zu Cinema.java passen (BAR_ADV 2049, FLASH_ADV 1201, GRADE_ADV 2801).
Stil: tools/icon_db/STYLE.md (Rampen aus materials.py, Licht oben links, Glühen von innen).
Aufruf: python3 tools/build_cinema.py [pack-ordner]
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "icon_db"))
from materials import OUTLINE, RAMPS, rgb  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(HERE), "resourcepack/KubyUI")
A = os.path.join(OUT, "assets/kuby")


def ramp(name):
    return [rgb(c) for c in RAMPS[name]]


SILBER, GOLD, EISEN, GLAS, BLAU, HOSE, GRAS, ERDE, FEUER, LILA = (ramp(n) for n in (
    "silber", "gold", "eisen", "glas", "blau", "hose", "gras", "erde", "feuer", "lila"))
OUT_C = rgb(OUTLINE)


def save(img, rel):
    p = os.path.join(A, "textures", rel + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def write(rel, data):
    p = os.path.join(A, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)


def item_def(name, model, tint=False):
    m = {"type": "minecraft:model", "model": model}
    if tint:
        m["tints"] = [{"type": "minecraft:dye", "default": 16777215}]
    write("items/" + name + ".json", {"model": m})


# =====================================================================================================================
# Modelle aus Kästen: jede Fläche bekommt ihren eigenen Bereich in einem Atlas (2 Pixel je Modell-Einheit)
# =====================================================================================================================

class Atlas:
    def __init__(self, size=128, ppu=2):
        self.size, self.ppu = size, ppu
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        self.x = self.y = self.row = 0

    def alloc(self, w, h):
        if self.x + w > self.size:
            self.x, self.y, self.row = 0, self.y + self.row + 1, 0
        if self.y + h > self.size:
            raise ValueError("Atlas voll")
        x, y = self.x, self.y
        self.x += w + 1
        self.row = max(self.row, h)
        return x, y

    def uv(self, x, y, w, h):
        k = 16.0 / self.size
        return [round(x * k, 4), round(y * k, 4), round((x + w) * k, 4), round((y + h) * k, 4)]


FACE_DIMS = {  # Fläche -> (Breite-Achse, Höhe-Achse)
    "north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}


def box(atlas, frm, to, painter, faces=("north", "south", "east", "west", "up", "down"), tint=False, rot=None, shade=True):
    """Kasten von frm bis to; painter(face, w, h) -> Bild (w x h Pixel) für jede Fläche."""
    el = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": {}}
    if not shade:
        el["shade"] = False
    if rot:
        el["rotation"] = rot
    size = [to[i] - frm[i] for i in range(3)]
    for f in faces:
        a, b = FACE_DIMS[f]
        w = max(1, round(size[a] * atlas.ppu))
        h = max(1, round(size[b] * atlas.ppu))
        x, y = atlas.alloc(w, h)
        atlas.img.alpha_composite(painter(f, w, h).convert("RGBA"), (x, y))
        face = {"uv": atlas.uv(x, y, w, h), "texture": "#t"}
        if tint:
            face["tintindex"] = 0
        el["faces"][f] = face
    return el


def model(rel, tex, elements, display=None, parent=None):
    m = {"textures": {"t": tex, "particle": tex}, "elements": elements}
    if display:
        m["display"] = display
    if parent:
        m["parent"] = parent
    write("models/" + rel + ".json", m)


# =====================================================================================================================
# Heiliges Schwert
# =====================================================================================================================

def grad_v(w, h, cols, top_light=True):
    """Senkrechter Verlauf über die Rampe (oben hell)."""
    im = Image.new("RGBA", (w, h))
    for y in range(h):
        k = y / max(1, h - 1)
        c = cols[int(round((1 - k) * (len(cols) - 1))) if top_light else int(round(k * (len(cols) - 1)))]
        for x in range(w):
            im.putpixel((x, y), c + (255,))
    return im


def blade(face, w, h):
    """Klinge: Silber mit goldener Hohlkehle und leuchtenden Runen; Licht von links oben."""
    im = Image.new("RGBA", (w, h))
    if face in ("north", "south"):
        for y in range(h):
            for x in range(w):
                k = y / max(1, h - 1)  # 0 oben (Parierstange) .. 1 unten (Spitze)
                if x == 0:
                    c = SILBER[2]
                elif x == w - 1:
                    c = SILBER[1]
                elif x == 1:
                    c = SILBER[7] if face == "north" else SILBER[6]
                elif x == w // 2:
                    c = GOLD[6] if (y % 6) else GLAS[5]
                elif x == w // 2 - 1:
                    c = GOLD[4] if (y % 6) else GLAS[4]
                else:
                    c = SILBER[5 if x < w // 2 else 3]
                # zur Spitze hin heller (Glanz)
                if k > 0.8 and x not in (0, w - 1):
                    c = SILBER[min(7, SILBER.index(c) + 1)] if c in SILBER else c
                im.putpixel((x, y), c + (255,))
        # ein Glanzpunkt, bewusst asymmetrisch (nur auf der langen Klinge)
        if h > 10 and w > 2:
            im.putpixel((1, 3), (255, 255, 255, 255))
            im.putpixel((1, 4), SILBER[7] + (255,))
    elif face in ("east", "west"):
        for y in range(h):
            for x in range(w):
                im.putpixel((x, y), (SILBER[7] if face == "west" else SILBER[4]) + (255,))
    else:
        im.paste(SILBER[5] + (255,), (0, 0, w, h))
    return im


def tip(face, w, h):
    return blade(face, w, h)


def guard(face, w, h):
    """Parierstange: Gold, oben hell, unten dunkel, an den Enden Kerben."""
    im = grad_v(w, h, GOLD[2:8])
    if face in ("north", "south"):
        for x in range(w):
            im.putpixel((x, 0), GOLD[7] + (255,))
            im.putpixel((x, h - 1), GOLD[1] + (255,))
        for x in (2, w - 3):
            for y in range(1, h - 1):
                im.putpixel((x, y), GOLD[2] + (255,))
    return im


def gem(face, w, h):
    """Edelstein (glüht): Kern weiß-blau, Rand tiefblau."""
    im = Image.new("RGBA", (w, h), BLAU[3] + (255,))
    for y in range(h):
        for x in range(w):
            dx, dy = (x + 0.5) / w - 0.4, (y + 0.5) / h - 0.4
            r = math.hypot(dx, dy)
            c = GLAS[5] if r < 0.18 else BLAU[8] if r < 0.32 else BLAU[5] if r < 0.48 else BLAU[2]
            im.putpixel((x, y), c + (255,))
    return im


def grip(face, w, h):
    """Griff: tiefblaues Leder mit goldenem Draht schräg gewickelt."""
    im = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            wire = (x + y) % 4 == 0
            c = GOLD[5] if wire else (HOSE[2] if x < w / 2 else HOSE[1])
            if not wire and y % 4 == 3:
                c = HOSE[0]
            im.putpixel((x, y), c + (255,))
    return im


def pommel(face, w, h):
    im = grad_v(w, h, GOLD[1:8])
    if face in ("north", "south", "east", "west") and w > 2:
        im.putpixel((1, 1), GOLD[8] + (255,))
    return im


def sword_elements(atlas, flip=False):
    """Schwert senkrecht, Spitze unten (flip: Spitze oben). Einheiten wie Itemmodelle (0..16 = 1 Block)."""
    E = []

    def B(frm, to, painter, **kw):
        if flip:
            frm, to = [frm[0], 16 - to[1], frm[2]], [to[0], 16 - frm[1], to[2]]
        E.append(box(atlas, frm, to, painter, **kw))

    B([6.5, -3, 7.5], [9.5, 12, 8.5], blade)                 # Klinge
    B([7.0, -4.5, 7.5], [9.0, -3, 8.5], tip)                 # Spitze, Stufe 1
    B([7.5, -5.5, 7.6], [8.5, -4.5, 8.4], tip)               # Spitze, Stufe 2
    B([2.0, 12, 7.0], [14.0, 13.5, 9.0], guard)              # Parierstange
    B([2.0, 13.5, 7.25], [3.5, 14.5, 8.75], guard)           # Flügel links
    B([12.5, 13.5, 7.25], [14.0, 14.5, 8.75], guard)         # Flügel rechts
    B([7.0, 11.9, 6.4], [9.0, 13.6, 9.6], gem, shade=False)  # Stein in der Parierstange
    B([7.0, 13.5, 7.0], [9.0, 18.5, 9.0], grip)              # Griff
    B([6.5, 18.5, 6.5], [9.5, 20.5, 9.5], pommel)            # Knauf
    B([7.25, 20.5, 7.25], [8.75, 21.25, 8.75], gem, shade=False)
    return E


def build_sword():
    at = Atlas(128, 2)
    els = sword_elements(at)
    save(at.img, "cine/sword")
    model("cine/sword", "kuby:cine/sword", els)
    at2 = Atlas(128, 2)
    els2 = sword_elements(at2, flip=True)
    save(at2.img, "cine/sword_held")
    # wie ein Vanilla-Schwert in der Hand (das Modell steht senkrecht, darum z um 45 Grad weniger)
    disp = {
        "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 4.0, 0.5], "scale": [0.72, 0.72, 0.72]},
        "thirdperson_lefthand": {"rotation": [0, 90, -10], "translation": [0, 4.0, 0.5], "scale": [0.72, 0.72, 0.72]},
        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.2, 1.13], "scale": [0.58, 0.58, 0.58]},
        "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 3.2, 1.13], "scale": [0.58, 0.58, 0.58]},
        "ground": {"rotation": [0, 0, -45], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.52, 0.52, 0.52]},
        "fixed": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.8, 0.8, 0.8]},
    }
    model("cine/sword_held", "kuby:cine/sword_held", els2, display=disp)
    item_def("cine/sword", "kuby:cine/sword")
    item_def("cine/sword_held", "kuby:cine/sword_held")
    # leeres Modell (Hand der Figur beim Greifen)
    write("models/cine/empty.json", {"textures": {"particle": "minecraft:block/stone"}, "elements": []})
    item_def("cine/empty", "kuby:cine/empty")
    # Waffe: Icon im Inventar (gui), sonst das 3D-Modell
    icon = sword_icon()
    save(icon, "item/weapons/heiliges_schwert")
    write("models/item/weapons/heiliges_schwert_icon.json",
          {"parent": "minecraft:item/handheld", "textures": {"layer0": "kuby:item/weapons/heiliges_schwert"}})
    write("items/weapons/heiliges_schwert.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": ["gui", "fixed", "ground"], "model": {"type": "minecraft:model", "model": "kuby:item/weapons/heiliges_schwert_icon"}}],
        "fallback": {"type": "minecraft:model", "model": "kuby:cine/sword_held"}}})
    return icon


def sword_icon():
    """32-px-Icon: diagonales Schwert wie die Referenzen, Kontur fast schwarz, goldene Hohlkehle, blauer Stein."""
    S = 32
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = im.load()
    # Klinge von (9, 22) bis (28, 3): Diagonale, 3 px breit
    for i in range(0, 20):
        x, y = 9 + i, 22 - i
        for k, c in ((-1, SILBER[7]), (0, GOLD[5] if i % 5 else GLAS[5]), (1, SILBER[3])):
            px[x + k if k > 0 else x, y + (k if k < 0 else 0)] = c + (255,)
        px[x - 1, y] = SILBER[6] + (255,)
        px[x, y + 1] = SILBER[2] + (255,)
    # Spitze
    px[29, 2] = SILBER[6] + (255,)
    px[28, 2] = SILBER[7] + (255,)
    # Parierstange quer zur Klinge (von (4,20) nach (11,27))
    for i in range(-4, 5):
        x, y = 8 + i, 23 + i
        px[x, y] = GOLD[6 if i < 0 else 4] + (255,)
        px[x + 1, y] = GOLD[3] + (255,)
    px[8, 23] = BLAU[8] + (255,)
    px[9, 23] = GLAS[5] + (255,)
    # Griff und Knauf
    for i in range(1, 5):
        px[7 - i, 24 + i] = (HOSE[2] if i % 2 else GOLD[5]) + (255,)
        px[8 - i, 24 + i] = HOSE[1] + (255,)
    px[2, 29] = GOLD[6] + (255,)
    px[3, 29] = GOLD[4] + (255,)
    px[2, 30] = GOLD[3] + (255,)
    px[3, 30] = BLAU[6] + (255,)
    # Kontur (4er-Nachbarschaft) außen
    filled = {(x, y) for x in range(S) for y in range(S) if px[x, y][3] > 0}
    for y in range(S):
        for x in range(S):
            if (x, y) in filled:
                continue
            if any((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUT_C + (255,)
    return im


# =====================================================================================================================
# Stein
# =====================================================================================================================

def rock_painter(glow):
    rnd = random.Random(7 if not glow else 7)
    ST = EISEN[1:7]

    def paint(face, w, h):
        im = Image.new("RGBA", (w, h))
        for y in range(h):
            for x in range(w):
                n = rnd.random()
                k = 2 + (1 if n > 0.6 else 0) - (1 if n < 0.2 else 0)
                if face == "up":
                    k += 1
                elif face == "down":
                    k -= 1
                elif y < 2:
                    k += 1
                elif y > h - 3:
                    k -= 1
                c = ST[max(0, min(len(ST) - 1, k))]
                # Moos oben und an der Oberkante der Seiten
                if (face == "up" and n > 0.82) or (face not in ("up", "down") and y < 2 and n > 0.55):
                    c = GRAS[1 + int(n * 3) % 3]
                im.putpixel((x, y), c + (255,))
        # glühende Risse (Runen) auf den Seiten und oben
        if glow:
            d = ImageDraw.Draw(im)
            r2 = random.Random(w * 31 + h)
            for _ in range(max(1, (w * h) // 90)):
                x0, y0 = r2.randrange(w), r2.randrange(h)
                pts = [(x0, y0)]
                for _ in range(r2.randint(3, 7)):
                    x0 += r2.choice((-1, 0, 1))
                    y0 += r2.choice((-1, 1))
                    pts.append((x0, y0))
                d.line(pts, fill=FEUER[6] + (255,))
                for p in pts[::2]:
                    if 0 <= p[0] < w and 0 <= p[1] < h:
                        im.putpixel(p, FEUER[9] + (255,))
        return im
    return paint


def build_stone():
    for glow in (False, True):
        at = Atlas(256, 2)
        P = rock_painter(glow)
        # flacher Felsen, 1,6 Blöcke breit, 0,45 hoch (Modell-y 4.4..11.6 = mittig um 8)
        els = [
            box(at, [-4.5, 4.4, -3.5], [20.5, 9.0, 19.5], P),
            box(at, [-2.0, 9.0, -1.5], [18.0, 11.6, 17.5], P),
            box(at, [-6.0, 4.4, 3.0], [-4.5, 7.6, 12.0], P),
            box(at, [20.5, 4.4, 1.0], [22.5, 8.2, 9.0], P),
            box(at, [4.0, 4.4, 19.5], [13.0, 7.0, 21.5], P),
            box(at, [1.0, 4.4, -5.5], [9.0, 7.8, -3.5], P),
            box(at, [11.0, 9.0, -3.0], [17.0, 10.4, -1.5], P),
            box(at, [-3.5, 9.0, 12.0], [-2.0, 10.2, 17.0], P),
        ]
        name = "cine/stone_glow" if glow else "cine/stone"
        save(at.img, name)
        model(name, "kuby:" + name, els)
        item_def(name, "kuby:" + name)


# =====================================================================================================================
# Effekte
# =====================================================================================================================

def quad_flat(tex, size=16, y=8.0, tint=True):
    """Waagerechte Fläche (oben und unten sichtbar)."""
    return {"textures": {"t": tex, "particle": tex}, "elements": [{
        "from": [0, y, 0], "to": [size, y, size], "shade": False,
        "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#t", **({"tintindex": 0} if tint else {})},
                  "down": {"uv": [0, 0, 16, 16], "texture": "#t", **({"tintindex": 0} if tint else {})}}}]}


def quad_face(tex, tint=True):
    """Senkrechte Fläche zur Kamera (Billboard), beidseitig."""
    t = {"tintindex": 0} if tint else {}
    return {"textures": {"t": tex, "particle": tex}, "elements": [{
        "from": [0, 0, 8], "to": [16, 16, 8], "shade": False,
        "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#t", **t}, "south": {"uv": [16, 0, 0, 16], "texture": "#t", **t}}}]}


def radial(size, fn):
    im = Image.new("RGBA", (size, size))
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - c, y - c) / c
            im.putpixel((x, y), fn(r, x, y))
    return im


def build_fx():
    # Druckwellen-Ring: heller Rand, nach innen weicher Schleier; 4 Stufen Deckkraft
    for lv, k in ((1, 0.25), (2, 0.45), (3, 0.7), (4, 1.0)):
        def ring(r, x, y, k=k):
            if r > 1:
                return (0, 0, 0, 0)
            edge = math.exp(-((r - 0.9) / 0.06) ** 2)
            inner = 0.22 * max(0, (r - 0.45) / 0.45) if r < 0.9 else 0
            a = min(1, edge + inner) * k
            return (255, 255, 255, int(255 * a) if a >= 0.1 else 0)
        save(radial(64, ring), "fx/ring_%d" % lv)
        write("models/fx/ring_%d.json" % lv, quad_flat("kuby:fx/ring_%d" % lv))
        item_def("fx/ring_%d" % lv, "kuby:fx/ring_%d" % lv, tint=True)
    # Himmelskuppel: Würfel mit halbdurchsichtiger Farbe (innen sichtbar durch negative Größe)
    for lv, a in ((1, 0.16), (2, 0.28), (3, 0.42), (4, 0.56)):
        im = Image.new("RGBA", (16, 16), (255, 255, 255, int(255 * a)))
        save(im, "fx/dome_%d" % lv)
        faces = {f: {"uv": [0, 0, 16, 16], "texture": "#t", "tintindex": 0} for f in ("north", "south", "east", "west", "up", "down")}
        write("models/fx/dome_%d.json" % lv, {"textures": {"t": "kuby:fx/dome_%d" % lv, "particle": "kuby:fx/dome_%d" % lv},
                                              "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "faces": faces}]})
        item_def("fx/dome_%d" % lv, "kuby:fx/dome_%d" % lv, tint=True)
    # Wolken: weiche, dichte Wolkenfelder (werden dunkel eingefärbt)
    rnd = random.Random(42)
    base = Image.new("L", (128, 128), 0)
    d = ImageDraw.Draw(base)
    for _ in range(26):
        cx, cy = rnd.randint(20, 108), rnd.randint(20, 108)
        r = rnd.randint(10, 26)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    base = base.filter(ImageFilter.GaussianBlur(7))
    for lv, k in ((1, 0.3), (2, 0.55), (3, 0.8), (4, 1.0)):
        im = Image.new("RGBA", (128, 128))
        for y in range(128):
            for x in range(128):
                v = base.getpixel((x, y)) / 255
                c = (128 - 64) / 64
                edge = max(0, 1 - (math.hypot(x - 64, y - 64) / 64) ** 3)
                a = v * edge * 0.92 * k
                shade = 150 + int(70 * (1 - y / 128))
                im.putpixel((x, y), (shade, shade, shade, int(255 * a) if a >= 0.1 else 0))
        save(im, "fx/cloud_%d" % lv)
        write("models/fx/cloud_%d.json" % lv, quad_flat("kuby:fx/cloud_%d" % lv))
        item_def("fx/cloud_%d" % lv, "kuby:fx/cloud_%d" % lv, tint=True)
    # Bodenrisse: verästelte Linien, Kern hell, Rand Glut
    im = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rr = random.Random(9)
    for k in range(9):
        a = k * 2 * math.pi / 9 + rr.random() * 0.4
        x, y, w = 64.0, 64.0, 4
        while math.hypot(x - 64, y - 64) < 62 and w > 0:
            nx = x + math.cos(a) * 5
            ny = y + math.sin(a) * 5
            d.line((x, y, nx, ny), fill=(255, 120, 40, 255), width=max(1, w))
            d.line((x, y, nx, ny), fill=(255, 236, 190, 255), width=max(1, w - 2))
            if rr.random() < 0.18:
                b = a + rr.choice((-1, 1)) * (0.6 + rr.random() * 0.5)
                bx, by = x, y
                for _ in range(rr.randint(2, 5)):
                    ex, ey = bx + math.cos(b) * 4, by + math.sin(b) * 4
                    d.line((bx, by, ex, ey), fill=(255, 110, 30, 230), width=1)
                    bx, by, b = ex, ey, b + rr.uniform(-0.4, 0.4)
            x, y = nx, ny
            a += rr.uniform(-0.35, 0.35)
            if rr.random() < 0.12:
                w -= 1
    save(im, "fx/cracks")
    write("models/fx/cracks.json", quad_flat("kuby:fx/cracks", y=0.2))
    item_def("fx/cracks", "kuby:fx/cracks", tint=True)
    # Strahl: Kern (weiß) und Hülle (weich)
    beam = Image.new("RGBA", (16, 16))
    glow = Image.new("RGBA", (16, 16))
    for x in range(16):
        u = abs(x - 7.5) / 8
        beam_a = 1.0 if u < 0.55 else max(0.0, 1 - (u - 0.55) / 0.45)
        glow_a = 0.78 * max(0.0, 1 - u) ** 1.4
        for y in range(16):
            beam.putpixel((x, y), (255, 255, 255, int(255 * beam_a) if beam_a >= 0.1 else 0))
            glow.putpixel((x, y), (255, 255, 255, int(255 * glow_a) if glow_a >= 0.1 else 0))
    save(beam, "fx/beam")
    save(glow, "fx/beam_glow")
    for name in ("beam", "beam_glow"):
        faces = {f: {"uv": [0, 0, 16, 16], "texture": "#t", "tintindex": 0} for f in ("north", "south", "east", "west")}
        write("models/fx/%s.json" % name, {"textures": {"t": "kuby:fx/" + name, "particle": "kuby:fx/" + name}, "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "faces": faces},
            {"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "rotation": {"angle": 45, "axis": "y", "origin": [8, 8, 8]},
             "faces": dict(faces)}]})
        item_def("fx/" + name, "kuby:fx/" + name, tint=True)
    # Glühen und Funkeln (Billboards)
    def soft(r, x, y):
        a = max(0.0, 1 - r) ** 2.2
        return (255, 255, 255, int(255 * a) if a >= 0.1 else 0)
    save(radial(64, soft), "fx/glow")
    write("models/fx/glow.json", quad_face("kuby:fx/glow"))
    item_def("fx/glow", "kuby:fx/glow", tint=True)

    def flare(r, x, y):
        dx, dy = abs(x - 31.5) / 31.5, abs(y - 31.5) / 31.5
        a = max(0, 1 - dx) ** 2.2 * max(0, 1 - dy * 16) + max(0, 1 - dy) ** 2.2 * max(0, 1 - dx * 16)
        a += 0.9 * max(0, 1 - r * 2.6) ** 2
        a = min(1, a)
        return (255, 255, 255, int(255 * a) if a >= 0.1 else 0)
    save(radial(64, flare), "fx/flare")
    write("models/fx/flare.json", quad_face("kuby:fx/flare"))
    item_def("fx/flare", "kuby:fx/flare", tint=True)
    # Stern: stachelig wie ein RPG-Stern (Kern + 14 Strahlen), glühende Farben von innen
    st = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = y / 15
            c = (255, int(255 - 40 * k), int(235 - 120 * k))
            st.putpixel((x, y), c + (255,))
    save(st, "fx/star")
    F = lambda: {f: {"uv": [0, 0, 16, 16], "texture": "#t", "tintindex": 0} for f in ("north", "south", "east", "west", "up", "down")}  # noqa: E731
    els = [{"from": [5, 5, 5], "to": [11, 11, 11], "shade": False, "faces": F()},
           {"from": [5, 5, 5], "to": [11, 11, 11], "shade": False, "faces": F(), "rotation": {"angle": 45, "axis": "y", "origin": [8, 8, 8]}},
           {"from": [5, 5, 5], "to": [11, 11, 11], "shade": False, "faces": F(), "rotation": {"angle": 45, "axis": "x", "origin": [8, 8, 8]}}]
    # Hauptstrahlen entlang der Achsen lang, schräge kürzer; jeder in drei Stufen dünner werdend
    for axis in ("x", "y", "z"):
        for ang in (0, 45, -45):
            reach = 16 if ang == 0 else 11
            for lo, hi, half in ((0, reach * 0.45, 1.6), (reach * 0.45, reach * 0.8, 1.0), (reach * 0.8, reach, 0.45)):
                for sgn in (1, -1):
                    a0, a1 = sorted((8 + sgn * lo, 8 + sgn * hi))
                    if axis == "x":
                        frm, to = [a0, 8 - half, 8 - half], [a1, 8 + half, 8 + half]
                    elif axis == "y":
                        frm, to = [8 - half, a0, 8 - half], [8 + half, a1, 8 + half]
                    else:
                        frm, to = [8 - half, 8 - half, a0], [8 + half, 8 + half, a1]
                    e = {"from": frm, "to": to, "shade": False, "faces": F()}
                    if ang:
                        other = {"x": "y", "y": "z", "z": "x"}[axis]
                        e["rotation"] = {"angle": ang, "axis": other, "origin": [8, 8, 8]}
                    els.append(e)
    write("models/fx/star.json", {"textures": {"t": "kuby:fx/star", "particle": "kuby:fx/star"}, "elements": els})
    item_def("fx/star", "kuby:fx/star", tint=True)


# =====================================================================================================================
# Schrift kuby:cine
# =====================================================================================================================

def build_fonts():
    os.makedirs(os.path.join(A, "font"), exist_ok=True)
    bar = Image.new("RGBA", (128, 16), (0, 0, 0, 255))
    save(bar, "cine/bar")
    flash = Image.new("RGBA", (64, 16), (255, 255, 255, 255))
    save(flash, "cine/flash")
    prov = [{"type": "space", "advances": {" ": 4}}]
    for i, T in enumerate((11, 22, 33, 44)):
        # oben (Bossbar-Titel, Text bei y = 3): Glyphe von T - 256 bis T
        prov.append({"type": "bitmap", "file": "kuby:cine/bar.png", "height": 256, "ascent": 266 - T, "chars": [chr(0xE000 + i)]})
        # unten (Actionbar, Text bei y = H - 72): Glyphe von H - T nach unten
        prov.append({"type": "bitmap", "file": "kuby:cine/bar.png", "height": 256, "ascent": T - 65, "chars": [chr(0xE010 + i)]})
    prov.append({"type": "bitmap", "file": "kuby:cine/flash.png", "height": 300, "ascent": 150, "chars": [""]})
    grades = {
        # Name: (Farbe Mitte RGBA-Anteil, Farbe Rand, Deckkraft Mitte, Deckkraft Rand)
        "red": ((150, 18, 10), (40, 0, 0), 0.20, 0.62),
        "warm": ((255, 168, 72), (70, 30, 10), 0.13, 0.42),
        "space": ((14, 16, 52), (2, 2, 10), 0.16, 0.66),
        "dark": ((0, 0, 0), (0, 0, 0), 0.0, 0.55),
        "gold": ((255, 210, 90), (60, 34, 0), 0.12, 0.5),
        "white": ((255, 255, 255), (200, 210, 230), 0.22, 0.35),
        "cold": ((90, 150, 255), (5, 15, 45), 0.12, 0.55),
        "purple": ((150, 60, 230), (25, 0, 45), 0.14, 0.58),
    }
    codes = {"red": 0xE030, "warm": 0xE031, "space": 0xE032, "dark": 0xE033, "gold": 0xE034, "white": 0xE035, "cold": 0xE036,
             "purple": 0xE037}
    for name, (cm, ce, am, ae) in grades.items():
        W, H = 256, 64
        im = Image.new("RGBA", (W, H))
        for y in range(H):
            for x in range(W):
                # sichtbarer Bereich etwa Mitte (Breite je nach Bildschirm 34 bis 70 Prozent)
                u = (x + 0.5 - W / 2) / (W * 0.21)
                v = (y + 0.5 - H / 2) / (H * 0.4)
                r = min(1.6, math.hypot(u, v * 1.05))
                k = max(0, min(1, (r - 0.55) / 0.85)) ** 1.5
                a = am + (ae - am) * k
                c = tuple(int(cm[i] + (ce[i] - cm[i]) * k) for i in range(3))
                im.putpixel((x, y), c + (int(255 * a) if a >= 0.1 else 0,))
        save(im, "cine/grade_" + name)
        prov.append({"type": "bitmap", "file": "kuby:cine/grade_%s.png" % name, "height": 700, "ascent": 560, "chars": [chr(codes[name])]})
    write("font/cine.json", {"providers": prov})
    # Titelkarte: die Gold-Titelschrift klein
    with open(os.path.join(A, "font/title.json"), encoding="utf-8") as f:
        title = json.load(f)
    card = []
    for p in title["providers"]:
        if p["type"] == "space":
            card.append({"type": "space", "advances": {k: max(1, round(v * 9 / 23)) for k, v in p["advances"].items()}})
        elif p["type"] == "bitmap" and "title_gold" in p["file"]:
            q = dict(p)
            q["height"] = 9
            q["ascent"] = 7
            card.append(q)
    write("font/cine_card.json", {"providers": card})


def main():
    build_fonts()
    icon = build_sword()
    build_stone()
    build_fx()
    print("Kino-Grafik ok:", OUT)
    return icon


if __name__ == "__main__":
    main()
