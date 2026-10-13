package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.swarm.V3;
import dev.minebleach.reiatsutest.core.uryu.ArrowSim.Event;
import dev.minebleach.reiatsutest.core.uryu.ArrowSim.EventType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The arrow stepper against a fake world: spheres for entities, one wall plane for terrain. */
class ArrowSimTest {
	private static final UUID OWNER = UUID.randomUUID();

	/** Entities are spheres of radius 0.5; the terrain is an optional wall at x = wallX (solid for x &gt;= wallX). */
	private static final class FakeWorld implements ArrowSim.HitWorld {
		final Map<Integer, V3> entities = new HashMap<>();
		Double wallX;

		@Override
		public double raycastBlock(V3 from, V3 to) {
			if (wallX == null) {
				return -1.0;
			}
			if (from.x() >= wallX) {
				return 0.0;
			}
			if (to.x() < wallX) {
				return -1.0;
			}
			return (wallX - from.x()) / (to.x() - from.x());
		}

		@Override
		public List<ArrowSim.EntityHit> entitiesAlong(UUID owner, V3 from, V3 to, double radius) {
			List<ArrowSim.EntityHit> out = new ArrayList<>();
			for (Map.Entry<Integer, V3> en : entities.entrySet()) {
				double t = closest(from, to, en.getValue(), 0.5 + radius);
				if (t >= 0) {
					out.add(new ArrowSim.EntityHit(en.getKey(), t));
				}
			}
			return out;
		}

		@Override
		public V3 targetPosition(int id) {
			return entities.get(id);
		}

		/** First t where the segment is within r of c, or -1 (sampled finely). */
		static double closest(V3 a, V3 b, V3 c, double r) {
			int n = 200;
			for (int i = 0; i <= n; i++) {
				double t = (double) i / n;
				if (a.lerp(b, t).distanceTo(c) <= r) {
					return t;
				}
			}
			return -1;
		}
	}

	private static ArrowSim.Spec straight(V3 pos, V3 vel, float damage, int life, int pierce) {
		return new ArrowSim.Spec(OWNER, ArrowSim.Kind.BASIC, pos, vel, 0.0, damage, life, pierce, 0.3, -1, 0.0, 0);
	}

	private static long count(List<Event> ev, EventType t) {
		return ev.stream().filter(e -> e.type() == t).count();
	}

	@Test
	void arrowMovesByItsVelocityEveryTick() {
		ArrowSim sim = new ArrowSim();
		ArrowSim.Arrow a = sim.spawn(straight(new V3(0, 0, 0), new V3(2.2, 0, 0), 4f, 50, 0));
		FakeWorld w = new FakeWorld();
		List<Event> ev = sim.step(w);
		assertEquals(2.2, a.pos().x(), 1e-9);
		Event moved = ev.get(ev.size() - 1);
		assertEquals(EventType.MOVED, moved.type());
		assertEquals(0.0, moved.from().x(), 1e-9);
		assertEquals(2.2, moved.to().x(), 1e-9);
		sim.step(w);
		assertEquals(4.4, a.pos().x(), 1e-9);
	}

	@Test
	void gravityBendsTheFlightButZeroGravityDoesNot() {
		ArrowSim sim = new ArrowSim();
		ArrowSim.Arrow bent = sim.spawn(new ArrowSim.Spec(OWNER, ArrowSim.Kind.BASIC, new V3(0, 10, 0), new V3(2.2, 0, 0), UryuBalance.ARROW_GRAVITY, 4f, 50,
				0, 0.3, -1, 0, 0));
		ArrowSim.Arrow flat = sim.spawn(new ArrowSim.Spec(OWNER, ArrowSim.Kind.FOCUS, new V3(0, 10, 0), new V3(4.4, 0, 0), 0.0, 14f, 50, 1, 0.3, -1, 0, 3));
		FakeWorld w = new FakeWorld();
		for (int i = 0; i < 20; i++) {
			sim.step(w);
		}
		assertTrue(bent.pos().y() < 10.0 - 1.0, "the unfocused arrow drops");
		assertEquals(10.0, flat.pos().y(), 1e-9);
	}

	@Test
	void arrowHitsAnEntityAndStopsAtItsHitPoint() {
		ArrowSim sim = new ArrowSim();
		sim.spawn(straight(new V3(0, 0, 0), new V3(2.2, 0, 0), 4f, 50, 0));
		FakeWorld w = new FakeWorld();
		w.entities.put(7, new V3(5, 0, 0));
		int ticks = 0;
		List<Event> hit = null;
		while (hit == null && ticks++ < 10) {
			List<Event> ev = sim.step(w);
			if (count(ev, EventType.HIT) > 0) {
				hit = ev;
			}
		}
		assertNotNull(hit);
		Event h = hit.stream().filter(e -> e.type() == EventType.HIT).findFirst().orElseThrow();
		assertEquals(7, h.targetId());
		assertEquals(4f, h.damage());
		assertTrue(h.to().x() > 3.9 && h.to().x() < 4.7, "hit point on the near surface: " + h.to().x());
		assertTrue(sim.isEmpty(), "an arrow without pierce dies on its first target");
	}

	@Test
	void pierceGoesThroughOneExtraTargetAndOnlyOne() {
		ArrowSim sim = new ArrowSim();
		sim.spawn(straight(new V3(0, 0, 0), new V3(2.2, 0, 0), 14f, 50, 1));
		FakeWorld w = new FakeWorld();
		w.entities.put(1, new V3(4, 0, 0));
		w.entities.put(2, new V3(7, 0, 0));
		w.entities.put(3, new V3(10, 0, 0));
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			for (Event e : sim.step(w)) {
				if (e.type() == EventType.HIT) {
					order.add(e.targetId());
				}
			}
		}
		assertEquals(List.of(1, 2), order, "two targets in order, the third is behind the pierce limit");
		assertTrue(sim.isEmpty());
	}

	@Test
	void aTargetIsHitOnlyOncePerArrowEvenWhileTheArrowStaysInsideItsShape() {
		ArrowSim sim = new ArrowSim();
		// a slow arrow that overlaps the same target on several ticks and pierces a lot
		sim.spawn(straight(new V3(0, 0, 0), new V3(0.2, 0, 0), 4f, 40, 5));
		FakeWorld w = new FakeWorld();
		w.entities.put(9, new V3(1.0, 0, 0));
		long hits = 0;
		for (int i = 0; i < 40; i++) {
			hits += count(sim.step(w), EventType.HIT);
		}
		assertEquals(1, hits);
	}

	@Test
	void arrowExpiresAfterItsLife() {
		ArrowSim sim = new ArrowSim();
		sim.spawn(straight(new V3(0, 0, 0), new V3(0, 1, 0), 4f, 3, 0));
		FakeWorld w = new FakeWorld();
		assertEquals(0, count(sim.step(w), EventType.EXPIRED));
		assertEquals(0, count(sim.step(w), EventType.EXPIRED));
		List<Event> last = sim.step(w);
		assertEquals(1, count(last, EventType.EXPIRED));
		assertTrue(sim.isEmpty());
	}

	@Test
	void terrainBlocksTheArrowAndTheEntityBehindTheWallIsSafe() {
		ArrowSim sim = new ArrowSim();
		sim.spawn(straight(new V3(0, 0, 0), new V3(2.2, 0, 0), 4f, 50, 1));
		FakeWorld w = new FakeWorld();
		w.wallX = 3.0;
		w.entities.put(5, new V3(4, 0, 0));
		long hits = 0;
		long blocked = 0;
		double lastX = 0;
		for (int i = 0; i < 6; i++) {
			List<Event> ev = sim.step(w);
			hits += count(ev, EventType.HIT);
			blocked += count(ev, EventType.BLOCKED);
			for (Event e : ev) {
				if (e.type() == EventType.BLOCKED) {
					lastX = e.to().x();
				}
			}
		}
		assertEquals(0, hits);
		assertEquals(1, blocked);
		assertEquals(3.0, lastX, 1e-9);
		assertTrue(sim.isEmpty());
	}

	@Test
	void anEntityInFrontOfTheWallIsStillHit() {
		ArrowSim sim = new ArrowSim();
		sim.spawn(straight(new V3(0, 0, 0), new V3(2.2, 0, 0), 4f, 50, 0));
		FakeWorld w = new FakeWorld();
		w.wallX = 6.0;
		w.entities.put(5, new V3(4, 0, 0));
		long hits = 0;
		for (int i = 0; i < 6; i++) {
			hits += count(sim.step(w), EventType.HIT);
		}
		assertEquals(1, hits);
	}

	@Test
	void homingTurnsAtMostTheRateEveryTick() {
		ArrowSim sim = new ArrowSim();
		double rate = Math.toRadians(UryuBalance.VOLLEY_TURN_DEG);
		ArrowSim.Arrow a = sim.spawn(new ArrowSim.Spec(OWNER, ArrowSim.Kind.VOLLEY, new V3(0, 0, 0), new V3(1.8, 0, 0), 0.0, 3f, 30, 0, 0.3, 1, rate, 0));
		FakeWorld w = new FakeWorld();
		w.entities.put(1, new V3(0, 0, 400)); // 90 degrees to the side and far away, so the aim stays the same
		V3 prev = a.vel();
		for (int i = 0; i < 6; i++) {
			sim.step(w);
			double turned = ArrowSim.angleBetween(prev, a.vel());
			assertTrue(turned <= rate + 1e-9, "tick " + i + " turned " + Math.toDegrees(turned) + " degrees");
			assertEquals(1.8, a.vel().length(), 1e-9, "speed is kept");
			prev = a.vel();
		}
		// 6 ticks of 12 degrees each, not more (the target direction itself drifts a little as the arrow moves, so allow a small margin)
		double total = Math.toDegrees(ArrowSim.angleBetween(new V3(1, 0, 0), a.vel()));
		assertTrue(total <= 72.0 + 1e-6, "total turn " + total);
		assertTrue(total > 60.0, "total turn " + total);
	}

	@Test
	void homingArrowReachesTheTargetAndHitsIt() {
		ArrowSim sim = new ArrowSim();
		ArrowSim.Arrow a = sim.spawn(new ArrowSim.Spec(OWNER, ArrowSim.Kind.VOLLEY, new V3(0, 0, 0), new V3(1.8, 0, 0), 0.0, 3f, 30, 0, 0.3, 1,
				Math.toRadians(12), 0));
		assertNotNull(a);
		FakeWorld w = new FakeWorld();
		w.entities.put(1, new V3(14, 0, 10)); // about 35 degrees off the launch direction
		int hit = -1;
		for (int i = 0; i < 30 && hit < 0; i++) {
			for (Event e : sim.step(w)) {
				if (e.type() == EventType.HIT) {
					hit = e.targetId();
				}
			}
		}
		assertEquals(1, hit);
	}

	@Test
	void homingArrowFliesStraightOnWhenTheTargetIsGone() {
		ArrowSim sim = new ArrowSim();
		ArrowSim.Arrow a = sim.spawn(new ArrowSim.Spec(OWNER, ArrowSim.Kind.VOLLEY, new V3(0, 0, 0), new V3(1.8, 0, 0), 0.0, 3f, 30, 0, 0.3, 4,
				Math.toRadians(12), 0));
		FakeWorld w = new FakeWorld(); // entity 4 does not exist
		sim.step(w);
		sim.step(w);
		assertEquals(-1, a.homingTarget());
		assertEquals(3.6, a.pos().x(), 1e-9);
		assertEquals(0.0, a.pos().z(), 1e-9);
	}

	@Test
	void turnTowardIsExactWithinTheLimitAndLimitedWhenBehind() {
		V3 x = new V3(1, 0, 0);
		V3 near = new V3(Math.cos(0.1), Math.sin(0.1), 0);
		assertEquals(0.0, ArrowSim.angleBetween(ArrowSim.turnToward(x, near, 0.2), near), 1e-9);
		V3 behind = new V3(-1, 0, 0);
		V3 turned = ArrowSim.turnToward(x, behind, 0.2);
		assertEquals(0.2, ArrowSim.angleBetween(x, turned), 1e-9);
	}

	@Test
	void removeOwnedDropsTheArrowsOfOneOwner() {
		ArrowSim sim = new ArrowSim();
		UUID other = UUID.randomUUID();
		sim.spawn(straight(new V3(0, 0, 0), new V3(1, 0, 0), 4f, 50, 0));
		sim.spawn(new ArrowSim.Spec(other, ArrowSim.Kind.BASIC, new V3(0, 0, 0), new V3(1, 0, 0), 0, 4f, 50, 0, 0.3, -1, 0, 0));
		assertEquals(1, sim.removeOwned(OWNER));
		assertEquals(1, sim.size());
	}

	@Test
	void removeOwnedByKindLeavesTheOthers() {
		ArrowSim sim = new ArrowSim();
		sim.spawn(straight(new V3(0, 0, 0), new V3(1, 0, 0), 4f, 50, 0));
		sim.spawn(new ArrowSim.Spec(OWNER, ArrowSim.Kind.RAIN, new V3(0, 0, 0), new V3(0, -1, 0), 0, 1.2f, 50, 0, 0.3, -1, 0, 0));
		assertEquals(1, sim.removeOwned(OWNER, ArrowSim.Kind.RAIN));
		assertEquals(1, sim.size());
		assertEquals(ArrowSim.Kind.BASIC, sim.arrows().get(0).kind());
	}

	@Test
	void capRefusesFurtherArrows() {
		ArrowSim sim = new ArrowSim(2);
		assertNotNull(sim.spawn(straight(new V3(0, 0, 0), new V3(1, 0, 0), 4f, 5, 0)));
		assertNotNull(sim.spawn(straight(new V3(0, 0, 0), new V3(1, 0, 0), 4f, 5, 0)));
		assertNull(sim.spawn(straight(new V3(0, 0, 0), new V3(1, 0, 0), 4f, 5, 0)));
	}

	@Test
	void movedEventCarriesTheFocusOfTheArrow() {
		ArrowSim sim = new ArrowSim();
		sim.spawn(new ArrowSim.Spec(OWNER, ArrowSim.Kind.FOCUS, new V3(0, 0, 0), new V3(4.4, 0, 0), 0, 14f, 50, 1, 0.3, -1, 0, 3));
		List<Event> ev = sim.step(new FakeWorld());
		Event moved = ev.get(ev.size() - 1);
		assertEquals(3, moved.focus());
		assertEquals(OWNER, moved.owner());
		assertFalse(ev.isEmpty());
	}
}
