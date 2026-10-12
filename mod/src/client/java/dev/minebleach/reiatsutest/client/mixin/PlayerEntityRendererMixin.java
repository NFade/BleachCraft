package dev.minebleach.reiatsutest.client.mixin;

import dev.minebleach.reiatsutest.client.model.FpArms;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fp2: the first person arm model must stand straight when the mod places it with {@code ArmIk}. {@code PlayerEntityRenderer#renderArm} asks the
 * player for the arm pose (block, held item, idle sway) and applies it to the model part before drawing; only the pitch is reset afterwards, so a
 * blocking player drew its arm turned by 30 degrees about the shoulder (the fist moved 0.3 blocks sideways of the hilt). While {@link FpArms} draws, the
 * yaw and roll of the arm and its sleeve are cleared right before the first {@code ModelPart#render}.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
	@Inject(method = "renderArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/model/ModelPart;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelPart;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V", ordinal = 0))
	private void reiatsu$straightArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
			ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
		if (FpArms.drawing()) {
			arm.yaw = 0f;
			arm.roll = 0f;
			arm.pitch = 0f;
			sleeve.yaw = 0f;
			sleeve.roll = 0f;
			sleeve.pitch = 0f;
		}
	}
}
