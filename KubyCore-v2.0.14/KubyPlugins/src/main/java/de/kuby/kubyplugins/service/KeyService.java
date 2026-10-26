package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Virtuelle Kisten-Schlüssel: sie liegen nicht im Inventar, sondern auf einem Konto je Modus (Survival-Schlüssel
 * öffnen nur Survival-Kisten). Arten stehen in keys.types ("id;Name;#Farbe[;Kurzname]"). Vergabe per /keys give (Konsole,
 * Juwelenshop, Votes) oder direkt (Spielzeit, Keyall). Gespeichert in keys.yml.
 */
public final class KeyService {

    /** Eine Schlüsselart: id, kurzer Name (wie die Kiste, z. B. "Episch"), Farbe, Kurzname in Listen (z. B. "Epic-Key"). */
    public record KeyType(String id, String name, String color, String label) {
        /** Voller Name, z. B. "Episch-Schlüssel". */
        public String full() {
            return name + "-Schlüssel";
        }

        /** Voller Name in seiner Farbe (MiniMessage). */
        public String colored() {
            return "<" + color + ">" + full() + "</" + color + ">";
        }
    }

    private final KubyPlugins plugin;
    private final File file;
    /** Schlüssel: modus|uuid, Wert: Schlüsselart -> Anzahl. */
    private final Map<String, Map<String, Integer>> keys = new HashMap<>();

    public KeyService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "keys.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(k);
            if (s == null) continue;
            Map<String, Integer> m = new LinkedHashMap<>();
            for (String t : s.getKeys(false)) if (s.getInt(t) > 0) m.put(t, s.getInt(t));
            keys.put(k.replace('_', '|'), m);
        }
    }

    /** Alle Schlüsselarten aus der Config, in ihrer Reihenfolge. */
    public Map<String, KeyType> types() {
        Map<String, KeyType> out = new LinkedHashMap<>();
        for (String raw : plugin.getConfig().getStringList("keys.types")) {
            String[] p = raw.split(";");
            if (p.length < 2) continue;
            String id = p[0].trim().toLowerCase(Locale.ROOT);
            String name = p[1].trim();
            out.put(id, new KeyType(id, name, p.length > 2 ? p[2].trim() : "#F8D35A",
                    p.length > 3 && !p[3].isBlank() ? p[3].trim() : name + "-Key"));
        }
        return out;
    }

    public KeyType type(String id) {
        return id == null ? null : types().get(id.toLowerCase(Locale.ROOT));
    }

    private static String k(String mode, UUID id) {
        return mode + "|" + id;
    }

    public synchronized int count(UUID id, String mode, String key) {
        return keys.getOrDefault(k(mode, id), Map.of()).getOrDefault(key, 0);
    }

    /** Alle Schlüssel eines Spielers in einem Modus (nur Arten mit Anzahl > 0). */
    public synchronized Map<String, Integer> all(UUID id, String mode) {
        return new LinkedHashMap<>(keys.getOrDefault(k(mode, id), Map.of()));
    }

    /** Schlüssel gutschreiben; ist der Spieler gerade in diesem Modus, bekommt er eine Nachricht. */
    public synchronized boolean give(UUID id, String mode, String key, int amount) {
        KeyType t = type(key);
        if (t == null || amount <= 0) return false;
        keys.computeIfAbsent(k(mode, id), x -> new LinkedHashMap<>()).merge(t.id(), amount, Integer::sum);
        save();
        Player p = Bukkit.getPlayer(id);
        if (p != null && mode.equals(plugin.modes().modeOf(id))) {
            Msg.send(p, "<gray>+" + amount + " " + t.colored() + " <dark_gray>(<click:run_command:'/keys'><gray>/keys</click>)");
            Sfx.play(plugin, p, Sfx.Kind.PING);
        }
        return true;
    }

    public synchronized boolean take(UUID id, String mode, String key, int amount) {
        Map<String, Integer> m = keys.get(k(mode, id));
        int have = m == null ? 0 : m.getOrDefault(key, 0);
        if (have < amount || amount <= 0) return false;
        if (have == amount) m.remove(key);
        else m.put(key, have - amount);
        save();
        return true;
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        keys.forEach((key, m) -> m.forEach((t, n) -> {
            if (n > 0) out.set(key.replace('|', '_') + "." + t, n);
        }));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("keys.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
