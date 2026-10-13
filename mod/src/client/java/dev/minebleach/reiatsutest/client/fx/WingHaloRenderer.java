package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.HakuteikenCurves;
import dev.minebleach.reiatsutest.core.fx.HakuteikenFirstPerson;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Wings, halo and blade of Shukei: Hakuteiken (VFX_STORYBOARD 1.7.3 {@code WingHaloRenderer}, 6.3): the five exported meshes of the model
 * (two wing sheets, the halo ring, the blade body and tip) on the owner, plus the glows that belong to them (hand ignition, halo bloom ring,
 * feather shaft bloom, blade strip and the sparks sliding along it).
 * <p>The wing sheets are one continuous mesh per wing with the feather shape in the texture alpha, so the unfurl is a vertex deformation in
 * polar coordinates about the root ({@link HakuteikenCurves#fanVertex}: from folded at -12 degrees and 15 percent length to the final fan, ease out
 * back), the feather ripple an offset along the wing normal and the dissolve a vertex alpha by radius, tips first. Two passes: the sheet in the
 * translucent emissive layer (white, full bright, alpha cut by the texture) and the overglow, the same mesh 6 percent larger about the root in an
 * additive (source alpha, one) layer, which makes the white feathers bloom.
 */
public final class WingHaloRenderer {
	private static final Identifier EMISSIVE = ReiatsuTest.id("textures/item/byakuya_bankai_emissive.png");
	private static RenderLayer solid;
	private static RenderLayer additive;
	private static FxMesh wingL;
	private static FxMesh wingR;
	private static FxMesh halo;
	private static FxMesh bladeBody;
	private static FxMesh bladeTip;
	private static boolean tried;
	/** Second allocator: the solid and the additive buffer are open at the same time and must not share one. */
	private static Tessellator addTess;
	private static final float[] M = new float[9];
	private static final double[] TMP = new double[3];
	private static final double[] W = new double[3];
	/** First-person guard of the local camera (see {@link FpView}). */
	private static final FpView FP = new FpView();

	/**
	 * First-person guard (core: {@link HakuteikenFirstPerson}). Only for the owner seen from his own first-person camera: the wings, halo and
	 * their glows hang off the body yaw limited to a few degrees around the camera yaw, a little further back, and fade out by their depth
	 * along the view. Third person and the views of other players use the pose as it is. Tunables: {@code hak.fpGuard} (0 = off),
	 * {@code hak.fpYawMax} degrees, {@code hak.fpBack} metres, {@code hak.fpClear} / {@code hak.fpFull} metres of view depth; reduce motion and
	 * photosensitive safe halve the yaw limit and widen the fade.
	 */
	private static final class FpView {
		boolean enabled;
		boolean on;
		double cx;
		double cy;
		double cz;
		double camYaw;
		double yawMax;
		double back;
		double clear;
		double full;
		final double[] fwd = new double[3];
		final HakuteikenFx.Pose rig = new HakuteikenFx.Pose();

		void begin(Camera cam) {
			on = false;
			enabled = cam != null && cam.isReady() && !cam.isThirdPerson() && FxTune.d("hak.fpGuard", 1.0) > 0.5;
			if (!enabled) {
				return;
			}
			Vec3d p = cam.getPos();
			cx = p.x;
			cy = p.y;
			cz = p.z;
			camYaw = Math.toRadians(cam.getYaw());
			HakuteikenFirstPerson.forward(camYaw, Math.toRadians(cam.getPitch()), fwd);
			boolean gentle = FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
			yawMax = HakuteikenFirstPerson.yawLimit(FxTune.d("hak.fpYawMax", HakuteikenFirstPerson.YAW_MAX_DEG), gentle);
			back = Math.max(0.0, FxTune.d("hak.fpBack", HakuteikenFirstPerson.BACK));
			clear = FxTune.d("hak.fpClear", HakuteikenFirstPerson.CLEAR);
			full = FxTune.d("hak.fpFull", HakuteikenFirstPerson.FULL) * (gentle ? 1.3 : 1.0);
		}

		/** The pose the rig of this state is drawn with: the limited one for the first-person view of its owner, else the pose itself. */
		HakuteikenFx.Pose rigFor(HakuteikenFx.State st, Camera cam, HakuteikenFx.Pose pose) {
			on = enabled && cam.getFocusedEntity() != null && cam.getFocusedEntity().getId() == st.ownerId;
			if (!on) {
				return pose;
			}
			double yaw = HakuteikenFirstPerson.limitYaw(pose.yaw, camYaw, yawMax);
			rig.yaw = yaw;
			rig.sin = Math.sin(yaw);
			rig.cos = Math.cos(yaw);
			// pushed back along the rig's own backwards axis (forward is (-sin, cos))
			rig.fx = pose.fx + rig.sin * back;
			rig.fy = pose.fy;
			rig.fz = pose.fz - rig.cos * back;
			rig.lx = pose.lx;
			rig.ly = pose.ly;
			rig.lz = pose.lz;
			rig.hx = pose.hx;
			rig.hy = pose.hy;
			rig.hz = pose.hz;
			rig.eyeHeight = pose.eyeHeight;
			return rig;
		}

		/** Opacity factor of a world point: 1 away from the guard, else by its depth along the view. */
		double fade(double x, double y, double z) {
			return on ? HakuteikenFirstPerson.fade(HakuteikenFirstPerson.depth(x, y, z, cx, cy, cz, fwd), clear, full) : 1.0;
		}
	}

	/**
	 * For the shed and falling feathers ({@link HakuteikenFx#frame}): the pose the wings are drawn with right now, so the feathers come off the
	 * wings that are seen; in the first-person view of the owner that is the guarded rig.
	 */
	public static HakuteikenFx.Pose guardedPose(HakuteikenFx.State st, HakuteikenFx.Pose pose) {
		Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
		FP.begin(camera);
		return FP.rigFor(st, camera, pose);
	}

	/** True when a point lies so near the first-person eye that the guard hides it (valid after {@link #guardedPose}); a feather born there is not spawned. */
	public static boolean guardHides(double x, double y, double z) {
		return FP.on && FP.fade(x, y, z) < 0.05;
	}

	private static int scaleAlpha(int argb, double k) {
		int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0.0, Math.min(1.0, k)) + 0.5);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** Stats for the harness. */
	public static double lastMs;
	public static double sumMs;
	public static long frames;
	public static int lastVertices;
	public static boolean lastWingsDrawn;
	public static boolean lastHaloDrawn;
	public static double lastFan;

	private WingHaloRenderer() {
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(WingHaloRenderer::draw);
		FxGlowBatch.addSource(WingHaloRenderer::emitGlow);
	}

	public static void resetStats() {
		sumMs = 0;
		frames = 0;
	}

	private static RenderLayer solid() {
		if (solid == null) {
			solid = RenderLayer.getEntityTranslucentEmissive(EMISSIVE);
		}
		return solid;
	}

	/** Alpha-respecting additive layer for the overglow: source alpha x colour added to the frame, no depth write, no culling. */
	private static RenderLayer additive() {
		if (additive == null) {
			additive = RenderLayer.of("reiatsu_wing_glow", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 1536, false, true,
					RenderLayer.MultiPhaseParameters.builder()
							.program(RenderPhase.ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
							.texture(new RenderPhase.Texture(EMISSIVE, false, false))
							.transparency(RenderPhase.LIGHTNING_TRANSPARENCY)
							.cull(RenderPhase.DISABLE_CULLING)
							.writeMaskState(RenderPhase.COLOR_MASK)
							.overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
							.build(false));
		}
		return additive;
	}

	private static void load() {
		if (tried) {
			return;
		}
		tried = true;
		wingL = FxMesh.load("byakuya_bankai", "hakuteiken_wing_l");
		wingR = FxMesh.load("byakuya_bankai", "hakuteiken_wing_r");
		halo = FxMesh.load("byakuya_bankai", "hakuteiken_halo");
		bladeBody = FxMesh.load("byakuya_bankai", "hakuteiken_blade_body");
		bladeTip = FxMesh.load("byakuya_bankai", "hakuteiken_blade_tip");
	}

	private static int argb(double a, float[] rgb) {
		int ia = (int) (255 * Math.max(0, Math.min(1, a)) + 0.5);
		return (ia << 24) | (FxMath.packRgb(rgb) & 0xFFFFFF);
	}

	// ------------------------------------------------------------------------------------------ draw

	private static void draw(WorldRenderContext ctx) {
		var states = HakuteikenFx.states();
		lastWingsDrawn = false;
		lastHaloDrawn = false;
		if (states.isEmpty()) {
			lastVertices = 0;
			return;
		}
		load();
		long t0 = System.nanoTime();
		ClientWorld world = ctx.world();
		Vec3d cp = ctx.camera().getPos();
		float td = ctx.tickCounter().getTickDelta(true);
		Camera camera = ctx.camera();
		FP.begin(camera);
		BufferBuilder solidBuf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
		if (addTess == null) {
			addTess = new Tessellator(1 << 17);
		}
		BufferBuilder addBuf = addTess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
		int quads = 0;
		HakuteikenFx.Pose pose = new HakuteikenFx.Pose();
		float[] tint = FxMath.hex(FxTune.s("hak.tint", "#F4F8FF"));
		float[] tintGlow = FxMath.hex(FxTune.s("hak.glowTint", "#F4F8FF"));
		for (HakuteikenFx.State st : states.values()) {
			double t = st.time();
			HakuteikenCurves.Timing tm = st.tm;
			if (t < 0 || t > tm.life) {
				continue;
			}
			HakuteikenFx.pose(st, world, td, pose);
			HakuteikenFx.Pose rp = FP.rigFor(st, camera, pose);
			double dist = cp.distanceTo(new Vec3d(pose.fx, pose.fy, pose.fz));
			if (dist > 160) {
				continue;
			}
			// the feather ripple and the pulse stand still during the held breath
			double tv = HakuteikenCurves.rippleTime(tm, t);
			double wingScale = FxConfig.wingScale * FxTune.d("hak.wingScale", 1.0);
			if (HakuteikenCurves.wingsVisible(tm, t) && wingL != null && wingR != null) {
				double fan = HakuteikenCurves.fan(tm, t);
				lastFan = fan;
				double ripple = FxTune.d("hak.ripple", 1.0);
				for (int side = 1; side >= -1; side -= 2) {
					FxMesh wm = side == 1 ? wingL : wingR;
					quads += writeWing(solidBuf, wm, side, rp, tm, t, tv, fan, wingScale, 1.0, tint, FxTune.d("hak.wingAlpha", 1.0), ripple, cp);
					if (FxConfig.fxTier != FxConfig.TierSetting.LOW) {
						quads += writeWing(addBuf, wm, side, rp, tm, t, tv, fan, wingScale, FxTune.d("hak.overglowScale", 1.06), tintGlow, FxTune.d("hak.overglowAlpha", 0.35), ripple,
								cp);
					}
				}
				lastWingsDrawn = true;
			}
			double ha = HakuteikenCurves.haloAlpha(tm, t);
			if (ha > 0.003 && halo != null) {
				double hs = HakuteikenCurves.haloScale(tm, t) * FxTune.d("hak.haloScale", 1.0);
				double[] hc = new double[3];
				double top = pose.eyeHeight + FxTune.d("hak.headTop", 0.18) + FxTune.d("hak.haloUp", 0.35);
				rp.local(0, top, -FxTune.d("hak.haloBack", 0.45), hc);
				double spin = FxTune.d("hak.haloSpin", 0.25) * tv;
				quads += writeHalo(solidBuf, rp, hc, hs, spin, argb(ha * FxTune.d("hak.haloAlpha", 1.0), tint), cp);
				if (FxConfig.fxTier != FxConfig.TierSetting.LOW) {
					quads += writeHalo(addBuf, rp, hc, hs * 1.06, spin, argb(ha * FxTune.d("hak.haloGlow", 0.45), tintGlow), cp);
				}
				lastHaloDrawn = true;
			}
			double len = HakuteikenCurves.bladeLength(tm, t, st.lineLength);
			if (len > 0.05 && bladeBody != null && bladeTip != null) {
				quads += writeBlade(solidBuf, addBuf, st, pose, len, tm, tv, tint, tintGlow, cp);
			}
		}
		BuiltBuffer b1 = solidBuf.endNullable();
		if (b1 != null) {
			solid().draw(b1);
		}
		BuiltBuffer b2 = addBuf.endNullable();
		if (b2 != null) {
			additive().draw(b2);
		}
		lastVertices = quads * 4;
		lastMs = (System.nanoTime() - t0) / 1.0e6;
		sumMs += lastMs;
		frames++;
	}

	/** Writes one wing sheet: fan deformation, ripple, dissolve alpha; returns the quad count. */
	private static int writeWing(BufferBuilder b, FxMesh m, int side, HakuteikenFx.Pose pose, HakuteikenCurves.Timing tm, double t, double tv, double fan, double scale, double glowScale,
			float[] rgb, double alphaMul, double rippleMul, Vec3d cp) {
		double rootX = side * FxTune.d("hak.rootX", 0.12);
		double rootY = FxTune.d("hak.rootY", 1.35);
		double rootZ = -FxTune.d("hak.rootBack", 0.15);
		int n = m.quads * 4;
		int o = OverlayTexture.DEFAULT_UV;
		for (int c = 0; c < n; c++) {
			double x = m.pos[c * 3];
			double y = m.pos[c * 3 + 1];
			double z = m.pos[c * 3 + 2];
			double rho = Math.sqrt(x * x + y * y) / HakuteikenCurves.WING_MAX_R;
			HakuteikenCurves.fanVertex(tm, side, x, y, z, fan, TMP);
			double r2 = Math.sqrt(TMP[0] * TMP[0] + TMP[1] * TMP[1]);
			// feather ripple along the wing normal (forward), fixed at the root
			double rip = HakuteikenCurves.ripple(tv, r2) * rippleMul * Math.min(1.0, r2 / 2.0);
			double lx = TMP[0] * glowScale;
			double ly = TMP[1] * glowScale;
			double lz = (TMP[2] + rip) * glowScale;
			pose.local(rootX + lx * scale, rootY + ly * scale, rootZ + lz * scale, W);
			double alpha = HakuteikenCurves.wingAlpha(tm, t, rho) * alphaMul * FP.fade(W[0], W[1], W[2]);
			int color = argb(alpha, rgb);
			float nx0 = m.nrm[c * 3];
			float ny0 = m.nrm[c * 3 + 1];
			float nz0 = m.nrm[c * 3 + 2];
			float nx = (float) (nx0 * pose.cos - nz0 * pose.sin);
			float nz = (float) (nx0 * pose.sin + nz0 * pose.cos);
			b.vertex((float) (W[0] - cp.x), (float) (W[1] - cp.y), (float) (W[2] - cp.z), color, m.uv[c * 2], m.uv[c * 2 + 1], o, 0xF000F0, nx, ny0, nz);
		}
		return m.quads;
	}

	private static int writeHalo(BufferBuilder b, HakuteikenFx.Pose pose, double[] centre, double scale, double spin, int color, Vec3d cp) {
		int n = halo.quads * 4;
		int o = OverlayTexture.DEFAULT_UV;
		double cs = Math.cos(spin);
		double sn = Math.sin(spin);
		for (int c = 0; c < n; c++) {
			double x = halo.pos[c * 3] * scale;
			double y = halo.pos[c * 3 + 1] * scale;
			double z = halo.pos[c * 3 + 2] * scale;
			double rx = x * cs - y * sn;
			double ry = x * sn + y * cs;
			// player frame to world: x left, z forward
			double wx = centre[0] + rx * pose.cos - z * pose.sin;
			double wy = centre[1] + ry;
			double wz = centre[2] + rx * pose.sin + z * pose.cos;
			float nx0 = halo.nrm[c * 3];
			float nz0 = halo.nrm[c * 3 + 2];
			float nx = (float) (nx0 * pose.cos - nz0 * pose.sin);
			float nz = (float) (nx0 * pose.sin + nz0 * pose.cos);
			b.vertex((float) (wx - cp.x), (float) (wy - cp.y), (float) (wz - cp.z), FP.on ? scaleAlpha(color, FP.fade(wx, wy, wz)) : color, halo.uv[c * 2], halo.uv[c * 2 + 1], o, 0xF000F0, nx, halo.nrm[c * 3 + 1], nz);
		}
		return halo.quads;
	}

	/** The blade body (stretched along the line direction) and its tip mesh at the end, in both layers. */
	private static int writeBlade(BufferBuilder solidBuf, BufferBuilder addBuf, HakuteikenFx.State st, HakuteikenFx.Pose pose, double len, HakuteikenCurves.Timing tm, double tv,
			float[] tint, float[] tintGlow, Vec3d cp) {
		double dx = st.end[0] - pose.hx;
		double dy = st.end[1] - pose.hy;
		double dz = st.end[2] - pose.hz;
		double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dl < 1e-3) {
			return 0;
		}
		dx /= dl;
		dy /= dl;
		dz /= dl;
		// X = dir x up, Z = X x dir
		double xx = dy * 0 - dz * 1;
		double xy = 0;
		double xz = dx * 1 - dy * 0;
		double xl = Math.sqrt(xx * xx + xz * xz);
		if (xl < 1e-4) {
			xx = 1;
			xz = 0;
			xl = 1;
		}
		xx /= xl;
		xz /= xl;
		double zx = xy * dz - xz * dy;
		double zy = xz * dx - xx * dz;
		double zz = xx * dy - xy * dx;
		double wid = FxTune.d("hak.bladeWidth", 3.0);
		double pulse = HakuteikenCurves.bladePulse(tv);
		double alpha = Math.min(1.0, 0.85 * pulse);
		int quads = 0;
		float[] pos0 = {(float) (pose.hx - cp.x), (float) (pose.hy - cp.y), (float) (pose.hz - cp.z)};
		// body: local x thickness, y along the blade (mesh length 1 m), z width
		M[0] = (float) (xx * wid);
		M[3] = (float) (xy * wid);
		M[6] = (float) (xz * wid);
		M[1] = (float) (dx * len);
		M[4] = (float) (dy * len);
		M[7] = (float) (dz * len);
		M[2] = (float) (zx * wid);
		M[5] = (float) (zy * wid);
		M[8] = (float) (zz * wid);
		bladeBody.write(solidBuf, M, pos0[0], pos0[1], pos0[2], argb(alpha, tint), 0xF000F0);
		quads += bladeBody.quads;
		if (FxConfig.fxTier != FxConfig.TierSetting.LOW) {
			bladeBody.write(addBuf, M, pos0[0], pos0[1], pos0[2], argb(0.5 * pulse, tintGlow), 0xF000F0);
			quads += bladeBody.quads;
		}
		// tip at the end of the body
		M[1] = (float) dx;
		M[4] = (float) dy;
		M[7] = (float) dz;
		bladeTip.write(solidBuf, M, pos0[0] + dx * len, pos0[1] + dy * len, pos0[2] + dz * len, argb(alpha, tint), 0xF000F0);
		quads += bladeTip.quads;
		return quads;
	}

	// ------------------------------------------------------------------------------------------ glow source (after the grade)

	private static void emitGlow(FxGlowBatch.Emitter e) {
		var states = HakuteikenFx.states();
		if (states.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld world = mc.world;
		if (world == null) {
			return;
		}
		float td = mc.getRenderTickCounter().getTickDelta(false);
		Camera camera = mc.gameRenderer.getCamera();
		FP.begin(camera);
		HakuteikenFx.Pose pose = new HakuteikenFx.Pose();
		double[] hc = new double[3];
		double[] a = new double[3];
		double[] bb = new double[3];
		double glowMul = FxTune.d("hak.glowMul", 1.0);
		for (HakuteikenFx.State st : states.values()) {
			double t = st.time();
			HakuteikenCurves.Timing tm = st.tm;
			if (t < 0 || t > tm.life) {
				continue;
			}
			HakuteikenFx.pose(st, world, td, pose);
			HakuteikenFx.Pose rp = FP.rigFor(st, camera, pose);
			double tv = HakuteikenCurves.rippleTime(tm, t);
			double fade = 1.0 - FxMath.smoothstep(tm.lineFade, tm.lineFade + 0.5, t);
			// 0.45 ignition: white core at the hand, glow_core 0.3 to 1.2, a flare of size 3
			double ig = FxMath.smoothstep(tm.ignite, tm.ignite + 0.2, t) * fade;
			if (ig > 0.01) {
				double size = FxMath.lerp(0.3, FxTune.d("hak.coreSize", 1.2), FxMath.oc((t - tm.ignite) / 0.5));
				e.sprite(GlowSprite.GLOW_CORE, pose.hx, pose.hy, pose.hz, size * (0.9 + 0.1 * HakuteikenCurves.bladePulse(tv)), 0, 1f, 1f, 1f, FxTune.d("hak.coreGlow", 1.0) * ig * glowMul);
				e.sprite(GlowSprite.FLARE, pose.hx, pose.hy, pose.hz, FxTune.d("hak.flareSize", 3.0) * ig, 0, 0.96f, 0.97f, 1f, 0.7 * ig * glowMul);
				e.sprite(GlowSprite.STAR4, pose.hx, pose.hy, pose.hz, 1.4 * ig, tv * 0.8, 1f, 1f, 1f, 0.6 * ig * glowMul);
			}
			// halo: bloom ring on its plane (r 1.3, #FFFFFF 0.45) and a soft glow behind it
			double ha = HakuteikenCurves.haloAlpha(tm, t);
			if (ha > 0.01) {
				double hs = HakuteikenCurves.haloScale(tm, t);
				double top = pose.eyeHeight + FxTune.d("hak.headTop", 0.18) + FxTune.d("hak.haloUp", 0.35);
				rp.local(0, top, -FxTune.d("hak.haloBack", 0.45), hc);
				ha *= FP.fade(hc[0], hc[1], hc[2]);
				double fwx = -rp.sin;
				double fwz = rp.cos;
				double pop = 1.0 + 0.8 * (1.0 - FxMath.smoothstep(tm.wingStart, tm.wingStart + 0.5, t));
				double ringR = FxTune.d("hak.ringBloomR", 1.3) * hs;
				e.plane(GlowSprite.RING_THIN, hc[0], hc[1], hc[2], ringR / 0.4375 * 1.0, fwx, 0, fwz, tv * 0.3, 1f, 1f, 1f, FxTune.d("hak.ringBloom", 0.45) * ha * pop * glowMul);
				e.plane(GlowSprite.RING_SOFT, hc[0], hc[1], hc[2], ringR / 0.375 * 1.05, fwx, 0, fwz, 0, 0.96f, 0.97f, 1f, 0.28 * ha * glowMul);
				e.sprite(GlowSprite.GLOW_SOFT, hc[0] - fwx * 0.1, hc[1], hc[2] - fwz * 0.1, 3.4 * hs, 0, 0.96f, 0.97f, 1f, 0.22 * ha * glowMul);
			}
			// wings: a soft shaft of light along every feather (9 per wing) and a glow at the roots
			if (HakuteikenCurves.wingsVisible(tm, t)) {
				double fan = HakuteikenCurves.fan(tm, t);
				double wingScale = FxConfig.wingScale * FxTune.d("hak.wingScale", 1.0);
				double bloom = FxTune.d("hak.featherBloom", 0.30);
				for (int side = 1; side >= -1; side -= 2) {
					double rootX = side * FxTune.d("hak.rootX", 0.12);
					double rootY = FxTune.d("hak.rootY", 1.35);
					double rootZ = -FxTune.d("hak.rootBack", 0.15);
					rp.local(rootX, rootY, rootZ, a);
					double fa = FP.fade(a[0], a[1], a[2]);
					for (int i = 0; i < 9; i++) {
						double len = HakuteikenCurves.featherLength(i);
						double ang = Math.toRadians(HakuteikenCurves.featherAngleDeg(i));
						HakuteikenCurves.fanVertex(tm, side, side * len * Math.cos(ang), len * Math.sin(ang), 0.8 * (len / 6.0) * (len / 6.0), fan, TMP);
						rp.local(rootX + TMP[0] * wingScale, rootY + TMP[1] * wingScale, rootZ + TMP[2] * wingScale, bb);
						double fb = FP.fade(bb[0], bb[1], bb[2]);
						double alpha = HakuteikenCurves.wingAlpha(tm, t, len / HakuteikenCurves.WING_MAX_R);
						e.streak(GlowSprite.LINE, bb[0], bb[1], bb[2], a[0], a[1], a[2], FxTune.d("hak.featherBloomWidth", 0.55) * wingScale, 0.96f, 0.97f, 1f, bloom * alpha * Math.min(1.0, fan * 2.0) * Math.max(fa, fb) * glowMul);
						e.sprite(GlowSprite.GLOW_SOFT, bb[0], bb[1], bb[2], 0.9 * wingScale, 0, 0.96f, 0.97f, 1f, 0.22 * alpha * Math.min(1.0, fan * 2.0) * fb * glowMul);
					}
					e.sprite(GlowSprite.GLOW_SOFT, a[0], a[1], a[2], 1.6, 0, 1f, 1f, 1f, 0.35 * Math.min(1.0, fan * 2.0) * (1.0 - FxMath.smoothstep(tm.dissolveStart, tm.dissolveEnd, t)) * fa * glowMul);
				}
			}
			// blade: the strip of light along it (width 0.35, 0.4) and sparks sliding toward the tip
			double len = HakuteikenCurves.bladeLength(tm, t, st.lineLength);
			if (len > 0.05) {
				double dx = st.end[0] - pose.hx;
				double dy = st.end[1] - pose.hy;
				double dz = st.end[2] - pose.hz;
				double dl = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
				dx /= dl;
				dy /= dl;
				dz /= dl;
				double pulse = HakuteikenCurves.bladePulse(tv);
				double lineK = FxTune.d("hak.bladeGlow", 0.4) * pulse * (t < tm.strike ? 1.0 : 0.35);
				e.streak(GlowSprite.LINE, pose.hx + dx * len, pose.hy + dy * len, pose.hz + dz * len, pose.hx, pose.hy, pose.hz, FxTune.d("hak.bladeGlowWidth", 0.35), 0.96f, 0.97f, 1f,
						lineK * glowMul);
				double span = Math.min(len, 4.0);
				for (int i = 0; i < 8; i++) {
					double u = ((tv * 1.7 + i / 8.0) % 1.0) * span;
					e.sprite(GlowSprite.STAR4, pose.hx + dx * u, pose.hy + dy * u, pose.hz + dz * u, 0.32 + 0.1 * Math.sin(i + tv * 9), i * 0.9 + tv * 2.0, 1f, 1f, 1f,
							0.7 * (1.0 - u / (span + 1e-3) * 0.3) * glowMul);
				}
				e.sprite(GlowSprite.GLOW_CORE, pose.hx + dx * len, pose.hy + dy * len, pose.hz + dz * len, 0.6 * pulse, 0, 1f, 1f, 1f, 0.5 * glowMul);
			}
		}
	}
}
