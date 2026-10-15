package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.RukiaAim;
import dev.minebleach.reiatsutest.core.sound.SoundIds;
import dev.minebleach.reiatsutest.core.fx.RukiaFxParams;
import dev.minebleach.reiatsutest.core.fx.ShirafuneTimeline;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Rukia shikai abilities (VFX_STORYBOARD 3.1 to 3.3): Tsukishiro (id 20, a ring of frost, a pillar of light, rim crystals,
 * shatter at 2.0), Hakuren (id 21, a fan of ice spikes running along the look at the server's step speed) and Shirafune (id 22,
 * a blade of ice thrust along the aim ray). Radii, lengths, widths and step speeds come from the event params
 * ({@link RukiaFxParams}), never from constants: the storyboard numbers are written for the reference radius (Tsukishiro 4, Hakuren
 * 12 x 4) and every size, count and crystal scale follows the server value (BalanceConfig after the b4 balance pass: radius 7,
 * 20 x 7, reach 14). The shells on the targets are the entity fx ({@link RukiaEntityFx}); the server hit (kind HIT) shatters them.
 * Every number can be tuned live through {@code FxTune} keys {@code tsuki.*}, {@code hak.*}, {@code shira.*} and the global
 * {@code rk.count}, {@code rk.glow}, {@code rk.size} (see design/results/p6_rukia.md for the list).
 */
public final class RukiaShikaiFx {
	/** Statistics for the harness: the numbers of the last events as the client read them from the params. */
	public static double lastTsukishiroRadius;
	public static double lastHakurenLength;
	public static double lastHakurenHalfWidth;
	public static int lastHakurenSteps;
	public static double lastShirafuneReach;

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

	private static String col(String key, String def) {
		return FxTune.s(key, def);
	}

	private static double g(double peak) {
		return RukiaKit.glow(peak);
	}

	// ============================================================================================================ 3.1 Tsukishiro

	private static void tsukishiro(EffectTimeline t) {
		double R = Math.max(1.5, RukiaFxParams.param(t.params, 3, 4.0));
		double H = RukiaFxParams.param(t.params, 4, R * RukiaFxParams.TSUKISHIRO_PILLAR_PER_RADIUS);
		lastTsukishiroRadius = R;
		double kR = RukiaFxParams.radiusScale(R, RukiaFxParams.REF_TSUKISHIRO_RADIUS, 0.6);
		double sk = Math.sqrt(kR);
		Vec3d c = t.pos;
		double gy = c.y;
		double[] f = RukiaKit.flat(t.dir);
		double a0 = Math.atan2(f[1], f[0]);
		List<FxMeshPass.Crystal> rim = new ArrayList<>();
		final ScreenFx.Layer[] vig = new ScreenFx.Layer[1];
		t.lifetime(3.1);

		// 0.0 wind-up: a comet of motes sweeping the ring, 360 degrees in 0.2 s from the caster's yaw
		t.at(0.0, x -> x.sound(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_WINDUP, "entity.player.attack.sweep", 1.1, 0.7));
		int steps = (int) FxMath.clamp(R * d("tsuki.windSteps", 5.0), 20, 48);
		double windT = d("tsuki.windTime", 0.2);
		for (int i = 0; i < steps; i++) {
			final int k = i;
			t.at(windT * i / steps, x -> {
				double a = a0 + Math.PI * 2 * k / steps;
				double px = c.x + Math.cos(a) * R;
				double pz = c.z + Math.sin(a) * R;
				Random r = x.rng(100 + k);
				double py = FxGround.sample(px, pz, gy);
				FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(px, py + 0.3 + r.nextDouble() * 0.3, pz).vel(-Math.sin(a) * 2, 1.0, Math.cos(a) * 2).drag(0.95).life(14)
						.size(RukiaKit.size(d("tsuki.moteSize", 0.42))).color(col("tsuki.colMote", "#F0F8FF")).seed(k).fade(0.4).owner(x).spawn();
				FxGlowBatch.sprite(GlowSprite.SPECK).at(px, py + 0.35, pz).size(RukiaKit.size(d("tsuki.speck", 0.38))).life(0.4).curve(0.02, 0.7).color("#FFFFFF").peak(g(0.9)).owner(x).spawn();
				// the comet: a streak running along the tangent
				FxGlowBatch.sprite(GlowSprite.STREAK).at(px, py + 0.4, pz).vel(-Math.sin(a) * 26, 0.5, Math.cos(a) * 26).streak().streakTime(0.03).size(RukiaKit.size(1.4)).lifeTicks(3)
						.curve(0.01, 0.6).color("#DFF3FF").peak(g(0.9)).owner(x).spawn();
			});
		}
		// 0.2 ring complete: two sigils (the big one and a counter rotating inner one), a pulse running over the ring, the frost decal creeping out
		t.at(windT, x -> {
			FxGlowBatch.sprite(GlowSprite.FROST_SIGIL).at(c.x, gy + 0.05, c.z).ground().size(R * 2.1).life(2.6).curve(0.1, 0.2).rot(a0, 2 * Math.PI * 0.05)
					.color(col("tsuki.colSigil", "#BFE4FF")).peak(g(d("tsuki.sigil", 0.9))).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.FROST_SIGIL).at(c.x, gy + 0.06, c.z).ground().size(R * 1.25).life(2.6).curve(0.14, 0.2).rot(-a0, -2 * Math.PI * 0.09)
					.color("#EAF8FF").peak(g(d("tsuki.sigilInner", 0.5))).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(c.x, gy + 0.07, c.z).ground().size(R * 0.6 / 0.4375, R * 1.0 / 0.4375).sizeEase(FxMath.OC).life(0.4).curve(0.02, 0.7)
					.color("#FFFFFF").peak(g(d("tsuki.ringPulse", 0.8))).owner(x).spawn();
			FxDecals.frost(c.x, c.z, gy, R * 1.05, 0).reveal(d("tsuki.decalReveal", 0.3)).fade(2.3, 0.5).alpha(d("tsuki.decal", 0.9));
			x.sound(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_PILLAR, "block.amethyst_block.chime", 1.0, 0.7);
		});
		// 0.5 the pillar: two walls of light (core 0.825 R, outer R), 0 to H in 0.35 s, scrolling up, collapsing at 2.0
		t.at(0.5, x -> {
			double rise = d("tsuki.riseTime", 0.35);
			FxShapes.Wall core = FxShapes.wall(c.x, c.z, gy, 16, 0).radius(a -> R * 0.825).height(a -> H * FxMath.oc(a / rise) * collapse(a, 1.5)).topFade(d("tsuki.coreTopFade", 0.55)).scroll(2.0, 0.2);
			core.color(col("tsuki.colCore", "#EAF8FF")).intensity(a -> d("tsuki.core", 0.2) * FxMath.clamp(a / 0.05) * (1 - FxMath.clamp((a - 1.5) / 0.3))).life(1.9).owner(x).cell(GlowSprite.PILLAR);
			FxShapes.Wall outer = FxShapes.wall(c.x, c.z, gy, 16, 0).radius(a -> R).height(a -> H * FxMath.oc(a / rise) * collapse(a, 1.5)).topFade(d("tsuki.outerTopFade", 0.4)).scroll(1.5, 0.25);
			outer.color(col("tsuki.colOuter", "#CFEFFF")).intensity(a -> d("tsuki.outer", 0.10) * FxMath.clamp(a / 0.05) * (1 - FxMath.clamp((a - 1.5) / 0.3))).life(1.9).owner(x).cell(GlowSprite.PILLAR);
			// a bright ring at the foot of the pillar
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(c.x, gy + 0.08, c.z).ground().size(R / 0.375).life(1.5).curve(0.1, 0.6).color("#EAF8FF").peak(g(d("tsuki.footRing", 0.55))).owner(x).spawn();
			x.flash(d("tsuki.flash", 0.30), "#EAF8FF", true);
			x.sound(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_PILLAR, "item.trident.thunder", 1.4, 0.3);
			x.sound(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_PILLAR, "block.beacon.power_select", 1.2, 0.5);
		});
		int risers = 12;
		for (int k = 0; k < risers; k++) {
			final int kk = k;
			t.at(0.5 + 0.03 * k, x -> {
				Random r = x.rng(200 + kk);
				RukiaKit.frostMotes(x, r, RukiaKit.areaCount(10, R, 4.0), c.x, gy + 0.1, c.z, R * 0.95, 0.8, 4.0, 0.3, 20, d("tsuki.moteSize", 0.42), "#EAF8FF", "#CFEFFF", 1.0);
				if (kk % 3 == 0) {
					RukiaKit.stars(x, r, GlowSprite.STAR6, RukiaKit.areaCount(4, R, 4.0), c.x, gy + 0.3, c.z, R * 0.9, 1.0, 4.0, 0.2, 1.1, 0.7, "#FFFFFF", 0.8);
				}
			});
		}
		// 1.0 freeze: rim crystals (a crust of ice standing on the circle), ice dust, frost creeps in at the edges of the screen
		t.at(1.0, x -> {
			Random r = x.rng(300);
			int n = (int) FxMath.clamp(Math.round(R * d("tsuki.crystalsPerBlock", 2.5)), 8, 20);
			double cs = d("tsuki.crystalScale", 2.1) * sk;
			for (int i = 0; i < n; i++) {
				double a = Math.PI * 2 * (i + r.nextDouble() * 0.7) / n;
				double rad = R * (0.9 + 0.12 * r.nextDouble());
				double px = c.x + Math.cos(a) * rad;
				double pz = c.z + Math.sin(a) * rad;
				double py = FxGround.sample(px, pz, gy);
				double lean = 0.15 + r.nextDouble() * 0.2;
				// every fourth is a tall spike (crystal d) so the crust has a skyline
				boolean tall = i % 4 == 1;
				String mesh = tall ? FxMeshes.CRYSTAL_D : (r.nextBoolean() ? FxMeshes.CRYSTAL_C : FxMeshes.CRYSTAL_B);
				FxMeshPass.Crystal cr = FxMeshPass.crystal(mesh, px, py - 0.03, pz, Math.cos(a) * lean, 1, Math.sin(a) * lean, r.nextDouble() * 6.28,
						(tall ? cs * 0.85 : cs) * (0.7 + 0.6 * r.nextDouble()), 0.04 * i);
				cr.field = -1;
				rim.add(cr);
			}
			// a ring of light on the rim, standing for the crust, held until the shatter
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(c.x, gy + 0.09, c.z).ground().size(R / 0.4375).life(1.0).curve(0.15, 0.4).color("#DFF3FF").peak(g(d("tsuki.crustRing", 0.55))).owner(x).spawn();
			vig[0] = x.vignette(ScreenFx.Kind.FROST, d("tsuki.vignette", 0.20), 0.3);
			x.sound(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_CRUST, "entity.player.hurt_freeze", 1.0, 0.8);
			x.sound(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_CRUST, "block.amethyst_cluster.place", 0.9, 0.6);
			x.soundAt(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_CRUST, "block.amethyst_cluster.place", c.add(R * 0.7, 0.5, 0), 1.2, 0.5);
			RukiaKit.iceShards(x, r, RukiaKit.areaCount(60, R, 4.0), c.x, gy + 0.2, c.z, R, 0.8, 1.2, 0.0, 60, 0.34);
		});
		// 1.5 hold: frost creeps, the sigil pulses at 3 Hz
		for (int k = 0; k < 3; k++) {
			t.at(1.5 + k / 3.0 * 0.5, x -> {
				Random r = x.rng(400);
				FxGlowBatch.sprite(GlowSprite.FROST_SIGIL).at(c.x, gy + 0.06, c.z).ground().size(R * 2.1).life(0.17).curve(0.04, 0.5).rot(a0, 0).color("#FFFFFF").peak(g(0.35)).owner(x).spawn();
				RukiaKit.frostMotes(x, r, RukiaKit.areaCount(5, R, 4.0), c.x, gy + 0.2, c.z, R, 1.5, 1.2, 0.3, 18, 0.34, "#DFF3FF", null, 0.9);
				RukiaKit.stars(x, r, GlowSprite.STAR6, RukiaKit.areaCount(3, R, 4.0), c.x, gy + 0.6, c.z, R * 0.9, 1.6, 0.4, 0.2, 0.6, 0.6, "#FFFFFF", 0.8);
			});
		}
		// 2.0 shatter: hit stop, then the pillar is gone, crystals and the ice burst (the shells burst on the server's HIT)
		t.at(2.0, x -> x.hitstop(d("tsuki.hitstopMs", 60)));
		t.at(2.0001, x -> {
			Random r = x.rng(500);
			for (FxMeshPass.Crystal cr : rim) {
				FxMeshPass.shatter(cr, (int) d("tsuki.crystalShards", 4), 6.5 * sk, r.nextLong());
			}
			RukiaKit.iceShards(x, r, RukiaKit.areaCount(140, R, 4.0), c.x, gy + 0.3, c.z, R * 0.9, d("tsuki.burstSpeed", 5.0) * sk, 4.5, 1.0, 26, 0.42);
			RukiaKit.frostMotes(x, r, RukiaKit.areaCount(80, R, 4.0), c.x, gy + 0.4, c.z, R * 0.9, 2.0, 2.0, 5.0 * sk, 22, 0.42, "#FFFFFF", "#BFE4FF", 1.0);
			RukiaKit.stars(x, r, GlowSprite.STAR4, RukiaKit.areaCount(40, R, 4.0), c.x, gy + 0.8, c.z, R * 0.9, 2.0, 3.0, 5.0 * sk, 0.4, 0.8, "#FFFFFF", 0.95);
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(c.x, gy + 0.1, c.z).ground().size(R * 0.6 / 0.375, R * 1.3 / 0.375).sizeEase(FxMath.OC).life(0.45).curve(0.02, 0.7).color("#FFFFFF")
					.peak(g(d("tsuki.burstRing", 0.8))).spawn();
			x.shake(d("tsuki.shake", 0.6), 0.35);
			if (vig[0] != null) {
				vig[0].release(0.5);
			}
			x.soundAt(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_SHATTER, "block.glass.break", c.add(0, 1, 0), 1.0, 1.0);
		});
		t.at(2.07, x -> x.sound(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_SHATTER, "block.glass.break", 1.2, 0.9));
		t.at(2.14, x -> x.sound(SoundIds.RUKIA_SHIKAI_TSUKISHIRO_SHATTER, "block.glass.break", 0.8, 0.8));
		// 2.5 fade: a last drift of motes
		t.at(2.5, x -> RukiaKit.frostMotes(x, x.rng(600), RukiaKit.areaCount(20, R, 4.0), c.x, gy + 0.3, c.z, R, 1.0, 0.8, 0.5, 20, 0.34, "#DFF3FF", null, 0.8));
	}

	/** Collapse factor: 1 until {@code from}, then 1 to 0 in 0.25 s (easeInQuad), age in seconds. */
	private static double collapse(double age, double from) {
		return 1.0 - FxMath.iq((age - from) / 0.25);
	}

	// ============================================================================================================ 3.2 Hakuren

	private static void hakuren(EffectTimeline t) {
		double L = Math.max(3.0, RukiaFxParams.param(t.params, 3, 12.0));
		double step = RukiaFxParams.param(t.params, 4, L / RukiaFxParams.HAKUREN_STEPS);
		int steps = (int) Math.max(1, RukiaFxParams.param(t.params, 5, RukiaFxParams.HAKUREN_STEPS));
		double half = Math.max(0.8, RukiaFxParams.param(t.params, 6, RukiaFxParams.HAKUREN_HALF_WIDTH));
		lastHakurenLength = L;
		lastHakurenHalfWidth = half;
		lastHakurenSteps = steps;
		double wS = FxMath.clamp(half / RukiaFxParams.HAKUREN_HALF_WIDTH, 0.6, 2.0);
		double cs = d("hak.scale", 1.0) * FxMath.clamp(Math.sqrt(wS), 0.8, 1.5);
		int perStep = (int) FxMath.clamp(Math.round(d("hak.crystalsTotal", 30) / steps), 3, 6);
		double revert = d("hak.revertAfter", 3.0);
		Vec3d c = t.pos;
		double gy = c.y;
		double[] f = RukiaKit.flat(t.dir);
		double[] rg = RukiaKit.right(f);
		// the wave leaves along the look of the moment it starts (the server reads its own at the same phase, AbilityExecutor.hakuren), not
		// the one of the key press: f, rg and cp (feet x, y, z) are rewritten in place at that moment, before the first step is drawn
		double[] cp = {c.x, c.y, c.z};
		List<FxMeshPass.Crystal> punctures = new ArrayList<>();
		List<List<FxMeshPass.Crystal>> stepCrystals = new ArrayList<>();
		for (int k = 0; k < steps; k++) {
			stepCrystals.add(new ArrayList<>());
		}
		t.lifetime(1.0 + 0.1 * steps + revert + 0.6);

		// 0.0 ground puncture under her
		t.at(0.0, x -> {
			RukiaKit.ring(x, c.x, gy + 0.05, c.z, 2.0, 2.0, 0.5, col("hak.colRing", "#C0D3E7"), 1.0, false);
			RukiaKit.frostMotes(x, x.rng(10), 30, c.x, gy + 0.1, c.z, 2.0, 0.4, 3.0, 0.3, 16, 0.4, "#EAF8FF", "#CFEFFF", 1.0);
			x.sound(SoundIds.RUKIA_SHIKAI_HAKUREN_START, "block.glass.break", 1.2, 0.5);
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
				punctures.add(cr);
				x.soundAt(SoundIds.RUKIA_SHIKAI_HAKUREN_SPIKES, "block.amethyst_cluster.break", new Vec3d(px, py + 0.5, pz), 1.0 + 0.1 * kk, 0.7);
			});
		}
		// 0.7 gather at the blade tip
		t.at(0.7, x -> {
			Vec3d tip = RukiaKit.tip(x, 0.9, 0.0);
			FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(tip.x, tip.y, tip.z).size(RukiaKit.size(0.9), RukiaKit.size(0.35)).sizeEase(FxMath.IQ).life(0.3).curve(0.05, 0.4).color("#EAF8FF").peak(g(0.9)).owner(x).spawn();
			Random r = x.rng(30);
			int n = RukiaKit.count(30);
			for (int i = 0; i < n; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double b = (r.nextDouble() - 0.5) * 2.0;
				double rad = 1.0 + r.nextDouble() * 0.4;
				double px = tip.x + Math.cos(a) * rad;
				double py = tip.y + b * 0.6;
				double pz = tip.z + Math.sin(a) * rad;
				FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(px, py, pz).vel((tip.x - px) / 0.3, (tip.y - py) / 0.3, (tip.z - pz) / 0.3).drag(1.0).life(6).size(0.3)
						.color("#FFFFFF").seed(i).fade(0.5).owner(x).spawn();
			}
			x.sound(SoundIds.RUKIA_SHIKAI_HAKUREN_FLARE, "block.beacon.power_select", 1.3, 0.6);
		});
		// the end of the cast: the owner's look and feet now (live entity, so remote viewers see it too); not loaded = the press values stay
		double aimAt = RukiaAim.aimSeconds(BalanceConfig.defaults().spec(AbilityId.HAKUREN));
		t.at(aimAt < 0 ? 1.0 : aimAt, x -> {
			Entity ow = x.caster();
			if (ow == null) {
				return;
			}
			Vec3d look = ow.getRotationVec(1.0f);
			Vec3d feet = ow.getLerpedPos(1.0f);
			RukiaAim.flat(look.x, look.z, f[0], f[1], f);
			rg[0] = -f[1];
			rg[1] = f[0];
			cp[0] = feet.x;
			cp[1] = feet.y;
			cp[2] = feet.z;
		});
		// 1.0 and on: the wave, one step every 0.1 s (about 24 blocks per second for the standard step); each step lifts a stagger of crystals across the fan
		double speed = step * 10.0;
		for (int k = 0; k < steps; k++) {
			final int kk = k;
			t.at(1.0 + 0.1 * k, x -> {
				Random r = x.rng(40 + kk);
				double d0 = step * kk;
				double dc = step * (kk + 1);
				for (int j = 0; j < perStep; j++) {
					// lateral position across the whole fan, staggered every other step so the rows interlock
					double u = (j + 0.5 * (kk % 2) + r.nextDouble() * 0.5) / perStep;
					double lat = -half + 2.0 * half * Math.min(0.98, u);
					double along = d0 + step * (0.25 + 0.75 * r.nextDouble());
					double px = cp[0] + f[0] * along + rg[0] * lat;
					double pz = cp[2] + f[1] * along + rg[1] * lat;
					double py = FxGround.sample(px, pz, cp[1]);
					double lean = Math.tan(Math.toRadians(25 + r.nextDouble() * 10));
					boolean big = j % 3 == 2;
					FxMeshPass.Crystal cr = FxMeshPass.crystal(big ? FxMeshes.CRYSTAL_D : FxMeshes.CRYSTAL_C, px, py - 0.04, pz, f[0] * lean + rg[0] * (r.nextDouble() - 0.5) * 0.3, 1,
							f[1] * lean + rg[1] * (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 6.28, (big ? d("hak.bigScale", 2.2) : d("hak.smallScale", 2.0)) * cs * (0.75 + 0.5 * r.nextDouble()),
							0.01 * j);
					cr.grow = d("hak.grow", 0.12);
					stepCrystals.get(kk).add(cr);
				}
				// a ripple of light on the ground where the step lands, and the mist along the path
				FxGlowBatch.sprite(GlowSprite.RING_THIN).at(cp[0] + f[0] * (d0 + step * 0.5), cp[1] + 0.07, cp[2] + f[1] * (d0 + step * 0.5)).ground().size(0.5 / 0.4375, (half + 0.8) / 0.4375)
						.sizeEase(FxMath.OC).life(0.35).curve(0.02, 0.7).color("#DFF3FF").peak(g(d("hak.ripple", 0.55))).owner(x).spawn();
				int nm = Math.max(3, (int) Math.round(8 * wS));
				for (int j = 0; j < nm; j++) {
					double along = d0 + step * r.nextDouble();
					double lat = (r.nextDouble() - 0.5) * 2.0 * half;
					FxGlowBatch.sprite(GlowSprite.MIST).at(cp[0] + f[0] * along + rg[0] * lat, cp[1] + 0.2 + r.nextDouble() * 0.5, cp[2] + f[1] * along + rg[1] * lat).ground()
							.rot(r.nextDouble() * 6.28, 0.1).size(RukiaKit.size(3.0 + r.nextDouble())).vel(f[0] * 0.8, 0.1, f[1] * 0.8).life(2.0).curve(0.15, 0.7).color("#EAF8FF").peak(g(d("hak.mist", 0.22)))
							.owner(x).spawn();
				}
				int nf = RukiaKit.count((int) Math.round(220.0 / steps * wS));
				for (int j = 0; j < nf; j++) {
					double along = d0 + step * r.nextDouble();
					double lat = (r.nextDouble() - 0.5) * 2.0 * half;
					FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(cp[0] + f[0] * along + rg[0] * lat, cp[1] + 0.1 + r.nextDouble() * 2.2, cp[2] + f[1] * along + rg[1] * lat)
							.vel(f[0] * 3, 1.5 + r.nextDouble(), f[1] * 3).drag(0.95).life(16 + r.nextInt(8)).size(RukiaKit.size(0.38)).seed(j).color("#FFFFFF").colorTo("#BFE4FF").fade(0.4).owner(x).spawn();
				}
				int ni = RukiaKit.count((int) Math.round(40.0 / steps * wS));
				for (int j = 0; j < ni; j++) {
					double along = d0 + step * r.nextDouble();
					double lat = (r.nextDouble() - 0.5) * 2.0 * half;
					FxParticles.spec(FxParticles.Kind.ICE_SHARD).at(cp[0] + f[0] * along + rg[0] * lat, cp[1] + 0.2 + r.nextDouble() * 1.5, cp[2] + f[1] * along + rg[1] * lat)
							.vel(f[0] * 2, 2 + r.nextDouble() * 2, f[1] * 2).drag(0.95).gravity(0.6).life(26).size(RukiaKit.size(0.36)).seed(j).spin((r.nextDouble() - 0.5) * 0.4).fade(0.3).owner(x).spawn();
				}
				x.soundAt(SoundIds.RUKIA_SHIKAI_HAKUREN_TRAIL, "block.amethyst_cluster.place", new Vec3d(cp[0] + f[0] * dc, cp[1] + 0.5, cp[2] + f[1] * dc), 0.8 + 0.1 * Math.min(kk, 7), 0.6);
			});
		}
		// the leading edge: three flares standing across the path, running at the step speed
		t.at(1.0, x -> {
			double life = Math.max(0.3, L / speed);
			for (int i = 0; i < 3; i++) {
				FxGlowBatch.sprite(GlowSprite.FLARE).at(cp[0] + f[0] * 0.3, cp[1] + 1.2, cp[2] + f[1] * 0.3).plane(f[0], 0, f[1]).size(RukiaKit.size(d("hak.flare", 1.25) * 2.0 * half * Math.max(0.8, cs * 0.8)))
						.vel(f[0] * speed, 0, f[1] * speed).delay(0.03 * i).life(life).curve(0.04, 0.35).color(i == 0 ? "#EAF8FF" : "#C0D3E7").peak(g(0.55 - 0.12 * i)).owner(x).spawn();
			}
			x.shake(d("hak.shake", 0.5), 0.4);
			x.sound(SoundIds.RUKIA_SHIKAI_HAKUREN_WAVE, "entity.breeze.wind_burst", 1.2, 0.8);
			x.sound(SoundIds.RUKIA_SHIKAI_HAKUREN_WAVE, "block.powder_snow.break", 0.9, 0.8);
		});
		// 1.5 frost on the real blocks
		t.at(1.5, x -> {
			Random r = x.rng(50);
			int n = RukiaKit.count((int) Math.round(60 * Math.max(0.6, L / 12.0)));
			for (int i = 0; i < n; i++) {
				double along = L * r.nextDouble();
				double lat = (r.nextDouble() - 0.5) * 2.0 * half;
				double px = cp[0] + f[0] * along + rg[0] * lat;
				double pz = cp[2] + f[1] * along + rg[1] * lat;
				FxParticles.spec(FxParticles.Kind.ICE_SHARD).at(px, cp[1] + 0.2, pz).vel(0, 1.0 + r.nextDouble(), 0).drag(0.94).gravity(0.2).life(26).size(RukiaKit.size(0.34)).seed(i).spin(0.1).fade(0.3)
						.owner(x).spawn();
				if (i % 3 == 0) {
					FxGlowBatch.sprite(GlowSprite.STAR6).at(px, cp[1] + 0.4 + r.nextDouble(), pz).size(RukiaKit.size(0.5)).lifeTicks(12).color("#FFFFFF").peak(g(0.85)).twinkle().rot(r.nextDouble() * 6, 1).owner(x).spawn();
				}
			}
			x.sound(SoundIds.RUKIA_SHIKAI_HAKUREN_FREEZE, "entity.player.hurt_freeze", 1.0, 0.6);
		});
		// the crystals shatter as the temporary blocks revert (the server places a step's blocks at 1.0 + 0.1 k and removes them 3 s later)
		t.at(0.3 + 0.06 * 3 + revert - 0.1, x -> {
			Random r = x.rng(55);
			for (FxMeshPass.Crystal cr : punctures) {
				FxMeshPass.shatter(cr, 3, 3.5, r.nextLong());
			}
		});
		for (int k = 0; k < steps; k++) {
			final int kk = k;
			t.at(1.0 + 0.1 * k + revert, x -> {
				Random r = x.rng(60 + kk);
				int shards = (int) d("hak.crystalShards", 4);
				for (FxMeshPass.Crystal cr : stepCrystals.get(kk)) {
					FxMeshPass.shatter(cr, shards, 4.5, r.nextLong());
				}
				double along = step * (kk + 0.5);
				RukiaKit.frostMotes(x, r, Math.max(4, (int) Math.round(40.0 / steps * wS)), cp[0] + f[0] * along, cp[1] + 0.5, cp[2] + f[1] * along, half, 1.5, 1.0, 2.0, 20, 0.4, "#FFFFFF", "#BFE4FF", 0.9);
				if (kk % 2 == 0) {
					x.soundAt(SoundIds.RUKIA_SHIKAI_HAKUREN_SHATTER, "block.glass.break", new Vec3d(cp[0] + f[0] * along, cp[1] + 0.5, cp[2] + f[1] * along), 1.0 + 0.05 * kk, 0.4);
				}
			});
		}
	}

	// ============================================================================================================ 3.3 Shirafune

	private static void shirafune(EffectTimeline t) {
		double reach = Math.max(2.0, RukiaFxParams.param(t.params, 3, 8.0));
		lastShirafuneReach = reach;
		Vec3d aim = t.params.length >= 3 ? new Vec3d(t.params[0], t.params[1], t.params[2]) : t.pos.add(t.dir.multiply(reach));
		Vec3d tip0 = RukiaKit.tip(t, 0.9, 0.0);
		Vec3d to = aim.subtract(tip0);
		double dist = to.length();
		Vec3d bd = dist < 0.6 ? (t.dir.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : t.dir.normalize()) : to.multiply(1.0 / dist);
		double U = Math.max(1.5, Math.min(dist, reach + 1.0));
		Vec3d end = tip0.add(bd.multiply(U));
		double lk = FxMath.clamp(U / 8.0, 0.8, 2.0);
		final FxMeshPass.Crystal[] tipCrystal = new FxMeshPass.Crystal[1];
		// r8 fx2 item 3: the event arrives at the RELEASE (the server hits at the same moment), so the clock starts at the hit: the blade grows
		// at once and the contact lands when it has reached its end. The 0.6 s of cast time before it is the local telegraph (ShirafuneCast).
		ShirafuneTimeline tl = ShirafuneTimeline.atRelease(d("shira.lead", 0.0), d("shira.growTime", ShirafuneTimeline.GROW));
		t.lifetime(tl.lifetime);
		if (t.localCaster) {
			ShirafuneCast.onRelease();
		}

		// at the release: a quick mote swirl that closes on the blade tip while the blade leaves (0.15 s, the old lead-in was 0.4 s before the blade)
		t.at(tl.bladeAt, x -> {
			Random r = x.rng(70);
			int n = RukiaKit.count(30);
			for (int i = 0; i < n; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double b = (r.nextDouble() - 0.5) * 2;
				double px = tip0.x + Math.cos(a) * 1.0;
				double py = tip0.y + b * 0.5;
				double pz = tip0.z + Math.sin(a) * 1.0;
				FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(px, py, pz).vel((tip0.x - px) / 0.15 - Math.sin(a) * 1.5, (tip0.y - py) / 0.15, (tip0.z - pz) / 0.15 + Math.cos(a) * 1.5).drag(1.0)
						.life(3).size(RukiaKit.size(0.36)).seed(i).color("#FFFFFF").colorTo("#9ED3F0").fade(0.4).owner(x).spawn();
			}
			RukiaKit.stars(x, r, GlowSprite.STAR6, 10, tip0.x, tip0.y, tip0.z, 0.6, 0.6, 0.0, 0.0, 0.3, 0.55, "#FFFFFF", 0.9);
			x.sound(SoundIds.RUKIA_SHIKAI_SHIRAFUNE_CHARGE, "block.amethyst_block.resonate", 1.4, 0.4);
		});
		// the blade of ice grows along the aim in 0.2 s (easeOutExpo), a crystal rides the tip
		t.at(tl.bladeAt, x -> {
			dev.minebleach.reiatsutest.ReiatsuTest.LOGGER.info("[fx] shirafune blade tip0={} end={} U={} dir={}", tip0, end, U, bd);
			double grow = d("shira.growTime", 0.2);
			FxShapes.Strip glow = FxShapes.strip(tip0.x, tip0.y, tip0.z, bd.x, bd.y, bd.z, 0).length(a -> bladeLen(a, U, grow)).width(a -> d("shira.glowWidth", 0.8) * FxMath.clamp(1.0 - (a - 1.7) / 0.2));
			glow.color(col("shira.colGlow", "#7FB8DF")).intensity(a -> d("shira.glow", 0.55) * (0.85 + 0.15 * Math.sin(a * 20))).life(1.95).owner(x);
			FxShapes.Strip core = FxShapes.strip(tip0.x, tip0.y, tip0.z, bd.x, bd.y, bd.z, 0).length(a -> bladeLen(a, U, grow)).width(a -> d("shira.coreWidth", 0.28) * FxMath.clamp(1.0 - (a - 1.7) / 0.2));
			core.color(col("shira.colCore", "#EAF8FF")).intensity(a -> d("shira.core", 1.0)).life(1.95).owner(x);
			Random r = x.rng(80);
			// a string of soft lights along the blade: visible from every angle (the crossed quads vanish when seen end on)
			int orbs = (int) FxMath.clamp(Math.round(U * 1.3), 8, 22);
			for (int i = 0; i < orbs; i++) {
				double fr = i / (double) (orbs - 1);
				double dd = 1 + (U - 1) * fr;
				Vec3d p = tip0.add(bd.multiply(dd));
				FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(p.x, p.y, p.z).size(RukiaKit.size(d("shira.orb", 0.8))).life(1.7 - 0.2 * fr * fr).delay(0.2 * fr * fr * 0.5).curve(0.04, 0.15).color("#DFF3FF")
						.peak(g(0.75)).owner(x).spawn();
			}
			int sheds = RukiaKit.count((int) Math.round(40 * lk));
			for (int i = 0; i < sheds; i++) {
				double fr = r.nextDouble();
				double dd = 1 + (U - 1) * FxMath.oe(fr);
				Vec3d p = tip0.add(bd.multiply(dd));
				FxParticles.spec(FxParticles.Kind.ICE_SHARD).at(p.x + (r.nextDouble() - 0.5) * 0.2, p.y + (r.nextDouble() - 0.5) * 0.2, p.z + (r.nextDouble() - 0.5) * 0.2)
						.vel((r.nextDouble() - 0.5) * 1.5, r.nextDouble() * 1.2, (r.nextDouble() - 0.5) * 1.5).drag(0.93).gravity(0.5).life(22).size(RukiaKit.size(0.26)).seed(i).spin(0.2).fade(0.3).owner(x).spawn();
			}
			FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(tip0.x, tip0.y, tip0.z).size(RukiaKit.size(0.6), RukiaKit.size(1.3)).life(0.3).color("#EAF8FF").peak(g(0.8)).owner(x).spawn();
			x.sound(SoundIds.RUKIA_SHIKAI_SHIRAFUNE_THROW, "item.trident.throw", 1.1, 0.8);
		});
		t.at(tl.crystalAt, x -> {
			Vec3d base = end.subtract(bd.multiply(0.85));
			tipCrystal[0] = FxMeshPass.crystal(FxMeshes.CRYSTAL_B, base.x, base.y, base.z, bd.x, bd.y, bd.z, 0.8, d("shira.tipScale", 2.4), 0.0);
			tipCrystal[0].grow = 0.12;
		});
		// the thrust arrives (half the growth time after the release, with the server hit): contact burst
		t.at(tl.contactAt, x -> {
			Random r = x.rng(90);
			FxGlowBatch.sprite(GlowSprite.FLARE).at(end.x, end.y, end.z).size(RukiaKit.size(d("shira.flare", 3.2))).life(0.18).curve(0.02, 0.7).color("#FFFFFF").peak(g(0.95)).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(end.x, end.y, end.z).size(RukiaKit.size(1.6), RukiaKit.size(2.6)).life(0.3).curve(0.02, 0.7).color("#CFEFFF").peak(g(0.7)).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(end.x, end.y, end.z).plane(bd.x, bd.y, bd.z).size(0.3 / 0.4375, 1.3 / 0.4375).sizeEase(FxMath.OC).life(0.3).curve(0.02, 0.7).color("#EAF8FF")
					.peak(g(0.8)).owner(x).spawn();
			RukiaKit.iceShards(x, r, 60, end.x, end.y - 0.3, end.z, 0.3, 4.0, 3.0, 0.8, 24, 0.4);
			RukiaKit.frostMotes(x, r, 40, end.x, end.y, end.z, 0.3, 0.8, 1.5, 4.0, 16, 0.4, "#FFFFFF", "#BFE4FF", 1.0);
			RukiaKit.stars(x, r, GlowSprite.STAR4, 24, end.x, end.y, end.z, 0.3, 0.6, 1.0, 4.0, 0.3, 0.7, "#FFFFFF", 0.95);
			x.shake(d("shira.shake", 0.4), 0.2);
			x.soundAt(SoundIds.RUKIA_SHIKAI_SHIRAFUNE_HIT, "entity.player.attack.crit", end, 1.0, 0.9);
			x.soundAt(SoundIds.RUKIA_SHIKAI_SHIRAFUNE_HIT, "block.glass.break", end, 1.3, 0.7);
		});
		t.at(tl.chillAt, x -> x.soundAt(SoundIds.RUKIA_SHIKAI_SHIRAFUNE_CHILL, "entity.player.hurt_freeze", end, 1.1, 0.6));
		// the blade retracts (0.2 s), the tip crystal shatters
		t.at(tl.retractAt, x -> {
			Random r = x.rng(95);
			if (tipCrystal[0] != null) {
				FxMeshPass.shatter(tipCrystal[0], (int) d("shira.tipShards", 6), 4.0, r.nextLong());
			}
			RukiaKit.iceShards(x, r, 30, end.x, end.y, end.z, 0.3, 3.0, 2.0, 1.5, 26, 0.36);
			x.soundAt(SoundIds.RUKIA_SHIKAI_SHIRAFUNE_RETRACT, "block.glass.break", end, 1.4, 0.3);
		});
	}

	/** Blade length: 1 to U in {@code grow} seconds (easeOutExpo), then retract 0.2 s from age 1.7 (t = 2.0). */
	private static double bladeLen(double age, double U, double grow) {
		double len = 1 + (U - 1) * FxMath.oe(age / Math.max(0.02, grow));
		if (age > 1.7) {
			len *= 1.0 - FxMath.clamp((age - 1.7) / 0.2);
		}
		return age < 0 ? 0 : len;
	}
}
