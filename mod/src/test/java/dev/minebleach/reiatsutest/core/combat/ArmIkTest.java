package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Arm placement of the fp2 rig. The expected numbers come from the numpy replica of the rig (blender/fp2/fp_math.py). */
class ArmIkTest {
	/** Model matrix of a placement: translate(t) rotate(q) scale(1, k, 1) translate(-fistLocal), row major 4x4. */
	private static double[] matrix(ArmIk.Placement p) {
		double x = p.q()[0];
		double y = p.q()[1];
		double z = p.q()[2];
		double w = p.q()[3];
		double[] r = {1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
			2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)};
		double k = p.stretch();
		// R * S: scale the second column
		double[] m = new double[16];
		for (int i = 0; i < 3; i++) {
			m[i * 4] = r[i * 3];
			m[i * 4 + 1] = r[i * 3 + 1] * k;
			m[i * 4 + 2] = r[i * 3 + 2];
		}
		double[] fl = {p.fistLocal()[0], p.fistLocal()[1], p.fistLocal()[2]};
		for (int i = 0; i < 3; i++) {
			m[i * 4 + 3] = p.t()[i] - (m[i * 4] * fl[0] + m[i * 4 + 1] * fl[1] + m[i * 4 + 2] * fl[2]);
		}
		m[15] = 1;
		return m;
	}

	private static void assertMatrix(double[] expected, double[] got) {
		for (int i = 0; i < 12; i++) {
			assertEquals(expected[i], got[i], 2e-4, "element " + i);
		}
	}

	@Test
	void shoulderIsAlongTheRestDirectionBehindTheRestFist() {
		float[] s = ArmIk.shoulder(false);
		assertEquals(0.36015f, s[0], 1e-4f);
		assertEquals(-1.00766f, s[1], 1e-4f);
		assertEquals(-0.21892f, s[2], 1e-4f);
		float[] l = ArmIk.shoulder(true);
		assertEquals(-s[0], l[0], 1e-5f);
		assertEquals(s[1], l[1], 1e-5f);
		assertEquals(s[2], l[2], 1e-5f);
	}

	@Test
	void rightArmMatchesTheReplica() {
		float[] blade = {0.2f, 0.8f, -0.55f};
		float n = (float) Math.sqrt(0.04 + 0.64 + 0.3025);
		for (int i = 0; i < 3; i++) {
			blade[i] /= n;
		}
		ArmIk.Placement p = ArmIk.place(false, false, new float[] {0.25f, -0.60f, -1.0f}, ArmIk.shoulder(false), blade, 0f, 1f);
		assertEquals(1f, p.stretch(), 0f);
		double[] exp = {-0.55255, -0.12406, 0.82419, 0.12033, -0.76832, 0.45911, -0.44599, -1.17506, -0.32307, -0.87967, -0.349, -0.57136};
		assertMatrix(exp, matrix(p));
	}

	@Test
	void slimLeftArmWithRollMatchesTheReplica() {
		ArmIk.Placement p = ArmIk.place(true, true, new float[] {-0.3f, -0.4f, -0.9f}, ArmIk.shoulder(true), null, 15f, 1f);
		double[] exp = {-0.59093, 0.06576, -0.80404, -0.14002, 0.62719, 0.6643, -0.40662, -1.05154, 0.50738, -0.74457, -0.4338, -0.58579};
		assertMatrix(exp, matrix(p));
	}

	@Test
	void theFistLandsOnTheTargetAndTheArmPointsToTheShoulder() {
		float[] fist = {0.1f, -0.5f, -0.9f};
		ArmIk.Placement p = ArmIk.place(false, false, fist, ArmIk.shoulder(false), null, 0f, 1f);
		double[] m = matrix(p);
		float[] fl = p.fistLocal();
		float[] f = ArmIk.transform(m, fl);
		for (int i = 0; i < 3; i++) {
			assertEquals(fist[i], f[i], 1e-4f);
		}
		// the far end of the arm (y = 0 of the box) lies on the line fist -> shoulder
		float[] end = ArmIk.transform(m, new float[] {fl[0], 0f, 0f});
		float[] sh = ArmIk.shoulder(false);
		double dx = sh[0] - fist[0];
		double dy = sh[1] - fist[1];
		double dz = sh[2] - fist[2];
		double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double ex = end[0] - fist[0];
		double ey = end[1] - fist[1];
		double ez = end[2] - fist[2];
		double el = Math.sqrt(ex * ex + ey * ey + ez * ez);
		assertEquals(1.0, (ex * dx + ey * dy + ez * dz) / (el * dl), 1e-4);
		assertEquals(ArmIk.ARM_LEN, el, 1e-4);
	}

	@Test
	void armStretchesOnlyBeyondTheRestReach() {
		float[] sh = ArmIk.shoulder(false);
		ArmIk.Placement near = ArmIk.place(false, false, new float[] {0.2f, -0.5f, -0.5f}, sh, null, 0f, 2f);
		assertEquals(1f, near.stretch(), 0f);
		ArmIk.Placement far = ArmIk.place(false, false, new float[] {0.0f, -0.2f, -1.9f}, sh, null, 0f, 2f);
		assertTrue(far.stretch() > 1.5f, "stretch " + far.stretch());
		ArmIk.Placement capped = ArmIk.place(false, false, new float[] {0.0f, -0.2f, -3.9f}, sh, null, 0f, 1.6f);
		assertEquals(1.6f, capped.stretch(), 1e-6f);
	}
}
