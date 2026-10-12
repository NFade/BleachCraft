package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * G3: "dirty" live-speech phrases (split and glued words, vowel and voicing slips, hesitations and polite words around
 * the command, Cyrillic transcriptions) next to the untouched 202/153 fixture. The text is how Web Speech en-US and
 * ru-RU is expected to render the names; it is written from knowledge of recogniser behaviour, not recorded from a
 * microphone (UNVERIFIED against live input). Positives must fire exactly their command; neutrals must not fire in any
 * gate context.
 */
class DirtyPhraseFixtureTest {
	static final Path DIRTY = Paths.get("..", "tests", "voice", "dirty_fixture.json");

	private record Case(String command, String lang, String text) {
	}

	private static JsonObject fixture() throws IOException {
		return JsonParser.parseString(Files.readString(DIRTY, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static List<Case> positives() throws IOException {
		List<Case> out = new ArrayList<>();
		for (JsonElement e : fixture().getAsJsonArray("positives")) {
			JsonObject o = e.getAsJsonObject();
			out.add(new Case(o.get("command").getAsString(), o.get("lang").getAsString(), o.get("text").getAsString()));
		}
		return out;
	}

	private static List<Case> neutrals() throws IOException {
		List<Case> out = new ArrayList<>();
		for (JsonElement e : fixture().getAsJsonArray("neutrals")) {
			JsonObject o = e.getAsJsonObject();
			out.add(new Case("", o.get("lang").getAsString(), o.get("text").getAsString()));
		}
		return out;
	}

	@Test
	void fixtureHasTheAgreedSize() throws IOException {
		assertEquals(251, positives().size()); // 270 before the Absolute Zero phrases were removed (mech2 task 4)
		assertEquals(81, neutrals().size());
	}

	@Test
	void everyDirtyPositiveFiresExactlyItsCommand() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> misses = new ArrayList<>();
		for (Case c : positives()) {
			PhraseBook.Command cmd = m.book().command(c.command());
			assertTrue(cmd != null, "fixture names an unknown command " + c.command());
			PhraseMatcher.Result r = m.match(c.text(), VoiceTestSupport.contextFor(cmd));
			if (!(r.matched() && r.commandId().equals(c.command()))) {
				misses.add(c.command() + " <- \"" + c.text() + "\" key=" + r.normalized() + " got " + r.status() + " " + r.commandId());
			}
		}
		System.out.println("[voice] dirty fixture: " + (positives().size() - misses.size()) + "/" + positives().size());
		assertTrue(misses.isEmpty(), "dirty phrases that no longer fire: " + misses);
	}

	@Test
	void dirtyNeutralsNeverFireInAnyContext() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> fired = new ArrayList<>();
		for (Case n : neutrals()) {
			for (GateContext g : VoiceTestSupport.allContexts()) {
				PhraseMatcher.Result r = m.match(n.text(), g);
				if (r.matched()) {
					fired.add("\"" + n.text() + "\" -> " + r.commandId() + " via " + r.variant().text() + " in " + g);
				}
			}
		}
		assertTrue(fired.isEmpty(), "false positives: " + fired);
	}

	/** A loose (second pass) hit is never as confident as a strict one, so a strict rival always outranks it. */
	@Test
	void looseHitsScoreBelowTheStrictOnes() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		GateContext byakuyaBase = GateContext.of(CharacterId.BYAKUYA, ZanpakutoState.BASE, true);
		PhraseMatcher.Result strict = m.match("chire senbonzakura", byakuyaBase);
		PhraseMatcher.Result loose = m.match("chire send bonds a cura", byakuyaBase);
		assertTrue(strict.matched() && loose.matched());
		assertEquals("byakuya.shikai.release", loose.commandId());
		assertEquals(1.0, strict.confidence(), 1e-9);
		assertTrue(loose.confidence() < 0.97, "loose confidence " + loose.confidence());
		assertTrue(loose.confidence() >= m.config().threshold());
	}

	/** The new aliases and hesitation words must not have opened the gated "bank eye" family when bankai is not ready. */
	@Test
	void bankEyeFamilyStaysGatedOnBankaiReady() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		for (String t : new String[] {"bank eye", "bankeye", "bankee", "bank i", "bankie", "banki", "uh bank eye"}) {
			for (CharacterId who : new CharacterId[] {CharacterId.RUKIA, CharacterId.BYAKUYA}) {
				assertFalse(m.match(t, GateContext.of(who, ZanpakutoState.SHIKAI, false)).matched(), t + " fired while bankai is not ready");
				assertFalse(m.match(t, GateContext.of(who, ZanpakutoState.BASE, true)).matched(), t + " fired outside shikai");
				assertFalse(m.match(t, GateContext.ungated()).matched(), t + " fired ungated");
			}
		}
	}

	/** Hesitations and polite words around a weak command are ignored, real extra words still block it. */
	@Test
	void fillersOnlyHelpTheWholeUtteranceRule() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		GateContext ctx = GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, false);
		for (String t : new String[] {"seal", "uh seal", "um, seal it please", "so seal sword now", "ну запечатай меч", "эм запечатай"}) {
			PhraseMatcher.Result r = m.match(t, ctx);
			assertTrue(r.matched() && r.commandId().equals("common.seal"), t + " -> " + r.status() + " " + r.commandId());
		}
		for (String t : new String[] {"uh seal the deal", "I will seal the chest", "seal the envelope please", "запечатай письмо"}) {
			assertFalse(m.match(t, ctx).matched(), t + " must not fire");
		}
	}

	/** Deterministic random sentences made only of everyday words: nothing in the gate matrix may fire on them. */
	@Test
	void everydaySentencesNeverFire() {
		String[] vocab = ("i you we they it the a my your our this that go went come make made build built find found "
				+ "look see saw take took give gave need want like love have has had can will would should could "
				+ "sand box bond bonds cure cherry chili sherry shoe show row hero sue ski key kay hockey hack "
				+ "tale cage yoga barn bar bury dome home some same sum samba sandbox banker bunk bunker "
				+ "diamond iron stone wood door chest house farm field wheat seeds night day sun moon rain snow "
				+ "zombie creeper skeleton spider arrow sword shield armor helmet server lag ping game player "
				+ "я ты мы вы он она оно мой твой наш этот тот иду шёл пришёл делай сделал строй построил найди нашёл "
				+ "смотри видел бери взял дай нужно хочу люблю есть было буду может песок коробка банк бонус "
				+ "вишня шоу ряд герой ключ хакер дом кот барьер купол алмаз железо камень дерево дверь сундук "
				+ "ферма поле пшеница семена ночь день солнце луна дождь снег зомби крипер скелет паук стрела щит "
				+ "броня шлем сервер лаг пинг игра игрок").split(" ");
		PhraseMatcher m = VoiceTestSupport.matcher();
		Random rnd = new Random(20261011L);
		List<GateContext> ctxs = VoiceTestSupport.allContexts();
		List<String> fired = new ArrayList<>();
		for (int i = 0; i < 3000; i++) {
			int n = 1 + rnd.nextInt(5);
			StringBuilder sb = new StringBuilder();
			for (int k = 0; k < n; k++) {
				sb.append(sb.length() == 0 ? "" : " ").append(vocab[rnd.nextInt(vocab.length)]);
			}
			String text = sb.toString();
			for (GateContext g : ctxs) {
				PhraseMatcher.Result r = m.match(text, g);
				if (r.matched()) {
					fired.add("\"" + text + "\" -> " + r.commandId() + " via " + r.variant().text());
					break;
				}
			}
		}
		assertTrue(fired.isEmpty(), "everyday sentences that fired: " + fired);
	}
}
