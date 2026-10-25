#!/usr/bin/env python3
"""Bausteine des Guides (SMP-Hilfe, Referenz „Referenzen2“) aus tools/assets/guide (pixelgenau aus dem
Referenzbild geschnitten, GUI-Pixel, siehe extract_guide.py).

- Kästen als 9-Slice (Box GD_<STIL>): Kopf, Inhalt, Navigation (aktiv grün gefüllt, sonst grau)
- Grafiken in Originalgröße als Sprites (Sprite GD_<NAME>): Buch im Kopf, zwölf Icons der Navigation (GD_NAV00 …)
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets", "guide")


def available():
    return os.path.isdir(ASSETS)


def _files(prefix):
    return sorted(f for f in os.listdir(ASSETS) if f.startswith(prefix) and f.endswith(".png"))


def boxes():
    """Stil gd_<name> -> 15 x 15-Vorlage."""
    if not available():
        return {}
    return {"gd_" + f[4:-4]: Image.open(os.path.join(ASSETS, f)).convert("RGBA") for f in _files("box_")}


def sprites():
    """[(gd_<name>, Bild)] Grafiken (Reihenfolge fest: alphabetisch)."""
    if not available():
        return []
    return [("gd_" + f[4:-4], Image.open(os.path.join(ASSETS, f)).convert("RGBA")) for f in _files("art_")]
