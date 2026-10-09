package dev.minebleach.reiatsutest.core.state;

/** Token bucket: {@code perSecond} requests per 20 ticks, burst of the same size. Deterministic given the clock. */
public final class RateLimiter {
	private final Clock clock;
	private final int perSecond;
	private double tokens;
	private long last;

	public RateLimiter(Clock clock, int perSecond) {
		this.clock = clock;
		this.perSecond = perSecond;
		this.tokens = perSecond;
		this.last = clock.nowTick();
	}

	public boolean tryAcquire() {
		long now = clock.nowTick();
		if (now > last) {
			tokens = Math.min(perSecond, tokens + (now - last) * perSecond / 20.0);
		}
		last = now;
		if (tokens >= 1.0 - 1e-9) {
			tokens -= 1.0;
			return true;
		}
		return false;
	}
}
