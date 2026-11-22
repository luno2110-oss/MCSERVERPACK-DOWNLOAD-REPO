package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abend-Booster: erhöht XP und Daily-Belohnungen zwischen start-hour und end-hour (Berliner Zeit).
 *
 * <p>Dazu persönliche Booster (v2.05, aus dem XP-Shop): je Art („xp“ für XP-Quellen, „sell“ für /sell und das
 * Verkaufsmenü) ein Faktor mit Ablaufzeit. Ein zweiter Kauf derselben Art verlängert und nimmt den höheren Faktor.
 * Gespeichert in boosters.yml, die Zeit läuft auch offline weiter.
 */
public final class BoosterService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    public record Personal(double mult, long until) {
        public boolean active() {
            return until > System.currentTimeMillis();
        }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Map<String, Personal>> personal = new HashMap<>();

    public BoosterService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boosters.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(key);
            if (s == null) continue;
            try {
                UUID id = UUID.fromString(key);
                for (String kind : s.getKeys(false)) {
                    Personal p = new Personal(s.getDouble(kind + ".mult", 1), s.getLong(kind + ".until", 0));
                    if (p.active()) personal.computeIfAbsent(id, k -> new HashMap<>()).put(kind, p);
                }
            } catch (IllegalArgumentException ignored) {
                // kaputte Zeile überspringen
            }
        }
    }

    public boolean active() {
        int h = ZonedDateTime.now(ZONE).getHour();
        return h >= plugin.getConfig().getInt("booster.start-hour", 18)
                && h < plugin.getConfig().getInt("booster.end-hour", 24);
    }

    public double multiplier() {
        return active() ? plugin.getConfig().getDouble("booster.multiplier", 1.5) : 1.0;
    }

    // =================================================================================================================
    // Persönliche Booster
    // =================================================================================================================

    /** Booster geben oder verlängern (Minuten werden an die Restzeit gehängt, der höhere Faktor gilt). */
    public synchronized void grant(UUID id, String kind, double mult, int minutes) {
        Map<String, Personal> m = personal.computeIfAbsent(id, k -> new HashMap<>());
        Personal old = m.get(kind);
        long now = System.currentTimeMillis();
        long from = old != null && old.active() ? old.until() : now;
        double f = old != null && old.active() ? Math.max(old.mult(), mult) : mult;
        m.put(kind, new Personal(f, from + minutes * 60_000L));
        save();
    }

    /** Laufender persönlicher Faktor dieser Art, sonst 1. */
    public synchronized double personal(UUID id, String kind) {
        Map<String, Personal> m = personal.get(id);
        if (m == null) return 1.0;
        Personal p = m.get(kind);
        return p != null && p.active() ? p.mult() : 1.0;
    }

    /** Restminuten des persönlichen Boosters (0 = keiner). */
    public synchronized long minutesLeft(UUID id, String kind) {
        Map<String, Personal> m = personal.get(id);
        Personal p = m == null ? null : m.get(kind);
        return p == null || !p.active() ? 0 : (p.until() - System.currentTimeMillis() + 59_999) / 60_000;
    }

    /** XP-Faktor: Abend-Booster mal persönlicher XP-Booster. */
    public double xpMultiplier(UUID id) {
        return multiplier() * personal(id, "xp");
    }

    /** Verkaufs-Faktor des persönlichen Verkaufs-Boosters (ohne Booster 1). */
    public double sellMultiplier(UUID id) {
        return personal(id, "sell");
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        personal.forEach((id, m) -> m.forEach((kind, p) -> {
            if (!p.active()) return;
            yml.set(id + "." + kind + ".mult", p.mult());
            yml.set(id + "." + kind + ".until", p.until());
        }));
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("boosters.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
