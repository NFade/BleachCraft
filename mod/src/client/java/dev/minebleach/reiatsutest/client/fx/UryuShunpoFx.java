package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.UryuShunpoMath;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.math.Vec3d;

/**
 * The Quincy shunpo look (storyboard 2.15, Hirenkyaku): a Reishi platform ellipse under the feet at the start (with a rim of specks), flechette trail
 * strips and 20 aligned dashes along the path, a smaller mid disc on long paths and a platform with a ring and 6 flakes at the arrival. The afterimages
 * (3, cyan, alpha 0.35, 0.05 s apart) and the rush lines live in {@link ShunpoFx}. The old look is {@link UryuFx#shunpoReishi} ({@code uryu.shunpoOld}).
 */
final class UryuShunpoFx {
	private static final class Disc {
		double x;
		double y;
		double z;
		double rot;
		double scale;
		double born;
		float[] rgb;
		double peak;
	}

	private static final List<Disc> DISCS = new ArrayList<>();
	private static final Random RNG = new Random(0x4B1A);
	private static boolean inited;

	/** Harness statistics. */
	static int plays;
	static int dashes;
	static int discsSpawned;
	static int specks;
	static int flakes;

	private UryuShunpoFx() {
	}

	static void init() {
		if (inited) {
			return;
		}
		inited = true;
		FxGlowBatch.addSource(UryuShunpoFx::emit);
	}

	static void clear() {
		DISCS.clear();
	}

	private static boolean calm() {
		return FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
	}

	static void play(Vec3d start, Vec3d delta) {
		init();
		plays++;
		boolean calm = calm();
		double len = delta.length();
		double rot = len > 0.05 ? Math.atan2(delta.z, delta.x) : 0.0;
		double now = FxClock.now;
		disc(start.x, start.y, start.z, rot, 1.0, now);
		rim(start, rot, calm);
		if (len > 0.5) {
			Vec3d u = delta.multiply(1.0 / len);
			trail(start, u, len, calm);
			if (UryuShunpoMath.hasMidDisc(len)) {
				Vec3d mid = start.add(delta.multiply(0.5));
				FxDelayed.after(UryuShunpoMath.MID_DELAY, () -> disc(mid.x, mid.y, mid.z, rot, UryuShunpoMath.MID_SCALE, FxClock.now));
			}
		}
		Vec3d end = start.add(delta);
		FxDelayed.after(UryuShunpoMath.ARRIVE_DELAY, () -> arrive(end, rot, calm));
		FxSound.play(null, "entity.phantom.flap", start.x, start.y + 1.0, start.z, 1.9, 0.3);
		FxSound.play(null, "block.amethyst_block.chime", start.x, start.y + 1.0, start.z, 1.8, 0.3);
	}

	private static void disc(double x, double y, double z, double rot, double scale, double born) {
		Disc d = new Disc();
		d.x = x;
		d.y = y + 0.04;
		d.z = z;
		d.rot = rot;
		d.scale = scale;
		d.born = born;
		d.rgb = UryuPalette.FQ2;
		d.peak = 0.8;
		DISCS.add(d);
		discsSpawned++;
	}

	/** 8 specks on the rim of the start ellipse. */
	private static void rim(Vec3d at, double rot, boolean calm) {
		int n = calm ? UryuShunpoMath.RIM_SPECKS / 2 : UryuShunpoMath.RIM_SPECKS;
		double c = Math.cos(rot);
		double s = Math.sin(rot);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2.0 * i / n + RNG.nextDouble() * 0.3;
			double lx = Math.cos(a) * UryuShunpoMath.DISC_LONG * 0.5;
			double lz = Math.sin(a) * UryuShunpoMath.DISC_SHORT * 0.5;
			double px = at.x + c * lx - s * lz;
			double pz = at.z + s * lx + c * lz;
			specks++;
			FxGlowBatch.sprite(GlowSprite.SPECK).at(px, at.y + 0.1, pz).vel(0.0, 0.5 + RNG.nextDouble() * 0.6, 0.0).size(0.16).life(UryuShunpoMath.DISC_LIFE).curve(0.03, 0.7)
					.color(UryuPalette.Q1).peak(0.9).twinkle().spawn();
		}
	}

	/** Three flechette strips (Q3) with a Q0 core along the path, and the aligned dashes staggered over 0.4 s. */
	private static void trail(Vec3d start, Vec3d u, double len, boolean calm) {
		double life = UryuShunpoMath.TRAIL_TIME;
		for (int i = 0; i < 3; i++) {
			double y = start.y + 0.3 + i * 0.6;
			FxShapes.Strip s = FxShapes.strip(start.x, y, start.z, u.x, u.y, u.z, 0).length(a -> len).width(a -> 0.34 * Math.max(0.0, 1.0 - a / life));
			s.color(UryuPalette.Q3).life(life).intensity(a -> 0.9 * Math.max(0.0, 1.0 - a / life));
		}
		FxShapes.Strip core = FxShapes.strip(start.x, start.y + 0.9, start.z, u.x, u.y, u.z, 0).length(a -> len).width(a -> 0.12);
		core.color(UryuPalette.Q0).life(life).intensity(a -> Math.max(0.0, 1.0 - a / life));
		int n = UryuShunpoMath.dashCount(FxConfig.uryuMaxFlechettes, calm);
		double speed = 30.0;
		for (int i = 0; i < n; i++) {
			double f = UryuShunpoMath.dashFraction(i, n, RNG.nextDouble());
			double px = start.x + u.x * len * f + (RNG.nextDouble() - 0.5) * 0.5;
			double py = start.y + u.y * len * f + 0.2 + RNG.nextDouble() * 1.5;
			double pz = start.z + u.z * len * f + (RNG.nextDouble() - 0.5) * 0.5;
			dashes++;
			FxGlowBatch.sprite(GlowSprite.DASH).at(px, py, pz).vel(u.x * speed, u.y * speed, u.z * speed).streak().streakTime(0.05).size(0.35)
					.delay(UryuShunpoMath.dashDelay(f)).life(UryuShunpoMath.DASH_LIFE).curve(0.01, 0.6).color(i % 2 == 0 ? UryuPalette.Q1 : UryuPalette.Q2).peak(0.9).spawn();
		}
	}

	/** The arrival: a platform, a thin ring 0 to 1.6 in 0.25 s, 6 flakes and the second chime. */
	private static void arrive(Vec3d end, double rot, boolean calm) {
		disc(end.x, end.y, end.z, rot, 1.0, FxClock.now);
		double d = UryuShunpoMath.RING_R * 2.0;
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(end.x, end.y + 0.06, end.z).ground().size(0.0, d).life(UryuShunpoMath.RING_TIME).curve(0.02, 0.7).color(UryuPalette.Q1).peak(0.9).spawn();
		int n = calm ? UryuShunpoMath.ARRIVE_FLAKES / 2 : UryuShunpoMath.ARRIVE_FLAKES;
		for (int i = 0; i < n; i++) {
			double a = RNG.nextDouble() * Math.PI * 2.0;
			Vec3d v = new Vec3d(Math.cos(a) * 1.5, 1.0 + RNG.nextDouble() * 1.5, Math.sin(a) * 1.5);
			Vec3d at = end.add(0.0, 0.3 + RNG.nextDouble() * 0.6, 0.0);
			if (UryuFlakes.spawn(at, v, 20, 0.3, i % 2 == 0 ? UryuPalette.Q2 : UryuPalette.Q1, (RNG.nextDouble() - 0.5) * 0.6, i, 1.0)) {
				flakes++;
			}
		}
		FxSound.play(null, "block.amethyst_block.chime", end.x, end.y + 1.0, end.z, 2.0, 0.3);
	}

	private static void emit(FxGlowBatch.Emitter e) {
		if (DISCS.isEmpty()) {
			return;
		}
		double now = FxClock.now;
		double emi = Math.max(0.0, FxConfig.emissiveMultiplier);
		DISCS.removeIf(d -> now - d.born >= UryuShunpoMath.DISC_LIFE);
		for (Disc d : DISCS) {
			double age = now - d.born;
			double lv = UryuShunpoMath.discLevel(age) * emi;
			if (lv < 0.004) {
				continue;
			}
			double lx = UryuShunpoMath.DISC_LONG * d.scale;
			double sz = UryuShunpoMath.DISC_SHORT * d.scale;
			e.ground(GlowSprite.PLATFORM_DISC, d.x, d.y, d.z, lx, sz, d.rot, d.rgb[0], d.rgb[1], d.rgb[2], d.peak * lv);
			e.ground(GlowSprite.GLOW_CORE, d.x, d.y + 0.01, d.z, lx * 0.45, sz * 0.45, d.rot, UryuPalette.FQ0[0], UryuPalette.FQ0[1], UryuPalette.FQ0[2], 0.7 * lv);
		}
	}
}
