package dev.minebleach.reiatsutest.data;

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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Better Combat presets (design/results/bc3.md): one data/reiatsu_test/weapon_attributes/{item}.json per zanpakuto item, the file
 * name is the item id path, each file references a Better Combat preset by parent. Pure file checks, no Minecraft classes.
 */
class WeaponAttributesPresetTest {
	private static final Path ROOT = Paths.get("src", "main");
	private static final Path DIR = ROOT.resolve("resources/data/reiatsu_test/weapon_attributes");
	private static final Path MOD_ITEMS = ROOT.resolve("java/dev/minebleach/reiatsutest/registry/ModItems.java");
	private static final Pattern ZANPAKUTO = Pattern.compile("register\\(\"([a-z0-9_]+)\",\\s*CharacterId\\.");

	/** Item id paths registered through {@code ModItems.register(name, CharacterId)}: the zanpakuto swords. */
	private static Set<String> swordIds() throws IOException {
		Set<String> ids = new TreeSet<>();
		Matcher m = ZANPAKUTO.matcher(Files.readString(MOD_ITEMS, StandardCharsets.UTF_8));
		while (m.find()) {
			ids.add(m.group(1));
		}
		return ids;
	}

	private static Set<String> presetFiles() throws IOException {
		Set<String> names = new TreeSet<>();
		try (Stream<Path> s = Files.list(DIR)) {
			s.forEach(p -> {
				String n = p.getFileName().toString();
				assertTrue(n.endsWith(".json"), "not a json file: " + n);
				names.add(n.substring(0, n.length() - ".json".length()));
			});
		}
		return names;
	}

	@Test
	void everySwordHasExactlyOnePresetFileAndNothingElse() throws IOException {
		Set<String> swords = swordIds();
		assertFalse(swords.isEmpty(), "no zanpakuto registrations found in ModItems.java");
		assertTrue(swords.contains("sode_no_shirayuki") && swords.contains("senbonzakura"), "found " + swords);
		assertEquals(swords, presetFiles(), "weapon_attributes files must match the registered swords by id path");
	}

	@Test
	void presetFilesParseAndReferenceABetterCombatPreset() throws IOException {
		for (String id : swordIds()) {
			JsonElement root = JsonParser.parseString(Files.readString(DIR.resolve(id + ".json"), StandardCharsets.UTF_8));
			assertTrue(root.isJsonObject(), id + ": root is not an object");
			JsonObject o = root.getAsJsonObject();
			assertTrue(o.has("parent") && o.get("parent").isJsonPrimitive(), id + ": parent missing");
			String parent = o.get("parent").getAsString();
			assertTrue(parent.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"), id + ": parent is not a namespaced id: " + parent);
			assertEquals("bettercombat:katana", parent, id + ": katana preset expected");
		}
	}

	/** BC5 decision 1: an empty (non-null, so it overrides the parent) pose means no Better Combat katana stance, the sword is held one handed as usual. */
	@Test
	void presetsOverrideThePoseWithAnEmptyString() throws IOException {
		for (String id : swordIds()) {
			JsonObject o = JsonParser.parseString(Files.readString(DIR.resolve(id + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
			assertTrue(o.has("attributes") && o.get("attributes").isJsonObject(), id + ": attributes object missing");
			JsonObject attrs = o.getAsJsonObject("attributes");
			assertTrue(attrs.has("pose") && attrs.get("pose").isJsonPrimitive(), id + ": pose missing");
			assertEquals("", attrs.get("pose").getAsString(), id + ": pose must be empty");
			assertFalse(attrs.has("attacks"), id + ": attacks stay inherited from the katana preset");
		}
	}
}
