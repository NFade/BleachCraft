package dev.minebleach.reiatsutest.client.fx;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Body of the Rukia bankai (VFX_STORYBOARD 5.1, 5.2, 1.7.3 "Rukia feature renderer"): the ice sheen (the posed player model drawn
 * again 4 percent larger into the additive energy swirl layer with the scrolling frost texture, vanilla charged creeper pattern)
 * and three floating ribbon chains of {@code rukia_bankai_ribbon_seg} (9 segments and a tip) behind her back that curve in
 * S-loops, sway and unfurl one segment per 0.05 s with a spring flick. Levels and segment counts come from
 * {@link RukiaBankaiClient}; players further than 48 blocks are skipped.
 */
public final class RukiaFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	private static final float[] SHEEN_DEFAULT = FxMath.hex("#9ED3F0");
	/** Statistics for the harness. */
	public static int drawn;
	public static int lastSegments;
	/** Harness: horizontal displacement of the tip of the middle ribbon chain from her feet along her body facing (negative = behind her), blocks. */
	public static double lastTipForward;
	public static double lastTipHeight;
	private static boolean warned;

	public RukiaFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	public static void register() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			if (renderer instanceof PlayerEntityRenderer pr) {
				helper.register(new RukiaFeature(pr));
			}
		});
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity entity, float limbAngle, float limbDistance, float tickDelta,
			float animationProgress, float headYaw, float headPitch) {
		int id = entity.getId();
		if (!RukiaBankaiClient.visible(id) || entity.isInvisible()) {
			return;
		}
		try {
			draw(matrices, vcp, light, entity, tickDelta, id);
		} catch (RuntimeException ex) {
			if (!warned) {
				warned = true;
				dev.minebleach.reiatsutest.ReiatsuTest.LOGGER.error("[fx] rukia feature renderer failed (further errors are not logged)", ex);
			}
		}
	}

	private void draw(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity entity, float tickDelta, int id) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		if (cam.squaredDistanceTo(entity.getPos()) > 48 * 48) {
			return;
		}
		drawn++;
		float[] SHEEN = FxTune.s("rbankai.colSheen", "#9ED3F0").equals("#9ED3F0") ? SHEEN_DEFAULT : FxMath.hex(FxTune.s("rbankai.colSheen", "#9ED3F0"));
		double sheen = RukiaBankaiClient.sheen(id) * FxTune.d("rbankai.sheen", 1.9) * FxConfig.emissiveMultiplier;
		if (sheen > 0.01) {
			double ticks = FxClock.now * 20.0;
			float u = (float) ((0.004 * ticks) % 1.0);
			float v = (float) ((0.011 * ticks) % 1.0);
			VertexConsumer vc = vcp.getBuffer(RenderLayer.getEnergySwirl(FxMeshes.SWIRL, u, v));
			matrices.push();
			matrices.translate(0f, 0.75f, 0f);
			float sh = (float) FxTune.d("rbankai.sheenScale", 1.04);
			matrices.scale(sh, sh, sh);
			matrices.translate(0f, -0.75f, 0f);
			getContextModel().render(matrices, vc, 0xF000F0, OverlayTexture.DEFAULT_UV, FxMeshes.argb(SHEEN[0] * sheen, SHEEN[1] * sheen, SHEEN[2] * sheen, 1.0));
			matrices.pop();
		}
		double segs = RukiaBankaiClient.ribbonSegments(id);
		if (segs > 0.01 && FxMeshes.available()) {
			ribbons(matrices, vcp, light, entity, segs, tickDelta);
		}
	}

	/**
	 * Free fraction (0..1) of a ribbon segment that starts at {@code p} (the {@code base} frame, camera relative after the transform) and runs {@code len}
	 * along {@code d}: four probes along it, each with the centre and the two edges of the strip ({@code halfW} along {@code xAxis}). The fraction is the
	 * last probe that was still free minus nothing, so the strip ends one probe before the first solid block.
	 */
	private static double clipFraction(ClientWorld w, BlockPos.Mutable bp, Matrix4f base, Vector3f p, Vector3f d, Vector3f xAxis, double len, double halfW, Vec3d cam) {
		Vector3f q = new Vector3f();
		for (int s = 1; s <= 4; s++) {
			float t = (float) (len * s / 4.0);
			for (int side = -1; side <= 1; side++) {
				q.set(p).fma(t, d).fma((float) (side * halfW), xAxis);
				base.transformPosition(q);
				if (FxPlace.blocked(w, bp, q.x + cam.x, q.y + cam.y, q.z + cam.z)) {
					return (s - 1) / 4.0;
				}
			}
		}
		return 1.0;
	}

	private void ribbons(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity entity, double segs, float td) {
		int id = entity.getId();
		int maxSeg = FxConfig.fxTier == FxConfig.TierSetting.LOW ? 6 : 9;
		double now = FxClock.now;
		matrices.push();
		// back to the entity frame (feet origin, +z forward after the 180 degree turn below, body yaw applied): the inverse of the renderer's scale(-1,-1,1) and translate(0,-1.501,0)
		// (the player renderer also scales by 0.9375 between the flip and the translate: undone here, so the ribbons are in block units)
		matrices.translate(0f, 1.501f, 0f);
		matrices.scale(1f / 0.9375f, 1f / 0.9375f, 1f / 0.9375f);
		matrices.scale(-1f, -1f, 1f);
		// the renderer's setupTransforms turns the body by (180 - bodyYaw) about Y, so this frame still has +z BEHIND the player (the player model faces -z in model space):
		// without this turn the ribbons stood up and FORWARD of her (the three white spikes of the first review). 180 degrees about Y gives +z forward again.
		matrices.multiply(new org.joml.Quaternionf().rotateY((float) Math.PI));
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		FxMeshes.Mesh seg = FxMeshes.get(FxMeshes.RIBBON_SEG);
		FxMeshes.Mesh tip = FxMeshes.get(FxMeshes.RIBBON_TIP);
		Matrix4f m = new Matrix4f();
		Vector3f p = new Vector3f();
		// collision clipping (rbankai.ribbonClip, 1 = on): a segment that would end inside a solid block is shortened to the free part and the chain stops there
		ClientWorld world = MinecraftClient.getInstance().world;
		boolean clip = world != null && FxTune.d("rbankai.ribbonClip", 1.0) > 0.5;
		double[][] clipF = new double[3][maxSeg + 2];
		BlockPos.Mutable bp = new BlockPos.Mutable();
		Vec3d camPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		double halfW = Math.min(0.45, seg.radius * FxTune.d("rbankai.ribbonWidth", 2.4) * FxTune.d("rbankai.ribbonClipWidth", 0.6));
		Vector3f d = new Vector3f();
		Vector3f xAxis = new Vector3f();
		Vector3f zAxis = new Vector3f();
		// pass 1: diffuse; pass 2: the emissive edge. The geometry is built twice from the same function (cheap: 3 x 10 small meshes).
		for (int pass = 0; pass < 2; pass++) {
			VertexConsumer vc = pass == 0 ? vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(FxMeshes.DIFFUSE)) : vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(FxMeshes.EMISSIVE));
			for (int k = 0; k < 3; k++) {
				double side = k - 1;
				p.set((float) (side * FxTune.d("rbankai.ribbonSpread", 0.10)), (float) FxTune.d("rbankai.ribbonRootY", 0.90), (float) -FxTune.d("rbankai.ribbonRootBack", 0.14));
				double theta = Math.toRadians(FxTune.d("rbankai.ribbonStart", 22));
				double curve = Math.toRadians(FxTune.d("rbankai.ribbonCurve", 18));
				int count = (int) Math.min(maxSeg + 1, Math.ceil(segs));
				for (int j = 0; j < count; j++) {
					boolean isTip = j >= maxSeg;
					// segment direction: up and back, outward by the chain side, S-loop curvature, sway, spring flick of a new segment
					double sway = Math.toRadians(FxTune.d("rbankai.ribbonSway", 6.0)) * Math.sin(2 * Math.PI * 0.35 * now - 0.6 * j + k);
					double tau = RukiaBankaiClient.segmentAge(id, j);
					double flick = tau < 0 ? 0 : Math.toRadians(15) * Math.exp(-tau / 0.2) * Math.cos(2 * Math.PI * tau / 0.28);
					double th = theta + sway + flick;
					double out = side * (FxTune.d("rbankai.ribbonOut", 0.12) + 0.05 * j);
					d.set((float) (out * Math.sin(th)), (float) Math.cos(th), (float) -Math.sin(th)).normalize();
					// orientation: mesh -Y along the direction, width axis along the lateral axis
					xAxis.set(1f, 0f, 0f).fma(-d.x, d).normalize();
					zAxis.set(xAxis).cross(new Vector3f(d).negate());
					Matrix3f rot = new Matrix3f(xAxis.x, xAxis.y, xAxis.z, -d.x, -d.y, -d.z, zAxis.x, zAxis.y, zAxis.z);
					double grow = j + 1 <= segs ? 1.0 : segs - j;
					boolean cut = false;
					if (clip) {
						if (pass == 0) {
							clipF[k][j] = clipFraction(world, bp, base, p, d, xAxis, FxTune.d("rbankai.ribbonSegLen", 0.35) * grow, halfW, camPos);
						}
						double f = clipF[k][j];
						if (f <= 0.0) {
							break;
						}
						if (f < 1.0) {
							grow *= f;
							cut = true;
						}
					}
					m.set(base).translate(p).rotate(rot.getNormalizedRotation(new org.joml.Quaternionf())).scale((float) FxTune.d("rbankai.ribbonWidth", 2.4), (float) grow, 1f);
					if (pass == 0) {
						FxMeshes.draw(vc, isTip ? tip : seg, m, FxMeshes.argb(1, 1, 1, 0.95), 0xF000F0);
					} else {
						FxMeshes.draw(vc, isTip ? tip : seg, m, FxMeshes.argb(0.85, 0.95, 1.0, FxTune.d("rbankai.ribbonGlow", 0.85) * FxConfig.glowIntensity), 0xF000F0);
					}
					p.fma((float) (FxTune.d("rbankai.ribbonSegLen", 0.35) * grow), d);
					if (cut) {
						break; // the chain ends at the obstacle
					}
					theta += j < 5 ? curve : -curve;
				}
				lastSegments = count;
				if (k == 1 && pass == 0) {
					Vector3f w = base.transformPosition(new Vector3f(p));
					Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
					Vec3d f = entity.getLerpedPos(td);
					double dx = w.x + cam.x - f.x;
					double dz = w.z + cam.z - f.z;
					double yaw = Math.toRadians(entity.bodyYaw);
					lastTipForward = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
					lastTipHeight = w.y + cam.y - f.y;
				}
			}
		}
		matrices.pop();
	}
}
