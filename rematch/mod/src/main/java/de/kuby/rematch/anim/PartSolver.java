package de.kuby.rematch.anim;

/**
 * Bildet die Knochen des Gerüsts auf die Modellteile des Minecraft-Spielers ab.
 * Minecraft-Modellraum: x gespiegelt, y nach unten, Hals bei y=0 (Blockbench y=24).
 * Für jedes Teil: Welt = C * W_knochen * C * T(ruhe-drehpunkt), lokal = inverse(Welt der Eltern) * Welt.
 * Ergebnis je Teil: x, y, z, xRot, yRot, zRot (Bogenmaß, Reihenfolge ZYX wie ModelPart), xScale, yScale, zScale.
 */
public final class PartSolver {
    /** Teile in fester Reihenfolge; Eltern stehen vor Kindern. */
    public enum Part {
        HEAD("head", -1, 0, 0, 0),
        BODY("torso2", -1, 0, 0, 0),             // Brust (vanilla body, Drehpunkt am Hals)
        BELLY("torso1", 1, 0, 12, 0),            // Bauch (Kind von body, Drehpunkt an der Hüfte)
        RIGHT_ARM("right_arm", -1, -5, 2, 0),
        RIGHT_FOREARM("right_arm2", 3, -6, 6, 0),
        LEFT_ARM("left_arm", -1, 5, 2, 0),
        LEFT_FOREARM("left_arm2", 5, 6, 6, 0),
        RIGHT_LEG("right_leg", -1, -1.9, 12, 0),
        RIGHT_SHIN("right_leg2", 7, -1.9, 18, 0),
        LEFT_LEG("left_leg", -1, 1.9, 12, 0),
        LEFT_SHIN("left_lag2", 9, 1.9, 18, 0);

        public final String bone;
        public final int parent;
        public final double px, py, pz;

        Part(String bone, int parent, double px, double py, double pz) {
            this.bone = bone; this.parent = parent; this.px = px; this.py = py; this.pz = pz;
        }
    }

    public static final int N = Part.values().length;
    /** C: Blockbench ↔ Minecraft-Modellraum (eigene Umkehrung). */
    private static final Mat4 C;
    static {
        C = new Mat4();
        C.m[0] = -1; C.m[5] = -1; C.m[7] = 24;
    }

    private final Rig rig;
    private final int[] boneOf = new int[N];
    private final Mat4[] world = new Mat4[N];
    private final double[] tmp = new double[9];
    /** [teil][x,y,z,xRot,yRot,zRot,xScale,yScale,zScale] */
    public final double[][] out = new double[N][9];

    public PartSolver(Rig rig) {
        this.rig = rig;
        for (Part p : Part.values()) boneOf[p.ordinal()] = rig.index(p.bone);
    }

    /** Rechnet die Teile für eine Pose aus. Werte relativ zum Eltern-Teil (oder zum Modell). */
    public double[][] solve(Pose pose) {
        Mat4[] w = rig.solve(pose);
        for (Part p : Part.values()) {
            int i = p.ordinal();
            int b = boneOf[i];
            Mat4 bw = b >= 0 ? w[b] : new Mat4();
            world[i] = C.mul(bw).mul(C).mul(Mat4.translation(p.px, p.py, p.pz));
            Mat4 local = p.parent < 0 ? world[i] : world[p.parent].inverseAffine().mul(world[i]);
            local.decompose(tmp);
            System.arraycopy(tmp, 0, out[i], 0, 9);
        }
        return out;
    }

    /** Welt-Lage (Minecraft-Modellraum) eines Knochens, z. B. für den Ball: Drehpunkt-Position. */
    public double[] bonePoint(Pose pose, String bone) {
        int b = rig.index(bone);
        if (b < 0) return null;
        Mat4 bw = rig.solve(pose)[b];
        double[] pv = rig.pivot[b];
        double[] p = bw.apply(pv[0], pv[1], pv[2]);
        return C.apply(p[0], p[1], p[2]);
    }

    /** Winkel weich mischen (kürzester Weg). */
    public static double lerpAngle(double from, double to, double w) {
        double d = to - from;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return from + d * w;
    }
}
