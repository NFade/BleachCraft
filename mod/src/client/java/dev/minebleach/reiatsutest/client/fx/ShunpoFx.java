package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Client visuals of the shunpo (B4 step 5, effect id 40). The server has already moved the player; this class only draws.
 *
 * <p>Afterimages: 4 to 6 translucent copies of the caster's own player model (skin, slim or wide arms) stand along the path
 * and dissolve in {@link #LIFE} seconds. Implementation: a private {@link PlayerEntityModel} (baked from the model layer)
 * is posed with a fixed running pose and rendered with {@code RenderLayer.getEntityTranslucent} at full bright light and a
 * per character tint from a {@code WorldRenderEvents.AFTER_ENTITIES} hook, appended to the entity vertex consumers so that
 * the translucency sorts with the entities. No mixin, no extra entity, no dependence on the live pose of the real player.
 * Trail: wind puffs and petals (Byakuya) or snow and frost (Rukia) along the path, vanilla particles only.
 */
public final class ShunpoFx {
	/** Seconds an afterimage lives (spec: 0.4 to 0.6). */
	public static final double LIFE = 0.5;
	/** Seconds between the appearance of two neighbouring afterimages (the first one at the start of the path). */
	public static final double STAGGER = 0.02;
	/** Starting opacity of an afterimage. */
	public static final float ALPHA0 = 0.6f;

	private static final class Image {
		double x;
		double y;
		double z;
		float yaw;
		float lean;
		float headPitch;
		float stride;
		double born;
		Identifier texture;
		boolean slim;
		int rgb;
	}

	private static final List<Image> LIVE = new ArrayList<>();
	private static PlayerEntityModel<AbstractClientPlayerEntity> wide;
	private static PlayerEntityModel<AbstractClientPlayerEntity> slim;
	private static boolean registered;

	/** Harness statistics. */
	public static int played;
	public static int lastImageCount;
	public static double lastDistance;
	public static int lastTrailParticles;
	public static int drawnLastFrame;
	public static double maxDrawnAlpha;
	public static long renderNanos;
	public static long renderNanosMax;
	public static long renderFrames;

	private ShunpoFx() {
	}

	public static int liveImages() {
		return LIVE.size();
	}

	public static void clear() {
		LIVE.clear();
	}

	public static void play(MinecraftClient client, EffectEventS2C e) {
		ClientWorld world = client.world;
		if (world == null) {
			return;
		}
		if (!registered) {
			registered = true;
			WorldRenderEvents.AFTER_ENTITIES.register(ShunpoFx::render);
		}
		float[] pr = e.params();
		double dist = pr.length > 0 ? pr[0] : Math.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz());
		CharacterId who = pr.length > 1 ? CharacterId.fromCode((byte) Math.round(pr[1])) : CharacterId.NONE;
		boolean byakuya = who == CharacterId.BYAKUYA;
		Vec3d start = new Vec3d(e.x(), e.y(), e.z());
		Vec3d delta = new Vec3d(e.dx(), e.dy(), e.dz());
		Entity caster = world.getEntityById(e.casterId());
		ReiatsuTest.LOGGER.info("[fx] shunpo caster={} from=({}, {}, {}) distance={} character={}", e.casterId(), fmt(start.x), fmt(start.y),
				fmt(start.z), fmt(dist), who);
		played++;
		lastDistance = dist;

		// ---- afterimages
		Identifier texture = null;
		boolean isSlim = false;
		float casterYaw = 0f;
		if (caster instanceof AbstractClientPlayerEntity pl) {
			SkinTextures skin = pl.getSkinTextures();
			texture = skin.texture();
			isSlim = skin.model() == SkinTextures.Model.SLIM;
			casterYaw = pl.getYaw();
		}
		double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		float yaw = horiz > 0.01 ? (float) Math.toDegrees(Math.atan2(-delta.x, delta.z)) : casterYaw;
		float pitch = dist > 0.01 ? (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, delta.y / dist)))) : 0f;
		int n = FxConfig.reduceMotion ? 4 : 5;
		lastImageCount = 0;
		if (texture != null && dist > 0.5) {
			Random rnd = new Random(e.seed());
			double now = FxClock.now;
			int rgb = byakuya ? 0xFFD6E4 : 0xC8E6FF;
			for (int i = 0; i < n; i++) {
				Image im = new Image();
				double f = (double) i / n; // the first copy stands where the player was, the last one just before the arrival
				im.x = start.x + delta.x * f;
				im.y = start.y + delta.y * f;
				im.z = start.z + delta.z * f;
				im.yaw = yaw;
				im.lean = 18f + 6f * rnd.nextFloat();
				im.headPitch = pitch;
				im.stride = (i % 2 == 0 ? 1f : -1f) * (0.55f + 0.25f * rnd.nextFloat());
				im.born = now + i * STAGGER;
				im.texture = texture;
				im.slim = isSlim;
				im.rgb = rgb;
				LIVE.add(im);
				lastImageCount++;
			}
		}

		// ---- trail along the path (vanilla particles)
		Random rnd = new Random(e.seed() ^ 0x5DEECE66DL);
		int steps = (int) Math.max(4, Math.round(dist * 2.0));
		int before = 0;
		ParticleEffect main = byakuya ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.SNOWFLAKE;
		for (int i = 0; i <= steps; i++) {
			double f = (double) i / steps;
			double px = start.x + delta.x * f;
			double py = start.y + delta.y * f + 0.9;
			double pz = start.z + delta.z * f;
			for (int k = 0; k < 2; k++) {
				double ox = (rnd.nextDouble() - 0.5) * 0.7;
				double oy = (rnd.nextDouble() - 0.5) * 1.5;
				double oz = (rnd.nextDouble() - 0.5) * 0.7;
				world.addParticle(main, px + ox, py + oy, pz + oz, -delta.x * 0.01, 0.01, -delta.z * 0.01);
				before++;
			}
			if (i % 2 == 0) {
				world.addParticle(ParticleTypes.CLOUD, px, py - 0.2 + rnd.nextDouble() * 0.6, pz, -delta.x * 0.02, 0.0, -delta.z * 0.02);
				before++;
			}
			if (!byakuya && i % 3 == 0) {
				world.addParticle(ParticleTypes.END_ROD, px, py + (rnd.nextDouble() - 0.5), pz, 0, 0.01, 0);
				before++;
			}
		}
		lastTrailParticles = before;

		// ---- sound: a sweep at the start, a softer one at the arrival, plus a character accent
		FxSound.play(null, "entity.player.attack.sweep", start.x, start.y + 1.0, start.z, 1.6, 0.7);
		FxSound.play(null, byakuya ? "block.cherry_leaves.break" : "block.amethyst_block.chime", start.x, start.y + 1.0, start.z,
				byakuya ? 0.9 : 1.8, 0.6);
		FxSound.play(null, "entity.player.attack.sweep", start.x + delta.x, start.y + delta.y + 1.0, start.z + delta.z, 1.9, 0.4);
	}

	private static PlayerEntityModel<AbstractClientPlayerEntity> model(boolean thin) {
		if (thin) {
			if (slim == null) {
				slim = new PlayerEntityModel<>(MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER_SLIM), true);
			}
			return slim;
		}
		if (wide == null) {
			wide = new PlayerEntityModel<>(MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER), false);
		}
		return wide;
	}

	/** A fixed running pose: the figure is rotated as a whole for the lean, so the parts only need the stride. */
	private static void pose(PlayerEntityModel<AbstractClientPlayerEntity> m, Image im) {
		for (ModelPart p : new ModelPart[] {m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg, m.leftSleeve, m.rightSleeve,
				m.leftPants, m.rightPants, m.jacket}) {
			p.resetTransform();
		}
		m.setVisible(true);
		m.head.pitch = (float) Math.toRadians(im.headPitch) - 0.25f;
		m.hat.copyTransform(m.head);
		m.rightLeg.pitch = 0.9f * im.stride;
		m.leftLeg.pitch = -0.9f * im.stride;
		m.rightArm.pitch = -0.7f * im.stride;
		m.leftArm.pitch = 0.7f * im.stride;
		m.rightArm.roll = 0.08f;
		m.leftArm.roll = -0.08f;
		m.rightPants.copyTransform(m.rightLeg);
		m.leftPants.copyTransform(m.leftLeg);
		m.rightSleeve.copyTransform(m.rightArm);
		m.leftSleeve.copyTransform(m.leftArm);
		m.jacket.copyTransform(m.body);
	}

	private static void render(WorldRenderContext ctx) {
		if (LIVE.isEmpty()) {
			drawnLastFrame = 0;
			return;
		}
		VertexConsumerProvider vcp = ctx.consumers();
		if (vcp == null) {
			return;
		}
		long t0 = System.nanoTime();
		double now = FxClock.now;
		Vec3d cam = ctx.camera().getPos();
		MatrixStack ms = ctx.matrixStack();
		int drawn = 0;
		for (Iterator<Image> it = LIVE.iterator(); it.hasNext();) {
			Image im = it.next();
			double t = (now - im.born) / LIFE;
			if (t >= 1.0) {
				it.remove();
				continue;
			}
			if (t < 0) {
				continue;
			}
			float alpha = ALPHA0 * (float) Math.pow(1.0 - t, 1.4);
			if (alpha < 0.01f) {
				continue;
			}
			PlayerEntityModel<AbstractClientPlayerEntity> m = model(im.slim);
			pose(m, im);
			ms.push();
			ms.translate(im.x - cam.x, im.y - cam.y, im.z - cam.z);
			ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - im.yaw));
			// lean forward about the hips, like a sprinting body
			ms.translate(0.0F, 0.9F, 0.0F);
			ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-im.lean));
			ms.translate(0.0F, -0.9F, 0.0F);
			ms.scale(-1.0F, -1.0F, 1.0F);
			ms.scale(0.9375F, 0.9375F, 0.9375F);
			ms.translate(0.0F, -1.501F, 0.0F);
			VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(im.texture));
			int argb = (Math.round(alpha * 255f) << 24) | im.rgb;
			m.render(ms, vc, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, argb);
			ms.pop();
			drawn++;
			maxDrawnAlpha = Math.max(maxDrawnAlpha, alpha);
		}
		drawnLastFrame = drawn;
		if (drawn > 0) {
			renderFrames++;
		}
		long dt = System.nanoTime() - t0;
		renderNanos += dt;
		renderNanosMax = Math.max(renderNanosMax, dt);
	}

	private static String fmt(double v) {
		return String.format(java.util.Locale.ROOT, "%.2f", v);
	}
}
