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

import java.util.ArrayList;
import java.util.List;

/**
 * Das Plinko-Brett an der Wand (v2.05) aus Anzeigen, in Ebenen wie die Skyblock-Generator-Grafiken (Lieblingsstil des
 * Besitzers): hinten die dunkle Rückwand, darauf das Innenfeld, das zur Mitte hin aufglüht, davor die Stifte und Fächer,
 * ganz vorn der Goldrahmen mit Nieten an den Ecken. Oben der Schriftzug „PLINKO“ und der Einwurf-Trichter, links oben
 * Spieler/Risiko/Einsatz, rechts oben „ZULETZT“ (die letzten sechs Kugeln), unten das Bedienfeld: drei Risiko-Tafeln,
 * Einsatz-Tafel (Klick höher, Schleichen + Klick tiefer) und der Abwurf-Knopf „BALL“.
 *
 * <p>Koordinaten wie in {@link Plinko} (u rechts, v hoch, f nach vorn aus der Wand), mal {@code size}. center = Mitte
 * des Bretts auf der Wandoberfläche.
 */
public final class PlinkoBoard {

    /** Unterkante der Rückwand (unter dem Bedienfeld) und Oberkante (über dem Schriftzug), in Brett-Einheiten. */
    public static final double BOTTOM = -2.45, TOP = 2.52;
    private static final double HALF_W = 1.9;
    /** Vorderkante des Innenfelds, Mitte der Stifte, Ebene der Kugel. */
    private static final double F_INNER = 0.08, F_PEG = 0.105, F_BALL = 0.17;
    /** Versatz gegen Z-Fighting (Flächen zweier Teile nie in derselben Ebene, v2.04). */
    private static final float ZF = 0.0024f;
    public static final double CTRL_V = -2.2;
    public static final double[] RISK_U = {-1.45, -1.0, -0.55};
    public static final double BET_U = 0.2, DROP_U = 1.25;
    private static final int HISTORY = 6;
    public static final int[] LOGO = {0xFF6EC4, 0xF24AAE, 0xC860E0, 0xA070F0, 0xF8CC2A, 0xFCEB7A};

    private final String id;
    private final Location center;
    private final float frontYaw;
    private final double size;
    private final NamespacedKey tag;
    private final NamespacedKey click;

    private final ItemDisplay[][] pegs = new ItemDisplay[Plinko.ROWS][];
    private final int[][] pegLitUntil = new int[Plinko.ROWS][];
    private final ItemDisplay[] slots = new ItemDisplay[Plinko.SLOTS];
    private final int[] slotLitUntil = new int[Plinko.SLOTS];
    private final TextDisplay[] slotText = new TextDisplay[Plinko.SLOTS];
    private final ItemDisplay[] riskPlate = new ItemDisplay[3];
    private final ItemDisplay[] histPlate = new ItemDisplay[HISTORY];
    private final TextDisplay[] histText = new TextDisplay[HISTORY];
    private ItemDisplay button;
    private int buttonLitUntil;
    private TextDisplay info;
    private TextDisplay bet;
    private int tick;

    public PlinkoBoard(String id, Location center, float frontYaw, double size, NamespacedKey tag, NamespacedKey click) {
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

    // =================================================================================================================
    // Geometrie
    // =================================================================================================================

    private Vector3f front() {
        double r = Math.toRadians(frontYaw);
        return new Vector3f((float) -Math.sin(r), 0, (float) Math.cos(r));
    }

    /** Rechts aus Sicht des Spielers vor dem Brett. */
    private Vector3f right() {
        Vector3f n = front();
        return new Vector3f(n.z, 0, -n.x);
    }

    /** Weltpunkt zu Brett-Koordinaten (u rechts, v hoch, f vor der Wand). */
    public Location at(double u, double v, double f) {
        Vector3f r = right(), n = front();
        double k = size;
        return center.clone().add((r.x * u + n.x * f) * k, v * k, (r.z * u + n.z * f) * k);
    }

    private Quaternionf face() {
        Vector3f n = front();
        return new Quaternionf().rotateY((float) Math.atan2(n.x, n.z));
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

    /** Quader (Material m_&lt;mat&gt;): Mitte bei (u, v, f), Größe w × h × d in Brett-Einheiten, um die Normale gedreht. */
    private ItemDisplay box(double u, double v, double f, double w, double h, double d, double roll, String mat) {
        Quaternionf q = new Quaternionf(face()).rotateZ((float) roll);
        float k = (float) size;
        return center.getWorld().spawn(at(u, v, f), ItemDisplay.class, e -> {
            e.setItemStack(model("m_" + mat));
            e.setTransformation(new Transformation(new Vector3f(), q, new Vector3f((float) w * k, (float) h * k, (float) d * k), new Quaternionf()));
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

    /** Klickfläche: Quadrat w (an der Wand und in den Raum), Unterkante bei v0, Höhe h. */
    private void interaction(double u, double v0, double w, double h, String what) {
        Location l = at(u, v0, w / 2);
        float k = (float) size;
        l.getWorld().spawn(l, Interaction.class, e -> {
            e.setInteractionWidth((float) w * k);
            e.setInteractionHeight((float) h * k);
            e.setResponsive(true);
            mark(e, "click");
            e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":" + what);
        });
    }

    // =================================================================================================================
    // Aufbau
    // =================================================================================================================

    public boolean ready() {
        return button != null && button.isValid();
    }

    public void build() {
        if (center.getWorld() == null) return;
        removeAll();
        double vMid = (TOP + BOTTOM) / 2, hAll = TOP - BOTTOM;
        // hinten: Rückwand (dunkel), darauf das Innenfeld, das zur Mitte aufglüht
        box(0, vMid, 0.03, 2 * HALF_W, hAll, 0.06, 0, "hd_plinko_dark");
        double inTop = Plinko.DROP_V + 0.18, inBot = Plinko.SLOT_V - 0.27;
        box(0, (inTop + inBot) / 2, 0.07, 3.5, inTop - inBot, 0.02, 0, "hd_plinko");
        // Goldrahmen ums Innenfeld (senkrechte Leisten 1,2 mm weiter vorn: die Ecken überlappen sonst in einer Ebene)
        // waagrechte Leisten 2,4 mm kürzer, senkrechte 2,4 mm niedriger: Enden und Oberseiten liegen sonst bündig
        box(0, inTop + 0.04, 0.095, 3.66 - 2 * ZF, 0.08, 0.07, 0, "hd_gold");
        box(0, inBot - 0.04, 0.095, 3.66 - 2 * ZF, 0.08, 0.07, 0, "hd_gold");
        for (int side = -1; side <= 1; side += 2) {
            // vorn 2,4 mm weiter heraus, hinten 1,2 mm weiter drin als die waagrechten Leisten (f 0,0612 bis 0,1324)
            box(side * 1.79, (inTop + inBot) / 2, 0.095 + ZF * 0.75, 0.08, inTop - inBot + 0.16 - 2 * ZF, 0.07 + ZF / 2, 0, "hd_gold");
        }
        // Nieten an den Ecken (über Eck gestellt), eine bewusst dunkler (Symmetrie einmal gebrochen)
        double[][] riv = {{-1.79, inTop + 0.04}, {1.79, inTop + 0.04}, {-1.79, inBot - 0.04}, {1.79, inBot - 0.04}};
        for (int i = 0; i < riv.length; i++) {
            box(riv[i][0], riv[i][1], 0.14, 0.075, 0.075, 0.02, Math.PI / 4, i == 3 ? "hd_gold" : "hd_gold_lit");
        }
        // Schriftzug „PLINKO“ auf eigener Tafel über dem Rahmen
        box(0, 2.24, 0.075, 1.9, 0.42, 0.03, 0, "hd_plinko_dark");
        box(0, 2.0125, 0.0925, 1.98, 0.035, 0.035, 0, "hd_gold");
        box(0, 2.4675, 0.0925, 1.98, 0.035, 0.035, 0, "hd_gold");
        text(0, 2.1, 0.095, 0.9f, 400, CrashScreen.logo("PLINKO", LOGO));
        // Einwurf-Trichter
        for (int side = -1; side <= 1; side += 2) {
            box(side * 0.12, Plinko.DROP_V + 0.06, F_PEG, 0.2, 0.035, 0.04, side * Math.toRadians(38), "hd_gold");
        }
        // Stifte (über Eck gestellte Würfel)
        for (int row = 0; row < Plinko.ROWS; row++) {
            int n = Plinko.pegsIn(row);
            pegs[row] = new ItemDisplay[n];
            pegLitUntil[row] = new int[n];
            for (int j = 0; j < n; j++) {
                ItemDisplay d = box(Plinko.pegU(row, j), Plinko.pegV(row), F_PEG, 2 * Plinko.PEG_R, 2 * Plinko.PEG_R, 0.05, Math.PI / 4, "hd_peg");
                d.setBrightness(new Display.Brightness(13, 13));
                pegs[row][j] = d;
            }
        }
        // Fächer mit Gewinnfaktor (Beschriftung je Risiko über multipliers())
        for (int i = 0; i < Plinko.SLOTS; i++) {
            ItemDisplay d = box(Plinko.slotU(i), Plinko.SLOT_V - 0.03, 0.10, Plinko.S - 0.03, 0.22, 0.04, 0, "hd_pl" + Plinko.tier(i));
            d.setBrightness(new Display.Brightness(12, 12));
            slots[i] = d;
            slotText[i] = text(Plinko.slotU(i), Plinko.SLOT_V - 0.125, 0.123, 0.3f, 200, Component.empty());
        }
        // links oben: Spieler, Risiko, Einsatz
        info = text(-1.27, 0.44, 0.09, 0.36f, 120, Component.empty());
        // rechts oben: ZULETZT
        text(1.3, 1.36, 0.09, 0.32f, 160, Component.text("ZULETZT", TextColor.color(0xF2DA90), TextDecoration.BOLD));
        for (int i = 0; i < HISTORY; i++) {
            double v = 1.24 - i * 0.17;
            histPlate[i] = box(1.3, v, 0.0875, 0.44, 0.13, 0.015, 0, "hd_plinko_dark");
            histPlate[i].setBrightness(new Display.Brightness(12, 12));
            histText[i] = text(1.3, v - 0.05, 0.098, 0.32f, 160, Component.empty());
        }
        // Bedienfeld
        text(-1.0, -1.99, 0.07, 0.26f, 200, Component.text("RISIKO", TextColor.color(0xACA48E), TextDecoration.BOLD));
        String[] labels = {"NIEDRIG", "MITTEL", "HOCH"};
        for (int i = 0; i < 3; i++) {
            riskPlate[i] = box(RISK_U[i], CTRL_V, 0.08, 0.42, 0.22, 0.04, 0, "hd_plinko");
            riskPlate[i].setBrightness(new Display.Brightness(13, 13));
            text(RISK_U[i], CTRL_V - 0.035, 0.1025, 0.24f, 200, Component.text(labels[i], NamedTextColor.WHITE, TextDecoration.BOLD));
            interaction(RISK_U[i], CTRL_V - 0.12, 0.42, 0.24, "risk" + i);
        }
        box(BET_U, CTRL_V, 0.08, 0.7, 0.3, 0.04, 0, "hd_plinko_dark");
        box(BET_U, CTRL_V + 0.165, 0.0825, 0.74, 0.03, 0.045, 0, "hd_gold");
        box(BET_U, CTRL_V - 0.165, 0.0825, 0.74, 0.03, 0.045, 0, "hd_gold");
        bet = text(BET_U, CTRL_V - 0.1, 0.1025, 0.3f, 200, Component.empty());
        interaction(BET_U, CTRL_V - 0.16, 0.7, 0.32, "bet");
        box(DROP_U, CTRL_V, 0.08, 0.62, 0.32, 0.04, 0, "hd_plinko_dark");
        button = box(DROP_U, CTRL_V + 0.01, 0.12, 0.5, 0.22, 0.04 + ZF, 0, "hd_pl_btn");
        button.setBrightness(new Display.Brightness(15, 15));
        text(DROP_U, CTRL_V - 0.04, 0.1425, 0.3f, 200, Component.text("BALL ▼", NamedTextColor.WHITE, TextDecoration.BOLD));
        interaction(DROP_U, CTRL_V - 0.17, 0.62, 0.34, "drop");
        // das Brett selbst: Klick öffnet den Plinko-Dialog (Risiko, Einsatz, eigener Einsatz, Gewinnplan, Zuletzt)
        double bot = Plinko.SLOT_V - 0.3, top = Plinko.DROP_V + 0.25;
        for (int i = 0; i < 6; i++) interaction(-1.75 + 0.35 + i * 0.58, bot, 0.58, top - bot, "board");
    }

    public void removeAll() {
        if (center.getWorld() == null) return;
        double r = (TOP - BOTTOM) * size + 2;
        for (Entity e : center.getWorld().getNearbyEntities(center, r, r, r)) {
            String v = e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            if (v != null && v.startsWith(id + ":")) e.remove();
        }
        button = null;
    }

    // =================================================================================================================
    // Anzeige
    // =================================================================================================================

    public static TextColor tierColor(int tier) {
        return TextColor.color(switch (tier) {
            case 0 -> 0xB4A8D8;
            case 1 -> 0x8EB4FF;
            case 2 -> 0x7EE6DA;
            case 3 -> 0xFFD860;
            case 4 -> 0xFFA860;
            case 5 -> 0xFF6A6E;
            default -> 0xFF7EC8;
        });
    }

    /** Gewinnfaktoren in die Fächer schreiben. */
    public void multipliers(Plinko.Risk risk) {
        for (int i = 0; i < Plinko.SLOTS; i++) {
            if (slotText[i] == null || !slotText[i].isValid()) continue;
            slotText[i].text(Component.text(Plinko.mult(risk.mult(i)), NamedTextColor.WHITE, TextDecoration.BOLD));
        }
    }

    /** Spieler, Risiko und Einsatz (links oben) und die Einsatz-Tafel; aktive Risiko-Tafel leuchtet gold. */
    public void controls(String player, Plinko.Risk risk, String betText, int balls) {
        if (!ready()) return;
        TextColor gray = TextColor.color(0xACA48E), gold = TextColor.color(0xF4D46A);
        Component c = Component.text("SPIELER", gray, TextDecoration.BOLD).append(Component.newline())
                .append(Component.text(player == null ? "frei" : player, NamedTextColor.WHITE)).append(Component.newline())
                .append(Component.text("Risiko ", gray)).append(Component.text(risk.label, gold)).append(Component.newline())
                .append(Component.text("Einsatz ", gray)).append(Component.text(betText, gold));
        if (balls > 0) c = c.append(Component.newline()).append(Component.text(balls + (balls == 1 ? " Kugel" : " Kugeln") + " im Fall", gray));
        info.text(c);
        bet.text(Component.text("EINSATZ", gray, TextDecoration.BOLD).append(Component.newline())
                .append(Component.text("◂ ", gray)).append(Component.text(betText, gold, TextDecoration.BOLD))
                .append(Component.text(" ▸", gray)));
        for (int i = 0; i < 3; i++) {
            if (riskPlate[i] != null && riskPlate[i].isValid()) {
                riskPlate[i].setItemStack(model(i == risk.ordinal() ? "m_hd_pl3_lit" : "m_hd_plinko"));
            }
        }
    }

    /** Letzte Ergebnisse (neuestes zuerst): Faktor und Fach. */
    public void history(List<double[]> recent) {
        for (int i = 0; i < HISTORY; i++) {
            if (histText[i] == null || !histText[i].isValid()) continue;
            if (i < recent.size()) {
                double m = recent.get(i)[0];
                int tier = (int) recent.get(i)[1];
                histPlate[i].setItemStack(model("m_hd_pl" + tier));
                histText[i].text(Component.text(Plinko.mult(m), NamedTextColor.WHITE, TextDecoration.BOLD));
            } else {
                histPlate[i].setItemStack(model("m_hd_plinko_dark"));
                histText[i].text(Component.empty());
            }
        }
    }

    // =================================================================================================================
    // Kugeln
    // =================================================================================================================

    /** Neue Kugel am Einwurf: Bild (immer zur Kamera gedreht), leuchtet. */
    public ItemDisplay spawnBall(double u, double v) {
        float s = (float) (2 * Plinko.BALL_R * size * 16 / 13.0);
        return center.getWorld().spawn(at(u, v, F_BALL), ItemDisplay.class, e -> {
            e.setItemStack(model("pl_ball"));
            e.setBillboard(Display.Billboard.CENTER);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(1);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            mark(e, "ball");
        });
    }

    public void moveBall(ItemDisplay ball, double u, double v) {
        if (ball == null || !ball.isValid()) return;
        ball.teleport(at(u, v, F_BALL));
    }

    /** Kugel schrumpft weg (Ende), dann entfernen. */
    public void fadeBall(ItemDisplay ball) {
        if (ball == null || !ball.isValid()) return;
        Transformation t = ball.getTransformation();
        ball.setInterpolationDelay(0);
        ball.setInterpolationDuration(5);
        ball.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(), new Vector3f(0, 0, 0), t.getRightRotation()));
    }

    public Location pegLocation(int row, int j) {
        return at(Plinko.pegU(row, j), Plinko.pegV(row), F_PEG);
    }

    public Location slotLocation(int slot) {
        return at(Plinko.slotU(slot), Plinko.SLOT_V, F_BALL);
    }

    /** Stift aufleuchten lassen (rosa-weiß, 5 Ticks). */
    public void hitPeg(int row, int j) {
        if (row < 0 || row >= Plinko.ROWS || j < 0 || j >= pegs[row].length) return;
        ItemDisplay d = pegs[row][j];
        if (d == null || !d.isValid()) return;
        if (pegLitUntil[row][j] <= tick) {
            d.setItemStack(model("m_hd_peg_lit"));
            d.setBrightness(new Display.Brightness(15, 15));
        }
        pegLitUntil[row][j] = tick + 5;
    }

    /** Fach aufleuchten lassen (14 Ticks) und den Faktor kurz größer zeigen. */
    public void hitSlot(int slot) {
        if (slot < 0 || slot >= Plinko.SLOTS) return;
        ItemDisplay d = slots[slot];
        if (d == null || !d.isValid()) return;
        d.setItemStack(model("m_hd_pl" + Plinko.tier(slot) + "_lit"));
        d.setBrightness(new Display.Brightness(15, 15));
        slotLitUntil[slot] = tick + 14;
        TextDisplay t = slotText[slot];
        if (t != null && t.isValid()) {
            Transformation tr = t.getTransformation();
            float s = (float) (0.42 * size);
            t.setInterpolationDelay(0);
            t.setInterpolationDuration(3);
            t.setTransformation(new Transformation(new Vector3f(0, (float) (0.02 * size), 0), tr.getLeftRotation(), new Vector3f(s, s, s), tr.getRightRotation()));
        }
    }

    /** Knopf drücken: leuchtet und sinkt 2 cm ein. */
    public void press() {
        if (!ready()) return;
        button.setItemStack(model("m_hd_pl_btn_lit"));
        Transformation t = button.getTransformation();
        Vector3f n = front();
        float k = (float) (size * -0.02);
        button.setInterpolationDelay(0);
        button.setInterpolationDuration(2);
        button.setTransformation(new Transformation(new Vector3f(n.x * k, 0, n.z * k), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
        buttonLitUntil = tick + 5;
    }

    /** Je Tick: abgelaufenes Leuchten zurücksetzen. */
    public void tick() {
        tick++;
        for (int row = 0; row < Plinko.ROWS; row++) {
            if (pegs[row] == null) continue;
            for (int j = 0; j < pegs[row].length; j++) {
                if (pegLitUntil[row][j] == tick && pegs[row][j] != null && pegs[row][j].isValid()) {
                    pegs[row][j].setItemStack(model("m_hd_peg"));
                    pegs[row][j].setBrightness(new Display.Brightness(13, 13));
                }
            }
        }
        for (int i = 0; i < Plinko.SLOTS; i++) {
            if (slotLitUntil[i] == tick && slots[i] != null && slots[i].isValid()) {
                slots[i].setItemStack(model("m_hd_pl" + Plinko.tier(i)));
                slots[i].setBrightness(new Display.Brightness(12, 12));
                TextDisplay t = slotText[i];
                if (t != null && t.isValid()) {
                    Transformation tr = t.getTransformation();
                    float s = (float) (0.3 * size);
                    t.setInterpolationDelay(0);
                    t.setInterpolationDuration(4);
                    t.setTransformation(new Transformation(new Vector3f(), tr.getLeftRotation(), new Vector3f(s, s, s), tr.getRightRotation()));
                }
            }
        }
        if (buttonLitUntil == tick && ready()) {
            button.setItemStack(model("m_hd_pl_btn"));
            Transformation t = button.getTransformation();
            button.setInterpolationDelay(0);
            button.setInterpolationDuration(3);
            button.setTransformation(new Transformation(new Vector3f(), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
        }
    }

    /** Schwebende Zahl über dem Fach („+800“), steigt und verschwindet. */
    public TextDisplay popup(int slot, Component c) {
        TextDisplay t = text(Plinko.slotU(slot), Plinko.SLOT_V + 0.12, F_BALL + 0.02, 0.34f, 200, c);
        t.setPersistent(false);
        return t;
    }

    /** Alle Teile (für Vorschau und Tests). */
    public List<Entity> parts() {
        List<Entity> l = new ArrayList<>();
        if (center.getWorld() == null) return l;
        double r = (TOP - BOTTOM) * size + 2;
        for (Entity e : center.getWorld().getNearbyEntities(center, r, r, r)) {
            String v = e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            if (v != null && v.startsWith(id + ":")) l.add(e);
        }
        return l;
    }
}
