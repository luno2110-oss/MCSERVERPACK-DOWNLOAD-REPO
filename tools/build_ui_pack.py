"""Erzeugt das Resource Pack "KubyUI": Dialog-Kit (kuby:k/...), Titelschrift, Casino- und Angel-Grafik, Scoreboard.

Aufruf: KUBY_VANILLA=<vanilla-assets> python3 tools/build_ui_pack.py <ausgabe-ordner>
Die Schrift kuby:ui enthält nur noch die Abstands-Zeichen (de.kuby.kubyplugins.ui.Ui.space).
"""
import json
import os
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "resourcepack/KubyUI"
FONT = os.path.join(OUT, "assets/kuby/font")
os.makedirs(FONT, exist_ok=True)

T = (0, 0, 0, 0)

# ---------- Schrift kuby:ui: Abstände (U+F801.. nach links, U+F811.. nach rechts, je 2^i Pixel) ----------
neg = {chr(0xF801 + i): -(1 << i) for i in range(10)}
pos = {chr(0xF811 + i): (1 << i) for i in range(10)}
with open(os.path.join(FONT, "ui.json"), "w", encoding="utf-8") as f:
    json.dump({"providers": [{"type": "space", "advances": {**neg, **pos}}]}, f, ensure_ascii=True, indent=1)

with open(os.path.join(OUT, "pack.mcmeta"), "w", encoding="utf-8") as f:
    json.dump({"pack": {
        "description": "KubyUI: Menüs, Casino, Angeln und Scoreboard",
        "pack_format": 64,
        "supported_formats": {"min_inclusive": 46, "max_inclusive": 1000},
        "min_format": 46,
        "max_format": 1000,
    }}, f, indent=2)
print("ok", OUT)


# Scoreboard-Grafik (Schrift kubyplugins:sb) in doppelter Aufloesung, siehe build_sb_pack.py.
import build_sb_pack  # noqa: E402

build_sb_pack.build(OUT, {**neg, **pos})

# Dialog-Kit (Schriften kuby:k/...), siehe build_kit.py. Braucht die Vanilla-Schriften (Release "vanilla-assets")
# im Ordner KUBY_VANILLA; ohne den bleibt das vorhandene Kit unverändert.
import build_kit  # noqa: E402

import build_fish  # noqa: E402
import build_angel_icons  # noqa: E402
import build_casino  # noqa: E402
import build_help_icons  # noqa: E402
import build_hats  # noqa: E402
import build_settings  # noqa: E402
import build_skins  # noqa: E402
import build_sym  # noqa: E402

build_sym.build(OUT)
# Truhen-Fenster: eigene Hintergründe je Farbwelt und Reihenzahl (Schrift kuby:chest)
import build_chest_bg  # noqa: E402
build_chest_bg.build(OUT)
# Event-Items (Candy Corn)
# Event-Presets: Kopf-Bilder, Reiter-Symbole, Währungen und Sammel-Items zeichnen (tools/assets/events), dann die Items
import build_event_art  # noqa: E402
build_event_art.build()
import build_generator_art  # noqa: E402
build_generator_art.build()
import build_cookie_art  # noqa: E402
build_cookie_art.build()
import build_event_items  # noqa: E402
build_event_items.build(OUT)
# Generatoren: 3D-Spawner-Käfige je Art und Qualität, dazu die Symbole, die sich im Spawner-Block drehen
build_generator_art.write_models(OUT)
# Cookie Clicker: großer Keks, Glückskeks und Kekse aus dem Keksregen
build_cookie_art.write_models(OUT)
# Kino-Engine und Heiliges Schwert: Schwert, Stein, Effekte, Kino-Schrift (Sounds: tools/build_cine_sounds.py, braucht ffmpeg)
import build_cinema  # noqa: E402
build_cinema.OUT = OUT
build_cinema.A = os.path.join(OUT, "assets/kuby")
build_cinema.main()

VANILLA = os.environ.get("KUBY_VANILLA")
if VANILLA and os.path.isdir(VANILLA):
    build_fish.build(OUT)
    own = [("fish_" + f[0], build_fish.render(f)) for f in build_fish.FISH]
    own += [("fishx_" + f[0], build_fish.render(f, silhouette=True)) for f in build_fish.FISH]
    own += build_angel_icons.build(OUT)
    casino16, casino_small, casino_big = build_casino.build(OUT)
    build_skins.build(OUT)
    own += casino16
    own += build_hats.particle_icons(VANILLA)
    # Icons der Survival-Hilfe in Originalgröße (18 px) hinter die Casino-Icons
    small = casino_small + build_help_icons.build(VANILLA, build_casino.chip("red"))
    # Einstellungen-Menü (Referenzen2): Icons und AN/AUS-Schalter hinter den Hilfe-Icons
    small += build_settings.small_icons(VANILLA)
    # Hüte (Modelle kuby:hat/<id>) und ihre Icons (32 px) hinter den Casino-Icons
    hat_big = build_hats.build(OUT)
    # Discord-Statue: Wumpus als Item-Modell (kuby:deco/wumpus)
    import build_wumpus  # noqa: E402
    build_wumpus.write(OUT)
    # Survival-Juwelenshop: Möbel als Modelle (kuby:furniture/<id>, mit Katalog moebel.yml) und Pets (kuby:pet/<id>)
    import build_furniture  # noqa: E402
    import build_pets  # noqa: E402
    furniture = build_furniture.all_models()  # meldet dabei auch die eigenen Texturen an
    build_furniture.write_textures(OUT)
    build_furniture.write(furniture, OUT)
    build_furniture.catalog(furniture)
    build_pets.write(OUT)
    build_pets.catalog()
    # Juwelenshop (SMP), Battle-Pass und Guide: große Grafiken als Sprites, goldener Schlüssel als eigenes Icon
    import build_jewelshop  # noqa: E402
    # Hauptmenü: eigene Icons neu zeichnen (tools/assets/hub, als Sprites hb_…)
    import build_hub_icons  # noqa: E402
    build_hub_icons.build()
    import build_battlepass  # noqa: E402
    import build_guide  # noqa: E402
    own += build_battlepass.icons()
    # Menü-Dialoge (12.10): Blöcke als kleine Würfel (Gras, Truhe, Bett ...) und Banner
    import build_menu_icons  # noqa: E402
    own += build_menu_icons.icons(VANILLA)
    build_kit.build(OUT, {**neg, **pos}, VANILLA, own, small, casino_big + hat_big + build_settings.big_icons(),
                    build_jewelshop.sprites() + build_battlepass.sprites() + build_guide.sprites(),
                    build_jewelshop.strips())
    # Icons für Hologramm-Zeilen (kuby:holo): dieselben Zeichen wie kuby:k/i0, aber nur 9 px hoch wie Text
    with open(os.path.join(OUT, "assets/kuby/font/k/i0.json"), encoding="utf-8") as fh:
        i0 = json.load(fh)
    holo = []
    for prov in i0["providers"]:
        if prov.get("type") != "bitmap" or prov.get("height") not in (16, 20):
            continue
        if "title" in prov.get("file", ""):
            continue
        q = dict(prov)
        q["height"] = 9
        q["ascent"] = 8
        holo.append(q)
    with open(os.path.join(OUT, "assets/kuby/font/holo.json"), "w", encoding="utf-8") as fh:
        json.dump({"providers": holo}, fh, ensure_ascii=False)
    # Item-Bilder für alle Vanilla-Items (kuby:k/x…) und Kategorie-Titel im Referenz-Stil (kuby:k/h…): liegen in
    # denselben Ordnern, die build_kit leert, also hier jedes Mal neu bauen
    import build_item_icons  # noqa: E402
    import build_headline  # noqa: E402
    build_item_icons.PACK = build_headline.PACK = OUT
    build_item_icons.VAN = VANILLA
    build_item_icons.main()
    build_headline.main()
else:
    print("Hinweis: KUBY_VANILLA nicht gesetzt, Dialog-Kit nicht neu gebaut")

# Das Warn-Zeichen ("!") neben dem Titel jedes Server-Dialogs wird zum Kuby-Logo (wie das C beim Chaos-Server).
# Der Knopf bleibt anklickbar, Esc schließt jedes Menü.
import build_logo  # noqa: E402

WARN = os.path.join(OUT, "assets/minecraft/textures/gui/sprites/dialog")
os.makedirs(WARN, exist_ok=True)
for name, state in (("warning_button", "normal"), ("warning_button_disabled", "disabled"),
                    ("warning_button_highlighted", "highlighted")):
    build_logo.logo(state).save(os.path.join(WARN, name + ".png"))


# Minecraft-Knöpfe golden wie in den Referenzen (auch unter den Eingabefeldern der Dialoge und in der Link-Abfrage)
import build_buttons  # noqa: E402

build_buttons.write(OUT)


# ---------- Prüfung: Fehler in einer Schrift-Datei lassen Minecraft die ganze Schrift verwerfen ----------
import check_fonts  # noqa: E402

errors = check_fonts.check_fonts(OUT)
for e in errors:
    print("FEHLER " + e)
if errors:
    sys.exit(1)
print("Schriften geprüft: ok")
