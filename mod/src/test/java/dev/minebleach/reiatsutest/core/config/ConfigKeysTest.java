package dev.minebleach.reiatsutest.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** R14: the key list and typed parse behind {@code /reiatsu dev get|set}. */
class ConfigKeysTest {
	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void leavesAreTheNumbersAndSwitchesOfTheChangeableSections() {
		JsonObject root = json("{\"configVersion\":3,\"voice\":{\"port\":1},\"rage\":{\"parryGainTenths\":50,\"name\":\"x\"},"
				+ "\"uryu\":{\"bow\":{\"damage\":3.0,\"on\":true,\"list\":[1,2]}}}");
		Map<String, JsonPrimitive> m = ConfigKeys.leaves(root);
		assertEquals(3, m.size(), m.toString());
		assertEquals(50, m.get("rage.parryGainTenths").getAsInt());
		assertTrue(m.get("uryu.bow.on").isBoolean());
		assertFalse(m.containsKey("voice.port"), "voice needs a restart");
		assertFalse(m.containsKey("configVersion"));
	}

	@Test
	void allowedSections() {
		assertTrue(ConfigKeys.allowed("rage.parryGainTenths"));
		assertTrue(ConfigKeys.allowed("balance.tsukishiro.cost"));
		assertFalse(ConfigKeys.allowed("voice.port"));
		assertFalse(ConfigKeys.allowed("rage"));
		assertFalse(ConfigKeys.allowed(null));
	}

	@Test
	void parseFollowsTheTypeOfTheCurrentValue() {
		assertEquals(30, ConfigKeys.parse("30", new JsonPrimitive(50)).getAsInt());
		assertTrue(ConfigKeys.parse("30", new JsonPrimitive(50)).getAsJsonPrimitive().toString().equals("30"), "a whole number stays whole");
		assertEquals(0.25, ConfigKeys.parse("0.25", new JsonPrimitive(1.0)).getAsDouble(), 0.0);
		assertEquals(3.0, ConfigKeys.parse("3", new JsonPrimitive(1.5)).getAsDouble(), 0.0);
		assertTrue(ConfigKeys.parse("off", new JsonPrimitive(true)).getAsBoolean() == false);
		assertTrue(ConfigKeys.parse("true", new JsonPrimitive(false)).getAsBoolean());
		assertThrows(IllegalArgumentException.class, () -> ConfigKeys.parse("maybe", new JsonPrimitive(true)));
		assertThrows(IllegalArgumentException.class, () -> ConfigKeys.parse("many", new JsonPrimitive(5)));
		assertThrows(IllegalArgumentException.class, () -> ConfigKeys.parse("NaN", new JsonPrimitive(5)));
		assertThrows(IllegalArgumentException.class, () -> ConfigKeys.parse("Infinity", new JsonPrimitive(5)));
	}

	@Test
	void setPathCreatesTheObjectsOnTheWay() {
		JsonObject root = json("{\"uryu\":{\"bow\":{\"damage\":3.0}}}");
		ConfigKeys.setPath(root, "uryu.bow.damage", new JsonPrimitive(4.5));
		ConfigKeys.setPath(root, "rukia.passive.shikai.bonusPerStack", new JsonPrimitive(0.04));
		assertEquals(4.5, root.getAsJsonObject("uryu").getAsJsonObject("bow").get("damage").getAsDouble(), 0.0);
		assertEquals(0.04, root.getAsJsonObject("rukia").getAsJsonObject("passive").getAsJsonObject("shikai").get("bonusPerStack").getAsDouble(), 0.0);
	}

	@Test
	void showIsShort() {
		assertEquals("30", ConfigKeys.show(new JsonPrimitive(30.0)));
		assertEquals("0.25", ConfigKeys.show(new JsonPrimitive(0.25)));
		assertEquals("3.333", ConfigKeys.show(new JsonPrimitive(10.0 / 3.0)));
		assertEquals("true", ConfigKeys.show(new JsonPrimitive(true)));
	}
}
