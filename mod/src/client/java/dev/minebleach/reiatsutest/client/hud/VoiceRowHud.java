package dev.minebleach.reiatsutest.client.hud;

import static dev.minebleach.reiatsutest.client.hud.HudAssets.*;

import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxMath;
import dev.minebleach.reiatsutest.voice.VoiceHudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The voice indicator (VFX_STORYBOARD 7.8, S6; v4 2026-10-11): microphone icon, the last heard phrase with its result, and the
 * phrase toast. Drawn by {@link ReiatsuHud} independent of the spirit plate, so it is on screen in every state, also SEALED with
 * an empty hand, whenever the voice bridge runs in this game; dim with a crossed microphone while no page is connected
 * (the page polls /status every 2.5 s, six seconds without contact count as "not connected").
 */
final class VoiceRowHud {
	/** After this long the last phrase is shown dimmer (it stays until the next one). */
	private static final double FRESH_S = 8.0;

	private VoiceRowHud() {
	}

	static void draw(HudGfx g, MinecraftClient mc, TextRenderer font, int w, int x0, int y0, float[] accent) {
		if (!FxConfig.voiceHud) {
			return;
		}
		VoiceHudState v = VoiceHudState.get();
		if (!v.indicatorWanted()) {
			return; // no bridge in this JVM (dedicated server, voice disabled) and no page ever in touch
		}
		double now = HudModel.now;
		long wall = System.currentTimeMillis();
		VoiceHudState.Mic mic = v.effectiveMic(wall);
		boolean connected = mic != VoiceHudState.Mic.OFF;
		float alpha = connected ? 1f : 0.55f;
		int mx = x0 + 9;
		int my = y0 + 38;
		int frame;
		float[] tint;
		switch (mic) {
			case OFF -> {
				frame = 0;
				tint = DIM;
			}
			case IDLE -> {
				frame = 1;
				tint = STEEL;
			}
			case LISTENING -> {
				frame = 2;
				tint = STEEL;
			}
			case HEARING -> {
				frame = 3 + (int) ((now * 6) % 3);
				tint = accent;
			}
			default -> {
				frame = 6;
				tint = ERROR;
			}
		}
		g.sprite(VOICE_MIC, 84, 12, mx, my, frame * 12, 0, 12, 12, tint, alpha);
		if (mic == VoiceHudState.Mic.LISTENING) {
			float pulse = (float) (0.55 + 0.45 * Math.sin(2 * Math.PI * 0.55 * now));
			g.fill(mx + 9, my + 1, mx + 11, my + 3, HudGfx.argb(OK, pulse * alpha));
		}
		if (!drawToast(g, font, w, x0, y0, accent, now)) {
			drawStatusLine(g, font, w, mx + 16, my + (PlateHud.rageVisible() ? 16 : 2), v, mic, wall);
		}
	}

	/** The one line next to the icon: the last phrase with its result, or what the microphone is doing. */
	private static void drawStatusLine(HudGfx g, TextRenderer font, int w, int x, int y, VoiceHudState v, VoiceHudState.Mic mic, long wall) {
		String text;
		float[] color;
		float a = 0.9f;
		if (mic == VoiceHudState.Mic.OFF) {
			text = HudText.tr("hud.reiatsu_test.voice.state.off");
			color = DIM;
			a = 0.6f;
		} else if (mic == VoiceHudState.Mic.ERROR) {
			text = HudText.tr("hud.reiatsu_test.voice.state.error");
			color = ERROR;
		} else if (!v.finalText().isEmpty() && v.atMs() > 0) {
			String mark;
			switch (v.result()) {
				case ACCEPTED -> {
					mark = "✓ ";
					color = OK;
				}
				case NO_MATCH, GATED, NONE -> {
					mark = "- ";
					color = DIM;
				}
				default -> {
					mark = "✗ ";
					color = ERROR;
				}
			}
			text = mark + "“" + v.finalText() + "”";
			a = (wall - v.atMs()) / 1000.0 < FRESH_S ? 0.95f : 0.65f;
		} else {
			String key = switch (mic) {
				case HEARING -> "hud.reiatsu_test.voice.state.hearing";
				case LISTENING -> "hud.reiatsu_test.voice.state.listening";
				default -> "hud.reiatsu_test.voice.state.idle";
			};
			text = HudText.tr(key);
			color = DIM;
		}
		int maxW = Math.max(40, Math.min(150, w - x - 8));
		if (font.getWidth(text) > maxW) {
			text = font.trimToWidth(text, maxW - font.getWidth("...")) + "...";
		}
		g.text(font, text, x, y, (HudGfx.argb(color, 1f) & 0x00FFFFFF) | PlateHud.alphaInt(a) << 24, true);
	}

	/** The phrase toast of the earlier versions (unchanged): interim or final text, then the verdict. Returns true while it is on screen. */
	private static boolean drawToast(HudGfx g, TextRenderer font, int w, int x0, int y0, float[] accent, double now) {
		HudModel.VoiceToast t = HudModel.VOICE;
		double age = now - t.lastTouch;
		if (t.lastTouch < 0 || age > 2.5 || (t.line1.isEmpty())) {
			return false;
		}
		double inP = FxMath.oc((now - t.start) / 0.15);
		float fade = (float) Math.min(1.0, (2.5 - age) / 0.4);
		float ta = fade;
		int maxW = Math.min(220, w - 40);
		String l1 = t.interim ? t.line1 + "..." : "“" + t.line1 + "”";
		if (t.line1.length() > 40) {
			l1 = t.interim ? "..." + t.line1.substring(t.line1.length() - 38) + "..." : "“" + t.line1.substring(0, 38) + "...”";
		}
		boolean two = !t.interim && !t.line2.isEmpty();
		int tw = Math.max(font.getWidth(l1), two ? font.getWidth(t.line2) : 0);
		tw = Math.min(tw, maxW - 10);
		int pw = tw + 10;
		int ph = two ? 25 : 16;
		float off = FxConfig.reduceMotion ? 0 : (float) ((1 - inP) * -12);
		float px = x0 + 24 + off;
		float py = y0 + 36;
		float[] panelTint = WHITE;
		if (t.accepted && t.bankai && now - t.start < 0.3 && now - t.start >= 0 || (t.bankai && now - t.lastTouch < 0.3)) {
			panelTint = FxMath.mix(WHITE, accent, 0.6);
		}
		g.nineSlice(TOAST_SPRITE, Math.round(px), Math.round(py), pw, ph, panelTint, ta);
		int ia = PlateHud.alphaInt(ta);
		String line1 = font.getWidth(l1) > tw ? font.trimToWidth(l1, tw) : l1;
		Text l1t = Text.literal(line1).formatted(Formatting.ITALIC);
		g.text(font, l1t, px + 5, py + 4, (t.interim ? 0x008A93A6 : 0x00E8ECF4) | ia << 24, false);
		if (two) {
			String line2 = font.getWidth(t.line2) > tw ? font.trimToWidth(t.line2, tw) : t.line2;
			String mark = t.accepted ? "✓ " : t.line2Tint == DIM ? "- " : "✗ ";
			g.text(font, mark + line2, px + 5, py + 14, (HudGfx.argb(t.line2Tint, 1f) & 0x00FFFFFF) | ia << 24, false);
		}
		return true;
	}
}
