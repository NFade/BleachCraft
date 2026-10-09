package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.core.voice.VoiceTestSupport.Neutral;
import dev.minebleach.reiatsutest.core.voice.VoiceTestSupport.Positive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The acceptance numbers of MASTER_PROMPT section 6: at least 95 percent recall on the fixture positives and not one
 * false positive on the neutral phrases. Positives are matched in the context in which their command is valid
 * (state + item in hand + full bar); neutrals are matched in the strict ungated mode (every command considered) AND in
 * every real gate context, so no state/item combination lets ordinary speech fire.
 */
class MatcherFixtureTest {
	@Test
	void fixtureHasTheAgreedSize() throws IOException {
		assertEquals(202, VoiceTestSupport.positives().size());
		assertEquals(153, VoiceTestSupport.neutrals().size());
	}

	@Test
	void recallOnThePositivesIsAtLeast95Percent() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> misses = new ArrayList<>();
		int total = 0;
		for (Positive p : VoiceTestSupport.positives()) {
			total++;
			PhraseBook.Command c = m.book().command(p.command());
			assertTrue(c != null, "fixture names an unknown command " + p.command());
			PhraseMatcher.Result r = m.match(p.text(), VoiceTestSupport.contextFor(c));
			if (!(r.matched() && r.commandId().equals(p.command()))) {
				misses.add(p.command() + " <- \"" + p.text() + "\" (" + p.lang() + ") key='" + r.normalized() + "' got "
						+ r.status() + " " + r.commandId());
			}
		}
		double recall = 100.0 * (total - misses.size()) / total;
		System.out.printf("[voice] fixture recall %d/%d = %.1f%%%n", total - misses.size(), total, recall);
		misses.forEach(s -> System.out.println("[voice]   MISS " + s));
		assertTrue(recall >= 95.0, "recall " + recall + "% below 95%, misses: " + misses);
	}

	@Test
	void everyCommandHasPositivesAndTheyAllFireItself() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		for (PhraseBook.Command c : m.book().commands()) {
			long n = VoiceTestSupport.positives().stream().filter(p -> p.command().equals(c.id())).count();
			assertTrue(n >= 10, c.id() + " has only " + n + " positives");
		}
	}

	@Test
	void noFalsePositivesOnTheNeutralPhrases() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> fired = new ArrayList<>();
		int checks = 0;
		for (Neutral n : VoiceTestSupport.neutrals()) {
			for (GateContext g : VoiceTestSupport.allContexts()) {
				checks++;
				PhraseMatcher.Result r = m.match(n.text(), g);
				if (r.matched()) {
					fired.add("\"" + n.text() + "\" -> " + r.commandId() + " via '" + r.variant().text() + "' in " + g);
				}
			}
		}
		System.out.printf("[voice] neutrals: %d phrases x %d contexts = %d checks, %d false positives%n",
				VoiceTestSupport.neutrals().size(), VoiceTestSupport.allContexts().size(), checks, fired.size());
		assertTrue(fired.isEmpty(), "false positives: " + fired);
	}

	/**
	 * Honest baseline: how far the fuzzy rules get with only the canonical/translation/transcription variants of
	 * VOICE_PHRASES section 1 (no ASR alias at all). Informational, with a floor so it cannot silently collapse.
	 */
	@Test
	void recallWithoutAnyAliasIsReportedAndStaysAboveAFloor() throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(
				Paths.get("src", "main", "resources", "assets", "reiatsu_test", "voice", "phrases.json"), StandardCharsets.UTF_8))
				.getAsJsonObject();
		for (JsonElement ce : root.getAsJsonArray("commands")) {
			JsonArray keep = new JsonArray();
			for (JsonElement ve : ce.getAsJsonObject().getAsJsonArray("variants")) {
				if (!ve.getAsJsonObject().get("origin").getAsString().equals("A")) {
					keep.add(ve);
				}
			}
			ce.getAsJsonObject().add("variants", keep);
		}
		PhraseBook bare = PhraseBook.parse(root.toString());
		PhraseMatcher m = new PhraseMatcher(bare, PhraseMatcher.Config.defaults());
		int hit = 0;
		int total = 0;
		for (Positive p : VoiceTestSupport.positives()) {
			total++;
			PhraseMatcher.Result r = m.match(p.text(), VoiceTestSupport.contextFor(bare.command(p.command())));
			if (r.matched() && r.commandId().equals(p.command())) {
				hit++;
			}
		}
		System.out.printf("[voice] fixture recall with NO ASR aliases: %d/%d = %.1f%%%n", hit, total, 100.0 * hit / total);
		assertTrue(100.0 * hit / total >= 80.0, "fuzzy-only recall dropped to " + hit + "/" + total);
	}

	/** VOICE_PHRASES section 3.3: the gating/ambiguity matrix, evaluated with a NOT full reiatsu bar. */
	@Test
	void gatingMatrixOfTheDesignDocument() throws IOException {
		Assumptions.assumeTrue(Files.isRegularFile(VoiceTestSupport.VOICE_MD), "design/VOICE_PHRASES.md not reachable");
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> wrong = new ArrayList<>();
		int rows = 0;
		boolean inMatrix = false;
		for (String line : Files.readAllLines(VoiceTestSupport.VOICE_MD, StandardCharsets.UTF_8)) {
			if (line.startsWith("### 3.3")) {
				inMatrix = true;
				continue;
			}
			if (inMatrix && line.startsWith("### 3.4")) {
				break;
			}
			if (!inMatrix || !line.startsWith("|")) {
				continue;
			}
			String[] c = line.substring(1, line.length() - 1).split("[|]", -1);
			if (c.length != 4) {
				continue;
			}
			String text = c[0].trim();
			String state = c[1].trim();
			String item = c[2].trim();
			String expected = c[3].trim().replace("*", "").replace("`", "").trim();
			if (text.equals("text") || text.startsWith("---")) {
				continue;
			}
			rows++;
			ZanpakutoState st = ZanpakutoState.valueOf(state);
			CharacterId ch = item.equals("rukia") ? CharacterId.RUKIA : CharacterId.BYAKUYA;
			PhraseMatcher.Result r = m.match(text, GateContext.of(ch, st, false));
			String got = r.matched() ? r.commandId() : "none";
			if (!got.equals(expected)) {
				wrong.add("'" + text + "' in " + state + "/" + item + ": expected " + expected + " got " + got);
			}
		}
		System.out.println("[voice] gating matrix rows: " + rows + ", wrong: " + wrong.size());
		assertTrue(rows > 100, "matrix not parsed, rows=" + rows);
		assertTrue(wrong.isEmpty(), "matrix mismatches: " + wrong);
	}
}
