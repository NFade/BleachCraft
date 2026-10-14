package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The vanilla draw curve and the power scaling of the basic shot. */
class BowDrawTest {
	@Test
	void powerFollowsTheVanillaCurve() {
		assertEquals(0.0, BowDraw.power(0), 1e-12);
		assertEquals((0.25 * 0.25 + 0.5) / 3.0, BowDraw.power(5), 1e-12);
		assertEquals((0.25 + 1.0) / 3.0, BowDraw.power(10), 1e-12, "half draw");
		assertEquals(1.0, BowDraw.power(20), 1e-12);
		assertEquals(1.0, BowDraw.power(200), 1e-12, "capped");
		assertEquals(0.0, BowDraw.power(-3), 1e-12);
	}

	@Test
	void powerRisesWithEveryTick() {
		double last = -1;
		for (int t = 0; t <= 20; t++) {
			assertTrue(BowDraw.power(t) > last);
			last = BowDraw.power(t);
		}
	}

	@Test
	void releasesUnderPointOneFireNothing() {
		assertFalse(BowDraw.fires(BowDraw.power(2)), "2 ticks give 0.07: too weak");
		assertTrue(BowDraw.fires(BowDraw.power(3)), "3 ticks give 0.1075");
		assertFalse(BowDraw.fires(0.099));
		assertTrue(BowDraw.fires(0.1));
	}

	@Test
	void fullDrawGivesTheBaseDamageAndSpeed() {
		Shots.Params p = Shots.basic(0, false, BowDraw.power(20));
		assertEquals(UryuBalance.BASIC_DAMAGE, p.damage(), 1e-6);
		assertEquals(UryuBalance.ARROW_SPEED, p.speed(), 1e-9);
	}

	@Test
	void shortDrawsAreWeakerAndSlowerButStillFly() {
		Shots.Params half = Shots.basic(0, false, BowDraw.power(10));
		Shots.Params full = Shots.basic(0, false, 1.0);
		assertTrue(half.damage() < full.damage());
		assertTrue(half.speed() < full.speed());
		assertTrue(half.speed() > full.speed() * UryuBalance.BOW_MIN_SPEED_SHARE - 1e-9);
		Shots.Params tiny = Shots.basic(0, false, 0.1);
		assertEquals(0.4f, tiny.damage(), 1e-5);
	}

	@Test
	void focusAndArterieStillStackOnTheDraw() {
		assertEquals(4.0f * 1.2f * 2.0f, Shots.basic(3, true, 1.0).damage(), 1e-4);
		assertEquals(4.0f * 0.5f * 1.25f, Shots.basic(1, false, 0.5).damage(), 1e-4);
	}

	@Test
	void everyShotHasHomingWeakForTheBasicStrongForZ() {
		Homing.Params basic = Shots.basic(0, false).homing();
		Homing.Params z = Shots.focusShot(0).homing();
		assertEquals(Math.toRadians(2.5), basic.turnRad(), 1e-12);
		assertEquals(14.0, basic.radius(), 1e-12);
		assertEquals(Math.toRadians(60.0), basic.coneRad(), 1e-12);
		assertEquals(Math.toRadians(10.0), z.turnRad(), 1e-12);
		assertEquals(20.0, z.radius(), 1e-12);
		assertEquals(Math.toRadians(100.0), z.coneRad(), 1e-12);
		assertTrue(z.turnRad() > basic.turnRad());
	}
}
