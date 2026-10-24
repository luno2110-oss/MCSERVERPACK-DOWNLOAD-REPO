"""Fisch-Grafiken für das Angeln (48 Arten) im Stil der Vanilla-Fische: 16 x 16, schräg (Kopf unten links,
Schwanz oben rechts), mit Schattierung, Flossen und Muster. Erzeugt Item-Texturen, Item-Modelle und einen
Icon-Bogen für das Dialog-Kit (auch dunkle Umrisse für noch nicht gefangene Arten).

Die Liste FISH muss zu de.kuby.kubysurvival.angeln.FishSpecies passen (gleiche IDs, gleiche Reihenfolge).
Aufruf über build_ui_pack.py (oder: python3 tools/build_fish.py <pack-ordner>).
"""
import json
import math
import os
import sys

from PIL import Image

T = (0, 0, 0, 0)
S2 = math.sqrt(2)


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, f):
    f = max(0.0, min(1.0, f))
    return tuple(round(a + (b - a) * f) for a, b in zip(c1[:3], c2[:3])) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c[:3]) + (255,)


def hsh(a, b, seed):
    """Deterministisches Rauschen 0..1 für Pixel (a, b)."""
    n = (a * 374761393 + b * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


# =====================================================================================================================
# Arten: id, Name, Gewässer, Seltenheit, kg von-bis, Form, Farben (Rücken, Körper, Bauch, Flossen), Muster, Extras
# Gewässer: fluss, ozean, warm, kalt, sumpf, mythos  ·  Seltenheit: g u s e l m
# =====================================================================================================================
FISH = [
    # ---- Flüsse & Seen ----
    ("rotauge", "Rotauge", "fluss", "g", (0.08, 0.6), "normal", ("5E6E78", "B9C3CB", "EEF2F4", "D8433A"), "", {"eye": "D83030"}),
    ("flussbarsch", "Flussbarsch", "fluss", "g", (0.1, 1.2), "normal", ("4E6A2A", "A8B24A", "E6E2B8", "D8573A"), "bars", {}),
    ("bachforelle", "Bachforelle", "fluss", "u", (0.3, 2.5), "slim", ("6A5230", "B0925A", "EAD8B0", "9A7A48"), "spots_red", {}),
    ("karpfen", "Karpfen", "fluss", "u", (1.0, 14.0), "deep", ("7A5A1E", "C49A3A", "EED48A", "A8742A"), "scales", {}),
    ("zander", "Zander", "fluss", "s", (0.8, 9.0), "slim", ("4E5E58", "9AAAA2", "E2E8E2", "7A8A82"), "bars_faint", {"eye": "E8E8D0"}),
    ("hecht", "Hecht", "fluss", "s", (1.5, 16.0), "pike", ("3E5A2A", "7E9A48", "DCE4B0", "6E8040"), "spots_light", {}),
    ("wels", "Wels", "fluss", "e", (5.0, 60.0), "catfish", ("2E2A26", "5E564A", "B8AE98", "3E3830"), "mottled", {}),
    ("stoer", "Stör", "fluss", "l", (10.0, 120.0), "sturgeon", ("4A5058", "8A929C", "D8DCE0", "5E646C"), "plates", {}),
    # ---- Ozean ----
    ("hering", "Hering", "ozean", "g", (0.05, 0.5), "slim", ("2E5A8A", "A8BED0", "EEF4F8", "7A96AE"), "", {}),
    ("makrele", "Makrele", "ozean", "g", (0.2, 1.5), "slim", ("1E6A6A", "8CC0C4", "EEF6F6", "5A8A90"), "waves", {}),
    ("kabeljau", "Kabeljau", "ozean", "u", (1.0, 12.0), "normal", ("6E5A3A", "B49C6E", "ECE2C8", "8E7A54"), "spots_dark", {}),
    ("dorade", "Dorade", "ozean", "u", (0.4, 3.0), "deep", ("5A6A7A", "BCC8D2", "F2F4F6", "8A9AA8"), "gold_band", {}),
    ("thunfisch", "Thunfisch", "ozean", "s", (10.0, 250.0), "tuna", ("1A2E5A", "6A84AA", "DDE4EC", "E2C23A"), "", {}),
    ("schwertfisch", "Schwertfisch", "ozean", "s", (20.0, 300.0), "sword", ("2A3A5A", "7A8EA8", "D8E0E8", "4A5E7A"), "", {}),
    ("mondfisch", "Mondfisch", "ozean", "e", (50.0, 1000.0), "sunfish", ("6E7680", "AEB6BE", "E2E6EA", "8A929A"), "mottled_light", {}),
    ("weisser_hai", "Weißer Hai", "ozean", "l", (300.0, 1800.0), "shark", ("4E5862", "8E98A2", "F2F2F2", "5E6872"), "", {}),
    # ---- Warme Gewässer ----
    ("clownfisch", "Clownfisch", "warm", "g", (0.05, 0.3), "deep", ("C8501A", "F27A2A", "FFB070", "1E1E1E"), "clown", {}),
    ("doktorfisch", "Doktorfisch", "warm", "g", (0.2, 1.0), "deep", ("1A3A9A", "3A6AE0", "6A9AF0", "F2D23A"), "doctor", {}),
    ("papageifisch", "Papageifisch", "warm", "u", (1.0, 9.0), "normal", ("1A8A7A", "3AC8A8", "9AF0D8", "E85AA8"), "scales_pink", {}),
    ("kugelfisch", "Kugelfisch", "warm", "u", (0.3, 3.0), "puffer", ("A87A1E", "E2B23A", "F8E4A8", "6AA8D8"), "spines", {}),
    ("feuerfisch", "Feuerfisch", "warm", "s", (0.3, 1.5), "lion", ("8A1E1E", "E2583A", "F8D2C0", "F0A890"), "lion", {}),
    ("mahi_mahi", "Mahi-Mahi", "warm", "s", (5.0, 40.0), "mahi", ("1E7A3A", "5AC83A", "F2E25A", "2A9AD8"), "spots_blue", {}),
    ("mantarochen", "Mantarochen", "warm", "e", (200.0, 2000.0), "ray", ("1E2632", "3A4656", "E8ECF0", "2A3442"), "", {}),
    ("marlin", "Blauer Marlin", "warm", "l", (100.0, 800.0), "marlin", ("1A3A8A", "4A7AC8", "DDE6F2", "2A5AB0"), "bars_blue", {}),
    # ---- Kalte Gewässer ----
    ("lodde", "Lodde", "kalt", "g", (0.02, 0.1), "slim", ("4A7A6A", "A8C8BA", "EEF6F2", "7AA090"), "", {}),
    ("polardorsch", "Polardorsch", "kalt", "g", (0.1, 0.8), "normal", ("5A6A6E", "A8B4B6", "E8EEEE", "7E8A8E"), "spots_dark", {}),
    ("saibling", "Seesaibling", "kalt", "u", (0.3, 4.0), "slim", ("3A5A4A", "7A9A7E", "E8603A", "E8E8E0"), "spots_cream", {"belly_glow": True}),
    ("lachs", "Lachs", "kalt", "u", (2.0, 20.0), "normal", ("3A4E6A", "A8B8C8", "F2C8C0", "6E7E92"), "spots_dark", {}),
    ("heilbutt", "Heilbutt", "kalt", "s", (5.0, 200.0), "flat", ("5A4A3A", "8E7A5E", "C8B898", "6E5E48"), "mottled", {}),
    ("steinbeisser", "Steinbeißer", "kalt", "s", (2.0, 20.0), "eelfish", ("3A3E46", "6E7480", "B8BEC6", "4E545E"), "bars", {}),
    ("groenlandhai", "Grönlandhai", "kalt", "e", (200.0, 1000.0), "shark", ("3E4248", "6A7076", "9AA0A6", "4A5056"), "mottled", {"eye": "C8E8F0"}),
    ("kristallforelle", "Kristallforelle", "kalt", "l", (1.0, 12.0), "slim", ("4AA8D8", "A8E4F8", "F2FCFF", "7AD0F0"), "crystal", {}),
    # ---- Sumpf & Höhlen ----
    ("schlammpeitzger", "Schlammpeitzger", "sumpf", "g", (0.02, 0.15), "eelfish", ("4A3A22", "8A7244", "C8B488", "6A5634"), "stripes_long", {}),
    ("moderlieschen", "Moderlieschen", "sumpf", "g", (0.01, 0.05), "slim", ("5A6A4A", "B8C2A8", "EEF2E4", "8E9A7E"), "line", {}),
    ("aal", "Aal", "sumpf", "u", (0.3, 5.0), "eel", ("2E3A1E", "5E6A3A", "C8C49A", "3E4A2A"), "", {}),
    ("schleie", "Schleie", "sumpf", "u", (0.3, 6.0), "deep", ("2E4A1E", "6E8A3A", "C8C47A", "4A6A2A"), "", {"eye": "D83030"}),
    ("grottenolm", "Grottenolm", "sumpf", "s", (0.01, 0.05), "olm", ("C8A8A0", "F0D8D0", "FFF0EA", "F07A8A"), "", {}),
    ("hoehlensalmler", "Höhlensalmler", "sumpf", "s", (0.02, 0.2), "normal", ("C89A9A", "F0C8C8", "FFEAEA", "E8B0B0"), "", {"eye": None}),
    ("lungenfisch", "Lungenfisch", "sumpf", "e", (2.0, 20.0), "eel", ("3A3A2A", "6E6A4A", "A8A07A", "5A563E"), "spots_dark", {"fins": True}),
    ("gluehwels", "Glühwels", "sumpf", "l", (3.0, 40.0), "catfish", ("142028", "2A3E4A", "4A6070", "1E2E38"), "glow", {}),
    # ---- Mythen & Zone ----
    ("gluecks_karpfen", "Glücks-Karpfen", "mythos", "s", (3.0, 25.0), "deep", ("2E5A1E", "5E8A2E", "E8C83A", "E8B82A"), "lucky", {}),
    ("sternenhecht", "Sternenhecht", "mythos", "s", (2.0, 18.0), "pike", ("141E4A", "2A3A8A", "6A7AD8", "3A4AB0"), "stars", {}),
    ("geisterfisch", "Geisterfisch", "mythos", "e", (0.5, 6.0), "normal", ("A8B8C8", "DCE6F0", "FFFFFF", "C8D8E8"), "ghost", {"eye": "4AD8F0"}),
    ("lavaforelle", "Lavaforelle", "mythos", "e", (1.0, 10.0), "slim", ("6A1E0A", "E2581E", "FFC23A", "FF8A2A"), "lava", {}),
    ("kraken_junges", "Kraken-Junges", "mythos", "l", (5.0, 80.0), "squid", ("5A1E6A", "A84AC8", "E8A8F0", "7A2E8A"), "spots_light", {}),
    ("phoenixfisch", "Phönixfisch", "mythos", "l", (2.0, 30.0), "phoenix", ("A81E1E", "F25A1E", "FFD23A", "FFB02A"), "flame", {}),
    ("leviathan", "Leviathan", "mythos", "m", (500.0, 5000.0), "serpent", ("0E3A3A", "1E6A6A", "6AC8B8", "2AA898"), "scales_dark", {}),
    ("ur_koi", "Ur-Koi", "mythos", "m", (5.0, 50.0), "deep", ("C8C8C8", "FFFFFF", "FFFFFF", "F2D23A"), "koi", {}),
]

# Körperformen: Länge der Achse (Kopf bis Schwanzende), Höhe, Profil
SHAPES = {
    "normal": dict(L=15.5, H=2.9, tail=3.4, fork=True, dorsal=1.4, front=0.35),
    "slim": dict(L=16.0, H=2.4, tail=3.2, fork=True, dorsal=1.1, front=0.3),
    "pike": dict(L=16.5, H=2.2, tail=3.0, fork=False, dorsal=1.2, front=0.25, dorsal_pos=0.72),
    "deep": dict(L=14.5, H=3.7, tail=3.2, fork=True, dorsal=1.6, front=0.42),
    "tuna": dict(L=15.5, H=3.2, tail=3.6, fork=True, dorsal=1.6, front=0.4, crescent=True),
    "catfish": dict(L=16.0, H=2.7, tail=2.6, fork=False, dorsal=0.9, front=0.2, whiskers=True),
    "sturgeon": dict(L=16.5, H=2.4, tail=3.4, fork=True, dorsal=1.0, front=0.2, snout=True),
    "sword": dict(L=13.5, H=2.8, tail=3.6, fork=True, dorsal=2.0, front=0.35, bill=4.0, crescent=True),
    "marlin": dict(L=13.5, H=2.8, tail=3.6, fork=True, dorsal=2.8, front=0.35, bill=4.0, crescent=True, sail=True),
    "sunfish": dict(L=11.0, H=4.2, tail=1.2, fork=False, dorsal=0.0, front=0.5, sunfish=True),
    "shark": dict(L=16.5, H=2.6, tail=3.8, fork=True, dorsal=3.4, front=0.3, crescent=True, shark=True,
                  dorsal_pos=0.42),
    "puffer": dict(L=12.0, H=4.4, tail=2.4, fork=False, dorsal=0.8, front=0.5, round=True),
    "lion": dict(L=14.0, H=3.0, tail=3.0, fork=False, dorsal=3.2, front=0.4, spiky=True),
    "mahi": dict(L=15.5, H=3.0, tail=3.4, fork=True, dorsal=1.6, front=0.6, blunt=True),
    "ray": dict(L=13.0, H=6.0, tail=5.0, fork=False, dorsal=0.0, front=0.5, ray=True),
    "flat": dict(L=14.0, H=4.2, tail=2.6, fork=False, dorsal=0.8, front=0.5, flat=True),
    "eelfish": dict(L=17.0, H=1.9, tail=1.8, fork=False, dorsal=0.8, front=0.2, longfin=True),
    "eel": dict(L=18.0, H=1.5, tail=0.0, fork=False, dorsal=0.7, front=0.15, eel=True, longfin=True),
    "olm": dict(L=17.0, H=1.5, tail=0.0, fork=False, dorsal=0.5, front=0.15, eel=True, olm=True),
    "squid": dict(L=11.5, H=2.5, tail=0.0, fork=False, dorsal=0.0, front=0.5, squid=True),
    "phoenix": dict(L=15.0, H=2.9, tail=4.2, fork=True, dorsal=2.6, front=0.35, flamefins=True),
    "serpent": dict(L=17.0, H=1.8, tail=2.0, fork=False, dorsal=1.6, front=0.2, eel=True, spikes=True),
}


def body_profile(t, shape):
    """Halbe Körperhöhe als Anteil (0..1) an Achsposition t (0 = Kopf, 1 = Schwanzwurzel)."""
    if shape.get("round"):
        return math.sqrt(max(0.0, 1 - (2 * t - 1) ** 2))
    if shape.get("spikes"):
        return (1.25 if t < 0.12 else 1.0) * min(1.0, t * 9) * (1 - 0.5 * t)
    if shape.get("eel"):
        return min(1.0, t * 7) * (1 - 0.55 * t)
    if shape.get("ray"):
        return max(0.0, 1 - abs(t - 0.42) / 0.58) ** 0.9
    if shape.get("sunfish"):
        return math.sqrt(max(0.0, 1 - ((t - 0.5) / 0.52) ** 2))
    if shape.get("squid"):
        if t < 0.22:
            return 0.62
        return min(1.0, 0.75 + (t - 0.22) * 2) * (1 - max(0.0, (t - 0.55) / 0.45) ** 1.3)
    front = shape["front"]
    if shape.get("blunt"):
        head = min(1.0, 0.75 + t * 1.2)
    else:
        head = math.sin(min(1.0, t / front) * math.pi / 2) ** 0.8
    back = 1 - max(0.0, (t - front) / (1 - front)) ** 1.6 * 0.72
    return head * back


def render(fish, silhouette=False):
    fid, name, water, rarity, kg, shape_id, cols, pattern, extra = fish
    sh = SHAPES[shape_id]
    c_back, c_body, c_belly, c_fin = (rgb(c) for c in cols)
    seed = sum(ord(ch) for ch in fid)
    L, H = sh["L"], sh["H"]
    tail_len = sh["tail"]
    body_len = L - tail_len
    bill = sh.get("bill", 0.0)
    head_x, head_y = 1.0 + (bill / S2 if bill else 0.0), 15.0 - (bill / S2 if bill else 0.0)
    if sh.get("eel") or sh.get("serpent"):
        head_x, head_y = 0.6, 15.4

    G = 32               # Arbeitsraster; danach wird der Fisch auf 16 x 16 zentriert
    head_x += 6
    head_y += 10
    img = Image.new("RGBA", (G, G), T)
    mask = {}           # (x, y) -> Teil: body, fin, tail, bill, eye ...
    info = {}
    for y in range(G):
        for x in range(G):
            px, py = x + 0.5, y + 0.5
            u = ((px - head_x) - (py - head_y)) / S2          # entlang der Achse (Kopf -> Schwanz)
            v = ((px - head_x) + (py - head_y)) / S2          # quer: + = Bauch (unten rechts), - = Rücken
            if sh.get("eel"):
                v -= 0.9 * math.sin(u / L * math.pi * 2.0) if not sh.get("olm") else 0.5 * math.sin(u / L * math.pi * 1.5)
            part = None
            if 0 <= u <= body_len:
                t = u / body_len
                hh = H * body_profile(t, sh)
                if sh.get("flat"):
                    hh = H * math.sqrt(max(0.0, 1 - (2 * t - 1) ** 2))
                if abs(v) <= hh:
                    part = "body"
                    info[(x, y)] = (t, v / max(hh, 0.01))
                # Rückenflosse
                dpos = sh.get("dorsal_pos", 0.48)
                dl = 0.3 if not sh.get("sail") else 0.6
                if part is None and sh["dorsal"] > 0 and v < 0 and abs(t - dpos) < dl / 2:
                    ft = 1 - abs(t - dpos) / (dl / 2)
                    if sh.get("shark"):
                        a0 = dpos - dl / 2
                        ft = (t - a0) / (dl * 0.45) if t < a0 + dl * 0.45 else max(0.0, 1 - (t - a0 - dl * 0.45) / (dl * 0.25))
                    if -v <= hh + sh["dorsal"] * max(0.0, ft):
                        part = "fin"
                if part is None and sh.get("longfin") and v < 0 and 0.25 < t < 0.95 and -v <= hh + 0.8:
                    part = "fin"
                if part is None and sh.get("sunfish") and abs(t - 0.78) < 0.1 and abs(v) <= hh + 2.6 * (1 - abs(t - 0.78) / 0.1):
                    part = "fin"
                if part is None and sh.get("spiky") and abs(v) <= hh + sh["dorsal"] and 0.2 < t < 0.85:
                    if (x + y + seed) % 2 == 0 and hsh(x, y, seed) < 0.7:
                        part = "fin"
                # Brust-/Bauchflosse
                if part is None and not sh.get("eel") and not sh.get("ray") and v > 0 and abs(t - 0.38) < 0.1 \
                        and v <= hh + 1.0:
                    part = "fin"
                if part is None and sh.get("fins") and abs(t - 0.3) < 0.08 and abs(v) <= hh + 1.4:
                    part = "fin"
                if part is None and sh.get("olm") and abs(abs(v) - hh - 0.8) < 0.8 and (abs(t - 0.22) < 0.05 or abs(t - 0.6) < 0.05):
                    part = "fin"
                if part is None and sh.get("spikes") and v < 0 and -v <= hh + 1.1 and (int(u) % 3 == 0) and t > 0.1:
                    part = "fin"
                if part is None and sh.get("ray") and abs(v) <= hh:
                    part = "body"
            # Schwanz
            if part is None and body_len < u <= L and tail_len > 0:
                tt = (u - body_len) / tail_len
                spread = 0.8 + tt * (H * 0.95 if not sh.get("flamefins") else H * 1.2)
                if sh.get("ray"):
                    if abs(v) <= 0.6:
                        part = "fin"
                elif sh.get("crescent"):
                    if abs(v) <= spread and (abs(v) > 0.9 * (tt * H) - 0.4 or tt < 0.35):
                        part = "tail"
                elif sh["fork"]:
                    if abs(v) <= spread and not (tt > 0.6 and abs(v) < (tt - 0.6) * 3.0):
                        part = "tail"
                else:
                    if abs(v) <= 0.8 + tt * H * 0.7:
                        part = "tail"
            # Schwert / Schnauze
            if part is None and bill and -bill <= u < 0 and abs(v) <= 0.55:
                part = "bill"
            if part is None and sh.get("snout") and -1.5 <= u < 0 and abs(v) <= 0.6:
                part = "bill"
            if part is None and sh.get("squid") and 0.78 * body_len < u <= body_len + 0.5 and \
                    abs(v) <= 1.0 + (u - 0.78 * body_len) * 0.9:
                part = "fin"
            if part:
                mask[(x, y)] = part

    if not mask:
        return img
    tip = min((k for k, p in mask.items() if p == "body"), key=lambda k: (k[0] - k[1]))
    if sh.get("whiskers"):
        tx, ty = tip
        for dx, dy in ((-1, -1), (-1, -2), (-1, -3), (0, -4), (1, 1), (2, 1), (3, 2), (4, 2)):
            if (tx + dx, ty + dy) not in mask:
                mask[(tx + dx, ty + dy)] = "whisker"
    if sh.get("squid"):
        # Fangarme vorne (unten links), wellig
        tx, ty = tip
        for arm in range(4):
            ax, ay = tx + arm - 1, ty + 1 - arm
            for k in range(1, 5):
                wob = (k + arm) % 2
                p2 = (ax - k + (0 if arm % 2 else wob), ay + k - (wob if arm % 2 else 0))
                if p2 not in mask:
                    mask[p2] = "tentacle"
    # Farben
    px = img.load()
    for (x, y), part in mask.items():
        if part == "body":
            t, vn = info[(x, y)]
            if vn < -0.35:
                c = mix(c_back, c_body, (vn + 1) / 0.65 * 0.6)
            elif vn > 0.35:
                c = mix(c_body, c_belly, (vn - 0.35) / 0.65 * 1.2)
            else:
                c = c_body
            c = pattern_color(pattern, c, x, y, t, vn, seed, cols, extra)
            if extra.get("belly_glow") and vn > 0.2:
                c = mix(c, rgb(cols[2]), 0.7)
        elif part in ("fin", "tail", "tentacle"):
            c = c_fin if part != "tentacle" else c_body
            if pattern == "flame":
                c = mix(rgb("FFD23A"), rgb("E8381E"), hsh(x, y, seed))
            if pattern == "lion" and part == "fin":
                c = rgb("F8E0D0") if (x + y) % 2 else rgb("C8381E")
            if pattern == "crystal":
                c = mix(c, rgb("FFFFFF"), 0.35)
        elif part == "bill":
            c = mix(c_back, c_body, 0.5)
        elif part == "whisker":
            c = shade(c_belly, 0.8)
        else:
            c = c_body
        px[x, y] = c
    # Rand: Pixel an der Außenkante etwas dunkler (wie Vanilla)
    edge = []
    for (x, y), part in mask.items():
        n = sum(1 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (x + dx, y + dy) not in mask)
        if n:
            edge.append((x, y, n))
    for x, y, n in edge:
        part = mask[(x, y)]
        f = 0.72 if part == "body" else 0.82
        # Rücken- und Kopfseite (oben links) dunkler, Bauchseite etwas weniger
        if part == "body":
            t, vn = info[(x, y)]
            f = 0.66 if vn < 0 else 0.8
        px[x, y] = shade(px[x, y], f)
    # Auge
    eye = extra.get("eye", "141414")
    if eye is not None and not sh.get("ray") and not sh.get("squid"):
        best = None
        for (x, y), part in mask.items():
            if part != "body":
                continue
            t, vn = info[(x, y)]
            score = abs(t - (0.12 if not sh.get("eel") else 0.06)) * 3 + abs(vn + 0.25)
            if best is None or score < best[0]:
                best = (score, x, y)
        if best:
            _, ex, ey = best
            px[ex, ey] = rgb(eye) if eye != "141414" else rgb("141414")
            if eye != "141414" and (ex + 1, ey) in mask:
                pass
    if sh.get("ray"):
        for (x, y), part in mask.items():
            if part == "body" and info[(x, y)][1] > 0.55:
                px[x, y] = mix(px[x, y], c_belly, 0.6)
    if silhouette:
        dark = rgb("1A2430")
        edge_c = rgb("2E3E50")
        sil = Image.new("RGBA", (G, G), T)
        sp = sil.load()
        for (x, y) in mask:
            sp[x, y] = dark
        for x, y, n in edge:
            sp[x, y] = edge_c
        img = sil
    return center16(img)


def center16(img):
    bbox = img.getchannel("A").getbbox()
    out = Image.new("RGBA", (16, 16), T)
    if not bbox:
        return out
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    if w > 16 or h > 16:
        print("WARNUNG: Fisch größer als 16 px:", w, h)
    ox = (16 - w) // 2
    oy = (16 - h + 1) // 2
    out.alpha_composite(img.crop(bbox), (max(0, ox), max(0, oy)))
    return out


def pattern_color(pattern, c, x, y, t, vn, seed, cols, extra):
    if pattern == "bars" and int(t * 9) % 2 == 0 and vn < 0.5:
        return shade(c, 0.7)
    if pattern == "bars_faint" and int(t * 8) % 2 == 0 and vn < 0.4:
        return shade(c, 0.85)
    if pattern == "bars_blue" and int(t * 10) % 2 == 0 and vn < 0.3:
        return mix(c, rgb("8AB8F0"), 0.45)
    if pattern == "spots_red" and hsh(x, y, seed) < 0.22 and abs(vn) < 0.7:
        return rgb("C83020") if hsh(y, x, seed) < 0.5 else rgb("3A2A1A")
    if pattern == "spots_dark" and hsh(x, y, seed) < 0.2 and vn < 0.5:
        return shade(c, 0.6)
    if pattern == "spots_light" and hsh(x, y, seed) < 0.25 and vn < 0.6:
        return mix(c, rgb("F2F2D8"), 0.6)
    if pattern == "spots_cream" and hsh(x, y, seed) < 0.22 and vn < 0.2:
        return rgb("F2E8C8")
    if pattern == "spots_blue" and hsh(x, y, seed) < 0.18 and abs(vn) < 0.6:
        return rgb("2A8AD8")
    if pattern == "scales" and (x + y) % 2 == 0 and abs(vn) < 0.6:
        return shade(c, 0.88)
    if pattern == "scales_pink" and (x + 2 * y) % 3 == 0 and abs(vn) < 0.7:
        return rgb("E87AB8")
    if pattern == "scales_dark" and (x + y) % 2 == 0:
        return shade(c, 0.8)
    if pattern == "waves" and vn < -0.05 and (x * 2 + y + int(3 * math.sin(x))) % 4 == 0:
        return rgb("1A2E30")
    if pattern == "gold_band" and abs(vn + 0.1) < 0.2:
        return rgb("E8C23A")
    if pattern == "clown" and (abs(t - 0.2) < 0.08 or abs(t - 0.55) < 0.08):
        return rgb("FFFFFF") if abs(t - 0.2) < 0.06 or abs(t - 0.55) < 0.06 else rgb("1E1E1E")
    if pattern == "doctor" and t > 0.8:
        return rgb("F2D23A")
    if pattern == "lion" and int(t * 10) % 2 == 0:
        return rgb("F8E0D0")
    if pattern == "mottled" and hsh(x, y, seed) < 0.35:
        return shade(c, 0.78)
    if pattern == "mottled_light" and hsh(x, y, seed) < 0.3:
        return mix(c, rgb("FFFFFF"), 0.3)
    if pattern == "plates" and vn < -0.3 and x % 2 == 0:
        return mix(c, rgb("D8D2C0"), 0.6)
    if pattern == "crystal" and (x + y) % 3 == 0:
        return mix(c, rgb("FFFFFF"), 0.55)
    if pattern == "stripes_long" and abs(vn) < 0.2:
        return shade(c, 0.65)
    if pattern == "line" and abs(vn) < 0.18:
        return mix(c, rgb("8AA8C8"), 0.5)
    if pattern == "glow" and hsh(x, y, seed) < 0.22:
        return rgb("4AF0E8")
    if pattern == "lucky" and vn > -0.2 and (x + y) % 2 == 0:
        return mix(c, rgb("F2D23A"), 0.6)
    if pattern == "stars" and hsh(x, y, seed) < 0.18:
        return rgb("FFF4A0")
    if pattern == "ghost":
        return mix(c, rgb("E8F4FF"), 0.2 + 0.3 * hsh(x, y, seed))
    if pattern == "lava" and hsh(x, y, seed) < 0.3:
        return rgb("3A0E06") if hsh(y, x, seed) < 0.4 else rgb("FFE27A")
    if pattern == "flame" and vn < 0:
        return mix(c, rgb("FFD23A"), hsh(x, y, seed) * 0.6)
    if pattern == "koi" and hsh(x // 2, y // 2, seed) < 0.4:
        return rgb("E8381E") if hsh(y, x, seed) < 0.7 else rgb("F2B82A")
    return c


# Arten, die nachts doppelt so oft beißen
NIGHT = {"wels", "aal", "gluehwels", "sternenhecht", "geisterfisch", "lungenfisch", "zander", "groenlandhai"}
WATERS = {"fluss": "FLUSS", "ozean": "OZEAN", "warm": "WARM", "kalt": "KALT", "sumpf": "SUMPF", "mythos": "MYTHOS"}
RARITIES = {"g": "GEWOEHNLICH", "u": "UNGEWOEHNLICH", "s": "SELTEN", "e": "EPISCH", "l": "LEGENDAER", "m": "MYTHISCH"}
JAVA_OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "KubySurvival-v1.0.0", "KubySurvival", "src",
                        "main", "java", "de", "kuby", "kubysurvival", "angeln", "FishSpecies.java")


def write_java():
    L = ["package de.kuby.kubysurvival.angeln;", "", "import java.util.List;", "",
         "// ERZEUGT von tools/build_fish.py - nicht von Hand ändern.", "",
         "/** Fischart: ID (= Textur kuby:item/fish/<id>), Name, Gewässer, Seltenheit, Gewicht von-bis, nachtaktiv. */",
         "public record FishSpecies(String id, String name, Water water, Rarity rarity, double minKg, double maxKg,",
         "                          boolean night, int index) {", "",
         "    public static final List<FishSpecies> ALL = List.of("]
    rows = []
    for i, f in enumerate(FISH):
        fid, name, water, rarity, kg = f[:5]
        rows.append('            new FishSpecies("%s", "%s", Water.%s, Rarity.%s, %s, %s, %s, %d)' % (
            fid, name, WATERS[water], RARITIES[rarity], repr(float(kg[0])), repr(float(kg[1])),
            "true" if fid in NIGHT else "false", i))
    L.append(",\n".join(rows) + ");")
    L += ["", "    public static FishSpecies byId(String id) {",
          "        for (FishSpecies s : ALL) if (s.id.equals(id)) return s;",
          "        return null;", "    }", "", "    public static List<FishSpecies> of(Water w) {",
          "        return ALL.stream().filter(s -> s.water == w).toList();", "    }", "}", ""]
    with open(JAVA_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(L))


def build(out):
    write_java()
    tex = os.path.join(out, "assets/kuby/textures/item/fish")
    models = os.path.join(out, "assets/kuby/models/item/fish")
    items = os.path.join(out, "assets/kuby/items/fish")
    for d in (tex, models, items):
        os.makedirs(d, exist_ok=True)
    sheet = Image.new("RGBA", (16 * 8, 16 * 12), T)
    for i, f in enumerate(FISH):
        img = render(f)
        img.save(os.path.join(tex, f[0] + ".png"))
        with open(os.path.join(models, f[0] + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/fish/" + f[0]}}, fh)
        with open(os.path.join(items, f[0] + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/fish/" + f[0]}}, fh)
        sheet.alpha_composite(img, ((i % 8) * 16, (i // 8) * 16))
        sheet.alpha_composite(render(f, silhouette=True), ((i % 8) * 16, (6 + i // 8) * 16))
    return sheet


if __name__ == "__main__":
    s = build(sys.argv[1])
    s.save(os.path.join(sys.argv[1], "fish_sheet_preview.png"))
