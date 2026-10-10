package dev.minebleach.reiatsutest.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.mixin.PostEffectProcessorAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * The one post pass of the mod (1.3.3, 1.4 {@code desat}): {@code freeze_desat} turns the colour of the world down to grey with a
 * cool tint while Rukia's absolute zero holds time still. {@link #set} ramps the amount (linear in time), {@link #render} runs the
 * {@link PostEffectProcessor} at {@code WorldRenderEvents.LAST} (after the world, before the hand and the HUD, so those stay in
 * colour) and sets the {@code Amount} uniform each frame through {@link PostEffectProcessorAccessor}. The program sits under
 * {@code assets/minecraft/shaders/program/reiatsu_test_freeze_desat.*} because post programs cannot be namespaced (LOG, phase 6
 * step 0). If the processor cannot be built the fallback of ADR R2.3 is a grey screen quad (a blend toward grey, not a true
 * desaturation) and {@link #usingFallback} is true.
 */
public final class FreezeDesat {
	private static PostEffectProcessor processor;
	private static boolean failed;
	private static int width;
	private static int height;
	private static double level;
	private static double target;
	private static double speed = 1.0;
	public static boolean usingFallback;
	/** Harness: frames in which the post pass (or the fallback) really ran. */
	public static long passFrames;

	private FreezeDesat() {
	}

	/** Ramps the amount to {@code to} (0..1) in {@code seconds}. */
	public static void set(double to, double seconds) {
		target = FxMath.clamp(to);
		speed = Math.abs(target - level) / Math.max(0.01, seconds);
	}

	public static double level() {
		return level;
	}

	public static void clear() {
		level = 0;
		target = 0;
	}

	public static boolean active() {
		return level > 0.004 || target > 0.004;
	}

	/** Dev: forces the fallback (harness check of the grey quad). */
	public static volatile boolean forceFallback;

	/** WorldRenderEvents.LAST, before the glow batch. */
	public static void render(WorldRenderContext ctx) {
		double dt = FxClock.dt;
		if (level < target) {
			level = Math.min(target, level + speed * dt);
		} else if (level > target) {
			level = Math.max(target, level - speed * dt);
		}
		if (level < 0.004) {
			return;
		}
		double amount = level * FxConfig.gradeStrength;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!forceFallback && !failed && ensureProcessor(mc)) {
			try {
				for (PostEffectPass p : ((PostEffectProcessorAccessor) processor).reiatsu$passes()) {
					GlUniform u = p.getProgram().getUniformByName("Amount");
					if (u != null) {
						u.set((float) amount);
					}
				}
				FxDepth.stash();
				processor.render(0.0f);
				mc.getFramebuffer().beginWrite(false);
				FxDepth.unstash();
				usingFallback = false;
				passFrames++;
				return;
			} catch (RuntimeException e) {
				ReiatsuTest.LOGGER.error("[fx] freeze_desat post pass failed, using the grey quad fallback", e);
				failed = true;
			}
		}
		usingFallback = true;
		passFrames++;
		greyQuad((float) (amount * 0.55));
	}

	private static boolean ensureProcessor(MinecraftClient mc) {
		int w = mc.getWindow().getFramebufferWidth();
		int h = mc.getWindow().getFramebufferHeight();
		if (processor == null) {
			try {
				processor = new PostEffectProcessor(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), ReiatsuTest.id("shaders/post/freeze_desat.json"));
				processor.setupDimensions(w, h);
				width = w;
				height = h;
				ReiatsuTest.LOGGER.info("[fx] freeze_desat post effect loaded ({}x{})", w, h);
			} catch (Exception e) {
				ReiatsuTest.LOGGER.error("[fx] cannot load shaders/post/freeze_desat.json, using the grey quad fallback", e);
				failed = true;
				processor = null;
				return false;
			}
		} else if (w != width || h != height) {
			processor.setupDimensions(w, h);
			width = w;
			height = h;
		}
		return true;
	}

	/** Fallback: a blue grey blend over the world (identity matrices, no depth), alpha = strength. */
	private static void greyQuad(float alpha) {
		Matrix4fStack mv = RenderSystem.getModelViewStack();
		Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
		var sorter = RenderSystem.getVertexSorting();
		mv.pushMatrix();
		mv.identity();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.setProjectionMatrix(new Matrix4f(), sorter);
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		float r = 0.62f;
		float g = 0.67f;
		float bl = 0.74f;
		b.vertex(-1f, -1f, 0f).color(r, g, bl, alpha);
		b.vertex(1f, -1f, 0f).color(r, g, bl, alpha);
		b.vertex(1f, 1f, 0f).color(r, g, bl, alpha);
		b.vertex(-1f, 1f, 0f).color(r, g, bl, alpha);
		BufferRenderer.drawWithGlobalProgram(b.end());
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.setProjectionMatrix(proj, sorter);
		mv.popMatrix();
		RenderSystem.applyModelViewMatrix();
	}
}
