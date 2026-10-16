package dev.minebleach.reiatsutest.client.hud;

import static dev.minebleach.reiatsutest.client.hud.HudAssets.*;

import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxMath;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.CharacterWording;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

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
		rageOnScreen = false;
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
		drawRage(g, font, barX, y0 + 37, barW, alpha, now);
		drawMomentum(g, barX, y0 + 37, barW, alpha, now);
		long q3 = System.nanoTime();
		PROF[0] += q1 - q0;
		PROF[1] += q2 - q1;
		PROF[2] += q3 - q2;
		ms.pop();
		drawFocus(g, w, h, alpha, now, ch, st);
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
			g.sprite(ABILITY_ICONS, 64, 64, x0 + 7, y0 + 7, 16 * 2, 32 - 32 + 32, 16, 16, new float[] {1f, 0.45f, 0.4f}, fa2);
		}
		ms.pop();
	}

	private static void drawRings(HudGfx g, int x0, int y0, ZanpakutoState st, float[] accent, float alpha, float tickDelta) {
		double now = HudModel.now;
		int rx = x0 - 4;
		int ry = y0 - 4;
		if (st == ZanpakutoState.BANKAI) {
			double rem = HudModel.bankaiRemaining(tickDelta);
			double frac = rem / (HudModel.cfg().bankaiCapTicks() / 20.0);
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
		return stateWord(CharacterId.NONE, s);
	}

	/** The state word in the wording of the character (Uryu: Heilig Bogen / Blut Arterie / Quincy: Vollstaendig). */
	static Text stateWord(CharacterId ch, ZanpakutoState s) {
		return Text.translatable(CharacterWording.stateKey(ch, s));
	}

	private static String nameKey = "";
	private static Text nameText;

	private static void drawNameLine(HudGfx g, TextRenderer font, int x, int y, CharacterId ch, ZanpakutoState st, float[] accent, float alpha, int barW) {
		int accentRgb = HudGfx.argb(accent, 1f) & 0x00FFFFFF;
		String key = "hud.reiatsu_test.zanpakuto." + ch.langKey() + (st == ZanpakutoState.BANKAI ? ".bankai" : "");
		String ck = st + "|" + key + "|" + accentRgb + "|" + barW + "|" + HudText.generation();
		if (!ck.equals(nameKey)) {
			String word = HudText.tr(CharacterWording.stateKey(ch, st)).toUpperCase(java.util.Locale.ROOT);
			String name = HudText.tr(key);
			// Uryu: the state word and the name can be the same words (Heilig Bogen, Quincy: Vollstaendig); then they show once
			String sep = name.equalsIgnoreCase(word) ? "" : " \u00b7 ";
			if (sep.isEmpty()) {
				name = "";
			}
			int used = font.getWidth(word) + font.getWidth(sep);
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
			int tx = fillLen(HudModel.cfg().shikaiReleaseCost() / max, span);
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
			left = HudText.tr(CharacterWording.key("hud.reiatsu_test.bankai_ended", ch));
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
			left = HudText.tr(CharacterWording.key("hud.reiatsu_test.hint.draw.short", ch), dev.minebleach.reiatsutest.client.input.ReiatsuKeys.DRAW.getBoundKeyLocalizedText().getString());
		} else if (st == ZanpakutoState.BASE) {
			left = HudText.tr(CharacterWording.key("hud.reiatsu_test.hint.release.short", ch), dev.minebleach.reiatsutest.client.input.ReiatsuKeys.RELEASE.getBoundKeyLocalizedText().getString());
		}
		if (left != null) {
			int maxW = barW - rw - 6;
			if (font.getWidth(left) > maxW) {
				left = font.trimToWidth(left, Math.max(0, maxW));
			}
			g.text(font, left, barX, y, col | a << 24, true);
		}
	}

	// ------------------------------------------------------------------------------------------ rage

	private static final int RAGE_H = 9;
	private static final int RAGE_ICON = 9;
	private static final int RAGE_NUM_W = 22;
	private static final float[] RAGE_INK = FxMath.hex("#0B0F1A");
	private static final float[] RAGE_RIM = FxMath.hex("#8A3A1C");
	private static final float[] RAGE_BACK = FxMath.hex("#2A0E0A");
	private static final float[] RAGE_LOW = FxMath.hex("#A81E0B");
	private static final float[] RAGE_MID = FxMath.hex("#F0541A");
	private static final float[] RAGE_HIGH = FxMath.hex("#FFB02E");
	private static final float[] RAGE_GLOW = FxMath.hex("#FFC27A");
	private static final float[] RAGE_TRAIL = FxMath.hex("#FFE0B0");
	private static final float[] RAGE_TEXT = FxMath.hex("#FF8A3C");
	private static final float[] RAGE_TEXT_FULL = FxMath.hex("#FFE9A8");
	private static final float[] RAGE_CAPTION = FxMath.hex("#D98A5A");
	/** 9x9 flame: o outline, r red, y orange, w pale yellow core, dot transparent. */
	private static final String[] FLAME = {
			"....o....",
			"...oro...",
			"...orro..",
			"..orryro.",
			".orryyyro",
			".orywyyro",
			".orywwyro",
			"..orwwro.",
			"..ooooo.."};
	private static final float[] FLAME_O = FxMath.hex("#5A1408");
	private static final float[] FLAME_R = FxMath.hex("#E04214");
	private static final float[] FLAME_Y = FxMath.hex("#FF8A1E");
	private static final float[] FLAME_W = FxMath.hex("#FFE6A0");

	/** Eased fill fraction (fast), the pale "just lost" trail (slow, only above the fill), gain flash 0..1, fade-in 0..1. */
	private static double rageShown;
	private static double rageTrail;
	private static double rageFlash;
	private static double rageFade;
	private static double rageYOff;
	private static int rageLastValue;
	private static double rageLastNow = -1;
	/** Round 5: time (HudModel.now seconds) of the last refused shikai/bankai for lack of Rage, and the needed fraction of the bar. */
	private static double rageDenyAt = -99;
	private static double rageDenyFrac;
	private static final double RAGE_DENY_LIFE = 1.0;

	/** Flashes the Rage bar after a refused release: the missing part up to the needed mark turns red and the mark blinks. */
	static void flashRageDenied(double neededFraction) {
		rageDenyAt = HudModel.now;
		rageDenyFrac = FxMath.clamp(neededFraction);
	}

	/** Set by {@link #draw} for the frame: the bar is on screen (the plate is visible and the bar has not faded out). */
	private static boolean rageOnScreen;

	/**
	 * Rage bar (round 3): flame icon, framed bar with an orange gradient, top gloss and a slow sheen, quarter ticks, the value on
	 * the right and the caption under the bar. The fill eases up fast and down slower, a pale trail shows what was just lost,
	 * every gain flashes the fill, a full bar pulses (glow rim, brighter fill, number and icon; static with reduceMotion). It
	 * fades in when the first rage arrives and out when it is spent. While the voice toast is on screen the whole row steps
	 * below it, so the toast never covers the bar. Occupies {@code RAGE_H + 7} px below {@code by0} (the voice status line
	 * moves to y0+54 while the bar is visible, see {@link #rageVisible}).
	 */
	private static void drawRage(HudGfx g, TextRenderer font, int bx, int by0, int barW, float alpha, double now) {
		var r = dev.minebleach.reiatsutest.client.ClientState.rage();
		boolean rm = FxConfig.reduceMotion;
		double max = Math.max(1, r.max());
		double target = FxMath.clamp(r.value() / max);
		double dt = rageLastNow < 0 ? 1.0 : Math.min(0.25, Math.max(0.0, now - rageLastNow));
		rageLastNow = now;
		if (r.value() > rageLastValue) {
			rageFlash = 1.0;
		}
		rageLastValue = r.value();
		rageFlash = Math.max(0.0, rageFlash - dt * 2.8);
		if (rm) {
			rageShown = target;
			rageTrail = target;
			rageFlash = 0;
		} else {
			rageShown += (target - rageShown) * Math.min(1.0, dt * (target > rageShown ? 14.0 : 6.0));
			rageTrail = rageShown >= rageTrail ? rageShown : rageTrail + (rageShown - rageTrail) * Math.min(1.0, dt * 2.2);
		}
		boolean denying = now - rageDenyAt >= 0 && now - rageDenyAt < RAGE_DENY_LIFE;
		boolean want = target > 0.0 || rageShown > 0.004 || rageTrail > 0.004 || denying;
		rageFade = rm ? (want ? 1.0 : 0.0) : FxMath.clamp(rageFade + (want ? 1 : -1) * dt * 5.0);
		if (!want && rageFade <= 0.01) {
			rageShown = 0;
			rageTrail = 0;
			rageFade = 0;
			return;
		}
		rageOnScreen = true;
		float a = alpha * (float) rageFade;
		// below the voice toast while it is up (the toast is 16 px, 25 with a verdict line)
		double yWant = toastHeight(now);
		rageYOff = rm ? yWant : rageYOff + (yWant - rageYOff) * Math.min(1.0, dt * 12.0);
		int y = by0 + (int) Math.round(rageYOff);
		boolean full = r.value() >= r.max() && r.max() > 0;
		float pulse = full ? (rm ? 0.6f : (float) (0.5 + 0.5 * Math.sin(2 * Math.PI * 1.6 * now))) : 0f;

		int x = bx + RAGE_ICON + 2;
		int bw = Math.max(24, barW - RAGE_ICON - 2 - RAGE_NUM_W);
		int ix0 = x + 2;
		int innerW = bw - 4;
		int len = (int) Math.round(innerW * rageShown);
		int trailLen = (int) Math.round(innerW * rageTrail);

		// glow rim on a full bar
		if (full) {
			int ga = HudGfx.argb(RAGE_GLOW, a * (0.25f + 0.5f * pulse));
			g.fill(x - 1, y - 1, x + bw + 1, y, ga);
			g.fill(x - 1, y + RAGE_H, x + bw + 1, y + RAGE_H + 1, ga);
			g.fill(x - 1, y, x, y + RAGE_H, ga);
			g.fill(x + bw, y, x + bw + 1, y + RAGE_H, ga);
		}
		// frame: ink outline, copper rim, dark well
		g.fill(x, y, x + bw, y + RAGE_H, HudGfx.argb(RAGE_INK, 0.9f * a));
		g.fill(x + 1, y + 1, x + bw - 1, y + RAGE_H - 1, HudGfx.argb(RAGE_RIM, a));
		g.fill(ix0, y + 2, ix0 + innerW, y + RAGE_H - 2, HudGfx.argb(RAGE_BACK, a));
		// the pale trail of what was just lost
		if (trailLen > len) {
			g.fill(ix0 + len, y + 2, ix0 + trailLen, y + RAGE_H - 2, HudGfx.argb(RAGE_TRAIL, 0.55f * a));
		}
		// fill: position based gradient red -> orange -> amber in 3 px steps
		for (int px = 0; px < len; px += 3) {
			double t = (px + 1.5) / innerW;
			float[] c = t < 0.5 ? FxMath.mix(RAGE_LOW, RAGE_MID, t * 2.0) : FxMath.mix(RAGE_MID, RAGE_HIGH, (t - 0.5) * 2.0);
			if (full) {
				c = FxMath.mix(c, RAGE_GLOW, 0.25 + 0.4 * pulse);
			}
			g.fill(ix0 + px, y + 2, ix0 + Math.min(len, px + 3), y + RAGE_H - 2, HudGfx.argb(c, a));
		}
		if (len > 0) {
			g.fill(ix0, y + 2, ix0 + len, y + 3, HudGfx.argb(WHITE, 0.30f * a)); // top gloss line
			g.fill(ix0, y + RAGE_H - 3, ix0 + len, y + RAGE_H - 2, HudGfx.argb(RAGE_INK, 0.28f * a)); // bottom shade
			if (rageFlash > 0.01) {
				g.fill(ix0, y + 2, ix0 + len, y + RAGE_H - 2, HudGfx.argb(WHITE, (float) rageFlash * 0.45f * a));
			}
			if (!rm && len > 10) { // slow sheen band running along the fill
				int sx = (int) ((now * 0.5 % 1.0) * (len + 12)) - 6;
				int s0 = Math.max(0, sx);
				int s1 = Math.min(len, sx + 5);
				if (s1 > s0) {
					g.fill(ix0 + s0, y + 2, ix0 + s1, y + RAGE_H - 2, HudGfx.argb(WHITE, 0.20f * a));
				}
			}
		}
		// quarter ticks (25 / 50 / 75 percent)
		for (int k = 1; k <= 3; k++) {
			int tx = ix0 + Math.round(innerW * k / 4.0f);
			g.fill(tx, y + 2, tx + 1, y + RAGE_H - 2, HudGfx.argb(RAGE_INK, (rageShown >= k / 4.0 ? 0.35f : 0.6f) * a));
		}
		if (denying) {
			// refused release: the missing part up to the needed mark in red, the mark itself blinks white (steady with reduceMotion)
			float k = (float) (1.0 - (now - rageDenyAt) / RAGE_DENY_LIFE);
			float blink = rm ? 1f : (float) (0.5 + 0.5 * Math.cos(2 * Math.PI * 4.0 * (now - rageDenyAt)));
			int mark = ix0 + (int) Math.round(innerW * rageDenyFrac);
			if (mark > ix0 + len) {
				g.fill(ix0 + len, y + 2, mark, y + RAGE_H - 2, HudGfx.argb(ERROR, 0.55f * k * a));
			}
			g.fill(mark, y + 1, mark + 1, y + RAGE_H - 1, HudGfx.argb(WHITE, (0.45f + 0.5f * blink) * k * a));
			g.fill(x, y, x + bw, y + 1, HudGfx.argb(ERROR, 0.7f * k * a));
			g.fill(x, y + RAGE_H - 1, x + bw, y + RAGE_H, HudGfx.argb(ERROR, 0.7f * k * a));
		}
		drawFlame(g, bx, y, (float) rageShown, pulse, a);
		// value on the right, caption under the bar
		String num = Integer.toString((int) Math.round(r.value() / 10.0));
		float[] tc = full ? FxMath.mix(RAGE_TEXT, RAGE_TEXT_FULL, 0.4 + 0.6 * pulse) : RAGE_TEXT;
		g.text(font, num, bx + barW - font.getWidth(num), y + 1, (HudGfx.argb(tc, 1f) & 0x00FFFFFF) | alphaInt(a) << 24, true);
		MatrixStack ms = g.ctx().getMatrices();
		ms.push();
		ms.translate(x + 2, y + RAGE_H + 1, 0);
		ms.scale(0.75f, 0.75f, 1f);
		g.textAt(font, HudText.tr("hud.reiatsu_test.rage"), (HudGfx.argb(full ? RAGE_TEXT_FULL : RAGE_CAPTION, 1f) & 0x00FFFFFF) | alphaInt(a * 0.9f) << 24, true);
		ms.pop();
	}

	/** R11 T6: 5x5 pixel snowflake, the stack marker of the Rukia passive. */
	private static final String[] FLAKE = {"..#..", "#.#.#", ".###.", "#.#.#", "..#.."};
	private static final float[] FLAKE_ON = {0.72f, 0.92f, 1.0f};
	private static final float[] FLAKE_MAX = {1.0f, 1.0f, 1.0f};
	private static final float[] FLAKE_OFF = {0.30f, 0.42f, 0.55f};

	/**
	 * R11 T6: the frost stacks of Rukia as a row of snowflakes at the right end of the caption line under the rage bar (the caption is on the left,
	 * so they do not meet). Only while she holds at least one stack; empty slots up to the current maximum are dim; at the maximum the flakes
	 * glow white and pulse (steady with reduceMotion). Follows the rage bar when the voice toast pushes it down. UNVERIFIED in the game.
	 */
	private static void drawMomentum(HudGfx g, int bx, int by0, int barW, float alpha, double now) {
		var m = dev.minebleach.reiatsutest.client.ClientState.momentum();
		if (m.max() <= 0 || m.stacks() <= 0) {
			return;
		}
		boolean rm = FxConfig.reduceMotion;
		int y = by0 + (int) Math.round(rageYOff) + RAGE_H + 2;
		int step = 7;
		int x = bx + barW - m.max() * step + 2;
		boolean atMax = m.stacks() >= m.max();
		float pulse = atMax && !rm ? (float) (0.5 + 0.5 * Math.sin(2 * Math.PI * 1.6 * now)) : atMax ? 0.6f : 0f;
		for (int i = 0; i < m.max(); i++) {
			boolean on = i < m.stacks();
			float[] c = on ? (atMax ? FxMath.mix(FLAKE_ON, FLAKE_MAX, 0.4 + 0.6 * pulse) : FLAKE_ON) : FLAKE_OFF;
			float a = alpha * (on ? 1.0f : 0.45f);
			int fx = x + i * step;
			for (int row = 0; row < 5; row++) {
				String line = FLAKE[row];
				for (int col = 0; col < 5; col++) {
					if (line.charAt(col) == '#') {
						g.fill(fx + col, y + row, fx + col + 1, y + row + 1, HudGfx.argb(c, a));
					}
				}
			}
		}
	}

	private static final String[] PIP_FULL = {"..#..", ".###.", "#####", ".###.", "..#.."};
	private static final String[] PIP_EMPTY = {"..#..", ".#.#.", "#...#", ".#.#.", "..#.."};
	private static final float[] PIP_ON = {0.66f, 0.85f, 0.96f};
	private static final float[] PIP_MAX = {1.0f, 1.0f, 1.0f};
	private static final float[] PIP_OFF = {0.38f, 0.52f, 0.66f};

	/**
	 * Uryu's Focus (design/URYU.md section 3): three Reishi diamonds centred under the crosshair (R14; they were in the row under the reiatsu / Rage
	 * bars of the plate, away from where the archer looks). Server synced ({@code UryuFx.focus}); only for a drawn Uryu. Filled diamonds pale blue, empty ones
	 * an outline, white and pulsing at 3 stacks (steady with reduceMotion). Without the Rage bar the row moves up under the value line.
	 * Pure rectangles, so it cannot crash on a missing atlas (the Gin / Uryu placeholder rule).
	 */
	private static void drawFocus(HudGfx g, int w, int h, float alpha, double now, CharacterId ch, ZanpakutoState st) {
		if (ch != CharacterId.URYU || st == ZanpakutoState.SEALED) {
			return;
		}
		int stacks = Math.max(0, Math.min(3, dev.minebleach.reiatsutest.client.fx.UryuFx.focus));
		boolean rm = FxConfig.reduceMotion;
		// R14: under the crosshair (the cross of the ability pips is at h/2 + 9), not in the plate at the top left corner
		int step = 8;
		int y = h / 2 + 14;
		int x = w / 2 - (2 * step + 5) / 2;
		boolean atMax = stacks >= 3;
		float pulse = atMax && !rm ? (float) (0.5 + 0.5 * Math.sin(2 * Math.PI * 1.6 * now)) : atMax ? 0.6f : 0f;
		for (int i = 0; i < 3; i++) {
			boolean on = i < stacks;
			float[] c = on ? (atMax ? FxMath.mix(PIP_ON, PIP_MAX, 0.4 + 0.6 * pulse) : PIP_ON) : PIP_OFF;
			float a = alpha * (on ? 1.0f : 0.5f);
			String[] pat = on ? PIP_FULL : PIP_EMPTY;
			int fx = x + i * step;
			for (int row = 0; row < 5; row++) {
				for (int col = 0; col < 5; col++) {
					if (pat[row].charAt(col) == '#') {
						g.fill(fx + col, y + row, fx + col + 1, y + row + 1, HudGfx.argb(c, a));
					}
				}
			}
		}
	}

	/** 9x9 pixel flame; brighter with the fill, white-hot pulse on a full bar. */
	private static void drawFlame(HudGfx g, int x, int y, float fill, float pulse, float a) {
		float lift = 0.55f + 0.45f * fill + 0.35f * pulse;
		for (int row = 0; row < RAGE_ICON; row++) {
			String line = FLAME[row];
			for (int col = 0; col < RAGE_ICON; col++) {
				char ch = line.charAt(col);
				if (ch == '.') {
					continue;
				}
				float[] base = ch == 'o' ? FLAME_O : ch == 'r' ? FLAME_R : ch == 'y' ? FLAME_Y : FLAME_W;
				float[] c = {Math.min(1f, base[0] * lift), Math.min(1f, base[1] * lift), Math.min(1f, base[2] * lift)};
				g.fill(x + col, y + row, x + col + 1, y + row + 1, HudGfx.argb(c, a));
			}
		}
	}

	/** Height of the voice toast while it is on screen (same conditions as {@code VoiceRowHud.drawToast}) plus a gap, 0 otherwise. */
	private static double toastHeight(double now) {
		HudModel.VoiceToast t = HudModel.VOICE;
		double age = now - t.lastTouch;
		if (t.lastTouch < 0 || age > 2.5 || t.line1.isEmpty()) {
			return 0.0;
		}
		return (!t.interim && !t.line2.isEmpty() ? 25 : 16) + 3;
	}

	/** True while the rage bar is on screen: the voice row then moves its status text line down (to y0+54, below the bar). */
	static boolean rageVisible() {
		return rageOnScreen;
	}
}
