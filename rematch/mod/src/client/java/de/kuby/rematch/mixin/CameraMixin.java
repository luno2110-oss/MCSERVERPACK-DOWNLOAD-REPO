package de.kuby.rematch.mixin;

import de.kuby.rematch.client.anim.CameraShake;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kamera-Wackeln beim harten Ballkontakt (nur Blickrichtung, Position bleibt). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private float xRot;
    @Shadow private float yRot;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void kuby$shake(Level level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
        float[] o = CameraShake.offset();
        if (o != null) setRotation(yRot + o[0], xRot + o[1]);
    }
}
