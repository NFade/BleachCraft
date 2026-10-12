package dev.minebleach.reiatsutest.core.swarm;

/**
 * Server side validation of the steer packets of one caster (client to server, about 10 per second while the key is held).
 * Nothing the client sends is trusted: a non-finite or wildly scaled vector is dropped, packets above the rate limit are dropped,
 * a direction further than the tolerance from the server's own look vector is replaced by it, and steering lapses by itself when
 * packets stop (the client died, lagged out or lost the release packet). Turn speed and range are enforced by {@link BladeFlight},
 * not trusted to the packet.
 */
public final class SteerGate {
	public enum Verdict { OK, RELEASE, BAD_VECTOR, RATE_LIMITED }

	/** {@code dir} is the direction to use (OK only); {@code clamped} is true when the sent one was replaced by the server look. */
	public record Result(Verdict verdict, V3 dir, boolean clamped) {
	}

	private final int timeoutTicks;
	private final int ratePerSecond;
	private final int burst;
	private final double toleranceRad;
	private double tokens;
	private long lastRefill;
	private boolean held;
	private long lastTick = Long.MIN_VALUE / 2;
	private V3 dir = V3.ZERO;
	private int accepted;
	private int dropped;
	private int clampedCount;

	public SteerGate(FlightConfig cfg, long nowTick) {
		this(cfg.steerTimeoutTicks(), cfg.steerRatePerSecond(), Math.toRadians(cfg.steerLookToleranceDeg()), nowTick);
	}

	public SteerGate(int timeoutTicks, int ratePerSecond, double toleranceRad, long nowTick) {
		this.timeoutTicks = timeoutTicks;
		this.ratePerSecond = Math.max(1, ratePerSecond);
		this.burst = Math.max(2, this.ratePerSecond / 4);
		this.toleranceRad = toleranceRad;
		this.tokens = burst;
		this.lastRefill = nowTick;
	}

	public Result accept(long tick, boolean holding, V3 sent, V3 serverLook) {
		if (!holding) {
			held = false;
			accepted++;
			return new Result(Verdict.RELEASE, dir, false);
		}
		if (tick > lastRefill) {
			tokens = Math.min(burst, tokens + (tick - lastRefill) * ratePerSecond / 20.0);
		}
		lastRefill = tick;
		if (tokens < 1.0 - 1.0E-9) {
			dropped++;
			return new Result(Verdict.RATE_LIMITED, dir, false);
		}
		tokens -= 1.0;
		double len = sent.length();
		if (!sent.isFinite() || len < 0.5 || len > 1.5) {
			dropped++;
			return new Result(Verdict.BAD_VECTOR, dir, false);
		}
		V3 look = serverLook.normalizedOr(sent.scale(1.0 / len));
		V3 unit = sent.scale(1.0 / len);
		V3 used = SteerMath.withinCone(unit, look, toleranceRad);
		boolean clamped = used != unit;
		if (clamped) {
			clampedCount++;
		}
		held = true;
		lastTick = tick;
		dir = used;
		accepted++;
		return new Result(Verdict.OK, used, clamped);
	}

	/** True while a held packet is recent enough. */
	public boolean holding(long tick) {
		return held && tick - lastTick <= timeoutTicks;
	}

	public V3 dir() {
		return dir;
	}

	public int accepted() {
		return accepted;
	}

	public int dropped() {
		return dropped;
	}

	public int clamped() {
		return clampedCount;
	}
}
