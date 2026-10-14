package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import net.minecraft.util.math.Vec3d;

/**
 * Sternenstaub (effect id 77, URYU_VFX_STORYBOARD 2.14; U-FX 10 fills it). Today the LINE packet draws a glow strip and a white core along
 * the 40 block line. With S-U1 the server sends the LINE packet (and effect 77) at the RELEASE, 0.8 s after the key press; effect 78
 * (params[3] = 77) marks the key press so the flechette gather can start. Called by {@link UryuFx}.
 */
final class UryuStarFx {
	private UryuStarFx() {
	}

	/** Effect 78 (S-U4) with params[3] = 77: the key was pressed, the release comes {@code params[4]} seconds later. Nothing drawn yet. */
	static void castStart(EffectEventS2C e) {
		ReiatsuTest.LOGGER.info("[fx] uryu cast start (sternenstaub) caster={} release in {} s", e.casterId(), e.params()[4]);
	}

	static void line(Vec3d a, Vec3d b, double halfWidth) {
		Vec3d d = b.subtract(a);
		double len = d.length();
		if (len < 0.5) {
			return;
		}
		Vec3d u = d.multiply(1.0 / len);
		double life = 0.45;
		FxShapes.Strip glow = FxShapes.strip(a.x, a.y, a.z, u.x, u.y, u.z, 0).length(x -> len).width(x -> halfWidth * 2.0 * Math.max(0.0, 1.0 - x / life));
		glow.color("#7FD0FF").life(life).intensity(x -> 0.7 * Math.max(0.0, 1.0 - x / life));
		FxShapes.Strip core = FxShapes.strip(a.x, a.y, a.z, u.x, u.y, u.z, 0).length(x -> len).width(x -> 0.25);
		core.color("#FFFFFF").life(life).intensity(x -> Math.max(0.0, 1.0 - x / life));
	}
}
