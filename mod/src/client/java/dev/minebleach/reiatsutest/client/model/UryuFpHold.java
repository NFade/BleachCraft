package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.client.fx.FxTune;
import dev.minebleach.reiatsutest.registry.UryuCrossItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Quaternionf;

/**
 * First person hold of Uryu's bow: the bow hand (arm and bow as one body) is moved from the vanilla bottom right corner to a natural archer's hold, a little
 * right of the centre, raised, pushed forward, the bow tipped inward and turned to the crosshair so the limbs span the view and the string faces the player.
 * The gesture rows of the draw ({@link UryuBowMotion}) work on top of it. The string / free hand follows the shift ({@link #shift}).
 * UNVERIFIED in the game; tunables {@code uryu.fp.hold*} in the FxTune file (hand frame, blocks / degrees, right hander layout, mirrored for the left hand).
 */
public final class UryuFpHold {
	private static final float RAD = (float) (Math.PI / 180.0);
	/** Fist of the empty hand in the hand frame (rotation pivot), see {@code FirstPersonHand}. */
	private static final double FIST_X = 0.134;
	private static final double FIST_Y = -0.018;
	private static final double FIST_Z = -0.316;

	private UryuFpHold() {
	}

	private static boolean on() {
		return FxTune.d("uryu.fp.hold", 1.0) > 0.5;
	}

	/** Shift of the bow hand in the hand frame for {@code side} (+1 right hand, -1 left): x, y, z, blocks. */
	public static double[] shift(int side) {
		if (!on()) {
			return new double[3];
		}
		return new double[] {side * FxTune.d("uryu.fp.holdX", -0.12), FxTune.d("uryu.fp.holdY", 0.07), FxTune.d("uryu.fp.holdZ", 0.0)};
	}

	/** Pushes the hold onto the matrix stack at the head of the held item render (the caller pops when true is returned). */
	public static boolean push(ClientPlayerEntity player, ItemStack stack, boolean leftHanded, MatrixStack m) {
		if (!on() || !UryuCrossItem.isBow(stack) || stack != player.getMainHandStack() || !UryuBowMotion.fpOn()) {
			return false;
		}
		int side = leftHanded ? -1 : 1;
		double[] s = shift(side);
		float rx = (float) FxTune.d("uryu.fp.holdRx", -4.0);
		float ry = (float) FxTune.d("uryu.fp.holdRy", 60.0);
		float rz = (float) FxTune.d("uryu.fp.holdRz", -13.0);
		m.push();
		m.translate(s[0], s[1], s[2]);
		m.translate(side * FIST_X, FIST_Y, FIST_Z);
		m.multiply(new Quaternionf().rotationXYZ(rx * RAD, side * ry * RAD, side * rz * RAD));
		m.translate(-side * FIST_X, -FIST_Y, -FIST_Z);
		return true;
	}
}
