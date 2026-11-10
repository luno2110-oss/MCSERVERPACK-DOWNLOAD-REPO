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
    /** Verkaufte Items seit dem Start (Profil „Verkauft“). */
    private final Map<UUID, Long> sold = new ConcurrentHashMap<>();

    public SellService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sell.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            levels.put(UUID.fromString(key), yml.getInt(key + ".level"));
            long n = yml.getLong(key + ".verkauft");
            if (n > 0) sold.put(UUID.fromString(key), n);
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

    public long sold(UUID id) {
        return sold.getOrDefault(id, 0L);
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

    /** Verkaufswert eines Stücks (mit Wochen-Meta und Verkaufs-Stufe), 0 = nicht verkaufbar. */
    public long unitPrice(UUID id, ItemStack item) {
        Long price = worth().get(item.getType());
        if (price == null || price <= 0) return 0;
        long unit = plugin.weekly().isMeta(item.getType()) ? price * 125 / 100 : price;
        return (long) Math.floor(unit * multiplier(id));
    }

    /** Verkauft alle Items mit Preis aus dem Hauptinventar. Gibt die Auszahlung zurück. */
    public synchronized long sellInventory(Player p) {
        Map<Material, Long> prices = worth();
        long sum = 0, items = 0;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            Long price = prices.get(item.getType());
            if (price == null) continue;
            if (special(item)) continue;
            long unit = plugin.weekly().isMeta(item.getType()) ? price * 125 / 100 : price;
            sum += unit * item.getAmount();
            items += item.getAmount();
            if (plugin.trading() != null) plugin.trading().demandMaterial(item.getType(), item.getAmount(), -1);
            contents[i] = null;
        }
        p.getInventory().setStorageContents(contents);
        long payout = (long) Math.floor(sum * multiplier(p.getUniqueId()));
        if (payout > 0) plugin.economy().deposit(p.getUniqueId(), payout);
        if (items > 0) {
            sold.merge(p.getUniqueId(), items, Long::sum);
            save();
        }
        return payout;
    }

    /** Eigene Sonder-Items (eigenes Modell oder Plugin-Daten, z. B. Generatoren, Möbel, Kit-Items) und Behälter mit
     *  Inhalt werden nie zum Material-Preis verkauft (sonst wären sie oder ihr Inhalt verloren). */
    public static boolean special(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        if (meta.hasItemModel()) return true;
        for (org.bukkit.NamespacedKey k : meta.getPersistentDataContainer().getKeys()) {
            if (!k.getKey().equals("item_value")) return true;   // die Wert-Anzeige (ItemValueService) macht kein Sonder-Item
        }
        if (meta instanceof org.bukkit.inventory.meta.BlockStateMeta bsm && bsm.hasBlockState()
                && bsm.getBlockState() instanceof org.bukkit.block.Container box && !box.getInventory().isEmpty()) return true;
        return meta instanceof org.bukkit.inventory.meta.BundleMeta bm && bm.hasItems();
    }

    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        levels.forEach((id, lvl) -> yml.set(id + ".level", lvl));
        sold.forEach((id, n) -> yml.set(id + ".verkauft", n));
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("sell.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
