package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.RukiaFxParams;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Rukia bankai ability 1, the ice wall (R11 T5, effect id 24; it was the ice spike thrust of round 6). The wall itself is REAL blocks that the
 * server places and grows (the {@code ice_wall} block, translucent); this effect only dresses the cast: the cold gathers at the blade tip during
 * the cast time, at the release a flare and a ring of frost run out of the blade and a burst of frost motes and shards stands where the wall
 * rises. The sounds are vanilla stand ins (entries of the sound sheet, design/results/r11_T5.md). Event params
 * {@code [aimX, aimY, aimZ, reach, usedLength, firstSeconds]} ({@link RukiaFxParams}); nothing is hard coded, a changed balance only changes the numbers
 * the server sends. UNVERIFIED in the game.
 */
final class IceWallFx {
	private IceWallFx() {
	}

	static void play(EffectEventS2C e) {
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		dir = dir.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : dir.normalize();
		final Vec3d look = dir;
		EffectTimeline t = FxTimelines.create(24, e.seed(), pos, look, e.params(), e.casterId(), FxClient.forceRemote());
		double reach = Math.max(2.0, RukiaFxParams.param(t.params, 3, 10.0));
		double used = Math.max(0.0, Math.min(reach, RukiaFxParams.param(t.params, 4, reach)));
		double first = Math.max(0.2, RukiaFxParams.param(t.params, 5, 0.7));
		Vec3d eye = pos.add(0, 1.62, 0);
		double lunge = Math.max(0.0, first - FxTune.d("iwall.lead", 0.12));
		t.lifetime(first + 2.0);

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
		// the release: a flare at the tip and a ring of frost along the look
		t.at(lunge, x -> {
			Vec3d tip = RukiaKit.tip(x, 1.1, 0.2);
			FxGlowBatch.sprite(GlowSprite.FLARE).at(tip.x, tip.y, tip.z).size(1.4, 2.8).sizeEase(FxMath.OC).life(0.25).curve(0.02, 0.8).color("#EAF8FF").peak(RukiaKit.glow(0.9)).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(tip.x, tip.y, tip.z).plane(look.x, look.y, look.z).size(0.3, 2.0).sizeEase(FxMath.OC).life(0.25).curve(0.02, 0.8).color("#FFFFFF")
					.peak(RukiaKit.glow(0.8)).owner(x).spawn();
			x.shake(0.3, 0.2);
			x.sound("entity.player.attack.sweep", 1.7, 0.7);
		});
		// where the wall rises: frost motes, shards and a ground ring (the blocks themselves come from the server)
		t.at(first, x -> {
			ClientWorld w = MinecraftClient.getInstance().world;
			if (w == null) {
				return;
			}
			Random r = x.rng(10);
			double d = Math.max(1.0, used - 1.0);
			Vec3d p = eye.add(look.multiply(d));
			double gy = FxPlace.ground(w, p.x, p.z, p.y, 3.0, 26.0);
			if (Double.isNaN(gy)) {
				gy = p.y;
			}
			RukiaKit.iceShards(x, r, 8, p.x, gy + 0.1, p.z, 2.5, 2.0, 3.0, 0.7, 20, 0.34);
			RukiaKit.frostMotes(x, r, 14, p.x, gy + 0.6, p.z, 3.0, 1.2, 1.8, 1.2, 18, 0.34, "#FFFFFF", "#BFE4FF", 0.9);
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(p.x, gy + 0.06, p.z).ground().size(0.6, 9.0).sizeEase(FxMath.OC).life(0.5).curve(0.02, 0.7).color("#EAF8FF").peak(RukiaKit.glow(0.6)).owner(x).spawn();
			x.soundAt("block.amethyst_cluster.place", new Vec3d(p.x, gy + 0.5, p.z), 0.8, 0.5);
		});
		FxTimelines.start(t);
		RukiaBankaiClient.flare(e.casterId());
	}
}
