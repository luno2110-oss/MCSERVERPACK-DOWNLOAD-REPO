package de.kuby.kubyplugins.cinema.scenes;

import de.kuby.kubyplugins.cinema.Ease;
import de.kuby.kubyplugins.cinema.Ev;
import de.kuby.kubyplugins.cinema.Frame;
import de.kuby.kubyplugins.cinema.Prop;
import de.kuby.kubyplugins.cinema.Script;
import de.kuby.kubyplugins.cinema.V3;

/**
 * „Das Schwert im Stein“ (Drehbuch des Besitzers, 10 Szenen, etwa 47 Sekunden):
 * <ol>
 * <li>Kamera tief neben dem Stein, filmt den ankommenden Spieler, oben der Himmel.</li>
 * <li>Hinter dem Spieler, leicht links, Blick aufs Schwert; der Spieler schaut sich nervös, aber edel um.</li>
 * <li>Kurz vor dem Ziel: die laufenden Beine von der Seite.</li>
 * <li>Hinter dem Schwert mittig Richtung Spieler, steigt langsam bis über den Griff, folgt dem Spieler.</li>
 * <li>Griff von links: der Spieler greift zu und macht sich bereit.</li>
 * <li>Kamera schwingt hinter den Spieler: Druckwelle, Bass, der Boden bricht auf, Himmel blutrot, Wolken schwarz.</li>
 * <li>Auf den Stein: das Schwert ist fast draußen.</li>
 * <li>Schneller, gedämpfter Schwung über den Spieler um einen Punkt 5 Blöcke über dem Schwert.</li>
 * <li>Hinauf ins All zu einem riesigen Stern; er lädt auf und schießt einen Strahl auf das Schwert; Spieler und
 * Schwert laden sich auf.</li>
 * <li>Der Spieler betrachtet das Schwert im warmen Licht eines Sonnenuntergangs.</li>
 * </ol>
 * Alles wird nur aus dem Tick berechnet (keine Bukkit-Teile).
 *
 * @param stone  Mitte des Steins auf Bodenhöhe (Oberkante des Bodens)
 * @param toward waagerechte Richtung vom Stein zum Spieler (von dort kommt er)
 * @param dist   Abstand, ab dem die Figur losläuft (8 bis 13)
 */
public final class SwordInStone implements Script {

    // ---------- Zeitplan (Ticks) ----------
    static final int S2 = 64, S3 = 132, S4 = 164, ARRIVE = 178, S5 = 236, SILENT = 292, S6 = 300, IMPACT1 = 308, S7 = 396,
            S8 = 456, OUT = 472, S9 = 548, HOLD = 606, FIRE = 658, GROUND = 668, IMPACT2 = 672, ABSORB = 730, S10 = 766,
            CARD = 860, FADE = 908, LEN = 948;

    // ---------- Maße (Blöcke über dem Boden) ----------
    /** Oberkante des Steins. */
    public static final double ROCK = 0.45;
    /** Ursprung des Schwert-Modells über dem Boden, wenn es ganz drinsteckt (Spitze bei ~0). */
    public static final double SWORD = 0.89;
    /** Griff-Mitte über dem Modell-Ursprung (Modell: Griff 13,5 bis 18,5 von 16). */
    static final double GRIP = 0.5;
    /** Wie weit das Schwert für „ganz draußen“ steigt. */
    static final double FREE = 0.62;

    static final String SWORD_MODEL = "kuby:cine/sword", SWORD_HELD = "kuby:cine/sword_held", STONE = "kuby:cine/stone",
            STONE_GLOW = "kuby:cine/stone_glow", EMPTY = "kuby:cine/empty";
    static final int GOLD = 0xFFE7A8, RED = 0xB0120E, EMBER = 0xFF6A1A, SPACE = 0x080C24, NIGHT_RED = 0x5A0A10;

    private final V3 S, f, d, L, R, P0, A, Q;
    private final long seed;
    private final float stoneYaw;

    /** stoneYaw: Ausrichtung von Stein und Schwert in der Welt (bleibt gleich, egal woher der Spieler kommt). */
    public SwordInStone(V3 stone, V3 toward, double dist, long seed, float stoneYaw) {
        this.S = stone;
        this.f = toward.flat().len() < 0.5 ? new V3(0, 0, 1) : toward.flat();
        this.d = f.mul(-1);
        this.L = d.left();
        this.R = d.right();
        double D = Math.max(8, Math.min(13, dist));
        this.P0 = S.add(f.mul(D));
        this.A = S.add(f.mul(0.5)).up(ROCK);
        this.Q = S.add(R.mul(24)).up(430);
        this.seed = seed;
        this.stoneYaw = stoneYaw;
    }

    @Override
    public int length() {
        return LEN;
    }

    @Override
    public int skippableFrom() {
        return 0;
    }

    /** Wo der Spieler am Ende steht (vor dem Stein, Blick zum Stein). */
    public V3 endPoint() {
        return S.add(f.mul(1.45));
    }

    public float endYaw() {
        return V3.yawOf(d);
    }

    // =================================================================================================================
    // Figur
    // =================================================================================================================

    private V3 walk(double u) {
        V3 a = S.add(f.mul(0.5));
        V3 h = new V3(P0.x(), S.y(), P0.z()).lerp(new V3(a.x(), S.y(), a.z()), u);
        double ds = Math.hypot(h.x() - S.x(), h.z() - S.z());
        double y = S.y() + ROCK * Ease.smooth((1.15 - ds) / 0.4);
        return new V3(h.x(), y, h.z());
    }

    private double walkU(double t) {
        return Ease.inOutSine(t / ARRIVE);
    }

    private V3 actorAt(double t) {
        return t >= ARRIVE ? A : walk(walkU(Math.max(0, t)));
    }

    /** Wie weit das Schwert schon herausgezogen ist (0 bis FREE). */
    private double rise(int t) {
        if (t < 303) return 0;
        if (t < 312) return 0.08 * Ease.outCubic(Ease.seg(t, 303, 312));
        if (t < S7) return 0.08 + 0.14 * Ease.seg(t, 312, S7) + 0.012 * Ease.noise(t * 0.9, 3);
        if (t < OUT) return 0.22 + 0.22 * Ease.inOutSine(Ease.seg(t, S7, 462)) + 0.01 * Ease.noise(t * 1.3, 5);
        return 0.44 + (FREE - 0.44) * Ease.outExpo(Ease.seg(t, OUT, OUT + 6));
    }

    private static double bell(double t, double a, double b) {
        double s = Math.sin(Math.PI * Ease.seg(t, a, b));
        return s * s;
    }

    private void actor(int t, Frame fr) {
        V3 pos = actorAt(t);
        fr.actor = pos;
        float walkYaw = V3.yawOf(d);
        fr.actorYaw = walkYaw;
        double yawOff = 0, pitch = 4;
        if (t < ARRIVE) {
            // nervös, aber edel: links, rechts, dann aufrecht zum Schwert
            yawOff = -38 * bell(t, 72, 94) + 32 * bell(t, 96, 118) - 12 * bell(t, 30, 52);
            pitch = 2 - 8 * bell(t, 118, 160) + 6 * bell(t, 72, 118);
        } else if (t < S10) {
            pitch = Ease.lerp(-6, 36, Ease.smooth(Ease.seg(t, 182, 206)));
            if (t >= 300) pitch = Ease.lerp(36, 8, Ease.smooth(Ease.seg(t, 300, 330)));
            if (t >= OUT) pitch = Ease.lerp(8, -42, Ease.smooth(Ease.seg(t, OUT, OUT + 14)));
            if (t >= 690) pitch = -42 + 6 * Math.sin(t * 0.05);
        } else {
            yawOff = Ease.lerp(0, 24, Ease.smooth(Ease.seg(t, S10, S10 + 30)));
            pitch = Ease.lerp(-30, 32, Ease.smooth(Ease.seg(t, S10, S10 + 34)));
        }
        fr.headYaw = (float) (walkYaw + yawOff);
        fr.headPitch = (float) pitch;
        // Griff: in die Hocke, beide Hände nach vorn (Armbrust-Haltung), beim Ziehen aufrichten
        if (t >= 250 && t < 302) fr.crouch = true;
        if (t == 249) fr.swing = true;
        if (t >= 252 && t < OUT + 4) {
            fr.hand = EMPTY + "|crossbow";
        } else if (t >= OUT + 4 && t < S10) {
            fr.hand = SWORD_HELD + "|trident";
        } else if (t >= S10) {
            fr.hand = SWORD_HELD;
        }
        fr.glow = t >= 680 && t < 790;
    }

    // =================================================================================================================
    // Kamera
    // =================================================================================================================

    private void camera(int t, Frame fr) {
        V3 act = actorAt(t);
        V3 head = act.up(t >= 250 && t < 302 ? 1.27 : 1.62);
        V3 grip = S.up(SWORD + GRIP + rise(t));
        V3 cam;
        V3 look;
        double shake = 0;
        if (t < S2) {
            // 1: tief neben dem Stein, schaut zum Spieler hoch
            double u = Ease.inOutSine(t / (double) S2);
            cam = S.add(L.mul(Ease.lerp(1.2, 1.0, u))).add(f.mul(0.15)).up(Ease.lerp(0.28, 0.42, u));
            look = head.up(-0.1);
        } else if (t < S3) {
            // 2: über die Schulter, leicht links versetzt, Blick aufs Schwert
            V3 lag = actorAt(t - 5);
            cam = lag.sub(d.mul(2.3)).add(L.mul(0.85)).up(1.95);
            look = S.up(1.15).lerp(grip, 0.3);
            fr.cut = t == S2;
        } else if (t < S4) {
            // 3: die Beine von der Seite
            cam = act.add(R.mul(2.1)).add(d.mul(0.45)).up(0.42);
            look = act.up(0.45).add(d.mul(0.25));
            fr.cut = t == S3;
        } else if (t < S5) {
            // 4: hinter dem Schwert, steigt langsam bis über den Griff, folgt dem Spieler
            double u = Ease.inOutSine(Ease.seg(t, S4, S5));
            cam = S.sub(f.mul(Ease.lerp(1.75, 1.45, u))).up(Ease.lerp(0.7, SWORD + GRIP + 0.42, u));
            look = head;
            fr.cut = t == S4;
        } else if (t < S6) {
            // 5: Griff von links, langsam näher
            double u = Ease.inOutSine(Ease.seg(t, S5, S6));
            cam = S.add(L.mul(Ease.lerp(0.98, 0.74, u))).add(f.mul(0.12)).up(SWORD + GRIP + 0.06);
            look = grip.add(f.mul(0.08));
            fr.cut = t == S5;
        } else if (t < S7) {
            // 6: Schwung hinter den Spieler (um seine Achse), dann ruhiges Zurückweichen
            V3 c = A.up(1.2);
            V3 from = S.add(L.mul(0.74)).add(f.mul(0.12)).up(SWORD + GRIP + 0.06).sub(c);
            double a0 = Math.toDegrees(Math.atan2(from.z(), from.x()));
            double a1 = Math.toDegrees(Math.atan2(f.z(), f.x())) - 18;
            double sw = Ease.outCubic(Ease.seg(t, S6, 322));
            double ang = Math.toRadians(Ease.lerpAngle(a0, a1, sw));
            double rad = Ease.lerp(Math.hypot(from.x(), from.z()), 3.4, sw) + 0.5 * Ease.seg(t, 322, S7);
            double h = Ease.lerp(from.y(), 1.15, sw) + 0.2 * Ease.seg(t, 322, S7);
            cam = c.add(new V3(Math.cos(ang) * rad, h, Math.sin(ang) * rad));
            look = S.up(1.35).lerp(head, 0.25);
            shake = 0.32 * Math.exp(-(t - IMPACT1) / 9.0) * (t >= IMPACT1 ? 1 : 0) + 0.12 * Math.exp(-Math.max(0, t - 350) / 8.0) * (t >= 350 ? 1 : 0)
                    + 0.015;
        } else if (t < S8) {
            // 7: auf den Stein, das Schwert ist fast draußen
            double u = Ease.inOutSine(Ease.seg(t, S7, S8));
            // von der Gegenseite (die Figur steht dahinter), Blick auf Spalt und Klinge
            cam = S.sub(f.mul(Ease.lerp(1.25, 1.0, u))).add(R.mul(Ease.lerp(0.85, 0.7, u))).up(Ease.lerp(0.62, 0.72, u));
            look = S.up(ROCK + 0.42);
            shake = 0.012;
            fr.cut = t == S7;
        } else if (t < S9) {
            // 8: schneller, gedämpfter Schwung über den Spieler um den Punkt 5 Blöcke über dem Schwert
            cam = swing(t);
            look = S.up(1.4);
            shake = 0.05 * Math.exp(-Math.max(0, t - OUT) / 6.0) * (t >= OUT ? 1 : 0);
            fr.cut = t == S8;
        } else if (t < GROUND) {
            cam = sky(t);
            V3 target = t < FIRE ? Q : Q.lerp(S, 0.9);
            look = t < 580 ? S.up(1.4).lerp(Q, Ease.smooth(Ease.seg(t, 556, 580))) : target;
            if (t >= FIRE) look = Q.lerp(S, Ease.lerp(0.02, 0.6, Ease.smooth(Ease.seg(t, FIRE, GROUND))));
            shake = t >= 620 && t < FIRE ? 0.04 * Ease.seg(t, 620, FIRE) : t >= FIRE ? 0.25 * Math.exp(-(t - FIRE) / 5.0) : 0;
        } else if (t < S10) {
            // Einschlag von unten, dann langsam heran
            double u = Ease.inOutSine(Ease.seg(t, 690, 745));
            V3 wide = S.add(f.mul(10)).add(R.mul(6)).up(2.2);
            V3 close = S.add(f.mul(4.4)).add(R.mul(2.6)).up(1.9);
            cam = wide.lerp(close, u);
            look = S.up(5).lerp(A.up(1.8), u);
            shake = (t >= IMPACT2 ? 0.75 * Math.exp(-(t - IMPACT2) / 10.0) : 0) + (t >= IMPACT2 && t < ABSORB ? 0.035 : 0);
            fr.cut = t == GROUND;
        } else {
            // 10: der Spieler betrachtet das Schwert im Abendlicht
            double u = Ease.inOutSine(Ease.seg(t, S10, LEN));
            V3 base = A.add(d.mul(Ease.lerp(1.75, 1.3, u))).add(R.mul(Ease.lerp(0.95, 0.7, u))).up(Ease.lerp(1.45, 1.38, u));
            cam = base;
            look = A.up(1.22).add(R.mul(0.3)).add(d.mul(0.35));
            fr.cut = t == S10;
        }
        V3 sh = Ease.shake(t, shake, 0.9, 11);
        fr.cam = cam.add(sh);
        fr.look(look.add(sh.mul(0.5)));
    }

    /** Szene 8: Kreisbahn um den Punkt über dem Schwert (in der Ebene aus Blickrichtung und oben). */
    private V3 swing(int t) {
        V3 P = S.up(5);
        double r = 4.4;
        double a0 = -38, a1 = 150;
        double u = Ease.outBack(Ease.seg(t, S8, 500), 0.55);
        double a = Math.toRadians(Ease.lerp(a0, a1, u) + 8 * Ease.seg(t, 500, S9));
        V3 side = R.mul(0.9 * Math.sin(Math.toRadians(Ease.lerp(0, 180, Ease.seg(t, S8, 500)))));
        return P.add(f.mul(Math.cos(a) * r)).add(V3.UP.mul(Math.sin(a) * r)).add(side);
    }

    /** Szene 9 oben: Aufstieg ins All, Halt vor dem Stern. */
    private V3 sky(int t) {
        V3 start = swing(S9 - 1);
        V3 hold = Q.sub(R.mul(30)).up(-6);
        if (t < HOLD) {
            double u = Ease.inCubic(Ease.seg(t, S9, HOLD - 8));
            double y = Ease.lerp(start.y(), hold.y(), u);
            V3 h = new V3(start.x(), 0, start.z()).lerp(new V3(hold.x(), 0, hold.z()), Ease.smooth(Ease.seg(t, S9 + 10, HOLD - 4)));
            return new V3(h.x(), y, h.z());
        }
        return hold.add(R.mul(-1.5 * Ease.seg(t, HOLD, GROUND))).up(0.6 * Math.sin((t - HOLD) * 0.06));
    }

    // =================================================================================================================
    // Bild
    // =================================================================================================================

    @Override
    public void frame(int t, Frame fr) {
        camera(t, fr);
        actor(t, fr);
        // Balken rein und am Ende raus
        fr.bars = t < 14 ? Ease.smooth(t / 14.0) : t > LEN - 18 ? Ease.smooth((LEN - t) / 18.0) : 1;
        stoneAndSword(t, fr);
        sky(t, fr);
        shockwaves(t, fr);
        debris(t, fr);
        star(t, fr);
        sounds(t, fr);
        particles(t, fr);
        flashes(t, fr);
        if (t >= CARD && t < FADE + 6) {
            fr.card = "HEILIGES SCHWERT";
            fr.cardSub = "<#F2DA90>Das Schwert hat dich erwählt.";
        }
    }

    private void stoneAndSword(int t, Frame fr) {
        if (t == 0) fr.ev(new Ev.Shared(false));
        boolean glow = t >= 400 && t < S10;
        fr.prop(Prop.item("stone", glow ? STONE_GLOW : STONE, S.up(ROCK / 2), 1.0).facing(stoneYaw, 0).lit(glow ? 12 : -1).snap(0));
        if (t < OUT + 4) {
            double tremble = t >= IMPACT1 && t < OUT ? 0.006 * Ease.noise(t * 2.1, 9) : 0;
            fr.prop(Prop.item("sword", SWORD_MODEL, S.up(SWORD + rise(t)).add(R.mul(tremble)), 1.0).facing(stoneYaw, 0).lit(15));
        }
        // glühender Riss im Boden unter dem Stein
        if (t >= IMPACT1 && t < S10) {
            double s = 2 + 12 * Ease.outExpo(Ease.seg(t, IMPACT1, 340)) + (t >= IMPACT2 ? 10 * Ease.outExpo(Ease.seg(t, IMPACT2, 700)) : 0);
            fr.prop(Prop.item("cracks", "kuby:fx/cracks", S.up(0.03), 1).scaled(new V3(s, 1, s)).tint(EMBER).lit(15));
        }
    }

    private static String alpha(String base, int level) {
        return base + "_" + Math.max(1, Math.min(4, level));
    }

    private void sky(int t, Frame fr) {
        if (t == IMPACT1 + 2) fr.ev(new Ev.Sky(-1, 1));
        if (t == 566) fr.ev(new Ev.Sky(18000, 0));
        if (t == GROUND) fr.ev(new Ev.Sky(18000, 1));
        if (t == S10 - 4) fr.ev(new Ev.Sky(12650, 0));
        if (t == S10) fr.ev(new Ev.Mend());
        // Farbe über dem Himmel: Kuppel um die Kamera (innen sichtbar)
        int level = 0;
        int color = RED;
        if (t >= IMPACT1 + 2 && t < 566) level = 1 + (int) (3 * Ease.seg(t, IMPACT1 + 2, 350));
        else if (t >= 566 && t < GROUND) {
            level = 3;
            color = SPACE;
        } else if (t >= GROUND && t < S10 - 4) {
            level = 3;
            color = NIGHT_RED;
        }
        if (level > 0) fr.prop(Prop.item("dome", alpha("kuby:fx/dome", level), fr.cam, -46).tint(color).lit(15).snap(fr.cut ? 0 : 3));
        fr.grade = t >= IMPACT1 + 4 && t < 566 ? "red" : t >= 566 && t < GROUND ? "space" : t >= GROUND && t < S10 - 4 ? "red"
                : t >= S10 - 4 ? "warm" : t >= S5 ? "dark" : null;
        // schwarze Wolken über dem Schlachtfeld
        boolean storm = (t >= 316 && t < 566) || (t >= GROUND && t < S10 - 4);
        if (storm) {
            int lv = t < 566 ? 1 + (int) (3 * Ease.seg(t, 316, 356)) : 4;
            for (int i = 0; i < 7; i++) {
                double ang = i * 51.4 + Ease.rnd(seed, i) * 30;
                double rad = 8 + Ease.rnd(seed, i + 20) * 26;
                V3 base = S.add(V3.dir(ang, 0).mul(rad)).up(34 + Ease.rnd(seed, i + 40) * 14);
                V3 drift = R.mul(0.04 * t).add(f.mul(0.015 * t));
                double sc = 30 + Ease.rnd(seed, i + 60) * 26;
                fr.prop(Prop.item("cloud" + i, alpha("kuby:fx/cloud", lv), base.add(drift), sc)
                        .facing((float) (ang * 2), 0).tint(0x16110F).lit(4));
            }
        }
    }

    /** Ring am Boden: wächst und wird blasser. */
    private void ring(Frame fr, String id, int t, int t0, int dur, double rmax, int color, V3 at, double tiltPitch) {
        if (t < t0 || t >= t0 + dur) return;
        double u = Ease.seg(t, t0, t0 + dur);
        double r = 0.6 + rmax * Ease.outExpo(u);
        int lv = 4 - (int) Math.floor(u * 4);
        Prop p = Prop.item(id, alpha("kuby:fx/ring", lv), at, 1).scaled(new V3(r * 2, 1, r * 2)).tint(color).lit(15).snap(2);
        if (tiltPitch != 0) p = p.facing((float) V3.yawOf(f), (float) tiltPitch);
        fr.prop(p);
    }

    private void shockwaves(int t, Frame fr) {
        V3 g = S.up(0.12);
        ring(fr, "ring1", t, IMPACT1, 24, 22, 0xFFF1D0, g, 0);
        ring(fr, "ring1b", t, IMPACT1 + 1, 18, 11, 0xFF8A3A, S.up(1.2), 90);
        ring(fr, "ring2", t, 350, 18, 11, 0xFF5A2A, g, 0);
        ring(fr, "ring3", t, OUT, 20, 16, 0xFFF6E0, S.up(1.6), 0);
        ring(fr, "ringA", t, IMPACT2, 30, 34, 0xFFFFFF, g, 0);
        ring(fr, "ringB", t, IMPACT2 + 6, 26, 24, 0xFFE2A0, g.up(0.4), 0);
        ring(fr, "ringC", t, IMPACT2 + 14, 22, 15, 0xFFB060, g.up(0.8), 0);
        ring(fr, "ringV", t, IMPACT2 + 1, 22, 18, 0xFFFFFF, S.up(2.5), 90);
        ring(fr, "ringE", t, ABSORB + 22, 18, 9, 0xFFF0C0, S.up(1.8), 0);
        ring(fr, "ringStar", t, FIRE - 2, 14, 30, 0xFFFFFF, Q, 90);
    }

    /** Trümmer: fliegen aus dem Riss, fallen zurück. */
    private void debris(int t, Frame fr) {
        burst(t, fr, "a", IMPACT1 + 1, 22, 1.4, 5.0, 0.32, 0.62, 0);
        burst(t, fr, "b", OUT, 10, 1.0, 3.0, 0.3, 0.5, 100);
        burst(t, fr, "c", IMPACT2 + 1, 34, 2.0, 9.0, 0.45, 0.95, 200);
    }

    private void burst(int t, Frame fr, String tag, int t0, int n, double r0, double r1, double v0, double v1, int k0) {
        for (int i = 0; i < n; i++) {
            int k = k0 + i;
            double start = t0 + i * 0.6;
            double tau = t - start;
            if (tau < 0 || tau > 70) continue;
            double ang = Ease.rnd(seed, k) * 360;
            V3 dir = V3.dir(ang, 0);
            double r = Ease.lerp(r0, r1, Ease.rnd(seed, k + 500));
            double vy = Ease.lerp(v0, v1, Ease.rnd(seed, k + 900));
            double vh = 0.04 + 0.16 * Ease.rnd(seed, k + 1300);
            double y = vy * tau - 0.5 * 0.045 * tau * tau;
            if (y < -0.4 && tau > 3) continue;
            V3 pos = S.add(dir.mul(r + vh * tau)).up(Math.max(-0.4, y) + 0.2);
            double sc = 0.28 + 0.4 * Ease.rnd(seed, k + 1700);
            String mat = Ease.rnd(seed, k + 2100) < 0.55 ? "@ground" : Ease.rnd(seed, k + 2100) < 0.85 ? "minecraft:blackstone" : "minecraft:magma_block";
            double[] rot = Ease.quatAxis(new V3(Ease.rnd(seed, k + 2500) - 0.5, 1, Ease.rnd(seed, k + 2900) - 0.5), tau * (9 + 14 * Ease.rnd(seed, k + 3300)));
            Prop p = Prop.block("deb" + tag + i, mat, pos, sc);
            V3 half = new V3(-sc / 2, -sc / 2, -sc / 2);
            fr.prop(p.rotated(rot).moved(Ease.rotate(rot, half)));
        }
    }

    /** Szene 9: Stern, Aufladen, Strahl. */
    private void star(int t, Frame fr) {
        if (t >= 586 && t < GROUND) {
            double grow = Ease.outCubic(Ease.seg(t, 586, 612));
            double pulse = t >= 620 && t < FIRE ? 1 + 0.12 * Math.sin((t - 620) * (0.3 + 0.02 * (t - 620))) * Ease.seg(t, 620, FIRE) : 1;
            double sc = 13 * grow * pulse;
            double[] rot = Ease.quatMul(Ease.quatAxis(V3.UP, t * 1.6), Ease.quatAxis(f, 18));
            fr.prop(Prop.item("star", "kuby:fx/star", Q, sc).rotated(rot).lit(15).tint(t >= 620 ? 0xFFF4D8 : 0xFFFFFF));
            double halo = (26 + 26 * Ease.seg(t, 620, FIRE)) * grow * pulse;
            fr.prop(Prop.item("halo", "kuby:fx/glow", Q, halo).board(1).lit(15).tint(t >= 620 ? 0xFFD98A : 0xBFD8FF));
            fr.prop(Prop.item("flare", "kuby:fx/flare", Q, halo * 1.6).board(1).lit(15).tint(0xFFFFFF)
                    .rotated(Ease.quatAxis(new V3(0, 0, 1), t * 0.8)));
            // Funken fliegen in den Stern (Aufladen)
            if (t >= 620 && t < FIRE) {
                for (int i = 0; i < 10; i++) {
                    double ph = ((t - 620) * 0.06 + Ease.rnd(seed, 4000 + i)) % 1.0;
                    V3 dir = V3.dir(Ease.rnd(seed, 4100 + i) * 360, Ease.rnd(seed, 4200 + i) * 140 - 70);
                    V3 pos = Q.add(dir.mul(30 * (1 - Ease.inCubic(ph))));
                    fr.prop(Prop.item("spark" + i, "kuby:fx/flare", pos, 2.2 * (0.4 + ph)).board(1).lit(15).tint(0xFFE7A8).snap(1));
                }
            }
        }
        // Strahl: vom Stern aufs Schwert
        if (t >= FIRE && t < S10 - 2) {
            V3 target = S.up(1.2);
            V3 dir = target.sub(Q);
            double full = dir.len();
            V3 n = dir.norm();
            double len = full * Ease.outExpo(Ease.seg(t, FIRE, IMPACT2));
            double w = (t < IMPACT2 ? 1.1 : 1.9 + 0.3 * Math.sin(t * 0.9)) * (1 - Ease.smooth(Ease.seg(t, ABSORB, ABSORB + 22)));
            if (w > 0.02 && len > 0.5) {
                double[] rot = Ease.quatTo(V3.UP, n);
                fr.prop(new Prop("beam", Prop.Kind.ITEM, "kuby:fx/beam", Q, 0, 0, n.mul(len / 2), rot, new V3(w, len, w), 15, 0, 0xFFFFFF, 2));
                fr.prop(new Prop("beamglow", Prop.Kind.ITEM, "kuby:fx/beam_glow", Q, 0, 0, n.mul(len / 2), rot, new V3(w * 2.4, len, w * 2.4),
                        15, 0, 0xFFD27A, 2));
            }
        }
        // Licht am Schwert: Aufladen und Abendlicht
        if (t >= IMPACT2 && t < LEN) {
            V3 at = t < S10 ? A.up(2.6) : A.up(1.15).add(R.mul(0.35)).add(d.mul(0.45));
            double s = t < S10 ? 3.2 + 0.6 * Math.sin(t * 0.4) : 1.1 + 0.15 * Math.sin(t * 0.12);
            fr.prop(Prop.item("swordglow", "kuby:fx/glow", at, s).board(1).lit(15).tint(t < S10 ? 0xFFF0C8 : 0xFFB45A));
        }
    }

    // =================================================================================================================
    // Ton und Licht
    // =================================================================================================================

    private static Ev.Sound snd(String key, double vol, double pitch) {
        return new Ev.Sound(key, (float) vol, (float) pitch);
    }

    private void sounds(int t, Frame fr) {
        switch (t) {
            case 0 -> {
                fr.ev(snd("kuby:cine.wind", 0.8, 1));
                fr.ev(snd("kuby:cine.drone", 0.9, 1));
            }
            case 150 -> fr.ev(snd("kuby:cine.drone", 0.9, 0.95));
            case 182 -> fr.ev(snd("kuby:cine.choir", 0.55, 1));
            case 240, 258, 271, 281, 288 -> fr.ev(snd("kuby:cine.heartbeat", 1, 1));
            case 254 -> fr.ev(snd("kuby:cine.grip", 1, 1));
            case SILENT -> {
                fr.ev(new Ev.Silence());
                fr.ev(snd("kuby:cine.tinnitus", 0.35, 1));
            }
            case S6 -> fr.ev(snd("kuby:cine.whoosh", 1, 0.9));
            case IMPACT1 -> {
                fr.ev(snd("kuby:cine.impact", 1, 1));
                fr.ev(snd("minecraft:entity.warden.sonic_boom", 1, 0.5));
                fr.ev(snd("minecraft:entity.generic.explode", 1, 0.55));
                fr.ev(snd("minecraft:item.trident.thunder", 1, 0.6));
            }
            case IMPACT1 + 4 -> fr.ev(snd("kuby:cine.rumble", 1, 1));
            case 336 -> fr.ev(snd("minecraft:entity.lightning_bolt.thunder", 1, 0.7));
            case 350 -> fr.ev(snd("kuby:cine.impact_small", 1, 1));
            case 370 -> fr.ev(snd("kuby:cine.rumble", 0.8, 0.9));
            case S7 + 2 -> fr.ev(snd("kuby:cine.grind", 1, 1));
            case S8 -> fr.ev(snd("kuby:cine.whoosh", 1, 1.15));
            case OUT -> {
                fr.ev(snd("kuby:cine.shing", 1, 1));
                fr.ev(snd("kuby:cine.impact_small", 1, 0.8));
                fr.ev(snd("minecraft:block.beacon.power_select", 1, 0.6));
            }
            case S9 -> fr.ev(snd("kuby:cine.ascend", 1, 1));
            case 620 -> fr.ev(snd("kuby:cine.charge", 1, 1));
            case FIRE -> fr.ev(snd("kuby:cine.beam", 1, 1));
            case IMPACT2 -> {
                fr.ev(snd("kuby:cine.impact", 1, 0.8));
                fr.ev(snd("kuby:cine.impact_small", 1, 0.6));
                fr.ev(snd("minecraft:entity.warden.sonic_boom", 1, 0.5));
                fr.ev(snd("minecraft:entity.generic.explode", 1, 0.5));
                fr.ev(snd("minecraft:entity.lightning_bolt.thunder", 1, 0.5));
            }
            case IMPACT2 + 8 -> fr.ev(snd("kuby:cine.rumble", 1, 0.8));
            case 700 -> fr.ev(snd("kuby:cine.charge", 0.7, 0.7));
            case ABSORB + 22 -> {
                fr.ev(snd("kuby:cine.shing", 1, 0.85));
                fr.ev(snd("minecraft:item.totem.use", 0.5, 1.3));
            }
            case S10 -> fr.ev(snd("kuby:cine.resolve", 0.9, 1));
            case CARD -> fr.ev(snd("kuby:cine.stinger", 1, 1));
            default -> {
            }
        }
        // Schritte (je 0,72 Blöcke)
        if (t > 4 && t < ARRIVE + 2) {
            double s0 = P0.dist(actorAt(t - 1)), s1 = P0.dist(actorAt(t));
            if (Math.floor(s1 / 0.72) > Math.floor(s0 / 0.72)) {
                fr.ev(new Ev.SoundAt("kuby:cine.step", actorAt(t), t >= S3 ? 1.0f : 0.7f, (float) (0.9 + 0.2 * Ease.rnd(seed, t))));
            }
        }
    }

    private void flashes(int t, Frame fr) {
        switch (t) {
            case 0 -> fr.ev(new Ev.Flash(0x000000, 0, 4, 22));
            // Impact-Frames: weiß, schwarz, weiß
            case IMPACT1 -> fr.ev(new Ev.Flash(0xFFFFFF, 0, 1, 0));
            case IMPACT1 + 1 -> fr.ev(new Ev.Flash(0x000000, 0, 1, 0));
            case IMPACT1 + 2 -> fr.ev(new Ev.Flash(0xFFFFFF, 0, 0, 8));
            case 336 -> fr.ev(new Ev.Flash(0xFFFFFF, 0, 0, 6));
            case 350 -> fr.ev(new Ev.Flash(0xFF3A20, 0, 0, 5));
            case OUT -> fr.ev(new Ev.Flash(0xFFFFFF, 0, 1, 6));
            case FIRE - 2 -> fr.ev(new Ev.Flash(0xFFFFFF, 0, 1, 4));
            case IMPACT2 -> fr.ev(new Ev.Flash(0xFFFFFF, 0, 1, 0));
            case IMPACT2 + 1 -> fr.ev(new Ev.Flash(0x000000, 0, 1, 0));
            case IMPACT2 + 2 -> fr.ev(new Ev.Flash(0xFFFFFF, 0, 1, 0));
            case IMPACT2 + 3 -> fr.ev(new Ev.Flash(0x000000, 0, 1, 0));
            case IMPACT2 + 4 -> fr.ev(new Ev.Flash(0xFFFFFF, 0, 0, 12));
            case ABSORB + 22 -> fr.ev(new Ev.Flash(0xFFF2D0, 0, 2, 14));
            case FADE -> fr.ev(new Ev.Flash(0x000000, 20, 40, 20));
            default -> {
            }
        }
        if (t == IMPACT1 + 1) fr.ev(new Ev.Crack(S, 3.5, seed));
        if (t == IMPACT1 + 6) fr.ev(new Ev.Crack(S, 6.5, seed));
        if (t == IMPACT1 + 14) fr.ev(new Ev.Crack(S, 9.5, seed));
        if (t == OUT + 1) fr.ev(new Ev.Crack(S, 12.5, seed));
        if (t == IMPACT2 + 1) fr.ev(new Ev.Crack(S, 18, seed));
    }

    private void particles(int t, Frame fr) {
        V3 slit = S.up(ROCK + 0.04);
        if (t >= IMPACT1 && t < S10) {
            // Glut und Rauch aus den Rissen
            if (t % 3 == 0) {
                double a = Ease.rnd(seed, 7000 + t) * 360, r = 1.5 + Ease.rnd(seed, 7100 + t) * 9;
                V3 at = S.add(V3.dir(a, 0).mul(r)).up(0.2);
                fr.ev(Ev.Particle.of("LAVA", at, 1, 0.2, 0));
                fr.ev(new Ev.Particle("LARGE_SMOKE", at, 2, 0.3, 0.1, 0.3, 0.02, -1, 1));
            }
            fr.ev(Ev.Particle.dust(S.up(1.5), 3, 6, 0x8A1010, 2.2f));
        }
        if (t >= S7 && t < OUT + 2) {
            fr.ev(new Ev.Particle("ELECTRIC_SPARK", slit, 6, 0.08, 0.02, 0.08, 0.25, -1, 1));
            fr.ev(new Ev.Particle("CRIT", slit, 4, 0.06, 0.02, 0.06, 0.4, -1, 1));
            if (t % 2 == 0) fr.ev(Ev.Particle.of("LAVA", slit, 1, 0.05, 0));
        }
        if (t == IMPACT1 || t == IMPACT2 || t == OUT) {
            fr.ev(Ev.Particle.of("EXPLOSION_EMITTER", S.up(0.6), 1, 0, 0));
            fr.ev(Ev.Particle.of("FLASH", S.up(1.4), 2, 0.2, 0));
            fr.ev(new Ev.Particle("CLOUD", S.up(0.3), 80, 3, 0.2, 3, 0.35, -1, 1));
        }
        if (t == IMPACT2) fr.ev(Ev.Particle.of("SONIC_BOOM", S.up(1.2), 6, 2.5, 0));
        if (t >= IMPACT2 && t < ABSORB + 20) {
            // Aufladen: Spirale um die Figur
            for (int i = 0; i < 3; i++) {
                double a = (t * 18 + i * 120) % 360;
                double y = ((t * 0.05 + i * 0.33) % 1.0) * 2.2;
                V3 at = A.add(V3.dir(a, 0).mul(0.75)).up(y);
                fr.ev(new Ev.Particle("END_ROD", at, 1, 0, 0, 0, 0, -1, 1));
                fr.ev(Ev.Particle.dust(at, 1, 0.05, 0xFFD45A, 1.4f));
            }
            fr.ev(new Ev.Particle("ELECTRIC_SPARK", A.up(2.2), 4, 0.4, 0.6, 0.4, 0.3, -1, 1));
        }
        if (t >= S10) {
            // warmer Staub im Abendlicht, Funkeln am Schwert
            if (t % 2 == 0) fr.ev(Ev.Particle.dust(A.up(1.4).add(d.mul(0.8)), 2, 1.2, 0xFFC873, 0.9f));
            if (t % 5 == 0) fr.ev(new Ev.Particle("END_ROD", A.up(1.2).add(R.mul(0.35)).add(d.mul(0.45)), 1, 0.15, 0.25, 0.15, 0.003, -1, 1));
        }
    }

    /** Für die Vorschau: Mitte des Steins. */
    public V3 stone() {
        return S;
    }
}
