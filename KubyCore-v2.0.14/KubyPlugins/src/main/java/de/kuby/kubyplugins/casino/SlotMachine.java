package de.kuby.kubyplugins.casino;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Ein Slot-Automat aus Anzeigen vor einem gebauten Gehäuse (wie im Clip): drei weiße Walzen als Trommeln aus fünf
 * Flächen (drei mit Symbol), dunkle Stege, goldene Leisten oben und unten, orange Pfeile an der Gewinnlinie. Auf dem
 * Pult davor: Hebel auf Steinsockel links, schwarzer Einsatzknopf rechts, flache Einsatz-Anzeige, vorn am Pult das
 * Schild mit dem Gewinnplan. Darüber das Hologramm, davor ein Hocker.
 *
 * <p>Lage: center = Mitte des Walzenfensters an der Wand (Pult-Oberkante 0,5 darunter), front zeigt zum Spieler,
 * desk = Tiefe des Pults in Blöcken (0 = kein Pult), stool = Hocker (Boden).
 */
public final class SlotMachine {

    public static final String[] SYMBOLS = {"cherry", "lemon", "banana", "bell", "star", "clover", "chip", "coin", "diamond", "seven"};
    /** Kacheln je Walze (Trommel aus 10 Symbolen, 36° je Kachel). */
    public static final int FACES = 10;
    /** Reihenfolge der Symbole auf den drei Walzen (jede Walze anders, jedes Symbol genau einmal). */
    private static final int[][] STRIPS = {
            {0, 3, 9, 1, 6, 2, 8, 4, 7, 5},
            {9, 0, 5, 2, 7, 1, 4, 8, 3, 6},
            {2, 6, 0, 8, 3, 9, 5, 1, 7, 4}};

    public final String id;
    private final NamespacedKey tag;
    private final NamespacedKey click;
    private final Location center;
    private final Location stool;
    private final float frontYaw;
    private final double width;
    private final int desk;

    /** Stil: "klassik" (vor gebautem Gehäuse wie im Clip), "gold" (Goldrausch) oder "neon" (Neon 7). */
    private final String style;
    private final java.util.List<ItemDisplay> lights = new java.util.ArrayList<>();
    /** Walzen: je 10 Kacheln, alle mit ihrer Mitte auf der Walzenachse (drehen auf dem Kreis). */
    private final ItemDisplay[][] reels = new ItemDisplay[3][FACES];
    private final double[] reelAngle = new double[3];
    private final boolean[] reelBlur = new boolean[3];
    /** Alle Anzeigen des Automaten (zum Wackeln). */
    private final java.util.List<Display> parts = new java.util.ArrayList<>();
    private TextDisplay panel;
    private TextDisplay holo;
    private BlockDisplay lever;
    private ArmorStand seat;

    public SlotMachine(String id, Location center, float frontYaw, double width, int desk, Location stool, NamespacedKey tag,
                       NamespacedKey click) {
        this(id, center, frontYaw, width, desk, stool, tag, click, "klassik");
    }

    public SlotMachine(String id, Location center, float frontYaw, double width, int desk, Location stool, NamespacedKey tag,
                       NamespacedKey click, String style) {
        this.style = style == null ? "klassik" : style;
        this.id = id;
        this.center = center.clone();
        this.frontYaw = frontYaw;
        this.width = width;
        this.desk = desk;
        this.stool = stool == null ? null : stool.clone();
        this.tag = tag;
        this.click = click;
    }

    public Location center() {
        return center.clone();
    }

    public Location stool() {
        return stool == null ? null : stool.clone();
    }

    public float frontYaw() {
        return frontYaw;
    }

    public double width() {
        return width;
    }

    public String style() {
        return style;
    }

    private Vector3f front() {
        double r = Math.toRadians(frontYaw);
        return new Vector3f((float) -Math.sin(r), 0, (float) Math.cos(r));
    }

    /** Rechts aus Sicht des Spielers, der auf den Automaten schaut. */
    private Vector3f right() {
        Vector3f n = front();
        return new Vector3f(n.z, 0, -n.x);
    }

    private Location off(double r, double up, double f) {
        Vector3f rr = right(), n = front();
        return center.clone().add(rr.x * r + n.x * f, up, rr.z * r + n.z * f);
    }

    private float faceAngle() {
        Vector3f n = front();
        return (float) Math.atan2(n.x, n.z);
    }

    /** Abstand der Walzen (Mitte zu Mitte). */
    private double pitch() {
        return width / 3;
    }

    /** Radius der Walzen-Trommel. */
    private double radius() {
        return Math.min(0.45, pitch() * 0.5);
    }

    /** Pult-Oberkante relativ zur Fenstermitte. */
    private static final double DESK_TOP = -0.5;

    private static ItemStack model(String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("kuby", "casino/" + name));
        it.setItemMeta(meta);
        return it;
    }

    private void mark(Entity e, String role) {
        if (e instanceof Display d) parts.add(d);
        e.setPersistent(false);
        e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id + ":" + role);
    }

    private ItemDisplay box(Location c, Quaternionf q, float w, float h, float d, String material) {
        return c.getWorld().spawn(c, ItemDisplay.class, e -> {
            e.setItemStack(model("m_" + material));
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(q), new Vector3f(w, h, d), new Quaternionf()));
            mark(e, "static");
        });
    }

    /** Block als Anzeige, Mitte bei c, gedreht und skaliert. */
    private BlockDisplay block(Location c, Quaternionf q, Material m, float w, float h, float d) {
        return c.getWorld().spawn(c, BlockDisplay.class, e -> {
            e.setBlock(Bukkit.createBlockData(m));
            Vector3f t = new Vector3f(-w / 2, -h / 2, -d / 2);
            q.transform(t);
            e.setTransformation(new Transformation(t, new Quaternionf(q), new Vector3f(w, h, d), new Quaternionf()));
            mark(e, "static");
        });
    }

    private void interaction(Location at, float w, float h, String what) {
        at.getWorld().spawn(at, Interaction.class, e -> {
            e.setInteractionWidth(w);
            e.setInteractionHeight(h);
            e.setResponsive(true);
            mark(e, "click");
            e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":" + what);
        });
    }

    // =================================================================================================================
    // Aufbau
    // =================================================================================================================

    public void build(String[][] start) {
        if (center.getWorld() == null) return;
        removeAll();
        double s = pitch();
        double rad = radius();
        double apothem = rad * Math.cos(Math.toRadians(18));
        float chord = (float) (2 * rad * Math.sin(Math.toRadians(18)) * 1.03);
        float rw = (float) (s - 0.08);
        float symScale = (float) Math.min(0.3, chord * 0.86);
        Quaternionf face = new Quaternionf().rotateY(faceAngle());
        parts.clear();
        for (int i = 0; i < 3; i++) {
            Location axis = off((i - 1) * s, 0, 0);
            for (int k = 0; k < FACES; k++) {
                String sym = SYMBOLS[STRIPS[i][k]];
                int col = i, fk = k;
                reels[i][k] = center.getWorld().spawn(axis, ItemDisplay.class, e -> {
                    e.setItemStack(model("sr_" + sym));
                    e.setInterpolationDuration(1);
                    mark(e, "reel:" + col + ":" + fk);
                });
            }
            reelBlur[i] = false;
            String mid = start == null ? SYMBOLS[STRIPS[i][0]] : start[i][1];
            reel(i, stopAngle(i, mid), false, 0);
        }
        // Stege zwischen den Walzen, Leisten oben und unten
        float depth = (float) (rad * 0.92);
        // Rückseite 2 mm vor der der Leisten (lag in derselben Ebene, v2.04)
        for (int i = 0; i < 2; i++) box(off((i - 0.5) * s, 0, depth / 2 + 0.002), face, 0.06f, (float) (2 * rad + 0.04), depth, "black");
        float barW = (float) (3 * s + 0.12);
        box(off(0, rad + 0.035, (rad + 0.1) / 2), face, barW, 0.05f, (float) (rad + 0.1), "gold");
        box(off(0, -rad - 0.035, (rad + 0.1) / 2), face, barW, 0.05f, (float) (rad + 0.1), "gold");
        // Pfeile an der Gewinnlinie
        for (int side = -1; side <= 1; side += 2) {
            Location al = off(side * (1.5 * s + 0.05), -0.07, rad + 0.03);
            String arrow = side < 0 ? "▶" : "◀";
            al.getWorld().spawn(al, TextDisplay.class, e -> {
                e.text(Component.text(arrow, TextColor.color(0xF89A1C)));
                e.setBillboard(Display.Billboard.FIXED);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setShadowed(false);
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(face), new Vector3f(0.55f, 0.55f, 0.55f),
                        new Quaternionf()));
                mark(e, "static");
            });
        }
        // Pult: Einsatz-Anzeige flach vorn, Hebel links, Knopf rechts
        double front = desk > 0 ? Math.max(rad + 0.4, desk - 0.08) : rad + 0.5;
        Location pl = off(0, DESK_TOP + 0.012, front);
        panel = pl.getWorld().spawn(pl, TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.FIXED);
            e.setBackgroundColor(Color.fromARGB(225, 10, 24, 28));
            e.setShadowed(false);
            e.setLineWidth(220);
            // liegt fast flach, Oberkante zur Wand
            Quaternionf q = new Quaternionf(face).rotateX((float) Math.toRadians(-78));
            e.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(0.3f, 0.3f, 0.3f), new Quaternionf()));
            mark(e, "panel");
        });
        interaction(off(0, DESK_TOP - 0.02, front - 0.1), 0.4f, 0.08f, "panel");
        double ctrl = rad + 0.26;
        // Hebel und Knopf an den Enden des Pults: vom Hocker aus verdecken sie keine Symbole
        Location lb = off(-(1.5 * s - 0.17), DESK_TOP + 0.09, ctrl);
        if (style.equals("klassik")) block(lb, face, Material.POLISHED_ANDESITE, 0.34f, 0.18f, 0.3f);
        else box(lb, face, 0.34f, 0.18f, 0.3f, style.equals("gold") ? "hd_gold" : "hd_dark");
        lever = lb.getWorld().spawn(lb.clone().add(0, 0.09, 0), BlockDisplay.class, e -> {
            Switch data = (Switch) Bukkit.createBlockData(Material.LEVER);
            data.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.FLOOR);
            data.setFacing(org.bukkit.block.BlockFace.NORTH);
            // in Ruhe nach hinten gekippt, gezogen zum Spieler
            data.setPowered(true);
            e.setBlock(data);
            // kippt nach vorn und hinten (zum Spieler gezogen)
            Quaternionf q = new Quaternionf(face);
            Vector3f t = new Vector3f(-0.5f, 0, -0.5f);
            q.transform(t);
            e.setTransformation(new Transformation(t, q, new Vector3f(1, 1, 1), new Quaternionf()));
            mark(e, "lever");
        });
        interaction(lb.clone().add(0, -0.09, 0), 0.36f, 0.75f, "lever");
        Location bt = off(1.5 * s - 0.17, DESK_TOP + 0.05, ctrl);
        box(bt, face, 0.3f, 0.1f, 0.24f, "black");
        box(bt.clone().add(0, 0.065, 0), face, 0.17f, 0.04f, 0.13f, "screen");
        interaction(bt.clone().add(0, -0.05, 0), 0.32f, 0.2f, "button");
        // Schild vorn am Pult (Gewinnplan)
        if (desk > 0) {
            Location sl = off(0, DESK_TOP - 0.5, desk + 0.02);
            box(sl, face, (float) Math.min(1.2, 3 * s * 0.45), 0.42f, 0.04f,
                    style.equals("gold") ? "hd_gold" : style.equals("neon") ? "hd_neon_cyan" : "rack");
            box(sl.clone().add(0, 0, 0), face, (float) Math.min(1.12, 3 * s * 0.45 - 0.08), 0.34f, 0.05f,
                    style.equals("gold") ? "hd_dark" : style.equals("neon") ? "hd_purple" : "wood");
            Vector3f n = front();
            interaction(sl.clone().add(n.x * 0.1, -0.25, n.z * 0.1), 0.5f, 0.5f, "sign");
        }
        // Hologramm über dem Dach
        Location hl = off(0, rad + 1.6, Math.max(0.5, front * 0.6));
        holo = hl.getWorld().spawn(hl, TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setLineWidth(300);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6f, 0.6f, 0.6f),
                    new Quaternionf()));
            mark(e, "holo");
        });
        // Hocker
        if (stool != null) {
            Quaternionf sq = new Quaternionf().rotateY(faceAngle());
            String leg = style.equals("gold") ? "hd_gold" : style.equals("neon") ? "hd_dark" : "white";
            String top = style.equals("gold") ? "hd_velvet" : style.equals("neon") ? "hd_purple" : "white";
            box(stool.clone().add(0, 0.03, 0), sq, 0.42f, 0.06f, 0.42f, leg);
            box(stool.clone().add(0, 0.33, 0), sq, 0.12f, 0.56f, 0.12f, leg);
            box(stool.clone().add(0, 0.64, 0), sq, 0.5f, 0.1f, 0.5f, top);
            if (style.equals("neon")) lights.add(box(stool.clone().add(0, 0.585, 0), sq, 0.52f, 0.02f, 0.52f, "hd_neon_cyan"));
            interaction(stool.clone(), 0.6f, 0.75f, "seat");
        }
        if (!style.equals("klassik")) cabinet(face, s, rad, front);
    }

    /**
     * Eigenes Gehäuse für die Stile Goldrausch und Neon: Seitensäulen, Rückwand, Dach, Leuchtschild oben, Zierleisten
     * (leuchten beim Drehen), Pultplatte und Pultfront. Das Walzenfenster bleibt wie beim klassischen Automaten.
     */
    private void cabinet(Quaternionf face, double s, double rad, double front) {
        boolean gold = style.equals("gold");
        String body = gold ? "hd_dark" : "hd_purple";
        String trim = gold ? "hd_gold" : "hd_neon_pink";
        String trim2 = gold ? "hd_gold" : "hd_neon_cyan";
        double W = 3 * s + 0.16;
        double colW = 0.16;
        double top = rad + 0.25;
        double depth = Math.max(rad + 0.12, 0.5);
        // Rückwand hinter den Walzen
        box(off(0, 0.0, -0.06), face, (float) W, (float) (2 * rad + 0.3), 0.06f, body);
        // Seitensäulen von der Pultplatte bis über das Fenster
        double colH = top - DESK_TOP + 0.1;
        for (int side = -1; side <= 1; side += 2) {
            double x = side * (W / 2 + colW / 2);
            box(off(x, DESK_TOP + colH / 2, depth / 2 - 0.05), face, (float) colW, (float) colH, (float) depth, body);
            // Leuchtleiste vorn an der Säule
            lights.add(box(off(x, DESK_TOP + colH / 2, depth - 0.04), face, 0.05f, (float) (colH - 0.1), 0.03f, side < 0 ? trim : trim2));
        }
        // Dach über dem Fenster und Zierleiste darunter
        // Dach 2 mm größer als die Säulen außen: gleiche Seitenflächen flackerten (v2.04)
        box(off(0, top + 0.06, depth / 2 - 0.05), face, (float) (W + 2 * colW + 0.004), 0.12f, (float) depth + 0.004f, body);
        lights.add(box(off(0, top - 0.01, depth - 0.03), face, (float) (W + 2 * colW), 0.03f, 0.03f, trim2));
        // Leuchtschild über dem Dach (senkrecht, Vorderseite zum Spieler)
        double signW = W + 2 * colW;
        double signH = signW * 6 / 16.0;
        Quaternionf upright = new Quaternionf(face).rotateX((float) Math.toRadians(90)).rotateY((float) Math.PI);
        ItemDisplay sign = center.getWorld().spawn(off(0, top + 0.12 + signH / 2, depth / 2), ItemDisplay.class, e -> {
            e.setItemStack(model("sl_" + style + "_top"));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), upright, new Vector3f((float) signW, 1f, (float) signW), new Quaternionf()));
            e.setBrightness(new Display.Brightness(15, 15));
            mark(e, "static");
        });
        lights.add(sign);
        box(off(0, top + 0.12 + signH / 2, depth / 2 - 0.04), face, (float) signW, (float) signH, 0.05f, body);
        lights.add(box(off(0, top + 0.13 + signH, depth / 2), face, (float) signW + 0.004f, 0.03f, 0.08f, trim));
        // Pultplatte und Pultfront
        if (desk > 0) {
            double d = desk;
            // Pultplatte 2 mm breiter und 1,2 mm tiefer: Unterkante lag bündig mit Säulen, Hebel- und Knopfsockel (v2.04)
            box(off(0, DESK_TOP + 0.01 - 0.0012, d / 2 + 0.02), face, (float) (W + 2 * colW + 0.004), 0.02f, (float) d, body);
            lights.add(box(off(0, DESK_TOP + 0.025, d + 0.02), face, (float) (W + 2 * colW), 0.03f, 0.03f, trim));
            Quaternionf frontQ = new Quaternionf(face).rotateX((float) Math.toRadians(90)).rotateY((float) Math.PI);
            float fw = (float) (W + 2 * colW);
            center.getWorld().spawn(off(0, DESK_TOP - 0.5, d + 0.03), ItemDisplay.class, e -> {
                e.setItemStack(model("sl_" + style + "_front"));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                // Fläche 16 x 8: Breite fw, Höhe fw / 2 -> auf 0,96 Höhe begrenzen
                float sc = Math.min(fw, 1.9f);
                e.setTransformation(new Transformation(new Vector3f(), frontQ, new Vector3f(fw, 1f, sc), new Quaternionf()));
                mark(e, "static");
            });
        }
        for (ItemDisplay l : lights) l.setBrightness(new Display.Brightness(15, 15));
    }

    public void removeAll() {
        if (center.getWorld() == null) return;
        for (Entity e : center.getWorld().getNearbyEntities(center, 4, 4, 4)) {
            String v = e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            if (v != null && v.startsWith(id + ":")) {
                if (e instanceof ArmorStand a) a.eject();
                e.remove();
            }
        }
        seat = null;
    }

    public boolean ready() {
        return panel != null && panel.isValid();
    }

    // =================================================================================================================
    // Anzeige
    // =================================================================================================================

    // ---------- Walzen ----------

    /** Symbol auf Kachel k der Walze i. */
    public static String symbolAt(int reel, int k) {
        return SYMBOLS[STRIPS[reel][((k % FACES) + FACES) % FACES]];
    }

    /** Walzenwinkel, bei dem sym genau auf der Gewinnlinie steht (Kachel k bei θ + 36k = 0). */
    public static double stopAngle(int reel, String sym) {
        for (int k = 0; k < FACES; k++) if (symbolAt(reel, k).equals(sym)) return -36.0 * k;
        return 0;
    }

    /** Was die Walze bei diesem Winkel oben, in der Mitte und unten zeigt. */
    public static String[] visible(int reel, double angle) {
        int mid = (int) Math.round(-angle / 36.0);
        return new String[]{symbolAt(reel, mid - 1), symbolAt(reel, mid), symbolAt(reel, mid + 1)};
    }

    public double reelAngle(int reel) {
        return reelAngle[reel];
    }

    /**
     * Walze i auf Winkel angle (Grad; kleiner = Symbole wandern nach unten) stellen, interp = Überblendung in Ticks.
     * Bei hoher Geschwindigkeit zeigen die Kacheln die verwischten Symbole.
     */
    public void reel(int i, double angle, boolean blur, int interp) {
        reelAngle[i] = angle;
        double rad = radius();
        float rw = (float) (pitch() - 0.08);
        float sc = (float) (2 * rad);
        Quaternionf face = new Quaternionf().rotateY(faceAngle());
        boolean swap = blur != reelBlur[i];
        reelBlur[i] = blur;
        for (int k = 0; k < FACES; k++) {
            ItemDisplay d = reels[i][k];
            if (d == null || !d.isValid()) continue;
            double a = Math.toRadians(angle + 36.0 * k);
            // Kachel zeigt nach (hoch sin a, vor cos a); das Modell liegt bei +Z, die Anzeige dreht es um 180°
            Quaternionf q = new Quaternionf(face).rotateX((float) -a).rotateY((float) Math.PI);
            if (swap) d.setItemStack(model("sr_" + symbolAt(i, k) + (blur ? "_blur" : "")));
            // jede zweite Kachel 0,15 % schmaler: die Stirnseiten liegen sonst in einer Ebene und überlappen (v2.04)
            d.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(k % 2 == 0 ? rw : rw * 0.9985f, sc, sc), new Quaternionf()));
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interp);
        }
    }

    /** Für die Kamera: Mitte der Vorderseite von Walze i, ihre Blickrichtung (zum Spieler) und oben. */
    public Location reelFront(int i) {
        Location l = off((i - 1) * pitch(), 0, radius());
        l.setYaw(frontYaw);
        return l;
    }

    public org.bukkit.util.Vector frontVector() {
        Vector3f n = front();
        return new org.bukkit.util.Vector(n.x, 0, n.z);
    }

    /**
     * Gehäuse wackeln lassen (Rumpeln beim Gewinn, kurzer Ruck beim Einrasten): alle Anzeigen springen um ihre
     * Ruheposition, die Stärke klingt ab. amp in Blöcken.
     */
    public void shake(org.bukkit.plugin.Plugin plugin, int ticks, double amp) {
        java.util.Map<Display, Location> base = new java.util.HashMap<>();
        for (Display d : parts) if (d.isValid()) base.put(d, d.getLocation());
        java.util.Random rnd = new java.util.Random();
        for (int t = 0; t <= ticks; t++) {
            final int tt = t;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                double k = tt >= ticks ? 0 : amp * (1 - tt / (double) ticks);
                double dx = (rnd.nextDouble() * 2 - 1) * k, dy = (rnd.nextDouble() * 2 - 1) * k * 0.6, dz = (rnd.nextDouble() * 2 - 1) * k;
                for (var e : base.entrySet()) {
                    Display d = e.getKey();
                    if (!d.isValid()) continue;
                    d.setTeleportDuration(1);
                    d.teleport(e.getValue().clone().add(dx, dy, dz));
                }
            }, t);
        }
    }

    /** Münzregen: Münzen schießen aus dem Automaten und fallen rundherum (nicht aufhebbar, verschwinden wieder). */
    public void coinBurst(org.bukkit.plugin.Plugin plugin, int count) {
        Location from = effectLocation();
        java.util.Random rnd = new java.util.Random();
        Vector3f n = front();
        for (int i = 0; i < count; i++) {
            final int delay = i / 6;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (from.getWorld() == null) return;
                org.bukkit.entity.Item it = from.getWorld().dropItem(from, model(rnd.nextInt(4) == 0 ? "slot_chip" : "slot_coin"), e -> {
                    e.setPickupDelay(Integer.MAX_VALUE);
                    e.setCanMobPickup(false);
                    e.setPersistent(false);
                    e.setInvulnerable(true);
                    double side = (rnd.nextDouble() * 2 - 1) * 0.35, up = 0.35 + rnd.nextDouble() * 0.35, out = 0.05 + rnd.nextDouble() * 0.25;
                    Vector3f r = right();
                    e.setVelocity(new org.bukkit.util.Vector(r.x * side + n.x * out, up, r.z * side + n.z * out));
                });
                plugin.getServer().getScheduler().runTaskLater(plugin, it::remove, 50L + rnd.nextInt(30));
            }, delay);
        }
    }

    public void panel(long[] levels, int selected) {
        if (panel == null || !panel.isValid()) return;
        TextComponent.Builder b = Component.text();
        b.append(Component.text("ᴇɪɴꜱᴀᴛᴢ", NamedTextColor.GRAY)).append(Component.newline());
        for (int i = 0; i < levels.length; i++) {
            if (i > 0) b.append(Component.text("  "));
            String v = levels[i] >= 1000 ? (levels[i] / 1000) + "k" : String.valueOf(levels[i]);
            if (i == selected) {
                b.append(Component.text("▸" + v + "◂", TextColor.color(0xF6E23A)).decorate(TextDecoration.BOLD));
            } else {
                b.append(Component.text(v, TextColor.color(0xC8CCD0)));
            }
        }
        panel.text(b.build());
    }

    public void holo(long jackpot, long min, long max) {
        if (holo == null || !holo.isValid()) return;
        Key chip = Key.key("kuby", "k/i-5");
        TextComponent.Builder b = Component.text();
        b.append(Component.text(String.valueOf(de.kuby.kubyplugins.ui.KitData.SmallIcon.C_RED.c)).font(chip).color(NamedTextColor.WHITE));
        String title = style.equals("gold") ? " GOLDRAUSCH " : style.equals("neon") ? " NEON 7 " : " SLOTS ";
        int tcol = style.equals("gold") ? 0xF2C23A : style.equals("neon") ? 0xFF4FA8 : 0xF0303E;
        b.append(Component.text(de.kuby.kubyplugins.ui.Canvas.titleChars(title, false)).font(Key.key("kuby", "title"))
                .color(TextColor.color(tcol)));
        b.append(Component.text(String.valueOf(de.kuby.kubyplugins.ui.KitData.SmallIcon.C_RED.c)).font(chip).color(NamedTextColor.WHITE));
        // Der Titel-Font ist 23 px hoch (Grundlinie bei 15): eine Leerzeile, damit er nicht in die Jackpot-Zeile ragt
        b.append(Component.newline()).append(Component.newline());
        b.append(Component.text("Jackpot ", NamedTextColor.GRAY)).append(Component.text(CasinoText.num(jackpot), NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD)).append(Component.text(" Chips", NamedTextColor.YELLOW)).append(Component.newline());
        b.append(Component.text("Einsatz " + CasinoText.num(min) + " bis " + CasinoText.num(max), NamedTextColor.WHITE)).append(Component.newline());
        b.append(Component.text("➜ ", NamedTextColor.YELLOW)).append(Component.text("Hebel ziehen", NamedTextColor.WHITE)).append(Component.newline());
        b.append(Component.text("Gewinnplan: Schleichen + Schild", NamedTextColor.GRAY)).append(Component.newline());
        b.append(Component.text("anklicken", NamedTextColor.GRAY));
        holo.text(b.build());
    }

    /** Hebel nach unten (gezogen) oder oben. */
    public void lever(boolean pulled) {
        if (lever == null || !lever.isValid()) return;
        Switch data = (Switch) lever.getBlock();
        data.setPowered(!pulled);
        lever.setBlock(data);
    }

    public ArmorStand seat() {
        if (seat != null && seat.isValid()) return seat;
        if (stool == null) return null;
        // Reiter sitzt 0,6 unter dem Sitzpunkt, Hüfte 0,7 über seinen Füßen: Hüfte genau auf dem Hocker (0,69)
        Location l = stool.clone().add(0, 0.6, 0);
        l.setYaw(frontYaw + 180);
        seat = l.getWorld().spawn(l, ArmorStand.class, e -> {
            e.setInvisible(true);
            e.setMarker(true);
            e.setGravity(false);
            e.setSmall(true);
            e.setInvulnerable(true);
            mark(e, "vehicle");
        });
        return seat;
    }

    public Location effectLocation() {
        return off(0, 0.1, radius() + 0.25);
    }

    /** Redstone-Lampen im Gehäuse über den Walzen (Leuchtkasten) an- oder ausschalten. */
    public void lamps(boolean on) {
        if (center.getWorld() == null) return;
        if (!style.equals("klassik")) {
            // Leuchtleisten: beim Drehen auf die helle Textur (…_lit) umschalten. Früher Glow-Umriss: im echten Client
            // ersetzte der die Textur durch eine flache Farbe (beim Gold-Automaten texturlos, beim Neon dunkelrot).
            for (ItemDisplay l : lights) {
                if (!l.isValid()) continue;
                l.setGlowing(false);
                ItemStack it = l.getItemStack();
                if (it == null || !it.hasItemMeta() || !it.getItemMeta().hasItemModel()) continue;
                String key = it.getItemMeta().getItemModel().getKey();       // casino/m_hd_gold(_lit)
                if (!key.startsWith("casino/m_hd_gold") && !key.startsWith("casino/m_hd_neon_")) continue;
                String base = key.substring("casino/".length()).replace("_lit", "");
                l.setItemStack(model(on ? base + "_lit" : base));
            }
            return;
        }
        double s = pitch();
        int reach = (int) Math.ceil(1.5 * s + 0.5);
        Vector3f r = right(), n = front();
        java.util.Set<org.bukkit.block.Block> seen = new java.util.HashSet<>();
        for (int a = -reach; a <= reach; a++) {
            for (int up = 0; up <= 3; up++) {
                for (int f = -1; f <= 1; f++) {
                    Location l = center.clone().add(r.x * a + n.x * (f - 0.5), radius() + 0.3 + up * 0.8, r.z * a + n.z * (f - 0.5));
                    org.bukkit.block.Block b = l.getBlock();
                    if (!seen.add(b) || b.getType() != Material.REDSTONE_LAMP) continue;
                    if (b.getBlockData() instanceof org.bukkit.block.data.Lightable lt && lt.isLit() != on) {
                        lt.setLit(on);
                        b.setBlockData(lt, false);
                    }
                }
            }
        }
    }
}
