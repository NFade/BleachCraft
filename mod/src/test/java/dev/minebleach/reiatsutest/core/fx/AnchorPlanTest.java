package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.fx.AnchorPlan.Command;
import dev.minebleach.reiatsutest.core.fx.AnchorPlan.Op;
import java.util.List;
import org.junit.jupiter.api.Test;

/** S1 to S3: which anchors the server spawns, ends or re-phases per effect id. */
class AnchorPlanTest {
	@Test
	void shikaiReleaseSpawnsOneSwarmAndEndsStaleAnchorsFirst() {
		List<Command> c = AnchorPlan.forEffect(3);
		assertEquals(Op.SPAWN_SWARM, c.get(c.size() - 1).op());
		assertTrue(c.indexOf(new Command(Op.END_SWARM, (byte) 0)) < c.size() - 1, "an old swarm ends before the new one spawns");
	}

	@Test
	void bankaiReleaseDropsTheSwarmAndSpawnsStaticRows() {
		List<Command> c = AnchorPlan.forEffect(4);
		assertTrue(c.stream().anyMatch(x -> x.op() == Op.END_SWARM));
		assertEquals(Op.SPAWN_ROWS, c.get(c.size() - 1).op());
		assertTrue(c.stream().noneMatch(x -> x.op() == Op.SPAWN_SWARM));
	}

	@Test
	void sealAndBankaiEndRemoveEverything() {
		for (int id : new int[] {10, 11}) {
			List<Command> c = AnchorPlan.forEffect(id);
			assertTrue(c.stream().anyMatch(x -> x.op() == Op.END_SWARM));
			assertTrue(c.stream().anyMatch(x -> x.op() == Op.END_ROWS));
		}
		assertEquals(2, AnchorPlan.forReset().size());
	}

	@Test
	void scatterAndHakuteikenSetTheRowsPhase() {
		assertEquals(new Command(Op.ROWS_PHASE, AnchorPlan.PHASE_SCATTERED), AnchorPlan.forEffect(32).get(0));
		assertEquals(new Command(Op.ROWS_PHASE, AnchorPlan.PHASE_CONSUMED), AnchorPlan.forEffect(33).get(0));
	}

	@Test
	void otherEffectsNeedNoAnchors() {
		for (int id : new int[] {1, 2, 20, 21, 22, 23, 30, 31, 34}) {
			assertTrue(AnchorPlan.forEffect(id).isEmpty(), "id " + id);
		}
	}

	@Test
	void flatYawFollowsTheMinecraftConvention() {
		assertEquals(0f, AnchorPlan.flatYawDeg(0, 1), 1e-4);
		assertEquals(90f, AnchorPlan.flatYawDeg(-1, 0), 1e-4);
		assertEquals(-90f, AnchorPlan.flatYawDeg(1, 0), 1e-4);
		assertEquals(0f, AnchorPlan.flatYawDeg(0, 0), 1e-4);
	}
}
