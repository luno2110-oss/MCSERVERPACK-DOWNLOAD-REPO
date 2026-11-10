package de.kuby.rematch.client.anim;

/** Kurzes Kamera-Wackeln (Trauma-Modell: Stärke klingt ab, Ausschlag ~ Stärke²). */
public final class CameraShake {
    private static double trauma;
    private static long last = System.nanoTime();
    private static double t;

    private CameraShake() { }

    public static void kick(double amount) { trauma = Math.min(1, trauma + amount); }

    /** [gieren, nicken] in Grad für dieses Bild. */
    public static float[] offset() {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        t += dt;
        trauma = Math.max(0, trauma - dt * 1.8);
        if (trauma <= 0) return null;
        double s = trauma * trauma * 2.2;
        float yaw = (float) (s * (Math.sin(t * 61) * 0.6 + Math.sin(t * 37 + 1) * 0.4));
        float pitch = (float) (s * (Math.sin(t * 53 + 2) * 0.6 + Math.sin(t * 29 + 3) * 0.4));
        return new float[]{yaw, pitch};
    }
}
