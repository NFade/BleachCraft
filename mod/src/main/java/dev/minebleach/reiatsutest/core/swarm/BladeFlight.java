package dev.minebleach.reiatsutest.core.swarm;

/**
 * Server side flight of the Senbonzakura attack swarm (the head of the blade ribbon). Pure logic, one {@link #step} per server tick.
 *
 * <p>The head is stored as a direction and a distance from the caster's eye, so it travels with the caster. Each tick it turns
 * toward the target direction by at most {@link FlightConfig#turnRadPerTick} (also limited so that its sideways speed stays below
 * the flight speed: a far swarm cannot whip around) and closes the distance by at most {@link FlightConfig#speedPerTick}. The target
 * is a world point: the aimed point at the cast, then (while the key is held) the point the caster looks at. Phases:
 * WINDUP (blades still at the hand), OUT (flying to the target), LOITER (arrived, cutting there), BACK (returning to the caster),
 * DONE. After {@code returnTick} the swarm returns as soon as the key is not held, at {@code maxTicks} in any case. Releasing the
 * key just freezes the target where it was: the swarm finishes that trajectory and returns as before.
 *
 * <p>The return ignores blocks ({@link #clipHead} does nothing in BACK): it takes a fixed number of ticks (at most {@link #maxBackTicks}, a
 * watchdog ends it even then), so the head cannot get stuck in a wall. Outward, a head held at a block for {@link #STUCK_TICKS} ticks is recalled.
 */
public final class BladeFlight {
	public enum Phase {
		WINDUP(0), OUT(1), LOITER(2), BACK(3), DONE(4);

		public final int code;

		Phase(int code) {
			this.code = code;
		}

		public static Phase fromCode(int code) {
			for (Phase p : values()) {
				if (p.code == code) {
					return p;
				}
			}
			return DONE;
		}
	}

	/** One tick of movement: the segment the head covered, the phase after the step, and whether the segment may hurt. */
	public record Step(V3 from, V3 to, Phase phase, boolean sweep, int leg) {
	}

	/** Distance from the eye at which the blades leave the hand. */
	public static final double START_R = 1.0;
	/** The head counts as arrived within this distance of the target, and leaves LOITER again beyond twice of it. */
	public static final double ARRIVE = 0.6;
	/** BACK ends when the head is this close to the caster's eye. */
	public static final double BACK_DONE_R = 1.0;
	/** The head was held at a block (moved less than {@link #STUCK_MOVE} per tick) for this many ticks: the swarm is recalled (BACK). */
	public static final int STUCK_TICKS = 8;
	public static final double STUCK_MOVE = 0.1;
	/** Extra ticks on top of the longest possible return before the watchdog ends the flight by force. */
	public static final int BACK_GRACE_TICKS = 6;

	private final FlightConfig cfg;
	private final double maxRange;
	private V3 dir;
	private double r;
	private V3 target;
	private V3 head;
	private Phase phase = Phase.WINDUP;
	private int tick;
	private boolean held;
	private boolean steered;
	private int backTicks;
	private int stuckTicks;
	private boolean clippedThisTick;
	private V3 stepFrom;

	public BladeFlight(FlightConfig cfg, double maxRange, V3 eye, V3 look, V3 target) {
		this.cfg = cfg;
		this.maxRange = Math.max(cfg.minRange() + 0.5, maxRange);
		this.dir = look.normalizedOr(new V3(0, 0, 1));
		this.r = START_R;
		this.target = target;
		this.head = eye.add(dir.scale(r));
	}

	public V3 head() {
		return head;
	}

	public V3 target() {
		return target;
	}

	public Phase phase() {
		return phase;
	}

	public int ticks() {
		return tick;
	}

	public boolean done() {
		return phase == Phase.DONE;
	}

	/** True once steering moved the target at least once (the landing burst then follows the head, not the cast point). */
	public boolean steered() {
		return steered;
	}

	public boolean held() {
		return held;
	}

	/** Is the control key held right now (validated by the server). Keeps the swarm out past {@code returnTick}. */
	public void setHeld(boolean held) {
		this.held = held;
	}

	/** New world target from steering. */
	public void steerTo(V3 worldTarget) {
		if (worldTarget.isFinite()) {
			this.target = worldTarget;
			this.steered = true;
		}
	}

	/** Moves the head one tick. {@code eye} is the caster's eye now. */
	public Step step(V3 eye) {
		V3 from = head;
		if (phase == Phase.DONE) {
			return new Step(from, from, phase, false, HitCooldown.LEG_BACK);
		}
		tick++;
		stepFrom = from;
		if (!clippedThisTick) {
			stuckTicks = 0;
		}
		clippedThisTick = false;
		if (phase == Phase.WINDUP) {
			if (tick <= cfg.startDelayTicks()) {
				head = eye.add(dir.scale(r));
				return new Step(from, head, phase, false, HitCooldown.LEG_OUT);
			}
			phase = Phase.OUT;
		}
		if (phase != Phase.BACK && (tick >= cfg.maxTicks() || (tick >= cfg.returnTick() && !held))) {
			phase = Phase.BACK;
		}
		if (phase == Phase.BACK) {
			backTicks++;
			r -= cfg.backSpeedPerTick();
			if (r <= BACK_DONE_R || backTicks > maxBackTicks()) {
				r = BACK_DONE_R;
				phase = Phase.DONE;
			}
		} else {
			V3 tv = target.sub(eye);
			double rt = Math.max(cfg.minRange(), Math.min(maxRange, tv.length()));
			double turn = SteerMath.maxTurnPerTick(cfg.turnRadPerTick(), cfg.speedPerTick(), r);
			dir = SteerMath.turnToward(dir, tv.normalizedOr(dir), turn);
			r = SteerMath.stepToward(r, rt, cfg.speedPerTick());
		}
		head = eye.add(dir.scale(r));
		if (phase == Phase.OUT || phase == Phase.LOITER) {
			double d = head.distanceTo(target);
			phase = (phase == Phase.LOITER ? d <= ARRIVE * 2.0 : d <= ARRIVE) ? Phase.LOITER : Phase.OUT;
		}
		boolean teleport = from.distanceTo(head) > cfg.maxSweepPerTick();
		int leg = phase == Phase.BACK || phase == Phase.DONE ? HitCooldown.LEG_BACK : HitCooldown.LEG_OUT;
		return new Step(from, head, phase, !teleport, leg);
	}

	/** The most ticks a return can take: the longest possible distance at the return speed plus a grace (the watchdog of the BACK phase). */
	public int maxBackTicks() {
		return (int) Math.ceil(maxRange / cfg.backSpeedPerTick()) + BACK_GRACE_TICKS;
	}

	/** Ticks the head has been returning (0 before BACK). */
	public int backTicks() {
		return backTicks;
	}

	/** True while the head is held at a block and has not moved on (counts toward {@link #STUCK_TICKS}). */
	public int stuckTicks() {
		return stuckTicks;
	}

	/** Turns the swarm back at once (the server uses it when the head cannot get on). No effect after BACK has started. */
	public void recall() {
		if (phase == Phase.WINDUP || phase == Phase.OUT || phase == Phase.LOITER) {
			phase = Phase.BACK;
		}
	}

	/**
	 * The server stopped the head at a block: it continues from {@code clipped} (the direction and distance follow). Only outward: the return
	 * (BACK, DONE) ignores blocks, the petals fly back through them like spirits, so a head can never stick in a wall on the way home. A head that
	 * stays at a block for {@link #STUCK_TICKS} ticks without reaching its target (OUT, it moves less than {@link #STUCK_MOVE} per tick) is recalled.
	 */
	public void clipHead(V3 eye, V3 clipped) {
		if (phase == Phase.BACK || phase == Phase.DONE) {
			return;
		}
		clippedThisTick = true;
		if (phase == Phase.OUT && stepFrom != null && stepFrom.distanceTo(clipped) < STUCK_MOVE) { // (hanging at the target next to a wall is LOITER, not stuck)
			if (++stuckTicks >= STUCK_TICKS) {
				recall();
			}
		} else {
			stuckTicks = 0;
		}
		V3 v = clipped.sub(eye);
		r = Math.max(0.3, v.length());
		dir = v.normalizedOr(dir);
		head = eye.add(dir.scale(r));
	}
}
