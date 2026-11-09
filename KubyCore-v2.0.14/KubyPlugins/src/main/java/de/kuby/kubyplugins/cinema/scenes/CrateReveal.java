package de.kuby.kubyplugins.cinema.scenes;

import de.kuby.kubyplugins.cinema.Ease;
import de.kuby.kubyplugins.cinema.Ev;
import de.kuby.kubyplugins.cinema.Frame;
import de.kuby.kubyplugins.cinema.Prop;
import de.kuby.kubyplugins.cinema.Script;
import de.kuby.kubyplugins.cinema.V3;

/**
 * Kiste öffnen als kurze Kino-Szene (etwa 9,5 Sekunden, mit Schleichen überspringbar):
 * über die Schulter auf die Kiste, Lichtsäule steigt auf, die Kamera kreist tief um die Kiste, die Farbe steigert
 * sich bis zur Seltenheit des Gewinns (blau, lila, gold), Explosion mit Druckwelle, der Gewinn schwebt groß und
 * drehend über der Kiste, Titelkarte mit Name, Seltenheit und Chance.
 *
 * @param crate  Mitte der Kiste auf ihrer Unterkante
 * @param viewer wo der Spieler steht (Füße)
 * @param tier   0 gewöhnlich, 1 selten, 2 episch, 3 legendär
 * @param model  Itemmodell des Gewinns (z. B. "minecraft:diamond_sword" oder ein eigenes)
 */
public final class CrateReveal implements Script {

    static final int BURST = 104, CLOSE = 106, LEN = 196;
    private static final int[] COLORS = {0x9FE8A0, 0x64C0FF, 0xC77DFF, 0xFFC83A};
    private static final String[] GRADES = {"dark", "cold", "purple", "gold"};
    private static final String[] NAMES = {"Gewöhnlich", "Selten", "Episch", "Legendär"};

    private final V3 C, P, f, d, R;
    private final int tier;
    private final String model, name, chance;
    private final long seed;

    public CrateReveal(V3 crate, V3 viewer, int tier, String model, String name, String chance, long seed) {
        this.C = crate;
        V3 to = viewer.sub(crate);
        this.f = new V3(to.x(), 0, to.z()).len() < 0.3 ? new V3(0, 0, 1) : to.flat();
        this.d = f.mul(-1);
        this.R = d.right();
        double dist = Math.max(1.6, Math.min(3.0, Math.hypot(to.x(), to.z())));
        this.P = new V3(crate.x() + f.x() * dist, viewer.y(), crate.z() + f.z() * dist);
        this.tier = Math.max(0, Math.min(3, tier));
        this.model = model;
        this.name = name;
        this.chance = chance;
        this.seed = seed;
    }

    @Override
    public int length() {
        return LEN;
    }

    @Override
    public int skippableFrom() {
        return 8;
    }

    /** Farbstufe zum Zeitpunkt t (steigert sich bis zur echten Seltenheit). */
    private int level(int t) {
        int lv = 1;
        if (t >= 72 && tier >= 2) lv = 2;
        if (t >= 90 && tier >= 3) lv = 3;
        if (t >= BURST) lv = tier;
        return lv;
    }

    @Override
    public void frame(int t, Frame fr) {
        int col = COLORS[level(t)];
        V3 top = C.up(1.0);
        // ---------- Figur ----------
        fr.actor = P;
        fr.actorYaw = V3.yawOf(d);
        fr.headYaw = fr.actorYaw;
        fr.headPitch = t < BURST ? 18 : -12;
        if (t >= BURST && t < CLOSE + 50) fr.hand = "kuby:cine/empty|block";
        // ---------- Kamera ----------
        V3 cam;
        V3 look;
        double shake = 0;
        if (t < 40) {
            double u = Ease.inOutSine(t / 40.0);
            cam = P.sub(d.mul(Ease.lerp(2.2, 1.7, u))).add(d.left().mul(1.05)).up(2.15);
            look = top.up(0.2);
        } else if (t < CLOSE) {
            double u = Ease.inOutSine(Ease.seg(t, 40, CLOSE));
            double a0 = Math.toDegrees(Math.atan2(f.z(), f.x())) + 70;
            double ang = Math.toRadians(a0 + 110 * u);
            double r = Ease.lerp(2.8, 2.3, u);
            cam = C.add(new V3(Math.cos(ang) * r, Ease.lerp(0.45, 0.8, u), Math.sin(ang) * r));
            look = top.up(Ease.lerp(0.4, 0.9, u));
            fr.cut = t == 40;
            shake = t >= 72 ? 0.02 + 0.04 * Ease.seg(t, 72, BURST) : 0;
        } else {
            double u = Ease.inOutSine(Ease.seg(t, CLOSE, LEN));
            V3 dir = f.rotY(Ease.lerp(-14, 10, u));
            cam = C.add(dir.mul(Ease.lerp(2.5, 1.85, u))).up(Ease.lerp(1.65, 1.8, u));
            // Gewinn etwas unter der Bildmitte, darüber steht der Name (Titel)
            look = C.up(2.05);
            fr.cut = t == CLOSE;
            shake = 0.22 * Math.exp(-(t - BURST) / 6.0);
        }
        V3 sh = Ease.shake(t, shake, 1.1, 5);
        fr.cam = cam.add(sh);
        fr.look(look.add(sh.mul(0.4)));
        // ---------- Bildschirm ----------
        fr.bars = t < 12 ? Ease.smooth(t / 12.0) : t > LEN - 14 ? Ease.smooth((LEN - t) / 14.0) : 1;
        fr.grade = t < 20 ? "dark" : GRADES[level(t)];
        if (t >= CLOSE + 8 && t < LEN - 10) {
            fr.card = name;
            fr.cardSub = "<#" + String.format("%06X", COLORS[tier]) + ">" + NAMES[tier] + " <gray>· " + chance;
        }
        // ---------- Licht und Teile ----------
        if (t >= 14 && t < BURST + 30) {
            double grow = Ease.outCubic(Ease.seg(t, 14, 60));
            double w = (0.18 + 0.25 * Ease.seg(t, 60, BURST)) * grow * (t >= BURST ? Math.max(0, 1 - (t - BURST) / 30.0) : 1);
            double len = 12 * grow;
            if (w > 0.01) {
                fr.prop(Prop.item("pillar", "kuby:fx/beam", top, 1).scaled(new V3(w, len, w)).moved(V3.UP.mul(len / 2)).tint(col).lit(15).snap(3));
                fr.prop(Prop.item("pillarglow", "kuby:fx/beam_glow", top, 1).scaled(new V3(w * 3, len, w * 3)).moved(V3.UP.mul(len / 2))
                        .tint(col).lit(15).snap(3));
            }
            double pulse = 1 + 0.15 * Math.sin(t * (0.3 + 0.01 * t));
            fr.prop(Prop.item("crateglow", "kuby:fx/glow", top.up(0.1), (1.2 + 1.6 * Ease.seg(t, 20, BURST)) * pulse).board(1).tint(col).lit(15));
        }
        // Funken, die in die Kiste gezogen werden
        if (t >= 44 && t < BURST) {
            for (int i = 0; i < 8; i++) {
                double ph = ((t - 44) * 0.05 + Ease.rnd(seed, 300 + i)) % 1.0;
                V3 dir = V3.dir(Ease.rnd(seed, 400 + i) * 360, -10 - Ease.rnd(seed, 500 + i) * 50);
                V3 pos = top.add(dir.mul(3.2 * (1 - Ease.inCubic(ph))));
                fr.prop(Prop.item("spark" + i, "kuby:fx/flare", pos, 0.35 + 0.5 * ph).board(1).tint(col).lit(15).snap(1));
            }
        }
        // Druckwelle und Gewinn
        if (t >= BURST && t < BURST + 18) {
            double u = Ease.seg(t, BURST, BURST + 18);
            int lv = 4 - (int) Math.floor(u * 4);
            double r = 0.5 + (4 + 3 * tier) * Ease.outExpo(u);
            fr.prop(Prop.item("ring", "kuby:fx/ring_" + Math.max(1, lv), C.up(0.15), 1).scaled(new V3(r * 2, 1, r * 2)).tint(col).lit(15).snap(2));
            if (tier >= 2) {
                fr.prop(Prop.item("ringv", "kuby:fx/ring_" + Math.max(1, lv), C.up(1.6), 1).scaled(new V3(r * 1.4, 1, r * 1.4))
                        .facing(V3.yawOf(f), 90).tint(col).lit(15).snap(2));
            }
        }
        if (t >= BURST) {
            double s = 0.95 * Ease.outBack(Ease.seg(t, BURST, BURST + 14), 1.6);
            double bob = 0.06 * Math.sin((t - BURST) * 0.12);
            double[] rot = Ease.quatAxis(V3.UP, (t - BURST) * 4.5);
            fr.prop(Prop.item("reward", model, C.up(1.62 + bob), s).rotated(rot).lit(15).snap(2));
            fr.prop(Prop.item("rewardglow", "kuby:fx/glow", C.up(1.62 + bob), 2.2 * s * (1 + 0.08 * Math.sin(t * 0.2))).board(1).tint(col).lit(15));
            if (tier >= 2) {
                fr.prop(Prop.item("rewardflare", "kuby:fx/flare", C.up(1.62 + bob), 3.4 * s).board(1).tint(0xFFFFFF).lit(15)
                        .rotated(Ease.quatAxis(new V3(0, 0, 1), t * 1.5)));
            }
        }
        // Legendär: Himmel wird Nacht, goldene Kuppel
        if (tier == 3 && t == 92) fr.ev(new Ev.Sky(18000, -1));
        if (tier == 3 && t >= 92) {
            fr.prop(Prop.item("dome", "kuby:fx/dome_2", fr.cam, -40).tint(0xFFB43A).lit(15).snap(fr.cut ? 0 : 3));
        }
        sounds(t, fr);
        flashes(t, fr);
        particles(t, fr, col);
    }

    private static Ev.Sound snd(String k, double v, double p) {
        return new Ev.Sound(k, (float) v, (float) p);
    }

    private void sounds(int t, Frame fr) {
        if (t == 0) {
            fr.ev(snd("kuby:cine.drone", 0.6, 1.1));
            fr.ev(snd("kuby:cine.whoosh", 0.6, 1.2));
        }
        if (t == 20 || t == 36) fr.ev(snd("kuby:cine.heartbeat", 0.9, 1.05));
        if (t == 40) fr.ev(snd("kuby:cine.charge", 0.9, 1.0 + 0.1 * tier));
        if (t == 72 && tier >= 2) fr.ev(snd("kuby:cine.shing", 0.9, 1.0));
        if (t == 90 && tier >= 3) {
            fr.ev(snd("kuby:cine.shing", 1, 0.8));
            fr.ev(snd("minecraft:block.beacon.power_select", 0.8, 1.2));
        }
        if (t == BURST - 6) fr.ev(new Ev.Silence());
        if (t == BURST) {
            fr.ev(snd(tier >= 2 ? "kuby:cine.impact" : "kuby:cine.impact_small", 1, tier >= 3 ? 0.9 : 1.1));
            fr.ev(snd("minecraft:entity.firework_rocket.large_blast", 1, 0.8));
            if (tier >= 3) fr.ev(snd("minecraft:ui.toast.challenge_complete", 0.8, 1));
            else fr.ev(snd("minecraft:entity.player.levelup", 0.8, 1));
        }
        if (t == CLOSE + 8) fr.ev(snd(tier >= 2 ? "kuby:cine.stinger" : "kuby:cine.shing", 0.8, tier >= 2 ? 1 : 1.3));
        if (t == CLOSE + 10 && tier >= 2) fr.ev(snd("kuby:cine.resolve", 0.6, 1));
    }

    private void flashes(int t, Frame fr) {
        if (t == 0) fr.ev(new Ev.Flash(0x000000, 0, 2, 12));
        if (t == 72 && tier >= 2) fr.ev(new Ev.Flash(COLORS[2], 0, 0, 5));
        if (t == 90 && tier >= 3) fr.ev(new Ev.Flash(COLORS[3], 0, 0, 5));
        if (t == BURST) fr.ev(new Ev.Flash(0xFFFFFF, 0, 1, 0));
        if (t == BURST + 1 && tier >= 2) fr.ev(new Ev.Flash(0x000000, 0, 1, 0));
        if (t == BURST + (tier >= 2 ? 2 : 1)) fr.ev(new Ev.Flash(0xFFFFFF, 0, 0, 8));
        if (t == LEN - 14) fr.ev(new Ev.Flash(0x000000, 10, 6, 10));
    }

    private void particles(int t, Frame fr, int col) {
        V3 top = C.up(1.0);
        if (t >= 40 && t < BURST && t % 2 == 0) {
            fr.ev(Ev.Particle.dust(top.up(0.3), 3, 0.6, col, 1.1f));
            fr.ev(new Ev.Particle("END_ROD", top, 1, 0.3, 0.1, 0.3, 0.05, -1, 1));
        }
        if (t == BURST) {
            fr.ev(Ev.Particle.of("FIREWORK", top.up(0.4), 60, 0.3, 0.4));
            fr.ev(Ev.Particle.dust(top.up(0.5), 60, 1.4, col, 2.0f));
            if (tier >= 2) fr.ev(Ev.Particle.of("EXPLOSION_EMITTER", top, 1, 0, 0));
        }
        if (t > BURST && t % 3 == 0) {
            fr.ev(new Ev.Particle("END_ROD", C.up(1.62), 1, 0.4, 0.4, 0.4, 0.01, -1, 1));
            fr.ev(Ev.Particle.dust(C.up(1.62), 2, 0.6, col, 0.9f));
        }
    }
}
