package dev.minebleach.reiatsutest.client.hud;

import static dev.minebleach.reiatsutest.client.hud.HudAssets.*;

import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxMath;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/** Title cards, technique toast and the denied / info toasts (VFX_STORYBOARD 7.6, 7.7). */
final class OverlayHud {
	private static final float[] LETTERBOX = FxMath.hex("#05070D");
	private OverlayHud() {
	}

	private static float[] cardAccent(CharacterId c, boolean bankai) {
		return HudAssets.accent(c, bankai ? dev.minebleach.reiatsutest.core.state.ZanpakutoState.BANKAI : dev.minebleach.reiatsutest.core.state.ZanpakutoState.SHIKAI);
	}

	static void drawToasts(HudGfx g, TextRenderer font, int w, int h, int x0, int y0) {
		HudModel.Toast t = HudModel.toast;
		if (t != null) {
			double age = HudModel.now - t.start;
			float a = (float) Math.min(FxMath.clamp(age / 0.12), FxMath.clamp((t.life - age) / 0.3));
			String s = t.text.getString();
			int tw = font.getWidth(s);
			int pw = tw + 14;
			int ph = 15;
			int px;
			int py;
			if (t.kind == HudModel.ToastKind.DENIED) {
				px = x0;
				py = y0 + 54;
			} else {
				px = (w - pw) / 2;
				py = h / 2 + 30;
			}
			float rise = FxConfig.reduceMotion ? 0 : (float) ((1 - FxMath.clamp(age / 0.12)) * 4);
			g.nineSlice(TOAST_SPRITE, px, Math.round(py + rise), pw, ph, WHITE, a);
			int ia = PlateHud.alphaInt(a);
			g.text(font, s, px + 7, py + 4 + rise, (HudGfx.argb(t.tint, 1f) & 0x00FFFFFF) | ia << 24, false);
		}
		HudModel.Technique q = HudModel.technique;
		if (q != null) {
			double age = HudModel.now - q.start;
			float in = (float) FxMath.clamp(age / 0.12);
			float a = Math.min(in, (float) FxMath.clamp((1.4 - age) / 0.3));
			String s = q.text.getString();
			int tw = font.getWidth(s);
			float[] accent = cardAccent(q.character, false);
			float rise = FxConfig.reduceMotion ? 0 : (1 - in) * 4;
			int cx = w / 2;
			int y = h / 2 + 30;
			int ia = PlateHud.alphaInt(a);
			g.text(font, s, cx - tw / 2f, y + rise, (HudGfx.argb(accent, 1f) & 0x00FFFFFF) | ia << 24, true);
			double wipe = FxConfig.reduceMotion ? 1 : FxMath.oc(age / 0.15);
			int lw = Math.min(96, tw + 16);
			g.quad(TOAST_LINE, 96, 3, cx - lw / 2f, y + 11 + rise, (float) (lw * wipe), 3, 0, 0, (float) (96 * wipe), 3, accent[0], accent[1], accent[2], a * 0.95f,
					HudGfx.NORMAL);
		}
	}

	static void drawCard(HudGfx g, TextRenderer font, int w, int h) {
		HudModel.Card c = HudModel.card;
		if (c == null || !FxConfig.titleCards) {
			return;
		}
		g.layerBreak();
		if (c.bankai) {
			drawBankai(g, font, c, w, h);
		} else {
			drawShikai(g, font, c, w, h);
		}
	}

	private static void drawShikai(HudGfx g, TextRenderer font, HudModel.Card c, int w, int h) {
		double t = HudModel.now - c.start;
		boolean rm = FxConfig.reduceMotion;
		float[] accent = cardAccent(c.character, false);
		int bandW = Math.min(w - 16, 300);
		int bandX = (w - bandW) / 2;
		int yc = Math.round(0.28f * h);
		double wipe = rm ? FxMath.clamp(t / 0.1) : FxMath.oc(t / 0.18);
		double ex = t > 1.4 ? FxMath.clamp((t - 1.4) / 0.25) : 0;
		float fade = (float) (1 - ex);
		float dx = rm ? 0 : (float) (24 * FxMath.oc(ex));
		float vis = (float) (bandW * (rm ? 1 : wipe));
		float ba = 0.85f * fade * (float) (rm ? wipe : 1);
		float[] deep = FxMath.mix(accent, INK, 0.55);
		g.quad(TITLE_BAND, 300, 32, bandX + dx + 2, yc - 14, vis, 32, 0, 0, vis, 32, INK[0], INK[1], INK[2], ba * 0.55f, HudGfx.NORMAL);
		g.quad(TITLE_BAND, 300, 32, bandX + dx, yc - 16, vis, 32, 0, 0, vis, 32, deep[0], deep[1], deep[2], ba, HudGfx.NORMAL);
		int scale = w < 400 ? 1 : 2;
		String phrase = Text.translatable("hud.reiatsu_test.release." + (c.character == CharacterId.BYAKUYA ? "byakuya" : "rukia")).getString();
		double pt = FxMath.clamp((t - 0.08) / 0.2);
		float off = rm ? 0 : (float) ((1 - FxMath.oc(pt)) * 12);
		float pa = (float) pt * fade;
		int tw = font.getWidth(phrase) * scale;
		MatrixStack ms = g.ctx().getMatrices();
		int ia = PlateHud.alphaInt(pa);
		if (pa > 0.03f) {
			ms.push();
			ms.translate((w - tw) / 2f + off + dx, yc - (scale == 2 ? 5 : 2), 0);
			ms.scale(scale, scale, 1f);
			g.textAt(font, phrase, 0x00FFFFFF | ia << 24, true);
			ms.pop();
			// SHIKAI, letter spaced, accent, one size smaller than the phrase
			String label = dev.minebleach.reiatsutest.client.hud.PlateHud.stateWord(dev.minebleach.reiatsutest.core.state.ZanpakutoState.SHIKAI).getString().toUpperCase(java.util.Locale.ROOT);
			int lw = 0;
			for (char ch : label.toCharArray()) {
				lw += font.getWidth(String.valueOf(ch)) + 1;
			}
			lw -= 1;
			float lx = (w - lw) / 2f + off + dx;
			int ly = yc - 15;
			for (char ch : label.toCharArray()) {
				String s = String.valueOf(ch);
				g.text(font, s, lx, ly, (HudGfx.argb(accent, 1f) & 0x00FFFFFF) | ia << 24, true);
				lx += font.getWidth(s) + 1;
			}
		}
	}

	private static void drawBankai(HudGfx g, TextRenderer font, HudModel.Card c, int w, int h) {
		double t = HudModel.now - c.start;
		boolean rm = FxConfig.reduceMotion;
		float[] accent = cardAccent(c.character, true);
		int bh = Math.round(0.085f * h);
		double in = rm ? FxMath.clamp(t / 0.1) : FxMath.oc(t / 0.25);
		double ex = t > 2.45 ? FxMath.clamp((t - 2.45) / 0.35) : 0;
		float fade = (float) (1 - ex);
		double bars = in * (1 - FxMath.oc(ex));
		int cur = (int) Math.round(bh * bars);
		if (cur > 0) {
			int col = HudGfx.argb(LETTERBOX, 0.85f);
			g.fill(0, 0, w, cur, col);
			g.fill(0, h - cur, w, h, col);
			// thin accent line on the inner edge of the bars
			int lc = HudGfx.argb(accent, 0.55f * fade);
			g.fill(0, cur - 1, w, cur, lc);
			g.fill(0, h - cur, w, h - cur + 1, lc);
		}
		int ky = Math.round(0.27f * h);
		int ks = Math.min(6, Math.max(3, Math.round(h / 45f)));
		double kt = FxMath.clamp((t - 0.15) / 0.2);
		double pop = rm ? 1.0 : FxMath.lerp(1.6, 1.0, FxMath.ob(kt));
		float ka = (float) kt * fade;
		String kanji = "卍解";
		MatrixStack ms = g.ctx().getMatrices();
		if (ka > 0.02f) {
			float gw = 40f * ks * (float) pop;
			float gh = 20f * ks * (float) pop;
			// dark halo first (so the white glyphs read on a bright sky), then the accent glow
			g.quad(TITLE_GLOW, 192, 96, w / 2f - gw * 0.62f, ky - gh * 0.62f, gw * 1.24f, gh * 1.24f, 0, 0, 192, 96, INK[0], INK[1], INK[2], 0.62f * ka, HudGfx.NORMAL);
			g.quad(TITLE_GLOW, 192, 96, w / 2f - gw / 2, ky - gh / 2, gw, gh, 0, 0, 192, 96, accent[0], accent[1], accent[2], 0.38f * ka, HudGfx.ADD);
			int kw = font.getWidth(kanji);
			int ia = PlateHud.alphaInt(ka);
			ms.push();
			ms.translate(w / 2f, ky, 0);
			ms.scale((float) (ks * pop), (float) (ks * pop), 1f);
			ms.translate(-kw / 2f, -4, 0);
			// ink drop shadow, accent double strike, white face
			ms.push();
			ms.translate(0.45f, 0.45f, 0);
			g.textAt(font, kanji, 0x000B0F1A | PlateHud.alphaInt(ka * 0.8f) << 24, false);
			ms.pop();
			ms.push();
			ms.translate(0.22f, 0.1f, 0);
			g.textAt(font, kanji, (HudGfx.argb(accent, 1f) & 0x00FFFFFF) | ia << 24, false);
			ms.pop();
			g.textAt(font, kanji, 0x00FFFFFF | ia << 24, false);
			ms.pop();
		}
		// bankai name, revealed letter by letter, scale 2 with +1 px tracking
		String name = Text.translatable("hud.reiatsu_test.zanpakuto." + (c.character == CharacterId.BYAKUYA ? "byakuya" : "rukia") + ".bankai").getString()
				.toUpperCase(java.util.Locale.ROOT);
		int count = rm ? name.length() : (int) Math.max(0, Math.min(name.length(), Math.floor((t - 0.35) / 0.02)));
		float na = (float) (rm ? FxMath.clamp((t - 0.35) / 0.2) : 1.0) * fade;
		int nscale = w < 400 ? 1 : 2;
		int total = 0;
		for (char ch : name.toCharArray()) {
			total += (font.getWidth(String.valueOf(ch)) + 1) * nscale;
		}
		float nx = (w - total) / 2f;
		int ny = ky + ks * 4 + 8;
		int ia = PlateHud.alphaInt(na);
		if (na > 0.03f) {
			ms.push();
			ms.translate(nx, ny, 0);
			ms.scale(nscale, nscale, 1f);
			float cx = 0;
			for (int i = 0; i < count; i++) {
				String s = String.valueOf(name.charAt(i));
				ms.push();
				ms.translate(cx, 0, 0);
				g.textAt(font, s, 0x00FFFFFF | ia << 24, true);
				ms.pop();
				cx += font.getWidth(s) + 1;
			}
			ms.pop();
		}
		double tt = FxMath.clamp((t - 0.9) / 0.3);
		if (tt > 0.01) {
			String tr = Text.translatable("hud.reiatsu_test.translation." + (c.character == CharacterId.BYAKUYA ? "byakuya" : "rukia") + ".bankai").getString();
			int a2 = PlateHud.alphaInt((float) tt * fade);
			g.text(font, tr, (w - font.getWidth(tr)) / 2f, ny + 8 * nscale + 6, 0x00AEB9C9 | a2 << 24, true);
		}
	}
}
