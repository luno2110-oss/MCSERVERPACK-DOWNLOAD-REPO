package de.kuby.kubyplugins.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
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
 *       Rotor in der Bahn, wird langsamer, trifft Rauten, springt über die Stege und bleibt im Fach liegen, das sich
 *       danach mit dem Rotor weiterdreht. Ton kuby:casino.roulette_spin läuft zeitgleich (tools/build_roulette_sfx.py).</li>
 *   <li>Croupier (Gelenkfigur wie beim Blackjack) auf der Kesselseite, Chip-Ablage vor ihm, Anzeigetafel mit den
 *       letzten Zahlen (rot links, schwarz rechts, Null mittig) wie an jedem Tisch in Vegas.</li>
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

    /** Ablauf eines Wurfs in Ticks (passt zum Ton): Einwurf, Kugel verlässt die Bahn, liegt im Fach, Ende. */
    public static final int T_LAUNCH = 10, T_DROP = 120, T_LAND = 150, T_END = 175;

    static final double FELT = 0.86;
    private static final double PX = 0.025;
    private static final double LAY_U0 = -1.35, LAY_V0 = -0.8;
    private static final int LW = 64, LH = 160;
    private static final int X_OUT = 2, X_DOZ = 13, X_NUM = 24, X_END = 57, CELL_W = 11, ROW_H = 10;
    private static final int Y_ZERO = 2, Y_NUM = 15, Y_COL = Y_NUM + 12 * ROW_H, Y_END = Y_COL + 11;
    private static final double WU = -2.3, WV = 0.0;
    private static final float BOWL = 1.5f, ROTOR = 1.0226f, TURRET = 0.24f, BALL = 0.055f;
    private static final double H_ROTOR = FELT + 0.055, H_BOWL = FELT + 0.07;
    private static final double R_TRACK = 0.59, R_POCKET = 0.395;
    private static final double CU = -1.25, CV = 1.58;
    private static final float CHIP = 0.21f;
    private static final double RACK_U = 0.15, RACK_V = 0.935;
    /** Aufschläge der Kugel nach dem Verlassen der Bahn (Ticks ab T_DROP) und Sprunghöhe danach. */
    private static final int[] HITS = {0, 5, 16, 23, 27, 30};
    private static final double[] HOP = {0.05, 0.045, 0.028, 0.014, 0.007};

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
    private final NamespacedKey click;
    private Location origin;
    private float yaw;
    private final Random rnd = new Random();

    private DealerAnim croupier;
    private TextDisplay status;
    private TextDisplay betsText;
    private TextDisplay board;
    private TextDisplay boardStats;
    private ItemDisplay rotor;
    private ItemDisplay turret;
    private ItemDisplay ball;
    private ItemDisplay dolly;
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
    private double[] rhoAt;
    private double beta0, kScale;
    private int target;
    private Runnable onLand;
    private Runnable onDone;

    public RouletteTable(Plugin plugin, NamespacedKey tag) {
        this.plugin = plugin;
        this.tag = tag;
        this.click = new NamespacedKey(plugin, "roulette_click");
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
        slab(-1.35, 2.6, -0.7, 0.85, 0.06, FELT - 0.12, "hd_walnut");
        slab(-1.32, 2.57, -0.67, 0.82, 0, 0.06, "hd_dark");
        slab(-1.35, 2.6, -0.715, -0.7, 0.34, 0.36, "hd_gold");
        slab(-2.95, -1.65, -0.6, 0.6, 0.06, FELT - 0.1, "hd_walnut");
        slab(-2.92, -1.68, -0.57, 0.57, 0, 0.06, "hd_dark");
        slab(-1.55, 2.9, -1.05, 1.1, FELT - 0.12, FELT - 0.06, "hd_walnut");
        slab(-1.52, 2.68, -0.85, 1.04, FELT - 0.06, FELT - 0.003, "rv_felt");
        display(layoutCenter(), "rv_layout", base(), new Vector3f((float) (LW * PX), 1f, (float) (LH * PX)), "static");
        // Polster-Reling auf der Spielerseite und am Ende, Goldkante innen; Croupier-Seite Holzkante mit Goldleiste
        slab(-1.52, 2.9, -1.05, -0.83, FELT - 0.04, FELT + 0.09, "hd_leather");
        slab(2.68, 2.9, -0.83, 1.1, FELT - 0.04, FELT + 0.09, "hd_leather");
        slab(-1.52, 2.68, -0.84, -0.83, FELT - 0.002, FELT + 0.03, "hd_gold");
        slab(-1.52, 2.68, 1.04, 1.1, FELT - 0.06, FELT + 0.02, "hd_walnut");
        slab(-1.52, 2.68, 1.04, 1.1, FELT + 0.02, FELT + 0.03, "hd_gold");
        // --- Kessel: Zwölfeck aus drei gedrehten Quadern, Goldlippe, Kessel-Bild, Rotor, Drehkreuz, Kugel ---
        Location wc = local(WU, WV, 0);
        for (int k = 0; k < 3; k++) {
            Quaternionf q = base().rotateY((float) Math.toRadians(30 * k));
            display(wc.clone().add(0, FELT - 0.04, 0), "m_hd_walnut", q, new Vector3f(1.56f, 0.16f, 1.56f), "static");
        }
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(15 + 30 * k);
            Vector3f d = rotate(new Vector3f((float) Math.sin(a), 0, (float) -Math.cos(a)));
            Location at = wc.clone().add(d.x * 0.765, H_BOWL + 0.018, d.z * 0.765);
            display(at, "m_hd_gold", yawTo(d), new Vector3f(0.425f, 0.036f, 0.03f), "static");
        }
        display(local(WU, WV, H_BOWL), "rv_bowl", base(), new Vector3f(BOWL, 1f, BOWL), "static");
        rotor = display(local(WU, WV, H_ROTOR), "rv_rotor", rotorRot(rho), new Vector3f(ROTOR, 1f, ROTOR), "rotor");
        turret = display(local(WU, WV, H_ROTOR + TURRET * 0.5), "rv_turret", rotorRot(rho),
                new Vector3f(TURRET, TURRET, TURRET), "rotor");
        ball = display(local(WU, WV, H_BOWL), "rv_ball", new Quaternionf(), new Vector3f(BALL, BALL, BALL), "ball");
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
        for (int i = 0; i < COLORS.length; i++) {
            for (int k = 0; k < 3 - (i % 3 == 1 ? 1 : 0); k++) {
                Location c = rackSpot(i).add(0, BlackjackTable.chipStep(CHIP) * k, 0);
                display(c, "c3chip_" + COLORS[i], new Quaternionf().rotateY((float) (k * 0.7 + i)), new Vector3f(CHIP, CHIP, CHIP), "static");
            }
        }
        // --- Anzeigetafel (Marquee) an der Kesselseite, schräg zu den Spielern ---
        Location pole = local(-2.95, 0.8, 0);
        slab(-3.08, -2.82, 0.67, 0.93, 0, 0.03, "hd_dark");
        slab(-2.97, -2.93, 0.78, 0.82, 0.03, 2.34, "hd_gold");
        Vector3f toPlayers = new Vector3f(local(0.4, -1.6, 0).toVector().subtract(pole.toVector()).toVector3f());
        toPlayers.y = 0;
        toPlayers.normalize();
        Quaternionf face = yawTo(toPlayers);
        display(pole.clone().add(0, 1.8, 0), "m_hd_dark", face, new Vector3f(0.62f, 1.06f, 0.07f), "static");
        display(pole.clone().add(0, 2.34, 0), "m_hd_gold", face, new Vector3f(0.66f, 0.03f, 0.08f), "static");
        display(pole.clone().add(0, 1.26, 0), "m_hd_gold", face, new Vector3f(0.66f, 0.03f, 0.08f), "static");
        Location boardAt = pole.clone().add(toPlayers.x * 0.045, 1.31, toPlayers.z * 0.045);
        board = text(boardAt, 0.4f, Display.Billboard.FIXED, face, "board");
        board.setLineWidth(120);
        board.setShadowed(false);
        board.setBrightness(new Display.Brightness(15, 15));
        boardStats = text(local(-2.95, 0.8, 2.42).add(toPlayers.x * 0.05, 0, toPlayers.z * 0.05), 0.3f, Display.Billboard.FIXED, face, "board");
        boardStats.setBrightness(new Display.Brightness(15, 15));
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

    private Location rackSpot(int colorIndex) {
        return local(RACK_U + (colorIndex - 3.5) * 0.15, RACK_V, FELT + 0.035 + BlackjackTable.chipStep(CHIP) * 0.5);
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
        board = null;
        rotor = null;
        ball = null;
        dolly = null;
        // Abbau mitten im Wurf: kein Rückruf mehr (das Spiel erstattet offene Einsätze selbst, siehe RouletteGame.abortRound)
        spinTick = -1;
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
        float th = 0.014f, h = 0.006f;
        Vector3f r = right(), fw = fwd();
        double[][] pos = {{0, dv}, {0, -dv}, {du, 0}, {-du, 0}};
        float[][] scale = {{th, h, (float) (sz[1] + th)}, {th, h, (float) (sz[1] + th)}, {(float) (sz[0] + th), h, th}, {(float) (sz[0] + th), h, th}};
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
        Location place = base.clone().add((rnd.nextDouble() - 0.5) * 0.008, BlackjackTable.chipStep(CHIP) * (st.size() + 0.5),
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
            for (Map.Entry<String, List<ItemDisplay>> c : e.getValue().entrySet()) {
                int ci = Math.max(0, java.util.Arrays.asList(COLORS).indexOf(c.getKey()));
                Location rack = rackSpot(ci).add(0, 0.06, 0);
                slide(c.getValue(), d -> rack, 9);
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
        int ci = Math.max(0, java.util.Arrays.asList(COLORS).indexOf(color));
        Location from = rackSpot(ci).add(0, 0.05, 0);
        int n = Math.max(1, Math.min(10, count));
        for (int i = 0; i < n; i++) {
            Location end = target.clone().add(0, BlackjackTable.chipStep(CHIP) * i, 0);
            ItemDisplay d = from.getWorld().spawn(from, ItemDisplay.class, e -> {
                e.setItemStack(BlackjackTable.model("c3chip_" + color));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(rnd.nextFloat() * 6.28f),
                        new Vector3f(CHIP, CHIP, CHIP), new Quaternionf()));
                e.setViewRange(0.6f);
                mark(e, "chip");
            });
            payouts.add(d);
            later(1 + i / 2, () -> {
                if (!d.isValid()) return;
                d.setTeleportDuration(8);
                d.teleport(end);
            });
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

    /** Anzeigetafel: neueste Zahl oben (fett), rot links, schwarz rechts, Null in der Mitte; darüber heiße Zahlen. */
    public void history(List<Integer> last) {
        if (board == null || !board.isValid()) return;
        TextComponent.Builder b = Component.text();
        b.append(Component.text("KUBY", TextColor.color(0xF2C94C)).decorate(TextDecoration.BOLD));
        for (int i = 0; i < Math.min(9, last.size()); i++) {
            int n = last.get(i);
            String num = (n < 10 ? " " : "") + n;
            String line = n == 0 ? "    " + num + "    " : red(n) ? num + "         " : "         " + num;
            TextColor col = TextColor.color(n == 0 ? 0x3FD55C : red(n) ? 0xFF4A4A : 0xE8E8E8);
            Component c = Component.text(line, col);
            if (i == 0) c = c.decorate(TextDecoration.BOLD);
            b.append(Component.newline()).append(c);
        }
        board.text(b.build());
        if (boardStats != null && boardStats.isValid()) {
            int[] count = new int[37];
            int red = 0, black = 0, n = Math.min(100, last.size());
            for (int i = 0; i < n; i++) {
                int x = last.get(i);
                count[x]++;
                if (x != 0 && red(x)) red++;
                else if (x != 0) black++;
            }
            List<Integer> hot = new ArrayList<>();
            for (int k = 0; k < 3 && n > 0; k++) {
                int best = -1;
                for (int x = 0; x < 37; x++) if (!hot.contains(x) && count[x] > 0 && (best < 0 || count[x] > count[best])) best = x;
                if (best >= 0) hot.add(best);
            }
            TextComponent.Builder s = Component.text();
            s.append(Component.text("HEISS ", TextColor.color(0xFF8A3A)).decorate(TextDecoration.BOLD));
            for (int h : hot) s.append(Component.text(h + " ", TextColor.color(h == 0 ? 0x3FD55C : red(h) ? 0xFF4A4A : 0xE8E8E8)));
            if (n > 0) {
                s.append(Component.newline()).append(Component.text("ROT " + Math.round(100.0 * red / n) + "%  ", TextColor.color(0xFF4A4A)))
                        .append(Component.text("SCHWARZ " + Math.round(100.0 * black / n) + "%", TextColor.color(0xE8E8E8)));
            }
            boardStats.text(s.build());
        }
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
        return base().rotateY((float) Math.toRadians(-rhoDeg));
    }

    /** Kugel-Versatz zur Kesselmitte (Weltachsen): Winkel im Uhrzeigersinn, Radius, Höhe über der Kesselebene. */
    private Vector3f ballOffset(double betaDeg, double r, double lift) {
        double b = Math.toRadians(betaDeg);
        return rotate(new Vector3f((float) (r * Math.sin(b)), 0, (float) (-r * Math.cos(b)))).add(0, (float) lift, 0);
    }

    private static double ballLift(boolean onTrack) {
        return onTrack ? BALL / 2 + 0.002 : H_ROTOR - H_BOWL + BALL / 2 - 0.005;
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
     * Wurf: Croupier stößt den Rotor an, nach T_LAUNCH Ticks rollt die Kugel gegen die Drehrichtung in der Bahn,
     * verlässt sie bei T_DROP, springt über Rauten und Stege und liegt bei T_LAND im Fach von result.
     * land läuft beim Liegenbleiben, done am Ende (T_END).
     */
    public void spin(int result, Runnable land, Runnable done) {
        target = position(result);
        onLand = land;
        onDone = done;
        // Rotorwinkel vorausberechnen: kurz anschieben (8 Ticks), dann langsam auslaufen
        rhoAt = new double[T_END + 1];
        double r = rho, om = omega, decay = Math.exp(-1 / 260.0);
        for (int t = 0; t <= T_END; t++) {
            om = t < 8 ? omega + (5.0 - omega) * (t + 1) / 8.0 : om * decay;
            r += om;
            rhoAt[t] = r;
        }
        // Kugel kommt von der Croupier-Seite (Modell +X = 90°), Geschwindigkeit fällt linear bis zum Liegenbleiben
        beta0 = 90 + (rnd.nextDouble() - 0.5) * 50;
        double fS = travel(T_LAND - T_LAUNCH);
        double want = wheelAngle(target) + rhoAt[T_LAND];
        // Zusatzweg 0..360°, damit die Kugel genau im Fach landet: auf die ganze Bahn verteilt (bis 21 % schneller, unsichtbar)
        double c = ((beta0 - fS - want) % 360 + 360) % 360;
        kScale = (fS + c) / fS;
        spinTick = 0;
        Location w = wheel();
        if (w != null) w.getWorld().playSound(w, "kuby:casino.roulette_spin", SoundCategory.BLOCKS, 1.0f, 1.0f);
        if (croupier != null) croupier.deal(w);
    }

    /** Strecke der Kugel (Grad) nach s Ticks in der Bahn: Geschwindigkeit 24°/Tick, fällt linear auf 0 bei T_LAND. */
    private static double travel(double s) {
        double S = T_LAND - T_LAUNCH, a = 24;
        s = Math.max(0, Math.min(S, s));
        return a * s - a * s * s / (2 * S);
    }

    private void wheelStep() {
        wheelTick++;
        // Chunk entladen: Anzeigen sind weg, ein laufender Wurf läuft trotzdem zu Ende (Auszahlung)
        if ((rotor == null || !rotor.isValid()) && spinTick < 0) return;
        if (spinTick < 0) {
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
        rho = rhoAt[Math.min(t, T_END)];
        placeRotor(2);
        if (t < T_LAUNCH) {
            // Croupier nimmt die Kugel aus dem Fach
            placeBall(new Vector3f(0, -0.2f, 0), 2);
        } else if (t <= T_LAND) {
            double s = t - T_LAUNCH;
            double beta = beta0 - kScale * travel(s);
            double rad = R_TRACK, lift = ballLift(true);
            if (t >= T_DROP) {
                double u = (t - T_DROP) / (double) (T_LAND - T_DROP);
                double e = u * u * (3 - 2 * u);
                rad = R_TRACK + (R_POCKET - R_TRACK) * e;
                lift = ballLift(true) + (ballLift(false) - ballLift(true)) * e;
                int k = t - T_DROP;
                for (int i = 0; i + 1 < HITS.length; i++) {
                    if (k >= HITS[i] && k < HITS[i + 1]) {
                        double x = (k - HITS[i]) / (double) (HITS[i + 1] - HITS[i]);
                        double hop = HOP[i] * 4 * x * (1 - x);
                        lift += hop;
                        rad += hop * 0.6;
                    }
                }
            }
            placeBall(ballOffset(beta, rad, lift), t == T_LAUNCH ? 0 : 2);
        } else {
            // im Fach: dreht mit, wackelt kurz nach
            double since = t - T_LAND;
            double wob = 4.5 * Math.sin(since * 1.3) * Math.exp(-since / 4.0);
            placeBall(ballOffset(wheelAngle(target) + rho + wob, R_POCKET, ballLift(false)), 2);
        }
        if (t == T_LAND && onLand != null) {
            Runnable r = onLand;
            onLand = null;
            r.run();
        }
        spinTick++;
        if (spinTick > T_END) {
            spinTick = -1;
            ballPocket = target;
            omega = Math.max(0.35, (rhoAt[T_END] - rhoAt[T_END - 1]));
            Runnable d = onDone;
            onDone = null;
            if (d != null) d.run();
        }
    }

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
