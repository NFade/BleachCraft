package dev.minebleach.reiatsutest.core.uryu;

/**
 * R14: the mean of the last few horizontal speeds of a player. The server sees a player's position only when a movement packet arrives, so a tick
 * can see two steps and the next none; a sneak walk (0.065 blocks a tick) then reads as 0.13 for one tick, above {@link UryuBalance#FOCUS_SNEAK_SPEED},
 * and the Focus was lost while the player walked on shift. The mean over {@link #SIZE} ticks removes that jitter and keeps the real speed.
 */
public final class SpeedWindow {
	public static final int SIZE = 4;

	private final double[] ring = new double[SIZE];
	private int count;
	private int next;

	/** Adds a sample, returns the mean of the samples held (at most {@link #SIZE}). */
	public double push(double speed) {
		ring[next] = Math.max(0.0, speed);
		next = (next + 1) % SIZE;
		count = Math.min(SIZE, count + 1);
		double sum = 0.0;
		for (int i = 0; i < count; i++) {
			sum += ring[i];
		}
		return sum / count;
	}

	/** Forgets everything (after a teleport the samples are not a walk). */
	public void clear() {
		count = 0;
		next = 0;
	}
}
