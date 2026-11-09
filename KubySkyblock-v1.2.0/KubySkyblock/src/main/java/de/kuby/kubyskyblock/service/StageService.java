package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.WorldService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.GeneratorService.Gen;
import de.kuby.kubyskyblock.service.IslandService.Island;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Skyblock-Stufen Nether und End (K7): keine normalen Dimensionen, sondern eigene leere Welten, in denen jede Insel an
 * derselben Stelle wie im Skyblock eine eigene Stufen-Insel bekommt (gleiche Mitglieder, Rechte und Schutz).
 * <p>
 * Freischalten (Vorgabe des Besitzers): die Nether-Stufe erst, wenn die Insel von jeder Generator-Art mindestens einen
 * hat und alle ihre Generatoren auf der höchsten Stufe sind; die End-Stufe danach genauso mit den neuen Nether-
 * Generatoren. Die Bedingungen stehen in der config (stufen.*), damit man sie lockern kann, falls es zu lange dauert.
 * <p>
 * Jede Stufe hat eigene Generator-Arten (nur dort aufstellbar), einen eigenen Block-Generator (Nether: Basalt aus Lava,
 * Seelenerde und Blaueis; End: Bruchstein-Generator mit End-Blöcken), eine Start-Insel mit Kiste und ein Rückkehr-
 * Portal. Vanilla-Portale führen von der Insel zur freigeschalteten Stufe und von dort zurück.
 */
public final class StageService {

    /** Die Stufen über der normalen Insel. */
    public enum Stage {
        NETHER(1, "nether", "Nether", "#FF7A4A", World.Environment.NETHER),
        END(2, "end", "End", "#C98CFF", World.Environment.THE_END);

        public final int order;
        public final String key, label, color;
        public final World.Environment env;

        Stage(int order, String key, String label, String color, World.Environment env) {
            this.order = order;
            this.key = key;
            this.label = label;
            this.color = color;
            this.env = env;
        }

        public static Stage of(String key) {
            if (key == null) return null;
            for (Stage s : values()) if (s.key.equalsIgnoreCase(key)) return s;
            return null;
        }
    }

    /** Eine Bedingung zum Freischalten: Text, erfüllt?, Stand (z. B. „Stufe 7/10“). */
    public record Req(String text, boolean done, String state) {}

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final Map<Stage, World> worlds = new EnumMap<>(Stage.class);

    public StageService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        // vorhandene Stufen-Welten gleich laden (neue entstehen erst beim ersten Freischalten)
        for (Stage s : Stage.values()) {
            if (new File(Bukkit.getWorldContainer(), worldName(s)).exists()) world(s, true);
        }
    }

    // =================================================================================================================
    // Welten
    // =================================================================================================================

    /** Namen der Stufen-Welten (einmal aus der config gelesen; stageOf wird bei sehr vielen Ereignissen gefragt). */
    private final Map<Stage, String> names = new EnumMap<>(Stage.class);

    public String worldName(Stage s) {
        return names.computeIfAbsent(s, k -> addon.getConfig().getString("stufen." + k.key + ".welt", "skyblock_" + k.key));
    }

    /** Welt der Stufe (null, wenn noch nicht angelegt und create false). */
    public World world(Stage s, boolean create) {
        World w = Bukkit.getWorld(worldName(s));
        if (w == null && create) {
            w = new WorldCreator(worldName(s)).environment(s.env).generator(new WorldService.VoidGenerator())
                    .generateStructures(false).createWorld();
            if (w == null) return null;
            WorldBorder b = w.getWorldBorder();
            b.setCenter(0, 0);
            b.setSize(addon.getConfig().getInt("insel.weltgrenze", 50_000));
            w.setSpawnLocation(0, IslandService.HEIGHT, 0);
        }
        if (w != null) {
            worlds.put(s, w);
            if (s == Stage.END) calmDragon(w);
        }
        return w;
    }

    /** Stufe einer Welt (null für alle anderen Welten). */
    public Stage stageOf(World w) {
        if (w == null) return null;
        String n = w.getName();
        for (Stage s : Stage.values()) if (n.equals(worldName(s))) return s;
        return null;
    }

    /** In der End-Stufe gibt es keinen Drachenkampf: Drache entfernen, Kampf als erledigt markieren. */
    public void calmDragon(World w) {
        DragonBattle b = w.getEnderDragonBattle();
        if (b == null) return;
        if (b.getEnderDragon() != null) b.getEnderDragon().remove();
        if (!b.hasBeenPreviouslyKilled()) b.setPreviouslyKilled(true);
        if (b.getBossBar() != null) b.getBossBar().removeAll();
    }

    // =================================================================================================================
    // Freischalten
    // =================================================================================================================

    public boolean unlocked(Island i, Stage s) {
        return i != null && i.stage >= s.order;
    }

    /** Generator-Art: zu welcher Stufe gehört sie? ("" = normale Insel) */
    public static String stageKey(GeneratorService.Type t) {
        return t == null || t.stage() == null ? "" : t.stage();
    }

    /** Bedingungen zum Freischalten der Stufe für diese Insel. */
    public List<Req> requirements(Island i, Stage s) {
        List<Req> out = new ArrayList<>();
        String base = "stufen." + s.key + ".";
        if (s == Stage.END) out.add(new Req("Nether-Stufe freigeschaltet", unlocked(i, Stage.NETHER), unlocked(i, Stage.NETHER) ? "✔" : "gesperrt"));
        GeneratorService gs = addon.generators();
        String need = s == Stage.NETHER ? "" : Stage.NETHER.key;
        boolean eachType = addon.getConfig().getBoolean(base + "jede-art", true);
        boolean allMax = addon.getConfig().getBoolean(base + "alle-max", true);
        int max = gs.maxLevel();
        List<Gen> gens = new ArrayList<>();
        if (i != null) {
            for (Gen g : gs.onIsland(i.slot)) if (need.equals(stageKey(gs.type(g.type)))) gens.add(g);
        }
        if (eachType) {
            for (GeneratorService.Type t : gs.types()) {
                if (!need.equals(stageKey(t))) continue;
                int best = -1;
                for (Gen g : gens) if (g.type.equals(t.id())) best = Math.max(best, g.level);
                boolean ok = allMax ? best >= max : best >= 1;
                out.add(new Req(t.name(), ok, best < 0 ? "fehlt" : "Level " + best + "/" + max));
            }
        }
        if (allMax) {
            int low = 0;
            for (Gen g : gens) if (g.level < max) low++;
            String what = s == Stage.NETHER ? "Alle Generatoren" : "Alle Nether-Generatoren";
            out.add(new Req(what + " auf Level " + max, !gens.isEmpty() && low == 0, gens.isEmpty() ? "keine" : low == 0 ? "✔" : low + " darunter"));
        }
        int lvl = addon.getConfig().getInt(base + "insel-level", 0);
        if (lvl > 0) {
            long have = i == null ? 0 : addon.islands().levelOf(i);
            out.add(new Req("Insel-Level " + lvl, have >= lvl, "Level " + have));
        }
        return out;
    }

    public boolean ready(Island i, Stage s) {
        for (Req r : requirements(i, s)) if (!r.done()) return false;
        return true;
    }

    /** Freischalten (Besitzer und Co-Besitzer). Gibt null bei Erfolg zurück. */
    public String unlock(Player p, Stage s) {
        IslandService is = addon.islands();
        Island i = is.islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (unlocked(i, s)) return "Die " + s.label + "-Stufe ist schon freigeschaltet.";
        if (!is.canManage(i, p.getUniqueId())) return "Freischalten dürfen nur Besitzer und Co-Besitzer.";
        if (!ready(i, s)) return "Es fehlen noch Bedingungen für die " + s.label + "-Stufe (/insel stufen).";
        World w = world(s, true);
        if (w == null) return "Die Welt der " + s.label + "-Stufe konnte nicht angelegt werden.";
        i.stage = s.order;
        is.markDirty();
        ensureBuilt(i, s);
        for (UUID m : i.everyone()) {
            Player o = Bukkit.getPlayer(m);
            if (o == null) continue;
            Msg.send(o, "<" + s.color + "><b>" + s.label + "-Stufe freigeschaltet!</b> <gray>Hin geht es mit <white>/insel " + s.key
                    + "</white> oder durch ein Portal auf der Insel.");
            Sfx.play(core, o, Sfx.Kind.SUCCESS_BIG);
        }
        core.questProgress(p, "stufe", s.key, 1);
        return null;
    }

    // =================================================================================================================
    // Reisen
    // =================================================================================================================

    /** Mitte der Stufen-Insel (gleiche Stelle wie die Insel im Skyblock). */
    public Location center(Island i, Stage s) {
        Location c = addon.islands().center(i);
        World w = worlds.get(s);
        if (w == null) w = world(s, false);
        return new Location(w, c.getX(), c.getY(), c.getZ());
    }

    /** Zur eigenen Stufen-Insel (baut sie beim ersten Mal). Gibt null bei Erfolg zurück. */
    public String travel(Player p, Stage s) {
        Island i = addon.islands().islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (!unlocked(i, s)) return "Die " + s.label + "-Stufe ist noch gesperrt (/insel stufen).";
        World w = world(s, true);
        if (w == null) return "Die Welt der " + s.label + "-Stufe ist nicht geladen.";
        ensureBuilt(i, s);
        if (p.isInsideVehicle()) p.leaveVehicle();
        p.setFallDistance(0);
        p.setPortalCooldown(Math.max(p.getPortalCooldown(), 60));
        p.teleportAsync(center(i, s).add(0.5, 0, 0.5)).thenAccept(ok -> {
            if (!ok) Bukkit.getScheduler().runTask(addon, () -> Msg.send(p, "<red>Die Reise zur " + s.label + "-Insel hat nicht geklappt. Versuch es gleich nochmal."));
        });
        return null;
    }

    /** Ins Leere gefallen in einer Stufen-Welt: zurück auf die Stufen-Insel, sonst nach Hause. */
    public void rescue(Player p, Stage s) {
        Island i = addon.islands().islandOf(p.getUniqueId());
        if (i != null && unlocked(i, s)) {
            p.teleportAsync(center(i, s).add(0.5, 0, 0.5));
        } else if (i != null) {
            addon.islands().home(p, i);
        } else {
            Location sp = core.modes().spawnOf(ModeService.SKYBLOCK);
            if (sp != null) p.teleportAsync(sp);
        }
    }

    // =================================================================================================================
    // Block-Generator der Stufe
    // =================================================================================================================

    /** Zufälliger Block des Stufen-Generators (Nether: statt Basalt, End: statt Bruchstein); null = Block bleibt. */
    public Material generate(Island i, Stage s) {
        var sec = addon.getConfig().getConfigurationSection("stufen." + s.key + ".generator");
        if (sec == null) return null;
        // Stufe des Insel-Generators wählt die Tabelle (höchste Schwelle, die erreicht ist)
        String best = null;
        int bestAt = -1;
        for (String k : sec.getKeys(false)) {
            try {
                int at = Integer.parseInt(k);
                if (at <= i.generator && at > bestAt) {
                    bestAt = at;
                    best = k;
                }
            } catch (NumberFormatException ignored) {
                // keine Zahl
            }
        }
        if (best == null) return null;
        var w = sec.getConfigurationSection(best);
        if (w == null) return null;
        int total = 0;
        for (String m : w.getKeys(false)) total += Math.max(0, w.getInt(m));
        if (total <= 0) return null;
        int r = java.util.concurrent.ThreadLocalRandom.current().nextInt(total);
        for (String m : w.getKeys(false)) {
            r -= Math.max(0, w.getInt(m));
            if (r < 0) {
                Material mat = Material.matchMaterial(m);
                return mat != null && mat.isBlock() ? mat : null;
            }
        }
        return null;
    }

    // =================================================================================================================
    // Start-Inseln
    // =================================================================================================================

    /** Baut die Start-Insel der Stufe einmal (Merker je Insel). */
    public void ensureBuilt(Island i, Stage s) {
        int bit = 1 << s.order;
        if ((i.built & bit) != 0) return;
        Location c = center(i, s);
        if (c.getWorld() == null) return;
        if (s == Stage.NETHER) buildNether(c);
        else buildEnd(c);
        i.built |= bit;
        addon.islands().markDirty();
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private static boolean inDisc(int dx, int dz, double r) {
        return dx * dx + dz * dz <= r * r + 0.5;
    }

    /** Nether-Insel: Netherrack mit Karmesin-Nyzel, Seelensand mit Netherwarzen, Karmesinpilz-Baum, Glowstone darunter,
     *  Kiste mit allem für den Basalt-Generator und ein brennendes Rückkehr-Portal aus Obsidian. */
    private void buildNether(Location c) {
        World w = c.getWorld();
        int x0 = c.getBlockX(), y = c.getBlockY(), z0 = c.getBlockZ();
        double[] radius = {5, 4, 3, 2, 1.2};
        for (int layer = 0; layer < radius.length; layer++) {
            int yy = y - 1 - layer;
            int r = (int) Math.ceil(radius[layer]);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!inDisc(dx, dz, radius[layer])) continue;
                    Material m = Material.NETHERRACK;
                    if (layer == 0 && dx <= -1 && dz <= -1) m = Material.CRIMSON_NYLIUM;
                    if (layer == 1 && (dx + dz) % 3 == 0) m = Material.BLACKSTONE;
                    if (layer >= 3) m = Material.GLOWSTONE;
                    set(w, x0 + dx, yy, z0 + dz, m);
                }
            }
        }
        // Seelensand mit Netherwarzen (reif)
        for (int dx = 2; dx <= 3; dx++) {
            for (int dz = 1; dz <= 2; dz++) {
                set(w, x0 + dx, y - 1, z0 + dz, Material.SOUL_SAND);
                Block b = w.getBlockAt(x0 + dx, y, z0 + dz);
                b.setType(Material.NETHER_WART, false);
                if (b.getBlockData() instanceof Ageable a) {
                    a.setAge(a.getMaximumAge());
                    b.setBlockData(a, false);
                }
            }
        }
        // Karmesinpilz-Baum
        int tx = x0 - 3, tz = z0 - 2;
        for (int h = 0; h < 4; h++) set(w, tx, y + h, tz, Material.CRIMSON_STEM);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(w, tx + dx, y + 4, tz + dz, dx == 1 && dz == -1 ? Material.SHROOMLIGHT : Material.NETHER_WART_BLOCK);
                if (Math.abs(dx) + Math.abs(dz) < 2) set(w, tx + dx, y + 5, tz + dz, Material.NETHER_WART_BLOCK);
            }
        }
        set(w, tx - 1, y + 3, tz + 1, Material.WEEPING_VINES);
        // Rückkehr-Portal aus Obsidian (brennend), Rand der Insel
        int px = x0 - 1, pz = z0 + 4;
        for (int dx = 0; dx < 4; dx++) {
            for (int h = 0; h < 5; h++) {
                boolean frame = dx == 0 || dx == 3 || h == 0 || h == 4;
                Block b = w.getBlockAt(px + dx, y + h, pz);
                if (frame) {
                    b.setType(Material.OBSIDIAN, false);
                } else {
                    b.setType(Material.NETHER_PORTAL, false);
                    if (b.getBlockData() instanceof Orientable o) {
                        o.setAxis(Axis.X);
                        b.setBlockData(o, false);
                    }
                }
            }
        }
        // Laterne und Kiste
        set(w, x0 + 3, y, z0 - 2, Material.BLACKSTONE);
        set(w, x0 + 3, y + 1, z0 - 2, Material.SOUL_LANTERN);
        chest(w.getBlockAt(x0 - 2, y, z0 + 2), List.of(new ItemStack(Material.LAVA_BUCKET), new ItemStack(Material.BLUE_ICE, 2),
                new ItemStack(Material.SOUL_SOIL, 8), new ItemStack(Material.CRIMSON_FUNGUS, 2), new ItemStack(Material.WARPED_FUNGUS, 2),
                new ItemStack(Material.WARPED_NYLIUM, 2), new ItemStack(Material.BONE_MEAL, 16), new ItemStack(Material.NETHER_WART, 4)));
    }

    /** End-Insel: Endstein mit Steinziegel-Ring, Chorus-Pflanze, Purpur-Säulen mit Endstäben, Obsidian-Kern und ein
     *  Rückkehr-Portal (End-Portalrahmen mit Augen). */
    private void buildEnd(Location c) {
        World w = c.getWorld();
        int x0 = c.getBlockX(), y = c.getBlockY(), z0 = c.getBlockZ();
        double[] radius = {6, 5, 4, 3, 2, 1.2};
        for (int layer = 0; layer < radius.length; layer++) {
            int yy = y - 1 - layer;
            int r = (int) Math.ceil(radius[layer]);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!inDisc(dx, dz, radius[layer])) continue;
                    Material m = Material.END_STONE;
                    int d2 = dx * dx + dz * dz;
                    if (layer == 0 && d2 >= 6 && d2 <= 10) m = Material.END_STONE_BRICKS;
                    if (layer >= 1 && layer <= 2 && d2 <= 2) m = Material.OBSIDIAN;
                    set(w, x0 + dx, yy, z0 + dz, m);
                }
            }
        }
        // Rückkehr-Portal: 3 x 3 End-Portal im Rahmen mit Augen, vor der Mitte
        int pcx = x0, pcz = z0 - 3;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                Block b = w.getBlockAt(pcx + dx, y - 1, pcz + dz);
                if (corner) {
                    b.setType(Material.END_STONE_BRICKS, false);
                } else if (ring) {
                    b.setType(Material.END_PORTAL_FRAME, false);
                    if (b.getBlockData() instanceof EndPortalFrame f) {
                        f.setEye(true);
                        f.setFacing(dx == -2 ? BlockFace.EAST : dx == 2 ? BlockFace.WEST : dz == -2 ? BlockFace.SOUTH : BlockFace.NORTH);
                        b.setBlockData(f, false);
                    }
                } else {
                    b.setType(Material.END_PORTAL, false);
                }
            }
        }
        // Chorus-Pflanze
        int cx = x0 - 3, cz = z0 + 2;
        set(w, cx, y, cz, Material.CHORUS_PLANT);
        set(w, cx, y + 1, cz, Material.CHORUS_PLANT);
        set(w, cx, y + 2, cz, Material.CHORUS_FLOWER);
        // Purpur-Säulen mit Endstäben
        for (int[] q : new int[][]{{x0 + 3, z0 + 3}, {x0 + 4, z0 - 1}}) {
            set(w, q[0], y, q[1], Material.PURPUR_PILLAR);
            set(w, q[0], y + 1, q[1], Material.PURPUR_PILLAR);
            set(w, q[0], y + 2, q[1], Material.END_ROD);
        }
        chest(w.getBlockAt(x0 + 2, y, z0 + 2), List.of(new ItemStack(Material.CHORUS_FLOWER, 2), new ItemStack(Material.END_STONE, 32),
                new ItemStack(Material.ENDER_PEARL, 4), new ItemStack(Material.OBSIDIAN, 8), new ItemStack(Material.PURPUR_BLOCK, 16),
                new ItemStack(Material.WATER_BUCKET), new ItemStack(Material.LAVA_BUCKET)));
    }

    private static void chest(Block b, List<ItemStack> items) {
        b.setType(Material.CHEST, false);
        if (b.getState() instanceof Chest ch) {
            for (ItemStack it : items) ch.getBlockInventory().addItem(it);
        }
    }

    /** Insel aufgegeben: Stufen-Inseln leeren (Spalten je Tick begrenzt). */
    public void clear(Island i) {
        for (Stage s : Stage.values()) {
            if ((i.built & (1 << s.order)) == 0) continue;
            World w = worlds.get(s);
            if (w == null) continue;
            Location c = center(i, s);
            int r = addon.islands().radius(addon.islands().maxSize());
            int x0 = c.getBlockX() - r, z0 = c.getBlockZ() - r, side = 2 * r + 1;
            int min = w.getMinHeight();
            int[] col = {0};
            int perTick = addon.getConfig().getInt("insel.leeren-spalten-pro-tick", 400);
            Bukkit.getScheduler().runTaskTimer(addon, task -> {
                for (int n = 0; n < perTick && col[0] < side * side; n++, col[0]++) {
                    int x = x0 + col[0] % side, z = z0 + col[0] / side;
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) w.getChunkAt(x >> 4, z >> 4);
                    int top = w.getHighestBlockYAt(x, z);
                    for (int yy = top; yy >= min; yy--) {
                        Block b = w.getBlockAt(x, yy, z);
                        if (!b.getType().isAir()) b.setType(Material.AIR, false);
                    }
                }
                if (col[0] >= side * side) task.cancel();
            }, 1L, 1L);
        }
        i.built = 0;
        i.stage = 0;
    }

    /** Kurzer Stand für Chat und Karten, z. B. „5/9 Bedingungen“. */
    public String progressText(Island i, Stage s) {
        if (unlocked(i, s)) return "Freigeschaltet";
        List<Req> rs = requirements(i, s);
        int done = 0;
        for (Req r : rs) if (r.done()) done++;
        return done + "/" + rs.size() + " Bedingungen";
    }

    public static String key(Stage s) {
        return s.key.toLowerCase(Locale.ROOT);
    }
}
