package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Drei One-Block-Plattformen. Der Block wird nach jedem Abbau durch einen zufälligen ersetzt.
 * Der abbauende Spieler bekommt den Loot des Blocks, der vorher dort lag.
 */
public final class OneBlockService {

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final Random rnd = new Random();

    public OneBlockService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
    }

    /** Erzeugt die drei Plattformen an den Positionen aus der config. */
    public int setup(World w) {
        List<String> pos = addon.getConfig().getStringList("oneblock.positions");
        int made = 0;
        for (String s : pos) {
            String[] p = s.split(";");
            if (p.length < 3) continue;
            int x = Integer.parseInt(p[0].trim()), y = Integer.parseInt(p[1].trim()), z = Integer.parseInt(p[2].trim());
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(x + dx, y - 1, z + dz).setType(Material.STONE, false);
            }
            w.getBlockAt(x, y, z).setType(randomBlock(), false);
            made++;
        }
        return made;
    }

    public boolean isOneBlock(Block b) {
        for (String s : addon.getConfig().getStringList("oneblock.positions")) {
            String[] p = s.split(";");
            if (p.length < 3) continue;
            if (b.getX() == Integer.parseInt(p[0].trim()) && b.getY() == Integer.parseInt(p[1].trim())
                    && b.getZ() == Integer.parseInt(p[2].trim())) return true;
        }
        return false;
    }

    /** Abbau: Loot geben, dann neuen zufälligen Block setzen. */
    public void harvest(Player p, Block b) {
        Material old = b.getType();
        ItemStack drop = new ItemStack(old, 1);
        var left = p.getInventory().addItem(drop);
        left.values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
        core.economy().deposit(p.getUniqueId(), addon.getConfig().getLong("oneblock.money", 5));
        core.stats().get(p.getUniqueId()).xp += addon.getConfig().getLong("oneblock.xp", 3);
        addon.progress().recordOneBlock(p);
        addon.progress().recordItem(p, old);
        b.setType(randomBlock(), false);
        Sfx.play(core, p, Sfx.Kind.CHIME);
    }

    /** Zufälliger Block aus der gewichteten Liste in der config. */
    public Material randomBlock() {
        ConfigurationSection s = addon.getConfig().getConfigurationSection("oneblock.blocks");
        List<String> names = new ArrayList<>();
        if (s != null) names.addAll(s.getKeys(false));
        if (names.isEmpty()) return Material.STONE;
        int total = 0;
        for (String n : names) total += Math.max(1, s.getInt(n, 1));
        int r = rnd.nextInt(total);
        for (String n : names) {
            r -= Math.max(1, s.getInt(n, 1));
            if (r < 0) {
                try { return Material.valueOf(n.toUpperCase()); } catch (IllegalArgumentException ex) { return Material.STONE; }
            }
        }
        return Material.STONE;
    }
}
