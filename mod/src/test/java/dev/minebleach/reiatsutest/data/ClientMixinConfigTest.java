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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * BC5: the client mixin list matches the mixin sources one to one (a removed mixin, like the old third person slash on
 * {@code BipedEntityModel}, must not stay listed; a new one must be listed), and our own slash (T4) is gone for good.
 * Pure file checks, no Minecraft classes.
 */
class ClientMixinConfigTest {
	private static final Path CONFIG = Paths.get("src", "main", "resources", "reiatsu_test.client.mixins.json");
	private static final Path DIR = Paths.get("src", "client", "java", "dev", "minebleach", "reiatsutest", "client", "mixin");
	private static final Path MODEL = Paths.get("src", "client", "java", "dev", "minebleach", "reiatsutest", "client", "model");

	private static Set<String> listed() throws IOException {
		JsonObject o = JsonParser.parseString(Files.readString(CONFIG, StandardCharsets.UTF_8)).getAsJsonObject();
		Set<String> names = new TreeSet<>();
		for (JsonElement e : o.getAsJsonArray("client")) {
			names.add(e.getAsString());
		}
		return names;
	}

	@Test
	void listedMixinsMatchTheSourceFiles() throws IOException {
		Set<String> files = new TreeSet<>();
		try (Stream<Path> s = Files.list(DIR)) {
			s.forEach(p -> files.add(p.getFileName().toString().replace(".java", "")));
		}
		assertEquals(files, listed(), "client mixin config and the mixin package must list the same classes");
	}

	@Test
	void ourOwnSlashIsGone() throws IOException {
		assertFalse(listed().contains("BipedEntityModelMixin"));
		assertFalse(Files.exists(DIR.resolve("BipedEntityModelMixin.java")));
		assertFalse(Files.exists(MODEL.resolve("SwingPose.java")));
		assertTrue(listed().contains("BcFirstPersonModeMixin"), "the first person view guard for Better Combat strikes must stay listed");
	}
}
