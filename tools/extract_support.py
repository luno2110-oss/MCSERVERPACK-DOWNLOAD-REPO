"""Schneidet die Bausteine des Support-Dialogs (/support) aus den nativen Referenzbildern (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_support.py <ordner-mit-n_61..n_68.png> tools/assets/support [<4k-neues-ticket.png>]
Die n_XX.png sind Aufnahmen aus dem Release "Neue-referenzen" (wie bei extract_playtime.py). Die 4K-Aufnahme von
„Neues Ticket“ (2026-10-26_05.40.31.png) liefert die Flächen hinter den Beschriftungen der Minecraft-Knöpfe
„Abschicken“ (lila) und „Zurück“ (grau); sie liegen außerhalb der nativen Ausschnitte. Danach tools/build_ui_pack.py.

Bilder: 61 Übersicht, 62 Neues Ticket, 63/64 Bestenliste (Woche, Gesamt), 65 Übersicht mit Tooltip, 66 Link-Abfrage,
67 Meine Tickets (leer), 68 Helfer werden.

Ausgabe (Präfix su_ im Kit):
  box_<name>.png    Köpfe (lila, gold, blau, grün), Karten und Knöpfe der Übersicht, Zeilen, Bestenliste, Schalter
  art_<name>.png    Headset, Schriftzug „SUPPORT“, große Bilder der Karten, kleine Symbole, Teile des Fortschritts
  art_plate_<farbe>.png  Fläche 195 x 16 hinter der Beschriftung eines Minecraft-Knopfs (199 breit), lila und grau
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jewelshop import art, mode_color, nine_auto  # noqa: E402
from extract_playtime import pill  # noqa: E402


def nine_clean(a, x, y, w, h, n=7, mirror_bottom=False):
    """9-Slice wie nine_auto, aber Mittelzeile und -spalte so, dass auch ihr Schnittpunkt Füllfarbe ist und sie
    möglichst überall der häufigsten Farbe ihrer Zeile bzw. Spalte gleichen (in vollen Karten mit Bild, Text und Knopf
    trifft nine_auto sonst ein Schriftpixel, einen Schatten oder den Pfeil am Rand, und der ganze Innenraum oder die
    Kante bekäme einen Punkt)."""
    box = a[y:y + h, x:x + w]
    fill = mode_color(box[n:h - n, n:w - n])
    is_fill = np.abs(box - fill).sum(axis=2) < 6
    rows = list(range(n, h - n)) if h > 2 * n else [n]
    cols = list(range(n, w - n))
    top = h if not mirror_bottom else h - n
    # häufigste Farbe je Zeile (über die inneren Spalten) und je Spalte (über die inneren Zeilen)
    row_mode = np.array([mode_color(box[r:r + 1, n:w - n]) for r in range(h)])
    col_mode = np.array([mode_color(box[n:h - n, c:c + 1]) for c in range(w)])

    edge_rows = [r for r in range(top) if r < n or r >= h - n]
    edge_cols = list(range(n)) + list(range(w - n, w))

    def col_score(c):
        ok = np.abs(box[:top, c] - row_mode[:top]).sum(axis=1) < 6
        return int(ok[edge_rows].sum()), int(ok.sum())

    def row_score(r):
        ok = np.abs(box[r, :] - col_mode).sum(axis=1) < 6
        return int(ok[edge_cols].sum()), int(ok.sum())

    # Vorbild für saubere Ränder: die innersten Spalten bzw. Zeilen direkt neben den Ecken (meist nur Füllung)
    def col_clean(c):
        return any(bool((np.abs(box[edge_rows, c] - box[edge_rows, ref]).sum(axis=1) < 6).all()) for ref in (n, w - n - 1))

    def row_clean(r):
        return any(bool((np.abs(box[r, edge_cols] - box[ref, edge_cols]).sum(axis=1) < 6).all()) for ref in (n, h - n - 1))

    my = max(rows, key=lambda r: (row_clean(r), row_score(r)[0], int(is_fill[r, n:w - n].sum()), row_score(r)[1]))
    mx = max(cols, key=lambda c: (bool(is_fill[my, c]), col_clean(c), col_score(c)))
    img = nine_auto(a, x, y, w, h, n=n, mid=(mx, my), mirror_bottom=mirror_bottom)
    return clean_corners(img, n)


def clean_corners(img, n=7):
    """Runde Ecken: je Zeile von außen her durchsichtig bis zum ersten Pixel in der Umrissfarbe (Mitte der oberen bzw.
    unteren Kante). Gezählt wird an allen vier Ecken, es gilt die kleinste Zahl (die Ecken sind gleich geformt): liegt
    hinter einer Ecke fast schwarzer Hintergrund, hält nine_auto ihn sonst für den Umriss."""
    a = np.array(img)
    size = a.shape[0]
    for j in range(n):
        counts = []
        for bottom in (False, True):
            outline = a[size - 1 if bottom else 0, n, :3].astype(int)
            yy = size - 1 - j if bottom else j
            for right in (False, True):
                k = 0
                while k < n and np.abs(a[yy, size - 1 - k if right else k, :3].astype(int) - outline).sum() > 12:
                    k += 1
                counts.append(k)
        k = min(counts)
        for yy in (j, size - 1 - j):
            a[yy, :k, 3] = 0
            a[yy, size - k:, 3] = 0
            a[yy, k:size - k, 3] = 255
    return Image.fromarray(a, "RGBA")


# 4K-Aufnahme: GUI-Pixel (x, y) des Dialogs (x = 0 am linken Rand des Körpers) liegt bei (1332 + 3x + 1, 201 + 3y + 1)
ORIGIN_4K = (1332, 201)
# Knöpfe unter den Feldern: Beschriftungsfläche Zeilen 179..194; rechter Rand (4 Spalten) und eine Spalte innen
PLATES = {"purple": (188, 20), "gray": (389, 218)}
PLATE_Y, PLATE_H, PLATE_W, EDGE = 179, 16, 195, 4


def plates(path, out):
    """Flächen hinter den Knopf-Beschriftungen. Im Original ist die Fläche 196 breit und wird links vom Knopf um eine
    Spalte beschnitten (Minecraft schneidet Beschriftungen ab, die breiter als der Knopf - 4 sind); hier 195 breit,
    links der gespiegelte rechte Rand, damit nichts beschnitten wird und die Beschriftung nicht hin und her läuft."""
    big = np.array(Image.open(path).convert("RGB")).astype(int)
    ox, oy = ORIGIN_4K

    def gui(x, y):
        return big[oy + 3 * y + 1, ox + 3 * x + 1]

    button = {(0x30, 0x20, 0x09), (0xEA, 0xB8, 0x35), (0xEA, 0xB8, 0x34), (0xE9, 0xB7, 0x33), (0x70, 0x44, 0x07),
              (0xB7, 0x70, 0x0E)}
    for name, (edge_x, inner_x) in PLATES.items():
        img = np.zeros((PLATE_H, PLATE_W, 4), dtype=np.uint8)
        for j in range(PLATE_H):
            y = PLATE_Y + j
            right = [gui(edge_x + k, y) for k in range(EDGE)]
            row = [gui(inner_x, y)] * (PLATE_W - 2 * EDGE)
            for i, c in enumerate(right[::-1] + row + right):
                if tuple(int(v) for v in c) not in button:
                    img[j, i, :3] = c
                    img[j, i, 3] = 255
        Image.fromarray(img, "RGBA").save(os.path.join(out, "art_plate_%s.png" % name))


def load(ref, i):
    return np.array(Image.open(os.path.join(ref, "n_%02d.png" % i)).convert("RGB")).astype(int)


def main():
    ref, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    im = {i: load(ref, i) for i in range(61, 69)}
    boxes = {
        # Übersicht
        "head": (61, (0, 0, 390, 51)),
        "card_purple": (61, (0, 54, 126, 128)), "card_blue": (61, (132, 54, 126, 128)),
        "card_green": (61, (264, 54, 126, 128)),
        "cbtn_purple": (61, (7, 156, 112, 20)), "cbtn_blue": (61, (139, 156, 112, 20)),
        "cbtn_green": (61, (271, 156, 112, 20)),
        "purple": (61, (0, 189, 390, 42)), "gold": (61, (0, 234, 390, 32)), "navy": (61, (0, 270, 390, 32)),
        # Unterseiten: Köpfe 42 hoch
        "head_purple": (62, (0, 0, 390, 42)), "head_gold": (63, (0, 0, 390, 42)), "head_blue": (67, (0, 0, 390, 42)),
        "head_green": (68, (0, 0, 390, 42)),
        # Bestenliste: Schalter Woche/Gesamt, Plätze 1 bis 3 und Rest, Hinweis unten
        "toggle_on": (63, (248, 12, 64, 19)), "toggle_off": (63, (316, 12, 64, 19)),
        "lb_gold": (63, (0, 48, 390, 20)), "lb_silver": (63, (0, 75, 390, 20)), "lb_bronze": (63, (0, 102, 390, 20)),
        "lb_gray": (63, (0, 129, 390, 20)), "footer": (63, (0, 327, 390, 20)),
        # Meine Tickets, Helfer werden
        "blue": (67, (0, 45, 390, 42)), "green": (68, (0, 45, 390, 96)),
        "ok": (68, (316, 87, 64, 19)), "locked": (68, (65, 144, 260, 32)),
    }
    for name, (i, (x, y, w, h)) in boxes.items():
        # in den Karten liegt der Knopf bis an den unteren Rand: Unterkante = Oberkante gespiegelt
        nine_clean(im[i], x, y, w, h, mirror_bottom=name.startswith("card_")).save(os.path.join(out, "box_%s.png" % name))
    arts = {
        "headset": (61, (8, 6, 48, 46)), "title": (61, (48, 6, 200, 44)), "ticket": (61, (290, 8, 312, 40)),
        "question": (61, (4, 58, 122, 115)), "tickets": (61, (136, 58, 254, 115)), "shield": (61, (268, 58, 386, 115)),
        "trophy": (61, (4, 238, 34, 262)), "discord": (61, (4, 274, 34, 298)), "pencil": (62, (360, 6, 386, 36)),
        "hourglass": (68, (4, 62, 24, 84)), "check": (68, (4, 84, 26, 104)), "check_head": (68, (360, 6, 386, 36)),
        "lock": (68, (68, 148, 96, 170)), "ticket_row": (67, (4, 50, 30, 80)),
        "seg_on": (68, (180, 70, 202, 80)), "seg_off": (68, (220, 70, 242, 80)),
    }
    pos = {}
    for name, (i, rect) in arts.items():
        img, xy = art(im[i], *rect)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": i}
    pill(im[63], 8, 9, 22, 22).save(os.path.join(out, "art_back.png"))
    if len(sys.argv) > 3:
        plates(sys.argv[3], out)
    with open(os.path.join(out, "art.json"), "w", encoding="utf-8") as f:
        json.dump(pos, f, indent=1, sort_keys=True)
    print("fertig", len(boxes), "Kästen,", len(arts) + 1, "Grafiken")


if __name__ == "__main__":
    main()
