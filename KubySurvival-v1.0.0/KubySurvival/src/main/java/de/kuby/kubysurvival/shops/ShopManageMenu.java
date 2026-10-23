package de.kuby.kubysurvival.shops;

import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.LongConsumer;

/** Kistenladen aus Besitzersicht: Ware, Menge, Preise, Kasse, schließen. */
public final class ShopManageMenu extends GuiMenu {

    private static final int[] AMOUNTS = {1, 4, 8, 16, 32, 64};
    private static final long MAX_PRICE = 1_000_000_000L;

    private final KubySurvival addon;
    private final ChestShopService.Shop shop;

    public ShopManageMenu(KubySurvival addon, ChestShopService.Shop shop) {
        super(addon.core(), Msg.parse("<dark_blue>Dein Laden"), 5);
        this.addon = addon;
        this.shop = shop;
    }

    @Override
    protected void build(Player p) {
        ChestShopService cs = addon.chestShops();
        if (shop.item == null) {
            set(4, Items.make(Material.BARRIER, "<red>Noch keine Ware", List.of(
                    "<gray>Nimm das Item in die Hand,", "<gray>das du verkaufen willst,", "<gray>und klick hier.")), e -> setItem(p));
        } else {
            ItemStack show = shop.item.clone();
            show.setAmount(1);
            set(4, Items.make(show.getType(), "<white>Ware: " + ChestShopService.name(shop.item), List.of(
                    "<gray>Auf Lager: <white>" + cs.stock(shop), "", "<yellow>Klick mit anderem Item in der Hand:", "<yellow>Ware ändern")),
                    e -> setItem(p));
        }

        for (int i = 0; i < AMOUNTS.length; i++) {
            int a = AMOUNTS[i];
            boolean on = shop.amount == a;
            ItemStack it = Items.make(on ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE,
                    (on ? "<green>" : "<white>") + a + " Stück pro Handel", List.of(on ? "<green>Gewählt" : "<yellow>Klick"));
            it.setAmount(a);
            set(10 + i + (i >= 3 ? 1 : 0), it, e -> {
                shop.amount = a;
                cs.save();
                cs.updateSign(shop);
                open(p);
            });
        }

        set(29, Items.make(Material.EMERALD, "<green>Verkaufspreis: " + price(shop.buyPrice), List.of(
                "<gray>Das zahlen Kunden, wenn sie", "<gray>bei dir <white>kaufen<gray>.", "", "<yellow>Klick: Preis ändern (0 = aus)")),
                e -> ask(p, "Verkaufspreis in Coins für " + shop.amount + " Stück?", v -> shop.buyPrice = v));
        set(31, Items.make(Material.GOLD_INGOT, "<gold>Kasse: " + Msg.coins(shop.cash) + " Coins", List.of(
                "<gray>Hier landet das Geld deiner Kunden.", "<gray>Fürs Ankaufen muss genug drin sein.", "",
                "<yellow>Linksklick: alles abheben", "<yellow>Rechtsklick: einzahlen")), e -> {
            if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                addon.core().prompts().ask(p, "Wie viele Coins einzahlen?", text -> {
                    long v = parse(text);
                    if (v <= 0 || !cs.depositCash(p, shop, v)) Msg.send(p, "<red>Ungültiger Betrag oder zu wenig Coins.");
                    else Msg.send(p, "<green>" + Msg.coins(v) + " Coins eingezahlt.");
                    new ShopManageMenu(addon, shop).open(p);
                });
            } else {
                long got = cs.withdrawCash(p, shop);
                Msg.send(p, got > 0 ? "<green>" + Msg.coins(got) + " Coins abgehoben." : "<gray>Die Kasse ist leer.");
                open(p);
            }
        });
        set(33, Items.make(Material.REDSTONE, "<red>Ankaufspreis: " + price(shop.sellPrice), List.of(
                "<gray>Das zahlst du (aus der Kasse),", "<gray>wenn Kunden dir etwas <white>verkaufen<gray>.", "",
                "<yellow>Klick: Preis ändern (0 = aus)")),
                e -> ask(p, "Ankaufspreis in Coins für " + shop.amount + " Stück?", v -> shop.sellPrice = v));

        set(36, Items.make(Material.CHEST, "<white>Lager öffnen", List.of("<gray>Die Kiste hinter dem Schild", "", "<yellow>Klick")), e -> {
            var inv = cs.inventory(shop);
            if (inv != null) p.openInventory(inv);
        });
        set(44, Items.make(Material.BARRIER, "<red>Laden schließen", List.of(
                "<gray>Die Kasse geht auf dein Konto,", "<gray>Kiste und Schild bleiben stehen.", "", "<red>Shift + Klick")), e -> {
            if (!e.isShiftClick()) return;
            long cash = shop.cash;
            cs.remove(shop);
            Msg.send(p, "<gray>Laden geschlossen." + (cash > 0 ? " <gold>+" + Msg.coins(cash) + " Coins" : ""));
            p.closeInventory();
        });
    }

    private void setItem(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            Msg.send(p, "<red>Nimm die Ware in die Hand.");
            return;
        }
        addon.chestShops().setItem(shop, hand);
        Msg.send(p, "<green>Ware gesetzt: <white>" + ChestShopService.name(hand));
        open(p);
    }

    private void ask(Player p, String question, LongConsumer apply) {
        addon.core().prompts().ask(p, question, text -> {
            long v = parse(text);
            if (v < 0 || v > MAX_PRICE) {
                Msg.send(p, "<red>Bitte eine Zahl von 0 bis " + Msg.coins(MAX_PRICE) + ".");
            } else {
                apply.accept(v);
                addon.chestShops().save();
                addon.chestShops().updateSign(shop);
                Msg.send(p, "<green>Gespeichert.");
            }
            new ShopManageMenu(addon, shop).open(p);
        });
    }

    private static long parse(String text) {
        try {
            return Long.parseLong(text.replace(".", "").replace(",", "").replace(" ", "").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String price(long v) {
        return v <= 0 ? "<gray>aus" : Msg.coins(v) + " Coins";
    }
}
