package de.kuby.kubyplugins.casino;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Plinko (v2.05, Wunsch des Besitzers nach einem Screenshot, eigene Umsetzung im Kuby-Stil): Kugel fällt durch zwölf
 * Stiftreihen (oben drei Stifte, unten vierzehn) in eines von dreizehn Fächern. An jedem Stift geht es mit 50 % links
 * oder rechts, das Fach ist also binomialverteilt (Mitte oft, Ränder selten). Drei Risikostufen mit eigenen
 * Gewinnfaktoren, alle bei rund 96 % Rückfluss (Cashback und Boni des Casinos kommen obendrauf).
 *
 * <p>Diese Klasse ist reine Logik (ohne Bukkit): Tabellen, Zufallsweg, Flugbahn Tick für Tick und der SELFTEST.
 * Koordinaten in Brett-Einheiten bei Größe 1: u nach rechts, v nach oben, Ursprung = Mitte des Bretts.
 */
public final class Plinko {

    public static final int ROWS = 12;
    public static final int SLOTS = ROWS + 1;
    /** Waagrechter Stiftabstand, Reihenabstand, Höhe der obersten Reihe. */
    public static final double S = 0.25, H = 0.23, V0 = 1.30;
    /** Radius von Stift und Kugel; Abstand der Mittelpunkte bei Berührung. */
    public static final double PEG_R = 0.03, BALL_R = 0.055, C = PEG_R + BALL_R;
    /** Mitte der Fächer (senkrecht). */
    public static final double SLOT_V = V0 - (ROWS - 1) * H - 0.30;
    /** Start der Kugel über dem obersten Stift (Einwurf). */
    public static final double DROP_V = V0 + 0.42;
    /** Schwerkraft in Brett-Einheiten je Tick² (doppelt so stark wie echt: eine Kugel braucht rund 3 s). */
    public static final double G = 0.049;

    public enum Risk {
        NIEDRIG("Niedrig", new double[]{10, 3, 1.6, 1.3, 1.1, 0.9, 0.6}),
        MITTEL("Mittel", new double[]{25, 10, 3.5, 2, 1.1, 0.6, 0.3}),
        HOCH("Hoch", new double[]{150, 22, 8, 2, 0.7, 0.2, 0.2});

        public final String label;
        /** Faktoren vom Rand (Fach 0 und 12) zur Mitte (Fach 6). */
        final double[] outer;

        Risk(String label, double[] outer) {
            this.label = label;
            this.outer = outer;
        }

        /** Gewinnfaktor im Fach (0..12). */
        public double mult(int slot) {
            return outer[Math.min(slot, SLOTS - 1 - slot)];
        }

        public double[] mults() {
            double[] m = new double[SLOTS];
            for (int i = 0; i < SLOTS; i++) m[i] = mult(i);
            return m;
        }

        public double max() {
            return outer[0];
        }

        public Risk next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public Risk prev() {
            return values()[(ordinal() + values().length - 1) % values().length];
        }

        public static Risk byName(String s) {
            if (s == null) return null;
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "niedrig", "low", "leicht" -> NIEDRIG;
                case "mittel", "medium", "normal" -> MITTEL;
                case "hoch", "high", "schwer" -> HOCH;
                default -> null;
            };
        }

        /** Rückfluss exakt (Binomialverteilung über alle 4.096 Wege). */
        public double rtp() {
            double ev = 0;
            for (int k = 0; k < SLOTS; k++) ev += prob(k) * mult(k);
            return ev;
        }

        /** Anteil der Kugeln mit mehr als dem Einsatz zurück. */
        public double hitRate() {
            double h = 0;
            for (int k = 0; k < SLOTS; k++) if (mult(k) > 1) h += prob(k);
            return h;
        }
    }

    private Plinko() {
    }

    /** Wahrscheinlichkeit für Fach k: C(12, k) / 2^12. */
    public static double prob(int k) {
        double c = 1;
        for (int i = 0; i < k; i++) c = c * (ROWS - i) / (i + 1);
        return c / (1 << ROWS);
    }

    /** Farbstufe eines Fachs: 0 = Mitte (kleinster Faktor) bis 6 = Rand (größter). */
    public static int tier(int slot) {
        return Math.abs(slot - ROWS / 2);
    }

    /** „150x“, „1,6x“, „0,2x“ (deutsches Komma). */
    public static String mult(double m) {
        return SlotTheme.mult(m);
    }

    /** Zufallsweg: an jeder Reihe rechts (true) oder links. */
    public static boolean[] path(Random r) {
        boolean[] p = new boolean[ROWS];
        for (int i = 0; i < ROWS; i++) p[i] = r.nextBoolean();
        return p;
    }

    public static int slotOf(boolean[] path) {
        int k = 0;
        for (boolean b : path) if (b) k++;
        return k;
    }

    public static double pegU(int row, int j) {
        return (j - (row + 2) / 2.0) * S;
    }

    public static double pegV(int row) {
        return V0 - row * H;
    }

    public static int pegsIn(int row) {
        return row + 3;
    }

    public static double slotU(int slot) {
        return (slot - ROWS / 2) * S;
    }

    // =================================================================================================================
    // Flugbahn
    // =================================================================================================================

    /** Ein Ereignis auf der Bahn: Stift getroffen (row, peg), im Fach gelandet (row = -1, peg = Fach), Ende (row = -2). */
    public record Event(int tick, int row, int peg) {}

    /** Bahn Tick für Tick (u[t], v[t]) und die Ereignisse; slot = Ziel-Fach. */
    public record Flight(double[] u, double[] v, List<Event> events, int slot, int landTick) {
        public int length() {
            return u.length;
        }
    }

    private static final class Seg {
        final double u0, v0, u1, v1, vy0, t;

        Seg(double u0, double v0, double u1, double v1, double vy0, double t) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.vy0 = vy0;
            this.t = t;
        }

        double u(double s) {
            return u0 + (u1 - u0) * (s / t);
        }

        double v(double s) {
            return v0 + vy0 * s - G * s * s / 2;
        }
    }

    /** Wurfparabel von (u0, v0) mit Scheitel apex darüber nach (u1, v1) darunter. */
    private static Seg arc(double u0, double v0, double u1, double v1, double apex) {
        double drop = v0 - v1;
        double vy = Math.sqrt(2 * G * Math.max(0, apex));
        double t = (vy + Math.sqrt(vy * vy + 2 * G * drop)) / G;
        return new Seg(u0, v0, u1, v1, vy, t);
    }

    /**
     * Plant die Bahn zu einem Weg: freier Fall aus dem Einwurf auf den obersten Stift, dann je Reihe ein Sprung von der
     * Berührstelle zum nächsten Stift (Berührung auf der Seite, zu der die Kugel weiterspringt, 20–40° neben der Spitze,
     * Sprunghöhe zufällig 8–34 % des Reihenabstands, nach unten flacher), zum Schluss ins Fach mit zwei kleinen
     * Nachhüpfern. Die Kugel durchdringt nie einen Stift (SELFTEST prüft das).
     */
    public static Flight flight(boolean[] path, Random r) {
        List<Seg> segs = new ArrayList<>();
        List<int[]> hits = new ArrayList<>();       // {Segment-Index, Reihe, Stift}
        double u = (r.nextDouble() - 0.5) * 0.04, v = DROP_V;
        int right = 0;
        double lastDir = 0;
        for (int row = 0; row < ROWS; row++) {
            int j = right + 1;                      // Stift in dieser Reihe, den die Kugel trifft
            double pu = pegU(row, j), pv = pegV(row);
            double dir = path[row] ? 1 : -1;
            // Berührstelle auf der Seite, zu der es weitergeht: läuft die Kugel in dieselbe Richtung weiter, streift sie
            // den Stift nahe der Spitze (6–18°), kehrt sie um, trifft sie die Schulter, von der sie kommt (18–34°)
            double deg = row == 0 ? 8 + 14 * r.nextDouble() : dir == lastDir ? 6 + 12 * r.nextDouble() : 18 + 16 * r.nextDouble();
            double phi = dir * Math.toRadians(deg);
            double apex = row == 0 ? 0 : H * (0.10 + 0.24 * r.nextDouble()) * (1 - 0.025 * row);
            // nie durch einen Stift: andere Sprunghöhen und Berührstellen näher an der Spitze durchprobieren, die erste
            // freie nehmen (sonst die mit dem größten Abstand)
            Seg s = null;
            double cu = pu, cv = pv + C, best = -1;
            search:
            for (double ps : PHI_SCALE) {
                for (double af : APEX_SCALE) {
                    double ph = phi * ps;
                    double tu = pu + C * Math.sin(ph), tv = pv + C * Math.cos(ph);
                    Seg c = arc(u, v, tu, tv, row == 0 ? 0 : apex * af);
                    double d = minDist(c, row - 2, row);
                    if (d > best) {
                        best = d;
                        s = c;
                        cu = tu;
                        cv = tv;
                    }
                    if (d >= C - 0.004 || row == 0) break search;
                }
            }
            segs.add(s);
            hits.add(new int[]{segs.size() - 1, row, j});
            u = cu;
            v = cv;
            lastDir = dir;
            if (path[row]) right++;
        }
        // ins Fach
        int slot = right;
        double su = slotU(slot) + (r.nextDouble() - 0.5) * 0.03;
        double fa = H * (0.08 + 0.15 * r.nextDouble());
        Seg last = null;
        double bestLast = -1;
        for (double af : APEX_SCALE) {
            Seg c = arc(u, v, su, SLOT_V, fa * af);
            double d = minDist(c, ROWS - 2, ROWS - 1);
            if (d > bestLast) {
                bestLast = d;
                last = c;
            }
            if (d >= C - 0.004) break;
        }
        segs.add(last);
        int landSeg = segs.size() - 1;
        double settle = slotU(slot);
        segs.add(arc(su, SLOT_V, (su + settle) / 2, SLOT_V, 0.035));
        segs.add(arc((su + settle) / 2, SLOT_V, settle, SLOT_V, 0.012));
        // in Ticks abtasten
        double total = 0;
        double[] startAt = new double[segs.size()];
        for (int i = 0; i < segs.size(); i++) {
            startAt[i] = total;
            total += segs.get(i).t;
        }
        int rest = 6;
        int n = (int) Math.ceil(total) + 1 + rest;
        double[] us = new double[n], vs = new double[n];
        int si = 0;
        for (int t = 0; t < n; t++) {
            double tt = Math.min(t, total);
            while (si < segs.size() - 1 && tt > startAt[si] + segs.get(si).t) si++;
            Seg s = segs.get(si);
            double local = Math.min(s.t, Math.max(0, tt - startAt[si]));
            us[t] = s.u(local);
            vs[t] = s.v(local);
        }
        List<Event> ev = new ArrayList<>();
        for (int[] h : hits) {
            int seg = h[0];
            ev.add(new Event((int) Math.round(startAt[seg] + segs.get(seg).t), h[1], h[2]));
        }
        int land = (int) Math.round(startAt[landSeg] + segs.get(landSeg).t);
        ev.add(new Event(land, -1, slot));
        ev.add(new Event(n - 1, -2, slot));
        return new Flight(us, vs, ev, slot, land);
    }

    private static final double[] APEX_SCALE = {1, 0.6, 1.4, 0.35, 1.9, 2.5, 0.15, 3.2};
    private static final double[] PHI_SCALE = {1, 0.6, 0.3, 0};

    /** Kleinster Abstand der Parabel zu den Stiften der Reihen from..to (ohne ihren Endpunkt, dort ist er genau C). */
    private static double minDist(Seg s, int fromRow, int toRow) {
        int steps = 40;
        double min = 9;
        for (int k = 1; k < steps; k++) {
            double t = s.t * k / steps;
            double bu = s.u(t), bv = s.v(t);
            for (int row = Math.max(0, fromRow); row <= Math.min(ROWS - 1, toRow); row++) {
                for (int j = 0; j < pegsIn(row); j++) {
                    min = Math.min(min, Math.hypot(bu - pegU(row, j), bv - pegV(row)));
                }
            }
        }
        return min;
    }

    // =================================================================================================================
    // SELFTEST
    // =================================================================================================================

    public static String lastStats = "";

    /**
     * Prüft: jede Stufe 95–97 % Rückfluss und symmetrisch; 600 Bahnen landen im Fach ihres Wegs, treffen jede Reihe
     * genau einmal in der richtigen Reihenfolge, bleiben im Brett, springen nie mehr als eine halbe Stiftbreite je Tick
     * und durchdringen keinen Stift; die Fächer verteilen sich wie erwartet. Leere Liste = in Ordnung.
     */
    public static List<String> selfCheck() {
        List<String> errs = new ArrayList<>();
        for (Risk k : Risk.values()) {
            double q = k.rtp();
            if (q < 0.95 || q > 0.97) errs.add(k.label + ": Rückfluss " + String.format(Locale.ROOT, "%.2f", q * 100) + " % (Soll 95–97 %)");
            for (int i = 0; i < SLOTS; i++) if (k.mult(i) != k.mult(SLOTS - 1 - i)) errs.add(k.label + ": nicht symmetrisch");
        }
        Random r = new Random(4711);
        int[] count = new int[SLOTS];
        int minLen = Integer.MAX_VALUE, maxLen = 0;
        double minGap = 9;
        for (int n = 0; n < 600 && errs.size() < 12; n++) {
            boolean[] p = path(r);
            Flight f = flight(p, r);
            count[f.slot()]++;
            if (f.slot() != slotOf(p)) errs.add("Bahn " + n + ": Fach " + f.slot() + " statt " + slotOf(p));
            int lastRow = -1, lastTick = -1;
            for (Event e : f.events()) {
                if (e.tick() < lastTick) errs.add("Bahn " + n + ": Ereignisse nicht in Reihenfolge");
                lastTick = e.tick();
                if (e.row() >= 0) {
                    if (e.row() != lastRow + 1) errs.add("Bahn " + n + ": Reihe " + e.row() + " nach " + lastRow);
                    lastRow = e.row();
                }
            }
            if (lastRow != ROWS - 1) errs.add("Bahn " + n + ": nur bis Reihe " + lastRow);
            double endU = f.u()[f.length() - 1], endV = f.v()[f.length() - 1];
            if (Math.abs(endU - slotU(f.slot())) > 0.02 || Math.abs(endV - SLOT_V) > 0.02) {
                errs.add("Bahn " + n + ": Kugel liegt nicht im Fach (" + String.format(Locale.ROOT, "%.2f %.2f", endU, endV) + ")");
            }
            for (int t = 0; t < f.length(); t++) {
                double bu = f.u()[t], bv = f.v()[t];
                if (Math.abs(bu) > (ROWS + 2) * S / 2 + 0.05 || bv > DROP_V + 0.3 || bv < SLOT_V - 0.05) {
                    errs.add("Bahn " + n + ": verlässt das Brett bei Tick " + t);
                    break;
                }
                if (t > 0 && Math.hypot(bu - f.u()[t - 1], bv - f.v()[t - 1]) > S * 0.9) {
                    errs.add("Bahn " + n + ": Sprung bei Tick " + t);
                    break;
                }
                for (int row = 0; row < ROWS; row++) {
                    for (int j = 0; j < pegsIn(row); j++) {
                        double d = Math.hypot(bu - pegU(row, j), bv - pegV(row));
                        minGap = Math.min(minGap, d);
                    }
                }
            }
            minLen = Math.min(minLen, f.length());
            maxLen = Math.max(maxLen, f.length());
        }
        if (minGap < C - 0.006) errs.add("Kugel durchdringt einen Stift (Abstand " + String.format(Locale.ROOT, "%.3f", minGap) + " < " + C + ")");
        if (count[6] < 80 || count[0] + count[12] > 6) errs.add("Fächer ungleich verteilt: " + java.util.Arrays.toString(count));
        if (minLen < 40 || maxLen > 110) errs.add("Fallzeit " + minLen + "–" + maxLen + " Ticks (Soll 2–5 s)");
        StringBuilder b = new StringBuilder();
        for (Risk k : Risk.values()) {
            b.append(k.label).append(' ').append(String.format(Locale.ROOT, "%.1f %%", k.rtp() * 100)).append(" bis ")
                    .append(mult(k.max())).append(", ");
        }
        b.append("600 Bahnen ").append(minLen).append('–').append(maxLen).append(" Ticks, Fächer ")
                .append(java.util.Arrays.toString(count)).append(", kleinster Stiftabstand ")
                .append(String.format(Locale.ROOT, "%.3f", minGap));
        lastStats = b.toString();
        return errs;
    }
}
