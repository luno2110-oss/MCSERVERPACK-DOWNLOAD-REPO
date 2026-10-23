package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Abholbox: Käufe, Order-Lieferungen und zurückgezogene Angebote warten hier auf den Spieler. */
public final class AbholboxService {

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, List<ItemStack>> boxes = new ConcurrentHashMap<>();

    public AbholboxService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "abholbox.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            List<ItemStack> items = new ArrayList<>();
            for (Object o : yml.getList(key, List.of())) {
                if (o instanceof ItemStack item) items.add(item);
            }
            boxes.put(UUID.fromString(key), items);
        }
    }

    public synchronized void add(UUID id, ItemStack item) {
        boxes.computeIfAbsent(id, k -> new ArrayList<>()).add(item.clone());
        save();
    }

    public synchronized List<ItemStack> items(UUID id) {
        return new ArrayList<>(boxes.getOrDefault(id, List.of()));
    }

    /** Entnimmt alle Items. Der Aufrufer muss nicht Passendes wieder einlagern. */
    public synchronized List<ItemStack> takeAll(UUID id) {
        List<ItemStack> items = boxes.remove(id);
        save();
        return items == null ? new ArrayList<>() : items;
    }

    public synchronized void importItems(UUID id, List<ItemStack> list) {
        if (list == null || list.isEmpty()) boxes.remove(id);
        else boxes.put(id, new ArrayList<>(list));
        save();
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        boxes.forEach((id, items) -> {
            if (!items.isEmpty()) out.set(id.toString(), items);
        });
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("abholbox.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
