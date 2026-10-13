package dev.minebleach.reiatsutest.core.uryu;

/**
 * Numbers of one Heilig Pfeil by Focus (design/URYU.md section 3): +25 / +60 / +100 percent damage and speed for 1 / 2 / 3 stacks;
 * 3 stacks also pierce one extra target and fly without the gravity curve.
 */
public final class Shots {
	private Shots() {
	}

	/** @param pierce extra targets the arrow goes through */
	public record Params(float damage, double speed, double gravity, int pierce, int focus) {
	}

	/** The basic shot of the BASE state ({@code arterie} = SHIKAI or BANKAI: +20 percent). */
	public static Params basic(int stacks, boolean arterie) {
		double base = UryuBalance.BASIC_DAMAGE * (arterie ? UryuBalance.ARTERIE_SHOT_FACTOR : 1.0);
		return build(base, stacks);
	}

	/** Heilig Pfeil: Focus shot (Z, SHIKAI). */
	public static Params focusShot(int stacks) {
		return build(UryuBalance.FOCUS_SHOT_DAMAGE, stacks);
	}

	private static Params build(double baseDamage, int stacks) {
		int s = Math.max(0, Math.min(UryuBalance.FOCUS_MAX, stacks));
		double f = UryuBalance.focusFactor(s);
		boolean full = s >= UryuBalance.FOCUS_FULL;
		return new Params((float) (baseDamage * f), UryuBalance.ARROW_SPEED * f, full ? 0.0 : UryuBalance.ARROW_GRAVITY, full ? 1 : 0, s);
	}
}
