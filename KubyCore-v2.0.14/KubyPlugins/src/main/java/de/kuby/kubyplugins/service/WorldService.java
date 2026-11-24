package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Void-Welten für Hub, SMP und Survival. Start: 3x3 Glasplattform mit kleiner Worldborder.
 * Wird in der Nähe der Grenze gebaut, wächst die Grenze. Gilt auch für Schematics und WorldEdit.
 */
public final class WorldService {

    /** Leere Welt ohne Gelände, Höhlen oder Strukturen. */
    public static final class VoidGenerator extends ChunkGenerator {}

    private final KubyPlugins plugin;

    public WorldService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    /**
     * Welten der Modi nach einem Neustart wieder laden (v2.07): Paper lädt nur die Hauptwelten von selbst, eigene Welten
     * (Skyblock, mit /kp create angelegte) blieben danach weg – „man kann Skyblock nicht mehr einrichten“, weil
     * /kp skyblock einrichten die Welt nicht fand und der Ordner das Neu-Anlegen verhinderte.
     */
    public void loadModeWorlds() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (String m : List.of(ModeService.HUB, ModeService.SMP, ModeService.SURVIVAL, ModeService.SKYBLOCK)) names.add(plugin.modes().worldOf(m));
        names.addAll(plugin.getConfig().getStringList("worldbuilder.created"));
        for (String n : names) {
            if (Bukkit.getWorld(n) != null || !new File(new File(Bukkit.getWorldContainer(), n), "level.dat").exists()) continue;
            World w = load(n);
            plugin.getLogger().info(w != null ? "Welt " + n + " geladen" + (isVoid(n) ? " (leer)" : "") + "." : "Welt " + n + " ließ sich nicht laden.");
        }
    }

    /** Welt eines Modus laden, wenn sie auf der Platte liegt, aber nicht geladen ist; sonst die geladene oder null. */
    public World loadExisting(String mode) {
        String name = plugin.modes().worldOf(mode);
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        if (!new File(new File(Bukkit.getWorldContainer(), name), "level.dat").exists()) return null;
        return load(name);
    }

    /** Leere Welt (ohne Gelände nachladen)? Von uns angelegt, mit wachsender Grenze oder Skyblock. */
    private boolean isVoid(String name) {
        return plugin.getConfig().getStringList("worldbuilder.created").contains(name)
                || plugin.getConfig().getStringList("worldbuilder.auto-border").contains(name)
                || name.equals(plugin.modes().worldOf(ModeService.SKYBLOCK));
    }

    private World load(String name) {
        WorldCreator c = new WorldCreator(name).environment(World.Environment.NORMAL);
        if (isVoid(name)) c.generator(new VoidGenerator()).generateStructures(false);
        try {
            return c.createWorld();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Welt " + name + ": " + e.getMessage());
            return null;
        }
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public String create(String mode) {
        String name = plugin.modes().worldOf(mode);
        if (Bukkit.getWorld(name) != null) return "Die Welt " + name + " ist schon geladen.";
        if (new File(Bukkit.getWorldContainer(), name).exists()) {
            // liegt schon auf der Platte (z. B. nach einem Neustart nicht geladen): laden statt Fehler
            if (loadExisting(mode) != null) return null;
            return "Ein Ordner " + name + " existiert schon. Wähle mit /kp setworld einen anderen Namen.";
        }
        World w = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .generator(new VoidGenerator())
                .generateStructures(false)
                .createWorld();
        if (w == null) return "Die Welt konnte nicht erstellt werden.";

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(dx, 63, dz).setType(Material.GLASS, false);
            }
        }
        w.setSpawnLocation(0, 64, 0);

        WorldBorder b = w.getWorldBorder();
        b.setCenter(0.5, 0.5);
        b.setSize(startSize(mode));

        plugin.getConfig().set("modes." + mode + ".spawn", null);
        List<String> auto = new ArrayList<>(plugin.getConfig().getStringList("worldbuilder.auto-border"));
        if (!auto.contains(name)) auto.add(name);
        plugin.getConfig().set("worldbuilder.auto-border", auto);
        List<String> made = new ArrayList<>(plugin.getConfig().getStringList("worldbuilder.created"));
        if (!made.contains(name)) made.add(name);
        plugin.getConfig().set("worldbuilder.created", made);
        plugin.saveConfig();
        return null;
    }

    public boolean isAutoBorder(World w) {
        return w != null && plugin.getConfig().getStringList("worldbuilder.auto-border").contains(w.getName());
    }

    /** Prüft einen einzelnen gesetzten Block. */
    public void checkPlacement(World w, int x, int z) {
        if (!isAutoBorder(w)) return;
        WorldBorder b = w.getWorldBorder();
        if (edgeDistance(b, x + 0.5, z + 0.5) <= trigger()) expand(w, b);
    }

    /** Prüft den Randbereich nach einem Schematic- oder WorldEdit-Befehl. Gibt true zurück, wenn gewachsen wurde. */
    public boolean scanAfterBuild(World w) {
        if (!isAutoBorder(w)) return false;
        WorldBorder b = w.getWorldBorder();
        double half = b.getSize() / 2.0;
        Location c = b.getCenter();
        int minX = (int) Math.floor(c.getX() - half);
        int maxX = (int) Math.ceil(c.getX() + half) - 1;
        int minZ = (int) Math.floor(c.getZ() - half);
        int maxZ = (int) Math.ceil(c.getZ() + half) - 1;
        int t = trigger();
        boolean found = false;
        for (int x = minX; x <= maxX && !found; x++) {
            boolean edgeColumn = x - minX < t || maxX - x < t;
            if (edgeColumn) {
                for (int z = minZ; z <= maxZ && !found; z++) found = occupied(w, x, z);
            } else {
                for (int z = minZ; z < minZ + t && !found; z++) found = occupied(w, x, z);
                for (int z = maxZ - t + 1; z <= maxZ && !found; z++) found = occupied(w, x, z);
            }
        }
        if (found) expand(w, b);
        return found;
    }

    private boolean occupied(World w, int x, int z) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return false;
        return w.getHighestBlockYAt(x, z) > w.getMinHeight();
    }

    private boolean expand(World w, WorldBorder b) {
        double size = b.getSize();
        double max = plugin.getConfig().getDouble("worldbuilder.max-size", 1000);
        if (size >= max) return false;
        double step = plugin.getConfig().getDouble("worldbuilder.step", 16);
        b.setSize(Math.min(max, size + 2 * step));
        return true;
    }

    private double edgeDistance(WorldBorder b, double x, double z) {
        double half = b.getSize() / 2.0;
        Location c = b.getCenter();
        return Math.min(
                Math.min(x - (c.getX() - half), (c.getX() + half) - x),
                Math.min(z - (c.getZ() - half), (c.getZ() + half) - z));
    }

    private int trigger() {
        return plugin.getConfig().getInt("worldbuilder.trigger", 8);
    }

    private double startSize(String mode) {
        if (ModeService.HUB.equals(mode) && plugin.getConfig().contains("hub-settings.border-size")) {
            return plugin.getConfig().getDouble("hub-settings.border-size");
        }
        return plugin.getConfig().getDouble("worldbuilder.start-size", 16);
    }
}
