package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** R11 review fix 9: ranges of the {@code tp.*} keys of the player's client file, and the display scale 0 guard of the solver. */
class TpKeyLimitsTest {
	@Test
	void aHandTypedHugeNumberIsClamped() {
		assertEquals(90.0, TpKeyLimits.num("hold.tilt", 1e9, 12.0));
		assertEquals(-90.0, TpKeyLimits.num("hold.tilt", -1e9, 12.0));
		assertEquals(2.5, TpKeyLimits.num("swordScale", 99, 1.25));
		assertEquals(0.5, TpKeyLimits.num("swordScale.senbonzakura", 0.0, 1.25), "per item keys use the swordScale range");
		assertEquals(16.0, TpKeyLimits.num("grip.c_r", 1000, -0.8));
		assertEquals(80.0, TpKeyLimits.num("arm.yaw_r", 90, 0), "the solver divides by cos(yaw)");
		assertEquals(1.0, TpKeyLimits.num("draw.pull", 5, 0.5));
	}

	@Test
	void valuesInsideTheRangeAndTheShippedDefaultsPass() {
		assertEquals(12.0, TpKeyLimits.num("hold.tilt", 12.0, 0));
		assertEquals(TpPose.GUARD_FWD, TpKeyLimits.num("guard.fwd", TpPose.GUARD_FWD, 0));
		assertEquals(TpPose.GUARD_TILT, TpKeyLimits.num("guard.tilt", TpPose.GUARD_TILT, 0));
		assertEquals(6.0, TpKeyLimits.num("arm.max_shift", 6.0, 0));
		assertEquals(0.10, TpKeyLimits.num("guard.in_s", 0.10, 0));
	}

	@Test
	void nonFiniteAndUnknownKeysGiveTheDefault() {
		assertEquals(7.0, TpKeyLimits.num("hold.tilt", Double.NaN, 7.0));
		assertEquals(7.0, TpKeyLimits.num("hold.tilt", Double.POSITIVE_INFINITY, 7.0));
		assertEquals(7.0, TpKeyLimits.num("hold.nonsense", 3.0, 7.0));
		assertEquals(7.0, TpKeyLimits.num("swordScale.", 3.0, 7.0), "an empty item id is not a key");
		assertNull(TpKeyLimits.numRange("nope"));
		assertNotNull(TpKeyLimits.numRange("hold.tilt"));
	}

	@Test
	void vectorsAreClampedPerComponentAndMustHaveTheRightLength() {
		double[] def = {1, 2, 3};
		assertArrayEquals(new double[] {8, -8, 0.5}, TpKeyLimits.vec("guard.shift", new double[] {99, -99, 0.5}, def));
		assertArrayEquals(new double[] {16, 0, -16}, TpKeyLimits.vec("hold.pos", new double[] {50, 0, -50, 7}, def), "extra components are dropped");
		assertSame(def, TpKeyLimits.vec("guard.shift", new double[] {1, 2}, def), "too short");
		assertSame(def, TpKeyLimits.vec("guard.shift", new double[] {1, Double.NaN, 2}, def));
		assertSame(def, TpKeyLimits.vec("unknown", new double[] {1, 2, 3}, def));
		assertSame(def, TpKeyLimits.vec("guard.shift", null, def));
		assertEquals(6, TpKeyLimits.vecSize("guard.arm_r"));
		assertEquals(0, TpKeyLimits.vecSize("unknown"));
		assertArrayEquals(new double[] {180, -180, 0, 16, -16, 0.5}, TpKeyLimits.vec("guard.arm_l", new double[] {999, -999, 0, 99, -99, 0.5}, null),
				"the six numbers of an authored arm: angles within a half turn, offsets within a body size");
	}

	@Test
	void everyKeyTheSolverAsksForHasARange() throws IOException {
		String src = Files.readString(Paths.get("src", "main", "java", "dev", "minebleach", "reiatsutest", "core", "combat", "TpPose.java"), StandardCharsets.UTF_8);
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("pr\\.(num|vec)\\(\"([a-z_.A-Z]+)\"").matcher(src);
		int seen = 0;
		while (m.find()) {
			String key = m.group(2);
			boolean ok = m.group(1).equals("num") ? TpKeyLimits.numRange(key) != null : TpKeyLimits.vecSize(key) > 0;
			assertTrue(ok, "TpPose reads " + key + " but TpKeyLimits has no range for it");
			seen++;
		}
		assertTrue(seen >= 20, "the scan found the keys (" + seen + ")");
	}

	@Test
	void aDisplayScaleOfZeroGivesNoNaNPose() {
		var rig = new dev.minebleach.reiatsutest.core.obj.DrawRig(0.566f, -2.5276f, 3.0276f, 0.7514f, dev.minebleach.reiatsutest.core.obj.DrawRig.Rigid.IDENTITY,
				0.55f, 0.55f, 0.15f);
		for (double bad : new double[] {0.0, -1.0, Double.NaN, 1e-9}) {
			var hip = new TpPose.Hip(new double[] {3.0, 9.0, -2.5}, new double[] {0.35, 0.30, 0.89}, bad);
			var disp = new TpPose.Display(new double[] {45, 180, 0}, new double[] {0, -2 / 16.0, 1.75 / 16}, bad);
			for (double b : new double[] {0, 1}) {
				for (double p : new double[] {0, 1}) {
					TpPose.Result r = TpPose.solve(new TpPose.Input(p, b, false, false, hip, disp, rig, 1.25), TpPose.Params.DEFAULTS);
					assertFinite(r.main(), "main " + bad);
					assertFinite(r.off(), "off " + bad);
					assertFalse(Double.isNaN(r.gS()) || Double.isNaN(r.gT()[0]) || Double.isNaN(r.sword().t()[0]), "frames " + bad);
				}
			}
		}
		assertEquals(TpPose.REF_SCALE, TpPose.saneScale(0.0));
		assertEquals(2.0, TpPose.saneScale(2.0));
		// a NaN sword scale from a bad config is the neutral 1.0
		var hip = new TpPose.Hip(new double[] {3.0, 9.0, -2.5}, new double[] {0.35, 0.30, 0.89}, 1.6);
		var disp = new TpPose.Display(new double[] {45, 180, 0}, new double[] {0, -2 / 16.0, 1.75 / 16}, 1.6);
		TpPose.Result r = TpPose.solve(new TpPose.Input(1, 1, false, false, hip, disp, rig, Double.NaN), TpPose.Params.DEFAULTS);
		assertFinite(r.main(), "NaN sword scale");
	}

	private static void assertFinite(TpPose.Arm a, String what) {
		for (double v : new double[] {a.pitch(), a.yaw(), a.roll(), a.dx(), a.dy(), a.dz()}) {
			assertTrue(!Double.isNaN(v) && !Double.isInfinite(v), what);
		}
	}
}
