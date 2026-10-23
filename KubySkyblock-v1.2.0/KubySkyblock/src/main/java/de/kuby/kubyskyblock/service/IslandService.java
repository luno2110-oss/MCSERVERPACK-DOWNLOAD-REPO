package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Eine Insel pro Spieler. Die Inseln liegen in einem Raster mit festem Abstand. */
public final class IslandService {

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<UUID, Integer> index = new HashMap<>();
    private final int spacing = 100;
    private final int borderSize = 50_000;

    public IslandService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "islands.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) index.put(UUID.fromString(k), yml.getInt(k));
        World w = world();
        if (w != null) {
            WorldBorder b = w.getWorldBorder();
            b.setCenter(0, 0);
            b.setSize(borderSize);
        }
    }

    public World world() {
        return Bukkit.getWorld(core.modes().worldOf(ModeService.SKYBLOCK));
    }

    /** Legt beim ersten Betreten eine Insel an und teleportiert den Spieler dorthin. */
    public void ensureIsland(Player p) {
        World w = world();
        if (w == null) {
            p.sendMessage("Die Skyblock-Welt ist nicht eingerichtet.");
            return;
        }
        if (!index.containsKey(p.getUniqueId())) {
            int n = index.size();
            index.put(p.getUniqueId(), n);
            Location origin = originOf(n);
            if (addon.templates().hasTemplate()) {
                buildTemplate(w, origin);
            } else {
                build(w, origin);
            }
            save();
        }
        Location origin = originOf(index.get(p.getUniqueId()));
        Location spawn;
        if (addon.templates().hasTemplate()) {
            int[] sp = addon.templates().spawn();
            spawn = origin.clone().add(sp[0] + 0.5, sp[1], sp[2] + 0.5);
        } else {
            spawn = origin.clone().add(0.5, 2, 0.5);
        }
        spawn.setWorld(w);
        p.teleportAsync(spawn);
    }

    public Location originOf(int n) {
        World w = world();
        int x = (n % 500) * spacing;
        int z = (n / 500) * spacing;
        return new Location(w, x, 100, z);
    }

    /** Baut die gespeicherte Vorlage an die Insel-Position. */
    private void buildTemplate(World w, Location origin) {
        int ox = origin.getBlockX(), oy = origin.getBlockY(), oz = origin.getBlockZ();
        for (var b : addon.templates().blocks()) {
            w.getBlockAt(ox + b.x(), oy + b.y(), oz + b.z()).setType(b.type(), false);
        }
    }

    public int count() {
        return index.size();
    }

    /** Standard-Insel: 5x5 Erde mit Grasdecke, Kiste mit Startwerkzeug. */
    private void build(World w, Location origin) {
        int ox = origin.getBlockX(), oy = origin.getBlockY(), oz = origin.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(ox + dx, oy - 1, oz + dz).setType(Material.DIRT, false);
                w.getBlockAt(ox + dx, oy - 2, oz + dz).setType(Material.DIRT, false);
                w.getBlockAt(ox + dx, oy, oz + dz).setType(Material.GRASS_BLOCK, false);
            }
        }
        w.getBlockAt(ox - 1, oy + 1, oz - 1).setType(Material.CHEST, false);
        if (w.getBlockAt(ox - 1, oy + 1, oz - 1).getState() instanceof Chest chest) {
            chest.getBlockInventory().addItem(new ItemStack(Material.ICE, 2), new ItemStack(Material.LAVA_BUCKET, 1),
                    new ItemStack(Material.OAK_SAPLING, 1), new ItemStack(Material.BONE_MEAL, 3));
        }
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        index.forEach((id, n) -> out.set(id.toString(), n));
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("islands.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Liegt die Position auf der Insel des Spielers? */
    public boolean isIslandOwner(UUID id, Location l) {
        Integer n = index.get(id);
        if (n == null) return false;
        Location o = originOf(n);
        return Math.abs(l.getBlockX() - o.getBlockX()) <= 12 && Math.abs(l.getBlockZ() - o.getBlockZ()) <= 12;
    }
}
