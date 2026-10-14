package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** R11 T0/T2: the {@code mech} config section. */
class MechSpecConfigTest {
	private static JsonObject root(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void missingSectionIsTheDefaults() {
		assertEquals(MechSpec.defaults(), MechSpec.fromRoot(root("{}")));
	}

	@Test
	void writtenDefaultsReadBackUnchanged() {
		JsonObject r = new JsonObject();
		r.add(MechSpec.SECTION, MechSpec.defaults().toJson());
		assertEquals(MechSpec.defaults(), MechSpec.fromRoot(r));
	}

	@Test
	void valuesAreReadAndClamped() {
		MechSpec m = MechSpec.fromRoot(root("{\"mech\":{\"meleeHitReiatsu\":7.5,\"parryReiatsu\":999,\"meleeHitMaxPerSecond\":-4,\"parryGapTicks\":3}}"));
		assertEquals(7.5, m.meleeHitReiatsu(), 1e-9);
		assertEquals(100.0, m.parryReiatsu(), 1e-9);
		assertEquals(0.0, m.meleeHitMaxPerSecond(), 1e-9);
		assertEquals(3, m.parryGapTicks());
		assertEquals(MechSpec.defaults().abilityBlockReduction(), m.abilityBlockReduction(), 1e-9, "untouched keys keep their default");
	}

	@Test
	void junkFallsBackToTheDefault() {
		MechSpec m = MechSpec.fromRoot(root("{\"mech\":{\"meleeHitReiatsu\":\"lots\",\"parryReiatsu\":null,\"parryGapTicks\":[1]}}"));
		assertEquals(MechSpec.defaults(), m);
		assertEquals(MechSpec.defaults(), MechSpec.fromRoot(root("{\"mech\":5}")));
	}

	@Test
	void theDefaultsAreTheR11Numbers() {
		MechSpec d = MechSpec.defaults();
		assertEquals(5.0, d.meleeHitReiatsu(), 1e-9);
		assertEquals(10.0, d.parryReiatsu(), 1e-9);
		assertEquals(15.0, d.meleeHitMaxPerSecond(), 1e-9);
	}
}
