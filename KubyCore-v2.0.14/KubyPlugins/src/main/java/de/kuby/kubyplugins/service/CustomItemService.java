package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Eigene Items ohne externes Plugin. Das Item bekommt ein Modell aus dem Resource Pack
 * (Feld item_model, ab Minecraft 1.21.4). Definitionen stehen in der config.yml unter custom-items.
 * Format: id;Name;MATERIAL;namespace:modell;Lore1|Lore2
 */
public final class CustomItemService {

    public record Def(String id, String name, Material material, String model, List<String> lore) {}

    private final KubyPlugins plugin;
    private final NamespacedKey idKey;
    private final Map<String, Def> defs = new LinkedHashMap<>();

    public CustomItemService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "kp_custom");
        load();
    }

    private void load() {
        defs.clear();
        for (String raw : plugin.getConfig().getStringList("custom-items")) {
            String[] p = raw.split(";", 5);
            if (p.length < 4) {
                plugin.getLogger().warning("Ungültiges Custom Item: " + raw);
                continue;
            }
            try {
                Material mat = Material.valueOf(p[2].trim().toUpperCase(Locale.ROOT));
                List<String> lore = new ArrayList<>();
                if (p.length == 5 && !p[4].isBlank()) {
                    for (String l : p[4].split("\\|")) lore.add(l.trim());
                }
                String id = p[0].trim().toLowerCase(Locale.ROOT);
                defs.put(id, new Def(id, p[1].trim(), mat, p[3].trim(), lore));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unbekanntes Material im Custom Item: " + raw);
            }
        }
    }

    public Collection<Def> all() {
        return defs.values();
    }

    public Def get(String id) {
        return defs.get(id.toLowerCase(Locale.ROOT));
    }

    public ItemStack create(Def d, int amount) {
        ItemStack item = Items.make(d.material(), d.name(), d.lore());
        ItemMeta meta = item.getItemMeta();
        NamespacedKey model = NamespacedKey.fromString(d.model());
        if (model != null) meta.setItemModel(model);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, d.id());
        item.setItemMeta(meta);
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return item;
    }

    /** Gibt die ID eines Custom Items zurück, sonst null. */
    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }
}
