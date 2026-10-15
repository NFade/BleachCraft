package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** R12: the two key files (mode B and mode A) differ only in {@code petalBlade.mode}, and carry the defaults of the code. */
class PetalBladeKeysTest {
	private static JsonObject load(String name) throws IOException {
		return JsonParser.parseString(Files.readString(Path.of(name))).getAsJsonObject();
	}

	@Test
	void bothModeFilesHaveTheSameKeysAndOnlyTheModeDiffers() throws IOException {
		JsonObject b = load("run_tune_r12_petals_B.json");
		JsonObject a = load("run_tune_r12_petals_A.json");
		assertEquals(b.keySet(), a.keySet());
		for (String k : b.keySet()) {
			if (k.equals("petalBlade.mode")) {
				continue;
			}
			assertEquals(b.get(k), a.get(k), k);
		}
		assertEquals(1, b.get("petalBlade.mode").getAsInt(), "B holds N seconds");
		assertEquals(0, a.get("petalBlade.mode").getAsInt(), "A is only for the strike");
	}

	@Test
	void theFileValuesAreTheDefaultsOfTheCode() throws IOException {
		JsonObject b = load("run_tune_r12_petals_B.json");
		PetalBladeMath.Params p = new PetalBladeMath.Params();
		assertEquals(p.hold, b.get("petalBlade.hold").getAsDouble(), 0);
		assertEquals(p.holdA, b.get("petalBlade.holdA").getAsDouble(), 0);
		assertEquals(p.assemble, b.get("petalBlade.assemble").getAsDouble(), 0);
		assertEquals(p.disperse, b.get("petalBlade.disperse").getAsDouble(), 0);
		assertEquals(p.settle, b.get("petalBlade.settle").getAsDouble(), 0);
		assertEquals(p.lead, b.get("petalBlade.lead").getAsDouble(), 0);
		assertEquals(p.sweep, b.get("petalBlade.sweep").getAsDouble(), 0);
		SwarmView.Fp fp = new SwarmView.Fp();
		assertEquals(fp.idleClear, b.get("swarm.fpIdleClear").getAsDouble(), 0);
		assertEquals(fp.idleEdge, b.get("swarm.fpIdleEdge").getAsDouble(), 0);
		assertEquals(fp.idleDensity, b.get("swarm.fpIdleDensity").getAsDouble(), 0);
		assertEquals(fp.moveClear, b.get("swarm.fpMoveClear").getAsDouble(), 0);
		SwarmView.Tp tp = new SwarmView.Tp();
		assertEquals(tp.keep, b.get("swarm.tpKeep").getAsDouble(), 0);
		assertEquals(tp.glow, b.get("swarm.tpGlow").getAsDouble(), 0);
		assertEquals(tp.swordClear, b.get("swarm.swordClear").getAsDouble(), 0);
		SwarmMath.RibbonParams rb = new SwarmMath.RibbonParams();
		assertEquals(rb.streams, b.get("swarm.rbStreams").getAsInt());
		assertEquals(rb.radius, b.get("swarm.rbRadius").getAsDouble(), 0);
		assertEquals(rb.amp, b.get("swarm.rbAmp").getAsDouble(), 0);
		assertEquals(rb.tilt, b.get("swarm.rbTilt").getAsDouble(), 0);
		assertEquals(rb.band, b.get("swarm.rbBand").getAsDouble(), 0);
		assertEquals(rb.twist, b.get("swarm.rbTwist").getAsDouble(), 0);
		assertTrue(b.size() > 40);
	}
}
