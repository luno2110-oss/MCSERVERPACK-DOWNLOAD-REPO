#!/usr/bin/env python3
"""Pferderennen-Grafik (v2.05, Casino-Automat „wie im Casino“): Pixel-Pferde für den Wand-Bildschirm und die Bahn.

Nach tools/icon_db/STYLE.md: 1-px-Kontur fast schwarz (leicht getönt), Farben aus Rampen mit 4 Stufen, Licht oben
links, Beine hinter dem Körper eine Stufe dunkler (Tiefe), Jockey-Dress in der Bahnfarbe, Startnummer auf der
Satteldecke. Je Bahn ein eigenes Fell (Fuchs, Rappe, Palomino, Brauner, Schimmel, Dunkelbrauner), vier Galopp-Bilder.

Schreibt (assets/kuby):
  * rn_h<bahn>_<bild>  Items (flaches Bild 32 x 32) für die Pferde, bahn 0–5, bild 0–3
  * m_hd_turf, m_hd_finish, m_hd_rail, m_hd_gate, m_hd_race_dark, m_hd_rn0..5 (Würfel-Materialien)
  * Icon race_big (32 x 32) für den Dialog-Kopf (über build_casino.build)

Aufruf: python3 tools/build_race.py [pack] [--sheet bild.png]
"""
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_casino as bc  # noqa: E402
import build_slot_themes as st  # noqa: E402

rgb = bc.rgb
T = (0, 0, 0, 0)
OUT = rgb("1A1218")


def ramp(*h):
    return [rgb(x) for x in h]


# Fell (dunkel → hell) und Mähne je Bahn
COATS = [
    (ramp("5A2410", "8A3E1C", "B25E2C", "D08246"), ramp("3A160A", "5A2410")),     # Fuchs
    (ramp("100E16", "1E1A26", "322C3E", "4A4258"), ramp("08060C", "100E16")),     # Rappe
    (ramp("8A6224", "B88C36", "DCB45A", "F0D488"), ramp("C8C0AE", "F2EEE4")),     # Palomino (helle Mähne)
    (ramp("3A1E10", "5E341C", "86502C", "A86C3C"), ramp("100E16", "1E1A26")),     # Brauner (schwarze Mähne)
    (ramp("62667A", "9298A8", "C2C8D2", "E8ECF2"), ramp("8A8E9C", "B8BCC6")),     # Schimmel
    (ramp("24140C", "3A2216", "56361F", "72492C"), ramp("100A08", "24140C")),     # Dunkelbrauner
]
# Rennfarben (wie HorseRace.COLORS): Rot, Blau, Gelb, Grün, Violett, Orange
SILKS = [
    ramp("8A1420", "C8283A", "E8404A", "FF8A86"),
    ramp("143A8A", "2A62C8", "4E8EF2", "A0C8FF"),
    ramp("8A6A08", "C89A10", "F2C42A", "FFE88A"),
    ramp("145A28", "2A8A3E", "46C25E", "9AEAA6"),
    ramp("4A1A7A", "7A3AB8", "B46AF0", "DCB0FF"),
    ramp("8A3A08", "C85E14", "F08A2A", "FFC080"),
]
HOOF = rgb("2A2630")
SKIN = rgb("F0C49A")
CLOTH = ramp("B8B4AC", "E8E4DA", "FFFFFF")
DIGITS = {"1": ["010", "110", "010", "010", "111"], "2": ["110", "001", "010", "100", "111"],
          "3": ["110", "001", "010", "001", "110"], "4": ["101", "101", "111", "001", "001"],
          "5": ["111", "100", "110", "001", "110"], "6": ["011", "100", "110", "101", "010"]}

# Beine je Bild: (Knie dx, dy), (Huf dx, dy) relativ zum Ansatz; Reihenfolge: vorn nah, vorn fern, hinten nah, hinten fern
LEGS = [
    [((3, 4), (6, 7)), ((2, 4), (5, 8)), ((-2, 4), (-6, 8)), ((-1, 4), (-5, 7))],   # gestreckt
    [((1, 4), (1, 9)), ((2, 4), (3, 8)), ((-1, 4), (-3, 9)), ((0, 4), (-1, 9))],    # Landen
    [((3, 3), (1, 6)), ((2, 3), (0, 6)), ((1, 4), (-2, 6)), ((2, 4), (-1, 6))],     # gesammelt (Schwebe)
    [((3, 2), (5, 4)), ((2, 3), (4, 6)), ((1, 4), (2, 9)), ((0, 4), (1, 9))],      # Abdruck hinten
]
BOB = [0, 0, -1, 0]   # Körper hebt sich in der Schwebe


def horse(lane, frame):
    coat, mane = COATS[lane]
    silk = SILKS[lane]
    p = bc.Pix(32)
    dy = BOB[frame]
    hip_y = 19 + dy
    # ferne Beine (hinter dem Körper, eine Stufe dunkler)
    legs = LEGS[frame]
    anchors = [(20, hip_y), (21, hip_y), (10, hip_y), (11, hip_y)]

    def leg(i, col):
        ax, ay = anchors[i]
        (kx, ky), (hx, hy) = legs[i]
        p.line(ax, ay, ax + kx, ay + ky, col, 1.7)
        p.line(ax + kx, ay + ky, ax + hx, ay + hy, col, 1.5)
        p.put(ax + hx, ay + hy, HOOF)
        p.put(ax + hx + (1 if hx >= 0 else 0), ay + hy, HOOF)

    leg(1, coat[0])
    leg(3, coat[0])
    # Schweif (fließt nach hinten, bewegt sich mit)
    tail = [(7, 13 + dy), (3, 13 + dy + (frame % 2)), (1, 16 + dy + (frame % 2)), (3, 18 + dy), (6, 16 + dy)]
    st.shade(p, st.poly(tail), [mane[0], mane[0], mane[1], mane[1]], rim=False)
    # Körper: Rumpf, Schulter, Hinterhand, Hals, Kopf
    body = set()
    for x in range(32):
        for y in range(32):
            if ((x - 15) / 7.6) ** 2 + ((y - 16 - dy) / 3.9) ** 2 <= 1:
                body.add((x, y))
    body |= st.disk(20, 16 + dy, 3.6) | st.disk(9.5, 15.5 + dy, 4.2)
    neck = st.poly([(19, 14 + dy), (22, 9 + dy), (25, 6 + dy), (27.5, 8 + dy), (24.5, 14 + dy), (22, 18 + dy)])
    head = st.poly([(24, 5 + dy), (27, 3.5 + dy), (30, 6.5 + dy), (31.5, 9 + dy), (29.5, 10.5 + dy), (26.5, 9.5 + dy), (24.5, 8 + dy)])
    ear = st.poly([(24.5, 5 + dy), (25.5, 1.5 + dy), (26.8, 4.8 + dy)])
    shape = body | neck | head | ear
    st.shade(p, shape, coat)
    # Mähne entlang des Halses
    p.line(20, 12 + dy, 24.5, 5.5 + dy, mane[1], 1.6)
    p.put(23, 6 + dy, mane[0])
    # Auge, Nüstern
    p.put(27, 6 + dy, OUT)
    p.put(30, 9 + dy, coat[0])
    # nahe Beine
    leg(0, coat[1])
    leg(2, coat[1])
    # Satteldecke mit Startnummer
    for x in range(11, 17):
        for y in range(13 + dy, 18 + dy):
            p.put(x, y, CLOTH[1] if y > 13 + dy else CLOTH[2])
    for x in range(11, 17):
        p.put(x, 18 + dy, CLOTH[0])
    dig = DIGITS[str(lane + 1)]
    w = len(dig[0])
    ox = 14 - w // 2
    for r, row in enumerate(dig):
        for c, b in enumerate(row):
            if b == "1":
                p.put(ox + c, 13 + dy + r, silk[0])
    # Zügel
    p.line(20, 10 + dy, 27, 8.5 + dy, rgb("3A2418"), 1.0)
    # Jockey: geduckt, Dress in Bahnfarbe, Kappe dunkler mit hellem Schirm, Stiefel dunkel
    torso = st.poly([(11.5, 11 + dy), (15, 6.5 + dy), (19.5, 7.5 + dy), (17, 11.5 + dy)])
    st.shade(p, torso, silk)
    cap = st.disk(19.5, 5.2 + dy, 2.1)
    st.shade(p, cap, [silk[0], silk[0], silk[1], silk[2]], rim=False)
    p.put(21, 5 + dy, silk[3])
    p.put(21, 6 + dy, SKIN)
    p.line(17, 8.5 + dy, 21, 10 + dy, silk[1], 1.2)          # Arm zum Zügel
    p.put(21, 10 + dy, SKIN)
    p.line(13, 11 + dy, 15, 13 + dy, rgb("201A22"), 1.6)    # Stiefel
    return p.image(outline=OUT)


def mat(fn):
    img = Image.new("RGBA", (16, 16), T)
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = fn(x, y)
    return img


def mat_ramp(r):
    return mat(lambda x, y: r[3] if y == 0 else r[2] if y < 6 else r[1])


def turf():
    """Rasen: gemähte Streifen (4 px breit, zwei Grüntöne), oben eine helle Kante."""
    g = ramp("1E5A24", "286E2C", "348236", "46963E")
    return mat(lambda x, y: g[3] if y == 0 else g[2] if (x // 4) % 2 == 0 else g[1])


def finish():
    """Ziellinie: Schachbrett 4 x 4 (weiß / fast schwarz)."""
    return mat(lambda x, y: rgb("F2F0EA") if ((x // 4) + (y // 4)) % 2 == 0 else rgb("1E1C24"))


def materials():
    m = {
        "hd_turf": turf,
        "hd_finish": finish,
        "hd_rail": lambda: mat_ramp(ramp("8E929E", "C8CCD4", "EEF0F4", "FFFFFF")),
        "hd_gate": lambda: mat_ramp(ramp("3A3E4A", "5E6472", "8E95A2", "BCC4CE")),
        "hd_race_dark": lambda: mat(lambda x, y: rgb("16241C") if y == 0 else rgb("0E1814")),
    }
    for i, s in enumerate(SILKS):
        m["hd_rn%d" % i] = (lambda s=s: mat_ramp(s))
    return m


def icon():
    """Dialog-Icon 32 x 32: das Pferd der Bahn 3 (Gelb) im gestreckten Galopp."""
    return horse(2, 0)


def write(pack):
    a = os.path.join(pack, "assets/kuby")
    tex = os.path.join(a, "textures/block/casino")
    itex = os.path.join(a, "textures/item/casino")
    models = os.path.join(a, "models/item/casino")
    items = os.path.join(a, "items/casino")
    for d in (tex, itex, models, items):
        os.makedirs(d, exist_ok=True)

    def item(name, model):
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump(model, fh)
        with open(os.path.join(items, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/casino/" + name}}, fh)

    B = "kuby:block/casino/"
    n = 0
    for name, fn in materials().items():
        fn().save(os.path.join(tex, name + ".png"))
        faces = {d: {"texture": "#all", "uv": [0, 0, 16, 16]} for d in ("north", "south", "east", "west", "up", "down")}
        item("m_" + name, {"textures": {"all": B + name, "particle": B + name},
                           "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]})
        n += 1
    for lane in range(6):
        for fr in range(4):
            name = "rn_h%d_%d" % (lane, fr)
            horse(lane, fr).save(os.path.join(itex, name + ".png"))
            item(name, {"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/casino/" + name}})
            n += 1
    return n


def sheet(path):
    """Kontaktbogen: alle Pferde 6-fach (Zeile je Bahn, Spalte je Galopp-Bild), rechts 1-fach, unten Materialien."""
    S = 6
    out = Image.new("RGBA", (4 * 32 * S + 60 + 4 * 34 + 20, 6 * 32 * S + 110), (36, 60, 40, 255))
    for lane in range(6):
        for fr in range(4):
            im = horse(lane, fr)
            out.alpha_composite(im.resize((32 * S, 32 * S), Image.NEAREST), (10 + fr * 32 * S, 10 + lane * 32 * S))
            out.alpha_composite(im, (4 * 32 * S + 30 + fr * 34, 10 + lane * 40))
    for i, (_, fn) in enumerate(materials().items()):
        out.alpha_composite(fn().resize((64, 64), Image.NEAREST), (10 + i * 70, 6 * 32 * S + 30))
    out.save(path)


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--sheet":
        sheet(args[1])
    else:
        print(write(args[0] if args else os.path.join(os.path.dirname(HERE), "resourcepack/KubyUI")), "Pferderennen-Modelle")
