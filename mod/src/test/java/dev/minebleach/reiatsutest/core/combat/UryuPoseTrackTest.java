package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Reading and sampling of the emote JSON of the Uryu poses ({@link UryuPoseTrack}). */
class UryuPoseTrackTest {
	private static final double EPS = 1e-9;

	private static final String JSON = "{\"name\":\"t\",\"emote\":{\"endTick\":10,\"stopTick\":20,\"moves\":["
			+ "{\"tick\":0,\"easing\":\"LINEAR\",\"rightArm\":{\"pitch\":-1.0}},"
			+ "{\"tick\":0,\"easing\":\"LINEAR\",\"rightArm\":{\"x\":-3.0}},"
			+ "{\"tick\":10,\"easing\":\"LINEAR\",\"rightArm\":{\"pitch\":1.0}},"
			+ "{\"tick\":10,\"easing\":\"LINEAR\",\"rightArm\":{\"x\":-1.0}},"
			+ "{\"tick\":4,\"easing\":\"INCUBIC\",\"torso\":{\"yaw\":0.5}},"
			+ "{\"tick\":0,\"easing\":\"LINEAR\",\"torso\":{\"yaw\":0.0}}]}}";

	@Test
	void parsesNamesTicksAndMergesTheOneAxisMoves() {
		UryuPoseTrack t = UryuPoseTrack.parse(JSON);
		assertEquals("t", t.name());
		assertEquals(10, t.endTick());
		assertEquals(20, t.stopTick());
		assertTrue(t.keysRotation("rightArm"));
		assertTrue(t.keysPosition("rightArm"));
		assertTrue(t.keysRotation("torso"));
		assertFalse(t.keysPosition("torso"));
		assertFalse(t.keysRotation("leftArm"));
	}

	@Test
	void interpolatesHoldsAndLeavesUnkeyedAxesOpen() {
		UryuPoseTrack t = UryuPoseTrack.parse(JSON);
		assertEquals(-1.0, t.rotation("rightArm", 0)[0], EPS);
		assertEquals(0.0, t.rotation("rightArm", 5)[0], EPS);
		assertEquals(1.0, t.rotation("rightArm", 10)[0], EPS);
		assertEquals(1.0, t.rotation("rightArm", 99)[0], EPS, "held after the last key");
		assertEquals(-1.0, t.rotation("rightArm", -5)[0], EPS, "held before the first key");
		assertTrue(Double.isNaN(t.rotation("rightArm", 5)[1]), "yaw is not keyed");
		assertTrue(Double.isNaN(t.rotation("leftArm", 5)[0]));
		// keys are sorted by tick whatever their order in the file, the easing of the key shapes the segment that ends at it
		assertEquals(0.5 * 0.125, t.rotation("torso", 2)[1], EPS);
		assertEquals(0.5, t.rotation("torso", 4)[1], EPS);
	}

	@Test
	void positionsAreDeltasFromTheVanillaPivot() {
		UryuPoseTrack t = UryuPoseTrack.parse(JSON);
		// the right arm pivot of the game is x = -5 px
		assertEquals(2.0, t.position("rightArm", 0)[0], EPS);
		assertEquals(3.0, t.position("rightArm", 5)[0], EPS);
		assertTrue(Double.isNaN(t.position("rightArm", 0)[1]));
		assertEquals(-5.0, UryuPoseTrack.rest("rightArm")[0], EPS);
		assertEquals(5.0, UryuPoseTrack.rest("leftArm")[0], EPS);
		assertEquals(12.0, UryuPoseTrack.rest("leftLeg")[1], EPS);
	}

	@Test
	void easingNamesMatchTheirFormulas() {
		for (String n : new String[] {"LINEAR", "INOUTSINE", "OUTCUBIC", "INCUBIC", "OUTBACK", "INOUTQUAD", "OUTSINE", "INSINE", "EASEINOUTSINE", "INOUTCUBIC"}) {
			assertEquals(0.0, UryuPoseTrack.ease(n, 0.0), 1e-9, n);
			assertEquals(1.0, UryuPoseTrack.ease(n, 1.0), 1e-9, n);
		}
		assertEquals(0.5, UryuPoseTrack.ease("INOUTSINE", 0.5), EPS);
		assertEquals(0.875, UryuPoseTrack.ease("OUTCUBIC", 0.5), EPS);
		assertEquals(0.125, UryuPoseTrack.ease("INCUBIC", 0.5), EPS);
		assertEquals(0.5, UryuPoseTrack.ease("nonsense", 0.5), EPS, "unknown names are linear");
		assertEquals(0.0, UryuPoseTrack.ease("CONSTANT", 0.99), EPS);
		double max = 0.0;
		for (int i = 0; i <= 100; i++) {
			max = Math.max(max, UryuPoseTrack.ease("OUTBACK", i / 100.0));
		}
		assertTrue(max > 1.05, "OUTBACK overshoots");
		assertEquals(1.0, UryuPoseTrack.ease("OUTCUBIC", 7.0), EPS, "the input is clamped");
		assertEquals(0.0, UryuPoseTrack.ease("OUTCUBIC", -7.0), EPS);
	}

	@Test
	void theIdleFileIsTheDrawnBowPoseP1() {
		UryuPoseTrack t = UryuTestData.track("idle");
		double[] arm = t.rotation("rightArm", 0);
		// the chest turn of 6 degrees is composed into the arm key (the arms are not children of the torso in the game): about the 18 degrees of the storyboard
		assertEquals(-18.0, Math.toDegrees(arm[0]), 1.0);
		assertEquals(Math.toDegrees(UryuPoseMath.IDLE_ARM[0]), Math.toDegrees(arm[0]), 1.0, "close to the pitch of vanilla's held item pose, so a small pop into the layer");
		assertEquals(arm[0], t.rotation("rightArm", 20)[0], 1e-6, "a still pose");
		assertFalse(t.keysRotation("leftArm"), "the left arm is loose in the idle (vanilla walk swing)");
		assertFalse(t.keysRotation("rightLeg"));
	}

	@Test
	void everyShippedAnimationEndsNearTheIdleBowArm() {
		UryuPoseTrack idle = UryuTestData.track("idle");
		double[] ia = idle.rotation("rightArm", 0);
		for (String a : UryuAnims.names()) {
			if ("idle".equals(a) || "draw".equals(a) || "wings".equals(a)) {
				continue; // held poses
			}
			UryuPoseTrack t = UryuTestData.track(a);
			if (!t.keysRotation("rightArm")) {
				continue;
			}
			// the flow blends the last pose into the idle in 0.1 s: the bow arm is never far from it (the bankai release ends on the loose arms of the storyboard)
			double[] last = t.rotation("rightArm", t.endTick());
			double d = Math.max(Math.abs(ia[0] - last[0]), Math.max(Math.abs(ia[1] - last[1]), Math.abs(ia[2] - last[2])));
			assertTrue(Math.toDegrees(d) < 20.0, a + " ends " + Math.toDegrees(d) + " degrees from the idle bow arm");
		}
	}
}
