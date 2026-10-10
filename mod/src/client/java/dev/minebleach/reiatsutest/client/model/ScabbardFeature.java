package dev.minebleach.reiatsutest.client.model;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ArmorStandEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

/** Third person scabbard on the hip of players and armor stands (feature renderer, no item involved). */
public final class ScabbardFeature<T extends LivingEntity, M extends BipedEntityModel<T>> extends FeatureRenderer<T, M> {
	public ScabbardFeature(FeatureRendererContext<T, M> context) {
		super(context);
	}

	public static void register() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			if (renderer instanceof PlayerEntityRenderer pr) {
				helper.register(new ScabbardFeature<>(pr));
			} else if (renderer instanceof ArmorStandEntityRenderer ar) {
				helper.register(new ScabbardFeature<>(ar));
			}
		});
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T entity, float limbAngle,
			float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		ScabbardRenderer.renderHip(matrices, vertexConsumers, light, entity, getContextModel());
	}
}
