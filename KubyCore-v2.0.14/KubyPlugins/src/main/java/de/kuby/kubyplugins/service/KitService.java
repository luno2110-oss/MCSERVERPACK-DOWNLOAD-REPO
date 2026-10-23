package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Kits aus der config: Items, Icon und Cooldown pro Kit. Leere Kits werden angezeigt, lassen sich aber nicht abholen. */
public final class KitService implements ModeData {

    public record Kit(String id, String name, Material icon, long cooldownMinutes, List<ItemStack> items) {}

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Map<String, Long>> lastClaim = new HashMap<>();

    public KitService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(k);
            if (s == null) continue;
            Map<String, Long> m = new HashMap<>();
            for (String kit : s.getKeys(false)) m.put(kit, s.getLong(kit));
            lastClaim.put(UUID.fromString(k), m);
        }
    }

    public List<Kit> kits() {
        Map<String, Kit> sorted = new TreeMap<>();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("kits");
        if (root == null) return new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Material icon = Material.matchMaterial(s.getString("icon", "CHEST"));
            List<ItemStack> items = new ArrayList<>();
            for (String raw : s.getStringList("items")) {
                ItemStack it = parse(raw);
                if (it != null) items.add(it);
            }
            sorted.put(id, new Kit(id, s.getString("name", "Kit " + id), icon == null ? Material.CHEST : icon,
                    s.getLong("cooldown-minutes", 30), items));
        }
        return new ArrayList<>(sorted.values());
    }

    /** Format: MATERIAL:MENGE:ENCHANTMENT=STUFE,ENCHANTMENT=STUFE */
    private static ItemStack parse(String raw) {
        String[] p = raw.split(":", 3);
        Material m = Material.matchMaterial(p[0].trim().toUpperCase(Locale.ROOT));
        if (m == null) return null;
        int amount = 1;
        try {
            if (p.length > 1) amount = Integer.parseInt(p[1].trim());
        } catch (NumberFormatException ignored) {
            amount = 1;
        }
        ItemStack it = new ItemStack(m, Math.max(1, amount));
        if (p.length > 2) {
            for (String e : p[2].split(",")) {
                String[] kv = e.split("=");
                if (kv.length != 2) continue;
                Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(kv[0].trim().toLowerCase(Locale.ROOT)));
                try {
                    if (ench != null) it.addUnsafeEnchantment(ench, Integer.parseInt(kv[1].trim()));
                } catch (NumberFormatException ignored) {
                    // Ungültige Stufe wird übersprungen
                }
            }
        }
        return it;
    }

    /** Verbleibende Cooldown-Zeit in Millisekunden. 0 = verfügbar. */
    public synchronized long remaining(UUID id, Kit kit) {
        long last = lastClaim.getOrDefault(id, Map.of()).getOrDefault(kit.id(), 0L);
        long end = last + kit.cooldownMinutes() * 60_000L;
        return Math.max(0, end - System.currentTimeMillis());
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String claim(Player p, Kit kit) {
        if (kit.items().isEmpty()) return "Dieses Kit ist noch leer.";
        long left = remaining(p.getUniqueId(), kit);
        if (left > 0) return "Cooldown: " + format(left) + ".";
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : kit.items()) {
            ItemStack give = it.clone();
            String n = give.getType().name();
            if (n.endsWith("_HELMET") && inv.getHelmet() == null) inv.setHelmet(give);
            else if (n.endsWith("_CHESTPLATE") && inv.getChestplate() == null) inv.setChestplate(give);
            else if (n.endsWith("_LEGGINGS") && inv.getLeggings() == null) inv.setLeggings(give);
            else if (n.endsWith("_BOOTS") && inv.getBoots() == null) inv.setBoots(give);
            else inv.addItem(give).values().forEach(rest -> plugin.abholbox().add(p.getUniqueId(), rest));
        }
        lastClaim.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(kit.id(), System.currentTimeMillis());
        save();
        return null;
    }

    public static String format(long ms) {
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        return m + "m " + s + "s";
    }

    private synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        lastClaim.forEach((id, m) -> m.forEach((kit, t) -> out.set(id + "." + kit, t)));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("kits.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public static String describe(Kit kit) {
        return kit.items().isEmpty() ? "leer" : kit.items().size() + " Items";
    }

    public static String msgNone() {
        return Msg.escape("");
    }

    @Override
    public String key() { return "kits"; }

    @Override
    public synchronized Map<String, Object> export(UUID id) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        lastClaim.getOrDefault(id, Map.of()).forEach((kit, t) -> m.put("kit|" + kit, t));
        return m;
    }

    @Override
    public synchronized void importData(UUID id, Map<String, Object> m) {
        lastClaim.remove(id);
        if (m != null && !m.isEmpty()) {
            Map<String, Long> c = new java.util.HashMap<>();
            m.forEach((k, v) -> {
                if (k.startsWith("kit|")) c.put(k.substring(4), ModeData.lng(v));
            });
            lastClaim.put(id, c);
        }
        save();
    }
}
