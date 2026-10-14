package dev.minebleach.reiatsutest.core.uryu;

/**
 * The side-on archer stance of the clip {@code uryu_bow_draw} in the game. playerAnimator turns the WHOLE model (legs, head, arms) with the body yaw,
 * while the clip was made in Blockbench where only the torso turns and the arms and head stand in the root frame. The arms and the head therefore get the
 * body yaw added back (their world direction stays forward) and the arm positions are carried back into the turned frame:
 * {@code p' = R(yaw) p}, {@code R(a): (x, z) -> (x cos a + z sin a, -x sin a + z cos a)} (the same formula the model parts use). With the clip numbers the
 * left shoulder (1.21, -4.851) at the body yaw -76 degrees returns to its natural pivot (5, 0) (see the test), which confirms the sign.
 */
public final class BowStance {
	private BowStance() {
	}

	/** Rotates the point (x, z) about the vertical axis by {@code angleRad} (the model part convention). */
	public static double[] rotateXZ(double x, double z, double angleRad) {
		double c = Math.cos(angleRad);
		double s = Math.sin(angleRad);
		return new double[] {x * c + z * s, -x * s + z * c};
	}
}
