package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.sound.SoundIds;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.gin.GinFxModes;
import dev.minebleach.reiatsutest.core.gin.GinParams;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/**
 * Client visuals of Gin Ichimaru (effect ids 60 and 61 for the releases, 62 to 67 for the abilities; layout of the events in
 * {@link GinFxModes}). The server has done the damage; this class only draws.
 *
 * <ul>
 * <li>The thrusts (Ikorose, Renjin, Korose press 1) are a white glowing line from the hand: it extends in 2 ticks and falls back in 3
 * ({@link FxShapes#strip}, the way Rukia's Shirafune and the kido lines are drawn; the exported {@code gin_shinso_ray} mesh was not used, see
 * design/results/gin_step2.md).</li>
 * <li>Hyapponzashi: a glow at the hand during the wind-up, then the blade sweeps the arc as a fan of short strips.</li>
 * <li>Snake Hook: the line stays taut from the live hand to the anchor while Gin is pulled.</li>
 * <li>Korose: the sliver glows on its carrier for the OWNER's client only (event mode SLIVER, sent to him alone); the recast shows a rising
 * dust ring on the target, the dissolve a white dust burst.</li>
 * <li>Hakufuku: a short shimmer at the feet, then no Gin particles and no flash for the hidden time.</li>
 * </ul>
 *
 * Every colour and size can be tuned in {@code run/fx_override.json} (keys {@code gin.*}). UNVERIFIED in game.
 */
public final class GinFx {
	/** Harness statistics. */
	public static int played;

	/** Carriers of a sliver of the local player (entity id -> wall-clock end in ms); only ever filled from owner-only events. */
	private static final Map<Integer, Long> SLIVERS = new HashMap<>();
	private static final Set<Integer> PULSING = new HashSet<>();
	/** Gin entities that are hidden by Hakufuku (entity id -> wall-clock end in ms). */
	private static final Map<Integer, Long> HIDDEN = new HashMap<>();

	private GinFx() {
	}

	public static boolean handles(int effectId) {
		return effectId >= EffectIds.GIN_SHIKAI_RELEASE && effectId <= 67;
	}

	/** True while Gin's Hakufuku hides entity {@code casterId} (no particles, no flash). */
	public static boolean hidden(int casterId) {
		Long end = HIDDEN.get(casterId);
		return end != null && System.currentTimeMillis() < end;
	}

	public static boolean slivered(int entityId) {
		Long end = SLIVERS.get(entityId);
		return end != null && System.currentTimeMillis() < end;
	}

	public static void play(MinecraftClient client, EffectEventS2C e) {
		ClientWorld world = client.world;
		if (world == null) {
			return;
		}
		int id = e.effectId();
		float[] pr = e.params();
		played++;
		if (id == EffectIds.GIN_SHIKAI_RELEASE || id == EffectIds.GIN_BANKAI_RELEASE) {
			release(world, e, id == EffectIds.GIN_BANKAI_RELEASE);
			return;
		}
		if (pr.length < GinFxModes.MIN_LEN) {
			cast(world, e, id);
			return;
		}
		int mode = Math.round(pr[0]);
		Vec3d eye = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		double dl = dir.length();
		dir = dl < 1.0E-6 ? new Vec3d(0, 0, 1) : dir.multiply(1.0 / dl);
		ReiatsuTest.LOGGER.info("[fx] gin id={} mode={} caster={} target={}", id, mode, e.casterId(), e.targetId());
		switch (mode) {
			case GinFxModes.STRIKE -> strike(world, e, eye, dir, pr);
			case GinFxModes.SWEEP -> sweep(world, e, eye, dir, pr);
			case GinFxModes.HOOK -> hook(world, e, eye, dir, pr);
			case GinFxModes.CHANNEL -> channel(world, e, eye, dir, pr);
			case GinFxModes.DISSOLVE -> dissolve(world, e, pr);
			case GinFxModes.INTERRUPT -> interrupt(world, e, eye);
			case GinFxModes.SLIVER -> sliver(e, pr);
			case GinFxModes.GONE -> gone(world, e);
			default -> { }
		}
	}

	// ------------------------------------------------------------------ small helpers

	private static double q() {
		return FxMath.clamp(FxConfig.effectQuality, 0.1, 1.0);
	}

	private static void particle(ClientWorld w, ParticleEffect p, Vec3d at, double vx, double vy, double vz) {
		w.addParticle(p, at.x, at.y, at.z, vx, vy, vz);
	}

	private static Vec3d hand(Vec3d eye, Vec3d dir) {
		return KidoFx.hand(eye, dir);
	}

	private static void line(Vec3d from, Vec3d to, double extendS, double holdS, double retractS, double coreW, double glowW, String glow) {
		Vec3d d = to.subtract(from);
		double len = d.length();
		if (len < 0.3) {
			return;
		}
		Vec3d u = d.multiply(1.0 / len);
		double life = extendS + holdS + retractS;
		java.util.function.DoubleUnaryOperator reach = a -> a < extendS ? len * FxMath.oc(a / extendS)
				: a < extendS + holdS ? len : len * Math.max(0.0, 1.0 - FxMath.ios((a - extendS - holdS) / retractS));
		FxShapes.Strip g = FxShapes.strip(from.x, from.y, from.z, u.x, u.y, u.z, 0).length(reach).width(a -> glowW);
		g.color(glow).life(life).intensity(a -> FxTune.d("gin.glow", 0.55) * (1.0 - 0.3 * FxMath.clamp(a / life)));
		FxShapes.Strip c = FxShapes.strip(from.x, from.y, from.z, u.x, u.y, u.z, 0).length(reach).width(a -> coreW);
		c.color("#FFFFFF").life(life).intensity(a -> 1.0);
	}

	// ------------------------------------------------------------------ releases and the cast pose (events without a mode)

	private static void release(ClientWorld w, EffectEventS2C e, boolean bankai) {
		Vec3d feet = new Vec3d(e.x(), e.y(), e.z());
		double size = bankai ? 7.0 : 4.5;
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(feet.x, feet.y + 0.05, feet.z).ground().size(0.5, size / 0.375).sizeEase(FxMath.OC).life(0.6).curve(0.01, 0.6)
				.color("#EAF8FF").peak(0.8).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(feet.x, feet.y + 1.0, feet.z).size(0.6, size * 0.5).sizeEase(FxMath.OC).life(0.4).curve(0.01, 0.8)
				.color("#FFFFFF").peak(0.7).spawn();
		Random r = new Random(e.seed());
		int n = (int) Math.round((bankai ? 24 : 12) * q());
		for (int i = 0; i < n; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			particle(w, ParticleTypes.END_ROD, feet.add(Math.cos(a) * 0.6, 0.2 + r.nextDouble() * 1.4, Math.sin(a) * 0.6), Math.cos(a) * 0.06, 0.02, Math.sin(a) * 0.06);
		}
		FxSound.play(null, bankai ? SoundIds.GIN_BANKAI_RELEASE : SoundIds.GIN_SHIKAI_RELEASE, "item.trident.return", feet.x, feet.y + 1, feet.z, bankai ? 0.6 : 1.5, 0.7);
	}

	/** The event of the key press: the pose, the wind-up and the sound. The geometry of a strike comes with its own event. */
	private static void cast(ClientWorld w, EffectEventS2C e, int id) {
		Vec3d feet = new Vec3d(e.x(), e.y(), e.z());
		Vec3d eye = feet.add(0, 1.62, 0);
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		if (dir.lengthSquared() < 1.0E-6) {
			dir = new Vec3d(0, 0, 1);
		}
		Vec3d hand = hand(eye, dir.normalize());
		switch (id) {
			case 62, 64, 66 -> FxSound.play(null, id == 62 ? SoundIds.GIN_SHIKAI_IKOROSE_CAST : id == 64 ? SoundIds.GIN_SHIKAI_SNAKE_HOOK_CAST : SoundIds.GIN_BANKAI_KOROSE_CAST, "item.trident.throw", hand.x, hand.y, hand.z, 2.0, 0.5);
			case 63 -> {
				// 1.0 s wind-up: a glow grows at the hand
				double windup = BalanceConfig.defaults().spec(AbilityId.HYAPPONZASHI).phaseOffsets().get(1) / 20.0;
				FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(hand.x, hand.y, hand.z).size(0.2, FxTune.d("gin.windupGlow", 1.4)).sizeEase(FxMath.IQ).life(windup).curve(0.05, 0.2)
						.color("#DFF1FF").peak(0.8).spawn();
				FxSound.play(null, SoundIds.GIN_SHIKAI_HYAPPONZASHI_WINDUP, "block.beacon.activate", hand.x, hand.y, hand.z, 2.0, 0.4);
			}
			case 65 -> FxSound.play(null, SoundIds.GIN_BANKAI_BUTOU_RENJIN_CAST, "block.amethyst_block.resonate", hand.x, hand.y, hand.z, 1.9, 0.5);
			case 67 -> hakufuku(w, e, feet);
			default -> { }
		}
	}

	private static void hakufuku(ClientWorld w, EffectEventS2C e, Vec3d feet) {
		long ms = BalanceConfig.defaults().spec(AbilityId.HAKUFUKU).intNum(GinParams.HIDE_TICKS) * 50L;
		HIDDEN.put(e.casterId(), System.currentTimeMillis() + ms);
		// the only visible part of the hiding: a short pale shimmer that fades before he vanishes
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(feet.x, feet.y + 1.0, feet.z).size(1.6, 0.4).life(0.3).curve(0.01, 0.9).color("#E8F4FF").peak(0.5).spawn();
		FxSound.play(null, SoundIds.GIN_BANKAI_HAKUFUKU, "block.amethyst_block.chime", feet.x, feet.y + 1, feet.z, 2.0, 0.3);
	}

	// ------------------------------------------------------------------ strikes

	/** {mode, endX, endY, endZ, hit, quiet, index}: the thrust line. */
	private static void strike(ClientWorld w, EffectEventS2C e, Vec3d eye, Vec3d dir, float[] pr) {
		Vec3d end = new Vec3d(pr[1], pr[2], pr[3]);
		boolean hit = pr[4] > 0.5f;
		boolean quiet = pr[5] > 0.5f || hidden(e.casterId());
		int index = pr.length > 6 ? Math.round(pr[6]) : 0;
		Vec3d hand = hand(eye, dir);
		// extends in 2 ticks (0.1 s), falls back in 3 (0.15 s)
		line(hand, end, 0.1, 0.0, 0.15, FxTune.d("gin.coreWidth", 0.09), FxTune.d("gin.glowWidth", 0.4), FxTune.s("gin.color", "#BFE4FF"));
		if (!quiet) {
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(hand.x, hand.y, hand.z).size(0.5, FxTune.d("gin.handFlash", 1.2)).sizeEase(FxMath.OC).life(0.18).curve(0.01, 0.8)
					.color("#FFFFFF").peak(0.9).spawn();
			FxSound.play(null, SoundIds.GIN_SHIKAI_STRIKE, "entity.player.attack.sweep", hand.x, hand.y, hand.z, 1.8 + 0.03 * index, 0.5);
		}
		if (hit) {
			FxGlowBatch.sprite(GlowSprite.STAR4).at(end.x, end.y, end.z).size(0.3, FxTune.d("gin.hitStar", 1.5)).sizeEase(FxMath.OC).life(0.2).curve(0.01, 0.8)
					.color("#FFFFFF").peak(0.9).rot(0.3, 1.2).spawn();
			int n = (int) Math.round(5 * q());
			Random r = new Random(e.seed() + index);
			for (int i = 0; i < n; i++) {
				particle(w, ParticleTypes.ELECTRIC_SPARK, end, (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.3) * 0.3, (r.nextDouble() - 0.5) * 0.4);
			}
			FxSound.play(null, SoundIds.GIN_SHIKAI_STRIKE_HIT, "entity.player.attack.crit", end.x, end.y, end.z, 1.6, 0.5);
		}
	}

	/** {mode, radius, arcDeg, sweepTicks}: the blade swept around the look direction, as a fan of short strips. */
	private static void sweep(ClientWorld w, EffectEventS2C e, Vec3d eye, Vec3d dir, float[] pr) {
		double radius = pr[1];
		double arc = Math.toRadians(pr[2]);
		double sweepS = Math.max(0.1, pr[3] / 20.0);
		Vec3d flat = new Vec3d(dir.x, 0, dir.z);
		flat = flat.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : flat.normalize();
		Vec3d hand = hand(eye, flat).add(0, -0.15, 0);
		int steps = 13;
		for (int k = 0; k < steps; k++) {
			double f = (double) k / (steps - 1);
			double th = -arc / 2 + arc * f; // the same rotation sense as GinGeometry.yawOffsetDeg: atan2(z, x) grows
			Vec3d d = new Vec3d(flat.x * Math.cos(th) - flat.z * Math.sin(th), 0, flat.x * Math.sin(th) + flat.z * Math.cos(th));
			double delay = sweepS * f;
			double life = 0.16;
			FxShapes.Strip g = FxShapes.strip(hand.x, hand.y, hand.z, d.x, d.y, d.z, delay).length(a -> radius).width(a -> FxTune.d("gin.sweepGlow", 0.5));
			g.color(FxTune.s("gin.color", "#BFE4FF")).life(life).intensity(a -> 0.5 * Math.max(0, 1.0 - FxMath.clamp(a / life)));
			FxShapes.Strip c = FxShapes.strip(hand.x, hand.y, hand.z, d.x, d.y, d.z, delay).length(a -> radius).width(a -> 0.1);
			c.color("#FFFFFF").life(life).intensity(a -> Math.max(0, 1.0 - FxMath.clamp(a / life)));
			if (k % 3 == 0) {
				Vec3d tip = hand.add(d.multiply(radius));
				FxDelayed.after(delay, () -> {
					particle(w, ParticleTypes.END_ROD, tip, 0, 0.01, 0);
					FxSound.play(null, SoundIds.GIN_BANKAI_BUTOU_RENJIN_SWEEP, "entity.player.attack.sweep", tip.x, tip.y, tip.z, 1.3, 0.25);
				});
			}
		}
	}

	/** {mode, anchorX, anchorY, anchorZ, hit, pullTicks}: the hook line, kept taut from the live hand while Gin is pulled. */
	private static void hook(ClientWorld w, EffectEventS2C e, Vec3d eye, Vec3d dir, float[] pr) {
		Vec3d anchor = new Vec3d(pr[1], pr[2], pr[3]);
		boolean hit = pr[4] > 0.5f;
		int ticks = Math.round(pr[5]);
		Vec3d hand = hand(eye, dir);
		String color = FxTune.s("gin.color", "#BFE4FF");
		line(hand, anchor, 0.1, hit ? Math.max(0, ticks / 20.0 - 0.1) : 0.0, 0.15, 0.07, 0.3, color);
		FxSound.play(null, SoundIds.GIN_SHIKAI_SNAKE_HOOK_THROW, "entity.fishing_bobber.throw", hand.x, hand.y, hand.z, 1.5, 0.5);
		if (hit) {
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(anchor.x, anchor.y, anchor.z).size(0.4, 1.2).sizeEase(FxMath.OC).life(0.3).curve(0.01, 0.8).color("#FFFFFF").peak(0.8).spawn();
			FxSound.play(null, SoundIds.GIN_SHIKAI_SNAKE_HOOK_HIT, "block.chain.hit", anchor.x, anchor.y, anchor.z, 1.4, 0.6);
		}
	}

	// ------------------------------------------------------------------ Korose

	/** {mode, channelTicks} + target: the raised hand and a ring of dust that closes on the target. */
	private static void channel(ClientWorld w, EffectEventS2C e, Vec3d eye, Vec3d dir, float[] pr) {
		double seconds = Math.max(0.2, pr[1] / 20.0);
		Vec3d hand = eye.add(0, 0.5, 0);
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(hand.x, hand.y, hand.z).size(0.3, 1.0).sizeEase(FxMath.OC).life(seconds).curve(0.1, 0.3).color("#FFFFFF").peak(0.7).spawn();
		FxSound.play(null, SoundIds.GIN_BANKAI_KOROSE_CHANNEL, "block.beacon.power_select", hand.x, hand.y, hand.z, 2.0, 0.5);
		int steps = (int) Math.max(2, Math.round(seconds * 10 * q()));
		Random r = new Random(e.seed());
		for (int k = 0; k < steps; k++) {
			double t = (double) k / steps;
			FxDelayed.after(seconds * t, () -> {
				Entity target = w.getEntityById(e.targetId());
				if (target == null) {
					return;
				}
				Vec3d c = target.getBoundingBox().getCenter();
				double ring = 1.6 * (1.0 - t) + 0.25;
				for (int i = 0; i < 4; i++) {
					double a = r.nextDouble() * Math.PI * 2;
					particle(w, ParticleTypes.WHITE_ASH, c.add(Math.cos(a) * ring, (r.nextDouble() - 0.5) * target.getHeight(), Math.sin(a) * ring), -Math.cos(a) * 0.05, 0.0,
							-Math.sin(a) * 0.05);
				}
			});
		}
	}

	/** {mode, x, y, z} + target: the carrier turns to white dust. */
	private static void dissolve(ClientWorld w, EffectEventS2C e, float[] pr) {
		Vec3d c = new Vec3d(pr[1], pr[2], pr[3]);
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(c.x, c.y, c.z).size(0.8, FxTune.d("gin.dissolveSize", 4.0)).sizeEase(FxMath.OC).life(0.6).curve(0.01, 0.8).color("#FFFFFF")
				.peak(0.95).spawn();
		FxGlowBatch.sprite(GlowSprite.STAR4).at(c.x, c.y, c.z).size(0.5, 2.6).sizeEase(FxMath.OC).life(0.35).curve(0.01, 0.8).color("#EAF8FF").peak(0.9).rot(0.2, 0.8).spawn();
		Random r = new Random(e.seed());
		int n = (int) Math.round(40 * q());
		for (int i = 0; i < n; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double sp = 0.03 + r.nextDouble() * 0.12;
			particle(w, i % 2 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.POOF, c.add((r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 1.2, (r.nextDouble() - 0.5) * 0.6),
					Math.cos(a) * sp, 0.02 + r.nextDouble() * 0.05, Math.sin(a) * sp);
		}
		FxSound.play(null, SoundIds.GIN_BANKAI_KOROSE_DISSOLVE, "block.amethyst_cluster.break", c.x, c.y, c.z, 0.7, 0.9);
		FxSound.play(null, SoundIds.GIN_BANKAI_KOROSE_DISSOLVE, "entity.generic.extinguish_fire", c.x, c.y, c.z, 0.6, 0.6);
	}

	private static void interrupt(ClientWorld w, EffectEventS2C e, Vec3d eye) {
		FxGlowBatch.sprite(GlowSprite.STAR4).at(eye.x, eye.y - 0.4, eye.z).size(0.3, 1.2).sizeEase(FxMath.OC).life(0.2).curve(0.01, 0.8).color("#FFFFFF").peak(0.6).spawn();
		FxSound.play(null, SoundIds.GIN_BANKAI_KOROSE_INTERRUPT, "block.glass.break", eye.x, eye.y, eye.z, 1.6, 0.4);
	}

	/** Owner only: {mode, ticks} + target. Starts (or renews) the glow on the carrier. */
	private static void sliver(EffectEventS2C e, float[] pr) {
		int target = e.targetId();
		if (target < 0) {
			return;
		}
		SLIVERS.put(target, System.currentTimeMillis() + Math.round(pr[1]) * 50L);
		if (PULSING.add(target)) {
			pulse(target);
		}
	}

	private static void pulse(int target) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Entity en = mc.world == null ? null : mc.world.getEntityById(target);
		if (en == null || !en.isAlive() || !slivered(target)) {
			PULSING.remove(target);
			SLIVERS.remove(target);
			return;
		}
		Vec3d c = en.getBoundingBox().getCenter();
		double size = Math.max(en.getWidth(), en.getHeight() * 0.7) * FxTune.d("gin.sliverSize", 1.3);
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(c.x, c.y, c.z).size(size * 0.8, size).life(0.45).curve(0.15, 0.6).color(FxTune.s("gin.sliverColor", "#EAF8FF")).peak(0.45).spawn();
		FxDelayed.after(0.3, () -> pulse(target));
	}

	private static void gone(ClientWorld w, EffectEventS2C e) {
		int target = e.targetId();
		SLIVERS.remove(target);
		PULSING.remove(target);
		Entity en = target < 0 ? null : w.getEntityById(target);
		if (en != null) {
			Vec3d c = en.getBoundingBox().getCenter();
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(c.x, c.y, c.z).size(0.5, 1.6).sizeEase(FxMath.OC).life(0.25).curve(0.01, 0.8).color("#EAF8FF").peak(0.5).spawn();
		}
	}
}
