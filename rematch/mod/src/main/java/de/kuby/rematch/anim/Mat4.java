package de.kuby.rematch.anim;

/** Kleine 4x4-Matrix (Zeilen zuerst, double) für Gelenkketten. Nur starre Bewegungen plus Skalierung. */
public final class Mat4 {
    public final double[] m = new double[16];

    public Mat4() { identity(); }

    public Mat4 identity() {
        java.util.Arrays.fill(m, 0);
        m[0] = m[5] = m[10] = m[15] = 1;
        return this;
    }

    public Mat4 set(Mat4 o) { System.arraycopy(o.m, 0, m, 0, 16); return this; }

    public static Mat4 translation(double x, double y, double z) {
        Mat4 r = new Mat4();
        r.m[3] = x; r.m[7] = y; r.m[11] = z;
        return r;
    }

    public static Mat4 scale(double x, double y, double z) {
        Mat4 r = new Mat4();
        r.m[0] = x; r.m[5] = y; r.m[10] = z;
        return r;
    }

    /** Drehung in Grad, R = Rz * Ry * Rx (wie Blockbench und Minecrafts rotationZYX). */
    public static Mat4 rotZYXdeg(double rx, double ry, double rz) {
        return rotZYX(Math.toRadians(rx), Math.toRadians(ry), Math.toRadians(rz));
    }

    public static Mat4 rotZYX(double x, double y, double z) {
        double cx = Math.cos(x), sx = Math.sin(x), cy = Math.cos(y), sy = Math.sin(y), cz = Math.cos(z), sz = Math.sin(z);
        Mat4 r = new Mat4();
        double[] a = r.m;
        a[0] = cz * cy; a[1] = cz * sy * sx - sz * cx; a[2] = cz * sy * cx + sz * sx;
        a[4] = sz * cy; a[5] = sz * sy * sx + cz * cx; a[6] = sz * sy * cx - cz * sx;
        a[8] = -sy;     a[9] = cy * sx;                a[10] = cy * cx;
        return r;
    }

    /** this * o (neue Matrix). */
    public Mat4 mul(Mat4 o) {
        Mat4 r = new Mat4();
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += m[i * 4 + k] * o.m[k * 4 + j];
                r.m[i * 4 + j] = s;
            }
        return r;
    }

    /** Inverse einer affinen Matrix (3x3-Teil allgemein invertiert). */
    public Mat4 inverseAffine() {
        double a = m[0], b = m[1], c = m[2], d = m[4], e = m[5], f = m[6], g = m[8], h = m[9], k = m[10];
        double A = e * k - f * h, B = -(d * k - f * g), C = d * h - e * g;
        double det = a * A + b * B + c * C;
        if (Math.abs(det) < 1e-12) det = 1e-12;
        double inv = 1.0 / det;
        Mat4 r = new Mat4();
        double[] o = r.m;
        o[0] = A * inv; o[1] = -(b * k - c * h) * inv; o[2] = (b * f - c * e) * inv;
        o[4] = B * inv; o[5] = (a * k - c * g) * inv;  o[6] = -(a * f - c * d) * inv;
        o[8] = C * inv; o[9] = -(a * h - b * g) * inv; o[10] = (a * e - b * d) * inv;
        double tx = m[3], ty = m[7], tz = m[11];
        o[3] = -(o[0] * tx + o[1] * ty + o[2] * tz);
        o[7] = -(o[4] * tx + o[5] * ty + o[6] * tz);
        o[11] = -(o[8] * tx + o[9] * ty + o[10] * tz);
        return r;
    }

    public double[] apply(double x, double y, double z) {
        return new double[]{m[0] * x + m[1] * y + m[2] * z + m[3], m[4] * x + m[5] * y + m[6] * z + m[7],
                m[8] * x + m[9] * y + m[10] * z + m[11]};
    }

    /**
     * Zerlegt in Verschiebung, Drehung (Bogenmaß, R = Rz*Ry*Rx) und Skalierung je Achse.
     * out: [tx, ty, tz, rx, ry, rz, sx, sy, sz]
     */
    public void decompose(double[] out) {
        out[0] = m[3]; out[1] = m[7]; out[2] = m[11];
        double sx = Math.sqrt(m[0] * m[0] + m[4] * m[4] + m[8] * m[8]);
        double sy = Math.sqrt(m[1] * m[1] + m[5] * m[5] + m[9] * m[9]);
        double sz = Math.sqrt(m[2] * m[2] + m[6] * m[6] + m[10] * m[10]);
        if (sx < 1e-9) sx = 1e-9;
        if (sy < 1e-9) sy = 1e-9;
        if (sz < 1e-9) sz = 1e-9;
        double r00 = m[0] / sx, r10 = m[4] / sx, r20 = m[8] / sx;
        double r21 = m[9] / sy, r22 = m[10] / sz, r11 = m[5] / sy, r12 = m[6] / sz;
        double y = Math.asin(Math.max(-1, Math.min(1, -r20)));
        double x, z;
        if (Math.abs(r20) < 0.999999) {
            x = Math.atan2(r21, r22);
            z = Math.atan2(r10, r00);
        } else { // Gimbal: z auf 0 legen
            z = 0;
            x = Math.atan2(-r12, r11);
        }
        out[3] = x; out[4] = y; out[5] = z;
        out[6] = sx; out[7] = sy; out[8] = sz;
    }
}
