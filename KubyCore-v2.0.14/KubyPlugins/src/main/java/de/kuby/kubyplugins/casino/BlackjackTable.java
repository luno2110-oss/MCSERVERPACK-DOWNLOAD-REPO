package de.kuby.kubyplugins.casino;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Blackjack-Tisch im Clean-Look (v1.68): ein durchgehender Filz in hoher Auflösung (512 x 256 px, mit Aufdruck,
 * Einsatzkreisen und feinen Kartenecken), schwarze Leder-Armauflage, Sockel in Creme-Lack mit Goldband, gerade
 * Bankkante aus Nussholz, fünf Barhocker (Creme-Leder, Goldfuß). Der Tisch ist niedriger als vorher (Filz 0,8 statt
 * 1,0), vom Hocker aus liegen Karten, Chips und alle Zahlen frei im Blick.
 *
 * <p>Alle Lagen auf dem Filz sind Anteile am Radius und passen zum Aufdruck (tools/build_casino_hd.py). Karten sind
 * 3D-Körper (Vorderseite oben, Rückseite unten): sie kommen verdeckt aus dem Schlitten, gleiten an ihren Platz und
 * werden umgedreht. Zahlen schweben als kleine Plaketten neben Karten und Chips, nie darüber.
 *
 * <p>Lage: origin = Boden an der Mitte der geraden Kante (Dealer-Seite), fwd zeigt zu den Spielern.
 * Alle Anzeigen werden nicht gespeichert und beim Laden des Chunks neu gebaut.
 */
public final class BlackjackTable {

    public static final int SEATS = 5;
    private static final double[] SEAT_ANGLES = {-60, -30, 0, 30, 60};
    static final double FELT_Y = 0.80;
    private static final double RIM_TOP = FELT_Y + 0.10;
    // Anteile am Radius, gleich wie im Aufdruck des Filzes
    private static final double F_RIM_IN = 0.885;
    private static final double F_BET = 0.70;
    private static final double F_CARDS = 0.50;
    private static final double F_RACK = 0.832;
    private static final double F_DEALER_CARDS = 0.21;
    private static final float CARD_SCALE = 0.48f;
    private static final float CHIP_SCALE = 0.30f;
    /** Chips im Tablett/Pot: 0,152 breit, damit Nachbarstapel (Abstand 0,15–0,16) sich nicht durchdringen. */
    public static final float TRAY_CHIP = 0.19f;

    /** Höhe eines 3D-Chips bei dieser Skalierung (Modell 1,28 / 16): Stapel liegen lückenlos, ohne Überlappung. */
    public static double chipStep(float scale) {
        return 0.08 * scale;
    }

    public final String id;
    private final org.bukkit.plugin.Plugin plugin;
    private final NamespacedKey tag;
    private final NamespacedKey click;
    private final Location origin;
    private final float yaw;
    private final double radius;

    private DealerAnim anim;
    private TextDisplay dealerText;
    private TextDisplay dealerBadge;
    private final TextDisplay[] seatText = new TextDisplay[SEATS];
    private final TextDisplay[] resultText = new TextDisplay[SEATS];
    private final TextDisplay[] streakText = new TextDisplay[SEATS];
    private final TextDisplay[] totals = new TextDisplay[SEATS];
    private final ArmorStand[] seatVehicle = new ArmorStand[SEATS];
    private final List<List<Entity>> cards = new ArrayList<>();
    private final List<List<Entity>> chips = new ArrayList<>();
    private final List<List<Button>> rack = new ArrayList<>();
    private final List<List<Button>> controls = new ArrayList<>();
    private final List<List<Button>> actions = new ArrayList<>();
    private final List<List<Entity>> extras = new ArrayList<>();
    private final ItemDisplay[] timerBar = new ItemDisplay[SEATS];
    private final int[] hovered = {-1, -1, -1, -1, -1};

    /** Klickbares Teil vor einem Platz: Anzeige, Mitte (für den Blick-Test) und Grund-Transformation (für Hover). */
    public static final class Button {
        final ItemDisplay display;
        final Location center;
        final Transformation base;
        final List<Entity> parts = new ArrayList<>();

        Button(ItemDisplay display, Location center, Transformation base) {
            this.display = display;
            this.center = center;
            this.base = base;
        }
    }

    /** Weinroter High-Roller-Tisch (höhere Einsätze, Serie bis x8). */
    private TableTier tier = TableTier.GRUEN;

    public BlackjackTable highRoller(boolean hr) {
        this.tier = hr ? TableTier.ROT : TableTier.GRUEN;
        return this;
    }

    public BlackjackTable tier(TableTier t) {
        this.tier = t == null ? TableTier.GRUEN : t;
        return this;
    }

    public TableTier tier() {
        return tier;
    }

    public boolean isHighRoller() {
        return tier.highRoller();
    }

    public BlackjackTable(org.bukkit.plugin.Plugin plugin, String id, Location origin, float yaw, double radius,
                          NamespacedKey tag, NamespacedKey click) {
        this.plugin = plugin;
        this.id = id;
        this.origin = origin.clone();
        this.yaw = yaw;
        this.radius = radius;
        this.tag = tag;
        this.click = click;
        for (int i = 0; i <= SEATS; i++) {
            cards.add(new ArrayList<>());
            chips.add(new ArrayList<>());
            rack.add(new ArrayList<>());
            controls.add(new ArrayList<>());
            actions.add(new ArrayList<>());
            extras.add(new ArrayList<>());
        }
    }

    public Location origin() {
        return origin.clone();
    }

    public float yaw() {
        return yaw;
    }

    public double radius() {
        return radius;
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

    /** Richtung zum Winkel (0 = geradeaus zu den Spielern, positiv nach rechts). */
    private Vector3f dir(double deg) {
        double a = Math.toRadians(deg);
        Vector3f f = fwd(), r = right();
        return new Vector3f((float) (f.x * Math.cos(a) + r.x * Math.sin(a)), 0, (float) (f.z * Math.cos(a) + r.z * Math.sin(a)));
    }

    private Location at(Vector3f d, double dist, double up) {
        return origin.clone().add(d.x * dist, up, d.z * dist);
    }

    /** Ort auf einer Platz-Achse: dist vom Mittelpunkt, side quer dazu (positiv = vom Spieler aus links). */
    private Location onSeatAxis(int seat, double dist, double side, double up) {
        Vector3f d = dir(SEAT_ANGLES[seat]);
        Vector3f t = new Vector3f(-d.z, 0, d.x);
        return origin.clone().add(d.x * dist + t.x * side, up, d.z * dist + t.z * side);
    }

    private static float angleOf(Vector3f d) {
        return (float) Math.atan2(d.x, d.z);
    }

    /** Drehung für Modelle, deren Oberkante (-Z) zur Tischmitte zeigen soll, wenn der Leser auf Achse d sitzt. */
    private static Quaternionf facing(Vector3f d) {
        return new Quaternionf().rotateY(angleOf(d) + (float) Math.PI);
    }

    public Location seatLocation(int seat) {
        return onSeatAxis(seat, radius + 0.55, 0, 0);
    }

    /** Einsatzkreis eines Platzes (Mitte, auf dem Filz). */
    public Location field(int seat) {
        return onSeatAxis(seat, radius * F_BET, 0, FELT_Y);
    }

    /** Mitte der Karten eines Platzes. */
    public Location cardsAt(int seat) {
        return onSeatAxis(seat, radius * F_CARDS, 0, FELT_Y);
    }

    private Location shoeAt() {
        Vector3f r = right();
        return origin.clone().add(fwd().x * 0.36 - r.x * radius * 0.42, FELT_Y + 0.11, fwd().z * 0.36 - r.z * radius * 0.42);
    }

    // =================================================================================================================
    // Aufbau
    // =================================================================================================================

    public static ItemStack model(String name) {
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

    /** Quader aus einem Material, Mitte bei center, um die Y-Achse gedreht (angle), Größe (Breite, Höhe, Tiefe). */
    private ItemDisplay box(Location center, float angle, float w, float h, float d, String material) {
        return center.getWorld().spawn(center, ItemDisplay.class, e -> {
            e.setItemStack(model("m_" + material));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(angle), new Vector3f(w, h, d),
                    new Quaternionf()));
            e.setViewRange(1.5f);
            mark(e, "static");
        });
    }

    private ItemDisplay item(Location at, String model, Quaternionf rot, float scale, String role) {
        return at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(model(model));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(scale, scale, scale), new Quaternionf()));
            mark(e, role);
        });
    }

    public void build() {
        World w = origin.getWorld();
        if (w == null) return;
        removeAll();
        // Filz: eine Fläche, Halbkreis mit Radius R, Bankkante bei z = 0
        origin.getWorld().spawn(origin.clone().add(0, FELT_Y, 0), ItemDisplay.class, e -> {
            e.setItemStack(model(tier.feltBlackjack()));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), facing(fwd()),
                    new Vector3f((float) (2 * radius), 1f, (float) (2 * radius)), new Quaternionf()));
            e.setViewRange(1.5f);
            mark(e, "static");
        });
        int n = 28;
        double step = 180.0 / n;
        double rimIn = radius * F_RIM_IN, rimOut = radius + 0.04;
        for (int i = 0; i < n; i++) {
            double a = -90 + step * (i + 0.5);
            Vector3f d = dir(a);
            float ang = angleOf(d);
            double rimMid = (rimIn + rimOut) / 2;
            float rimLen = (float) (2 * rimOut * Math.tan(Math.toRadians(step / 2)) * 1.02);
            box(at(d, rimMid, RIM_TOP - 0.08), ang, rimLen, 0.16f, (float) (rimOut - rimIn), "hd_leather");
            float baseLen = (float) (2 * (radius - 0.1) * Math.tan(Math.toRadians(step / 2)) * 1.03);
            box(at(d, radius - 0.16, (FELT_Y - 0.02) / 2), ang, baseLen, (float) (FELT_Y - 0.02), 0.2f, "hd_cream");
        }
        // gerade Kante (Dealer-Seite): Nussholz mit Goldleiste
        box(origin.clone().add(0, (FELT_Y + 0.05) / 2, 0), angleOf(fwd()), (float) (2 * radius + 0.08), (float) (FELT_Y + 0.05), 0.14f, "hd_walnut");
        box(origin.clone().add(0, FELT_Y + 0.055, 0), angleOf(fwd()), (float) (2 * radius + 0.08), 0.012f, 0.15f, "hd_gold");
        for (int s = 0; s < SEATS; s++) stool(s);
        // Dealer
        Location dealerAt = at(fwd(), -0.6, 0);
        dealerAt.setYaw(yaw);
        anim = new DealerAnim(plugin, w, dealerAt, yaw, e -> mark(e, "dealer"),
                hb -> hb.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":dealer:0"));
        dealerText = text(at(fwd(), -0.6, 2.25), Component.empty(), 0.85f, Display.Billboard.VERTICAL);
        dealerText.setViewRange(0.3f);      // ~19 Blöcke: aus der Ferne überlagern sich die Schilder vieler Tische
        // Chip-Tablett der Bank: schwarz glänzend, sechs Stapel echter 3D-Chips
        Vector3f r = right();
        Location trayAt = origin.clone().add(fwd().x * 0.2, FELT_Y + 0.02, fwd().z * 0.2);
        box(trayAt, angleOf(fwd()), 1.02f, 0.04f, 0.24f, "hd_dark");
        box(trayAt.clone().add(fwd().x * 0.12, 0.01, fwd().z * 0.12), angleOf(fwd()), 1.02f, 0.05f, 0.012f, "hd_gold");
        String[] stacks = {"white", "red", "blue", "green", "black", "purple"};
        for (int i = 0; i < stacks.length; i++) {
            double side = (i - 2.5) * 0.16;
            for (int k = 0; k < 3; k++) {
                Location c = trayAt.clone().add(r.x * side, 0.03 + chipStep(TRAY_CHIP) * k, r.z * side);
                item(c, "c3chip_" + stacks[i], new Quaternionf().rotateY((float) (k * Math.PI / 8)), TRAY_CHIP, "static");
            }
        }
        // Kartenschlitten (aus Spielersicht links) und Ablage (rechts)
        Location shoe = shoeAt();
        box(shoe, angleOf(fwd()), 0.3f, 0.2f, 0.42f, "hd_dark");
        box(shoe.clone().add(0, 0.105, 0), angleOf(fwd()), 0.31f, 0.012f, 0.43f, "hd_gold");
        Location face = shoe.clone().add(fwd().x * 0.2, 0.02, fwd().z * 0.2);
        item(face, "c3_back", new Quaternionf().rotateY(angleOf(fwd()) + (float) Math.PI).rotateX((float) Math.toRadians(-62)),
                CARD_SCALE * 0.9f, "static");
        Location discard = origin.clone().add(fwd().x * 0.34 + r.x * radius * 0.42, FELT_Y + 0.05, fwd().z * 0.34 + r.z * radius * 0.42);
        box(discard, angleOf(fwd()), 0.3f, 0.1f, 0.4f, "hd_dark");
        for (int k = 0; k < 4; k++) {
            item(discard.clone().add(0, 0.055 + 0.006 * k, 0), "c3_back", new Quaternionf().rotateY(angleOf(fwd()) + (float) (0.05 * k)),
                    CARD_SCALE * 0.82f, "static");
        }
        for (int s = 0; s < SEATS; s++) {
            seatText[s] = text(seatLocation(s).add(0, 1.4 + (s % 2) * 0.22, 0), Component.empty(), 0.5f, Display.Billboard.VERTICAL);
            seatText[s].setViewRange(0.18f);  // ~11 Blöcke: „Freier Platz“ nur aus der Nähe (im echten Client überlagerten sie sich)
            // Nachbarplätze in der Höhe versetzt (im echten Client lagen die Ergebnisse von der Tischmitte aus nebeneinander
            // auf einer Linie und gingen ineinander über)
            double lift = (s % 2) * 0.3;
            resultText[s] = text(cardsAt(s).add(0, 0.55 + lift, 0), Component.empty(), 0.62f, Display.Billboard.CENTER);
            streakText[s] = text(cardsAt(s).add(0, 0.47 + lift, 0), Component.empty(), 0.5f, Display.Billboard.CENTER);
            totals[s] = badge(onSeatAxis(s, radius * F_CARDS + 0.12, 0.44, FELT_Y + 0.09)); // weit genug neben der ersten Karte, damit die Zahl aus keinem Winkel Karten verdeckt
        }
        dealerBadge = badge(at(fwd(), radius * F_DEALER_CARDS + 0.05, FELT_Y + 0.1).add(r.x * 0.5, 0, r.z * 0.5));
    }

    private void stool(int s) {
        Location base = seatLocation(s);
        Vector3f d = dir(SEAT_ANGLES[s]);
        float ang = angleOf(d);
        box(base.clone().add(0, 0.62, 0), ang, 0.5f, 0.12f, 0.5f, "hd_seat");
        box(base.clone().add(0, 0.55, 0), ang, 0.54f, 0.03f, 0.54f, "hd_gold");
        box(base.clone().add(0, 0.28, 0), ang + (float) (Math.PI / 4), 0.07f, 0.52f, 0.07f, "hd_gold");
        box(base.clone().add(0, 0.015, 0), ang + (float) (Math.PI / 4), 0.36f, 0.03f, 0.36f, "hd_gold");
        // Fußstütze vorn (zum Tisch hin), mit einer Strebe fest am Mittelrohr (schwebte vorher frei vor dem Hocker)
        box(base.clone().add(-d.x * 0.17, 0.24, -d.z * 0.17), ang, 0.34f, 0.03f, 0.03f, "hd_gold");
        box(base.clone().add(-d.x * 0.095, 0.24, -d.z * 0.095), ang, 0.03f, 0.03f, 0.16f, "hd_gold");
        base.getWorld().spawn(base.clone(), Interaction.class, e -> {
            e.setInteractionWidth(0.6f);
            e.setInteractionHeight(0.75f);
            e.setResponsive(true);
            mark(e, "seat");
            e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":seat:" + s);
        });
    }

    private TextDisplay text(Location l, Component c, float scale, Display.Billboard bb) {
        return l.getWorld().spawn(l, TextDisplay.class, e -> {
            e.text(c);
            e.setBillboard(bb);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setLineWidth(240);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale),
                    new Quaternionf()));
            mark(e, "text");
        });
    }

    /** Kleine Zahl-Plakette (dunkel, abgerundet wirkend), schwebt neben Karten oder Chips. */
    private TextDisplay badge(Location l) {
        return l.getWorld().spawn(l, TextDisplay.class, e -> {
            e.text(Component.empty());
            e.setBillboard(Display.Billboard.CENTER);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(false);
            e.setSeeThrough(false);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55f, 0.55f, 0.55f),
                    new Quaternionf()));
            mark(e, "text");
        });
    }

    private static void badgeText(TextDisplay e, Component c) {
        if (e == null || !e.isValid()) return;
        boolean empty = c == null || c.equals(Component.empty());
        e.text(empty ? Component.empty() : c);
        e.setBackgroundColor(empty ? Color.fromARGB(0, 0, 0, 0) : Color.fromARGB(200, 14, 16, 20));
    }

    public void removeAll() {
        World w = origin.getWorld();
        if (w == null) return;
        for (Entity e : w.getNearbyEntities(origin, radius + 3, 4, radius + 3)) {
            String v = e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            if (v != null && v.startsWith(id + ":")) {
                if (e instanceof ArmorStand a) a.eject();
                e.remove();
            }
        }
        for (int i = 0; i <= SEATS; i++) {
            cards.get(i).clear();
            chips.get(i).clear();
            rack.get(i).clear();
            controls.get(i).clear();
            actions.get(i).clear();
            extras.get(i).clear();
        }
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

    public void seatText(int seat, Component c) {
        if (seatText[seat] != null && seatText[seat].isValid()) seatText[seat].text(c);
    }

    /** Ergebnis über den Karten, springt kurz groß auf und setzt sich. */
    public void result(int seat, Component c) {
        TextDisplay t = resultText[seat];
        if (t == null || !t.isValid()) return;
        t.text(c);
        if (c.equals(Component.empty())) return;
        t.setInterpolationDelay(0);
        t.setInterpolationDuration(0);
        t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.2f, 0.2f, 0.2f), new Quaternionf()));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!t.isValid()) return;
            t.setInterpolationDelay(0);
            t.setInterpolationDuration(4);
            t.setTransformation(new Transformation(new Vector3f(0, 0.06f, 0), new Quaternionf(), new Vector3f(0.85f, 0.85f, 0.85f),
                    new Quaternionf()));
        }, 2L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!t.isValid()) return;
            t.setInterpolationDelay(0);
            t.setInterpolationDuration(5);
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.62f, 0.62f, 0.62f), new Quaternionf()));
        }, 7L);
    }

    public void streak(int seat, Component c) {
        if (streakText[seat] != null && streakText[seat].isValid()) streakText[seat].text(c);
    }

    /** Handwert neben den Karten (seat = SEATS für den Dealer). Leer = ausblenden. */
    public void total(int seat, String text) {
        TextDisplay t = seat == SEATS ? dealerBadge : totals[seat];
        if (text.isEmpty()) {
            badgeText(t, Component.empty());
            return;
        }
        TextColor col = NamedTextColor.WHITE;
        String plain = text.replace("BJ", "");
        if (text.startsWith("BJ")) col = TextColor.color(0xFFD34D);
        else if (text.contains("21") && !text.contains("|")) col = TextColor.color(0x7CFF9A);
        else {
            try {
                if (Integer.parseInt(plain.contains("/") ? plain.substring(plain.indexOf('/') + 1) : plain) > 21) col = TextColor.color(0xFF6B6B);
            } catch (NumberFormatException ignored) {
            }
        }
        badgeText(t, Component.text(" " + text + " ", col).decorate(TextDecoration.BOLD));
    }

    // =================================================================================================================
    // Sitzen
    // =================================================================================================================

    public ArmorStand seatVehicle(int seat) {
        ArmorStand a = seatVehicle[seat];
        if (a != null && a.isValid()) return a;
        // Hüfte genau auf dem Polster (Oberkante 0,68)
        Location l = seatLocation(seat).add(0, 0.58, 0);
        l.setYaw(yaw + 180 + (float) SEAT_ANGLES[seat]);
        a = l.getWorld().spawn(l, ArmorStand.class, e -> {
            e.setInvisible(true);
            e.setMarker(true);
            e.setGravity(false);
            e.setSmall(true);
            e.setInvulnerable(true);
            mark(e, "vehicle");
        });
        seatVehicle[seat] = a;
        return a;
    }

    public void removeVehicle(int seat) {
        ArmorStand a = seatVehicle[seat];
        if (a != null) {
            a.eject();
            a.remove();
        }
        seatVehicle[seat] = null;
    }

    // =================================================================================================================
    // Karten
    // =================================================================================================================

    private Location cardTarget(int seat, int hand, int k, int handsAtSeat) {
        if (seat == SEATS) {
            Vector3f r = right();
            double side = (k - 0.5) * 0.33 - 0.05 * Math.max(0, k - 1);
            return at(fwd(), radius * F_DEALER_CARDS, FELT_Y + 0.004 + 0.003 * k).add(r.x * side, 0, r.z * side);
        }
        double base = handsAtSeat > 1 ? (hand == 0 ? 0.3 : -0.3) : 0;
        return onSeatAxis(seat, radius * F_CARDS + 0.03 * k, base - k * 0.12 + 0.06, FELT_Y + 0.004 + 0.003 * k);
    }

    private Quaternionf cardRot(int seat, boolean faceUp, int k) {
        Vector3f d = seat == SEATS ? fwd() : dir(SEAT_ANGLES[seat]);
        Quaternionf q = facing(d).rotateY((float) Math.toRadians((k % 2 == 0 ? 1 : -1) * 2.5));
        if (!faceUp) q.rotateZ((float) Math.PI);
        return q;
    }

    /**
     * Teilt eine Karte aus: sie kommt verdeckt aus dem Schlitten, gleitet in 6 Ticks an ihren Platz und wird (wenn card
     * nicht null) umgedreht. seat = SEATS: Dealer. hand = Index der Hand (nach Teilen 0 oder 1), k = Karte in der Hand.
     */
    public ItemDisplay card(int seat, int hand, int k, BjCards.Card card, int handsAtSeat) {
        Location target = cardTarget(seat, hand, k, handsAtSeat);
        Location from = shoeAt().add(0, 0.05, 0);
        Quaternionf down = cardRot(seat, false, k);
        ItemDisplay e = from.getWorld().spawn(from, ItemDisplay.class, d -> {
            d.setItemStack(model(card == null ? "c3_back" : "c3_" + card.model().substring("card_".length())));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), down, new Vector3f(CARD_SCALE, CARD_SCALE, CARD_SCALE), new Quaternionf()));
            d.setTeleportDuration(6);
            mark(d, "card");
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid()) e.teleport(target);
        }, 1L);
        if (card != null) flip(e, seat, k, 8L);
        cards.get(seat).add(e);
        return e;
    }

    private void flip(ItemDisplay e, int seat, int k, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!e.isValid()) return;
            e.setInterpolationDelay(0);
            e.setInterpolationDuration(3);
            Quaternionf half = cardRot(seat, true, k).rotateZ((float) (Math.PI / 2));
            e.setTransformation(new Transformation(new Vector3f(0, 0.12f, 0), half, new Vector3f(CARD_SCALE, CARD_SCALE, CARD_SCALE), new Quaternionf()));
        }, delay);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!e.isValid()) return;
            e.setInterpolationDelay(0);
            e.setInterpolationDuration(3);
            e.setTransformation(new Transformation(new Vector3f(), cardRot(seat, true, k), new Vector3f(CARD_SCALE, CARD_SCALE, CARD_SCALE),
                    new Quaternionf()));
            e.getWorld().playSound(e.getLocation(), "minecraft:item.book.put", 0.5f, 1.8f);
        }, delay + 3);
    }

    /** Deckt die verdeckte Karte des Dealers auf (Umdrehen wie beim Austeilen). */
    public void reveal(ItemDisplay e, BjCards.Card card) {
        if (e == null || !e.isValid()) return;
        e.setItemStack(model("c3_" + card.model().substring("card_".length())));
        int k = cards.get(SEATS).indexOf(e);
        flip(e, SEATS, Math.max(0, k), 1L);
    }

    /** Überkauft: die Karten des Platzes wackeln kurz und werden dunkler (rot leuchtender Rand). */
    public void bust(int seat) {
        for (Entity c : cards.get(seat)) {
            if (!(c instanceof ItemDisplay d) || !d.isValid()) continue;
            Transformation t = d.getTransformation();
            for (int i = 0; i < 4; i++) {
                float dx = (i % 2 == 0 ? 0.03f : -0.03f);
                long at = 1 + i * 2L;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!d.isValid()) return;
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(2);
                    d.setTransformation(new Transformation(new Vector3f(dx, 0.02f, 0), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
                }, at);
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!d.isValid()) return;
                d.setInterpolationDelay(0);
                d.setTransformation(t);
                d.setGlowing(true);
                d.setGlowColorOverride(Color.fromRGB(0xE83A34));
            }, 10L);
        }
    }

    /** Gewinner-Leuchten (Gold bei Blackjack, Grün sonst) für die Karten eines Platzes. */
    public void glow(int seat, int rgb) {
        for (Entity c : cards.get(seat)) {
            if (c instanceof ItemDisplay d && d.isValid()) {
                d.setGlowing(true);
                d.setGlowColorOverride(Color.fromRGB(rgb));
            }
        }
    }

    public void clearCards() {
        for (List<Entity> l : cards) {
            for (Entity e : l) if (e.isValid()) e.remove();
            l.clear();
        }
        for (int s = 0; s <= SEATS; s++) total(s, "");
    }

    /** Karten wandern am Rundenende in die Ablage (statt einfach zu verschwinden). */
    public void collectCards() {
        Vector3f r = right();
        Location discard = origin.clone().add(fwd().x * 0.34 + r.x * radius * 0.42, FELT_Y + 0.12, fwd().z * 0.34 + r.z * radius * 0.42);
        int i = 0;
        for (List<Entity> l : cards) {
            for (Entity e : l) {
                if (!(e instanceof ItemDisplay d) || !d.isValid()) continue;
                long at = 1 + (i++ / 2);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!d.isValid()) return;
                    d.setGlowing(false);
                    d.setTeleportDuration(6);
                    d.teleport(discard);
                }, at);
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, this::clearCards, 10L + i / 2);
    }

    // =================================================================================================================
    // Chips
    // =================================================================================================================

    public static String chipColor(long amount) {
        return amount >= 100_000 ? "pink" : amount >= 25_000 ? "gold" : amount >= 5000 ? "purple" : amount >= 1000 ? "black" : amount >= 500 ? "green" : amount >= 100 ? "blue"
                : amount >= 50 ? "red" : "white";
    }

    private static String chipColor(long amount, boolean jewels) {
        if (!jewels) return chipColor(amount);
        return amount >= 5000 ? "pink" : amount >= 1000 ? "gold" : amount >= 500 ? "purple" : "blue";
    }

    private boolean jewels;

    /** Juwelen-Modus des Tisches für die Chip-Farben (wird je Platz vom Spiel gesetzt). */
    public void jewels(boolean j) {
        this.jewels = j;
    }

    /** Zeigt den Einsatz auf dem Einsatzkreis: Stapel echter Chips und die Summe als Plakette daneben. */
    public void bet(int seat, List<Long> stack, long total) {
        bet(seat, stack, total, false);
    }

    public void bet(int seat, List<Long> stack, long total, boolean jw) {
        for (Entity e : chips.get(seat)) if (e.isValid()) e.remove();
        chips.get(seat).clear();
        if (total <= 0) return;
        Location f = field(seat);
        int shown = Math.min(stack.size(), 12);
        for (int i = 0; i < shown; i++) {
            long v = stack.get(stack.size() - shown + i);
            Location l = f.clone().add(0, 0.012 + chipStep(CHIP_SCALE) * i, 0);
            float rot = (float) (i * Math.PI / 8); // 22,5°-Schritte: Kanten der Chips liegen übereinander
            chips.get(seat).add(item(l, "c3chip_" + chipColor(v, jw), new Quaternionf().rotateY(rot), CHIP_SCALE, "chip"));
        }
        TextDisplay t = badge(onSeatAxis(seat, radius * F_BET + 0.06, -0.3, FELT_Y + 0.05));
        badgeText(t, Component.text(" " + CasinoText.num(total) + (jw ? " ◆" : "") + " ", TextColor.color(jw ? 0x7FE3FF : 0xF2C94C))
                .decorate(TextDecoration.BOLD));
        mark(t, "chip");
        chips.get(seat).add(t);
    }

    /** Ein Chip fliegt vom Rack (Index) auf den Einsatzkreis. */
    public void flyChip(int seat, int index, int count, long value, boolean jw) {
        Location from = rackSpot(seat, index, count);
        Location to = field(seat).add(0, 0.05 + chipStep(CHIP_SCALE) * chips.get(seat).size(), 0);
        ItemDisplay e = item(from.clone().add(0, 0.05, 0), "c3chip_" + chipColor(value, jw), new Quaternionf(), CHIP_SCALE, "fly");
        e.setTeleportDuration(4);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid()) e.teleport(to.clone().add(0, 0.08, 0));
        }, 1L);
        plugin.getServer().getScheduler().runTaskLater(plugin, e::remove, 6L);
    }

    /** Gewinn: die Bank legt Chips dazu (kurz sichtbar), dann rutscht alles zum Spieler. Verlust: alles zur Bank. */
    public void sweepChips(int seat, boolean toPlayer) {
        if (toPlayer) {
            Location f = field(seat);
            Vector3f d = dir(SEAT_ANGLES[seat]);
            for (int i = 0; i < 4; i++) {
                Location from = origin.clone().add(fwd().x * 0.25, FELT_Y + 0.1, fwd().z * 0.25);
                ItemDisplay e = item(from, "c3chip_" + (jewels ? "gold" : "green"), new Quaternionf(), CHIP_SCALE, "chip");
                e.setTeleportDuration(7);
                Location to = f.clone().add(-d.z * 0.16 * (i % 2 == 0 ? 1 : -1), 0.012 + chipStep(CHIP_SCALE) * (i / 2), d.x * 0.16 * (i % 2 == 0 ? 1 : -1));
                long at = 2 + i * 2L;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (e.isValid()) e.teleport(to);
                }, at);
                chips.get(seat).add(e);
            }
        }
        Vector3f d = dir(SEAT_ANGLES[seat]);
        float dist = toPlayer ? 1.2f : -(float) (radius * F_BET - 0.2);
        long delay = toPlayer ? 22L : 4L;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Entity e : chips.get(seat)) {
                if (e instanceof Display disp && disp.isValid()) {
                    Transformation t = disp.getTransformation();
                    disp.setInterpolationDelay(0);
                    disp.setInterpolationDuration(12);
                    disp.setTransformation(new Transformation(new Vector3f(d.x * dist, 0.25f, d.z * dist), t.getLeftRotation(),
                            t.getScale(), t.getRightRotation()));
                }
            }
        }, delay);
    }

    public void clearChips(int seat) {
        for (Entity e : chips.get(seat)) if (e.isValid()) e.remove();
        chips.get(seat).clear();
    }

    // =================================================================================================================
    // Rack, Bedienknöpfe und Aktionen (mit Hover)
    // =================================================================================================================

    private Location rackSpot(int seat, int i, int count) {
        double side = ((count - 1) / 2.0 - i) * 0.2;
        return onSeatAxis(seat, radius * F_RACK, side, FELT_Y + 0.01);
    }

    /** Chip-Rack vor dem Platz (während der Einsätze): klickbare 3D-Chips mit Wert darüber. */
    public void rack(int seat, long[] values) {
        rack(seat, values, false);
    }

    public void rack(int seat, long[] values, boolean jw) {
        clearRack(seat);
        if (values == null) return;
        for (int i = 0; i < values.length; i++) {
            long v = values[i];
            Location l = rackSpot(seat, i, values.length);
            ItemDisplay chip = item(l.clone().add(0, 0.012, 0), "c3chip_" + chipColor(v, jw),
                    new Quaternionf().rotateY((float) (i * 0.6)), 0.24f, "rack");
            Button b = new Button(chip, l.clone().add(0, 0.02, 0), chip.getTransformation());
            b.parts.add(chip);
            TextDisplay t = l.getWorld().spawn(l.clone().add(0, 0.085, 0), TextDisplay.class, e -> {
                e.text(Component.text(short_(v), jw ? TextColor.color(0x7FE3FF) : NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
                e.setBillboard(Display.Billboard.CENTER);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setShadowed(true);
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.24f, 0.24f, 0.24f), new Quaternionf()));
                mark(e, "rack");
            });
            b.parts.add(t);
            int k = i;
            b.parts.add(l.getWorld().spawn(l.clone().add(0, -0.02, 0), Interaction.class, e -> {
                e.setInteractionWidth(0.18f);
                e.setInteractionHeight(0.12f);
                e.setResponsive(true);
                mark(e, "rack");
                e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":chip:" + seat + ":" + k);
            }));
            rack.get(seat).add(b);
        }
    }

    private static String short_(long v) {
        if (v >= 1000 && v % 1000 == 0) return (v / 1000) + "k";
        return CasinoText.num(v);
    }

    public void clearRack(int seat) {
        for (Button b : rack.get(seat)) for (Entity e : b.parts) if (e.isValid()) e.remove();
        rack.get(seat).clear();
        if (hovered[seat] >= 0 && hovered[seat] < 100) hovered[seat] = -1;
    }

    /** Bedienknöpfe auf der Armauflage: Wiederholen, Zurücknehmen und (für VIP) Chips/Juwelen. */
    public static final String[] CONTROLS = {"repeat", "clear", "currency"};

    public void controls(int seat, boolean repeat, boolean clear, Boolean jewelsMode) {
        clearControls(seat);
        String[][] list = {
                {"repeat", repeat ? "bj_ctl_repeat" : null, "Wie zuletzt"},
                {"clear", clear ? "bj_ctl_clear" : null, "Zurücknehmen"},
                {"currency", jewelsMode == null ? null : jewelsMode ? "bj_ctl_jewels" : "bj_ctl_chips", jewelsMode != null && jewelsMode ? "Juwelen" : "Chips"},
        };
        Vector3f d = dir(SEAT_ANGLES[seat]);
        for (int i = 0; i < list.length; i++) {
            if (list[i][1] == null) continue;
            Location l = onSeatAxis(seat, (radius * F_RIM_IN + radius + 0.04) / 2, (1 - i) * 0.24, RIM_TOP + 0.004);
            ItemDisplay disp = item(l, list[i][1], facing(d), 0.2f, "ctl");
            Button b = new Button(disp, l.clone(), disp.getTransformation());
            b.parts.add(disp);
            int k = i;
            b.parts.add(l.getWorld().spawn(l.clone().add(0, -0.03, 0), Interaction.class, e -> {
                e.setInteractionWidth(0.2f);
                e.setInteractionHeight(0.07f);
                e.setResponsive(true);
                mark(e, "ctl");
                e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":ctl:" + seat + ":" + list[k][0]);
            }));
            controls.get(seat).add(b);
        }
    }

    public void clearControls(int seat) {
        for (Button b : controls.get(seat)) for (Entity e : b.parts) if (e.isValid()) e.remove();
        controls.get(seat).clear();
    }

    public static final String[] ACTIONS = {"Karte", "Halten", "Doppeln", "Teilen"};
    private static final String[] ACTION_MODELS = {"bj_btn_hit", "bj_btn_stand", "bj_btn_double", "bj_btn_split"};

    public void actions(int seat, boolean[] allowed) {
        clearActions(seat);
        Vector3f d = dir(SEAT_ANGLES[seat]);
        for (int i = 0; i < ACTIONS.length; i++) {
            if (!allowed[i]) continue;
            double side = (1.5 - i) * 0.27;
            Location l = onSeatAxis(seat, radius * F_RACK, side, FELT_Y + 0.008);
            ItemDisplay disp = item(l, ACTION_MODELS[i], facing(d), 0.25f, "action");
            Button b = new Button(disp, l.clone(), disp.getTransformation());
            b.parts.add(disp);
            int k = i;
            b.parts.add(l.getWorld().spawn(l.clone().add(0, -0.03, 0), Interaction.class, e -> {
                e.setInteractionWidth(0.24f);
                e.setInteractionHeight(0.08f);
                e.setResponsive(true);
                mark(e, "action");
                e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":act:" + seat + ":" + k);
            }));
            actions.get(seat).add(b);
        }
        Location bl = onSeatAxis(seat, (radius * F_RIM_IN + radius + 0.04) / 2, 0, RIM_TOP + 0.004);
        Button bg = new Button(box(bl, angleOf(d), 1.0f, 0.008f, 0.05f, "hd_dark"), bl, null);
        bg.parts.add(bg.display);
        timerBar[seat] = box(bl.clone().add(0, 0.005, 0), angleOf(d), 0.96f, 0.008f, 0.032f, "bar");
        timerBar[seat].setInterpolationDuration(20);
        bg.parts.add(timerBar[seat]);
        extras.get(seat).addAll(bg.parts);
    }

    /** Zeitbalken: frac 1 = voll, 0 = leer (schrumpft zur Mitte hin), unter 30 % rot. */
    public void timer(int seat, double frac) {
        ItemDisplay b = timerBar[seat];
        if (b == null || !b.isValid()) return;
        Transformation t = b.getTransformation();
        b.setInterpolationDelay(0);
        if (frac < 0.3) b.setItemStack(model("m_crashred"));
        b.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(),
                new Vector3f((float) Math.max(0.001, 0.96 * frac), t.getScale().y, t.getScale().z), t.getRightRotation()));
    }

    public void clearActions(int seat) {
        for (Button b : actions.get(seat)) for (Entity e : b.parts) if (e.isValid()) e.remove();
        actions.get(seat).clear();
        for (Entity e : extras.get(seat)) if (e.isValid()) e.remove();
        extras.get(seat).clear();
        timerBar[seat] = null;
    }

    /**
     * Hover: der Knopf, auf den der sitzende Spieler schaut, hebt sich und wird größer. Gibt true zurück, wenn sich
     * etwas geändert hat (für einen leisen Ton).
     */
    public boolean hover(int seat, Location eye, Vector look) {
        List<Button> all = new ArrayList<>();
        all.addAll(rack.get(seat));
        all.addAll(actions.get(seat));
        all.addAll(controls.get(seat));
        int best = -1;
        double bd = 0.13;
        Vector o = eye.toVector();
        for (int i = 0; i < all.size(); i++) {
            Vector c = all.get(i).center.toVector();
            Vector oc = c.clone().subtract(o);
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
            Button b = all.get(i);
            if (b.base == null || !b.display.isValid()) continue;
            boolean on = i == best;
            Vector3f s = new Vector3f(b.base.getScale()).mul(on ? 1.28f : 1f);
            b.display.setInterpolationDelay(0);
            b.display.setInterpolationDuration(3);
            b.display.setTransformation(new Transformation(new Vector3f(0, on ? 0.035f : 0f, 0), b.base.getLeftRotation(), s,
                    b.base.getRightRotation()));
            b.display.setGlowing(on);
            if (on) b.display.setGlowColorOverride(Color.fromRGB(0xFFE680));
        }
        hovered[seat] = best;
        return best >= 0;
    }

    public Location dealerLocation() {
        return anim != null ? anim.location() : at(fwd(), -0.6, 0);
    }

    /** Dealer-Geste beim Austeilen (ohne Ziel: vor sich). */
    public void dealerGesture() {
        if (anim != null) anim.deal(null);
    }

    /** Animationen des Dealers (nie null nach dem Aufbau). */
    public DealerAnim dealer() {
        return anim;
    }

    public Location shoeLocation() {
        return shoeAt();
    }

    public boolean contains(Location l) {
        if (l.getWorld() == null || !l.getWorld().equals(origin.getWorld())) return false;
        double dx = l.getX() - origin.getX(), dz = l.getZ() - origin.getZ();
        return dx * dx + dz * dz <= (radius + 0.3) * (radius + 0.3) && l.getY() >= origin.getY() - 0.5
                && l.getY() <= origin.getY() + 1.1;
    }
}
