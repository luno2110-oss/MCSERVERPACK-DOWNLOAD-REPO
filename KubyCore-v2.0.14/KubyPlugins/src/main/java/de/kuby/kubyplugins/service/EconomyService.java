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
        long old = balance(id);
        balances.put(id, Math.max(0, amount));
        save();
        log(id, Math.max(0, amount) - old, Math.max(0, amount), "Admin: Stand gesetzt");
    }

    /** Ins Wirtschafts-Protokoll (Modus = aktueller Modus des Spielers). */
    private void log(UUID id, long delta, long balance, String label) {
        EconomyLog l = plugin.economyLog();
        if (l == null || delta == 0) return;
        String mode = plugin.modes() == null ? "?" : plugin.modes().modeOf(id);
        if (label == null) l.record(id, mode, delta, balance);
        else EconomyLog.with(label, () -> l.record(id, mode, delta, balance));
    }

    /** Gutschrift: nur positive Beträge, ohne Überlauf (sättigt bei Long.MAX_VALUE). Sehr große Einzelbeträge stehen
     *  im Server-Log (economy.log-ab, Standard 100 Mio.), damit das Team Auffälliges sieht. */
    public synchronized void deposit(UUID id, long amount) {
        if (amount <= 0) return;
        long cur = balance(id);
        long next = cur + amount;
        if (next < cur) next = Long.MAX_VALUE;
        balances.put(id, next);
        log(id, next - cur, next, null);
        long warn = plugin.getConfig().getLong("economy.log-ab", 100_000_000L);
        if (warn > 0 && amount >= warn) {
            String name = org.bukkit.Bukkit.getOfflinePlayer(id).getName();
            plugin.getLogger().warning("[Wirtschaft] Große Gutschrift: " + (name == null ? id : name) + " +" + amount + " (neu " + next + ")");
        }
    }

    public synchronized boolean withdraw(UUID id, long amount) {
        long current = balance(id);
        if (amount <= 0 || current < amount) return false;
        balances.put(id, current - amount);
        log(id, -amount, current - amount, null);
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
