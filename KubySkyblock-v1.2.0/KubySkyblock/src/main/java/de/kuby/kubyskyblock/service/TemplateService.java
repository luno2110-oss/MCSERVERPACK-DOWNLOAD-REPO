package de.kuby.kubyskyblock.service;

import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Insel-Vorlage aus zwei markierten Ecken. Gespeichert wird relativ zur kleinsten Ecke,
 * plus der Standposition des Admins beim Speichern (Spawn auf der Insel).
 */
public final class TemplateService {

    public record Block3(int x, int y, int z, Material type) {}

    private final KubySkyblock addon;
    private final File file;
    private List<Block3> blocks = new ArrayList<>();
    private int[] spawn = null;

    public TemplateService(KubySkyblock addon) {
        this.addon = addon;
        this.file = new File(addon.getDataFolder(), "template.yml");
        load();
    }

    public boolean hasTemplate() {
        return !blocks.isEmpty() && spawn != null;
    }

    public List<Block3> blocks() { return blocks; }
    public int[] spawn() { return spawn; }

    /** Speichert alle Blöcke zwischen den zwei Ecken. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public String save(Location a, Location b, Location standing) {
        World w = a.getWorld();
        if (w == null || !w.equals(b.getWorld())) return "Beide Ecken müssen in der Skyblock-Welt liegen.";
        int minX = Math.min(a.getBlockX(), b.getBlockX()), maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY()), maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ()), maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 200_000) return "Die Vorlage ist zu groß (max. 200.000 Blöcke).";
        List<Block3> out = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block bl = w.getBlockAt(x, y, z);
                    if (bl.getType().isAir()) continue;
                    out.add(new Block3(x - minX, y - minY, z - minZ, bl.getType()));
                }
            }
        }
        blocks = out;
        spawn = new int[] {standing.getBlockX() - minX, standing.getBlockY() - minY, standing.getBlockZ() - minZ};
        write();
        return null;
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        List<String> list = yml.getStringList("blocks");
        for (String s : list) {
            String[] p = s.split(";");
            if (p.length < 4) continue;
            try {
                blocks.add(new Block3(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Material.valueOf(p[3])));
            } catch (IllegalArgumentException ignored) { }
        }
        List<Integer> sp = yml.getIntegerList("spawn");
        if (sp.size() == 3) spawn = new int[] {sp.get(0), sp.get(1), sp.get(2)};
    }

    private void write() {
        YamlConfiguration out = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (Block3 b : blocks) list.add(b.x() + ";" + b.y() + ";" + b.z() + ";" + b.type().name());
        out.set("blocks", list);
        out.set("spawn", List.of(spawn[0], spawn[1], spawn[2]));
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("template.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
