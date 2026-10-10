package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.state.AbilityId;

/**
 * Parameters of the Rukia effect events (VFX_STORYBOARD 1.1, 3 and 5), pure Java so the numbers are unit tested and shared by
 * the server executor and the client effects. The {@code effect_event} params of every ability start with
 * {@code [aimX, aimY, aimZ, size]} ({@code size} = radius, length or reach, set by the server); the Rukia abilities append
 * {@link #extend}:
 * <ul>
 * <li>TSUKISHIRO {@code [.., radius, pillarHeight]}</li>
 * <li>HAKUREN {@code [.., length, stepLength, steps]} (the wave advances one step every 0.1 s, so its speed is stepLength x 10 blocks per second)</li>
 * <li>SHIRAFUNE {@code [.., reach, usedLength]} (distance from the eye to the aimed point, never more than the reach)</li>
 * <li>ABSOLUTE_ZERO {@code [.., radius, pauseSeconds]} (how long the encased targets stand frozen before the shatter)</li>
 * </ul>
 * The client never hard codes a radius: when the balance numbers change (FIXES_B4 step 4: radii x1.5 to 2) only the
 * {@code size} the server sends changes and every effect follows.
 */
public final class RukiaFxParams {
	/** Default Hakuren steps and blocks per step (BalanceConfig after FIXES_B4: 8 steps of 2.5 blocks for the 20 block reach); the server sends the real values through {@link #extend(AbilityId, float[], double, int, int)}. */
	public static final double HAKUREN_STEP_LENGTH = 2.5;
	public static final int HAKUREN_STEPS = 8;
	/** Absolute zero default: encased from tick 40 to the shatter at tick 66 (the server sends the real gap of its phase offsets). */
	public static final int ABSOLUTE_ZERO_PAUSE_TICKS = 26;
	/** Height of the Tsukishiro pillar (blocks) per block of radius (14 for the reference radius 4). */
	public static final double TSUKISHIRO_PILLAR_PER_RADIUS = 3.5;

	private RukiaFxParams() {
	}

	/** True for the abilities whose params are extended here. */
	public static boolean handles(AbilityId id) {
		return id == AbilityId.TSUKISHIRO || id == AbilityId.HAKUREN || id == AbilityId.SHIRAFUNE || id == AbilityId.ABSOLUTE_ZERO;
	}

	/** Effect id to ability (the ids of AbilitySpec: 20 to 23 are the Rukia abilities), or null. */
	public static AbilityId abilityOf(int effectId) {
		return switch (effectId) {
			case 20 -> AbilityId.TSUKISHIRO;
			case 21 -> AbilityId.HAKUREN;
			case 22 -> AbilityId.SHIRAFUNE;
			case 23 -> AbilityId.ABSOLUTE_ZERO;
			default -> null;
		};
	}

	/**
	 * Appends the extra parameters to {@code base = [aimX, aimY, aimZ, size]}; other abilities and malformed arrays are returned
	 * unchanged. {@code eyeToAim} is the distance from the caster's eye to the aimed point.
	 */
	public static float[] extend(AbilityId id, float[] base, double eyeToAim) {
		return extend(id, base, eyeToAim, HAKUREN_STEPS, ABSOLUTE_ZERO_PAUSE_TICKS);
	}

	/** As {@link #extend(AbilityId, float[], double)} with the real phase data of the spec: Hakuren step count and the Absolute zero gap between freeze and shatter (ticks). */
	public static float[] extend(AbilityId id, float[] base, double eyeToAim, int hakurenSteps, int absoluteZeroPauseTicks) {
		if (id == null || !handles(id) || base == null || base.length < 4) {
			return base;
		}
		float size = base[3];
		float[] out;
		switch (id) {
			case TSUKISHIRO -> {
				out = java.util.Arrays.copyOf(base, 5);
				out[4] = (float) (size * TSUKISHIRO_PILLAR_PER_RADIUS);
			}
			case HAKUREN -> {
				out = java.util.Arrays.copyOf(base, 6);
				int steps = Math.max(1, hakurenSteps);
				out[4] = (float) (size / steps);
				out[5] = steps;
			}
			case SHIRAFUNE -> {
				out = java.util.Arrays.copyOf(base, 5);
				out[4] = (float) Math.max(0.0, Math.min(size, eyeToAim));
			}
			default -> {
				out = java.util.Arrays.copyOf(base, 5);
				out[4] = absoluteZeroPauseTicks / 20.0f;
			}
		}
		return out;
	}

	/** Reads parameter {@code i} of an event or {@code def} when the event is shorter (older server, other ability). */
	public static double param(float[] params, int i, double def) {
		return params != null && i >= 0 && i < params.length ? params[i] : def;
	}
}
