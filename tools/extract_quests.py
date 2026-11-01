"""Schneidet die Bausteine der Quests (/quest) aus den nativen Referenzbildern (390 px breit, GUI-Pixel).

Aufruf: python3 tools/extract_quests.py <ordner-mit-n_17..n_38.png> tools/assets/quests
Die n_XX.png sind Aufnahmen aus dem Release "Neue-referenzen" (wie bei extract_pwarps.py). Danach
tools/build_ui_pack.py.

Bilder: 17 Übersicht, 18-21 Halloween (Kapitel), 22/23 Täglich, 24-25 Halloween, 26 Wöchentlich, 27-32 Hauptquests,
33-37 Meilensteine, 38 Team.

Ausgabe (Präfix qu_ im Kit):
  box_<name>.png   Kopf, Karten (gold, grau gesperrt, braun, blau, rot), Kacheln der Übersicht je Bereich, kleine
                   Knöpfe (Rangliste, Abholbox, Streak, Hilfe), Reiter (gewählt breit, sonst klein) je Bereich,
                   Knöpfe (gold gefüllt, gold umrandet)
  art_<name>.png   Buch, Symbole der Kacheln, Reiter und Karten, Belohnungen (Münze, Claim-Schaufel, Token, Candy Corn,
                   Titel, Geschenk), Schloss, Fragezeichen, Feuer, Uhr, Zurück
  strip_<name>.png Balken (leer mit Rand, Füllung gold)
  art.json         Lage der Grafiken
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jewelshop import art  # noqa: E402
from extract_playtime import pill  # noqa: E402
from extract_pwarps import art_on, mirrored_template, recolor_template  # noqa: E402
from extract_support import nine_clean  # noqa: E402

# Bereiche: Farbe der Kachel/Reiter
AREAS = ("event", "daily", "weekly", "main", "mile", "team")


def load(ref, i):
    return np.array(Image.open(os.path.join(ref, "n_%02d.png" % i)).convert("RGB")).astype(int)


def strip(column, end, path):
    """Füllung: 256 gleiche Spalten, rechts die Endspalte (None = durchsichtig)."""
    h = len(column)
    out = np.zeros((h, 257, 4), dtype=np.uint8)
    for x in range(256):
        for y, c in enumerate(column):
            if c is not None:
                out[y, x, :3] = c
                out[y, x, 3] = 255
    for y, c in enumerate(end):
        if c is not None:
            out[y, 256, :3] = c
            out[y, 256, 3] = 255
    Image.fromarray(out, "RGBA").save(path)


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def main():
    ref, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    im = {i: load(ref, i) for i in list(range(17, 39)) + list(range(79, 91))}

    # ---------------- Kästen ----------------
    boxes = {
        "head": (17, (0, 0, 390, 48)),
        "card_gold": (17, (0, 54, 390, 105)),
        "card_gray": (18, (0, 207, 390, 96)),
        "card_brown": (22, (0, 81, 390, 69)),
        "card_blue": (23, (0, 81, 390, 87)),
        "card_red": (23, (0, 180, 390, 87)),
        "tile_event": (17, (0, 171, 126, 67)), "tile_daily": (17, (132, 171, 126, 67)),
        "tile_weekly": (17, (264, 171, 126, 67)), "tile_main": (17, (0, 243, 126, 67)),
        "tile_mile": (17, (132, 243, 126, 67)), "tile_team": (17, (264, 243, 126, 67)),
        "tile_side": (17, (0, 315, 126, 67)),
        "small_rank": (17, (132, 324, 126, 22)), "small_box": (17, (264, 324, 126, 22)),
        "small_streak": (17, (132, 351, 126, 22)), "small_help": (17, (264, 351, 126, 22)),
        "tab_event": (22, (0, 54, 34, 22)), "tab_daily": (18, (204, 54, 34, 22)), "tab_weekly": (18, (242, 54, 34, 22)),
        "tab_main": (18, (280, 54, 34, 22)), "tab_mile": (18, (318, 54, 34, 22)), "tab_team": (18, (356, 54, 34, 22)),
        "btn_line": (22, (284, 100, 96, 18)),
        "npc": (79, (0, 54, 126, 72)), "card_tan": (80, (0, 54, 390, 114)), "card_orange": (83, (0, 54, 390, 96)),
        "card_green": (84, (0, 81, 390, 94)), "help_tab_off": (84, (98, 54, 95, 22)),
        "rank_tab_off": (89, (198, 54, 192, 22)), "pod_silver": (89, (0, 90, 126, 70)),
        "pod_gold": (89, (132, 81, 126, 79)), "pod_bronze": (89, (264, 90, 126, 70)), "rank_row": (89, (8, 177, 184, 16)),
    }
    for name, (i, (x, y, w, h)) in boxes.items():
        if name == "tab_event":
            continue
        nine_clean(im[i], x, y, w, h).save(os.path.join(out, "box_%s.png" % name))
    # Event-Reiter: Kürbis und „?“ füllen ihn fast ganz; Form des Tages-Reiters in seinen Farben (Rahmen-Zeilen und
    # -Spalten Pixel für Pixel verglichen)
    src = im[18][54:76, 204:238]
    dst = im[22][54:76, 0:34]
    table = {}
    for yy in range(22):
        for xx in range(34):
            if yy in (0, 1, 2, 19, 20, 21) or xx in (0, 1, 2, 31, 32, 33):
                table.setdefault(tuple(src[yy, xx]), []).append(tuple(dst[yy, xx]))
    for yy in range(4, 18):
        table.setdefault(tuple(src[yy, 30]), []).append(tuple(dst[yy, 30]))
    mapping = {c: max(set(v), key=v.count) for c, v in table.items()}
    recolor_template(Image.open(os.path.join(out, "box_tab_daily.png")).convert("RGBA"), mapping).save(
        os.path.join(out, "box_tab_event.png"))
    # gewählte Reiter und gefüllte Knöpfe: rechts steht Text (Zahl); die linke Seite ist sauber -> gespiegelt
    pills = {"pill_event": (18, (0, 54, 200, 22)), "pill_daily": (22, (38, 54, 200, 22)),
             "pill_weekly": (26, (76, 54, 200, 22)), "pill_main": (27, (114, 54, 200, 22)),
             "pill_mile": (33, (152, 54, 200, 22)), "pill_team": (38, (190, 54, 200, 22)),
             "btn_gold": (17, (284, 91, 96, 18)), "btn_wide": (80, (0, 252, 390, 20)),
             "help_tab_on": (84, (0, 54, 95, 22)), "rank_tab_on": (89, (0, 54, 192, 22))}
    for name, (i, (x, y, w, h)) in pills.items():
        mirrored_template(im[i], x, y, w, h).save(os.path.join(out, "box_%s.png" % name))

    # ---------------- Balken ----------------
    edge, empty = hexc("0C0B09"), hexc("2A251E")
    strip([edge] + [empty] * 4 + [edge], [edge] * 6, os.path.join(out, "strip_bar.png"))
    gold, gold_dark = hexc("FCB037"), hexc("C68A2B")
    # so hoch wie der leere Balken (Rand-Zeilen durchsichtig), damit beide dieselben Schrift-Höhen nutzen
    strip([None] + [gold] * 3 + [gold_dark] + [None], [None] * 6, os.path.join(out, "strip_fill_gold.png"))
    # Meilensteine: helle Füllung
    strip([None] + [hexc("FCCF79")] * 3 + [hexc("C6A35F")] + [None], [None] * 6, os.path.join(out, "strip_fill_cream.png"))

    # ---------------- Grafiken ----------------
    arts = {
        "logo": (17, (3, 3, 45, 45)), "title": (17, (44, 8, 144, 40)), "fire": (17, (325, 4, 344, 24)), "medal": (17, (306, 25, 328, 44)),
        "compass": (17, (6, 72, 24, 92)), "coin": (17, (150, 72, 172, 92)), "shovel": (17, (200, 72, 220, 92)),
        "medal_s": (17, (246, 72, 262, 92)),
        "t_event": (17, (3, 175, 44, 218)), "t_daily": (17, (135, 175, 176, 218)),
        "t_weekly": (17, (267, 175, 308, 218)), "t_main": (17, (3, 247, 44, 290)),
        "t_mile": (17, (135, 247, 176, 290)), "t_team": (17, (267, 247, 308, 290)),
        "t_side": (17, (3, 319, 44, 362)),
        "s_rank": (17, (156, 326, 182, 344)), "s_box": (17, (288, 326, 314, 344)),
        "s_streak": (17, (162, 353, 186, 371)), "s_help": (17, (302, 353, 324, 371)),
        "q_mark": (18, (6, 86, 24, 108)), "candy": (18, (88, 154, 106, 172)), "lock": (18, (6, 212, 24, 232)),
        "hourglass": (22, (6, 98, 24, 120)),
        "feather": (34, (166, 184, 187, 201)), "gift": (22, (352, 310, 371, 332)), "qbig": (22, (8, 311, 24, 333)),
        "lock_s": (18, (292, 244, 310, 264)), "set_clock": (22, (334, 292, 352, 311)),
    }
    on_box = {
        "logo": (0, 0, 390, 48), "title": (0, 0, 390, 48), "fire": (0, 0, 390, 48), "medal": (0, 0, 390, 48),
        "t_event": (0, 171, 126, 67), "t_daily": (132, 171, 126, 67), "t_weekly": (264, 171, 126, 67),
        "t_main": (0, 243, 126, 67), "t_mile": (132, 243, 126, 67), "t_team": (264, 243, 126, 67),
        "t_side": (0, 315, 126, 67),
        "s_rank": (132, 324, 126, 22), "s_box": (264, 324, 126, 22), "s_streak": (132, 351, 126, 22),
        "s_help": (264, 351, 126, 22),
    }
    # Symbole in den Reitern (ohne den Rahmen) und in den gewählten Reitern
    tabs = {"tab_i_event": (22, (3, 57, 31, 73), (0, 54, 34, 22)),
            "tab_i_daily": (18, (207, 57, 235, 73), (204, 54, 34, 22)),
            "tab_i_weekly": (18, (245, 57, 273, 73), (242, 54, 34, 22)),
            "tab_i_main": (18, (283, 57, 311, 73), (280, 54, 34, 22)),
            "tab_i_mile": (18, (321, 57, 349, 73), (318, 54, 34, 22)),
            "tab_i_team": (18, (359, 57, 387, 73), (356, 54, 34, 22)),
            "pill_i_event": (18, (3, 56, 26, 74), (0, 54, 200, 22)),
            "pill_i_daily": (22, (41, 56, 64, 74), (38, 54, 200, 22)),
            "pill_i_weekly": (26, (79, 56, 102, 74), (76, 54, 200, 22)),
            "pill_i_main": (27, (117, 56, 140, 74), (114, 54, 200, 22)),
            "pill_i_mile": (33, (155, 56, 178, 74), (152, 54, 200, 22)),
            "pill_i_team": (38, (193, 56, 216, 74), (190, 54, 200, 22))}
    pos = {}
    for name, (i, rect) in arts.items():
        if name in on_box:
            img, xy = art_on(im[i], *rect, on_box[name])
        else:
            img, xy = art(im[i], *rect)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": i}
    for name, (i, rect, box) in tabs.items():
        # kleine Reiter sind innen einfarbig (das „?“ beim Event hat fast die Randfarbe), gewählte haben einen Verlauf
        img, xy = art(im[i], *rect) if name.startswith("tab_") else art_on(im[i], *rect, box)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": i}
    # Blättern: Pfeil-Kästen (rechts auf Seite 1 von Täglich, links auf Seite 2)
    pill(im[22], 231, 261, 24, 14).save(os.path.join(out, "art_page_next.png"))
    pos["page_next"] = {"x": 231, "y": 261, "w": 24, "h": 14, "bild": 22}
    pill(im[23], 135, 279, 24, 14).save(os.path.join(out, "art_page_prev.png"))
    pos["page_prev"] = {"x": 135, "y": 279, "w": 24, "h": 14, "bild": 23}
    # Gesichter der Nebenquest-Figuren (24 x 24, 3-fach vergrößerte Skins; Mortis und Ralf: Schriftrolle)
    for k, name in enumerate(("leonie", "frieda", "mortis", "lilia", "konrad", "hermann", "rudolf", "mia", "ralf")):
        x, y = (k % 3) * 132 + 7, 54 + (k // 3) * 81 + 7
        face = np.zeros((24, 24, 4), dtype=np.uint8)
        face[..., :3] = im[79][y:y + 24, x:x + 24]
        face[..., 3] = 255
        if name in ("mortis", "ralf"):
            img, xy = art(im[79], x - 1, y - 1, x + 25, y + 25)
            img.save(os.path.join(out, "art_face_%s.png" % name))
            pos["face_" + name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": 79}
            continue
        Image.fromarray(face, "RGBA").save(os.path.join(out, "art_face_%s.png" % name))
        pos["face_" + name] = {"x": x, "y": y, "w": 24, "h": 24, "bild": 79}
    # Stern (Schwierigkeit) und Kompass im breiten Knopf der Detailseite
    for name, (i, rect, box) in {"streak_head": (83, (34, 3, 74, 45), (0, 0, 390, 48)),
                                 "box_head": (88, (34, 3, 74, 45), (0, 0, 390, 48)),
                                 "help_head": (84, (34, 3, 74, 45), (0, 0, 390, 48)),
                                 "rank_head": (89, (34, 3, 74, 45), (0, 0, 390, 48)),
                                 "tab_clock": (89, (60, 56, 86, 74), (0, 54, 192, 22)),
                                 "tab_trophy": (89, (262, 56, 284, 74), (198, 54, 192, 22)),
                                 "m_silver": (89, (48, 96, 80, 128), (0, 90, 126, 70)),
                                 "m_gold": (89, (180, 88, 212, 122), (132, 81, 126, 79)),
                                 "m_bronze": (89, (312, 96, 344, 128), (264, 90, 126, 70)),
                                 "crown": (89, (228, 84, 254, 106), (132, 81, 126, 79)),
                                 "eye": (85, (4, 318, 40, 360), (0, 270, 390, 103)),
                                 "shield": (83, (278, 22, 300, 42), (0, 0, 390, 48)),
                                 "fire_s": (83, (6, 62, 26, 80), (0, 54, 390, 96)),
                                 "node_on": (83, (40, 80, 68, 112), (0, 54, 390, 96)),
                                 "node_off": (83, (133, 80, 161, 112), (0, 54, 390, 96)),
                                 "bulb": (83, (6, 166, 26, 190), (0, 162, 390, 78)),
                                 "chest_s": (88, (6, 58, 26, 80), (0, 54, 390, 51)),
                                 "star": (80, (318, 58, 340, 78), (0, 54, 390, 114)),
                                 "btn_icon": (80, (116, 253, 136, 271), (0, 252, 390, 20))}.items():
        img, xy = art_on(im[i], *rect, box)
        img.save(os.path.join(out, "art_%s.png" % name))
        pos[name] = {"x": xy[0], "y": xy[1], "w": img.width, "h": img.height, "bild": i}
    # Zurück-Knopf im Kopf
    pill(im[18], 8, 13, 22, 22).save(os.path.join(out, "art_back.png"))
    pos["back"] = {"x": 8, "y": 13, "w": 22, "h": 22, "bild": 18}
    with open(os.path.join(out, "art.json"), "w", encoding="utf-8") as f:
        json.dump(pos, f, indent=1, sort_keys=True)
    print("fertig", len(boxes) + len(pills), "Kästen,", len(arts) + len(tabs) + 1, "Grafiken")


if __name__ == "__main__":
    main()
