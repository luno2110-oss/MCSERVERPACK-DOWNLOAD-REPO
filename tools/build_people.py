#!/usr/bin/env python3
"""Figuren-Baukasten (v2.07, Wunsch des Besitzers: „mehrere Varianten für alle NPCs – Hautfarbe, Haare, Anzug usw.;
Security-Guards und normale Menschen zum freien Platzieren; stell dir vor, du arbeitest an einem GTA-Titel“).

Jede Figur wird aus Bausteinen zusammengesetzt und als 64x64-Skin gemalt (Layout wie Minecraft, Arme 3 px bei
schlanken, 4 px bei breiten Figuren):

* Haut: sechs Töne mit eigener Rampe (Schatten, Grund, Licht, Wangen, Lippen)
* Gesicht: Brauen, Augen (Iris-Farben), Nase, Mund (neutral, Lächeln, traurig); Frauen mit Wimpern und Rouge
* Frisuren: kurz, Seitenscheitel, zurückgegelt, Stoppel, Glatze, lang, Bob, Dutt, Zopf, Afro, Locken, zerzaust
* Bärte: Stoppeln, Schnurrbart, Kinnbart, Vollbart, Zwirbelbart
* Kleidung: Anzug (Krawatte, Fliege, offen), Security (Abzeichen, Ohrhörer), Abendkleid, Cocktailkleid, T-Shirt
  und Jeans, Lederjacke, Bluse und Hose, Weste (Kellner, Kassierer, Losbude mit Streifen), Strickjacke
* Zubehör: Sonnenbrille, Brille, Kette, Ohrringe, Augenschirm (Kassierer), Strohhut (Losbude)

Licht von links oben: die linke Spalte jeder Vorderseite eine Stufe heller, die rechte eine dunkler, Rücken dunkler,
Oberseite heller. Die zweite Ebene (Jacke, Ärmel, Hose, Hut) gibt Tiefe: Revers, Rocksaum, Haarvolumen, Brillen.

Je Figur ein zweiter Skin mit geschlossenen Augen (<id>_b): die Figuren blinzeln im Spiel. Dazu die Blinzel-Fassungen
der handgemalten Casino-Figuren (Kassierer, Croupier, Dealer, Mia) und Requisiten für die Hand (Gläser, Handy,
Geldbündel, Lose, unsichtbar für Gesten).

Aufruf: python3 tools/build_people.py [pack] [--sheet bild.png]
"""
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from build_skins import UV  # noqa: E402

T = (0, 0, 0, 0)


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def ramp(*hexes):
    return [rgb(h) for h in hexes]


# =====================================================================================================================
# Farben
# =====================================================================================================================

# Haut: Schatten, Grund, Licht, Wange, Lippe/Mund, Lid (geschlossen)
TONES = {
    "porzellan": ("E6BCA8", "F7D8C8", "FCE8DC", "F2B4A8", "C47A72", "DDAE9C"),
    "hell": ("DCAA8C", "F0C6A8", "F8D6BE", "E8A890", "B46E62", "D2A086"),
    "oliv": ("C2905E", "DAA878", "E6BC90", "D08E6E", "9E5E48", "BC8C60"),
    "braun": ("9C6640", "B67C52", "C69064", "B06C50", "7A4434", "966040"),
    "dunkel": ("6A4028", "835032", "966244", "8A4A36", "5A2E22", "684028"),
    "tief": ("48291A", "5C3624", "6E4630", "6A3A2A", "3E2018", "4A2C1E"),
}
# Haare: dunkel, Grund, Licht
HAIR = {
    "schwarz": ramp("120E14", "221C24", "3A3240"),
    "dunkelbraun": ramp("2A1A10", "40281A", "5A3C26"),
    "braun": ("52341C", "74502E", "946A40"),
    "kastanie": ramp("5E200E", "862E16", "AA4422"),
    "rot": ramp("8A3010", "B4481C", "D8682E"),
    "blond": ramp("A6823A", "CCA656", "E8CC86"),
    "platin": ramp("BEB8A6", "DCD6C6", "F2EEE2"),
    "grau": ramp("6A6A70", "96969C", "BEBEC4"),
}
HAIR = {k: (v if isinstance(v[0], tuple) else ramp(*v)) for k, v in HAIR.items()}
EYES = {"braun": rgb("4A2E1C"), "blau": rgb("3A6AB0"), "gruen": rgb("3E7A4A"), "grau": rgb("66707C"), "dunkel": rgb("2A1C14"),
        "bernstein": rgb("8A5A1E")}
WHITE = rgb("F4F2EE")
LASH = rgb("2A1C22")

CLOTH = {   # dunkel, Grund, Licht
    "schwarz": ramp("101014", "1E1E24", "32323A"),
    "anthrazit": ramp("2A2C32", "3E4048", "565862"),
    "marine": ramp("141C34", "1F2A4C", "2E3E6A"),
    "bordeaux": ramp("4A0E1C", "6A1628", "8A2236"),
    "creme": ramp("BEB29A", "DCD2BA", "F0E8D4"),
    "grau": ramp("5A5C62", "76787E", "94969C"),
    "tweed": ramp("4E3A26", "6A5034", "866846"),
    "rot": ramp("7A0C18", "AA1426", "D02838"),
    "smaragd": ramp("0A4A30", "106844", "1A8A5A"),
    "royal": ramp("142E7A", "1E44A6", "3060CC"),
    "violett": ramp("3E1E5A", "56307A", "70449A"),
    "jeans": ramp("2A4466", "3A5C86", "5478A6"),
    "leder": ramp("3A2014", "52301E", "6E442A"),
    "weiss": ramp("C8CCD4", "E6E8EE", "F8F8FA"),
    "hellblau": ramp("8EA6C4", "B0C6E0", "D0E0F2"),
    "senf": ramp("8A6A1A", "B48C28", "D4AC40"),
    "gruen": ramp("1E4A26", "2E6838", "428A4E"),
    "beige": ramp("8E7A5C", "AC9674", "C8B492"),
    "gold": ramp("8A6416", "C49A2C", "E8C658"),
}
GOLD = ramp("8A6416", "C49A2C", "F0D068")
SHOE = {"schwarz": ramp("0E0C10", "1C1A20", "34303A"), "braun": ramp("3A2212", "5A361E", "7A4C2C"),
        "weiss": ramp("A8AAB0", "E2E4E8", "F8F8FA"), "rot": ramp("6A0C16", "A01826", "C83040"),
        "gold": ramp("8A6416", "C49A2C", "E8C658"), "nude": ramp("B48C70", "D2AA8C", "E6C4A8")}


def lighter(c, k=1.18):
    return tuple(min(255, int(v * k)) for v in c[:3]) + (c[3],)


def darker(c, k=0.78):
    return tuple(int(v * k) for v in c[:3]) + (c[3],)


# =====================================================================================================================
# Maler
# =====================================================================================================================

class Skin:
    def __init__(self, slim):
        self.img = Image.new("RGBA", (64, 64), T)
        self.slim = slim

    def arm(self, which):
        """rarm/larm in der passenden Breite (schlank 3 px, breit 4 px)."""
        return which if self.slim else which + "4"

    def put(self, part, side, x, y, c):
        x0, y0, w, h = UV[part][side]
        if 0 <= x < w and 0 <= y < h and c is not None:
            self.img.putpixel((x0 + x, y0 + y), c)

    def size(self, part, side):
        return UV[part][side][2], UV[part][side][3]

    def fill(self, part, side, c):
        w, h = self.size(part, side)
        for y in range(h):
            for x in range(w):
                self.put(part, side, x, y, c)

    def box(self, part, r, rows=None):
        """Teil mit Rampe r (dunkel, Grund, Licht). Licht von links oben: die Teile auf der linken Bildseite (rechter
        Arm, rechtes Bein) bekommen links eine Lichtkante, die rechten rechts eine Schattenkante; Rücken und abgewandte
        Seiten im Grundton, Oberseite hell, Unterseite dunkel."""
        lit = part.startswith("r") or part in ("body", "jacket")
        shadowed = part.startswith("l") or part in ("body", "jacket")
        for side in ("front", "back", "right", "left", "top", "bottom"):
            w, h = self.size(part, side)
            for y in range(h):
                if rows is not None and (side in ("top", "bottom") or y not in rows):
                    continue
                for x in range(w):
                    c = r[1]
                    if side == "top":
                        c = r[2]
                    elif side == "bottom":
                        c = r[0]
                    elif side == "front":
                        if x == 0 and lit:
                            c = r[2]
                        elif x == w - 1 and shadowed:
                            c = r[0]
                    elif side == "left" and shadowed:
                        c = r[0]
                    self.put(part, side, x, y, c)

    def pattern(self, part, side, rows, pal, y0=0):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch not in (" ", "."):
                    self.put(part, side, x, y0 + y, pal[ch])

    def mirror_side(self, part, rows, pal):
        """Muster für die rechte Kopfseite (x 7 grenzt ans Gesicht) und gespiegelt für die linke."""
        self.pattern(part, "right", rows, pal)
        self.pattern(part, "left", [r[::-1] for r in rows], pal)


# =====================================================================================================================
# Kopf: Haut, Gesicht, Haare, Bart, Zubehör
#
# Gesichtsraster wie beim handgemalten Dealer: Haare Zeilen 0-1, Stirn 2, Brauen 3, Augen 4 (außen Weiß, innen Iris),
# Nase 5, Mund 6, Kinn 7. Frauen: Wimpernlinie 3, Augen 4, Rouge 5, Lippen 6.
# =====================================================================================================================

def tone_ramp(tone):
    sh, base, li, cheek, lip, lid = (rgb(h) for h in TONES[tone])
    return dict(sh=sh, base=base, li=li, cheek=cheek, lip=lip, lid=lid)


def head_skin(s, tone):
    t = tone_ramp(tone)
    for side in ("front", "back", "right", "left", "top"):
        s.fill("head", side, t["base"])
    s.fill("head", "bottom", t["sh"])
    # Kinn und Kieferlinie im Schatten, Licht links auf der Stirn
    s.pattern("head", "front", ["........", "........", "L.......", "........", "........", "........",
                                "s......s", "ss....ss"], {"s": t["sh"], "L": t["li"]})
    for side, ex in (("right", 3), ("left", 4)):          # Ohr: Ohrmuschel im Schatten, Rand hell
        s.put("head", side, ex, 3, t["li"])
        s.put("head", side, ex, 4, t["sh"])
        s.put("head", side, ex, 5, t["sh"])
    for x in range(8):                                    # Hals-Schatten unten an den Seiten und hinten
        s.put("head", "right", x, 7, t["sh"])
        s.put("head", "left", x, 7, t["sh"])
        s.put("head", "back", x, 7, t["sh"])


def face(s, f, blink):
    t = tone_ramp(f["tone"])
    hr = HAIR[f.get("brows", f["hair_color"])]
    eye = EYES[f.get("eyes", "braun")]
    female = f.get("female", False)
    mood = f.get("mood", "neutral")
    P = lambda x, y, c: s.put("head", "front", x, y, c)  # noqa: E731
    brow = hr[0] if f["hair_color"] not in ("grau", "platin", "blond") else darker(hr[0], 0.8)
    if not female:
        for x in (1, 2, 5, 6):
            P(x, 3, brow)
        if mood == "sad":                                 # innere Brauenenden hochgezogen
            P(2, 3, t["base"])
            P(5, 3, t["base"])
            P(2, 2, brow)
            P(5, 2, brow)
        if f.get("brows_angry"):
            P(1, 3, t["base"])
            P(6, 3, t["base"])
            P(1, 2, brow)
            P(6, 2, brow)
    else:
        for x in (1, 2, 5, 6):
            P(x, 3, LASH)
        if mood == "sad":
            P(1, 3, t["base"])
            P(6, 3, t["base"])
            P(2, 2, darker(hr[0], 0.9))
            P(5, 2, darker(hr[0], 0.9))
        P(1, 5, t["cheek"])
        P(6, 5, t["cheek"])
    if blink:
        if female:
            for x in (1, 2, 5, 6):
                P(x, 3, t["base"])
                P(x, 4, LASH)
        else:
            for x in (1, 2, 5, 6):
                P(x, 4, t["lid"])
    else:
        P(1, 4, WHITE)
        P(2, 4, eye)
        P(5, 4, eye)
        P(6, 4, WHITE)
    if not female:
        P(3, 5, t["sh"])
        P(4, 5, t["sh"])
    lip = rgb(f["lips"]) if f.get("lips") else t["lip"]
    if mood == "smile":
        P(2, 6, darker(lip, 0.85))
        P(3, 6, lip)
        P(4, 6, lip)
        P(5, 6, darker(lip, 0.85))
    elif mood == "sad":
        P(3, 6, darker(lip, 0.9))
        P(4, 6, darker(lip, 0.9))
        P(2, 7, darker(lip, 0.8))
        P(5, 7, darker(lip, 0.8))
    else:
        P(3, 6, lip)
        P(4, 6, lip)
    if f.get("freckles"):
        for x, y in ((0, 5), (2, 5), (5, 5), (7, 5)):
            P(x, y, darker(t["base"], 0.88))
    if f.get("tear") and not blink:
        P(2, 5, rgb("A8D4F4"))
        P(2, 6, rgb("88B8E0"))
    if f.get("eyebags"):                                  # müde Ringe unter den Augen
        P(1, 5, darker(t["base"], 0.9))
        P(6, 5, darker(t["base"], 0.9))


# Frisuren: Vorderseite (Zeilen ab oben), rechte Seite (x 7 am Gesicht), Rücken-Zeilen, Hut-Ebene
#   H Grund, h dunkel, L Licht, S Stoppel
HAIRSTYLES = {
    "kurz": dict(front=["hLLHHHHh", "HH....Hh", "h......h"],
                 side=["HHHHHHHH", "HHHHHHHH", "hHHHHH.h", "hHHH....", "hHH.....", "hh......", "hh......"], back=7),
    "scheitel": dict(front=["HLLLHhHh", "HLLH...h", "h......h"],
                     side=["HHHHHHHH", "HHHHHHHH", "hHHHHH.h", "hHHH....", "hHH.....", "hh......", "hh......"], back=7),
    "zurueck": dict(front=["HLLLLLHh", "h......h", "h......h"],
                    side=["LLLLLLLL", "HHHHHHHH", "hHHHHH.h", "hHHH....", "hHH.....", "hh......", "hh......"], back=7),
    "zerzaust": dict(front=["HLHLHHHh", "H.LH.H.h", "h......h"],
                     side=["HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "hHHH.H..", "hHH.....", "hh......", "hh......"], back=7,
                     hat=dict(front=["L...L.H.", "........"], top="tufts")),
    "locken": dict(front=["LHLHLHLh", "HLH..HLh", "h......h"],
                   side=["LHLHLHLH", "HLHLHLHL", "hHLHLH.h", "hLHL....", "hHH.....", "hh......", "hh......"], back=7,
                   hat=dict(front=["L.L.L.L."], top="curls")),
    "stoppel": dict(front=["SSSSSSSS", "S......S"],
                    side=["SSSSSSSS", "SSSSSSSS", "SSSSSS.S", "SSS.....", "SS......", "S......."], back=6, stubble=True),
    "glatze": dict(front=[], side=["........", "........", "hHH.....", "hHH.....", "hHH.....", "hh......"], back=-2),
    "kahl": dict(front=[], side=[], back=0),
    "lang": dict(front=["HHHhHHHH", "HL....Hh", "H......h", "H......h", "H......h", "H......h", "H......h", "h......h"],
                 side=["HHHHHHHH"] * 8, back=8,
                 hat=dict(front=["........", "L......h", "H......h", "H......h", "H......h", "H......h", "h......h"],
                          side=["HHHHH...", "HHHHH...", "HHHH....", "HHHH....", "HHHH....", "hHHH....", "hhhh...."],
                          back="full"), shoulders=True),
    "bob": dict(front=["HHHHHHHH", "HLLHHHHh", "H......h", "H......h", "H......h", "h......h"],
                side=["HHHHHHHH"] * 5 + ["hhhhhh.."], back=6,
                hat=dict(front=["........", "........", "H......h", "H......h", "h......h"],
                         side=["HHHH....", "HHHH....", "HHHH....", "HHHH....", "hhhh...."])),
    "dutt": dict(front=["HLLHHHHh", "H......h", "h......h"],
                 side=["HHHHHHHH", "HHHHHHHH", "hHHHHH.h", "hHHH....", "hHH.....", "hh......"], back=6, bun=True),
    "zopf": dict(front=["HLLHHhHh", "HH.....h", "h......h"],
                 side=["HHHHHHHH", "HHHHHHHH", "hHHHHH.h", "hHHH....", "hHH.....", "hh......"], back=6, tail=True),
    "afro": dict(front=["HLHHLHHH", "H......H", "H......H"],
                 side=["HHHHHHHH"] * 4 + ["hHHH....", "hh......"], back=7,
                 hat=dict(front=["HLHHHLHH", "H......H", "H......H", "h......h"],
                          side=["HLHHHHHH", "HHHHLHHH", "HHHHHHH.", "hHHHH...", "hhHH....", "hh......"], back="afro",
                          top="full")),
}


def hair_style(s, f):
    style = HAIRSTYLES[f["hair"]]
    D, B, L = HAIR[f["hair_color"]]
    t = tone_ramp(f["tone"])
    st = tuple((a + 2 * b) // 3 for a, b in zip(D[:3], t["base"][:3])) + (255,)   # Stoppelfarbe
    pal = {"H": B, "h": D, "L": L, "S": st}
    if f["hair"] in ("glatze", "kahl"):
        s.put("head", "top", 2, 5, t["li"])                 # Glanz auf der Glatze
        s.put("head", "top", 1, 6, t["li"])
        s.put("head", "front", 1, 0, t["li"])
    else:
        top = st if style.get("stubble") else None
        for y in range(8):
            for x in range(8):
                s.put("head", "top", x, y, top or (L if (x < 3 and y > 4) or (x, y) in ((3, 6), (2, 4)) else B))
        if f["hair"] == "zurueck":                           # zurückgekämmte Kammspuren auf dem Kopf
            for y in range(8):
                s.put("head", "top", 5, y, D)
                s.put("head", "top", 2, y, L)
        if f["hair"] == "scheitel":
            for y in range(8):
                s.put("head", "top", 5, y, D)
    s.pattern("head", "front", style["front"], pal)
    s.mirror_side("head", style["side"], pal)
    back = style["back"]
    if back:
        rows = range(8) if back > 0 else range(2, 2 - back + 3)
        for y in (range(back) if back > 0 else (2, 3, 4)):
            for x in range(8):
                s.put("head", "back", x, y, (st if style.get("stubble") else (D if y == max(rows) or x == 7 else B)))
        if back > 0 and not style.get("stubble"):
            s.put("head", "back", 1, 0, L)
            s.put("head", "back", 2, 1, L)
    hat = style.get("hat", {})
    if "front" in hat:
        s.pattern("hat", "front", hat["front"], pal)
    if "side" in hat:
        s.mirror_side("hat", hat["side"], pal)
    if hat.get("back") == "full":
        for y in range(8):
            for x in range(8):
                s.put("hat", "back", x, y, D if y == 7 or x == 7 else (L if (x, y) in ((1, 1), (2, 2), (5, 1)) else B))
    if hat.get("back") == "afro" or hat.get("top") == "full":
        for y in range(7):
            for x in range(8):
                s.put("hat", "back", x, y, D if y == 6 else (L if (x + 2 * y) % 5 == 0 else B))
        for y in range(8):
            for x in range(8):
                s.put("hat", "top", x, y, L if (x + 2 * y) % 5 == 0 else B)
    if hat.get("top") == "curls":                            # Locken: gleichmäßige Kringel, einmal gebrochen
        for y in range(8):
            for x in range(8):
                if (x + 2 * y) % 4 == 0 and (x, y) != (4, 2):
                    s.put("hat", "top", x, y, L)
    if hat.get("top") == "tufts":
        for x, y in ((1, 6), (4, 7), (6, 5), (2, 2)):
            s.put("hat", "top", x, y, L)
    if style.get("bun"):                                     # Dutt hinten oben
        s.pattern("hat", "back", ["..HHHh..", "..LHHh..", "..hhhh.."], pal)
        s.pattern("hat", "top", ["..HHHh..", "..LHHh.."], pal)
    if style.get("tail"):                                    # Zopf mit Haargummi über den Rücken
        s.pattern("hat", "back", ["...HL...", "...cc...", "...Hh...", "...Hh...", "...Hh...", "...Hh...", "...Hh...",
                                  "...hh..."], dict(pal, c=CLOTH[f.get("tie_color", "schwarz")][1]))
        s.pattern("jacket", "back", ["...Hh...", "...Hh...", "...Hh...", "....h..."], pal)
    if style.get("shoulders"):                               # lange Haare fallen über Rücken und Schultern
        s.pattern("jacket", "back", ["HHLHHHHh", "HHHHLHHh", "HHHHHHHh", "hHHHHHhh", ".hhhhhh."], pal)
        s.pattern("jacket", "front", ["H......h", "H......h", "h......h"], pal)


def beard(s, f):
    kind = f.get("beard")
    if not kind:
        return
    D, B, L = HAIR[f.get("beard_color", f["hair_color"])]
    t = tone_ramp(f["tone"])
    P = lambda x, y, c: s.put("head", "front", x, y, c)  # noqa: E731
    if kind == "stoppeln":
        st = tuple((2 * a + b) // 3 for a, b in zip(t["base"][:3], D[:3])) + (255,)
        for x, y in ((0, 5), (7, 5), (0, 6), (1, 6), (2, 6), (5, 6), (6, 6), (7, 6), (1, 7), (2, 7), (3, 7), (4, 7),
                     (5, 7), (6, 7), (0, 7), (7, 7)):
            P(x, y, st)
        for side, xs in (("right", (6, 7)), ("left", (0, 1))):
            for x in xs:
                for y in (5, 6):
                    s.put("head", side, x, y, st)
        return
    if kind in ("schnurrbart", "zwirbel", "voll", "kinn"):
        for x in range(2, 6):
            P(x, 5, B)
        P(2, 5, L)
    if kind == "zwirbel":                                    # Zwirbelbart: breit bis an die Wangen, Spitzen dunkel
        P(1, 5, B)
        P(6, 5, B)
        P(0, 5, D)
        P(7, 5, D)
    if kind == "kinn":
        P(2, 7, D)
        P(3, 7, B)
        P(4, 7, B)
        P(5, 7, D)
        P(2, 6, B)
        P(5, 6, B)
    if kind == "voll":
        s.pattern("head", "front", ["h......h", "hH....Hh", "hHHHHHHh"], {"H": B, "h": D}, y0=5)
        P(1, 7, L)
        for side, xs in (("right", (5, 6, 7)), ("left", (0, 1, 2))):
            for x in xs:
                for y in (5, 6, 7):
                    s.put("head", side, x, y, B if y < 7 else D)
        for x in range(8):
            s.put("head", "bottom", x, 0, D)


def accessories(s, f):
    female = f.get("female", False)
    for a in f.get("acc", []):
        if a == "sonnenbrille":                               # dunkle Gläser, Glanz links oben, Bügel bis zum Ohr
            frame, glass, shine = rgb("141218"), rgb("23293A"), rgb("6A7890")
            s.pattern("hat", "front", ["kGGkkGGk", ".gg..gg."], {"k": frame, "G": glass, "g": darker(glass, 0.8)}, y0=3)
            s.put("hat", "front", 1, 3, shine)
            s.pattern("hat", "right", ["....kkkk"], {"k": frame}, y0=3)
            s.pattern("hat", "left", ["kkkk...."], {"k": frame}, y0=3)
        elif a == "brille":                                    # Lesebrille: dünnes Gestell um die Augen
            frame = rgb("3A2E2A")
            s.pattern("hat", "front", [".kk..kk.", "k..kk..k"], {"k": frame}, y0=3)
            s.pattern("hat", "right", ["....kkkk"], {"k": frame}, y0=4)
            s.pattern("hat", "left", ["kkkk...."], {"k": frame}, y0=4)
        elif a == "ohrhoerer":                                 # Ohrhörer rechts, Spiralkabel in den Kragen
            s.put("hat", "right", 3, 4, rgb("16161A"))
            for y in (5, 6, 7):
                s.put("hat", "right", 3, y, rgb("C6D0DA", 210))
            s.put("jacket", "right", 2, 0, rgb("C6D0DA", 210))
        elif a == "ohrringe":
            s.put("hat", "right", 3, 5, GOLD[2])
            s.put("hat", "left", 4, 5, GOLD[2])
        elif a == "augenschirm":                               # grüner Augenschirm des Kassierers
            g = ramp("1E5A32", "2A7A44", "44A262")
            s.pattern("hat", "front", ["LGGGGGGg", "gggggggg"], {"G": g[1], "g": g[0], "L": g[2]})
            s.mirror_side("hat", ["GGGGGGGG"], {"G": g[0]})
            s.pattern("hat", "top", ["", "", "", "", "", "", "LGGGGGGg", "GGGGGGGg"], {"G": g[1], "g": g[0], "L": g[2]})
        elif a == "mikro":                                     # Headset-Mikro (Croupier, Barkeeper)
            s.pattern("hat", "right", [".kkkk...", "....kk.."], {"k": rgb("26242C")}, y0=4)


# =====================================================================================================================
# Kleidung
# =====================================================================================================================

def arms(s, f, sleeve, cuff=None, short=False, band=None, bare=False, watch=False, bracelet=False):
    t = tone_ramp(f["tone"])
    skin = [t["sh"], t["base"], t["li"]]
    for which in ("rarm", "larm"):
        part = s.arm(which)
        s.box(part, skin if bare or short else sleeve)
        if short:                                          # kurze Ärmel: Stoff bis Zeile 3
            s.box(part, sleeve, rows=range(0, 4))
            for side in ("front", "back", "right", "left"):
                w, _ = s.size(part, side)
                for x in range(w):
                    s.put(part, side, x, 4, darker(skin[1], 0.92))   # Schatten unter dem Ärmel
        elif not bare:
            for side in ("front", "back", "right", "left"):
                w, _ = s.size(part, side)
                for x in range(w):
                    if cuff:
                        s.put(part, side, x, 10, cuff)
                    s.put(part, side, x, 11, skin[1] if side in ("front", "right") else skin[0])
        s.fill(part, "bottom", skin[0])
        if band:                                           # Ärmelhalter
            for side in ("front", "back", "right", "left"):
                w, _ = s.size(part, side)
                for x in range(w):
                    s.put(part, side, x, 4, band)
        if which == "larm" and (watch or bracelet):
            for side in ("front", "back", "right", "left"):
                w, _ = s.size(part, side)
                for x in range(w):
                    s.put(part, side, x, 10 if not bare else 9, GOLD[1] if side != "front" or x else GOLD[2])
        if which == "rarm" and f.get("ring"):
            s.put(part, "front", 1, 11, GOLD[2])


def legs(s, f, pants, shoes="schwarz", bare_from=None, skirt=None):
    """Beine: Hose mit Lichtkante links, Schattenkante rechts, Schuhe mit Spitze; Rock auf der Hosen-Ebene."""
    t = tone_ramp(f["tone"])
    sh = SHOE[shoes]
    for part in ("rleg", "lleg"):
        s.box(part, pants)
        if bare_from is not None:
            for side in ("front", "back", "right", "left"):
                w, _ = s.size(part, side)
                for y in range(bare_from, 10):
                    for x in range(w):
                        s.put(part, side, x, y, t["base"] if side in ("front", "right") else t["sh"])
        for side in ("front", "back", "right", "left"):
            w, _ = s.size(part, side)
            for x in range(w):
                s.put(part, side, x, 10, sh[1])
                s.put(part, side, x, 11, sh[0])
        s.put(part, "front", 0 if part == "rleg" else 3, 10, sh[2])      # Glanz auf der Schuhspitze
        s.fill(part, "bottom", sh[0])
    if skirt is not None:
        D, B, L = skirt["r"]
        end = skirt["end"]
        # Falten: senkrechte Linien in festem Abstand, an der Innenseite einmal gebrochen; Saum dunkler
        folds = {"rpants": "LB.B", "lpants": "B.Bd"}
        for part, fold in folds.items():
            for side in ("front", "back", "right", "left"):
                w, _ = s.size(part, side)
                for y in range(0, end):
                    for x in range(w):
                        ch = fold[x] if side == "front" else ("B" if x % 2 == 0 else ".")
                        c = {"L": L, "B": B, "d": D, ".": darker(B, 0.88)}[ch]
                        if y == end - 1:
                            c = D
                        s.put(part, side, x, y, c)


def pal_cloth(r, prefix):
    return {prefix.upper(): r[1], prefix: r[0], prefix + "+": r[2]}


def suit(s, f):
    """Anzug: Sakko mit Revers (Jacken-Ebene), Hemd, Krawatte/Fliege/offen, Einstecktuch, Knöpfe; Hose, Schuhe."""
    J = CLOTH[f["jacket"]]
    shirt = CLOTH[f.get("shirt", "weiss")]
    tie_kind = f.get("tie", "krawatte")
    tie = CLOTH[f.get("tie_color", "schwarz")]
    t = tone_ramp(f["tone"])
    pal = {"J": J[1], "j": J[0], "K": J[2], "S": shirt[1], "s": shirt[0], "T": tie[1], "t": tie[0],
           "B": GOLD[1] if f.get("gold_buttons") else darker(J[0], 0.8), "P": CLOTH[f.get("pocket", "weiss")][2],
           "N": t["base"], "n": t["sh"], "G": GOLD[2]}
    if tie_kind == "krawatte":
        rows = ["KSSTtSSj", "JKSTtSKj", "JJKTtKJj", "JJKTtKPj", "JJJKtJJj", "JJJJBJJj", "JJJJJJJj", "JJJJBJJj",
                "JJJJJJJj", "jJJJJJJj", "JJJJJJJj", "jjjjjjjj"]
    elif tie_kind == "fliege":
        rows = ["KSSSSSSj", "JKTtTtKj", "JJKSsKJj", "JJKSsKPj", "JJJKsJJj", "JJJJBJJj", "JJJJJJJj", "JJJJBJJj",
                "JJJJJJJj", "jJJJJJJj", "JJJJJJJj", "jjjjjjjj"]
    else:
        rows = ["KSnNNSSj", "JKSNnSKj", "JJKSsKJj", "JJKSsKPj", "JJJKsJJj", "JJJJBJJj", "JJJJJJJj", "JJJJBJJj",
                "JJJJJJJj", "jJJJJJJj", "JJJJJJJj", "jjjjjjjj"]
    if f.get("loose_tie"):   # Pechvogel: Krawatte gelockert, Kragen offen, Hemd hängt raus
        rows = ["KSnNSSSj", "JKSsTSKj", "JJKSTtJj", "JJKsTtJj", "JJJSKtJj", "JJJSsJJj", "JJJJJJJj", "JJJJBJJj",
                "JJsSSJJj", "jJSSsSJj", "JJSsSSsj", "jjjjjjjj"]
    if f.get("badge"):
        rows[3] = rows[3][:6] + "G" + rows[3][7]
    s.box("body", J)
    s.pattern("body", "front", rows, pal)
    for y in range(8, 12):                                   # Rückenschlitz
        s.put("body", "back", 4, y, J[0])
    # Revers auf der Jacken-Ebene: gibt dem Sakko Tiefe
    s.pattern("jacket", "front", [".K....j.", ".K...j..", "..K.j...", "..K.j..."], pal)
    arms(s, f, J, cuff=shirt[1], watch=f.get("watch"))
    legs(s, f, CLOTH[f.get("pants", f["jacket"])], f.get("shoes", "schwarz"))


def vest(s, f):
    """Hemd mit Weste (Kellner, Kassierer, Croupier, Losbude): Fliege, Knopfleiste, Streifen optional."""
    shirt = CLOTH[f.get("shirt", "weiss")]
    V = CLOTH[f["vest"]]
    tie = CLOTH[f.get("tie_color", "schwarz")]
    st = CLOTH[f["stripes"]] if f.get("stripes") else None
    pal = {"S": shirt[1], "s": shirt[0], "V": V[1], "v": V[0], "W": V[2], "T": tie[1], "t": tie[0], "B": GOLD[1],
           "G": GOLD[2], "R": st[1] if st else V[1], "r": st[0] if st else V[0]}
    rows = ["SSSSSSSs", "STtTtSSs", "VWSSsVVv", "VVWSsVVv", "VVVWsVVv", "VVVVBVVv", "VVVVVVVv", "VVVVBVVv",
            "VVVVVVVv", "VVVVVVVv", "vvvvvvvv", "ssssssss"]
    if st:   # rot-weiße Streifen in festem Takt, an der Knopfleiste gebrochen
        rows = [r if i < 2 or i == 11 else "".join(("R" if x % 2 == 0 else "V") if ch in "VWv" else ch
                                                    for x, ch in enumerate(r)) for i, r in enumerate(rows)]
        rows[10] = "rvrvrvrv"
    if f.get("badge"):
        rows[3] = rows[3][:6] + "G" + rows[3][7]
    s.box("body", shirt)
    s.pattern("body", "front", rows, pal)
    for side in ("back", "right", "left"):
        w, _ = s.size("body", side)
        for y in range(1, 11):
            for x in range(w):
                c = (st[1] if (x % 2 == 0) else V[1]) if st else (V[0] if side == "left" or y == 10 else V[1])
                s.put("body", side, x, y, c)
    s.pattern("body", "back", ["", "", "", "", "", "", "..vvvv.."], pal)     # Rückengurt der Weste
    arms(s, f, shirt, cuff=shirt[0], band=CLOTH[f["band"]][1] if f.get("band") else None, watch=f.get("watch"))
    legs(s, f, CLOTH[f.get("pants", "schwarz")], f.get("shoes", "schwarz"))


def dress(s, f):
    """Kleid: lang (Abend) oder kurz (Cocktail); Träger, Taillenband, Falten, Rock mit Tiefe auf der Hosen-Ebene."""
    D = CLOTH[f["dress"]]
    t = tone_ramp(f["tone"])
    pal = {"D": D[1], "d": D[0], "L": D[2], "N": t["base"], "n": t["sh"], "G": GOLD[2], "g": GOLD[1],
           "P": rgb("F4F2EC"), "p": rgb("C8C4BC")}
    rows = ["NNNNNNNn", "NNNNNNNn", "LDDNNDDd", "LDDDDDDd", "DLDDDDDd", "DDDDDDDd", "ddgGgggd",
            "DLDDDdDd", "DLDDDdDd", "DLDDDdDd", "DLDDDdDd", "dddddddd"]
    if f.get("necklace") == "gold":
        rows[1] = "NNgGGgNn"
        rows[2] = "LDDgGDDd"
    elif f.get("necklace") == "perlen":
        rows[1] = "NPpPpPNn"
    if f.get("strapless"):
        rows[2] = "DDDDDDDd"
    s.box("body", D)
    s.pattern("body", "front", rows, pal)
    for side in ("back", "right", "left"):
        w, _ = s.size("body", side)
        for y in (0, 1):
            for x in range(w):
                s.put("body", side, x, y, t["base"] if side != "left" else t["sh"])
    s.pattern("body", "back", ["", "", "dDDDDDDd", "", "", "", "ddgggggd"], pal)
    arms(s, f, None, bare=True, bracelet=f.get("bracelet"))
    long = f.get("long", True)
    legs(s, f, D, f.get("shoes", "schwarz"), bare_from=None if long else 5, skirt=dict(r=D, end=10 if long else 5))


def casual(s, f):
    """T-Shirt mit Aufdruck oder Bluse, optional Lederjacke; Jeans oder Hose; Turnschuhe oder Lederschuhe."""
    top = CLOTH[f["top"]]
    t = tone_ramp(f["tone"])
    pr = CLOTH[f.get("print", "rot")]
    jk = CLOTH[f["over"]] if f.get("over") else None
    pal = {"T": top[1], "t": top[0], "L": top[2], "N": t["base"], "n": t["sh"], "P": pr[1], "p": pr[0],
           "J": jk[1] if jk else top[1], "j": jk[0] if jk else top[0], "K": jk[2] if jk else top[2],
           "Z": rgb("B8B8C0"), "B": GOLD[2], "b": rgb("2A2430")}
    if f.get("blouse"):
        rows = ["LTnNnTTt", "LTTnTTTt", "TLTTsTTt", "TTTTTTTt", "TTTTsTTt", "TTTTTTTt", "TTTTsTTt", "TTTTTTTt",
                "bbbbBbbb", "TTTTTTTt", "TTTTTTTt", "tttttttt"]
        pal["s"] = top[0]
    else:
        rows = ["LTTnNTTt", "LTTTTTTt", "TLTPPTTt", "TTPppPTt", "TTTPPTTt", "TTTTTTTt", "TTTTTTTt", "TTTTTTTt",
                "TTTTTTTt", "TTTTTTTt", "TTTTTTTt", "tttttttt"]
    if jk:   # offene Lederjacke mit Reißverschluss links, Kragen auf der Jacken-Ebene
        rows = ["KJTnNTJj", "KJTTTTJj", "JJTPPTJj", "JZPppPJj", "JJTPPTJj", "JZTTTTJj", "JJTTTTJj", "JZTTTTJj",
                "JJTTTTJj", "JJTTTTJj", "JJjjjjJj", "jjjjjjjj"]
    s.box("body", jk if jk else top)
    s.pattern("body", "front", rows, pal)
    if jk:
        s.pattern("jacket", "front", ["K......j", ".K....j.", ".K....j."], pal)
        arms(s, f, jk, watch=f.get("watch"))
    else:
        arms(s, f, top, short=not f.get("blouse"), watch=f.get("watch"), bracelet=f.get("bracelet"))
    legs(s, f, CLOTH[f.get("pants", "jeans")], f.get("shoes", "weiss"))
    if f.get("pants", "jeans") == "jeans":            # Jeans: helle Naht, Knie leicht ausgeblichen
        for part in ("rleg", "lleg"):
            s.put(part, "front", 1 if part == "rleg" else 2, 5, CLOTH["jeans"][2])
            s.put(part, "front", 1 if part == "rleg" else 2, 6, CLOTH["jeans"][2])


OUTFIT = {"anzug": suit, "weste": vest, "kleid": dress, "casual": casual}


def build(f, blink=False):
    s = Skin(f.get("female", False) or f.get("slim", False))
    head_skin(s, f["tone"])
    OUTFIT[f["outfit"]](s, f)
    hair_style(s, f)
    face(s, f, blink)
    beard(s, f)
    accessories(s, f)
    return s.img


# =====================================================================================================================
# Figuren
# =====================================================================================================================

def P(id, name, group, **kw):
    kw.update(id=id, name=name, group=group)
    return kw


PEOPLE = [
    # Security: schwarzer Anzug, weißes Hemd, schwarze Krawatte, Abzeichen, Ohrhörer
    P("sec_1", "Marco", "security", tone="hell", hair="stoppel", hair_color="dunkelbraun", eyes="braun", beard="stoppeln",
      outfit="anzug", jacket="schwarz", tie_color="schwarz", badge=True, acc=["sonnenbrille", "ohrhoerer"], mood="neutral"),
    P("sec_2", "Darnell", "security", tone="dunkel", hair="kahl", hair_color="schwarz", eyes="dunkel", beard="kinn",
      outfit="anzug", jacket="schwarz", tie_color="schwarz", badge=True, acc=["ohrhoerer"]),
    P("sec_3", "Yusuf", "security", tone="oliv", hair="kurz", hair_color="schwarz", eyes="braun", beard="voll",
      outfit="anzug", jacket="schwarz", tie_color="anthrazit", badge=True, acc=["ohrhoerer"]),
    P("sec_4", "Svenja", "security", female=True, tone="porzellan", hair="dutt", hair_color="blond", eyes="blau",
      outfit="anzug", jacket="schwarz", tie_color="schwarz", badge=True, acc=["sonnenbrille", "ohrhoerer"]),
    P("sec_5", "Kenji", "security", tone="hell", hair="scheitel", hair_color="schwarz", eyes="dunkel",
      outfit="anzug", jacket="anthrazit", tie_color="schwarz", badge=True, acc=["ohrhoerer"]),
    # Gäste
    P("gast_1", "Viktor", "gast", tone="hell", hair="zurueck", hair_color="grau", eyes="blau", beard="zwirbel",
      outfit="anzug", jacket="marine", tie_color="gold", pocket="gold", gold_buttons=True, mood="smile", watch=True,
      ring=True),
    P("gast_2", "Elena", "gast", female=True, tone="oliv", hair="lang", hair_color="schwarz", eyes="braun",
      outfit="kleid", dress="rot", necklace="gold", shoes="rot", lips="B0202E", acc=["ohrringe"], mood="smile"),
    P("gast_3", "James", "gast", tone="dunkel", hair="locken", hair_color="schwarz", eyes="dunkel",
      outfit="anzug", jacket="schwarz", tie="fliege", tie_color="schwarz", mood="smile"),
    P("gast_4", "Sophie", "gast", female=True, tone="porzellan", hair="bob", hair_color="blond", eyes="blau",
      outfit="kleid", dress="schwarz", long=False, necklace="perlen", shoes="schwarz", lips="C0485A"),
    P("gast_5", "Mateo", "gast", tone="braun", hair="scheitel", hair_color="dunkelbraun", eyes="braun", beard="stoppeln",
      outfit="anzug", jacket="bordeaux", shirt="schwarz", tie="offen", pants="schwarz", mood="smile"),
    P("gast_6", "Amara", "gast", female=True, tone="tief", hair="afro", hair_color="schwarz", eyes="bernstein",
      outfit="kleid", dress="smaragd", necklace="gold", shoes="gold", lips="8E3A40", acc=["ohrringe"], bracelet=True),
    P("gast_7", "Lukas", "gast", tone="hell", hair="kurz", hair_color="blond", eyes="gruen",
      outfit="casual", top="weiss", over="leder", print="marine", pants="jeans", shoes="braun", mood="smile"),
    P("gast_8", "Mei", "gast", female=True, tone="hell", hair="zopf", hair_color="schwarz", eyes="dunkel",
      outfit="kleid", dress="royal", necklace="perlen", shoes="nude", lips="C05A6A"),
    P("gast_9", "Frank", "gast", tone="porzellan", hair="glatze", hair_color="grau", eyes="grau", beard="voll",
      outfit="anzug", jacket="tweed", shirt="creme", tie_color="gruen", pants="tweed", shoes="braun", acc=["brille"]),
    P("gast_10", "Nina", "gast", female=True, tone="porzellan", hair="lang", hair_color="kastanie", eyes="gruen",
      freckles=True, outfit="casual", top="weiss", blouse=True, pants="schwarz", shoes="schwarz", lips="B85A5A"),
    P("gast_11", "Paul", "gast", tone="hell", hair="zerzaust", hair_color="braun", eyes="braun", beard="stoppeln",
      outfit="anzug", jacket="grau", tie_color="bordeaux", loose_tie=True, mood="sad", tear=True),
    P("gast_12", "Rita", "gast", female=True, tone="braun", hair="bob", hair_color="dunkelbraun", eyes="braun",
      outfit="kleid", dress="violett", long=False, shoes="schwarz", mood="sad", lips="8E4A50"),
    # Personal: Varianten der Rollen (die handgemalten bleiben Variante 1)
    P("kassierer_2", "Kassierer", "kasse", tone="braun", hair="glatze", hair_color="schwarz", eyes="braun",
      beard="schnurrbart", outfit="weste", vest="schwarz", band="rot", acc=["augenschirm"], mood="smile"),
    P("kassierer_3", "Kassiererin", "kasse", female=True, tone="oliv", hair="dutt", hair_color="braun", eyes="gruen",
      outfit="weste", vest="schwarz", band="rot", acc=["augenschirm"], lips="B06060", mood="smile"),
    P("dealer_2", "Dealer", "dealer", tone="dunkel", hair="kurz", hair_color="schwarz", eyes="dunkel",
      outfit="weste", vest="bordeaux", tie_color="schwarz", pants="schwarz", mood="smile"),
    P("dealer_3", "Dealerin", "dealer", female=True, tone="hell", hair="zopf", hair_color="blond", eyes="blau",
      outfit="weste", vest="bordeaux", tie_color="schwarz", pants="schwarz", lips="C04A5A"),
    P("croupier_2", "Croupier", "croupier", tone="oliv", hair="zurueck", hair_color="schwarz", eyes="braun",
      outfit="weste", vest="schwarz", tie_color="rot", pants="schwarz"),
    P("croupier_3", "Croupière", "croupier", female=True, tone="tief", hair="dutt", hair_color="schwarz", eyes="braun",
      outfit="weste", vest="schwarz", tie_color="rot", pants="schwarz", lips="7A3A40", acc=["ohrringe"]),
    P("chipshop_1", "Chip-Shop", "chipshop", tone="hell", hair="zurueck", hair_color="dunkelbraun", eyes="gruen",
      outfit="anzug", jacket="schwarz", tie="fliege", tie_color="gold", pocket="gold", gold_buttons=True, mood="smile"),
    P("chipshop_2", "Chip-Shop", "chipshop", female=True, tone="dunkel", hair="lang", hair_color="schwarz", eyes="bernstein",
      outfit="kleid", dress="gold", necklace="gold", shoes="gold", lips="8E3A40", mood="smile"),
    P("barkeeper_1", "Barkeeper", "bar", tone="hell", hair="zurueck", hair_color="dunkelbraun", eyes="blau", beard="voll",
      outfit="weste", vest="schwarz", tie_color="schwarz", pants="schwarz", band="schwarz", mood="smile"),
    P("barkeeper_2", "Barkeeperin", "bar", female=True, tone="braun", hair="bob", hair_color="schwarz", eyes="braun",
      outfit="weste", vest="schwarz", tie_color="bordeaux", pants="schwarz", lips="8E4A50", acc=["ohrringe"], mood="smile"),
    # Losbude: neue Figur (Strohhut, rot-weiß gestreifte Weste, Ärmelhalter, Fliege)
    P("losbude_1", "Lotte", "losbude", female=True, tone="porzellan", hair="locken", hair_color="rot", eyes="gruen",
      freckles=True, outfit="weste", vest="creme", stripes="rot", band="rot", tie_color="schwarz", pants="schwarz",
      lips="C0485A", hat="strohhut", mood="smile"),
    P("losbude_2", "Otto", "losbude", tone="hell", hair="kurz", hair_color="dunkelbraun", eyes="braun", beard="zwirbel",
      outfit="weste", vest="creme", stripes="rot", band="rot", tie_color="schwarz", pants="beige", shoes="braun",
      hat="strohhut", mood="smile"),
]

# Handgemalte Casino-Figuren: Augen für die Blinzel-Fassung (x, y im Skin, Farbe beim Blinzeln: "lid" = Hautschatten)
HANDMADE = {
    "kassierer": dict(rows={3: "lid"}, female=False),
    "dealer": dict(rows={4: "lid"}, female=False),
    "croupier": dict(rows={4: "skin", 5: "lash"}, female=True),
    "mia": dict(rows={4: "skin", 5: "lash"}, female=True),
}


def blink_handmade(img, spec):
    """Blinzel-Fassung einer handgemalten Figur: Augenpixel (x 1, 2, 5, 6) durch Lid bzw. Wimpernlinie ersetzen."""
    out = img.copy()
    skin = img.getpixel((8 + 3, 8 + min(spec["rows"])))   # Hautton neben dem Auge
    lid = tuple(int(v * 0.86) for v in skin[:3]) + (255,)
    for y, kind in spec["rows"].items():
        for x in (1, 2, 5, 6):
            c = {"lid": lid, "skin": skin, "lash": LASH}[kind]
            out.putpixel((8 + x, 8 + y), c)
    return out


# =====================================================================================================================
# Requisiten für die Hand (16x16, als Item gehalten)
# =====================================================================================================================

def prop_img(kind):
    im = Image.new("RGBA", (16, 16), T)
    px = im.load()
    K = rgb("1A1820")

    def r(x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[x, y] = c

    if kind == "leer":
        return im
    if kind == "sekt":          # Sektflöte: Fuß, Stiel, schlanker Kelch mit Perlen
        r(7, 3, 8, 9, rgb("F2D27A"))
        r(6, 2, 6, 9, rgb("DCE8F0", 200))
        r(9, 2, 9, 9, rgb("B8C8D4", 200))
        r(7, 2, 8, 2, rgb("F4F8FA", 220))
        px[7, 5] = rgb("FFF6D0")
        px[8, 7] = rgb("FFF6D0")
        r(7, 10, 8, 12, rgb("D0DCE6", 220))
        r(5, 13, 10, 13, rgb("C8D4DE", 230))
    elif kind == "cocktail":    # Martini-Glas mit Olive
        for i in range(5):
            r(4 + i, 3 + i, 11 - i, 3 + i, rgb("E8EEF2", 190) if i == 0 else rgb("C83A5A"))
        px[5, 3] = rgb("F8FAFC")
        r(7, 8, 8, 11, rgb("D0DCE6", 220))
        r(5, 12, 10, 12, rgb("C8D4DE", 230))
        px[10, 2] = rgb("6A8A2A")
        px[9, 3] = rgb("4A6A1A")
    elif kind == "whisky":      # Tumbler mit Bernstein und Eiswürfel
        r(5, 7, 10, 12, rgb("D8E4EC", 160))
        r(5, 9, 10, 12, rgb("B8701E"))
        r(6, 9, 7, 10, rgb("E8F2F8", 230))
        r(5, 12, 10, 12, rgb("8A5414"))
        r(5, 7, 5, 12, rgb("F0F6FA", 210))
    elif kind == "bier":        # Bierkrug mit Schaum
        r(4, 5, 10, 13, rgb("E6A81E"))
        r(4, 3, 10, 5, rgb("F8F4E8"))
        r(11, 7, 12, 11, rgb("C8D4DE", 220))
        r(4, 5, 4, 13, rgb("F6C848"))
        r(10, 6, 10, 13, rgb("B47A10"))
    elif kind == "handy":       # Smartphone
        r(6, 2, 10, 13, K)
        r(7, 3, 9, 11, rgb("3A6AA0"))
        px[7, 3] = rgb("8AB4E0")
        px[8, 12] = rgb("5A5866")
    elif kind == "geld":        # Geldbündel mit Banderole
        r(3, 5, 12, 10, rgb("4A8A4A"))
        r(3, 5, 12, 5, rgb("6AAA62"))
        r(3, 10, 12, 10, rgb("2E5E32"))
        r(7, 4, 8, 11, rgb("E8C658"))
        px[4, 7] = rgb("8ACA7A")
    elif kind == "lose":        # Fächer aus Losen
        for i, c in enumerate(("D02838", "F0C030", "3A7AD0")):
            r(3 + 3 * i, 3 + i, 6 + 3 * i, 11 + i, rgb(c))
            r(3 + 3 * i, 3 + i, 6 + 3 * i, 3 + i, lighter(rgb(c), 1.25))
            px[4 + 3 * i, 7 + i] = rgb("FFFFFF")
    return im


PROPS = ["leer", "sekt", "cocktail", "whisky", "bier", "handy", "geld", "lose"]

HAND_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 3, 1.2], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 3, 1.2], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


# =====================================================================================================================
# Schreiben, Kontaktbogen
# =====================================================================================================================

def hat_img():
    """Strohhut-Textur: Geflecht oben (gleichmäßig, einmal gebrochen), Krone, rotes Band, Krempe, Unterseite."""
    im = Image.new("RGBA", (16, 16), T)
    px = im.load()
    straw = ramp("9E7A34", "D2AE5C", "EED288")
    band = ramp("6E0A16", "A81424", "D23040")
    for y in range(8):
        for x in range(8):
            px[x, y] = straw[2] if (x // 2 + y) % 2 == 0 else straw[1]
    px[5, 3] = straw[0]                                     # ein Halm gebrochen
    for y in range(4):
        for x in range(8, 16):
            px[x, y] = straw[2] if y == 0 else (straw[1] if (x + y) % 3 else straw[2])
    for x in range(8, 16):
        px[x, 4] = band[2]
        px[x, 5] = band[1]
        px[x, 6] = straw[0]
        px[x, 7] = darker(straw[0], 0.8)
    for y in range(8, 16):
        for x in range(8):
            px[x, y] = straw[0] if (x + y) % 2 else darker(straw[0], 0.85)
    return im


def hat_model():
    """Kreissäge-Strohhut (Boater): flache Krone, rotes Band, breite Krempe. Maße in Modell-Einheiten: auf dem Kopf
    wird das Modell auf 0,625 skaliert, der Kopf reicht von 1,6 bis 14,4."""
    t = "#0"

    def faces(up, side, down=None):
        f = {d: {"uv": side, "texture": t} for d in ("north", "south", "east", "west")}
        f["up"] = {"uv": up, "texture": t}
        if down:
            f["down"] = {"uv": down, "texture": t}
        return f

    band_faces = {d: {"uv": [8, 4, 16, 6], "texture": t} for d in ("north", "south", "east", "west")}
    return {
        "textures": {"0": "kuby:item/people/strohhut", "particle": "kuby:item/people/strohhut"},
        "elements": [
            {"from": [1.2, 14.2, 1.2], "to": [14.8, 17.8, 14.8], "faces": faces([0, 0, 8, 8], [8, 0, 16, 4])},
            {"from": [1.0, 14.6, 1.0], "to": [15.0, 15.8, 15.0], "faces": band_faces},
            {"from": [-2.4, 14.0, -2.4], "to": [18.4, 14.6, 18.4],
             "faces": faces([0, 0, 8, 8], [8, 6, 16, 7], [0, 8, 8, 16])},
        ],
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -9, 0], "scale": [0.7, 0.7, 0.7]},
            "fixed": {"translation": [0, -8, 0], "scale": [0.6, 0.6, 0.6]},
        },
    }


HATS = {"strohhut": (hat_img, hat_model)}


def write(pack):
    a = os.path.join(pack, "assets/kuby")
    sk = os.path.join(a, "textures/entity/people")
    os.makedirs(sk, exist_ok=True)
    n = 0
    for f in PEOPLE:
        build(f).save(os.path.join(sk, f["id"] + ".png"))
        build(f, blink=True).save(os.path.join(sk, f["id"] + "_b.png"))
        n += 2
    casino = os.path.join(a, "textures/entity/casino")
    for name, spec in HANDMADE.items():
        src = os.path.join(casino, name + ".png")
        if os.path.exists(src):
            blink_handmade(Image.open(src).convert("RGBA"), spec).save(os.path.join(casino, name + "_b.png"))
            n += 1
    itex = os.path.join(a, "textures/item/people")
    models = os.path.join(a, "models/item/people")
    items = os.path.join(a, "items/people")
    for d in (itex, models, items):
        os.makedirs(d, exist_ok=True)

    def item(kind, model):
        with open(os.path.join(models, kind + ".json"), "w", encoding="utf-8") as fh:
            json.dump(model, fh)
        with open(os.path.join(items, kind + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/people/" + kind}}, fh)

    for kind in PROPS:
        prop_img(kind).save(os.path.join(itex, kind + ".png"))
        item(kind, {"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/people/" + kind},
                    "display": HAND_DISPLAY})
    for kind, (img, model) in HATS.items():
        img().save(os.path.join(itex, kind + ".png"))
        item(kind, model())
    # Liste für das Plugin: id;Name;Gruppe;schlank;Hut
    res = os.path.join(os.path.dirname(HERE), "KubyCore-v2.0.14", "KubyPlugins", "src", "main", "resources", "people.txt")
    with open(res, "w", encoding="utf-8") as fh:
        fh.write("# Figuren aus tools/build_people.py: id;Name;Gruppe;schlank;Hut "
                 "(Skin kuby:entity/people/<id>, Blinzeln <id>_b, Hut kuby:people/<hut>)\n")
        for f in PEOPLE:
            fh.write("%s;%s;%s;%s;%s\n" % (f["id"], f["name"], f["group"],
                                          "1" if (f.get("female") or f.get("slim")) else "0", f.get("hat", "-")))
    return n


def doll(img, slim, back=False):
    a = 3 if slim else 4
    out = Image.new("RGBA", (8 + 2 * a, 32), T)

    def crop(x, y, w, h):
        return img.crop((x, y, x + w, y + h))

    def put(base, over, x, y):
        out.alpha_composite(base, (x, y))
        out.alpha_composite(over, (x, y))

    if not back:
        put(crop(8, 8, 8, 8), crop(40, 8, 8, 8), a, 0)
        put(crop(20, 20, 8, 12), crop(20, 36, 8, 12), a, 8)
        put(crop(44, 20, a, 12), crop(44, 36, a, 12), 0, 8)
        put(crop(36, 52, a, 12), crop(52, 52, a, 12), a + 8, 8)
        put(crop(4, 20, 4, 12), crop(4, 36, 4, 12), a, 20)
        put(crop(20, 52, 4, 12), crop(4, 52, 4, 12), a + 4, 20)
    else:
        put(crop(24, 8, 8, 8), crop(56, 8, 8, 8), a, 0)
        put(crop(32, 20, 8, 12), crop(32, 36, 8, 12), a, 8)
        put(crop(48 + a, 20, a, 12), crop(48 + a, 36, a, 12), a + 8, 8)
        put(crop(40 + a, 52, a, 12), crop(56 + a, 52, a, 12), 0, 8)
        put(crop(12, 20, 4, 12), crop(12, 36, 4, 12), a + 4, 20)
        put(crop(28, 52, 4, 12), crop(12, 52, 4, 12), a, 20)
    return out


def sheet(path):
    cells = []
    for f in PEOPLE:
        slim = f.get("female", False)
        img = build(f)
        cells.append((f["id"], doll(img, slim), doll(img, slim, True), doll(build(f, True), slim)))
    cols = 8
    cw, ch = 16 * 3 * 6 + 20, 32 * 6 + 30
    rows = (len(cells) + cols - 1) // cols
    out = Image.new("RGBA", (cols * cw, rows * ch), (52, 48, 62, 255))
    for i, (_id, fr, bk, bl) in enumerate(cells):
        x, y = (i % cols) * cw + 6, (i // cols) * ch + 6
        for k, im in enumerate((fr, bk, bl)):
            big = im.resize((im.width * 6, im.height * 6), Image.NEAREST)
            out.alpha_composite(big, (x + k * 16 * 6, y))
    out.save(path)


if __name__ == "__main__":
    args = sys.argv[1:]
    if "--sheet" in args:
        sheet(args[args.index("--sheet") + 1])
    else:
        print(write(args[0] if args else os.path.join(os.path.dirname(HERE), "resourcepack/KubyUI")), "Skins")
