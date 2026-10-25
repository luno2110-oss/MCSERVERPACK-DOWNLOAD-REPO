package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persönliche Einstellungen (für alle Modi gleich). Schlüssel und Standardwerte stehen in DEFAULTS.
 * Nur Abweichungen vom Standard werden gespeichert (settings.yml).
 */
public final class PlayerSettings {

    /** Schlüssel, Anzeigename und Standard. */
    public record Option(String key, String name, String desc, boolean def) {}

    public static final Map<String, Option> OPTIONS = new LinkedHashMap<>();

    static {
        add("msg", "Private Nachrichten", "Andere dürfen dir /msg schreiben (Freunde immer)", true);
        add("friends", "Freundesanfragen", "Andere dürfen dir Anfragen schicken", true);
        add("tpa", "Teleport-Anfragen", "Andere dürfen /tpa zu dir schicken (Freunde immer)", true);
        add("jump", "Nachspringen", "Freunde dürfen mit /friend jump zu dir springen", true);
        add("sounds", "Sounds", "Klick-, Erfolgs- und Hinweis-Sounds", true);
        add("scoreboard", "Scoreboard", "Die Anzeige rechts am Bildschirm", true);
        add("infobar", "Info-Leiste", "Die Leiste oben in Survival (Inhalt: /bossbar)", true);
        add("bar.money", "Leiste: Geld", "Coins und Juwelen", true);
        add("bar.job", "Leiste: Job", "Job, Level, Fortschritt, Limit", true);
        add("bar.place", "Leiste: Ort", "Spawn, Grundstück, Wildnis, Farmwelt", true);
        add("bar.quest", "Leiste: Quest", "Verfolgte Quest mit Fortschritt", true);
        add("bar.time", "Leiste: Uhrzeit", "Echte Uhrzeit (Berlin)", false);
    }

    private static void add(String key, String name, String desc, boolean def) {
        OPTIONS.put(key, new Option(key, name, desc, def));
    }

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Map<String, Boolean>> values = new HashMap<>();

    public PlayerSettings(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "settings.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(k);
            if (s == null) continue;
            try {
                Map<String, Boolean> m = new HashMap<>();
                for (String opt : s.getKeys(true)) if (s.isBoolean(opt)) m.put(opt, s.getBoolean(opt));
                values.put(UUID.fromString(k), m);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized boolean get(UUID id, String key) {
        Map<String, Boolean> m = values.get(id);
        if (m != null && m.containsKey(key)) return m.get(key);
        Option o = OPTIONS.get(key);
        return o == null || o.def();
    }

    public synchronized boolean toggle(UUID id, String key) {
        boolean now = !get(id, key);
        Option o = OPTIONS.get(key);
        Map<String, Boolean> m = values.computeIfAbsent(id, k -> new HashMap<>());
        if (o != null && now == o.def()) m.remove(key);
        else m.put(key, now);
        save();
        return now;
    }

    public synchronized void set(UUID id, String key, boolean value) {
        Option o = OPTIONS.get(key);
        Map<String, Boolean> m = values.computeIfAbsent(id, k -> new HashMap<>());
        if (o != null && value == o.def()) m.remove(key);
        else m.put(key, value);
        save();
    }

    /** Setzt alle Schlüssel mit diesem Anfang auf den Standard zurück. */
    public synchronized void reset(UUID id, String prefix) {
        Map<String, Boolean> m = values.get(id);
        if (m == null) return;
        m.keySet().removeIf(k -> k.startsWith(prefix));
        save();
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Boolean>> e : values.entrySet()) {
            for (Map.Entry<String, Boolean> v : e.getValue().entrySet()) out.set(e.getKey() + "." + v.getKey(), v.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("settings.yml: " + ex.getMessage());
        }
    }
}
