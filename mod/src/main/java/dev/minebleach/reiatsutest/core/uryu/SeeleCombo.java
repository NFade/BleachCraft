package dev.minebleach.reiatsutest.core.uryu;

import dev.minebleach.reiatsutest.core.swarm.V3;

/**
 * Seele Schneider (B, SHIKAI), the melee combo of design/URYU_PLAN.md: a short velocity lunge forward, then three slashes of a Reishi
 * blade 0.25 s apart, each hitting everything in a cone of {@link UryuBalance#SEELE_REACH} blocks and
 * {@link UryuBalance#SEELE_CONE_DEG} degrees in front, the third one also knocking back. Pure timing and geometry; the glue moves the
 * player, finds the targets and does the damage.
 */
public final class SeeleCombo {
	private SeeleCombo() {
	}

	/** Ticks (from the cast) at which the lunge velocity is applied: 0, 1, 2. */
	public static boolean lungeAt(int k) {
		return k >= 0 && k < UryuBalance.SEELE_LUNGE_TICKS;
	}

	/** Tick of the slash with this index (0..2): 3, 8, 13. */
	public static int slashTick(int index) {
		return UryuBalance.SEELE_FIRST_SLASH + index * UryuBalance.SEELE_SLASH_GAP;
	}

	/** Index of the slash that happens at tick {@code k}, or -1. */
	public static int slashAt(int k) {
		for (int i = 0; i < UryuBalance.SEELE_SLASHES; i++) {
			if (slashTick(i) == k) {
				return i;
			}
		}
		return -1;
	}

	/** True once the combo is over (after the last slash). */
	public static boolean over(int k) {
		return k > slashTick(UryuBalance.SEELE_SLASHES - 1);
	}

	/** Only the last slash knocks back. */
	public static boolean knocksBack(int index) {
		return index == UryuBalance.SEELE_SLASHES - 1;
	}

	/** Horizontal unit direction of the lunge from a look vector (a look straight up or down falls back to {@code fallback}). */
	public static V3 lungeDirection(V3 look, V3 fallback) {
		V3 flat = new V3(look.x(), 0.0, look.z());
		return flat.lengthSq() < 1.0E-6 ? fallback : flat.normalizedOr(fallback);
	}

	/** Velocity per tick of the lunge (blocks per tick) in the given horizontal direction. */
	public static V3 lungeVelocity(V3 dir) {
		return new V3(dir.x() * UryuBalance.SEELE_LUNGE_SPEED, 0.0, dir.z() * UryuBalance.SEELE_LUNGE_SPEED);
	}

	/**
	 * True when {@code target} (the aim point of the body) is inside the cone of one slash: within the reach of {@code origin} (the body's
	 * eye or chest) and within half the opening angle of {@code look}. A target on top of the origin counts.
	 */
	public static boolean inCone(V3 origin, V3 look, V3 target) {
		V3 to = target.sub(origin);
		double d = to.length();
		if (d > UryuBalance.SEELE_REACH) {
			return false;
		}
		if (d < 1.0E-6) {
			return true;
		}
		return ArrowSim.angleBetween(look, to) <= Math.toRadians(UryuBalance.SEELE_CONE_DEG / 2.0);
	}
}
