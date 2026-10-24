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
    /** Flächen einer Walze (Winkel zur Vorderseite); 1, 2, 3 tragen die sichtbaren Symbole oben, Mitte, unten. */
    private static final double[] FACETS = {72, 36, 0, -36, -72};

    public final String id;
    private final NamespacedKey tag;
    private final NamespacedKey click;
    private final Location center;
    private final Location stool;
    private final float frontYaw;
    private final double width;
    private final int desk;

    private final ItemDisplay[][] symbols = new ItemDisplay[3][3];
    private TextDisplay panel;
    private TextDisplay holo;
    private BlockDisplay lever;
    private ArmorStand seat;

    public SlotMachine(String id, Location center, float frontYaw, double width, int desk, Location stool, NamespacedKey tag,
                       NamespacedKey click) {
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
        for (int i = 0; i < 3; i++) {
            double x = (i - 1) * s;
            for (int k = 0; k < FACETS.length; k++) {
                double a = Math.toRadians(FACETS[k]);
                // Fläche: Normale (hoch sin a, vor cos a), Kasten liegt knapp hinter der Oberfläche
                Quaternionf q = new Quaternionf(face).rotateX((float) -a);
                double up = Math.sin(a) * (apothem - 0.02), out = Math.cos(a) * (apothem - 0.02);
                box(off(x, up, out), q, rw, chord, 0.04f, "white");
                if (k < 1 || k > 3) continue;
                int row = k - 1;
                String sym = start == null ? SYMBOLS[(i + row * 3) % SYMBOLS.length] : start[i][row];
                double sUp = Math.sin(a) * (apothem + 0.012), sOut = Math.cos(a) * (apothem + 0.012);
                // Items zeigen ihre Vorderseite nach -Z (der Client dreht sie um 180°): noch einmal drehen
                Quaternionf qs = new Quaternionf(q).rotateY((float) Math.PI);
                int col = i;
                symbols[i][row] = center.getWorld().spawn(off(x, sUp, sOut), ItemDisplay.class, e -> {
                    e.setItemStack(model("slot_" + sym));
                    e.setTransformation(new Transformation(new Vector3f(), qs, new Vector3f(symScale, symScale, symScale),
                            new Quaternionf()));
                    mark(e, "symbol:" + col + ":" + row);
                });
            }
        }
        // Stege zwischen den Walzen, Leisten oben und unten
        float depth = (float) (rad + 0.06);
        for (int i = 0; i < 2; i++) box(off((i - 0.5) * s, 0, depth / 2), face, 0.07f, (float) (2 * rad + 0.04), depth, "black");
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
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(face), new Vector3f(0.7f, 0.7f, 0.7f),
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
            e.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(0.4f, 0.4f, 0.4f), new Quaternionf()));
            mark(e, "panel");
        });
        interaction(off(0, DESK_TOP - 0.02, front - 0.12), 0.5f, 0.08f, "panel");
        double ctrl = rad + 0.26;
        Location lb = off(-1.1 * s, DESK_TOP + 0.09, ctrl);
        block(lb, face, Material.POLISHED_ANDESITE, 0.34f, 0.18f, 0.3f);
        lever = lb.getWorld().spawn(lb.clone().add(0, 0.09, 0), BlockDisplay.class, e -> {
            Switch data = (Switch) Bukkit.createBlockData(Material.LEVER);
            data.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.FLOOR);
            data.setFacing(org.bukkit.block.BlockFace.NORTH);
            e.setBlock(data);
            Quaternionf q = new Quaternionf(face).rotateY((float) Math.PI / 2);
            Vector3f t = new Vector3f(-0.5f, 0, -0.5f);
            q.transform(t);
            e.setTransformation(new Transformation(t, q, new Vector3f(1, 1, 1), new Quaternionf()));
            mark(e, "lever");
        });
        interaction(lb.clone().add(0, -0.09, 0), 0.36f, 0.75f, "lever");
        Location bt = off(1.1 * s, DESK_TOP + 0.05, ctrl);
        box(bt, face, 0.3f, 0.1f, 0.24f, "black");
        box(bt.clone().add(0, 0.065, 0), face, 0.17f, 0.04f, 0.13f, "screen");
        interaction(bt.clone().add(0, -0.05, 0), 0.32f, 0.2f, "button");
        // Schild vorn am Pult (Gewinnplan)
        if (desk > 0) {
            Location sl = off(0, DESK_TOP - 0.5, desk + 0.02);
            box(sl, face, (float) Math.min(1.2, 3 * s * 0.45), 0.42f, 0.04f, "rack");
            box(sl.clone().add(0, 0, 0), face, (float) Math.min(1.12, 3 * s * 0.45 - 0.08), 0.34f, 0.05f, "wood");
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
            box(stool.clone().add(0, 0.03, 0), sq, 0.42f, 0.06f, 0.42f, "white");
            box(stool.clone().add(0, 0.33, 0), sq, 0.12f, 0.56f, 0.12f, "white");
            box(stool.clone().add(0, 0.64, 0), sq, 0.5f, 0.1f, 0.5f, "white");
            interaction(stool.clone(), 0.6f, 0.75f, "seat");
        }
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

    public void symbol(int reel, int row, String sym, boolean blur) {
        ItemDisplay d = symbols[reel][row];
        if (d != null && d.isValid()) d.setItemStack(model("slot_" + sym + (blur ? "_blur" : "")));
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
        b.append(Component.text(de.kuby.kubyplugins.ui.Canvas.titleChars(" SLOTS ", false)).font(Key.key("kuby", "title"))
                .color(TextColor.color(0xF0303E)));
        b.append(Component.text(String.valueOf(de.kuby.kubyplugins.ui.KitData.SmallIcon.C_RED.c)).font(chip).color(NamedTextColor.WHITE));
        b.append(Component.newline());
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
        data.setPowered(pulled);
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
