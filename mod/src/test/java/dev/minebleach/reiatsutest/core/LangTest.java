package dev.minebleach.reiatsutest.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** The en_us and ru_ru lang files carry the same keys, and every ability, item and keybind is named. */
class LangTest {
	private static final Path DIR = Paths.get("src", "main", "resources", "assets", "reiatsu_test", "lang");

	private static Set<String> keys(String file) throws IOException {
		JsonObject o = JsonParser.parseString(Files.readString(DIR.resolve(file), StandardCharsets.UTF_8)).getAsJsonObject();
		return new TreeSet<>(o.keySet());
	}

	@Test
	void bothLanguagesHaveTheSameKeys() throws IOException {
		Set<String> en = keys("en_us.json");
		Set<String> ru = keys("ru_ru.json");
		Set<String> missingInRu = new TreeSet<>(en);
		missingInRu.removeAll(ru);
		Set<String> missingInEn = new TreeSet<>(ru);
		missingInEn.removeAll(en);
		assertTrue(missingInRu.isEmpty(), "missing in ru_ru: " + missingInRu);
		assertTrue(missingInEn.isEmpty(), "missing in en_us: " + missingInEn);
	}

	@Test
	void everyAbilityItemAndKeybindHasAName() throws IOException {
		Set<String> en = keys("en_us.json");
		for (AbilityId a : AbilityId.values()) {
			assertTrue(en.contains("ability.reiatsu_test." + a.commandId), "ability name for " + a.commandId);
		}
		for (String k : new String[] {"item.reiatsu_test.sode_no_shirayuki", "item.reiatsu_test.senbonzakura",
				"key.categories.reiatsu_test", "key.reiatsu_test.release", "key.reiatsu_test.bankai", "key.reiatsu_test.seal",
				"key.reiatsu_test.ability_1", "key.reiatsu_test.ability_2", "key.reiatsu_test.ability_3", "key.reiatsu_test.draw",
				"message.reiatsu_test.denied.not_drawn", "hud.reiatsu_test.state.base", "hud.reiatsu_test.hint.draw",
				"hud.reiatsu_test.hint.release",
				"message.reiatsu_test.denied.state", "message.reiatsu_test.denied.item", "message.reiatsu_test.denied.reiatsu",
				"hud.reiatsu_test.reiatsu", "hud.reiatsu_test.state.sealed", "hud.reiatsu_test.state.shikai",
				"hud.reiatsu_test.state.bankai"}) {
			assertTrue(en.contains(k), "missing key " + k);
		}
		assertEquals(en.size(), keys("ru_ru.json").size());
	}
}
