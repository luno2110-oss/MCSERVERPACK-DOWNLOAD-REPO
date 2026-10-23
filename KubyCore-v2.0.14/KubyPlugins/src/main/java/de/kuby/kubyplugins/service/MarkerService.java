package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Marker in der Hub-Welt: Text, der über einem Block schwebt. Nur Admins sehen ihn. */
public final class MarkerService {

    public static final List<String> COLORS = List.of("white", "red", "green", "blue", "gold");

    private record Marker(String world, double x, double y, double z, String color, String text) {}

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final List<Marker> markers = new ArrayList<>();

    public MarkerService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "editdata.yml");
        this.tag = new NamespacedKey(plugin, "kp_marker");
        for (String raw : org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file).getStringList("markers")) {
            String[] p = raw.split(";", 6);
            if (p.length < 6) continue;
            try {
                markers.add(new Marker(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                        Double.parseDouble(p[3]), p[4], p[5]));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public void create(Location block, String color, String text) {
        Marker m = new Marker(block.getWorld().getName(), block.getX() + 0.5, block.getY() + 1.4,
                block.getZ() + 0.5, color, Msg.escape(text));
        markers.add(m);
        spawn(m);
        save();
    }

    /** Entfernt den nächsten Marker im Umkreis von 3 Blöcken. Gibt false zurück, wenn keiner gefunden wurde. */
    public boolean removeNear(Player p) {
        Location at = p.getLocation();
        World w = at.getWorld();
        for (int i = 0; i < markers.size(); i++) {
            Marker m = markers.get(i);
            if (!m.world().equals(w.getName())) continue;
            if (at.distanceSquared(new Location(w, m.x(), m.y(), m.z())) <= 9) {
                markers.remove(i);
                for (TextDisplay d : w.getEntitiesByClass(TextDisplay.class)) {
                    if (d.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)
                            && d.getLocation().distanceSquared(new Location(w, m.x(), m.y(), m.z())) < 0.25) {
                        d.remove();
                    }
                }
                save();
                return true;
            }
        }
        return false;
    }

    /** Stellt alle Marker nach einem Neustart wieder her. */
    public void respawnAll() {
        for (Marker m : markers) {
            World w = Bukkit.getWorld(m.world());
            if (w == null) continue;
            for (TextDisplay d : w.getEntitiesByClass(TextDisplay.class)) {
                if (d.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) d.remove();
            }
        }
        for (Marker m : markers) spawn(m);
    }

    /** Verbirgt alle Marker vor Nicht-Admins in der Welt des Spielers. */
    public void applyVisibility(Player p) {
        if (p.hasPermission("kuby.admin")) return;
        for (TextDisplay d : p.getWorld().getEntitiesByClass(TextDisplay.class)) {
            if (d.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) p.hideEntity(plugin, d);
        }
    }

    private void spawn(Marker m) {
        World w = Bukkit.getWorld(m.world());
        if (w == null) return;
        Location loc = new Location(w, m.x(), m.y(), m.z());
        TextDisplay d = w.spawn(loc, TextDisplay.class, td -> {
            td.text(Msg.parse("<" + m.color() + ">" + m.text()));
            td.setBillboard(Display.Billboard.CENTER);
            td.setPersistent(true);
            td.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
        for (Player p : w.getPlayers()) {
            if (!p.hasPermission("kuby.admin")) p.hideEntity(plugin, d);
        }
    }

    private void save() {
        List<String> out = new ArrayList<>();
        for (Marker m : markers) {
            out.add(m.world() + ";" + m.x() + ";" + m.y() + ";" + m.z() + ";" + m.color() + ";" + m.text());
        }
        org.bukkit.configuration.file.YamlConfiguration yml = new org.bukkit.configuration.file.YamlConfiguration();
        yml.set("markers", out);
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("editdata.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
