package dev.minebleach.reiatsutest.core.hud;

/** Cooldown numbers of the side cells (pure, tested): total with a fallback, sweep frame, wipe height and the seconds label. */
public final class SideCellMath {
	/** Frames of {@code cooldown_sweep.png}. */
	public static final int SWEEP_FRAMES = 24;

	private SideCellMath() {
	}

	/**
	 * Total length of the running cooldown. The client only knows the default numbers; a server config can differ, so the largest
	 * remaining value seen during this cooldown wins when it is higher. Never below 1 while something is cooling.
	 */
	public static int total(int configuredTicks, int peakSeenTicks) {
		return Math.max(1, Math.max(configuredTicks, peakSeenTicks));
	}

	/** Frame 1..24 of the radial sweep (24 = just started), 0 when nothing is cooling. */
	public static int sweepFrame(int remaining, int total) {
		if (remaining <= 0 || total <= 0) {
			return 0;
		}
		return Math.max(1, Math.min(SWEEP_FRAMES, (int) Math.ceil((double) SWEEP_FRAMES * remaining / total)));
	}

	/** Height in pixels of the dark wipe over an icon of {@code size} pixels (full at the start, shrinking to 0). */
	public static int wipeHeight(int remaining, int total, int size) {
		if (remaining <= 0 || total <= 0) {
			return 0;
		}
		return Math.max(1, Math.min(size, (int) Math.ceil((double) size * remaining / total)));
	}

	/**
	 * Text in the cell while cooling, null when ready: whole seconds (rounded up) from one second on, below one second a
	 * tenth ("0.7"), never longer than three characters.
	 */
	public static String secondsLabel(int remaining) {
		if (remaining <= 0) {
			return null;
		}
		if (remaining >= 20) {
			return Integer.toString(Math.min(99, (int) Math.ceil(remaining / 20.0)));
		}
		int tenths = Math.max(1, Math.min(9, (int) Math.ceil(remaining / 2.0)));
		return "0." + tenths;
	}

	/** First character of a key's display name, upper case, or "?" when empty (unbound keys have long names). */
	public static String keyLetter(String keyName) {
		if (keyName == null || keyName.isBlank()) {
			return "?";
		}
		int cp = keyName.codePointAt(0);
		return new String(Character.toChars(Character.toUpperCase(cp)));
	}
}
