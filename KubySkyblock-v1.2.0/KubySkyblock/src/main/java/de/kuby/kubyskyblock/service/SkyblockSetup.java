package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Skyblock in einem Befehl einrichten (/kp skyblock einrichten): Welt anlegen (leer), Weltgrenze, Spawn-Insel mit Platz,
 * Laternen und Bäumen, Brücke zu den One-Block-Plattformen, Spawn setzen, One-Block-Plattformen, Biom-Inseln und Loot-Inseln (in
 * kleinen Portionen, damit der Server nicht hängt). Schritte, die schon erledigt sind, werden übersprungen.
 */
public final class SkyblockSetup {

    /** Höhe der Oberfläche (Spawn-Insel, Brücken und One-Block-Sockel liegen gleich hoch). */
    static final int TOP = 99;

    private SkyblockSetup() {
    }

    public static void run(KubySkyblock addon, Player p) {
        KubyPlugins core = addon.core();
        String name = core.modes().worldOf(ModeService.SKYBLOCK);
        World w = Bukkit.getWorld(name);
        if (w == null) {
            String err = core.worlds().create(ModeService.SKYBLOCK);
            if (err != null) {
                Msg.send(p, "<red>Welt: " + err);
                return;
            }
            w = Bukkit.getWorld(name);
            Msg.send(p, "<green>✔ Welt <white>" + name + " <green>angelegt (leer).");
        } else {
            Msg.send(p, "<gray>✔ Welt <white>" + name + " <gray>gibt es schon.");
        }
        if (w == null) return;
        // Skyblock wächst nicht mit dem Bauen: feste, große Grenze
        List<String> auto = new ArrayList<>(core.getConfig().getStringList("worldbuilder.auto-border"));
        if (auto.remove(name)) {
            core.getConfig().set("worldbuilder.auto-border", auto);
            core.saveConfig();
        }
        WorldBorder b = w.getWorldBorder();
        b.setCenter(0, 0);
        b.setSize(addon.getConfig().getInt("insel.weltgrenze", 50_000));
        Msg.send(p, "<green>✔ Weltgrenze <white>" + addon.getConfig().getInt("insel.weltgrenze", 50_000) + " <green>Blöcke.");
        // Glasplatte aus dem Anlegen entfernen
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (w.getBlockAt(dx, 63, dz).getType() == Material.GLASS) w.getBlockAt(dx, 63, dz).setType(Material.AIR, false);
        if (w.getBlockAt(0, TOP, 0).getType().isAir()) {
            int n = buildSpawn(w, addon);
            Msg.send(p, "<green>✔ Spawn-Insel gebaut <gray>(" + n + " Blöcke, Brücke zu den One-Blocks).");
        } else {
            Msg.send(p, "<gray>✔ Spawn-Insel steht schon.");
        }
        Location spawn = new Location(w, 0.5, TOP + 1, 0.5, 180, 0);
        w.setSpawnLocation(spawn);
        core.getConfig().set("modes." + ModeService.SKYBLOCK + ".spawn", Locs.serialize(spawn));
        core.saveConfig();
        Msg.send(p, "<green>✔ Spawn gesetzt <gray>(0, " + (TOP + 1) + ", 0).");
        int ob = addon.oneblock().setup(w);
        Msg.send(p, "<green>✔ " + ob + " One-Block-Plattformen.");
        int bio = Math.max(0, addon.getConfig().getInt("biom-inseln.anzahl", 13) - addon.biomes().all().size());
        if (bio > 0) {
            Msg.send(p, "<yellow>… baue " + bio + " Biom-Inseln (eine je Tick).");
            addon.biomes().generate(bio, () -> {
                if (p.isOnline()) Msg.send(p, "<green>✔ " + bio + " Biom-Inseln <gray>(Dschungel, Wüste, Pilzland, Kirschhain …).");
            });
        } else {
            Msg.send(p, "<gray>✔ Biom-Inseln gibt es schon (" + addon.biomes().all().size() + ").");
        }
        int want = Math.max(0, addon.getConfig().getInt("loot.einrichten-anzahl", 40) - addon.loot().all().size());
        if (want <= 0) {
            Msg.send(p, "<gray>✔ Loot-Inseln gibt es schon (" + addon.loot().all().size() + ").");
            finish(p);
            return;
        }
        Msg.send(p, "<yellow>… baue " + want + " Loot-Inseln (2 je Tick).");
        World fw = w;
        new BukkitRunnable() {
            int left = want;

            @Override
            public void run() {
                int n = Math.min(2, left);
                addon.loot().generate(fw, n);
                left -= n;
                if (left <= 0) {
                    cancel();
                    if (p.isOnline()) {
                        Msg.send(p, "<green>✔ " + want + " Loot-Inseln.");
                        finish(p);
                    }
                }
            }
        }.runTaskTimer(addon, 1L, 1L);
    }

    private static void finish(Player p) {
        Msg.send(p, "<gold><b>Skyblock ist eingerichtet.</b> <gray>Spieler kommen über die Modus-Wahl hinein und wählen beim ersten Mal ihre Insel.");
        Sfx.play(KubySkyblock.get().core(), p, Sfx.Kind.SUCCESS_BIG);
    }

    /** Schwebende Spawn-Insel: Steinplatz mit Laternen, Grasring mit Bäumen, Unterseite als Zapfen; Brücken zu den One-Blocks. */
    static int buildSpawn(World w, KubySkyblock addon) {
        Random r = new Random(4711);
        int n = 0, R = 14;
        for (int x = -R - 1; x <= R + 1; x++) {
            for (int z = -R - 1; z <= R + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                double edge = R + Math.sin(Math.atan2(z, x) * 5) * 0.8;
                if (d > edge) continue;
                // Oberfläche
                Material top = d <= 5.2 ? (d <= 1.5 ? Material.CHISELED_STONE_BRICKS : (Math.abs(x) + Math.abs(z)) % 3 == 0 ? Material.CRACKED_STONE_BRICKS
                        : Material.STONE_BRICKS) : d <= 6.2 ? Material.POLISHED_ANDESITE : Material.GRASS_BLOCK;
                w.getBlockAt(x, TOP, z).setType(top, false);
                n++;
                int dirt = 3;
                for (int y = 1; y <= dirt; y++) w.getBlockAt(x, TOP - y, z).setType(top == Material.GRASS_BLOCK ? Material.DIRT : Material.STONE, false);
                // Zapfen nach unten
                double depth = (edge - d) * 0.9 + r.nextDouble() * 1.5;
                for (int y = dirt + 1; y <= dirt + depth; y++) {
                    w.getBlockAt(x, TOP - y, z).setType(r.nextInt(9) == 0 ? Material.ANDESITE : Material.STONE, false);
                    n++;
                }
                if (top == Material.GRASS_BLOCK && d > 7 && r.nextInt(6) == 0) {
                    w.getBlockAt(x, TOP + 1, z).setType(r.nextBoolean() ? Material.SHORT_GRASS : r.nextBoolean() ? Material.POPPY : Material.DANDELION, false);
                }
            }
        }
        // Laternen an den vier Ecken des Platzes
        for (int[] c : new int[][]{{5, 5}, {-5, 5}, {5, -5}, {-5, -5}}) {
            w.getBlockAt(c[0], TOP + 1, c[1]).setType(Material.STONE_BRICK_WALL, false);
            w.getBlockAt(c[0], TOP + 2, c[1]).setType(Material.SPRUCE_FENCE, false);
            lantern(w, c[0], TOP + 3, c[1]);
        }
        // Bäume auf dem Grasring
        for (int[] c : new int[][]{{10, 4}, {-9, 7}, {7, 10}, {-10, -3}, {9, -8}}) {
            w.getBlockAt(c[0], TOP + 1, c[1]).setType(Material.AIR, false);
            w.generateTree(new Location(w, c[0], TOP + 1, c[1]), (c[0] + c[1]) % 2 == 0 ? TreeType.TREE : TreeType.BIRCH);
        }
        // Brücke nach Norden zu den One-Blocks (z = -60) und quer zwischen den Plattformen
        List<String> pos = addon.getConfig().getStringList("oneblock.positions");
        int oz = -60, minX = 0, maxX = 0;
        for (String s : pos) {
            String[] p = s.split(";");
            try {
                oz = Integer.parseInt(p[2].trim());
                minX = Math.min(minX, Integer.parseInt(p[0].trim()));
                maxX = Math.max(maxX, Integer.parseInt(p[0].trim()));
            } catch (RuntimeException ignored) {
            }
        }
        for (int z = -R; z >= oz + 2; z--) n += bridge(w, -1, 1, z, z, z % 8 == 0);
        for (int x = minX + 2; x <= maxX - 2; x++) n += bridge(w, x, x, oz - 1, oz + 1, x % 8 == 0 && Math.abs(x) > 3);
        return n;
    }

    private static int bridge(World w, int x0, int x1, int z0, int z1, boolean lamp) {
        int n = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!w.getBlockAt(x, TOP, z).getType().isAir()) continue;
                boolean side = x0 != x1 ? (x == x0 || x == x1) : (z == z0 || z == z1);
                w.getBlockAt(x, TOP, z).setType(side ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS, false);
                w.getBlockAt(x, TOP - 1, z).setType(Material.STONE_BRICK_SLAB, false);
                n++;
            }
        }
        if (lamp) {
            // Querreihe (über x): Laternen links und rechts; Längsstück (über z): Laternen davor und dahinter
            int[][] spots = z0 == z1 ? new int[][]{{x0 - 1, z0}, {x1 + 1, z0}} : new int[][]{{x0, z0 - 1}, {x0, z1 + 1}};
            for (int[] sp : spots) {
                w.getBlockAt(sp[0], TOP, sp[1]).setType(Material.STONE_BRICKS, false);
                w.getBlockAt(sp[0], TOP + 1, sp[1]).setType(Material.SPRUCE_FENCE, false);
                lantern(w, sp[0], TOP + 2, sp[1]);
            }
        }
        return n;
    }

    private static void lantern(World w, int x, int y, int z) {
        var b = w.getBlockAt(x, y, z);
        b.setType(Material.LANTERN, false);
        if (b.getBlockData() instanceof Lantern l) {
            l.setHanging(false);
            b.setBlockData(l, false);
        }
    }
}
