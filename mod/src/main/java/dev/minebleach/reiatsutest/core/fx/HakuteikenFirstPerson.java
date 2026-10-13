package dev.minebleach.reiatsutest.core.fx;

/**
 * First-person guard of the Hakuteiken wings and halo (pure math, no Minecraft types). The rig (wings, halo, their glows) hangs off the
 * owner's body yaw, which in vanilla trails the head by up to 75 degrees (and swings to the walking direction while moving). From the
 * owner's own camera that tilts the 4.3 m wide wing sheets, which stand about 0.15 m behind the eye, into the frustum, so a wing fills the
 * view. For the local first-person camera only, the rig yaw is limited to a few degrees around the camera yaw ({@link #limitYaw}), the rig
 * is pushed back a little, and whatever still lies near the eye is faded out by its depth along the view direction ({@link #fade}).
 * All angles are radians, the yaw convention is the Minecraft one (forward = (-sin yaw, cos yaw)).
 */
public final class HakuteikenFirstPerson {
	/** Default limit of the rig yaw around the camera yaw, degrees. */
	public static final double YAW_MAX_DEG = 15.0;
	/** Default extra push of the rig backwards (along the rig's own -forward), metres. */
	public static final double BACK = 0.35;
	/** Default depth along the view where the fade-in starts (0 at and before it), metres. */
	public static final double CLEAR = 0.8;
	/** Default depth along the view where the fade-in ends (1 from it on), metres. */
	public static final double FULL = 3.0;

	private HakuteikenFirstPerson() {
	}

	/** Wraps an angle to [-pi, pi). */
	public static double wrapPi(double a) {
		double t = (a + Math.PI) % (2 * Math.PI);
		if (t < 0) {
			t += 2 * Math.PI;
		}
		return t - Math.PI;
	}

	/**
	 * The yaw of the rig: the body yaw, but never further than {@code maxDiff} from the camera yaw. Continuous in both inputs (so it stays
	 * smooth with the partial tick), equal to the body yaw while it is within the limit.
	 */
	public static double limitYaw(double bodyYaw, double camYaw, double maxDiff) {
		double m = Math.max(0.0, maxDiff);
		double d = wrapPi(bodyYaw - camYaw);
		if (d > m) {
			d = m;
		} else if (d < -m) {
			d = -m;
		}
		return camYaw + d;
	}

	/** Unit view direction of a camera with the given yaw and pitch (Minecraft convention: pitch positive looks down). */
	public static void forward(double yaw, double pitch, double[] out) {
		double cp = Math.cos(pitch);
		out[0] = -Math.sin(yaw) * cp;
		out[1] = -Math.sin(pitch);
		out[2] = Math.cos(yaw) * cp;
	}

	/** Signed distance of the point p from the camera c along the unit view direction f. */
	public static double depth(double px, double py, double pz, double cx, double cy, double cz, double[] f) {
		return (px - cx) * f[0] + (py - cy) * f[1] + (pz - cz) * f[2];
	}

	/** Opacity factor by view depth: 0 at depth {@code clear} and nearer (including everything behind the eye), 1 from {@code full} on, smoothstep between. */
	public static double fade(double depth, double clear, double full) {
		if (full <= clear) {
			return depth >= full ? 1.0 : 0.0;
		}
		double x = (depth - clear) / (full - clear);
		if (x <= 0) {
			return 0.0;
		}
		if (x >= 1) {
			return 1.0;
		}
		return x * x * (3 - 2 * x);
	}

	/** Yaw limit in radians from the tuned degrees; reduce motion / photosensitive safe halve it. */
	public static double yawLimit(double maxDeg, boolean gentle) {
		return Math.toRadians(Math.max(0.0, maxDeg) * (gentle ? 0.5 : 1.0));
	}
}
