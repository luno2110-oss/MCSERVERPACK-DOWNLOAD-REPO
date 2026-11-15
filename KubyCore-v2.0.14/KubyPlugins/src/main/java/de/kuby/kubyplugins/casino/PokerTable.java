package de.kuby.kubyplugins.casino;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pokertisch im Clean-Look (v1.68): Stadion-Form (Rechteck mit Halbkreisen), ein durchgehender Filz in hoher
 * Auflösung mit Goldlinie um die Gemeinschaftskarten, schwarze Leder-Armauflage, Sockel in Creme-Lack, sechs
 * Barhocker, Dealerin an der Mitte der einen Längsseite. Alles aus Anzeigen, nichts gespeichert.
 *
 * <p>Je Platz: zwei Handkarten (für den Besitzer offen, für alle anderen verdeckt: zwei Anzeigen, die je Spieler
 * ein- oder ausgeblendet werden), Einsatz-Chips mit Plakette, Namensschild mit Stack und letzter Aktion, Zeitbalken
 * und – wenn der Spieler dran ist – Aktionsknöpfe auf dem Filz (Passen, Check/Mitgehen, Erhöhen, All-in, beim
 * Erhöhen: −, +, ½ Pot, Pot, Setzen, Zurück). In der Mitte: fünf Gemeinschaftskarten, Pot-Chips mit Plakette,
 * Dealer-Button wandert von Platz zu Platz.
 *
 * <p>Lage: origin = Boden in der Tischmitte, yaw = Blickrichtung der Dealerin (sie steht an der -fwd-Längsseite).
 */
public final class PokerTable {

    public static final int SEATS = 6;
    /** Plätze als Anteil am Umfang, 0 = Mitte der Längsseite gegenüber der Dealerin, 0,5 = Dealerin. */
    private static final double[] SEAT_T = {0.08, 0.2, 0.32, 0.68, 0.8, 0.92};
    static final double FELT_Y = 0.8;
    private static final double RIM_TOP = FELT_Y + 0.10;
    private static final double S = 1.35;   // halbe Länge der geraden Seiten
    private static final double R = 1.35;   // Radius der Enden
    private static final float CARD = 0.46f;

    public final String id;
    private final org.bukkit.plugin.Plugin plugin;
    private final NamespacedKey tag;
    private final NamespacedKey click;
    private final Location origin;
    private final float yaw;

    private DealerAnim anim;
    private TextDisplay dealerText;
    private TextDisplay potBadge;
    private ItemDisplay dealerButton;
    private final TextDisplay[] plates = new TextDisplay[SEATS];
    private final TextDisplay[] betBadge = new TextDisplay[SEATS];
    private final TextDisplay[] handHint = new TextDisplay[SEATS];
    private final ArmorStand[] vehicles = new ArmorStand[SEATS];
    private final List<List<Entity>> hole = new ArrayList<>();
    private final List<List<Entity>> bets = new ArrayList<>();
    private final List<List<Entity>> buttons = new ArrayList<>();
    private final List<List<BlackjackTable.Button>> hoverables = new ArrayList<>();
    private final List<Entity> board = new ArrayList<>();
    private final List<Entity> pot = new ArrayList<>();
    private final ItemDisplay[] timer = new ItemDisplay[SEATS];
    private final int[] hovered = {-1, -1, -1, -1, -1, -1};

    public PokerTable(org.bukkit.plugin.Plugin plugin, String id, Location origin, float yaw, NamespacedKey tag, NamespacedKey click) {
        this.plugin = plugin;
        this.id = id;
        this.origin = origin.clone();
        this.yaw = yaw;
        this.tag = tag;
        this.click = click;
        for (int i = 0; i < SEATS; i++) {
            hole.add(new ArrayList<>());
            bets.add(new ArrayList<>());
            buttons.add(new ArrayList<>());
            hoverables.add(new ArrayList<>());
        }
    }

    public Location origin() {
        return origin.clone();
    }

    public float yaw() {
        return yaw;
    }

    // =================================================================================================================
    // Koordinaten: x = quer (right), z = fwd
    // =================================================================================================================

    private Vector3f fwd() {
        double r = Math.toRadians(yaw);
        return new Vector3f((float) -Math.sin(r), 0, (float) Math.cos(r));
    }

    private Vector3f right() {
        Vector3f f = fwd();
        return new Vector3f(-f.z, 0, f.x);
    }

    private Location local(double x, double y, double z) {
        Vector3f f = fwd(), r = right();
        return origin.clone().add(r.x * x + f.x * z, y, r.z * x + f.z * z);
    }

    private Vector3f localDir(double x, double z) {
        Vector3f f = fwd(), r = right();
        return new Vector3f((float) (r.x * x + f.x * z), 0, (float) (r.z * x + f.z * z)).normalize();
    }

    /** Punkt am Rand der Stadion-Form bei Umfangsanteil t, um d nach außen versetzt. [x, z, nx, nz] lokal. */
    static double[] edge(double t, double d) {
        double len = 4 * S + 2 * Math.PI * R;
        double s = ((t % 1) + 1) % 1 * len;
        double[] seg = {S, Math.PI * R, 2 * S, Math.PI * R, S};
        double x, z, nx, nz;
        if (s < seg[0]) {
            x = s; z = R; nx = 0; nz = 1;
        } else if ((s -= seg[0]) < seg[1]) {
            double a = Math.PI / 2 - s / R;
            nx = Math.cos(a); nz = Math.sin(a);
            x = S + R * nx; z = R * nz;
        } else if ((s -= seg[1]) < seg[2]) {
            x = S - s; z = -R; nx = 0; nz = -1;
        } else if ((s -= seg[2]) < seg[3]) {
            double a = -Math.PI / 2 - s / R;
            nx = Math.cos(a); nz = Math.sin(a);
            x = -S + R * nx; z = R * nz;
        } else {
            s -= seg[3];
            x = -S + s; z = R; nx = 0; nz = 1;
        }
        return new double[]{x + nx * d, z + nz * d, nx, nz};
    }

    private Location seatPoint(int seat, double d, double side, double y) {
        double[] e = edge(SEAT_T[seat], d);
        // side: quer zur Normalen (vom Spieler aus nach rechts positiv)
        double tx = -e[3], tz = e[2];
        return local(e[0] + tx * side, y, e[1] + tz * side);
    }

    /** Richtung vom Platz zur Tischmitte (für Karten, Knöpfe, Texte). */
    private Vector3f inward(int seat) {
        double[] e = edge(SEAT_T[seat], 0);
        return localDir(-e[2], -e[3]);
    }

    private static float angleOf(Vector3f d) {
        return (float) Math.atan2(d.x, d.z);
    }

    /** Oberkante des Modells (-Z) zeigt in Richtung look. */
    private static Quaternionf facingTop(Vector3f look) {
        return new Quaternionf().rotateY(angleOf(look));
    }

    public Location seatLocation(int seat) {
        return seatPoint(seat, 0.62, 0, 0);
    }

    // =================================================================================================================
    // Aufbau
    // =================================================================================================================

    private void mark(Entity e, String role) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id + ":" + role);
    }

    private ItemDisplay box(Location c, float angle, float w, float h, float d, String mat) {
        return c.getWorld().spawn(c, ItemDisplay.class, e -> {
            e.setItemStack(BlackjackTable.model("m_" + mat));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(angle), new Vector3f(w, h, d), new Quaternionf()));
            mark(e, "static");
        });
    }

    private ItemDisplay item(Location at, String model, Quaternionf rot, float scale, String role) {
        return at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(BlackjackTable.model(model));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(scale, scale, scale), new Quaternionf()));
            mark(e, role);
        });
    }

    private TextDisplay text(Location l, Component c, float scale, Display.Billboard bb) {
        return l.getWorld().spawn(l, TextDisplay.class, e -> {
            e.text(c);
            e.setBillboard(bb);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setLineWidth(220);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
            mark(e, "text");
        });
    }

    private TextDisplay badge(Location l, float scale) {
        TextDisplay t = text(l, Component.empty(), scale, Display.Billboard.CENTER);
        t.setShadowed(false);
        return t;
    }

    static void badgeText(TextDisplay e, Component c) {
        if (e == null || !e.isValid()) return;
        boolean empty = c == null || c.equals(Component.empty());
        e.text(empty ? Component.empty() : c);
        e.setBackgroundColor(empty ? Color.fromARGB(0, 0, 0, 0) : Color.fromARGB(200, 14, 16, 20));
    }

    public void build() {
        World w = origin.getWorld();
        if (w == null) return;
        removeAll();
        float a0 = angleOf(fwd());
        // Filz: Textur 512 x 256 = Länge 2(S+R) x Breite 2R
        double L = 2 * (S + R);
        w.spawn(origin.clone().add(0, FELT_Y, 0), ItemDisplay.class, e -> {
            e.setItemStack(BlackjackTable.model("pk_felt"));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            // Element 16 x 8 (x 0..16, z 4..12) -> Skala L in beide Richtungen ergibt L x L/2
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(a0 + (float) Math.PI),
                    new Vector3f((float) L, 1f, (float) L), new Quaternionf()));
            e.setViewRange(1.5f);
            mark(e, "static");
        });
        // Armauflage und Sockel entlang des Randes
        int n = 40;
        double len = 4 * S + 2 * Math.PI * R;
        for (int i = 0; i < n; i++) {
            double t = (i + 0.5) / n;
            double[] e = edge(t, 0);
            Vector3f out = localDir(e[2], e[3]);
            float ang = angleOf(out);
            float segLen = (float) (len / n * 1.08);
            double[] rim = edge(t, -0.05);
            box(local(rim[0], RIM_TOP - 0.08, rim[1]), ang, segLen * 1.05f, 0.16f, 0.24f, "hd_leather");
            double[] base = edge(t, -0.2);
            box(local(base[0], (FELT_Y - 0.02) / 2, base[1]), ang, segLen * 0.9f, (float) (FELT_Y - 0.02), 0.2f, "hd_cream");
        }
        for (int s = 0; s < SEATS; s++) stool(s);
        // Dealerin an der Längsseite (-fwd), schaut zur Mitte
        Location dAt = local(0, 0, -(R + 0.55));
        dAt.setYaw(yaw);
        anim = new DealerAnim(plugin, w, dAt, yaw, e -> mark(e, "dealer"),
                hb -> hb.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":dealer:0"));
        dealerText = text(local(0, 2.25, -(R + 0.55)), Component.empty(), 0.85f, Display.Billboard.VERTICAL);
        // Chip-Tablett der Dealerin
        Location tray = local(0, FELT_Y + 0.02, -(R - 0.28));
        box(tray, a0, 0.9f, 0.04f, 0.22f, "hd_dark");
        String[] stacks = {"white", "red", "blue", "green", "black"};
        for (int i = 0; i < stacks.length; i++) {
            for (int k = 0; k < 3; k++) {
                item(local((i - 2) * 0.16, FELT_Y + 0.05 + BlackjackTable.chipStep(BlackjackTable.TRAY_CHIP) * k, -(R - 0.28)), "c3chip_" + stacks[i],
                        new Quaternionf().rotateY((float) (k * Math.PI / 8)), BlackjackTable.TRAY_CHIP, "static");
            }
        }
        // Stapel verdeckter Karten links der Dealerin
        for (int k = 0; k < 5; k++) {
            item(local(-0.75, FELT_Y + 0.006 + 0.006 * k, -(R - 0.32)), "c3_back", facingTop(localDir(0, -1)).rotateZ((float) Math.PI),
                    CARD * 0.95f, "static");
        }
        potBadge = badge(local(0.55, FELT_Y + 0.1, 0.5), 0.55f);
        dealerButton = item(seatPoint(0, -0.9, -0.35, FELT_Y + 0.01), "pk_dealer", new Quaternionf(), 0.22f, "button");
        dealerButton.setTeleportDuration(10);
        for (int s = 0; s < SEATS; s++) {
            plates[s] = text(seatLocation(s).add(0, 2.05, 0), Component.empty(), 0.55f, Display.Billboard.VERTICAL);
            betBadge[s] = badge(seatPoint(s, -0.95, -0.28, FELT_Y + 0.07), 0.45f);
            handHint[s] = badge(seatPoint(s, -0.5, 0.42, FELT_Y + 0.12), 0.42f);
            handHint[s].setVisibleByDefault(false);
        }
    }

    private void stool(int s) {
        Location base = seatLocation(s);
        Vector3f d = inward(s);
        float ang = angleOf(d);
        box(base.clone().add(0, 0.62, 0), ang, 0.5f, 0.12f, 0.5f, "hd_seat");
        box(base.clone().add(0, 0.55, 0), ang, 0.54f, 0.03f, 0.54f, "hd_gold");
        box(base.clone().add(0, 0.28, 0), ang + (float) (Math.PI / 4), 0.07f, 0.52f, 0.07f, "hd_gold");
        box(base.clone().add(0, 0.015, 0), ang + (float) (Math.PI / 4), 0.36f, 0.03f, 0.36f, "hd_gold");
        base.getWorld().spawn(base.clone(), Interaction.class, e -> {
            e.setInteractionWidth(0.6f);
            e.setInteractionHeight(0.75f);
            e.setResponsive(true);
            mark(e, "seat");
            e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":seat:" + s);
        });
    }

    public void removeAll() {
        World w = origin.getWorld();
        if (w == null) return;
        for (Entity e : w.getNearbyEntities(origin, S + R + 3, 4, S + R + 3)) {
            String v = e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            if (v != null && v.startsWith(id + ":")) {
                if (e instanceof ArmorStand a) a.eject();
                e.remove();
            }
        }
        for (int i = 0; i < SEATS; i++) {
            hole.get(i).clear();
            bets.get(i).clear();
            buttons.get(i).clear();
            hoverables.get(i).clear();
        }
        board.clear();
        pot.clear();
    }

    public boolean ready() {
        return dealerText != null && dealerText.isValid();
    }

    // =================================================================================================================
    // Texte
    // =================================================================================================================

    public void dealerText(Component c) {
        if (dealerText != null && dealerText.isValid()) dealerText.text(c);
    }

    public void plate(int seat, Component c) {
        if (plates[seat] != null && plates[seat].isValid()) plates[seat].text(c);
    }

    /** Nur für den Besitzer sichtbar: das aktuelle beste Blatt (z. B. "Zwei Paare"). */
    public void hint(int seat, Player owner, Component c) {
        TextDisplay t = handHint[seat];
        if (t == null || !t.isValid()) return;
        badgeText(t, c);
        if (owner != null) owner.showEntity(plugin, t);
    }

    public void hideHint(int seat, Player owner) {
        TextDisplay t = handHint[seat];
        if (t == null || !t.isValid()) return;
        badgeText(t, Component.empty());
        if (owner != null) owner.hideEntity(plugin, t);
    }

    public void pot(long amount, String sidePots, boolean jewels) {
        badgeText(potBadge, amount <= 0 ? Component.empty() : Component.text(" Pot " + CasinoText.num(amount) + " ", TextColor.color(0xF2C94C))
                .decorate(TextDecoration.BOLD).append(sidePots == null || sidePots.isEmpty() ? Component.empty()
                        : Component.newline().append(Component.text(sidePots, NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))));
        for (Entity e : pot) if (e.isValid()) e.remove();
        pot.clear();
        if (amount <= 0) return;
        int stacks = (int) Math.min(5, 1 + Math.log10(Math.max(1, amount)));
        for (int i = 0; i < stacks; i++) {
            int h = 2 + (int) ((amount >> i) % 4);
            for (int k = 0; k < h; k++) {
                pot.add(item(local((i - (stacks - 1) / 2.0) * 0.15, FELT_Y + 0.012 + BlackjackTable.chipStep(BlackjackTable.TRAY_CHIP) * k, 0.5 + (i % 2) * 0.08),
                        "c3chip_" + new String[]{"black", "green", "blue", "red", "white"}[i], new Quaternionf().rotateY((float) (k * Math.PI / 8)), BlackjackTable.TRAY_CHIP, "pot"));
            }
        }
    }

    // =================================================================================================================
    // Sitzen
    // =================================================================================================================

    /** Das eigene Namensschild schwebt über dem Kopf des Sitzenden: für ihn ausblenden, für alle anderen zeigen. */
    public void ownPlate(int seat, Player p, boolean seated) {
        TextDisplay t = plates[seat];
        if (t == null || !t.isValid() || p == null) return;
        if (seated) p.hideEntity(plugin, t);
        else p.showEntity(plugin, t);
    }

    public ArmorStand vehicle(int seat) {
        ArmorStand a = vehicles[seat];
        if (a != null && a.isValid()) return a;
        Location l = seatLocation(seat).add(0, 0.58, 0);
        Vector3f d = inward(seat);
        l.setYaw((float) Math.toDegrees(-Math.atan2(d.x, d.z)));
        a = l.getWorld().spawn(l, ArmorStand.class, e -> {
            e.setInvisible(true);
            e.setMarker(true);
            e.setGravity(false);
            e.setSmall(true);
            e.setInvulnerable(true);
            mark(e, "vehicle");
        });
        vehicles[seat] = a;
        return a;
    }

    public void removeVehicle(int seat) {
        ArmorStand a = vehicles[seat];
        if (a != null) {
            a.eject();
            a.remove();
        }
        vehicles[seat] = null;
    }

    // =================================================================================================================
    // Karten
    // =================================================================================================================

    /**
     * Handkarten eines Platzes: für alle verdeckt, für den Besitzer offen (zweite Anzeige, nur ihm gezeigt).
     * Kommen aus dem Kartenstapel der Dealerin geflogen.
     */
    public void holeCards(int seat, UUID owner, String[] models) {
        clearHole(seat);
        Vector3f in = inward(seat);
        for (int k = 0; k < models.length; k++) {
            Location target = seatPoint(seat, -0.52, (k - 0.5) * 0.34, FELT_Y + 0.006 + 0.003 * k);
            Quaternionf faceUp = facingTop(in).rotateY((float) Math.toRadians(k == 0 ? 4 : -4));
            Quaternionf faceDown = new Quaternionf(faceUp).rotateZ((float) Math.PI);
            Location from = local(-0.75, FELT_Y + 0.08, -(R - 0.32));
            ItemDisplay back = item(from, "c3_back", faceDown, CARD, "hole");
            ItemDisplay front = item(from, "c3_" + models[k], faceUp, CARD, "holef");
            front.setVisibleByDefault(false);
            org.bukkit.entity.Player p = owner == null ? null : org.bukkit.Bukkit.getPlayer(owner);
            if (p != null) {
                p.hideEntity(plugin, back);
                p.showEntity(plugin, front);
            }
            for (ItemDisplay e : new ItemDisplay[]{back, front}) {
                e.setTeleportDuration(7);
                long at = 1 + k * 3L;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (e.isValid()) e.teleport(target);
                }, at);
            }
            hole.get(seat).add(back);
            hole.get(seat).add(front);
        }
    }

    /** Showdown oder Zeigen: alle sehen die Karten offen (verdeckte ausblenden, offene für alle). */
    public void reveal(int seat) {
        for (Entity e : hole.get(seat)) {
            if (!(e instanceof ItemDisplay d) || !d.isValid()) continue;
            String role = d.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            boolean front = role != null && role.endsWith(":holef");
            if (front) {
                d.setVisibleByDefault(true);
                Transformation t = d.getTransformation();
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(4);
                d.setTransformation(new Transformation(new Vector3f(0, 0.06f, 0), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
            } else {
                d.remove();
            }
        }
    }

    /** Passen: Karten gleiten verdeckt zur Dealerin und verschwinden. */
    public void muck(int seat) {
        Location to = local(0, FELT_Y + 0.05, -(R - 0.5));
        for (Entity e : hole.get(seat)) {
            if (!(e instanceof ItemDisplay d) || !d.isValid()) continue;
            d.setTeleportDuration(8);
            d.teleport(to);
        }
        List<Entity> gone = new ArrayList<>(hole.get(seat));
        hole.get(seat).clear();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> gone.forEach(Entity::remove), 10L);
    }

    public void glowHole(int seat, int rgb) {
        for (Entity e : hole.get(seat)) {
            if (e instanceof ItemDisplay d && d.isValid()) {
                d.setGlowing(true);
                d.setGlowColorOverride(Color.fromRGB(rgb));
            }
        }
    }

    public void clearHole(int seat) {
        for (Entity e : hole.get(seat)) if (e.isValid()) e.remove();
        hole.get(seat).clear();
    }

    /** Gemeinschaftskarte k (0..4) aufdecken. */
    public void boardCard(int k, String model) {
        Location target = local((k - 2) * 0.36, FELT_Y + 0.006, -0.02);
        Quaternionf up = facingTop(localDir(0, -1)).rotateY((float) Math.PI);
        Location from = local(-0.75, FELT_Y + 0.08, -(R - 0.32));
        ItemDisplay e = item(from, "c3_" + model, new Quaternionf(up).rotateZ((float) Math.PI), CARD, "board");
        e.setTeleportDuration(7);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid()) e.teleport(target);
        }, 1L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!e.isValid()) return;
            e.setInterpolationDelay(0);
            e.setInterpolationDuration(4);
            e.setTransformation(new Transformation(new Vector3f(), up, new Vector3f(CARD, CARD, CARD), new Quaternionf()));
            e.getWorld().playSound(e.getLocation(), "minecraft:item.book.put", 0.6f, 1.7f);
        }, 9L);
        board.add(e);
    }

    public void glowBoard(int k, int rgb) {
        if (k < 0 || k >= board.size()) return;
        if (board.get(k) instanceof ItemDisplay d && d.isValid()) {
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(rgb));
        }
    }

    public void clearBoard() {
        for (Entity e : board) if (e.isValid()) e.remove();
        board.clear();
    }

    // =================================================================================================================
    // Einsätze und Dealer-Button
    // =================================================================================================================

    public void bet(int seat, long amount, boolean jewels) {
        for (Entity e : bets.get(seat)) if (e.isValid()) e.remove();
        bets.get(seat).clear();
        badgeText(betBadge[seat], amount <= 0 ? Component.empty() : Component.text(" " + CasinoText.num(amount) + (jewels ? " ◆" : "") + " ",
                TextColor.color(jewels ? 0x7FE3FF : 0xF2C94C)).decorate(TextDecoration.BOLD));
        if (amount <= 0) return;
        int n = (int) Math.min(8, 1 + Math.log(amount) / Math.log(3));
        for (int k = 0; k < n; k++) {
            bets.get(seat).add(item(seatPoint(seat, -0.95, 0, FELT_Y + 0.012 + BlackjackTable.chipStep(0.26f) * k),
                    "c3chip_" + BlackjackTable.chipColor(amount), new Quaternionf().rotateY((float) (k * Math.PI / 8)), 0.26f, "bet"));
        }
    }

    /** Einsätze aller Plätze rutschen in den Pot. */
    public void collectBets() {
        Location to = local(0, FELT_Y + 0.05, 0.5);
        for (List<Entity> l : bets) {
            for (Entity e : l) {
                if (!(e instanceof ItemDisplay d) || !d.isValid()) continue;
                d.setTeleportDuration(8);
                d.teleport(to);
            }
            List<Entity> gone = new ArrayList<>(l);
            l.clear();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> gone.forEach(Entity::remove), 10L);
        }
        for (TextDisplay t : betBadge) badgeText(t, Component.empty());
    }

    /** Pot rutscht zum Gewinner. */
    public void pushPot(int seat) {
        Location to = seatPoint(seat, -0.95, 0, FELT_Y + 0.05);
        for (Entity e : pot) {
            if (!(e instanceof ItemDisplay d) || !d.isValid()) continue;
            d.setTeleportDuration(12);
            d.teleport(to);
        }
        List<Entity> gone = new ArrayList<>(pot);
        pot.clear();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> gone.forEach(Entity::remove), 30L);
        badgeText(potBadge, Component.empty());
    }

    public void dealerButton(int seat) {
        if (dealerButton == null || !dealerButton.isValid() || seat < 0) return;
        dealerButton.teleport(seatPoint(seat, -0.9, -0.38, FELT_Y + 0.01));
    }

    // =================================================================================================================
    // Aktionsknöpfe und Zeitbalken
    // =================================================================================================================

    /** Knöpfe vor einem Platz; names = Modellnamen ohne "pk_btn_" (z. B. fold, call, raise, allin). */
    public void buttons(int seat, String[] names, Component raiseLabel) {
        clearButtons(seat);
        Vector3f in = inward(seat);
        int n = names.length;
        for (int i = 0; i < n; i++) {
            String name = names[i];
            if (name == null) continue;
            double side = (i - (n - 1) / 2.0) * 0.27;   // positive Seite = vom Spieler aus rechts
            Location l = seatPoint(seat, -0.07, side, RIM_TOP + 0.004);
            float sc = name.equals("minus") || name.equals("plus") ? 0.17f : 0.25f;
            ItemDisplay disp = item(l, "pk_btn_" + name, facingTop(in), sc, "btn");
            BlackjackTable.Button b = new BlackjackTable.Button(disp, l.clone(), disp.getTransformation());
            b.parts.add(disp);
            b.parts.add(l.getWorld().spawn(l.clone().add(0, -0.03, 0), Interaction.class, e -> {
                e.setInteractionWidth(0.24f);
                e.setInteractionHeight(0.08f);
                e.setResponsive(true);
                mark(e, "btn");
                e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":act:" + seat + ":" + name);
            }));
            hoverables.get(seat).add(b);
            buttons.get(seat).addAll(b.parts);
        }
        if (raiseLabel != null) {
            TextDisplay t = badge(seatPoint(seat, -0.3, 0, FELT_Y + 0.2), 0.4f);
            badgeText(t, raiseLabel);
            buttons.get(seat).add(t);
        }
        Location bl = seatPoint(seat, 0.045, 0, RIM_TOP + 0.004);
        buttons.get(seat).add(box(bl, angleOf(in), 0.9f, 0.008f, 0.05f, "hd_dark"));
        timer[seat] = box(bl.clone().add(0, 0.005, 0), angleOf(in), 0.86f, 0.008f, 0.032f, "bar");
        timer[seat].setInterpolationDuration(20);
        buttons.get(seat).add(timer[seat]);
    }

    public void timer(int seat, double frac) {
        ItemDisplay b = timer[seat];
        if (b == null || !b.isValid()) return;
        Transformation t = b.getTransformation();
        b.setInterpolationDelay(0);
        if (frac < 0.3) b.setItemStack(BlackjackTable.model("m_crashred"));
        b.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(),
                new Vector3f((float) Math.max(0.001, 0.86 * frac), t.getScale().y, t.getScale().z), t.getRightRotation()));
    }

    public void clearButtons(int seat) {
        for (Entity e : buttons.get(seat)) if (e.isValid()) e.remove();
        buttons.get(seat).clear();
        hoverables.get(seat).clear();
        timer[seat] = null;
        hovered[seat] = -1;
    }

    /** Hover wie am Blackjack-Tisch: Knopf unter dem Blick hebt sich und leuchtet. */
    public boolean hover(int seat, Location eye, Vector look) {
        List<BlackjackTable.Button> all = hoverables.get(seat);
        int best = -1;
        double bd = 0.13;
        Vector o = eye.toVector();
        for (int i = 0; i < all.size(); i++) {
            Vector oc = all.get(i).center.toVector().subtract(o);
            double t = oc.dot(look);
            if (t < 0) continue;
            double dist = oc.clone().subtract(look.clone().multiply(t)).length();
            if (dist < bd) {
                bd = dist;
                best = i;
            }
        }
        if (best == hovered[seat]) return false;
        for (int i = 0; i < all.size(); i++) {
            BlackjackTable.Button b = all.get(i);
            if (!b.display.isValid()) continue;
            boolean on = i == best;
            b.display.setInterpolationDelay(0);
            b.display.setInterpolationDuration(3);
            b.display.setTransformation(new Transformation(new Vector3f(0, on ? 0.03f : 0f, 0), b.base.getLeftRotation(),
                    new Vector3f(b.base.getScale()).mul(on ? 1.22f : 1f), b.base.getRightRotation()));
            b.display.setGlowing(on);
            if (on) b.display.setGlowColorOverride(Color.fromRGB(0xFFE680));
        }
        hovered[seat] = best;
        return best >= 0;
    }

    /** Dealer-Geste beim Austeilen (ohne Ziel: vor sich). */
    public void dealerGesture() {
        if (anim != null) anim.deal(null);
    }

    /** Animationen des Dealers (nie null nach dem Aufbau). */
    public DealerAnim dealer() {
        return anim;
    }

    public Location center() {
        return origin.clone().add(0, FELT_Y, 0);
    }

    public boolean near(Location l, double range) {
        return l.getWorld() != null && l.getWorld().equals(origin.getWorld()) && l.distanceSquared(origin) <= range * range;
    }
}
