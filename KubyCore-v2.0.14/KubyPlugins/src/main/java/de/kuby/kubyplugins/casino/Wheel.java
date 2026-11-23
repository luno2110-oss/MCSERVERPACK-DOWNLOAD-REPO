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

    public static List<Field> fields(FileConfiguration cfg) {
        List<Field> out = new ArrayList<>();
        for (String raw : cfg.getStringList("casino.gluecksrad.felder")) {
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

    /** Hauptgewinn: das Feld mit den meisten Chips. */
    public static int jackpot(List<Field> f) {
        int best = -1;
        long most = -1;
        for (int i = 0; i < f.size(); i++) {
            long n = chips(f.get(i).prize());
            if (n > most) {
                most = n;
                best = i;
            }
        }
        return best;
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

    /**
     * Winkel je Tick: von from bis zum Halt auf Feld target (Versatz offset in Grad, höchstens ±12 im Feld), turns volle
     * Umdrehungen, Dauer ticks, auslaufend wie ein echtes Rad (Ease-out mit Potenz 3,2).
     */
    public static double[] plan(double from, int target, double offset, int turns, int ticks) {
        double end = target * STEP + Math.max(-12, Math.min(12, offset));
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

    /** Erwartungswert in Chips je Dreh (nur Chip-Felder), für die Info im SELFTEST. */
    public static double expectedChips(List<Field> f) {
        int sum = 0;
        double ev = 0;
        for (Field x : f) sum += x.weight();
        for (Field x : f) ev += x.weight() * (double) chips(x.prize());
        return sum == 0 ? 0 : ev / sum;
    }

    /**
     * Selbsttest: 2.000 geplante Drehungen landen auf dem gezogenen Feld, laufen nur vorwärts und werden langsamer,
     * höchstens 50 Grad je Tick (Anzeige wird alle 2 Ticks weich nachgezogen, unter 90 Grad bleibt die Richtung eindeutig); Ziehung trifft die Gewichte (±2 Prozentpunkte bei 60.000 Ziehungen).
     */
    public static List<String> selfCheck(List<Field> f) {
        List<String> err = new ArrayList<>();
        Random rng = new Random(11);
        for (int n = 0; n < 2000 && err.size() < 8; n++) {
            double from = rng.nextDouble() * 720;
            int target = rng.nextInt(SEGMENTS);
            int ticks = 130 + rng.nextInt(50);
            double[] a = plan(from, target, (rng.nextDouble() * 2 - 1) * 11, 3 + rng.nextInt(2), ticks);
            if (fieldAt(a[ticks]) != target) err.add("Drehung " + n + " endet auf Feld " + fieldAt(a[ticks]) + " statt " + target);
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
