package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.SwarmMath;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

/**
 * Batched petal swarm (VFX_STORYBOARD 1.7.3 {@code PetalSwarmRenderer}): every petal of every swarm in view is computed in closed
 * form ({@link SwarmMath}) and written into one buffer per frame. The nearest {@code nearPetalCount} within 16 blocks use the
 * 12-quad petal mesh, the rest one tumbling oriented card (not camera facing, so it flickers like a real petal). Glint: vertex
 * colour goes toward white with {@code pow(|n . toCamera|, 16)}; the glow source adds a star sparkle on the strong glints, a
 * soft halo on a few petals and short streaks behind fast ones (Senbonzakura glitter). Distance thinning per 1.6.
 */
public final class PetalSwarmRenderer {
	private static final Identifier TEXTURE = ReiatsuTest.id("textures/item/byakuya_shikai_diffuse.png");
	private static RenderLayer layer;
	private static FxMesh mesh;
	private static boolean meshTried;
	private static final float[] M = new float[9];
	private static final double[] OUT = new double[3];
	private static final double[] OUT2 = new double[3];
	private static float[] sortTmp = new float[0];
	private static float[] d2 = new float[0];

	/** Stats for the harness. */
	public static double lastMs;
	public static double sumMs;
	public static long frames;
	public static int lastVertices;
	public static int lastPetals;
	public static int lastNear;
	public static int lastGlints;
	public static int lastStreaks;

	/** Glow data collected while drawing (camera relative), consumed by the glow source later in the same frame. */
	private static final class GlowItem {
		float x;
		float y;
		float z;
		float vx;
		float vy;
		float vz;
		float strength;
		int kind;
	}

	private static GlowItem[] items = new GlowItem[0];
	private static int itemCount;

	private PetalSwarmRenderer() {
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(PetalSwarmRenderer::draw);
		FxGlowBatch.addSource(PetalSwarmRenderer::emitGlow);
	}

	public static void resetStats() {
		sumMs = 0;
		frames = 0;
	}

	private static RenderLayer layer() {
		if (layer == null) {
			layer = RenderLayer.getEntityCutoutNoCull(TEXTURE);
		}
		return layer;
	}

	private static void addItem(int kind, float x, float y, float z, float vx, float vy, float vz, float strength) {
		if (itemCount >= items.length) {
			items = Arrays.copyOf(items, Math.max(256, items.length * 2));
			for (int i = itemCount; i < items.length; i++) {
				items[i] = new GlowItem();
			}
		}
		GlowItem g = items[itemCount++];
		g.kind = kind;
		g.x = x;
		g.y = y;
		g.z = z;
		g.vx = vx;
		g.vy = vy;
		g.vz = vz;
		g.strength = strength;
	}

	/** Rotation matrix (row major) of an angle about a unit axis. */
	private static void rot(float ax, float ay, float az, double ang, float[] m) {
		double c = Math.cos(ang);
		double s = Math.sin(ang);
		double k = 1 - c;
		m[0] = (float) (c + ax * ax * k);
		m[1] = (float) (ax * ay * k - az * s);
		m[2] = (float) (ax * az * k + ay * s);
		m[3] = (float) (ay * ax * k + az * s);
		m[4] = (float) (c + ay * ay * k);
		m[5] = (float) (ay * az * k - ax * s);
		m[6] = (float) (az * ax * k - ay * s);
		m[7] = (float) (az * ay * k + ax * s);
		m[8] = (float) (c + az * az * k);
	}

	private static void draw(WorldRenderContext ctx) {
		itemCount = 0;
		var states = SwarmFx.states();
		if (states.isEmpty()) {
			lastVertices = 0;
			lastPetals = 0;
			return;
		}
		long t0 = System.nanoTime();
		if (!meshTried) {
			meshTried = true;
			mesh = FxMesh.load("byakuya_shikai", "byakuya_shikai_petal");
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld world = ctx.world();
		Vec3d cp = ctx.camera().getPos();
		float td = ctx.tickCounter().getTickDelta(true);
		double fyaw = Math.toRadians(ctx.camera().getYaw());
		double fpitch = Math.toRadians(ctx.camera().getPitch());
		double fwx = -Math.sin(fyaw) * Math.cos(fpitch);
		double fwy = -Math.sin(fpitch);
		double fwz = Math.cos(fyaw) * Math.cos(fpitch);
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
		int quads = 0;
		int petalsDrawn = 0;
		int near = 0;
		int glints = 0;
		double now = FxClock.now;
		double petalScale = FxTune.d("swarm.petalScale", 1.8);
		double glintPower = FxTune.d("swarm.glintPower", 16);
		float uc = mesh != null ? mesh.centreU : 0.313f;
		float vc = mesh != null ? mesh.centreV : 0.055f;
		boolean allStreaks = FxConfig.effectQuality > 0.4;
		int streakStride = FxConfig.effectQuality >= 1.0 ? 10 : FxConfig.effectQuality >= 0.5 ? 20 : 0;
		for (SwarmFx.State st : states.values()) {
			Entity owner = world.getEntityById(st.ownerId);
			Vec3d feet;
			if (owner != null) {
				feet = owner.getLerpedPos(td);
				st.yaw = Math.toRadians(owner.getYaw(td));
				st.lastFeet = feet;
			} else {
				feet = st.lastFeet;
			}
			double dOwner = cp.distanceTo(feet);
			if (dOwner > 80) {
				continue;
			}
			int stride = dOwner <= 16 ? 1 : dOwner <= 32 ? 2 : 4;
			double t = st.time();
			st.t = t;
			// light of the swarm (block light at least 8)
			int packed = LightmapTextureManager.pack(Math.max(8, world.getLightLevel(LightType.BLOCK, BlockPos.ofFloored(feet.x, feet.y + 1.2, feet.z))),
					world.getLightLevel(LightType.SKY, BlockPos.ofFloored(feet.x, feet.y + 1.2, feet.z)));
			double fall = 0;
			double shrink = 1;
			if (st.endAt >= 0) {
				double tau = now - st.endAt;
				fall = 0.5 * 9.0 * tau * tau;
				shrink = Math.max(0, 1 - tau / 0.5);
			}
			SwarmMath.Mode mode = st.mode;
			SwarmMath.Mode prev = st.prev;
			double blend = prev != null ? SwarmMath.blendWeight(t - mode.t0) : 1.0;
			int n = st.n;
			if (d2.length < n) {
				d2 = new float[n];
				sortTmp = new float[n];
			}
			// pass 1: positions (camera relative) and distances
			for (int i = 0; i < n; i++) {
				if (i % stride != 0 && stride > 1) {
					d2[i] = Float.MAX_VALUE;
					continue;
				}
				SwarmMath.position(st.slots, i, t, mode, st.yaw, OUT);
				if (prev != null && blend < 1.0) {
					SwarmMath.position(st.slots, i, t, prev, st.yaw, OUT2);
					OUT[0] = OUT2[0] + (OUT[0] - OUT2[0]) * blend;
					OUT[1] = OUT2[1] + (OUT[1] - OUT2[1]) * blend;
					OUT[2] = OUT2[2] + (OUT[2] - OUT2[2]) * blend;
				}
				float x = (float) (feet.x + OUT[0] - cp.x);
				float y = (float) (feet.y + OUT[1] - fall - cp.y);
				float z = (float) (feet.z + OUT[2] - cp.z);
				st.px[i] = x;
				st.py[i] = y;
				st.pz[i] = z;
				d2[i] = x * x + y * y + z * z;
			}
			// near set: the nearPetalCount closest within 16 blocks
			int nearCap = Math.min(n, FxConfig.nearPetalCount);
			System.arraycopy(d2, 0, sortTmp, 0, n);
			Arrays.sort(sortTmp, 0, n);
			float thr = Math.min(256f, sortTmp[Math.max(0, nearCap - 1)]);
			for (int i = 0; i < n; i++) {
				if (d2[i] == Float.MAX_VALUE) {
					continue;
				}
				double dist2 = d2[i];
				if (dist2 > 64.0 * 64.0) {
					continue;
				}
				// behind the camera: skip (petals are small; no frustum test per petal needed beyond this)
				float x = st.px[i];
				float y = st.py[i];
				float z = st.pz[i];
				if (x * fwx + y * fwy + z * fwz < -0.5) {
					continue;
				}
				double sc = SwarmMath.scale(st.slots, i, t, mode) * shrink;
				if (sc <= 0.001) {
					continue;
				}
				double size = petalScale * sc * (mode.kind == SwarmMath.Kind.BARRIER ? 1.25 : 1.0) * (0.8 + 0.4 * st.slots.tumblePhase[i] / 6.2832);
				double ang = st.slots.tumblePhase[i] + st.slots.tumbleRate[i] * now;
				rot(st.slots.tx[i], st.slots.ty[i], st.slots.tz[i], ang, M);
				// local axes: U (long) = column 1, V (width) = column 0, normal = column 2
				float nx = M[2];
				float ny = M[5];
				float nz = M[8];
				double dl = Math.sqrt(dist2);
				double tcx = -x / Math.max(dl, 1e-3);
				double tcy = -y / Math.max(dl, 1e-3);
				double tcz = -z / Math.max(dl, 1e-3);
				double facing = Math.abs(nx * tcx + ny * tcy + nz * tcz);
				double glint = Math.pow(facing, glintPower);
				st.glint[i] = (float) glint;
				float base = 0.88f + 0.12f * (float) facing;
				int cr = (int) (255 * Math.min(1.0, base * 1.12 + glint * 0.3));
				int cg = (int) (255 * Math.min(1.0, base * 1.04 + glint * 0.5));
				int cb = (int) (255 * Math.min(1.0, base * 1.1 + glint * 0.35));
				int color = 0xFF000000 | (cr << 16) | (cg << 8) | cb;
				boolean useMesh = mesh != null && dist2 <= thr && dist2 <= 256.0 && near < nearCap;
				if (useMesh) {
					near++;
					float[] mm = new float[9];
					float s = (float) size;
					// column scale: x thickness, y length, z width (the exported petal is slim: widen it to match the cards)
					mm[0] = M[0] * s;
					mm[1] = M[1] * s;
					mm[2] = M[2] * s * 1.6f;
					mm[3] = M[3] * s;
					mm[4] = M[4] * s;
					mm[5] = M[5] * s * 1.6f;
					mm[6] = M[6] * s;
					mm[7] = M[7] * s;
					mm[8] = M[8] * s * 1.6f;
					// centre the petal (origin is its base): shift half the length back along U
					double off = -0.06 * size;
					mesh.write(buf, mm, x + M[1] * off, y + M[4] * off, z + M[7] * off, color, packed);
					quads += mesh.quads;
				} else {
					double L = 0.12 * size;
					float ux = (float) (M[1] * L);
					float uy = (float) (M[4] * L);
					float uz = (float) (M[7] * L);
					float vx = (float) (M[0] * L * 0.40);
					float vy = (float) (M[3] * L * 0.40);
					float vz = (float) (M[6] * L * 0.40);
					// kite: base, right shoulder (35 percent), tip, left shoulder
					float bx = x - ux * 0.5f;
					float by = y - uy * 0.5f;
					float bz = z - uz * 0.5f;
					float tx = x + ux * 0.5f;
					float ty = y + uy * 0.5f;
					float tz = z + uz * 0.5f;
					float mx = x - ux * 0.15f;
					float my = y - uy * 0.15f;
					float mz = z - uz * 0.15f;
					int darker = shade(color, 0.78f);
					int lighter = shade(color, 1.0f);
					int side = shade(color, 0.92f);
					float cn = (float) FxTune.d("swarm.cardUp", 0.85);
					card(buf, bx, by, bz, darker, mx + vx, my + vy, mz + vz, side, tx, ty, tz, lighter, mx - vx, my - vy, mz - vz, side, nx * (1 - cn), ny * (1 - cn) + cn, nz * (1 - cn), uc, vc, packed);
					quads++;
				}
				petalsDrawn++;
				// glow items: sparkle on the strong glints, streaks behind fast petals
				if (glint > 0.55 && glints < 220 && dl < 40) {
					glints++;
					addItem(0, x, y, z, 0, 0, 0, (float) glint);
				}
				if (streakStride > 0 && i % streakStride == 0 && dl < 24) {
					SwarmMath.position(st.slots, i, Math.max(0, t - 0.02), mode, st.yaw, OUT2);
					SwarmMath.position(st.slots, i, t, mode, st.yaw, OUT);
					float vx = (float) (OUT[0] - OUT2[0]) / 0.02f;
					float vy = (float) (OUT[1] - OUT2[1]) / 0.02f;
					float vz = (float) (OUT[2] - OUT2[2]) / 0.02f;
					float sp = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
					if (sp > 12.0f) {
						addItem(1, x, y, z, vx, vy, vz, sp);
					}
				}
				// halo: 1 petal in 8 gets a soft glow
				if ((i & 7) == 0 && dl < 30) {
					addItem(2, x, y, z, 0, 0, 0, 1f);
				}
			}
			st.visible = petalsDrawn;
		}
		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			layer().draw(built);
		}
		lastVertices = quads * 4;
		lastPetals = petalsDrawn;
		lastNear = near;
		lastGlints = glints;
		lastMs = (System.nanoTime() - t0) / 1.0e6;
		sumMs += lastMs;
		frames++;
	}

	private static int shade(int argb, float k) {
		int r = (int) (((argb >> 16) & 255) * k);
		int g = (int) (((argb >> 8) & 255) * k);
		int b = (int) ((argb & 255) * k);
		return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
	}

	private static void card(BufferBuilder b, float ax, float ay, float az, int ca, float bx, float by, float bz, int cb, float cx, float cy, float cz, int cc, float dx, float dy, float dz,
			int cd, float nx, float ny, float nz, float u, float v, int light) {
		int o = OverlayTexture.DEFAULT_UV;
		b.vertex(ax, ay, az, ca, u, v, o, light, nx, ny, nz);
		b.vertex(bx, by, bz, cb, u, v, o, light, nx, ny, nz);
		b.vertex(cx, cy, cz, cc, u, v, o, light, nx, ny, nz);
		b.vertex(dx, dy, dz, cd, u, v, o, light, nx, ny, nz);
	}

	// ------------------------------------------------------------------------------------------ glow source (after the grade)

	private static void emitGlow(FxGlowBatch.Emitter e) {
		MinecraftClient mc = MinecraftClient.getInstance();
		double glowMul = FxTune.d("swarm.glowMul", 1.0);
		Vec3d cp = mc.gameRenderer.getCamera().getPos();
		for (int k = 0; k < itemCount; k++) {
			GlowItem g = items[k];
			double wx = g.x + cp.x;
			double wy = g.y + cp.y;
			double wz = g.z + cp.z;
			switch (g.kind) {
				case 0 -> e.sprite(GlowSprite.STAR4, wx, wy, wz, 0.38 + 0.4 * g.strength, k * 0.7, 1f, 0.93f, 1f, FxTune.d("swarm.glintIntensity", 0.85) * g.strength * glowMul);
				case 1 -> {
					double sp = Math.sqrt(g.vx * g.vx + g.vy * g.vy + g.vz * g.vz);
					if (sp > 1e-3) {
						double len = Math.min(1.6, sp * 0.05);
						e.streak(GlowSprite.STREAK, wx, wy, wz, wx - g.vx / sp * len, wy - g.vy / sp * len, wz - g.vz / sp * len, 0.14, 0.98f, 0.82f, 0.98f, 0.40 * glowMul);
					}
				}
				default -> e.sprite(GlowSprite.PETAL_GLOW, wx, wy, wz, 0.5, k * 1.3, 0.98f, 0.78f, 0.96f, FxTune.d("swarm.haloIntensity", 0.30) * glowMul);
			}
		}
		// barrier: lavender rim on the ground, shimmer discs on the dome surface
		double now = FxClock.now;
		for (SwarmFx.State st : SwarmFx.states().values()) {
			if (st.mode.kind != SwarmMath.Kind.BARRIER) {
				continue;
			}
			double tm = st.time() - st.mode.t0;
			double alive = Double.isInfinite(st.mode.collapseAt) ? 1.0 : Math.max(0, 1 - (st.time() - st.mode.collapseAt) / 0.6);
			double rim = FxMath.smoothstep(0.4, 0.9, tm) * alive;
			if (rim > 0.01) {
				e.ground(GlowSprite.RING_SOFT, st.lastFeet.x, st.lastFeet.y + 0.06, st.lastFeet.z, 3.0 / 0.375 * 1.0, 3.0 / 0.375, now * 0.15, 0.81f, 0.76f, 0.94f, 0.55 * rim * glowMul);
				e.ground(GlowSprite.RING_THIN, st.lastFeet.x, st.lastFeet.y + 0.07, st.lastFeet.z, 3.0 / 0.4375, 3.0 / 0.4375, 0, 1f, 0.95f, 1f, 0.25 * rim * glowMul);
				double[] o = new double[3];
				for (int j = 0; j < 12; j++) {
					int i = (j * 83) % st.n;
					SwarmMath.dome(st.slots, i, tm, o);
					e.sprite(GlowSprite.GLOW_SOFT, st.lastFeet.x + o[0], st.lastFeet.y + o[1], st.lastFeet.z + o[2], 2.5, 0, 0.95f, 0.89f, 1f, 0.12 * rim * glowMul);
				}
			}
		}
	}
}
