package de.kuby.rematch.anim;

/** Werte aller Knochen zu einem Zeitpunkt: Drehung (Grad), Verschiebung (Pixel), Skalierung. */
public final class Pose {
    public final double[][] rot, pos, scl;

    public Pose(int bones) {
        rot = new double[bones][3];
        pos = new double[bones][3];
        scl = new double[bones][3];
        reset();
    }

    public int size() { return rot.length; }

    public Pose reset() {
        for (int i = 0; i < size(); i++) {
            for (int k = 0; k < 3; k++) { rot[i][k] = 0; pos[i][k] = 0; scl[i][k] = 1; }
        }
        return this;
    }

    public Pose copyFrom(Pose o) {
        for (int i = 0; i < size(); i++) {
            System.arraycopy(o.rot[i], 0, rot[i], 0, 3);
            System.arraycopy(o.pos[i], 0, pos[i], 0, 3);
            System.arraycopy(o.scl[i], 0, scl[i], 0, 3);
        }
        return this;
    }

    /** this = this + (o - this) * w */
    public Pose lerpTo(Pose o, double w) {
        for (int i = 0; i < size(); i++)
            for (int k = 0; k < 3; k++) {
                rot[i][k] += (o.rot[i][k] - rot[i][k]) * w;
                pos[i][k] += (o.pos[i][k] - pos[i][k]) * w;
                scl[i][k] += (o.scl[i][k] - scl[i][k]) * w;
            }
        return this;
    }

    /** Additiv: Drehung und Verschiebung dazu, Skalierung multiplizieren (mit Gewicht). */
    public Pose add(Pose o, double w) {
        for (int i = 0; i < size(); i++)
            for (int k = 0; k < 3; k++) {
                rot[i][k] += o.rot[i][k] * w;
                pos[i][k] += o.pos[i][k] * w;
                scl[i][k] *= 1 + (o.scl[i][k] - 1) * w;
            }
        return this;
    }
}
