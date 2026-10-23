package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casino-Protokoll: jede Runde als Zeile in casino-log.txt (im Hintergrund geschrieben), die letzten Einträge im
 * Speicher für das Admin-Fenster und Summen je Spiel (eingesetzt, ausgezahlt) in casino-stats.yml.
 */
public final class CasinoLog {

    /** Summen eines Spiels in Chips. */
    public static final class Totals {
        public long rounds;
        public long staked;
        public long paid;
    }

    private static final int RECENT = 25;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KubyPlugins plugin;
    private final File logFile;
    private final File statsFile;
    private final Object writeLock = new Object();
    private final Deque<String> recent = new ArrayDeque<>();
    private final Map<String, Totals> totals = new LinkedHashMap<>();
    private final Map<UUID, Long> playerNet = new java.util.HashMap<>();
    private long jackpotsPaid;
    private long jackpotMoney;

    public CasinoLog(KubyPlugins plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "casino-log.txt");
        this.statsFile = new File(plugin.getDataFolder(), "casino-stats.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(statsFile);
        ConfigurationSection games = yml.getConfigurationSection("games");
        if (games != null) {
            for (String game : games.getKeys(false)) {
                Totals t = new Totals();
                t.rounds = games.getLong(game + ".rounds");
                t.staked = games.getLong(game + ".staked");
                t.paid = games.getLong(game + ".paid");
                totals.put(game, t);
            }
        }
        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                try {
                    playerNet.put(UUID.fromString(key), players.getLong(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        jackpotsPaid = yml.getLong("jackpot.count");
        jackpotMoney = yml.getLong("jackpot.money");
    }

    /** Hält eine Runde fest: Einsatz und Auszahlung in Chips. */
    public synchronized void record(String game, UUID player, long staked, long paid) {
        Totals t = totals.computeIfAbsent(game, g -> new Totals());
        t.rounds++;
        t.staked += staked;
        t.paid += paid;
        long net = paid - staked;
        playerNet.merge(player, net, Long::sum);
        write(String.format("%s | %s | %s | Einsatz %d | Auszahlung %d | %s%d",
                LocalDateTime.now().format(TIME), nameOf(player), game, staked, paid, net >= 0 ? "+" : "", net));
    }

    /** Hält eine Jackpot-Auszahlung fest (in Coins). */
    public synchronized void recordJackpot(UUID player, long money) {
        jackpotsPaid++;
        jackpotMoney += money;
        write(String.format("%s | %s | JACKPOT | %d Coins", LocalDateTime.now().format(TIME), nameOf(player), money));
    }

    public synchronized List<String> recent() {
        return new ArrayList<>(recent);
    }

    public synchronized Map<String, Totals> totals() {
        Map<String, Totals> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Totals> e : totals.entrySet()) {
            Totals t = new Totals();
            t.rounds = e.getValue().rounds;
            t.staked = e.getValue().staked;
            t.paid = e.getValue().paid;
            copy.put(e.getKey(), t);
        }
        return copy;
    }

    /** Rangliste "Am meisten Plus": Spieler nach Gewinn minus Einsatz (Chips), höchster zuerst. */
    public synchronized List<Map.Entry<UUID, Long>> top(int n) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>(playerNet.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<Map.Entry<UUID, Long>> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, list.size()); i++) out.add(Map.entry(list.get(i).getKey(), list.get(i).getValue()));
        return out;
    }

    /** Platz in der Rangliste (ab 1), 0 wenn der Spieler noch nicht gespielt hat. */
    public synchronized int rankOf(UUID id) {
        Long mine = playerNet.get(id);
        if (mine == null) return 0;
        int rank = 1;
        for (long v : playerNet.values()) if (v > mine) rank++;
        return rank;
    }

    public synchronized long netOf(UUID id) {
        return playerNet.getOrDefault(id, 0L);
    }

    public synchronized long jackpotsPaid() {
        return jackpotsPaid;
    }

    public synchronized long jackpotMoney() {
        return jackpotMoney;
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<String, Totals> e : totals.entrySet()) {
            out.set("games." + e.getKey() + ".rounds", e.getValue().rounds);
            out.set("games." + e.getKey() + ".staked", e.getValue().staked);
            out.set("games." + e.getKey() + ".paid", e.getValue().paid);
        }
        for (Map.Entry<UUID, Long> e : playerNet.entrySet()) out.set("players." + e.getKey(), e.getValue());
        out.set("jackpot.count", jackpotsPaid);
        out.set("jackpot.money", jackpotMoney);
        try {
            plugin.getDataFolder().mkdirs();
            out.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("casino-stats.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private void write(String line) {
        recent.addFirst(line);
        while (recent.size() > RECENT) recent.removeLast();
        if (!plugin.isEnabled()) {
            append(line);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> append(line));
    }

    private void append(String line) {
        synchronized (writeLock) {
            try {
                plugin.getDataFolder().mkdirs();
                Files.writeString(logFile.toPath(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().warning("casino-log.txt konnte nicht geschrieben werden: " + e.getMessage());
            }
        }
    }

    private static String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? id.toString() : name;
    }
}
