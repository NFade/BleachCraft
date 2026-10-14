package dev.minebleach.reiatsutest.core.rage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** R11 T3: the reworked Rage economy (new defaults, anti-farm rules, the config section). Times to shikai and bankai are in {@link RageSimulatorTest}. */
class RageR11Test {
	private final RageConfig cfg = RageConfig.defaults();

	private RageMeter meter() {
		return new RageMeter(cfg);
	}

	// ------------------------------------------------------------------ defaults

	@Test
	void theR11Defaults() {
		assertEquals(1000, cfg.maxTenths());
		assertEquals(0, cfg.damageTenthsPerHp(), "damage taken gives nothing");
		assertEquals(2, cfg.dealtTenthsPerHp());
		assertEquals(25, cfg.maxDealtGainPerHitTenths());
		assertEquals(40, cfg.parryGainTenths());
		assertEquals(20, cfg.parryGapTicks());
		assertEquals(20, cfg.abilityHitTenths());
		assertEquals(25, cfg.mobMultiplierPercent());
		assertEquals(0, cfg.passiveTenths(), "no passive inflow");
		assertEquals(80, cfg.targetCapPer10sTenths());
		assertEquals(400, cfg.bankaiMinShikaiTicks());
		assertEquals(400, cfg.shikaiCostTenths());
		assertEquals(800, cfg.bankaiCostTenths());
		assertEquals(160, cfg.decayDelayTicks(), "drain unchanged: 0.5 per second after 8 s");
		assertEquals(5, cfg.decayTenths());
	}

	// ------------------------------------------------------------------ damage taken

	@Test
	void damageTakenGivesNothingButStillCountsAsCombat() {
		RageMeter m = meter();
		assertEquals(0, m.onDamageTaken(8.0F, 100));
		assertEquals(0, m.value());
		assertEquals(100, m.lastCombatTick(), "the hit still resets the out-of-combat timer");
		m.devSet(100, 100);
		m.onDamageTaken(8.0F, 150);
		m.tick(150 + cfg.decayDelayTicks() - 1);
		assertEquals(100, m.value(), "no drain inside the delay after a hit taken");
	}

	@Test
	void aMaximumHitTakenStillGivesZero() {
		RageMeter m = meter();
		assertEquals(0, m.onDamageTaken(1000.0F, 5));
		assertEquals(0, m.value());
	}

	@Test
	void damageTakenCanBeSwitchedBackOnInTheConfig() {
		RageConfig c = RageConfig.fromRoot(JsonParser.parseString("{\"rage\":{\"damageTenthsPerHp\":10,\"maxGainPerHitTenths\":30}}").getAsJsonObject());
		RageMeter m = new RageMeter(c);
		assertEquals(30, m.onDamageTaken(6.0F, 1), "6 hp * 1.0 = 6 points would be 60 tenths; the per hit cap 30 wins");
	}

	// ------------------------------------------------------------------ damage dealt

	@Test
	void aSixHpSwordHitOnAPlayerGivesOnePointTwo() {
		RageMeter m = meter();
		assertEquals(12, m.onDamageDealt(6.0F, 7, false, 0));
	}

	@Test
	void aHugeHitIsCappedAtTwoPointFive() {
		RageMeter m = meter();
		assertEquals(25, m.onDamageDealt(40.0F, 7, false, 0));
	}

	@Test
	void aHitOnAMobGivesAQuarter() {
		RageMeter m = meter();
		assertEquals(3, m.onDamageDealt(6.0F, 7, true, 0), "12 tenths * 25 percent = 3");
		assertEquals(6, m.onDamageDealt(40.0F, 8, true, 0), "capped 25 tenths * 25 percent = 6 (rounded)");
	}

	@Test
	void theMobShareFollowsTheConfig() {
		RageConfig c = new RageConfig(1000, 0, 250, 40, 2, 25, 160, 20, 5, 0, 10, 120, 400, 800, 20, 20, 100, 80, 400);
		assertEquals(12, new RageMeter(c).onDamageDealt(6.0F, 1, true, 0), "100 percent: mobs count like players");
		RageConfig zero = new RageConfig(1000, 0, 250, 40, 2, 25, 160, 20, 5, 0, 10, 120, 400, 800, 20, 20, 0, 80, 400);
		assertEquals(0, new RageMeter(zero).onDamageDealt(6.0F, 1, true, 0), "0 percent: mobs give nothing");
	}

	@Test
	void oneTargetPaysAtMostEightPointsPerTenSeconds() {
		RageMeter m = meter();
		int total = 0;
		for (int t = 0; t < 200; t += 5) {
			total += m.onDamageDealt(6.0F, 42, false, t); // 40 hits in 10 s
		}
		assertEquals(80, total, "the cap of one target in a 10 s window");
		assertEquals(0, m.onDamageDealt(6.0F, 42, false, 199), "window still full");
	}

	@Test
	void theTargetCapIsPerTargetAndSlides() {
		RageMeter m = meter();
		for (int t = 0; t < 100; t += 5) {
			m.onDamageDealt(6.0F, 1, false, t);
		}
		assertEquals(80, m.value(), "target 1 filled its window");
		assertEquals(12, m.onDamageDealt(6.0F, 2, false, 100), "another target pays in full");
		assertEquals(12, m.onDamageDealt(6.0F, 1, false, 205), "after 10 s the first gains of target 1 have left the window");
	}

	@Test
	void theTargetCapCutsAHitToWhatIsLeft() {
		RageMeter m = meter();
		for (int i = 0; i < 6; i++) {
			m.onDamageDealt(6.0F, 1, false, i * 10); // 6 x 12 = 72
		}
		assertEquals(8, m.onDamageDealt(6.0F, 1, false, 61), "only 8 tenths are left of the 80");
		assertEquals(0, m.onDamageDealt(6.0F, 1, false, 62));
	}

	@Test
	void aBlockedZeroHitGivesNothingAndNaNIsHarmless() {
		RageMeter m = meter();
		assertEquals(0, m.onDamageDealt(0.0F, 1, false, 0));
		assertEquals(0, m.onDamageDealt(Float.NaN, 1, false, 0));
		assertEquals(0, m.onDamageDealt(-3.0F, 1, false, 0));
		assertEquals(0, m.value());
	}

	// ------------------------------------------------------------------ parry

	@Test
	void aParryGivesFourPoints() {
		RageMeter m = meter();
		assertEquals(40, m.onParry(1000));
		assertEquals(40, m.value());
	}

	@Test
	void parriesPayAtMostOncePerTwentyTicks() {
		RageMeter m = meter();
		assertEquals(40, m.onParry(1000));
		assertEquals(0, m.onParry(1005), "inside the gap");
		assertEquals(0, m.onParry(1019));
		assertEquals(40, m.onParry(1020), "gap over");
		assertEquals(80, m.value());
	}

	@Test
	void aSkippedParryStillCountsAsCombat() {
		RageMeter m = meter();
		m.onParry(1000);
		m.onParry(1010);
		assertEquals(1010, m.lastCombatTick());
	}

	@Test
	void theFirstParryPaysEvenAtTickZero() {
		assertEquals(40, meter().onParry(0));
	}

	// ------------------------------------------------------------------ abilities

	@Test
	void anAbilityCastPaysOnceHoweverManyTargetsItHits() {
		RageMeter m = meter();
		m.armAbility(100);
		assertEquals(20, m.onAbilityHit(false, 101));
		assertEquals(0, m.onAbilityHit(false, 101), "second target of the same cast");
		assertEquals(0, m.onAbilityHit(false, 150), "later pulse of the same cast");
		assertEquals(20, m.value());
	}

	@Test
	void everyCastPaysItsOwnFirstHit() {
		RageMeter m = meter();
		m.armAbility(100);
		assertEquals(20, m.onAbilityHit(false, 102));
		m.armAbility(300);
		assertEquals(20, m.onAbilityHit(false, 302));
		assertEquals(40, m.value());
	}

	@Test
	void aHitWithoutACastPaysNothing() {
		RageMeter m = meter();
		assertEquals(0, m.onAbilityHit(false, 100));
		assertEquals(0, m.value());
	}

	@Test
	void aCastThatNeverHitsExpires() {
		RageMeter m = meter();
		m.armAbility(0);
		assertEquals(0, m.onAbilityHit(false, RageMeter.ABILITY_ARM_TICKS + 1), "30 s later it is a different fight");
		m.armAbility(1000);
		assertEquals(20, m.onAbilityHit(false, 1000 + RageMeter.ABILITY_ARM_TICKS));
	}

	@Test
	void manyCastsWithoutHitsDoNotStoreUpAHugeReward() {
		RageMeter m = meter();
		for (int i = 0; i < 20; i++) {
			m.armAbility(i);
		}
		int total = 0;
		for (int i = 0; i < 20; i++) {
			total += m.onAbilityHit(false, 30);
		}
		assertTrue(total <= 3 * 20, "at most three casts wait: " + total);
	}

	@Test
	void anAbilityHitOnAMobGivesAQuarterOfTheReward() {
		RageMeter m = meter();
		m.armAbility(0);
		assertEquals(5, m.onAbilityHit(true, 1));
	}

	@Test
	void withTheRewardSwitchedOffCastsArmNothing() {
		RageConfig c = new RageConfig(1000, 0, 250, 40, 2, 25, 160, 20, 5, 0, 10, 120, 400, 800, 20, 0, 25, 80, 400);
		RageMeter m = new RageMeter(c);
		m.armAbility(0);
		assertEquals(0, m.onAbilityHit(false, 1));
	}

	// ------------------------------------------------------------------ passive and drain

	@Test
	void noPassiveInflowEvenInAFight() {
		RageMeter m = meter();
		m.onDamageDealt(6.0F, 1, false, 0);
		int v = m.value();
		for (int t = 1; t < 160; t++) {
			m.tick(t);
		}
		assertEquals(v, m.value(), "the bar only moves by hits and by the drain");
	}

	@Test
	void theDrainIsUnchanged() {
		RageMeter m = meter();
		m.onParry(0);
		assertEquals(40, m.value());
		m.tick(159);
		assertEquals(40, m.value());
		m.tick(160);
		assertEquals(35, m.value());
		m.tick(180);
		assertEquals(30, m.value());
	}

	// ------------------------------------------------------------------ reload and reset

	@Test
	void setConfigKeepsTheValueAndSwitchesTheRules() {
		RageMeter m = meter();
		m.onParry(0);
		RageConfig stronger = new RageConfig(1000, 0, 250, 100, 2, 25, 160, 20, 5, 0, 10, 120, 400, 800, 20, 20, 25, 80, 400);
		m.setConfig(stronger);
		assertEquals(40, m.value());
		assertEquals(100, m.onParry(100));
		assertEquals(140, m.value());
	}

	@Test
	void resetClearsTheGapsTheCapsAndTheArmedCasts() {
		RageMeter m = meter();
		m.onParry(10);
		for (int t = 0; t < 100; t += 5) {
			m.onDamageDealt(6.0F, 3, false, t);
		}
		m.armAbility(5);
		m.reset();
		assertEquals(0, m.value());
		assertEquals(40, m.onParry(11), "the parry gap is gone");
		assertEquals(12, m.onDamageDealt(6.0F, 3, false, 12), "the target window is gone");
		assertEquals(0, m.onAbilityHit(false, 13), "armed casts are gone: no reward");
	}

	// ------------------------------------------------------------------ the config section

	@Test
	void anEmptyFileGivesTheDefaults() {
		assertEquals(RageConfig.defaults(), RageConfig.fromRoot(new JsonObject()));
	}

	@Test
	void toJsonAndFromRootRoundTrip() {
		JsonObject root = new JsonObject();
		root.add(RageConfig.SECTION, cfg.toJson());
		assertEquals(cfg, RageConfig.fromRoot(root));
	}

	@Test
	void everyKeyIsWrittenWithItsOwnName() {
		JsonObject o = cfg.toJson();
		for (String k : new String[] {"damageTenthsPerHp", "maxGainPerHitTenths", "dealtTenthsPerHp", "maxDealtGainPerHitTenths", "mobMultiplierPercent",
				"targetCapPer10sTenths", "parryGainTenths", "parryGapTicks", "abilityHitTenths", "passiveTenths", "passiveIntervalTicks", "passiveWindowTicks",
				"decayDelayTicks", "decayIntervalTicks", "decayTenths", "shikaiCostTenths", "bankaiCostTenths", "bankaiMinShikaiTicks"}) {
			assertTrue(o.has(k), k);
		}
		assertFalse(o.has("maxTenths"), "the size of the bar is not configurable");
	}

	@Test
	void editedValuesAreRead() {
		JsonObject root = JsonParser.parseString("{\"rage\":{\"dealtTenthsPerHp\":5,\"mobMultiplierPercent\":50,\"bankaiMinShikaiTicks\":100,\"abilityHitTenths\":0}}").getAsJsonObject();
		RageConfig c = RageConfig.fromRoot(root);
		assertEquals(5, c.dealtTenthsPerHp());
		assertEquals(50, c.mobMultiplierPercent());
		assertEquals(100, c.bankaiMinShikaiTicks());
		assertEquals(0, c.abilityHitTenths());
		assertEquals(40, c.parryGainTenths(), "untouched keys keep the default");
	}

	@Test
	void garbageFallsBackAndNumbersAreClamped() {
		JsonObject root = JsonParser.parseString("{\"rage\":{\"dealtTenthsPerHp\":\"many\",\"parryGainTenths\":-50,\"shikaiCostTenths\":99999,"
				+ "\"bankaiCostTenths\":null,\"decayIntervalTicks\":0,\"passiveIntervalTicks\":-4,\"bankaiMinShikaiTicks\":1e12,\"mobMultiplierPercent\":[1]}}").getAsJsonObject();
		RageConfig c = RageConfig.fromRoot(root);
		assertEquals(2, c.dealtTenthsPerHp(), "a string gives the default");
		assertEquals(0, c.parryGainTenths(), "negative is clamped to 0");
		assertEquals(1000, c.shikaiCostTenths(), "above the bar is clamped to the bar");
		assertEquals(800, c.bankaiCostTenths(), "null gives the default");
		assertEquals(1, c.decayIntervalTicks());
		assertEquals(1, c.passiveIntervalTicks());
		assertEquals(72_000, c.bankaiMinShikaiTicks());
		assertEquals(25, c.mobMultiplierPercent(), "an array gives the default");
	}

	@Test
	void aSectionThatIsNotAnObjectGivesTheDefaults() {
		assertEquals(RageConfig.defaults(), RageConfig.fromRoot(JsonParser.parseString("{\"rage\":5}").getAsJsonObject()));
		assertEquals(RageConfig.defaults(), RageConfig.fromRoot(JsonParser.parseString("{\"rage\":null}").getAsJsonObject()));
	}

	@Test
	void theConstructorStillRejectsNonsense() {
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, 0, 250, 40, 2, 25, 160, 20, 5, 0, 10, 120, 400, 800, -1, 20, 25, 80, 400));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, 0, 250, 40, 2, 25, 160, 20, 5, 0, 10, 120, 400, 800, 20, 20, 25, 80, -1));
	}

	@Test
	void theLegacyConstructorIsNeutralForTheNewRules() {
		RageConfig old = new RageConfig(1000, 15, 250, 300, 10, 150, 160, 20, 5, 5, 10, 120, 400, 800);
		assertEquals(0, old.parryGapTicks());
		assertEquals(0, old.abilityHitTenths());
		assertEquals(100, old.mobMultiplierPercent());
		assertEquals(0, old.bankaiMinShikaiTicks());
		RageMeter m = new RageMeter(old);
		assertEquals(300, m.onParry(0));
		assertEquals(300, m.onParry(1), "no gap");
	}
}
