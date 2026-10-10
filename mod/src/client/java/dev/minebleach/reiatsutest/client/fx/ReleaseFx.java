package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Release flash, shockwave ring and burst of the four release events (VFX_STORYBOARD 2.1, ids 1 to 4). Rukia: ice white
 * (flash, mote burst, a light ring that follows the blade hand); Byakuya: lilac (petal burst, glow petals). The bankai
 * variants (2, 4) only add the common part here (ring 10 blocks, double burst; Byakuya's has no white flash, his release
 * is a quiet drop): their own sequences are the bankai steps. Everything is derived from the event, the seed and the
 * timeline clock, so every client draws the same release.
 */
public final class ReleaseFx {
	private static final String RUKIA_FLASH = "#DDF3FF";
	private static final String BYAKUYA_FLASH = "#F3E4FF";
	/** The ground rings are tinted more saturated than the flash: additive white on grass turns lime, a blue or pink body keeps the hue (the thin ring on top stays white). */
	private static final String RUKIA_RING = "#8CCBF2";
	private static final String BYAKUYA_RING = "#E8A0F0";

	private ReleaseFx() {
	}

	public static void play(EffectEventS2C e) {
		int id = e.effectId();
		boolean rukia = id == 1 || id == 2;
		boolean bankai = id == 2 || id == 4;
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		EffectTimeline t = FxTimelines.create(id, e.seed(), pos, dir, e.params(), e.casterId(), FxClient.forceRemote());
		String tint = rukia ? RUKIA_FLASH : BYAKUYA_FLASH;
		double ringR = FxTune.d("release.ringRadius", bankai ? 10 : 5);
		double speed = FxTune.d("release.burstSpeed", bankai ? 8 : 6);
		int count = (int) FxTune.d("release.burstCount", (bankai ? 2 : 1) * 72);

		// 0.00: flash, bell of the release
		t.at(0.0, x -> {
			if (id == 1) {
				x.flash(FxTune.d("release.flashRukiaShikai", 0.60), FxTune.s("release.rukiaFlashColor", RUKIA_FLASH), false);
			} else if (id == 2) {
				x.flash(1.00, RUKIA_FLASH, false);
			} else if (id == 3) {
				x.flash(0.40, BYAKUYA_FLASH, false);
			}
			if (rukia) {
				x.sound("block.beacon.activate", bankai ? 0.9 : 1.2, bankai ? 0.9 : 0.7);
			} else if (bankai) {
				x.sound("item.trident.return", 0.6, 0.5);
			} else {
				x.sound("block.beacon.activate", 1.4, 0.6);
				x.sound("entity.illusioner.cast_spell", 1.2, 0.5);
			}
			bloom(x, tint, bankai ? 5.5 : 3.2);
		});
		// 0.05: the ground ring (two rings, the thin one a little behind), then a few streaks of light
		t.at(0.05, x -> {
			new Ring(x, FxTune.s("release.ringTint", rukia ? RUKIA_RING : BYAKUYA_RING), ringR, 0.0).soft();
			x.sound("entity.generic.explode", bankai ? 1.3 : 1.6, bankai ? 0.6 : 0.5);
		});
		t.at(0.11, x -> new Ring(x, tint, ringR * 0.85, 0.0).thin());
		// 0.10: burst from the chest
		t.at(0.10, x -> {
			burst(x, rukia, tint, speed, count);
			x.shake(bankai ? 0.55 : 0.4, 0.3);
			x.sound("entity.player.attack.sweep", 1.0, 0.7);
			streaks(x, tint, bankai ? 20 : 14);
		});
		// 0.20 to 0.50: Rukia: a vertical light ring around the blade, following the hand
		if (rukia) {
			t.at(0.20, x -> x.sound("block.amethyst_block.chime", 1.3, 0.6));
			for (int k = 0; k < 8; k++) {
				double at = 0.20 + 0.04 * k;
				final double progress = k / 7.0;
				t.at(at, x -> handRing(x, progress));
			}
		}
		// title card sound (the card itself is the HUD's), caster only
		t.at(0.12, x -> {
			if (x.localCaster) {
				x.sound("block.bell.use", 1.5, 0.4);
			}
		});
		t.lifetime(1.0);
		FxTimelines.start(t);
	}

	// ------------------------------------------------------------------------------------------ parts

	private static void bloom(EffectTimeline t, String tint, double size) {
		Vec3d c = t.pos.add(0, 1.1, 0);
		// anamorphic flare across the chest and a short column of light: the anime "something just happened" shape, readable on a bright sky too
		FxGlowBatch.sprite(GlowSprite.FLARE).at(c.x, c.y, c.z).size(size * 2.4, size * 3.2).sizeEase(FxMath.OC).life(0.32).curve(0.02, 0.8).color(tint)
				.peak(FxTune.d("release.flare", 0.8)).owner(t).spawn();
		// the column: the flare cell turned upright and facing the camera (a soft ended beam, no hard pillar edges)
		Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		FxGlowBatch.sprite(GlowSprite.FLARE).at(t.pos.x, t.pos.y + size * 1.1, t.pos.z).plane(cam.x - t.pos.x, 0, cam.z - t.pos.z).rot(Math.PI / 2, 0)
				.size(size * 1.8, size * 2.4).sizeEase(FxMath.OC).life(0.45).curve(0.03, 0.75).color(tint).peak(FxTune.d("release.pillar", 0.75)).owner(t).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(c.x, c.y, c.z).size(size * 0.6, size).sizeEase(FxMath.OC).life(0.5).curve(0.03, 0.8)
				.color(tint).peak(0.9).owner(t).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(c.x, c.y, c.z).size(size * 0.3, size * 0.55).sizeEase(FxMath.OC).life(0.28).curve(0.02, 0.8)
				.color("#FFFFFF").peak(0.85).owner(t).spawn();
	}

	private static final class Ring {
		final EffectTimeline t;
		final String tint;
		final double radius;
		final double yOff;

		Ring(EffectTimeline t, String tint, double radius, double yOff) {
			this.t = t;
			this.tint = tint;
			this.radius = radius;
			this.yOff = yOff;
		}

		void soft() {
			// RING_SOFT draws its ring at 0.375 of its width
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(t.pos.x, t.pos.y + 0.05 + yOff, t.pos.z).ground().size(0.6, radius / 0.375).sizeEase(FxMath.OC)
					.life(0.55).curve(0.02, 0.85).color(tint).peak(1.0).owner(t).spawn();
		}

		void thin() {
			// RING_THIN draws its ring at 0.4375 of its width
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(t.pos.x, t.pos.y + 0.06 + yOff, t.pos.z).ground().size(0.5, radius / 0.4375).sizeEase(FxMath.OC)
					.life(0.55).curve(0.02, 0.85).color("#FFFFFF").peak(0.9).owner(t).spawn();
		}
	}

	private static void burst(EffectTimeline t, boolean rukia, String tint, double speed, int count) {
		Random r = t.rng(7);
		Vec3d c = t.pos.add(0, 1.15, 0);
		FxParticles.Kind kind = rukia ? FxParticles.Kind.FROST_MOTE : FxParticles.Kind.PETAL;
		String col = rukia ? RUKIA_FLASH : "#E5A4DC";
		for (int i = 0; i < count; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double s = speed * (0.55 + 0.45 * r.nextDouble());
			double up = Math.sin(Math.toRadians(15)) * s + r.nextGaussian() * 0.5;
			double hx = Math.cos(a) * s;
			double hz = Math.sin(a) * s;
			FxParticles.Spec p = FxParticles.spec(kind).at(c.x + Math.cos(a) * 0.3, c.y + (r.nextDouble() - 0.5) * 0.5, c.z + Math.sin(a) * 0.3)
					.vel(hx, up, hz).drag(0.9).life(20).size(FxTune.d("release.moteSize", rukia ? 0.42 : 0.30)).seed(r.nextInt(64)).owner(t);
			if (rukia) {
				p.color(col).colorTo(FxTune.s("release.moteEnd", "#CFEFFF"));
			} else {
				p.color(col).colorTo("#F9C8F6").spin((r.nextDouble() - 0.5) * 0.4).gravity(0.02);
			}
			p.spawn();
		}
		// Rukia: ice shards flying out (a lit alpha sprite: the part of the burst that stays visible on a bright sky) and a few slow snowflakes
		if (rukia) {
			int shards = (int) FxTune.d("release.shards", 16);
			for (int i = 0; i < shards; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double s = speed * (0.55 + 0.5 * r.nextDouble());
				FxParticles.spec(FxParticles.Kind.ICE_SHARD).at(c.x + Math.cos(a) * 0.3, c.y + (r.nextDouble() - 0.5) * 0.4, c.z + Math.sin(a) * 0.3)
						.vel(Math.cos(a) * s, 1.0 + r.nextDouble() * 2.0, Math.sin(a) * s).drag(0.9).gravity(0.5).life(24).size(0.34 + r.nextDouble() * 0.2)
						.spin((r.nextDouble() - 0.5) * 0.5).seed(r.nextInt(4)).fade(0.3).owner(t).spawn();
			}
			for (int i = 0; i < 8; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double s = 1.0 + r.nextDouble() * 2.5;
				FxParticles.spec(FxParticles.Kind.SNOWFLAKE).at(c.x, c.y + (r.nextDouble() - 0.5) * 0.4, c.z).vel(Math.cos(a) * s, 0.5 + r.nextDouble() * 0.6, Math.sin(a) * s)
						.drag(0.93).gravity(0.02).life(28).size(0.2).spin((r.nextDouble() - 0.5) * 0.2).seed(r.nextInt(3)).owner(t).spawn();
			}
		}
		// sparkle accents: 12 star4 (Byakuya: glowing petals) that twinkle and drift outward
		for (int i = 0; i < 12; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double s = 2.0 + r.nextDouble() * 2.5;
			FxGlowBatch.Spec g = FxGlowBatch.sprite(rukia ? GlowSprite.STAR4 : GlowSprite.PETAL_GLOW)
					.at(c.x + Math.cos(a) * 0.5, c.y + (r.nextDouble() - 0.3) * 0.7, c.z + Math.sin(a) * 0.5)
					.vel(Math.cos(a) * s, 0.8 + r.nextDouble(), Math.sin(a) * s).drag(0.9).lifeTicks(10).size(0.35 + r.nextDouble() * 0.45)
					.color(rukia ? "#FFFFFF" : "#F9C8F6").peak(0.9).twinkle().rot(r.nextDouble() * 6.28, (r.nextDouble() - 0.5) * 3).owner(t);
			g.spawn();
		}
	}

	/** Thin streaks of light flying out of the chest in a ring (anime style speed lines of the burst). */
	private static void streaks(EffectTimeline t, String tint, int n) {
		Random r = t.rng(11);
		Vec3d c = t.pos.add(0, 1.15, 0);
		for (int i = 0; i < n; i++) {
			double a = (i + r.nextDouble() * 0.6) / n * Math.PI * 2;
			double s = 9 + r.nextDouble() * 5;
			FxGlowBatch.sprite(GlowSprite.STREAK).at(c.x, c.y + (r.nextDouble() - 0.5) * 0.3, c.z).vel(Math.cos(a) * s, 0.9 + r.nextDouble() * 1.2, Math.sin(a) * s)
					.drag(0.86).streak().streakTime(0.08).size(0.9 + r.nextDouble() * 0.6).lifeTicks(8).curve(0.01, 0.7).color(tint).peak(0.8).owner(t).spawn();
		}
	}

	/** One frame of the light ring around the blade: right hand 0.35 right, 1.1 up, 0.45 forward of the live body yaw, facing the camera. */
	private static void handRing(EffectTimeline t, double progress) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Entity c = t.caster();
		double yaw = c != null ? Math.toRadians(c.getYaw()) : Math.atan2(-t.dir.x, t.dir.z);
		Vec3d feet = c != null ? c.getPos() : t.pos;
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -fz;
		double rz = fx;
		Vec3d hand = new Vec3d(feet.x + rx * 0.35 + fx * 0.45, feet.y + 1.1, feet.z + rz * 0.35 + fz * 0.45);
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		double nx = cam.x - hand.x;
		double nz = cam.z - hand.z;
		double radius = FxMath.lerp(0.8, 1.4, FxMath.oc(progress));
		float intensity = (float) (1.0 - 0.35 * progress);
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(hand.x, hand.y, hand.z).plane(nx, 0, nz).size(radius / 0.4375 * 0.92).lifeTicks(3).curve(0.01, 0.6)
				.color("#FFFFFF").peak(0.9 * intensity).owner(t).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(hand.x, hand.y, hand.z).plane(nx, 0, nz).size(radius * 1.8).lifeTicks(5).curve(0.02, 0.8)
				.color("#CFEFFF").peak(0.25 * intensity).owner(t).spawn();
	}
}
