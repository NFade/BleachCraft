package dev.minebleach.reiatsutest.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.BankaiCurves;
import dev.minebleach.reiatsutest.core.fx.BladeRowLayout;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Batched giant blades (VFX_STORYBOARD 1.7.3 {@code BankaiBladeRenderer}, {@code GroundHiltRenderer}). Rising, sinking and leaning
 * blades are written on the CPU every frame (full mesh within {@code bladeLodDistance}, LOD beyond, frustum and distance culled); once
 * every blade is in its final pose the whole set is uploaded once into a STATIC {@link VertexBuffer} and drawn with one call
 * (set {@code -Dreiatsu.fx.vbo=false} to compare). Tips glow without a pass: vertices at local y >= 6.85 are lit full bright and
 * tinted {@code #F2E9FF}; a glow-core billboard per tip (glow source, drawn after the grade) carries them at any distance. Also draws the
 * ground hilt, the ripples and the crack decals.
 */
public final class BankaiBladeRenderer {
	private static final Identifier TEXTURE = ReiatsuTest.id("textures/item/byakuya_bankai_diffuse.png");
	private static final Identifier CRACK = ReiatsuTest.id("textures/fx/crack_ground.png");
	private static final int WHITE = 0xFFFFFFFF;
	private static final int TIP = 0xFFF2E9FF;
	private static final float TIP_Y = 6.85f;
	private static final double TIP_Z = -0.826;
	private static final float[] M = new float[9];
	private static FxMesh blade;
	private static FxMesh lod;
	private static FxMesh hilt;
	private static FxMesh ripple;
	private static boolean meshesTried;
	private static RenderLayer bladeLayer;
	private static RenderLayer rippleLayer;
	private static RenderLayer crackLayer;
	/** Dev switch for the comparison of the static buffer against the CPU path. */
	public static volatile boolean useVbo = !"false".equals(System.getProperty("reiatsu.fx.vbo"));

	/** Stats for the harness. */
	public static double lastMs;
	public static double sumMs;
	public static long frames;
	public static int lastCpuVertices;
	public static int lastStaticVertices;
	public static int lastCpuBlades;
	public static int vboBuilds;
	public static double lastBuildMs;
	public static boolean lastStatic;

	private BankaiBladeRenderer() {
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(BankaiBladeRenderer::draw);
		FxGlowBatch.addSource(BankaiBladeRenderer::emitGlow);
	}

	public static void resetStats() {
		sumMs = 0;
		frames = 0;
	}

	private static void loadMeshes() {
		if (meshesTried) {
			return;
		}
		meshesTried = true;
		blade = FxMesh.load("byakuya_bankai", "byakuya_bankai_blade");
		lod = FxMesh.load("byakuya_bankai", "byakuya_bankai_blade_lod");
		hilt = FxMesh.load("byakuya_bankai", "byakuya_bankai_hilt_ground");
		ripple = FxMesh.load("byakuya_bankai", "byakuya_bankai_ripple");
		bladeLayer = RenderLayer.getEntityCutoutNoCull(TEXTURE);
		rippleLayer = RenderLayer.getEntityTranslucentEmissive(TEXTURE);
		crackLayer = RenderLayer.getEntityTranslucent(CRACK);
	}

	// ------------------------------------------------------------------------------------------ blade pose

	/** Vertical offset of blade i from its rest height at row time t (rise, sink at the seal). */
	static double offsetOf(RowsFx.State st, int i, double t) {
		BladeRowLayout l = st.layout;
		if (st.endAt >= 0) {
			double tau = FxClock.now - st.endAt - 0.006 * l.dist[i];
			double x = FxMath.clamp(tau / 0.5);
			double risen = BladeRowLayout.riseOffset(st.endAt - st.epoch - l.start[i]);
			// sink from where the blade stood at the seal (reverse of the rise, easeInQuad)
			return risen + (-BladeRowLayout.SINK_DEPTH - risen) * x * x;
		}
		return BladeRowLayout.riseOffset(t - l.start[i]);
	}

	/** Basis of blade i with the lean (radians toward the axis) and the scale, row major 3x3 into {@code m}. */
	static void basis(RowsFx.State st, int i, double leanRad, float[] m) {
		double yaw = Math.toRadians(st.yawDeg);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -fz;
		double rz = fx;
		int s = st.layout.side[i];
		double xX = s * fx;
		double xZ = s * fz;
		double zX = s * rx;
		double zZ = s * rz;
		double c = Math.cos(leanRad);
		double sn = Math.sin(leanRad);
		float sc = st.layout.scale[i];
		m[0] = (float) (xX * sc);
		m[1] = (float) (-zX * sn * sc);
		m[2] = (float) (zX * c * sc);
		m[3] = 0f;
		m[4] = (float) (c * sc);
		m[5] = (float) (sn * sc);
		m[6] = (float) (xZ * sc);
		m[7] = (float) (-zZ * sn * sc);
		m[8] = (float) (zZ * c * sc);
	}

	/** World position of the glowing tip of blade i at row time t. */
	static void tipPosition(RowsFx.State st, int i, double t, double[] out) {
		double lean = Math.toRadians(st.layout.leanDeg(t, i));
		basis(st, i, lean, M);
		double off = offsetOf(st, i, t);
		double by = st.ground[i] - 0.5 + off;
		out[0] = st.bx[i] + M[1] * BladeRowLayout.BLADE_LENGTH + M[2] * TIP_Z;
		out[1] = by + M[4] * BladeRowLayout.BLADE_LENGTH + M[5] * TIP_Z;
		out[2] = st.bz[i] + M[7] * BladeRowLayout.BLADE_LENGTH + M[8] * TIP_Z;
	}

	private static int lightOf(RowsFx.State st, int i) {
		int l = st.light[i];
		int block = LightmapTextureManager.getBlockLightCoordinates(l);
		int sky = LightmapTextureManager.getSkyLightCoordinates(l);
		return LightmapTextureManager.pack(Math.max(block, (int) FxTune.d("bankai.minBlock", 9)), sky);
	}

	// ------------------------------------------------------------------------------------------ draw

	private static void draw(WorldRenderContext ctx) {
		var states = RowsFx.states();
		if (states.isEmpty()) {
			lastCpuVertices = 0;
			lastStaticVertices = 0;
			return;
		}
		loadMeshes();
		if (blade == null) {
			return;
		}
		long t0 = System.nanoTime();
		ClientWorld world = ctx.world();
		Camera cam = ctx.camera();
		Vec3d cp = cam.getPos();
		Frustum frustum = ctx.frustum();
		int cpuQuads = 0;
		int cpuBlades = 0;
		lastStatic = false;
		int staticVerts = 0;
		BufferBuilder buf = null;
		for (RowsFx.State st : states.values()) {
			double t = st.time();
			BladeRowLayout l = st.layout;
			boolean staticOk = useVbo && st.endAt < 0 && t >= st.settleAt;
			if (staticOk) {
				if (!st.vboValid) {
					buildStatic(st, world);
				}
				drawStatic(st, cp);
				staticVerts += st.vboVertices;
				lastStatic = true;
			} else {
				if (buf == null) {
					buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
				}
				double lodDist = l.n > 400 ? Math.min(24.0, FxConfig.bladeLodDistance) : FxConfig.bladeLodDistance;
				double cull = FxConfig.bladeCullDistance;
				double comp = l.completionTime();
				boolean sweep = FxConfig.effectQuality >= 0.4;
				for (int i = 0; i < l.n; i++) {
					double off = offsetOf(st, i, t);
					if (off < -7.6) {
						continue;
					}
					double dx = st.bx[i] - cp.x;
					double dz = st.bz[i] - cp.z;
					double dy = st.ground[i] + 4 - cp.y;
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (d > cull) {
						continue;
					}
					double lean = Math.toRadians(l.leanDeg(t, i));
					basis(st, i, lean, M);
					double by = st.ground[i] - 0.5 + off;
					double tx = st.bx[i] + M[1] * 8.0;
					double tz = st.bz[i] + M[7] * 8.0;
					double ty = by + M[4] * 8.0;
					if (frustum != null) {
						double r = 0.9 * l.scale[i];
						Box box = new Box(Math.min(st.bx[i], tx) - r, Math.min(by, ty) - 0.2, Math.min(st.bz[i], tz) - r, Math.max(st.bx[i], tx) + r, Math.max(by, ty) + 0.2,
								Math.max(st.bz[i], tz) + r);
						if (!frustum.isVisible(box)) {
							continue;
						}
					}
					float emissive = 0f;
					if (sweep && st.endAt < 0) {
						emissive = (float) Math.min(1.0, BankaiCurves.sweepBoost(l.dist[i], t, comp) / 0.35 * 0.7);
					}
					FxMesh m = d < lodDist || lod == null ? blade : lod;
					m.writeBlade(buf, M, st.bx[i] - cp.x, by - cp.y, st.bz[i] - cp.z, lightOf(st, i), TIP_Y, WHITE, TIP, emissive, 1f / l.scale[i]);
					cpuQuads += m.quads;
					cpuBlades++;
				}
			}
		}
		if (buf != null) {
			BuiltBuffer built = buf.endNullable();
			if (built != null) {
				bladeLayer.draw(built);
			}
		}
		// hilts, ripples and decals of every state
		drawExtras(ctx, cp);
		lastCpuVertices = cpuQuads * 4;
		lastStaticVertices = staticVerts;
		lastCpuBlades = cpuBlades;
		lastMs = (System.nanoTime() - t0) / 1.0e6;
		sumMs += lastMs;
		frames++;
	}

	// ------------------------------------------------------------------------------------------ static buffer

	private static void buildStatic(RowsFx.State st, ClientWorld world) {
		long t0 = System.nanoTime();
		BladeRowLayout l = st.layout;
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
		int quads = 0;
		double t = 1.0e6; // the final pose
		for (int i = 0; i < l.n; i++) {
			double lean = Math.toRadians(l.leanDeg(t, i));
			basis(st, i, lean, M);
			double by = st.ground[i] - 0.5;
			blade.writeBlade(b, M, st.bx[i] - st.fx, by - st.fy, st.bz[i] - st.fz, lightOf(st, i), TIP_Y, WHITE, TIP, 0f, 1f / l.scale[i]);
			quads += blade.quads;
		}
		BuiltBuffer built = b.endNullable();
		if (st.vbo == null) {
			st.vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
		}
		if (built != null) {
			st.vbo.bind();
			st.vbo.upload(built);
			VertexBuffer.unbind();
		}
		st.vboVertices = quads * 4;
		st.vboValid = true;
		int h = 0;
		for (int i = 0; i < l.n; i++) {
			h = h * 31 + st.light[i];
		}
		st.vboLightHash = h;
		vboBuilds++;
		lastBuildMs = (System.nanoTime() - t0) / 1.0e6;
		ReiatsuTest.LOGGER.info("[fx] static row buffer built: {} blades, {} vertices, {} ms", l.n, st.vboVertices, String.format(java.util.Locale.ROOT, "%.1f", lastBuildMs));
	}

	private static void drawStatic(RowsFx.State st, Vec3d cp) {
		if (st.vbo == null || st.vboVertices == 0) {
			return;
		}
		bladeLayer.startDrawing();
		ShaderProgram sp = RenderSystem.getShader();
		Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix()).translate((float) (st.fx - cp.x), (float) (st.fy - cp.y), (float) (st.fz - cp.z));
		st.vbo.bind();
		st.vbo.draw(mv, RenderSystem.getProjectionMatrix(), sp);
		VertexBuffer.unbind();
		bladeLayer.endDrawing();
	}

	// ------------------------------------------------------------------------------------------ hilt, ripples, decals

	private static void drawExtras(WorldRenderContext ctx, Vec3d cp) {
		ClientWorld world = ctx.world();
		BufferBuilder hb = null;
		BufferBuilder rb = null;
		BufferBuilder cb = null;
		for (RowsFx.State st : RowsFx.states().values()) {
			double t = st.time();
			double now = FxClock.now;
			double hx = st.hiltX();
			double hz = st.hiltZ();
			double hg = st.hiltGround;
			int light = LightmapTextureManager.pack(Math.max(7, world.getLightLevel(net.minecraft.world.LightType.BLOCK, net.minecraft.util.math.BlockPos.ofFloored(hx, hg + 1, hz))),
					world.getLightLevel(net.minecraft.world.LightType.SKY, net.minecraft.util.math.BlockPos.ofFloored(hx, hg + 1, hz)));
			double hs = FxTune.d("bankai.hiltScale", 1.8);
			if (hilt != null) {
				if (hb == null) {
					hb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
				}
				double hy;
				double yawDeg = st.yawDeg;
				if (st.endAt >= 0) {
					// the hilt rises out of the ground and flies to the hand (0.4 s, ease out cubic)
					double x = FxMath.clamp((now - st.endAt) / 0.4);
					double e = FxMath.oc(x);
					Entity ow = ctx.world().getEntityById(st.ownerId);
					Vec3d hand = ow != null ? ow.getLerpedPos(ctx.tickCounter().getTickDelta(true)).add(0, 1.1, 0) : new Vec3d(st.fx, st.fy + 1.1, st.fz);
					double sx = hx;
					double sy = hg - BankaiCurves.SINK_DEPTH * hs;
					double sz = hz;
					double px = FxMath.lerp(sx, hand.x, e);
					double py = FxMath.lerp(sy, hand.y, e) + Math.sin(Math.PI * e) * 0.6;
					double pz = FxMath.lerp(sz, hand.z, e);
					fillBasis(yawDeg, hs);
					if (x < 1.0) {
						hilt.write(hb, M, px - cp.x, py - cp.y, pz - cp.z, WHITE, light);
					}
				} else {
					hy = hg + BankaiCurves.hiltHeight(t, 1.1) * (t < BankaiCurves.DROP_TIME ? 1.0 : hs);
					// the drop starts at the hand (0 ahead of the feet) and ends at the hilt point
					double k = FxMath.iq(FxMath.clamp(t / BankaiCurves.DROP_TIME));
					double px = FxMath.lerp(st.fx, hx, k);
					double pz = FxMath.lerp(st.fz, hz, k);
					fillBasis(yawDeg, hs);
					hilt.write(hb, M, px - cp.x, hy - cp.y, pz - cp.z, WHITE, light);
				}
			}
			if (ripple != null && st.endAt < 0) {
				if (rb == null) {
					rb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
				}
				for (int k = 0; k < 3; k++) {
					double rad = BankaiCurves.rippleRadius(k, t);
					double a = BankaiCurves.rippleAlpha(k, t);
					if (rad > 0.02 && a > 0.01) {
						ripple(rb, hx - cp.x, hg + 0.03 - cp.y, hz - cp.z, rad, a * FxTune.d("bankai.rippleAlpha", 0.85));
					}
				}
				// the small ripple of the hold (0 to 1.5 every 4 s)
				if (t > st.layout.completionTime()) {
					double ph = ((FxClock.now - st.epoch) % 4.0) / 1.2;
					if (ph < 1.0) {
						ripple(rb, hx - cp.x, hg + 0.03 - cp.y, hz - cp.z, 1.5 * FxMath.oc(ph), (1 - ph) * 0.6);
					}
				}
			}
			// crack scars under the blades near the camera
			if (FxConfig.decals) {
				BladeRowLayout l = st.layout;
				double fadeEnd = st.endAt >= 0 ? 1.0 - FxMath.clamp((now - st.endAt) / 1.0) : 1.0;
				double speed = FxTune.d("bankai.faultSpeed", 100);
				for (int i = 0; i < l.n; i++) {
					double dx = st.bx[i] - cp.x;
					double dz = st.bz[i] - cp.z;
					if (dx * dx + dz * dz > 32 * 32) {
						continue;
					}
					double reveal = FxMath.smoothstep(BankaiCurves.faultTime(l.dist[i], speed), BankaiCurves.faultTime(l.dist[i], speed) + 0.15, t);
					if (reveal <= 0.01) {
						continue;
					}
					if (cb == null) {
						cb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
					}
					scar(cb, dx, st.ground[i] + 0.02 - cp.y, dz, 1.7, i * 2.399, (int) (255 * reveal * fadeEnd), lightOf(st, i));
				}
			}
		}
		if (cb != null) {
			BuiltBuffer b = cb.endNullable();
			if (b != null) {
				crackLayer.draw(b);
			}
		}
		if (hb != null) {
			BuiltBuffer b = hb.endNullable();
			if (b != null) {
				bladeLayer.draw(b);
			}
		}
		if (rb != null) {
			BuiltBuffer b = rb.endNullable();
			if (b != null) {
				rippleLayer.draw(b);
			}
		}
	}

	private static void fillBasis(double yawDeg, double scale) {
		double yaw = Math.toRadians(yawDeg);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -fz;
		double rz = fx;
		M[0] = (float) (fx * scale);
		M[1] = 0;
		M[2] = (float) (rx * scale);
		M[3] = 0;
		M[4] = (float) scale;
		M[5] = 0;
		M[6] = (float) (fz * scale);
		M[7] = 0;
		M[8] = (float) (rz * scale);
	}

	private static void ripple(BufferBuilder b, double ox, double oy, double oz, double radius, double alpha) {
		int a = (int) (255 * Math.max(0, Math.min(1, alpha)));
		int color = (a << 24) | 0xCFC3F0;
		float[] m = {(float) radius, 0, 0, 0, 1f, 0, 0, 0, (float) radius};
		ripple.write(b, m, ox, oy, oz, color, 0xF000F0);
	}

	private static void scar(BufferBuilder b, double ox, double oy, double oz, double size, double rot, int alpha, int light) {
		double c = Math.cos(rot) * size * 0.5;
		double s = Math.sin(rot) * size * 0.5;
		int color = (alpha << 24) | 0xFFFFFF;
		int o = OverlayTexture.DEFAULT_UV;
		float y = (float) oy;
		b.vertex((float) (ox - c - s), y, (float) (oz - s + c), color, 0f, 1f, o, light, 0f, 1f, 0f);
		b.vertex((float) (ox + c - s), y, (float) (oz + s + c), color, 1f, 1f, o, light, 0f, 1f, 0f);
		b.vertex((float) (ox + c + s), y, (float) (oz + s - c), color, 1f, 0f, o, light, 0f, 1f, 0f);
		b.vertex((float) (ox - c + s), y, (float) (oz - s - c), color, 0f, 0f, o, light, 0f, 1f, 0f);
	}

	// ------------------------------------------------------------------------------------------ glow source: the tips

	private static void emitGlow(FxGlowBatch.Emitter e) {
		var states = RowsFx.states();
		if (states.isEmpty()) {
			return;
		}
		double[] tip = new double[3];
		double glow = FxTune.d("bankai.tipGlow", 0.45);
		double size = FxTune.d("bankai.tipSize", 1.6);
		for (RowsFx.State st : states.values()) {
			double t = st.time();
			BladeRowLayout l = st.layout;
			double breath = BankaiCurves.tipBreath(t);
			double fade = st.endAt >= 0 ? 1.0 - FxMath.clamp((FxClock.now - st.endAt) / 0.5) : 1.0;
			double lit = FxMath.smoothstep(l.completionTime() - 0.4, l.completionTime() + 0.2, t);
			for (int i = 0; i < l.n; i++) {
				double off = offsetOf(st, i, t);
				if (off < -7.0) {
					continue;
				}
				tipPosition(st, i, t, tip);
				double rise = FxMath.clamp((off + 7.0) / 3.0);
				double k = glow * breath * fade * (0.45 + 0.55 * Math.max(lit, rise * 0.6));
				e.sprite(GlowSprite.GLOW_CORE, tip[0], tip[1], tip[2], size * l.scale[i], 0, 0.95f, 0.91f, 1f, k);
			}
			// the sparkle running up: a faint lilac star at every 4th tip, twinkling
			for (int i = 0; i < l.n; i += 4) {
				double off = offsetOf(st, i, t);
				if (off < -1.0 || st.endAt >= 0) {
					continue;
				}
				tipPosition(st, i, t, tip);
				double tw = 0.5 + 0.5 * Math.sin(FxClock.now * 3.0 + i * 1.7);
				e.sprite(GlowSprite.STAR4, tip[0], tip[1], tip[2], 0.9 * l.scale[i], i * 0.3, 1f, 0.96f, 1f, 0.28 * tw * fade * lit);
			}
		}
	}
}
