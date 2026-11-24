#!/usr/bin/env python3
"""Glücksräder (v2.07): Grafik für das große Rad auf der Bühne (3D) und für das Rad im Fenster (Bild über den
Bildschirm), im Kuby-Stil (tools/icon_db/STYLE.md, Lieblingsstil des Besitzers: gedeckte tiefe Farben, Licht oben
links, Goldrahmen mit Lämpchen, Glühen aus der Mitte).

Schreibt in das Pack (assets/kuby):
  item/casino/wh_disc_casino  Radscheibe 128 x 128 (flach, item/generated, im Spiel um 180 Grad gedreht): 12 Felder
                              (Farbe je Feld, nach außen heller), goldene Trennstriche, je Feld Symbol und Betrag,
                              so gedreht, dass das Feld aufrecht steht, wenn es oben an der Nadel ankommt
  item/casino/wh_back         Rückwand hinter der Scheibe (dunkel, Goldkante, Bolzen)
  item/casino/wh_hub          Nabe (Gold mit Stern und Edelstein)
  item/casino/wh_burst        Strahlenkranz hinter dem Rad (dreht sich beim Drehen langsam gegenläufig)
  item/casino/wh_peg          Stift (3D-Modell, Würfel 21,5 über der Mitte: dreht sich mit der Scheibe um die Radmitte)
  item/casino/wh_needle       Nadel (3D-Modell, Gelenk im Ursprung, hängt nach unten, rot mit Goldkante)
  font/wheel.json             Schrift kuby:wheel für die Dreh-Animation im Fenster: je Rad 36 Bilder (alle 10 Grad,
                              mit Goldring, Lauflicht und Stiften), die Nadel in 9 Stellungen, zwei feste Leerzeichen
  Dialog-Grafiken wh_ui_casino / wh_ui_smp (über build_kit, sprites())
Die Felder kommen aus der config.yml (gluecksrad.casino / gluecksrad.smp), damit Bild und Gewinne zusammenpassen.

Aufruf: python3 tools/build_wheel.py [pack] [--sheet bild.png]
"""
import json
import math
import random
import os
import re
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import build_casino as bc  # noqa: E402

rgb = bc.rgb
T = (0, 0, 0, 0)
OUT = rgb("140A0C")
CONFIG = os.path.join(REPO, "KubyCore-v2.0.14/KubyPlugins/src/main/resources/config.yml")
WHEELS = ("casino", "smp")

DEFAULT = {
    "casino": ["18;chips:10000;10.000 Chips", "10;jewels:50;50 Juwelen", "14;chips:25000;25.000 Chips",
               "10;key:selten:3;3 Selten-Keys", "9;chips:50000;50.000 Chips", "8;booster:cashback:3:60;Cashback x3",
               "18;chips:10000;10.000 Chips", "6;key:episch:2;2 Episch-Keys", "5;chips:100000;100.000 Chips",
               "2;cosmetic:c_goldzylinder:150000;Goldener Zylinder", "3;key:legendaer:1;Legendär-Key",
               "1;chips:1000000;Hauptgewinn"],
    "smp": ["18;coins:5000;5.000 $", "12;xp:1000;1.000 XP", "14;coins:15000;15.000 $", "10;key:vote:3;3 Vote-Keys",
            "10;jewels:25;25 Juwelen", "8;booster:xp:2:60;XP x2", "18;coins:5000;5.000 $", "6;key:selten:2;2 Selten-Keys",
            "5;coins:50000;50.000 $", "5;booster:sell:2:120;Verkauf x2", "3;key:episch:2;2 Episch-Keys",
            "1;coins:500000;Hauptgewinn"],
}

# Feldfarben (dunkel, mitte, hell), gedeckt und tief; reihum, das Hauptgewinn-Feld schwarz-gold
# (die mittleren Töne stehen auch in WheelScreen.SEG für die Farbfelder im Fenster)
SEG = [
    ("6A0A16", "9A1222", "C81E30"),   # Rot
    ("12306A", "1A4A9A", "2A64C0"),   # Blau
    ("0C5A2E", "128040", "1EA454"),   # Grün
    ("44186A", "62248E", "8234B4"),   # Violett
    ("7A3A06", "A8540C", "D2701A"),   # Orange
    ("0C4A5A", "106A7A", "18909A"),   # Türkis
]
JACKPOT = ("120E10", "221A1C", "34282A")
# Goldener Dreh (v2.07, „das Glücksrad soll gold werden mit goldenen Glitzer-Effekten“): Felder in drei Goldtönen
# reihum (Königsgold, Champagner, Bernstein), Trennstriche weißgolden; das Hauptgewinn-Feld bleibt schwarz-gold
GOLD_SEG = [
    ("6A400A", "A87010", "E0A828"),   # Königsgold
    ("7A5A22", "B8903C", "EACB6E"),   # Champagner
    ("4E2406", "84420C", "B46414"),   # Bernstein
]
WHITE_GOLD = rgb("FFF6C8")
GOLD = [rgb(h) for h in ("6A400A", "A06A10", "D8A020", "F4C21C", "FFE870")]
GEM = {"casino": ("8E1E2E", "B8283A", "E85A6A"), "smp": ("0C5A2E", "1EA454", "7FE08A")}

# Ziffern 3 x 5 (und K, M, Komma, x) für die Beträge
FONT = {
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["###", "..#", "###", "#..", "###"], "3": ["###", "..#", ".##", "..#", "###"],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "###", "..#", "###"],
    "6": ["###", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "###"],
    "K": ["#.#", "##.", "#..", "##.", "#.#"], "M": ["#...#", "##.##", "#.#.#", "#...#", "#...#"],
    ",": ["...", "...", "...", ".#.", "#.."], "x": ["...", "#.#", ".#.", "#.#", "..."],
}

# Maße im 128er-Bild: Scheibe bis 58,5, Stifte bei 54,5, Ring 57,5 bis 63,5; Symbole und Beträge
R_IN, R_DISC, R_PEG, R_ICON, R_LABEL = 13.0, 58.5, 54.5, 33.0, 46.5
# Größe eines Schrift-Pixels der Beträge (128er-Einheiten): „100K“ ist dann 19,5 breit, das Feld dort gut 23
LABEL_U = 1.3
# Auflösung: Scheibe auf der Bühne, Bilder der Dreh-Animation im Fenster
DISC_3D, FRAME = 256, 192


# =====================================================================================================================
# Felder aus der config.yml
# =====================================================================================================================

def fields(wheel="casino"):
    """[(Gewicht, Gewinn, Anzeige)] aus der config.yml (gluecksrad.<rad>.felder), sonst die Vorgabe."""
    lines = []
    try:
        with open(CONFIG, encoding="utf-8") as fh:
            text = fh.read()
        top = re.search(r"\ngluecksrad:\n((?:(?:  [^\n]*|#[^\n]*|)\n)+)", text)
        if top:
            sec = re.search(r"(?:^|\n)  " + wheel + r":\n(?:    [^\n]*\n|\s*#[^\n]*\n)*?    felder:\n((?:      - \"[^\n]*\"[^\n]*\n)+)",
                            top.group(1))
            if sec:
                lines = re.findall(r'      - "([^\n"]*)"', sec.group(1))
    except OSError:
        pass
    out = []
    for raw in lines or DEFAULT[wheel]:
        p = raw.split(";")
        out.append((int(p[0]), p[1], p[2] if len(p) > 2 else p[1]))
    return out[:12] + [(0, "chips:0", "Niete")] * (12 - len(out[:12]))


def jackpot(fs):
    """Wie Wheel.jackpot: kleinstes Gewicht, bei Gleichstand das letzte."""
    best = 0
    for i in range(1, len(fs)):
        if fs[i][0] <= fs[best][0]:
            best = i
    return best


def label(prize):
    kind, _, rest = prize.partition(":")
    if kind in ("chips", "coins", "xp"):
        n = int(rest.split(":")[0])
        if n >= 1_000_000:
            k = n / 1_000_000
            return (("%d" % k) if k == int(k) else ("%.1f" % k).replace(".", ",")) + "M"
        if n >= 1000:
            k = n / 1000
            return (("%d" % k) if k == int(k) else ("%.1f" % k).replace(".", ",")) + "K"
        return str(n)
    if kind == "jewels":
        return rest.split(":")[0]
    if kind == "key":
        p = rest.split(":")
        return "x" + p[1] if len(p) > 1 and p[1] != "1" else ""
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


def icon_for(prize, jack):
    kind, _, rest = prize.partition(":")
    if jack:
        return vanilla_item("nether_star") or bc.chip("gold")
    if kind == "chips":
        n = int(rest)
        name = "white" if n < 20000 else "blue" if n < 40000 else "green" if n < 80000 else "black"
        return bc.chip(name)
    if kind == "jewels":
        return vanilla_item("emerald") or bc.chip("green")
    if kind == "coins":
        return vanilla_item("gold_ingot") or bc.chip("gold")
    if kind == "xp":
        return vanilla_item("experience_bottle") or bc.chip("green")
    if kind == "key":
        key = Image.open(os.path.join(HERE, "assets/battlepass/icon_key.png")).convert("RGBA")
        return recolor(key, KEY_RAMPS.get(rest.split(":")[0], KEY_RAMPS["keyall"]))
    if kind == "booster":
        b = rest.split(":")[0]
        return vanilla_item({"xp": "experience_bottle", "sell": "gold_nugget"}.get(b, "glowstone_dust")) or bc.chip("purple")
    if kind == "tag":
        return vanilla_item("name_tag") or bc.chip("pink")
    return vanilla_item("golden_helmet") or bc.chip("pink")


def paste_rotated(base, img, cx, cy, deg, scale=1.0):
    """img mittig bei (cx, cy) einsetzen, um deg Grad im Uhrzeigersinn gedreht und scale-fach vergrößert (über eine
    4-fache Zwischenstufe gedreht, nächster Nachbar: Pixel bleiben scharf)."""
    big = img.resize((max(1, round(img.width * scale * 4)), max(1, round(img.height * scale * 4))), Image.NEAREST)
    big = big.rotate(-deg, resample=Image.NEAREST, expand=True)
    small = big.resize((max(1, big.width // 4), max(1, big.height // 4)), Image.NEAREST)
    base.alpha_composite(small, (int(round(cx - small.width / 2)), int(round(cy - small.height / 2))))


def draw_label(img, text, cx, cy, deg, u):
    """Betrag als Pixel-Schrift, die Schrift-Pixel als gedrehte Quadrate gezeichnet (nicht als gedrehtes Bild): auch
    schräg scharf und lesbar (Wunsch des Besitzers: „man erkennt die Zahlen gar nicht richtig“). u = Größe eines
    Schrift-Pixels in Bild-Pixeln, deg = Drehung im Uhrzeigersinn, dunkle Kontur rundum."""
    cells = []
    x = 0
    for ch in text:
        g = FONT[ch]
        for gy, row in enumerate(g):
            for gx, c in enumerate(row):
                if c == "#":
                    cells.append((x + gx, gy))
        x += len(g[0]) + 1
    w, h = x - 1, 5
    th = math.radians(deg)
    co, si = math.cos(th), math.sin(th)
    d = ImageDraw.Draw(img)

    def quad(px, py, e):
        pts = []
        for qx, qy in ((px - e, py - e), (px + 1 + e, py - e), (px + 1 + e, py + 1 + e), (px - e, py + 1 + e)):
            lx, ly = (qx - w / 2) * u, (qy - h / 2) * u
            pts.append((cx + lx * co - ly * si, cy + lx * si + ly * co))
        return pts

    for px, py in cells:
        d.polygon(quad(px, py, 0.55), fill=OUT)
    for px, py in cells:
        d.polygon(quad(px, py, 0.0), fill=rgb("FFF4D0"))


# =====================================================================================================================
# Scheibe (numpy): Feld je Pixel nach Winkel, Ringe nach Abstand. Alle Maße in Einheiten des 128er-Bilds, gezeichnet in
# beliebiger Größe (Bühne 256 px, Bildschirm-Dreh 192 px, Fenster 128 px)
# =====================================================================================================================

S = 128
C = S / 2
_GRID = {}


def grid(size):
    """Pixelmitten in 128er-Einheiten: (DX, DY, DIST, PHI im Uhrzeigersinn ab oben, LAM Licht oben links)."""
    if size not in _GRID:
        yy, xx = np.mgrid[0:size, 0:size]
        k = S / size
        dx, dy = (xx + 0.5) * k - C, (yy + 0.5) * k - C
        dist = np.hypot(dx, dy)
        phi = np.degrees(np.arctan2(dx, -dy)) % 360
        lam = (-dx - dy) / (np.maximum(dist, 1e-6) * math.sqrt(2))
        _GRID[size] = (dx, dy, dist, phi, lam)
    return _GRID[size]


DX, DY, DIST, PHI, LAM = grid(S)


def _arr(col):
    return np.array(col[:3], dtype=np.float64)


def disc(wheel="casino", rot=0.0, ui=False, size=S, labels=True, gold=False):
    """Scheibe um rot Grad gegen den Uhrzeigersinn gedreht. ui: mit Stiften und festem Licht oben links.
    gold: Felder in Goldtönen (Goldener Dreh)."""
    fs = fields(wheel)
    jack = jackpot(fs)
    _dx, _dy, dist, phi, lam = grid(size)
    f = size / S
    img = np.zeros((size, size, 4), dtype=np.float64)
    field = np.round((phi + rot) / 30.0).astype(int) % 12
    inside = (dist >= R_IN) & (dist <= R_DISC)
    for i in range(12):
        dark, mid, light = JACKPOT if i == jack else (GOLD_SEG[i % len(GOLD_SEG)] if gold else SEG[i % len(SEG)])
        m = inside & (field == i)
        img[m & (dist < 24), :3] = _arr(rgb(dark))
        img[m & (dist >= 24) & (dist < 49), :3] = _arr(rgb(mid))
        img[m & (dist >= 49), :3] = _arr(rgb(light))
        img[m, 3] = 255
    # Hauptgewinn-Feld: feine Goldkante innen am Rand
    jm = inside & (field == jack) & (dist >= 55.5) & (dist < 56.5)
    img[jm, :3] = _arr(GOLD[2])
    # Kante außen dunkel
    edge = (dist > R_DISC - 1) & (dist <= R_DISC)
    img[edge, :3] = img[edge, :3] * 0.55
    # goldene Trennstriche auf den Feldgrenzen (15 + 30 k)
    delta = ((phi + rot - 15) % 30)
    delta = np.minimum(delta, 30 - delta)
    sep = inside & (dist * np.sin(np.radians(delta)) <= max(0.62, 0.55 / f))
    img[sep, :3] = _arr(GOLD[4] if gold else GOLD[3])
    img[sep & (dist > 40), :3] = _arr(WHITE_GOLD if gold else GOLD[4])
    # Goldring innen
    ring = (dist >= R_IN - 1.4) & (dist < R_IN)
    img[ring, :3] = _arr(GOLD[2])
    img[ring, 3] = 255
    if ui:
        # festes Licht oben links (die Scheibe im Fenster dreht sich, das Licht bleibt)
        k = np.clip(lam, -1, 1)[..., None]
        on = img[..., 3:] > 0
        lit = np.where(k > 0, img[..., :3] + (255 - img[..., :3]) * 0.10 * k, img[..., :3] * (1 + 0.16 * k))
        img[..., :3] = np.where(on, lit, img[..., :3])
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")
    c = size / 2
    # Symbole und Beträge, aufrecht wenn das Feld oben ist
    for i, (_w, prize, _name) in enumerate(fs):
        deg = i * 30 - rot
        a = math.radians(deg - 90)
        paste_rotated(out, icon_for(prize, i == jack), c + R_ICON * f * math.cos(a), c + R_ICON * f * math.sin(a), deg, f)
        lab = label(prize) if labels else ""
        if lab:
            draw_label(out, lab, c + R_LABEL * f * math.cos(a), c + R_LABEL * f * math.sin(a), deg, LABEL_U * f)
    if ui:
        # Stifte auf den Feldgrenzen: Goldknopf mit Glanz oben links
        p = bc.Pix(size)
        for kk in range(12):
            a = math.radians(15 + kk * 30 - rot - 90)
            x, y = c + R_PEG * f * math.cos(a), c + R_PEG * f * math.sin(a)
            p.disk(x, y, 2.3 * f, GOLD[2])
            p.disk(x - 0.5 * f, y - 0.5 * f, 1.4 * f, GOLD[3])
            p.put(int(x - f), int(y - f), GOLD[4])
        out.alpha_composite(p.image())
    return out


def rim(lit_phase=None, size=S):
    """Goldring (steht still, Licht oben links), dazu 24 Lämpchen; lit_phase: jedes dritte ab dieser Stelle leuchtet."""
    _dx, _dy, dist, _phi, lam = grid(size)
    f = size / S
    img = np.zeros((size, size, 4), dtype=np.uint8)
    band = (dist >= 57.5) & (dist <= 63.5)
    k = np.where(lam > 0.75, 4, np.where(lam > 0.1, 3, np.where(lam > -0.6, 2, 1)))
    k = np.where((dist > 60.2) & (dist < 61.0), np.maximum(1, k - 1), k)
    gold = np.array([GOLD[i] for i in range(5)], dtype=np.uint8)[:, :3]
    img[band, :3] = gold[k[band]]
    img[band, 3] = 255
    o = band & ((dist < 57.5 + 1 / f) | (dist > 63.5 - 1 / f))
    img[o, :3] = np.array(OUT[:3], dtype=np.uint8)
    out = Image.fromarray(img, "RGBA")
    if lit_phase is not None:
        c = size / 2
        p = bc.Pix(size)
        for j in range(24):
            a = math.radians(j * 15 + 7.5 - 90)
            x, y = c + 60.6 * f * math.cos(a), c + 60.6 * f * math.sin(a)
            on = (j + lit_phase) % 3 == 0
            p.disk(x, y, 1.6 * f, rgb("FFF4C0") if on else rgb("7A4A10"))
            if on:
                p.put(int(x - 0.5 * f), int(y - 0.5 * f), rgb("FFFFFF"))
        out.alpha_composite(p.image(outline=None))
    return out


def hub(wheel="casino", size=32):
    """Nabe: Goldring, Edelstein, Stern (32 px = Maß 1; größer für schärfere Bilder)."""
    gem = GEM.get(wheel, GEM["casino"])
    f = size / 32
    p = bc.Pix(size)
    c = 16 * f
    p.disk(c, c, 11.5 * f, GOLD[1])
    p.disk(c, c, 10.5 * f, GOLD[3])
    p.sector(c, c, 6 * f, 10.5 * f, 180, 300, GOLD[4])
    p.disk(c, c, 6.5 * f, rgb(gem[0]))
    p.disk(c, c, 5.5 * f, rgb(gem[1]))
    star = [(16, 11.5), (17.3, 14.6), (20.5, 14.8), (18, 16.9), (18.9, 20.2), (16, 18.4), (13.1, 20.2), (14, 16.9),
            (11.5, 14.8), (14.7, 14.6)]
    p.poly([(x * f, y * f) for x, y in star], GOLD[4])
    p.put(int(15 * f), int(14 * f), rgb("FFFFFF"))
    p.put(int(14 * f), int(13 * f), rgb(gem[2]))
    return p.image()


def back():
    """Rückwand: dunkles Pflaume, Goldkante, 24 Bolzen (steht hinter der Scheibe, außen ein schmaler Rand sichtbar)."""
    img = np.zeros((S, S, 4), dtype=np.uint8)
    on = DIST <= 63.5
    base = np.where(LAM[..., None] > 0.3, np.array([0x3A, 0x1A, 0x2A]), np.array([0x2A, 0x12, 0x20]))
    img[on, :3] = base[on]
    img[on, 3] = 255
    edge = on & (DIST > 62.0)
    img[edge, :3] = np.array(GOLD[1][:3], dtype=np.uint8)
    img[on & (DIST > 63.0), :3] = np.array(OUT[:3], dtype=np.uint8)
    out = Image.fromarray(img, "RGBA")
    p = bc.Pix(S)
    for j in range(24):
        a = math.radians(j * 15 - 90)
        p.disk(C + 61 * math.cos(a), C + 61 * math.sin(a), 0.9, GOLD[3])
    out.alpha_composite(p.image(outline=None))
    return out


def burst():
    """Strahlenkranz hinter dem Rad (Tiefe wie bei den Generator-Bildern: hinten dunkel, zur Mitte heller und wärmer),
    24 Strahlen im Wechsel, außen ein Goldring mit Punkten."""
    img = np.zeros((S, S, 4), dtype=np.float64)
    on = DIST <= 63.5
    ray = (np.floor(PHI / 15.0).astype(int) % 2) == 0
    a = np.array([0x4A, 0x22, 0x3A], dtype=np.float64)
    b = np.array([0x2A, 0x14, 0x26], dtype=np.float64)
    col = np.where(ray[..., None], a, b)
    glow = np.clip(1 - DIST / 63.5, 0, 1)[..., None] ** 1.6
    warm = np.array([0xC8, 0x7A, 0x2A], dtype=np.float64)
    col = col + (warm - col) * glow * 0.55
    img[on, :3] = col[on]
    img[on, 3] = 255
    edge = on & (DIST > 60.5)
    img[edge, :3] = np.array(GOLD[1][:3])
    img[on & (DIST > 61.5) & (DIST <= 62.5), :3] = np.array(GOLD[2][:3])
    img[on & (DIST > 62.5), :3] = np.array(OUT[:3])
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")
    p = bc.Pix(S)
    for j in range(32):
        an = math.radians(j * 11.25 - 90)
        p.disk(C + 58.2 * math.cos(an), C + 58.2 * math.sin(an), 1.1, GOLD[3] if j % 2 else GOLD[4])
    out.alpha_composite(p.image(outline=None))
    return out


def burst_gold():
    """Strahlenkranz für den Goldenen Dreh: Strahlen in Gold und Bernstein, zur Mitte fast weiß (Glühen aus dem Inneren)."""
    img = np.zeros((S, S, 4), dtype=np.float64)
    on = DIST <= 63.5
    ray = (np.floor(PHI / 15.0).astype(int) % 2) == 0
    a = np.array([0xB0, 0x78, 0x14], dtype=np.float64)
    b = np.array([0x6A, 0x3A, 0x08], dtype=np.float64)
    col = np.where(ray[..., None], a, b)
    glow = np.clip(1 - DIST / 63.5, 0, 1)[..., None] ** 1.4
    hot = np.array([0xFF, 0xF0, 0xB0], dtype=np.float64)
    col = col + (hot - col) * glow * 0.7
    img[on, :3] = col[on]
    img[on, 3] = 255
    img[on & (DIST > 60.5), :3] = np.array(GOLD[2][:3])
    img[on & (DIST > 61.5) & (DIST <= 62.5), :3] = np.array(GOLD[4][:3])
    img[on & (DIST > 62.5), :3] = np.array(OUT[:3])
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")
    p = bc.Pix(S)
    for j in range(32):
        an = math.radians(j * 11.25 - 90)
        p.disk(C + 58.2 * math.cos(an), C + 58.2 * math.sin(an), 1.1, WHITE_GOLD if j % 2 else GOLD[4])
    out.alpha_composite(p.image(outline=None))
    return out


def sparkles(img, i, size):
    """Glitzer für den Goldenen Dreh: kleine Vierstrahl-Sterne auf Scheibe und Ring, je Bild an anderen Stellen
    (deterministisch), damit es beim Drehen funkelt. Mitte weiß, Strahlen hellgold, eine Stufe Schein."""
    rnd = random.Random(7919 * (i + 1))
    f = size / S
    px = img.load()
    for _k in range(9):
        r = rnd.uniform(18, 62) * f
        an = rnd.uniform(0, 2 * math.pi)
        x, y = int(size / 2 + r * math.cos(an)), int(size / 2 + r * math.sin(an))
        arm = 1 + int(rnd.random() < 0.45) + (1 if f > 1.2 else 0)
        pts = [(0, 0, (255, 255, 250, 255))]
        for d in range(1, arm + 1):
            c = (255, 240, 170, 255) if d < arm else (255, 214, 90, 210)
            pts += [(d, 0, c), (-d, 0, c), (0, d, c), (0, -d, c)]
        for dx, dy, c in pts:
            if 0 <= x + dx < size and 0 <= y + dy < size and px[x + dx, y + dy][3] > 0:
                px[x + dx, y + dy] = c
    return img


# =====================================================================================================================
# Nadel (Fenster): Zelle 32 x 96, Gelenk bei (16, 9); im Rad-Bild sitzt das Gelenk 7 px über dem oberen Rand
# =====================================================================================================================

NEEDLE_W, NEEDLE_H, HINGE = 32, 96, (16, 9)
NEEDLE_STATES = [-10 + 5 * k for k in range(9)]


def needle_img(deg):
    th = math.radians(deg)
    hx, hy = HINGE

    def rot(pts, sx=1.0):
        out = []
        for dx, dy in pts:
            dx *= sx
            out.append((hx + dx * math.cos(th) - dy * math.sin(th), hy + dx * math.sin(th) + dy * math.cos(th)))
        return out

    body = [(-4.0, 0.0), (4.0, 0.0), (2.8, 12.0), (0.0, 19.5), (-2.8, 12.0)]
    inner = [(-2.6, 1.0), (2.6, 1.0), (1.6, 11.5), (0.0, 16.5), (-1.6, 11.5)]
    left = [(-2.6, 1.0), (0.0, 1.0), (0.0, 16.5), (-1.6, 11.5)]
    p = bc.Pix(NEEDLE_W, NEEDLE_H)
    p.poly(rot(body), GOLD[2])
    p.poly(rot(inner), rgb("B8283A"))
    p.poly(rot(left), rgb("D8384A"))
    p.disk(hx, hy, 4.2, GOLD[2])
    p.disk(hx - 0.4, hy - 0.4, 3.2, GOLD[3])
    p.disk(hx, hy, 1.4, GOLD[1])
    p.put(hx - 2, hy - 2, GOLD[4])
    img = p.image()
    img.putpixel((NEEDLE_W - 1, 0), (0, 0, 0, 1))   # feste Breite der Schrift-Zelle (Minecraft misst bis zum letzten Pixel)
    return img


def frame(wheel, i, size=FRAME, labels=True, gold=False):
    """Bild i der Dreh-Animation: Scheibe um i * 10 Grad gedreht, Ring mit Lauflicht, Nabe (size px, Maße wie 128).
    gold: goldene Felder und Glitzer (Goldener Dreh)."""
    img = Image.new("RGBA", (size, size), T)
    img.alpha_composite(disc(wheel, i * 10.0, ui=True, size=size, labels=labels, gold=gold))
    img.alpha_composite(rim(lit_phase=i, size=size))
    hs = 32 * size // S
    img.alpha_composite(hub(wheel, hs), ((size - hs) // 2, (size - hs) // 2))
    if gold:
        sparkles(img, i, size)
    img.putpixel((size - 1, 0), (0, 0, 0, 1))
    return img


def ui_sprite(wheel):
    """Dialog-Grafik: Rad in Ruhe (Feld 0 oben), Nadel darüber; 128 x 144."""
    out = Image.new("RGBA", (S, S + 16), T)
    f = frame(wheel, 0, S, labels=False)   # 128 px: Beträge zu klein zum Lesen, die Liste daneben nennt sie
    f.putpixel((S - 1, 0), T)
    out.alpha_composite(f, (0, 16))
    n = needle_img(0)
    n.putpixel((NEEDLE_W - 1, 0), T)
    out.alpha_composite(n, (64 - HINGE[0], 16 - 7 - HINGE[1]))
    return out


def sprites():
    return [("wh_ui_casino", ui_sprite("casino")), ("wh_ui_smp", ui_sprite("smp"))]


# =====================================================================================================================
# 3D-Modelle (Stift, Nadel)
# =====================================================================================================================

def _box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down")):
    return {"from": frm, "to": to, "faces": {f: {"texture": tex, "uv": [0, 0, 16, 16]} for f in faces}}


def peg_model():
    """Stift: Goldstift 21,5 über dem Ursprung, ragt nach vorn (Modell -z zeigt im Spiel zu den Zuschauern)."""
    return {"textures": {"g": "kuby:block/casino/hd_gold", "l": "kuby:block/casino/hd_gold_lit",
                         "particle": "kuby:block/casino/hd_gold"},
            "elements": [_box([7.15, 28.5, 4.0], [8.85, 30.5, 9.0], "#g"),
                         _box([6.8, 28.1, 3.3], [9.2, 30.9, 4.0], "#l")]}


def hub_model():
    """Nabe in 3D (Wunsch des Besitzers: „der Stern in der Mitte ist nur eine 2D-Textur“): achteckiger Goldkörper (zwei
    Quader, einer um 45 Grad gedreht: acht Spitzen), davor die Kappe mit Stern und Edelstein (Textur wh_hub), in der
    Sternmitte ein kleiner Goldnagel. Vorderseite = Modell -z (im Spiel zu den Zuschauern), Mitte im Ursprung (8, 8, 8)."""
    rot = {"angle": 45, "axis": "z", "origin": [8, 8, 8]}
    body = _box([3.0, 3.0, 6.4], [13.0, 13.0, 10.4], "#g")
    body2 = dict(_box([3.0, 3.0, 6.5], [13.0, 13.0, 10.3], "#g"), rotation=rot)
    rim = _box([2.6, 2.6, 6.0], [13.4, 13.4, 6.4], "#l")
    rim2 = dict(_box([2.6, 2.6, 6.05], [13.4, 13.4, 6.35], "#l"), rotation=rot)
    cap = _box([3.6, 3.6, 5.2], [12.4, 12.4, 6.0], "#g")
    cap["faces"]["north"] = {"texture": "#hub", "uv": [16, 0, 0, 16]}
    knob = dict(_box([7.45, 7.45, 4.7], [8.55, 8.55, 5.2], "#l"), rotation=rot)   # Goldnagel in der Sternmitte
    return {"textures": {"g": "kuby:block/casino/hd_gold", "l": "kuby:block/casino/hd_gold_lit",
                         "r": "kuby:block/casino/hd_velvet", "hub": "kuby:item/casino/wh_hub",
                         "particle": "kuby:block/casino/hd_gold"},
            "elements": [body, body2, rim, rim2, cap, knob]}


def needle_model():
    """Nadel: Gelenk im Ursprung (8, 8, 8), hängt nach unten (Spitze 12 Einheiten tiefer), rot mit Goldkante."""
    return {"textures": {"g": "kuby:block/casino/hd_gold", "l": "kuby:block/casino/hd_gold_lit",
                         "r": "kuby:block/casino/hd_velvet", "particle": "kuby:block/casino/hd_velvet"},
            "elements": [
                _box([6.6, -1.0, 6.6], [9.4, 7.0, 9.4], "#r"),
                _box([7.1, -3.0, 6.8], [8.9, -1.0, 9.2], "#r"),
                _box([7.6, -4.2, 7.0], [8.4, -3.0, 9.0], "#l"),
                _box([7.7, -0.5, 6.3], [8.3, 6.0, 6.6], "#l"),
                _box([6.4, -1.5, 7.4], [9.6, -0.9, 8.6], "#g"),
                _box([6.2, 6.2, 6.8], [9.8, 9.8, 9.8], "#g"),
                _box([6.8, 6.8, 5.8], [9.2, 9.2, 6.8], "#l"),
            ]}


# =====================================================================================================================
# Schreiben
# =====================================================================================================================

OLD = ("wh_disc", "wh_rim", "wh_pointer")


def write(pack):
    a = os.path.join(pack, "assets/kuby")
    itex = os.path.join(a, "textures/item/casino")
    models = os.path.join(a, "models/item/casino")
    items = os.path.join(a, "items/casino")
    ftex = os.path.join(a, "textures/font")
    for d in (itex, models, items, ftex, os.path.join(a, "font")):
        os.makedirs(d, exist_ok=True)
    for name in OLD:   # alte Wand-Fassung
        for p in (os.path.join(itex, name + ".png"), os.path.join(models, name + ".json"), os.path.join(items, name + ".json")):
            if os.path.isfile(p):
                os.remove(p)

    def item(name, model):
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump(model, fh)
        with open(os.path.join(items, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/" + name}}, fh)

    flat = {"wh_disc_casino": disc("casino", size=DISC_3D), "wh_back": back(), "wh_hub": hub("casino", 64),
            "wh_burst": burst(), "wh_disc_casino_gold": disc("casino", size=DISC_3D, gold=True), "wh_burst_gold": burst_gold()}
    for name, img in flat.items():
        img.save(os.path.join(itex, name + ".png"))
        item(name, {"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/casino/" + name}})
    item("wh_peg", peg_model())
    item("wh_hub3d", hub_model())
    item("wh_needle", needle_model())
    # Schrift kuby:wheel
    prov = [{"type": "space", "advances": {"": -26, "": 15}}]
    # Bilder der Dreh-Animation: Casino, SMP und je eine goldene Fassung (Goldener Dreh, v2.07)
    for wheel, base, gold in (("casino", 0xE000, False), ("smp", 0xE040, False), ("casino", 0xE100, True), ("smp", 0xE140, True)):
        sheet = Image.new("RGBA", (FRAME * 6, FRAME * 6), T)
        for i in range(36):
            sheet.alpha_composite(frame(wheel, i, gold=gold), ((i % 6) * FRAME, (i // 6) * FRAME))
        name = "wheel_%s%s" % (wheel, "_gold" if gold else "")
        sheet.save(os.path.join(ftex, name + ".png"))
        rows = ["".join(chr(base + r * 6 + c) for c in range(6)) for r in range(6)]
        prov.append({"type": "bitmap", "file": "kuby:font/%s.png" % name, "height": 40, "ascent": 21, "chars": rows})
    ns = Image.new("RGBA", (NEEDLE_W * len(NEEDLE_STATES), NEEDLE_H), T)
    for k, deg in enumerate(NEEDLE_STATES):
        ns.alpha_composite(needle_img(deg), (k * NEEDLE_W, 0))
    ns.save(os.path.join(ftex, "wheel_needle.png"))
    prov.append({"type": "bitmap", "file": "kuby:font/wheel_needle.png", "height": 30, "ascent": 26,
                 "chars": ["".join(chr(0xE080 + k) for k in range(len(NEEDLE_STATES)))]})
    with open(os.path.join(a, "font/wheel.json"), "w", encoding="utf-8") as fh:
        json.dump({"providers": prov}, fh, ensure_ascii=False)
    return len(flat) + 3 + 3


def sheet(path):
    """Vorschau: Scheibe (3D), Rückwand, Bild 0/3/7 der Animation mit Nadel, Dialog-Grafiken; 3-fach."""
    k = 3
    tiles = [disc("casino", size=DISC_3D).resize((S, S), Image.LANCZOS), back(), burst()]
    for i, deg in ((0, 0), (3, 20), (7, 30)):
        f = frame("casino", i, S).copy()
        n = needle_img(deg)
        canvas = Image.new("RGBA", (S, S + 16), T)
        canvas.alpha_composite(f, (0, 16))
        canvas.alpha_composite(n, (64 - HINGE[0], 16 - 7 - HINGE[1]))
        tiles.append(canvas)
    tiles += [ui_sprite("casino"), ui_sprite("smp")]
    w = sum(t.width * k + 16 for t in tiles) + 16
    out = Image.new("RGBA", (w, (S + 16) * k + 32), (24, 18, 30, 255))
    x = 16
    for t in tiles:
        out.alpha_composite(t.resize((t.width * k, t.height * k), Image.NEAREST), (x, 16))
        x += t.width * k + 16
    out.save(path)


if __name__ == "__main__":
    args = sys.argv[1:]
    if "--sheet" in args:
        sheet(args[args.index("--sheet") + 1])
    else:
        print("ok Glücksrad:", write(args[0] if args else os.path.join(REPO, "resourcepack/KubyUI")), "Teile")
