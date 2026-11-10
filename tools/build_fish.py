"""Fisch-Grafiken für das Angeln (72 Arten, 12 je Gewässer) im Stil der Icon-Datenbank: 16 x 16, Seitenansicht
(Kopf links), handgezeichnete Körperformen je Tierart (tools/fish_shapes.py), 1 px Kontur, Farbrampen mit Licht oben
links (tools/fishart.py) und Muster der echten Tiere. Erzeugt Item-Texturen, Item-Modelle und Icons für das Dialog-Kit
(auch dunkle Umrisse für noch nicht gefangene Arten).

Die Liste FISH erzeugt de.kuby.kubysurvival.angeln.FishSpecies (gleiche IDs, gleiche Reihenfolge).
Aufruf über build_ui_pack.py (oder: python3 tools/build_fish.py <pack-ordner>).
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fish_shapes  # noqa: E402
from fishart import dark, from_grid, light, mix, rgb  # noqa: E402

T = (0, 0, 0, 0)

# =====================================================================================================================
# Arten: id, Name, Gewässer, Seltenheit, kg von-bis, Form, Farben (Rücken, Körper, Bauch, Flossen), Muster, Extras
# Gewässer: fluss, ozean, warm, kalt, sumpf, mythos  ·  Seltenheit: g u s e l m
# Extras: eye (Iris), noeye, tail (Schwanzfarbe), acc1..acc2, extra (Bartfäden/Beine), pect, fin_pattern,
#         add {Zeichen: [(x, y)]} zusätzliche Pixel in Vorlagen-Koordinaten
# =====================================================================================================================
FISH = [
    # ---- Flüsse & Seen ----
    ("rotauge", "Rotauge", "fluss", "g", (0.08, 0.6), "classic", ("4E6070", "B4C0CA", "EEF2F4", "D8433A"), "",
     {"eye": "D83030"}),
    ("flussbarsch", "Flussbarsch", "fluss", "g", (0.1, 1.2), "perch", ("3E5A22", "94A43E", "E6E2B8", "D8573A"), "bars",
     {"eye": "E8C83A"}),
    ("brasse", "Brasse", "fluss", "g", (0.3, 5.0), "deep", ("4E524A", "A8A48E", "E2DCC8", "5E5A50"), "",
     {"eye": "C8B890"}),
    ("bachforelle", "Bachforelle", "fluss", "u", (0.3, 2.5), "salmon", ("5A4A2A", "A8905A", "EAD8B0", "9A7A48"),
     "spots_red", {}),
    ("karpfen", "Karpfen", "fluss", "u", (1.0, 14.0), "deep", ("6A4E1A", "B8902E", "EED48A", "A8742A"), "scales",
     {"eye": "E8C060", "extra": "8A6A2A", "add": {"X": [(0, 8), (0, 9)]}}),
    ("aesche", "Äsche", "fluss", "u", (0.2, 2.5), "grayling", ("4E5A6A", "A8B4BE", "E8ECF0", "8A4A7A"), "spots_dark",
     {"fin_pattern": True}),
    ("zander", "Zander", "fluss", "s", (0.8, 9.0), "perch", ("4A5A54", "98A8A0", "E2E8E2", "7A8A82"), "bars_faint",
     {"eye": "E8E8D0"}),
    ("hecht", "Hecht", "fluss", "s", (1.5, 16.0), "pike", ("34502A", "72903E", "DCE4B0", "6E8040"), "spots_light",
     {"eye": "E8D27A"}),
    ("barbe", "Barbe", "fluss", "s", (0.5, 6.0), "barbel", ("6A5A32", "B4A26A", "EADCB0", "C8783A"), "",
     {"extra": "D8C8A0"}),
    ("wels", "Wels", "fluss", "e", (5.0, 60.0), "catfish", ("2A2622", "58503F", "B8AE98", "3E3830"), "mottled",
     {"extra": "9A8A6A"}),
    ("huchen", "Huchen", "fluss", "e", (5.0, 50.0), "salmon", ("4A3A3A", "A88A80", "E8D8D0", "8A6A62"), "spots_dark",
     {}),
    ("stoer", "Stör", "fluss", "l", (10.0, 120.0), "sturgeon", ("464C54", "868E98", "D8DCE0", "5E646C"), "",
     {"acc1": "E2DCC8", "extra": "C8C0A8"}),
    # ---- Ozean ----
    ("hering", "Hering", "ozean", "g", (0.05, 0.5), "slim", ("2A5684", "A4BACE", "EEF4F8", "7A96AE"), "", {}),
    ("makrele", "Makrele", "ozean", "g", (0.2, 1.5), "tuna", ("1A5E62", "88BCC0", "EEF6F6", "5A8A90"), "waves", {}),
    ("sardine", "Sardine", "ozean", "g", (0.03, 0.2), "slim", ("2E4E7A", "9AB2C8", "F0F4F8", "6E86A0"), "dots_row",
     {}),
    ("kabeljau", "Kabeljau", "ozean", "u", (1.0, 12.0), "cod", ("6A5636", "B09A6C", "ECE2C8", "8E7A54"),
     "spots_dark", {"extra": "D8CCA8"}),
    ("dorade", "Dorade", "ozean", "u", (0.4, 3.0), "deep", ("566676", "B8C4CE", "F2F4F6", "8A9AA8"), "gold_band", {}),
    ("rotbarsch", "Rotbarsch", "ozean", "u", (0.5, 6.0), "perch", ("A8281E", "E05A3A", "F2B0A0", "C83A2A"), "",
     {"eye": "1E1A28"}),
    ("thunfisch", "Thunfisch", "ozean", "s", (10.0, 250.0), "tuna", ("16285A", "62809E", "DDE4EC", "E2C23A"),
     "tuna_stripe", {"pect": "34507A", "tail": "2A4070"}),
    ("schwertfisch", "Schwertfisch", "ozean", "s", (20.0, 300.0), "sword", ("2A3A5A", "74889E", "D8E0E8", "4A5E7A"),
     "", {}),
    ("seeteufel", "Seeteufel", "ozean", "s", (2.0, 40.0), "angler", ("5A4632", "8E7656", "C8B490", "6E5A42"),
     "mottled", {"acc1": "F2EEE0", "acc2": "FFE27A", "extra": "8E7656"}),
    ("mondfisch", "Mondfisch", "ozean", "e", (50.0, 1000.0), "sunfish", ("6A7280", "AAB2BC", "E2E6EA", "8A929A"),
     "mottled_light", {}),
    ("barrakuda", "Barrakuda", "ozean", "e", (3.0, 40.0), "barracuda", ("3A4A5A", "98A6B2", "E8ECF0", "5A6A7A"),
     "bars_faint", {}),
    ("weisser_hai", "Weißer Hai", "ozean", "l", (300.0, 1800.0), "shark", ("4E5862", "8E98A2", "F2F2F2", "5E6872"),
     "", {}),
    # ---- Warme Gewässer ----
    ("clownfisch", "Clownfisch", "warm", "g", (0.05, 0.3), "clown", ("C84A16", "F07828", "FFA860", "E05A1A"), "clown",
     {}),
    ("doktorfisch", "Doktorfisch", "warm", "g", (0.2, 1.0), "disc", ("1A3A9A", "3A6AE0", "6A9AF0", "2A4AB0"), "doctor",
     {"tail": "F2D23A"}),
    ("riffbarsch", "Riffbarsch", "warm", "g", (0.02, 0.15), "damsel", ("1A4AAA", "3A7AE8", "F2D23A", "F2C82A"), "",
     {}),
    ("papageifisch", "Papageifisch", "warm", "u", (1.0, 9.0), "deep", ("1A8A7A", "3AC8A8", "9AF0D8", "E85AA8"),
     "scales_pink", {"eye": "F2D23A"}),
    ("kugelfisch", "Kugelfisch", "warm", "u", (0.3, 3.0), "puffer", ("A8781E", "E2B23A", "F8E4A8", "6AA8D8"),
     "spines", {"extra": "C8A050"}),
    ("kaiserfisch", "Kaiserfisch", "warm", "u", (0.3, 1.5), "disc", ("1A2A8A", "2A4AC8", "4A6AE0", "F2C82A"),
     "emperor", {}),
    ("feuerfisch", "Feuerfisch", "warm", "s", (0.3, 1.5), "lion", ("7A1A1A", "D8503A", "F8D2C0", "D84A3A"), "lion",
     {"fin_pattern": True, "eye": "E83A2A"}),
    ("mahi_mahi", "Mahi-Mahi", "warm", "s", (5.0, 40.0), "mahi", ("1E7A3A", "5AC83A", "F2E25A", "2A9AD8"),
     "spots_blue", {}),
    ("seepferdchen", "Seepferdchen", "warm", "s", (0.01, 0.05), "seahorse", ("C87A1E", "F2A83A", "FFD88A", "E89030"),
     "rings", {}),
    ("mantarochen", "Mantarochen", "warm", "e", (200.0, 2000.0), "ray", ("1A222E", "36424E", "E8ECF0", "2A3442"), "",
     {"acc1": "D8DEE6", "extra": "36424E"}),
    ("hammerhai", "Hammerhai", "warm", "e", (100.0, 450.0), "hammer", ("5E625E", "A0A29C", "EEEEE8", "6E726E"), "", {}),
    ("marlin", "Blauer Marlin", "warm", "l", (100.0, 800.0), "marlin", ("1A3A8A", "4A7AC8", "DDE6F2", "2A5AB0"),
     "bars_blue", {}),
    # ---- Kalte Gewässer ----
    ("lodde", "Lodde", "kalt", "g", (0.02, 0.1), "slim", ("467666", "A4C4B6", "EEF6F2", "7AA090"), "", {}),
    ("polardorsch", "Polardorsch", "kalt", "g", (0.1, 0.8), "cod", ("56666A", "A4B0B2", "E8EEEE", "7E8A8E"),
     "spots_dark", {"extra": "C8D0D2"}),
    ("seehase", "Seehase", "kalt", "g", (1.0, 6.0), "lump", ("4A6A3A", "8AA05A", "D8C878", "5A7A4A"), "bumps", {}),
    ("saibling", "Seesaibling", "kalt", "u", (0.3, 4.0), "salmon", ("34523E", "72927A", "E8603A", "D8583A"),
     "spots_cream", {}),
    ("lachs", "Lachs", "kalt", "u", (2.0, 20.0), "salmon", ("36485E", "A4B4C4", "F2C8C0", "6E7E92"), "spots_dark", {}),
    ("rotlachs", "Rotlachs", "kalt", "u", (1.5, 7.0), "salmon", ("A8281E", "D83A2A", "E8604A", "3A6A42"), "redhead",
     {}),
    ("heilbutt", "Heilbutt", "kalt", "s", (5.0, 200.0), "flat", ("564636", "8A765A", "C8B898", "6E5E48"), "mottled",
     {}),
    ("steinbeisser", "Steinbeißer", "kalt", "s", (2.0, 20.0), "eelfish", ("363A42", "6A7080", "B8BEC6", "4E545E"),
     "bars", {"acc1": "F2EEE0", "add": {"1": [(0, 6)]}}),
    ("eisfisch", "Eisfisch", "kalt", "s", (0.3, 2.0), "pike", ("7A98AA", "C0D6E2", "F2F8FC", "A0BCCC"), "ghost",
     {"eye": "1E2A3A"}),
    ("groenlandhai", "Grönlandhai", "kalt", "e", (200.0, 1000.0), "shark", ("3A3E44", "666C72", "969CA2", "4A5056"),
     "mottled", {}),
    ("heringshai", "Heringshai", "kalt", "e", (100.0, 250.0), "shark", ("2A3A4E", "5A6E86", "E8ECF0", "3A4A60"), "",
     {}),
    ("kristallforelle", "Kristallforelle", "kalt", "l", (1.0, 12.0), "salmon", ("3AA0D0", "A0E0F6", "F2FCFF", "7AD0F0"),
     "crystal", {}),
    # ---- Sumpf & Höhlen ----
    ("schlammpeitzger", "Schlammpeitzger", "sumpf", "g", (0.02, 0.15), "eelfish", ("46361E", "847040", "C8B488",
                                                                                     "6A5634"),
     "stripes_long", {"extra": "C8B488", "add": {"X": [(0, 8), (0, 9)]}}),
    ("moderlieschen", "Moderlieschen", "sumpf", "g", (0.01, 0.05), "slim", ("566646", "B4BEA4", "EEF2E4", "8E9A7E"),
     "line", {}),
    ("rotfeder", "Rotfeder", "sumpf", "g", (0.1, 1.5), "classic", ("4E5226", "B8A660", "F0E2B0", "D8402A"), "",
     {"eye": "E8B030"}),
    ("aal", "Aal", "sumpf", "u", (0.3, 5.0), "eel", ("2A361A", "5A6636", "C8C49A", "3E4A2A"), "", {}),
    ("schleie", "Schleie", "sumpf", "u", (0.3, 6.0), "deep", ("2A461A", "6A8636", "C8C47A", "4A6A2A"), "",
     {"eye": "D83030", "extra": "6A8636", "add": {"X": [(0, 8)]}}),
    ("piranha", "Piranha", "sumpf", "u", (0.3, 3.5), "deep", ("5A626A", "A8B0B4", "D8402A", "6A7276"), "sparkle",
     {"eye": "D83030", "acc1": "F4F0E4", "add": {"1": [(1, 7)], "k": [(0, 7)]}}),
    ("grottenolm", "Grottenolm", "sumpf", "s", (0.01, 0.05), "olm", ("D8B0A8", "F0D0C8", "FFE8E2", "E8C4BC"), "",
     {"acc1": "E8506A"}),
    ("hoehlensalmler", "Höhlensalmler", "sumpf", "s", (0.02, 0.2), "classic", ("C89A9A", "F0C8C8", "FFEAEA", "E8B0B0"),
     "", {"noeye": True}),
    ("axolotl", "Axolotl", "sumpf", "s", (0.05, 0.3), "axolotl", ("D88AA0", "F2B0C2", "FCD8E0", "F2C0D0"), "",
     {"acc1": "D8405A", "extra": "F2B0C2"}),
    ("lungenfisch", "Lungenfisch", "sumpf", "e", (2.0, 20.0), "eelfish", ("36362A", "6A664A", "A8A07A", "5A563E"),
     "spots_dark", {}),
    ("schlangenkopf", "Schlangenkopf", "sumpf", "e", (2.0, 15.0), "eelfish", ("3A3A26", "7A7246", "C8BC8A", "5A5236"),
     "blotches", {}),
    ("gluehwels", "Glühwels", "sumpf", "l", (3.0, 40.0), "catfish", ("10202A", "26404E", "4A6A7A", "1E2E38"), "glow",
     {"extra": "4AF0E8"}),
    # ---- Mythen & Zone ----
    ("gluecks_karpfen", "Glücks-Karpfen", "mythos", "s", (3.0, 25.0), "deep", ("2A561A", "5A8A2A", "E8C83A", "E8B82A"),
     "lucky", {"eye": "F2D23A", "extra": "E8C83A", "add": {"X": [(0, 8), (0, 9)]}}),
    ("sternenhecht", "Sternenhecht", "mythos", "s", (2.0, 18.0), "pike", ("101A46", "263A8A", "6A7AD8", "3A4AB0"),
     "stars", {"eye": "FFF4A0"}),
    ("kristallqualle", "Kristallqualle", "mythos", "s", (0.5, 8.0), "jelly", ("4AA8D8", "8AD8F2", "D8F6FF", "8AD8F0"),
     "", {"acc1": "F2A8E8", "extra": "A8E8F8"}),
    ("geisterfisch", "Geisterfisch", "mythos", "e", (0.5, 6.0), "classic", ("A4B4C4", "D8E2EC", "F8FBFF", "C8D8E8"),
     "ghost", {"eye": "4AD8F0"}),
    ("lavaforelle", "Lavaforelle", "mythos", "e", (1.0, 10.0), "salmon", ("5A1A0A", "D8501A", "FFC23A", "FF8A2A"),
     "lava", {}),
    ("drachenfisch", "Drachenfisch", "mythos", "e", (0.5, 6.0), "dragon", ("241A3A", "3E2E62", "6E5A9A", "4A3A72"),
     "glowdots", {"acc1": "F2F0E8", "acc2": "6AF0FF", "extra": "5A4670"}),
    ("kraken_junges", "Kraken-Junges", "mythos", "l", (5.0, 80.0), "squid", ("4E1A5E", "A048C0", "E8A8F0", "7A2E8A"),
     "spots_light", {"extra": "C878D8"}),
    ("phoenixfisch", "Phönixfisch", "mythos", "l", (2.0, 30.0), "fancy", ("A01A1A", "F0581A", "FFD23A", "FFB02A"),
     "flame", {"fin_pattern": True, "eye": "FFD23A"}),
    ("nebelrochen", "Nebelrochen", "mythos", "l", (50.0, 600.0), "ray", ("5A6A86", "9AAAC4", "E8F0FA", "7A8AA8"),
     "ghost", {"acc1": "E8F0FA", "extra": "9AAAC4"}),
    ("leviathan", "Leviathan", "mythos", "m", (500.0, 5000.0), "serpent", ("0E3636", "1E6262", "6AC8B8", "2AA898"),
     "scales_dark", {"extra": "E8E0C0", "eye": "F2D23A"}),
    ("ur_koi", "Ur-Koi", "mythos", "m", (5.0, 50.0), "deep", ("D8D4CC", "FAF8F2", "FFFFFF", "F2D23A"), "koi",
     {"extra": "F2D23A", "add": {"X": [(0, 8), (0, 9)]}}),
    ("abgrundangler", "Abgrundangler", "mythos", "m", (10.0, 120.0), "angler", ("1E2038", "34365A", "565A82",
                                                                                 "2C2E4C"),
     "glowdots", {"acc1": "E8F0FF", "acc2": "6AF0FF", "extra": "3A3A5A"}),
]


# =====================================================================================================================
# Muster: (x, y, v, t, Farbe, xr) -> neue Farbe oder None. v: 0 Rücken .. 1 Bauch (Flossen -1), xr: Spalte ab Kopf
# =====================================================================================================================
def spots(mod, rest, lo, hi, color_fn, start=3):
    return lambda x, y, v, t, c, xr: (color_fn(c) if lo <= v < hi and xr >= start and (xr * 3 + y * 5) % mod == rest
                                      else None)


PATTERNS = {
    "": None,
    "bars": lambda x, y, v, t, c, xr: dark(c, 0.16) if 0 <= v < 0.84 and xr in (4, 6, 8) else None,
    "bars_faint": lambda x, y, v, t, c, xr: dark(c, 0.08) if 0 <= v < 0.8 and xr in (4, 6, 8) else None,
    "bars_blue": lambda x, y, v, t, c, xr: mix(c, rgb("8AC8FF"), 0.6) if 0.12 < v < 0.7 and xr in (6, 8) else None,
    "spots_red": spots(7, 0, 0.15, 0.78, lambda c: rgb("D83A2A")),
    "spots_dark": spots(7, 0, 0.0, 0.72, lambda c: dark(c, 0.2)),
    "spots_light": spots(5, 0, 0.2, 0.82, lambda c: light(c, 0.16)),
    "spots_cream": spots(7, 0, 0.0, 0.6, lambda c: rgb("F2E8C8")),
    "spots_blue": spots(7, 0, 0.0, 0.66, lambda c: rgb("2A8AE0"), 2),
    "scales": lambda x, y, v, t, c, xr: dark(c, 0.07) if 0.15 < v < 0.86 and xr >= 3 and (xr + 2 * y) % 4 == 0 else None,
    "scales_pink": lambda x, y, v, t, c, xr: rgb("E85AA8") if 0 <= v < 0.86 and xr >= 3 and (xr + 2 * y) % 4 == 0 else None,
    "scales_dark": lambda x, y, v, t, c, xr: dark(c, 0.12) if v >= 0 and (xr + 2 * y) % 4 == 0 else None,
    "waves": lambda x, y, v, t, c, xr: dark(c, 0.2) if 0 <= v < 0.46 and xr >= 3 and (xr + y) % 3 == 0 else None,
    "gold_band": lambda x, y, v, t, c, xr: rgb("E8C040") if xr == 2 and 0 <= v < 0.45 else None,
    "clown": lambda x, y, v, t, c, xr: ((246, 246, 240) if xr in (2, 6, 9) else (30, 24, 26) if xr in (3, 7, 10)
                                        else None) if v >= 0 else None,
    "doctor": lambda x, y, v, t, c, xr: rgb("10183A") if 0 <= v < 0.3 and 3 <= xr <= 8 else None,
    "spines": spots(7, 0, 0.0, 0.85, lambda c: dark(c, 0.18), 1),
    "lion": lambda x, y, v, t, c, xr: rgb("F8E8E0") if xr % 2 == 0 and xr > 0 else None,
    "mottled": lambda x, y, v, t, c, xr: dark(c, 0.12) if 0 <= v < 0.72 and (xr * 7 + y * 3) % 6 == 0 else None,
    "mottled_light": lambda x, y, v, t, c, xr: light(c, 0.1) if v >= 0 and (xr * 7 + y * 3) % 6 == 0 else None,
    "crystal": lambda x, y, v, t, c, xr: (246, 252, 255) if v >= 0 and (xr * 2 + y * 3) % 7 == 0 else None,
    "stripes_long": lambda x, y, v, t, c, xr: dark(c, 0.18) if 0.4 < v < 0.62 and xr >= 2 else None,
    "line": lambda x, y, v, t, c, xr: mix(c, rgb("8AA8C8"), 0.6) if 0.44 < v < 0.62 and xr >= 3 else None,
    "glow": lambda x, y, v, t, c, xr: rgb("4AF0E8") if v >= 0 and (xr * 3 + y * 5) % 6 == 0 else None,
    "lucky": lambda x, y, v, t, c, xr: rgb("FFE27A") if 0.15 < v < 0.86 and xr >= 3 and (xr + 2 * y) % 4 == 0 else None,
    "stars": spots(7, 0, 0.0, 0.9, lambda c: rgb("FFF4A0"), 2),
    "ghost": lambda x, y, v, t, c, xr: mix(c, (240, 246, 255), 0.25),
    "lava": lambda x, y, v, t, c, xr: (rgb("FFE27A") if v >= 0 and (xr * 3 + y * 5) % 5 == 0 else
                                       rgb("3A0E06") if 0 <= v < 0.5 and (xr * 3 + y * 5) % 5 == 2 else None),
    "flame": lambda x, y, v, t, c, xr: (rgb("FFE27A") if v < 0 and (x + y) % 3 == 0 else
                                        rgb("FFD23A") if 0 <= v < 0.3 else None),
    "koi": lambda x, y, v, t, c, xr: rgb("E8381E") if v >= 0 and xr >= 2 and (xr // 2 + y // 2) % 3 == 0 else None,
    "tuna_stripe": lambda x, y, v, t, c, xr: rgb("E2C23A") if 0.4 < v < 0.56 and xr >= 5 else None,
    "dots_row": lambda x, y, v, t, c, xr: dark(c, 0.25) if 0.28 < v < 0.5 and xr in (4, 6, 8) else None,
    "emperor": lambda x, y, v, t, c, xr: rgb("F2D23A") if v >= 0 and y % 2 == 0 and xr >= 3 else None,
    "rings": lambda x, y, v, t, c, xr: dark(c, 0.08) if v >= 0 and y % 2 == 0 else None,
    "redhead": lambda x, y, v, t, c, xr: rgb("3A7A42") if v >= 0 and xr <= 3 else None,
    "bumps": spots(5, 0, 0.0, 0.9, lambda c: light(c, 0.12), 1),
    "sparkle": spots(7, 0, 0.0, 0.6, lambda c: light(c, 0.18), 2),
    "blotches": lambda x, y, v, t, c, xr: dark(c, 0.15) if v >= 0 and ((xr // 2) * 3 + (y // 2) * 5) % 4 == 0 else None,
    "glowdots": lambda x, y, v, t, c, xr: rgb("6AF0FF") if v > 0.45 and xr % 3 == 1 and y % 2 == 0 else None,
}


def palette(fish):
    cols, extra = fish[6], fish[8]
    p = dict(back=rgb(cols[0]), body=rgb(cols[1]), belly=rgb(cols[2]), fin=rgb(cols[3]))
    for k in ("eye", "tail", "acc1", "acc2", "acc3", "acc4", "extra", "pect"):
        if extra.get(k):
            p[k] = rgb(extra[k])
    if extra.get("fin_pattern"):
        p["fin_pattern"] = True
    return p


def grid(fish):
    """Vorlage der Form mit den Zusätzen der Art (Iris, ohne Augen, eigene Pixel)."""
    extra = fish[8]
    rows = [list(r.ljust(14, ".")) for r in fish_shapes.SHAPES[fish[5]]]
    while len(rows) < 14:
        rows.append(["."] * 14)
    for y in range(len(rows)):
        for x in range(14):
            if rows[y][x] == "w":
                if extra.get("noeye"):
                    rows[y][x] = "b"
                    for dx, dy in ((1, 0), (0, 1), (1, 1)):
                        if y + dy < len(rows) and x + dx < 14 and rows[y + dy][x + dx] in "ei":
                            rows[y + dy][x + dx] = "b"
                elif extra.get("eye") and y + 1 < len(rows) and rows[y + 1][x] == "e":
                    rows[y + 1][x] = "i"
    for ch, pts in extra.get("add", {}).items():
        for x, y in pts:
            if 0 <= y < len(rows) and 0 <= x < 14:
                rows[y][x] = ch
    return ["".join(r) for r in rows]


def render(fish, silhouette=False):
    return from_grid(grid(fish), palette(fish), PATTERNS.get(fish[7]), silhouette=silhouette)


# Arten, die nachts doppelt so oft beißen
NIGHT = {"wels", "aal", "gluehwels", "sternenhecht", "geisterfisch", "lungenfisch", "zander", "groenlandhai",
         "seeteufel", "abgrundangler", "drachenfisch", "barbe", "schlangenkopf", "nebelrochen"}
WATERS = {"fluss": "FLUSS", "ozean": "OZEAN", "warm": "WARM", "kalt": "KALT", "sumpf": "SUMPF", "mythos": "MYTHOS"}
RARITIES = {"g": "GEWOEHNLICH", "u": "UNGEWOEHNLICH", "s": "SELTEN", "e": "EPISCH", "l": "LEGENDAER", "m": "MYTHISCH"}
JAVA_OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "KubySurvival-v1.0.0", "KubySurvival", "src",
                        "main", "java", "de", "kuby", "kubysurvival", "angeln", "FishSpecies.java")


def write_java():
    L = ["package de.kuby.kubysurvival.angeln;", "", "import java.util.List;", "",
         "// ERZEUGT von tools/build_fish.py - nicht von Hand ändern.", "",
         "/** Fischart: ID (= Textur kuby:item/fish/<id>), Name, Gewässer, Seltenheit, Gewicht von-bis, nachtaktiv. */",
         "public record FishSpecies(String id, String name, Water water, Rarity rarity, double minKg, double maxKg,",
         "                          boolean night, int index) {", "",
         "    public static final List<FishSpecies> ALL = List.of("]
    rows = []
    for i, f in enumerate(FISH):
        fid, name, water, rarity, kg = f[:5]
        rows.append('            new FishSpecies("%s", "%s", Water.%s, Rarity.%s, %s, %s, %s, %d)' % (
            fid, name, WATERS[water], RARITIES[rarity], repr(float(kg[0])), repr(float(kg[1])),
            "true" if fid in NIGHT else "false", i))
    L.append(",\n".join(rows) + ");")
    L += ["", "    public static FishSpecies byId(String id) {",
          "        for (FishSpecies s : ALL) if (s.id.equals(id)) return s;",
          "        return null;", "    }", "", "    public static List<FishSpecies> of(Water w) {",
          "        return ALL.stream().filter(s -> s.water == w).toList();", "    }", "}", ""]
    with open(JAVA_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(L))


def build(out):
    write_java()
    tex = os.path.join(out, "assets/kuby/textures/item/fish")
    models = os.path.join(out, "assets/kuby/models/item/fish")
    items = os.path.join(out, "assets/kuby/items/fish")
    for d in (tex, models, items):
        os.makedirs(d, exist_ok=True)
    cols = 12
    rows = (len(FISH) + cols - 1) // cols
    sheet = Image.new("RGBA", (16 * cols, 16 * rows * 2), T)
    for i, f in enumerate(FISH):
        img = render(f)
        img.save(os.path.join(tex, f[0] + ".png"))
        with open(os.path.join(models, f[0] + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kuby:item/fish/" + f[0]}}, fh)
        with open(os.path.join(items, f[0] + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "kuby:item/fish/" + f[0]}}, fh)
        sheet.alpha_composite(img, ((i % cols) * 16, (i // cols) * 16))
        sheet.alpha_composite(render(f, silhouette=True), ((i % cols) * 16, (rows + i // cols) * 16))
    return sheet


if __name__ == "__main__":
    s = build(sys.argv[1])
    s.save(os.path.join(sys.argv[1], "fish_sheet_preview.png"))
