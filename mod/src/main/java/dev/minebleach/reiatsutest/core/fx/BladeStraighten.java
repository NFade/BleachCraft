package dev.minebleach.reiatsutest.core.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure maths of the r8 straightening of the Byakuya bankai giant blades (the user's play test: the exported mesh bends 0.83 m over 8 m,
 * and with the blade width scaled 1.5 times the sori is 1.24 m, a scimitar; the reference shows nearly straight slabs). The mesh keeps
 * its length axis along local +Y and bends along local Z (the width axis). The straightened mesh is a SHEAR along Z that removes the
 * share {@code 1 - curve} of the spine offset at every height:
 * <pre>z' = z - (1 - curve) * spine(y)</pre>
 * {@code spine(y)} is the centreline of the blade measured from the mesh itself: vertices are grouped into rings (heights closer than
 * {@link #RING_GAP}), the centre of a ring is the middle of its Z extent, and the spine is the piecewise linear curve through the ring
 * centres (the apex counts as a ring of one vertex). The shear never changes Y, so the length and the glow height (local y >= 6.85) stay
 * as they are; {@code curve = 1} returns the original mesh and {@code curve = 0} puts every ring centre on z = spine(0).
 * Positions are interleaved xyz triples ({@code float[3 * n]}).
 */
public final class BladeStraighten {
	/** Vertices whose heights differ by less than this (blocks) belong to the same ring (the tilted rings of the export span 0.02 to 0.1). */
	public static final double RING_GAP = 0.075;

	private BladeStraighten() {
	}

	/**
	 * The spine table of a mesh: {@code {y0, z0, y1, z1, ...}} with ascending heights, one pair per ring (mean height, centre of the Z extent).
	 * An empty mesh gives an empty table.
	 */
	public static double[] spine(float[] pos) {
		int n = pos.length / 3;
		Integer[] idx = new Integer[n];
		for (int i = 0; i < n; i++) {
			idx[i] = i;
		}
		java.util.Arrays.sort(idx, (a, b) -> Float.compare(pos[a * 3 + 1], pos[b * 3 + 1]));
		List<double[]> rings = new ArrayList<>();
		int i = 0;
		while (i < n) {
			double sumY = 0;
			double lo = Double.MAX_VALUE;
			double hi = -Double.MAX_VALUE;
			int count = 0;
			int j = i;
			while (j < n && (j == i || pos[idx[j] * 3 + 1] - pos[idx[j - 1] * 3 + 1] <= RING_GAP)) {
				float z = pos[idx[j] * 3 + 2];
				sumY += pos[idx[j] * 3 + 1];
				lo = Math.min(lo, z);
				hi = Math.max(hi, z);
				count++;
				j++;
			}
			rings.add(new double[] {sumY / count, 0.5 * (lo + hi)});
			i = j;
		}
		double[] t = new double[rings.size() * 2];
		for (int k = 0; k < rings.size(); k++) {
			t[k * 2] = rings.get(k)[0];
			t[k * 2 + 1] = rings.get(k)[1];
		}
		return t;
	}

	/** The spine offset (local z) at height {@code y}: linear between the rings, constant outside them. */
	public static double spineAt(double[] table, double y) {
		int n = table.length / 2;
		if (n == 0) {
			return 0;
		}
		if (y <= table[0]) {
			return table[1];
		}
		if (y >= table[(n - 1) * 2]) {
			return table[(n - 1) * 2 + 1];
		}
		for (int k = 1; k < n; k++) {
			if (y <= table[k * 2]) {
				double y0 = table[(k - 1) * 2];
				double y1 = table[k * 2];
				double f = y1 > y0 ? (y - y0) / (y1 - y0) : 0;
				return table[(k - 1) * 2 + 1] + f * (table[k * 2 + 1] - table[(k - 1) * 2 + 1]);
			}
		}
		return table[(n - 1) * 2 + 1];
	}

	/** Slope {@code d spine / d y} of the segment that contains {@code y} (0 outside the rings); used to tilt the normals with the shear. */
	public static double slopeAt(double[] table, double y) {
		int n = table.length / 2;
		for (int k = 1; k < n; k++) {
			if (y <= table[k * 2]) {
				double dy = table[k * 2] - table[(k - 1) * 2];
				return dy > 1e-9 ? (table[k * 2 + 1] - table[(k - 1) * 2 + 1]) / dy : 0;
			}
		}
		return 0;
	}

	/** Clamps the user value to 0 (straight) .. 1 (the mesh as exported). */
	public static double clampCurve(double curve) {
		return Double.isNaN(curve) ? 1.0 : Math.max(0.0, Math.min(1.0, curve));
	}

	/** New positions of {@code pos} sheared with {@code spine}: {@code z' = z - (1 - curve) * spine(y)}. {@code pos} is not modified. */
	public static float[] straighten(float[] pos, double[] spine, double curve) {
		double k = 1.0 - clampCurve(curve);
		float[] out = pos.clone();
		if (k == 0) {
			return out;
		}
		for (int i = 0; i + 2 < pos.length; i += 3) {
			out[i + 2] = (float) (pos[i + 2] - k * spineAt(spine, pos[i + 1]));
		}
		return out;
	}

	/**
	 * New normals for the sheared surface: the inverse transpose of the shear {@code (y, z) -> (y, z - k s'(y) y)} turns a normal
	 * {@code (nx, ny, nz)} into {@code (nx, ny + k s' nz, nz)}, renormalised. {@code pos} holds the ORIGINAL positions (the heights).
	 */
	public static float[] normals(float[] pos, float[] nrm, double[] spine, double curve) {
		double k = 1.0 - clampCurve(curve);
		float[] out = nrm.clone();
		if (k == 0) {
			return out;
		}
		for (int i = 0; i + 2 < nrm.length; i += 3) {
			double s = slopeAt(spine, pos[i + 1]);
			double nx = nrm[i];
			double ny = nrm[i + 1] + k * s * nrm[i + 2];
			double nz = nrm[i + 2];
			double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (l > 1e-9) {
				out[i] = (float) (nx / l);
				out[i + 1] = (float) (ny / l);
				out[i + 2] = (float) (nz / l);
			}
		}
		return out;
	}

	/** Local z of the apex of the straightened blade at height {@code y}: {@code spine(y) * curve} (the glow tip is the apex at y = blade length). */
	public static double tipZ(double[] spine, double y, double curve) {
		return spineAt(spine, y) * clampCurve(curve);
	}

	/** The largest |z offset of the spine from its base| over the table (the sori of the mesh). */
	public static double sori(double[] spine) {
		double base = spine.length >= 2 ? spine[1] : 0;
		double m = 0;
		for (int k = 1; k < spine.length; k += 2) {
			m = Math.max(m, Math.abs(spine[k] - base));
		}
		return m;
	}
}
