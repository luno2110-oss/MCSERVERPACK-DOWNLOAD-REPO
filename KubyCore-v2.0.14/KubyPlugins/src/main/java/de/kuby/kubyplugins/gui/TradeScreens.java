package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.casino.CasinoInput;
import de.kuby.kubyplugins.service.MarketService;
import de.kuby.kubyplugins.service.ShopService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Icons;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Startseiten von Shop und Markt als Dialoge mit eigenen Grafiken (die Item-Listen bleiben Truhen, weil dort echte
 * Items liegen). Shop in Smaragd: Kategorien als Karten (Blöcke, Essen, Redstone, Dekoration, Wochenende, Daily Shop),
 * dazu Verkaufen, Suche, Abholbox und Börse. Markt in Bernstein: Angebote, Item verkaufen (Preisfeld), meine Angebote,
 * Aufträge, Abholbox und die neuesten Angebote. Ohne Pack: ShopMenu und MarketMenu.
 */
public final class TradeScreens {

    static final int W = 390;
    static final TextColor EMERALD = TextColor.color(0x6BE08A), MINT = TextColor.color(0xA8E8B8), GOLD = TextColor.color(0xF8D35A),
            AMBER = TextColor.color(0xF2B84A), SAND = TextColor.color(0xE8D2A0), DIM = TextColor.color(0x8A8070), CREAM = TextColor.color(0xF6EEDC),
            DARK = TextColor.color(0x1A1206);

    /** Karte: Bild, Name, Text, Zusatz rechts (z. B. Anzahl), Klick. */
    public record Card(KitData.Sprite art, String name, String desc, String extra, Consumer<Player> click) {}

    /** Kleine Zeile (neueste Angebote): Item, Name, Verkäufer, Preis. */
    public record Offer(KitData.Icon icon, String name, String seller, long price) {}

    private final KubyPlugins plugin;

    public TradeScreens(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private static Consumer<Player> go(Consumer<Player> c) {
        return p -> {
            de.kuby.kubyplugins.ui.UiDialog.close(p);
            c.accept(p);
        };
    }

    // =================================================================================================================
    // Shop
    // =================================================================================================================

    public void shop(Player p) {
        var hook = plugin.shopHook(p);
        if (hook != null) {
            hook.open(p);
            return;
        }
        if (!plugin.uiPack().active(p)) {
            new ShopMenu(plugin).open(p);
            return;
        }
        ShopItemScreen items = new ShopItemScreen(plugin);
        List<Card> cats = List.of(
                new Card(KitData.Sprite.HB_C_BLOECKE, "Blöcke", "Bausteine und Baumaterial", n("blocks"), go(pl -> items.open(pl, "blocks", 0))),
                new Card(KitData.Sprite.HB_C_ESSEN, "Essen", "Nahrung für unterwegs", n("food"), go(pl -> items.open(pl, "food", 0))),
                new Card(KitData.Sprite.HB_C_REDSTONE, "Redstone", "Schaltungen und Mechanik", n("redstone"), go(pl -> items.open(pl, "redstone", 0))),
                new Card(KitData.Sprite.HB_C_DEKO, "Dekoration", "Für dein Zuhause", n("deco"), go(pl -> items.open(pl, "deco", 0))),
                new Card(KitData.Sprite.PT_GIFT_BIG, "Wochenende", "Jedes Wochenende neue Angebote", null,
                        go(pl -> items.open(pl, ShopService.WEEKEND, 0))),
                new Card(KitData.Sprite.HB_C_DAILY, "Daily Shop", "Jeden Tag neu, mit Juwelen", null, go(pl -> items.open(pl, ShopService.DAILY, 0))));
        List<Card> tools = new ArrayList<>(List.of(
                new Card(KitData.Sprite.HB_WIRTSCHAFT, "Verkaufen", "Items verkaufen", null, go(pl -> SellMenu.open(plugin, pl))),
                new Card(KitData.Sprite.HB_C_SUCHE, "Suche", "Name, ID oder Material", null, go(pl -> plugin.prompts().ask(pl,
                        "Name, ID, Material oder Nummer eingeben:", text -> items.search(pl, text)))),
                new Card(KitData.Sprite.QU_BOX_HEAD, "Abholbox", "Gekaufte Items", null, go(pl -> new ShopStorageMenu(plugin, 0).open(pl))),
                new Card(KitData.Sprite.HB_BOERSE, "Börse", "Kurse und Anteile", null, go(pl -> new ExtraScreens(plugin).investments(pl)))));
        if (p.hasPermission("kuby.admin")) {
            tools.add(new Card(KitData.Sprite.HB_JOBS, "Bearbeiten", "Nur Admins", null, go(pl -> new ShopEditorMenu(plugin).open(pl))));
        }
        UiDialog.show(p, shop(plugin.economy().balance(p.getUniqueId()), cats, tools), 330);
    }

    private String n(String section) {
        int c = plugin.shop().items(section).size();
        return c + " Items";
    }

    public static Canvas shop(long coins, List<Card> cats, List<Card> tools) {
        Canvas c = new Canvas(W).heading("Shop", EMERALD);
        c.box(0, 0, W, 54, KitData.Box.CLOVER);
        c.sprite(10 + (32 - KitData.Sprite.HB_SHOP.width) / 2, 11 + (32 - KitData.Sprite.HB_SHOP.height) / 2, KitData.Sprite.HB_SHOP);
        c.headline(50, 12, "Shop", EMERALD);
        c.text(50, 36, "Preise folgen den Kursen der Börse", MINT, false);
        c.textRight(378, 14, Msg.coins(coins) + " $", GOLD, true);
        c.textRight(378, 26, Canvas.caps("Guthaben"), MINT, false);
        int y = 60, cw = (W - 8) / 3, ch = 60;
        for (int i = 0; i < cats.size(); i++) {
            Card k = cats.get(i);
            int x = (i % 3) * (cw + 4), cy = y + (i / 3) * (ch + 4);
            c.box(x, cy, cw, ch, KitData.Box.LIME_CARD, Canvas.Z_BUTTON);
            c.sprite(x + 6 + (32 - k.art().width) / 2, cy + 6 + (32 - k.art().height) / 2, k.art());
            c.text(x + 42, cy + 9, Canvas.fit(k.name(), true, cw - 48), CREAM, true);
            List<String> d = Canvas.wrap(k.desc(), cw - 48);
            for (int l = 0; l < Math.min(2, d.size()); l++) c.text(x + 42, cy + 21 + 10 * l, d.get(l), MINT, false);
            if (k.extra() != null) c.text(x + 8, cy + ch - 13, k.extra(), EMERALD, false);
            c.action(x, cy, cw, ch, k.click(), k.name() + " · " + k.desc());
        }
        y += ((cats.size() + 2) / 3) * (ch + 4) + 6;
        int tw = (W - 4 * (tools.size() - 1)) / Math.max(1, tools.size()), th = 44;
        for (int i = 0; i < tools.size(); i++) {
            Card k = tools.get(i);
            int x = i * (tw + 4);
            c.box(x, y, tw, th, KitData.Box.LIME, Canvas.Z_BUTTON);
            c.sprite(x + (tw - k.art().width) / 2, y + 2 + Math.max(0, (24 - k.art().height) / 2) - (k.art().height > 28 ? 4 : 0), k.art());
            c.textCenter(x + tw / 2, y + th - 11, Canvas.fit(k.name(), true, tw - 6), CREAM, true);
            c.action(x, y, tw, th, k.click(), k.name() + " · " + k.desc());
        }
        return c;
    }

    // =================================================================================================================
    // Markt
    // =================================================================================================================

    public void market(Player p) {
        if (!plugin.uiPack().active(p)) {
            new MarketMenu(plugin).open(p);
            return;
        }
        String mode = plugin.modes().modeOf(p.getUniqueId());
        List<MarketService.Listing> all = plugin.market().inMode(mode);
        UUID id = p.getUniqueId();
        long mine = all.stream().filter(l -> l.seller().equals(id)).count();
        List<Card> cards = List.of(
                new Card(KitData.Sprite.HB_MARKT, "Angebote", "Items anderer Spieler kaufen", all.size() + " Angebote",
                        go(pl -> new MarketMenu(plugin).open(pl))),
                new Card(KitData.Sprite.HB_WIRTSCHAFT, "Verkaufen", "Item in der Hand anbieten", null, go(this::sell)),
                new Card(KitData.Sprite.HB_TITEL, "Meine Angebote", "Klick im Markt zieht zurück", mine + " aktiv",
                        go(pl -> new MarketMenu(plugin).open(pl))),
                new Card(KitData.Sprite.QU_T_SIDE, "Aufträge", "Gesuche: liefern und kassieren", null, go(pl -> pl.performCommand("orders"))));
        List<Offer> latest = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && latest.size() < 5; i--) {
            MarketService.Listing l = all.get(i);
            latest.add(new Offer(Icons.of(l.item().getType(), KitData.Icon.PAPER), itemName(l), l.sellerName(), l.price()));
        }
        UiDialog.show(p, market(plugin.economy().balance(id), all.size(), cards, latest, go(pl -> new MarketMenu(plugin).open(pl)),
                go(pl -> pl.performCommand("abholbox"))), 330);
    }

    private static String itemName(MarketService.Listing l) {
        var meta = l.item().getItemMeta();
        String n = meta != null && meta.hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                : l.item().getType().name().toLowerCase().replace('_', ' ');
        return l.item().getAmount() + "x " + n;
    }

    /** Item in der Hand anbieten: Preis eingeben, dann /markt sell. */
    private void sell(Player p) {
        if (p.getInventory().getItemInMainHand().getType().isAir()) {
            Msg.send(p, "<red>Nimm das Item, das du verkaufen willst, in die Hand.");
            return;
        }
        CasinoInput.amount(p, "Verkaufen", List.of("<gray>Preis für das Item in deiner Hand:",
                        "<white>" + p.getInventory().getItemInMainHand().getAmount() + "x "
                                + p.getInventory().getItemInMainHand().getType().name().toLowerCase().replace('_', ' ')),
                1, 1_000_000_000L, "Anbieten", (pl, price) -> pl.performCommand("markt sell " + price), this::market);
    }

    public static Canvas market(long coins, int count, List<Card> cards, List<Offer> latest, Consumer<Player> browse,
                                Consumer<Player> pickup) {
        Canvas c = new Canvas(W).heading("Markt", AMBER);
        c.box(0, 0, W, 54, KitData.Box.WOOD);
        c.sprite(10 + (32 - KitData.Sprite.HB_MARKT.width) / 2, 11 + (32 - KitData.Sprite.HB_MARKT.height) / 2, KitData.Sprite.HB_MARKT);
        c.headline(50, 12, "Markt", AMBER);
        c.text(50, 36, "Spieler handeln untereinander · " + Msg.coins(count) + " Angebote", SAND, false);
        c.textRight(378, 14, Msg.coins(coins) + " $", GOLD, true);
        c.textRight(378, 26, Canvas.caps("Guthaben"), SAND, false);
        int y = 60, cw = (W - 4) / 2, ch = 52;
        for (int i = 0; i < cards.size(); i++) {
            Card k = cards.get(i);
            int x = (i % 2) * (cw + 4), cy = y + (i / 2) * (ch + 4);
            c.box(x, cy, cw, ch, KitData.Box.WOOD_CARD, Canvas.Z_BUTTON);
            c.sprite(x + 8 + (32 - k.art().width) / 2, cy + 10 + (32 - k.art().height) / 2, k.art());
            c.text(x + 46, cy + 9, Canvas.fit(k.name(), true, cw - 52), CREAM, true);
            c.text(x + 46, cy + 21, Canvas.fit(k.desc(), false, cw - 52), SAND, false);
            if (k.extra() != null) c.text(x + 46, cy + 34, k.extra(), AMBER, true);
            c.action(x, cy, cw, ch, k.click(), k.name() + " · " + k.desc());
        }
        y += ((cards.size() + 1) / 2) * (ch + 4) + 6;
        c.text(2, y, Canvas.caps("Neueste Angebote"), AMBER, true);
        c.line(4 + Canvas.width(Canvas.caps("Neueste Angebote"), true) + 4, y + 3, W - 8 - Canvas.width(Canvas.caps("Neueste Angebote"), true) - 4, AMBER);
        y += 12;
        if (latest.isEmpty()) {
            c.box(0, y, W, 26, KitData.Box.WOOD_CARD);
            c.text(10, y + 9, "Noch keine Angebote. Sei der Erste: Item in die Hand, „Verkaufen“.", SAND, false);
            y += 26 + 4;
        }
        for (Offer o : latest) {
            c.box(0, y, W, 22, KitData.Box.WOOD_CARD);
            c.icon(5, y + 3, o.icon(), false);
            c.text(26, y + 7, Canvas.fit(Canvas.lit(o.name()), true, 200), CREAM, true);
            c.text(232, y + 7, Canvas.fit(Canvas.lit(o.seller()), false, 80), SAND, false);
            c.textRight(W - 8, y + 7, Msg.coins(o.price()) + " $", GOLD, true);
            c.action(0, y, W, 22, browse, "Im Markt ansehen");
            y += 24;
        }
        y += 4;
        c.smallButton(W - 8 - 100, y, 100, KitData.Small.GOLD, "Abholbox", GOLD, true);
        c.action(W - 8 - 100, y, 100, KitData.SMALL_H, pickup, "Gekaufte und zurückgegebene Items");
        c.smallButton(W - 8 - 210, y, 100, KitData.Small.GOLD, "Alle Angebote", GOLD, true);
        c.action(W - 8 - 210, y, 100, KitData.SMALL_H, browse, "Alle Angebote");
        return c;
    }

    /** Selbsttest: volle Karten, lange Namen, leerer Markt. */
    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        String w = "W".repeat(30);
        List<Card> cats = new ArrayList<>();
        for (int i = 0; i < 6; i++) cats.add(new Card(KitData.Sprite.HB_C_BLOECKE, w, w + w, "999 Items", no));
        List<Card> tools = new ArrayList<>();
        for (int i = 0; i < 5; i++) tools.add(new Card(KitData.Sprite.HB_C_SUCHE, w, w, null, no));
        reg.accept("Shop Start", () -> shop(999_999_999L, cats, tools));
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 4; i++) cards.add(new Card(KitData.Sprite.HB_MARKT, w, w + w, "999.999 Angebote", no));
        List<Offer> offers = new ArrayList<>();
        for (int i = 0; i < 5; i++) offers.add(new Offer(KitData.Icon.DIAMOND, w + w, "W".repeat(16), 999_999_999L));
        reg.accept("Markt Start", () -> market(999_999_999L, 999_999, cards, offers, no, no));
        reg.accept("Markt Start leer", () -> market(0, 0, cards, List.of(), no, no));
    }
}
