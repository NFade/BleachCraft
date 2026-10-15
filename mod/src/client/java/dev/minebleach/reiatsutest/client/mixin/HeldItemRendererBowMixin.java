package dev.minebleach.reiatsutest.client.mixin;

import dev.minebleach.reiatsutest.client.model.FpBow;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * R12: replaces the first person pass of the main hand for Uryu's bow of a right handed player (the bow in the left hand slot, the right arm
 * on the string, see {@link FpBow}). The vanilla pass (UseAction.BOW transform) is skipped only for that case; everything else is untouched.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererBowMixin {
	@Shadow
	public abstract void renderItem(net.minecraft.entity.LivingEntity entity, ItemStack stack, ModelTransformationMode mode,
			boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light);

	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void reiatsu$archerView(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
			ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (!FpBow.wants(player, hand, item)) {
			return;
		}
		FpBow.render(player, item, equipProgress, matrices, vertexConsumers, light,
				m -> renderItem(player, item, ModelTransformationMode.FIRST_PERSON_RIGHT_HAND, false, m, vertexConsumers, light));
		ci.cancel();
	}
}
