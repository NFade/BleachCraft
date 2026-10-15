package dev.minebleach.reiatsutest.client.mixin;

import dev.minebleach.reiatsutest.client.model.UryuBowPose;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third person: Uryu's bow of a right handed player is drawn in the LEFT hand (the right hand pulls the string, see {@link UryuBowPose#bowInLeftHand}).
 * The held item of the main arm is handed to the other arm: this redirects the call once (the second call has {@code arm = LEFT} and passes through).
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
	@Shadow
	protected abstract void renderItem(LivingEntity entity, ItemStack stack, ModelTransformationMode transformationMode, Arm arm, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light);

	@Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
	private void reiatsu$bowInTheLeftHand(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (arm == Arm.RIGHT && UryuBowPose.bowInLeftHand(entity) && entity.getMainHandStack() == stack && UryuBowPose.isBowStack(stack)) {
			renderItem(entity, stack, ModelTransformationMode.THIRD_PERSON_LEFT_HAND, Arm.LEFT, matrices, vertexConsumers, light);
			ci.cancel();
		}
	}
}
