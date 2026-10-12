package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure maths of the ability gestures (FP_ABILITY_ANIMS stage 1): keys, easing, envelope, holds, circles, the shipped tables. */
class CastGestureTest {
	private static final float EPS = 1e-4f;

	private static CastGesture simple() {
		return CastGesture.builder("t").dur(1.0).in(0.1).out(0.5)
				.r(0.5, CastGesture.E_LINEAR, 0.4, 0, 0, 0, 0, 0)
				.r(1.0, CastGesture.E_LINEAR, 0, 0, 0, 0, 0, 0)
				.build();
	}

	@Test
	void keysEaseFromZeroAndHoldTheLastValue() {
		CastGesture g = CastGesture.builder("t").dur(2.0).in(0.0).out(0.0)
				.r(1.0, CastGesture.E_LINEAR, 0.4, 0, 0, -60, 0, 0)
				.r(2.0, CastGesture.E_LINEAR, 0.8, 0, 0, -60, 0, 0).build();
		assertEquals(0f, g.sample(0f, 0f).r()[0], EPS);
		assertEquals(0.2f, g.sample(0.5f, 0.5f).r()[0], EPS);
		assertEquals(0.4f, g.sample(1.0f, 1.0f).r()[0], EPS);
		assertEquals(0.6f, g.sample(1.5f, 1.5f).r()[0], EPS);
		assertEquals(-30f, g.sample(0.5f, 0.5f).r()[3], EPS);
		// after the last key the channel keeps its value (the envelope would fade it)
		assertEquals(0.8f, g.sample(2.0f, 2.0f).r()[0], EPS);
		// empty channels are zero
		assertArrayEquals(new float[CastGesture.C_DIM], g.sample(1.0f, 1.0f).c(), EPS);
	}

	@Test
	void easeCurvesStartAtZeroEndAtOneAndStayMonotonic() {
		for (int code = 0; code <= 4; code++) {
			assertEquals(0f, CastGesture.ease(code, 0f), EPS, "start " + code);
			assertEquals(1f, CastGesture.ease(code, 1f), EPS, "end " + code);
			float prev = -1f;
			for (int i = 0; i <= 20; i++) {
				float v = CastGesture.ease(code, i / 20f);
				if (code != CastGesture.E_BACK) {
					assertTrue(v >= prev - 1e-6f, "monotonic " + code + " at " + i);
				}
				prev = v;
			}
		}
		// the spring overshoots, the out curve is ahead of the linear one and the in curve behind it
		float max = 0f;
		for (int i = 0; i <= 50; i++) {
			max = Math.max(max, CastGesture.ease(CastGesture.E_BACK, i / 50f));
		}
		assertTrue(max > 1.0f && max < 1.2f);
		assertTrue(CastGesture.ease(CastGesture.E_OUT, 0.3f) > 0.3f);
		assertTrue(CastGesture.ease(CastGesture.E_IN, 0.3f) < 0.3f);
	}

	@Test
	void envelopeBlendsInAndOutOfTheStance() {
		CastGesture g = simple();
		assertEquals(0f, g.sample(0f, 0f).env(), EPS);
		assertEquals(1f, g.sample(0.5f, 0.5f).env(), EPS);
		// values are multiplied by the envelope: nothing is left when the gesture is over
		assertEquals(0f, g.sample(1.5f, 1.5f).env(), EPS);
		assertArrayEquals(new float[CastGesture.R_DIM], g.sample(1.6f, 1.6f).r(), EPS);
		// halfway through the fade the value is smaller than at the key
		float half = g.sample(1.25f, 1.25f).env();
		assertTrue(half > 0.3f && half < 0.7f);
		assertEquals(1.5f, g.totalSeconds(), EPS);
	}

	@Test
	void aHoldStopsTheGestureTimeButNotTheRealClock() {
		CastGesture g = CastGesture.builder("t").dur(3.0).out(0.4).hold(2.0, 1.0).hold(2.5, 0.5)
				.r(2.0, CastGesture.E_LINEAR, 0.2, 0, 0, 0, 0, 0).build();
		assertEquals(1.0f, g.gestureTime(1.0f), EPS);
		assertEquals(2.0f, g.gestureTime(2.0f), EPS);
		assertEquals(2.0f, g.gestureTime(2.7f), EPS);
		assertEquals(2.0f, g.gestureTime(2.999f), 1e-2f);
		assertEquals(2.2f, g.gestureTime(3.2f), EPS);
		// the second hold is at gesture time 2.5, i.e. real 3.5 .. 4.0
		assertEquals(2.5f, g.gestureTime(3.5f), EPS);
		assertEquals(2.5f, g.gestureTime(3.9f), EPS);
		assertEquals(2.6f, g.gestureTime(4.1f), EPS);
		assertEquals(3.0f + 0.4f + 1.5f, g.totalSeconds(), EPS);
		// the frozen pose is the same for the whole hold
		assertArrayEquals(g.sampleReal(2.1f).r(), g.sampleReal(2.9f).r(), EPS);
	}

	@Test
	void absoluteZeroIsGoneWithItsGestureAndItsEffectId() {
		// mech2 task 4: the ability, its gesture and effect id 23 were removed (the second Rukia bankai slot is the passive frost territory)
		assertNull(CastGestures.defaults().get("rukia.bankai.absolute_zero"));
		assertNull(CastGestures.idForEffect(23));
		assertNotNull(CastGestures.defaults().get("rukia.bankai.ice_spike"));
	}

	@Test
	void circlesAndTremorFadeInAndOutAndStayInsideTheirRadius() {
		CastGesture g = CastGesture.builder("t").dur(3.0).in(0.0).out(0.0).orbit('L', 1.0, 2.0, 0.1, 1.0, 0.0).orbit('P', 1.0, 2.0, 0.2, 1.0, 0.0)
				.orbit('T', 1.0, 2.0, 10, 1.0, 0.0).vib('R', 1.0, 2.0, 0.01, 4).build();
		CastGesture.Sample at0 = g.sample(1.0f, 1.0f);
		assertArrayEquals(new float[CastGesture.L_DIM], at0.l(), EPS);
		assertArrayEquals(new float[CastGesture.R_DIM], at0.r(), EPS);
		float maxL = 0f;
		float maxP = 0f;
		float maxT = 0f;
		for (int i = 0; i <= 200; i++) {
			float t = 1.0f + i * 0.005f;
			CastGesture.Sample s = g.sample(t, t);
			maxL = Math.max(maxL, (float) Math.hypot(s.l()[0], s.l()[1]));
			maxP = Math.max(maxP, (float) Math.hypot(s.r()[0], s.r()[1] - 0f));
			maxT = Math.max(maxT, (float) Math.hypot(s.r()[3], s.r()[5]));
		}
		assertTrue(maxL > 0.08f && maxL <= 0.1001f, "off hand circle " + maxL);
		assertTrue(maxP > 0.15f && maxP < 0.212f, "main hand circle " + maxP);
		assertTrue(maxT > 8f && maxT <= 10.001f, "tip circle " + maxT);
		assertArrayEquals(new float[CastGesture.L_DIM], g.sample(2.5f, 2.5f).l(), EPS);
		// the clock of the circles is separate from the gesture time (the steering waits on its key but keeps conducting)
		CastGesture.Sample a = g.sample(1.5f, 1.5f);
		CastGesture.Sample b = g.sample(1.5f, 1.62f);
		assertTrue(Math.abs(a.l()[0] - b.l()[0]) > 0.01f);
	}

	@Test
	void shiftedAfterMovesOnlyWhatComesLater() {
		CastGesture g = CastGestures.defaults().get(CastGestures.BYAKUYA_SHIKAI);
		CastGesture s = CastGestures.withPinkHold(g, 0.7);
		List<CastGesture.Key> a = g.keys('R');
		List<CastGesture.Key> b = s.keys('R');
		assertEquals(a.size(), b.size());
		for (int i = 0; i < a.size(); i++) {
			float t = a.get(i).t();
			if (t < CastGestures.PINK_HOLD - 1e-4f) {
				assertEquals(t, b.get(i).t(), EPS, "before the pink hold " + i);
			} else {
				assertEquals(t + 0.3f, b.get(i).t(), EPS, "after the pink hold " + i);
			}
		}
		assertEquals(g.dur() + 0.3f, s.dur(), EPS);
		assertEquals(g.orbits().get(0).t0() + 0.3f, s.orbits().get(0).t0(), EPS);
		// the hand does not move away before the dissolve starts: the jerk key is not earlier than the end of the hold
		float jerk = -1f;
		for (CastGesture.Key k : s.keys('R')) {
			if (k.v()[2] < -0.3f) {
				jerk = k.t();
				break;
			}
		}
		assertTrue(jerk >= 0.7f, "jerk at " + jerk);
		// an unchanged hold returns the same gesture
		assertTrue(CastGestures.withPinkHold(g, CastGestures.PINK_HOLD) == g);
	}

	@Test
	void sustainedGestureWaitsOnItsKeyAndPlaysOutAfterTheRelease() {
		CastGesture g = CastGestures.defaults().get(CastGestures.STEER);
		assertEquals(0.25f, g.sustainAt(), EPS);
		CastGesture.Sample held = g.sample(g.sustainAt(), 5.0f);
		assertTrue(held.r()[2] < -0.25f, "hand thrown forward " + held.r()[2]);
		assertTrue(held.env() > 0.99f);
		CastGesture.Sample gone = g.sample(g.dur() + g.out() + 0.01f, 9f);
		assertArrayEquals(new float[CastGesture.R_DIM], gone.r(), EPS);
		assertArrayEquals(new float[CastGesture.L_DIM], gone.l(), EPS);
		assertEquals(0f, gone.env(), EPS);
		// the conducting circle of the off hand keeps running while it waits
		float lo = 1e9f;
		float hi = -1e9f;
		for (int i = 0; i < 40; i++) {
			float x = g.sample(g.sustainAt(), i * 0.05f).l()[0];
			lo = Math.min(lo, x);
			hi = Math.max(hi, x);
		}
		assertTrue(hi - lo > 0.05f);
	}

	@Test
	void freeHandsRiseFromBelowAndSinkBack() {
		CastGesture g = CastGestures.defaults().get(CastGestures.BYAKUYA_BANKAI);
		assertEquals(0f, g.sample(0f, 0f).hand(), EPS);
		assertEquals(1f, g.sample(1.0f, 1.0f).hand(), EPS);
		assertEquals(0f, g.sample(g.dur() + g.out(), g.dur() + g.out()).hand(), EPS);
		assertTrue(g.freeHands());
	}

	@Test
	void samplesMixAndScale() {
		CastGesture.Sample a = CastGesture.builder("a").dur(1).in(0).out(0).r(0.0, 0, 1, 0, 0, 0, 0, 0).build().sample(0.5f, 0.5f);
		CastGesture.Sample b = CastGesture.Sample.NONE;
		assertEquals(1f, a.r()[0], EPS);
		assertEquals(0.25f, CastGesture.Sample.mix(a, b, 0.75f).r()[0], EPS);
		assertEquals(0.5f, a.scaled(0.5f).r()[0], EPS);
		assertEquals(0.5f, CastGesture.Sample.fade(a, 0.5f).r()[0], EPS);
	}

	@Test
	void rowsMustMatchTheirChannel() {
		assertThrows(IllegalArgumentException.class, () -> CastGesture.builder("bad").r(0.1, 0, 1, 2, 3));
		assertThrows(IllegalArgumentException.class, () -> CastGesture.builder("bad").l(0.1, 0, 1, 2, 3, 4, 5, 6));
		assertThrows(IllegalArgumentException.class, () -> CastGesture.builder("bad").c(0.1, 0, 1, 2, 3));
		assertThrows(IllegalArgumentException.class, () -> CastGesture.builder("bad").tr(0.1, 0, 1, 2));
	}

	@Test
	void builderRoundTripKeepsEverything() {
		for (CastGesture g : CastGestures.defaults().values()) {
			CastGesture c = g.toBuilder().build();
			assertEquals(g.dur(), c.dur(), EPS);
			assertEquals(g.holds(), c.holds());
			for (char ch : new char[] {'R', 'L', 'C', 'r', 'l'}) {
				assertEquals(g.keys(ch).size(), c.keys(ch).size(), g.id() + " " + ch);
			}
			assertEquals(g.orbits(), c.orbits());
			assertEquals(g.vibs(), c.vibs());
			for (float t = 0f; t < g.totalSeconds(); t += 0.07f) {
				assertArrayEquals(g.sampleReal(t).r(), c.sampleReal(t).r(), EPS);
				assertArrayEquals(g.sampleReal(t).l(), c.sampleReal(t).l(), EPS);
			}
		}
	}

	// ------------------------------------------------------------------ the shipped tables

	@Test
	void everyEffectOfAnAbilityHasAGesture() {
		assertEquals("rukia.shikai.release", CastGestures.idForEffect(1));
		assertEquals("rukia.bankai.release", CastGestures.idForEffect(2));
		assertEquals("byakuya.shikai.release", CastGestures.idForEffect(3));
		assertEquals("byakuya.bankai.release", CastGestures.idForEffect(4));
		assertEquals("common.seal", CastGestures.idForEffect(10));
		assertEquals("rukia.shikai.tsukishiro", CastGestures.idForEffect(20));
		assertEquals("rukia.shikai.hakuren", CastGestures.idForEffect(21));
		assertEquals("rukia.shikai.shirafune", CastGestures.idForEffect(22));
		assertEquals("byakuya.shikai.mode_attack", CastGestures.idForEffect(30));
		assertEquals("byakuya.shikai.mode_barrier", CastGestures.idForEffect(31));
		assertEquals("byakuya.bankai.scatter", CastGestures.idForEffect(32));
		assertEquals("byakuya.bankai.hakuteiken", CastGestures.idForEffect(33));
		assertEquals("byakuya.bankai.senkei", CastGestures.idForEffect(34));
		assertEquals("kido.byakurai", CastGestures.idForEffect(50));
		assertEquals("kido.shakkaho", CastGestures.idForEffect(51));
		assertEquals("kido.sai", CastGestures.idForEffect(52));
		// no gesture: the end of the bankai, shunpo, block feedback, unknown
		assertNull(CastGestures.idForEffect(11));
		assertNull(CastGestures.idForEffect(40));
		assertNull(CastGestures.idForEffect(41));
		assertNull(CastGestures.idForEffect(0));
		assertNull(CastGestures.idForEffect(999));
		for (AbilityId a : AbilityId.values()) {
			assertNotNull(CastGestures.defaults().get(a.commandId), a.commandId);
		}
		for (KidoId k : KidoId.values()) {
			assertNotNull(CastGestures.defaults().get(k.commandId), k.commandId);
		}
		assertNotNull(CastGestures.defaults().get(CastGestures.SEAL));
		assertNotNull(CastGestures.defaults().get(CastGestures.STEER));
	}

	@Test
	void shippedGesturesAreWellFormedAndModest() {
		for (CastGesture g : CastGestures.defaults().values()) {
			String id = g.id();
			assertTrue(g.dur() > 0.3f && g.dur() < 7f, id + " dur");
			assertTrue(g.totalSeconds() < 8f, id + " total");
			assertTrue(!g.keys('R').isEmpty() || !g.keys('L').isEmpty(), id + " has a hand channel");
			for (char ch : new char[] {'R', 'L', 'C', 'r', 'l'}) {
				float prev = -1f;
				for (CastGesture.Key k : g.keys(ch)) {
					assertTrue(k.t() >= prev, id + " " + ch + " keys sorted");
					assertTrue(k.t() <= g.dur() + EPS, id + " " + ch + " key inside the duration");
					prev = k.t();
					for (float v : k.v()) {
						assertTrue(Float.isFinite(v), id + " finite");
					}
					assertTrue(k.ease() >= 0 && k.ease() <= 4, id + " ease code");
				}
				// every channel that is used ends on a zero row so the hand is back on the stance
				if (!g.keys(ch).isEmpty()) {
					CastGesture.Key last = g.keys(ch).get(g.keys(ch).size() - 1);
					float sum = 0f;
					for (float v : last.v()) {
						sum += Math.abs(v);
					}
					assertTrue(sum < 1e-6f, id + " channel " + ch + " ends on zero, sum " + sum);
				}
			}
			// bounds over the whole timeline: hands stay on screen, the blade does not spin, the camera is a nudge
			for (float t = 0f; t <= g.totalSeconds(); t += 0.02f) {
				CastGesture.Sample s = g.sampleReal(t);
				for (int i = 0; i < 3; i++) {
					assertTrue(Math.abs(s.r()[i]) < 0.65f, id + " R pos " + i + " at " + t);
					assertTrue(Math.abs(s.l()[i]) < 0.65f, id + " L pos " + i + " at " + t);
				}
				for (int i = 3; i < 6; i++) {
					// the empty hands (freeHands) carry rotation vectors of the arm (a big turn in one component), the sword hand Euler angles
					assertTrue(Math.abs(s.r()[i]) < (g.freeHands() ? 170f : 125f), id + " R rot " + i + " at " + t + " = " + s.r()[i]);
					assertTrue(Math.abs(s.l()[i]) < (g.freeHands() ? 170f : 90f), id + " L arm rot " + i);
				}
				assertTrue(s.l()[6] >= -1e-4f && s.l()[6] <= 1.0001f, id + " free weight");
				assertTrue(Math.abs(s.c()[0]) <= 4f && Math.abs(s.c()[1]) <= 4f && Math.abs(s.c()[2]) <= 4f, id + " camera angles");
				assertTrue(Math.abs(s.c()[3]) <= 5f, id + " fov nudge");
				for (int i = 0; i < 3; i++) {
					assertTrue(Math.abs(s.tr()[i]) <= 180f && Math.abs(s.tl()[i]) <= 180f, id + " third person arms");
				}
			}
			// the gesture is over at the end: nothing is left of it
			CastGesture.Sample end = g.sampleReal(g.totalSeconds() + 0.01f);
			assertArrayEquals(new float[CastGesture.R_DIM], end.r(), EPS, id);
			assertEquals(0f, end.env(), EPS, id);
		}
	}

	@Test
	void anInstantGestureStartsGentlyThroughTheEnvelope() {
		// rows at t = 0 do not make a jump: the envelope brings the hand in over `in`
		for (CastGesture g : CastGestures.defaults().values()) {
			CastGesture.Sample s0 = g.sampleReal(0f);
			assertArrayEquals(new float[CastGesture.R_DIM], s0.r(), EPS, g.id() + " R at 0");
			assertArrayEquals(new float[CastGesture.L_DIM], s0.l(), EPS, g.id() + " L at 0");
		}
	}

	@Test
	void theThreeTsukishiroDancesAndTheReleaseDiffer() {
		// different lunges: Shirafune thrusts furthest forward, Tsukishiro turns the tip in a circle, Hakuren keeps the left hand forward
		CastGesture tsuki = CastGestures.defaults().get("rukia.shikai.tsukishiro");
		CastGesture hak = CastGestures.defaults().get("rukia.shikai.hakuren");
		CastGesture shira = CastGestures.defaults().get("rukia.shikai.shirafune");
		assertTrue(tsuki.orbits().stream().anyMatch(o -> o.target() == 'T'));
		float shiraZ = 0f;
		float hakLz = 0f;
		for (float t = 0f; t < 3f; t += 0.02f) {
			shiraZ = Math.min(shiraZ, shira.sampleReal(t).r()[2]);
			hakLz = Math.min(hakLz, hak.sampleReal(t).l()[2]);
		}
		assertTrue(shiraZ < -0.25f && hakLz < -0.35f);
	}
}
