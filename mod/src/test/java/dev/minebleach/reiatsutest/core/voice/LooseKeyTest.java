package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** G3: the second, consonant-folding key used for long names heard by an English or Russian recogniser. */
class LooseKeyTest {
	private static String loose(String raw) {
		return Normalizer.compact(LooseKey.tokens(Normalizer.tokens(Normalizer.normalize(raw))));
	}

	@Test
	void voicedAndUnvoicedStopsMeet() {
		assertEquals(LooseKey.token("tomo"), LooseKey.token("domo"));
		assertEquals(LooseKey.token("gara"), LooseKey.token("kara"));
		assertEquals(LooseKey.token("bara"), LooseKey.token("pala".replace('l', 'r')));
		assertEquals("kakeyosi", LooseKey.token("gageyosi"));
	}

	@Test
	void anEpentheticStopAfterNIsDropped() {
		// "send bonds a cura" is how a recogniser hears senbonzakura
		assertEquals(loose("senbonzakura"), loose("send bonds a cura"));
		assertEquals("san", LooseKey.token("sant"));
		assertEquals("santa", LooseKey.token("santa"), "a stop before a vowel is a real syllable");
	}

	@Test
	void vowelsAreKeptSoTheKeyStaysInformative() {
		assertNotEquals(LooseKey.token("kogeki"), LooseKey.token("kageyosi"));
		assertNotEquals(loose("sode no shirayuki"), loose("semena zoloto"));
	}

	@Test
	void tokensStayOneToOneAndNeverEmpty() {
		List<String> strict = Normalizer.tokens(Normalizer.normalize("Chire, Senbonzakura wow"));
		List<String> lk = LooseKey.tokens(strict);
		assertEquals(strict.size(), lk.size());
		lk.forEach(t -> assertNotEquals("", t));
	}

	@Test
	void hardCAndKMeetAndRunsCollapse() {
		assertEquals(LooseKey.token("cura"), LooseKey.token("kura"));
		assertEquals("hak", LooseKey.token("hack"));
		assertEquals("kara", LooseKey.token("kkara"));
	}
}
