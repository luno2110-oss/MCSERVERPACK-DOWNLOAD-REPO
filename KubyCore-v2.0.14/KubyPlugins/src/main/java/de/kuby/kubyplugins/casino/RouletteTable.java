package de.kuby.kubyplugins.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Der Roulette-Tisch aus Anzeigen wie im Clip: 37 Zahlenfelder (rot, schwarz, die Null grün) im Oval am Tischrand,
 * gelbe Zahlen flach darauf, in der Mitte flacher Text ("ROULETTE / Warte auf Einsätze.."), Chips auf dem Tisch und
 * ein Hologramm mit den Einsätzen. Beim Drehen läuft ein Licht über die Felder.
 *
 * <p>Lage: Mitte auf der Tischoberfläche, Breite (rx) quer zur Blickrichtung des Admins, Tiefe (rz) in Blickrichtung.
 * Die Felder folgen einem abgerundeten Rechteck entlang der Tischkante.
 * Die Null liegt hinten (beim Croupier), der Mitteltext ist von vorn lesbar.
 */
public final class RouletteTable {

    /** Reihenfolge der Fächer auf dem europäischen Rad. */
    public static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24,
            16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};

    /** Rote und grüne Felder liegen erhöht, schwarze flach im Filz (wie im Clip). */
    private static float fieldHeight(int n) {
        return n != 0 && !red(n) ? 0.03f : 0.12f;
    }
    private static final TextColor NUMBER = TextColor.color(0xF6E23A);

    private final NamespacedKey tag;
    private Location center;
    private double rx, rz;
    private float yaw;
    private final BlockDisplay[] fields = new BlockDisplay[37];
    private final TextDisplay[] numbers = new TextDisplay[37];
    private TextDisplay centerText;
    private TextDisplay betsText;
    private final List<ItemDisplay> chips = new ArrayList<>();
    private final Map<String, Integer> chipStacks = new HashMap<>();
    /** Weltposition jedes Felds (Index = Position auf dem Rad). */
    private final Location[] fieldAt = new Location[37];
    private final Vector3f[] outward = new Vector3f[37];
    private double spacing = 0.7;

    public RouletteTable(NamespacedKey tag) {
        this.tag = tag;
    }

    public void geometry(Location center, double rx, double rz, float yaw) {
        this.center = center.clone();
        this.rx = rx;
        this.rz = rz;
        this.yaw = yaw;
    }

    public double rx() {
        return rx;
    }

    public double rz() {
        return rz;
    }

    public float yaw() {
        return yaw;
    }

    public Location center() {
        return center == null ? null : center.clone();
    }

    public boolean ready() {
        return center != null && center.getWorld() != null && centerText != null && centerText.isValid();
    }

    /** Einheitsvektoren der Tischebene: right (quer), fwd (in Blickrichtung des Admins). */
    private Vector3f fwd() {
        double r = Math.toRadians(yaw);
        return new Vector3f((float) -Math.sin(r), 0, (float) Math.cos(r));
    }

    private Vector3f right() {
        Vector3f f = fwd();
        return new Vector3f(-f.z, 0, f.x);
    }

    /** Ort auf dem Tisch in Tisch-Koordinaten (u quer, v in Blickrichtung). */
    public Location local(double u, double v, double up) {
        Vector3f r = right(), f = fwd();
        return center.clone().add(r.x * u + f.x * v, up, r.z * u + f.z * v);
    }

    // =================================================================================================================
    // Aufbauen und Abbauen
    // =================================================================================================================

    /** Entfernt alle Anzeigen dieses Tischs (auch Reste nach einem Absturz) und baut neu auf. */
    public void build() {
        if (center == null || center.getWorld() == null) return;
        removeAll();
        layout();
        World w = center.getWorld();
        for (int i = 0; i < 37; i++) {
            int n = WHEEL[i];
            int idx = i;
            Location at = fieldAt[i];
            Quaternionf rot = new Quaternionf().rotateY((float) Math.atan2(outward[i].x, outward[i].z));
            float size = (float) Math.min(0.86, spacing * 0.94);
            float height = fieldHeight(n);
            fields[i] = w.spawn(at, BlockDisplay.class, d -> {
                d.setBlock(fieldBlock(n));
                Vector3f c = new Vector3f(size / 2, 0, size / 2);
                rot.transform(c);
                d.setTransformation(new Transformation(c.negate(), new Quaternionf(rot), new Vector3f(size, height, size),
                        new Quaternionf()));
                mark(d, "field");
            });
            numbers[i] = w.spawn(at.clone().add(0, height + 0.006, 0), TextDisplay.class, d -> {
                d.text(numberText(n, false));
                flat(d, (float) Math.atan2(outward[idx].x, outward[idx].z), 1.5f, -0.13f);
                mark(d, "number");
            });
        }
        Location mid = local(0, 0.15, 0.01);
        centerText = w.spawn(mid, TextDisplay.class, d -> {
            d.text(Component.empty());
            // Text liegt flach und ist von vorn (Admin-Seite) lesbar: Oberkante zeigt in Blickrichtung
            Vector3f f = fwd();
            flat(d, (float) Math.atan2(-f.x, -f.z), 1.35f, -0.3f);
            d.setLineWidth(300);
            mark(d, "center");
        });
        betsText = w.spawn(local(0, 0.4, 1.9), TextDisplay.class, d -> {
            d.text(Component.empty());
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setLineWidth(300);
            d.setTransformation(scale(0.9f));
            mark(d, "bets");
        });
    }

    /** Punkt auf dem abgerundeten Rechteck (Superellipse, Exponent 4) in Tisch-Koordinaten. */
    private double[] shape(double t) {
        double c = Math.cos(t), s = Math.sin(t);
        double u = rx * Math.signum(s) * Math.pow(Math.abs(s), 0.5);
        double v = rz * Math.signum(c) * Math.pow(Math.abs(c), 0.5);
        return new double[]{u, v};
    }

    private void layout() {
        // gleich lange Bögen auf dem abgerundeten Rechteck, Null hinten (in Blickrichtung)
        int samples = 2880;
        double[] len = new double[samples + 1];
        double[] prev = shape(0);
        for (int s = 1; s <= samples; s++) {
            double[] q = shape(2 * Math.PI * s / samples);
            len[s] = len[s - 1] + Math.hypot(q[0] - prev[0], q[1] - prev[1]);
            prev = q;
        }
        double total = len[samples];
        spacing = total / 37;
        int s = 0;
        Vector3f r = right(), f = fwd();
        for (int i = 0; i < 37; i++) {
            double target = total * i / 37;
            while (s < samples && len[s + 1] < target) s++;
            double t = 2 * Math.PI * s / samples;
            double[] q = shape(t);
            fieldAt[i] = local(q[0], q[1], 0);
            // Normale aus der Tangente (Nachbarpunkte), nach außen
            double[] a = shape(t - 0.01), b = shape(t + 0.01);
            double tu = b[0] - a[0], tv = b[1] - a[1];
            double nu = tv, nv = -tu;
            if (nu * q[0] + nv * q[1] < 0) {
                nu = -nu;
                nv = -nv;
            }
            double nl = Math.hypot(nu, nv);
            nu /= nl;
            nv /= nl;
            outward[i] = new Vector3f((float) (r.x * nu + f.x * nv), 0, (float) (r.z * nu + f.z * nv));
        }
    }

    /** Text liegt flach; seine Oberkante zeigt zur Tischmitte (angle = Richtung nach außen). */
    private static void flat(TextDisplay d, float outwardAngle, float scale, float shift) {
        d.setBillboard(Display.Billboard.FIXED);
        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        d.setShadowed(false);
        Quaternionf q = new Quaternionf().rotateY(outwardAngle).rotateX((float) Math.toRadians(-90));
        // um die halbe Zeilenhöhe verschieben, damit der Text mittig auf dem Punkt liegt
        Vector3f t = new Vector3f(0, shift * scale, 0);
        q.transform(t);
        d.setTransformation(new Transformation(t, q, new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    private static Transformation scale(float s) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf());
    }

    private void mark(Entity e, String role) {
        // nicht speichern: der Tisch wird beim Laden des Chunks neu aufgebaut
        e.setPersistent(false);
        e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, role);
    }

    public void removeAll() {
        if (center == null || center.getWorld() == null) return;
        double r = Math.max(rx, rz) + 4;
        for (Entity e : center.getWorld().getNearbyEntities(center, r, 4, r)) {
            if (e.getPersistentDataContainer().has(tag, PersistentDataType.STRING)) e.remove();
        }
        chips.clear();
        chipStacks.clear();
        centerText = null;
        betsText = null;
    }

    // =================================================================================================================
    // Anzeige
    // =================================================================================================================

    public static boolean red(int n) {
        return switch (n) {
            case 1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36 -> true;
            default -> false;
        };
    }

    private static BlockData fieldBlock(int n) {
        Material m = n == 0 ? Material.GREEN_CONCRETE : red(n) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
        return Bukkit.createBlockData(m);
    }

    private static Component numberText(int n, boolean lit) {
        return Component.text(String.valueOf(n), lit ? NamedTextColor.BLACK : NUMBER).decorate(TextDecoration.BOLD);
    }

    public void center(Component text) {
        if (centerText != null && centerText.isValid()) centerText.text(text);
    }

    public void bets(Component text) {
        if (betsText != null && betsText.isValid()) betsText.text(text);
    }

    /** Lauflicht: Feld an Position i (Radreihenfolge) hell oder normal. */
    public void light(int i, boolean on) {
        BlockDisplay f = fields[i];
        TextDisplay t = numbers[i];
        if (f == null || !f.isValid()) return;
        int n = WHEEL[i];
        f.setBlock(on ? Bukkit.createBlockData(Material.GOLD_BLOCK) : fieldBlock(n));
        f.setBrightness(on ? new Display.Brightness(15, 15) : null);
        if (t != null && t.isValid()) t.text(numberText(n, on));
    }

    public static int position(int number) {
        for (int i = 0; i < 37; i++) if (WHEEL[i] == number) return i;
        return 0;
    }

    // =================================================================================================================
    // Chips auf dem Tisch
    // =================================================================================================================

    /** Legt einen Chip auf einen Platz: Zahl (Feld) oder Außenwette (Platz im vorderen Tischteil). */
    public void chip(String spot, int number, long amount) {
        if (center == null || center.getWorld() == null) return;
        Location at = spot.equals("zahl") ? fieldAt[position(number)].clone().add(0, fieldHeight(number), 0) : spotLocation(spot);
        if (at == null) return;
        String key = spot + ":" + number;
        int n = chipStacks.merge(key, 1, Integer::sum);
        if (n > 6) return;
        Location place = at.clone().add((Math.random() - 0.5) * 0.06, 0.012 + 0.028 * (n - 1), (Math.random() - 0.5) * 0.06);
        String color = amount >= 10_000 ? "gold" : amount >= 1000 ? "black" : amount >= 500 ? "green" : amount >= 100 ? "blue"
                : amount >= 50 ? "red" : "white";
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("kuby", "casino/chip_" + color));
        item.setItemMeta(meta);
        ItemDisplay d = center.getWorld().spawn(place, ItemDisplay.class, e -> {
            e.setItemStack(item);
            Quaternionf q = new Quaternionf().rotateY((float) (Math.random() * Math.PI * 2)).rotateX((float) Math.toRadians(-90));
            e.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(0.34f, 0.34f, 0.34f), new Quaternionf()));
            mark(e, "chip");
        });
        chips.add(d);
    }

    private Location spotLocation(String spot) {
        double v = -rz * 0.42;
        return switch (spot) {
            case "niedrig" -> local(-rx * 0.5, v, 0);
            case "gerade" -> local(-rx * 0.3, v, 0);
            case "rot" -> local(-rx * 0.1, v, 0);
            case "schwarz" -> local(rx * 0.1, v, 0);
            case "ungerade" -> local(rx * 0.3, v, 0);
            case "hoch" -> local(rx * 0.5, v, 0);
            case "dutzend1" -> local(-rx * 0.32, v - rz * 0.2, 0);
            case "dutzend2" -> local(0, v - rz * 0.2, 0);
            case "dutzend3" -> local(rx * 0.32, v - rz * 0.2, 0);
            case "kolonne1" -> local(-rx * 0.62, v + rz * 0.22, 0);
            case "kolonne2" -> local(-rx * 0.62, v + rz * 0.44, 0);
            case "kolonne3" -> local(-rx * 0.62, v + rz * 0.66, 0);
            default -> null;
        };
    }

    public void clearChips() {
        for (ItemDisplay d : chips) if (d.isValid()) d.remove();
        chips.clear();
        chipStacks.clear();
    }
}
