#!/usr/bin/env python3
"""Bausteine des Juwelenshops (SMP, Referenz „Referenzen2“) aus tools/assets/jewelshop (pixelgenau aus den
Referenzbildern geschnitten, GUI-Pixel, siehe extract_jewelshop.py).

- Kästen als 9-Slice (wie build_settings): Karten, Zeilen, Knöpfe in den Farben der Referenz (Box JS_<STIL>)
- Grafiken in Originalgröße als Sprites (beliebig groß, in 32-px-Kacheln): Schriftzug, Smaragde, Krone, Ticket,
  Kisten, Rang-Icons, Schlüssel, Pakete, Zurück-Knopf und die Mengen-Knöpfe (Sprite JS_<NAME>)
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets", "jewelshop")


def available():
    return os.path.isdir(ASSETS)


def _files(prefix):
    return sorted(f for f in os.listdir(ASSETS) if f.startswith(prefix) and f.endswith(".png"))


def boxes():
    """Stil js_<name> -> 15 x 15-Vorlage."""
    if not available():
        return {}
    return {"js_" + f[4:-4]: Image.open(os.path.join(ASSETS, f)).convert("RGBA") for f in _files("box_")}


def sprites():
    """[(js_<name>, Bild)] Grafiken und Mengen-Knöpfe (Reihenfolge fest: alphabetisch)."""
    if not available():
        return []
    out = []
    for f in _files("art_"):
        out.append(("js_" + f[4:-4], Image.open(os.path.join(ASSETS, f)).convert("RGBA")))
    for f in _files("pill_"):
        out.append(("js_" + f[:-4], Image.open(os.path.join(ASSETS, f)).convert("RGBA")))
    return out
