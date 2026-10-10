package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuMath;
import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuState;
import dev.minebleach.reiatsutest.core.voice.PhraseBook;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** STATE_MACHINE section 6, meta tests M2 and M3 (M1 is CorePurityTest). M3 reads voice/phrases.json since phase 5. */
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
		assertEquals(10, c.rate(ZanpakutoState.BASE).net()); // drawn base form regenerates like sealed
		assertEquals(5, c.rate(ZanpakutoState.SHIKAI).net());
		// B4 step 4: bankai costs no reiatsu, has no upkeep drain, and ends by its own timer
		assertEquals(5, c.rate(ZanpakutoState.BANKAI).net());
		assertEquals(0, c.rate(ZanpakutoState.BANKAI).drainPerBatch());
		assertEquals(0, c.bankaiCost());
		assertEquals(900, c.bankaiCapTicks());
		assertTrue(c.bankaiReentryTicks() > 0);
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
			assertTrue(spec.cooldownTicks() > 0, a.name());
			assertEquals(a.requiredState == ZanpakutoState.BANKAI, spec.costTenths() == 0, a.name() + ": bankai abilities are free, shikai ones cost");
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

	/**
	 * M3: ability command ids equal the state-gated ids of the bundled phrase table (voice/phrases.json, generated from
	 * design/VOICE_PHRASES.md by tools/gen_voice_phrases.py), with the same state and character.
	 */
	@Test
	void M3_commandIdsMatchThePhraseTable() {
		PhraseBook book = PhraseBook.loadBundled();
		Set<String> abilityIds = new HashSet<>();
		Set<String> all = new HashSet<>();
		for (PhraseBook.Command c : book.commands()) {
			String id = c.id();
			all.add(id);
			if (!id.endsWith(".release") && !id.equals("common.seal")) {
				abilityIds.add(id);
				AbilityId a = AbilityId.fromCommandId(id);
				assertNotNull(a, "phrase table id without AbilityId: " + id);
				assertEquals(Set.of(a.requiredState), c.states(), id);
				assertEquals(a.character, c.item(), id);
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
			assertEquals(Set.of(ZanpakutoState.BASE), book.command(CommandIds.forTransition(ch, ZanpakutoState.SHIKAI)).states());
			assertEquals(Set.of(ZanpakutoState.SHIKAI), book.command(CommandIds.forTransition(ch, ZanpakutoState.BANKAI)).states());
			assertEquals(ch, book.command(CommandIds.forTransition(ch, ZanpakutoState.SHIKAI)).item());
		}
		assertTrue(all.contains(CommandIds.SEAL));
		assertEquals(Set.of(ZanpakutoState.BASE, ZanpakutoState.SHIKAI, ZanpakutoState.BANKAI), book.command(CommandIds.SEAL).states());
		assertNull(CommandIds.forTransition(CharacterId.RUKIA, ZanpakutoState.BASE), "the draw has no voice command");
		assertEquals(14, all.size());
	}
}
