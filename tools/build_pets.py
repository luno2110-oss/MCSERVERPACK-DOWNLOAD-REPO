#!/usr/bin/env python3
"""Pets aus dem Survival-Juwelenshop: Item-Modelle aus den Shop-Bildern und der Katalog fürs Plugin.

Jedes Pet ist ein Item-Modell (minecraft:item/generated, also ein Bild mit 1 px Tiefe) aus seinem Bild im Shop
(tools/assets/jewelshop_survival/p_<id>.png, 30 x 30, auf 32 x 32 erweitert). Im Spiel zeigt ein ItemDisplay das Bild,
immer zum Betrachter gedreht, auf einem unsichtbaren Träger (Katze, Flieger: Allay), der dem Besitzer folgt.

Schreibt:
  resourcepack/KubyUI/assets/kuby/textures/item/pet/<id>.png
  resourcepack/KubyUI/assets/kuby/models/item/pet/<id>.json
  resourcepack/KubyUI/assets/kuby/items/pet/<id>.json
  KubySurvival/src/main/resources/pets-shop.yml   (Name, Seltenheit, Preis in Juwelen, Träger, Größe)
Aufruf: python3 tools/build_pets.py [<pack>]
"""
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import jewelshop_survival_data as data  # noqa: E402

ICONS = os.path.join(HERE, "assets/jewelshop_survival")
CATALOG = os.path.join(REPO, "KubySurvival-v1.0.0/KubySurvival/src/main/resources/pets-shop.yml")

# Flieger: Drachen, Vögel, Funken und schwebende Wesen folgen auf einem Allay
FLYING = {"laubdrache", "steindrache", "winddrache", "anzuki", "baby_phoenix", "magmadrache", "seelenfunke",
          "wasserdrache", "baby_wither", "endfunke", "nachtdrache", "sonnendrache", "rabenkoenig", "perlchen",
          "portalchen", "endportalchen"}
# Größe (Blöcke) nach Seltenheit
SIZE = {"common": 0.75, "rare": 0.85, "epic": 0.95, "legendary": 1.05, "mythic": 1.2}


def write(pack):
    tdir = os.path.join(pack, "assets/kuby/textures/item/pet")
    mdir = os.path.join(pack, "assets/kuby/models/item/pet")
    idir = os.path.join(pack, "assets/kuby/items/pet")
    for d in (tdir, mdir, idir):
        os.makedirs(d, exist_ok=True)
    for pid, _name, _rar, _price in data.PETS:
        src = Image.open(os.path.join(ICONS, "p_%s.png" % pid)).convert("RGBA")
        img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        img.paste(src, (1, 2))
        img.save(os.path.join(tdir, pid + ".png"))
        with open(os.path.join(mdir, pid + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/pet/" + pid}}, f)
        with open(os.path.join(idir, pid + ".json"), "w", encoding="utf-8") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/pet/" + pid}}, f)


def catalog():
    lines = ["# Pets aus dem Survival-Juwelenshop. Generiert von tools/build_pets.py, nicht von Hand ändern.",
             "# Wie pets.yml, dazu seltenheit, juwelen (Preis im Shop), modell (Bild aus dem Pack), groesse (Blöcke).",
             "# tier = unsichtbarer Träger, der dem Besitzer folgt (Katze; Flieger: Allay).",
             "pets:"]
    for pid, name, rar, price in data.PETS:
        tier = "ALLAY" if pid in FLYING else "CAT"
        lines.append('  %s: {name: "%s", tier: %s, baby: %s, reitbar: false, preis: 0, quelle: "Juwelenshop", '
                     'seltenheit: %s, juwelen: %d, modell: "kuby:pet/%s", groesse: %s}'
                     % (pid, name, tier, "false" if tier == "ALLAY" else "true", rar, price, pid, SIZE[rar]))
    with open(CATALOG, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    pack = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "resourcepack/KubyUI")
    write(pack)
    catalog()
    print("ok pets:", len(data.PETS))
