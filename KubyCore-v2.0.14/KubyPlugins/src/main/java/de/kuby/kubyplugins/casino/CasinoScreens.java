package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Casino-Dialoge im Stil der Referenz-Clips: roter Kopf mit Goldrand und großer Goldschrift, grüner Filz, dunkle
 * Kästen mit Goldrand, kleine Knöpfe. Nur Layout; die Spiele liefern die Daten (Views) und die Klick-Aktionen.
 */
public final class CasinoScreens {

    public static final int W = 390;

    public static final TextColor GRAY = TextColor.color(0xACA48E);
    public static final TextColor LIGHT = TextColor.color(0xE8E0D8);
    public static final TextColor GOLD = TextColor.color(0xF4D46A);
    public static final TextColor GOLD_SOFT = TextColor.color(0xF2DA90);
    public static final TextColor ON_GOLD = TextColor.color(0x2C1C06);
    public static final TextColor ON_GREEN = TextColor.color(0x06240E);
    public static final TextColor BTN_GRAY = TextColor.color(0x8E8A84);
    public static final TextColor WHITE = NamedTextColor.WHITE;
    public static final TextColor RED = TextColor.color(0xFF5A5A);
    public static final TextColor GREEN = TextColor.color(0x6BF07A);

    private static final DecimalFormatSymbols DE = DecimalFormatSymbols.getInstance(Locale.GERMANY);

    private CasinoScreens() {
    }

    public static String num(long v) {
        return new DecimalFormat("#,##0", DE).format(v);
    }

    private static Component tip(String... lines) {
        var b = Component.text();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) b.append(Component.newline());
            b.append(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(lines[i]));
        }
        return b.build();
    }

    /** Kopf: Icon 32 px, großer Titel, graue Unterzeile. */
    private static void head(Canvas c, KitData.Icon icon, String title, String sub) {
        c.box(0, 0, W, 51, KitData.Box.CASINO);
        c.icon(10, 10, icon, true);
        c.title(46, 11, title);
        c.text(47, 33, sub, GRAY);
    }

    // =================================================================================================================
    // Roulette
    // =================================================================================================================

    /** Ein Chip in der Auswahl: Wert (0 = eigener Betrag, -1 = alles), Icon, Beschriftung. */
    public record ChipChoice(long value, String chip, String label) {}

    public static final List<ChipChoice> CHIPS = List.of(
            new ChipChoice(10, "white", "10"), new ChipChoice(50, "red", "50"), new ChipChoice(100, "blue", "100"),
            new ChipChoice(500, "green", "500"), new ChipChoice(1000, "black", "1.000"),
            new ChipChoice(0, "purple", "EIGEN"), new ChipChoice(-1, "gold", "ALLES"));

    /** Ein Einsatz in der Liste "Deine Einsätze". */
    public record BetLine(String label, TextColor color, long amount) {}

    public record RouletteView(long chips, boolean expert, int history, long chipValue, int chipIndex,
                               String status, List<BetLine> bets, long roundTotal, boolean canRepeat) {}

    public interface RouletteActions {
        Consumer<Player> mode(boolean expert);

        Consumer<Player> history();

        Consumer<Player> color(String color);

        Consumer<Player> chip(int index);

        Consumer<Player> custom();

        Consumer<Player> repeat();

        Consumer<Player> clear();
    }

    private static KitData.SmallIcon small(String name) {
        return KitData.SmallIcon.valueOf(name.toUpperCase(Locale.ROOT));
    }

    public static Canvas roulette(RouletteView v, RouletteActions a) {
        Canvas c = new Canvas(W);
        head(c, KitData.Icon.ROULETTE_WHEEL, "ROULETTE", v.status());
        // rechts: Deine Chips
        c.smallIcon(299, 21, KitData.SmallIcon.S_CHIP_RED);
        c.text(315, 16, "Deine", GRAY);
        c.text(315, 26, "Chips", GRAY);
        c.textRight(378, 21, num(v.chips()), GOLD, true);

        // Reiter Einfach / Experte, Verlauf
        int ty = 54;
        c.smallButton(0, ty, 75, v.expert() ? KitData.Small.GOLD : KitData.Small.GOLD_ON);
        c.smallIcon(7, ty + 3, KitData.SmallIcon.S_CHIP_RED);
        c.text(21, ty + 4, Canvas.caps("Einfach"), v.expert() ? GOLD_SOFT : ON_GOLD, true);
        c.action(0, ty, 75, 16, a.mode(false), "Rot, Schwarz oder Grün");
        c.smallButton(79, ty, 77, v.expert() ? KitData.Small.GOLD_ON : KitData.Small.GOLD);
        c.smallIcon(86, ty + 3, KitData.SmallIcon.S_STAR);
        c.text(99, ty + 4, Canvas.caps("Experte"), v.expert() ? ON_GOLD : GOLD_SOFT, true);
        c.action(79, ty, 77, 16, a.mode(true), "Zahlen, Dutzende und mehr");
        c.text(318, ty + 4, Canvas.caps("Verlauf"), GRAY, false);
        c.smallButton(366, ty, 24, KitData.Small.GOLD_GRAY, String.valueOf(v.history()), WHITE, true);
        c.action(316, ty, 74, 16, a.history(), "Die letzten Ergebnisse");

        // Farbe setzen
        int fy = 72;
        c.box(0, fy, W, 60, KitData.Box.FELT);
        c.text(12, fy + 7, Canvas.caps("Farbe setzen"), WHITE, true);
        c.textRight(377, fy + 7, List.of(Canvas.Span.of("Rot/Schwarz ", GRAY), Canvas.Span.of("1:1", WHITE),
                Canvas.Span.of(" · Grün ", GRAY), Canvas.Span.of("35:1", WHITE)));
        String sets = v.chipValue() > 0 ? "Setzt <yellow>" + num(v.chipValue()) + " Chips" : "Erst einen Chip wählen";
        field(c, 10, fy + 18, KitData.Box.FIELD_RED, "Rot", "Quote 1:1", a.color("rot"), tip("Quote <white>1:1", sets));
        field(c, 136, fy + 18, KitData.Box.FIELD_GREEN, "Grün · 0", "Quote 35:1", a.color("gruen"),
                tip("Quote <white>35:1", sets));
        field(c, 262, fy + 18, KitData.Box.FIELD_BLACK, "Schwarz", "Quote 1:1", a.color("schwarz"),
                tip("Quote <white>1:1", sets));

        // Chip wählen
        int cy = 135;
        c.box(0, cy, W, 60, KitData.Box.CASINO_DARK);
        c.text(12, cy + 8, Canvas.caps("Chip wählen"), WHITE, true);
        c.smallIcon(95, cy + 9, KitData.SmallIcon.S_CHIP_RED);
        c.text(107, cy + 8, "Eigener Betrag", GOLD_SOFT);
        c.action(93, cy + 6, 92, 12, a.custom(), tip("Eigenen Betrag eingeben", "<gray>Mindestens 10, höchstens 100.000 Chips"));
        c.textRight(378, cy + 8, List.of(Canvas.Span.of("Jeder Klick setzt ", GRAY),
                Canvas.Span.bold(v.chipValue() > 0 ? num(v.chipValue()) : "-", GOLD)));
        for (int i = 0; i < CHIPS.size(); i++) {
            ChipChoice ch = CHIPS.get(i);
            int cx = 38 + 52 * i;
            boolean sel = i == v.chipIndex();
            c.smallIcon(cx - 9, cy + (sel ? 21 : 24), small("c_" + ch.chip()));
            boolean word = ch.value() <= 0;
            String label = word ? Canvas.caps(ch.label()) : ch.label();
            c.textCenter(cx + 1, cy + 44, label, sel ? GOLD : GRAY, sel);
            String hover = ch.value() > 0 ? "Jeder Klick setzt <yellow>" + num(ch.value()) + " Chips"
                    : ch.value() == 0 ? "Eigenen Betrag eingeben" : "Setzt alle deine Chips";
            c.action(cx - 24, cy + 20, 48, 34, ch.value() == 0 ? a.custom() : a.chip(i), tip(hover));
        }

        // Knöpfe
        int by = 198;
        c.smallButton(0, by, 196, KitData.Small.DARK);
        c.text(8, by + 4, "↻", BTN_GRAY, true);
        c.text(21, by + 4, "Letzte Einsätze wiederholen", BTN_GRAY, true);
        c.action(0, by, 196, 16, a.repeat(), v.canRepeat() ? "Setzt deine Einsätze der letzten Runde" : "Noch keine letzte Runde");
        c.smallButton(252, by, 138, KitData.Small.DARK);
        c.text(262, by + 4, "✗", RED, true);
        c.text(273, by + 4, "Alle zurücknehmen", BTN_GRAY, true);
        c.action(252, by, 138, 16, a.clear(), "Gibt dir alle Einsätze dieser Runde zurück");

        // Deine Einsätze
        int ey = 216;
        int rows = Math.max(1, v.bets().size());
        int eh = 33 + 10 * (rows - 1) + (v.bets().isEmpty() ? 0 : 10);
        c.box(0, ey, W, eh, KitData.Box.CASINO_DARK);
        c.smallIcon(13, ey + 8, KitData.SmallIcon.S_COIN);
        c.text(26, ey + 8, Canvas.caps("Deine Einsätze"), WHITE, true);
        if (v.bets().isEmpty()) {
            c.text(26, ey + 19, "Chip wählen, dann auf eine Farbe klicken.", LIGHT);
        } else {
            c.textRight(378, ey + 8, List.of(Canvas.Span.of("Im Topf ", GRAY), Canvas.Span.bold(num(v.roundTotal()), GOLD)));
            int y = ey + 19;
            for (BetLine b : v.bets()) {
                c.text(26, y, b.label(), b.color(), true);
                c.textRight(378, y, num(b.amount()) + " Chips", LIGHT, false);
                y += 10;
            }
        }
        return c;
    }

    private static void field(Canvas c, int x, int y, KitData.Box style, String label, String quote, Consumer<Player> click,
                              Component hover) {
        c.box(x, y, 118, 34, style, Canvas.Z_BOX + 1);
        c.textCenter(x + 59, y + 8, Canvas.caps(label), WHITE, true);
        c.textCenter(x + 59, y + 20, quote, LIGHT, false);
        c.action(x, y, 118, 34, click, hover);
    }

    // =================================================================================================================
    // Musik
    // =================================================================================================================

    public record Song(String name, String length) {}

    /** choice: -1 = alle Songs, 0..n-1 = ein Song, -2 = Musik aus. */
    public record MusicView(List<Song> songs, int choice, int playing, String time) {}

    public interface MusicActions {
        Consumer<Player> choose(int choice);

        Consumer<Player> next();
    }

    public static Canvas music(MusicView v, MusicActions a) {
        Canvas c = new Canvas(W);
        head(c, KitData.Icon.JUKEBOX, "CASINO", "Musik · Dein Soundtrack im Casino");
        String pill = v.choice() == -1 ? "Alle Songs" : v.choice() == -2 ? "Musik aus" : v.songs().get(v.choice()).name();
        int pw = Canvas.width(pill, true) + 13;
        c.smallButton(378 - pw, 9, pw, v.choice() == -1 ? KitData.Small.GREEN : KitData.Small.GOLD, pill,
                v.choice() == -1 ? ON_GREEN : GOLD_SOFT, true);
        c.textRight(378 - pw - 8, 13, "Deine Wahl", GRAY, false);

        // Läuft gerade
        int ny = 54;
        c.box(0, ny, W, 51, KitData.Box.CASINO_DARK);
        c.icon(8, ny + 10, KitData.Icon.VINYL, true);
        c.text(46, ny + 12, Canvas.caps("Läuft gerade"), GRAY, false);
        if (v.playing() >= 0) {
            Song s = v.songs().get(v.playing());
            c.smallIcon(46, ny + 23, KitData.SmallIcon.S_NOTE_GOLD);
            c.text(59, ny + 22, s.name(), GOLD_SOFT, true);
            c.text(46, ny + 32, List.of(Canvas.Span.of(v.time(), LIGHT),
                    Canvas.Span.of(" · Song " + (v.playing() + 1) + " von " + v.songs().size(), GRAY)));
        } else {
            c.text(46, ny + 22, "Musik ist aus", GRAY, true);
        }
        if (v.choice() == -1) {
            c.smallButton(266, ny + 18, 113, KitData.Small.GOLD);
            c.text(275, ny + 22, "⏭", GOLD_SOFT, false);
            c.text(287, ny + 22, "Nächster Song", GOLD_SOFT, true);
            c.action(266, ny + 18, 113, 16, a.next(), "Springt zum nächsten Song");
        }

        // Auswahl
        int ly = 108;
        int n = v.songs().size() + 2;
        c.box(0, ly, W, 15 + 18 * n, KitData.Box.CASINO_DARK);
        for (int i = 0; i < n; i++) {
            int choice = i == 0 ? -1 : i == n - 1 ? -2 : i - 1;
            int y = ly + 9 + 18 * i;
            boolean on = choice == v.choice();
            String name;
            String desc;
            if (choice == -1) {
                c.text(15, y + 4, "⤨", WHITE, false);
                name = "Alle Songs";
                desc = "Die " + (v.songs().size() == 3 ? "drei" : v.songs().size()) + " Songs wechseln sich ab.";
            } else if (choice == -2) {
                c.smallIcon(15, y + 4, KitData.SmallIcon.S_MUTE);
                name = "Musik aus";
                desc = "Im Casino bleibt es ruhig.";
            } else {
                c.smallIcon(15, y + 4, KitData.SmallIcon.S_NOTE);
                name = v.songs().get(choice).name();
                desc = "Nur dieser Song, immer wieder.";
            }
            c.text(30, y + 4, name, on ? GOLD_SOFT : LIGHT, true);
            c.text(149, y + 4, desc, on ? LIGHT : GRAY, false);
            if (on) {
                c.smallButton(326, y, 51, KitData.Small.GREEN, "Aktiv", ON_GREEN, true);
            } else {
                c.smallButton(326, y, 51, KitData.Small.GOLD, "Wählen", GOLD_SOFT, true);
            }
            c.action(10, y, 370, 16, on ? null : a.choose(choice), on ? "Läuft gerade" : "Klick zum Wählen");
        }
        c.textCenter(W / 2, ly + 15 + 18 * n + 7, "Lautstärke: Optionen > Musik & Töne > Jukebox/Notenblöcke", GRAY, false);
        return c;
    }
}
