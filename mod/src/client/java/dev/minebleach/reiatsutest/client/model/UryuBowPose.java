package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.registry.UryuCrossItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Quaternionf;

/**
 * Third person pose of Uryu's bow. The display transform of {@code uryu_cross_display.json} is solved for the idle arm of a held item
 * (vanilla {@code ArmPose.ITEM}: pitch -18 degrees): the bow stands upright at the side, the arrow axis points forward. While the bow is
 * drawn vanilla switches the arm to {@code ArmPose.BOW_AND_ARROW} (pitch -90 degrees plus the look pitch, yaw -0.1), which turns the item
 * with the arm; this class pre-rotates the item about its grip by {@code R_draw * R_idle^-1} so the bow stays upright with its arrow axis
 * along the arm. Both rotations are the XYZ Euler angles of the numbers below (degrees, as the display JSON), solved in
 * design/results/uryu_u4_u5.md with the vanilla frames ({@code HeldItemFeatureRenderer}: X -90, Y 180; {@code ModelPart.rotate}).
 * The display translation of the third person entries is zero, so the rotation about the hand origin is a rotation about the grip.
 * R12: right handed players hold the bow in the LEFT hand (see {@link #bowInLeftHand}); the correction is the mirrored one, because the left hand mirrors the display. Left handed players: the plain display, as before.
 * The U-FX 11 pose layer ({@link UryuBowMotion}) drives the arm of the players whose bow is NOT in the left-hand-by-default arrangement (left handed players, mirrored) with its own animations and puts the matching correction;
 * right handed players use the R12 draw clip ({@code TpBowLayer}) and the constant correction below. The constants are also the fallback when {@code pose_override.json uryu_pose.enabled} is 0 or an animation file is missing.
 * Round 2: the display scales are 1.8 (first person) and 1.4 (third person); the nocked arrow, the string and the pull are baked in the same
 * model space, so they scale with the bow, and the pre-rotation about the grip does not depend on the scale.
 */
public final class UryuBowPose {
	/** Display rotation of the idle pose (the same numbers as {@code thirdperson_righthand} in the display JSON). */
	private static final float[] IDLE = {-118.0f, 0.0f, -180.0f};
	/** The rotation that gives the same bow orientation in the BOW_AND_ARROW arm pose. */
	private static final float[] DRAW = {-180.0f, 5.73f, -180.0f};

	private UryuBowPose() {
	}

	private static Quaternionf euler(float[] d) {
		float r = (float) (Math.PI / 180.0);
		return new Quaternionf().rotationXYZ(d[0] * r, d[1] * r, d[2] * r);
	}

	/** The mirrored rotations (the left hand mirrors the display: Euler y and z change sign) for the bow in the left hand. */
	private static final float[] IDLE_L = {IDLE[0], -IDLE[1], -IDLE[2]};
	private static final float[] DRAW_L = {DRAW[0], -DRAW[1], -DRAW[2]};

	private static Quaternionf correctionLeft;

	/**
	 * True when this entity holds the bow in its LEFT hand: the player is right handed (main arm RIGHT), then the bow arm is the
	 * left one and the right hand draws the string (clip {@code uryu_bow_draw}). Left handed players keep the bow in the main (left) hand as before.
	 */
	public static boolean bowInLeftHand(LivingEntity entity) {
		return entity.getMainArm() == Arm.RIGHT;
	}

	public static boolean isBowStack(ItemStack stack) {
		return UryuCrossItem.isBow(stack);
	}

	/** True when the third person pose of this entity is the drawn bow pose. */
	public static boolean drawing(LivingEntity entity, ItemStack stack) {
		return UryuCrossItem.isBow(stack) && entity.getMainHandStack() == stack && entity.isUsingItem() && entity.getActiveHand() == Hand.MAIN_HAND;
	}

	/** Pushes the correction (caller pops when true is returned). {@code leftHanded} = the item is drawn on the left arm (mirrored display). */
	public static boolean push(LivingEntity entity, ItemStack stack, boolean leftHanded, MatrixStack matrices) {
		if (stack.getItem() instanceof UryuCrossItem && !isBowStack(stack)) {
			// R14: the sealed cross: the arm is lifted by UryuCrossHold, the pendant is turned back so the chain hangs down
			Quaternionf q = entity.getMainHandStack() == stack ? UryuCrossHold.itemCorrection(entity, leftHanded) : null;
			if (q == null) {
				return false;
			}
			matrices.push();
			matrices.multiply(q);
			return true;
		}
		if (!bowInLeftHand(entity)) {
			// U-FX 11: while the pose layer drives the arm (idle, draw, release, the ability poses) the bow is kept upright in the moving hand, mirrored for a left hander
			if (entity instanceof AbstractClientPlayerEntity player && UryuCrossItem.isBow(stack) && entity.getMainHandStack() == stack) {
				Quaternionf q = UryuBowMotion.bowCorrection(player, leftHanded);
				if (q != null) {
					matrices.push();
					matrices.multiply(q);
					return true;
				}
			}
			return false; // left handed players: the plain display
		}
		if (leftHanded && entity instanceof AbstractClientPlayerEntity player && isBowStack(stack) && entity.getMainHandStack() == stack) {
			// R14: an ability gesture of the right hander (the mirrored pose layer drives the bow arm): the bow is kept upright in the moving hand
			Quaternionf q = UryuBowMotion.bowCorrection(player, true);
			if (q != null) {
				matrices.push();
				matrices.multiply(q);
				return true;
			}
		}
		if (!leftHanded || !drawing(entity, stack)) {
			return false; // right hand: the plain display, as before R12
		}
		if (correctionLeft == null) {
			correctionLeft = euler(DRAW_L).mul(euler(IDLE_L).conjugate(new Quaternionf()));
		}
		matrices.push();
		matrices.multiply(correctionLeft);
		return true;
	}
}
