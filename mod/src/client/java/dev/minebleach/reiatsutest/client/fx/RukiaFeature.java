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
	private static final float[] SHEEN = FxMath.hex("#9ED3F0");
	/** Statistics for the harness. */
	public static int drawn;
	public static int lastSegments;

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
		MinecraftClient mc = MinecraftClient.getInstance();
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		if (cam.squaredDistanceTo(entity.getPos()) > 48 * 48) {
			return;
		}
		drawn++;
		double sheen = RukiaBankaiClient.sheen(id) * FxTune.d("rbankai.sheen", 1.0) * FxConfig.emissiveMultiplier;
		if (sheen > 0.01) {
			double ticks = FxClock.now * 20.0;
			float u = (float) ((0.004 * ticks) % 1.0);
			float v = (float) ((0.011 * ticks) % 1.0);
			VertexConsumer vc = vcp.getBuffer(RenderLayer.getEnergySwirl(FxMeshes.SWIRL, u, v));
			matrices.push();
			matrices.translate(0f, 0.75f, 0f);
			matrices.scale(1.04f, 1.04f, 1.04f);
			matrices.translate(0f, -0.75f, 0f);
			getContextModel().render(matrices, vc, 0xF000F0, OverlayTexture.DEFAULT_UV, FxMeshes.argb(SHEEN[0] * sheen, SHEEN[1] * sheen, SHEEN[2] * sheen, 1.0));
			matrices.pop();
		}
		double segs = RukiaBankaiClient.ribbonSegments(id);
		if (segs > 0.01 && FxMeshes.available()) {
			ribbons(matrices, vcp, light, entity, segs, tickDelta);
		}
	}

	private void ribbons(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity entity, double segs, float td) {
		int id = entity.getId();
		int maxSeg = FxConfig.fxTier == FxConfig.TierSetting.LOW ? 6 : 9;
		double now = FxClock.now;
		matrices.push();
		// back to the entity frame (feet origin, +z forward, body yaw applied): the inverse of the renderer's scale(-1,-1,1) and translate(0,-1.501,0)
		matrices.translate(0f, 1.501f, 0f);
		matrices.scale(-1f, -1f, 1f);
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		FxMeshes.Mesh seg = FxMeshes.get(FxMeshes.RIBBON_SEG);
		FxMeshes.Mesh tip = FxMeshes.get(FxMeshes.RIBBON_TIP);
		Matrix4f m = new Matrix4f();
		Vector3f p = new Vector3f();
		Vector3f d = new Vector3f();
		Vector3f xAxis = new Vector3f();
		Vector3f zAxis = new Vector3f();
		// pass 1: diffuse; pass 2: the emissive edge. The geometry is built twice from the same function (cheap: 3 x 10 small meshes).
		for (int pass = 0; pass < 2; pass++) {
			VertexConsumer vc = pass == 0 ? vcp.getBuffer(RenderLayer.getEntityTranslucent(FxMeshes.DIFFUSE)) : vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(FxMeshes.EMISSIVE));
			for (int k = 0; k < 3; k++) {
				double side = k - 1;
				p.set((float) (side * 0.10), 0.90f, -0.14f);
				double theta = Math.toRadians(FxTune.d("rbankai.ribbonStart", 22));
				double curve = Math.toRadians(FxTune.d("rbankai.ribbonCurve", 18));
				int count = (int) Math.min(maxSeg + 1, Math.ceil(segs));
				for (int j = 0; j < count; j++) {
					boolean isTip = j >= maxSeg;
					// segment direction: up and back, outward by the chain side, S-loop curvature, sway, spring flick of a new segment
					double sway = Math.toRadians(6.0) * Math.sin(2 * Math.PI * 0.35 * now - 0.6 * j + k);
					double tau = RukiaBankaiClient.segmentAge(id, j);
					double flick = tau < 0 ? 0 : Math.toRadians(15) * Math.exp(-tau / 0.2) * Math.cos(2 * Math.PI * tau / 0.28);
					double th = theta + sway + flick;
					double out = side * (0.35 + 0.05 * j);
					d.set((float) (out * Math.sin(th)), (float) Math.cos(th), (float) -Math.sin(th)).normalize();
					// orientation: mesh -Y along the direction, width axis along the lateral axis
					xAxis.set(1f, 0f, 0f).fma(-d.x, d).normalize();
					zAxis.set(xAxis).cross(new Vector3f(d).negate());
					Matrix3f rot = new Matrix3f(xAxis.x, xAxis.y, xAxis.z, -d.x, -d.y, -d.z, zAxis.x, zAxis.y, zAxis.z);
					double grow = j + 1 <= segs ? 1.0 : segs - j;
					m.set(base).translate(p).rotate(rot.getNormalizedRotation(new org.joml.Quaternionf())).scale(1f, (float) grow, 1f);
					if (pass == 0) {
						FxMeshes.draw(vc, isTip ? tip : seg, m, FxMeshes.argb(1, 1, 1, 0.95), light);
					} else {
						FxMeshes.draw(vc, isTip ? tip : seg, m, FxMeshes.argb(0.75, 0.9, 1.0, 0.42 * FxConfig.glowIntensity), 0xF000F0);
					}
					p.fma((float) (0.35 * grow), d);
					theta += j < 5 ? curve : -curve;
				}
				lastSegments = count;
			}
		}
		matrices.pop();
	}
}
