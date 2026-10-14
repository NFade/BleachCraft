package dev.minebleach.reiatsutest.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.kido.KidoConfig;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import org.junit.jupiter.api.Test;

class ConfigMigrationsTest {
	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void oldDefaultsAreReplacedAndTheVersionIsStamped() {
		JsonObject root = json("{\"kido\":{\"shakkaho\":{\"damage\":2.0,\"fireTicks\":60,\"cooldownTicks\":140}}}");
		ConfigMigrations.Result r = ConfigMigrations.migrateServer(root);
		JsonObject s = root.getAsJsonObject("kido").getAsJsonObject("shakkaho");
		assertEquals(1.0, s.get("damage").getAsDouble());
		assertEquals(15, s.get("fireTicks").getAsInt());
		assertEquals(140, s.get("cooldownTicks").getAsInt(), "a key with no rule is never touched");
		assertEquals(ConfigMigrations.SERVER_VERSION, root.get("configVersion").getAsInt());
		assertEquals(2, r.changed().size());
		assertEquals(1, r.fromVersion());
	}

	@Test
	void theDevValueOfTheDuelTestIsResetToo() {
		JsonObject root = json("{\"kido\":{\"shakkaho\":{\"damage\":5.0}}}");
		ConfigMigrations.migrateServer(root);
		assertEquals(1.0, root.getAsJsonObject("kido").getAsJsonObject("shakkaho").get("damage").getAsDouble());
	}

	@Test
	void aValueTheUserChangedStays() {
		JsonObject root = json("{\"kido\":{\"shakkaho\":{\"damage\":3.3,\"fireTicks\":100}}}");
		ConfigMigrations.Result r = ConfigMigrations.migrateServer(root);
		JsonObject s = root.getAsJsonObject("kido").getAsJsonObject("shakkaho");
		assertEquals(3.3, s.get("damage").getAsDouble());
		assertEquals(100, s.get("fireTicks").getAsInt());
		assertTrue(r.changed().isEmpty());
		assertTrue(r.touched(), "the version stamp itself still changes");
	}

	@Test
	void aCurrentFileIsNotTouched() {
		JsonObject root = json("{\"configVersion\":" + ConfigMigrations.SERVER_VERSION + ",\"kido\":{\"shakkaho\":{\"damage\":2.0}}}");
		ConfigMigrations.Result r = ConfigMigrations.migrateServer(root);
		assertFalse(r.touched());
		assertEquals(2.0, root.getAsJsonObject("kido").getAsJsonObject("shakkaho").get("damage").getAsDouble());
	}

	@Test
	void missingKeysAndSectionsAreSkippedWithoutError() {
		JsonObject root = json("{\"voice\":{}}");
		ConfigMigrations.Result r = ConfigMigrations.migrateServer(root);
		assertTrue(r.changed().isEmpty());
		assertFalse(root.has("kido"), "a migration never invents a section: the loader writes the defaults");
	}

	@Test
	void junkVersionCountsAsOne() {
		assertEquals(1, ConfigMigrations.versionOf(json("{\"configVersion\":\"x\"}")));
		assertEquals(1, ConfigMigrations.versionOf(json("{\"configVersion\":0}")));
		assertEquals(1, ConfigMigrations.versionOf(json("{\"configVersion\":2.5}")));
		assertEquals(3, ConfigMigrations.versionOf(json("{\"configVersion\":3}")));
	}

	@Test
	void everyRuleTargetsTheCurrentCodeDefault() {
		JsonObject kido = new JsonObject();
		kido.add("kido", KidoConfig.defaults().toJson());
		for (ConfigMigrations.Rule r : ConfigMigrations.serverRules()) {
			if (!r.path().startsWith("kido.")) {
				continue;
			}
			String[] p = r.path().split("[.]");
			double code = kido.getAsJsonObject("kido").getAsJsonObject(p[1]).get(p[2]).getAsDouble();
			assertEquals(r.value(), code, 1e-9, r.path() + " rule value must equal the code default");
			for (double old : r.olds()) {
				assertTrue(Math.abs(old - r.value()) > 1e-9, r.path() + " old value equals the new default: the rule does nothing");
			}
		}
		assertEquals(1.0, KidoConfig.defaults().spec(KidoId.SHAKKAHO).damage());
		assertEquals(15, KidoConfig.defaults().spec(KidoId.SHAKKAHO).fireTicks());
	}

	@Test
	void ruleVersionsStayInsideTheCurrentVersion() {
		for (ConfigMigrations.Rule r : ConfigMigrations.serverRules()) {
			assertTrue(r.since() >= 2 && r.since() <= ConfigMigrations.SERVER_VERSION, r.path());
		}
		for (ConfigMigrations.Rule r : ConfigMigrations.clientRules()) {
			assertTrue(r.since() >= 2 && r.since() <= ConfigMigrations.CLIENT_VERSION, r.path());
		}
	}
}
