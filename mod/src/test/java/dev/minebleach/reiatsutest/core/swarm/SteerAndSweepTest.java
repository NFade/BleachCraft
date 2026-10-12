package dev.minebleach.reiatsutest.core.swarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Turning limit, radial step, cone check, swept sphere and the per target hit cooldown of the blade flight. */
class SteerAndSweepTest {
	private static final V3 X = new V3(1, 0, 0);
	private static final V3 Y = new V3(0, 1, 0);

	@Test
	void turnIsLimitedAndExactWhenInReach() {
		V3 t = SteerMath.turnToward(X, Y, Math.toRadians(10));
		assertEquals(10.0, Math.toDegrees(SteerMath.angle(X, t)), 1e-6);
		assertEquals(1.0, t.length(), 1e-9);
		V3 reach = SteerMath.turnToward(X, Y, Math.toRadians(100));
		assertEquals(0.0, SteerMath.angle(reach, Y), 1e-9);
	}

	@Test
	void turnConvergesInTheExpectedNumberOfSteps() {
		V3 d = X;
		int steps = 0;
		while (SteerMath.angle(d, Y) > 1e-9 && steps < 100) {
			d = SteerMath.turnToward(d, Y, Math.toRadians(9));
			steps++;
		}
		assertEquals(10, steps); // 90 degrees at 9 degrees per tick
	}

	@Test
	void oppositeDirectionStillTurns() {
		V3 t = SteerMath.turnToward(X, new V3(-1, 0, 0), Math.toRadians(20));
		assertEquals(20.0, Math.toDegrees(SteerMath.angle(X, t)), 1e-6);
		assertTrue(t.isFinite());
	}

	@Test
	void farSwarmTurnsSlowerSoItDoesNotWhipAround() {
		double near = SteerMath.maxTurnPerTick(Math.toRadians(10), 1.5, 2.0);
		double far = SteerMath.maxTurnPerTick(Math.toRadians(10), 1.5, 30.0);
		assertEquals(Math.toRadians(10), near, 1e-9);
		assertEquals(1.5 / 30.0, far, 1e-9);
		assertTrue(far < near);
	}

	@Test
	void stepTowardNeverOvershoots() {
		assertEquals(3.5, SteerMath.stepToward(2.0, 10.0, 1.5), 1e-12);
		assertEquals(10.0, SteerMath.stepToward(9.5, 10.0, 1.5), 1e-12);
		assertEquals(0.5, SteerMath.stepToward(2.0, -10.0, 1.5), 1e-12);
	}

	@Test
	void coneKeepsNearDirectionsAndReplacesFarOnes() {
		V3 near = SteerMath.turnToward(X, Y, Math.toRadians(10));
		assertEquals(near, SteerMath.withinCone(near, X, Math.toRadians(35)));
		assertEquals(X, SteerMath.withinCone(Y, X, Math.toRadians(35)));
	}

	private static double[] box(double cx, double cy, double cz, double hx, double hy, double hz) {
		return new double[] {cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz};
	}

	@Test
	void fastSweepHitsATargetThatNeitherEndOfTheStepTouches() {
		// target slab x 0.6..0.9 on a 1.5 block step with radius 0.5: both ends are 0.6 away from it, the middle touches it
		double[] b = box(0.75, 0, 0, 0.15, 1, 1);
		V3 a = new V3(0, 0, 0);
		V3 c = new V3(1.5, 0, 0);
		assertFalse(SweepMath.sphereTouchesBox(a, 0.5, b[0], b[1], b[2], b[3], b[4], b[5]));
		assertFalse(SweepMath.sphereTouchesBox(c, 0.5, b[0], b[1], b[2], b[3], b[4], b[5]));
		double t = SweepMath.sweepSphereVsBox(a, c, 0.5, b[0], b[1], b[2], b[3], b[4], b[5]);
		assertTrue(t >= 0.0 && t <= 1.0, "swept hit expected, t=" + t);
		// a radius 0.2 sphere still passes through the slab on the full step, but a short step that stops at x 0.3 never reaches it
		assertTrue(SweepMath.sweepSphereVsBox(a, c, 0.2, b[0], b[1], b[2], b[3], b[4], b[5]) >= 0.0);
		assertEquals(-1.0, SweepMath.sweepSphereVsBox(a, new V3(0.3, 0, 0), 0.2, b[0], b[1], b[2], b[3], b[4], b[5]));
	}

	@Test
	void sweepHitsTunnellingTargetsAtAnySpeed() {
		// a wall 0.1 thick, the sphere steps 6 blocks in one go and ends far behind it
		double[] wall = box(3.0, 0, 0, 0.05, 2, 2);
		double t = SweepMath.sweepSphereVsBox(new V3(0, 0, 0), new V3(6, 0, 0), 0.4, wall[0], wall[1], wall[2], wall[3], wall[4], wall[5]);
		assertTrue(t > 0.3 && t < 0.6, "t=" + t);
		assertFalse(SweepMath.sphereTouchesBox(new V3(6, 0, 0), 0.4, wall[0], wall[1], wall[2], wall[3], wall[4], wall[5]));
	}

	@Test
	void sweepMissesWhatIsOffThePath() {
		double[] b = box(0.75, 5, 0, 0.3, 0.3, 0.3);
		assertEquals(-1.0, SweepMath.sweepSphereVsBox(new V3(0, 0, 0), new V3(1.5, 0, 0), 1.0, b[0], b[1], b[2], b[3], b[4], b[5]));
	}

	@Test
	void zeroLengthSweepIsAPlainOverlap() {
		double[] b = box(0, 0, 0, 0.5, 0.5, 0.5);
		assertEquals(0.0, SweepMath.sweepSphereVsBox(new V3(1.0, 0, 0), new V3(1.0, 0, 0), 0.6, b[0], b[1], b[2], b[3], b[4], b[5]));
		assertEquals(-1.0, SweepMath.sweepSphereVsBox(new V3(3.0, 0, 0), new V3(3.0, 0, 0), 0.6, b[0], b[1], b[2], b[3], b[4], b[5]));
	}

	@Test
	void startingInsideReportsContactAtOnce() {
		double[] b = box(0, 0, 0, 1, 1, 1);
		assertEquals(0.0, SweepMath.sweepSphereVsBox(new V3(0, 0, 0), new V3(4, 0, 0), 0.5, b[0], b[1], b[2], b[3], b[4], b[5]));
	}

	@Test
	void cornersAreRoundedNotSquare() {
		// the grown box test alone would accept the corner; the exact sphere test must refuse it
		double[] b = box(0, 0, 0, 1, 1, 1);
		V3 p = new V3(1.45, 1.45, 1.45);
		assertFalse(SweepMath.sphereTouchesBox(p, 0.5, b[0], b[1], b[2], b[3], b[4], b[5]));
		assertEquals(-1.0, SweepMath.sweepSphereVsBox(p, p.add(new V3(0.1, 0, 0)), 0.5, b[0], b[1], b[2], b[3], b[4], b[5]));
	}

	@Test
	void cooldownLimitsHitsPerLegAndAllowsTheReturnPass() {
		HitCooldown c = new HitCooldown(8, 6);
		assertTrue(c.tryHit(7, 100, HitCooldown.LEG_OUT));
		assertFalse(c.tryHit(7, 101, HitCooldown.LEG_OUT));
		assertFalse(c.tryHit(7, 107, HitCooldown.LEG_OUT));
		assertTrue(c.tryHit(7, 108, HitCooldown.LEG_OUT));
		// the return leg may hit after half the cooldown
		assertFalse(c.tryHit(7, 110, HitCooldown.LEG_BACK));
		assertTrue(c.tryHit(7, 112, HitCooldown.LEG_BACK));
		// another target is independent
		assertTrue(c.tryHit(8, 112, HitCooldown.LEG_OUT));
		assertEquals(3, c.hits(7));
	}

	@Test
	void hitCapStopsEndlessHitting() {
		HitCooldown c = new HitCooldown(2, 3);
		int hits = 0;
		for (int tick = 0; tick < 100; tick++) {
			if (c.tryHit(1, tick, HitCooldown.LEG_OUT)) {
				hits++;
			}
		}
		assertEquals(3, hits);
	}

	@Test
	void landingHitsRestartTheCooldownButDoNotCountAgainstTheCap() {
		HitCooldown c = new HitCooldown(8, 1);
		c.markOther(5, 50);
		assertFalse(c.ready(5, 54, HitCooldown.LEG_OUT));
		assertTrue(c.ready(5, 58, HitCooldown.LEG_OUT));
		assertEquals(0, c.hits(5));
		assertTrue(c.tryHit(5, 58, HitCooldown.LEG_OUT));
		assertFalse(c.tryHit(5, 100, HitCooldown.LEG_OUT)); // cap of 1 flight hit
	}
}
