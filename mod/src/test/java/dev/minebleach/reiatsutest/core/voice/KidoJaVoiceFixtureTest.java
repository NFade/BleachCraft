package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Voice v4: Japanese kido. The live ja-JP log of 2026-10-11 showed Chrome writing 破道 as "波動" (empty key before v4) and 白雷 as
 * "くらい"; this fixture (tests/voice/kido_ja_fixture.json, separate from every older one) pins the new kanji / kana / digit
 * renderings. The older fixtures are re-run against all commands by {@link KidoVoiceFixtureTest}, MatcherFixtureTest and
 * MustNotFireTest, which stay untouched.
 */
class KidoJaVoiceFixtureTest {
	static final Path FILE = Paths.get("..", "tests", "voice", "kido_ja_fixture.json");

	private record Case(String command, String text) {
	}

	private static List<Case> load(String array) throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
		List<Case> out = new ArrayList<>();
		for (JsonElement e : root.getAsJsonArray(array)) {
			JsonObject o = e.getAsJsonObject();
			out.add(new Case(o.has("command") ? o.get("command").getAsString() : "", o.get("text").getAsString()));
		}
		return out;
	}

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
	void fixtureHasTheAgreedSize() throws IOException {
		assertEquals(71, load("positives").size());
		assertEquals(61, load("neutrals").size());
		for (String id : new String[] {"kido.byakurai", "kido.shakkaho", "kido.sai"}) {
			long n = load("positives").stream().filter(c -> c.command().equals(id)).count();
			assertTrue(n >= 15, id + " has only " + n + " positives");
		}
	}

	@Test
	void everyJapanesePositiveFiresExactlyItsCommandInEveryContext() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> misses = new ArrayList<>();
		for (Case c : load("positives")) {
			for (GateContext g : realContexts()) {
				PhraseMatcher.Result r = m.match(c.text(), g);
				if (!(r.matched() && r.commandId().equals(c.command()))) {
					misses.add(c.command() + " <- \"" + c.text() + "\" key=" + r.normalized() + " in " + g + " got " + r.status() + " "
							+ r.commandId());
					break;
				}
			}
		}
		assertTrue(misses.isEmpty(), "ja kido phrases that do not fire: " + misses);
	}

	@Test
	void japaneseNeutralsNeverFireInAnyContext() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> fired = new ArrayList<>();
		for (Case n : load("neutrals")) {
			for (GateContext g : VoiceTestSupport.allContexts()) {
				PhraseMatcher.Result r = m.match(n.text(), g);
				if (r.matched()) {
					fired.add("\"" + n.text() + "\" -> " + r.commandId() + " via " + r.variant().text() + " in " + g);
				}
			}
		}
		assertTrue(fired.isEmpty(), "false positives: " + fired);
	}

	/** The two outputs of the live session, at the key level. */
	@Test
	void homophonesOfHadoGetTheSameKeyAsHado() {
		assertEquals("hado", Normalizer.normalize("波動"));
		assertEquals("hado", Normalizer.normalize("破道"));
		assertEquals("hado no yon byakurai", Normalizer.normalize("破道の四 白雷"));
		assertEquals("hado no yon", Normalizer.normalize("波動の4"));
		assertEquals("hado no sansuici sakaho", Normalizer.normalize("波動の31 借家法"));
		assertEquals("bakudo no ici sai", Normalizer.normalize("縛道の一 サイ"));
		assertEquals("sakaho", Normalizer.normalize("シャッカ砲"));
	}

	/** "Hado" alone is ambiguous (4 or 31) and must stay silent in any script; "bakudo" alone is the documented weak sai. */
	@Test
	void loneHadoNeverFiresInAnyScript() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		for (String t : new String[] {"波動", "破道", "覇道", "はどう", "ハドウ", "hado", "hadou", "は"}) {
			for (GateContext g : VoiceTestSupport.allContexts()) {
				assertFalse(m.match(t, g).matched(), t + " in " + g);
			}
		}
	}

	/** くらい is a real word; it fires only as the whole utterance, never after a kanji quantity or inside a sentence. */
	@Test
	void loneKuraiIsWeak() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		GateContext g = GateContext.of(dev.minebleach.reiatsutest.core.state.CharacterId.NONE,
				dev.minebleach.reiatsutest.core.state.ZanpakutoState.SEALED, false);
		assertEquals("kido.byakurai", m.match("くらい", g).commandId());
		assertEquals("kido.byakurai", m.match("お願い くらい", g).commandId());
		for (String t : new String[] {"百くらい", "半分くらい", "どのくらい", "これくらい 食べた", "くらい部屋", "暗い"}) {
			assertFalse(m.match(t, g).matched(), t);
		}
	}
}
