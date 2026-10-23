package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WorthMenu extends GuiMenu {

    public WorthMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Preisliste")), 6);
    }

    @Override
    protected void build(Player p) {
        double mult = plugin.sell().multiplier(p.getUniqueId());
        int slot = 0;
        for (Map.Entry<Material, Long> e : plugin.sell().worth().entrySet()) {
            if (slot >= 45) break;
            set(slot++, Items.make(e.getKey(), "<gold>" + SmallCaps.of(e.getKey().name().replace('_', ' ')), List.of(
                    "<gray>Grundpreis: <gold>" + Msg.coins(e.getValue()) + "$ <dark_gray>/ Stück",
                    "<gray>Dein Multiplikator: <green>x" + String.format(Locale.GERMANY, "%.2f", mult))), null);
        }
        set(51, Items.make(Material.EMERALD, "<green>" + SmallCaps.of("Alles verkaufen"), List.of(
                        "<gray>Verkauft alle Items aus deinem Inventar, die einen Preis haben")),
                e -> {
                    long payout = plugin.sell().sellInventory(p);
                    Msg.send(p, payout > 0
                            ? "<green>Verkauft für <gold>" + Msg.coins(payout) + "$"
                            : "<gray>Keine verkaufbaren Items gefunden.");
                    open(p);
                });
        addBack(p);
    }
}
