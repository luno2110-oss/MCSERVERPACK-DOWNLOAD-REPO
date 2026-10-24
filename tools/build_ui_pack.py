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
import build_skins  # noqa: E402
import build_sym  # noqa: E402

build_sym.build(OUT)

VANILLA = os.environ.get("KUBY_VANILLA")
if VANILLA and os.path.isdir(VANILLA):
    build_fish.build(OUT)
    own = [("fish_" + f[0], build_fish.render(f)) for f in build_fish.FISH]
    own += [("fishx_" + f[0], build_fish.render(f, silhouette=True)) for f in build_fish.FISH]
    own += build_angel_icons.build(OUT)
    casino16, casino_small, casino_big = build_casino.build(OUT)
    build_skins.build(OUT)
    own += casino16
    # Icons der Survival-Hilfe in Originalgröße (18 px) hinter die Casino-Icons
    small = casino_small + build_help_icons.build(VANILLA, build_casino.chip("red"))
    build_kit.build(OUT, {**neg, **pos}, VANILLA, own, small, casino_big)
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
else:
    print("Hinweis: KUBY_VANILLA nicht gesetzt, Dialog-Kit nicht neu gebaut")

# Das Warn-Zeichen ("!") oben in Server-Dialogen ausblenden, die Menüs zeichnen ihren eigenen Kopf.
# Der Knopf bleibt anklickbar, Esc schließt jedes Menü.
WARN = os.path.join(OUT, "assets/minecraft/textures/gui/sprites/dialog")
os.makedirs(WARN, exist_ok=True)
for name in ("warning_button", "warning_button_disabled", "warning_button_highlighted"):
    Image.new("RGBA", (20, 20), T).save(os.path.join(WARN, name + ".png"))


# ---------- Prüfung: Fehler in einer Schrift-Datei lassen Minecraft die ganze Schrift verwerfen ----------
import check_fonts  # noqa: E402

errors = check_fonts.check_fonts(OUT)
for e in errors:
    print("FEHLER " + e)
if errors:
    sys.exit(1)
print("Schriften geprüft: ok")
