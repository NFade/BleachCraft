package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import org.junit.jupiter.api.Test;

class RukiaFxParamsTest {
	private static float[] base(float size) {
		return new float[] {1f, 2f, 3f, size};
	}

	@Test
	void tsukishiroGetsPillarHeightFromRadius() {
		float[] p = RukiaFxParams.extend(AbilityId.TSUKISHIRO, base(4f), 0);
		assertEquals(5, p.length);
		assertEquals(14f, p[4], 1e-4);
		// radius x1.5 (FIXES_B4 step 4): the pillar follows without a client change
		assertEquals(21f, RukiaFxParams.extend(AbilityId.TSUKISHIRO, base(6f), 0)[4], 1e-4);
	}

	@Test
	void hakurenStepsSplitTheLength() {
		float[] p = RukiaFxParams.extend(AbilityId.HAKUREN, base(12f), 0);
		assertEquals(6, p.length);
		assertEquals(2.4f, p[4], 1e-4);
		assertEquals(5f, p[5], 0);
		assertEquals(12.0, p[4] * p[5], 1e-3);
		assertEquals(2.4, RukiaFxParams.HAKUREN_STEP_LENGTH, 1e-9);
	}

	@Test
	void shirafuneUsedLengthIsClampedToTheReach() {
		assertEquals(5.5f, RukiaFxParams.extend(AbilityId.SHIRAFUNE, base(8f), 5.5)[4], 1e-4);
		assertEquals(8f, RukiaFxParams.extend(AbilityId.SHIRAFUNE, base(8f), 30.0)[4], 1e-4);
		assertEquals(0f, RukiaFxParams.extend(AbilityId.SHIRAFUNE, base(8f), -1.0)[4], 1e-4);
	}

	@Test
	void absoluteZeroCarriesThePauseSeconds() {
		float[] p = RukiaFxParams.extend(AbilityId.ABSOLUTE_ZERO, base(10f), 0);
		assertEquals(10f, p[3], 0);
		assertEquals(1.3f, p[4], 1e-4);
	}

	@Test
	void pauseMatchesTheServerPhaseTimes() {
		// the server shatters absolute zero at tick 66 after the freeze at tick 40 (BalanceConfig)
		var phases = BalanceConfig.defaults().spec(AbilityId.ABSOLUTE_ZERO).phaseOffsets();
		assertEquals(RukiaFxParams.ABSOLUTE_ZERO_PAUSE_TICKS, phases.get(1) - phases.get(0));
		// Hakuren: steps at ticks 20, 22, ..., 28 = 5 steps
		assertEquals(RukiaFxParams.HAKUREN_STEPS, BalanceConfig.defaults().spec(AbilityId.HAKUREN).phaseOffsets().size());
	}

	@Test
	void otherAbilitiesAreUntouched() {
		float[] b = base(1.5f);
		assertSame(b, RukiaFxParams.extend(AbilityId.MODE_ATTACK, b, 3));
		assertSame(b, RukiaFxParams.extend(AbilityId.HAKUTEIKEN, b, 3));
		assertNull(RukiaFxParams.extend(AbilityId.TSUKISHIRO, null, 0));
		float[] shortArr = {1f, 2f};
		assertSame(shortArr, RukiaFxParams.extend(AbilityId.TSUKISHIRO, shortArr, 0));
		assertFalse(RukiaFxParams.handles(AbilityId.SCATTER));
		assertTrue(RukiaFxParams.handles(AbilityId.ABSOLUTE_ZERO));
	}

	@Test
	void baseValuesAreKept() {
		float[] p = RukiaFxParams.extend(AbilityId.SHIRAFUNE, base(8f), 4);
		assertArrayEquals(new float[] {1f, 2f, 3f, 8f}, java.util.Arrays.copyOf(p, 4), 0);
	}

	@Test
	void effectIdsMapToAbilities() {
		assertEquals(AbilityId.TSUKISHIRO, RukiaFxParams.abilityOf(20));
		assertEquals(AbilityId.ABSOLUTE_ZERO, RukiaFxParams.abilityOf(23));
		assertNull(RukiaFxParams.abilityOf(30));
		for (AbilityId a : AbilityId.values()) {
			if (RukiaFxParams.handles(a)) {
				assertEquals(a, RukiaFxParams.abilityOf(BalanceConfig.defaults().spec(a).effectId()));
			}
		}
	}

	@Test
	void paramReadFallsBackToTheDefault() {
		float[] p = {1f, 2f, 3f, 4f};
		assertEquals(4.0, RukiaFxParams.param(p, 3, 9), 0);
		assertEquals(9.0, RukiaFxParams.param(p, 4, 9), 0);
		assertEquals(9.0, RukiaFxParams.param(null, 0, 9), 0);
	}

	@Test
	void fieldAnchorLivesOnlyInTheRukiaBankai() {
		assertTrue(FieldAnchorPolicy.spawnsOn(2));
		assertFalse(FieldAnchorPolicy.spawnsOn(4));
		assertFalse(FieldAnchorPolicy.spawnsOn(1));
		assertTrue(FieldAnchorPolicy.keep(true, true, CharacterId.RUKIA, ZanpakutoState.BANKAI));
		assertFalse(FieldAnchorPolicy.keep(true, true, CharacterId.RUKIA, ZanpakutoState.SHIKAI));
		assertFalse(FieldAnchorPolicy.keep(true, true, CharacterId.RUKIA, ZanpakutoState.SEALED));
		assertFalse(FieldAnchorPolicy.keep(true, true, CharacterId.BYAKUYA, ZanpakutoState.BANKAI));
		assertFalse(FieldAnchorPolicy.keep(false, true, CharacterId.RUKIA, ZanpakutoState.BANKAI));
		assertFalse(FieldAnchorPolicy.keep(true, false, CharacterId.RUKIA, ZanpakutoState.BANKAI));
	}

	@Test
	void checksRunEveryTenTicks() {
		assertFalse(FieldAnchorPolicy.due(0));
		assertFalse(FieldAnchorPolicy.due(9));
		assertTrue(FieldAnchorPolicy.due(10));
		assertTrue(FieldAnchorPolicy.due(40));
	}
}
