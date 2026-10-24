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
import org.bukkit.entity.Mannequin;
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
 * Ein Blackjack-Tisch aus Anzeigen wie im Clip: halbrunder Tisch mit grünem Filz, brauner Polsterkante und
 * Holzsockel, fünf gelbe Kartenfelder, fünf Hocker (Goldgestell, grünes Polster), Dealer mit Hologramm, Chip-Rack
 * und Kartenschlitten. Dazu die wechselnden Anzeigen einer Runde (Karten, Chips, Aktionsfelder, Texte).
 *
 * <p>Lage: origin = Boden an der Mitte der geraden Kante (Dealer-Seite), fwd zeigt zu den Spielern.
 * Alle Anzeigen werden nicht gespeichert und beim Laden des Chunks neu gebaut.
 */
public final class BlackjackTable {

    public static final int SEATS = 5;
    private static final double[] SEAT_ANGLES = {-60, -30, 0, 30, 60};
    private static final double FELT_Y = 1.0;
    private static final double RIM_Y = 1.14;

    public final String id;
    private final org.bukkit.plugin.Plugin plugin;
    private final NamespacedKey tag;
    private final NamespacedKey click;
    private final Location origin;
    private final float yaw;
    private final double radius;

    private Mannequin dealer;
    private TextDisplay dealerText;
    private final TextDisplay[] seatText = new TextDisplay[SEATS];
    private final TextDisplay[] resultText = new TextDisplay[SEATS];
    private final ArmorStand[] seatVehicle = new ArmorStand[SEATS];
    /** Wechselnde Anzeigen je Platz (Karten, Chips, Rack, Aktionsfelder) und für den Dealer (Index SEATS). */
    private final List<List<Entity>> cards = new ArrayList<>();
    private final List<List<Entity>> chips = new ArrayList<>();
    private final List<List<Entity>> rack = new ArrayList<>();
    private final List<List<Entity>> actions = new ArrayList<>();
    private final ItemDisplay[] timerBar = new ItemDisplay[SEATS];
    private final TextDisplay[] totals = new TextDisplay[SEATS + 1];

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
            actions.add(new ArrayList<>());
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

    /** Ort auf einer Platz-Achse: dist vom Mittelpunkt, side quer dazu (positiv nach rechts vom Spieler aus links). */
    private Location onSeatAxis(int seat, double dist, double side, double up) {
        Vector3f d = dir(SEAT_ANGLES[seat]);
        Vector3f t = new Vector3f(-d.z, 0, d.x);
        return origin.clone().add(d.x * dist + t.x * side, up, d.z * dist + t.z * side);
    }

    private static float angleOf(Vector3f d) {
        return (float) Math.atan2(d.x, d.z);
    }

    /** Abstand der Dealer-Karten von der geraden Tischkante. */
    private static final double DEALER_CARDS = 0.64;

    /**
     * Flaches Item auf dem Tisch, Vorderseite oben, Oberkante zeigt in Richtung -dir(angle). Der Client dreht Items um
     * 180° (Vorderseite nach -Z), deshalb zuerst zurückdrehen.
     */
    private static Quaternionf flatItem(float angle) {
        return new Quaternionf().rotateY(angle).rotateX((float) Math.toRadians(-90)).rotateY((float) Math.PI);
    }

    public Location seatLocation(int seat) {
        return onSeatAxis(seat, radius + 0.5, 0, 0);
    }

    /** Fester Punkt der Runde: Kartenfeld eines Platzes (Mitte). */
    public Location field(int seat) {
        return onSeatAxis(seat, radius - 1.1, 0, FELT_Y);
    }

    // =================================================================================================================
    // Aufbau
    // =================================================================================================================

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

    /** Quader aus einem Material, Mitte bei center, um die Y-Achse gedreht (angle), Größe (Breite, Höhe, Tiefe). */
    private ItemDisplay box(Location center, float angle, float w, float h, float d, String material) {
        return center.getWorld().spawn(center, ItemDisplay.class, e -> {
            e.setItemStack(model("m_" + material));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(angle), new Vector3f(w, h, d),
                    new Quaternionf()));
            mark(e, "static");
        });
    }

    public void build() {
        World w = origin.getWorld();
        if (w == null) return;
        removeAll();
        int n = 24;
        double step = 180.0 / n;
        double feltR = radius - 0.3;
        for (int i = 0; i < n; i++) {
            double a = -90 + step * (i + 0.5);
            Vector3f d = dir(a);
            float ang = angleOf(d);
            float chord = (float) (2 * feltR * Math.sin(Math.toRadians(step / 2)) * 1.04);
            box(at(d, feltR / 2, FELT_Y - 0.03), ang, chord, 0.06f, (float) feltR, "felt");
            float rimLen = (float) (2 * radius * Math.tan(Math.toRadians(step / 2)) * 1.03);
            box(at(d, radius - 0.21, RIM_Y - 0.11), ang, rimLen, 0.22f, 0.42f, "rim");
            float baseLen = (float) (2 * (radius - 0.1) * Math.tan(Math.toRadians(step / 2)) * 1.03);
            box(at(d, radius - 0.27, 0.46), ang, baseLen, 0.92f, 0.3f, "wood");
        }
        // gerade Kante (Dealer-Seite) und Filz-Streifen dahinter
        Vector3f r = right();
        box(origin.clone().add(0, 0.53, 0), angleOf(fwd()), (float) (2 * radius), 1.06f, 0.16f, "wood");
        // Kartenfelder
        for (int s = 0; s < SEATS; s++) {
            Vector3f d = dir(SEAT_ANGLES[s]);
            box(field(s).add(0, 0.006, 0), angleOf(d), 0.5f, 0.012f, 0.42f, "gold");
        }
        // Hocker
        for (int s = 0; s < SEATS; s++) stool(s);
        // Dealer, Hologramm, Rack und Schlitten
        Location dealerAt = at(fwd(), -0.75, 0);
        dealerAt.setYaw(yaw);
        dealer = w.spawn(dealerAt, Mannequin.class, m -> {
            m.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile().name("Dealer")
                    .skinPatch(sp -> sp.body(Key.key("kuby", "entity/casino/croupier"))
                            .model(org.bukkit.profile.PlayerTextures.SkinModel.SLIM)).build());
            m.setImmovable(true);
            m.setDescription(null);
            m.setAI(false);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setCustomNameVisible(false);
            mark(m, "dealer");
            m.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":dealer:0");
        });
        dealerText = text(at(fwd(), -0.75, 2.25), Component.empty(), 0.9f, true);
        // Chip-Rack des Dealers mittig vor ihm: dunkles Tablett mit goldener Kante (wie im Clip)
        Location rackAt = origin.clone().add(fwd().x * 0.3, FELT_Y + 0.03, fwd().z * 0.3);
        box(rackAt, angleOf(fwd()), 0.95f, 0.05f, 0.24f, "black");
        box(rackAt.clone().add(fwd().x * 0.12, 0.01, fwd().z * 0.12), angleOf(fwd()), 0.95f, 0.04f, 0.025f, "gold");
        String[] stacks = {"white", "pink", "blue", "green", "dark"};
        for (int i = 0; i < stacks.length; i++) {
            Location c = rackAt.clone().add(r.x * (i - 2) * 0.17, 0.12, r.z * (i - 2) * 0.17);
            int k = i;
            w.spawn(c, ItemDisplay.class, e -> {
                e.setItemStack(model("stack_" + stacks[k]));
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(angleOf(fwd()) + (float) Math.PI),
                        new Vector3f(0.22f, 0.22f, 0.22f), new Quaternionf()));
                mark(e, "static");
            });
        }
        // Kartenschlitten rechts vom Dealer (aus Spielersicht)
        Location shoeAt = origin.clone().add(fwd().x * 0.42 - r.x * 1.3, FELT_Y + 0.12, fwd().z * 0.42 - r.z * 1.3);
        box(shoeAt, angleOf(fwd()), 0.32f, 0.22f, 0.42f, "black");
        box(shoeAt.clone().add(0, 0.115, 0), angleOf(fwd()), 0.34f, 0.02f, 0.44f, "gold");
        w.spawn(shoeAt.clone().add(fwd().x * 0.23, -0.02, fwd().z * 0.23), ItemDisplay.class, e -> {
            e.setItemStack(model("card_back"));
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(angleOf(fwd()) + (float) Math.PI)
                    .rotateX((float) Math.toRadians(-70)).rotateY((float) Math.PI), new Vector3f(0.32f, 0.32f, 0.32f), new Quaternionf()));
            mark(e, "static");
        });
        // Aufdruck auf dem Filz
        TextDisplay rules = text(at(fwd(), 1.12, FELT_Y + 0.01), Component.text("BLACKJACK ZAHLT 3 ZU 2", TextColor.color(0xE8B23A))
                .append(Component.newline()).append(Component.text("Dealer zieht bis 16, steht ab 17", TextColor.color(0x8E9A7A))),
                0.4f, false);
        flatToDealer(rules, 0.4f);
        for (int s = 0; s < SEATS; s++) {
            seatText[s] = text(seatLocation(s).add(0, 1.55, 0), Component.empty(), 0.7f, true);
            resultText[s] = text(field(s).add(0, 0.55, 0), Component.empty(), 0.75f, true);
        }
        for (int s = 0; s <= SEATS; s++) {
            Location tl = s == SEATS ? at(fwd(), DEALER_CARDS, FELT_Y + 0.01).add(r.x * -0.74, 0, r.z * -0.74)
                    : onSeatAxis(s, radius - 1.45, -0.4, FELT_Y + 0.01);
            totals[s] = text(tl, Component.empty(), 0.6f, false);
            if (s < SEATS) flatText(totals[s], s, 0.6f);
            else flatToDealer(totals[s], 0.6f);
        }
    }

    private void stool(int s) {
        Location base = seatLocation(s);
        Vector3f d = dir(SEAT_ANGLES[s]);
        float ang = angleOf(d);
        Vector3f t = new Vector3f(-d.z, 0, d.x);
        box(base.clone().add(0, 0.76, 0), ang, 0.56f, 0.12f, 0.56f, "cushion");
        box(base.clone().add(0, 0.685, 0), ang, 0.62f, 0.05f, 0.62f, "frame");
        for (int i = -1; i <= 1; i += 2) {
            for (int j = -1; j <= 1; j += 2) {
                Location leg = base.clone().add(t.x * 0.24 * i + d.x * 0.24 * j, 0.33, t.z * 0.24 * i + d.z * 0.24 * j);
                box(leg, ang, 0.07f, 0.66f, 0.07f, "frame");
            }
        }
        for (int j = -1; j <= 1; j += 2) {
            box(base.clone().add(d.x * 0.24 * j, 0.25, d.z * 0.24 * j), ang, 0.5f, 0.05f, 0.05f, "frame");
        }
        Location ia = base.clone();
        base.getWorld().spawn(ia, Interaction.class, e -> {
            e.setInteractionWidth(0.7f);
            e.setInteractionHeight(0.95f);
            e.setResponsive(true);
            mark(e, "seat");
            e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":seat:" + s);
        });
    }

    private TextDisplay text(Location l, Component c, float scale, boolean billboard) {
        return l.getWorld().spawn(l, TextDisplay.class, e -> {
            e.text(c);
            e.setBillboard(billboard ? Display.Billboard.VERTICAL : Display.Billboard.FIXED);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setLineWidth(240);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale),
                    new Quaternionf()));
            mark(e, "text");
        });
    }

    /** Text flach auf dem Filz, lesbar vom Platz aus. */
    private void flatText(TextDisplay e, int seat, float scale) {
        Vector3f d = dir(SEAT_ANGLES[seat]);
        // Oberkante zeigt zur Tischmitte: Richtung "nach außen" = d
        Quaternionf q = new Quaternionf().rotateY(angleOf(d)).rotateX((float) Math.toRadians(-90));
        Vector3f t = new Vector3f(0, -0.12f * scale, 0);
        q.transform(t);
        e.setShadowed(false);
        e.setTransformation(new Transformation(t, q, new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    private void flatToDealer(TextDisplay e, float scale) {
        Quaternionf q = new Quaternionf().rotateY(angleOf(fwd())).rotateX((float) Math.toRadians(-90));
        e.setShadowed(false);
        e.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(scale, scale, scale), new Quaternionf()));
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
            actions.get(i).clear();
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

    public void result(int seat, Component c) {
        if (resultText[seat] != null && resultText[seat].isValid()) resultText[seat].text(c);
    }

    /** Handwert unter den Karten (seat = SEATS für den Dealer). */
    public void total(int seat, String text) {
        if (totals[seat] != null && totals[seat].isValid()) {
            totals[seat].text(text.isEmpty() ? Component.empty() : Component.text(text, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
        }
    }

    // =================================================================================================================
    // Sitzen
    // =================================================================================================================

    public ArmorStand seatVehicle(int seat) {
        ArmorStand a = seatVehicle[seat];
        if (a != null && a.isValid()) return a;
        // Reiter sitzt 0,6 unter dem Sitzpunkt, Hüfte 0,7 über seinen Füßen: Hüfte genau auf dem Polster (0,82)
        Location l = seatLocation(seat).add(0, 0.72, 0);
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

    /**
     * Legt eine Karte. seat = SEATS: Dealer. hand = Index der Hand (nach Teilen 0 oder 1), k = Karte in der Hand.
     * card = null: verdeckt.
     */
    public ItemDisplay card(int seat, int hand, int k, BjCards.Card card, int handsAtSeat) {
        Location l;
        float ang;
        if (seat == SEATS) {
            Vector3f r = right();
            double side = (k - 1) * 0.25 - 0.1;
            l = at(fwd(), DEALER_CARDS, FELT_Y + 0.008 + 0.004 * k).add(r.x * side, 0, r.z * side);
            ang = angleOf(fwd());
        } else {
            double base = handsAtSeat > 1 ? (hand == 0 ? -0.32 : 0.32) : 0;
            l = onSeatAxis(seat, radius - 1.6, base + k * 0.15 - 0.07, FELT_Y + 0.008 + 0.004 * k);
            // Oberkante der Karte zeigt zur Tischmitte: vom Platz aus richtig herum lesbar
            ang = angleOf(dir(SEAT_ANGLES[seat]));
        }
        float a = ang;
        ItemDisplay e = l.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(model(card == null ? "card_back" : card.model()));
            d.setTransformation(new Transformation(new Vector3f(0, 0.3f, 0), flatItem(a),
                    new Vector3f(0.56f, 0.56f, 0.56f), new Quaternionf()));
            d.setInterpolationDuration(5);
            mark(d, "card");
        });
        // fällt einen Tick später auf den Tisch (Interpolation braucht eine Änderung nach dem Erscheinen)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!e.isValid()) return;
            e.setInterpolationDelay(0);
            e.setTransformation(new Transformation(new Vector3f(), flatItem(a),
                    new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
        }, 2L);
        cards.get(seat).add(e);
        return e;
    }

    public void reveal(ItemDisplay e, BjCards.Card card) {
        if (e != null && e.isValid()) e.setItemStack(model(card.model()));
    }

    public void clearCards() {
        for (List<Entity> l : cards) {
            for (Entity e : l) if (e.isValid()) e.remove();
            l.clear();
        }
        for (int s = 0; s <= SEATS; s++) total(s, "");
    }

    // =================================================================================================================
    // Chips
    // =================================================================================================================

    public static String chipColor(long amount) {
        return amount >= 5000 ? "purple" : amount >= 1000 ? "black" : amount >= 500 ? "green" : amount >= 100 ? "blue"
                : amount >= 50 ? "red" : "white";
    }

    /** Zeigt den Einsatz auf dem Feld: Stapel flacher Chips und die Summe daneben. */
    public void bet(int seat, List<Long> stack, long total) {
        for (Entity e : chips.get(seat)) if (e.isValid()) e.remove();
        chips.get(seat).clear();
        if (total <= 0) return;
        Location f = field(seat);
        int shown = Math.min(stack.size(), 10);
        for (int i = 0; i < shown; i++) {
            long v = stack.get(stack.size() - shown + i);
            Location l = f.clone().add(0, 0.016 + 0.022 * i, 0);
            chips.get(seat).add(l.getWorld().spawn(l, ItemDisplay.class, e -> {
                e.setItemStack(model("chip_" + chipColor(v)));
                e.setTransformation(new Transformation(new Vector3f(), flatItem((float) (Math.random() * 0.6)), new Vector3f(0.3f, 0.3f, 0.3f), new Quaternionf()));
                mark(e, "chip");
            }));
        }
        Location tl = onSeatAxis(seat, radius - 1.1, -0.4, FELT_Y + 0.06);
        TextDisplay t = tl.getWorld().spawn(tl, TextDisplay.class, e -> {
            e.text(Component.text(CasinoText.num(total), TextColor.color(0xF2C94C)).decorate(TextDecoration.BOLD));
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.42f, 0.42f, 0.42f), new Quaternionf()));
            mark(e, "chip");
        });
        chips.get(seat).add(t);
    }

    /** Chips fliegen zum Spieler (gewonnen) oder zum Dealer (verloren) und verschwinden. */
    public void sweepChips(int seat, boolean toPlayer) {
        Vector3f d = dir(SEAT_ANGLES[seat]);
        float dist = toPlayer ? 1.4f : -(float) (radius - 0.6);
        for (Entity e : chips.get(seat)) {
            if (e instanceof Display disp && disp.isValid()) {
                Transformation t = disp.getTransformation();
                disp.setInterpolationDelay(0);
                disp.setInterpolationDuration(12);
                disp.setTransformation(new Transformation(new Vector3f(d.x * dist, 0.3f, d.z * dist), t.getLeftRotation(),
                        t.getScale(), t.getRightRotation()));
            }
        }
    }

    public void clearChips(int seat) {
        for (Entity e : chips.get(seat)) if (e.isValid()) e.remove();
        chips.get(seat).clear();
    }

    /** Chip-Rack vor dem Platz (während der Einsätze): klickbare Chips mit Wert darüber. */
    public void rack(int seat, long[] values) {
        clearRack(seat);
        if (values == null) return;
        for (int i = 0; i < values.length; i++) {
            long v = values[i];
            double side = ((values.length - 1) / 2.0 - i) * 0.21;
            Location l = onSeatAxis(seat, radius - 0.72, side, FELT_Y + 0.012);
            Vector3f d = dir(SEAT_ANGLES[seat]);
            rack.get(seat).add(l.getWorld().spawn(l, ItemDisplay.class, e -> {
                e.setItemStack(model("chip_" + chipColor(v)));
                e.setTransformation(new Transformation(new Vector3f(), flatItem(angleOf(d)), new Vector3f(0.24f, 0.24f, 0.24f), new Quaternionf()));
                mark(e, "rack");
            }));
            rack.get(seat).add(l.getWorld().spawn(l.clone().add(0, 0.13, 0), TextDisplay.class, e -> {
                e.text(Component.text(CasinoText.num(v), NamedTextColor.WHITE));
                e.setBillboard(Display.Billboard.VERTICAL);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setShadowed(true);
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.26f, 0.26f, 0.26f),
                        new Quaternionf()));
                mark(e, "rack");
            }));
            int k = i;
            rack.get(seat).add(l.getWorld().spawn(l.clone().add(0, -0.02, 0), Interaction.class, e -> {
                e.setInteractionWidth(0.19f);
                e.setInteractionHeight(0.12f);
                e.setResponsive(true);
                mark(e, "rack");
                e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":chip:" + seat + ":" + k);
            }));
        }
    }

    public void clearRack(int seat) {
        for (Entity e : rack.get(seat)) if (e.isValid()) e.remove();
        rack.get(seat).clear();
    }

    // =================================================================================================================
    // Aktionsfelder und Zeitbalken
    // =================================================================================================================

    public static final String[] ACTIONS = {"Karte", "Halten", "Doppeln", "Teilen"};

    public void actions(int seat, boolean[] allowed) {
        clearActions(seat);
        Vector3f d = dir(SEAT_ANGLES[seat]);
        for (int i = 0; i < ACTIONS.length; i++) {
            if (!allowed[i]) continue;
            double side = (1.5 - i) * 0.37;
            Location l = onSeatAxis(seat, radius - 0.72, side, FELT_Y + 0.008);
            actions.get(seat).add(box(l, angleOf(d), 0.34f, 0.016f, 0.15f, "gold"));
            actions.get(seat).get(actions.get(seat).size() - 1).getPersistentDataContainer()
                    .set(tag, PersistentDataType.STRING, id + ":action");
            String label = ACTIONS[i];
            TextDisplay t = l.getWorld().spawn(l.clone().add(0, 0.01, 0), TextDisplay.class, e -> {
                e.text(Component.text(label, TextColor.color(0x0A9A0A)).decorate(TextDecoration.BOLD));
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                mark(e, "action");
            });
            flatText(t, seat, 0.42f);
            actions.get(seat).add(t);
            int k = i;
            actions.get(seat).add(l.getWorld().spawn(l.clone().add(0, -0.03, 0), Interaction.class, e -> {
                e.setInteractionWidth(0.3f);
                e.setInteractionHeight(0.08f);
                e.setResponsive(true);
                mark(e, "action");
                e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":act:" + seat + ":" + k);
            }));
        }
        Location bl = onSeatAxis(seat, radius - 0.28, 0, RIM_Y + 0.004);
        actions.get(seat).add(box(bl, angleOf(d), 1.2f, 0.01f, 0.07f, "black"));
        timerBar[seat] = box(bl.clone().add(0, 0.006, 0), angleOf(d), 1.16f, 0.01f, 0.05f, "bar");
        timerBar[seat].setInterpolationDuration(20);
        actions.get(seat).add(timerBar[seat]);
    }

    /** Zeitbalken: frac 1 = voll, 0 = leer (schrumpft zur Mitte hin). */
    public void timer(int seat, double frac) {
        ItemDisplay b = timerBar[seat];
        if (b == null || !b.isValid()) return;
        Transformation t = b.getTransformation();
        b.setInterpolationDelay(0);
        b.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(),
                new Vector3f((float) Math.max(0.001, 1.16 * frac), t.getScale().y, t.getScale().z), t.getRightRotation()));
    }

    public void clearActions(int seat) {
        for (Entity e : actions.get(seat)) if (e.isValid()) e.remove();
        actions.get(seat).clear();
        timerBar[seat] = null;
    }

    public Location dealerLocation() {
        return dealer != null && dealer.isValid() ? dealer.getLocation() : at(fwd(), -0.75, 0);
    }

    public boolean contains(Location l) {
        if (l.getWorld() == null || !l.getWorld().equals(origin.getWorld())) return false;
        double dx = l.getX() - origin.getX(), dz = l.getZ() - origin.getZ();
        return dx * dx + dz * dz <= (radius + 0.3) * (radius + 0.3) && l.getY() >= origin.getY() - 0.5
                && l.getY() <= origin.getY() + 1.3;
    }
}
