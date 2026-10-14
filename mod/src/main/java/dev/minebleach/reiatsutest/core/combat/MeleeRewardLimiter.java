package dev.minebleach.reiatsutest.core.combat;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Anti-spam of the melee hit reiatsu (R11 T2) for ONE player. Better Combat allows fast attacks, and +5 per hit unchecked would print dozens of
 * points a second. Two limits: one reward per target per {@code targetGapTicks}, and at most {@code maxPerSecondTenths} tenths inside any sliding
 * 20-tick window (a reward that does not fit is cut to what is left, 0 when the window is full). Pure Java; the glue keeps one instance per player.
 */
public final class MeleeRewardLimiter {
	private static final int WINDOW_TICKS = 20;

	private final int targetGapTicks;
	private final int maxPerSecondTenths;
	private final Map<Integer, Integer> lastRewardOnTarget = new HashMap<>();
	private final ArrayDeque<int[]> window = new ArrayDeque<>(); // {tick, tenths}
	private int windowSum;

	public MeleeRewardLimiter(int targetGapTicks, int maxPerSecondTenths) {
		this.targetGapTicks = Math.max(0, targetGapTicks);
		this.maxPerSecondTenths = Math.max(0, maxPerSecondTenths);
	}

	/**
	 * @param targetId entity id of the victim
	 * @param tick     server tick
	 * @param wanted   tenths the hit would give
	 * @return the tenths granted (0 when limited); the grant is recorded
	 */
	public int grant(int targetId, int tick, int wanted) {
		if (wanted <= 0) {
			return 0;
		}
		expire(tick);
		Integer last = lastRewardOnTarget.get(targetId);
		if (last != null && tick >= last && tick - last < targetGapTicks) {
			return 0;
		}
		int give = Math.min(wanted, maxPerSecondTenths - windowSum);
		if (give <= 0) {
			return 0;
		}
		window.addLast(new int[] {tick, give});
		windowSum += give;
		lastRewardOnTarget.put(targetId, tick);
		if (lastRewardOnTarget.size() > 64) {
			lastRewardOnTarget.values().removeIf(t -> tick - t >= targetGapTicks);
		}
		return give;
	}

	private void expire(int tick) {
		while (!window.isEmpty() && (tick - window.peekFirst()[0] >= WINDOW_TICKS || tick < window.peekFirst()[0])) {
			windowSum -= window.pollFirst()[1];
		}
	}
}
