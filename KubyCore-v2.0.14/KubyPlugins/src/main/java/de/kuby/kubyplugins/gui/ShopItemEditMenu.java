package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ShopService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.LongConsumer;

/** Ein Shop-Item bearbeiten: Preis, Menge, Name, Lore, Icon. Shift-Klick auf Löschen. */
public final class ShopItemEditMenu extends GuiMenu {

    private final String itemId;

    public ShopItemEditMenu(KubyPlugins plugin, String itemId) {
        super(plugin, Msg.parse("<dark_red>" + SmallCaps.of("item bearbeiten")), 4);
        this.itemId = itemId;
    }

    @Override
    protected void build(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Keine Berechtigung.");
            return;
        }
        ShopService.Item it = plugin.shop().byId(itemId);
        if (it == null) {
            Msg.send(p, "<red>Dieses Item gibt es nicht mehr.");
            new ShopEditorMenu(plugin).open(p);
            return;
        }
        String sec = plugin.shop().sectionOf(itemId);

        set(4, Items.make(it.material(), "<gold>" + (it.name() != null ? it.name() : it.material().name()), List.of(
                "<gray>Sektion: <white>" + ShopService.TITLES.get(sec),
                "<gray>Preis: <gold>" + Msg.coins(it.price()) + "$",
                "<gray>Menge: <white>" + it.amount(),
                "<gray>ID: " + it.id())), null);

        set(10, btn(Material.RED_DYE, "-100$"), e -> update(p, withPrice(it, it.price() - 100)));
        set(11, btn(Material.RED_DYE, "-10$"), e -> update(p, withPrice(it, it.price() - 10)));
        set(13, btn(Material.GOLD_INGOT, "Preis eingeben"), e -> askNumber(p, "Neuer Preis in$:",
                v -> update(p, withPrice(it, v))));
        set(15, btn(Material.LIME_DYE, "+10$"), e -> update(p, withPrice(it, it.price() + 10)));
        set(16, btn(Material.LIME_DYE, "+100$"), e -> update(p, withPrice(it, it.price() + 100)));

        set(19, btn(Material.RED_DYE, "Menge -1"), e -> update(p, withAmount(it, it.amount() - 1)));
        set(20, btn(Material.PAPER, "Menge eingeben"), e -> askNumber(p, "Neue Menge:",
                v -> update(p, withAmount(it, (int) v))));
        set(21, btn(Material.LIME_DYE, "Menge +1"), e -> update(p, withAmount(it, it.amount() + 1)));

        set(23, btn(Material.NAME_TAG, "Name ändern"), e -> plugin.prompts().ask(p,
                "Neuer Name (MiniMessage, '-' für keinen):", text ->
                        update(p, new ShopService.Item(it.id(), it.material(), it.amount(), it.price(),
                                text.equals("-") ? null : text, it.lore()))));
        set(24, btn(Material.WRITABLE_BOOK, "Beschreibung ändern"), e -> plugin.prompts().ask(p,
                "Zeilen mit | trennen, '-' für keine:", text ->
                        update(p, new ShopService.Item(it.id(), it.material(), it.amount(), it.price(), it.name(),
                                text.equals("-") ? List.of() : List.of(text.split("[|]"))))));
        set(25, btn(Material.ITEM_FRAME, "Icon aus der Hand"), e -> {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                Sfx.play(plugin, p, Sfx.Kind.ERROR);
                Msg.send(p, "<red>Halte ein Item in der Haupthand.");
                open(p);
            } else {
                update(p, new ShopService.Item(it.id(), hand.getType(), Math.min(it.amount(), hand.getMaxStackSize()),
                        it.price(), it.name(), it.lore()));
            }
        });

        set(29, btn(Material.BARRIER, "Löschen (Shift)"), e -> {
            if (!e.isShiftClick()) {
                Msg.send(p, "<gray>Zum Löschen Shift + Klick.");
                return;
            }
            plugin.shop().remove(itemId);
            Sfx.play(plugin, p, Sfx.Kind.FAIL);
            Msg.send(p, "<gray>Item gelöscht.");
            new ShopSectionEditMenu(plugin, sec, 0).open(p);
        });
        set(31, Items.make(Material.ARROW, "<gray>Zurück", List.of()),
                e -> new ShopSectionEditMenu(plugin, sec, 0).open(p));
    }

    private static org.bukkit.inventory.ItemStack btn(Material m, String label) {
        return Items.make(m, "<white>" + label, List.of("<yellow>Klick zum Ändern"));
    }

    private static ShopService.Item withPrice(ShopService.Item it, long price) {
        return new ShopService.Item(it.id(), it.material(), it.amount(), Math.max(0, price), it.name(), it.lore());
    }

    private static ShopService.Item withAmount(ShopService.Item it, int amount) {
        int max = it.material().getMaxStackSize();
        return new ShopService.Item(it.id(), it.material(), Math.max(1, Math.min(max, amount)), it.price(), it.name(), it.lore());
    }

    private void update(Player p, ShopService.Item n) {
        String sec = plugin.shop().sectionOf(n.id());
        if (sec != null) plugin.shop().replace(sec, n);
        open(p);
    }

    private void askNumber(Player p, String question, LongConsumer action) {
        plugin.prompts().ask(p, question, text -> {
            try {
                action.accept(Long.parseLong(text.trim()));
            } catch (NumberFormatException ex) {
                Sfx.play(plugin, p, Sfx.Kind.ERROR);
                Msg.send(p, "<red>Keine gültige Zahl.");
            }
            new ShopItemEditMenu(plugin, itemId).open(p);
        });
    }
}
