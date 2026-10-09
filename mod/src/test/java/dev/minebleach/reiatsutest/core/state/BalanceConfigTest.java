package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuMath;
import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** STATE_MACHINE section 6, meta tests M2 and M3 (M1 is CorePurityTest). */
class BalanceConfigTest {
	@Test
	void M2_defaultsInvariants() {
		BalanceConfig c = BalanceConfig.defaults();
		assertEquals(1000, c.maxTenths());
		assertTrue(c.shikaiReleaseCost() < c.maxTenths());
		assertTrue(c.bankaiCost() < c.maxTenths());
		for (ZanpakutoState s : ZanpakutoState.values()) {
			assertNotNull(c.rate(s), s.name());
		}
		// STATE_MACHINE section 3: per batch +10 / +5 / -4
		assertEquals(10, c.rate(ZanpakutoState.SEALED).net());
		assertEquals(5, c.rate(ZanpakutoState.SHIKAI).net());
		assertEquals(-4, c.rate(ZanpakutoState.BANKAI).net());
		// bankai cap comes before the idle drain reaches zero from 80.0
		int toZero = ReiatsuMath.ticksToZero(new ReiatsuState(c.maxTenths() - c.bankaiCost(), c.maxTenths()),
				c.rate(ZanpakutoState.BANKAI), c.regenBatchTicks());
		assertTrue(c.bankaiCapTicks() < toZero, "cap " + c.bankaiCapTicks() + " vs zero at " + toZero);
		// a per-second rate must give a whole number of tenths per batch: batches per second is integral
		assertEquals(0, 20 % c.regenBatchTicks());

		Set<Byte> codes = new HashSet<>();
		Set<Integer> effects = new HashSet<>();
		Set<String> ids = new HashSet<>();
		for (AbilityId a : AbilityId.values()) {
			AbilitySpec spec = c.spec(a);
			assertNotNull(spec, a.name());
			assertEquals(a, spec.id());
			assertTrue(spec.costTenths() < c.maxTenths(), a.name());
			assertTrue(spec.costTenths() > 0 && spec.cooldownTicks() > 0, a.name());
			assertTrue(codes.add(a.code), "duplicate code " + a);
			assertTrue(effects.add(spec.effectId()), "duplicate effect id " + a);
			assertTrue(ids.add(a.commandId), "duplicate command id " + a);
			assertTrue(a.commandId.startsWith(a.character.name().toLowerCase() + "." + a.requiredState.name().toLowerCase() + "."), a.commandId);
			for (int i = 1; i < spec.phaseOffsets().size(); i++) {
				assertTrue(spec.phaseOffsets().get(i) >= spec.phaseOffsets().get(i - 1), a.name());
			}
		}
		// transition effect ids do not collide with ability effect ids
		for (int id : new int[] {EffectIds.RUKIA_SHIKAI_RELEASE, EffectIds.RUKIA_BANKAI_RELEASE,
				EffectIds.BYAKUYA_SHIKAI_RELEASE, EffectIds.BYAKUYA_BANKAI_RELEASE, EffectIds.SEAL, EffectIds.BANKAI_END}) {
			assertFalse(effects.contains(id), "effect id " + id);
		}
		// each (character, state) has at most 3 slots, none used twice
		for (CharacterId ch : new CharacterId[] {CharacterId.RUKIA, CharacterId.BYAKUYA}) {
			for (ZanpakutoState st : new ZanpakutoState[] {ZanpakutoState.SHIKAI, ZanpakutoState.BANKAI}) {
				Set<Integer> slots = new HashSet<>();
				for (AbilityId a : AbilityId.values()) {
					if (a.character == ch && a.requiredState == st) {
						assertTrue(slots.add(a.slot()), a.name());
						assertTrue(a.slot() >= 0 && a.slot() <= 2);
					}
				}
			}
		}
	}

	/** M3: ability command ids equal the state-gated ids of design/VOICE_PHRASES.md section 1. */
	@Test
	void M3_commandIdsMatchTheVoiceTable() throws IOException {
		Path md = Paths.get("..", "design", "VOICE_PHRASES.md");
		Assumptions.assumeTrue(Files.isRegularFile(md), "design/VOICE_PHRASES.md not reachable from " + Paths.get("").toAbsolutePath());
		Pattern row = Pattern.compile("^\\| `([a-z_.]+)` \\| (SEALED|SHIKAI|BANKAI|SHIKAI / BANKAI) \\|");
		Set<String> abilityIds = new HashSet<>();
		Set<String> all = new HashSet<>();
		for (String line : Files.readAllLines(md)) {
			Matcher m = row.matcher(line);
			if (!m.find()) {
				continue;
			}
			String id = m.group(1);
			all.add(id);
			if (!id.endsWith(".release") && !id.equals("common.seal")) {
				abilityIds.add(id);
				AbilityId a = AbilityId.fromCommandId(id);
				assertNotNull(a, "table id without AbilityId: " + id);
				assertEquals(a.requiredState.name(), m.group(2), id);
			}
		}
		Set<String> ours = new HashSet<>();
		for (AbilityId a : AbilityId.values()) {
			ours.add(a.commandId);
		}
		assertEquals(abilityIds, ours);
		// transitions use the same ids
		for (CharacterId ch : new CharacterId[] {CharacterId.RUKIA, CharacterId.BYAKUYA}) {
			assertTrue(all.contains(CommandIds.forTransition(ch, ZanpakutoState.SHIKAI)));
			assertTrue(all.contains(CommandIds.forTransition(ch, ZanpakutoState.BANKAI)));
		}
		assertTrue(all.contains(CommandIds.SEAL));
		assertEquals(14, all.size());
	}
}
