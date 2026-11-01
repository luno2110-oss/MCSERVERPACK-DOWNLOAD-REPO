"""Grafiken der Skyblock-Generatoren: je Art ein Bild für die Dialoge (32 px, Sprites gn_<art>) und ein Item-Bild
(16 px, Item-Modell kuby:generator/<art>). Stil wie die Menü-Icons (build_hub_icons.Art).

Ergebnis: tools/assets/generators/art_<art>.png und item_<art>.png
"""
import os

from build_hub_icons import Art, GOLD, WOOD, trim

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "assets", "generators")

# Art -> (Kern-Farbe, Zeichnung des Ertrags oben)
STEEL = "8A94A4"
STEEL_D = "5A6474"


def machine(a, core, n=32):
    """Maschine: Stahlgehäuse mit Nieten, leuchtendes Fenster in der Kern-Farbe."""
    s = n / 32
    a.ground_shadow(round(3 * s), round(27 * s), round(29 * s), round(31 * s))
    a.rect(round(4 * s), round(12 * s), round(27 * s), round(29 * s), STEEL)
    a.rect(round(4 * s), round(12 * s), round(27 * s), round(14 * s), "AAB4C2", mode="soft")
    a.rect(round(8 * s), round(17 * s), round(23 * s), round(26 * s), "2A3040", mode="flat")
    a.rect(round(10 * s), round(19 * s), round(21 * s), round(24 * s), core, mode="soft", edge=False)
    if n >= 24:
        a.dots([(6, 15), (25, 15), (6, 27), (25, 27)], "D8DEE8")
        a.dots([(11, 20), (12, 20)], "FFFFFF")


def gen_erz(n=32):
    a = Art(n)
    machine(a, "FF8A1E", n)
    s = n / 32
    a.ellipse(round(7 * s), round(3 * s), round(16 * s), round(12 * s), "4A4A52")
    a.ellipse(round(15 * s), round(2 * s), round(25 * s), round(12 * s), "C8C8D0")
    a.dots([(round(18 * s), round(5 * s)), (round(21 * s), round(8 * s))], "E8B07A")
    if n >= 24:
        a.dots([(10, 6), (12, 8)], "1A1A20")
        a.sparkle(28, 4, 2, "FFE0B0")
    return a


def gen_holz(n=32):
    a = Art(n)
    machine(a, "8AD050", n)
    s = n / 32
    for i, x in enumerate((6, 13, 20)):
        a.rect(round(x * s), round((4 + (i % 2) * 2) * s), round((x + 6) * s), round(11 * s), WOOD)
        a.ellipse(round(x * s), round((3 + (i % 2) * 2) * s), round((x + 6) * s), round((6 + (i % 2) * 2) * s), "D8B070", mode="soft")
    if n >= 24:
        a.sparkle(28, 4, 1)
    return a


def gen_farm(n=32):
    a = Art(n)
    machine(a, "FFE070", n)
    s = n / 32
    for x in (8, 13, 18, 23):
        a.line([(round(x * s), round(11 * s)), (round(x * s), round(4 * s))], "C8A030", max(1, round(2 * s)))
        a.ellipse(round((x - 2) * s), round(2 * s), round((x + 2) * s), round(7 * s), "F2C443")
    if n >= 24:
        a.sparkle(28, 3, 1)
    return a


def gen_mob(n=32):
    a = Art(n)
    machine(a, "C83AE8", n)
    s = n / 32
    a.rect(round(9 * s), round(2 * s), round(22 * s), round(11 * s), "E8E4DA")
    a.rect(round(11 * s), round(5 * s), round(13 * s), round(7 * s), "1A1A20", mode="flat", edge=False)
    a.rect(round(18 * s), round(5 * s), round(20 * s), round(7 * s), "1A1A20", mode="flat", edge=False)
    if n >= 24:
        a.dots([(14, 9), (16, 9), (18, 9)], "1A1A20")
        a.sparkle(28, 4, 1, "F0C8FF")
    return a


def gen_nether(n=32):
    a = Art(n)
    machine(a, "F2545B", n)
    s = n / 32
    a.poly([(round(6 * s), round(11 * s)), (round(11 * s), round(2 * s)), (round(16 * s), round(11 * s))], "8A2A2A")
    a.poly([(round(14 * s), round(11 * s)), (round(20 * s), round(3 * s)), (round(26 * s), round(11 * s))], "F4F0E8")
    if n >= 24:
        a.dots([(10, 7), (19, 7), (21, 9)], "FFB0A0")
        a.sparkle(28, 3, 1, "FFB0A0")
    return a


def gen_edelstein(n=32):
    a = Art(n)
    machine(a, "62D6E0", n)
    s = n / 32
    a.poly([(round(9 * s), round(6 * s)), (round(12 * s), round(2 * s)), (round(15 * s), round(6 * s)), (round(12 * s), round(11 * s))], "3AD86A")
    a.poly([(round(16 * s), round(5 * s)), (round(20 * s), round(1 * s)), (round(24 * s), round(5 * s)), (round(20 * s), round(11 * s))], "62D6E0")
    if n >= 24:
        a.dots([(11, 4), (19, 3)], "FFFFFF")
        a.sparkle(28, 8, 2)
        a.sparkle(5, 4, 1)
    return a


def gen_geld(n=32):
    a = Art(n)
    machine(a, "F8D35A", n)
    s = n / 32
    for i, (x, y) in enumerate(((7, 6), (15, 3), (22, 7))):
        for k in range(3 - (i % 2)):
            a.ellipse(round(x * s), round((y + 4 - 2 * k) * s), round((x + 6) * s), round((y + 7 - 2 * k) * s), GOLD)
    if n >= 24:
        a.dots([(17, 4), (18, 4), (9, 7)], "FFF4C0")
        a.sparkle(28, 3, 2)
    return a


def gen_xp(n=32):
    a = Art(n)
    machine(a, "9EF07A", n)
    s = n / 32
    a.ellipse(round(10 * s), round(1 * s), round(21 * s), round(11 * s), "7AE84A")
    a.ellipse(round(13 * s), round(3 * s), round(18 * s), round(8 * s), "D8FF9A", mode="soft", edge=False)
    if n >= 24:
        a.dots([(13, 3), (14, 3)], "FFFFFF")
        a.sparkle(26, 3, 2, "D8FFB0")
        a.sparkle(5, 6, 1, "D8FFB0")
    return a


TYPES = {"geld": gen_geld, "xp": gen_xp, "erz": gen_erz, "holz": gen_holz, "farm": gen_farm, "mob": gen_mob, "nether": gen_nether, "edelstein": gen_edelstein}


def build():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in TYPES.items():
        trim(fn(32).render()).save(os.path.join(OUT, "art_%s.png" % name))
        fn(16).render().save(os.path.join(OUT, "item_%s.png" % name))
    return list(TYPES)


if __name__ == "__main__":
    print("ok Generator-Grafiken:", ", ".join(build()))
