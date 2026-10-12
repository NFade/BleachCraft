package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.minebleach.reiatsutest.core.combat.HitRules.Outcome;
import dev.minebleach.reiatsutest.core.combat.HitRules.Refusal;
import org.junit.jupiter.api.Test;

class HitRulesTest {
	private static Refusal r(boolean self, boolean alive, boolean spectator, boolean immune, boolean pet, boolean player,
			boolean affectPlayers, boolean pvp) {
		return HitRules.refusal(self, alive, spectator, immune, pet, player, affectPlayers, pvp);
	}

	@Test
	void aSurvivalPlayerIsATargetWhenPlayerHitsAndPvpAreOn() {
		// the round 3 bug: Tuning.affectPlayers defaulted to false, so this was PLAYERS_OFF for every player target
		assertEquals(Refusal.OK, r(false, true, false, false, false, true, true, true));
	}

	@Test
	void playersAreRefusedWithTheRightReason() {
		assertEquals(Refusal.PLAYERS_OFF, r(false, true, false, false, false, true, false, true));
		assertEquals(Refusal.SERVER_PVP_OFF, r(false, true, false, false, false, true, true, false));
		assertEquals(Refusal.PLAYERS_OFF, r(false, true, false, false, false, true, false, false), "the Tuning switch is named first");
	}

	@Test
	void mobsIgnorePlayerSwitchesAndPvp() {
		assertEquals(Refusal.OK, r(false, true, false, false, false, false, false, false));
	}

	@Test
	void otherRefusalsKeepTheirOrder() {
		assertEquals(Refusal.SELF, r(true, false, true, true, true, true, false, false));
		assertEquals(Refusal.DEAD, r(false, false, true, true, true, true, false, false));
		assertEquals(Refusal.SPECTATOR, r(false, true, true, true, true, true, false, false));
		assertEquals(Refusal.IMMUNE_TYPE, r(false, true, false, true, true, true, false, false));
		assertEquals(Refusal.OWN_PET, r(false, true, false, false, true, true, false, false));
	}

	private static Outcome o(float amount, boolean returned, float lost, Outcome veto, boolean creative, boolean invul,
			boolean team, boolean iframes) {
		return HitRules.outcome(amount, returned, lost, veto, creative, invul, team, iframes);
	}

	@Test
	void landedDamageIsDamaged() {
		assertEquals(Outcome.DAMAGED, o(6, true, 6, null, false, false, false, false));
		assertEquals(Outcome.DAMAGED, o(6, true, 0.5F, Outcome.BLOCKED, false, false, false, false), "a lowered block still hurt");
	}

	@Test
	void creativeIsNamedWhenNothingWasLost() {
		assertEquals(Outcome.CREATIVE, o(6, false, 0, null, true, true, false, false));
	}

	@Test
	void vetoBeatsTheGuessedReasons() {
		assertEquals(Outcome.PARRY, o(6, false, 0, Outcome.PARRY, false, false, false, false));
		assertEquals(Outcome.BARRIER, o(6, false, 0, Outcome.BARRIER, false, false, false, false));
	}

	@Test
	void remainingReasons() {
		assertEquals(Outcome.INVULNERABLE, o(6, false, 0, null, false, true, false, false));
		assertEquals(Outcome.TEAM, o(6, false, 0, null, false, false, true, false));
		assertEquals(Outcome.IFRAMES, o(6, false, 0, null, false, false, false, true));
		assertEquals(Outcome.REFUSED_UNKNOWN, o(6, false, 0, null, false, false, false, false));
		assertEquals(Outcome.NO_HEALTH_LOST, o(6, true, 0, null, false, false, false, false));
		assertEquals(Outcome.ZERO_AMOUNT, o(0, true, 0, null, false, false, false, false));
		assertEquals(Outcome.ZERO_AMOUNT, o(Float.NaN, true, 0, null, false, false, false, false));
	}
}
