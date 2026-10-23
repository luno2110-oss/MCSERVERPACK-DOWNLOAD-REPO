package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class DailyPotMenu extends GuiMenu {

    public DailyPotMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Tagespot")), 3);
    }

    @Override
    protected void build(Player p) {
        long price = plugin.getConfig().getLong("dailypot.ticket-price", 200);
        int hour = plugin.getConfig().getInt("dailypot.draw-hour", 19);
        set(4, Items.make(Material.GOLD_BLOCK, "<gold>" + SmallCaps.of("Topf"), List.of(
                "<gray>Im Topf: <gold>" + Msg.coins(plugin.dailyPot().pot()) + "$",
                "<gray>Deine Lose: <white>" + plugin.dailyPot().ticketsOf(p.getUniqueId()),
                "<gray>Ziehung: <white>" + hour + ":00 Uhr")), null);

        buyButton(p, 11, 1, price);
        buyButton(p, 13, 5, price);
        buyButton(p, 15, 20, price);
        addBack(p);
    }

    private void buyButton(Player p, int slot, int amount, long price) {
        set(slot, Items.make(Material.PAPER, "<green>" + amount + " Los" + (amount > 1 ? "e" : ""), List.of(
                        "<gray>Kosten: <gold>" + Msg.coins(price * amount) + "$")),
                e -> {
                    String err = plugin.dailyPot().buy(p.getUniqueId(), amount);
                    Msg.send(p, err == null ? "<green>" + amount + " Los gekauft." : "<red>" + err); Sfx.play(plugin, p, err == null ? Sfx.Kind.COIN : Sfx.Kind.ERROR);
                    open(p);
                });
    }
}
