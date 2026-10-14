package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import org.junit.jupiter.api.Test;

/** The aim of the Rukia shikai abilities is read at the end of the cast (Hakuren: the phase where the wave leaves), not at the key press. */
class RukiaAimTest {
	private static AbilitySpec spec(AbilityId id) {
		return BalanceConfig.defaults().spec(id);
	}

	private static double[] look(double yawDeg) {
		double y = Math.toRadians(yawDeg);
		return new double[] {-Math.sin(y), Math.cos(y)};
	}

	@Test
	void shirafuneAndHakurenAimAtTheEndTsukishiroHasNothingToAim() {
		assertTrue(RukiaAim.aimsAtEnd(AbilityId.SHIRAFUNE));
		assertTrue(RukiaAim.aimsAtEnd(AbilityId.HAKUREN));
		assertFalse(RukiaAim.aimsAtEnd(AbilityId.TSUKISHIRO), "a circle around the caster has no direction");
		assertFalse(RukiaAim.aimsAtEnd(AbilityId.ICE_WALL), "the bankai wall has its own release read");
		assertEquals(-1, RukiaAim.aimPhase(spec(AbilityId.TSUKISHIRO)));
		assertEquals(-1.0, RukiaAim.aimSeconds(spec(AbilityId.TSUKISHIRO)));
	}

	@Test
	void shirafuneReadsAtItsReleasePhase() {
		AbilitySpec s = spec(AbilityId.SHIRAFUNE);
		assertEquals(s.releasePhase(), RukiaAim.aimPhase(s));
		assertEquals(0.6, RukiaAim.aimSeconds(s), 1e-9, "12 ticks, the end of the cast time");
	}

	@Test
	void hakurenReadsWhereTheWaveLeavesNotAtTheLastStep() {
		AbilitySpec s = spec(AbilityId.HAKUREN);
		assertEquals(8, s.phaseOffsets().size());
		assertEquals(0, RukiaAim.aimPhase(s), "the effect starts at phase 0; phases 1..7 are the later steps of the same wave");
		assertEquals(1.0, RukiaAim.aimSeconds(s), 1e-9, "tick 20: the end of the cast time, where the client wave begins (1.0 s)");
		assertTrue(RukiaAim.aimSeconds(s) < s.phaseOffsets().get(s.phaseOffsets().size() - 1) / 20.0);
	}

	@Test
	void aSpecWithoutPhasesHasNoAimPhase() {
		AbilitySpec none = new AbilitySpec(AbilityId.HAKUREN, 0, 20, 21, true, java.util.List.of());
		assertEquals(-1, RukiaAim.aimPhase(none));
	}

	@Test
	void theWaveDirectionIsTheLookOfTheEndNotThePress() {
		double[] press = new double[2];
		double[] end = new double[2];
		double[] pl = look(0);
		double[] el = look(90);
		RukiaAim.flat(pl[0], pl[1], 0, 1, press);
		RukiaAim.flat(el[0], el[1], press[0], press[1], end);
		assertEquals(0.0, press[0], 1e-9);
		assertEquals(1.0, press[1], 1e-9);
		assertEquals(-1.0, end[0], 1e-9, "turned to the west: the wave goes west");
		assertEquals(0.0, end[1], 1e-9);
		// a point 10 blocks west of the caster lies on the new wave axis and 10 blocks beside the old one
		double px = -10.0;
		double pz = 0.0;
		assertEquals(10.0, px * end[0] + pz * end[1], 1e-9, "along the new axis");
		assertEquals(0.0, Math.abs(px * end[1] - pz * end[0]), 1e-9, "no lateral offset from the new axis");
		assertEquals(0.0, px * press[0] + pz * press[1], 1e-9, "not along the old axis");
		assertEquals(10.0, Math.abs(px * press[1] - pz * press[0]), 1e-9, "10 blocks beside the old axis, outside the half width");
	}

	@Test
	void flatIsAUnitVectorForAnyPitchAndKeepsTheFallbackWhenLookingStraightUp() {
		double[] out = new double[2];
		for (double yaw = -180; yaw <= 180; yaw += 15) {
			for (double pitch = -85; pitch <= 85; pitch += 17) {
				double cp = Math.cos(Math.toRadians(pitch));
				double[] l = look(yaw);
				RukiaAim.flat(l[0] * cp, l[1] * cp, 0, 1, out);
				assertEquals(1.0, Math.hypot(out[0], out[1]), 1e-9, yaw + "/" + pitch);
				assertEquals(l[0], out[0], 1e-9);
				assertEquals(l[1], out[1], 1e-9);
			}
		}
		RukiaAim.flat(0, 0, 3, 4, out);
		assertEquals(0.6, out[0], 1e-9);
		assertEquals(0.8, out[1], 1e-9);
		RukiaAim.flat(0, 0, 0, 0, out);
		assertEquals(0.0, out[0]);
		assertEquals(1.0, out[1]);
	}
}
