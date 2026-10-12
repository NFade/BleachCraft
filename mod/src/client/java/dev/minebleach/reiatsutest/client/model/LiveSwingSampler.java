package dev.minebleach.reiatsutest.client.model;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.minebleach.reiatsutest.core.combat.LiveSwing;
import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * Reads the live bones of the local player from the playerAnimator stack (round 6, public API only: {@code AnimationStack.get3DTransform}) and
 * hands them to {@link LiveSwing}. Better Combat plays its strike animation on the player whatever the first person mode says, so while a strike
 * runs the stack holds the third person strike: the same value the player model gets when it is drawn. The defaults handed in are the rest values
 * of the model part (zero rotation, the default pivots), so a layer that does not animate a bone gives them back unchanged, and a bone that
 * is animated gives its absolute value (the stack passes the value through the layers from the bottom).
 */
final class LiveSwingSampler {
	private LiveSwingSampler() {
	}

	/** The bones of the main arm chain of {@code p} (its right or left arm), or null when the stack cannot be read. */
	static LiveSwing.Bones sample(AbstractClientPlayerEntity p, boolean leftArm, float tickDelta) {
		try {
			AnimationStack stack = PlayerAnimationAccess.getPlayerAnimLayer(p);
			if (stack == null) {
				return null;
			}
			// the layers keep the tick delta of the last render they were set up for; the hand pass has not set it up yet this frame
			stack.setupAnim(tickDelta);
			String arm = leftArm ? "leftArm" : "rightArm";
			String item = leftArm ? "leftItem" : "rightItem";
			float[] torsoRot = rot(stack, "torso", tickDelta);
			float[] bodyRot = rot(stack, "body", tickDelta);
			float[] torsoPos = pos(stack, "torso", tickDelta, new float[3]);
			float[] pivot = pos(stack, arm, tickDelta, new float[] {leftArm ? 5f : -5f, 2f, 0f});
			float[] armRot = rot(stack, arm, tickDelta);
			Vec3f bend = stack.get3DTransform(arm, TransformType.BEND, tickDelta, new Vec3f(0f, 0f, 0f));
			float[] itemRot = rot(stack, item, tickDelta);
			if (!finite(torsoRot) || !finite(bodyRot) || !finite(torsoPos) || !finite(pivot) || !finite(armRot) || !finite(itemRot)) {
				return null;
			}
			float bendAxis = bend == null ? 0f : bend.getX();
			float bendAngle = bend == null ? 0f : bend.getY();
			if (!Float.isFinite(bendAxis) || !Float.isFinite(bendAngle)) {
				bendAxis = 0f;
				bendAngle = 0f;
			}
			return new LiveSwing.Bones(torsoRot, bodyRot, torsoPos, pivot, armRot, bendAxis, bendAngle, itemRot);
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

	private static float[] rot(AnimationStack s, String bone, float td) {
		Vec3f v = s.get3DTransform(bone, TransformType.ROTATION, td, new Vec3f(0f, 0f, 0f));
		return v == null ? new float[3] : new float[] {v.getX(), v.getY(), v.getZ()};
	}

	private static float[] pos(AnimationStack s, String bone, float td, float[] def) {
		Vec3f v = s.get3DTransform(bone, TransformType.POSITION, td, new Vec3f(def[0], def[1], def[2]));
		return v == null ? def : new float[] {v.getX(), v.getY(), v.getZ()};
	}

	private static boolean finite(float[] a) {
		for (float f : a) {
			if (!Float.isFinite(f)) {
				return false;
			}
		}
		return true;
	}

	/** True while a playerAnimator layer (the Better Combat strike) drives the item bone of the main arm. */
	static boolean itemDriven(AbstractClientPlayerEntity p, boolean leftArm, float tickDelta) {
		return BcItemPose.drivenByAnimation(p, leftArm, tickDelta);
	}
}
