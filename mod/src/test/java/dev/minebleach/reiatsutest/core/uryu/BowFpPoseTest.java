package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.combat.FpParams;
import org.junit.jupiter.api.Test;

/** The first person bow pose numbers (defaults, dev override) and the nock point of the bow model. */
class BowFpPoseTest {
	@Test
	void defaultsAreTheShippedNumbers() {
		BowFpPose p = BowFpPose.defaults();
		assertTrue(p.enabled());
		assertArrayEquals(BowFpPose.GRIP, p.grip());
		assertEquals(BowFpPose.SCALE, p.scale());
		assertEquals(BowFpPose.YAW, p.yaw());
		assertEquals(BowFpPose.PITCH, p.pitch());
		assertEquals(BowFpPose.ROLL, p.roll());
		assertArrayEquals(BowFpPose.RIGHT_ROT, p.rightRot());
	}

	@Test
	void theBowStandsInFrontOfTheCameraRightOfTheCrosshairAndBelowIt() {
		float[] g = BowFpPose.defaults().grip();
		assertTrue(g[0] > 0f, "R13: the bow body is right of the crosshair, the sight stays open");
		assertTrue(g[1] < -0.3f, "R13: held low, the crosshair is free");
		assertTrue(g[2] < -0.5f, "in front of the camera");
		assertTrue(BowFpPose.defaults().scale() > 0.5f);
		assertTrue(BowFpPose.defaults().yaw() > 0f && BowFpPose.defaults().yaw() < 25f, "the arrow points back to the crosshair, not out to the side");
	}

	@Test
	void theBowArmComesFromTheLowerLeftAndRestsLower() {
		BowFpPose p = BowFpPose.defaults();
		assertTrue(p.leftRot()[2] < -30f, "drawn: the forearm points right, from the lower left corner");
		assertTrue(p.placed(0, 0).leftRot()[2] < p.placed(1, 1).leftRot()[2], "at rest the forearm lies flatter than at full draw");
		assertTrue(p.placed(0, 0).grip()[1] < p.placed(1, 1).grip()[1], "at rest the bow is lower");
	}

	@Test
	void placedIsTheRestPoseAtZeroAndTheDrawPoseAtOne() {
		BowFpPose p = BowFpPose.defaults();
		BowFpPose.Placed rest = p.placed(0, 0);
		BowFpPose.Placed full = p.placed(1, 1);
		assertArrayEquals(BowFpPose.IDLE_GRIP, rest.grip(), 1e-6f);
		assertEquals(BowFpPose.IDLE_ROT[0], rest.yaw(), 1e-5f);
		assertArrayEquals(p.grip(), full.grip(), 1e-6f);
		assertEquals(p.yaw(), full.yaw(), 1e-5f);
		assertEquals(p.roll(), full.roll(), 1e-5f);
		assertArrayEquals(p.leftRot(), full.leftRot(), 1e-5f);
		// the raise alone (no pull yet) lifts the bow only a little; half the draw has it nearly up; every step is monotone
		BowFpPose.Placed ready = p.placed(1, 0);
		assertTrue(ready.grip()[1] < p.placed(1, 0.5).grip()[1] && p.placed(1, 0.5).grip()[1] <= full.grip()[1] + 1e-6f);
		assertTrue(ready.grip()[1] > rest.grip()[1] - 1e-6f);
		float last = -10f;
		for (int i = 0; i <= 20; i++) {
			float y = p.placed(1, i / 20.0).grip()[1];
			assertTrue(y >= last - 1e-6f, "the bow only rises while the string is drawn");
			last = y;
		}
		assertTrue(p.placed(1, 0.8).roll() == p.roll() || Math.abs(p.placed(1, 0.8).roll() - p.roll()) < 1.0f, "the bow lean has arrived near the end");
		assertTrue(rest.roll() < 0f && full.roll() < 0f, "lowered and drawn the bow leans to the right (negative roll)");
		// out of range inputs are clamped
		assertArrayEquals(rest.grip(), p.placed(-3, 9).grip(), 1e-6f);
	}

	@Test
	void overridesWinAndTheRestKeepsTheDefaults() {
		FpParams p = new FpParams() {
			@Override
			public float num(String key, float def) {
				return key.equals("bow.scale") ? 2f : key.equals("bow.enabled") ? 0f : def;
			}

			@Override
			public float[] vec(String key, float[] def) {
				return key.equals("bow.rot") ? new float[] {10f, 20f, 30f} : def;
			}
		};
		BowFpPose o = BowFpPose.of(p);
		assertFalse(o.enabled());
		assertEquals(2f, o.scale());
		assertEquals(10f, o.yaw());
		assertEquals(20f, o.pitch());
		assertEquals(30f, o.roll());
		assertArrayEquals(BowFpPose.GRIP, o.grip());
	}

	@Test
	void timesAreNeverZero() {
		FpParams zero = new FpParams() {
			@Override
			public float num(String key, float def) {
				return 0f;
			}

			@Override
			public float[] vec(String key, float[] def) {
				return def;
			}
		};
		BowFpPose o = BowFpPose.of(zero);
		assertTrue(o.readyIn() > 0f && o.readyOut() > 0f && o.snap() > 0f);
	}

	@Test
	void nockTravelsBackWithTheDrawAndStaysOnTheModelCube() {
		assertEquals(BowDraw.STRING_Z - BowDraw.NOCK_START, BowDraw.nockZ(0.0), 1e-6);
		assertEquals(BowDraw.STRING_Z - BowDraw.NOCK_START - BowDraw.NOCK_TRAVEL, BowDraw.nockZ(1.0), 1e-6);
		assertEquals(BowDraw.nockZ(1.0), BowDraw.nockZ(5.0), 1e-6, "capped");
		assertEquals(BowDraw.nockZ(0.0), BowDraw.nockZ(-1.0), 1e-6, "floored");
		float last = 1f;
		for (int t = 0; t <= 20; t++) {
			float z = BowDraw.nockZ(BowDraw.visualPull(t));
			assertTrue(z <= last + 1e-6f, "the nock only moves toward the archer");
			last = z;
		}
		assertTrue(BowDraw.nockZ(0.0) < 0.5f, "behind the grip");
	}
}
