package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.RukiaFxParams;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Rukia bankai ability 1, the ice spike (round 6, effect id 24): a thrust of ice spikes along the look ray. Not a field and not a wave on
 * the ground like Hakuren: a narrow lance of light leaves the blade tip, and one segment after the other (the server hit segments, one every
 * {@code stepSeconds}) a pair of ice crystals erupts from the floor under the line with a flash, shards and frost motes; the crystals stay
 * for about a second and shatter. Event params {@code [aimX, aimY, aimZ, length, usedLength, steps, stepSeconds, halfWidth, firstSeconds]}
 * ({@link RukiaFxParams}); nothing is hard coded, a changed balance only changes the numbers the server sends. UNVERIFIED in the game.
 */
final class IceSpikeFx {
	private IceSpikeFx() {
	}

	static void play(EffectEventS2C e) {
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		dir = dir.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : dir.normalize();
		final Vec3d look = dir;
		EffectTimeline t = FxTimelines.create(24, e.seed(), pos, look, e.params(), e.casterId(), FxClient.forceRemote());
		double length = Math.max(4.0, RukiaFxParams.param(t.params, 3, 36.0));
		double used = Math.max(0.0, Math.min(length, RukiaFxParams.param(t.params, 4, length)));
		int steps = (int) Math.max(1, RukiaFxParams.param(t.params, 5, RukiaFxParams.HAKUREN_STEPS));
		double stepSec = Math.max(0.02, RukiaFxParams.param(t.params, 6, 0.1));
		double half = Math.max(0.5, RukiaFxParams.param(t.params, 7, 1.5));
		double first = Math.max(0.2, RukiaFxParams.param(t.params, 8, 0.7));
		double seg = length / steps;
		Vec3d eye = pos.add(0, 1.62, 0);
		double lunge = Math.max(0.0, first - FxTune.d("ispike.lead", 0.12));
		t.lifetime(first + steps * stepSec + 2.0);

		// 0.00 the blade gathers cold at its tip
		t.at(0.0, x -> {
			Vec3d tip = RukiaKit.tip(x, 0.9, 0.2);
			Random r = x.rng(1);
			int n = RukiaKit.count(26);
			for (int i = 0; i < n; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double rad = 0.8 + r.nextDouble() * 0.6;
				double px = tip.x + Math.cos(a) * rad;
				double py = tip.y + (r.nextDouble() - 0.5) * 1.6;
				double pz = tip.z + Math.sin(a) * rad;
				double life = Math.max(0.2, lunge);
				FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(px, py, pz).vel((tip.x - px) / life, (tip.y - py) / life, (tip.z - pz) / life).drag(1.0).life((int) Math.max(4, life * 20))
						.size(RukiaKit.size(0.3)).color("#FFFFFF").colorTo("#9ED3F0").seed(i).fade(0.4).owner(x).spawn();
			}
			FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(tip.x, tip.y, tip.z).size(0.3, 1.1).sizeEase(FxMath.OC).life(Math.max(0.2, lunge)).curve(0.1, 0.4).color("#CFEFFF").peak(RukiaKit.glow(0.9)).owner(x).spawn();
			x.sound("block.beacon.power_select", 1.5, 0.6);
		});
		// the thrust: a flare at the tip and a streak of light along the ray
		t.at(lunge, x -> {
			Vec3d tip = RukiaKit.tip(x, 1.1, 0.2);
			FxGlowBatch.sprite(GlowSprite.FLARE).at(tip.x, tip.y, tip.z).size(1.4, 2.8).sizeEase(FxMath.OC).life(0.25).curve(0.02, 0.8).color("#EAF8FF").peak(RukiaKit.glow(0.9)).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(tip.x, tip.y, tip.z).plane(look.x, look.y, look.z).size(0.3, 2.0).sizeEase(FxMath.OC).life(0.25).curve(0.02, 0.8).color("#FFFFFF")
					.peak(RukiaKit.glow(0.8)).owner(x).spawn();
			int line = (int) Math.min(40, Math.max(4, used / 1.2));
			for (int i = 0; i < RukiaKit.count(line); i++) {
				double d = used * (i + 1.0) / line;
				Vec3d p = eye.add(look.multiply(d));
				FxGlowBatch.sprite(GlowSprite.STAR4).at(p.x, p.y, p.z).size(RukiaKit.size(0.45)).life(0.18).curve(0.01, 0.8).delay(d / 140.0).color("#CFEFFF").peak(RukiaKit.glow(0.7)).twinkle()
						.rot(i * 0.7, 2).owner(x).spawn();
			}
			x.shake(0.4, 0.25);
			x.sound("entity.player.attack.sweep", 1.7, 0.7);
			x.sound("item.trident.throw", 1.4, 0.5);
		});
		// one burst per server segment, at the server times
		for (int k = 0; k < steps; k++) {
			final int kk = k;
			if (k * seg >= used) {
				break;
			}
			t.at(first + k * stepSec, x -> segment(x, eye, look, kk, seg, used, half, steps));
		}
		FxTimelines.start(t);
		RukiaBankaiClient.flare(e.casterId());
	}

	private static void segment(EffectTimeline x, Vec3d eye, Vec3d look, int k, double seg, double used, double half, int steps) {
		ClientWorld w = MinecraftClient.getInstance().world;
		if (w == null) {
			return;
		}
		Random r = x.rng(10 + k);
		double d0 = k * seg;
		double d1 = Math.min((k + 1) * seg, used);
		Vec3d side = new Vec3d(-look.z, 0, look.x);
		side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
		final FxMeshPass.Crystal[] made = new FxMeshPass.Crystal[2];
		String[] meshes = {FxMeshes.CRYSTAL_D, FxMeshes.CRYSTAL_C, FxMeshes.CRYSTAL_B};
		for (int j = 0; j < 2; j++) {
			double d = d0 + (d1 - d0) * (j == 0 ? 0.3 : 0.75);
			double off = (j == 0 ? -1 : 1) * half * (0.35 + 0.4 * r.nextDouble());
			Vec3d p = eye.add(look.multiply(d)).add(side.multiply(off));
			double gy = FxPlace.ground(w, p.x, p.z, p.y, 3.0, 26.0);
			if (Double.isNaN(gy)) {
				continue;
			}
			double scale = (2.4 + 0.5 * r.nextDouble()) * (1.0 + 0.1 * k) * FxTune.d("ispike.crystalScale", 1.0);
			double lean = 0.1 + r.nextDouble() * 0.2;
			FxMeshPass.Crystal c = FxMeshPass.crystal(meshes[r.nextInt(meshes.length)], p.x, gy + 0.02, p.z, side.x * lean * (j == 0 ? -1 : 1) + look.x * 0.15, 1, side.z * lean * (j == 0 ? -1 : 1) + look.z * 0.15,
					r.nextDouble() * 6.28, scale, 0.0);
			c.grow = 0.12;
			c.emissive = 0.9;
			made[j] = c;
			RukiaKit.iceShards(x, r, 5, p.x, gy + 0.1, p.z, 0.5, 2.0, 3.0, 0.7, 20, 0.34);
			RukiaKit.frostMotes(x, r, 8, p.x, gy + 0.2, p.z, 0.6, 0.6, 1.8, 1.2, 18, 0.34, "#FFFFFF", "#BFE4FF", 0.9);
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(p.x, gy + 0.06, p.z).ground().size(0.4, 5.0).sizeEase(FxMath.OC).life(0.35).curve(0.02, 0.7).color("#EAF8FF").peak(RukiaKit.glow(0.6)).owner(x).spawn();
			x.soundAt("block.amethyst_cluster.place", new Vec3d(p.x, gy + 0.5, p.z), 0.8 + 0.5 * k / Math.max(1, steps - 1), 0.5);
			if (k % 2 == 0) {
				x.soundAt("block.glass.break", new Vec3d(p.x, gy + 0.5, p.z), 1.4 + 0.2 * r.nextDouble(), 0.25);
			}
		}
		// the spikes stand for a moment, then shatter
		long seed = x.seed * 31 + k;
		final Vec3d p0 = eye.add(look.multiply((d0 + d1) * 0.5));
		FxDelayed.after(1.1, () -> {
			for (FxMeshPass.Crystal c : made) {
				if (c != null) {
					FxMeshPass.shatter(c, 5, 4.5, seed);
				}
			}
			FxSound.play(null, "block.glass.break", p0.x, p0.y, p0.z, 1.2, 0.3);
		});
	}
}
