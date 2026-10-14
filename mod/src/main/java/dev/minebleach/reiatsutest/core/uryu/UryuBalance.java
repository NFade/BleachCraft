package dev.minebleach.reiatsutest.core.uryu;

/**
 * Every number of Uryu Ishida's gameplay (design/URYU.md sections 3 and 5), kept here and not in the shared
 * {@code BalanceConfig} so that file stays untouched. Lengths are blocks, damage is HP (1 HP = half a heart), times are ticks
 * (20 per second). First guesses; tune here.
 */
public final class UryuBalance {
	private UryuBalance() {
	}

	// ---------------------------------------------------------------- Focus (design section 3)
	public static final int FOCUS_MAX = 3;
	/** Ticks of standing still per stack (0.7 s) in BASE. */
	public static final int FOCUS_INTERVAL = 14;
	/** The same in SHIKAI and BANKAI (Blut Arterie, 0.5 s). */
	public static final int FOCUS_INTERVAL_ARTERIE = 10;
	/** Blocks per tick the player may move and still count as standing still (design: speed &lt; 0.02). */
	public static final double FOCUS_MOVE_EPS = 0.02;
	/** Horizontal blocks per tick below which a SNEAKING walk still keeps Focus (normal sneak speed is about 0.065). */
	public static final double FOCUS_SNEAK_SPEED = 0.09;
	/** Ticks after a shunpo in which Focus is neither built nor reset (the teleport is not walking). */
	public static final int FOCUS_GRACE_TICKS = 3;
	/** Damage and speed factor by stack count 0..3 (+25, +60, +100 percent). */
	private static final double[] FOCUS_FACTOR = {1.0, 1.25, 1.6, 2.0};
	/** From this many stacks the arrow pierces one extra target and ignores gravity. */
	public static final int FOCUS_FULL = 3;

	// ---------------------------------------------------------------- arrows
	/** Blocks per tick of an unfocused Heilig Pfeil (design: 2.2). */
	public static final double ARROW_SPEED = 2.2;
	/** Downward acceleration per tick squared ("gravity small"; vanilla arrows use 0.05). */
	public static final double ARROW_GRAVITY = 0.012;
	/** Half thickness of the hit shape of an arrow. */
	public static final double ARROW_RADIUS = 0.3;
	/** Life of a shot arrow in ticks (about 110 blocks of range). */
	public static final int ARROW_LIFE = 50;
	/** Arrows of one world alive at once (a safety cap). */
	public static final int MAX_ARROWS = 256;

	/** Basic shot of the BASE state (right click), HP. */
	public static final float BASIC_DAMAGE = 4.0F;
	/** Factor of the basic shot while Blut Arterie is on (SHIKAI and BANKAI, +20 percent). */
	public static final double ARTERIE_SHOT_FACTOR = 1.2;
	/** Ticks between two basic shots (anti spam only; the draw time is the real pause). */
	public static final int BASIC_COOLDOWN = 4;
	/** Heilig Pfeil: homing arrow (Z), HP, base damage before Focus. */
	public static final float FOCUS_SHOT_DAMAGE = 7.0F;
	/** Speed of the Z arrow in blocks per tick (faster than the basic shot, before Focus). */
	public static final double Z_ARROW_SPEED = 2.4;
	/** Life of the Z arrow in ticks (it has to be able to catch a target that runs). */
	public static final int Z_ARROW_LIFE = 60;

	// ---------------------------------------------------------------- bow (revision 2: vanilla draw, no slowdown)
	/** Ticks of drawing for full power (vanilla: 20). */
	public static final int BOW_FULL_DRAW_TICKS = 20;
	/** A release under this power fires nothing (vanilla: 0.1). */
	public static final double BOW_MIN_POWER = 0.1;
	/** Share of the full arrow speed that even a minimal draw keeps (speed factor = this + (1 - this) * power). */
	public static final double BOW_MIN_SPEED_SHARE = 0.3;

	// ---------------------------------------------------------------- homing (revision 2: every arrow homes)
	/** Passive homing of the basic shot: degrees per tick, radius in blocks, half opening angle of the cone in degrees. */
	public static final double BASIC_HOMING_DEG = 2.5;
	public static final double BASIC_HOMING_RADIUS = 14.0;
	public static final double BASIC_HOMING_CONE_DEG = 60.0;
	/** Strong homing of the Z arrow. */
	public static final double Z_HOMING_DEG = 10.0;
	public static final double Z_HOMING_RADIUS = 20.0;
	public static final double Z_HOMING_CONE_DEG = 100.0;
	/** A locked target is kept until it is farther than radius times this (or hidden behind a block). */
	public static final double HOMING_KEEP_FACTOR = 1.25;

	// ---------------------------------------------------------------- Sprenger (H, SHIKAI)
	public static final double SPRENGER_RANGE = 20.0;
	public static final double SPRENGER_RADIUS = 3.0;
	/** The legs are bound 0.8 s after the cast. */
	public static final int SPRENGER_BIND_AT = 16;
	/** Ticks the bind lasts (2 s). */
	public static final int SPRENGER_BIND_TICKS = 40;
	/** Slowness amplifier of the bind (4 = Slowness V). */
	public static final int SPRENGER_SLOW_AMP = 4;
	/** The pentagon explodes 2.0 s after the cast. */
	public static final int SPRENGER_EXPLODE_AT = 40;
	public static final float SPRENGER_DAMAGE = 9.0F;
	/** Factor of the damage when a fully focused arrow detonates the pentagon at once. */
	public static final double SPRENGER_EARLY_FACTOR = 1.5;
	/** How far above the floor the pentagon still counts (blocks), and how far below. */
	public static final double SPRENGER_HEIGHT = 3.0;
	public static final double SPRENGER_BELOW = 0.6;
	/** Pentagons of one player alive at once. */
	public static final int SPRENGER_MAX_PER_PLAYER = 2;

	// ---------------------------------------------------------------- Hirenkyaku (B, SHIKAI)
	public static final double DASH_BLOCKS = 8.0;
	/** Ticks the dash velocity is applied (8 blocks at 2 per tick). */
	public static final int DASH_TICKS = 4;
	/** The fall is cancelled for this many ticks from the cast (0.6 s). */
	public static final int HOVER_TICKS = 12;
	/** The vertical share of the look that the dash follows. */
	public static final double DASH_MIN_Y = -0.3;
	public static final double DASH_MAX_Y = 0.5;

	// ---------------------------------------------------------------- Licht Regen (Z, BANKAI)
	public static final double RAIN_RANGE = 28.0;
	public static final double RAIN_RADIUS = 4.0;
	public static final int RAIN_ARROWS = 12;
	/** The 12 arrows leave over 1.2 s. */
	public static final int RAIN_TICKS = 24;
	public static final float RAIN_DAMAGE = 1.2F;
	/** Height above the landing point where an arrow starts, and its fall speed. */
	public static final double RAIN_DROP_HEIGHT = 10.0;
	public static final double RAIN_SPEED = 2.2;
	/** Scatter around a picked target (blocks); with no target the arrows land uniformly in the circle. */
	public static final double RAIN_JITTER = 0.6;

	// ---------------------------------------------------------------- Homing Volley (H, BANKAI)
	public static final int VOLLEY_BASE_ARROWS = 3;
	public static final int VOLLEY_MAX_ARROWS = 6;
	public static final float VOLLEY_DAMAGE = 3.0F;
	/** 1.5 s of life. */
	public static final int VOLLEY_LIFE = 30;
	public static final double VOLLEY_CONE_DEG = 15.0;
	public static final double VOLLEY_LOCK_RANGE = 30.0;
	/** Turn rate of a homing arrow, degrees per tick. */
	public static final double VOLLEY_TURN_DEG = 12.0;
	public static final double VOLLEY_SPEED = 1.8;
	/** Half opening angle of the fan the arrows leave in, degrees. */
	public static final double VOLLEY_SPREAD_DEG = 20.0;

	// ---------------------------------------------------------------- Sternenstaub (B, BANKAI)
	/** Stacks of Focus the cast needs (and consumes). */
	public static final int STAR_FOCUS = 3;
	public static final double STAR_LENGTH = 40.0;
	/** Width 2. */
	public static final double STAR_HALF_WIDTH = 1.0;
	public static final float STAR_FIRST = 20.0F;
	public static final float STAR_FOLLOW = 12.0F;
	public static final int STAR_MAX_TARGETS = 16;

	/** Damage and speed factor of an arrow shot with {@code stacks} of Focus (clamped to 0..3). */
	public static double focusFactor(int stacks) {
		return FOCUS_FACTOR[Math.max(0, Math.min(FOCUS_MAX, stacks))];
	}
}
