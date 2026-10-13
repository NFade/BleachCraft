package dev.minebleach.reiatsutest.core.gin;

/**
 * Names of the Gin-only numbers stored in {@code AbilitySpec.params()} (design/GIN.md section 4). The shared names (length, half width,
 * damage, knockback, radius, max targets, slow amplifier) stay in {@code AbilityParams}. All times are ticks, lengths blocks, damage HP.
 */
public final class GinParams {
	/** Ticks the caster is rooted, counted from the phase that applies the root. */
	public static final String ROOT_TICKS = "rootTicks";
	/** Slowness amplifier of the cast root (3 = Slowness IV); the movement-speed modifier does the real rooting. */
	public static final String ROOT_AMP = "rootAmp";
	/** Opening angle of the Hyapponzashi sweep in degrees and the half height of the sweep volume. */
	public static final String ARC_DEGREES = "arcDegrees";
	public static final String ARC_HEIGHT = "arcHeight";
	/** Snake Hook: the caster stops this far from the anchor, pulls at {@code pullSpeed} blocks per tick for at most {@code pullTicks}. */
	public static final String STOP_SHORT = "stopShort";
	public static final String PULL_SPEED = "pullSpeed";
	public static final String PULL_TICKS = "pullTicks";
	/** Korose: life of the sliver, the least damage the thrust must really deal to leave it, and the reach of the recast. */
	public static final String SLIVER_TICKS = "sliverTicks";
	public static final String SLIVER_MIN_HP = "sliverMinHp";
	public static final String RECAST_RANGE = "recastRange";
	/** Korose recast: channel length, the dissolve damage and the cooldowns that follow a dissolve / an interrupted channel. */
	public static final String CHANNEL_TICKS = "channelTicks";
	public static final String DISSOLVE_DAMAGE = "dissolveDamage";
	public static final String COOLDOWN_DISSOLVE = "cooldownDissolve";
	public static final String COOLDOWN_INTERRUPT = "cooldownInterrupt";
	/** One hit of at least this many HP on the channelling caster interrupts it; horizontal push (blocks) that counts as knockback. */
	public static final String INTERRUPT_HP = "interruptHp";
	public static final String PUSH_LIMIT = "pushLimit";
	/** 1 = an interrupted channel keeps the sliver until its own timer; 0 (default) = the interrupt spends it. */
	public static final String INTERRUPT_KEEPS_MARK = "interruptKeepsMark";
	/** Hakufuku: hide time (Invisibility) and the window in which the next thrust shows no white flash. */
	public static final String HIDE_TICKS = "hideTicks";
	public static final String FLASH_TICKS = "flashTicks";

	private GinParams() {
	}
}
