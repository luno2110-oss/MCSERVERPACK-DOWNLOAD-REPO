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
        EVENT("Event", 0xFC981F, 0xEEC14A, KitData.Box.QU_TILE_EVENT, KitData.Sprite.QU_T_EVENT),
        DAILY("Täglich", 0xFCAF49, 0x9FA1A7, KitData.Box.QU_TILE_DAILY, KitData.Sprite.QU_T_DAILY),
        WEEKLY("Wöchentlich", 0xF26A6A, 0x9FA1A7, KitData.Box.QU_TILE_WEEKLY, KitData.Sprite.QU_T_WEEKLY),
        MAIN("Hauptquests", 0x6BD169, 0x6BD169, KitData.Box.QU_TILE_MAIN, KitData.Sprite.QU_T_MAIN),
        MILE("Meilensteine", 0xFCD559, 0x9FA1A7, KitData.Box.QU_TILE_MILE, KitData.Sprite.QU_T_MILE),
        TEAM("Team", 0x8DB2E5, 0x6A6D74, KitData.Box.QU_TILE_TEAM, KitData.Sprite.QU_T_TEAM),
        SIDE("Nebenquests", 0xD69F59, 0x88ED9E, KitData.Box.QU_TILE_SIDE, KitData.Sprite.QU_T_SIDE);

        public final String label;
        public final TextColor title, sub;
        final KitData.Box tile;
        final KitData.Sprite icon;

        Area(String label, int title, int sub, KitData.Box tile, KitData.Sprite icon) {
            this.label = label;
            this.title = TextColor.color(title);
            this.sub = TextColor.color(sub);
            this.tile = tile;
            this.icon = icon;
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
        c.text(150, 30, h.next() == null ? Canvas.caps("Höchster Rang") : "→ " + Canvas.caps(h.next()), GRAY, false);
        int tw = c.textRight(379, 12, Canvas.caps("Tage"), GRAY, false);
        c.textRight(379 - tw - 4, 12, String.valueOf(h.streak()), h.streak() > 0 ? WHITE : GRAY, true);
        c.sprite(326, 8, KitData.Sprite.QU_FIRE);
        tw = c.textRight(379, 28, Canvas.caps("Tokens"), LIGHT, false);
        c.textRight(379 - tw - 4, 28, n(h.tokens()), WHITE, true);
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
            c.text(vx + 6, 63, Canvas.caps(t.area()), GRAY, false);
            c.sprite(9, 72, KitData.Sprite.QU_COMPASS);
            c.text(30, 76, Canvas.caps(t.name()), NAME, true);
            rewards(c, 153, 72, t.coins(), t.claims(), t.tokens());
            int ty = 89;
            for (String line : Canvas.wrap(t.task(), 240)) {
                if (ty > 119) break;
                c.text(30, ty, line, BODY, false);
                ty += 10;
            }
            bar(c, 30, 136, 150, t.progress());
            c.textRight(271, 135, t.goal(), WHITE, true);
            if (t.button() != null) {
                c.box(284, 91, 96, 18, KitData.Box.QU_BTN_GOLD, Canvas.Z_BUTTON);
                c.textFlat(332 - Canvas.width(t.button(), true) / 2, 96, t.button(), DARK, true);
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
            x += 19 + c.text(x + 19, y + 4, n(coins), COINS, true) + 12;
        }
        if (claims > 0) {
            c.sprite(x, y, KitData.Sprite.QU_SHOVEL);
            x += 18 + c.text(x + 18, y + 4, n(claims), CLAIMS, true) + 12;
        }
        if (tokens > 0) {
            c.sprite(x, y, KitData.Sprite.QU_MEDAL_S);
            x += 17 + c.text(x + 17, y + 4, n(tokens), TOKENS, true) + 12;
        }
        return x;
    }

    private static void tile(Canvas c, int x, int y, Tile t, Actions a) {
        Area ar = t.area();
        c.box(x, y, 126, 67, ar.tile, Canvas.Z_BUTTON);
        icon(c, x + 8, y + 7, 30, 32, ar.icon);
        c.text(x + 44, y + 9, Canvas.caps(Canvas.fit(t.title(), false, 78)), ar.title, false);
        // Untertitel kommt fertig (Kapitälchen außer Zahlen und Einheiten wie „24min“)
        c.text(x + 44, y + 20, t.sub(), ar.sub, false);
        bar(c, x + 7, y + 52, 65, t.total() > 0 ? (double) t.done() / t.total() : t.total() < 0 ? t.done() / 100.0 : 0);
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
}
