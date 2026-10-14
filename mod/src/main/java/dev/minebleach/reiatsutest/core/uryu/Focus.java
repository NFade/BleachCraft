package dev.minebleach.reiatsutest.core.uryu;

/**
 * The Focus counter of one Uryu (design/URYU.md section 3, revision 3): stacks 0..3, one more every 14 ticks (10 with Blut Arterie)
 * while the player stands on the ground, does not sprint and moves slowly enough: less than {@link UryuBalance#FOCUS_MOVE_EPS}
 * blocks per tick when standing, less than {@link UryuBalance#FOCUS_SNEAK_SPEED} while sneaking (a sneak walk at the normal sneak
 * speed is still enough). Real movement above that, sprinting, a jump or a fall, and being pushed ({@link #reset()}, called for
 * knockback) set it back to 0. A shunpo does not reset it: the glue skips the tick of the teleport ({@link #grace()}). The next
 * Heilig Pfeil {@link #consume()}s all stacks.
 */
public final class Focus {
	private int stacks;
	private int charge;
	private int graceLeft;

	public int stacks() {
		return stacks;
	}

	/** Ticks accumulated towards the next stack. */
	public int charge() {
		return charge;
	}

	public boolean full() {
		return stacks >= UryuBalance.FOCUS_MAX;
	}

	/** The horizontal speed below which the player counts as still enough. */
	public static double speedLimit(boolean sneaking) {
		return sneaking ? UryuBalance.FOCUS_SNEAK_SPEED : UryuBalance.FOCUS_MOVE_EPS;
	}

	/** Old form without the sneak rule (standing only). */
	public boolean tick(boolean grounded, boolean sprinting, double speed, boolean arterie) {
		return tick(grounded, sprinting, false, speed, arterie);
	}

	/**
	 * One server tick.
	 *
	 * @param grounded the player stands on the ground
	 * @param sprinting the player sprints
	 * @param sneaking the player holds sneak (a slow walk then keeps Focus)
	 * @param speed horizontal blocks the player moved since the last tick
	 * @param arterie Blut Arterie is on (SHIKAI or BANKAI): the faster interval
	 * @return true when the stack count changed
	 */
	public boolean tick(boolean grounded, boolean sprinting, boolean sneaking, double speed, boolean arterie) {
		if (graceLeft > 0) {
			graceLeft--;
			return false; // a shunpo just happened: neither counted nor reset
		}
		if (!grounded || sprinting || !(speed < speedLimit(sneaking))) {
			return reset();
		}
		if (stacks >= UryuBalance.FOCUS_MAX) {
			charge = 0;
			return false;
		}
		charge++;
		int interval = arterie ? UryuBalance.FOCUS_INTERVAL_ARTERIE : UryuBalance.FOCUS_INTERVAL;
		if (charge >= interval) {
			charge = 0;
			stacks++;
			return true;
		}
		return false;
	}

	/** The next {@link UryuBalance#FOCUS_GRACE_TICKS} ticks are skipped (shunpo): the stacks stay and nothing is built. */
	public void grace() {
		graceLeft = UryuBalance.FOCUS_GRACE_TICKS;
	}

	/** A push (knockback of a hit or of any ability), a state change: everything back to 0. Returns true when a stack was lost. */
	public boolean reset() {
		boolean had = stacks > 0;
		stacks = 0;
		charge = 0;
		graceLeft = 0;
		return had;
	}

	/** The stacks the shot uses; the counter starts again from 0. */
	public int consume() {
		int used = stacks;
		stacks = 0;
		charge = 0;
		return used;
	}
}
