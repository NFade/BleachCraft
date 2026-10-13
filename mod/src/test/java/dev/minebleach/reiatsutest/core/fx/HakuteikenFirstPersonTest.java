package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** First-person guard of the Hakuteiken wings and halo. */
class HakuteikenFirstPersonTest {
	private static final double YAW_MAX = Math.toRadians(HakuteikenFirstPerson.YAW_MAX_DEG);

	@Test
	void wrapPiKeepsAnglesInOnePeriod() {
		assertEquals(0.0, HakuteikenFirstPerson.wrapPi(0.0), 1e-12);
		assertEquals(-Math.PI, HakuteikenFirstPerson.wrapPi(Math.PI), 1e-12);
		assertEquals(Math.toRadians(10), HakuteikenFirstPerson.wrapPi(Math.toRadians(370)), 1e-12);
		assertEquals(Math.toRadians(-170), HakuteikenFirstPerson.wrapPi(Math.toRadians(190)), 1e-12);
		assertEquals(Math.toRadians(170), HakuteikenFirstPerson.wrapPi(Math.toRadians(-190)), 1e-12);
	}

	@Test
	void limitYawKeepsTheBodyYawInsideTheLimit() {
		double cam = Math.toRadians(40);
		double body = Math.toRadians(48);
		assertEquals(body, HakuteikenFirstPerson.limitYaw(body, cam, YAW_MAX), 1e-12);
		assertEquals(cam, HakuteikenFirstPerson.limitYaw(cam, cam, YAW_MAX), 1e-12);
	}

	@Test
	void limitYawClampsToTheCameraSideOfTheBody() {
		double cam = Math.toRadians(40);
		assertEquals(cam + YAW_MAX, HakuteikenFirstPerson.limitYaw(cam + Math.toRadians(75), cam, YAW_MAX), 1e-12);
		assertEquals(cam - YAW_MAX, HakuteikenFirstPerson.limitYaw(cam - Math.toRadians(75), cam, YAW_MAX), 1e-12);
		// across the +-180 seam the short way round is used: body at -160 is 25 degrees past a camera at 175
		double seamCam = Math.toRadians(175);
		double seamBody = Math.toRadians(-160);
		double got = HakuteikenFirstPerson.limitYaw(seamBody, seamCam, YAW_MAX);
		assertEquals(YAW_MAX, HakuteikenFirstPerson.wrapPi(got - seamCam), 1e-12);
	}

	@Test
	void limitYawIsContinuousAsTheBodyTurns() {
		double cam = 0.3;
		double prev = HakuteikenFirstPerson.limitYaw(cam - Math.PI + 0.001, cam, YAW_MAX);
		for (double d = -Math.PI + 0.001; d <= Math.PI - 0.003; d += 0.002) {
			double y = HakuteikenFirstPerson.limitYaw(cam + d, cam, YAW_MAX);
			assertTrue(Math.abs(HakuteikenFirstPerson.wrapPi(y - prev)) < 0.0021, "jump at offset " + d);
			prev = y;
		}
	}

	@Test
	void zeroLimitPinsTheRigToTheCamera() {
		assertEquals(1.0, HakuteikenFirstPerson.limitYaw(2.0, 1.0, 0.0), 1e-12);
		assertEquals(1.0, HakuteikenFirstPerson.limitYaw(2.0, 1.0, -3.0), 1e-12);
	}

	@Test
	void forwardFollowsTheMinecraftConvention() {
		double[] f = new double[3];
		HakuteikenFirstPerson.forward(0, 0, f);
		assertEquals(0, f[0], 1e-12);
		assertEquals(0, f[1], 1e-12);
		assertEquals(1, f[2], 1e-12);
		HakuteikenFirstPerson.forward(Math.toRadians(90), 0, f);
		assertEquals(-1, f[0], 1e-12);
		assertEquals(0, f[2], 1e-12);
		HakuteikenFirstPerson.forward(0, Math.toRadians(-90), f);
		assertEquals(1, f[1], 1e-12, "pitch -90 looks straight up");
	}

	@Test
	void fadeIsZeroNearTheEyeAndOneFar() {
		double c = HakuteikenFirstPerson.CLEAR;
		double f = HakuteikenFirstPerson.FULL;
		assertEquals(0.0, HakuteikenFirstPerson.fade(-5, c, f), 0.0);
		assertEquals(0.0, HakuteikenFirstPerson.fade(0, c, f), 0.0);
		assertEquals(0.0, HakuteikenFirstPerson.fade(c, c, f), 0.0);
		assertEquals(1.0, HakuteikenFirstPerson.fade(f, c, f), 0.0);
		assertEquals(1.0, HakuteikenFirstPerson.fade(50, c, f), 0.0);
		assertEquals(0.5, HakuteikenFirstPerson.fade((c + f) / 2, c, f), 1e-12);
		double prev = 0;
		for (double d = c; d <= f; d += 0.01) {
			double v = HakuteikenFirstPerson.fade(d, c, f);
			assertTrue(v >= prev - 1e-12, "monotonic");
			prev = v;
		}
		// degenerate band: a hard step
		assertEquals(0.0, HakuteikenFirstPerson.fade(1.0, 2.0, 2.0), 0.0);
		assertEquals(1.0, HakuteikenFirstPerson.fade(2.0, 2.0, 2.0), 0.0);
	}

	@Test
	void yawLimitIsHalvedForReduceMotion() {
		assertEquals(Math.toRadians(15), HakuteikenFirstPerson.yawLimit(15, false), 1e-12);
		assertEquals(Math.toRadians(7.5), HakuteikenFirstPerson.yawLimit(15, true), 1e-12);
		assertEquals(0.0, HakuteikenFirstPerson.yawLimit(-4, false), 0.0);
	}

	// ------------------------------------------------------------------------------------------ root cause numbers and the fix on the wing shape

	/** Local points of the final wing fan (angle -12 to 48 degrees, radius to 4.46 m, forward curve z = 0.8 (r / 4.46)^2 like the exported mesh). */
	private static List<double[]> wingSamples(int side) {
		List<double[]> out = new ArrayList<>();
		for (double deg = -12; deg <= 48.001; deg += 6) {
			for (double r = 0.2; r <= 4.46; r += 0.25) {
				double x = side * r * Math.cos(Math.toRadians(deg));
				double y = r * Math.sin(Math.toRadians(deg));
				double z = 0.8 * (r / 4.46) * (r / 4.46);
				out.add(new double[] {side * 0.12 + x, 1.35 + y, -0.15 + z});
			}
		}
		return out;
	}

	/**
	 * Number of wing sample points (both wings) inside the view cone of half angle {@code coneDeg} about the camera forward (camera yaw 0, eye at
	 * 1.62 m over the feet) whose fade is at least {@code minFade}, for a rig yawed by {@code rigYaw} and pushed back by {@code back}.
	 */
	private static int visibleSamples(double rigYaw, double back, double coneDeg, double minFade) {
		double s = Math.sin(rigYaw);
		double c = Math.cos(rigYaw);
		double eyeY = 1.62;
		double[] fwd = new double[3];
		HakuteikenFirstPerson.forward(0, 0, fwd);
		int n = 0;
		for (int side = 1; side >= -1; side -= 2) {
			for (double[] p : wingSamples(side)) {
				double z = p[2] - back;
				double wx = p[0] * c - z * s;
				double wz = p[0] * s + z * c;
				double wy = p[1];
				double depth = HakuteikenFirstPerson.depth(wx, wy, wz, 0, eyeY, 0, fwd);
				if (depth <= 0.05) {
					continue;
				}
				double fade = HakuteikenFirstPerson.fade(depth, HakuteikenFirstPerson.CLEAR, HakuteikenFirstPerson.FULL);
				double ang = Math.toDegrees(Math.atan2(Math.hypot(wx, wy - eyeY), depth));
				if (ang < coneDeg && fade >= minFade) {
					n++;
				}
			}
		}
		return n;
	}

	@Test
	void rootCauseWingSheetsEnterTheViewWhenTheBodyTrailsTheHead() {
		// unfixed rig: body yaw 0, 10, 45, 75 degrees off the camera, 55 degree half cone (a 16:9 view at FOV 70 to 90)
		assertEquals(0, visibleSamples(0.0, 0.0, 55, 0.0), "aligned: the sheets stay behind the eye");
		assertEquals(0, visibleSamples(Math.toRadians(10), 0.0, 55, 0.0));
		assertTrue(visibleSamples(Math.toRadians(45), 0.0, 55, 0.0) > 10, "45 degrees: a wing is in view");
		assertTrue(visibleSamples(Math.toRadians(75), 0.0, 55, 0.0) > 50, "75 degrees (vanilla idle limit): a wing fills the view");
	}

	@Test
	void fixKeepsEveryOpaqueWingPointOutOfTheView() {
		for (double offDeg = -180; offDeg <= 180; offDeg += 5) {
			double rig = HakuteikenFirstPerson.limitYaw(Math.toRadians(offDeg), 0.0, YAW_MAX);
			assertEquals(0, visibleSamples(rig, HakuteikenFirstPerson.BACK, 55, 0.02), "body offset " + offDeg + " leaves wing points in the view");
		}
	}

	@Test
	void fixDoesNotChangeTheRigWhenTheBodyFollowsTheHead() {
		for (double d = -YAW_MAX; d <= YAW_MAX; d += 0.01) {
			assertEquals(d, HakuteikenFirstPerson.limitYaw(d, 0.0, YAW_MAX), 1e-12);
		}
	}
}
