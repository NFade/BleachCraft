package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilityParams;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import org.junit.jupiter.api.Test;

/** The petal cage picture against the real ability spec (r8 fx2 item 2): the area and the pulse times are the damage's. */
class CageVisualTest {
	private static final AbilitySpec SPEC = BalanceConfig.defaults().spec(AbilityId.CAGE);

	@Test
	void theDrawnAreaIsTheSpecSphereAndThePulsesAreTheDamageTicks() {
		float[] params = CageVisual.paramsOf(SPEC, 10, 70, -4);
		CageVisual.Plan plan = CageVisual.parse(params, 0, 0, 0);
		assertEquals(10.0, plan.cx());
		assertEquals(70.0, plan.cy());
		assertEquals(-4.0, plan.cz(), "the area centre comes from the params, not from the event position (the caster)");
		assertEquals(SPEC.num(AbilityParams.RADIUS), plan.radius(), 1e-6, "the drawn radius is the server's hit radius");
		assertEquals(SPEC.phaseOffsets().size(), plan.pulses());
		double[] times = CageVisual.pulseTimes(plan);
		for (int k = 0; k < times.length; k++) {
			assertEquals(SPEC.phaseOffsets().get(k) / 20.0, times[k], 1e-4, "pulse " + k + " is drawn at the tick the server hurts at");
		}
		assertEquals(SPEC.phaseOffsets().get(SPEC.phaseOffsets().size() - 1) / 20.0, plan.lastPulse(), 1e-4);
	}

	@Test
	void missingParamsFallBackToTheEventPositionAndSaneDefaults() {
		CageVisual.Plan p = CageVisual.parse(new float[0], 1, 2, 3);
		assertEquals(1.0, p.cx());
		assertEquals(2.0, p.cy());
		assertEquals(3.0, p.cz());
		assertEquals(5.0, p.radius());
		assertEquals(6, p.pulses());
		assertEquals(0.6, p.first(), 1e-9);
		assertEquals(0.5, p.spacing(), 1e-9);
		CageVisual.Plan q = CageVisual.parse(null, 0, 0, 0);
		assertEquals(6, q.pulses());
	}

	@Test
	void brokenParamsAreClamped() {
		CageVisual.Plan p = CageVisual.parse(new float[] {0, 0, 0, -3f, -1f, 9999f, 0f}, 0, 0, 0);
		assertTrue(p.radius() >= CageVisual.MIN_RADIUS);
		assertTrue(p.first() >= 0.1);
		assertEquals(CageVisual.MAX_PULSES, p.pulses());
		assertTrue(p.spacing() >= CageVisual.MIN_SPACING);
		assertEquals(1, CageVisual.parse(new float[] {0, 0, 0, 5f, 0.6f, 0f, 0.5f}, 0, 0, 0).pulses());
	}

	@Test
	void groundRingIsTheExactReachOfTheSphereOnAStandingCreature() {
		// the centre in the middle of a body (aimed at a mob): the whole radius, plus the half width of the box
		assertEquals(5.3, CageVisual.groundRingRadius(5.0, 0.9, 1.8, 0.3), 1e-9);
		// the aimed point on the ground lifted 1.6: still inside the body height
		assertEquals(5.3, CageVisual.groundRingRadius(5.0, 1.6, 1.8, 0.3), 1e-9);
		// a centre 3 blocks above the head: dy = 3 - ... the sphere reaches the head box in a circle of sqrt(25 - dy^2)
		double dy = 4.8 - 1.8;
		assertEquals(Math.sqrt(25 - dy * dy) + 0.3, CageVisual.groundRingRadius(5.0, 4.8, 1.8, 0.3), 1e-9);
		// a centre more than a radius above the head reaches nothing on the ground
		assertEquals(0.0, CageVisual.groundRingRadius(5.0, 7.0, 1.8, 0.3));
		// the ring never grows with the height
		double prev = Double.MAX_VALUE;
		for (double h = 0; h < 7.0; h += 0.25) {
			double r = CageVisual.groundRingRadius(5.0, h, 1.8, 0.3);
			assertTrue(r <= prev + 1e-9, "monotonic at " + h);
			prev = r;
		}
	}

	@Test
	void pulsesGetBiggerAtTheEnds() {
		assertEquals(1.2, CageVisual.pulseScale(0, 6));
		assertEquals(1.0, CageVisual.pulseScale(2, 6));
		assertEquals(1.8, CageVisual.pulseScale(5, 6), "the last one is the final burst");
		assertEquals(1.8, CageVisual.pulseScale(0, 1), "a single pulse is the final one");
	}

	@Test
	void warningTicksComeBeforeThePulseAndNeverOverlapTheLastOne() {
		CageVisual.Plan plan = CageVisual.parse(CageVisual.paramsOf(SPEC, 0, 0, 0), 0, 0, 0);
		for (int k = 0; k < plan.pulses(); k++) {
			double w = CageVisual.warnTime(plan, k, 0.15);
			assertTrue(w < plan.pulseTime(k), "warning " + k + " ahead of its pulse");
			assertTrue(w >= (k == 0 ? 0.05 : plan.pulseTime(k - 1) + 0.05) - 1e-9);
		}
		// a lead longer than the spacing is held back after the previous pulse
		assertEquals(plan.pulseTime(2) + 0.05, CageVisual.warnTime(plan, 3, 5.0), 1e-9);
	}

	@Test
	void convergingPetalsArriveWithTheFirstPulse() {
		// a petal 6.5 blocks out (1.3 R) flying for 0.6 s: 10.83 blocks per second; after the arrival time it is at the centre
		double v = CageVisual.convergeSpeed(6.5, 0.6);
		assertEquals(6.5, v * 0.6, 1e-9);
		assertEquals(0.0, CageVisual.convergeSpeed(6.5, 0.0));
		// the area law: a double radius gives 2.2 times (cap), a half radius 0.5 times (floor)
		assertEquals(24, CageVisual.convergeCount(24, 5.0));
		assertEquals(53, CageVisual.convergeCount(24, 10.0));
		assertEquals(12, CageVisual.convergeCount(24, 2.0));
	}

	@Test
	void shockwaveRingGrowsToTheExactRadius() {
		assertEquals(0.5, CageVisual.shockRadius(5.0, 0.0, 0.3), 1e-9);
		assertEquals(5.0, CageVisual.shockRadius(5.0, 0.3, 0.3), 1e-9, "the ring ends on the hit radius at the end of its life");
		assertTrue(CageVisual.shockRadius(5.0, 0.15, 0.3) > 2.75, "ease out");
	}

	@Test
	void specIsTheOneThePictureWasWrittenFor() {
		// if the balance changes, the picture follows by itself (nothing is hard coded); this documents the numbers of today
		assertEquals(5.0, SPEC.num(AbilityParams.RADIUS));
		assertEquals(6, SPEC.phaseOffsets().size());
		assertEquals(12, SPEC.phaseOffsets().get(0));
		assertEquals(10, SPEC.phaseOffsets().get(1) - SPEC.phaseOffsets().get(0));
	}
}
