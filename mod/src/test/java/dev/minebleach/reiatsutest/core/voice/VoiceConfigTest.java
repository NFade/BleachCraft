package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoiceConfigTest {
	@Test
	void defaultsMatchTheAdr() {
		VoiceConfig c = VoiceConfig.defaults();
		assertTrue(c.enabled);
		assertEquals(47821, c.port);
		assertEquals(0.86, c.threshold, 1e-9);
		assertEquals(1500, c.debounceMs);
		assertEquals(300, c.globalGapMs);
		assertFalse(c.interimEnabled, "final results only unless the user opts in");
		assertEquals(List.of("ja-JP", "en-US", "ru-RU"), c.languages);
	}

	@Test
	void aMissingOrPartialSectionFallsBackToDefaults() {
		assertEquals(47821, VoiceConfig.fromRoot(new JsonObject()).port);
		assertEquals(47821, VoiceConfig.fromRoot(null).port);
		JsonObject root = JsonParser.parseString("{\"other\":1,\"voice\":{\"port\":50000,\"debounceMs\":900}}").getAsJsonObject();
		VoiceConfig c = VoiceConfig.fromRoot(root);
		assertEquals(50000, c.port);
		assertEquals(900, c.debounceMs);
		assertEquals(0.86, c.threshold, 1e-9, "unset keys keep their default");
		assertEquals("en-US", c.defaultLanguage);
	}

	@Test
	void nonsenseIsClamped() {
		JsonObject root = JsonParser.parseString("{\"voice\":{\"port\":80,\"threshold\":7,\"margin\":-3,\"debounceMs\":-5,"
				+ "\"maxBodyBytes\":10,\"maxRequestsPerSecond\":0,\"languages\":[],\"defaultLanguage\":\"xx\"}}").getAsJsonObject();
		VoiceConfig c = VoiceConfig.fromRoot(root);
		assertEquals(47821, c.port, "ports below 1024 are refused");
		assertEquals(1.0, c.threshold, 1e-9);
		assertEquals(0.0, c.margin, 1e-9);
		assertEquals(0, c.debounceMs);
		assertEquals(256, c.maxBodyBytes);
		assertEquals(1, c.maxRequestsPerSecond);
		assertEquals(List.of("ja-JP", "en-US", "ru-RU"), c.languages);
		assertEquals("ja-JP", c.defaultLanguage);
	}

	@Test
	void writeKeepsOtherKeysAndRoundTrips() {
		JsonObject root = JsonParser.parseString("{\"effectQuality\":0.5}").getAsJsonObject();
		VoiceConfig c = VoiceConfig.defaults();
		c.port = 51234;
		c.languages = new java.util.ArrayList<>(List.of("en-US"));
		c.writeInto(root);
		assertEquals(0.5, root.get("effectQuality").getAsDouble(), 1e-9);
		VoiceConfig back = VoiceConfig.fromRoot(root);
		assertEquals(51234, back.port);
		assertEquals(List.of("en-US"), back.languages);
	}

	@Test
	void matcherConfigFollowsTheValues() {
		VoiceConfig c = VoiceConfig.defaults();
		c.threshold = 0.9;
		c.margin = 0.1;
		assertEquals(0.9, c.matcherConfig().threshold(), 1e-9);
		assertEquals(0.1, c.matcherConfig().margin(), 1e-9);
	}
}
