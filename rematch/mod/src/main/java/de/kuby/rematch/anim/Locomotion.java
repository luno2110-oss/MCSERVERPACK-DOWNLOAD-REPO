package de.kuby.rematch.anim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fortbewegung als Grundebene: Stehen, Gehen, Laufen, Sprinten nach Tempo gemischt (gemeinsame Phase, damit die Füße
 * zusammenpassen), dazu eine Luft-Pose. Die Abspielrate folgt dem echten Tempo: Schrittlänge = speed_at * Länge,
 * so rutschen die Füße nicht. Zusätze: Vorlage beim Beschleunigen, Schräglage in Kurven, Kopf folgt dem Blick.
 */
public final class Locomotion {
    public record Gait(Clip clip, double speedAt) { }

    private final Clip idle, air;
    private final List<Gait> gaits = new ArrayList<>();
    private final Pose a, b;
    private double phase, idleTime, airTime, airW, speed, accel, turn;
    private boolean grounded = true;
    // Eingaben
    private double inSpeed, inTurn, lookYaw, lookPitch;

    public Locomotion(ClipLibrary lib, String idle, String air, String... gaits) {
        this.idle = lib.get(idle);
        this.air = lib.get(air);
        for (String g : gaits) {
            Clip c = lib.get(g);
            if (c != null && c.meta.speedAt() > 0) this.gaits.add(new Gait(c, c.meta.speedAt()));
        }
        this.gaits.sort(Comparator.comparingDouble(Gait::speedAt));
        int n = lib.rig().size();
        a = new Pose(n);
        b = new Pose(n);
    }

    public boolean usable() { return idle != null && !gaits.isEmpty(); }

    /** Tempo in Blöcken/s (waagerecht), Drehrate des Körpers in Grad/s, Boden, Blick relativ zum Körper (Grad). */
    public void input(double speed, double turnRate, boolean grounded, double lookYaw, double lookPitch) {
        this.inSpeed = speed;
        this.inTurn = turnRate;
        this.grounded = grounded;
        this.lookYaw = lookYaw;
        this.lookPitch = lookPitch;
    }

    public double speed() { return speed; }

    public void update(double dt) {
        double old = speed;
        speed += (inSpeed - speed) * (1 - Math.exp(-dt * 12));
        double ac = dt > 0 ? (speed - old) / dt : 0;
        accel += (ac - accel) * (1 - Math.exp(-dt * 6));
        turn += (inTurn - turn) * (1 - Math.exp(-dt * 8));
        idleTime += dt;
        if (grounded) { airTime = 0; airW = Math.max(0, airW - dt / 0.08); }
        else { airTime += dt; if (airTime > 0.06) airW = Math.min(1, airW + dt / 0.1); }
        // Phase: Zyklen pro Sekunde aus den beiden Nachbar-Gangarten gemischt
        int[] ij = new int[2];
        double w = gaitWeights(speed, ij);
        Gait g0 = gaits.get(ij[0]), g1 = gaits.get(ij[1]);
        // Minecraft läuft für die Figurgröße sehr schnell: Schrittfrequenz steigt nur abgeschwächt (^0.8) mit
        double cps0 = cadence(speed, g0), cps1 = cadence(speed, g1);
        phase = (phase + dt * (cps0 + (cps1 - cps0) * w)) % 1.0;
    }

    private static double cadence(double v, Gait g) {
        double r = Math.max(v, g.speedAt * 0.35) / g.speedAt;
        return (r > 1 ? Math.pow(r, 0.8) : r) / g.clip.length;
    }

    /** Gewicht zwischen zwei Gangarten (Indizes in ij) für ein Tempo. */
    private double gaitWeights(double v, int[] ij) {
        int n = gaits.size();
        if (v <= gaits.get(0).speedAt || n == 1) { ij[0] = ij[1] = 0; return 0; }
        for (int i = 0; i < n - 1; i++) {
            double s0 = gaits.get(i).speedAt, s1 = gaits.get(i + 1).speedAt;
            if (v <= s1) { ij[0] = i; ij[1] = i + 1; return Easing.SMOOTH.apply((v - s0) / (s1 - s0)); }
        }
        ij[0] = ij[1] = n - 1;
        return 0;
    }

    public void sample(Pose out, Rig rig) {
        int[] ij = new int[2];
        double w = gaitWeights(speed, ij);
        Gait g0 = gaits.get(ij[0]), g1 = gaits.get(ij[1]);
        g0.clip.sample(phase * g0.clip.length, out);
        if (ij[1] != ij[0]) {
            g1.clip.sample(phase * g1.clip.length, a);
            out.lerpTo(a, w);
        }
        // Stehen einblenden, wenn langsam
        double move = Easing.SMOOTH.apply(Math.min(1, speed / (gaits.get(0).speedAt * 0.6)));
        if (move < 1) {
            idle.sample(idleTime % idle.length, a);
            a.lerpTo(out, move);
            out.copyFrom(a);
        }
        if (air != null && airW > 0) {
            air.sample(Math.min(airTime, air.length), a);
            out.lerpTo(a, Easing.SMOOTH.apply(airW));
        }
        // Zusätze: Vorlage beim Beschleunigen, Schräglage in Kurven, Blick
        add(out, rig, "middle_body", -clamp(accel * 2.2, -10, 12), 0, 0);
        double bank = clamp(turn * speed * 0.006, -10, 10);
        add(out, rig, "full_body", 0, 0, bank);
        add(out, rig, "head", -clamp(lookPitch, -70, 70), -clamp(lookYaw, -80, 80) * 0.75, -bank * 0.5);
        add(out, rig, "torso2", 0, -clamp(lookYaw, -80, 80) * 0.25, 0);
    }

    private static void add(Pose p, Rig rig, String bone, double x, double y, double z) {
        int i = rig.index(bone);
        if (i < 0) return;
        p.rot[i][0] += x; p.rot[i][1] += y; p.rot[i][2] += z;
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
