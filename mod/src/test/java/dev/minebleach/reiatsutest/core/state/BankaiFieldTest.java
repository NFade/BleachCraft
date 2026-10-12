package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.world.FrostConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Mech2 task 4: the passive frost territory of the Rukia bankai replaces the Absolute Zero ability. Timing, targets, numbers, and that
 * the old ability is gone from every table (keys, voice, book, lang).
 */
class BankaiFieldTest {
	private static final BankaiFieldSpec F = BalanceConfig.defaults().bankaiField();

	@Test
	void defaultsAreSlownessOneAndOneHeartEveryThreeSeconds() {
		assertEquals(0, F.slowAmplifier(), "bankai.field.slowAmplifier: Slowness I");
		assertEquals(2.0F, F.tickDamage(), 0.0F, "bankai.field.tickDamage: exactly one heart");
		assertEquals(60, F.periodTicks(), "bankai.field.periodTicks: 3 s");
		assertEquals(20, F.refreshTicks(), "Slowness renewed every 20 ticks");
	}

	@Test
	void slownessIsRenewedEveryTwentyTicksFromTheFirstTick() {
		int renewals = 0;
		for (long age = 0; age < 900; age++) {
			if (F.slowDue(age)) {
				assertEquals(0, age % 20);
				renewals++;
			}
		}
		assertEquals(45, renewals, "once per second over the 45 s bankai");
		assertFalse(F.slowDue(-1));
	}

	@Test
	void damageComesEverySixtyTicksButNeverAtTheStart() {
		assertFalse(F.damageDue(0));
		assertFalse(F.damageDue(59));
		assertTrue(F.damageDue(60));
		assertFalse(F.damageDue(61));
		assertTrue(F.damageDue(120));
		int pulses = 0;
		for (long age = 0; age <= 900; age++) {
			pulses += F.damageDue(age) ? 1 : 0;
		}
		assertEquals(15, pulses, "15 pulses over the whole 45 s bankai");
		assertEquals(15 * 2.0F, pulses * F.tickDamage(), 0.0F);
		assertEquals(0, F.pulsesIn(59));
		assertEquals(1, F.pulsesIn(60));
		assertEquals(10, F.pulsesIn(600));
	}

	@Test
	void everyKnobIsConfigurableThroughBalanceConfig() {
		BankaiFieldSpec custom = new BankaiFieldSpec(2, 40, 10, 4.0F, 30, 8);
		BalanceConfig c = BalanceConfig.defaults().withBankaiField(custom);
		assertEquals(2, c.bankaiField().slowAmplifier());
		assertEquals(4.0F, c.bankaiField().tickDamage(), 0.0F);
		assertEquals(30, c.bankaiField().periodTicks());
		assertTrue(c.bankaiField().damageDue(30));
		assertEquals(BalanceConfig.defaults().barrierReductionPercent(), c.barrierReductionPercent(), "other numbers untouched");
		assertThrows(IllegalArgumentException.class, () -> new BankaiFieldSpec(0, 30, 20, 2.0F, 0, 64));
		assertThrows(IllegalArgumentException.class, () -> new BankaiFieldSpec(-1, 30, 20, 2.0F, 60, 64));
	}

	@Test
	void onlyEnemiesAreTouched() {
		// isPlayer, isMonster, ally, playerInvulnerable
		assertTrue(BankaiFieldSpec.eligible(false, true, false, false), "hostile mob");
		assertTrue(BankaiFieldSpec.eligible(true, false, false, false), "survival player (the pvp filter runs before this)");
		assertFalse(BankaiFieldSpec.eligible(false, false, false, false), "a cow, villager or other passive creature");
		assertFalse(BankaiFieldSpec.eligible(true, false, true, false), "a player of the caster's team");
		assertFalse(BankaiFieldSpec.eligible(false, true, true, false), "a mob on the caster's team");
		assertFalse(BankaiFieldSpec.eligible(true, false, false, true), "a Creative / invulnerable player");
	}

	@Test
	void theTerritoryGrowsWithTheFreezeWaveToTheFrostRadius() {
		FrostConfig fc = FrostConfig.defaults();
		assertEquals(30, fc.radius(), "the field radius is the existing frozen territory radius");
		assertEquals(0.0, BankaiFieldSpec.radiusAt(0, fc.radius(), fc.waveSpeed(), true), 1e-9);
		assertEquals(12.0, BankaiFieldSpec.radiusAt(20, fc.radius(), fc.waveSpeed(), true), 1e-9, "12 blocks per second");
		assertEquals(30.0, BankaiFieldSpec.radiusAt(100, fc.radius(), fc.waveSpeed(), true), 1e-9, "capped at the radius");
		assertEquals(30.0, BankaiFieldSpec.radiusAt(0, fc.radius(), fc.waveSpeed(), false), "a disabled wave: the whole radius at once");
		assertEquals(fc.radius(), FrostConfig.defaults().radius(), 0);
	}

	/** One enemy standing in the territory for the whole bankai takes 15 hearts and gets Slowness all the time (pure simulation of the timing). */
	@Test
	void anEnemyInsideForTheWholeBankaiLosesFifteenHeartsAndIsNeverFreeOfSlowness() {
		double hp = 0;
		long slowUntil = -1;
		long unslowed = 0;
		for (long age = 0; age <= 900; age++) {
			if (F.slowDue(age)) {
				slowUntil = age + F.slowTicks();
			}
			if (F.damageDue(age)) {
				hp += F.tickDamage();
			}
			if (age >= 0 && age > slowUntil) {
				unslowed++;
			}
		}
		assertEquals(30.0, hp, 1e-9, "15 pulses of 2.0");
		assertEquals(0, unslowed, "the renewal gap (20) is shorter than one application (30): no free tick");
	}

	/** The old ability is gone from the id table, the voice table, the book and both lang files; the bankai slot 2 of Rukia is empty. */
	@Test
	void absoluteZeroIsGoneEverywhere() throws IOException {
		for (AbilityId a : AbilityId.values()) {
			assertFalse(a.name().contains("ABSOLUTE"), a.name());
			assertFalse(a.commandId.contains("absolute"), a.commandId);
		}
		assertNull(AbilityId.fromCommandId("rukia.bankai.absolute_zero"));
		assertNull(AbilityId.forSlot(CharacterId.RUKIA, ZanpakutoState.BANKAI, 1));
		Path res = Path.of("src/main/resources/assets/reiatsu_test");
		for (String f : new String[] {"voice/phrases.json", "lang/en_us.json", "lang/ru_ru.json"}) {
			String text = Files.readString(res.resolve(f), StandardCharsets.UTF_8).toLowerCase();
			assertFalse(text.contains("absolute_zero"), f);
			assertFalse(text.contains("absolute zero"), f);
		}
		String ru = Files.readString(res.resolve("lang/ru_ru.json"), StandardCharsets.UTF_8).toLowerCase();
		assertFalse(ru.contains("абсолютный ноль"));
	}
}
