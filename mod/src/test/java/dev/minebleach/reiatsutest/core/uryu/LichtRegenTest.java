package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.swarm.V3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Licht Regen: the square, the schedule, the slant, the per target cap and the safe caster. */
class LichtRegenTest {
	private static final V3 CENTRE = new V3(100.5, 64.0, -30.25);

	private static List<LichtRegen.Drop> all(LichtRegen r) {
		List<LichtRegen.Drop> out = new ArrayList<>();
		for (int k = 0; k < UryuBalance.RAIN_TICKS; k++) {
			for (int i = 0; i < LichtRegen.arrowsAt(k); i++) {
				out.add(r.drop(k, i));
			}
		}
		return out;
	}

	@Test
	void numbersOfTheDesign() {
		assertEquals(28.0, UryuBalance.RAIN_RANGE);
		assertEquals(9.0, UryuBalance.RAIN_HALF_WIDTH * 2.0);
		assertEquals(14.0, UryuBalance.RAIN_HEIGHT);
		assertEquals(60, UryuBalance.RAIN_TICKS, "3.0 s");
		assertEquals(6, UryuBalance.RAIN_PER_TICK);
		assertEquals(2.6, UryuBalance.RAIN_SPEED);
		assertEquals(1.0f, UryuBalance.RAIN_DAMAGE);
		assertEquals(14.0f, UryuBalance.RAIN_TARGET_CAP);
		assertTrue(UryuBalance.MAX_ARROWS >= 400);
	}

	@Test
	void sixArrowsPerTickForSixtyTicksThenNothing() {
		assertEquals(0, LichtRegen.arrowsAt(-1));
		for (int k = 0; k < 60; k++) {
			assertEquals(6, LichtRegen.arrowsAt(k), "tick " + k);
			assertTrue(LichtRegen.active(k));
		}
		assertEquals(0, LichtRegen.arrowsAt(60));
		assertFalse(LichtRegen.active(60));
		assertEquals(360, LichtRegen.totalArrows());
	}

	@Test
	void everyArrowLandsInsideTheNineByNineSquareAndStartsFourteenBlocksUp() {
		for (long seed : new long[] {1L, 77L, -5L, 123456789L}) {
			LichtRegen r = new LichtRegen(1, seed, CENTRE);
			for (LichtRegen.Drop d : all(r)) {
				assertTrue(r.inSquare(d.landing().x(), d.landing().z()), "seed " + seed + " landing " + d.landing());
				assertEquals(CENTRE.y(), d.landing().y(), 1e-9);
				assertEquals(CENTRE.y() + 14.0, d.start().y(), 1e-6, "14 blocks above the ground level of the centre");
				assertEquals(UryuBalance.RAIN_SPEED, d.vel().length(), 1e-9);
				assertTrue(d.vel().y() < 0.0);
				// the fall line from the start really ends on the landing point
				double t = (d.start().y() - d.landing().y()) / -d.vel().y();
				assertEquals(d.landing().x(), d.start().x() + d.vel().x() * t, 1e-6);
				assertEquals(d.landing().z(), d.start().z() + d.vel().z() * t, 1e-6);
			}
		}
	}

	@Test
	void theSlantIsSmallAndTheSameForAllArrowsOfOneCast() {
		LichtRegen r = new LichtRegen(1, 42L, CENTRE);
		LichtRegen.Drop first = r.drop(0, 0);
		double tilt = Math.toDegrees(Math.atan2(Math.hypot(first.vel().x(), first.vel().z()), -first.vel().y()));
		assertTrue(tilt > 1.0 && tilt < 15.0, "slant " + tilt + " degrees");
		for (LichtRegen.Drop d : all(r)) {
			assertEquals(first.vel().x(), d.vel().x(), 1e-12);
			assertEquals(first.vel().z(), d.vel().z(), 1e-12);
		}
	}

	@Test
	void positionsAreDeterministicPerCastAndDifferBetweenCasts() {
		LichtRegen a = new LichtRegen(1, 9L, CENTRE);
		LichtRegen b = new LichtRegen(1, 9L, CENTRE);
		LichtRegen c = new LichtRegen(1, 10L, CENTRE);
		for (int k = 0; k < 60; k += 7) {
			for (int i = 0; i < 6; i++) {
				assertEquals(a.drop(k, i), b.drop(k, i));
			}
		}
		assertNotEquals(a.drop(3, 2).landing(), c.drop(3, 2).landing());
		assertNotEquals(a.drop(3, 2).landing(), a.drop(3, 3).landing(), "two arrows of one tick land apart");
		assertNotEquals(a.drop(3, 2).landing(), a.drop(4, 2).landing());
	}

	@Test
	void theWholeSquareIsCoveredAndEvenlyEnough() {
		LichtRegen r = new LichtRegen(1, 2024L, CENTRE);
		double minX = 1e9;
		double maxX = -1e9;
		double minZ = 1e9;
		double maxZ = -1e9;
		int[][] cells = new int[3][3];
		for (LichtRegen.Drop d : all(r)) {
			minX = Math.min(minX, d.landing().x());
			maxX = Math.max(maxX, d.landing().x());
			minZ = Math.min(minZ, d.landing().z());
			maxZ = Math.max(maxZ, d.landing().z());
			int cx = Math.min(2, (int) ((d.landing().x() - (CENTRE.x() - 4.5)) / 3.0));
			int cz = Math.min(2, (int) ((d.landing().z() - (CENTRE.z() - 4.5)) / 3.0));
			cells[cx][cz]++;
		}
		assertTrue(maxX - minX > 8.5 && maxZ - minZ > 8.5, "spread " + (maxX - minX) + " x " + (maxZ - minZ));
		for (int[] col : cells) {
			for (int n : col) {
				assertTrue(n > 20 && n < 60, "a 3 x 3 block cell got " + n + " of 360 arrows");
			}
		}
	}

	@Test
	void aTargetTakesAtMostFourteenHpPerCast() {
		LichtRegen r = new LichtRegen(1, 5L, CENTRE);
		float sum = 0f;
		for (int i = 0; i < 40; i++) {
			sum += r.allow(7, UryuBalance.RAIN_DAMAGE);
		}
		assertEquals(14.0f, sum, 1e-5);
		assertEquals(14.0f, r.dealt(7), 1e-5);
		assertEquals(0f, r.allow(7, 1f), "the cap is reached");
		assertEquals(1f, r.allow(8, 1f), "another target has its own budget");
	}

	@Test
	void theLastArrowBeforeTheCapIsCutToWhatIsLeft() {
		LichtRegen r = new LichtRegen(1, 5L, CENTRE);
		for (int i = 0; i < 13; i++) {
			r.allow(7, 1f);
		}
		assertEquals(0.5f, r.allow(7, 0.5f), 1e-6);
		assertEquals(0.5f, r.allow(7, 1f), 1e-6, "13.5 + 0.5 = 14, then it stops");
		assertEquals(0f, r.allow(7, 1f));
	}

	@Test
	void theCasterNeverTakesDamageFromHisOwnRain() {
		LichtRegen r = new LichtRegen(33, 5L, CENTRE);
		assertEquals(0f, r.allow(33, 1f));
		assertEquals(0f, r.dealt(33));
		assertEquals(1f, r.allow(34, 1f));
	}

	@Test
	void aStationaryTargetExpectsAbout8To9HpAndFarLessThanTheCapIsReachedByLuckOnly() {
		// a player (0.6 wide) seen from above with the arrow radius 0.4 on each side: a (0.6 + 0.8)^2 patch of the 81 m^2 square
		double patch = Math.pow(0.6 + 2 * UryuBalance.RAIN_ARROW_RADIUS, 2);
		double expected = LichtRegen.totalArrows() * patch / 81.0 * UryuBalance.RAIN_DAMAGE;
		assertTrue(expected > 7.0 && expected < 11.0, "expected HP " + expected);
		assertTrue(expected < UryuBalance.RAIN_TARGET_CAP);
	}
}
