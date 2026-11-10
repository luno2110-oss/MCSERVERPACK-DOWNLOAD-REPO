package de.kuby.rematch.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Leuchtende Effekt-Partikel: Größe und Deckkraft folgen Kurven über die Lebenszeit.
 * Stern: blitzt groß auf und fällt schnell zusammen. Ring: wächst schnell (Schockwelle). Funke: fliegt mit Schwerkraft,
 * schrumpft. Staub: quillt auf, sinkt, blasst aus (normal beleuchtet). Spur: kleiner Lichtpunkt hinter dem Ball.
 */
public final class FxParticle extends SingleQuadParticle {
    public enum Kind { STERN, RING, FUNKE, STAUB, SPUR }

    private final Kind kind;
    private final float size0, size1, spin;

    private FxParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                       SpriteSet sprites, Kind kind, RandomSource rnd) {
        super(level, x, y, z, sprites.get(rnd));
        this.kind = kind;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        switch (kind) {
            case STERN -> { lifetime = 7; size0 = 0.6f; size1 = 1.9f; friction = 1f; gravity = 0; spin = (rnd.nextFloat() - 0.5f) * 0.3f; }
            case RING -> { lifetime = 9; size0 = 0.25f; size1 = 3.4f; friction = 1f; gravity = 0; spin = 0; }
            case FUNKE -> { lifetime = 9 + rnd.nextInt(8); size0 = 0.16f + rnd.nextFloat() * 0.08f; size1 = 0.02f; friction = 0.86f; gravity = 0.9f; spin = (rnd.nextFloat() - 0.5f) * 0.6f; hasPhysics = true; }
            case STAUB -> { lifetime = 18 + rnd.nextInt(10); size0 = 0.25f; size1 = 0.75f + rnd.nextFloat() * 0.3f; friction = 0.82f; gravity = -0.01f; spin = (rnd.nextFloat() - 0.5f) * 0.08f; }
            default -> { lifetime = 7; size0 = 0.24f; size1 = 0.02f; friction = 0.9f; gravity = 0; spin = 0; }
        }
        this.roll = rnd.nextFloat() * 6.283f;
        this.oRoll = roll;
        this.quadSize = size0;
        this.alpha = 1f;
    }

    @Override
    protected Layer getLayer() { return Layer.TRANSLUCENT; }

    @Override
    protected int getLightColor(float partialTick) {
        return kind == Kind.STAUB ? super.getLightColor(partialTick) : 0xF000F0; // leuchtet selbst
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = Math.min(1f, (age + partialTick) / lifetime);
        float e = switch (kind) {
            case STERN -> t < 0.25f ? (float) Math.sin(t / 0.25f * Math.PI / 2) : 1f - (t - 0.25f) / 0.75f * 0.9f;
            case RING, STAUB -> 1f - (float) Math.pow(1 - t, 3);
            default -> t;
        };
        if (kind == Kind.STERN) return size1 * Math.max(0.05f, e);
        return size0 + (size1 - size0) * e;
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += spin;
        float t = Math.min(1f, (float) age / lifetime);
        alpha = switch (kind) {
            case STERN -> t < 0.3f ? 1f : 1f - (t - 0.3f) / 0.7f;
            case RING -> (1f - t) * (1f - t) * 0.9f;
            case STAUB -> 0.75f * (1f - t);
            default -> 1f - t * t;
        };
    }

    /** Hersteller je Art (für die Fabric-Registrierung). */
    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Kind kind) {
        return (opt, level, x, y, z, vx, vy, vz, rnd) -> new FxParticle(level, x, y, z, vx, vy, vz, sprites, kind, rnd);
    }

    @SuppressWarnings("unused")
    private static Particle unused() { return null; }
}
