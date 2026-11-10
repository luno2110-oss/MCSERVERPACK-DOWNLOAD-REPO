package de.kuby.rematch.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import de.kuby.rematch.client.fx.ScreenFx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bildeffekte direkt nach der Welt, vor der Oberfläche (HUD bleibt scharf). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private CrossFrameResourcePool resourcePool;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER))
    private void kuby$screenFx(DeltaTracker delta, boolean tick, CallbackInfo ci) {
        ScreenFx.render(resourcePool);
    }
}
