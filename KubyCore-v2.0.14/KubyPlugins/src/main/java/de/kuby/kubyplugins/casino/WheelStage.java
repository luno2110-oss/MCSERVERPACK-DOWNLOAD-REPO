package de.kuby.kubyplugins.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Das große Glücksrad auf der Bühne (v2.07, Wunsch des Besitzers: „kein Wandbild, ein stehendes großes Rad“). Aufbau in
 * Ebenen wie die Generator-Grafiken: Bühne mit Stufe und Lichterkette, Ständer, das Rad (Scheibe mit Feldern, Rückseite,
 * dicker Goldrand mit 24 Lämpchen, 12 Stifte auf den Feldgrenzen, Nabe), oben die Nadel an ihrem Gelenk (die Stifte
 * drücken sie beim Vorbeilaufen weg, sie schnappt zurück), darüber das Schild „GLÜCKSRAD“ mit Lichterrahmen, vorn zwei
 * Scheinwerfer, rechts das Podest mit dem Hauptgewinn (die Tafel „Letzte Gewinner“ links ist auf Wunsch weg).
 *
 * <p>Koordinaten (mal {@code size}): u rechts, v hoch, f nach vorn (zu den Zuschauern); center = Mitte der Bühne am
 * Boden. Flache Bilder werden wie die Slot-Symbole um 180 Grad gedreht (sonst sähe man sie von hinten, gespiegelt).
 */
public final class WheelStage {

    /** Halbmesser des Rads, Höhe der Radmitte, Bühne. */
    public static final double R = 1.6, HC = 2.85, PLAT_H = 0.35, PLAT_W = 5.2, PLAT_D = 3.0;
    public static final int RIM = 24, PEGS = 12, PLAT_BULBS = 14, SIGN_BULBS = 18;
    private static final float ZF = 0.0024f;
    public static final int[] LOGO = {0xFFE870, 0xF8CC2A, 0xF2A21C, 0xE8622A, 0xD8384A, 0xF8CC2A};
    /** Gelenk der Nadel über der Radmitte (Modell wh_needle: Spitze 0,75 unter dem Gelenk, reicht zwischen die Stifte). */
    private static final double HINGE = R + 0.6;
    /** Stifte: Abstand zur Mitte und Abstand des Würfels im Modell wh_peg (21,5 / 16 Block). */
    private static final double PEG_R = R - 0.12, PEG_MODEL = 21.5 / 16;

    private final String id;
    private final Location center;
    private final float frontYaw;
    private final double size;
    private final NamespacedKey tag;
    private final NamespacedKey click;

    private ItemDisplay disc;
    private ItemDisplay burst;
    private ItemDisplay needle;
    private ItemDisplay prizeItem;
    private final ItemDisplay[] pegs = new ItemDisplay[PEGS];
    private final ItemDisplay[] rimBulbs = new ItemDisplay[RIM];
    private final ItemDisplay[] platBulbs = new ItemDisplay[PLAT_BULBS];
    private final ItemDisplay[] signBulbs = new ItemDisplay[SIGN_BULBS];
    private final ItemDisplay[] spotLens = new ItemDisplay[2];
    private TextDisplay prize;
    private TextDisplay info;
    private TextDisplay jackpotText;
    private final List<Location> lights = new ArrayList<>();
    private double angle;
    private double needleDeg;
    private float podiumYaw;

    public WheelStage(String id, Location center, float frontYaw, double size, NamespacedKey tag, NamespacedKey click) {
        this.id = id;
        this.center = center.clone();
        this.frontYaw = frontYaw;
        this.size = size;
        this.tag = tag;
        this.click = click;
    }

    public String id() {
        return id;
    }

    public Location center() {
        return center.clone();
    }

    public double size() {
        return size;
    }

    public double angle() {
        return angle;
    }

    // ---------- Geometrie ----------

    private Vector3f front() {
        double r = Math.toRadians(frontYaw);
        return new Vector3f((float) -Math.sin(r), 0, (float) Math.cos(r));
    }

    private Vector3f right() {
        Vector3f n = front();
        return new Vector3f(n.z, 0, -n.x);
    }

    public Location at(double u, double v, double f) {
        Vector3f r = right(), n = front();
        double k = size;
        return center.clone().add((r.x * u + n.x * f) * k, v * k, (r.z * u + n.z * f) * k);
    }

    private Quaternionf face() {
        Vector3f n = front();
        return new Quaternionf().rotateY((float) Math.atan2(n.x, n.z));
    }

    /** Drehung um die Achse nach vorn, a Grad gegen den Uhrzeigersinn (aus Sicht der Zuschauer). */
    private Quaternionf roll(double a) {
        Vector3f n = front();
        return new Quaternionf().rotationAxis((float) Math.toRadians(a), n.x, n.y, n.z);
    }

    /** Flaches Bild, a Grad gedreht, Vorderseite zu den Zuschauern. */
    private Quaternionf flat(double a) {
        return roll(a).mul(face()).rotateY((float) Math.PI);
    }

    private void mark(Entity e, String role) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id + ":" + role);
    }

    static ItemStack model(String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("kuby", "casino/" + name));
        it.setItemMeta(meta);
        return it;
    }

    /** Quader (Material m_&lt;mat&gt;) mit Mitte (u, v, f), Größe w × h × d, um die Achse nach vorn um roll gedreht. */
    private ItemDisplay box(double u, double v, double f, double w, double h, double d, double rollRad, String mat) {
        return box(u, v, f, w, h, d, new Quaternionf(face()).rotateZ((float) rollRad), mat);
    }

    private ItemDisplay box(double u, double v, double f, double w, double h, double d, Quaternionf q, String mat) {
        float k = (float) size;
        return center.getWorld().spawn(at(u, v, f), ItemDisplay.class, e -> {
            e.setItemStack(model("m_" + mat));
            e.setTransformation(new Transformation(new Vector3f(), q, new Vector3f((float) w * k, (float) h * k, (float) d * k), new Quaternionf()));
            mark(e, "static");
        });
    }

    private ItemDisplay sprite(double u, double v, double f, double w, String name, Quaternionf q, int light) {
        float s = (float) (w * size);
        return center.getWorld().spawn(at(u, v, f), ItemDisplay.class, e -> {
            e.setItemStack(model(name));
            e.setBrightness(new Display.Brightness(light, light));
            e.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(s, s, s * 0.1f), new Quaternionf()));
            mark(e, "static");
        });
    }

    private TextDisplay text(double u, double v, double f, float scale, int lineWidth, Component c) {
        float k = (float) size;
        return center.getWorld().spawn(at(u, v, f), TextDisplay.class, e -> {
            e.text(c);
            e.setBillboard(Display.Billboard.FIXED);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setLineWidth(lineWidth);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), face(), new Vector3f(scale * k, scale * k, scale * k), new Quaternionf()));
            mark(e, "static");
        });
    }

    private void interaction(double u, double v0, double f, double w, double h) {
        Location l = at(u, v0, f);
        float k = (float) size;
        l.getWorld().spawn(l, Interaction.class, e -> {
            e.setInteractionWidth((float) w * k);
            e.setInteractionHeight((float) h * k);
            e.setResponsive(true);
            mark(e, "click");
            e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":wheel");
        });
    }

    // ---------- Aufbau ----------

    public boolean ready() {
        return disc != null && disc.isValid();
    }

    public void build(Component jackpotName, ItemStack jackpotItem) {
        if (center.getWorld() == null) return;
        removeAll();
        // Bühne: dunkler Sockel, Goldkanten, Stufe vorn, Lichterkette an der Vorderkante
        box(0, PLAT_H / 2, 0, PLAT_W, PLAT_H, PLAT_D, 0, "hd_plinko_dark");
        box(0, PLAT_H + 0.01, PLAT_D / 2 - 0.04 + ZF, PLAT_W - 2 * ZF, 0.04, 0.08, 0, "hd_gold");
        box(0, PLAT_H + 0.01 + ZF, -PLAT_D / 2 + 0.04 - ZF, PLAT_W - 2 * ZF, 0.04, 0.08, 0, "hd_gold");
        for (int s = -1; s <= 1; s += 2) box(s * (PLAT_W / 2 - 0.04 + ZF), PLAT_H + 0.01 + 2 * ZF, 0, 0.08, 0.04, PLAT_D - 0.16, 0, "hd_gold");
        box(0, 0.09, PLAT_D / 2 + 0.25, 2.6, 0.18, 0.5, 0, "hd_plinko_dark");
        box(0, 0.18 + ZF, PLAT_D / 2 + 0.48 + ZF, 2.6 - 2 * ZF, 0.03, 0.04, 0, "hd_gold");
        for (int i = 0; i < PLAT_BULBS; i++) {
            double u = -PLAT_W / 2 + 0.25 + i * (PLAT_W - 0.5) / (PLAT_BULBS - 1);
            platBulbs[i] = box(u, PLAT_H * 0.62, PLAT_D / 2 + 0.01, 0.09, 0.09, 0.03, Math.PI / 4, "hd_gold");
        }
        // Ständer: zwei schräge Beine und eine Säule hinter dem Rad, Achse
        double legLen = Math.hypot(0.95, HC - PLAT_H);
        double legAng = Math.atan2(0.95, HC - PLAT_H);
        for (int s = -1; s <= 1; s += 2) {
            box(s * 0.475, PLAT_H + (HC - PLAT_H) / 2, -0.32, 0.16, legLen, 0.16 - (s + 1) * 2 * ZF, s * legAng, "hd_gold");
        }
        box(0, PLAT_H + (HC - PLAT_H) / 2 - 0.2, -0.45, 0.26, HC - PLAT_H - 0.4, 0.2, 0, "hd_plinko_dark");
        box(0, HC, -0.2, 0.3, 0.3, 0.29, Math.PI / 4, "hd_gold");
        // Strahlenkranz ganz hinten (Tiefe), Rückseite und Scheibe
        burst = sprite(0, HC, -0.3, 2 * (R + 1.05) * 64 / 63.5, "wh_burst", flat(0), 11);
        sprite(0, HC, -0.07, 2 * R * 64 / 58.5, "wh_back", roll(0).mul(face()), 14);
        float ds = (float) (2 * (R - 0.02) * 64 / 58.5 * size);
        disc = center.getWorld().spawn(at(0, HC, 0.02), ItemDisplay.class, e -> {
            e.setItemStack(model("wh_disc_casino"));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), flat(angle), new Vector3f(ds, ds, ds * 0.1f), new Quaternionf()));
            mark(e, "disc");
        });
        // dicker Goldrand aus 24 Stücken, darauf 24 Lämpchen (Lauflicht)
        double rr = R + 0.06;
        double seg = 2 * Math.PI * rr / RIM * 1.04;
        for (int i = 0; i < RIM; i++) {
            double a = 2 * Math.PI * i / RIM;
            box(rr * Math.sin(a), HC + rr * Math.cos(a), 0.0, seg, 0.13, 0.26 + (i % 2) * ZF, -a, "hd_gold");
            rimBulbs[i] = box(rr * Math.sin(a + Math.PI / RIM), HC + rr * Math.cos(a + Math.PI / RIM), 0.135 + (i % 2) * ZF,
                    0.07, 0.07, 0.02, Math.PI / 4 - a, "hd_gold");
        }
        // Stifte auf den Feldgrenzen (Modell mit Abstand zur Mitte: Drehung um die Radmitte)
        for (int i = 0; i < PEGS; i++) {
            int pi = i;
            float k = (float) (size * PEG_R / PEG_MODEL);
            pegs[i] = center.getWorld().spawn(at(0, HC, 0.05), ItemDisplay.class, e -> {
                e.setItemStack(model("wh_peg"));
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTransformation(new Transformation(new Vector3f(), pegRot(pi, angle), new Vector3f(k, k, k), new Quaternionf()));
                mark(e, "peg");
            });
        }
        // Nabe in 3D (achteckiger Goldkörper, Kappe mit Stern, Edelstein-Knopf), steht still
        float hk = (float) (1.05 * size);
        center.getWorld().spawn(at(0, HC, 0.12), ItemDisplay.class, e -> {
            e.setItemStack(model("wh_hub3d"));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), face(), new Vector3f(hk, hk, hk), new Quaternionf()));
            mark(e, "static");
        });
        // Nadel am Gelenk über dem Rad, Halterung
        box(0, HC + HINGE + 0.12, -0.05, 0.42, 0.5, 0.3, 0, "hd_plinko_dark");
        box(0, HC + HINGE, 0.12, 0.2, 0.2, 0.12, Math.PI / 4, "hd_gold_lit");
        float nk = (float) size;
        needle = center.getWorld().spawn(at(0, HC + HINGE, 0.18), ItemDisplay.class, e -> {
            e.setItemStack(model("wh_needle"));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), roll(0).mul(face()), new Vector3f(nk, nk, nk), new Quaternionf()));
            mark(e, "needle");
        });
        // Schild mit Lichterrahmen
        double sv = HC + R + 1.25;
        box(0, sv, -0.05, 3.6, 0.7, 0.12, 0, "hd_plinko_dark");
        box(0, sv + 0.37, 0.02, 3.66 - 2 * ZF, 0.05, 0.06, 0, "hd_gold");
        box(0, sv - 0.37, 0.02, 3.66 - 2 * ZF, 0.05, 0.06, 0, "hd_gold");
        for (int i = 0; i < SIGN_BULBS; i++) {
            boolean top = i < SIGN_BULBS / 2;
            int j = top ? i : i - SIGN_BULBS / 2;
            double u = -1.7 + j * 3.4 / (SIGN_BULBS / 2 - 1);
            signBulbs[i] = box(u, sv + (top ? 0.37 : -0.37), 0.06, 0.08, 0.08, 0.02, Math.PI / 4, "hd_gold");
        }
        // Schriftzug als Bild (Text in der Titelschrift rutschte in manchen Clients zur Seite)
        sprite(0, sv + 0.02, 0.03, 3.62, "wh_logo", flat(0), 15);
        // Scheinwerfer vorn links und rechts
        for (int s = -1; s <= 1; s += 2) {
            double u = s * (PLAT_W / 2 - 0.25);
            box(u, PLAT_H + 0.8, PLAT_D / 2 - 0.3, 0.1, 1.6, 0.1, 0, "hd_plinko_dark");
            Quaternionf q = new Quaternionf(face()).rotateY((float) (s * Math.toRadians(28))).rotateX((float) Math.toRadians(-20));
            box(u, PLAT_H + 1.65, PLAT_D / 2 - 0.3, 0.3, 0.26, 0.36, q, "hd_plinko_dark");
            Quaternionf ql = new Quaternionf(face()).rotateY((float) (s * Math.toRadians(28))).rotateX((float) Math.toRadians(-20));
            spotLens[(s + 1) / 2] = box(u - s * 0.075, PLAT_H + 1.6, PLAT_D / 2 - 0.3 - 0.17, 0.22, 0.18, 0.04, ql, "hd_gold");
        }
        // Podest mit dem Hauptgewinn (rechts)
        double pu = PLAT_W / 2 + 0.85;
        box(pu, 0.55, 0.4, 0.9, 1.1, 0.9, 0, "hd_plinko_dark");
        box(pu, 1.11, 0.4, 0.96, 0.05, 0.96, 0, "hd_gold");
        prizeItem = center.getWorld().spawn(at(pu, 1.5, 0.4), ItemDisplay.class, e -> {
            e.setItemStack(jackpotItem);
            e.setBrightness(new Display.Brightness(15, 15));
            float s = (float) (0.55 * size);
            e.setTransformation(new Transformation(new Vector3f(), face(), new Vector3f(s, s, s), new Quaternionf()));
            mark(e, "podium");
        });
        jackpotText = text(pu, 1.95, 0.4, 0.42f, 150, Component.text("HAUPTGEWINN", TextColor.color(0xFFE870), TextDecoration.BOLD)
                .appendNewline().append(jackpotName));
        // Gewinn-Anzeige und Info vor der Bühne
        prize = text(0, PLAT_H + 0.5, PLAT_D / 2 - 0.1, 0.62f, 300, Component.empty());
        info = text(0, PLAT_H + 0.2, PLAT_D / 2 - 0.1, 0.34f, 400, Component.empty());
        // Klickflächen vor dem Rad
        for (int i = -1; i <= 1; i++) interaction(i * 1.05, HC - R, 0.55, 1.05, 2 * R);
        // echtes Licht vor der Bühne (unsichtbare Lichtblöcke, nur in Luft)
        placeLight(-1.4, 1.2, PLAT_D / 2 + 1.0, 13);
        placeLight(1.4, 1.2, PLAT_D / 2 + 1.0, 13);
        placeLight(0, HC, 1.0, 12);
        placeLight(0, HC + R + 1.0, 0.8, 11);
    }

    private void placeLight(double u, double v, double f, int level) {
        Block b;
        try {
            b = at(u, v, f).getBlock();
        } catch (RuntimeException e) {
            return;   // Vorschau ohne Blöcke
        }
        if (b == null || b.getType() != Material.LIGHT && !b.getType().isAir()) return;
        b.setType(Material.LIGHT, false);
        if (b.getBlockData() instanceof Light l) {
            l.setLevel(level);
            b.setBlockData(l, false);
        }
        lights.add(b.getLocation());
    }

    /** Lichtblöcke heller/dunkler (Pulsieren beim Drehen). */
    public void lights(int level) {
        for (Location l : lights) {
            Block b = l.getBlock();
            if (b.getType() != Material.LIGHT || !(b.getBlockData() instanceof Light li)) continue;
            int lv = Math.max(0, Math.min(15, level));
            if (li.getLevel() == lv) continue;
            li.setLevel(lv);
            b.setBlockData(li, false);
        }
    }

    public void removeAll() {
        if (center.getWorld() == null) return;
        double r = 8 * size + 2;
        for (Entity e : center.getWorld().getNearbyEntities(center, r, r, r)) {
            String v = e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            if (v != null && v.startsWith(id + ":")) e.remove();
        }
        for (Location l : lights) if (l.getBlock().getType() == Material.LIGHT) l.getBlock().setType(Material.AIR, false);
        lights.clear();
        disc = null;
    }

    // ---------- Bewegung und Anzeige ----------

    /** Lage von Stift i bei Scheibenwinkel a: Stift sitzt auf der Feldgrenze 15 + 30 · i Grad (im Uhrzeigersinn). */
    private Quaternionf pegRot(int i, double a) {
        return roll(a - (Wheel.STEP / 2 + i * Wheel.STEP)).mul(face());
    }

    /** Scheibe und Stifte auf Winkel a (Grad, gegen den Uhrzeigersinn); dt Ticks weich. */
    public void spinTo(double a, int dt) {
        angle = a;
        if (!ready()) return;
        set(disc, flat(a), dt);
        for (int i = 0; i < PEGS; i++) set(pegs[i], pegRot(i, a), dt);
    }

    private static void set(ItemDisplay d, Quaternionf q, int dt) {
        if (d == null || !d.isValid()) return;
        Transformation t = d.getTransformation();
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(dt);
        d.setTransformation(new Transformation(t.getTranslation(), q, t.getScale(), t.getRightRotation()));
    }

    /** Strahlenkranz auf Winkel a (Grad) drehen, dt Ticks weich. */
    public void burst(double a, int dt) {
        set(burst, flat(a), dt);
    }

    /** Nadel ausschlagen lassen (Grad, positiv = Spitze nach links; die Nadel hängt nach unten, also im Uhrzeigersinn). */
    public void needle(double deg) {
        if (Math.abs(deg - needleDeg) < 0.2) return;
        needleDeg = deg;
        set(needle, roll(-deg).mul(face()), 1);
    }

    /** Hauptgewinn auf dem Podest langsam drehen. */
    public void podium() {
        podiumYaw += 6;
        if (prizeItem != null && prizeItem.isValid()) {
            set(prizeItem, new Quaternionf(face()).rotateY((float) Math.toRadians(podiumYaw)), 2);
        }
    }

    public void rimBulbs(boolean[] lit, boolean gold) {
        swap(rimBulbs, lit, gold);
    }

    public void platBulbs(boolean[] lit, boolean gold) {
        swap(platBulbs, lit, gold);
    }

    public void signBulbs(boolean[] lit, boolean gold) {
        swap(signBulbs, lit, gold);
    }

    public void spots(boolean on) {
        boolean[] lit = {on, on};
        swap(spotLens, lit, false);
    }

    private static void swap(ItemDisplay[] arr, boolean[] lit, boolean gold) {
        for (int i = 0; i < arr.length && i < lit.length; i++) {
            ItemDisplay b = arr[i];
            if (b == null || !b.isValid()) continue;
            String want = lit[i] ? (gold ? "m_hd_pl_btn_lit" : "m_hd_gold_lit") : "m_hd_gold";
            ItemStack cur = b.getItemStack();
            if (cur != null && cur.hasItemMeta() && cur.getItemMeta().hasItemModel()
                    && cur.getItemMeta().getItemModel().getKey().equals("casino/" + want)) continue;
            b.setItemStack(model(want));
            b.setBrightness(new Display.Brightness(lit[i] ? 15 : 9, lit[i] ? 15 : 9));
        }
    }

    public void prize(Component c) {
        if (prize != null && prize.isValid()) prize.text(c);
    }

    public void info(Component c) {
        if (info != null && info.isValid()) info.text(c);
    }


    public static Component prizeText(String name, boolean big) {
        TextColor col = big ? TextColor.color(0xFFE870) : NamedTextColor.WHITE;
        return Component.text("◆ ", TextColor.color(0xF2C94C)).append(Component.text(name, col, TextDecoration.BOLD))
                .append(Component.text(" ◆", TextColor.color(0xF2C94C)));
    }

    /** Punkt vor dem Rad für Partikel und Töne. */
    public Location effectLocation() {
        return at(0, HC, 0.7);
    }

    /** Punkte der Scheinwerfer und ihr Ziel (Radmitte), für Lichtstrahlen aus Partikeln. */
    public Location[] spotBeam(int side) {
        double u = (side == 0 ? -1 : 1) * (PLAT_W / 2 - 0.25);
        return new Location[]{at(u, PLAT_H + 1.6, PLAT_D / 2 - 0.5), at(0, HC, 0.3)};
    }

    /** Punkt über der Bühne für Konfetti. */
    public Location confettiLocation() {
        return at(0, HC + R + 0.4, 1.2);
    }

    /** Stehplatz vor der Bühne (für Teleport und Kamera), Blick zum Rad. */
    public Location viewpoint(double dist) {
        Location l = at(0, 0, PLAT_D / 2 + dist);
        l.setYaw(frontYaw + 180);
        return l;
    }
}
