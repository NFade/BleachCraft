package dev.minebleach.reiatsutest.client.mixin;

import dev.minebleach.reiatsutest.client.model.SwingPose;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * T4: replaces the vanilla arm swing of a drawn zanpakuto in the main hand (players, own and other) with the katana slash of
 * {@link SwingPose}. Same inputs as vanilla ({@code handSwingProgress}, the arm that swings), the same body pivot handling;
 * only the angles differ. Everything else (sheathed sword, other items, off hand swings) stays vanilla.
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin<T extends LivingEntity> {
	@Shadow
	@org.spongepowered.asm.mixin.Final
	public ModelPart body;

	@Shadow
	@org.spongepowered.asm.mixin.Final
	public ModelPart rightArm;

	@Shadow
	@org.spongepowered.asm.mixin.Final
	public ModelPart leftArm;

	@Shadow
	protected abstract ModelPart getArm(Arm arm);

	@Inject(method = "animateArms", at = @At("HEAD"), cancellable = true)
	private void reiatsu$katanaSlash(T entity, float animationProgress, CallbackInfo ci) {
		float s = ((EntityModel<?>) (Object) this).handSwingProgress;
		if (s <= 0f || !SwingPose.active(entity)) {
			return;
		}
		Arm arm = entity.getMainArm();
		float sign = arm == Arm.RIGHT ? 1f : -1f;
		ModelPart part = getArm(arm);
		float[] a = SwingPose.arm(s, part.pitch, part.yaw, part.roll, sign); // from the walk / idle angles of this frame
		// the pivots of vanilla's body twist
		body.yaw = SwingPose.body(s, sign);
		float by = body.yaw;
		rightArm.pivotZ = MathHelper.sin(by) * 5.0F;
		rightArm.pivotX = -MathHelper.cos(by) * 5.0F;
		leftArm.pivotZ = -MathHelper.sin(by) * 5.0F;
		leftArm.pivotX = MathHelper.cos(by) * 5.0F;
		rightArm.yaw += by;
		leftArm.yaw += by;
		leftArm.pitch += by; // vanilla: the left arm follows the twist a little
		part.pitch = a[0];
		part.yaw = a[1] + by;
		part.roll = a[2];
		ci.cancel();
	}
}
