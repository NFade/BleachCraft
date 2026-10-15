package dev.minebleach.reiatsutest.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import org.junit.jupiter.api.Test;

class DoubleJumpTest {
	private static final boolean AIR = false;
	private static final boolean GROUND = true;

	/** Ticks in the air with the key up, allowed. */
	private static void airUp(DoubleJump j, int ticks) {
		for (int i = 0; i < ticks; i++) {
			assertFalse(j.tick(AIR, false, false, true));
		}
	}

	@Test
	void allowedInEveryState() {
		assertTrue(DoubleJump.allowedState(ZanpakutoState.SEALED));
		assertTrue(DoubleJump.allowedState(ZanpakutoState.BASE));
		assertTrue(DoubleJump.allowedState(ZanpakutoState.SHIKAI));
		assertTrue(DoubleJump.allowedState(ZanpakutoState.BANKAI));
		assertTrue(DoubleJump.allowedState(null));
	}

	@Test
	void jumpFromGroundThenReleaseThenPressFiresOnce() {
		DoubleJump j = new DoubleJump();
		assertFalse(j.tick(GROUND, true, false, true)); // pressing on the ground is the vanilla jump
		assertFalse(j.tick(AIR, true, false, true)); // key still held from the first jump
		assertFalse(j.tick(AIR, false, false, true)); // released
		assertFalse(j.tick(AIR, false, false, true));
		assertTrue(j.tick(AIR, true, false, true)); // pressed again, 4th air tick: fires
		assertTrue(j.airJumpUsed());
	}

	@Test
	void heldKeyNeverFires() {
		DoubleJump j = new DoubleJump();
		j.tick(GROUND, true, false, true);
		for (int i = 0; i < 60; i++) {
			assertFalse(j.tick(AIR, true, false, true));
		}
	}

	@Test
	void threeAirJumpsPerAirTime() {
		DoubleJump j = new DoubleJump();
		j.tick(GROUND, false, false, true);
		airUp(j, 4);
		for (int n = 1; n <= DoubleJump.MAX_AIR_JUMPS; n++) {
			assertTrue(j.tick(AIR, true, false, true), "air jump " + n);
			assertFalse(j.tick(AIR, false, false, true));
		}
		assertEquals(3, j.airJumpsUsed());
		assertFalse(j.tick(AIR, true, false, true)); // the fourth press in the same air time does nothing
		assertFalse(j.tick(AIR, false, false, true));
		assertFalse(j.tick(AIR, true, false, true));
	}

	@Test
	void landingResets() {
		DoubleJump j = new DoubleJump();
		j.tick(GROUND, false, false, true);
		airUp(j, 4);
		assertTrue(j.tick(AIR, true, false, true));
		assertFalse(j.tick(GROUND, false, false, true));
		assertEquals(0, j.airTicks());
		assertFalse(j.airJumpUsed());
		airUp(j, 5);
		assertTrue(j.tick(AIR, true, false, true)); // the next air time has its own air jump
	}

	@Test
	void pressOnTheLandingTickDoesNotFire() {
		DoubleJump j = new DoubleJump();
		airUp(j, 5);
		assertFalse(j.tick(GROUND, true, false, true));
		assertFalse(j.airJumpUsed());
	}

	@Test
	void fallingOffALedgeGivesOneAirJump() {
		DoubleJump j = new DoubleJump();
		j.tick(GROUND, false, false, true);
		airUp(j, 10); // walked off, never pressed jump
		assertTrue(j.tick(AIR, true, false, true));
		assertFalse(j.tick(AIR, false, false, true));
		assertTrue(j.tick(AIR, true, false, true)); // the second of three
	}

	@Test
	void minimumAirTimeIsThreeTicks() {
		DoubleJump j = new DoubleJump();
		j.tick(GROUND, false, false, true);
		assertFalse(j.tick(AIR, true, false, true)); // air tick 1, press ignored
		assertFalse(j.tick(AIR, false, false, true)); // 2
		assertTrue(j.tick(AIR, true, false, true)); // 3: fires
	}

	@Test
	void pressBeforeMinimumAirTimeIsLostNotBuffered() {
		DoubleJump j = new DoubleJump();
		j.tick(GROUND, false, false, true);
		assertFalse(j.tick(AIR, true, false, true)); // 1: pressed too early
		assertFalse(j.tick(AIR, true, false, true)); // 2: held
		assertFalse(j.tick(AIR, true, false, true)); // 3: held, no new edge
		assertFalse(j.airJumpUsed());
		assertFalse(j.tick(AIR, false, false, true));
		assertTrue(j.tick(AIR, true, false, true));
	}

	@Test
	void neverWhenBlocked() {
		DoubleJump j = new DoubleJump();
		j.tick(GROUND, false, false, true);
		airUp(j, 5);
		assertFalse(j.tick(AIR, true, true, true)); // water / lava / ladder / elytra / riding / creative flying / spectator
		assertEquals(0, j.airTicks());
	}

	@Test
	void leavingWaterStartsAFreshAirTime() {
		DoubleJump j = new DoubleJump();
		for (int i = 0; i < 10; i++) {
			assertFalse(j.tick(AIR, i % 2 == 0, true, true));
		}
		assertFalse(j.tick(AIR, false, false, true)); // 1
		assertFalse(j.tick(AIR, true, false, true)); // 2: too early
		assertFalse(j.tick(AIR, false, false, true)); // 3
		assertTrue(j.tick(AIR, true, false, true)); // 4
	}

	@Test
	void notAllowedNeverFiresAndKeepsTheAirJump() {
		DoubleJump j = new DoubleJump();
		j.tick(GROUND, false, false, false);
		for (int i = 0; i < 12; i++) {
			assertFalse(j.tick(AIR, i % 2 == 1, false, false)); // sealed or base: nothing happens however often you press
		}
		assertFalse(j.airJumpUsed());
		// released into shikai in mid air: the next press fires
		assertFalse(j.tick(AIR, false, false, true));
		assertTrue(j.tick(AIR, true, false, true));
	}

	@Test
	void keyHeldAcrossAStateChangeIsNoPress() {
		DoubleJump j = new DoubleJump();
		j.tick(GROUND, false, false, false);
		airUp(j, 4);
		assertFalse(j.tick(AIR, true, false, false)); // pressed while sealed
		assertFalse(j.tick(AIR, true, false, true)); // state becomes shikai while the key is still down: no edge
		assertFalse(j.airJumpUsed());
	}

	@Test
	void resetForgetsTheAirTimeButKeepsTheKey() {
		DoubleJump j = new DoubleJump();
		j.tick(GROUND, false, false, true);
		airUp(j, 4);
		assertTrue(j.tick(AIR, true, false, true));
		j.reset();
		assertEquals(0, j.airTicks());
		assertFalse(j.airJumpUsed());
		for (int i = 0; i < 6; i++) {
			assertFalse(j.tick(AIR, true, false, true)); // the key is still down: never a press
		}
		j.resetAll();
		assertEquals(0, j.airTicks());
	}
}
