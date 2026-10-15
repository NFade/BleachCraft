package dev.minebleach.reiatsutest.core.uryu;

/**
 * R13: while the bow is drawn (and for a moment after the release, so the release pose does not snap) the body of an archer faces where he looks. Vanilla turns the body
 * of a walking player towards the direction of the walk (within 75 degrees of the head), so a strafing archer aimed along his legs and shot sideways.
 * Pure decision; the client glue ({@code UryuBowBody}) copies the head yaw to the body yaw while this is true.
 */
public final class BowBodyAlign {
	/** Ticks after the last drawing tick in which the body is still held to the look (the release and the follow through of the clip). */
	public static final int HOLD_AFTER_TICKS = 12;

	private BowBodyAlign() {
	}

	/** {@code lastDrawTick} is the world tick of the last tick the archer was drawing, or {@link Long#MIN_VALUE} when never. */
	public static boolean active(boolean drawing, long now, long lastDrawTick) {
		if (drawing) {
			return true;
		}
		return lastDrawTick != Long.MIN_VALUE && now >= lastDrawTick && now - lastDrawTick <= HOLD_AFTER_TICKS;
	}
}
