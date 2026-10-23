package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Abholbox des Shops: gekaufte Items landen hier. 3 Seiten à 27 Plätzen, Seite 1 ist frei, die anderen gibt es für XP. */
public final class ShopStorageService implements ModeData {

    public static final int PAGE = 27;
    public static final int PAGES = 3;

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, ItemStack[]> boxes = new HashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();

    public ShopStorageService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shopstorage.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            UUID id = UUID.fromString(k);
            pages.put(id, Math.max(1, Math.min(PAGES, yml.getInt(k + ".pages", 1))));
            ItemStack[] arr = new ItemStack[PAGE * PAGES];
            ConfigurationSection s = yml.getConfigurationSection(k + ".items");
            if (s != null) {
                for (String slot : s.getKeys(false)) {
                    int idx = Integer.parseInt(slot);
                    Object o = s.get(slot);
                    if (idx >= 0 && idx < arr.length && o instanceof ItemStack it) arr[idx] = it;
                }
            }
            boxes.put(id, arr);
        }
    }

    public synchronized int unlockedPages(UUID id) {
        return pages.getOrDefault(id, 1);
    }

    private ItemStack[] box(UUID id) {
        return boxes.computeIfAbsent(id, k -> new ItemStack[PAGE * PAGES]);
    }

    public synchronized ItemStack get(UUID id, int slot) {
        return box(id)[slot];
    }

    public synchronized void set(UUID id, int slot, ItemStack it) {
        box(id)[slot] = it;
        save();
    }

    /** Legt ein Item in die erste passende Stelle. False, wenn kein Platz ist. */
    public synchronized boolean add(UUID id, ItemStack it) {
        ItemStack[] b = box(id);
        int limit = unlockedPages(id) * PAGE;
        for (int i = 0; i < limit; i++) {
            if (b[i] != null && b[i].isSimilar(it) && b[i].getAmount() + it.getAmount() <= b[i].getMaxStackSize()) {
                b[i].setAmount(b[i].getAmount() + it.getAmount());
                save();
                return true;
            }
        }
        for (int i = 0; i < limit; i++) {
            if (b[i] == null) {
                b[i] = it.clone();
                save();
                return true;
            }
        }
        return false;
    }

    /** XP-Preis für die Seite (1-basiert). */
    public int cost(int pageNumber) {
        return plugin.getConfig().getInt("shop-storage.xp-" + pageNumber, pageNumber == 2 ? 2000 : 5000);
    }

    /** Schaltet die nächste Seite gegen XP frei. Gibt null bei Erfolg zurück. */
    public synchronized String unlock(Player p) {
        UUID id = p.getUniqueId();
        int next = unlockedPages(id) + 1;
        if (next > PAGES) return "Alle Seiten sind schon freigeschaltet.";
        int cost = cost(next);
        var stats = plugin.stats().get(id);
        if (stats.xp < cost) return "Dir fehlen noch " + (cost - stats.xp) + " XP.";
        stats.xp -= cost;
        pages.put(id, next);
        plugin.stats().save();
        save();
        return null;
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        boxes.forEach((id, arr) -> {
            String k = id.toString();
            out.set(k + ".pages", unlockedPages(id));
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] != null) out.set(k + ".items." + i, arr[i]);
            }
        });
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("shopstorage.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    @Override
    public String key() { return "shoplager"; }

    @Override
    public synchronized Map<String, Object> export(UUID id) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("pages", unlockedPages(id));
        ItemStack[] arr = boxes.get(id);
        if (arr != null) {
            for (int i = 0; i < arr.length; i++) if (arr[i] != null) m.put("s|" + i, arr[i].clone());
        }
        return m;
    }

    @Override
    public synchronized void importData(UUID id, Map<String, Object> m) {
        boxes.remove(id);
        pages.remove(id);
        if (m != null) {
            ItemStack[] arr = new ItemStack[PAGE * PAGES];
            m.forEach((k, v) -> {
                if (k.startsWith("s|") && v instanceof ItemStack it) {
                    int i = Integer.parseInt(k.substring(2));
                    if (i >= 0 && i < arr.length) arr[i] = it;
                }
            });
            boxes.put(id, arr);
            pages.put(id, Math.max(1, Math.min(PAGES, ModeData.integer(m.get("pages")))));
        }
        save();
    }
}
