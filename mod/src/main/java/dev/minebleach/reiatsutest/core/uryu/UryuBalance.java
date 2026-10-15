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
	/** Arrows of one world alive at once (a safety cap; the Licht Regen alone keeps about 40 alive). */
	public static final int MAX_ARROWS = 400;

	/** Basic shot of the BASE state (right click), HP. */
	public static final float BASIC_DAMAGE = 3.0F; // R13: was 4.0; the live value is BowDraw.tuning().damage()
	/** Factor of the basic shot while Blut Arterie is on (SHIKAI and BANKAI, +20 percent). */
	public static final double ARTERIE_SHOT_FACTOR = 1.2;
	/** Ticks between two basic shots (anti spam only; the draw time is the real pause). */
	public static final int BASIC_COOLDOWN = 14; // R13: was 4 (anti spam); now a real fire rate limit, uryu.bow.cooldownTicks
	/** Heilig Pfeil: homing arrow (Z), HP, base damage before Focus. */
	public static final float FOCUS_SHOT_DAMAGE = 7.0F;
	/** Speed of the Z arrow in blocks per tick (faster than the basic shot, before Focus). */
	public static final double Z_ARROW_SPEED = 2.4;
	/** Life of the Z arrow in ticks (it has to be able to catch a target that runs). */
	public static final int Z_ARROW_LIFE = 60;

	// ---------------------------------------------------------------- bow (revision 2: vanilla draw, no slowdown)
	/** Ticks of drawing for full power (vanilla: 20). */
	public static final int BOW_FULL_DRAW_TICKS = 24; // R13: was 20; uryu.bow.fullDrawTicks
	/** A release under this power fires nothing (vanilla: 0.1). */
	public static final double BOW_MIN_POWER = 0.5; // R13: was 0.1; uryu.bow.minPower
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

	// ---------------------------------------------------------------- shunpo
	/** Blocks added to the normal shunpo distance for Uryu (the Quincy flavour: Hirenkyaku). */
	public static final double SHUNPO_BONUS = 2.0;

	// ---------------------------------------------------------------- Sprenger (H, SHIKAI)
	public static final double SPRENGER_RANGE = 20.0;
	public static final double SPRENGER_RADIUS = 3.0;
	/** The legs are bound 0.8 s after the cast. */
	public static final int SPRENGER_BIND_AT = 16;
	/**
	 * Ticks the ROOT lasts (round 2): from the bind moment to the explosion, 1.2 s. Nobody inside can move horizontally or jump and a
	 * player cannot shunpo; any damage to the rooted one ends it early (the explosion itself ends it anyway).
	 */
	public static final int SPRENGER_BIND_TICKS = 24;
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

	// ---------------------------------------------------------------- Seele Schneider (B, SHIKAI; replaces Hirenkyaku)
	/** Forward lunge in blocks (velocity based, no teleport): 3 ticks of {@link #SEELE_LUNGE_SPEED}, then the friction of the ground. */
	public static final double SEELE_LUNGE = 1.5;
	public static final int SEELE_LUNGE_TICKS = 3;
	public static final double SEELE_LUNGE_SPEED = 0.36;
	/** Three slashes, 5 ticks (0.25 s) apart, the first right after the lunge. */
	public static final int SEELE_SLASHES = 3;
	public static final int SEELE_FIRST_SLASH = 3;
	public static final int SEELE_SLASH_GAP = 5;
	/** Reach in blocks and the full opening angle in degrees of the cone of one slash (centred on the look). */
	public static final double SEELE_REACH = 3.5;
	public static final double SEELE_CONE_DEG = 100.0;
	public static final float SEELE_DAMAGE = 2.5F;
	/** The third slash also knocks back. */
	public static final double SEELE_KNOCKBACK = 1.0;
	/** Ticks after the cast in which Focus is neither built nor reset (the lunge moves the player; Focus is not consumed either). */
	public static final int SEELE_FOCUS_GRACE = 12;

	// ---------------------------------------------------------------- Wing barrage (Z, BANKAI; replaces Licht Regen)
	/** The wings fire for 6 s. */
	public static final int WING_TICKS = 120;
	/** Targets are the nearest valid enemies within this many blocks of Uryu, at most one per wing slot. */
	public static final double WING_RANGE = 30.0;
	public static final int WING_SLOTS = 4;
	/** The targets are chosen again every 8 ticks; every target gets one arrow per cycle (0.4 s), the slots staggered by {@link #WING_STAGGER}. */
	public static final int WING_CYCLE = 8;
	public static final int WING_STAGGER = 2;
	public static final float WING_DAMAGE = 2.0F;
	public static final double WING_SPEED = 2.2;
	public static final int WING_LIFE = 50;
	/** Strong homing of a wing arrow, degrees per tick. */
	public static final double WING_TURN_DEG = 12.0;
	/** Wing tips relative to the feet in the body frame: sideways (right positive), height, backwards, for the slots upper right, upper left, lower right, lower left. */
	public static final double[][] WING_TIPS = {{1.7, 2.35, 0.45}, {-1.7, 2.35, 0.45}, {1.25, 1.2, 0.6}, {-1.25, 1.2, 0.6}};

	// ---------------------------------------------------------------- Licht Regen (H, BANKAI; round 2, replaces the Homing Volley)
	/** Range in blocks of the aimed point (the server aim at the END of the 0.6 s cast time, like Hakuren and the Byakuya impact). */
	public static final double RAIN_RANGE = 28.0;
	/** The area is a SQUARE of 9 x 9 blocks, axis aligned to the world grid and centred on the aimed point. */
	public static final double RAIN_HALF_WIDTH = 4.5;
	/** The arrows start this many blocks above the ground level of the centre. */
	public static final double RAIN_HEIGHT = 14.0;
	/** The rain falls for 3.0 s. */
	public static final int RAIN_TICKS = 60;
	/** Arrows that start every tick (360 per cast). */
	public static final int RAIN_PER_TICK = 6;
	public static final double RAIN_SPEED = 2.6;
	/** Horizontal share of the speed (the slant): the arrows fall a little aslant, the same way for the whole cast (direction from the seed). */
	public static final double RAIN_SLANT = 0.12;
	/** Life of a rain arrow in ticks (about 31 blocks of fall, enough to pass the ground level of a pit). */
	public static final int RAIN_LIFE = 12;
	public static final float RAIN_DAMAGE = 1.0F;
	/** Hit radius of a rain arrow (a little fatter than the shot, so a stationary player in the square takes about 8 to 9 HP in all). */
	public static final double RAIN_ARROW_RADIUS = 0.4;
	/** Most HP one target can take from one cast. */
	public static final float RAIN_TARGET_CAP = 14.0F;

	// ---------------------------------------------------------------- Sternenstaub (B, BANKAI)
	/** Stacks of Focus the cast needs (and consumes). */
	public static final int STAR_FOCUS = 3;
	public static final double STAR_LENGTH = 40.0;
	/** Width 2. */
	public static final double STAR_HALF_WIDTH = 1.0;
	public static final float STAR_FIRST = 20.0F;
	public static final float STAR_FOLLOW = 12.0F;
	public static final int STAR_MAX_TARGETS = 16;
	/**
	 * S-U1: the 0.8 s charge. The cast resolves at the RELEASE, this many ticks after the key press (aim and Focus are read then, like Licht
	 * Regen); the key press only starts the visuals. No damage, cost or cooldown changed.
	 */
	public static final int STAR_CHARGE_TICKS = 16;
	/** S-U1: a charge whose Focus dropped below {@link #STAR_FOCUS} before the release fizzles and keeps only this share of the cooldown. */
	public static final double STAR_FIZZLE_COOLDOWN_SHARE = 0.5;

	/** Damage and speed factor of an arrow shot with {@code stacks} of Focus (clamped to 0..3). */
	public static double focusFactor(int stacks) {
		return FOCUS_FACTOR[Math.max(0, Math.min(FOCUS_MAX, stacks))];
	}
}
