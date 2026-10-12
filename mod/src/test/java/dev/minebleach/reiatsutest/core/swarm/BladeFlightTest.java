package dev.minebleach.reiatsutest.core.swarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

/** The server flight of the attack swarm: phases, limits per tick, steering, the return, and the validation of steer packets. */
class BladeFlightTest {
	private static final FlightConfig CFG = FlightConfig.defaults();
	private static final V3 EYE = new V3(0, 1.62, 0);
	private static final V3 LOOK = new V3(0, 0, 1);

	private static BladeFlight flight(V3 target) {
		return new BladeFlight(CFG, 32.0, EYE, LOOK, target);
	}

	/** Runs until DONE (or the limit) with a standing caster and returns the number of ticks. */
	private static int runToEnd(BladeFlight f, int limit) {
		int n = 0;
		while (!f.done() && n < limit) {
			f.step(EYE);
			n++;
		}
		return n;
	}

	@Test
	void unsteeredFlightGoesOutLoitersAndReturnsOnTheOldTimetable() {
		BladeFlight f = flight(new V3(0, 1.62, 20));
		boolean sawOut = false;
		boolean sawLoiter = false;
		boolean sawBack = false;
		int backStart = -1;
		while (!f.done() && f.ticks() < 200) {
			BladeFlight.Step st = f.step(EYE);
			sawOut |= st.phase() == BladeFlight.Phase.OUT;
			sawLoiter |= st.phase() == BladeFlight.Phase.LOITER;
			if (st.phase() == BladeFlight.Phase.BACK) {
				sawBack = true;
				if (backStart < 0) {
					backStart = f.ticks();
				}
			}
		}
		assertTrue(sawOut && sawLoiter && sawBack);
		assertEquals(CFG.returnTick(), backStart, "returns at returnTick when the key is not held");
		assertTrue(f.done());
		assertFalse(f.steered());
	}

	@Test
	void headNeverMovesFasterThanTheConfiguredSpeed() {
		BladeFlight f = flight(new V3(0, 1.62, 30));
		double cap = CFG.speedPerTick() + 1e-9;
		while (!f.done() && f.ticks() < 200) {
			BladeFlight.Step st = f.step(EYE);
			assertTrue(st.from().distanceTo(st.to()) <= cap + 1e-6, "step " + f.ticks() + " = " + st.from().distanceTo(st.to()));
		}
	}

	@Test
	void windupKeepsTheBladesAtTheHandWithoutSweeping() {
		BladeFlight f = flight(new V3(0, 1.62, 20));
		for (int i = 0; i < CFG.startDelayTicks(); i++) {
			BladeFlight.Step st = f.step(EYE);
			assertEquals(BladeFlight.Phase.WINDUP, st.phase());
			assertFalse(st.sweep());
			assertEquals(BladeFlight.START_R, st.to().distanceTo(EYE), 1e-9);
		}
		assertEquals(BladeFlight.Phase.OUT, f.step(EYE).phase());
	}

	@Test
	void steeringTurnsTheHeadByAtMostTheTurnLimitPerTick() {
		BladeFlight f = flight(new V3(0, 1.62, 6));
		for (int i = 0; i < 10; i++) {
			f.step(EYE); // arrive and hang at 6 blocks in front (before the return tick)
		}
		V3 before = f.head().sub(EYE);
		f.setHeld(true);
		f.steerTo(new V3(6, 1.62, 0)); // 90 degrees to the right
		double limit = SteerMath.maxTurnPerTick(CFG.turnRadPerTick(), CFG.speedPerTick(), before.length());
		for (int i = 0; i < 20; i++) { // (held: stays out until maxTicks)
			f.step(EYE);
			V3 now = f.head().sub(EYE);
			assertTrue(SteerMath.angle(before, now) <= limit + 1e-6, "turned " + Math.toDegrees(SteerMath.angle(before, now)) + " deg");
			before = now;
		}
		assertTrue(f.steered());
		assertTrue(f.head().distanceTo(new V3(6, 1.62, 0)) < 1.0, "reaches the new target: " + f.head());
	}

	@Test
	void turningIsAGradualCurveNotATeleport() {
		BladeFlight f = flight(new V3(0, 1.62, 10));
		for (int i = 0; i < 12; i++) {
			f.step(EYE);
		}
		f.setHeld(true);
		f.steerTo(new V3(-10, 1.62, 0));
		double maxJump = 0;
		for (int i = 0; i < 30; i++) {
			BladeFlight.Step st = f.step(EYE);
			maxJump = Math.max(maxJump, st.from().distanceTo(st.to()));
		}
		assertTrue(maxJump <= CFG.speedPerTick() + 0.05, "max step " + maxJump);
	}

	@Test
	void holdingTheKeyKeepsTheSwarmOutUntilTheHardLimitReleaseReturnsAtOnce() {
		BladeFlight f = flight(new V3(0, 1.62, 15));
		f.setHeld(true);
		while (f.ticks() < CFG.maxTicks() - 1) {
			assertNotEquals(BladeFlight.Phase.BACK, f.step(EYE).phase());
		}
		assertEquals(BladeFlight.Phase.BACK, f.step(EYE).phase(), "hard limit");

		BladeFlight g = flight(new V3(0, 1.62, 15));
		g.setHeld(true);
		while (g.ticks() < CFG.maxTicks() - 5) {
			g.step(EYE);
		}
		g.setHeld(false);
		assertEquals(BladeFlight.Phase.BACK, g.step(EYE).phase(), "released after returnTick returns at once");
	}

	@Test
	void releaseBeforeTheReturnTickFinishesTheTrajectoryThenReturnsAsBefore() {
		BladeFlight f = flight(new V3(0, 1.62, 12));
		f.setHeld(true);
		for (int i = 0; i < 10; i++) {
			f.step(EYE);
		}
		f.steerTo(new V3(5, 1.62, 5));
		f.setHeld(false);
		int backStart = -1;
		while (!f.done() && f.ticks() < 200) {
			if (f.step(EYE).phase() == BladeFlight.Phase.BACK && backStart < 0) {
				backStart = f.ticks();
			}
		}
		assertEquals(CFG.returnTick(), backStart);
	}

	@Test
	void returnLegShrinksTowardTheCasterAndEndsThere() {
		BladeFlight f = flight(new V3(0, 1.62, 10));
		while (f.phase() != BladeFlight.Phase.BACK && f.ticks() < 100) {
			f.step(EYE);
		}
		double prev = f.head().distanceTo(EYE);
		while (!f.done()) {
			f.step(EYE);
			double d = f.head().distanceTo(EYE);
			assertTrue(d <= prev + 1e-9);
			prev = d;
		}
		assertEquals(BladeFlight.BACK_DONE_R, prev, 1e-9);
	}

	@Test
	void aTeleportOfTheCasterIsNotSweptAsADiveThroughTheWorld() {
		BladeFlight f = flight(new V3(0, 1.62, 10));
		for (int i = 0; i < 12; i++) {
			f.step(EYE);
		}
		BladeFlight.Step st = f.step(EYE.add(new V3(20, 0, 0))); // shunpo
		assertFalse(st.sweep());
		assertTrue(f.step(EYE.add(new V3(20, 0, 0))).sweep());
	}

	@Test
	void rangeIsClampedToTheAbilityRange() {
		BladeFlight f = flight(new V3(0, 1.62, 500));
		f.setHeld(true);
		for (int i = 0; i < 60; i++) {
			f.step(EYE);
		}
		assertTrue(f.head().distanceTo(EYE) <= 32.0 + 1e-6);
	}

	@Test
	void clippingTheHeadAtABlockKeepsDirectionAndShortensTheRange() {
		BladeFlight f = flight(new V3(0, 1.62, 20));
		for (int i = 0; i < 12; i++) {
			f.step(EYE);
		}
		f.clipHead(EYE, new V3(0, 1.62, 5));
		assertEquals(5.0, f.head().distanceTo(EYE), 1e-9);
		assertEquals(1.0, f.head().sub(EYE).normalizedOr(V3.ZERO).z(), 1e-9);
	}

	// ------------------------------------------------------------------ blocks (round 5: the return must never stick in a wall)

	/** A block region (axis aligned box) standing in the world; the harness below plays the part of BladeFlights.run. */
	private record Block(double x0, double y0, double z0, double x1, double y1, double z1) {
		boolean contains(V3 p) {
			return p.x() >= x0 && p.x() <= x1 && p.y() >= y0 && p.y() <= y1 && p.z() >= z0 && p.z() <= z1;
		}
	}

	/** First point of the segment inside a block, backed off by 0.05; null when free. A segment that starts inside a block is stopped at its start. */
	private static V3 raycast(Block b, V3 from, V3 to) {
		if (b.contains(from)) {
			return from;
		}
		V3 d = to.sub(from);
		int n = Math.max(1, (int) Math.ceil(d.length() / 0.02));
		V3 prev = from;
		for (int i = 1; i <= n; i++) {
			V3 p = from.add(d.scale((double) i / n));
			if (b.contains(p)) {
				return prev;
			}
			prev = p;
		}
		return null;
	}

	/** One server tick like the old BladeFlights.run: the head is stopped at a block in EVERY phase (the bug); {@code fixed}: only outward, the way it is now. */
	private static BladeFlight.Step serverTick(BladeFlight f, V3 eye, Block b, boolean fixed) {
		BladeFlight.Step st = f.step(eye);
		if (st.sweep()) {
			V3 hit = raycast(b, st.from(), st.to());
			boolean back = st.leg() == HitCooldown.LEG_BACK;
			if (hit != null && !(fixed && back)) {
				f.clipHead(eye, hit);
			}
		}
		return st;
	}

	@Test
	void returnLegIgnoresBlocksAndEndsWithinTheWatchdogLimitEvenWhenTheHeadIsInsideABlock() {
		BladeFlight f = flight(new V3(0, 1.62, 18));
		Block none = new Block(100, 100, 100, 101, 101, 101);
		while (f.phase() != BladeFlight.Phase.BACK && f.ticks() < 100) {
			serverTick(f, EYE, none, true);
		}
		assertEquals(BladeFlight.Phase.BACK, f.phase());
		// the caster turned, a pillar now stands right where the head is and across the way home (what stuck the swarm before)
		V3 h = f.head();
		Block pillar = new Block(h.x() - 1.0, 0, 2.0, h.x() + 1.0, 6, h.z() + 1.0);
		assertTrue(pillar.contains(h));
		int limit = f.maxBackTicks();
		int n = 0;
		while (!f.done() && n < limit + 5) {
			serverTick(f, EYE, pillar, true);
			n++;
		}
		assertTrue(f.done(), "the return is over");
		assertTrue(n <= limit, "ticks " + n + " of at most " + limit);
		assertTrue(n <= Math.ceil(19.0 / CFG.backSpeedPerTick()) + 1, "about as long as the distance needs: " + n);
		assertEquals(BladeFlight.BACK_DONE_R, f.head().distanceTo(EYE), 1e-9);
		assertFalse(pillar.contains(f.head()), "the head is not left inside the block");
	}

	@Test
	void clipHeadIsIgnoredOnTheReturn() {
		BladeFlight f = flight(new V3(0, 1.62, 18));
		while (f.phase() != BladeFlight.Phase.BACK && f.ticks() < 100) {
			f.step(EYE);
		}
		f.step(EYE);
		V3 before = f.head();
		int backBefore = f.backTicks();
		f.clipHead(EYE, new V3(0, 1.62, 15.0)); // would have thrown the head back out to 15 blocks
		assertEquals(before, f.head());
		f.step(EYE);
		assertEquals(backBefore + 1, f.backTicks());
		assertTrue(f.head().distanceTo(EYE) < before.distanceTo(EYE), "still coming home");
	}

	@Test
	void aWallInFrontOfTheTargetHoldsTheHeadOnlyBrieflyThenTheSwarmIsRecalledAndComesHome() {
		BladeFlight f = flight(new V3(0, 1.62, 18)); // target behind a wall at z 8..9 (a tall wall: no way round)
		Block wall = new Block(-50, 0, 8, 50, 20, 9);
		int backAt = -1;
		int n = 0;
		while (!f.done() && n < 200) {
			BladeFlight.Step st = serverTick(f, EYE, wall, true);
			assertFalse(wall.contains(f.head()) && st.phase() == BladeFlight.Phase.OUT, "the outward head never enters the block (after the clip)");
			if (st.phase() == BladeFlight.Phase.BACK && backAt < 0) {
				backAt = f.ticks();
			}
			n++;
		}
		assertTrue(f.done());
		assertTrue(backAt > 0 && backAt <= CFG.startDelayTicks() + 8 + BladeFlight.STUCK_TICKS + 2, "recalled at tick " + backAt);
		assertTrue(n <= backAt + f.maxBackTicks(), "whole flight " + n + " ticks");
		assertEquals(BladeFlight.BACK_DONE_R, f.head().distanceTo(EYE), 1e-9);
	}

	@Test
	void heldKeyAtAWallStillEndsByTheHardLimit() {
		BladeFlight f = flight(new V3(0, 1.62, 18));
		f.setHeld(true);
		Block wall = new Block(-50, 0, 8, 50, 20, 9);
		int n = 0;
		while (!f.done() && n < 400) {
			serverTick(f, EYE, wall, true);
			n++;
		}
		assertTrue(f.done());
		assertTrue(n <= CFG.maxTicks() + f.maxBackTicks(), "ticks " + n);
	}

	@Test
	void hangingAtTheTargetNextToAWallIsNotStuck() {
		BladeFlight f = flight(new V3(0, 1.62, 7.95)); // the aimed point is the wall surface itself
		Block wall = new Block(-50, 0, 8, 50, 20, 9);
		f.setHeld(true);
		for (int i = 0; i < CFG.maxTicks() - 2; i++) {
			serverTick(f, EYE, wall, true);
			assertNotEquals(BladeFlight.Phase.BACK, f.phase(), "tick " + f.ticks());
		}
	}

	@Test
	void recallAndClipAreHarmlessAfterTheFlightEnded() {
		BladeFlight f = flight(new V3(0, 1.62, 10));
		assertTrue(runToEnd(f, 300) < 300);
		f.recall();
		f.clipHead(EYE, new V3(0, 1.62, 5));
		assertTrue(f.done());
		assertEquals(BladeFlight.BACK_DONE_R, f.head().distanceTo(EYE), 1e-9);
	}

	@Test
	void defaultTimingGivesAboutOneAndAHalfSecondsPerCycleAndTwoWithTheKeyHeld() {
		BladeFlight a = new BladeFlight(CFG, 19.0, EYE, LOOK, new V3(0, 1.62, 19));
		int ticks = runToEnd(a, 300);
		assertTrue(ticks >= 26 && ticks <= 32, "unheld cycle " + ticks + " ticks = " + ticks / 20.0 + " s");
		BladeFlight b = new BladeFlight(CFG, 19.0, EYE, LOOK, new V3(0, 1.62, 19));
		b.setHeld(true);
		int held = runToEnd(b, 300);
		assertTrue(held <= 44, "held cycle " + held + " ticks = " + held / 20.0 + " s");
		assertTrue(CFG.speed() >= 40.0);
	}

	@Test
	void flightEndsEvenWithNoTarget() {
		assertTrue(runToEnd(flight(EYE), 300) < 300);
	}

	// ------------------------------------------------------------------ steer gate

	@Test
	void gateAcceptsAHeldPacketAndLapsesWhenPacketsStop() {
		SteerGate g = new SteerGate(CFG, 100);
		SteerGate.Result r = g.accept(100, true, LOOK, LOOK);
		assertEquals(SteerGate.Verdict.OK, r.verdict());
		assertTrue(g.holding(100));
		assertTrue(g.holding(100 + CFG.steerTimeoutTicks()));
		assertFalse(g.holding(100 + CFG.steerTimeoutTicks() + 1));
	}

	@Test
	void gateReleaseEndsSteeringAtOnce() {
		SteerGate g = new SteerGate(CFG, 100);
		g.accept(100, true, LOOK, LOOK);
		assertEquals(SteerGate.Verdict.RELEASE, g.accept(101, false, LOOK, LOOK).verdict());
		assertFalse(g.holding(101));
	}

	@Test
	void gateDropsNonFiniteAndWronglyScaledVectors() {
		SteerGate g = new SteerGate(CFG, 100);
		assertEquals(SteerGate.Verdict.BAD_VECTOR, g.accept(100, true, new V3(Double.NaN, 0, 1), LOOK).verdict());
		assertEquals(SteerGate.Verdict.BAD_VECTOR, g.accept(101, true, new V3(0, 0, 0), LOOK).verdict());
		assertEquals(SteerGate.Verdict.BAD_VECTOR, g.accept(102, true, new V3(0, 0, 50), LOOK).verdict());
		assertFalse(g.holding(102));
		assertEquals(3, g.dropped());
	}

	@Test
	void gateRateLimitsAFloodButAllowsTenHertz() {
		SteerGate g = new SteerGate(CFG, 0);
		int ok = 0;
		for (int i = 0; i < 100; i++) { // 100 packets in one tick
			if (g.accept(0, true, LOOK, LOOK).verdict() == SteerGate.Verdict.OK) {
				ok++;
			}
		}
		assertTrue(ok <= 6, "burst only: " + ok);
		SteerGate h = new SteerGate(CFG, 0);
		for (int tick = 0; tick < 200; tick += 2) { // 10 Hz for 10 s
			assertEquals(SteerGate.Verdict.OK, h.accept(tick, true, LOOK, LOOK).verdict());
		}
		assertEquals(0, h.dropped());
	}

	@Test
	void gateReplacesADirectionFarFromTheServerLook() {
		SteerGate g = new SteerGate(CFG, 100);
		V3 cheat = new V3(1, 0, 0); // 90 degrees from the server's look
		SteerGate.Result r = g.accept(100, true, cheat, LOOK);
		assertEquals(SteerGate.Verdict.OK, r.verdict());
		assertTrue(r.clamped());
		assertEquals(LOOK, r.dir());
		V3 near = SteerMath.turnToward(LOOK, cheat, Math.toRadians(10));
		SteerGate.Result r2 = g.accept(102, true, near, LOOK);
		assertFalse(r2.clamped());
		assertEquals(near, r2.dir());
	}

	// ------------------------------------------------------------------ config

	@Test
	void configRoundTripsAndClampsBadValues() {
		FlightConfig d = FlightConfig.defaults();
		assertEquals(d, FlightConfig.fromJson(d.toJson()));
		JsonObject bad = new JsonObject();
		bad.addProperty("hitDamage", -5);
		bad.addProperty("turnDegPerSec", 1.0E9);
		bad.addProperty("hitCooldownTicks", "soon");
		bad.addProperty("enabled", false);
		FlightConfig c = FlightConfig.fromJson(bad);
		assertEquals(0.0, c.hitDamage());
		assertEquals(1080.0, c.turnDegPerSec());
		assertEquals(d.hitCooldownTicks(), c.hitCooldownTicks());
		assertFalse(c.enabled());
		JsonObject root = new JsonObject();
		assertEquals(d, FlightConfig.fromRoot(root));
		d.writeInto(root);
		assertTrue(root.has(FlightConfig.SECTION));
	}

	@Test
	void inFlightHitIsWeakerThanTheLandingHit() {
		// balance guard: the landing burst of mode_attack deals 2.0 per phase; a flight hit must stay below that
		assertTrue(FlightConfig.defaults().hitDamage() < 2.0);
	}
}
