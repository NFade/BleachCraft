package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pure maths of the first person strike, block stance and parry recoil (BC5b). */
class FpMotionTest {
	private static final float EPS = 1e-4f;

	private static FpParams params(Map<String, float[]> m) {
		return new FpParams() {
			@Override
			public float num(String key, float def) {
				float[] v = m.get(key);
				return v == null ? def : v[0];
			}

			@Override
			public float[] vec(String key, float[] def) {
				float[] v = m.get(key);
				return v == null ? def : v;
			}
		};
	}

	@Test
	void zRotationTurnsTheBladeFromUpToTheLeftOnTheScreen() {
		// +90 degrees about z (right-handed, seen from the camera side): +Y goes to -X
		float[] d = FpPose.of(0, 0, 0, 0, 0, 90).applyDir(new float[] {0, 1, 0});
		assertArrayEquals(new float[] {-1, 0, 0}, d, EPS);
		// negative x rotation tilts the tip forward (away from the camera, -z)
		float[] f = FpPose.of(0, 0, 0, -90, 0, 0).applyDir(new float[] {0, 1, 0});
		assertArrayEquals(new float[] {0, 0, -1}, f, EPS);
	}

	@Test
	void eulerOrderMatchesTheVanillaDisplayTransform() {
		// Rx * Ry * Rz: z is applied to the vector first. (0,1,0) -z90-> (-1,0,0) -y90-> (0,0,1) (y90 takes +x to -z, so -x to +z)
		float[] d = FpPose.of(0, 0, 0, 0, 90, 90).applyDir(new float[] {0, 1, 0});
		assertArrayEquals(new float[] {0, 0, 1}, d, EPS);
	}

	@Test
	void applyRotatesAboutThePivotAndShifts() {
		FpPose p = FpPose.of(0.5f, 0, 0, 0, 0, 90);
		float[] pivot = {1, 1, 0};
		// the pivot itself only moves by pos
		assertArrayEquals(new float[] {1.5f, 1, 0}, p.apply(pivot, pivot), EPS);
		// a point one block above the pivot goes one block to the left of it
		assertArrayEquals(new float[] {0.5f, 1, 0}, p.apply(pivot, new float[] {1, 2, 0}), EPS);
	}

	@Test
	void mirrorFlipsXOfTheShiftAndYZOfTheRotation() {
		FpPose m = FpPose.of(0.1f, 0.2f, 0.3f, 10, 20, 30).mirrored();
		assertArrayEquals(new float[] {-0.1f, 0.2f, 0.3f}, m.pos(), EPS);
		assertArrayEquals(new float[] {10, -20, -30}, m.rot(), EPS);
	}

	@Test
	void profilesFollowTheKatanaAnimationNames() {
		assertEquals(SwingMotion.Profile.H_R, SwingMotion.profileFor("bettercombat:two_handed_slash_horizontal_right", 0));
		assertEquals(SwingMotion.Profile.V_L, SwingMotion.profileFor("bettercombat:two_handed_slash_vertical_left", 1));
		assertEquals(SwingMotion.Profile.V_R, SwingMotion.profileFor("bettercombat:two_handed_slash_vertical_right", 2));
		assertEquals(SwingMotion.Profile.H_L, SwingMotion.profileFor("bettercombat:two_handed_slash_horizontal_left", 3));
		assertEquals(SwingMotion.Profile.STAB, SwingMotion.profileFor("bettercombat:one_handed_stab", 0));
		assertEquals(SwingMotion.Profile.UPPER, SwingMotion.profileFor("bettercombat:two_handed_uppercut_left", 0));
		// unknown or missing name: the combo index decides, wrapping round
		assertEquals(SwingMotion.Profile.V_R, SwingMotion.profileFor(null, 2));
		assertEquals(SwingMotion.Profile.H_L, SwingMotion.profileFor("x:y", 7));
		assertEquals(SwingMotion.Profile.H_R, SwingMotion.profileFor("", -4));
	}

	@Test
	void strikeReachesTheHitKeyExactlyWhenTheHitLands() {
		FpParams pr = FpParams.DEFAULTS;
		SwingMotion.Timing tm = SwingMotion.Timing.DEFAULT;
		for (SwingMotion.Profile pf : SwingMotion.Profile.values()) {
			float up = 0.25f;
			FpPose hit = SwingMotion.key(pr, pf, SwingMotion.Key.HIT);
			FpPose at = SwingMotion.sample(pr, pf, FpPose.ZERO, up, up, tm);
			assertArrayEquals(hit.pos(), at.pos(), EPS, pf.name());
			assertArrayEquals(hit.rot(), at.rot(), EPS, pf.name());
			// the wind-up key is reached at windFrac of the upswing
			FpPose wind = SwingMotion.key(pr, pf, SwingMotion.Key.WIND);
			FpPose w = SwingMotion.sample(pr, pf, FpPose.ZERO, up * tm.windFrac(), up, tm);
			assertArrayEquals(wind.rot(), w.rot(), EPS, pf.name());
		}
	}

	@Test
	void strikeIsContinuousAndReturnsToRest() {
		FpParams pr = FpParams.DEFAULTS;
		SwingMotion.Timing tm = SwingMotion.Timing.DEFAULT;
		SwingMotion.Profile pf = SwingMotion.Profile.H_R;
		float up = 0.3f;
		float total = SwingMotion.duration(up, tm);
		FpPose prev = SwingMotion.sample(pr, pf, FpPose.ZERO, 0f, up, tm);
		float maxStep = 0f;
		for (float t = 0.002f; t <= total + 0.05f; t += 0.002f) {
			FpPose cur = SwingMotion.sample(pr, pf, FpPose.ZERO, t, up, tm);
			for (int i = 0; i < 3; i++) {
				maxStep = Math.max(maxStep, Math.abs(cur.rot()[i] - prev.rot()[i]));
			}
			prev = cur;
		}
		// the fastest part is the strike, about 90 degrees in 0.13 s: 1.4 degrees per 2 ms; a jump would be much larger
		assertTrue(maxStep < 3f, "no jump in the strike: " + maxStep);
		assertTrue(prev.isNearZero(), "rest after the strike");
		assertTrue(SwingMotion.sample(pr, pf, FpPose.ZERO, total + 1f, up, tm).isNearZero());
	}

	@Test
	void interruptedStrikeStartsFromTheCurrentPose() {
		FpParams pr = FpParams.DEFAULTS;
		FpPose origin = FpPose.of(0.1f, 0.2f, -0.1f, 5, 6, 7);
		FpPose at0 = SwingMotion.sample(pr, SwingMotion.Profile.V_R, origin, 0f, 0.2f, SwingMotion.Timing.DEFAULT);
		assertArrayEquals(origin.pos(), at0.pos(), EPS);
		FpPose tiny = SwingMotion.sample(pr, SwingMotion.Profile.V_R, origin, 0.001f, 0.2f, SwingMotion.Timing.DEFAULT);
		assertEquals(origin.rot()[2], tiny.rot()[2], 0.5f);
	}

	@Test
	void overridesReplaceKeysAndScaleApplies() {
		FpParams pr = params(Map.of("swing.h_r.hit.pos", new float[] {1, 2, 3}, "swing.h_r.hit.rot", new float[] {4, 5, 6},
				"swing.scale", new float[] {0.5f}));
		FpPose hit = SwingMotion.key(pr, SwingMotion.Profile.H_R, SwingMotion.Key.HIT);
		assertArrayEquals(new float[] {0.5f, 1, 1.5f}, hit.pos(), EPS);
		assertArrayEquals(new float[] {2, 2.5f, 3}, hit.rot(), EPS);
		SwingMotion.Timing t = SwingMotion.timing(params(Map.of("swing.wind_frac", new float[] {5f})));
		assertEquals(0.9f, t.windFrac(), EPS, "clamped");
	}

	@Test
	void stanceWeightRampsAtTheConfiguredSpeed() {
		float w = 0f;
		for (int i = 0; i < 5; i++) {
			w = GuardMotion.stepWeight(w, true, 0.02f, 0.10f, 0.25f);
		}
		assertEquals(1f, w, EPS, "up in 0.1 s");
		for (int i = 0; i < 5; i++) {
			w = GuardMotion.stepWeight(w, false, 0.05f, 0.10f, 0.25f);
		}
		assertEquals(0f, w, EPS, "down in 0.25 s");
		assertEquals(0f, GuardMotion.stepWeight(0f, false, 1f, 0.1f, 0.25f), EPS, "never below 0");
		assertEquals(1f, GuardMotion.stepWeight(1f, true, 1f, 0.1f, 0.25f), EPS, "never above 1");
	}

	@Test
	void stanceIsRestAtZeroAndGuardAtOne() {
		FpParams pr = FpParams.DEFAULTS;
		assertTrue(GuardMotion.stance(pr, 0f).isNearZero());
		assertArrayEquals(GuardMotion.GUARD.rot(), GuardMotion.stance(pr, 1f).rot(), EPS);
		assertArrayEquals(GuardMotion.GUARD.pos(), GuardMotion.stance(pr, 1f).pos(), EPS);
		// the user's cross-body guard: the blade in the frontal plane, tip up and to the left, not at the face (blade axis of the real rig)
		float[] d = GuardMotion.GUARD.applyDir(FpRig.DEFAULT.axis());
		assertTrue(d[0] < -0.45f && d[1] > 0.7f, "tip up and to the left: " + d[0] + " " + d[1]);
		assertTrue(Math.abs(d[2]) < 0.1f, "in the frontal plane, tip not at the face: " + d[2]);
		assertEquals(-0.508f, d[0], 0.07f);
		assertEquals(0.860f, d[1], 0.05f);
	}

	@Test
	void guardPoseBlendsFromTheStrikeAndOnlyBreathesWhenUp() {
		FpParams pr = FpParams.DEFAULTS;
		FpPose strike = FpPose.of(0.1f, 0, 0, 0, 0, 30);
		FpPose none = GuardMotion.pose(pr, strike, 0f, 0.7f, FpPose.ZERO, FpPose.ZERO);
		assertArrayEquals(strike.pos(), none.pos(), EPS);
		assertArrayEquals(strike.rot(), none.rot(), EPS);
		assertTrue(GuardMotion.breath(pr, 0.5f, 0f).isNearZero());
		FpPose up = GuardMotion.pose(pr, FpPose.ZERO, 1f, 0f, FpPose.ZERO, FpPose.ZERO);
		assertArrayEquals(GuardMotion.GUARD.rot(), up.rot(), EPS);
	}

	@Test
	void envelopeRisesReboundsAndSettles() {
		float rise = 0.05f;
		float decay = 0.15f;
		float freq = 3f;
		assertEquals(0f, GuardMotion.envelope(-0.1f, rise, decay, freq), EPS);
		assertEquals(0f, GuardMotion.envelope(0f, rise, decay, freq), EPS);
		assertEquals(1f, GuardMotion.envelope(rise, rise, decay, freq), EPS);
		float min = 0f;
		for (float s = rise; s < 1f; s += 0.005f) {
			min = Math.min(min, GuardMotion.envelope(s, rise, decay, freq));
		}
		assertTrue(min < -0.2f && min > -0.5f, "one rebound past zero: " + min);
		assertTrue(Math.abs(GuardMotion.envelope(0.6f, rise, decay, freq)) < 0.03f, "still after 0.6 s");
	}

	@Test
	void parryKickPushesTheSwordForwardAndSideDependsOnTheAttacker() {
		FpParams pr = FpParams.DEFAULTS;
		FpPose peak = GuardMotion.kick(pr, GuardMotion.Kick.PARRY, 0.05f, 0f, 1f);
		assertTrue(peak.pos()[2] < -0.1f, "forward: " + peak.pos()[2]);
		assertTrue(peak.rot()[0] < -10f, "tip forward");
		FpPose fromRight = GuardMotion.kick(pr, GuardMotion.Kick.PARRY, 0.05f, 1f, 1f);
		FpPose fromLeft = GuardMotion.kick(pr, GuardMotion.Kick.PARRY, 0.05f, -1f, 1f);
		assertTrue(fromRight.pos()[0] < fromLeft.pos()[0], "attacker on the right pushes the sword to the left");
		assertTrue(GuardMotion.kick(pr, GuardMotion.Kick.PARRY, 0f, 0f, 1f).isNearZero());
		FpPose half = GuardMotion.kick(pr, GuardMotion.Kick.PARRY, 0.05f, 0f, 0.5f);
		assertEquals(peak.pos()[2] * 0.5f, half.pos()[2], EPS, "reduced motion halves it");
		float over = GuardMotion.kickLength(pr, GuardMotion.Kick.PARRY) + 0.05f;
		assertTrue(Math.abs(GuardMotion.kick(pr, GuardMotion.Kick.PARRY, over, 0f, 1f).pos()[2]) < 0.01f, "settled");
		FpPose hit = GuardMotion.kick(pr, GuardMotion.Kick.HIT, 0.03f, 0f, 1f);
		assertTrue(hit.pos()[2] > 0.02f, "a blocked hit pushes the sword back");
	}

	@Test
	void leftFistFollowsTheBladeThroughThePose() {
		FpParams pr = params(Map.of("guard.left.offset", new float[] {0, 0, 0}));
		float[] pivot = {0.1f, 0.1f, -0.3f};
		float[] bladePoint = {0.1f + 0.12f * 0.7f, 0.1f + 0.97f * 0.7f, -0.3f + 0.23f * 0.7f};
		float[] rest = GuardMotion.leftFist(pr, FpPose.ZERO, pivot, bladePoint);
		assertArrayEquals(bladePoint, rest, EPS);
		float[] g = GuardMotion.leftFist(pr, GuardMotion.GUARD, pivot, bladePoint);
		// the grip moved by pos; the point 0.7 along the blade is the same distance from the grip afterwards as well
		float[] grip = GuardMotion.GUARD.apply(pivot, pivot);
		double dist = Math.sqrt(Math.pow(g[0] - grip[0], 2) + Math.pow(g[1] - grip[1], 2) + Math.pow(g[2] - grip[2], 2));
		double before = 0.7 * Math.sqrt(0.12 * 0.12 + 0.97 * 0.97 + 0.23 * 0.23);
		assertEquals(before, dist, 1e-3);
		assertEquals(0f, GuardMotion.leftWeight(0.2f), EPS);
		assertEquals(1f, GuardMotion.leftWeight(1f), EPS);
	}
}
