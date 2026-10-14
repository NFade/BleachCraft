package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.swarm.V3;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Seele Schneider combo (B, SHIKAI) and the wing barrage (Z, BANKAI): timing, geometry, target assignment. */
class SeeleWingTest {
	// ------------------------------------------------------------------ Seele Schneider

	@Test
	void theNumbersOfTheCombo() {
		assertEquals(1.5, UryuBalance.SEELE_LUNGE, 0.0);
		assertEquals(3, UryuBalance.SEELE_SLASHES);
		assertEquals(5, UryuBalance.SEELE_SLASH_GAP, "0.25 s");
		assertEquals(3.5, UryuBalance.SEELE_REACH, 0.0);
		assertEquals(100.0, UryuBalance.SEELE_CONE_DEG, 0.0);
		assertEquals(2.5f, UryuBalance.SEELE_DAMAGE, 0f);
		assertEquals(1.0, UryuBalance.SEELE_KNOCKBACK, 0.0);
		assertEquals(7.5f, UryuBalance.SEELE_DAMAGE * UryuBalance.SEELE_SLASHES, 1e-6, "7.5 HP for a full combo, well under the peak burst");
	}

	@Test
	void theLungeIsThreeTicksOfVelocityThatCoverAboutOneAndAHalfBlocks() {
		for (int k = 0; k < 3; k++) {
			assertTrue(SeeleCombo.lungeAt(k));
		}
		assertFalse(SeeleCombo.lungeAt(3));
		assertFalse(SeeleCombo.lungeAt(-1));
		// vanilla ground: the velocity is multiplied by 0.546 per tick (0.6 slipperiness * 0.91) after each move
		double v = UryuBalance.SEELE_LUNGE_SPEED;
		double dist = 0;
		for (int k = 0; k < 40; k++) {
			if (SeeleCombo.lungeAt(k)) {
				v = UryuBalance.SEELE_LUNGE_SPEED;
			}
			dist += v;
			v *= 0.546;
		}
		assertEquals(UryuBalance.SEELE_LUNGE, dist, 0.15, "the whole lunge incl. the slide");
		assertTrue(UryuBalance.SEELE_LUNGE_SPEED < 0.5, "far under what the server accepts as a normal move");
	}

	@Test
	void threeSlashesAtTicks3_8_13AndOnlyTheLastKnocksBack() {
		assertEquals(3, SeeleCombo.slashTick(0));
		assertEquals(8, SeeleCombo.slashTick(1));
		assertEquals(13, SeeleCombo.slashTick(2));
		assertEquals(-1, SeeleCombo.slashAt(4));
		assertEquals(1, SeeleCombo.slashAt(8));
		assertFalse(SeeleCombo.knocksBack(0));
		assertFalse(SeeleCombo.knocksBack(1));
		assertTrue(SeeleCombo.knocksBack(2));
		assertFalse(SeeleCombo.over(13));
		assertTrue(SeeleCombo.over(14));
	}

	@Test
	void theLungeDirectionIsHorizontal() {
		V3 d = SeeleCombo.lungeDirection(new V3(0, -0.8, 0.6), new V3(0, 0, 1));
		assertEquals(0.0, d.y(), 0.0);
		assertEquals(1.0, d.length(), 1e-9);
		V3 straightDown = SeeleCombo.lungeDirection(new V3(0, -1, 0), new V3(1, 0, 0));
		assertEquals(1.0, straightDown.x(), 1e-9, "a look straight down uses the fallback (the body yaw)");
	}

	@Test
	void theConeIs35BlocksLongAndHundredDegreesWide() {
		V3 o = V3.ZERO;
		V3 look = new V3(0, 0, 1);
		assertTrue(SeeleCombo.inCone(o, look, new V3(0, 0, 3.4)));
		assertFalse(SeeleCombo.inCone(o, look, new V3(0, 0, 3.6)), "beyond the reach");
		double in = Math.toRadians(49);
		double out = Math.toRadians(51);
		assertTrue(SeeleCombo.inCone(o, look, new V3(Math.sin(in) * 2.5, 0, Math.cos(in) * 2.5)), "49 degrees off the look is inside the 100 degree cone");
		assertFalse(SeeleCombo.inCone(o, look, new V3(Math.sin(out) * 2.5, 0, Math.cos(out) * 2.5)));
		assertFalse(SeeleCombo.inCone(o, look, new V3(0, 0, -1)), "behind");
		assertTrue(SeeleCombo.inCone(o, look, o), "on top of the caster");
	}

	@Test
	void theSeeleComboDoesNotConsumeFocus() {
		// the glue never calls consume() for it; the grace keeps the stacks through the lunge
		Focus f = new Focus();
		for (int i = 0; i < 42; i++) {
			f.tick(true, false, 0, false);
		}
		assertTrue(f.full());
		f.grace(UryuBalance.SEELE_FOCUS_GRACE);
		for (int i = 0; i < UryuBalance.SEELE_FOCUS_GRACE; i++) {
			f.tick(true, false, 0.4, false);
		}
		assertEquals(3, f.stacks());
	}

	// ------------------------------------------------------------------ wing barrage

	@Test
	void theNumbersOfTheBarrage() {
		assertEquals(120, UryuBalance.WING_TICKS, "6 s");
		assertEquals(30.0, UryuBalance.WING_RANGE, 0.0);
		assertEquals(4, UryuBalance.WING_SLOTS);
		assertEquals(8, UryuBalance.WING_CYCLE, "0.4 s");
		assertEquals(2.0f, UryuBalance.WING_DAMAGE, 0f);
		assertTrue(WingBarrage.active(0));
		assertTrue(WingBarrage.active(119));
		assertFalse(WingBarrage.active(120));
		assertTrue(WingBarrage.isCycleStart(0));
		assertTrue(WingBarrage.isCycleStart(112));
		assertFalse(WingBarrage.isCycleStart(120));
		assertFalse(WingBarrage.isCycleStart(5));
		// 15 cycles, 4 targets, 2 HP: at most 120 HP in 6 s over all targets, 30 HP on one target if all four slots were on it (they are not: distinct)
		assertEquals(15, UryuBalance.WING_TICKS / UryuBalance.WING_CYCLE);
	}

	@Test
	void everyTargetIsDistinctAndTheNearestGetTheFirstSlots() {
		List<WingBarrage.Target> near = List.of(new WingBarrage.Target(5, new V3(3, 0, 0)), new WingBarrage.Target(2, new V3(6, 0, 0)),
				new WingBarrage.Target(5, new V3(3, 0, 0)), new WingBarrage.Target(9, new V3(8, 0, 0)));
		List<WingBarrage.Shot> shots = WingBarrage.cycle(16, near);
		assertEquals(3, shots.size(), "three different targets, one arrow each, a duplicate is skipped");
		assertEquals(5, shots.get(0).target().id());
		assertEquals(2, shots.get(1).target().id());
		assertEquals(9, shots.get(2).target().id());
		assertEquals(0, shots.get(0).slot());
		assertEquals(16, shots.get(0).fireTick());
		assertEquals(18, shots.get(1).fireTick());
		assertEquals(20, shots.get(2).fireTick(), "the slots are staggered by two ticks");
	}

	@Test
	void atMostFourTargetsAreServedAndNoTargetMeansNoShot() {
		List<WingBarrage.Target> many = new java.util.ArrayList<>();
		for (int i = 0; i < 9; i++) {
			many.add(new WingBarrage.Target(i, new V3(i + 1, 0, 0)));
		}
		assertEquals(4, WingBarrage.cycle(0, many).size());
		assertEquals(0, WingBarrage.cycle(0, List.of()).size());
		List<WingBarrage.Shot> last = WingBarrage.cycle(112, many);
		assertTrue(last.get(3).fireTick() < UryuBalance.WING_TICKS + UryuBalance.WING_CYCLE);
	}

	@Test
	void theWingTipsMoveWithTheBodyYaw() {
		V3 feet = new V3(10, 64, 20);
		// facing +Z (yaw 0): the right hand side is -X, the wings are behind (-Z)
		V3 upperRight = WingBarrage.tip(0, 0.0, feet);
		assertEquals(10 - 1.7, upperRight.x(), 1e-9);
		assertEquals(64 + 2.35, upperRight.y(), 1e-9);
		assertEquals(20 - 0.45, upperRight.z(), 1e-9);
		V3 upperLeft = WingBarrage.tip(1, 0.0, feet);
		assertEquals(10 + 1.7, upperLeft.x(), 1e-9);
		// turned half way round (yaw 180): everything mirrors about the feet
		V3 turned = WingBarrage.tip(0, 180.0, feet);
		assertEquals(10 + 1.7, turned.x(), 1e-9);
		assertEquals(20 + 0.45, turned.z(), 1e-9);
		// four slots, all above the feet, none at the same place
		for (int a = 0; a < 4; a++) {
			assertTrue(WingBarrage.tip(a, 33.0, feet).y() > feet.y() + 1.0);
			for (int b = a + 1; b < 4; b++) {
				assertTrue(WingBarrage.tip(a, 33.0, feet).distanceTo(WingBarrage.tip(b, 33.0, feet)) > 0.5);
			}
		}
	}
}
