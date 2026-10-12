package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.fx.RukiaFxParams;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Round 6 (design/results/rukia_bankai_round6.md): the two Rukia bankai abilities are now different (ice spike, absolute zero) and
 * Byakuya's shikai has a third ability, the petal cage. Balance rules: the bankai pair together does less damage per second than the
 * Rukia shikai set, the cage less than the attack mode, and the costs and cooldowns differ.
 */
class Round6AbilityTest {
	private static final BalanceConfig CFG = BalanceConfig.defaults();
	private static final CharacterId R = CharacterId.RUKIA;
	private static final CharacterId B = CharacterId.BYAKUYA;

	private static AbilitySpec s(AbilityId a) {
		return CFG.spec(a);
	}

	/** HP per second of an ability that hits one target once per cast and is recast as soon as its cooldown ends. */
	private static double dps(AbilityId a, double hitsPerCast) {
		return s(a).num(AbilityParams.DAMAGE) * hitsPerCast / (s(a).cooldownTicks() / 20.0);
	}

	@Test
	void theTwoRukiaBankaiAbilitiesAreDifferentInEverything() {
		AbilitySpec spike = s(AbilityId.ICE_SPIKE);
		AbilitySpec zero = s(AbilityId.ABSOLUTE_ZERO);
		assertEquals(0, AbilityId.ICE_SPIKE.slot());
		assertEquals(1, AbilityId.ABSOLUTE_ZERO.slot());
		assertNotEquals(spike.effectId(), zero.effectId());
		assertNotEquals(spike.costTenths(), zero.costTenths());
		assertNotEquals(spike.cooldownTicks(), zero.cooldownTicks());
		assertNotEquals(spike.phaseOffsets(), zero.phaseOffsets());
		// different mechanics: a long narrow line against a big sphere with a delayed shatter
		assertTrue(spike.num(AbilityParams.LENGTH) >= 30.0 && spike.num(AbilityParams.HALF_WIDTH) <= 2.5);
		assertEquals(0.0, spike.num(AbilityParams.RADIUS), 0.0, "the spike has no area, only a line");
		assertTrue(zero.num(AbilityParams.RADIUS) >= 10.0);
		assertEquals(0.0, zero.num(AbilityParams.LENGTH), 0.0);
		assertTrue(zero.phaseOffsets().get(1) - zero.phaseOffsets().get(0) >= 60, "absolute zero holds the targets frozen for 3 s");
		assertTrue(zero.intNum(AbilityParams.STATUS_TICKS) >= 60, "frozen for at least 3 s");
		assertEquals("rukia.bankai.ice_spike", AbilityId.ICE_SPIKE.commandId);
		assertEquals(AbilityId.ICE_SPIKE, AbilityId.fromCommandId("rukia.bankai.ice_spike"));
	}

	@Test
	void absoluteZeroIsTheHeavyOneAndTheSpikeTheLightOne() {
		AbilitySpec spike = s(AbilityId.ICE_SPIKE);
		AbilitySpec zero = s(AbilityId.ABSOLUTE_ZERO);
		assertTrue(zero.costTenths() >= 400 && zero.costTenths() <= 600, "about half the bar");
		assertTrue(zero.cooldownTicks() >= 600, "a long cooldown: 30 s or more");
		assertTrue(spike.costTenths() >= 100 && spike.costTenths() <= 250);
		assertTrue(spike.cooldownTicks() >= 100 && spike.cooldownTicks() <= 300);
		assertTrue(zero.costTenths() > 2 * spike.costTenths());
		assertTrue(zero.cooldownTicks() > 3 * spike.cooldownTicks());
	}

	@Test
	void rukiaBankaiDamagePerSecondStaysBelowTheShikaiSet() {
		double shikai = dps(AbilityId.TSUKISHIRO, 1) + dps(AbilityId.HAKUREN, 1) + dps(AbilityId.SHIRAFUNE, 1);
		double bankai = dps(AbilityId.ICE_SPIKE, 1) + dps(AbilityId.ABSOLUTE_ZERO, 1);
		assertTrue(bankai <= shikai, "bankai pair " + bankai + " HP/s must not exceed the shikai set " + shikai + " HP/s");
		// and no single bankai ability beats the best single shikai one
		double best = Math.max(dps(AbilityId.TSUKISHIRO, 1), Math.max(dps(AbilityId.HAKUREN, 1), dps(AbilityId.SHIRAFUNE, 1)));
		assertTrue(dps(AbilityId.ICE_SPIKE, 1) <= best);
		assertTrue(dps(AbilityId.ABSOLUTE_ZERO, 1) <= best);
	}

	@Test
	void theCageHasItsOwnKeyEffectAndNumbers() {
		AbilitySpec cage = s(AbilityId.CAGE);
		assertEquals("byakuya.shikai.cage", AbilityId.CAGE.commandId);
		assertEquals(AbilityId.CAGE, AbilityId.fromCommandId("byakuya.shikai.cage"));
		assertEquals(ZanpakutoState.SHIKAI, AbilityId.CAGE.requiredState);
		assertEquals(B, AbilityId.CAGE.character);
		assertEquals(2, AbilityId.CAGE.slot(), "third shikai slot (key B by default)");
		assertEquals(AbilityId.CAGE, AbilityId.forSlot(B, ZanpakutoState.SHIKAI, 2));
		assertEquals(35, cage.effectId());
		assertTrue(cage.enabled());
		assertTrue(cage.costTenths() > 0 && cage.cooldownTicks() >= 200);
		assertTrue(cage.num(AbilityParams.RADIUS) >= 4.0 && cage.num(AbilityParams.AIM_RANGE) >= 15.0);
		assertTrue(cage.phaseOffsets().size() >= 4, "pulses");
		int slow = cage.intNum(AbilityParams.STATUS_TICKS);
		assertTrue(slow >= 40 && slow <= 80, "2 to 4 s of slowness, got " + slow);
		assertTrue(cage.intNum(AbilityParams.SLOW_AMP) >= 1);
		// the pulses are evenly spaced (the client draws one flash per pulse from the first gap)
		List<Integer> p = cage.phaseOffsets();
		for (int i = 2; i < p.size(); i++) {
			assertEquals(p.get(1) - p.get(0), p.get(i) - p.get(i - 1));
		}
	}

	@Test
	void cageDamagePerSecondStaysBelowTheAttackMode() {
		double pulses = s(AbilityId.CAGE).phaseOffsets().size();
		double cage = dps(AbilityId.CAGE, pulses);
		double attack = dps(AbilityId.MODE_ATTACK, 1);
		assertTrue(cage <= attack, "cage " + cage + " HP/s must not exceed the attack mode " + attack + " HP/s");
	}

	@Test
	void newAbilitiesUseUniqueCodesEffectsAndSlots() {
		assertEquals(10, AbilityId.ICE_SPIKE.code);
		assertEquals(11, AbilityId.CAGE.code);
		assertEquals(AbilityId.ICE_SPIKE, AbilityId.fromCode((byte) 10));
		assertEquals(AbilityId.CAGE, AbilityId.fromCode((byte) 11));
		assertTrue(EffectIds.FROST_FIELD != EffectIds.FROST_THAW);
		for (AbilityId a : AbilityId.values()) {
			assertNotEquals(EffectIds.FROST_FIELD, s(a).effectId());
			assertNotEquals(EffectIds.FROST_THAW, s(a).effectId());
		}
	}

	@Test
	void iceSpikeCastsInBankaiPaysItsCostAndSchedulesSixSegments() {
		Fx f = new Fx().toShikai(R).toBankai(R);
		f.adv(44);
		int before = f.sm.reiatsu().value();
		TransitionResult r = f.cast(AbilityId.ICE_SPIKE, R);
		assertTrue(r.ok(), String.valueOf(r.reason()));
		assertEquals(before - s(AbilityId.ICE_SPIKE).costTenths(), f.sm.reiatsu().value());
		assertEquals(24, Fx.events(r.events(), StateEvent.BroadcastEffect.class).get(0).effectId());
		assertEquals(6, Fx.events(r.events(), StateEvent.ScheduledPhase.class).size());
		assertEquals(s(AbilityId.ICE_SPIKE).cooldownTicks(), f.sm.cooldownRemaining(AbilityId.ICE_SPIKE));
		assertEquals(0, f.sm.cooldownRemaining(AbilityId.ABSOLUTE_ZERO), "the other bankai ability has its own cooldown");
	}

	@Test
	void absoluteZeroNeedsHalfTheBarAndHasItsLongCooldown() {
		Fx f = new Fx().toShikai(R).toBankai(R);
		f.adv(44);
		f.sm.devSetReiatsu(400);
		assertEquals(RejectReason.NOT_ENOUGH_REIATSU, f.cast(AbilityId.ABSOLUTE_ZERO, R).reason());
		f.sm.devSetReiatsu(1000);
		TransitionResult r = f.cast(AbilityId.ABSOLUTE_ZERO, R);
		assertTrue(r.ok(), String.valueOf(r.reason()));
		assertEquals(1000 - s(AbilityId.ABSOLUTE_ZERO).costTenths(), f.sm.reiatsu().value());
		assertEquals(800, f.sm.cooldownRemaining(AbilityId.ABSOLUTE_ZERO));
	}

	@Test
	void cageCastsInShikaiAndInsideTheByakuyaBankai() {
		Fx f = new Fx().toShikai(B);
		f.adv(12);
		int before = f.sm.reiatsu().value();
		TransitionResult r = f.cast(AbilityId.CAGE, B);
		assertTrue(r.ok(), String.valueOf(r.reason()));
		assertEquals(before - s(AbilityId.CAGE).costTenths(), f.sm.reiatsu().value());
		assertEquals(35, Fx.events(r.events(), StateEvent.BroadcastEffect.class).get(0).effectId());
		assertEquals(6, Fx.events(r.events(), StateEvent.ScheduledPhase.class).size());
		// not for the other zanpakuto, not while sealed
		Fx rukia = new Fx().toShikai(R);
		rukia.adv(12);
		assertEquals(RejectReason.NOT_IN_STATE, rukia.cast(AbilityId.CAGE, R).reason());
		Fx bankai = new Fx().toShikai(B).toBankai(B);
		bankai.adv(44);
		assertTrue(bankai.cast(AbilityId.CAGE, B).ok(), "shikai abilities stay available inside bankai");
	}

	@Test
	void iceSpikeParamsCarryTheStepsTheWidthAndTheFirstSegmentTime() {
		AbilitySpec sp = s(AbilityId.ICE_SPIKE);
		float[] base = {1f, 2f, 3f, (float) sp.num(AbilityParams.LENGTH)};
		float[] p = RukiaFxParams.extend(AbilityId.ICE_SPIKE, base, 20.0, sp.phaseOffsets().size(), 60, sp.num(AbilityParams.HALF_WIDTH),
				sp.phaseOffsets().get(1) - sp.phaseOffsets().get(0), sp.phaseOffsets().get(0));
		assertEquals(9, p.length);
		assertEquals(36f, p[3], 1e-4);
		assertEquals(20f, p[4], 1e-4, "the free ray length");
		assertEquals(6f, p[5], 1e-4);
		assertEquals(0.1f, p[6], 1e-4, "2 ticks per segment");
		assertEquals(1.5f, p[7], 1e-4);
		assertEquals(0.7f, p[8], 1e-4, "the first segment at tick 14");
		assertEquals(36f, RukiaFxParams.extend(AbilityId.ICE_SPIKE, base, 99.0, 6, 60, Double.NaN)[4], 1e-4, "never longer than the reach");
		assertTrue(RukiaFxParams.handles(AbilityId.ICE_SPIKE));
		assertEquals(AbilityId.ICE_SPIKE, RukiaFxParams.abilityOf(24));
	}
}
