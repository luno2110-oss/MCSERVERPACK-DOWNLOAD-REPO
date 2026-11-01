"""Schneidet die Bausteine des Profils (/stats in Survival) aus den nativen Referenzbildern (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_profile.py <ordner-mit-n_53..n_56.png> tools/assets/profile <vanilla-ordner>
Die n_XX.png sind Aufnahmen aus dem Release "Neue-referenzen" (wie bei extract_playtime.py), der Vanilla-Ordner ist
derselbe wie bei build_ui_pack.py (KUBY_VANILLA, für die großen Ziffern aus assets/minecraft/textures/font/ascii.png).
Danach tools/build_ui_pack.py.

Bilder: 53 Übersicht, 54 Berufe, 55 Quests, 56 Welt.

Ausgabe (Präfix pf_ im Kit):
  box_<name>.png      Kopf, Reiter an/aus, Bereich, Berufe-Karte (normal, Hauptberuf), Zeile unten, Quest-Rang, Karten
                      (Quests, Serie, beste Serie, grau, rot, gold), Grundstücke
  art_<name>.png      Avatar, Rang-Abzeichen, Reiter-Symbole, Symbole der Übersicht, Berufe, Karten, Grundstück
  art_bar_l/r.png     Enden des Balkens (10 hoch), strip_bar.png Mittelstück; art_fill_<farbe>_l/r.png und
  strip_fill_<farbe>.png die Füllung (grün, gold)
  art_big_<z>.png     große Ziffern (Minecraft-Schrift doppelt), weiß zum Einfärben
  art.json            Lage der Grafiken
"""
import json
import os
import sys
from collections import Counter

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jewelshop import art  # noqa: E402
from extract_support import nine_clean  # noqa: E402

BG = (0x07, 0x1B, 0x28)
# Balken der Übersicht: x 10..379, Zeilen 127..136; Füllung (37 von 250) bis x 65
BAR_X0, BAR_X1, BAR_Y = 10, 379, 127
BAR_H = 10
# Hauptberuf-Karte (n_54): Balken x 206..380, Zeilen 235..244, Füllung gold
GOLD_X0, GOLD_Y = 206, 235
TABS = [1 + 98 * i for i in range(4)]
JOBS = ("graeber", "holzfaeller", "miner", "farmer", "sammler", "jaeger", "abenteurer", "angler")
BIG = "0123456789.,/%"


def load(ref, i):
    return np.array(Image.open(os.path.join(ref, "n_%02d.png" % i)).convert("RGB")).astype(int)


def rgba(r, mask):
    out = np.zeros((r.shape[0], r.shape[1], 4), dtype=np.uint8)
    out[..., :3] = r
    out[..., 3] = np.where(mask, 255, 0)
    return Image.fromarray(out, "RGBA")


def bar_parts(a, y, out):
    """Leerer Balken (Quest-Rang in n_55, Zeilen y..y+9): Enden (4 Spalten), Mittelstück (eine Spalte als Füllung
    beliebiger Länge). Außen liegt die Füllung des Kastens (häufigste Farbe darum), die wird durchsichtig."""
    r = a[y:y + BAR_H]
    around = Counter(map(tuple, a[y - 3:y - 1, BAR_X0:BAR_X1].reshape(-1, 3))).most_common(1)[0][0]
    bg = np.abs(r - np.array(around)).sum(axis=2) > 10
    rgba(r[:, BAR_X0:BAR_X0 + 4], bg[:, BAR_X0:BAR_X0 + 4]).save(os.path.join(out, "art_bar_l.png"))
    rgba(r[:, BAR_X1 - 3:BAR_X1 + 1], bg[:, BAR_X1 - 3:BAR_X1 + 1]).save(os.path.join(out, "art_bar_r.png"))
    strip = np.zeros((BAR_H, 257, 4), dtype=np.uint8)
    strip[..., :3] = r[:, 200][:, None, :]
    strip[..., 3] = 255
    Image.fromarray(strip, "RGBA").save(os.path.join(out, "strip_bar.png"))


def fill_parts(bar, empty, name, out, shape=None):
    """Füllung eines Balkens (Ausschnitt 10 hoch, ab dem linken Ende): alles, was vom leeren Balken abweicht. Enden je 2
    Spalten, Mitte als Füllung. shape: Masken (links, Mitte, rechts) einer anderen Füllung, wenn kein leerer Balken
    gleicher Umgebung da ist (gold auf goldener Karte); dann endet die Füllung, wo Zeile 4 wieder leer ist."""
    if shape is None:
        diff = np.abs(bar - empty).sum(axis=2) > 10
        # nur das Innere (Zeilen 2..7, ohne die äußeren Spalten): außen liegt der Hintergrund des Kastens
        diff[:2] = diff[8:] = False
        diff[:, :2] = diff[:, -2:] = False
        cols = np.where(diff.any(axis=0))[0]
        c0, c1 = int(cols.min()), int(cols.max())
        ml, mm, mr = diff[:, c0:c0 + 2], diff[:, (c0 + c1) // 2], diff[:, c1 - 1:c1 + 1]
    else:
        ml, mm, mr = shape
        c0 = 2
        inner = empty[4, 100]
        c1 = c0
        while c1 + 1 < bar.shape[1] and np.abs(bar[4, c1 + 1] - inner).sum() > 10:
            c1 += 1
    rgba(bar[:, c0:c0 + 2], ml).save(os.path.join(out, "art_fill_%s_l.png" % name))
    rgba(bar[:, c1 - 1:c1 + 1], mr).save(os.path.join(out, "art_fill_%s_r.png" % name))
    mid = (c0 + c1) // 2
    strip = np.zeros((BAR_H, 257, 4), dtype=np.uint8)
    strip[..., :3] = bar[:, mid][:, None, :]
    strip[..., 3] = np.where(mm, 255, 0)[:, None]
    Image.fromarray(strip, "RGBA").save(os.path.join(out, "strip_fill_%s.png" % name))
    return {"x": c0, "w": c1 - c0 + 1}, (ml, mm, mr)


# Eigene Mini-Symbole (7 x 7) im Stil der Berufe-Symbole für Berufe ohne Vorlage: Züchter (Weizen), Verzauberer (Buch)
OWN_ICONS = {
    "zuechter": ["...Y...", "..YYY..", "...Y...", "..YGY..", "...G...", "..G....", ".G....."],
    "verzauberer": [".PPPPP.", "PWWWWPp", "PWWWWPp", "PWPPWPp", "PWWWWPp", "PPPPPPp", ".ppppp."],
}
OWN_COLORS = {"Y": (0xE8, 0xC4, 0x4A), "G": (0x5C, 0x8A, 0x2C), "P": (0x8E, 0x4C, 0xC8), "p": (0x4E, 0x24, 0x78),
              "W": (0xE8, 0xE0, 0xF4)}


def own_icons(out):
    for name, rows in OWN_ICONS.items():
        img = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in OWN_COLORS:
                    img.putpixel((x, y), OWN_COLORS[ch] + (255,))
        img.save(os.path.join(out, "art_job_%s.png" % name))


def big_digits(vanilla, out):
    """Ziffern der Minecraft-Schrift doppelt groß (Zeilen 0..6 der 8 x 8-Zelle), weiß."""
    font = Image.open(os.path.join(vanilla, "assets/minecraft/textures/font/ascii.png")).convert("RGBA")
    adv = {}
    for ch in BIG:
        code = ord(ch)
        cell = font.crop(((code % 16) * 8, (code // 16) * 8, (code % 16) * 8 + 8, (code // 16) * 8 + 8))
        a = np.array(cell)
        cols = np.where(a[..., 3].any(axis=0))[0]
        w = int(cols.max()) + 1 if len(cols) else 3
        glyph = cell.crop((0, 0, w, 7)).resize((2 * w, 14), Image.NEAREST)
        g = np.array(glyph)
        g[..., :3] = 255
        name = {".": "dot", ",": "comma", "/": "slash", "%": "pct"}.get(ch, ch)
        Image.fromarray(g, "RGBA").save(os.path.join(out, "art_big_%s.png" % name))
        adv[ch] = 2 * (w + 1)
    return adv


def main():
    ref, out, vanilla = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(out, exist_ok=True)
    im = {i: load(ref, i) for i in range(53, 57)}
    boxes = {
        "head": (53, (0, 0, 390, 52)), "tab_on": (53, (1, 63, 94, 34)), "tab_off": (53, (99, 63, 94, 34)),
        "panel": (53, (0, 108, 390, 142)),
        "job": (54, (0, 108, 192, 34)), "job_main": (54, (198, 216, 192, 34)), "footer": (54, (0, 252, 390, 25)),
        "rank": (55, (0, 108, 390, 70)), "card_quest": (55, (0, 189, 126, 52)), "card_streak": (55, (132, 189, 126, 52)),
        "card_best": (55, (264, 189, 126, 52)),
        "card_gray": (56, (0, 108, 126, 52)), "card_red": (56, (132, 108, 126, 52)), "card_gold": (56, (264, 108, 126, 52)),
        "claims": (56, (0, 171, 390, 61)),
    }
    for name, (i, (x, y, w, h)) in boxes.items():
        nine_clean(im[i], x, y, w, h).save(os.path.join(out, "box_%s.png" % name))

    rows = (149, 167, 185, 203, 221)
    arts = {"avatar": (53, (6, 10, 43, 43)), "rank": (53, (44, 15, 56, 27))}
    # Reiter-Symbole: im gewählten Reiter hell (Bild des Reiters), sonst gedämpft (Bild eines anderen Reiters)
    for k, name in enumerate(("overview", "jobs", "quests", "world")):
        arts["tab_%s_on" % name] = (53 + k, (TABS[k] + 28, 66, TABS[k] + 66, 83))
        arts["tab_%s_off" % name] = (54 if k == 0 else 53, (TABS[k] + 28, 66, TABS[k] + 66, 83))
    for name, y in zip(("time", "rank_row", "coins", "friends", "married"), rows):
        arts["ov_" + name] = (53, (8, y - 6, 31, y + 12))
    for name, y in zip(("since", "team", "jewels", "badges", "farmpass"), rows):
        arts["ov_" + name] = (53, (200, y - 6, 224, y + 12))
    for k, name in enumerate(JOBS):
        x, y = (k % 2) * 198, 108 + (k // 2) * 36
        arts["job_" + name] = (54, (x + 5, y + 4, x + 19, y + 17))
    arts.update({
        "q_quests": (55, (20, 195, 52, 216)), "q_streak": (55, (150, 195, 188, 216)),
        "q_best": (55, (270, 195, 301, 216)),
        "w_mined": (56, (14, 113, 46, 134)), "w_deaths": (56, (150, 113, 188, 134)),
        "w_sold": (56, (278, 113, 311, 134)), "w_claims": (56, (10, 177, 30, 197)),
    })
    pos = {}
    for name, (i, rect) in arts.items():
        img, xy = art(im[i], *rect)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": i}
    bar_parts(im[55], 145, out)
    # leerer Balken gleicher Lage: Quest-Rang (n_55, Zeilen 145..154)
    empty = im[55][145:145 + BAR_H, BAR_X0:BAR_X1 + 1]
    green, shape = fill_parts(im[53][BAR_Y:BAR_Y + BAR_H, BAR_X0:BAR_X1 + 1], empty, "green", out)
    gold, _ = fill_parts(im[54][GOLD_Y:GOLD_Y + BAR_H, GOLD_X0:GOLD_X0 + 175], empty, "gold", out, shape)
    fills = {"green": green, "gold": gold}
    adv = big_digits(vanilla, out)
    own_icons(out)
    with open(os.path.join(out, "art.json"), "w", encoding="utf-8") as f:
        json.dump({"art": pos, "fill": fills, "big_advance": adv}, f, indent=1, sort_keys=True, ensure_ascii=False)
    print("fertig", len(boxes), "Kästen,", len(arts), "Grafiken")


if __name__ == "__main__":
    main()
