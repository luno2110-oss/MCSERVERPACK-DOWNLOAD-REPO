package de.kuby.kubyplugins.casino;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Chip-Rack der Bank (v2.07, Wunsch des Besitzers: „für jeden Tisch interaktive Chip-Racks: verliert der Spieler, kommen
 * die Chips physisch ins Rack des Dealers, bei Gewinnen werden sie physisch wieder herausgenommen“).
 *
 * <p>Röhren nebeneinander (along), in jeder stehen die Chips einer Farbe hochkant aufgereiht (axis), gut ein Viertel
 * steckt in der Rille – wie in einem echten Chip-Tray. Verlorene Chips fliegen in ihre Röhre, drehen sich dabei hochkant
 * und bleiben stehen. Für Gewinne nimmt der Dealer den vordersten Chip heraus (er legt sich beim Flug flach). Ist eine
 * Röhre leer, füllt der Dealer sie mit der Grundfüllung nach; ist sie voll, verschwinden weitere Chips „nach hinten“
 * (die Menge zählt weiter). Die Mengen leben nur im Speicher: nach einem Neuaufbau wieder die Grundfüllung.
 */
public final class ChipRack {

    /** Legt eine Chip-Anzeige an (die Tisch-Klasse markiert sie, damit ihr Abbau sie mitnimmt). */
    public interface Spawner {
        ItemDisplay spawn(Location at, String color, Quaternionf rot, float scale);
    }

    /** Farben nach Wert (für „nächste Röhre“, wenn eine Farbe im Rack fehlt). */
    static final List<String> ORDER = List.of("white", "red", "blue", "green", "black", "purple", "gold", "pink");

    private final Plugin plugin;
    private final Location center;
    private final Vector3f along;
    private final Vector3f axis;
    private final double pitch;
    private final double length;
    private final float scale;
    private final String[] colors;
    private final int base;
    private final Spawner spawner;
    private final List<List<ItemDisplay>> tubes = new ArrayList<>();
    private final int[] count;

    /**
     * @param center Mitte des Racks auf Höhe der Tablett-Oberkante
     * @param along  Richtung, in der die Röhren nebeneinander liegen (Einheitsvektor)
     * @param axis   Richtung einer Röhre (Einheitsvektor, waagrecht)
     * @param pitch  Abstand der Röhren, length = Länge einer Röhre
     * @param base   Grundfüllung je Röhre
     */
    public ChipRack(Plugin plugin, Location center, Vector3f along, Vector3f axis, double pitch, double length, float scale,
                    String[] colors, int base, Spawner spawner) {
        this.plugin = plugin;
        this.center = center.clone();
        this.along = new Vector3f(along).normalize();
        this.axis = new Vector3f(axis).normalize();
        this.pitch = pitch;
        this.length = length;
        this.scale = scale;
        this.colors = colors.clone();
        this.base = base;
        this.spawner = spawner;
        this.count = new int[colors.length];
        for (int i = 0; i < colors.length; i++) tubes.add(new ArrayList<>());
    }

    public String[] colors() {
        return colors.clone();
    }

    public int count(int tube) {
        return count[tube];
    }

    private double spacing() {
        return 0.08 * scale * 1.18;
    }

    /** So viele Chips passen sichtbar in eine Röhre. */
    public int capacity() {
        return Math.max(1, (int) Math.floor(length / spacing()));
    }

    /** Röhre für eine Chip-Farbe; fehlt sie, die mit dem nächsten Wert. */
    public int tubeOf(String color) {
        int i = Arrays.asList(colors).indexOf(color);
        if (i >= 0) return i;
        int want = Math.max(0, ORDER.indexOf(color)), best = 0, bd = Integer.MAX_VALUE;
        for (int t = 0; t < colors.length; t++) {
            int d = Math.abs(ORDER.indexOf(colors[t]) - want);
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        return best;
    }

    /** Platz k (0 = hinten, beim Dealer) in Röhre t, Chip-Mitte. */
    public Location slot(int t, int k) {
        double off = (t - (colors.length - 1) / 2.0) * pitch;
        // Nachbarröhren 1,2 mm versetzt: Chips gleicher Nummer lägen sonst in einer Ebene (Z-Fighting)
        double a = -length / 2 + spacing() * (Math.min(k, capacity() - 1) + 0.5) + (t % 2) * 0.0012;
        double up = 0.4 * scale * 0.72;
        return center.clone().add(along.x * off + axis.x * a, up, along.z * off + axis.z * a);
    }

    /** Hochkant, Fläche zur Röhrenachse, je Platz leicht verdreht (wirkt wie echte, nicht ganz gerade Chips). */
    public Quaternionf standing(int t, int k) {
        float roll = (float) (((t * 7 + k * 3) % 5 - 2) * 0.06);
        return new Quaternionf().rotationAxis(roll, axis.x, axis.y, axis.z).mul(new Quaternionf().rotationTo(0, 1, 0, axis.x, axis.y, axis.z));
    }

    /** Grundfüllung aufbauen (nach dem Tablett). */
    public void build() {
        clear();
        for (int t = 0; t < colors.length; t++) {
            count[t] = base;
            for (int k = 0; k < Math.min(base, capacity()); k++) tubes.get(t).add(spawner.spawn(slot(t, k), colors[t], standing(t, k), scale));
        }
    }

    public void clear() {
        for (List<ItemDisplay> l : tubes) {
            for (ItemDisplay d : l) if (d.isValid()) d.remove();
            l.clear();
        }
        Arrays.fill(count, 0);
    }

    /** Chip e fliegt (nach delay Ticks) in Röhre t, stellt sich hochkant und bleibt dort (oder verschwindet, wenn voll). */
    public void deposit(ItemDisplay e, int t, long delay) {
        if (e == null || !e.isValid()) return;
        int k = count[t]++;
        boolean shown = k < capacity();
        Location to = slot(t, k);
        Quaternionf q = standing(t, k);
        later(delay, () -> {
            if (!e.isValid()) return;
            e.setTeleportDuration(8);
            e.teleport(to);
            Transformation tr = e.getTransformation();
            e.setInterpolationDelay(0);
            e.setInterpolationDuration(8);
            e.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(scale, scale, scale), tr.getRightRotation()));
        });
        later(delay + 9, () -> {
            if (!e.isValid()) return;
            if (shown) {
                setModel(e, colors[t]);
                tubes.get(t).add(e);
            } else {
                e.remove();
            }
        });
    }

    /**
     * Nimmt den vordersten Chip aus Röhre t (leer: erst nachfüllen). Die Anzeige gehört danach dem Aufrufer, der sie
     * bewegt (z. B. mit {@link #fly}). Ist die Röhre über ihre sichtbare Länge gefüllt, kommt ein Chip „von hinten nach“.
     */
    public ItemDisplay withdraw(int t) {
        List<ItemDisplay> l = tubes.get(t);
        l.removeIf(d -> !d.isValid());
        if (count[t] <= 0 || l.isEmpty()) refill(t);
        count[t] = Math.max(0, count[t] - 1);
        if (count[t] >= l.size() || l.isEmpty()) {
            int k = Math.max(0, l.size() - 1);
            return spawner.spawn(slot(t, k), colors[t], standing(t, k), scale);
        }
        return l.remove(l.size() - 1);
    }

    /** Röhre mit der Grundfüllung nachfüllen (die neuen Chips fallen kurz von oben hinein). */
    public void refill(int t) {
        List<ItemDisplay> l = tubes.get(t);
        l.removeIf(d -> !d.isValid());
        count[t] = Math.max(count[t], base);
        for (int k = l.size(); k < Math.min(count[t], capacity()); k++) {
            Location at = slot(t, k);
            ItemDisplay d = spawner.spawn(at.clone().add(0, 0.12, 0), colors[t], standing(t, k), scale);
            l.add(d);
            later(1 + k / 3, () -> {
                if (!d.isValid()) return;
                d.setTeleportDuration(4);
                d.teleport(at);
            });
        }
    }

    /** Chip (aus withdraw) nach delay Ticks flach an den Ort to fliegen lassen. */
    public void fly(ItemDisplay e, Location to, float yRot, long delay, int ticks) {
        later(delay, () -> {
            if (!e.isValid()) return;
            e.setTeleportDuration(ticks);
            e.teleport(to);
            Transformation tr = e.getTransformation();
            e.setInterpolationDelay(0);
            e.setInterpolationDuration(ticks);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(yRot), tr.getScale(), tr.getRightRotation()));
        });
    }

    /** Farbe eines Chips aus seinem Modell (kuby:casino/c3chip_&lt;farbe&gt;), sonst null. */
    public static String colorOf(ItemDisplay d) {
        ItemStack it = d.getItemStack();
        if (it == null || !it.hasItemMeta() || !it.getItemMeta().hasItemModel()) return null;
        String k = it.getItemMeta().getItemModel().getKey();
        int i = k.indexOf("c3chip_");
        return i < 0 ? null : k.substring(i + 7);
    }

    private static void setModel(ItemDisplay d, String color) {
        if (color.equals(colorOf(d))) return;
        ItemStack it = d.getItemStack();
        if (it == null) return;
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new org.bukkit.NamespacedKey("kuby", "casino/c3chip_" + color));
        it.setItemMeta(m);
        d.setItemStack(it);
    }

    /**
     * Betrag in Chips der Rack-Farben zerlegen (größte zuerst), höchstens max Chips (Bild, nicht Buchhaltung).
     * values[i] = Wert der Röhre i.
     */
    public static List<Integer> breakdown(long amount, long[] values, int max) {
        List<Integer> out = new ArrayList<>();
        Integer[] idx = new Integer[values.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Long.compare(values[b], values[a]));
        long rest = amount;
        for (int i : idx) {
            while (rest >= values[i] && out.size() < max) {
                out.add(i);
                rest -= values[i];
            }
        }
        if (out.isEmpty() && amount > 0) out.add(idx[idx.length - 1]);
        return out;
    }

    private void later(long ticks, Runnable r) {
        if (ticks <= 0) r.run();
        else plugin.getServer().getScheduler().runTaskLater(plugin, r, ticks);
    }
}
