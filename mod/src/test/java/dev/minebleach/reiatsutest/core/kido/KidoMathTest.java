package dev.minebleach.reiatsutest.core.kido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KidoMathTest {
	private static final KidoSpec SHAKKAHO = KidoConfig.defaults().spec(KidoId.SHAKKAHO);

	@Test
	void travelTimeRoundsUpAndInstantSpellsHaveNone() {
		assertEquals(0, KidoMath.travelTicks(30.0, 0.0));
		assertEquals(0, KidoMath.travelTicks(0.0, 1.2));
		assertEquals(0, KidoMath.travelTicks(Double.NaN, 1.2));
		assertEquals(1, KidoMath.travelTicks(0.5, 1.2));
		assertEquals(10, KidoMath.travelTicks(12.0, 1.2));
		assertEquals(11, KidoMath.travelTicks(12.1, 1.2));
		assertEquals(20, KidoMath.travelTicks(24.0, 1.2));
	}

	@Test
	void burstFalloffIsLinearFromFullToTheEdgeFraction() {
		assertEquals(1.0, KidoMath.falloff(0.0, 3.5), 1e-9);
		assertEquals(KidoMath.EDGE_FRACTION, KidoMath.falloff(3.5, 3.5), 1e-9);
		assertEquals(0.7, KidoMath.falloff(1.75, 3.5), 1e-9);
		assertEquals(0.0, KidoMath.falloff(3.51, 3.5));
		assertEquals(0.0, KidoMath.falloff(-1.0, 3.5));
		assertEquals(0.0, KidoMath.falloff(Double.NaN, 3.5));
		assertEquals(1.0, KidoMath.falloff(0.0, 0.0));
		assertEquals(0.0, KidoMath.falloff(0.1, 0.0));
	}

	@Test
	void burstDamageUsesTheSpec() {
		assertEquals(4.0, KidoMath.burstDamage(SHAKKAHO, 0.0), 1e-9);
		assertEquals(1.6, KidoMath.burstDamage(SHAKKAHO, 3.5), 1e-9);
		assertEquals(0.0, KidoMath.burstDamage(SHAKKAHO, 4.0));
		double prev = Double.MAX_VALUE;
		for (double d = 0; d <= 3.5; d += 0.25) {
			double dmg = KidoMath.burstDamage(SHAKKAHO, d);
			assertTrue(dmg <= prev, "monotone");
			prev = dmg;
		}
	}

	@Test
	void usedLengthIsClampedToTheRange() {
		assertEquals(30.0, KidoMath.usedLength(30.0, 99.0));
		assertEquals(7.5, KidoMath.usedLength(30.0, 7.5));
		assertEquals(0.0, KidoMath.usedLength(30.0, -1.0));
		assertEquals(0.0, KidoMath.usedLength(30.0, Double.NaN));
	}

	@Test
	void effectParamsCarryWhatTheClientNeeds() {
		float[] p = KidoMath.effectParams(SHAKKAHO, 12.0, KidoMath.HIT_BLOCK);
		assertEquals(6, p.length);
		assertEquals(12.0f, p[0]);
		assertEquals(10.0f, p[1], "travel ticks");
		assertEquals(3.5f, p[2]);
		assertEquals((float) KidoMath.HIT_BLOCK, p[3]);
		assertEquals(1.2f, p[4], 1e-6);
		assertEquals(0.0f, p[5]);
		float[] sai = KidoMath.effectParams(KidoConfig.defaults().spec(KidoId.SAI), 5.0, KidoMath.HIT_ENTITY);
		assertEquals(0.0f, sai[1], "instant");
		assertEquals(60.0f, sai[5], "binding ticks");
		assertTrue(p.length <= 8, "effect_event carries at most 8 params");
	}
}
