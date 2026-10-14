package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.swarm.V3;
import dev.minebleach.reiatsutest.core.uryu.SeeleCombo;
import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import org.junit.jupiter.api.Test;

/** Tables and maths of the Seele Schneider combo visuals (URYU_VFX_STORYBOARD 2.9): times, strokes, trails, crescent, blade basis, counts, cues. */
class SeeleFxMathTest {
	private static final V3 SOUTH = new V3(0, 0, 1);

	private static void near(V3 a, V3 b, double eps) {
		assertEquals(b.x(), a.x(), eps, "x");
		assertEquals(b.y(), a.y(), eps, "y");
		assertEquals(b.z(), a.z(), eps, "z");
	}

	@Test
	void slashTimesAreThoseOfTheServerCombo() {
		assertEquals(0.15, SeeleFxMath.slashTime(0), 1e-12);
		assertEquals(0.40, SeeleFxMath.slashTime(1), 1e-12);
		assertEquals(0.65, SeeleFxMath.slashTime(2), 1e-12);
		for (int i = 0; i < UryuBalance.SEELE_SLASHES; i++) {
			assertEquals(SeeleCombo.slashTick(i) / 20.0, SeeleFxMath.slashTime(i), 1e-12);
		}
		assertEquals(0.15, UryuBalance.SEELE_LUNGE_TICKS / 20.0, 1e-12, "the lunge ends when the first slash starts");
		assertEquals(0.20, SeeleFxMath.DISSOLVE_AT - SeeleFxMath.slashTime(2), 1e-12, "0.2 s of rest after the last stroke");
		assertEquals(0.85, SeeleFxMath.dissolveStart(SeeleFxMath.slashTime(2)), 1e-12);
		assertTrue(SeeleFxMath.DISSOLVE_AT + SeeleFxMath.DISSOLVE < SeeleFxMath.COMBO_LIFETIME);
		assertTrue(SeeleFxMath.SWEEP < UryuBalance.SEELE_SLASH_GAP / 20.0, "a stroke is over before the next one starts");
		assertEquals(3.5, UryuBalance.SEELE_REACH, 1e-12);
		assertEquals(100.0, UryuBalance.SEELE_CONE_DEG, 1e-12);
	}

	@Test
	void profilesAreRightToLeftBackAndAnOverheadChop() {
		SeeleFxMath.Profile a = SeeleFxMath.profile(0);
		SeeleFxMath.Profile b = SeeleFxMath.profile(1);
		SeeleFxMath.Profile c = SeeleFxMath.profile(2);
		assertEquals(18.0, a.tiltDeg(), 0);
		assertEquals(-18.0, b.tiltDeg(), 0);
		assertEquals(1.0, a.sign() * -b.sign(), 0, "the reverse stroke runs the other way");
		assertEquals(1.05, b.reachScale() / a.reachScale(), 1e-12, "slash 2 is 5 percent longer");
		assertEquals(1.4, c.crescentScale(), 0);
		assertTrue(c.wide() && c.chop() && !a.wide() && !b.chop());
		assertEquals(90.0, c.tiltDeg(), 0);
		assertEquals(c, SeeleFxMath.profile(9), "indices are clamped");
		assertEquals(a, SeeleFxMath.profile(-3));
	}

	@Test
	void firstStrokeSweepsFromTheRightToTheLeftAndEndsFlush() {
		SeeleFxMath.Frame f = SeeleFxMath.serverFrame(SOUTH, 0);
		V3 d0 = f.dir(0);
		V3 d1 = f.dir(1);
		V3 dm = f.dir(0.5);
		// looking south the player's right is -x
		assertTrue(d0.x() < -0.7 && d1.x() > 0.7, "right to left: " + d0 + " -> " + d1);
		near(dm, SOUTH, 1e-9);
		assertEquals(Math.toRadians(50), Math.acos(d0.dot(SOUTH)), 1e-9, "half the cone of 100 degrees");
		assertEquals(Math.toRadians(50), Math.acos(d1.dot(SOUTH)), 1e-9);
		assertTrue(d0.y() > 0 && d1.y() < 0, "the 18 degree tilt: from a little above to a little below");
		V3 e0 = SeeleFxMath.serverFrame(SOUTH, 1).dir(0);
		V3 e1 = SeeleFxMath.serverFrame(SOUTH, 1).dir(1);
		assertTrue(e0.x() > 0.7 && e1.x() < -0.7, "the reverse stroke goes back");
		assertTrue(e0.y() > 0 && e1.y() < 0, "-18 degrees with the other sign: it also falls");
	}

	@Test
	void theChopFallsFromAboveToBelowInAVerticalPlane() {
		SeeleFxMath.Frame f = SeeleFxMath.serverFrame(SOUTH, 2);
		V3 top = f.dir(0);
		V3 low = f.dir(1);
		assertTrue(top.y() > 0.7 && low.y() < -0.7, top + " " + low);
		assertEquals(0.0, top.x(), 1e-9);
		assertEquals(0.0, low.x(), 1e-9);
		assertEquals(1.0, Math.abs(f.normal().x()), 1e-9, "the swing plane faces sideways");
		for (double u = 0; u <= 1.0; u += 0.1) {
			assertEquals(0.0, f.dir(u).dot(f.normal()), 1e-9, "every direction lies in the plane");
			assertEquals(1.0, f.dir(u).length(), 1e-9);
		}
	}

	@Test
	void everyFrameIsOrthonormalAndWorksForAnyLook() {
		V3[] looks = {SOUTH, new V3(1, 0.3, -0.5), new V3(0, 1, 0), new V3(0, -1, 0), new V3(0.001, 0.97, 0.0), new V3(0, 0, 0)};
		for (V3 look : looks) {
			for (int i = 0; i < 3; i++) {
				SeeleFxMath.Frame f = SeeleFxMath.serverFrame(look, i);
				assertEquals(1.0, f.look().length(), 1e-9);
				assertEquals(1.0, f.plane().length(), 1e-9);
				assertEquals(1.0, f.normal().length(), 1e-9);
				assertEquals(0.0, f.look().dot(f.plane()), 1e-9);
				assertEquals(0.0, f.look().dot(f.normal()), 1e-9);
				assertEquals(0.0, f.plane().dot(f.normal()), 1e-9);
				assertTrue(f.dir(0.3).isFinite());
			}
		}
	}

	@Test
	void aTargetIsCrossedWhenTheBladePointsAtIt() {
		for (int i = 0; i < 3; i++) {
			SeeleFxMath.Frame f = SeeleFxMath.serverFrame(new V3(0.2, 0.1, 0.95), i);
			assertEquals(0.5, SeeleFxMath.crossProgress(f, f.look().scale(2.5)), 1e-9, "on the look: the middle of the stroke");
			assertEquals(0.0, SeeleFxMath.crossProgress(f, f.dir(0)), 1e-9);
			assertEquals(1.0, SeeleFxMath.crossProgress(f, f.dir(1)), 1e-9);
			for (double u = 0.1; u < 0.95; u += 0.1) {
				assertEquals(u, SeeleFxMath.crossProgress(f, f.dir(u).scale(3.0)), 1e-9, "stroke " + i + " u " + u);
			}
			assertEquals(0.0, SeeleFxMath.crossProgress(f, f.plane()), 1e-9, "beyond the cone: clamped");
			assertEquals(1.0, SeeleFxMath.crossProgress(f, f.plane().scale(-1)), 1e-9);
			assertEquals(0.5, SeeleFxMath.crossProgress(f, new V3(0, 0, 0)), 1e-9, "a target on the pivot: the look");
		}
		// the first stroke (right to left) meets a target on the right early and one on the left late
		SeeleFxMath.Frame f = SeeleFxMath.serverFrame(new V3(0, 0, 1), 0);
		double onTheRight = SeeleFxMath.crossProgress(f, new V3(-1, 0, 3));
		double onTheLeft = SeeleFxMath.crossProgress(f, new V3(1, 0, 3));
		assertTrue(onTheRight < 0.5 && onTheLeft > 0.5, onTheRight + " " + onTheLeft);
		assertTrue(SeeleFxMath.passTime(onTheRight) < SeeleFxMath.passTime(onTheLeft));
	}

	@Test
	void progressIsASmoothstepAndPassTimeInvertsIt() {
		assertEquals(0.0, SeeleFxMath.progress(0), 0);
		assertEquals(0.5, SeeleFxMath.progress(SeeleFxMath.SWEEP / 2), 1e-12);
		assertEquals(1.0, SeeleFxMath.progress(SeeleFxMath.SWEEP), 1e-12);
		assertEquals(1.0, SeeleFxMath.progress(5), 0);
		for (double u = 0; u <= 1.0001; u += 0.125) {
			assertEquals(u, SeeleFxMath.progress(SeeleFxMath.passTime(u)), 1e-6, "u " + u);
		}
		assertEquals(0.0, SeeleFxMath.passTime(0), 1e-6);
		assertEquals(SeeleFxMath.SWEEP, SeeleFxMath.passTime(1), 1e-6);
	}

	@Test
	void bladeGrowsThenKeepsThenDissolvesFromTheTip() {
		assertEquals(0.0, SeeleFxMath.bladeGrow(0), 0);
		assertEquals(0.0, SeeleFxMath.bladeGrow(SeeleFxMath.GROW_FROM), 1e-12);
		assertTrue(SeeleFxMath.bladeGrow(SeeleFxMath.GROW_FROM + SeeleFxMath.GROW / 4) > 0.8, "ease out expo: most of it at once");
		assertEquals(1.0, SeeleFxMath.bladeGrow(SeeleFxMath.GROW_FROM + SeeleFxMath.GROW), 1e-12);
		assertTrue(SeeleFxMath.GROW_FROM + SeeleFxMath.GROW <= SeeleFxMath.slashTime(0), "the blade is out before the first slash");
		assertTrue(SeeleFxMath.DRAW_HAND <= SeeleFxMath.slashTime(0));
		double ds = SeeleFxMath.DISSOLVE_AT;
		assertEquals(1.0, SeeleFxMath.bladeLength(0.5, ds), 1e-12);
		assertEquals(1.0, SeeleFxMath.bladeLength(ds, ds), 1e-12);
		assertEquals(0.5, SeeleFxMath.bladeLength(ds + SeeleFxMath.DISSOLVE / 2, ds), 1e-12);
		assertEquals(0.0, SeeleFxMath.bladeLength(ds + SeeleFxMath.DISSOLVE, ds), 1e-12);
		double prev = 2;
		for (double t = ds; t <= ds + SeeleFxMath.DISSOLVE; t += 0.01) {
			double l = SeeleFxMath.bladeLength(t, ds);
			assertTrue(l <= prev + 1e-12, "monotone");
			prev = l;
		}
		assertEquals(0.0, SeeleFxMath.hiltRise(0), 1e-12);
		assertEquals(1.0, SeeleFxMath.hiltRise(SeeleFxMath.DRAW_HAND), 1e-12);
		assertEquals(1.0, SeeleFxMath.hiltRise(9), 1e-12);
	}

	@Test
	void bladeBasisIsARightHandedFrameWithTheFlatFaceOnTheSwingPlane() {
		for (int i = 0; i < 3; i++) {
			SeeleFxMath.Frame f = SeeleFxMath.serverFrame(new V3(0.3, 0.2, 0.9), i);
			for (double u = 0; u <= 1.0; u += 0.25) {
				V3 d = f.dir(u);
				V3[] b = SeeleFxMath.bladeBasis(d, f.normal());
				near(b[1], d, 1e-9);
				near(b[0], f.normal(), 1e-9);
				assertEquals(1.0, b[2].length(), 1e-9);
				near(b[0].cross(b[1]), b[2], 1e-9);
				assertEquals(1.0, b[0].dot(b[1].cross(b[2])), 1e-9, "determinant +1: a proper rotation");
			}
		}
		V3[] fallback = SeeleFxMath.bladeBasis(new V3(0, 1, 0), new V3(0, 1, 0));
		assertEquals(1.0, fallback[0].length(), 1e-9, "a normal parallel to the blade still gives a frame");
		assertEquals(0.0, fallback[0].dot(fallback[1]), 1e-9);
	}

	@Test
	void bladeDirectionBlendsFromTheLastStrokeToTheNewOne() {
		V3 a = new V3(1, 0, 0);
		V3 b = new V3(0, 0, 1);
		near(SeeleFxMath.blendDir(null, b, 0), b, 0);
		near(SeeleFxMath.blendDir(a, b, 0), a, 1e-9);
		near(SeeleFxMath.blendDir(a, b, SeeleFxMath.BLEND_IN), b, 1e-9);
		V3 mid = SeeleFxMath.blendDir(a, b, SeeleFxMath.BLEND_IN / 2);
		assertEquals(1.0, mid.length(), 1e-9);
		assertEquals(mid.x(), mid.z(), 1e-9);
	}

	@Test
	void trailsTaperFadeAndTheOuterWaveRunsOut() {
		assertEquals(12, SeeleFxMath.segments(2));
		assertEquals(9, SeeleFxMath.segments(1));
		assertEquals(6, SeeleFxMath.segments(0), "tier Low: 6 segments");
		assertEquals(6, SeeleFxMath.segments(-1));
		assertEquals(0.55, SeeleFxMath.trailWidth(1, 0.55), 1e-12, "head width 0.55");
		assertTrue(SeeleFxMath.trailWidth(0, 0.55) < 0.08 && SeeleFxMath.trailWidth(0, 0.55) > 0);
		double prev = 0;
		for (double f = 0; f <= 1.0; f += 0.1) {
			double w = SeeleFxMath.trailWidth(f, 0.55);
			assertTrue(w >= prev, "tapering");
			prev = w;
		}
		assertEquals(0.12, SeeleFxMath.coreWidth(1), 1e-12, "the Q1 core is 0.12 wide");
		assertTrue(SeeleFxMath.coreWidth(0) < SeeleFxMath.coreWidth(1));
		assertEquals(1.0, SeeleFxMath.innerAlpha(0), 1e-12);
		assertEquals(0.0, SeeleFxMath.innerAlpha(SeeleFxMath.INNER_LIFE), 1e-12);
		assertEquals(0.0, SeeleFxMath.innerAlpha(-0.01), 0);
		assertTrue(SeeleFxMath.innerAlpha(0.1) < SeeleFxMath.innerAlpha(0.05));
		assertEquals(1.8, SeeleFxMath.innerRadius(1.5) - 0.0, 1e-9, "hilt 0.45 plus the 1.35 m blade");
		assertEquals(2.975, SeeleFxMath.outerRadiusMax(3.5), 1e-9);
		assertEquals(1.8, SeeleFxMath.outerRadius(0, 1.8, 3.0), 1e-12);
		assertEquals(3.0, SeeleFxMath.outerRadius(SeeleFxMath.OUTER_RISE, 1.8, 3.0), 1e-12);
		assertTrue(SeeleFxMath.outerRadius(0.05, 1.8, 3.0) > 2.4, "ease out: most of the way in the first quarter");
		assertEquals(0.0, SeeleFxMath.outerAlpha(-0.1), 0);
		assertEquals(0.0, SeeleFxMath.outerAlpha(SeeleFxMath.OUTER_LIFE + 0.01), 0);
		assertTrue(SeeleFxMath.outerAlpha(0.04) > 0.9);
		assertEquals(0.0, SeeleFxMath.outerAlpha(0.0), 1e-12);
	}

	@Test
	void theCrescentEchoAppearsAtTheEndOfTheSweepAndFadesIn025Seconds() {
		assertEquals(0.0, SeeleFxMath.echoAlpha(0), 0);
		assertEquals(0.0, SeeleFxMath.echoAlpha(0.5 * SeeleFxMath.SWEEP), 0);
		assertEquals(1.0, SeeleFxMath.echoAlpha(SeeleFxMath.SWEEP), 1e-12);
		assertTrue(SeeleFxMath.echoAlpha(SeeleFxMath.SWEEP + 0.1) < 1.0);
		assertEquals(0.0, SeeleFxMath.echoAlpha(SeeleFxMath.SWEEP + SeeleFxMath.ECHO_LIFE), 1e-12);
		assertEquals(0.0, SeeleFxMath.echoAlpha(5), 1e-12);
		assertEquals(0.25, SeeleFxMath.ECHO_LIFE, 0, "the storyboard: crescent life 0.25 s");
	}

	@Test
	void theCrescentQuadSitsOnTheArcWithItsApexOnTheRadius() {
		SeeleFxMath.Frame f = SeeleFxMath.serverFrame(SOUTH, 0);
		double radius = 2.975;
		double size = SeeleFxMath.crescentSize(radius, f.halfCone(), 1.0);
		assertEquals(2 * radius * Math.sin(Math.toRadians(50)), size, 1e-12, "the chord of the arc");
		assertEquals(1.4 * size, SeeleFxMath.crescentSize(radius, f.halfCone(), 1.4), 1e-12);
		V3 pivot = new V3(10, 5, -3);
		V3 mid = f.dir(0.5);
		V3 centre = SeeleFxMath.crescentCentre(pivot, mid, radius, size);
		double rot = SeeleFxMath.planeRot(f.normal(), mid);
		V3 v = SeeleFxMath.planeV(f.normal(), rot);
		near(v, mid, 1e-9);
		V3 apex = centre.add(v.scale(SeeleFxMath.CRESCENT_APEX * size));
		assertEquals(radius, apex.sub(pivot).length(), 1e-9, "the apex of the sprite is on the arc radius");
	}

	@Test
	void planeRotationTurnsTheSpritesUpAxisToAnyInPlaneDirectionForAnyNormal() {
		V3[] normals = {new V3(0, 1, 0), new V3(0, -1, 0), new V3(1, 0, 0), new V3(0.3, 0.8, -0.2), new V3(0, 0.999999, 0.001), new V3(-0.5, 0.1, 0.9)};
		for (V3 raw : normals) {
			V3 n = raw.normalizedOr(new V3(0, 1, 0));
			V3[] tb = SeeleFxMath.planeBasis(n);
			assertEquals(0.0, tb[0].dot(n), 1e-6);
			assertEquals(0.0, tb[1].dot(n), 1e-6);
			assertEquals(1.0, tb[1].length(), 1e-6);
			for (int k = 0; k < 8; k++) {
				double a = k * Math.PI / 4 + 0.3;
				V3 want = tb[0].scale(Math.cos(a)).add(tb[1].scale(Math.sin(a)));
				V3 v = SeeleFxMath.planeV(n, SeeleFxMath.planeRot(n, want));
				near(v, want, 1e-6);
			}
		}
	}

	@Test
	void firstPersonRibbonsAreClippedNearTheCamera() {
		assertEquals(0.5, SeeleFxMath.nearClip(0.77, 0.4), 1e-12);
		assertEquals(0.5, SeeleFxMath.nearClip(0.77, 1.5), 1e-12);
		assertEquals(0.4, SeeleFxMath.nearClip(0.4, 0.2), 1e-12, "a ribbon narrower than the clip is never widened");
		assertEquals(0.55, SeeleFxMath.nearClip(0.55, 3.0), 1e-12);
		assertEquals(0.77, SeeleFxMath.nearClip(0.77, 9.0), 1e-12);
		double prev = 0;
		for (double d = 0; d < 4; d += 0.25) {
			double w = SeeleFxMath.nearClip(0.77, d);
			assertTrue(w >= prev - 1e-12, "grows back smoothly, never jumps down");
			prev = w;
		}
	}

	@Test
	void groundCrackLiesAheadWithinTheReach() {
		assertEquals(2.4, SeeleFxMath.crackDistance(3.5), 1e-12);
		assertEquals(0.7, SeeleFxMath.crackDistance(1.0), 1e-12);
		assertTrue(SeeleFxMath.crackDistance(UryuBalance.SEELE_REACH) <= UryuBalance.SEELE_REACH);
		assertEquals(1.0, SeeleFxMath.CRACK_RADIUS, 0);
		assertEquals(0.2, SeeleFxMath.RING_FROM, 0);
		assertEquals(2.2, SeeleFxMath.RING_TO, 0);
		assertEquals(0.25, SeeleFxMath.RING_LIFE, 0);
	}

	@Test
	void hitsAndScreenEffectsFollowTheTable() {
		SeeleFxMath.Hit h0 = SeeleFxMath.hit(0);
		SeeleFxMath.Hit h1 = SeeleFxMath.hit(1);
		SeeleFxMath.Hit h2 = SeeleFxMath.hit(2);
		assertEquals(h0, h1);
		assertEquals(1.1, h0.burst(), 0, "burst_rays 1.1");
		assertEquals(0.7, h0.star(), 0, "star4 0.7");
		assertEquals(6, h0.flakes(), "6 flakes");
		assertEquals(0, h0.dashes());
		assertFalse(h0.ring());
		assertTrue(h2.ring());
		assertEquals(8, h2.dashes(), "the knockback burst: 8 radial dashes");
		assertTrue(h2.burst() > h0.burst());
		assertEquals(0.2, SeeleFxMath.screen(0).shakeDeg(), 0);
		assertEquals(0.15, SeeleFxMath.screen(1).shakeSecs(), 0);
		assertEquals(0.5, SeeleFxMath.screen(2).shakeDeg(), 0);
		assertEquals(0.25, SeeleFxMath.screen(2).shakeSecs(), 0);
		assertEquals(40.0, SeeleFxMath.screen(2).hitstopMs(), 0, "HITSTOP 40 ms on the third stroke");
		assertEquals(0.0, SeeleFxMath.screen(0).hitstopMs(), 0);
		assertEquals(3.0, SeeleFxMath.LUNGE_FOV_DEG, 0);
	}

	@Test
	void lungeTableMatchesTheStoryboard() {
		assertEquals(3, SeeleFxMath.LUNGE_HEIGHTS.length, "hip, chest, head");
		assertEquals(2.0, SeeleFxMath.lungeLength(), 1e-12);
		assertEquals(12, SeeleFxMath.LUNGE_DASHES);
		assertEquals(8, SeeleFxMath.START_DASHES);
		assertEquals(0.04, SeeleFxMath.AFTERIMAGE_GAP, 0);
		assertEquals(0.35, SeeleFxMath.AFTERIMAGE_ALPHA, 0);
		assertTrue(2 * SeeleFxMath.AFTERIMAGE_STEP <= UryuBalance.SEELE_LUNGE, "the copies stand inside the run");
		assertEquals(1.8, SeeleFxMath.DISC_SIZE, 0);
		assertEquals(8, SeeleFxMath.DISSOLVE_FLAKES);
		assertEquals(12, SeeleFxMath.DISSOLVE_DASHES);
		assertEquals(3, SeeleFxMath.ARC_FLAKES);
		assertEquals(-1.0, SeeleFxMath.offhandSide(true), 0, "right hander: the free hand is the left one");
		assertEquals(1.0, SeeleFxMath.offhandSide(false), 0);
	}

	@Test
	void soundCuesUseKnownVanillaIdsAndSaneLevels() {
		java.util.List<SeeleFxMath.Cue> all = new java.util.ArrayList<>(SeeleFxMath.lungeCues());
		for (int i = 0; i < 3; i++) {
			all.addAll(SeeleFxMath.slashCues(i));
			all.addAll(SeeleFxMath.hitCues(i));
		}
		all.add(SeeleFxMath.dissolveCue());
		for (SeeleFxMath.Cue c : all) {
			assertNotNull(c.id());
			assertTrue(c.id().matches("[a-z_]+(\\.[a-z_]+)+"), c.id());
			assertTrue(c.pitch() >= 0.5 && c.pitch() <= 2.0, c.id() + " pitch " + c.pitch());
			assertTrue(c.volume() > 0 && c.volume() <= 1.0, c.id() + " volume " + c.volume());
		}
		assertEquals("item.trident.throw", SeeleFxMath.lungeCues().get(0).id());
		assertEquals(1.6, SeeleFxMath.lungeCues().get(0).pitch(), 0);
		assertEquals("entity.player.attack.strong", SeeleFxMath.slashCues(2).get(0).id());
		assertEquals(1.2, SeeleFxMath.slashCues(2).get(0).pitch(), 0);
		assertEquals("item.mace.smash_air", SeeleFxMath.slashCues(2).get(1).id());
		assertEquals("entity.player.attack.crit", SeeleFxMath.hitCues(0).get(0).id());
		assertEquals("block.amethyst_block.break", SeeleFxMath.dissolveCue().id());
		assertEquals(1.9, SeeleFxMath.dissolveCue().pitch(), 0);
		assertEquals(0.2, SeeleFxMath.dissolveCue().volume(), 0);
		// the server plays the sweep at every slash, so the client layer never repeats it (but the lunge has no server sound)
		for (int i = 0; i < 3; i++) {
			for (SeeleFxMath.Cue c : SeeleFxMath.slashCues(i)) {
				assertFalse(c.id().equals("entity.player.attack.sweep"), "slash " + i);
			}
		}
	}
}
