package de.kuby.kubyplugins.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
 * Das Glücksrad an der Wand (v2.07) aus Anzeigen, in Ebenen: dunkle Rückwand mit Goldrahmen und Nieten, davor die
 * Radscheibe (dreht sich als Ganzes), darüber der feste Goldring mit 16 Lämpchen (Lauflicht beim Drehen), die Nabe und
 * oben der Zeiger. Darüber der Schriftzug „GLÜCKSRAD“, darunter die Gewinn-Tafel (zeigt beim Drehen das Feld am Zeiger)
 * und die Info-Zeile.
 *
 * <p>Brett-Koordinaten wie bei Plinko: u rechts, v hoch, f nach vorn aus der Wand, mal {@code size}; center = Mitte der
 * Scheibe auf der Wandoberfläche. Flache Bilder (item/generated) werden wie die Slot-Symbole um 180 Grad gedreht, sonst
 * sähe man sie von hinten (gespiegelt).
 */
public final class WheelBoard {

    /** Halbmesser der Scheibe (Brett-Einheiten), Unter- und Oberkante der Rückwand. */
    public static final double R = 1.5, BOTTOM = -2.35, TOP = 2.35;
    private static final double HALF_W = 1.85;
    private static final float ZF = 0.0024f;
    public static final int BULBS = 16;
    public static final int[] LOGO = {0xFFE870, 0xF8CC2A, 0xF2A21C, 0xE8622A, 0xD8384A, 0xF8CC2A};

    private final String id;
    private final Location center;
    private final float frontYaw;
    private final double size;
    private final NamespacedKey tag;
    private final NamespacedKey click;

    private ItemDisplay disc;
    private ItemDisplay pointer;
    private final ItemDisplay[] bulbs = new ItemDisplay[BULBS];
    private TextDisplay prize;
    private TextDisplay info;
    private double angle;

    public WheelBoard(String id, Location center, float frontYaw, double size, NamespacedKey tag, NamespacedKey click) {
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

    public float frontYaw() {
        return frontYaw;
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

    /** Lage eines flachen Bilds, um a Grad gegen den Uhrzeigersinn (aus Sicht des Spielers) gedreht. */
    private Quaternionf flat(double a) {
        Vector3f n = front();
        return new Quaternionf().rotationAxis((float) Math.toRadians(a), n.x, n.y, n.z).mul(face()).rotateY((float) Math.PI);
    }

    private void mark(Entity e, String role) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id + ":" + role);
    }

    private static ItemStack model(String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("kuby", "casino/" + name));
        it.setItemMeta(meta);
        return it;
    }

    private ItemDisplay box(double u, double v, double f, double w, double h, double d, double roll, String mat) {
        Quaternionf q = new Quaternionf(face()).rotateZ((float) roll);
        float k = (float) size;
        return center.getWorld().spawn(at(u, v, f), ItemDisplay.class, e -> {
            e.setItemStack(model("m_" + mat));
            e.setTransformation(new Transformation(new Vector3f(), q, new Vector3f((float) w * k, (float) h * k, (float) d * k), new Quaternionf()));
            mark(e, "static");
        });
    }

    /** Flaches Bild, Breite w (Brett-Einheiten), dünn gepresst (1/10 der Tiefe eines Items). */
    private ItemDisplay sprite(double u, double v, double f, double w, String name, double a, int light) {
        float s = (float) (w * size);
        return center.getWorld().spawn(at(u, v, f), ItemDisplay.class, e -> {
            e.setItemStack(model(name));
            e.setBrightness(new Display.Brightness(light, light));
            e.setTransformation(new Transformation(new Vector3f(), flat(a), new Vector3f(s, s, s * 0.1f), new Quaternionf()));
            mark(e, "static");
        });
    }

    private TextDisplay text(double u, double v, double f, float scale, int lineWidth, Component c) {
        float k = (float) size;
        return center.getWorld().spawn(at(u, v, f), TextDisplay.class, e -> {
            e.text(c);
            e.setBillboard(Display.Billboard.FIXED);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(false);
            e.setLineWidth(lineWidth);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), face(), new Vector3f(scale * k, scale * k, scale * k), new Quaternionf()));
            mark(e, "static");
        });
    }

    private void interaction(double u, double v0, double w, double h) {
        Location l = at(u, v0, w / 2);
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

    public void build() {
        if (center.getWorld() == null) return;
        removeAll();
        // Rückwand (Pflaume wie Plinko), Goldrahmen mit Nieten an den Ecken (eine bewusst dunkler)
        box(0, (TOP + BOTTOM) / 2, 0.03, 2 * HALF_W, TOP - BOTTOM, 0.06, 0, "hd_plinko_dark");
        double fTop = TOP - 0.05, fBot = BOTTOM + 0.05;
        box(0, fTop, 0.075, 2 * HALF_W - 4 * ZF, 0.08, 0.04, 0, "hd_gold");
        box(0, fBot, 0.075, 2 * HALF_W - 4 * ZF, 0.08, 0.04, 0, "hd_gold");
        for (int side = -1; side <= 1; side += 2) {
            // 2,4 mm schmaler als bis zur Kante: die Außenseite läge sonst bündig mit der Seite der Rückwand
            box(side * (HALF_W - 0.04 - ZF), 0, 0.075 + ZF, 0.08, TOP - BOTTOM - 0.1 - 2 * ZF, 0.04 + ZF, 0, "hd_gold");
        }
        double[][] riv = {{-HALF_W + 0.04, fTop}, {HALF_W - 0.04, fTop}, {-HALF_W + 0.04, fBot}, {HALF_W - 0.04, fBot}};
        for (int i = 0; i < riv.length; i++) box(riv[i][0], riv[i][1], 0.1, 0.075, 0.075, 0.02, Math.PI / 4, i == 2 ? "hd_gold" : "hd_gold_lit");
        // Glühen hinter der Scheibe (Innenfeld von Plinko, leuchtet zur Mitte)
        box(0, 0, 0.065, 2 * R + 0.1, 2 * R + 0.1, 0.01, 0, "hd_plinko");
        // Scheibe (dreht), Ring, Lämpchen, Nabe, Zeiger
        float ds = (float) (2 * R * size);
        disc = center.getWorld().spawn(at(0, 0, 0.09), ItemDisplay.class, e -> {
            e.setItemStack(model("wh_disc"));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), flat(angle), new Vector3f(ds, ds, ds * 0.1f), new Quaternionf()));
            mark(e, "disc");
        });
        sprite(0, 0, 0.11, 2 * R, "wh_rim", 0, 15);
        for (int i = 0; i < BULBS; i++) {
            double a = Math.toRadians(i * 360.0 / BULBS);
            double rr = R * 61.0 / 64;
            bulbs[i] = box(rr * Math.sin(a), rr * Math.cos(a), 0.13, 0.07, 0.07, 0.02, Math.PI / 4, "hd_gold");
            bulbs[i].setBrightness(new Display.Brightness(12, 12));
        }
        sprite(0, 0, 0.14, 0.85, "wh_hub", 0, 15);
        pointer = sprite(0, R + 0.04, 0.16, 0.62, "wh_pointer", 0, 15);
        // Schriftzug, Gewinn-Tafel, Info
        text(0, 1.82, 0.08, 0.95f, 400, CrashScreen.logo("GLÜCKSRAD", LOGO));
        box(0, -1.83, 0.075, 2.3, 0.3, 0.03, 0, "hd_plinko_dark");
        box(0, -1.665, 0.0925, 2.36, 0.03, 0.035, 0, "hd_gold");
        box(0, -1.995, 0.0925, 2.36, 0.03, 0.035, 0, "hd_gold");
        prize = text(0, -1.93, 0.095, 0.42f, 300, Component.empty());
        info = text(0, -2.2, 0.075, 0.26f, 400, Component.empty());
        for (int i = -1; i <= 1; i++) interaction(i * 1.0, -R, 1.0, 2 * R);
    }

    public void removeAll() {
        if (center.getWorld() == null) return;
        double r = (TOP - BOTTOM) * size + 2;
        for (Entity e : center.getWorld().getNearbyEntities(center, r, r, r)) {
            String v = e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            if (v != null && v.startsWith(id + ":")) e.remove();
        }
        disc = null;
    }

    // ---------- Bewegung und Anzeige ----------

    /** Scheibe auf Winkel a (Grad, gegen den Uhrzeigersinn) drehen; dt Ticks weich (0 = sofort). */
    public void spinTo(double a, int dt) {
        angle = a;
        if (!ready()) return;
        Transformation t = disc.getTransformation();
        disc.setInterpolationDelay(0);
        disc.setInterpolationDuration(dt);
        disc.setTransformation(new Transformation(t.getTranslation(), flat(a), t.getScale(), t.getRightRotation()));
    }

    /** Zeiger kippt kurz (Feldwechsel: der Zeiger wird vom Stift mitgenommen) und federt zurück. */
    public void flick(double deg) {
        if (pointer == null || !pointer.isValid()) return;
        Transformation t = pointer.getTransformation();
        pointer.setInterpolationDelay(0);
        pointer.setInterpolationDuration(2);
        pointer.setTransformation(new Transformation(t.getTranslation(), flat(deg), t.getScale(), t.getRightRotation()));
    }

    /** Lämpchen: lit[i] = an. */
    public void bulbs(boolean[] lit) {
        for (int i = 0; i < BULBS; i++) {
            ItemDisplay b = bulbs[i];
            if (b == null || !b.isValid()) continue;
            b.setItemStack(model(lit[i] ? "m_hd_gold_lit" : "m_hd_gold"));
            b.setBrightness(new Display.Brightness(lit[i] ? 15 : 10, lit[i] ? 15 : 10));
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

    public Location effectLocation() {
        return at(0, 0, 0.6);
    }

    public Location front(double dist) {
        Location l = at(0, -R, dist);
        l.setY(center.getY() - (R + 0.6) * size);
        l.setYaw(frontYaw + 180);
        return l;
    }
}
