package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Kills, Tode, Trophäen, XP und Ligen. */
public final class StatsService {

    public static final class Stats {
        public int kills;
        public int deaths;
        public int trophies = 100;
        public long xp;
        public long firstJoin;
        public final java.util.Set<String> claimed = new java.util.HashSet<>();
    }

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Stats> data = new ConcurrentHashMap<>();

    public StatsService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            Stats s = new Stats();
            s.kills = yml.getInt(key + ".kills");
            s.deaths = yml.getInt(key + ".deaths");
            s.trophies = yml.getInt(key + ".trophies");
            s.xp = yml.getLong(key + ".xp");
            s.firstJoin = yml.getLong(key + ".firstJoin");
            for (String c : yml.getStringList(key + ".claimed")) s.claimed.add(c);
            data.put(UUID.fromString(key), s);
        }
    }

    public Stats get(UUID id) {
        return data.computeIfAbsent(id, k -> new Stats());
    }

    public static String league(int trophies) {
        return League.of(trophies).label();
    }

    public List<Map.Entry<UUID, Stats>> topTrophies(int n) {
        List<Map.Entry<UUID, Stats>> list = new ArrayList<>(data.entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<UUID, Stats> e) -> e.getValue().trophies).reversed());
        return list.subList(0, Math.min(n, list.size()));
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        data.forEach((id, s) -> {
            out.set(id + ".kills", s.kills);
            out.set(id + ".deaths", s.deaths);
            out.set(id + ".trophies", s.trophies);
            out.set(id + ".xp", s.xp);
            out.set(id + ".firstJoin", s.firstJoin);
            out.set(id + ".claimed", new java.util.ArrayList<>(s.claimed));
        });
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("stats.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
