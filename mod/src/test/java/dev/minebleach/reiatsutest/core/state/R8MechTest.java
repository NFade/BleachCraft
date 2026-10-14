package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.kido.KidoConfig;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** r8 mechanics round: the config knobs in BalanceConfig / KidoConfig and the cast data the server glue reads. */
class R8MechTest {
	private static final CharacterId R = CharacterId.RUKIA;

	@Test
	void everyNewKnobHasADocumentedDefault() {
		BalanceConfig c = BalanceConfig.defaults();
		assertEquals(0.30, c.abilityBlockReduction(), 1e-9);
		assertEquals(5.0, c.meleeHitReiatsu(), 1e-9, "R11 T2: was 1.0");
		assertEquals(50, c.meleeHitTenths());
		assertEquals(10.0, c.parryReiatsu(), 1e-9, "R11 T2: a parry restores reiatsu");
		assertEquals(100, c.parryTenths());
		assertEquals(150, c.meleeHitMaxPerSecondTenths());
		assertEquals(5, c.meleeHitTargetGapTicks());
		assertEquals(10, c.parryGapTicks());
		assertEquals(4, c.byakuyaAttackTickInterval());
		assertEquals(3.5, c.barrierPushRadius(), 1e-9);
		assertTrue(c.barrierPushStrength() > 0.0);
		assertTrue(c.barrierPushUp() > 0.0 && c.barrierPushUp() < 0.42, "a small hop, not a jump");
		assertEquals(2, c.barrierPushIntervalTicks());
		// the old numbers are untouched
		assertEquals(80, c.barrierReductionPercent());
		assertTrue(c.barrierControlImmune());
	}

	@Test
	void theKnobsSurviveTheCopyConstructorsUsedByTests() {
		MechSpec custom = new MechSpec(0.5, 2.5, 8, 5.0, 1.0, 0.1, 4);
		BalanceConfig c = BalanceConfig.defaults().withMech(custom);
		assertEquals(0.5, c.abilityBlockReduction(), 1e-9);
		assertEquals(25, c.meleeHitTenths(), "2.5 points are 25 tenths");
		assertEquals(8, c.byakuyaAttackTickInterval());
		// every other copy keeps them
		assertEquals(25, c.withBarrier(0, 50, false).meleeHitTenths());
		assertEquals(25, c.withRates(new java.util.EnumMap<>(ZanpakutoState.class)).meleeHitTenths());
		assertEquals(25, c.withBankaiField(BankaiFieldSpec.defaults()).meleeHitTenths());
		assertEquals(80, c.withMech(custom).barrierReductionPercent());
	}

	@Test
	void onlyShirafuneIsAimedAtItsRelease() {
		BalanceConfig c = BalanceConfig.defaults();
		for (AbilityId a : AbilityId.values()) {
			AbilitySpec spec = c.spec(a);
			assertEquals(a == AbilityId.SHIRAFUNE || a == AbilityId.LICHT_REGEN /* Uryu round 2: the rain falls where he aims at the end of the raised bow */, spec.aimsAtRelease(), a.name());
		}
		AbilitySpec s = c.spec(AbilityId.SHIRAFUNE);
		assertEquals(List.of(12), s.phaseOffsets(), "the cast time is unchanged: 0.6 s");
		assertEquals(0, s.releasePhase(), "the single phase is the release");
		assertEquals(14.0, s.num(AbilityParams.LENGTH), "reach and damage are unchanged");
		assertEquals(3.5, s.num(AbilityParams.DAMAGE));
	}

	@Test
	void anAbilityWithoutPhasesCanNeverBeAimedAtARelease() {
		AbilitySpec bogus = new AbilitySpec(AbilityId.SHIRAFUNE, 0, 20, 99, true, List.of(), java.util.Map.of(AbilityParams.AIM_AT_RELEASE, 1.0));
		assertFalse(bogus.aimsAtRelease());
	}

	@Test
	void shirafuneCastStillBroadcastsOnceAndSchedulesTheRelease() {
		// the machine is unchanged: the server glue decides to send the broadcast from the release phase for aimsAtRelease abilities
		Fx f = new Fx().toShikai(R);
		TransitionResult r = f.cast(AbilityId.SHIRAFUNE, R);
		assertTrue(r.ok());
		long broadcasts = r.events().stream().filter(e -> e instanceof StateEvent.BroadcastEffect).count();
		assertEquals(1, broadcasts);
		StateEvent.ScheduledPhase ph = r.events().stream().filter(e -> e instanceof StateEvent.ScheduledPhase)
				.map(e -> (StateEvent.ScheduledPhase) e).findFirst().orElseThrow();
		assertEquals(12, ph.offsetTicks());
		assertEquals(0, ph.phaseId());
	}

	@Test
	void kidoDamageIsHalved() {
		KidoConfig k = KidoConfig.defaults();
		assertEquals(1.25, k.spec(KidoId.BYAKURAI).damage(), 1e-9, "was 2.5");
		assertEquals(1.0, k.spec(KidoId.SHAKKAHO).damage(), 1e-9, "R11 T4: was 2.0 after r8, 4.0 before");
		assertEquals(0.0, k.spec(KidoId.SAI).damage(), 1e-9, "Sai never had damage");
		// everything else of the spells is untouched
		assertEquals(30.0, k.spec(KidoId.BYAKURAI).range());
		assertEquals(3.5, k.spec(KidoId.SHAKKAHO).radius());
		assertNotNull(k.spec(KidoId.SAI));
	}

	@Test
	void barrierPushDoesNotChangeTheBarrierItself() {
		// the dome still cuts 80 percent for 5 s and stays control immune; the push is extra (server/BarrierShove)
		Fx f = new Fx().toShikai(CharacterId.BYAKUYA);
		assertTrue(f.cast(AbilityId.MODE_BARRIER, CharacterId.BYAKUYA).ok());
		assertTrue(f.sm.barrierActive());
		assertEquals(20, f.sm.absorbBarrier(100));
		f.adv(100);
		assertFalse(f.sm.barrierActive(), "when the dome ends the push ends with it (the glue asks barrierActive)");
	}
}
