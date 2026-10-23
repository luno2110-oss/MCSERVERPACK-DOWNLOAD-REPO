package de.kp.jumprun.stats;

import de.kp.jumprun.KPJumpRun;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Speichert pro Arena und Spieler: beste Zeit, Abschlüsse und Fails (stats.yml).
 * Grundlage für die Bestenliste in Teil 4.
 */
public final class StatsManager {

    public static final class Record {
        private String name = "";
        private long bestMillis = -1;
        private int completions;
        private int fails;

        public String name() {
            return name;
        }

        public long bestMillis() {
            return bestMillis;
        }

        public int completions() {
            return completions;
        }

        public int fails() {
            return fails;
        }

        public boolean hasTime() {
            return bestMillis >= 0;
        }
    }

    private final KPJumpRun plugin;
    private final File file;
    private final Map<String, Map<UUID, Record>> data = new HashMap<>();

    public StatsManager(KPJumpRun plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public void load() {
        data.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("stats");
        if (root == null) {
            return;
        }

        for (String arena : root.getKeys(false)) {
            ConfigurationSection arenaSection = root.getConfigurationSection(arena);
            if (arenaSection == null) {
                continue;
            }

            Map<UUID, Record> records = new HashMap<>();
            for (String uuid : arenaSection.getKeys(false)) {
                ConfigurationSection s = arenaSection.getConfigurationSection(uuid);
                if (s == null) {
                    continue;
                }
                Record record = new Record();
                record.name = s.getString("name", "");
                record.bestMillis = s.getLong("best", -1);
                record.completions = s.getInt("completions", 0);
                record.fails = s.getInt("fails", 0);
                records.put(UUID.fromString(uuid), record);
            }
            data.put(arena, records);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Map<UUID, Record>> arenaEntry : data.entrySet()) {
            for (Map.Entry<UUID, Record> entry : arenaEntry.getValue().entrySet()) {
                String base = "stats." + arenaEntry.getKey() + "." + entry.getKey() + ".";
                Record r = entry.getValue();
                yml.set(base + "name", r.name);
                yml.set(base + "best", r.bestMillis);
                yml.set(base + "completions", r.completions);
                yml.set(base + "fails", r.fails);
            }
        }

        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("stats.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public Record record(String arena, UUID uuid, String name) {
        Record r = data.computeIfAbsent(arena, k -> new HashMap<>())
                .computeIfAbsent(uuid, k -> new Record());
        r.name = name;
        return r;
    }

    public void recordFinish(String arena, UUID uuid, String name, long millis) {
        Record r = record(arena, uuid, name);
        r.completions++;
        if (!r.hasTime() || millis < r.bestMillis) {
            r.bestMillis = millis;
        }
        save();
    }

    public void recordFail(String arena, UUID uuid, String name) {
        Record r = record(arena, uuid, name);
        r.fails++;
        save();
    }

    /** Alle Spieler mit einer Zeit, sortiert nach bester Zeit (schnellste zuerst). */
    public List<Map.Entry<UUID, Record>> ranking(String arena) {
        List<Map.Entry<UUID, Record>> list = new ArrayList<>();
        for (Map.Entry<UUID, Record> entry : data.getOrDefault(arena, Collections.emptyMap()).entrySet()) {
            if (entry.getValue().hasTime()) {
                list.add(entry);
            }
        }
        list.sort(Comparator.comparingLong(e -> e.getValue().bestMillis));
        return list;
    }

    public Map<UUID, Record> records(String arena) {
        return data.getOrDefault(arena, Collections.emptyMap());
    }
}
