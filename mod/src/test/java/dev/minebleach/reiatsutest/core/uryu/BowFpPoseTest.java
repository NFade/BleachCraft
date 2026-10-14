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
	void theBowStandsLeftOfTheCentreAndInFrontOfTheCamera() {
		float[] g = BowFpPose.defaults().grip();
		assertTrue(g[0] < 0f, "left of the crosshair");
		assertTrue(g[2] < -0.5f, "in front of the camera");
		assertTrue(BowFpPose.defaults().scale() > 0.5f);
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
