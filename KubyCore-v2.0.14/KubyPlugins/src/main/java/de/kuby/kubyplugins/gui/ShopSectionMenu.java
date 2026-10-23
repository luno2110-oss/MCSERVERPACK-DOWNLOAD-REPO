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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Sektion, Suchergebnisse, Wochenende oder Daily Shop. Drei Reihen, unten Suche, Zurück und Abholbox.
 * Wochenende und Daily Shop haben eigene Angebote, der Daily Shop wird mit Juwelen bezahlt.
 */
public final class ShopSectionMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] WEEKEND_SLOTS = {10, 12, 14, 16};
    private static final int[] DAILY_SLOTS = {10, 11, 12, 14, 15, 16};

    private final String title;
    private final String section;
    private final List<ShopService.Item> list;
    private final int page;

    public ShopSectionMenu(KubyPlugins plugin, String title, String section, List<ShopService.Item> list, int page) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of(title)), 3);
        this.title = title;
        this.section = section;
        this.list = list;
        this.page = page;
    }

    private boolean weekendView() { return ShopService.WEEKEND.equals(section); }
    private boolean dailyView() { return ShopService.DAILY.equals(section); }

    @Override
    protected void build(Player p) {
        UUID id = p.getUniqueId();
        for (int i = 0; i < 27; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }

        if (weekendView()) {
            for (int i = 0; i < list.size() && i < WEEKEND_SLOTS.length; i++) {
                ShopService.Item it = list.get(i);
                boolean bought = plugin.shop().isBought(id, it.id());
                set(WEEKEND_SLOTS[i], display(it, bought), bought ? null : e -> buy(p, it));
            }
            if (list.isEmpty()) {
                set(13, Items.make(Material.BARRIER, "<gray>Keine Angebote", List.of()), null);
            } else {
                set(13, Items.make(Material.ENDER_EYE, "<light_purple>Neu würfeln", List.of(
                        "<gray>Kosten: <aqua>" + plugin.shop().rerollCost() + " Juwelen",
                        "<gray>Neuwürfe übrig: <white>" + plugin.shop().rerollsLeft(id),
                        "<gray>Nur für dich bis zum nächsten Wochenende")), e -> reroll(p));
            }
        } else if (dailyView()) {
            for (int i = 0; i < list.size() && i < DAILY_SLOTS.length; i++) {
                ShopService.Item it = list.get(i);
                boolean bought = plugin.shop().dailyBoughtBy(id, it.id());
                set(DAILY_SLOTS[i], display(it, bought), bought ? null : e -> buy(p, it));
            }
            set(13, Items.make(Material.CLOCK, "<aqua>Daily Shop", List.of(
                    "<gray>Neue Angebote jeden Tag um 0 Uhr",
                    "<gray>Für alle Spieler gleich",
                    "<gray>Bezahlt wird mit <aqua>Juwelen")), null);
        } else {
            int start = page * SLOTS.length;
            for (int i = 0; i < SLOTS.length; i++) {
                int idx = start + i;
                if (idx >= list.size()) break;
                ShopService.Item it = list.get(idx);
                set(SLOTS[i], display(it, false), e -> buy(p, it));
            }
            if (list.isEmpty()) {
                set(13, Items.make(Material.BARRIER, "<gray>Keine Items", List.of()), null);
            }
            if (page > 0) {
                set(9, Items.make(Material.ARROW, "<gray>Vorherige Seite", List.of()),
                        e -> new ShopSectionMenu(plugin, title, section, list, page - 1).open(p));
            }
            if (start + SLOTS.length < list.size()) {
                set(17, Items.make(Material.ARROW, "<gray>Nächste Seite", List.of()),
                        e -> new ShopSectionMenu(plugin, title, section, list, page + 1).open(p));
            }
        }

        set(20, Items.make(Material.COMPASS, "<aqua>Suche", List.of("<gray>Name, ID, Material oder Nummer")),
                e -> plugin.prompts().ask(p, "Name, ID, Material oder Nummer eingeben:", text ->
                        new ShopSectionMenu(plugin, "Suche", "search", plugin.shop().search(text), 0).open(p)));
        set(22, Items.make(Material.ARROW, "<gray>Zurück zu den Sektionen", List.of()),
                e -> new ShopMenu(plugin).open(p));
        set(24, Items.make(Material.CHEST, "<gold>Abholbox", List.of("<gray>Gekaufte Items abholen")),
                e -> new ShopStorageMenu(plugin, 0).open(p));
    }

    private ItemStack display(ShopService.Item it, boolean bought) {
        if (!weekendView() && !dailyView() && !plugin.investments().isActive(it.id())) {
            return Items.make(Material.BARRIER, "<red>Deaktiviert: <white>" + pretty(it.material()), List.of(
                    "<gray>Der Markt hat dieses Item aus dem Shop genommen",
                    "<gray>Reaktivieren kostet <gold>" + Msg.coins(it.price() * plugin.getConfig().getLong("economy.reactivate-multiplier", 10)) + "$",
                    "<yellow>Klick: reaktivieren"));
        }
        String name = it.name() != null ? it.name()
                : "<white>" + pretty(it.material()) + (it.amount() > 1 ? " x" + it.amount() : "");
        String currency = dailyView() ? " Juwelen" : "$";
        List<String> lore = new ArrayList<>();
        if (it.lore() != null) lore.addAll(it.lore());
        long price = weekendView() || dailyView() ? it.price() : plugin.investments().priceOf(it);
        lore.add("<gray>Preis: <gold>" + Msg.coins(price) + currency);
        lore.add("<gray>Menge: <white>" + it.amount());
        if (!weekendView() && !dailyView()) {
            lore.add("<dark_gray>Nr. " + plugin.shop().slotNumber(it.id()) + " | ID " + it.id());
        }
        lore.add(bought ? "<green>✔ Bereits gekauft" : "<yellow>Klick: kaufen");
        return Items.make(it.material(), name, lore);
    }

    private void buy(Player p, ShopService.Item it) {
        UUID id = p.getUniqueId();
        boolean wk = weekendView();
        boolean dl = dailyView();
        if (!wk && !dl && !plugin.investments().isActive(it.id())) {
            String err = plugin.investments().reactivate(id, it);
            result(p, err, "<green>Item ist wieder im Shop!");
            new ShopSectionMenu(plugin, title, section, plugin.shop().items(section), page).open(p);
            return;
        }
        long price = wk || dl ? it.price() : plugin.investments().priceOf(it);
        if (wk && plugin.shop().isBought(id, it.id())) {
            result(p, "Dieses Angebot hast du schon gekauft.", null);
            return;
        }
        if (dl && plugin.shop().dailyBoughtBy(id, it.id())) {
            result(p, "Dieses Angebot hast du heute schon gekauft.", null);
            return;
        }
        boolean paid = dl ? plugin.jewels().take(id, price) : plugin.economy().withdraw(id, price);
        if (!paid) {
            result(p, dl ? "Du brauchst " + it.price() + " Juwelen." : "Nicht genug$ (" + Msg.coins(it.price()) + ").", null);
            return;
        }
        if (!plugin.shopStorage().add(id, new ItemStack(it.material(), it.amount()))) {
            if (dl) plugin.jewels().add(id, price);
            else plugin.economy().deposit(id, price);
            result(p, "Die Abholbox ist voll. Hol Items ab oder schalte eine Seite frei.", null);
            return;
        }
        if (wk) plugin.shop().markBought(id, it.id());
        if (!wk && !dl) plugin.investments().recordSale(it.id(), it.amount(), price);
        if (dl) plugin.shop().markDailyBought(id, it.id());
        Sfx.play(plugin, p, Sfx.Kind.COIN);
        Msg.send(p, "<green>Gekauft! <gray>Hol es in der Abholbox ab.");
        if (wk) {
            new ShopSectionMenu(plugin, title, section, plugin.shop().weekendPicks(id), 0).open(p);
        } else if (dl) {
            new ShopSectionMenu(plugin, title, section, plugin.shop().dailyItems(id), 0).open(p);
        } else {
            open(p);
        }
    }

    private void reroll(Player p) {
        String err = plugin.shop().reroll(p);
        result(p, err, "<green>Neue Auswahl für dieses Wochenende!");
        new ShopSectionMenu(plugin, title, section, plugin.shop().weekendPicks(p.getUniqueId()), 0).open(p);
    }

    private void result(Player p, String error, String ok) {
        Sfx.play(plugin, p, error == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        Msg.send(p, error == null ? ok : "<red>" + error);
    }

    private static String pretty(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
