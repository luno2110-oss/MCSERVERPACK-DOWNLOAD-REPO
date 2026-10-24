package de.kuby.kubyplugins.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Der Crash-Bildschirm aus Anzeigen vor einer gebauten dunklen Wand (wie im Clip): oben der große Wert ("BEREIT" in
 * Titelschrift oder "1,45x"), darunter Status und Hinweis, darin die Kurve aus schmalen Leisten mit dem Wert an der
 * Spitze. Darüber der Schriftzug "CRASH" (Verlauf gelb nach rot), rechts daneben "LETZTE RUNDEN".
 *
 * <p>Lage: center = Mitte der Bildfläche an der Wand, front zeigt zum Publikum, width/height = Größe der Bildfläche.
 * Titel und "Letzte Runden" haben eigene Plätze (Standard: über bzw. rechts neben dem Bildschirm).
 */
public final class CrashScreen {

    /** Leisten der Kurve. */
    public static final int SEGMENTS = 28;
    private static final float THICK = 0.065f;

    private final NamespacedKey tag;
    private final Location center;
    private final float frontYaw;
    private final double width;
    private final double height;
    private final Location titleAt;
    private final Location roundsAt;

    private TextDisplay header;
    private TextDisplay status;
    private TextDisplay hint;
    private TextDisplay tip;
    private TextDisplay rounds;
    private final ItemDisplay[] segments = new ItemDisplay[SEGMENTS];
    private final boolean[] segmentRed = new boolean[SEGMENTS];

    public CrashScreen(Location center, float frontYaw, double width, double height, Location titleAt, Location roundsAt,
                       NamespacedKey tag) {
        this.center = center.clone();
        this.frontYaw = frontYaw;
        this.width = width;
        this.height = height;
        this.titleAt = titleAt == null ? null : titleAt.clone();
        this.roundsAt = roundsAt == null ? null : roundsAt.clone();
        this.tag = tag;
    }

    public Location center() {
        return center.clone();
    }

    public float frontYaw() {
        return frontYaw;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    private Vector3f front() {
        double r = Math.toRadians(frontYaw);
        return new Vector3f((float) -Math.sin(r), 0, (float) Math.cos(r));
    }

    /** Rechts aus Sicht des Publikums. */
    private Vector3f right() {
        Vector3f n = front();
        return new Vector3f(n.z, 0, -n.x);
    }

    private Location off(double r, double up, double f) {
        Vector3f rr = right(), n = front();
        return center.clone().add(rr.x * r + n.x * f, up, rr.z * r + n.z * f);
    }

    private Quaternionf face() {
        Vector3f n = front();
        return new Quaternionf().rotateY((float) Math.atan2(n.x, n.z));
    }

    /** Standardplatz des Titels: mittig über dem Bildschirm. */
    public Location defaultTitle() {
        return off(0, height / 2 + 0.95, 0.05);
    }

    /** Standardplatz von "Letzte Runden": rechts neben dem Bildschirm. */
    public Location defaultRounds() {
        return off(width / 2 + 0.8, height / 2 - 1.2, 0.3);
    }

    private void mark(Entity e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
    }

    private TextDisplay text(Location at, Quaternionf q, float scale, int lineWidth) {
        return at.getWorld().spawn(at, TextDisplay.class, e -> {
            e.text(Component.empty());
            e.setBillboard(Display.Billboard.FIXED);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(false);
            e.setLineWidth(lineWidth);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(q), new Vector3f(scale, scale, scale),
                    new Quaternionf()));
            mark(e);
        });
    }

    private static ItemStack model(String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("kuby", "casino/" + name));
        it.setItemMeta(meta);
        return it;
    }

    // =================================================================================================================
    // Aufbau
    // =================================================================================================================

    public void build() {
        if (center.getWorld() == null) return;
        removeAll();
        Quaternionf face = face();
        header = text(off(0, height / 2 - 0.56, 0.02), face, 0.6f, 400);
        status = text(off(0, height / 2 - 0.86, 0.02), face, 0.48f, 400);
        hint = text(off(0, height / 2 - 1.0, 0.02), face, 0.3f, 400);
        tip = text(off(0, 0, 0.03), face, 0.5f, 200);
        tip.setInterpolationDuration(2);
        for (int i = 0; i < SEGMENTS; i++) {
            Location at = off(0, 0, 0.025);
            segments[i] = at.getWorld().spawn(at, ItemDisplay.class, e -> {
                e.setItemStack(model("m_teal"));
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTransformation(new Transformation(new Vector3f(), face, new Vector3f(0, 0, 0), new Quaternionf()));
                e.setInterpolationDuration(2);
                mark(e);
            });
            segmentRed[i] = false;
        }
        Location ta = titleAt != null ? titleAt : defaultTitle();
        TextDisplay title = text(ta, face, 0.85f, 400);
        title.text(logo("CRASH", LOGO));
        Location ra = roundsAt != null ? roundsAt : defaultRounds();
        rounds = text(ra, face, 0.55f, 300);
    }

    public void removeAll() {
        if (center.getWorld() == null) return;
        double r = Math.max(width, height) + 4;
        for (Entity e : center.getWorld().getNearbyEntities(center, r, r, r)) {
            if (e.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) e.remove();
        }
        header = null;
    }

    public boolean ready() {
        return header != null && header.isValid();
    }

    // =================================================================================================================
    // Anzeige
    // =================================================================================================================

    /** Oberer Bereich: großer Wert (eigene Größe), Statuszeile, Hinweis. */
    public void texts(Component big, float bigScale, Component line, Component small) {
        if (!ready()) return;
        header.text(big);
        Transformation t = header.getTransformation();
        if (Math.abs(t.getScale().x - bigScale) > 0.001f) {
            header.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(),
                    new Vector3f(bigScale, bigScale, bigScale), t.getRightRotation()));
        }
        status.text(line);
        hint.text(small);
    }

    public void rounds(Component text) {
        if (rounds != null && rounds.isValid()) rounds.text(text);
    }

    /** Linke untere Ecke und Größe der Kurvenfläche (Bildschirm-Koordinaten: rechts, hoch). */
    public double plotLeft() {
        return -width / 2 + 0.55;
    }

    public double plotBottom() {
        return -height / 2 + 0.5;
    }

    public double plotWidth() {
        return width - 1.1;
    }

    public double plotHeight() {
        return height - 1.65;
    }

    /**
     * Kurve aus Punkten (Bildschirm-Koordinaten, rechts und hoch ab Mitte). Überzählige Leisten verschwinden.
     * label steht über der Spitze.
     */
    public void curve(double[] xs, double[] ys, boolean red, Component label) {
        if (!ready()) return;
        Quaternionf face = face();
        int n = Math.min(xs.length - 1, SEGMENTS);
        for (int i = 0; i < SEGMENTS; i++) {
            ItemDisplay d = segments[i];
            if (d == null || !d.isValid()) continue;
            if (segmentRed[i] != red) {
                d.setItemStack(model(red ? "m_crashred" : "m_teal"));
                segmentRed[i] = red;
            }
            Transformation now = d.getTransformation();
            Transformation next;
            if (i < n) {
                double x0 = xs[i], y0 = ys[i], x1 = xs[i + 1], y1 = ys[i + 1];
                double len = Math.hypot(x1 - x0, y1 - y0);
                float ang = (float) Math.atan2(y1 - y0, x1 - x0);
                // Mitte der Leiste relativ zur Bildschirmmitte, in Weltrichtungen
                Vector3f r = right();
                double mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
                Vector3f tr = new Vector3f((float) (r.x * mx), (float) my, (float) (r.z * mx));
                // Drehung in der Bildebene: Leiste zeigt nach "rechts" und wird um die Normale gedreht
                Quaternionf q = new Quaternionf(face).rotateZ(ang);
                next = new Transformation(tr, q, new Vector3f((float) len + THICK * 0.8f, THICK, 0.02f), new Quaternionf());
            } else {
                next = new Transformation(new Vector3f(), face, new Vector3f(0, 0, 0), new Quaternionf());
            }
            if (!sameTransform(now, next)) {
                d.setInterpolationDelay(0);
                d.setTransformation(next);
            }
        }
        if (tip != null && tip.isValid()) {
            if (label == null || xs.length == 0) {
                tip.text(Component.empty());
            } else {
                int last = Math.min(xs.length - 1, SEGMENTS);
                Vector3f r = right();
                Transformation t = tip.getTransformation();
                Vector3f tr = new Vector3f((float) (r.x * xs[last]), (float) (ys[last] + 0.1), (float) (r.z * xs[last]));
                if (t.getTranslation().distanceSquared(tr) > 1e-6) {
                    tip.setInterpolationDelay(0);
                    tip.setTransformation(new Transformation(tr, t.getLeftRotation(), t.getScale(), t.getRightRotation()));
                }
                tip.text(label);
            }
        }
    }

    public void hideCurve() {
        curve(new double[0], new double[0], false, null);
    }

    /** Farbverlauf des Schriftzugs "CRASH" (gelb nach rot, je Buchstabe). */
    public static final int[] LOGO = {0xFFE23C, 0xFFC22E, 0xFF9A26, 0xFF6A26, 0xF23A2A};

    /** Text in der hellen Titelschrift, Buchstabe für Buchstabe eingefärbt (Farben werden wiederholt). */
    public static Component logo(String text, int[] colors) {
        net.kyori.adventure.text.TextComponent.Builder b = Component.text();
        net.kyori.adventure.key.Key font = net.kyori.adventure.key.Key.key("kuby", "title");
        int k = 0;
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            int col = colors[Math.min(k, colors.length - 1)];
            if (!ch.isBlank()) k++;
            b.append(Component.text(de.kuby.kubyplugins.ui.Canvas.titleChars(ch, false)).font(font)
                    .color(net.kyori.adventure.text.format.TextColor.color(col)));
        }
        return b.build();
    }

    private static boolean sameTransform(Transformation a, Transformation b) {
        return a.getTranslation().distanceSquared(b.getTranslation()) < 1e-8 && a.getScale().distanceSquared(b.getScale()) < 1e-8
                && Math.abs(a.getLeftRotation().dot(b.getLeftRotation())) > 0.999999f;
    }
}
