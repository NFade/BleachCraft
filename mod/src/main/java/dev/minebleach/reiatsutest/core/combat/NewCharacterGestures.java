package dev.minebleach.reiatsutest.core.combat;

import java.util.Map;

/**
 * Placeholder gestures of the abilities of Gin Ichimaru (Uryu Ishida has his own, {@link UryuGestures}; branch gin-uryu, registration skeleton), kept apart from
 * {@link CastGestures} like {@link ExtraGestures}. Every ability gets the same plain thrust (the ice spike pose, rescaled to the
 * duration given here) until the real hand gestures exist; replace an entry here or override it from {@code pose_override.json}
 * ({@code fp_cast.<ability id>}). UNVERIFIED in the game.
 */
final class NewCharacterGestures {
	private static final int SM = CastGesture.E_SMOOTH;
	private static final int OUT = CastGesture.E_OUT;
	private static final int IN = CastGesture.E_IN;

	private NewCharacterGestures() {
	}

	static void addTo(Map<String, CastGesture> m) {
		thrust(m, "gin.shikai.ikorose", 1.2);
		thrust(m, "gin.shikai.hyapponzashi", 2.4);
		thrust(m, "gin.shikai.snake_hook", 1.6);
		thrust(m, "gin.bankai.butou_renjin", 2.6);
		thrust(m, "gin.bankai.korose", 2.4);
		thrust(m, "gin.bankai.hakufuku", 1.4);
		UryuGestures.addTo(m); // U-FX 11: the real first person hands of Uryu (pull, release, abilities), the placeholder thrusts are gone
	}

	/** The right hand is drawn back, thrown forward along the look and held, the left hand points down the line; times scaled to {@code dur}. */
	private static void thrust(Map<String, CastGesture> m, String id, double dur) {
		double k = dur / 2.6;
		CastGesture.Builder b = CastGesture.builder(id).dur(dur).out(0.3)
				.r(0.00 * k, OUT, 0.10, -0.12, 0.08, 20, 0, 8)
				.r(0.30 * k, SM, 0.07, -0.08, 0.04, 28, 0, 6)
				.r(0.52 * k, IN, 0.04, -0.05, -0.04, 8, 0, 0)
				.r(0.62 * k, OUT, 0, -0.03, -0.32, -74, 0, 0)
				.r(0.80 * k, SM, 0, -0.03, -0.28, -70, 0, 0)
				.r(2.10 * k, SM, 0, -0.03, -0.28, -70, 0, 0)
				.r(dur, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00 * k, OUT, -0.05, 0.02, -0.26, -26, 0, 0, 1)
				.l(0.70 * k, SM, -0.06, 0.05, -0.34, -34, 0, 0, 1)
				.l(2.00 * k, SM, -0.06, 0.05, -0.34, -34, 0, 0, 1)
				.l(dur, SM, 0, 0, 0, 0, 0, 0, 0);
		// third person: the main arm up to -80 degrees, the other to -70 with 10 outward, back at the end
		double up = 0.25 * k;
		double e = Math.max(up + 0.1, dur - 0.3);
		b.tr(up, OUT, -80, 0, 0).tr(e, SM, -80, 0, 0).tr(dur, SM, 0, 0, 0);
		b.tl(up, OUT, -70, 0, 10).tl(e, SM, -70, 0, 10).tl(dur, SM, 0, 0, 0);
		CastGesture g = b.build();
		m.put(g.id(), g);
	}
}
