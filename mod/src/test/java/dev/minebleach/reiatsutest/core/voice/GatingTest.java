package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import org.junit.jupiter.api.Test;

/** A command only fires when it is valid for the player's state and the zanpakuto in the main hand. */
class GatingTest {
	private final PhraseMatcher m = VoiceTestSupport.matcher();

	private String fire(String text, CharacterId item, ZanpakutoState st, boolean full) {
		PhraseMatcher.Result r = m.match(text, GateContext.of(item, st, full));
		return r.matched() ? r.commandId() : null;
	}

	private String fire(String text, CharacterId item, ZanpakutoState st) {
		return fire(text, item, st, true);
	}

	@Test
	void chireMeansDifferentThingsInDifferentStates() {
		assertEquals("byakuya.shikai.release", fire("chire", CharacterId.BYAKUYA, ZanpakutoState.SEALED));
		assertEquals("byakuya.bankai.scatter", fire("chire", CharacterId.BYAKUYA, ZanpakutoState.BANKAI));
		assertNull(fire("chire", CharacterId.BYAKUYA, ZanpakutoState.SHIKAI), "no Chire command in shikai");
		for (ZanpakutoState st : ZanpakutoState.values()) {
			assertNull(fire("chire", CharacterId.RUKIA, st), "Rukia has no Chire in " + st);
			assertNull(fire("chire senbonzakura", CharacterId.RUKIA, st));
		}
	}

	@Test
	void bankaiOnlyFromShikai() {
		assertEquals("byakuya.bankai.release", fire("bankai", CharacterId.BYAKUYA, ZanpakutoState.SHIKAI));
		assertEquals("rukia.bankai.release", fire("bankai", CharacterId.RUKIA, ZanpakutoState.SHIKAI));
		assertNull(fire("bankai", CharacterId.BYAKUYA, ZanpakutoState.SEALED));
		assertNull(fire("bankai", CharacterId.BYAKUYA, ZanpakutoState.BANKAI));
		assertNull(fire("bankai hakka no togame", CharacterId.RUKIA, ZanpakutoState.BANKAI),
				"the tail of the bankai phrase must not re-fire in the new state");
		// Gate A decision 8: "Chire" is never a bankai trigger
		assertFalse("byakuya.bankai.release".equals(fire("chire", CharacterId.BYAKUYA, ZanpakutoState.SHIKAI)));
		assertNull(fire("senbonzakura", CharacterId.BYAKUYA, ZanpakutoState.SHIKAI), "the sword name alone is not a trigger");
	}

	@Test
	void theBankaiPhraseDoesNotCareAboutTheBarBecauseTheServerDecides() {
		// a spoken "bankai" without a full bar still reaches the server, which answers DENIED_REIATSU with the usual feedback
		assertEquals("rukia.bankai.release", fire("bankai", CharacterId.RUKIA, ZanpakutoState.SHIKAI, false));
	}

	@Test
	void itemGate() {
		assertEquals("rukia.shikai.release", fire("sode no shirayuki", CharacterId.RUKIA, ZanpakutoState.SEALED));
		assertNull(fire("sode no shirayuki", CharacterId.BYAKUYA, ZanpakutoState.SEALED), "wrong sword in hand");
		assertNull(fire("sode no shirayuki", CharacterId.NONE, ZanpakutoState.SEALED), "nothing in hand");
		assertNull(fire("chire senbonzakura", CharacterId.NONE, ZanpakutoState.SEALED));
		assertNull(fire("hakuren", CharacterId.NONE, ZanpakutoState.SHIKAI));
		assertNull(fire("attack mode", CharacterId.RUKIA, ZanpakutoState.SHIKAI));
	}

	@Test
	void stateGate() {
		assertNull(fire("hakuren", CharacterId.RUKIA, ZanpakutoState.SEALED));
		assertNull(fire("hakuren", CharacterId.RUKIA, ZanpakutoState.BANKAI));
		assertEquals("rukia.bankai.absolute_zero", fire("absolute zero", CharacterId.RUKIA, ZanpakutoState.BANKAI));
		assertNull(fire("absolute zero", CharacterId.RUKIA, ZanpakutoState.SHIKAI));
		assertEquals("byakuya.bankai.hakuteiken", fire("hakuteiken", CharacterId.BYAKUYA, ZanpakutoState.BANKAI));
		assertNull(fire("hakuteiken", CharacterId.BYAKUYA, ZanpakutoState.SHIKAI));
		assertNull(fire("shirayuki", CharacterId.RUKIA, ZanpakutoState.SHIKAI));
	}

	@Test
	void sealWorksFromShikaiAndBankaiWithEitherSwordButNotFromSealed() {
		for (CharacterId ch : new CharacterId[] {CharacterId.RUKIA, CharacterId.BYAKUYA}) {
			assertEquals("common.seal", fire("seal", ch, ZanpakutoState.SHIKAI));
			assertEquals("common.seal", fire("запечатать", ch, ZanpakutoState.BANKAI));
			assertNull(fire("seal", ch, ZanpakutoState.SEALED));
		}
		assertNull(fire("the seal is broken", CharacterId.RUKIA, ZanpakutoState.SHIKAI), "weak word inside a sentence");
	}

	/**
	 * Decision: "bank eye" (the commonest en-US rendering of "bankai") counts as bankai ONLY in the context where a bankai
	 * request can really succeed: state SHIKAI, the matching sword in the main hand AND a full reiatsu bar. Everywhere
	 * else (and in the ungated strict test mode) it is ordinary speech.
	 */
	@Test
	void bankEyeIsBankaiOnlyWithShikaiItemAndFullBar() {
		for (String phrase : new String[] {"bank eye", "bankeye", "bank i"}) {
			assertEquals("rukia.bankai.release", fire(phrase, CharacterId.RUKIA, ZanpakutoState.SHIKAI, true), phrase);
			assertEquals("byakuya.bankai.release", fire(phrase, CharacterId.BYAKUYA, ZanpakutoState.SHIKAI, true), phrase);
			assertNull(fire(phrase, CharacterId.RUKIA, ZanpakutoState.SHIKAI, false), phrase + ": bar not full");
			assertNull(fire(phrase, CharacterId.RUKIA, ZanpakutoState.SEALED, true), phrase + ": not shikai");
			assertNull(fire(phrase, CharacterId.RUKIA, ZanpakutoState.BANKAI, true), phrase + ": not shikai");
			assertNull(fire(phrase, CharacterId.NONE, ZanpakutoState.SHIKAI, true), phrase + ": nothing in hand");
			assertFalse(m.match(phrase, GateContext.ungated()).matched(), phrase + " ungated");
		}
		// whole utterance only: ordinary sentences with the same sound stay quiet even in the one context that allows it
		for (String sentence : new String[] {"I need to go to the bank", "bank the loot in the chest", "bank eye doctor tomorrow",
				"the bank is closed", "банковская карта"}) {
			assertNull(fire(sentence, CharacterId.RUKIA, ZanpakutoState.SHIKAI, true), sentence);
		}
	}

	@Test
	void whyNothingFiredIsExplained() {
		// the same text matches nothing in this state but a command in another state/item would take it
		PhraseMatcher.Result none = m.match("bankai", GateContext.of(CharacterId.RUKIA, ZanpakutoState.SEALED, true));
		assertEquals(PhraseMatcher.Status.NO_MATCH, none.status());
		PhraseMatcher.Result any = m.match("hakuren", GateContext.ungated());
		assertTrue(any.matched());
		assertEquals("rukia.shikai.hakuren", any.commandId());
	}

	@Test
	void everyCommandIsReachableInItsOwnContextAndOnlyThere() {
		for (PhraseBook.Command c : m.book().commands()) {
			// the canonical phrase fires its command in a valid context
			GateContext ok = VoiceTestSupport.contextFor(c);
			PhraseMatcher.Result r = m.match(c.say(), ok);
			assertTrue(r.matched() && r.commandId().equals(c.id()), c.id() + " canonical '" + c.say() + "' -> " + r.status() + " " + r.commandId());
			// and never in a context where the command is invalid
			for (GateContext g : VoiceTestSupport.allContexts()) {
				if (g.gated() && !c.validIn(g)) {
					PhraseMatcher.Result bad = m.match(c.say(), g);
					assertFalse(bad.matched() && bad.commandId().equals(c.id()), c.id() + " fired in " + g);
				}
			}
		}
	}
}
