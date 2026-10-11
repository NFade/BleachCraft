package dev.minebleach.reiatsutest.core.kido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KidoConfigTest {
	@Test
	void idsAreUniqueAndDoNotCollideWithAbilitiesOrEffects() {
		Set<Byte> codes = new HashSet<>();
		Set<Integer> effects = new HashSet<>();
		Set<Integer> slots = new HashSet<>();
		for (KidoId k : KidoId.values()) {
			assertTrue(codes.add(k.code), k.name());
			assertTrue(effects.add(k.effectId), k.name());
			assertTrue(slots.add(k.slot), k.name());
			assertNull(AbilityId.fromCode(k.code), "cooldown sync shares the byte space with abilities: " + k.name());
			assertEquals(k, KidoId.fromCode(k.code));
			assertEquals(k, KidoId.fromCommandId(k.commandId));
			assertEquals(k, KidoId.fromEffectId(k.effectId));
			assertEquals(k, KidoId.forSlot(k.slot));
			assertEquals("kido." + k.key, k.commandId);
			assertTrue(k.effectId > EffectIds.SHUNPO, "above the existing effect ids");
		}
		assertNull(KidoId.fromCode((byte) 9));
		assertNull(KidoId.fromCommandId("rukia.shikai.hakuren"));
	}

	@Test
	void defaultsAreTheDocumentedNumbers() {
		KidoConfig c = KidoConfig.defaults();
		assertTrue(c.enabled());
		assertEquals(10, c.gcdTicks());
		KidoSpec b = c.spec(KidoId.BYAKURAI);
		assertEquals(100, b.costTenths());
		assertEquals(60, b.cooldownTicks());
		assertEquals(30.0, b.range());
		assertEquals(6.0, b.damage());
		assertEquals(0.0, b.speed(), "instant");
		KidoSpec s = c.spec(KidoId.SHAKKAHO);
		assertEquals(200, s.costTenths());
		assertEquals(140, s.cooldownTicks());
		assertEquals(24.0, s.range());
		assertEquals(10.0, s.damage());
		assertEquals(3.5, s.radius());
		assertEquals(1.2, s.speed());
		assertEquals(60, s.fireTicks());
		KidoSpec a = c.spec(KidoId.SAI);
		assertEquals(60, a.costTenths());
		assertEquals(100, a.cooldownTicks());
		assertEquals(8.0, a.range());
		assertEquals(0.0, a.damage());
		assertEquals(60, a.statusTicks());
		for (KidoId k : KidoId.values()) {
			assertEquals(k, c.spec(k).id());
			assertTrue(c.spec(k).enabled());
			assertTrue(c.spec(k).costTenths() < 1000, "never more than the bar (R2 floor)");
		}
	}

	@Test
	void roundTripThroughJson() {
		KidoConfig d = KidoConfig.defaults();
		JsonObject root = new JsonObject();
		d.writeInto(root);
		KidoConfig back = KidoConfig.fromRoot(root);
		assertEquals(d.gcdTicks(), back.gcdTicks());
		for (KidoId k : KidoId.values()) {
			assertEquals(d.spec(k), back.spec(k), k.name());
		}
	}

	@Test
	void missingSectionMeansDefaultsAndPartialFilesFillTheRest() {
		assertEquals(KidoConfig.defaults().spec(KidoId.SAI), KidoConfig.fromRoot(new JsonObject()).spec(KidoId.SAI));
		assertEquals(KidoConfig.defaults().spec(KidoId.SAI), KidoConfig.fromRoot(null).spec(KidoId.SAI));
		JsonObject root = JsonParser.parseString("{\"kido\":{\"shakkaho\":{\"damage\":20,\"costTenths\":300}}}").getAsJsonObject();
		KidoConfig c = KidoConfig.fromRoot(root);
		assertEquals(20.0, c.spec(KidoId.SHAKKAHO).damage());
		assertEquals(300, c.spec(KidoId.SHAKKAHO).costTenths());
		assertEquals(140, c.spec(KidoId.SHAKKAHO).cooldownTicks(), "the rest keeps its default");
		assertEquals(KidoConfig.defaults().spec(KidoId.BYAKURAI), c.spec(KidoId.BYAKURAI));
	}

	@Test
	void garbageIsClampedOrIgnored() {
		String json = "{\"kido\":{\"enabled\":\"yes\",\"gcdTicks\":-5,\"byakurai\":{\"costTenths\":99999,\"range\":-3,\"damage\":\"lots\","
				+ "\"cooldownTicks\":null,\"enabled\":false},\"sai\":\"nope\",\"shakkaho\":{\"radius\":1e9,\"speed\":-1,\"width\":0}}}";
		KidoConfig c = KidoConfig.fromRoot(JsonParser.parseString(json).getAsJsonObject());
		assertTrue(c.enabled(), "a non boolean keeps the default");
		assertEquals(0, c.gcdTicks());
		KidoSpec b = c.spec(KidoId.BYAKURAI);
		assertEquals(1000, b.costTenths());
		assertEquals(1.0, b.range());
		assertEquals(6.0, b.damage(), "a string keeps the default");
		assertEquals(60, b.cooldownTicks());
		assertFalse(b.enabled());
		assertEquals(KidoConfig.defaults().spec(KidoId.SAI), c.spec(KidoId.SAI));
		KidoSpec s = c.spec(KidoId.SHAKKAHO);
		assertEquals(16.0, s.radius());
		assertEquals(0.0, s.speed());
		assertEquals(0.05, s.width());
	}

	@Test
	void anIncompleteSpecMapIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> new KidoConfig(true, 10, java.util.Map.of()));
	}

	@Test
	void langNamesExistForEverySpellInBothLanguages() throws IOException {
		for (String file : new String[] {"en_us.json", "ru_ru.json"}) {
			JsonObject o = JsonParser.parseString(Files.readString(
					Paths.get("src", "main", "resources", "assets", "reiatsu_test", "lang", file), StandardCharsets.UTF_8)).getAsJsonObject();
			for (KidoId k : KidoId.values()) {
				assertNotNull(o.get("kido.reiatsu_test." + k.key), file + " name of " + k);
				assertNotNull(o.get("key.reiatsu_test.kido_" + (k.slot + 1)), file + " key of " + k);
			}
		}
	}
}
