package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.swarm.V3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The passive homing of every arrow: target rule, turn limit, terrain, determinism. */
class HomingTest {
	private static final UUID OWNER = UUID.randomUUID();

	/** Spheres of radius 0.5 for entities (players flagged), an optional wall plane at x = wallX. */
	private static final class FakeWorld implements ArrowSim.HitWorld {
		final Map<Integer, V3> pos = new HashMap<>();
		final Map<Integer, Boolean> player = new HashMap<>();
		Double wallX;
		int candidateCalls;

		void add(int id, V3 p, boolean isPlayer) {
			pos.put(id, p);
			player.put(id, isPlayer);
		}

		@Override
		public double raycastBlock(V3 from, V3 to) {
			if (wallX == null) {
				return -1.0;
			}
			if (from.x() < wallX && to.x() >= wallX) {
				return (wallX - from.x()) / (to.x() - from.x());
			}
			if (from.x() >= wallX && to.x() < wallX) {
				return (from.x() - wallX) / (from.x() - to.x());
			}
			return -1.0;
		}

		@Override
		public List<ArrowSim.EntityHit> entitiesAlong(UUID owner, V3 from, V3 to, double radius) {
			List<ArrowSim.EntityHit> out = new ArrayList<>();
			for (Map.Entry<Integer, V3> en : pos.entrySet()) {
				for (int i = 0; i <= 100; i++) {
					double t = i / 100.0;
					if (from.lerp(to, t).distanceTo(en.getValue()) <= 0.5 + radius) {
						out.add(new ArrowSim.EntityHit(en.getKey(), t));
						break;
					}
				}
			}
			return out;
		}

		@Override
		public V3 targetPosition(int id) {
			return pos.get(id);
		}

		@Override
		public List<Homing.Candidate> candidates(UUID owner, V3 p, double radius) {
			candidateCalls++;
			List<Homing.Candidate> out = new ArrayList<>();
			for (Map.Entry<Integer, V3> en : pos.entrySet()) {
				if (en.getValue().distanceTo(p) <= radius) {
					out.add(new Homing.Candidate(en.getKey(), en.getValue(), player.get(en.getKey())));
				}
			}
			return out;
		}
	}

	private static ArrowSim.Spec arrow(Homing.Params auto, V3 vel) {
		return new ArrowSim.Spec(OWNER, ArrowSim.Kind.BASIC, V3.ZERO, vel, 0.0, 4f, 80, 0, 0.3, -1, 0.0, 0, auto);
	}

	private static Homing.Params basic() {
		return Homing.Params.degrees(2.5, 14, 60);
	}

	private static Homing.Params strong() {
		return Homing.Params.degrees(10, 20, 100);
	}

	private static double angleDeg(V3 a, V3 b) {
		return Math.toDegrees(ArrowSim.angleBetween(a, b));
	}

	private static boolean flies(FakeWorld w, Homing.Params p, V3 vel, int ticks) {
		ArrowSim sim = new ArrowSim();
		sim.spawn(arrow(p, vel));
		boolean hit = false;
		for (int i = 0; i < ticks && !sim.isEmpty(); i++) {
			for (ArrowSim.Event e : sim.step(w)) {
				hit |= e.type() == ArrowSim.EventType.HIT;
			}
		}
		return hit;
	}

	@Test
	void anArrowWithoutHomingFliesStraight() {
		FakeWorld w = new FakeWorld();
		w.add(1, new V3(10, 0, 4), true);
		ArrowSim sim = new ArrowSim();
		ArrowSim.Arrow a = sim.spawn(arrow(null, new V3(2, 0, 0)));
		for (int i = 0; i < 3; i++) {
			sim.step(w);
		}
		assertEquals(0.0, a.vel().z(), 1e-12);
		assertEquals(0, w.candidateCalls, "no query for an arrow that does not home");
	}

	@Test
	void theTurnPerTickNeverExceedsTheLimit() {
		FakeWorld w = new FakeWorld();
		w.add(1, new V3(6, 0, 6), true);
		ArrowSim sim = new ArrowSim();
		ArrowSim.Arrow a = sim.spawn(arrow(basic(), new V3(2, 0, 0)));
		V3 last = a.vel();
		for (int i = 0; i < 6; i++) {
			sim.step(w);
			assertTrue(angleDeg(last, a.vel()) <= 2.5 + 1e-6, "tick " + i + " turned " + angleDeg(last, a.vel()));
			assertEquals(2.0, a.vel().length(), 1e-9, "the speed is kept");
			last = a.vel();
		}
		assertTrue(last.z() > 0, "it bends towards +z");
	}

	@Test
	void strongHomingCurvesIntoAPlayerOffToTheSide() {
		FakeWorld w = new FakeWorld();
		w.add(7, new V3(14, 0, 5), true);
		assertTrue(flies(w, strong(), new V3(2.4, 0, 0), 40), "a 10 degrees per tick arrow reaches a player 30 degrees off its line");
	}

	@Test
	void weakHomingStillReachesASmallOffsetButNotAWideOne() {
		FakeWorld near = new FakeWorld();
		near.add(1, new V3(10, 0, 1.6), true);
		assertTrue(flies(near, basic(), new V3(2.2, 0, 0), 30), "1.6 blocks off at 10 blocks is within the weak homing");
		FakeWorld wide = new FakeWorld();
		wide.add(1, new V3(10, 0, 9), true);
		assertTrue(!flies(wide, basic(), new V3(2.2, 0, 0), 30), "42 degrees off is outside what 2.5 degrees per tick can bend");
	}

	@Test
	void playersArePreferredOverACloserMob() {
		List<Homing.Candidate> cs = List.of(new Homing.Candidate(1, new V3(4, 0, 0), false), new Homing.Candidate(2, new V3(9, 0, 1), true));
		Homing.Candidate c = Homing.pick(V3.ZERO, new V3(1, 0, 0), cs, strong(), -1, 1.25, x -> true);
		assertEquals(2, c.id());
	}

	@Test
	void theNearestPlayerWinsAndMobsAreTheFallback() {
		V3 dir = new V3(1, 0, 0);
		Homing.Candidate c = Homing.pick(V3.ZERO, dir, List.of(new Homing.Candidate(1, new V3(9, 0, 1), true), new Homing.Candidate(2, new V3(5, 0, 1), true),
				new Homing.Candidate(3, new V3(2, 0, 0), false)), strong(), -1, 1.25, x -> true);
		assertEquals(2, c.id());
		Homing.Candidate m = Homing.pick(V3.ZERO, dir, List.of(new Homing.Candidate(3, new V3(8, 0, 0), false), new Homing.Candidate(4, new V3(5, 0, 1), false)),
				strong(), -1, 1.25, x -> true);
		assertEquals(4, m.id());
	}

	@Test
	void targetsBehindTheConeOrBeyondTheRadiusAreIgnored() {
		V3 dir = new V3(1, 0, 0);
		assertNull(Homing.pick(V3.ZERO, dir, List.of(new Homing.Candidate(1, new V3(-5, 0, 0), true)), basic(), -1, 1.25, x -> true), "behind");
		assertNull(Homing.pick(V3.ZERO, dir, List.of(new Homing.Candidate(1, new V3(15, 0, 0), true)), basic(), -1, 1.25, x -> true), "radius 14");
		assertNotNull(Homing.pick(V3.ZERO, dir, List.of(new Homing.Candidate(1, new V3(13, 0, 0), true)), basic(), -1, 1.25, x -> true));
		double a59 = Math.toRadians(59);
		double a61 = Math.toRadians(61);
		assertNotNull(Homing.pick(V3.ZERO, dir, List.of(new Homing.Candidate(1, new V3(Math.cos(a59) * 8, 0, Math.sin(a59) * 8), true)), basic(), -1, 1.25, x -> true));
		assertNull(Homing.pick(V3.ZERO, dir, List.of(new Homing.Candidate(1, new V3(Math.cos(a61) * 8, 0, Math.sin(a61) * 8), true)), basic(), -1, 1.25, x -> true));
	}

	@Test
	void aTargetBehindATerrainBlockIsNotChosenAndTheArrowFliesOn() {
		FakeWorld w = new FakeWorld();
		w.wallX = 5.0;
		w.add(1, new V3(8, 0, 3), true);
		ArrowSim sim = new ArrowSim();
		ArrowSim.Arrow a = sim.spawn(arrow(strong(), new V3(2, 0, 0)));
		sim.step(w);
		assertEquals(0.0, a.vel().z(), 1e-12, "the wall hides the player: no steering");
		assertEquals(-1, a.lockedTarget());
	}

	@Test
	void aLockedTargetIsKeptOutsideTheConeButNotBeyondRadiusTimesKeep() {
		V3 dir = new V3(1, 0, 0);
		Homing.Candidate c = new Homing.Candidate(5, new V3(0, 0, 8), true);
		assertNull(Homing.pick(V3.ZERO, dir, List.of(c), basic(), -1, 1.25, x -> true));
		assertEquals(5, Homing.pick(V3.ZERO, dir, List.of(c), basic(), 5, 1.25, x -> true).id());
		Homing.Candidate far = new Homing.Candidate(5, new V3(0, 0, 18), true);
		assertNull(Homing.pick(V3.ZERO, dir, List.of(far), basic(), 5, 1.25, x -> true));
	}

	@Test
	void aLockedMobIsDroppedForAPlayerWhoAppears() {
		V3 dir = new V3(1, 0, 0);
		Homing.Candidate mob = new Homing.Candidate(1, new V3(5, 0, 0), false);
		Homing.Candidate pl = new Homing.Candidate(2, new V3(10, 0, 1), true);
		assertEquals(1, Homing.pick(V3.ZERO, dir, List.of(mob), strong(), 1, 1.25, x -> true).id());
		assertEquals(2, Homing.pick(V3.ZERO, dir, List.of(mob, pl), strong(), 1, 1.25, x -> true).id());
	}

	@Test
	void anAlreadyHitTargetIsNotChasenAgain() {
		FakeWorld w = new FakeWorld();
		w.add(1, new V3(5, 0, 0), true);
		ArrowSim sim = new ArrowSim();
		ArrowSim.Spec s = new ArrowSim.Spec(OWNER, ArrowSim.Kind.BASIC, V3.ZERO, new V3(2.5, 0, 0), 0.0, 4f, 80, 1, 0.3, -1, 0.0, 3, strong());
		ArrowSim.Arrow a = sim.spawn(s);
		for (int i = 0; i < 4; i++) {
			sim.step(w);
		}
		assertTrue(a.hasHit(1));
		assertEquals(-1, a.lockedTarget(), "after piercing it no longer homes on the same body");
	}

	@Test
	void theSameInputsGiveTheSameFlight() {
		assertEquals(fly(), fly());
	}

	private static List<V3> fly() {
		FakeWorld w = new FakeWorld();
		w.add(1, new V3(10, 1, 5), true);
		w.add(2, new V3(10, 1, -5), true);
		ArrowSim sim = new ArrowSim();
		ArrowSim.Arrow a = sim.spawn(arrow(strong(), new V3(2.4, 0, 0)));
		List<V3> out = new ArrayList<>();
		for (int i = 0; i < 12 && !sim.isEmpty(); i++) {
			sim.step(w);
			out.add(a.pos());
		}
		return out;
	}
}
