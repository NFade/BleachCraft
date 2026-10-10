package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.CondenseMath;
import dev.minebleach.reiatsutest.core.fx.StormMath;
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
 * Batched petals of the Byakuya bankai effects that are not the shikai swarm: the petal storm of the scatter ability
 * ({@link StormMath}, up to 3000 petals, lit, same mesh and cards as {@link PetalSwarmRenderer}) and the condensing petals of Hakuteiken
 * ({@link CondenseMath}, up to 2000, full-bright cards that turn white toward the hand). Also the glow source of the storm (ground ring at
 * the foot, the growing core at the aim) and the streaks, glints and halos of the petals.
 */
public final class StormRenderer {
	private static final Identifier SHIKAI = ReiatsuTest.id("textures/item/byakuya_shikai_diffuse.png");
	private static final Identifier EMISSIVE = ReiatsuTest.id("textures/item/byakuya_bankai_emissive.png");
	/** Centre of a fully opaque white 6 x 6 texel block of the emissive atlas (pixel 277, 239 of 512). */
	private static final float WHITE_U = 277.0f / 512.0f;
	private static final float WHITE_V = 239.0f / 512.0f;
	private static RenderLayer litLayer;
	private static RenderLayer whiteLayer;
	private static FxMesh mesh;
	private static boolean meshTried;
	private static final float[] M = new float[9];
	private static final float[] MM = new float[9];
	private static final double[] OUT = new double[3];
	private static final double[] OUT2 = new double[3];
	private static float[] sortTmp = new float[0];
	private static float[] d2 = new float[0];
	private static float[] px = new float[0];
	private static float[] py = new float[0];
	private static float[] pz = new float[0];
	private static float[] psc = new float[0];

	/** Stats for the harness. */
	public static double lastMs;
	public static double sumMs;
	public static long frames;
	public static int lastVertices;
	public static int lastPetals;
	public static int lastCondensed;
	public static int lastStreaks;

	private static final class GlowItem {
		float x;
		float y;
		float z;
		float vx;
		float vy;
		float vz;
		float strength;
		float r;
		float g;
		float b;
		int kind;
	}

	private static GlowItem[] items = new GlowItem[0];
	private static int itemCount;

	private StormRenderer() {
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(StormRenderer::draw);
		FxGlowBatch.addSource(StormRenderer::emitGlow);
	}

	public static void resetStats() {
		sumMs = 0;
		frames = 0;
	}

	private static RenderLayer lit() {
		if (litLayer == null) {
			litLayer = RenderLayer.getEntityCutoutNoCull(SHIKAI);
		}
		return litLayer;
	}

	private static RenderLayer white() {
		if (whiteLayer == null) {
			whiteLayer = RenderLayer.getEntityTranslucentEmissive(EMISSIVE);
		}
		return whiteLayer;
	}

	private static void addItem(int kind, float x, float y, float z, float vx, float vy, float vz, float strength, float r, float g, float b) {
		if (itemCount >= items.length) {
			items = Arrays.copyOf(items, Math.max(256, items.length * 2));
			for (int i = itemCount; i < items.length; i++) {
				items[i] = new GlowItem();
			}
		}
		GlowItem it = items[itemCount++];
		it.kind = kind;
		it.x = x;
		it.y = y;
		it.z = z;
		it.vx = vx;
		it.vy = vy;
		it.vz = vz;
		it.strength = strength;
		it.r = r;
		it.g = g;
		it.b = b;
	}

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

	private static void ensure(int n) {
		if (d2.length < n) {
			d2 = new float[n];
			sortTmp = new float[n];
			px = new float[n];
			py = new float[n];
			pz = new float[n];
			psc = new float[n];
		}
	}

	private static int light(ClientWorld w, double x, double y, double z) {
		BlockPos p = BlockPos.ofFloored(x, y, z);
		return LightmapTextureManager.pack(Math.max(8, w.getLightLevel(LightType.BLOCK, p)), w.getLightLevel(LightType.SKY, p));
	}

	// ------------------------------------------------------------------------------------------ draw

	private static void draw(WorldRenderContext ctx) {
		itemCount = 0;
		boolean storms = !StormFx.states().isEmpty();
		boolean cond = !HakuteikenFx.states().isEmpty();
		if (!storms && !cond) {
			lastVertices = 0;
			lastPetals = 0;
			lastCondensed = 0;
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
		int quads = 0;
		int petals = 0;
		int condensed = 0;
		if (storms) {
			BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
			for (StormFx.State st : StormFx.states().values()) {
				int[] r = drawStorm(buf, st, world, cp, td, fwx, fwy, fwz);
				quads += r[0];
				petals += r[1];
			}
			BuiltBuffer built = buf.endNullable();
			if (built != null) {
				lit().draw(built);
			}
		}
		if (cond) {
			BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
			for (HakuteikenFx.State st : HakuteikenFx.states().values()) {
				condensed += drawCondense(buf, st, world, cp, td, fwx, fwy, fwz);
			}
			quads += condensed;
			BuiltBuffer built = buf.endNullable();
			if (built != null) {
				white().draw(built);
			}
		}
		lastVertices = quads * 4;
		lastPetals = petals;
		lastCondensed = condensed;
		lastMs = (System.nanoTime() - t0) / 1.0e6;
		sumMs += lastMs;
		frames++;
	}

	private static int[] drawStorm(BufferBuilder buf, StormFx.State st, ClientWorld world, Vec3d cp, float td, double fwx, double fwy, double fwz) {
		double t = st.time();
		StormMath.Storm s = st.storm;
		int n = s.m;
		if (t < 0 || t > st.life) {
			return new int[] {0, 0};
		}
		ensure(n);
		Entity owner = world.getEntityById(st.ownerId);
		Vec3d feet = owner != null ? owner.getLerpedPos(td) : st.feet0;
		double[] ownerA = {feet.x, feet.y, feet.z};
		int lightOwner = light(world, feet.x, feet.y + 1.2, feet.z);
		int lightAim = light(world, st.aim[0], st.aim[1], st.aim[2]);
		double petalScale = FxTune.d("storm.petalScale", 1.8);
		double glintPower = FxTune.d("swarm.glintPower", 16);
		double now = FxClock.now;
		float uc = mesh != null ? mesh.centreU : 0.313f;
		float vc = mesh != null ? mesh.centreV : 0.055f;
		int nearCap = Math.min(n, FxConfig.nearPetalCount);
		int streakStride = FxConfig.effectQuality >= 1.0 ? 10 : FxConfig.effectQuality >= 0.5 ? 20 : 0;
		double lead = s.p.launchStart + s.p.launchSpan * Math.min(1.0, 200.0 / Math.max(1, st.streamCount));
		double aimX = st.aim[0];
		double aimZ = st.aim[2];
		// pass 1: positions (camera relative) and distances
		for (int j = 0; j < n; j++) {
			int b = s.blade(j);
			double sc = s.position(j, t, ownerA, st.bx[b], st.by[b], st.bz[b], st.aim, st.look, OUT);
			if (sc <= 0.002) {
				d2[j] = Float.MAX_VALUE;
				continue;
			}
			float x = (float) (OUT[0] - cp.x);
			float y = (float) (OUT[1] - cp.y);
			float z = (float) (OUT[2] - cp.z);
			px[j] = x;
			py[j] = y;
			pz[j] = z;
			psc[j] = (float) sc;
			d2[j] = x * x + y * y + z * z;
		}
		System.arraycopy(d2, 0, sortTmp, 0, n);
		Arrays.sort(sortTmp, 0, n);
		float thr = Math.min(256f, sortTmp[Math.max(0, nearCap - 1)]);
		int quads = 0;
		int drawn = 0;
		int near = 0;
		int glints = 0;
		for (int j = 0; j < n; j++) {
			if (d2[j] == Float.MAX_VALUE) {
				continue;
			}
			double dist2 = d2[j];
			if (dist2 > 90.0 * 90.0) {
				continue;
			}
			int bj = s.blade(j);
			float x = px[j];
			float y = py[j];
			float z = pz[j];
			if (x * fwx + y * fwy + z * fwz < -0.5) {
				continue;
			}
			double sc = psc[j];
			double size = petalScale * sc * (0.8 + 0.4 * st.phase[j] / 6.2832);
			double ang = st.phase[j] + st.rate[j] * now;
			rot(st.tx[j], st.ty[j], st.tz[j], ang, M);
			float nx = M[2];
			float ny = M[5];
			float nz = M[8];
			double dl = Math.sqrt(dist2);
			double tcx = -x / Math.max(dl, 1e-3);
			double tcy = -y / Math.max(dl, 1e-3);
			double tcz = -z / Math.max(dl, 1e-3);
			double facing = Math.abs(nx * tcx + ny * tcy + nz * tcz);
			double glint = Math.pow(facing, glintPower);
			float base = 0.88f + 0.12f * (float) facing;
			int cr = (int) (255 * Math.min(1.0, base * 1.12 + glint * 0.3));
			int cg = (int) (255 * Math.min(1.0, base * 1.04 + glint * 0.5));
			int cb = (int) (255 * Math.min(1.0, base * 1.1 + glint * 0.35));
			int color = 0xFF000000 | (cr << 16) | (cg << 8) | cb;
			double wx = x + cp.x - aimX;
			double wz = z + cp.z - aimZ;
			int packed = wx * wx + wz * wz < 14 * 14 ? lightAim : lightOwner;
			boolean useMesh = mesh != null && dist2 <= thr && dist2 <= 256.0 && near < nearCap;
			if (useMesh) {
				near++;
				float sf = (float) size;
				MM[0] = M[0] * sf;
				MM[1] = M[1] * sf;
				MM[2] = M[2] * sf * 1.6f;
				MM[3] = M[3] * sf;
				MM[4] = M[4] * sf;
				MM[5] = M[5] * sf * 1.6f;
				MM[6] = M[6] * sf;
				MM[7] = M[7] * sf;
				MM[8] = M[8] * sf * 1.6f;
				double off = -0.06 * size;
				mesh.write(buf, MM, x + M[1] * off, y + M[4] * off, z + M[7] * off, color, packed);
				quads += mesh.quads;
			} else {
				quads += card(buf, x, y, z, M, 0.12 * size, color, packed, uc, vc, true);
			}
			drawn++;
			if (glint > 0.55 && glints < 160 && dl < 40) {
				glints++;
				addItem(0, x, y, z, 0, 0, 0, (float) glint, 1f, 0.93f, 1f);
			}
			// streaks: the leading 200 petals of the stream are bright (#FFE9FB 0.5), the fast ones of the tornado 1 in 10
			boolean leading = s.isStream(j) && s.launchTime(j) <= lead && t >= s.launchTime(j) && t <= s.arriveTime(j) + 0.05;
			if ((leading || (streakStride > 0 && j % streakStride == 0)) && dl < (leading ? 60 : 24)) {
				s.position(j, Math.max(0, t - 0.02), ownerA, st.bx[bj], st.by[bj], st.bz[bj], st.aim, st.look, OUT2);
				float vx = (float) (OUT[0] - OUT2[0]) / 0.02f;
				float vy = (float) (OUT[1] - OUT2[1]) / 0.02f;
				float vz = (float) (OUT[2] - OUT2[2]) / 0.02f;
				float sp = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
				if (sp > 12.0f) {
					addItem(1, x, y, z, vx, vy, vz, leading ? 1.25f : 0.8f, 1f, 0.91f, 0.98f);
				}
			}
			if ((j & 7) == 0 && dl < 36) {
				addItem(2, x, y, z, 0, 0, 0, 1f, 0.98f, 0.78f, 0.96f);
			}
		}
		st.visible = drawn;
		return new int[] {quads, drawn};
	}

	/** One tumbling oriented kite card; {@code useCardUp} leans the normal up like the swarm cards. Returns the quad count (1). */
	private static int card(BufferBuilder b, float x, float y, float z, float[] m, double L, int color, int light, float u, float v, boolean lit) {
		float ux = (float) (m[1] * L);
		float uy = (float) (m[4] * L);
		float uz = (float) (m[7] * L);
		float vx = (float) (m[0] * L * 0.40);
		float vy = (float) (m[3] * L * 0.40);
		float vz = (float) (m[6] * L * 0.40);
		float bx = x - ux * 0.5f;
		float by = y - uy * 0.5f;
		float bz = z - uz * 0.5f;
		float tx = x + ux * 0.5f;
		float ty = y + uy * 0.5f;
		float tz = z + uz * 0.5f;
		float mx = x - ux * 0.15f;
		float my = y - uy * 0.15f;
		float mz = z - uz * 0.15f;
		int darker = lit ? shade(color, 0.78f) : color;
		int side = lit ? shade(color, 0.92f) : color;
		float cn = (float) FxTune.d("swarm.cardUp", 0.85);
		float nx = m[2] * (1 - cn);
		float ny = m[5] * (1 - cn) + cn;
		float nz = m[8] * (1 - cn);
		int o = OverlayTexture.DEFAULT_UV;
		b.vertex(bx, by, bz, darker, u, v, o, light, nx, ny, nz);
		b.vertex(mx + vx, my + vy, mz + vz, side, u, v, o, light, nx, ny, nz);
		b.vertex(tx, ty, tz, color, u, v, o, light, nx, ny, nz);
		b.vertex(mx - vx, my - vy, mz - vz, side, u, v, o, light, nx, ny, nz);
		return 1;
	}

	private static int shade(int argb, float k) {
		int r = (int) (((argb >> 16) & 255) * k);
		int g = (int) (((argb >> 8) & 255) * k);
		int b = (int) ((argb & 255) * k);
		return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
	}

	private static int drawCondense(BufferBuilder buf, HakuteikenFx.State st, ClientWorld world, Vec3d cp, float td, double fwx, double fwy, double fwz) {
		double t = st.time();
		CondenseMath.Cloud c = st.cloud;
		if (t < 0 || t > c.endTime() + 0.2) {
			return 0;
		}
		HakuteikenFx.Pose pose = new HakuteikenFx.Pose();
		HakuteikenFx.pose(st, world, td, pose);
		double[] hand = {pose.hx, pose.hy, pose.hz};
		double petalScale = FxTune.d("hak.petalScale", 1.8);
		double now = FxClock.now;
		float[] pink = {0.98f, 0.78f, 0.96f};
		int drawn = 0;
		int streakEvery = Math.max(1, (int) (c.m / Math.max(1.0, 100.0 * FxConfig.effectQuality)));
		for (int j = 0; j < c.m; j++) {
			int b = c.blade(j);
			double sc = c.position(j, t, st.bx[b], st.by[b], st.bz[b], hand, OUT);
			if (sc <= 0.003) {
				continue;
			}
			float x = (float) (OUT[0] - cp.x);
			float y = (float) (OUT[1] - cp.y);
			float z = (float) (OUT[2] - cp.z);
			if (x * fwx + y * fwy + z * fwz < -0.5) {
				continue;
			}
			double dist2 = x * x + y * y + z * z;
			if (dist2 > 110.0 * 110.0) {
				continue;
			}
			double dh = Math.sqrt((OUT[0] - hand[0]) * (OUT[0] - hand[0]) + (OUT[1] - hand[1]) * (OUT[1] - hand[1]) + (OUT[2] - hand[2]) * (OUT[2] - hand[2]));
			double w = c.whiteness(dh);
			int cr = (int) (255 * (pink[0] + (1 - pink[0]) * w));
			int cg = (int) (255 * (pink[1] + (1 - pink[1]) * w));
			int cbb = (int) (255 * (pink[2] + (1 - pink[2]) * w));
			int color = 0xFF000000 | (cr << 16) | (cg << 8) | cbb;
			double size = petalScale * sc * (0.8 + 0.4 * st.phase[j] / 6.2832);
			rot(st.tx[j], st.ty[j], st.tz[j], st.phase[j] + st.rate[j] * now, M);
			drawn += card(buf, x, y, z, M, 0.12 * size, color, 0xF000F0, WHITE_U, WHITE_V, false);
			if (j % streakEvery == 0 && dist2 < 60 * 60) {
				c.position(j, Math.max(0, t - 0.03), st.bx[b], st.by[b], st.bz[b], hand, OUT2);
				float vx = (float) (OUT[0] - OUT2[0]) / 0.03f;
				float vy = (float) (OUT[1] - OUT2[1]) / 0.03f;
				float vz = (float) (OUT[2] - OUT2[2]) / 0.03f;
				float sp = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
				if (sp > 6.0f) {
					addItem(1, x, y, z, vx, vy, vz, 1.0f, 1f, 0.9f + 0.1f * (float) w, 1f);
				}
			}
			if (w > 0.2 && (j & 3) == 0 && dist2 < 50 * 50) {
				addItem(2, x, y, z, 0, 0, 0, (float) w, 1f, 1f, 1f);
			}
		}
		st.visible = drawn;
		return drawn;
	}

	// ------------------------------------------------------------------------------------------ glow source (after the grade)

	private static void emitGlow(FxGlowBatch.Emitter e) {
		MinecraftClient mc = MinecraftClient.getInstance();
		double glowMul = FxTune.d("swarm.glowMul", 1.0);
		Vec3d cp = mc.gameRenderer.getCamera().getPos();
		int streaks = 0;
		for (int k = 0; k < itemCount; k++) {
			GlowItem g = items[k];
			double wx = g.x + cp.x;
			double wy = g.y + cp.y;
			double wz = g.z + cp.z;
			switch (g.kind) {
				case 0 -> e.sprite(GlowSprite.STAR4, wx, wy, wz, 0.38 + 0.4 * g.strength, k * 0.7, g.r, g.g, g.b, FxTune.d("storm.glintIntensity", 0.8) * g.strength * glowMul);
				case 1 -> {
					double sp = Math.sqrt(g.vx * g.vx + g.vy * g.vy + g.vz * g.vz);
					if (sp > 1e-3) {
						double len = Math.min(2.2, sp * 0.055);
						if (e.streak(GlowSprite.STREAK, wx, wy, wz, wx - g.vx / sp * len, wy - g.vy / sp * len, wz - g.vz / sp * len, 0.16 * g.strength, g.r, g.g, g.b,
								FxTune.d("storm.streakIntensity", 0.5) * g.strength * glowMul)) {
							streaks++;
						}
					}
				}
				default -> e.sprite(GlowSprite.PETAL_GLOW, wx, wy, wz, 0.5, k * 1.3, g.r, g.g, g.b, FxTune.d("storm.haloIntensity", 0.28) * g.strength * glowMul);
			}
		}
		lastStreaks = streaks;
		double now = FxClock.now;
		ClientWorld w = mc.world;
		for (StormFx.State st : StormFx.states().values()) {
			double t = st.time();
			StormMath.Params p = st.storm.p;
			Entity ow = w != null ? w.getEntityById(st.ownerId) : null;
			Vec3d feet = ow != null ? ow.getLerpedPos(mc.getRenderTickCounter().getTickDelta(false)) : st.feet0;
			// ground ring at the foot (r 5.5, #E5A4DC 0.25, rotating)
			double ring = FxMath.smoothstep(0.8, 1.1, t) * (1.0 - FxMath.smoothstep(p.returnStart, p.returnStart + 0.4, t));
			if (ring > 0.01) {
				double gy = FxGround.sample(feet.x, feet.z, feet.y);
				double wdt = FxTune.d("storm.footRing", 5.5) / 0.375;
				e.ground(GlowSprite.RING_SOFT, feet.x, gy + 0.06, feet.z, wdt, wdt, now * 0.4, 0.90f, 0.64f, 0.86f, FxTune.d("storm.footRingIntensity", 0.25) * ring * glowMul);
			}
			// the core at the aim grows while the sphere contracts (G glow_soft size 6, #F9C8F6 0 to 0.6)
			double core = FxMath.smoothstep(p.shrinkStart, p.impactTime - 0.02, t) * (t < p.impactTime ? 1.0 : 0.0);
			if (core > 0.01) {
				double rad = st.storm.sphereRadius(t);
				e.sprite(GlowSprite.GLOW_SOFT, st.aim[0], st.aim[1], st.aim[2], FxTune.d("storm.envelopSize", 6.0) * (0.6 + 0.4 * rad / 5.0), 0, 0.98f, 0.78f, 0.96f,
						FxTune.d("storm.envelopGlow", 0.6) * core * glowMul);
				e.sprite(GlowSprite.STAR4, st.aim[0], st.aim[1], st.aim[2], 2.2 * core, now * 1.5, 1f, 0.95f, 1f, 0.5 * core * glowMul);
			}
		}
	}
}
