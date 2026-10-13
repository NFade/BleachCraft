package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.MechSpec;
import org.junit.jupiter.api.Test;

/** r8 item 6: the barrier push (pure math; the entity filter lives in server/BarrierShove). */
class BarrierPushTest {
	private static final double SPRINT = 0.28; // blocks per tick of a sprinting player
	private static final double GROUND_FRICTION = 0.6 * 0.91;

	@Test
	void defaultsAreTheDocumentedNumbers() {
		BalanceConfig c = BalanceConfig.defaults();
		assertEquals(3.5, c.barrierPushRadius(), 1e-9);
		assertEquals(0.6, c.barrierPushStrength(), 1e-9);
		assertEquals(0.18, c.barrierPushUp(), 1e-9);
		assertEquals(2, c.barrierPushIntervalTicks());
	}

	@Test
	void insideMeansWithinTheRadiusPlusTheHalfWidthAndTheHeightBand() {
		assertTrue(BarrierPush.inside(3.0, 0.0, 0.0, 0.3, 3.5, 2.5));
		assertTrue(BarrierPush.inside(3.7, 0.0, 0.0, 0.3, 3.5, 2.5), "a wide mob counts by its edge");
		assertFalse(BarrierPush.inside(3.9, 0.0, 0.0, 0.3, 3.5, 2.5));
		assertTrue(BarrierPush.inside(2.0, 0.0, 2.0, 0.3, 3.5, 2.5), "diagonal: 2.83 away");
		assertFalse(BarrierPush.inside(2.8, 0.0, 2.8, 0.3, 3.5, 2.5), "diagonal: 3.96 away");
		assertFalse(BarrierPush.inside(1.0, 3.0, 0.0, 0.3, 3.5, 2.5), "far above");
		assertFalse(BarrierPush.inside(1.0, -3.0, 0.0, 0.3, 3.5, 2.5), "far below");
		assertTrue(BarrierPush.inside(0.0, 0.0, 0.0, 0.3, 3.5, 2.5), "standing on the owner");
	}

	@Test
	void theHorizontalPushIsOutwardAndTheVerticalPartSmall() {
		double[] v = BarrierPush.velocity(0, 0, 0, 2.0, 0.0, 0.6, 0.18, 0.0, true, 0.0);
		assertEquals(0.6, v[0], 1e-9, "away from the owner along +x");
		assertEquals(0.0, v[2], 1e-9);
		assertEquals(0.18, v[1], 1e-9, "a small hop on the ground");
		double[] w = BarrierPush.velocity(0, 0, 0, 0.0, -3.0, 0.6, 0.18, 0.0, true, 0.0);
		assertEquals(-0.6, w[2], 1e-9, "away along -z");
		assertTrue(v[1] < 0.42, "less than a jump");
	}

	@Test
	void anEntityThatIsRushingInIsTurnedBackNotJustSlowed() {
		// moving inward at the sprint speed (toward the owner at the origin from +x): the old velocity is halved, then the push is added
		double[] v = BarrierPush.velocity(-SPRINT, 0, 0, 2.0, 0.0, 0.6, 0.18, 0.0, true, 0.0);
		assertTrue(v[0] > 0.4, "now moving out: " + v[0]);
	}

	@Test
	void theAirIsNotLaunchedAndKnockbackResistanceCutsThePush() {
		double[] air = BarrierPush.velocity(0, -0.3, 0, 2.0, 0.0, 0.6, 0.18, 0.0, false, 0.0);
		assertEquals(-0.3, air[1], 1e-9, "no upward kick in the air: the fall goes on");
		double[] half = BarrierPush.velocity(0, 0, 0, 2.0, 0.0, 0.6, 0.18, 0.5, true, 0.0);
		assertEquals(0.3, half[0], 1e-9, "resistance 0.5 halves the push");
		double[] full = BarrierPush.velocity(0, 0, 0, 2.0, 0.0, 0.6, 0.18, 1.0, true, 0.0);
		assertEquals(0.0, full[0], 1e-9, "a fully resistant entity is not moved sideways");
		double[] over = BarrierPush.velocity(0, 0, 0, 2.0, 0.0, 0.6, 0.18, 7.0, true, 0.0);
		assertEquals(0.0, over[0], 1e-9, "resistance above 1 never turns the push around");
	}

	@Test
	void anEntityOnTheOwnerGetsAUsableDirection() {
		double[] v = BarrierPush.velocity(0, 0, 0, 0.0, 0.0, 0.6, 0.18, 0.0, true, Math.PI / 2);
		assertEquals(0.0, v[0], 1e-9);
		assertEquals(0.6, v[2], 1e-9, "the fallback angle decides");
		assertTrue(Math.hypot(v[0], v[2]) > 0.59);
	}

	@Test
	void aSprintingPlayerCannotWalkThroughTheDefaultPush() {
		BalanceConfig c = BalanceConfig.defaults();
		double outward = BarrierPush.averageOutwardSpeed(c.barrierPushStrength(), c.barrierPushIntervalTicks(), GROUND_FRICTION);
		assertTrue(outward > SPRINT * 1.8, "outward " + outward + " b/tick must clearly beat the sprint " + SPRINT);
		// the walker adds his own 0.28 inward every tick: the net is still outward, so he never gets through
		assertTrue(outward - SPRINT > 0.2, "net outward drift " + (outward - SPRINT));
	}

	@Test
	void aTooWeakPushWouldLetHimThrough() {
		// guards the test above: the model really can say "he gets through"
		assertTrue(BarrierPush.averageOutwardSpeed(0.1, 2, GROUND_FRICTION) < SPRINT);
	}

	@Test
	void theKnobsAreClamped() {
		MechSpec m = new MechSpec(9.0, -3.0, 0, -5.0, 99.0, 9.0, 0);
		assertEquals(1.0, m.abilityBlockReduction());
		assertEquals(0.0, m.meleeHitReiatsu());
		assertEquals(1, m.byakuyaAttackTickInterval());
		assertEquals(0.0, m.barrierPushRadius());
		assertEquals(4.0, m.barrierPushStrength());
		assertEquals(1.0, m.barrierPushUp());
		assertEquals(1, m.barrierPushIntervalTicks());
		assertEquals(0.0, new MechSpec(Double.NaN, Double.NaN, 4, Double.NaN, Double.NaN, Double.NaN, 2).abilityBlockReduction());
	}
}
