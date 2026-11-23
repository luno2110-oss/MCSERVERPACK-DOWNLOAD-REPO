package de.kuby.kubyplugins.casino;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Glücksrad (v2.07): Felder, Ziehung und Drehung (rein rechnerisch, ohne Server, für den SELFTEST prüfbar).
 *
 * <p>Zwölf Felder im Uhrzeigersinn ab oben (Feld i hat seine Mitte bei i · 30 Grad). Das Ergebnis wird beim Drehen
 * nach Gewicht gezogen; die Drehung wird dann so geplant, dass das Rad genau auf diesem Feld zum Stehen kommt: ein
 * paar volle Umdrehungen, gleichmäßig auslaufend (Ease-out), Halt irgendwo im Feld (nicht immer genau in der Mitte),
 * damit es echt aussieht. Winkel = wie weit das Rad gegen den Uhrzeigersinn gedreht ist; das Feld oben am Zeiger ist
 * dann das bei diesem Winkel im Uhrzeigersinn.
 */
public final class Wheel {

    public static final int SEGMENTS = 12;
    public static final double STEP = 360.0 / SEGMENTS;

    /** Ein Feld: Gewicht, Gewinn (chips:N, coins:N, jewels:N, key:art:n, booster:art:faktor:min, tag:id, cosmetic:id), Anzeige. */
    public record Field(int weight, String prize, String name) {}

    private Wheel() {
    }

    /** Räder: casino (Bühne im Casino und Fenster in Survival), smp (nur Fenster). */
    public static final String CASINO = "casino", SMP = "smp";

    public static List<Field> fields(FileConfiguration cfg) {
        return fields(cfg, CASINO);
    }

    public static List<Field> fields(FileConfiguration cfg, String wheel) {
        List<Field> out = new ArrayList<>();
        for (String raw : cfg.getStringList("gluecksrad." + wheel + ".felder")) {
            String[] p = raw.split(";", 3);
            if (p.length < 2) continue;
            try {
                out.add(new Field(Math.max(0, Integer.parseInt(p[0].trim())), p[1].trim(), p.length > 2 ? p[2].trim() : p[1].trim()));
            } catch (NumberFormatException ignored) {
                // falsche Zeile auslassen
            }
        }
        while (out.size() < SEGMENTS) out.add(new Field(0, "chips:0", "Niete"));
        return out.subList(0, SEGMENTS);
    }

    /** Feld nach Gewicht ziehen. */
    public static int pick(List<Field> f, Random rng) {
        int sum = 0;
        for (Field x : f) sum += x.weight();
        if (sum <= 0) return 0;
        int r = rng.nextInt(sum);
        for (int i = 0; i < f.size(); i++) {
            r -= f.get(i).weight();
            if (r < 0) return i;
        }
        return 0;
    }

    /** Hauptgewinn: das Feld mit dem kleinsten Gewicht (bei Gleichstand das letzte). */
    public static int jackpot(List<Field> f) {
        int best = 0;
        for (int i = 1; i < f.size(); i++) if (f.get(i).weight() <= f.get(best).weight()) best = i;
        return best;
    }

    /** Wert eines Gewinns in der Hauptwährung des Rads (Chips bzw. Geld), für Ansagen und Schnitt. */
    public static long value(String prize) {
        String[] p = prize.split(":");
        if (p.length < 2) return 0;
        try {
            return switch (p[0]) {
                case "chips", "coins" -> Long.parseLong(p[1].trim());
                default -> 0;
            };
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static long chips(String prize) {
        if (!prize.startsWith("chips:")) return 0;
        try {
            return Long.parseLong(prize.substring(6).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Feld oben am Zeiger bei Winkel a (Grad, gegen den Uhrzeigersinn gedreht). */
    public static int fieldAt(double a) {
        return Math.floorMod((int) Math.round(a / STEP), SEGMENTS);
    }

    /** Halt im Feld: so weit hinter der Feldmitte (Grad), dass die Nadel frei steht und sicher auf diesem Feld zeigt. */
    public static final double OFF_MIN = 1, OFF_MAX = 13;

    /**
     * Winkel je Tick: von from bis zum Halt auf Feld target (Versatz offset in Grad, OFF_MIN bis OFF_MAX hinter der
     * Feldmitte), turns volle Umdrehungen, Dauer ticks, auslaufend wie ein echtes Rad (Ease-out mit Potenz 3,2).
     */
    public static double[] plan(double from, int target, double offset, int turns, int ticks) {
        double end = target * STEP + Math.max(OFF_MIN, Math.min(OFF_MAX, offset));
        double delta = Math.floorMod((long) Math.floor((end - from) * 1000), 360_000) / 1000.0;
        double total = delta + 360.0 * Math.max(1, turns);
        double[] a = new double[ticks + 1];
        for (int t = 0; t <= ticks; t++) {
            double u = t / (double) ticks;
            a[t] = from + total * (1 - Math.pow(1 - u, 3.2));
        }
        a[ticks] = from + total;
        return a;
    }

    // ---------- Stifte und Nadel ----------

    /**
     * Stifte sitzen auf den Feldgrenzen (15 + 30 · i Grad im Uhrzeigersinn auf der Scheibe). Das Rad dreht gegen den
     * Uhrzeigersinn, die Stifte kommen also von rechts nach oben und laufen oben nach links weiter. Ein Stift, der oben
     * vorbei ist, nimmt die Spitze der Nadel mit nach links (sie zeigt dabei noch aufs alte Feld), bis er NEEDLE_ZONE
     * Grad weiter ist: dann rutscht die Nadel über den Stift (Klack) und federt zurück aufs neue Feld.
     */
    public static final double NEEDLE_ZONE = 15, NEEDLE_MAX = 30;

    /** Wie weit (Grad) der letzte Stift schon links am Zeiger vorbei ist (0 bis 30). */
    public static double pegPast(double a) {
        return ((a - STEP / 2) % STEP + STEP) % STEP;
    }

    /** Wie weit der Stift die Nadel gerade nach links drückt (Grad, 0 = frei). */
    public static double needlePush(double a) {
        double s = pegPast(a);
        return s < NEEDLE_ZONE ? NEEDLE_MAX * s / NEEDLE_ZONE : 0;
    }

    /** Ist zwischen den Winkeln a0 und a1 die Nadel über einen Stift gerutscht (Klack)? */
    public static boolean pegPassed(double a0, double a1) {
        return Math.floor((a1 - STEP / 2 - NEEDLE_ZONE) / STEP) != Math.floor((a0 - STEP / 2 - NEEDLE_ZONE) / STEP);
    }

    /** Feld, auf das die Nadel gerade zeigt (hängt sie noch am Stift, das alte). Beim Halt gleich fieldAt. */
    public static int pointerField(double a) {
        return Math.floorMod((int) Math.floor((a - STEP / 2 - NEEDLE_ZONE) / STEP) + 1, SEGMENTS);
    }

    /** Nadel als gedämpfte Feder: [Ausschlag, Geschwindigkeit] einen Tick weiter, gedrückt vom Stift bei Winkel a. */
    public static double[] needleStep(double[] n, double a) {
        double push = needlePush(a);
        if (push >= n[0]) return new double[]{push, 0};
        double v = n[1] * 0.55 - n[0] * 0.55;
        double x = n[0] + v;
        if (Math.abs(x) < 0.3 && Math.abs(v) < 0.3) return new double[]{0, 0};
        return new double[]{push > 0 ? Math.max(push, x) : x, v};
    }

    // ---------- Gold-Dreh und Ersatz ----------

    /** Gewinn verdoppelt (Gold-Dreh): Beträge, Schlüssel-Anzahl, Booster-Minuten; Titel/Kosmetik: Ersatz-Betrag doppelt. */
    public static String doubled(String prize) {
        String[] p = prize.split(":");
        try {
            switch (p[0]) {
                case "chips", "coins", "jewels", "xp" -> p[1] = String.valueOf(Long.parseLong(p[1].trim()) * 2);
                case "key" -> {
                    int n = p.length > 2 ? Integer.parseInt(p[2].trim()) : 1;
                    return "key:" + p[1] + ":" + n * 2;
                }
                case "booster" -> {
                    if (p.length >= 4) p[3] = String.valueOf(Integer.parseInt(p[3].trim()) * 2);
                }
                case "cosmetic", "tag" -> {
                    if (p.length >= 3) p[2] = String.valueOf(Long.parseLong(p[2].trim()) * 2);
                }
                default -> {
                    return prize;
                }
            }
        } catch (RuntimeException e) {
            return prize;
        }
        return String.join(":", p);
    }

    /** Erwartungswert in Chips je Dreh (nur Chip-Felder), für die Info im SELFTEST. */
    public static double expectedChips(List<Field> f) {
        int sum = 0;
        double ev = 0;
        for (Field x : f) sum += x.weight();
        for (Field x : f) ev += x.weight() * (double) value(x.prize());
        return sum == 0 ? 0 : ev / sum;
    }

    /**
     * Selbsttest: 2.000 geplante Drehungen landen auf dem gezogenen Feld (auch die Nadel zeigt frei darauf), laufen nur vorwärts und werden langsamer,
     * höchstens 50 Grad je Tick (Anzeige wird alle 2 Ticks weich nachgezogen, unter 90 Grad bleibt die Richtung eindeutig); Ziehung trifft die Gewichte (±2 Prozentpunkte bei 60.000 Ziehungen).
     */
    public static List<String> selfCheck(List<Field> f) {
        List<String> err = new ArrayList<>();
        Random rng = new Random(11);
        for (int n = 0; n < 2000 && err.size() < 8; n++) {
            double from = rng.nextDouble() * 720;
            int target = rng.nextInt(SEGMENTS);
            int ticks = 130 + rng.nextInt(50);
            double[] a = plan(from, target, OFF_MIN + rng.nextDouble() * (OFF_MAX - OFF_MIN), 3 + rng.nextInt(2), ticks);
            if (fieldAt(a[ticks]) != target) err.add("Drehung " + n + " endet auf Feld " + fieldAt(a[ticks]) + " statt " + target);
            if (pointerField(a[ticks]) != target) err.add("Drehung " + n + ": Nadel zeigt auf Feld " + pointerField(a[ticks]) + " statt " + target);
            if (needlePush(a[ticks]) > 0) err.add("Drehung " + n + ": Nadel hängt beim Halt am Stift");
            double last = Double.MAX_VALUE;
            for (int t = 1; t <= ticks; t++) {
                double v = a[t] - a[t - 1];
                if (v < -1e-9) {
                    err.add("Drehung " + n + " läuft rückwärts (Tick " + t + ")");
                    break;
                }
                if (v > 50) {
                    err.add("Drehung " + n + " zu schnell (" + Math.round(v) + " Grad je Tick)");
                    break;
                }
                if (v > last + 1e-9) {
                    err.add("Drehung " + n + " wird schneller (Tick " + t + ")");
                    break;
                }
                last = v;
            }
        }
        int sum = 0;
        for (Field x : f) sum += x.weight();
        if (sum <= 0) {
            err.add("keine Gewichte");
            return err;
        }
        int[] hits = new int[SEGMENTS];
        for (int n = 0; n < 60_000; n++) hits[pick(f, rng)]++;
        for (int i = 0; i < SEGMENTS; i++) {
            double want = f.get(i).weight() / (double) sum, got = hits[i] / 60_000.0;
            if (Math.abs(want - got) > 0.02) err.add("Feld " + i + ": " + Math.round(got * 1000) / 10.0 + " % statt " + Math.round(want * 1000) / 10.0 + " %");
        }
        return err;
    }
}
