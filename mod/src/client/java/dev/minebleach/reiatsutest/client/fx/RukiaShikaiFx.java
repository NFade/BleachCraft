package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.RukiaFxParams;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.math.Vec3d;

/**
 * Rukia shikai abilities (VFX_STORYBOARD 3.1 to 3.3): Tsukishiro (id 20, a ring of frost, a pillar of light, rim crystals,
 * shatter at 2.0), Hakuren (id 21, a fan of ice spikes running along the look at the server's step speed) and Shirafune (id 22,
 * a blade of ice thrust along the aim ray). Radii, lengths and step speeds come from the event params
 * ({@link RukiaFxParams}), never from constants. The shells on the targets are the entity fx ({@link RukiaEntityFx}); the
 * server hit (kind HIT) shatters them. Every number can be tuned live through {@code FxTune} keys {@code tsuki.*},
 * {@code hak.*} and {@code shira.*}.
 */
public final class RukiaShikaiFx {
	private RukiaShikaiFx() {
	}

	public static void play(EffectEventS2C e) {
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		EffectTimeline t = FxTimelines.create(e.effectId(), e.seed(), pos, dir, e.params(), e.casterId(), FxClient.forceRemote());
		switch (e.effectId()) {
			case 20 -> tsukishiro(t);
			case 21 -> hakuren(t);
			case 22 -> shirafune(t);
			default -> {
				return;
			}
		}
		FxTimelines.start(t);
	}

	private static double d(String key, double def) {
		return FxTune.d(key, def);
	}

	// ============================================================================================================ 3.1 Tsukishiro

	private static void tsukishiro(EffectTimeline t) {
		double R = RukiaFxParams.param(t.params, 3, 4.0);
		double H = RukiaFxParams.param(t.params, 4, R * RukiaFxParams.TSUKISHIRO_PILLAR_PER_RADIUS);
		Vec3d c = t.pos;
		double gy = c.y;
		double[] f = RukiaKit.flat(t.dir);
		double a0 = Math.atan2(f[1], f[0]);
		List<FxMeshPass.Crystal> rim = new ArrayList<>();
		final ScreenFx.Layer[] vig = new ScreenFx.Layer[1];
		t.lifetime(3.1);

		// 0.0 wind-up: a comet of motes sweeping the ring, 360 degrees in 0.2 s from the caster's yaw
		t.at(0.0, x -> x.sound("entity.player.attack.sweep", 1.1, 0.7));
		int steps = 24;
		for (int i = 0; i < steps; i++) {
			final int k = i;
			t.at(0.2 * i / steps, x -> {
				double a = a0 + Math.PI * 2 * k / steps;
				double px = c.x + Math.cos(a) * R;
				double pz = c.z + Math.sin(a) * R;
				Random r = x.rng(100 + k);
				FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(px, gy + 0.3 + r.nextDouble() * 0.3, pz).vel(-Math.sin(a) * 2, 1.0, Math.cos(a) * 2).drag(0.95).life(14)
						.size(d("tsuki.moteSize", 0.42)).color("#F0F8FF").seed(k).fade(0.4).owner(x).spawn();
				FxGlowBatch.sprite(GlowSprite.SPECK).at(px, gy + 0.35, pz).size(0.38).life(0.4).curve(0.02, 0.7).color("#FFFFFF").peak(0.9).owner(x).spawn();
				// the comet: a streak running along the tangent
				FxGlowBatch.sprite(GlowSprite.STREAK).at(px, gy + 0.4, pz).vel(-Math.sin(a) * 26, 0.5, Math.cos(a) * 26).streak().streakTime(0.03).size(1.4).lifeTicks(3)
						.curve(0.01, 0.6).color("#DFF3FF").peak(0.9).owner(x).spawn();
			});
		}
		// 0.2 ring complete: sigil on the ground, frost decal creeping out
		t.at(0.2, x -> {
			FxGlowBatch.sprite(GlowSprite.FROST_SIGIL).at(c.x, gy + 0.05, c.z).ground().size(R * 2.1).life(2.6).curve(0.1, 0.2).rot(a0, 2 * Math.PI * 0.05)
					.color("#BFE4FF").peak(d("tsuki.sigil", 0.9)).owner(x).spawn();
			FxDecals.frost(c.x, c.z, gy, R * 1.05, 0).reveal(0.3).fade(2.3, 0.5).alpha(d("tsuki.decal", 0.9));
			x.sound("block.amethyst_block.chime", 1.0, 0.7);
		});
		// 0.5 the pillar: two walls of light (core 0.825 R, outer R), 0 to H in 0.35 s, scrolling up, collapsing at 2.0
		t.at(0.5, x -> {
			FxShapes.Wall core = FxShapes.wall(c.x, c.z, gy, 16, 0).radius(a -> R * 0.825).height(a -> H * FxMath.oc(a / 0.35) * collapse(a, 1.5)).topFade(0.55).scroll(2.0, 0.2);
			core.color("#EAF8FF").intensity(a -> d("tsuki.core", 0.2) * FxMath.clamp(a / 0.05) * (1 - FxMath.clamp((a - 1.5) / 0.3))).life(1.9).owner(x).cell(GlowSprite.PILLAR);
			FxShapes.Wall outer = FxShapes.wall(c.x, c.z, gy, 16, 0).radius(a -> R).height(a -> H * FxMath.oc(a / 0.35) * collapse(a, 1.5)).topFade(0.4).scroll(1.5, 0.25);
			outer.color("#CFEFFF").intensity(a -> d("tsuki.outer", 0.10) * FxMath.clamp(a / 0.05) * (1 - FxMath.clamp((a - 1.5) / 0.3))).life(1.9).owner(x).cell(GlowSprite.PILLAR);
			x.flash(0.30, "#EAF8FF", true);
			x.sound("item.trident.thunder", 1.4, 0.3);
			x.sound("block.beacon.power_select", 1.2, 0.5);
		});
		for (int k = 0; k < 12; k++) {
			final int kk = k;
			t.at(0.5 + 0.03 * k, x -> {
				Random r = x.rng(200 + kk);
				RukiaKit.frostMotes(x, r, 10, c.x, gy + 0.1, c.z, R * 0.95, 0.8, 4.0, 0.3, 20, d("tsuki.moteSize", 0.42), "#EAF8FF", "#CFEFFF", 1.0);
				if (kk % 3 == 0) {
					RukiaKit.stars(x, r, GlowSprite.STAR6, 4, c.x, gy + 0.3, c.z, R * 0.9, 1.0, 4.0, 0.2, 1.1, 0.7, "#FFFFFF", 0.8);
				}
			});
		}
		// 1.0 freeze: rim crystals, ice dust, frost creeps in at the edges of the screen
		t.at(1.0, x -> {
			Random r = x.rng(300);
			int n = (int) d("tsuki.crystals", 10);
			for (int i = 0; i < n; i++) {
				double a = Math.PI * 2 * (i + r.nextDouble() * 0.7) / n;
				double rad = R * (0.86 + 0.16 * r.nextDouble());
				double px = c.x + Math.cos(a) * rad;
				double pz = c.z + Math.sin(a) * rad;
				double py = FxGround.sample(px, pz, gy);
				double lean = 0.15 + r.nextDouble() * 0.2;
				FxMeshPass.Crystal cr = FxMeshPass.crystal(r.nextBoolean() ? FxMeshes.CRYSTAL_C : FxMeshes.CRYSTAL_B, px, py - 0.03, pz, Math.cos(a) * lean, 1, Math.sin(a) * lean,
						r.nextDouble() * 6.28, d("tsuki.crystalScale", 2.1) * (0.7 + 0.6 * r.nextDouble()), 0.04 * i);
				cr.field = -1;
				rim.add(cr);
			}
			vig[0] = x.vignette(ScreenFx.Kind.FROST, 0.20, 0.3);
			x.sound("entity.player.hurt_freeze", 1.0, 0.8);
			x.sound("block.amethyst_cluster.place", 0.9, 0.6);
			x.soundAt("block.amethyst_cluster.place", c.add(R * 0.7, 0.5, 0), 1.2, 0.5);
			RukiaKit.iceShards(x, r, 24, c.x, gy + 0.2, c.z, R, 0.8, 1.2, 0.0, 40, 0.34);
		});
		// 1.5 hold: frost creeps, the sigil pulses at 3 Hz
		for (int k = 0; k < 3; k++) {
			t.at(1.5 + k / 3.0 * 0.5, x -> {
				Random r = x.rng(400);
				FxGlowBatch.sprite(GlowSprite.FROST_SIGIL).at(c.x, gy + 0.06, c.z).ground().size(R * 2.1).life(0.17).curve(0.04, 0.5).rot(a0, 0).color("#FFFFFF").peak(0.35).owner(x).spawn();
				RukiaKit.frostMotes(x, r, 5, c.x, gy + 0.2, c.z, R, 1.5, 1.2, 0.3, 18, 0.34, "#DFF3FF", null, 0.9);
				RukiaKit.stars(x, r, GlowSprite.STAR6, 3, c.x, gy + 0.6, c.z, R * 0.9, 1.6, 0.4, 0.2, 0.6, 0.6, "#FFFFFF", 0.8);
			});
		}
		// 2.0 shatter: hit stop, then the pillar is gone, crystals and the ice burst
		t.at(2.0, x -> x.hitstop(60));
		t.at(2.0001, x -> {
			Random r = x.rng(500);
			for (FxMeshPass.Crystal cr : rim) {
				FxMeshPass.shatter(cr, (int) d("tsuki.crystalShards", 4), 6.5, r.nextLong());
			}
			RukiaKit.iceShards(x, r, 140, c.x, gy + 0.3, c.z, R * 0.9, d("tsuki.burstSpeed", 5.0), 4.5, 1.0, 26, 0.42);
			RukiaKit.frostMotes(x, r, 80, c.x, gy + 0.4, c.z, R * 0.9, 2.0, 2.0, 5.0, 22, 0.42, "#FFFFFF", "#BFE4FF", 1.0);
			RukiaKit.stars(x, r, GlowSprite.STAR4, 40, c.x, gy + 0.8, c.z, R * 0.9, 2.0, 3.0, 5.0, 0.4, 0.8, "#FFFFFF", 0.95);
			x.shake(0.6, 0.35);
			if (vig[0] != null) {
				vig[0].release(0.5);
			}
			x.soundAt("block.glass.break", c.add(0, 1, 0), 1.0, 1.0);
		});
		t.at(2.07, x -> x.sound("block.glass.break", 1.2, 0.9));
		t.at(2.14, x -> x.sound("block.glass.break", 0.8, 0.8));
		// 2.5 fade: a last drift of motes
		t.at(2.5, x -> RukiaKit.frostMotes(x, x.rng(600), 20, c.x, gy + 0.3, c.z, R, 1.0, 0.8, 0.5, 20, 0.34, "#DFF3FF", null, 0.8));
	}

	/** Collapse factor: 1 until {@code from}, then 1 to 0 in 0.25 s (easeInQuad), age in seconds. */
	private static double collapse(double age, double from) {
		return 1.0 - FxMath.iq((age - from) / 0.25);
	}

	// ============================================================================================================ 3.2 Hakuren

	private static void hakuren(EffectTimeline t) {
		double L = RukiaFxParams.param(t.params, 3, 12.0);
		double step = RukiaFxParams.param(t.params, 4, L / RukiaFxParams.HAKUREN_STEPS);
		int steps = (int) Math.max(1, RukiaFxParams.param(t.params, 5, RukiaFxParams.HAKUREN_STEPS));
		double sc = Math.max(0.6, L / 12.0);
		Vec3d c = t.pos;
		double gy = c.y;
		double[] f = RukiaKit.flat(t.dir);
		double[] rg = RukiaKit.right(f);
		List<FxMeshPass.Crystal> mine = new ArrayList<>();
		t.lifetime(3.4);

		// 0.0 ground puncture under her
		t.at(0.0, x -> {
			RukiaKit.ring(x, c.x, gy + 0.05, c.z, 2.0, 2.0, 0.5, "#C0D3E7", 1.0, false);
			RukiaKit.frostMotes(x, x.rng(10), 30, c.x, gy + 0.1, c.z, 2.0, 0.4, 3.0, 0.3, 16, 0.4, "#EAF8FF", "#CFEFFF", 1.0);
			x.sound("block.glass.break", 1.2, 0.5);
		});
		// 0.3 four punctures on a semicircle of radius 2.5 in front (-60, -20, 20, 60 degrees)
		double[] offs = {-60, -20, 20, 60};
		for (int k = 0; k < 4; k++) {
			final int kk = k;
			t.at(0.3 + 0.06 * k, x -> {
				double ang = Math.toRadians(offs[kk]);
				double dx = f[0] * Math.cos(ang) - f[1] * Math.sin(ang);
				double dz = f[0] * Math.sin(ang) + f[1] * Math.cos(ang);
				double px = c.x + dx * 2.5;
				double pz = c.z + dz * 2.5;
				double py = FxGround.sample(px, pz, gy);
				Random r = x.rng(20 + kk);
				RukiaKit.ring(x, px, py + 0.05, pz, 0.8, 0.8, 0.45, "#DFF3FF", 0.9, true);
				RukiaKit.frostMotes(x, r, 20, px, py + 0.1, pz, 0.35, 0.3, 5.0, 0.4, 14, 0.38, "#EAF8FF", "#CFEFFF", 1.0);
				FxMeshPass.Crystal cr = FxMeshPass.crystal(FxMeshes.CRYSTAL_C, px, py - 0.03, pz, dx * 0.3, 1, dz * 0.3, r.nextDouble() * 6.28, d("hak.puncture", 1.9), 0.0);
				mine.add(cr);
				x.soundAt("block.amethyst_cluster.break", new Vec3d(px, py + 0.5, pz), 1.0 + 0.1 * kk, 0.7);
			});
		}
		// 0.7 gather at the blade tip
		t.at(0.7, x -> {
			Vec3d tip = RukiaKit.tip(x, 0.9, 0.0);
			FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(tip.x, tip.y, tip.z).size(0.9, 0.35).sizeEase(FxMath.IQ).life(0.3).curve(0.05, 0.4).color("#EAF8FF").peak(0.9).owner(x).spawn();
			Random r = x.rng(30);
			for (int i = 0; i < 30; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double b = (r.nextDouble() - 0.5) * 2.0;
				double rad = 1.0 + r.nextDouble() * 0.4;
				double px = tip.x + Math.cos(a) * rad;
				double py = tip.y + b * 0.6;
				double pz = tip.z + Math.sin(a) * rad;
				FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(px, py, pz).vel((tip.x - px) / 0.3, (tip.y - py) / 0.3, (tip.z - pz) / 0.3).drag(1.0).life(6).size(0.3)
						.color("#FFFFFF").seed(i).fade(0.5).owner(x).spawn();
			}
			x.sound("block.beacon.power_select", 1.3, 0.6);
		});
		// 1.0 to 1.4: the wave, one step every 0.1 s (24 blocks per second for the standard 2.4 block step)
		for (int k = 0; k < steps; k++) {
			final int kk = k;
			t.at(1.0 + 0.1 * k, x -> {
				Random r = x.rng(40 + kk);
				double d0 = step * kk;
				double dc = step * (kk + 1);
				for (int j = 0; j < 6; j++) {
					double lat = -1.9 + 3.8 * (j + r.nextDouble() * 0.6) / 6.0;
					double along = d0 + step * (0.25 + 0.75 * r.nextDouble());
					double px = c.x + f[0] * along + rg[0] * lat;
					double pz = c.z + f[1] * along + rg[1] * lat;
					double py = FxGround.sample(px, pz, gy);
					double lean = Math.tan(Math.toRadians(25 + r.nextDouble() * 10));
					boolean big = j % 3 == 2;
					FxMeshPass.Crystal cr = FxMeshPass.crystal(big ? FxMeshes.CRYSTAL_D : FxMeshes.CRYSTAL_C, px, py - 0.04, pz, f[0] * lean + rg[0] * (r.nextDouble() - 0.5) * 0.3, 1,
							f[1] * lean + rg[1] * (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 6.28, (big ? d("hak.bigScale", 2.2) : d("hak.smallScale", 2.0)) * sc * (0.75 + 0.5 * r.nextDouble()),
							0.01 * j);
					cr.grow = 0.12;
					mine.add(cr);
				}
				// mist along the path and the particles of the slices
				for (int j = 0; j < 8; j++) {
					double along = d0 + step * r.nextDouble();
					double lat = (r.nextDouble() - 0.5) * 4.0;
					FxGlowBatch.sprite(GlowSprite.MIST).at(c.x + f[0] * along + rg[0] * lat, gy + 0.2 + r.nextDouble() * 0.5, c.z + f[1] * along + rg[1] * lat).ground()
							.rot(r.nextDouble() * 6.28, 0.1).size(3.0 + r.nextDouble()).vel(f[0] * 0.8, 0.1, f[1] * 0.8).life(2.0).curve(0.15, 0.7).color("#EAF8FF").peak(d("hak.mist", 0.22)).owner(x).spawn();
				}
				for (int j = 0; j < 44; j++) {
					double along = d0 + step * r.nextDouble();
					double lat = (r.nextDouble() - 0.5) * 4.0;
					FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(c.x + f[0] * along + rg[0] * lat, gy + 0.1 + r.nextDouble() * 2.2, c.z + f[1] * along + rg[1] * lat)
							.vel(f[0] * 3, 1.5 + r.nextDouble(), f[1] * 3).drag(0.95).life(16 + r.nextInt(8)).size(0.38).seed(j).color("#FFFFFF").colorTo("#BFE4FF").fade(0.4).owner(x).spawn();
				}
				for (int j = 0; j < 8; j++) {
					double along = d0 + step * r.nextDouble();
					double lat = (r.nextDouble() - 0.5) * 4.0;
					FxParticles.spec(FxParticles.Kind.ICE_SHARD).at(c.x + f[0] * along + rg[0] * lat, gy + 0.2 + r.nextDouble() * 1.5, c.z + f[1] * along + rg[1] * lat)
							.vel(f[0] * 2, 2 + r.nextDouble() * 2, f[1] * 2).drag(0.95).gravity(0.6).life(26).size(0.36).seed(j).spin((r.nextDouble() - 0.5) * 0.4).fade(0.3).owner(x).spawn();
				}
				x.soundAt("block.amethyst_cluster.place", new Vec3d(c.x + f[0] * dc, gy + 0.5, c.z + f[1] * dc), 0.8 + 0.1 * kk, 0.6);
			});
		}
		// the leading edge: three flares standing across the path, running at the step speed
		t.at(1.0, x -> {
			double speed = step * 10.0;
			for (int i = 0; i < 3; i++) {
				FxGlowBatch.sprite(GlowSprite.FLARE).at(c.x + f[0] * 0.3, gy + 1.2, c.z + f[1] * 0.3).plane(f[0], 0, f[1]).size(5.0 * sc).vel(f[0] * speed, 0, f[1] * speed).delay(0.03 * i)
						.life(Math.max(0.3, L / speed)).curve(0.04, 0.35).color(i == 0 ? "#EAF8FF" : "#C0D3E7").peak(0.55 - 0.12 * i).owner(x).spawn();
			}
			x.shake(0.5, 0.4);
			x.sound("entity.breeze.wind_burst", 1.2, 0.8);
			x.sound("block.powder_snow.break", 0.9, 0.8);
		});
		// 1.5 frost on the real blocks
		t.at(1.5, x -> {
			Random r = x.rng(50);
			for (int i = 0; i < 60; i++) {
				double along = L * r.nextDouble();
				double lat = (r.nextDouble() - 0.5) * 4.0;
				double px = c.x + f[0] * along + rg[0] * lat;
				double pz = c.z + f[1] * along + rg[1] * lat;
				FxParticles.spec(FxParticles.Kind.ICE_SHARD).at(px, gy + 0.2, pz).vel(0, 1.0 + r.nextDouble(), 0).drag(0.94).gravity(0.2).life(26).size(0.34).seed(i).spin(0.1).fade(0.3)
						.owner(x).spawn();
				if (i % 3 == 0) {
					FxGlowBatch.sprite(GlowSprite.STAR6).at(px, gy + 0.4 + r.nextDouble(), pz).size(0.5).lifeTicks(12).color("#FFFFFF").peak(0.85).twinkle().rot(r.nextDouble() * 6, 1).owner(x).spawn();
				}
			}
			x.sound("entity.player.hurt_freeze", 1.0, 0.6);
		});
		// 3.0 the crystals shatter as the temporary blocks revert
		t.at(3.0, x -> {
			Random r = x.rng(60);
			for (FxMeshPass.Crystal cr : mine) {
				FxMeshPass.shatter(cr, 4, 4.5, r.nextLong());
			}
			RukiaKit.frostMotes(x, r, 40, c.x + f[0] * L * 0.5, gy + 0.5, c.z + f[1] * L * 0.5, L * 0.35, 1.5, 1.0, 2.0, 20, 0.4, "#FFFFFF", "#BFE4FF", 0.9);
			x.sound("block.glass.break", 1.0, 0.4);
		});
		t.at(3.1, x -> x.sound("block.glass.break", 1.1, 0.4));
	}

	// ============================================================================================================ 3.3 Shirafune

	private static void shirafune(EffectTimeline t) {
		double reach = RukiaFxParams.param(t.params, 3, 8.0);
		Vec3d aim = t.params.length >= 3 ? new Vec3d(t.params[0], t.params[1], t.params[2]) : t.pos.add(t.dir.multiply(reach));
		Vec3d tip0 = RukiaKit.tip(t, 0.9, 0.0);
		Vec3d to = aim.subtract(tip0);
		double dist = to.length();
		Vec3d bd = dist < 0.6 ? (t.dir.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : t.dir.normalize()) : to.multiply(1.0 / dist);
		double U = Math.max(1.5, Math.min(dist, reach + 1.0));
		Vec3d end = tip0.add(bd.multiply(U));
		final FxMeshPass.Crystal[] tipCrystal = new FxMeshPass.Crystal[1];
		t.lifetime(2.4);

		// 0.0 mote swirl converging on the blade tip
		t.at(0.0, x -> {
			Random r = x.rng(70);
			for (int i = 0; i < 30; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double b = (r.nextDouble() - 0.5) * 2;
				double px = tip0.x + Math.cos(a) * 1.0;
				double py = tip0.y + b * 0.5;
				double pz = tip0.z + Math.sin(a) * 1.0;
				FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(px, py, pz).vel((tip0.x - px) / 0.4 - Math.sin(a) * 1.5, (tip0.y - py) / 0.4, (tip0.z - pz) / 0.4 + Math.cos(a) * 1.5).drag(1.0)
						.life(8).size(0.36).seed(i).color("#FFFFFF").colorTo("#9ED3F0").fade(0.4).owner(x).spawn();
			}
			RukiaKit.stars(x, r, GlowSprite.STAR6, 10, tip0.x, tip0.y, tip0.z, 0.6, 0.6, 0.0, 0.0, 0.3, 0.55, "#FFFFFF", 0.9);
			x.sound("block.amethyst_block.resonate", 1.4, 0.6);
		});
		// 0.3 the blade of ice grows along the aim in 0.2 s (easeOutExpo), a crystal rides the tip
		t.at(0.3, x -> {
			dev.minebleach.reiatsutest.ReiatsuTest.LOGGER.info("[fx] shirafune blade tip0={} end={} U={} dir={}", tip0, end, U, bd);
			FxShapes.Strip glow = FxShapes.strip(tip0.x, tip0.y, tip0.z, bd.x, bd.y, bd.z, 0).length(a -> bladeLen(a, U)).width(a -> d("shira.glowWidth", 0.8) * FxMath.clamp(1.0 - (a - 1.7) / 0.2));
			glow.color("#7FB8DF").intensity(a -> d("shira.glow", 0.55) * (0.85 + 0.15 * Math.sin(a * 20))).life(1.95).owner(x);
			FxShapes.Strip core = FxShapes.strip(tip0.x, tip0.y, tip0.z, bd.x, bd.y, bd.z, 0).length(a -> bladeLen(a, U)).width(a -> d("shira.coreWidth", 0.28) * FxMath.clamp(1.0 - (a - 1.7) / 0.2));
			core.color("#EAF8FF").intensity(a -> d("shira.core", 1.0)).life(1.95).owner(x);
			Random r = x.rng(80);
			// a string of soft lights along the blade: visible from every angle (the crossed quads vanish when seen end on)
			for (int i = 0; i < 10; i++) {
				double f = i / 9.0;
				double dd = 1 + (U - 1) * f;
				Vec3d p = tip0.add(bd.multiply(dd));
				FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(p.x, p.y, p.z).size(d("shira.orb", 0.8)).life(1.7 - 0.2 * f * f).delay(0.2 * f * f * 0.5).curve(0.04, 0.15).color("#DFF3FF")
						.peak(0.75).owner(x).spawn();
			}
			for (int i = 0; i < 40; i++) {
				double f = r.nextDouble();
				double dd = 1 + (U - 1) * FxMath.oe(f);
				Vec3d p = tip0.add(bd.multiply(dd));
				FxParticles.spec(FxParticles.Kind.ICE_SHARD).at(p.x + (r.nextDouble() - 0.5) * 0.2, p.y + (r.nextDouble() - 0.5) * 0.2, p.z + (r.nextDouble() - 0.5) * 0.2)
						.vel((r.nextDouble() - 0.5) * 1.5, r.nextDouble() * 1.2, (r.nextDouble() - 0.5) * 1.5).drag(0.93).gravity(0.5).life(22).size(0.26).seed(i).spin(0.2).fade(0.3).owner(x).spawn();
			}
			FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(tip0.x, tip0.y, tip0.z).size(0.6, 1.3).life(0.3).color("#EAF8FF").peak(0.8).owner(x).spawn();
			x.sound("item.trident.throw", 1.1, 0.8);
		});
		t.at(0.46, x -> {
			Vec3d base = end.subtract(bd.multiply(0.85));
			tipCrystal[0] = FxMeshPass.crystal(FxMeshes.CRYSTAL_B, base.x, base.y, base.z, bd.x, bd.y, bd.z, 0.8, d("shira.tipScale", 2.4), 0.0);
			tipCrystal[0].grow = 0.12;
		});
		// 0.6 the thrust arrives: contact burst
		t.at(0.6, x -> {
			Random r = x.rng(90);
			FxGlowBatch.sprite(GlowSprite.FLARE).at(end.x, end.y, end.z).size(d("shira.flare", 3.2)).life(0.18).curve(0.02, 0.7).color("#FFFFFF").peak(0.95).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(end.x, end.y, end.z).size(1.6, 2.6).life(0.3).curve(0.02, 0.7).color("#CFEFFF").peak(0.7).owner(x).spawn();
			RukiaKit.iceShards(x, r, 60, end.x, end.y - 0.3, end.z, 0.3, 4.0, 3.0, 0.8, 24, 0.4);
			RukiaKit.frostMotes(x, r, 40, end.x, end.y, end.z, 0.3, 0.8, 1.5, 4.0, 16, 0.4, "#FFFFFF", "#BFE4FF", 1.0);
			RukiaKit.stars(x, r, GlowSprite.STAR4, 24, end.x, end.y, end.z, 0.3, 0.6, 1.0, 4.0, 0.3, 0.7, "#FFFFFF", 0.95);
			x.shake(0.4, 0.2);
			x.soundAt("entity.player.attack.crit", end, 1.0, 0.9);
			x.soundAt("block.glass.break", end, 1.3, 0.7);
		});
		t.at(1.0, x -> x.soundAt("entity.player.hurt_freeze", end, 1.1, 0.6));
		// 2.0 the blade retracts (0.2 s), the tip crystal shatters
		t.at(2.0, x -> {
			Random r = x.rng(95);
			if (tipCrystal[0] != null) {
				FxMeshPass.shatter(tipCrystal[0], (int) d("shira.tipShards", 6), 4.0, r.nextLong());
			}
			RukiaKit.iceShards(x, r, 30, end.x, end.y, end.z, 0.3, 3.0, 2.0, 1.5, 26, 0.36);
			x.soundAt("block.glass.break", end, 1.4, 0.3);
		});
	}

	/** Blade length: 1 to U in 0.2 s (easeOutExpo), then retract 0.2 s from age 1.7 (t = 2.0). */
	private static double bladeLen(double age, double U) {
		double grow = 1 + (U - 1) * FxMath.oe(age / 0.2);
		if (age > 1.7) {
			grow *= 1.0 - FxMath.clamp((age - 1.7) / 0.2);
		}
		return age < 0 ? 0 : grow;
	}
}
