package dev.minebleach.reiatsutest.client.mixin;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.minebleach.reiatsutest.client.model.TpPoseDriver;
import dev.minebleach.reiatsutest.client.model.TpPoseLayer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * round6 tp_block: the third person pose of a blocking zanpakuto holder gets the last word on the arms. playerAnimator writes the
 * layer values inside {@code PlayerEntityModel#setAngles} (its own injection at the first {@code copyTransform}, after the vanilla
 * arm poses including {@code ArmPose.BLOCK}); HEAD here forgets what {@code TpPoseLayer} wrote in the previous pass and RETURN checks
 * the model against it ({@link TpPoseDriver#finishPass}: nothing to do when the layer worked, otherwise the pose is applied or restored
 * and the sleeves follow). Not in the first person pass (the first person arms belong to {@code FirstPersonHand}).
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
	private void reiatsu$tpBegin(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		if (entity instanceof AbstractClientPlayerEntity p) {
			TpPoseLayer.beginPass(p.getId());
		}
	}

	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
	private void reiatsu$tpFinish(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		if (entity instanceof AbstractClientPlayerEntity p && !FirstPersonMode.isFirstPersonPass()) {
			TpPoseDriver.finishPass((PlayerEntityModel<?>) (Object) this, p);
		}
	}
}
