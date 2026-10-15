package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R12 sword that the petals build in the hand of Byakuya: the two hold modes, the film clock, the pink, the swing clock. */
class PetalBladeMathTest {
	private static final double DT = 1.0 / 60.0;

	private static PetalBladeMath.Params modeB() {
		return new PetalBladeMath.Params();
	}

	private static PetalBladeMath.Params modeA() {
		PetalBladeMath.Params p = new PetalBladeMath.Params();
		p.holdMode = false;
		return p;
	}

	/** Runs the clock from {@code from} to {@code to} and returns the form at the end. */
	private static double run(PetalBladeMath.Timeline tl, PetalBladeMath.Params p, double from, double to) {
		double f = tl.form;
		for (double t = from; t <= to + 1e-9; t += DT) {
			f = tl.update(t, p);
		}
		return f;
	}

	@Test
	void modeBHoldsTheSwordForNSecondsAfterTheFirstHitThenBreaksUp() {
		PetalBladeMath.Params p = modeB();
		PetalBladeMath.Timeline tl = new PetalBladeMath.Timeline();
		run(tl, p, 0, 1.0);
		assertEquals(0, tl.form, 0, "no strike, no sword");
		assertEquals(PetalBladeMath.Phase.NONE, tl.phase(1.0, p));
		tl.strike(1.0, 0.25, p);
		run(tl, p, 1.0, 1.0 + p.assemble + 0.05);
		assertEquals(1.0, tl.form, 1e-9, "the sword stands once the film has run");
		assertEquals(PetalBladeMath.Phase.HOLD, tl.phase(1.0 + p.assemble + 0.05, p));
		// the hit moment is 1.25; N = 3.0 -> normal until 4.25, the pink lead takes 0.25 more
		run(tl, p, 1.0 + p.assemble + 0.05, 4.25 + p.lead - 0.02);
		assertEquals(1.0, tl.form, 1e-9, "still held through the pink lead");
		run(tl, p, 4.25 + p.lead - 0.02, 4.25 + p.lead + p.disperse * 0.5);
		assertTrue(tl.form > 0.0 && tl.form < 1.0, "breaking up: " + tl.form);
		assertEquals(PetalBladeMath.Phase.DISPERSE, tl.phase(4.25 + p.lead + p.disperse * 0.5, p));
		run(tl, p, 4.25 + p.lead + p.disperse * 0.5, 7.0);
		assertEquals(0.0, tl.form, 1e-9);
	}

	@Test
	void modeBFurtherStrikesRestartTheTimer() {
		PetalBladeMath.Params p = modeB();
		PetalBladeMath.Timeline tl = new PetalBladeMath.Timeline();
		tl.strike(0, 0.25, p);
		run(tl, p, 0, 2.5);
		tl.strike(2.5, 0.25, p);
		run(tl, p, 2.5, 5.9);
		assertEquals(1.0, tl.form, 1e-9, "second strike at 2.5 holds until 2.75 + 3.0 (+ lead)");
		run(tl, p, 5.9, 8.0);
		assertEquals(0.0, tl.form, 1e-9);
	}

	@Test
	void modeAHoldsOnlyForTheStrike() {
		PetalBladeMath.Params p = modeA();
		PetalBladeMath.Timeline tl = new PetalBladeMath.Timeline();
		tl.strike(0, 0.25, p);
		run(tl, p, 0, 0.7);
		assertEquals(1.0, tl.form, 1e-9);
		// sweep ends at 0.37, + holdA 0.25 + lead 0.25 = 0.87, then the break up takes disperse seconds
		run(tl, p, 0.7, 0.87 + p.disperse + 0.1);
		assertEquals(0.0, tl.form, 1e-9, "no N-second hold in mode A");
	}

	@Test
	void aBlockBuildsTheSwordAndKeepsItWhileBlockingInBothModes() {
		for (PetalBladeMath.Params p : new PetalBladeMath.Params[] {modeA(), modeB()}) {
			PetalBladeMath.Timeline tl = new PetalBladeMath.Timeline();
			tl.block(0, true, p);
			for (double t = 0; t < 2.6; t += DT) {
				tl.block(t, false, p);
				tl.update(t, p);
			}
			assertEquals(1.0, tl.form, 1e-9);
			assertEquals(0.0, tl.pink(2.6, p), 1e-9, "settled, and no pink lead while blocking");
			tl.unblock(2.6, p);
			run(tl, p, 2.6, 2.6 + 3.0 + p.lead + p.disperse + 0.3);
			assertEquals(0.0, tl.form, 1e-9, "broken up after the block (mode " + (p.holdMode ? "B" : "A") + ")");
		}
	}

	@Test
	void aStrikeDuringTheBreakUpPullsTheSwordBackFromWhereItIs() {
		PetalBladeMath.Params p = modeB();
		PetalBladeMath.Timeline tl = new PetalBladeMath.Timeline();
		tl.strike(0, 0.2, p);
		double t0 = 3.2 + p.lead + p.disperse * 0.5;
		run(tl, p, 0, t0);
		double mid = tl.form;
		assertTrue(mid > 0 && mid < 1, "mid film: " + mid);
		tl.strike(t0, 0.2, p);
		double next = tl.update(t0 + DT, p);
		assertTrue(next > mid - 1e-9, "no jump down when a strike comes during the break up");
	}

	@Test
	void theSwordIsPinkWhileTheFilmRunsEasesToNormalAndGoesPinkBeforeBreakingUp() {
		PetalBladeMath.Params p = modeB();
		PetalBladeMath.Timeline tl = new PetalBladeMath.Timeline();
		assertEquals(0.0, tl.pink(0, p), 0.0, "nothing, nothing to tint");
		tl.strike(0, 0.2, p);
		run(tl, p, 0, 0.3);
		assertEquals(1.0, tl.pink(0.3, p), 0.0, "building: pink");
		run(tl, p, 0.3, p.assemble + 0.02);
		double justFormed = tl.pink(p.assemble + 0.02, p);
		assertTrue(justFormed > 0.9, "just formed: still pink " + justFormed);
		double later = p.assemble + p.settle + 0.1;
		run(tl, p, p.assemble + 0.02, later);
		assertEquals(0.0, tl.pink(later, p), 1e-9, "the normal sword");
		double leadMid = 0.2 + 3.0 + p.lead * 0.5;
		run(tl, p, later, leadMid);
		double mid = tl.pink(leadMid, p);
		assertTrue(mid > 0.2 && mid < 0.8, "pink returns in the lead: " + mid);
		run(tl, p, leadMid, 0.2 + 3.0 + p.lead + 0.3);
		assertEquals(1.0, tl.pink(0.2 + 3.0 + p.lead + 0.3, p), 0.0, "breaking up: pink");
	}

	@Test
	void aSecondStrikeWhileTheSwordStandsDoesNotMakeItPinkAgain() {
		PetalBladeMath.Params p = modeB();
		PetalBladeMath.Timeline tl = new PetalBladeMath.Timeline();
		tl.strike(0, 0.2, p);
		run(tl, p, 0, 3.0);
		assertEquals(0.0, tl.pink(3.0, p), 1e-9);
		tl.strike(3.0, 0.2, p);
		run(tl, p, 3.0, 3.3);
		assertEquals(0.0, tl.pink(3.3, p), 1e-9, "formedAt is not reset by a strike on a standing sword");
	}

	@Test
	void theFilmPositionMatchesTheFormAndTheSwingClockFades() {
		PetalBladeMath.Params p = modeB();
		PetalBladeMath.Timeline tl = new PetalBladeMath.Timeline();
		tl.strike(0, 0.2, p);
		double f1 = run(tl, p, 0, p.assemble * 0.5);
		assertEquals(0.5, f1, 0.05, "the form rises linearly over assemble seconds");
		assertEquals(1.0, tl.swingStrength(0.1, p), 1e-9);
		assertEquals(0.0, tl.swingStrength(5.0, p), 1e-9);
		double mid = tl.swingStrength(0.2 + p.sweep + 0.12 + 0.15, p);
		assertTrue(mid > 0.3 && mid < 0.7, "fading: " + mid);
		// a block counts as a strong swing for the swarm
		PetalBladeMath.Timeline b = new PetalBladeMath.Timeline();
		b.block(0, true, p);
		assertEquals(0.6, b.swingStrength(1.0, p), 1e-9);
	}

	@Test
	void anAbilityBreaksTheSwordUpAtOnceAndAStrikeRightAfterBuildsItAgain() {
		PetalBladeMath.Params p = modeB();
		PetalBladeMath.Timeline tl = new PetalBladeMath.Timeline();
		tl.strike(0, 0.2, p);
		run(tl, p, 0, 1.5);
		assertEquals(1.0, tl.form, 1e-9, "standing with plenty of time left");
		tl.dispel();
		assertEquals(PetalBladeMath.Phase.DISPERSE, tl.phase(1.5, p), "no pink lead, the break up starts now");
		double f = run(tl, p, 1.5, 1.5 + p.disperse * 0.4);
		assertTrue(f < 0.7 && f > 0.3, "breaking up: " + f);
		// a strike right after: the sword comes back from where the film is, and holds again
		tl.strike(1.5 + p.disperse * 0.4, 0.2, p);
		double up = tl.update(1.5 + p.disperse * 0.4 + DT, p);
		assertTrue(up > f - 1e-9, "no jump down");
		run(tl, p, 1.5 + p.disperse * 0.4, 1.5 + p.disperse * 0.4 + 1.0);
		assertEquals(1.0, tl.form, 1e-9);
		// without the strike it is gone
		PetalBladeMath.Timeline other = new PetalBladeMath.Timeline();
		other.strike(0, 0.2, p);
		run(other, p, 0, 1.0);
		other.dispel();
		run(other, p, 1.0, 1.0 + p.disperse + 0.1);
		assertEquals(0.0, other.form, 1e-9);
	}

	@Test
	void theShikaiBladeStaysPinkAboutASecondLongerThanBefore() {
		PetalBladeMath.Params p = modeB();
		assertTrue(p.settle >= 1.4, "settle " + p.settle);
		PetalBladeMath.Timeline tl = new PetalBladeMath.Timeline();
		tl.strike(0, 0.2, p);
		double t = p.assemble + 1.0;
		run(tl, p, 0, t);
		assertTrue(tl.pink(t, p) > 0.2, "one second after it formed it is still pink: " + tl.pink(t, p));
		run(tl, p, t, p.assemble + p.settle + 0.05);
		assertEquals(0.0, tl.pink(p.assemble + p.settle + 0.05, p), 1e-9);
	}
}
