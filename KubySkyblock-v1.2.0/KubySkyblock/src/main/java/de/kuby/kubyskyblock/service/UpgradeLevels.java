package de.kuby.kubyskyblock.service;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Level der Skyblock-Upgrades (Insel und One-Block): Level 0 bis 50 in vier Stufen (Wunsch des Besitzers). Stufe 1 =
 * Level 1 bis 10, Stufe 2 = bis 15, Stufe 3 = bis 20 (bezahlt mit Geld bzw. Block-XP), Stufe 4 = Level 21 bis 50, nur
 * mit Juwelen. Die Icons zeigen die Stufe: ab Stufe 2 goldene, ab Stufe 3 lila, in Stufe 4 Regenbogen-Sterne.
 * <p>
 * Früher hatten die Upgrades 4 bis 10 Stufen. Diese Klasse ersetzt unveränderte alte Standard-Werte in der config durch
 * die neuen 20-Level-Listen und rechnet gespeicherte Stände so um, dass jede Insel und jedes Team genau die bisherige
 * Wirkung behält (die alten Stufen liegen in den neuen Listen auf festen Leveln).
 */
public final class UpgradeLevels {

    /** Letztes Level, das mit Geld (Insel) bzw. Block-XP (One-Block) bezahlt wird (Ende von Stufe 3). */
    public static final int BASE = 20;
    /** Höchstes Level (Ende von Stufe 4, Level über BASE nur mit Juwelen). */
    public static final int MAX = 50;
    /** Version der gespeicherten Upgrade-Stände (1 = alte Stufen, 2 = Level 0 bis 20/50). */
    public static final int VERSION = 2;

    private UpgradeLevels() {
    }

    /** Radius je Größen-Level (wie config insel.groessen): bis Level 20 wie die alten Stufen, danach +1 je Level. */
    public static final List<Integer> SIZES;
    /** Mitglieder (ohne Besitzer) je Level (wie config insel.mitglieder): 2 + Level. */
    public static final List<Integer> MEMBERS;

    static {
        List<Integer> s = new ArrayList<>(List.of(15, 17, 19, 21, 23, 25, 27, 29, 31, 33, 35, 38, 41, 44, 47, 50, 54, 58, 62, 66, 70));
        for (int l = BASE + 1; l <= MAX; l++) s.add(70 + l - BASE);
        SIZES = List.copyOf(s);
        List<Integer> m = new ArrayList<>();
        for (int l = 0; l <= MAX; l++) m.add(2 + l);
        MEMBERS = List.copyOf(m);
    }

    /** Ist das nächste Level eines aus Stufe 4 (nur Juwelen)? */
    public static boolean jewelLevel(int level) {
        return level > BASE;
    }

    /** Juwelen-Preis eines Levels aus Stufe 4: basis * faktor^(Level - 21), gerundet. */
    public static long jewels(int level, double basis, double faktor) {
        return Math.max(1, Math.round(basis * Math.pow(faktor, Math.max(0, level - BASE - 1))));
    }

    /** Ersatz-Preisliste, wenn die config keine hat: Level 1 kostet first, jedes weitere 22 % mehr (gerundet). */
    public static List<Long> prices(long first) {
        List<Long> out = new ArrayList<>();
        out.add(0L);
        for (int l = 1; l <= BASE; l++) {
            double v = first * Math.pow(1.22, l - 1);
            double e = Math.pow(10, Math.floor(Math.log10(v)) - 1);
            out.add(Math.round(Math.round(v / e) * e));
        }
        return out;
    }

    /** Stufe eines Levels: 1 (bis 10), 2 (bis 15), 3 (bis 20), 4 (bis 50, Juwelen). */
    public static int tier(int level) {
        return level > BASE ? 4 : level > 15 ? 3 : level > 10 ? 2 : 1;
    }

    // =================================================================================================================
    // Umrechnung alter Stände (alte Stufe -> neues Level mit derselben Wirkung)
    // =================================================================================================================

    /** Größe: alte Radien 15/25/35/50/70 liegen auf Level 0/5/10/15/20. */
    public static int islandSize(int old) {
        return clamp(old * 5);
    }

    /** Mitglieder: alte Plätze 2/4/6/10 liegen auf Level 0/2/4/8 (neu: 2 + Level). */
    public static int islandSlots(int old) {
        int[] m = {0, 2, 4, 8};
        return old < m.length ? m[Math.max(0, old)] : clamp(old * 2 + 2);
    }

    /** Generator: die alten 6 Stufen liegen auf Level 0/4/8/12/16/20. */
    public static int islandGenerator(int old) {
        return clamp(old * 4);
    }

    /** Wachstum, Spawnrate, Limits: alte Stufe x 25/20/25 % = neues Level x 5/4/5 %. */
    public static int islandBonus(int old) {
        return clamp(old * 5);
    }

    /** One-Block: Tempo hatte 5 Stufen (Eile I bis V), die anderen 10. */
    public static int oneBlock(String key, int old) {
        return clamp("tempo".equals(key) ? old * 4 : old * 2);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(MAX, v));
    }

    // =================================================================================================================
    // config: alte Standard-Werte durch die neuen ersetzen
    // =================================================================================================================

    /** Alte Standard-Listen (5 Stufen und 20 Level ohne Stufe 4), die durch die aktuellen ersetzt werden. */
    private static final List<Map.Entry<String, List<Long>>> OLD_LISTS = List.of(
            Map.entry("insel.groessen", List.of(15L, 25L, 35L, 50L, 70L)),
            Map.entry("insel.groessen", longs(SIZES.subList(0, BASE + 1))),
            Map.entry("insel.mitglieder", List.of(2L, 4L, 6L, 10L)),
            Map.entry("insel.mitglieder", longs(MEMBERS.subList(0, BASE + 1))),
            Map.entry("insel.upgrades.groesse", List.of(0L, 25_000L, 75_000L, 200_000L, 500_000L)),
            Map.entry("insel.upgrades.mitglieder", List.of(0L, 10_000L, 40_000L, 100_000L)),
            Map.entry("insel.upgrades.wachstum", List.of(0L, 15_000L, 50_000L, 150_000L, 400_000L)),
            Map.entry("insel.upgrades.spawnrate", List.of(0L, 20_000L, 60_000L, 180_000L, 450_000L)),
            Map.entry("insel.upgrades.limits", List.of(0L, 25_000L, 75_000L, 200_000L, 500_000L)));
    private static final Map<String, Double> OLD_BONUS = Map.of("wachstum.bonus-je-stufe", 0.25, "spawnrate.bonus-je-stufe", 0.2,
            "limits.bonus-je-stufe", 0.25);
    /** One-Block: Upgrade -> alte Werte (max, basis, faktor). */
    private static final Map<String, double[]> OLD_ONEBLOCK = Map.of("glueck", new double[]{10, 250, 1.55},
            "cooldown", new double[]{10, 200, 1.5}, "tempo", new double[]{5, 600, 2.2}, "xp", new double[]{10, 200, 1.5},
            "geld", new double[]{10, 200, 1.5}, "beute", new double[]{10, 300, 1.55});
    private static final List<List<Long>> OLD_GENERATOR_COSTS = List.of(List.of(0L, 5_000L, 20_000L, 60_000L, 150_000L, 400_000L),
            List.of(0L, 1500L, 1900L, 2400L, 3100L, 4000L, 5000L, 6400L, 8200L, 10_000L, 13_000L, 17_000L, 22_000L, 27_000L,
                    35_000L, 45_000L, 57_000L, 72_000L, 92_000L, 120_000L, 150_000L));
    /** One-Block-Werte der 20-Level-Version (max, basis, faktor): nur max wird auf 50 gehoben. */
    private static final Map<String, double[]> V2_ONEBLOCK = Map.of("glueck", new double[]{20, 125, 1.245},
            "cooldown", new double[]{20, 100, 1.225}, "tempo", new double[]{20, 150, 1.218}, "xp", new double[]{20, 100, 1.225},
            "geld", new double[]{20, 100, 1.225}, "beute", new double[]{20, 150, 1.245});

    private static List<Long> longs(List<Integer> l) {
        List<Long> out = new ArrayList<>();
        for (int v : l) out.add((long) v);
        return out;
    }

    /**
     * Ersetzt in der config der Server-Installation alle Upgrade-Werte, die noch genau den alten Standard haben, durch
     * die neuen aus dem Plugin (eigene Werte bleiben). Gibt die Zahl der ersetzten Einträge zurück.
     */
    public static int updateConfig(JavaPlugin plugin) {
        FileConfiguration c = plugin.getConfig();
        Configuration d = c.getDefaults();
        if (d == null) return 0;
        int n = 0;
        for (Map.Entry<String, List<Long>> e : OLD_LISTS) {
            if (c.isSet(e.getKey()) && numbers(c.getList(e.getKey())).equals(e.getValue())) {
                c.set(e.getKey(), d.getList(e.getKey()));
                n++;
            }
        }
        List<Map<?, ?>> gen = c.getMapList("generator.stufen");
        List<Long> costs = new ArrayList<>();
        for (Map<?, ?> m : gen) costs.add(m.get("kosten") instanceof Number x ? x.longValue() : -1L);
        if (OLD_GENERATOR_COSTS.contains(costs)) {
            c.set("generator.stufen", d.getList("generator.stufen"));
            n++;
        }
        for (Map.Entry<String, Double> e : OLD_BONUS.entrySet()) {
            if (c.isSet(e.getKey()) && Math.abs(c.getDouble(e.getKey()) - e.getValue()) < 1e-9) {
                c.set(e.getKey(), d.getDouble(e.getKey()));
                n++;
            }
        }
        // Juwelen-Preise der Stufe 4 nach Euro-Maßstab (1.000 Juwelen = 10 €): alte Standard-Werte (ohne je-upgrade)
        // durch den neuen Abschnitt ersetzen (Insel vorher 10 x 1,1^n bzw. 4 x 1,05^n, One-Block 8 x 1,1^n bzw. 3 x 1,05^n)
        for (String[] e : new String[][]{{"insel.juwelen-stufe", "10", "1.1"}, {"insel.juwelen-stufe", "4", "1.05"},
                {"oneblock.juwelen-stufe", "8", "1.1"}, {"oneblock.juwelen-stufe", "3", "1.05"}}) {
            String b = e[0];
            if (c.isSet(b + ".basis") && !c.isSet(b + ".je-upgrade") && d.isConfigurationSection(b)
                    && Math.abs(c.getDouble(b + ".basis") - Double.parseDouble(e[1])) < 1e-9
                    && Math.abs(c.getDouble(b + ".faktor") - Double.parseDouble(e[2])) < 1e-9) {
                c.set(b, null);
                copy(d.getConfigurationSection(b), c.createSection(b));
                n++;
            }
        }
        for (Map<String, double[]> old : List.of(OLD_ONEBLOCK, V2_ONEBLOCK)) {
            for (Map.Entry<String, double[]> e : old.entrySet()) {
                String b = "oneblock.upgrades." + e.getKey() + ".";
                double[] o = e.getValue();
                if (c.isSet(b + "max") && c.getInt(b + "max") == (int) o[0] && Math.abs(c.getDouble(b + "basis") - o[1]) < 1e-9
                        && Math.abs(c.getDouble(b + "faktor") - o[2]) < 1e-9) {
                    c.set(b + "max", d.getInt(b + "max"));
                    c.set(b + "basis", d.getDouble(b + "basis"));
                    c.set(b + "faktor", d.getDouble(b + "faktor"));
                    n++;
                }
            }
        }
        // Stufen (Nether, End): neue Abschnitte einmal in ältere configs übernehmen (config-stand 7)
        int added = 0;
        boolean stamped = false;
        if (c.getInt("config-stand", 0) < 7) {
            stamped = true;
            for (String path : List.of("stufen", "generatoren.typen.lohe", "generatoren.typen.netherit", "generatoren.typen.ender",
                    "generatoren.typen.shulker", "shop.bereiche.stufen")) {
                int dot = path.lastIndexOf('.');
                if (c.isSet(path) || !d.isConfigurationSection(path) || (dot > 0 && !c.isSet(path.substring(0, dot)))) continue;
                copy(d.getConfigurationSection(path), c.createSection(path));
                added++;
            }
            c.set("config-stand", 7);
        }
        if (n > 0 || stamped) {
            plugin.saveConfig();
            if (n > 0) plugin.getLogger().info("Upgrades: " + n + " alte Standard-Werte auf 50 Level (4 Stufen) umgestellt.");
            if (added > 0) plugin.getLogger().info("Stufen: " + added + " neue Abschnitte (Nether, End, Stufen-Generatoren, Shop) übernommen.");
        }
        return n + added;
    }

    private static void copy(org.bukkit.configuration.ConfigurationSection from, org.bukkit.configuration.ConfigurationSection to) {
        for (String k : from.getKeys(false)) {
            if (from.isConfigurationSection(k)) copy(from.getConfigurationSection(k), to.createSection(k));
            else to.set(k, from.get(k));
        }
    }

    private static List<Long> numbers(List<?> l) {
        List<Long> out = new ArrayList<>();
        if (l == null) return out;
        for (Object o : l) out.add(o instanceof Number x ? x.longValue() : Long.MIN_VALUE);
        return out;
    }
}
