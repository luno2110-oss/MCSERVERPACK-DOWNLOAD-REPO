#!/usr/bin/env python3
"""Slot-Themen (v2.05, Wunsch des Besitzers: „pro Slot-Maschine unterschiedliche Icons und Gewinne“).

Drei neue Walzen-Sätze zu je zehn Symbolen, gezeichnet im Stil der Früchte-Klassik (tools/build_casino.py) und nach
tools/icon_db/STYLE.md: 1-px-Kontur fast schwarz, Farben aus Rampen mit 4–5 Stufen, Licht oben links (Oberkante je
Fläche eine Stufe heller, Unterkante eine dunkler), Lichter wärmer, Schatten kühler, 1–3 Glanzpunkte.

Index 0 ist je Satz das kleine Symbol (zahlt schon einzeln, wie die Kirsche), Index 9 das Jackpot-Symbol.
Die Reihenfolge muss zu SlotTheme.java passen.

* gold  (Goldrausch): Nugget, Spitzhacke, Grubenlampe, Hufeisen, Dynamit, Goldbarren, Geldsack, Krone, Rubin, Schatztruhe
* neon  (Neon 7):     Neon-Kirsche, BAR, Doppel-BAR, Dreifach-BAR, Herz, Blitz, Würfel, Stern, blaue 7, Neon-7
* ozean (Tiefsee):    Muschel, Seestern, Fisch, Krabbe, Anker, Krake, Steuerrad, Perle, Goldfisch, Dreizack

Je Thema eine eigene Walzen-Grundplatte (reel_plate_<thema>), damit jeder Automat auf einen Blick anders aussieht.
Schreibt slot_<id>(_blur).png als Item, sym_<id>(_blur).png als Block-Textur und sr_<id>(_blur) als Walzen-Kachel.
Aufruf: python3 tools/build_slot_themes.py [pack] [--sheet bild.png]
"""
import json
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_casino as bc  # noqa: E402

rgb = bc.rgb
OUT = rgb("1A1218")


def ramp(*hexes):
    return [rgb(h) for h in hexes]


GOLD = ramp("7A4A0C", "B8740A", "E8A412", "F8CC2A", "FCEB7A")
IRON = ramp("3A3F4A", "5E6672", "8C95A2", "BCC4CE", "E8EDF2")
WOOD = ramp("3E220E", "5E3618", "80502A", "A06A3A", "C08A52")
RED = ramp("6A0A1A", "A01224", "D41F2E", "F04A44", "FF8A6E")
PINK = ramp("7A0A4E", "C0147A", "F02E9E", "FF6EC4", "FFB8E6")
CYAN = ramp("0A4A6A", "0E7AA6", "18B0D8", "52DCF2", "B0F6FF")
YEL = ramp("8A5A00", "D49A00", "FFD21A", "FFEA5A", "FFF8B0")
BLUE = ramp("102A6A", "1A4AB0", "2A78E8", "5AA8FA", "A8DCFF")
PURP = ramp("3A0E5A", "5E1A8E", "8A30C0", "B460E6", "DCA8FA")
ORNG = ramp("7A2A06", "B84A0A", "E8761A", "FAA43A", "FFD27A")
TEAL = ramp("0A3A3E", "0E5E64", "16908E", "3EC4B4", "9EF0DE")
PEACH = ramp("8A3A2E", "C2604A", "E8906E", "F8B894", "FFE2C8")
WHITE = ramp("7A808E", "A8B0BE", "D4DAE4", "EEF2F8", "FFFFFF")
SAND = ramp("8A6A3A", "B8945A", "D8BC84", "EED8A8", "FFF2D4")
GREEN = ramp("1E4A14", "2E7A1E", "46A82A", "7AD24A", "B8F08A")
INK = ramp("12101A", "1E1C2A", "2C2A3C", "3E3C52", "585670")


# ---------------------------------------------------------------------------------------------------------------------
# Werkzeug: Formen als Masken, schattiert füllen
# ---------------------------------------------------------------------------------------------------------------------

def M(draw):
    """Maske aus einer Pix-Zeichnung (Menge der gesetzten Pixel)."""
    t = bc.Pix(32)
    draw(t)
    return set(t.c)


def poly(pts):
    return M(lambda t: t.poly(pts, 1))


def disk(cx, cy, r):
    return M(lambda t: t.disk(cx, cy, r, 1))


def rect(x0, y0, x1, y1):
    return {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)}


def line(x0, y0, x1, y1, w):
    return M(lambda t: t.line(x0, y0, x1, y1, 1, w))


def ring(cx, cy, r0, r1):
    return M(lambda t: t.ring(cx, cy, r0, r1, 1))


def sector(cx, cy, r0, r1, a0, a1):
    return M(lambda t: t.sector(cx, cy, r0, r1, a0, a1, 1))


def shade(p, mask, rp, base=None, rim=True):
    """Füllt die Maske mit der Rampe rp (dunkel → hell): Licht oben links, Oberkante eine Stufe heller, Unterkante
    eine dunkler. base = Mittelstufe (Standard: zweithellste − 1)."""
    if not mask:
        return
    n = len(rp)
    mid = n - 3 if base is None else base
    xs = [x for x, _ in mask]
    ys = [y for _, y in mask]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    w, h = max(1, x1 - x0 + 1), max(1, y1 - y0 + 1)
    for x, y in mask:
        t = 0.42 * (x - x0 + 0.5) / w + 0.58 * (y - y0 + 0.5) / h
        i = mid + 1 if t < 0.34 else mid if t < 0.68 else mid - 1
        if rim:
            if (x - 1, y) not in mask or (x, y - 1) not in mask:
                i += 1
            elif (x + 1, y) not in mask or (x, y + 1) not in mask:
                i -= 1
        p.put(x, y, rp[max(0, min(n - 1, i))])


def flat(p, mask, col):
    for x, y in mask:
        p.put(x, y, col)


def glint(p, pts, col=None):
    for x, y in pts:
        p.put(x, y, col or rgb("FFFFFF"))


# ---------------------------------------------------------------------------------------------------------------------
# Goldrausch
# ---------------------------------------------------------------------------------------------------------------------

def g_nugget(p):
    body = poly([(5, 20), (7, 14), (11, 13), (13, 8), (19, 7), (22, 10), (26, 11), (28, 17), (25, 22), (21, 22),
                 (18, 26), (11, 26), (8, 23)])
    shade(p, body, GOLD)
    flat(p, poly([(11, 13.5), (13.5, 9), (18.5, 8), (17, 13)]), GOLD[4])        # Facetten, Licht oben links
    flat(p, poly([(7, 15), (11, 14), (12, 19), (8, 20)]), GOLD[3])
    flat(p, poly([(19, 10), (22, 11), (25, 12), (21, 15)]), GOLD[3])
    flat(p, poly([(17, 13), (21, 15), (20, 20), (14, 19)]), GOLD[2])
    flat(p, line(12, 19, 17, 18, 1) | line(21, 15, 25, 18, 1), GOLD[1])      # Ritzen
    flat(p, poly([(12, 22), (17, 21), (19, 24), (13, 25)]), GOLD[1])
    glint(p, [(14, 10), (15, 10), (9, 16)])


def g_pick(p):
    shade(p, line(9, 28, 21, 10, 3.2), WOOD)
    head = sector(17, 21, 11.5, 15, 205, 335)
    shade(p, head, IRON)
    flat(p, poly([(18.5, 6), (21.5, 6), (22, 10), (18, 10)]), IRON[1])           # Hülse
    glint(p, [(9, 9), (10, 8)])


def g_lamp(p):
    shade(p, rect(10, 9, 22, 11), IRON)                                        # Dach
    flat(p, ring(16, 7, 3.2, 4.4) & {(x, y) for x in range(32) for y in range(0, 7)}, IRON[3])
    glass = rect(11, 12, 21, 23)
    for x, y in glass:
        d = math.hypot(x + 0.5 - 16, y + 0.5 - 17.5)
        p.put(x, y, YEL[4] if d < 2.6 else YEL[3] if d < 4.2 else YEL[2] if d < 5.8 else ORNG[3])
    for x in (13, 19):
        flat(p, rect(x, 12, x, 23), IRON[2])                                   # Gitterstäbe
    shade(p, rect(9, 24, 23, 26), IRON)                                        # Boden
    glint(p, [(17, 16)], YEL[4])


def g_horseshoe(p):
    shoe = sector(16, 14, 6, 12.5, 150, 30) | rect(3, 14, 9, 25) | rect(23, 14, 29, 25)
    shoe |= rect(2, 24, 10, 28) | rect(22, 24, 30, 28)                        # verbreiterte Enden
    shoe -= rect(10, 15, 22, 28)
    shade(p, shoe, GOLD)
    for x, y in ((5, 17), (5, 21), (26, 17), (26, 21), (8, 8), (23, 8)):
        flat(p, rect(x, y, x, y + 1), GOLD[0])                               # Nagellöcher, gleicher Abstand
    glint(p, [(7, 6), (8, 5)])


def g_dynamite(p):
    for i, x in enumerate((8, 14, 20)):
        stick = rect(x, 11 + (i == 1) * -2, x + 5, 27)
        shade(p, stick, RED)
        flat(p, rect(x, 18, x + 5, 19), SAND[2])                               # Band
    flat(p, rect(8, 18, 25, 19), SAND[2])
    shade(p, line(17, 9, 21, 3, 1.4), INK, rim=False)                          # Zündschnur
    glint(p, [(22, 2), (23, 3), (21, 1)], YEL[4])
    p.put(23, 1, ORNG[3])
    glint(p, [(9, 12), (15, 10)], RED[4])


def g_bar(p):
    top = poly([(3, 13), (18, 8), (29, 12), (14, 17)])
    front = poly([(3, 13), (14, 17), (14, 26), (3, 21)])
    side = poly([(14, 17), (29, 12), (29, 21), (14, 26)])
    flat(p, front, GOLD[2])
    flat(p, side, GOLD[1])
    flat(p, top, GOLD[3])
    flat(p, line(4, 13.5, 17.5, 9, 1), GOLD[4])                               # Lichtkante
    flat(p, line(14, 17.5, 14, 25.5, 1), GOLD[3])
    flat(p, poly([(17, 18), (25, 15.5), (25, 17), (17, 19.5)]), GOLD[0])     # Prägung
    glint(p, [(9, 12), (10, 12), (11, 11)])


def g_sack(p):
    bag = disk(16, 20, 9.2) | poly([(12, 11), (20, 11), (22, 15), (10, 15)])
    shade(p, bag, SAND)
    shade(p, poly([(11, 6), (21, 6), (19, 10), (13, 10)]), SAND)               # Zipfel
    flat(p, rect(12, 10, 20, 11), WOOD[1])                                     # Schnur
    shade(p, disk(16, 21, 4.6), GOLD)
    flat(p, ring(16, 21, 2.6, 3.4), GOLD[1])                                   # Münzrand
    glint(p, [(14, 19)], GOLD[4])
    glint(p, [(10, 16), (11, 15)], SAND[4])


def g_crown(p):
    crown = poly([(5, 25), (5, 11), (10, 17), (16, 7), (22, 17), (27, 11), (27, 25)])
    shade(p, crown, GOLD)
    flat(p, rect(5, 22, 27, 25), GOLD[1])
    flat(p, rect(5, 22, 27, 22), GOLD[3])
    for cx, col in ((10, RED), (16, BLUE), (22, RED)):
        shade(p, disk(cx, 19, 1.8), col, rim=False)
    for x, y in ((5, 10), (16, 6), (27, 10)):
        flat(p, disk(x + 0.5, y + 0.5, 1.6), GOLD[4])
    glint(p, [(15, 9), (9, 18)], GOLD[4])


def g_ruby(p):
    gem = poly([(10, 6), (22, 6), (28, 13), (16, 28), (4, 13)])
    shade(p, gem, RED)
    flat(p, poly([(10, 6), (22, 6), (20, 12), (12, 12)]), RED[3])               # Tafel
    flat(p, poly([(4, 13), (12, 12), (16, 28)]), RED[2])
    flat(p, poly([(28, 13), (20, 12), (16, 28)]), RED[1])
    flat(p, poly([(12, 12), (20, 12), (16, 28)]), RED[2])
    glint(p, [(12, 8), (13, 8), (12, 9)], RED[4])
    p.put(13, 7, rgb("FFFFFF"))


def g_chest(p):
    lid = sector(16, 16, 0, 11.5, 180, 360) & rect(4, 4, 28, 16)
    shade(p, lid, WOOD)
    body = rect(5, 16, 27, 27)
    shade(p, body, WOOD)
    for x in (9, 22):
        flat(p, rect(x, 5, x + 1, 27) & (lid | body), GOLD[2])                 # Beschläge
    flat(p, rect(5, 16, 27, 17), GOLD[3])
    shade(p, rect(14, 15, 18, 20), GOLD)                                        # Schloss
    p.put(16, 18, WOOD[0])
    for cx, cy in ((12, 5), (16, 4), (20, 5)):
        shade(p, disk(cx, cy, 2.1), GOLD, rim=False)                           # Münzen schauen heraus
    glint(p, [(15, 3), (11, 4)], GOLD[4])


# ---------------------------------------------------------------------------------------------------------------------
# Neon 7 (dunkle Walzen, leuchtende Farben)
# ---------------------------------------------------------------------------------------------------------------------
BAR_FONT = {"B": ["110", "101", "110", "101", "110"], "A": ["010", "101", "111", "101", "101"],
            "R": ["110", "101", "110", "101", "101"]}


def _bar(p, y0, h, rp, text=True):
    plate = rect(5, y0, 26, y0 + h - 1) - {(5, y0), (26, y0), (5, y0 + h - 1), (26, y0 + h - 1)}
    shade(p, plate, rp)
    inner = rect(7, y0 + 2, 24, y0 + h - 3)
    flat(p, inner, INK[1])
    if text and h >= 9:
        ty = y0 + (h - 5) // 2
        for i, ch in enumerate("BAR"):
            for r, row in enumerate(BAR_FONT[ch]):
                for c, b in enumerate(row):
                    if b == "1":
                        p.put(10 + i * 4 + c, ty + r, rp[4])
    elif not text:
        flat(p, rect(9, y0 + h // 2 - 1, 22, y0 + h // 2), rp[3])


def n_cherry(p):
    stem = GREEN
    p.line(11, 17, 17, 6, stem[3], 1.6)
    p.line(22, 18, 17, 6, stem[3], 1.6)
    shade(p, poly([(17, 6), (25, 3), (27, 7), (20, 8)]), stem)
    for cx, cy in ((10.5, 21.5), (21.5, 22.5)):
        shade(p, disk(cx, cy, 5.6), PINK)
        glint(p, [(int(cx - 2), int(cy - 3))], PINK[4])


def n_bar1(p):
    _bar(p, 11, 11, CYAN)


def n_bar2(p):
    _bar(p, 6, 9, PINK)
    _bar(p, 17, 9, PINK)


def n_bar3(p):
    for y in (4, 13, 22):
        _bar(p, y, 7, YEL, text=False)


def n_heart(p):
    heart = disk(11, 12, 6.2) | disk(21, 12, 6.2) | poly([(5, 14), (27, 14), (16, 28)])
    shade(p, heart, RED)
    glint(p, [(8, 9), (9, 8), (9, 9)], RED[4])


def n_bolt(p):
    bolt = poly([(19, 3), (8, 18), (15, 18), (11, 29), (25, 12), (17, 12), (22, 3)])
    shade(p, bolt, YEL)
    glint(p, [(19, 5), (18, 6)])


def n_dice(p):
    top = poly([(16, 4), (27, 9), (16, 14), (5, 9)])
    left = poly([(5, 9), (16, 14), (16, 28), (5, 22)])
    right = poly([(16, 14), (27, 9), (27, 22), (16, 28)])
    flat(p, top, WHITE[4])
    flat(p, left, WHITE[2])
    flat(p, right, WHITE[1])
    flat(p, line(5.5, 9.5, 15.5, 14, 1), WHITE[3])                             # Kanten
    flat(p, disk(16, 9, 1.5), RED[2])                                          # oben: eins
    for x, y in ((8.5, 14.5), (13, 21.5)):                                     # links: zwei
        flat(p, disk(x, y, 1.3), RED[1])
    for x, y in ((19, 22), (21.5, 18.5), (24.5, 15)):                           # rechts: drei
        flat(p, disk(x, y, 1.3), RED[0])


def n_star(p):
    pts = []
    for i in range(10):
        a = math.radians(-90 + i * 36)
        r = 13 if i % 2 == 0 else 5.6
        pts.append((16 + r * math.cos(a), 17 + r * math.sin(a)))
    shade(p, poly(pts), YEL)
    glint(p, [(14, 10), (15, 9)])


def _seven(p, rp, outer):
    shade(p, poly([(5, 4), (27, 4), (27, 9), (17, 28), (10, 28), (19, 10), (5, 10)]), outer)
    shade(p, poly([(7, 6), (25, 6), (25, 9), (15.5, 26), (12.5, 26), (21, 8), (7, 8)]), rp)


def n_seven_blue(p):
    _seven(p, BLUE, CYAN)


def n_seven(p):
    _seven(p, PINK, YEL)
    glint(p, [(8, 6), (9, 6)])


# ---------------------------------------------------------------------------------------------------------------------
# Tiefsee
# ---------------------------------------------------------------------------------------------------------------------

def o_shell(p):
    shell = sector(16, 24, 0, 15, 200, 340) | rect(13, 23, 19, 28)
    shade(p, shell, PEACH)
    for a in range(212, 340, 21):                                             # Rippen, gleicher Abstand
        r = math.radians(a)
        p.line(16, 24, 16 + 14 * math.cos(r), 24 + 14 * math.sin(r), PEACH[1], 1)
    flat(p, rect(12, 25, 20, 28), PEACH[2])
    glint(p, [(9, 14), (10, 13)], PEACH[4])


def o_starfish(p):
    pts = []
    for i in range(10):
        a = math.radians(-90 + i * 36)
        r = 13.5 if i % 2 == 0 else 5.2
        pts.append((16 + r * math.cos(a), 17 + r * math.sin(a)))
    shade(p, poly(pts), ORNG)
    for i in range(5):
        a = math.radians(-90 + i * 72)
        for k in (4, 8):
            p.put(int(16 + k * math.cos(a)), int(17 + k * math.sin(a)), ORNG[4])
    glint(p, [(14, 12)], ORNG[4])


def _fish(p, rp, fin):
    body = M(lambda t: [t.put(x, y, 1) for x in range(32) for y in range(32)
                        if ((x + 0.5 - 14) / 10.5) ** 2 + ((y + 0.5 - 16) / 7.2) ** 2 <= 1])
    tail = poly([(23, 16), (30, 8), (29, 24)])
    shade(p, tail, fin)
    shade(p, body, rp)
    shade(p, poly([(10, 10), (16, 5), (19, 10)]), fin)                          # Rückenflosse
    flat(p, line(9, 18, 13, 21, 1), rp[1])                                     # Kieme
    flat(p, disk(8, 14, 1.6), rgb("FFFFFF"))
    p.put(8, 14, INK[0])
    glint(p, [(12, 11), (13, 11)], rp[4])


def o_fish(p):
    _fish(p, BLUE, CYAN)


def o_goldfish(p):
    _fish(p, GOLD, ORNG)
    glint(p, [(16, 12)])


def o_crab(p):
    shade(p, M(lambda t: [t.put(x, y, 1) for x in range(32) for y in range(32)
                          if ((x + 0.5 - 16) / 9) ** 2 + ((y + 0.5 - 19) / 6) ** 2 <= 1]), RED)
    for sx in (-1, 1):
        shade(p, disk(16 + sx * 11, 9, 3.6) - disk(16 + sx * 12.5, 7.5, 1.6), RED)   # Scheren
        flat(p, line(16 + sx * 6, 15, 16 + sx * 10, 11, 1.4), RED[1])
        for k in range(3):
            flat(p, line(16 + sx * (6 + k * 2), 22, 16 + sx * (9 + k * 2), 27, 1.2), RED[1])
    for x in (13, 19):
        flat(p, rect(x, 12, x, 14), INK[0])
        flat(p, rect(x - 1, 11, x, 12), rgb("FFFFFF"))
    glint(p, [(11, 16), (12, 15)], RED[4])


def o_anchor(p):
    shade(p, rect(15, 7, 17, 25), IRON)
    shade(p, ring(16, 5, 2.0, 3.6), IRON)
    shade(p, rect(10, 10, 22, 12), IRON)
    shade(p, sector(16, 17, 8, 10.6, 20, 160), IRON)
    shade(p, poly([(4, 18), (9, 16), (8, 21)]) | poly([(28, 18), (23, 16), (24, 21)]), IRON)
    glint(p, [(15, 8), (11, 10)], IRON[4])


def o_octopus(p):
    head = disk(16, 12, 8.5) | rect(9, 12, 23, 18)
    for i, x in enumerate((7, 11, 15, 19, 23)):
        shade(p, line(x + 1.5, 17, x + (i - 2) * 0.8 + 1.5, 28, 2.6), PURP)
    shade(p, head, PURP)
    for x in (12, 20):
        flat(p, disk(x, 14, 2.0), rgb("FFFFFF"))
        p.put(x, 14, INK[0])
    glint(p, [(11, 7), (12, 6)], PURP[4])


def o_wheel(p):
    for i in range(8):
        a = math.radians(i * 45)
        shade(p, line(16, 16, 16 + 14 * math.cos(a), 16 + 14 * math.sin(a), 2.2), WOOD)
    shade(p, ring(16, 16, 7.5, 10.5), WOOD)
    shade(p, disk(16, 16, 3), GOLD)
    glint(p, [(15, 15)], GOLD[4])


def o_pearl(p):
    shade(p, sector(16, 20, 0, 13, 0, 180) & rect(2, 20, 30, 30), PEACH)       # Muschel unten
    shade(p, disk(16, 15, 7.2), WHITE)
    flat(p, ring(16, 15, 5.0, 6.0) & rect(17, 15, 24, 22), WHITE[1])
    glint(p, [(13, 11), (14, 11), (13, 12)])


def o_trident(p):
    gold = GOLD
    shade(p, rect(14, 10, 17, 29), gold)
    shade(p, sector(15.5, 9, 6, 9.4, 0, 180), gold)
    for x in (6, 14, 23):
        shade(p, poly([(x - 1, 11), (x + 1.5, 1), (x + 4, 11)]), gold)
    shade(p, disk(15.5, 14.5, 2.6), TEAL)
    glint(p, [(14, 13)], TEAL[4])
    glint(p, [(15, 4), (7, 5), (16, 15)], GOLD[4])


THEMES = {
    "gold": ([("g_nugget", g_nugget), ("g_pick", g_pick), ("g_lamp", g_lamp), ("g_horseshoe", g_horseshoe),
              ("g_dynamite", g_dynamite), ("g_bar", g_bar), ("g_sack", g_sack), ("g_crown", g_crown), ("g_ruby", g_ruby),
              ("g_chest", g_chest)], ramp("5A3A1A", "F2E2C2", "FFF6E2")),
    "neon": ([("n_cherry", n_cherry), ("n_bar1", n_bar1), ("n_bar2", n_bar2), ("n_bar3", n_bar3), ("n_heart", n_heart),
              ("n_bolt", n_bolt), ("n_dice", n_dice), ("n_star", n_star), ("n_seven_blue", n_seven_blue),
              ("n_seven", n_seven)], ramp("0A0A1A", "1C1A34", "2A2848")),
    "ozean": ([("o_shell", o_shell), ("o_starfish", o_starfish), ("o_fish", o_fish), ("o_crab", o_crab),
               ("o_anchor", o_anchor), ("o_octopus", o_octopus), ("o_wheel", o_wheel), ("o_pearl", o_pearl),
               ("o_goldfish", o_goldfish), ("o_trident", o_trident)], ramp("2A6A7A", "CDEFF2", "EAFBFC")),
}


def symbol(fn):
    p = bc.Pix(32)
    fn(p)
    return p.image(outline=OUT)


def plate(edge, base, light):
    """Walzen-Grundplatte 16 x 16 je Thema: Kante, Mitte hell (wie reel_plate der Klassik)."""
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = edge if y in (0, 15) else light if 3 <= y <= 12 else base
    return img


def write(pack):
    a = os.path.join(pack, "assets/kuby")
    items_tex = os.path.join(a, "textures/item/casino")
    block_tex = os.path.join(a, "textures/block/casino")
    models = os.path.join(a, "models/item/casino")
    items = os.path.join(a, "items/casino")
    for d in (items_tex, block_tex, models, items):
        os.makedirs(d, exist_ok=True)
    import build_casino_px as px

    def item(name, model):
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump(model, fh)
        with open(os.path.join(items, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/" + name}}, fh)

    n = 0
    for theme, (syms, pl) in THEMES.items():
        plate(pl[0], pl[1], pl[2]).save(os.path.join(block_tex, "reel_plate_" + theme + ".png"))
        for name, fn in syms:
            img = symbol(fn)
            for suffix, im in (("", img), ("_blur", bc.blur(img))):
                im.save(os.path.join(items_tex, "slot_" + name + suffix + ".png"))
                im.save(os.path.join(block_tex, "sym_" + name + suffix + ".png"))
                with open(os.path.join(models, "slot_" + name + suffix + ".json"), "w", encoding="utf-8") as fh:
                    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/casino/slot_" + name + suffix}}, fh)
                with open(os.path.join(items, "slot_" + name + suffix + ".json"), "w", encoding="utf-8") as fh:
                    json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/slot_" + name + suffix}}, fh)
                m = px.reel_model(name, suffix == "_blur")
                m["textures"]["plate"] = "kuby:block/casino/reel_plate_" + theme
                m["textures"]["particle"] = m["textures"]["plate"]
                item("sr_" + name + suffix, m)
                n += 1
    return n


def sheet(path):
    """Kontaktbogen 4-fach: Früchte-Klassik zum Vergleich, dann die drei neuen Sätze auf ihrer Walzenfarbe."""
    rows = [("frucht", [bc._sym(fn) for _, fn in bc.SYMBOLS], (244, 240, 230))]
    for theme, (syms, pl) in THEMES.items():
        rows.append((theme, [symbol(fn) for _, fn in syms], pl[2][:3]))
    W, H = 10 * 140 + 20, len(rows) * 140 + 10
    out = Image.new("RGBA", (W, H), (30, 28, 36, 255))
    for r, (theme, imgs, bg) in enumerate(rows):
        band = Image.new("RGBA", (W - 20, 134), bg + (255,))
        out.alpha_composite(band, (10, 5 + r * 140))
        for i, im in enumerate(imgs):
            out.alpha_composite(im.resize((128, 128), Image.NEAREST), (14 + i * 140, 8 + r * 140))
    out.save(path)


if __name__ == "__main__":
    args = sys.argv[1:]
    if "--sheet" in args:
        sheet(args[args.index("--sheet") + 1])
    else:
        print(write(args[0] if args else os.path.join(os.path.dirname(HERE), "resourcepack/KubyUI")), "Kacheln")
