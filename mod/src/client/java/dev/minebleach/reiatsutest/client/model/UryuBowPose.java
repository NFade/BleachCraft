package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.registry.UryuCrossItem;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
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
 * Right handed players only (the left hand mirrors the display; there the plain display is used).
 */
public final class UryuBowPose {
	/** Display rotation of the idle pose (the same numbers as {@code thirdperson_righthand} in the display JSON). */
	private static final float[] IDLE = {-118.0f, 0.0f, -180.0f};
	/** The rotation that gives the same bow orientation in the BOW_AND_ARROW arm pose. */
	private static final float[] DRAW = {-180.0f, 5.73f, -180.0f};

	private static Quaternionf correction;

	private UryuBowPose() {
	}

	private static Quaternionf euler(float[] d) {
		float r = (float) (Math.PI / 180.0);
		return new Quaternionf().rotationXYZ(d[0] * r, d[1] * r, d[2] * r);
	}

	/** True when the third person pose of this entity is the drawn bow pose. */
	public static boolean drawing(LivingEntity entity, ItemStack stack) {
		return UryuCrossItem.isBow(stack) && entity.getMainHandStack() == stack && entity.isUsingItem() && entity.getActiveHand() == Hand.MAIN_HAND;
	}

	/** Pushes the correction (caller pops when true is returned). */
	public static boolean push(LivingEntity entity, ItemStack stack, boolean leftHanded, MatrixStack matrices) {
		if (leftHanded || !drawing(entity, stack)) {
			return false;
		}
		if (correction == null) {
			correction = euler(DRAW).mul(euler(IDLE).conjugate(new Quaternionf()));
		}
		matrices.push();
		matrices.multiply(correction);
		return true;
	}
}
