package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.rage.RageConfig;
import dev.minebleach.reiatsutest.core.rage.RageMeter;
import dev.minebleach.reiatsutest.core.rage.RageReward;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** R13: the {@code uryu.bow} / {@code uryu.rage} keys, the Rage of an arrow, and the kill times the numbers were chosen for. */
class UryuBowTuningTest {
	private static JsonObject root(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	@AfterEach
	void restore() {
		BowDraw.setTuning(UryuBowTuning.defaults());
	}

	@Test
	void defaultsAreSetBOfTheReport() {
		UryuBowTuning d = UryuBowTuning.defaults();
		assertEquals(0.5, d.minPower());
		assertEquals(24, d.fullDrawTicks());
		assertEquals(3.0, d.damage());
		assertEquals(1.0, d.damageCurve());
		assertEquals(14, d.cooldownTicks());
		assertEquals(5, d.rageTenthsPerHp());
		assertEquals(30, d.rageMaxPerHitTenths());
		assertTrue(d.castRewardForAbilityArrows());
	}

	@Test
	void missingSectionGivesDefaultsAndBadValuesAreClamped() {
		assertEquals(UryuBowTuning.defaults(), UryuBowTuning.fromRoot(new JsonObject()));
		assertEquals(UryuBowTuning.defaults(), UryuBowTuning.fromRoot(root("{\"uryu\": 5}")));
		UryuBowTuning t = UryuBowTuning.fromRoot(root("{\"uryu\":{\"bow\":{\"minPower\":7,\"fullDrawTicks\":-3,\"damage\":\"x\",\"damageCurve\":0,\"cooldownTicks\":99999},"
				+ "\"rage\":{\"arrowTenthsPerHp\":-4,\"abilityArrowsPayCastReward\":false}}}"));
		assertEquals(1.0, t.minPower());
		assertEquals(4, t.fullDrawTicks());
		assertEquals(UryuBalance.BASIC_DAMAGE, t.damage(), 1e-9);
		assertEquals(0.25, t.damageCurve());
		assertEquals(400, t.cooldownTicks());
		assertEquals(0, t.rageTenthsPerHp());
		assertFalse(t.castRewardForAbilityArrows());
	}

	@Test
	void jsonRoundTrip() {
		UryuBowTuning t = new UryuBowTuning(0.6, 30, 2.5, 1.3, 18, 4, 20, false);
		JsonObject r = new JsonObject();
		r.add(UryuBowTuning.SECTION, t.toJson());
		assertEquals(t, UryuBowTuning.fromRoot(r));
	}

	@Test
	void constructorRejectsNonsense() {
		assertThrows(IllegalArgumentException.class, () -> new UryuBowTuning(1.5, 24, 3, 1, 14, 5, 30, true));
		assertThrows(IllegalArgumentException.class, () -> new UryuBowTuning(0.5, 0, 3, 1, 14, 5, 30, true));
		assertThrows(IllegalArgumentException.class, () -> new UryuBowTuning(0.5, 24, 3, 0, 14, 5, 30, true));
	}

	@Test
	void tuningChangesTheDrawWithoutRebuild() {
		BowDraw.setTuning(new UryuBowTuning(0.6, 30, 2.6, 1.5, 18, 5, 30, true));
		assertEquals(1.0, BowDraw.power(30), 1e-12);
		assertEquals(21, BowDraw.minDrawTicks());
		assertFalse(BowDraw.fires(BowDraw.power(20)));
		assertTrue(BowDraw.fires(BowDraw.power(21)));
		assertEquals(2.6f, Shots.basic(0, false, 1.0).damage(), 1e-5);
		assertEquals(2.6f * Math.pow(0.5, 1.5), Shots.basic(0, false, 0.5).damage(), 1e-5, "curve 1.5");
		BowDraw.setTuning(null);
		assertEquals(UryuBowTuning.defaults(), BowDraw.tuning());
	}

	@Test
	void arrowRageFollowsTheDamageAndIsCapped() {
		UryuBowTuning d = UryuBowTuning.defaults();
		assertEquals(15, d.arrowRageTenths(3.0f), "a full unfocused arrow pays 1.5");
		assertEquals(30, d.arrowRageTenths(7.2f), "a three stack shikai arrow hits the cap of 3.0");
		assertEquals(30, d.arrowRageTenths(100f));
		assertEquals(0, d.arrowRageTenths(0f));
		assertEquals(0, d.arrowRageTenths(-1f));
		assertTrue(d.arrowRageTenths(1.5f) < d.arrowRageTenths(3.0f), "a weak draw pays less");
	}

	@Test
	void meterPaysAFixedDealtGainWithMobShareAndCap() {
		RageConfig cfg = RageConfig.defaults();
		RageMeter m = new RageMeter(cfg);
		assertEquals(15, m.onFixedDealt(15, 1, false, 100));
		assertEquals(15, m.value());
		assertEquals(0, m.onFixedDealt(0, 1, false, 101), "nothing to pay is still combat");
		RageMeter capped = new RageMeter(new RageConfig(RageConfig.MAX_TENTHS, 0, 250, 50, 1000, 30, 600, 20, 5, 0, 10, 120, 400, 800, 20, 20, 50, 20, 400));
		assertEquals(10, capped.onFixedDealt(20, 7, true, 10), "mobs pay half");
		assertEquals(10, capped.onFixedDealt(20, 7, true, 11), "second mob hit fills the per-target cap of 2.0");
		assertEquals(0, capped.onFixedDealt(20, 7, true, 12), "cap reached");
	}

	@Test
	void rewardClassifierKeepsTheOldRulesWithoutAMark() {
		for (boolean ability : new boolean[] {false, true}) {
			assertEquals(RageReward.classify(false, ability), RageReward.classify(false, ability, RageReward.UryuMark.NONE, true));
			assertEquals(RageReward.NONE, RageReward.classify(true, ability, RageReward.UryuMark.BOW, true));
		}
		assertEquals(RageReward.ARROW, RageReward.classify(false, false, RageReward.UryuMark.BOW, true));
		assertEquals(RageReward.ABILITY, RageReward.classify(false, false, RageReward.UryuMark.CAST, true));
		assertEquals(RageReward.DEALT, RageReward.classify(false, false, RageReward.UryuMark.CAST, false), "switch off: the old per hit reward");
	}

	// ------------------------------------------------------------------ the design numbers (vanilla armor, 20 HP, i-frames 10 ticks)

	private static double armored(double dmg, double armor, double toughness) {
		return dmg * (1.0 - Math.min(20.0, Math.max(armor / 5.0, armor - dmg / (2.0 + toughness / 4.0))) / 25.0);
	}

	/** Seconds of continuous fire to kill a 20 HP player: stationary (Focus builds over the cycle) or moving, best draw length. */
	private static double killSeconds(UryuBowTuning t, boolean arterie, boolean still, double armor, double toughness) {
		BowDraw.setTuning(t);
		double best = Double.MAX_VALUE;
		int gap = 3; // ticks between a release and the next press
		for (int n = BowDraw.minDrawTicks(); n < 70; n++) {
			int cycle = Math.max(n + gap, t.cooldownTicks());
			int interval = arterie ? UryuBalance.FOCUS_INTERVAL_ARTERIE : UryuBalance.FOCUS_INTERVAL;
			int stacks = still ? Math.min(UryuBalance.FOCUS_MAX, cycle / interval) : 0;
			double raw = Shots.basic(stacks, arterie, BowDraw.power(n)).damage();
			double d = armored(raw, armor, toughness);
			int hits = (int) Math.ceil(20.0 / d - 1e-9);
			best = Math.min(best, hits * Math.max(cycle, 10) / 20.0);
		}
		return best;
	}

	@Test
	void killTimesOfTheChosenSetMatchTheDesignTarget() {
		UryuBowTuning b = UryuBowTuning.defaults();
		double shikaiStillIron = killSeconds(b, true, true, 15, 0);
		assertTrue(shikaiStillIron >= 8.0 && shikaiStillIron <= 10.0, "strongest sustained form kills iron in 8..10 s, was " + shikaiStillIron);
		assertTrue(killSeconds(b, false, true, 15, 0) >= 11.0, "BASE standing, iron");
		assertTrue(killSeconds(b, true, false, 15, 0) >= 15.0, "SHIKAI moving, iron");
		assertTrue(killSeconds(b, true, true, 20, 12) >= 15.0, "SHIKAI standing, netherite");
	}

	@Test
	void theOldNumbersKilledTooFast() {
		UryuBowTuning old = new UryuBowTuning(0.1, 20, 4.0, 1.0, 4, 1000, 30, false);
		assertTrue(killSeconds(old, true, true, 15, 0) < 6.5, "old SHIKAI standing, iron");
	}

	@Test
	void ragePerSecondIsBelowTheOldFlatThreePerHit() {
		UryuBowTuning b = UryuBowTuning.defaults();
		float armored = 3.0f * 1.6f; // BASE, two stacks over a 28 tick cycle
		double perHit = b.arrowRageTenths(armored) / 10.0;
		assertTrue(perHit < 3.0);
		assertTrue(40.0 / perHit >= 14, "shikai needs at least 14 landed arrows");
		// a minimum draw arrow (power 0.5, 1.5 HP) pays a quarter of the old flat 3.0
		assertEquals(0.8, b.arrowRageTenths((float) (3.0 * 0.5)) / 10.0, 0.11);
	}
}
