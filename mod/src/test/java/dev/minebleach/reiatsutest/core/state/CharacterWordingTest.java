package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Uryu is a Quincy: his state words and denial texts have their own lang keys; the other characters keep the shared ones. */
class CharacterWordingTest {
	private static final Path DIR = Paths.get("src", "main", "resources", "assets", "reiatsu_test", "lang");

	private static JsonObject lang(String file) throws IOException {
		return JsonParser.parseString(Files.readString(DIR.resolve(file), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@Test
	void uryuStatesAreHeiligBogenBlutArterieAndVollstaendig() throws IOException {
		JsonObject en = lang("en_us.json");
		JsonObject ru = lang("ru_ru.json");
		assertEquals("Heilig Bogen", en.get(CharacterWording.stateKey(CharacterId.URYU, ZanpakutoState.BASE)).getAsString());
		assertEquals("Blut Arterie", en.get(CharacterWording.stateKey(CharacterId.URYU, ZanpakutoState.SHIKAI)).getAsString());
		assertEquals("Quincy: Vollständig", en.get(CharacterWording.stateKey(CharacterId.URYU, ZanpakutoState.BANKAI)).getAsString());
		assertEquals("Хайлиг Боген",
				ru.get(CharacterWording.stateKey(CharacterId.URYU, ZanpakutoState.BASE)).getAsString());
		assertEquals("Блут Артерие",
				ru.get(CharacterWording.stateKey(CharacterId.URYU, ZanpakutoState.SHIKAI)).getAsString());
		assertEquals("Квинси: Фолльштендиг",
				ru.get(CharacterWording.stateKey(CharacterId.URYU, ZanpakutoState.BANKAI)).getAsString());
	}

	@Test
	void sealedKeepsTheSharedWordAndTheOthersAreUntouched() {
		assertEquals("hud.reiatsu_test.state.sealed", CharacterWording.stateKey(CharacterId.URYU, ZanpakutoState.SEALED));
		for (CharacterId c : CharacterId.values()) {
			if (c == CharacterId.URYU) {
				continue;
			}
			for (ZanpakutoState s : ZanpakutoState.values()) {
				assertEquals("hud.reiatsu_test.state." + s.name().toLowerCase(java.util.Locale.ROOT), CharacterWording.stateKey(c, s));
			}
			assertEquals("hud.reiatsu_test.hint.release", CharacterWording.key("hud.reiatsu_test.hint.release", c));
		}
		assertEquals("hud.reiatsu_test.state.shikai", CharacterWording.stateKey(null, ZanpakutoState.SHIKAI));
		assertEquals("hud.reiatsu_test.reiatsu", CharacterWording.key("hud.reiatsu_test.reiatsu", CharacterId.URYU), "no variant: unchanged");
	}

	@Test
	void everyVariantExistsInBothLanguagesAndDiffersFromTheSharedText() throws IOException {
		JsonObject en = lang("en_us.json");
		JsonObject ru = lang("ru_ru.json");
		for (CharacterId c : CharacterWording.characters()) {
			for (String base : CharacterWording.variantsOf(c)) {
				String key = CharacterWording.key(base, c);
				assertNotEquals(base, key, base);
				for (JsonObject l : new JsonObject[] {en, ru}) {
					assertTrue(l.has(base), "base key " + base);
					assertTrue(l.has(key), "variant " + key);
				}
			}
		}
	}

	@Test
	void theQuincyTextsNeverSayShikaiBankaiOrSword() throws IOException {
		String[] bad = {"shikai", "bankai", "sword", "zanpakuto", "шикай", "банкай",
				"меч", "занпакто", "дзанпакто"};
		for (String file : new String[] {"en_us.json", "ru_ru.json"}) {
			JsonObject l = lang(file);
			for (String base : CharacterWording.variantsOf(CharacterId.URYU)) {
				String text = l.get(base + ".uryu").getAsString().toLowerCase(java.util.Locale.ROOT);
				for (String b : bad) {
					assertFalse(text.contains(b), file + " " + base + ".uryu says '" + b + "': " + text);
				}
			}
		}
	}
}
