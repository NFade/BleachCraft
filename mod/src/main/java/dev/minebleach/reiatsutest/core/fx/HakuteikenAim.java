package dev.minebleach.reiatsutest.core.fx;

/**
 * Aim geometry of Shukei: Hakuteiken (effect id 33), shared by the server hit line and the client white line / blade so that both agree
 * with the crosshair. Pure math, no Minecraft types (CorePurityTest).
 *
 * <p>The crosshair is the camera ray {@code eye + look * t}. The server hits along exactly that ray ({@code eye}, {@code look}, reach =
 * min(range, distance to the first block)). The visual cannot start on the ray in third person (it leaves the hand), but it must END on it:
 * {@link #target} is that end point and the beam runs from {@link #origin} to it. The origin is built in the LOOK frame (the head yaw), not
 * in the body frame: the body yaw lags the head by up to ~50 degrees in vanilla, which used to swing the shoulder offset around the eye.
 * In first person the owner's lateral offset is dropped (the caller passes {@code side = 0}), so the beam lies in the vertical plane of the
 * crosshair and has no sideways bias at any depth.
 */
public final class HakuteikenAim {
	/** Hand anchor, metres from the eye: to the right of the look, below the eye (1.62 - 1.40), forward along the look. */
	public static final double HAND_SIDE = 0.30;
	public static final double HAND_DOWN = 0.22;
	public static final double HAND_FORWARD = 0.60;

	private HakuteikenAim() {
	}

	/** Length of the beam: the free ray up to the first block ({@code toAim}), at most {@code range}. */
	public static double reach(double range, double toAim) {
		return Math.max(0.0, Math.min(range, toAim));
	}

	/** Horizontal unit vector to the RIGHT of the look (x, z written to out[0], out[1]); straight up or down gives -x. */
	public static void right(double lx, double lz, double[] out) {
		double h = Math.sqrt(lx * lx + lz * lz);
		if (h < 1.0E-4) {
			out[0] = -1.0;
			out[1] = 0.0;
		} else {
			out[0] = -lz / h;
			out[1] = lx / h;
		}
	}

	/** The hand anchor: {@code eye + right * side - up * down + look * forward}, right taken from the look (head yaw). */
	public static void origin(double ex, double ey, double ez, double lx, double ly, double lz, double side, double down, double forward, double[] out) {
		double[] r = new double[2];
		right(lx, lz, r);
		out[0] = ex + r[0] * side + lx * forward;
		out[1] = ey - down + ly * forward;
		out[2] = ez + r[1] * side + lz * forward;
	}

	/** The end of the beam: the point of the crosshair ray at {@code reach} (the server burst point when reach is the server ray length). */
	public static void target(double ex, double ey, double ez, double lx, double ly, double lz, double reach, double[] out) {
		out[0] = ex + lx * reach;
		out[1] = ey + ly * reach;
		out[2] = ez + lz * reach;
	}

	/** Unit direction {@code from -> to}; {@code fallback} (look) when the points coincide. */
	public static void direction(double[] from, double[] to, double[] fallback, double[] out) {
		double dx = to[0] - from[0];
		double dy = to[1] - from[1];
		double dz = to[2] - from[2];
		double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (l < 1.0E-9) {
			System.arraycopy(fallback, 0, out, 0, 3);
			return;
		}
		out[0] = dx / l;
		out[1] = dy / l;
		out[2] = dz / l;
	}

	/**
	 * Where the beam segment {@code from -> to} is seen relative to the crosshair at the given depth along the look (the distance from the
	 * eye measured on the look axis). Returns {right, up, angleDeg} with right and up in metres in the camera frame (+right, +up) and the
	 * angle between the look and the direction eye -> beam point; all NaN when the segment does not reach that depth.
	 */
	public static double[] offsetAtDepth(double[] eye, double[] look, double[] from, double[] to, double depth) {
		double d0 = (from[0] - eye[0]) * look[0] + (from[1] - eye[1]) * look[1] + (from[2] - eye[2]) * look[2];
		double d1 = (to[0] - eye[0]) * look[0] + (to[1] - eye[1]) * look[1] + (to[2] - eye[2]) * look[2];
		double lo = Math.min(d0, d1);
		double hi = Math.max(d0, d1);
		if (depth < lo - 1.0E-9 || depth > hi + 1.0E-9 || Math.abs(d1 - d0) < 1.0E-12) {
			return new double[] {Double.NaN, Double.NaN, Double.NaN};
		}
		double k = (depth - d0) / (d1 - d0);
		double px = from[0] + (to[0] - from[0]) * k - eye[0];
		double py = from[1] + (to[1] - from[1]) * k - eye[1];
		double pz = from[2] + (to[2] - from[2]) * k - eye[2];
		double[] r = new double[2];
		right(look[0], look[2], r);
		double rx = r[0];
		double rz = r[1];
		// up = right x look
		double ux = 0.0 * look[2] - rz * look[1];
		double uy = rz * look[0] - rx * look[2];
		double uz = rx * look[1] - 0.0 * look[0];
		double right = px * rx + pz * rz;
		double up = px * ux + py * uy + pz * uz;
		double lateral = Math.sqrt(right * right + up * up);
		return new double[] {right, up, Math.toDegrees(Math.atan2(lateral, depth))};
	}
}
