package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EconomyService {

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();

    public EconomyService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            balances.put(UUID.fromString(key), yml.getLong(key));
        }
    }

    public long balance(UUID id) {
        return balances.getOrDefault(id, 0L);
    }

    /** Admin: Kontostand direkt setzen. */
    public synchronized void set(UUID id, long amount) {
        balances.put(id, Math.max(0, amount));
        save();
    }

    public synchronized void deposit(UUID id, long amount) {
        balances.merge(id, amount, Long::sum);
    }

    public synchronized boolean withdraw(UUID id, long amount) {
        long current = balance(id);
        if (amount <= 0 || current < amount) return false;
        balances.put(id, current - amount);
        return true;
    }

    public List<Map.Entry<UUID, Long>> top(int n) {
        return balances.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(n)
                .toList();
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        balances.forEach((id, value) -> yml.set(id.toString(), value));
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("economy.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
