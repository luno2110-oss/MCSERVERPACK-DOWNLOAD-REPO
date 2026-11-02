package de.kuby.kubyskyblock.service;

import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.IslandService.Island;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.WaterMob;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lag-Grenzen je Insel: Höchstzahl bestimmter Blöcke (Trichter, Kolben, Beobachter, Werfer, Redstone-Bauteile) und
 * Wesen (Tiere, Monster, Dorfbewohner, Golems). Das Upgrade „Limits“ hebt alle Grenzen an (limits.bonus-je-stufe).
 * <p>
 * Schnell: Blöcke werden beim ersten Bedarf einmal gezählt (Chunk-Schnappschüsse, 8 je Tick, Zählen im Hintergrund),
 * danach nur noch beim Setzen und Abbauen mitgezählt. Wesen werden höchstens alle 10 Sekunden je Insel gezählt (nur
 * geladene Chunks), dazwischen nur mitgezählt.
 */
public final class LimitService {

    /** Block-Gruppe: Name, Grundgrenze, Blöcke. */
    public record Group(String id, String name, int max, Set<Material> blocks) {}

    /** Wesen-Arten mit Grenze. */
    public enum Kind {
        TIERE("Tiere"), MONSTER("Monster"), DORFBEWOHNER("Dorfbewohner"), GOLEMS("Golems");
        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** Eine Zeile für die Anzeige: Name, belegt, Grenze. */
    public record Usage(String name, int used, int max, boolean counted) {}

    private final KubySkyblock addon;
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<Material, Group> byMaterial = new EnumMap<>(Material.class);
    private final Map<Kind, Integer> kindMax = new EnumMap<>(Kind.class);
    /** Gezählte Blöcke je Insel (Platz → Gruppe → Anzahl). */
    private final Map<Integer, Map<String, Integer>> blocks = new HashMap<>();
    private final Set<Integer> scanning = new HashSet<>();
    /** Gezählte Wesen je Insel: [Zeit, Tiere, Monster, Dorfbewohner, Golems]. */
    private final Map<Integer, long[]> mobs = new HashMap<>();

    public LimitService(KubySkyblock addon) {
        this.addon = addon;
        reload();
    }

    public void reload() {
        groups.clear();
        byMaterial.clear();
        kindMax.clear();
        ConfigurationSection b = addon.getConfig().getConfigurationSection("limits.bloecke");
        if (b != null) {
            for (String id : b.getKeys(false)) {
                ConfigurationSection g = b.getConfigurationSection(id);
                if (g == null) continue;
                Set<Material> ms = new HashSet<>();
                for (String m : g.getStringList("items")) {
                    Material mat = Material.matchMaterial(m);
                    if (mat != null && mat.isBlock()) ms.add(mat);
                }
                if (ms.isEmpty()) continue;
                Group gr = new Group(id, g.getString("name", id), Math.max(0, g.getInt("max", 32)), ms);
                groups.put(id, gr);
                for (Material m : ms) byMaterial.put(m, gr);
            }
        }
        for (Kind k : Kind.values()) kindMax.put(k, addon.getConfig().getInt("limits.wesen." + k.name().toLowerCase(java.util.Locale.ROOT), switch (k) {
            case TIERE -> 60;
            case MONSTER -> 50;
            case DORFBEWOHNER -> 20;
            case GOLEMS -> 4;
        }));
    }

    public boolean enabled() {
        return addon.getConfig().getBoolean("limits.aktiv", true);
    }

    /** Bonus des Limits-Upgrades (0,25 = +25 %). */
    public double bonus(Island i) {
        return i == null ? 0 : i.limit * addon.getConfig().getDouble("limits.bonus-je-stufe", 0.25);
    }

    private int scaled(Island i, int base) {
        return (int) Math.round(base * (1 + bonus(i)));
    }

    public int max(Island i, Group g) {
        return scaled(i, g.max());
    }

    public int max(Island i, Kind k) {
        return scaled(i, kindMax.getOrDefault(k, 0));
    }

    public Group groupOf(Material m) {
        return byMaterial.get(m);
    }

    // =================================================================================================================
    // Blöcke
    // =================================================================================================================

    /** Darf noch ein Block dieser Art auf die Insel? Gibt den Grund zurück oder null. */
    public String checkPlace(Island i, Material m) {
        if (!enabled() || i == null) return null;
        Group g = byMaterial.get(m);
        if (g == null) return null;
        Map<String, Integer> c = blocks.get(i.slot);
        if (c == null) {
            scan(i);
            return null;
        }
        int used = c.getOrDefault(g.id(), 0), max = max(i, g);
        return used >= max ? "Limit erreicht: " + g.name() + " " + used + "/" + max + ". Das Upgrade „Limits“ hebt die Grenze an." : null;
    }

    /** Block gesetzt (+1) oder entfernt (-1). */
    public void changed(Island i, Material m, int delta) {
        if (i == null) return;
        Group g = byMaterial.get(m);
        if (g == null) return;
        Map<String, Integer> c = blocks.get(i.slot);
        if (c != null) c.merge(g.id(), delta, (a, b) -> Math.max(0, a + b));
    }

    /** Insel geleert oder aufgegeben: neu zählen, wenn es wieder gebraucht wird. */
    public void forget(int slot) {
        blocks.remove(slot);
        mobs.remove(slot);
    }

    /** Blöcke der Insel (mit Biom-Inseln) zählen: Schnappschüsse in Portionen, Zählen im Hintergrund. */
    public void scan(Island i) {
        if (i == null || !scanning.add(i.slot)) return;
        World w = addon.islands().world();
        if (w == null) {
            scanning.remove(i.slot);
            return;
        }
        List<long[]> chunks = new ArrayList<>();
        List<int[]> areas = areas(i);
        Set<Long> seen = new HashSet<>();
        for (int[] a : areas) {
            for (int cx = a[0] >> 4; cx <= a[2] >> 4; cx++) {
                for (int cz = a[1] >> 4; cz <= a[3] >> 4; cz++) if (seen.add(((long) cx << 32) ^ (cz & 0xffffffffL))) chunks.add(new long[]{cx, cz});
            }
        }
        List<ChunkSnapshot> snaps = new ArrayList<>();
        int[] at = {0};
        Bukkit.getScheduler().runTaskTimer(addon, task -> {
            for (int n = 0; n < 8 && at[0] < chunks.size(); n++, at[0]++) {
                long[] c = chunks.get(at[0]);
                snaps.add(w.getChunkAt((int) c[0], (int) c[1]).getChunkSnapshot(false, false, false));
            }
            if (at[0] < chunks.size()) return;
            task.cancel();
            int min = w.getMinHeight(), max = w.getMaxHeight();
            Bukkit.getScheduler().runTaskAsynchronously(addon, () -> {
                Map<String, Integer> out = new HashMap<>();
                for (ChunkSnapshot s : snaps) {
                    int bx = s.getX() << 4, bz = s.getZ() << 4;
                    for (int dx = 0; dx < 16; dx++) {
                        for (int dz = 0; dz < 16; dz++) {
                            int x = bx + dx, z = bz + dz;
                            if (!inside(areas, x, z)) continue;
                            int top = Math.min(max - 1, s.getHighestBlockYAt(dx, dz));
                            for (int y = min; y <= top; y++) {
                                Group g = byMaterial.get(s.getBlockType(dx, y, dz));
                                if (g != null) out.merge(g.id(), 1, Integer::sum);
                            }
                        }
                    }
                }
                Bukkit.getScheduler().runTask(addon, () -> {
                    scanning.remove(i.slot);
                    if (addon.islands().bySlot(i.slot) == i) blocks.put(i.slot, out);
                });
            });
        }, 1L, 1L);
    }

    /** Bereiche der Insel: [x0, z0, x1, z1] für die Insel selbst und ihre Biom-Inseln. */
    public List<int[]> areas(Island i) {
        List<int[]> out = new ArrayList<>();
        Location c = addon.islands().center(i);
        int r = addon.islands().radius(i);
        out.add(new int[]{c.getBlockX() - r, c.getBlockZ() - r, c.getBlockX() + r, c.getBlockZ() + r});
        if (addon.biomes() != null) {
            for (BiomeIslandService.BIsland b : addon.biomes().of(i.slot)) out.add(new int[]{b.x - b.radius, b.z - b.radius, b.x + b.radius, b.z + b.radius});
        }
        return out;
    }

    private static boolean inside(List<int[]> areas, int x, int z) {
        for (int[] a : areas) if (x >= a[0] && x <= a[2] && z >= a[1] && z <= a[3]) return true;
        return false;
    }

    // =================================================================================================================
    // Wesen
    // =================================================================================================================

    /** Art eines Wesens (null = zählt nicht, z. B. Rüstungsständer, Spieler). */
    public static Kind kindOf(Entity e) {
        if (e instanceof Villager || e instanceof WanderingTrader) return Kind.DORFBEWOHNER;
        if (e instanceof Golem && !(e instanceof Shulker)) return Kind.GOLEMS;
        if (e instanceof Enemy) return Kind.MONSTER;
        if (e instanceof Animals || e instanceof WaterMob || e instanceof Ambient || e instanceof Mob) return Kind.TIERE;
        return null;
    }

    /** Wie viele Wesen dieser Art sind auf der Insel? Zählt höchstens alle 10 Sekunden neu. */
    public int count(Island i, Kind k) {
        long[] c = mobs.get(i.slot);
        long now = System.currentTimeMillis();
        if (c == null || now - c[0] > 10_000) {
            c = new long[1 + Kind.values().length];
            c[0] = now;
            World w = addon.islands().world();
            if (w != null) {
                List<int[]> areas = areas(i);
                Set<Long> seen = new HashSet<>();
                for (int[] a : areas) {
                    for (int cx = a[0] >> 4; cx <= a[2] >> 4; cx++) {
                        for (int cz = a[1] >> 4; cz <= a[3] >> 4; cz++) {
                            if (!seen.add(((long) cx << 32) ^ (cz & 0xffffffffL)) || !w.isChunkLoaded(cx, cz)) continue;
                            Chunk ch = w.getChunkAt(cx, cz);
                            for (Entity e : ch.getEntities()) {
                                Kind ek = kindOf(e);
                                if (ek == null || !inside(areas, e.getLocation().getBlockX(), e.getLocation().getBlockZ())) continue;
                                c[1 + ek.ordinal()]++;
                            }
                        }
                    }
                }
            }
            mobs.put(i.slot, c);
        }
        return (int) c[1 + k.ordinal()];
    }

    /** Darf hier noch ein Wesen dieser Art dazukommen? Zählt es gleich mit, wenn ja. */
    public boolean allowSpawn(Island i, Kind k) {
        if (!enabled() || i == null || k == null) return true;
        if (count(i, k) >= max(i, k)) return false;
        mobs.get(i.slot)[1 + k.ordinal()]++;
        return true;
    }

    // =================================================================================================================
    // Anzeige
    // =================================================================================================================

    /** Alle Grenzen der Insel (Blöcke zuerst; noch nicht gezählte Blöcke werden jetzt gezählt). */
    public List<Usage> usage(Island i) {
        List<Usage> out = new ArrayList<>();
        Map<String, Integer> c = blocks.get(i.slot);
        if (c == null) scan(i);
        for (Group g : groups.values()) out.add(new Usage(g.name(), c == null ? 0 : c.getOrDefault(g.id(), 0), max(i, g), c != null));
        for (Kind k : Kind.values()) out.add(new Usage(k.label, count(i, k), max(i, k), true));
        return out;
    }
}
