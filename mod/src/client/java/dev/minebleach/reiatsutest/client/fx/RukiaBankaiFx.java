package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.RukiaFxParams;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.math.Vec3d;

/**
 * Rukia bankai, Hakka no Togame (VFX_STORYBOARD 5.1 and 5.4): the release (id 2: implosion, the white pillar of light with its
 * mist cap, the snow, the shock ring; the frost ground, crystals, sheen and ribbons are the passive state, see
 * {@link RukiaBankaiClient}) and Absolute zero (id 23: cold wave, crystallising air, the absolute stop in which every motion of the
 * effect is paused while the world loses its colour through {@link FreezeDesat}, cracks, the shatter). The radius comes from the
 * event params (the server's size), the length of the pause from params[4]; the shatter of the shells is the server's HIT.
 */
public final class RukiaBankaiFx {
	private RukiaBankaiFx() {
	}

	private static double d(String key, double def) {
		return FxTune.d(key, def);
	}

	// ============================================================================================================ 5.1 release

	public static void release(EffectEventS2C e) {
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		EffectTimeline t = FxTimelines.create(2, e.seed(), pos, dir, e.params(), e.casterId(), FxClient.forceRemote());
		double gy = pos.y;
		double pillarH = d("rb.pillarHeight", 32);
		double ringR = d("rb.ringRadius", 12);
		final ScreenFx.Layer[] vig = new ScreenFx.Layer[1];
		t.lifetime(3.0);

		// 0.00 implosion: motes and star glints are sucked into the chest from radius 2.5 in 0.12 s
		t.at(0.0, x -> {
			Random r = x.rng(1);
			Vec3d chest = pos.add(0, 1.2, 0);
			for (int i = 0; i < 54; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double b = Math.acos(2 * r.nextDouble() - 1);
				double rad = 2.5 * (0.7 + 0.3 * r.nextDouble());
				double px = chest.x + Math.sin(b) * Math.cos(a) * rad;
				double py = chest.y + Math.cos(b) * rad;
				double pz = chest.z + Math.sin(b) * Math.sin(a) * rad;
				if (i < 30) {
					FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(px, py, pz).vel((chest.x - px) / 0.12, (chest.y - py) / 0.12, (chest.z - pz) / 0.12).drag(1.0).life(3)
							.size(0.4).color("#FFFFFF").seed(i).fade(0.3).owner(x).spawn();
				} else {
					FxGlowBatch.sprite(GlowSprite.STAR6).at(px, py, pz).vel((chest.x - px) / 0.12, (chest.y - py) / 0.12, (chest.z - pz) / 0.12).drag(1.0).lifeTicks(3).size(0.55)
							.color("#FFFFFF").peak(0.95).rot(r.nextDouble() * 6, 3).owner(x).spawn();
				}
			}
			vig[0] = x.vignette(ScreenFx.Kind.FROST, 0.25, 0.1);
			x.sound("block.beacon.power_select", 1.6, 0.6);
			x.sound("block.amethyst_block.resonate", 0.5, 0.8);
		});
		// 0.12 detonation: the white pillar (core r 0.9, outer r 2.2), shock ring, sparks, flash
		t.at(0.12, x -> {
			// the pillar narrows to x0.2 and its bottom lifts away between 1.2 and 2.0 (ease in out sine), then it is gone
			FxShapes.Wall core = FxShapes.wall(pos.x, pos.z, gy, 14, 0).radius(a -> 0.9 * pillarScale(a)).height(a -> (pillarH - lift(a)) * FxMath.oe(a / 0.18)).y0(a -> gy + lift(a))
					.topFade(0.85).scroll(6.0, 0.12);
			core.color("#FFFFFF").intensity(a -> d("rb.core", 1.0) * FxMath.clamp(a / 0.03) * pillarFade(a)).life(2.0).owner(x).cell(GlowSprite.PILLAR);
			FxShapes.Wall outer = FxShapes.wall(pos.x, pos.z, gy, 14, 0).radius(a -> 2.2 * pillarScale(a)).height(a -> (pillarH - lift(a)) * FxMath.oe(a / 0.18)).y0(a -> gy + lift(a))
					.topFade(0.5).scroll(4.0, 0.2);
			outer.color("#CFEFFF").intensity(a -> d("rb.outer", 0.45) * FxMath.clamp(a / 0.03) * pillarFade(a)).life(2.0).owner(x).cell(GlowSprite.PILLAR);
			// the hot core of the base: flare and soft disc
			FxGlowBatch.sprite(GlowSprite.FLARE).at(pos.x, gy + 0.5, pos.z).size(d("rb.baseFlare", 9.0)).life(0.55).curve(0.02, 0.75).color("#FFFFFF").peak(0.95).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(pos.x, gy + 1.2, pos.z).axisY().size(3.0, 6.0).sizeEase(FxMath.OC).life(0.7).curve(0.03, 0.75).color("#EAF8FF").peak(0.85).owner(x).spawn();
			RukiaKit.ring(x, pos.x, gy + 0.06, pos.z, 0.8, ringR, 0.45 + 0.35, "#EAF8FF", 0.85, false);
			RukiaKit.ring(x, pos.x, gy + 0.07, pos.z, 0.8, ringR * 0.9, 0.5, "#FFFFFF", 0.9, true);
			Random r = x.rng(2);
			for (int i = 0; i < 80; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double s = 9.0 * (0.6 + 0.4 * r.nextDouble());
				FxGlowBatch.sprite(GlowSprite.STAR4).at(pos.x, gy + 1.0 + r.nextDouble() * 0.8, pos.z).vel(Math.cos(a) * s, 0.5 + r.nextDouble() * 1.5, Math.sin(a) * s).drag(0.86)
						.lifeTicks(14).size(0.35 + r.nextDouble() * 0.5).color("#FFFFFF").peak(0.95).twinkle().rot(r.nextDouble() * 6, 2).owner(x).spawn();
			}
			RukiaKit.iceShards(x, r, 30, pos.x, gy + 0.8, pos.z, 0.8, 8.0, 4.0, 0.5, 22, 0.48);
			x.flash(1.00, "#FFFFFF", false);
			x.shake(1.0, 0.45);
			x.sound("block.end_portal.spawn", 1.2, 1.0);
			x.sound("entity.warden.sonic_boom", 1.6, 0.5);
			x.sound("block.glass.break", 0.6, 0.8);
		});
		// 0.30 the mist cap at y + 18, the mist column and the snow spiral around the pillar
		t.at(0.30, x -> {
			double capY = gy + d("rb.capHeight", 18);
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(pos.x, capY, pos.z).ground().size(2.0, d("rb.capSize", 22)).sizeEase(FxMath.OC).life(1.7).curve(0.12, 0.65).color("#FFFFFF").peak(0.75).owner(x)
					.spawn();
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(pos.x, capY, pos.z).ground().size(2.0, d("rb.capGlow", 16)).sizeEase(FxMath.OC).life(1.7).curve(0.12, 0.65).color("#EAF8FF").peak(0.6).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.MIST).at(pos.x, capY + 0.1, pos.z).ground().size(4.0, 20).sizeEase(FxMath.OC).life(1.7).curve(0.12, 0.65).color("#FFFFFF").peak(0.5).owner(x).spawn();
			for (int i = 0; i < 6; i++) {
				FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(pos.x, gy + 0.5 + i, pos.z).ground().size(2.4 + 0.2 * i, 6.4).sizeEase(FxMath.OC).vel(0, 1.2, 0).life(1.4).curve(0.1, 0.6)
						.rot(i * 0.7, (i % 2 == 0 ? 1 : -1) * 1.5).color("#EAF8FF").peak(0.45).owner(x).spawn();
			}
			Random r = x.rng(3);
			for (int i = 0; i < 30; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double rad = 1.0 + r.nextDouble() * 1.8;
				FxGlowBatch.sprite(GlowSprite.MIST).at(pos.x + Math.cos(a) * rad, gy + r.nextDouble() * 9.0, pos.z + Math.sin(a) * rad).axisY().size(2.5).vel(0, 0.4, 0).life(1.5)
						.curve(0.2, 0.6).color("#EAF8FF").peak(0.2).owner(x).spawn();
			}
			x.sound("block.powder_snow.break", 0.8, 0.9);
			x.sound("item.elytra.flying", 1.4, 0.3);
		});
		t.at(0.38, x -> x.sound("block.powder_snow.break", 0.9, 0.9));
		t.at(0.46, x -> x.sound("block.powder_snow.break", 1.0, 0.9));
		for (int k = 0; k < 60; k++) {
			final int kk = k;
			t.at(0.30 + 0.016 * k, x -> {
				double a = Math.PI * 2 * 1.5 * (0.016 * kk) + kk * 0.9;
				double rad = 1.4 + 0.6 * ((kk * 37) % 10) / 10.0;
				FxParticles.spec(FxParticles.Kind.SNOWFLAKE).at(pos.x + Math.cos(a) * rad, gy + 0.3, pos.z + Math.sin(a) * rad)
						.vel(-Math.sin(a) * 1.2, 5.0, Math.cos(a) * 1.2).drag(0.98).life(30).size(0.3).seed(kk).spin(0.1).alpha(0.95).fade(0.3).owner(x).spawn();
			});
		}
		// 0.60 the ground freezes: the crystals and the decal belong to the passive (RukiaBankaiClient); the sounds and a spray of ice here
		t.at(0.60, x -> {
			Random r = x.rng(4);
			RukiaKit.iceShards(x, r, 24, pos.x, gy + 0.1, pos.z, 1.5, 1.8, 3.0, 0.8, 26, 0.38);
			x.sound("block.amethyst_block.chime", 1.0, 1.0);
		});
		for (int i = 0; i < 6; i++) {
			final int ii = i;
			t.at(0.60 + 0.06 * i, x -> x.sound("block.amethyst_cluster.place", 0.9 + 0.08 * ii, 0.7));
		}
		t.at(1.15, x -> x.sound("block.amethyst_block.chime", 1.15, 1.0));
		t.at(1.5, x -> x.sound("block.amethyst_block.chime", 1.3, 1.0));
		t.at(0.9, x -> {
			x.sound("item.armor.equip_elytra", 1.5, 0.5);
			x.sound("entity.phantom.flap", 1.6, 0.3);
		});
		// 1.20 the pillar narrows and lifts away, the snow falls out of it
		for (int k = 0; k < 8; k++) {
			final int kk = k;
			t.at(1.2 + 0.1 * k, x -> {
				Random r = x.rng(50 + kk);
				for (int i = 0; i < 15; i++) {
					double a = r.nextDouble() * Math.PI * 2;
					double rad = Math.sqrt(r.nextDouble()) * 1.6;
					double y = gy + 4 + r.nextDouble() * 24;
					FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(pos.x + Math.cos(a) * rad, y, pos.z + Math.sin(a) * rad).vel(0, -2.5 - r.nextDouble() * 2, 0).drag(0.99).life(40)
							.size(0.36).color("#FFFFFF").colorTo("#BFE4FF").seed(i).fade(0.4).owner(x).spawn();
					if (i % 3 == 0) {
						FxParticles.spec(FxParticles.Kind.SNOWFLAKE).at(pos.x + Math.cos(a) * rad * 1.5, y, pos.z + Math.sin(a) * rad * 1.5).vel(0, -1.8, 0).drag(0.99).life(60).size(0.28)
								.seed(i).spin(0.08).alpha(0.95).fade(0.2).owner(x).spawn();
					}
				}
			});
		}
		t.at(1.2, x -> x.sound("block.beacon.ambient", 1.5, 0.4));
		// 2.00 settle: the passive owns everything from here
		t.at(2.0, x -> {
			x.sound("block.amethyst_block.resonate", 0.6, 0.3);
			if (vig[0] != null) {
				vig[0].release(1.0);
			}
		});
		// title card sound for the owner (the card is the HUD's)
		t.at(0.12, x -> {
			if (x.localCaster) {
				x.sound("block.bell.use", 1.5, 0.4);
			}
		});
		FxTimelines.start(t);
	}

	/** Pillar radius factor: 1, then 1 to 0.2 between t = 1.2 and 2.0 (age counted from t = 0.12). */
	private static double pillarScale(double age) {
		double t = age + 0.12;
		return 1.0 - 0.8 * FxMath.ios((t - 1.2) / 0.8);
	}

	/** Height the bottom of the pillar has lifted (0 to 20 blocks between t = 1.2 and 2.0). */
	private static double lift(double age) {
		double t = age + 0.12;
		return 20.0 * FxMath.ios((t - 1.2) / 0.8);
	}

	private static double pillarFade(double age) {
		double t = age + 0.12;
		return 1.0 - FxMath.clamp((t - 1.65) / 0.4);
	}

	// ============================================================================================================ 5.4 Absolute zero

	public static void absoluteZero(EffectEventS2C e) {
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		EffectTimeline t = FxTimelines.create(23, e.seed(), pos, dir, e.params(), e.casterId(), FxClient.forceRemote());
		double R = RukiaFxParams.param(t.params, 3, 10.0);
		double pause = RukiaFxParams.param(t.params, 4, RukiaFxParams.ABSOLUTE_ZERO_PAUSE_TICKS / 20.0);
		double stop = 2.0;
		double shatter = stop + pause;
		double gy = pos.y;
		double sc = Math.max(0.7, R / 10.0);
		List<FxMeshPass.Crystal> mine = new ArrayList<>();
		final ScreenFx.Layer[] vigs = new ScreenFx.Layer[3];
		final ScreenFx.Layer[] grade = new ScreenFx.Layer[1];
		double strength = t.strength;
		t.lifetime(7.0);

		// 0.00 the blade is raised: gather at the tip
		t.at(0.0, x -> {
			Vec3d tip = RukiaKit.tip(x, 1.0, 0.3);
			Random r = x.rng(1);
			for (int i = 0; i < 40; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double b = (r.nextDouble() - 0.5) * 2.4;
				double rad = 1.2 + r.nextDouble() * 0.8;
				double px = tip.x + Math.cos(a) * rad;
				double py = tip.y + b;
				double pz = tip.z + Math.sin(a) * rad;
				FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(px, py, pz).vel((tip.x - px) / 0.35, (tip.y - py) / 0.35, (tip.z - pz) / 0.35).drag(1.0).life(7).size(0.34)
						.color("#FFFFFF").colorTo("#9ED3F0").seed(i).fade(0.4).owner(x).spawn();
				if (i < 16) {
					FxGlowBatch.sprite(GlowSprite.STAR4).at(px, py, pz).vel((tip.x - px) / 0.35, (tip.y - py) / 0.35, (tip.z - pz) / 0.35).drag(1.0).lifeTicks(7).size(0.5)
							.color("#FFFFFF").peak(0.9).rot(r.nextDouble() * 6, 2).owner(x).spawn();
				}
			}
			FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(tip.x, tip.y, tip.z).size(0.5, 1.5).sizeEase(FxMath.OC).life(0.5).curve(0.1, 0.4).color("#CFEFFF").peak(0.95).owner(x).spawn();
			vigs[0] = x.vignette(ScreenFx.Kind.FROST, 0.15, 0.3);
			x.sound("block.beacon.power_select", 0.8, 0.7);
			x.sound("block.amethyst_block.resonate", 0.5, 0.8);
		});
		// 0.40 the cold wave: ring, frost wall at the front, the ground freezing behind it, mist, motes
		t.at(0.40, x -> {
			Random r = x.rng(2);
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(pos.x, gy + 0.06, pos.z).ground().size(1.0, R / 0.375).sizeEase(FxMath.OC).life(0.9).curve(0.02, 0.6).color("#EAF8FF").peak(0.95).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(pos.x, gy + 0.07, pos.z).ground().size(1.0, R / 0.4375).sizeEase(FxMath.OC).life(0.7).curve(0.02, 0.6).delay(0.1).color("#FFFFFF").peak(0.85)
					.owner(x).spawn();
			FxShapes.Wall wall = FxShapes.wall(pos.x, pos.z, gy, 28, 0).radius(a -> Math.max(0.2, R * FxMath.oc(a / 0.6))).height(a -> 2.6 * sc).topFade(0.0).scroll(3, 0.25);
			wall.color("#CFEFFF").intensity(a -> d("az.wall", 0.5) * FxMath.clamp(a / 0.05) * (1 - FxMath.clamp((a - 0.6) / 0.5))).life(1.3).owner(x).cell(GlowSprite.PILLAR);
			FxDecals.frost(pos.x, pos.z, gy, R * 1.05, 0).reveal(0.6).erase(shatter + 0.3 - 0.4, 3.0).alpha(d("az.decal", 0.9));
			for (int i = 0; i < 60; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double rad = R * Math.sqrt(r.nextDouble());
				FxGlowBatch.sprite(GlowSprite.MIST).at(pos.x + Math.cos(a) * rad, gy + 0.3, pos.z + Math.sin(a) * rad).ground().rot(r.nextDouble() * 6.28, 0.05).size(2.5 + r.nextDouble() * 1.5)
						.life(4.0).delay(0.6 * rad / R).curve(0.5, 0.6).color("#EAF8FF").peak(d("az.mist", 0.16)).owner(x).spawn();
			}
			for (int i = 0; i < 120; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double rad = R * Math.sqrt(r.nextDouble());
				double delay = 0.6 * rad / R;
				FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(pos.x + Math.cos(a) * rad, gy + 0.1, pos.z + Math.sin(a) * rad).vel(Math.cos(a) * 0.8, 1.0 + r.nextDouble() * 1.5, Math.sin(a) * 0.8)
						.drag(0.97).life(24 + r.nextInt(10)).size(0.4).color("#FFFFFF").colorTo("#BFE4FF").seed(i).fade(0.4).owner(x).spawn();
			}
			x.flash(0.50, "#EAF8FF", true);
			x.shake(0.5, 0.3);
			x.sound("entity.warden.sonic_boom", 0.5, 0.8);
			x.sound("item.trident.riptide_3", 0.7, 0.6);
			x.sound("block.powder_snow.break", 0.6, 0.8);
		});
		// 1.00 the air crystallises: hanging shards, eight crystals, desaturation begins
		t.at(1.00, x -> {
			Random r = x.rng(3);
			for (int i = 0; i < 80; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double rad = R * Math.sqrt(r.nextDouble());
				FxParticles.spec(FxParticles.Kind.ICE_SHARD).at(pos.x + Math.cos(a) * rad, gy + 0.3 + r.nextDouble() * 3.7, pos.z + Math.sin(a) * rad)
						.vel((r.nextDouble() - 0.5) * 0.3, 0.2 + r.nextDouble() * 0.1, (r.nextDouble() - 0.5) * 0.3).drag(0.98).life(110).size(0.42 + 0.3 * r.nextDouble()).seed(i)
						.spin((r.nextDouble() - 0.5) * 0.04).alpha(0.95).fade(0.1).owner(x).spawn();
			}
			RukiaKit.stars(x, r, GlowSprite.STAR6, 30, pos.x, gy + 0.3, pos.z, R, 4.0, 0.2, 0.0, 2.4, 0.9, "#FFFFFF", 0.85);
			vigs[1] = x.vignette(ScreenFx.Kind.FROST, 0.35, 1.0);
			FreezeDesat.set(0.25 * strength, 1.0);
			x.sound("block.large_amethyst_bud.break", 0.7, 0.5);
		});
		String[] meshes = {FxMeshes.CRYSTAL_B, FxMeshes.CRYSTAL_C, FxMeshes.CRYSTAL_D, FxMeshes.CRYSTAL_B, FxMeshes.CRYSTAL_C, FxMeshes.CRYSTAL_D, FxMeshes.CRYSTAL_B, FxMeshes.CRYSTAL_C};
		double[] scales = {3.2, 2.6, 2.0, 3.0, 2.8, 1.8, 3.4, 2.4};
		for (int i = 0; i < 8; i++) {
			final int ii = i;
			t.at(1.0 + 0.05 * i, x -> {
				Random r = x.rng(10 + ii);
				// seeded points at r 3 to 9, never within 2.5 of the caster
				double a = (ii + r.nextDouble() * 0.6) / 8 * Math.PI * 2;
				double rad = Math.min(R * 0.9, 3.0 + 6.0 * r.nextDouble()) * sc * 0.9 + 0.3;
				double px = pos.x + Math.cos(a) * rad;
				double pz = pos.z + Math.sin(a) * rad;
				double py = FxGround.sample(px, pz, gy);
				double lean = 0.1 + r.nextDouble() * 0.25;
				FxMeshPass.Crystal c = FxMeshPass.crystal(meshes[ii], px, py - 0.04, pz, Math.cos(a) * lean, 1, Math.sin(a) * lean, r.nextDouble() * 6.28, scales[ii] * sc * d("az.crystalScale", 1.0), 0);
				c.grow = 0.5;
				c.emissive = 0.8;
				mine.add(c);
				RukiaKit.iceShards(x, r, 6, px, py + 0.1, pz, 0.5, 1.5, 2.5, 0.6, 24, 0.36);
				RukiaKit.frostMotes(x, r, 8, px, py + 0.1, pz, 0.6, 0.3, 1.5, 1.0, 16, 0.36, "#FFFFFF", "#BFE4FF", 0.9);
				x.soundAt("block.amethyst_cluster.place", new Vec3d(px, py + 0.5, pz), 0.8 + 0.4 * ii / 7.0, 0.6);
			});
		}
		// 2.00 the absolute stop: all motion of the effect pauses, the colour leaves the world
		t.at(stop, x -> {
			x.pause(pause * 1000.0);
			x.duck(stop, stop + 1.0, 0.0);
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(pos.x, gy + 0.08, pos.z).ground().size(1.0, 14.0 * sc / 0.4375).sizeEase(FxMath.OC).life(0.5).curve(0.02, 0.7).color("#FFFFFF").peak(0.8).spawn();
			FreezeDesat.set(0.45 * strength, 0.1);
			grade[0] = x.grade("#C8DCF0", 0.5, 0.1);
			vigs[2] = x.vignette(ScreenFx.Kind.DARK, 0.25, 0.2);
			x.sound("block.beacon.deactivate", 2.0, 0.4);
			x.sound("block.amethyst_block.resonate", 2.0, 0.35);
		});
		// 3.00 cracks flicker on every shell and crystal
		t.at(stop + 1.0, x -> {
			Random r = x.rng(30);
			Vec3d cam = net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
			List<Vec3d> centers = new ArrayList<>();
			List<Double> heights = new ArrayList<>();
			for (FxMeshPass.Crystal c : mine) {
				c.emissive = 1.0;
				centers.add(c.mid());
				heights.add(c.height());
			}
			for (FxMeshPass.Shell s : FxMeshPass.shells()) {
				if (s.encased) {
					centers.add(s.center());
					heights.add(s.height());
				}
			}
			for (int i = 0; i < centers.size(); i++) {
				Vec3d c = centers.get(i);
				double nx = cam.x - c.x;
				double nz = cam.z - c.z;
				FxGlowBatch.sprite(GlowSprite.CRACK).at(c.x, c.y, c.z).plane(nx, 0, nz).size(heights.get(i) * 1.1).life(0.3).curve(0.01, 0.4).rot(r.nextDouble() * 6.28, 0).color("#FFFFFF").peak(0.9)
						.twinkle().spawn();
			}
			for (int i = 0; i < 40; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double rad = R * Math.sqrt(r.nextDouble());
				FxParticles.spec(FxParticles.Kind.ICE_SHARD).at(pos.x + Math.cos(a) * rad, gy + 0.3 + r.nextDouble() * 3.5, pos.z + Math.sin(a) * rad).vel(0, 0.1, 0).drag(0.98).life(9).size(0.3)
						.seed(i).alpha(0.9).fade(0.4).spawn();
			}
			x.sound("block.glass.break", 0.8, 1.0);
			x.sound("block.amethyst_cluster.break", 0.6, 0.7);
		});
		// 3.30 the shatter (the server's damage): hit stop 60 ms, then everything bursts and the colour floods back
		t.at(shatter, x -> x.hitstop(60));
		t.at(shatter + 0.0001, x -> {
			Random r = x.rng(40);
			for (FxMeshPass.Crystal c : mine) {
				FxMeshPass.shatter(c, 8, 8.0, r.nextLong());
			}
			FreezeDesat.set(0.0, 0.25);
			if (grade[0] != null) {
				grade[0].release(0.25);
			}
			if (vigs[2] != null) {
				vigs[2].release(0.25);
			}
			if (vigs[1] != null) {
				vigs[1].release(0.7);
			}
			if (vigs[0] != null) {
				vigs[0].release(0.7);
			}
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(pos.x, gy + 0.09, pos.z).ground().size(1.0, 12.0 * sc / 0.375).sizeEase(FxMath.OC).life(0.6).curve(0.02, 0.6).color("#FFFFFF").peak(0.95)
					.spawn();
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(pos.x, gy + 1.0, pos.z).ground().size(2.0, R * 2.4).sizeEase(FxMath.OC).life(0.5).curve(0.02, 0.7).color("#EAF8FF").peak(0.7).spawn();
			RukiaKit.iceShards(x, r, 260, pos.x, gy + 0.4, pos.z, R * 0.9, 8.0, 5.0, 1.0, 26, 0.46);
			RukiaKit.frostMotes(x, r, 160, pos.x, gy + 0.6, pos.z, R * 0.9, 3.0, 2.5, 6.0, 22, 0.44, "#FFFFFF", "#BFE4FF", 1.0);
			RukiaKit.stars(x, r, GlowSprite.STAR4, 120, pos.x, gy + 1.2, pos.z, R * 0.9, 3.0, 3.0, 6.0, 0.45, 0.9, "#FFFFFF", 0.95);
			x.flash(0.35, "#FFFFFF", false);
			x.shake(1.0, 0.5);
			x.sound("block.glass.break", 0.7, 1.0);
			x.sound("entity.generic.explode", 1.4, 0.5);
			x.sound("item.trident.thunder", 1.8, 0.3);
			x.sound("block.amethyst_block.break", 1.0, 0.8);
			// the shells the server did not report (target gone before the hit): break them here
			for (FxMeshPass.Shell s : new ArrayList<>(FxMeshPass.shells())) {
				if (s.encased) {
					FxMeshPass.breakShell(s.entityId, 12, 8.0);
				}
			}
		});
		double[] gl = {0.05, 0.10, 0.17, 0.25};
		double[] glp = {0.85, 1.0, 1.15, 1.3};
		for (int i = 0; i < 4; i++) {
			final int ii = i;
			t.at(shatter + gl[i], x -> x.sound("block.glass.break", glp[ii], 0.9));
		}
		t.at(shatter + 0.12, x -> x.sound("block.amethyst_block.break", 0.9, 0.7));
		t.at(shatter + 0.2, x -> x.sound("block.amethyst_block.break", 1.1, 0.7));
		// 3.60 to 7.00 aftermath: falling glitter, a last glints, the melt
		for (int k = 0; k < 8; k++) {
			final int kk = k;
			t.at(shatter + 0.3 + 0.25 * k, x -> {
				Random r = x.rng(60 + kk);
				RukiaKit.iceShards(x, r, 5, pos.x, gy + 3.5, pos.z, R * 0.8, 0.4, 0.0, 0.25, 40, 0.28);
				int n = Math.max(0, (int) Math.round(5 * (1 - kk / 8.0)));
				RukiaKit.stars(x, r, GlowSprite.STAR6, n, pos.x, gy + 0.4, pos.z, R * 0.8, 3.0, 0.2, 0.0, 0.8, 0.6, "#FFFFFF", 0.8);
			});
		}
		t.at(shatter + 0.7, x -> x.sound("block.powder_snow.break", 0.8, 0.4));
		t.at(shatter + 1.7, x -> x.sound("block.fire.extinguish", 1.8, 0.15));
		FxTimelines.start(t);
		// the sheen of her feature renderer flares with the cast
		RukiaBankaiClient.flare(e.casterId());
	}
}
