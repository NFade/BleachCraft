package dev.minebleach.reiatsutest.core.movement;

import dev.minebleach.reiatsutest.core.state.ZanpakutoState;

/**
 * Double jump state machine (SHIKAI and BANKAI of every character). Pure Java: the client feeds it once per client tick and applies the
 * jump itself when {@link #tick} returns true.
 *
 * <p>Rules:
 * <ul>
 * <li>One air jump per air time. Landing (or entering water, a ladder, elytra flight, a vehicle, creative flight ...) resets everything.</li>
 * <li>The jump key must be PRESSED anew in the air (a rising edge): a key that is simply held from the ground jump never fires, the
 *     player has to release it and press it again. Walking off a ledge without jumping also gives ONE air jump on the next press.</li>
 * <li>At least {@link #MIN_AIR_TICKS} client ticks after leaving the ground; a press earlier than that is ignored (not buffered).</li>
 * <li>Never while {@code blocked} (water, lava, climbing, flying, elytra, riding, spectator ...).</li>
 * <li>Only while {@code allowed} (the synced state is SHIKAI or BANKAI, see {@link #allowedState}). The key edge is tracked even while
 *     not allowed, so a held key never turns into a fake press when the state changes.</li>
 * </ul>
 */
public final class DoubleJump {
	/** The air jump may fire from the third tick in the air on. */
	public static final int MIN_AIR_TICKS = 3;

	private int airTicks;
	private boolean used;
	private boolean prevKeyDown;

	/** True for the states that have the double jump: SHIKAI and BANKAI (not SEALED, not BASE, not unknown). */
	public static boolean allowedState(ZanpakutoState state) {
		return state == ZanpakutoState.SHIKAI || state == ZanpakutoState.BANKAI;
	}

	/**
	 * One client tick.
	 *
	 * @param onGround    the player stands on the ground (after this tick's movement)
	 * @param jumpKeyDown the jump key is down now
	 * @param blocked     in water or lava, climbing, fall flying, riding, creative flying, spectator, sleeping, dead: resets like the ground
	 * @param allowed     the synced state allows the double jump
	 * @return true when the air jump fires now (at most once per air time)
	 */
	public boolean tick(boolean onGround, boolean jumpKeyDown, boolean blocked, boolean allowed) {
		boolean pressed = jumpKeyDown && !prevKeyDown;
		prevKeyDown = jumpKeyDown;
		if (onGround || blocked) {
			airTicks = 0;
			used = false;
			return false;
		}
		if (airTicks < Integer.MAX_VALUE) {
			airTicks++;
		}
		if (!pressed || used || !allowed || airTicks < MIN_AIR_TICKS) {
			return false;
		}
		used = true;
		return true;
	}

	/** Forgets the air time (feature switched off, a screen opened). The key edge is kept so a held key stays no press. */
	public void reset() {
		airTicks = 0;
		used = false;
	}

	/** Like {@link #reset()} but also forgets the key, for a new player object. */
	public void resetAll() {
		reset();
		prevKeyDown = false;
	}

	public int airTicks() {
		return airTicks;
	}

	public boolean airJumpUsed() {
		return used;
	}
}
