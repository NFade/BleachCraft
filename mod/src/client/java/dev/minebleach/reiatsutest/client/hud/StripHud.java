package dev.minebleach.reiatsutest.client.hud;

import static dev.minebleach.reiatsutest.client.hud.HudAssets.*;

import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxMath;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.AttackIndicator;

/** Ability strip beside the hotbar and the three crosshair pips (VFX_STORYBOARD 7.3). */
final class StripHud {
	private static final float[] POOR_RED = FxMath.hex("#FF5A4A");
	private StripHud() {
	}

	static int iconIndex(AbilityId a) {
		return switch (a) {
			case TSUKISHIRO -> 0;
			case HAKUREN -> 1;
			case SHIRAFUNE -> 2;
			case ABSOLUTE_ZERO -> 3;
			case MODE_ATTACK -> 4;
			case MODE_BARRIER -> 5;
			case SCATTER -> 6;
			case HAKUTEIKEN -> 7;
			case SENKEI -> 8;
		};
	}

	/** Left edge of the horizontal strip for the window, or -1 when it has to stack vertically. */
	static int stripX(MinecraftClient mc, int w, int slots) {
		int x = w / 2 + 91 + 6;
		if (mc.options.getAttackIndicator().getValue() == AttackIndicator.HOTBAR) {
			x += 20;
		}
		int width = slots * 22 + (slots - 1) * 2;
		return x + width > w - 4 ? -1 : x;
	}

	/** Slot origin of slot i (horizontal strip next to the hotbar, or the vertical stack on narrow windows). */
	static int[] slotPos(MinecraftClient mc, int w, int h, int n, int i) {
		int x = stripX(mc, w, n);
		return x >= 0 ? new int[] {x + i * 24, h - 22} : new int[] {w - 24, h - 22 - 24 * i};
	}

	static void draw(HudGfx g, MinecraftClient mc, TextRenderer font, int w, int h) {
		int n = HudModel.slotCount;
		if (n == 0) {
			return;
		}
		double now = HudModel.now;
		boolean liveStrip = HudModel.state == ZanpakutoState.SHIKAI || HudModel.state == ZanpakutoState.BANKAI;
		float master = 1f;
		float lift = 0f;
		if (!liveStrip) {
			double t = (now - HudModel.stripOutStart) / 0.2;
			if (HudModel.stripOutStart < 0 || t >= 1) {
				return;
			}
			master = (float) (1 - t);
			lift = FxConfig.reduceMotion ? 0f : (float) (t * 8);
		}
		float[] accent = HudAssets.accent(HudModel.stripChar, HudModel.stripState);
		for (int i = 0; i < n; i++) {
			HudModel.Slot s = HudModel.SLOTS[i];
			int[] pos = slotPos(mc, w, h, n, i);
			float enter = 1f;
			double ti = (now - HudModel.stripInStart - 0.05 * i) / 0.25;
			if (liveStrip && HudModel.stripInStart > 0 && ti < 1) {
				enter = FxConfig.reduceMotion ? (float) Math.max(0, ti) : (float) FxMath.oc(Math.max(0, ti));
			}
			float a = enter * master;
			float yo = FxConfig.reduceMotion ? lift * 0 : (1 - enter) * 8 + lift;
			float xo = 0;
			if (now < s.shake && !FxConfig.reduceMotion) {
				xo = Math.round((float) (2 * Math.sin(2 * Math.PI * 30 * now)));
			}
			drawSlot(g, font, s, pos[0] + xo, pos[1] + yo, accent, a);
		}
		if (FxConfig.crosshairPips && liveStrip && HudModel.plateWanted) {
			int px = w / 2 - 6;
			int py = h / 2 + 9;
			for (int i = 0; i < n; i++) {
				HudModel.Slot s = HudModel.SLOTS[i];
				if (s.state == HudModel.SlotState.DISABLED) {
					continue;
				}
				int col = switch (s.state) {
					case READY, ACTIVE -> HudGfx.argb(accent, 0.9f);
					case POOR -> HudGfx.argb(POOR_RED, 0.6f);
					default -> HudGfx.argb(DIM, 0.4f);
				};
				g.fill(px + i * 5, py, px + i * 5 + 2, py + 2, col);
			}
		}
	}

	private static void drawSlot(HudGfx g, TextRenderer font, HudModel.Slot s, float x, float y, float[] accent, float alpha) {
		double now = HudModel.now;
		var ms = g.ctx().getMatrices();
		ms.push();
		ms.translate(x, y, 0);
		int frame = switch (s.state) {
			case POOR -> 1;
			case DISABLED -> 2;
			case ACTIVE -> 3;
			default -> 0;
		};
		float fa = alpha;
		if (s.state == HudModel.SlotState.ACTIVE && !FxConfig.reduceMotion) {
			fa *= (float) (0.8 + 0.2 * Math.sin(2 * Math.PI * 2 * now));
		}
		float[] frameTint = accent;
		if (now < s.flashRed) {
			frameTint = POOR_RED;
		} else if (now < s.flashGrey) {
			frameTint = DIM;
		} else if (s.state == HudModel.SlotState.POOR) {
			frameTint = new float[] {1f, 0.77f, 0.72f};
		}
		g.sprite(SLOT_FRAME, 88, 22, 0, 0, frame * 22, 0, 22, 22, s.state == HudModel.SlotState.DISABLED ? WHITE : frameTint, fa);
		int ii = iconIndex(s.ability);
		float ib = switch (s.state) {
			case COOLING -> 0.55f;
			case POOR -> 0.6f;
			case DISABLED -> 0.35f;
			default -> 1f;
		};
		g.sprite(ABILITY_ICONS, 64, 48, 3, 3, (ii % 4) * 16, (ii / 4) * 16, 16, 16, new float[] {ib, ib, ib}, alpha);
		if (s.state == HudModel.SlotState.COOLING && s.totalTicks > 0) {
			int f = Math.max(1, Math.min(24, (int) Math.ceil(24.0 * s.remainingTicks / s.totalTicks)));
			g.sprite(COOLDOWN_SWEEP, 120, 80, 1, 1, ((f - 1) % 6) * 20, ((f - 1) / 6) * 20, 20, 20, WHITE, alpha);
		}
		if (s.state == HudModel.SlotState.POOR) {
			g.fill(2, 19, 20, 21, HudGfx.argb(POOR_RED, 0.95f * alpha));
		}
		if (s.state == HudModel.SlotState.ACTIVE) {
			g.fill(2, 2, 2 + Math.round(18 * (float) s.activeFraction), 4, HudGfx.argb(accent, 0.95f * alpha));
		}
		if (s.state == HudModel.SlotState.DISABLED) {
			int c = HudGfx.argb(DIM, 0.9f * alpha);
			g.fill(8, 11, 14, 17, c);
			g.fill(9, 8, 10, 11, c);
			g.fill(12, 8, 13, 11, c);
			g.fill(9, 8, 13, 9, c);
			g.fill(10, 13, 12, 15, HudGfx.argb(INK, 0.9f * alpha));
		}
		if (s.pingAge < 0.3) {
			float p = (float) (1 - FxMath.clamp(s.pingAge / 0.25));
			g.quad(SLOT_FRAME, 88, 22, 0, 0, 22, 22, 0, 0, 22, 22, 1f, 1f, 1f, p * alpha, HudGfx.ADD);
			double gp = FxMath.clamp(s.pingAge / 0.3);
			float size = (float) FxMath.lerp(20, 28, gp);
			g.quad(SLOT_GLOW, 28, 28, 11 - size / 2, 11 - size / 2, size, size, 0, 0, 28, 28, accent[0], accent[1], accent[2],
					(float) (0.6 * (1 - gp)) * alpha, HudGfx.ADD);
		}
		if (s.state == HudModel.SlotState.COOLING && s.remainingTicks >= 20) {
			String t = Integer.toString((int) Math.ceil(s.remainingTicks / 20.0));
			int col = now < s.flashGold ? 0x00FFD860 : 0x00FFFFFF;
			int a = PlateHud.alphaInt(alpha);
			g.text(font, t, 11 - font.getWidth(t) / 2f, 7, col | a << 24, true);
		}
		g.sprite(KEY_TAB, 9, 9, 13, 13, 0, 0, 9, 9, accent, alpha);
		String key = (s.bankaiKey ? ReiatsuKeys.BANKAI_SLOTS : ReiatsuKeys.SLOTS)[s.index].getBoundKeyLocalizedText().getString();
		if (key.length() > 1) {
			key = key.substring(0, 1);
		}
		int a = PlateHud.alphaInt(alpha);
		g.text(font, key, 13 + (9 - font.getWidth(key)) / 2f + 0.5f, 14, 0x00E8ECF4 | a << 24, false);
		ms.pop();
	}
}
