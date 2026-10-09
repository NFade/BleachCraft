package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * VOICE_PHRASES 3.2, right-hand column: prefixes and short words that must never fire a command alone, and never when
 * combined with ordinary words. Run in every gate context (and ungated).
 */
class MustNotFireTest {
	private static final String[] ALONE = {
		"mae", "sode", "dance", "some no mai", "tsugi no mai", "san no mai", "first dance", "next dance", "third dance",
		"hakka", "togame", "zero", "absolute", "senbonzakura", "attack", "barrier", "dome", "shukei", "emperor", "sword",
		"sen", "ноль", "атака", "барьер", "сан", "мае", "сенбонзакура", "千本桜", "桜", "雪", "banzai", "bonsai", "банка", "cherry",
	};

	/** Ordinary words used to dress the prefixes up as sentences. */
	private static final String[] WORDS = {
		"the", "a", "is", "to", "and", "of", "my", "you", "it", "go", "get", "see", "now", "here", "this", "that", "good",
		"night", "day", "stone", "iron", "gold", "chest", "door", "house", "torch", "lava", "water", "rain", "zombie",
		"pig", "cow", "sheep", "block", "build", "farm", "trade", "loot", "level", "boss", "play", "world", "mine",
		"я", "ты", "это", "где", "мой", "иди", "дом", "ночь", "день", "камень", "железо", "меч", "кирка", "сундук", "дверь",
		"вода", "огонь", "снег", "дождь", "зомби", "свинья", "блок", "ферма", "игра", "мир", "копать",
	};

	@Test
	void prefixesAndShortWordsAloneStayQuiet() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> fired = new ArrayList<>();
		for (String p : ALONE) {
			for (GateContext g : VoiceTestSupport.allContexts()) {
				PhraseMatcher.Result r = m.match(p, g);
				if (r.matched()) {
					fired.add("'" + p + "' -> " + r.commandId() + " in " + g);
				}
			}
		}
		assertTrue(fired.isEmpty(), fired.toString());
	}

	@Test
	void prefixesDressedAsSentencesStayQuiet() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> fired = new ArrayList<>();
		int checks = 0;
		for (String p : ALONE) {
			for (String w : WORDS) {
				for (String text : new String[] {p + " " + w, w + " " + p, w + " " + p + " " + w}) {
					for (GateContext g : VoiceTestSupport.allContexts()) {
						checks++;
						PhraseMatcher.Result r = m.match(text, g);
						if (r.matched()) {
							fired.add("'" + text + "' -> " + r.commandId() + " via '" + r.variant().text() + "' in " + g);
						}
					}
				}
			}
		}
		System.out.println("[voice] must-not-fire combinations checked: " + checks + ", fired: " + fired.size());
		assertTrue(fired.isEmpty(), fired.size() + " unexpected: " + fired.subList(0, Math.min(15, fired.size())));
	}

	@Test
	void singleOrdinaryWordsAndPairsNeverFire() {
		PhraseMatcher m = VoiceTestSupport.matcher();
		List<String> fired = new ArrayList<>();
		for (String w : WORDS) {
			for (String text : new String[] {w, w + " " + WORDS[(w.hashCode() & 0x7fffffff) % WORDS.length]}) {
				for (GateContext g : VoiceTestSupport.allContexts()) {
					PhraseMatcher.Result r = m.match(text, g);
					if (r.matched()) {
						fired.add("'" + text + "' -> " + r.commandId() + " in " + g);
					}
				}
			}
		}
		assertTrue(fired.isEmpty(), fired.toString());
	}
}
