package dev.minebleach.reiatsutest.client.mixin;

import dev.minebleach.reiatsutest.client.model.DrawTracker;
import dev.minebleach.reiatsutest.client.model.FirstPersonHand;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the player arm before one of our zanpakuto items in first person (vanilla draws no arm for held items). */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@Shadow
	@org.spongepowered.asm.mixin.Final
	private MinecraftClient client;

	@Shadow
	private ItemStack mainHand;

	@Shadow
	private float equipProgressMainHand;

	@Shadow
	private float prevEquipProgressMainHand;

	/**
	 * A release state change (release_state component) makes the stack unequal to the cached one, which vanilla answers with
	 * the lower-and-raise equip animation: it would hide the draw animation. For the same zanpakuto item the cached stack is
	 * simply replaced.
	 */
	@Inject(method = "updateHeldItems", at = @At("HEAD"))
	private void reiatsu$noReequipOnStateChange(CallbackInfo ci) {
		if (client.player == null) {
			return;
		}
		ItemStack cur = client.player.getMainHandStack();
		if (cur.getItem() instanceof ZanpakutoItem && mainHand.getItem() == cur.getItem()) {
			mainHand = cur;
		}
	}

	@Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"))
	private void reiatsu$firstPersonHand(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		DrawTracker.beginRender(entity);
		dev.minebleach.reiatsutest.client.model.HeldPose.poll();
		FirstPersonHand.render(entity, stack, mode, leftHanded, matrices, vertexConsumers, light);
		// B4 polish: the sword is raised in the hand (the scabbard and the stow pose share the lift), after the arm was drawn
		reiatsu$lifted = false;
		if (mode.isFirstPerson() && stack.getItem() instanceof ZanpakutoItem
				&& entity instanceof net.minecraft.client.network.AbstractClientPlayerEntity player
				&& MinecraftClient.getInstance().getItemRenderer().getModel(stack, player.getWorld(), player, 0)
						instanceof dev.minebleach.reiatsutest.client.model.ObjItemBakedModel obj) {
			org.joml.Vector3f lift = FirstPersonHand.lift(obj, stack, player, mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND);
			if (lift != null) {
				matrices.push();
				matrices.translate(lift.x, lift.y, lift.z);
				reiatsu$lifted = true;
			}
		}
	}

	@org.spongepowered.asm.mixin.Unique
	private boolean reiatsu$lifted;

	/** B4 step 2: the scabbard in the other hand, drawn with the hands, before the buffer is flushed (no item, no inventory slot). */
	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;draw()V"))
	private void reiatsu$scabbard(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
			net.minecraft.client.network.ClientPlayerEntity player, int light, CallbackInfo ci) {
		float equip = net.minecraft.util.math.MathHelper.lerp(tickDelta, prevEquipProgressMainHand, equipProgressMainHand);
		dev.minebleach.reiatsutest.client.model.ScabbardRenderer.renderFirstPerson(matrices, vertexConsumers, player, light, mainHand, equip);
	}

	/** The item model asks the DrawTracker which entity it is drawn for (draw / sheathe animation of the sealed sword). */
	@Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("RETURN"))
	private void reiatsu$endRender(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (reiatsu$lifted) {
			matrices.pop();
			reiatsu$lifted = false;
		}
		DrawTracker.endRender();
	}
}
