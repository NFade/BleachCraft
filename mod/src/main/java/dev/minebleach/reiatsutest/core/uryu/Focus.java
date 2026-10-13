package dev.minebleach.reiatsutest.core.uryu;

/**
 * The Focus counter of one Uryu (design/URYU.md section 3): stacks 0..3, one more every 14 ticks (10 with Blut Arterie) while the
 * player stands on the ground, does not sprint and moves less than {@link UryuBalance#FOCUS_MOVE_EPS} blocks per tick. Any
 * movement, a jump or a fall, sprinting, a hit with knockback or a Hirenkyaku sets it back to 0. The next Heilig Pfeil
 * {@link #consume()}s all stacks.
 */
public final class Focus {
	private int stacks;
	private int charge;

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

	/**
	 * One server tick.
	 *
	 * @param grounded the player stands on the ground
	 * @param sprinting the player sprints
	 * @param speed blocks the player moved since the last tick
	 * @param arterie Blut Arterie is on (SHIKAI or BANKAI): the faster interval
	 * @return true when the stack count changed
	 */
	public boolean tick(boolean grounded, boolean sprinting, double speed, boolean arterie) {
		if (!grounded || sprinting || !(speed < UryuBalance.FOCUS_MOVE_EPS)) {
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

	/** A hit with knockback, Hirenkyaku, a state change: everything back to 0. Returns true when a stack was lost. */
	public boolean reset() {
		boolean had = stacks > 0;
		stacks = 0;
		charge = 0;
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
