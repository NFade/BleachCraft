package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.client.ClientOptions;
import dev.minebleach.reiatsutest.core.obj.ItemManifest.ArmPose;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First person arm for the zanpakuto items. Called from the {@code HeldItemRenderer#renderItem} mixin with the matrix
 * stack in the hand frame (swing and equip animation already applied), BEFORE the item itself is drawn. The arm is
 * positioned in the display frame of the item, so it follows the sword exactly and any display tweak moves both.
 */
public final class FirstPersonHand {
	private static final float DEG = (float) (Math.PI / 180.0);

	private FirstPersonHand() {
	}

	public static void render(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
			MatrixStack m, VertexConsumerProvider vcp, int light) {
		if (!ClientOptions.showFirstPersonHand || !mode.isFirstPerson() || !(stack.getItem() instanceof ZanpakutoItem)
				|| !(entity instanceof AbstractClientPlayerEntity player) || player.isInvisible()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		BakedModel model = mc.getItemRenderer().getModel(stack, player.getWorld(), player, 0);
		if (!(model instanceof ObjItemBakedModel obj) || obj.armPose() == null || !obj.drawsInHand(stack)) {
			return;
		}
		ArmPose pose = obj.armPose();
		Transformation tr = model.getTransformation().getTransformation(mode);
		boolean left = mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
		float mx = left ? -1f : 1f; // the left hand mirrors x and the roll of the right-hand values
		boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;

		m.push();
		tr.apply(leftHanded, m); // now in the model frame of the item, origin = grip_hand
		m.translate(mx * pose.grip()[0], pose.grip()[1], pose.grip()[2]);
		Vector3f axis = new Vector3f(mx * pose.axis()[0], pose.axis()[1], pose.axis()[2]).normalize();
		Quaternionf q = new Quaternionf().rotationTo(0f, 1f, 0f, axis.x, axis.y, axis.z);
		q.premul(new Quaternionf().rotationAxis(mx * pose.roll() * DEG, axis.x, axis.y, axis.z));
		m.multiply(q);
		m.scale(pose.scale() / tr.scale.x(), pose.scale() / tr.scale.y(), pose.scale() / tr.scale.z()); // the arm keeps its real size times pose.scale
		// arm part: pivot (+-5, 2 or 2.5, 0) px; cuboid x centre -1 / -0.5 (right wide/slim), +1 / +0.5 (left); fist centre 8 px down
		float pivotX = left ? 5f : -5f;
		float pivotY = slim ? 2.5f : 2.0f;
		float cx = (left ? 1f : -1f) * (slim ? 0.5f : 1f);
		m.translate(-(pivotX + cx + mx * pose.anchorPx()[0]) / 16f, -(pivotY + 8f + pose.anchorPx()[1]) / 16f,
				-(pose.anchorPx()[2]) / 16f);
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
		if (left) {
			renderer.renderLeftArm(m, vcp, light, player);
		} else {
			renderer.renderRightArm(m, vcp, light, player);
		}
		m.pop();
	}
}
