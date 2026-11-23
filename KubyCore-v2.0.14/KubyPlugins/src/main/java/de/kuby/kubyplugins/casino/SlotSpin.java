package de.kuby.kubyplugins.casino;

import java.util.ArrayList;
import java.util.List;

/**
 * Bewegung einer Slot-Walze wie bei den alten mechanischen Automaten in Vegas, Tick für Tick vorab berechnet.
 *
 * <ol>
 *   <li>Ausholen: die Walze ruckt kurz zurück (der Hebel spannt die Feder)</li>
 *   <li>Anlauf und schnelles Drehen (verwischte Symbole)</li>
 *   <li>normal: Abbremsen und Einrasten mit Überschwingen und Zurückfedern (Bounce)</li>
 *   <li>Spannung (zwei gleiche auf den ersten Walzen): die letzte Walze dreht im vollen Tempo einfach länger und
 *       rastet dann PLÖTZLICH ein (Stopp in 3 Ticks, kräftiges Überschwingen)</li>
 * </ol>
 * Die Strecke wird so gestreckt, dass die Walze genau auf dem Zielsymbol landet.
 */
public final class SlotSpin {

    /** Winkel je Tick (Grad); index = Tick seit dem Ziehen. */
    public final double[] angle;
    /** Tick, in dem die Walze einrastet (Ton, Ruck). */
    public final int arrive;
    /** Geschwindigkeit je Tick (Grad, positiv = Symbole wandern nach unten). */
    public final double[] speed;

    private SlotSpin(double[] angle, double[] speed, int arrive) {
        this.angle = angle;
        this.speed = speed;
        this.arrive = arrive;
    }

    public int length() {
        return angle.length;
    }

    /**
     * Dreh-Tempo (v2.06, Wunsch des Besitzers: „mal kurz, mal länger, damit es nicht langweilig ist“). Jede Runde
     * wird eines gezogen; es ändert nur, wann die Walzen einrasten (Ticks), nie das Ergebnis.
     * a/b/c: Einrasten der drei Walzen, weight: Häufigkeit.
     */
    public enum Tempo {
        /** zackig: alles in knapp 2 Sekunden */
        SCHNELL(18, 26, 34, 25),
        /** wie bisher, gut 3 Sekunden */
        NORMAL(32, 46, 60, 40),
        /** lange laufen lassen, gut 4 Sekunden */
        LANG(40, 62, 84, 18),
        /** eine Walze nach der anderen mit großen Pausen */
        EINZELN(24, 50, 76, 12),
        /** alle drei fast gleichzeitig */
        ZUSAMMEN(42, 46, 50, 5);

        public final int a, b, c, weight;

        Tempo(int a, int b, int c, int weight) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.weight = weight;
        }
    }

    public static Tempo pickTempo(java.util.Random rng) {
        int sum = 0;
        for (Tempo t : Tempo.values()) sum += t.weight;
        int r = rng.nextInt(sum);
        for (Tempo t : Tempo.values()) {
            r -= t.weight;
            if (r < 0) return t;
        }
        return Tempo.NORMAL;
    }

    /**
     * Einrast-Ticks der drei Walzen: Tempo plus kleines Zittern (±2 Ticks), immer in Reihenfolge mit mindestens
     * 3 Ticks Abstand. Spannung (zwei gleiche): die letzte Walze läuft 50 bis 80 Ticks länger als die zweite.
     */
    public static int[] arrivals(Tempo t, java.util.Random rng, boolean tension) {
        int[] r = {t.a + rng.nextInt(5) - 2, t.b + rng.nextInt(5) - 2, t.c + rng.nextInt(5) - 2};
        r[0] = Math.max(16, r[0]);
        r[1] = Math.max(r[1], r[0] + 3);
        r[2] = Math.max(r[2], r[1] + 3);
        if (tension) r[2] = r[1] + 50 + rng.nextInt(31);
        return r;
    }

    /**
     * @param from    Startwinkel
     * @param to      Zielwinkel (Zielsymbol auf der Linie, bis auf volle Umdrehungen)
     * @param arrive  Tick des Einrastens
     * @param crawlAt > 0: Spannung (länger drehen, plötzlicher Stopp), -1 = normal
     */
    public static SlotSpin plan(double from, double to, int arrive, int crawlAt) {
        boolean tension = crawlAt > 0 && crawlAt < arrive - 8;
        List<Double> v = new ArrayList<>();
        List<Boolean> scalable = new ArrayList<>();
        // Ausholen
        for (double w : new double[]{-3.5, -2.5, -1}) {
            v.add(w);
            scalable.add(false);
        }
        double vmax = 30;
        // kurze Drehs (Tempo SCHNELL): kürzerer Anlauf und kürzeres Abbremsen, damit die Walze rechtzeitig einrastet
        int accel = arrive < 24 ? 4 : 6;
        for (int i = 1; i <= accel; i++) {
            v.add(vmax * i / accel);
            scalable.add(true);
        }
        if (!tension) {
            // Einrasten genau in Tick arrive (= letzter Bewegungs-Tick, Index len - 1)
            int decel = Math.max(6, Math.min(14, arrive + 1 - v.size() - 4));
            int cruise = Math.max(4, arrive + 1 - v.size() - decel);
            for (int i = 0; i < cruise; i++) {
                v.add(vmax);
                scalable.add(true);
            }
            // weiches Abbremsen bis knapp über Stillstand (die Walze „fällt“ ins Raster)
            for (int i = 1; i <= decel; i++) {
                double u = i / (double) decel;
                v.add(Math.max(2.5, vmax * (1 - u) * (1 - u)));
                scalable.add(true);
            }
        } else {
            // Spannung: die letzte Walze dreht im vollen Tempo einfach länger und rastet dann PLÖTZLICH ein
            int stop = 3;
            int cruise = Math.max(4, arrive + 1 - v.size() - stop);
            for (int i = 0; i < cruise; i++) {
                v.add(vmax);
                scalable.add(true);
            }
            for (double w : new double[]{vmax * 0.55, vmax * 0.25, vmax * 0.08}) {
                v.add(w);
                scalable.add(true);
            }
        }
        double over = tension ? 13 : 7;
        // gewünschte Strecke bis zum Einrasten: Ziel + Überschwingen, so viele Umdrehungen, wie zur Kurve passen
        double raw = 0, fixed = 0, scal = 0;
        for (int i = 0; i < v.size(); i++) {
            raw += v.get(i);
            if (scalable.get(i)) scal += v.get(i);
            else fixed += v.get(i);
        }
        double base = (((from - to) % 360) + 360) % 360;
        double want = base + over;
        double turns = Math.max(1, Math.round((raw - want) / 360.0));
        want += 360 * turns;
        double k = (want - fixed) / scal;
        int len = v.size();
        double[] bounce = tension ? new double[]{0.42, -0.22, 0.1, -0.04, 0} : new double[]{0.35, -0.12, 0.04, 0};
        double[] ang = new double[len + bounce.length];
        double[] spd = new double[len + bounce.length];
        double s = 0;
        for (int i = 0; i < len; i++) {
            double vi = v.get(i) * (scalable.get(i) ? k : 1);
            s += vi;
            spd[i] = vi;
            ang[i] = from - s;
        }
        double target = from - (want - over);
        for (int i = 0; i < bounce.length; i++) {
            ang[len + i] = target - over * bounce[i];
            spd[len + i] = 0;
        }
        ang[ang.length - 1] = target;
        return new SlotSpin(ang, spd, len - 1);
    }

    /**
     * Selbsttest der Dreh-Tempos: je Tempo, mit und ohne Spannung, 300 zufällige Drehs. Jede Walze rastet genau im
     * geplanten Tick ein, die Walzen in Reihenfolge, Endwinkel = Zielsymbol, höchstens 120 Grad je Tick.
     */
    public static List<String> selfCheck() {
        List<String> err = new ArrayList<>();
        java.util.Random rng = new java.util.Random(7);
        for (Tempo t : Tempo.values()) {
            for (boolean tension : new boolean[]{false, true}) {
                for (int n = 0; n < 300 && err.size() < 10; n++) {
                    int[] arr = arrivals(t, rng, tension);
                    int last = -1;
                    for (int i = 0; i < 3; i++) {
                        double from = rng.nextInt(10) * 36.0, to = rng.nextInt(10) * 36.0;
                        SlotSpin sp = plan(from, to, arr[i], tension && i == 2 ? arr[1] + 6 : -1);
                        if (sp.arrive != arr[i]) err.add(t + (tension ? " Spannung" : "") + ": Walze " + (i + 1) + " rastet in Tick "
                                + sp.arrive + " statt " + arr[i] + " ein");
                        if (sp.arrive <= last) err.add(t + ": Walze " + (i + 1) + " nicht nach der vorigen");
                        last = sp.arrive;
                        double end = sp.angle[sp.length() - 1];
                        double d = ((end - to) % 360 + 360) % 360;
                        if (Math.min(d, 360 - d) > 1e-6) err.add(t + ": Walze " + (i + 1) + " endet nicht auf dem Symbol");
                        for (double v : sp.speed) {
                            if (Math.abs(v) > 120) {
                                err.add(t + ": Walze " + (i + 1) + " zu schnell (" + Math.round(v) + " Grad je Tick)");
                                break;
                            }
                        }
                    }
                }
            }
        }
        return err;
    }
}
