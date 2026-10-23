package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Geschützte Bereiche. Owner "-" heißt: nur Admins dürfen hier bauen. Sonst ist es die UUID des Besitzers. */
public final class RegionService {

    public record Region(String id, String world, int x1, int y1, int z1, int x2, int y2, int z2, String owner) {
        public boolean contains(Location l) {
            if (l == null || l.getWorld() == null || !l.getWorld().getName().equals(world)) return false;
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final List<Region> regions = new ArrayList<>();

    public RegionService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regions.yml");
        for (String raw : YamlConfiguration.loadConfiguration(file).getStringList("regions")) {
            String[] p = raw.split(";", 9);
            if (p.length < 9) continue;
            try {
                regions.add(new Region(p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]),
                        Integer.parseInt(p[5]), Integer.parseInt(p[6]), Integer.parseInt(p[7]), p[8]));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /** Gibt null bei Erfolg zurück. */
    public synchronized String create(String id, Location a, Location b, String owner) {
        if (!id.matches("[a-z0-9_-]{1,24}")) return "Ungültige Region-ID. Erlaubt: Kleinbuchstaben, Ziffern, - und _.";
        if (find(id) != null) return "Eine Region mit der ID " + id + " gibt es schon.";
        if (a == null || b == null) return "Setze erst beide Ecken mit der Blaze Rod im Bearbeitungsmodus.";
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return "Beide Ecken müssen in derselben Welt liegen.";
        }
        regions.add(new Region(id, a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()),
                owner));
        save();
        return null;
    }

    public synchronized boolean delete(String id) {
        boolean removed = regions.removeIf(r -> r.id().equals(id.toLowerCase(Locale.ROOT)));
        if (removed) save();
        return removed;
    }

    public synchronized List<Region> all() {
        return new ArrayList<>(regions);
    }

    public synchronized Region find(String id) {
        for (Region r : regions) if (r.id().equals(id.toLowerCase(Locale.ROOT))) return r;
        return null;
    }

    public synchronized Region at(Location l) {
        for (Region r : regions) if (r.contains(l)) return r;
        return null;
    }

    /** True, wenn der Spieler hier nichts verändern darf. */
    public boolean protects(Player p, Location l) {
        if (p.hasPermission("kuby.admin")) return false;
        Region r = at(l);
        if (r == null) return false;
        if (plugin.isTrusted(r.owner(), p.getUniqueId())) return false;
        return !p.getUniqueId().toString().equals(r.owner());
    }

    /** True, wenn der Ort irgendwo geschützt ist (z. B. für Explosionen). */
    public boolean protectedAt(Location l) {
        return at(l) != null;
    }

    public synchronized void save() {
        List<String> out = new ArrayList<>();
        for (Region r : regions) {
            out.add(r.id() + ";" + r.world() + ";" + r.x1() + ";" + r.y1() + ";" + r.z1() + ";"
                    + r.x2() + ";" + r.y2() + ";" + r.z2() + ";" + r.owner());
        }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("regions", out);
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("regions.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
