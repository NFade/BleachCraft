package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.uryu.BindEnd;
import org.junit.jupiter.api.Test;

/** The bind visual (storyboard 2.8): sizes, rings, chains and what each end reason looks like. */
class BindFxPlanTest {
	@Test
	void footSigilSizeIsEightTenthsOfTheWidthPlusNineTenths() {
		assertEquals(1.38, BindFxPlan.sigilSize(0.6), 1e-9, "a zombie or a player");
		assertEquals(1.7, BindFxPlan.sigilSize(1.0), 1e-9);
		assertTrue(BindFxPlan.sigilSize(1.4) > BindFxPlan.sigilSize(0.6));
	}

	@Test
	void theSigilFlickersAtEightHertzBetweenSixAndTen() {
		double min = 9;
		double max = 0;
		for (double t = 0; t < 1.0; t += 0.001) {
			double v = BindFxPlan.sigilFlicker(t, false);
			min = Math.min(min, v);
			max = Math.max(max, v);
		}
		assertEquals(0.6, min, 0.01);
		assertEquals(1.0, max, 0.01);
		// 8 Hz: the value repeats every 1/8 s
		assertEquals(BindFxPlan.sigilFlicker(0.03, false), BindFxPlan.sigilFlicker(0.03 + 0.125, false), 1e-9);
		assertEquals(0.8, BindFxPlan.sigilFlicker(0.123, true), 1e-9, "reduce motion: steady");
		assertEquals(0.0, BindFxPlan.sigilRotation(5.0, true), 1e-9, "reduce motion: no rotation");
		assertEquals(Math.PI, BindFxPlan.sigilRotation(1.0, false), 1e-9, "half a turn per second");
	}

	@Test
	void theThreeRingsSitAtTheLegsAndGrowWithTheEntity() {
		assertEquals(3, BindFxPlan.RING_HEIGHTS.length);
		assertEquals(0.12, BindFxPlan.ringHeight(0, 1.8), 1e-9);
		assertEquals(0.45, BindFxPlan.ringHeight(1, 1.8), 1e-9);
		assertEquals(0.78, BindFxPlan.ringHeight(2, 1.8), 1e-9);
		assertEquals(0.78 * 2.0, BindFxPlan.ringHeight(2, 3.6), 1e-9);
		assertEquals(0.78 * 2.0, BindFxPlan.ringHeight(2, 10.0), 1e-9, "scale clamped to 2");
		assertEquals(0.78 * 0.6, BindFxPlan.ringHeight(2, 0.5), 1e-9, "and to 0.6");
		assertEquals(0.4, BindFxPlan.ringRadius(0.6), 1e-9);
		assertEquals(0.32, BindFxPlan.ringRadius(0.3), 1e-9, "a small entity keeps 80 percent of the reference ring");
		assertTrue(BindFxPlan.ringRadius(1.2) > 0.4);
		assertEquals(1.0, BindFxPlan.ringTurns(0), 1e-9);
		assertEquals(-1.0, BindFxPlan.ringTurns(1), 1e-9);
		assertEquals(1.0, BindFxPlan.ringTurns(2), 1e-9);
	}

	@Test
	void aChainSagsThroughTwoSegments() {
		double[] from = {3, 2, 0};
		double[] to = {0, 1, 0};
		double[] m = BindFxPlan.chainMiddle(from, to);
		assertArrayEquals(new double[] {1.5, 1.5 - BindFxPlan.CHAIN_SAG, 0}, m, 1e-9);
		assertArrayEquals(from, BindFxPlan.chainPoint(from, to, 0.0), 1e-9);
		assertArrayEquals(m, BindFxPlan.chainPoint(from, to, 0.5), 1e-9);
		assertArrayEquals(to, BindFxPlan.chainPoint(from, to, 1.0), 1e-9);
		double[] q = BindFxPlan.chainPoint(from, to, 0.25);
		assertEquals((from[0] + m[0]) / 2, q[0], 1e-9);
		assertEquals((from[1] + m[1]) / 2, q[1], 1e-9);
		assertArrayEquals(from, BindFxPlan.chainPoint(from, to, -3.0), 1e-9, "clamped");
		assertEquals(5, BindFxPlan.CHAINS);
	}

	@Test
	void theLightRunningDownTheChainsIsStaggeredByAFifth() {
		for (int i = 0; i < BindFxPlan.CHAINS; i++) {
			double p = BindFxPlan.pulseAt(0.0, i);
			assertEquals(i / 5.0, p, 1e-9);
			assertTrue(BindFxPlan.pulseAt(7.77, i) >= 0.0 && BindFxPlan.pulseAt(7.77, i) < 1.0);
		}
		assertEquals(0.5, BindFxPlan.pulseAt(1.0, 0), 1e-9, "1.5 passes per second");
	}

	@Test
	void onlyTheNearestThreeGetTheFullVisual() {
		assertEquals(3, BindFxPlan.MAX_FULL);
		assertArrayEquals(new int[] {3, 1, 4}, BindFxPlan.nearest(new double[] {9, 2, 12, 1, 5, 20}, 3));
		assertArrayEquals(new int[] {1, 0}, BindFxPlan.nearest(new double[] {5, 2}, 3), "fewer entities than slots");
		assertArrayEquals(new int[0], BindFxPlan.nearest(new double[0], 3));
		assertEquals(2, BindFxPlan.SOUND_SLOTS);
		assertEquals(0.8, BindFxPlan.SOUND_EVERY, 1e-9);
	}

	@Test
	void everyEndReasonHasItsOwnLook() {
		BindFxPlan.EndLook d = BindFxPlan.endLook(BindEnd.DAMAGE);
		assertSame(BindFxPlan.DAMAGE, d);
		assertTrue(d.snapChains() && d.shatterRings() && d.glassSound());
		assertEquals(12, d.flakes());
		assertEquals(0.15, d.sigilFade(), 1e-9, "the sigil goes out in 0.15 s");
		for (BindEnd r : new BindEnd[] {BindEnd.EXPLOSION, BindEnd.EXPIRE, BindEnd.CANCEL}) {
			BindFxPlan.EndLook l = BindFxPlan.endLook(r);
			assertFalse(l.snapChains() || l.shatterRings() || l.glassSound(), r + " is a quiet fade");
			assertEquals(0, l.flakes());
		}
		assertEquals(0.10, BindFxPlan.EXPLOSION.sigilFade(), 1e-9, "the explosion covers the end");
		assertEquals(0.30, BindFxPlan.CANCEL.sigilFade(), 1e-9);
		assertSame(BindFxPlan.CANCEL, BindFxPlan.endLook(BindEnd.fromCode(99)), "an unknown code is a cancel");
		for (BindEnd r : BindEnd.values()) {
			assertTrue(BindFxPlan.endLook(r) != null);
		}
	}

	@Test
	void endingsFadeAndBurstOverTheirTimes() {
		assertEquals(1.0, BindFxPlan.endFade(-1.0, 0.15), 1e-9, "still bound");
		assertEquals(1.0, BindFxPlan.endFade(0.0, 0.15), 1e-9);
		assertEquals(0.0, BindFxPlan.endFade(0.15, 0.15), 1e-9);
		assertEquals(0.5, BindFxPlan.endFade(0.075, 0.15), 1e-9);
		assertEquals(1.0, BindFxPlan.ringBurst(0.0, 0.25), 1e-9);
		assertEquals(3.0, BindFxPlan.ringBurst(0.25, 0.25), 1e-9, "the rings shatter outward to three times the radius");
		assertEquals(1.0, BindFxPlan.snapRemaining(0.0), 1e-9);
		assertEquals(0.0, BindFxPlan.snapRemaining(BindFxPlan.SNAP_TIME), 1e-9);
		assertEquals(0.0, BindFxPlan.rampIn(0.0), 1e-9);
		assertEquals(1.0, BindFxPlan.rampIn(0.15), 1e-9);
		assertEquals(1.0, BindFxPlan.rampIn(5.0), 1e-9);
		assertEquals(0.5, BindFxPlan.rampIn(0.075), 1e-9);
	}

	@Test
	void theResonanceIsTheStoryboardSound() {
		assertEquals("block.amethyst_block.resonate", BindFxPlan.SOUND_ID);
		assertEquals(0.9, BindFxPlan.SOUND_PITCH, 1e-9);
		assertEquals(0.3, BindFxPlan.SOUND_VOLUME, 1e-9);
		assertEquals(0.7, BindFxPlan.CHEST, 1e-9);
	}
}
