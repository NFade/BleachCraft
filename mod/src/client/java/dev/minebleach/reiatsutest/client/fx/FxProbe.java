package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import java.nio.FloatBuffer;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/** Dev diagnostics: GL state at WorldRenderEvents.LAST (depth test of the glow batch, step 0 open point 1). */
public final class FxProbe {
	public static volatile boolean armed;

	private FxProbe() {
	}

	public static void run(String tag) {
		if (!armed) {
			return;
		}
		armed = false;
		MinecraftClient mc = MinecraftClient.getInstance();
		int fb = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		int mainFb = mc.getFramebuffer().fbo;
		boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		int func = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		boolean mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		int w = mc.getFramebuffer().textureWidth;
		int h = mc.getFramebuffer().textureHeight;
		FloatBuffer buf = MemoryUtil.memAllocFloat(1);
		StringBuilder sb = new StringBuilder();
		int[][] pts = {{w / 2, h / 2}, {w / 2, h / 4}, {10, h / 2}, {w / 2, h - 10}};
		for (int[] p : pts) {
			GL11.glReadPixels(p[0], p[1], 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, buf);
			sb.append(String.format(" (%d,%d)=%.5f", p[0], p[1], buf.get(0)));
		}
		MemoryUtil.memFree(buf);
		ReiatsuTest.LOGGER.info("[fxprobe] {} drawFB={} mainFB={} depthTest={} func=0x{} mask={} depth px:{} fabulous={}", tag, fb, mainFb, depthTest,
				Integer.toHexString(func), mask, sb, MinecraftClient.isFabulousGraphicsOrBetter());
	}
}
