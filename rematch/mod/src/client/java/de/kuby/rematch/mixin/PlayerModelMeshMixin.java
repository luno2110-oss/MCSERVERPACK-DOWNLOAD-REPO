package de.kuby.rematch.mixin;

import de.kuby.rematch.client.anim.ModelSplit;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spielermodell mit geteilten Gliedern (nur das Haut-Modell, nicht die Rüstung). */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMeshMixin {
    @Inject(method = "createMesh", at = @At("RETURN"))
    private static void kuby$split(CubeDeformation deformation, boolean slim, CallbackInfoReturnable<MeshDefinition> cir) {
        if (deformation == CubeDeformation.NONE) ModelSplit.apply(cir.getReturnValue(), slim);
    }
}
