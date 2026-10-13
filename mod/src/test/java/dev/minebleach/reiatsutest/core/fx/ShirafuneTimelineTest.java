package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import org.junit.jupiter.api.Test;

/** Shirafune client timeline against the server's release timing (r8 fx2 item 3). */
class ShirafuneTimelineTest {
	private static final AbilitySpec SPEC = BalanceConfig.defaults().spec(AbilityId.SHIRAFUNE);

	@Test
	void castTimeIsTheReleasePhaseOfTheSpec() {
		assertTrue(SPEC.aimsAtRelease());
		assertEquals(0.6, ShirafuneTimeline.castSeconds(SPEC), 1e-9, "12 ticks");
	}

	@Test
	void theEventClockStartsAtTheHit() {
		ShirafuneTimeline tl = ShirafuneTimeline.atRelease();
		assertEquals(0.0, tl.bladeAt, "the blade leaves at once (the server hits at the event)");
		assertTrue(tl.contactAt > tl.bladeAt && tl.contactAt <= 0.15, "contact lands with the damage: " + tl.contactAt);
		assertTrue(tl.contactAt <= ShirafuneTimeline.GROW, "the blade has nearly arrived when the contact lands");
		assertTrue(tl.crystalAt > tl.bladeAt && tl.crystalAt < tl.retractAt);
		assertTrue(tl.chillAt > tl.contactAt);
		assertTrue(tl.retractAt > tl.chillAt);
		assertTrue(tl.lifetime > tl.retractAt);
	}

	@Test
	void theOldPressTimelineWasTheOffsetThatWasRemoved() {
		ShirafuneTimeline old = ShirafuneTimeline.atPress();
		ShirafuneTimeline now = ShirafuneTimeline.atRelease();
		assertEquals(0.3, old.bladeAt - now.bladeAt, 1e-9, "the blade was 0.3 s late");
		assertEquals(0.6 - 0.1, old.contactAt - now.contactAt, 1e-9, "the contact was 0.5 s late (0.6 s after the event, the hit is at 0.6 s of cast)");
		// the shape of the effect itself is unchanged: blade to retract is the same 1.7 s as before
		assertEquals(old.retractAt - old.bladeAt, now.retractAt - now.bladeAt, 1e-9);
		assertEquals(old.crystalAt - old.bladeAt, now.crystalAt - now.bladeAt, 1e-9);
	}

	@Test
	void leadAndGrowTimeMoveTheWholeTable() {
		ShirafuneTimeline a = ShirafuneTimeline.atRelease(0.1, 0.4);
		assertEquals(0.1, a.bladeAt, 1e-9);
		assertEquals(0.3, a.contactAt, 1e-9, "lead + half the growth time");
		assertEquals(1.8, a.retractAt, 1e-9);
		ShirafuneTimeline b = ShirafuneTimeline.atRelease(-5, 0.0);
		assertEquals(0.0, b.bladeAt, "a negative lead is clamped");
		assertTrue(b.contactAt > 0.0, "a zero growth time still has a positive contact delay");
	}

	@Test
	void gatherRisesFromTheKeyPressToTheRelease() {
		assertEquals(0.15, ShirafuneTimeline.gather(0.0), 1e-9);
		assertEquals(1.0, ShirafuneTimeline.gather(1.0), 1e-9);
		double prev = -1;
		for (double p = 0; p <= 1.0001; p += 0.02) {
			double s = ShirafuneTimeline.gatherSmooth(p);
			assertTrue(s >= prev - 1e-12, "monotonic at " + p);
			assertTrue(s >= 0.15 - 1e-9 && s <= 1.0 + 1e-9);
			double g = ShirafuneTimeline.gather(p);
			assertTrue(g >= 0.0 && g <= 1.0, "bounded with the flicker at " + p);
			assertTrue(Math.abs(g - s) <= 0.081, "the flicker is at most 8 percent");
			prev = s;
		}
		assertEquals(ShirafuneTimeline.gather(-3), ShirafuneTimeline.gather(0.0), "outside the range it clamps");
	}

	@Test
	void moteBudgetPerTickIsBounded() {
		assertEquals(1, ShirafuneTimeline.moteCount(0.0, 4)); // 4 * (0.25 + 0.75 * 0.15) = 1.45
		assertEquals(4, ShirafuneTimeline.moteCount(1.0, 4));
		assertEquals(0, ShirafuneTimeline.moteCount(0.5, 0));
		assertEquals(0, ShirafuneTimeline.moteCount(0.5, -9));
		// over the whole cast the telegraph spawns a bounded number of motes
		int ticks = (int) Math.round(ShirafuneTimeline.castSeconds(SPEC) / 0.05);
		int total = 0;
		for (int i = 0; i < ticks; i++) {
			total += ShirafuneTimeline.moteCount(i / (double) ticks, 4);
		}
		assertTrue(total <= 4 * ticks, "never more than the peak per tick: " + total);
		assertTrue(total >= ticks, "at least one mote a tick: " + total);
	}
}
