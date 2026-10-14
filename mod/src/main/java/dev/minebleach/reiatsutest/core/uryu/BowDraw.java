package dev.minebleach.reiatsutest.core.uryu;

/**
 * The draw of the Heilig Bogen (design/URYU_PLAN.md revision 2): the vanilla bow curve. Full power after
 * {@link UryuBalance#BOW_FULL_DRAW_TICKS} ticks; arrow damage scales with the power, the speed keeps a share even for a short draw.
 */
public final class BowDraw {
	private BowDraw() {
	}

	/** Power 0..1 of a draw held for {@code ticks}: {@code (t*t + 2t) / 3} with {@code t = ticks / 20}, capped at 1 (vanilla). */
	public static double power(int ticks) {
		if (ticks <= 0) {
			return 0.0;
		}
		double t = (double) ticks / UryuBalance.BOW_FULL_DRAW_TICKS;
		return Math.min(1.0, (t * t + t * 2.0) / 3.0);
	}

	/** The same curve for a fractional tick count (the client animation adds the partial tick). */
	public static double powerAt(double ticks) {
		if (ticks <= 0.0) {
			return 0.0;
		}
		double t = ticks / UryuBalance.BOW_FULL_DRAW_TICKS;
		return Math.min(1.0, (t * t + t * 2.0) / 3.0);
	}

	/** True when a release at this power fires an arrow (vanilla ignores releases under 0.1). */
	public static boolean fires(double power) {
		return power >= UryuBalance.BOW_MIN_POWER;
	}

	/** Factor on the damage of the full draw. */
	public static double damageFactor(double power) {
		return Math.max(0.0, Math.min(1.0, power));
	}

	/** Factor on the arrow speed of the full draw. */
	public static double speedFactor(double power) {
		double p = Math.max(0.0, Math.min(1.0, power));
		return UryuBalance.BOW_MIN_SPEED_SHARE + (1.0 - UryuBalance.BOW_MIN_SPEED_SHARE) * p;
	}

	/** Draw progress 0..1 for the client animation (the same curve, so the nocked arrow follows the real power). */
	public static double visualPull(int ticks) {
		return power(ticks);
	}

	/** Client animation: where the nock point starts behind the string (model units = blocks of the bow model). */
	public static final float NOCK_START = 0.04f;
	/** Client animation: how far the nock point travels back along the arrow axis at full draw. */
	public static final float NOCK_TRAVEL = 0.26f;
	/** Model z of the resting string (the string side of the exported bow, centre of the model cube = 0.5). */
	public static final float STRING_Z = 0.395f;

	/** How far (model units) the nock point is pulled back from the resting string for a draw progress 0..1. */
	public static float nockBack(double pull) {
		return NOCK_START + NOCK_TRAVEL * (float) Math.max(0.0, Math.min(1.0, pull));
	}

	/** Model z of the nock point (string middle and arrow tail) for a draw progress 0..1; the grip is at z = 0.5. */
	public static float nockZ(double pull) {
		return STRING_Z - nockBack(pull);
	}
}
