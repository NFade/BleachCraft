package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilityParams;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.swarm.FlightConfig;
import dev.minebleach.reiatsutest.core.swarm.HitCooldown;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** r8 item 2: the per victim immunity of the Byakuya attack mode (Z), and the new maximum damage of one cast on one victim. */
class AbilityHitGateTest {
	@Test
	void defaultIntervalIsFourTicks() {
		assertEquals(4, BalanceConfig.defaults().byakuyaAttackTickInterval()); // 0.2 s
		assertEquals(4, new AbilityHitGate(BalanceConfig.defaults().byakuyaAttackTickInterval()).intervalTicks());
	}

	@Test
	void aVictimIsImmuneForTheIntervalAfterEachTick() {
		AbilityHitGate g = new AbilityHitGate(4);
		assertTrue(g.tryHit(7, 100), "a never hit victim is ready");
		assertFalse(g.ready(7, 101));
		assertFalse(g.ready(7, 103));
		assertTrue(g.ready(7, 104), "the window is exactly 4 ticks");
		assertTrue(g.tryHit(7, 104));
		assertFalse(g.tryHit(7, 107));
		assertEquals(1, g.victims());
	}

	@Test
	void victimsDoNotShareAWindow() {
		AbilityHitGate g = new AbilityHitGate(4);
		assertTrue(g.tryHit(1, 100));
		assertTrue(g.tryHit(2, 100), "another victim is hit in the same tick");
		assertTrue(g.tryHit(3, 101));
		assertFalse(g.tryHit(1, 102));
		assertEquals(3, g.victims());
	}

	@Test
	void aRefusedTryDoesNotRestartTheWindow() {
		AbilityHitGate g = new AbilityHitGate(4);
		assertTrue(g.tryHit(1, 100));
		assertFalse(g.tryHit(1, 102));
		assertTrue(g.tryHit(1, 104), "the refused try at 102 did not move the window");
	}

	@Test
	void oneInstancePerCastKeepsCastsApart() {
		// the gate lives with the cast: a second cast (new instance) starts clean, other abilities have their own gates
		AbilityHitGate first = new AbilityHitGate(4);
		AbilityHitGate second = new AbilityHitGate(4);
		assertTrue(first.tryHit(1, 100));
		assertTrue(second.tryHit(1, 101));
	}

	@Test
	void intervalIsAtLeastOneTick() {
		AbilityHitGate g = new AbilityHitGate(0);
		assertEquals(1, g.intervalTicks());
		assertTrue(g.tryHit(1, 5));
		assertTrue(g.tryHit(1, 6));
		assertFalse(g.tryHit(1, 6), "never twice in one tick");
	}

	// ------------------------------------------------------------------ the maximum damage of one cast

	/**
	 * Worst case for one victim that stands in the whole flight: every tick from 3 to 42 the head sweeps it, and it is inside the
	 * landing sphere. The model is the server's: the landing burst phases run first in a tick, then the flight sweep; the flight has
	 * its own HitCooldown (FlightConfig defaults 8 ticks / 6 hits, 4 ticks on a leg change), a landing hit restarts it (markOther).
	 * {@code outLegEnd} is the tick the head turns back (18 when the key is not held, 32 when held the whole time).
	 */
	private static double maxDamage(int interval, int outLegEnd) {
		FlightConfig fc = FlightConfig.defaults();
		BalanceConfig b = BalanceConfig.defaults();
		var spec = b.spec(AbilityId.MODE_ATTACK);
		double landing = spec.num(AbilityParams.DAMAGE);
		HitCooldown cd = new HitCooldown(fc.hitCooldownTicks(), fc.maxHitsPerTarget());
		AbilityHitGate gate = new AbilityHitGate(interval);
		Set<Integer> landingTicks = Set.copyOf(spec.phaseOffsets());
		double total = 0;
		for (int t = 0; t <= 42; t++) {
			if (landingTicks.contains(t) && gate.tryHit(1, t)) {
				total += landing;
				cd.markOther(1, t);
			}
			if (t >= fc.startDelayTicks()) {
				int leg = t < outLegEnd ? HitCooldown.LEG_OUT : HitCooldown.LEG_BACK;
				if (cd.ready(1, t, leg) && gate.ready(1, t)) {
					cd.tryHit(1, t, leg);
					gate.record(1, t);
					total += fc.hitDamage();
				}
			}
		}
		return total;
	}

	@Test
	void landingPhasesAndDamagePerTickAreUnchanged() {
		var spec = BalanceConfig.defaults().spec(AbilityId.MODE_ATTACK);
		assertEquals(1.0, spec.num(AbilityParams.DAMAGE), "landing tick: kept");
		assertEquals(List.of(10, 12, 14, 20), spec.phaseOffsets(), "landing phases: kept");
		assertEquals(0.5, FlightConfig.defaults().hitDamage(), "flight tick: kept");
	}

	@Test
	void oneCastOnOneVictimNowDealsAtMostFiveHp() {
		double before = Math.max(maxDamage(1, 18), maxDamage(1, 32)); // no gate: the 6.5 HP (3.25 hearts) the user saw
		double after = Math.max(maxDamage(4, 18), maxDamage(4, 32));
		assertEquals(6.5, before, 1e-9);
		assertEquals(5.0, after, 1e-9);
		assertEquals(4.5, maxDamage(4, 18), 1e-9, "key released early: 2 landing ticks and 5 flight ticks");
		assertEquals(5.0, maxDamage(4, 32), 1e-9, "key held: 3 landing ticks and 4 flight ticks");
		assertTrue(after < before);
	}

	@Test
	void aLongerWindowCutsFurther() {
		// the knob works in the expected direction (numbers for tuning in design/results/r8_mech.md)
		assertTrue(maxDamage(6, 32) < maxDamage(4, 32));
		assertTrue(maxDamage(8, 32) < maxDamage(6, 32));
	}
}
