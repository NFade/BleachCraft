package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.world.IceWallLayout.Plan;
import dev.minebleach.reiatsutest.core.world.IceWallLayout.Probe;
import dev.minebleach.reiatsutest.core.world.IceWallLayout.WallCell;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/** R11 T5: the pure cells of the ice wall (size, axis, surface search, dips, skipped cells, growth and fade schedule) and its config. */
class IceWallLayoutTest {
	private static final IceWallConfig CFG = IceWallConfig.defaults();

	/** A world with ground heights given by a function of (x, z): solid up to and including the height, free above. */
	private static Probe ground(BiFunction<Integer, Integer, Integer> height) {
		return new Probe() {
			@Override
			public boolean passable(int x, int y, int z) {
				return y > height.apply(x, z);
			}

			@Override
			public boolean support(int x, int y, int z) {
				return y <= height.apply(x, z);
			}
		};
	}

	private static final Probe FLAT = ground((x, z) -> 63);

	@Test
	void distanceIsTheRangeOrOneBlockBeforeTheObstacle() {
		assertEquals(10.0, IceWallLayout.distance(10.0, Double.NaN));
		assertEquals(10.0, IceWallLayout.distance(10.0, 10.0), "a hit at the end of the ray is no obstacle");
		assertEquals(5.0, IceWallLayout.distance(10.0, 6.0));
		assertEquals(1.0, IceWallLayout.distance(10.0, 1.5), "never closer than one block");
		assertEquals(1.0, IceWallLayout.distance(10.0, 0.2));
	}

	@Test
	void theAxisIsPerpendicularToTheDominantLookDirection() {
		assertTrue(IceWallLayout.plan(FLAT, CFG, 10.5, 64.5, 10.5, 1.0, 0.2).normalX());
		assertEquals(1, IceWallLayout.plan(FLAT, CFG, 10.5, 64.5, 10.5, 1.0, 0.2).sign());
		assertTrue(IceWallLayout.plan(FLAT, CFG, 10.5, 64.5, 10.5, -1.0, 0.9).normalX());
		assertEquals(-1, IceWallLayout.plan(FLAT, CFG, 10.5, 64.5, 10.5, -1.0, 0.9).sign());
		Plan z = IceWallLayout.plan(FLAT, CFG, 10.5, 64.5, 10.5, -0.3, 1.0);
		assertFalse(z.normalX());
		assertEquals(1, z.sign());
		assertEquals(-1, IceWallLayout.plan(FLAT, CFG, 10.5, 64.5, 10.5, 0.0, -1.0).sign());
		assertTrue(IceWallLayout.plan(FLAT, CFG, 10.5, 64.5, 10.5, 0.0, 0.0).normalX(), "no horizontal look: X");
	}

	@Test
	void defaultWallIsNineByFiveByTwoOnTheGround() {
		Plan p = IceWallLayout.plan(FLAT, CFG, 10.5, 64.5, 20.5, 1.0, 0.0);
		assertTrue(p.grounded());
		assertEquals(64, p.baseY());
		assertEquals(90, p.cells().size());
		assertEquals(5, p.layers());
		assertEquals(64, p.lowestY());
		assertEquals(68, p.topY());
		Set<Integer> xs = new HashSet<>();
		Set<Integer> zs = new HashSet<>();
		Set<Integer> ys = new HashSet<>();
		for (WallCell c : p.cells()) {
			xs.add(c.x());
			zs.add(c.z());
			ys.add(c.y());
			assertEquals(c.y() - 64, c.layer());
		}
		assertEquals(Set.of(10, 11), xs, "thickness 2 along the look (the normal is X)");
		assertEquals(Set.of(16, 17, 18, 19, 20, 21, 22, 23, 24), zs, "width 9 centred on the aimed column");
		assertEquals(Set.of(64, 65, 66, 67, 68), ys);
	}

	@Test
	void theWallLooksTheOtherWayAroundForANegativeLook() {
		Plan p = IceWallLayout.plan(FLAT, CFG, 10.5, 64.5, 20.5, -1.0, 0.0);
		Set<Integer> xs = new HashSet<>();
		for (WallCell c : p.cells()) {
			xs.add(c.x());
		}
		assertEquals(Set.of(10, 9), xs, "the far layer is away from the caster: it runs the way of the look");
		Plan z = IceWallLayout.plan(FLAT, CFG, 10.5, 64.5, 20.5, 0.0, 1.0);
		Set<Integer> zs = new HashSet<>();
		Set<Integer> xs2 = new HashSet<>();
		for (WallCell c : z.cells()) {
			zs.add(c.z());
			xs2.add(c.x());
		}
		assertEquals(Set.of(20, 21), zs);
		assertEquals(9, xs2.size());
		assertTrue(xs2.contains(6) && xs2.contains(14));
	}

	@Test
	void anEvenWidthIsOffCentreByOne() {
		IceWallConfig c = new IceWallConfig(true, 8, 5, 2, 200, 10, 10, 60.0, 160, 6, 2);
		Plan p = IceWallLayout.plan(FLAT, c, 0.5, 64.5, 0.5, 1.0, 0.0);
		Set<Integer> zs = new HashSet<>();
		for (WallCell cell : p.cells()) {
			zs.add(cell.z());
		}
		assertEquals(8, zs.size());
		assertEquals(-3, zs.stream().min(Integer::compare).get());
		assertEquals(4, zs.stream().max(Integer::compare).get());
	}

	@Test
	void theBaseIsFoundBelowTheAimedPointUpToTheSearchDepth() {
		// aimed 3 blocks above the ground: the base is the ground
		Plan p = IceWallLayout.plan(FLAT, CFG, 0.5, 67.5, 0.5, 1.0, 0.0);
		assertTrue(p.grounded());
		assertEquals(64, p.baseY());
		// aimed 10 blocks above: no ground within 6, the wall hangs at the aimed height
		Plan hang = IceWallLayout.plan(FLAT, CFG, 0.5, 74.5, 0.5, 1.0, 0.0);
		assertFalse(hang.grounded());
		assertEquals(74, hang.baseY());
		assertEquals(5, hang.layers());
		// exactly the search depth: 6 blocks below the start still counts
		assertTrue(IceWallLayout.plan(FLAT, CFG, 0.5, 70.5, 0.5, 1.0, 0.0).grounded());
		assertFalse(IceWallLayout.plan(FLAT, CFG, 0.5, 71.5, 0.5, 1.0, 0.0).grounded());
	}

	@Test
	void aimedIntoAHillsideTheBaseMovesUp() {
		// the aimed cell is inside the ground: look up to 2 cells for air
		Plan p = IceWallLayout.plan(FLAT, CFG, 0.5, 62.5, 0.5, 1.0, 0.0);
		assertTrue(p.grounded());
		assertEquals(64, p.baseY());
		// buried deeper than 2: no ground
		assertFalse(IceWallLayout.plan(FLAT, CFG, 0.5, 60.5, 0.5, 1.0, 0.0).grounded());
	}

	@Test
	void columnsReachDownIntoADipSoNoGapIsLeft() {
		// the column z = 2 is a trench 2 blocks deep (ground at 61)
		Probe dip = ground((x, z) -> z == 2 ? 61 : 63);
		Plan p = IceWallLayout.plan(dip, CFG, 0.5, 64.5, 0.5, 1.0, 0.0);
		assertEquals(64, p.baseY());
		assertEquals(62, p.lowestY());
		assertEquals(7, p.layers(), "the dip adds two layers at the bottom");
		int inDip = 0;
		for (WallCell c : p.cells()) {
			if (c.z() == 2) {
				inDip++;
			}
		}
		assertEquals(2 * 7, inDip, "7 cells high in the trench column, two deep");
		// a hole deeper than fillDown is not filled: the column keeps the base
		Probe deep = ground((x, z) -> z == 2 ? 50 : 63);
		Plan q = IceWallLayout.plan(deep, CFG, 0.5, 64.5, 0.5, 1.0, 0.0);
		assertEquals(64, q.lowestY());
		assertEquals(5, q.layers());
	}

	@Test
	void aColumnOnAStepKeepsTheBase() {
		// ground one block higher in the column z = 1: its lowest cell is still the base (that cell is solid, the glue skips it)
		Probe step = ground((x, z) -> z == 1 ? 64 : 63);
		Plan p = IceWallLayout.plan(step, CFG, 0.5, 64.5, 0.5, 1.0, 0.0);
		assertEquals(64, p.baseY());
		assertEquals(5, p.layers());
		assertEquals(90, p.cells().size());
	}

	@Test
	void skippedCellsAreRemovedAndTheRestKeepsItsOrder() {
		Plan p = IceWallLayout.plan(FLAT, CFG, 10.5, 64.5, 20.5, 1.0, 0.0);
		List<WallCell> rest = IceWallLayout.without(p.cells(), c -> c.x() == 10 && c.z() == 20); // an entity stands in the column
		assertEquals(90 - 5, rest.size());
		for (WallCell c : rest) {
			assertFalse(c.x() == 10 && c.z() == 20);
		}
		int last = -1;
		for (WallCell c : rest) {
			assertTrue(c.layer() >= last, "still bottom to top");
			last = c.layer();
		}
		assertEquals(p.cells().size(), IceWallLayout.without(p.cells(), c -> false).size());
		assertTrue(IceWallLayout.without(p.cells(), c -> true).isEmpty());
	}

	@Test
	void theLimitKeepsTheLowestLayers() {
		Plan p = IceWallLayout.plan(FLAT, CFG, 10.5, 64.5, 20.5, 1.0, 0.0);
		List<WallCell> cut = IceWallLayout.limit(p.cells(), 40);
		assertEquals(40, cut.size());
		for (WallCell c : cut) {
			assertTrue(c.layer() <= 2, "no hole at the bottom: layers 0, 1 and part of 2");
		}
		assertEquals(90, IceWallLayout.limit(p.cells(), 500).size());
		assertTrue(IceWallLayout.limit(p.cells(), 0).isEmpty());
	}

	@Test
	void floorYFindsTheFirstFreeCellOnGround() {
		assertEquals(64, IceWallLayout.floorY(FLAT, 0, 0, 64, 6, 2));
		assertEquals(64, IceWallLayout.floorY(FLAT, 0, 0, 68, 6, 2));
		assertEquals(IceWallLayout.NO_GROUND, IceWallLayout.floorY(FLAT, 0, 0, 71, 6, 2));
		assertEquals(64, IceWallLayout.floorY(FLAT, 0, 0, 63, 6, 2), "starts inside the ground: moves up");
		assertEquals(IceWallLayout.NO_GROUND, IceWallLayout.floorY(FLAT, 0, 0, 55, 6, 2));
		// water below the free cells is neither free nor ground: there is no floor to stand on
		Probe lake = new Probe() {
			@Override
			public boolean passable(int x, int y, int z) {
				return y > 64;
			}

			@Override
			public boolean support(int x, int y, int z) {
				return y <= 60;
			}
		};
		assertEquals(IceWallLayout.NO_GROUND, IceWallLayout.floorY(lake, 0, 0, 66, 6, 2));
	}

	@Test
	void growthRunsBottomToTopOverTheGrowTime() {
		int[] due = new int[5];
		for (int layer = 0; layer < 5; layer++) {
			due[layer] = IceWallLayout.dueTick(layer, 5, 10);
		}
		assertEquals(0, due[0]);
		assertEquals(2, due[1]);
		assertEquals(4, due[2]);
		assertEquals(6, due[3]);
		assertEquals(8, due[4]);
		assertTrue(due[4] < 10, "the last layer lands before the 0.5 s are over");
		assertEquals(0, IceWallLayout.dueTick(0, 1, 10));
		assertEquals(0, IceWallLayout.dueTick(5, 1, 10));
		for (int layers = 1; layers <= 9; layers++) {
			int prev = -1;
			for (int layer = 0; layer < layers; layer++) {
				int d = IceWallLayout.dueTick(layer, layers, 10);
				assertTrue(d >= prev && d <= 10);
				prev = d;
			}
		}
	}

	@Test
	void theTopLayersFadeAtTheEndOfTheLife() {
		assertEquals(0, IceWallLayout.goneLayers(0, 200, 10, 5));
		assertEquals(0, IceWallLayout.goneLayers(190, 200, 10, 5));
		assertEquals(1, IceWallLayout.goneLayers(191, 200, 10, 5));
		assertEquals(3, IceWallLayout.goneLayers(195, 200, 10, 5));
		assertEquals(5, IceWallLayout.goneLayers(199, 200, 10, 5));
		assertEquals(5, IceWallLayout.goneLayers(200, 200, 10, 5));
		assertEquals(5, IceWallLayout.goneLayers(500, 200, 10, 5));
		assertEquals(0, IceWallLayout.goneLayers(199, 200, 0, 5), "no fade: the wall just ends at the life time");
		int prev = 0;
		for (int age = 0; age <= 200; age++) {
			int g = IceWallLayout.goneLayers(age, 200, 10, 7);
			assertTrue(g >= prev && g <= 7);
			prev = g;
		}
	}

	// ------------------------------------------------------------------ config

	@Test
	void defaultsMatchThePlan() {
		assertTrue(CFG.enabled());
		assertEquals(9, CFG.width());
		assertEquals(5, CFG.height());
		assertEquals(2, CFG.thickness());
		assertEquals(200, CFG.lifeTicks(), "10 s");
		assertEquals(10, CFG.growTicks(), "0.5 s");
		assertEquals(60.0, CFG.hp());
		assertEquals(160, CFG.maxBlocks());
	}

	@Test
	void configRoundTripsAndSharesTheRukiaObjectWithThePassive() {
		JsonObject root = new JsonObject();
		JsonObject rukia = new JsonObject();
		rukia.add("passive", new JsonObject());
		rukia.add(IceWallConfig.SUB, CFG.toJson());
		root.add(IceWallConfig.SECTION, rukia);
		assertEquals(CFG, IceWallConfig.fromRoot(root));
		assertEquals("rukia", IceWallConfig.SECTION);
		assertEquals("iceWall", IceWallConfig.SUB);
	}

	@Test
	void configClampsGarbageAndFillsMissingKeys() {
		JsonObject root = JsonParser.parseString("""
				{"rukia": {"iceWall": {"width": 999, "height": 0, "thickness": "x", "hp": -5, "lifeTicks": 100, "enabled": false}}}""").getAsJsonObject();
		IceWallConfig c = IceWallConfig.fromRoot(root);
		assertFalse(c.enabled());
		assertEquals(25, c.width());
		assertEquals(1, c.height());
		assertEquals(2, c.thickness(), "garbage gives the default");
		assertEquals(1.0, c.hp());
		assertEquals(100, c.lifeTicks());
		assertEquals(10, c.growTicks(), "a missing key gives the default");
		assertEquals(CFG, IceWallConfig.fromRoot(new JsonObject()));
		assertEquals(CFG, IceWallConfig.fromRoot(null));
	}
}
