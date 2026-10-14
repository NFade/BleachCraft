package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import org.junit.jupiter.api.Test;

/** The Sprenger look (storyboard 2.7): timings against the server, the pentagon geometry, the explosion tables and the tiers. */
class SprengerFxPlanTest {
	private static final double R = SprengerFxPlan.REFERENCE_RADIUS;

	@Test
	void bindAndExplosionFollowTheServerTicks() {
		assertEquals(0.8, SprengerFxPlan.BIND_AT, 1e-9);
		assertEquals(2.0, SprengerFxPlan.EXPLODE_AT, 1e-9);
		assertEquals(UryuBalance.SPRENGER_BIND_AT / 20.0, SprengerFxPlan.BIND_AT, 1e-9);
		assertEquals(UryuBalance.SPRENGER_EXPLODE_AT / 20.0, SprengerFxPlan.EXPLODE_AT, 1e-9);
		assertEquals(UryuBalance.SPRENGER_RADIUS, SprengerFxPlan.REFERENCE_RADIUS, 1e-9);
	}

	@Test
	void theFigureHappensInTheStoryboardOrder() {
		// daggers fall (0.22 s, 0.05 s apart), then the hub and spokes at 0.30, the edges from 0.55 to 0.80, the bind at 0.80
		assertEquals(0.22, SprengerFxPlan.landAt(0), 1e-9);
		assertEquals(0.42, SprengerFxPlan.landAt(4), 1e-9);
		assertTrue(SprengerFxPlan.SPOKE_AT < SprengerFxPlan.EDGE_AT);
		assertTrue(SprengerFxPlan.spokesDoneAt(R) < SprengerFxPlan.EDGE_AT, "the spokes have arrived before the first edge starts");
		assertEquals(0.30 + 3.0 / 40.0, SprengerFxPlan.spokesDoneAt(R), 1e-9);
		assertEquals(0.55, SprengerFxPlan.edgeStart(0), 1e-9);
		assertEquals(0.75, SprengerFxPlan.edgeStart(4), 1e-9);
		assertEquals(0.80, SprengerFxPlan.edgesDoneAt(), 1e-9);
		assertEquals(SprengerFxPlan.BIND_AT, SprengerFxPlan.edgesDoneAt(), 1e-9, "the outline is complete at the moment of the bind");
		assertTrue(SprengerFxPlan.ANTICIPATION_AT < SprengerFxPlan.EXPLODE_AT);
		assertTrue(SprengerFxPlan.BIND_AT + SprengerFxPlan.CHARGE_TIME <= SprengerFxPlan.ANTICIPATION_AT + 1e-9, "the charge column ends where the anticipation starts");
	}

	@Test
	void spokesRunAtFortyBlocksPerSecond() {
		assertEquals(0.0, SprengerFxPlan.spokeLength(0.29, R), 1e-9);
		assertEquals(0.0, SprengerFxPlan.spokeLength(0.30, R), 1e-9);
		assertEquals(1.0, SprengerFxPlan.spokeLength(0.325, R), 1e-9);
		assertEquals(R, SprengerFxPlan.spokeLength(0.40, R), 1e-9, "never longer than the radius");
		assertEquals(R, SprengerFxPlan.spokeLength(5.0, R), 1e-9);
	}

	@Test
	void edgesDrawOneAfterTheOtherFiveHundredthsEach() {
		for (int i = 0; i < 5; i++) {
			assertEquals(0.0, SprengerFxPlan.edgeProgress(i, SprengerFxPlan.edgeStart(i) - 0.001), 1e-9);
			assertEquals(0.5, SprengerFxPlan.edgeProgress(i, SprengerFxPlan.edgeStart(i) + 0.025), 1e-9);
			assertEquals(1.0, SprengerFxPlan.edgeProgress(i, SprengerFxPlan.edgeStart(i) + 0.05), 1e-9);
		}
		// at 0.65 the edges 0 and 1 are done, edge 2 starts
		assertEquals(1.0, SprengerFxPlan.edgeProgress(1, 0.65), 1e-9);
		assertEquals(0.0, SprengerFxPlan.edgeProgress(2, 0.65), 1e-9);
	}

	@Test
	void theGeometryIsARegularPentagonWithVertexZeroTowardsMinusZ() {
		assertEquals(0.0, SprengerFxPlan.vertexX(0, R, 0), 1e-9);
		assertEquals(-R, SprengerFxPlan.vertexZ(0, R, 0), 1e-9);
		double edge = SprengerFxPlan.edgeLength(R);
		for (int i = 0; i < 5; i++) {
			int j = (i + 1) % 5;
			double dx = SprengerFxPlan.vertexX(0, R, j) - SprengerFxPlan.vertexX(0, R, i);
			double dz = SprengerFxPlan.vertexZ(0, R, j) - SprengerFxPlan.vertexZ(0, R, i);
			assertEquals(edge, Math.hypot(dx, dz), 1e-9, "all five edges have the same length");
			assertEquals(R, Math.hypot(SprengerFxPlan.vertexX(0, R, i), SprengerFxPlan.vertexZ(0, R, i)), 1e-9);
		}
		assertEquals(2.0 * R * Math.sin(Math.toRadians(36)), edge, 1e-9);
		assertEquals(R * Math.cos(Math.toRadians(36)), SprengerFxPlan.apothem(R), 1e-9);
		assertTrue(SprengerFxPlan.insidePentagon(0, 0, R, 0.1));
		assertFalse(SprengerFxPlan.insidePentagon(0, -R, R, 0.01), "a vertex is on the boundary, not inside");
		assertTrue(SprengerFxPlan.insidePentagon(0, -R + 0.2, R, 0.0));
		assertFalse(SprengerFxPlan.insidePentagon(R, 0, R, 0.0));
		assertFalse(SprengerFxPlan.insidePentagon(0, SprengerFxPlan.apothem(R) + 0.01, R, 0.0), "just past the middle of the edge opposite vertex 0");
		assertTrue(SprengerFxPlan.insidePentagon(0, SprengerFxPlan.apothem(R) - 0.01, R, 0.0));
	}

	@Test
	void theFloorFillsToTheStoryboardValueOverThreeTenths() {
		assertEquals(0.0, SprengerFxPlan.floorFill(0.5), 1e-9);
		assertEquals(0.0, SprengerFxPlan.floorFill(0.55), 1e-9);
		assertEquals(0.125, SprengerFxPlan.floorFill(0.70), 1e-9, "smoothstep halfway");
		assertEquals(0.25, SprengerFxPlan.floorFill(0.85), 1e-9);
		assertEquals(0.25, SprengerFxPlan.floorFill(3.0), 1e-9);
	}

	@Test
	void bindFlashLastsOneTenthOfASecond() {
		assertEquals(0.0, SprengerFxPlan.bindFlash(0.79), 1e-9);
		assertEquals(1.0, SprengerFxPlan.bindFlash(0.80), 1e-9);
		assertEquals(0.5, SprengerFxPlan.bindFlash(0.85), 1e-9);
		assertEquals(0.0, SprengerFxPlan.bindFlash(0.91), 1e-9);
	}

	@Test
	void theFenceRisesToThreeBlocksInTwoTenthsAndNeverBeforeTheBind() {
		assertEquals(0.0, SprengerFxPlan.fenceHeight(0.79), 1e-9);
		assertEquals(0.0, SprengerFxPlan.fenceHeight(0.80), 1e-9);
		double mid = SprengerFxPlan.fenceHeight(0.90);
		assertTrue(mid > 1.5 && mid < 3.0, "easeOutCubic is past the middle halfway: " + mid);
		assertEquals(3.0, SprengerFxPlan.fenceHeight(1.00), 1e-9);
		assertEquals(3.0, SprengerFxPlan.fenceHeight(1.9), 1e-9);
		assertEquals(0.12, SprengerFxPlan.FENCE_ALPHA, 1e-9);
	}

	@Test
	void runeBandsTravelUpAndFadeAtTheEnds() {
		for (int k = 0; k < SprengerFxPlan.RUNE_BANDS; k++) {
			double p = SprengerFxPlan.runePhase(0.0, k);
			assertTrue(p >= 0.0 && p < 1.0);
		}
		assertEquals(0.0, SprengerFxPlan.runePhase(0.0, 0), 1e-9);
		assertEquals(1.0 / 3.0, SprengerFxPlan.runePhase(0.0, 1), 1e-9);
		assertTrue(SprengerFxPlan.runePhase(0.5, 0) > SprengerFxPlan.runePhase(0.0, 0));
		assertEquals(0.0, SprengerFxPlan.runeAlpha(0.0), 1e-9);
		assertEquals(1.0, SprengerFxPlan.runeAlpha(0.5), 1e-9);
		assertEquals(0.0, SprengerFxPlan.runeAlpha(1.0), 1e-9);
	}

	@Test
	void theHubStarOnlyFlaresAtTheBindAndIsGoneAfterFourTenths() {
		assertEquals(0.0, SprengerFxPlan.hubFlare(0.5), 1e-9, "no star while the outline draws");
		assertEquals(0.0, SprengerFxPlan.hubFlare(0.79), 1e-9);
		assertEquals(0.7, SprengerFxPlan.hubFlare(0.80), 1e-9);
		assertTrue(SprengerFxPlan.hubFlare(1.0) < 0.2);
		assertEquals(0.0, SprengerFxPlan.hubFlare(1.21), 1e-9);
		assertEquals(0.0, SprengerFxPlan.hubFlare(1.6), 1e-9, "nothing of the star during the charge: it belongs to the explosion");
	}

	@Test
	void theEdgePulseSpeedsUpTowardsTheExplosion() {
		// the existing formula: sin(t (6 + 22 k^2)), k = t / life
		double early = SprengerFxPlan.pulse(0.1, 2.0, false);
		assertEquals(0.65 + 0.35 * Math.sin(0.1 * (6.0 + 22.0 * 0.05 * 0.05)), early, 1e-9);
		double late = SprengerFxPlan.pulse(1.9, 2.0, false);
		assertEquals(0.65 + 0.35 * Math.sin(1.9 * (6.0 + 22.0 * 0.95 * 0.95)), late, 1e-9);
		for (double t = 0.0; t <= 2.0; t += 0.01) {
			double p = SprengerFxPlan.pulse(t, 2.0, false);
			assertTrue(p >= 0.3 - 1e-9 && p <= 1.0 + 1e-9, "pulse stays in 0.3 to 1: " + p);
			double q = SprengerFxPlan.pulse(t, 2.0, true);
			assertTrue(q >= 0.84 - 1e-9 && q <= 1.0 + 1e-9, "reduce motion almost still: " + q);
		}
	}

	@Test
	void edgeIntensityRampsWithTheFigureAndStaysBounded() {
		assertEquals(0.0, SprengerFxPlan.edgeIntensity(0.0, 2.0, false), 1e-9);
		for (double t = 0.0; t <= 2.0; t += 0.01) {
			double v = SprengerFxPlan.edgeIntensity(t, 2.0, false);
			assertTrue(v >= 0.0 && v <= 1.7, "intensity at " + t + " = " + v);
		}
		// the bind flash adds 0.6 over the base in its tenth of a second
		double base = SprengerFxPlan.edgeIntensity(0.79, 2.0, false);
		assertTrue(SprengerFxPlan.edgeIntensity(0.80, 2.0, false) > base - 0.2);
	}

	@Test
	void everythingDimsToTwoTenthsAtTheAnticipation() {
		assertEquals(1.0, SprengerFxPlan.dim(0.0), 1e-9);
		assertEquals(1.0, SprengerFxPlan.dim(1.74), 1e-9);
		assertEquals(0.2, SprengerFxPlan.dim(1.80), 1e-9);
		assertEquals(0.2, SprengerFxPlan.dim(1.89), 1e-9);
		assertTrue(SprengerFxPlan.dim(1.95) > 0.2 && SprengerFxPlan.dim(1.95) < 0.5);
		assertEquals(0.5, SprengerFxPlan.dim(2.0), 1e-9);
	}

	@Test
	void theChargeColumnGrowsForTheWholeChargeAndTheNeedleIsThin() {
		assertEquals(0.0, SprengerFxPlan.chargeHeight(0.0), 1e-9);
		assertEquals(4.0, SprengerFxPlan.chargeHeight(0.95), 1e-9);
		assertEquals(0.3, SprengerFxPlan.chargeWidth(0.0), 1e-9, "radius 0.15");
		assertEquals(1.2, SprengerFxPlan.chargeWidth(0.95), 1e-9, "radius 0.6");
		assertEquals(0.25, SprengerFxPlan.chargeIntensity(0.0), 1e-9);
		assertEquals(0.6, SprengerFxPlan.chargeIntensity(0.95), 1e-9);
		assertTrue(SprengerFxPlan.chargeHeight(0.475) < 2.0, "easeInQuad starts slowly");
		assertEquals(0.08, SprengerFxPlan.NEEDLE_RADIUS, 1e-9);
		assertTrue(SprengerFxPlan.NEEDLE_RADIUS < SprengerFxPlan.CHARGE_R0);
	}

	@Test
	void theHumRisesFromOneToTwoAndTheTremorGrows() {
		assertEquals(1.0, SprengerFxPlan.humPitch(0), 1e-9);
		assertEquals(2.0, SprengerFxPlan.humPitch(3), 1e-9);
		for (int k = 1; k < SprengerFxPlan.HUM_STEPS; k++) {
			assertTrue(SprengerFxPlan.humPitch(k) > SprengerFxPlan.humPitch(k - 1));
			assertTrue(SprengerFxPlan.tremor(k) > SprengerFxPlan.tremor(k - 1));
		}
		assertEquals(0.05, SprengerFxPlan.tremor(0), 1e-9);
		assertEquals(0.20, SprengerFxPlan.tremor(3), 1e-9);
		// four steps of 0.25 s from the bind, the last one still before the anticipation
		assertTrue(SprengerFxPlan.BIND_AT + SprengerFxPlan.HUM_EVERY * (SprengerFxPlan.HUM_STEPS - 1) < SprengerFxPlan.ANTICIPATION_AT);
	}

	@Test
	void theColumnOfTheExplosionIsFortyHighAndNarrowsToThirtyPercent() {
		assertEquals(0.0, SprengerFxPlan.columnHeight(0.0), 1e-9);
		assertTrue(SprengerFxPlan.columnHeight(0.06) > 30.0, "easeOutExpo is almost there at half the time");
		assertEquals(40.0, SprengerFxPlan.columnHeight(0.12), 1e-9);
		assertEquals(1.0, SprengerFxPlan.columnWidthScale(0.12), 1e-9);
		assertEquals(0.3, SprengerFxPlan.columnWidthScale(0.62), 1e-9);
		assertEquals(0.3, SprengerFxPlan.columnWidthScale(2.0), 1e-9);
		assertEquals(1.0, SprengerFxPlan.columnIntensity(0.3), 1e-9);
		assertEquals(0.0, SprengerFxPlan.columnIntensity(SprengerFxPlan.COLUMN_LIFE), 1e-9);
		assertEquals(2.61, SprengerFxPlan.coreRadius(R), 1e-9, "0.87 r");
	}

	@Test
	void theFlareAndTheShockRingScaleWithTheRadius() {
		assertEquals(12.0, SprengerFxPlan.flareSize(R, false), 1e-9);
		assertEquals(14.4, SprengerFxPlan.flareSize(R, true), 1e-9, "x1.2 for an early detonation");
		assertEquals(18.0, SprengerFxPlan.shockRingSize(R), 1e-9, "radius 9");
		assertEquals(6.0, SprengerFxPlan.rayLength(1.0, R), 1e-3);
		assertEquals(0.0, SprengerFxPlan.rayLength(0.0, R), 1e-9);
		assertEquals(12.0, SprengerFxPlan.rayLength(1.0, 2 * R), 1e-3);
		assertEquals(3.4, SprengerFxPlan.scarRadius(R), 1e-9);
		assertTrue(SprengerFxPlan.FLASH_EARLY > SprengerFxPlan.FLASH);
		assertEquals(0.55, SprengerFxPlan.FLASH, 1e-9);
		assertEquals(0.65, SprengerFxPlan.FLASH_EARLY, 1e-9);
		assertEquals(60.0, SprengerFxPlan.HITSTOP_MS, 1e-9);
	}

	@Test
	void raggedBeamsAreSeededInsideThePentagon() {
		for (long seed = 1; seed <= 25; seed++) {
			SprengerFxPlan.Ragged[] a = SprengerFxPlan.ragged(seed, R, 8);
			SprengerFxPlan.Ragged[] b = SprengerFxPlan.ragged(seed, R, 8);
			assertEquals(8, a.length);
			for (int i = 0; i < a.length; i++) {
				assertEquals(a[i], b[i], "same seed, same beams on every client");
				assertTrue(a[i].radius() >= 0.5 && a[i].radius() <= 1.0, "radius " + a[i].radius());
				assertTrue(a[i].height() >= 8.0 && a[i].height() <= 22.0, "height " + a[i].height());
				assertEquals(0.02 * i, a[i].delay(), 1e-9);
				assertTrue(SprengerFxPlan.insidePentagon(a[i].dx(), a[i].dz(), R, 0.0), "beam " + i + " of seed " + seed + " lies inside the pentagon");
			}
		}
		assertEquals(4, SprengerFxPlan.ragged(3, R, 4).length);
		SprengerFxPlan.Ragged[] one = SprengerFxPlan.ragged(1, R, 8);
		SprengerFxPlan.Ragged[] two = SprengerFxPlan.ragged(2, R, 8);
		assertFalse(java.util.Arrays.equals(one, two), "different seeds make different edges");
	}

	@Test
	void aCancelledPentagonSinksFastAndFadesInThreeTenths() {
		assertEquals(0.0, SprengerFxPlan.sinkDepth(0.0), 1e-9);
		assertEquals(SprengerFxPlan.CANCEL_SINK_DEPTH, SprengerFxPlan.sinkDepth(0.2), 1e-9);
		assertTrue(SprengerFxPlan.sinkDepth(0.1) < SprengerFxPlan.CANCEL_SINK_DEPTH * 0.2, "easeInCubic");
		assertEquals(1.0, SprengerFxPlan.fadeOut(0.0, 0.3), 1e-9);
		assertEquals(0.0, SprengerFxPlan.fadeOut(0.3, 0.3), 1e-9);
		assertEquals(1.0, SprengerFxPlan.fadeOut(-0.1, 0.3), 1e-9, "before the shatter nothing has faded");
		assertEquals(2.25, SprengerFxPlan.cancelTimeout(2.0), 1e-9);
		assertTrue(SprengerFxPlan.cancelTimeout(2.0) > SprengerFxPlan.EXPLODE_AT, "a late BURST is not mistaken for a cancel");
	}

	@Test
	void screenEffectsFollowTheDistanceRule() {
		assertEquals(1.0, SprengerFxPlan.screenFactor(true, 100.0), 1e-9, "the owner and the bound feel it fully");
		assertEquals(0.5, SprengerFxPlan.screenFactor(false, 0.0), 1e-9);
		assertEquals(0.0, SprengerFxPlan.screenFactor(false, 24.0), 1e-9);
		assertEquals(0.25, SprengerFxPlan.screenFactor(false, 16.0), 1e-9);
		assertTrue(SprengerFxPlan.gradeApplies(true, 100.0));
		assertTrue(SprengerFxPlan.gradeApplies(false, 16.0));
		assertFalse(SprengerFxPlan.gradeApplies(false, 16.1));
	}

	@Test
	void tiersKeepTheStoryboardCounts() {
		assertSame(SprengerFxPlan.HIGH, SprengerFxPlan.detail("high"));
		assertSame(SprengerFxPlan.HIGH, SprengerFxPlan.detail("CUSTOM"));
		assertSame(SprengerFxPlan.HIGH, SprengerFxPlan.detail(null));
		assertSame(SprengerFxPlan.MEDIUM, SprengerFxPlan.detail(" Medium "));
		assertSame(SprengerFxPlan.LOW, SprengerFxPlan.detail("LOW"));
		assertEquals(40, SprengerFxPlan.HIGH.dust());
		assertEquals(20, SprengerFxPlan.LOW.dust(), "dust x0.5 at Low");
		assertFalse(SprengerFxPlan.LOW.glitter(), "no glitter at Low");
		assertFalse(SprengerFxPlan.LOW.runes(), "fence alpha only at Low");
		assertTrue(SprengerFxPlan.HIGH.glitter() && SprengerFxPlan.HIGH.runes());
		assertTrue(SprengerFxPlan.HIGH.motes() > SprengerFxPlan.MEDIUM.motes() && SprengerFxPlan.MEDIUM.motes() > SprengerFxPlan.LOW.motes());
		assertTrue(SprengerFxPlan.HIGH.dust() + SprengerFxPlan.HIGH.motes() <= 180, "spawn P 180 at High");
	}

	@Test
	void soundTablesAreVanillaIdsWithSaneValues() {
		assertEquals(6, SprengerFxPlan.EXPLOSION_SOUNDS.length);
		for (SprengerFxPlan.Sfx s : SprengerFxPlan.EXPLOSION_SOUNDS) {
			assertTrue(s.id().matches("[a-z_]+(\\.[a-z_]+)+"), s.id());
			assertTrue(s.pitch() >= 0.5 && s.pitch() <= 2.0 && s.volume() > 0.0 && s.volume() <= 1.0, s.id());
			assertTrue(s.at() >= 0.0 && s.at() < 0.3);
		}
		assertEquals("entity.generic.explode", SprengerFxPlan.EXPLOSION_SOUNDS[0].id());
		assertEquals(3, java.util.Arrays.stream(SprengerFxPlan.EXPLOSION_SOUNDS).filter(s -> s.id().equals("block.amethyst_cluster.break")).count(), "three crystal breaks");
		assertEquals(SprengerFxPlan.SHATTER_AT, SprengerFxPlan.SHATTER_SOUND.at(), 1e-9);
		assertEquals(SprengerFxPlan.BIND_AT, SprengerFxPlan.BIND_SOUND_A.at(), 1e-9);
		assertEquals(SprengerFxPlan.SPOKE_AT, SprengerFxPlan.SPOKE_SOUND.at(), 1e-9);
		assertEquals(5 * SprengerFxPlan.EDGE_SOUND_STEP, 0.3, 1e-9);
	}
}
