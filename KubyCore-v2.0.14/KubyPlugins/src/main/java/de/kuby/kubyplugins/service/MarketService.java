package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Auktionshaus: Angebote mit Verkäufer, Item und Preis. */
public final class MarketService {

    public record Listing(int id, UUID seller, String sellerName, ItemStack item, long price) {}

    private final KubyPlugins plugin;
    private final File file;
    private final Map<Integer, Listing> listings = new TreeMap<>();
    private final Map<Integer, String> modes = new java.util.HashMap<>();
    private int nextId = 1;

    public MarketService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                ItemStack item = (ItemStack) yml.get(key + ".item");
                if (item == null) continue;
                Listing l = new Listing(id,
                        UUID.fromString(yml.getString(key + ".seller")),
                        yml.getString(key + ".name", "Unbekannt"),
                        item,
                        yml.getLong(key + ".price"));
                listings.put(id, l);
                modes.put(id, yml.getString(key + ".mode", "smp"));
                nextId = Math.max(nextId, id + 1);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Markt-Eintrag " + key + " übersprungen: " + e.getMessage());
            }
        }
    }

    /** Angebot im Modus des Verkäufers. */
    public synchronized Listing add(UUID seller, String sellerName, ItemStack item, long price) {
        return addIn(seller, sellerName, item, price, plugin.modes().modeOf(seller));
    }

    public synchronized Listing addIn(UUID seller, String sellerName, ItemStack item, long price, String mode) {
        Listing l = new Listing(nextId++, seller, sellerName, item.clone(), price);
        listings.put(l.id(), l);
        modes.put(l.id(), mode);
        save();
        return l;
    }

    public synchronized String modeOf(int id) {
        return modes.getOrDefault(id, "smp");
    }

    public synchronized List<Listing> inMode(String mode) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) if (modeOf(l.id()).equals(mode)) out.add(l);
        return out;
    }

    public synchronized Listing get(int id) {
        return listings.get(id);
    }

    public synchronized Listing remove(int id) {
        Listing l = listings.remove(id);
        if (l != null) save();
        return l;
    }

    public synchronized List<Listing> all() {
        return new ArrayList<>(listings.values());
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Listing l : listings.values()) {
            String key = String.valueOf(l.id());
            out.set(key + ".seller", l.seller().toString());
            out.set(key + ".name", l.sellerName());
            out.set(key + ".item", l.item());
            out.set(key + ".price", l.price());
            out.set(key + ".mode", modeOf(l.id()));
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("market.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
