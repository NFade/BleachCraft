package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.combat.SwingMotion;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The arcs of the pink melee trail of the Byakuya sword: direction by animation name, fixed budget, timing, mirror symmetry. */
class MeleeTrailMathTest {
	private static double[] tip(SwingMotion.Profile p, double u) {
		double[] o = new double[3];
		MeleeTrailMath.tip(p, u, o);
		return o;
	}

	@Test
	void horizontalSlashesSweepAcrossTheViewInTheirOwnDirection() {
		double[] a = tip(SwingMotion.Profile.H_R, 0);
		double[] b = tip(SwingMotion.Profile.H_R, 1);
		assertTrue(a[0] > 0.7 && b[0] < -0.7, "from the right to the left: " + a[0] + " to " + b[0]);
		assertTrue(a[2] > 0.3 && b[2] > 0.3, "both ends stay in front of the player");
		// backhand: the mirror
		double[] c = tip(SwingMotion.Profile.H_L, 0);
		double[] d = tip(SwingMotion.Profile.H_L, 1);
		assertEquals(-a[0], c[0], 1e-9);
		assertEquals(-b[0], d[0], 1e-9);
		assertEquals(a[1], c[1], 1e-9);
		assertEquals(a[2], c[2], 1e-9);
	}

	@Test
	void overheadChopsFallAndTheUppercutRises() {
		for (SwingMotion.Profile p : new SwingMotion.Profile[] {SwingMotion.Profile.V_R, SwingMotion.Profile.V_L}) {
			assertTrue(tip(p, 0)[1] > 0.85 && tip(p, 1)[1] < -0.4, p + " goes from high to low");
		}
		assertTrue(tip(SwingMotion.Profile.V_R, 0)[0] > 0 && tip(SwingMotion.Profile.V_L, 0)[0] < 0, "chops start on their own side");
		assertTrue(tip(SwingMotion.Profile.UPPER, 0)[1] < -0.6 && tip(SwingMotion.Profile.UPPER, 1)[1] > 0.8, "the uppercut rises");
	}

	@Test
	void theThrustRunsStraightForward() {
		double prev = -1;
		for (double u = 0; u <= 1.0001; u += 0.1) {
			double[] t = tip(SwingMotion.Profile.STAB, u);
			assertTrue(t[2] > prev, "z grows");
			prev = t[2];
			assertTrue(Math.abs(t[0]) < 0.1 && Math.abs(t[1]) < 0.1, "stays on the line in front of the chest");
		}
		assertTrue(tip(SwingMotion.Profile.STAB, 1)[2] > 1.0);
	}

	@Test
	void everyArcPointIsAtTheReachAndTheBladeStartsAtTheHand() {
		for (SwingMotion.Profile p : SwingMotion.Profile.values()) {
			if (p == SwingMotion.Profile.STAB) {
				continue;
			}
			for (double u = 0; u <= 1.0001; u += 0.05) {
				double[] t = tip(p, u);
				assertEquals(1.0, Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]), 1e-9, p + " on the sphere of the reach");
				double[] hand = new double[3];
				MeleeTrailMath.blade(p, u, 0, 1.85, hand);
				assertEquals(MeleeTrailMath.HAND * 1.85, Math.sqrt(hand[0] * hand[0] + hand[1] * hand[1] + hand[2] * hand[2]), 1e-9);
				double[] edge = new double[3];
				MeleeTrailMath.blade(p, u, 1, 1.85, edge);
				assertEquals(1.85, Math.sqrt(edge[0] * edge[0] + edge[1] * edge[1] + edge[2] * edge[2]), 1e-9);
			}
		}
	}

	@Test
	void theBudgetIsFixedAndNeverExceeded() {
		assertTrue(MeleeTrailMath.PETALS + MeleeTrailMath.GLOWS + MeleeTrailMath.ACCENTS <= MeleeTrailMath.BUDGET);
		for (SwingMotion.Profile p : SwingMotion.Profile.values()) {
			for (double scale : new double[] {0.1, 1.0, 3.0}) {
				List<MeleeTrailMath.Mote> m = MeleeTrailMath.plan(p, 7L, MeleeTrailMath.REACH, scale);
				assertEquals(MeleeTrailMath.PETALS + MeleeTrailMath.GLOWS + MeleeTrailMath.ACCENTS, m.size(), "the same number of motes at every scale");
				assertTrue(m.size() <= MeleeTrailMath.BUDGET);
			}
		}
	}

	@Test
	void theTrailAssemblesInAFewTenthsAndIsGoneAfterAQuarterSecond() {
		for (SwingMotion.Profile p : SwingMotion.Profile.values()) {
			for (MeleeTrailMath.Mote m : MeleeTrailMath.plan(p, 3L, MeleeTrailMath.REACH, 1.0)) {
				assertTrue(m.delay() >= 0 && m.delay() <= MeleeTrailMath.REVEAL + 1e-9, "appears within the assembly time: " + m.delay());
				assertTrue(m.life() > 0, "lives");
				assertTrue(m.delay() + m.life() <= MeleeTrailMath.SECONDS + 1e-9, "gone after " + MeleeTrailMath.SECONDS + " s: " + (m.delay() + m.life()));
			}
		}
		assertEquals(0.0, MeleeTrailMath.appearAt(0), 0);
		assertEquals(MeleeTrailMath.REVEAL, MeleeTrailMath.appearAt(1), 1e-12);
		assertTrue(MeleeTrailMath.appearAt(0.3) < MeleeTrailMath.appearAt(0.6), "the blade grows along the path");
	}

	@Test
	void theLaidOutMotesFollowThePathAndTheScaleScalesThemLinearly() {
		for (SwingMotion.Profile p : SwingMotion.Profile.values()) {
			List<MeleeTrailMath.Mote> one = MeleeTrailMath.plan(p, 11L, MeleeTrailMath.REACH, 1.0);
			List<MeleeTrailMath.Mote> two = MeleeTrailMath.plan(p, 11L, MeleeTrailMath.REACH, 2.0);
			for (int i = 0; i < one.size(); i++) {
				MeleeTrailMath.Mote a = one.get(i);
				MeleeTrailMath.Mote b = two.get(i);
				assertEquals(a.kind(), b.kind());
				assertEquals(2 * a.x(), b.x(), 1e-9);
				assertEquals(2 * a.y(), b.y(), 1e-9);
				assertEquals(2 * a.z(), b.z(), 1e-9);
				assertEquals(a.delay(), b.delay(), 1e-12, "the timing does not scale");
				double d = Math.sqrt(a.x() * a.x() + a.y() * a.y() + a.z() * a.z());
				if (p != SwingMotion.Profile.STAB) {
					assertTrue(d >= MeleeTrailMath.HAND * MeleeTrailMath.REACH - 0.25 && d <= MeleeTrailMath.REACH + 0.25, p + " mote " + i + " on the blade: " + d);
				}
			}
		}
		// the glow along the edge covers the whole path: first and last glow sit at the two ends
		List<MeleeTrailMath.Mote> m = MeleeTrailMath.plan(SwingMotion.Profile.H_R, 1L, MeleeTrailMath.REACH, 1.0);
		MeleeTrailMath.Mote first = m.get(0);
		MeleeTrailMath.Mote last = m.get(MeleeTrailMath.GLOWS - 1);
		assertTrue(first.x() > 1.0 && last.x() < -1.0, "the glow starts on the right and ends on the left");
	}

	@Test
	void theSameSwingLooksTheSameEverywhere() {
		List<MeleeTrailMath.Mote> a = MeleeTrailMath.plan(SwingMotion.Profile.V_L, 99L, 1.8, 1.0);
		List<MeleeTrailMath.Mote> b = MeleeTrailMath.plan(SwingMotion.Profile.V_L, 99L, 1.8, 1.0);
		assertEquals(a, b);
		assertTrue(!a.equals(MeleeTrailMath.plan(SwingMotion.Profile.V_L, 100L, 1.8, 1.0)), "another seed scatters the petals differently");
	}

	@Test
	void profilesComeFromTheBetterCombatAnimationNames() {
		assertEquals(SwingMotion.Profile.H_L, SwingMotion.profileFor("bettercombat:two_handed_slash_horizontal_left", 0));
		assertEquals(SwingMotion.Profile.V_R, SwingMotion.profileFor("bettercombat:two_handed_slash_vertical_right", 0));
		assertEquals(SwingMotion.Profile.STAB, SwingMotion.profileFor("bettercombat:two_handed_stab", 0));
	}
}
