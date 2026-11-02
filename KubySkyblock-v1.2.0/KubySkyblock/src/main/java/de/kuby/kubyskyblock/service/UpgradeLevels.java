package de.kuby.kubyskyblock.service;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Level der Skyblock-Upgrades (Insel und One-Block): Level 0 bis 20 in drei Stufen. Stufe 1 = Level 1 bis 10, Stufe 2 =
 * bis 15, Stufe 3 = bis 20 (Wunsch des Besitzers). Die Icons zeigen die Stufe: ab Stufe 2 goldene, ab Stufe 3 lila Sterne.
 * <p>
 * Früher hatten die Upgrades 4 bis 10 Stufen. Diese Klasse ersetzt unveränderte alte Standard-Werte in der config durch
 * die neuen 20-Level-Listen und rechnet gespeicherte Stände so um, dass jede Insel und jedes Team genau die bisherige
 * Wirkung behält (die alten Stufen liegen in den neuen Listen auf festen Leveln).
 */
public final class UpgradeLevels {

    public static final int MAX = 20;
    /** Version der gespeicherten Upgrade-Stände (1 = alte Stufen, 2 = Level 0 bis 20). */
    public static final int VERSION = 2;

    private UpgradeLevels() {
    }

    /** Radius je Größen-Level (wie config insel.groessen). */
    public static final List<Integer> SIZES = List.of(15, 17, 19, 21, 23, 25, 27, 29, 31, 33, 35, 38, 41, 44, 47, 50, 54, 58, 62, 66, 70);
    /** Mitglieder (ohne Besitzer) je Level (wie config insel.mitglieder). */
    public static final List<Integer> MEMBERS = List.of(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);

    /** Ersatz-Preisliste, wenn die config keine hat: Level 1 kostet first, jedes weitere 22 % mehr (gerundet). */
    public static List<Long> prices(long first) {
        List<Long> out = new ArrayList<>();
        out.add(0L);
        for (int l = 1; l <= MAX; l++) {
            double v = first * Math.pow(1.22, l - 1);
            double e = Math.pow(10, Math.floor(Math.log10(v)) - 1);
            out.add(Math.round(Math.round(v / e) * e));
        }
        return out;
    }

    /** Stufe eines Levels: 1 (bis 10), 2 (bis 15), 3 (bis 20). */
    public static int tier(int level) {
        return level > 15 ? 3 : level > 10 ? 2 : 1;
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

    private static final Map<String, List<Long>> OLD_LISTS = Map.of(
            "insel.groessen", List.of(15L, 25L, 35L, 50L, 70L),
            "insel.mitglieder", List.of(2L, 4L, 6L, 10L),
            "insel.upgrades.groesse", List.of(0L, 25_000L, 75_000L, 200_000L, 500_000L),
            "insel.upgrades.mitglieder", List.of(0L, 10_000L, 40_000L, 100_000L),
            "insel.upgrades.wachstum", List.of(0L, 15_000L, 50_000L, 150_000L, 400_000L),
            "insel.upgrades.spawnrate", List.of(0L, 20_000L, 60_000L, 180_000L, 450_000L),
            "insel.upgrades.limits", List.of(0L, 25_000L, 75_000L, 200_000L, 500_000L));
    private static final Map<String, Double> OLD_BONUS = Map.of("wachstum.bonus-je-stufe", 0.25, "spawnrate.bonus-je-stufe", 0.2,
            "limits.bonus-je-stufe", 0.25);
    /** One-Block: Upgrade -> alte Werte (max, basis, faktor). */
    private static final Map<String, double[]> OLD_ONEBLOCK = Map.of("glueck", new double[]{10, 250, 1.55},
            "cooldown", new double[]{10, 200, 1.5}, "tempo", new double[]{5, 600, 2.2}, "xp", new double[]{10, 200, 1.5},
            "geld", new double[]{10, 200, 1.5}, "beute", new double[]{10, 300, 1.55});
    private static final List<Long> OLD_GENERATOR_COSTS = List.of(0L, 5_000L, 20_000L, 60_000L, 150_000L, 400_000L);

    /**
     * Ersetzt in der config der Server-Installation alle Upgrade-Werte, die noch genau den alten Standard haben, durch
     * die neuen aus dem Plugin (eigene Werte bleiben). Gibt die Zahl der ersetzten Einträge zurück.
     */
    public static int updateConfig(JavaPlugin plugin) {
        FileConfiguration c = plugin.getConfig();
        Configuration d = c.getDefaults();
        if (d == null) return 0;
        int n = 0;
        for (Map.Entry<String, List<Long>> e : OLD_LISTS.entrySet()) {
            if (c.isSet(e.getKey()) && numbers(c.getList(e.getKey())).equals(e.getValue())) {
                c.set(e.getKey(), d.getList(e.getKey()));
                n++;
            }
        }
        List<Map<?, ?>> gen = c.getMapList("generator.stufen");
        List<Long> costs = new ArrayList<>();
        for (Map<?, ?> m : gen) costs.add(m.get("kosten") instanceof Number x ? x.longValue() : -1L);
        if (costs.equals(OLD_GENERATOR_COSTS)) {
            c.set("generator.stufen", d.getList("generator.stufen"));
            n++;
        }
        for (Map.Entry<String, Double> e : OLD_BONUS.entrySet()) {
            if (c.isSet(e.getKey()) && Math.abs(c.getDouble(e.getKey()) - e.getValue()) < 1e-9) {
                c.set(e.getKey(), d.getDouble(e.getKey()));
                n++;
            }
        }
        for (Map.Entry<String, double[]> e : OLD_ONEBLOCK.entrySet()) {
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
        if (n > 0) {
            plugin.saveConfig();
            plugin.getLogger().info("Upgrades: " + n + " alte Standard-Werte auf 20 Level (3 Stufen) umgestellt.");
        }
        return n;
    }

    private static List<Long> numbers(List<?> l) {
        List<Long> out = new ArrayList<>();
        if (l == null) return out;
        for (Object o : l) out.add(o instanceof Number x ? x.longValue() : Long.MIN_VALUE);
        return out;
    }
}
