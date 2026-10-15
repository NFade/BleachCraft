package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.sound.SoundIds;
import dev.minebleach.reiatsutest.core.fx.ShirafuneTimeline;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Cast telegraph of the Rukia Shirafune (r8 fx2 item 3), local player only. The server now aims and hits at the END of the 0.6 s cast
 * and sends the effect event then, so without this the first 0.6 s after the key would show nothing. The client starts it when the server
 * ACCEPTS the cast (the {@code action_result OK} of a request for {@link AbilityId#SHIRAFUNE}, key or voice; a refused cast shows nothing):
 * a pale-blue glow gathers along the sword and brightens (the curve of {@link ShirafuneTimeline#gather}), frost motes converge on the tip,
 * a faint frost vignette closes in, and two soft cues sound. The release event ({@link RukiaShikaiFx}) ends it and takes over with the blade.
 *
 * <p>The sword position is a guess from the player pose (like the pink hold of {@link ReleaseHold}); in first person it is placed from the
 * camera. Tuning ({@code fx_override.json}): {@code shira.cast} (1 = on, 0 = off), {@code shira.castTime} (seconds, default the spec's cast
 * time 0.6), {@code shira.castGlow} (brightness, 0.6), {@code shira.castMotes} (motes per 0.05 s at the end, 4), {@code shira.castVignette}
 * (frost vignette level, 0.10, 0 = off), {@code shira.castSound} (volume scale, 1.0), {@code shira.castColor} (#BFE4FF), and for the first
 * person guess {@code shira.castFpForward} (1.0), {@code shira.castFpRight} (0.30), {@code shira.castFpUp} (-0.30), {@code shira.castLength} (0.8).
 * UNVERIFIED in the game.
 */
public final class ShirafuneCast {
	private static final double STEP = 0.05;
	private static EffectTimeline current;
	private static ScreenFx.Layer vignette;
	/** Harness statistics. */
	public static int casts;
	public static double lastSeconds;

	private ShirafuneCast() {
	}

	/** Starts the telegraph of a cast the server accepted. Safe to call at any time (a running one is replaced). */
	public static void start() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity me = mc.player;
		if (me == null || mc.world == null || FxTune.d("shira.cast", 1.0) < 0.5) {
			return;
		}
		stop();
		double secs = Math.max(0.1, FxTune.d("shira.castTime", ShirafuneTimeline.castSeconds(BalanceConfig.defaults().spec(AbilityId.SHIRAFUNE))));
		lastSeconds = secs;
		casts++;
		EffectTimeline t = FxTimelines.create(22, me.age, me.getPos(), Vec3d.ZERO, new float[0], me.getId(), false);
		current = t;
		t.lifetime(secs + 0.3);
		for (double s = 0.0; s < secs; s += STEP) {
			final double ts = s;
			t.at(s, x -> tick(x, ts / secs));
		}
		// a cast that never reaches its release (sealed, killed) lets go by itself
		t.at(secs + 0.2, x -> {
			if (current == x) {
				stop();
			}
		});
		double vg = FxTune.d("shira.castVignette", 0.10);
		if (vg > 0.001) {
			vignette = ScreenFx.vignette(ScreenFx.Kind.FROST, vg, secs * 0.8, 1.0);
		}
		double v = FxTune.d("shira.castSound", 1.0);
		FxSound.play(null, SoundIds.RUKIA_SHIKAI_SHIRAFUNE_CAST, "block.amethyst_block.resonate", me.getX(), me.getY() + 1.0, me.getZ(), 1.2, 0.5 * v);
		FxDelayed.after(secs * 0.55, () -> FxSound.play(null, SoundIds.RUKIA_SHIKAI_SHIRAFUNE_CAST, "block.amethyst_cluster.hit", me.getX(), me.getY() + 1.0, me.getZ(), 1.6, 0.35 * v));
		FxTimelines.start(t);
	}

	/** The release event arrived (or the cast ended): the glow stops and the vignette lets go. */
	public static void onRelease() {
		stop();
	}

	public static void stop() {
		if (current != null) {
			current.cancel();
			current = null;
		}
		if (vignette != null) {
			vignette.release(0.25);
			vignette = null;
		}
	}

	/** True while a telegraph runs (harness). */
	public static boolean active() {
		return current != null && !current.isEnded();
	}

	private static void tick(EffectTimeline x, double p) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity me = mc.player;
		if (me == null || mc.world == null) {
			return;
		}
		boolean fp = mc.options.getPerspective().isFirstPerson() && !FxClient.forceRemote();
		Vec3d base;
		Vec3d axis;
		double len = FxTune.d("shira.castLength", 0.8);
		if (fp) {
			var cam = mc.gameRenderer.getCamera();
			Vec3d look = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
			Vec3d right = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
			Vec3d up = right.crossProduct(look).normalize();
			base = cam.getPos().add(look.multiply(FxTune.d("shira.castFpForward", 1.0))).add(right.multiply(FxTune.d("shira.castFpRight", 0.30)))
					.add(up.multiply(FxTune.d("shira.castFpUp", -0.30)));
			axis = up.multiply(0.95).add(look.multiply(0.1)).normalize();
		} else {
			float td = mc.getRenderTickCounter().getTickDelta(false);
			Vec3d feet = me.getLerpedPos(td);
			double yaw = Math.toRadians(me.getYaw(td));
			double fx = -Math.sin(yaw);
			double fz = Math.cos(yaw);
			base = new Vec3d(feet.x + (-fz) * 0.35 + fx * 0.45, feet.y + 1.15, feet.z + fx * 0.35 + fz * 0.45);
			axis = new Vec3d(fx * 0.45, 0.9, fz * 0.45).normalize();
			len = Math.max(len, 0.9);
		}
		double g = ShirafuneTimeline.gather(p);
		double smooth = ShirafuneTimeline.gatherSmooth(p);
		double glow = FxTune.d("shira.castGlow", 0.6) * g;
		String colour = FxTune.s("shira.castColor", "#BFE4FF");
		// a string of soft glows along the sword, refreshed every tick (each lives a little over one step)
		for (int i = 0; i < 5; i++) {
			double s = (i + 0.5) / 5.0 * len;
			Vec3d pt = base.add(axis.multiply(s));
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(pt.x, pt.y, pt.z).size((fp ? 0.45 : 0.6) * (0.6 + 0.8 * smooth)).life(STEP * 1.6).curve(0.01, 0.6).color(colour)
					.peak(RukiaKit.glow(glow * (i == 4 ? 1.3 : 0.8))).owner(x).spawn();
		}
		Vec3d tip = base.add(axis.multiply(len));
		if (p > 0.5) {
			FxGlowBatch.sprite(GlowSprite.STAR4).at(tip.x, tip.y, tip.z).size((fp ? 0.5 : 0.7) * (p - 0.35)).life(STEP * 1.6).curve(0.01, 0.6).color("#FFFFFF")
					.peak(RukiaKit.glow(glow)).rot(p * 12.0, 0).owner(x).spawn();
		}
		// frost motes that close on the tip
		Random r = x.rng((int) Math.round(p * 1000));
		int n = RukiaKit.count(ShirafuneTimeline.moteCount(p, (int) Math.round(FxTune.d("shira.castMotes", 4))));
		for (int i = 0; i < n; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double b = (r.nextDouble() - 0.5) * 2;
			double rad = 0.7 + r.nextDouble() * 0.6;
			double px = tip.x + Math.cos(a) * rad;
			double py = tip.y + b * 0.4;
			double pz = tip.z + Math.sin(a) * rad;
			FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(px, py, pz).vel((tip.x - px) / 0.25, (tip.y - py) / 0.25, (tip.z - pz) / 0.25).drag(1.0).life(5).size(RukiaKit.size(0.3))
					.seed(i).color("#FFFFFF").colorTo("#9ED3F0").fade(0.4).owner(x).spawn();
		}
	}
}
