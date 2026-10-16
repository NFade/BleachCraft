package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Revision 3 of the Focus rules: the sneak walk keeps it, only real movement, a jump, sprinting or a push take it. */
class FocusRulesTest {
	/** Normal sneak speed of a player in blocks per tick (1.3 blocks per second). */
	private static final double SNEAK_WALK = 0.065;
	/** Normal walking speed (4.3 blocks per second). */
	private static final double WALK = 0.215;

	@Test
	void sneakWalkAtNormalSneakSpeedStillBuildsFocus() {
		Focus f = new Focus();
		for (int i = 0; i < 28; i++) {
			f.tick(true, false, true, SNEAK_WALK, false);
		}
		assertEquals(2, f.stacks());
		assertTrue(SNEAK_WALK < UryuBalance.FOCUS_SNEAK_SPEED);
	}

	@Test
	void sneakLimitIsAboveSneakSpeedAndBelowWalkingSpeed() {
		assertTrue(UryuBalance.FOCUS_SNEAK_SPEED > SNEAK_WALK);
		assertTrue(UryuBalance.FOCUS_SNEAK_SPEED < WALK);
	}

	@Test
	void walkingNormallyOrSprintingResets() {
		Focus f = new Focus();
		for (int i = 0; i < 28; i++) {
			f.tick(true, false, false, 0.0, false);
		}
		assertEquals(2, f.stacks());
		f.tick(true, false, false, WALK, false);
		assertEquals(0, f.stacks(), "walking is movement");
		for (int i = 0; i < 28; i++) {
			f.tick(true, false, true, 0.0, false);
		}
		assertEquals(2, f.stacks());
		f.tick(true, true, false, 0.0, false);
		assertEquals(0, f.stacks(), "sprint resets");
	}

	@Test
	void aStaleSprintFlagWhileSneakingDoesNotReset() {
		Focus f = new Focus();
		for (int i = 0; i < 14; i++) {
			f.tick(true, true, true, SNEAK_WALK, false); // R15: sneaking and sprinting at once is a lagging flag, the speed decides
		}
		assertEquals(1, f.stacks());
	}

	@Test
	void aBumpOfTheGroundFlagWhileSneakWalkingDoesNotReset() {
		Focus f = new Focus();
		for (int i = 0; i < 28; i++) {
			f.tick(i % 5 != 4, false, true, SNEAK_WALK, false); // every fifth tick the path reports 'not on ground'
		}
		assertTrue(f.stacks() >= 1, "stacks " + f.stacks());
	}

	@Test
	void fastMovementWhileSneakingResetsToo() {
		Focus f = new Focus();
		for (int i = 0; i < 14; i++) {
			f.tick(true, false, true, 0.0, false);
		}
		assertEquals(1, f.stacks());
		f.tick(true, false, true, UryuBalance.FOCUS_SNEAK_SPEED, false);
		assertEquals(0, f.stacks(), "a sneaking dash (a push, a slide on ice) is above the sneak limit");
	}

	@Test
	void standingUsesTheTightLimitNotTheSneakOne() {
		Focus f = new Focus();
		for (int i = 0; i < 14; i++) {
			f.tick(true, false, false, 0.05, false);
		}
		assertEquals(0, f.stacks(), "0.05 b/t is walking when not sneaking");
	}

	@Test
	void jumpingWhileSneakingResets() {
		Focus f = new Focus();
		for (int i = 0; i < 14; i++) {
			f.tick(true, false, true, 0.0, false);
		}
		for (int i = 0; i < UryuBalance.FOCUS_AIR_TICKS; i++) {
			f.tick(false, false, true, 0.0, false);
		}
		assertEquals(0, f.stacks());
	}

	@Test
	void shunpoGraceKeepsTheStacksAndSkipsTheJumpInPosition() {
		Focus f = new Focus();
		for (int i = 0; i < 28; i++) {
			f.tick(true, false, false, 0.0, false);
		}
		assertEquals(2, f.stacks());
		f.grace();
		for (int i = 0; i < UryuBalance.FOCUS_GRACE_TICKS; i++) {
			f.tick(true, false, false, 9.0, false); // the teleport looks like 9 blocks in one tick
		}
		assertEquals(2, f.stacks(), "a shunpo does not reset Focus");
		f.tick(true, false, false, 9.0, false);
		assertEquals(0, f.stacks(), "after the grace real movement resets again");
	}

	@Test
	void aPushResetsEvenInsideTheGrace() {
		Focus f = new Focus();
		for (int i = 0; i < 28; i++) {
			f.tick(true, false, false, 0.0, false);
		}
		f.grace();
		assertTrue(f.reset());
		assertEquals(0, f.stacks());
	}
}
