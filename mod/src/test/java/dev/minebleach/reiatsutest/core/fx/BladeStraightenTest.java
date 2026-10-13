package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.obj.ObjMesh;
import dev.minebleach.reiatsutest.core.obj.ObjParser;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** The shear that straightens the Byakuya bankai blades, checked on the shipped OBJ meshes themselves. */
class BladeStraightenTest {
	private static final String DIR = "/assets/reiatsu_test/models/obj/byakuya_bankai/";

	private static ObjMesh load(String object) throws IOException {
		try (InputStream in = BladeStraightenTest.class.getResourceAsStream(DIR + object + ".obj")) {
			return ObjParser.parse(in, object);
		}
	}

	/** Positions and normals per face corner, the layout FxMesh uses (corner order of the faces). */
	private static float[][] corners(ObjMesh m, boolean normals) {
		int n = 0;
		for (ObjMesh.Face f : m.faces) {
			n += f.corners();
		}
		float[] out = new float[n * 3];
		int i = 0;
		for (ObjMesh.Face f : m.faces) {
			for (int c = 0; c < f.corners(); c++) {
				int k = normals ? f.vn()[c] : f.v()[c];
				float[] src = normals ? m.normals : m.positions;
				out[i++] = src[k * 3];
				out[i++] = src[k * 3 + 1];
				out[i++] = src[k * 3 + 2];
			}
		}
		return new float[][] {out};
	}

	@Test
	void theMeasuredSpineIsTheSoriOfTheArtBible() throws IOException {
		double[] sp = BladeStraighten.spine(load("byakuya_bankai_blade").positions);
		assertEquals(0.0, BladeStraighten.spineAt(sp, 0.0), 1e-6, "vertical at the buried base");
		assertEquals(0.0, BladeStraighten.spineAt(sp, 0.5), 1e-6);
		assertEquals(-0.826, BladeStraighten.spineAt(sp, 8.0), 1e-3, "the apex of the export (BankaiBladeRenderer TIP_Z_EXPORT)");
		assertEquals(0.826, BladeStraighten.sori(sp), 1e-3);
		// concentrated in the upper half: less than a fifth of the sori below 4.5 m, and a one way bend
		assertTrue(Math.abs(BladeStraighten.spineAt(sp, 4.5)) < 0.2 * 0.826 + 1e-6, "little bend in the lower half: " + BladeStraighten.spineAt(sp, 4.5));
		for (double y = 0; y < 8.0; y += 0.1) {
			assertTrue(BladeStraighten.spineAt(sp, y + 0.1) <= BladeStraighten.spineAt(sp, y) + 1e-9, "the spine bends one way only, at y " + y);
		}
	}

	@Test
	void curveOneIsTheExportAndCurveZeroPutsTheRingCentresOnTheAxis() throws IOException {
		float[] pos = load("byakuya_bankai_blade").positions;
		double[] sp = BladeStraighten.spine(pos);
		assertArrayEquals(pos, BladeStraighten.straighten(pos, sp, 1.0), 0f);
		float[] flat = BladeStraighten.straighten(pos, sp, 0.0);
		double[] spFlat = BladeStraighten.spine(flat);
		for (int k = 1; k < spFlat.length; k += 2) {
			assertEquals(0.0, spFlat[k], 0.035, "ring " + k / 2 + " centre of the fully straightened blade (the tilted rings of the export leave a few cm)");
		}
	}

	@Test
	void theDefaultCurveRemovesFourFifthsOfTheBendAndKeepsLengthAndWidth() throws IOException {
		ObjMesh m = load("byakuya_bankai_blade");
		double[] sp = BladeStraighten.spine(m.positions);
		double c = 0.2;
		float[] out = BladeStraighten.straighten(m.positions, sp, c);
		assertEquals(m.positions.length, out.length);
		double[] after = BladeStraighten.spine(out);
		assertEquals(BladeStraighten.sori(sp) * c, BladeStraighten.sori(after), 0.02, "the sori is the original times the curve");
		assertEquals(BladeStraighten.tipZ(sp, 8.0, c), BladeStraighten.spineAt(after, 8.0), 1e-5, "tipZ is where the apex ends up");
		assertEquals(-0.826 * c, BladeStraighten.tipZ(sp, 8.0, c), 1e-3);
		float maxY = 0;
		for (int i = 0; i < out.length; i += 3) {
			assertEquals(m.positions[i], out[i], 0f, "x (thickness) untouched");
			assertEquals(m.positions[i + 1], out[i + 1], 0f, "y (length) untouched: the glow height 6.85 and the 8 m stay");
			maxY = Math.max(maxY, out[i + 1]);
		}
		assertEquals(8.0f, maxY, 1e-4f);
		// the width of the slab: every vertex pair of the same ring keeps its spacing to within the ring tilt
		double widthBefore = 0.55;
		double lo = Double.MAX_VALUE;
		double hi = -Double.MAX_VALUE;
		for (int i = 0; i < out.length; i += 3) {
			if (Math.abs(out[i + 1] - 4.5) < 0.1) {
				lo = Math.min(lo, out[i + 2]);
				hi = Math.max(hi, out[i + 2]);
			}
		}
		assertEquals(widthBefore, hi - lo, 0.03, "width at 4.5 m");
	}

	@Test
	void theLodMeshUsesTheSpineOfTheFullMeshAndEndsOnTheSameApex() throws IOException {
		double[] sp = BladeStraighten.spine(load("byakuya_bankai_blade").positions);
		float[] lod = load("byakuya_bankai_blade_lod").positions;
		float[] full = load("byakuya_bankai_blade").positions;
		double c = 0.2;
		float[] a = BladeStraighten.straighten(lod, sp, c);
		float[] b = BladeStraighten.straighten(full, sp, c);
		assertEquals(tipZOf(b), tipZOf(a), 1e-5, "same apex in both meshes");
		assertEquals(-0.826 * c, tipZOf(a), 1e-3);
		// the LOD rings (z 4.5 and 7.1) land where the full mesh rings do
		double[] spLod = BladeStraighten.spine(a);
		double[] spFull = BladeStraighten.spine(b);
		assertEquals(BladeStraighten.spineAt(spFull, 4.5), BladeStraighten.spineAt(spLod, 4.5), 0.01);
		assertEquals(BladeStraighten.spineAt(spFull, 7.1), BladeStraighten.spineAt(spLod, 7.1), 0.02);
	}

	private static double tipZOf(float[] pos) {
		double z = 0;
		float maxY = -1;
		for (int i = 0; i < pos.length; i += 3) {
			if (pos[i + 1] > maxY) {
				maxY = pos[i + 1];
				z = pos[i + 2];
			}
		}
		return z;
	}

	@Test
	void normalsStayUnitAndAreUntouchedAtCurveOne() throws IOException {
		ObjMesh m = load("byakuya_bankai_blade");
		float[] pos = corners(m, false)[0];
		float[] nrm = corners(m, true)[0];
		double[] sp = BladeStraighten.spine(pos);
		assertArrayEquals(nrm, BladeStraighten.normals(pos, nrm, sp, 1.0), 0f);
		float[] n2 = BladeStraighten.normals(pos, nrm, sp, 0.2);
		for (int i = 0; i < n2.length; i += 3) {
			assertEquals(1.0, Math.sqrt(n2[i] * n2[i] + n2[i + 1] * n2[i + 1] + n2[i + 2] * n2[i + 2]), 1e-5);
			assertEquals(nrm[i], n2[i], 0.1, "the shear along z tilts a normal only in its y part");
		}
	}

	@Test
	void shearedNormalsStayPerpendicularToTheShearedFaces() {
		// a face spanned by the directions (1,0,0) and (0,1,s) has normal (0,-s,1)/|..|; after the shear z' = z - k s y it is a plane z' = (1-k) s y
		double s = -0.2;
		double k = 0.8;
		float[] pos = {0, 1, 0, 0, 2, 0, 0, 3, 0};
		double[] sp = {0.0, 0.0, 10.0, 10 * s}; // spine z = s y
		float[] nrm = {0, (float) -s, 1, 0, (float) -s, 1, 0, (float) -s, 1};
		float[] n = BladeStraighten.normals(pos, nrm, sp, 1.0 - k);
		// edge direction of the sheared face along y: (0, 1, (1-k) s)
		double ey = 1;
		double ez = (1 - k) * s;
		for (int i = 0; i < 9; i += 3) {
			assertEquals(0.0, n[i + 1] * ey + n[i + 2] * ez, 1e-6, "normal . edge = 0 after the shear");
		}
	}

	@Test
	void curveIsClamped() {
		assertEquals(0.0, BladeStraighten.clampCurve(-3), 0);
		assertEquals(1.0, BladeStraighten.clampCurve(7), 0);
		assertEquals(1.0, BladeStraighten.clampCurve(Double.NaN), 0);
		assertEquals(0.2, BladeStraighten.clampCurve(0.2), 0);
	}
}
