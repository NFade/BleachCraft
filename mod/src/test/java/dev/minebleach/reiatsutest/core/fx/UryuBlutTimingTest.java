package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The numbers of the Blut Arterie release, URYU_VFX_STORYBOARD 2.5 (effect id 70), as the pure tables of {@link UryuBlutTiming}. */
class UryuBlutTimingTest {
	private static final double EPS = 1e-9;

	@Test
	void theTimesAreTheRowsOfTheTable() {
		assertEquals(0.00, UryuBlutTiming.T_FISTS, EPS);
		assertEquals(0.20, UryuBlutTiming.T_IGNITE, EPS);
		assertEquals(0.35, UryuBlutTiming.SWEEP_TIME, EPS);
		assertEquals(0.55, UryuBlutTiming.T_BURST, EPS);
		assertEquals(UryuBlutTiming.T_BURST, UryuBlutTiming.T_IGNITE + UryuBlutTiming.SWEEP_TIME, EPS, "the sweep ends with the burst");
		assertEquals(0.90, UryuBlutTiming.T_HEART, EPS);
		assertEquals(0.70, UryuBlutTiming.BEAT_PERIOD, EPS);
		assertEquals(3, UryuBlutTiming.BEATS);
		assertEquals(1.20, UryuBlutTiming.T_REST, EPS);
		assertEquals(3.0, UryuBlutTiming.heartEnd(), EPS);
		assertEquals(0.90, UryuBlutTiming.beatStart(0), EPS);
		assertEquals(1.60, UryuBlutTiming.beatStart(1), EPS);
		assertEquals(2.30, UryuBlutTiming.beatStart(2), EPS);
	}

	@Test
	void theSweepRunsFromTheFingertipsToTheNeckInHalfAThirdOfASecond() {
		assertEquals(0.0, UryuBlutTiming.sweep(0.0), EPS);
		assertEquals(0.0, UryuBlutTiming.sweep(0.20), EPS);
		assertEquals(0.5, UryuBlutTiming.sweep(0.375), 1e-6, "easeInOutSine is 0.5 in the middle");
		assertEquals(1.0, UryuBlutTiming.sweep(0.55), EPS);
		assertEquals(1.0, UryuBlutTiming.sweep(2.0), EPS);
		double last = -1;
		for (double t = 0; t <= 0.7; t += 0.005) {
			double s = UryuBlutTiming.sweep(t);
			assertTrue(s >= last - EPS, "monotone at " + t);
			last = s;
		}
	}

	@Test
	void theStageNeverStepsBackAndVisitsEveryVersion() {
		assertEquals(-1, UryuBlutTiming.stage(0.0), "nothing lit before the sweep");
		assertEquals(0, UryuBlutTiming.stage(0.001), "the first fingertips");
		assertEquals(UryuBlutTiming.STAGES - 1, UryuBlutTiming.stage(1.0));
		assertEquals(UryuBlutTiming.STAGES - 1, UryuBlutTiming.stage(0.99));
		Set<Integer> seen = new HashSet<>();
		int last = -1;
		for (double t = 0.19; t <= 0.6; t += 0.001) {
			int s = UryuBlutTiming.stage(UryuBlutTiming.sweep(t));
			assertTrue(s >= last, "stage never goes back at " + t);
			last = s;
			seen.add(s);
		}
		for (int k = 0; k < UryuBlutTiming.STAGES; k++) {
			assertTrue(seen.contains(k), "stage " + k + " is shown during the sweep");
		}
	}

	@Test
	void theThumpStaysBetweenTheTwoLevelsOfTheStoryboard() {
		assertEquals(0.55, UryuBlutTiming.thump(0.0), EPS);
		assertEquals(1.00, UryuBlutTiming.thump(0.15), EPS);
		for (double tau = 0; tau <= 1.0; tau += 0.01) {
			double v = UryuBlutTiming.thump(tau);
			assertTrue(v >= 0.55 - EPS && v <= 1.0 + EPS, "0.55..1.0 at " + tau + ": " + v);
		}
		assertEquals(0.55, UryuBlutTiming.thump(1.0), 1e-12, "it has died away at the end of the period: no step into the next beat");
	}

	@Test
	void theReleaseLevelFollowsTheTable() {
		assertEquals(0.0, UryuBlutTiming.releaseLevel(0.0, false, 0.5), EPS);
		assertEquals(0.0, UryuBlutTiming.releaseLevel(0.19, false, 0.5), EPS);
		assertTrue(UryuBlutTiming.releaseLevel(0.21, false, 0.5) >= 0.6, "lit with the sweep");
		assertEquals(1.0, UryuBlutTiming.releaseLevel(0.55, false, 0.5), 1e-9, "full at the burst");
		assertEquals(0.55, UryuBlutTiming.releaseLevel(0.90, false, 0.5), 1e-9, "down to the first beat");
		// the beats pulse 0.55 .. 1.0
		for (int k = 0; k < 3; k++) {
			double t0 = UryuBlutTiming.beatStart(k);
			assertEquals(0.55, UryuBlutTiming.releaseLevel(t0, false, 0.5), 1e-9, "beat " + k + " start");
			assertEquals(1.0, UryuBlutTiming.releaseLevel(t0 + 0.7 * 0.15, false, 0.5), 1e-9, "beat " + k + " peak");
		}
		// then the state level
		assertEquals(0.5, UryuBlutTiming.releaseLevel(3.4, false, 0.5), EPS);
		assertEquals(0.5, UryuBlutTiming.releaseLevel(60.0, false, 0.5), EPS);
		assertEquals(0.8, UryuBlutTiming.releaseLevel(60.0, false, 0.8), EPS, "Focus 3");
	}

	@Test
	void theReleaseLevelHasNoJumpAfterTheIgnition() {
		double prev = UryuBlutTiming.releaseLevel(0.2001, false, 0.5);
		for (double t = 0.2001; t < 6.0; t += 0.001) {
			double v = UryuBlutTiming.releaseLevel(t, false, 0.5);
			// a beat rises 0.45 within 0.105 s: 0.0043 per ms at most (smoothstep slope 1.5): 0.01 per step is a safe bound
			assertTrue(Math.abs(v - prev) < 0.01, "jump at " + t + ": " + prev + " -> " + v);
			prev = v;
		}
	}

	@Test
	void reduceMotionMakesThePulseAConstantSeventyPercent() {
		double min = 9, max = -9;
		for (double t = 0.21; t < 3.0; t += 0.01) {
			double v = UryuBlutTiming.releaseLevel(t, true, 0.5);
			min = Math.min(min, v);
			max = Math.max(max, v);
		}
		assertEquals(0.7, min, EPS);
		assertEquals(0.7, max, EPS);
		assertEquals(0.5, UryuBlutTiming.releaseLevel(3.5, true, 0.5), EPS, "settles to the state level");
		assertEquals(0.0, UryuBlutTiming.releaseLevel(0.1, true, 0.5), EPS);
		// no jump into the settle
		double prev = UryuBlutTiming.releaseLevel(2.999, true, 0.5);
		for (double t = 3.0; t < 3.5; t += 0.001) {
			double v = UryuBlutTiming.releaseLevel(t, true, 0.5);
			assertTrue(Math.abs(v - prev) < 0.01, "RM settle jump at " + t);
			prev = v;
		}
	}

	@Test
	void theStateLevelAndTheShotBrightening() {
		assertEquals(0.5, UryuBlutTiming.stateLevel(0), EPS);
		assertEquals(0.5, UryuBlutTiming.stateLevel(2), EPS);
		assertEquals(0.8, UryuBlutTiming.stateLevel(3), EPS);
		assertEquals(0.0, UryuBlutTiming.fireEnvelope(-0.1), EPS);
		assertEquals(0.0, UryuBlutTiming.fireEnvelope(0.30), EPS);
		assertEquals(0.0, UryuBlutTiming.fireEnvelope(1.0), EPS);
		double peak = 0;
		for (double s = 0; s < 0.3; s += 0.001) {
			peak = Math.max(peak, UryuBlutTiming.fireEnvelope(s));
		}
		assertEquals(1.0, peak, 1e-3);
		assertEquals(0.8, UryuBlutTiming.withFire(0.5, 1.0), 1e-9, "0.5 brightens to 0.8");
		assertEquals(0.5, UryuBlutTiming.withFire(0.5, 0.0), EPS);
		assertEquals(1.0, UryuBlutTiming.withFire(0.8, 1.0), 1e-9, "at Focus 3 it goes to the top");
		assertEquals(1.0, UryuBlutTiming.withFire(1.0, 1.0), 1e-9);
	}

	@Test
	void theOverlayFadesOutAfterTheState() {
		assertEquals(1.0, UryuBlutTiming.fadeOut(0.0), EPS);
		assertEquals(0.5, UryuBlutTiming.fadeOut(0.2), 1e-9);
		assertEquals(0.0, UryuBlutTiming.fadeOut(0.4), 1e-9);
		assertEquals(0.0, UryuBlutTiming.fadeOut(5.0), EPS);
	}

	@Test
	void theColourOfTheTwoPassesReadsAtTheStateLevel() {
		// the solid pass keeps the veins readable on a bright sky (rule 5): well above 0.8 alpha at the state level
		assertTrue(UryuBlutTiming.solidAlpha(0.5) >= 0.8, "state level alpha " + UryuBlutTiming.solidAlpha(0.5));
		assertEquals(1.0, UryuBlutTiming.solidAlpha(1.0), EPS);
		assertEquals(0.30, UryuBlutTiming.solidAlpha(0.0), EPS);
		assertEquals(0.7, UryuBlutTiming.glowGain(0.5), 1e-9);
		assertEquals(1.0, UryuBlutTiming.glowGain(0.8), EPS);
		assertEquals(0.0, UryuBlutTiming.glowGain(0.0), EPS);
		assertTrue(UryuBlutTiming.invisible(0.0));
		assertFalse(UryuBlutTiming.invisible(0.5));
		double prev = -1;
		for (double l = 0; l <= 1.0; l += 0.01) {
			assertTrue(UryuBlutTiming.solidAlpha(l) >= prev - EPS && UryuBlutTiming.glowGain(l) >= 0);
			prev = UryuBlutTiming.solidAlpha(l);
		}
	}

	@Test
	void lowTierDrawsTheArmsOnly() {
		assertEquals(List.of(UryuBlutTiming.Part.BODY, UryuBlutTiming.Part.RIGHT_ARM, UryuBlutTiming.Part.LEFT_ARM), UryuBlutTiming.parts(UryuFxTiers.VeinOverlay.FULL));
		assertEquals(List.of(UryuBlutTiming.Part.RIGHT_ARM, UryuBlutTiming.Part.LEFT_ARM), UryuBlutTiming.parts(UryuFxTiers.VeinOverlay.ARMS));
		assertEquals(UryuFxTiers.VeinOverlay.ARMS, UryuFxTiers.LOW.veinOverlay());
		assertEquals(UryuFxTiers.VeinOverlay.FULL, UryuFxTiers.HIGH.veinOverlay());
		assertEquals(UryuFxTiers.VeinOverlay.FULL, UryuFxTiers.MEDIUM.veinOverlay());
	}

	@Test
	void theOverlayReachesBeyondTheOuterSkinLayerOnSlimAndClassicArmsAndTheChest() {
		double need = UryuBlutTiming.OUTER_LAYER_PX + UryuBlutTiming.CLEARANCE_PX;
		assertTrue(UryuBlutTiming.reach(4, UryuBlutTiming.armScaleX(false)) >= need, "classic arm width");
		assertTrue(UryuBlutTiming.reach(3, UryuBlutTiming.armScaleX(true)) >= need, "slim arm width");
		assertTrue(UryuBlutTiming.reach(4, UryuBlutTiming.armScaleZ()) >= need, "arm depth");
		assertTrue(UryuBlutTiming.reach(8, UryuBlutTiming.bodyScaleX()) >= need, "torso width");
		assertTrue(UryuBlutTiming.reach(4, UryuBlutTiming.bodyScaleZ()) >= need, "torso depth");
		// ... without swelling the limbs into blobs
		assertTrue(UryuBlutTiming.reach(3, UryuBlutTiming.armScaleX(true)) < 0.5);
		assertTrue(UryuBlutTiming.reach(8, UryuBlutTiming.bodyScaleX()) < 0.5);
	}

	@Test
	void thePivotShiftKeepsTheScaledArmWhereItWas() {
		// right arm centre -1 (classic) scaled about the pivot lands at -1.16: shift +0.16 to bring it back; the left mirrors it
		assertEquals(0.16, UryuBlutTiming.armPivotShiftX(false, false), 1e-9);
		assertEquals(-0.16, UryuBlutTiming.armPivotShiftX(false, true), 1e-9);
		assertEquals(0.11, UryuBlutTiming.armPivotShiftX(true, false), 1e-9);
		assertEquals(-0.11, UryuBlutTiming.armPivotShiftX(true, true), 1e-9);
	}

	@Test
	void theSoundsAreTheOnesOfTheStoryboard() {
		List<UryuBlutTiming.Cue> c = UryuBlutTiming.CUES;
		assertEquals(7, c.size());
		assertEquals(new UryuBlutTiming.Cue(0.00, "block.beacon.power_select", 1.7, 0.5), c.get(0));
		assertEquals(new UryuBlutTiming.Cue(0.20, "block.respawn_anchor.charge", 1.6, 0.4), c.get(1));
		assertEquals(new UryuBlutTiming.Cue(0.55, "block.beacon.activate", 1.7, 0.7), c.get(2));
		assertEquals(new UryuBlutTiming.Cue(0.55, "entity.player.attack.sweep", 1.8, 0.4), c.get(3));
		for (int k = 0; k < 3; k++) {
			assertEquals(new UryuBlutTiming.Cue(UryuBlutTiming.beatStart(k), "entity.warden.heartbeat", 1.6, 0.25), c.get(4 + k), "heartbeat " + k);
		}
		double last = -1;
		for (UryuBlutTiming.Cue cue : c) {
			assertTrue(cue.t() >= last, "time ordered");
			last = cue.t();
		}
	}

	@Test
	void theCountsAndSizesAreTheStoryboardOnes() {
		assertEquals(30, UryuBlutTiming.FIST_DASHES);
		assertEquals(1.4, UryuBlutTiming.FIST_RADIUS, EPS);
		assertEquals(1.8, UryuBlutTiming.PENTAGON_RADIUS, EPS);
		assertEquals(0.04, UryuBlutTiming.PENTAGON_EDGE_TIME, EPS);
		assertEquals(6, UryuBlutTiming.SPARKLES);
		assertEquals(0.25, UryuBlutTiming.SPARKLE_SIZE, EPS);
		assertEquals(0.12, UryuBlutTiming.SPARKLE_LIFE, EPS);
		assertEquals(4.0, UryuBlutTiming.RING_RADIUS, EPS);
		assertEquals(0.35, UryuBlutTiming.RING_TIME, EPS);
		assertEquals(3.4, UryuBlutTiming.STAR_SIZE, EPS);
		assertEquals(0.6, UryuBlutTiming.STAR_PEAK, EPS);
		assertEquals(0.25, UryuBlutTiming.STAR_SPIN, EPS);
		assertEquals(1.3, UryuBlutTiming.STAR_LIFE, EPS);
		assertEquals(30, UryuBlutTiming.BURST_DASHES);
		assertEquals(7.0, UryuBlutTiming.BURST_SPEED, EPS);
		assertEquals(0.88, UryuBlutTiming.BURST_DRAG, EPS);
		assertEquals(12, UryuBlutTiming.BURST_LIFE_TICKS);
		assertEquals(3.0, UryuBlutTiming.BODY_FLASH_SIZE, EPS);
		assertEquals(0.5, UryuBlutTiming.BODY_FLASH_PEAK, EPS);
		assertEquals(0.12, UryuBlutTiming.BODY_FLASH_LIFE, EPS);
		assertEquals(0.35, UryuBlutTiming.FLASH, EPS);
		assertEquals(0.25, UryuBlutTiming.FLASH_RM, EPS);
		assertEquals(0.5, UryuBlutTiming.SHAKE_AMPLITUDE, EPS);
		assertEquals(0.25, UryuBlutTiming.SHAKE_TIME, EPS);
		assertEquals(0.20, UryuBlutTiming.VIGNETTE, EPS);
		assertEquals(0.10, UryuBlutTiming.VIGNETTE_RM, EPS);
		assertEquals(0.3, UryuBlutTiming.VIGNETTE_RAMP, EPS);
		assertEquals(0.6, UryuBlutTiming.VIGNETTE_FADE, EPS);
		assertEquals(4.0, UryuBlutTiming.MOTES_PER_SECOND, EPS);
		assertEquals(0.6, UryuBlutTiming.MOTE_RISE, EPS);
		assertEquals(16, UryuBlutTiming.MOTE_LIFE_TICKS);
		assertEquals(48.0, UryuBlutTiming.VIEW_DISTANCE, EPS);
	}

	@Test
	void theReleaseStaysInsideItsGlowBudgetAndTheRmCapsAreBelowTheNormalValues() {
		assertTrue(UryuBlutTiming.glowSprites() <= UryuBlutTiming.GLOW_BUDGET, "90 G at the release: " + UryuBlutTiming.glowSprites());
		assertTrue(UryuBlutTiming.FLASH_RM < UryuBlutTiming.FLASH);
		assertTrue(UryuBlutTiming.VIGNETTE_RM < UryuBlutTiming.VIGNETTE);
		assertTrue(UryuBlutTiming.FLASH_RM <= 0.40, "Reduce Motion flash cap of the storyboard");
	}

	@Test
	void thePentagonOfTheFeetIsRegularAndDrawsOneEdgePerFortyMilliseconds() {
		double r = UryuBlutTiming.PENTAGON_RADIUS;
		double[] v0 = UryuBlutTiming.pentagonVertex(r, 0, 0, 1);
		assertEquals(0.0, v0[0], 1e-9, "the first vertex points along the facing (south: +z)");
		assertEquals(r, v0[1], 1e-9);
		double side = 2 * r * Math.sin(Math.PI / 5);
		double cx = 0;
		double cz = 0;
		for (int k = 0; k < 5; k++) {
			double[] a = UryuBlutTiming.pentagonVertex(r, k, 0.6, -0.8);
			double[] b = UryuBlutTiming.pentagonVertex(r, k + 1, 0.6, -0.8);
			assertEquals(r, Math.hypot(a[0], a[1]), 1e-9, "on the circle");
			assertEquals(side, Math.hypot(b[0] - a[0], b[1] - a[1]), 1e-9, "equal sides");
			cx += a[0];
			cz += a[1];
		}
		assertEquals(0.0, cx, 1e-9, "centred");
		assertEquals(0.0, cz, 1e-9);
		double[] f = UryuBlutTiming.pentagonVertex(r, 0, 3, 4);
		assertEquals(0.6 * r, f[0], 1e-9, "the facing is normalised");
		assertEquals(0.8 * r, f[1], 1e-9);
		for (int k = 0; k < 5; k++) {
			assertEquals(0.04 * k, UryuBlutTiming.pentagonEdgeStart(k), EPS);
		}
		assertEquals(UryuBlutTiming.T_IGNITE, UryuBlutTiming.pentagonClosed(), EPS, "the outline closes when the veins ignite");
	}
}
