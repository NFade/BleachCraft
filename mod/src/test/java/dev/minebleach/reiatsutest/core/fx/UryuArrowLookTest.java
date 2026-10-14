package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.uryu.ArrowSim;
import org.junit.jupiter.api.Test;

/** The flying and hitting Heilig Pfeil, URYU_VFX_STORYBOARD 2.3. */
class UryuArrowLookTest {
	private static final double EPS = 1e-9;

	@Test
	void theKindsAreTheOrdinalsOfTheSimulation() {
		assertEquals(ArrowSim.Kind.BASIC.ordinal(), UryuArrowLook.BASIC);
		assertEquals(ArrowSim.Kind.FOCUS.ordinal(), UryuArrowLook.Z);
		assertEquals(ArrowSim.Kind.RAIN.ordinal(), UryuArrowLook.RAIN);
		assertEquals(ArrowSim.Kind.WING.ordinal(), UryuArrowLook.WING);
	}

	@Test
	void theDrawPowerIsReadBackFromTheLaunchSpeed() {
		for (int focus = 0; focus <= 3; focus++) {
			for (double p : new double[] {0.1, 0.3, 0.55, 0.8, 1.0}) {
				double v = UryuArrowLook.launchSpeed(p, focus);
				assertEquals(p, UryuArrowLook.drawPower(v, focus, UryuArrowLook.BASIC), 1e-9, "focus " + focus + " p " + p);
			}
		}
		assertEquals(1.0, UryuArrowLook.drawPower(2.4, 0, UryuArrowLook.Z), EPS, "every other kind is full");
		assertEquals(0.0, UryuArrowLook.drawPower(0.1, 0, UryuArrowLook.BASIC), EPS, "clamped");
		assertEquals(1.0, UryuArrowLook.drawPower(99, 0, UryuArrowLook.BASIC), EPS);
	}

	@Test
	void theMeshScaleGrowsWithPowerAndFocusAndStopsAtThree() {
		assertEquals(1.4, UryuArrowLook.meshScale(UryuArrowLook.BASIC, 0.0, 0), EPS);
		assertEquals(2.2, UryuArrowLook.meshScale(UryuArrowLook.BASIC, 1.0, 0), EPS);
		assertEquals(1.4 + 0.8 * 0.5 + 0.4, UryuArrowLook.meshScale(UryuArrowLook.BASIC, 0.5, 1), EPS);
		assertEquals(3.0, UryuArrowLook.meshScale(UryuArrowLook.BASIC, 1.0, 3), EPS, "2.2 + 1.2 is capped");
		assertEquals(2.6, UryuArrowLook.meshScale(UryuArrowLook.Z, 0.3, 2), EPS);
		for (int f = 0; f <= 3; f++) {
			assertTrue(UryuArrowLook.meshScale(UryuArrowLook.WING, 1.0, f) <= 3.0);
		}
	}

	@Test
	void theGlowLayersAndTheTrailFollowTheTable() {
		assertEquals(0.9, UryuArrowLook.streakLength(0.0), EPS);
		assertEquals(2.1, UryuArrowLook.streakLength(1.0), EPS);
		assertEquals(8, UryuArrowLook.trailPoints(UryuArrowLook.BASIC));
		assertEquals(14, UryuArrowLook.trailPoints(UryuArrowLook.Z));
		assertEquals(0.16, UryuArrowLook.trailWidth(UryuArrowLook.BASIC), EPS);
		assertEquals(0.28, UryuArrowLook.trailWidth(UryuArrowLook.Z), EPS);
		assertEquals(0.7, UryuArrowLook.trailAlpha(0, 8), EPS);
		assertEquals(0.0, UryuArrowLook.trailAlpha(8, 8), EPS);
		double prev = 1;
		for (int k = 0; k < 8; k++) {
			double a = UryuArrowLook.trailAlpha(k, 8);
			assertTrue(a < prev, "falls toward the tail");
			prev = a;
		}
		assertEquals(0.16, UryuArrowLook.trailPieceWidth(UryuArrowLook.BASIC, 0, 8), EPS);
		assertTrue(UryuArrowLook.trailPieceWidth(UryuArrowLook.BASIC, 7, 8) < 0.16);
	}

	@Test
	void flakeSparksComeFromFocusTwoAndFromTheZArrow() {
		assertEquals(0.0, UryuArrowLook.flakeSpacing(UryuArrowLook.BASIC, 0), EPS);
		assertEquals(0.0, UryuArrowLook.flakeSpacing(UryuArrowLook.BASIC, 1), EPS);
		assertEquals(2.0, UryuArrowLook.flakeSpacing(UryuArrowLook.BASIC, 2), EPS);
		assertEquals(2.0, UryuArrowLook.flakeSpacing(UryuArrowLook.BASIC, 3), EPS);
		assertEquals(1.2, UryuArrowLook.flakeSpacing(UryuArrowLook.Z, 0), EPS);
		assertEquals(8, UryuArrowLook.FLAKE_LIFE_TICKS);
	}

	@Test
	void theMuzzleFlashAndTheBowFlash() {
		assertTrue(UryuArrowLook.hasMuzzle(UryuArrowLook.BASIC));
		assertTrue(UryuArrowLook.hasMuzzle(UryuArrowLook.Z));
		assertFalse(UryuArrowLook.hasMuzzle(UryuArrowLook.RAIN));
		assertFalse(UryuArrowLook.hasMuzzle(UryuArrowLook.WING));
		assertEquals(0.5, UryuArrowLook.muzzleStarSize(UryuArrowLook.BASIC, 0.0), EPS);
		assertEquals(1.2, UryuArrowLook.muzzleStarSize(UryuArrowLook.BASIC, 1.0), EPS);
		assertEquals(1.2, UryuArrowLook.muzzleStarSize(UryuArrowLook.Z, 0.2), EPS);
		assertEquals(1.2, UryuArrowLook.muzzleRingSize(UryuArrowLook.BASIC), EPS);
		assertEquals(2.0, UryuArrowLook.muzzleRingSize(UryuArrowLook.Z), EPS);
		assertEquals(1.6, UryuArrowLook.muzzleShotPitch(0.0), EPS);
		assertEquals(2.0, UryuArrowLook.muzzleShotPitch(1.0), EPS);
		assertEquals(6, UryuArrowLook.MUZZLE_DASHES);
		assertEquals(0.08, UryuArrowLook.BOW_FLASH_TIME, EPS);
	}

	@Test
	void theHitGrowsWithFocusAndFocusThreeAddsTheRing() {
		assertEquals(0.9, UryuArrowLook.hitStarSize(0), EPS);
		assertEquals(2.4, UryuArrowLook.hitStarSize(3), EPS);
		assertEquals(1.0, UryuArrowLook.hitRaysSize(0), EPS);
		assertEquals(2.8, UryuArrowLook.hitRaysSize(3), EPS);
		assertEquals(10, UryuArrowLook.hitDashes(0));
		assertEquals(28, UryuArrowLook.hitDashes(3));
		assertFalse(UryuArrowLook.hitRing(2));
		assertTrue(UryuArrowLook.hitRing(3));
		assertEquals(0, UryuArrowLook.hitLevel(UryuArrowLook.BASIC, 0));
		assertEquals(3, UryuArrowLook.hitLevel(UryuArrowLook.BASIC, 3));
		assertEquals(1, UryuArrowLook.hitLevel(UryuArrowLook.Z, 0), "the Z arrow hits one level higher");
		assertEquals(3, UryuArrowLook.hitLevel(UryuArrowLook.Z, 3));
		assertEquals(3, UryuArrowLook.hitLevel(UryuArrowLook.BASIC, 9), "clamped");
		assertEquals(0, UryuArrowLook.hitLevel(UryuArrowLook.BASIC, -2));
	}

	@Test
	void theBlockHitIsSmallerAndScarsOnlyAFloor() {
		assertTrue(UryuArrowLook.BLOCK_STAR < UryuArrowLook.hitStarSize(0));
		assertTrue(UryuArrowLook.BLOCK_DASHES < UryuArrowLook.hitDashes(0));
		assertTrue(UryuArrowLook.floorHit(-60.0));
		assertTrue(UryuArrowLook.floorHit(-59.97));
		assertTrue(UryuArrowLook.floorHit(-60.03));
		assertFalse(UryuArrowLook.floorHit(-59.5));
		assertFalse(UryuArrowLook.floorHit(12.2));
		assertEquals(0.25, UryuArrowLook.SCAR_RADIUS, EPS);
		assertEquals(0.6, UryuArrowLook.SCAR_LIFE, EPS);
	}
}
