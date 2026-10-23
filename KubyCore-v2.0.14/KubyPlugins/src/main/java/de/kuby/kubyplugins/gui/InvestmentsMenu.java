package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.EconomyMarketService;
import de.kuby.kubyplugins.service.ShopService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Investieren und Anteile verkaufen. Klick: Standardbetrag, Shift: zehnfach, Rechtsklick: alle Anteile verkaufen. */
public final class InvestmentsMenu extends GuiMenu {

    public InvestmentsMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Investments")), 5);
    }

    @Override
    protected void build(Player p) {
        UUID id = p.getUniqueId();
        EconomyMarketService inv = plugin.investments();
        double value = 0;
        long invested = 0;
        for (String sec : EconomyMarketService.DYNAMIC) {
            for (ShopService.Item it : plugin.shop().items(sec)) {
                value += inv.valueOf(id, it.id());
                invested += inv.costOf(id, it.id());
            }
        }
        long gain = Math.round(value - invested);
        set(4, Items.make(Material.GOLD_BLOCK, "<gold>" + SmallCaps.of("Dein Portfolio"), List.of(
                "<gray>Wert: <white>" + Msg.coins(Math.round(value)) + "$",
                "<gray>Eingesetzt: <white>" + Msg.coins(invested) + "$",
                "<gray>Gewinn / Verlust: " + signed(gain),
                "<dark_gray>Unten: Anteile kaufen oder verkaufen")), null);

        int slot = 9;
        long step = plugin.getConfig().getLong("economy.invest-step", 1000);
        for (String sec : EconomyMarketService.DYNAMIC) {
            for (ShopService.Item it : plugin.shop().items(sec)) {
                if (slot > 35) break;
                set(slot++, display(it, id, step), e -> onClick(p, it, e, step));
            }
        }
        addBack(p);
    }

    private org.bukkit.inventory.ItemStack display(ShopService.Item it, UUID id, long step) {
        EconomyMarketService inv = plugin.investments();
        double mine = inv.sharesOf(id, it.id());
        double value = inv.valueOf(id, it.id());
        long gain = Math.round(value - inv.costOf(id, it.id()));
        String name = it.name() != null ? it.name() : it.material().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Status: " + (inv.isActive(it.id()) ? "<green>aktiv" : "<red>deaktiviert"));
        lore.add("<gray>Shop-Preis: <white>" + Msg.coins(inv.priceOf(it)) + "$");
        lore.add("<gray>Anteilswert: <white>" + String.format(Locale.ROOT, "%.2f", inv.sharePrice(it.id())) + "$");
        lore.add("<gray>Deine Anteile: <white>" + String.format(Locale.ROOT, "%.2f", mine));
        lore.add("<gray>Wert: <white>" + Msg.coins(Math.round(value)) + "$ <dark_gray>| " + signed(gain));
        lore.add("");
        lore.add("<yellow>Klick: " + Msg.coins(step) + "$ investieren");
        lore.add("<yellow>Shift: " + Msg.coins(step * 10) + "$ investieren");
        lore.add("<yellow>Rechtsklick: alle Anteile verkaufen");
        return Items.make(it.material(), "<white>" + name, lore);
    }

    private void onClick(Player p, ShopService.Item it, InventoryClickEvent e, long step) {
        String err;
        if (e.isRightClick()) {
            err = plugin.investments().sellShares(p.getUniqueId(), it.id());
            if (err == null) Msg.send(p, "<green>Anteile verkauft.");
        } else {
            long amount = e.isShiftClick() ? step * 10 : step;
            err = plugin.investments().invest(p.getUniqueId(), it.id(), amount);
            if (err == null) Msg.send(p, "<green>" + Msg.coins(amount) + "$ investiert.");
        }
        if (err != null) Msg.send(p, "<red>" + err);
        Sfx.play(plugin, p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        open(p);
    }

    private static String signed(long v) {
        return v >= 0 ? "<green>+" + Msg.coins(v) + "$" : "<red>-" + Msg.coins(-v) + "$";
    }
}
