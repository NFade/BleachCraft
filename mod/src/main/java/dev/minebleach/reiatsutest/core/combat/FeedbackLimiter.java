package dev.minebleach.reiatsutest.core.combat;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Rate limit of the block feedback events per defender (r8 fx2 item 1): inside a sliding window of {@code windowTicks} at most
 * {@code maxNormal} events are sent for one key, and a priority event (the parry) is still sent while fewer than {@code maxPriority}
 * are in the window, so a flood of weak ability ticks can never swallow a parry. Pure Java (the key is any object, the server uses the
 * player UUID); a refused event is not counted.
 */
public final class FeedbackLimiter {
	private final int windowTicks;
	private final int maxNormal;
	private final int maxPriority;
	private final Map<Object, ArrayDeque<Long>> sent = new HashMap<>();

	public FeedbackLimiter(int windowTicks, int maxNormal, int maxPriority) {
		this.windowTicks = Math.max(1, windowTicks);
		this.maxNormal = Math.max(0, maxNormal);
		this.maxPriority = Math.max(this.maxNormal, maxPriority);
	}

	/** The server default: 4 events per second per player, a parry may bring it to 6. */
	public static FeedbackLimiter standard() {
		return new FeedbackLimiter(20, 4, 6);
	}

	/** True (and the event is counted) when an event for {@code key} at game tick {@code tick} may be sent. */
	public boolean tryAcquire(Object key, long tick, boolean priority) {
		ArrayDeque<Long> q = sent.computeIfAbsent(key, k -> new ArrayDeque<>());
		while (!q.isEmpty() && tick - q.peekFirst() >= windowTicks) {
			q.pollFirst();
		}
		int cap = priority ? maxPriority : maxNormal;
		if (q.size() >= cap) {
			return false;
		}
		q.addLast(tick);
		return true;
	}

	/** Drops the keys whose last event is older than the window (call now and then so a long running server keeps the table small). */
	public void purge(long tick) {
		sent.values().removeIf(q -> q.isEmpty() || tick - q.peekLast() >= windowTicks);
	}

	public int keys() {
		return sent.size();
	}
}
