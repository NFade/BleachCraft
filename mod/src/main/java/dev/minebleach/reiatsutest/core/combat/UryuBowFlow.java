package dev.minebleach.reiatsutest.core.combat;

import java.util.function.ToIntFunction;

/**
 * The pull and release state machine of one Uryu bow (third person animation and first person hands): which animation plays, at which tick, and how
 * it is blended with the one before. Pure maths with the time given by the caller (seconds), so it is unit tested without the game.
 *
 * <p>States: {@link Phase#IDLE} (the drawn bow in the hand, pose P1), {@link Phase#DRAW} (the use key is held: the animation tick IS the draw time in
 * ticks, held at the last key for as long as the key is held, the tremor is added by the pose), {@link Phase#RELEASE} (the key was let go: the
 * release animation plays from the full draw pose, a shorter draw blends from the pose it had), {@link Phase#ABILITY} (an ability gesture, see
 * {@link UryuAnims}) and {@link Phase#OFF} (the sealed pendant or another item: the whole pose is faded out). A change of animation blends the old one
 * (frozen at the tick it had) into the new one over {@link #XFADE}; a whole pose fades in over {@link #FADE_IN} and out over {@link #FADE_OUT}.
 */
public final class UryuBowFlow {
	public enum Phase { OFF, IDLE, DRAW, RELEASE, ABILITY }

	public static final double TPS = 20.0;
	/** Seconds a change of animation takes to blend. */
	public static final double XFADE = 0.10;
	public static final double FADE_IN = 0.12;
	public static final double FADE_OUT = 0.15;
	/** A draw shorter than this many ticks is cancelled, not released (no release animation). */
	public static final double MIN_RELEASE_TICKS = 1.0;
	/** A cast start event this many seconds old makes the event of the same ability not restart its animation. */
	public static final double CAST_START_WINDOW = 3.0;

	private final ToIntFunction<String> endTicks;
	private Phase phase = Phase.OFF;
	private String anim = UryuAnims.IDLE;
	private double animStart;
	private double animStartTick;
	private String prevAnim;
	private double prevTick;
	private double xfadeStart;
	private double weight;
	private double last = Double.NaN;
	private boolean wasUsing;
	private double useTicks;
	private String castAnim;
	private double castAt;

	/** @param endTicks end tick of an animation by name (the track's {@code endTick}); 0 for an unknown name ends it at once */
	public UryuBowFlow(ToIntFunction<String> endTicks) {
		this.endTicks = endTicks;
	}

	public Phase phase() {
		return phase;
	}

	public String anim() {
		return anim;
	}

	/** Weight 0..1 of the whole pose against the vanilla pose (fades in and out). */
	public double weight() {
		return weight;
	}

	/** True while the pose changes the model (the layer is active, including the fade out). */
	public boolean active() {
		return weight > 0.0 || phase != Phase.OFF;
	}

	/** Animation tick of the current animation at {@code now}. */
	public double tick(double now) {
		if (phase == Phase.DRAW) {
			return Math.min(useTicks, endTicks.applyAsInt(UryuAnims.DRAW));
		}
		return Math.min(animStartTick + (now - animStart) * TPS, endTicks.applyAsInt(anim));
	}

	/** Previous animation (being blended out) or null. */
	public String prevAnim() {
		return prevAnim;
	}

	public double prevTick() {
		return prevTick;
	}

	/** Blend 0..1 (smoothstep) from the previous animation to the current one; 1 when there is no previous. */
	public double xfade(double now) {
		if (prevAnim == null) {
			return 1.0;
		}
		double u = Math.max(0.0, Math.min(1.0, (now - xfadeStart) / XFADE));
		return u * u * (3.0 - 2.0 * u);
	}

	private void switchTo(Phase p, String name, double startTick, double now) {
		if (phase != Phase.OFF && weight > 0.0) {
			prevAnim = anim;
			prevTick = tick(now);
			xfadeStart = now;
		} else {
			prevAnim = null;
		}
		phase = p;
		anim = name;
		animStart = now;
		animStartTick = startTick;
	}

	/**
	 * Per frame. {@code holdingBow}: the main hand holds the drawn bow (not the sealed pendant); {@code using}: the use key is held on it;
	 * {@code useTicks}: ticks it has been held (with the partial tick).
	 */
	public void update(double now, boolean holdingBow, boolean using, double useTicks) {
		double dt = Double.isNaN(last) ? 0.0 : Math.max(0.0, Math.min(0.25, now - last));
		last = now;
		if (!holdingBow) {
			weight = Math.max(0.0, weight - dt / FADE_OUT);
			if (weight <= 0.0) {
				phase = Phase.OFF;
				prevAnim = null;
				anim = UryuAnims.IDLE;
			}
			wasUsing = false;
			return;
		}
		weight = Math.min(1.0, weight + dt / FADE_IN);
		if (phase == Phase.OFF) {
			phase = Phase.IDLE;
			anim = UryuAnims.IDLE;
			animStart = now;
			animStartTick = 0.0;
			prevAnim = null;
		}
		if (using) {
			this.useTicks = useTicks;
			if (phase != Phase.DRAW && phase != Phase.ABILITY) {
				switchTo(Phase.DRAW, UryuAnims.DRAW, 0.0, now);
			} else if (phase == Phase.ABILITY && tick(now) >= endTicks.applyAsInt(anim)) {
				switchTo(Phase.DRAW, UryuAnims.DRAW, 0.0, now);
			}
		} else if (wasUsing && phase == Phase.DRAW) {
			if (this.useTicks >= MIN_RELEASE_TICKS) {
				switchTo(Phase.RELEASE, UryuAnims.RELEASE, 0.0, now);
			} else {
				switchTo(Phase.IDLE, UryuAnims.IDLE, 0.0, now);
			}
		}
		wasUsing = using;
		if ((phase == Phase.RELEASE || phase == Phase.ABILITY) && tick(now) >= endTicks.applyAsInt(anim) && !using) {
			switchTo(Phase.IDLE, UryuAnims.IDLE, 0.0, now);
		}
		if (prevAnim != null && now - xfadeStart >= XFADE) {
			prevAnim = null;
		}
	}

	/** An ability gesture starts its animation (ignored while the bow is not held). */
	public void trigger(String animName, int startTick, double now) {
		if (weight <= 0.0 || phase == Phase.OFF) {
			return;
		}
		switchTo(Phase.ABILITY, animName, startTick, now);
	}

	/**
	 * An effect event of an Uryu ability for this player. {@code castStart}: the cast start event (the key press of Licht Regen and Sternenstaub, effect 78),
	 * which starts the animation from its first tick; the ability event itself then does not restart that animation when it comes within
	 * {@link #CAST_START_WINDOW} seconds (the animation already runs since the key press); without a cast start (an old server, or an ability that has none)
	 * the animation starts at the tick {@link UryuAnims.Meta#startTick()}, a few ticks before the release.
	 *
	 * @return true when the animation was (re)started
	 */
	public boolean onAbility(UryuAnims.Meta m, boolean castStart, double now) {
		if (m == null) {
			return false;
		}
		if (castStart) {
			castAnim = m.anim();
			castAt = now;
			trigger(m.anim(), 0, now);
			return true;
		}
		if (m.anim().equals(castAnim) && now - castAt < CAST_START_WINDOW) {
			castAnim = null;
			return false;
		}
		trigger(m.anim(), m.startTick(), now);
		return true;
	}

	/**
	 * Progress 0..1 of the raise or the draw before the effect event of an ability that has a cast start (Licht Regen, Sternenstaub: the animation runs since
	 * the key press and the first person gesture of the ability only starts at the event), or -1 when the flow is not in that phase.
	 */
	public double preEvent(double now) {
		if (phase != Phase.ABILITY) {
			return -1.0;
		}
		UryuAnims.Meta m = UryuAnims.meta(anim);
		if (m == null || m.startTick() <= 0 || m.eventTick() <= 0) {
			return -1.0;
		}
		double t = tick(now);
		return t >= m.eventTick() ? -1.0 : Math.max(0.0, t / m.eventTick());
	}
}
