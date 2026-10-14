package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.world.TerrainRules.Cell;
import dev.minebleach.reiatsutest.core.world.TerrainRules.Config;
import dev.minebleach.reiatsutest.core.world.TerrainRules.Facts;
import dev.minebleach.reiatsutest.core.world.TerrainRules.Profile;
import dev.minebleach.reiatsutest.core.world.TerrainRules.Verdict;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** R11 T9: the pure rules of block destruction by abilities (profiles by hardness, never-broken blocks, craters, tunnels, caps, config). */
class TerrainRulesTest {
	private static final Config CFG = Config.defaults();

	private static Facts block(double hardness) {
		return new Facts(false, false, hardness, false, false, false);
	}

	private static Verdict v(Profile p, Facts f) {
		return TerrainRules.verdict(CFG, p, f);
	}

	@Test
	void profilesBreakUpToTheirHardnessAndNoFurther() {
		// leaves 0.2, glass 0.3, dirt 0.5, wool 0.8 | stone 1.5, planks 2.0, log 2.0 | deepslate 3.0, iron block 5.0 | obsidian 50
		assertEquals(Verdict.OK, v(Profile.S, block(0.8)));
		assertEquals(Verdict.TOO_HARD, v(Profile.S, block(1.5)));
		assertEquals(Verdict.OK, v(Profile.M, block(1.5)));
		assertEquals(Verdict.OK, v(Profile.M, block(2.0)));
		assertEquals(Verdict.TOO_HARD, v(Profile.M, block(3.0)));
		assertEquals(Verdict.OK, v(Profile.L, block(3.0)));
		assertEquals(Verdict.OK, v(Profile.L, block(5.0)));
		assertEquals(Verdict.TOO_HARD, v(Profile.L, block(50.0)));
	}

	@Test
	void profileNoneBreaksNothing() {
		assertEquals(Verdict.PROFILE_OFF, v(Profile.NONE, block(0.0)));
	}

	@Test
	void neverBrokenWhateverTheProfile() {
		for (Profile p : new Profile[] {Profile.S, Profile.M, Profile.L}) {
			assertEquals(Verdict.AIR, v(p, new Facts(true, false, 0.0, false, false, false)));
			assertEquals(Verdict.FLUID, v(p, new Facts(false, true, 0.0, false, false, false)));
			assertEquals(Verdict.UNBREAKABLE, v(p, block(-1.0)), "hardness below 0 (bedrock, barrier, command blocks)");
			assertEquals(Verdict.BLOCK_ENTITY, v(p, new Facts(false, false, 0.5, true, false, false)), "chest, furnace, spawner");
			assertEquals(Verdict.SPECIAL, v(p, new Facts(false, false, 0.5, false, true, false)), "portals, frames, frost blocks, ice wall");
			assertEquals(Verdict.PROTECTED, v(p, new Facts(false, false, 0.5, false, false, true)), "spawn protection");
		}
	}

	@Test
	void specialBeatsHardnessInTheVerdictOrder() {
		// an ice wall block has strength -1: the reason shown is SPECIAL, not UNBREAKABLE, so the log names it
		assertEquals(Verdict.SPECIAL, v(Profile.L, new Facts(false, false, -1.0, false, true, false)));
	}

	@Test
	void worldSwitches() {
		assertTrue(TerrainRules.worldAllows(CFG, true, true));
		assertFalse(TerrainRules.worldAllows(CFG, false, true), "mobGriefing off: nothing breaks");
		Config ignore = new Config(true, true, false, 40, 6, 0.8, 2.0, 5.0, CFG.modeAttack(), CFG.scatter(), CFG.hakuteiken(), CFG.shakkaho());
		assertTrue(TerrainRules.worldAllows(ignore, false, true), "terrain.ignoreMobGriefing");
		Config off = new Config(false, false, false, 40, 6, 0.8, 2.0, 5.0, CFG.modeAttack(), CFG.scatter(), CFG.hakuteiken(), CFG.shakkaho());
		assertFalse(TerrainRules.worldAllows(off, true, true), "terrain.enabled false");
		assertFalse(TerrainRules.worldAllows(CFG, true, false), "the game mode of the owner");
	}

	@Test
	void gameModes() {
		assertTrue(TerrainRules.gameModeMayBreak("survival"));
		assertTrue(TerrainRules.gameModeMayBreak("creative"));
		assertFalse(TerrainRules.gameModeMayBreak("adventure"));
		assertFalse(TerrainRules.gameModeMayBreak("spectator"));
		assertFalse(TerrainRules.gameModeMayBreak(null));
	}

	@Test
	void sphereIsRoundCentredAndOrderedFromTheCentre() {
		List<Cell> cells = TerrainRules.sphere(10.5, 64.5, 10.5, 5.0);
		assertEquals(515, cells.size(), "a ball of radius 5 on a block centre holds 515 cell centres");
		assertEquals(new Cell(10, 64, 10), cells.get(0), "the centre cell comes first");
		double last = -1.0;
		for (Cell c : cells) {
			double d = Math.sqrt(Math.pow(c.x() + 0.5 - 10.5, 2) + Math.pow(c.y() + 0.5 - 64.5, 2) + Math.pow(c.z() + 0.5 - 10.5, 2));
			assertTrue(d <= 5.0 + 1.0E-9);
			assertTrue(d >= last - 1.0E-9, "non-decreasing distance");
			last = d;
		}
		assertEquals(cells.size(), new HashSet<>(cells).size(), "no duplicates");
	}

	@Test
	void modeAttackRadiusThreeIsSmall() {
		assertEquals(123, TerrainRules.sphere(0.5, 0.5, 0.5, 3.0).size());
	}

	@Test
	void zeroRadiusGivesNothing() {
		assertTrue(TerrainRules.sphere(0, 0, 0, 0.0).isEmpty());
		assertTrue(TerrainRules.tunnel(0, 0, 0, 5, 0, 0, 0.0).isEmpty());
	}

	@Test
	void tunnelFollowsTheSegmentAndOpensFromItsStart() {
		List<Cell> cells = TerrainRules.tunnel(0.5, 0.5, 0.5, 20.5, 0.5, 0.5, 1.5);
		Set<Cell> set = new HashSet<>(cells);
		assertTrue(set.contains(new Cell(10, 0, 0)));
		assertTrue(set.contains(new Cell(10, 1, 0)));
		assertTrue(set.contains(new Cell(10, 0, -1)));
		assertFalse(set.contains(new Cell(10, 0, 3)), "outside the radius");
		assertFalse(set.contains(new Cell(10, 3, 0)));
		int maxX = -1000;
		for (Cell c : cells) {
			assertTrue(c.x() >= maxX - 2, "ordered from the start to the end");
			maxX = Math.max(maxX, c.x());
		}
		assertTrue(cells.size() > 120 && cells.size() < 220, "a round tunnel of radius 1.5 over 20 blocks, cells: " + cells.size());
	}

	@Test
	void tunnelOfADegenerateSegmentIsASphere() {
		assertEquals(TerrainRules.sphere(3.5, 3.5, 3.5, 2.0), TerrainRules.tunnel(3.5, 3.5, 3.5, 3.5, 3.5, 3.5, 2.0));
	}

	@Test
	void capsLimitACastAcrossPhases() {
		assertEquals(60, TerrainRules.remaining(60, 0));
		assertEquals(20, TerrainRules.remaining(60, 40));
		assertEquals(0, TerrainRules.remaining(60, 60));
		assertEquals(0, TerrainRules.remaining(60, 500));
		List<Cell> cells = TerrainRules.sphere(0.5, 0.5, 0.5, 5.0);
		assertEquals(60, TerrainRules.capped(cells, 60).size());
		assertEquals(cells.get(0), TerrainRules.capped(cells, 60).get(0));
		assertTrue(TerrainRules.capped(cells, 0).isEmpty());
		assertEquals(cells.size(), TerrainRules.capped(cells, 100000).size());
	}

	@Test
	void batchesSpreadTheWork() {
		assertEquals(0, TerrainRules.budgetTicks(0, 40));
		assertEquals(1, TerrainRules.budgetTicks(40, 40));
		assertEquals(2, TerrainRules.budgetTicks(41, 40));
		assertEquals(18, TerrainRules.budgetTicks(700, 40));
		assertEquals(700, TerrainRules.budgetTicks(700, 1));
		assertEquals(700, TerrainRules.budgetTicks(700, 0), "a zero batch size is read as 1");
		assertTrue(TerrainRules.effectAt(0, 6));
		assertFalse(TerrainRules.effectAt(5, 6));
		assertTrue(TerrainRules.effectAt(6, 6));
		assertTrue(TerrainRules.effectAt(5, 0), "a zero interval is read as every block");
	}

	@Test
	void defaultsMatchThePlan() {
		assertTrue(CFG.enabled());
		assertFalse(CFG.ignoreMobGriefing());
		assertFalse(CFG.drops());
		assertEquals(40, CFG.blocksPerTick());
		assertEquals(0.8, CFG.maxHardness(Profile.S));
		assertEquals(2.0, CFG.maxHardness(Profile.M));
		assertEquals(5.0, CFG.maxHardness(Profile.L));
		assertEquals(-1.0, CFG.maxHardness(Profile.NONE));
		assertTrue(CFG.modeAttack().enabled());
		assertEquals(Profile.S, CFG.modeAttack().profile());
		assertEquals(3.0, CFG.modeAttack().radius());
		assertEquals(60, CFG.modeAttack().cap());
		assertEquals(Profile.M, CFG.scatter().profile());
		assertEquals(5.0, CFG.scatter().radius());
		assertEquals(400, CFG.scatter().cap());
		assertEquals(Profile.L, CFG.hakuteiken().profile());
		assertEquals(1.5, CFG.hakuteiken().radius());
		assertEquals(5.0, CFG.hakuteiken().radius2());
		assertEquals(700, CFG.hakuteiken().cap());
		assertFalse(CFG.shakkaho().enabled(), "Shakkaho does not break blocks by default");
		assertEquals(2.5, CFG.shakkaho().radius());
		assertEquals(40, CFG.shakkaho().cap());
	}

	@Test
	void configRoundTripsThroughJson() {
		JsonObject root = new JsonObject();
		root.add(Config.SECTION, CFG.toJson());
		assertEquals(CFG, Config.fromRoot(root));
	}

	@Test
	void configReadsUserValuesAndClampsGarbage() {
		JsonObject root = JsonParser.parseString("""
				{"terrain": {"enabled": false, "ignoreMobGriefing": true, "drops": true, "blocksPerTick": 99999, "effectEveryBlocks": 0,
				  "profiles": {"S": {"maxHardness": 1.2}, "M": {"maxHardness": "x"}},
				  "scatter": {"enabled": false, "profile": "l", "craterRadius": 99, "cap": -5},
				  "hakuteiken": {"profile": "nonsense", "tunnelRadius": 2.0},
				  "shakkaho": {"enabled": true}}}""").getAsJsonObject();
		Config c = Config.fromRoot(root);
		assertFalse(c.enabled());
		assertTrue(c.ignoreMobGriefing());
		assertTrue(c.drops());
		assertEquals(2000, c.blocksPerTick());
		assertEquals(1, c.effectEveryBlocks());
		assertEquals(1.2, c.maxHardness(Profile.S));
		assertEquals(2.0, c.maxHardness(Profile.M), "garbage gives the default");
		assertFalse(c.scatter().enabled());
		assertEquals(Profile.L, c.scatter().profile(), "profile names are case insensitive");
		assertEquals(16.0, c.scatter().radius(), "radius is clamped");
		assertEquals(0, c.scatter().cap());
		assertEquals(Profile.L, c.hakuteiken().profile(), "an unknown profile gives the default");
		assertEquals(2.0, c.hakuteiken().radius());
		assertEquals(5.0, c.hakuteiken().radius2(), "a missing key gives the default");
		assertTrue(c.shakkaho().enabled());
	}

	@Test
	void missingSectionGivesDefaults() {
		assertEquals(CFG, Config.fromRoot(new JsonObject()));
		assertEquals(CFG, Config.fromRoot(null));
	}
}
