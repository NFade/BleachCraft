package dev.minebleach.reiatsutest.core.combat;

import java.util.ArrayList;
import java.util.List;

/**
 * The keep-out rectangle of the first person hands (r8, the user's play test: "the hands cover half of the screen and the crosshair"). The rule:
 * no part of an arm box (the vanilla arm of 4 x 12 x 4 pixels, fist included) may project into the central rectangle of the picture of
 * {@link #HALF_X} x {@link #HALF_Y} of the half width and half height (normalised device coordinates, so 18 percent of the width and of the height
 * of the screen). The sword is not an arm: its blade may cross the centre (only the tip is meant to be seen there).
 *
 * <p>Exact, not sampled: the box is convex, so its picture is the convex hull of the projected corners of the part of the box in front of the
 * near plane (the box is clipped against the plane first, edge by edge); the margin is the largest separation of that hull from the rectangle over the
 * axes of the separating axis test (the two screen axes and the hull edge normals): positive = clear by that much (ndc), negative = penetration depth.
 * Pure maths on {@link FpGeometry} (the same projection {@link FpClamp} uses: vertical field of view 70).
 */
public final class FpCentre {
	/** Half width of the keep-out rectangle in ndc (0.18 = 18 percent of the screen width). */
	public static final float HALF_X = 0.18f;
	/** Half height of the keep-out rectangle in ndc (18 percent of the screen height). */
	public static final float HALF_Y = 0.18f;
	private static final float TAN_HALF_FOV = (float) Math.tan(Math.toRadians(35.0));

	private FpCentre() {
	}

	/** Margin of one box (8 corners in camera space, the corner order of {@link FpGeometry#armCorners}): see the class comment. Large when nothing of it is in front of the near plane. */
	public static float boxMargin(float[][] cam, float aspect, float hx, float hy) {
		List<float[]> pts = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			if (cam[i][2] <= -FpGeometry.NEAR) {
				pts.add(cam[i]);
			}
			for (int b = 1; b < 8; b <<= 1) {
				int j = i ^ b;
				if (j > i && (cam[i][2] <= -FpGeometry.NEAR) != (cam[j][2] <= -FpGeometry.NEAR)) {
					float t = (-FpGeometry.NEAR - cam[i][2]) / (cam[j][2] - cam[i][2]);
					pts.add(new float[] {cam[i][0] + (cam[j][0] - cam[i][0]) * t, cam[i][1] + (cam[j][1] - cam[i][1]) * t, -FpGeometry.NEAR});
				}
			}
		}
		if (pts.isEmpty()) {
			return 1f;
		}
		float[][] p = new float[pts.size()][];
		for (int i = 0; i < p.length; i++) {
			float[] c = pts.get(i);
			p[i] = new float[] {c[0] / -c[2] / (TAN_HALF_FOV * aspect), c[1] / -c[2] / TAN_HALF_FOV};
		}
		return separation(hull(p), hx, hy);
	}

	/** Margin of the right arm of a pose ({@link FpGeometry#rightArm}). */
	public static float right(FpGeometry.Hold h, FpPose pose, float aspect, float hx, float hy) {
		float[][] ra = FpGeometry.rightArm(h, pose);
		float[][] cr = new float[8][];
		for (int i = 0; i < 8; i++) {
			cr[i] = FpGeometry.cam(ra[i]);
		}
		return boxMargin(cr, aspect, hx, hy);
	}

	/** Margin of the free left arm of a pose ({@code l} as in {@link FpClamp#margin}, null: riding on the hilt); large when the hold has no left hand. */
	public static float left(FpGeometry.Hold h, FpPose pose, FpPose stanceBase, float[] l, float aspect, float hx, float hy) {
		if (!h.left()) {
			return 1f;
		}
		float free = l == null ? 0f : l[6];
		float[] shift = l == null ? null : new float[] {l[0], l[1], l[2]};
		float[] fist = FpGeometry.leftFist(h, pose, stanceBase, free, shift);
		float[] extra = l == null ? new float[3] : new float[] {l[3], l[4], l[5]};
		return boxMargin(FpGeometry.leftArm(fist, pose, stanceBase, free, h.leftArmRot(), extra), aspect, hx, hy);
	}

	/** Both arms of a pose: the smaller margin. */
	public static float margin(FpGeometry.Hold h, FpPose pose, FpPose stanceBase, float[] l, float aspect, float hx, float hy) {
		return Math.min(right(h, pose, aspect, hx, hy), left(h, pose, stanceBase, l, aspect, hx, hy));
	}

	/** A gesture frame shaped for the picture: the hand rows scaled, dropped and softly limited forward. */
	public record Shaped(float[] r, float[] l) {
	}

	/**
	 * First step of the r8 gesture limits (before the reach factors of {@link FpClamp}): both hand rows (px py pz arx ary arz [free]) are scaled by
	 * {@code scale} (position and turn; the {@code free} weight of the left row is kept), the gesture envelope {@code env} (0..1) drops the hands by
	 * {@code drop} blocks (hand frame y, so the whole gesture works lower on the screen) and the forward reach is limited softly to {@code fwd}.
	 */
	public static Shaped shape(float[] r, float[] l, float env, float scale, float drop, float fwd) {
		float[] rr = r.clone();
		float[] ll = l.clone();
		for (int i = 0; i < 6; i++) {
			rr[i] *= scale;
			ll[i] *= scale;
		}
		rr[1] -= drop * env;
		ll[1] -= drop * env;
		rr[2] = FpClamp.softForward(rr[2], fwd);
		ll[2] = FpClamp.softForward(ll[2], fwd);
		return new Shaped(rr, ll);
	}

	/**
	 * Factor (0..1) of a row of the empty hands of the Byakuya bankai that keeps the arm out of the central rectangle: the largest {@code k} for which the
	 * arm with the row scaled by {@code k} (breath off, hand fully risen) has a margin of at least 0; 1 when the whole row is allowed.
	 */
	public static float freeFactor(FpParams pr, boolean leftArm, float[] row, float time, float aspect, float hx, float hy) {
		if (row == null) {
			return 1f;
		}
		return FpClamp.solve(k -> {
			float[] s = row.clone();
			for (int i = 0; i < 6 && i < s.length; i++) {
				s[i] *= k;
			}
			return boxMargin(freeArm(leftArm, FreeHands.frame(pr, leftArm, s, time, 0f, 1f)), aspect, hx, hy);
		});
	}

	/**
	 * Camera space corners of an empty vanilla arm drawn by the free hands of the Byakuya bankai ({@code FpCast.drawFreeArm}): the arm box of its side
	 * in its hand frame, turned about its fist by {@code f.q()}, moved by {@code f.move()}, placed at the hand frame of its side (x 0.56 / -0.56, y -0.52,
	 * z -0.72). Right hander layout.
	 */
	public static float[][] freeArm(boolean leftArm, FreeHands.Frame f) {
		float[][] src = FpGeometry.corners(leftArm);
		float fx = (leftArm ? -1f : 1f) * FpGeometry.FIST[0];
		float[] fist = {fx, FpGeometry.FIST[1], FpGeometry.FIST[2]};
		float ox = leftArm ? -0.56f : 0.56f;
		float[][] r = new float[8][];
		for (int i = 0; i < 8; i++) {
			float[] v = FpPose.rotate(f.q(), new float[] {src[i][0] - fist[0], src[i][1] - fist[1], src[i][2] - fist[2]});
			r[i] = new float[] {ox + f.move()[0] + fist[0] + v[0], -0.52f + f.move()[1] + fist[1] + v[1], -0.72f + f.move()[2] + fist[2] + v[2]};
		}
		return r;
	}

	// ------------------------------------------------------------------ 2D

	/** Convex hull of 2D points (monotone chain), counter clockwise; fewer than 3 points come back as they are. */
	static float[][] hull(float[][] pts) {
		float[][] p = pts.clone();
		java.util.Arrays.sort(p, (a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : Float.compare(a[1], b[1]));
		if (p.length < 3) {
			return p;
		}
		float[][] h = new float[2 * p.length][];
		int k = 0;
		for (int i = 0; i < p.length; i++) {
			while (k >= 2 && cross(h[k - 2], h[k - 1], p[i]) <= 0) {
				k--;
			}
			h[k++] = p[i];
		}
		for (int i = p.length - 2, t = k + 1; i >= 0; i--) {
			while (k >= t && cross(h[k - 2], h[k - 1], p[i]) <= 0) {
				k--;
			}
			h[k++] = p[i];
		}
		return java.util.Arrays.copyOf(h, Math.max(1, k - 1));
	}

	private static float cross(float[] o, float[] a, float[] b) {
		return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
	}

	/** Largest separation of a convex polygon from the rectangle |x| <= hx, |y| <= hy (negative: the deepest penetration along the best axis). */
	static float separation(float[][] poly, float hx, float hy) {
		float minX = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (float[] p : poly) {
			minX = Math.min(minX, p[0]);
			maxX = Math.max(maxX, p[0]);
			minY = Math.min(minY, p[1]);
			maxY = Math.max(maxY, p[1]);
		}
		float best = Math.max(Math.max(minX - hx, -hx - maxX), Math.max(minY - hy, -hy - maxY));
		if (poly.length < 3) {
			return best;
		}
		float[][] rect = {{-hx, -hy}, {hx, -hy}, {hx, hy}, {-hx, hy}};
		for (int i = 0; i < poly.length; i++) {
			float[] a = poly[i];
			float[] b = poly[(i + 1) % poly.length];
			// counter clockwise polygon: the outward normal of the edge is (dy, -dx)
			float nx = b[1] - a[1];
			float ny = -(b[0] - a[0]);
			float l = (float) Math.sqrt(nx * nx + ny * ny);
			if (l < 1e-9f) {
				continue;
			}
			nx /= l;
			ny /= l;
			float lo = Float.MAX_VALUE;
			for (float[] c : rect) {
				lo = Math.min(lo, nx * c[0] + ny * c[1]);
			}
			best = Math.max(best, lo - (nx * a[0] + ny * a[1]));
		}
		return best;
	}
}
