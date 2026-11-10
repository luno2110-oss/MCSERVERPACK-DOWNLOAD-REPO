package de.kuby.kubyplugins.cookie;

import de.kuby.kubyplugins.ui.KitData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Inhalte des Cookie Clickers (eigene Bäckerei-Welt): 14 Gebäude, Upgrades (Stufen je Gebäude, Klick, Rezepte, Ofen,
 * Glück), Erfolge in fünf Bereichen, Titel nach Stufe, der Sternen-Baum für Backsterne aus dem Aufstieg und der
 * Keks-Laden (Items und Vorteile für Kekse).
 */
public final class CookieContent {

    private CookieContent() {
    }

    /** Gebäude: Grundpreis und Kekse pro Sekunde je Stück. */
    public record Building(String id, String name, String desc, double cost, double cps) {
        public KitData.Sprite art() {
            try {
                return KitData.Sprite.valueOf("CK_B_" + id.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return KitData.Sprite.CK_COOKIE;
            }
        }
    }

    public static final List<Building> BUILDINGS = List.of(
            new Building("loeffel", "Kochlöffel", "Rührt ab und zu einen Keks zusammen.", 15, 0.1),
            new Building("schuessel", "Rührschüssel", "Teig für eine ganze Ladung.", 100, 1),
            new Building("blech", "Backblech", "Zwölf Kekse auf einmal.", 1_100, 8),
            new Building("ofen", "Steinofen", "Heiß, schnell, zuverlässig.", 12_000, 47),
            new Building("lehrling", "Bäckerlehrling", "Lernt schnell und knetet fleißig.", 130_000, 260),
            new Building("muehle", "Windmühle", "Frisches Mehl, sobald der Wind weht.", 1_400_000, 1_400),
            new Building("zucker", "Zuckerrohrfeld", "Süßer Nachschub vom Feld.", 20_000_000, 7_800),
            new Building("schoko", "Schokobrunnen", "Ein Fluss aus flüssiger Schokolade.", 330_000_000, 44_000),
            new Building("fabrik", "Keksfabrik", "Fließbänder rund um die Uhr.", 5_100_000_000d, 260_000),
            new Building("zeppelin", "Lieferzeppelin", "Bringt Kekse in jede Ecke der Welt.", 75_000_000_000d, 1_600_000),
            new Building("bank", "Keksbank", "Kekse vermehren sich mit Zinsen.", 1e12, 10_000_000),
            new Building("zauber", "Zauberküche", "Ein Spruch, ein Blech voller Kekse.", 1.4e13, 65_000_000),
            new Building("zeitofen", "Zeitofen", "Backt die Kekse von morgen schon heute.", 1.7e14, 430_000_000),
            new Building("sterne", "Sternenbäckerei", "Kekse aus dem Licht ferner Sterne.", 2.1e15, 2_900_000_000d));

    /** Große Zahlen kurz: 137, 1,4K, 12,0K, 3,2 Mio, 4,5 Mrd, 1,1 Bio, 7,0 Brd, 2,3 Trio … */
    public static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "∞";
        boolean neg = v < 0;
        v = Math.abs(v);
        String s;
        if (v < 1_000) {
            s = v < 100 && v != Math.floor(v) ? String.format(Locale.GERMANY, "%.1f", v) : String.format(Locale.GERMANY, "%,d", (long) Math.floor(v));
        } else if (v < 1_000_000) {
            s = String.format(Locale.GERMANY, "%.1fK", v / 1_000);
        } else {
            String[] units = {"Mio", "Mrd", "Bio", "Brd", "Trio", "Trd", "Qio", "Qrd", "Qui", "Qid", "Sex", "Sed"};
            int u = (int) Math.floor(Math.log10(v) / 3) - 2;
            if (u >= units.length) {
                s = String.format(Locale.GERMANY, "%.2e", v);
            } else {
                double d = v / Math.pow(1000, u + 2);
                if (d >= 999.95 && u + 1 < units.length) {
                    d /= 1000;
                    u++;
                }
                s = String.format(Locale.GERMANY, "%.1f %s", d, units[u]);
            }
        }
        return neg ? "-" + s : s;
    }

    public static int buildingIndex(String id) {
        for (int i = 0; i < BUILDINGS.size(); i++) if (BUILDINGS.get(i).id().equals(id)) return i;
        return -1;
    }

    // =================================================================================================================
    // Upgrades
    // =================================================================================================================

    public enum Kind {
        /** Gebäude backt doppelt. */ TIER,
        /** Klick x2 (value = Faktor). */ CLICK_X,
        /** Klick +value % der Kekse pro Sekunde. */ CLICK_CPS,
        /** Alle Gebäude +value %. */ GLOBAL,
        /** Ofen: Wirkungsgrad und Stunden, während du weg bist. */ OFEN,
        /** Glückskekse kommen öfter und wirken länger. */ GLUECK
    }

    /** Bedingung zum Anzeigen und Kaufen: Gebäude-Anzahl, Klicks, gebackene Kekse (diese Runde) oder Glückskekse. */
    public record Req(String type, String target, double amount) {}

    public record Upgrade(String id, String name, String desc, Kind kind, String target, double value, double cost, Req req) {}

    static final String[] TIER_NAMES = {"Geölt", "Verstärkt", "Doppelt gebacken", "Meisterhaft", "Legendär", "Mythisch", "Göttlich"};
    static final int[] TIER_AT = {1, 10, 25, 50, 100, 150, 200};
    static final double[] TIER_COST = {10, 50, 500, 50_000, 5e6, 5e8, 5e10};

    /** Ofen-Stufe 0..5: Stunden, die die Bäckerei ohne dich backt. */
    static final int[] OFEN_HOURS = {1, 2, 4, 8, 12, 24};

    public static final List<Upgrade> UPGRADES = buildUpgrades();

    private static List<Upgrade> buildUpgrades() {
        List<Upgrade> out = new ArrayList<>();
        for (Building b : BUILDINGS) {
            for (int t = 0; t < TIER_AT.length; t++) {
                out.add(new Upgrade("t_" + b.id() + "_" + (t + 1), b.name() + ": " + TIER_NAMES[t], b.name() + " backen doppelt so viel.",
                        Kind.TIER, b.id(), 2, b.cost() * TIER_COST[t], new Req("building", b.id(), TIER_AT[t])));
            }
        }
        out.add(new Upgrade("c_handschuh", "Ofenhandschuh", "Klicks backen doppelt so viel.", Kind.CLICK_X, null, 2, 100, new Req("building", "loeffel", 1)));
        out.add(new Upgrade("c_backhandschuh", "Backhandschuh", "Klicks backen doppelt so viel.", Kind.CLICK_X, null, 2, 500, new Req("building", "loeffel", 1)));
        out.add(new Upgrade("c_teigrolle", "Goldene Teigrolle", "Klicks backen doppelt so viel.", Kind.CLICK_X, null, 2, 10_000, new Req("building", "loeffel", 10)));
        double[] cc = {50_000, 5e6, 5e8, 5e10, 5e12, 5e14, 5e16};
        long[] cr = {100, 1_000, 5_000, 20_000, 100_000, 500_000, 2_000_000};
        String[] roman = {"I", "II", "III", "IV", "V", "VI", "VII"};
        for (int i = 0; i < cc.length; i++) {
            out.add(new Upgrade("c_finger_" + (i + 1), "Zuckerfinger " + roman[i], "Jeder Klick bringt zusätzlich 1 % deiner Kekse pro Sekunde.",
                    Kind.CLICK_CPS, null, 1, cc[i], new Req("clicks", null, cr[i])));
        }
        String[] rec = {"Schokostückchen", "Haselnuss", "Vanillezucker", "Zimt", "Karamell", "Macadamia", "Pistazie", "Marzipan", "Trüffel",
                "Blattgold", "Sternenstaub", "Kometenkruste"};
        int[] pct = {5, 5, 5, 5, 10, 10, 10, 10, 15, 15, 20, 20};
        for (int i = 0; i < rec.length; i++) {
            double cost = 100_000 * Math.pow(10, i);
            out.add(new Upgrade("r_" + (i + 1), "Rezept: " + rec[i], "Alle Gebäude backen " + pct[i] + " % mehr.", Kind.GLOBAL, null, pct[i], cost,
                    new Req("baked", null, cost / 2)));
        }
        for (int i = 0; i < 5; i++) {
            double cost = 5_000 * Math.pow(100, i);
            out.add(new Upgrade("o_" + (i + 1), "Ofen " + roman[i], "Während du weg bist, backt die Bäckerei mit " + (20 + i * 10) + " % bis zu "
                    + OFEN_HOURS[i + 1] + " Stunden.", Kind.OFEN, null, i + 1, cost, new Req("baked", null, cost / 2)));
        }
        double[] gc = {77_700, 77_700_000, 77_700_000_000d};
        long[] gr = {3, 7, 27};
        for (int i = 0; i < gc.length; i++) {
            out.add(new Upgrade("g_" + (i + 1), "Glücksbringer " + roman[i], "Glückskekse kommen 15 % öfter und wirken 10 % länger.", Kind.GLUECK, null,
                    i + 1, gc[i], new Req("golden", null, gr[i])));
        }
        out.sort(Comparator.comparingDouble(Upgrade::cost));
        return List.copyOf(out);
    }

    public static Upgrade upgrade(String id) {
        for (Upgrade u : UPGRADES) if (u.id().equals(id)) return u;
        return null;
    }

    // =================================================================================================================
    // Erfolge (jeder: +2 % Kekse pro Sekunde)
    // =================================================================================================================

    public record Achievement(String id, String tab, String name, String desc, String type, String target, double amount) {}

    public static final List<String> TABS = List.of("Backen", "Klicken", "Gebäude", "Glück", "Aufstieg");

    public static final List<Achievement> ACHIEVEMENTS = buildAchievements();

    private static List<Achievement> buildAchievements() {
        List<Achievement> out = new ArrayList<>();
        String[] bn = {"Erste Krümel", "Gut im Teig", "Volle Dose", "Keksberg", "Keksimperium", "Keksgalaxie", "Keksuniversum", "Unendlich Kekse"};
        double[] ba = {1_000, 100_000, 10_000_000, 1e9, 1e11, 1e13, 1e15, 1e18};
        for (int i = 0; i < bn.length; i++) out.add(new Achievement("bake_" + i, "Backen", bn[i], "Backe insgesamt " + fmt(ba[i]) + " Kekse.", "alltime", null, ba[i]));
        String[] cn = {"Klicker", "Fleißige Finger", "Klickmaschine", "Unermüdlich", "Legende der Maus"};
        double[] ca = {100, 1_000, 10_000, 100_000, 1_000_000};
        for (int i = 0; i < cn.length; i++) out.add(new Achievement("click_" + i, "Klicken", cn[i], "Klicke " + fmt(ca[i]) + "-mal auf den Keks.", "clicks", null, ca[i]));
        out.add(new Achievement("hand_0", "Klicken", "Handarbeit", "Backe 1.000 Kekse mit Klicks.", "handmade", null, 1_000));
        out.add(new Achievement("hand_1", "Klicken", "Goldene Hände", "Backe 1 Mio Kekse mit Klicks.", "handmade", null, 1e6));
        out.add(new Achievement("hand_2", "Klicken", "Klick-Millionär", "Backe 1 Mrd Kekse mit Klicks.", "handmade", null, 1e9));
        for (Building b : BUILDINGS) {
            out.add(new Achievement("b_" + b.id() + "_1", "Gebäude", b.name(), "Besitze 1 " + b.name() + ".", "building", b.id(), 1));
            out.add(new Achievement("b_" + b.id() + "_50", "Gebäude", b.name() + " x50", "Besitze 50 " + b.name() + ".", "building", b.id(), 50));
            out.add(new Achievement("b_" + b.id() + "_100", "Gebäude", b.name() + " x100", "Besitze 100 " + b.name() + ".", "building", b.id(), 100));
        }
        out.add(new Achievement("total_100", "Gebäude", "Kleine Bäckerei", "Besitze 100 Gebäude.", "buildings", null, 100));
        out.add(new Achievement("total_500", "Gebäude", "Großbäckerei", "Besitze 500 Gebäude.", "buildings", null, 500));
        out.add(new Achievement("total_1000", "Gebäude", "Keks-Konzern", "Besitze 1.000 Gebäude.", "buildings", null, 1_000));
        String[] gn = {"Glückspilz", "Goldjäger", "Glücksritter", "Fortunas Liebling"};
        double[] ga = {1, 10, 50, 200};
        for (int i = 0; i < gn.length; i++) out.add(new Achievement("gold_" + i, "Glück", gn[i], "Fange " + (long) ga[i] + (ga[i] == 1 ? " Glückskeks." : " Glückskekse."), "golden", null, ga[i]));
        String[] rn = {"Regenfänger", "Keksregen-Profi", "Wolkenbäcker"};
        double[] ra = {10, 100, 1_000};
        for (int i = 0; i < rn.length; i++) out.add(new Achievement("rain_" + i, "Glück", rn[i], "Fange " + (long) ra[i] + " Kekse im Keksregen.", "rain", null, ra[i]));
        out.add(new Achievement("cps_0", "Backen", "Ofen läuft", "Backe 100 Kekse pro Sekunde.", "cps", null, 100));
        out.add(new Achievement("cps_1", "Backen", "Fließband", "Backe 100.000 Kekse pro Sekunde.", "cps", null, 100_000));
        out.add(new Achievement("cps_2", "Backen", "Keksflut", "Backe 100 Mio Kekse pro Sekunde.", "cps", null, 1e8));
        out.add(new Achievement("cps_3", "Backen", "Keksgewitter", "Backe 100 Mrd Kekse pro Sekunde.", "cps", null, 1e11));
        String[] an = {"Neuanfang", "Wiedergeboren", "Sternenwanderer", "Ewige Bäckerei"};
        double[] aa = {1, 5, 25, 100};
        for (int i = 0; i < an.length; i++) out.add(new Achievement("asc_" + i, "Aufstieg", an[i], "Steige " + (long) aa[i] + "-mal auf.", "ascensions", null, aa[i]));
        String[] ln = {"Erster Stern", "Sternbild", "Milchstraße", "Supernova"};
        double[] la = {1, 10, 100, 1_000};
        for (int i = 0; i < ln.length; i++) out.add(new Achievement("lvl_" + i, "Aufstieg", ln[i], "Erreiche Stufe " + fmt(la[i]) + ".", "level", null, la[i]));
        return List.copyOf(out);
    }

    // =================================================================================================================
    // Titel und Sternen-Baum
    // =================================================================================================================

    /** Titel nach der höchsten Stufe: [ab Stufe, Name]. */
    public static final Object[][] TITLES = {{0, "Krümel"}, {1, "Teigkneter"}, {5, "Bäckerlehrling"}, {10, "Bäcker"}, {25, "Konditor"},
            {50, "Backmeister"}, {100, "Keksbaron"}, {250, "Zuckerfürst"}, {500, "Keksmagier"}, {1_000, "Sternenbäcker"}};

    public static String title(long level) {
        String t = "Krümel";
        for (Object[] r : TITLES) if (level >= ((Number) r[0]).longValue()) t = (String) r[1];
        return t;
    }

    /** Knoten des Sternen-Baums (bleibt für immer). Jeder braucht den vorigen. */
    public record Node(String id, String name, String desc, long cost) {}

    public static final List<Node> TREE = List.of(
            new Node("start1", "Startkapital", "Jede Runde beginnt mit 1.000 Keksen.", 1),
            new Node("teig1", "Sternenteig", "+10 % Kekse pro Sekunde.", 3),
            new Node("gluecksstern", "Glücksstern", "Glückskekse kommen 15 % öfter.", 5),
            new Node("glut", "Ewige Glut", "Ohne dich backt die Bäckerei 10 % mehr und 4 Stunden länger.", 10),
            new Node("finger", "Flinke Finger", "Klicks +25 %.", 15),
            new Node("magnet", "Keksmagnet", "Kekse aus dem Keksregen bringen doppelt so viel.", 25),
            new Node("start2", "Startkapital II", "Jede Runde beginnt mit 1 Mio Keksen.", 40),
            new Node("rausch", "Langer Rausch", "Glückskeks-Effekte halten 25 % länger.", 60),
            new Node("teig2", "Sternenteig II", "+25 % Kekse pro Sekunde.", 100),
            new Node("combo", "Combo-Meister", "Die Combo geht bis x3 statt x2.", 150),
            new Node("goldader", "Goldader", "Der Keksschatz aus Glückskeksen ist doppelt so groß.", 250),
            new Node("sternenbaeckerei", "Sternenbäckerei", "+50 % Kekse pro Sekunde.", 500));

    public static Node node(String id) {
        for (Node n : TREE) if (n.id().equals(id)) return n;
        return null;
    }

    // =================================================================================================================
    // Keks-Laden
    // =================================================================================================================

    /**
     * Angebot im Keks-Laden. Preis = so viele Sekunden deiner Produktion (seconds), mindestens minCost; so bleibt der
     * Laden vom ersten Keks bis zur Sternenbäckerei spannend. daily: höchstens so oft am Tag.
     * perk: Vorteil (Trank-Effekt oder sofort), sonst Item. effect/level/minutes für Effekte, amount für Items.
     */
    public record Offer(String id, String name, String desc, boolean perk, org.bukkit.Material icon, double minCost,
                        int seconds, int daily, String effect, int level, int minutes, int amount) {}

    public static final List<Offer> OFFERS = List.of(
            // Vorteile
            new Offer("satt", "Satt essen", "Hunger und Sättigung voll.", true, org.bukkit.Material.COOKED_BEEF,
                    1_000, 60, 10, "satt", 0, 0, 0),
            new Offer("nachtsicht", "Nachtsicht", "20 Min. im Dunkeln sehen.", true, org.bukkit.Material.GOLDEN_CARROT,
                    3_000, 200, 5, "night_vision", 0, 20, 0),
            new Offer("tempo", "Tempo", "10 Min. schneller laufen.", true, org.bukkit.Material.SUGAR,
                    5_000, 300, 5, "speed", 0, 10, 0),
            new Offer("eile", "Eile", "10 Min. schneller abbauen.", true, org.bukkit.Material.GOLDEN_PICKAXE,
                    5_000, 300, 5, "haste", 1, 10, 0),
            new Offer("feuer", "Feuerschutz", "10 Min. Schutz vor Feuer.", true, org.bukkit.Material.MAGMA_CREAM,
                    6_000, 300, 3, "fire_resistance", 0, 10, 0),
            new Offer("wasser", "Unterwasser", "10 Min. unter Wasser atmen.", true,
                    org.bukkit.Material.HEART_OF_THE_SEA, 6_000, 300, 3, "water_breathing", 0, 10, 0),
            new Offer("glueck", "Glück", "15 Min. bessere Beute.", true, org.bukkit.Material.RABBIT_FOOT,
                    8_000, 400, 3, "luck", 0, 15, 0),
            new Offer("level", "Erfahrung", "Sofort 5 Level.", true, org.bukkit.Material.EXPERIENCE_BOTTLE,
                    20_000, 900, 3, "xp", 5, 0, 0),
            // Items
            new Offer("kekse", "Kekse", "16 echte Kekse zum Essen.", false, org.bukkit.Material.COOKIE,
                    500, 30, 20, null, 0, 0, 16),
            new Offer("kuchen", "Kuchen", "Ein ganzer Kuchen.", false, org.bukkit.Material.CAKE,
                    2_000, 120, 5, null, 0, 0, 1),
            new Offer("karotten", "Goldene Karotten", "8 Goldene Karotten.", false, org.bukkit.Material.GOLDEN_CARROT,
                    10_000, 600, 3, null, 0, 0, 8),
            new Offer("feuerwerk", "Feuerwerk", "16 Raketen für Elytren.", false, org.bukkit.Material.FIREWORK_ROCKET,
                    12_000, 600, 3, null, 0, 0, 16),
            new Offer("flaschen", "XP-Fläschchen", "8 Fläschchen mit Erfahrung.", false,
                    org.bukkit.Material.EXPERIENCE_BOTTLE, 15_000, 800, 3, null, 0, 0, 8),
            new Offer("namensschild", "Namensschild", "Für ein Tier.", false, org.bukkit.Material.NAME_TAG,
                    20_000, 1_000, 2, null, 0, 0, 1),
            new Offer("goldapfel", "Goldener Apfel", "Ein Goldener Apfel.", false, org.bukkit.Material.GOLDEN_APPLE,
                    25_000, 1_200, 2, null, 0, 0, 1),
            new Offer("diamant", "Diamant", "Ein Diamant.", false, org.bukkit.Material.DIAMOND,
                    50_000, 2_400, 1, null, 0, 0, 1));

    public static Offer offer(String id) {
        for (Offer o : OFFERS) if (o.id().equals(id)) return o;
        return null;
    }
}
