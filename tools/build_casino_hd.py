"""Casino in hoher Auflösung (v1.68): Blackjack- und Poker-Tisch, 3D-Spielkarten, 3D-Chips.

Clean-Look: tiefer Smaragd-Filz mit wenigen goldenen Linien und viel freier Fläche, schwarze Lederkante, Sockel in
Creme-Lack mit Goldband, Barhocker mit Creme-Leder. Texturen 64 px je Material (statt 16), der Filz 512 x 256 px als
eine Fläche mit Aufdruck. Karten als echte 3D-Körper (Vorderseite oben, Rückseite unten, weiße Kante), damit sie
beim Austeilen umgedreht werden können. Chips als Zylinder aus vier gedrehten Quadern mit runder Oberseite.

Farben aus Rampen der Icon-Datenbank (tools/icon_db/refpal.py): Gold, Grün (Smaragd), Grau, Braun.

Aufruf: python3 tools/build_casino_hd.py [resourcepack/KubyUI]   (schreibt Texturen, Modelle, Item-Definitionen)
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT_SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
FONT_SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

T = (0, 0, 0, 0)


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, f):
    f = max(0.0, min(1.0, f))
    return tuple(round(x + (y - x) * f) for x, y in zip(a[:3], b[:3])) + (a[3] if len(a) > 3 else 255,)


# Rampen (dunkel -> hell), aus refpal / STYLE.md abgeleitet
GOLD = [rgb(c) for c in ("8A5A08", "B57E0C", "D9A11A", "F2C23A", "FAE07A", "FBEFB0")]
FELT = [rgb(c) for c in ("0E3220", "143F28", "1A4D31", "215C3A", "2A6B44")]
LEATHER = [rgb(c) for c in ("0B0A0D", "16141A", "211E26", "2E2A34", "48434F")]
CREAM = [rgb(c) for c in ("B9AE98", "D2C8B2", "E4DCC8", "F0EADB", "F8F4EA")]
WALNUT = [rgb(c) for c in ("2A170C", "3A2212", "4A2E18", "5E3C20", "74502C")]
INK = rgb("0C0907")


def font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


# =====================================================================================================================
# Filz (Draufsicht): x = quer (-R..R), y = von der Bank-Kante (0) zu den Spielern (R)
# =====================================================================================================================

SEAT_ANGLES = (-60, -30, 0, 30, 60)
# Lage auf dem Filz als Anteil am Radius (gleiche Werte wie BlackjackTable.java)
F_RIM_IN = 0.885
F_BET = 0.70
F_BET_R = 0.078
F_CARDS = 0.50
F_LINE = 0.60


def felt_blackjack(w=512):
    h = w // 2
    R = w / 2
    S = 4  # Überabtastung für saubere Kanten
    big = Image.new("RGBA", (w * S, h * S), T)
    d = ImageDraw.Draw(big)
    cx, cy, r = w * S / 2, 0, R * S
    # Filz mit sanftem Licht in der Mitte (Tischlampe), zum Rand hin dunkler
    base = Image.new("RGBA", (w, h), T)
    px = base.load()
    for y in range(h):
        for x in range(w):
            dx, dy = (x + 0.5 - R) / R, (y + 0.5) / R
            q = math.hypot(dx, dy)
            if q > 0.995:
                continue
            light = 1.0 - min(1.0, q * 1.05)
            f = 0.15 + 0.85 * light
            idx = f * (len(FELT) - 1)
            i0 = int(idx)
            c = mix(FELT[i0], FELT[min(i0 + 1, len(FELT) - 1)], idx - i0)
            px[x, y] = c
    big = base.resize((w * S, h * S), Image.NEAREST)
    d = ImageDraw.Draw(big)
    gold = GOLD[3]
    gold_dim = GOLD[2]

    def arc(fr, col, width):
        rr = fr * r
        d.arc((cx - rr, cy - rr, cx + rr, cy + rr), 0, 180, fill=col, width=int(width * S))

    # zwei feine Goldlinien (außen und zwischen Karten und Einsatzfeldern)
    arc(F_RIM_IN - 0.02, gold_dim, 1.6)
    arc(F_LINE, gold, 1.4)
    # Einsatzkreise: feiner Goldring, innen eine Spur heller
    for a in SEAT_ANGLES:
        t = math.radians(a)
        bx, by = cx + math.sin(t) * F_BET * r, cy + math.cos(t) * F_BET * r
        br = F_BET_R * r
        d.ellipse((bx - br, by - br, bx + br, by + br), fill=mix(FELT[3], FELT[4], 0.5), outline=gold, width=int(1.6 * S))
        ir = br * 0.62
        d.ellipse((bx - ir, by - ir, bx + ir, by + ir), outline=mix(gold, FELT[3], 0.55), width=int(1.0 * S))
    # Kartenfelder: nur feine helle Ecken (viel freie Fläche)
    for a in SEAT_ANGLES:
        t = math.radians(a)
        mx, my = cx + math.sin(t) * F_CARDS * r, cy + math.cos(t) * F_CARDS * r
        _corners(d, mx, my, -t, 0.135 * r, 0.085 * r, mix(FELT[4], CREAM[2], 0.35), S)
    big = big.resize((w, h), Image.LANCZOS)
    # Aufdruck entlang des Bogens (auf dem verkleinerten Bild, damit die Schrift scharf bleibt)
    _arc_text(big, "BLACKJACK ZAHLT 3 ZU 2", R, 0, 0.385 * R, font(FONT_SERIF, max(10, int(w * 0.034))), GOLD[3], GOLD[0])
    _arc_text(big, "DIE BANK ZIEHT BIS 16 UND STEHT AB 17", R, 0, 0.31 * R, font(FONT_SANS, max(7, int(w * 0.019))),
              mix(CREAM[2], FELT[3], 0.25), None)
    # Bank-Logo an der geraden Kante: kleine Raute aus den vier Farben
    _suit_row(big, R, 0.085 * R, w)
    return big


def _corners(d, mx, my, rot, hw, hh, col, S):
    L = min(hw, hh) * 0.45
    pts = []
    for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
        x0, y0 = sx * hw, sy * hh
        seg = [((x0, y0), (x0 - sx * L, y0)), ((x0, y0), (x0, y0 - sy * L))]
        for (a, b) in seg:
            pts.append((a, b))
    c, s = math.cos(rot), math.sin(rot)
    for a, b in pts:
        A = (mx + a[0] * c - a[1] * s, my + a[0] * s + a[1] * c)
        B = (mx + b[0] * c - b[1] * s, my + b[0] * s + b[1] * c)
        d.line((A, B), fill=col, width=int(1.4 * S))


def _arc_text(img, text, cx, cy, radius, fnt, col, shadow):
    """Text entlang eines Bogens um (cx, cy), unten gelesen (Spieler sitzen unten), Mitte bei 90° (geradeaus)."""
    widths = [fnt.getlength(ch) for ch in text]
    total = sum(widths) * 1.08
    ang_total = total / radius
    a = math.pi / 2 + ang_total / 2
    for ch, wch in zip(text, widths):
        step = wch * 1.08 / radius
        mid = a - step / 2
        x, y = cx + math.cos(mid) * radius, cy + math.sin(mid) * radius
        size = int(fnt.size * 2.2)
        tile = Image.new("RGBA", (size, size), T)
        td = ImageDraw.Draw(tile)
        if shadow is not None:
            td.text((size / 2 + 1, size / 2 + 1), ch, font=fnt, fill=shadow[:3] + (150,), anchor="mm")
        td.text((size / 2, size / 2), ch, font=fnt, fill=col, anchor="mm")
        deg = math.degrees(mid - math.pi / 2)
        tile = tile.rotate(-deg, resample=Image.BICUBIC)
        img.alpha_composite(tile, (int(x - size / 2), int(y - size / 2)))
        a -= step


def _suit_row(img, R, y, w):
    d = ImageDraw.Draw(img)
    s = w * 0.016
    cols = [GOLD[3], GOLD[3], GOLD[3], GOLD[3]]
    for i, sym in enumerate("♠♥♣♦"):
        x = R + (i - 1.5) * s * 2.4
        f = font(FONT_SANS, int(s * 2))
        d.text((x, y), sym, font=f, fill=cols[i], anchor="mm")


def felt_poker(w=512):
    """Ovaler Pokertisch (Draufsicht, ganze Fläche): Filz, Goldlinie für die Gemeinschaftskarten, Pot-Feld."""
    h = w // 2
    img = Image.new("RGBA", (w, h), T)
    px = img.load()
    a, b = w / 2, h / 2
    for y in range(h):
        for x in range(w):
            dx, dy = (x + 0.5 - a) / a, (y + 0.5 - b) / b
            q = _stadium(dx, dy, a / b)
            if q > 0.995:
                continue
            light = 1.0 - min(1.0, q * 1.02)
            f = 0.12 + 0.88 * light
            idx = f * (len(FELT) - 1)
            i0 = int(idx)
            px[x, y] = mix(FELT[i0], FELT[min(i0 + 1, len(FELT) - 1)], idx - i0)
    S = 4
    big = img.resize((w * S, h * S), Image.NEAREST)
    d = ImageDraw.Draw(big)
    # Linie um die Gemeinschaftskarten (Stadion-Form)
    _stadium_line(d, w * S / 2, h * S / 2, w * S * 0.30, h * S * 0.20, GOLD[3], int(1.6 * S))
    # fünf Kartenplätze als feine Ecken
    for i in range(5):
        mx = w * S / 2 + (i - 2) * w * S * 0.083
        _corners(d, mx, h * S / 2, 0, w * S * 0.033, w * S * 0.045, mix(FELT[4], CREAM[2], 0.35), S)
    img = big.resize((w, h), Image.LANCZOS)
    _straight_text(img, "KUBY POKER", w / 2, h * 0.255, font(FONT_SERIF, int(w * 0.034)), GOLD[3], GOLD[0])
    _straight_text(img, "TEXAS HOLD'EM", w / 2, h * 0.745, font(FONT_SANS, int(w * 0.02)), mix(CREAM[2], FELT[3], 0.25), None)
    return img


def _stadium(dx, dy, ratio):
    """Abstand-Maß für eine Stadion-Form (Rechteck mit Halbkreisen), 1 = Rand."""
    # in Einheiten der kurzen Halbachse
    x = abs(dx) * ratio
    y = abs(dy)
    straight = ratio - 1.0
    if x <= straight:
        return y
    return math.hypot(x - straight, y)


def _stadium_line(d, cx, cy, hw, hh, col, width):
    r = hh
    d.rounded_rectangle((cx - hw, cy - hh, cx + hw, cy + hh), radius=r, outline=col, width=width)


def _straight_text(img, text, x, y, fnt, col, shadow):
    d = ImageDraw.Draw(img)
    if shadow is not None:
        d.text((x + 1, y + 1), text, font=fnt, fill=shadow[:3] + (150,), anchor="mm")
    d.text((x, y), text, font=fnt, fill=col, anchor="mm")


# =====================================================================================================================
# Materialien (64 x 64)
# =====================================================================================================================

def mat_leather(n=64):
    """Schwarzes Leder der Armauflage: oben Glanzkante, Steppnaht, nach unten dunkler."""
    img = Image.new("RGBA", (n, n), LEATHER[2])
    px = img.load()
    for y in range(n):
        f = y / (n - 1)
        for x in range(n):
            if f < 0.12:
                c = mix(LEATHER[4], LEATHER[3], f / 0.12)
            elif f < 0.55:
                c = mix(LEATHER[3], LEATHER[2], (f - 0.12) / 0.43)
            else:
                c = mix(LEATHER[2], LEATHER[0], (f - 0.55) / 0.45)
            px[x, y] = c
    # Steppnaht (Gold, gestrichelt) einmal oben, mit einem Bruch
    yy = int(n * 0.2)
    for x in range(1, n - 1):
        if (x // 3) % 2 == 0 and x not in (n // 2, n // 2 + 1):
            px[x, yy] = mix(GOLD[2], LEATHER[2], 0.25)
    return img


def mat_cream(n=64):
    """Sockel in Creme-Lack mit Goldband oben und dunklem Fuß."""
    img = Image.new("RGBA", (n, n), CREAM[2])
    px = img.load()
    for y in range(n):
        f = y / (n - 1)
        for x in range(n):
            if f < 0.06:
                c = GOLD[4] if y == 0 else GOLD[3]
            elif f < 0.11:
                c = GOLD[1]
            elif f < 0.86:
                g = (f - 0.11) / 0.75
                c = mix(CREAM[3], CREAM[1], g)
                # senkrechte Lackfugen in gleichem Abstand
                if x % 16 == 0:
                    c = mix(c, CREAM[0], 0.35)
            elif f < 0.9:
                c = GOLD[2]
            else:
                c = WALNUT[1]
            px[x, y] = c
    return img


def mat_seat(n=64):
    """Creme-Leder des Hockers mit Ziernaht und Knöpfen (Rautenmuster, ein Knopf versetzt)."""
    img = Image.new("RGBA", (n, n), CREAM[3])
    px = img.load()
    cx = cy = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            q = max(abs(x - cx), abs(y - cy)) / cx
            c = mix(CREAM[4], CREAM[2], q ** 1.5)
            if q > 0.9:
                c = CREAM[1]
            px[x, y] = c
    d = ImageDraw.Draw(img)
    for i in (n // 4, n // 2, 3 * n // 4):
        for j in (n // 4, n // 2, 3 * n // 4):
            if (i, j) == (n // 2, n // 4):
                continue
            d.ellipse((i - 2, j - 2, i + 2, j + 2), fill=CREAM[0])
            px[i - 1, j - 1] = CREAM[4]
    return img


def mat_gold(n=64):
    """Gebürstetes Gold: waagrechte Striche, oben hell, unten dunkler."""
    img = Image.new("RGBA", (n, n), GOLD[3])
    px = img.load()
    for y in range(n):
        f = y / (n - 1)
        for x in range(n):
            c = mix(GOLD[4], GOLD[2], f)
            if (y * 7 + (x // 9) * 3) % 11 == 0:
                c = mix(c, GOLD[5], 0.35)
            px[x, y] = c
    return img


def mat_dark(n=64):
    """Glänzendes Schwarz (Schlitten, Tablett)."""
    img = Image.new("RGBA", (n, n), LEATHER[1])
    px = img.load()
    for y in range(n):
        for x in range(n):
            f = (x + y) / (2 * n)
            c = mix(LEATHER[3], LEATHER[0], f)
            if abs(x - y) < 2 and x < n // 3:
                c = mix(c, LEATHER[4], 0.6)
            px[x, y] = c
    return img


def mat_walnut(n=64):
    img = Image.new("RGBA", (n, n), WALNUT[2])
    px = img.load()
    for y in range(n):
        for x in range(n):
            g = math.sin(y * 0.55 + math.sin(x * 0.09) * 2.2)
            c = mix(WALNUT[1], WALNUT[3], 0.5 + 0.5 * g)
            px[x, y] = c
    return img


MATERIALS = {
    "hd_leather": mat_leather,
    "hd_cream": mat_cream,
    "hd_seat": mat_seat,
    "hd_gold": mat_gold,
    "hd_dark": mat_dark,
    "hd_walnut": mat_walnut,
}


# =====================================================================================================================
# 3D-Karten (40 x 56 px auf 64er-Textur)
# =====================================================================================================================

CW, CH = 40, 56
RED = [rgb(c) for c in ("8A0A18", "B8121F", "D81E2A")]
BLACK = [rgb(c) for c in ("0E0C12", "1E1B24", "34303C")]
PAPER = [rgb(c) for c in ("D8D3C6", "ECE8DE", "F8F6F0", "FFFEFA")]
RANKS = ["A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"]
SUITS = {"h": "♥", "d": "♦", "s": "♠", "c": "♣"}


def _card_base():
    img = Image.new("RGBA", (64, 64), T)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, CW - 1, CH - 1), radius=4, fill=PAPER[2], outline=PAPER[0])
    # Licht oben links: obere Kante eine Stufe heller
    d.line((3, 1, CW - 4, 1), fill=PAPER[3])
    return img, d


def _suit_shape(d, cx, cy, size, suit, col):
    """Farbzeichen als Formen (unabhängig von Schriften), Mitte cx/cy, Höhe size."""
    s = size / 2
    if suit == "h":
        d.ellipse((cx - s, cy - s * 0.8, cx, cy + s * 0.2), fill=col)
        d.ellipse((cx, cy - s * 0.8, cx + s, cy + s * 0.2), fill=col)
        d.polygon([(cx - s * 0.98, cy - s * 0.18), (cx + s * 0.98, cy - s * 0.18), (cx, cy + s)], fill=col)
    elif suit == "d":
        d.polygon([(cx, cy - s), (cx + s * 0.72, cy), (cx, cy + s), (cx - s * 0.72, cy)], fill=col)
    elif suit == "s":
        d.polygon([(cx, cy - s), (cx + s * 0.95, cy + s * 0.15), (cx - s * 0.95, cy + s * 0.15)], fill=col)
        d.ellipse((cx - s * 0.98, cy - s * 0.2, cx, cy + s * 0.62), fill=col)
        d.ellipse((cx, cy - s * 0.2, cx + s * 0.98, cy + s * 0.62), fill=col)
        d.polygon([(cx - s * 0.12, cy + s * 0.3), (cx + s * 0.12, cy + s * 0.3), (cx + s * 0.45, cy + s), (cx - s * 0.45, cy + s)], fill=col)
    else:
        r = s * 0.46
        d.ellipse((cx - r, cy - s, cx + r, cy - s + 2 * r), fill=col)
        d.ellipse((cx - s, cy - s * 0.2, cx - s + 2 * r, cy - s * 0.2 + 2 * r), fill=col)
        d.ellipse((cx + s - 2 * r, cy - s * 0.2, cx + s, cy - s * 0.2 + 2 * r), fill=col)
        d.polygon([(cx - s * 0.12, cy), (cx + s * 0.12, cy), (cx + s * 0.45, cy + s), (cx - s * 0.45, cy + s)], fill=col)


def _rank_text(d, x, y, rank, col, size):
    f = font(FONT_SANS, size)
    if rank == "10":
        d.text((x, y), "10", font=f, fill=col, anchor="mt")
    else:
        d.text((x, y), rank, font=f, fill=col, anchor="mt")


def card_face(rank, suit):
    img, d = _card_base()
    col = RED[1] if suit in "hd" else BLACK[1]
    S = 4
    big = img.resize((64 * S, 64 * S), Image.NEAREST)
    bd = ImageDraw.Draw(big)
    # Index oben links und unten rechts (gedreht)
    for flip in (False, True):
        layer = Image.new("RGBA", (64 * S, 64 * S), T)
        ld = ImageDraw.Draw(layer)
        _rank_text(ld, 5.5 * S, 2.5 * S, rank, col, 8 * S if rank != "10" else 6.5 * S)
        _suit_shape(ld, 5.5 * S, 14 * S, 5 * S, suit, col)
        if flip:
            layer = layer.crop((0, 0, CW * S, CH * S)).rotate(180)
            tmp = Image.new("RGBA", (64 * S, 64 * S), T)
            tmp.paste(layer, (0, 0))
            layer = tmp
        big.alpha_composite(layer)
    cx, cy = CW * S / 2, CH * S / 2
    if rank in ("J", "Q", "K"):
        # Bildkarte: gerahmtes Feld mit großem Buchstaben, Krone darüber, Farbzeichen darunter
        bd.rounded_rectangle((11 * S, 11 * S, (CW - 11) * S, (CH - 11) * S), radius=3 * S,
                             fill=mix(PAPER[2], GOLD[4], 0.25), outline=GOLD[2], width=S)
        bd.rounded_rectangle((12.5 * S, 12.5 * S, (CW - 12.5) * S, (CH - 12.5) * S), radius=2 * S,
                             outline=col, width=max(1, S // 2))
        crown = [(cx - 6 * S, cy - 9 * S), (cx - 6 * S, cy - 13 * S), (cx - 3 * S, cy - 10.5 * S), (cx, cy - 14.5 * S),
                 (cx + 3 * S, cy - 10.5 * S), (cx + 6 * S, cy - 13 * S), (cx + 6 * S, cy - 9 * S)]
        bd.polygon(crown, fill=GOLD[3], outline=GOLD[0])
        f = font(FONT_SERIF, 16 * S)
        bd.text((cx, cy + 1 * S), rank, font=f, fill=col, anchor="mm")
        _suit_shape(bd, cx, cy + 11 * S, 6 * S, suit, col)
    elif rank == "A":
        _suit_shape(bd, cx, cy, 20 * S, suit, col)
    else:
        n = int(rank)
        for (px_, py_) in _pips(n):
            _suit_shape(bd, cx + px_ * S, cy + py_ * S, 6 * S, suit, col)
    img = big.resize((64, 64), Image.LANCZOS)
    return img


def _pips(n):
    L, M, Rr = -5.5, 0, 5.5
    rows = {
        2: [(M, -13), (M, 13)],
        3: [(M, -13), (M, 0), (M, 13)],
        4: [(L, -13), (Rr, -13), (L, 13), (Rr, 13)],
        5: [(L, -13), (Rr, -13), (M, 0), (L, 13), (Rr, 13)],
        6: [(L, -13), (Rr, -13), (L, 0), (Rr, 0), (L, 13), (Rr, 13)],
        7: [(L, -13), (Rr, -13), (M, -6.5), (L, 0), (Rr, 0), (L, 13), (Rr, 13)],
        8: [(L, -13), (Rr, -13), (M, -6.5), (L, 0), (Rr, 0), (M, 6.5), (L, 13), (Rr, 13)],
        9: [(L, -15), (Rr, -15), (L, -5), (Rr, -5), (M, 0), (L, 5), (Rr, 5), (L, 15), (Rr, 15)],
        10: [(L, -15), (Rr, -15), (M, -9), (L, -5), (Rr, -5), (L, 5), (Rr, 5), (M, 9), (L, 15), (Rr, 15)],
    }
    return rows[n]


def card_back():
    """Rückseite: dunkles Bordeaux mit feinem Gold-Rautenmuster, weißer Rand, Kuby-K in der Mitte."""
    img = Image.new("RGBA", (64, 64), T)
    S = 4
    big = Image.new("RGBA", (64 * S, 64 * S), T)
    d = ImageDraw.Draw(big)
    d.rounded_rectangle((0, 0, (CW - 1) * S, (CH - 1) * S), radius=4 * S, fill=PAPER[2], outline=PAPER[0], width=S)
    x0, y0, x1, y1 = 3 * S, 3 * S, (CW - 4) * S, (CH - 4) * S
    d.rounded_rectangle((x0, y0, x1, y1), radius=2 * S, fill=rgb("5A0E1C"))
    # Rautengitter
    step = 5 * S
    for k in range(-20, 30):
        d.line((x0 + k * step, y0, x0 + k * step + (y1 - y0), y1), fill=rgb("74182A"), width=S)
        d.line((x0 + k * step, y1, x0 + k * step + (y1 - y0), y0), fill=rgb("74182A"), width=S)
    # Maske auf das innere Feld
    mask = Image.new("L", big.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((x0, y0, x1, y1), radius=2 * S, fill=255)
    inner = big.copy()
    base = Image.new("RGBA", big.size, T)
    ImageDraw.Draw(base).rounded_rectangle((0, 0, (CW - 1) * S, (CH - 1) * S), radius=4 * S, fill=PAPER[2],
                                           outline=PAPER[0], width=S)
    base.paste(inner, (0, 0), mask)
    d = ImageDraw.Draw(base)
    d.rounded_rectangle((x0, y0, x1, y1), radius=2 * S, outline=GOLD[3], width=S)
    cx, cy = CW * S / 2, CH * S / 2
    d.ellipse((cx - 8 * S, cy - 8 * S, cx + 8 * S, cy + 8 * S), fill=rgb("3E0812"), outline=GOLD[3], width=S)
    d.text((cx, cy + S * 0.5), "K", font=font(FONT_SERIF, 12 * S), fill=GOLD[4], anchor="mm")
    return base.resize((64, 64), Image.LANCZOS)


def card_model(front, back):
    """Karte als flacher Körper: 10 x 14 Pixel, 0,3 dick, Oberseite = Vorderseite, Unterseite = Rückseite."""
    uv = [0, 0, CW / 4, CH / 4]
    return {
        "textures": {"front": front, "back": back, "edge": "kuby:block/casino/hd_edge", "particle": front},
        "elements": [{
            "from": [3, 7.85, 1], "to": [13, 8.15, 15],
            "faces": {
                "up": {"texture": "#front", "uv": uv},
                "down": {"texture": "#back", "uv": [uv[0], uv[3], uv[2], uv[1]]},
                "north": {"texture": "#edge", "uv": [0, 0, 16, 1]},
                "south": {"texture": "#edge", "uv": [0, 0, 16, 1]},
                "east": {"texture": "#edge", "uv": [0, 0, 16, 1]},
                "west": {"texture": "#edge", "uv": [0, 0, 16, 1]},
            }}],
    }


# =====================================================================================================================
# 3D-Chips
# =====================================================================================================================

CHIP_COLORS = {
    # Name: (Rampe dunkel->hell der Grundfarbe, Farbe der Randflecken)
    "white": (["9C9A94", "C8C5BC", "E6E3DA", "F6F4EE"], "1F5FB8"),
    "red": (["7A0A14", "A8121E", "CC1C26", "E83A34"], "F6F4EE"),
    "blue": (["0C3A86", "1555B8", "2272DA", "4C98F0"], "F6F4EE"),
    "green": (["0B5A22", "11782E", "1B963C", "3CB858"], "F6F4EE"),
    "black": (["0E0C12", "1E1B24", "2E2A34", "48434F"], "F2C23A"),
    "purple": (["4A0C86", "6A16B4", "8A28DA", "AA52F0"], "F6F4EE"),
    "gold": (["8A5A08", "B57E0C", "D9A11A", "F2C23A"], "7A0A14"),
    "pink": (["7A1440", "A81E5A", "D63A7A", "F0629A"], "F6F4EE"),
}


def chip_top(name, n=32):
    ramp, spot = CHIP_COLORS[name]
    ramp = [rgb(c) for c in ramp]
    spot = rgb(spot)
    S = 8
    big = Image.new("RGBA", (n * S, n * S), T)
    d = ImageDraw.Draw(big)
    c = n * S / 2
    r = n * S / 2 - S * 0.5
    d.ellipse((c - r, c - r, c + r, c + r), fill=ramp[2], outline=mix(ramp[0], INK, 0.5), width=S)
    # sechs Randflecken
    for k in range(6):
        a0 = k * 60 - 12
        d.pieslice((c - r, c - r, c + r, c + r), a0, a0 + 24, fill=spot)
    # Innenring und Mittelfeld
    ir = r * 0.68
    d.ellipse((c - r * 0.78, c - r * 0.78, c + r * 0.78, c + r * 0.78), fill=ramp[2])
    d.ellipse((c - ir, c - ir, c + ir, c + ir), outline=spot, width=S)
    d.ellipse((c - ir * 0.86, c - ir * 0.86, c + ir * 0.86, c + ir * 0.86), fill=ramp[3])
    # Licht oben links (Glanzbogen), Schatten unten rechts
    d.arc((c - r * 0.92, c - r * 0.92, c + r * 0.92, c + r * 0.92), 190, 260, fill=mix(ramp[3], PAPER[3], 0.5), width=S)
    d.arc((c - r * 0.92, c - r * 0.92, c + r * 0.92, c + r * 0.92), 20, 110, fill=ramp[1], width=S)
    # Kuby-K in der Mitte
    d.text((c, c + S * 0.4), "K", font=font(FONT_SERIF, int(ir * 1.0)), fill=mix(ramp[1], INK, 0.2), anchor="mm")
    return big.resize((n, n), Image.LANCZOS)


def chip_side(name, n=32):
    ramp, spot = CHIP_COLORS[name]
    ramp = [rgb(c) for c in ramp]
    spot = rgb(spot)
    img = Image.new("RGBA", (n, 4), ramp[1])
    px = img.load()
    for x in range(n):
        for y in range(4):
            c = ramp[2] if y == 0 else ramp[1] if y < 3 else ramp[0]
            if (x // 4) % 2 == 0:
                c = spot if y in (1, 2) else mix(spot, ramp[1], 0.4)
            px[x, y] = c
    full = Image.new("RGBA", (n, n), T)
    for k in range(n // 4):
        full.paste(img, (0, k * 4))
    return full


def chip_model(name):
    """Chip als 16-Eck aus vier um 22,5° gedrehten Quadern, oben eine runde Scheibe (Textur mit Transparenz)."""
    tex = "kuby:item/casino/hd/chip_" + name
    side = "kuby:item/casino/hd/chipside_" + name
    els = []
    for ang in (0, 22.5, 45, -22.5):
        els.append({
            "from": [2.2, 7.4, 2.2], "to": [13.8, 8.6, 13.8],
            "rotation": {"angle": ang, "axis": "y", "origin": [8, 8, 8]},
            "faces": {f: {"texture": "#side", "uv": [0, 0, 16, 1]} for f in ("north", "south", "east", "west")},
        })
    els.append({"from": [1.6, 8.62, 1.6], "to": [14.4, 8.62, 14.4],
                "faces": {"up": {"texture": "#top", "uv": [0, 0, 16, 16]}}})
    els.append({"from": [1.6, 7.38, 1.6], "to": [14.4, 7.38, 14.4],
                "faces": {"down": {"texture": "#top", "uv": [0, 0, 16, 16]}}})
    return {"textures": {"top": tex, "side": side, "particle": tex}, "elements": els}


# =====================================================================================================================
# Knöpfe auf dem Filz (Aktionen) und auf der Armauflage (Bedienung)
# =====================================================================================================================

BUTTONS = {
    # Name: (Text, Rampe dunkel->hell, Symbol)
    "hit": ("KARTE", ["0E5A2C", "16803C", "22A04E", "4CC872"], "plus"),
    "stand": ("HALTEN", ["7A1418", "A81E22", "CC2E2C", "E85A4C"], "hand"),
    "double": ("DOPPELN", ["8A5A08", "B57E0C", "D9A11A", "F2C23A"], "x2"),
    "split": ("TEILEN", ["123E86", "1A56B4", "2872DA", "56A0F0"], "split"),
}


PK_BUTTONS = {
    "fold": ("PASSEN", ["7A1418", "A81E22", "CC2E2C", "E85A4C"], "x"),
    "check": ("CHECK", ["0E5A2C", "16803C", "22A04E", "4CC872"], "check"),
    "call": ("MITGEHEN", ["0E5A2C", "16803C", "22A04E", "4CC872"], "plus"),
    "raise": ("ERHÖHEN", ["8A5A08", "B57E0C", "D9A11A", "F2C23A"], "up"),
    "allin": ("ALL-IN", ["4A0C86", "6A16B4", "8A28DA", "AA52F0"], "star"),
    "ok": ("SETZEN", ["0E5A2C", "16803C", "22A04E", "4CC872"], "check"),
    "half": ("½ POT", ["123E86", "1A56B4", "2872DA", "56A0F0"], "none"),
    "pot": ("POT", ["123E86", "1A56B4", "2872DA", "56A0F0"], "none"),
    "minus": ("−", ["2A2238", "3E3354", "5A4C78", "8A7AB0"], "none"),
    "plus": ("+", ["2A2238", "3E3354", "5A4C78", "8A7AB0"], "none"),
    "back": ("ZURÜCK", ["3A3640", "524C5A", "6A6474", "8A8494"], "none"),
    "show": ("ZEIGEN", ["123E86", "1A56B4", "2872DA", "56A0F0"], "none"),
    "sitout": ("PAUSE", ["3A3640", "524C5A", "6A6474", "8A8494"], "none"),
}


def action_button(name, w=128, h=64):
    label, ramp, sym = BUTTONS[name] if name in BUTTONS else PK_BUTTONS[name]
    ramp = [rgb(c) for c in ramp]
    S = 4
    big = Image.new("RGBA", (w * S, h * S), T)
    d = ImageDraw.Draw(big)
    # Schatten unten, Körper, Lichtkante oben
    d.rounded_rectangle((2 * S, 6 * S, (w - 2) * S, (h - 2) * S), radius=12 * S, fill=mix(ramp[0], INK, 0.5))
    d.rounded_rectangle((2 * S, 2 * S, (w - 2) * S, (h - 6) * S), radius=12 * S, fill=ramp[1], outline=mix(ramp[0], INK, 0.6), width=2 * S)
    d.rounded_rectangle((5 * S, 5 * S, (w - 5) * S, (h - 9) * S), radius=9 * S, fill=ramp[2])
    d.rounded_rectangle((8 * S, 7 * S, (w - 8) * S, 11 * S), radius=3 * S, fill=mix(ramp[3], PAPER[3], 0.35))
    size = 30 if len(label) <= 1 else 17 if len(label) <= 6 else 14 if len(label) <= 7 else 12.5
    f = font(FONT_SANS, int(size * S))
    cy = (h - 4) * S / 2
    tx = (w / 2 + (9 if sym != "none" else 0)) * S
    d.text((tx, cy), label, font=f, fill=PAPER[3], anchor="mm", stroke_width=S, stroke_fill=mix(ramp[0], INK, 0.4))
    # Symbol links
    ix, iy, r = 16 * S, cy, 8 * S
    col = PAPER[3]
    if sym == "plus":
        d.rectangle((ix - r, iy - 2 * S, ix + r, iy + 2 * S), fill=col)
        d.rectangle((ix - 2 * S, iy - r, ix + 2 * S, iy + r), fill=col)
    elif sym == "hand":
        d.rounded_rectangle((ix - r, iy - r * 0.4, ix + r, iy + r), radius=3 * S, fill=col)
        for k in range(4):
            x = ix - r + k * (2 * r / 4) + S
            d.rounded_rectangle((x, iy - r, x + 3 * S, iy), radius=S, fill=col)
    elif sym == "x2":
        d.text((ix, iy), "2×", font=font(FONT_SANS, int(14 * S)), fill=col, anchor="mm")
    elif sym == "split":
        d.polygon([(ix - r, iy), (ix - 2 * S, iy - r), (ix - 2 * S, iy + r)], fill=col)
        d.polygon([(ix + r, iy), (ix + 2 * S, iy - r), (ix + 2 * S, iy + r)], fill=col)
    elif sym == "x":
        d.line((ix - r * 0.8, iy - r * 0.8, ix + r * 0.8, iy + r * 0.8), fill=col, width=4 * S)
        d.line((ix - r * 0.8, iy + r * 0.8, ix + r * 0.8, iy - r * 0.8), fill=col, width=4 * S)
    elif sym == "check":
        d.line((ix - r * 0.9, iy, ix - r * 0.2, iy + r * 0.7, ix + r, iy - r * 0.8), fill=col, width=4 * S, joint="curve")
    elif sym == "up":
        d.polygon([(ix, iy - r), (ix + r, iy + r * 0.2), (ix + r * 0.35, iy + r * 0.2), (ix + r * 0.35, iy + r),
                   (ix - r * 0.35, iy + r), (ix - r * 0.35, iy + r * 0.2), (ix - r, iy + r * 0.2)], fill=col)
    elif sym == "star":
        pts = []
        for k in range(10):
            rr = r if k % 2 == 0 else r * 0.45
            a = math.radians(-90 + k * 36)
            pts.append((ix + rr * math.cos(a), iy + rr * math.sin(a)))
        d.polygon(pts, fill=col)
    return big.resize((w, h), Image.LANCZOS)


# =====================================================================================================================
# Slot-Automaten "Goldrausch" und "Neon 7": Leuchtschild oben, Front am Pult, Materialien
# =====================================================================================================================

VELVET = [rgb(c) for c in ("4A0610", "6E0A18", "961224", "B82434")]
NEON_P = [rgb(c) for c in ("8A1050", "D0207A", "FF4FA8", "FFC2E0")]
NEON_C = [rgb(c) for c in ("0A6A7A", "12A8C0", "3FE6F0", "C8FAFF")]
PURPLE = [rgb(c) for c in ("12081E", "1E0E30", "2C1646", "40205E")]


def _glow_text(img, xy, text, fnt, core, glow, radius=6):
    layer = Image.new("RGBA", img.size, T)
    ImageDraw.Draw(layer).text(xy, text, font=fnt, fill=glow[:3] + (255,), anchor="mm")
    blur = layer.filter(ImageFilter.GaussianBlur(radius))
    img.alpha_composite(blur)
    img.alpha_composite(blur)
    ImageDraw.Draw(img).text(xy, text, font=fnt, fill=core, anchor="mm")


def slot_top(style, w=256, h=96):
    S = 4
    if style == "gold":
        big = Image.new("RGBA", (w * S, h * S), LEATHER[1])
        d = ImageDraw.Draw(big)
        d.rectangle((0, 0, w * S - 1, h * S - 1), outline=GOLD[2], width=3 * S)
        d.rectangle((6 * S, 6 * S, (w - 6) * S, (h - 6) * S), outline=GOLD[4], width=S)
        # rotes Feld mit Licht von innen
        inner = (14 * S, 14 * S, (w - 14) * S, (h - 14) * S)
        d.rounded_rectangle(inner, radius=8 * S, fill=VELVET[1], outline=GOLD[3], width=2 * S)
        glow = Image.new("RGBA", big.size, T)
        ImageDraw.Draw(glow).ellipse((w * S * 0.25, h * S * 0.2, w * S * 0.75, h * S * 0.8), fill=VELVET[3][:3] + (200,))
        big.alpha_composite(glow.filter(ImageFilter.GaussianBlur(16 * S)))
        # Lauflicht-Birnen rundum (gleichmäßig, eine bewusst dunkler)
        k = 0
        for x in range(12, w - 8, 12):
            for y in (9, h - 9):
                col = GOLD[5] if (k % 9) != 4 else GOLD[1]
                d.ellipse(((x - 2.5) * S, (y - 2.5) * S, (x + 2.5) * S, (y + 2.5) * S), fill=col, outline=GOLD[0], width=S)
                k += 1
        img = big.resize((w, h), Image.LANCZOS)
        f = font(FONT_SERIF, 23)
        d2 = ImageDraw.Draw(img)
        d2.text((w / 2 + 2, h / 2 + 2), "GOLDRAUSCH", font=f, fill=INK, anchor="mm")
        d2.text((w / 2, h / 2), "GOLDRAUSCH", font=f, fill=GOLD[4], anchor="mm", stroke_width=1, stroke_fill=GOLD[0])
        for x in (27, w - 27):
            d2.text((x, h / 2), "7", font=font(FONT_SERIF, 24), fill=GOLD[3], anchor="mm", stroke_width=1, stroke_fill=VELVET[0])
        return img
    big = Image.new("RGBA", (w, h), PURPLE[0])
    d = ImageDraw.Draw(big)
    for y in range(h):
        d.line((0, y, w, y), fill=mix(PURPLE[1], PURPLE[0], abs(y - h / 2) / (h / 2)))
    # Röhrenrahmen cyan mit Leuchten
    frame = Image.new("RGBA", big.size, T)
    ImageDraw.Draw(frame).rounded_rectangle((5, 5, w - 6, h - 6), radius=10, outline=NEON_C[2], width=3)
    big.alpha_composite(frame.filter(ImageFilter.GaussianBlur(4)))
    big.alpha_composite(frame)
    ImageDraw.Draw(big).rounded_rectangle((5, 5, w - 6, h - 6), radius=10, outline=NEON_C[3], width=1)
    _glow_text(big, (w / 2 - 14, h / 2 + 2), "NEON", font(FONT_SANS, 34), NEON_P[3], NEON_P[2], 5)
    _glow_text(big, (w / 2 + 62, h / 2 + 2), "7", font(FONT_SERIF, 46), NEON_C[3], NEON_C[2], 6)
    return big


def slot_front(style, w=128, h=64):
    if style == "gold":
        img = Image.new("RGBA", (w, h), LEATHER[1])
        d = ImageDraw.Draw(img)
        # Art-déco-Fächer aus der Mitte unten, gleichmäßig, ein Strahl länger
        cx, cy = w / 2, h - 4
        for k in range(-6, 7):
            a = math.radians(90 + k * 13)
            L = 54 if k != 2 else 60
            d.line((cx, cy, cx + math.cos(a) * L, cy - math.sin(a) * L), fill=GOLD[2] if k % 2 else GOLD[3], width=1)
        d.rectangle((0, 0, w - 1, h - 1), outline=GOLD[3], width=2)
        d.line((0, 5, w, 5), fill=GOLD[1])
        return img
    img = Image.new("RGBA", (w, h), PURPLE[1])
    d = ImageDraw.Draw(img)
    for k in range(-4, 10):
        x = k * 18
        d.line((x, h, x + h, 0), fill=NEON_P[2] if k % 2 else NEON_C[2], width=2)
    layer = img.filter(ImageFilter.GaussianBlur(2))
    img.alpha_composite(layer)
    d.rectangle((0, 0, w - 1, h - 1), outline=NEON_C[3], width=2)
    return img


def mat_flat(ramp, kind="plain", n=64):
    img = Image.new("RGBA", (n, n), ramp[2])
    px = img.load()
    for y in range(n):
        for x in range(n):
            f = y / (n - 1)
            c = mix(ramp[3], ramp[1], f)
            if kind == "tube":
                q = abs(x - n / 2) / (n / 2)
                c = mix(ramp[3], ramp[2], q * 1.4)
            elif kind == "velvet" and (x + y * 3) % 7 == 0:
                c = mix(c, ramp[0], 0.25)
            px[x, y] = c
    return img


SLOT_MATS = {
    "hd_velvet": lambda: mat_flat(VELVET, "velvet"),
    "hd_neon_pink": lambda: mat_flat(NEON_P, "tube"),
    "hd_neon_cyan": lambda: mat_flat(NEON_C, "tube"),
    "hd_purple": lambda: mat_flat(PURPLE),
}


def dealer_button(n=64):
    """Dealer-Button: weiße Scheibe mit schwarzem D, Goldrand."""
    S = 4
    big = Image.new("RGBA", (n * S, n * S), T)
    d = ImageDraw.Draw(big)
    c = n * S / 2
    r = n * S / 2 - 2 * S
    d.ellipse((c - r, c - r, c + r, c + r), fill=PAPER[2], outline=GOLD[2], width=3 * S)
    d.ellipse((c - r * 0.8, c - r * 0.8, c + r * 0.8, c + r * 0.8), outline=PAPER[0], width=S)
    d.text((c, c + S), "D", font=font(FONT_SERIF, int(r * 1.2)), fill=BLACK[1], anchor="mm")
    d.arc((c - r * 0.9, c - r * 0.9, c + r * 0.9, c + r * 0.9), 200, 280, fill=PAPER[3], width=2 * S)
    return big.resize((n, n), Image.LANCZOS)


def control_button(kind, n=64):
    S = 4
    big = Image.new("RGBA", (n * S, n * S), T)
    d = ImageDraw.Draw(big)
    c = n * S / 2
    r = n * S / 2 - 3 * S
    base = {"repeat": ["2A2238", "3E3354", "5A4C78", "8A7AB0"], "clear": ["3A1418", "5A1E22", "7A2A2C", "A84A44"],
            "chips": ["5A3A06", "8A5A08", "B57E0C", "F2C23A"], "jewels": ["0C3A5A", "11577E", "1E7DAA", "5CC8EE"]}[kind]
    ramp = [rgb(x) for x in base]
    d.ellipse((c - r, c - r + 3 * S, c + r, c + r + 3 * S), fill=mix(ramp[0], INK, 0.5))
    d.ellipse((c - r, c - r, c + r, c + r), fill=ramp[1], outline=mix(ramp[0], INK, 0.6), width=2 * S)
    d.ellipse((c - r * 0.84, c - r * 0.84, c + r * 0.84, c + r * 0.84), fill=ramp[2])
    d.arc((c - r * 0.84, c - r * 0.84, c + r * 0.84, c + r * 0.84), 200, 290, fill=ramp[3], width=2 * S)
    w = PAPER[3]
    if kind == "repeat":
        d.arc((c - r * 0.5, c - r * 0.5, c + r * 0.5, c + r * 0.5), 40, 320, fill=w, width=4 * S)
        d.polygon([(c + r * 0.42, c - r * 0.62), (c + r * 0.72, c - r * 0.2), (c + r * 0.18, c - r * 0.12)], fill=w)
    elif kind == "clear":
        q = r * 0.42
        d.line((c - q, c - q, c + q, c + q), fill=w, width=5 * S)
        d.line((c - q, c + q, c + q, c - q), fill=w, width=5 * S)
    elif kind == "chips":
        d.ellipse((c - r * 0.5, c - r * 0.5, c + r * 0.5, c + r * 0.5), fill=w)
        d.ellipse((c - r * 0.3, c - r * 0.3, c + r * 0.3, c + r * 0.3), fill=ramp[2])
    else:
        q = r * 0.5
        d.polygon([(c - q, c - q * 0.3), (c - q * 0.5, c - q * 0.8), (c + q * 0.5, c - q * 0.8), (c + q, c - q * 0.3), (c, c + q)], fill=w)
        d.polygon([(c - q, c - q * 0.3), (c + q, c - q * 0.3), (c, c + q)], fill=mix(w, ramp[3], 0.4))
    return big.resize((n, n), Image.LANCZOS)


# =====================================================================================================================
# Schreiben
# =====================================================================================================================

def flat_model(tex, x0, z0, x1, z1):
    return {"textures": {"top": tex, "particle": tex},
            "elements": [{"from": [x0, 8, z0], "to": [x1, 8.02, z1],
                          "faces": {"up": {"texture": "#top", "uv": [0, 0, 16, 16]}}}]}


def cube_model(tex):
    faces = {d: {"texture": "#all", "uv": [0, 0, 16, 16]} for d in ("north", "south", "east", "west", "up", "down")}
    return {"textures": {"all": tex, "particle": tex}, "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]}


def write(out):
    a = os.path.join(out, "assets/kuby")
    btex = os.path.join(a, "textures/block/casino")
    itex = os.path.join(a, "textures/item/casino/hd")
    models = os.path.join(a, "models/item/casino")
    items = os.path.join(a, "items/casino")
    for d in (btex, itex, models, items):
        os.makedirs(d, exist_ok=True)

    def item(name, model):
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump(model, fh)
        with open(os.path.join(items, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/" + name}}, fh)

    felt_blackjack().save(os.path.join(btex, "bj_felt.png"))
    item("bj_felt", flat_model("kuby:block/casino/bj_felt", 0, 8, 16, 16))
    felt_poker().save(os.path.join(btex, "pk_felt.png"))
    item("pk_felt", flat_model("kuby:block/casino/pk_felt", 0, 4, 16, 12))
    for name, fn in MATERIALS.items():
        fn().save(os.path.join(btex, name + ".png"))
        item("m_" + name, cube_model("kuby:block/casino/" + name))
    Image.new("RGBA", (16, 16), PAPER[2]).save(os.path.join(btex, "hd_edge.png"))
    back = "kuby:item/casino/hd/back"
    card_back().save(os.path.join(itex, "back.png"))
    for s in SUITS:
        for r in RANKS:
            cid = r.lower() + s
            card_face(r, s).save(os.path.join(itex, cid + ".png"))
            item("c3_" + cid, card_model("kuby:item/casino/hd/" + cid, back))
    item("c3_back", card_model(back, back))
    for name in BUTTONS:
        action_button(name).save(os.path.join(itex, "btn_" + name + ".png"))
        item("bj_btn_" + name, flat_model("kuby:item/casino/hd/btn_" + name, 0, 4, 16, 12))
    for name in PK_BUTTONS:
        action_button(name).save(os.path.join(itex, "pk_" + name + ".png"))
        item("pk_btn_" + name, flat_model("kuby:item/casino/hd/pk_" + name, 0, 4, 16, 12))
    dealer_button().save(os.path.join(itex, "dealer_btn.png"))
    for name, fn in SLOT_MATS.items():
        fn().save(os.path.join(btex, name + ".png"))
        item("m_" + name, cube_model("kuby:block/casino/" + name))
    for style in ("gold", "neon"):
        slot_top(style).save(os.path.join(itex, "sl_" + style + "_top.png"))
        item("sl_" + style + "_top", flat_model("kuby:item/casino/hd/sl_" + style + "_top", 0, 5, 16, 11))
        slot_front(style).save(os.path.join(itex, "sl_" + style + "_front.png"))
        item("sl_" + style + "_front", flat_model("kuby:item/casino/hd/sl_" + style + "_front", 0, 4, 16, 12))
    item("pk_dealer", {"textures": {"top": "kuby:item/casino/hd/dealer_btn", "side": "kuby:block/casino/hd_edge",
                                    "particle": "kuby:item/casino/hd/dealer_btn"},
                       "elements": [{"from": [2, 7.5, 2], "to": [14, 8.5, 14],
                                     "faces": {"up": {"texture": "#top", "uv": [0, 0, 16, 16]},
                                               "north": {"texture": "#side", "uv": [0, 0, 16, 1]},
                                               "south": {"texture": "#side", "uv": [0, 0, 16, 1]},
                                               "east": {"texture": "#side", "uv": [0, 0, 16, 1]},
                                               "west": {"texture": "#side", "uv": [0, 0, 16, 1]}}}]})
    for kind in ("repeat", "clear", "chips", "jewels"):
        control_button(kind).save(os.path.join(itex, "ctl_" + kind + ".png"))
        item("bj_ctl_" + kind, flat_model("kuby:item/casino/hd/ctl_" + kind, 0, 0, 16, 16))
    for name in CHIP_COLORS:
        chip_top(name).save(os.path.join(itex, "chip_" + name + ".png"))
        chip_side(name).save(os.path.join(itex, "chipside_" + name + ".png"))
        item("c3chip_" + name, chip_model(name))


def preview(path):
    """Kontaktbogen: Filz, Materialien, alle Karten, Chips (4-fach)."""
    cards = [card_face(r, s) for s in "hdsc" for r in RANKS] + [card_back()]
    sheet = Image.new("RGBA", (13 * 44 * 2 + 20, 5 * 60 * 2 + 1000), rgb("1C1A20"))
    for i, c in enumerate(cards):
        x, y = (i % 13) * 88 + 10, (i // 13) * 120 + 10
        sheet.alpha_composite(c.crop((0, 0, CW, CH)).resize((CW * 2, CH * 2), Image.NEAREST), (x, y))
    f = felt_blackjack()
    sheet.alpha_composite(f.resize((512, 256)), (10, 620))
    p = felt_poker()
    sheet.alpha_composite(p.resize((512, 256)), (540, 620))
    for i, (name, fn) in enumerate(MATERIALS.items()):
        sheet.alpha_composite(fn().resize((96, 96), Image.NEAREST), (10 + i * 104, 890))
    for i, name in enumerate(CHIP_COLORS):
        sheet.alpha_composite(chip_top(name).resize((96, 96), Image.NEAREST), (640 + (i % 4) * 100, 880 + (i // 4) * 100))
    for i, name in enumerate(BUTTONS):
        sheet.alpha_composite(action_button(name), (10 + i * 136, 1000))
    for i, k in enumerate(("repeat", "clear", "chips", "jewels")):
        sheet.alpha_composite(control_button(k), (10 + i * 72, 1080))
    for i, name in enumerate(PK_BUTTONS):
        sheet.alpha_composite(action_button(name), (10 + (i % 8) * 136, 1150 + (i // 8) * 70))
    sheet.alpha_composite(dealer_button(), (1000, 1080))
    for i, style in enumerate(("gold", "neon")):
        sheet.alpha_composite(slot_top(style).resize((384, 144)), (10 + i * 400, 1300))
        sheet.alpha_composite(slot_front(style).resize((256, 128)), (10 + i * 400, 1450))
    sheet.save(path)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--preview":
        preview(sys.argv[2])
    else:
        write(sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "resourcepack/KubyUI"))
