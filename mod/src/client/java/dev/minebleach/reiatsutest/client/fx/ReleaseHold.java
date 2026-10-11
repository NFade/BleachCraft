package dev.minebleach.reiatsutest.client.fx;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Byakuya shikai release, part 1 (plan A6): the state flips to SHIKAI at the server's release tick and the item model would at once show
 * the bare hilt, but in the anime the blade turns pink first and only then bursts into petals. For {@code release.pinkTime} seconds (0.4)
 * the item model keeps drawing the drawn blade and tints it from silver to pink ({@link #tint}); the release effects (flash, ring, the
 * petal swarm) are started by {@link FxDelayed} when the hold ends, so the petals leave from a blade that was seen turning pink.
 * Everything is clocked by the FX clock (a frozen clock freezes the hold, the harness takes frames at exact milliseconds).
 */
public final class ReleaseHold {
	private static final Map<Integer, double[]> HOLDS = new HashMap<>();
	/** Harness statistics. */
	public static int started;
	public static int tintedFrames;

	private ReleaseHold() {
	}

	public static void init() {
		FxGlowBatch.addSource(ReleaseHold::emitGlow);
	}

	public static void start(int entityId, double seconds) {
		HOLDS.put(entityId, new double[] {FxClock.now, seconds});
		started++;
	}

	public static void clear() {
		HOLDS.clear();
	}

	/** True while the entity's blade is held in its pink phase. */
	public static boolean active(int entityId) {
		return progress(entityId) >= 0;
	}

	/** 0..1 through the hold, -1 when there is none (any more). */
	public static double progress(int entityId) {
		double[] h = HOLDS.get(entityId);
		if (h == null) {
			return -1;
		}
		double p = (FxClock.now - h[0]) / h[1];
		if (p >= 1.0 || p < 0) {
			if (p >= 1.0) {
				HOLDS.remove(entityId);
			}
			return -1;
		}
		return p;
	}

	/** ARGB colour multiplier of the blade at this progress: silver to a saturated pink, with a quick flicker in the last quarter, or 0 when there is no hold. */
	public static int tint(int entityId) {
		double p = progress(entityId);
		if (p < 0) {
			return 0;
		}
		tintedFrames++;
		double k = FxMath.smoothstep(0.0, 0.8, p);
		double flick = p > 0.7 ? 0.08 * Math.sin(p * 90.0) : 0;
		k = FxMath.clamp(k + flick);
		float[] pink = FxMath.hex(FxTune.s("release.pinkColor", "#FF6EC0"));
		int r = (int) (255 * FxMath.lerp(1.0, pink[0], k));
		int g = (int) (255 * FxMath.lerp(1.0, pink[1], k));
		int b = (int) (255 * FxMath.lerp(1.0, pink[2], k));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	// ------------------------------------------------------------------------------------------ glow along the blade

	private static void emitGlow(FxGlowBatch.Emitter e) {
		if (HOLDS.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		float td = mc.getRenderTickCounter().getTickDelta(false);
		for (Map.Entry<Integer, double[]> en : new HashMap<>(HOLDS).entrySet()) {
			double p = progress(en.getKey());
			if (p < 0) {
				continue;
			}
			Entity ent = mc.world.getEntityById(en.getKey());
			if (ent == null) {
				continue;
			}
			boolean fp = ent == mc.player && !FxClient.forceRemote() && mc.options.getPerspective().isFirstPerson();
			if (fp && FxTune.d("release.pinkFpGlow", 0.0) < 0.5) {
				continue; // the tinted blade itself is the effect in first person (a guessed glow position floated beside it in the first review)
			}
			Vec3d base;
			Vec3d axis;
			double len;
			if (fp) {
				var cam = mc.gameRenderer.getCamera();
				Vec3d look = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
				Vec3d right = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
				Vec3d up = right.crossProduct(look).normalize();
				base = cam.getPos().add(look.multiply(FxTune.d("release.pinkFpForward", 1.0))).add(right.multiply(FxTune.d("release.pinkFpRight", 0.30)))
						.add(up.multiply(FxTune.d("release.pinkFpUp", -0.30)));
				axis = up.multiply(0.95).add(look.multiply(0.1)).normalize();
				len = FxTune.d("release.pinkFpLength", 0.7);
			} else {
				Vec3d feet = ent.getLerpedPos(td);
				double yaw = Math.toRadians(ent.getYaw(td));
				double fx = -Math.sin(yaw);
				double fz = Math.cos(yaw);
				double rx = -fz;
				double rz = fx;
				base = new Vec3d(feet.x + rx * 0.35 + fx * 0.45, feet.y + 1.15, feet.z + rz * 0.35 + fz * 0.45);
				axis = new Vec3d(fx * 0.45, 0.9, fz * 0.45).normalize();
				len = 0.9;
			}
			double k = FxMath.smoothstep(0.0, 0.8, p);
			double flick = 0.85 + 0.15 * Math.sin(p * 80.0);
			double glow = FxTune.d("release.pinkGlow", 0.6) * k * flick * FxConfig.glowIntensity;
			float[] pink = FxMath.hex(FxTune.s("release.pinkColor", "#FF6EC0"));
			for (int i = 0; i < 5; i++) {
				double s = (i + 0.5) / 5.0 * len;
				Vec3d pt = base.add(axis.multiply(s));
				e.sprite(GlowSprite.GLOW_SOFT, pt.x, pt.y, pt.z, (fp ? 0.5 : 0.7) * (0.6 + 0.8 * k), 0, pink[0], pink[1], pink[2], glow * (i == 4 ? 1.3 : 0.8));
			}
			if (p > 0.75) {
				Vec3d tip = base.add(axis.multiply(len));
				e.sprite(GlowSprite.STAR4, tip.x, tip.y, tip.z, 0.7 * (p - 0.6), p * 12.0, 1f, 0.85f, 0.95f, glow * 0.9);
			}
		}
	}
}
