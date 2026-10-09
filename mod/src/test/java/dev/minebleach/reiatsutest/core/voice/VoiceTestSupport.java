package dev.minebleach.reiatsutest.core.voice;

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

/** Shared loading helpers of the voice tests (the tests run with the mod/ project dir as working directory). */
final class VoiceTestSupport {
	static final Path FIXTURE = Paths.get("..", "tests", "voice", "phrases_fixture.json");
	static final Path VOICE_MD = Paths.get("..", "design", "VOICE_PHRASES.md");

	private VoiceTestSupport() {
	}

	static PhraseBook book() {
		return PhraseBook.loadBundled();
	}

	static PhraseMatcher matcher() {
		return new PhraseMatcher(book(), PhraseMatcher.Config.defaults());
	}

	record Positive(String command, String lang, String text, String kind) {
	}

	record Neutral(String lang, String text) {
	}

	static JsonObject fixture() throws IOException {
		return JsonParser.parseString(Files.readString(FIXTURE, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	static List<Positive> positives() throws IOException {
		List<Positive> out = new ArrayList<>();
		for (JsonElement e : fixture().getAsJsonArray("positives")) {
			JsonObject o = e.getAsJsonObject();
			out.add(new Positive(o.get("command").getAsString(), o.get("lang").getAsString(), o.get("text").getAsString(),
					o.get("kind").getAsString()));
		}
		return out;
	}

	static List<Neutral> neutrals() throws IOException {
		List<Neutral> out = new ArrayList<>();
		for (JsonElement e : fixture().getAsJsonArray("neutrals")) {
			JsonObject o = e.getAsJsonObject();
			out.add(new Neutral(o.get("lang").getAsString(), o.get("text").getAsString()));
		}
		return out;
	}

	/** The context in which a command is valid: its first required state (SHIKAI for the shared seal), its item, full bar. */
	static GateContext contextFor(PhraseBook.Command c) {
		ZanpakutoState st = c.states().size() > 1 ? ZanpakutoState.SHIKAI : c.states().iterator().next();
		CharacterId item = c.item() == CharacterId.NONE ? CharacterId.RUKIA : c.item();
		return GateContext.of(item, st, true);
	}

	/** Every real gate context plus the ungated strict mode. */
	static List<GateContext> allContexts() {
		List<GateContext> ctxs = new ArrayList<>();
		ctxs.add(GateContext.ungated());
		for (CharacterId ch : new CharacterId[] {CharacterId.NONE, CharacterId.RUKIA, CharacterId.BYAKUYA}) {
			for (ZanpakutoState st : ZanpakutoState.values()) {
				for (boolean full : new boolean[] {false, true}) {
					ctxs.add(GateContext.of(ch, st, full));
				}
			}
		}
		return ctxs;
	}
}
