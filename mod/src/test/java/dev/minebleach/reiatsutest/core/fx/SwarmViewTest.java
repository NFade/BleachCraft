package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R12 view gates of the shikai swarm: the strikes keep the old numbers, the resting swarm is thinned by motion, the sword zone is cleared. */
class SwarmViewTest {
	private static final SwarmView.Fp FP = new SwarmView.Fp();
	private static final SwarmView.Tp TP = new SwarmView.Tp();
	/** The thinning that R12 round 2 shipped (and round 3 took back as a default): the gate must still work when the keys ask for it. */
	private static final SwarmView.Fp THIN = new SwarmView.Fp();

	static {
		THIN.idleClear = 0.6;
		THIN.idleEdge = 0.8;
		THIN.idleDensity = 0.5;
		THIN.moveClear = 1.2;
		THIN.moveCut = 0.7;
	}

	@Test
	void theOldGateIsUnchangedForStrikes() {
		// the pre-R12 formula, written out
		for (int i = 0; i < 400; i++) {
			for (double d = 0.5; d < 8.0; d += 0.37) {
				double expect;
				if (d < 1.5) {
					expect = 0;
				} else {
					double a = SwarmView.smoothstep(1.5, 5.0, d);
					double hash = (i * 0.6180339887498949) % 1.0;
					expect = hash > 0.12 + 0.88 * a ? 0 : 0.35 + 0.65 * a;
				}
				assertEquals(expect, SwarmView.fpScale(i, d, FP, false, 0.9), 1e-12, "i=" + i + " d=" + d);
			}
		}
		assertEquals(0.15, SwarmView.fpGlow(1.0, FP, false, 1.0), 1e-12);
		assertEquals(1.0, SwarmView.fpGlow(9.0, FP, false, 1.0), 1e-12);
	}

	@Test
	void theRestingSwarmIsThinnerAndMotionRemovesIt() {
		int standing = 0;
		int walking = 0;
		int old = 0;
		for (int i = 0; i < 1000; i++) {
			for (double d : new double[] {1.8, 2.5, 3.5, 4.5}) {
				old += SwarmView.fpScale(i, d, THIN, false, 0) > 0 ? 1 : 0;
				standing += SwarmView.fpScale(i, d, THIN, true, 0) > 0 ? 1 : 0;
				walking += SwarmView.fpScale(i, d, THIN, true, 1) > 0 ? 1 : 0;
			}
		}
		assertTrue(standing < old * 0.6, standing + " vs " + old);
		assertTrue(walking < standing * 0.4, walking + " vs " + standing);
		// nothing closer than the clear radius, growing with the motion
		assertEquals(0, SwarmView.fpScale(3, 2.0, THIN, true, 0), 0);
		assertEquals(0, SwarmView.fpScale(0, 3.2, THIN, true, 1.0), 0);
		// no pop: the size goes to zero over the soft edge
		double edge = THIN.clear + THIN.idleClear;
		for (int i = 0; i < 20; i++) {
			assertTrue(SwarmView.fpScale(i, edge + 0.01, THIN, true, 0) < 0.02);
		}
	}

	@Test
	void byDefaultTheFirstPersonPetalsAreBackAsTheyWereAndOnlyTheGlintsAreOff() {
		for (int i = 0; i < 400; i++) {
			for (double d = 0.5; d < 8.0; d += 0.37) {
				assertEquals(SwarmView.fpScale(i, d, FP, false, 0), SwarmView.fpScale(i, d, FP, true, 0), 1e-12, "resting = old, i=" + i + " d=" + d);
				assertEquals(SwarmView.fpScale(i, d, FP, false, 0), SwarmView.fpScale(i, d, FP, true, 1.0), 1e-12, "walking = old, i=" + i + " d=" + d);
			}
		}
		assertEquals(0.0, FP.glint, 0.0, "white star glints off in the first person");
		assertEquals(0.6, TP.size, 0.0, "third person petals are smaller");
	}

	@Test
	void theGlowOfTheRestingSwarmIsCutByMotion() {
		double still = SwarmView.fpGlow(4.0, FP, true, 0);
		double moving = SwarmView.fpGlow(4.0, FP, true, 1);
		assertTrue(still < SwarmView.fpGlow(4.0, FP, false, 0) * 0.3);
		assertTrue(SwarmView.fpGlow(4.0, THIN, true, 0.5) < still);
		assertEquals(0.0, moving, 1e-12);
	}

	@Test
	void activityIsTheLargerOfWalkAndTurnAndSmoothedFastUpSlowDown() {
		assertEquals(0.0, SwarmView.activity(0, 0, 4.3, 90), 0);
		assertEquals(1.0, SwarmView.activity(4.3, 0, 4.3, 90), 1e-12);
		assertEquals(1.0, SwarmView.activity(0, 500, 4.3, 90), 1e-12);
		assertEquals(0.5, SwarmView.activity(0, 45, 4.3, 90), 1e-12);
		double a = 0;
		for (int k = 0; k < 12; k++) {
			a = SwarmView.smoothActivity(a, 1, 1.0 / 60, 12, 3);
		}
		assertTrue(a > 0.6, "up in about 0.2 s: " + a);
		double b = 1;
		for (int k = 0; k < 12; k++) {
			b = SwarmView.smoothActivity(b, 0, 1.0 / 60, 12, 3);
		}
		assertTrue(b > 0.4, "down slowly: " + b);
	}

	@Test
	void thirdPersonKeepsAFixedFractionAndTheSwordZoneIsClearedOnlyWhileStriking() {
		int kept = 0;
		for (int i = 0; i < 1000; i++) {
			kept += SwarmView.tpKeep(i, TP) ? 1 : 0;
		}
		assertEquals(500, kept, 5);
		assertEquals(0.4, SwarmView.tpGlow(TP), 1e-12);
		// a petal on the line of the sword, 1 block in front of the shoulder
		assertEquals(1.0, SwarmView.swordClear(0, 0, 1.0, 0, 0, 1, 0.0, TP), 0, "not striking: untouched");
		assertEquals(0.0, SwarmView.swordClear(0, 0, 1.0, 0, 0, 1, 1.0, TP), 1e-12, "striking: gone");
		assertEquals(0.5, SwarmView.swordClear(0, 0, 1.0, 0, 0, 1, 0.5, TP), 1e-12);
		// behind the owner and far to the side: untouched
		assertEquals(1.0, SwarmView.swordClear(0, 0, -1.5, 0, 0, 1, 1.0, TP), 1e-12);
		assertEquals(1.0, SwarmView.swordClear(2.0, 0, 1.0, 0, 0, 1, 1.0, TP), 1e-12);
	}
}
