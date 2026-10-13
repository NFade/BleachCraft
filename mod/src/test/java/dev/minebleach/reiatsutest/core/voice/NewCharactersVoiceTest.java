package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.CommandIds;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The phrase table of Gin and Uryu (gin-uryu registration skeleton). They have no entries in the shared positives fixture yet, so this
 * checks the table itself: every written phrase of the design document fires its own command in the context where it is valid, and
 * never fires anything in the context of another sword.
 */
class NewCharactersVoiceTest {
	private final PhraseMatcher matcher = VoiceTestSupport.matcher();

	private List<PhraseBook.Command> mine() {
		List<PhraseBook.Command> out = new ArrayList<>();
		for (PhraseBook.Command c : matcher.book().commands()) {
			if (c.id().startsWith("gin.") || c.id().startsWith("uryu.")) {
				out.add(c);
			}
		}
		return out;
	}

	@Test
	void sixteenCommandsWithTheRightItemsAndStates() {
		List<PhraseBook.Command> cmds = mine();
		assertEquals(16, cmds.size());
		for (PhraseBook.Command c : cmds) {
			CharacterId expected = c.id().startsWith("gin.") ? CharacterId.GIN : CharacterId.URYU;
			assertEquals(expected, c.item(), c.id());
			assertFalse(c.optional(), c.id());
		}
		for (CharacterId ch : new CharacterId[] {CharacterId.GIN, CharacterId.URYU}) {
			PhraseBook.Command shikai = matcher.book().command(CommandIds.forTransition(ch, ZanpakutoState.SHIKAI));
			PhraseBook.Command bankai = matcher.book().command(CommandIds.forTransition(ch, ZanpakutoState.BANKAI));
			assertEquals(java.util.Set.of(ZanpakutoState.BASE), shikai.states(), ch.name());
			assertEquals(java.util.Set.of(ZanpakutoState.SHIKAI), bankai.states(), ch.name());
			assertEquals(ch, shikai.item());
			assertEquals(ch, bankai.item());
		}
	}

	@Test
	void everyWrittenPhraseFiresItsOwnCommandInItsContext() {
		List<String> bad = new ArrayList<>();
		for (PhraseBook.Command c : mine()) {
			GateContext ctx = VoiceTestSupport.contextFor(c);
			for (PhraseBook.Variant v : c.variants()) {
				if (v.origin().equals("A")) {
					continue; // ASR aliases are guesses; the written phrases of the document are the contract
				}
				PhraseMatcher.Result r = matcher.match(v.text(), ctx);
				if (!(r.matched() && r.commandId().equals(c.id()))) {
					bad.add(c.id() + " <- \"" + v.text() + "\" key='" + r.normalized() + "' got " + r.status() + " " + r.commandId());
				}
			}
		}
		assertTrue(bad.isEmpty(), bad.toString());
	}

	@Test
	void theirPhrasesDoNotFireWithAnotherSwordInTheHand() {
		List<String> bad = new ArrayList<>();
		for (PhraseBook.Command c : mine()) {
			for (CharacterId other : new CharacterId[] {CharacterId.RUKIA, CharacterId.BYAKUYA, CharacterId.NONE}) {
				for (ZanpakutoState st : ZanpakutoState.values()) {
					GateContext ctx = GateContext.of(other, st, true);
					for (PhraseBook.Variant v : c.variants()) {
						PhraseMatcher.Result r = matcher.match(v.text(), ctx);
						// a "bankai" phrase may legitimately fire the bankai release of the sword in hand; only Gin/Uryu commands are wrong here
						if (r.matched() && (r.commandId().startsWith("gin.") || r.commandId().startsWith("uryu."))) {
							bad.add("\"" + v.text() + "\" of " + c.id() + " fired " + r.commandId() + " for " + other + " in " + st);
						}
					}
				}
			}
		}
		assertTrue(bad.isEmpty(), bad.toString());
	}
}
