package de.kuby.rematch.mixin;

import de.kuby.rematch.anim.Animator;
import de.kuby.rematch.anim.PartSolver;
import de.kuby.rematch.client.anim.ClientAnims;
import de.kuby.rematch.client.anim.ModelSplit;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Legt die Pose des Animators über die normale Minecraft-Pose (weich nach Gewicht). */
@Mixin(PlayerModel.class)
public abstract class PlayerModelAnimMixin {
    @Unique private ModelPart[] kuby$parts;
    @Unique private ModelPart[] kuby$lowerLayers;

    @Unique
    private ModelPart[] kuby$parts() {
        if (kuby$parts == null) {
            HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
            ModelPart[] p = new ModelPart[PartSolver.N];
            p[PartSolver.Part.HEAD.ordinal()] = m.head;
            p[PartSolver.Part.BODY.ordinal()] = m.body;
            p[PartSolver.Part.BELLY.ordinal()] = child(m.body);
            p[PartSolver.Part.RIGHT_ARM.ordinal()] = m.rightArm;
            p[PartSolver.Part.RIGHT_FOREARM.ordinal()] = child(m.rightArm);
            p[PartSolver.Part.LEFT_ARM.ordinal()] = m.leftArm;
            p[PartSolver.Part.LEFT_FOREARM.ordinal()] = child(m.leftArm);
            p[PartSolver.Part.RIGHT_LEG.ordinal()] = m.rightLeg;
            p[PartSolver.Part.RIGHT_SHIN.ordinal()] = child(m.rightLeg);
            p[PartSolver.Part.LEFT_LEG.ordinal()] = m.leftLeg;
            p[PartSolver.Part.LEFT_SHIN.ordinal()] = child(m.leftLeg);
            kuby$parts = p;
        }
        return kuby$parts;
    }

    @Unique
    private static ModelPart child(ModelPart p) {
        return p.hasChild(ModelSplit.LOWER) ? p.getChild(ModelSplit.LOWER) : null;
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void kuby$animate(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel self = (PlayerModel) (Object) this;
        ModelPart[] parts = kuby$parts();
        // Untere Hälften der zweiten Hautschicht wie die oberen ein-/ausblenden
        layer(parts[PartSolver.Part.BELLY.ordinal()], self.jacket.visible);
        layer(parts[PartSolver.Part.RIGHT_FOREARM.ordinal()], self.rightSleeve.visible);
        layer(parts[PartSolver.Part.LEFT_FOREARM.ordinal()], self.leftSleeve.visible);
        layer(parts[PartSolver.Part.RIGHT_SHIN.ordinal()], self.rightPants.visible);
        layer(parts[PartSolver.Part.LEFT_SHIN.ordinal()], self.leftPants.visible);

        Animator a = ClientAnims.animatorFor(state);
        if (a == null || !a.active()) return;
        double w = a.weight();
        double[][] o = ClientAnims.solver().solve(a.pose());
        // Schlagen oder Gegenstand benutzen: Arme bleiben bei Minecraft (außer während einer Aktion)
        boolean armsVanilla = (state.attackTime > 0 || state.isUsingItem) && a.action() == null;
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = parts[i];
            if (p == null) continue;
            if (armsVanilla && i >= PartSolver.Part.RIGHT_ARM.ordinal() && i <= PartSolver.Part.LEFT_FOREARM.ordinal()) continue;
            double[] v = o[i];
            p.x = (float) (p.x + (v[0] - p.x) * w);
            p.y = (float) (p.y + (v[1] - p.y) * w);
            p.z = (float) (p.z + (v[2] - p.z) * w);
            p.xRot = (float) PartSolver.lerpAngle(p.xRot, v[3], w);
            p.yRot = (float) PartSolver.lerpAngle(p.yRot, v[4], w);
            p.zRot = (float) PartSolver.lerpAngle(p.zRot, v[5], w);
            p.xScale = (float) (p.xScale + (v[6] - p.xScale) * w);
            p.yScale = (float) (p.yScale + (v[7] - p.yScale) * w);
            p.zScale = (float) (p.zScale + (v[8] - p.zScale) * w);
        }
    }

    @Unique
    private static void layer(ModelPart lower, boolean visible) {
        if (lower != null && lower.hasChild(ModelSplit.LOWER_LAYER)) lower.getChild(ModelSplit.LOWER_LAYER).visible = visible;
    }
}
