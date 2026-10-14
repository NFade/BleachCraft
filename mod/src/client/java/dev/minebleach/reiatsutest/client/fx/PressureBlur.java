package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.mixin.PostEffectProcessorAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;

/**
 * R11 T7: the blur and the horizontal tear of the reiatsu pressure, one post pass ({@code assets/reiatsu_test/shaders/post/pressure_blur.json},
 * program {@code minecraft:shaders/program/reiatsu_test_pressure_blur}) run at {@code WorldRenderEvents.LAST} with the same machinery as
 * {@link FreezeDesat} (the processor, the per frame uniforms through {@link PostEffectProcessorAccessor}, the depth stash). The radius (pixels), the
 * tear (pixels) and the tear phase come from {@link PressureFx#blurPx()}, {@link PressureFx#tearPx()} and {@link PressureFx#tearPhase()}. If the shader
 * cannot be loaded or throws while drawing, the pass switches itself off for the rest of the session with one log line and the rest of the pressure
 * (vignette, grade, shake, stripes) goes on; there is no fallback picture.
 */
public final class PressureBlur {
	private static PostEffectProcessor processor;
	private static boolean failed;
	private static int width;
	private static int height;
	/** Frames in which the pass ran (harness / dev command statistics). */
	public static long passFrames;

	private PressureBlur() {
	}

	/** True when the shader failed once and the pass is off for this session. */
	public static boolean failed() {
		return failed;
	}

	/** WorldRenderEvents.LAST, after {@link FreezeDesat#render}. */
	public static void render(WorldRenderContext ctx) {
		if (failed) {
			return;
		}
		double radius = PressureFx.blurPx();
		double tear = PressureFx.tearPx();
		if (radius < 0.05 && tear < 0.05) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!ensureProcessor(mc)) {
			return;
		}
		try {
			for (PostEffectPass p : ((PostEffectProcessorAccessor) processor).reiatsu$passes()) {
				set(p, "Radius", (float) radius);
				set(p, "Tear", (float) tear);
				set(p, "Phase", (float) PressureFx.tearPhase());
			}
			FxDepth.stash();
			processor.render(0.0f);
			mc.getFramebuffer().beginWrite(false);
			FxDepth.unstash();
			passFrames++;
		} catch (RuntimeException e) {
			ReiatsuTest.LOGGER.error("[fx] pressure_blur post pass failed, the blur and the tear are off for this session", e);
			failed = true;
		}
	}

	private static void set(PostEffectPass p, String name, float value) {
		GlUniform u = p.getProgram().getUniformByName(name);
		if (u != null) {
			u.set(value);
		}
	}

	private static boolean ensureProcessor(MinecraftClient mc) {
		int w = mc.getWindow().getFramebufferWidth();
		int h = mc.getWindow().getFramebufferHeight();
		if (processor == null) {
			try {
				processor = new PostEffectProcessor(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), ReiatsuTest.id("shaders/post/pressure_blur.json"));
				processor.setupDimensions(w, h);
				width = w;
				height = h;
				ReiatsuTest.LOGGER.info("[fx] pressure_blur post effect loaded ({}x{})", w, h);
			} catch (Exception e) {
				ReiatsuTest.LOGGER.error("[fx] cannot load shaders/post/pressure_blur.json, the blur and the tear are off for this session", e);
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
}
