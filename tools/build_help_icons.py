"""Eigene Icons für die Survival-Hilfe (16 x 16, Vanilla-Stil) für Themen ohne passendes Vanilla-Item.

Gezeichnet mit Pix aus build_angel_icons.py (Formen mit Schattierung und dunklerem Rand). Reihenfolge = ICONS,
die Namen landen als HELP_... in KitData.Icon (build_kit.py liest die Liste).
"""
import math

from build_angel_icons import Pix, rgb, mix


def token():
    p = Pix()
    p.disk(8, 8, 7.0, rgb("C8901A"))
    p.disk(8, 8, 5.4, rgb("F2C23A"), g=1)
    img = p.image()
    light, shadow = rgb("FFF4C0"), rgb("B07A14")
    for x in range(5, 11):
        for y in (5, 6):
            img.putpixel((x, y), light)
        img.putpixel((x + 1, 7) if x >= 9 else (x, 7), shadow if x >= 9 or x < 7 else light)
    for y in range(7, 12):
        img.putpixel((7, y), light)
        img.putpixel((8, y), light)
        img.putpixel((9, y), shadow)
    img.putpixel((11, 5), shadow)
    img.putpixel((11, 6), shadow)
    img.putpixel((7, 12), shadow)
    img.putpixel((8, 12), shadow)
    img.putpixel((5, 3), rgb("FFF8D8"))
    img.putpixel((4, 4), rgb("FFF8D8"))
    return img


def people():
    p = Pix()
    # hinten rechts (grün), davor links (blau)
    p.poly([(7.5, 15.5), (8.5, 11), (11, 9.6), (13, 9.6), (15.5, 11), (16, 15.5)], rgb("4CC040"), g=0)
    p.disk(12, 6.3, 2.9, rgb("E8B48A"), g=1)
    p.rect(9, 3, 14, 4, rgb("6A4220"), g=2)
    p.put(9, 5, rgb("6A4220"), g=2)
    p.put(14, 5, rgb("6A4220"), g=2)
    p.poly([(0, 15.5), (0.5, 11.5), (3, 10), (6.5, 10), (9, 11.5), (9.5, 15.5)], rgb("2FA8E0"), g=3)
    p.disk(4.8, 6.8, 3.0, rgb("F0C098"), g=4)
    p.rect(2, 3, 7, 4, rgb("3A2410"), g=5)
    p.put(2, 5, rgb("3A2410"), g=5)
    p.put(7, 5, rgb("3A2410"), g=5)
    img = p.image()
    for x, y in ((3, 7), (6, 7), (11, 6), (13, 6)):
        img.putpixel((x, y), rgb("2A2A3A"))
    return img


def grave():
    p = Pix()
    p.rect(4, 6, 11, 13, rgb("9A9AA4"))
    p.disk(7.9, 6.2, 3.95, rgb("9A9AA4"))
    p.rect(7, 4, 8, 10, rgb("5E5E6A"), g=1)
    p.rect(5, 6, 10, 7, rgb("5E5E6A"), g=1)
    p.rect(1, 13, 14, 15, rgb("4CA03C"), g=2)
    img = p.image()
    for x, y in ((2, 12), (13, 12), (3, 12)):
        img.putpixel((x, y), rgb("6CC85A"))
    img.putpixel((5, 4), rgb("C8C8D2"))
    img.putpixel((6, 3), rgb("C8C8D2"))
    return img


def rings():
    p = Pix()
    gold, gold2 = rgb("F2C23A"), rgb("E0A82A")
    p.ring(5.6, 10, 2.5, 4.4, gold)
    p.ring(10.4, 10, 2.5, 4.4, gold2, g=1)
    # vorn wieder den linken Ring über den rechten legen (verschlungen)
    p.arc(5.6, 10, 2.5, 4.4, 300, 60, gold, g=0)
    p.poly([(10.4, 1), (12.8, 3.4), (10.4, 5.8), (8, 3.4)], rgb("F060A0"), g=2)
    img = p.image()
    img.putpixel((10, 2), rgb("FFC8E0"))
    return img


def lifebuoy():
    p = Pix()
    for i in range(8):
        col = rgb("E03C40") if i % 2 == 0 else rgb("F4F4F4")
        p.arc(8, 8, 3.2, 7.0, i * 45 + 22.5, i * 45 + 67.5, col, g=i % 2)
    img = p.image()
    return img


def discord():
    rows = [
        "................",
        "................",
        "...bb......bb...",
        "..bBBBBBBBBBBb..",
        "..BBBBBBBBBBBB..",
        ".bBBBBBBBBBBBBb.",
        ".BBBBBBBBBBBBBB.",
        ".BBBWWBBBBWWBBB.",
        "bBBWWWWBBWWWWBBb",
        "BBBWWWWBBWWWWBBB",
        "BBBBWWBBBBWWBBBB",
        "BBBBBBBBBBBBBBBB",
        ".BBBBBBBBBBBBBB.",
        ".bbBBBbbbbBBBbb.",
        "...bb......bb...",
        "................",
    ]
    pal = {"B": rgb("5865F2"), "b": rgb("3A44B8"), "W": rgb("FFFFFF")}
    p = Pix()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in pal:
                p.put(x, y, pal[c], g=1 if c == "W" else 0)
    return p.image(light=False)


def chart():
    p = Pix()
    p.rect(1, 2, 14, 14, rgb("F4F0E4"))
    p.rect(3, 10, 4, 12, rgb("E03C40"), g=1)
    p.rect(6, 7, 7, 12, rgb("F2C23A"), g=2)
    p.rect(9, 8, 10, 12, rgb("4C8CD8"), g=3)
    p.rect(12, 4, 12, 12, rgb("3CB43C"), g=4)
    img = p.image()
    for x in range(2, 14):
        img.putpixel((x, 13), rgb("8A8478"))
    img.putpixel((13, 4), mix(rgb("3CB43C"), rgb("1E6A1E"), 0.5))
    for x in range(13, 14):
        for y in range(5, 13):
            img.putpixel((x, y), rgb("2E8C2E"))
    return img


def infobar():
    p = Pix()
    p.rect(0, 3, 15, 12, rgb("26324A"))
    p.rect(2, 5, 9, 6, rgb("E060C0"), g=1)
    p.rect(10, 5, 13, 6, rgb("4A3A5A"), g=2)
    img = p.image()
    for x in range(2, 11):
        img.putpixel((x, 9), rgb("E8E8F0"))
    for x in range(2, 8):
        img.putpixel((x, 10), rgb("A8B0C0"))
    img.putpixel((12, 9), rgb("F2C23A"))
    img.putpixel((13, 9), rgb("F2C23A"))
    return img


def chair():
    p = Pix()
    wood, dark = rgb("A86E3A"), rgb("7A4A22")
    p.rect(3, 1, 4, 14, dark)
    p.rect(5, 2, 5, 3, wood, g=1)
    p.rect(5, 5, 5, 6, wood, g=1)
    p.rect(3, 9, 12, 10, wood, g=2)
    p.rect(4, 7, 12, 8, rgb("D83040"), g=3)
    p.rect(11, 11, 12, 14, dark, g=4)
    p.rect(5, 11, 5, 14, wood, g=5)
    return p.image()


def ballot():
    p = Pix()
    p.rect(3, 1, 12, 14, rgb("F4F0E4"))
    p.line(4.8, 8.3, 7.0, 10.8, rgb("3CB43C"), 1.7, g=1)
    p.line(7.0, 10.8, 11.3, 4.5, rgb("3CB43C"), 1.7, g=1)
    img = p.image()
    for x in range(5, 11):
        img.putpixel((x, 3), rgb("B0A898"))
    return img


ICONS = [
    ("help_token", token), ("help_people", people), ("help_grave", grave), ("help_rings", rings),
    ("help_lifebuoy", lifebuoy), ("help_discord", discord), ("help_chart", chart), ("help_infobar", infobar),
    ("help_chair", chair), ("help_ballot", ballot),
]


def build():
    """Gibt [(name, bild)] zurück."""
    return [(name, fn()) for name, fn in ICONS]


if __name__ == "__main__":
    import sys
    from PIL import Image
    imgs = build()
    sheet = Image.new("RGBA", (20 * len(imgs), 20), (40, 50, 80, 255))
    for i, (_, img) in enumerate(imgs):
        sheet.alpha_composite(img, (20 * i + 2, 2))
    sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[1])
