package dev.minebleach.reiatsutest.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SideCellMathTest {
	@Test
	void totalTakesTheLargerOfConfigAndPeakAndIsNeverZero() {
		assertEquals(100, SideCellMath.total(100, 0));
		assertEquals(160, SideCellMath.total(100, 160));
		assertEquals(1, SideCellMath.total(0, 0));
	}

	@Test
	void sweepFrameRunsFrom24DownToOneAndIsZeroWhenReady() {
		assertEquals(0, SideCellMath.sweepFrame(0, 100));
		assertEquals(24, SideCellMath.sweepFrame(100, 100));
		assertEquals(12, SideCellMath.sweepFrame(50, 100));
		assertEquals(1, SideCellMath.sweepFrame(1, 100));
		assertEquals(24, SideCellMath.sweepFrame(500, 100)); // remaining above total (server config longer) is clamped
		assertEquals(0, SideCellMath.sweepFrame(5, 0));
	}

	@Test
	void wipeHeightShrinksToOnePixelAndNeverExceedsTheIcon() {
		assertEquals(16, SideCellMath.wipeHeight(50, 50, 16));
		assertEquals(8, SideCellMath.wipeHeight(25, 50, 16));
		assertEquals(1, SideCellMath.wipeHeight(1, 50, 16));
		assertEquals(0, SideCellMath.wipeHeight(0, 50, 16));
		assertEquals(16, SideCellMath.wipeHeight(80, 50, 16));
	}

	@Test
	void secondsLabelRoundsUpAndShowsTenthsBelowOneSecond() {
		assertNull(SideCellMath.secondsLabel(0));
		assertNull(SideCellMath.secondsLabel(-3));
		assertEquals("10", SideCellMath.secondsLabel(200));
		assertEquals("10", SideCellMath.secondsLabel(181));
		assertEquals("9", SideCellMath.secondsLabel(180));
		assertEquals("1", SideCellMath.secondsLabel(20));
		assertEquals("0.9", SideCellMath.secondsLabel(19));
		assertEquals("0.5", SideCellMath.secondsLabel(9));
		assertEquals("0.1", SideCellMath.secondsLabel(1));
		assertEquals("99", SideCellMath.secondsLabel(100000));
	}

	@Test
	void keyLetterIsOneUpperCaseCharacter() {
		assertEquals("K", SideCellMath.keyLetter("k"));
		assertEquals("Y", SideCellMath.keyLetter("Y"));
		assertEquals("Ж", SideCellMath.keyLetter("ж"));
		assertEquals("?", SideCellMath.keyLetter(""));
		assertEquals("?", SideCellMath.keyLetter(null));
	}
}
