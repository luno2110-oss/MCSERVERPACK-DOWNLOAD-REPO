"""Hintergründe für Truhen-Fenster (12.10): ein Bild je Farbwelt und Reihenzahl (1 bis 6), gezeichnet wie die
Vanilla-Truhe (Rahmen, Slots, Spieler-Inventar), aber in den Farben des Menüs. Das Bild steht als Zeichen in der
Schrift kuby:chest und wird über den Fenstertitel gelegt (ChestBg.java im Kern).

Maße wie in Minecraft: Fenster 176 x (114 + 18 * Reihen), Titel bei (8, 6), Truhen-Slots ab (7, 17), Spieler-Inventar
ab y = 30 + 18 * Reihen, Hotbar 58 px tiefer. Zeichen: E000 + 8 * Farbwelt + Reihen; Abstände F808 (-8) und F8A9 (-169).
Reihenfolge der Farbwelten = THEMES in ChestBg.java (neue hinten anhängen).
"""
import json
import os

from PIL import Image, ImageDraw

W = 176


def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


# Farbwelt: (Licht, Fläche, Schatten, Slot innen, Slot-Schatten, Akzent-Linie unter dem Titel)
THEMES = [
    ("market", ("FFF0C8", "E2C27A", "8A6A2A", "A8884A", "4A3612", "C8902A")),     # Markt: Bernstein
    ("shop", ("E4FFE8", "9ED8A6", "3E7A48", "6EA878", "1E4426", "2E9A48")),       # Server-Shop: Smaragd
    ("storage", ("F4DCC0", "C49A6C", "6A4626", "946A44", "3A2410", "8A5A2A")),    # Lager: Holz
    ("pickup", ("E8F6FF", "A8D0EC", "4A7AA0", "7AA4C8", "24425E", "3A8AC8")),     # Abholbox: Himmelblau
    ("heads", ("F4E4FF", "C8A8E8", "6A4A94", "9A7AC0", "36244E", "8A5AC8")),      # Köpfe: Violett
    ("sell", ("E4FFFA", "98DCD0", "3A7A70", "6AAEA4", "1C423C", "2AA090")),       # Verkaufen: Türkis
    ("steel", ("F4F8FC", "BCC6D0", "5A6470", "8C96A2", "2C333A", "6A7A8C")),      # Kit-Vorschau: Stahl
    ("sky", ("FFFFFF", "D4ECFC", "6A98BC", "9CC4E4", "34587A", "4AA0E0")),        # Skyblock: Wolken
]
ROWS = range(1, 7)


def frame(d, x0, y0, x1, y1, light, face, shadow):
    """Vanilla-Rahmen: schwarzer Umriss mit runden Ecken, innen oben/links hell, unten/rechts Schatten."""
    k = (0, 0, 0, 255)
    d.rectangle((x0 + 2, y0 + 2, x1 - 2, y1 - 2), fill=face)
    # Umriss
    d.line((x0 + 2, y0, x1 - 3, y0), fill=k)
    d.line((x0, y0 + 2, x0, y1 - 3), fill=k)
    d.line((x0 + 3, y1, x1 - 2, y1), fill=k)
    d.line((x1, y0 + 3, x1, y1 - 2), fill=k)
    for px, py in ((x0 + 1, y0 + 1), (x1 - 2, y0 + 1), (x1 - 1, y0 + 2), (x0 + 1, y1 - 2), (x0 + 2, y1 - 1), (x1 - 1, y1 - 1)):
        d.point((px, py), fill=k)
    # Licht oben/links (2 px), Schatten unten/rechts (2 px)
    d.line((x0 + 2, y0 + 1, x1 - 3, y0 + 1), fill=light)
    d.line((x0 + 1, y0 + 2, x0 + 1, y1 - 3), fill=light)
    d.line((x0 + 2, y0 + 2, x1 - 4, y0 + 2), fill=light)
    d.line((x0 + 2, y0 + 2, x0 + 2, y1 - 4), fill=light)
    d.line((x0 + 3, y1 - 1, x1 - 2, y1 - 1), fill=shadow)
    d.line((x1 - 1, y0 + 3, x1 - 1, y1 - 2), fill=shadow)
    d.line((x0 + 4, y1 - 2, x1 - 2, y1 - 2), fill=shadow)
    d.line((x1 - 2, y0 + 4, x1 - 2, y1 - 2), fill=shadow)


def slot(d, x, y, light, inner, dark):
    """Slot 18 x 18 ab (x, y): oben/links dunkel, unten/rechts hell, innen inner."""
    d.rectangle((x, y, x + 17, y + 17), fill=inner)
    d.line((x, y, x + 16, y), fill=dark)
    d.line((x, y, x, y + 16), fill=dark)
    d.line((x + 1, y + 17, x + 17, y + 17), fill=light)
    d.line((x + 17, y + 1, x + 17, y + 17), fill=light)


def draw(colors, rows):
    light, face, shadow, inner, dark, accent = (rgb(c) for c in colors)
    h = 114 + 18 * rows
    img = Image.new("RGBA", (W, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    frame(d, 0, 0, W - 1, h - 1, light, face, shadow)
    # Akzent-Linie unter dem Titel (Zier, 1 px mit hellem Schatten)
    d.line((7, 15, W - 8, 15), fill=accent)
    d.line((8, 16, W - 7, 16), fill=light)
    for r in range(rows):
        for c in range(9):
            slot(d, 7 + 18 * c, 17 + 18 * r, light, inner, dark)
    # zwischen Truhe und Spieler-Inventar bleibt frei: dort steht die Beschriftung „Inventar“
    inv = 30 + 18 * rows
    for r in range(3):
        for c in range(9):
            slot(d, 7 + 18 * c, inv + 18 * r, light, inner, dark)
    for c in range(9):
        slot(d, 7 + 18 * c, inv + 58, light, inner, dark)
    return img


def build(out):
    """Schreibt die Bilder (kuby:gui/chest/<welt>_<reihen>.png) und die Schrift kuby:chest."""
    tex = os.path.join(out, "assets/kuby/textures/gui/chest")
    os.makedirs(tex, exist_ok=True)
    providers = [{"type": "space", "advances": {"": -8, "": -169}}]
    for t, (name, colors) in enumerate(THEMES):
        for rows in ROWS:
            img = draw(colors, rows)
            img.save(os.path.join(tex, "%s_%d.png" % (name, rows)))
            providers.append({"type": "bitmap", "file": "kuby:gui/chest/%s_%d.png" % (name, rows), "height": img.height,
                              "ascent": 13, "chars": [chr(0xE000 + 8 * t + rows)]})
    fdir = os.path.join(out, "assets/kuby/font")
    os.makedirs(fdir, exist_ok=True)
    with open(os.path.join(fdir, "chest.json"), "w", encoding="utf-8") as f:
        json.dump({"providers": providers}, f, ensure_ascii=True, separators=(",", ":"))
    return [name for name, _ in THEMES]


if __name__ == "__main__":
    import sys
    names = build(sys.argv[1])
    print("ok Truhen-Hintergründe:", ", ".join(names))
