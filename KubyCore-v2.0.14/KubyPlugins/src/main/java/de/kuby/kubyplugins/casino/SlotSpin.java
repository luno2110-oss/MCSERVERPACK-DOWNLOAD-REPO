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
        int accel = 6;
        for (int i = 1; i <= accel; i++) {
            v.add(vmax * i / accel);
            scalable.add(true);
        }
        if (!tension) {
            int decel = 14;
            int cruise = Math.max(4, arrive - v.size() - decel);
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
            int cruise = Math.max(4, arrive - v.size() - stop);
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
}
