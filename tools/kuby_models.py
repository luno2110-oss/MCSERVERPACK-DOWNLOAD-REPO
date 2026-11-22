#!/usr/bin/env python3
"""Eigene Modelle des Besitzers (Kuby_Models.zip, Blockbench) als Möbel im Survival-Juwelenshop.

Die .bbmodel-Dateien liegen in tools/assets/kuby_models (Texturen eingebettet). bbmodel_import rechnet sie in
Item-Modelle um (Drehungen eingerechnet). Hier stehen Name, Reiter, Preis, Ausrichtung und Sitze; Fläche und
Sitzhöhe werden aus der Geometrie gemessen. Der Wumpus (Discord-Statue) kommt über build_wumpus.py.

Koordinaten wie in build_furniture: 16 = 1 Block, vorne = Norden (kleines z), breite Stücke nach +x.
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bbmodel_import as bi  # noqa: E402

DIR = os.path.join(HERE, "assets", "kuby_models")

# id, Name, Reiter, Preis, Drehung um y (Grad, Vielfache von 90), Verschiebung (x, y, z), Sitze [(x, z)] in Pixeln
MODELS = [
    ("kuby_holzstuhl", "Kuby-Holzstuhl", "sitzen", 100, 0, (0, 0, 0), [(8, 7)]),
    ("kuby_stuhl_blau", "Kuby-Stuhl Blau", "sitzen", 100, 0, (0, 0, 0), [(8, 7)]),
    ("kuby_stuhl_gruen", "Kuby-Stuhl Grün", "sitzen", 100, 0, (0, 0, 0), [(8, 7)]),
    ("kuby_stuhl_rot", "Kuby-Stuhl Rot", "sitzen", 100, 0, (0, 0, 0), [(8, 7)]),
    ("kuby_holzbank", "Kuby-Holzbank", "sitzen", 150, 0, (0, 0, 0), [(10, 7.5), (22, 7.5)]),
    ("kuby_bank_blau", "Kuby-Bank Blau", "sitzen", 150, 0, (0, 0, 0), [(10, 7.5), (22, 7.5)]),
    ("kuby_bank_gruen", "Kuby-Bank Grün", "sitzen", 150, 0, (0, 0, 0), [(10, 7.5), (22, 7.5)]),
    ("kuby_bank_rot", "Kuby-Bank Rot", "sitzen", 150, 0, (0, 0, 0), [(10, 7.5), (22, 7.5)]),
    ("kuby_topf_klein", "Pflanzkübel Klein", "pflanzen", 50, 0, (0, 0, 0), []),
    ("kuby_topf_mittel", "Pflanzkübel Mittel", "pflanzen", 50, 0, (0, 0, 0), []),
    ("kuby_topf_gross", "Pflanzkübel Groß", "pflanzen", 100, 0, (0, 0, 0), []),
    ("kuby_blumentopf_1", "Rosenkübel", "pflanzen", 100, 0, (0, 0, 0), []),
    ("kuby_blumentopf_2", "Blumenkübel", "pflanzen", 100, 0, (0, 0, 0), []),
    ("kuby_baumtopf_1", "Baumkübel", "pflanzen", 150, 0, (0, 0, 0), []),
    ("kuby_baumtopf_2", "Baumkübel Doppelt", "pflanzen", 150, 0, (0, 0, 0), []),
    ("kuby_baumtopf_3", "Baumkübel Dreifach", "pflanzen", 200, 0, (0, 0, 0), []),
    ("kuby_baumtopf_4", "Baumkübel Vierfach", "pflanzen", 200, 0, (0, 0, 0), []),
    ("kuby_birkentopf_2", "Birkenkübel", "pflanzen", 250, 0, (0, 0, 0), []),
    ("kuby_birkentopf_3", "Birkenkübel Groß", "pflanzen", 250, 0, (0, 0, 0), []),
    ("kuby_obstkiste", "Obstkiste", "markt", 100, 0, (0, 0, 0), []),
    ("kuby_obstkiste_schraeg", "Obstkiste Schräg", "markt", 100, 0, (0, 0, 0), []),
    ("kuby_apfelkiste", "Apfelkiste", "markt", 150, 0, (0, 0, 0), []),
]


def extra_items():
    """Zusätzliche Einträge je Reiter für den Katalog: {Reiter: [(id, Name, Preis)]}."""
    out = {}
    for mid, name, tab, price, *_ in MODELS:
        out.setdefault(tab, []).append((mid, name, price))
    return out


def _seat_height(els, x, z):
    """Oberkante der Sitzfläche über (x, z): höchste Fläche darunter, die niedriger als 14 Pixel ist."""
    top = 0.0
    for e in els:
        f, t = e["from"], e["to"]
        if e.get("rotation"):
            continue
        if f[0] - 0.01 <= x <= t[0] + 0.01 and f[2] - 0.01 <= z <= t[2] + 0.01 and t[1] < 14:
            top = max(top, t[1])
    return top


def build(bf):
    """Möbel-Modelle (build_furniture.Model) aller eigenen Modelle. bf: das Modul build_furniture."""
    models = []
    for mid, _name, _tab, _price, rot, off, seats in MODELS:
        path = os.path.join(DIR, mid + ".bbmodel")
        bb = bi.load(path)
        m = bf.Model(mid)
        keys = []
        for i, (_n, img, _uw, _uh) in enumerate(bi.textures(bb)):
            keys.append(m._t(bf.custom("km_%s_%d" % (mid, i), img)))
        # Blätter mit schrägen Mehrachsen-Drehungen werden genähert (bis gut 40 Grad Abweichung bei Laub)
        els, _err = bi.elements(bb, outer_rot_y=rot, offset=off, tex_keys=keys, max_err=1.2)
        for e in els:
            for i in range(3):
                if not (-16 <= e["from"][i] <= 32 and -16 <= e["to"][i] <= 32):
                    raise ValueError("%s: Element außerhalb -16..32: %s %s" % (mid, e["from"], e["to"]))
        m.elements = els
        # Fläche: nur was am Boden steht (Baumkronen dürfen überstehen)
        ground = [e for e in els if e["from"][1] <= 1.5] or els
        lo = np.min([e["from"] for e in ground], axis=0)
        hi = np.max([e["to"] for e in ground], axis=0)
        m.width = max(1, int(math.ceil((hi[0] - 1) / 16)))
        m.depth = max(1, int(math.ceil((hi[2] - 1) / 16)))
        if lo[0] < -1 or lo[2] < -1:
            raise ValueError("%s: steht vor dem ersten Block (%s)" % (mid, lo))
        m.seats = list(seats)
        if seats:
            m.seat_y = max(_seat_height(els, x, z) for x, z in seats)
        models.append(m)
    return models


def _outline(img):
    """1 px dunkle Kontur (warm getönt) um die Form, wie bei den Shop-Bildern."""
    from PIL import Image
    a = np.array(img)
    al = a[..., 3] > 0
    out = a.copy()
    h, w = al.shape
    for y in range(h):
        for x in range(w):
            if al[y, x]:
                continue
            if any(0 <= y + dy < h and 0 <= x + dx < w and al[y + dy, x + dx] for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0))):
                out[y, x] = (0x1A, 0x12, 0x0C, 255)
    return Image.fromarray(out)


def icons(vanilla, folder=os.path.join(HERE, "assets", "jewelshop_survival")):
    """Shop-Bilder f_<id>.png (30 x 30) im Inventar-Blickwinkel für alle eigenen Modelle schreiben."""
    from PIL import Image
    import build_furniture as bf
    for m in build(bf):
        for name, img in bf.CUSTOM.items():
            bf._TEX.setdefault("kuby:block/furniture/" + name, np.array(img.convert("RGBA")).astype(float) / 255)
        im = bf.render(m, 28, vanilla)
        c = Image.new("RGBA", (30, 30))
        c.alpha_composite(im, (1, 1))
        _outline(c).save(os.path.join(folder, "f_%s.png" % m.id))
    print("ok Shop-Bilder:", len(MODELS))


if __name__ == "__main__":
    if "--icons" in sys.argv:
        icons(os.environ.get("KUBY_VANILLA", "vanilla"))
