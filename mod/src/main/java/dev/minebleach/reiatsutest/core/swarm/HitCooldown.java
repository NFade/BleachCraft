package dev.minebleach.reiatsutest.core.swarm;

import java.util.HashMap;
import java.util.Map;

/**
 * Per target hit limiter of one blade flight. A target can be hit again after {@code cooldownTicks} in the same leg (outward or
 * return); a new leg allows a hit after half of that, so a target on the path is hit going out and again coming back. At most
 * {@code maxHits} flight hits per target. Hits dealt by other phases of the same cast (the landing burst) are recorded with
 * {@link #markOther}: they restart the cooldown but do not count against the cap.
 */
public final class HitCooldown {
	public static final int LEG_OUT = 0;
	public static final int LEG_BACK = 1;

	private final int cooldownTicks;
	private final int maxHits;
	private final Map<Integer, long[]> state = new HashMap<>(); // id -> {lastTick, lastLeg, flightHits}

	public HitCooldown(int cooldownTicks, int maxHits) {
		this.cooldownTicks = Math.max(1, cooldownTicks);
		this.maxHits = Math.max(1, maxHits);
	}

	/** True when the target may be hit now. */
	public boolean ready(int id, long tick, int leg) {
		long[] s = state.get(id);
		if (s == null) {
			return true;
		}
		if (s[2] >= maxHits) {
			return false;
		}
		long since = tick - s[0];
		if (since >= cooldownTicks) {
			return true;
		}
		return s[1] != leg && since >= cooldownTicks / 2;
	}

	/** {@link #ready} then records the hit. */
	public boolean tryHit(int id, long tick, int leg) {
		if (!ready(id, tick, leg)) {
			return false;
		}
		long[] s = state.computeIfAbsent(id, k -> new long[3]);
		s[0] = tick;
		s[1] = leg;
		s[2]++;
		return true;
	}

	/** A hit of another phase of the same cast (landing burst): restarts the cooldown, does not count against the cap. */
	public void markOther(int id, long tick) {
		long[] s = state.computeIfAbsent(id, k -> new long[3]);
		s[0] = tick;
		s[1] = LEG_OUT;
	}

	public int hits(int id) {
		long[] s = state.get(id);
		return s == null ? 0 : (int) s[2];
	}

	public int targets() {
		return state.size();
	}
}
