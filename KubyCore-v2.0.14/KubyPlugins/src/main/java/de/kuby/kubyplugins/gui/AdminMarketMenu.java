package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.MarketService;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Angebote entfernen. Das Item geht in die Abholbox des Verkäufers. */
public final class AdminMarketMenu extends AdminBase {

    public AdminMarketMenu(KubyPlugins plugin) {
        super(plugin, "Markt", 5);
    }

    @Override
    protected void build(Player p) {
        int slot = 9;
        for (MarketService.Listing l : plugin.market().all()) {
            if (slot > 35) break;
            ItemStack display = l.item().clone();
            ItemMeta meta = display.getItemMeta();
            meta.lore(List.of(
                    Msg.item("<gray>Verkäufer: <white>" + l.sellerName()),
                    Msg.item("<gray>Preis: <gold>" + Msg.coins(l.price()) + "$"),
                    Msg.item("<red>Klick: Angebot entfernen")));
            display.setItemMeta(meta);
            set(slot++, display, e -> {
                String mode = plugin.market().modeOf(l.id());
                MarketService.Listing removed = plugin.market().remove(l.id());
                if (removed != null) {
                    plugin.modes().abholboxIn(removed.seller(), mode, removed.item());
                    done(p, "<gray>Angebot entfernt, Item in der Abholbox des Verkäufers.");
                } else {
                    done(p, "<red>Angebot existiert nicht mehr.");
                }
            });
        }
        adminBack(p);
    }
}
