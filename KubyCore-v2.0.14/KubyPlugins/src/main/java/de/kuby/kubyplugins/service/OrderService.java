package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Orders: Käufer hinterlegt$, Verkäufer liefert Items, die Items landen in der Abholbox des Käufers. */
public final class OrderService {

    public record Order(int id, UUID buyer, String buyerName, Material material, int amount, long unitPrice) {
        public long remainingTotal() { return unitPrice * amount; }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final Map<Integer, Order> orders = new TreeMap<>();
    private final Map<Integer, String> modes = new java.util.HashMap<>();
    private int nextId = 1;

    public OrderService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "orders.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                Material m = Material.matchMaterial(yml.getString(key + ".material", ""));
                if (m == null) continue;
                orders.put(id, new Order(id,
                        UUID.fromString(yml.getString(key + ".buyer")),
                        yml.getString(key + ".name", "Unbekannt"),
                        m,
                        yml.getInt(key + ".amount"),
                        yml.getLong(key + ".unit")));
                modes.put(id, yml.getString(key + ".mode", "smp"));
                nextId = Math.max(nextId, id + 1);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Order " + key + " übersprungen: " + e.getMessage());
            }
        }
    }

    /** Der Aufrufer muss die$ vorher abgebucht haben. */
    public synchronized Order add(UUID buyer, String name, Material m, int amount, long unitPrice) {
        Order o = new Order(nextId++, buyer, name, m, amount, unitPrice);
        orders.put(o.id(), o);
        modes.put(o.id(), plugin.modes().modeOf(buyer));
        save();
        return o;
    }

    public synchronized Order get(int id) {
        return orders.get(id);
    }

    /** Verringert die offene Menge um n. Bei 0 wird der Order entfernt. */
    public synchronized Order fill(int id, int n) {
        Order o = orders.get(id);
        if (o == null) return null;
        int left = o.amount() - n;
        if (left <= 0) {
            orders.remove(id);
        } else {
            orders.put(id, new Order(o.id(), o.buyer(), o.buyerName(), o.material(), left, o.unitPrice()));
        }
        save();
        return o;
    }

    public synchronized Order cancel(int id) {
        Order o = orders.remove(id);
        if (o != null) save();
        return o;
    }

    public synchronized String modeOf(int id) {
        return modes.getOrDefault(id, "smp");
    }

    public synchronized List<Order> inMode(String mode) {
        List<Order> out = new ArrayList<>();
        for (Order o : orders.values()) if (modeOf(o.id()).equals(mode)) out.add(o);
        return out;
    }

    public synchronized List<Order> all() {
        return new ArrayList<>(orders.values());
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Order o : orders.values()) {
            String k = String.valueOf(o.id());
            out.set(k + ".buyer", o.buyer().toString());
            out.set(k + ".name", o.buyerName());
            out.set(k + ".material", o.material().name());
            out.set(k + ".amount", o.amount());
            out.set(k + ".unit", o.unitPrice());
            out.set(k + ".mode", modeOf(o.id()));
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("orders.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
