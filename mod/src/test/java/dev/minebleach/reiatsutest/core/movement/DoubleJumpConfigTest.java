package dev.minebleach.reiatsutest.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DoubleJumpConfigTest {
	private static JsonObject parse(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	@Test
	void defaultsAreOnAndNormalStrength() {
		DoubleJumpConfig d = DoubleJumpConfig.defaults();
		assertTrue(d.enabled());
		assertEquals(1.0, d.strength());
		assertEquals(d, DoubleJumpConfig.fromRoot(new JsonObject()));
	}

	@Test
	void readsTheSection() {
		DoubleJumpConfig c = DoubleJumpConfig.fromRoot(parse("{\"doubleJump\":{\"enabled\":false,\"strength\":1.5}}"));
		assertFalse(c.enabled());
		assertEquals(1.5, c.strength());
	}

	@Test
	void badValuesGiveDefaultsAndStrengthIsClamped() {
		assertEquals(DoubleJumpConfig.defaults(), DoubleJumpConfig.fromRoot(parse("{\"doubleJump\":{\"enabled\":\"yes\",\"strength\":\"big\"}}")));
		assertEquals(DoubleJumpConfig.defaults(), DoubleJumpConfig.fromRoot(parse("{\"doubleJump\":5}")));
		assertEquals(DoubleJumpConfig.MAX_STRENGTH, DoubleJumpConfig.fromRoot(parse("{\"doubleJump\":{\"strength\":99}}")).strength());
		assertEquals(DoubleJumpConfig.MIN_STRENGTH, DoubleJumpConfig.fromRoot(parse("{\"doubleJump\":{\"strength\":-3}}")).strength());
		assertEquals(1.0, new DoubleJumpConfig(true, Double.NaN).strength());
	}

	@Test
	void roundTripsThroughJson() {
		DoubleJumpConfig c = new DoubleJumpConfig(false, 0.8);
		JsonObject root = new JsonObject();
		root.add(DoubleJumpConfig.SECTION, c.toJson());
		assertEquals(c, DoubleJumpConfig.fromRoot(root));
	}
}
