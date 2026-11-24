#!/usr/bin/env python3
"""Las-Vegas-Roulette-Tisch (v1.98): Tableau, Kessel, Rotor, Drehkreuz, Kugel, Marker („Dolly“).

Wunsch des Besitzers: „einen richtigen Roulette-Tisch wie in Las Vegas“. Aufbau wie am echten Tisch:
* Tableau (rv_layout, 64 x 160): Null oben am Kessel, 1–36 in drei Reihen (Felder rot/schwarz, helle Ziffern),
  unten „2 TO 1“ je Kolonne, daneben die Dutzende (1ST 12 …) und außen die einfachen Chancen
  (1-18, EVEN, Raute rot, Raute schwarz, ODD, 19-36). Goldene 1-px-Linien auf grünem Filz.
* Kessel (rv_bowl, 128 x 128, oben offen): Holzrand mit Goldkante, dunkle Kugelbahn mit acht Rauten.
* Rotor (rv_rotor, 96 x 96): 37 Fächer in europäischer Reihenfolge mit Goldstegen, Holzkegel mit Speichen.
* rv_turret (Drehkreuz, 3D), rv_ball (Kugel), rv_dolly (Marker auf der Gewinnzahl).

Farben aus den Rampen von build_casino_px (Icon-Datenbank), Licht oben links, 1-px-Kontur.
Wird von build_casino_px.write() mit aufgerufen; die Geometrie (Pixel ↔ Tisch) steht auch in RouletteTable.java.
"""
import json
import math
import os

from PIL import Image

import build_casino_px as px

T = (0, 0, 0, 0)
rgb = px.rgb
FELT, GOLD, WALNUT, LEATHER = px.FELT, px.GOLD, px.WALNUT, px.LEATHER
RED = [rgb(c) for c in ("6E0A18", "961224", "B82434", "D24050")]
BLACK = [rgb(c) for c in ("0E0C12", "1A171F", "26222C", "38333F")]
ZERO = [rgb(c) for c in ("0E4A22", "167030", "1E8A3E", "34A852")]
CREAM = rgb("F0EADB")
INK = px.INK

# Europäisches Rad (wie RouletteTable.WHEEL)
WHEEL = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24,
         16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26]
REDS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}

# ---------------------------------------------------------------------------------------------------------------------
# Tableau-Geometrie in Pixeln (x quer, y entlang; y = 0 am Kessel). RouletteTable.java rechnet mit denselben Zahlen.
# ---------------------------------------------------------------------------------------------------------------------
LW, LH = 64, 160
X_OUT, X_DOZ, X_NUM, X_END = 2, 13, 24, 57        # Außen-Streifen, Dutzende, Zahlen (3 x 11), Ende
CELL_W, ROW_H = 11, 10
Y_ZERO, Y_NUM = 2, 15                               # Null 2..15, Zahlenreihen ab 15
Y_COL = Y_NUM + 12 * ROW_H                          # 135: Kolonnen „2 TO 1“
Y_END = Y_COL + 11                                  # 146


def num_color(n):
    return ZERO if n == 0 else RED if n in REDS else BLACK


def fill(img, x0, y0, x1, y1, col):
    p = img.load()
    for y in range(max(0, y0), min(img.height, y1)):
        for x in range(max(0, x0), min(img.width, x1)):
            p[x, y] = col


def hline(img, x0, x1, y, col):
    fill(img, x0, y, x1 + 1, y + 1, col)


def vline(img, x, y0, y1, col):
    fill(img, x, y0, x + 1, y1 + 1, col)


NARROW = {"1": (2, "0111010101"), "-": (2, "0000110000"), ":": (1, "01010")}


def text_img(s, col, narrow=False):
    """Text in der 3x5-Schrift; narrow: „1“, „-“ und „:“ schmaler (damit „19-36“ und „2:1“ in ihre Felder passen)."""
    if not narrow:
        w = px.text_width(s)
        im = Image.new("RGBA", (max(1, w), 5), T)
        px.draw_text(im, 0, 0, s, col)
        return im
    glyphs = [NARROW[ch] if ch in NARROW else px._glyph(ch) for ch in s.upper()]
    w = sum(g[0] + 1 for g in glyphs) - 1
    im = Image.new("RGBA", (max(1, w), 5), T)
    p = im.load()
    x = 0
    for gw, g in glyphs:
        for k, b in enumerate(g):
            if b == "1":
                p[x + k % gw, k // gw] = col
        x += gw + 1
    return im


def up(small):
    """Text so drehen, dass seine Oberkante zum Croupier zeigt (im Bild nach +x): Leserichtung dann entlang +y."""
    return small.rotate(-90, expand=True)


def paste_center(img, small, cx, cy):
    img.alpha_composite(small, (int(round(cx - small.width / 2)), int(round(cy - small.height / 2))))


def cell(img, x0, y0, x1, y1, base):
    """Feld mit Rampe: Licht oben links (hellste Stufe), Schatten unten rechts (dunkelste)."""
    fill(img, x0, y0, x1, y1, base[2])
    hline(img, x0, x1 - 1, y0, base[3])
    vline(img, x0, y0, y1 - 1, base[3])
    hline(img, x0, x1 - 1, y1 - 1, base[1])
    vline(img, x1 - 1, y0, y1 - 1, base[1])


def layout(FELT=FELT):
    """Tableau; FELT = Filzfarbe (grün, v2.07 auch rot und violett für die High-Roller- und Elite-Tische)."""
    img = Image.new("RGBA", (LW, LH), FELT[2])
    p = img.load()
    # Filz: ganz feines Webmuster (jede 4. Diagonale eine Stufe heller), Licht oben links
    for y in range(LH):
        for x in range(LW):
            if (x + 2 * y) % 7 == 0:
                p[x, y] = FELT[3]
    gold = GOLD[3]
    # Null (oben, über alle drei Kolonnen, Ecken angeschrägt wie am echten Tisch)
    cell(img, X_NUM + 1, Y_ZERO + 1, X_END, Y_NUM, ZERO)
    for k in range(3):   # angeschrägte Ecken oben
        fill(img, X_NUM + 1, Y_ZERO + 1 + k, X_NUM + 4 - k, Y_ZERO + 2 + k, FELT[2])
        fill(img, X_END - 3 + k, Y_ZERO + 1 + k, X_END, Y_ZERO + 2 + k, FELT[2])
    # Schrift liegt so, dass man sie von der Spielerseite liest: Oberkante zum Croupier (Bild +x)
    paste_center(img, up(text_img("0", CREAM)), (X_NUM + X_END) / 2 + 0.5, (Y_ZERO + Y_NUM) / 2)
    # Zahlen 1–36: Reihe r (0 am Kessel) hat 3r+1, 3r+2, 3r+3; Kolonne c = 0, 1, 2 quer
    for r in range(12):
        for c in range(3):
            n = 3 * r + c + 1
            x0, y0 = X_NUM + c * CELL_W, Y_NUM + r * ROW_H
            cell(img, x0 + 1, y0 + 1, x0 + CELL_W, y0 + ROW_H, num_color(n))
            paste_center(img, up(text_img(str(n), CREAM)), x0 + (CELL_W + 1) / 2, y0 + (ROW_H + 1) / 2)
    # Kolonnen „2 TO 1“ (gedreht, damit es in 11 px passt: „2:1“)
    for c in range(3):
        x0 = X_NUM + c * CELL_W
        # 8 px hoch im 10 px hohen Feld (Y_COL+1 … Y_END-1): Mitte Y_COL + 6, je 1 px Luft (v2.07: saß 1 px zu weit an
        # den Zahlen, weil round(…,5) zur geraden Zahl rundet)
        paste_center(img, up(text_img("2:1", gold, True)), x0 + (CELL_W + 1) / 2, Y_COL + 6)
    # Dutzende: je 4 Reihen, Schrift quer gedreht (liest sich von der Spielerseite)
    for d in range(3):
        y0 = Y_NUM + d * 4 * ROW_H
        lab = up(text_img(("1ST", "2ND", "3RD")[d] + " 12", CREAM))
        paste_center(img, lab, (X_DOZ + X_NUM) / 2 + 0.5, y0 + 2 * ROW_H + 0.5)
    # Einfache Chancen: je 2 Reihen
    outs = ["1-18", "EVEN", "R", "B", "ODD", "19-36"]
    for k, o in enumerate(outs):
        y0 = Y_NUM + k * 2 * ROW_H
        cy = y0 + ROW_H
        cx = (X_OUT + X_DOZ) / 2 + 0.5
        if o in ("R", "B"):
            ramp = RED if o == "R" else BLACK
            for dy in range(-6, 7):
                half = 4 - abs(dy) * 4 // 6
                for dx in range(-half, half + 1):
                    col = ramp[3] if dy < 0 and dx <= 0 else ramp[2] if dy <= 2 else ramp[1]
                    if abs(dx) == half or abs(dy) == 6:
                        col = gold
                    p[int(cx + dx), int(cy + dy)] = col
        else:
            paste_center(img, up(text_img(o, CREAM, True)), cx, cy + 0.5)
    # Goldlinien: Außenrahmen und Raster
    # Die Null ist ein Feld mit angeschrägten Ecken: der Goldrand folgt den Schrägen (v2.07, Wunsch des Besitzers: „den
    # goldenen Rand an die Form des grünen Feldes anpassen“), die Kolonnen-Linien beginnen erst unter der Null
    for x in (X_OUT, X_DOZ, X_NUM, X_NUM + CELL_W, X_NUM + 2 * CELL_W, X_END):
        y_top = Y_ZERO + 4 if x in (X_NUM, X_END) else Y_NUM
        y_bot = Y_END if x >= X_NUM else Y_COL
        vline(img, x, y_top, y_bot, gold)
    hline(img, X_NUM + 4, X_END - 4, Y_ZERO, gold)
    for k in range(4):
        p[X_NUM + 3 - k, Y_ZERO + 1 + k] = gold
        p[X_END - 3 + k, Y_ZERO + 1 + k] = gold
    for r in range(13):
        y = Y_NUM + r * ROW_H
        hline(img, X_NUM, X_END, y, gold)
    hline(img, X_NUM, X_END, Y_END, gold)
    for d in range(4):
        hline(img, X_DOZ, X_NUM, Y_NUM + d * 4 * ROW_H, gold)
    for k in range(7):
        hline(img, X_OUT, X_DOZ, Y_NUM + k * 2 * ROW_H, gold)
    # Abschluss unten: drei goldene Rauten (die mittlere größer, Licht von oben links: obere Hälfte heller)
    mx = (X_NUM + X_END) / 2
    for side, big in ((-1, 0), (0, 1), (1, 0)):
        cx, cy = int(mx + side * 9), Y_END + 6
        r = 2 + big
        for dy in range(-r, r + 1):
            hw = r - abs(dy)
            for dx in range(-hw, hw + 1):
                p[cx + dx, cy + dy] = GOLD[3] if dy < 0 or (dy == 0 and dx < 0) else GOLD[1]
    # Rand: dunkler Filz-Saum, damit die Kante zur Reling sauber ist
    for y in range(LH):
        for x in range(LW):
            if x in (0, LW - 1) or y in (0, LH - 1):
                p[x, y] = FELT[0]
    return img


def ang_of(dx, dy):
    """Winkel im Bild in Grad, 0 = nach oben (zum Betrachter weg), im Uhrzeigersinn."""
    return math.degrees(math.atan2(dx, -dy)) % 360


def rotor(size=96):
    img = Image.new("RGBA", (size, size), T)
    p = img.load()
    c = size / 2
    slot = 360 / 37
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            r = math.hypot(dx, dy)
            a = ang_of(dx, dy)
            light = math.cos(math.radians(a + 45))     # Licht oben links
            if r > 46:
                continue
            if r > 43:                                   # Zahlenring: dunkles Holz, helle Teilstriche je Fach
                k = (a + slot / 2) % slot
                p[x, y] = CREAM if k < 1.1 else WALNUT[1] if light < 0 else WALNUT[2]
            elif r > 42:
                p[x, y] = GOLD[3] if light > 0 else GOLD[2]
            elif r > 32:                                 # Fächer
                i = int(((a + slot / 2) % 360) // slot)
                n = WHEEL[i]
                ramp = num_color(n)
                k = (a + slot / 2) % slot
                if k < 0.9:
                    p[x, y] = GOLD[3] if light > 0 else GOLD[1]       # Steg
                else:
                    p[x, y] = ramp[3] if r > 40 and light > 0.3 else ramp[2] if r > 35 else ramp[1]
            elif r > 30:
                p[x, y] = GOLD[3] if light > 0 else GOLD[2]
            elif r > 7:                                  # Holzkegel mit vier Speichen
                spoke = min(abs(((a + 45) % 90) - 45), 90) < 2.2 * 12 / max(r, 8)
                if spoke:
                    p[x, y] = GOLD[3] if light > 0 else GOLD[2]
                else:
                    shade = 3 if light > 0.5 else 2 if light > -0.2 else 1
                    if r < 12:
                        shade = max(0, shade - 1)
                    p[x, y] = WALNUT[shade]
            else:
                p[x, y] = GOLD[4] if light > 0.6 else GOLD[3] if light > 0 else GOLD[2]
    return img


def bowl(size=128):
    """Kessel von oben: Mitte frei (Rotor), Kugelbahn dunkel poliert, acht Rauten, Holzrand mit Goldkante."""
    img = Image.new("RGBA", (size, size), T)
    p = img.load()
    c = size / 2
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            r = math.hypot(dx, dy)
            a = ang_of(dx, dy)
            light = math.cos(math.radians(a + 45))
            if r < 41.5 or r > 63.5:
                continue
            if r < 43:
                p[x, y] = GOLD[3] if light > 0 else GOLD[1]          # Innenkante (Rotorspalt)
            elif r < 53:
                # Kugelbahn: poliertes dunkles Holz, Glanzstreifen oben links
                p[x, y] = WALNUT[2] if (light > 0.75 and 47 < r < 50) else WALNUT[1] if light > -0.3 else WALNUT[0]
            elif r < 54.5:
                p[x, y] = GOLD[3] if light > 0 else GOLD[2]
            elif r < 62:
                p[x, y] = WALNUT[3] if light > 0.4 else WALNUT[2] if light > -0.4 else WALNUT[1]
            else:
                p[x, y] = INK
    # acht Rauten (Ablenker) auf der Bahn, eine davon minimal anders (einmal gebrochenes Muster: etwas größer)
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        cx, cy = c + math.sin(a) * 48, c - math.cos(a) * 48
        big = 1 if k == 3 else 0
        for ddy in range(-2 - big, 3 + big):
            hw = 1 + big - abs(ddy) // 2
            for ddx in range(-hw, hw + 1):
                q = (int(cx + ddx), int(cy + ddy))
                p[q] = GOLD[4] if ddy < 0 else GOLD[3]
    return img


def small(color_rows):
    h = len(color_rows)
    w = len(color_rows[0])
    img = Image.new("RGBA", (w, h), T)
    for y, row in enumerate(color_rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.load()[x, y] = ch
    return img


def ball_tex():
    W, L, S = rgb("FFFFFF"), rgb("E6E1D6"), rgb("B4A890")
    return small([[W, W, L, L], [W, L, L, S], [L, L, S, S], [L, S, S, S]])


def dolly_tex():
    G3, G2, C, D = GOLD[3], GOLD[2], rgb("E8F4FF"), rgb("A8C0D8")
    return small([[G3, G3, G3, G2], [C, C, C, D], [C, C, D, D], [C, D, D, D]])


def flat(tex, half=8.0):
    """Fläche (eine Seite oben) über das ganze Modell; die Anzeige skaliert sie auf Tischgröße."""
    return {"textures": {"top": tex, "particle": tex},
            "elements": [{"from": [8 - half, 8, 8 - half], "to": [8 + half, 8.01, 8 + half],
                          "faces": {"up": {"texture": "#top", "uv": [0, 0, 16, 16]}}}]}


def box_el(x0, y0, z0, x1, y1, z1, tex="#t"):
    faces = {d: {"texture": tex, "uv": [0, 0, 16, 16]} for d in ("north", "south", "east", "west", "up", "down")}
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces}


def turret(gold_tex):
    els = [box_el(7, 0, 7, 9, 6.9, 9), box_el(6.5, 6, 6.5, 9.5, 7, 9.5)]     # Säule endet in der Kappe (v2.04)
    # vier Arme mit Kugelenden (ein Arm minimal länger: Griff des Croupiers)
    for k, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        ln = 6.6 if k == 0 else 6.0
        if dx:
            x0, x1 = (9, 8 + ln) if dx > 0 else (8 - ln, 7)
            els.append(box_el(x0, 5.2, 7.5, x1, 6.2, 8.5))
            bx = 8 + dx * ln
            els.append(box_el(bx - 0.8, 4.9, 7.2, bx + 0.8, 6.5, 8.8))
        else:
            z0, z1 = (9, 8 + ln) if dz > 0 else (8 - ln, 7)
            els.append(box_el(7.5, 5.2, z0, 8.5, 6.2, z1))
            bz = 8 + dz * ln
            els.append(box_el(7.2, 4.9, bz - 0.8, 8.8, 6.5, bz + 0.8))
    return {"textures": {"t": gold_tex, "particle": gold_tex}, "elements": els}


# ---------------------------------------------------------------------------------------------------------------------
# 3D-Kessel (v2.00, Wunsch des Besitzers: „mehr Tiefe, keine flache Textur“)
# Maße in Blöcken stehen in RouletteTable.java (Kessel-Konstanten); hier in Modell-Einheiten umgerechnet.
# ---------------------------------------------------------------------------------------------------------------------
BOWL_SCALE = 1.5            # Anzeige-Größe eines Kessel-Segments: 16 Einheiten = 1,5 Blöcke
ROTOR_SCALE = 1.0226        # Rotor: 16 Einheiten = 1,0226 Blöcke (Fächer im Bild bei r 32..42 px von 48)


def _el(x0, y0, z0, x1, y1, z1, tex, rot=None):
    e = box_el(x0, y0, z0, x1, y1, z1, tex)
    if rot:
        e["rotation"] = rot
    return e


def bowl_segment(diamond):
    """Ein Sechzehntel des Kessels, radial entlang +x (Mitte des Modells = Kesselmitte, y 8 = Filzhöhe).

    Von außen nach innen: Holzrand (Wand, an der die Kugel läuft) mit Goldlippe, dunkle Kugelbahn 22,5° nach innen
    geneigt, Holzschräge (ebenfalls 22,5°) hinunter zum Rotor, auf jedem zweiten Segment eine goldene Raute.
    """
    u = 0.09375 * BOWL_SCALE / 1.5          # Blöcke je Einheit
    y = lambda h: 8 + h / u                 # Höhe über dem Filz -> Modell-y
    r = lambda b: 8 + b / u                 # Radius -> Modell-x
    els = [
        _el(r(0.69), y(-0.04), 8 - 1.65, r(0.75), y(0.15), 8 + 1.65, "#w"),                 # Randwand
        _el(r(0.684), y(0.15), 8 - 1.6, r(0.70), y(0.158), 8 + 1.6, "#g"),                   # Goldkante innen
        _el(r(0.744), y(0.15), 8 - 1.72, r(0.756), y(0.156), 8 + 1.72, "#g"),                # Goldkante außen
        _el(r(0.60), y(0.10) - 0.3, 8 - 1.5, r(0.69), y(0.10), 8 + 1.5, "#d",                 # Kugelbahn
            {"origin": [r(0.69), y(0.10), 8], "axis": "z", "angle": 22.5}),
        _el(r(0.484), y(0.0628) - 0.3, 8 - 1.3, r(0.607), y(0.0628), 8 + 1.3, "#w",           # Schräge zum Rotor
            {"origin": [r(0.607), y(0.0628), 8], "axis": "z", "angle": 22.5}),
    ]
    if diamond:
        c = r(0.55)
        els.append(_el(c - 0.32, y(0.025), 7.68, c + 0.32, y(0.075), 8.32, "#g",
                       {"origin": [c, y(0.05), 8], "axis": "y", "angle": 45}))
    return {"textures": {"w": "kuby:block/casino/rv_wood", "d": "kuby:block/casino/hd_dark", "g": "kuby:block/casino/hd_gold",
                         "particle": "kuby:block/casino/rv_wood"}, "elements": els}


def _ring16(r0, r1, y0, y1, tex):
    """Sechzehneck-Ring aus 16 tangentialen Teilen (Elemente dürfen nur um 0/±22,5/45° drehen)."""
    out = []
    half = r1 * math.tan(math.radians(11.25)) * 1.04
    for k in range(16):
        q, res = divmod(k, 4)
        ang = [0, 22.5, 45, -22.5][res]
        if res == 3:
            q += 1
        q %= 4
        # Teil in Richtung der Quadranten-Achse anlegen, dann um ang drehen
        if q == 0:
            box = (8 + r0, y0, 8 - half, 8 + r1, y1, 8 + half)
        elif q == 1:
            box = (8 - half, y0, 8 + r0, 8 + half, y1, 8 + r1)
        elif q == 2:
            box = (8 - r1, y0, 8 - half, 8 - r0, y1, 8 + half)
        else:
            box = (8 - half, y0, 8 - r1, 8 + half, y1, 8 - r0)
        rot = {"origin": [8, y0, 8], "axis": "y", "angle": ang} if ang else None
        out.append(_el(*box, tex, rot))
    return out


def rotor3d(tex):
    """Rotor mit Tiefe: Fächer-Bild als Boden einer Rinne, außen erhöhter Zahlenring mit Goldlippe, innen ein
    gestufter Holzkegel (drei Sechzehnecke). Mitte = Kesselmitte, y 8 = Fächerboden."""
    u = 0.0639 * ROTOR_SCALE / 1.0226
    h = lambda b: 8 + b / u
    rr = lambda b: b / u
    els = [{"from": [0, 8, 0], "to": [16, 8.01, 16], "faces": {"up": {"texture": "#top", "uv": [0, 0, 16, 16]}}}]
    # v2.04 gegen Z-Fighting (der Rotor dreht sich, darum flackerte es ständig): die 16 Teile eines Rings überlappen
    # sich an den Stößen, die 16 Speichen einer Kegelstufe sogar alle in der Mitte – gleiche Oberkante = gleiche Ebene.
    # Holzring: Oberseite liegt unter der Goldlippe, entfällt. Goldlippe: jedes zweite Teil 0,012 (0,8 mm) höher.
    ring = _ring16(rr(0.445), rr(0.478), 8, h(0.033), "#w")
    for e in ring:
        e["faces"].pop("up", None)
        e["faces"].pop("down", None)      # liegt auf dem Fächerboden, nie sichtbar
    els += ring
    lip = _ring16(rr(0.44), rr(0.482), h(0.033), h(0.042), "#g")
    for k, e in enumerate(lip):
        if k % 2:
            e["to"][1] += 0.012
            e["from"][1] -= 0.012
    els += lip
    # Kegelstufen: Speichen ohne Oberseite, oben je eine flache Deckplatte mit Sechzehneck-Textur (außen durchsichtig)
    for i, (top, rad) in enumerate(CONE_TIERS):
        spokes = _ring16(0, rr(rad), 8, h(top), "#w")
        for e in spokes:
            e["faces"].pop("up", None)
            e["faces"].pop("down", None)
            # Stirnfläche in der Kegelmitte (liegt bei x = 8 oder z = 8) ist immer verdeckt
            f, t = e["from"], e["to"]
            inner = "west" if f[0] == 8 else "east" if t[0] == 8 else "north" if f[2] == 8 else "south" if t[2] == 8 else None
            if inner:
                e["faces"].pop(inner, None)
        els += spokes
        r = rr(rad) * CAP_PAD
        els.append({"from": [8 - r, h(top), 8 - r], "to": [8 + r, h(top), 8 + r],
                    "faces": {"up": {"texture": "#c%d" % i, "uv": [0, 0, 16, 16]}}})
    textures = {"top": tex, "w": "kuby:block/casino/rv_wood", "g": "kuby:block/casino/hd_gold", "particle": tex}
    for i in range(len(CONE_TIERS)):
        textures["c%d" % i] = "kuby:block/casino/rv_cap%d" % i
    return {"textures": textures, "elements": els}


CONE_TIERS = ((0.035, 0.33), (0.065, 0.24), (0.09, 0.14))
CAP_PAD = 1.12          # Deckplatte etwas größer als der Umkreis des Sechzehnecks (Ecken der Speichen ragen 2 % heraus)


def cone_cap(walnut):
    """Deckplatte einer Kegelstufe (64 x 64): Nussholz, außerhalb des Sechzehnecks der Speichen durchsichtig.
    Speiche k zeigt in Richtung k·22,5°, ihre Stirnfläche liegt im Abstand r (Apothem); Platte = 2·r·CAP_PAD breit."""
    n = 64
    wood = walnut.convert("RGBA").resize((n, n), Image.NEAREST)
    out = Image.new("RGBA", (n, n), T)
    px, src = out.load(), wood.load()
    for y in range(n):
        for x in range(n):
            dx = ((x + 0.5) / n * 2 - 1) * CAP_PAD
            dz = ((y + 0.5) / n * 2 - 1) * CAP_PAD
            inside = all(dx * math.cos(math.radians(22.5 * k)) + dz * math.sin(math.radians(22.5 * k)) <= 1.004
                         for k in range(16))
            if inside:
                px[x, y] = src[x, y]
    return out


def write(out):
    a = os.path.join(out, "assets/kuby")
    tex = os.path.join(a, "textures/block/casino")
    models = os.path.join(a, "models/item/casino")
    items = os.path.join(a, "items/casino")
    for d in (tex, models, items):
        os.makedirs(d, exist_ok=True)
    B = "kuby:block/casino/"

    def item(name, model):
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump(model, fh)
        with open(os.path.join(items, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/" + name}}, fh)

    layout().save(os.path.join(tex, "rv_layout.png"))
    rotor().save(os.path.join(tex, "rv_rotor.png"))
    # alter flacher Kessel (bis v1.99) wird nicht mehr gebraucht
    for old in (os.path.join(tex, "rv_bowl.png"), os.path.join(models, "rv_bowl.json"), os.path.join(items, "rv_bowl.json")):
        if os.path.exists(old):
            os.remove(old)
    ball_tex().save(os.path.join(tex, "rv_ball.png"))
    dolly_tex().save(os.path.join(tex, "rv_dolly.png"))
    item("rv_layout", flat(B + "rv_layout"))
    # High-Roller (rot) und Elite (violett), v2.07: gleiches Tableau und gleicher Filz in der Tischfarbe
    for suf, felt in (("_red", px.FELT_RED), ("_violet", px.FELT_VIOLET)):
        layout(felt).save(os.path.join(tex, "rv_layout" + suf + ".png"))
        item("rv_layout" + suf, flat(B + "rv_layout" + suf))
        Image.new("RGBA", (16, 16), felt[2]).save(os.path.join(tex, "rv_felt" + suf + ".png"))
        item("m_rv_felt" + suf, px.cube_model(B + "rv_felt" + suf))
    walnut = px.mat_walnut()
    for i in range(len(CONE_TIERS)):
        cone_cap(walnut).save(os.path.join(tex, "rv_cap%d.png" % i))
    item("rv_rotor", rotor3d(B + "rv_rotor"))
    item("rv_bowlseg", bowl_segment(False))
    item("rv_bowlseg_d", bowl_segment(True))
    item("rv_turret", turret(B + "hd_gold"))
    item("rv_ball", {"textures": {"t": B + "rv_ball", "particle": B + "rv_ball"}, "elements": [box_el(0, 0, 0, 16, 16, 16)]})
    # Filz um das Tableau (Croupier-Streifen, Kesselseite): einfarbig, gleiche Grundstufe wie das Tableau
    Image.new("RGBA", (16, 16), FELT[2]).save(os.path.join(tex, "rv_felt.png"))
    item("m_rv_felt", px.cube_model(B + "rv_felt"))
    item("rv_dolly", {"textures": {"t": B + "rv_dolly", "particle": B + "rv_dolly"},
                      "elements": [box_el(5, 0, 5, 11, 12, 11), box_el(4, 12, 4, 12, 16, 12)]})


def preview(path):
    """Vorschau (4-fach): Tableau, Kessel mit Rotor, Kleinteile."""
    lay = layout().resize((LW * 4, LH * 4), Image.NEAREST)
    bw = bowl()
    rt = rotor()
    wheel = Image.new("RGBA", (128, 128), (40, 30, 24, 255))
    # Rotor wie im Spiel: 1,0226 Blöcke bei 1,5 Blöcken Kessel (85,3 px je Block)
    wheel.alpha_composite(rt.resize((87, 87), Image.NEAREST), (20, 20))
    wheel.alpha_composite(bw)
    wheel = wheel.resize((512, 512), Image.NEAREST)
    out = Image.new("RGBA", (lay.width + 540, max(lay.height, 520)), (30, 30, 34, 255))
    out.alpha_composite(lay, (0, 0))
    out.alpha_composite(wheel, (lay.width + 20, 0))
    out.save(path)


if __name__ == "__main__":
    import sys
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(sys.argv[2])
    else:
        write(sys.argv[1] if len(sys.argv) > 1 else os.path.join(px.REPO, "resourcepack/KubyUI"))
