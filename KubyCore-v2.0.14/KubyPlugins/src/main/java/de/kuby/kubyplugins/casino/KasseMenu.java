package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Casino-Kasse wie im Clip: Truhe "» CASINO-KASSE", oben dein Stand, darunter Chips kaufen (Stapel 100, 1.000,
 * 10.000, alles, eigener Betrag) und auszahlen (einzelne Chips, alle, eigener Betrag). 1 Chip = 1 Coin, beim
 * Auszahlen 5 % Steuer (Farmpass: keine).
 */
public final class KasseMenu extends GuiMenu {

    private static final long[] AMOUNTS = {100, 1_000, 10_000};
    private static final String[] COLORS = {"white", "pink", "blue"};
    private static final long MIN_CUSTOM = 10;
    private static final long MAX_CUSTOM = 10_000_000;

    public KasseMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>»</dark_gray> <gold><b>" + SmallCaps.of("casino-kasse") + "</b></gold>"), 5);
    }

    @Override
    protected boolean fillEmpty() {
        return false;
    }

    static ItemStack chipItem(String model, String name, List<String> lore) {
        ItemStack it = Items.make(Material.PAPER, name, lore);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("kuby", "casino/" + model));
        it.setItemMeta(meta);
        return it;
    }

    @Override
    protected void build(Player p) {
        var casino = plugin.casino();
        long chips = casino.chips(p.getUniqueId());
        long coins = plugin.economy().balance(p.getUniqueId());
        int tax = casino.taxPercent(p);

        set(4, Items.head(p, CasinoText.title("gold", "Dein Stand"), List.of("",
                CasinoText.line("<white>" + SmallCaps.of("chips") + ":</white> <#F2C94C>" + CasinoText.chips(chips)),
                CasinoText.line("<white>" + SmallCaps.of("coins") + ":</white> <green>" + CasinoText.coins(coins)))), null);

        // Kaufen: Stapel
        for (int i = 0; i < AMOUNTS.length; i++) {
            long amount = AMOUNTS[i];
            boolean ok = coins >= amount;
            set(19 + i, chipItem("stack_" + COLORS[i], "<green><b>+" + CasinoText.num(amount) + " " + SmallCaps.of("chips"),
                    List.of("", CasinoText.line("Kostet <green>" + CasinoText.coins(amount)), "",
                            ok ? CasinoText.click("kaufen") : CasinoText.line("<#FF5A5A>So viele Coins hast du nicht"))),
                    e -> buy(p, amount));
        }
        set(23, chipItem("stack_dark", CasinoText.title("green", "Alles in Chips"), List.of("",
                CasinoText.line("Tauscht <green>" + CasinoText.coins(coins) + "</green> in Chips"), "",
                coins > 0 ? CasinoText.click("alles tauschen") : CasinoText.line("<#FF5A5A>Du hast keine Coins"))),
                e -> buy(p, plugin.economy().balance(p.getUniqueId())));
        ItemStack customBuy = Items.make(Material.NAME_TAG, CasinoText.title("yellow", "Eigener Betrag"), List.of("",
                CasinoText.line("Betrag eintippen, in Chips tauschen"),
                CasinoText.line("Mindestens " + CasinoText.num(MIN_CUSTOM)), "",
                CasinoText.click("eingeben")));
        set(25, customBuy, e -> {
            p.closeInventory();
            CasinoInput.amount(p, "Chips kaufen", List.of("<gray>Wie viele Chips möchtest du kaufen?",
                            "<gray>1 Chip = 1 Coin · Du hast <green>" + CasinoText.coins(plugin.economy().balance(p.getUniqueId()))),
                    MIN_CUSTOM, MAX_CUSTOM, "Kaufen", (pl, v) -> {
                        buy(pl, v);
                        new KasseMenu(plugin).open(pl);
                    }, pl -> new KasseMenu(plugin).open(pl));
        });

        // Auszahlen: einzelne Chips
        for (int i = 0; i < AMOUNTS.length; i++) {
            long amount = AMOUNTS[i];
            boolean ok = chips >= amount;
            long t = casino.tax(p, amount);
            List<String> lore = new ArrayList<>(List.of("",
                    CasinoText.line("Du erhältst <green>" + CasinoText.coins(amount - t)),
                    CasinoText.line("Steuer " + tax + "%: <#FF5A5A>-" + CasinoText.coins(t)), "",
                    ok ? CasinoText.click("auszahlen") : CasinoText.line("<#FF5A5A>So viele Chips hast du nicht")));
            set(28 + i, chipItem("chip_" + COLORS[i], "<yellow><b>" + CasinoText.num(amount) + " "
                    + SmallCaps.of("chips auszahlen"), lore), e -> sell(p, amount));
        }
        long allTax = casino.tax(p, chips);
        set(32, chipItem("stack_green", CasinoText.title("yellow", "Alle Chips auszahlen"), chips > 0 ? List.of("",
                CasinoText.line("Zahlt <#F2C94C>" + CasinoText.chips(chips) + "</#F2C94C> aus"),
                CasinoText.line("Du erhältst <green>" + CasinoText.coins(chips - allTax)),
                CasinoText.line("Steuer " + tax + "%: <#FF5A5A>-" + CasinoText.coins(allTax)), "",
                CasinoText.click("alles auszahlen")) : List.of("", CasinoText.line("<#FF5A5A>Du hast keine Chips"))),
                e -> sell(p, plugin.casino().chips(p.getUniqueId())));
        ItemStack customSell = Items.make(Material.NAME_TAG, CasinoText.title("yellow", "Eigener Betrag"), List.of("",
                CasinoText.line("Anzahl eintippen, in Coins auszahlen"),
                CasinoText.line("Steuer beim Auszahlen: <#FF5A5A>" + tax + "%"), "",
                CasinoText.click("eingeben")));
        set(34, customSell, e -> {
            p.closeInventory();
            CasinoInput.amount(p, "Chips auszahlen", List.of("<gray>Wie viele Chips möchtest du auszahlen?",
                            "<gray>Du hast <#F2C94C>" + CasinoText.chips(plugin.casino().chips(p.getUniqueId()))
                                    + "</#F2C94C> · Steuer " + tax + "%"),
                    1, MAX_CUSTOM, "Auszahlen", (pl, v) -> {
                        sell(pl, v);
                        new KasseMenu(plugin).open(pl);
                    }, pl -> new KasseMenu(plugin).open(pl));
        });
    }

    private void buy(Player p, long amount) {
        if (amount <= 0) {
            CasinoText.error(p, "Du hast keine Coins.");
            return;
        }
        String err = plugin.casino().buy(p, amount);
        if (err != null) {
            CasinoText.error(p, err);
            return;
        }
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        CasinoText.send(p, "<green>+" + CasinoText.chips(amount) + "</green> gekauft · Stand <#F2C94C>"
                + CasinoText.chips(plugin.casino().chips(p.getUniqueId())));
        if (p.getOpenInventory().getTopInventory().getHolder() == this) refresh(p);
    }

    private void sell(Player p, long amount) {
        if (amount <= 0) {
            CasinoText.error(p, "Du hast keine Chips.");
            return;
        }
        long tax = plugin.casino().tax(p, amount);
        String err = plugin.casino().sell(p, amount);
        if (err != null) {
            CasinoText.error(p, err);
            return;
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.9f);
        p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.6f, 1.6f);
        CasinoText.send(p, CasinoText.chips(amount) + " ausgezahlt · <green>+" + CasinoText.coins(amount - tax)
                + "</green>" + (tax > 0 ? " <dark_gray>(Steuer " + CasinoText.num(tax) + ")" : ""));
        if (p.getOpenInventory().getTopInventory().getHolder() == this) refresh(p);
    }
}
