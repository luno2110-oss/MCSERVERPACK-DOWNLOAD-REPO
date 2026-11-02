#!/usr/bin/env python3
"""Material-Rampen für neue Icons (dunkel -> hell), zusammengesetzt aus Rampen der Referenz-Icons.

Jede Rampe stammt aus Referenz-Icons der Datenbank (icons.json, getrennt nach Material); Quelle steht dabei. Die
dunkelste Stufe ist nie schwarz (das macht die Kontur), die hellste nie reines Weiß (Glanz liegt knapp darunter).

    import materials; materials.RAMPS["gold"]
    python3 tools/icon_db/materials.py     -> materials.png (Übersicht)
"""
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))

# Kontur: warmes Fast-Schwarz wie Münze/Krone der Quests-Referenz (#050301, #060401)
OUTLINE = "#060401"

RAMPS = {
    # Gold: Münze, Truhe und Geschenkband (quests/art_coin, jewelshop/art_chest, quests/art_gift)
    "gold": ["#7E3000", "#A14900", "#C86600", "#E08001", "#F49D02", "#F7B804", "#F8DE06", "#FAF44A", "#FAF9B6"],
    # Silber: Silbermedaille (quests/art_m_silver), kühles Blaugrau
    "silber": ["#485368", "#5A667B", "#717C91", "#8792A7", "#AAB3C4", "#C4CBD8", "#DBDFE8", "#F2F3F5"],
    # Eisen: Schloss (quests/art_lock), neutrales Grau
    "eisen": ["#2A2D30", "#3D434C", "#5D646D", "#737981", "#8D9399", "#A4A9AF", "#C6CBD0", "#DDDEE0", "#F8F8F8"],
    # Rot: Kiste (jewelshop/art_crate), Lichter Richtung Orange
    "rot": ["#5E0210", "#890416", "#A50617", "#BB0C15", "#D20B19", "#E71217", "#F72C18", "#FA471D", "#FB7A52"],
    # Holz: Truhe und Kiste (jewelshop/art_chest, jewelshop/art_crate)
    "holz": ["#3A1606", "#54210A", "#74300A", "#813801", "#974000", "#AA4E00", "#C05F01", "#DA7901", "#E98601"],
    # Smaragd: großer Smaragd und Truhe (jewelshop/art_emerald_big, jewelshop/art_chest)
    "smaragd": ["#014714", "#015623", "#006B2A", "#008029", "#019427", "#01B427", "#02CD27", "#04E228", "#21F250", "#B0FAA8"],
    # Gras/Blatt: Farm-Übersicht (jewelshop_survival/art_ov_farm)
    "gras": ["#05521A", "#037521", "#017C0F", "#0B9409", "#29C304", "#55D605", "#6CDE03", "#8CEC07", "#B5F40D"],
    # Erde/Stein warm: Felsen (jewelshop_survival/f_felsen_2)
    "erde": ["#2A1C10", "#362616", "#48311B", "#52422A", "#685536", "#7B633D", "#917C52"],
    # Lila: Kisten-Übersicht (jewelshop_survival/art_ov_crate)
    "lila": ["#4D0098", "#5B00AF", "#6F01CA", "#7302E4", "#8901DE", "#9C02EE", "#B807F5", "#D51FFA", "#F550FA", "#FA71FB"],
    # Blau: Tickets und Portal (support/art_tickets, pwarps/art_portal)
    "blau": ["#00387D", "#004391", "#02569A", "#0376F0", "#0796EE", "#269BF8", "#43ADF9", "#5FBDF9", "#8AD4FA", "#B7E9FA"],
    # Türkis: Portal (pwarps/art_portal)
    "tuerkis": ["#0F3F43", "#17515F", "#02676F", "#019491", "#00A5A3", "#01C0BA", "#02D5C9", "#05EDDD", "#29F7E9", "#94F2F3"],
    # Feuer/Orange: Flamme (quests/art_fire)
    "feuer": ["#8C0501", "#C00400", "#E20301", "#F72201", "#FA4C01", "#FA7201", "#FA9801", "#FBB002", "#FBD104", "#FBEB19"],
    # Papier/Pergament: Schriftrolle (battlepass/art_tab_scroll)
    "papier": ["#4A2207", "#592C09", "#9B5C26", "#B7783A", "#D59853", "#E2A85E", "#EEB970", "#F8D08D", "#FBDFA5", "#FBEDC4"],
    # Rosa: großes Geschenk (playtime/art_gift_big)
    "rosa": ["#6E1838", "#A72656", "#B92664", "#CE316E", "#E03C7D", "#F86298", "#F985B1", "#FBB3CF"],
    # Glas/Wasser hell: Sanduhr (quests/art_hourglass)
    "glas": ["#1A4A66", "#287498", "#4097B9", "#6FCAE9", "#A8E4F6", "#D8F4FB"],
    # Stiel wie der Vanilla-Stock: Angel der Info-Leiste (infobar/art_hud_rod), Licht aus jewelshop_survival/f_tisch_gruen
    "stiel": ["#281E0B", "#483515", "#674D1E", "#876627", "#9C7C40"],
    # Kessel/dunkles Metall, blau-violett: Profi-Paket (jewelshop/art_pack_profi)
    "kessel": ["#25293E", "#393D55", "#4D516A", "#6E7188", "#80849C"],
    # Steak, rotbraun: Rang-Kopf und Bronze-Medaille der Quests (quests/art_rank_head, quests/art_m_bronze),
    # Licht aus jewelshop_survival/f_kleiderstange_2, dunkelste Stufe wie das Vanilla-Steak
    "steak": ["#2A160D", "#58240E", "#682511", "#833316", "#9B3F1A", "#BC4A35"],
    # Minecraft-Gegenstände in Vanilla-Farben (Wunsch: Minecraft-Look): Enderkiste, rotes Bett, Eichenbretter
    "ender": ["#020E14", "#05151C", "#10232D", "#192328", "#233032", "#34494B", "#4B645C", "#597163"],
    "bett": ["#6B1213", "#851A1A", "#902120", "#A22722", "#B53129", "#C9473A"],
    "planke": ["#695433", "#9B7742", "#9F844D", "#AF8F55", "#B8945F", "#C29D62"],
    # Zombie (Vanilla): Haut dunkel -> hell, Augen fast schwarz an Index 0; Hemd türkis, Hose blau-violett
    "zombie": ["#1A1A1A", "#385226", "#3E692D", "#4E7B36", "#698756", "#799C65"],
    "hemd": ["#007A7A", "#009E9E", "#00AFAF", "#00CCCC"],
    "hose": ["#2A2366", "#3A3189", "#463AA5", "#5A4EC0"],
}


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def sheet(out):
    z = 22
    w = 140 + 11 * z
    img = Image.new("RGB", (w, len(RAMPS) * (z + 6) + 10), (34, 38, 48))
    d = ImageDraw.Draw(img)
    for i, (name, hx) in enumerate(RAMPS.items()):
        y = 6 + i * (z + 6)
        d.text((6, y + 5), name, fill=(220, 220, 230))
        for k, h in enumerate(hx):
            d.rectangle((130 + k * z, y, 130 + k * z + z - 2, y + z - 2), fill=rgb(h))
    img.save(out)


if __name__ == "__main__":
    sheet(os.path.join(HERE, "materials.png"))
    print("ok", len(RAMPS), "Materialien")
