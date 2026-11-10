package de.kuby.rematch.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Uniform-Puffer eines Durchgangs: Minecraft legt sie unbeschreibbar an, die Mod tauscht ihren eigenen ein. */
@Mixin(PostPass.class)
public interface PostPassAccessor {
    @Accessor("customUniforms")
    Map<String, GpuBuffer> kuby$uniforms();

    @Mutable
    @Accessor("customUniforms")
    void kuby$setUniforms(Map<String, GpuBuffer> m);
}
