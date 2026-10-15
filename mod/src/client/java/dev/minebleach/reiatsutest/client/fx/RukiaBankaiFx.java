package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.sound.SoundIds;
import dev.minebleach.reiatsutest.core.fx.RukiaFxParams;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.math.Vec3d;

/**
 * Rukia bankai, Hakka no Togame (VFX_STORYBOARD 5.1): the release (id 2: implosion, the white pillar of light with its
 * mist cap, the snow, the shock ring; the frost ground, crystals, sheen and ribbons are the passive state, see
 * {@link RukiaBankaiClient}). The Absolute zero effect (id 23, 5.4) was removed with the ability in mech2 task 4.
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
		double coreR = d("rb.coreRadius", 0.9);
		double outerR = d("rb.outerRadius", 2.2);
		double implodeR = d("rb.implodeRadius", 2.5);
		double sparkSpeed = d("rb.sparkSpeed", 9.0);
		final ScreenFx.Layer[] vig = new ScreenFx.Layer[1];
		t.lifetime(3.0);

		// 0.00 implosion: motes and star glints are sucked into the chest from radius 2.5 in 0.12 s
		t.at(0.0, x -> {
			Random r = x.rng(1);
			Vec3d chest = pos.add(0, 1.2, 0);
			int nImplode = RukiaKit.count(54);
			for (int i = 0; i < nImplode; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double b = Math.acos(2 * r.nextDouble() - 1);
				double rad = implodeR * (0.7 + 0.3 * r.nextDouble());
				double px = chest.x + Math.sin(b) * Math.cos(a) * rad;
				double py = chest.y + Math.cos(b) * rad;
				double pz = chest.z + Math.sin(b) * Math.sin(a) * rad;
				if (i < 30) {
					FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(px, py, pz).vel((chest.x - px) / 0.12, (chest.y - py) / 0.12, (chest.z - pz) / 0.12).drag(1.0).life(3)
							.size(0.4).color("#FFFFFF").seed(i).fade(0.3).owner(x).spawn();
				} else {
					FxGlowBatch.sprite(GlowSprite.STAR6).at(px, py, pz).vel((chest.x - px) / 0.12, (chest.y - py) / 0.12, (chest.z - pz) / 0.12).drag(1.0).lifeTicks(3).size(0.55)
							.color("#FFFFFF").peak(RukiaKit.glow(0.95)).rot(r.nextDouble() * 6, 3).owner(x).spawn();
				}
			}
			vig[0] = x.vignette(ScreenFx.Kind.FROST, 0.25, 0.1);
			x.sound(SoundIds.RUKIA_BANKAI_RELEASE_GATHER, "block.beacon.power_select", 1.6, 0.6);
			x.sound(SoundIds.RUKIA_BANKAI_RELEASE_GATHER, "block.amethyst_block.resonate", 0.5, 0.8);
		});
		// 0.12 detonation: the white pillar (core r 0.9, outer r 2.2), shock ring, sparks, flash
		t.at(0.12, x -> {
			// the pillar narrows to x0.2 and its bottom lifts away between 1.2 and 2.0 (ease in out sine), then it is gone
			FxShapes.Wall core = FxShapes.wall(pos.x, pos.z, gy, 14, 0).radius(a -> coreR * pillarScale(a)).height(a -> (pillarH - lift(a)) * FxMath.oe(a / 0.18)).y0(a -> gy + lift(a))
					.topFade(0.85).scroll(6.0, 0.12);
			core.color("#FFFFFF").intensity(a -> d("rb.core", 0.8) * FxMath.clamp(a / 0.03) * pillarFade(a)).life(2.0).owner(x).cell(GlowSprite.PILLAR);
			FxShapes.Wall outer = FxShapes.wall(pos.x, pos.z, gy, 14, 0).radius(a -> outerR * pillarScale(a)).height(a -> (pillarH - lift(a)) * FxMath.oe(a / 0.18)).y0(a -> gy + lift(a))
					.topFade(0.5).scroll(4.0, 0.2);
			outer.color("#CFEFFF").intensity(a -> d("rb.outer", 0.15) * FxMath.clamp(a / 0.03) * pillarFade(a)).life(2.0).owner(x).cell(GlowSprite.PILLAR);
			// the hot core of the base: flare and soft disc
			FxGlowBatch.sprite(GlowSprite.FLARE).at(pos.x, gy + 0.5, pos.z).size(d("rb.baseFlare", 9.0)).life(0.55).curve(0.02, 0.75).color("#FFFFFF").peak(RukiaKit.glow(0.95)).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(pos.x, gy + 1.2, pos.z).axisY().size(3.0, 6.0).sizeEase(FxMath.OC).life(0.7).curve(0.03, 0.75).color("#EAF8FF").peak(RukiaKit.glow(0.85)).owner(x).spawn();
			double ry = FxPlace.ringY(pos.x, pos.z, gy, ringR);
			RukiaKit.ring(x, pos.x, ry + 0.06, pos.z, 0.8, ringR, 0.45 + 0.35, "#EAF8FF", 0.85, false);
			RukiaKit.ring(x, pos.x, ry + 0.07, pos.z, 0.8, ringR * 0.9, 0.5, "#FFFFFF", 0.9, true);
			Random r = x.rng(2);
			int nSparks = RukiaKit.count(80);
			for (int i = 0; i < nSparks; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double s = sparkSpeed * (0.6 + 0.4 * r.nextDouble());
				FxGlowBatch.sprite(GlowSprite.STAR4).at(pos.x, gy + 1.0 + r.nextDouble() * 0.8, pos.z).vel(Math.cos(a) * s, 0.5 + r.nextDouble() * 1.5, Math.sin(a) * s).drag(0.86)
						.lifeTicks(14).size(0.35 + r.nextDouble() * 0.5).color("#FFFFFF").peak(RukiaKit.glow(0.95)).twinkle().rot(r.nextDouble() * 6, 2).owner(x).spawn();
			}
			RukiaKit.iceShards(x, r, 30, pos.x, gy + 0.8, pos.z, 0.8, 8.0, 4.0, 0.5, 22, d("rb.shardSize", 0.30));
			x.flash(d("rb.flash", 1.00), "#FFFFFF", false);
			x.shake(d("rb.shake", 1.0), 0.45);
			x.sound(SoundIds.RUKIA_BANKAI_RELEASE_BURST, "block.end_portal.spawn", 1.2, 1.0);
			x.sound(SoundIds.RUKIA_BANKAI_RELEASE_BURST, "entity.warden.sonic_boom", 1.6, 0.5);
			x.sound(SoundIds.RUKIA_BANKAI_RELEASE_BURST, "block.glass.break", 0.6, 0.8);
		});
		// 0.30 the mist cap at y + 18, the mist column and the snow spiral around the pillar
		t.at(0.30, x -> {
			double capY = gy + d("rb.capHeight", 18);
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(pos.x, capY, pos.z).ground().size(2.0, d("rb.capSize", 22)).sizeEase(FxMath.OC).life(1.7).curve(0.12, 0.65).color("#FFFFFF").peak(RukiaKit.glow(0.75)).owner(x)
					.spawn();
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(pos.x, capY, pos.z).ground().size(2.0, d("rb.capGlow", 16)).sizeEase(FxMath.OC).life(1.7).curve(0.12, 0.65).color("#EAF8FF").peak(RukiaKit.glow(0.6)).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.MIST).at(pos.x, capY + 0.1, pos.z).ground().size(4.0, 20).sizeEase(FxMath.OC).life(1.7).curve(0.12, 0.65).color("#FFFFFF").peak(RukiaKit.glow(0.5)).owner(x).spawn();
			for (int i = 0; i < 6; i++) {
				FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(pos.x, gy + 0.5 + i, pos.z).ground().size(2.4 + 0.2 * i, 6.4).sizeEase(FxMath.OC).vel(0, 1.2, 0).life(1.4).curve(0.1, 0.6)
						.rot(i * 0.7, (i % 2 == 0 ? 1 : -1) * 1.5).color("#EAF8FF").peak(d("rb.column", 0.3)).owner(x).spawn();
			}
			Random r = x.rng(3);
			for (int i = 0; i < 30; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double rad = 1.0 + r.nextDouble() * 1.8;
				FxGlowBatch.sprite(GlowSprite.MIST).at(pos.x + Math.cos(a) * rad, gy + r.nextDouble() * 9.0, pos.z + Math.sin(a) * rad).axisY().size(2.5).vel(0, 0.4, 0).life(1.5)
						.curve(0.2, 0.6).color("#EAF8FF").peak(d("rb.mist", 0.12)).owner(x).spawn();
			}
			x.sound(SoundIds.RUKIA_BANKAI_RELEASE_FLOWER, "block.powder_snow.break", 0.8, 0.9);
			x.sound(SoundIds.RUKIA_BANKAI_RELEASE_FLOWER, "item.elytra.flying", 1.4, 0.3);
		});
		t.at(0.38, x -> x.sound(SoundIds.RUKIA_BANKAI_RELEASE_FLOWER, "block.powder_snow.break", 0.9, 0.9));
		t.at(0.46, x -> x.sound(SoundIds.RUKIA_BANKAI_RELEASE_FLOWER, "block.powder_snow.break", 1.0, 0.9));
		for (int k = 0; k < 60; k++) {
			final int kk = k;
			t.at(0.30 + 0.016 * k, x -> {
				double a = Math.PI * 2 * 1.5 * (0.016 * kk) + kk * 0.9;
				double rad = 1.4 + 0.6 * ((kk * 37) % 10) / 10.0;
				FxParticles.spec(FxParticles.Kind.SNOWFLAKE).at(pos.x + Math.cos(a) * rad, gy + 0.3, pos.z + Math.sin(a) * rad)
						.vel(-Math.sin(a) * 1.2, 5.0, Math.cos(a) * 1.2).drag(0.98).life(30).size(d("rb.flake", 0.2)).seed(kk).spin(0.1).alpha(0.95).fade(0.3).owner(x).spawn();
			});
		}
		// 0.60 the ground freezes: the crystals and the decal belong to the passive (RukiaBankaiClient); the sounds and a spray of ice here
		t.at(0.60, x -> {
			Random r = x.rng(4);
			RukiaKit.iceShards(x, r, 24, pos.x, gy + 0.1, pos.z, 1.5, 1.8, 3.0, 0.8, 26, 0.38);
			x.sound(SoundIds.RUKIA_BANKAI_RELEASE_FREEZE, "block.amethyst_block.chime", 1.0, 1.0);
		});
		for (int i = 0; i < 6; i++) {
			final int ii = i;
			t.at(0.60 + 0.06 * i, x -> x.sound(SoundIds.RUKIA_BANKAI_RELEASE_FREEZE, "block.amethyst_cluster.place", 0.9 + 0.08 * ii, 0.7));
		}
		t.at(1.15, x -> x.sound(SoundIds.RUKIA_BANKAI_RELEASE_FREEZE, "block.amethyst_block.chime", 1.15, 1.0));
		t.at(1.5, x -> x.sound(SoundIds.RUKIA_BANKAI_RELEASE_FREEZE, "block.amethyst_block.chime", 1.3, 1.0));
		t.at(0.9, x -> {
			x.sound(SoundIds.RUKIA_BANKAI_RELEASE_WINGS, "item.armor.equip_elytra", 1.5, 0.5);
			x.sound(SoundIds.RUKIA_BANKAI_RELEASE_WINGS, "entity.phantom.flap", 1.6, 0.3);
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
		t.at(1.2, x -> x.sound(SoundIds.RUKIA_BANKAI_RELEASE_AMBIENT, "block.beacon.ambient", 1.5, 0.4));
		// 2.00 settle: the passive owns everything from here
		t.at(2.0, x -> {
			x.sound(SoundIds.RUKIA_BANKAI_RELEASE_SETTLE, "block.amethyst_block.resonate", 0.6, 0.3);
			if (vig[0] != null) {
				vig[0].release(1.0);
			}
		});
		// title card sound for the owner (the card is the HUD's)
		t.at(0.12, x -> {
			if (x.localCaster) {
				x.sound(SoundIds.GENERAL_TITLE_CARD, "block.bell.use", 1.5, 0.4);
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
}
