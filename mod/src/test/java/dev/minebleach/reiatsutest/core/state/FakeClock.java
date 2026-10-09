package dev.minebleach.reiatsutest.core.state;

/** Test clock: ticks only move when the test says so. */
public final class FakeClock implements Clock {
	private long now;

	public FakeClock() {
		this(0L);
	}

	public FakeClock(long start) {
		this.now = start;
	}

	@Override
	public long nowTick() {
		return now;
	}

	public void advance(long ticks) {
		now += ticks;
	}

	public void set(long t) {
		now = t;
	}
}
