package dev.minebleach.reiatsutest.core.fx;

/**
 * Phases of the standing Byakuya blade rows after a bankai ability (VFX_STORYBOARD 6.2, 6.3, S3): every blade is cut from the tip down
 * (the "burning dissolve", its petals are born at the cut), stays gone, and regrows from the ground up. Everything is a pure function of
 * the time since the phase start and the blade's distance from the release point, so a late joiner (S3: {@code phaseKind}, {@code phaseTime})
 * reconstructs the exact state. The result is the remaining blade FRACTION (1 = standing, 0 = gone); the cut height is
 * {@code fraction * 8 m}. Petal birth (cut passes height y while dissolving) and petal consumption (regrowth passes y) are derived here
 * so the petals of {@link StormMath} and {@link CondenseMath} meet the blade edge exactly.
 */
public final class RowsPhase {
	public static final byte STANDING = 0;
	public static final byte SCATTERED = 32;
	public static final byte CONSUMED = 33;
	public static final double BLADE_LENGTH = 8.0;

	/** What a blade is doing at one moment. */
	public enum Stage { STANDING, DISSOLVING, GONE, REGROWING }

	/** Timing of one phase (seconds from the phase start). */
	public static final class Timing {
		/** Dissolve of the blade at distance d starts at {@code cutStart + cutPerBlock d} and takes {@code cutSpan}. */
		public final double cutStart;
		public final double cutPerBlock;
		public final double cutSpan;
		/** Regrowth of the blade at distance d starts at {@code reformStart + reformPerBlock d} and takes {@code reformSpan}. */
		public final double reformStart;
		public final double reformPerBlock;
		public final double reformSpan;

		public Timing(double cutStart, double cutPerBlock, double cutSpan, double reformStart, double reformPerBlock, double reformSpan) {
			this.cutStart = cutStart;
			this.cutPerBlock = cutPerBlock;
			this.cutSpan = Math.max(1e-3, cutSpan);
			this.reformStart = reformStart;
			this.reformPerBlock = reformPerBlock;
			this.reformSpan = Math.max(1e-3, reformSpan);
		}

		/** 6.2 scatter: cut 8 to 0 over 0.4 s from 0.3 + 0.008 d, regrow 0 to 8 over 0.6 s from 4.5 + 0.004 d. */
		public static Timing scatter() {
			return new Timing(0.30, 0.008, 0.40, 4.5, 0.004, 0.6);
		}

		/** 6.3 Hakuteiken: the rows dissolve fast (0.3 s, from 0 + 0.002 d), regrow from 3.0 + 0.004 d over 0.6 s. */
		public static Timing consumed() {
			return new Timing(0.0, 0.002, 0.30, 3.0, 0.004, 0.6);
		}

		public static Timing of(byte phaseKind) {
			return phaseKind == CONSUMED ? consumed() : scatter();
		}

		public double cutBegin(double dist) {
			return cutStart + cutPerBlock * dist;
		}

		public double reformBegin(double dist) {
			return reformStart + reformPerBlock * dist;
		}

		/** Time at which the dissolving cut of a blade passes the height {@code y} (0..8 m). */
		public double cutPassesAt(double dist, double y) {
			return cutBegin(dist) + cutSpan * (1.0 - y / BLADE_LENGTH);
		}

		/** Time at which the regrowing blade reaches the height {@code y}. */
		public double regrowReaches(double dist, double y) {
			return reformBegin(dist) + reformSpan * (y / BLADE_LENGTH);
		}

		/** Time at which the last blade (distance {@code maxDist}) is whole again: the phase is over. */
		public double end(double maxDist) {
			return reformBegin(maxDist) + reformSpan;
		}

		/** Time of the first regrowth anywhere (the glow edge of regrowth starts here). */
		public double firstReform() {
			return reformStart;
		}
	}

	private RowsPhase() {
	}

	private static double clamp(double x) {
		return x < 0 ? 0 : Math.min(x, 1);
	}

	/** Remaining fraction (1 = whole, 0 = gone) of a blade at distance {@code dist}, {@code tau} seconds after the phase start. */
	public static double fraction(Timing t, double tau, double dist) {
		if (tau < t.reformBegin(dist)) {
			// before the regrowth: standing until the cut starts, then cut from the tip
			return 1.0 - clamp((tau - t.cutBegin(dist)) / t.cutSpan);
		}
		return clamp((tau - t.reformBegin(dist)) / t.reformSpan);
	}

	public static Stage stage(Timing t, double tau, double dist) {
		if (tau < t.cutBegin(dist)) {
			return Stage.STANDING;
		}
		if (tau < t.cutBegin(dist) + t.cutSpan) {
			return Stage.DISSOLVING;
		}
		if (tau < t.reformBegin(dist)) {
			return Stage.GONE;
		}
		if (tau < t.reformBegin(dist) + t.reformSpan) {
			return Stage.REGROWING;
		}
		return Stage.STANDING;
	}

	/** True while the blade is neither whole before the phase nor whole again after it (the CPU path must draw it, not the static buffer). */
	public static boolean animating(Timing t, double tau, double dist) {
		return tau >= t.cutBegin(dist) && tau < t.reformBegin(dist) + t.reformSpan;
	}

	/** True when any blade up to {@code maxDist} is still changing. */
	public static boolean active(Timing t, double tau, double maxDist) {
		return tau >= 0 && tau < t.end(maxDist);
	}

	/** Glow at the cut edge: 1 while the blade dissolves or regrows (smooth in and out), 0 otherwise. */
	public static double edgeGlow(Timing t, double tau, double dist) {
		Stage s = stage(t, tau, dist);
		if (s == Stage.DISSOLVING) {
			double x = (tau - t.cutBegin(dist)) / t.cutSpan;
			return Math.min(1.0, Math.min(x * 8, (1 - x) * 3 + 0.35));
		}
		if (s == Stage.REGROWING) {
			double x = (tau - t.reformBegin(dist)) / t.reformSpan;
			return Math.min(1.0, Math.min(x * 8, (1 - x) * 4));
		}
		return 0;
	}

	/**
	 * Age of a phase from the tracked anchor data (S1/S3): {@code phaseTime} is the game time of the phase change, {@code nowTime} the current
	 * game time (ticks); the age in seconds, clamped at 0.
	 */
	public static double ageSeconds(long nowTime, long phaseTime, double partialTicks) {
		return Math.max(0, (nowTime - phaseTime + partialTicks) / 20.0);
	}
}
