package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * B4 step 4: bankai is usable. No reiatsu cost and no upkeep, a separate 45 s timer that falls back to SHIKAI, free bankai
 * abilities with cooldowns, shikai abilities as a superset inside bankai, and the scaled areas and damage numbers.
 */
class BankaiTest {
	private static final CharacterId R = CharacterId.RUKIA;
	private static final CharacterId B = CharacterId.BYAKUYA;
	private static final BalanceConfig CFG = BalanceConfig.defaults();

	private static Fx inBankai(CharacterId c) {
		Fx f = new Fx().toShikai(c).toBankai(c);
		f.adv(44); // settle lock
		return f;
	}

	@Test
	void entryCostsNothingAndNeedsNoFullBar() {
		// mech2 task 3: the "reiatsu must be full" rule is gone; only the Rage gate (RageGateTest) and the locks remain
		Fx f = new Fx().toShikai(R);
		f.adv(30);
		assertTrue(f.sm.reiatsu().value() < 1000);
		int before = f.sm.reiatsu().value();
		TransitionResult ok = f.tr(ZanpakutoState.BANKAI, R);
		assertTrue(ok.ok(), String.valueOf(ok.reason()));
		assertEquals(before, f.sm.reiatsu().value());
		assertFalse(Fx.has(ok.events(), StateEvent.ReiatsuSpent.class));
		assertEquals(ZanpakutoState.BANKAI, f.sm.state());
	}

	@Test
	void entryWorksAtAlmostEmptyBarAndKeepsTheTimerAndTheReentryLock() {
		for (CharacterId c : new CharacterId[] {R, B}) {
			Fx f = new Fx().toShikai(c);
			f.sm.devSetReiatsu(1); // the bar never goes to zero (R2); one tenth is as empty as it gets
			TransitionResult ok = f.tr(ZanpakutoState.BANKAI, c);
			assertTrue(ok.ok(), c + " " + ok.reason());
			assertEquals(1, f.sm.reiatsu().value(), "no cost");
			assertEquals(f.now() + CFG.bankaiCapTicks(), f.sm.snapshot().bankaiEndTick(), "45 s timer armed");
			f.adv(CFG.bankaiCapTicks());
			assertEquals(ZanpakutoState.SHIKAI, f.sm.state(), "the timer still ends it");
			// the 60 s re-entry lock still applies (not the bar)
			f.sm.devSetReiatsu(1000);
			f.adv(CFG.transitionLockTicks() + 1);
			assertEquals(RejectReason.RELEASE_LOCK, f.tr(ZanpakutoState.BANKAI, c).reason());
			f.adv(CFG.bankaiReentryTicks() - CFG.transitionLockTicks() - 1);
			assertTrue(f.tr(ZanpakutoState.BANKAI, c).ok(), "after 60 s the second bankai starts");
		}
	}

	@Test
	void noReiatsuDrainDuringTheWholeTimer() {
		Fx f = new Fx().toShikai(B).toBankai(B);
		f.sm.devSetReiatsu(500);
		int last = f.sm.reiatsu().value();
		for (int i = 0; i < 899; i++) {
			f.adv(1);
			int now = f.sm.reiatsu().value();
			assertTrue(now >= last, "reiatsu fell at tick " + i + ": " + last + " -> " + now);
			last = now;
		}
		assertEquals(ZanpakutoState.BANKAI, f.sm.state());
		assertEquals(1000, last, "the bar refilled to the top at +5 per batch");
	}

	@Test
	void timerRunsIndependentlyOfCastsAndEndsInShikai() {
		Fx f = inBankai(R);
		assertTrue(f.cast(AbilityId.ICE_WALL, R).ok());
		f.adv(840); // 44 + 840 = 884 since entry
		assertEquals(ZanpakutoState.BANKAI, f.sm.state());
		long endTick = f.sm.snapshot().bankaiEndTick();
		f.adv(15);
		assertEquals(ZanpakutoState.BANKAI, f.sm.state());
		List<StateEvent> ev = f.adv(1);
		assertEquals(endTick, f.now());
		assertEquals(ZanpakutoState.SHIKAI, f.sm.state());
		assertEquals(Trigger.BANKAI_CAP, Fx.lastChange(ev).trigger());
		assertEquals(R, f.sm.character());
		// back in shikai the shikai abilities work (after the 10 tick transition lock) and bankai abilities do not
		f.adv(10);
		assertTrue(f.cast(AbilityId.SHIRAFUNE, R).ok());
		f.adv(12);
		assertEquals(RejectReason.NOT_IN_STATE, f.cast(AbilityId.ICE_WALL, R).reason());
	}

	@Test
	void bankaiAbilitiesAreFreeEvenAtAlmostEmptyBar() {
		Fx f = inBankai(B);
		f.sm.devSetReiatsu(0);
		int prev = 0;
		for (AbilityId a : new AbilityId[] {AbilityId.SCATTER, AbilityId.HAKUTEIKEN}) {
			TransitionResult r = f.cast(a, B);
			assertTrue(r.ok(), a + " " + r.reason());
			assertFalse(Fx.has(r.events(), StateEvent.ReiatsuSpent.class));
			assertTrue(f.sm.reiatsu().value() <= prev + 10, "no reiatsu was spent by " + a);
			prev = f.sm.reiatsu().value();
			f.adv(12); // past the global cooldown (a little regeneration happens meanwhile)
			assertTrue(f.sm.reiatsu().value() >= prev);
			assertEquals(ZanpakutoState.BANKAI, f.sm.state(), "an empty bar does not end bankai");
		}
	}

	@Test
	void bankaiAbilitiesHaveCooldownsSoTheyCannotBeSpammed() {
		for (AbilityId a : AbilityId.values()) {
			AbilitySpec spec = CFG.spec(a);
			if (a.requiredState != ZanpakutoState.BANKAI || !spec.enabled()) {
				continue;
			}
			assertTrue(spec.cooldownTicks() >= 160, a + " cooldown " + spec.cooldownTicks() + " ticks is at least 8 s");
			Fx f = inBankai(a.character);
			assertTrue(f.cast(a, a.character).ok());
			int cd = spec.cooldownTicks();
			f.adv(cd - 1);
			RejectReason early = f.cast(a, a.character).reason();
			assertEquals(RejectReason.ON_COOLDOWN, early, a + " one tick early");
			assertEquals(1, f.sm.cooldownRemaining(a));
			f.adv(1);
			// the timer (45 s) may have ended by now for the longest cooldowns: only check where bankai still runs
			if (f.sm.state() == ZanpakutoState.BANKAI) {
				assertTrue(f.cast(a, a.character).ok(), a + " ready at the cooldown tick");
			}
		}
		// hammering one ability: exactly one cast per cooldown
		Fx f = inBankai(B);
		int accepted = 0;
		for (int i = 0; i < 200; i++) {
			if (f.cast(AbilityId.SCATTER, B).ok()) {
				accepted++;
			}
			f.adv(1);
		}
		assertEquals(1, accepted, "200 ticks of spamming give one cast (scatter cooldown 300)");
	}

	@Test
	void shikaiAbilitiesStayAvailableInsideBankaiWithTheirNormalCostsAndCooldowns() {
		for (AbilityId a : AbilityId.values()) {
			AbilitySpec spec = CFG.spec(a);
			if (a.requiredState != ZanpakutoState.SHIKAI || !spec.enabled()) {
				continue;
			}
			Fx f = inBankai(a.character);
			int before = f.sm.reiatsu().value();
			TransitionResult r = f.cast(a, a.character);
			assertTrue(r.ok(), a + " in bankai: " + r.reason());
			assertEquals(before - spec.costTenths(), f.sm.reiatsu().value(), a + " pays its normal cost");
			assertEquals(spec.cooldownTicks(), f.sm.cooldownRemaining(a), a + " normal cooldown");
			assertEquals(ZanpakutoState.BANKAI, f.sm.state(), "state is still bankai");
			assertEquals(spec.effectId(), Fx.events(r.events(), StateEvent.BroadcastEffect.class).get(0).effectId());
			// the other character's shikai ability is still not available
			AbilityId foreign = a.character == R ? AbilityId.MODE_ATTACK : AbilityId.TSUKISHIRO;
			f.adv(12);
			assertEquals(RejectReason.NOT_IN_STATE, f.cast(foreign, a.character).reason());
		}
	}

	@Test
	void shikaiAbilityInBankaiNeedsReiatsuAndRespectsTheGlobalCooldownAndBankaiAbilitiesStillWork() {
		Fx f = inBankai(R);
		f.sm.devSetReiatsu(200); // Tsukishiro costs 25.0
		assertEquals(RejectReason.NOT_ENOUGH_REIATSU, f.cast(AbilityId.TSUKISHIRO, R).reason());
		assertEquals(0, f.sm.cooldownRemaining(AbilityId.TSUKISHIRO), "a refused cast starts no cooldown");
		assertTrue(f.cast(AbilityId.ICE_WALL, R).ok(), "the ice wall (18.0) works with the same low bar");
		f.sm.devSetReiatsu(900);
		assertEquals(RejectReason.GCD, f.cast(AbilityId.HAKUREN, R).reason());
		f.adv(12);
		assertTrue(f.cast(AbilityId.HAKUREN, R).ok());
		f.adv(12);
		assertTrue(f.cast(AbilityId.SHIRAFUNE, R).ok());
	}

	@Test
	void castingShikaiAbilitiesDoesNotExtendTheBankaiTimer() {
		Fx f = inBankai(B);
		long end = f.sm.snapshot().bankaiEndTick();
		assertTrue(f.cast(AbilityId.MODE_ATTACK, B).ok());
		assertEquals(end, f.sm.snapshot().bankaiEndTick());
	}

	@Test
	void sealingInBankaiGoesToSealedAsBeforeAndReentryStaysLocked() {
		Fx f = inBankai(R);
		TransitionResult r = f.tr(ZanpakutoState.SEALED, R);
		assertTrue(r.ok());
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertEquals(f.now() + CFG.bankaiReentryTicks(), f.sm.bankaiLockEndTick());
	}

	@Test
	void deathClearsTheBankaiLock() {
		Fx f = new Fx().toShikai(R).toBankai(R);
		f.adv(900);
		assertTrue(f.sm.bankaiLockEndTick() > f.now());
		f.sm.onDeath();
		f.sm.onRespawn();
		assertEquals(0, f.sm.bankaiLockEndTick());
	}

	@Test
	void handLossDoesNotEndBankai() {
		Fx f = inBankai(R);
		f.sm.onHandChanged(CharacterId.NONE);
		f.adv(200);
		assertEquals(ZanpakutoState.BANKAI, f.sm.state(), "round 5: only seal, death, logout and the bankai cap end it");
	}

	// ------------------------------------------------------------------ scaled numbers (BalanceConfig, LOG "B4 step 4")

	private static double n(AbilityId a, String key) {
		return CFG.spec(a).num(key);
	}

	@Test
	void theRukiaBankaiTerritoryIsOneHeartEveryThreeSecondsAndSlownessOne() {
		// mech2 task 4: the passive frost territory replaces Absolute Zero; numbers: bankai.field.slowAmplifier / tickDamage / periodTicks
		BankaiFieldSpec f = CFG.bankaiField();
		assertEquals(0, f.slowAmplifier(), "Slowness I");
		assertEquals(2.0F, f.tickDamage(), 0.0F, "exactly one heart (2.0 HP)");
		assertEquals(60, f.periodTicks(), "every 3 s");
		assertEquals(20, f.refreshTicks(), "Slowness renewed every 20 ticks");
		assertTrue(f.slowTicks() > f.refreshTicks() && f.slowTicks() <= 60, "a short Slowness that outlives one refresh gap");
	}

	@Test
	void byakuyaBankaiHitsGroupsTwelveToSixteenBlocksOut() {
		AbilitySpec sc = CFG.spec(AbilityId.SCATTER);
		assertTrue(sc.num(AbilityParams.TORNADO_RADIUS) >= 12 && sc.num(AbilityParams.TORNADO_RADIUS) <= 16);
		assertTrue(sc.num(AbilityParams.RADIUS) >= 12 && sc.num(AbilityParams.RADIUS) <= 16);
		assertTrue(sc.intNum(AbilityParams.MAX_TARGETS) >= 32, "group damage: dozens of targets");
		assertTrue(sc.num(AbilityParams.DAMAGE) > 5.0);
		AbilitySpec hk = CFG.spec(AbilityId.HAKUTEIKEN);
		assertTrue(hk.num(AbilityParams.RADIUS) >= 10 && hk.num(AbilityParams.RADIUS) <= 16);
		assertTrue(hk.num(AbilityParams.LENGTH) > 20.0 && hk.num(AbilityParams.HALF_WIDTH) > 1.0);
		assertTrue(hk.intNum(AbilityParams.MAX_TARGETS) >= 32);
	}

	@Test
	void shikaiAbilityAreasAreOnePointFiveToTwoTimesTheOldOnes() {
		// old values: tsukishiro radius 4, hakuren range 12 / half width 2, shirafune reach 8, mode_attack radius 1.5
		double[][] cases = {
				{n(AbilityId.TSUKISHIRO, AbilityParams.RADIUS), 4.0},
				{n(AbilityId.HAKUREN, AbilityParams.LENGTH), 12.0},
				{n(AbilityId.HAKUREN, AbilityParams.HALF_WIDTH), 2.0},
				{n(AbilityId.SHIRAFUNE, AbilityParams.LENGTH), 8.0},
				{n(AbilityId.MODE_ATTACK, AbilityParams.RADIUS), 1.5}};
		for (double[] c : cases) {
			double ratio = c[0] / c[1];
			assertTrue(ratio >= 1.5 - 1e-9 && ratio <= 2.0 + 1e-9, c[0] + " / " + c[1] + " = " + ratio);
		}
	}

	@Test
	void hakurenPhasesCoverTheWholeLengthAndEveryAbilityHasItsCaps() {
		AbilitySpec h = CFG.spec(AbilityId.HAKUREN);
		assertEquals(8, h.phaseOffsets().size());
		assertEquals(20.0, h.num(AbilityParams.LENGTH));
		for (AbilityId a : AbilityId.values()) {
			AbilitySpec s = CFG.spec(a);
			if (!s.enabled() || a == AbilityId.MODE_BARRIER || a == AbilityId.ICE_WALL) {
				continue; // the barrier and (R11 T5) the ice wall deal no damage on purpose
			}
			assertTrue(s.params().containsKey(AbilityParams.DAMAGE) || s.params().containsKey(AbilityParams.DAMAGE2), a + " has damage");
			if (s.params().containsKey(AbilityParams.MAX_TARGETS)) {
				assertTrue(s.intNum(AbilityParams.MAX_TARGETS) <= 64, a + " target cap");
			}
		}
	}
}
