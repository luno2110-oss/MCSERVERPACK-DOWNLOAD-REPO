package de.kuby.kubysurvival.shop;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.util.I18n;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Survival-Juwelenshop auf dem Dialog-Kit wie in der Referenz („Referenz2“): Übersicht mit sechs Karten (Ränge,
 * Farmpass, Crates, Vorteilspakete, Möbel, Pets) und drei Knöpfen (Als Geschenk, Tokens, Zum Store); Unterseiten
 * Farmpass, Crates (Zeilen mit Menge), Möbel und Pets (Reiter, Raster 4 x 2 mit Seiten) und Tokens (drei Karten).
 * Ränge und Pakete haben keine eigene Referenz und folgen den Zeilen bzw. Karten der anderen Seiten.
 *
 * <p>Nur Zeichnen: die Daten kommen als {@link View}, Klicks über {@link Actions} (null in Vorschau und Selbsttest).
 * Texte stehen auf Deutsch, die Sprache des Spielers setzt das Kit beim Zeichnen ein.
 */
public final class ShopCanvas {

    private ShopCanvas() {
    }

    public enum Page { HOME, RANKS, FARMPASS, CRATES, PACKS, FURNITURE, PETS, TOKENS }

    /** Knopf eines Angebots. */
    public enum State { MISSING, BUY, ARMED, OWNED, INCLUDED }

    public static final int W = 390;
    static final TextColor SUB = TextColor.color(0x9FA1A7), DESC = TextColor.color(0xD1D2D5),
            GREEN = TextColor.color(0x5BED79), WHITE = TextColor.color(0xFCFCFC), DARK = TextColor.color(0x201400),
            GOLD = TextColor.color(0xFCD45D), FARM = TextColor.color(0x6DE288), VIOLET = TextColor.color(0xCD89FC),
            RED = TextColor.color(0xFC7986), BROWN = TextColor.color(0xEFB16A), PINK = TextColor.color(0xFC99DA),
            TOKEN = TextColor.color(0xFCC45B), STORE = TextColor.color(0x6EB6FC), BLUE = TextColor.color(0x62CAFC),
            TIP_CLICK = TextColor.color(0xFCFC54), STRUCK = TextColor.color(0x797D84);

    /** Zeile (Ränge, Crates): count = Schlüssel je Kauf (Anzeige „(x5)“), amounts = wählbare Mengen. */
    public record Row(String id, String name, String box, String icon, TextColor color, List<String> lines,
                      long price, State state, int count, int amount, int[] amounts) {}

    /** Karte im Raster (Möbel, Pets): sprite = Bild im 30-px-Feld, box = Kartenstil. */
    public record Card(String id, String name, String sprite, String box, TextColor color, long price, State state) {}

    public record Tab(String id, String label) {}

    /** Token-Paket: perToken = Juwelen je Token, instead = Preis ohne Rabatt (0 = keiner). */
    public record TokenPack(String id, int tokens, long price, long perToken, long instead, String box, String sprite,
                            TextColor color, boolean best, State state) {}

    /** Vorteilspaket: Zeilen mit Inhalt (Schlüssel). */
    public record Pack(String id, String name, String box, String sprite, TextColor color, List<Span> lines, long price,
                       State state) {}

    /** Farmpass-Seite: Vorteile links/rechts als (weiß, grau). */
    public record Farmpass(String priceText, String head, List<String[]> left, List<String[]> right) {}

    public record View(Page page, long jewels, long tokens, String giftTo, String storeText, List<Row> rows,
                       List<Tab> tabs, int tab, String sub, List<Card> cards, int pageNo, int pages,
                       List<TokenPack> tokenPacks, List<Pack> packs, Farmpass farmpass) {}

    public interface Actions {
        Consumer<Player> page(Page page);

        Consumer<Player> buy(String id);

        Consumer<Player> amount(String id, int amount);

        Consumer<Player> tab(int tab);

        Consumer<Player> turn(int pageNo);

        Consumer<Player> gift();

        Consumer<Player> farmpassMenu();

        Consumer<Player> monthPets();

        String storeUrl();

        String aboUrl();
    }

    // =================================================================================================================
    // Hilfen
    // =================================================================================================================

    /** Kasten-Stil: Name aus dem Survival-Satz (jss_), mit „js_“ davor aus dem SMP-Satz. */
    static KitData.Box box(String style) {
        try {
            String s = style.toUpperCase(Locale.ROOT);
            return KitData.Box.valueOf(s.startsWith("JS_") ? s : "JSS_" + s);
        } catch (IllegalArgumentException e) {
            return KitData.Box.JSS_K_FURNITURE;
        }
    }

    static KitData.Sprite sprite(String name) {
        try {
            String s = name.toUpperCase(Locale.ROOT);
            return KitData.Sprite.valueOf(s.startsWith("JS_") ? s : "JSS_" + s);
        } catch (IllegalArgumentException e) {
            return KitData.Sprite.JSS_EMERALD;
        }
    }

    /** Text mittig um cx (bei ungerader Breite 1 px weiter links, wie in der Referenz). */
    private static void center(Canvas c, int cx, int y, String text, TextColor color, boolean bold) {
        int w = Canvas.width(Canvas.clean(text), bold);
        c.text(cx - (w + 1) / 2, y, text, color, bold);
    }

    private static void center(Canvas c, int cx, int y, List<Span> spans) {
        int w = 0;
        for (Span s : spans) w += Canvas.width(Canvas.clean(s.text()), s.bold());
        c.text(cx - (w + 1) / 2, y, spans);
    }

    private static Component tip(String title, String text, String click) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text(title, WHITE).decorate(TextDecoration.BOLD));
        if (text != null && !text.isBlank()) {
            for (String l : Canvas.wrap(text, 200)) b.append(Component.newline()).append(Component.text(l, DESC));
        }
        if (click != null) b.append(Component.newline()).append(Component.newline()).append(Component.text(click, TIP_CLICK));
        return b.build();
    }

    /** Rechts im Kopf: Smaragd, „Deine / Juwelen“, Anzahl (wie im SMP-Shop). Gibt die linke Kante zurück. */
    private static int balance(Canvas c, int y0, long jewels) {
        String n = Msg.coins(jewels);
        int nw = Canvas.width(n, true);
        int nx = 380 - nw;
        int lx = Math.min(328, nx - 45);
        c.sprite(lx - 20, y0 + 11, KitData.Sprite.JSS_EMERALD);
        c.text(lx, y0 + 9, "Deine", SUB, false);
        c.text(lx, y0 + 19, "Juwelen", SUB, false);
        c.text(nx, y0 + 14, n, GREEN, true);
        return lx - 20;
    }

    /** Kopf der Unterseiten: kurz (38) mit Zurück-Knopf, Titel und Untertitel; Farmpass hoch (46) mit Bild. */
    private static void subHead(Canvas c, View v, String title, TextColor color, String sub, Actions a) {
        c.box(0, 0, W, 38, KitData.Box.JSS_HEAD_SHORT);
        c.sprite(8, 9, KitData.Sprite.JSS_BACK);
        if (a != null) c.action(8, 9, 22, 22, a.page(Page.HOME), Component.text("Zurück", WHITE));
        c.text(40, 10, I18n.ctx(title).toUpperCase(Locale.GERMAN), color, true);
        c.text(40, 21, v.giftTo() != null ? "Geschenk für " + v.giftTo() : sub, SUB, false);
        balance(c, 0, v.jewels());
    }

    /** Knopf unten in einer Karte oder Zeile: „Dir fehlen …“, „Kaufen“, „Bestätigen“, „Besitzt du“. */
    private static void button(Canvas c, Actions a, int x, int y, int w, KitData.Box style, State st, long price,
                               long jewels, String id, Component tip) {
        int cx = x + w / 2;
        switch (st) {
            case MISSING -> {
                c.box(x, y, w, 18, style, 1);
                String n = Msg.coins(price - jewels);
                List<Span> full = List.of(Span.of("Dir fehlen ", DESC), Span.of(n, GREEN));
                // passt „Dir fehlen …“ nicht auf den Knopf, steht nur der Betrag mit Minus da
                center(c, cx, y + 5, Canvas.width(full) <= w - 8 ? full : List.of(Span.of("-" + n, GREEN)));
            }
            case BUY -> {
                c.box(x, y, w, 18, KitData.Box.JSS_CB_FARM, 1);
                center(c, cx, y + 5, "KAUFEN", FARM, true);
            }
            case ARMED -> {
                c.box(x, y, w, 18, KitData.Box.JSS_CB_GOLD, 1);
                center(c, cx, y + 5, "BESTÄTIGEN", GOLD, true);
            }
            case OWNED -> {
                c.box(x, y, w, 18, style, 1);
                center(c, cx, y + 5, "Besitzt du", GREEN, false);
            }
            case INCLUDED -> {
                c.box(x, y, w, 18, style, 1);
                center(c, cx, y + 5, "Enthalten", SUB, false);
            }
        }
        if (a != null && st != State.OWNED && st != State.INCLUDED) c.action(x, y, w, 18, a.buy(id), tip);
    }

    private static String stateText(State st) {
        return switch (st) {
            case MISSING -> "Nicht genug Juwelen";
            case BUY -> "Klick: kaufen (danach bestätigen)";
            case ARMED -> "Nochmal klicken: jetzt kaufen";
            case OWNED -> "Hast du schon";
            case INCLUDED -> "In deinem Rang enthalten";
        };
    }

    /** Smaragd und Preis, zusammen mittig um cx. */
    private static void price(Canvas c, int cx, int y, long price) {
        String n = Msg.coins(price);
        int w = Canvas.width(n, true);
        int x = cx - (17 + w + 1) / 2;
        c.sprite(x, y, KitData.Sprite.JSS_EMERALD_PRICE);
        c.text(x + 17, y + 4, n, GREEN, true);
    }

    public static Canvas canvas(View v, Actions a) {
        return switch (v.page()) {
            case HOME -> home(v, a);
            case FARMPASS -> farmpass(v, a);
            case CRATES, RANKS -> rows(v, a);
            case FURNITURE, PETS -> grid(v, a);
            case TOKENS -> tokens(v, a);
            case PACKS -> packs(v, a);
        };
    }

    /** Höhe des Dialogs je Seite (für UiDialog.show). */
    public static int height(Page page) {
        return switch (page) {
            case HOME -> 292;
            case FARMPASS -> 248;
            case CRATES -> 222;
            case RANKS -> 276;
            case FURNITURE, PETS -> 290;
            case TOKENS -> 232;
            case PACKS -> 232;
        };
    }

    // =================================================================================================================
    // Übersicht
    // =================================================================================================================

    private static Canvas home(View v, Actions a) {
        Canvas c = new Canvas(W).heading("Juwelenshop", TextColor.color(0x54FC54));
        c.box(0, 0, W, 46, KitData.Box.JSS_HEAD);
        c.sprite(4, 7, KitData.Sprite.JSS_EMERALD_BIG);
        c.sprite(44, 8, KitData.Sprite.JSS_TITLE);
        c.text(46, 30, v.giftTo() != null ? "→ Geschenk für " + v.giftTo()
                : "→ Ränge · Farmpass · Crates · Pakete · Möbel · Pets", SUB, false);
        balance(c, 0, v.jewels());
        card(c, a, 0, 50, "gold", KitData.Sprite.JSS_OV_RANKS, 41, 9, "RÄNGE", GOLD, "Pets, Homes & Plätze", Page.RANKS);
        card(c, a, 132, 50, "farm", KitData.Sprite.JSS_OV_FARM, 39, 9, "FARMPASS", FARM, "Fliegen & Monats-Pet",
                Page.FARMPASS);
        card(c, a, 264, 50, "crate", KitData.Sprite.JSS_OV_CRATE, 40, 7, "CRATES", VIOLET, "Keys in jeder Menge",
                Page.CRATES);
        card(c, a, 0, 158, "red", KitData.Sprite.JSS_OV_PACKS, 39, 7, "VORTEILSPAKETE", RED, "Viele Keys, Sparpreis",
                Page.PACKS);
        card(c, a, 132, 158, "brown", KitData.Sprite.JSS_OV_FURNITURE, 39, 11, "MÖBEL", BROWN, "Selbst aussuchen",
                Page.FURNITURE);
        card(c, a, 264, 158, "pink", KitData.Sprite.JSS_OV_PETS, 42, 7, "PETS", PINK, "Haustiere für Juwelen", Page.PETS);
        boolean gift = v.giftTo() != null;
        bottom(c, 0, "gift", KitData.Sprite.JSS_GIFT, gift ? "Für " + v.giftTo() : "Als Geschenk", FARM,
                gift ? "Klick: beenden" : "für Freunde");
        if (a != null) c.action(0, 261, 126, 27, a.gift(), gift
                ? tip("Geschenk beenden", "Käufe sind wieder für dich.", "Klick")
                : tip("Als Geschenk", "Ränge, Keys, Pakete, Möbel, Pets und Tokens für einen anderen Spieler kaufen, "
                + "bezahlt mit deinen Juwelen.", "Klick: Spieler wählen"));
        bottom(c, 132, "token", KitData.Sprite.JSS_TOKEN, "Tokens", TOKEN, "für Juwelen");
        if (a != null) c.action(132, 261, 126, 27, a.page(Page.TOKENS), tip("Tokens", "Tokens für Juwelen, sofort auf "
                + "dein Konto.", "Klick: öffnen"));
        bottom(c, 264, "store", KitData.Sprite.JSS_CART, "Zum Store", STORE, "Juwelen holen");
        if (a != null && !a.storeUrl().isBlank()) {
            c.actionUrl(264, 261, 126, 27, a.storeUrl(), tip("Zum Store", "Juwelen gibt es im Store: " + v.storeText(),
                    "Klick: Link öffnen"));
        }
        return c;
    }

    private static void card(Canvas c, Actions a, int x, int y, String style, KitData.Sprite art, int ax, int ay,
                             String title, TextColor color, String sub, Page target) {
        c.box(x, y, 126, 99, box("c_" + style));
        c.sprite(x + ax, y + ay, art);
        center(c, x + 63, y + 58, title, color, true);
        center(c, x + 63, y + 69, sub, DESC, false);
        c.box(x + 6, y + 79, 114, 18, box("cb_" + style), 1);
        center(c, x + 63, y + 84, "ANSEHEN", color, true);
        c.text(x + 108, y + 84, "›", color, true);
        if (a != null) c.action(x, y, 126, 99, a.page(target), tip(title, sub, "Klick: öffnen"));
    }

    private static void bottom(Canvas c, int x, String style, KitData.Sprite icon, String title, TextColor color,
                               String sub) {
        c.box(x, 261, 126, 27, box("b_" + style));
        c.sprite(x + 3, 264, icon);
        c.text(x + 29, 266, Canvas.fit(title, true, 84), color, true);
        c.text(x + 29, 275, sub, SUB, false);
        c.text(x + 114, 272, "›", color, true);
    }

    // =================================================================================================================
    // Farmpass
    // =================================================================================================================

    private static Canvas farmpass(View v, Actions a) {
        Canvas c = new Canvas(W).heading("Juwelenshop", TextColor.color(0x54FC54));
        Farmpass f = v.farmpass();
        c.box(0, 0, W, 46, KitData.Box.JSS_HEAD);
        c.sprite(8, 9, KitData.Sprite.JSS_BACK);
        if (a != null) c.action(8, 9, 22, 22, a.page(Page.HOME), Component.text("Zurück", WHITE));
        c.sprite(38, 7, KitData.Sprite.JSS_FP_ICON);
        c.text(76, 13, "FARMPASS", FARM, true);
        c.text(76, 29, v.giftTo() != null ? "Geschenk für " + v.giftTo() : f.head(), SUB, false);
        balance(c, 0, v.jewels());
        list(c, 0, f.left());
        list(c, 198, f.right());
        // Store: Preis, Abo, Knopf
        c.box(0, 135, 192, 72, KitData.Box.JSS_FP_STORE);
        c.sprite(3, 138, KitData.Sprite.JSS_FP_CART);
        c.text(34, 146, "IM STORE", STORE, true);
        c.text(12, 162, f.priceText(), DESC, false);
        c.text(12, 173, "Abo, jederzeit kündbar", SUB, false);
        c.box(6, 185, 180, 18, KitData.Box.JSS_FP_STOREBTN, 1);
        center(c, 90, 190, "Im Store holen", STORE, true);
        c.text(173, 190, "›", STORE, true);
        if (a != null && !a.storeUrl().isBlank()) {
            c.actionUrl(6, 185, 180, 18, a.storeUrl(), tip("Farmpass im Store", f.priceText() + ", Abo, jederzeit kündbar.",
                    "Klick: Link öffnen"));
        }
        wide(c, 198, 135, "fp_pets", KitData.Sprite.JSS_FP_EGG, "Monats-Pets", VIOLET, "Jeden Monat eins für immer");
        if (a != null) c.action(198, 135, 192, 27, a.monthPets(), tip("Monats-Pets", "Für jeden bezahlten Monat suchst "
                + "du dir ein Haustier aus, es bleibt für immer.", "Klick: öffnen"));
        wide(c, 198, 180, "fp_menu", KitData.Sprite.JSS_FP_LEAF, "Farmpass-Menü öffnen", FARM, "Alles im Überblick");
        if (a != null) c.action(198, 180, 192, 27, a.farmpassMenu(), tip("Farmpass-Menü", "Laufzeit, Wochenkiste, "
                + "Fliegen und Haustiere.", "Klick: öffnen"));
        wide(c, 0, 216, "fp_abo", KitData.Sprite.JSS_FP_CART2, "Abo verwalten", STORE, "Ansehen oder kündigen");
        if (a != null && !a.aboUrl().isBlank()) {
            c.actionUrl(0, 216, 192, 27, a.aboUrl(), tip("Abo verwalten", "Dein Abo im Store ansehen oder kündigen.",
                    "Klick: Link öffnen"));
        }
        return c;
    }

    private static void list(Canvas c, int x, List<String[]> lines) {
        c.box(x, 50, 192, 82, KitData.Box.JSS_FP_LIST);
        int y = 60;
        for (String[] l : lines) {
            if (y > 115) break;
            List<Span> spans = new ArrayList<>();
            spans.add(Span.of(l[0], WHITE));
            if (l.length > 1 && !l[1].isBlank()) spans.add(Span.of(" " + l[1], SUB));
            c.text(x + 8, y, "✔", FARM, false);
            c.text(x + 18, y, spans);
            y += 11;
        }
    }

    /** Breiter Knopf (27 hoch) mit Bild, Titel, Untertitel und Pfeil. */
    private static void wide(Canvas c, int x, int y, String style, KitData.Sprite icon, String title, TextColor color,
                             String sub) {
        c.box(x, y, 192, 27, box(style));
        c.sprite(x + 3, y + 3, icon);
        c.text(x + 34, y + 5, title, color, true);
        c.text(x + 34, y + 14, sub, SUB, false);
        c.text(x + 178, y + 11, "›", color, true);
    }

    // =================================================================================================================
    // Zeilen: Crates und Ränge
    // =================================================================================================================

    private static Canvas rows(View v, Actions a) {
        Canvas c = new Canvas(W).heading("Juwelenshop", TextColor.color(0x54FC54));
        boolean ranks = v.page() == Page.RANKS;
        subHead(c, v, ranks ? "Ränge" : "Crates", ranks ? GOLD : VIOLET, v.sub(), a);
        int y = 41;
        for (Row r : v.rows()) {
            KitData.Box b = box(r.box());
            c.box(0, y, W, 48, b);
            KitData.Sprite icon = sprite(r.icon());
            c.sprite(24 - (icon.width + 1) / 2, y + 24 - (icon.height + 1) / 2, icon);
            List<Span> title = new ArrayList<>();
            // Leerzeichen vor „(x5)“ noch fett (1 px breiter), wie in der Referenz
            // erst übersetzen, dann groß (sonst wird nur „RANG“ zu „RANK“)
            String name = I18n.ctx(r.name()).toUpperCase(Locale.GERMAN);
            title.add(Span.bold(r.count() > 1 ? name + " " : name, r.color()));
            if (r.count() > 1) title.add(Span.of("(x" + r.count() + ")", SUB));
            c.text(48, y + 9, title);
            if (!r.lines().isEmpty()) c.text(48, y + 20, Canvas.fit(r.lines().get(0), false, 216), DESC, false);
            if (r.amounts() != null && r.amounts().length > 0) {
                c.text(48, y + 32, "Menge", SUB, false);
                for (int i = 0; i < r.amounts().length; i++) {
                    int px = 84 + 27 * i;
                    boolean on = r.amounts()[i] == r.amount();
                    c.sprite(px, y + 31, on ? (r.box().contains("farm") ? KitData.Sprite.JSS_PILL_GREEN
                            : KitData.Sprite.JSS_PILL_VIOLET) : KitData.Sprite.JSS_PILL_OFF);
                    center(c, px + 12, y + 34, String.valueOf(r.amounts()[i]), on ? DARK : DESC, false);
                    if (a != null && !on) c.action(px, y + 31, 24, 14, a.amount(r.id(), r.amounts()[i]),
                            Component.text("Menge " + r.amounts()[i], WHITE));
                }
            } else if (r.lines().size() > 1) {
                c.text(48, y + 31, Canvas.fit(r.lines().get(1), false, 216), DESC, false);
            }
            price(c, 325, y + 7, r.price());
            button(c, a, 270, y + 22, 110, KitData.Box.JSS_GRAY, r.state(), r.price(), v.jewels(), r.id(),
                    rowTip(v, r));
            y += 54;
        }
        center(c, W / 2, y + 2, ranks ? "Mit Rang zahlst du beim Aufstieg nur den Unterschied."
                : "Die Schlüssel landen sofort bei dir. Öffnen am Spawn.", SUB, false);
        return c;
    }

    private static Component rowTip(View v, Row r) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text(r.name() + (r.count() > 1 ? " (x" + r.count() + ")" : ""), r.color())
                .decorate(TextDecoration.BOLD));
        for (String l : r.lines()) b.append(Component.newline()).append(Component.text(l, DESC));
        b.append(Component.newline()).append(Component.newline());
        if (v.giftTo() != null) b.append(Component.text("Geschenk für " + v.giftTo(), FARM)).append(Component.newline());
        b.append(Component.text(stateText(r.state()), TIP_CLICK));
        return b.build();
    }

    // =================================================================================================================
    // Raster: Möbel und Pets
    // =================================================================================================================

    private static Canvas grid(View v, Actions a) {
        Canvas c = new Canvas(W).heading("Juwelenshop", TextColor.color(0x54FC54));
        boolean pets = v.page() == Page.PETS;
        TextColor accent = pets ? PINK : BROWN;
        subHead(c, v, pets ? "Pets" : "Möbel", accent, v.sub(), a);
        // Reiter: Breite = Text + 16 (gewählter Reiter fett), 4 px Abstand, mittig
        int total = -4;
        for (int i = 0; i < v.tabs().size(); i++) total += Canvas.width(v.tabs().get(i).label(), i == v.tab()) + 16 + 4;
        int x = (W - total) / 2;
        for (int i = 0; i < v.tabs().size(); i++) {
            boolean on = i == v.tab();
            String label = v.tabs().get(i).label();
            int w = Canvas.width(label, on) + 16;
            c.box(x, 42, w, 20, on ? (pets ? KitData.Box.JSS_TAB_PINK : KitData.Box.JSS_TAB_BROWN) : KitData.Box.JSS_TAB_OFF);
            center(c, x + w / 2, 48, label, on ? DARK : DESC, on);
            if (a != null && !on) c.action(x, 42, w, 20, a.tab(i), Component.text(label, WHITE));
            x += w + 4;
        }
        for (int k = 0; k < v.cards().size() && k < 8; k++) {
            Card card = v.cards().get(k);
            int x0 = 1 + 98 * (k % 4), y0 = 64 + 99 * (k / 4);
            c.box(x0, y0, 94, 97, box(card.box()));
            c.sprite(x0 + 32, y0 + 8, sprite(card.sprite()));
            name(c, x0 + 47, y0, card.name(), card.color());
            price(c, x0 + 47, y0 + 60, card.price());
            button(c, a, x0 + 4, y0 + 77, 86, KitData.Box.JSS_KB, card.state(), card.price(), v.jewels(), card.id(),
                    tip(card.name(), (v.giftTo() != null ? "Geschenk für " + v.giftTo() : null), stateText(card.state())));
        }
        // Seiten und Hinweis
        center(c, W / 2, 264, "Seite " + (v.pageNo() + 1) + " von " + Math.max(1, v.pages()), SUB, false);
        if (v.pageNo() > 0) {
            c.text(10, 264, "« Zurück", accent, true);
            if (a != null) c.action(8, 261, 60, 13, a.turn(v.pageNo() - 1), Component.text("Vorige Seite", WHITE));
        }
        if (v.pageNo() + 1 < v.pages()) {
            c.textRight(382, 264, "Weiter »", accent, true);
            if (a != null) c.action(322, 261, 60, 13, a.turn(v.pageNo() + 1), Component.text("Nächste Seite", WHITE));
        }
        center(c, W / 2, 276, pets ? "Gekaufte Haustiere findest du unter /pet."
                : "Aufstellen auf deinem Grundstück, Shift+Linksklick hebt es wieder auf.", SUB, false);
        return c;
    }

    /**
     * Name in der Karte: fett, wenn er fett höchstens 78 px breit ist, sonst normal bis 82 px; länger wird
     * umgebrochen (an einem Leerzeichen, sonst mit Bindestrich mitten im Wort) und steht dann 5 px höher.
     */
    private static void name(Canvas c, int cx, int y0, String name, TextColor color) {
        name = I18n.ctx(name);  // umbrechen in der Sprache, in der gezeigt wird
        if (Canvas.width(name, true) <= 78) {
            center(c, cx, y0 + 46, name, color, true);
            return;
        }
        if (Canvas.width(name, false) <= 82) {
            center(c, cx, y0 + 46, name, color, false);
            return;
        }
        String first, second;
        int sp = name.lastIndexOf(' ');
        if (sp > 0 && Canvas.width(name.substring(0, sp), false) <= 82) {
            first = name.substring(0, sp);
            second = name.substring(sp + 1);
        } else {
            int n = name.length();
            while (n > 1 && Canvas.width(name.substring(0, n) + "-", false) > 82) n--;
            first = name.substring(0, n) + "-";
            second = name.substring(n);
        }
        center(c, cx, y0 + 41, first, color, false);
        center(c, cx, y0 + 50, second, color, false);
    }

    // =================================================================================================================
    // Tokens
    // =================================================================================================================

    private static Canvas tokens(View v, Actions a) {
        Canvas c = new Canvas(W).heading("Juwelenshop", TextColor.color(0x54FC54));
        c.box(0, 0, W, 38, KitData.Box.JSS_HEAD_SHORT);
        c.sprite(8, 9, KitData.Sprite.JSS_BACK);
        if (a != null) c.action(8, 9, 22, 22, a.page(Page.HOME), Component.text("Zurück", WHITE));
        c.text(40, 10, "TOKENS", TOKEN, true);
        c.text(40, 21, v.giftTo() != null ? "Geschenk für " + v.giftTo() : "Für Juwelen · sofort da", SUB, false);
        int left = balance(c, 0, v.jewels());
        // davor: Münze, „Deine / Tokens“, Anzahl
        String n = Msg.coins(v.tokens());
        int nx = left - 12 - Canvas.width(n, true);
        int lx = nx - 41;
        c.sprite(lx - 24, 10, KitData.Sprite.JSS_TOK_COIN);
        c.text(lx, 9, "Deine", SUB, false);
        c.text(lx, 19, "Tokens", SUB, false);
        c.text(nx, 14, n, TOKEN, true);
        int x = 0;
        for (TokenPack t : v.tokenPacks()) {
            c.box(x, 44, 126, 157, box(t.box()));
            KitData.Sprite art = sprite(t.sprite());
            // Bilder stehen unten bündig (Unterkante y 90)
            c.sprite(x + 63 - (art.width + 1) / 2, 91 - art.height, art);
            center(c, x + 63, 95, t.tokens() + " Tokens", t.color(), true);
            if (t.best()) {
                center(c, x + 63, 105, "★ BESTER WERT", GOLD, true);
            } else {
                center(c, x + 63, 105, "Token-Paket", SUB, false);
            }
            center(c, x + 63, 117, t.perToken() + " Juwelen je Token", DESC, false);
            center(c, x + 63, 126, "Sofort auf dein Konto", SUB, false);
            if (t.instead() > t.price()) {
                int pct = (int) Math.round(100.0 * (t.instead() - t.price()) / t.instead());
                String old = Msg.coins(t.instead());
                List<Span> line = List.of(Span.of("Statt ", SUB), Span.of(old + " ", STRUCK), Span.bold("-" + pct + " %", FARM));
                int sx = x + 63 - (Canvas.width(line) + 1) / 2 + Canvas.width("Statt ", false);
                c.text(sx - Canvas.width("Statt ", false), 142, line);
                strike(c, sx, 142, Canvas.width(old, false), STRUCK);
            }
            String p = Msg.coins(t.price()) + " ";
            int w = 18 + Canvas.width(p, true) + Canvas.width("Juwelen", false);
            int px = x + 63 - (w + 1) / 2;
            c.sprite(px, 156, KitData.Sprite.JSS_EMERALD_PRICE);
            c.text(px + 18, 160, List.of(Span.bold(p, GREEN), Span.of("Juwelen", SUB)));
            button(c, a, x + 6, 180, 114, KitData.Box.JSS_KB, t.state(), t.price(), v.jewels(), t.id(),
                    tip(t.tokens() + " Tokens", t.perToken() + " Juwelen je Token, sofort auf dein Konto.",
                            stateText(t.state())));
            x += 132;
        }
        center(c, W / 2, 209, "Tokens gibst du im Token-Shop aus (/tokenshop): Rucksack, Booster,", SUB, false);
        center(c, W / 2, 219, "Claim-Blöcke und mehr.", SUB, false);
        return c;
    }

    /** Alter Preis: Linie auf der untersten Zeile der Ziffern von x - 1 bis x + w (Vorschub w), Schatten darunter. */
    private static void strike(Canvas c, int x, int y, int w, TextColor color) {
        c.line(x, y + 7, w + 2, TextColor.color(color.red() / 4, color.green() / 4, color.blue() / 4));
        c.line(x - 1, y + 6, w + 2, color);
    }

    // =================================================================================================================
    // Vorteilspakete
    // =================================================================================================================

    private static Canvas packs(View v, Actions a) {
        Canvas c = new Canvas(W).heading("Juwelenshop", TextColor.color(0x54FC54));
        subHead(c, v, "Vorteilspakete", RED, v.sub(), a);
        int x = 0;
        for (Pack k : v.packs()) {
            c.box(x, 44, 126, 157, box(k.box()));
            KitData.Sprite art = sprite(k.sprite());
            c.sprite(x + 63 - (art.width + 1) / 2, 50 + Math.max(0, 44 - art.height) / 2, art);
            center(c, x + 63, 98, I18n.ctx(k.name()).toUpperCase(Locale.GERMAN), k.color(), true);
            center(c, x + 63, 108, "Vorteils-Paket", SUB, false);
            int ly = 120;
            for (Span s : k.lines()) {
                if (ly > 145) break;
                center(c, x + 63, ly, List.of(s));
                ly += 9;
            }
            String p = Msg.coins(k.price()) + " ";
            int w = 18 + Canvas.width(p, true) + Canvas.width("Juwelen", false);
            int px = x + 63 - (w + 1) / 2;
            c.sprite(px, 155, KitData.Sprite.JSS_EMERALD_PRICE);
            c.text(px + 18, 159, List.of(Span.bold(p, GREEN), Span.of("Juwelen", SUB)));
            button(c, a, x + 6, 180, 114, KitData.Box.JSS_KB, k.state(), k.price(), v.jewels(), k.id(),
                    tip(k.name() + "-Paket", null, stateText(k.state())));
            x += 132;
        }
        center(c, W / 2, 207, "Die Schlüssel landen sofort bei dir. Öffnen am Spawn.", SUB, false);
        return c;
    }
}
