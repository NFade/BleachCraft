package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class NormalizerTest {
	/** Every key printed in sections 1 and 2 of VOICE_PHRASES.md is normative: the normaliser must reproduce all of them. */
	@Test
	void reproducesEveryGoldenKeyOfTheDesignDocument() throws IOException {
		Assumptions.assumeTrue(Files.isRegularFile(VoiceTestSupport.VOICE_MD), "design/VOICE_PHRASES.md not reachable");
		int rows = 0;
		List<String> bad = new ArrayList<>();
		boolean section2 = false;
		for (String line : Files.readAllLines(VoiceTestSupport.VOICE_MD, StandardCharsets.UTF_8)) {
			if (line.startsWith("## 2.")) {
				section2 = true;
			}
			if (line.startsWith("## 3.")) {
				section2 = false;
			}
			if (line.startsWith("## 4.")) {
				break;
			}
			if (!line.startsWith("|") || !line.endsWith("|")) {
				continue;
			}
			String[] c = line.substring(1, line.length() - 1).split("[|]", -1);
			for (int i = 0; i < c.length; i++) {
				c[i] = c[i].trim();
			}
			String phrase = null;
			String key = null;
			if (!section2 && c.length == 4 && c[2].matches("`[CTGMXA]`")) {
				phrase = c[1];
				key = c[3];
			} else if (section2 && c.length == 4 && (c[3].equals("F") || c[3].equals("A"))) {
				phrase = c[1];
				key = c[2];
			}
			if (phrase == null) {
				continue;
			}
			rows++;
			phrase = phrase.replace(" (weak: whole utterance)", "");
			key = key.replace("`", "");
			String got = Normalizer.normalize(phrase);
			if (!got.equals(key)) {
				bad.add(phrase + " -> '" + got + "' expected '" + key + "'");
			}
		}
		assertTrue(rows > 250, "parsed only " + rows + " golden rows");
		assertTrue(bad.isEmpty(), bad.toString());
	}

	@Test
	void spotChecksOfTheDesignDocument() {
		assertEquals("sode no sirayuki", Normalizer.normalize("袖白雪"));
		assertEquals("some no mai cukisiro", Normalizer.normalize("初の舞 月白"));
		assertEquals("haka no togame", Normalizer.normalize("白霞の咎め"));
		assertEquals("senbonsakura kageosi", Normalizer.normalize("千本桜景厳"));
		assertEquals("suke hakuteken", Normalizer.normalize("Сюкэй: Хакутэйкэн"));
		assertEquals("suke hakuteken", Normalizer.normalize("Shūkei: Hakuteiken"));
		assertEquals("cire", Normalizer.normalize("ちれ"));
		assertEquals("cire", Normalizer.normalize("Чире"));
		assertEquals("cire", Normalizer.normalize("Chire"));
	}

	@Test
	void caseDiacriticsAndLongVowels() {
		assertEquals(Normalizer.normalize("Shukei"), Normalizer.normalize("SHŪKEI"));
		assertEquals(Normalizer.normalize("tokyo"), Normalizer.normalize("Tōkyō"));
		assertEquals(Normalizer.normalize("tokyo"), Normalizer.normalize("toukyou"));
		assertEquals(Normalizer.normalize("tokyo"), Normalizer.normalize("tookyoo"));
		assertEquals("sear", Normalizer.normalize("SEAL!"));
		assertEquals(Normalizer.normalize("cafe"), Normalizer.normalize("café"));
	}

	@Test
	void punctuationAndWhitespaceCollapse() {
		assertEquals("mae sode no sirayuki", Normalizer.normalize("  Mae,   Sode-no  \"Shirayuki\"!!  "));
		assertEquals("mae sode no sirayuki", Normalizer.normalize("Mae、Sode no Shirayuki。"));
		assertEquals("", Normalizer.normalize("   ...  "));
		assertEquals("", Normalizer.normalize(null));
		assertEquals("", Normalizer.normalize(""));
	}

	@Test
	void kanaRomanisation() {
		assertEquals(Normalizer.normalize("shashin"), Normalizer.normalize("しゃしん"));
		assertEquals(Normalizer.normalize("kitte"), Normalizer.normalize("きって"));          // sokuon doubles the consonant
		assertEquals(Normalizer.normalize("rame n"), Normalizer.normalize("らめ ん"));       // n is kept
		assertEquals(Normalizer.normalize("bankai"), Normalizer.normalize("バンカイ"));       // katakana = hiragana
		assertEquals(Normalizer.normalize("raamen"), Normalizer.normalize("ラーメン"));       // long mark repeats the vowel
		assertEquals("ha", Normalizer.normalize("は"));
		assertEquals("o", Normalizer.normalize("を"));
	}

	@Test
	void cyrillicTransliteration() {
		assertEquals(Normalizer.normalize("bankai"), Normalizer.normalize("Банкай"));
		assertEquals(Normalizer.normalize("resim ataki"), Normalizer.normalize("Режим атаки"));
		assertEquals(Normalizer.normalize("yoshi"), Normalizer.normalize("ёши"));
		assertEquals(Normalizer.normalize("mai"), Normalizer.normalize("май"));
	}

	@Test
	void foldsMakeEnglishRomajiAndRussianMeet() {
		// the same sound spelled three ways
		String a = Normalizer.normalize("Tsukishiro");
		String b = Normalizer.normalize("Цукиширо");
		String c = Normalizer.normalize("つきしろ");
		assertEquals(a, b);
		assertEquals(a, c);
		assertEquals(Normalizer.normalize("hakuren"), Normalizer.normalize("Хакурэн"));
		assertEquals(Normalizer.normalize("hakka"), Normalizer.normalize("haka"));
	}

	@Test
	void unknownKanjiAreDroppedNotGuessed() {
		assertEquals("", Normalizer.normalize("盆栽"));
		assertEquals("o keru", Normalizer.normalize("封筒を開ける"));
	}

	@Test
	void fullWidthLatinIsFolded() {
		assertEquals("bankai", Normalizer.normalize("ＢＡＮＫＡＩ"));
	}

	@Test
	void tokensAndCompact() {
		List<String> t = Normalizer.tokens(Normalizer.normalize("Mae, Sode no Shirayuki"));
		assertEquals(List.of("mae", "sode", "no", "sirayuki"), t);
		assertEquals("maesodenosirayuki", Normalizer.compact(t));
	}
}
