#!/usr/bin/env python3
"""Goldene Minecraft-Knöpfe (widget/button) wie in den Referenzen „Neue-referenzen“ (Link-Abfrage „Yes / Copy to
Clipboard / No“, Knopf „Anfrage senden“ unter den Eingabefeldern der Dialoge).

Ausschneiden (einmal, aus der 4K-Aufnahme 2026-10-26_05.40.49.png, GUI-Maßstab 3; die Link-Abfrage liegt auf einem
eigenen Pixelraster, darum nicht aus den nativen Ausschnitten):
  python3 tools/build_buttons.py extract <4k.png>   ->  tools/assets/vanilla_ui/button_ref.png (100 x 20)
Bauen (aus tools/build_ui_pack.py): write(<pack>) schreibt
  assets/minecraft/textures/gui/sprites/widget/button{,_highlighted,_disabled}.png (200 x 20) mit .mcmeta (9-Slice,
  links und rechts 6 px Rand, damit die hellen Ecken der Lichtkante nicht wiederholt werden)
"""
import json
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REF = os.path.join(HERE, "assets", "vanilla_ui", "button_ref.png")
# Knopf „Yes“ in der 4K-Aufnahme: linke obere Ecke (1458, 1149), 100 x 20 GUI-Pixel zu je 3 x 3
ORIGIN, SIZE = (1458, 1149), (100, 20)
W, H, SIDE = 200, 20, 6


def extract(path):
    big = np.array(Image.open(path).convert("RGB")).astype(int)
    x0, y0 = ORIGIN
    a = np.array([[big[y0 + 3 * j + 1, x0 + 3 * i + 1] for i in range(SIZE[0])] for j in range(SIZE[1])])
    # Schrift „Yes“ entfernen: jede Zeile in der Mitte bekommt die Farbe der Spalte 20 (frei von Schrift)
    for y in range(a.shape[0]):
        for x in range(SIDE + 14, a.shape[1] - SIDE - 1):
            a[y, x] = a[y, 30]
    os.makedirs(os.path.dirname(REF), exist_ok=True)
    Image.fromarray(a.astype(np.uint8), "RGB").save(REF)
    print("ok", REF, a.shape[1], "x", a.shape[0])


def normal():
    """200 x 20 aus der Vorlage: links und rechts die echten Kanten, dazwischen die Spalte aus der Mitte."""
    ref = np.array(Image.open(REF).convert("RGB")).astype(int)
    w = ref.shape[1]
    out = np.zeros((H, W, 3), dtype=int)
    left = 30                       # bis hier die echte linke Seite (mit der helleren Lichtkante am Anfang)
    out[:, :left] = ref[:, :left]
    out[:, W - SIDE - 4:] = ref[:, w - SIDE - 4:]
    for x in range(left, W - SIDE - 4):
        out[:, x] = ref[:, 40]
    return out


def write(pack):
    if not os.path.isfile(REF):
        print("Hinweis: %s fehlt, Knöpfe bleiben wie in Minecraft" % REF)
        return
    d = os.path.join(pack, "assets/minecraft/textures/gui/sprites/widget")
    os.makedirs(d, exist_ok=True)
    base = normal()
    states = {
        "button": base,
        # Maus darüber: heller, Umriss golden statt dunkel
        "button_highlighted": np.clip(base * 1.12 + 10, 0, 255),
        # gesperrt: grau und dunkler
        "button_disabled": np.repeat((base.mean(axis=2, keepdims=True) * 0.55), 3, axis=2),
    }
    hl = states["button_highlighted"]
    dark = (np.abs(base - np.array([0x30, 0x20, 0x09])).sum(axis=2) < 6)
    hl[dark] = [0xF0, 0xD0, 0x70]
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": W, "height": H,
                                "border": {"left": SIDE, "top": 3, "right": SIDE, "bottom": 3}}}}
    for name, arr in states.items():
        Image.fromarray(arr.astype(np.uint8), "RGB").convert("RGBA").save(os.path.join(d, name + ".png"))
        with open(os.path.join(d, name + ".png.mcmeta"), "w", encoding="utf-8") as f:
            json.dump(meta, f)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "extract":
        extract(sys.argv[2])
    else:
        write(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(HERE), "resourcepack/KubyUI"))
