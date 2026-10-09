package dev.minebleach.reiatsutest.client.dev;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.PointerBuffer;

/**
 * Dev-only: moves the game window onto the monitor that contains the point given by
 * {@code -Dreiatsu.devMonitor=x,y} (virtual desktop coordinates) and centres it there.
 * Set by the Gradle run configs; absent in normal runs, so this does nothing in production.
 */
public final class DevWindowPlacement {
	private static final String PROPERTY = "reiatsu.devMonitor";

	private DevWindowPlacement() {
	}

	public static void init() {
		String spec = System.getProperty(PROPERTY);
		if (spec == null || spec.isBlank()) {
			return;
		}
		String[] parts = spec.split(",");
		int px;
		int py;
		try {
			px = Integer.parseInt(parts[0].trim());
			py = Integer.parseInt(parts[1].trim());
		} catch (RuntimeException e) {
			System.err.println("[reiatsu_test] bad -D" + PROPERTY + "=" + spec + " (expected x,y)");
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.getWindow() != null) {
			move(client.getWindow(), px, py);
		} else {
			ClientLifecycleEvents.CLIENT_STARTED.register(c -> move(c.getWindow(), px, py));
		}
	}

	private static void move(Window window, int px, int py) {
		if (window.isFullscreen()) {
			return;
		}
		PointerBuffer monitors = GLFW.glfwGetMonitors();
		if (monitors == null) {
			return;
		}
		int[] mx = new int[1];
		int[] my = new int[1];
		for (int i = 0; i < monitors.limit(); i++) {
			long monitor = monitors.get(i);
			GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
			if (mode == null) {
				continue;
			}
			GLFW.glfwGetMonitorPos(monitor, mx, my);
			if (px >= mx[0] && px < mx[0] + mode.width() && py >= my[0] && py < my[0] + mode.height()) {
				int[] w = new int[1];
				int[] h = new int[1];
				GLFW.glfwGetWindowSize(window.getHandle(), w, h);
				int x = mx[0] + Math.max(0, (mode.width() - w[0]) / 2);
				int y = my[0] + Math.max(32, (mode.height() - h[0]) / 2);
				GLFW.glfwSetWindowPos(window.getHandle(), x, y);
				System.out.println("[reiatsu_test] dev window moved to monitor at " + mx[0] + "," + my[0]);
				return;
			}
		}
		System.err.println("[reiatsu_test] no monitor contains " + px + "," + py + "; window not moved");
	}
}
