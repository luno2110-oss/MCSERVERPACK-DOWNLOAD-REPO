package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.MarketService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public final class MarketMenu extends GuiMenu {

    public MarketMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Markt")), 6);
    }

    @Override
    protected void build(Player p) {
        int slot = 0;
        for (MarketService.Listing l : plugin.market().inMode(plugin.modes().modeOf(p.getUniqueId()))) {
            if (slot >= 45) break;
            boolean own = l.seller().equals(p.getUniqueId());

            ItemStack display = l.item().clone();
            ItemMeta meta = display.getItemMeta();
            meta.lore(List.of(
                    Msg.item("<gray>Verkäufer: <white>" + l.sellerName()),
                    Msg.item("<gray>Preis: <gold>" + Msg.coins(l.price()) + "$"),
                    Msg.item(own ? "<red>Klick: Angebot zurückziehen" : "<green>Klick: kaufen")));
            display.setItemMeta(meta);

            set(slot++, display, e -> {
                if (own) cancel(p, l.id());
                else buy(p, l.id());
            });
        }

        if (slot == 0) {
            set(22, Items.make(Material.BARRIER, "<gray>" + SmallCaps.of("Keine Angebote"), List.of(
                    "<gray>/markt sell <Preis> zum Anbieten")), null);
        }
        addBack(p);
    }

    private void buy(Player p, int id) {
        MarketService.Listing l = plugin.market().get(id);
        if (l == null) {
            Msg.send(p, "<red>Angebot nicht mehr verfügbar.");
            open(p);
            return;
        }
        if (!plugin.market().modeOf(id).equals(plugin.modes().modeOf(p.getUniqueId()))) {
            Msg.send(p, "<red>Dieses Angebot gibt es nicht in deinem Modus.");
            open(p);
            return;
        }
        if (!plugin.economy().withdraw(p.getUniqueId(), l.price())) {
            Msg.send(p, "<red>Nicht genug$.");
            return;
        }
        String modeOfSold = plugin.market().modeOf(id);
        if (plugin.market().remove(id) == null) {
            plugin.economy().deposit(p.getUniqueId(), l.price());
            Msg.send(p, "<red>Zu spät, das Angebot wurde gerade verkauft.");
            open(p);
            return;
        }
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(l.item().clone());
        if (!leftover.isEmpty()) {
            // Inventar voll: Käufer bekommt alles zurück, Angebot wird neu eingestellt
            plugin.economy().deposit(p.getUniqueId(), l.price());
            plugin.market().addIn(l.seller(), l.sellerName(), l.item(), l.price(), modeOfSold);
            Msg.send(p, "<red>Dein Inventar ist voll.");
            open(p);
            return;
        }

        int taxPercent = plugin.getConfig().getInt("market.tax-percent", 5);
        long payout = l.price() - (l.price() * taxPercent / 100);
        plugin.modes().depositIn(l.seller(), modeOfSold, payout);

        Sfx.play(plugin, p, Sfx.Kind.COIN);
        Msg.send(p, "<green>Gekauft für <gold>" + Msg.coins(l.price()) + "$<green>.");
        var seller = Bukkit.getPlayer(l.seller());
        if (seller != null) {
            Msg.send(seller, "<gold>Dein Angebot wurde verkauft: <white>+" + Msg.coins(payout) + "$ <gray>(nach " + taxPercent + " % Steuer)");
        }
        open(p);
    }

    private void cancel(Player p, int id) {
        MarketService.Listing l = plugin.market().remove(id);
        if (l == null) {
            Msg.send(p, "<red>Angebot nicht mehr vorhanden.");
            open(p);
            return;
        }
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(l.item().clone());
        leftover.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        Msg.send(p, "<gray>Angebot zurückgezogen.");
        open(p);
    }
}
