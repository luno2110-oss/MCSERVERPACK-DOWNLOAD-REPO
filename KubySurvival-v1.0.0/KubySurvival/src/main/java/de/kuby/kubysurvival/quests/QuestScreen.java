package de.kuby.kubysurvival.quests;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Quests (/quest) nach der Referenz „Neue-referenzen“: Übersicht mit Kopf (Buch, Schriftzug, Quest-Rang mit Balken,
 * Streak und Tokens), der verfolgten Quest und Kacheln je Bereich (Event, Täglich, Wöchentlich, Hauptquests,
 * Meilensteine, Team, Nebenquests) sowie Knöpfen Rangliste, Abholbox, Streak und Hilfe.
 */
public final class QuestScreen {

    public static final int W = 390, HEIGHT = 390;

    static final TextColor GOLD = TextColor.color(0xFCCF79), GRAY = TextColor.color(0x797D84),
            LIGHT = TextColor.color(0x9FA1A7), BODY = TextColor.color(0xD1D2D5), WHITE = TextColor.color(0xFCFCFC),
            NAME = TextColor.color(0xFCEECD), COINS = TextColor.color(0xFCD146), CLAIMS = TextColor.color(0x7DB6FC),
            TOKENS = TextColor.color(0xFCA55B), DARK = TextColor.color(0x201400);

    /** Bereiche: Kachel, Farben von Titel und Untertitel, Symbol. */
    public enum Area {
        EVENT("Event", 0xFC981F, 0xEEC14A, true, KitData.Box.QU_TILE_EVENT, KitData.Sprite.QU_T_EVENT,
                KitData.Box.QU_PILL_EVENT, KitData.Box.QU_TAB_EVENT, KitData.Sprite.QU_PILL_I_EVENT, KitData.Sprite.QU_TAB_I_EVENT),
        DAILY("Täglich", 0xFCAF49, 0x9FA1A7, true, KitData.Box.QU_TILE_DAILY, KitData.Sprite.QU_T_DAILY,
                KitData.Box.QU_PILL_DAILY, KitData.Box.QU_TAB_DAILY, KitData.Sprite.QU_PILL_I_DAILY, KitData.Sprite.QU_TAB_I_DAILY),
        WEEKLY("Wöchentlich", 0xF26A6A, 0x9FA1A7, true, KitData.Box.QU_TILE_WEEKLY, KitData.Sprite.QU_T_WEEKLY,
                KitData.Box.QU_PILL_WEEKLY, KitData.Box.QU_TAB_WEEKLY, KitData.Sprite.QU_PILL_I_WEEKLY, KitData.Sprite.QU_TAB_I_WEEKLY),
        MAIN("Hauptquests", 0x6BD169, 0x6BD169, false, KitData.Box.QU_TILE_MAIN, KitData.Sprite.QU_T_MAIN,
                KitData.Box.QU_PILL_MAIN, KitData.Box.QU_TAB_MAIN, KitData.Sprite.QU_PILL_I_MAIN, KitData.Sprite.QU_TAB_I_MAIN),
        MILE("Meilensteine", 0xFCD559, 0x9FA1A7, false, KitData.Box.QU_TILE_MILE, KitData.Sprite.QU_T_MILE,
                KitData.Box.QU_PILL_MILE, KitData.Box.QU_TAB_MILE, KitData.Sprite.QU_PILL_I_MILE, KitData.Sprite.QU_TAB_I_MILE),
        TEAM("Team", 0x8DB2E5, 0x6A6D74, true, KitData.Box.QU_TILE_TEAM, KitData.Sprite.QU_T_TEAM,
                KitData.Box.QU_PILL_TEAM, KitData.Box.QU_TAB_TEAM, KitData.Sprite.QU_PILL_I_TEAM, KitData.Sprite.QU_TAB_I_TEAM),
        SIDE("Nebenquests", 0xD69F59, 0x88ED9E, false, KitData.Box.QU_TILE_SIDE, KitData.Sprite.QU_T_SIDE,
                null, null, null, null);

        public final String label;
        public final TextColor title, sub;
        /** Titel der Kachel fett (wie im Original nur bei den kurzen Namen). */
        final boolean bold;
        final KitData.Box tile, pill, tab;
        final KitData.Sprite icon, pillIcon, tabIcon;

        Area(String label, int title, int sub, boolean bold, KitData.Box tile, KitData.Sprite icon, KitData.Box pill,
             KitData.Box tab, KitData.Sprite pillIcon, KitData.Sprite tabIcon) {
            this.label = label;
            this.bold = bold;
            this.title = TextColor.color(title);
            this.sub = TextColor.color(sub);
            this.tile = tile;
            this.icon = icon;
            this.pill = pill;
            this.tab = tab;
            this.pillIcon = pillIcon;
            this.tabIcon = tabIcon;
        }
    }

    /** Kopf: Quest-Rang, nächster Rang (null = höchster), Fortschritt dahin, Streak-Tage, Tokens. */
    public record Head(String rank, String next, double progress, int streak, long tokens) {}

    /**
     * Verfolgte Quest: Bereich (Name über der Quest), Name, Aufgabe, Belohnungen, Fortschritt, Text rechts unten
     * („1/2 Ziele“), Knopf (null = keiner) und Hinweis darunter.
     */
    public record Tracked(String area, String name, String task, long coins, long claims, long tokens, double progress,
                          String goal, String button, String hint) {}

    /** Kachel: Bereich, Titel (Event-Name oder Bereich), Untertitel (fertig gesetzt), erledigt, gesamt (total < 0: Prozent). */
    public record Tile(Area area, String title, String sub, long done, long total) {}

    public record Overview(Head head, Tracked tracked, List<Tile> tiles) {}

    public interface Actions {
        Consumer<Player> area(Area a);

        Consumer<Player> overview();

        Consumer<Player> page(int page);

        Consumer<Player> track();

        Consumer<Player> ranking();

        Consumer<Player> box();

        Consumer<Player> streak();

        Consumer<Player> help();
    }

    private QuestScreen() {
    }

    static String n(long v) {
        return String.format(Locale.GERMANY, "%,d", v);
    }

    /** Balken: leer mit Rand (w breit, 6 hoch), darin die goldene Füllung. */
    static void bar(Canvas c, int x, int y, int w, double progress) {
        for (int done = 0; done < w - 1; done += 256) c.strip(x + done, y, KitData.Strip.QU_BAR, Math.min(256, w - 1 - done), Canvas.Z_FILL);
        int fill = (int) Math.round(Math.max(0, Math.min(1, progress)) * (w - 2));
        for (int done = 0; done < fill; done += 256) c.strip(x + 1 + done, y, KitData.Strip.QU_FILL_GOLD, Math.min(256, fill - done), Canvas.Z_ICON);
    }

    /** Symbol mittig in einem Feld (sw x sh) ab x, y. */
    static void icon(Canvas c, int x, int y, int sw, int sh, KitData.Sprite s) {
        c.sprite(x + (sw - s.width) / 2, y + (sh - s.height) / 2, s);
    }

    // =================================================================================================================
    // Übersicht
    // =================================================================================================================

    public static Canvas overview(Overview v, Actions a) {
        Canvas c = new Canvas(W).heading("Quests", GOLD);
        // Kopf
        Head h = v.head();
        c.box(0, 0, W, 48, KitData.Box.QU_HEAD);
        c.sprite(8, 10, KitData.Sprite.QU_LOGO);
        c.sprite(46, 13, KitData.Sprite.QU_TITLE);
        c.text(150, 8, Canvas.caps(Canvas.fit(h.rank(), true, 150)), GOLD, true);
        bar(c, 150, 21, 64, h.progress());
        c.text(150, 31, h.next() == null ? Canvas.caps("Höchster Rang") : "→ " + Canvas.caps(Canvas.fit(h.next(), false, 140)), GRAY, false);
        // Symbole an der Stelle wie im Original; bei langen Zahlen rücken sie nach links
        int tw = c.textRight(380, 12, Canvas.caps("Tage"), GRAY, false);
        int nw = c.textRight(380 - tw - 5, 12, String.valueOf(h.streak()), h.streak() > 0 ? WHITE : GRAY, true);
        c.sprite(Math.min(326, 380 - tw - 5 - nw - 3 - KitData.Sprite.QU_FIRE.width), 8, KitData.Sprite.QU_FIRE);
        tw = c.textRight(380, 28, Canvas.caps("Tokens"), LIGHT, false);
        nw = c.textRight(380 - tw - 5, 28, n(h.tokens()), WHITE, true);
        c.sprite(Math.min(315, 380 - tw - 5 - nw - 3 - KitData.Sprite.QU_MEDAL.width), 25, KitData.Sprite.QU_MEDAL);

        // verfolgte Quest
        c.box(0, 54, W, 105, KitData.Box.QU_CARD_GOLD);
        Tracked t = v.tracked();
        if (t == null) {
            c.text(9, 63, Canvas.caps("Verfolgt"), GOLD, false);
            c.text(30, 80, "Du verfolgst gerade keine Quest.", BODY, false);
            c.text(30, 92, "Wähle unten einen Bereich und klicke „Verfolgen“.", LIGHT, false);
        } else {
            int vx = 9 + c.text(9, 63, Canvas.caps("Verfolgt"), GOLD, false);
            c.text(vx + 8, 63, Canvas.caps(t.area()), GRAY, false);
            c.sprite(9, 72, KitData.Sprite.QU_COMPASS);
            int rw = rewardsWidth(t.coins(), t.claims(), t.tokens());
            String nm = Canvas.caps(t.name());
            int rx = Math.max(153, 30 + Canvas.width(nm, true) + 10);
            if (rx + rw > 381) {
                rx = Math.max(153, 381 - rw);
                nm = Canvas.caps(Canvas.fit(t.name(), true, rx - 10 - 30));
            }
            c.text(30, 76, nm, NAME, true);
            rewards(c, rx, 72, t.coins(), t.claims(), t.tokens());
            int ty = 92;
            for (String line : Canvas.wrap(t.task(), 240)) {
                if (ty > 122) break;
                c.text(30, ty, line, BODY, false);
                ty += 10;
            }
            int gw = t.goal() == null ? 0 : Canvas.width(t.goal(), false);
            bar(c, 30, 136, Math.min(150, 273 - gw - 8 - 30), t.progress());
            if (t.goal() != null) c.textRight(273, 135, t.goal(), WHITE, false);
            if (t.button() != null) {
                c.box(284, 91, 96, 18, KitData.Box.QU_BTN_GOLD, Canvas.Z_BUTTON);
                String bt = Canvas.fit(t.button(), true, 90);
                c.textFlat(332 - Canvas.width(bt, true) / 2, 97, bt, DARK, true);
                c.action(284, 91, 96, 18, a.track(), t.button());
            }
            if (t.hint() != null) c.textCenter(332, 113, Canvas.caps(Canvas.fit(t.hint(), false, 96)), GOLD, false);
        }

        // Kacheln 3 x 3 (die letzte Zeile: Nebenquests und vier kleine Knöpfe)
        int i = 0;
        for (Tile tile : v.tiles()) {
            int x = (i % 3) * 132, y = 171 + (i / 3) * 72;
            tile(c, x, y, tile, a);
            i++;
        }
        small(c, 132, 324, KitData.Box.QU_SMALL_RANK, KitData.Sprite.QU_S_RANK, "Rangliste", a.ranking());
        small(c, 264, 324, KitData.Box.QU_SMALL_BOX, KitData.Sprite.QU_S_BOX, "Abholbox", a.box());
        small(c, 132, 351, KitData.Box.QU_SMALL_STREAK, KitData.Sprite.QU_S_STREAK, "Streak", a.streak());
        small(c, 264, 351, KitData.Box.QU_SMALL_HELP, KitData.Sprite.QU_S_HELP, "Hilfe", a.help());
        return c;
    }

    /** Belohnungen ab x: Münze + Coins, Schaufel + Claim-Blöcke, Medaille + Tokens (nur was es gibt). */
    static int rewards(Canvas c, int x, int y, long coins, long claims, long tokens) {
        if (coins > 0) {
            c.sprite(x, y, KitData.Sprite.QU_COIN);
            x += 19 + c.text(x + 19, y + 4, n(coins), COINS, true) + 11;
        }
        if (claims > 0) {
            c.sprite(x, y, KitData.Sprite.QU_SHOVEL);
            x += 18 + c.text(x + 18, y + 4, n(claims), CLAIMS, true) + 12;
        }
        if (tokens > 0) {
            c.sprite(x, y, KitData.Sprite.QU_MEDAL_S);
            x += 17 + c.text(x + 17, y + 4, n(tokens), TOKENS, true) + 11;
        }
        return x;
    }

    static int rewardsWidth(long coins, long claims, long tokens) {
        int w = 0;
        if (coins > 0) w += 19 + Canvas.width(n(coins), true) + 11;
        if (claims > 0) w += 18 + Canvas.width(n(claims), true) + 12;
        if (tokens > 0) w += 17 + Canvas.width(n(tokens), true) + 11;
        return Math.max(0, w - 11);
    }

    private static void tile(Canvas c, int x, int y, Tile t, Actions a) {
        Area ar = t.area();
        c.box(x, y, 126, 67, ar.tile, Canvas.Z_BUTTON);
        icon(c, x + 8, y + 7, 30, 32, ar.icon);
        c.text(x + 44, y + 9, Canvas.caps(Canvas.fit(t.title(), ar.bold, 78)), ar.title, ar.bold);
        // Untertitel kommt fertig (Kapitälchen außer Zahlen und Einheiten wie „24min“)
        c.text(x + 44, y + 20, Canvas.fit(t.sub(), false, 78), ar.sub, false);
        bar(c, x + 7, y + 52, 64, t.total() > 0 ? (double) t.done() / t.total() : t.total() < 0 ? t.done() / 100.0 : 0);
        String count = t.total() < 0 ? t.done() + " %" : t.done() + "/" + t.total();
        c.textRight(x + 119, y + 50, count, WHITE, false);
        c.action(x, y, 126, 67, a.area(ar), ar.label);
    }

    private static void small(Canvas c, int x, int y, KitData.Box box, KitData.Sprite s, String label, Consumer<Player> click) {
        c.box(x, y, 126, 22, box, Canvas.Z_BUTTON);
        int gw = 16 + 3 + Canvas.width(label, false);
        int gx = x + (126 - gw) / 2;
        icon(c, gx, y + 3, 16, 16, s);
        c.text(gx + 19, y + 7, label, WHITE, false);
        c.action(x, y, 126, 22, click, label);
    }

    // =================================================================================================================
    // Listen (Event, Täglich, Wöchentlich, Hauptquests, Meilensteine, Team)
    // =================================================================================================================

    static final TextColor LOCKED_NAME = TextColor.color(0xB6BAC2), CANDY = TextColor.color(0xFC8888),
            TITLE = TextColor.color(0xF0DFB6);

    /** Kartenrahmen. */
    public enum Style {
        GOLD(KitData.Box.QU_CARD_GOLD), GRAY(KitData.Box.QU_CARD_GRAY), BROWN(KitData.Box.QU_CARD_BROWN),
        BLUE(KitData.Box.QU_CARD_BLUE), RED(KitData.Box.QU_CARD_RED);

        final KitData.Box box;

        Style(KitData.Box box) {
            this.box = box;
        }
    }

    /** Symbol links neben dem Namen. */
    public enum Icon {
        QUEST(KitData.Sprite.QU_Q_MARK), LOCK(KitData.Sprite.QU_LOCK), TASK(KitData.Sprite.QU_HOURGLASS),
        TRACK(KitData.Sprite.QU_COMPASS);

        final KitData.Sprite sprite;

        Icon(KitData.Sprite sprite) {
            this.sprite = sprite;
        }
    }

    /** Belohnungsarten: Symbol (in einem 16-px-Feld) und Farbe der Zahl. */
    public enum Kind {
        COINS(KitData.Sprite.QU_COIN, QuestScreen.COINS), CANDY(KitData.Sprite.QU_CANDY, QuestScreen.CANDY),
        CLAIMS(KitData.Sprite.QU_SHOVEL, QuestScreen.CLAIMS), TOKENS(KitData.Sprite.QU_MEDAL_S, QuestScreen.TOKENS),
        TITLE(KitData.Sprite.QU_FEATHER, QuestScreen.TITLE), ITEMS(KitData.Sprite.QU_GIFT, QuestScreen.CANDY);

        final KitData.Sprite icon;
        final TextColor color;

        Kind(KitData.Sprite icon, TextColor color) {
            this.icon = icon;
            this.color = color;
        }
    }

    public record Reward(Kind kind, String text) {
        public static Reward of(Kind k, long v) {
            return new Reward(k, n(v));
        }
    }

    /** Knopf rechts: keiner, gold gefüllt (Annehmen, Weg zeigen), gold umrandet (Verfolgen), gesperrt (Schloss). */
    public enum Btn { NONE, GOLD, LINE, LOCKED }

    /**
     * Eine Quest-Karte. label (null = keine Zeile darüber) mit Farbe und Zusatz in Grau; Fortschritt null = kein Balken;
     * count rechts neben dem Balken; sub unter dem Knopf; hint unter den Belohnungen (gesperrte Karten).
     */
    public record Card(Style style, String label, TextColor labelColor, String labelExtra, Icon icon, String name,
                       String desc, List<Reward> rewards, Double progress, boolean cream, String count, Btn button,
                       String buttonText, String sub, String hint, Consumer<Player> click) {}

    /** Reiter: Bereich, Name (beim Event der Event-Name), Zähler. */
    public record Tab(Area area, String title, String count) {}

    /** Set-Bonus unter Täglich und Wöchentlich. */
    public record Bonus(int done, int total, String time, List<Reward> rewards) {}

    /**
     * Liste: Bereich, Titel und Untertitel im Kopf, Zähler rechts, Reiter, Karten dieser Seite, Hinweis-Karte (info,
     * z. B. „Kein Team“), Seite, Seiten, Set-Bonus (null = keiner).
     */
    public record ListView(Area area, String title, String subtitle, String count, List<Tab> tabs, List<Card> cards,
                           String infoLabel, String info, int page, int pages, Bonus bonus, Shop shop) {
        public ListView(Area area, String title, String subtitle, String count, List<Tab> tabs, List<Card> cards,
                        String infoLabel, String info, int page, int pages, Bonus bonus) {
            this(area, title, subtitle, count, tabs, cards, infoLabel, info, page, pages, bonus, null);
        }
    }

    /** Leiste unter der Event-Liste: Währung, Stand und Klick zum Shop. */
    public record Shop(String currency, long balance, Consumer<Player> open) {}

    public static Canvas list(ListView v, Actions a) {
        Canvas c = new Canvas(W).heading("Quests", GOLD);
        // Kopf
        c.box(0, 0, W, 48, KitData.Box.QU_HEAD);
        c.sprite(8, 13, KitData.Sprite.QU_BACK);
        c.action(8, 13, 22, 22, a.overview(), "Zurück");
        icon(c, 39, 8, 30, 32, v.area().icon);
        c.text(78, 13, Canvas.caps(Canvas.fit(v.title(), true, 220)), v.area().title, true);
        c.text(78, 27, Canvas.fit(v.subtitle(), false, 240), LIGHT, false);
        c.textRight(380, 13, v.count(), WHITE, true);
        c.textRight(380, 27, Canvas.caps("Erledigt"), LIGHT, false);

        // Reiter: der gewählte breit (200), die anderen 34 breit, 4 px Abstand
        int x = 0;
        for (Tab t : v.tabs()) {
            Area ar = t.area();
            if (ar == v.area()) {
                c.box(x, 54, 200, 22, ar.pill, Canvas.Z_BUTTON);
                icon(c, x + 7, 57, 16, 16, ar.pillIcon);
                c.textFlat(x + 29, 61, Canvas.caps(Canvas.fit(t.title(), true, 120)), DARK, true);
                int cw = Canvas.width(t.count(), false);
                c.textFlat(x + 192 - cw, 61, t.count(), DARK, false);
                x += 204;
            } else {
                c.box(x, 54, 34, 22, ar.tab, Canvas.Z_BUTTON);
                icon(c, x, 57, 34, 16, ar.tabIcon);
                c.action(x, 54, 34, 22, a.area(ar), t.title() + " · " + t.count());
                x += 38;
            }
        }

        int y = 81;
        if (v.info() != null) {
            c.box(0, y, W, 42, KitData.Box.QU_CARD_BROWN);
            c.text(9, y + 9, Canvas.caps(v.infoLabel()), GOLD, false);
            c.text(9, y + 22, Canvas.fit(v.info(), false, 370), BODY, false);
            y += 42 + 12;
        }
        for (Card card : v.cards()) y += card(c, y, card) + 12;

        // Blättern: Pfeile fest bei 135 und 231, „SEITE n/m“ mittig dazwischen (wie im Original)
        if (v.pages() > 1) {
            String label = Canvas.caps("Seite") + " " + (v.page() + 1) + "/" + v.pages();
            int tw = Canvas.width(label, false);
            if (v.page() > 0) {
                c.sprite(135, y, KitData.Sprite.QU_PAGE_PREV);
                c.action(135, y, 24, 14, a.page(v.page() - 1), "Vorige Seite");
            }
            c.text(195 - tw / 2, y + 3, label, LIGHT, false);
            if (v.page() + 1 < v.pages()) {
                c.sprite(231, y, KitData.Sprite.QU_PAGE_NEXT);
                c.action(231, y, 24, 14, a.page(v.page() + 1), "Nächste Seite");
            }
            y += 14 + 13;
        }

        // Set-Bonus
        Bonus b = v.bonus();
        if (b != null) {
            c.box(0, y, W, 51, KitData.Box.QU_CARD_GOLD);
            c.text(9, y + 11, Canvas.caps("Set-Bonus"), GOLD, false);
            int tw = c.textRight(381, y + 11, b.time(), LIGHT, false);
            c.sprite(381 - tw - 18, y + 7, KitData.Sprite.QU_SET_CLOCK);
            // so viele Fragezeichen, wie neben Zähler und Belohnungen Platz haben
            int textW = Canvas.width(b.done() + "/" + b.total(), false) + 4 + Canvas.width(Canvas.caps("Erledigt"), false);
            int room = 381 - rewardsWidth(b.rewards()) - 10 - textW - 2 - 11;
            int pips = Math.max(0, Math.min(b.total(), room / 18));
            for (int i = 0; i < pips; i++) c.sprite(11 + 18 * i, y + 26, KitData.Sprite.QU_QBIG);
            int bx = 11 + 18 * pips + 2;
            int dw = c.text(bx, y + 30, b.done() + "/" + b.total(), WHITE, false);
            c.text(bx + dw + 4, y + 30, Canvas.caps("Erledigt"), WHITE, false);
            rewardsRight(c, 381, y + 30, b.rewards());
            y += 51 + 12;
        }
        // Event: Leiste zum Candy-Shop
        Shop s = v.shop();
        if (s != null) {
            c.box(0, y, W, 33, KitData.Box.QU_CARD_GOLD);
            c.sprite(10, y + 10, KitData.Sprite.QU_CANDY);
            int nw = c.text(28, y + 8, n(s.balance()), CANDY, true);
            c.text(28 + nw + 5, y + 8, Canvas.fit(s.currency(), false, 140), LIGHT, false);
            c.text(28, y + 19, "Für Titel, Hüte, Schlüssel und mehr", GRAY, false);
            c.box(284, y + 7, 96, 18, KitData.Box.QU_BTN_GOLD, Canvas.Z_BUTTON);
            c.textFlat(332 - Canvas.width("Candy-Shop", true) / 2, y + 13, "Candy-Shop", DARK, true);
            c.action(0, y, W, 33, s.open(), "Candy-Shop öffnen");
        }
        return c;
    }

    // =================================================================================================================
    // Candy-Shop (Event)
    // =================================================================================================================

    /** Angebot: Icon, Name, Preis, gekauft (einmalige Angebote), bezahlbar, Klick. */
    public record ShopOffer(KitData.Icon icon, String name, long price, boolean owned, boolean affordable, Consumer<Player> buy) {}

    public record ShopView(String event, String currency, long balance, List<ShopOffer> offers) {}

    public static Canvas eventShop(ShopView v, Consumer<Player> back) {
        Canvas c = new Canvas(W).heading("Quests", GOLD);
        c.box(0, 0, W, 48, KitData.Box.QU_HEAD);
        c.sprite(8, 13, KitData.Sprite.QU_BACK);
        c.action(8, 13, 22, 22, back, "Zurück");
        icon(c, 39, 8, 30, 32, KitData.Sprite.QU_T_EVENT);
        c.text(78, 13, Canvas.caps("Candy-Shop"), Area.EVENT.title, true);
        c.text(78, 27, Canvas.fit(v.event() + " · nur während des Events", false, 200), LIGHT, false);
        int nw = c.textRight(380, 13, n(v.balance()), CANDY, true);
        c.sprite(380 - nw - 16, 11, KitData.Sprite.QU_CANDY);
        c.textRight(380, 27, Canvas.caps(Canvas.fit(v.currency(), false, 100)), LIGHT, false);
        int y = 54, cw = (W - 4) / 2, ch = 46;
        for (int i = 0; i < v.offers().size(); i++) {
            ShopOffer o = v.offers().get(i);
            int x = (i % 2) * (cw + 4), oy = y + (i / 2) * (ch + 4);
            c.box(x, oy, cw, ch, o.owned() ? KitData.Box.QU_CARD_BROWN : o.affordable() ? KitData.Box.QU_CARD_GOLD : KitData.Box.QU_CARD_GRAY);
            c.icon(x + 8, oy + 8, o.icon(), false);
            c.text(x + 30, oy + 10, Canvas.fit(o.name(), true, cw - 38), o.owned() ? LIGHT : NAME, true);
            c.sprite(x + 10, oy + 28, KitData.Sprite.QU_CANDY);
            c.text(x + 28, oy + 30, n(o.price()), o.affordable() || o.owned() ? CANDY : GRAY, true);
            if (o.owned()) {
                c.textRight(x + cw - 10, oy + 30, "✔ " + Canvas.caps("Gekauft"), LIGHT, false);
            } else {
                int bw = 64;
                c.box(x + cw - 8 - bw, oy + 23, bw, 18, o.affordable() ? KitData.Box.QU_BTN_GOLD : KitData.Box.QU_BTN_LINE, Canvas.Z_BUTTON);
                String l = "Kaufen";
                if (o.affordable()) c.textFlat(x + cw - 8 - bw / 2 - Canvas.width(l, true) / 2, oy + 29, l, DARK, true);
                else c.text(x + cw - 8 - bw / 2 - Canvas.width(l, true) / 2, oy + 29, l, GRAY, true);
                if (o.buy() != null) c.action(x, oy, cw, ch, o.buy(), o.name() + " für " + n(o.price()) + " " + v.currency());
            }
        }
        if (v.offers().isEmpty()) c.text(4, y, "Im Candy-Shop gibt es gerade nichts.", LIGHT, false);
        return c;
    }

    /** Breite einer Belohnungsreihe (16-px-Feld, 3 px, Zahl fett; 10 px zwischen den Belohnungen). */
    static int rewardsWidth(List<Reward> rs) {
        int w = 0;
        for (Reward r : rs) w += (w > 0 ? 10 : 0) + 19 + Canvas.width(r.text(), true);
        return w;
    }

    /** Belohnungen ab x (Text-Oberkante y, Symbole 4 px höher, mittig im 16-px-Feld). Gibt das Ende zurück. */
    static int rewards(Canvas c, int x, int y, List<Reward> rs) {
        boolean first = true;
        for (Reward r : rs) {
            if (!first) x += 10;
            first = false;
            KitData.Sprite s = r.kind().icon;
            c.sprite(x + (16 - s.width) / 2, y - 4 + (16 - s.height) / 2, s);
            x += 19 + c.text(x + 19, y, r.text(), r.kind().color, true);
        }
        return x;
    }

    static void rewardsRight(Canvas c, int right, int y, List<Reward> rs) {
        rewards(c, right - rewardsWidth(rs), y, rs);
    }

    /** Eine Karte ab y. Gibt die Höhe zurück. */
    static int card(Canvas c, int y, Card k) {
        boolean locked = k.button() == Btn.LOCKED;
        int nameY = y + (k.label() != null ? 22 : 11);
        // Belohnungen auf der Namenszeile, wenn sie mit 16 px Abstand passen
        int nameW = Canvas.width(Canvas.caps(k.name()), true);
        int rw = rewardsWidth(k.rewards());
        boolean inline = !k.rewards().isEmpty() && 30 + nameW + 16 + rw <= 272;
        List<String> lines = k.desc() == null || k.desc().isBlank() ? List.of() : wrap(k.desc());
        int ty = nameY + (inline ? 16 : 11);
        int last = lines.isEmpty() ? nameY : ty + 10 * (lines.size() - 1);
        int rewardY = -1;
        if (!inline && !k.rewards().isEmpty()) rewardY = last + 15;
        int bottom = rewardY > 0 ? rewardY + 6 : last + 6;
        int barY = -1, hintY = -1;
        if (k.progress() != null) {
            barY = rewardY > 0 ? rewardY + 16 : last + 14;
            bottom = barY + 5;
        }
        if (k.hint() != null) {
            hintY = rewardY > 0 ? rewardY + 15 : last + 13;
            bottom = hintY + 7;
        }
        // Höhe: kleinstes 9k + 6 mit mindestens 12 px unter dem Inhalt
        int h = 6;
        while (h < bottom - y + 12) h += 9;
        c.box(0, y, W, h, k.style().box);
        if (k.label() != null) {
            int lw = c.text(9, y + 9, Canvas.caps(k.label()), k.labelColor(), false);
            if (k.labelExtra() != null) c.text(9 + lw + 8, y + 9, k.labelExtra(), GRAY, false);
        }
        c.sprite(9 + (13 - k.icon().sprite.width) / 2, nameY - 4, k.icon().sprite);
        c.text(30, nameY, Canvas.caps(Canvas.fit(k.name(), true, inline ? 272 - 30 - 16 - rw : 240)),
                locked ? LOCKED_NAME : NAME, true);
        if (inline) rewardsRight(c, 272, nameY, k.rewards());
        for (String line : lines) {
            c.text(30, ty, line, locked ? LIGHT : BODY, false);
            ty += 10;
        }
        if (rewardY > 0) rewards(c, 30, rewardY, k.rewards());
        if (barY > 0) {
            int cw = k.count() == null ? 0 : Canvas.width(k.count(), false);
            int bw = Math.max(40, Math.min(150, 272 - cw - 8 - 30));
            if (k.cream()) barCream(c, 30, barY, bw, k.progress());
            else bar(c, 30, barY, bw, k.progress());
            // rechts bündig wie im Original: bei gefülltem Balken 1 px weiter rechts (dort zählt die Füllung mit)
            if (k.count() != null) c.textRight(k.progress() > 0 ? 273 : 272, barY - 1, k.count(), WHITE, false);
        }
        if (hintY > 0) c.text(30, hintY, Canvas.fit(k.hint(), false, 242), LIGHT, false);
        // rechts: Knopf mit Text darunter, mittig in der Karte
        int by = y + (h + 1) / 2 - 16;
        switch (k.button()) {
            case GOLD, LINE -> {
                c.box(284, by, 96, 18, k.button() == Btn.GOLD ? KitData.Box.QU_BTN_GOLD : KitData.Box.QU_BTN_LINE, Canvas.Z_BUTTON);
                String bt = Canvas.fit(k.buttonText(), true, 90);
                int bw = Canvas.width(bt, true);
                if (k.button() == Btn.GOLD) c.textFlat(332 - bw / 2, by + 6, bt, DARK, true);
                else c.text(331 - bw / 2, by + 6, bt, NAME, true);
                if (k.click() != null) c.action(284, by, 96, 18, k.click(), k.buttonText());
                // wie die Zahl am Balken: bei gefülltem Balken 1 px weiter rechts
                int sx = 332 + (k.progress() != null && k.progress() > 0 && k.button() == Btn.LINE ? 1 : 0);
                if (k.sub() != null) c.textCenter(sx, by + 22, Canvas.fit(k.sub(), false, 96), k.button() == Btn.GOLD ? GOLD : LIGHT, false);
            }
            case LOCKED -> {
                String g = Canvas.caps("Gesperrt");
                int gw = Canvas.width(g, true);
                int gx = 331 - (13 + 4 + gw) / 2;
                c.sprite(gx, y + (h - 16) / 2, KitData.Sprite.QU_LOCK_S);
                c.text(gx + 18, y + (h - 16) / 2 + 4, g, LIGHT, true);
            }
            case NONE -> {
            }
        }
        return h;
    }

    /** Zeilenumbruch der Quest-Texte wie im Original: höchstens 242 px. Ein „\\n“ im Text bricht immer um. */
    static List<String> wrap(String text) {
        List<String> out = new java.util.ArrayList<>();
        for (String para : de.kuby.kubyplugins.util.I18n.ctx(text).split("\\R|\\\\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String probe = line.isEmpty() ? word : line + " " + word;
                if (measure(probe) > 242 && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(probe);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private static int measure(String s) {
        return Canvas.width(s, false);
    }

    static void barCream(Canvas c, int x, int y, int w, double progress) {
        c.strip(x, y, KitData.Strip.QU_BAR, w - 1, Canvas.Z_FILL);
        int fill = (int) Math.round(Math.max(0, Math.min(1, progress)) * (w - 2));
        if (fill > 0) c.strip(x + 1, y, KitData.Strip.QU_FILL_CREAM, fill, Canvas.Z_ICON);
    }

    // =================================================================================================================
    // Nebenquests: Figuren (3 x 3) und Detailseite einer Figur
    // =================================================================================================================

    static final TextColor SIDE_JOB = TextColor.color(0xD69F59), AVAILABLE = TextColor.color(0x88ED9E);

    /** Eine Figur: Name, Gesicht (null = Schriftrolle), Beruf, aktuelle Aufgabe, geschafft/gesamt, Klick. */
    public record Npc(String name, KitData.Sprite face, String job, String quest, int done, int total, boolean open,
                      Consumer<Player> click) {}

    public record SideView(int done, int total, List<Npc> npcs, int page, int pages) {}

    /** Gegenstand oder Ziel einer Nebenquest: Symbol (null = keins), Name, geschafft, nötig. */
    public record Goal(KitData.Icon icon, String name, long have, long need) {}

    public record SideDetail(String name, KitData.Sprite face, String status, boolean available, String job, int done,
                             int total, int step, String difficulty, String title, String quote, List<Goal> goals,
                             long coins, long tokens, String button, Consumer<Player> click) {}

    private static KitData.Sprite face(KitData.Sprite f) {
        return f == null ? KitData.Sprite.QU_FACE_RALF : f;
    }

    private static Canvas sideHead(String heading) {
        Canvas c = new Canvas(W).heading(heading, GOLD);
        c.box(0, 0, W, 48, KitData.Box.QU_HEAD);
        c.sprite(8, 13, KitData.Sprite.QU_BACK);
        return c;
    }

    public static Canvas sides(SideView v, Actions a) {
        Canvas c = sideHead("Nebenquests");
        c.action(8, 13, 22, 22, a.overview(), "Zurück");
        icon(c, 39, 8, 30, 32, KitData.Sprite.QU_T_SIDE);
        c.text(78, 13, Canvas.caps("Nebenquests"), SIDE_JOB, true);
        c.text(78, 27, "Wähle einen NPC und nimm seine Quest an.", BODY, false);
        int ew = c.textRight(380, 20, Canvas.caps("Erledigt"), LIGHT, false);
        c.textRight(380 - ew - 5, 20, v.done() + "/" + v.total(), WHITE, true);
        int i = 0;
        for (Npc n : v.npcs()) {
            int x = (i % 3) * 132, y = 54 + (i / 3) * 81;
            c.box(x, y, 126, 72, KitData.Box.QU_NPC, Canvas.Z_BUTTON);
            KitData.Sprite f = face(n.face());
            c.sprite(x + 7 + (24 - f.width) / 2, y + 7 + (24 - f.height) / 2, f);
            c.text(x + 35, y + 9, Canvas.fit(Canvas.lit(n.name()), true, 66), NAME, true);
            c.text(x + 35, y + 21, n.done() + "/" + n.total(), WHITE, true);
            if (n.open()) c.sprite(x + 105, y + 7, KitData.Sprite.QU_QBIG);
            // Beruf und Aufgabe unten bündig (zweizeilige Aufgabe schiebt den Beruf nach oben)
            List<String> ql = Canvas.wrap(n.quest(), 116);
            if (ql.size() > 2) ql = ql.subList(0, 2);
            int ty = y + 54 - 10 * (ql.size() - 1);
            c.text(x + 7, ty - 11, Canvas.caps(Canvas.fit(n.job(), false, 112)), SIDE_JOB, false);
            for (String line : ql) {
                c.text(x + 7, ty, line, BODY, false);
                ty += 10;
            }
            if (n.click() != null) c.action(x, y, 126, 72, n.click(), n.name());
            i++;
        }
        if (v.pages() > 1) {
            int y = 54 + 3 * 81;
            String label = Canvas.caps("Seite") + " " + (v.page() + 1) + "/" + v.pages();
            int tw = Canvas.width(label, false);
            if (v.page() > 0) {
                c.sprite(135, y, KitData.Sprite.QU_PAGE_PREV);
                c.action(135, y, 24, 14, a.page(v.page() - 1), "Vorige Seite");
            }
            c.text(195 - tw / 2, y + 3, label, LIGHT, false);
            if (v.page() + 1 < v.pages()) {
                c.sprite(231, y, KitData.Sprite.QU_PAGE_NEXT);
                c.action(231, y, 24, 14, a.page(v.page() + 1), "Nächste Seite");
            }
        }
        return c;
    }

    public static Canvas side(SideDetail d, Consumer<Player> back) {
        Canvas c = sideHead("Nebenquests");
        c.action(8, 13, 22, 22, back, "Zurück");
        KitData.Sprite f = face(d.face());
        c.sprite(42 + (24 - f.width) / 2, 12 + (24 - f.height) / 2, f);
        c.text(78, 13, Canvas.fit(Canvas.lit(d.name()), true, 180), NAME, true);
        c.text(78, 27, Canvas.caps(d.status()), d.available() ? AVAILABLE : LIGHT, false);
        c.textRight(380, 13, Canvas.caps(Canvas.fit(d.job(), false, 110)), SIDE_JOB, false);
        int qw = c.textRight(380, 27, Canvas.caps("Quests"), LIGHT, false);
        c.textRight(380 - qw - 4, 27, d.done() + "/" + d.total(), WHITE, true);

        // Quest-Karte: Kopfzeile, Titel, Zitat, Ziele (je 18 px)
        List<String> quote = Canvas.wrap(d.quote(), 370);
        int rows = d.goals().size();
        int goalsY = 92 + 10 * quote.size() + 14;
        int h = goalsY + 18 * rows + 6 - 54;
        c.box(0, 54, W, h, KitData.Box.QU_CARD_TAN);
        c.text(9, 65, Canvas.caps("Quest") + " " + d.step() + "/" + d.total(), LIGHT, false);
        if (d.difficulty() != null) {
            int dw = c.textRight(381, 65, Canvas.caps(d.difficulty()), LIGHT, false);
            c.sprite(381 - dw - 21, 61, KitData.Sprite.QU_STAR);
        }
        c.text(9, 81, Canvas.fit(d.title(), true, 370), NAME, true);
        int ty = 92;
        for (String line : quote) {
            c.text(9, ty, line, BODY, false);
            ty += 10;
        }
        int gy = goalsY;
        for (Goal g : d.goals()) {
            if (g.icon() != null) c.icon8(11, gy, g.icon());
            String cnt = n(g.have()) + "/" + n(g.need());
            int bx = Math.min(258, 381 - Canvas.width(cnt, false) - 6 - 64);
            c.text(24, gy, Canvas.fit(g.name(), false, Math.min(220, bx - 14 - 24)), BODY, false);
            bar(c, bx, gy + 1, 64, g.need() > 0 ? (double) g.have() / g.need() : 0);
            c.textRight(381, gy, cnt, LIGHT, false);
            gy += 18;
        }
        // Belohnung
        int ry = 54 + h + 12;
        c.box(0, ry, W, 60, KitData.Box.QU_CARD_BROWN);
        c.sprite(8, ry + 7, KitData.Sprite.QU_GIFT);
        c.text(30, ry + 11, Canvas.caps("Belohnung"), GOLD, false);
        c.sprite(9, ry + 27, KitData.Sprite.QU_COIN);
        c.text(29, ry + 28, n(d.coins()), COINS, true);
        c.text(29, ry + 38, Canvas.caps("Coins"), LIGHT, false);
        if (d.tokens() > 0) {
            c.sprite(195 + (16 - KitData.Sprite.QU_MEDAL_S.width) / 2, ry + 27, KitData.Sprite.QU_MEDAL_S);
            c.text(215, ry + 28, n(d.tokens()), TOKENS, true);
            c.text(215, ry + 38, Canvas.caps("Tokens"), LIGHT, false);
        }
        // breiter Knopf
        if (d.button() != null) {
            int by = ry + 60 + 12;
            c.box(0, by, W, 20, KitData.Box.QU_BTN_WIDE, Canvas.Z_BUTTON);
            int tw = Canvas.width(d.button(), true);
            int gx = 194 - (17 + tw) / 2;
            c.sprite(gx, by + 3, KitData.Sprite.QU_BTN_ICON);
            c.textFlat(gx + 17, by + 6, d.button(), DARK, true);
            if (d.click() != null) c.action(0, by, W, 20, d.click(), d.button());
        }
        return c;
    }

    // =================================================================================================================
    // Streak und Abholbox
    // =================================================================================================================

    static final TextColor STREAK = TextColor.color(0xFCB079), BOX = TextColor.color(0x7DB6FC);

    /** Stufe des Streaks: Tage, Claim-Blöcke, Tokens, erreicht. */
    public record Stage(int days, long claims, long tokens, boolean reached) {}

    /** Streak: aktuell, bester, Gnadentag frei, Stufen, Tage bis zur nächsten Stufe (-1: alle erreicht). */
    public record StreakView(int days, int best, Boolean grace, List<Stage> stages, int nextIn) {}

    /** Ein Gegenstand in der Abholbox. */
    public record BoxItem(KitData.Icon icon, String name, int amount, Consumer<Player> click) {}

    public record BoxView(List<BoxItem> items, int free, Consumer<Player> takeAll) {}

    private static Canvas subHead(String title, TextColor color, KitData.Sprite icon, Consumer<Player> back) {
        Canvas c = new Canvas(W).heading("Quests", GOLD);
        c.box(0, 0, W, 48, KitData.Box.QU_HEAD);
        c.sprite(8, 13, KitData.Sprite.QU_BACK);
        c.action(8, 13, 22, 22, back, "Zurück");
        icon(c, 39, 8, 30, 32, icon);
        c.text(78, 13, Canvas.caps(title), color, true);
        return c;
    }

    public static Canvas streak(StreakView v, Consumer<Player> back) {
        Canvas c = subHead("Streak", STREAK, KitData.Sprite.QU_STREAK_HEAD, back);
        c.text(78, 27, Canvas.caps("Bester Streak") + " " + v.best() + " " + Canvas.caps(v.best() == 1 ? "Tag" : "Tage"), LIGHT, false);
        int tw = c.textRight(380, 13, Canvas.caps(v.days() == 1 ? "Tag" : "Tage"), v.days() > 0 ? LIGHT : GRAY, false);
        c.textRight(380 - tw - 5, 13, String.valueOf(v.days()), v.days() > 0 ? WHITE : GRAY, true);
        if (v.grace() != null) {
            String g = Canvas.caps(v.grace() ? "Gnadentag frei" : "Gnadentag genutzt");
            int gw = c.textRight(380, 27, g, v.grace() ? AVAILABLE : GRAY, false);
            c.sprite(380 - gw - 17, 23, KitData.Sprite.QU_SHIELD);
        }

        // Stufen: Knoten bei 43 + 93 * i, dazwischen Balken
        c.box(0, 54, W, 96, KitData.Box.QU_CARD_ORANGE);
        c.sprite(10, 62, KitData.Sprite.QU_FIRE_S);
        c.text(30, 65, Canvas.caps("Stufen"), GOLD, false);
        if (v.nextIn() >= 0) {
            c.textRight(381, 65, Canvas.caps("Nächste Stufe in") + " " + v.nextIn() + " " + Canvas.caps(v.nextIn() == 1 ? "Tag" : "Tagen"), LIGHT, false);
        } else {
            c.textRight(381, 65, Canvas.caps("Alle Stufen erreicht"), LIGHT, false);
        }
        List<Stage> st = v.stages();
        int nextIdx = -1;
        for (int i = 0; i < st.size(); i++) if (!st.get(i).reached()) { nextIdx = i; break; }
        for (int i = 0; i < st.size() && i < 4; i++) {
            Stage s = st.get(i);
            int nx = 43 + 93 * i, cx = nx + 12;
            if (i > 0) c.strip(nx - 67, 94, KitData.Strip.QU_BAR, 63, Canvas.Z_FILL);
            c.sprite(nx, 85, s.reached() || i == nextIdx && i == 0 ? KitData.Sprite.QU_NODE_ON : KitData.Sprite.QU_NODE_OFF);
            boolean hi = s.reached() || i == 0 && nextIdx == 0;
            String label = s.days() + " " + Canvas.caps("Tage");
            int lw = Canvas.width(label, hi);
            c.text(cx - (lw + 1) / 2, 115, label, hi ? GOLD : LIGHT, hi);
            // Belohnung: Schaufel + Claim-Blöcke, Medaille + Tokens, mittig
            String a1 = n(s.claims()), a2 = n(s.tokens());
            boolean cl = s.claims() > 0;
            int w = (cl ? 13 + 5 + Canvas.width(a1, true) + 10 : 0) + 12 + 6 + Canvas.width(a2, true);
            int x = cx - (w - 1) / 2;
            if (cl) {
                c.sprite(x, 127, KitData.Sprite.QU_SHOVEL);
                x += 18 + c.text(x + 18, 131, a1, CLAIMS, true) + 10;
            }
            c.sprite(x, 127, KitData.Sprite.QU_MEDAL_S);
            c.text(x + 18, 131, a2, TOKENS, true);
        }

        // So zählt dein Streak
        c.box(0, 162, W, 78, KitData.Box.QU_CARD_BROWN);
        c.sprite(10, 169, KitData.Sprite.QU_BULB);
        c.text(30, 173, Canvas.caps("So zählt dein Streak"), GOLD, false);
        String[][] rows = v.grace() != null ? new String[][]{{"Pro Tag", "1 erledigter Tagesauftrag und 10 Minuten aktive Spielzeit."},
                {"Gnadentag", "Rettet einen verpassten Tag je Woche."},
                {"Gerissen", "Der Streak startet wieder bei 1, die Stufen gibt es neu."}}
                : new String[][]{{"Pro Tag", "Mindestens 1 Tagesauftrag abholen."},
                {"Bonus", "Der erste Tagesauftrag am Tag bringt extra Coins je Streak-Tag."},
                {"Gerissen", "Der Streak startet wieder bei 1, die Stufen gibt es neu."}};
        int y = 191;
        for (String[] r : rows) {
            c.text(9, y, r[0], NAME, true);
            c.text(80, y, Canvas.fit(r[1], false, 300), BODY, false);
            y += 14;
        }
        return c;
    }

    public static Canvas box(BoxView v, Consumer<Player> back) {
        Canvas c = subHead("Abholbox", BOX, KitData.Sprite.QU_BOX_HEAD, back);
        int total = v.items().stream().mapToInt(BoxItem::amount).sum();
        int cw = c.text(78, 27, String.valueOf(total), WHITE, false);
        c.text(78 + cw + 4, 27, Canvas.caps(total == 1 ? "Item" : "Items"), LIGHT, false);
        int fw = c.textRight(380, 20, String.valueOf(v.free()), WHITE, true);
        c.textRight(380 - fw - 4, 20, Canvas.caps("Inventar frei"), LIGHT, false);
        if (v.items().isEmpty()) {
            c.box(0, 54, W, 51, KitData.Box.QU_CARD_BROWN);
            c.sprite(8, 61, KitData.Sprite.QU_CHEST_S);
            c.text(30, 65, Canvas.caps("Inhalt"), GOLD, false);
            c.text(30, 79, "Keine Items. Belohnungen, die nicht ins Inventar passen, landen hier.", BODY, false);
            return c;
        }
        // Inhalt: je Zeile ein Gegenstand (Symbol, Name, Menge), ganz unten „Alles abholen“
        int rows = Math.min(10, v.items().size());
        int h = 6;
        while (h < 26 + 18 * rows + 6) h += 9;
        c.box(0, 54, W, h, KitData.Box.QU_CARD_BROWN);
        c.sprite(8, 61, KitData.Sprite.QU_CHEST_S);
        c.text(30, 65, Canvas.caps("Inhalt"), GOLD, false);
        int y = 80;
        for (int i = 0; i < rows; i++) {
            BoxItem it = v.items().get(i);
            if (it.icon() != null) c.icon(10, y - 4, it.icon(), false);
            c.text(30, y, Canvas.fit(it.name(), false, 260), BODY, false);
            c.textRight(381, y, it.amount() + "x", WHITE, true);
            if (it.click() != null) c.action(4, y - 5, W - 8, 18, it.click(), "Abholen");
            y += 18;
        }
        int by = 54 + h + 12;
        c.box(0, by, W, 20, KitData.Box.QU_BTN_WIDE, Canvas.Z_BUTTON);
        String t = "Alles abholen";
        int tw = Canvas.width(t, true);
        c.textFlat(195 - tw / 2, by + 6, t, DARK, true);
        if (v.takeAll() != null) c.action(0, by, W, 20, v.takeAll(), t);
        return c;
    }

    // =================================================================================================================
    // Hilfe (4 Seiten)
    // =================================================================================================================

    static final TextColor HELP = TextColor.color(0x88ED9E);

    /** Inhalt einer Hilfe-Karte. */
    public sealed interface HelpItem permits Para, Row, Big, Pair, Chips, Stages {}

    /** Absatz (370 px breit, 6 px Abstand danach). */
    public record Para(String text) implements HelpItem {}

    /** Zeile: Symbol (16 px, null = keins), fetter Begriff bei labelX, Text bei textX. */
    public record Row(KitData.Sprite icon, String label, String text, int labelX, int textX) implements HelpItem {}

    /** Große Zeile: Symbol (32 px), Titel in Kapitälchen und Farbe, Text darunter. */
    public record Big(KitData.Sprite icon, String title, TextColor color, String text) implements HelpItem {}

    /** Links Text, rechts bündig Text (13 px je Zeile). */
    public record Pair(String left, String right) implements HelpItem {}

    /** Symbole mit Namen nebeneinander. */
    public record Chips(List<KitData.Sprite> icons, List<String> labels) implements HelpItem {}

    /** Streak-Stufen als Tabelle (Tage, Schaufel + Claim-Blöcke, Medaille + Tokens). */
    public record Stages(List<Stage> stages) implements HelpItem {}

    public record HelpCard(String title, List<HelpItem> items) {}

    public static Canvas help(int page, List<HelpCard> cards, Consumer<Player> back, java.util.function.IntFunction<Consumer<Player>> tab) {
        Canvas c = subHead("Hilfe", HELP, KitData.Sprite.QU_HELP_HEAD, back);
        c.text(78, 27, Canvas.caps("Seite") + " " + (page + 1) + "/4", LIGHT, false);
        String[] tabs = {"Start", "Arten", "Lohn", "Rang"};
        KitData.Sprite[] icons = {KitData.Sprite.QU_COMPASS, KitData.Sprite.QU_STAR, KitData.Sprite.QU_GIFT, KitData.Sprite.QU_S_RANK};
        for (int i = 0; i < 4; i++) {
            int x = 98 * i;
            c.box(x, 54, 95, 22, i == page ? KitData.Box.QU_HELP_TAB_ON : KitData.Box.QU_HELP_TAB_OFF, Canvas.Z_BUTTON);
            int tw = Canvas.width(tabs[i], true);
            int gx = x + (95 - (16 + 3 + tw)) / 2;
            icon(c, gx, 57, 16, 16, icons[i]);
            if (i == page) c.textFlat(gx + 19, 61, tabs[i], DARK, true);
            else c.text(gx + 19, 61, tabs[i], NAME, true);
            if (i != page) c.action(x, 54, 95, 22, tab.apply(i), tabs[i]);
        }
        int y = 81;
        for (HelpCard card : cards) y += helpCard(c, y, card) + 12;
        return c;
    }

    private static int helpCard(Canvas c, int y0, HelpCard card) {
        // erst messen (Höhe), dann zeichnen
        int bottom = layoutHelp(null, y0, card);
        int h = 6;
        while (h < bottom - y0 + 12) h += 9;
        c.box(0, y0, W, h, KitData.Box.QU_CARD_GREEN);
        layoutHelp(c, y0, card);
        return h;
    }

    /** Zeichnet (c != null) oder misst die Karte. Gibt die Unterkante des Inhalts zurück. */
    private static int layoutHelp(Canvas c, int y0, HelpCard card) {
        if (c != null) c.text(9, y0 + 9, Canvas.caps(card.title()), GOLD, false);
        int y = y0 + 22, bottom = y0 + 16;
        for (HelpItem it : card.items()) {
            switch (it) {
                case Para p -> {
                    for (String line : Canvas.wrap(p.text(), 370)) {
                        if (c != null) c.text(9, y, line, BODY, false);
                        bottom = y + 7;
                        y += 10;
                    }
                    y += 6;
                }
                case Row r -> {
                    List<String> lines = Canvas.wrap(r.text(), 382 - r.textX());
                    if (c != null) {
                        if (r.icon() != null) icon(c, 9, y - 4, 13, 16, r.icon());
                        c.text(r.labelX(), y, r.label(), NAME, true);
                        int ly = y;
                        for (String line : lines) {
                            c.text(r.textX(), ly, line, BODY, false);
                            ly += 10;
                        }
                    }
                    bottom = y + 10 * (lines.size() - 1) + 7;
                    y += r.icon() != null ? Math.max(20, 10 * lines.size() + 6) : 10 * lines.size() + 3;
                }
                case Big b -> {
                    List<String> lines = Canvas.wrap(b.text(), 300);
                    if (c != null) {
                        if (b.icon().height > 32) c.sprite(8 + (32 - b.icon().width) / 2, y - 10, b.icon());
                        else icon(c, 8, y - 3, 32, 32, b.icon());
                        c.text(49, y, Canvas.caps(b.title()), b.color(), true);
                        int ly = y + 11;
                        for (String line : lines) {
                            c.text(49, ly, line, BODY, false);
                            ly += 10;
                        }
                    }
                    bottom = y + 28;
                    y += 36;
                }
                case Pair p -> {
                    if (c != null) {
                        c.text(9, y, p.left(), BODY, false);
                        c.textRight(381, y, p.right(), BODY, false);
                    }
                    bottom = y + 7;
                    y += 13;
                }
                case Chips ch -> {
                    if (c != null) {
                        int x = 9;
                        for (int i = 0; i < ch.icons().size(); i++) {
                            KitData.Sprite s = ch.icons().get(i);
                            c.sprite(x, y - 4 + (16 - s.height) / 2, s);
                            x += s.width + 4;
                            x += c.text(x, y, ch.labels().get(i), BODY, false) + 12;
                        }
                    }
                    bottom = y + 12;
                    y += 21;
                }
                case Stages st -> {
                    for (int i = 0; i < st.stages().size() && i < 4; i++) {
                        Stage s = st.stages().get(i);
                        int cx = 55 + 93 * i;
                        if (c != null) {
                            String d = String.valueOf(s.days());
                            int dw = Canvas.width(d, true), tw = Canvas.width("Tage", false);
                            int lx = cx - (dw + 4 + tw) / 2;
                            c.text(lx, y + 2, d, WHITE, true);
                            c.text(lx + dw + 4, y + 2, "Tage", BODY, false);
                            String a1 = n(s.claims()), a2 = n(s.tokens());
                            boolean cl = s.claims() > 0;
                            int w = (cl ? 13 + 5 + Canvas.width(a1, true) + 10 : 0) + 12 + 6 + Canvas.width(a2, true);
                            int x = cx - (w - 1) / 2;
                            if (cl) {
                                c.sprite(x, y + 14, KitData.Sprite.QU_SHOVEL);
                                x += 18 + c.text(x + 18, y + 18, a1, CLAIMS, true) + 10;
                            }
                            c.sprite(x, y + 14, KitData.Sprite.QU_MEDAL_S);
                            c.text(x + 18, y + 18, a2, TOKENS, true);
                        }
                    }
                    bottom = y + 30;
                    y += 32;
                }
            }
        }
        return bottom;
    }

    /** Inhalt der Hilfe-Seiten (0 bis 3). ranks: Text der Ränge („Neuling, Lehrling ab 15, …“). */
    public static List<HelpCard> helpPage(int page, String ranks, List<Stage> stages, String mainText, String setBonus) {
        return switch (page) {
            case 0 -> List.of(
                    new HelpCard("Was sind Quests", List.of(
                            new Para("Quests sind Aufgaben beim Spielen: abbauen, bauen, jagen, angeln, erkunden."),
                            new Para("Jede erledigte Quest bringt eine Belohnung, zum Beispiel Coins oder Tokens."),
                            new Para("Die meisten Quests laufen von selbst mit. Du musst dafür nichts starten."))),
                    new HelpCard("So läuft eine Quest", List.of(
                            new Row(KitData.Sprite.QU_Q_MARK, "Annehmen", "Nur Hauptquests und Geheim-Quests, im Menü oder am NPC.", 30, 103),
                            new Row(KitData.Sprite.QU_COMPASS, "Verfolgen", "Zeigt die Quest mit ihrem Fortschritt oben in der Bossbar.", 30, 103),
                            new Row(KitData.Sprite.QU_HOURGLASS, "Erledigen", "Einfach spielen. Items gibst du im Menü oder am NPC ab.", 30, 103),
                            new Row(KitData.Sprite.QU_GIFT, "Abholen", "Die meisten zahlen sofort, der Rest im Menü oder am NPC.", 30, 103),
                            new Row(KitData.Sprite.QU_LOCK, "Abbrechen", "Hauptquests und Geheim-Quests, der Fortschritt ist dann weg.", 30, 103))));
            case 1 -> List.of(
                    new HelpCard("Laufen von selbst", List.of(
                            new Big(KitData.Sprite.QU_T_DAILY, "Täglich", Area.DAILY.title, "3 Aufträge je Tag, neu um 00:00 Uhr."),
                            new Big(KitData.Sprite.QU_T_WEEKLY, "Wöchentlich", Area.WEEKLY.title, "3 Aufträge je Woche, neu Montag um 00:00 Uhr."),
                            new Big(KitData.Sprite.QU_T_MILE, "Meilensteine", Area.MILE.title, "Große Ziele in Stufen, zählen dauerhaft mit."),
                            new Big(KitData.Sprite.QU_T_TEAM, "Team", Area.TEAM.title, "1 Aufgabe je Woche fürs ganze Team, ab 2 Mitgliedern."))),
                    new HelpCard("Mit Annahme", List.of(
                            new Big(KitData.Sprite.QU_T_MAIN, "Hauptquests", Area.MAIN.title, mainText),
                            new Big(KitData.Sprite.QU_EYE, "Geheim", TextColor.color(0xC77DFC), "Versteckt am Spawn. Wer sie findet, sieht sie im Menü."))));
            case 2 -> List.of(
                    new HelpCard("Belohnungen", List.of(
                            new Chips(List.of(KitData.Sprite.QU_COIN, KitData.Sprite.QU_SHOVEL, KitData.Sprite.QU_MEDAL_S, KitData.Sprite.QU_FEATHER, KitData.Sprite.QU_GIFT),
                                    List.of("Coins", "Claim-Blöcke", "Tokens", "Titel", "Items")),
                            new Row(null, "Aufträge", "Coins und Tokens. Team-Aufgaben zahlen in die Team-Kasse.", 9, 80),
                            new Row(null, "Einmalige", "Dazu Claim-Blöcke, Titel oder Items.", 9, 80),
                            new Row(null, "Discord", "Titel nur mit verifiziertem Account (/verify).", 9, 80),
                            new Row(null, "Set-Bonus", setBonus, 9, 80),
                            new Row(null, "Abholbox", "Items, die nicht ins Inventar passen, warten dort.", 9, 80))),
                    new HelpCard("Streak", List.of(
                            new Row(null, "Pro Tag", "Mindestens 1 Tagesauftrag am Tag abholen.", 9, 80),
                            new Row(null, "Bonus", "Der erste Tagesauftrag am Tag bringt extra Coins je Streak-Tag.", 9, 80),
                            new Row(null, "Gerissen", "Der Streak startet wieder bei 1, die Stufen gibt es neu.", 9, 80),
                            new Stages(stages))));
            default -> List.of(
                    new HelpCard("Tokens und Rang", List.of(
                            new Row(null, "Tokens", "Für jede erledigte Quest, dazu Set-Bonus und Streak-Stufen.", 9, 73),
                            new Row(null, "Ränge", ranks, 9, 73),
                            new Row(null, "Rangliste", "Top 10 nach Tokens, für den Monat und gesamt.", 9, 73))),
                    new HelpCard("Resets", List.of(
                            new Pair("Tagesaufträge", "jeden Tag um 00:00 Uhr"),
                            new Pair("Wochenaufträge und Team", "jeden Montag um 00:00 Uhr"),
                            new Pair("Rangliste Monat", "am Monatsersten um 00:00 Uhr"))),
                    new HelpCard("Befehle", List.of(
                            new Pair("/quest", "Menü öffnen"),
                            new Pair("/quest <Kategorie>", "daily, weekly, hauptquests, meilensteine, team"),
                            new Pair("/quest abholen", "Belohnungen und Abholbox abholen"),
                            new Pair("/quest top · /quest streak", "Rangliste und Streak"),
                            new Pair("/quest gui", "Menü als Kiste, /quest wieder als Fenster"))));
        };
    }

    // =================================================================================================================
    // Rangliste (Monat / gesamt)
    // =================================================================================================================

    static final TextColor SILVER = TextColor.color(0xC7D0DB), GOLDEN = TextColor.color(0xFCC63C),
            BRONZE = TextColor.color(0xE29A63);

    public record Entry(int place, String name, long tokens) {}

    /** Rangliste: Monat (sonst gesamt), Name des Monats, eigene Tokens und Platz (0 = keiner), Top 10, Hinweis. */
    public record RankView(boolean month, String monthName, long tokens, int place, List<Entry> top, String hint) {}

    public static Canvas ranking(RankView v, Consumer<Player> back, Consumer<Player> month, Consumer<Player> total) {
        Canvas c = subHead("Rangliste", GOLD, KitData.Sprite.QU_RANK_HEAD, back);
        c.text(78, 27, Canvas.caps(v.month() ? "Monat " + v.monthName() : "Seit Start"), LIGHT, false);
        int tw = c.textRight(380, 13, Canvas.caps("Tokens"), LIGHT, false);
        int nw = c.textRight(380 - tw - 6, 13, n(v.tokens()), WHITE, true);
        c.sprite(380 - tw - 6 - nw - 16, 9, KitData.Sprite.QU_MEDAL);
        c.textRight(380, 27, Canvas.caps("Dein Platz") + " " + (v.place() > 0 ? "#" + v.place() : "—"), LIGHT, false);
        // Reiter Monat / Gesamt
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 0) == v.month();
            int x = i * 198;
            c.box(x, 54, 192, 22, on ? KitData.Box.QU_RANK_TAB_ON : KitData.Box.QU_RANK_TAB_OFF, Canvas.Z_BUTTON);
            KitData.Sprite ic = i == 0 ? KitData.Sprite.QU_TAB_CLOCK : KitData.Sprite.QU_TAB_TROPHY;
            String label = Canvas.caps(i == 0 ? "Monat" : "Gesamt");
            int lw = Canvas.width(label, true);
            int gx = x + (192 - (ic.width + 4 + lw)) / 2;
            c.sprite(gx, 57, ic);
            if (on) c.textFlat(gx + ic.width + 4, 61, label, DARK, true);
            else c.text(gx + ic.width + 4, 61, label, NAME, true);
            if (!on) c.action(x, 54, 192, 22, i == 0 ? month : total, i == 0 ? "Monat" : "Gesamt");
        }
        // Podest: 2 | 1 | 3
        int[][] pod = {{1, 0, 90, 70}, {0, 132, 81, 79}, {2, 264, 90, 70}};
        KitData.Box[] boxes = {KitData.Box.QU_POD_GOLD, KitData.Box.QU_POD_SILVER, KitData.Box.QU_POD_BRONZE};
        KitData.Sprite[] medals = {KitData.Sprite.QU_M_GOLD, KitData.Sprite.QU_M_SILVER, KitData.Sprite.QU_M_BRONZE};
        TextColor[] cols = {GOLDEN, SILVER, BRONZE};
        for (int[] p : pod) {
            int rank = p[0], x = p[1], y = p[2], h = p[3];
            c.box(x, y, 126, h, boxes[rank], Canvas.Z_BUTTON);
            c.text(x + 9, y + 9, "#" + (rank + 1), cols[rank], true);
            if (rank == 0) c.sprite(x + 101, y + 7, KitData.Sprite.QU_CROWN);
            KitData.Sprite m = medals[rank];
            c.sprite(x + 63 - m.width / 2, rank == 0 ? y + 13 : y + 10, m);
            Entry e = rank < v.top().size() ? v.top().get(rank) : null;
            String name = e == null ? "—" : Canvas.fit(Canvas.lit(e.name()), true, 110);
            c.textCenter(x + 63, 135, name, NAME, true);
            if (e != null) {
                String num = n(e.tokens()), t = Canvas.caps("Tokens");
                int w1 = Canvas.width(num, true), w2 = Canvas.width(t, false);
                int sx = x + 63 - (w1 + 6 + w2) / 2;
                c.text(sx, 144, num, cols[rank], true);
                c.text(sx + w1 + 6, 144, t, LIGHT, false);
            }
        }
        // Plätze 4 bis 10 in zwei Spalten
        c.box(0, 171, W, 87, KitData.Box.QU_CARD_BROWN);
        for (int i = 3; i < 10; i++) {
            int col = i < 7 ? 0 : 1, row = col == 0 ? i - 3 : i - 7;
            int x = 8 + 190 * col, y = 177 + 18 * row;
            c.box(x, y, 184, 16, KitData.Box.QU_RANK_ROW, Canvas.Z_BUTTON);
            c.textRight(x + 25, y + 3, "#" + (i + 1), LIGHT, false);
            if (i < v.top().size()) {
                Entry e = v.top().get(i);
                int tw2 = c.textRight(x + 177, y + 3, n(e.tokens()), WHITE, true);
                c.text(x + 33, y + 3, Canvas.fit(Canvas.lit(e.name()), false, Math.min(100, 177 - 33 - tw2 - 6)), NAME, false);
            }
        }
        // Du
        c.box(0, 270, W, 42, KitData.Box.QU_CARD_GOLD);
        c.text(9, 279, v.place() > 0 ? "#" + v.place() : "—", LIGHT, false);
        c.text(49, 279, Canvas.caps("Du"), GOLD, true);
        int dw = c.textRight(380, 279, Canvas.caps("Tokens"), LIGHT, false);
        c.textRight(380 - dw - 6, 279, n(v.tokens()), WHITE, true);
        if (v.hint() != null) c.text(9, 291, Canvas.fit(v.hint(), false, 370), LIGHT, false);
        return c;
    }
}
