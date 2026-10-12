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
import org.junit.jupiter.api.Test;

/**
 * The first live session (2026-10-11, real microphone, Chrome ru-RU / en-US): what the recogniser really wrote for the
 * Byakuya names, and the ordinary phrases it produced in between. The fixture is separate from the 202/153 and the dirty
 * (G3) ones. Positives must fire exactly their command; neutrals must not fire in any gate context.
 */
class LiveSessionFixtureTest {
	static final Path LIVE = Paths.get("..", "tests", "voice", "live_fixture_2026_10_11.json");

	private record Case(String command, String lang, String text) {
	}

	private static List<Case> load(String array) throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(LIVE, StandardCharsets.UTF_8)).getAsJsonObject();
		List<Case> out = new ArrayList<>();
		for (JsonElement e : root.getAsJsonArray(array)) {
			JsonObject o = e.getAsJsonObject();
			out.add(new Case(o.has("command") ? o.get("command").getAsString() : "", o.get("lang").getAsString(),
					o.get("text").getAsString()));
		}
		return out;
	}

	@Test
	void fixtureHasTheAgreedSize() throws IOException {
		assertEquals(48, load("positives").size()); // 49 before the Absolute Zero phrase was removed (mech2 task 4)
		assertEquals(48, load("neutrals").size());
	}

	@Test
	void everyLivePositiveFiresExactlyItsCommand() throws IOException {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> misses = new ArrayList<>();
		for (Case c : load("positives")) {
			PhraseBook.Command cmd = m.book().command(c.command());
			assertTrue(cmd != null, "unknown command " + c.command());
			PhraseMatcher.Result r = m.match(c.text(), VoiceTestSupport.contextFor(cmd));
			if (!(r.matched() && r.commandId().equals(c.command()))) {
				misses.add(c.command() + " <- \"" + c.text() + "\" key=" + r.normalized() + " got " + r.status() + " " + r.commandId());
			}
		}
		assertTrue(misses.isEmpty(), "live phrases that do not fire: " + misses);
	}

	@Test
	void liveNeutralsNeverFireInAnyContext() throws IOException {
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

	/** The sword name alone releases the shikai from the drawn base form, and does nothing in any other state or hand. */
	@Test
	void theSwordNameAloneIsTheShikaiReleaseOnlyInBase() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		for (String t : new String[] {"senbonzakura", "Senbonzakura", "sen bonzakura", "senbonsakura", "сэмбонзакура", "сэм бонзакура", "千本桜"}) {
			for (CharacterId who : CharacterId.values()) {
				for (ZanpakutoState st : ZanpakutoState.values()) {
					PhraseMatcher.Result r = m.match(t, GateContext.of(who, st, true));
					boolean expected = who == CharacterId.BYAKUYA && st == ZanpakutoState.BASE;
					assertEquals(expected, r.matched(), t + " / " + who + " / " + st + " -> " + r.status() + " " + r.commandId());
					if (expected) {
						assertEquals("byakuya.shikai.release", r.commandId());
					}
				}
			}
		}
	}

	/** Weak: a longer sentence that merely contains the name must not release the shikai. */
	@Test
	void theSwordNameInsideASentenceDoesNothing() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		GateContext base = GateContext.of(CharacterId.BYAKUYA, ZanpakutoState.BASE, true);
		for (String t : new String[] {"I like senbonzakura music", "senbonzakura is a great song", "спой сенбонзакура для меня",
				"это сэнбонзакура из блича", "senbonzakura kageyoshi", "bankai senbonzakura", "the senbonzakura sword"}) {
			assertFalse(m.match(t, base).matched(), t + " must not fire in BASE");
		}
	}

	/** "siro" is also the key of the Minecraft word "silo": it only counts glued to the sword name. */
	@Test
	void loneSiroAndOrdinaryChireLookalikesStaySilent() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		for (String t : new String[] {"siro", "shiro", "silo", "щиро", "cherry", "cheery", "chili", "тире", "чири"}) {
			for (GateContext g : VoiceTestSupport.allContexts()) {
				assertFalse(m.match(t, g).matched(), t + " fired in " + g);
			}
		}
	}
}
