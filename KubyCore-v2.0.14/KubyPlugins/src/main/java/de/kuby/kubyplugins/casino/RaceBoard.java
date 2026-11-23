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

import java.util.List;

/**
 * Der Pferderennen-Bildschirm an der Wand (v2.05, Casino-Automat): oben „PFERDERENNEN“ und die Statuszeile, in der
 * Mitte die Bahn (Rasen, sechs Spuren mit weißen Rails, Startboxen mit Türen in der Rennfarbe, Ziellinie, Entfernungs-
 * marken), links die Namen, rechts die Toto-Tafel (je Spur eine Zeile: Form, Sieg- und Platzquote, nach dem Rennen die
 * Platzierung), unten die letzten Sieger. Die Pferde sind Pixel-Bilder mit vier Galopp-Bildern (tools/build_race.py),
 * hinter jedem läuft seine aktuelle Platzierung mit.
 *
 * <p>Koordinaten u (rechts), v (hoch), f (vor der Wand) in Brett-Einheiten, mal {@code size}; center = Bildmitte auf
 * der Wandoberfläche.
 */
public final class RaceBoard {

    public static final double LEFT = -4.6, RIGHT = 4.6, BOTTOM = -1.95, TOP = 2.15;
    /** Bahn: Spurhöhe, obere Kante, Start- und Ziellinie (u), Ende des Auslaufs. */
    public static final double LANE = 0.42, TRACK_TOP = 1.2, U_START = -3.15, U_FINISH = 1.75, U_END = 2.35;
    private static final double HORSE = 0.58;
    private static final float ZF = 0.0024f;
    public static final int[] LOGO = {0x7EE07A, 0x5ACB6A, 0x46B45E, 0xA8D45A, 0xE8D04A, 0xF8CC2A, 0xFCEB7A,
            0xF8CC2A, 0xE8D04A, 0xA8D45A, 0x5ACB6A, 0x7EE07A};

    private final String id;
    private final Location center;
    private final float frontYaw;
    private final double size;
    private final NamespacedKey tag;
    private final NamespacedKey click;

    private final ItemDisplay[] horses = new ItemDisplay[HorseRace.HORSES];
    private final int[] frame = new int[HorseRace.HORSES];
    private final double[] lastX = new double[HorseRace.HORSES];
    private final TextDisplay[] placeTag = new TextDisplay[HorseRace.HORSES];
    private final ItemDisplay[] doors = new ItemDisplay[HorseRace.HORSES];
    private final TextDisplay[] doorText = new TextDisplay[HorseRace.HORSES];
    private final TextDisplay[] toto = new TextDisplay[HorseRace.HORSES];
    private final ItemDisplay[] histPlate = new ItemDisplay[8];
    private final TextDisplay[] histText = new TextDisplay[8];
    private TextDisplay status;

    public RaceBoard(String id, Location center, float frontYaw, double size, NamespacedKey tag, NamespacedKey click) {
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

    /** Rechts aus Sicht des Publikums vor dem Bildschirm. */
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

    /** Mitte der Spur lane (0 = oben). */
    public static double laneV(int lane) {
        return TRACK_TOP - LANE * (lane + 0.5);
    }

    /** Mitte des Pferdebilds bei Position x (0 = Start, 1 = Nase an der Ziellinie). */
    public static double horseU(double x) {
        return U_START + x * (U_FINISH - U_START) - HORSE * 0.45;
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

    private ItemDisplay box(double u, double v, double f, double w, double h, double d, String mat) {
        float k = (float) size;
        return center.getWorld().spawn(at(u, v, f), ItemDisplay.class, e -> {
            e.setItemStack(model("m_" + mat));
            e.setTransformation(new Transformation(new Vector3f(), face(), new Vector3f((float) w * k, (float) h * k, (float) d * k), new Quaternionf()));
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
            e.getPersistentDataContainer().set(click, PersistentDataType.STRING, id + ":board");
        });
    }

    public static TextColor color(int lane) {
        return TextColor.color(HorseRace.COLORS[lane]);
    }

    // =================================================================================================================
    // Aufbau
    // =================================================================================================================

    public boolean ready() {
        return status != null && status.isValid();
    }

    public void build() {
        if (center.getWorld() == null) return;
        removeAll();
        double w = RIGHT - LEFT, h = TOP - BOTTOM, vMid = (TOP + BOTTOM) / 2;
        // Rückwand und Goldrahmen (senkrechte Leisten vorn 2,4 mm weiter heraus, hinten 1,2 mm weiter drin)
        box(0, vMid, 0.03, w, h, 0.06, "hd_race_dark");
        box(0, TOP - 0.04, 0.095, w - 2 * ZF, 0.08, 0.07, "hd_gold");
        box(0, BOTTOM + 0.04, 0.095, w - 2 * ZF, 0.08, 0.07, "hd_gold");
        for (int side = -1; side <= 1; side += 2) {
            box(side * (w / 2 - 0.04), vMid, 0.095 + ZF * 0.75, 0.08, h - 2 * ZF, 0.07 + ZF / 2, "hd_gold");
        }
        double[][] riv = {{LEFT + 0.04, TOP - 0.04}, {RIGHT - 0.04, TOP - 0.04}, {LEFT + 0.04, BOTTOM + 0.04}, {RIGHT - 0.04, BOTTOM + 0.04}};
        for (int i = 0; i < riv.length; i++) {
            ItemDisplay rv = box(riv[i][0], riv[i][1], 0.14, 0.075, 0.075, 0.02, i == 3 ? "hd_gold" : "hd_gold_lit");
            Transformation t = rv.getTransformation();
            rv.setTransformation(new Transformation(t.getTranslation(), new Quaternionf(face()).rotateZ((float) (Math.PI / 4)), t.getScale(), t.getRightRotation()));
        }
        // Titel und Status
        text(0, 1.6, 0.07, 0.75f, 600, CrashScreen.logo("PFERDERENNEN", LOGO));
        status = text(0, 1.42, 0.07, 0.36f, 600, Component.empty());
        // Bahn: Rasen, Rails, Ziellinie
        double trackW = U_END - (U_START - 0.4), trackMid = (U_END + U_START - 0.4) / 2, trackH = LANE * HorseRace.HORSES;
        box(trackMid, TRACK_TOP - trackH / 2, 0.07, trackW, trackH, 0.02, "hd_turf");
        for (int k = 0; k <= HorseRace.HORSES; k++) {
            boolean edge = k == 0 || k == HorseRace.HORSES;
            box(trackMid, TRACK_TOP - LANE * k, 0.0875 + (edge ? ZF : 0), trackW, edge ? 0.05 : 0.03, 0.015, "hd_rail");
        }
        // Ziellinie vor den Rails (gemalt über die Querstreben), nie in einer Ebene mit ihnen
        box(U_FINISH, TRACK_TOP - trackH / 2, 0.098, 0.1, trackH, 0.004, "hd_finish");
        text(U_FINISH, TRACK_TOP + 0.08, 0.07, 0.24f, 200, Component.text("ZIEL", NamedTextColor.WHITE, TextDecoration.BOLD));
        String[] marks = {"1200 m", "800 m", "400 m"};
        for (int i = 0; i < 3; i++) {
            double u = U_START + (i + 1) / 4.0 * (U_FINISH - U_START);
            box(u, TRACK_TOP + 0.04, 0.0875 + 2 * ZF, 0.03, 0.08, 0.015, "hd_rail");
            text(u, TRACK_TOP + 0.1, 0.07, 0.2f, 200, Component.text(marks[i], TextColor.color(0xACA48E)));
        }
        // Startboxen: Pfosten und Dach grau, Tür in der Rennfarbe mit Nummer (geht beim Start auf)
        for (int lane = 0; lane < HorseRace.HORSES; lane++) {
            double v = laneV(lane);
            // Pfosten enden im Dach (oben nicht bündig mit ihm)
            box(U_START - 0.5, v - 0.01, 0.12, 0.04, LANE - 0.08, 0.08, "hd_gate");
            box(U_START + 0.02, v - 0.01, 0.12 + ZF, 0.04, LANE - 0.08, 0.08, "hd_gate");
            box(U_START - 0.24, v + LANE / 2 - 0.05, 0.12 + ZF / 2, 0.58, 0.04, 0.08, "hd_gate");
            doors[lane] = box(U_START + 0.01, v - 0.02, 0.175, 0.05, LANE - 0.12, 0.02, "hd_rn" + lane);
            doorText[lane] = text(U_START - 0.24, v - 0.07, 0.19, 0.3f, 100, Component.text(String.valueOf(lane + 1), NamedTextColor.WHITE, TextDecoration.BOLD));
            // Name links
            text(-4.08, v - 0.05, 0.07, 0.25f, 200, Component.text((lane + 1) + " ", color(lane), TextDecoration.BOLD)
                    .append(Component.text(HorseRace.NAMES[lane], NamedTextColor.WHITE)));
            // Pferd und Platzierung
            int ln = lane;
            float s = (float) (HORSE * size);
            horses[lane] = center.getWorld().spawn(at(horseU(0), v + 0.02, 0.11 + 0.004 * lane), ItemDisplay.class, e -> {
                e.setItemStack(model("rn_h" + ln + "_1"));
                e.setBrightness(new Display.Brightness(15, 15));
                // flaches Bild (item/generated): um 180 Grad gedreht wie die Slot-Symbole, sonst sieht man es von hinten
                // (gespiegelt) und die Pferde laufen rückwärts zum Ziel (Meldung des Besitzers, v2.07)
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(face()).rotateY((float) Math.PI),
                        new Vector3f(s, s, s), new Quaternionf()));
                mark(e, "horse");
            });
            frame[lane] = 1;
            lastX[lane] = 0;
            placeTag[lane] = text(horseU(0) - HORSE * 0.62, v - 0.06, 0.135 + 0.004 * lane, 0.26f, 100, Component.empty());
            // Toto-Zeile rechts, auf Höhe der Spur
            box(2.62, v, 0.075, 0.1, LANE - 0.1, 0.03, "hd_rn" + lane);
            toto[lane] = text(3.6, v - 0.085, 0.07, 0.27f, 300, Component.empty());
        }
        text(3.6, TRACK_TOP + 0.06, 0.07, 0.26f, 200, Component.text("TOTO · SIEG · PLATZ", TextColor.color(0xF2DA90), TextDecoration.BOLD));
        // unten: letzte Sieger und Hinweis
        text(-3.85, -1.72, 0.07, 0.24f, 200, Component.text("LETZTE SIEGER", TextColor.color(0xACA48E), TextDecoration.BOLD));
        for (int i = 0; i < histPlate.length; i++) {
            double u = -3.05 + i * 0.3;
            histPlate[i] = box(u, -1.62, 0.075, 0.24, 0.22, 0.03, "hd_race_dark");
            histText[i] = text(u, -1.68, 0.095, 0.28f, 100, Component.empty());
        }
        text(2.3, -1.72, 0.07, 0.26f, 400, Component.text("➜ Rechtsklick: Wetten", TextColor.color(0xF2DA90))
                .append(Component.text("  ·  /casino rennen", TextColor.color(0xACA48E))));
        // Klickflächen über den ganzen Bildschirm
        int tiles = 12;
        double tw = w / tiles;
        for (int i = 0; i < tiles; i++) interaction(LEFT + tw * (i + 0.5), BOTTOM, tw, h);
    }

    public void removeAll() {
        if (center.getWorld() == null) return;
        double r = (RIGHT - LEFT) * size + 2;
        for (Entity e : center.getWorld().getNearbyEntities(center, r, r, r)) {
            String v = e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            if (v != null && v.startsWith(id + ":")) e.remove();
        }
        status = null;
    }

    // =================================================================================================================
    // Anzeige
    // =================================================================================================================

    public void status(Component c) {
        if (ready()) status.text(c);
    }

    private static String stars(int n) {
        return "★".repeat(Math.max(0, n)) + "☆".repeat(Math.max(0, 5 - n));
    }

    /** Toto-Tafel: je Spur Form und Quoten; place != null zeigt nach dem Rennen die Platzierung (1-basiert). */
    public void toto(HorseRace.Field f, int[] place) {
        for (int lane = 0; lane < HorseRace.HORSES; lane++) {
            if (toto[lane] == null || !toto[lane].isValid()) continue;
            Component line1 = Component.text(HorseRace.NAMES[lane] + " ", NamedTextColor.WHITE, TextDecoration.BOLD)
                    .append(Component.text(stars(f.form()[lane]), TextColor.color(0xF2C42A)).decoration(TextDecoration.BOLD, false));
            Component line2;
            if (place != null) {
                int pl = place[lane];
                TextColor c = pl == 1 ? TextColor.color(0xFFD860) : pl == 2 ? TextColor.color(0xD8DCE4) : pl == 3 ? TextColor.color(0xE0A060)
                        : TextColor.color(0x8E8A84);
                line2 = Component.text(pl + ". Platz", c, TextDecoration.BOLD)
                        .append(Component.text("  Sieg " + HorseRace.fmt(f.winOdds()[lane]) + " · Platz " + HorseRace.fmt(f.placeOdds()[lane]),
                                TextColor.color(0x8E8A84)).decoration(TextDecoration.BOLD, false));
            } else {
                line2 = Component.text("Sieg ", TextColor.color(0xACA48E)).append(Component.text(HorseRace.fmt(f.winOdds()[lane]), TextColor.color(0xF4D46A), TextDecoration.BOLD))
                        .append(Component.text("  Platz ", TextColor.color(0xACA48E)))
                        .append(Component.text(HorseRace.fmt(f.placeOdds()[lane]), TextColor.color(0xF4D46A), TextDecoration.BOLD));
            }
            toto[lane].text(line1.append(Component.newline()).append(line2));
        }
    }

    /** Letzte Sieger (Spur-Index, neuester zuerst). */
    public void history(List<Integer> winners) {
        for (int i = 0; i < histPlate.length; i++) {
            if (histText[i] == null || !histText[i].isValid()) continue;
            if (i < winners.size()) {
                int lane = winners.get(i);
                histPlate[i].setItemStack(model("m_hd_rn" + lane));
                histText[i].text(Component.text(String.valueOf(lane + 1), NamedTextColor.WHITE, TextDecoration.BOLD));
            } else {
                histPlate[i].setItemStack(model("m_hd_race_dark"));
                histText[i].text(Component.empty());
            }
        }
    }

    /** Startbox-Türen: zu (Rennfarbe mit Nummer) oder auf (klappen in 4 Ticks weg). */
    public void gates(boolean open) {
        float k = (float) size;
        for (int lane = 0; lane < HorseRace.HORSES; lane++) {
            ItemDisplay d = doors[lane];
            if (d == null || !d.isValid()) continue;
            Transformation t = d.getTransformation();
            Vector3f sc = open ? new Vector3f(0.05f * k, 0.001f, 0.02f * k) : new Vector3f(0.05f * k, (float) (LANE - 0.12) * k, 0.02f * k);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(open ? 4 : 2);
            d.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(), sc, t.getRightRotation()));
            if (doorText[lane] != null && doorText[lane].isValid()) doorText[lane].setTextOpacity(open ? (byte) 4 : (byte) -1);
        }
    }

    /**
     * Pferde auf ihre Positionen (0 = Start, 1 = Ziel) und die Galopp-Bilder weiterschalten (je nach gelaufenem Weg);
     * standing = Platzierung je Platz (Spur-Index) oder null (dann keine Platz-Schilder).
     */
    public void positions(double[] x, int[] standing) {
        float k = (float) size;
        Vector3f r = right();
        int[] placeOf = new int[HorseRace.HORSES];
        if (standing != null) for (int i = 0; i < standing.length; i++) placeOf[standing[i]] = i + 1;
        for (int lane = 0; lane < HorseRace.HORSES; lane++) {
            ItemDisplay d = horses[lane];
            if (d == null || !d.isValid()) continue;
            double du = (horseU(x[lane]) - horseU(0)) * k;
            Vector3f tr = new Vector3f((float) (r.x * du), 0, (float) (r.z * du));
            Transformation t = d.getTransformation();
            if (t.getTranslation().distanceSquared(tr) > 1e-8) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(1);
                d.setTransformation(new Transformation(tr, t.getLeftRotation(), t.getScale(), t.getRightRotation()));
            }
            // Galopp: ein Bild je 1/110 der Bahn; steht das Pferd, Bild 1 (Landen, alle Hufe am Boden)
            double moved = x[lane] - lastX[lane];
            int f;
            if (Math.abs(moved) < 1e-5) f = 1;
            else f = (int) Math.floor(x[lane] * 110) % 4;
            lastX[lane] = x[lane];
            if (f != frame[lane]) {
                d.setItemStack(model("rn_h" + lane + "_" + f));
                frame[lane] = f;
            }
            TextDisplay pt = placeTag[lane];
            if (pt != null && pt.isValid()) {
                Transformation pt0 = pt.getTransformation();
                if (pt0.getTranslation().distanceSquared(tr) > 1e-8) {
                    pt.setInterpolationDelay(0);
                    pt.setInterpolationDuration(1);
                    pt.setTransformation(new Transformation(tr, pt0.getLeftRotation(), pt0.getScale(), pt0.getRightRotation()));
                }
                int pl = placeOf[lane];
                pt.text(pl == 0 ? Component.empty() : Component.text(pl + ".", pl == 1 ? TextColor.color(0xFFD860) : NamedTextColor.WHITE,
                        TextDecoration.BOLD));
            }
        }
    }

    /** Punkt über der Ziellinie (für Effekte). */
    public Location finishLocation(int lane) {
        return at(U_FINISH, lane < 0 ? TRACK_TOP - LANE * 3 : laneV(lane), 0.2);
    }

    public Location horseLocation(int lane, double x) {
        return at(horseU(x), laneV(lane), 0.2);
    }
}
