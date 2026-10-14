package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.sound.SoundIds;
import dev.minebleach.reiatsutest.core.fx.RukiaFxParams;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Visuals of the territory freeze of the Rukia bankai (round 6, effect ids 42 and 43). The server replaces the blocks (FrostField);
 * this class only adds what blocks cannot do: a ring that runs outward with the wave (snowflakes, frost motes, glints, ground mist, rare
 * ice crystals standing on the surface, the crackle of freezing glass and amethyst) and the same ring running inward when the field thaws.
 * Everything is closed form from the event {@code [radius, speed, cap]} / {@code [radius, thaw speed]} and the FX clock, so every client
 * sees the same ring at the same time. The ring position follows the real terrain (client side floor lookups); no state is kept besides
 * the timeline. UNVERIFIED in the game.
 */
final class FrostFieldFx {
	/** Seconds between two ring steps. */
	private static final double STEP = 0.05;
	private static final String[] CRACKS = {"block.glass.break", "block.amethyst_block.break", "block.powder_snow.break", "block.amethyst_cluster.break", "block.glass.hit"};
	/** Statistics for the harness. */
	static int fields;
	static int thaws;
	static int crystalsMade;

	private FrostFieldFx() {
	}

	static boolean handles(int effectId) {
		return effectId == EffectIds.FROST_FIELD || effectId == EffectIds.FROST_THAW;
	}

	/** Effect 42: the freeze wave runs outward. */
	static void field(EffectEventS2C e) {
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		EffectTimeline t = FxTimelines.create(EffectIds.FROST_FIELD, e.seed(), pos, Vec3d.ZERO, e.params(), e.casterId(), FxClient.forceRemote());
		double R = Math.max(4.0, RukiaFxParams.param(t.params, 0, 30.0));
		double speed = Math.max(1.0, RukiaFxParams.param(t.params, 1, 12.0));
		double dur = R / speed;
		double sk = Math.sqrt(RukiaFxParams.radiusScale(R, 30.0, 0.3));
		fields++;
		t.lifetime(dur + 4.0);
		t.at(0.0, x -> {
			double ry = FxPlace.ringY(pos.x, pos.z, pos.y, Math.min(R, 8.0));
			// the front: a soft white ring on the ground that runs at the wave speed, and a second thin one just behind it
			FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(pos.x, ry + 0.07, pos.z).ground().size(1.0, R / 0.375).sizeEase(FxMath.LINEAR).life(dur).curve(0.02, 0.35).color("#EAF8FF")
					.peak(RukiaKit.glow(0.55)).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(pos.x, ry + 0.08, pos.z).ground().size(1.0, R / 0.4375).sizeEase(FxMath.LINEAR).life(dur).curve(0.02, 0.35).delay(0.08).color("#FFFFFF")
					.peak(RukiaKit.glow(0.45)).owner(x).spawn();
			x.shake(0.25, 0.4);
			x.sound(SoundIds.RUKIA_BANKAI_FIELD_FRONT, "block.glass.break", 0.55, 0.7);
			x.sound(SoundIds.RUKIA_BANKAI_FIELD_FRONT, "block.amethyst_block.resonate", 0.5, 0.8);
			x.sound(SoundIds.RUKIA_BANKAI_FIELD_FRONT, "block.powder_snow.break", 0.6, 0.6);
		});
		for (int i = 1; i * STEP <= dur + 1e-9; i++) {
			final int ii = i;
			t.at(i * STEP, x -> outward(x, pos, R, speed * ii * STEP, ii, e.casterId(), sk));
		}
		t.at(dur + 0.15, x -> {
			// the rim: a last glitter all round and a deep chime
			Random r = x.rng(900);
			ClientWorld w = MinecraftClient.getInstance().world;
			if (w == null) {
				return;
			}
			int n = RukiaKit.count(36);
			for (int i = 0; i < n; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double px = pos.x + Math.cos(a) * R;
				double pz = pos.z + Math.sin(a) * R;
				double gy = surface(w, px, pz, pos.y);
				if (Double.isNaN(gy)) {
					continue;
				}
				FxGlowBatch.sprite(GlowSprite.STAR4).at(px, gy + 0.3 + r.nextDouble(), pz).lifeTicks(10).size(RukiaKit.size(0.6)).color("#FFFFFF").peak(RukiaKit.glow(0.8)).twinkle().rot(r.nextDouble() * 6.28, 1).owner(x).spawn();
			}
			x.sound(SoundIds.RUKIA_BANKAI_FIELD_CHIME, "block.amethyst_block.chime", 0.5, 0.5);
		});
		FxTimelines.start(t);
	}

	private static void outward(EffectTimeline x, Vec3d pos, double R, double r, int step, int casterId, double sk) {
		ClientWorld w = MinecraftClient.getInstance().world;
		if (w == null) {
			return;
		}
		Random rnd = x.rng(100 + step);
		int n = RukiaKit.count((int) FxMath.clamp(Math.round(r * 0.9 + 6), 6, 28));
		for (int i = 0; i < n; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double rr = Math.max(0.3, r + (rnd.nextDouble() - 0.5) * 1.6);
			double c = Math.cos(a);
			double s = Math.sin(a);
			double px = pos.x + c * rr;
			double pz = pos.z + s * rr;
			double gy = surface(w, px, pz, pos.y);
			if (Double.isNaN(gy)) {
				continue;
			}
			boolean snow = (i & 1) == 0;
			FxParticles.spec(snow ? FxParticles.Kind.SNOWFLAKE : FxParticles.Kind.FROST_MOTE).at(px, gy + 0.15 + rnd.nextDouble() * 0.9, pz)
					.vel(c * 1.4 * rnd.nextDouble(), 0.5 + rnd.nextDouble() * 1.1, s * 1.4 * rnd.nextDouble()).drag(0.97).life(22 + rnd.nextInt(16))
					.size(RukiaKit.size(snow ? 0.22 : 0.3) * (0.7 + 0.6 * rnd.nextDouble())).color("#FFFFFF").colorTo("#BFE4FF").seed(rnd.nextInt(4)).fade(0.4).owner(x).spawn();
		}
		if (step % 2 == 0) {
			for (int k = 0; k < RukiaKit.count(3); k++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				double px = pos.x + Math.cos(a) * r;
				double pz = pos.z + Math.sin(a) * r;
				double gy = surface(w, px, pz, pos.y);
				if (!Double.isNaN(gy)) {
					FxGlowBatch.sprite(GlowSprite.STAR4).at(px, gy + 0.2 + rnd.nextDouble() * 0.7, pz).lifeTicks(8).size(RukiaKit.size(0.5)).color("#FFFFFF").peak(RukiaKit.glow(0.8)).twinkle()
							.rot(rnd.nextDouble() * 6.28, 1).owner(x).spawn();
				}
			}
		}
		if (step % 3 == 0) {
			for (int k = 0; k < RukiaKit.count(2); k++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				double px = pos.x + Math.cos(a) * (r - 0.8 * rnd.nextDouble());
				double pz = pos.z + Math.sin(a) * (r - 0.8 * rnd.nextDouble());
				double gy = surface(w, px, pz, pos.y);
				if (!Double.isNaN(gy)) {
					FxGlowBatch.sprite(GlowSprite.MIST).at(px, gy + 0.15, pz).ground().rot(rnd.nextDouble() * 6.28, 0.04).size(RukiaKit.size(2.5 + rnd.nextDouble() * 1.5)).life(2.6).curve(0.4, 0.6)
							.color("#EAF8FF").peak(RukiaKit.glow(0.13)).owner(x).spawn();
				}
			}
		}
		if (step % 4 == 1) {
			// the crackle: at the point of the ring nearest to the listener, so it is heard wherever the camera is
			Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
			double a = Math.atan2(cam.z - pos.z, cam.x - pos.x) + (rnd.nextDouble() - 0.5) * 1.2;
			double px = pos.x + Math.cos(a) * r;
			double pz = pos.z + Math.sin(a) * r;
			double gy = surface(w, px, pz, pos.y);
			if (!Double.isNaN(gy)) {
				FxSound.play(x, SoundIds.RUKIA_BANKAI_FIELD_CRACKLE, CRACKS[rnd.nextInt(CRACKS.length)], px, gy, pz, 1.1 + rnd.nextDouble() * 0.9, 0.3);
			}
		}
		if (step % 7 == 3 && crystalsMade < 400 && r > 3.0) {
			rareCrystal(x, pos, rnd, r, casterId, sk);
		}
	}

	/** One ice crystal that stands on the surface where the ring is. They shatter with the Rukia bankai field (the same owner key). */
	private static void rareCrystal(EffectTimeline x, Vec3d pos, Random rnd, double r, int casterId, double sk) {
		ClientWorld w = MinecraftClient.getInstance().world;
		double a = rnd.nextDouble() * Math.PI * 2;
		double px = pos.x + Math.cos(a) * r;
		double pz = pos.z + Math.sin(a) * r;
		double gy = surface(w, px, pz, pos.y);
		if (Double.isNaN(gy)) {
			return;
		}
		String[] meshes = {FxMeshes.CRYSTAL_B, FxMeshes.CRYSTAL_C, FxMeshes.CRYSTAL_B, FxMeshes.CRYSTAL_D};
		double[] scales = {2.4, 2.0, 2.8, 1.6};
		int k = rnd.nextInt(meshes.length);
		FxMeshPass.Crystal c = RukiaBankaiClient.placeCrystal(meshes[k], new Vec3d(pos.x, gy, pos.z), a, r, 0.1 + rnd.nextDouble() * 0.25, rnd.nextDouble() * 6.28,
				scales[k] * sk * (0.8 + 0.4 * rnd.nextDouble()), 0.0, 0.04, 3);
		if (c == null) {
			return;
		}
		crystalsMade++;
		c.field = casterId;
		c.grow = 0.5;
		c.emissive = 0.7;
		RukiaKit.iceShards(x, rnd, 4, c.x, c.y + 0.1, c.z, 0.4, 1.2, 2.0, 0.6, 18, 0.3);
		x.soundAt(SoundIds.RUKIA_BANKAI_FIELD_CRYSTAL, "block.amethyst_cluster.place", new Vec3d(c.x, c.y + 0.5, c.z), 0.8 + 0.5 * rnd.nextDouble(), 0.4);
	}

	/** Effect 43: the field thaws, the ring runs inward (speed 0 = fast thaw after a death or a disconnect). */
	static void thaw(EffectEventS2C e) {
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		EffectTimeline t = FxTimelines.create(EffectIds.FROST_THAW, e.seed(), pos, Vec3d.ZERO, e.params(), e.casterId(), FxClient.forceRemote());
		double R = Math.max(4.0, RukiaFxParams.param(t.params, 0, 30.0));
		double speed = RukiaFxParams.param(t.params, 1, 20.0);
		double dur = speed <= 0.0 ? 0.5 : R / speed;
		thaws++;
		t.lifetime(dur + 2.0);
		t.at(0.0, x -> {
			x.sound(SoundIds.RUKIA_BANKAI_FIELD_THAW, "block.amethyst_block.resonate", 0.4, 0.6);
			x.sound(SoundIds.RUKIA_BANKAI_FIELD_THAW, "block.glass.break", 0.5, 0.5);
		});
		for (int i = 1; i * STEP <= dur + 1e-9; i++) {
			final int ii = i;
			t.at(i * STEP, x -> inward(x, pos, R, R * (1.0 - ii * STEP / dur), ii));
		}
		FxTimelines.start(t);
	}

	private static void inward(EffectTimeline x, Vec3d pos, double R, double r, int step) {
		ClientWorld w = MinecraftClient.getInstance().world;
		if (w == null || r < 0.3) {
			return;
		}
		Random rnd = x.rng(2000 + step);
		int n = RukiaKit.count((int) FxMath.clamp(Math.round(r * 0.6 + 4), 4, 18));
		for (int i = 0; i < n; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double rr = Math.max(0.3, r + (rnd.nextDouble() - 0.5) * 1.6);
			double px = pos.x + Math.cos(a) * rr;
			double pz = pos.z + Math.sin(a) * rr;
			double gy = surface(w, px, pz, pos.y);
			if (Double.isNaN(gy)) {
				continue;
			}
			// meltwater steam: slow rising pale motes
			FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(px, gy + 0.1 + rnd.nextDouble() * 0.5, pz).vel((rnd.nextDouble() - 0.5) * 0.4, 0.5 + rnd.nextDouble() * 0.6, (rnd.nextDouble() - 0.5) * 0.4)
					.drag(0.97).life(20 + rnd.nextInt(10)).size(RukiaKit.size(0.3)).color("#EAF8FF").colorTo("#9ED3F0").alpha(0.7).seed(rnd.nextInt(4)).fade(0.5).owner(x).spawn();
		}
		if (step % 4 == 2) {
			Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
			double a = Math.atan2(cam.z - pos.z, cam.x - pos.x) + (rnd.nextDouble() - 0.5) * 1.2;
			double px = pos.x + Math.cos(a) * r;
			double pz = pos.z + Math.sin(a) * r;
			double gy = surface(w, px, pz, pos.y);
			if (!Double.isNaN(gy)) {
				FxSound.play(x, SoundIds.RUKIA_BANKAI_FIELD_THAW_CRACKLE, rnd.nextBoolean() ? "block.powder_snow.break" : "block.glass.break", px, gy, pz, 0.7 + rnd.nextDouble() * 0.5, 0.25);
			}
		}
	}

	/** Top face of the floor under (x, z) near {@code hintY}, or NaN. */
	private static double surface(ClientWorld w, double x, double z, double hintY) {
		return FxPlace.ground(w, x, z, hintY, 8.0, 24.0);
	}
}
