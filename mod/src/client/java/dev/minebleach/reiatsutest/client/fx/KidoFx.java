package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.kido.KidoMath;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/**
 * Client visuals of the kido spells (B5, effect ids 50 to 52). The server has already done the damage; this class only
 * draws, from the one {@code effect_event}: x y z = the caster's eye, d = look direction, target = entity hit or -1,
 * params = {line length, travel ticks, burst radius, hit kind, speed, binding ticks} (see {@code KidoMath.effectParams}).
 *
 * <ul>
 * <li>Byakurai: a white lightning line from the hand to the end point (core, soft glow and a short zigzag), a flash at both
 * ends and sparks.</li>
 * <li>Shakkaho: a red ball that flies the line in the server's travel time (a train of glow sprites with staggered births plus
 * flame particles) and bursts at the end (glow, star, ground ring, flames).</li>
 * <li>Sai: a yellow flash at the hand, a thin yellow line and a ring with sparks on the target.</li>
 * </ul>
 *
 * Every size, colour and time can be tuned live in {@code run/fx_override.json} (keys {@code kido.*}, see {@link FxTune}); counts
 * follow {@link FxConfig#effectQuality}, {@code reduceMotion} drops the zigzag and the extra sparks. UNVERIFIED in game.
 */
public final class KidoFx {
	/** Harness statistics. */
	public static int played;
	public static int lastParticles;

	private KidoFx() {
	}

	public static boolean handles(int effectId) {
		return KidoId.fromEffectId(effectId) != null;
	}

	public static void play(MinecraftClient client, EffectEventS2C e) {
		ClientWorld world = client.world;
		KidoId id = KidoId.fromEffectId(e.effectId());
		if (world == null || id == null) {
			return;
		}
		float[] pr = e.params();
		double length = pr.length > 0 ? pr[0] : 0.0;
		int travelTicks = pr.length > 1 ? Math.round(pr[1]) : 0;
		double radius = pr.length > 2 ? pr[2] : 0.0;
		int hitKind = pr.length > 3 ? Math.round(pr[3]) : KidoMath.HIT_NONE;
		Vec3d eye = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		double dl = dir.length();
		if (dl < 1.0E-6) {
			return;
		}
		dir = dir.multiply(1.0 / dl);
		Vec3d end = eye.add(dir.multiply(length));
		Vec3d hand = hand(eye, dir);
		ReiatsuTest.LOGGER.info("[fx] kido {} caster={} length={} travel={} hit={}", id, e.casterId(), String.format(java.util.Locale.ROOT, "%.2f", length),
				travelTicks, hitKind);
		played++;
		lastParticles = 0;
		Random rnd = new Random(e.seed());
		switch (id) {
			case BYAKURAI -> byakurai(world, hand, end, hitKind, rnd);
			case SHAKKAHO -> shakkaho(world, hand, dir, end, travelTicks, radius, hitKind, rnd);
			case SAI -> sai(world, hand, end, hitKind, rnd);
		}
	}

	/** Where the spell leaves the caster: in front of the eye, a little to the right and down (the right hand). */
	static Vec3d hand(Vec3d eye, Vec3d dir) {
		double hl = Math.hypot(dir.x, dir.z);
		Vec3d right = hl < 1.0E-4 ? new Vec3d(1, 0, 0) : new Vec3d(-dir.z / hl, 0, dir.x / hl);
		return eye.add(dir.multiply(0.6)).add(right.multiply(0.25)).add(0, -0.3, 0);
	}

	private static double q() {
		return FxMath.clamp(FxConfig.effectQuality, 0.1, 1.0);
	}

	private static void particle(ClientWorld w, ParticleEffect p, Vec3d at, double vx, double vy, double vz) {
		w.addParticle(p, at.x, at.y, at.z, vx, vy, vz);
		lastParticles++;
	}

	// ------------------------------------------------------------------ Byakurai (Hado 4)

	private static void byakurai(ClientWorld w, Vec3d hand, Vec3d end, int hitKind, Random rnd) {
		Vec3d d = end.subtract(hand);
		double len = d.length();
		if (len < 0.3) {
			return;
		}
		Vec3d u = d.multiply(1.0 / len);
		String color = FxTune.s("kido.byakurai.color", "#F4F8FF");
		double life = FxTune.d("kido.byakurai.life", 0.28);
		double coreW = FxTune.d("kido.byakurai.coreWidth", 0.10);
		double glowW = FxTune.d("kido.byakurai.glowWidth", 0.5);
		double glowI = FxTune.d("kido.byakurai.glowIntensity", 0.6);
		// core and glow: two crossed quads each, the whole line at once (hitscan), fading out together
		FxShapes.Strip core = FxShapes.strip(hand.x, hand.y, hand.z, u.x, u.y, u.z, 0).length(a -> len).width(a -> coreW * (1.0 - 0.5 * FxMath.clamp(a / life)));
		core.color("#FFFFFF").life(life).intensity(a -> Math.max(0, 1.0 - FxMath.ios(FxMath.clamp(a / life))));
		FxShapes.Strip glow = FxShapes.strip(hand.x, hand.y, hand.z, u.x, u.y, u.z, 0).length(a -> len).width(a -> glowW);
		glow.color(color).life(life).intensity(a -> glowI * Math.max(0, 1.0 - FxMath.ios(FxMath.clamp(a / life))));
		// a short zigzag around the line: electricity, not a ruler (dropped with reduceMotion)
		if (!FxConfig.reduceMotion) {
			int segs = (int) Math.min(14, Math.max(3, Math.ceil(len / 2.5) * q()));
			Vec3d cr = u.crossProduct(new Vec3d(0, 1, 0));
			Vec3d side = cr.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : cr.normalize();
			Vec3d up = u.crossProduct(side);
			double amp = FxTune.d("kido.byakurai.zigzag", 0.28);
			Vec3d prev = hand;
			for (int i = 1; i <= segs; i++) {
				double f = (double) i / segs;
				Vec3d base = hand.add(u.multiply(len * f));
				double o = i == segs ? 0 : (rnd.nextDouble() - 0.5) * 2.0 * amp;
				double o2 = i == segs ? 0 : (rnd.nextDouble() - 0.5) * 2.0 * amp;
				Vec3d p = base.add(side.multiply(o)).add(up.multiply(o2));
				Vec3d seg = p.subtract(prev);
				double sl = seg.length();
				if (sl > 0.05) {
					Vec3d su = seg.multiply(1.0 / sl);
					FxShapes.Strip z = FxShapes.strip(prev.x, prev.y, prev.z, su.x, su.y, su.z, 0).length(a -> sl).width(a -> 0.07);
					z.color("#FFFFFF").life(life * 0.7).intensity(a -> 0.9 * Math.max(0, 1.0 - FxMath.clamp(a / (life * 0.7))));
				}
				prev = p;
			}
		}
		// flashes at the hand and at the end
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(hand.x, hand.y, hand.z).size(0.6, FxTune.d("kido.byakurai.handFlash", 1.4)).sizeEase(FxMath.OC).life(0.2)
				.curve(0.01, 0.8).color(color).peak(0.9).spawn();
		FxGlowBatch.sprite(GlowSprite.STAR4).at(end.x, end.y, end.z).size(0.4, FxTune.d("kido.byakurai.endFlash", 2.2)).sizeEase(FxMath.OC).life(0.22)
				.curve(0.01, 0.8).color("#FFFFFF").peak(1.0).rot(0.3, 1.5).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(end.x, end.y, end.z).size(0.8, FxTune.d("kido.byakurai.endGlow", 3.0)).sizeEase(FxMath.OC).life(0.3)
				.curve(0.01, 0.8).color(color).peak(0.8).spawn();
		// sparks along the line and at the end
		int along = (int) Math.min(12, len / 3.0);
		for (int i = 0; i < along; i++) {
			Vec3d p = hand.add(u.multiply(len * (i + rnd.nextDouble()) / Math.max(1, along)));
			particle(w, ParticleTypes.ELECTRIC_SPARK, p, 0, 0, 0);
		}
		int burst = (int) Math.round(FxTune.d("kido.byakurai.sparks", hitKind == KidoMath.HIT_NONE ? 4 : 12) * q());
		for (int i = 0; i < burst; i++) {
			particle(w, ParticleTypes.ELECTRIC_SPARK, end, (rnd.nextDouble() - 0.5) * 0.5, (rnd.nextDouble() - 0.2) * 0.4, (rnd.nextDouble() - 0.5) * 0.5);
		}
		FxSound.play(null, "item.trident.thunder", hand.x, hand.y, hand.z, FxTune.d("kido.byakurai.pitch", 1.9), FxTune.d("kido.byakurai.volume", 0.7));
		FxSound.play(null, "entity.firework_rocket.blast", end.x, end.y, end.z, 1.6, 0.6);
	}

	// ------------------------------------------------------------------ Shakkaho (Hado 31)

	private static void shakkaho(ClientWorld w, Vec3d hand, Vec3d dir, Vec3d end0, int travelTicks, double radius, int hitKind, Random rnd) {
		Vec3d end = hitKind == KidoMath.HIT_BLOCK ? end0.subtract(dir.multiply(KidoMath.SURFACE_BACKOFF)) : end0;
		Vec3d d = end.subtract(hand);
		double len = d.length();
		String red = FxTune.s("kido.shakkaho.color", "#FF3A1A");
		String hot = FxTune.s("kido.shakkaho.core", "#FFB347");
		double ball = FxTune.d("kido.shakkaho.ballSize", 1.1);
		double flight = Math.max(0.0, travelTicks / 20.0);
		// charge flash at the hand
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(hand.x, hand.y, hand.z).size(0.4, ball * 1.6).sizeEase(FxMath.OC).life(0.25).curve(0.01, 0.8).color(red).peak(0.9).spawn();
		FxSound.play(null, "entity.blaze.shoot", hand.x, hand.y, hand.z, FxTune.d("kido.shakkaho.castPitch", 0.8), FxTune.d("kido.shakkaho.castVolume", 0.9));
		if (len > 0.3) {
			// the ball: a train of sprites with staggered births along the line, two per tick of flight
			int steps = (int) Math.max(3, Math.min(60, Math.round(flight * 40.0)));
			if (flight <= 0.0) {
				steps = 3;
			}
			Vec3d u = d.multiply(1.0 / len);
			for (int i = 0; i <= steps; i++) {
				double f = (double) i / steps;
				Vec3d p = hand.add(d.multiply(f));
				double delay = flight * f;
				FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(p.x, p.y, p.z).size(ball, ball * 0.4).life(0.22).delay(delay).curve(0.01, 0.9).color(red).peak(0.95).spawn();
				FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(p.x, p.y, p.z).size(ball * 0.45, ball * 0.2).life(0.18).delay(delay).curve(0.01, 0.9).color(hot).peak(1.0).spawn();
			}
			// flame and smoke particles dropped by the ball, 1 per 2 blocks
			int trail = (int) Math.min(24, Math.max(2, len / 2.0) * q());
			for (int i = 0; i < trail; i++) {
				double f = (double) i / trail;
				Vec3d p = hand.add(d.multiply(f));
				FxDelayed.after(flight * f, () -> {
					particle(w, ParticleTypes.FLAME, p, 0, 0.01, 0);
					particle(w, ParticleTypes.SMOKE, p, 0, 0.02, 0);
				});
			}
			if (flight > 0.0) {
				FxDelayed.after(flight * 0.5, () -> FxSound.play(null, "block.fire.ambient", hand.x + d.x * 0.5, hand.y + d.y * 0.5, hand.z + d.z * 0.5, 1.2, 0.5));
			}
		}
		FxDelayed.after(flight, () -> explode(w, end, radius, rnd.nextLong()));
	}

	private static void explode(ClientWorld w, Vec3d c, double radius, long seed) {
		Random r = new Random(seed);
		String red = FxTune.s("kido.shakkaho.color", "#FF3A1A");
		String hot = FxTune.s("kido.shakkaho.core", "#FFB347");
		double rad = Math.max(1.0, radius);
		double big = FxTune.d("kido.shakkaho.burstSize", rad * 2.4);
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(c.x, c.y, c.z).size(big * 0.35, big).sizeEase(FxMath.OC).life(0.45).curve(0.01, 0.85).color(red).peak(1.0).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(c.x, c.y, c.z).size(big * 0.2, big * 0.55).sizeEase(FxMath.OC).life(0.3).curve(0.01, 0.8).color(hot).peak(1.0).spawn();
		FxGlowBatch.sprite(GlowSprite.STAR4).at(c.x, c.y, c.z).size(big * 0.3, big * 0.9).sizeEase(FxMath.OC).life(0.3).curve(0.01, 0.8).color("#FFE2B0").peak(0.9).rot(0.2, 0.8).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(c.x, c.y, c.z).ground().size(0.5, rad * 2.0 / 0.375).sizeEase(FxMath.OC).life(0.45).curve(0.01, 0.6).color(red).peak(0.8).spawn();
		int flames = (int) Math.round(FxTune.d("kido.shakkaho.flames", 30) * q());
		for (int i = 0; i < flames; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double up = r.nextDouble() * 0.5;
			double sp = 0.15 + r.nextDouble() * 0.25;
			particle(w, ParticleTypes.FLAME, c, Math.cos(a) * sp, up * sp * 2, Math.sin(a) * sp);
		}
		int smoke = (int) Math.round(FxTune.d("kido.shakkaho.smoke", 12) * q());
		for (int i = 0; i < smoke; i++) {
			particle(w, ParticleTypes.LARGE_SMOKE, c, (r.nextDouble() - 0.5) * 0.3, 0.05 + r.nextDouble() * 0.1, (r.nextDouble() - 0.5) * 0.3);
		}
		particle(w, ParticleTypes.EXPLOSION, c, 0, 0, 0);
		FxSound.play(null, "entity.generic.explode", c.x, c.y, c.z, FxTune.d("kido.shakkaho.burstPitch", 1.1), FxTune.d("kido.shakkaho.burstVolume", 0.9));
	}

	// ------------------------------------------------------------------ Sai (Bakudo 1)

	private static void sai(ClientWorld w, Vec3d hand, Vec3d end, int hitKind, Random rnd) {
		Vec3d d = end.subtract(hand);
		double len = d.length();
		String gold = FxTune.s("kido.sai.color", "#FFD84A");
		double life = FxTune.d("kido.sai.life", 0.25);
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(hand.x, hand.y, hand.z).size(0.4, FxTune.d("kido.sai.handFlash", 1.2)).sizeEase(FxMath.OC).life(0.25).curve(0.01, 0.8).color(gold).peak(0.9).spawn();
		if (len > 0.3) {
			Vec3d u = d.multiply(1.0 / len);
			FxShapes.Strip line = FxShapes.strip(hand.x, hand.y, hand.z, u.x, u.y, u.z, 0).length(a -> len * Math.min(1.0, a / 0.06)).width(a -> FxTune.d("kido.sai.width", 0.14));
			line.color(gold).life(life).intensity(a -> 0.9 * Math.max(0, 1.0 - FxMath.ios(FxMath.clamp(a / life))));
		}
		boolean hit = hitKind == KidoMath.HIT_ENTITY;
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(end.x, end.y, end.z).size(0.5, hit ? 2.4 : 1.0).sizeEase(FxMath.OC).life(0.35).curve(0.01, 0.8).color(gold).peak(hit ? 0.9 : 0.5).spawn();
		if (hit) {
			// the binding: two rings around the body (waist and chest), plus a star
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(end.x, end.y - 0.4, end.z).plane(0, 1, 0).size(0.4, FxTune.d("kido.sai.ringSize", 1.8)).sizeEase(FxMath.OC).life(0.5)
					.curve(0.01, 0.7).color(gold).peak(0.9).spawn();
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(end.x, end.y + 0.2, end.z).plane(0, 1, 0).size(0.4, FxTune.d("kido.sai.ringSize", 1.8)).sizeEase(FxMath.OC).life(0.5).delay(0.08)
					.curve(0.01, 0.7).color(gold).peak(0.9).spawn();
			FxGlowBatch.sprite(GlowSprite.STAR4).at(end.x, end.y, end.z).size(0.4, 1.8).sizeEase(FxMath.OC).life(0.25).curve(0.01, 0.8).color("#FFF3B0").peak(0.9).rot(0.2, 1.0).spawn();
			int sparks = (int) Math.round((FxConfig.reduceMotion ? 4 : 10) * q());
			for (int i = 0; i < sparks; i++) {
				particle(w, ParticleTypes.ELECTRIC_SPARK, end, (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.3) * 0.3, (rnd.nextDouble() - 0.5) * 0.3);
			}
		}
		FxSound.play(null, "block.amethyst_block.resonate", hand.x, hand.y, hand.z, FxTune.d("kido.sai.pitch", 1.6), FxTune.d("kido.sai.volume", 0.8));
		if (hit) {
			FxSound.play(null, "entity.iron_golem.repair", end.x, end.y, end.z, 1.7, 0.5);
		}
	}
}
