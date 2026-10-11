package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * B5: the three kido commands (kido.byakurai, kido.shakkaho, kido.sai) in the phrase table. They are valid in EVERY state with
 * any sword or none, which makes a false positive more expensive than for a shikai word (it costs reiatsu while the player
 * talks), so the neutral set is checked in every gate context. The fixture is separate from the 202/153, dirty (G3) and live
 * (2026-10-11) ones, which stay untouched; those neutrals are re-run here against the kido commands too.
 */
class KidoVoiceFixtureTest {
	static final Path KIDO = Paths.get("..", "tests", "voice", "kido_fixture.json");
	static final Path[] OLD_FIXTURES = {VoiceTestSupport.FIXTURE, Paths.get("..", "tests", "voice", "dirty_fixture.json"),
			Paths.get("..", "tests", "voice", "live_fixture_2026_10_11.json")};

	private record Case(String command, String lang, String text) {
	}

	private static List<Case> load(Path file, String array) throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		List<Case> out = new ArrayList<>();
		for (JsonElement e : root.getAsJsonArray(array)) {
			JsonObject o = e.getAsJsonObject();
			out.add(new Case(o.has("command") ? o.get("command").getAsString() : "", o.get("lang").getAsString(),
					o.get("text").getAsString()));
		}
		return out;
	}

	/** Every real gate context: any character in hand or none, any state, bar full or not. (Not the ungated test mode.) */
	private static List<GateContext> realContexts() {
		List<GateContext> out = new ArrayList<>();
		for (GateContext g : VoiceTestSupport.allContexts()) {
			if (g.gated()) {
				out.add(g);
			}
		}
		return out;
	}

	@Test
	void theThreeCommandsAreValidInEveryStateWithAnySword() {
		PhraseBook book = VoiceTestSupport.book();
		for (KidoId k : KidoId.values()) {
			PhraseBook.Command c = book.command(k.commandId);
			assertNotNull(c, k.commandId);
			assertEquals(Set.of(ZanpakutoState.values()), c.states(), k.commandId);
			assertEquals(CharacterId.NONE, c.item(), k.commandId + " item any");
			assertFalse(c.optional());
			assertFalse(c.interimSafe(), "kido never fires from an interim result");
			for (GateContext g : realContexts()) {
				assertTrue(c.validIn(g), k.commandId + " in " + g);
			}
		}
	}

	@Test
	void fixtureHasTheAgreedSize() throws IOException {
		assertEquals(71, load(KIDO, "positives").size());
		assertEquals(68, load(KIDO, "neutrals").size());
		for (KidoId k : KidoId.values()) {
			long n = load(KIDO, "positives").stream().filter(c -> c.command().equals(k.commandId)).count();
			assertTrue(n >= 20, k.commandId + " has only " + n + " positives");
		}
	}

	@Test
	void everyPositiveFiresExactlyItsCommandInEveryContext() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> misses = new ArrayList<>();
		for (Case c : load(KIDO, "positives")) {
			for (GateContext g : realContexts()) {
				PhraseMatcher.Result r = m.match(c.text(), g);
				if (!(r.matched() && r.commandId().equals(c.command()))) {
					misses.add(c.command() + " <- \"" + c.text() + "\" key=" + r.normalized() + " in " + g + " got " + r.status() + " "
							+ r.commandId());
					break;
				}
			}
		}
		assertTrue(misses.isEmpty(), "kido phrases that do not fire: " + misses);
	}

	@Test
	void kidoNeutralsNeverFireInAnyContext() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> fired = new ArrayList<>();
		for (Case n : load(KIDO, "neutrals")) {
			for (GateContext g : VoiceTestSupport.allContexts()) {
				PhraseMatcher.Result r = m.match(n.text(), g);
				if (r.matched()) {
					fired.add("\"" + n.text() + "\" -> " + r.commandId() + " via " + r.variant().text() + " in " + g);
				}
			}
		}
		assertTrue(fired.isEmpty(), "false positives: " + fired);
	}

	/** The older neutral sets (every sentence of the 202, dirty and live fixtures) must not wake a kido command either. */
	@Test
	void theOlderNeutralSetsDoNotFireAnyKidoCommand() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> fired = new ArrayList<>();
		for (Path f : OLD_FIXTURES) {
			for (Case n : load(f, "neutrals")) {
				for (GateContext g : VoiceTestSupport.allContexts()) {
					PhraseMatcher.Result r = m.match(n.text(), g);
					if (r.matched() && r.commandId().startsWith("kido.")) {
						fired.add("\"" + n.text() + "\" -> " + r.commandId() + " via " + r.variant().text() + " in " + g);
					}
				}
			}
		}
		assertTrue(fired.isEmpty(), "false positives: " + fired);
	}

	/** The older positives keep firing their own command (a kido variant must not steal them). */
	@Test
	void theOlderPositivesAreNotStolenByAKidoCommand() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> stolen = new ArrayList<>();
		for (Path f : OLD_FIXTURES) {
			for (Case p : load(f, "positives")) {
				PhraseBook.Command cmd = m.book().command(p.command());
				PhraseMatcher.Result r = m.match(p.text(), VoiceTestSupport.contextFor(cmd));
				if (r.matched() && r.commandId().startsWith("kido.")) {
					stolen.add(p.command() + " <- \"" + p.text() + "\" fired " + r.commandId());
				}
			}
		}
		assertTrue(stolen.isEmpty(), "stolen: " + stolen);
	}

	/** The category word and the bare number never fire: "Hado" would be Hado 4 or Hado 31. */
	@Test
	void theCategoryWordsAloneAreAmbiguousAndSilent() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		for (String t : new String[] {"hado", "Hado", "хадо", "はどう", "hado is", "hado no"}) {
			for (GateContext g : VoiceTestSupport.allContexts()) {
				PhraseMatcher.Result r = m.match(t, g);
				assertFalse(r.matched(), "'" + t + "' fired " + r.commandId() + " in " + g);
			}
		}
	}

	/** Short spell names and lone Sai / Bakudo are whole-utterance only: inside a sentence they do nothing. */
	@Test
	void weakNamesInsideASentenceDoNothing() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		GateContext g = GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, true);
		for (String t : new String[] {"the sai is a weapon", "I like bakudo spells", "he woke up by a cry in the night", "sai and then more",
				"бакудо это категория", "сай это оружие"}) {
			assertFalse(m.match(t, g).matched(), t);
		}
		assertTrue(m.match("sai", g).matched());
		assertTrue(m.match("uh bakudo", g).matched(), "fillers are ignored");
	}

	/** Numbers pick the right spell, and Hado 4 / 31 never fire each other. */
	@Test
	void numbersSelectTheSpell() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		GateContext g = GateContext.of(CharacterId.NONE, ZanpakutoState.SEALED, false);
		assertEquals("kido.byakurai", m.match("hado 4", g).commandId());
		assertEquals("kido.byakurai", m.match("Хадо 4", g).commandId());
		assertEquals("kido.shakkaho", m.match("hado 31", g).commandId());
		assertEquals("kido.shakkaho", m.match("Хадо 31", g).commandId());
		assertEquals("kido.sai", m.match("bakudo 1", g).commandId());
		assertEquals("kido.byakurai", m.match("Хадо чётыре Бякурай", g).commandId());
		assertEquals("kido.shakkaho", m.match("Сякухо", g).commandId());
	}

	/** A kido word does not interfere with the zanpakuto words: Chire in BASE stays the Senbonzakura release. */
	@Test
	void kidoDoesNotDisturbTheOtherCommands() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		assertEquals("byakuya.shikai.release", m.match("Chire Senbonzakura", GateContext.of(CharacterId.BYAKUYA, ZanpakutoState.BASE, false)).commandId());
		assertEquals("rukia.shikai.hakuren", m.match("Hakuren", GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, false)).commandId());
		assertEquals("common.seal", m.match("seal", GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, false)).commandId());
		assertFalse(m.match("Bankai", GateContext.of(CharacterId.RUKIA, ZanpakutoState.SEALED, true)).matched());
	}
}
