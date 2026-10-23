package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Erfolge mit Fortschritt. Definiert in achievements.list der config.yml:
 * id;Name;Wert;Ziel;Juwelen;MATERIAL;Beschreibung. Der Wert kommt aus den Stat-Quellen (registerStat),
 * die Kern und Addons bereitstellen, z. B. playtime, votes, kills, casino_plus, fish_caught, angel_level, claims.
 * Geprüft wird jede Minute für alle Online-Spieler und beim Öffnen des Menüs.
 */
public final class AchievementService {

    public record Achievement(String id, String name, String stat, long target, long jewels, Material icon, String desc) {}

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Set<String>> done = new HashMap<>();

    public AchievementService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "achievements.yml");
        ConfigurationSection s = YamlConfiguration.loadConfiguration(file).getConfigurationSection("done");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                try {
                    done.put(UUID.fromString(key), new HashSet<>(s.getStringList(key)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) check(p);
        }, 1200L, 1200L);
    }

    public List<Achievement> all() {
        List<Achievement> out = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("achievements.list")) {
            String[] p = raw.split(";", 7);
            if (p.length < 7) continue;
            try {
                Material icon = Material.matchMaterial(p[5]);
                out.add(new Achievement(p[0], p[1], p[2], Long.parseLong(p[3]), Long.parseLong(p[4]),
                        icon == null ? Material.PAPER : icon, p[6]));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public long progress(UUID id, Achievement a) {
        return plugin.statValue(a.stat(), id);
    }

    public synchronized boolean isDone(UUID id, String achievement) {
        return done.getOrDefault(id, Set.of()).contains(achievement);
    }

    public synchronized int doneCount(UUID id) {
        return done.getOrDefault(id, Set.of()).size();
    }

    /** Schaltet alle erreichten Erfolge frei und vergibt die Belohnung. */
    public synchronized void check(Player p) {
        UUID id = p.getUniqueId();
        boolean changed = false;
        for (Achievement a : all()) {
            if (isDone(id, a.id())) continue;
            if (progress(id, a) < a.target()) continue;
            done.computeIfAbsent(id, k -> new HashSet<>()).add(a.id());
            if (a.jewels() > 0) plugin.jewels().add(id, a.jewels());
            Msg.send(p, "<gold><b>Erfolg freigeschaltet:</b> <white>" + a.name()
                    + (a.jewels() > 0 ? " <light_purple>+" + a.jewels() + " Juwelen" : ""));
            Sfx.play(plugin, p, Sfx.Kind.CHIME);
            changed = true;
        }
        if (changed) save();
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> e : done.entrySet()) out.set("done." + e.getKey(), new ArrayList<>(e.getValue()));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("achievements.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
