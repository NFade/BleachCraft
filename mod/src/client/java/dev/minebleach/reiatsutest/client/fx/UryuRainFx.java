package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.UryuRainMath;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Licht Regen (BANKAI H, URYU_VFX_STORYBOARD 2.13; U-FX 9). Effect 78 with params[3] = 76 is the KEY PRESS: the square outline lights on the ground at the
 * pre-aim point (drawn from the four corners toward the middles of the edges, corner beads, a bead running along). Effect 76 is the RELEASE, 0.6 s later: the
 * sky card of stardust, the four shafts, the glitter fall and the haze start, the rain begins (the needle arrows come from the server and are drawn by
 * {@link UryuArrowRenderer}, which reports every ended RAIN arrow to {@link #landing}: a splash of rays and a star, now and then a puff and a chime).
 * Reduce Motion and the Photosensitive mode: a faint constant vignette, no running bead. Tier Low ({@link FxConfig#uryuMaxFlechettes} under 80): one
 * landing in three, no haze, puffs only near the camera. Called by {@link UryuFx}.
 */
final class UryuRainFx {
	private static final class Square {
		int caster;
		double cx;
		double y0;
		double cz;
		double litAt;
		/** Time of the release (first the key press time plus the cast seconds, then the real time of the release event). */
		double releaseAt;
		boolean released;
		double life = 3.3;
		ScreenFx.Layer vignette;
		double glitterAcc;
		double hazeAcc;
		boolean ended;
	}

	private static final List<Square> SQUARES = new ArrayList<>();
	private static final Random RNG = new Random(0x4C1E);
	private static final List<Double> RECENT = new ArrayList<>();
	private static boolean inited;
	private static int landingNo;
	private static double lastChime;
	private static double lastHit;
	private static double lastTwinkle;
	private static double lastNow;

	/** Harness: outline quads, sky card quads, landings seen, splashes drawn, puffs, glitter and haze specks spawned. */
	static int outlineQuads;
	static int cardQuads;
	static int landings;
	static int splashes;
	static int puffs;
	static int specks;

	private UryuRainFx() {
	}

	static void init() {
		if (inited) {
			return;
		}
		inited = true;
		FxGlowBatch.addSource(UryuRainFx::emit);
	}

	static void clear() {
		SQUARES.clear();
		RECENT.clear();
	}

	private static boolean calm() {
		return FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
	}

	private static double tier() {
		return Math.max(0.0, Math.min(1.0, FxConfig.uryuMaxFlechettes / 160.0));
	}

	private static Square find(int caster, double x, double z) {
		for (Square s : SQUARES) {
			if (s.caster == caster && !s.released && Math.abs(s.cx - x) < 3.5 && Math.abs(s.cz - z) < 3.5) {
				return s;
			}
		}
		return null;
	}

	/** The outline lights at the pre-aim point of the key press; the release follows {@code params[4]} seconds later. */
	static void castStart(EffectEventS2C e) {
		init();
		float[] p = e.params();
		SQUARES.removeIf(s -> s.caster == e.casterId());
		Square s = new Square();
		s.caster = e.casterId();
		s.cx = UryuRainMath.snap(p[0]);
		s.y0 = Math.floor(p[1] + 0.01);
		s.cz = UryuRainMath.snap(p[2]);
		s.litAt = FxClock.now;
		s.releaseAt = FxClock.now + p[4];
		SQUARES.add(s);
		FxSound.play(null, "block.beacon.power_select", s.cx, s.y0 + 0.5, s.cz, 1.9, 0.5);
	}

	/** params = {aim x, y, z, half width, seconds}: the release. The square of the key press is kept when it is near the aim, else it is lit now. */
	static void rainSquare(EffectEventS2C e) {
		init();
		float[] pr = e.params();
		if (pr.length < 5) {
			return;
		}
		Square s = find(e.casterId(), UryuRainMath.snap(pr[0]), UryuRainMath.snap(pr[2]));
		if (s == null) {
			SQUARES.removeIf(q -> q.caster == e.casterId());
			s = new Square();
			s.caster = e.casterId();
			s.cx = UryuRainMath.snap(pr[0]);
			s.y0 = Math.floor(pr[1] + 0.01);
			s.cz = UryuRainMath.snap(pr[2]);
			s.litAt = FxClock.now;
			SQUARES.add(s);
			FxSound.play(null, "block.beacon.power_select", s.cx, s.y0 + 0.5, s.cz, 1.9, 0.5);
		}
		s.released = true;
		s.releaseAt = FxClock.now;
		s.life = pr[4] + 0.3;
		FxSound.play(null, "block.conduit.ambient", s.cx, s.y0 + 6.0, s.cz, 1.6, 0.3);
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null && mc.player.getId() == e.casterId()) {
			s.vignette = ScreenFx.vignette(ScreenFx.Kind.DARK, calm() ? 0.10 : 0.20, 0.3, 1.0);
		}
	}

	/** A RAIN arrow left the list at {@code (x, y, z)}: if it is a landing inside a rain square, the splash (called by {@link UryuArrowRenderer}). */
	static void landing(double lx, double ly, double lz, double vx, double vy, double vz) {
		if (SQUARES.isEmpty()) {
			return;
		}
		for (Square s : SQUARES) {
			// The list holds the LAST position before the floor (2.6 blocks per tick: always about 1.1 over it), so the point is carried on along
			// the velocity to the floor plane; an arrow more than one step above the floor did not land (it hit a target or timed out).
			double t = (ly - s.y0) / -vy;
			if (t < -0.2 || t > 1.1) {
				continue;
			}
			t = Math.max(0.0, t);
			double x = lx + vx * t;
			double y = s.y0;
			double z = lz + vz * t;
			if (!s.released || !UryuRainMath.isLanding(s.cx, s.y0, s.cz, x, y, z)) {
				continue;
			}
			double now = FxClock.now;
			landings++;
			int n = landingNo++;
			RECENT.add(now);
			Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
			double dx = x - cam.x;
			double dz = z - cam.z;
			boolean near = dx * dx + dz * dz < UryuRainMath.NEAR_CAMERA * UryuRainMath.NEAR_CAMERA;
			double gy = s.y0 + 0.1;
			if (UryuRainMath.landingDrawn(n, tier(), near)) {
				splashes++;
				FxGlowBatch.sprite(GlowSprite.BURST_RAYS).at(x, gy, z).ground().size(0.9).lifeTicks(6).curve(0.01, 0.8).color(UryuPalette.Q2).peak(0.8).rot(RNG.nextDouble() * 6.28, 0.0).spawn();
				FxGlowBatch.sprite(GlowSprite.STAR4).at(x, gy + 0.12, z).size(0.5).lifeTicks(5).curve(0.01, 0.7).color(UryuPalette.Q1).peak(0.9).rot(RNG.nextDouble() * 3.0, 0.0).spawn();
			}
			if (UryuRainMath.landingPuff(n, tier(), near)) {
				puffs++;
				FxGlowBatch.sprite(GlowSprite.MIST).at(x, gy, z).vel(0.0, 0.5, 0.0).size(0.6, 1.5).lifeTicks(18).curve(0.05, 0.7).color(UryuPalette.Q9).peak(0.25).spawn();
			}
			if (now - lastChime > 1.0 / 3.0) {
				lastChime = now;
				FxSound.play(null, "block.amethyst_block.chime", x, gy, z, 1.5 + 0.7 * RNG.nextDouble(), 0.2);
			}
			if (now - lastHit > 1.0 / 8.0) {
				lastHit = now;
				FxSound.play(null, "block.amethyst_cluster.hit", x, gy, z, 1.9, 0.15);
			}
			if (now - lastTwinkle > 0.5) {
				lastTwinkle = now;
				FxSound.play(null, "entity.firework_rocket.twinkle", x, gy, z, 1.8, 0.1);
			}
			return;
		}
	}

	private static void emit(FxGlowBatch.Emitter e) {
		if (SQUARES.isEmpty()) {
			return;
		}
		double now = FxClock.now;
		double dt = Math.max(0.0, Math.min(0.1, now - lastNow));
		lastNow = now;
		RECENT.removeIf(t -> now - t > 0.5);
		double pulse = Math.min(1.0, RECENT.size() / 30.0);
		double emi = Math.max(0.0, FxConfig.emissiveMultiplier);
		boolean calm = calm();
		for (Square s : SQUARES) {
			double age = now - s.litAt;
			double rel = now - s.releaseAt;
			double end = s.releaseAt - s.litAt + s.life;
			if (!s.released && rel > 1.5) {
				s.ended = true; // the release never came
			}
			if (s.released && rel > s.life + 1.0) {
				s.ended = true;
			}
			if (s.ended) {
				continue;
			}
			if (s.vignette != null && s.vignette.held() && rel > s.life) {
				s.vignette.release(0.5);
			}
			outline(e, s, age, end, emi, calm);
			if (!s.released || rel < 0.05) {
				continue;
			}
			sky(e, s, rel - 0.05, s.life - 0.05, emi, pulse, dt);
		}
		SQUARES.removeIf(s -> s.ended);
	}

	private static void outline(FxGlowBatch.Emitter e, Square s, double age, double end, double emi, boolean calm) {
		double lvl = UryuRainMath.outlineLevel(age, end) * emi;
		if (lvl < 0.004) {
			return;
		}
		double h = UryuRainMath.HALF;
		double g = UryuRainMath.outlineGrow(age);
		double y = s.y0 + 0.08;
		double[][] c = {{s.cx - h, s.cz - h}, {s.cx + h, s.cz - h}, {s.cx + h, s.cz + h}, {s.cx - h, s.cz + h}};
		float[] q7 = FxMath.hex(UryuPalette.Q7);
		for (int i = 0; i < 4; i++) {
			double[] a = c[i];
			double[] b = c[(i + 1) % 4];
			// two halves, each from its corner toward the middle of the edge
			for (int half = 0; half < 2; half++) {
				double[] from = half == 0 ? a : b;
				double[] to = half == 0 ? b : a;
				double tx = from[0] + (to[0] - from[0]) * 0.5 * g;
				double tz = from[1] + (to[1] - from[1]) * 0.5 * g;
				if (e.streak(GlowSprite.STREAK, from[0], y, from[1], tx, y, tz, UryuRainMath.OUTLINE_WIDTH, q7[0], q7[1], q7[2], 0.5 * lvl)) {
					outlineQuads++;
				}
				e.streak(GlowSprite.STREAK, from[0], y + 0.01, from[1], tx, y + 0.01, tz, 0.05, 1f, 1f, 1f, 0.8 * lvl);
			}
			if (g > 0.99 && !calm) {
				// a bead runs along the edge
				double t = UryuRainMath.bead(age + i * 0.25);
				e.sprite(GlowSprite.GLOW_CORE, a[0] + (b[0] - a[0]) * t, y + 0.05, a[1] + (b[1] - a[1]) * t, 0.3, 0, 1f, 1f, 1f, 0.8 * lvl);
			}
			e.sprite(GlowSprite.GLOW_CORE, a[0], y + 0.1, a[1], 0.4, 0, UryuPalette.FQ1[0], UryuPalette.FQ1[1], UryuPalette.FQ1[2], lvl);
		}
	}

	private static void sky(FxGlowBatch.Emitter e, Square s, double rel, double life, double emi, double pulse, double dt) {
		double c = UryuRainMath.cardLevel(rel, life) * emi;
		double tier = tier();
		double top = s.y0 + UryuRainMath.CARD_HEIGHT;
		if (c > 0.004) {
			float[] q2 = UryuPalette.FQ2;
			float[] q3 = UryuPalette.FQ3;
			float[] q4 = FxMath.hex(UryuPalette.Q4);
			if (e.ground(GlowSprite.STAR_FIELD, s.cx, top, s.cz, UryuRainMath.CARD_SIZE, UryuRainMath.CARD_SIZE, 0.0, q2[0], q2[1], q2[2], UryuRainMath.CARD_ALPHA * c)) {
				cardQuads++;
			}
			e.ground(GlowSprite.GLOW_SOFT, s.cx, top + 0.05, s.cz, UryuRainMath.GLOW_SIZE, UryuRainMath.GLOW_SIZE, 0.0, q4[0], q4[1], q4[2], UryuRainMath.GLOW_ALPHA * c);
			double h = UryuRainMath.HALF;
			for (int i = 0; i < 4; i++) {
				double sx = s.cx + ((i & 1) == 0 ? -h : h);
				double sz = s.cz + (i < 2 ? -h : h);
				e.streak(GlowSprite.LINE, sx, s.y0 + 0.1, sz, sx, top, sz, UryuRainMath.SHAFT_WIDTH, q2[0], q2[1], q2[2], UryuRainMath.SHAFT_ALPHA * c);
			}
			// the ground glow beats with the landing rate
			e.ground(GlowSprite.GLOW_SOFT, s.cx, s.y0 + 0.06, s.cz, h * 2.0, h * 2.0, 0.0, q3[0], q3[1], q3[2], (0.1 + 0.08 * pulse) * c);
		}
		if (rel < 0.25) {
			return;
		}
		// glitter falls from the card, 10 a second (it goes on for 1 s after the rain); haze at the floor (not on Low)
		s.glitterAcc += UryuRainMath.GLITTER_RATE * Math.max(0.3, tier) * dt;
		while (s.glitterAcc >= 1.0) {
			s.glitterAcc -= 1.0;
			double px = s.cx + (RNG.nextDouble() * 2.0 - 1.0) * UryuRainMath.HALF;
			double pz = s.cz + (RNG.nextDouble() * 2.0 - 1.0) * UryuRainMath.HALF;
			specks++;
			FxGlowBatch.sprite(GlowSprite.GLITTER).at(px, top - 0.2, pz).vel(0.0, -UryuRainMath.GLITTER_SPEED, 0.0).size(0.25).life(UryuRainMath.GLITTER_LIFE).curve(0.05, 0.4)
					.color(UryuPalette.Q2).peak(0.8).spawn();
		}
		if (tier >= 0.5 && rel < life) {
			s.hazeAcc += UryuRainMath.HAZE_RATE * dt;
			while (s.hazeAcc >= 1.0) {
				s.hazeAcc -= 1.0;
				double px = s.cx + (RNG.nextDouble() * 2.0 - 1.0) * UryuRainMath.HALF;
				double pz = s.cz + (RNG.nextDouble() * 2.0 - 1.0) * UryuRainMath.HALF;
				specks++;
				FxGlowBatch.sprite(GlowSprite.MIST).at(px, s.y0 + 0.3, pz).vel(0.0, 0.05, 0.0).size(3.0).lifeTicks(40).curve(0.4, 0.5).color(UryuPalette.Q2).peak(0.1).spawn();
			}
		}
	}
}
