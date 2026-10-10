package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * B4 step 5: the server logic of the shunpo (distance, wall stop, safe landing, cooldown, cost, allowed states). The world is a
 * voxel grid behind {@link ShunpoPath.Probe}; the real server uses block collisions with the same path code.
 */
class ShunpoTest {
	private static final CharacterId R = CharacterId.RUKIA;
	private static final CharacterId B = CharacterId.BYAKUYA;
	private static final ShunpoSpec SP = BalanceConfig.defaults().shunpo();
	/** Player box: 0.6 wide, 1.8 tall (vanilla standing). */
	private static final double HALF = 0.3;
	private static final double HEIGHT = 1.8;

	/** A grid of solid unit cubes; everything else is air. */
	private static final class Grid implements ShunpoPath.Probe {
		final Set<Long> solid = new HashSet<>();
		final Set<Long> lava = new HashSet<>();

		static long key(int x, int y, int z) {
			return (((long) x & 0x3FFFFFFL) << 38) | (((long) y & 0xFFFL) << 26) | ((long) z & 0x3FFFFFFL);
		}

		Grid fill(int x0, int y0, int z0, int x1, int y1, int z1) {
			for (int x = x0; x <= x1; x++) {
				for (int y = y0; y <= y1; y++) {
					for (int z = z0; z <= z1; z++) {
						solid.add(key(x, y, z));
					}
				}
			}
			return this;
		}

		/** A flat floor at y = -1 (top face at y = 0) over a wide area. */
		static Grid floor() {
			return new Grid().fill(-40, -1, -40, 40, -1, 40);
		}

		@Override
		public boolean fits(double x, double y, double z) {
			for (int bx = (int) Math.floor(x - HALF); bx <= (int) Math.floor(x + HALF - 1e-9); bx++) {
				for (int by = (int) Math.floor(y); by <= (int) Math.floor(y + HEIGHT - 1e-9); by++) {
					for (int bz = (int) Math.floor(z - HALF); bz <= (int) Math.floor(z + HALF - 1e-9); bz++) {
						if (solid.contains(key(bx, by, bz))) {
							return false;
						}
					}
				}
			}
			return true;
		}

		@Override
		public boolean safeLanding(double x, double y, double z, int maxDrop) {
			int bx = (int) Math.floor(x);
			int bz = (int) Math.floor(z);
			for (int d = 0; d <= maxDrop; d++) {
				int by = (int) Math.floor(y - 0.1 - d);
				if (lava.contains(key(bx, by, bz))) {
					return false;
				}
				if (solid.contains(key(bx, by, bz))) {
					return true;
				}
			}
			return false;
		}
	}

	private static final double[] EAST = {1, 0, 0};

	private static TransitionResult shunpo(Fx f, CharacterId held, Grid g, double[] dir, boolean ground) {
		return f.sm.shunpo(++f.seq, held, g, 0.5, 0.0, 0.5, dir, ground);
	}

	private static StateEvent.ShunpoMove move(TransitionResult r) {
		return Fx.events(r.events(), StateEvent.ShunpoMove.class).get(0);
	}

	// ------------------------------------------------------------------ distance and the path

	@Test
	void openGroundGivesTheFullNineBlocks() {
		Fx f = new Fx().toShikai(R);
		TransitionResult r = shunpo(f, R, Grid.floor(), EAST, true);
		assertTrue(r.ok(), String.valueOf(r.reason()));
		StateEvent.ShunpoMove m = move(r);
		assertEquals(9.0, m.distance(), 1e-6);
		assertEquals(9.5, m.toX(), 1e-6);
		assertEquals(0.0, m.toY(), 1e-6);
		assertFalse(m.stoppedByWall());
		assertTrue(SP.distance() >= 8.0 && SP.distance() <= 10.0, "spec: 8 to 10 blocks");
	}

	@Test
	void aWallStopsTheDashBeforeIt() {
		Fx f = new Fx().toShikai(R);
		Grid g = Grid.floor().fill(5, 0, -3, 5, 3, 3); // a wall at x in [5, 6)
		TransitionResult r = shunpo(f, R, g, EAST, true);
		assertTrue(r.ok());
		StateEvent.ShunpoMove m = move(r);
		assertTrue(m.stoppedByWall());
		assertTrue(m.toX() + HALF <= 5.0 + 1e-9, "the box must not reach into the wall: x=" + m.toX());
		assertTrue(m.toX() + HALF > 5.0 - SP.step() - 1e-9, "and stops within one step of it: x=" + m.toX());
		assertTrue(g.fits(m.toX(), m.toY(), m.toZ()));
	}

	@Test
	void aThinWallCannotBeSkipped() {
		Fx f = new Fx().toShikai(R);
		Grid g = Grid.floor().fill(3, 0, -3, 3, 3, 3); // one block thick, open space behind
		StateEvent.ShunpoMove m = move(shunpo(f, R, g, EAST, true));
		assertTrue(m.stoppedByWall());
		assertTrue(m.toX() < 3.0, "stopped in front of the wall, not behind it: x=" + m.toX());
	}

	@Test
	void aWallRightInFrontRefusesWithoutCostOrCooldown() {
		Fx f = new Fx().toShikai(R);
		Grid g = Grid.floor().fill(1, 0, -3, 1, 3, 3);
		int before = f.sm.reiatsu().value();
		TransitionResult r = shunpo(f, R, g, EAST, true);
		assertEquals(RejectReason.NO_ROOM, r.reason());
		assertEquals(ResultCode.BLOCKED, r.code());
		assertEquals(before, f.sm.reiatsu().value());
		assertEquals(0, f.sm.shunpoCooldownRemaining());
		assertTrue(r.events().isEmpty());
		// the next try right away works elsewhere (no cooldown was started)
		assertTrue(shunpo(f, R, Grid.floor(), EAST, true).ok());
	}

	@Test
	void aCeilingStopsAnUpwardDash() {
		Fx f = new Fx().toShikai(R);
		Grid g = Grid.floor().fill(-5, 3, -5, 15, 3, 5); // ceiling whose underside is at y = 3
		double[] up = {0.7071067811865476, 0.7071067811865476, 0};
		TransitionResult r = f.sm.shunpo(++f.seq, R, g, 0.5, 0.0, 0.5, up, false);
		assertTrue(r.ok());
		assertTrue(move(r).toY() + HEIGHT <= 3.0 + 1e-9);
	}

	@Test
	void lookingUpFromTheGroundLandsOnTheFloorAtAShorterDistance() {
		Fx f = new Fx().toShikai(R);
		Grid g = Grid.floor();
		double[] up = ShunpoPath.direction(270, -30, true); // yaw 270 = +X, 30 degrees up
		TransitionResult r = f.sm.shunpo(++f.seq, R, g, 0.5, 0.0, 0.5, up, true);
		assertTrue(r.ok(), String.valueOf(r.reason()));
		StateEvent.ShunpoMove m = move(r);
		assertEquals(0.0, m.toY(), 0.06, "snapped back down onto the floor, not left hanging in the air");
		assertTrue(m.toX() - 0.5 > 3.0 && m.toX() - 0.5 < 9.0, "x " + m.toX());
		assertTrue(g.fits(m.toX(), m.toY(), m.toZ()));
		assertTrue(g.safeLanding(m.toX(), m.toY(), m.toZ(), SP.maxDrop()));
	}

	@Test
	void aFlatDashKeepsTheFloorHeightExactly() {
		Fx f = new Fx().toShikai(R);
		StateEvent.ShunpoMove m = move(shunpo(f, R, Grid.floor(), EAST, true));
		assertEquals(0.0, m.toY(), 1e-9);
	}

	// ------------------------------------------------------------------ safe landing

	@Test
	void doesNotLandOverAPit() {
		Fx f = new Fx().toShikai(R);
		Grid g = new Grid().fill(-10, -1, -10, 5, -1, 10); // the floor ends at x < 6, then nothing
		StateEvent.ShunpoMove m = move(shunpo(f, R, g, EAST, true));
		assertTrue(m.toX() < 6.0, "landing needs ground below: x=" + m.toX());
		assertTrue(m.toX() > 4.0, "but it goes as far as the ground allows: x=" + m.toX());
		assertTrue(g.safeLanding(m.toX(), m.toY(), m.toZ(), SP.maxDrop()));
	}

	@Test
	void nothingSafeAheadMeansNoShunpo() {
		Fx f = new Fx().toShikai(R);
		Grid g = new Grid().fill(-10, -1, -10, 1, -1, 10); // a pit from 1.5 blocks on
		TransitionResult r = shunpo(f, R, g, EAST, true);
		assertEquals(RejectReason.NO_ROOM, r.reason());
	}

	@Test
	void aTwoBlockDropIsAFineLandingAndLavaIsNot() {
		Fx f = new Fx().toShikai(R);
		Grid g = new Grid().fill(-10, -1, -10, 3, -1, 10).fill(4, -3, -10, 20, -3, 10); // lower floor beyond x = 4
		StateEvent.ShunpoMove m = move(shunpo(f, R, g, EAST, true));
		assertEquals(9.5, m.toX(), 1e-6, "ground 2 blocks lower is within maxDrop: the full 9 blocks along x");
		assertEquals(-2.0, m.toY(), 0.06, "and the player is put down on that lower floor");

		Fx h = new Fx().toShikai(R);
		Grid lava = Grid.floor();
		for (int x = 4; x <= 20; x++) {
			for (int z = -10; z <= 10; z++) {
				lava.lava.add(Grid.key(x, -1, z));
			}
		}
		StateEvent.ShunpoMove m2 = move(shunpo(h, R, lava, EAST, true));
		assertTrue(m2.toX() < 4.0, "never lands on lava: x=" + m2.toX());
	}

	@Test
	void anAirborneStartMayLandInTheAir() {
		Fx f = new Fx().toShikai(R);
		Grid g = new Grid(); // nothing at all
		TransitionResult r = f.sm.shunpo(++f.seq, R, g, 0.5, 20.0, 0.5, EAST, false);
		assertTrue(r.ok());
		assertEquals(9.0, move(r).distance(), 1e-6);
	}

	// ------------------------------------------------------------------ direction

	@Test
	void directionFromYawAndPitch() {
		double[] south = ShunpoPath.direction(0, 0, true); // yaw 0 = +Z
		assertEquals(0.0, south[0], 1e-9);
		assertEquals(1.0, south[2], 1e-9);
		double[] west = ShunpoPath.direction(90, 0, true); // yaw 90 = -X
		assertEquals(-1.0, west[0], 1e-9);
		double[] up = ShunpoPath.direction(0, -45, true);
		assertEquals(Math.sqrt(0.5), up[1], 1e-9, "looking up keeps its vertical part");
		double[] downOnGround = ShunpoPath.direction(0, 45, true);
		assertEquals(0.0, downOnGround[1], 1e-9, "on the ground a downward look is flattened");
		assertEquals(1.0, downOnGround[2], 1e-9);
		double[] downInAir = ShunpoPath.direction(0, 45, false);
		assertTrue(downInAir[1] < -0.7, "in the air a downward look dives");
		double[] straightDown = ShunpoPath.direction(90, 90, true);
		assertEquals(-1.0, straightDown[0], 1e-9, "straight down falls back to the horizontal facing");
		double len = 0;
		for (double c : ShunpoPath.direction(33, -17, false)) {
			len += c * c;
		}
		assertEquals(1.0, len, 1e-9);
	}

	// ------------------------------------------------------------------ cooldown, cost, states

	@Test
	void cooldownIsBetweenTwoAndThreeSecondsAndExactToTheTick() {
		assertTrue(SP.cooldownTicks() >= 40 && SP.cooldownTicks() <= 60);
		Fx f = new Fx().toShikai(R);
		assertTrue(shunpo(f, R, Grid.floor(), EAST, true).ok());
		assertEquals(SP.cooldownTicks(), f.sm.shunpoCooldownRemaining());
		f.adv(SP.cooldownTicks() - 1);
		assertEquals(RejectReason.ON_COOLDOWN, shunpo(f, R, Grid.floor(), EAST, true).reason());
		f.adv(1);
		assertTrue(shunpo(f, R, Grid.floor(), EAST, true).ok());
	}

	@Test
	void smallReiatsuCostAndTheNeverToZeroRule() {
		assertTrue(SP.costTenths() > 0 && SP.costTenths() <= 100, "a small cost");
		Fx f = new Fx().toShikai(B);
		int before = f.sm.reiatsu().value();
		TransitionResult r = shunpo(f, B, Grid.floor(), EAST, true);
		assertTrue(r.ok());
		assertEquals(before - SP.costTenths(), f.sm.reiatsu().value());
		assertEquals(SP.costTenths(), Fx.events(r.events(), StateEvent.ReiatsuSpent.class).get(0).tenths());

		Fx poor = new Fx().toShikai(B);
		poor.sm.devSetReiatsu(SP.costTenths()); // exactly the cost: would leave 0
		assertEquals(RejectReason.NOT_ENOUGH_REIATSU, shunpo(poor, B, Grid.floor(), EAST, true).reason());
		assertEquals(0, poor.sm.shunpoCooldownRemaining());
		poor.sm.devSetReiatsu(SP.costTenths() + 1);
		assertTrue(shunpo(poor, B, Grid.floor(), EAST, true).ok());
		assertEquals(1, poor.sm.reiatsu().value());
	}

	@Test
	void onlyShikaiAndBankaiAllowShunpo() {
		Fx sealed = new Fx();
		assertEquals(RejectReason.NOT_IN_STATE, shunpo(sealed, R, Grid.floor(), EAST, true).reason());
		Fx base = new Fx().toBase(R);
		TransitionResult denied = shunpo(base, R, Grid.floor(), EAST, true);
		assertEquals(RejectReason.NOT_IN_STATE, denied.reason());
		assertEquals(ResultCode.DENIED_STATE, denied.code());
		assertEquals(0, base.sm.shunpoCooldownRemaining());

		for (CharacterId c : new CharacterId[] {R, B}) {
			Fx shikai = new Fx().toShikai(c);
			assertTrue(shunpo(shikai, c, Grid.floor(), EAST, true).ok(), c + " in shikai");
			Fx bankai = new Fx().toShikai(c).toBankai(c);
			bankai.adv(11);
			assertTrue(shunpo(bankai, c, Grid.floor(), EAST, true).ok(), c + " in bankai");
			assertEquals(ZanpakutoState.BANKAI, bankai.sm.state(), "shunpo does not change the state");
		}
	}

	@Test
	void itemAndHandChecksAndTheShared() {
		Fx f = new Fx().toShikai(R);
		assertEquals(RejectReason.WRONG_ITEM, shunpo(f, CharacterId.NONE, Grid.floor(), EAST, true).reason());
		assertEquals(RejectReason.WRONG_ITEM, shunpo(f, B, Grid.floor(), EAST, true).reason());
		f.sm.onHandChanged(CharacterId.NONE);
		assertEquals(RejectReason.WRONG_ITEM, shunpo(f, R, Grid.floor(), EAST, true).reason(), "during the hand grace");
	}

	@Test
	void transitionLockAfterReleaseAndDeadOrSpectator() {
		Fx f = new Fx().toBase(R);
		assertTrue(f.tr(ZanpakutoState.SHIKAI, R).ok());
		assertEquals(RejectReason.TRANSITION_LOCK, shunpo(f, R, Grid.floor(), EAST, true).reason());
		f.adv(10);
		assertTrue(shunpo(f, R, Grid.floor(), EAST, true).ok());
		f.sm.setSpectator(true);
		f.adv(60);
		assertEquals(RejectReason.DEAD_OR_SPECTATOR, shunpo(f, R, Grid.floor(), EAST, true).reason());
	}

	@Test
	void shunpoIsActivityForTheShikaiIdleTimerAndDoesNotNeedAGlobalCooldownSlot() {
		Fx f = new Fx().toShikai(R);
		f.adv(2000);
		assertTrue(shunpo(f, R, Grid.floor(), EAST, true).ok());
		f.adv(2399);
		assertEquals(ZanpakutoState.SHIKAI, f.sm.state());
		// an ability right after a shunpo is not held back by the shunpo
		Fx g = new Fx().toShikai(R);
		assertTrue(g.cast(AbilityId.SHIRAFUNE, R).ok());
		g.adv(12);
		assertTrue(shunpo(g, R, Grid.floor(), EAST, true).ok());
		assertTrue(g.cast(AbilityId.HAKUREN, R).ok());
	}

	@Test
	void eventCarriesTheMoveAndNoMachineSideEffectId() {
		Fx f = new Fx().toShikai(R);
		TransitionResult r = shunpo(f, R, Grid.floor(), EAST, true);
		StateEvent.ShunpoMove m = move(r);
		assertEquals(0.5, m.fromX(), 1e-9);
		assertEquals(9.5, m.toX(), 1e-9);
		assertEquals(0.5, m.toZ(), 1e-9);
		assertFalse(Fx.has(r.events(), StateEvent.BroadcastEffect.class), "the glue broadcasts effect 40 with the real positions");
		assertEquals(40, EffectIds.SHUNPO);
	}

	@Test
	void wireMappingOfTheNewResult() {
		assertEquals(ResultCode.BLOCKED, RejectReason.NO_ROOM.toWire());
		assertEquals(7, ResultCode.BLOCKED.code());
		assertEquals(ResultCode.BLOCKED, ResultCode.fromCode((byte) 7));
		assertNotNull(ResultCode.fromCode((byte) 99));
		assertNull(AbilityId.fromCode((byte) 99));
	}

	@Test
	void deathResetsTheShunpoCooldown() {
		Fx f = new Fx().toShikai(R);
		assertTrue(shunpo(f, R, Grid.floor(), EAST, true).ok());
		f.sm.onDeath();
		f.sm.onRespawn();
		assertEquals(0, f.sm.shunpoCooldownRemaining());
	}
}
