"""Schneidet die Bausteine des Spielzeit-Dialogs (/playtime) aus den nativen Referenzbildern (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_playtime.py <ordner-mit-n_00..n_16.png> tools/assets/playtime
Die n_XX.png sind die ersten 17 Aufnahmen aus dem Release "Neue-referenzen" (05.34.18 bis 05.36.xx) in Zeitfolge, auf
GUI-Pixel verkleinert (Ursprung 1332,201, Faktor 3, Pixel 3i+1). Danach tools/build_ui_pack.py.

Bilder: 00 Stufen 1-4 (drei bereit), 01-08 Stufen 5-32, 09 Stufen 29-32 nach dem Abholen, 10-15 Stufen 33-56, 16 Stufen
54-57 (letzte Seite).

Ausgabe (Präfix pt_ im Kit):
  box_<stil>.png      15 x 15 (9-Slice, Ecken 7 x 7): Kopf, Knöpfe der zweiten Zeile, Stufen-Zeilen je Farbgruppe
  art_<name>.png      Grafiken mit transparentem Hintergrund: Geschenk, Sanduhr, Seiten-Knöpfe, Balken
  strip_<name>.png    Füllung eines Balkens mit Farbverlauf (198 x 8, dazu rechts die dunkle Endspalte); das Kit baut
                      daraus je Länge ein Zeichen (Canvas.strip)
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jewelshop import art, nine_auto  # noqa: E402

ROW_Y = (81, 153, 225, 297)       # Stufen-Zeilen (390 x 60)
BAR_X, BAR_DY, BAR_W, BAR_H = 30, 35, 200, 10


def load(ref, i):
    return np.array(Image.open(os.path.join(ref, "n_%02d.png" % i)).convert("RGB")).astype(int)


def cut(a, x, y, w, h, bg):
    """Rechteck als RGBA, Pixel in der Farbe bg (Füllung des Kastens dahinter) transparent."""
    r = a[y:y + h, x:x + w]
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[..., :3] = r
    out[..., 3] = np.where(np.abs(r - np.array(bg)).sum(axis=2) > 6, 255, 0)
    return out


def pill(a, x, y, w=24, h=14):
    """Seiten-Knopf mit Pfeil: außerhalb der Rundung (Farbe der Ecke oben links, von beiden Seiten her) transparent."""
    r = a[y:y + h, x:x + w]
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[..., :3] = r
    out[..., 3] = 255
    outside = r[0, 0]
    for j in range(h):
        for i in range(w // 2):
            if np.abs(r[j, i] - outside).sum() > 12:
                break
            out[j, i, 3] = 0
        for i in range(w - 1, w // 2, -1):
            if np.abs(r[j, i] - outside).sum() > 12:
                break
            out[j, i, 3] = 0
    return Image.fromarray(out, "RGBA")


def main():
    ref, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    im = {i: load(ref, i) for i in range(17)}

    # ---------------- Kästen ----------------
    def row(i, k):
        return (i, (0, ROW_Y[k], 390, 60))

    boxes = {
        "head": (0, (0, 0, 390, 51)),
        "claim": (0, (0, 54, 187, 24)), "idle": (9, (0, 54, 187, 24)), "pager": (0, (203, 54, 187, 24)),
        "btn": (0, (316, 115, 64, 14), (8, 7)),   # Mitte links neben dem Text
        # Stufen: bereit (grün), Gruppen 1-8 grau, 9-16 türkis, 17-24 gold, 25-32 orange, 33 bernstein,
        # 34-41 blau, 42-49 pink, 50-56 lila, 57 rot
        "ready": row(0, 0), "gray": row(0, 3), "teal": row(2, 0), "gold": row(4, 0), "orange": row(7, 0),
        "amber": row(10, 0), "blue": row(10, 1), "pink": row(12, 1), "purple": row(14, 1), "red": row(16, 3),
    }
    for name, (i, (x, y, w, h), *mid) in boxes.items():
        nine_auto(im[i], x, y, w, h, mid=mid[0] if mid else None).save(os.path.join(out, "box_%s.png" % name))

    # ---------------- Grafiken ----------------
    arts = {
        # Geschenk und Sanduhr in den Zeilen (Rechteck innerhalb der abgerundeten Innenecke); dieselben Bilder stehen
        # im Abholen-Knopf und im Kopf
        "gift_big": (0, (5, 5, 44, 46)), "gift": (0, (5, 86, 27, 106)), "hourglass": (4, (5, 86, 27, 106)),
    }
    pos = {}
    for name, (i, rect) in arts.items():
        img, xy = art(im[i], *rect)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": i}
    # Seiten-Knöpfe 24 x 14 mit Pfeil: aktiv gold, sonst dunkel
    for name, i, x in (("nav_l_off", 0, 209), ("nav_r_on", 0, 360), ("nav_l_on", 16, 209), ("nav_r_off", 16, 360)):
        pill(im[i], x, 59).save(os.path.join(out, "art_%s.png" % name))

    # ---------------- Balken ----------------
    # bereit: voll grün mit Haken (Stufe 1 auf Bild 0)
    y = ROW_Y[0] + BAR_DY
    bg = im[0][y + 20 - BAR_DY, 10]
    Image.fromarray(cut(im[0], BAR_X, y, BAR_W, BAR_H, bg), "RGBA").save(os.path.join(out, "art_bar_ready.png"))
    # leer: Stufe 29 auf Bild 9 (3 px Füllung), Füllung durch die Spur ersetzt, linkes Ende = rechtes gespiegelt
    a = im[9]
    y = ROW_Y[0] + BAR_DY
    bar = a[y:y + BAR_H, BAR_X:BAR_X + BAR_W].copy()
    track = bar[:, 100].copy()
    for x in range(2, 6):
        bar[:, x] = track
    bar[:, 0] = bar[:, BAR_W - 1]
    bar[:, 1] = bar[:, BAR_W - 2]
    bg = a[y - BAR_DY + 20, 10]
    full = np.zeros((BAR_H, BAR_W, 4), dtype=np.uint8)
    full[..., :3] = bar
    full[..., 3] = np.where(np.abs(bar - bg).sum(axis=2) > 6, 255, 0)
    Image.fromarray(full, "RGBA").save(os.path.join(out, "art_bar_empty.png"))
    # gold: Stufe 4 auf Bild 0 (92 %, Füllung bis x 214); der Rest des Verlaufs linear fortgesetzt
    a = im[0]
    y = ROW_Y[3] + BAR_DY + 1
    inner = a[y:y + 8, BAR_X + 1:BAR_X + BAR_W - 1].copy()      # 198 x 8, Spalte 0 = Ecke
    known = 214 - (BAR_X + 1) + 1                               # 184 Spalten Füllung
    seps = {x - (BAR_X + 1) for x in range(BAR_X + 40, BAR_X + BAR_W, 40)}
    xs = [x for x in range(1, known) if x not in seps]
    # Zeilen: 0 Licht, 1-6 Füllung, 7 Schatten; Spalte 0 ist die abgerundete Ecke (Rand, Licht, 4 Füllung, Schatten, Rand)
    for r in range(8):
        fit = [np.polyfit(xs, inner[r, xs, ch], 1) for ch in range(3)]
        for x in range(known, 198):
            inner[r, x] = [int(round(np.polyval(fit[ch], x))) for ch in range(3)]
    border = inner[0, 0].copy()
    light, fill, shadow = inner[0, 197].copy(), inner[1, 197].copy(), inner[7, 197].copy()
    inner[:, 197] = [border, light, fill, fill, fill, fill, shadow, border]
    strip = np.zeros((8, 199, 4), dtype=np.uint8)
    strip[:, :198, :3] = inner
    strip[:, 198, :3] = border                                  # Endspalte hinter der Füllung: dunkel wie der Rand
    strip[..., 3] = 255
    Image.fromarray(strip, "RGBA").save(os.path.join(out, "strip_gold.png"))

    with open(os.path.join(out, "art.json"), "w", encoding="utf-8") as f:
        json.dump(pos, f, indent=1, sort_keys=True)
    print("fertig", len(boxes), "Kästen,", len(arts) + 6, "Grafiken, 1 Verlauf")


if __name__ == "__main__":
    main()
