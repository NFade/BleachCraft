package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.obj.ObjMesh;
import dev.minebleach.reiatsutest.core.obj.ObjParser;
import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The geometry of the Quincy wings, bracers and boots ({@link UryuWingMath}), checked against the exported meshes of design/results/uryu_fx_models.md. */
class UryuWingMathTest {
	private static final double EPS = 1e-9;
	private static final UryuWingMath.Rig REST = UryuWingMath.rig(0, 0, 0, 1.0, 0, 0);

	private static ObjMesh load(String name) throws IOException {
		try (InputStream in = UryuWingMathTest.class.getResourceAsStream("/assets/reiatsu_test/models/obj/uryu_fx/" + name + ".obj")) {
			assertNotNull(in, name);
			return ObjParser.parse(in, name);
		}
	}

	// ------------------------------------------------------------------------------------------ the meshes

	@Test
	void theWingMeshHasSevenSlabsTwoSpikesAndTwoChipsInThisOrder() throws IOException {
		for (String name : new String[] {"uryu_wing_l", "uryu_wing_r"}) {
			ObjMesh m = load(name);
			assertEquals(UryuWingMath.QUADS, m.faceCount(), name);
			int[] sizes = {6, 6, 6, 6, 6, 6, 6, 5, 5, 4, 4};
			int[] verts = {8, 8, 8, 8, 8, 8, 8, 5, 5, 4, 4};
			int at = 0;
			for (int g = 0; g < sizes.length; g++) {
				Set<Integer> u = new HashSet<>();
				for (int q = at; q < at + sizes[g]; q++) {
					assertEquals(g, UryuWingMath.groupOfQuad(q), "quad " + q);
					for (int v : m.faces.get(q).v()) {
						u.add(v);
					}
				}
				assertEquals(verts[g], u.size(), name + " group " + g + " is one closed solid");
				at += sizes[g];
			}
			assertEquals(UryuWingMath.QUADS, at);
		}
		assertEquals(UryuWingMath.GROUPS, UryuWingMath.groupOfQuad(UryuWingMath.QUADS - 1) + 1);
	}

	@Test
	void theRightWingIsTheMirrorOfTheLeftOne() throws IOException {
		ObjMesh l = load("uryu_wing_l");
		ObjMesh r = load("uryu_wing_r");
		assertEquals(l.vertexCount(), r.vertexCount());
		for (int i = 0; i < l.vertexCount(); i++) {
			assertEquals(-l.positions[i * 3], r.positions[i * 3], 1e-5);
			assertEquals(l.positions[i * 3 + 1], r.positions[i * 3 + 1], 1e-5);
			assertEquals(l.positions[i * 3 + 2], r.positions[i * 3 + 2], 1e-5);
		}
		// the left mesh extends +X (that is the one unverified constant: +X is the player's left)
		assertTrue(UryuWingMath.MESH_PLUS_X_IS_LEFT);
		assertEquals("uryu_wing_l", UryuWingMath.meshName(true));
		assertEquals("uryu_wing_r", UryuWingMath.meshName(false));
	}

	@Test
	void theTipsAreTheMiddleOfTheEndEdgeOfTheSlab() throws IOException {
		ObjMesh l = load("uryu_wing_l");
		assertTrue(edgeMiddleDistance(l, UryuWingMath.TIP_UPPER) < 0.002, "the upper spar tip: " + edgeMiddleDistance(l, UryuWingMath.TIP_UPPER));
		assertTrue(edgeMiddleDistance(l, UryuWingMath.TIP_LOWER) < 0.002, "the lowest plate tip: " + edgeMiddleDistance(l, UryuWingMath.TIP_LOWER));
	}

	/** The slab ends in an edge 25 mm thick: the tip empty is the middle of the two nearest vertices. */
	private static double edgeMiddleDistance(ObjMesh m, double[] p) {
		double d1 = 1e9;
		double d2 = 1e9;
		double[] a = null;
		double[] b = null;
		for (int i = 0; i < m.vertexCount(); i++) {
			double[] v = {m.positions[i * 3], m.positions[i * 3 + 1], m.positions[i * 3 + 2]};
			double d = Math.sqrt(Math.pow(v[0] - p[0], 2) + Math.pow(v[1] - p[1], 2) + Math.pow(v[2] - p[2], 2));
			if (d < d1) {
				d2 = d1;
				b = a;
				d1 = d;
				a = v;
			} else if (d < d2) {
				d2 = d;
				b = v;
			}
		}
		double mx = (a[0] + b[0]) / 2;
		double my = (a[1] + b[1]) / 2;
		double mz = (a[2] + b[2]) / 2;
		return Math.sqrt(Math.pow(mx - p[0], 2) + Math.pow(my - p[1], 2) + Math.pow(mz - p[2], 2));
	}

	// ------------------------------------------------------------------------------------------ the rig and the tips

	@Test
	void atRestTheTipsAreExactlyTheWingTipsOfTheBalance() {
		double[] o = new double[3];
		for (int slot = 0; slot < 4; slot++) {
			UryuWingMath.tip(slot, REST, o);
			double[] want = UryuBalance.WING_TIPS[slot];
			assertEquals(want[0], o[0], EPS, "slot " + slot + " sideways");
			assertEquals(want[1], o[1], EPS, "slot " + slot + " height");
			assertEquals(want[2], o[2], EPS, "slot " + slot + " backwards");
		}
	}

	@Test
	void theWingBeatMovesTheTipsAndTheyStayOnTheVisibleWing() {
		double[] rest = new double[3];
		double[] up = new double[3];
		double[] down = new double[3];
		for (int slot = 0; slot < 4; slot++) {
			UryuWingMath.tip(slot, REST, rest);
			UryuWingMath.tip(slot, UryuWingMath.rig(0, 0, 0.25, 1.0, 0, 0), up); // flap +4 degrees
			UryuWingMath.tip(slot, UryuWingMath.rig(0, 0, 0.75, 1.0, 0, 0), down); // flap -4 degrees
			assertTrue(up[1] > rest[1] + 0.05, "slot " + slot + " rises with the beat");
			assertTrue(down[1] < rest[1] - 0.05, "slot " + slot + " falls with the beat");
			assertEquals(Math.signum(rest[0]), Math.signum(up[0]), "the tip stays on its side");
			// the roll is a rotation about the root: the distance to the root does not change
			double dr = Math.hypot(rest[0], rest[1] - UryuWingMath.ROOT_Y);
			double du = Math.hypot(up[0], up[1] - UryuWingMath.ROOT_Y);
			assertEquals(dr, du, 1e-9);
		}
	}

	@Test
	void theMirrorSlotsAreMirrored() {
		double[] a = new double[3];
		double[] b = new double[3];
		for (int pair = 0; pair < 2; pair++) {
			UryuWingMath.tip(pair * 2, UryuWingMath.rig(0.3, 0.4, 0.1, 1.0, 0.2, 0.1), a);
			UryuWingMath.tip(pair * 2 + 1, UryuWingMath.rig(0.3, 0.4, 0.1, 1.0, 0.2, 0.1), b);
			assertEquals(-a[0], b[0], EPS);
			assertEquals(a[1], b[1], EPS);
			assertEquals(a[2], b[2], EPS);
		}
	}

	@Test
	void rollRaisesBothWingsAndPlaceIsExactAtRest() {
		double[] out = new double[3];
		UryuWingMath.place(1, 1.0, 0.0, -0.2, 1.0, 0.0, REST, out);
		assertEquals(1.0, out[0], EPS);
		assertEquals(UryuWingMath.ROOT_Y, out[1], EPS);
		assertEquals(-UryuWingMath.ROOT_BACK - 0.2, out[2], EPS);
		// the right mesh (x negative) lands on the right (negative left coordinate)
		UryuWingMath.place(-1, -1.0, 0.0, -0.2, 1.0, 0.0, REST, out);
		assertEquals(-1.0, out[0], EPS);
		// roll +90 degrees: the left wing tip points straight up, the right one too
		UryuWingMath.place(1, 1.0, 0.0, 0.0, 1.0, 90.0, REST, out);
		assertEquals(0.0, out[0], 1e-9);
		assertEquals(UryuWingMath.ROOT_Y + 1.0, out[1], 1e-9);
		UryuWingMath.place(-1, -1.0, 0.0, 0.0, 1.0, 90.0, REST, out);
		assertEquals(0.0, out[0], 1e-9);
		assertEquals(UryuWingMath.ROOT_Y + 1.0, out[1], 1e-9);
		// a folded plate: shorter and lower
		UryuWingMath.place(1, 1.0, 0.0, 0.0, 0.15, -55.0, REST, out);
		assertEquals(Math.cos(Math.toRadians(55)) * 0.15, out[0], 1e-9);
		assertEquals(UryuWingMath.ROOT_Y - Math.sin(Math.toRadians(55)) * 0.15, out[1], 1e-9);
	}

	@Test
	void theWingScaleScalesAboutTheRoot() {
		double[] a = new double[3];
		double[] b = new double[3];
		UryuWingMath.place(1, 1.7, 1.0, -0.3, 1.0, 0.0, UryuWingMath.rig(0, 0, 0, 1.0, 0, 0), a);
		UryuWingMath.place(1, 1.7, 1.0, -0.3, 1.0, 0.0, UryuWingMath.rig(0, 0, 0, 1.5, 0, 0), b);
		assertEquals(a[0] * 1.5, b[0], EPS);
		assertEquals(UryuWingMath.ROOT_Y + (a[1] - UryuWingMath.ROOT_Y) * 1.5, b[1], EPS);
		assertEquals(-UryuWingMath.ROOT_BACK + (a[2] + UryuWingMath.ROOT_BACK) * 1.5, b[2], EPS);
		// out of range scales are clamped to 0.5 .. 1.5
		assertEquals(1.5, UryuWingMath.clampScale(9.0), EPS);
		assertEquals(0.5, UryuWingMath.clampScale(0.0), EPS);
		assertEquals(1.5, UryuWingMath.rig(0, 0, 0, 7.0, 0, 0).scale(), EPS);
	}

	@Test
	void sneakingDropsTheRootFoldsTheWingsAndLeansThemBack() {
		UryuWingMath.Rig s = UryuWingMath.rig(1, 0, 0, 1.0, 0, 0);
		assertEquals(UryuWingMath.SNEAK_ROOT_Y, s.rootY(), EPS);
		assertEquals(UryuWingMath.SNEAK_ROOT_Z, s.rootZ(), EPS);
		assertEquals(UryuWingMath.SNEAK_FOLD_DEG, s.rollDeg(), EPS);
		double[] rest = new double[3];
		double[] sneak = new double[3];
		UryuWingMath.tip(0, REST, rest);
		UryuWingMath.tip(0, s, sneak);
		assertTrue(sneak[1] < rest[1] - 0.5, "the upper tip is much lower when folded: " + sneak[1] + " vs " + rest[1]);
		// continuity: half sneaking is between
		UryuWingMath.Rig h = UryuWingMath.rig(0.5, 0, 0, 1.0, 0, 0);
		assertTrue(h.rootY() < UryuWingMath.ROOT_Y && h.rootY() > UryuWingMath.SNEAK_ROOT_Y);
		assertEquals(0.5 * Math.toRadians(UryuWingMath.SNEAK_PITCH_DEG), h.pitchRad(), EPS);
	}

	@Test
	void airAndSpreadAndVolleyAddTheirAngles() {
		assertEquals(UryuWingMath.AIR_DEG, UryuWingMath.rig(0, 1, 0, 1.0, 0, 0).rollDeg(), EPS);
		assertEquals(UryuWingMath.SPREAD_DEG, UryuWingMath.rig(0, 0, 0, 1.0, 1, 0).rollDeg(), EPS);
		assertEquals(1.0 * (1 + UryuWingMath.SPREAD_SCALE), UryuWingMath.rig(0, 0, 0, 1.0, 1, 0).scale(), EPS);
		assertEquals(1.0 + UryuWingMath.VOLLEY_SCALE, UryuWingMath.rig(0, 0, 0, 1.0, 0, 1).scale(), EPS);
		assertEquals(UryuWingMath.FLAP_DEG, UryuWingMath.rig(0, 0, 0.25, 1.0, 0, 0).rollDeg(), 1e-9);
		assertEquals(0.25, UryuWingMath.flapHz(0), EPS);
		assertEquals(0.50, UryuWingMath.flapHz(1), EPS);
	}

	@Test
	void approachNeverOvershootsAndConverges() {
		double v = 0;
		for (int i = 0; i < 400; i++) {
			double n = UryuWingMath.approach(v, 1.0, 1.0 / 60.0, 8.0);
			assertTrue(n >= v && n <= 1.0);
			v = n;
		}
		assertEquals(1.0, v, 1e-6);
		assertEquals(0.4, UryuWingMath.approach(0.4, 0.9, 0.0, 8.0), EPS, "no time, no change");
	}

	@Test
	void spreadAndVolleyEnvelopes() {
		assertEquals(0.0, UryuWingMath.spread(5.0, -1, -1), EPS, "no barrage");
		assertEquals(0.0, UryuWingMath.spread(0.9, 1.0, 7.0), EPS, "before the start");
		assertEquals(1.0, UryuWingMath.spread(2.0, 1.0, 7.0), EPS, "open");
		assertEquals(0.0, UryuWingMath.spread(7.5, 1.0, 7.0), EPS, "relaxed 0.4 s after the end");
		double prev = 0;
		for (double t = 1.0; t <= 1.2; t += 0.005) {
			double v = UryuWingMath.spread(t, 1.0, 7.0);
			assertTrue(v >= prev - 1e-12 && v <= 1.0 + 1e-12);
			prev = v;
		}
		assertEquals(0.0, UryuWingMath.volley(-0.1), EPS);
		assertEquals(0.0, UryuWingMath.volley(UryuWingMath.VOLLEY_TIME), EPS);
		double peak = 0;
		for (double t = 0; t < UryuWingMath.VOLLEY_TIME; t += 0.002) {
			peak = Math.max(peak, UryuWingMath.volley(t));
		}
		assertEquals(1.0, peak, 0.02);
	}

	// ------------------------------------------------------------------------------------------ the unfold

	@Test
	void theSparAndTheRootHookStartFirstAndTheLastPlateEndsWithTheUnfold() {
		assertEquals(0.0, UryuWingMath.groupDelay(5), EPS);
		assertEquals(0.0, UryuWingMath.groupDelay(6), EPS);
		double last = 0;
		for (int g = 0; g < UryuWingMath.GROUPS; g++) {
			double d = UryuWingMath.groupDelay(g);
			assertTrue(d >= 0 && d <= UryuWingMath.MAX_DELAY + EPS);
			last = Math.max(last, UryuWingMath.arrival(g));
			if (g != 5 && g != 6) {
				assertTrue(d >= UryuVollstTiming.PLATE_STAGGER - EPS, "the others follow the spar by at least one stagger");
			}
		}
		assertEquals(UryuVollstTiming.T_UNFOLD + UryuVollstTiming.UNFOLD_TIME, last, EPS);
		// the plates in a row: parallel plate and step, then the lower plates, staggered by 0.04
		assertEquals(0.04, UryuWingMath.groupDelay(3), EPS);
		assertEquals(0.08, UryuWingMath.groupDelay(2), EPS);
		assertEquals(0.12, UryuWingMath.groupDelay(1), EPS);
		assertEquals(0.16, UryuWingMath.groupDelay(0), EPS);
	}

	@Test
	void aPlateIsFoldedHiddenThenUnfoldsWithASmallOvershootAndStaysPut() {
		double[] p = new double[4];
		for (int g = 0; g < UryuWingMath.GROUPS; g++) {
			UryuWingMath.plate(0.0, g, p);
			assertEquals(UryuVollstTiming.FOLD_LENGTH, p[0], EPS);
			assertEquals(UryuVollstTiming.FOLD_ANGLE_DEG, p[1], EPS);
			assertEquals(0.0, p[2], EPS, "invisible before the unfold");
			assertEquals(0.0, p[3], EPS);
			UryuWingMath.plate(UryuVollstTiming.T_UNFOLD + 1.0, g, p);
			assertEquals(1.0, p[0], EPS);
			assertEquals(0.0, p[1], EPS);
			assertEquals(1.0, p[2], EPS);
			assertEquals(0.0, p[3], EPS, "the flash is over");
			double maxK = 0;
			double minRoll = 0;
			for (double t = UryuVollstTiming.T_UNFOLD; t <= 2.0; t += 0.002) {
				UryuWingMath.plate(t, g, p);
				maxK = Math.max(maxK, p[0]);
				assertTrue(p[2] >= 0 && p[2] <= 1.0 + EPS);
				assertTrue(p[3] >= 0 && p[3] <= 1.0 + EPS);
				minRoll = Math.min(minRoll, p[1]);
				if (t > UryuWingMath.arrival(g) + 0.2) {
					assertEquals(1.0, p[0], 1e-9, "stays put");
				}
			}
			assertTrue(maxK > 1.005 && maxK < 1.06, "a small overshoot: " + maxK);
			assertTrue(minRoll >= UryuVollstTiming.FOLD_ANGLE_DEG - 1e-9);
		}
	}

	@Test
	void everyPlateFlashesOnceAroundItsArrival() {
		double[] p = new double[4];
		for (int g = 0; g < UryuWingMath.GROUPS; g++) {
			double a = UryuWingMath.arrival(g);
			UryuWingMath.plate(a, g, p);
			assertEquals(1.0, p[3], EPS, "full flash at the arrival");
			UryuWingMath.plate(a - UryuWingMath.FLASH_BEFORE - 0.001, g, p);
			assertEquals(0.0, p[3], EPS);
			UryuWingMath.plate(a + UryuWingMath.FLASH_AFTER + 0.001, g, p);
			assertEquals(0.0, p[3], EPS);
			double sum = 0;
			for (double t = 0; t < 2.5; t += 0.001) {
				UryuWingMath.plate(t, g, p);
				sum += p[3] > 0 ? 0.001 : 0.0;
			}
			assertTrue(sum > 0.11 && sum < 0.14, "one window of about 0.13 s: " + sum);
		}
	}

	@Test
	void theFoldedWingLiesAgainstTheBackAndTheUnfoldedOneIsTheMesh() throws IOException {
		ObjMesh l = load("uryu_wing_l");
		double[] out = new double[3];
		double[] p = new double[4];
		double maxFolded = 0;
		double maxOpen = 0;
		for (int q = 0; q < l.faceCount(); q++) {
			int g = UryuWingMath.groupOfQuad(q);
			for (int v : l.faces.get(q).v()) {
				double x = l.positions[v * 3];
				double y = l.positions[v * 3 + 1];
				double z = l.positions[v * 3 + 2];
				UryuWingMath.plate(UryuVollstTiming.T_UNFOLD, g, p);
				UryuWingMath.place(1, x, y, z, p[0], p[1], REST, out);
				maxFolded = Math.max(maxFolded, Math.abs(out[0]));
				UryuWingMath.plate(5.0, g, p);
				UryuWingMath.place(1, x, y, z, p[0], p[1], REST, out);
				maxOpen = Math.max(maxOpen, Math.abs(out[0]));
			}
		}
		assertTrue(maxFolded < 0.30, "folded: the wing is a short stub at the shoulder: " + maxFolded);
		assertEquals(1.7045, maxOpen, 0.001, "unfolded: the mesh width");
	}

	// ------------------------------------------------------------------------------------------ bands

	@Test
	void theBandsReachBeyondTheLimbWithAndWithoutArmor() {
		double apothem = Math.cos(Math.toRadians(15.0));
		// the 12 sided ring of the bracer and the boot (circumradius 0.135 at the narrow end), scaled
		assertTrue(UryuWingMath.BRACER_RADIUS * UryuWingMath.BRACER_RADIAL * apothem > UryuWingMath.limbHalfDiagonal(4, 0.25), "bracer over the bare arm");
		assertTrue(UryuWingMath.BRACER_RADIUS * UryuWingMath.BRACER_RADIAL_ARMORED * apothem > UryuWingMath.limbHalfDiagonal(4, 1.25), "bracer over a chestplate");
		assertTrue(UryuWingMath.BOOT_RADIUS * UryuWingMath.BOOT_RADIAL * apothem > UryuWingMath.limbHalfDiagonal(4, 0.25), "boot over the bare leg");
		assertTrue(UryuWingMath.BOOT_RADIUS * UryuWingMath.BOOT_RADIAL_ARMORED * apothem > UryuWingMath.limbHalfDiagonal(4, 1.25), "boot over leggings");
		// the slim arm is narrower: the same band reaches it too
		assertTrue(UryuWingMath.limbHalfDiagonal(3, 0.25) < UryuWingMath.limbHalfDiagonal(4, 0.25));
		// not absurdly wide either (a band, not a barrel)
		assertTrue(UryuWingMath.BRACER_RADIUS * UryuWingMath.BRACER_RADIAL < 0.22);
	}

	@Test
	void theBandSitsOnTheForearmAndTheArmCentresAreTheVanillaOnes() {
		assertEquals(-1.0, UryuWingMath.armCentrePx(false, false), EPS);
		assertEquals(1.0, UryuWingMath.armCentrePx(false, true), EPS);
		assertEquals(-0.5, UryuWingMath.armCentrePx(true, false), EPS);
		assertEquals(0.5, UryuWingMath.armCentrePx(true, true), EPS);
		// the arm cuboid spans -2 .. 10 px below the pivot: 7 px is the forearm (the wrist is at 10)
		assertTrue(UryuWingMath.BRACER_Y_PX > 4.0 && UryuWingMath.BRACER_Y_PX < 10.0 - 1.7, "the band stays on the forearm");
		assertEquals(12.0, UryuWingMath.BOOT_Y_PX, EPS, "the sole is 12 px below the leg pivot");
	}

	// ------------------------------------------------------------------------------------------ first person

	/** Body frame point (x left, y up, z forward) to the world for a rig yawed by {@code yaw} (Minecraft convention) and pushed back {@code back}. */
	private static double[] world(double[] b, double yaw, double back) {
		double z = b[2] - back;
		return new double[] {b[0] * Math.cos(yaw) - z * Math.sin(yaw), b[1], b[0] * Math.sin(yaw) + z * Math.cos(yaw)};
	}

	private static List<double[]> wingPoints() throws IOException {
		List<double[]> pts = new ArrayList<>();
		double[] out = new double[3];
		for (String name : new String[] {"uryu_wing_l", "uryu_wing_r"}) {
			ObjMesh m = load(name);
			int side = name.endsWith("_l") ? 1 : -1;
			for (int i = 0; i < m.vertexCount(); i++) {
				UryuWingMath.place(side, m.positions[i * 3], m.positions[i * 3 + 1], m.positions[i * 3 + 2], 1.0, 0.0, REST, out);
				pts.add(out.clone());
			}
		}
		return pts;
	}

	private static int visible(List<double[]> pts, double rigYaw, double back, double coneDeg, double minFade) {
		double eyeY = 1.62;
		double[] fwd = new double[3];
		HakuteikenFirstPerson.forward(0, 0, fwd);
		int n = 0;
		for (double[] p : pts) {
			double[] w = world(p, rigYaw, back);
			double depth = HakuteikenFirstPerson.depth(w[0], w[1], w[2], 0, eyeY, 0, fwd);
			if (depth <= 0.05) {
				continue;
			}
			double fade = HakuteikenFirstPerson.fade(depth, HakuteikenFirstPerson.CLEAR, HakuteikenFirstPerson.FULL);
			double ang = Math.toDegrees(Math.atan2(Math.hypot(w[0], w[1] - eyeY), depth));
			if (ang < coneDeg && fade >= minFade) {
				n++;
			}
		}
		return n;
	}

	@Test
	void theWingsEnterTheViewWhenTheBodyTrailsTheHeadAndTheGuardKeepsThemOut() throws IOException {
		List<double[]> pts = wingPoints();
		assertEquals(0, visible(pts, 0.0, 0.0, 55, 0.0), "aligned: the wings stay behind the eye");
		assertTrue(visible(pts, Math.toRadians(75), 0.0, 55, 0.0) > 10, "75 degrees (the vanilla idle limit): a wing is in view");
		for (double offDeg = -180; offDeg <= 180; offDeg += 5) {
			double rig = UryuWingMath.guardYaw(Math.toRadians(offDeg), 0.0, 15.0, false);
			assertEquals(0, visible(pts, rig, HakuteikenFirstPerson.BACK, 55, 0.02), "body offset " + offDeg + " leaves wing points in the view");
			double gentle = UryuWingMath.guardYaw(Math.toRadians(offDeg), 0.0, 15.0, true);
			assertTrue(Math.abs(HakuteikenFirstPerson.wrapPi(gentle)) <= Math.toRadians(7.5) + 1e-12, "Reduce Motion halves the limit");
		}
		assertFalse(pts.isEmpty());
	}

	@Test
	void barrageCrossRisesHoldsAndFades() {
		assertEquals(0.0, UryuWingMath.crossLevel(-0.1, 6.0), 1e-12);
		assertEquals(0.0, UryuWingMath.crossLevel(0.0, 6.0), 1e-12);
		assertEquals(UryuWingMath.CROSS_ALPHA, UryuWingMath.crossLevel(3.0, 6.0), 1e-12);
		assertEquals(UryuWingMath.CROSS_ALPHA, UryuWingMath.crossLevel(6.0, 6.0), 1e-12);
		assertTrue(UryuWingMath.crossLevel(6.2, 6.0) < UryuWingMath.CROSS_ALPHA && UryuWingMath.crossLevel(6.2, 6.0) > 0.0);
		assertEquals(0.0, UryuWingMath.crossLevel(6.0 + UryuWingMath.CROSS_FADE + 0.01, 6.0), 1e-12);
	}

	@Test
	void barrageCrossTurnsOneTenthRevPerSecondAndStopsUnderReduceMotion() {
		assertEquals(2.0 * Math.PI * 0.1 * 5.0, UryuWingMath.crossAngle(5.0, false), 1e-9);
		assertEquals(0.0, UryuWingMath.crossAngle(5.0, true), 0.0);
	}
}
