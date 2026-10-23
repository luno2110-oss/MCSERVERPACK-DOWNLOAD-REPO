package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ShopService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.LongConsumer;

/** Admin-Editor: Sektionen, Wochenende-Einstellungen. */
public final class ShopEditorMenu extends GuiMenu {

    public ShopEditorMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_red>" + SmallCaps.of("shop-editor")), 4);
    }

    @Override
    protected void build(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Keine Berechtigung.");
            return;
        }
        for (int i = 0; i < 36; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }

        int[] slots = {10, 11, 12, 13, 14, 15};
        int i = 0;
        for (String sec : ShopService.SECTIONS) {
            int count = plugin.shop().items(sec).size();
            set(slots[i++], Items.make(Material.CHEST, "<gold>" + ShopService.TITLES.get(sec), List.of(
                    "<gray>" + count + " Items",
                    "<yellow>Klick: bearbeiten")),
                    e -> new ShopSectionEditMenu(plugin, sec, 0).open(p));
        }

        set(19, Items.make(Material.PAPER, "<white>Wochenende: Anzahl", List.of(
                "<gray>Aktuell: <white>" + plugin.shop().count(),
                "<yellow>Klick: ändern")), e -> askNumber(p, "Anzahl Angebote pro Spieler (1-10):",
                "weekend.count", 1, 10));
        set(20, Items.make(Material.ENDER_EYE, "<light_purple>Neuwurf-Kosten", List.of(
                "<gray>Aktuell: <aqua>" + plugin.shop().rerollCost() + " Juwelen",
                "<yellow>Klick: ändern")), e -> askNumber(p, "Kosten für einen Neuwurf in Juwelen:",
                "weekend.reroll-jewels", 0, 1000000));
        set(21, Items.make(Material.BARRIER, "<red>Max. Neuwürfe", List.of(
                "<gray>Pro Spieler und Wochenende",
                "<gray>Aktuell: <white>" + plugin.shop().maxRerolls(),
                "<yellow>Klick: ändern")), e -> askNumber(p, "Max. Neuwürfe pro Wochenende:",
                "weekend.max-rerolls", 0, 100));

        set(23, Items.make(Material.CLOCK, "<aqua>Daily Shop: Anzahl", List.of(
                "<gray>Angebote pro Tag, für alle gleich",
                "<gray>Aktuell: <white>" + plugin.getConfig().getInt("daily-shop.count", 6),
                "<yellow>Klick: ändern")), e -> askNumber(p, "Anzahl Angebote im Daily Shop (1-12):",
                "daily-shop.count", 1, 12));

        set(31, Items.make(Material.ARROW, "<gray>Zurück zum Admin-Panel", List.of()),
                e -> new AdminMenu(plugin).open(p));
    }

    private void askNumber(Player p, String question, String path, long min, long max) {
        plugin.prompts().ask(p, question, text -> {
            try {
                long v = Math.max(min, Math.min(max, Long.parseLong(text.trim())));
                plugin.getConfig().set(path, v);
                plugin.saveConfig();
                Msg.send(p, "<green>Gespeichert: " + v);
            } catch (NumberFormatException ex) {
                Msg.send(p, "<red>Keine gültige Zahl.");
            }
            new ShopEditorMenu(plugin).open(p);
        });
    }
}
