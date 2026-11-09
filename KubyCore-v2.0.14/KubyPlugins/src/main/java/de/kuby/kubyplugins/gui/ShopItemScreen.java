package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ShopService;
import de.kuby.kubyplugins.service.TradingService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.ItemNames;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Die Items eines Shop-Bereichs als Dialog im Stil der Juwelenshop-Referenz: Kopf mit Zurück, Bereich und Guthaben,
 * Reiter für alle Bereiche, Karten 4 x 2 (Bild des Items, Name, Menge, Börsen-Trend, Preis, Knopf „Kaufen“ oder
 * „Dir fehlen …“), Seiten, unten Suche, Abholbox und Verkaufen. Wochenende (eigene Auswahl, einmal kaufbar, neu
 * würfeln) und Daily Shop (mit Juwelen) haben dieselben Karten. Ohne Pack: ShopSectionMenu (Truhe).
 */
public final class ShopItemScreen {

    static final int W = 390, PER_PAGE = 8;
    static final TextColor SUB = TextColor.color(0x9FA1A7), DESC = TextColor.color(0xD1D2D5), GREEN = TextColor.color(0x5BED79),
            WHITE = TextColor.color(0xFCFCFC), DARK = TextColor.color(0x201400), GOLD = TextColor.color(0xFCD45D),
            FARM = TextColor.color(0x6DE288), RED = TextColor.color(0xFC7986), BROWN = TextColor.color(0xEFB16A),
            BLUE = TextColor.color(0x62CAFC), TIP_CLICK = TextColor.color(0xFCFC54);

    /** Zustand des Knopfs einer Karte. */
    public enum State { BUY, MISSING, BOUGHT, OFF }

    /** Karte: Material, Name, Menge, Trend in Prozent (NaN = keiner), Preis, Juwelen statt $, Zustand, Klick, Tooltip. */
    public record Card(Material material, String name, int amount, double trend, long price, boolean jewels, State state,
                       Consumer<Player> click, String info) {}

    public record Tab(String label, boolean on, Consumer<Player> click) {}

    public record View(String title, String sub, TextColor accent, long coins, long jewels, boolean showJewels, List<Tab> tabs,
                       List<Card> cards, int page, int pages, String note, Consumer<Player> back, Consumer<Player> prev,
                       Consumer<Player> next, Consumer<Player> search, Consumer<Player> storage, Consumer<Player> sell,
                       String extraLabel, Consumer<Player> extra) {}

    private final KubyPlugins plugin;

    public ShopItemScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private static Consumer<Player> go(Consumer<Player> c) {
        return p -> {
            de.kuby.kubyplugins.ui.UiDialog.close(p);
            c.accept(p);
        };
    }

    static TextColor accent(String sec) {
        return switch (sec) {
            case "blocks" -> BROWN;
            case "food" -> TextColor.color(0xFCA25B);
            case "redstone" -> RED;
            case "deco" -> TextColor.color(0xFC99DA);
            case ShopService.WEEKEND -> GOLD;
            case ShopService.DAILY -> BLUE;
            default -> TextColor.color(0x7FD8FC);
        };
    }

    static String subtitle(String sec) {
        return switch (sec) {
            case "blocks" -> "Bausteine und Baumaterial";
            case "food" -> "Nahrung für unterwegs";
            case "redstone" -> "Schaltungen und Mechanik";
            case "deco" -> "Für dein Zuhause";
            case ShopService.WEEKEND -> "Nur dieses Wochenende, jedes Angebot einmal";
            case ShopService.DAILY -> "Jeden Tag neu, bezahlt mit Juwelen";
            default -> "Suchergebnisse";
        };
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    /** Bereich öffnen (blocks, food, redstone, deco, weekend, daily). */
    public void open(Player p, String sec, int page) {
        List<ShopService.Item> list = switch (sec) {
            case ShopService.WEEKEND -> plugin.shop().weekendPicks(p.getUniqueId());
            case ShopService.DAILY -> plugin.shop().dailyItems(p.getUniqueId());
            default -> plugin.shop().items(sec);
        };
        show(p, sec, ShopService.TITLES.getOrDefault(sec, sec), list, page, null);
    }

    /** Suchergebnisse. */
    public void search(Player p, String query) {
        show(p, "search", "Suche", plugin.shop().search(query), 0, query);
    }

    private void show(Player p, String sec, String title, List<ShopService.Item> list, int page, String query) {
        if (!plugin.uiPack().active(p)) {
            new ShopSectionMenu(plugin, title, sec, list, page).open(p);
            return;
        }
        UUID id = p.getUniqueId();
        boolean wk = ShopService.WEEKEND.equals(sec), dl = ShopService.DAILY.equals(sec);
        long coins = plugin.economy().balance(id), jewels = plugin.jewels().balance(id);
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE), pg = Math.max(0, Math.min(page, pages - 1));
        List<Card> cards = new ArrayList<>();
        TradingService ts = plugin.trading();
        for (int i = pg * PER_PAGE; i < Math.min(list.size(), pg * PER_PAGE + PER_PAGE); i++) {
            ShopService.Item it = list.get(i);
            boolean special = wk || dl;
            boolean active = special || plugin.investments().isActive(it.id());
            long price = special ? it.price() : plugin.investments().priceOf(it);
            boolean bought = wk ? plugin.shop().isBought(id, it.id()) : dl && plugin.shop().dailyBoughtBy(id, it.id());
            State st;
            if (bought) st = State.BOUGHT;
            else if (!active) st = State.OFF;
            else st = (dl ? jewels : coins) >= price ? State.BUY : State.MISSING;
            double trend = Double.NaN;
            if (!special && ts != null) {
                TradingService.Ticker t = ts.ticker(it.id());
                if (t != null) trend = ts.change24(t);
            }
            String name = it.name() != null ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Msg.parse(it.name()))
                    : ItemNames.de(it.material());
            long reactivate = it.price() * plugin.getConfig().getLong("economy.reactivate-multiplier", 10);
            String info = !active ? "Der Markt hat dieses Item aus dem Shop genommen. Wieder aufnehmen kostet " + Msg.coins(reactivate) + " $."
                    : special ? null : "Nr. " + plugin.shop().slotNumber(it.id()) + " · ID " + it.id();
            int pgf = pg;
            cards.add(new Card(it.material(), name, it.amount(), trend, price, dl, st, st == State.BOUGHT ? null : go(pl -> {
                String err = purchase(plugin, pl, sec, it);
                Sfx.play(plugin, pl, err == null ? Sfx.Kind.COIN : Sfx.Kind.ERROR);
                Msg.send(pl, err == null ? (active ? "<green>Gekauft! <gray>Hol es in der Abholbox ab." : "<green>Das Item ist wieder im Shop!")
                        : "<red>" + err);
                if (query != null) search(pl, query);
                else open(pl, sec, pgf);
            }), info));
        }
        List<Tab> tabs = new ArrayList<>();
        for (String s : ShopService.SECTIONS) {
            String label = switch (s) {
                case "deco" -> "Deko";
                case ShopService.DAILY -> "Daily";
                default -> ShopService.TITLES.get(s);
            };
            tabs.add(new Tab(label, s.equals(sec), go(pl -> open(pl, s, 0))));
        }
        String sub = query != null ? "„" + query + "“ · " + list.size() + " Treffer"
                : subtitle(sec) + (wk || dl ? "" : " · " + list.size() + " Items");
        String note = wk ? "Neu würfeln: " + plugin.shop().rerollCost() + " Juwelen, noch " + plugin.shop().rerollsLeft(id) + "x bis zum nächsten Wochenende"
                : dl ? "Neue Angebote jeden Tag um 0 Uhr, für alle gleich"
                : query != null ? null : "Preise folgen den Kursen der Börse · Gekauftes liegt in der Abholbox";
        String extraLabel = wk && !list.isEmpty() ? "Neu würfeln" : null;
        Consumer<Player> extra = extraLabel == null ? null : go(pl -> {
            String err = plugin.shop().reroll(pl);
            Sfx.play(plugin, pl, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
            Msg.send(pl, err == null ? "<green>Neue Auswahl für dieses Wochenende!" : "<red>" + err);
            open(pl, sec, 0);
        });
        int pgf = pg;
        UiDialog.show(p, canvas(new View(title, sub, accent(sec), coins, jewels, dl, query != null ? List.of() : tabs, cards, pg, pages,
                list.isEmpty() ? (query != null ? "Nichts gefunden." : "Hier gibt es gerade keine Angebote.") : note,
                go(pl -> new TradeScreens(plugin).shop(pl)),
                pg > 0 ? go(pl -> reopen(pl, sec, title, query, pgf - 1)) : null,
                pg < pages - 1 ? go(pl -> reopen(pl, sec, title, query, pgf + 1)) : null,
                go(pl -> plugin.prompts().ask(pl, "Name, ID, Material oder Nummer eingeben:", text -> search(pl, text))),
                go(pl -> new ShopStorageMenu(plugin, 0).open(pl)),
                go(pl -> SellMenu.open(plugin, pl)), extraLabel, extra)), 330);
    }

    private void reopen(Player p, String sec, String title, String query, int page) {
        if (query != null) show(p, sec, title, plugin.shop().search(query), page, query);
        else open(p, sec, page);
    }

    /**
     * Kauf eines Shop-Items (auch für die Truhe ohne Pack): bezahlen ($ oder im Daily Shop Juwelen), in die Abholbox
     * legen, Wochenende und Daily nur einmal. Vom Markt genommene Items werden stattdessen wieder aufgenommen.
     * Gibt die Fehlermeldung zurück oder null.
     */
    public static String purchase(KubyPlugins plugin, Player p, String sec, ShopService.Item it) {
        UUID id = p.getUniqueId();
        boolean wk = ShopService.WEEKEND.equals(sec), dl = ShopService.DAILY.equals(sec);
        if (!wk && !dl && !plugin.investments().isActive(it.id())) return plugin.investments().reactivate(id, it);
        long price = wk || dl ? it.price() : plugin.investments().priceOf(it);
        if (wk && plugin.shop().isBought(id, it.id())) return "Dieses Angebot hast du schon gekauft.";
        if (dl && plugin.shop().dailyBoughtBy(id, it.id())) return "Dieses Angebot hast du heute schon gekauft.";
        boolean paid = dl ? plugin.jewels().take(id, price) : plugin.economy().withdraw(id, price);
        if (!paid) return dl ? "Du brauchst " + Msg.coins(price) + " Juwelen." : "Nicht genug $ (" + Msg.coins(price) + " $).";
        if (!plugin.shopStorage().add(id, new ItemStack(it.material(), it.amount()))) {
            if (dl) plugin.jewels().add(id, price);
            else plugin.economy().deposit(id, price);
            return "Die Abholbox ist voll. Hol Items ab oder schalte eine Seite frei.";
        }
        if (wk) plugin.shop().markBought(id, it.id());
        if (!wk && !dl) plugin.investments().recordSale(it.id(), it.amount(), price);
        if (dl) plugin.shop().markDailyBought(id, it.id());
        return null;
    }

    // =================================================================================================================
    // Zeichnen
    // =================================================================================================================

    private static void center(Canvas c, int cx, int y, String text, TextColor color, boolean bold) {
        int w = Canvas.width(Canvas.clean(text), bold);
        c.text(cx - (w + 1) / 2, y, text, color, bold);
    }

    private static void center(Canvas c, int cx, int y, List<Span> spans) {
        int w = 0;
        for (Span s : spans) w += Canvas.width(Canvas.clean(s.text()), s.bold());
        c.text(cx - (w + 1) / 2, y, spans);
    }

    private static Component tip(Card k) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text(k.name() + (k.amount() > 1 ? " x" + k.amount() : ""), WHITE).decorate(TextDecoration.BOLD));
        b.append(Component.newline()).append(Component.text("Preis: " + Msg.coins(k.price()) + (k.jewels() ? " Juwelen" : " $"), GOLD));
        if (!Double.isNaN(k.trend())) {
            b.append(Component.newline()).append(Component.text(String.format(Locale.GERMANY, "Börse 24h: %+.1f %%", k.trend()),
                    k.trend() >= 0 ? GREEN : RED));
        }
        if (k.info() != null) b.append(Component.newline()).append(Component.text(k.info(), SUB));
        String click = switch (k.state()) {
            case BUY -> "Klick: kaufen";
            case MISSING -> k.jewels() ? "Nicht genug Juwelen" : "Nicht genug $";
            case BOUGHT -> "Schon gekauft";
            case OFF -> "Klick: wieder aufnehmen";
        };
        b.append(Component.newline()).append(Component.newline()).append(Component.text(click, TIP_CLICK));
        return b.build();
    }

    /** Wortteile, nach denen ein langes zusammengesetztes Wort umbrochen werden darf. */
    private static final String[] JOINTS = {"holz", "stein", "ziegel", "schiefer", "kupfer", "eisen", "gold", "diamant", "redstone",
            "glas", "beton", "keramik", "korallen", "erz", "netherit", "leder", "ketten", "laub", "block", "fackel", "pilz"};

    /** Name in der Karte: fett bis 84 px, sonst normal bis 88 px, länger zwei Zeilen (Leerzeichen, Bindestrich, Wortfuge). */
    private static void name(Canvas c, int cx, int y0, String name, TextColor color) {
        if (Canvas.width(name, true) <= 84) {
            center(c, cx, y0 + 46, name, color, true);
            return;
        }
        if (Canvas.width(name, false) <= 88) {
            center(c, cx, y0 + 46, name, color, false);
            return;
        }
        int cut = -1;
        boolean keepChar = false;
        // an einem Leerzeichen oder Bindestrich, möglichst weit hinten, so dass die erste Zeile passt
        for (int i = name.length() - 1; i > 0; i--) {
            char ch = name.charAt(i);
            if ((ch == ' ' || ch == '-') && Canvas.width(name.substring(0, ch == '-' ? i + 1 : i), false) <= 88) {
                cut = i;
                keepChar = ch == '-';
                break;
            }
        }
        String first, second;
        if (cut > 0) {
            first = name.substring(0, keepChar ? cut + 1 : cut);
            second = name.substring(cut + 1);
        } else {
            // Wortfuge: nach einem bekannten Wortteil (Eichenholz|bretter), sonst mit Bindestrich
            int at = -1;
            String low = name.toLowerCase(Locale.GERMAN);
            for (String j : JOINTS) {
                int k = low.indexOf(j);
                while (k >= 0) {
                    int e = k + j.length();
                    if (e < name.length() - 2 && e > at && Canvas.width(name.substring(0, e) + "-", false) <= 88) at = e;
                    k = low.indexOf(j, k + 1);
                }
            }
            if (at < 0) {
                at = name.length();
                while (at > 1 && Canvas.width(name.substring(0, at) + "-", false) > 88) at--;
            }
            first = name.substring(0, at) + "-";
            second = name.substring(at);
        }
        center(c, cx, y0 + 42, first, color, false);
        center(c, cx, y0 + 51, Canvas.fit(second, false, 88), color, false);
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Shop", v.accent());
        // Kopf: Zurück, Titel, Unterzeile, Guthaben
        c.box(0, 0, W, 38, KitData.Box.JSS_HEAD_SHORT);
        c.sprite(8, 9, KitData.Sprite.JSS_BACK);
        if (v.back() != null) c.action(8, 9, 22, 22, v.back(), "Zurück zum Shop");
        c.text(40, 10, v.title().toUpperCase(Locale.GERMAN), v.accent(), true);
        String n = v.showJewels() ? Msg.coins(v.jewels()) : Msg.coins(v.coins()) + " $";
        int nw = Canvas.width(n, true), nx = 380 - nw, lx = Math.min(322, nx - 52);
        if (v.showJewels()) c.sprite(lx - 20, 11, KitData.Sprite.JSS_EMERALD);
        else c.icon(lx - 20, 11, KitData.Icon.GOLD_NUGGET, false);
        c.text(lx, 9, "Dein", SUB, false);
        c.text(lx, 19, v.showJewels() ? "Juwelen" : "Guthaben", SUB, false);
        c.text(nx, 14, n, v.showJewels() ? GREEN : GOLD, true);
        c.text(40, 21, Canvas.fit(v.sub(), false, lx - 20 - 46), SUB, false);

        // Reiter
        int y = 42;
        if (!v.tabs().isEmpty()) {
            int total = -4;
            for (Tab t : v.tabs()) total += Canvas.width(t.label(), t.on()) + 16 + 4;
            int x = (W - total) / 2;
            for (Tab t : v.tabs()) {
                int w = Canvas.width(t.label(), t.on()) + 16;
                c.box(x, y, w, 20, t.on() ? KitData.Box.JSS_TAB_BROWN : KitData.Box.JSS_TAB_OFF);
                center(c, x + w / 2, y + 6, t.label(), t.on() ? DARK : DESC, t.on());
                if (!t.on() && t.click() != null) c.action(x, y, w, 20, t.click(), Component.text(t.label(), WHITE));
                x += w + 4;
            }
        }
        y = 64;

        // Karten 4 x 2
        for (int k = 0; k < v.cards().size() && k < PER_PAGE; k++) {
            Card card = v.cards().get(k);
            int x0 = 1 + 98 * (k % 4), y0 = y + 99 * (k / 4);
            KitData.Box style = card.state() == State.OFF ? KitData.Box.JSS_GRAY : card.jewels() ? KitData.Box.JSS_K_RARE
                    : card.state() == State.BOUGHT ? KitData.Box.JSS_K_COMMON : KitData.Box.JSS_K_FURNITURE;
            c.box(x0, y0, 94, 97, style);
            c.item(x0 + 31, y0 + 9, card.material(), true, KitData.Icon.PAPER);
            if (card.amount() > 1) c.textRight(x0 + 90, y0 + 3, "x" + card.amount(), WHITE, true);
            if (!Double.isNaN(card.trend())) {
                String tr = (card.trend() >= 0 ? "▲" : "▼") + String.format(Locale.GERMANY, "%.1f%%", Math.abs(card.trend()));
                c.text(x0 + 5, y0 + 3, tr, card.trend() >= 0 ? GREEN : RED, false);
            }
            name(c, x0 + 47, y0, card.name(), card.state() == State.OFF ? SUB : GOLD);
            // Preis
            String pr = Msg.coins(card.price());
            int pw = Canvas.width(pr, true);
            if (card.jewels()) {
                int px = x0 + 47 - (17 + pw + 1) / 2;
                c.sprite(px, y0 + 59, KitData.Sprite.JSS_EMERALD_PRICE);
                c.text(px + 17, y0 + 63, pr, GREEN, true);
            } else {
                String full = pr + " $";
                int fw = Canvas.width(full, true);
                c.text(x0 + 47 - (fw + 1) / 2, y0 + 63, full, card.state() == State.MISSING ? RED : GOLD, true);
            }
            // Knopf
            int bx = x0 + 4, by = y0 + 77, bw = 86, cx = bx + bw / 2;
            switch (card.state()) {
                case BUY -> {
                    c.box(bx, by, bw, 18, KitData.Box.JSS_CB_FARM, 1);
                    center(c, cx, by + 5, "KAUFEN", FARM, true);
                }
                case MISSING -> {
                    c.box(bx, by, bw, 18, KitData.Box.JSS_KB, 1);
                    long have = card.jewels() ? v.jewels() : v.coins();
                    String miss = Msg.coins(Math.max(0, card.price() - have));
                    List<Span> full = List.of(Span.of("Dir fehlen ", DESC), Span.of(miss, card.jewels() ? GREEN : GOLD));
                    center(c, cx, by + 5, Canvas.width(full) <= bw - 8 ? full : List.of(Span.of("-" + miss, card.jewels() ? GREEN : GOLD)));
                }
                case BOUGHT -> {
                    c.box(bx, by, bw, 18, KitData.Box.JSS_KB, 1);
                    center(c, cx, by + 5, "✔ Gekauft", GREEN, false);
                }
                case OFF -> {
                    c.box(bx, by, bw, 18, KitData.Box.JSS_CB_GOLD, 1);
                    center(c, cx, by + 5, "AUFNEHMEN", GOLD, true);
                }
            }
            if (card.click() != null) c.action(x0, y0, 94, 97, card.click(), tip(card));
        }
        if (v.cards().isEmpty()) center(c, W / 2, y + 40, v.note() == null ? "Hier gibt es nichts." : v.note(), SUB, false);

        // Seiten
        int py = y + 2 * 99 + 2;
        center(c, W / 2, py, "Seite " + (v.page() + 1) + " von " + Math.max(1, v.pages()), SUB, false);
        if (v.prev() != null) {
            c.text(10, py, "« Zurück", v.accent(), true);
            c.action(8, py - 3, 60, 13, v.prev(), "Vorige Seite");
        }
        if (v.next() != null) {
            c.textRight(382, py, "Weiter »", v.accent(), true);
            c.action(322, py - 3, 60, 13, v.next(), "Nächste Seite");
        }
        // Werkzeuge und Hinweis
        int ty = py + 14, n4 = v.extraLabel() == null ? 3 : 4, tw = (W - 4 * (n4 - 1)) / n4;
        String[] labels = {"Suche", "Abholbox", "Verkaufen", v.extraLabel()};
        @SuppressWarnings("unchecked")
        Consumer<Player>[] acts = new Consumer[]{v.search(), v.storage(), v.sell(), v.extra()};
        for (int i = 0; i < n4; i++) {
            int x = i * (tw + 4);
            KitData.Small st = i == 3 ? KitData.Small.GOLD : KitData.Small.DARK;
            c.smallButton(x, ty, tw, st, labels[i], i == 3 ? GOLD : DESC, i == 3);
            if (acts[i] != null) c.action(x, ty, tw, KitData.SMALL_H, acts[i], labels[i]);
        }
        if (v.note() != null && !v.cards().isEmpty()) center(c, W / 2, ty + KitData.SMALL_H + 4, Canvas.fit(v.note(), false, W - 8), SUB, false);
        return c;
    }

    // =================================================================================================================
    // Selbsttest
    // =================================================================================================================

    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        List<Tab> tabs = new ArrayList<>();
        for (String s : ShopService.SECTIONS) tabs.add(new Tab(ShopService.TITLES.get(s), s.equals("blocks"), no));
        Material[] ms = {Material.COBBLESTONE, Material.OAK_PLANKS, Material.GLASS, Material.STONE_BRICKS, Material.CHEST, Material.REDSTONE,
                Material.PLAYER_HEAD, Material.WHITE_BED};
        State[] sts = State.values();
        List<Card> cards = new ArrayList<>();
        for (int k = 0; k < ms.length; k++) {
            cards.add(new Card(ms[k], k % 2 == 0 ? "W".repeat(30) : "Wwwwwwww Wwwwwwwww Wwwwww", 64, k % 3 == 0 ? Double.NaN : (k % 2 == 0 ? 99.9 : -99.9),
                    999_999_999L, k == 5, sts[k % sts.length], no, "W".repeat(60)));
        }
        reg.accept("Shop Items", () -> canvas(new View("Dekoration", "W".repeat(80), BROWN, 999_999_999L, 999_999L, false, tabs, cards, 98, 99,
                "W".repeat(100), no, no, no, no, no, no, "Neu würfeln", no)));
        reg.accept("Shop Items leer", () -> canvas(new View("Suche", "", BLUE, 0, 0, true, List.of(), List.of(), 0, 1, "Nichts gefunden.",
                no, null, null, no, no, no, null, null)));
    }
}
