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
		// BC5b: strike, block stance and parry recoil move the whole hand (arm and sword) as one rigid body, before the arm is drawn
		reiatsu$fpMoved = false;
		if (mode.isFirstPerson() && stack.getItem() instanceof ZanpakutoItem && entity == client.player) {
			dev.minebleach.reiatsutest.client.model.FpArms.captureHand(matrices); // fp2: the hand frame before the pose is pushed
			reiatsu$fpMoved = dev.minebleach.reiatsutest.client.model.FpMotion.push(client.player, stack, mode, leftHanded, matrices);
		}
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
		if (!mode.isFirstPerson() && stack.getItem() instanceof ZanpakutoItem && entity.getMainHandStack() == stack
				&& MinecraftClient.getInstance().getItemRenderer().getModel(stack, entity.getWorld(), entity, 0)
						instanceof dev.minebleach.reiatsutest.client.model.ObjItemBakedModel obj3) {
			dev.minebleach.reiatsutest.client.model.ScabbardRenderer.captureHand(entity, matrices, leftHanded, obj3);
		}
		// BC5: third person, while a Better Combat animation drives the item bone: fixed correction so our model follows the
		// strike like a vanilla sword (fades in and out; nothing at rest, the resting hold is tuned by the display transform)
		reiatsu$bcPushed = false;
		if (!mode.isFirstPerson() && stack.getItem() instanceof ZanpakutoItem && entity.getMainHandStack() == stack
				&& leftHanded == (entity.getMainArm() == net.minecraft.util.Arm.LEFT)) {
			reiatsu$bcPushed = dev.minebleach.reiatsutest.client.model.BcItemPose.push(entity, leftHanded,
					MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false), matrices);
		}
		// plan G-glow round 2: while the Byakuya release effect is alive, hand the sword's model to view matrix to the world part of it
		if (stack.getItem() instanceof ZanpakutoItem && entity.getMainHandStack() == stack
				&& dev.minebleach.reiatsutest.client.fx.ShikaiDissolve.wantsPose(entity.getId())
				&& MinecraftClient.getInstance().getItemRenderer().getModel(stack, entity.getWorld(), entity, 0)
						instanceof dev.minebleach.reiatsutest.client.model.ObjItemBakedModel dissolveModel) {
			dissolveModel.captureDissolvePose(entity, mode, leftHanded, matrices);
		}
	}

	@org.spongepowered.asm.mixin.Unique
	private boolean reiatsu$fpMoved;

	@org.spongepowered.asm.mixin.Unique
	private boolean reiatsu$lifted;

	@org.spongepowered.asm.mixin.Unique
	private boolean reiatsu$bcPushed;

	/** B4 step 2: the scabbard in the other hand, drawn with the hands, before the buffer is flushed (no item, no inventory slot). */
	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;draw()V"))
	private void reiatsu$scabbard(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
			net.minecraft.client.network.ClientPlayerEntity player, int light, CallbackInfo ci) {
		float equip = net.minecraft.util.math.MathHelper.lerp(tickDelta, prevEquipProgressMainHand, equipProgressMainHand);
		dev.minebleach.reiatsutest.client.model.ScabbardRenderer.renderFirstPerson(matrices, vertexConsumers, player, light, mainHand, equip);
		dev.minebleach.reiatsutest.client.model.FpMotion.renderLeftGuard(matrices, vertexConsumers, player, light, mainHand, equip);
	}

	/** BC5b: once per frame, before the hands are drawn: advances the clocks of the strike, the block stance and the recoils. */
	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At("HEAD"))
	private void reiatsu$fpFrame(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
			net.minecraft.client.network.ClientPlayerEntity player, int light, CallbackInfo ci) {
		dev.minebleach.reiatsutest.client.model.FpArms.begin(matrices); // fp2: base space of the hand pass (camera with bobbing and sway)
		dev.minebleach.reiatsutest.client.model.FpMotion.frame(player, tickDelta);
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
		if (reiatsu$bcPushed) {
			matrices.pop();
			reiatsu$bcPushed = false;
		}
		if (reiatsu$fpMoved) {
			matrices.pop();
			reiatsu$fpMoved = false;
		}
		DrawTracker.endRender();
	}
}
