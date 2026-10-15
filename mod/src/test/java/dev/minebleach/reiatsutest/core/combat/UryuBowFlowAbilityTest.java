package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.combat.UryuBowFlow.Phase;
import org.junit.jupiter.api.Test;

/** The ability events of {@link UryuBowFlow#onAbility} (cast start and release event of Licht Regen / Sternenstaub) and {@link UryuBowFlow#preEvent}. */
class UryuBowFlowAbilityTest {
	private double now = 100.0;

	private UryuBowFlow holding() {
		UryuBowFlow f = new UryuBowFlow(UryuTestData::endTick);
		advance(f, 0.5);
		return f;
	}

	private void advance(UryuBowFlow f, double seconds) {
		int n = (int) Math.round(seconds * 60.0);
		for (int i = 0; i < n; i++) {
			now += 1.0 / 60.0;
			f.update(now, true, false, 0.0);
		}
	}

	private static UryuAnims.Meta rain() {
		return UryuAnims.meta("licht_regen");
	}

	@Test
	void anAbilityWithoutACastStartStartsAtItsStartTick() {
		UryuBowFlow f = holding();
		assertTrue(f.onAbility(rain(), false, now));
		assertEquals(Phase.ABILITY, f.phase());
		assertEquals("licht_regen", f.anim());
		assertEquals(rain().startTick(), f.tick(now), 1e-9);
	}

	@Test
	void theCastStartStartsFromTheFirstTickAndTheEventDoesNotRestartIt() {
		UryuBowFlow f = holding();
		assertTrue(f.onAbility(rain(), true, now));
		assertEquals(0.0, f.tick(now), 1e-9);
		advance(f, 0.3);
		double before = f.tick(now);
		assertTrue(before > 4.0, "tick " + before);
		assertFalse(f.onAbility(rain(), false, now), "the release event of the same ability runs on");
		assertEquals(before, f.tick(now), 1e-9);
		// the next event of that ability (no cast start before it) starts at the start tick again
		assertTrue(f.onAbility(rain(), false, now));
		assertEquals(rain().startTick(), f.tick(now), 1e-9);
	}

	@Test
	void aStaleCastStartDoesNotSwallowTheEvent() {
		UryuBowFlow f = holding();
		f.onAbility(rain(), true, now);
		now += UryuBowFlow.CAST_START_WINDOW + 0.5;
		f.update(now, true, false, 0.0);
		assertTrue(f.onAbility(rain(), false, now));
		assertEquals(rain().startTick(), f.tick(now), 1e-9);
	}

	@Test
	void anotherAbilityEventIsNotSwallowedByTheCastStart() {
		UryuBowFlow f = holding();
		f.onAbility(rain(), true, now);
		advance(f, 0.2);
		assertTrue(f.onAbility(UryuAnims.meta("seele"), false, now));
		assertEquals("seele", f.anim());
	}

	@Test
	void anUnknownAbilityIsIgnored() {
		UryuBowFlow f = holding();
		assertFalse(f.onAbility(null, false, now));
		assertEquals(Phase.IDLE, f.phase());
	}

	@Test
	void preEventRunsFromZeroToOneOnlyForAbilitiesWithACastStart() {
		UryuBowFlow f = holding();
		assertEquals(-1.0, f.preEvent(now), "idle");
		f.onAbility(rain(), true, now);
		assertEquals(0.0, f.preEvent(now), 1e-9);
		advance(f, 0.3);
		double k = f.preEvent(now);
		assertTrue(k > 0.3 && k < 1.0, "k=" + k);
		advance(f, 0.5);
		assertEquals(-1.0, f.preEvent(now), "past the event tick");
		// Seele and the shots have no raise before their event
		UryuBowFlow g = holding();
		g.onAbility(UryuAnims.meta("seele"), false, now);
		assertEquals(-1.0, g.preEvent(now));
		UryuBowFlow h = holding();
		h.onAbility(UryuAnims.meta("sternenstaub"), true, now);
		assertTrue(h.preEvent(now) >= 0.0);
	}
}
