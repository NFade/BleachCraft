package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** R14: the sparse {@code balance} section of the config ({@code /reiatsu dev set balance.*}). */
class BalanceOverridesTest {
	private static final BalanceConfig BASE = BalanceConfig.defaults();

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void noSectionMeansTheSameObject() {
		assertSame(BASE, BalanceOverrides.apply(BASE, json("{}")));
		assertSame(BASE, BalanceOverrides.apply(BASE, json("{\"balance\":{}}")));
		assertSame(BASE, BalanceOverrides.apply(BASE, json("{\"balance\":5}")), "a section that is not an object is ignored");
	}

	@Test
	void anAbilityCostCooldownAndParamAreRead() {
		BalanceConfig c = BalanceOverrides.apply(BASE, json("{\"balance\":{\"tsukishiro\":{\"cost\":20,\"cooldown\":8.5,\"damage\":4.5,\"enabled\":false}}}"));
		AbilitySpec a = c.spec(AbilityId.TSUKISHIRO);
		assertEquals(200, a.costTenths());
		assertEquals(170, a.cooldownTicks());
		assertEquals(4.5, a.num(AbilityParams.DAMAGE), 1e-9);
		assertFalse(a.enabled());
		AbilitySpec before = BASE.spec(AbilityId.TSUKISHIRO);
		assertEquals(before.phaseOffsets(), a.phaseOffsets(), "phases are not tunable");
		assertEquals(before.num(AbilityParams.RADIUS), a.num(AbilityParams.RADIUS), 1e-9, "other numbers keep the default");
		assertEquals(BASE.spec(AbilityId.HAKUREN), c.spec(AbilityId.HAKUREN), "other abilities are untouched");
	}

	@Test
	void regenAndShunpoAreRead() {
		BalanceConfig c = BalanceOverrides.apply(BASE, json("{\"balance\":{\"regen\":{\"shikai\":{\"perBatchTenths\":12}},"
				+ "\"shunpo\":{\"cooldown\":4,\"uryuCooldownBonus\":0.5,\"distance\":10}}}"));
		assertEquals(12, c.rate(ZanpakutoState.SHIKAI).regenPerBatch());
		assertEquals(BASE.rate(ZanpakutoState.SHIKAI).drainPerBatch(), c.rate(ZanpakutoState.SHIKAI).drainPerBatch());
		assertEquals(BASE.rate(ZanpakutoState.BANKAI), c.rate(ZanpakutoState.BANKAI));
		assertEquals(80, c.shunpo().cooldownTicks());
		assertEquals(10, c.shunpo().uryuCooldownBonus());
		assertEquals(10.0, c.shunpo().distance(), 1e-9);
		ShunpoSpec u = c.shunpo().forCharacter(CharacterId.URYU);
		assertEquals(90, u.cooldownTicks());
		assertEquals(10.0 + c.shunpo().uryuDistanceBonus(), u.distance(), 1e-9);
	}

	@Test
	void garbageAndOutOfRangeValuesAreTamed() {
		BalanceConfig c = BalanceOverrides.apply(BASE, json("{\"balance\":{\"gcdTicks\":99999,\"tsukishiro\":{\"cost\":\"lots\",\"cooldown\":-5,\"radius\":1e9},"
				+ "\"regen\":{\"sealed\":{\"perBatchTenths\":-3}}}}"));
		assertEquals(200, c.gcdTicks());
		assertEquals(BASE.spec(AbilityId.TSUKISHIRO).costTenths(), c.spec(AbilityId.TSUKISHIRO).costTenths());
		assertEquals(0, c.spec(AbilityId.TSUKISHIRO).cooldownTicks());
		assertEquals(10_000.0, c.spec(AbilityId.TSUKISHIRO).num(AbilityParams.RADIUS), 1e-9);
		assertEquals(0, c.rate(ZanpakutoState.SEALED).regenPerBatch());
	}

	@Test
	void flattenListsEveryKeyWithTheValueInForce() {
		Map<String, Double> m = BalanceOverrides.flatten(BASE);
		assertEquals(10.0, m.get("regen.shikai.perBatchTenths"), 1e-9);
		assertEquals(2.0, m.get("regen.shikai.upkeepTenths"), 1e-9);
		assertEquals(25.0, m.get("tsukishiro.cost"), 1e-9);
		assertEquals(12.0, m.get("tsukishiro.cooldown"), 1e-9);
		assertEquals(3.5, m.get("shunpo.cooldown") + m.get("shunpo.uryuCooldownBonus"), 1e-9, "R14: Uryu's flash step waits 3.5 s");
		assertTrue(m.containsKey("heilig_pfeil.cost"));
		for (AbilityId id : AbilityId.values()) {
			assertTrue(m.containsKey(BalanceOverrides.key(id) + ".cost"), id.name());
		}
		BalanceConfig c = BalanceOverrides.apply(BASE, json("{\"balance\":{\"hakuren\":{\"length\":30}}}"));
		assertEquals(30.0, BalanceOverrides.flatten(c).get("hakuren.length"), 1e-9);
	}

	@Test
	void everyListedKeyCanBeSetAndReadBack() {
		for (Map.Entry<String, Double> e : BalanceOverrides.flatten(BASE).entrySet()) {
			JsonObject root = new JsonObject();
			BalanceOverrides.set(root, e.getKey(), e.getValue());
			BalanceConfig c = BalanceOverrides.apply(BASE, root);
			assertEquals(e.getValue(), BalanceOverrides.flatten(c).get(e.getKey()), 0.051, e.getKey());
		}
	}

	@Test
	void setAndUnsetKeepTheSectionSparse() {
		JsonObject root = new JsonObject();
		BalanceOverrides.set(root, "tsukishiro.cost", 20);
		BalanceOverrides.set(root, "tsukishiro.cooldown", 9);
		BalanceOverrides.set(root, "regen.shikai.perBatchTenths", 12);
		assertEquals(20.0, root.getAsJsonObject("balance").getAsJsonObject("tsukishiro").get("cost").getAsDouble(), 0.0);
		assertTrue(BalanceOverrides.unset(root, "tsukishiro.cost"));
		assertFalse(BalanceOverrides.unset(root, "tsukishiro.cost"), "already gone");
		assertTrue(BalanceOverrides.unset(root, "tsukishiro.cooldown"));
		assertFalse(root.getAsJsonObject("balance").has("tsukishiro"), "an empty object is dropped");
		assertTrue(root.getAsJsonObject("balance").has("regen"));
		assertFalse(BalanceOverrides.unset(new JsonObject(), "x.y"));
	}
}
