package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.combat.UryuBowFlow.Phase;
import org.junit.jupiter.api.Test;

/** The pull and release state machine of the Uryu bow ({@link UryuBowFlow}). Time in seconds, 60 frames per second. */
class UryuBowFlowTest {
	private static final double DT = 1.0 / 60.0;

	private double now;
	private double useTicks;

	private UryuBowFlow flow() {
		now = 100.0;
		useTicks = 0.0;
		return new UryuBowFlow(UryuTestData::endTick);
	}

	/** Runs frames for {@code seconds}; the use time follows the clock while {@code using} (and restarts at 0 when it was not used before). */
	private void run(UryuBowFlow f, double seconds, boolean holding, boolean using) {
		int n = (int) Math.round(seconds / DT);
		for (int i = 0; i < n; i++) {
			now += DT;
			useTicks = using ? useTicks + DT * 20.0 : 0.0;
			f.update(now, holding, using, useTicks);
		}
	}

	@Test
	void offUntilTheBowIsHeldThenFadesInAndOut() {
		UryuBowFlow f = flow();
		run(f, 0.5, false, false);
		assertEquals(Phase.OFF, f.phase());
		assertFalse(f.active());
		assertEquals(0.0, f.weight());
		run(f, 0.05, true, false);
		assertEquals(Phase.IDLE, f.phase());
		assertTrue(f.active());
		assertTrue(f.weight() > 0.0 && f.weight() < 1.0, "fading in");
		run(f, 0.3, true, false);
		assertEquals(1.0, f.weight(), 1e-9);
		run(f, 0.05, false, false);
		assertTrue(f.weight() < 1.0 && f.weight() > 0.0, "fading out");
		assertTrue(f.active());
		run(f, 0.3, false, false);
		assertFalse(f.active());
		assertEquals(Phase.OFF, f.phase());
		assertEquals(UryuAnims.IDLE, f.anim());
	}

	@Test
	void theDrawTickIsTheUseTimeAndHoldsAtFullDraw() {
		UryuBowFlow f = flow();
		run(f, 0.3, true, false);
		run(f, 0.05, true, true);
		assertEquals(Phase.DRAW, f.phase());
		assertEquals(UryuAnims.DRAW, f.anim());
		assertEquals(UryuAnims.IDLE, f.prevAnim(), "blending out of the idle");
		run(f, 0.2, true, true);
		assertEquals(5.0, f.tick(now), 0.4, "5 ticks after a quarter second");
		run(f, 0.3, true, true);
		assertNull(f.prevAnim(), "the blend is over");
		assertEquals(1.0, f.xfade(now));
		run(f, 2.0, true, true);
		assertEquals(Phase.DRAW, f.phase());
		assertEquals(20.0, f.tick(now), 1e-9, "held at the last key of the draw while the key is held");
	}

	@Test
	void releasingPlaysTheReleaseFromTheFullDrawAndReturnsToIdle() {
		UryuBowFlow f = flow();
		run(f, 0.3, true, false);
		run(f, 1.2, true, true);
		run(f, DT, true, false);
		assertEquals(Phase.RELEASE, f.phase());
		assertEquals(UryuAnims.RELEASE, f.anim());
		assertEquals(UryuAnims.DRAW, f.prevAnim());
		assertEquals(20.0, f.prevTick(), 1e-9, "the draw is frozen at the tick it had");
		assertTrue(f.xfade(now) < 1.0);
		run(f, 0.3, true, false);
		assertEquals(Phase.RELEASE, f.phase());
		assertEquals(6.0, f.tick(now), 0.8);
		run(f, 0.5, true, false);
		assertEquals(Phase.IDLE, f.phase(), "the release has ended (14 ticks, 0.7 s)");
		assertEquals(UryuAnims.IDLE, f.anim());
	}

	@Test
	void aShortDrawIsCancelledWithoutARelease() {
		UryuBowFlow f = flow();
		run(f, 0.3, true, false);
		f.update(now += DT, true, true, 0.4);
		f.update(now += DT, true, false, 0.0);
		assertEquals(Phase.IDLE, f.phase());
		assertEquals(UryuAnims.IDLE, f.anim());
	}

	@Test
	void aShorterDrawReleasesFromThePoseItHad() {
		UryuBowFlow f = flow();
		run(f, 0.3, true, false);
		run(f, 0.25, true, true);
		double drawTick = f.tick(now);
		run(f, DT, true, false);
		assertEquals(Phase.RELEASE, f.phase());
		assertEquals(drawTick, f.prevTick(), 1.0, "the frozen draw tick is the short draw, the blend starts from it");
		assertTrue(f.prevTick() < 20.0);
	}

	@Test
	void anAbilityGestureRunsItsAnimationThenGoesBackToIdle() {
		UryuBowFlow f = flow();
		run(f, 0.3, true, false);
		f.trigger("seele", 0, now);
		assertEquals(Phase.ABILITY, f.phase());
		assertEquals("seele", f.anim());
		assertEquals(UryuAnims.IDLE, f.prevAnim());
		run(f, 0.5, true, false);
		assertEquals(Phase.ABILITY, f.phase());
		assertEquals(10.0, f.tick(now), 0.8);
		run(f, 0.9, true, false);
		assertEquals(Phase.IDLE, f.phase(), "26 ticks = 1.3 s");
	}

	@Test
	void anAbilityThatStartsLateSkipsItsRaise() {
		UryuBowFlow f = flow();
		run(f, 0.3, true, false);
		f.trigger("licht_regen", 8, now);
		assertEquals(8.0, f.tick(now), 1e-9);
		run(f, 0.5, true, false);
		assertEquals(18.0, f.tick(now), 0.8);
	}

	@Test
	void theKeyHeldDuringAnAbilityDrawsAfterItEnds() {
		UryuBowFlow f = flow();
		run(f, 0.3, true, false);
		f.trigger("release_z", 0, now);
		run(f, 0.2, true, true);
		assertEquals(Phase.ABILITY, f.phase(), "the ability pose is not cut by the key");
		run(f, 0.6, true, true);
		assertEquals(Phase.DRAW, f.phase());
	}

	@Test
	void triggerIsIgnoredWhileTheBowIsNotHeld() {
		UryuBowFlow f = flow();
		run(f, 0.3, false, false);
		f.trigger("seele", 0, now);
		assertEquals(Phase.OFF, f.phase());
		assertEquals(UryuAnims.IDLE, f.anim());
	}

	@Test
	void switchingTheItemAwayInTheMiddleOfADrawResetsEverything() {
		UryuBowFlow f = flow();
		run(f, 0.3, true, false);
		run(f, 0.4, true, true);
		run(f, 0.5, false, false);
		assertEquals(Phase.OFF, f.phase());
		assertNull(f.prevAnim());
		run(f, 0.3, true, false);
		assertEquals(Phase.IDLE, f.phase(), "no release animation for a draw that was cancelled by the switch");
	}

	@Test
	void theCrossfadeIsASmoothstepOfTheBlendTime() {
		UryuBowFlow f = flow();
		run(f, 0.3, true, false);
		f.update(now += DT, true, true, 1.0);
		assertTrue(f.xfade(now) >= 0.0 && f.xfade(now) < 0.2);
		double mid = now + UryuBowFlow.XFADE * 0.5;
		assertEquals(0.5, f.xfade(mid), 1e-9);
		assertEquals(1.0, f.xfade(now + 1.0), 1e-9);
	}
}
