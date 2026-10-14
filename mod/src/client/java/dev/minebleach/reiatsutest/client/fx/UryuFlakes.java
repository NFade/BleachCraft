package dev.minebleach.reiatsutest.client.fx;

import java.util.ArrayDeque;
import net.minecraft.util.math.Vec3d;

/**
 * The flake flechettes P:FL of the Uryu effects (URYU_VFX_STORYBOARD 3.3 of the particle list: the leaf with the comb of barbs) with their own
 * live cap {@code uryu.maxFlechettes} (160 / 80 / 40 by tier): {@link FxParticles} only knows the global particle cap, so the spawns of the bow,
 * the arrows and the Focus go through here and are refused when this many flakes of this family are alive. Lives are counted on the FX clock,
 * so a frozen clock (the harness) keeps them. Render thread only.
 */
final class UryuFlakes {
	/** Expiry times (FX clock seconds) of the flakes spawned through {@link #spawn}, oldest first. */
	private static final ArrayDeque<Double> LIVE = new ArrayDeque<>();
	/** Statistics for the harness. */
	static int spawned;
	static int refused;

	private UryuFlakes() {
	}

	/** Flakes of this family alive now. */
	static int live() {
		prune();
		return LIVE.size();
	}

	private static void prune() {
		double now = FxClock.now;
		while (!LIVE.isEmpty() && LIVE.peekFirst() <= now) {
			LIVE.pollFirst();
		}
	}

	static void clear() {
		LIVE.clear();
	}

	/**
	 * Spawns one flake (blocks per second, life in ticks, size in blocks, colour hex, spin in radians per tick, {@code seed} picks one of the
	 * three leaf variants) unless the cap or the global particle budget refuses it. {@code lod} is the distance factor (1, 0.5, 0.25).
	 */
	static boolean spawn(Vec3d at, Vec3d vel, int lifeTicks, double size, String colour, double spin, int seed, double lod) {
		prune();
		if (LIVE.size() >= Math.max(0, FxConfig.uryuMaxFlechettes)) {
			refused++;
			return false;
		}
		boolean ok = FxParticles.spec(FxParticles.Kind.FLECHETTE).at(at).vel(vel).life(lifeTicks).size(size).color(colour).spin(spin).seed(seed).lod(lod).spawn();
		if (ok) {
			LIVE.addLast(FxClock.now + lifeTicks / 20.0);
			spawned++;
		}
		return ok;
	}
}
