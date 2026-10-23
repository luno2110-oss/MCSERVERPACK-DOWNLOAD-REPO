package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** /sell, /worth und /sellmulti: Verkaufspreise aus der Config plus Multiplikator-Stufen pro Spieler. */
public final class SellService {

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Integer> levels = new ConcurrentHashMap<>();

    public SellService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sell.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            levels.put(UUID.fromString(key), yml.getInt(key + ".level"));
        }
    }

    public Map<Material, Long> worth() {
        Map<Material, Long> result = new LinkedHashMap<>();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("worth");
        if (s == null) return result;
        for (String key : s.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            if (m != null) result.put(m, s.getLong(key));
        }
        return result;
    }

    public int level(UUID id) {
        return levels.getOrDefault(id, 0);
    }

    public int maxLevel() {
        return plugin.getConfig().getInt("sellmulti.max-level", 10);
    }

    public double multiplier(UUID id) {
        return 1.0 + level(id) * plugin.getConfig().getDouble("sellmulti.step", 0.05);
    }

    public long upgradeCost(UUID id) {
        return plugin.getConfig().getLong("sellmulti.base-cost", 500) * (level(id) + 1);
    }

    /** Stufe kaufen. Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String upgrade(UUID id) {
        if (level(id) >= maxLevel()) return "Du hast bereits die Maximalstufe.";
        long cost = upgradeCost(id);
        if (!plugin.economy().withdraw(id, cost)) return "Nicht genug$ (" + Msg.coins(cost) + ").";
        levels.put(id, level(id) + 1);
        save();
        return null;
    }

    /** Verkauft alle Items mit Preis aus dem Hauptinventar. Gibt die Auszahlung zurück. */
    public synchronized long sellInventory(Player p) {
        Map<Material, Long> prices = worth();
        long sum = 0;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            Long price = prices.get(item.getType());
            if (price == null) continue;
            long unit = plugin.weekly().isMeta(item.getType()) ? price * 125 / 100 : price;
            sum += unit * item.getAmount();
            contents[i] = null;
        }
        p.getInventory().setStorageContents(contents);
        long payout = (long) Math.floor(sum * multiplier(p.getUniqueId()));
        if (payout > 0) plugin.economy().deposit(p.getUniqueId(), payout);
        return payout;
    }

    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        levels.forEach((id, lvl) -> yml.set(id + ".level", lvl));
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("sell.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
