package dev.minebleach.reiatsutest.core.uryu;

import dev.minebleach.reiatsutest.core.swarm.V3;
import java.util.List;

/**
 * Homing Volley (design/URYU.md section 5): locks the first living target in a cone of 15 degrees up to 30 blocks, fires
 * {@code 3 + Focus} arrows (at most 6) that curve towards it at 12 degrees per tick.
 */
public final class Volley {
	private Volley() {
	}

	/** A possible target: its entity id and the point the arrows aim at. */
	public record Candidate(int id, V3 center) {
	}

	/** Arrows for the stacks used: 3, 4, 5, 6. */
	public static int arrowCount(int focusStacks) {
		return Math.min(UryuBalance.VOLLEY_MAX_ARROWS, UryuBalance.VOLLEY_BASE_ARROWS + Math.max(0, focusStacks));
	}

	/**
	 * The nearest candidate inside the cone around {@code look} (half angle {@code coneDeg}) and within {@code range} of the eye, or -1.
	 * Whether the way to it is free of blocks is the glue's business (it filters the candidates before).
	 */
	public static int pickTarget(V3 eye, V3 look, List<Candidate> candidates, double coneDeg, double range) {
		V3 l = look.normalizedOr(new V3(0, 0, 1));
		double cosMax = Math.cos(Math.toRadians(coneDeg));
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		for (Candidate c : candidates) {
			V3 d = c.center().sub(eye);
			double dist = d.length();
			if (dist > range || dist < 1.0E-6) {
				continue;
			}
			if (d.dot(l) / dist >= cosMax && dist < bestDist) {
				bestDist = dist;
				best = c.id();
			}
		}
		return best;
	}

	/**
	 * Start directions of {@code n} arrows: a ring of arrows on a cone of {@code spreadDeg} half angle around {@code look}, evenly spaced,
	 * so the volley leaves as a fan and closes on the target while it curves.
	 */
	public static V3[] directions(V3 look, int n, double spreadDeg) {
		V3 l = look.normalizedOr(new V3(0, 0, 1));
		V3 up = Math.abs(l.y()) > 0.95 ? new V3(1, 0, 0) : new V3(0, 1, 0);
		V3 u = l.cross(up).normalizedOr(new V3(1, 0, 0));
		V3 v = l.cross(u).normalizedOr(new V3(0, 1, 0));
		double s = Math.toRadians(spreadDeg);
		V3[] out = new V3[n];
		for (int i = 0; i < n; i++) {
			double th = Math.PI / 2.0 + 2.0 * Math.PI * i / Math.max(1, n);
			V3 side = u.scale(Math.cos(th)).add(v.scale(Math.sin(th)));
			out[i] = l.scale(Math.cos(s)).add(side.scale(Math.sin(s))).normalizedOr(l);
		}
		return out;
	}
}
