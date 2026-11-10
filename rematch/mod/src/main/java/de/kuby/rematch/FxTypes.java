package de.kuby.rematch;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Eigene Partikel (Effekte am Ball). Nur Grafik, der Server schickt sie nie. */
public final class FxTypes {
    public static final SimpleParticleType STERN = reg("stern");
    public static final SimpleParticleType RING = reg("ring");
    public static final SimpleParticleType FUNKE = reg("funke");
    public static final SimpleParticleType STAUB = reg("staub");
    public static final SimpleParticleType SPUR = reg("spur");

    private FxTypes() { }

    private static SimpleParticleType reg(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(KubyRematch.MOD_ID, name),
                FabricParticleTypes.simple(true));
    }

    public static void init() { }
}
