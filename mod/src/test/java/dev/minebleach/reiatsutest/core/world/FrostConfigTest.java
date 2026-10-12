package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class FrostConfigTest {
	@Test
	void defaultsMatchTheDesign() {
		FrostConfig c = FrostConfig.defaults();
		assertTrue(c.enabled());
		assertEquals(30, c.radius());
		assertEquals(12.0, c.waveSpeed(), 1e-9);
		assertEquals(900, c.maxTicks(), "45 s cap, the same as the bankai");
		assertEquals(50, c.waveTicks());
		assertTrue(c.blocksPerTick() <= 500, "the tick budget stays bounded");
		assertTrue(c.fastRestoreBlocksPerTick() >= c.restoreBlocksPerTick());
		assertTrue(c.maxTotalBlocks() >= c.maxBlocks());
	}

	@Test
	void jsonRoundTripAndClamping() {
		JsonObject root = new JsonObject();
		FrostConfig.defaults().writeInto(root);
		assertEquals(FrostConfig.defaults(), FrostConfig.fromRoot(root));
		JsonObject o = JsonParser.parseString("{\"frost\":{\"radius\":9999,\"waveSpeed\":-3,\"maxTicks\":\"x\",\"blocksPerTick\":1,\"enabled\":false}}").getAsJsonObject();
		FrostConfig c = FrostConfig.fromRoot(o);
		assertEquals(64, c.radius());
		assertEquals(1.0, c.waveSpeed(), 1e-9);
		assertEquals(900, c.maxTicks(), "a malformed number falls back to the default");
		assertEquals(16, c.blocksPerTick());
		assertFalse(c.enabled());
		assertEquals(FrostConfig.defaults(), FrostConfig.fromRoot(new JsonObject()));
		assertEquals(FrostConfig.defaults(), FrostConfig.fromRoot(null));
	}

	@Test
	void thawRingRunsInwardAtTheThawSpeed() {
		FrostConfig c = FrostConfig.defaults();
		assertEquals(20.0, c.thawSpeed(), 1e-9);
		assertEquals(30.0, c.thawRadiusAt(0), 1e-9);
		assertEquals(20.0, c.thawRadiusAt(10), 1e-9);
		assertEquals(0.0, c.thawRadiusAt(1000), 1e-9);
	}

	@Test
	void waveRadiusFollowsTheSpeed() {
		FrostConfig c = FrostConfig.defaults();
		assertEquals(0.0, c.waveRadiusAt(0), 1e-9);
		assertEquals(12.0, c.waveRadiusAt(20), 1e-9);
		assertEquals(30.0, c.waveRadiusAt(500), 1e-9);
	}
}
