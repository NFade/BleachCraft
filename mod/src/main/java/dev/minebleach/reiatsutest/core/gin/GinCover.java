package dev.minebleach.reiatsutest.core.gin;

/** Hakufuku of one Gin: hidden until {@code hiddenUntil}, and the window in which his next thrust shows no white flash. */
public final class GinCover {
	private long hiddenUntil;
	private long flashUntil;

	public void start(long now, int hideTicks, int flashTicks) {
		hiddenUntil = now + hideTicks;
		flashUntil = now + flashTicks;
	}

	public boolean hidden(long now) {
		return now < hiddenUntil;
	}

	/** Damage breaks the cover. True when he was hidden. The flash window stays (the next thrust is still a surprise). */
	public boolean damaged(long now) {
		boolean was = hidden(now);
		hiddenUntil = 0;
		return was;
	}

	/** A thrust leaves now: true when it must show no white flash. The window is used up by that thrust. */
	public boolean takeNoFlash(long now) {
		boolean quiet = now < flashUntil;
		flashUntil = 0;
		return quiet;
	}

	public boolean flashWindow(long now) {
		return now < flashUntil;
	}

	public void reset() {
		hiddenUntil = 0;
		flashUntil = 0;
	}
}
