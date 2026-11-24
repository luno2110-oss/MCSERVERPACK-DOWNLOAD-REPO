"""Casino-Grafik im Minecraft-Look (v1.69): ersetzt die hochauflösende Fassung (build_casino_hd.py).

Wunsch des Besitzers: „alles simpler, es soll immer noch wie Minecraft wirken“. Darum:
* etwa 16 Pixel je Block (Filz 96 x 48 für den 6-Block-Tisch, Materialien 16 x 16, Karten 32 x 32, Chips 16 x 16),
* harte Farbstufen aus den Rampen der Icon-Datenbank, 1-px-Konturen, keine weichen Verläufe, kein Glühen, keine
  Schriftarten (eigene 3 x 5-Pixelschrift),
* alle Texturen unter textures/block/casino/ (wie die Tisch-Materialien, die im Spiel sicher geladen werden).

Die Modell- und Item-Namen bleiben gleich (bj_felt, pk_felt, m_hd_*, c3_<karte>, c3chip_<farbe>, bj_btn_*, bj_ctl_*,
pk_btn_*, pk_dealer, sl_<stil>_top/front), der Plugin-Code muss nichts wissen.

Aufruf: python3 tools/build_casino_px.py [resourcepack/KubyUI] | --preview bild.png
"""
import json
import math
import os
import shutil
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_casino as bc  # noqa: E402  (Pix, Karten, Chip-Rampen)

REPO = os.path.dirname(HERE)
T = (0, 0, 0, 0)
rgb, mix = bc.rgb, bc.mix

GOLD = [rgb(c) for c in ("8A5A08", "B57E0C", "D9A11A", "F2C23A", "FAE07A")]
FELT = [rgb(c) for c in ("163F27", "1B4D31", "215C3A", "2A6B44", "347A50")]
# Weinrot für die High-Roller-Tische
FELT_RED = [rgb(c) for c in ("3C0A14", "4E0F1C", "621624", "761E2E", "8A2A3A")]
# Violett (höchste Stufe): gedecktes Königsviolett, Licht wärmer (ins Rosa), Schatten kühler (ins Blau)
FELT_VIOLET = [rgb(c) for c in ("231238", "30194C", "3F2262", "512E78", "653D8E")]
LEATHER = [rgb(c) for c in ("0E0C12", "1A171F", "26222C", "38333F")]
CREAM = [rgb(c) for c in ("B4A890", "CFC4AC", "E2D9C4", "F0EADB")]
WALNUT = [rgb(c) for c in ("2A170C", "3E2614", "52341C", "684426")]
VELVET = [rgb(c) for c in ("4A0610", "6E0A18", "961224", "B82434")]
NEON_P = [rgb(c) for c in ("8A1050", "D0207A", "FF4FA8", "FFB0D8")]
NEON_C = [rgb(c) for c in ("0A6A7A", "12A8C0", "3FE6F0", "B8F6FA")]
PURPLE = [rgb(c) for c in ("140A20", "1E0E30", "2C1646", "40205E")]
# Tiefsee-Automat (v2.05): tiefes, gedecktes Petrol statt flacher Sättigung
OCEAN = [rgb(c) for c in ("08202E", "0C3244", "14495E", "1E6478")]
PAPER = [rgb(c) for c in ("C8C2B4", "E6E1D6", "F6F3EC")]
INK = rgb("0C0907")

# ---------------------------------------------------------------------------------------------------------------------
# 3 x 5 Pixelschrift
# ---------------------------------------------------------------------------------------------------------------------
FONT = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110",
    "E": "111100110100111", "F": "111100110100100", "G": "011100101101011", "H": "101101111101101",
    "I": "111010010010111", "J": "001001001101010", "K": "101101110101101", "L": "100100100100111",
    "M": "101111111101101", "N": "110101101101101", "O": "010101101101010", "P": "110101110100100",
    "Q": "010101101110011", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010",
    "U": "101101101101111", "V": "101101101101010", "W": "101101111111101", "X": "101101010101101",
    "Y": "101101010010010", "Z": "111001010100111", "Ä": "101010101111101", "Ö": "101010101101010",
    "Ü": "101000101101111", "0": "111101101101111", "1": "010110010010111", "2": "110001010100111",
    "3": "110001010001110", "4": "101101111001001", "5": "111100110001110", "6": "011100110101010",
    "7": "111001010010010", "8": "010101010101010", "9": "010101011001110", ":": "000010000010000",
    "-": "000000111000000", "+": "000010111010000", "½": "100101010010011", " ": "000000000000000",
    "'": "010010000000000", "/": "001001010100100",
}


WIDE = {  # breitere Glyphen, damit M/N/W in 3 px nicht wie H aussehen
    "M": (5, "1000111011101011000110001"), "N": (4, "10011101101110011001"), "W": (5, "1000110001101011101110001"),
}


def _glyph(ch):
    if ch in WIDE:
        return WIDE[ch]
    return 3, FONT.get(ch, FONT[" "])


def text_width(s):
    return max(0, sum(_glyph(c)[0] + 1 for c in s.upper()) - 1)


def draw_text(img, x, y, s, col, outline=None):
    px = img.load()
    pts = []
    cx = x
    for ch in s.upper():
        gw, g = _glyph(ch)
        for k, b in enumerate(g):
            if b == "1":
                pts.append((cx + k % gw, y + k // gw))
        cx += gw + 1
    if outline is not None:
        ps = set(pts)
        for (a, b) in pts:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1)):
                q = (a + dx, b + dy)
                if q not in ps and 0 <= q[0] < img.width and 0 <= q[1] < img.height:
                    px[q] = outline
    for (a, b) in pts:
        if 0 <= a < img.width and 0 <= b < img.height:
            px[a, b] = col


# ---------------------------------------------------------------------------------------------------------------------
# Filz
# ---------------------------------------------------------------------------------------------------------------------
SEAT_ANGLES = (-60, -30, 0, 30, 60)
F_RIM_IN, F_BET, F_BET_R, F_LINE = 0.885, 0.70, 0.078, 0.60


# Dealer-Seite als flacher Bogen (v2.07, Wunsch des Besitzers: „auf der Seite des Dealers runder, bei einem echten
# Tisch fällt es nach innen, Richtung Spieler“): Kreisbogen durch die Ecken, in der Mitte BJ_BULGE · R zu den Spielern
# hin. Dieselbe Form baut BlackjackTable (DEALER_BULGE) aus Holz-Segmenten.
BJ_BULGE = 0.12
BJ_RHO = (1 + BJ_BULGE * BJ_BULGE) / (2 * BJ_BULGE)


def bj_edge(u):
    """Dealer-Kante (in Radius-Anteilen) bei Querlage u (-1 bis 1)."""
    u = max(-1.0, min(1.0, u))
    return BJ_BULGE - BJ_RHO + math.sqrt(BJ_RHO * BJ_RHO - u * u)


def felt_blackjack(w=96, FELT=FELT):
    h = w // 2
    R = w / 2
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    for y in range(h):
        for x in range(w):
            q = math.hypot(x + 0.5 - R, y + 0.5) / R
            if q > 0.99 or (y + 0.5) / R < bj_edge((x + 0.5 - R) / R):
                continue
            px[x, y] = FELT[3] if q < 0.42 else FELT[2] if q < 0.74 else FELT[1]
    for y in range(h):
        for x in range(w):
            q = math.hypot(x + 0.5 - R, y + 0.5) / R
            if abs(q - F_LINE) < 0.6 / R:
                px[x, y] = GOLD[3]
            elif abs(q - (F_RIM_IN - 0.03)) < 0.6 / R:
                px[x, y] = GOLD[1]
    for a in SEAT_ANGLES:
        t = math.radians(a)
        bx, by = R + math.sin(t) * F_BET * R, math.cos(t) * F_BET * R
        br = F_BET_R * R + 0.4
        for y in range(h):
            for x in range(w):
                d = math.hypot(x + 0.5 - bx, y + 0.5 - by)
                if d <= br - 1:
                    px[x, y] = FELT[4]
                elif d <= br:
                    px[x, y] = GOLD[3]
    # feine Goldlinie entlang der gebogenen Bankkante, in der Mitte ein kleines Gold-Karo
    for x in range(w):
        e = bj_edge((x + 0.5 - R) / R) * R
        yy = int(e + 1.2)
        if 0 <= yy < h and px[x, yy][3]:
            px[x, yy] = GOLD[1]
    e0 = int(BJ_BULGE * R)
    for dx, dy in ((0, 3), (-1, 4), (0, 4), (1, 4), (0, 5)):
        px[int(R) + dx, e0 + dy] = GOLD[3]
    return img


def felt_poker(w=96, FELT=FELT, label="POKER"):
    h = w // 2
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    a, b = w / 2, h / 2
    ratio = a / b
    for y in range(h):
        for x in range(w):
            dx, dy = abs(x + 0.5 - a) / b, abs(y + 0.5 - b) / b
            straight = ratio - 1
            q = dy if dx <= straight else math.hypot(dx - straight, dy)
            if q > 0.99:
                continue
            px[x, y] = FELT[3] if q < 0.45 else FELT[2] if q < 0.78 else FELT[1]
    # Goldlinie um die Gemeinschaftskarten (abgerundetes Rechteck)
    x0, x1, y0, y1 = int(w * 0.2), int(w * 0.8), int(h * 0.3), int(h * 0.7)
    for x in range(x0 + 2, x1 - 1):
        px[x, y0] = GOLD[3]
        px[x, y1] = GOLD[3]
    for y in range(y0 + 2, y1 - 1):
        px[x0, y] = GOLD[3]
        px[x1, y] = GOLD[3]
    for (cx, cy) in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
        px[cx, cy] = GOLD[3]
    draw_text(img, (w - text_width(label)) // 2, y0 - 7, label, GOLD[3])
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Materialien 16 x 16 (harte Stufen, Licht oben)
# ---------------------------------------------------------------------------------------------------------------------

def _mat(fn):
    img = Image.new("RGBA", (16, 16), T)
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = fn(x, y)
    return img


def mat_leather():
    def f(x, y):
        if y == 0:
            return LEATHER[3]
        if y == 3 and x % 3 != 2:
            return GOLD[1]
        return LEATHER[2] if y < 8 else LEATHER[1] if y < 14 else LEATHER[0]
    return _mat(f)


def mat_cream():
    def f(x, y):
        if y == 0:
            return GOLD[4]
        if y == 1:
            return GOLD[2]
        if y >= 14:
            return WALNUT[1] if y == 15 else GOLD[1]
        if x % 8 == 0:
            return CREAM[1]
        return CREAM[3] if y < 6 else CREAM[2]
    return _mat(f)


def mat_seat():
    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return CREAM[1]
        if (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11), (8, 8)):
            return CREAM[0]
        return CREAM[3] if y < 5 else CREAM[2]
    return _mat(f)


def mat_gold():
    def f(x, y):
        if (x, y) in ((3, 2), (4, 2), (11, 9)):
            return GOLD[4]
        return GOLD[3] if y < 5 else GOLD[2] if y < 12 else GOLD[1]
    return _mat(f)


def mat_dark():
    def f(x, y):
        if x == y and x < 5:
            return LEATHER[3]
        return LEATHER[2] if y < 4 else LEATHER[1]
    return _mat(f)


def mat_walnut():
    def f(x, y):
        if y % 4 == 3:
            return WALNUT[0]
        return WALNUT[2] if (x + y // 4 * 5) % 9 else WALNUT[3]
    return _mat(f)


# Roulette (v2.07, Wunsch des Besitzers: „die gelben Streifen auf der schwarzen Ablage weg oder sauberer“, „die
# Holztextur gefällt mir nicht“). Die Würfel-Materialien werden über ganze Tischkanten gezogen (eine 16-px-Textur auf
# 4 Blöcke Länge): deshalb nur Muster, die waagrecht gestreckt gut aussehen – durchgehende Linien, keine Fugen und
# keine Stiche (die Goldnaht im Leder wurde gestreckt zu Straßen-Markierungen).
RV_WOOD = [rgb(c) for c in ("2A1609", "41230F", "5A3317", "714222", "8C5730", "A8703F")]
RV_RAIL = [rgb(c) for c in ("0C0B10", "15131B", "1E1B26", "2A2634", "3C3748")]


def mat_rv_wood():
    """Lackiertes Nussholz: Licht oben (Lackkante), zwei dunkle Maserlinien in sanften Wellen, ein heller Streifen,
    unten dunkler (Schatten kühler, Licht wärmer)."""
    def f(x, y):
        if y == 0:
            return RV_WOOD[5]
        if y == 15:
            return RV_WOOD[0]
        g1 = 5 + round(1.4 * math.sin(2 * math.pi * x / 16))
        g2 = 10 + round(1.2 * math.sin(2 * math.pi * (x + 6) / 16))
        if y in (g1, g2):
            return RV_WOOD[1]
        if y == g1 + 1 and x % 5 != 0:
            return RV_WOOD[2]
        if y == 2 and 3 <= x <= 12:
            return RV_WOOD[5]            # Lackglanz
        if y < 4:
            return RV_WOOD[4]
        if y < 12:
            return RV_WOOD[3]
        return RV_WOOD[2]
    return _mat(f)


def mat_rv_rail():
    """Schwarzes Polsterleder ohne Naht: helle Oberkante, ein ruhiger Glanzstreifen, nach unten dunkler."""
    def f(x, y):
        if y == 0:
            return RV_RAIL[4]
        if y == 2:
            return RV_RAIL[3]
        if y < 6:
            return RV_RAIL[2]
        if y < 13:
            return RV_RAIL[1]
        return RV_RAIL[0]
    return _mat(f)


def mat_ramp(r, tube=False):
    def f(x, y):
        if tube:
            return r[3] if x in (7, 8) else r[2] if 4 <= x <= 11 else r[1]
        return r[3] if y == 0 else r[2] if y < 6 else r[1]
    return _mat(f)


MATERIALS = {
    "hd_leather": mat_leather, "hd_cream": mat_cream, "hd_seat": mat_seat, "hd_gold": mat_gold,
    "hd_dark": mat_dark, "hd_walnut": mat_walnut,
    "hd_velvet": lambda: mat_ramp(VELVET), "hd_purple": lambda: mat_ramp(PURPLE),
    "hd_neon_pink": lambda: mat_ramp(NEON_P, True), "hd_neon_cyan": lambda: mat_ramp(NEON_C, True),
    "hd_edge": lambda: _mat(lambda x, y: PAPER[2]),
    "hd_ocean": lambda: mat_ramp(OCEAN),
    "rv_wood": mat_rv_wood, "rv_rail": mat_rv_rail,
    # Leuchtleisten der Slot-Automaten im eingeschalteten Zustand (beim Drehen): hellere Rampe mit fast weißem Kern.
    # Früher leuchteten sie über den Glow-Umriss, der im echten Client die Textur durch eine flache Farbe ersetzte.
    "hd_gold_lit": lambda: mat_ramp([rgb(c) for c in ("D9A11A", "F2C23A", "FFE48A", "FFF8DC")], True),
    "hd_neon_pink_lit": lambda: mat_ramp([rgb(c) for c in ("D0207A", "FF6FB8", "FFC0E0", "FFF2F8")], True),
    "hd_neon_cyan_lit": lambda: mat_ramp([rgb(c) for c in ("12A8C0", "52EEF6", "C2FAFC", "F2FFFF")], True),
}

# ---------------------------------------------------------------------------------------------------------------------
# Karten (32 x 32 Pixel-Karten aus build_casino) und Chips (16 x 16)
# ---------------------------------------------------------------------------------------------------------------------
RANKS = ["A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"]
# Karte im 32er-Bild: x 4..27, y 1..30 -> uv in 16tel
CARD_UV = [2.0, 0.5, 14.0, 15.5]


def card_model(front, back):
    u0, v0, u1, v1 = CARD_UV
    return {
        "textures": {"front": front, "back": back, "edge": "kuby:block/casino/hd_edge", "particle": front},
        "elements": [{
            "from": [3, 7.85, 1.75], "to": [13, 8.15, 14.25],
            "faces": {
                "up": {"texture": "#front", "uv": [u0, v0, u1, v1]},
                "down": {"texture": "#back", "uv": [u0, v1, u1, v0]},
                "north": {"texture": "#edge", "uv": [0, 0, 16, 1]},
                "south": {"texture": "#edge", "uv": [0, 0, 16, 1]},
                "east": {"texture": "#edge", "uv": [0, 0, 16, 1]},
                "west": {"texture": "#edge", "uv": [0, 0, 16, 1]},
            }}],
    }


CHIP_NAMES = ["white", "red", "blue", "green", "black", "purple", "gold", "pink"]


def chip_side(name):
    """Kante: Spalte 0 = Farbtupfer, Spalte 1 = Grundfarbe; oben Licht, unten Schatten (je 16 Zeilen)."""
    ramp, spot, _ = bc.CHIP_RAMPS[name]
    r = [rgb(c) for c in ramp]
    sp = rgb(spot)

    def f(x, y):
        # dünne Kante: nur oben eine Lichtkante und unten eine Schattenkante, sonst Vollfarbe (sonst wirkt der
        # Schatten im Stapel wie eine Lücke zwischen den Chips)
        if x % 2 == 0:
            return mix(sp, r[3], 0.35) if y < 3 else sp if y < 14 else mix(sp, r[1], 0.5)
        return r[3] if y < 3 else r[2] if y < 14 else mix(r[2], r[1], 0.5)
    return _mat(f)


# Chip-Maße in Modell-Einheiten: Deckel 12,8 breit (0,8 je Textur-Pixel), Höhe 1,28 (Stapelabstand 0,08 x Skalierung)
CHIP_R = 6.4
# Deckel knapp innerhalb der Wand (Innenkreis des 16-Ecks 6,28): kein dunkler Rand ragt im Stapel heraus
CHIP_TOP = 6.27
CHIP_Y0, CHIP_Y1 = 7.36, 8.64


def chip_model(name):
    """Runder Chip: 16-eckige Seitenwand aus 8 schmalen Stäben (je zwei Stirnseiten = zwei Kanten des 16-Ecks, keine
    Zacken), Deckel oben und unten mit der runden Chip-Textur. Abwechselnd Farbtupfer und Grundfarbe an der Kante."""
    tex = "kuby:block/casino/chip_" + name
    side = "kuby:block/casino/chipside_" + name
    a = CHIP_R * math.cos(math.radians(11.25))
    b = CHIP_R * math.sin(math.radians(11.25))
    els = []
    # (Achse, Drehung, gerade = Tupfer): Richtungen 0, ±22,5, ±45 (Stab entlang x) und 90, 90 ± 22,5 (Stab entlang z)
    for axis, ang, spot in (("x", 0, True), ("x", 22.5, False), ("x", -22.5, False), ("x", 45, True), ("x", -45, True),
                            ("z", 0, True), ("z", 22.5, False), ("z", -22.5, False)):
        col = 0 if spot else 1
        if axis == "x":
            fr, to, faces = [8 - a, CHIP_Y0, 8 - b], [8 + a, CHIP_Y1, 8 + b], ("east", "west")
        else:
            fr, to, faces = [8 - b, CHIP_Y0, 8 - a], [8 + b, CHIP_Y1, 8 + a], ("north", "south")
        el = {"from": [round(v, 4) for v in fr], "to": [round(v, 4) for v in to],
              "faces": {fc: {"texture": "#side", "uv": [col, 0, col + 1, 16]} for fc in faces}}
        if ang:
            el["rotation"] = {"angle": ang, "axis": "y", "origin": [8, 8, 8]}
        els.append(el)
    lo, hi = 8 - CHIP_TOP, 8 + CHIP_TOP
    els.append({"from": [lo, CHIP_Y1, lo], "to": [hi, CHIP_Y1, hi], "faces": {"up": {"texture": "#top", "uv": [0, 0, 16, 16]}}})
    els.append({"from": [lo, CHIP_Y0, lo], "to": [hi, CHIP_Y0, hi], "faces": {"down": {"texture": "#top", "uv": [0, 16, 16, 0]}}})
    return {"textures": {"top": tex, "side": side, "particle": tex}, "elements": els}


# ---------------------------------------------------------------------------------------------------------------------
# Knöpfe (48 x 16) und Bedienknöpfe (16 x 16)
# ---------------------------------------------------------------------------------------------------------------------
GREEN = ["0E5A2C", "16803C", "22A04E", "4CC872"]
RED = ["7A1418", "A81E22", "CC2E2C", "E85A4C"]
GOLDB = ["8A5A08", "B57E0C", "D9A11A", "F2C23A"]
BLUE = ["123E86", "1A56B4", "2872DA", "56A0F0"]
VIOLET = ["4A0C86", "6A16B4", "8A28DA", "AA52F0"]
GREY = ["3A3640", "524C5A", "6A6474", "8A8494"]
DUSK = ["2A2238", "3E3354", "5A4C78", "8A7AB0"]

BUTTONS = {"hit": ("KARTE", GREEN), "stand": ("HALTEN", RED), "double": ("DOPPELN", GOLDB), "split": ("TEILEN", BLUE)}
PK_BUTTONS = {
    "fold": ("PASSEN", RED), "check": ("CHECK", GREEN), "call": ("MITGEHEN", GREEN), "raise": ("ERHÖHEN", GOLDB),
    "allin": ("ALL-IN", VIOLET), "ok": ("SETZEN", GREEN), "half": ("1/2 POT", BLUE), "pot": ("POT", BLUE),
    "minus": ("-", DUSK), "plus": ("+", DUSK), "back": ("ZURÜCK", GREY), "show": ("ZEIGEN", BLUE), "sitout": ("PAUSE", GREY),
}


def action_button(name):
    label, ramp = BUTTONS[name] if name in BUTTONS else PK_BUTTONS[name]
    r = [rgb(c) for c in ramp]
    lw = text_width(label)
    # ungerade Textbreite: 49 px breit, damit links und rechts gleich viel Rand bleibt (Fläche im Modell bleibt gleich)
    w, h = (48 if lw % 2 == 0 else 49), 16
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            if (x in (1, w - 2)) and (y in (1, h - 2)):
                continue
            px[x, y] = r[3] if y == 2 else r[2] if y < h - 4 else r[1]
    img = _outline(img, mix(r[0], INK, 0.6))
    draw_text(img, (w - lw) // 2, 5, label, PAPER[2], outline=mix(r[0], INK, 0.3))
    return img


def control_button(kind):
    """Runder Bedienknopf 16 px: gleicher Kreis wie die Chips (bc.CIRCLE16), Licht oben links, Symbol mittig."""
    ramp = {"repeat": DUSK, "clear": RED, "chips": GOLDB, "jewels": BLUE}[kind]
    r = [rgb(c) for c in ramp]
    p = bc.Pix(16)
    p.disk(8, 8, 8.5, r[2])
    p.sector(8, 8, 5.6, 8.5, 190, 260, r[3])
    p.sector(8, 8, 5.6, 8.5, 10, 170, r[1])
    w = PAPER[2]
    if kind == "repeat":
        p.ring(8, 8, 2.2, 3.4, w)
        p.rect(8, 3, 10, 5, r[2])
        p.poly([(9, 2.5), (11.5, 4.5), (9, 6.5)], w)
    elif kind == "clear":
        for k in range(5, 11):
            p.put(k, k, w)
            p.put(15 - k, k, w)
    elif kind == "chips":
        p.ring(8, 8, 1.4, 3.2, w)
    else:
        p.poly([(5, 6.5), (6.5, 4.5), (9.5, 4.5), (11, 6.5), (8, 11.5)], w)
    return bc.round16(p.image(), mix(r[0], INK, 0.6))


def _outline(img, col):
    out = img.copy()
    px, po = img.load(), out.load()
    for y in range(img.height):
        for x in range(img.width):
            if px[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                a, b = x + dx, y + dy
                if 0 <= a < img.width and 0 <= b < img.height and px[a, b][3]:
                    po[x, y] = col
                    break
    return out


def dealer_button():
    p = bc.Pix(16)
    p.disk(8, 8, 6.6, PAPER[2])
    p.ring(8, 8, 5.6, 6.7, GOLD[3])
    img = p.image(outline=GOLD[0])
    draw_text(img, 7, 6, "D", INK)
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Slot-Automaten: Schild oben (64 x 24), Pultfront (32 x 16)
# ---------------------------------------------------------------------------------------------------------------------

def slot_top(style):
    w, h = 64, 24
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    if style == "gold":
        for y in range(h):
            for x in range(w):
                edge = x in (0, w - 1) or y in (0, h - 1)
                frame = x in (1, 2, w - 2, w - 3) or y in (1, 2, h - 2, h - 3)
                px[x, y] = GOLD[1] if edge else GOLD[3] if frame else (VELVET[2] if 6 <= y <= h - 7 else VELVET[1])
        # Lauflicht-Birnen im Rahmen, gleicher Abstand, eine aus
        k = 0
        for x in range(4, w - 3, 4):
            for y in (1, h - 2):
                px[x, y] = GOLD[4] if k != 5 else GOLD[0]
                k += 1
        label = "GOLDRAUSCH"
        draw_text(img, (w - text_width(label)) // 2, 10, label, GOLD[4], outline=VELVET[0])
        return img
    if style == "ozean":
        # Tiefsee: Goldrahmen mit Nieten an den Ecken, innen Wasser von hell (oben) nach tief (unten), zwei Wellen,
        # aufsteigende Luftblasen (eine Spalte bewusst ohne), Schrift hell-türkis mit dunkler Kontur
        for y in range(h):
            for x in range(w):
                edge = x in (0, w - 1) or y in (0, h - 1)
                frame = x in (1, w - 2) or y in (1, h - 2)
                if edge:
                    px[x, y] = GOLD[1]
                elif frame:
                    px[x, y] = GOLD[3] if y < h // 2 else GOLD[2]
                else:
                    px[x, y] = OCEAN[3] if y < 6 else OCEAN[2] if y < 14 else OCEAN[1]
        for cx, cy in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            px[cx, cy] = GOLD[4] if cy == 1 else GOLD[1]
        # Wellen: oben hell (Wasseroberfläche), unten dunkel (Strömung), gleicher Takt von 6 px, gegeneinander versetzt
        for x in range(2, w - 2):
            px[x, 4 if x % 6 < 3 else 5] = NEON_C[1] if x % 6 in (2, 5) else NEON_C[2]
            px[x, 19 if (x + 3) % 6 < 3 else 20] = OCEAN[3]
        for bx, by in ((5, 15), (6, 12), (5, 9), (57, 16), (58, 13)):
            px[bx, by] = NEON_C[3]
        label = "TIEFSEE"
        draw_text(img, (w - text_width(label)) // 2, 9, label, NEON_C[3], outline=OCEAN[0])
        return img
    for y in range(h):
        for x in range(w):
            frame = x in (2, w - 3) or y in (2, h - 3)
            inside = 2 <= x <= w - 3 and 2 <= y <= h - 3
            px[x, y] = NEON_C[2] if frame and inside else PURPLE[1] if inside else PURPLE[0]
    draw_text(img, 14, 10, "NEON", NEON_P[2], outline=NEON_P[0])
    # große 7 (doppelt gesetzt)
    for k, b in enumerate(FONT["7"]):
        if b == "1":
            x, y = 38 + (k % 3) * 2, 7 + (k // 3) * 2
            for dx in (0, 1):
                for dy in (0, 1):
                    px[x + dx, y + dy] = NEON_C[3]
    return img


def slot_front(style):
    """Pultfront 32 x 16: ruhige Fläche, Rahmen, drei gleiche Rauten (die mittlere größer)."""
    w, h = 32, 16
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    base, hi, frame, gem = ((LEATHER[1], LEATHER[2], GOLD[2], GOLD[3]) if style == "gold"
                            else (OCEAN[1], OCEAN[2], GOLD[2], NEON_C[2]) if style == "ozean"
                            else (PURPLE[1], PURPLE[2], NEON_C[2], NEON_P[2]))
    for y in range(h):
        for x in range(w):
            px[x, y] = frame if x in (0, w - 1) or y in (0, h - 1) else hi if y == 1 else base
    for cx, r in ((8, 2), (16, 3), (24, 2)):
        for y in range(h):
            for x in range(w):
                if abs(x - cx) + abs(y - 8) <= r:
                    px[x, y] = gem
        px[cx, 8 - r] = GOLD[4] if style == "gold" else NEON_C[3] if style == "ozean" else NEON_P[3]
    return img


# ---------------------------------------------------------------------------------------------------------------------
# Schreiben
# ---------------------------------------------------------------------------------------------------------------------

def flat_model(tex, x0, z0, x1, z1):
    return {"textures": {"top": tex, "particle": tex},
            "elements": [{"from": [x0, 8, z0], "to": [x1, 8.02, z1], "faces": {"up": {"texture": "#top", "uv": [0, 0, 16, 16]}}}]}


def cube_model(tex):
    faces = {d: {"texture": "#all", "uv": [0, 0, 16, 16]} for d in ("north", "south", "east", "west", "up", "down")}
    return {"textures": {"all": tex, "particle": tex}, "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]}


def write(out):
    a = os.path.join(out, "assets/kuby")
    tex = os.path.join(a, "textures/block/casino")
    cards = os.path.join(tex, "card")
    models = os.path.join(a, "models/item/casino")
    items = os.path.join(a, "items/casino")
    for d in (tex, cards, models, items):
        os.makedirs(d, exist_ok=True)
    old = os.path.join(a, "textures/item/casino/hd")
    if os.path.isdir(old):
        shutil.rmtree(old)

    def item(name, model):
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump(model, fh)
        with open(os.path.join(items, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/" + name}}, fh)

    B = "kuby:block/casino/"
    felt_blackjack().save(os.path.join(tex, "bj_felt.png"))
    item("bj_felt", flat_model(B + "bj_felt", 0, 8, 16, 16))
    felt_poker().save(os.path.join(tex, "pk_felt.png"))
    item("pk_felt", flat_model(B + "pk_felt", 0, 4, 16, 12))
    felt_blackjack(FELT=FELT_RED).save(os.path.join(tex, "bj_felt_red.png"))
    item("bj_felt_red", flat_model(B + "bj_felt_red", 0, 8, 16, 16))
    felt_poker(FELT=FELT_RED, label="HIGH ROLLER").save(os.path.join(tex, "pk_felt_red.png"))
    item("pk_felt_red", flat_model(B + "pk_felt_red", 0, 4, 16, 12))
    felt_blackjack(FELT=FELT_VIOLET).save(os.path.join(tex, "bj_felt_violet.png"))
    item("bj_felt_violet", flat_model(B + "bj_felt_violet", 0, 8, 16, 16))
    felt_poker(FELT=FELT_VIOLET, label="ELITE").save(os.path.join(tex, "pk_felt_violet.png"))
    item("pk_felt_violet", flat_model(B + "pk_felt_violet", 0, 4, 16, 12))
    for name, fn in MATERIALS.items():
        fn().save(os.path.join(tex, name + ".png"))
        item("m_" + name, cube_model(B + name))
    bc.card_back().save(os.path.join(cards, "back.png"))
    for s in "hdsc":
        for r in RANKS:
            cid = r.lower() + s
            bc.card(r, s).save(os.path.join(cards, cid + ".png"))
            item("c3_" + cid, card_model(B + "card/" + cid, B + "card/back"))
    item("c3_back", card_model(B + "card/back", B + "card/back"))
    for name in CHIP_NAMES:
        bc.chip(name, edge="rim").save(os.path.join(tex, "chip_" + name + ".png"))
        chip_side(name).save(os.path.join(tex, "chipside_" + name + ".png"))
        item("c3chip_" + name, chip_model(name))
    for name in BUTTONS:
        action_button(name).save(os.path.join(tex, "btn_" + name + ".png"))
        item("bj_btn_" + name, flat_model(B + "btn_" + name, 0, 16 / 3, 16, 32 / 3))
    for name in PK_BUTTONS:
        action_button(name).save(os.path.join(tex, "pk_" + name + ".png"))
        item("pk_btn_" + name, flat_model(B + "pk_" + name, 0, 16 / 3, 16, 32 / 3))
    for kind in ("repeat", "clear", "chips", "jewels"):
        control_button(kind).save(os.path.join(tex, "ctl_" + kind + ".png"))
        item("bj_ctl_" + kind, flat_model(B + "ctl_" + kind, 0, 0, 16, 16))
    dealer_button().save(os.path.join(tex, "dealer_btn.png"))
    item("pk_dealer", {"textures": {"top": B + "dealer_btn", "side": B + "hd_edge", "particle": B + "dealer_btn"},
                       "elements": [{"from": [2, 7.5, 2], "to": [14, 8.5, 14],
                                     "faces": {"up": {"texture": "#top", "uv": [0, 0, 16, 16]},
                                               "north": {"texture": "#side", "uv": [0, 0, 16, 1]},
                                               "south": {"texture": "#side", "uv": [0, 0, 16, 1]},
                                               "east": {"texture": "#side", "uv": [0, 0, 16, 1]},
                                               "west": {"texture": "#side", "uv": [0, 0, 16, 1]}}}]})
    reel_plate().save(os.path.join(tex, "reel_plate.png"))
    icons = os.path.join(a, "textures/item/casino")
    for sym in SLOT_SYMS:
        for blur in (False, True):
            name = "slot_" + sym + ("_blur" if blur else "")
            src = os.path.join(icons, name + ".png")
            if os.path.exists(src):
                shutil.copyfile(src, os.path.join(tex, "sym_" + sym + ("_blur" if blur else "") + ".png"))
            item("sr_" + sym + ("_blur" if blur else ""), reel_model(sym, blur))
    jukebox_front().save(os.path.join(tex, "jukebox_front.png"))
    jukebox_side().save(os.path.join(tex, "jukebox_side.png"))
    item("jukebox", jukebox_model())
    for style in ("gold", "neon", "ozean"):
        slot_top(style).save(os.path.join(tex, "sl_" + style + "_top.png"))
        item("sl_" + style + "_top", flat_model(B + "sl_" + style + "_top", 0, 5, 16, 11))
        slot_front(style).save(os.path.join(tex, "sl_" + style + "_front.png"))
        item("sl_" + style + "_front", flat_model(B + "sl_" + style + "_front", 0, 4, 16, 12))


# ---------------------------------------------------------------------------------------------------------------------
# Walzen der Automaten (v1.73): je Symbol eine Kachel der Trommel, außen bei Radius 8 (0,5 Block), 1/10 des Umfangs
# hoch. Die Kachel liegt im Modell um die Mitte (8, 8, 8) herum versetzt: dreht man die Anzeige um ihre eigene Mitte,
# läuft die Kachel auf dem Kreis um die Walzenachse (der Client dreht sauber, statt auf der Sehne zu schieben).
# ---------------------------------------------------------------------------------------------------------------------
SLOT_SYMS = ["cherry", "lemon", "banana", "bell", "star", "clover", "chip", "coin", "diamond", "seven"]
REEL_R = 8.0
REEL_HALF = REEL_R * math.tan(math.radians(18))  # halbe Kantenlänge des Zehnecks (Innenkreis = Radius)


def reel_plate():
    def f(x, y):
        if y in (0, 15):
            return CREAM[1]
        return CREAM[3] if 3 <= y <= 12 else CREAM[2]
    return _mat(f)


def reel_model(sym, blur):
    plate = "kuby:block/casino/reel_plate"
    tex = "kuby:block/casino/sym_" + sym + ("_blur" if blur else "")
    z1 = 8 + REEL_R
    h = REEL_HALF
    edge = {"texture": "#plate", "uv": [0, 0, 16, 1]}
    return {"textures": {"plate": plate, "sym": tex, "particle": plate},
            "elements": [
                {"from": [0, 8 - h, z1 - 0.6], "to": [16, 8 + h, z1],
                 "faces": {"south": {"texture": "#plate", "uv": [0, 0, 16, 16]}, "east": edge, "west": edge}},
                {"from": [8 - 2.2, 8 - 2.2, z1 + 0.03], "to": [8 + 2.2, 8 + 2.2, z1 + 0.03],
                 "faces": {"south": {"texture": "#sym", "uv": [0, 0, 16, 16]}}},
            ]}


# ---------------------------------------------------------------------------------------------------------------------
# Jukebox (v1.75) im Stil der alten Wurlitzer aus amerikanischen Bowlingbahnen: runder Bogen mit leuchtenden
# Farbröhren, Fenster mit Schallplatten, Chromgitter, Holzsockel. Front 32 x 64 (2 Pixel je Modell-Einheit).
# ---------------------------------------------------------------------------------------------------------------------
JB_WOOD = [rgb(c) for c in ("3A1E0C", "5A3016", "7A4420", "9A5A2C")]
JB_TUBE = [rgb(c) for c in ("FF4A2A", "FF8A1E", "FFC23A", "FFE680")]
JB_GREEN = [rgb(c) for c in ("1E8A4A", "3AC06A", "8AF0A8")]
JB_CHROME = [rgb(c) for c in ("6A6E78", "9AA0AC", "C8CDD6", "EEF1F6")]


def jukebox_front():
    w, h = 32, 64
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    cx = 15.5
    for y in range(h):
        for x in range(w):
            dx = x - cx
            if y < 16:
                # Bogen: Halbkreis, außen Holz, dann Farbröhren (orange -> gelb), innen dunkles Glas
                r = math.hypot(dx, (y - 16) * 1.0)
                if r > 16:
                    continue
                if r > 14.6:
                    c = JB_WOOD[1]
                elif r > 12.4:
                    c = JB_TUBE[0] if r > 13.5 else JB_TUBE[1]
                elif r > 11.2:
                    c = JB_TUBE[3]
                elif r > 10.2:
                    c = JB_CHROME[2]
                else:
                    c = PURPLE[1] if y < 12 else PURPLE[2]
                px[x, y] = c
                continue
            # Seiten: Holz mit senkrechten Farbröhren
            if x <= 1 or x >= w - 2:
                px[x, y] = JB_WOOD[1] if y < h - 6 else JB_WOOD[0]
                continue
            if x <= 4 or x >= w - 5:
                if y < h - 8:
                    k = (y // 3) % 3
                    px[x, y] = JB_TUBE[1] if (x in (3, w - 4)) else (JB_TUBE[2] if k else JB_TUBE[3])
                else:
                    px[x, y] = JB_WOOD[2]
                continue
            if y < 30:
                # Fenster mit Schallplatten und warmem Licht
                px[x, y] = rgb("2A1830") if y > 17 else JB_CHROME[2]
                continue
            if y < 32:
                px[x, y] = JB_CHROME[3] if y == 30 else JB_CHROME[1]
                continue
            if y < 50:
                # Chromgitter: senkrechte Stäbe, Mitte Lautsprecher-Raute
                px[x, y] = JB_CHROME[2] if (x % 3 == 0) else JB_CHROME[0]
                continue
            if y < 52:
                px[x, y] = JB_TUBE[2]
                continue
            px[x, y] = JB_WOOD[2] if y < h - 4 else JB_WOOD[0]
    # Schallplatten im Fenster (gleiche Abstände, die mittlere größer = bricht das Muster)
    for (pxc, rr) in ((9.5, 3.2), (15.5, 4.2), (21.5, 3.2)):
        for y in range(18, 30):
            for x in range(5, w - 5):
                d = math.hypot(x - pxc, y - 24)
                if d <= rr:
                    px[x, y] = INK if d > 1.1 else JB_TUBE[0]
                    if abs(d - rr * 0.6) < 0.5:
                        px[x, y] = rgb("2A2A34")
    # warmes Licht oben links im Fenster
    for x in range(6, 10):
        px[x, 18] = JB_TUBE[3]
    # Lautsprecher-Raute im Gitter
    for y in range(33, 49):
        for x in range(5, w - 5):
            if abs(x - cx) + abs(y - 41) * 0.8 <= 6.2:
                px[x, y] = JB_GREEN[1] if abs(x - cx) + abs(y - 41) * 0.8 > 5 else (JB_CHROME[3] if y % 2 else JB_CHROME[1])
    # Bedientasten unten
    for i, x in enumerate(range(8, 24, 3)):
        px[x, 51] = JB_CHROME[3]
    img = _outline(img, mix(JB_WOOD[0], INK, 0.5))
    # quadratisches Blatt (64 x 64), die Front liegt in der linken Hälfte
    sheet = Image.new("RGBA", (64, 64), T)
    sheet.alpha_composite(img, (0, 0))
    return sheet


def jukebox_side():
    def f(x, y):
        if y % 8 == 7:
            return JB_WOOD[0]
        return JB_WOOD[2] if x < 3 else JB_WOOD[1]
    return _mat(f)


def jukebox_model():
    tex = "kuby:block/casino/jukebox_front"
    side = "kuby:block/casino/jukebox_side"
    # Körper 16 x 24 (Front unten 0..24), Bogen oben 24..32; die Anzeige dreht um 180°: Front = Nordseite
    return {"textures": {"front": tex, "side": side, "particle": side},
            "elements": [
                {"from": [0, 0, 3], "to": [16, 24, 13],
                 "faces": {"north": {"texture": "#front", "uv": [0, 4, 8, 16]},
                           "south": {"texture": "#side", "uv": [0, 0, 16, 16]},
                           "east": {"texture": "#side", "uv": [0, 0, 10, 16]},
                           "west": {"texture": "#side", "uv": [0, 0, 10, 16]},
                           "up": {"texture": "#side", "uv": [0, 0, 16, 10]},
                           "down": {"texture": "#side", "uv": [0, 0, 16, 10]}}},
                {"from": [0, 24, 4], "to": [16, 32, 12],
                 "faces": {"north": {"texture": "#front", "uv": [0, 0, 8, 4]},
                           "south": {"texture": "#side", "uv": [0, 0, 16, 8]}}},
            ]}


def preview(path):
    z = 4
    sheet = Image.new("RGBA", (1200, 760), rgb("1C1A20"))

    def put(img, x, y, s=z):
        sheet.alpha_composite(img.resize((img.width * s, img.height * s), Image.NEAREST), (x, y))

    put(felt_blackjack(), 10, 10)
    put(felt_poker(), 410, 10)
    for i, (n, fn) in enumerate(MATERIALS.items()):
        put(fn(), 10 + i * 70, 220)
    for i, s in enumerate("hdsc"):
        for j, r in enumerate(["A", "7", "10", "K"]):
            put(bc.card(r, s), 10 + (i * 4 + j) * 70, 300, 2)
    put(bc.card_back(), 10 + 16 * 70, 300, 2)
    for i, n in enumerate(CHIP_NAMES):
        put(bc.chip(n), 10 + i * 70, 380)
    for i, n in enumerate(list(BUTTONS) + list(PK_BUTTONS)):
        put(action_button(n), 10 + (i % 6) * 200, 460 + (i // 6) * 72)
    for i, k in enumerate(("repeat", "clear", "chips", "jewels")):
        put(control_button(k), 810 + i * 70, 380)
    put(dealer_button(), 1100, 380)
    put(slot_top("gold"), 10, 680 - 30, 4)
    put(slot_top("neon"), 300, 650, 4)
    put(slot_front("gold"), 600, 650, 4)
    put(slot_front("neon"), 750, 650, 4)
    sheet.save(path)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(sys.argv[2])
    else:
        write(sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "resourcepack/KubyUI"))
