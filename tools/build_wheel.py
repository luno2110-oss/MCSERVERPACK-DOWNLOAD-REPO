#!/usr/bin/env python3
"""Glücksrad im Casino (v2.07): Grafik für das Rad an der Wand, im Kuby-Stil (tools/icon_db/STYLE.md, Lieblingsstil
des Besitzers: gedeckte tiefe Farben, Licht oben links, Goldrahmen, Glühen aus der Mitte).

Schreibt in das Pack (assets/kuby, flache Bilder als item/generated, im Spiel wie die Slot-Symbole um 180 Grad gedreht):
  wh_disc     Radscheibe 128 x 128: 12 Felder (Farbe je Feld, Licht nach außen heller), goldene Trennstriche, je Feld
              ein Symbol (Chips, Juwel, Schlüssel, Booster, Stern für den Hauptgewinn) und der Betrag in Pixel-Ziffern.
              Symbole und Zahlen sind so gedreht, dass das Feld aufrecht steht, wenn es oben am Zeiger ankommt.
  wh_rim      Goldring 128 x 128 (steht still, Licht oben links bleibt oben links)
  wh_hub      Nabe 32 x 32 (Gold mit Stern, steht still)
  wh_pointer  Zeiger 32 x 32 (zeigt nach unten auf das Feld)
Die Felder kommen aus der config.yml (casino.gluecksrad.felder), damit Bild und Gewinne zusammenpassen.

Aufruf: python3 tools/build_wheel.py [pack] [--sheet bild.png]
"""
import json
import math
import os
import re
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import build_casino as bc  # noqa: E402

rgb = bc.rgb
T = (0, 0, 0, 0)
OUT = rgb("140A0C")
CONFIG = os.path.join(REPO, "KubyCore-v2.0.14/KubyPlugins/src/main/resources/config.yml")

DEFAULT = ["20;chips:1000;1.000 Chips", "12;jewels:5;5 Juwelen", "16;chips:2500;2.500 Chips", "12;key:vote:1;Vote-Key",
           "10;chips:5000;5.000 Chips", "8;booster:cashback:2:30;Cashback x2", "20;chips:1000;1.000 Chips",
           "6;key:selten:1;Selten-Key", "5;chips:10000;10.000 Chips", "4;jewels:25;25 Juwelen", "3;key:episch:1;Episch-Key",
           "1;chips:100000;Hauptgewinn"]

# Feldfarben (dunkel, mitte, hell), gedeckt und tief; reihum, das Hauptgewinn-Feld schwarz-gold
SEG = [
    ("6A0A16", "9A1222", "C81E30"),   # Rot
    ("12306A", "1A4A9A", "2A64C0"),   # Blau
    ("0C5A2E", "128040", "1EA454"),   # Grün
    ("44186A", "62248E", "8234B4"),   # Violett
    ("7A3A06", "A8540C", "D2701A"),   # Orange
    ("0C4A5A", "106A7A", "18909A"),   # Türkis
]
JACKPOT = ("120E10", "221A1C", "34282A")
GOLD = [rgb(h) for h in ("6A400A", "A06A10", "D8A020", "F4C21C", "FFE870")]

# Ziffern 3 x 5 (und K, Komma, x) für die Beträge
FONT = {
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["###", "..#", "###", "#..", "###"], "3": ["###", "..#", ".##", "..#", "###"],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "###", "..#", "###"],
    "6": ["###", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "###"],
    "K": ["#.#", "##.", "#..", "##.", "#.#"], ",": ["...", "...", "...", ".#.", "#.."],
    "x": ["...", "#.#", ".#.", "#.#", "..."],
}


def fields():
    """[(Gewicht, Gewinn, Anzeige)] aus der config.yml (casino.gluecksrad.felder), sonst die Vorgabe."""
    lines = []
    try:
        with open(CONFIG, encoding="utf-8") as fh:
            text = fh.read()
        m = re.search(r"\n  gluecksrad:.*?\n    felder:\n((?:      - \"[^\n]*\"\n)+)", text, re.S)
        if m:
            lines = re.findall(r'      - "([^\n]*)"', m.group(1))
    except OSError:
        pass
    out = []
    for raw in lines or DEFAULT:
        p = raw.split(";")
        out.append((int(p[0]), p[1], p[2] if len(p) > 2 else p[1]))
    return out[:12] + [(1, "chips:1000", "")] * (12 - len(out[:12]))


def label(prize):
    kind, _, rest = prize.partition(":")
    if kind == "chips":
        n = int(rest)
        if n >= 1000:
            k = n / 1000
            return (("%d" % k) if k == int(k) else ("%.1f" % k).replace(".", ",")) + "K"
        return str(n)
    if kind in ("jewels", "coins"):
        return rest.split(":")[0]
    if kind == "booster":
        return "x" + rest.split(":")[1].split(".")[0]
    return ""


def text_img(s):
    w = sum(len(FONT[c][0]) + 1 for c in s) - 1 if s else 1
    img = Image.new("RGBA", (w + 2, 7), T)
    px = img.load()
    x = 1
    for c in s:
        g = FONT[c]
        for yy, row in enumerate(g):
            for xx, ch in enumerate(row):
                if ch == "#":
                    px[x + xx, 1 + yy] = rgb("FFF4D0")
        x += len(g[0]) + 1
    # 1-px-Kontur
    out = img.copy()
    po = out.load()
    for y in range(img.height):
        for x2 in range(img.width):
            if px[x2, y][3]:
                continue
            if any(0 <= x2 + dx < img.width and 0 <= y + dy < img.height and px[x2 + dx, y + dy][3]
                   for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                po[x2, y] = OUT
    return out


def vanilla_item(name):
    root = os.environ.get("KUBY_VANILLA", os.path.join(REPO, "vanilla"))
    p = os.path.join(root, "assets/minecraft/textures/item", name + ".png")
    if os.path.exists(p):
        return Image.open(p).convert("RGBA")
    return None


def recolor(img, ramp):
    """Grautöne/Gold eines Bilds nach Helligkeit auf eine Rampe legen (Schlüssel je Kistenart)."""
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if p[3] == 0:
                continue
            lum = (p[0] * 299 + p[1] * 587 + p[2] * 114) / 1000
            if lum < 45:
                continue
            i = min(len(ramp) - 1, int(lum / 256 * len(ramp)))
            px[x, y] = rgb(ramp[i])[:3] + (p[3],)
    return out


KEY_RAMPS = {"vote": ["1E6A2A", "2E9A3E", "54C860", "9CF0A0"], "selten": ["14306A", "2050B0", "4C86E8", "A8CCFF"],
             "episch": ["3A1060", "6A20A8", "A050E8", "D8B0FF"], "legendaer": ["6A3006", "B0600C", "F0A030", "FFE090"],
             "keyall": ["6A4A08", "A87A10", "E8B830", "FFF0A0"]}


def icon_for(prize, jackpot):
    kind, _, rest = prize.partition(":")
    if jackpot:
        return vanilla_item("nether_star") or bc.chip("gold")
    if kind == "chips":
        n = int(rest)
        name = "white" if n < 2000 else "blue" if n < 4000 else "green" if n < 8000 else "black"
        return bc.chip(name)
    if kind == "jewels":
        return vanilla_item("emerald") or bc.chip("green")
    if kind == "coins":
        return vanilla_item("gold_ingot") or bc.chip("gold")
    if kind == "key":
        key = Image.open(os.path.join(HERE, "assets/battlepass/icon_key.png")).convert("RGBA")
        return recolor(key, KEY_RAMPS.get(rest.split(":")[0], KEY_RAMPS["keyall"]))
    if kind == "booster":
        return vanilla_item("experience_bottle") or bc.chip("purple")
    if kind == "tag":
        return vanilla_item("name_tag") or bc.chip("pink")
    return vanilla_item("golden_helmet") or bc.chip("pink")


def paste_rotated(base, img, cx, cy, deg):
    """img mittig bei (cx, cy) einsetzen, um deg Grad im Uhrzeigersinn gedreht (4-fach vergrößert, nächster Nachbar)."""
    big = img.resize((img.width * 4, img.height * 4), Image.NEAREST)
    big = big.rotate(-deg, resample=Image.NEAREST, expand=True)
    small = big.resize((max(1, big.width // 4), max(1, big.height // 4)), Image.NEAREST)
    base.alpha_composite(small, (int(round(cx - small.width / 2)), int(round(cy - small.height / 2))))


def disc():
    S = 128
    c = S / 2
    p = bc.Pix(S)
    fs = fields()
    jack = max(range(12), key=lambda i: int(fs[i][1].split(":")[1]) if fs[i][1].startswith("chips:") else -1)
    r_out, r_in = 58.5, 13.0
    for i in range(12):
        dark, mid, light = JACKPOT if i == jack else SEG[i % len(SEG)]
        # Feld i: Mitte bei i*30 Grad im Uhrzeigersinn ab oben; Pix.sector zählt ab rechts im Uhrzeigersinn
        a0 = (i * 30 - 90 - 15) % 360
        a1 = (a0 + 30) % 360
        p.sector(c, c, r_in, r_out, a0, a1, rgb(mid))
        p.sector(c, c, r_in, 24, a0, a1, rgb(dark))
        p.sector(c, c, 49, r_out, a0, a1, rgb(light))
    # goldene Trennstriche
    for i in range(12):
        a = math.radians(i * 30 - 90 - 15)
        p.line(c + r_in * math.cos(a), c + r_in * math.sin(a), c + r_out * math.cos(a), c + r_out * math.sin(a), GOLD[3], 1.2)
    p.ring(c, c, r_in - 1.2, r_in, GOLD[2])
    img = p.image(outline=None)
    # Symbole und Beträge
    for i, (_w, prize, _name) in enumerate(fs):
        deg = i * 30
        a = math.radians(deg - 90)
        icon = icon_for(prize, i == jack)
        paste_rotated(img, icon, c + 37 * math.cos(a), c + 37 * math.sin(a), deg)
        lab = label(prize)
        if lab:
            paste_rotated(img, text_img(lab), c + 52 * math.cos(a), c + 52 * math.sin(a), deg)
    return img


def rim():
    """Goldring, Licht oben links: obere linke Hälfte heller, untere rechte dunkler; innen und außen Kontur."""
    S = 128
    c = S / 2
    img = Image.new("RGBA", (S, S), T)
    px = img.load()
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            d = math.hypot(dx, dy)
            if 57.5 <= d <= 63.5:
                if d < 58.5 or d > 62.5:
                    px[x, y] = OUT
                    continue
                lam = (-dx - dy) / (d * 1.4142)
                k = 4 if lam > 0.75 else 3 if lam > 0.1 else 2 if lam > -0.6 else 1
                if 60.2 < d < 61.0:
                    k = max(1, k - 1)       # Rille im Ring
                px[x, y] = GOLD[k]
    return img


def hub():
    p = bc.Pix(32)
    p.disk(16, 16, 11.5, GOLD[1])
    p.disk(16, 16, 10.5, GOLD[3])
    p.sector(16, 16, 6, 10.5, 180, 300, GOLD[4])
    p.disk(16, 16, 6.5, rgb("8E1E2E"))
    p.disk(16, 16, 5.5, rgb("B8283A"))
    star = [(16, 11.5), (17.3, 14.6), (20.5, 14.8), (18, 16.9), (18.9, 20.2), (16, 18.4), (13.1, 20.2), (14, 16.9),
            (11.5, 14.8), (14.7, 14.6)]
    p.poly(star, GOLD[4])
    p.put(15, 14, rgb("FFFFFF"))
    return p.image()


def pointer():
    """Zeiger nach unten: Goldrahmen, rotes Inneres, oben eine Niete."""
    p = bc.Pix(32)
    p.poly([(7, 3), (25, 3), (16, 29)], GOLD[2])
    p.poly([(9, 5), (23, 5), (16, 25)], rgb("B8283A"))
    p.poly([(9, 5), (16, 5), (16, 25)], rgb("D8384A"))
    p.disk(16, 7.5, 2.6, GOLD[4])
    p.put(15, 6, rgb("FFFFFF"))
    return p.image()


def write(pack):
    a = os.path.join(pack, "assets/kuby")
    itex = os.path.join(a, "textures/item/casino")
    models = os.path.join(a, "models/item/casino")
    items = os.path.join(a, "items/casino")
    for d in (itex, models, items):
        os.makedirs(d, exist_ok=True)
    parts = {"wh_disc": disc(), "wh_rim": rim(), "wh_hub": hub(), "wh_pointer": pointer()}
    for name, img in parts.items():
        img.save(os.path.join(itex, name + ".png"))
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/casino/" + name}}, fh)
        with open(os.path.join(items, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/" + name}}, fh)
    return len(parts)


def sheet(path):
    """Vorschau: Rad zusammengesetzt (Scheibe, Ring, Nabe, Zeiger) 4-fach."""
    k = 4
    out = Image.new("RGBA", (128 * k + 40, 128 * k + 80), (24, 18, 30, 255))
    d = disc().resize((128 * k, 128 * k), Image.NEAREST)
    out.alpha_composite(d, (20, 60))
    out.alpha_composite(rim().resize((128 * k, 128 * k), Image.NEAREST), (20, 60))
    h = hub().resize((32 * k, 32 * k), Image.NEAREST)
    out.alpha_composite(h, (20 + 64 * k - 16 * k, 60 + 64 * k - 16 * k))
    pt = pointer().resize((32 * k, 32 * k), Image.NEAREST)
    out.alpha_composite(pt, (20 + 64 * k - 16 * k, 0))
    out.save(path)


if __name__ == "__main__":
    args = sys.argv[1:]
    if "--sheet" in args:
        sheet(args[args.index("--sheet") + 1])
    else:
        print("ok Glücksrad:", write(args[0] if args else os.path.join(REPO, "resourcepack/KubyUI")), "Bilder")
