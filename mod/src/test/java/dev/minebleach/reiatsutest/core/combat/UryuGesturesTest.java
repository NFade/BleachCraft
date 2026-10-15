package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The first person hands of Uryu ({@link UryuGestures}): the rows replace the placeholder thrusts, stay in reach and come back to the rest hold. */
class UryuGesturesTest {
	private static final List<String> ABILITIES = List.of("uryu.shikai.heilig_pfeil", "uryu.shikai.sprenger", "uryu.shikai.seele_schneider",
			"uryu.bankai.wing_barrage", "uryu.bankai.licht_regen", "uryu.bankai.sternenstaub");

	@Test
	void theGameUsesTheUryuRowsNotTheOldThrust() {
		assertEquals(10, UryuGestures.all().size(), "draw, release, six abilities, two release events");
		for (String id : UryuGestures.all().keySet()) {
			CastGesture g = CastGestures.defaults().get(id);
			assertNotNull(g, id);
			assertEquals(UryuGestures.all().get(id).dur(), g.dur(), 0.0, id);
			assertEquals(UryuGestures.all().get(id).keys('R').size(), g.keys('R').size(), id);
			assertEquals(UryuGestures.all().get(id).keys('L').size(), g.keys('L').size(), id);
		}
		for (String id : ABILITIES) {
			assertTrue(UryuGestures.all().containsKey(id), id);
			// the old placeholder was one thrust of 3 rows: the real gestures have more
			assertTrue(CastGestures.defaults().get(id).keys('R').size() >= 2 && !CastGestures.defaults().get(id).keys('L').isEmpty() || "uryu.shikai.sprenger".equals(id), id);
		}
		assertTrue(UryuGestures.all().containsKey(UryuGestures.DRAW));
		assertTrue(UryuGestures.all().containsKey(UryuGestures.RELEASE));
		assertTrue(UryuGestures.all().containsKey(UryuAnims.RELEASE_SHIKAI));
		assertTrue(UryuGestures.all().containsKey(UryuAnims.RELEASE_BANKAI));
	}

	@Test
	void ginKeepsHisPlaceholderRows() {
		assertNotNull(CastGestures.defaults().get("gin.shikai.ikorose"), "Gin keeps his placeholder rows");
	}

	@Test
	void everyRowStaysInReachAndInTheEaseCodes() {
		for (CastGesture g : UryuGestures.all().values()) {
			assertTrue(g.dur() > 0.0 && g.dur() <= 7.0, g.id());
			for (char ch : new char[] {'R', 'L', 'C'}) {
				float last = -1f;
				for (CastGesture.Key k : g.keys(ch)) {
					assertTrue(k.t() >= last, g.id() + " " + ch + " keys are in time order");
					last = k.t();
					assertTrue(k.ease() >= 0 && k.ease() <= 4, g.id());
					float[] v = k.v();
					if (ch == 'C') {
						assertTrue(Math.abs(v[0]) <= 4f && Math.abs(v[1]) <= 4f && Math.abs(v[2]) <= 4f && Math.abs(v[3]) <= 5f, g.id() + " camera nudge " + java.util.Arrays.toString(v));
						continue;
					}
					for (int i = 0; i < 3; i++) {
						assertTrue(Math.abs(v[i]) <= 0.65f, g.id() + " " + ch + " position " + java.util.Arrays.toString(v));
					}
					for (int i = 3; i < 6; i++) {
						assertTrue(Math.abs(v[i]) <= 90f, g.id() + " " + ch + " angle " + java.util.Arrays.toString(v));
					}
					if (ch == 'L') {
						assertTrue(v[6] == 0f || v[6] == 1f, g.id() + " free is 0 or 1");
					}
				}
			}
		}
	}

	@Test
	void everyGestureThatEndsReturnsToTheRestHold() {
		for (CastGesture g : UryuGestures.all().values()) {
			if (UryuGestures.DRAW.equals(g.id())) {
				continue; // held for as long as the key is held
			}
			CastGesture.Sample end = g.sample(g.dur(), g.dur());
			assertTrue(max(end.r()) < 0.02f, g.id() + " right hand at the end " + java.util.Arrays.toString(end.r()));
			assertTrue(max(end.l()) < 0.02f, g.id() + " free hand at the end");
			assertTrue(max(end.c()) < 0.2f, g.id() + " camera at the end");
			CastGesture.Sample after = g.sample(g.dur() + g.out(), g.dur() + g.out());
			assertEquals(0f, after.env(), 1e-6f, g.id() + " the envelope is over after the out time");
		}
	}

	private static float max(float[] v) {
		float m = 0f;
		for (float x : v) {
			m = Math.max(m, Math.abs(x));
		}
		return m;
	}

	@Test
	void theDrawRowsAreTheStoryboardNumbersAndHoldAtFullDraw() {
		CastGesture g = UryuGestures.all().get(UryuGestures.DRAW);
		CastGesture.Sample full = g.sample(1.0f, 1.0f);
		assertEquals(1.0f, full.env(), 1e-6f);
		assertEquals(0.03f, full.r()[0], 1e-6f);
		assertEquals(0.03f, full.r()[1], 1e-6f);
		assertEquals(-0.08f, full.r()[2], 1e-6f);
		assertEquals(-10f, full.r()[3], 1e-6f);
		assertEquals(0.22f, full.l()[2], 1e-6f, "the string hand 0.22 blocks back at full draw");
		assertEquals(1f, full.l()[6], 0f, "the string hand keeps its own place");
		assertEquals(-1.5f, full.c()[3], 1e-6f, "the field of view closes by 1.5");
		// the pull is at half way at 0.5 s: the middle row
		assertEquals(0.03f, g.sample(0.5f, 0.5f).r()[0], 1e-6f);
		// the bow rises and tips as the draw increases
		assertTrue(g.sample(0.25f, 0.25f).r()[3] > g.sample(1.0f, 1.0f).r()[3]);
		// the draw is sampled by the use time: nothing in the first instant, the envelope comes in over 0.04 s
		assertEquals(0f, g.sample(0f, 0f).env(), 1e-6f);
	}

	@Test
	void theStringHandShowsOnlyWhereTheStoryboardGivesTheFreeHandARow() {
		for (String id : new String[] {UryuGestures.DRAW, UryuGestures.RELEASE, "uryu.shikai.heilig_pfeil", "uryu.shikai.seele_schneider",
				UryuAnims.RELEASE_SHIKAI, UryuAnims.RELEASE_BANKAI, "uryu.bankai.wing_barrage", "uryu.bankai.licht_regen", "uryu.bankai.sternenstaub"}) {
			assertTrue(UryuGestures.hasStringHand(id), id);
		}
		assertFalse(UryuGestures.hasStringHand("uryu.shikai.sprenger"), "the dagger toss is a one handed throw");
		assertFalse(UryuGestures.hasStringHand("rukia.shikai.release"));
		assertFalse(UryuGestures.hasStringHand(null));
	}

	@Test
	void theReleaseIsTheDrawRowFollowedByTheKickAndTheLeavingHand() {
		CastGesture rel = UryuGestures.all().get(UryuGestures.RELEASE);
		CastGesture draw = UryuGestures.all().get(UryuGestures.DRAW);
		// no pop: the release starts exactly where the full draw stands
		CastGesture.Sample d = draw.sample(1.0f, 1.0f);
		CastGesture.Sample r0 = rel.sampleReal(0f);
		for (int i = 0; i < 6; i++) {
			assertEquals(d.r()[i], r0.r()[i], 1e-6f, "bow hand " + i);
		}
		for (int i = 0; i < 3; i++) {
			assertEquals(d.l()[i], r0.l()[i], 1e-6f, "string hand " + i);
		}
		// at 0.05 s the string hand has left back and out (z 0.40) and the camera kicks up
		CastGesture.Sample k = rel.sampleReal(0.05f);
		assertEquals(0.40f, k.l()[2], 1e-6f);
		assertEquals(0.4f, k.c()[0], 1e-6f);
	}

	@Test
	void theTwoReleasesRiseFromBelowAndBurstAtTheKeyTime() {
		CastGesture blut = UryuGestures.all().get(UryuAnims.RELEASE_SHIKAI);
		CastGesture voll = UryuGestures.all().get(UryuAnims.RELEASE_BANKAI);
		assertEquals(-0.60f, blut.keys('L').get(0).v()[1], 0f, "the free hand rises from below the picture");
		assertEquals(-0.60f, voll.keys('L').get(0).v()[1], 0f);
		// the burst row of the vein release at 0.55 s, of the bankai at 0.65 s
		assertEquals(0.55f, blut.keys('R').get(2).t(), 0f);
		assertEquals(0.65f, voll.keys('R').get(2).t(), 0f);
		assertEquals(3.5f, blut.sampleReal(0.55f).c()[3], 1e-5f, "fov opens by 3.5 on the burst");
		assertEquals(3.5f, voll.sampleReal(0.65f).c()[3], 1e-5f);
	}

	@Test
	void theRainAndTheStarGesturesStartAtTheirEvent() {
		// the effect event of both comes at the release: the rows begin with the bow raised / the string hand already back
		CastGesture rain = UryuGestures.all().get("uryu.bankai.licht_regen");
		assertTrue(rain.sampleReal(0.2f).r()[3] > 40f, "the bow points to the sky");
		assertTrue(rain.sampleReal(0.2f).c()[0] >= 3.9f, "the view tips up");
		CastGesture star = UryuGestures.all().get("uryu.bankai.sternenstaub");
		CastGesture shot = UryuGestures.all().get(UryuGestures.RELEASE);
		float starKick = star.sampleReal(0.05f).l()[2];
		float shotKick = shot.sampleReal(0.05f).l()[2];
		assertTrue(starKick / shotKick > 1.4f, "a stronger recoil than the basic shot (0.62 against 0.40 blocks)");
		CastGesture z = UryuGestures.all().get("uryu.shikai.heilig_pfeil");
		assertEquals(0f, z.sampleReal(0f).env(), 1e-6f, "the quick draw of the Z arrow brings the hands in");
		assertEquals(1f, z.sampleReal(0.1f).env(), 1e-6f);
		assertEquals(1f, rain.sampleReal(0f).env(), 1e-6f, "the rain and the star start at the pose the cast start gesture reached");
		assertEquals(1f, star.sampleReal(0f).env(), 1e-6f);
	}

	@Test
	void theWingBarrageHoldsForSixSecondsWithAVibration() {
		CastGesture w = UryuGestures.all().get("uryu.bankai.wing_barrage");
		assertEquals(6.4f, w.dur(), 0f, "6 s of fire and 0.4 s to come back");
		assertEquals(1, w.vibs().size());
		assertEquals(w.sampleReal(1.0f).r()[5], w.sampleReal(5.0f).r()[5], 1e-6f, "the hand is held out to the side");
	}
}
