package de.kuby.kubyplugins.cinema;

/**
 * Kleiner Vektor für Drehbücher (ohne Bukkit, damit sich Szenen auch außerhalb des Servers berechnen lassen).
 * Achsen wie in Minecraft: x Osten, y oben, z Süden. Yaw 0 schaut nach Süden (+z), 90 nach Westen.
 */
public record V3(double x, double y, double z) {

    public static final V3 ZERO = new V3(0, 0, 0);
    public static final V3 UP = new V3(0, 1, 0);

    public V3 add(V3 o) {
        return new V3(x + o.x, y + o.y, z + o.z);
    }

    public V3 add(double dx, double dy, double dz) {
        return new V3(x + dx, y + dy, z + dz);
    }

    public V3 sub(V3 o) {
        return new V3(x - o.x, y - o.y, z - o.z);
    }

    public V3 mul(double k) {
        return new V3(x * k, y * k, z * k);
    }

    public V3 up(double dy) {
        return new V3(x, y + dy, z);
    }

    public double dot(V3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public V3 cross(V3 o) {
        return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double len() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public V3 norm() {
        double l = len();
        return l < 1e-9 ? ZERO : mul(1 / l);
    }

    /** Nur waagerecht (y = 0), normiert. */
    public V3 flat() {
        return new V3(x, 0, z).norm();
    }

    public V3 lerp(V3 b, double t) {
        return new V3(x + (b.x - x) * t, y + (b.y - y) * t, z + (b.z - z) * t);
    }

    public double dist(V3 o) {
        return sub(o).len();
    }

    /** Rechts von dieser (waagerechten) Blickrichtung. */
    public V3 right() {
        return new V3(-z, 0, x).norm();
    }

    public V3 left() {
        return new V3(z, 0, -x).norm();
    }

    /** Um die y-Achse drehen (Grad, wie Yaw: positiv = im Uhrzeigersinn von oben gesehen). */
    public V3 rotY(double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new V3(x * c - z * s, y, x * s + z * c);
    }

    /** Blickrichtung aus Yaw und Pitch (Minecraft: Pitch positiv = nach unten). */
    public static V3 dir(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new V3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    /** Yaw, um von hier nach target zu schauen. */
    public float yawTo(V3 target) {
        double dx = target.x - x, dz = target.z - z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Pitch, um von hier nach target zu schauen. */
    public float pitchTo(V3 target) {
        double dx = target.x - x, dy = target.y - y, dz = target.z - z;
        return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    public static float yawOf(V3 d) {
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }
}
