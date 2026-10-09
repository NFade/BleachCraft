package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.entity.FxAnchorEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Renderer of {@code reiatsu_test:fx_anchor}. Step 0 scaffolding: the anchor draws nothing yet; the batched mesh renderers
 * (petal swarm, blade rows, crystals, wings) plug in here per {@code kind} in the later steps.
 */
public final class FxAnchorRenderer extends EntityRenderer<FxAnchorEntity> {
	private static final Identifier NONE = ReiatsuTest.id("textures/fx/fx_glow.png");

	public FxAnchorRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public Identifier getTexture(FxAnchorEntity entity) {
		return NONE;
	}

	@Override
	public boolean shouldRender(FxAnchorEntity entity, Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(FxAnchorEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		FxAnchors.seen(entity);
	}
}
