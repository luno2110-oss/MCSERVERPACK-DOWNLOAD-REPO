package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.OrderService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class OrdersMenu extends GuiMenu {

    public OrdersMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Orders")), 6);
    }

    @Override
    protected void build(Player p) {
        int slot = 0;
        for (OrderService.Order o : plugin.orders().inMode(plugin.modes().modeOf(p.getUniqueId()))) {
            if (slot >= 45) break;
            boolean own = o.buyer().equals(p.getUniqueId());
            String name = SmallCaps.of(o.material().name().replace('_', ' '));
            set(slot++, Items.make(o.material(), "<gold>" + name + " <dark_gray>x" + o.amount(), List.of(
                    "<gray>Gesucht von: <white>" + o.buyerName(),
                    "<gray>Preis pro Stück: <gold>" + Msg.coins(o.unitPrice()) + "$",
                    own ? "<red>Klick: Order abbrechen ($ zurück)"
                        : "<green>Klick: liefern (Items aus deinem Inventar)")),
                    e -> {
                        if (own) cancel(p, o.id());
                        else fill(p, o.id());
                    });
        }
        if (slot == 0) {
            set(22, Items.make(Material.BARRIER, "<gray>" + SmallCaps.of("Keine Orders"), List.of(
                    "<gray>/orders add <Material> <Menge> <Preis>")), null);
        }
        set(51, Items.make(Material.WRITABLE_BOOK, "<aqua>" + SmallCaps.of("Hilfe"), List.of(
                        "<gray>Neuer Order: <white>/orders add <Material> <Menge> <Preis>")), null);
        addBack(p);
    }

    private void fill(Player p, int id) {
        OrderService.Order o = plugin.orders().get(id);
        if (o == null) {
            Msg.send(p, "<red>Dieser Order existiert nicht mehr.");
            open(p);
            return;
        }
        int have = count(p, o.material());
        if (have == 0) {
            Msg.send(p, "<red>Du hast kein " + o.material().name().toLowerCase().replace('_', ' ') + " im Inventar.");
            return;
        }
        if (!plugin.orders().modeOf(id).equals(plugin.modes().modeOf(p.getUniqueId()))) {
            Msg.send(p, "<red>Dieser Order gehört zu einem anderen Modus.");
            open(p);
            return;
        }
        int n = Math.min(have, o.amount());
        String orderMode = plugin.orders().modeOf(id);
        p.getInventory().removeItem(new ItemStack(o.material(), n));
        plugin.orders().fill(id, n);

        // Liefern in Stacks, damit die Abholbox saubere Stacks enthält
        int max = o.material().getMaxStackSize();
        for (int left = n; left > 0; left -= max) {
            plugin.modes().abholboxIn(o.buyer(), orderMode, new ItemStack(o.material(), Math.min(max, left)));
        }

        long gross = (long) n * o.unitPrice();
        long tax = gross * plugin.getConfig().getInt("market.tax-percent", 5) / 100;
        plugin.economy().deposit(p.getUniqueId(), gross - tax);
        Sfx.play(plugin, p, Sfx.Kind.COIN);
        Msg.send(p, "<green>Geliefert: <white>" + n + "x <green>für <gold>" + Msg.coins(gross - tax) + "$");

        var buyer = Bukkit.getPlayer(o.buyer());
        if (buyer != null) {
            Msg.send(buyer, "<gold>Dein Order wurde geliefert. Hol die Items mit <white>/abholbox<gold> ab.");
        }
        open(p);
    }

    private void cancel(Player p, int id) {
        OrderService.Order o = plugin.orders().cancel(id);
        if (o == null) {
            Msg.send(p, "<red>Order nicht mehr vorhanden.");
        } else {
            plugin.economy().deposit(p.getUniqueId(), o.remainingTotal());
            Msg.send(p, "<gray>Order abgebrochen, <gold>" + Msg.coins(o.remainingTotal()) + "$ <gray>zurück.");
        }
        open(p);
    }

    private static int count(Player p, Material m) {
        int sum = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m) sum += it.getAmount();
        }
        return sum;
    }
}
