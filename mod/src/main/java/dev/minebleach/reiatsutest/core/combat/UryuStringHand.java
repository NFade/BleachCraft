package dev.minebleach.reiatsutest.core.combat;

/**
 * The string hand of the first person bow (U-FX 11), pure maths: when the vanilla left arm is drawn in the picture, how fast it comes and goes, and where it
 * is (the {@code L} row of the running gesture, absolute in the camera frame of the hand pass). The client class {@code UryuBowMotion} only pushes the result
 * on the matrix stack.
 */
public final class UryuStringHand {
	/** Resting place of the empty fist of the vanilla arm, relative to the hand frame ({@code FirstPersonHand}), blocks. */
	public static final double FIST_X = 0.134;
	public static final double FIST_Y = -0.018;
	public static final double FIST_Z = -0.316;
	/** Turn of the string arm about its fist (degrees, right hander layout): it comes in from the lower left like the free hand of the two handed hold. */
	public static final double[] ARM_TURN = {-15.0, -30.0, -35.0};
	/** Seconds the hand takes to come in and to leave. */
	public static final double IN_SECONDS = 0.18;
	public static final double OUT_SECONDS = 0.22;
	/** Where the hand comes from: this far below and behind its place (blocks). */
	private static final double FROM_Y = -0.9;
	private static final double FROM_Z = 0.1;
	/** Main hand frame of the hand pass (blocks): x right, y up, z forward is negative. */
	private static final double HAND_X = 0.56;
	private static final double HAND_Y = -0.52;
	private static final double HAND_Z = -0.72;
	/** The release gesture shows the hand for this many ticks (it leaves back and out of the picture). */
	public static final double RELEASE_TICKS = 14.0;

	private UryuStringHand() {
	}

	/**
	 * Placement of the arm.
	 *
	 * @param baseX base translation of the hand pass: the hand frame of the arm that rests on the other side, plus the equip offset in y
	 * @param dx translation from the base to the fist, blocks
	 * @param rotDeg rotation of the arm about its fist (x, y, z), degrees
	 */
	public record Placement(double baseX, double baseY, double baseZ, double dx, double dy, double dz, double[] rotDeg) {
	}

	/**
	 * True while the hand is wanted: the bow is shown in the main hand and the hands are visible ({@code candidate}), a gesture sample exists ({@code haveSample}) and
	 * the flow is drawing, in the first {@link #RELEASE_TICKS} of the release, in the raise before the event of Licht Regen / Sternenstaub ({@code preEvent} >= 0,
	 * see {@link UryuBowFlow#preEvent}) or an ability gesture with rows for the free hand runs ({@code runHasStringHand}).
	 *
	 * @param releaseTick animation tick of the flow (used for the release)
	 */
	public static boolean wanted(boolean candidate, boolean haveSample, UryuBowFlow.Phase phase, double releaseTick, double preEvent, boolean runHasStringHand) {
		if (!candidate || !haveSample) {
			return false;
		}
		return phase == UryuBowFlow.Phase.DRAW || phase == UryuBowFlow.Phase.RELEASE && releaseTick < RELEASE_TICKS || preEvent >= 0.0 || runHasStringHand;
	}

	/** One frame of the presence 0..1 of the hand: in over {@link #IN_SECONDS}, out over {@link #OUT_SECONDS}. */
	public static float presence(float presence, boolean want, float dt) {
		return want ? Math.min(1f, presence + dt / (float) IN_SECONDS) : Math.max(0f, presence - dt / (float) OUT_SECONDS);
	}

	/**
	 * Where the arm goes.
	 *
	 * @param side +1 for a right handed player (the bow in the right hand, the string arm is the left one), -1 for a left hander
	 * @param l the {@code L} row of the sample: px, py, pz, arx, ary, arz, free
	 * @param equip equip progress of the hand 0..1 (the hand rises from below while it equips)
	 * @param presence presence 0..1 of the hand ({@link #presence})
	 */
	public static Placement placement(int side, float[] l, float equip, float presence) {
		double eqY = -0.6 * (1.0 - Math.max(0.0, Math.min(1.0, equip)));
		double e = FpEase.smooth(presence);
		// absolute (camera frame of the hand code): the main hand frame plus the row, the place of the empty fist of the other hand, the way in from below
		double tx = side * HAND_X + side * l[0];
		double ty = HAND_Y + eqY + l[1];
		double tz = HAND_Z + l[2];
		double px = tx;
		double py = ty + FROM_Y * (1.0 - e);
		double pz = tz + FROM_Z * (1.0 - e);
		double rx = -side * (HAND_X + FIST_X);
		double ry = HAND_Y + eqY + FIST_Y;
		double rz = HAND_Z + FIST_Z;
		double[] rot = {ARM_TURN[0] + l[3], side * ARM_TURN[1] + side * l[4], side * ARM_TURN[2] + side * l[5]};
		return new Placement(-side * HAND_X, HAND_Y + eqY, HAND_Z, px - rx, py - ry, pz - rz, rot);
	}
}
