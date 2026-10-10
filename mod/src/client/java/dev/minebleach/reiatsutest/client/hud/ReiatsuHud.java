package dev.minebleach.reiatsutest.client.hud;

import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.mixin.BossBarHudAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * The reiatsu HUD (VFX_STORYBOARD section 7): spirit plate with emblem, timer ring and reiatsu bar, ability strip beside
 * the hotbar, crosshair pips, denied feedback, title cards, voice indicator. Drawn in {@code HudRenderCallback}; the old
 * simple HUD ({@link LegacyHud}) is the fallback when {@code hud} is false in the client config. Hidden with F1.
 */
public final class ReiatsuHud {
	/** Dev only: the tune harness hides the mod HUD so in-hand screenshots are clean (vanilla F1 would hide the hand too). */
	public static volatile boolean devHidden;
	private static final HudGfx GFX = new HudGfx();
	/** Statistics for the harness: milliseconds spent in the last render and the running mean. */
	public static double lastMs;
	public static double sumMs;
	public static long frames;
	public static int lastDrawCalls;
	/** Section sums for the harness (plate, strip, overlays) in milliseconds. */
	public static double plateMs;
	public static double stripMs;
	public static double overlayMs;
	public static String profile() {
		return String.format(java.util.Locale.ROOT, "emblem %.3f bar %.3f text %.3f ms", PlateHud.PROF[0] / 1e6 / Math.max(1, frames), PlateHud.PROF[1] / 1e6 / Math.max(1, frames), PlateHud.PROF[2] / 1e6 / Math.max(1, frames));
	}

	private ReiatsuHud() {
	}

	public static void init() {
		HudRenderCallback.EVENT.register(ReiatsuHud::render);
	}

	public static void resetStats() {
		sumMs = 0;
		frames = 0;
		plateMs = stripMs = overlayMs = 0;
		java.util.Arrays.fill(PlateHud.PROF, 0);
	}

	public static double meanMs() {
		return frames == 0 ? 0 : sumMs / frames;
	}

	public static void flashBar() {
		LegacyHud.flashBar();
	}

	public static void shakeCooldowns() {
		LegacyHud.shakeCooldowns();
	}

	/** Layout of the plate for the window: origin and compact mode (7.9). */
	public static int[] plateOrigin(MinecraftClient mc, int w) {
		int y0 = 6;
		int x0 = 6;
		boolean compact = FxConfig.hudCompact || w < 360;
		int right = x0 + 30 + (compact ? 96 : 128) + 4;
		if (right > w / 2 - 91) {
			int bars = ((BossBarHudAccessor) mc.inGameHud.getBossBarHud()).reiatsu$bossBars().size();
			y0 += 19 * bars;
		}
		return new int[] {x0, y0, compact ? 1 : 0};
	}

	private static void render(DrawContext ctx, RenderTickCounter tick) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden || devHidden) {
			return;
		}
		if (!FxConfig.hud) {
			LegacyHud.render(ctx, tick);
			return;
		}
		long t0 = System.nanoTime();
		float tickDelta = tick.getTickDelta(false);
		HudModel.update(mc, tickDelta);
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		int[] o = plateOrigin(mc, w);
		var font = mc.textRenderer;
		GFX.begin(ctx);
		long a0 = System.nanoTime();
		PlateHud.draw(GFX, mc, font, w, h, o[0], o[1], o[2] == 1, tickDelta);
		GFX.flush();
		long a1 = System.nanoTime();
		StripHud.draw(GFX, mc, font, w, h);
		GFX.flush();
		long a2 = System.nanoTime();
		OverlayHud.drawToasts(GFX, font, w, h, o[0], o[1]);
		OverlayHud.drawCard(GFX, font, w, h);
		GFX.end();
		long a3 = System.nanoTime();
		plateMs += (a1 - a0) / 1.0e6;
		stripMs += (a2 - a1) / 1.0e6;
		overlayMs += (a3 - a2) / 1.0e6;
		double ms = (System.nanoTime() - t0) / 1.0e6;
		lastMs = ms;
		sumMs += ms;
		frames++;
		lastDrawCalls = GFX.drawCalls;
	}
}
