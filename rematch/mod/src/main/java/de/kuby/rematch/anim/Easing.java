package de.kuby.rematch.anim;

import java.util.Locale;

/** Kurven für Überblendungen (0..1 → 0..1). */
public enum Easing {
    LINEAR, IN_QUAD, OUT_QUAD, IN_OUT_QUAD, IN_CUBIC, OUT_CUBIC, IN_OUT_CUBIC, OUT_EXPO, IN_EXPO, OUT_BACK, SMOOTH;

    public double apply(double t) {
        t = Math.max(0, Math.min(1, t));
        return switch (this) {
            case LINEAR -> t;
            case IN_QUAD -> t * t;
            case OUT_QUAD -> 1 - (1 - t) * (1 - t);
            case IN_OUT_QUAD -> t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            case IN_CUBIC -> t * t * t;
            case OUT_CUBIC -> 1 - Math.pow(1 - t, 3);
            case IN_OUT_CUBIC -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            case OUT_EXPO -> t >= 1 ? 1 : 1 - Math.pow(2, -10 * t);
            case IN_EXPO -> t <= 0 ? 0 : Math.pow(2, 10 * t - 10);
            case OUT_BACK -> { double c1 = 1.70158, c3 = c1 + 1; yield 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); }
            case SMOOTH -> t * t * (3 - 2 * t);
        };
    }

    public static Easing parse(String s, Easing fallback) {
        if (s == null) return fallback;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
