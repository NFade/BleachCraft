package dev.minebleach.reiatsutest.core.uryu;

/**
 * Playback state of the bow draw clip {@code uryu_bow_draw} (design/anim/player_game_rig_bow_v2.bbmodel, 40 ticks): stance 0-5, draw 5-21, hold 21-26,
 * release 26-28, relax to 40. The clip follows the real draw: one clip tick per game tick up to {@link #FULL_DRAW_TICK}, frozen there while the player
 * keeps drawing, and on release it fast forwards (two ticks per game tick) to {@link #RELEASE_START_TICK}, then plays the release and the relax at normal
 * speed. Pure logic, the client layer (TpBowLayer) feeds it once per game tick and moves the playerAnimator player by {@link Step#advance}.
 */
public final class BowClipState {
	public static final int FULL_DRAW_TICK = 21;
	public static final int RELEASE_START_TICK = 24;
	public static final int END_TICK = 40;

	/** Limb speed (LimbAnimator, blocks per tick scaled) from which the legs and torso follow the walk instead of the clip stance. */
	public static final double MOVING_LIMB_SPEED = 0.3;

	/**
	 * Weight 1..0 of the clip on the legs and the torso: 1 standing still (the side-on archer stance), 0 while walking or running (vanilla legs, straight torso,
	 * so the player can shoot on the move); smooth between 0 and {@link #MOVING_LIMB_SPEED}. The arms are not touched by this.
	 */
	public static double stanceWeight(double limbSpeed) {
		double x = Math.max(0.0, Math.min(1.0, limbSpeed / MOVING_LIMB_SPEED));
		double smooth = x * x * (3.0 - 2.0 * x);
		return 1.0 - smooth;
	}

	public enum Phase {
		IDLE, DRAW, HOLD, RELEASE
	}

	/** What the layer does this game tick: start a new clip, advance it by {@code advance} clip ticks, keep it ({@code active}) or drop it. */
	public record Step(boolean restart, int advance, boolean active) {
	}

	private Phase phase = Phase.IDLE;
	private int tick;

	public Phase phase() {
		return phase;
	}

	/** Clip tick the player has reached (0 when idle). */
	public int tick() {
		return tick;
	}

	/**
	 * R13: 0..1 how much the string arm is "in the draw" (rises over the draw ticks, holds, goes after the release): scales extra turns of the string arm. Smooth, pure.
	 */
	public double drawWeight() {
		double x;
		switch (phase) {
			case DRAW, HOLD -> x = Math.min(1.0, tick / (double) FULL_DRAW_TICK);
			case RELEASE -> x = tick <= RELEASE_START_TICK + 2 ? 1.0 : Math.max(0.0, 1.0 - (tick - RELEASE_START_TICK - 2) / (double) (END_TICK - RELEASE_START_TICK - 2));
			default -> x = 0.0;
		}
		return x * x * (3.0 - 2.0 * x);
	}

	/** True while the clip is held at full draw (the player is frozen on the hold pose, no sub tick interpolation). */
	public boolean holding() {
		return phase == Phase.HOLD;
	}

	/** One game tick; {@code drawing} = the player is drawing the bow right now. */
	public Step step(boolean drawing) {
		switch (phase) {
			case IDLE:
				if (!drawing) {
					return new Step(false, 0, false);
				}
				return begin();
			case DRAW:
			case HOLD:
				if (!drawing) {
					phase = Phase.RELEASE;
					return release();
				}
				if (tick < FULL_DRAW_TICK) {
					tick++;
					phase = tick >= FULL_DRAW_TICK ? Phase.HOLD : Phase.DRAW;
					return new Step(false, 1, true);
				}
				phase = Phase.HOLD;
				return new Step(false, 0, true);
			default:
				if (drawing) {
					return begin();
				}
				return release();
		}
	}

	private Step begin() {
		phase = Phase.DRAW;
		tick = 0;
		return new Step(true, 0, true);
	}

	private Step release() {
		if (tick >= END_TICK) {
			phase = Phase.IDLE;
			tick = 0;
			return new Step(false, 0, false);
		}
		int adv = tick < RELEASE_START_TICK ? Math.min(2, RELEASE_START_TICK - tick) : 1;
		tick = Math.min(END_TICK, tick + adv);
		return new Step(false, adv, true);
	}
}
