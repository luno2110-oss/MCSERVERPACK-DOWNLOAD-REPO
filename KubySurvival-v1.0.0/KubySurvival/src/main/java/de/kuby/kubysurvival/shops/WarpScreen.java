package de.kuby.kubysurvival.shops;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.util.I18n;
import de.kuby.kubysurvival.shops.WarpService.Category;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Spieler-Warps (/sw) nach der Referenz „Neue-referenzen“: Kopf mit Logo, Schriftzug und Coins, Knöpfe Meine Warps /
 * Suchen / Sortieren, Kategorie-Chips (Alles, Mobfarm, Shops, Bücher, Spiele, Parkour), je Warp eine Karte in der Farbe
 * der Kategorie (Name in seiner Farbe, Besitzer, Ort, Koordinaten, Besuche diese Woche und gesamt, Beschreibung,
 * Anpinnen und Teleport), Blättern; leere Kategorie mit Karte; Meine Warps mit drei Plätzen (frei, gesperrt, belegt).
 * Kartenhöhe 27 + 10 je Zeile, die nächste Karte beginnt auf dem nächsten 9-px-Raster (wie im Original).
 */
public final class WarpScreen {

    public static final int W = 390, HEIGHT = 330, PER_PAGE = 3;
    static final TextColor TEAL = TextColor.color(0x7CEFE3), GRAY = TextColor.color(0x9FA1A7),
            BODY = TextColor.color(0xD1D2D5), DIM = TextColor.color(0x797D84), NAV = TextColor.color(0xC0C4CB),
            GOLD = TextColor.color(0xFCD45D), GREEN = TextColor.color(0x6DE288), WHITE = TextColor.color(0xFCFCFC);
    public static final TextColor ON = TextColor.color(0x1C140A);

    /** Ein Warp auf einer Karte. */
    public record Card(String name, int color, String owner, Category category, String place, String coords, long week,
                       long total, String desc, boolean pinned) {}

    /** Liste: Coins, Filter (null = alle), beliebt zuerst, Suche (leer = keine), Karten dieser Seite, Seite, Seiten. */
    public record ListView(long coins, Category filter, boolean popular, String search, List<Card> cards, int page,
                           int pages) {}

    /** Ein Platz unter „Meine Warps“: Preis, belegt (warp), frei (kaufbar, missing fehlende Coins) oder gesperrt. */
    public record Slot(int number, long price, Card warp, boolean free, long missing) {}

    public record MineView(long coins, List<Slot> slots) {}

    public interface Actions {
        Consumer<Player> list();

        Consumer<Player> mine();

        Consumer<Player> search();

        Consumer<Player> sort();

        Consumer<Player> filter(Category c);

        Consumer<Player> page(int page);

        Consumer<Player> pin(String warp);

        Consumer<Player> teleport(String warp);

        Consumer<Player> buy(int slot);

        Consumer<Player> edit(String warp);
    }

    private WarpScreen() {
    }

    // =================================================================================================================
    // Farben und Bilder je Kategorie
    // =================================================================================================================

    static KitData.Box card(Category c) {
        return switch (c) {
            case SHOPS -> KitData.Box.PW_CARD_GREEN;
            case BUECHER -> KitData.Box.PW_CARD_PURPLE;
            case MOBFARM -> KitData.Box.PW_CARD_RED;
            case SPIELE -> KitData.Box.PW_CARD_ORANGE;
            case PARKOUR -> KitData.Box.PW_CARD_BLUE;
        };
    }

    static KitData.Box teleport(Category c) {
        return switch (c) {
            case SHOPS -> KitData.Box.PW_TP_GREEN;
            case BUECHER -> KitData.Box.PW_TP_PURPLE;
            case MOBFARM -> KitData.Box.PW_TP_RED;
            case SPIELE -> KitData.Box.PW_TP_ORANGE;
            case PARKOUR -> KitData.Box.PW_TP_BLUE;
        };
    }

    static TextColor label(Category c) {
        return TextColor.color(switch (c) {
            case SHOPS -> 0x6DE288;
            case BUECHER -> 0xCD89FC;
            case MOBFARM -> 0xFC7986;
            case SPIELE -> 0xEFB16A;
            case PARKOUR -> 0x62CAFC;
        });
    }

    private static KitData.Box chipOn(Category c) {
        if (c == null) return KitData.Box.PW_CHIP_ALLES;
        return switch (c) {
            case SHOPS -> KitData.Box.PW_CHIP_SHOPS;
            case BUECHER -> KitData.Box.PW_CHIP_BUECHER;
            case MOBFARM -> KitData.Box.PW_CHIP_MOBFARM;
            case SPIELE -> KitData.Box.PW_CHIP_SPIELE;
            case PARKOUR -> KitData.Box.PW_CHIP_PARKOUR;
        };
    }

    private static KitData.Sprite chipIcon(Category c) {
        if (c == null) return KitData.Sprite.PW_C_ALLES;
        return switch (c) {
            case SHOPS -> KitData.Sprite.PW_C_SHOPS;
            case BUECHER -> KitData.Sprite.PW_C_BUECHER;
            case MOBFARM -> KitData.Sprite.PW_C_MOBFARM;
            case SPIELE -> KitData.Sprite.PW_C_SPIELE;
            case PARKOUR -> KitData.Sprite.PW_C_PARKOUR;
        };
    }

    // =================================================================================================================
    // Kopf
    // =================================================================================================================

    private static String n(long v) {
        return String.format(Locale.GERMANY, "%,d", v);
    }

    /** Rechts im Kopf: Münze, „Deine / Coins“ und der Kontostand. */
    /** Gibt x der Münze zurück (links davon darf Text stehen). */
    private static int coins(Canvas c, long coins) {
        // große Kontostände schieben Münze und Beschriftung nach links (8 px Abstand wie im Original)
        int vx = 380 - Canvas.width(n(coins), true);
        int lx = Math.min(327, vx - 6 - Math.max(Canvas.width("Deine", false), Canvas.width("Coins", false)));
        c.sprite(lx - 16, 14, KitData.Sprite.PW_COIN);
        c.text(lx, 10, "Deine", GRAY, false);
        c.text(lx, 20, "Coins", GRAY, false);
        c.text(vx, 15, n(coins), GOLD, true);
        return lx - 16;
    }

    private static Canvas head(long coins) {
        Canvas c = new Canvas(W).heading("Spielerwarps", TEAL);
        c.box(0, 0, W, 46, KitData.Box.PW_HEAD);
        c.sprite(4, 3, KitData.Sprite.PW_LOGO);
        c.sprite(46, 8, KitData.Sprite.PW_TITLE);
        c.text(47, 29, "→ Shops · Farmen · Spiele · Parkour", GRAY, false);
        coins(c, coins);
        return c;
    }

    /** Kleiner Kopf (Meine Warps): Zurück, Titel, Untertitel, Coins. */
    private static Canvas smallHead(long coins, String title, String sub, Consumer<Player> back) {
        Canvas c = new Canvas(W).heading("Spielerwarps", TEAL);
        c.box(0, 0, W, 38, KitData.Box.PW_HEAD_SMALL);
        c.sprite(8, 9, KitData.Sprite.PW_BACK);
        c.action(8, 9, 22, 22, back, "Zurück");
        int room = coins(c, coins) - 6 - 40;
        c.text(40, 10, Canvas.fit(upper(title), true, room), TEAL, true);
        c.text(40, 21, Canvas.fit(sub, false, room), GRAY, false);
        return c;
    }

    /** Kopf über dem Suchfeld (darunter Feld und Knöpfe von Minecraft). */
    public static Canvas searchHead() {
        Canvas c = new Canvas(W).heading("Spielerwarps", TEAL);
        c.box(0, 0, W, 46, KitData.Box.PW_HEAD);
        c.sprite(4, 3, KitData.Sprite.PW_LOGO);
        c.text(46, 12, "Warps suchen", TEAL, true);
        c.text(46, 27, "Name, Besitzer oder ein Wort aus der Beschreibung", GRAY, false);
        return c;
    }

    // =================================================================================================================
    // Liste
    // =================================================================================================================

    public static Canvas list(ListView v, Actions a) {
        Canvas c = head(v.coins());
        // Knöpfe: Meine Warps, Suchen, Sortieren (mittig, 4 px Abstand)
        String search = v.search().isEmpty() ? "Suchen"
                : Canvas.lit("„" + Canvas.clean(Canvas.fit(Canvas.lit(v.search()), true, 60)) + "“");
        String sort = v.popular() ? "Beliebt zuerst" : "Neueste zuerst";
        int w1 = Canvas.width(Canvas.clean("Meine Warps"), true) + 28, w2 = Canvas.width(search, true) + 28,
                w3 = Canvas.width(Canvas.clean(sort), true) + 28;
        int x = (W - (w1 + w2 + w3 + 8)) / 2;
        button(c, x, w1, KitData.Box.PW_BTN_MINE, KitData.Sprite.PW_I_MINE, 7, 5, "Meine Warps", ON, a.mine());
        x += w1 + 4;
        button(c, x, w2, KitData.Box.PW_BTN_SEARCH, KitData.Sprite.PW_I_SEARCH, 3, 3, search, TEAL, a.search());
        x += w2 + 4;
        button(c, x, w3, KitData.Box.PW_BTN_SORT, KitData.Sprite.PW_I_SORT, 3, 3, sort, NAV, a.sort());

        // Chips
        Category[] cats = {null, Category.MOBFARM, Category.SHOPS, Category.BUECHER, Category.SPIELE, Category.PARKOUR};
        int total = 15;
        int[] widths = new int[cats.length];
        for (int i = 0; i < cats.length; i++) {
            boolean on = cats[i] == v.filter();
            widths[i] = Canvas.width(Canvas.clean(chipName(cats[i])), on) + 25;
            total += widths[i];
        }
        x = (W - total) / 2;
        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            boolean on = cat == v.filter();
            c.box(x, 79, widths[i], 18, on ? chipOn(cat) : KitData.Box.PW_CHIP_OFF, Canvas.Z_BUTTON);
            c.sprite(x + (cat == null ? 4 : 3), 82, chipIcon(cat));
            if (on) c.text(x + 20, 84, chipName(cat), ON, true);
            else c.text(x + 20, 84, chipName(cat), BODY, false);
            if (!on) c.action(x, 79, widths[i], 18, a.filter(cat), chipName(cat));
            x += widths[i] + 3;
        }

        int y = 106;
        if (v.cards().isEmpty()) {
            c.box(0, y, W, 67, KitData.Box.PW_CARD_EMPTY);
            c.sprite(4, y + 3, KitData.Sprite.PW_MAP);
            c.text(68, y + 14, "Hier ist noch nichts", TEAL, true);
            c.text(68, y + 28, v.search().isEmpty() ? "In dieser Kategorie gibt es gerade keinen Warp."
                    : "Kein Warp passt zu deiner Suche.", BODY, false);
            return c;
        }
        for (Card card : v.cards()) y += step(cardBox(c, y, card, a, false));
        if (v.pages() > 1) {
            int cx = 195;
            c.box(cx - 33, y, 18, 18, v.page() > 0 ? KitData.Box.PW_PAGE_ON : KitData.Box.PW_PAGE_OFF, Canvas.Z_BUTTON);
            if (v.page() > 0) c.sprite(cx - 30, y + 3, KitData.Sprite.PW_ARROW_L_ON);
            else c.sprite(cx - 29, y + 3, KitData.Sprite.PW_ARROW_L);
            c.text(cx - 9, y + 5, (v.page() + 1) + "/" + v.pages(), BODY, false);
            c.box(cx + 15, y, 18, 18, v.page() + 1 < v.pages() ? KitData.Box.PW_PAGE_ON : KitData.Box.PW_PAGE_OFF, Canvas.Z_BUTTON);
            if (v.page() + 1 < v.pages()) c.sprite(cx + 18, y + 3, KitData.Sprite.PW_ARROW_R);
            else c.sprite(cx + 19, y + 3, KitData.Sprite.PW_ARROW_R_OFF);
            if (v.page() > 0) c.action(cx - 33, y, 18, 18, a.page(v.page() - 1), "Vorige Seite");
            if (v.page() + 1 < v.pages()) c.action(cx + 15, y, 18, 18, a.page(v.page() + 1), "Nächste Seite");
        }
        return c;
    }

    private static String chipName(Category c) {
        return c == null ? "Alles" : c.chip;
    }

    private static void button(Canvas c, int x, int w, KitData.Box box, KitData.Sprite icon, int ix, int iy, String label,
                               TextColor color, Consumer<Player> click) {
        c.box(x, 52, w, 18, box, Canvas.Z_BUTTON);
        c.sprite(x + ix, 52 + iy, icon);
        c.text(x + 21, 57, label, color, true);
        c.action(x, 52, w, 18, click, label);
    }

    /** Abstand zur nächsten Karte: Höhe + 6, auf das 9-px-Raster aufgerundet. */
    private static int step(int h) {
        return (h + 6 + 8) / 9 * 9;
    }

    /** Eine Karte ab y. Gibt die Höhe zurück. */
    static int cardBox(Canvas c, int y, Card w, Actions a, boolean own) {
        // Zeilen: Ort (+ Koordinaten, wenn Platz ist), Koordinaten, Besuche, Beschreibung
        // Name, Besitzer, Ort und Beschreibung haben Spieler geschrieben: nie übersetzen (Canvas.lit)
        String place = Canvas.fit(Canvas.lit(w.place()), false, 150);
        int placeW = Canvas.width(place, false);
        int coordsW = Canvas.width(Canvas.clean(w.coords()), false);
        boolean inline = 60 + placeW + 24 + coordsW <= 220;
        int lines = 2 + (inline ? 0 : 1) + (w.desc().isBlank() ? 0 : 1);
        int h = 27 + 10 * lines;
        c.box(0, y, W, h, card(w.category()));
        if (w.category() == Category.SHOPS) c.sprite(3, y + 3, KitData.Sprite.PW_STALL);
        else if (w.category() == Category.BUECHER) c.sprite(3, y + 3, KitData.Sprite.PW_SHELF);
        else c.sprite(9, y + 6, KitData.Sprite.PW_PORTAL);
        // Kategorie rechts; Name und Besitzer enden spätestens 6 px davor
        int room = 382 - c.textRight(382, y + 9, w.category().label, label(w.category()), true) - 6 - 48;
        int nx = 48 + c.text(48, y + 9, Canvas.fit(Canvas.lit(w.name()), true, Math.min(130, room - 40)), TextColor.color(w.color()), true);
        // „ von “ nicht als Vorlage (würde in anderen Texten mitübersetzt)
        String von = I18n.context() ? " by " : " von ";
        int vonW = Canvas.width(von, false);
        c.text(nx + 2, y + 9, List.of(Span.of(von, GRAY),
                Span.of(Canvas.fit(Canvas.lit(w.owner()), false, Math.max(10, 48 + room - nx - 2 - vonW)), GRAY)));
        // Textspalte wie im Original abwechselnd 61 / 60 / 61 px
        int ty = y + 20;
        c.sprite(48, ty - 1, KitData.Sprite.PW_R_HOUSE);
        c.text(61, ty, place, BODY, false);
        if (inline) {
            int px = 60 + placeW + 12;
            c.sprite(px, ty - 1, KitData.Sprite.PW_R_PIN);
            c.text(px + 12, ty, w.coords(), BODY, false);
        } else {
            ty += 10;
            c.sprite(48, ty - 1, KitData.Sprite.PW_R_PIN);
            c.text(60, ty, w.coords(), BODY, false);
        }
        ty += 10;
        c.sprite(48, ty - 2, KitData.Sprite.PW_R_PEOPLE);
        c.text(inline ? 60 : 61, ty, Canvas.fit(n(w.week()) + " diese Woche · " + n(w.total()) + " gesamt", false, 166), BODY, false);
        if (!w.desc().isBlank()) c.text(48, ty + 10, Canvas.fit(Canvas.lit(w.desc()), false, 330), GRAY, false);
        // Anpinnen (eigene Warps: Bearbeiten), Teleport
        c.box(233, y + 18, 80, 18, KitData.Box.PW_PIN, Canvas.Z_BUTTON);
        if (own) {
            c.textCenter(273, y + 23, "Bearbeiten", NAV, true);
            c.action(233, y + 18, 80, 18, a.edit(w.name()), "Ort, Beschreibung, Kategorie, Farbe");
        } else {
            c.sprite(240, y + 24, KitData.Sprite.PW_STAR);
            c.text(254, y + 23, w.pinned() ? "Angepinnt" : "Anpinnen", w.pinned() ? GOLD : NAV, true);
            c.action(233, y + 18, 80, 18, a.pin(w.name()), w.pinned() ? "Nicht mehr anpinnen" : "Oben in der Liste halten");
        }
        c.box(317, y + 18, 65, 18, teleport(w.category()), Canvas.Z_BUTTON);
        c.text(317 + (65 - Canvas.width(Canvas.clean("Teleport"), true)) / 2, y + 23, "Teleport", ON, true);
        c.action(317, y + 18, 65, 18, a.teleport(w.name()), "Zu " + w.name() + " reisen");
        return h;
    }

    // =================================================================================================================
    // Meine Warps
    // =================================================================================================================

    public static Canvas mine(MineView v, Actions a) {
        long used = v.slots().stream().filter(s -> s.warp() != null).count();
        Canvas c = smallHead(v.coins(), "Meine Warps", used + " von " + v.slots().size() + " Plätzen belegt", a.list());
        int y = 43;
        for (Slot s : v.slots()) {
            if (s.warp() != null) {
                y += step(cardBox(c, y, s.warp(), a, true));
                continue;
            }
            boolean free = s.free();
            int h = free ? 57 : 47;
            c.box(0, y, W, h, free ? KitData.Box.PW_SLOT_FREE : KitData.Box.PW_SLOT_LOCKED);
            c.sprite(4, y + 3, free ? KitData.Sprite.PW_PORTAL : KitData.Sprite.PW_PORTAL_OFF);
            c.text(48, y + 9, free ? "Platz " + s.number() + " · frei" : "Platz " + s.number() + " · gesperrt", free ? GREEN : DIM, true);
            int pw = c.textRight(382, y + 9, n(s.price()) + " Coins", free ? GOLD : DIM, true);
            c.sprite(382 - pw - 13, y + 8, KitData.Sprite.PW_COIN_SMALL);
            if (free) {
                List<String> lines = Canvas.wrap("Kauft einen Warp genau hier, wo du stehst.", 180);
                int ty = y + 20;
                for (String l : lines) {
                    c.text(48, ty, l, BODY, false);
                    ty += 10;
                }
                c.text(48, ty, "Danach Name, Farbe und Icon einstellen.", GRAY, false);
                if (s.missing() > 0) {
                    c.box(265, y + 18, 117, 18, KitData.Box.PW_MISSING, Canvas.Z_BUTTON);
                    c.text(272, y + 23, Canvas.fit(I18n.context() ? n(s.missing()) + " missing" : "Dir fehlen " + n(s.missing()), true, 104), DIM, true);
                } else {
                    c.box(317, y + 18, 65, 18, KitData.Box.PW_TP_GREEN, Canvas.Z_BUTTON);
                    c.text(317 + (65 - Canvas.width(Canvas.clean("Kaufen"), true)) / 2, y + 23, "Kaufen", ON, true);
                    c.action(317, y + 18, 65, 18, a.buy(s.number()), "Warp hier kaufen");
                }
            } else {
                c.text(48, y + 20, "Erst Platz " + (s.number() - 1) + " belegen.", DIM, false);
            }
            y += step(h);
        }
        String foot = "Stell dich auf dein Grundstück, um hier einen Warp zu kaufen.";
        c.text((W - Canvas.width(Canvas.clean(foot), false)) / 2, y + 1, foot, GRAY, false);
        return c;
    }

    /** Kopf über den Feldern zum Kaufen und Bearbeiten. */
    public static Canvas formHead(long coins, String title, String sub) {
        Canvas c = new Canvas(W).heading("Spielerwarps", TEAL);
        c.box(0, 0, W, 38, KitData.Box.PW_HEAD_SMALL);
        c.sprite(8, 9, KitData.Sprite.PW_BACK);
        int room = coins(c, coins) - 6 - 40;
        c.text(40, 10, Canvas.fit(upper(title), true, room), TEAL, true);
        c.text(40, 21, Canvas.fit(sub, false, room), GRAY, false);
        return c;
    }

    private static String upper(String text) {
        // übersetzt (außer Spieler-Text), dann groß; als fertiger Text markiert, damit er nicht noch einmal übersetzt wird
        return Canvas.lit(Canvas.clean(text).toUpperCase(Locale.GERMAN));
    }
}
