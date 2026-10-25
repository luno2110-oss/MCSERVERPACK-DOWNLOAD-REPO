#!/usr/bin/env python3
"""Bausteine des Battle-Pass-Dialogs (Referenz „Referenzen2“) aus tools/assets/battlepass (pixelgenau aus den
Referenzbildern geschnitten, GUI-Pixel, siehe extract_battlepass.py).

- Kästen als 9-Slice (Box BP_<STIL>): Kopf, Reiter, Stufen-Kasten, Free-/Premium-Reihe, Felder, Stufen-Knöpfe,
  Aufgaben-Kästen, Premium-Kästen und Knöpfe; alle auch schmaler als 48 px (Felder 46, Knöpfe 21 und 30)
- Grafiken in Originalgröße als Sprites (Sprite BP_<NAME>): Schriftzug, Ticket, Reiter-Icons, Haken, Schlösser,
  Krone, Pfeil
- goldener Schlüssel als eigenes 16-px-Icon (Icon BP_KEY, im Feld 32 px)
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets", "battlepass")


def available():
    return os.path.isdir(ASSETS)


def _files(prefix):
    return sorted(f for f in os.listdir(ASSETS) if f.startswith(prefix) and f.endswith(".png"))


def boxes():
    """Stil bp_<name> -> 15 x 15-Vorlage."""
    if not available():
        return {}
    return {"bp_" + f[4:-4]: Image.open(os.path.join(ASSETS, f)).convert("RGBA") for f in _files("box_")}


def sprites():
    """[(bp_<name>, Bild)] Grafiken (Reihenfolge fest: alphabetisch)."""
    if not available():
        return []
    return [("bp_" + f[4:-4], Image.open(os.path.join(ASSETS, f)).convert("RGBA")) for f in _files("art_")]


def icons():
    """[(bp_<name>, Bild 16 x 16)] eigene Item-Icons."""
    if not available():
        return []
    return [("bp_" + f[5:-4], Image.open(os.path.join(ASSETS, f)).convert("RGBA")) for f in _files("icon_")]
