#!/usr/bin/env python3
"""Grafik des Fang-Minispiels (Angeln, wirklich seltene Fische): Schrift kuby:fishgame für den Untertitel.

Drei Reihen übereinander (Höhe über der Grundlinie = ascent):
  - Fortschritt (oben, 4 px): Hintergrund E030, goldene Füllstücke E040.. (1, 2, 4, ... 64 px)
  - Fisch-Leiste (12 px): Wasser-Hintergrund E000, Fisch E001
  - Fangbereich (8 px): Hintergrund E002, grüne Stücke E010.. (Fisch drin) und orange Stücke E020.. (Fisch draußen)
Alle Leisten sind W = 110 px breit. Java (FishGame) setzt die Stücke mit den Abstands-Zeichen von kuby:ui zusammen.
Farben aus den Rampen der Icon-Datenbank (Kontur fast schwarz, Licht oben, Schatten unten).
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "icon_db"))
from materials import OUTLINE, RAMPS  # noqa: E402

W = 110
PIECES = (1, 2, 4, 8, 16, 32, 64)


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


INK = rgb(OUTLINE)


def box(w, h, top, mid, bottom, rim=None):
    """Leiste mit 1 px Kontur, oben heller, unten dunkler."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            edge = x in (0, w - 1) or y in (0, h - 1)
            corner = (x in (0, w - 1)) and (y in (0, h - 1))
            if corner:
                continue
            if edge:
                px[x, y] = (rim or INK) + (255,)
            elif y == 1:
                px[x, y] = top + (255,)
            elif y == h - 2:
                px[x, y] = bottom + (255,)
            else:
                px[x, y] = mid + (255,)
    return img


def fill(w, h, light, mid, dark):
    """Füllstück ohne Kontur: obere Reihe Licht, untere Schatten."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = (light if y == 0 else dark if y == h - 1 else mid) + (255,)
    return img


def water(w, h):
    """Wasser-Leiste: tiefes Blau, oben eine helle Wellenlinie (Muster einmal gebrochen)."""
    blau = [rgb(c) for c in RAMPS["blau"]]
    img = box(w, h, blau[2], blau[1], blau[0])
    px = img.load()
    for x in range(2, w - 2):
        if (x // 3) % 4 == 1 and x != 50:
            px[x, 2] = blau[4] + (255,)
    # ein paar Luftblasen, ungleich verteilt
    for (x, y) in ((17, 7), (18, 5), (63, 8), (91, 6)):
        px[x, y] = blau[5] + (255,)
    return img


def fish():
    """Fisch 11 x 6 (Kopf links) in Gold, 1 px Kontur, Licht oben links."""
    g = [rgb(c) for c in RAMPS["gold"]]
    rows = [
        "..ooooo....",
        ".oBBBBBo..o",
        "oeBBBBBBooo",
        "oBBBMMMMBoo",
        ".oMMMMMDo.o",
        "..ooooo....",
    ]
    img = Image.new("RGBA", (11, 12), (0, 0, 0, 0))
    px = img.load()
    col = {"o": INK, "B": g[6], "M": g[4], "D": g[2], "e": (20, 20, 30)}
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch in col:
                px[x, y + 3] = col[ch] + (255,)
    px[2, 4] = g[8] + (255,)          # Glanz oben links
    return img


def build(out):
    tex = os.path.join(out, "assets/kuby/textures/fishgame")
    font = os.path.join(out, "assets/kuby/font")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(font, exist_ok=True)
    providers = []

    def save(img, name, ascent, char):
        # Minecraft verlangt ascent <= height: unten transparent auffüllen (die Grafik bleibt oben am ascent)
        if img.height < ascent:
            pad = Image.new("RGBA", (img.width, ascent), (0, 0, 0, 0))
            pad.paste(img, (0, 0))
            img = pad
        img.save(os.path.join(tex, name + ".png"))
        providers.append({"type": "bitmap", "file": "kuby:fishgame/" + name + ".png", "height": img.height,
                          "ascent": ascent, "chars": [char]})

    gruen = [rgb(c) for c in RAMPS["smaragd"]]
    feuer = [rgb(c) for c in RAMPS["feuer"]]
    gold = [rgb(c) for c in RAMPS["gold"]]
    eisen = [rgb(c) for c in RAMPS["eisen"]]

    save(water(W, 12), "lane", 16, chr(0xE000))
    save(fish(), "fish", 16, chr(0xE001))
    save(box(W, 8, eisen[2], eisen[1], eisen[0]), "zone_bg", 2, chr(0xE002))
    for i, w in enumerate(PIECES):
        save(fill(w, 6, gruen[6], gruen[4], gruen[2]), "zone_in_%d" % w, 1, chr(0xE010 + i))
        save(fill(w, 6, feuer[min(len(feuer) - 1, 6)], feuer[4], feuer[2]), "zone_out_%d" % w, 1, chr(0xE020 + i))
        save(fill(w, 2, gold[7], gold[5], gold[3]), "prog_%d" % w, 21, chr(0xE040 + i))
    save(box(W, 4, eisen[1], eisen[0], eisen[0]), "prog_bg", 22, chr(0xE030))
    with open(os.path.join(font, "fishgame.json"), "w", encoding="utf-8") as f:
        json.dump({"providers": providers}, f, ensure_ascii=True, indent=1)
    print("ok Fang-Minispiel")


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "resourcepack/KubyUI")
