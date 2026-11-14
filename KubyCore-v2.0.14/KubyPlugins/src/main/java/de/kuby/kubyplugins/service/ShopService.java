package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shop-Sortiment (im Editor bearbeitbar) und der Wochenende-Shop: jeder Spieler bekommt eigene zufällige Angebote. */
public final class ShopService {

    /** special: OP-Werkzeug aus SpecialTools (z. B. "bohrer3"), sonst null = normales Item. */
    public record Item(String id, Material material, int amount, long price, String name, List<String> lore, String special) {
        public Item(String id, Material material, int amount, long price, String name, List<String> lore) {
            this(id, material, amount, price, name, lore, null);
        }
    }

    /** Stand der eingebauten Angebote: höher als in shop.yml = fehlende Standard-Items werden nachgetragen. */
    private static final int DEFAULTS_VERSION = 2;

    public static final String WEEKEND = "weekend";
    public static final String DAILY = "daily";
    public static final List<String> SECTIONS = List.of("blocks", "food", "redstone", "deco", WEEKEND, DAILY);
    public static final Map<String, String> TITLES = Map.of(
            "blocks", "Blöcke", "food", "Essen", "redstone", "Redstone", "deco", "Dekoration", WEEKEND, "Wochenende", DAILY, "Daily Shop");

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final String[][] DEFAULTS = {
            {"blocks", "COBBLESTONE", "64", "128"}, {"blocks", "OAK_PLANKS", "64", "300"},
            {"blocks", "GLASS", "64", "500"}, {"blocks", "STONE_BRICKS", "64", "640"},
            {"food", "BREAD", "16", "160"}, {"food", "COOKED_BEEF", "16", "480"},
            {"food", "GOLDEN_CARROT", "8", "400"}, {"food", "APPLE", "16", "240"},
            {"redstone", "REDSTONE", "64", "200"}, {"redstone", "REPEATER", "8", "160"},
            {"redstone", "PISTON", "4", "280"}, {"redstone", "OBSERVER", "4", "360"}, {"redstone", "HOPPER", "2", "400"},
            {"deco", "FLOWER_POT", "4", "100"}, {"deco", "PAINTING", "2", "80"},
            {"deco", "LANTERN", "4", "200"}, {"deco", "CANDLE", "8", "240"},
            {WEEKEND, "DIAMOND_BLOCK", "1", "5000"}, {WEEKEND, "NETHERITE_SCRAP", "2", "9000"},
            {WEEKEND, "ENCHANTED_GOLDEN_APPLE", "1", "15000"}, {WEEKEND, "TNT", "16", "800"},
            {WEEKEND, "EMERALD_BLOCK", "1", "4000"}, {WEEKEND, "ANVIL", "1", "2000"},
            {WEEKEND, "TOTEM_OF_UNDYING", "1", "12000"}, {WEEKEND, "ENDER_PEARL", "16", "1200"},
            {DAILY, "GOLDEN_APPLE", "1", "30"}, {DAILY, "DIAMOND", "3", "40"},
            {DAILY, "EMERALD_BLOCK", "2", "35"}, {DAILY, "NETHERITE_INGOT", "1", "80"},
            {DAILY, "SADDLE", "1", "20"}, {DAILY, "NAME_TAG", "1", "10"},
            {DAILY, "SHULKER_SHELL", "2", "25"}, {DAILY, "BEACON", "1", "100"}
    };

    /** Bau-Blöcke (je 64, Preis pro Stapel): Holz, Stein, Ziegel, Beton, Terrakotta, Wolle, Glas, Natur. */
    private static final String[] BLOCKS = {
            // Holz
            "OAK_PLANKS:300", "SPRUCE_PLANKS:300", "BIRCH_PLANKS:300", "JUNGLE_PLANKS:300", "ACACIA_PLANKS:300",
            "DARK_OAK_PLANKS:300", "MANGROVE_PLANKS:320", "CHERRY_PLANKS:360", "PALE_OAK_PLANKS:360", "BAMBOO_PLANKS:320",
            "CRIMSON_PLANKS:420", "WARPED_PLANKS:420", "OAK_LOG:520", "SPRUCE_LOG:520", "BIRCH_LOG:520", "JUNGLE_LOG:520",
            "ACACIA_LOG:520", "DARK_OAK_LOG:520", "MANGROVE_LOG:560", "CHERRY_LOG:600", "PALE_OAK_LOG:600",
            "STRIPPED_OAK_LOG:560", "STRIPPED_SPRUCE_LOG:560", "STRIPPED_BIRCH_LOG:560", "STRIPPED_DARK_OAK_LOG:560",
            "STRIPPED_CHERRY_LOG:640", "BAMBOO_MOSAIC:360",
            // Stein
            "STONE:200", "SMOOTH_STONE:260", "STONE_BRICKS:640", "MOSSY_STONE_BRICKS:720", "CRACKED_STONE_BRICKS:680",
            "CHISELED_STONE_BRICKS:720", "MOSSY_COBBLESTONE:320", "ANDESITE:200", "POLISHED_ANDESITE:240", "DIORITE:200",
            "POLISHED_DIORITE:240", "GRANITE:200", "POLISHED_GRANITE:240", "DEEPSLATE:240", "COBBLED_DEEPSLATE:200",
            "POLISHED_DEEPSLATE:300", "DEEPSLATE_BRICKS:420", "DEEPSLATE_TILES:420", "CHISELED_DEEPSLATE:480",
            "TUFF:220", "POLISHED_TUFF:280", "TUFF_BRICKS:360", "CHISELED_TUFF:400", "CALCITE:280", "DRIPSTONE_BLOCK:300",
            "BLACKSTONE:300", "POLISHED_BLACKSTONE:360", "POLISHED_BLACKSTONE_BRICKS:420", "CHISELED_POLISHED_BLACKSTONE:480",
            "BASALT:300", "SMOOTH_BASALT:340", "POLISHED_BASALT:340",
            // Sand, Ziegel, Quarz, Prismarin, End
            "SAND:120", "RED_SAND:160", "SANDSTONE:300", "SMOOTH_SANDSTONE:340", "CUT_SANDSTONE:340", "CHISELED_SANDSTONE:380",
            "RED_SANDSTONE:340", "SMOOTH_RED_SANDSTONE:380", "CUT_RED_SANDSTONE:380", "BRICKS:640", "MUD_BRICKS:480",
            "PACKED_MUD:360", "NETHER_BRICKS:520", "RED_NETHER_BRICKS:600", "CHISELED_NETHER_BRICKS:640", "QUARTZ_BLOCK:1200",
            "SMOOTH_QUARTZ:1300", "QUARTZ_BRICKS:1300", "QUARTZ_PILLAR:1300", "CHISELED_QUARTZ_BLOCK:1400", "PRISMARINE:900",
            "PRISMARINE_BRICKS:1100", "DARK_PRISMARINE:1200", "END_STONE:400", "END_STONE_BRICKS:520", "PURPUR_BLOCK:800",
            "PURPUR_PILLAR:860",
            // Kupfer
            "COPPER_BLOCK:1800", "CUT_COPPER:1800", "EXPOSED_COPPER:1800", "WEATHERED_COPPER:1800", "OXIDIZED_COPPER:1800",
            "WAXED_COPPER_BLOCK:2000", "WAXED_CUT_COPPER:2000", "CHISELED_COPPER:2000", "COPPER_GRATE:2000",
            // Beton, Pulver, Terrakotta, Wolle, Glas in allen Farben
            "COLOR:WHITE", "COLOR:LIGHT_GRAY", "COLOR:GRAY", "COLOR:BLACK", "COLOR:BROWN", "COLOR:RED", "COLOR:ORANGE",
            "COLOR:YELLOW", "COLOR:LIME", "COLOR:GREEN", "COLOR:CYAN", "COLOR:LIGHT_BLUE", "COLOR:BLUE", "COLOR:PURPLE",
            "COLOR:MAGENTA", "COLOR:PINK",
            "TERRACOTTA:320", "GLASS:500", "TINTED_GLASS:900", "GLASS_PANE:220",
            // Natur
            "DIRT:80", "COARSE_DIRT:100", "ROOTED_DIRT:120", "GRASS_BLOCK:200", "PODZOL:180", "MYCELIUM:260", "MUD:120",
            "CLAY:300", "GRAVEL:100", "MOSS_BLOCK:260", "PALE_MOSS_BLOCK:300", "SNOW_BLOCK:200", "PACKED_ICE:400",
            "BLUE_ICE:900", "OBSIDIAN:1600", "CRYING_OBSIDIAN:1800", "NETHERRACK:80", "SOUL_SAND:200", "SOUL_SOIL:200",
            "GLOWSTONE:800", "SEA_LANTERN:1000", "SHROOMLIGHT:900", "OCHRE_FROGLIGHT:1100", "VERDANT_FROGLIGHT:1100",
            "PEARLESCENT_FROGLIGHT:1100", "HAY_BLOCK:400", "HONEYCOMB_BLOCK:700", "BONE_BLOCK:500", "AMETHYST_BLOCK:900",
            "RESIN_BRICKS:700"
    };

    /** Deko (Anzahl:Preis): Blumen, Pflanzen, Licht, Kerzen, Möbel-Blöcke. */
    private static final String[] DECO = {
            // Blumen
            "DANDELION:8:120", "POPPY:8:120", "BLUE_ORCHID:8:160", "ALLIUM:8:160", "AZURE_BLUET:8:140", "RED_TULIP:8:140",
            "ORANGE_TULIP:8:140", "WHITE_TULIP:8:140", "PINK_TULIP:8:140", "OXEYE_DAISY:8:140", "CORNFLOWER:8:150",
            "LILY_OF_THE_VALLEY:8:160", "WITHER_ROSE:4:400", "TORCHFLOWER:4:300", "PINK_PETALS:16:200", "WILDFLOWERS:16:220",
            "SUNFLOWER:4:200", "LILAC:4:200", "ROSE_BUSH:4:200", "PEONY:4:200", "PITCHER_PLANT:4:320", "SPORE_BLOSSOM:2:500",
            "FLOWERING_AZALEA:4:300", "AZALEA:4:240", "CACTUS_FLOWER:8:220", "OPEN_EYEBLOSSOM:8:260", "CLOSED_EYEBLOSSOM:8:260",
            // Pflanzen
            "FERN:16:120", "LARGE_FERN:8:160", "SHORT_GRASS:16:80", "TALL_GRASS:8:120", "DEAD_BUSH:8:80", "BUSH:8:120",
            "FIREFLY_BUSH:4:300", "LILY_PAD:16:160", "VINE:16:120", "GLOW_LICHEN:16:160", "HANGING_ROOTS:16:120",
            "MOSS_CARPET:16:140", "PALE_MOSS_CARPET:16:160", "PALE_HANGING_MOSS:16:180", "BIG_DRIPLEAF:4:240",
            "SMALL_DRIPLEAF:4:200", "SEA_PICKLE:8:200", "KELP:16:100", "SEAGRASS:16:100", "BAMBOO:16:120", "SUGAR_CANE:16:120",
            "CACTUS:8:160", "SWEET_BERRIES:16:120", "GLOW_BERRIES:16:180", "BROWN_MUSHROOM:8:120", "RED_MUSHROOM:8:120",
            "CRIMSON_FUNGUS:8:160", "WARPED_FUNGUS:8:160", "CRIMSON_ROOTS:8:160", "WARPED_ROOTS:8:160", "NETHER_SPROUTS:16:160",
            "TWISTING_VINES:16:200", "WEEPING_VINES:16:200", "OAK_LEAVES:32:160", "SPRUCE_LEAVES:32:160", "BIRCH_LEAVES:32:160",
            "JUNGLE_LEAVES:32:160", "ACACIA_LEAVES:32:160", "DARK_OAK_LEAVES:32:160", "MANGROVE_LEAVES:32:180",
            "CHERRY_LEAVES:32:220", "PALE_OAK_LEAVES:32:220", "AZALEA_LEAVES:32:200", "FLOWERING_AZALEA_LEAVES:32:240",
            "OAK_SAPLING:4:120", "SPRUCE_SAPLING:4:120", "BIRCH_SAPLING:4:120", "JUNGLE_SAPLING:4:140", "CHERRY_SAPLING:4:200",
            "PALE_OAK_SAPLING:4:200", "TUBE_CORAL_BLOCK:8:400", "BRAIN_CORAL_BLOCK:8:400", "BUBBLE_CORAL_BLOCK:8:400",
            "FIRE_CORAL_BLOCK:8:400", "HORN_CORAL_BLOCK:8:400",
            // Licht
            "TORCH:32:120", "SOUL_TORCH:32:160", "LANTERN:4:200", "SOUL_LANTERN:4:240", "END_ROD:8:400", "CAMPFIRE:2:200",
            "SOUL_CAMPFIRE:2:240", "REDSTONE_LAMP:4:320", "IRON_CHAIN:16:240", "COPPER_BULB:4:600", "LIGHTNING_ROD:4:400",
            "CANDLE:8:240", "WHITE_CANDLE:8:260", "RED_CANDLE:8:260", "ORANGE_CANDLE:8:260", "YELLOW_CANDLE:8:260",
            "LIME_CANDLE:8:260", "LIGHT_BLUE_CANDLE:8:260", "PURPLE_CANDLE:8:260", "PINK_CANDLE:8:260", "BLACK_CANDLE:8:260",
            // Möbel-Blöcke und Deko
            "FLOWER_POT:4:100", "DECORATED_POT:2:300", "PAINTING:2:80", "ITEM_FRAME:4:160", "GLOW_ITEM_FRAME:4:240",
            "ARMOR_STAND:2:300", "BOOKSHELF:8:640", "CHISELED_BOOKSHELF:4:600", "LECTERN:2:300", "BARREL:4:240",
            "CHEST:4:200", "CRAFTING_TABLE:4:160", "LOOM:2:200", "CARTOGRAPHY_TABLE:2:240", "SMOKER:2:300", "BLAST_FURNACE:2:500",
            "ANVIL:1:2000", "GRINDSTONE:2:300", "STONECUTTER:2:300", "BELL:1:800", "CAULDRON:2:400", "COMPOSTER:4:200",
            "BEEHIVE:2:500", "SCAFFOLDING:32:200", "LADDER:16:120", "IRON_BARS:16:320", "OAK_FENCE:16:160",
            "SPRUCE_FENCE:16:160", "DARK_OAK_FENCE:16:160", "CHERRY_FENCE:16:200", "OAK_TRAPDOOR:8:200",
            "SPRUCE_TRAPDOOR:8:200", "IRON_TRAPDOOR:4:400", "COPPER_TRAPDOOR:4:500", "OAK_DOOR:4:200", "SPRUCE_DOOR:4:200",
            "IRON_DOOR:2:400", "WHITE_BED:1:300", "RED_BED:1:300", "BLUE_BED:1:300", "WHITE_BANNER:2:300", "RED_BANNER:2:300",
            "BLUE_BANNER:2:300", "BLACK_BANNER:2:300", "OAK_SIGN:8:200", "OAK_HANGING_SIGN:8:240", "CHERRY_HANGING_SIGN:8:260",
            "SKELETON_SKULL:1:1500", "CREEPER_HEAD:1:2500", "ZOMBIE_HEAD:1:2000", "POINTED_DRIPSTONE:8:240",
            "AMETHYST_CLUSTER:4:600", "SHULKER_BOX:1:6000", "JUKEBOX:1:1500", "NOTE_BLOCK:4:400", "CRAFTER:1:1200",
            "HEAVY_CORE:1:25000", "CREAKING_HEART:1:3000", "VAULT:1:20000"
    };

    /** OP-Werkzeuge und seltene Items. Wochenende in $, Daily in Juwelen: Art;Wochenend-Preis;Daily-Preis. */
    private static final String[] SPECIALS = {
            "bohrer3;250000;80", "bohrer5;900000;220", "sellaxt_eisen;150000;40", "sellaxt_gold;350000;80",
            "sellaxt_diamant;750000;160", "sellaxt_netherit;1500000;320", "holzfaeller;120000;35", "waldfresser;400000;100",
            "schmelzhacke;200000;60", "erntesense;150000;50", "vampirklinge;500000;140", "donnerklinge;600000;160",
            "enterhaken;80000;30", "buch_reparatur;120000;45", "buch_effizienz;90000;35"
    };

    /** Seltene Vanilla-Items für beide Angebote: Material;Anzahl;Wochenend-Preis;Daily-Preis. */
    private static final String[] RARES = {
            "ELYTRA;1;600000;250", "TRIDENT;1;180000;70", "MACE;1;450000;180", "NETHERITE_UPGRADE_SMITHING_TEMPLATE;1;90000;40",
            "NETHER_STAR;1;300000;120", "DRAGON_HEAD;1;150000;60", "SNIFFER_EGG;1;60000;25", "OMINOUS_TRIAL_KEY;2;80000;30",
            "EXPERIENCE_BOTTLE;64;40000;20", "NETHERITE_BLOCK;1;400000;160", "TOTEM_OF_UNDYING;2;30000;25"
    };

    /** Alle eingebauten Angebote: [Sektion, Material, Anzahl, Preis, special oder null]. */
    private static List<String[]> defaults() {
        List<String[]> out = new ArrayList<>();
        for (String[] d : DEFAULTS) out.add(new String[] {d[0], d[1], d[2], d[3], null});
        for (String b : BLOCKS) {
            String[] p = b.split(":");
            if (p[0].equals("COLOR")) {
                String c = p[1];
                out.add(new String[] {"blocks", c + "_CONCRETE", "64", "420", null});
                out.add(new String[] {"blocks", c + "_CONCRETE_POWDER", "64", "300", null});
                out.add(new String[] {"blocks", c + "_TERRACOTTA", "64", "360", null});
                out.add(new String[] {"blocks", c + "_GLAZED_TERRACOTTA", "64", "480", null});
                out.add(new String[] {"blocks", c + "_WOOL", "64", "380", null});
                out.add(new String[] {"blocks", c + "_STAINED_GLASS", "64", "560", null});
                out.add(new String[] {"deco", c + "_CARPET", "32", "200", null});
                continue;
            }
            out.add(new String[] {"blocks", p[0], "64", p[1], null});
        }
        for (String b : DECO) {
            String[] p = b.split(":");
            out.add(new String[] {"deco", p[0], p[1], p[2], null});
        }
        for (String s : SPECIALS) {
            String[] p = s.split(";");
            de.kuby.kubyplugins.tools.SpecialTools.Def d = de.kuby.kubyplugins.tools.SpecialTools.def(p[0]);
            if (d == null) continue;
            out.add(new String[] {WEEKEND, d.material().name(), "1", p[1], p[0]});
            out.add(new String[] {DAILY, d.material().name(), "1", p[2], p[0]});
        }
        for (String s : RARES) {
            String[] p = s.split(";");
            out.add(new String[] {WEEKEND, p[0], p[1], p[2], null});
            out.add(new String[] {DAILY, p[0], p[1], p[3], null});
        }
        return out;
    }

    private static Item fromDefault(String[] d) {
        Material m = Material.matchMaterial(d[1]);
        if (m == null || !m.isItem()) return null;
        if (d[4] != null) {
            de.kuby.kubyplugins.tools.SpecialTools.Def t = de.kuby.kubyplugins.tools.SpecialTools.def(d[4]);
            return new Item(newId(), m, 1, Long.parseLong(d[3]), "<" + t.color() + "><b>" + t.name(), t.lore(), d[4]);
        }
        return new Item(newId(), m, Integer.parseInt(d[2]), Long.parseLong(d[3]), null, List.of());
    }

    /** Fehlende eingebaute Angebote nachtragen (gleiche Sektion, gleiches Material bzw. gleiches Werkzeug = vorhanden). */
    private boolean addMissingDefaults() {
        boolean changed = false;
        for (String[] d : defaults()) {
            List<Item> list = sections.get(d[0]);
            boolean has = false;
            for (Item it : list) {
                if (d[4] != null ? d[4].equals(it.special()) : it.special() == null && it.material().name().equals(d[1])) {
                    has = true;
                    break;
                }
            }
            if (has) continue;
            Item it = fromDefault(d);
            if (it == null) continue;
            list.add(it);
            changed = true;
        }
        return changed;
    }

    private static final class Weekend {
        String key = "";
        final List<String> picks = new ArrayList<>();
        final Set<String> bought = new HashSet<>();
        int rerolls;
    }

    private final KubyPlugins plugin;
    private final File file;
    private final File weekendFile;
    private final File dailyFile;
    private final Map<String, List<Item>> sections = new LinkedHashMap<>();
    private final Map<UUID, Weekend> weekend = new HashMap<>();
    private String dailyDay = "";
    private final List<String> dailyPicks = new ArrayList<>();
    private final Map<UUID, Set<String>> dailyBought = new HashMap<>();

    public ShopService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop.yml");
        this.weekendFile = new File(plugin.getDataFolder(), "weekend.yml");
        this.dailyFile = new File(plugin.getDataFolder(), "dailyshop.yml");
        for (String s : SECTIONS) sections.put(s, new ArrayList<>());
        if (file.exists()) {
            load();
            // ältere shop.yml: neue Standard-Angebote (Bau-Blöcke, Deko, Werkzeuge) einmalig nachtragen
            if (YamlConfiguration.loadConfiguration(file).getInt("defaults-version", 1) < DEFAULTS_VERSION) {
                addMissingDefaults();
                save();
            }
        } else {
            addMissingDefaults();
            save();
        }
        loadWeekend();
        loadDaily();
        if (sections.get(DAILY).isEmpty()) {
            for (String[] d : DEFAULTS) {
                if (!DAILY.equals(d[0])) continue;
                Material m = Material.matchMaterial(d[1]);
                if (m != null) sections.get(DAILY).add(new Item(newId(), m, Integer.parseInt(d[2]), Long.parseLong(d[3]), null, List.of()));
            }
            save();
        }
    }

    public static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String sec : SECTIONS) {
            ConfigurationSection cs = yml.getConfigurationSection(sec);
            if (cs == null) continue;
            for (String id : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(id);
                if (s == null) continue;
                Material m = Material.matchMaterial(s.getString("material", ""));
                if (m == null) continue;
                sections.get(sec).add(new Item(id, m, s.getInt("amount", 1), s.getLong("price", 0),
                        s.getString("name"), s.getStringList("lore"), s.getString("special")));
            }
        }
    }

    public synchronized List<Item> items(String sec) {
        return new ArrayList<>(sections.getOrDefault(sec, List.of()));
    }

    public synchronized Item byId(String id) {
        for (String s : SECTIONS) {
            for (Item it : sections.get(s)) {
                if (it.id().equals(id)) return it;
            }
        }
        return null;
    }

    public synchronized String sectionOf(String id) {
        for (String s : SECTIONS) {
            for (Item it : sections.get(s)) {
                if (it.id().equals(id)) return s;
            }
        }
        return null;
    }

    public synchronized void add(String sec, Item it) {
        sections.get(sec).add(it);
        save();
    }

    public synchronized void replace(String sec, Item it) {
        List<Item> list = sections.get(sec);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(it.id())) {
                // Bearbeiten im Admin-Menü behält die Werkzeug-Art
                Item old = list.get(i);
                list.set(i, it.special() == null && old.special() != null
                        ? new Item(it.id(), it.material(), it.amount(), it.price(), it.name(), it.lore(), old.special()) : it);
                break;
            }
        }
        save();
    }

    public synchronized void remove(String id) {
        for (String s : SECTIONS) {
            if (sections.get(s).removeIf(it -> it.id().equals(id))) break;
        }
        save();
    }

    /** Laufende Nummer über alle Sektionen außer dem Wochenende. 0 = nicht gefunden. */
    public synchronized int slotNumber(String id) {
        int n = 0;
        for (String s : SECTIONS) {
            if (WEEKEND.equals(s) || DAILY.equals(s)) continue;
            for (Item it : sections.get(s)) {
                n++;
                if (it.id().equals(id)) return n;
            }
        }
        return 0;
    }

    /** Suche nach Nummer, ID, Material oder Name. */
    public synchronized List<Item> search(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Item> out = new ArrayList<>();
        try {
            int n = Integer.parseInt(q);
            for (String s : SECTIONS) {
                if (WEEKEND.equals(s) || DAILY.equals(s)) continue;
                for (Item it : sections.get(s)) {
                    if (slotNumber(it.id()) == n) out.add(it);
                }
            }
            return out;
        } catch (NumberFormatException ignored) {
            // Kein Zahlenwert, weiter mit Textsuche
        }
        String mat = q.replace(' ', '_');
        for (String s : SECTIONS) {
            for (Item it : sections.get(s)) {
                String name = it.name() == null ? "" : it.name().toLowerCase(Locale.ROOT);
                if (it.material().name().toLowerCase(Locale.ROOT).contains(mat)
                        || it.id().equalsIgnoreCase(q) || name.contains(q)) {
                    out.add(it);
                }
            }
        }
        return out;
    }

    // ---------- Wochenende ----------

    /** Schlüssel des aktuellen Wochenendes: das letzte Samstag-Datum. */
    public static String weekendKey() {
        LocalDate d = LocalDate.now(ZONE);
        int back = (d.getDayOfWeek().getValue() - DayOfWeek.SATURDAY.getValue() + 7) % 7;
        return d.minusDays(back).toString();
    }

    public int count() { return Math.max(1, plugin.getConfig().getInt("weekend.count", 4)); }
    public long rerollCost() { return plugin.getConfig().getLong("weekend.reroll-jewels", 50); }
    public int maxRerolls() { return plugin.getConfig().getInt("weekend.max-rerolls", 3); }

    private Weekend weekendOf(UUID id) {
        return weekend.computeIfAbsent(id, k -> new Weekend());
    }

    /** Die Angebote dieses Spielers für das aktuelle Wochenende. Neu gewürfelt, sobald ein neues Wochenende beginnt. */
    public synchronized List<Item> weekendPicks(UUID id) {
        Weekend w = weekendOf(id);
        String key = weekendKey();
        if (!key.equals(w.key)) {
            w.key = key;
            w.bought.clear();
            w.rerolls = 0;
            resample(w);
            saveWeekend();
        }
        List<Item> out = new ArrayList<>();
        for (String pid : w.picks) {
            Item it = byId(pid);
            if (it != null) out.add(it);
        }
        return out;
    }

    private void resample(Weekend w) {
        List<Item> pool = new ArrayList<>(sections.get(WEEKEND));
        Collections.shuffle(pool);
        w.picks.clear();
        for (int i = 0; i < Math.min(count(), pool.size()); i++) w.picks.add(pool.get(i).id());
    }

    public synchronized int rerollsLeft(UUID id) {
        return Math.max(0, maxRerolls() - weekendOf(id).rerolls);
    }

    /** Neuwurf nur für diesen Spieler, kostet Juwelen. Gibt null bei Erfolg zurück. */
    public synchronized String reroll(Player p) {
        UUID id = p.getUniqueId();
        weekendPicks(id);
        Weekend w = weekendOf(id);
        if (w.rerolls >= maxRerolls()) return "Du hast alle Neuwürfe für dieses Wochenende verbraucht.";
        if (!plugin.jewels().take(id, rerollCost())) return "Du brauchst " + rerollCost() + " Juwelen für einen Neuwurf.";
        w.rerolls++;
        resample(w);
        saveWeekend();
        return null;
    }

    public synchronized boolean isBought(UUID id, String itemId) {
        return weekendOf(id).bought.contains(itemId);
    }

    public synchronized void markBought(UUID id, String itemId) {
        weekendOf(id).bought.add(itemId);
        saveWeekend();
    }

    private void loadWeekend() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(weekendFile);
        for (String k : yml.getKeys(false)) {
            Weekend w = new Weekend();
            w.key = yml.getString(k + ".key", "");
            w.picks.addAll(yml.getStringList(k + ".picks"));
            w.bought.addAll(yml.getStringList(k + ".bought"));
            w.rerolls = yml.getInt(k + ".rerolls", 0);
            weekend.put(UUID.fromString(k), w);
        }
    }

    private synchronized void saveWeekend() {
        YamlConfiguration out = new YamlConfiguration();
        weekend.forEach((id, w) -> {
            String k = id.toString();
            out.set(k + ".key", w.key);
            out.set(k + ".picks", new ArrayList<>(w.picks));
            out.set(k + ".bought", new ArrayList<>(w.bought));
            out.set(k + ".rerolls", w.rerolls);
        });
        try {
            plugin.getDataFolder().mkdirs();
            out.save(weekendFile);
        } catch (IOException e) {
            plugin.getLogger().warning("weekend.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ---------- Daily Shop: für alle gleich, täglich neu, bezahlt mit Juwelen ----------

    public static String dailyKey() {
        return LocalDate.now(ZONE).toString();
    }

    public int dailyCount() {
        return Math.max(1, plugin.getConfig().getInt("daily-shop.count", 6));
    }

    /** Die heutigen Angebote. Werden neu gewürfelt, sobald ein neuer Tag beginnt. */
    public synchronized List<Item> dailyItems(UUID id) {
        String day = dailyKey();
        if (!day.equals(dailyDay)) {
            dailyDay = day;
            dailyPicks.clear();
            dailyPicks.addAll(sample(DAILY, dailyCount()));
            dailyBought.clear();
            saveDaily();
        }
        List<Item> out = new ArrayList<>();
        for (String pid : dailyPicks) {
            Item it = byId(pid);
            if (it != null) out.add(it);
        }
        return out;
    }

    public synchronized boolean dailyBoughtBy(UUID id, String itemId) {
        return dailyBought.getOrDefault(id, Set.of()).contains(itemId);
    }

    public synchronized void markDailyBought(UUID id, String itemId) {
        dailyBought.computeIfAbsent(id, k -> new HashSet<>()).add(itemId);
        saveDaily();
    }

    private List<String> sample(String sec, int n) {
        List<Item> pool = new ArrayList<>(sections.get(sec));
        Collections.shuffle(pool);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < Math.min(n, pool.size()); i++) ids.add(pool.get(i).id());
        return ids;
    }

    private void loadDaily() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dailyFile);
        dailyDay = yml.getString("day", "");
        dailyPicks.addAll(yml.getStringList("picks"));
        ConfigurationSection b = yml.getConfigurationSection("bought");
        if (b != null) {
            for (String k : b.getKeys(false)) dailyBought.put(UUID.fromString(k), new HashSet<>(b.getStringList(k)));
        }
    }

    private synchronized void saveDaily() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("day", dailyDay);
        out.set("picks", new ArrayList<>(dailyPicks));
        dailyBought.forEach((id, set) -> out.set("bought." + id, new ArrayList<>(set)));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(dailyFile);
        } catch (IOException e) {
            plugin.getLogger().warning("dailyshop.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (String sec : SECTIONS) {
            for (Item it : sections.get(sec)) {
                String base = sec + "." + it.id() + ".";
                out.set(base + "material", it.material().name());
                out.set(base + "amount", it.amount());
                out.set(base + "price", it.price());
                if (it.name() != null) out.set(base + "name", it.name());
                if (it.lore() != null && !it.lore().isEmpty()) out.set(base + "lore", it.lore());
                if (it.special() != null) out.set(base + "special", it.special());
            }
        }
        out.set("defaults-version", DEFAULTS_VERSION);
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("shop.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
