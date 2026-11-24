package de.kuby.kubyplugins.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Roulette-Tisch wie in Las Vegas (v1.98), komplett aus Anzeigen, freistehend:
 * <ul>
 *   <li>Tableau (Bild rv_layout, 1,6 x 4,0 Blöcke): Null am Kessel, 1–36 in drei Reihen, „2:1“ am Ende, Dutzende und
 *       einfache Chancen auf der Spielerseite. Gesetzt wird direkt auf dem Tisch: Rechtsklick legt den gewählten Chip
 *       auf das Feld unter dem Fadenkreuz, Linksklick nimmt die eigenen Chips von dem Feld zurück.</li>
 *   <li>Kessel mit Holzrand und Rauten, drehender Rotor mit 37 Fächern, Drehkreuz und echter Kugel: Sie rollt gegen den
 *       Rotor in der Bahn, wird durch Reibung langsamer, fällt ab, trifft manchmal eine Raute, springt über den Zahlenring
 *       und hüpft je Wurf unterschiedlich lange über die Fächer (Physik in {@link Throw}, v2.04). Töne aus einer echten
 *       Aufnahme je Ereignis (tools/build_roulette_sfx.py).</li>
 *   <li>Croupier (Gelenkfigur wie beim Blackjack) auf der Kesselseite, Chip-Ablage vor ihm.</li>
 *   <li>Jeder Spieler setzt mit eigener Chip-Farbe (wie am echten Tisch), der Croupier stellt den Marker („Dolly“) auf
 *       die Gewinnzahl, zieht verlorene Chips ein und schiebt Gewinne zu.</li>
 * </ul>
 *
 * <p>Koordinaten: origin = Boden in der Tischmitte. u läuft längs (vom Kessel weg, R), v quer zum Croupier (F),
 * die Spieler stehen bei v &lt; 0. Bild-x des Tableaus läuft entlang v, Bild-y entlang u (0 = Kesselseite).
 */
public final class RouletteTable {

    /** Reihenfolge der Fächer auf dem europäischen Rad. */
    public static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24,
            16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};


    static final double FELT = 0.86;
    /** Kleinster Versatz gegen Z-Fighting (1,2 mm): Tiefenpuffer in 20 Blöcken Abstand rund 0,5 mm genau (v2.04). */
    static final double ZF = 0.0012;
    private static final double PX = 0.025;
    private static final double LAY_U0 = -1.35, LAY_V0 = -0.8;
    private static final int LW = 64, LH = 160;
    private static final int X_OUT = 2, X_DOZ = 13, X_NUM = 24, X_END = 57, CELL_W = 11, ROW_H = 10;
    private static final int Y_ZERO = 2, Y_NUM = 15, Y_COL = Y_NUM + 12 * ROW_H, Y_END = Y_COL + 11;
    private static final double WU = -2.3, WV = 0.0;
    private static final float BOWL = 1.5f, ROTOR = 1.0226f, TURRET = 0.24f, BALL = 0.055f;
    /**
     * Kessel mit Tiefe (v2.00): Randwand bis FELT + 0,15, Kugelbahn (22,5° nach innen) von r 0,69 auf 0,60,
     * Schräge hinunter bis r 0,49, Rotor-Fächerboden FELT - 0,01 mit Zahlenring (+0,033) und Kegel (bis +0,09).
     * Kugel-Mitte: in der Bahn r 0,655 bei FELT + 0,113 (läuft an der Randwand), im Fach r 0,395 knapp über dem Boden.
     */
    private static final double H_ROTOR = FELT - 0.01, H_BALL = FELT;
    private static final double R_TRACK = 0.655, R_POCKET = 0.395;
    private static final double Y_TRACK = 0.113, Y_POCKET = H_ROTOR - FELT + 0.0225, CONE_TOP = 0.09;
    private static final double CU = -1.25, CV = 1.58;
    private static final float CHIP = 0.21f;
    /** Chips im Rack etwas kleiner (Röhrenabstand 0,15): Durchmesser 0,144 statt 0,168. */
    private static final float RACK_CHIP = 0.18f;
    private static final double RACK_U = 0.15, RACK_V = 0.935;

    /** Chip-Farben der Spieler (wie die Farbchips am echten Tisch) und ihre Textfarbe. */
    public static final String[] COLORS = {"red", "blue", "green", "purple", "pink", "white", "black", "gold"};
    public static final int[] COLOR_RGB = {0xE04848, 0x4A7BE0, 0x3FB860, 0x9A5CE0, 0xF07AB8, 0xF0F0F0, 0x9A9A9A, 0xF2C94C};

    /** Ein Platz auf dem Tableau: Art wie RouletteGame.Kind (Name) und Zahl/Nummer. */
    public record Spot(String kind, int param) {
        public String key() {
            return kind + ":" + param;
        }
    }

    private final Plugin plugin;
    private final NamespacedKey tag;
    /** Tischfarbe (v2.07): grün, rot (High Roller), violett (Elite) – Filz und Tableau in der Farbe. */
    private TableTier tier = TableTier.GRUEN;
    private final NamespacedKey click;
    private Location origin;
    private float yaw;
    private final Random rnd = new Random();

    private DealerAnim croupier;
    private TextDisplay status;
    private TextDisplay betsText;
    private ItemDisplay rotor;
    private ItemDisplay turret;
    private ItemDisplay ball;
    private ItemDisplay dolly;
    /** Chip-Rack des Croupiers (v2.07). */
    private ChipRack bankRack;
    private final List<ItemDisplay> winFrame = new ArrayList<>();
    private final List<Entity> blitzMarks = new ArrayList<>();
    /** Chips je Platz und Farbe (Stapel von unten nach oben). */
    private final Map<String, LinkedHashMap<String, List<ItemDisplay>>> stacks = new LinkedHashMap<>();
    /** Gewinn-Chips, die der Croupier dazulegt (verschwinden mit dem Rest). */
    private final List<ItemDisplay> payouts = new ArrayList<>();
    /** Feld-Rahmen unter dem Fadenkreuz, nur für den jeweiligen Spieler sichtbar. */
    private final Map<UUID, ItemDisplay[]> hover = new HashMap<>();
    private final Map<UUID, String> hoverKey = new HashMap<>();

    // Kessel-Zustand
    private BukkitTask wheelTask;
    private long wheelTick;
    private double rho;
    private double omega = 0.35;
    private int ballPocket;
    private int spinTick = -1;
    private Throw thr;
    private int evIdx;
    private int target;
    private Runnable onLand;
    private Runnable onDone;

    public RouletteTable(Plugin plugin, NamespacedKey tag) {
        this.plugin = plugin;
        this.tag = tag;
        // je Tisch ein eigener Klick-Schlüssel (v2.07: drei Roulette-Tische, grün/rot/violett): grün bleibt roulette_click
        this.click = new NamespacedKey(plugin, tag.getKey().equals("roulette_table") ? "roulette_click" : tag.getKey() + "_click");
    }

    public NamespacedKey clickKey() {
        return click;
    }

    public void geometry(Location floorCenter, float yaw) {
        this.origin = floorCenter.clone();
        this.origin.setYaw(0);
        this.origin.setPitch(0);
        this.yaw = yaw;
    }

    public float yaw() {
        return yaw;
    }

    /** Tischmitte am Boden (null, solange kein Tisch gesetzt ist). */
    public Location center() {
        return origin == null ? null : origin.clone();
    }

    /** Kesselmitte auf Höhe des Rotors. */
    public Location wheel() {
        return origin == null ? null : local(WU, WV, H_ROTOR);
    }

    /** Mitte des Tableaus auf dem Filz. */
    public Location layoutCenter() {
        return origin == null ? null : local(LAY_U0 + LH * PX / 2, LAY_V0 + LW * PX / 2, FELT);
    }

    /** Wo die Spieler stehen (Mitte der Spielerseite). */
    public Location playerSide() {
        return origin == null ? null : local(0.6, -1.55, 0);
    }

    public boolean ready() {
        return origin != null && origin.getWorld() != null && status != null && status.isValid();
    }

    public DealerAnim croupier() {
        return croupier;
    }

    // =================================================================================================================
    // Koordinaten
    // =================================================================================================================

    private Vector3f fwd() {
        double r = Math.toRadians(yaw);
        return new Vector3f((float) -Math.sin(r), 0, (float) Math.cos(r));
    }

    private Vector3f right() {
        Vector3f f = fwd();
        return new Vector3f(-f.z, 0, f.x);
    }

    /** Ort in Tisch-Koordinaten (u längs vom Kessel weg, v zum Croupier, up über dem Boden). */
    public Location local(double u, double v, double up) {
        Vector3f r = right(), f = fwd();
        return origin.clone().add(r.x * u + f.x * v, up, r.z * u + f.z * v);
    }

    /** Drehung, die Modell-+X auf F (Croupier) und Modell-+Z auf R (längs) legt. */
    private Quaternionf base() {
        Vector3f f = fwd();
        return new Quaternionf().rotateY((float) Math.atan2(-f.z, f.x));
    }

    /**
     * Drehung für Bild-Modelle (Tableau, Kessel, Rotor): der Client dreht Item-Anzeigen um 180° um die Hochachse,
     * das hebt diese Drehung wieder auf (sonst läge die Null am falschen Ende).
     */
    private Quaternionf pic() {
        return base().rotateY((float) Math.PI);
    }

    private static Quaternionf yawTo(Vector3f d) {
        return new Quaternionf().rotateY((float) Math.atan2(d.x, d.z));
    }

    // =================================================================================================================
    // Aufbau
    // =================================================================================================================

    private void mark(Entity e, String role) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, role);
    }

    private ItemDisplay display(Location at, String model, Quaternionf rot, Vector3f scale, String role) {
        return display(at, model, rot, scale, role, false);
    }

    /** hidden: von Anfang an für niemanden sichtbar (danach gezielt mit showEntity für einen Spieler). */
    private ItemDisplay display(Location at, String model, Quaternionf rot, Vector3f scale, String role, boolean hidden) {
        return at.getWorld().spawn(at, ItemDisplay.class, e -> {
            if (hidden) e.setVisibleByDefault(false);
            e.setItemStack(BlackjackTable.model(model));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), rot, scale, new Quaternionf()));
            e.setViewRange(1.2f);
            mark(e, role);
        });
    }

    /** Quader in Tisch-Koordinaten: u0..u1 längs, v0..v1 quer, y0..y1 Höhe. */
    private ItemDisplay slab(double u0, double u1, double v0, double v1, double y0, double y1, String material) {
        Location c = local((u0 + u1) / 2, (v0 + v1) / 2, (y0 + y1) / 2);
        return display(c, "m_" + material, base(), new Vector3f((float) (v1 - v0), (float) (y1 - y0), (float) (u1 - u0)), "static");
    }

    private TextDisplay text(Location at, float scale, Display.Billboard bb, Quaternionf rot, String role) {
        return at.getWorld().spawn(at, TextDisplay.class, e -> {
            e.text(Component.empty());
            e.setBillboard(bb);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setLineWidth(260);
            e.setTransformation(new Transformation(new Vector3f(), rot == null ? new Quaternionf() : rot,
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            mark(e, role);
        });
    }

    private Interaction hitbox(Location at, float w, float h, String what) {
        return at.getWorld().spawn(at, Interaction.class, e -> {
            e.setInteractionWidth(w);
            e.setInteractionHeight(h);
            e.setResponsive(true);
            mark(e, "click");
            e.getPersistentDataContainer().set(click, PersistentDataType.STRING, what);
        });
    }

    /** Entfernt alle Anzeigen dieses Tischs (auch Reste nach einem Absturz) und baut neu auf. */
    public void build() {
        if (origin == null || origin.getWorld() == null) return;
        removeAll();
        World w = origin.getWorld();
        // --- Unterbau: Korpus mit Sockelleiste, Kesselsockel, Platte, Filz, Tableau ---
        slab(-1.35, 2.6, -0.7, 0.85, 0.06, FELT - 0.12, "rv_wood");
        slab(-1.32, 2.57, -0.67, 0.82, 0, 0.06, "hd_dark");
        slab(-1.35, 2.6, -0.715, -0.7, 0.34, 0.36, "hd_gold");
        slab(-2.95, -1.65, -0.6, 0.6, 0.06, FELT - 0.1, "rv_wood");
        slab(-2.92, -1.68, -0.57, 0.57, 0, 0.06, "hd_dark");
        slab(-1.55, 2.9, -1.05, 1.1, FELT - 0.12, FELT - 0.06, "rv_wood");
        slab(-1.518, 2.68, -0.85, 1.04, FELT - 0.06, FELT - 0.003, "rv_felt" + feltSuffix());     // Ende 2 mm hinter dem Polster
        display(layoutCenter(), "rv_layout" + feltSuffix(), pic(), new Vector3f((float) (LW * PX), 1f, (float) (LH * PX)), "static");
        // Polster-Reling auf der Spielerseite und am Ende, Goldkante innen; Croupier-Seite Holzkante mit Goldleiste
        slab(-1.52, 2.9, -1.05, -0.83, FELT - 0.04, FELT + 0.09, "rv_rail");
        slab(2.68, 2.9, -0.83, 1.1, FELT - 0.04, FELT + 0.09, "rv_rail");
        slab(-1.518, 2.68, -0.84, -0.828, FELT - 0.002, FELT + 0.03, "hd_gold");     // 2 mm vor dem Polster (v2.04: lag bündig)
        slab(-1.52, 2.68, 1.04, 1.1, FELT - 0.06, FELT + 0.02, "rv_wood");
        slab(-1.52, 2.68, 1.04, 1.1, FELT + 0.02, FELT + 0.03, "hd_gold");
        // --- Kessel: Gehäuse (Zwölfeck), Kessel aus 16 Segmenten mit Tiefe, Rotor in der Mulde, Drehkreuz, Kugel ---
        Location wc = local(WU, WV, 0);
        // Gehäuse: zwölf Seitenteile tangential und ein Füllstück, Oberkante knapp unter dem Filz (Kessel sitzt darauf)
        display(wc.clone().add(0, FELT - 0.12, 0), "m_rv_wood", base(), new Vector3f(0.84f, 0.16f, 0.84f), "static");
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(30 * k);
            Vector3f d = rotate(new Vector3f((float) Math.sin(a), 0, (float) -Math.cos(a)));
            // Seitenteile überlappen sich an den Ecken: abwechselnd 1,2 / 2,4 mm höher, sonst flackern die Stöße (v2.04)
            Location at = wc.clone().add(d.x * 0.665, FELT - 0.12 + ZF * (1 + k % 2), d.z * 0.665);
            display(at, "m_rv_wood", yawTo(d), new Vector3f(0.44f, 0.16f, 0.27f), "static");
        }
        // Kessel: Randwand mit Goldlippe, geneigte Kugelbahn, Schräge zum Rotor, acht Rauten (jedes zweite Segment)
        for (int k = 0; k < 16; k++) {
            Quaternionf q = base().rotateY((float) Math.toRadians(22.5 * k));
            // Nachbar-Segmente überlappen sich am Rand (gleiche Oberkante): jedes zweite 1,2 mm höher (v2.04)
            display(local(WU, WV, FELT + ZF * (k % 2)), k % 2 == 0 ? "rv_bowlseg_d" : "rv_bowlseg", q, new Vector3f(BOWL, BOWL, BOWL), "static");
        }
        rotor = display(local(WU, WV, H_ROTOR), "rv_rotor", rotorRot(rho), new Vector3f(ROTOR, ROTOR, ROTOR), "rotor");
        turret = display(local(WU, WV, H_ROTOR + CONE_TOP + TURRET * 0.5), "rv_turret", rotorRot(rho),
                new Vector3f(TURRET, TURRET, TURRET), "rotor");
        ball = display(local(WU, WV, H_BALL), "rv_ball", new Quaternionf(), new Vector3f(BALL, BALL, BALL), "ball");
        ball.setViewRange(0.5f);
        placeBall(ballOffset(wheelAngle(ballPocket) + rho, R_POCKET, ballLift(false)), 0);
        // --- Croupier mit Chip-Ablage ---
        Location feet = local(CU, CV, 0);
        float cy = yaw + 180f;
        feet.setYaw(cy);
        croupier = new DealerAnim(plugin, w, feet, cy, e -> mark(e, "dealer"),
                hb -> hb.getPersistentDataContainer().set(click, PersistentDataType.STRING, "croupier"));
        slab(RACK_U - 0.64, RACK_U + 0.64, RACK_V - 0.1, RACK_V + 0.1, FELT - 0.003, FELT + 0.035, "hd_dark");
        slab(RACK_U - 0.64, RACK_U + 0.64, RACK_V - 0.11, RACK_V - 0.1, FELT, FELT + 0.045, "hd_gold");
        // Chip-Rack (v2.07): je Spielerfarbe eine Röhre (goldene Stege), Chips hochkant aufgereiht; verlorene Einsätze
        // fliegen in ihre Röhre, Gewinne nimmt der Croupier aus der Röhre der Spielerfarbe (ChipRack)
        for (int i = 0; i <= COLORS.length; i++) {
            double u = RACK_U + (i - COLORS.length / 2.0) * 0.15;
            slab(u - 0.004, u + 0.004, RACK_V - 0.09, RACK_V + 0.09, FELT + 0.035, FELT + 0.05, "hd_gold");
        }
        Location r0 = local(RACK_U, RACK_V, FELT + 0.035);
        Location ru = local(RACK_U + 1, RACK_V, FELT + 0.035), rv = local(RACK_U, RACK_V + 1, FELT + 0.035);
        bankRack = new ChipRack(plugin, r0, new Vector3f((float) (ru.getX() - r0.getX()), 0, (float) (ru.getZ() - r0.getZ())),
                new Vector3f((float) (rv.getX() - r0.getX()), 0, (float) (rv.getZ() - r0.getZ())), 0.15, 0.18, RACK_CHIP, COLORS, 6,
                (at, color, rot, sc) -> display(at, "c3chip_" + color, rot, new Vector3f(sc, sc, sc), "rack"));
        bankRack.build();
        // (v2.04: die Anzeigetafel an der Kesselseite ist entfernt, Wunsch des Besitzers; die letzten Zahlen stehen im
        // Text über dem Tisch)
        // --- Texte ---
        status = text(local(CU, CV, 2.4), 0.8f, Display.Billboard.VERTICAL, null, "status");
        status.setViewRange(0.4f);
        betsText = text(local(1.7, 1.25, 1.55), 0.5f, Display.Billboard.VERTICAL, null, "bets");
        betsText.setViewRange(0.25f);
        // --- Klickflächen: Tableau (10 Kacheln je 0,8), Kessel ---
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 2; j++) {
                hitbox(local(LAY_U0 + 0.4 + 0.8 * i, LAY_V0 + 0.4 + 0.8 * j, FELT - 0.05), 0.8f, 0.14f, "layout");
            }
        }
        hitbox(local(WU, WV, FELT - 0.12), 1.6f, 0.4f, "wheel");
        startWheel();
    }

    /** Dreht einen Vektor aus dem Kessel-Modellraum (Modell-+X = Croupier) in die Welt. */
    private Vector3f rotate(Vector3f v) {
        return base().transform(v);
    }

    public RouletteTable tier(TableTier t) {
        this.tier = t == null ? TableTier.GRUEN : t;
        return this;
    }

    public TableTier tier() {
        return tier;
    }

    private String feltSuffix() {
        return switch (tier) {
            case GRUEN -> "";
            case ROT -> "_red";
            case VIOLETT -> "_violet";
        };
    }

    public void removeAll() {
        if (wheelTask != null) {
            wheelTask.cancel();
            wheelTask = null;
        }
        if (croupier != null) {
            croupier.remove();
            croupier = null;
        }
        if (origin != null && origin.getWorld() != null) {
            for (Entity e : origin.getWorld().getNearbyEntities(origin, 5, 4, 5)) {
                if (e.getPersistentDataContainer().has(tag, PersistentDataType.STRING)) e.remove();
            }
        }
        stacks.clear();
        payouts.clear();
        winFrame.clear();
        blitzMarks.clear();
        hover.clear();
        hoverKey.clear();
        status = null;
        betsText = null;
        rotor = null;
        ball = null;
        dolly = null;
        // Abbau mitten im Wurf: kein Rückruf mehr (das Spiel erstattet offene Einsätze selbst, siehe RouletteGame.abortRound)
        spinTick = -1;
        thr = null;
        onLand = null;
        onDone = null;
    }

    // =================================================================================================================
    // Tableau: Plätze, Treffer unter dem Fadenkreuz
    // =================================================================================================================

    /** Rechteck eines Platzes im Bild (x0, y0, x1, y1), null wenn es ihn nicht gibt. */
    private static int[] rect(Spot s) {
        int p = s.param();
        return switch (s.kind()) {
            case "ZAHL" -> {
                if (p == 0) yield new int[]{X_NUM, Y_ZERO, X_END, Y_NUM};
                if (p < 0 || p > 36) yield null;
                int r = (p - 1) / 3, c = (p - 1) % 3;
                yield new int[]{X_NUM + c * CELL_W, Y_NUM + r * ROW_H, X_NUM + (c + 1) * CELL_W, Y_NUM + (r + 1) * ROW_H};
            }
            case "KOLONNE" -> p < 1 || p > 3 ? null
                    : new int[]{X_NUM + (p - 1) * CELL_W, Y_COL, X_NUM + p * CELL_W, Y_END};
            case "DUTZEND" -> p < 1 || p > 3 ? null
                    : new int[]{X_DOZ, Y_NUM + (p - 1) * 4 * ROW_H, X_NUM, Y_NUM + p * 4 * ROW_H};
            default -> {
                int k = switch (s.kind()) {
                    case "NIEDRIG" -> 0;
                    case "GERADE" -> 1;
                    case "ROT" -> 2;
                    case "SCHWARZ" -> 3;
                    case "UNGERADE" -> 4;
                    case "HOCH" -> 5;
                    default -> -1;
                };
                yield k < 0 ? null : new int[]{X_OUT, Y_NUM + k * 2 * ROW_H, X_DOZ, Y_NUM + (k + 1) * 2 * ROW_H};
            }
        };
    }

    /** Mitte eines Platzes auf dem Filz. */
    public Location spotCenter(Spot s) {
        int[] r = rect(s);
        if (r == null || origin == null) return null;
        return local(LAY_U0 + (r[1] + r[3]) / 2.0 * PX, LAY_V0 + (r[0] + r[2]) / 2.0 * PX, FELT);
    }

    /** Größe eines Platzes (quer v, längs u) in Blöcken. */
    private double[] spotSize(Spot s) {
        int[] r = rect(s);
        return r == null ? new double[]{0.2, 0.2} : new double[]{(r[2] - r[0]) * PX, (r[3] - r[1]) * PX};
    }

    /** Platz, auf den der Spieler schaut (Strahl vom Auge auf die Filzebene), oder null. */
    public Spot spotAt(Location eye, Vector dir) {
        if (origin == null || eye.getWorld() == null || !eye.getWorld().equals(origin.getWorld())) return null;
        double planeY = origin.getY() + FELT;
        if (dir.getY() >= -0.02 || eye.getY() <= planeY) return null;
        double t = (planeY - eye.getY()) / dir.getY();
        if (t > 5.5) return null;
        double hx = eye.getX() + dir.getX() * t - origin.getX(), hz = eye.getZ() + dir.getZ() * t - origin.getZ();
        Vector3f r = right(), f = fwd();
        double u = hx * r.x + hz * r.z, v = hx * f.x + hz * f.z;
        double px = (v - LAY_V0) / PX, py = (u - LAY_U0) / PX;
        if (px >= X_NUM && px < X_END) {
            int c = (int) ((px - X_NUM) / CELL_W);
            if (py >= Y_ZERO && py < Y_NUM) return new Spot("ZAHL", 0);
            if (py >= Y_NUM && py < Y_COL) return new Spot("ZAHL", 3 * (int) ((py - Y_NUM) / ROW_H) + c + 1);
            if (py >= Y_COL && py < Y_END) return new Spot("KOLONNE", c + 1);
            return null;
        }
        if (py < Y_NUM || py >= Y_COL) return null;
        if (px >= X_DOZ && px < X_NUM) return new Spot("DUTZEND", (int) ((py - Y_NUM) / (4 * ROW_H)) + 1);
        if (px >= X_OUT && px < X_DOZ) {
            String[] out = {"NIEDRIG", "GERADE", "ROT", "SCHWARZ", "UNGERADE", "HOCH"};
            return new Spot(out[(int) ((py - Y_NUM) / (2 * ROW_H))], 0);
        }
        return null;
    }

    /** Goldener Rahmen um das Feld unter dem Fadenkreuz, nur für diesen Spieler sichtbar (null = aus). */
    public void hover(Player p, Spot s) {
        UUID id = p.getUniqueId();
        if (s == null || origin == null) {
            hoverOff(id);
            return;
        }
        if (s.key().equals(hoverKey.get(id))) return;
        hoverKey.put(id, s.key());
        ItemDisplay[] f = hover.get(id);
        if (f == null || !f[0].isValid()) {
            f = new ItemDisplay[4];
            for (int i = 0; i < 4; i++) {
                ItemDisplay d = display(spotCenter(s), "m_hd_gold_lit", base(), new Vector3f(0.01f), "hover", true);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(2);
                d.setInterpolationDuration(2);
                p.showEntity(plugin, d);
                f[i] = d;
            }
            hover.put(id, f);
        }
        frame(f, s, 0.006);
    }

    public void hoverOff(UUID id) {
        hoverKey.remove(id);
        ItemDisplay[] f = hover.remove(id);
        if (f != null) for (ItemDisplay d : f) if (d != null) d.remove();
    }

    /** Legt vier dünne Leisten als Rahmen um einen Platz. */
    private void frame(ItemDisplay[] f, Spot s, double lift) {
        Location c = spotCenter(s);
        if (c == null) return;
        double[] sz = spotSize(s);
        double dv = sz[0] / 2, du = sz[1] / 2;
        float th = 0.014f, h = 0.006f, h2 = h + 2 * (float) ZF;   // Querleisten dicker: Ecken überlappen (v2.04)
        Vector3f r = right(), fw = fwd();
        double[][] pos = {{0, dv}, {0, -dv}, {du, 0}, {-du, 0}};
        float[][] scale = {{th, h, (float) (sz[1] + th)}, {th, h, (float) (sz[1] + th)}, {(float) (sz[0] + th + 0.003), h2, th}, {(float) (sz[0] + th + 0.003), h2, th}};
        for (int i = 0; i < 4; i++) {
            ItemDisplay d = f[i];
            if (d == null || !d.isValid()) continue;
            Location at = c.clone().add(r.x * pos[i][0] + fw.x * pos[i][1], lift, r.z * pos[i][0] + fw.z * pos[i][1]);
            d.teleport(at);
            d.setInterpolationDelay(0);
            d.setTransformation(new Transformation(new Vector3f(), base(), new Vector3f(scale[i][0], scale[i][1], scale[i][2]),
                    new Quaternionf()));
        }
    }

    // =================================================================================================================
    // Chips
    // =================================================================================================================

    /** Ein Chip in Spielerfarbe auf einen Platz; fällt aus 10 cm Höhe auf den Stapel. */
    public void chip(Spot s, String color) {
        Location c = spotCenter(s);
        if (c == null) return;
        LinkedHashMap<String, List<ItemDisplay>> at = stacks.computeIfAbsent(s.key(), k -> new LinkedHashMap<>());
        List<ItemDisplay> st = at.computeIfAbsent(color, k -> new ArrayList<>());
        st.removeIf(d -> !d.isValid());
        if (st.size() >= 12) return;
        int slot = new ArrayList<>(at.keySet()).indexOf(color);
        Location base = stackBase(s, c, slot);
        // Stapel verschiedener Spieler überlappen sich auf kleinen Feldern: je Stapel 1,2 mm höher (v2.04, Z-Fighting)
        Location place = base.clone().add((rnd.nextDouble() - 0.5) * 0.008, BlackjackTable.chipStep(CHIP) * (st.size() + 0.5) + ZF * (slot % 3),
                (rnd.nextDouble() - 0.5) * 0.008);
        ItemDisplay d = place.getWorld().spawn(place, ItemDisplay.class, e -> {
            e.setItemStack(BlackjackTable.model("c3chip_" + color));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(0, 0.1f, 0), new Quaternionf().rotateY(rnd.nextFloat() * 6.28f),
                    new Vector3f(CHIP, CHIP, CHIP), new Quaternionf()));
            e.setViewRange(0.6f);
            mark(e, "chip");
        });
        st.add(d);
        later(1, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(3);
            Transformation t = d.getTransformation();
            d.setTransformation(new Transformation(new Vector3f(), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
        });
    }

    /** Stapel-Ort je Farbe auf einem Platz: erster mittig, weitere versetzt (wie der Croupier sie nebeneinanderstellt). */
    private Location stackBase(Spot s, Location c, int slot) {
        if (slot <= 0) return c.clone();
        double[] sz = spotSize(s);
        double[][] off = {{0, 0}, {0, 0.6}, {0, -0.6}, {0.55, 0.3}, {-0.55, 0.3}, {0.55, -0.3}, {-0.55, -0.3}, {0, 0.3}};
        double[] o = off[Math.min(slot, off.length - 1)];
        double dv = Math.max(0.02, sz[0] / 2 - 0.08) * o[0], du = Math.max(0.03, sz[1] / 2 - 0.08) * o[1];
        Vector3f r = right(), f = fwd();
        return c.clone().add(r.x * du + f.x * dv, 0, r.z * du + f.z * dv);
    }

    /** Nimmt die Chips einer Farbe von einem Platz (Spieler zieht sie zu sich). */
    public void take(Spot s, String color) {
        LinkedHashMap<String, List<ItemDisplay>> at = stacks.get(s.key());
        if (at == null) return;
        List<ItemDisplay> st = at.remove(color);
        if (at.isEmpty()) stacks.remove(s.key());
        if (st != null) slide(st, d -> d.getLocation().add(fwdVec(-0.9)), 7);
    }

    private Vector fwdVec(double v) {
        Vector3f f = fwd();
        return new Vector(f.x * v, 0, f.z * v);
    }

    /** Lässt Chips gleiten (Teleport mit Übergang) und entfernt sie danach. */
    private void slide(List<ItemDisplay> list, java.util.function.Function<ItemDisplay, Location> to, int ticks) {
        int k = 0;
        for (ItemDisplay d : list) {
            if (!d.isValid()) continue;
            int delay = k++ / 3;
            later(delay, () -> {
                if (!d.isValid()) return;
                d.setTeleportDuration(ticks);
                d.teleport(to.apply(d));
            });
            later(delay + ticks + 1, d::remove);
        }
    }

    public void clearChips() {
        for (Map<String, List<ItemDisplay>> m : stacks.values()) for (List<ItemDisplay> l : m.values()) for (ItemDisplay d : l) d.remove();
        stacks.clear();
        for (ItemDisplay d : payouts) d.remove();
        payouts.clear();
    }

    /** Verlorene Plätze: Croupier zieht die Chips zur Ablage. Liefert einen der Orte (für die Geste) oder null. */
    public Location collect(Predicate<Spot> losing) {
        Location any = null;
        for (Iterator<Map.Entry<String, LinkedHashMap<String, List<ItemDisplay>>>> it = stacks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, LinkedHashMap<String, List<ItemDisplay>>> e = it.next();
            String[] k = e.getKey().split(":");
            Spot s = new Spot(k[0], Integer.parseInt(k[1]));
            if (!losing.test(s)) continue;
            it.remove();
            if (any == null) any = spotCenter(s);
            // die Chips fliegen in die Röhre ihrer Farbe und bleiben dort stehen (v2.07)
            for (Map.Entry<String, List<ItemDisplay>> c : e.getValue().entrySet()) {
                int ci = bankRack == null ? 0 : bankRack.tubeOf(c.getKey());
                int n = 0;
                for (ItemDisplay d : c.getValue()) {
                    if (bankRack != null) bankRack.deposit(d, ci, 1 + n++ / 2);
                    else d.remove();
                }
            }
        }
        return any;
    }

    /** Gewinn: Croupier schiebt neben jeden Gewinnstapel einen Stapel aus der Ablage (count Chips). */
    public void pay(Spot s, String color, int count) {
        LinkedHashMap<String, List<ItemDisplay>> at = stacks.get(s.key());
        if (at == null || !at.containsKey(color)) return;
        List<ItemDisplay> st = at.get(color);
        if (st.isEmpty() || !st.get(0).isValid()) return;
        Location base = st.get(0).getLocation();
        Vector3f r = right();
        Location target = base.clone().add(r.x * 0.12, 0, r.z * 0.12);
        if (bankRack == null) return;
        // der Croupier nimmt die Chips aus der Röhre der Spielerfarbe (v2.07): das Rack wird sichtbar leerer
        int t = bankRack.tubeOf(color);
        int n = Math.max(1, Math.min(10, count));
        for (int i = 0; i < n; i++) {
            Location end = target.clone().add(0, BlackjackTable.chipStep(CHIP) * (i + 0.5), 0);
            ItemDisplay d = bankRack.withdraw(t);
            d.setViewRange(0.6f);
            d.setTransformation(new Transformation(new Vector3f(), d.getTransformation().getLeftRotation(),
                    new Vector3f(CHIP, CHIP, CHIP), new Quaternionf()));
            payouts.add(d);
            bankRack.fly(d, end, rnd.nextFloat() * 6.28f, 1 + i / 2, 8);
        }
    }

    /** Rundenende: alle Chips gehen zu den Spielern (über die Reling) und verschwinden. */
    public void sweepToPlayers() {
        List<ItemDisplay> all = new ArrayList<>(payouts);
        for (Map<String, List<ItemDisplay>> m : stacks.values()) for (List<ItemDisplay> l : m.values()) all.addAll(l);
        stacks.clear();
        payouts.clear();
        Vector back = fwdVec(-1.0);
        slide(all, d -> d.getLocation().add(back).add(0, 0.12, 0), 8);
    }

    /** Alle Plätze mit Chips (für den Neuaufbau). */
    public boolean hasChips() {
        return !stacks.isEmpty();
    }

    // =================================================================================================================
    // Gewinnzahl, Blitz-Zahlen, Texte, Tafel
    // =================================================================================================================

    /** Marker auf die Gewinnzahl (auf den Chips, falls welche liegen) und Goldrahmen um das Feld. */
    public void dolly(int number) {
        clearWinner();
        Spot s = new Spot("ZAHL", number);
        Location c = spotCenter(s);
        if (c == null) return;
        double top = 0;
        LinkedHashMap<String, List<ItemDisplay>> at = stacks.get(s.key());
        if (at != null && !at.isEmpty()) {
            List<ItemDisplay> first = at.values().iterator().next();
            top = BlackjackTable.chipStep(CHIP) * first.size();
        }
        float sc = 0.17f;
        Location where = c.clone().add(0, top + sc * 0.5 + 0.002, 0);
        dolly = display(where.clone().add(0, 0.25, 0), "rv_dolly", new Quaternionf(), new Vector3f(sc, sc, sc), "dolly");
        dolly.setBrightness(new Display.Brightness(15, 15));
        later(1, () -> {
            if (dolly == null || !dolly.isValid()) return;
            dolly.setTeleportDuration(5);
            dolly.teleport(where);
        });
        ItemDisplay[] f = new ItemDisplay[4];
        for (int i = 0; i < 4; i++) {
            f[i] = display(c, "m_hd_gold_lit", base(), new Vector3f(0.01f), "win");
            f[i].setBrightness(new Display.Brightness(15, 15));
            winFrame.add(f[i]);
        }
        frame(f, s, 0.004);
    }

    /** Rahmen blinken lassen (an/aus). */
    public void winnerBlink(boolean on) {
        for (ItemDisplay d : winFrame) {
            if (d.isValid()) d.setItemStack(on ? BlackjackTable.model("m_hd_gold_lit") : new ItemStack(Material.AIR));
        }
    }

    public void clearWinner() {
        if (dolly != null) dolly.remove();
        dolly = null;
        for (ItemDisplay d : winFrame) d.remove();
        winFrame.clear();
    }

    /** Blitz-Zahlen: leuchtender Rahmen und „⚡x3“ über dem Feld (leere Map = aus). */
    public void blitz(Map<Integer, Integer> numbers) {
        for (Entity e : blitzMarks) e.remove();
        blitzMarks.clear();
        for (Map.Entry<Integer, Integer> e : numbers.entrySet()) {
            Spot s = new Spot("ZAHL", e.getKey());
            Location c = spotCenter(s);
            if (c == null) continue;
            ItemDisplay[] f = new ItemDisplay[4];
            for (int i = 0; i < 4; i++) {
                f[i] = display(c, "m_hd_gold_lit", base(), new Vector3f(0.01f), "blitz");
                f[i].setBrightness(new Display.Brightness(15, 15));
                f[i].setGlowing(true);
                f[i].setGlowColorOverride(Color.fromRGB(0xFFE070));
                blitzMarks.add(f[i]);
            }
            frame(f, s, 0.003);
            TextDisplay t = text(c.clone().add(0, 0.2, 0), 0.42f, Display.Billboard.CENTER, null, "blitz");
            t.text(Component.text("⚡x" + e.getValue(), TextColor.color(0xFFE070)).decorate(TextDecoration.BOLD));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setViewRange(0.25f);
            blitzMarks.add(t);
        }
    }

    public void center(Component text) {
        if (status != null && status.isValid()) status.text(text);
    }

    public void bets(Component text) {
        if (betsText != null && betsText.isValid()) betsText.text(text);
    }

    // =================================================================================================================
    // Kessel: Rotor, Kugel, Wurf
    // =================================================================================================================

    private static final double SLOT = 360.0 / 37;

    /** Winkel der Fach-Mitte i im Rotor-Bild (Grad im Uhrzeigersinn von oben, 0 = Modell -Z). */
    private static double wheelAngle(int i) {
        return i * SLOT;
    }

    private Quaternionf rotorRot(double rhoDeg) {
        return pic().rotateY((float) Math.toRadians(-rhoDeg));
    }

    /** Kugel-Versatz zur Kesselmitte (Weltachsen): Winkel im Uhrzeigersinn, Radius, Höhe über der Kesselebene. */
    private Vector3f ballOffset(double betaDeg, double r, double lift) {
        double b = Math.toRadians(betaDeg);
        return rotate(new Vector3f((float) (r * Math.sin(b)), 0, (float) (-r * Math.cos(b)))).add(0, (float) lift, 0);
    }

    private static double ballLift(boolean onTrack) {
        return onTrack ? Y_TRACK : Y_POCKET;
    }

    private void placeBall(Vector3f offset, int interp) {
        if (ball == null || !ball.isValid()) return;
        ball.setInterpolationDelay(0);
        ball.setInterpolationDuration(interp);
        ball.setTransformation(new Transformation(offset, new Quaternionf(), new Vector3f(BALL, BALL, BALL), new Quaternionf()));
    }

    private void placeRotor(int interp) {
        Quaternionf q = rotorRot(rho);
        for (ItemDisplay d : new ItemDisplay[]{rotor, turret}) {
            if (d == null || !d.isValid()) continue;
            Transformation t = d.getTransformation();
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interp);
            d.setTransformation(new Transformation(t.getTranslation(), q, t.getScale(), new Quaternionf()));
        }
    }

    private void startWheel() {
        if (wheelTask != null) wheelTask.cancel();
        wheelTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::wheelStep, 1L, 1L);
    }

    /** Läuft ein Wurf? */
    public boolean spinning() {
        return spinTick >= 0;
    }

    /**
     * Wurf: Croupier stößt den Rotor an, nach Throw.LAUNCH Ticks rollt die Kugel gegen die Drehrichtung in der Bahn, wird
     * durch Reibung langsamer, fällt ab, wenn die Fliehkraft sie nicht mehr an der Wand hält, trifft manchmal eine Raute,
     * springt über den Zahlenring und hüpft unterschiedlich lange über die Fächer (sofort bis rund 5 s), bis sie im Fach von
     * result liegt. land läuft beim Liegenbleiben, done gut eine Sekunde danach. Die Töne sind echte Aufnahmen und folgen
     * jedem Ereignis (Rollen in der Bahn, Raute, Ring, jeder Aufprall nach Sprunghöhe, Liegenbleiben).
     */
    public void spin(int result, Runnable land, Runnable done) {
        target = position(result);
        onLand = land;
        onDone = done;
        thr = new Throw(rnd, rho, omega, target);
        evIdx = 0;
        spinTick = 0;
        if (croupier != null) croupier.deal(wheel());
    }

    /** Ein Kessel-Tick ohne Scheduler (3D-Vorschau tools/preview3d). */
    public void previewTick() {
        wheelStep();
    }

    private final double[] pose = new double[3];

    private void wheelStep() {
        wheelTick++;
        // Chunk entladen: Anzeigen sind weg, ein laufender Wurf läuft trotzdem zu Ende (Auszahlung)
        if ((rotor == null || !rotor.isValid()) && spinTick < 0) return;
        if (spinTick < 0 || thr == null) {
            // Ruhe: Rotor läuft langsam aus, die Kugel liegt im Fach und dreht mit (alle 10 Ticks, Client glättet)
            omega = Math.max(0.35, omega * Math.exp(-1 / 260.0));
            rho += omega;
            if (wheelTick % 10 == 0) {
                placeRotor(10);
                placeBall(ballOffset(wheelAngle(ballPocket) + rho, R_POCKET, ballLift(false)), 10);
            }
            return;
        }
        int t = spinTick;
        rho = thr.rotor(t);
        placeRotor(2);
        if (t < Throw.LAUNCH) {
            // Croupier nimmt die Kugel aus dem Fach
            placeBall(new Vector3f(0, -0.25f, 0), 2);
        } else {
            thr.pose(t, pose);
            placeBall(ballOffset(pose[0], pose[1], pose[2]), t == Throw.LAUNCH ? 0 : 2);
        }
        Location w = wheel();
        while (evIdx < thr.events.size() && thr.events.get(evIdx).tick() <= t) {
            Ev e = thr.events.get(evIdx++);
            if (w != null && w.getWorld() != null) {
                w.getWorld().playSound(w, "kuby:casino.roulette_" + e.key(), SoundCategory.BLOCKS, e.volume(), e.pitch());
            }
        }
        if (t == thr.landTick && onLand != null) {
            Runnable r = onLand;
            onLand = null;
            r.run();
        }
        spinTick++;
        if (spinTick > thr.end) {
            spinTick = -1;
            ballPocket = target;
            omega = Math.max(0.35, thr.rotorSpeed(thr.end));
            thr = null;
            Runnable d = onDone;
            onDone = null;
            if (d != null) d.run();
        }
    }

    // =================================================================================================================
    // Wurf-Physik (v2.04)
    // =================================================================================================================

    /** Länge der Roll-Aufnahmen im Pack in Sekunden (tools/build_roulette_sfx.py, ROLL_LEN). */
    static final double ROLL_SECONDS = 4.20;
    /** Radius der Kugelmitte, wenn sie eine Raute (Mitte r 0,55, halbe Diagonale 0,04) berührt, und am Zahlenring. */
    private static final double R_DIAMOND = 0.605, R_RING = 0.49;
    /** Kugel-Halbmesser über der Lauffläche. */
    private static final double BALL_R = 0.027;

    /** Höhe der Kugelmitte über dem Filz auf Bahn (r ≥ 0,60) oder Schräge, beide 22,5° geneigt (build_roulette_vegas.py). */
    static double slopeLift(double r) {
        double s = r >= 0.60 ? 0.10 - (0.69 - r) * 0.41421 : 0.0628 - (0.607 - r) * 0.41421;
        return s + BALL_R;
    }

    /** Ein Ton im Wurf: Tick, Ereignis (kuby:casino.roulette_&lt;key&gt;), Lautstärke, Tonhöhe. */
    record Ev(int tick, String key, float volume, float pitch) {
    }

    /**
     * Ein vorausberechneter Wurf, reine Rechnung ohne Bukkit (SELFTEST prüft Hunderte davon). Zeit t in Ticks ab dem
     * Anstoßen, Winkel in Grad im Uhrzeigersinn von oben; die Kugel läuft mit fallendem Winkel, der Rotor mit steigendem.
     * <ol>
     *   <li>Rotor: Croupier stößt an (8 Ticks auf wP = 3,6–4,6°/Tick), danach Auslauf mit 260 Ticks Zeitkonstante.</li>
     *   <li>Bahn: Reibung dω/dt = −A − B·ω² (Rollreibung und Luft, schnell am Anfang stärker). Die Kugel hält sich an der
     *       Randwand, solange ω²·r·cos θ ≥ g·sin θ – mit r 0,655, θ 22,5° und g 9,81 m/s² (1 Block = 1 m) sind das rund
     *       7,1°/Tick; weil keine Bahn perfekt ist, streut der Wert je Wurf um ±0,4. Kurz davor klappert sie auf der Bahn.</li>
     *   <li>Schräge: fällt beschleunigt nach innen (r ~ t²) und wird dabei schneller (Drehimpuls). In gut der Hälfte der Würfe
     *       trifft sie eine der acht Rauten (genau an deren Winkel): springt hoch, wird nach außen gestoßen, verliert viel
     *       Tempo.</li>
     *   <li>Rotor: trifft den Zahlenring (relativ zum Rotor schnell, beide drehen gegeneinander), springt darüber und hüpft
     *       im Bezugssystem des Rotors über die Fächer. Je Aufprall wird der Sprung kürzer (Höhe ~ Dauer²) und langsamer,
     *       manchmal stößt ein Steg sie zurück oder ein Steg wirft sie neu hoch. Die Dauer wird je Wurf gewürfelt:
     *       22 % liegt sofort, 30 % rund 1 s, 30 % rund 3 s, 18 % rund 5 s.</li>
     *   <li>Ziel: das Fach steht vorher fest (RouletteGame würfelt fair). Gesucht wird die Einwurf-Geschwindigkeit v0
     *       (21–33°/Tick, also wie fest der Croupier wirft), bei der die Kugel genau dort liegen bleibt.</li>
     * </ol>
     */
    static final class Throw {
        static final int LAUNCH = 10;
        static final double FA = 0.06, FB = 0.0009, W_CRIT = 7.1;
        static final double K = Math.sqrt(FA / FB), C = Math.sqrt(FA * FB);
        static final double V_MIN = 21, V_MAX = 33, SETTLE = 25;

        final double rho0, wI, wP;
        final int target;
        final double beta0, wc, trackPhase, tdPlain, hopHit, hopHitH, td2, kickOut, entryDur, entryH, entryFrac;
        double entryR, lossHit;
        boolean diamondWanted;
        final double wantBounce;
        final double[] hopDur, hopH, hopR, hopW;
        final boolean[] hopRel;
        // Ergebnis für das gewählte v0
        double v0, th0, sDrop, tDrop, betaDrop, tdUsed, tHit, betaHit, wAfter, tContact, betaContact, wContact;
        double phiContact, we, tRest, phiRest;
        boolean diamond;
        final double[] hopStart, hopPhi0, hopWabs;
        int landTick, end;
        final List<Ev> events = new ArrayList<>();

        Throw(Random rnd, double rho0, double wI, int target) {
            this.rho0 = rho0;
            this.wI = wI;
            this.target = target;
            wP = 3.6 + rnd.nextDouble();
            beta0 = 90 + (rnd.nextDouble() - 0.5) * 50;
            wc = W_CRIT + (rnd.nextDouble() - 0.5) * 0.8;
            trackPhase = rnd.nextDouble() * 6.283;
            tdPlain = 12 + rnd.nextDouble() * 5;
            diamondWanted = rnd.nextDouble() < 0.55;
            hopHit = 5 + rnd.nextDouble() * 3;
            hopHitH = 0.03 + rnd.nextDouble() * 0.03;
            lossHit = 0.4 + rnd.nextDouble() * 0.35;
            td2 = 8 + rnd.nextDouble() * 5;
            kickOut = 0.02 + rnd.nextDouble() * 0.015;
            entryDur = 5 + rnd.nextDouble() * 3;
            entryH = 0.045 + rnd.nextDouble() * 0.025;
            entryFrac = 0.35 + rnd.nextDouble() * 0.25;
            // Springen über den Fächern: wie lange (Ticks), dann Stöße mit abklingender Dauer, Höhe und Geschwindigkeit
            double c = rnd.nextDouble();
            wantBounce = c < 0.22 ? 0 : c < 0.52 ? 14 + rnd.nextDouble() * 12 : c < 0.82 ? 50 + rnd.nextDouble() * 20
                    : 90 + rnd.nextDouble() * 20;
            List<double[]> hl = new ArrayList<>();
            double total = 0, prevR = 0.395;
            boolean first = true;
            while (true) {
                double tau = first ? (wantBounce >= 40 ? 8 + rnd.nextDouble() * 3 : wantBounce >= 10 ? 6 + rnd.nextDouble() * 2
                        : 2.4 + rnd.nextDouble() * 0.8) : 5 + rnd.nextDouble() * 6;
                // erster Stoß: Anteil der Geschwindigkeit beim Eintritt; spätere Stöße: ein Steg wirft sie neu (absolut)
                double w = first ? 0.55 + rnd.nextDouble() * 0.25 : (rnd.nextDouble() < 0.7 ? 1 : -1) * (1.5 + rnd.nextDouble() * 3.5);
                while (tau >= 1.8) {
                    double h = Math.min(0.09, 0.00075 * tau * tau * (0.8 + rnd.nextDouble() * 0.4));
                    double r = h > 0.02 ? 0.37 + rnd.nextDouble() * 0.05 : Math.max(0.38, Math.min(0.41, prevR + (rnd.nextDouble() - 0.5) * 0.01));
                    hl.add(new double[]{tau, h, r, w, first ? 1 : 0});
                    prevR = r;
                    total += tau;
                    tau *= 0.62 + rnd.nextDouble() * 0.18;
                    w *= 0.55 + rnd.nextDouble() * 0.25;
                    if (rnd.nextDouble() < 0.15) w = -w * (0.3 + rnd.nextDouble() * 0.2);
                }
                first = false;
                if (total >= wantBounce - 4) break;
            }
            int n = hl.size();
            hopDur = new double[n];
            hopH = new double[n];
            hopR = new double[n];
            hopW = new double[n];
            hopRel = new boolean[n];
            for (int i = 0; i < n; i++) {
                double[] h = hl.get(i);
                hopDur[i] = h[0];
                hopH[i] = h[1];
                hopR[i] = h[2];
                hopW[i] = h[3];
                hopRel[i] = h[4] > 0;
            }
            if (n > 0) hopR[n - 1] = R_POCKET;
            entryR = n == 0 ? R_POCKET : 0.385 + rnd.nextDouble() * 0.03;
            hopStart = new double[n];
            hopPhi0 = new double[n];
            hopWabs = new double[n];
            // Einwurf-Stärke (und bei einer Raute deren Bremswirkung) suchen, bei der die Kugel im Ziel-Fach liegen bleibt
            double v = diamondWanted ? findWithDiamond(rnd) : Double.NaN;
            if (Double.isNaN(v)) {
                diamondWanted = false;
                v = findV0(rnd);
            }
            if (Double.isNaN(v)) v = (V_MIN + V_MAX) / 2;     // kommt praktisch nicht vor (SELFTEST prüft es)
            solve(v);
            // Rest (Bisektion, Bruchteile eines Grades) im letzten Sprung ausgleichen: genau Fach-Mitte
            double res = err(phiRest);
            if (n > 0) hopWabs[n - 1] += res / hopDur[n - 1];
            else we += res / entryDur;
            phiRest -= res;
            landTick = (int) Math.ceil(tRest);
            end = (int) Math.ceil(tRest + SETTLE);
            sounds(rnd);
        }

        private double err(double phi) {
            double d = (phi - wheelAngle(target)) % 360;
            if (d > 180) d -= 360;
            if (d <= -180) d += 360;
            return d;
        }

        private double findV0(Random rnd) {
            List<Double> roots = new ArrayList<>();
            int steps = 600;
            double pv = V_MIN, pe = err(solve(V_MIN));
            for (int i = 1; i <= steps; i++) {
                double v = V_MIN + (V_MAX - V_MIN) * i / steps;
                double e = err(solve(v));
                if (Math.signum(e) != Math.signum(pe) && Math.abs(e - pe) < 90) {
                    double a = pv, b = v, ea = pe;
                    for (int k = 0; k < 40; k++) {
                        double m = (a + b) / 2, em = err(solve(m));
                        if (Math.signum(em) == Math.signum(ea)) {
                            a = m;
                            ea = em;
                        } else {
                            b = m;
                        }
                    }
                    double r = (a + b) / 2;
                    if (Math.abs(err(solve(r))) < 0.5) roots.add(r);
                }
                pv = v;
                pe = e;
            }
            return roots.isEmpty() ? Double.NaN : roots.get(rnd.nextInt(roots.size()));
        }

        /**
         * Mit Raute legt der Treffer den Winkel fest (die Kugel ist genau an der Raute); danach entscheidet, wie viel Tempo
         * sie dort verliert (30–85 %, hängt in echt vom Auftreffwinkel ab). Gesucht wird über Einwurf-Stärke und Verlust.
         */
        private double findWithDiamond(Random rnd) {
            List<double[]> roots = new ArrayList<>();
            int nv = 60, nl = 24;
            for (int i = 0; i < nv; i++) {
                double v = V_MIN + (V_MAX - V_MIN) * (i + 0.5) / nv;
                lossHit = 0.3;
                double pl = 0.3, pe = err(solve(v));
                if (!diamond) continue;
                for (int j = 1; j <= nl; j++) {
                    lossHit = 0.3 + 0.55 * j / nl;
                    double e = err(solve(v));
                    if (Math.signum(e) != Math.signum(pe) && Math.abs(e - pe) < 90) {
                        double a = pl, b = lossHit, ea = pe;
                        for (int k = 0; k < 40; k++) {
                            lossHit = (a + b) / 2;
                            double em = err(solve(v));
                            if (Math.signum(em) == Math.signum(ea)) {
                                a = lossHit;
                                ea = em;
                            } else {
                                b = lossHit;
                            }
                        }
                        lossHit = (a + b) / 2;
                        if (diamond && Math.abs(err(solve(v))) < 0.5) roots.add(new double[]{v, lossHit});
                        lossHit = 0.3 + 0.55 * j / nl;
                    }
                    pl = lossHit;
                    pe = e;
                }
            }
            if (roots.isEmpty()) return Double.NaN;
            double[] r = roots.get(rnd.nextInt(roots.size()));
            lossHit = r[1];
            return r[0];
        }

        double rotor(double t) {
            if (t <= 8) return rho0 + wI * t + (wP - wI) * t * t / 16;
            return rho0 + wI * 8 + (wP - wI) * 4 + wP * 260 * (1 - Math.exp(-(t - 8) / 260));
        }

        double rotorSpeed(double t) {
            return t <= 8 ? wI + (wP - wI) * t / 8 : wP * Math.exp(-(t - 8) / 260);
        }

        private double trackTravel(double s) {
            return Math.log(Math.cos(th0 - C * s) / Math.cos(th0)) / FB;
        }

        /** Rechnet den Wurf für die Einwurf-Geschwindigkeit v (Grad/Tick) und gibt den Ruhewinkel relativ zum Rotor. */
        double solve(double v) {
            v0 = v;
            th0 = Math.atan(v / K);
            sDrop = (th0 - Math.atan(wc / K)) / C;
            tDrop = LAUNCH + sDrop;
            betaDrop = beta0 - trackTravel(sDrop);
            diamond = false;
            if (diamondWanted) {
                double uh = Math.sqrt((R_TRACK - R_DIAMOND) / (R_TRACK - R_RING));
                double f = uh + 0.15 * uh * uh;
                // Raute (Vielfaches von 45°), die mit einer Fallzeit von 9–22 Ticks genau erreicht wird, möglichst nahe 14
                double lo = betaDrop - wc * f * 22, hi = betaDrop - wc * f * 9;
                double best = Double.NaN, bestTd = 0;
                for (double d = Math.ceil(lo / 45) * 45; d <= hi; d += 45) {
                    double td = (betaDrop - d) / (wc * f);
                    if (Double.isNaN(best) || Math.abs(td - 14) < Math.abs(bestTd - 14)) {
                        best = d;
                        bestTd = td;
                    }
                }
                if (!Double.isNaN(best)) {
                    diamond = true;
                    tdUsed = bestTd;
                    tHit = tDrop + uh * tdUsed;
                    betaHit = best;
                    wAfter = wc * (1 + 0.3 * uh) * lossHit;
                    tContact = tHit + td2;
                    betaContact = betaHit - wAfter * td2 * 1.15;
                    wContact = wAfter * 1.3;
                }
            }
            if (!diamond) {
                tdUsed = tdPlain;
                tContact = tDrop + tdUsed;
                betaContact = betaDrop - wc * tdUsed * 1.15;
                wContact = wc * 1.3;
            }
            phiContact = betaContact - rotor(tContact);
            we = (wContact + rotorSpeed(tContact)) * entryFrac;
            double phi = phiContact - we * entryDur, t = tContact + entryDur;
            for (int i = 0; i < hopDur.length; i++) {
                hopStart[i] = t;
                hopPhi0[i] = phi;
                hopWabs[i] = hopRel[i] ? we * hopW[i] : hopW[i];
                phi -= hopWabs[i] * hopDur[i];
                t += hopDur[i];
            }
            tRest = t;
            phiRest = phi;
            return phi;
        }

        /** Kugel zur Zeit t: o[0] Winkel (Welt), o[1] Radius, o[2] Höhe der Mitte über dem Filz. */
        void pose(double t, double[] o) {
            if (t <= tDrop) {
                double s = Math.max(0, t - LAUNCH);
                o[0] = beta0 - trackTravel(s);
                // klappert in den letzten 20 Ticks vor dem Absturz immer stärker auf der Bahn
                double k = Math.max(0, 1 - (tDrop - t) / 20.0);
                double ph = Math.abs(Math.sin(s * 1.9 + trackPhase)) * k * k;
                o[1] = R_TRACK - 0.012 * ph;
                o[2] = slopeLift(o[1]) + 0.01 * ph;
                return;
            }
            if (t < tContact) {
                if (diamond && t >= tHit) {
                    double x = (t - tHit) / td2, xh = (t - tHit) / hopHit;
                    o[1] = R_DIAMOND - (R_DIAMOND - R_RING) * x * x + kickOut * Math.sin(Math.PI * x) * (1 - x);
                    o[0] = betaHit - wAfter * td2 * (x + 0.15 * x * x);
                    o[2] = slopeLift(o[1]) + (xh < 1 ? hopHitH * 4 * xh * (1 - xh) : 0);
                } else {
                    double u = (t - tDrop) / tdUsed;
                    o[1] = R_TRACK - (R_TRACK - R_RING) * u * u;
                    o[0] = betaDrop - wc * tdUsed * (u + 0.15 * u * u);
                    o[2] = slopeLift(o[1]);
                }
                return;
            }
            double phi, r, lift;
            double te = tContact + entryDur;
            if (t < te) {
                // über den Zahlenring ins Fächerfeld
                double x = (t - tContact) / entryDur;
                phi = phiContact - we * (t - tContact);
                r = R_RING + (entryR - R_RING) * x;
                lift = slopeLift(R_RING) + (Y_POCKET - slopeLift(R_RING)) * x + entryH * 4 * x * (1 - x);
            } else if (t < tRest) {
                int i = hopDur.length - 1;
                while (i > 0 && hopStart[i] > t) i--;
                double x = (t - hopStart[i]) / hopDur[i];
                phi = hopPhi0[i] - hopWabs[i] * (t - hopStart[i]);
                double r0 = i == 0 ? entryR : hopR[i - 1];
                r = r0 + (hopR[i] - r0) * x;
                lift = Y_POCKET + hopH[i] * 4 * x * (1 - x);
            } else {
                // liegt: schaukelt kurz im Fach nach
                double since = t - tRest, damp = Math.exp(-since / 4.0);
                phi = phiRest + 1.6 * Math.sin(since * 1.4) * damp;
                r = R_POCKET + 0.006 * Math.sin(since * 1.1) * damp;
                lift = Y_POCKET;
            }
            o[0] = phi + rotor(t);
            o[1] = r;
            o[2] = lift;
        }

        /** Töne zu den Ereignissen (echte Aufnahmen, tools/build_roulette_sfx.py). */
        private void sounds(Random rnd) {
            float rollPitch = (float) Math.max(0.5, Math.min(2.0, ROLL_SECONDS * 20 / (tContact - LAUNCH)));
            events.add(new Ev(LAUNCH, "roll", 1.0f, rollPitch));
            if (diamond) events.add(new Ev((int) Math.round(tHit), "diamond", 0.95f, 0.92f + rnd.nextFloat() * 0.16f));
            events.add(new Ev((int) Math.round(tContact), "hit_big", 1.0f, 0.95f + rnd.nextFloat() * 0.1f));
            if (hopDur.length > 0) {
                landing(rnd, tContact + entryDur, entryH);
                for (int i = 0; i + 1 < hopDur.length; i++) landing(rnd, hopStart[i] + hopDur[i], hopH[i]);
            }
            events.add(new Ev((int) Math.round(tRest), "settle", 0.8f, 0.95f + rnd.nextFloat() * 0.1f));
            events.sort(java.util.Comparator.comparingInt(Ev::tick));
        }

        private void landing(Random rnd, double t, double h) {
            String key = h >= 0.03 ? "hit_big" : h >= 0.008 ? "hit" : "tick";
            float vol = (float) Math.max(0.35, Math.min(1.0, h >= 0.03 ? 0.75 + h * 3 : h >= 0.008 ? 0.55 + h * 8 : 0.4 + h * 20));
            events.add(new Ev((int) Math.round(t), key, vol, 0.9f + rnd.nextFloat() * 0.2f));
        }

        /** Sprungzeit über den Fächern in Ticks (ohne den Sprung über den Ring). */
        double bounceTicks() {
            return tRest - tContact - entryDur;
        }
    }

    /**
     * SELFTEST (v2.04): 400 Würfe mit zufälligem Ziel. Jede Kugel liegt genau in ihrem Fach, Bahn und Sprünge bleiben im
     * Kessel, keine Sprünge zwischen zwei Ticks, Rollton passt (Tonhöhe 0,6–1,6), Sprungdauern decken sofort/1/3/5 s ab.
     * Leere Liste = alles in Ordnung; stats() fasst die Verteilung zusammen.
     */
    public static List<String> selfCheck() {
        List<String> errs = new ArrayList<>();
        Random r = new Random(2026);
        int[] bins = new int[4];
        int diamonds = 0;
        double[] o = new double[3], p = new double[3];
        double minRoll = 99, maxRoll = 0;
        for (int k = 0; k < 400 && errs.size() < 8; k++) {
            int tgt = r.nextInt(37);
            Throw th = new Throw(new Random(r.nextLong()), r.nextDouble() * 360, 0.35 + r.nextDouble() * 0.5, tgt);
            if (Math.abs(th.err(th.phiRest)) > 0.01) errs.add("Wurf " + k + ": liegt " + String.format(java.util.Locale.ROOT, "%.1f", th.err(th.phiRest)) + "° neben Fach " + tgt);
            th.pose(th.end, o);
            double rel = ((o[0] - th.rotor(th.end)) % 360 + 360) % 360;
            int pocket = (int) Math.floor((rel + SLOT / 2) / SLOT) % 37;
            if (pocket != tgt) errs.add("Wurf " + k + ": Kugel am Ende in Fach " + pocket + " statt " + tgt);
            if (th.end > 400 || th.landTick >= th.end) errs.add("Wurf " + k + ": Ablauf " + th.landTick + "/" + th.end);
            double prevX = Double.NaN, prevZ = 0, prevY = 0;
            for (int t = Throw.LAUNCH; t <= th.end; t++) {
                th.pose(t, p);
                if (Double.isNaN(p[0] + p[1] + p[2])) {
                    errs.add("Wurf " + k + ": NaN bei Tick " + t);
                    break;
                }
                if (p[1] < 0.33 || p[1] > 0.69 || p[2] < 0 || p[2] > 0.2) {
                    errs.add("Wurf " + k + " Tick " + t + ": Kugel außerhalb (r " + String.format(java.util.Locale.ROOT, "%.3f h %.3f", p[1], p[2]) + ")");
                    break;
                }
                double b = Math.toRadians(p[0]), x = p[1] * Math.sin(b), z = -p[1] * Math.cos(b);
                if (!Double.isNaN(prevX)) {
                    double d = Math.sqrt((x - prevX) * (x - prevX) + (z - prevZ) * (z - prevZ) + (p[2] - prevY) * (p[2] - prevY));
                    if (d > 0.42) {
                        errs.add("Wurf " + k + " Tick " + t + ": Sprung " + String.format(java.util.Locale.ROOT, "%.2f", d) + " Blöcke");
                        break;
                    }
                }
                prevX = x;
                prevZ = z;
                prevY = p[2];
            }
            float rp = th.events.get(0).pitch();
            minRoll = Math.min(minRoll, rp);
            maxRoll = Math.max(maxRoll, rp);
            if (rp < 0.6f || rp > 1.6f) errs.add("Wurf " + k + ": Rollton-Tonhöhe " + rp);
            double s = th.bounceTicks() / 20.0;
            bins[s < 0.5 ? 0 : s < 2 ? 1 : s < 4 ? 2 : 3]++;
            if (th.diamond) diamonds++;
        }
        for (int i = 0; i < 4; i++) if (bins[i] == 0) errs.add("keine Würfe mit Sprungdauer " + new String[]{"sofort", "~1 s", "~3 s", "~5 s"}[i]);
        if (diamonds == 0) errs.add("keine Raute getroffen");
        lastStats = String.format(java.util.Locale.ROOT, "400 Würfe: sofort %d, ~1 s %d, ~3 s %d, ~5 s %d, Raute %d, Rollton %.2f–%.2f",
                bins[0], bins[1], bins[2], bins[3], diamonds, minRoll, maxRoll);
        return errs;
    }

    /** Zusammenfassung des letzten selfCheck (für das Log). */
    public static String lastStats = "";

    // =================================================================================================================
    // Hilfen
    // =================================================================================================================

    private void later(long ticks, Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, r, Math.max(1, ticks));
    }

    public static boolean red(int n) {
        return switch (n) {
            case 1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36 -> true;
            default -> false;
        };
    }

    public static int numberAt(int pos) {
        return WHEEL[Math.floorMod(pos, 37)];
    }

    public static int position(int number) {
        for (int i = 0; i < 37; i++) if (WHEEL[i] == number) return i;
        return 0;
    }

    /** Liegt der Block im Tisch (Grundfläche, bis 1,2 über dem Boden)? */
    public boolean contains(Location block) {
        if (origin == null || block.getWorld() == null || !block.getWorld().equals(origin.getWorld())) return false;
        double dy = block.getBlockY() + 0.5 - origin.getY();
        if (dy < -1 || dy > 1.3) return false;
        double x = block.getBlockX() + 0.5 - origin.getX(), z = block.getBlockZ() + 0.5 - origin.getZ();
        Vector3f r = right(), f = fwd();
        double u = x * r.x + z * r.z, v = x * f.x + z * f.z;
        return u >= -3.2 && u <= 3.0 && v >= -1.2 && v <= 1.2;
    }
}
