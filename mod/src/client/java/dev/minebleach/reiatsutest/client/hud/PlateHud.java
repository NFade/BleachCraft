package dev.minebleach.reiatsutest.client.hud;

import static dev.minebleach.reiatsutest.client.hud.HudAssets.*;

import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxMath;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.voice.VoiceHudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** The spirit plate (VFX_STORYBOARD 7.2): emblem with timer ring, name line, reiatsu bar, value line, voice row. */
final class PlateHud {
	private static final int ICON_W = 16;
	private static final float[] GLOW_RUKIA = FxMath.hex("#9ED3F0");
	private static CharacterId lastChar = CharacterId.RUKIA;
	/** Dev profile (ns): emblem + rings, bar, texts. */
	static final long[] PROF = new long[3];

	private PlateHud() {
	}

	static int shift(int row) {
		return Math.round(3 * (6 - row) / 6.0f);
	}

	private static float iconBright(ZanpakutoState s) {
		return s == ZanpakutoState.SEALED ? 0.6f : s == ZanpakutoState.BASE ? 0.85f : 1.0f;
	}

	/** Draws the plate; returns the y below the plate (for the layout of what follows). */
	static void draw(HudGfx g, MinecraftClient mc, TextRenderer font, int w, int h, int x0, int y0, boolean compact, float tickDelta) {
		double now = HudModel.now;
		float alpha = (float) HudModel.plateAlpha;
		if (alpha <= 0.01f) {
			return;
		}
		boolean rm = FxConfig.reduceMotion;
		ZanpakutoState st = HudModel.state;
		CharacterId ch = HudModel.character != CharacterId.NONE ? HudModel.character : lastChar;
		if (HudModel.character != CharacterId.NONE) {
			lastChar = HudModel.character;
		}
		MatrixStack ms = g.ctx().getMatrices();
		ms.push();
		float slide = rm ? 0 : (1f - alpha) * -10f;
		ms.translate(slide, 0, 0);
		// plate kick on the shikai -> bankai transition (1.0 -> 1.06 -> 1.0, easeOutBack, 0.25 s)
		if (!rm && now - HudModel.bankaiStart < 0.25 && now >= HudModel.bankaiStart) {
			double k = (now - HudModel.bankaiStart) / 0.25;
			double s = 1.0 + 0.06 * Math.sin(Math.PI * FxMath.ob(k));
			ms.translate(x0 + 15, y0 + 15, 0);
			ms.scale((float) s, (float) s, 1f);
			ms.translate(-(x0 + 15), -(y0 + 15), 0);
		}

		float[] accent = HudModel.accentNow();
		int barW = compact ? 96 : 128;
		int barX = x0 + 30;
		int barY = y0 + 13;

		// soft backdrop
		g.sprite(PLATE_SHADOW, 192, 48, x0 - 6, y0 - 6, 0, 0, 192, 48, WHITE, alpha);

		long q0 = System.nanoTime();
		drawEmblem(g, x0, y0, ch, st, accent, alpha, tickDelta);
		drawRings(g, x0, y0, st, accent, alpha, tickDelta);
		long q1 = System.nanoTime();

		// bar shake on denied reiatsu
		int shake = 0;
		if (now < HudModel.barShakeUntil && !rm) {
			shake = Math.round((float) (2 * Math.sin(2 * Math.PI * 30 * now)));
		}
		drawBar(g, barX + shake, barY, barW, ch, st, accent, alpha, tickDelta);
		long q2 = System.nanoTime();
		if (!compact) {
			drawNameLine(g, font, x0 + 34, y0 + 2, ch, st, accent, alpha, barW);
		}
		drawValueLine(g, font, barX, y0 + 26, barW, ch, st, accent, alpha, tickDelta);
		long q3 = System.nanoTime();
		PROF[0] += q1 - q0;
		PROF[1] += q2 - q1;
		PROF[2] += q3 - q2;
		ms.pop();
		drawVoiceRow(g, mc, font, w, x0, y0, accent, alpha);
	}

	// ------------------------------------------------------------------------------------------ emblem

	private static void drawEmblem(HudGfx g, int x0, int y0, CharacterId ch, ZanpakutoState st, float[] accent, float alpha, float tickDelta) {
		double now = HudModel.now;
		MatrixStack ms = g.ctx().getMatrices();
		ms.push();
		boolean rm = FxConfig.reduceMotion;
		double flip = (now - HudModel.flipStart) / 0.3;
		ZanpakutoState showSt = st;
		if (flip >= 0 && flip < 1 && !rm) {
			float sx = (float) Math.max(0.04, Math.abs(Math.cos(Math.PI * flip)));
			ms.translate(x0 + 15, y0 + 15, 0);
			ms.scale(sx, 1f, 1f);
			ms.translate(-(x0 + 15), -(y0 + 15), 0);
			if (flip < 0.5) {
				showSt = HudModel.fromState;
			}
		}
		float blend = HudModel.rowBlend();
		float bright = (float) FxMath.lerp(iconBright(HudModel.fromState), iconBright(st), blend);
		float[] fill = emblemFill(ch);
		float fa = alpha * 0.96f;
		g.sprite(EMBLEM_FILL, 30, 30, x0, y0, 0, 0, 30, 30, new float[] {fill[0] * bright, fill[1] * bright, fill[2] * bright}, fa);
		int iconIdx = ch == CharacterId.BYAKUYA ? 1 : 0;
		g.sprite(EMBLEM_ICONS, 32, 16, x0 + 7, y0 + 7, iconIdx * 16, 0, ICON_W, 16, new float[] {bright, bright, bright}, alpha);
		int frame = showSt == ZanpakutoState.BANKAI ? 2 : (showSt == ZanpakutoState.SEALED || showSt == ZanpakutoState.BASE) && blend > 0.5f ? 0 : 1;
		float[] tint = frame == 0 ? WHITE : accent;
		g.sprite(EMBLEM_FRAME, 90, 30, x0, y0, frame * 30, 0, 30, 30, tint, alpha);
		// denied item: crossed sword glyph over the emblem for 0.6 s
		double ia = now - HudModel.iconFlash;
		if (ia >= 0 && ia < 0.6) {
			float fa2 = (float) (1.0 - ia / 0.6) * alpha;
			g.sprite(ABILITY_ICONS, 64, 48, x0 + 7, y0 + 7, 16 * 2, 32 - 32 + 32, 16, 16, new float[] {1f, 0.45f, 0.4f}, fa2);
		}
		ms.pop();
	}

	private static void drawRings(HudGfx g, int x0, int y0, ZanpakutoState st, float[] accent, float alpha, float tickDelta) {
		double now = HudModel.now;
		int rx = x0 - 4;
		int ry = y0 - 4;
		if (st == ZanpakutoState.BANKAI) {
			double rem = HudModel.bankaiRemaining(tickDelta);
			double frac = rem / (HudModel.CFG.bankaiCapTicks() / 20.0);
			double draw = FxMath.oc((now - HudModel.bankaiStart) / 0.5);
			if (HudModel.bankaiStart < 0 || FxConfig.reduceMotion) {
				draw = 1;
			}
			double vis = Math.min(1.0, frac) * draw;
			int f = (int) Math.round(36 * (1 - vis));
			if (f < 36) {
				f = Math.max(0, f);
				float[] c = accent;
				float a = alpha;
				if (rem <= 10) {
					c = WARN;
					if (!FxConfig.reduceMotion) {
						a *= (float) (0.65 + 0.35 * Math.sin(2 * Math.PI * 2 * now));
					}
				}
				ringWithBacking(g, rx, ry, f, c, a);
			}
		} else {
			double since = now - HudModel.endedAt;
			if (since >= 0 && since < 0.25) {
				int f = Math.min(3, (int) (since / 0.25 * 4));
				g.sprite(RING_SHATTER, 160, 40, rx - 1, ry - 1, f * 40, 0, 40, 40, WARN, alpha * (float) (1 - since / 0.25 * 0.6));
			}
			// release lock: a grey ring counts the lock down
			if (HudModel.lockStart >= 0) {
				double left = 1.0 - (now - HudModel.lockStart) / Math.max(0.01, HudModel.lockDur);
				if (left > 0 && (st == ZanpakutoState.SEALED || st == ZanpakutoState.BASE)) {
					int f = (int) Math.round(36 * (1 - FxMath.clamp(left)));
					if (f < 36) {
						ringWithBacking(g, rx, ry, f, STEEL, alpha * 0.8f);
					}
				}
			}
		}
	}

	/** The ring with a 1 px ink outline, so it reads on a bright sky. */
	private static void ringWithBacking(HudGfx g, int rx, int ry, int f, float[] c, float a) {
		float u = (f % 6) * 38;
		float v = (f / 6) * 38;
		float[] ink = INK;
		for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
			g.quad(TIMER_RING, 228, 228, rx + o[0], ry + o[1], 38, 38, u, v, 38, 38, ink[0], ink[1], ink[2], a * 0.7f, HudGfx.NORMAL);
		}
		g.quad(TIMER_RING, 228, 228, rx, ry, 38, 38, u, v, 38, 38, c[0], c[1], c[2], a, HudGfx.NORMAL);
	}

	// ------------------------------------------------------------------------------------------ name line

	static Text stateWord(ZanpakutoState s) {
		return Text.translatable("hud.reiatsu_test.state." + s.name().toLowerCase(java.util.Locale.ROOT));
	}

	private static String nameKey = "";
	private static Text nameText;

	private static void drawNameLine(HudGfx g, TextRenderer font, int x, int y, CharacterId ch, ZanpakutoState st, float[] accent, float alpha, int barW) {
		int accentRgb = HudGfx.argb(accent, 1f) & 0x00FFFFFF;
		String key = "hud.reiatsu_test.zanpakuto." + (ch == CharacterId.BYAKUYA ? "byakuya" : "rukia") + (st == ZanpakutoState.BANKAI ? ".bankai" : "");
		String ck = st + "|" + key + "|" + accentRgb + "|" + barW + "|" + HudText.generation();
		if (!ck.equals(nameKey)) {
			String word = HudText.tr("hud.reiatsu_test.state." + st.name().toLowerCase(java.util.Locale.ROOT)).toUpperCase(java.util.Locale.ROOT);
			String sep = " \u00b7 ";
			int used = font.getWidth(word) + font.getWidth(sep);
			String name = HudText.tr(key);
			int avail = barW + 40 - used;
			if (font.getWidth(name) > avail) {
				name = font.trimToWidth(name, Math.max(0, avail - font.getWidth("..."))) + "...";
			}
			nameText = Text.literal(word).styled(t -> t.withColor(accentRgb)).append(Text.literal(sep).styled(t -> t.withColor(0xAEB9C9)))
					.append(Text.literal(name).styled(t -> t.withColor(0xE8ECF4)));
			nameKey = ck;
		}
		g.text(font, nameText, x, y, 0x00FFFFFF | alphaInt(alpha) << 24, true);
	}

	static int alphaInt(float a) {
		return Math.max(8, Math.min(255, Math.round(a * 255)));
	}

	// ------------------------------------------------------------------------------------------ bar

	private static int fillLen(double v, int span) {
		return (int) Math.round(span * FxMath.clamp(v));
	}

	/** One row span of a 1 px high strip of the fill region (slanted like the bar). */
	private static void rowSpan(HudGfx g, net.minecraft.util.Identifier tex, int texW, int texH, int fx, int fy, int row, int xs, int xe,
			float u0, float v, float uScale, float[] c, float a, int blend) {
		if (xe <= xs) {
			return;
		}
		int sh = shift(row);
		float u = (xs) * uScale + u0;
		g.quad(tex, texW, texH, fx + sh + xs, fy + row, xe - xs, 1, u, v, (xe - xs) * uScale, 1, c[0], c[1], c[2], a, blend);
	}

	private static void drawBar(HudGfx g, int bx, int by, int barW, CharacterId ch, ZanpakutoState st, float[] accent, float alpha, float tickDelta) {
		double now = HudModel.now;
		int fx = bx + 2;
		int fy = by + 2;
		int span = (barW - 4) - 3; // 121 at full width
		float uScale = 124f / (barW - 4);
		boolean compact = barW < 128;
		double max = Math.max(1, HudModel.max);
		double v = HudModel.value / max;
		int len = fillLen(v, span);

		// frame (left and right caps when compact)
		if (!compact) {
			g.sprite(BAR_FRAME, 128, 11, bx, by, 0, 0, 128, 11, WHITE, alpha);
		} else {
			g.sprite(BAR_FRAME, 128, 11, bx, by, 0, 0, 48, 11, WHITE, alpha);
			g.sprite(BAR_FRAME, 128, 11, bx + 48, by, 80, 0, 48, 11, WHITE, alpha);
		}
		// outer glow (Rukia bankai)
		if (st == ZanpakutoState.BANKAI && ch == CharacterId.RUKIA) {
			g.fill(bx - 1, by + 1, bx, by + 10, HudGfx.argb(GLOW_RUKIA, 0.35f * alpha));
			g.fill(bx + barW, by + 1, bx + barW + 1, by + 10, HudGfx.argb(GLOW_RUKIA, 0.35f * alpha));
			g.fill(bx + 2, by - 1, bx + barW - 2, by, HudGfx.argb(GLOW_RUKIA, 0.35f * alpha));
			g.fill(bx + 2, by + 11, bx + barW - 2, by + 12, HudGfx.argb(GLOW_RUKIA, 0.35f * alpha));
		}
		// fill: cross fade of the two rows during a transition
		float blend = HudModel.rowBlend();
		int rowTo = HudModel.rowToIdx();
		int rowFrom = HudModel.rowFromIdx();
		for (int r = 0; r < 7; r++) {
			if (blend < 0.999f && rowFrom != rowTo) {
				rowSpan(g, BAR_FILL, 124, 35, fx, fy, r, 0, len, 0, rowFrom * 7 + r, uScale, WHITE, alpha, HudGfx.NORMAL);
			}
			rowSpan(g, BAR_FILL, 124, 35, fx, fy, r, 0, len, 0, rowTo * 7 + r, uScale, WHITE, alpha * (rowFrom != rowTo ? blend : 1f), HudGfx.NORMAL);
		}
		// overlay: frost crystals / petals scrolling (Rukia bankai 4 px/s, Byakuya bankai 6 px/s)
		boolean over = (st == ZanpakutoState.BANKAI) || (HudModel.fromState == ZanpakutoState.BANKAI && blend < 1f);
		if (over) {
			float ov = (float) (st == ZanpakutoState.BANKAI ? FxMath.clamp((now - HudModel.bankaiStart) / 0.4) : 1 - blend);
			if (HudModel.bankaiStart < 0) {
				ov = 1;
			}
			boolean petals = ch == CharacterId.BYAKUYA;
			float scroll = (float) ((now * (petals ? 6 : 4)) % 64);
			for (int r = 0; r < 7; r++) {
				rowSpan(g, BAR_OVERLAY, 64, 14, fx, fy, r, 0, len, scroll, (petals ? 7 : 0) + r, 1f, WHITE, alpha * ov * 0.9f, HudGfx.NORMAL);
			}
		}
		// sheen: 14 x 7 band crossing the fill every 2.4 s; a triple strength pass on the release
		if ((st == ZanpakutoState.SHIKAI || st == ZanpakutoState.BANKAI) && len > 0) {
			float sa = 0.5f;
			double tri = now - HudModel.sheenStart;
			float sx = (float) (-14 + 52 * (now % 2.4));
			sheenAt(g, fx, fy, len, sx, sa * alpha);
			if (tri >= 0 && tri < 0.6 && !FxConfig.reduceMotion) {
				for (int k = 0; k < 3; k++) {
					double tk = tri - k * 0.13;
					if (tk > 0 && tk < 0.45) {
						sheenAt(g, fx, fy, len, (float) (-14 + 280 * tk), 1.0f * alpha);
					}
				}
			}
		}
		// chip bar: the part that was just spent, white, holds 0.25 s then shrinks over 0.45 s
		if (HudModel.chipFrom >= 0 && HudModel.chipFrom > HudModel.value) {
			double cf = HudModel.chipFrom / max;
			double shown = cf;
			if (now > HudModel.chipHoldUntil) {
				double t = FxMath.oc((now - HudModel.chipHoldUntil) / 0.45);
				shown = FxMath.lerp(cf, v, t);
			}
			int clen = fillLen(shown, span);
			for (int r = 0; r < 7; r++) {
				int sh = shift(r);
				g.fill(fx + sh + len, fy + r, fx + sh + clen, fy + r + 1, HudGfx.argb(WHITE, 0.78f * alpha));
			}
		}
		// denied reiatsu: red hatch over the missing span
		if (now < HudModel.hatchUntil) {
			float ha = (float) Math.min(1.0, (HudModel.hatchUntil - now) / 0.3) * alpha;
			int a = fillLen(HudModel.hatchFrom / max, span);
			int b = fillLen(HudModel.hatchTo / max, span);
			for (int r = 0; r < 7; r++) {
				int sh = shift(r);
				if (b > a) {
					g.quad(HATCH, 4, 4, fx + sh + a, fy + r, b - a, 1, (a % 4), (r % 4), b - a, 1, 1f, 0.29f, 0.29f, 0.85f * ha, HudGfx.NORMAL);
				}
			}
		}
		// ticks
		int tickColor = HudGfx.argb(INK, 0.35f * alpha);
		for (int k = 1; k < 10; k++) {
			int tx = (int) Math.round(span * k / 10.0);
			g.fill(fx + shift(1) + tx, fy, fx + shift(1) + tx + 1, fy + 3, tickColor);
		}
		// release cost tick (15 percent), gold, only before shikai
		if (st == ZanpakutoState.SEALED || st == ZanpakutoState.BASE) {
			int tx = fillLen(HudModel.CFG.shikaiReleaseCost() / max, span);
			for (int r = 0; r < 7; r++) {
				g.fill(fx + shift(r) + tx, fy + r, fx + shift(r) + tx + 1, fy + r + 1, HudGfx.argb(GOLD, 0.95f * alpha));
			}
		}
		// ability cost markers under the bar (slot colours: accent, x0.8, x0.6)
		if (st == ZanpakutoState.SHIKAI || st == ZanpakutoState.BANKAI) {
			for (int i = 0; i < HudModel.slotCount; i++) {
				HudModel.Slot s = HudModel.SLOTS[i];
				if (s.state == HudModel.SlotState.DISABLED || s.costTenths <= 0) {
					continue; // free (bankai) abilities have no cost tick
				}
				float m = 1f - 0.2f * s.index;
				int tx = fillLen(s.costTenths / max, span);
				float[] c = {accent[0] * m, accent[1] * m, accent[2] * m};
				g.fill(fx + 3 + tx, by + 11, fx + 3 + tx + 1, by + 13, HudGfx.argb(c, 0.95f * alpha));
			}
		}
		// front cap while regenerating
		if (now < HudModel.regenUntil && len > 0 && len < span) {
			for (int r = 0; r < 7; r++) {
				int sh = shift(r);
				g.fill(fx + sh + len - 1, fy + r, fx + sh + len, fy + r + 1, HudGfx.argb(WHITE, 0.78f * alpha));
			}
		}
		// frame flash (bankai release)
		if (now < HudModel.barFlashUntil || (now - HudModel.barRedFlashStart >= 0 && now - HudModel.barRedFlashStart < 0.6
				&& ((int) ((now - HudModel.barRedFlashStart) / 0.15)) % 2 == 0)) {
			boolean red = now - HudModel.barRedFlashStart < 0.6 && now - HudModel.barRedFlashStart >= 0;
			g.quad(BAR_FRAME, 128, 11, bx, by, 128, 11, 0, 0, 128, 11, red ? 1f : 1f, red ? 0.42f : 1f, red ? 0.35f : 1f, 0.9f * alpha, HudGfx.ADD);
		}
		// bankai ready gem at the right end
		drawGem(g, bx + barW - 3, by + 1, st, accent, alpha);
	}

	private static void sheenAt(HudGfx g, int fx, int fy, int len, float sx, float a) {
		for (int r = 0; r < 7; r++) {
			int sh = shift(r);
			int xs = Math.max(Math.round(sx), 0);
			int xe = Math.min(Math.round(sx) + 14, len);
			if (xe > xs) {
				int u = xs - Math.round(sx);
				g.quad(BAR_SHEEN, 14, 7, fx + sh + xs, fy + r, xe - xs, 1, u, r, xe - xs, 1, 1f, 1f, 1f, a, HudGfx.ADD);
			}
		}
	}

	private static void drawGem(HudGfx g, int x, int y, ZanpakutoState st, float[] accent, float alpha) {
		double now = HudModel.now;
		boolean ready = HudModel.gemReady;
		int frame = 0;
		float a = alpha;
		float[] tint = WHITE;
		if (ready) {
			tint = accent;
			double sp = now - HudModel.gemSparkAt;
			frame = 1;
			if (sp >= 0 && sp < 0.3 && !FxConfig.reduceMotion) {
				frame = (int) (sp / 0.3 * 4) % 2 == 0 ? 2 : 3;
			}
			double pulse = 0.8 + 0.2 * Math.sin(2 * Math.PI * 1.2 * now);
			a = alpha * (float) (FxConfig.reduceMotion ? 1.0 : pulse);
		}
		g.sprite(GEM, 28, 9, x, y, frame * 7, 0, 7, 9, tint, a);
	}

	// ------------------------------------------------------------------------------------------ value line

	private static String valueKey = "";
	private static Text valueText;
	private static int valueW;
	private static String leftKey = "";
	private static Text leftText;

	private static void drawValueLine(HudGfx g, TextRenderer font, int barX, int y, int barW, CharacterId ch, ZanpakutoState st, float[] accent,
			float alpha, float tickDelta) {
		double now = HudModel.now;
		int a = alphaInt(alpha);
		String vk = HudModel.value + "/" + HudModel.max;
		if (!vk.equals(valueKey)) {
			String right1 = String.format(java.util.Locale.ROOT, "%.1f", HudModel.value / 10.0);
			String right2 = "/" + Math.round(HudModel.max / 10.0);
			valueText = Text.literal(right1).styled(t -> t.withColor(0xE8ECF4)).append(Text.literal(right2).styled(t -> t.withColor(0x8A93A6)));
			valueW = font.getWidth(right1) + font.getWidth(right2);
			valueKey = vk;
		}
		int rw = valueW;
		g.text(font, valueText, barX + barW - rw, y, 0x00FFFFFF | a << 24, true);
		String left = null;
		int col = 0x00AEB9C9;
		if (now - HudModel.endedAt >= 0 && now - HudModel.endedAt < 2.0) {
			left = HudText.tr("hud.reiatsu_test.bankai_ended");
			col = HudGfx.argb(WARN, 1f) & 0x00FFFFFF;
		} else if (st == ZanpakutoState.BANKAI) {
			int s = (int) Math.ceil(HudModel.bankaiRemaining(tickDelta));
			left = (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
			col = (s <= 10 ? HudGfx.argb(WARN, 1f) : HudGfx.argb(accent, 1f)) & 0x00FFFFFF;
		} else if (st == ZanpakutoState.SHIKAI) {
			double idle = HudModel.idleRemaining();
			if (idle < 20 && idle > 0) {
				left = HudText.tr("hud.reiatsu_test.seal_in", Math.max(1, (int) Math.ceil(idle)));
			}
		} else if (st == ZanpakutoState.SEALED) {
			left = HudText.tr("hud.reiatsu_test.hint.draw.short", dev.minebleach.reiatsutest.client.input.ReiatsuKeys.DRAW.getBoundKeyLocalizedText().getString());
		} else if (st == ZanpakutoState.BASE) {
			left = HudText.tr("hud.reiatsu_test.hint.release.short", dev.minebleach.reiatsutest.client.input.ReiatsuKeys.RELEASE.getBoundKeyLocalizedText().getString());
		}
		if (left != null) {
			int maxW = barW - rw - 6;
			if (font.getWidth(left) > maxW) {
				left = font.trimToWidth(left, Math.max(0, maxW));
			}
			g.text(font, left, barX, y, col | a << 24, true);
		}
	}

	// ------------------------------------------------------------------------------------------ voice row

	private static void drawVoiceRow(HudGfx g, MinecraftClient mc, TextRenderer font, int w, int x0, int y0, float[] accent, float alpha) {
		if (!FxConfig.voiceHud) {
			return;
		}
		VoiceHudState v = VoiceHudState.get();
		if (v.lastContactMs() <= 0) {
			return;
		}
		double now = HudModel.now;
		long wall = System.currentTimeMillis();
		VoiceHudState.Mic mic = wall - v.lastContactMs() > 6000 ? VoiceHudState.Mic.OFF : v.mic();
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
		// phrase toast
		HudModel.VoiceToast t = HudModel.VOICE;
		double age = now - t.lastTouch;
		if (t.lastTouch < 0 || age > 2.5 || (t.line1.isEmpty())) {
			return;
		}
		double inP = FxMath.oc((now - t.start) / 0.15);
		float fade = (float) Math.min(1.0, (2.5 - age) / 0.4);
		float ta = alpha * fade;
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
		int ia = alphaInt(ta);
		String line1 = font.getWidth(l1) > tw ? font.trimToWidth(l1, tw) : l1;
		Text l1t = Text.literal(line1).formatted(Formatting.ITALIC);
		g.text(font, l1t, px + 5, py + 4, (t.interim ? 0x008A93A6 : 0x00E8ECF4) | ia << 24, false);
		if (two) {
			String line2 = font.getWidth(t.line2) > tw ? font.trimToWidth(t.line2, tw) : t.line2;
			String mark = t.accepted ? "✓ " : t.line2Tint == DIM ? "- " : "✗ ";
			g.text(font, mark + line2, px + 5, py + 14, (HudGfx.argb(t.line2Tint, 1f) & 0x00FFFFFF) | ia << 24, false);
		}
	}
}
