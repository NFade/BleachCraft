package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.UryuBlutTiming;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * The Blut Arterie release of Uryu (SHIKAI, effect id 70 = URYU_SHIKAI_RELEASE), URYU_VFX_STORYBOARD 2.5: the one shot timeline around the
 * vein overlay that {@link UryuQuincyFeature} draws on the player model. Called through {@link UryuAuraFx#releaseShikai}. All numbers are in
 * {@link UryuBlutTiming} (unit tested); this class only places them in the world.
 *
 * <pre>
 *   0.00  fists: 30 dashes converge into both hands (radius 1.4), a pentagon outline at the feet draws itself (one edge per 0.04 s), VIGNETTE dark
 *   0.20  the veins ignite from the fingertips up the arms and across chest and neck (the overlay, 0.35 s), 6 star4 sparkles at hands, elbows, neck
 *   0.55  burst: ground ring 0..4 in 0.35 s, rotating zeichen_star, 30 dashes radial from the chest, body flash, FLASH, SHAKE, the vignette lets go
 *   0.90  heartbeat x3 (the overlay pulses, sounds); then the state level
 * </pre>
 * Reduce Motion: the flash is capped at 0.25 for 0.07 s, no shake, the vignette is 0.10, the pulse of the overlay is a constant 0.7 (in the
 * feature). Tier LOW: half the dashes. Everything here is UNVERIFIED in game (design/results/uryu_ufx6.md).
 */
final class UryuBlutFx {
	/** Statistics for the harness. */
	static int releases;
	static int lastGlowSprites;

	private UryuBlutFx() {
	}

	/** Body frame of the caster: feet and the horizontal forward / right unit vectors (live body yaw when the entity is loaded, else the event direction). */
	private record Frame(Vec3d feet, double fx, double fz) {
		double rx() {
			return -fz;
		}

		double rz() {
			return fx;
		}

		/** A point {@code right} to the right, {@code fwd} forward and {@code up} above the feet. */
		Vec3d at(double right, double fwd, double up) {
			return new Vec3d(feet.x + rx() * right + fx * fwd, feet.y + up, feet.z + rz() * right + fz * fwd);
		}
	}

	private static Frame frame(EffectTimeline t) {
		Entity c = t.caster();
		if (c instanceof LivingEntity le) {
			double yaw = Math.toRadians(le.bodyYaw);
			return new Frame(c.getLerpedPos(0f), -Math.sin(yaw), Math.cos(yaw));
		}
		double[] f = RukiaKit.flat(t.dir);
		return new Frame(t.pos, f[0], f[1]);
	}

	/** Hand, elbow and neck points of the release pose (idle hands drift towards the crossed wrists in front of the chest, 3.7). */
	private static Vec3d hand(Frame f, int side) {
		return f.at(side * 0.30, 0.25, 0.95);
	}

	private static Vec3d elbow(Frame f, int side) {
		return f.at(side * 0.40, 0.12, 1.05);
	}

	private static Vec3d chest(Frame f) {
		return f.at(0.0, 0.15, 1.2);
	}

	private static Vec3d neck(Frame f) {
		return f.at(0.0, 0.05, 1.5);
	}

	private static int count(int n) {
		return FxConfig.fxTier == FxConfig.TierSetting.LOW ? Math.max(1, n / 2) : n;
	}

	static void release(EffectEventS2C e) {
		releases++;
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		EffectTimeline t = FxTimelines.create(e.effectId(), e.seed(), pos, dir, e.params(), e.casterId(), FxClient.forceRemote());
		// the overlay clock of the feature: t = 0 of the table is now, the veins start at 0.2 s
		UryuQuincyFeature.onRelease(e.casterId());
		boolean rm = FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
		ScreenFx.Layer[] vignette = new ScreenFx.Layer[1];
		lastGlowSprites = 0;

		// sounds: one table, shared with the unit test
		for (UryuBlutTiming.Cue cue : UryuBlutTiming.CUES) {
			t.at(cue.t(), x -> x.sound(cue.sound(), cue.pitch(), cue.volume()));
		}

		// 0.00 fists, pentagon, vignette
		t.at(UryuBlutTiming.T_FISTS, x -> {
			Frame f = frame(x);
			fists(x, f);
			pentagon(x, f);
			vignette[0] = x.vignette(ScreenFx.Kind.DARK, rm ? UryuBlutTiming.VIGNETTE_RM : UryuBlutTiming.VIGNETTE, UryuBlutTiming.VIGNETTE_RAMP);
		});
		// the ground ring that closes the outline, as the veins ignite
		t.at(UryuBlutTiming.pentagonClosed(), x -> {
			Frame f = frame(x);
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(f.feet().x, f.feet().y + 0.07, f.feet().z).ground().size(UryuBlutTiming.PENTAGON_RADIUS / 0.4375)
					.life(0.9).curve(0.05, 0.6).color(UryuPalette.Q7).peak(0.45).owner(x).spawn();
			lastGlowSprites++;
		});
		// 0.20 veins ignite: sparkles at hands, elbows, neck and the glow traces on the forearms
		t.at(UryuBlutTiming.T_IGNITE, x -> {
			Frame f = frame(x);
			Vec3d[] pts = {hand(f, 1), hand(f, -1), elbow(f, 1), elbow(f, -1), neck(f), chest(f)};
			for (int i = 0; i < UryuBlutTiming.SPARKLES; i++) {
				FxGlowBatch.sprite(GlowSprite.STAR4).at(pts[i].x, pts[i].y, pts[i].z).size(UryuBlutTiming.SPARKLE_SIZE * 2.2, UryuBlutTiming.SPARKLE_SIZE)
						.life(UryuBlutTiming.SPARKLE_LIFE + 0.06).curve(0.01, 0.6).color(UryuPalette.Q1).peak(0.95).rot(0.4 * i, 1.5).owner(x).spawn();
				lastGlowSprites++;
			}
			for (int side = -1; side <= 1; side += 2) {
				Vec3d a = f.at(side * 0.36, 0.2, 1.0);
				FxGlowBatch.sprite(GlowSprite.VEIN_TRACE).at(a.x, a.y, a.z).size(0.75).life(UryuBlutTiming.SWEEP_TIME + 0.15).curve(0.05, 0.6).color(UryuPalette.Q3).peak(0.55)
						.rot(side * 0.9, 0.0).owner(x).spawn();
				lastGlowSprites++;
			}
		});
		// 0.55 burst
		t.at(UryuBlutTiming.T_BURST, x -> {
			Frame f = frame(x);
			burst(x, f);
			x.flash(rm ? UryuBlutTiming.FLASH_RM : UryuBlutTiming.FLASH, UryuPalette.Q1, false);
			x.shake(UryuBlutTiming.SHAKE_AMPLITUDE, UryuBlutTiming.SHAKE_TIME);
			if (vignette[0] != null) {
				vignette[0].release(UryuBlutTiming.VIGNETTE_FADE);
			}
		});
		// safety: a vignette that was not let go at the burst (the effect was cut) is released here
		t.at(UryuBlutTiming.T_BURST + 0.5, x -> {
			if (vignette[0] != null && vignette[0].held()) {
				vignette[0].release(UryuBlutTiming.VIGNETTE_FADE);
			}
		});
		t.lifetime(UryuBlutTiming.heartEnd() + 0.2);
		FxTimelines.start(t);
	}

	/** 0.00: dashes converge into both hands from a sphere of radius 1.4; they need 0.2 s. */
	private static void fists(EffectTimeline t, Frame f) {
		Random r = t.rng(11);
		int n = count(UryuBlutTiming.FIST_DASHES);
		for (int i = 0; i < n; i++) {
			Vec3d h = hand(f, i % 2 == 0 ? 1 : -1);
			double a = r.nextDouble() * Math.PI * 2;
			double el = Math.asin(r.nextDouble() * 1.6 - 0.8);
			Vec3d d = new Vec3d(Math.cos(el) * Math.cos(a), Math.sin(el), Math.cos(el) * Math.sin(a));
			Vec3d start = h.add(d.multiply(UryuBlutTiming.FIST_RADIUS));
			Vec3d v = h.subtract(start).multiply(1.0 / UryuBlutTiming.FIST_TRAVEL);
			FxGlowBatch.sprite(GlowSprite.DASH).at(start.x, start.y, start.z).vel(v.x, v.y, v.z).streak().streakTime(0.06).size(0.7 + 0.3 * r.nextDouble())
					.life(UryuBlutTiming.FIST_TRAVEL).delay(0.04 * r.nextDouble()).curve(0.02, 0.35).color(i % 3 == 0 ? UryuPalette.Q1 : UryuPalette.Q2).peak(0.9).owner(t).spawn();
			lastGlowSprites++;
		}
	}

	/** 0.00: the pentagon outline at the feet (radius 1.8, Q7 0.7) draws itself, one edge per 0.04 s; a bright bead marks the pen. */
	private static void pentagon(EffectTimeline t, Frame f) {
		double r = UryuBlutTiming.PENTAGON_RADIUS;
		double y = f.feet().y + 0.07;
		for (int k = 0; k < 5; k++) {
			double[] a = UryuBlutTiming.pentagonVertex(r, k, f.fx(), f.fz());
			double[] b = UryuBlutTiming.pentagonVertex(r, k + 1, f.fx(), f.fz());
			double dx = b[0] - a[0];
			double dz = b[1] - a[1];
			double len = Math.hypot(dx, dz);
			double delay = UryuBlutTiming.pentagonEdgeStart(k);
			FxShapes.Strip s = FxShapes.strip(f.feet().x + a[0], y, f.feet().z + a[1], dx, 0, dz, delay)
					.length(age -> len * Math.min(1.0, age / UryuBlutTiming.PENTAGON_EDGE_TIME)).width(age -> 0.11);
			s.color(UryuPalette.Q7).life(0.9).owner(t).intensity(age -> UryuBlutTiming.PENTAGON_INTENSITY * Math.min(1.0, (0.9 - age) / 0.3));
			FxGlowBatch.sprite(GlowSprite.STAR4).at(f.feet().x + b[0], y + 0.04, f.feet().z + b[1]).delay(delay + UryuBlutTiming.PENTAGON_EDGE_TIME).size(0.45).life(0.55)
					.curve(0.02, 0.7).color(UryuPalette.Q1).peak(0.8).rot(0.3 * k, 1.2).owner(t).spawn();
			lastGlowSprites++;
		}
	}

	/** 0.55: ground ring 0..4 in 0.35 s, the rotating zeichen_star, 30 dashes radial from the chest, the body flash. */
	private static void burst(EffectTimeline t, Frame f) {
		Random r = t.rng(55);
		Vec3d c = chest(f);
		double gy = f.feet().y + 0.06;
		// RING_SOFT draws its ring at 0.375 of its width
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(f.feet().x, gy, f.feet().z).ground().size(0.6, UryuBlutTiming.RING_RADIUS / 0.375).sizeEase(FxMath.OC)
				.life(UryuBlutTiming.RING_TIME).curve(0.02, 0.85).color(UryuPalette.Q2).peak(0.7).owner(t).spawn();
		FxGlowBatch.sprite(GlowSprite.ZEICHEN_STAR).at(f.feet().x, gy + 0.01, f.feet().z).ground().size(UryuBlutTiming.STAR_SIZE).life(UryuBlutTiming.STAR_LIFE).curve(0.05, 0.55)
				.rot(0.0, Math.PI * 2 * UryuBlutTiming.STAR_SPIN).color(UryuPalette.Q1).peak(UryuBlutTiming.STAR_PEAK).owner(t).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(c.x, c.y, c.z).size(UryuBlutTiming.BODY_FLASH_SIZE).life(UryuBlutTiming.BODY_FLASH_LIFE).curve(0.02, 0.7).color(UryuPalette.Q1)
				.peak(UryuBlutTiming.BODY_FLASH_PEAK).owner(t).spawn();
		lastGlowSprites += 3;
		int n = count(UryuBlutTiming.BURST_DASHES);
		for (int i = 0; i < n; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double el = Math.asin(r.nextDouble() * 1.4 - 0.5);
			double s = UryuBlutTiming.BURST_SPEED * (0.7 + 0.3 * r.nextDouble());
			FxGlowBatch.sprite(GlowSprite.DASH).at(c.x, c.y, c.z).vel(Math.cos(el) * Math.cos(a) * s, Math.sin(el) * s, Math.cos(el) * Math.sin(a) * s).drag(UryuBlutTiming.BURST_DRAG)
					.streak().streakTime(0.07).size(0.6 + 0.3 * r.nextDouble()).lifeTicks(UryuBlutTiming.BURST_LIFE_TICKS).curve(0.01, 0.6)
					.color(i % 4 == 0 ? UryuPalette.Q1 : UryuPalette.Q2).peak(0.9).owner(t).spawn();
			lastGlowSprites++;
		}
	}
}
