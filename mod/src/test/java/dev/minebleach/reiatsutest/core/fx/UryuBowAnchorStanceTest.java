package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R13: the third person grip follows the side-on archer stance (the bow is in the left hand). */
class UryuBowAnchorStanceTest {
	private static final double[] FEET = {0.5, -60.0, 0.5};
	private static final double[] AIM = {0.0, 0.0, 1.0};

	@Test
	void stanceZeroIsTheOldFormula() {
		double[] old = UryuBowAnchor.thirdGrip(FEET, 0.0, AIM, true, -1.0);
		double[] zero = UryuBowAnchor.thirdGrip(FEET, 0.0, AIM, true, -1.0, 0.0);
		for (int i = 0; i < 3; i++) {
			assertEquals(old[i], zero[i], 1e-12);
		}
	}

	@Test
	void sideOnStanceBringsTheBowShoulderToTheAimAxis() {
		double[] across = UryuBowAnchor.thirdGrip(FEET, 0.0, AIM, true, -1.0, 0.0);
		double[] side = UryuBowAnchor.thirdGrip(FEET, 0.0, AIM, true, -1.0, 1.0);
		double lateralAcross = Math.abs(across[0] - FEET[0]);
		double lateralSide = Math.abs(side[0] - FEET[0]);
		assertTrue(lateralSide < lateralAcross * 0.4, "the shoulder is nearly on the aim axis: " + lateralSide + " vs " + lateralAcross);
		assertTrue(side[2] > across[2], "and it stands in front of the body");
		assertEquals(across[1], side[1], 1e-12, "the height does not change");
	}

	@Test
	void leftHandIsOnTheOppositeSideOfTheRightHand() {
		double[] left = UryuBowAnchor.thirdGrip(FEET, 0.0, AIM, true, -1.0, 0.0);
		double[] right = UryuBowAnchor.thirdGrip(FEET, 0.0, AIM, true, 1.0, 0.0);
		assertEquals(FEET[0] - (left[0] - FEET[0]), right[0], 1e-9);
	}
}
