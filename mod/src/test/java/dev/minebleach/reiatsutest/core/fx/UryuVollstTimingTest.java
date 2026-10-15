package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The numbers of the Quincy: Vollstaendig release and of the bankai state, URYU_VFX_STORYBOARD 2.10, 2.11, 2.16, as the pure tables of {@link UryuVollstTiming}. */
class UryuVollstTimingTest {
	private static final double EPS = 1e-9;

	@Test
	void theTimesAreTheRowsOfTheTable() {
		assertEquals(0.00, UryuVollstTiming.T_GATHER, EPS);
		assertEquals(0.50, UryuVollstTiming.T_COLLAPSE, EPS);
		assertEquals(0.15, UryuVollstTiming.COLLAPSE_TIME, EPS);
		assertEquals(0.65, UryuVollstTiming.T_BURST, EPS);
		assertEquals(UryuVollstTiming.T_BURST, UryuVollstTiming.T_COLLAPSE + UryuVollstTiming.COLLAPSE_TIME, EPS, "the burst follows the collapse");
		assertEquals(0.65, UryuVollstTiming.T_UNFOLD, EPS);
		assertEquals(0.60, UryuVollstTiming.UNFOLD_TIME, EPS);
		assertEquals(1.25, UryuVollstTiming.T_UNFOLD + UryuVollstTiming.UNFOLD_TIME, EPS, "the wings stand at 1.25 s");
		assertEquals(UryuVollstTiming.T_SETTLE, UryuVollstTiming.T_UNFOLD + UryuVollstTiming.UNFOLD_TIME, EPS);
		assertEquals(0.04, UryuVollstTiming.PLATE_STAGGER, EPS);
		assertEquals(1.2, UryuVollstTiming.UNFOLD_BACK, EPS);
		assertEquals(0.85, UryuVollstTiming.T_BANDS, EPS);
		assertEquals(0.35, UryuVollstTiming.BANDS_SWEEP, EPS);
		assertEquals(0.90, UryuVollstTiming.T_SIGN, EPS);
		assertEquals(1.5, UryuVollstTiming.SIGN_LIFE, EPS);
		assertEquals(1.0, UryuVollstTiming.T_GRADE_OUT, EPS);
		assertEquals(1.2, UryuVollstTiming.GRADE_OUT_TIME, EPS);
		assertEquals(2.2, UryuVollstTiming.LOCK, EPS);
		assertEquals(UryuVollstTiming.LOCK, UryuVollstTiming.T_GRADE_OUT + UryuVollstTiming.GRADE_OUT_TIME, EPS, "the grade is out at the end of the settle lock");
		assertTrue(UryuVollstTiming.LIFETIME > UryuVollstTiming.LOCK);
	}

	@Test
	void theCountsAndSizesAreTheStoryboardOnes() {
		assertEquals(90, UryuVollstTiming.GATHER_DASHES);
		assertEquals(60, UryuVollstTiming.GATHER_MOTES);
		assertEquals(150, UryuVollstTiming.GATHER_SPECKS);
		assertEquals(80, UryuVollstTiming.GATHER_SPECKS_LOW);
		assertEquals(10.0, UryuVollstTiming.GATHER_RADIUS, EPS);
		assertEquals(14.0, UryuVollstTiming.GATHER_SPEED, EPS);
		assertEquals(0.6, UryuVollstTiming.GATHER_LIFE, EPS);
		assertEquals(24, UryuVollstTiming.BURST_FLAKES);
		assertEquals(120, UryuVollstTiming.BURST_DASHES);
		assertEquals(14.0, UryuVollstTiming.RING_RADIUS, EPS);
		assertEquals(0.5, UryuVollstTiming.RING_TIME, EPS);
		assertEquals(200, UryuVollstTiming.UNFOLD_SPECKS);
		assertEquals(8.0, UryuVollstTiming.SIGN_SIZE, EPS);
		assertEquals(4.4, UryuVollstTiming.PENTAGON_RADIUS, EPS);
		assertEquals(0.8, UryuVollstTiming.FLASH, EPS);
		assertEquals(0.40, UryuVollstTiming.FLASH_RM, EPS);
		assertEquals(0.35, UryuVollstTiming.VIGNETTE, EPS);
		assertEquals(0.15, UryuVollstTiming.VIGNETTE_RM, EPS);
		assertEquals(0.6, UryuVollstTiming.GRADE_S, EPS);
		assertTrue(UryuVollstTiming.FLASH_RM <= 0.40 + EPS, "Reduce Motion never flashes above 0.40");
		// 2.11: 14 specks and a star per second per wing, 10 flakes, 3 players at the full look, 64 blocks
		assertEquals(14.0, UryuVollstTiming.STATE_SPECKS_PER_WING, EPS);
		assertEquals(10, UryuVollstTiming.FLAKES_HIGH);
		assertEquals(6, UryuVollstTiming.FLAKES_MEDIUM);
		assertEquals(3, UryuVollstTiming.FULL_PLAYERS);
		assertEquals(64.0, UryuVollstTiming.VIEW_DISTANCE, EPS);
		// 2.16: the end
		assertEquals(30, UryuVollstTiming.END_SPECKS);
		assertEquals(3.0, UryuVollstTiming.END_SIGN_RADIUS, EPS);
		assertEquals(30, UryuVollstTiming.END_FLAKE_LIFE_TICKS);
		assertEquals(0.25, UryuVollstTiming.BANDS_FADE_OUT, EPS);
	}

	@Test
	void theSoundsAreTheOnesOfTheStoryboardInTimeOrder() {
		double last = -1;
		Set<String> ids = new HashSet<>();
		for (UryuVollstTiming.Cue c : UryuVollstTiming.CUES) {
			assertTrue(c.t() >= last - EPS, "cues are in time order: " + c);
			assertTrue(c.t() <= UryuVollstTiming.LOCK, "no sound after the lock: " + c);
			assertTrue(c.sound().matches("[a-z_]+(\\.[a-z_]+)+"), "a vanilla sound id: " + c.sound());
			assertTrue(c.pitch() > 0 && c.volume() > 0 && c.volume() <= 1.0);
			ids.add(c.sound());
			last = c.t();
		}
		assertEquals(15, UryuVollstTiming.CUES.size());
		for (String id : new String[] {"block.beacon.power_select", "block.conduit.activate", "entity.generic.explode", "block.beacon.activate", "entity.warden.sonic_boom",
				"entity.ender_dragon.flap", "block.amethyst_block.resonate", "block.glass.break", "block.amethyst_block.chime", "entity.evoker.prepare_attack", "block.beacon.ambient"}) {
			assertTrue(ids.contains(id), id);
		}
		// the three glass breaks and the three chimes of the table
		assertEquals(3, UryuVollstTiming.CUES.stream().filter(c -> c.sound().equals("block.glass.break")).count());
		assertEquals(3, UryuVollstTiming.CUES.stream().filter(c -> c.sound().equals("block.amethyst_block.chime")).count());
		assertEquals(3, UryuVollstTiming.END_CUES.size());
		for (UryuVollstTiming.Cue c : UryuVollstTiming.END_CUES) {
			assertEquals(0.0, c.t(), EPS);
		}
	}

	@Test
	void easeOutBackOvershootsALittleAndLandsOnOne() {
		assertEquals(0.0, UryuVollstTiming.easeOutBack(0.0, 1.2), EPS);
		assertEquals(1.0, UryuVollstTiming.easeOutBack(1.0, 1.2), EPS);
		assertEquals(1.0, UryuVollstTiming.easeOutBack(7.0, 1.2), EPS, "clamped");
		assertEquals(0.0, UryuVollstTiming.easeOutBack(-3.0, 1.2), EPS);
		double max = 0;
		double prev = -1;
		for (double x = 0; x <= 1.0001; x += 0.005) {
			double v = UryuVollstTiming.easeOutBack(x, 1.2);
			max = Math.max(max, v);
			if (x < 0.5) {
				assertTrue(v >= prev - 1e-12, "rises first");
			}
			prev = v;
		}
		assertTrue(max > 1.01 && max < 1.06, "a small overshoot: " + max);
	}

	@Test
	void theGradeRampsInHoldsAndEasesBackByTheEndOfTheLock() {
		assertEquals(0.0, UryuVollstTiming.gradeLevel(0.0), EPS);
		assertEquals(0.0, UryuVollstTiming.gradeLevel(-1.0), EPS);
		assertEquals(0.5, UryuVollstTiming.gradeLevel(0.25), 1e-9, "smoothstep is 0.5 in the middle of the ramp");
		assertEquals(1.0, UryuVollstTiming.gradeLevel(0.5), EPS);
		assertEquals(1.0, UryuVollstTiming.gradeLevel(1.0), EPS);
		assertEquals(0.0, UryuVollstTiming.gradeLevel(UryuVollstTiming.LOCK), EPS);
		assertEquals(0.0, UryuVollstTiming.gradeLevel(5.0), EPS);
		double prev = 2;
		for (double t = 1.0; t <= 2.2; t += 0.01) {
			double v = UryuVollstTiming.gradeLevel(t);
			assertTrue(v <= prev + 1e-12, "the grade only goes down after 1.0 s");
			prev = v;
		}
	}

	@Test
	void theBandMaskSweepsFromTheHandOrSoleInward() {
		assertEquals(0.0, UryuVollstTiming.bandProgress(0.85), EPS);
		assertEquals(0.0, UryuVollstTiming.bandProgress(0.0), EPS);
		assertEquals(1.0, UryuVollstTiming.bandProgress(1.20), EPS);
		assertEquals(0.5, UryuVollstTiming.bandProgress(1.025), EPS);
		for (double h = 0; h <= 1.0001; h += 0.1) {
			assertEquals(0.0, UryuVollstTiming.bandAlpha(0.0, h), EPS, "nothing at the start");
			assertEquals(1.0, UryuVollstTiming.bandAlpha(1.0, h), EPS, "everything at the end");
		}
		for (double p = 0.05; p < 1.0; p += 0.05) {
			double prev = 2;
			for (double h = 0; h <= 1.0001; h += 0.05) {
				double a = UryuVollstTiming.bandAlpha(p, h);
				assertTrue(a <= prev + 1e-12, "the hand end is lit before the inner end (p " + p + ")");
				prev = a;
			}
		}
		// and never steps back in time
		for (double h = 0; h <= 1.0001; h += 0.1) {
			double prev = -1;
			for (double p = 0; p <= 1.0001; p += 0.02) {
				double a = UryuVollstTiming.bandAlpha(p, h);
				assertTrue(a >= prev - 1e-12);
				prev = a;
			}
		}
	}

	@Test
	void theBarsScrollWithoutLeavingTheRectAndWithoutASeam() {
		assertEquals(0.2 * 0.5, UryuVollstTiming.bandScroll(0.5), 1e-12);
		for (double now = 0; now < 40; now += 0.137) {
			double s = UryuVollstTiming.bandScroll(now);
			assertTrue(s >= 0 && s < UryuVollstTiming.BAND_PERIOD + 1e-12);
		}
		double quad = 1.0 / 12.0;
		for (double now = 0; now < 8; now += 0.173) {
			double shift = UryuVollstTiming.bandScroll(now);
			double prevEnd = Double.NaN;
			for (int k = 0; k < 12; k++) {
				double min = k * quad;
				double a = UryuVollstTiming.scrolledU(min, min, shift);
				double b = UryuVollstTiming.scrolledU(min + quad, min, shift);
				assertTrue(a >= -1e-12 && b <= UryuVollstTiming.BAND_PERIOD + quad + 1e-9, "inside the first period and one quad: " + a + " " + b);
				assertEquals(quad, b - a, 1e-9, "the quad keeps its width");
				if (!Double.isNaN(prevEnd)) {
					double d = ((a - prevEnd) % UryuVollstTiming.BAND_PERIOD + UryuVollstTiming.BAND_PERIOD) % UryuVollstTiming.BAND_PERIOD;
					assertTrue(d < 1e-9 || UryuVollstTiming.BAND_PERIOD - d < 1e-9, "the bars continue from quad to quad (modulo the period): " + d);
				}
				prevEnd = b;
			}
		}
	}

	@Test
	void theEmblemPopsAtTheBandsAndPulsesBetweenSeventyAndOneHundredPercentOfItsLevel() {
		assertEquals(0.0, UryuVollstTiming.emblemLevel(0.0, 3.0), EPS);
		assertEquals(0.0, UryuVollstTiming.emblemLevel(0.84, 3.0), EPS);
		double lo = 9;
		double hi = 0;
		for (double now = 0; now < 8; now += 0.01) {
			double v = UryuVollstTiming.emblemLevel(5.0, now);
			lo = Math.min(lo, v);
			hi = Math.max(hi, v);
		}
		assertEquals(UryuVollstTiming.EMBLEM_INTENSITY * 0.7, lo, 1e-3);
		assertEquals(UryuVollstTiming.EMBLEM_INTENSITY, hi, 1e-3);
		assertTrue(UryuVollstTiming.emblemLevel(0.86, 2.0) < UryuVollstTiming.emblemLevel(1.2, 2.0), "fades in");
	}

	@Test
	void theWingGlowBreathesBetweenEightyAndOneHundredPercent() {
		double lo = 9;
		double hi = 0;
		for (double now = 0; now < 4; now += 0.005) {
			double b = UryuVollstTiming.breath(now);
			lo = Math.min(lo, b);
			hi = Math.max(hi, b);
		}
		assertEquals(0.8, lo, 1e-3);
		assertEquals(1.0, hi, 1e-3);
		// 0.5 Hz: one breath in two seconds
		assertEquals(UryuVollstTiming.breath(0.3), UryuVollstTiming.breath(2.3), 1e-9);
	}

	@Test
	void flakesOrbitInsideTheirBoundsAndOneIsReplacedEveryEightTenthsOfASecond() {
		double[] f = new double[7];
		for (int count : new int[] {UryuVollstTiming.FLAKES_HIGH, UryuVollstTiming.FLAKES_MEDIUM}) {
			for (int i = 0; i < count; i++) {
				for (double now = 0; now < 40; now += 0.13) {
					UryuVollstTiming.flake(i, count, now, false, f);
					double r = Math.hypot(f[0], f[2]);
					assertTrue(r >= UryuVollstTiming.FLAKE_RADIUS_MIN - 1e-9 && r <= UryuVollstTiming.FLAKE_RADIUS_MAX + 1.0 + 1e-9, "radius " + r);
					assertTrue(f[1] >= UryuVollstTiming.FLAKE_HEIGHT_MIN - UryuVollstTiming.FLAKE_BOB - 1e-9 && f[1] <= UryuVollstTiming.FLAKE_HEIGHT_MAX + UryuVollstTiming.FLAKE_BOB + 0.4 + 1e-9,
							"height " + f[1]);
					assertTrue(f[3] >= UryuVollstTiming.FLAKE_SIZE_MIN - 1e-9 && f[3] <= UryuVollstTiming.FLAKE_SIZE_MAX + 1e-9, "size " + f[3]);
					assertTrue(f[4] >= 0 && f[4] <= 1.0 + 1e-9, "alpha " + f[4]);
				}
			}
		}
		// the flake of slot i starts a new life when (now + 0.8 i) is a multiple of the cycle: its alpha is 0 there; exactly one flake per 0.8 s
		int count = UryuVollstTiming.FLAKES_HIGH;
		for (int k = 1; k < 30; k++) {
			double now = 0.8 * k;
			int fresh = 0;
			for (int i = 0; i < count; i++) {
				UryuVollstTiming.flake(i, count, now, false, f);
				if (f[4] < 1e-9) {
					fresh++;
				}
			}
			assertEquals(1, fresh, "one flake is replaced at t = " + now);
		}
	}

	@Test
	void aFlakeIsAPureFunctionAndCalmModeIsSlower() {
		double[] a = new double[7];
		double[] b = new double[7];
		UryuVollstTiming.flake(3, 10, 12.34, false, a);
		UryuVollstTiming.flake(3, 10, 12.34, false, b);
		assertEquals(java.util.Arrays.toString(a), java.util.Arrays.toString(b));
		// calm: the orbit speed and the bob are halved: over one second the angle moves half as far
		double[] c0 = new double[7];
		double[] c1 = new double[7];
		UryuVollstTiming.flake(2, 10, 20.0, true, c0);
		UryuVollstTiming.flake(2, 10, 21.0, true, c1);
		double[] n0 = new double[7];
		double[] n1 = new double[7];
		UryuVollstTiming.flake(2, 10, 20.0, false, n0);
		UryuVollstTiming.flake(2, 10, 21.0, false, n1);
		double calm = Math.abs(wrap(Math.atan2(c1[2], c1[0]) - Math.atan2(c0[2], c0[0])));
		double norm = Math.abs(wrap(Math.atan2(n1[2], n1[0]) - Math.atan2(n0[2], n0[0])));
		assertEquals(2 * Math.PI * UryuVollstTiming.FLAKE_ORBIT_REV * 0.5, calm, 1e-6);
		assertEquals(2 * Math.PI * UryuVollstTiming.FLAKE_ORBIT_REV, norm, 1e-6);
	}

	private static double wrap(double a) {
		return Math.atan2(Math.sin(a), Math.cos(a));
	}

	@Test
	void flakesFollowTheTier() {
		assertEquals(10, UryuVollstTiming.flakeCount(false, false));
		assertEquals(6, UryuVollstTiming.flakeCount(false, true));
		assertEquals(0, UryuVollstTiming.flakeCount(true, false), "Low: wings and emblem only");
		assertEquals(0.0, UryuVollstTiming.flakeEnvelope(0.5), EPS);
		assertEquals(1.0, UryuVollstTiming.flakeEnvelope(1.5), EPS);
	}

	@Test
	void theClockOfTheWingsFollowsTheEventOrWaitsForIt() {
		// the event came: its clock
		assertEquals(0.3, UryuVollstTiming.releaseTime(0.3, 0.05), EPS);
		assertEquals(1.7, UryuVollstTiming.releaseTime(1.7, 3.0), EPS);
		// the attachment came, the event did not yet: hidden for the pending wait
		assertTrue(UryuVollstTiming.releaseTime(-1.0, 0.0) < 0);
		assertTrue(UryuVollstTiming.releaseTime(-1.0, UryuVollstTiming.PENDING_WAIT - 0.01) < 0);
		// no event ever came: the unfold starts by itself right after the wait
		assertEquals(UryuVollstTiming.T_UNFOLD, UryuVollstTiming.releaseTime(-1.0, UryuVollstTiming.PENDING_WAIT), EPS);
		assertEquals(UryuVollstTiming.T_UNFOLD + 0.5, UryuVollstTiming.releaseTime(-1.0, UryuVollstTiming.PENDING_WAIT + 0.5), EPS);
		// a player seen settled (the bankai was running when he came into view): everything is there
		assertTrue(UryuVollstTiming.releaseTime(-1.0, 100.0) > 100.0);
		assertFalse(UryuVollstTiming.releaseTime(-1.0, 100.0) < UryuVollstTiming.T_BANDS + UryuVollstTiming.BANDS_SWEEP);
	}

	@Test
	void theHashIsDeterministicAndInTheUnitInterval() {
		for (long s = 0; s < 200; s++) {
			for (int salt = 1; salt < 5; salt++) {
				double h = UryuVollstTiming.hash(s, salt);
				assertTrue(h >= 0 && h < 1);
				assertEquals(h, UryuVollstTiming.hash(s, salt), 0.0);
			}
		}
		assertTrue(UryuVollstTiming.hash(1, 1) != UryuVollstTiming.hash(1, 2));
	}
}
