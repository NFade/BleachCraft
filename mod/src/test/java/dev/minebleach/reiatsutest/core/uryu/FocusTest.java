package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Focus rules of design/URYU.md section 3. */
class FocusTest {
	private static void still(Focus f, int ticks, boolean arterie) {
		for (int i = 0; i < ticks; i++) {
			f.tick(true, false, 0.0, arterie);
		}
	}

	@Test
	void oneStackEvery14TicksInBase() {
		Focus f = new Focus();
		still(f, 13, false);
		assertEquals(0, f.stacks());
		still(f, 1, false);
		assertEquals(1, f.stacks());
		still(f, 14, false);
		assertEquals(2, f.stacks());
		still(f, 14, false);
		assertEquals(3, f.stacks());
		assertTrue(f.full());
	}

	@Test
	void blutArterieBuildsEvery10Ticks() {
		Focus f = new Focus();
		still(f, 9, true);
		assertEquals(0, f.stacks());
		still(f, 1, true);
		assertEquals(1, f.stacks());
		still(f, 20, true);
		assertEquals(3, f.stacks());
	}

	@Test
	void stopsAtThreeStacks() {
		Focus f = new Focus();
		still(f, 200, false);
		assertEquals(3, f.stacks());
		assertEquals(0, f.charge());
	}

	@Test
	void movementAboveTheThresholdResetsEverything() {
		Focus f = new Focus();
		still(f, 30, false);
		assertEquals(2, f.stacks());
		assertTrue(f.tick(true, false, UryuBalance.FOCUS_MOVE_EPS, false), "exactly the threshold already counts as moving");
		assertEquals(0, f.stacks());
		assertEquals(0, f.charge());
	}

	@Test
	void smallDriftBelowTheThresholdKeepsFocus() {
		Focus f = new Focus();
		for (int i = 0; i < 14; i++) {
			f.tick(true, false, 0.019, false);
		}
		assertEquals(1, f.stacks());
	}

	@Test
	void jumpOrFallResets() {
		Focus f = new Focus();
		still(f, 28, false);
		f.tick(false, false, 0.0, false);
		assertEquals(0, f.stacks());
	}

	@Test
	void sprintResets() {
		Focus f = new Focus();
		still(f, 14, false);
		f.tick(true, true, 0.0, false);
		assertEquals(0, f.stacks());
	}

	@Test
	void knockbackOrHirenkyakuResetViaReset() {
		Focus f = new Focus();
		still(f, 42, false);
		assertTrue(f.reset());
		assertEquals(0, f.stacks());
		assertFalse(f.reset(), "nothing to lose the second time");
	}

	@Test
	void consumeReturnsTheStacksAndStartsOver() {
		Focus f = new Focus();
		still(f, 30, false);
		assertEquals(2, f.consume());
		assertEquals(0, f.stacks());
		assertEquals(0, f.charge());
		still(f, 13, false);
		assertEquals(0, f.stacks(), "the charge of the consumed stacks is gone too");
		still(f, 1, false);
		assertEquals(1, f.stacks());
	}

	@Test
	void multipliersAre25_60_100Percent() {
		assertEquals(1.0, UryuBalance.focusFactor(0), 1e-9);
		assertEquals(1.25, UryuBalance.focusFactor(1), 1e-9);
		assertEquals(1.6, UryuBalance.focusFactor(2), 1e-9);
		assertEquals(2.0, UryuBalance.focusFactor(3), 1e-9);
		assertEquals(2.0, UryuBalance.focusFactor(9), 1e-9, "clamped");
		assertEquals(1.0, UryuBalance.focusFactor(-1), 1e-9, "clamped");
	}

	@Test
	void shotsScaleDamageAndSpeedAndFullFocusPiercesWithoutGravity() {
		Shots.Params p0 = Shots.focusShot(0);
		assertEquals(7.0f, p0.damage(), 1e-6);
		assertEquals(UryuBalance.Z_ARROW_SPEED, p0.speed(), 1e-9);
		assertEquals(UryuBalance.ARROW_GRAVITY, p0.gravity(), 1e-9);
		assertEquals(0, p0.pierce());
		assertEquals(7.0f * 1.25f, Shots.focusShot(1).damage(), 1e-5);
		assertEquals(7.0f * 1.6f, Shots.focusShot(2).damage(), 1e-5);
		assertEquals(UryuBalance.Z_ARROW_SPEED * 1.6, Shots.focusShot(2).speed(), 1e-9);
		assertEquals(0, Shots.focusShot(2).pierce());
		Shots.Params p3 = Shots.focusShot(3);
		assertEquals(14.0f, p3.damage(), 1e-5);
		assertEquals(UryuBalance.Z_ARROW_SPEED * 2.0, p3.speed(), 1e-9);
		assertEquals(0.0, p3.gravity(), 0.0);
		assertEquals(1, p3.pierce());
		assertEquals(3, p3.focus());
	}

	@Test
	void basicShotIs4HpAnd20PercentMoreWithArterie() {
		assertEquals(4.0f, Shots.basic(0, false).damage(), 1e-6);
		assertEquals(4.8f, Shots.basic(0, true).damage(), 1e-5);
		assertEquals(8.0f, Shots.basic(3, false).damage(), 1e-5);
		assertEquals(9.6f, Shots.basic(3, true).damage(), 1e-5);
		assertEquals(4, UryuBalance.BASIC_COOLDOWN);
	}

	@Test
	void peakBurstStaysUnderHakuteiken() {
		// design section 2: nothing above 24 HP in one hit of one shot
		assertTrue(Shots.focusShot(3).damage() <= 24.0f);
		assertTrue(Sternenstaub.damage(0) <= 24.0f);
	}
}
