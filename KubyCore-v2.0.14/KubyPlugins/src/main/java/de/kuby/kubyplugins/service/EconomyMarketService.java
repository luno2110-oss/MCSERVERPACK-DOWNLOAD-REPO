package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Angebot und Nachfrage für die Shop-Sektionen Blöcke, Essen, Redstone und Dekoration.
 * Täglich um 0 Uhr (Berliner Zeit) wird der Preis anhand der Verkäufe angepasst:
 * mehr verkauft als der Richtwert = Preis steigt, weniger = Preis sinkt, höchstens 10 % pro Tag.
 * Unter der Preisgrenze wird das Item deaktiviert und sein Topf halbiert (Verlustrisiko für Anteilseigner).
 * Spieler können Anteile kaufen und verkaufen. Jeder Verkauf fließt mit Steuer in den Topf.
 */
public final class EconomyMarketService implements ModeData {

    public enum Rarity { FREQUENT, COMMON, RARE, VERY_RARE, UNIQUE }

    public static final List<String> DYNAMIC = List.of("blocks", "food", "redstone", "deco");
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final double MAX_STEP = 0.10;

    private final KubyPlugins plugin;
    private final File file;
    private final Map<String, Double> factor = new HashMap<>();
    private final Map<String, Boolean> active = new HashMap<>();
    private final Map<String, Double> pool = new HashMap<>();
    private final Map<String, Integer> sold = new HashMap<>();
    private final Map<String, Map<UUID, Double>> shares = new HashMap<>();
    private final Map<String, Map<UUID, Long>> cost = new HashMap<>();
    private String lastRun = "";

    public EconomyMarketService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "investments.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        lastRun = yml.getString("lastRun", "");
        for (String id : yml.getConfigurationSection("items") == null ? List.<String>of()
                : yml.getConfigurationSection("items").getKeys(false)) {
            String k = "items." + id + ".";
            factor.put(id, yml.getDouble(k + "factor", 1.0));
            active.put(id, yml.getBoolean(k + "active", true));
            pool.put(id, yml.getDouble(k + "pool", 0.0));
            sold.put(id, yml.getInt(k + "sold", 0));
            var sh = yml.getConfigurationSection(k + "shares");
            if (sh != null) {
                for (String u : sh.getKeys(false)) {
                    shares.computeIfAbsent(id, x -> new HashMap<>()).put(UUID.fromString(u), sh.getDouble(u));
                }
            }
            var co = yml.getConfigurationSection(k + "cost");
            if (co != null) {
                for (String u : co.getKeys(false)) {
                    cost.computeIfAbsent(id, x -> new HashMap<>()).put(UUID.fromString(u), co.getLong(u));
                }
            }
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, 1200L, 1200L);
    }

    // ---------- Einstellungen ----------

    public Rarity rarityOf(Material m) {
        for (Rarity r : Rarity.values()) {
            List<String> list = plugin.getConfig().getStringList("economy.rarities." + key(r));
            if (list.contains(m.name())) return r;
        }
        return Rarity.COMMON;
    }

    private static String key(Rarity r) {
        return r.name().toLowerCase(java.util.Locale.ROOT);
    }

    private long target(Rarity r) {
        return plugin.getConfig().getLong("economy.target." + key(r), 100);
    }

    private long minPrice(Rarity r) {
        return plugin.getConfig().getLong("economy.min-price." + key(r), 1);
    }

    // ---------- Preise ----------

    public synchronized double factorOf(String id) {
        return factor.getOrDefault(id, 1.0);
    }

    public synchronized boolean isActive(String id) {
        return active.getOrDefault(id, true);
    }

    /** Aktueller Preis eines Shop-Items. */
    public synchronized long priceOf(ShopService.Item it) {
        return Math.max(1, Math.round(it.price() * factorOf(it.id())));
    }

    /** Verbuchung eines Kaufs: Stückzahl für den Richtwert, Erlös abzüglich Steuer in den Topf. */
    public synchronized void recordSale(String id, int units, long paid) {
        sold.merge(id, units, Integer::sum);
        double tax = plugin.getConfig().getDouble("economy.tax-percent", 9) / 100.0;
        pool.merge(id, paid * (1.0 - tax), Double::sum);
        save();
    }

    // ---------- Anteile ----------

    public synchronized double totalShares(String id) {
        double sum = 0;
        for (double v : shares.getOrDefault(id, Map.of()).values()) sum += v;
        return sum;
    }

    public synchronized double sharePrice(String id) {
        double total = totalShares(id);
        if (total <= 0) return 1.0;
        return Math.max(0.01, pool.getOrDefault(id, 0.0) / total);
    }

    public synchronized double sharesOf(UUID player, String id) {
        return shares.getOrDefault(id, Map.of()).getOrDefault(player, 0.0);
    }

    public synchronized long costOf(UUID player, String id) {
        return cost.getOrDefault(id, Map.of()).getOrDefault(player, 0L);
    }

    public synchronized double valueOf(UUID player, String id) {
        return sharesOf(player, id) * sharePrice(id);
    }

    /** Gibt null bei Erfolg zurück. */
    public synchronized String invest(UUID player, String id, long amount) {
        if (amount <= 0) return "Ungültiger Betrag.";
        if (!plugin.economy().withdraw(player, amount)) return "Nicht genug $.";
        double add = amount / sharePrice(id);
        shares.computeIfAbsent(id, k -> new HashMap<>()).merge(player, add, Double::sum);
        cost.computeIfAbsent(id, k -> new HashMap<>()).merge(player, amount, Long::sum);
        pool.merge(id, (double) amount, Double::sum);
        save();
        return null;
    }

    /** Verkauft alle Anteile. Gibt null bei Erfolg zurück. */
    public synchronized String sellShares(UUID player, String id) {
        double mine = sharesOf(player, id);
        if (mine <= 0) return "Du hast keine Anteile an diesem Item.";
        long payout = Math.round(valueOf(player, id));
        pool.put(id, Math.max(0.0, pool.getOrDefault(id, 0.0) - payout));
        shares.getOrDefault(id, new HashMap<>()).remove(player);
        cost.getOrDefault(id, new HashMap<>()).remove(player);
        plugin.economy().deposit(player, payout);
        save();
        return null;
    }

    // ---------- Deaktivieren und Reaktivieren ----------

    /** Bringt ein deaktiviertes Item zurück. Der Preis fließt in den Topf. Gibt null bei Erfolg zurück. */
    public synchronized String reactivate(UUID player, ShopService.Item it) {
        if (isActive(it.id())) return "Dieses Item ist schon aktiv.";
        long price = it.price() * plugin.getConfig().getLong("economy.reactivate-multiplier", 10);
        if (!plugin.economy().withdraw(player, price)) return "Du brauchst " + Msg.coins(price) + "$ zum Reaktivieren.";
        active.put(it.id(), true);
        factor.put(it.id(), 1.0);
        pool.merge(it.id(), (double) price, Double::sum);
        save();
        return null;
    }

    // ---------- Tägliche Preisanpassung ----------

    private void check() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        if (now.getHour() != 0 || now.getMinute() > 5) return;
        String day = now.toLocalDate().toString();
        if (day.equals(lastRun)) return;
        lastRun = day;
        runDaily();
    }

    private synchronized void runDaily() {
        for (String sec : DYNAMIC) {
            for (ShopService.Item it : plugin.shop().items(sec)) {
                String id = it.id();
                Rarity r = rarityOf(it.material());
                double daily = target(r) / 7.0;
                double ratio = daily > 0 ? sold.getOrDefault(id, 0) / daily : 1.0;
                double delta = Math.max(-MAX_STEP, Math.min(MAX_STEP, (ratio - 1.0) * 0.1));
                double f = Math.max(0.05, factorOf(id) * (1.0 + delta));
                factor.put(id, f);
                long effective = Math.round(it.price() * f);
                if (isActive(id) && effective < minPrice(r)) {
                    active.put(id, false);
                    pool.put(id, pool.getOrDefault(id, 0.0) * 0.5);
                    plugin.getServer().broadcast(Msg.parse("<gray>Der Markt hat <white>"
                            + it.material().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                            + " <gray>aus dem Shop genommen. Anteilseigner haben Verluste."));
                }
            }
        }
        sold.clear();
        save();
    }

    private synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("lastRun", lastRun);
        for (String id : factor.keySet()) {
            String k = "items." + id + ".";
            out.set(k + "factor", factor.get(id));
            out.set(k + "active", isActive(id));
            out.set(k + "pool", pool.getOrDefault(id, 0.0));
            out.set(k + "sold", sold.getOrDefault(id, 0));
            shares.getOrDefault(id, Map.of()).forEach((u, v) -> out.set(k + "shares." + u, v));
            cost.getOrDefault(id, Map.of()).forEach((u, v) -> out.set(k + "cost." + u, v));
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("investments.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public synchronized void saveNow() {
        save();
    }

    @Override
    public String key() { return "invest"; }

    @Override
    public synchronized Map<String, Object> export(UUID id) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        shares.forEach((item, byP) -> { Double v = byP.get(id); if (v != null) m.put("sh|" + item, v); });
        cost.forEach((item, byP) -> { Long c = byP.get(id); if (c != null) m.put("co|" + item, c); });
        return m;
    }

    @Override
    public synchronized void importData(UUID id, Map<String, Object> m) {
        shares.values().forEach(x -> x.remove(id));
        cost.values().forEach(x -> x.remove(id));
        if (m != null) {
            m.forEach((k, v) -> {
                if (k.startsWith("sh|")) shares.computeIfAbsent(k.substring(3), x -> new java.util.HashMap<>()).put(id, ModeData.dbl(v));
                else if (k.startsWith("co|")) cost.computeIfAbsent(k.substring(3), x -> new java.util.HashMap<>()).put(id, ModeData.lng(v));
            });
        }
        save();
    }
}
