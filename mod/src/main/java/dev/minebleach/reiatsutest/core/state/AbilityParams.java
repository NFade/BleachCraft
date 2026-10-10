package dev.minebleach.reiatsutest.core.state;

/**
 * Names of the per-ability numbers stored in {@link AbilitySpec#params()} (B4 step 4). Lengths are blocks, damage is HP
 * (1 HP = half a heart), times are ticks. A key an ability does not use is simply absent.
 */
public final class AbilityParams {
	/** Main area radius (sphere, circle or impact burst). */
	public static final String RADIUS = "radius";
	/** Half height of a circle area (cylinder). */
	public static final String HEIGHT = "height";
	/** Length of a line wave / beam / reach of a hitscan. */
	public static final String LENGTH = "length";
	/** Half width of a line wave / beam. */
	public static final String HALF_WIDTH = "halfWidth";
	public static final String MAX_TARGETS = "maxTargets";
	/** Main damage of the ability (the shatter, the hit, the impact). */
	public static final String DAMAGE = "damage";
	/** Second damage value (line hit before the burst, tornado tick). */
	public static final String DAMAGE2 = "damage2";
	public static final String KNOCKBACK = "knockback";
	/** Slowness amplifier (0 = Slowness I) and its duration. */
	public static final String SLOW_AMP = "slowAmp";
	public static final String STATUS_TICKS = "statusTicks";
	/** Range of the aim ray that picks the target point. */
	public static final String AIM_RANGE = "aimRange";
	/** Entities frozen by the cast are only shattered while still within this distance of the caster. */
	public static final String SPLIT_RADIUS = "splitRadius";
	/** Temporary blocks one cast may place (the per-player cap of the world journal still applies). */
	public static final String MAX_BLOCKS = "maxBlocks";
	/** Tornado radius around the caster (Byakuya scatter). */
	public static final String TORNADO_RADIUS = "tornadoRadius";

	private AbilityParams() {
	}
}
