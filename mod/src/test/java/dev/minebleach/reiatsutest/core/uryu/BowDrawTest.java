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
		assertEquals((0.25 * 0.25 + 0.5) / 3.0, BowDraw.power(6), 1e-12, "a quarter of the 24 ticks");
		assertEquals((0.25 + 1.0) / 3.0, BowDraw.power(12), 1e-12, "half of the time");
		assertEquals(1.0, BowDraw.power(24), 1e-12);
		assertEquals(1.0, BowDraw.power(200), 1e-12, "capped");
		assertEquals(0.0, BowDraw.power(-3), 1e-12);
	}

	@Test
	void powerRisesWithEveryTick() {
		double last = -1;
		for (int t = 0; t <= 24; t++) {
			assertTrue(BowDraw.power(t) > last);
			last = BowDraw.power(t);
		}
	}

	@Test
	void releasesUnderHalfPowerFireNothing() { // R13: minPower 0.5 (was 0.1)
		assertFalse(BowDraw.fires(BowDraw.power(13)), "13 ticks give 0.47: too weak");
		assertTrue(BowDraw.fires(BowDraw.power(14)), "14 ticks give 0.52");
		assertEquals(14, BowDraw.minDrawTicks());
		assertFalse(BowDraw.fires(0.499));
		assertTrue(BowDraw.fires(0.5));
	}

	@Test
	void tapSpamIsOutAndTheFastestShotNeedsAboutAThirdOfASecondMoreThanBefore() {
		int old = 3; // 3 ticks gave power 0.1075 and fired under the old numbers
		assertFalse(BowDraw.fires(BowDraw.power(old)));
		assertTrue(BowDraw.minDrawTicks() / 20.0 >= 0.7 - 1e-9, "at least 0.7 s of drawing");
	}

	@Test
	void fullDrawGivesTheBaseDamageAndSpeed() {
		Shots.Params p = Shots.basic(0, false, BowDraw.power(24));
		assertEquals(UryuBalance.BASIC_DAMAGE, p.damage(), 1e-6);
		assertEquals(UryuBalance.ARROW_SPEED, p.speed(), 1e-9);
	}

	@Test
	void shortDrawsAreWeakerAndSlowerButStillFly() {
		Shots.Params half = Shots.basic(0, false, BowDraw.power(12));
		Shots.Params full = Shots.basic(0, false, 1.0);
		assertTrue(half.damage() < full.damage());
		assertTrue(half.speed() < full.speed());
		assertTrue(half.speed() > full.speed() * UryuBalance.BOW_MIN_SPEED_SHARE - 1e-9);
		Shots.Params tiny = Shots.basic(0, false, 0.1);
		assertEquals(0.3f, tiny.damage(), 1e-5);
	}

	@Test
	void focusAndArterieStillStackOnTheDraw() {
		assertEquals(3.0f * 1.2f * 2.0f, Shots.basic(3, true, 1.0).damage(), 1e-4);
		assertEquals(3.0f * 0.5f * 1.25f, Shots.basic(1, false, 0.5).damage(), 1e-4);
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
