package de.kuby.kubyplugins.casino;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Pferderennen als Casino-Automat (v2.05, Wunsch des Besitzers: „wie im Casino, nicht wirklich Pferderennen“): sechs
 * Pixel-Pferde auf einem Wand-Bildschirm, gewettet wird auf Sieg oder Platz (unter den ersten zwei).
 *
 * <p>Reine Logik ohne Bukkit: Jedes Rennen bekommt ein neues Feld (Stärke je Pferd), daraus Sieg- und Platz-Chance
 * und die Quoten mit Hausvorteil (Rückfluss je Wette höchstens 95 %). Die Reihenfolge wird vorher ausgelost
 * (Plackett-Luce: Sieger mit seiner Sieg-Chance, dann der Zweite unter den übrigen usw.), der Rennverlauf dazu passend
 * geplant: jedes Pferd mit eigener Renneinteilung (Start, Zwischenspurt, Endspurt), mit Führungswechseln und
 * manchmal Foto-Finish. Position x je Tick: 0 = Startbox, 1 = Ziellinie, danach Auslauf.
 */
public final class HorseRace {

    public static final int HORSES = 6;
    public static final String[] NAMES = {"Blitz", "Donnerhuf", "Goldmähne", "Sturmwind", "Kuby-Express", "Mondschein"};
    /** Rennfarbe je Bahn (Jockey-Dress, Toto-Tafel): Rot, Blau, Gelb, Grün, Violett, Orange. */
    public static final int[] COLORS = {0xE8404A, 0x4E8EF2, 0xF2C42A, 0x46C25E, 0xB46AF0, 0xF08A2A};
    public static final String[] COLOR_NAMES = {"Rot", "Blau", "Gelb", "Grün", "Violett", "Orange"};
    /** Hausvorteil je Wette. */
    public static final double EDGE = 0.05;
    /** Auslauf hinter der Ziellinie (Anteil der Bahn). */
    public static final double RUN_OUT = 0.07;

    private HorseRace() {
    }

    // =================================================================================================================
    // Feld und Quoten
    // =================================================================================================================

    /** Ein Rennen vor dem Start: Sieg-Chance, Platz-Chance, Quoten (Auszahlung je 1 Chip inkl. Einsatz), Form 1–5. */
    public record Field(double[] win, double[] place, double[] winOdds, double[] placeOdds, int[] form) {}

    /** Neues Feld: Stärken log-normal gestreut, Chancen zwischen 4 % und 45 %, Quoten abgerundet auf 0,1. */
    public static Field field(Random r) {
        double[] p = new double[HORSES];
        for (int tries = 0; tries < 50; tries++) {
            double sum = 0;
            for (int i = 0; i < HORSES; i++) {
                p[i] = Math.exp(r.nextGaussian() * 0.6);
                sum += p[i];
            }
            boolean ok = true;
            for (int i = 0; i < HORSES; i++) {
                p[i] /= sum;
                if (p[i] < 0.04 || p[i] > 0.45) ok = false;
            }
            if (ok) break;
            if (tries == 49) Arrays.fill(p, 1.0 / HORSES);
        }
        return fieldOf(p);
    }

    public static Field fieldOf(double[] p) {
        double[] q = new double[HORSES], wo = new double[HORSES], po = new double[HORSES];
        int[] form = new int[HORSES];
        double min = 1, max = 0;
        for (double x : p) {
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        for (int i = 0; i < HORSES; i++) {
            q[i] = placeChance(p, i);
            wo[i] = odds(p[i]);
            po[i] = odds(q[i]);
            form[i] = max - min < 1e-9 ? 3 : 1 + (int) Math.round(4 * (p[i] - min) / (max - min));
        }
        return new Field(p.clone(), q, wo, po, form);
    }

    /** Quote zu einer Chance: (1 − Hausvorteil) / Chance, auf 0,1 abgerundet, mindestens 1,1 (sonst 1,0). */
    static double odds(double chance) {
        double o = Math.floor((1 - EDGE) / chance * 10 + 1e-9) / 10.0;
        return o < 1.1 ? (chance * 1.1 <= 1 - EDGE ? 1.1 : 1.0) : o;
    }

    /** Chance, unter den ersten zwei zu landen (Plackett-Luce): selbst Sieger oder Zweiter hinter einem anderen. */
    public static double placeChance(double[] p, int i) {
        double q = p[i];
        for (int j = 0; j < p.length; j++) if (j != i) q += p[j] * p[i] / (1 - p[j]);
        return q;
    }

    /** Einlaufreihenfolge (Bahn-Index je Platz) nach Plackett-Luce. */
    public static int[] order(double[] p, Random r) {
        int n = p.length;
        int[] out = new int[n];
        boolean[] used = new boolean[n];
        for (int k = 0; k < n; k++) {
            double rest = 0;
            for (int i = 0; i < n; i++) if (!used[i]) rest += p[i];
            double x = r.nextDouble() * rest;
            int pick = -1;
            for (int i = 0; i < n; i++) {
                if (used[i]) continue;
                pick = i;
                x -= p[i];
                if (x < 0) break;
            }
            used[pick] = true;
            out[k] = pick;
        }
        return out;
    }

    /** „3,4“ (deutsches Komma, eine Nachkommastelle). */
    public static String fmt(double odds) {
        return String.format(Locale.GERMAN, "%.1f", odds);
    }

    // =================================================================================================================
    // Rennverlauf
    // =================================================================================================================

    /**
     * Geplantes Rennen: x[h][t] Position von Bahn h im Tick t (0 Start, 1 Ziel, bis 1 + RUN_OUT), finish[h] = Tick an
     * der Ziellinie, order = Einlauf, photo = Foto-Finish zwischen Platz 1 und 2, leadChanges = Führungswechsel.
     */
    public record Plan(double[][] x, int[] finish, int[] order, boolean photo, int leadChanges, int length) {
        public int winner() {
            return order[0];
        }

        /** Führendes Pferd im Tick t (größte Position, bei Gleichstand die kleinere Bahn). */
        public int leader(int t) {
            int best = 0;
            for (int h = 1; h < HORSES; h++) if (x[h][t] > x[best][t]) best = h;
            return best;
        }

        /** Platzierung aller Pferde im Tick t (Bahn-Index je Platz). */
        public int[] standing(int t) {
            Integer[] idx = {0, 1, 2, 3, 4, 5};
            Arrays.sort(idx, (a, b) -> Double.compare(x[b][t], x[a][t]));
            int[] out = new int[HORSES];
            for (int i = 0; i < HORSES; i++) out[i] = idx[i];
            return out;
        }
    }

    /**
     * Plant den Verlauf zur Reihenfolge order. Sieger im Ziel nach 15–18 s, die anderen mit Abständen dahinter
     * (in gut jedem vierten Rennen Foto-Finish: 1–3 Ticks). Jedes Pferd läuft nach eigener Einteilung (Antritt aus der
     * Box, Zwischenspurt an zufälliger Stelle, Endspurt, leichtes Schwanken); von mehreren Versuchen gewinnt der mit den
     * meisten Führungswechseln (höchstens 5), damit das Rennen spannend bleibt, aber nicht zappelt.
     */
    public static Plan plan(int[] order, Random r) {
        Plan best = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            Plan p = planOnce(order, r);
            int score = Math.min(p.leadChanges(), 5);
            if (best == null || score > Math.min(best.leadChanges(), 5)) best = p;
            if (p.leadChanges() >= 2 && p.leadChanges() <= 5) break;
        }
        return best;
    }

    private static Plan planOnce(int[] order, Random r) {
        int[] finish = new int[HORSES];
        int t0 = 300 + r.nextInt(61);
        boolean photo = r.nextDouble() < 0.27;
        int t = t0;
        for (int k = 0; k < HORSES; k++) {
            if (k == 1) t += photo ? 1 + r.nextInt(3) : 4 + r.nextInt(15);
            else if (k > 1) t += 2 + r.nextInt(22);
            finish[order[k]] = t;
        }
        int runOut = 40;
        int len = t + runOut + 1;
        double[][] x = new double[HORSES][len];
        for (int h = 0; h < HORSES; h++) {
            int n = 400;
            double[] v = new double[n + 1];
            double surgeAt = 0.25 + 0.5 * r.nextDouble(), surge = 0.45 * r.nextDouble(), kick = 0.5 * r.nextDouble();
            double ph1 = r.nextDouble() * Math.PI * 2, ph2 = r.nextDouble() * Math.PI * 2, burst = 0.8 + 0.4 * r.nextDouble();
            for (int k = 0; k <= n; k++) {
                double u = (double) k / n;
                double go = 1 - Math.exp(-u / (0.035 * burst));                 // Antritt aus der Box
                double s = 1 + surge * Math.exp(-Math.pow((u - surgeAt) / 0.11, 2)) + kick * Math.pow(u, 3)
                        + 0.06 * Math.sin(u * 11 + ph1) + 0.04 * Math.sin(u * 23 + ph2);
                v[k] = Math.max(0.05, go * s);
            }
            double[] cum = new double[n + 1];
            for (int k = 1; k <= n; k++) cum[k] = cum[k - 1] + (v[k - 1] + v[k]) / 2;
            double total = cum[n];
            double endSpeed = v[n] / total * n / finish[h];   // Anteil je Tick an der Ziellinie
            for (int tt = 0; tt < len; tt++) {
                if (tt <= finish[h]) {
                    double u = (double) tt / finish[h] * n;
                    int k = Math.min(n - 1, (int) u);
                    double f = u - k;
                    x[h][tt] = (cum[k] + (cum[k + 1] - cum[k]) * f) / total;
                } else {
                    // Auslauf: bremst gleichmäßig bis zum Stand, höchstens RUN_OUT hinter dem Ziel
                    double dt = tt - finish[h];
                    double stop = Math.min(RUN_OUT / Math.max(endSpeed, 1e-6) * 2, 60);
                    double d = dt >= stop ? endSpeed * stop / 2 : endSpeed * dt - endSpeed * dt * dt / (2 * stop);
                    x[h][tt] = 1 + Math.min(RUN_OUT, d);
                }
            }
        }
        int changes = 0, lead = -1;
        for (int tt = 20; tt <= finish[order[0]]; tt++) {
            int best = 0;
            for (int h = 1; h < HORSES; h++) if (x[h][tt] > x[best][tt]) best = h;
            if (lead >= 0 && best != lead) changes++;
            lead = best;
        }
        return new Plan(x, finish, order.clone(), photo, changes, len);
    }

    // =================================================================================================================
    // SELFTEST
    // =================================================================================================================

    public static String lastStats = "";

    /**
     * Quoten: jede Sieg- und Platzwette höchstens 95 % Rückfluss, Platz-Chancen stimmen (Simulation); Reihenfolge:
     * Sieger so oft wie seine Chance; 300 Rennen: Einlauf wie geplant, keine Rückwärtsschritte, nie über den Auslauf,
     * Dauer 15–22 s, Führungswechsel in den meisten Rennen. Leere Liste = in Ordnung.
     */
    public static List<String> selfCheck() {
        List<String> errs = new ArrayList<>();
        Random r = new Random(1234);
        double worstWin = 0, worstPlace = 0, avgWin = 0, avgPlace = 0;
        int fields = 2000;
        for (int n = 0; n < fields; n++) {
            Field f = field(r);
            double s = 0;
            for (double x : f.win()) s += x;
            if (Math.abs(s - 1) > 1e-9) errs.add("Sieg-Chancen ergeben " + s);
            double sq = 0;
            for (double x : f.place()) sq += x;
            if (Math.abs(sq - 2) > 1e-9) errs.add("Platz-Chancen ergeben " + sq + " statt 2");
            for (int i = 0; i < HORSES; i++) {
                double rw = f.win()[i] * f.winOdds()[i], rp = f.place()[i] * f.placeOdds()[i];
                worstWin = Math.max(worstWin, rw);
                worstPlace = Math.max(worstPlace, rp);
                avgWin += rw / HORSES / fields;
                avgPlace += rp / HORSES / fields;
                if (f.winOdds()[i] < 1.1 || f.form()[i] < 1 || f.form()[i] > 5) errs.add("Quote/Form außerhalb: " + f.winOdds()[i] + " " + f.form()[i]);
            }
            if (errs.size() > 8) break;
        }
        if (worstWin > 1 - EDGE + 1e-9) errs.add("Siegwette mit " + String.format(Locale.ROOT, "%.1f", worstWin * 100) + " % Rückfluss");
        if (worstPlace > 1 - EDGE + 1e-9) errs.add("Platzwette mit " + String.format(Locale.ROOT, "%.1f", worstPlace * 100) + " % Rückfluss");
        // Platz-Chance per Simulation gegen die Formel
        double[] p = {0.38, 0.22, 0.16, 0.12, 0.08, 0.04};
        int[] winCount = new int[HORSES], placeCount = new int[HORSES];
        int sims = 40000;
        for (int n = 0; n < sims; n++) {
            int[] o = order(p, r);
            winCount[o[0]]++;
            placeCount[o[0]]++;
            placeCount[o[1]]++;
        }
        for (int i = 0; i < HORSES; i++) {
            if (Math.abs(winCount[i] / (double) sims - p[i]) > 0.012) errs.add("Sieger " + NAMES[i] + ": " + winCount[i] + " von " + sims);
            if (Math.abs(placeCount[i] / (double) sims - placeChance(p, i)) > 0.015) errs.add("Platz " + NAMES[i] + ": " + placeCount[i] + " von " + sims);
        }
        // Rennverläufe
        int races = 300, withChange = 0, photos = 0, minLen = Integer.MAX_VALUE, maxLen = 0;
        for (int n = 0; n < races && errs.size() < 12; n++) {
            Field f = field(r);
            int[] o = order(f.win(), r);
            Plan pl = plan(o, r);
            if (pl.leadChanges() > 0) withChange++;
            if (pl.photo()) photos++;
            int win = pl.finish()[o[0]];
            minLen = Math.min(minLen, win);
            maxLen = Math.max(maxLen, win);
            for (int k = 0; k + 1 < HORSES; k++) {
                if (pl.finish()[o[k]] >= pl.finish()[o[k + 1]]) errs.add("Rennen " + n + ": Platz " + (k + 1) + " nicht vor Platz " + (k + 2));
            }
            for (int h = 0; h < HORSES; h++) {
                double[] x = pl.x()[h];
                if (Math.abs(x[pl.finish()[h]] - 1) > 1e-6) errs.add("Rennen " + n + ": " + NAMES[h] + " nicht im Ziel zum geplanten Tick");
                for (int t = 1; t < x.length; t++) {
                    if (x[t] < x[t - 1] - 1e-9) {
                        errs.add("Rennen " + n + ": " + NAMES[h] + " läuft rückwärts (Tick " + t + ")");
                        break;
                    }
                    if (x[t] - x[t - 1] > 0.02) {
                        errs.add("Rennen " + n + ": " + NAMES[h] + " springt (Tick " + t + ")");
                        break;
                    }
                }
                if (x[x.length - 1] > 1 + RUN_OUT + 1e-9) errs.add("Rennen " + n + ": " + NAMES[h] + " läuft aus dem Bild");
            }
            // im Ziel-Tick des Siegers sind alle anderen noch vor der Linie
            for (int h = 0; h < HORSES; h++) if (h != o[0] && pl.x()[h][win] >= 1) errs.add("Rennen " + n + ": " + NAMES[h] + " gleichzeitig im Ziel");
        }
        if (withChange < races * 0.7) errs.add("Führungswechsel nur in " + withChange + " von " + races + " Rennen");
        if (minLen < 290 || maxLen > 370) errs.add("Siegerzeit " + minLen + "–" + maxLen + " Ticks (Soll 15–18 s)");
        lastStats = String.format(Locale.ROOT, "Rückfluss Sieg Ø %.1f %% (höchstens %.1f %%), Platz Ø %.1f %% (höchstens %.1f %%), "
                        + "%d Rennen: Führungswechsel in %d, Foto-Finish %d, Siegerzeit %d–%d Ticks", avgWin * 100, worstWin * 100,
                avgPlace * 100, worstPlace * 100, races, withChange, photos, minLen, maxLen);
        return errs;
    }
}
