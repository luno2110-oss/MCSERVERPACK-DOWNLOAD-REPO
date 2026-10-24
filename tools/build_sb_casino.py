"""Casino-Variante der Sidebar (wie im Clip): rot-oranger Rahmen ohne Ranken, Funkeln in den oberen Ecken,
Logo mit Spielkarten-Fächer, Chips und Münzstapeln, unten links ein Slot-Automat, unten rechts ein Roulette-Rad.

Zeichen (Schrift kubyplugins:sb): Oberteil 0xE180, Mittelstücke 0xE180 + k, Unterteile 0xE1C0 + k,
Logo 0xE053. Muss zu de.kuby.kubyscoreboard.SbUi passen.
"""
import math

import build_sb_pack as sb
from build_sb_pack import rgb, new, shade, ramp, stamp

RED = [(0.0, rgb("A81410")), (0.35, rgb("C42A16")), (0.7, rgb("E04C18")), (1.0, rgb("F27A20"))]
DRED = [(0.0, rgb("3E0604")), (0.5, rgb("5C0C06")), (1.0, rgb("701808"))]
DECO_EXT = 1          # Deko sitzt innen in den unteren Ecken
CARD_EXT = 11         # Logo ragt für die Karten 11 px weiter nach oben


def band(b, g):
    red = ramp(RED, g)
    dred = ramp(DRED, g)
    return [sb.OUT_BLACK, shade(red, 1.15), red, dred, shade(dred, 0.8), sb.IN_BLACK][b]


def frame_piece(rel_top, h, top, bottom, inner_alpha, ext_top=0, ext_bottom=0):
    """Wie build_sb_pack.frame_piece, aber rot-orange und ohne Ranken."""
    w = sb.FRAME_W
    img = new(sb.TEX_W, ext_top + h + ext_bottom)
    px = img.load()
    for y in range(h):
        g = (rel_top + y + 0.5) / sb.NOMINAL_H
        for x in range(w):
            dx = min(x, w - 1 - x)
            dt = y if top else 10 ** 6
            db = (h - 1 - y) if bottom else 10 ** 6
            dy = min(dt, db)
            if dx == 0 and dy == 0:
                continue
            d = min(dx, dy)
            if d >= sb.BORDER:
                a = inner_alpha
                if top and dt - sb.BORDER < 3:
                    a += (70, 40, 18)[dt - sb.BORDER]
                if x - sb.BORDER < 3:
                    a += (50, 26, 10)[x - sb.BORDER]
                px[sb.M + x, ext_top + y] = (0, 0, 0, min(a, 200))
                continue
            b = d
            if db <= dx and db < dt and b not in (0, sb.BORDER - 1):
                b = sb.BORDER - 1 - b
            px[sb.M + x, ext_top + y] = band(b, g)
    return img


SPARKLE = {"Y": rgb("FFE070"), "W": rgb("FFFBE0"), "O": rgb("E8A020")}


def sparkle(img, x, y, big=True):
    px = img.load()
    pts = [(0, -2, "O"), (0, -1, "Y"), (-2, 0, "O"), (-1, 0, "Y"), (0, 0, "W"), (1, 0, "Y"), (2, 0, "O"), (0, 1, "Y"),
           (0, 2, "O")]
    if big:
        pts += [(0, -3, "O"), (-3, 0, "O"), (3, 0, "O"), (0, 3, "O")]
    for dx, dy, c in pts:
        if 0 <= x + dx < img.width and 0 <= y + dy < img.height:
            px[x + dx, y + dy] = SPARKLE[c]


def outline(layer, col=rgb("140804")):
    """Dunkler Rand um alle deckenden Pixel."""
    px = layer.load()
    w, h = layer.size
    out = new(w, h)
    op = out.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3]:
                continue
            if any(0 <= x + dx < w and 0 <= y + dy < h and px[x + dx, y + dy][3] > 128
                   for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                op[x, y] = col
    out.alpha_composite(layer)
    return out


# ------------------------------------------------------------------------------------------------------------
# Deko unten
# ------------------------------------------------------------------------------------------------------------
SLOT_ROWS = [
    "...............rR",
    "...............RR",
    "................k",
    "..GgggggggggG...k",
    ".GRRRRRRRRRRRG..k",
    ".GRWWWWWWWWWRG..k",
    ".GRWsssWsssWRG.kk",
    ".GRWWWsWWWsWRGkk.",
    ".GRWWsWWWWsWRG...",
    ".GRWWsWWWsWWRG...",
    ".GRWWWWWWWWWRG...",
    ".GRRRRRRRRRRRG...",
    ".GRrgggggggrRG...",
    ".GRRRRRRRRRRRG...",
    ".GRrrrrrrrrrRG...",
    ".GRRRRRRRRRRRG...",
    ".GGGGGGGGGGGGG...",
    "YyYyYyYyYyYyYyY..",
    "yOyOyOyOyOyOyOy..",
]
SLOT_PAL = {"G": "E8B828", "g": "FFE070", "R": "D81E28", "r": "A01018", "W": "F4F2EE", "s": "D81E28",
            "k": "4A4A52", "Y": "FFD84A", "y": "E0A020", "O": "B07010"}


def slot_machine():
    return outline(stamp(SLOT_ROWS, {k: rgb(v) for k, v in SLOT_PAL.items()}))


def roulette_wheel():
    s = 19
    img = new(s, s)
    px = img.load()
    c = (s - 1) / 2
    for y in range(s):
        for x in range(s):
            d = math.hypot(x - c, y - c)
            a = (math.degrees(math.atan2(y - c, x - c)) + 360) % 360
            if d > 9.3:
                continue
            if d > 7.9:
                px[x, y] = rgb("E8B828") if (x + y) % 3 else rgb("FFE070")
            elif d > 2.4:
                seg = int(a // 22.5)
                px[x, y] = rgb("D81E28") if seg % 2 == 0 else rgb("1C1A20")
                if seg == 3 and d > 6.0:
                    px[x, y] = rgb("2FA84A")
            else:
                px[x, y] = rgb("E8B828")
    px[int(c), int(c)] = rgb("FFF4C0")
    # Chips und Münzen davor
    full = new(23, 22)
    full.alpha_composite(img, (2, 0))
    fp = full.load()
    for x in range(0, 7):
        fp[x, 19] = rgb("2E9A50") if x % 2 else rgb("4ACB6E")
        fp[x, 20] = rgb("1C6A36")
    for x in range(14, 23):
        fp[x, 17] = rgb("FFD84A") if x % 2 else rgb("E0A020")
        fp[x, 18] = rgb("E0A020") if x % 2 else rgb("FFD84A")
        fp[x, 19] = rgb("FFD84A") if x % 2 else rgb("E0A020")
        fp[x, 20] = rgb("B07010")
    for x in range(7, 14):
        fp[x, 20] = rgb("9030E0") if x % 2 else rgb("B060F0")
    return outline(full)


# ------------------------------------------------------------------------------------------------------------
# Logo: Spielkarten-Fächer über "KUBY", Chips und Münzstapel an den Seiten, darunter "CASINO"
# ------------------------------------------------------------------------------------------------------------
CARD = [
    "kkkkkkkkk",
    "kWWWWWWWk",
    "kWsWWWWWk",
    "kWWWWWWWk",
    "kWWWSWWWk",
    "kWWSSSWWk",
    "kWWWSWWWk",
    "kWWWWWWWk",
    "kWWWWWWWk",
]
SUITS = {
    "club": ["..k..", ".kkk.", "kkkkk", "..k.."],
    "heart": [".r.r.", "rrrrr", ".rrr.", "..r.."],
    "spade": ["..k..", ".kkk.", "kkkkk", ".kkk."],
    "diamond": ["..r..", ".rrr.", "rrrrr", ".rrr."],
}


def card(suit):
    img = new(11, 12)
    px = img.load()
    for y in range(12):
        for x in range(11):
            edge = x in (0, 10) or y in (0, 11)
            if edge and (x, y) in ((0, 0), (10, 0), (0, 11), (10, 11)):
                continue
            px[x, y] = rgb("2A2028") if edge else rgb("F6F4F0")
    col = rgb("D81E28") if suit in ("heart", "diamond") else rgb("1E1A20")
    for r, row in enumerate(SUITS[suit]):
        for c, ch in enumerate(row):
            if ch != ".":
                px[3 + c, 4 + r] = col
    px[2, 2] = col
    return img


def chip(base, spot):
    img = new(7, 7)
    px = img.load()
    for y in range(7):
        for x in range(7):
            d = math.hypot(x - 3, y - 3)
            if d <= 3.3:
                px[x, y] = rgb(base)
            if 2.3 < d <= 3.3 and (x + y) % 2 == 0:
                px[x, y] = rgb(spot)
    px[3, 3] = rgb(spot)
    return img


def coin_stack(n):
    img = new(8, 2 * n + 1)
    px = img.load()
    for i in range(n):
        y = img.height - 2 - 2 * i
        for x in range(8):
            px[x, y] = rgb("E0A020") if x in (0, 7) else rgb("FFD84A")
            px[x, y + 1] = rgb("A86A10")
    return img


def logo():
    w, h = sb.TEX_W, sb.LOGO_H + CARD_EXT
    img = new(w, h)
    top = CARD_EXT                       # Zeile, die beim normalen Logo 0 wäre
    mid = sb.M + sb.FRAME_W // 2
    # Karten-Fächer (hinter dem Schriftzug)
    cards = new(w, h)
    for i, (suit, dx, dy) in enumerate((("club", -21, 3), ("heart", -10, 0), ("spade", 1, 0), ("diamond", 12, 3))):
        cards.alpha_composite(card(suit), (mid + dx - 1, dy + 1))
    img.alpha_composite(outline(cards))
    text = new(w, h)
    big = sb.block_word("KUBY", sb.BIG, sb.GOLD_PAL)
    text.alpha_composite(big, (sb.M + (sb.FRAME_W - big.width) // 2, top + sb.ry(0) - big.height + 1))
    small = sb.block_word("CASINO", SMALL_EXTRA, sb.SILVER_PAL)
    text.alpha_composite(small, (sb.M + (sb.FRAME_W - small.width) // 2, top + sb.ry(-2)))
    # Chips und Münzen links und rechts vom Schriftzug
    deco = new(w, h)
    y0 = top + sb.ry(0) - 9
    deco.alpha_composite(chip("1E2A6A", "8CA8F8"), (sb.M + 8, y0 - 2))
    deco.alpha_composite(coin_stack(3), (sb.M + 15, y0))
    deco.alpha_composite(chip("1E5A2A", "8CF0A8"), (sb.M + sb.FRAME_W - 15, y0 - 2))
    deco.alpha_composite(coin_stack(3), (sb.M + sb.FRAME_W - 23, y0))
    text.alpha_composite(outline(deco))
    img.alpha_composite(sb.drop_shadow(text))
    img.load()[w - 1, h - 1] = (0, 0, 0, 1)
    return img


SMALL_EXTRA = dict(sb.SMALL)
SMALL_EXTRA.update({
    "C": [".XXXXXXXXX", "XXXXXXXXXX", "XXX.......", "XXX.......", "XXX.......", "XXX.......", "XXXXXXXXXX",
          ".XXXXXXXXX"],
    "N": ["XXX....XXX", "XXXX...XXX", "XXXXX..XXX", "XXXXXX.XXX", "XXX.XXXXXX", "XXX..XXXXX", "XXX...XXXX",
          "XXX....XXX"],
    "O": [".XXXXXXXX.", "XXXXXXXXXX", "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXXXXXXXXX",
          ".XXXXXXXX."],
})


# ------------------------------------------------------------------------------------------------------------
# Zeilen-Icons (8 x 8) für die Casino-Zeilen
# ------------------------------------------------------------------------------------------------------------
ICONS = {
    "name": (["........", "..yyyy..", ".yYYYYy.", "yYkkkkYy", "yYkWWkYy", "yYkkkkYy", ".yYYYYy.", "..oooo.."],
             {"y": "C8901A", "Y": "F6C83C", "k": "1E1A20", "W": "F4F2EE", "o": "8A5A10"}),
    "chip": (["..pPPp..", ".pWPPWp.", "pPPddPPp", "PPdWWdPP", "PPdWWdPP", "pPPddPPp", ".pWPPWp.", "..pppp.."],
             {"p": "8A2070", "P": "D040A0", "W": "F4F2EE", "d": "6A1A58"}),
    "seven": (["RRRRRRR.", "rrrrrRR.", "....RR..", "...RR...", "...RR...", "..RR....", "..RR....", "..rr...."],
              {"R": "E8282E", "r": "9A1418"}),
    "dice": ([".WWWWWW.", "WWkWWWWs", "WWWWWWWs", "WWWkWWWs", "WWWWWWWs", "WWWWWkWs", ".sssssss", "........"],
             {"W": "F4F2EE", "s": "A8A4A0", "k": "1E1A20"}),
}
ICON_ORDER = ["name", "chip", "seven", "dice"]


def icon(name):
    rows, pal = ICONS[name]
    img = new(8, 8)
    img.alpha_composite(stamp(rows, {k: rgb(v) for k, v in pal.items()}), (0, 0))
    px = img.load()
    if not any(px[7, y][3] for y in range(8)):
        px[7, 7] = (0, 0, 0, 1)
    return img


def build(save):
    """save(img, name, height, ascent, char) wie in build_sb_pack.build."""
    top = frame_piece(0, 15, True, False, sb.A_TITLE, ext_top=sb.TOP_EXT)
    sparkle(top, sb.M + sb.BORDER + 3, sb.TOP_EXT + sb.BORDER + 3)
    sparkle(top, sb.M + sb.BORDER + 8, sb.TOP_EXT + sb.BORDER + 2, big=False)
    sparkle(top, sb.M + sb.FRAME_W - sb.BORDER - 4, sb.TOP_EXT + sb.BORDER + 3)
    sparkle(top, sb.M + sb.FRAME_W - sb.BORDER - 9, sb.TOP_EXT + sb.BORDER + 2, big=False)
    save(top, "c_frame_top", top.height, 14 + sb.TOP_EXT, chr(0xE180))
    slot, wheel = slot_machine(), roulette_wheel()
    for k in range(1, sb.MAX_K + 1):
        save(frame_piece(6 + 9 * k, 9, False, False, sb.A_LINE), "c_frame_mid_%d" % k, 9, 8 - 9 * k, chr(0xE180 + k))
        bot = frame_piece(6 + 9 * k, 24, False, True, sb.A_LINE, ext_bottom=DECO_EXT)
        base = bot.height - 1 - DECO_EXT - sb.BORDER + 2   # steht auf dem unteren Rand
        bot.alpha_composite(slot, (sb.M + sb.BORDER - 2, base - slot.height + 1))
        bot.alpha_composite(wheel, (sb.M + sb.FRAME_W - sb.BORDER - wheel.width + 2, base - wheel.height + 1))
        sparkle(bot, sb.M + sb.FRAME_W - sb.BORDER - 3, base - wheel.height + 1, big=False)
        save(bot, "c_frame_bottom_%d" % k, bot.height, 8 - 9 * k, chr(0xE1C0 + k))
    lg = logo()
    save(lg, "logo_casino", lg.height, lg.height - 1, chr(0xE053))
    for i, name in enumerate(ICON_ORDER):
        save(icon(name), "sb_" + name, 8, 7, chr(0xE021 + i))
