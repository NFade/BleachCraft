package dev.minebleach.reiatsutest.core.combat;

import java.util.Map;

/**
 * First gestures of the abilities added in round 6 (Rukia bankai ice wall, Byakuya shikai petal cage), kept apart from
 * {@link CastGestures} so that file stays with the gesture agents. {@link CastGestures#defaults()} calls {@link #addTo}. Both are
 * deliberately plain placeholders (UNVERIFIED in the game): the ice wall is a Shirafune style thrust with a longer hold (it was the ice spike gesture before R11 T5; the release is at 0.7 s like the 14 tick cast time), the cage is
 * a raised hilt with the left hand closing into a fist. Replace the entries of {@code byakuya.shikai.cage} / {@code rukia.bankai.ice_wall}
 * in CastGestures when the real hand gestures exist, or override them from {@code pose_override.json} ({@code fp_cast.<ability id>}).
 */
final class ExtraGestures {
	private static final int SM = CastGesture.E_SMOOTH;
	private static final int OUT = CastGesture.E_OUT;
	private static final int IN = CastGesture.E_IN;

	private ExtraGestures() {
	}

	static void addTo(Map<String, CastGesture> m) {
		// ---- Rukia, bankai, ice wall: the blade is drawn back, one long thrust along the look, the left hand points down the line
		put(m, tp(CastGesture.builder("rukia.bankai.ice_wall").dur(2.6).out(0.4)
				.r(0.00, OUT, 0.10, -0.12, 0.08, 20, 0, 8)
				.r(0.30, SM, 0.07, -0.08, 0.04, 28, 0, 6)
				.r(0.52, IN, 0.04, -0.05, -0.04, 8, 0, 0)
				.r(0.62, OUT, 0, -0.03, -0.32, -74, 0, 0)
				.r(0.80, SM, 0, -0.03, -0.28, -70, 0, 0)
				.r(2.10, SM, 0, -0.03, -0.28, -70, 0, 0)
				.r(2.60, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, OUT, -0.05, 0.02, -0.26, -26, 0, 0, 1)
				.l(0.70, SM, -0.06, 0.05, -0.34, -34, 0, 0, 1)
				.l(2.00, SM, -0.06, 0.05, -0.34, -34, 0, 0, 1)
				.l(2.60, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.30, OUT, 0, 0, 0, 2)
				.c(0.62, SM, -1.5, 0, 0, 0)
				.c(1.00, SM, 0, 0, 0, 0), 0.25, -80, -70, 10));

		// ---- Byakuya, shikai, petal cage: the hilt rises, the left hand opens towards the target and closes into a fist
		put(m, tp(CastGesture.builder("byakuya.shikai.cage").dur(3.0).out(0.45)
				.r(0.00, OUT, 0.06, 0.04, -0.16, -20, -12, 0)
				.r(0.40, SM, 0.06, 0.10, -0.22, -40, -16, 0)
				.r(2.40, SM, 0.06, 0.08, -0.20, -38, -14, 0)
				.r(3.00, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, SM, -0.04, -0.06, -0.12, 0, 0, 0, 1)
				.l(0.35, OUT, -0.06, 0.06, -0.40, -30, 0, 0, 1)
				.l(0.70, SM, -0.06, 0.06, -0.38, -30, 0, 0, 0.2)
				.l(2.40, SM, -0.05, 0.05, -0.34, -26, 0, 0, 0.2)
				.l(3.00, SM, 0, 0, 0, 0, 0, 0, 0)
				.vib('L', 0.7, 2.4, 0.006, 8)
				.c(0.40, OUT, -0.5, 0, 0, 0)
				.c(0.90, SM, 0, 0, 0, 0), 0.25, -50, -60, 10));
	}

	private static void put(Map<String, CastGesture> m, CastGesture.Builder b) {
		CastGesture g = b.build();
		m.put(g.id(), g);
	}

	/** Third person: the main arm goes up to {@code rp} (pitch deg), the other to {@code lp} with {@code lroll} outward, over {@code up} s, back at the end. */
	private static CastGesture.Builder tp(CastGesture.Builder b, double up, double rp, double lp, double lroll) {
		double end = b.build().dur();
		double e = Math.max(up + 0.1, end - 0.3);
		b.tr(up, OUT, rp, 0, 0).tr(e, SM, rp, 0, 0).tr(end, SM, 0, 0, 0);
		b.tl(up, OUT, lp, 0, lroll).tl(e, SM, lp, 0, lroll).tl(end, SM, 0, 0, 0);
		return b;
	}
}
