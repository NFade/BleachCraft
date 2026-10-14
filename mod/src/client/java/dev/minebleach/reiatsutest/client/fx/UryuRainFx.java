package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.net.EffectEventS2C;

/**
 * Licht Regen (effect id 76, URYU_VFX_STORYBOARD 2.13; U-FX 9 fills it). Today a faint outline of the 9 x 9 square on the ground for the
 * whole rain; the arrows themselves are drawn by {@link UryuArrowRenderer}. Called by {@link UryuFx}.
 */
final class UryuRainFx {
	private UryuRainFx() {
	}

	/**
	 * Effect 78 (S-U4) with params[3] = 76: the key was pressed, the rain falls {@code params[4]} seconds later on (or near) the pre-aim point
	 * {@code params[0..2]}. Nothing drawn yet (U-FX 9: the flake flechettes gather above the caster).
	 */
	static void castStart(EffectEventS2C e) {
		float[] p = e.params();
		ReiatsuTest.LOGGER.info("[fx] uryu cast start (licht regen) caster={} preaim=({}, {}, {}) release in {} s", e.casterId(), p[0], p[1], p[2], p[4]);
	}

	/** params = {aim x, y, z, half width, seconds}. */
	static void rainSquare(EffectEventS2C e) {
		float[] pr = e.params();
		if (pr.length < 5) {
			return;
		}
		double cx = pr[0];
		double cy = pr[1] + 0.08;
		double cz = pr[2];
		double hw = pr[3];
		double life = pr[4] + 0.4;
		double side = hw * 2.0;
		double[][] edge = {{cx - hw, cz - hw, 1, 0}, {cx + hw, cz - hw, 0, 1}, {cx + hw, cz + hw, -1, 0}, {cx - hw, cz + hw, 0, -1}};
		for (double[] ed : edge) {
			FxShapes.Strip s = FxShapes.strip(ed[0], cy, ed[1], ed[2], 0, ed[3], 0).length(a -> side).width(a -> 0.14);
			s.color("#A9D8F5").life(life).intensity(a -> 0.35 * Math.min(1.0, a / 0.15) * Math.max(0.0, Math.min(1.0, (life - a) / 0.4)));
		}
		FxSound.play(null, "block.amethyst_block.resonate", cx, cy, cz, 1.2, 0.7);
	}
}
