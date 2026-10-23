package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class SellMultiMenu extends GuiMenu {

    public SellMultiMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Verkaufs-Multiplikator")), 3);
    }

    @Override
    protected void build(Player p) {
        int lvl = plugin.sell().level(p.getUniqueId());
        int max = plugin.sell().maxLevel();
        String mult = String.format(Locale.GERMANY, "%.2f", plugin.sell().multiplier(p.getUniqueId()));

        set(13, Items.make(Material.EMERALD, "<green>" + SmallCaps.of("Stufe " + lvl + " / " + max), List.of(
                "<gray>Aktueller Multiplikator: <green>x" + mult)), null);

        if (lvl < max) {
            long cost = plugin.sell().upgradeCost(p.getUniqueId());
            set(15, Items.make(Material.GOLD_INGOT, "<gold>" + SmallCaps.of("Stufe kaufen"), List.of(
                            "<gray>Kosten: <gold>" + Msg.coins(cost) + "$",
                            "<gray>Danach: <green>x" + String.format(Locale.GERMANY, "%.2f",
                            1.0 + (lvl + 1) * plugin.getConfig().getDouble("sellmulti.step", 0.05)))),
                    e -> {
                        String err = plugin.sell().upgrade(p.getUniqueId());
                        Msg.send(p, err == null ? "<green>Stufe erhöht!" : "<red>" + err);
                        open(p);
                    });
        } else {
            set(15, Items.make(Material.BARRIER, "<gray>" + SmallCaps.of("Maximalstufe"), List.of()), null);
        }
        addBack(p);
    }
}
