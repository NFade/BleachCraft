package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.fx.RukiaFxParams;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Round 6 (design/results/rukia_bankai_round6.md): Rukia's bankai had the ice spike (R11 T5 made it the ice wall; the second ability, Absolute Zero, was removed in
 * mech2 task 4) and Byakuya's shikai has a third ability, the petal cage. Balance rules: the bankai does less damage per second than the
 * Rukia shikai set, the cage less than the attack mode.
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
	void rukiaHasOneBankaiAbilityAndTheSecondSlotIsEmpty() {
		// mech2 task 4: Absolute Zero (key I) was the same effect as Tsukishiro and is gone; the frost territory is a passive (BankaiFieldSpec)
		AbilitySpec wall = s(AbilityId.ICE_WALL);
		assertEquals(0, AbilityId.ICE_WALL.slot());
		assertEquals(AbilityId.ICE_WALL, AbilityId.forSlot(R, ZanpakutoState.BANKAI, 0));
		assertNull(AbilityId.forSlot(R, ZanpakutoState.BANKAI, 1), "no second Rukia bankai ability");
		assertNull(AbilityId.forSlot(R, ZanpakutoState.BANKAI, 2));
		assertNull(AbilityId.fromCommandId("rukia.bankai.absolute_zero"));
		assertNull(AbilityId.fromCode((byte) 4), "code 4 stays unused");
		for (AbilityId a : AbilityId.values()) {
			if (a.character == R && a.requiredState == ZanpakutoState.BANKAI) {
				assertEquals(AbilityId.ICE_WALL, a, "the only Rukia bankai ability");
			}
		}
		// R11 T5 (variant W): a wall in front of her, no damage; 10 block reach, 14 ticks of cast time, 18 reiatsu, 14 s cooldown
		assertEquals(10.0, wall.num(AbilityParams.LENGTH), 0.0);
		assertEquals(180, wall.costTenths(), "18 reiatsu (it was 15 for the spike)");
		assertEquals(280, wall.cooldownTicks(), "14 s (it was 8 s for the spike)");
		assertEquals(List.of(14), wall.phaseOffsets(), "one release phase at tick 14");
		assertEquals(24, wall.effectId());
		assertEquals(0.0, wall.num(AbilityParams.DAMAGE), 0.0, "the wall deals no damage");
		assertEquals(0.0, wall.num(AbilityParams.RADIUS), 0.0);
		assertEquals("rukia.bankai.ice_wall", AbilityId.ICE_WALL.commandId);
		assertEquals(AbilityId.ICE_WALL, AbilityId.fromCommandId("rukia.bankai.ice_wall"));
	}

	@Test
	void rukiaBankaiDamagePerSecondStaysBelowTheShikaiSet() {
		double shikai = dps(AbilityId.TSUKISHIRO, 1) + dps(AbilityId.HAKUREN, 1) + dps(AbilityId.SHIRAFUNE, 1);
		double bankai = dps(AbilityId.ICE_WALL, 1);
		assertTrue(bankai <= shikai, "bankai ability " + bankai + " HP/s must not exceed the shikai set " + shikai + " HP/s");
		// and it does not beat the best single shikai one
		double best = Math.max(dps(AbilityId.TSUKISHIRO, 1), Math.max(dps(AbilityId.HAKUREN, 1), dps(AbilityId.SHIRAFUNE, 1)));
		assertTrue(dps(AbilityId.ICE_WALL, 1) <= best);
		assertEquals(0.0, bankai, 0.0, "the ice wall does no damage at all");
		// the passive territory adds one heart per 3 s per enemy: 0.67 HP/s, still below any single shikai ability
		BankaiFieldSpec f = CFG.bankaiField();
		double field = f.tickDamage() / (f.periodTicks() / 20.0);
		assertTrue(field + dps(AbilityId.ICE_WALL, 1) <= shikai, "bankai total with the field " + (field + dps(AbilityId.ICE_WALL, 1)) + " HP/s");
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
		assertEquals(10, AbilityId.ICE_WALL.code);
		assertEquals(11, AbilityId.CAGE.code);
		assertEquals(AbilityId.ICE_WALL, AbilityId.fromCode((byte) 10));
		assertEquals(AbilityId.CAGE, AbilityId.fromCode((byte) 11));
		assertTrue(EffectIds.FROST_FIELD != EffectIds.FROST_THAW);
		for (AbilityId a : AbilityId.values()) {
			assertNotEquals(EffectIds.FROST_FIELD, s(a).effectId());
			assertNotEquals(EffectIds.FROST_THAW, s(a).effectId());
		}
	}

	@Test
	void iceWallCastsInBankaiPaysItsCostAndSchedulesOneReleasePhase() {
		Fx f = new Fx().toShikai(R).toBankai(R);
		f.adv(44);
		int before = f.sm.reiatsu().value();
		TransitionResult r = f.cast(AbilityId.ICE_WALL, R);
		assertTrue(r.ok(), String.valueOf(r.reason()));
		assertEquals(before - s(AbilityId.ICE_WALL).costTenths(), f.sm.reiatsu().value());
		assertEquals(24, Fx.events(r.events(), StateEvent.BroadcastEffect.class).get(0).effectId());
		assertEquals(1, Fx.events(r.events(), StateEvent.ScheduledPhase.class).size());
		assertEquals(14, Fx.events(r.events(), StateEvent.ScheduledPhase.class).get(0).offsetTicks());
		assertEquals(s(AbilityId.ICE_WALL).cooldownTicks(), f.sm.cooldownRemaining(AbilityId.ICE_WALL));
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
	void iceWallParamsCarryTheReachTheFreeRayAndTheReleaseTime() {
		AbilitySpec sp = s(AbilityId.ICE_WALL);
		float[] base = {1f, 2f, 3f, (float) sp.num(AbilityParams.LENGTH)};
		float[] p = RukiaFxParams.extend(AbilityId.ICE_WALL, base, 6.0, sp.phaseOffsets().size(), Double.NaN, sp.phaseOffsets().get(0));
		assertEquals(6, p.length);
		assertEquals(10f, p[3], 1e-4);
		assertEquals(6f, p[4], 1e-4, "the free ray length");
		assertEquals(0.7f, p[5], 1e-4, "the wall appears at tick 14");
		assertEquals(10f, RukiaFxParams.extend(AbilityId.ICE_WALL, base, 99.0, 1, Double.NaN)[4], 1e-4, "never longer than the reach");
		assertEquals(0.7f, RukiaFxParams.extend(AbilityId.ICE_WALL, base, 5.0, 1, Double.NaN)[5], 1e-4, "the default release time is tick 14");
		assertTrue(RukiaFxParams.handles(AbilityId.ICE_WALL));
		assertEquals(AbilityId.ICE_WALL, RukiaFxParams.abilityOf(24));
	}
}
