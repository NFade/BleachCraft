package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.obj.DrawRig;
import org.junit.jupiter.api.Test;

/**
 * tp_pose: the maths of the third person pose. The chain of the held item is rebuilt here independently of the solver, with the
 * real left hand semantics of vanilla ({@code HeldItemFeatureRenderer}, {@code ModelPart#rotate}, {@code Transformation#apply}),
 * so the checks are not circular. R11 T10: the tests run at the sword scales 1.0, 1.6 and 2.0 (display scale 1.6 times the factor
 * {@code tp.swordScale} 0.625, 1.0 and 1.25; the shipped default is 1.25).
 */
class TpPoseTest {
	private static final double EPS = 1e-6;
	/** Numbers of the Rukia sealed model: sori arc centre, radius, clear travel, stow identity, slide end 0.55. */
	private static final DrawRig RIG = new DrawRig(0.566f, -2.5276f, 3.0276f, 0.7514f, DrawRig.Rigid.IDENTITY, 0.55f, 0.55f, 0.15f);
	private static final TpPose.Hip HIP = new TpPose.Hip(new double[] {3.0, 9.0, -2.5}, new double[] {0.35, 0.30, 0.89}, 1.6);
	private static final TpPose.Display DISPLAY = new TpPose.Display(new double[] {45, 180, 0}, new double[] {0, -2 / 16.0, 1.75 / 16}, 1.6);
	/** The factors tp.swordScale that give the effective scales 1.0, 1.6 and 2.0. */
	private static final double[] KS = {1.0 / 1.6, 1.0, 2.0 / 1.6};
	/** The shipped default of tp.swordScale. */
	private static final double KS_DEFAULT = 1.25;

	private static TpPose.Result solve(double p, double b, boolean left, boolean thin) {
		return solve(p, b, left, thin, 1.0);
	}

	private static TpPose.Result solve(double p, double b, boolean left, boolean thin, double ks) {
		return TpPose.solve(new TpPose.Input(p, b, left, thin, HIP, DISPLAY, RIG, ks), TpPose.Params.DEFAULTS);
	}

	/** Item model to model space chain of the hand item for the sword hand, the way vanilla builds it (mirrored for a left hander). */
	private static TpMat4 handChain(TpPose.Result r, boolean left, boolean thin) {
		double side = left ? -1 : 1;
		TpPose.Arm a = r.main();
		double px = -5 * side + a.dx() + (thin ? 0.5 * side : 0);
		double py = (thin ? 2.5 : 2.0) + a.dy();
		double pz = a.dz();
		TpMat4 arm = TpMat4.translation(px / 16, py / 16, pz / 16).mul(TpMat4.rotZYX(a.roll(), a.yaw(), a.pitch()));
		TpMat4 turn = TpMat4.rotX(-Math.PI / 2).mul(TpMat4.rotY(Math.PI)).mul(TpMat4.translation(side / 16, 0.125, -0.625));
		// the item correction G: translation, rotation and (R11 T10) the uniform scale of tp.swordScale
		TpMat4 g = TpMat4.translation(r.gT()[0], r.gT()[1], r.gT()[2]).mul(TpMat4.fromQuat(r.gQ())).mul(TpMat4.scale(r.gS()));
		double[] t = DISPLAY.transBlocks();
		double[] d = DISPLAY.rotDeg();
		TpMat4 disp = TpMat4.translation(side * t[0], t[1], t[2])
				.mul(TpMat4.rotXYZ(Math.toRadians(d[0]), Math.toRadians(side * d[1]), Math.toRadians(side * d[2]))).mul(TpMat4.scale(DISPLAY.scale()));
		return arm.mul(turn).mul(g).mul(disp).mul(TpMat4.translation(-0.5, -0.5, -0.5));
	}

	private static void assertSameMatrix(TpMat4 a, TpMat4 b, String what) {
		double[] pa = {0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1};
		for (int i = 0; i < 4; i++) {
			double[] x = a.point(pa[i * 3], pa[i * 3 + 1], pa[i * 3 + 2]);
			double[] y = b.point(pa[i * 3], pa[i * 3 + 1], pa[i * 3 + 2]);
			for (int k = 0; k < 3; k++) {
				assertEquals(x[k], y[k], 1e-7, what + " point " + i + " axis " + k);
			}
		}
	}

	/** The correction G puts the held sword exactly on the frame of the solver, for both hands, both arm models, held and blocking, at every sword scale. */
	@Test
	void heldSwordLandsOnItsFrame() {
		for (double ks : KS) {
			for (boolean left : new boolean[] {false, true}) {
				for (boolean thin : new boolean[] {false, true}) {
					for (double b : new double[] {0, 0.3, 1}) {
						TpPose.Result r = solve(1, b, left, thin, ks);
						assertSameMatrix(handChain(r, left, thin), r.sword().matrix(), "ks=" + ks + " left=" + left + " thin=" + thin + " b=" + b);
						assertEquals(ks, r.gS(), 1e-9, "the item correction carries the sword scale factor");
					}
				}
			}
		}
	}

	/** Hand centre of an arm: pivot plus the rotated centre of the lower 4 px of the arm cube. */
	private static double[] handCentre(TpPose.Arm a, double pivotX, boolean thin, boolean rightPart) {
		double hx = (rightPart ? -1 : 1) * (thin ? 0.5 : 1.0);
		TpMat4 rot = TpMat4.rotZYX(a.roll(), a.yaw(), a.pitch());
		double[] h = rot.dir(hx, 8, 0);
		return new double[] {pivotX + a.dx() + h[0], (thin ? 2.5 : 2.0) + a.dy() + h[1], a.dz() + h[2]};
	}

	/** Both hands are on the sword axis, in the held pose and in the block, for a right and a left hander, at every sword scale. */
	@Test
	void handsReachTheirPointsOnTheSword() {
		for (double ks : KS) {
			for (boolean left : new boolean[] {false, true}) {
				for (boolean thin : new boolean[] {false, true}) {
					for (double b : new double[] {0, 1}) {
						TpPose.Result r = solve(1, b, left, thin, ks);
						double mainPivot = left ? 5 : -5;
						double[] hm = handCentre(r.main(), mainPivot, thin, !left);
						double[] ho = handCentre(r.off(), -mainPivot, thin, left);
						for (int k = 0; k < 3; k++) {
							assertEquals(r.handTargetMain()[k], hm[k], 1e-6, "main hand " + k + " ks=" + ks + " left=" + left + " b=" + b);
							assertEquals(r.handTargetOff()[k], ho[k], 1e-6, "off hand " + k + " ks=" + ks + " left=" + left + " b=" + b);
						}
					}
				}
			}
		}
	}

	/** A left hander is the mirror image of a right hander. */
	@Test
	void leftHanderIsTheMirrorImage() {
		for (double ks : new double[] {1.0, KS_DEFAULT}) {
			for (double p : new double[] {0, 0.3, 0.7, 1}) {
				for (double b : new double[] {0, 1}) {
					TpPose.Result r = solve(p, b, false, false, ks);
					TpPose.Result l = solve(p, b, true, false, ks);
					assertEquals(r.main().pitch(), l.main().pitch(), EPS);
					assertEquals(r.main().yaw(), -l.main().yaw(), EPS);
					assertEquals(r.main().roll(), -l.main().roll(), EPS);
					assertEquals(r.main().dx(), -l.main().dx(), EPS);
					assertEquals(r.off().roll(), -l.off().roll(), EPS);
					assertEquals(r.gS(), l.gS(), EPS);
					double[] gr = r.sword().matrix().point(0.5, 0.5, 0.5);
					double[] gl = l.sword().matrix().point(0.5, 0.5, 0.5);
					assertEquals(gr[0], -gl[0], EPS);
					assertEquals(gr[1], gl[1], EPS);
					assertEquals(gr[2], gl[2], EPS);
					double[] sr = r.saya().matrix().point(0.5, 0.5, 0.5);
					double[] sl = l.saya().matrix().point(0.5, 0.5, 0.5);
					assertEquals(sr[0], -sl[0], EPS);
					// the blade axis is mirrored too
					double[] ar = r.sword().matrix().dir(0, 1, 0);
					double[] al = l.sword().matrix().dir(0, 1, 0);
					assertEquals(ar[0], -al[0], EPS);
					assertEquals(ar[1], al[1], EPS);
					assertEquals(ar[2], al[2], EPS);
				}
			}
		}
	}

	private static double dist(double[] a, double[] b) {
		return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
	}

	/**
	 * SEALED (round3, same as in first person): scabbard and sword at the hip frame, the right hand grips the hilt, the other hand
	 * holds the scabbard just in front of the mouth. Both hands reach their points, the shoulders stay close to the body. At every sword scale.
	 */
	@Test
	void sealedSitsOnTheHipWithTheRightHandOnTheHiltAndTheLeftOnTheScabbard() {
		for (double ks : KS) {
			double s = 1.6 * ks;
			for (boolean thin : new boolean[] {false, true}) {
				TpPose.Result r = solve(0, 0, false, thin, ks);
				assertEquals(1.0, r.wMain(), EPS, "the sword arm is posed on the hilt");
				assertEquals(1.0, r.wOff(), EPS, "the other hand holds the scabbard");
				assertSameMatrix(r.saya().matrix(), TpPose.hipFrame(HIP, ks), "saya at the hip");
				assertSameMatrix(r.sword().matrix(), TpPose.hipFrame(HIP, ks), "sword in the saya");
				// the scabbard hangs at the left hip of a right hander (positive x) with the hilt in front of the belly
				double[] grip = r.sword().matrix().point(0.5, 0.5, 0.5);
				assertTrue(grip[0] * 16 > 1 && grip[0] * 16 < 6, "grip x " + grip[0] * 16 + " ks=" + ks);
				// the right fist is on the hilt: its target is within a px of the point of the hilt under the tsuba
				TpMat4 f = r.sword().matrix();
				double[] hilt = TpPose.handPoint(f, 0, TpPose.fistR(s));
				assertTrue(dist(hilt, r.handTargetMain()) < 0.5, "right hand on the hilt " + dist(hilt, r.handTargetMain()) + " ks=" + ks);
				double[] tsuba = f.point(0.5, 0.5 + TpPose.TSUBA_PX / 25.6, 0.5);
				assertTrue(dist(new double[] {tsuba[0] * 16, tsuba[1] * 16, tsuba[2] * 16}, r.handTargetMain()) < 3.5 * ks, "within the hilt length of the tsuba, ks=" + ks);
				// the left fist is on the scabbard, further along the blade than the right one
				TpMat4 sy = r.saya().matrix();
				double[] onSaya = TpPose.handPoint(sy, 0, 4.5 * TpPose.scaleRatio(s));
				assertTrue(dist(onSaya, r.handTargetOff()) < 0.5, "left hand on the scabbard " + dist(onSaya, r.handTargetOff()) + " ks=" + ks);
				double[] along = sy.dir(0, 1, 0);
				double d = along[0] * (r.handTargetOff()[0] - r.handTargetMain()[0]) + along[1] * (r.handTargetOff()[1] - r.handTargetMain()[1])
						+ along[2] * (r.handTargetOff()[2] - r.handTargetMain()[2]);
				assertTrue(d > 0, "the left hand holds the scabbard in front of the right hand, towards the tip, ks=" + ks);
				// both arms really are posed away from the vanilla hang: the right arm crosses the belly towards the left hip, the left one stays at the side
				assertTrue(r.main().roll() < -0.3, "right arm swung across the body " + r.main().roll());
				for (TpPose.Arm a : new TpPose.Arm[] {r.main(), r.off()}) {
					double sh = Math.sqrt(a.dx() * a.dx() + a.dy() * a.dy() + a.dz() * a.dz());
					assertTrue(sh < 3.6, "shoulder shift " + sh + " px sealed, ks=" + ks);
				}
			}
		}
	}

	/** Held: blade upright, the right fist just under the tsuba with a small gap, the pommel shows below the left fist. At every sword scale. */
	@Test
	void heldSwordIsUprightAndGrippedUnderTheTsuba() {
		for (double ks : KS) {
			double s = 1.6 * ks;
			TpPose.Result r = solve(1, 0, false, false, ks);
			TpMat4 f = r.sword().matrix();
			double[] up = f.dir(0, 1, 0);
			double n = Math.sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2]);
			assertTrue(-up[1] / n > 0.9, "the blade points up, y component " + up[1] / n);
			assertTrue(Math.abs(up[0] / n) < 0.05, "no sideways lean");
			// both fists are on the hilt, the left one lower; the places scale with the sword
			double cR = TpPose.fistR(s);
			double cL = TpPose.fistL(s);
			assertTrue(cR < TpPose.tsubaPx(s) && cR > TpPose.pommelPx(s), "the right fist is on the hilt, s=" + s);
			assertTrue(cL < cR && cL > TpPose.pommelPx(s), "the left fist is lower on the hilt, s=" + s);
			assertEquals(TpPose.C_R * s / 1.6, cR, 1e-12);
			assertEquals(TpPose.C_L * s / 1.6, cL, 1e-12);
			if (s >= 1.6) {
				// a fist is 4 px: only a sword of at least the reference size has room for the gap under the tsuba and the pommel below the lower fist
				double gap = TpPose.tsubaPx(s) - (cR + 2);
				assertTrue(gap > 0.2 && gap < 1.2, "gap between the right fist and the tsuba " + gap + " at scale " + s);
				double lowerFistBottom = cL - 2;
				assertTrue(lowerFistBottom > TpPose.pommelPx(s) + 0.2, "the end of the hilt shows below the lower fist " + (lowerFistBottom - TpPose.pommelPx(s)));
			}
			// the sword is in front of the body (z < -2) and at belly height, not above the head
			double[] g = f.point(0.5, 0.5, 0.5);
			assertTrue(g[2] * 16 < -3.5, "in front of the torso " + g[2] * 16);
			assertTrue(g[1] * 16 > 3 && g[1] * 16 < 10, "grip height " + g[1] * 16);
		}
	}

	/** Length (px) of the sword (pommel to tip) that lies inside the torso rectangle of the front view and in front of it. */
	private static double bladeOverTorso(TpPose.Result r) {
		TpMat4 f = r.sword().matrix();
		double[] a = f.point(0.5, 0.31, 0.5);
		double[] c = f.point(0.5, 1.29, 0.5);
		int n = 400;
		double len = 0;
		for (int i = 0; i < n; i++) {
			double u = (i + 0.5) / n;
			double x = (a[0] + (c[0] - a[0]) * u) * 16;
			double y = (a[1] + (c[1] - a[1]) * u) * 16;
			double z = (a[2] + (c[2] - a[2]) * u) * 16;
			if (Math.abs(x) <= 4 && y >= 0 && y <= 12 && z < -2) {
				len += dist(a, c) * 16 / n;
			}
		}
		return len;
	}

	private static double[] unit(double[] v) {
		double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		return new double[] {v[0] / n, v[1] / n, v[2] / n};
	}

	/**
	 * Block (the user's own pose, 2026-10-13; R11 T1: the cutting edge FORWARD): the sword is carried diagonally in front of the chest in the frontal plane,
	 * hilt low right of centre, blade up and to the LEFT (59 degrees up, 86 degrees left of straight ahead), the cutting edge facing forward. Both states
	 * (drawn, sealed) alike, at every sword scale. The numbers are those of the user's sword prop in Blockbench (tip direction in body px
	 * (0.50843, -0.86031, -0.03685), tsuba point (3.9, 18.1, -6.1) in Blockbench px), checked here independently of the constants of TpPose. The edge is
	 * square to the blade, so it is the forward direction (0, 0, -1) with its component along the blade removed (the blade has z = -0.037): 2.1 degrees
	 * off straight ahead, its z is -0.9993 (within 1e-3 of -1). The sword is shifted by (1.45, -0.35, 0.55) px (R11 T10) and grows about the tsuba point.
	 */
	@Test
	void blockCarriesTheSwordDiagonallyAcrossTheChestWithTheTipUpAndLeftAndTheEdgeForward() {
		double[] wantTip = {0.50843, -0.86031, -0.03685};
		double dotF = -wantTip[2]; // (0, 0, -1) . tip
		double[] wantEdge = unit(new double[] {-dotF * wantTip[0], -dotF * wantTip[1], -1 - dotF * wantTip[2]});
		for (double ks : KS) {
			// the sword prop of the user: tsuba point (3.9, 18.1, -6.1) in Blockbench px = body (-3.9, 5.9, -6.1); the grip centre is the tsuba distance behind it, then the shift
			double[] grip = new double[3];
			double[] shift = {1.45, -0.35, 0.55};
			for (int k = 0; k < 3; k++) {
				grip[k] = new double[] {-3.9, 5.9, -6.1}[k] - TpPose.TSUBA_PX * ks * wantTip[k] + shift[k];
			}
			for (double p : new double[] {0, 1}) {
				for (boolean left : new boolean[] {false, true}) {
					TpPose.Result r = solve(p, 1, left, false, ks);
					double[] tip = r.sword().matrix().dir(0, 1, 0);
					double[] edge = r.sword().matrix().dir(0, 0, 1);
					double nt = Math.sqrt(tip[0] * tip[0] + tip[1] * tip[1] + tip[2] * tip[2]);
					double ne = Math.sqrt(edge[0] * edge[0] + edge[1] * edge[1] + edge[2] * edge[2]);
					double[] g = r.sword().matrix().point(0.5, 0.5, 0.5);
					for (int k = 0; k < 3; k++) {
						double mirror = left && k == 0 ? -1 : 1;
						assertEquals(mirror * wantTip[k], tip[k] / nt, 2e-4, "tip axis " + k + " p=" + p + " left=" + left + " ks=" + ks);
						assertEquals(mirror * wantEdge[k], edge[k] / ne, 1e-3, "edge axis " + k + " p=" + p + " left=" + left + " ks=" + ks);
						assertEquals(mirror * grip[k], g[k] * 16, 5e-3, "grip centre axis " + k + " p=" + p + " left=" + left + " ks=" + ks);
					}
					// the edge looks forward: z within 1e-3 of -1, the sideways parts are the 2.1 degrees of the blade's own slant
					assertEquals(-1.0, edge[2] / ne, 1e-3, "the cutting edge faces forward (z) p=" + p + " left=" + left + " ks=" + ks);
					assertTrue(Math.abs(edge[0] / ne) < 0.02 && Math.abs(edge[1] / ne) < 0.035, "edge x, y small " + edge[0] / ne + " " + edge[1] / ne);
				}
				assertEquals(1.0, solve(p, 1, false, false, ks).wMain(), EPS, "the sword arm is fully posed in a block");
			}
			TpPose.Result sealed = solve(0, 1, false, false, ks);
			assertSameMatrix(sealed.saya().matrix(), sealed.sword().matrix(), "the scabbard travels with the sword in a sealed block");
			TpPose.Result drawn = solve(1, 1, false, false, ks);
			assertSameMatrix(drawn.saya().matrix(), TpPose.hipFrame(HIP, ks), "a drawn sword leaves the scabbard on the hip while blocking");
		}
	}

	private static void assertArm(TpPose.Arm got, double[] want, double sign, String what) {
		assertEquals(Math.toRadians(want[0]), got.pitch(), 1e-9, what + " pitch");
		assertEquals(sign * Math.toRadians(want[1]), got.yaw(), 1e-9, what + " yaw");
		assertEquals(sign * Math.toRadians(want[2]), got.roll(), 1e-9, what + " roll");
		assertEquals(sign * want[3], got.dx(), 1e-9, what + " dx");
		assertEquals(want[4], got.dy(), 1e-9, what + " dy");
		assertEquals(want[5], got.dz(), 1e-9, what + " dz");
	}

	/**
	 * The arms of the block are the arms the user posed in Blockbench (right arm rx 43.9, ry 15.8, rz 3.8; left arm rx 40.0, ry -51.6, rz 16.5):
	 * game values pitch = -rx, yaw = -ry, roll = +rz, shoulder offsets dx = -x_bb, dy = -y_bb, dz = z_bb. Fully blended in at b = 1, plain IK at b = 0.
	 * Not changed by R11 (the sword moves to the hands, not the hands to the sword).
	 */
	@Test
	void blockArmsAreTheOnesTheUserPosed() {
		double[] userR = {-43.9005, -15.798, 3.8136, 0.048, -0.003, 0.649};
		double[] userL = {-40.034, 51.6452, 16.4722, -1.09, 0.0, -0.7};
		for (boolean thin : new boolean[] {false, true}) {
			for (double p : new double[] {0, 1}) {
				TpPose.Result r = solve(p, 1, false, thin, KS_DEFAULT);
				assertArm(r.main(), userR, 1, "right hander sword arm thin=" + thin + " p=" + p);
				assertArm(r.off(), userL, 1, "right hander other arm thin=" + thin + " p=" + p);
				TpPose.Result l = solve(p, 1, true, thin, KS_DEFAULT);
				// a left hander is the mirror image: pitch kept, yaw, roll and the x shift negated
				assertArm(l.main(), userR, -1, "left hander sword arm thin=" + thin + " p=" + p);
				assertArm(l.off(), userL, -1, "left hander other arm thin=" + thin + " p=" + p);
			}
			// the weight is eased and the arms really are a blend in between, not a jump
			TpPose.Arm none = solve(1, 0, false, thin).main();
			TpPose.Arm half = solve(1, 0.5, false, thin).main();
			TpPose.Arm full = solve(1, 1, false, thin).main();
			assertTrue(Math.abs(half.yaw() - none.yaw()) > 0.01 && Math.abs(half.yaw() - full.yaw()) > 0.01, "the half block is in between");
		}
		// the hand centres are the targets reported by the solver (so finishPass and the layer agree)
		TpPose.Result r = solve(1, 1, false, false, KS_DEFAULT);
		double[] hm = handCentre(r.main(), -5, false, true);
		double[] ho = handCentre(r.off(), 5, false, false);
		for (int k = 0; k < 3; k++) {
			assertEquals(r.handTargetMain()[k], hm[k], 1e-6, "main hand " + k);
			assertEquals(r.handTargetOff()[k], ho[k], 1e-6, "off hand " + k);
		}
	}

	/** Position of a point along the blade of a sword frame (px from the grip centre) and its distance from the blade axis. */
	private static double[] alongAndOff(TpPose.Result r, double[] hand) {
		double[] g = r.sword().matrix().point(0.5, 0.5, 0.5);
		double[] dir = unit(r.sword().matrix().dir(0, 1, 0));
		double[] d = {hand[0] - g[0] * 16, hand[1] - g[1] * 16, hand[2] - g[2] * 16};
		double along = d[0] * dir[0] + d[1] * dir[1] + d[2] * dir[2];
		double off = Math.sqrt(Math.max(0, d[0] * d[0] + d[1] * d[1] + d[2] * d[2] - along * along));
		return new double[] {along, off};
	}

	/**
	 * R11 T10, the criterion for the shift of the sword in the block: BOTH fists lie on the hilt, between the pommel and the tsuba (0.4 of the scale
	 * ratio inside both ends), 1.5 k to 4 k px apart along the blade and at most 2.5 px off its axis (k = scale / 1.6). Wide and slim arms, right and left
	 * hander, drawn and sealed, at the default sword scale (display 1.6 times 1.25) and at 1.6 times 1.2 / 1.3 (the shift is tuned for 2.0).
	 */
	@Test
	void blockPutsBothFistsOnTheHilt() {
		for (double ks : new double[] {1.2, KS_DEFAULT, 1.3}) {
			double s = 1.6 * ks;
			double k = ks;
			for (boolean left : new boolean[] {false, true}) {
				for (boolean thin : new boolean[] {false, true}) {
					for (double p : new double[] {0, 1}) {
						TpPose.Result r = solve(p, 1, left, thin, ks);
						double[] rm = alongAndOff(r, r.handTargetMain());
						double[] lo = alongAndOff(r, r.handTargetOff());
						String what = " ks=" + ks + " left=" + left + " thin=" + thin + " p=" + p;
						for (double[] f : new double[][] {rm, lo}) {
							assertTrue(f[0] > TpPose.pommelPx(s) && f[0] < TpPose.tsubaPx(s), "the fist is between the pommel and the tsuba: " + f[0] + what);
							assertTrue(f[1] <= 2.5, "the fist is at most 2.5 px off the blade axis: " + f[1] + what);
						}
						double gap = Math.abs(lo[0] - rm[0]);
						assertTrue(gap >= 1.5 * k && gap <= 4 * k, "the fists are 1.5 k .. 4 k apart along the blade: " + gap + what);
					}
				}
			}
		}
		// the default scale with more margin: 0.4 k inside both ends and 2.2 px off the axis (what the shift was searched for)
		double s = 1.6 * KS_DEFAULT;
		for (boolean thin : new boolean[] {false, true}) {
			TpPose.Result r = solve(1, 1, false, thin, KS_DEFAULT);
			for (double[] f : new double[][] {alongAndOff(r, r.handTargetMain()), alongAndOff(r, r.handTargetOff())}) {
				assertTrue(f[0] > TpPose.pommelPx(s) + 0.4 * KS_DEFAULT - 1e-6 && f[0] < TpPose.tsubaPx(s) - 0.4 * KS_DEFAULT + 1e-6, "inside both ends with margin: " + f[0]);
				assertTrue(f[1] <= 2.2 + 1e-6, "close to the axis: " + f[1]);
			}
		}
	}

	/**
	 * R11 T10: the same facts at the old scale without the shift: the left fist sat past the tsuba and 3.3 px off the axis (one hand on the hilt, the other
	 * beside it). A pin of the numbers the shift removes.
	 */
	@Test
	void withoutTheShiftTheLeftFistWasBesideTheHilt() {
		TpPose.Params noShift = new TpPose.Params() {
			@Override
			public double num(String key, double def) {
				return def;
			}

			@Override
			public double[] vec(String key, double[] def) {
				return key.equals("guard.shift") ? new double[3] : def;
			}
		};
		TpPose.Result r = TpPose.solve(new TpPose.Input(1, 1, false, false, HIP, DISPLAY, RIG, 1.0), noShift);
		double[] lo = alongAndOff(r, r.handTargetOff());
		assertEquals(2.205, lo[0], 2e-3, "left fist along the blade (past the tsuba at 1.7)");
		assertEquals(3.258, lo[1], 2e-3, "left fist off the axis");
		double[] rm = alongAndOff(r, r.handTargetMain());
		assertEquals(-0.415, rm[0], 2e-3);
		assertEquals(1.079, rm[1], 2e-3);
	}

	/**
	 * R11 T10, scale invariance: a sword with the display scale 1.6 and the factor 1.25 is the sword of the display scale 2.0 and the factor 1 (hip scale 1.6 / 2.0
	 * and the mouth of the scabbard kept in place): the same frames, arms, hand targets, only the item correction carries the factor.
	 */
	@Test
	void theSwordScaleFactorIsTheSameAsALargerDisplayScale() {
		TpPose.Display disp2 = new TpPose.Display(DISPLAY.rotDeg(), DISPLAY.transBlocks(), 2.0);
		double[] d = unit(HIP.dir());
		double back = 0.25 * TpPose.TSUBA_PX; // the mouth stays: the grip centre of the grown scabbard moves back by (ks - 1) * 1.7 px
		TpPose.Hip hip2 = new TpPose.Hip(new double[] {HIP.posPx()[0] - d[0] * back, HIP.posPx()[1] - d[1] * back, HIP.posPx()[2] - d[2] * back}, HIP.dir(), 2.0);
		for (boolean left : new boolean[] {false, true}) {
			for (boolean thin : new boolean[] {false, true}) {
				for (double p : new double[] {0, 0.3, 0.6, 1}) {
					for (double b : new double[] {0, 0.4, 1}) {
						TpPose.Result a = solve(p, b, left, thin, 1.25);
						TpPose.Result c = TpPose.solve(new TpPose.Input(p, b, left, thin, hip2, disp2, RIG, 1.0), TpPose.Params.DEFAULTS);
						String w = " p=" + p + " b=" + b + " left=" + left + " thin=" + thin;
						assertSameMatrix(a.sword().matrix(), c.sword().matrix(), "sword frame" + w);
						assertSameMatrix(a.saya().matrix(), c.saya().matrix(), "scabbard frame" + w);
						assertEquals(a.main().pitch(), c.main().pitch(), 1e-9, "arm" + w);
						assertEquals(a.main().roll(), c.main().roll(), 1e-9, "arm roll" + w);
						assertEquals(a.off().yaw(), c.off().yaw(), 1e-9, "off arm" + w);
						for (int k = 0; k < 3; k++) {
							assertEquals(a.handTargetMain()[k], c.handTargetMain()[k], 1e-9, "hand target" + w);
							assertEquals(a.handTargetOff()[k], c.handTargetOff()[k], 1e-9, "off hand target" + w);
						}
						assertEquals(1.25, a.gS(), 1e-9);
						assertEquals(1.0, c.gS(), 1e-9);
					}
				}
			}
		}
	}

	/** Every length along the hilt scales with the sword: positions of the fists in units of the scale ratio do not depend on the scale (held, sealed). */
	@Test
	void fistPlacesAlongTheHiltScaleWithTheSword() {
		for (double p : new double[] {0, 1}) {
			double[] ref = null;
			for (double ks : KS) {
				TpPose.Result r = solve(p, 0, false, false, ks);
				double[] rm = alongAndOff(r, r.handTargetMain());
				double[] lo = alongAndOff(r, r.handTargetOff());
				double[] v = {rm[0] / ks, lo[0] / ks};
				if (ref == null) {
					ref = v;
				} else {
					assertEquals(ref[0], v[0], 1e-6, "right fist along the hilt in units of the scale, p=" + p + " ks=" + ks);
					assertEquals(ref[1], v[1], 1e-6, "left hand along the hilt/scabbard in units of the scale, p=" + p + " ks=" + ks);
				}
			}
		}
		// the scabbard grows about its mouth: the mouth point stays where it was
		double[] mouth1 = TpPose.hipFrame(HIP, 1.0).point(0.5, 0.5 + TpPose.TSUBA_PX / 25.6, 0.5);
		for (double ks : KS) {
			double[] m = TpPose.hipFrame(HIP, ks).point(0.5, 0.5 + TpPose.TSUBA_PX / 25.6, 0.5);
			assertEquals(mouth1[0], m[0], 1e-9);
			assertEquals(mouth1[1], m[1], 1e-9);
			assertEquals(mouth1[2], m[2], 1e-9);
		}
		// the scabbard tip (blade length 0.72 m of the models) at the default factor stays at the hip: in front of the leg, not below the foot
		TpMat4 f = TpPose.hipFrame(HIP, KS_DEFAULT);
		double[] tipEnd = f.point(0.5, 0.5 - 0.19 + 0.9785, 0.5);
		assertTrue(tipEnd[1] * 16 < 24, "the scabbard tip is above the ground: " + tipEnd[1] * 16);
	}

	/** The block enters and leaves smoothly (eased weight): the sword frame moves gradually, no jump in the middle. */
	@Test
	void blockEnterIsGradual() {
		TpPose.Result prev = solve(1, 0, false, false);
		double maxStep = 0;
		for (int i = 1; i <= 100; i++) {
			TpPose.Result r = solve(1, i / 100.0, false, false);
			maxStep = Math.max(maxStep, dist(prev.sword().matrix().point(0.5, 0.5, 0.5), r.sword().matrix().point(0.5, 0.5, 0.5)) * 16);
			prev = r;
		}
		assertTrue(maxStep < 0.6, "grip moves in small steps while the block comes in: " + maxStep);
		// eased: the first and last steps are smaller than the middle ones
		double first = dist(solve(1, 0.0, false, false).sword().matrix().point(0.5, 0.5, 0.5), solve(1, 0.05, false, false).sword().matrix().point(0.5, 0.5, 0.5));
		double mid = dist(solve(1, 0.5, false, false).sword().matrix().point(0.5, 0.5, 0.5), solve(1, 0.55, false, false).sword().matrix().point(0.5, 0.5, 0.5));
		assertTrue(first < mid, "soft start of the block");
	}

	/** The draw is continuous: no jump of the sword, the scabbard or the arms between neighbouring progress values. */
	@Test
	void drawIsContinuous() {
		TpPose.Result prev = solve(0, 0, false, false);
		for (int i = 1; i <= 200; i++) {
			double p = i / 200.0;
			TpPose.Result r = solve(p, 0, false, false);
			double[] a = prev.sword().matrix().point(0.5, 0.5, 0.5);
			double[] c = r.sword().matrix().point(0.5, 0.5, 0.5);
			double d = 0;
			for (int k = 0; k < 3; k++) {
				d += (a[k] - c[k]) * (a[k] - c[k]);
			}
			assertTrue(Math.sqrt(d) * 16 < 1.2, "sword jump at p=" + p + ": " + Math.sqrt(d) * 16 + " px");
			assertTrue(Math.abs(r.main().pitch() - prev.main().pitch()) < 0.15, "main arm pitch jump at p=" + p);
			assertTrue(Math.abs(r.off().pitch() - prev.off().pitch()) < 0.15, "off arm pitch jump at p=" + p);
			double[] sa = prev.saya().matrix().point(0.5, 0.5, 0.5);
			double[] sb = r.saya().matrix().point(0.5, 0.5, 0.5);
			double ds = 0;
			for (int k = 0; k < 3; k++) {
				ds += (sa[k] - sb[k]) * (sa[k] - sb[k]);
			}
			assertTrue(Math.sqrt(ds) * 16 < 0.8, "scabbard jump at p=" + p);
			prev = r;
		}
		// the draw ends exactly on the held pose
		assertSameMatrix(prev.sword().matrix(), TpPose.holdFrame(TpPose.Params.DEFAULTS, 1.6), "held frame at p = 1");
		// and at the larger sword scale it ends on the larger held pose, continuously too
		TpPose.Result big = solve(1, 0, false, false, KS_DEFAULT);
		assertSameMatrix(big.sword().matrix(), TpPose.holdFrame(TpPose.Params.DEFAULTS, 2.0), "held frame at p = 1, scale 2.0");
		TpPose.Result pb = solve(0, 0, false, false, KS_DEFAULT);
		for (int i = 1; i <= 200; i++) {
			TpPose.Result r = solve(i / 200.0, 0, false, false, KS_DEFAULT);
			double[] a = pb.sword().matrix().point(0.5, 0.5, 0.5);
			double[] c = r.sword().matrix().point(0.5, 0.5, 0.5);
			assertTrue(dist(a, c) * 16 < 1.5, "sword jump at p=" + i / 200.0 + " at scale 2.0");
			pb = r;
		}
	}

	/** The block fades in and out smoothly too. */
	@Test
	void blockBlendIsContinuous() {
		for (double p : new double[] {0, 1}) {
			TpPose.Result prev = solve(p, 0, false, false);
			for (int i = 1; i <= 100; i++) {
				TpPose.Result r = solve(p, i / 100.0, false, false);
				assertTrue(Math.abs(r.main().roll() - prev.main().roll()) < 0.12, "main arm roll jump in the block blend at b=" + i / 100.0);
				assertTrue(Math.abs(r.off().roll() - prev.off().roll()) < 0.12, "off arm roll jump in the block blend");
				prev = r;
			}
		}
	}

	/** The shoulder never has to slide further than the limit in the held pose and the block (arms stay attached). */
	@Test
	void shouldersStayCloseToTheBody() {
		for (double ks : new double[] {1.0, KS_DEFAULT}) {
			for (double b : new double[] {0, 1}) {
				TpPose.Result r = solve(1, b, false, false, ks);
				for (TpPose.Arm a : new TpPose.Arm[] {r.main(), r.off()}) {
					double s = Math.sqrt(a.dx() * a.dx() + a.dy() * a.dy() + a.dz() * a.dz());
					assertTrue(s < (b > 0 ? 3.2 : 2.5), "shoulder shift " + s + " px in b=" + b + " ks=" + ks);
				}
			}
		}
	}

}
