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
# Survival-Juwelenshop (Release „Referenz2“, extract_jewelshop_survival.py): Präfix jss_, dazu Möbel (f_) und Pets (p_)
ASSETS_SURVIVAL = os.path.join(HERE, "assets", "jewelshop_survival")
# Spielzeit (Release „Neue-referenzen“, extract_playtime.py): Präfix pt_, dazu Balken-Verläufe (strip_)
ASSETS_PLAYTIME = os.path.join(HERE, "assets", "playtime")
# Regeln (Release „Neue-referenzen“, extract_rules.py): Präfix rg_
ASSETS_RULES = os.path.join(HERE, "assets", "rules")
# Freunde (Release „Neue-referenzen“, extract_friends.py): Präfix fr_
ASSETS_FRIENDS = os.path.join(HERE, "assets", "friends")
SETS = ((ASSETS, "js_"), (ASSETS_SURVIVAL, "jss_"), (ASSETS_PLAYTIME, "pt_"), (ASSETS_RULES, "rg_"),
        (ASSETS_FRIENDS, "fr_"))


def available():
    return os.path.isdir(ASSETS)


def _files(folder, prefix):
    if not os.path.isdir(folder):
        return []
    return sorted(f for f in os.listdir(folder) if f.startswith(prefix) and f.endswith(".png"))


def boxes():
    """Stil js_<name> bzw. jss_<name> -> 15 x 15-Vorlage."""
    out = {}
    for folder, pre in SETS:
        for f in _files(folder, "box_"):
            out[pre + f[4:-4]] = Image.open(os.path.join(folder, f)).convert("RGBA")
    return out


def sprites():
    """[(js_<name>, Bild)] Grafiken und Mengen-Knöpfe, danach jss_… mit Möbeln und Pets (Reihenfolge fest)."""
    out = []
    for folder, pre in SETS:
        for f in _files(folder, "art_"):
            out.append((pre + f[4:-4], Image.open(os.path.join(folder, f)).convert("RGBA")))
        for f in _files(folder, "pill_"):
            out.append((pre + f[:-4], Image.open(os.path.join(folder, f)).convert("RGBA")))
        for kind in ("f_", "p_"):
            for f in _files(folder, kind):
                out.append((pre + f[:-4], Image.open(os.path.join(folder, f)).convert("RGBA")))
    return out


def strips():
    """[(pt_<name>, Bild)] Balken-Füllungen mit Farbverlauf: volle Füllung, rechts daneben die Endspalte."""
    out = []
    for folder, pre in SETS:
        for f in _files(folder, "strip_"):
            out.append((pre + f[6:-4], Image.open(os.path.join(folder, f)).convert("RGBA")))
    return out
