package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Alpha ground decals D:x (1.3.3, 1.7.3): a polar grid (default 12 rings x 32 segments, tier dependent) whose vertex heights
 * come from {@link FxGround} and whose vertex alpha is a reveal mask, so the frost creeps outward over the real terrain and is
 * erased from the rim inward. A normal (non additive) translucent entity layer: it reads on a bright sky where additive
 * glows vanish (design rule 5). Drawn at {@code AFTER_ENTITIES} before the mesh pass.
 */
public final class FxDecals {
	public static final Identifier FROST = ReiatsuTest.id("textures/fx/frost_ground.png");
	public static final Identifier CRACK = ReiatsuTest.id("textures/fx/crack_ground.png");

	public static final class Decal {
		final Identifier tex;
		final double cx;
		final double cz;
		final double radius;
		final int rings;
		final int segs;
		final double[][] ys;
		final double[] light = new double[0];
		double birth;
		public double revealTime = 0.5;
		public double eraseAt = Double.MAX_VALUE;
		public double eraseLen = 3.0;
		public double fadeAt = Double.MAX_VALUE;
		public double fadeLen = 0.5;
		public double alphaMax = 0.85;
		public float[] tint = FxMath.hex("#EEF6FF");
		boolean dead;

		Decal(Identifier tex, double cx, double cz, double hintY, double radius, int rings, int segs, double birth) {
			this.tex = tex;
			this.cx = cx;
			this.cz = cz;
			this.radius = radius;
			this.rings = rings;
			this.segs = segs;
			this.birth = birth;
			this.ys = new double[rings + 1][segs];
			for (int i = 0; i <= rings; i++) {
				double r = radius * i / rings;
				for (int j = 0; j < segs; j++) {
					double a = Math.PI * 2 * j / segs;
					ys[i][j] = i == 0 && j > 0 ? ys[0][0] : FxGround.sample(cx + Math.cos(a) * r, cz + Math.sin(a) * r, hintY) + 0.025;
				}
			}
		}

		/** Starts the fade out now. */
		public Decal fadeOutNow(double seconds) {
			fadeAt = FxClock.now - birth;
			fadeLen = Math.max(0.01, seconds);
			return this;
		}

		public Decal reveal(double seconds) {
			revealTime = Math.max(0.01, seconds);
			return this;
		}

		public Decal erase(double atAge, double len) {
			eraseAt = atAge;
			eraseLen = len;
			return this;
		}

		public Decal fade(double atAge, double len) {
			fadeAt = atAge;
			fadeLen = len;
			return this;
		}

		public Decal alpha(double a) {
			alphaMax = a;
			return this;
		}

		public Decal tint(String hex) {
			tint = FxMath.hex(hex);
			return this;
		}

		public boolean isDead() {
			return dead;
		}

		/** Visible radius at an age (reveal growth, then erase from the rim). */
		double visible(double age) {
			double v = radius * FxMath.oc(age / revealTime);
			if (age > eraseAt) {
				v = Math.min(v, radius * (1 - FxMath.clamp((age - eraseAt) / eraseLen)));
			}
			return v;
		}

		double fadeFactor(double age) {
			return age > fadeAt ? 1.0 - FxMath.clamp((age - fadeAt) / fadeLen) : 1.0;
		}
	}

	private static final List<Decal> DECALS = new ArrayList<>();
	public static int lastVertices;

	private FxDecals() {
	}

	/** Creates a decal: radius in blocks, appears {@code delay} seconds from now; the grid density follows the quality tier. */
	public static Decal create(Identifier tex, double cx, double cz, double hintY, double radius, double delay) {
		if (!FxConfig.decals) {
			Decal dead = new Decal(tex, cx, cz, hintY, Math.max(0.1, radius), 1, 3, FxClock.now);
			dead.dead = true;
			return dead;
		}
		int rings = radius < 1.5 ? 2 : FxConfig.tier().decalRings;
		int segs = radius < 1.5 ? 12 : FxConfig.tier().decalSegments;
		Decal d = new Decal(tex, cx, cz, hintY, Math.max(0.1, radius), rings, segs, FxClock.now + delay);
		while (DECALS.size() >= 24) {
			DECALS.remove(0);
		}
		DECALS.add(d);
		return d;
	}

	public static Decal frost(double cx, double cz, double hintY, double radius, double delay) {
		return create(FROST, cx, cz, hintY, radius, delay);
	}

	public static void clear() {
		DECALS.clear();
	}

	public static int count() {
		return DECALS.size();
	}

	/** WorldRenderEvents.AFTER_ENTITIES. */
	public static void draw(WorldRenderContext ctx) {
		lastVertices = 0;
		DECALS.removeIf(d -> d.dead || (d.fadeAt != Double.MAX_VALUE && FxClock.now - d.birth > d.fadeAt + d.fadeLen)
				|| (d.eraseAt != Double.MAX_VALUE && FxClock.now - d.birth > d.eraseAt + d.eraseLen));
		if (DECALS.isEmpty()) {
			return;
		}
		VertexConsumerProvider vcp = ctx.consumers();
		ClientWorld world = MinecraftClient.getInstance().world;
		if (vcp == null || world == null) {
			return;
		}
		Vec3d cam = ctx.camera().getPos();
		double now = FxClock.now;
		// one layer per texture: all decals of a texture are written together
		for (Identifier tex : new Identifier[] {FROST, CRACK}) {
			VertexConsumer vc = null;
			for (Decal d : DECALS) {
				if (!d.tex.equals(tex) || now < d.birth) {
					continue;
				}
				if (cam.squaredDistanceTo(d.cx, cam.y, d.cz) > (48 + d.radius) * (48 + d.radius)) {
					continue;
				}
				if (vc == null) {
					vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(tex));
				}
				lastVertices += write(vc, d, now - d.birth, cam, world);
			}
		}
	}

	private static int write(VertexConsumer vc, Decal d, double age, Vec3d cam, ClientWorld world) {
		double vis = d.visible(age);
		double ff = d.fadeFactor(age);
		if (vis <= 0.02 || ff <= 0.004) {
			return 0;
		}
		double edge = Math.max(0.35, d.radius * 0.10);
		int l = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(d.cx, d.ys[0][0], d.cz));
		int light = LightmapTextureManager.pack(Math.max(LightmapTextureManager.getBlockLightCoordinates(l), 12), LightmapTextureManager.getSkyLightCoordinates(l));
		int verts = 0;
		double[] a = new double[4];
		double[] px = new double[4];
		double[] pz = new double[4];
		double[] py = new double[4];
		for (int i = 0; i < d.rings; i++) {
			double r0 = d.radius * i / d.rings;
			double r1 = d.radius * (i + 1) / d.rings;
			if (r0 > vis + 0.01) {
				break;
			}
			for (int j = 0; j < d.segs; j++) {
				int j1 = (j + 1) % d.segs;
				double a0 = Math.PI * 2 * j / d.segs;
				double a1 = Math.PI * 2 * (j + 1) / d.segs;
				px[0] = d.cx + Math.cos(a0) * r0;
				pz[0] = d.cz + Math.sin(a0) * r0;
				py[0] = d.ys[i][j];
				px[1] = d.cx + Math.cos(a0) * r1;
				pz[1] = d.cz + Math.sin(a0) * r1;
				py[1] = d.ys[i + 1][j];
				px[2] = d.cx + Math.cos(a1) * r1;
				pz[2] = d.cz + Math.sin(a1) * r1;
				py[2] = d.ys[i + 1][j1];
				px[3] = d.cx + Math.cos(a1) * r0;
				pz[3] = d.cz + Math.sin(a1) * r0;
				py[3] = d.ys[i][j1];
				double[] rr = {r0, r1, r1, r0};
				boolean any = false;
				for (int c = 0; c < 4; c++) {
					a[c] = d.alphaMax * ff * FxMath.smoothstep(vis, vis - edge, rr[c]);
					any |= a[c] > 0.004;
				}
				if (!any) {
					continue;
				}
				for (int c = 0; c < 4; c++) {
					float u = (float) (0.5 + (px[c] - d.cx) / (2 * d.radius));
					float v = (float) (0.5 + (pz[c] - d.cz) / (2 * d.radius));
					vc.vertex((float) (px[c] - cam.x), (float) (py[c] - cam.y), (float) (pz[c] - cam.z)).color(FxMeshes.argb(d.tint, a[c])).texture(u, v)
							.overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0f, 1f, 0f);
					verts++;
				}
			}
		}
		return verts;
	}
}
