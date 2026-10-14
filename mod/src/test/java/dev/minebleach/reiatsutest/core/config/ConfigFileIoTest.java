package dev.minebleach.reiatsutest.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R11 review fix 8: atomic writes, one lock per file, the file based migration (also of the client file). */
class ConfigFileIoTest {
	@TempDir
	Path dir;

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	private static JsonObject read(Path p) throws IOException {
		return json(Files.readString(p, StandardCharsets.UTF_8));
	}

	@Test
	void writeAtomicCreatesAndReplacesTheFileAndLeavesNoTemporaryFile() throws IOException {
		Path f = dir.resolve("sub").resolve("c.json");
		ConfigFileIo.writeAtomic(f, "{\"a\":1}");
		assertEquals("{\"a\":1}", Files.readString(f));
		ConfigFileIo.writeAtomic(f, "{\"a\":2}");
		assertEquals("{\"a\":2}", Files.readString(f));
		try (var list = Files.list(f.getParent())) {
			assertEquals(List.of("c.json"), list.map(p -> p.getFileName().toString()).toList(), "no .tmp is left behind");
		}
	}

	@Test
	void aFailedWriteLeavesTheOldFileComplete() throws IOException {
		Path f = dir.resolve("c.json");
		ConfigFileIo.writeAtomic(f, "{\"keep\":true}");
		// the temporary file name is taken by a directory: the write fails before the move, the real file is untouched
		Files.createDirectory(dir.resolve("c.json.tmp"));
		assertThrows(IOException.class, () -> ConfigFileIo.writeAtomic(f, "{\"new\":true}"));
		assertEquals("{\"keep\":true}", Files.readString(f), "never truncated, never half written");
	}

	@Test
	void writeJsonUsesPrettyPrintAndAFinalLineBreak() throws IOException {
		Path f = dir.resolve("c.json");
		JsonObject o = json("{\"a\":{\"b\":1}}");
		ConfigFileIo.writeJson(f, o);
		String s = Files.readString(f);
		assertTrue(s.contains("\n") && s.endsWith(System.lineSeparator()));
		assertEquals(o, read(f));
	}

	@Test
	void theSamePathGivesTheSameLockHoweverItIsSpelled() {
		Path a = dir.resolve("x.json");
		Path b = dir.resolve("sub").resolve("..").resolve("x.json");
		assertSame(ConfigFileIo.lockFor(a), ConfigFileIo.lockFor(b));
		assertFalse(ConfigFileIo.lockFor(a) == ConfigFileIo.lockFor(dir.resolve("y.json")));
	}

	@Test
	void concurrentReadModifyWriteCyclesUnderTheLockLoseNothing() throws Exception {
		Path f = dir.resolve("c.json");
		ConfigFileIo.writeJson(f, new JsonObject());
		int threads = 8;
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger failures = new AtomicInteger();
		Thread[] ts = new Thread[threads];
		for (int i = 0; i < threads; i++) {
			final int n = i;
			ts[i] = new Thread(() -> {
				try {
					start.await();
					for (int k = 0; k < 20; k++) {
						synchronized (ConfigFileIo.lockFor(f)) {
							JsonObject root = read(f);
							root.addProperty("t" + n + "_" + k, k);
							ConfigFileIo.writeJson(f, root);
						}
					}
				} catch (Exception e) {
					failures.incrementAndGet();
				}
			});
			ts[i].start();
		}
		start.countDown();
		for (Thread t : ts) {
			t.join();
		}
		assertEquals(0, failures.get());
		assertEquals(threads * 20, read(f).size(), "every section survived, and the file was never seen half written");
	}

	@Test
	void aMissingFileIsCreatedWithOnlyTheStamp() throws IOException {
		Path f = dir.resolve("reiatsu_test_client.json");
		assertNull(ConfigFileIo.migrateFile(f, ConfigMigrations.CLIENT_VERSION, ConfigMigrations::migrateClient));
		assertEquals(ConfigMigrations.CLIENT_VERSION, read(f).get("configVersion").getAsInt());
		assertEquals(1, read(f).size());
	}

	@Test
	void anUnstampedClientFileGetsTheStampKeepsEveryKeyAndIsBackedUpOnce() throws IOException {
		Path f = dir.resolve("reiatsu_test_client.json");
		String original = "{\"show_first_person_hand\":true,\"first_person_scale_multiplier\":1.5,\"voice\":{\"client\":{\"port\":47900}},"
				+ "\"tp\":{\"swordScale\":1.4,\"hold.tilt\":15},\"pressure\":{\"shake\":0.5}}";
		Files.writeString(f, original);
		ConfigMigrations.Result r = ConfigFileIo.migrateFile(f, ConfigMigrations.CLIENT_VERSION, ConfigMigrations::migrateClient);
		assertNotNull(r);
		assertEquals(1, r.fromVersion());
		JsonObject now = read(f);
		JsonObject before = json(original);
		for (String k : before.keySet()) {
			assertEquals(before.get(k), now.get(k), "key " + k + " must survive the migration");
		}
		assertEquals(ConfigMigrations.CLIENT_VERSION, now.get("configVersion").getAsInt());
		Path bak = dir.resolve("reiatsu_test_client.json.bak_v1");
		assertEquals(original, Files.readString(bak), "the backup is the file as it was");
		// a second run does nothing and does not touch the backup
		assertNull(ConfigFileIo.migrateFile(f, ConfigMigrations.CLIENT_VERSION, ConfigMigrations::migrateClient));
		assertEquals(original, Files.readString(bak));
	}

	@Test
	void anExplicitRuleTableMigratesTheFileAndOnlyOldDefaults() throws IOException {
		Path f = dir.resolve("c.json");
		Files.writeString(f, "{\"tp\":{\"swordScale\":1.25},\"keep\":7}");
		var rules = List.of(new ConfigMigrations.Rule(2, "tp.swordScale", new double[] {1.25}, 1.3, "test"));
		ConfigMigrations.Result r = ConfigFileIo.migrateFile(f, 2, ConfigFileIo.with(rules, 2));
		assertEquals(1, r.changed().size());
		assertEquals(1.3, read(f).getAsJsonObject("tp").get("swordScale").getAsDouble());
		assertEquals(7, read(f).get("keep").getAsInt());
	}

	@Test
	void aFileThatDoesNotParseIsLeftAlone() throws IOException {
		Path f = dir.resolve("c.json");
		Files.writeString(f, "{ this is not json");
		assertThrows(JsonParseException.class, () -> ConfigFileIo.migrateFile(f, 2, ConfigMigrations::migrateClient));
		assertEquals("{ this is not json", Files.readString(f));
		assertFalse(Files.exists(dir.resolve("c.json.bak_v1")));
		Path arr = dir.resolve("arr.json");
		Files.writeString(arr, "[1,2]");
		assertNull(ConfigFileIo.migrateFile(arr, 2, ConfigMigrations::migrateClient));
		assertEquals("[1,2]", Files.readString(arr));
	}

	@Test
	void aFileAtTheCurrentVersionIsNotRewritten() throws IOException {
		Path f = dir.resolve("c.json");
		String text = "{\"configVersion\":" + ConfigMigrations.CLIENT_VERSION + ",\"x\":1}";
		Files.writeString(f, text);
		assertNull(ConfigFileIo.migrateFile(f, ConfigMigrations.CLIENT_VERSION, ConfigMigrations::migrateClient));
		assertEquals(text, Files.readString(f));
	}
}
