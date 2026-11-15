package de.kuby.kubyplugins.cinema;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Gelenkfigur für Kino-Szenen (Dealer, Geschäftsführer …): dieselben Teile und dieselbe Gelenkkette wie
 * {@code casino.DealerAnim}, aber als Szenen-Teile ({@link Prop}), damit Drehbücher Figuren laufen, gestikulieren
 * und sprechen lassen können. Modelle: {@code kuby:casino/rig_<skin>_<teil>} (tools/build_skins.py).
 *
 * <p>Gelenke in Grad: Oberkörper (Neigung, Drehung, Seite), Kopf, Schultern (x vor/zurück, y Schwenk, z abspreizen),
 * Ellbogen, Beine (vor/zurück je Bein).
 */
public final class Rig {

    public static final int TP = 0, TY = 1, TR = 2, HP = 3, HY = 4, HR = 5, RSX = 6, RSY = 7, RSZ = 8, REX = 9,
            LSX = 10, LSY = 11, LSZ = 12, LEX = 13, RLX = 14, LLX = 15, N = 16;
    private static final String[] PARTS = {"head", "torso", "rleg", "lleg", "rarm_up", "rarm_lo", "larm_up", "larm_lo"};
    private static final float SCALE = 0.9375f;

    private Rig() {
    }

    /** Aufrecht, Brust leicht raus, Arme locker. */
    public static float[] stand() {
        float[] p = new float[N];
        p[TP] = -3;
        p[HP] = 6;
        p[RSX] = -5;
        p[RSZ] = -3;
        p[REX] = -12;
        p[LSX] = -5;
        p[LSZ] = 3;
        p[LEX] = -12;
        return p;
    }

    /** Gehen: phase in Schritten (0..1 = ein Doppelschritt), Beine und Arme gegengleich. */
    public static float[] walk(double phase) {
        float[] p = stand();
        double s = Math.sin(phase * Math.PI * 2);
        p[RLX] = (float) (-26 * s);
        p[LLX] = (float) (26 * s);
        p[RSX] = (float) (-5 + 20 * s);
        p[LSX] = (float) (-5 - 20 * s);
        p[REX] = -18;
        p[LEX] = -18;
        p[TY] = (float) (3 * s);
        p[TP] = -1;
        return p;
    }

    /** Weich zwischen zwei Posen mischen (u 0..1, smoothstep). */
    public static float[] mix(float[] a, float[] b, double u) {
        double k = Math.max(0, Math.min(1, u));
        k = k * k * (3 - 2 * k);
        float[] o = new float[N];
        for (int i = 0; i < N; i++) o[i] = (float) (a[i] + (b[i] - a[i]) * k);
        return o;
    }

    /** Pose mit einzelnen Gelenken überschreiben: set(p, RSX, -70, REX, -10 …). */
    public static float[] set(float[] p, float... jv) {
        float[] o = p.clone();
        for (int i = 0; i + 1 < jv.length; i += 2) o[(int) jv[i]] = jv[i + 1];
        return o;
    }

    private static float rad(float d) {
        return (float) Math.toRadians(d);
    }

    private static Matrix4f rot(Matrix4f m, float y, float p, float r) {
        return m.rotateY(rad(y)).rotateX(rad(p)).rotateZ(rad(r));
    }

    /** Höhe der Füße über dem Boden beim Gehen (kleines Wippen). */
    public static double bob(double phase) {
        return Math.abs(Math.sin(phase * Math.PI * 2)) * 0.03;
    }

    /**
     * Die acht Körperteile als Szenen-Teile in den Frame schreiben.
     *
     * @param id     Vorsilbe der Teil-IDs (eindeutig je Figur)
     * @param skin   Rig-Name (dealer, manager …)
     * @param feet   Füße (Mitte, Boden)
     * @param yaw    Blickrichtung (Minecraft-Yaw)
     * @param j      Gelenke
     * @param smooth Überblendung in Ticks (0 = springen)
     */
    public static void props(Frame fr, String id, String skin, V3 feet, double yaw, float[] j, int smooth) {
        float px = SCALE / 16f;
        Matrix4f base = new Matrix4f().rotateY(rad((float) -yaw)).scale(px);
        Matrix4f legs = new Matrix4f(base).translate(0, 12, 0);
        Matrix4f torso = rot(new Matrix4f(base).translate(0, 12, 0), j[TY], j[TP], j[TR]);
        Matrix4f head = rot(new Matrix4f(torso).translate(0, 12, 0), j[HY], j[HP], j[HR]);
        Matrix4f rUp = rot(new Matrix4f(torso).translate(-6, 11, 0), j[RSY], j[RSX], j[RSZ]);
        Matrix4f rLo = rot(new Matrix4f(rUp).translate(0, -5, 0), 0, j[REX], 0);
        Matrix4f lUp = rot(new Matrix4f(torso).translate(6, 11, 0), j[LSY], j[LSX], j[LSZ]);
        Matrix4f lLo = rot(new Matrix4f(lUp).translate(0, -5, 0), 0, j[LEX], 0);
        Matrix4f rLeg = rot(new Matrix4f(legs), 0, j[RLX], 0);
        Matrix4f lLeg = rot(new Matrix4f(legs), 0, j[LLX], 0);
        Matrix4f[] ms = {head, torso, rLeg, lLeg, rUp, rLo, lUp, lLo};
        for (int i = 0; i < PARTS.length; i++) {
            Matrix4f m = new Matrix4f(ms[i]).scale(16f);
            Vector3f t = m.getTranslation(new Vector3f());
            Quaternionf q = m.getNormalizedRotation(new Quaternionf());
            fr.prop(Prop.item(id + "_" + PARTS[i], "kuby:casino/rig_" + skin + "_" + PARTS[i], feet, SCALE)
                    .moved(new V3(t.x, t.y, t.z)).rotated(new double[]{q.x, q.y, q.z, q.w}).lit(13).snap(smooth));
        }
    }
}
