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
        c.text(150, 8, Canvas.caps(h.rank()), GOLD, true);
        bar(c, 150, 21, 64, h.progress());
        c.text(150, 31, h.next() == null ? Canvas.caps("Höchster Rang") : "→ " + Canvas.caps(h.next()), GRAY, false);
        int tw = c.textRight(380, 12, Canvas.caps("Tage"), GRAY, false);
        c.textRight(380 - tw - 5, 12, String.valueOf(h.streak()), h.streak() > 0 ? WHITE : GRAY, true);
        c.sprite(326, 8, KitData.Sprite.QU_FIRE);
        tw = c.textRight(380, 28, Canvas.caps("Tokens"), LIGHT, false);
        c.textRight(380 - tw - 5, 28, n(h.tokens()), WHITE, true);
        c.sprite(315, 25, KitData.Sprite.QU_MEDAL);

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
            c.text(30, 76, Canvas.caps(t.name()), NAME, true);
            rewards(c, 153, 72, t.coins(), t.claims(), t.tokens());
            int ty = 92;
            for (String line : Canvas.wrap(t.task(), 240)) {
                if (ty > 122) break;
                c.text(30, ty, line, BODY, false);
                ty += 10;
            }
            bar(c, 30, 136, 150, t.progress());
            c.textRight(273, 135, t.goal(), WHITE, false);
            if (t.button() != null) {
                c.box(284, 91, 96, 18, KitData.Box.QU_BTN_GOLD, Canvas.Z_BUTTON);
                c.textFlat(332 - Canvas.width(t.button(), true) / 2, 97, t.button(), DARK, true);
                c.action(284, 91, 96, 18, a.track(), t.button());
            }
            if (t.hint() != null) c.textCenter(332, 113, Canvas.caps(t.hint()), GOLD, false);
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

    private static void tile(Canvas c, int x, int y, Tile t, Actions a) {
        Area ar = t.area();
        c.box(x, y, 126, 67, ar.tile, Canvas.Z_BUTTON);
        icon(c, x + 8, y + 7, 30, 32, ar.icon);
        c.text(x + 44, y + 9, Canvas.caps(Canvas.fit(t.title(), ar.bold, 78)), ar.title, ar.bold);
        // Untertitel kommt fertig (Kapitälchen außer Zahlen und Einheiten wie „24min“)
        c.text(x + 44, y + 20, t.sub(), ar.sub, false);
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
                           String infoLabel, String info, int page, int pages, Bonus bonus) {}

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
            for (int i = 0; i < b.total(); i++) {
                if (i < b.done()) c.sprite(11 + 18 * i, y + 26, KitData.Sprite.QU_QBIG);
                else c.sprite(11 + 18 * i, y + 26, KitData.Sprite.QU_QBIG);
            }
            int bx = 11 + 18 * b.total() + 2;
            int dw = c.text(bx, y + 30, b.done() + "/" + b.total(), WHITE, false);
            c.text(bx + dw + 4, y + 30, Canvas.caps("Erledigt"), WHITE, false);
            rewardsRight(c, 381, y + 30, b.rewards());
        }
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
            if (k.cream()) barCream(c, 30, barY, 150, k.progress());
            else bar(c, 30, barY, 150, k.progress());
            // rechts bündig wie im Original: bei gefülltem Balken 1 px weiter rechts (dort zählt die Füllung mit)
            if (k.count() != null) c.textRight(k.progress() > 0 ? 273 : 272, barY - 1, k.count(), WHITE, false);
        }
        if (hintY > 0) c.text(30, hintY, k.hint(), LIGHT, false);
        // rechts: Knopf mit Text darunter, mittig in der Karte
        int by = y + (h + 1) / 2 - 16;
        switch (k.button()) {
            case GOLD, LINE -> {
                c.box(284, by, 96, 18, k.button() == Btn.GOLD ? KitData.Box.QU_BTN_GOLD : KitData.Box.QU_BTN_LINE, Canvas.Z_BUTTON);
                int bw = Canvas.width(k.buttonText(), true);
                if (k.button() == Btn.GOLD) c.textFlat(332 - bw / 2, by + 6, k.buttonText(), DARK, true);
                else c.text(331 - bw / 2, by + 6, k.buttonText(), NAME, true);
                if (k.click() != null) c.action(284, by, 96, 18, k.click(), k.buttonText());
                // wie die Zahl am Balken: bei gefülltem Balken 1 px weiter rechts
                int sx = 332 + (k.progress() != null && k.progress() > 0 && k.button() == Btn.LINE ? 1 : 0);
                if (k.sub() != null) c.textCenter(sx, by + 22, k.sub(), k.button() == Btn.GOLD ? GOLD : LIGHT, false);
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
}
