package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.combat.FrostMomentum.Config;
import dev.minebleach.reiatsutest.core.combat.FrostMomentum.Form;
import org.junit.jupiter.api.Test;

/** R11 T6: the Rukia passive (variant P1). Pure rules; the glue ({@code server/RukiaMomentum}) and the mixin are UNVERIFIED in the game. */
class FrostMomentumTest {
	private final FrostMomentum m = new FrostMomentum(Config.defaults());

	@Test
	void defaultsAreTheAgreedNumbers() {
		Config c = Config.defaults();
		assertTrue(c.enabled());
		assertEquals(0.05, c.shikai().bonusPerStack(), 1e-9);
		assertEquals(4, c.shikai().maxStacks());
		assertEquals(80, c.shikai().expireTicks());
		assertEquals(0.08, c.bankai().bonusPerStack(), 1e-9);
		assertEquals(5, c.bankai().maxStacks());
		assertEquals(100, c.bankai().expireTicks());
		assertEquals(1.20, c.shikai().maxMultiplier(), 1e-9);
		assertEquals(1.40, c.bankai().maxMultiplier(), 1e-9);
	}

	@Test
	void theMultiplierTableOfShikaiAndBankai() {
		double[] shikai = {1.00, 1.05, 1.10, 1.15, 1.20, 1.20};
		double[] bankai = {1.00, 1.08, 1.16, 1.24, 1.32, 1.40, 1.40};
		FrostMomentum s = new FrostMomentum(Config.defaults());
		for (int i = 0; i < shikai.length; i++) {
			assertEquals(shikai[i], s.multiplier(Form.SHIKAI, 100 + i * 2 + 1), 1e-9, "shikai after " + i + " hits");
			s.onHit(Form.SHIKAI, 100 + i * 2 + 1);
		}
		FrostMomentum b = new FrostMomentum(Config.defaults());
		for (int i = 0; i < bankai.length; i++) {
			assertEquals(bankai[i], b.multiplier(Form.BANKAI, 100 + i * 2 + 1), 1e-9, "bankai after " + i + " hits");
			b.onHit(Form.BANKAI, 100 + i * 2 + 1);
		}
	}

	@Test
	void everyHitAddsAStackUpToTheMaximum() {
		for (int i = 1; i <= 8; i++) {
			assertEquals(Math.min(i, 4), m.onHit(Form.SHIKAI, i * 10));
		}
		FrostMomentum b = new FrostMomentum(Config.defaults());
		for (int i = 1; i <= 8; i++) {
			assertEquals(Math.min(i, 5), b.onHit(Form.BANKAI, i * 10));
		}
	}

	@Test
	void aTickWithSeveralVictimsIsOneHitAndAllVictimsSeeTheSameBonus() {
		m.onHit(Form.SHIKAI, 100);
		double first = m.multiplier(Form.SHIKAI, 110);
		assertEquals(1.05, first, 1e-9);
		m.onHit(Form.SHIKAI, 110);
		m.onHit(Form.SHIKAI, 110);
		m.onHit(Form.SHIKAI, 110);
		assertEquals(2, m.stacks(Form.SHIKAI, 110), "four victims in one tick: one stack");
		assertEquals(first, m.multiplier(Form.SHIKAI, 110), 1e-9, "the second victim of that tick is not hurt more than the first");
		assertEquals(1.10, m.multiplier(Form.SHIKAI, 111), 1e-9, "the next tick sees the new stack");
	}

	@Test
	void theStacksBurnOutAfterTheTimerWithoutAHit() {
		m.onHit(Form.SHIKAI, 100);
		m.onHit(Form.SHIKAI, 110);
		assertEquals(2, m.stacks(Form.SHIKAI, 189));
		assertEquals(0, m.stacks(Form.SHIKAI, 190), "80 ticks after the last hit: all gone");
		assertEquals(1.0, m.multiplier(Form.SHIKAI, 190), 1e-9);
		assertEquals(1, m.onHit(Form.SHIKAI, 200), "a hit after the burn-out starts from zero");
	}

	@Test
	void bankaiBurnsOutAfterOneHundredTicksAndAHitRestartsTheTimer() {
		FrostMomentum b = new FrostMomentum(Config.defaults());
		b.onHit(Form.BANKAI, 0);
		b.onHit(Form.BANKAI, 90);
		assertEquals(2, b.stacks(Form.BANKAI, 189));
		assertEquals(0, b.stacks(Form.BANKAI, 190));
	}

	@Test
	void aHitOnRukiaRemovesExactlyOneStack() {
		for (int i = 1; i <= 4; i++) {
			m.onHit(Form.SHIKAI, i * 5);
		}
		assertEquals(3, m.onHurt(Form.SHIKAI, 30));
		assertEquals(2, m.onHurt(Form.SHIKAI, 31));
		assertEquals(0, new FrostMomentum(Config.defaults()).onHurt(Form.SHIKAI, 5), "nothing to remove");
	}

	@Test
	void beingHitDoesNotRestartTheBurnOutTimer() {
		m.onHit(Form.SHIKAI, 0);
		m.onHit(Form.SHIKAI, 10);
		m.onHurt(Form.SHIKAI, 70);
		assertEquals(0, m.stacks(Form.SHIKAI, 90), "the timer still runs from the last hit she LANDED");
	}

	@Test
	void resetClearsEverything() {
		m.onHit(Form.SHIKAI, 10);
		m.onHit(Form.SHIKAI, 20);
		m.reset();
		assertEquals(0, m.stacks(Form.SHIKAI, 21));
		assertEquals(1.0, m.multiplier(Form.SHIKAI, 21), 1e-9);
		assertEquals(1, m.onHit(Form.SHIKAI, 22));
	}

	@Test
	void noFormNoMomentum() {
		assertEquals(0, m.onHit(Form.NONE, 10));
		assertEquals(1.0, m.multiplier(Form.NONE, 11), 1e-9);
		assertEquals(0, m.stacks(Form.NONE, 11));
	}

	@Test
	void fallingBackFromBankaiToShikaiClampsTheStacks() {
		FrostMomentum b = new FrostMomentum(Config.defaults());
		for (int i = 1; i <= 5; i++) {
			b.onHit(Form.BANKAI, i * 5);
		}
		assertEquals(5, b.stacks(Form.BANKAI, 30));
		assertEquals(4, b.stacks(Form.SHIKAI, 30));
		assertEquals(1.20, b.multiplier(Form.SHIKAI, 30), 1e-9);
	}

	@Test
	void theShikaiTimerAppliesWhenFormIsShikai() {
		FrostMomentum b = new FrostMomentum(Config.defaults());
		b.onHit(Form.BANKAI, 0);
		assertEquals(0, b.stacks(Form.SHIKAI, 80), "80 ticks are enough to burn out in shikai");
		assertEquals(1, b.stacks(Form.BANKAI, 80), "but not in bankai (100)");
	}

	@Test
	void aDisabledPassiveDoesNothing() {
		FrostMomentum d = new FrostMomentum(new Config(false, Config.defaults().shikai(), Config.defaults().bankai()));
		assertEquals(0, d.onHit(Form.SHIKAI, 10));
		assertEquals(1.0, d.multiplier(Form.SHIKAI, 11), 1e-9);
	}

	@Test
	void liveReloadKeepsTheStacksAndUsesTheNewNumbers() {
		m.onHit(Form.SHIKAI, 10);
		m.onHit(Form.SHIKAI, 20);
		m.setConfig(new Config(true, new FrostMomentum.Tier(0.10, 4, 80), Config.defaults().bankai()));
		assertEquals(1.20, m.multiplier(Form.SHIKAI, 30), 1e-9);
	}

	// ------------------------------------------------------------------ config section rukia.passive

	@Test
	void anEmptyFileGivesTheDefaults() {
		assertEquals(Config.defaults(), Config.fromRoot(new JsonObject()));
	}

	@Test
	void toJsonAndFromRootRoundTripUnderRukiaPassive() {
		JsonObject root = new JsonObject();
		JsonObject rukia = new JsonObject();
		rukia.add("passive", Config.defaults().toJson());
		root.add("rukia", rukia);
		assertEquals(Config.defaults(), Config.fromRoot(root));
		JsonObject passive = Config.defaults().toJson();
		assertTrue(passive.has("enabled") && passive.getAsJsonObject("shikai").has("bonusPerStack") && passive.getAsJsonObject("bankai").has("maxStacks")
				&& passive.getAsJsonObject("bankai").has("expireTicks"));
	}

	@Test
	void editedValuesAreReadAndOtherRukiaKeysAreIgnored() {
		JsonObject root = JsonParser.parseString("{\"rukia\":{\"iceWall\":{\"x\":1},\"passive\":{\"shikai\":{\"bonusPerStack\":0.1,\"maxStacks\":2},"
				+ "\"bankai\":{\"expireTicks\":40},\"enabled\":false}}}").getAsJsonObject();
		Config c = Config.fromRoot(root);
		assertEquals(false, c.enabled());
		assertEquals(0.1, c.shikai().bonusPerStack(), 1e-9);
		assertEquals(2, c.shikai().maxStacks());
		assertEquals(80, c.shikai().expireTicks(), "untouched key keeps the default");
		assertEquals(40, c.bankai().expireTicks());
		assertEquals(5, c.bankai().maxStacks());
	}

	@Test
	void garbageFallsBackAndNumbersAreClamped() {
		JsonObject root = JsonParser.parseString("{\"rukia\":{\"passive\":{\"enabled\":\"yes\",\"shikai\":{\"bonusPerStack\":\"lots\",\"maxStacks\":-3,"
				+ "\"expireTicks\":0},\"bankai\":{\"bonusPerStack\":50,\"maxStacks\":9999,\"expireTicks\":1e12}}}}").getAsJsonObject();
		Config c = Config.fromRoot(root);
		assertTrue(c.enabled(), "a string gives the default");
		assertEquals(0.05, c.shikai().bonusPerStack(), 1e-9);
		assertEquals(0, c.shikai().maxStacks());
		assertEquals(1, c.shikai().expireTicks());
		assertEquals(1.0, c.bankai().bonusPerStack(), 1e-9);
		assertEquals(20, c.bankai().maxStacks());
		assertEquals(72_000, c.bankai().expireTicks());
	}

	@Test
	void sectionsThatAreNotObjectsGiveTheDefaults() {
		assertEquals(Config.defaults(), Config.fromRoot(JsonParser.parseString("{\"rukia\":5}").getAsJsonObject()));
		assertEquals(Config.defaults(), Config.fromRoot(JsonParser.parseString("{\"rukia\":{\"passive\":[1]}}").getAsJsonObject()));
		assertEquals(Config.defaults(), Config.fromRoot(JsonParser.parseString("{\"rukia\":{\"passive\":{\"shikai\":null}}}").getAsJsonObject()));
	}
}
