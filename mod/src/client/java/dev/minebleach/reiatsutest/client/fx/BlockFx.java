package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.sound.SoundIds;
import dev.minebleach.reiatsutest.client.model.FpMotion;
import dev.minebleach.reiatsutest.core.fx.BlockFxMath;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Block and parry feedback (effect id 41), Sekiro style (r8 fx2 item 1). The server sends ONE event per taken hit to everybody who
 * sees the defender: x y z = the contact point on the guard, d = horizontal vector to the attacker, target = the attacker, params =
 * {variant, damage, blade source}, see {@code ServerFx.blockFeedback} and {@link BlockFxMath}. Everybody draws and hears the world
 * part; only the defender's own client gets the first person recoil and the screen effects, and the attacker's client a small recoil
 * shake on a parry.
 * <ul>
 * <li>BLOCK: a sword scraping a sword. Three waves of orange-white sparks (thin glow streaks that fall in arcs) slide along the blade
 * axis away from the contact point, with a short white glint and a bright scrape line along the blade, and a layered metallic
 * clash: shield block, anvil, iron golem, chain, grindstone, trident, all with random pitch.</li>
 * <li>PARRY: clearly different and bigger. A white-gold burst of sparks over a cone towards the attacker plus the sliding sparks, two
 * expanding rings standing in front of the defender, a core flash, radial crack lines, a late shower of embers; a high bright
 * "ting" (amethyst chime, bell and trident chord) with a crack. On the defender's screen: a gold flash, a dark vignette punch, an
 * impact frame (the existing ScreenFx hit-stop-like frame), a stronger shake and FOV kick.</li>
 * <li>ABILITY (the block cut an ability hit by 30 percent): a light version. Few cool white-blue sparks, a small glint, a soft shield
 * sound; the scrape and the metallic layers only when the source is a blade.</li>
 * </ul>
 * Reduce motion, photosensitive safe and the screen scales are honoured by going through {@link ScreenFx} only; sound goes through
 * {@link FxSound} (its volume setting, the same-id and 24 live sound caps).
 *
 * <p>Tuning ({@code fx_override.json}, {@link FxTune}; every number is the default shown): {@code block.sparks} (count scale 1.0, 0 = no
 * sparks), {@code block.glow} (glint brightness 1.0), {@code block.sound} (volume scale 1.0), {@code block.tilt} (blade tilt in degrees
 * 35), {@code block.speedMin} / {@code block.speedMax} (spark speed, blocks per second, 3.5 / 11), {@code block.gravity} (9),
 * {@code block.streakTime} (0.035), {@code blockhit.shake} (degrees 0.6) and {@code blockhit.shakeTime} (0.16 s);
 * {@code parry.sparks} (sparks in front of the face, 10), {@code parry.burst} (world burst count scale 1.0), {@code parry.ringRadius} (2.2),
 * {@code parry.glow} (1.0), {@code parry.sound} (volume scale 1.0), {@code parry.pitch} (pitch scale 1.0), {@code parry.flash} (screen
 * flash peak 0.28), {@code parry.flashColor} (#FFF0C8), {@code parry.vignette} (dark vignette level 0.35, 0 = off), {@code parry.impact}
 * (1 = impact frame, 0 = off), {@code parry.shake} (degrees 1.1), {@code parry.shakeTime} (0.18), {@code parry.fov} (degrees 1.6),
 * {@code parry.fovTime} (0.18), {@code parry.attackerShake} (0.5, the shake of the one who was parried; 0 = off);
 * {@code ability.sparks} (count scale 1.0), {@code ability.shake} (0.3).
 */
public final class BlockFx {
	private static final float[] WHITE = FxMath.hex("#FFFFFF");
	private static final float[] CREAM = FxMath.hex("#FFE9B0");
	private static final float[] ORANGE = FxMath.hex("#FF9A2E");
	private static final float[] AMBER = FxMath.hex("#FFC060");
	private static final float[] GOLD = FxMath.hex("#FFC83D");
	private static final float[] PALE = FxMath.hex("#FFF3B0");
	private static final float[] ICE = FxMath.hex("#CFE8FF");
	private static final float[] COOL = FxMath.hex("#9ED3F0");
	/** Statistics for the harness: events handled and the sparks of the last one. */
	public static int events;
	public static int lastSparks;

	private BlockFx() {
	}

	public static void play(MinecraftClient client, EffectEventS2C e) {
		if (client.world == null) {
			return;
		}
		ClientPlayerEntity me = client.player;
		float[] p = e.params();
		int variant = BlockFxMath.variantOf(p.length > 0 ? p[0] : 0f);
		double damage = p.length > 1 ? p[1] : 0.0;
		boolean blade = p.length < 3 || p[2] > 0.5f;
		boolean local = me != null && e.casterId() == me.getId();
		boolean attackerIsMe = me != null && !local && e.targetId() == me.getId();
		Vec3d at = new Vec3d(e.x(), e.y(), e.z());
		double len = Math.hypot(e.dx(), e.dz());
		double[] n = len < 1e-4 ? new double[] {0.0, 0.0, 1.0} : new double[] {e.dx() / len, 0.0, e.dz() / len};
		long seed = e.seed() * 31L + 7L;
		events++;
		double dist = client.gameRenderer != null && client.gameRenderer.getCamera() != null ? client.gameRenderer.getCamera().getPos().distanceTo(at) : 0.0;
		double far = BlockFxMath.distanceFactor(dist);
		switch (variant) {
			case BlockFxMath.PARRY -> parryWorld(at, n, damage, seed, far);
			case BlockFxMath.ABILITY -> abilityWorld(at, n, damage, blade, seed, far);
			default -> blockWorld(at, n, damage, seed, far);
		}
		if (local) {
			localScreen(client, me, variant, e, n);
		} else if (attackerIsMe && variant == BlockFxMath.PARRY) {
			ScreenFx.shake(FxTune.d("parry.attackerShake", 0.5), 0.14, 1.0);
		}
	}

	// ============================================================================================================ the defender's own screen

	private static void localScreen(MinecraftClient client, ClientPlayerEntity me, int variant, EffectEventS2C e, double[] n) {
		float side = sideOf(me.getYaw(), e.dx(), e.dz());
		switch (variant) {
			case BlockFxMath.PARRY -> {
				FpMotion.onParry(side);
				ScreenFx.shunpoRush(0.0, FxTune.d("parry.fov", 1.6), FxTune.d("parry.fovTime", 0.18));
				ScreenFx.shake(FxTune.d("parry.shake", 1.1), FxTune.d("parry.shakeTime", 0.18), 1.0);
				ScreenFx.flash(FxTune.d("parry.flash", 0.28), FxMath.hex(FxTune.s("parry.flashColor", "#FFF0C8")), true, 1.0);
				double vg = FxTune.d("parry.vignette", 0.35);
				if (vg > 0.001) {
					// a dark edge punch: it closes in at once and lets go 0.08 s later (the impact frame below does the freeze)
					ScreenFx.Layer layer = ScreenFx.vignette(ScreenFx.Kind.DARK, vg, 0.03, 1.0);
					FxDelayed.after(0.08, () -> layer.release(0.22));
				}
				if (FxTune.d("parry.impact", 1.0) > 0.5) {
					ScreenFx.impactFrame(1.0);
				}
				faceSparks(client, me, (int) Math.round(FxTune.d("parry.sparks", 10)));
			}
			case BlockFxMath.ABILITY -> {
				FpMotion.onBlockedHit(side);
				ScreenFx.shake(FxTune.d("ability.shake", 0.3), 0.12, 1.0);
			}
			default -> {
				FpMotion.onBlockedHit(side);
				ScreenFx.shake(FxTune.d("blockhit.shake", 0.6), FxTune.d("blockhit.shakeTime", 0.16), 1.0);
			}
		}
	}

	/** +1 when the attacker (horizontal vector dx, dz from the player) is on the right of the view direction, -1 on the left, 0 ahead or behind. */
	static float sideOf(float yawDeg, float dx, float dz) {
		double len = Math.sqrt((double) dx * dx + (double) dz * dz);
		if (len < 1e-4) {
			return 0f;
		}
		double yaw = Math.toRadians(yawDeg);
		// yaw 0 looks along +z; the right hand side is -x there
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		return (float) MathHelper.clamp((dx * rx + dz * rz) / len, -1.0, 1.0);
	}

	private static void faceSparks(MinecraftClient client, ClientPlayerEntity me, int n) {
		if (n <= 0 || client.world == null) {
			return;
		}
		Vec3d eye = me.getEyePos();
		Vec3d look = me.getRotationVec(1.0F);
		Vec3d at = eye.add(look.multiply(0.85)).add(0.0, -0.1, 0.0);
		var rnd = client.world.random;
		for (int i = 0; i < n; i++) {
			double vx = (rnd.nextDouble() - 0.5) * 0.5;
			double vy = (rnd.nextDouble() - 0.3) * 0.4;
			double vz = (rnd.nextDouble() - 0.5) * 0.5;
			client.world.addParticle(i % 3 == 0 ? ParticleTypes.CRIT : ParticleTypes.ELECTRIC_SPARK, at.x + (rnd.nextDouble() - 0.5) * 0.3,
					at.y + (rnd.nextDouble() - 0.5) * 0.3, at.z + (rnd.nextDouble() - 0.5) * 0.3, vx, vy, vz);
		}
	}

	// ============================================================================================================ BLOCK

	private static void blockWorld(Vec3d at, double[] n, double damage, long seed, double far) {
		Random r = new Random(seed);
		double[] axis = BlockFxMath.bladeAxis(n[0], n[2], Math.toRadians(FxTune.d("block.tilt", 35.0)), (seed & 1L) != 0L);
		int total = (int) Math.round(BlockFxMath.sparkCount(BlockFxMath.BLOCK, damage, FxTune.d("block.sparks", 1.0)) * far);
		lastSparks = total;
		scrape(at, n, axis, total, 3, seed, r, false, far);
		double glow = FxTune.d("block.glow", 1.0);
		// the glint on the contact and the bright scrape line along the blade
		FxGlowBatch.sprite(GlowSprite.STAR4).at(at.x, at.y, at.z).size(1.1, 0.35).sizeEase(FxMath.OC).life(0.12).curve(0.005, 0.7).color(WHITE).peak(glow).rot(r.nextDouble() * 6.28, 0).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(at.x, at.y, at.z).size(0.9, 1.5).life(0.14).curve(0.005, 0.7).color(AMBER).peak(0.55 * glow).spawn();
		FxGlowBatch.sprite(GlowSprite.STREAK).at(at.x, at.y, at.z).vel(axis[0], axis[1], axis[2]).streak().size(1.5).life(0.1).curve(0.004, 0.75).color(CREAM).peak(glow).spawn();
		// the clash: shield, anvil, golem and chain layered with a little pitch scatter, the grindstone is the scrape
		double v = FxTune.d("block.sound", 1.0);
		double ps = 0.9 + r.nextDouble() * 0.2;
		sound(SoundIds.GENERAL_BLOCK_HIT, "item.shield.block", at, 1.25 * ps, 0.8 * v);
		sound(SoundIds.GENERAL_BLOCK_HIT, "block.anvil.land", at, 1.75 * ps, 0.28 * v);
		sound(SoundIds.GENERAL_BLOCK_HIT, "entity.iron_golem.damage", at, 1.55 * ps, 0.5 * v);
		FxDelayed.after(0.03, () -> sound(SoundIds.GENERAL_BLOCK_HIT, "block.chain.hit", at, (1.2 + r.nextDouble() * 0.4), 0.55 * v));
		FxDelayed.after(0.02, () -> sound(SoundIds.GENERAL_BLOCK_HIT, "block.grindstone.use", at, 1.9 + r.nextDouble() * 0.2, 0.25 * v));
		FxDelayed.after(0.05, () -> sound(SoundIds.GENERAL_BLOCK_HIT, "item.trident.hit", at, 1.8 + r.nextDouble() * 0.3, 0.3 * v));
	}

	/**
	 * Sparks that slide along the blade: {@code waves} groups, each starting a little further along the axis and a few hundredths later, so
	 * the scrape runs along the blade. Thin additive streaks that fall in arcs, white and cream at the head, orange at the tail.
	 */
	private static void scrape(Vec3d at, double[] n, double[] axis, int total, int waves, long seed, Random r, boolean cool, double far) {
		if (total <= 0) {
			return;
		}
		int[] counts = BlockFxMath.waves(total, waves);
		double lo = FxTune.d("block.speedMin", 3.5);
		double hi = FxTune.d("block.speedMax", 11.0);
		for (int w = 0; w < counts.length; w++) {
			final int wave = w;
			final int count = counts[w];
			double delay = 0.045 * w;
			Runnable emit = () -> {
				double slide = 0.28 * wave;
				double ox = at.x + axis[0] * slide;
				double oy = at.y + axis[1] * slide;
				double oz = at.z + axis[2] * slide;
				double[][] vel = BlockFxMath.slideVelocities(seed + wave, count, axis, n, lo, hi, 0.8, 0.6, 0.25);
				for (int i = 0; i < vel.length; i++) {
					sparkAt(ox, oy, oz, vel[i], i, r, cool);
				}
			};
			if (delay <= 0.0) {
				emit.run();
			} else {
				FxDelayed.after(delay, emit);
			}
		}
	}

	/** One spark: a streak sprite that follows its velocity, plus (every fourth) a lingering dot. */
	private static void sparkAt(double x, double y, double z, double[] v, int i, Random r, boolean cool) {
		float[] c = cool ? (i % 3 == 0 ? WHITE : i % 3 == 1 ? ICE : COOL) : (i % 4 == 0 ? WHITE : i % 4 == 1 ? CREAM : i % 4 == 2 ? AMBER : ORANGE);
		double life = 0.16 + 0.24 * r.nextDouble();
		FxGlowBatch.sprite(GlowSprite.STREAK).at(x, y, z).vel(v[0], v[1], v[2]).drag(0.88).gravity(FxTune.d("block.gravity", 9.0)).streak()
				.streakTime(FxTune.d("block.streakTime", 0.035)).size(0.05).life(life).curve(0.004, 0.7).color(c).peak(0.95).spawn();
		if (i % 4 == 0) {
			FxGlowBatch.sprite(GlowSprite.SPECK).at(x, y, z).vel(v[0] * 0.5, v[1] * 0.5, v[2] * 0.5).drag(0.9).gravity(7.0).size(0.12, 0.04).life(life * 1.6).curve(0.005, 0.8).color(c)
					.peak(0.9).spawn();
		}
	}

	// ============================================================================================================ ABILITY

	private static void abilityWorld(Vec3d at, double[] n, double damage, boolean blade, long seed, double far) {
		Random r = new Random(seed);
		double[] axis = BlockFxMath.bladeAxis(n[0], n[2], Math.toRadians(FxTune.d("block.tilt", 35.0)), (seed & 1L) != 0L);
		int total = (int) Math.round(BlockFxMath.sparkCount(BlockFxMath.ABILITY, damage, FxTune.d("ability.sparks", 1.0)) * far);
		lastSparks = total;
		scrape(at, n, axis, total, blade ? 2 : 1, seed, r, !blade, far);
		double glow = FxTune.d("block.glow", 1.0);
		FxGlowBatch.sprite(GlowSprite.STAR4).at(at.x, at.y, at.z).size(0.7, 0.25).sizeEase(FxMath.OC).life(0.1).curve(0.005, 0.7).color(blade ? WHITE : ICE).peak(0.8 * glow)
				.rot(r.nextDouble() * 6.28, 0).spawn();
		double v = FxTune.d("block.sound", 1.0);
		double ps = 0.92 + r.nextDouble() * 0.16;
		sound(SoundIds.GENERAL_BLOCK_ABILITY, "item.shield.block", at, 1.35 * ps, 0.55 * v);
		if (blade) {
			sound(SoundIds.GENERAL_BLOCK_ABILITY, "block.anvil.land", at, 1.9 * ps, 0.18 * v);
			sound(SoundIds.GENERAL_BLOCK_ABILITY, "entity.iron_golem.damage", at, 1.7 * ps, 0.3 * v);
		} else {
			sound(SoundIds.GENERAL_BLOCK_ABILITY, "block.amethyst_block.hit", at, 1.7 * ps, 0.35 * v);
		}
	}

	// ============================================================================================================ PARRY

	private static void parryWorld(Vec3d at, double[] n, double damage, long seed, double far) {
		Random r = new Random(seed);
		double[] axis = BlockFxMath.bladeAxis(n[0], n[2], Math.toRadians(FxTune.d("block.tilt", 35.0)), (seed & 1L) != 0L);
		int total = (int) Math.round(BlockFxMath.sparkCount(BlockFxMath.PARRY, damage, FxTune.d("parry.burst", 1.0)) * far);
		lastSparks = total;
		int burst = (int) Math.round(total * 0.7);
		// the burst: white-gold sparks over a cone towards the attacker
		double[][] bv = BlockFxMath.burstVelocities(seed, burst, n, Math.toRadians(80.0), 6.0, 19.0);
		for (int i = 0; i < bv.length; i++) {
			float[] c = i % 3 == 0 ? WHITE : i % 3 == 1 ? PALE : GOLD;
			double life = 0.2 + 0.32 * r.nextDouble();
			FxGlowBatch.sprite(GlowSprite.STREAK).at(at.x, at.y, at.z).vel(bv[i][0], bv[i][1], bv[i][2]).drag(0.9).gravity(7.0).streak().streakTime(0.05).size(0.06).life(life)
					.curve(0.004, 0.7).color(c).peak(1.0).spawn();
		}
		// and the sliding sparks of the guard on top, so it reads as a clash that went wrong for the attacker
		scrape(at, n, axis, total - burst, 2, seed, r, false, far);
		double glow = FxTune.d("parry.glow", 1.0);
		double ringR = FxTune.d("parry.ringRadius", 2.2);
		// two expanding rings standing in front of the defender (they face the attacker), the second wider and softer
		ring(GlowSprite.RING_THIN, 0.4375, at, n, 0.2, ringR, 0.25, PALE, glow, 0.0);
		ring(GlowSprite.RING_SOFT, 0.375, at, n, 0.3, ringR * 1.55, 0.32, GOLD, 0.6 * glow, 0.03);
		// the core flash, the horizontal flare, a big four point star
		FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(at.x, at.y, at.z).size(1.8, 3.4).sizeEase(FxMath.OC).life(0.16).curve(0.004, 0.75).color(WHITE).peak(glow).spawn();
		FxGlowBatch.sprite(GlowSprite.FLARE).at(at.x, at.y, at.z).size(4.6).life(0.11).curve(0.004, 0.7).color(PALE).peak(0.9 * glow).rot(0, 0).spawn();
		FxGlowBatch.sprite(GlowSprite.STAR4).at(at.x, at.y, at.z).size(2.8, 1.0).sizeEase(FxMath.OC).life(0.15).curve(0.004, 0.7).color(WHITE).peak(glow).rot(r.nextDouble() * 6.28, 3.0)
				.spawn();
		// radial crack lines in the plane of the rings
		double[] u = {-n[2], 0.0, n[0]};
		double[] w = {0.0, 1.0, 0.0};
		for (int k = 0; k < 6; k++) {
			double a = (k + r.nextDouble() * 0.5) * Math.PI / 3.0;
			double dx = u[0] * Math.cos(a) + w[0] * Math.sin(a);
			double dy = u[1] * Math.cos(a) + w[1] * Math.sin(a);
			double dz = u[2] * Math.cos(a) + w[2] * Math.sin(a);
			FxGlowBatch.sprite(GlowSprite.STREAK).at(at.x + dx * 0.9, at.y + dy * 0.9, at.z + dz * 0.9).vel(dx, dy, dz).streak().size(1.8 + r.nextDouble() * 0.8).life(0.09).curve(0.003, 0.8)
					.color(WHITE).peak(glow).spawn();
		}
		// a late shower of slow gold embers
		FxDelayed.after(0.06, () -> {
			int embers = (int) Math.round(16 * far);
			for (int i = 0; i < embers; i++) {
				double a = r.nextDouble() * Math.PI * 2.0;
				double s = 0.6 + r.nextDouble() * 1.4;
				FxGlowBatch.sprite(GlowSprite.SPECK).at(at.x + n[0] * 0.2, at.y, at.z + n[2] * 0.2).vel(Math.cos(a) * s, 0.5 + r.nextDouble() * 1.2, Math.sin(a) * s).drag(0.94).gravity(1.5)
						.size(0.16, 0.05).life(0.5 + r.nextDouble() * 0.4).curve(0.01, 0.8).color(i % 2 == 0 ? GOLD : PALE).peak(0.85).twinkle().spawn();
			}
		});
		// the sound: a loud high ting (chime chord + bell + trident) and a short crack of the deflect, pitched by parry.pitch
		double v = FxTune.d("parry.sound", 1.0);
		double pt = FxTune.d("parry.pitch", 1.0);
		double ps = 0.97 + r.nextDouble() * 0.06;
		sound(SoundIds.GENERAL_PARRY, "block.amethyst_block.chime", at, Math.min(2.0, 2.0 * pt * ps), 1.0 * v);
		FxDelayed.after(0.035, () -> sound(SoundIds.GENERAL_PARRY, "block.amethyst_block.chime", at, Math.min(2.0, 1.5 * pt * ps), 0.8 * v));
		sound(SoundIds.GENERAL_PARRY, "block.bell.use", at, Math.min(2.0, 2.0 * pt), 0.45 * v);
		sound(SoundIds.GENERAL_PARRY, "item.trident.hit", at, 1.9 * pt * ps, 0.8 * v);
		sound(SoundIds.GENERAL_PARRY, "item.trident.hit_ground", at, 1.7 * pt, 0.45 * v);
		sound(SoundIds.GENERAL_PARRY, "entity.player.attack.crit", at, 1.6 * pt, 0.7 * v);
		sound(SoundIds.GENERAL_PARRY, "item.shield.block", at, 1.7 * pt, 0.5 * v);
		FxDelayed.after(0.03, () -> sound(SoundIds.GENERAL_PARRY, "entity.iron_golem.damage", at, 1.8 * pt, 0.35 * v));
	}

	/** An expanding ring standing in the plane perpendicular to the horizontal unit vector {@code n} (it faces the attacker). */
	private static void ring(GlowSprite sprite, double k, Vec3d at, double[] n, double fromR, double toR, double life, float[] colour, double peak, double delay) {
		FxGlowBatch.sprite(sprite).at(at.x, at.y, at.z).plane(n[0], n[1], n[2]).size(Math.max(0.05, fromR) / k, toR / k).sizeEase(FxMath.OC).life(life).curve(0.006, 0.75).color(colour)
				.peak(peak).delay(delay).spawn();
	}

	private static void sound(String event, String vanilla, Vec3d at, double pitch, double volume) {
		FxSound.play(null, event, vanilla, at.x, at.y, at.z, pitch, volume);
	}
}
