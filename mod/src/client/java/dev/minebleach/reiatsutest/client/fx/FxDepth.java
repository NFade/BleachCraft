package dev.minebleach.reiatsutest.client.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

/**
 * Depth of the opaque world for the glow batch under Fabulous graphics. Measured (LOG, phase 6 step 0): at
 * {@code WorldRenderEvents.LAST} the main depth buffer is 1.0 everywhere in Fabulous mode, because the transparency
 * post chain clears the main target before the blit. The fix: copy the depth once the opaque pass is done
 * ({@code BEFORE_DEBUG_RENDER}, after the solid entity layers were flushed and before the translucent / particle passes)
 * into a private depth framebuffer and copy it back into the main target right before the glow batch is drawn. Fancy and
 * Fast need nothing (the main depth is valid at LAST).
 */
public final class FxDepth {
	private static Framebuffer copy;
	private static boolean captured;

	private FxDepth() {
	}

	private static boolean needed() {
		return MinecraftClient.isFabulousGraphicsOrBetter();
	}

	/** BEFORE_DEBUG_RENDER. */
	public static void capture() {
		captured = false;
		if (!needed() || FxGlowBatch.live() <= 0) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		Framebuffer main = mc.getFramebuffer();
		if (copy == null) {
			copy = new SimpleFramebuffer(main.textureWidth, main.textureHeight, true, MinecraftClient.IS_SYSTEM_MAC);
		} else if (copy.textureWidth != main.textureWidth || copy.textureHeight != main.textureHeight) {
			copy.resize(main.textureWidth, main.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
		}
		copy.copyDepthFrom(main);
		main.beginWrite(false);
		captured = true;
	}

	/** Just before the glow batch is drawn at LAST: puts the captured depth back into the main target. */
	public static void restore() {
		if (!captured || copy == null) {
			return;
		}
		Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
		main.copyDepthFrom(copy);
		main.beginWrite(false);
		captured = false;
	}
}
