package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Angel-Menüs als Canvas, Maße aus den Referenz-Clips (GUI-Pixel): Breite 390, Kopf 52 hoch, Kästen mit
 * Titel bei (10, 10) und Text ab x = 12, Kacheln 192 x 34 in zwei Spalten (Abstand 6, Raster 36), Knöpfe 22 hoch.
 * Nur Darstellung; Daten kommen als View-Records, Klicks über {@link Nav}.
 */
public final class AngelScreens {

    public static final int W = 390;
    /** Alle Angel-Menüs gleich hoch, dann steht der Kopf beim Blättern immer an derselben Stelle. */
    public static final int HEIGHT = 470;

    static final TextColor CYAN = TextColor.color(0x62CDFF), GRAY = TextColor.color(0xA0A0A8),
            LIGHT = TextColor.color(0xCAD2D4), WHITE = TextColor.color(0xF0F0F0), AQUA = TextColor.color(0x6FE3D2),
            GREEN = TextColor.color(0x6CE86E), LCYAN = TextColor.color(0xA3E3F3), ON_TEAL = TextColor.color(0x1C1400),
            ON_GREEN = TextColor.color(0x1C1400), ON_PURPLE = TextColor.color(0xF6ECFF), GOLD_T = TextColor.color(0xF8D35A),
            RED = TextColor.color(0xFF6A6A), TAB = TextColor.color(0xE0E0E0);

    /** Farbthema eines Kastens: Rahmen und Akzentfarbe (Titel, Pfeil). */
    public enum Theme {
        NAVY(KitData.Box.NAVY, 0x62CDFF), GREEN(KitData.Box.GREEN, 0x6CE086), GOLD(KitData.Box.GOLD, 0xF8D35A),
        PURPLE(KitData.Box.PURPLE, 0xCA8AFA), TEAL(KitData.Box.TEAL, 0x6FE3D2);

        final KitData.Box box;
        final TextColor accent;

        Theme(KitData.Box box, int accent) {
            this.box = box;
            this.accent = TextColor.color(accent);
        }
    }

    /** Klick-Ziele der Menüs (AngelMenus). */
    public interface Nav {
        Consumer<Player> main();

        Consumer<Player> book(Water w);

        Consumer<Player> detail(FishSpecies s);

        Consumer<Player> rod();

        Consumer<Player> baits();

        Consumer<Player> dealer();

        Consumer<Player> orders();

        Consumer<Player> top(String tab);

        Consumer<Player> help();

        Consumer<Player> toZone();

        Consumer<Player> buyUpgrade(Upgrade u);

        Consumer<Player> buyAutoSell();

        Consumer<Player> tokenShop();

        Consumer<Player> bait(Bait b, String action);

        Consumer<Player> sell(AngelService.SellMode mode);
    }

    private AngelScreens() {
    }

    // =================================================================================================================
    // Bausteine
    // =================================================================================================================

    /** Kopf: Zurück-Knopf (optional), großes Icon, Titel, Untertitel, rechts ein Wert. */
    static void head(Canvas c, Theme t, Consumer<Player> back, KitData.Icon icon, String title, String sub,
                     List<Span> right) {
        c.box(0, 0, W, 52, t.box);
        int x = 10;
        if (back != null) {
            c.back(11, 9);
            c.action(11, 9, 20, 20, back, "Zurück");
            x = 40;
        }
        c.icon(x, 10, icon, true);
        int tx = back != null ? 78 : 48;
        c.text(tx, 12, title, t.accent, true);
        if (sub != null) c.text(tx, 25, Canvas.fit(sub, false, W - tx - 12 - (right == null ? 0 : Canvas.width(right) + 8)), GRAY, false);
        if (right != null) c.textRight(380, 15, right);
    }

    /** Kachel 192 x 34: Icon, Titel, Untertitel, Pfeil; ganz klickbar mit Tooltip. */
    static void tile(Canvas c, int x, int y, Theme t, KitData.Icon icon, String title, TextColor titleColor,
                     List<Span> sub, boolean arrow, Consumer<Player> click, String hover) {
        c.box(x, y, 192, 34, t.box);
        if (icon != null) c.icon(x + 9, y + 9, icon, false);
        int tx = icon != null ? x + 34 : x + 10;
        int max = (arrow ? x + 174 : x + 184) - tx;
        c.text(tx, sub == null ? y + 13 : y + 6, Canvas.fit(title, true, max), titleColor == null ? t.accent : titleColor, true);
        if (sub != null) c.text(tx, y + 18, sub);
        if (arrow) c.text(x + 178, y + 13, "›", t.accent, false);
        if (click != null) c.action(x, y, 192, 34, click, hover);
    }

    static List<Span> spans(Object... parts) {
        List<Span> out = new ArrayList<>();
        for (int i = 0; i + 1 < parts.length; i += 2) out.add(Span.of((String) parts[i], (TextColor) parts[i + 1]));
        return out;
    }

    /** Reiter: aktiver Reiter türkis, andere dunkel. Gibt die nächste y-Position (unter den Reitern) zurück. */
    static int tabs(Canvas c, int y, List<String> labels, int active, List<Consumer<Player>> clicks) {
        int x = 0;
        for (int i = 0; i < labels.size(); i++) {
            boolean on = i == active;
            int w = Canvas.width(labels.get(i), on) + 16;
            if (x + w > W) {
                x = 0;
                y += 27;
            }
            c.button(x, y, w, on ? KitData.Button.TEAL : KitData.Button.DARK, labels.get(i), on ? ON_TEAL : TAB, on);
            if (!on) c.action(x, y, w, 22, clicks.get(i), labels.get(i));
            x += w + 4;
        }
        return y + 22;
    }

    static int textButton(Canvas c, int x, int y, KitData.Button style, String label, TextColor color, boolean bold,
                          Consumer<Player> click, String hover) {
        int w = Canvas.width(label, bold) + 16;
        c.button(x, y, w, style, label, color, bold);
        if (click != null) c.action(x, y, w, 22, click, hover);
        return w;
    }

    static int textButtonRight(Canvas c, int right, int y, KitData.Button style, String label, TextColor color,
                               boolean bold, Consumer<Player> click, String hover) {
        int w = Canvas.width(label, bold) + 16;
        return textButton(c, right - w, y, style, label, color, bold, click, hover);
    }

    static KitData.Icon fishIcon(FishSpecies s, boolean known) {
        return KitData.Icon.valueOf((known ? "FISH_" : "FISHX_") + s.id().toUpperCase(java.util.Locale.ROOT));
    }

    // =================================================================================================================
    // Hauptmenü
    // =================================================================================================================

    public record MainView(long points, int level, String rank, long nextLevelPoints, double levelFrac, int catches,
                           int weekCatches, int species, long earned, int bestStreak, int treasures, int jobBonus,
                           int jobLevel, List<FishSpecies> fishOfDay, boolean golden, long goldenSeconds,
                           double luck, double faster, String activeBait, KitData.Icon activeBaitIcon,
                           int activeBaitCount, long fishValue,
                           int ordersDone, boolean hasZone) {}

    public static Canvas main(MainView v, Nav nav) {
        Canvas c = new Canvas(W).heading("Angeln", net.kyori.adventure.text.format.TextColor.color(0x55FCFC));
        head(c, Theme.NAVY, null, KitData.Icon.ROD, "ANGELN", v.rank + " · Level " + v.level,
                List.of(Span.of("Angel-Punkte ", GRAY), Span.bold(AngelText.num(v.points), AQUA)));

        // Dein Profil
        c.box(0, 54, W, 88, KitData.Box.NAVY);
        c.text(10, 64, "Dein Profil", CYAN, true);
        c.textRight(380, 64, v.rank, GRAY, false);
        int lw = c.text(12, 78, "Level " + v.level, WHITE, true);
        int bx = Math.max(70, 12 + lw + 14);
        c.bar(bx, 78, 40, v.levelFrac, KitData.Fill.GOLD);
        c.text(bx + 45, 78, v.level >= 100 ? "Höchstes Level erreicht" : "noch " + AngelText.num(v.nextLevelPoints)
                + " Punkte bis Level " + (v.level + 1), LIGHT, false);
        row(c, 90, "Fänge", AngelText.num(v.catches), "Diese Woche", AngelText.num(v.weekCatches), WHITE);
        row(c, 101, "Fischbuch", v.species + "/48", "Verdient", AngelText.money(v.earned), WHITE);
        row(c, 112, "Beste Serie", "x" + v.bestStreak, "Schätze", AngelText.num(v.treasures), WHITE);
        c.text(12, 123, v.jobBonus >= 0
                ? "Angler-Job aktiv (Level " + v.jobLevel + "): +" + v.jobBonus + " % Glück und Preis"
                : "Kein Angler-Job: mit dem Angler-Job mehr Glück und Preis", LIGHT, false);

        // Heute
        c.box(0, 153, W, 52, KitData.Box.NAVY);
        c.text(10, 163, "Heute", CYAN, true);
        c.textRight(380, 163, v.golden ? "Goldene Stunde läuft noch " + AngelText.duration(v.goldenSeconds)
                : "Goldene Stunde in " + AngelText.duration(v.goldenSeconds), GRAY, false);
        int fx = 11;
        for (FishSpecies s : v.fishOfDay) {
            c.icon(fx, 172, fishIcon(s, true), false);
            c.text(fx + 21, 176, s.name(), s.rarity().color(), false);
            fx += 139;
        }
        c.textRight(380, 176, "+50 % Preis", GREEN, false);
        c.action(0, 153, W, 52, nav.dealer(), "Fische des Tages: +50 % beim Händler");

        // Kacheln
        int y = 216;
        tile(c, 0, y, Theme.NAVY, KitData.Icon.FISH_BOOK, "Fischbuch", null,
                spans(v.species + "/48 Arten gefangen", GRAY), true, nav.book(Water.FLUSS), "Alle Arten, Rekorde und wo sie beißen");
        tile(c, 198, y, Theme.NAVY, KitData.Icon.ROD, "Rute & Upgrades", null,
                spans("Glück +" + AngelText.dec(Math.round(v.luck)) + " % · Tempo +" + AngelText.dec(Math.round(v.faster)) + " %", GRAY),
                true, nav.rod(), "Verbessere deine Angel dauerhaft");
        tile(c, 0, y + 36, Theme.NAVY, v.activeBaitIcon == null ? KitData.Icon.BUNDLE : v.activeBaitIcon, "Köder", null,
                spans(v.activeBait == null ? "Kein Köder aktiv" : v.activeBait + " aktiv (" + v.activeBaitCount + ")", GRAY),
                true, nav.baits(), "Köder kaufen, basteln und wechseln");
        tile(c, 198, y + 36, Theme.NAVY, KitData.Icon.EMERALD, "Fischhändler", null,
                spans("Deine Fische: " + AngelText.money(v.fishValue), GRAY), true, nav.dealer(), "Fische zum Tagespreis verkaufen");
        tile(c, 0, y + 72, Theme.NAVY, KitData.Icon.BOOK_PLAIN, "Tagesaufträge", null,
                spans(v.ordersDone + "/3 erledigt", GRAY), true, nav.orders(), "Drei neue Aufträge jeden Tag");
        tile(c, 198, y + 72, Theme.NAVY, KitData.Icon.GOLD_HELMET, "Bestenliste", null,
                spans("Woche, Ränge und Rekorde", GRAY), true, nav.top("woche"), "Die besten Angler");

        // Knöpfe
        if (v.hasZone) {
            textButton(c, 0, 330, KitData.Button.TEAL, "Zur Angel-Zone", ON_TEAL, true, nav.toZone(),
                    "Dort beißt es besser: +10 % Glück, Mythen-Fische und Schätze");
        }
        textButtonRight(c, W, 330, KitData.Button.DARK, "So funktioniert's", TAB, false, nav.help(), "Kurz erklärt");
        return c;
    }

    private static void row(Canvas c, int y, String l1, String v1, String l2, String v2, TextColor v2Color) {
        c.text(12, y, l1, GRAY, false);
        c.textRight(188, y, v1, WHITE, false);
        c.text(202, y, l2, GRAY, false);
        c.textRight(380, y, v2, v2Color, false);
    }

    // =================================================================================================================
    // Fischbuch
    // =================================================================================================================

    public record BookEntry(FishSpecies species, int caught, double best) {}

    public record BookView(Water water, List<BookEntry> entries, int pageCaught, boolean pageDone, int total,
                           long pageCoins, long pageTokens) {}

    public static Canvas book(BookView v, Nav nav) {
        Canvas c = new Canvas(W).heading("Angeln", net.kyori.adventure.text.format.TextColor.color(0x55FCFC));
        String sub = v.pageCaught + "/8 gefangen · " + (v.pageDone ? "Seite komplett ✓"
                : "volle Seite: " + AngelText.money(v.pageCoins) + " + " + v.pageTokens + " Tokens");
        head(c, Theme.NAVY, nav.main(), KitData.Icon.FISH_BOOK, v.water.upper(), sub,
                List.of(Span.bold(v.total + "/48", AQUA)));
        List<String> labels = new ArrayList<>();
        List<Consumer<Player>> clicks = new ArrayList<>();
        for (Water w : Water.values()) {
            labels.add(w.label);
            clicks.add(nav.book(w));
        }
        int y = tabs(c, 54, labels, v.water.ordinal(), clicks) + 14;
        for (int i = 0; i < v.entries.size(); i++) {
            BookEntry e = v.entries.get(i);
            FishSpecies s = e.species;
            boolean known = e.caught > 0;
            int tx = (i % 2) * 198, ty = y + (i / 2) * 36;
            String subText = known ? s.rarity().label + " · " + e.caught + "x" + (e.best > 0 ? " · bis " + AngelText.kg(e.best) : "")
                    : s.rarity().label + " · noch offen";
            tile(c, tx, ty, Theme.NAVY, fishIcon(s, known), known ? s.name() : "???", s.rarity().color(),
                    spans(subText, GRAY), true, nav.detail(s), "Mehr zu diesem Fisch");
        }
        return c;
    }

    // =================================================================================================================
    // Fisch-Details
    // =================================================================================================================

    public record DetailView(FishSpecies species, int caught, double best, String recordName, double recordKg,
                             long minPrice, long maxPrice, int bonus, boolean inZoneOnly) {}

    public static Canvas detail(DetailView v, Nav nav) {
        Canvas c = new Canvas(W).heading("Angeln", net.kyori.adventure.text.format.TextColor.color(0x55FCFC));
        FishSpecies s = v.species;
        boolean known = v.caught > 0;
        c.box(0, 0, W, 52, KitData.Box.NAVY);
        c.back(11, 9);
        c.action(11, 9, 20, 20, nav.book(s.water()), "Zurück zum Fischbuch");
        c.icon(40, 10, fishIcon(s, known), true);
        c.text(78, 12, known ? s.name() : "???", s.rarity().color(), true);
        c.text(78, 25, s.rarity().label + " · " + s.water().label, GRAY, false);
        c.textRight(380, 15, List.of(Span.of(known ? v.caught + "x gefangen" : "noch nicht gefangen", known ? AQUA : GRAY)));

        c.box(0, 54, W, 74, KitData.Box.NAVY);
        c.text(10, 64, "Wo sie beißen", CYAN, true);
        c.text(12, 78, "Gewässer", GRAY, false);
        c.textRight(380, 78, v.inZoneOnly ? "Nur in der Angel-Zone" : s.water().label, WHITE, false);
        c.text(12, 89, "Tageszeit", GRAY, false);
        c.textRight(380, 89, s.night() ? "Beißt nachts doppelt so oft" : "Tag und Nacht", WHITE, false);
        c.text(12, 100, "Gewicht", GRAY, false);
        c.textRight(380, 100, AngelText.kg(s.minKg()) + " bis " + AngelText.kg(s.maxKg()), WHITE, false);
        c.text(12, 111, "Preis beim Händler", GRAY, false);
        c.textRight(380, 111, List.of(Span.of(AngelText.money(v.minPrice) + " bis " + AngelText.money(v.maxPrice), AQUA),
                Span.of(v.bonus > 0 ? "  +" + v.bonus + " %" : "", GREEN)));

        c.box(0, 139, W, 52, KitData.Box.NAVY);
        c.text(10, 149, "Rekorde", CYAN, true);
        c.text(12, 163, "Dein schwerster", GRAY, false);
        c.textRight(380, 163, v.best > 0 ? AngelText.kg(v.best) : "-", WHITE, false);
        c.text(12, 174, "Server-Rekord", GRAY, false);
        c.textRight(380, 174, v.recordName == null ? "noch keiner" : v.recordName + " · " + AngelText.kg(v.recordKg),
                GOLD_T, false);
        return c;
    }

    // =================================================================================================================
    // Rute & Upgrades
    // =================================================================================================================

    public record RodView(double luck, double faster, double treasure, int[] levels, boolean autoSell,
                          long autoSellTokens, String armed, long coins, long tokens) {}

    public static Canvas rod(RodView v, Nav nav) {
        Canvas c = new Canvas(W).heading("Angeln", net.kyori.adventure.text.format.TextColor.color(0x55FCFC));
        head(c, Theme.NAVY, nav.main(), KitData.Icon.ROD, "Rute & Upgrades", "Upgrades gelten für jede Angel", null);
        c.box(0, 54, W, 61, KitData.Box.NAVY);
        c.text(10, 64, "Deine Angel", CYAN, true);
        c.text(12, 78, List.of(Span.of("Glück ", GRAY), Span.bold("+" + AngelText.dec(Math.round(v.luck)) + " %", WHITE)));
        c.text(152, 78, List.of(Span.of("Wartezeit ", GRAY), Span.bold("-" + AngelText.dec(Math.round(v.faster)) + " %", WHITE)));
        c.textRight(380, 78, List.of(Span.of("Schatz ", GRAY), Span.bold(AngelText.dec(v.treasure) + " %", WHITE)));
        c.text(12, 89, "In der Angel-Zone zusätzlich +10 % Glück und -10 % Wartezeit", LIGHT, false);

        int y = 126;
        for (Upgrade u : Upgrade.values()) {
            int lv = v.levels[u.ordinal()];
            c.box(0, y, W, 52, KitData.Box.NAVY);
            c.icon(12, y + 18, u.icon, false);
            int tw = c.text(34, y + 8, u.label, CYAN, true);
            c.text(34 + tw + 4, y + 8, "Stufe " + lv + "/" + u.max, GRAY, false);
            c.text(34, y + 19, List.of(Span.of("Jetzt: ", GRAY), Span.of(u.effect(lv), GREEN)));
            if (lv < u.max) {
                c.text(34, y + 30, List.of(Span.of("Nächste Stufe: ", GRAY), Span.of(u.effect(lv + 1), GREEN)));
                boolean armed = ("up:" + u.name()).equals(v.armed);
                String label = armed ? "Nochmal klicken!" : "Kaufen · " + AngelText.money(u.price(lv));
                textButtonRight(c, 380, y + 15, KitData.Button.GREEN, label, ON_GREEN, true, nav.buyUpgrade(u),
                        "Zweimal klicken zum Kaufen");
            } else {
                c.text(34, y + 30, "Höchste Stufe erreicht", GOLD_T, false);
            }
            y += 63;
        }
        // Auto-Verkauf
        c.box(0, y, W, 52, KitData.Box.NAVY);
        c.icon(12, y + 18, KitData.Icon.HOPPER, false);
        c.text(34, y + 8, "Auto-Verkauf", CYAN, true);
        c.text(34, y + 19, "Fische bis Ungewöhnlich gehen sofort", LIGHT, false);
        c.text(34, y + 30, "an den Händler", LIGHT, false);
        if (v.autoSell) {
            c.textRight(380, y + 19, "Aktiv ✓", GREEN, true);
        } else {
            boolean armed = "autosell".equals(v.armed);
            textButtonRight(c, 380, y + 15, KitData.Button.PURPLE, armed ? "Nochmal klicken!" : "Kaufen · " + v.autoSellTokens + " Tokens",
                    ON_PURPLE, true, nav.buyAutoSell(), "Zweimal klicken zum Kaufen · Du hast " + v.tokens + " Tokens");
        }
        y += 60;
        textButtonRight(c, W, y, KitData.Button.PURPLE_LINE, "Doppelfang im Token-Shop", TextColor.color(0xD6A4FF), false,
                nav.tokenShop(), "Chance auf zwei Fische auf einmal");
        return c;
    }

    // =================================================================================================================
    // Köder
    // =================================================================================================================

    public record BaitView(int[] counts, Bait active, boolean[] canCraft, String[] missing, String armed) {}

    public static Canvas baits(BaitView v, Nav nav) {
        Canvas c = new Canvas(W).heading("Angeln", net.kyori.adventure.text.format.TextColor.color(0x55FCFC));
        head(c, Theme.NAVY, nav.main(), KitData.Icon.BUNDLE, "Köder", "Ein aktiver Köder wird bei jedem Fang verbraucht", null);
        int y = 54;
        for (Bait b : Bait.values()) {
            boolean on = v.active == b;
            int n = v.counts[b.ordinal()];
            c.box(0, y, W, 61, on ? KitData.Box.TEAL : KitData.Box.NAVY);
            c.icon(11, y + 10, b.icon, false);
            int tw = c.text(34, y + 8, b.label, CYAN, true);
            if (on) c.text(34 + tw + 4, y + 8, "aktiv", AQUA, false);
            c.text(34, y + 19, effect(b.effect));
            c.textRight(380, y + 8, "Im Beutel: " + n, GRAY, false);
            int bx = 35;
            if (on) {
                bx += textButton(c, bx, y + 33, KitData.Button.TEAL_LINE, "Abnehmen", TextColor.color(0x7FE6DA), false,
                        nav.bait(b, "use"), "Köder abnehmen") + 4;
            } else if (n > 0) {
                bx += textButton(c, bx, y + 33, KitData.Button.TEAL, "Benutzen", ON_TEAL, true, nav.bait(b, "use"),
                        "Diesen Köder an die Angel") + 4;
            }
            boolean armed = ("buy:" + b.name()).equals(v.armed);
            bx += textButton(c, bx, y + 33, KitData.Button.GOLD_LINE, armed ? "Nochmal klicken!" : "10x kaufen · " + AngelText.money(b.price10),
                    TextColor.color(0xF6CB4C), false, nav.bait(b, "buy"), "Zweimal klicken zum Kaufen") + 4;
            String craftHover = v.missing[b.ordinal()] == null ? "Basteln aus: " + recipe(b) : "Dir fehlt: " + v.missing[b.ordinal()];
            textButton(c, bx, y + 33, KitData.Button.BROWN_LINE, b.craftAmount + "x basteln", TextColor.color(0xE8A060),
                    false, nav.bait(b, "craft"), craftHover);
            y += 72;
        }
        return c;
    }

    static String recipe(Bait b) {
        List<String> parts = new ArrayList<>();
        b.recipe.forEach((m, n) -> parts.add(n + "x " + AngelService.materialName(m)));
        return String.join(", ", parts);
    }

    /** Effekt-Text: Zahlen mit Vorzeichen grün, Rest grau. */
    static List<Span> effect(String text) {
        List<Span> out = new ArrayList<>();
        for (String part : text.split("(?<=, )")) {
            int sp = part.indexOf(" % ");
            if ((part.startsWith("+") || part.startsWith("-")) && sp > 0) {
                out.add(Span.of(part.substring(0, sp + 2), GREEN));
                out.add(Span.of(part.substring(sp + 2), GRAY));
            } else {
                out.add(Span.of(part, GRAY));
            }
        }
        return out;
    }

    // =================================================================================================================
    // Fischhändler
    // =================================================================================================================

    public record DealerView(long value, long budgetLeft, long valueSmall, long valueRare, long valueDay,
                             List<FishSpecies> fishOfDay, int jobBonus, boolean golden, String lastSale) {}

    public static Canvas dealer(DealerView v, Nav nav) {
        Canvas c = new Canvas(W).heading("Angeln", net.kyori.adventure.text.format.TextColor.color(0x55FCFC));
        head(c, Theme.GREEN, nav.main(), KitData.Icon.EMERALD, "Fischhändler",
                v.budgetLeft > 0 ? "Diese Stunde noch " + AngelText.money(v.budgetLeft) + " zum vollen Preis"
                        : "Stundenbudget voll: Fische bringen gerade weniger",
                List.of(Span.bold(AngelText.money(v.value), TextColor.color(0x6CE086))));
        tile(c, 0, 54, Theme.GREEN, KitData.Icon.EMERALD, "Alles verkaufen", null, spans("Alle Fische im Inventar", GRAY),
                true, nav.sell(AngelService.SellMode.ALLES), "Verkauft alle Fische · " + AngelText.money(v.value));
        tile(c, 198, 54, Theme.NAVY, fishIcon(FishSpecies.byId("hering"), true), "Nur die kleinen", null, null,
                true, nav.sell(AngelService.SellMode.KLEINE), "Die Seltenen behältst du · " + AngelText.money(v.valueSmall));
        tile(c, 0, 90, Theme.PURPLE, fishIcon(FishSpecies.byId("hecht"), true), "Nur die seltenen", null, spans("Ab Selten aufwärts", GRAY),
                true, nav.sell(AngelService.SellMode.SELTENE), "Verkauft nur die wertvollen Fische · " + AngelText.money(v.valueRare));
        List<Span> day = new ArrayList<>();
        for (int i = 0; i < v.fishOfDay.size(); i++) {
            if (i > 0) day.add(Span.of(", ", GRAY));
            day.add(Span.of(v.fishOfDay.get(i).name(), v.fishOfDay.get(i).rarity().color()));
        }
        tile(c, 198, 90, Theme.GOLD, null, "Fische des Tages +50 %", null, day, false,
                nav.sell(AngelService.SellMode.TAGESFISCHE), "Verkauft nur die Fische des Tages · " + AngelText.money(v.valueDay));

        c.box(0, 135, W, 141, KitData.Box.NAVY);
        c.text(10, 145, v.golden ? "Preise heute · Goldene Stunde +50 %" : "Preise heute", CYAN, true);
        c.textRight(380, 145, "Angler-Job: +" + Math.max(0, v.jobBonus) + " %", GRAY, false);
        int y = 154;
        for (Rarity r : Rarity.values()) {
            FishSpecies sample = null;
            for (FishSpecies s : FishSpecies.ALL) {
                if (s.rarity() == r) {
                    sample = s;
                    break;
                }
            }
            if (sample != null) c.icon(12, y, fishIcon(sample, true), false);
            c.text(34, y + 4, r.label, r.color(), true);
            long f = 100 + Math.max(0, v.jobBonus) + (v.golden ? 50 : 0);
            c.textRight(380, y + 4, AngelText.money(Math.round(r.minPrice * f / 100.0)) + " bis "
                    + AngelText.money(Math.round(r.maxPrice * f / 100.0)), LIGHT, false);
            y += 18;
        }
        if (v.lastSale != null) c.textCenter(W / 2, 284, v.lastSale, GREEN, false);
        return c;
    }

    // =================================================================================================================
    // Tagesaufträge
    // =================================================================================================================

    public record OrderView(List<AngelProfile.Order> orders, boolean bonusDone, long secondsLeft) {}

    public static Canvas orders(OrderView v, Nav nav) {
        Canvas c = new Canvas(W).heading("Angeln", net.kyori.adventure.text.format.TextColor.color(0x55FCFC));
        head(c, Theme.GOLD, nav.main(), KitData.Icon.BOOK_PLAIN, "Tagesaufträge",
                "Neue Aufträge in " + AngelText.duration(v.secondsLeft), null);
        int y = 54;
        int done = 0;
        for (AngelProfile.Order o : v.orders) {
            if (o.finished) done++;
            c.box(0, y, W, 43, KitData.Box.NAVY);
            c.text(12, y + 8, Canvas.fit(AngelService.orderTitle(o), true, 330), CYAN, true);
            c.textRight(380, y + 8, o.finished ? "Erledigt ✓" : "Läuft", o.finished ? GREEN : GRAY, false);
            c.bar(12, y + 20, 40, o.goal == 0 ? 0 : Math.min(1.0, (double) o.done / o.goal),
                    o.finished ? KitData.Fill.GREEN : KitData.Fill.GOLD);
            c.text(58, y + 20, o.type.equals("sell") ? AngelText.money(o.done) + "/" + AngelText.money(o.goal)
                    : o.done + "/" + o.goal, LCYAN, false);
            c.textRight(380, y + 20, AngelText.money(o.money) + " · " + o.points + " Punkte", LIGHT, false);
            y += 54;
        }
        c.box(0, y, W, 43, KitData.Box.PURPLE);
        c.icon(11, y + 13, KitData.Icon.CHEST, false);
        c.text(34, y + 8, "Bonus für alle drei", TextColor.color(0xCA8AFA), true);
        c.text(34, y + 20, "1 Token und 3 Köder", GRAY, false);
        c.textRight(380, y + 8, v.bonusDone ? "Erhalten ✓" : done + "/3", v.bonusDone ? GREEN : GRAY, false);
        return c;
    }

    // =================================================================================================================
    // Bestenliste
    // =================================================================================================================

    public static final List<String> TOP_TABS = List.of("woche", "raenge", "faenge", "rekorde");

    public record TopView(String tab, List<AngelService.TopEntry> entries, int ownPlace, String ownValue) {}

    public static Canvas top(TopView v, Nav nav) {
        Canvas c = new Canvas(W).heading("Angeln", net.kyori.adventure.text.format.TextColor.color(0x55FCFC));
        head(c, Theme.GOLD, nav.main(), KitData.Icon.GOLD_HELMET, "Bestenliste", "Aktualisiert alle zwei Minuten", null);
        List<Consumer<Player>> clicks = new ArrayList<>();
        for (String t : TOP_TABS) clicks.add(nav.top(t));
        int active = Math.max(0, TOP_TABS.indexOf(v.tab));
        tabs(c, 54, List.of("Woche", "Ränge", "Fänge", "Rekorde"), active, clicks);
        String title = switch (v.tab) {
            case "raenge" -> "Die höchsten Angel-Level";
            case "faenge" -> "Die meisten Fänge insgesamt";
            case "rekorde" -> "Die schwersten Fänge (seltene zuerst)";
            default -> "Die meisten Fänge diese Woche";
        };
        c.box(0, 90, W, 140, KitData.Box.GOLD);
        c.text(10, 100, title, GOLD_T, true);
        int y = 114;
        if (v.entries.isEmpty()) c.text(12, y, "Noch keine Einträge.", GRAY, false);
        for (int i = 0; i < v.entries.size() && i < 10; i++) {
            AngelService.TopEntry e = v.entries.get(i);
            TextColor rc = i == 0 ? TextColor.color(0xF8D35A) : i == 1 ? TextColor.color(0xF0F0F0)
                    : i == 2 ? TextColor.color(0xF0B080) : GRAY;
            c.text(12, y, (i + 1) + ".", rc, i < 3);
            c.text(34, y, e.name(), WHITE, false);
            c.textRight(380, y, Canvas.fit(e.value(), false, 220), LCYAN, false);
            y += 11;
        }
        if (v.ownPlace > 10) {
            c.text(10, 236, "Du: Platz " + v.ownPlace, GRAY, false);
            c.textRight(380, 236, v.ownValue, LCYAN, false);
        }
        return c;
    }

    // =================================================================================================================
    // Hilfe
    // =================================================================================================================

    public static Canvas help(Nav nav) {
        Canvas c = new Canvas(W).heading("Angeln", net.kyori.adventure.text.format.TextColor.color(0x55FCFC));
        head(c, Theme.NAVY, nav.main(), KitData.Icon.BOOK, "So funktioniert's", "Angeln in Survival kurz erklärt", null);
        String[][] parts = {
                {"Angeln", "Wirf die Angel ins Wasser. Wenn es zieht, holst du ein."},
                {"", "Jeder Fang bringt Angel-Punkte für dein Level."},
                {"Gewässer", "Fluss, Ozean, warm, kalt, Sumpf: je Biom andere Fische."},
                {"", "Mythen-Fische und Schätze gibt es nur in der Angel-Zone."},
                {"Sterne", "Schwerere Fische haben mehr Sterne und bringen mehr Geld."},
                {"Glück", "Glück macht seltene Fische häufiger: Level, Glückshaken,"},
                {"", "Köder, Angel-Zone und der Angler-Job erhöhen es."},
                {"Händler", "Verkaufe beim Fischhändler. Pro Stunde gibt es ein Budget"},
                {"", "zum vollen Preis, danach weniger. Fische des Tages +50 %."},
                {"Goldene", "Jeden Tag eine Stunde mit +50 % auf alle Fische."},
                {"Aufträge", "Drei Tagesaufträge, für alle drei gibt es einen Bonus."},
                {"Fischbuch", "Fang alle 8 Arten einer Seite für eine Belohnung."},
        };
        c.box(0, 54, W, 12 + parts.length * 11 + 10, KitData.Box.NAVY);
        int y = 64;
        for (String[] p : parts) {
            if (!p[0].isEmpty()) c.text(12, y, p[0], CYAN, true);
            c.text(80, y, p[1], LIGHT, false);
            y += 11;
        }
        return c;
    }

    /** Für Tooltips: Komponente aus Zeilen. */
    static Component lines(String... lines) {
        Component out = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out = out.append(Component.newline());
            out = out.append(Component.text(lines[i], i == 0 ? NamedTextColor.WHITE : NamedTextColor.GRAY));
        }
        return out;
    }
}
