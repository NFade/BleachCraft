package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.swarm.V3;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Sprenger, Homing Volley and Sternenstaub: the numbers of design/URYU.md section 5 in ticks (Seele Schneider and the wing barrage have their own tests). */
class AbilityRulesTest {
	private static final UUID OWNER = UUID.randomUUID();

	// ------------------------------------------------------------------ Sprenger

	@Test
	void sprengerBindsAt16TicksAndExplodesAt40() {
		SprengerTrap t = new SprengerTrap(OWNER, new V3(0, 64, 0), 100);
		for (int now = 100; now < 116; now++) {
			assertEquals(SprengerTrap.Step.NONE, t.advance(now), "tick " + now);
		}
		assertEquals(SprengerTrap.Step.BIND, t.advance(116), "0.8 s");
		assertTrue(t.bound());
		assertEquals(SprengerTrap.Step.NONE, t.advance(117));
		for (int now = 118; now < 140; now++) {
			assertEquals(SprengerTrap.Step.NONE, t.advance(now));
		}
		assertEquals(SprengerTrap.Step.EXPLODE, t.advance(140), "2.0 s");
		assertTrue(t.finished());
		assertEquals(SprengerTrap.Step.NONE, t.advance(141), "reported once");
		assertEquals(9.0f, t.damage(), 1e-6);
	}

	@Test
	void sprengerNeverSkipsTheBindEvenAfterALateTick() {
		SprengerTrap t = new SprengerTrap(OWNER, new V3(0, 64, 0), 0);
		assertEquals(SprengerTrap.Step.BIND, t.advance(50), "the bind is reported before the explosion");
		assertEquals(SprengerTrap.Step.EXPLODE, t.advance(51));
	}

	@Test
	void earlyDetonationGivesFiftyPercentMoreAndEndsThePentagon() {
		SprengerTrap t = new SprengerTrap(OWNER, new V3(0, 64, 0), 0);
		assertTrue(t.detonateEarly());
		assertTrue(t.early());
		assertEquals(13.5f, t.damage(), 1e-5);
		assertEquals(SprengerTrap.Step.NONE, t.advance(40), "no second explosion");
		assertFalse(t.detonateEarly());
	}

	@Test
	void onlyAFullyFocusedArrowDetonates() {
		assertFalse(SprengerTrap.detonates(0));
		assertFalse(SprengerTrap.detonates(2));
		assertTrue(SprengerTrap.detonates(3));
	}

	@Test
	void pentagonAreaHasRadius3AndAHeightBand() {
		SprengerTrap t = new SprengerTrap(OWNER, new V3(10, 64, 10), 0);
		assertTrue(t.contains(10, 64, 10));
		assertTrue(t.contains(12.9, 65, 10));
		assertFalse(t.contains(13.1, 65, 10), "outside radius 3");
		assertFalse(t.contains(10, 68, 10), "too high");
		assertFalse(t.contains(10, 62, 10), "too deep");
	}

	@Test
	void leavingBeforeTheBindMeansNotBeingInsideAtTheBindTick() {
		SprengerTrap t = new SprengerTrap(OWNER, new V3(0, 64, 0), 0);
		V3 stays = new V3(1, 64, 0);
		V3 leaves = new V3(1, 64, 0).add(new V3(5, 0, 0)); // walked out in the 0.8 s
		assertEquals(SprengerTrap.Step.BIND, t.advance(UryuBalance.SPRENGER_BIND_AT));
		assertTrue(t.contains(stays.x(), stays.y(), stays.z()));
		assertFalse(t.contains(leaves.x(), leaves.y(), leaves.z()));
	}

	@Test
	void anArrowThroughThePentagonTouchesItAndOneThatPassesByDoesNot() {
		SprengerTrap t = new SprengerTrap(OWNER, new V3(0, 64, 0), 0);
		assertFalse(t.touchedBy(new V3(-10, 65, 0), new V3(-5.8, 65, 0)), "stops short of the radius");
		assertTrue(t.touchedBy(new V3(-4.4, 65, 0), new V3(-2.2, 65, 0)), "ends inside");
		assertTrue(t.touchedBy(new V3(-6.6, 65, 0), new V3(6.6, 65, 0)), "flies straight through");
		assertFalse(t.touchedBy(new V3(-6.6, 65, 4), new V3(6.6, 65, 4)), "passes beside it");
		assertFalse(t.touchedBy(new V3(-6.6, 70, 0), new V3(6.6, 70, 0)), "passes over it");
	}

	@Test
	void fivePointsOnTheCircle() {
		SprengerTrap t = new SprengerTrap(OWNER, new V3(2, 64, 3), 0);
		for (int i = 0; i < 5; i++) {
			V3 v = t.vertex(i);
			assertEquals(3.0, Math.hypot(v.x() - 2, v.z() - 3), 1e-9);
		}
		assertEquals(Math.toRadians(72), Math.acos(vecDot(t, 0, 1)), 1e-9);
	}

	private static double vecDot(SprengerTrap t, int a, int b) {
		V3 va = t.vertex(a).sub(t.center());
		V3 vb = t.vertex(b).sub(t.center());
		return va.dot(vb) / (va.length() * vb.length());
	}

	@Test
	void sprengerNumbers() {
		assertEquals(3.0, UryuBalance.SPRENGER_RADIUS);
		assertEquals(16, UryuBalance.SPRENGER_BIND_AT);
		assertEquals(40, UryuBalance.SPRENGER_EXPLODE_AT);
		assertEquals(40, UryuBalance.SPRENGER_BIND_TICKS);
		assertEquals(4, UryuBalance.SPRENGER_SLOW_AMP);
		assertEquals(9.0f, UryuBalance.SPRENGER_DAMAGE);
		assertEquals(20.0, UryuBalance.SPRENGER_RANGE);
	}

	// ------------------------------------------------------------------ Homing Volley

	@Test
	void volleyFiresThreePlusFocusMaxSix() {
		assertEquals(3, Volley.arrowCount(0));
		assertEquals(4, Volley.arrowCount(1));
		assertEquals(5, Volley.arrowCount(2));
		assertEquals(6, Volley.arrowCount(3));
		assertEquals(6, Volley.arrowCount(9));
		assertEquals(3.0f, UryuBalance.VOLLEY_DAMAGE);
		assertEquals(30, UryuBalance.VOLLEY_LIFE);
		assertEquals(12.0, UryuBalance.VOLLEY_TURN_DEG);
		assertEquals(15.0, UryuBalance.VOLLEY_CONE_DEG);
		assertEquals(30.0, UryuBalance.VOLLEY_LOCK_RANGE);
		// total 9 to 18 HP
		assertEquals(9.0f, Volley.arrowCount(0) * UryuBalance.VOLLEY_DAMAGE);
		assertEquals(18.0f, Volley.arrowCount(3) * UryuBalance.VOLLEY_DAMAGE);
	}

	@Test
	void coneLockPicksTheNearestInsideFifteenDegreesAndWithinThirtyBlocks() {
		V3 eye = new V3(0, 65, 0);
		V3 look = new V3(0, 0, 1);
		List<Volley.Candidate> c = new ArrayList<>();
		c.add(new Volley.Candidate(1, new V3(0, 65, 20))); // dead ahead, 20 blocks
		c.add(new Volley.Candidate(2, new V3(0, 65, 8))); // nearer, dead ahead
		c.add(new Volley.Candidate(3, new V3(10, 65, 10))); // 45 degrees: outside the cone
		c.add(new Volley.Candidate(4, new V3(0, 65, 35))); // too far
		assertEquals(2, Volley.pickTarget(eye, look, c, 15, 30));
		c.remove(1);
		assertEquals(1, Volley.pickTarget(eye, look, c, 15, 30));
		c.remove(0);
		assertEquals(-1, Volley.pickTarget(eye, look, c, 15, 30), "45 degrees and 35 blocks are no lock");
	}

	@Test
	void coneEdgeIsInclusive() {
		V3 eye = new V3(0, 0, 0);
		double a = Math.toRadians(14.9);
		V3 in = new V3(Math.sin(a) * 10, 0, Math.cos(a) * 10);
		double b = Math.toRadians(15.5);
		V3 out = new V3(Math.sin(b) * 10, 0, Math.cos(b) * 10);
		assertEquals(1, Volley.pickTarget(eye, new V3(0, 0, 1), List.of(new Volley.Candidate(1, in)), 15, 30));
		assertEquals(-1, Volley.pickTarget(eye, new V3(0, 0, 1), List.of(new Volley.Candidate(1, out)), 15, 30));
	}

	@Test
	void volleyDirectionsFanAroundTheLookAtTheSpreadAngle() {
		V3 look = new V3(0, 0, 1);
		for (int n = 3; n <= 6; n++) {
			V3[] d = Volley.directions(look, n, 20);
			assertEquals(n, d.length);
			for (V3 v : d) {
				assertEquals(1.0, v.length(), 1e-9);
				assertEquals(20.0, Math.toDegrees(Math.acos(v.dot(look))), 1e-6);
			}
			// all different
			for (int i = 0; i < n; i++) {
				for (int j = i + 1; j < n; j++) {
					assertTrue(d[i].distanceTo(d[j]) > 0.1);
				}
			}
		}
	}

	@Test
	void volleyDirectionsWorkLookingStraightUp() {
		V3[] d = Volley.directions(new V3(0, 1, 0), 4, 20);
		for (V3 v : d) {
			assertEquals(20.0, Math.toDegrees(Math.acos(v.y())), 1e-6);
		}
	}

	// ------------------------------------------------------------------ Sternenstaub

	@Test
	void sternenstaubNeedsAllThreeStacks() {
		assertFalse(Sternenstaub.canCast(0));
		assertFalse(Sternenstaub.canCast(1));
		assertFalse(Sternenstaub.canCast(2));
		assertTrue(Sternenstaub.canCast(3));
	}

	@Test
	void sternenstaubTwentyThenTwelve() {
		assertEquals(20.0f, Sternenstaub.damage(0));
		assertEquals(12.0f, Sternenstaub.damage(1));
		assertEquals(12.0f, Sternenstaub.damage(5));
		assertEquals(20.0f, Sternenstaub.total(1));
		assertEquals(44.0f, Sternenstaub.total(3));
		assertEquals(40.0, UryuBalance.STAR_LENGTH);
		assertEquals(2.0, UryuBalance.STAR_HALF_WIDTH * 2.0);
	}


	// ------------------------------------------------------------------ arrows numbers

	@Test
	void arrowConstants() {
		assertEquals(2.2, UryuBalance.ARROW_SPEED);
		assertEquals(14, UryuBalance.FOCUS_INTERVAL);
		assertEquals(10, UryuBalance.FOCUS_INTERVAL_ARTERIE);
		assertEquals(0.02, UryuBalance.FOCUS_MOVE_EPS);
		assertEquals(4.0f, UryuBalance.BASIC_DAMAGE);
		assertEquals(7.0f, UryuBalance.FOCUS_SHOT_DAMAGE);
	}
}
