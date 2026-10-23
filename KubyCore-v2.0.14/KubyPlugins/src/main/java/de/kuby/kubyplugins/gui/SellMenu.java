package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Verkaufs-Fenster: Items in die oberen drei Reihen legen, unten bestätigen. Nicht verkaufbare Items kommen zurück. */
public final class SellMenu {

    public static final int CONFIRM = 31;
    private static final int STORAGE = 27;

    public static final class Holder implements InventoryHolder {
        public final UUID owner;
        Inventory inv;

        Holder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private SellMenu() {}

    public static void open(KubyPlugins plugin, Player p) {
        Holder h = new Holder(p.getUniqueId());
        h.inv = Bukkit.createInventory(h, 36, Msg.parse("<dark_gray>Verkaufen"));
        for (int i = STORAGE; i < 36; i++) {
            h.inv.setItem(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        refresh(plugin, h.inv);
        p.openInventory(h.inv);
        Sfx.play(plugin, p, Sfx.Kind.OPEN);
    }

    public static void refresh(KubyPlugins plugin, Inventory inv) {
        Holder h = (Holder) inv.getHolder();
        long preview = preview(plugin, h.owner, inv);
        boolean ok = preview > 0;
        inv.setItem(CONFIRM, Items.make(ok ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                ok ? "<green>Verkaufen" : "<gray>Nichts zum Verkaufen", List.of(
                        "<gray>Vorschau: <gold>" + Msg.coins(preview) + "$",
                        "<gray>Nicht verkaufbare Items gehen zurück")));
    }

    private static long preview(KubyPlugins plugin, UUID id, Inventory inv) {
        Map<Material, Long> prices = plugin.sell().worth();
        long total = 0;
        for (int i = 0; i < STORAGE; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null) continue;
            Long price = prices.get(it.getType());
            if (price == null) continue;
            long unit = plugin.weekly().isMeta(it.getType()) ? price * 125 / 100 : price;
            total += unit * it.getAmount();
        }
        return (long) Math.floor(total * plugin.sell().multiplier(id));
    }

    public static void confirm(KubyPlugins plugin, Player p, Inventory inv) {
        Map<Material, Long> prices = plugin.sell().worth();
        long total = 0;
        for (int i = 0; i < STORAGE; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null) continue;
            Long price = prices.get(it.getType());
            if (price == null) continue;
            long unit = plugin.weekly().isMeta(it.getType()) ? price * 125 / 100 : price;
            total += unit * it.getAmount();
            inv.setItem(i, null);
        }
        long payout = (long) Math.floor(total * plugin.sell().multiplier(p.getUniqueId()));
        if (payout > 0) {
            plugin.economy().deposit(p.getUniqueId(), payout);
            Sfx.play(plugin, p, Sfx.Kind.COIN);
            Msg.send(p, "<green>Verkauft für <gold>" + Msg.coins(payout) + "$");
        } else {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<gray>Keine verkaufbaren Items gefunden.");
        }
        p.closeInventory();
    }

    /** Gibt alle Items im Fenster zurück. Was nicht passt, geht in die Abholbox. */
    public static void returnItems(KubyPlugins plugin, Player p, Inventory inv) {
        for (int i = 0; i < STORAGE; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null) continue;
            inv.setItem(i, null);
            p.getInventory().addItem(it).values().forEach(rest -> plugin.abholbox().add(p.getUniqueId(), rest));
        }
    }
}
