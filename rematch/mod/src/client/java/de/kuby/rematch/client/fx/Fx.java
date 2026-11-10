package de.kuby.rematch.client.fx;

import de.kuby.rematch.FxTypes;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Partikel-Effekte am Ball: Kontakt (Stern, Schockwellen-Ringe, Funken, Gras, Staub) und Ballspur. */
public final class Fx {
    private static final Random R = new Random();

    private Fx() { }

    public static void registerParticles() {
        ParticleFactoryRegistry r = ParticleFactoryRegistry.getInstance();
        r.register(FxTypes.STERN, s -> FxParticle.provider(s, FxParticle.Kind.STERN));
        r.register(FxTypes.RING, s -> FxParticle.provider(s, FxParticle.Kind.RING));
        r.register(FxTypes.FUNKE, s -> FxParticle.provider(s, FxParticle.Kind.FUNKE));
        r.register(FxTypes.STAUB, s -> FxParticle.provider(s, FxParticle.Kind.STAUB));
        r.register(FxTypes.SPUR, s -> FxParticle.provider(s, FxParticle.Kind.SPUR));
    }

    /** Ballkontakt; dir = Schussrichtung (waagerecht, Länge 1). */
    public static void contact(Minecraft mc, int entityId, Vec3 p, Vec3 dir, boolean hard) {
        var lv = mc.level;
        if (lv == null) return;
        if (hard) {
            lv.addParticle(FxTypes.STERN, true, true, p.x, p.y, p.z, 0, 0, 0);
            lv.addParticle(FxTypes.RING, true, true, p.x, p.y, p.z, 0, 0, 0);
            lv.addParticle(FxTypes.RING, true, true, p.x + dir.x * 0.4, p.y + 0.05, p.z + dir.z * 0.4, dir.x * 0.25, 0, dir.z * 0.25);
        }
        int sparks = hard ? 22 : 6;
        for (int i = 0; i < sparks; i++) {
            // Kegel in Schussrichtung, etwas nach oben
            double sp = (hard ? 0.55 : 0.3) * (0.5 + R.nextDouble());
            double sx = dir.x + (R.nextDouble() - 0.5) * 1.1, sy = 0.25 + R.nextDouble() * 0.7, sz = dir.z + (R.nextDouble() - 0.5) * 1.1;
            double n = Math.sqrt(sx * sx + sy * sy + sz * sz);
            lv.addParticle(FxTypes.FUNKE, true, true, p.x, p.y, p.z, sx / n * sp, sy / n * sp, sz / n * sp);
        }
        // Boden: Grasbrocken und Staub
        BlockPos ground = BlockPos.containing(p.x, p.y - 0.3, p.z);
        BlockState bs = lv.getBlockState(ground);
        if (bs.isAir()) { ground = ground.below(); bs = lv.getBlockState(ground); }
        double gy = ground.getY() + 1.02;
        if (!bs.isAir()) {
            BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK, bs);
            for (int i = 0; i < (hard ? 16 : 5); i++)
                lv.addParticle(block, p.x + (R.nextDouble() - 0.5) * 0.4, gy, p.z + (R.nextDouble() - 0.5) * 0.4,
                        dir.x * 0.15 + (R.nextDouble() - 0.5) * 0.3, 0.25 + R.nextDouble() * 0.35, dir.z * 0.15 + (R.nextDouble() - 0.5) * 0.3);
        }
        for (int i = 0; i < (hard ? 7 : 3); i++) {
            double a = R.nextDouble() * Math.PI * 2, v = 0.06 + R.nextDouble() * 0.08;
            lv.addParticle(FxTypes.STAUB, true, true, p.x + Math.cos(a) * 0.2, gy + 0.05, p.z + Math.sin(a) * 0.2,
                    Math.cos(a) * v - dir.x * 0.03, 0.015, Math.sin(a) * v - dir.z * 0.03);
        }
    }

    /** Leuchtspur zwischen zwei Ballpositionen (dicht genug, dass sie wie ein Streifen wirkt). */
    public static void trail(Minecraft mc, Vec3 from, Vec3 to) {
        if (mc.level == null) return;
        double d = from.distanceTo(to);
        int n = (int) Math.min(24, Math.ceil(d / 0.05));
        for (int i = 0; i < n; i++) {
            Vec3 q = from.lerp(to, (i + 1.0) / n);
            mc.level.addParticle(FxTypes.SPUR, true, true, q.x, q.y, q.z, 0, 0, 0);
        }
    }
}
