package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.state.AbilityId;

/**
 * Parameters of the Rukia effect events (VFX_STORYBOARD 1.1, 3 and 5), pure Java so the numbers are unit tested and shared by
 * the server executor and the client effects. The {@code effect_event} params of every ability start with
 * {@code [aimX, aimY, aimZ, size]} ({@code size} = radius, length or reach, set by the server); the Rukia abilities append
 * {@link #extend}:
 * <ul>
 * <li>TSUKISHIRO {@code [.., radius, pillarHeight]}</li>
 * <li>HAKUREN {@code [.., length, stepLength, steps, halfWidth]} (the wave advances one step every 0.1 s, so its speed is stepLength x 10 blocks
 * per second; {@code halfWidth} is the server half width of the path, absent in older events)</li>
 * <li>SHIRAFUNE {@code [.., reach, usedLength]} (distance from the eye to the aimed point, never more than the reach)</li>
 * <li>ICE_SPIKE {@code [.., length, usedLength, steps, stepSeconds, halfWidth, firstSeconds]} (the thrust runs {@code steps} segments, one every
 * {@code stepSeconds}, the first {@code firstSeconds} after the cast; the free ray {@code usedLength} ends at the first block)</li>
 * </ul>
 * The client never hard codes a radius: when the balance numbers change (FIXES_B4 step 4: radii x1.5 to 2) only the
 * {@code size} the server sends changes and every effect follows.
 */
public final class RukiaFxParams {
	/** Default Hakuren steps and blocks per step (BalanceConfig after FIXES_B4: 8 steps of 2.5 blocks for the 20 block reach); the server sends the real values through {@link #extend(AbilityId, float[], double, int, int)}. */
	public static final double HAKUREN_STEP_LENGTH = 2.5;
	public static final int HAKUREN_STEPS = 8;
	/** Default gap between two segments of the ice spike thrust (BalanceConfig phase offsets 14, 16, 18 ...). */
	public static final int ICE_SPIKE_STEP_TICKS = 2;
	/** Default tick of the first ice spike segment (BalanceConfig phase offsets 14, 16 ...). */
	public static final int ICE_SPIKE_FIRST_TICKS = 14;
	/** Hakuren half width of the path when the event does not say (BalanceConfig HALF_WIDTH before the balance pass was 2.0). */
	public static final double HAKUREN_HALF_WIDTH = 2.0;
	/** Reference radii the storyboard numbers were written for: Tsukishiro 4, Hakuren length 12, width 2. */
	public static final double REF_TSUKISHIRO_RADIUS = 4.0;
	public static final double REF_HAKUREN_LENGTH = 12.0;
	/** Height of the Tsukishiro pillar (blocks) per block of radius (14 for the reference radius 4). */
	public static final double TSUKISHIRO_PILLAR_PER_RADIUS = 3.5;

	private RukiaFxParams() {
	}

	/** True for the abilities whose params are extended here. */
	public static boolean handles(AbilityId id) {
		return id == AbilityId.TSUKISHIRO || id == AbilityId.HAKUREN || id == AbilityId.SHIRAFUNE || id == AbilityId.ICE_SPIKE;
	}

	/** Effect id to ability (the ids of AbilitySpec: 20 to 22 and 24 are the Rukia abilities), or null. */
	public static AbilityId abilityOf(int effectId) {
		return switch (effectId) {
			case 20 -> AbilityId.TSUKISHIRO;
			case 21 -> AbilityId.HAKUREN;
			case 22 -> AbilityId.SHIRAFUNE;
			case 24 -> AbilityId.ICE_SPIKE;
			default -> null;
		};
	}

	/**
	 * Appends the extra parameters to {@code base = [aimX, aimY, aimZ, size]}; other abilities and malformed arrays are returned
	 * unchanged. {@code eyeToAim} is the distance from the caster's eye to the aimed point.
	 */
	public static float[] extend(AbilityId id, float[] base, double eyeToAim) {
		return extend(id, base, eyeToAim, HAKUREN_STEPS);
	}

	/** As {@link #extend(AbilityId, float[], double)} with the real phase data of the spec: the step count of Hakuren and of the ice spike. */
	public static float[] extend(AbilityId id, float[] base, double eyeToAim, int hakurenSteps) {
		return extend(id, base, eyeToAim, hakurenSteps, Double.NaN);
	}

	/** As above plus the Hakuren half width of the spec (NaN = not sent, the event keeps its six numbers). */
	public static float[] extend(AbilityId id, float[] base, double eyeToAim, int hakurenSteps, double hakurenHalfWidth) {
		return extend(id, base, eyeToAim, hakurenSteps, hakurenHalfWidth, ICE_SPIKE_STEP_TICKS);
	}

	/** As above plus the ticks between two segments of the ice spike thrust (the gap of its phase offsets). */
	public static float[] extend(AbilityId id, float[] base, double eyeToAim, int hakurenSteps, double hakurenHalfWidth, int iceSpikeStepTicks) {
		return extend(id, base, eyeToAim, hakurenSteps, hakurenHalfWidth, iceSpikeStepTicks, ICE_SPIKE_FIRST_TICKS);
	}

	/** As above plus the tick of the first segment of the ice spike thrust (the first phase offset). */
	public static float[] extend(AbilityId id, float[] base, double eyeToAim, int hakurenSteps, double hakurenHalfWidth, int iceSpikeStepTicks,
			int iceSpikeFirstTicks) {
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
				boolean withWidth = !Double.isNaN(hakurenHalfWidth);
				out = java.util.Arrays.copyOf(base, withWidth ? 7 : 6);
				int steps = Math.max(1, hakurenSteps);
				out[4] = (float) (size / steps);
				out[5] = steps;
				if (withWidth) {
					out[6] = (float) hakurenHalfWidth;
				}
			}
			case SHIRAFUNE -> {
				out = java.util.Arrays.copyOf(base, 5);
				out[4] = (float) Math.max(0.0, Math.min(size, eyeToAim));
			}
			case ICE_SPIKE -> {
				boolean withWidth = !Double.isNaN(hakurenHalfWidth);
				out = java.util.Arrays.copyOf(base, 9);
				out[4] = (float) Math.max(0.0, Math.min(size, eyeToAim));
				out[5] = Math.max(1, hakurenSteps);
				out[6] = Math.max(1, iceSpikeStepTicks) / 20.0f;
				out[7] = withWidth ? (float) hakurenHalfWidth : 1.5f;
				out[8] = Math.max(0, iceSpikeFirstTicks) / 20.0f;
			}
			default -> out = base;
		}
		return out;
	}

	/**
	 * Linear scale of an effect with the server radius: {@code radius / reference}, never below {@code min} so a tiny test radius
	 * still draws something. Particle counts use the square of it (area), see {@link #areaScale}.
	 */
	public static double radiusScale(double radius, double reference, double min) {
		return Math.max(min, reference <= 0 ? 1.0 : radius / reference);
	}

	/** Count scale of an area effect: the square of {@link #radiusScale}, clamped to {@code [min, max]} (counts stay bounded for huge radii). */
	public static double areaScale(double radius, double reference, double min, double max) {
		double k = radiusScale(radius, reference, 0.0);
		return Math.max(min, Math.min(max, k * k));
	}

	/** Reads parameter {@code i} of an event or {@code def} when the event is shorter (older server, other ability). */
	public static double param(float[] params, int i, double def) {
		return params != null && i >= 0 && i < params.length ? params[i] : def;
	}
}
