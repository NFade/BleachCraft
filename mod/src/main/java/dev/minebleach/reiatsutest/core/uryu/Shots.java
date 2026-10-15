package dev.minebleach.reiatsutest.core.uryu;

/**
 * Numbers of one Heilig Pfeil by Focus (design/URYU.md section 3): +25 / +60 / +100 percent damage and speed for 1 / 2 / 3 stacks;
 * 3 stacks also pierce one extra target and fly without the gravity curve. Revision 2: the basic shot scales with the bow power
 * ({@link BowDraw}) and every arrow carries homing parameters (weak for the basic shot, strong for Z).
 */
public final class Shots {
	private Shots() {
	}

	/**
	 * @param pierce extra targets the arrow goes through
	 * @param life ticks of flight
	 * @param homing passive homing of this arrow
	 */
	public record Params(float damage, double speed, double gravity, int pierce, int focus, int life, Homing.Params homing) {
	}

	/** The basic shot at full power ({@code arterie} = SHIKAI or BANKAI: +20 percent). */
	public static Params basic(int stacks, boolean arterie) {
		return basic(stacks, arterie, 1.0);
	}

	/** The basic shot of a draw with the given bow power 0..1: damage scales with the power, the speed keeps a share. */
	public static Params basic(int stacks, boolean arterie, double power) {
		double base = BowDraw.tuning().damage() * (arterie ? UryuBalance.ARTERIE_SHOT_FACTOR : 1.0) * BowDraw.damageFactor(power);
		Params p = build(base, UryuBalance.ARROW_SPEED * BowDraw.speedFactor(power), stacks, UryuBalance.ARROW_LIFE,
				Homing.Params.degrees(UryuBalance.BASIC_HOMING_DEG, UryuBalance.BASIC_HOMING_RADIUS, UryuBalance.BASIC_HOMING_CONE_DEG));
		return p;
	}

	/** Heilig Pfeil, the homing arrow (Z, SHIKAI): fast, strong homing, 7 HP base. */
	public static Params focusShot(int stacks) {
		return build(UryuBalance.FOCUS_SHOT_DAMAGE, UryuBalance.Z_ARROW_SPEED, stacks, UryuBalance.Z_ARROW_LIFE,
				Homing.Params.degrees(UryuBalance.Z_HOMING_DEG, UryuBalance.Z_HOMING_RADIUS, UryuBalance.Z_HOMING_CONE_DEG));
	}

	private static Params build(double baseDamage, double baseSpeed, int stacks, int life, Homing.Params homing) {
		int s = Math.max(0, Math.min(UryuBalance.FOCUS_MAX, stacks));
		double f = UryuBalance.focusFactor(s);
		boolean full = s >= UryuBalance.FOCUS_FULL;
		return new Params((float) (baseDamage * f), baseSpeed * f, full ? 0.0 : UryuBalance.ARROW_GRAVITY, full ? 1 : 0, s, life, homing);
	}
}
