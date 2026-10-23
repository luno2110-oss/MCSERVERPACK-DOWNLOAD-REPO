package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Seltenheitswert für Waffen, Rüstung, Werkzeuge und ähnliche Items.
 * Wert = Materialbasis x Haltbarkeit x Verzauberungen. Steht als Zeile in der Lore.
 * Die Lore wird alle paar Sekunden für Items in den Spielerinventaren aktualisiert.
 */
public final class ItemValueService {

    private final KubyPlugins plugin;
    private final NamespacedKey key;

    public ItemValueService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "item_value");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 100L, 100L);
    }

    /** Materialbasis. Negativ = nicht bewertet. Über items.base.<MATERIAL> in der config anpassbar. */
    public double base(Material m) {
        String name = m.name();
        double fallback = -1;
        if (name.equals("ENCHANTED_GOLDEN_APPLE")) fallback = 50;
        else if (name.equals("GOLDEN_APPLE")) fallback = 10;
        else if (name.startsWith("NETHERITE_")) fallback = 12;
        else if (name.startsWith("DIAMOND_")) fallback = 8;
        else if (name.startsWith("IRON_")) fallback = 4;
        else if (name.startsWith("GOLDEN_") || name.startsWith("GOLD_")) fallback = 3;
        else if (name.startsWith("CHAINMAIL_")) fallback = 3;
        else if (name.startsWith("STONE_") && m.getMaxDurability() > 0) fallback = 2;
        else if (name.startsWith("WOODEN_") || name.startsWith("LEATHER_")) fallback = 1;
        else if (name.equals("TRIDENT") || name.equals("MACE")) fallback = 10;
        else if (name.equals("ELYTRA")) fallback = 15;
        else if (name.equals("CROSSBOW")) fallback = 5;
        else if (name.equals("BOW") || name.equals("TURTLE_HELMET")) fallback = 3;
        else if (name.equals("SHIELD")) fallback = 2;
        return plugin.getConfig().getDouble("items.base." + name, fallback);
    }

    /** Wert eines Items oder -1, wenn es nicht bewertet wird. */
    public double value(ItemStack it) {
        if (it == null || it.getType().isAir()) return -1;
        double base = base(it.getType());
        if (base <= 0) return -1;
        double durability = 1.0;
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof Damageable d && it.getType().getMaxDurability() > 0) {
            durability = 1.0 - d.getDamage() / (double) it.getType().getMaxDurability();
        }
        double durFactor = 0.5 + 0.5 * durability;
        int levels = 0;
        if (meta != null) {
            for (Map.Entry<?, Integer> e : meta.getEnchants().entrySet()) levels += e.getValue();
        }
        double enchFactor = 1.0 + levels * 0.15;
        return base * durFactor * enchFactor * 10;
    }

    public static String tier(long v) {
        if (v < 20) return "Gewöhnlich";
        if (v < 50) return "Selten";
        if (v < 100) return "Episch";
        return "Legendär";
    }

    private void sweep() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack it = p.getInventory().getItem(i);
                if (it == null) continue;
                double v = value(it);
                if (v < 0) continue;
                long rounded = Math.round(v);
                ItemMeta meta = it.getItemMeta();
                Long stored = meta.getPersistentDataContainer().get(key, PersistentDataType.LONG);
                if (stored != null && stored == rounded) continue;
                update(it, rounded);
                p.getInventory().setItem(i, it);
            }
        }
    }

    private void update(ItemStack it, long value) {
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) {
            for (Component c : meta.lore()) {
                String plain = PlainTextComponentSerializer.plainText().serialize(c);
                if (!plain.startsWith("Wert:")) lore.add(c);
            }
        }
        lore.add(Msg.item("<gray>Wert: <white>" + value + " <dark_gray>(" + tier(value) + ")"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, value);
        it.setItemMeta(meta);
    }
}
