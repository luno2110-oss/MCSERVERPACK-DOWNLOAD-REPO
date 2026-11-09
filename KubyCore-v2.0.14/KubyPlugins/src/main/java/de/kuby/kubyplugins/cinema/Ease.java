package de.kuby.kubyplugins.cinema;

/** Übergänge, Splines, Rauschen und Drehungen für Kamerafahrten (ohne Bukkit). */
public final class Ease {

    private Ease() {
    }

    public static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /** Anteil von t im Abschnitt [a, b], 0 bis 1. */
    public static double seg(double t, double a, double b) {
        return clamp01((t - a) / (b - a));
    }

    public static double smooth(double t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    public static double smoother(double t) {
        t = clamp01(t);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    public static double inOutSine(double t) {
        return -(Math.cos(Math.PI * clamp01(t)) - 1) / 2;
    }

    public static double inCubic(double t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static double outCubic(double t) {
        t = 1 - clamp01(t);
        return 1 - t * t * t;
    }

    public static double inOutCubic(double t) {
        t = clamp01(t);
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    public static double inExpo(double t) {
        t = clamp01(t);
        return t == 0 ? 0 : Math.pow(2, 10 * t - 10);
    }

    public static double outExpo(double t) {
        t = clamp01(t);
        return t == 1 ? 1 : 1 - Math.pow(2, -10 * t);
    }

    public static double inOutExpo(double t) {
        t = clamp01(t);
        if (t == 0 || t == 1) return t;
        return t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;
    }

    /** Schnell los, gedämpft auslaufen, ganz leicht über das Ziel (Kamera „schwingt“). */
    public static double outBack(double t, double over) {
        t = clamp01(t);
        double c3 = over + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + over * Math.pow(t - 1, 2);
    }

    /** Kritisch gedämpfte Feder von 0 nach 1 (k: Steifigkeit je Tick). */
    public static double spring(double ticks, double k) {
        if (ticks <= 0) return 0;
        double w = k;
        return 1 - (1 + w * ticks) * Math.exp(-w * ticks);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Winkel (Grad) auf kürzestem Weg mischen. */
    public static double lerpAngle(double a, double b, double t) {
        double d = ((b - a) % 360 + 540) % 360 - 180;
        return a + d * t;
    }

    /** Catmull-Rom durch p1..p2 (p0, p3 als Nachbarn). */
    public static V3 catmull(V3 p0, V3 p1, V3 p2, V3 p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return new V3(
                0.5 * (2 * p1.x() + (-p0.x() + p2.x()) * t + (2 * p0.x() - 5 * p1.x() + 4 * p2.x() - p3.x()) * t2 + (-p0.x() + 3 * p1.x() - 3 * p2.x() + p3.x()) * t3),
                0.5 * (2 * p1.y() + (-p0.y() + p2.y()) * t + (2 * p0.y() - 5 * p1.y() + 4 * p2.y() - p3.y()) * t2 + (-p0.y() + 3 * p1.y() - 3 * p2.y() + p3.y()) * t3),
                0.5 * (2 * p1.z() + (-p0.z() + p2.z()) * t + (2 * p0.z() - 5 * p1.z() + 4 * p2.z() - p3.z()) * t2 + (-p0.z() + 3 * p1.z() - 3 * p2.z() + p3.z()) * t3));
    }

    /** Punkt auf einem Pfad durch alle Punkte (t von 0 bis 1, gleichmäßig je Abschnitt). */
    public static V3 path(V3[] pts, double t) {
        if (pts.length == 1) return pts[0];
        double u = clamp01(t) * (pts.length - 1);
        int i = Math.min(pts.length - 2, (int) Math.floor(u));
        double f = u - i;
        V3 p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(pts.length - 1, i + 2)];
        return catmull(p0, p1, p2, p3, f);
    }

    // ---------- Rauschen (Kamera-Wackeln) ----------

    private static double hash(int i, int seed) {
        int h = i * 374761393 + seed * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xffffff) / (double) 0xffffff * 2 - 1;
    }

    /** Glattes Rauschen zwischen -1 und 1. */
    public static double noise(double t, int seed) {
        int i = (int) Math.floor(t);
        double f = t - i;
        double u = f * f * (3 - 2 * f);
        return hash(i, seed) * (1 - u) + hash(i + 1, seed) * u;
    }

    /** Wackeln: Versatz mit Stärke amp (Blöcke), freq Schwingungen je Tick. */
    public static V3 shake(double t, double amp, double freq, int seed) {
        if (amp <= 0) return V3.ZERO;
        double s = t * freq;
        return new V3(noise(s, seed) * amp, noise(s, seed + 17) * amp * 0.8, noise(s, seed + 31) * amp);
    }

    /** Zufallszahl 0..1 aus Seed und Index (für Trümmer, Funken). */
    public static double rnd(long seed, int i) {
        return (hash(i, (int) (seed ^ (seed >>> 32))) + 1) / 2;
    }

    // ---------- Drehungen (Quaternion x, y, z, w) ----------

    public static double[] quatIdentity() {
        return new double[]{0, 0, 0, 1};
    }

    public static double[] quatAxis(V3 axis, double deg) {
        V3 a = axis.norm();
        double h = Math.toRadians(deg) / 2, s = Math.sin(h);
        return new double[]{a.x() * s, a.y() * s, a.z() * s, Math.cos(h)};
    }

    public static double[] quatMul(double[] a, double[] b) {
        return new double[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    /** Drehung, die from auf to bringt. */
    public static double[] quatTo(V3 from, V3 to) {
        V3 f = from.norm(), t = to.norm();
        double d = f.dot(t);
        if (d > 0.999999) return quatIdentity();
        if (d < -0.999999) {
            V3 ax = Math.abs(f.x()) < 0.9 ? new V3(1, 0, 0).cross(f) : new V3(0, 1, 0).cross(f);
            return quatAxis(ax, 180);
        }
        V3 c = f.cross(t);
        double[] q = {c.x(), c.y(), c.z(), 1 + d};
        double l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        return new double[]{q[0] / l, q[1] / l, q[2] / l, q[3] / l};
    }

    /** Vektor mit Quaternion drehen. */
    public static V3 rotate(double[] q, V3 v) {
        double[] p = {v.x(), v.y(), v.z(), 0};
        double[] c = {-q[0], -q[1], -q[2], q[3]};
        double[] r = quatMul(quatMul(q, p), c);
        return new V3(r[0], r[1], r[2]);
    }
}
