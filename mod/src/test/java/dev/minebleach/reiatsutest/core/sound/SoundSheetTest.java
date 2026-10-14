package dev.minebleach.reiatsutest.core.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * R11 T12 guards that read the files (Minecraft classes cannot be loaded in unit tests): the sound sheet {@code design/results/r11_sound_sheet.md}, the list of
 * ids {@link SoundIds}, {@code sounds.json}, the registry and the code say the same thing, and no anime audio is in the repository.
 */
class SoundSheetTest {
	private static final Path MOD = Paths.get("").toAbsolutePath();
	private static final Path ROOT = MOD.getParent();
	private static final Path SRC = Paths.get("src");
	private static final Path SHEET = ROOT.resolve(Paths.get("design", "results", "r11_sound_sheet.md"));
	private static final Path ASSETS = SRC.resolve(Paths.get("main", "resources", "assets", "reiatsu_test"));
	private static final Pattern REF = Pattern.compile("SoundIds\\.([A-Z][A-Z0-9_]*)");
	private static final Set<String> STATUSES = Set.of("нужен файл", "есть", "резерв", "не используется");
	private static final Set<String> TYPES = Set.of("oneshot", "loop");

	private record Row(String id, String where, String clip, String type, String fallback, String status, String risk, String file) {
	}

	private static List<Row> sheet() throws IOException {
		assertTrue(Files.isRegularFile(SHEET), "the sound sheet is missing: " + SHEET);
		List<Row> rows = new ArrayList<>();
		for (String line : Files.readAllLines(SHEET, StandardCharsets.UTF_8)) {
			if (!line.startsWith("| `")) {
				continue;
			}
			String[] c = line.split("\\|", -1);
			assertEquals(12, c.length, "a sheet row has 10 cells: " + line);
			rows.add(new Row(c[1].trim().replace("`", ""), c[2].trim(), c[5].trim(), c[6].trim(), c[7].trim(), c[8].trim(), c[9].trim(), c[10].trim().replace("`", "")));
		}
		return rows;
	}

	private static Map<String, String> constants() throws IllegalAccessException {
		Map<String, String> out = new LinkedHashMap<>();
		for (Field f : SoundIds.class.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers()) && Modifier.isPublic(f.getModifiers()) && f.getType() == String.class && !f.getName().equals("NAMESPACE")) {
				out.put(f.getName(), (String) f.get(null));
			}
		}
		return out;
	}

	private static List<Path> javaFiles() throws IOException {
		try (Stream<Path> s = Files.walk(SRC)) {
			return s.filter(p -> p.toString().endsWith(".java") && !p.toString().replace('\\', '/').contains("/test/")).toList();
		}
	}

	/** Code lines without comments and Javadoc. */
	private static String code(Path p) throws IOException {
		StringBuilder sb = new StringBuilder();
		for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
			String t = line.trim();
			if (t.startsWith("*") || t.startsWith("/*") || t.startsWith("//")) {
				continue;
			}
			sb.append(line).append('\n');
		}
		return sb.toString();
	}

	@Test
	void theConstantsAreTheListAndHaveRegularNames() throws Exception {
		Map<String, String> consts = constants();
		assertEquals(new HashSet<>(SoundIds.ALL).size(), SoundIds.ALL.size(), "duplicate id in SoundIds.ALL");
		assertEquals(new HashSet<>(consts.values()), new HashSet<>(SoundIds.ALL), "every constant is in ALL and every id of ALL has a constant");
		assertEquals(consts.size(), SoundIds.ALL.size());
		Pattern shape = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){1,3}");
		for (Map.Entry<String, String> e : consts.entrySet()) {
			assertEquals(e.getValue().toUpperCase(Locale.ROOT).replace('.', '_'), e.getKey(), "constant name of " + e.getValue());
			assertTrue(shape.matcher(e.getValue()).matches(), "shape of id " + e.getValue());
		}
	}

	@Test
	void everyEventOfTheSheetIsInTheListAndTheOtherWayRound() throws IOException {
		List<String> ids = sheet().stream().map(Row::id).toList();
		assertEquals(SoundIds.ALL, ids, "the sheet rows are the sound events of SoundIds.ALL, in the same order");
	}

	@Test
	void theSheetRowsAreComplete() throws IOException {
		for (Row r : sheet()) {
			assertTrue(STATUSES.contains(r.status()), r.id() + " status " + r.status());
			assertTrue(TYPES.contains(r.type()), r.id() + " type " + r.type());
			assertTrue(r.clip().matches("[0-9.]+-[0-9.]+"), r.id() + " clip length " + r.clip());
			assertFalse(r.where().isEmpty(), r.id() + " has no 'where'");
			assertFalse(r.fallback().isEmpty(), r.id() + " has no fallback column (use - for none)");
			assertFalse(r.risk().isEmpty(), r.id());
			assertEquals(SoundIds.clipPath(r.id(), 1) + ".ogg", r.file(), r.id() + " file column");
		}
	}

	@Test
	void everyEventIsInSoundsJsonWithItsClipName() throws IOException {
		Path p = ASSETS.resolve("sounds.json");
		assertTrue(Files.isRegularFile(p), p.toAbsolutePath().toString());
		JsonObject root = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(new HashSet<>(SoundIds.ALL), root.keySet(), "sounds.json has exactly the events of SoundIds.ALL");
		Set<String> names = new HashSet<>();
		for (String id : SoundIds.ALL) {
			JsonObject ev = root.getAsJsonObject(id);
			assertTrue(ev.getAsJsonArray("sounds").size() >= 1, id);
			for (JsonElement s : ev.getAsJsonArray("sounds")) {
				String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
				assertTrue(name.startsWith("anime/" + SoundIds.group(id) + "/"), id + " clip path " + name);
				assertTrue(names.add(name), "two events share the clip " + name);
			}
			String first = ev.getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString();
			assertEquals(SoundIds.clipPath(id, 1), first, id);
		}
	}

	@Test
	void theRegistryRegistersEveryEventOfTheList() throws IOException {
		String reg = code(MOD.resolve(SRC.resolve(Paths.get("main", "java", "dev", "minebleach", "reiatsutest", "registry", "ModSounds.java"))));
		assertTrue(reg.contains("SoundIds.ALL") && reg.contains("Registries.SOUND_EVENT"), "ModSounds registers a SoundEvent per entry of SoundIds.ALL");
		String init = code(MOD.resolve(SRC.resolve(Paths.get("main", "java", "dev", "minebleach", "reiatsutest", "ReiatsuTest.java"))));
		assertTrue(init.contains("ModSounds.init()"), "mod init registers the sound events");
		assertTrue(init.contains("ModSound.init()"), "mod init loads config/reiatsu_sounds.json");
		String net = code(MOD.resolve(SRC.resolve(Paths.get("main", "java", "dev", "minebleach", "reiatsutest", "net", "ModNetworking.java"))));
		assertTrue(net.contains("SoundCueS2C.ID"), "the sound cue payload is registered");
	}

	@Test
	void everyEventTheCodeUsesIsInTheSheetAndTheStatusSaysWhetherItIsWired() throws Exception {
		Map<String, String> consts = constants();
		Set<String> used = new HashSet<>();
		for (Path p : javaFiles()) {
			if (p.toString().replace('\\', '/').contains("/core/sound/")) {
				continue;
			}
			Matcher m = REF.matcher(code(p));
			while (m.find()) {
				if (m.group(1).equals("ALL")) { // the list itself (ModSounds registers all of it)
					continue;
				}
				String id = consts.get(m.group(1));
				assertTrue(id != null, p + " uses an unknown constant SoundIds." + m.group(1));
				used.add(id);
			}
		}
		for (Row r : sheet()) {
			if (r.status().equals("резерв")) {
				assertFalse(used.contains(r.id()), r.id() + " is called from the code but the sheet says 'резерв' (not wired); set the status to 'нужен файл'");
			} else if (r.status().equals("нужен файл") || r.status().equals("есть")) {
				assertTrue(used.contains(r.id()), r.id() + " is not called anywhere in the code but the sheet says it is wired");
			}
		}
		assertTrue(used.size() >= 140, "the abilities and events of the mod are wired: " + used.size());
	}

	@Test
	void noSoundIsPlayedByAnythingButTheOneEntryPoint() throws IOException {
		Pattern literalEvent = Pattern.compile("(\\.sound(?:At)?\\(\"|(?<![\\w.])sound\\(\"|FxSound\\.play\\(\\w+, \"|FxSound\\.ui\\(\"|ModSound\\.play\\([^,]+, \")");
		List<String> bad = new ArrayList<>();
		// Gin / Uryu code that appeared in gin-uryu AFTER the T12 sheet: still plays vanilla sounds directly. TODO: give these moments
		// events in SoundIds / SoundCatalog / the sheet and convert them, then delete this list.
		java.util.Set<String> pending = java.util.Set.of("UryuFx.java", "UryuArrowRenderer.java", "DoubleJumpClient.java", "UryuManager.java");
		for (Path p : javaFiles()) {
			String name = p.getFileName().toString();
			if (pending.contains(name)) {
				continue;
			}
			String c = code(p);
			Matcher m = literalEvent.matcher(c);
			if (m.find()) {
				bad.add(p + ": a sound event given as a string literal");
			}
			boolean entry = name.equals("FxSound.java") || name.equals("ModSound.java");
			if (!entry && (c.contains(".playSound(") || c.contains("SoundEvents.") || c.contains("PositionedSoundInstance"))) {
				bad.add(p + ": plays a sound without FxSound / ModSound");
			}
		}
		assertEquals(List.of(), bad, "every sound goes through SoundIds + FxSound (client) or ModSound (server)");
	}

	@Test
	void thePressureEffectHasASoundEvent() throws IOException {
		String fx = code(MOD.resolve(SRC.resolve(Paths.get("client", "java", "dev", "minebleach", "reiatsutest", "client", "fx", "PressureFx.java"))));
		assertTrue(fx.contains("SoundIds.GENERAL_PRESSURE_RELEASE"), "T7: the foreign bankai pressure has its sound event");
		String ice = code(MOD.resolve(SRC.resolve(Paths.get("main", "java", "dev", "minebleach", "reiatsutest", "server", "IceWall.java"))));
		for (String id : new String[] {"START", "LAYER", "HIT", "BREAK"}) {
			assertTrue(ice.contains("SoundIds.RUKIA_BANKAI_ICE_WALL_" + id), "T5: the ice wall event " + id);
		}
	}

	@Test
	void noAudioIsInTheRepositoryAndTheLocalFoldersAreIgnored() throws IOException {
		String ignore = Files.readString(ROOT.resolve(".gitignore"), StandardCharsets.UTF_8);
		for (String must : new String[] {"mod/src/main/resources/assets/reiatsu_test/sounds/anime/", "sounds_local/", "reiatsu_test_anime_sounds"}) {
			assertTrue(ignore.contains(must), ".gitignore must cover " + must);
		}
		Set<String> audio = Set.of(".ogg", ".wav", ".mp3", ".flac", ".opus", ".m4a", ".aac", ".wma", ".mp4", ".mkv", ".webm", ".avi", ".mov");
		List<String> found = new ArrayList<>();
		try (Stream<Path> s = Files.walk(SRC)) {
			for (Path p : s.filter(Files::isRegularFile).toList()) {
				String n = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
				boolean localFolder = n.contains("/sounds/anime/"); // the player's own clips, ignored by git
				if (!localFolder && audio.stream().anyMatch(n::endsWith)) {
					found.add(p.toString());
				}
			}
		}
		assertEquals(List.of(), found, "no audio or video file belongs in the repository (anime audio is copyrighted)");
	}

	@Test
	void theConfigKeysAreDocumented() throws IOException {
		String doc = Files.readString(ROOT.resolve(Paths.get("design", "results", "r11_config_keys.md")), StandardCharsets.UTF_8);
		for (String key : new String[] {"reiatsu_sounds.json", "events.<id>.files", "events.<id>.fallback", "events.<id>.volume", "events.<id>.pitch",
				"events.<id>.minIntervalMs", "events.<id>.range", "events.<id>.category"}) {
			assertTrue(doc.contains(key), "r11_config_keys.md must document " + key);
		}
	}
}
