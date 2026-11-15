package de.kuby.kubyplugins.cinema.scenes;

import de.kuby.kubyplugins.cinema.Ease;
import de.kuby.kubyplugins.cinema.Ev;
import de.kuby.kubyplugins.cinema.Frame;
import de.kuby.kubyplugins.cinema.Prop;
import de.kuby.kubyplugins.cinema.Script;
import de.kuby.kubyplugins.cinema.V3;

import java.util.Random;

/**
 * Slots: Spannung auf der letzten Walze, wie im Film. Startet in dem Moment, in dem die zweite Walze das zweite
 * gleiche Symbol einrastet.
 *
 * <ul>
 *   <li>Kamera fährt langsam auf die letzte Walze zu (leichtes Handkamera-Schwanken), Kinobalken, weiche farblose
 *       Vignette (Mitte frei, langer Verlauf)</li>
 *   <li>Farbe als Licht, wie mit feuchten Augen auf eine Ampel geschaut: auf den beiden gleichen Symbolen weiche
 *       Lichthöfe mit langen Lichtstreifen in der Farbe des Symbols (Kirsche rot, Banane/Zitrone/Glocke/Münze gold,
 *       Klee grün, Diamant blau …), die im Herzschlag pulsieren; auf der drehenden Walze flackern Lichter</li>
 *   <li>Herzschlag, der immer schneller wird; darüber das Geklimper von Münzen, die in einen Topf fallen (lauter werdend)</li>
 *   <li>kurz vor dem Einrasten: Stille</li>
 *   <li>Einrasten: harter Schnitt nah ran, weißer Blitz, Krachen, die Kamera wackelt; Gewinn: goldenes Bild und Jubel,
 *       Verlust: dunkles Bild, kurzes Nachklingen; dann zieht die Kamera zurück</li>
 * </ul>
 *
 * @param face  Mitte der Vorderseite der letzten Walze
 * @param front Richtung vom Automaten zum Spieler (waagerecht)
 * @param snap  Tick des Einrastens (ab Szenenbeginn)
 * @param sym   Symbol, auf das alle warten (für die Farbe)
 * @param win   ob die letzte Walze trifft
 * @param pay   Zeile unter dem Bild nach dem Einrasten (MiniMessage) oder null
 */
public final class SlotTension implements Script {

    private static final int AFTER = 38;

    private final V3 face, n, r;
    private final V3[] lights;
    private final int color;
    private final int snap;
    private final String grade;
    private final boolean win;
    private final String pay;
    private final long seed;
    private final boolean[] beat;

    public SlotTension(V3[] faces, V3 front, int snap, String sym, boolean win, String pay, long seed) {
        this.face = faces[2];
        this.lights = faces;
        this.color = colorOf(sym);
        this.n = front.flat();
        this.r = n.right();
        this.snap = Math.max(20, snap);
        this.grade = gradeOf(sym);
        this.win = win;
        this.pay = pay;
        this.seed = seed;
        // Herzschlag: Abstand von 18 auf 6 Ticks schrumpfend, endet vor der Stille
        beat = new boolean[this.snap + AFTER];
        double at = 4;
        while (at < this.snap - 7) {
            beat[(int) at] = true;
            double u = at / this.snap;
            at += 18 - 12 * u;
        }
    }

    /** Lichtfarbe je Symbol. */
    public static int colorOf(String sym) {
        return switch (sym) {
            case "cherry" -> 0xFF3A46;
            case "chip" -> 0xFF4A6A;
            case "seven" -> 0xFF5A2A;
            case "lemon" -> 0xFFE45A;
            case "banana" -> 0xFFD640;
            case "bell" -> 0xFFB838;
            case "coin" -> 0xFFC848;
            case "clover" -> 0x58FF78;
            case "diamond" -> 0x58C0FF;
            case "star" -> 0xFFFFFF;
            default -> 0xFFD08A;
        };
    }

    /** Farbstimmung je Symbol (Glyphen aus tools/build_cinema.py). */
    public static String gradeOf(String sym) {
        return switch (sym) {
            case "cherry", "chip", "seven" -> "red";
            case "lemon", "banana", "bell", "coin" -> "gold";
            case "clover" -> "green";
            case "diamond" -> "cold";
            case "star" -> "white";
            default -> "warm";
        };
    }

    /** Herzschlag-Puls 0..1 (springt beim Schlag hoch und klingt ab). */
    private double pulse(int t) {
        for (int k = 0; k < 10; k++) {
            int b = t - k;
            if (b >= 0 && b < beat.length && beat[b]) return Math.exp(-k * 0.45);
        }
        return 0;
    }

    /** Lichthöfe und Lichtstreifen vor den Symbolen (zur Kamera gedreht, leuchtend, in Symbolfarbe). */
    private void light(int t, Frame fr, Random rnd) {
        double in = Ease.smooth(Math.min(1, t / 14.0));
        double out = t > length() - 12 ? Ease.smooth((length() - t) / 12.0) : 1;
        double fade = in * out;
        if (fade <= 0.01) return;
        double p = pulse(t);
        for (int i = 0; i < 2; i++) {
            V3 at = lights[i].add(n.mul(0.07));
            double s = (0.5 + 0.12 * p) * fade;
            fr.prop(Prop.item("glow" + i, "kuby:fx/glow", at, s).tint(color).lit(15).board(1).snap(2));
            fr.prop(Prop.item("bloom" + i, "kuby:fx/bloom", at.add(n.mul(0.01)), (0.7 + 0.18 * p) * fade).tint(color).lit(15).board(1).snap(2));
        }
        V3 at = face.add(n.mul(0.07));
        if (t < snap) {
            // die drehende Walze: Lichter huschen vorbei (heller, kleiner, unruhig)
            double s = (0.22 + 0.12 * rnd.nextDouble()) * fade;
            int c = rnd.nextInt(3) == 0 ? color : 0xFFF2D8;
            fr.prop(Prop.item("glow2", "kuby:fx/glow", at.up((rnd.nextDouble() - 0.5) * 0.18), s).tint(c).lit(15).board(1).snap(1));
        } else {
            int a = t - snap;
            double bloom = win ? 0.6 * Math.exp(-a * 0.06) + 0.32 : 0.4 * Math.exp(-a * 0.15);
            int c = win ? color : 0x9A9A9A;
            fr.prop(Prop.item("glow2", "kuby:fx/glow", at, bloom * fade).tint(c).lit(15).board(1).snap(a == 0 ? 0 : 2));
            fr.prop(Prop.item("bloom2", "kuby:fx/bloom", at.add(n.mul(0.01)), bloom * 1.5 * fade).tint(c).lit(15).board(1)
                    .snap(a == 0 ? 0 : 2));
            // Gewinn: zweiter, weiter Lichthof (weich, kein harter Stern)
            if (win) fr.prop(Prop.item("halo2", "kuby:fx/glow", at.add(n.mul(0.02)), (0.85 + 0.35 * Math.exp(-a * 0.08)) * fade)
                    .tint(color).lit(15).board(1).snap(a == 0 ? 0 : 2));
        }
    }

    @Override
    public int length() {
        return snap + AFTER;
    }

    @Override
    public void frame(int t, Frame fr) {
        Random rnd = new Random(seed * 31 + t);
        V3 cam;
        if (t < snap) {
            double u = Ease.inOutSine(t / (double) snap);
            double dist = 1.45 - 0.7 * u;
            double sway = Math.sin(t * 0.07) * 0.035 + Math.sin(t * 0.19) * 0.008;
            double bob = Math.sin(t * 0.11) * 0.012;
            cam = face.add(n.mul(dist)).add(r.mul(sway)).up(0.14 - 0.08 * u + bob);
            fr.cam = cam;
            fr.look(face.up(-0.02).add(r.mul(sway * 0.3)));
        } else {
            int a = t - snap;
            double back = Ease.outCubic(Math.min(1, a / 30.0));
            double dist = 0.52 + 0.9 * back;
            double shake = a < 22 ? 0.065 * (1 - a / 22.0) : 0;
            V3 jitter = new V3((rnd.nextDouble() * 2 - 1) * shake, (rnd.nextDouble() * 2 - 1) * shake, (rnd.nextDouble() * 2 - 1) * shake);
            cam = face.add(n.mul(dist)).up(0.05 + 0.25 * back).add(jitter);
            fr.cam = cam;
            fr.look(face.up(0.08 * back).add(jitter.mul(0.5)));
            fr.cut = a == 0;
        }
        // Bild
        fr.bars = t < 10 ? Ease.smooth(t / 10.0) : t > length() - 10 ? Ease.smooth((length() - t) / 10.0) : 1;
        fr.grade = t < snap || win ? "soft" : "dark";
        light(t, fr, rnd);
        if (t >= snap + 6 && t < length() - 6 && pay != null) fr.cardSub = pay;
        // Ton
        if (t < beat.length && beat[t]) {
            float vol = (float) (0.55 + 0.4 * t / snap);
            fr.ev(new Ev.Sound("minecraft:block.note_block.basedrum", vol, 0.5f));
            if (t + 4 < snap - 7) fr.ev(new Ev.Sound("minecraft:block.note_block.basedrum", vol * 0.55f, 0.45f));
        }
        if (t < snap - 6 && t % 2 == 0) {
            // Münzen fallen in den Topf: dichter und lauter, je näher das Einrasten
            double u = t / (double) snap;
            if (rnd.nextDouble() < 0.45 + 0.5 * u) {
                float vol = (float) (0.12 + 0.38 * u);
                String key = switch (rnd.nextInt(4)) {
                    case 0 -> "minecraft:block.chain.place";
                    case 1 -> "minecraft:block.amethyst_block.hit";
                    case 2 -> "minecraft:entity.experience_orb.pickup";
                    default -> "minecraft:block.note_block.chime";
                };
                fr.ev(new Ev.Sound(key, vol, (float) (1.45 + rnd.nextDouble() * 0.5)));
            }
        }
        if (t == snap - 5) fr.ev(new Ev.Silence());
        if (t == snap) {
            fr.ev(new Ev.Flash(0xFFFFFF, 0, 1, 5));
            fr.ev(new Ev.Sound("minecraft:block.anvil.land", 1f, 0.6f));
            fr.ev(new Ev.Sound("minecraft:block.iron_trapdoor.close", 1f, 0.5f));
            fr.ev(new Ev.Sound("minecraft:block.note_block.basedrum", 1f, 0.5f));
            if (win) {
                fr.ev(new Ev.Sound("minecraft:entity.firework_rocket.large_blast", 1f, 0.9f));
                fr.ev(new Ev.Sound("minecraft:entity.generic.explode", 0.5f, 1.4f));
            }
        }
        if (win && t == snap + 4) fr.ev(new Ev.Sound("minecraft:ui.toast.challenge_complete", 0.9f, 1.15f));
        if (win && t > snap && t < snap + 30 && t % 2 == 0) {
            fr.ev(new Ev.Sound("minecraft:block.chain.place", 0.6f, (float) (1.4 + rnd.nextDouble() * 0.6)));
            fr.ev(new Ev.Sound("minecraft:entity.experience_orb.pickup", 0.4f, (float) (1.2 + rnd.nextDouble() * 0.8)));
        }
        if (!win && t == snap + 3) fr.ev(new Ev.Sound("minecraft:block.note_block.didgeridoo", 0.6f, 0.6f));
    }
}
