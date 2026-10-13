package dev.minebleach.reiatsutest.core.uryu;

import dev.minebleach.reiatsutest.core.swarm.V3;

/**
 * Hirenkyaku (design/URYU.md section 5): a dash of 8 blocks along the look, done with server velocity (no teleport) over
 * {@link UryuBalance#DASH_TICKS} ticks, and the fall is cancelled for 0.6 s from the cast (a Reishi platform: no block is placed).
 * Focus is set to 0 by the cast.
 */
public final class Hirenkyaku {
	private Hirenkyaku() {
	}

	public enum Phase {
		/** Apply the dash velocity. */
		DASH,
		/** The tick after the dash: stop the horizontal speed so the player does not slide on. */
		BRAKE,
		/** Hold the player above any pit: no downward speed, no fall damage. */
		HOVER,
		DONE
	}

	/** Phase at tick {@code k} after the cast (0 = the tick of the cast). */
	public static Phase phaseAt(int k) {
		if (k < 0) {
			return Phase.DONE;
		}
		if (k < UryuBalance.DASH_TICKS) {
			return Phase.DASH;
		}
		if (k == UryuBalance.DASH_TICKS) {
			return Phase.BRAKE;
		}
		return k < UryuBalance.HOVER_TICKS ? Phase.HOVER : Phase.DONE;
	}

	/** Blocks per tick of the dash. */
	public static double speed() {
		return UryuBalance.DASH_BLOCKS / UryuBalance.DASH_TICKS;
	}

	/** Unit direction of the dash: the look with its vertical share limited, so a look at the floor does not dig in. */
	public static V3 direction(V3 look) {
		V3 l = look.normalizedOr(new V3(0, 0, 1));
		double y = Math.max(UryuBalance.DASH_MIN_Y, Math.min(UryuBalance.DASH_MAX_Y, l.y()));
		double h = Math.sqrt(l.x() * l.x() + l.z() * l.z());
		if (h < 1.0E-6) {
			return new V3(0, Math.signum(y == 0 ? 1 : y), 0);
		}
		double k = Math.sqrt(Math.max(0.0, 1.0 - y * y)) / h;
		return new V3(l.x() * k, y, l.z() * k);
	}

	/** The distance the dash covers at full speed (8). */
	public static double distance() {
		return speed() * UryuBalance.DASH_TICKS;
	}
}
