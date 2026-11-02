#!/usr/bin/env python3
"""Deutsche Item-Namen für Dialoge (z. B. Shop-Karten), ohne Sprachdatei vom Spiel.

Regeln für die großen Familien (Holzarten, Farben, Kupfer, Stein-Varianten) und ein Wörterbuch für einzelne Items.
Was keine Regel trifft, bekommt einen lesbaren Namen aus der ID (Wörter groß). Ergebnis:
KubyCore …/resources/ui/names_de.tsv (MATERIAL TAB Name). Eigene Namen im Shop (name:) haben immer Vorrang.

Aufruf: KUBY_VANILLA=<vanilla-assets> python3 tools/build_item_names.py
"""
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VAN = os.environ.get("KUBY_VANILLA")
OUT = os.path.join(REPO, "KubyCore-v2.0.14/KubyPlugins/src/main/resources/ui/names_de.tsv")

WOODS = {"oak": "Eichen", "spruce": "Fichten", "birch": "Birken", "jungle": "Tropen", "acacia": "Akazien",
         "dark_oak": "Schwarzeichen", "mangrove": "Mangroven", "cherry": "Kirsch", "pale_oak": "Blasseichen",
         "bamboo": "Bambus", "crimson": "Karmesin", "warped": "Wirr"}
# Holz-Teile: (Endung der ID, deutscher Teil, braucht „holz“ bei echten Bäumen)
WOOD_PARTS = [("hanging_sign", "hängeschild", True), ("pressure_plate", "druckplatte", True), ("fence_gate", "zauntor", True),
              ("trapdoor", "falltür", True), ("chest_boat", "truhenboot", True), ("chest_raft", "truhenfloß", False),
              ("planks", "bretter", True), ("stairs", "treppe", True), ("slab", "stufe", True), ("fence", "zaun", True),
              ("door", "tür", True), ("button", "knopf", True), ("sign", "schild", True), ("boat", "boot", True),
              ("raft", "floß", False), ("log", "stamm", False), ("wood", "holz", False), ("leaves", "laub", False),
              ("sapling", "setzling", False), ("stem", "stiel", False), ("hyphae", "hyphen", False), ("shelf", "regal", True)]
COLORS = {"white": "Weiß", "orange": "Orange", "magenta": "Magenta", "light_blue": "Hellblau", "yellow": "Gelb",
          "lime": "Hellgrün", "pink": "Rosa", "gray": "Grau", "light_gray": "Hellgrau", "cyan": "Türkis", "purple": "Violett",
          "blue": "Blau", "brown": "Braun", "green": "Grün", "red": "Rot", "black": "Schwarz"}
INVARIANT = {"Orange", "Magenta", "Rosa"}
# Farbige Teile: (Endung, Nomen, Geschlecht f/m/n)
COLOR_PARTS = [("stained_glass_pane", "Glasscheibe", "f"), ("stained_glass", "Glas", "n"), ("glazed_terracotta", "glasierte Keramik", "f"),
               ("concrete_powder", "Trockenbeton", "m"), ("concrete", "Beton", "m"), ("terracotta", "Keramik", "f"),
               ("wool", "Wolle", "f"), ("carpet", "Teppich", "m"), ("bed", "Bett", "n"), ("dye", "Farbstoff", "m"),
               ("banner", "Banner", "n"), ("candle", "Kerze", "f"), ("shulker_box", "Shulker-Kiste", "f"), ("bundle", "Bündel", "n"),
               ("harness", "Geschirr", "n")]
ENDING = {"f": "e", "m": "er", "n": "es"}

WORDS = {
    # Natur und Bauen
    "stone": "Stein", "cobblestone": "Bruchstein", "mossy_cobblestone": "Bemooster Bruchstein", "stone_bricks": "Steinziegel",
    "mossy_stone_bricks": "Bemooste Steinziegel", "cracked_stone_bricks": "Rissige Steinziegel", "chiseled_stone_bricks": "Gemeißelte Steinziegel",
    "smooth_stone": "Glatter Stein", "granite": "Granit", "polished_granite": "Polierter Granit", "diorite": "Diorit",
    "polished_diorite": "Polierter Diorit", "andesite": "Andesit", "polished_andesite": "Polierter Andesit", "deepslate": "Tiefenschiefer",
    "cobbled_deepslate": "Bruchtiefenschiefer", "polished_deepslate": "Polierter Tiefenschiefer", "deepslate_bricks": "Tiefenschieferziegel",
    "deepslate_tiles": "Tiefenschieferfliesen", "tuff": "Tuffstein", "calcite": "Kalzit", "dripstone_block": "Tropfsteinblock",
    "dirt": "Erde", "coarse_dirt": "Grobe Erde", "rooted_dirt": "Wurzelerde", "grass_block": "Grasblock", "podzol": "Podsol",
    "mycelium": "Myzel", "mud": "Schlamm", "packed_mud": "Fester Schlamm", "mud_bricks": "Schlammziegel", "clay": "Ton",
    "clay_ball": "Tonklumpen", "gravel": "Kies", "sand": "Sand", "red_sand": "Roter Sand", "sandstone": "Sandstein",
    "red_sandstone": "Roter Sandstein", "smooth_sandstone": "Glatter Sandstein", "cut_sandstone": "Geschnittener Sandstein",
    "glass": "Glas", "glass_pane": "Glasscheibe", "tinted_glass": "Getöntes Glas", "bricks": "Ziegelsteine", "brick": "Ziegel",
    "terracotta": "Keramik", "obsidian": "Obsidian", "crying_obsidian": "Weinender Obsidian", "bedrock": "Grundgestein",
    "ice": "Eis", "packed_ice": "Packeis", "blue_ice": "Blaueis", "snow": "Schnee", "snow_block": "Schneeblock", "snowball": "Schneeball",
    "netherrack": "Netherrack", "nether_bricks": "Netherziegel", "red_nether_bricks": "Rote Netherziegel", "soul_sand": "Seelensand",
    "soul_soil": "Seelenerde", "basalt": "Basalt", "polished_basalt": "Polierter Basalt", "blackstone": "Schwarzstein",
    "polished_blackstone": "Polierter Schwarzstein", "glowstone": "Leuchtstein", "glowstone_dust": "Glowstonestaub",
    "end_stone": "Endstein", "end_stone_bricks": "Endsteinziegel", "purpur_block": "Purpurblock", "prismarine": "Prismarin",
    "prismarine_bricks": "Prismarinziegel", "dark_prismarine": "Dunkler Prismarin", "sea_lantern": "Seelaterne",
    "quartz_block": "Quarzblock", "smooth_quartz": "Glatter Quarz", "quartz_bricks": "Quarzziegel", "quartz": "Netherquarz",
    "amethyst_block": "Amethystblock", "amethyst_shard": "Amethystscherbe", "moss_block": "Moosblock", "moss_carpet": "Moosteppich",
    "sponge": "Schwamm", "wet_sponge": "Nasser Schwamm", "hay_block": "Heuballen", "bone_block": "Knochenblock",
    "magma_block": "Magmablock", "slime_block": "Schleimblock", "honey_block": "Honigblock", "honeycomb_block": "Honigwabenblock",
    "cactus": "Kaktus", "sugar_cane": "Zuckerrohr", "bamboo": "Bambus", "vine": "Ranken", "lily_pad": "Seerosenblatt",
    "pumpkin": "Kürbis", "carved_pumpkin": "Geschnitzter Kürbis", "jack_o_lantern": "Kürbislaterne", "melon": "Melone",
    "short_grass": "Gras", "tall_grass": "Hohes Gras", "fern": "Farn", "large_fern": "Großer Farn", "dead_bush": "Toter Busch",
    "dandelion": "Löwenzahn", "poppy": "Mohn", "blue_orchid": "Blaue Orchidee", "allium": "Sternlauch", "azure_bluet": "Porzellansternchen",
    "red_tulip": "Rote Tulpe", "orange_tulip": "Orange Tulpe", "white_tulip": "Weiße Tulpe", "pink_tulip": "Rosa Tulpe",
    "oxeye_daisy": "Margerite", "cornflower": "Kornblume", "lily_of_the_valley": "Maiglöckchen", "sunflower": "Sonnenblume",
    "lilac": "Flieder", "rose_bush": "Rosenstrauch", "peony": "Pfingstrose", "torchflower": "Fackellilie", "pink_petals": "Rosa Blütenblätter",
    "brown_mushroom": "Brauner Pilz", "red_mushroom": "Roter Pilz", "flower_pot": "Blumentopf", "azalea": "Azalee",
    # Erze und Rohstoffe
    "coal": "Kohle", "charcoal": "Holzkohle", "coal_block": "Kohleblock", "coal_ore": "Steinkohle", "iron_ore": "Eisenerz",
    "gold_ore": "Golderz", "copper_ore": "Kupfererz", "diamond_ore": "Diamanterz", "emerald_ore": "Smaragderz", "lapis_ore": "Lapislazulierz",
    "redstone_ore": "Redstone-Erz", "nether_gold_ore": "Nethergolderz", "nether_quartz_ore": "Netherquarzerz", "ancient_debris": "Antiker Schrott",
    "iron_ingot": "Eisenbarren", "gold_ingot": "Goldbarren", "copper_ingot": "Kupferbarren", "netherite_ingot": "Netheritbarren",
    "netherite_scrap": "Netheritplatten", "iron_nugget": "Eisenklumpen", "gold_nugget": "Goldklumpen", "raw_iron": "Roheisen",
    "raw_gold": "Rohgold", "raw_copper": "Rohkupfer", "diamond": "Diamant", "emerald": "Smaragd", "lapis_lazuli": "Lapislazuli",
    "redstone": "Redstone-Staub", "iron_block": "Eisenblock", "gold_block": "Goldblock", "diamond_block": "Diamantblock",
    "emerald_block": "Smaragdblock", "lapis_block": "Lapislazuliblock", "redstone_block": "Redstone-Block", "netherite_block": "Netheritblock",
    "copper_block": "Kupferblock", "raw_iron_block": "Roheisenblock", "raw_gold_block": "Rohgoldblock", "raw_copper_block": "Rohkupferblock",
    # Essen
    "apple": "Apfel", "golden_apple": "Goldener Apfel", "enchanted_golden_apple": "Verzauberter Goldener Apfel", "bread": "Brot",
    "wheat": "Weizen", "wheat_seeds": "Weizenkörner", "carrot": "Karotte", "golden_carrot": "Goldene Karotte", "potato": "Kartoffel",
    "baked_potato": "Ofenkartoffel", "poisonous_potato": "Giftige Kartoffel", "beetroot": "Rote Bete", "beetroot_soup": "Rote-Bete-Suppe",
    "beetroot_seeds": "Rote-Bete-Samen", "melon_slice": "Melonenscheibe", "melon_seeds": "Melonenkerne", "pumpkin_seeds": "Kürbiskerne",
    "pumpkin_pie": "Kürbiskuchen", "cookie": "Keks", "cake": "Kuchen", "sweet_berries": "Süßbeeren", "glow_berries": "Leuchtbeeren",
    "beef": "Rohes Rindfleisch", "cooked_beef": "Steak", "porkchop": "Rohes Schweinefleisch", "cooked_porkchop": "Gebratenes Schweinefleisch",
    "chicken": "Rohes Hühnchen", "cooked_chicken": "Gebratenes Hühnchen", "mutton": "Rohes Hammelfleisch", "cooked_mutton": "Gebratenes Hammelfleisch",
    "rabbit": "Rohes Kaninchen", "cooked_rabbit": "Gebratenes Kaninchen", "rabbit_stew": "Kaninchenragout", "cod": "Roher Kabeljau",
    "cooked_cod": "Gebratener Kabeljau", "salmon": "Roher Lachs", "cooked_salmon": "Gebratener Lachs", "tropical_fish": "Tropenfisch",
    "pufferfish": "Kugelfisch", "mushroom_stew": "Pilzsuppe", "suspicious_stew": "Seltsame Suppe", "honey_bottle": "Honigflasche",
    "dried_kelp": "Getrockneter Seetang", "dried_kelp_block": "Getrockneter Seetangblock", "kelp": "Seetang", "sugar": "Zucker",
    "egg": "Ei", "milk_bucket": "Milcheimer", "chorus_fruit": "Chorusfrucht", "cocoa_beans": "Kakaobohnen", "rotten_flesh": "Verrottetes Fleisch",
    "spider_eye": "Spinnenauge",
    # Redstone
    "repeater": "Redstone-Verstärker", "comparator": "Redstone-Komparator", "redstone_torch": "Redstone-Fackel", "redstone_lamp": "Redstone-Lampe",
    "piston": "Kolben", "sticky_piston": "Klebriger Kolben", "observer": "Beobachter", "hopper": "Trichter", "dispenser": "Werfer",
    "dropper": "Spender", "lever": "Hebel", "tripwire_hook": "Haken", "daylight_detector": "Tageslichtsensor", "target": "Zielblock",
    "note_block": "Notenblock", "jukebox": "Plattenspieler", "tnt": "TNT", "rail": "Schiene", "powered_rail": "Antriebsschiene",
    "detector_rail": "Sensorschiene", "activator_rail": "Aktivierungsschiene", "minecart": "Lore", "chest_minecart": "Güterlore",
    "hopper_minecart": "Trichterlore", "tnt_minecart": "TNT-Lore", "furnace_minecart": "Ofenlore", "lightning_rod": "Blitzableiter",
    "sculk_sensor": "Sculk-Sensor", "calibrated_sculk_sensor": "Kalibrierter Sculk-Sensor", "crafter": "Werkbank-Automat",
    "stone_button": "Steinknopf", "stone_pressure_plate": "Steindruckplatte", "light_weighted_pressure_plate": "Wägeplatte (leicht)",
    "heavy_weighted_pressure_plate": "Wägeplatte (schwer)", "iron_door": "Eisentür", "iron_trapdoor": "Eisenfalltür", "slime_ball": "Schleimball",
    "string": "Faden", "tripwire": "Stolperdraht",
    # Deko und Möbel
    "torch": "Fackel", "soul_torch": "Seelenfackel", "lantern": "Laterne", "soul_lantern": "Seelenlaterne", "campfire": "Lagerfeuer",
    "soul_campfire": "Seelenlagerfeuer", "chain": "Kette", "iron_bars": "Eisengitter", "ladder": "Leiter", "scaffolding": "Gerüst",
    "bookshelf": "Bücherregal", "chiseled_bookshelf": "Gemeißeltes Bücherregal", "crafting_table": "Werkbank", "furnace": "Ofen",
    "blast_furnace": "Schmelzofen", "smoker": "Räucherofen", "anvil": "Amboss", "chipped_anvil": "Angeschlagener Amboss",
    "damaged_anvil": "Beschädigter Amboss", "enchanting_table": "Zaubertisch", "brewing_stand": "Braustand", "cauldron": "Kessel",
    "barrel": "Fass", "chest": "Truhe", "trapped_chest": "Redstone-Truhe", "ender_chest": "Endertruhe", "shulker_box": "Shulker-Kiste",
    "loom": "Webstuhl", "cartography_table": "Kartentisch", "fletching_table": "Bognertisch", "smithing_table": "Schmiedetisch",
    "grindstone": "Schleifstein", "stonecutter": "Steinsäge", "composter": "Komposter", "lectern": "Lesepult", "bell": "Glocke",
    "beacon": "Leuchtfeuer", "conduit": "Aquisator", "end_rod": "Endstab", "painting": "Gemälde", "item_frame": "Rahmen",
    "glow_item_frame": "Leuchtrahmen", "armor_stand": "Rüstungsständer", "decorated_pot": "Verzierter Krug", "candle": "Kerze",
    "lodestone": "Leitstein", "respawn_anchor": "Seelenanker", "spawner": "Spawner", "sculk": "Sculk", "shroomlight": "Pilzlicht",
    "ochre_froglight": "Ockergelbes Froschlicht", "verdant_froglight": "Junigrünes Froschlicht", "pearlescent_froglight": "Perlmutt-Froschlicht",
    # Werkzeuge, Rüstung, Sonstiges
    "stick": "Stock", "bowl": "Schüssel", "bucket": "Eimer", "water_bucket": "Wassereimer", "lava_bucket": "Lavaeimer",
    "powder_snow_bucket": "Pulverschneeeimer", "shears": "Schere", "flint_and_steel": "Feuerzeug", "flint": "Feuerstein",
    "fishing_rod": "Angel", "compass": "Kompass", "clock": "Uhr", "map": "Leere Karte", "filled_map": "Karte", "spyglass": "Fernrohr",
    "lead": "Leine", "name_tag": "Namensschild", "saddle": "Sattel", "bow": "Bogen", "crossbow": "Armbrust", "arrow": "Pfeil",
    "spectral_arrow": "Spektralpfeil", "trident": "Dreizack", "shield": "Schild", "mace": "Streitkolben", "elytra": "Elytren",
    "totem_of_undying": "Totem der Unsterblichkeit", "ender_pearl": "Enderperle", "ender_eye": "Enderauge", "blaze_rod": "Lohenrute",
    "blaze_powder": "Lohenstaub", "ghast_tear": "Ghastträne", "magma_cream": "Magmacreme", "gunpowder": "Schwarzpulver", "bone": "Knochen",
    "bone_meal": "Knochenmehl", "feather": "Feder", "leather": "Leder", "rabbit_hide": "Kaninchenfell", "rabbit_foot": "Hasenpfote",
    "ink_sac": "Tintenbeutel", "glow_ink_sac": "Leuchttintenbeutel", "paper": "Papier", "book": "Buch", "writable_book": "Buch und Feder",
    "written_book": "Beschriebenes Buch", "enchanted_book": "Verzaubertes Buch", "experience_bottle": "Erfahrungsfläschchen",
    "glass_bottle": "Glasflasche", "potion": "Trank", "nether_star": "Netherstern", "nether_wart": "Netherwarze", "honeycomb": "Honigwabe",
    "phantom_membrane": "Phantomhaut", "scute": "Hornschild", "turtle_scute": "Schildkrötenhornschild", "armadillo_scute": "Gürteltierhornschild",
    "nautilus_shell": "Nautilusschale", "heart_of_the_sea": "Herz des Meeres", "prismarine_shard": "Prismarinscherbe",
    "prismarine_crystals": "Prismarinkristalle", "shulker_shell": "Shulkerschale", "echo_shard": "Echoscherbe", "firework_rocket": "Feuerwerksrakete",
    "fire_charge": "Feuerkugel", "wind_charge": "Windkugel", "breeze_rod": "Böenrute", "trial_key": "Prüfungsschlüssel", "brush": "Pinsel",
    "goat_horn": "Bockshorn", "recovery_compass": "Bergungskompass", "player_head": "Spielerkopf",
    "skeleton_skull": "Skelettschädel", "wither_skeleton_skull": "Witherskelettschädel", "zombie_head": "Zombiekopf", "creeper_head": "Creeperkopf",
    "dragon_head": "Drachenkopf", "piglin_head": "Piglinkopf", "dragon_egg": "Drachenei", "end_crystal": "Endkristall", "bundle": "Bündel",
}
TOOLS = {"wooden": "Holz", "stone": "Stein", "iron": "Eisen", "golden": "Gold", "diamond": "Diamant", "netherite": "Netherit", "copper": "Kupfer"}
TOOL_PARTS = {"sword": "schwert", "pickaxe": "spitzhacke", "axe": "axt", "shovel": "schaufel", "hoe": "hacke"}
ARMOR_MAT = {"leather": "Leder", "chainmail": "Ketten", "iron": "Eisen", "golden": "Gold", "diamond": "Diamant", "netherite": "Netherit",
             "copper": "Kupfer", "turtle": "Schildkröten"}
ARMOR_PARTS = {"helmet": "helm", "chestplate": "harnisch", "leggings": "hose", "boots": "stiefel"}
STONE_FORMS = {"stairs": "treppe", "slab": "stufe", "wall": "mauer"}

WORDS.update({
    "amethyst_cluster": "Amethysthaufen", "small_amethyst_bud": "Kleine Amethystknospe", "medium_amethyst_bud": "Mittlere Amethystknospe",
    "large_amethyst_bud": "Große Amethystknospe", "budding_amethyst": "Knospender Amethyst", "bamboo_block": "Bambusblock",
    "stripped_bamboo_block": "Geschälter Bambusblock", "bamboo_mosaic": "Bambusmosaik", "bee_nest": "Bienennest", "beehive": "Bienenstock",
    "cobweb": "Spinnennetz", "chorus_flower": "Chorusblüte", "chorus_plant": "Choruspflanze", "dirt_path": "Trampelpfad", "farmland": "Ackerboden",
    "end_portal_frame": "Endportalrahmen", "fermented_spider_eye": "Fermentiertes Spinnenauge", "glistering_melon_slice": "Glitzernde Melonenscheibe",
    "glow_lichen": "Leuchtflechte", "heavy_core": "Schwerer Kern", "knowledge_book": "Buch des Wissens", "lingering_potion": "Verweiltrank",
    "splash_potion": "Wurftrank", "tipped_arrow": "Getränkter Pfeil", "mangrove_propagule": "Mangroven-Keimling", "mangrove_roots": "Mangrovenwurzeln",
    "muddy_mangrove_roots": "Schlammige Mangrovenwurzeln", "mushroom_stem": "Pilzstiel", "red_mushroom_block": "Roter Pilzblock",
    "brown_mushroom_block": "Brauner Pilzblock", "nether_brick": "Netherziegel", "nether_brick_fence": "Netherziegelzaun",
    "nether_wart_block": "Netherwarzenblock", "warped_wart_block": "Wirrwarzenblock", "ominous_bottle": "Unheilvolle Flasche",
    "ominous_trial_key": "Unheilvoller Prüfungsschlüssel", "pointed_dripstone": "Spitzer Tropfstein", "popped_chorus_fruit": "Geplatzte Chorusfrucht",
    "purpur_pillar": "Purpursäule", "quartz_pillar": "Quarzsäule", "sea_pickle": "Meeresgurke", "seagrass": "Seegras",
    "sculk_catalyst": "Sculk-Katalysator", "sculk_shrieker": "Sculk-Kreischer", "sculk_vein": "Sculk-Ader", "spore_blossom": "Sporenblüte",
    "suspicious_gravel": "Seltsamer Kies", "suspicious_sand": "Seltsamer Sand", "trial_spawner": "Prüfungs-Spawner", "turtle_egg": "Schildkrötenei",
    "sniffer_egg": "Schnüffler-Ei", "vault": "Tresor", "wither_rose": "Wither-Rose", "twisting_vines": "Zwirbelranken", "weeping_vines": "Trauerranken",
    "crimson_fungus": "Karmesinpilz", "warped_fungus": "Wirrpilz", "crimson_nylium": "Karmesin-Nyzel", "warped_nylium": "Wirr-Nyzel",
    "crimson_roots": "Karmesinwurzeln", "warped_roots": "Wirrwurzeln", "nether_sprouts": "Nethersprossen", "hanging_roots": "Hängende Wurzeln",
    "big_dripleaf": "Großes Tropfblatt", "small_dripleaf": "Kleines Tropfblatt", "azalea_leaves": "Azaleenlaub", "flowering_azalea": "Blühende Azalee",
    "flowering_azalea_leaves": "Blühendes Azaleenlaub", "gilded_blackstone": "Golddurchzogener Schwarzstein", "resin_block": "Harzblock",
    "resin_clump": "Harzklumpen", "resin_brick": "Harzziegel", "resin_bricks": "Harzziegel", "pale_moss_block": "Blasser Moosblock",
    "pale_moss_carpet": "Blasser Moosteppich", "pale_hanging_moss": "Blasses hängendes Moos", "creaking_heart": "Knarzherz",
    "leaf_litter": "Laubstreu", "wildflowers": "Wildblumen", "bush": "Busch", "firefly_bush": "Glühwürmchenbusch", "cactus_flower": "Kaktusblüte",
    "short_dry_grass": "Kurzes trockenes Gras", "tall_dry_grass": "Hohes trockenes Gras", "open_eyeblossom": "Offene Augenblüte",
    "closed_eyeblossom": "Geschlossene Augenblüte", "pitcher_plant": "Kannenpflanze", "pitcher_pod": "Kannenpflanzenkapsel",
    "torchflower_seeds": "Fackellilien-Samen", "frogspawn": "Froschlaich", "blue_egg": "Blaues Ei", "brown_egg": "Braunes Ei",
    "dried_ghast": "Ausgetrockneter Ghast", "reinforced_deepslate": "Verstärkter Tiefenschiefer", "dragon_breath": "Drachenatem",
    "carrot_on_a_stick": "Karottenrute", "warped_fungus_on_a_stick": "Wirrpilzrute", "firework_star": "Feuerwerksstern", "wolf_armor": "Wolfspanzer",
    "light": "Licht", "barrier": "Barriere", "command_block": "Befehlsblock", "chain_command_block": "Ketten-Befehlsblock",
    "repeating_command_block": "Wiederhol-Befehlsblock", "command_block_minecart": "Befehlsblocklore", "structure_block": "Konstruktionsblock",
    "structure_void": "Konstruktionsleere", "jigsaw": "Verbundblock", "debug_stick": "Debug-Stab", "disc_fragment_5": "Plattenbruchstück",
    "iron_chain": "Eisenkette", "tuff_bricks": "Tuffsteinziegel", "polished_blackstone_bricks": "Polierte Schwarzsteinziegel",
    "copper_nugget": "Kupferklumpen", "copper_torch": "Kupferfackel", "test_block": "Testblock", "test_instance_block": "Testinstanzblock",
    "petrified_oak_slab": "Versteinerte Eichenholzstufe", "air": "Luft", "polished_blackstone_button": "Schwarzsteinknopf",
    "polished_blackstone_pressure_plate": "Schwarzsteindruckplatte",
})
CORALS = {"tube": "Orgel", "brain": "Hirn", "bubble": "Blasen", "fire": "Feuer", "horn": "Geweih"}
CORAL_PARTS = {"coral": "koralle", "coral_block": "korallenblock", "coral_fan": "korallenfächer"}
PREFIX_ADJ = {"chiseled": "Gemeißelt", "cracked": "Rissig", "polished": "Poliert", "smooth": "Glatt", "cut": "Geschnitten",
              "infested": "Befallen", "waxed": "Gewachst", "exposed": "Angelaufen", "weathered": "Verwittert", "oxidized": "Oxidiert",
              "dead": "Abgestorben"}
# Kupfer-Teile mit Geschlecht
COPPER = {"copper": ("Kupfer", "m"), "copper_block": ("Kupferblock", "m"), "copper_bars": ("Kupfergitter", "n"),
          "copper_bulb": ("Kupferleuchte", "f"), "copper_chain": ("Kupferkette", "f"), "copper_chest": ("Kupfertruhe", "f"),
          "copper_door": ("Kupfertür", "f"), "copper_trapdoor": ("Kupferfalltür", "f"), "copper_grate": ("Kupferrost", "m"),
          "copper_lantern": ("Kupferlaterne", "f"), "copper_golem_statue": ("Kupfergolem-Statue", "f"), "lightning_rod": ("Blitzableiter", "m"),
          "chiseled_copper": ("Gemeißelter Kupfer", "m"), "cut_copper": ("Geschnittener Kupfer", "m"),
          "cut_copper_slab": ("Geschnittene Kupferstufe", "f"), "cut_copper_stairs": ("Geschnittene Kupfertreppe", "f")}
MOB_BUCKET = {"axolotl": "Axolotl", "cod": "Kabeljau", "salmon": "Lachs", "pufferfish": "Kugelfisch", "tropical_fish": "Tropenfisch",
              "tadpole": "Kaulquappe"}
GEAR_MAT = {"wooden": "Holz", "stone": "Stein", "iron": "Eisen", "golden": "Gold", "diamond": "Diamant", "netherite": "Netherit",
            "copper": "Kupfer", "leather": "Leder"}


def gender_adj(adj, gender):
    return adj + {"f": "e", "m": "er", "n": "es", "p": "e"}[gender]


def guess_gender(noun):
    low = noun.lower()
    if low.endswith(("ziegel", "wurzeln", "ranken", "steine", "blumen")):
        return "p"
    if low.endswith(("e", "tür", "kette", "heit", "keit", "ung", "laterne", "säule", "stufe", "treppe", "mauer")):
        return "f"
    if low.endswith(("glas", "gitter", "holz", "laub", "moos", "mosaik", "bett")):
        return "n"
    return "m"


def lower_first_adj(name):
    parts = name.split(" ")
    if len(parts) > 1 and parts[0][-2:] in ("er", "es") or len(parts) > 1 and parts[0].endswith("e"):
        return parts[0][0].lower() + parts[0][1:] + " " + " ".join(parts[1:])
    return name


def title(word):
    return " ".join(w.capitalize() for w in word.split("_"))


def color_name(color, noun, gender):
    adj = COLORS[color]
    if adj in INVARIANT:
        return adj + " " + noun
    return adj + ENDING[gender] + " " + noun


def name_of(key):
    if key in WORDS:
        return WORDS[key]
    # Holz
    for wood in sorted(WOODS, key=len, reverse=True):
        if key.startswith(wood + "_"):
            rest = key[len(wood) + 1:]
            for part, de, wants_holz in WOOD_PARTS:
                if rest == part:
                    holz = "holz" if wants_holz and wood not in ("bamboo", "crimson", "warped") else ""
                    return WOODS[wood] + holz + de
    if key.startswith("stripped_"):
        base = name_of(key[len("stripped_"):])
        return "Entrindeter " + base if base else None
    # Farben
    for color in sorted(COLORS, key=len, reverse=True):
        if key.startswith(color + "_"):
            rest = key[len(color) + 1:]
            for part, noun, g in COLOR_PARTS:
                if rest == part:
                    return color_name(color, noun, g)
    # Werkzeuge und Rüstung
    for mat, de in TOOLS.items():
        for part, dep in TOOL_PARTS.items():
            if key == mat + "_" + part:
                return de + dep
    for mat, de in ARMOR_MAT.items():
        for part, dep in ARMOR_PARTS.items():
            if key == mat + "_" + part:
                return de + dep
    # Stein-Treppen, -Stufen, -Mauern
    for suffix, de in STONE_FORMS.items():
        if key.endswith("_" + suffix):
            base = key[: -len(suffix) - 1]
            for cand in (base, base + "s", base + "_block"):
                word = WORDS.get(cand) or (name_of(cand) if cand != key else None)
                if word:
                    if word.endswith("steine"):
                        word = word[:-1]
                    parts = word.split(" ")
                    for i in range(len(parts) - 1):   # „Glatter Sandstein“ -> „Glatte Sandsteinstufe“
                        if parts[i][-2:] in ("er", "es"):
                            parts[i] = parts[i][:-2] + "e"
                    word = " ".join(parts)
                    return word + de
    # Spawn-Eier
    if key.endswith("_spawn_egg"):
        return "Spawn-Ei: " + title(key[:-10])
    if key.endswith("_ore") and key.startswith("deepslate_"):
        base = name_of(key[len("deepslate_"):])
        return "Tiefenschiefer-" + base if base else None
    # Kupfer (mit Wachs und Patina)
    k = key
    adjs = []
    while True:
        for pre in ("waxed", "exposed", "weathered", "oxidized"):
            if k.startswith(pre + "_"):
                adjs.append(PREFIX_ADJ[pre])
                k = k[len(pre) + 1:]
                break
        else:
            break
    if k in COPPER and (adjs or k in COPPER):
        noun, g = COPPER[k]
        if not adjs:
            return noun
        words = [gender_adj(a, g) for a in adjs]
        words = [words[0]] + [w.lower() for w in words[1:]]
        if noun.split(" ")[0][-2:] in ("er", "es") or noun.split(" ")[0].endswith("e"):
            noun = noun[0].lower() + noun[1:]
        return " ".join(words) + " " + noun
    # Korallen
    dead = key.startswith("dead_")
    k = key[5:] if dead else key
    for c, de in CORALS.items():
        for part, dep in CORAL_PARTS.items():
            if k == c + "_" + part:
                n = de + dep
                return (gender_adj("Abgestorben", guess_gender(n)) + " " + n) if dead else n
    # Vorlagen, Scherben, Platten, Eimer, Rüstung für Tiere, Speere
    if key.endswith("_pottery_sherd"):
        return "Töpferscherbe (" + title(key[:-14]) + ")"
    if key.endswith("_armor_trim_smithing_template"):
        return "Schmiedevorlage (" + title(key[:-29]) + ")"
    if key == "netherite_upgrade_smithing_template":
        return "Schmiedevorlage (Netherit)"
    if key.endswith("_banner_pattern"):
        return "Bannervorlage (" + title(key[:-15]) + ")"
    if key.startswith("music_disc_"):
        return "Schallplatte (" + title(key[11:]) + ")"
    if key.endswith("_bucket") and key[:-7] in MOB_BUCKET:
        return "Eimer mit " + MOB_BUCKET[key[:-7]]
    for mat, de in GEAR_MAT.items():
        if key == mat + "_horse_armor":
            return de + "-Rossharnisch"
        if key == mat + "_nautilus_armor":
            return de + "-Nautilusrüstung"
        if key == mat + "_spear":
            return de + "speer"
    # Vorsilben: gemeißelt, rissig, poliert, glatt, geschnitten, befallen
    for pre in ("chiseled", "cracked", "polished", "smooth", "cut", "infested"):
        if key.startswith(pre + "_"):
            base = name_of(key[len(pre) + 1:])
            if base:
                return gender_adj(PREFIX_ADJ[pre], guess_gender(base.split(" ")[-1])) + " " + lower_first_adj(base)
    return None


def main():
    if not VAN:
        sys.exit("KUBY_VANILLA fehlt")
    names = sorted(f[:-5] for f in os.listdir(os.path.join(VAN, "assets/minecraft/items")) if f.endswith(".json"))
    hit = 0
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("# MATERIAL\tdeutscher Name (tools/build_item_names.py; ohne Eintrag: Name aus der ID)\n")
        for n in names:
            de = name_of(n)
            if de:
                hit += 1
                f.write("%s\t%s\n" % (n.upper(), de))
    print("ok Namen:", hit, "von", len(names))


if __name__ == "__main__":
    main()
