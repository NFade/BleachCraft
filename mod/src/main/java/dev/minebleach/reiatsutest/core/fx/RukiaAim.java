package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;

/**
 * "Aim at the end of the cast" for the Rukia shikai abilities, shared by the server executor and the client effects (pure, no Minecraft types).
 * The look is not taken at the key press but at the phase where the effect STARTS:
 * <ul>
 * <li>SHIRAFUNE: the release phase (its single phase), see {@link AbilitySpec#aimsAtRelease()}, the effect event itself is sent then.</li>
 * <li>HAKUREN: phase 0, the tick the wave leaves (20); the later phases are the steps of the same wave and reuse that read. The effect event
 * still goes out at the key press (the wind-up and the punctures are the cast time), so the client re-reads the owner's look at
 * {@link #aimSeconds}.</li>
 * <li>TSUKISHIRO: nothing to aim, a circle around the caster; it keeps the press position (the ring is drawn there from the first moment).</li>
 * </ul>
 */
public final class RukiaAim {
	private RukiaAim() {
	}

	/** True when the ability takes the direction it is cast along from the phase {@link #aimPhase} and not from the key press. */
	public static boolean aimsAtEnd(AbilityId id) {
		return id == AbilityId.SHIRAFUNE || id == AbilityId.HAKUREN;
	}

	/** The phase index at which the aim is read, or -1 for an ability that does not aim at the end (or has no phases). */
	public static int aimPhase(AbilitySpec spec) {
		if (!aimsAtEnd(spec.id()) || spec.phaseOffsets().isEmpty()) {
			return -1;
		}
		return spec.id() == AbilityId.SHIRAFUNE ? spec.releasePhase() : 0;
	}

	/** Seconds after the key press at which the aim is read (the tick of {@link #aimPhase} over 20), -1 when there is none. */
	public static double aimSeconds(AbilitySpec spec) {
		int p = aimPhase(spec);
		return p < 0 ? -1.0 : spec.phaseOffsets().get(p) / 20.0;
	}

	/**
	 * Horizontal unit direction (x, z written to out[0], out[1]) of a look vector; a look that is (nearly) straight up or down keeps the
	 * fallback direction (fx, fz), which is normalised too ((0, 1) when that is zero as well).
	 */
	public static void flat(double lx, double lz, double fx, double fz, double[] out) {
		double h = Math.sqrt(lx * lx + lz * lz);
		if (h >= 1.0E-3) {
			out[0] = lx / h;
			out[1] = lz / h;
			return;
		}
		double f = Math.sqrt(fx * fx + fz * fz);
		if (f < 1.0E-6) {
			out[0] = 0.0;
			out[1] = 1.0;
		} else {
			out[0] = fx / f;
			out[1] = fz / f;
		}
	}
}
