package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Juwelen: Premium-Währung. Kommen aus dem Store und Admin-Vergabe. */
public final class JewelService {

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();

    public JewelService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "jewels.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            balances.put(UUID.fromString(key), yml.getLong(key));
        }
    }

    public long balance(UUID id) {
        return balances.getOrDefault(id, 0L);
    }

    public synchronized void add(UUID id, long amount) {
        balances.merge(id, amount, Long::sum);
        save();
    }

    public synchronized boolean take(UUID id, long amount) {
        if (amount <= 0 || balance(id) < amount) return false;
        balances.put(id, balance(id) - amount);
        save();
        return true;
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        balances.forEach((id, v) -> out.set(id.toString(), v));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("jewels.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
