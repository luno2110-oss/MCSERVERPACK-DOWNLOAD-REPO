package de.kuby.kubyplugins.casino;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Thema eines Slot-Automaten (v2.05, Wunsch des Besitzers: „pro Slot-Maschine unterschiedliche Icons und Gewinne“):
 * zehn eigene Walzen-Symbole (Bilder: tools/build_slot_themes.py, Früchte: tools/build_casino.py), eigene Walzenfarbe und
 * eigene Gewinntabelle. Index 0 ist das kleine Symbol (zahlt schon einzeln oder doppelt auf der Linie, wie die Kirsche),
 * Index 9 das Jackpot-Symbol (drei davon knacken den gemeinsamen Slots-Jackpot).
 *
 * <p>Alle Tabellen liegen bei rund 95 % Rückfluss (ohne den wachsenden Jackpot), unterscheiden sich aber im Spielgefühl:
 * Früchte-Klassik ausgewogen, Goldrausch selten und groß (bis 300x), Neon 7 mittel, Tiefsee oft kleine Gewinne.
 * SELFTEST prüft die Quote jedes Themas ({@link #rtp()}).
 */
public final class SlotTheme {

    public final String key;
    /** Name am Automaten (Titelschrift, Großbuchstaben). */
    public final String title;
    public final String[] symbols;
    public final String[] names;
    public final String[] plurals;
    /** Gewicht jedes Symbols auf jeder Walze. */
    public final int[] weights;
    /** Drei gleiche: Vielfaches des Einsatzes (Index 9 = Jackpot, hier 0). */
    public final double[] three;
    /** Symbol 0 genau einmal / genau zweimal irgendwo auf der Linie. */
    public final double one, two;
    /** Lichtfarbe je Symbol (Spannung an der letzten Walze). */
    public final int[] colors;
    /** Farbstimmung je Symbol (Kino-Glyphen aus tools/build_cinema.py). */
    public final String[] grades;
    private final int total;

    private SlotTheme(String key, String title, String[] symbols, String[] names, String[] plurals, int[] weights,
                      double[] three, double one, double two, int[] colors, String[] grades) {
        this.key = key;
        this.title = title;
        this.symbols = symbols;
        this.names = names;
        this.plurals = plurals;
        this.weights = weights;
        this.three = three;
        this.one = one;
        this.two = two;
        this.colors = colors;
        this.grades = grades;
        int t = 0;
        for (int w : weights) t += w;
        this.total = t;
    }

    public static final SlotTheme FRUCHT = new SlotTheme("frucht", "SLOTS",
            new String[]{"cherry", "lemon", "banana", "bell", "star", "clover", "chip", "coin", "diamond", "seven"},
            new String[]{"Kirsche", "Zitrone", "Banane", "Glocke", "Stern", "Kleeblatt", "Chip", "Münze", "Diamant", "Sieben"},
            new String[]{"Kirschen", "Zitronen", "Bananen", "Glocken", "Sterne", "Kleeblätter", "Chips", "Münzen", "Diamanten", "Siebener"},
            new int[]{6, 8, 8, 4, 5, 4, 5, 3, 2, 1},
            new double[]{10, 8, 8, 20, 12, 20, 12, 40, 80, 0}, 2, 4,
            new int[]{0xFF3A46, 0xFFE45A, 0xFFD640, 0xFFB838, 0xFFFFFF, 0x58FF78, 0xFF4A6A, 0xFFC848, 0x58C0FF, 0xFF5A2A},
            new String[]{"red", "gold", "gold", "gold", "white", "green", "red", "gold", "cold", "red"});

    public static final SlotTheme GOLD = new SlotTheme("gold", "GOLDRAUSCH",
            new String[]{"g_nugget", "g_pick", "g_lamp", "g_horseshoe", "g_dynamite", "g_bar", "g_sack", "g_crown", "g_ruby", "g_chest"},
            new String[]{"Nugget", "Spitzhacke", "Grubenlampe", "Hufeisen", "Dynamit", "Goldbarren", "Geldsack", "Krone", "Rubin", "Schatztruhe"},
            new String[]{"Nuggets", "Spitzhacken", "Grubenlampen", "Hufeisen", "Dynamitstangen", "Goldbarren", "Geldsäcke", "Kronen", "Rubine", "Schatztruhen"},
            new int[]{5, 6, 6, 5, 5, 4, 3, 3, 2, 1},
            new double[]{10, 18, 18, 30, 40, 70, 100, 160, 300, 0}, 1, 4,
            new int[]{0xFFC83A, 0xC8D2DC, 0xFFD25A, 0xFFC83A, 0xFF4A3A, 0xFFD040, 0xF2D8A0, 0xFFD040, 0xFF3A4A, 0xFFB030},
            new String[]{"gold", "cold", "warm", "gold", "red", "gold", "warm", "gold", "red", "gold"});

    public static final SlotTheme NEON = new SlotTheme("neon", "NEON 7",
            new String[]{"n_cherry", "n_bar1", "n_bar2", "n_bar3", "n_heart", "n_bolt", "n_dice", "n_star", "n_seven_blue", "n_seven"},
            new String[]{"Neon-Kirsche", "BAR", "Doppel-BAR", "Dreifach-BAR", "Herz", "Blitz", "Würfel", "Stern", "Blaue 7", "Neon-7"},
            new String[]{"Neon-Kirschen", "BARs", "Doppel-BARs", "Dreifach-BARs", "Herzen", "Blitze", "Würfel", "Sterne", "blaue Siebener", "Neon-Siebener"},
            new int[]{6, 7, 6, 5, 6, 5, 5, 4, 3, 1},
            new double[]{20, 16, 25, 40, 25, 30, 30, 50, 100, 0}, 1.5, 4,
            new int[]{0xFF4FB8, 0x52DCF2, 0xFF6EC4, 0xFFEA5A, 0xFF4A5A, 0xFFEA5A, 0xFFFFFF, 0xFFEA5A, 0x5AA8FA, 0xFF4FB8},
            new String[]{"purple", "cold", "purple", "gold", "red", "gold", "white", "gold", "cold", "purple"});

    public static final SlotTheme OZEAN = new SlotTheme("ozean", "TIEFSEE",
            new String[]{"o_shell", "o_starfish", "o_fish", "o_crab", "o_anchor", "o_octopus", "o_wheel", "o_pearl", "o_goldfish", "o_trident"},
            new String[]{"Muschel", "Seestern", "Fisch", "Krabbe", "Anker", "Krake", "Steuerrad", "Perle", "Goldfisch", "Dreizack"},
            new String[]{"Muscheln", "Seesterne", "Fische", "Krabben", "Anker", "Kraken", "Steuerräder", "Perlen", "Goldfische", "Dreizacke"},
            new int[]{9, 8, 8, 7, 6, 5, 4, 3, 2, 1},
            new double[]{8, 10, 10, 12, 16, 20, 30, 50, 100, 0}, 1.5, 3,
            new int[]{0xFFB894, 0xFAA43A, 0x5AA8FA, 0xFF4A44, 0xBCC4CE, 0xB460E6, 0xC08A52, 0xFFFFFF, 0xFFC83A, 0x3EC4B4},
            new String[]{"warm", "warm", "cold", "red", "cold", "purple", "warm", "white", "gold", "cold"});

    public static final List<SlotTheme> ALL = List.of(FRUCHT, GOLD, NEON, OZEAN);

    /** Thema nach Schlüssel oder Namen (frucht, früchte, klassik, gold, goldrausch, neon, ozean, tiefsee); sonst null. */
    public static SlotTheme byName(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "frucht", "früchte", "fruechte", "klassik" -> FRUCHT;
            case "gold", "goldrausch" -> GOLD;
            case "neon", "neon7" -> NEON;
            case "ozean", "tiefsee", "meer" -> OZEAN;
            default -> null;
        };
    }

    /** Standard-Thema zum Gehäuse-Stil (klassik → Früchte, gold → Goldrausch, neon → Neon 7, ozean → Tiefsee). */
    public static SlotTheme forStyle(String style) {
        SlotTheme t = byName(style);
        return t == null ? FRUCHT : t;
    }

    public int pick(Random r) {
        int x = r.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            x -= weights[i];
            if (x < 0) return i;
        }
        return 0;
    }

    public int index(String sym) {
        for (int i = 0; i < symbols.length; i++) if (symbols[i].equals(sym)) return i;
        return -1;
    }

    /** Auszahlung (Vielfaches des Einsatzes) für eine Linie ohne Jackpot; Jackpot-Linie (drei × Index 9) zählt hier 0. */
    public double payout(int[] r) {
        if (r[0] == r[1] && r[1] == r[2]) return three[r[0]];
        int small = 0;
        for (int x : r) if (x == 0) small++;
        return small == 2 ? two : small == 1 ? one : 0;
    }

    /**
     * Rückfluss exakt gerechnet (alle 1.000 Linien gewichtet), Jackpot-Linie mit ihrer Mindestauszahlung von 100x.
     * Der wachsende Jackpot (2 % jedes Einsatzes) kommt noch obendrauf.
     */
    public double rtp() {
        double ev = 0;
        int[] r = new int[3];
        for (int a = 0; a < 10; a++) {
            for (int b = 0; b < 10; b++) {
                for (int c = 0; c < 10; c++) {
                    r[0] = a;
                    r[1] = b;
                    r[2] = c;
                    double pr = (double) weights[a] * weights[b] * weights[c] / ((double) total * total * total);
                    ev += pr * (a == 9 && b == 9 && c == 9 ? 100 : payout(r));
                }
            }
        }
        return ev;
    }

    /** Lichtfarbe zu einem Symbol aus irgendeinem Thema (Standard warmweiß). */
    public static int colorOf(String sym) {
        for (SlotTheme t : ALL) {
            int i = t.index(sym);
            if (i >= 0) return t.colors[i];
        }
        return 0xFFD08A;
    }

    public static String gradeOf(String sym) {
        for (SlotTheme t : ALL) {
            int i = t.index(sym);
            if (i >= 0) return t.grades[i];
        }
        return "warm";
    }

    /** „2x“, „1,5x“ (deutsches Komma, ohne unnötige Null). */
    public static String mult(double m) {
        return (m == Math.rint(m) ? String.valueOf((long) m) : String.format(Locale.GERMAN, "%.1f", m)) + "x";
    }

    /** SELFTEST: jedes Thema 10 Symbole mit Bild, Quote 93–97 %; leere Liste = in Ordnung. */
    public static List<String> selfCheck() {
        List<String> errs = new ArrayList<>();
        for (SlotTheme t : ALL) {
            if (t.symbols.length != 10 || t.names.length != 10 || t.plurals.length != 10 || t.weights.length != 10
                    || t.three.length != 10 || t.colors.length != 10 || t.grades.length != 10) {
                errs.add(t.key + ": nicht 10 Einträge je Liste");
                continue;
            }
            double q = t.rtp();
            if (q < 0.93 || q > 0.97) errs.add(t.key + ": Rückfluss " + String.format(Locale.ROOT, "%.1f", q * 100) + " % (Soll 93–97 %)");
        }
        return errs;
    }

    public static String stats() {
        StringBuilder b = new StringBuilder();
        for (SlotTheme t : ALL) {
            if (b.length() > 0) b.append(", ");
            b.append(t.key).append(' ').append(String.format(Locale.ROOT, "%.1f %%", t.rtp() * 100));
        }
        return b.toString();
    }
}
