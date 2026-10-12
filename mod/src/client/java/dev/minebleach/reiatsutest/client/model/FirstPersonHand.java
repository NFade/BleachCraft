package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.client.ClientOptions;
import dev.minebleach.reiatsutest.core.combat.FpGeometry;
import dev.minebleach.reiatsutest.core.obj.DrawRig;
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
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
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

	/**
	 * Arm exactly as vanilla draws it for an empty hand (HeldItemRenderer#renderArmHoldingItem: same rotations and size),
	 * not scaled with the item. The matrix stack arrives in the hand frame of a held item (equip offset
	 * 0.56/-0.52/-0.72); the vanilla arm starts from 0.64/-0.6/-0.72, hence the small delta. Swing and equip animation are
	 * already inside the stack. {@code anchorPx} nudges the arm in its own pixels.
	 */
	private static void renderVanillaArm(MinecraftClient mc, AbstractClientPlayerEntity player, boolean left, ArmPose pose,
			MatrixStack m, VertexConsumerProvider vcp, int light) {
		float f = left ? -1f : 1f;
		m.push();
		m.translate(f * 0.08f, -0.08f, 0f);
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * 45f));
		m.translate(f * -1.0f, 3.6f, 3.5f);
		m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f * 120f));
		m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(200f));
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * -135f));
		m.translate(f * 5.6f, 0f, 0f);
		m.translate(f * pose.anchorPx()[0] / 16f, pose.anchorPx()[1] / 16f, pose.anchorPx()[2] / 16f);
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
		if (left) {
			renderer.renderLeftArm(m, vcp, light, player);
		} else {
			renderer.renderRightArm(m, vcp, light, player);
		}
		m.pop();
	}

	/** The vanilla arm of an empty hand in the hand frame of the current matrix stack (used for the hand that holds the scabbard). */
	public static void renderVanillaArmAt(MinecraftClient mc, AbstractClientPlayerEntity player, boolean left, MatrixStack m,
			VertexConsumerProvider vcp, int light) {
		renderVanillaArm(mc, player, left, new ArmPose(new float[3], 0f, new float[3], new float[3], 1f, true), m, vcp, light);
	}

	/**
	 * Hand frame lift of the sword and the scabbard (manifest or dev override) for a first person zanpakuto stack of the main
	 * hand with a scabbard rig, else null. The left hand mirrors x.
	 */
	public static Vector3f lift(ObjItemBakedModel obj, ItemStack stack, AbstractClientPlayerEntity player, boolean left) {
		if (obj.sayaMesh() == null || stack != player.getMainHandStack()) {
			return null;
		}
		float[] l = HeldPose.of(obj.manifest()).lift();
		return new Vector3f((left ? -1f : 1f) * l[0], l[1], l[2]);
	}

	public static void render(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
			MatrixStack m, VertexConsumerProvider vcp, int light) {
		if (!ClientOptions.showFirstPersonHand || !mode.isFirstPerson() || !(stack.getItem() instanceof ZanpakutoItem)
				|| !(entity instanceof AbstractClientPlayerEntity player) || player.isInvisible()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		BakedModel model = mc.getItemRenderer().getModel(stack, player.getWorld(), player, 0);
		if (!(model instanceof ObjItemBakedModel obj) || obj.armPose(stack) == null || !obj.drawsInHand(stack)) {
			return;
		}
		ArmPose pose = obj.armPose(stack);
		Transformation tr = model.getTransformation().getTransformation(mode);
		boolean left = mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
		float mx = left ? -1f : 1f; // the left hand mirrors x and the roll of the right-hand values
		boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;

		if (pose.vanilla()) {
			Vector3f lift = lift(obj, stack, player, left);
			if (lift != null && FpArms.hasHand()) {
				// fp2: the right fist is a point of the hilt (rigid with the sword and its draw rig), the arm comes from the shoulder (ArmIk)
				Matrix4f toHand = HandMath.modelToHand(tr, leftHanded);
				Matrix4f pose4 = FpMotion.currentPoseMatrix(obj, stack, player, tr, leftHanded, toHand);
				Matrix4f sword = FpArms.hand().mul(pose4).translate(lift).mul(toHand);
				FpArms.setSword(sword);
				float p = DrawTracker.effectiveProgress(player, stack);
				Matrix4f withRig = new Matrix4f(sword).mul(HandMath.toMatrix(obj.rig(left).at(p)));
				float s = FpMotion.num("arm.right_s", FpGeometry.RIGHT_S);
				Vector3f fist = withRig.transformPosition(new Vector3f(0.5f, 0.5f + s, 0.5f));
				Vector3f blade = withRig.transformDirection(new Vector3f(0f, 1f, 0f)).normalize();
				FpArms.draw(mc, player, left, fist, blade, (left ? -1f : 1f) * FpMotion.num("arm.right_roll", 0f), vcp, light);
			} else {
				renderVanillaArm(mc, player, left, pose, m, vcp, light);
			}
			return;
		}
		m.push();
		tr.apply(leftHanded, m); // now in the model frame of the item, origin = grip_hand
		m.translate(mx * pose.grip()[0], pose.grip()[1], pose.grip()[2]);
		Vector3f axis = new Vector3f(mx * pose.axis()[0], pose.axis()[1], pose.axis()[2]).normalize();
		Quaternionf q = new Quaternionf().rotationTo(0f, 1f, 0f, axis.x, axis.y, axis.z);
		q.premul(new Quaternionf().rotationAxis(mx * pose.roll() * DEG, axis.x, axis.y, axis.z));
		m.multiply(q);
		// the arm is NOT compensated for the display scale: pose.scale is relative to the sword, so fist and tsuka stay proportional
		m.scale(pose.scale(), pose.scale(), pose.scale());
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
