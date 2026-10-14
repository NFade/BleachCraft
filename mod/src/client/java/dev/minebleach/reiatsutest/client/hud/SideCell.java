package dev.minebleach.reiatsutest.client.hud;

import static dev.minebleach.reiatsutest.client.hud.HudAssets.*;

import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxMath;
import dev.minebleach.reiatsutest.core.hud.SideCellMath;
import dev.minebleach.reiatsutest.core.hud.SideHudLayout;
import net.minecraft.client.font.TextRenderer;

/**
 * One 22 x 22 cell of the side HUD (kido corner, shunpo side cell), drawn like a strip slot (VFX_STORYBOARD 7.3): slot frame,
 * 16 x 16 icon, cooldown sweep, whole seconds, key tab with the key letter, denied feedback. Holds the small animation state
 * (shake, flashes, ready ping, largest remaining value seen) and nothing else; the owners feed it the numbers every frame.
 */
final class SideCell {
	private static final float[] POOR_RED = FxMath.hex("#FF5A4A");

	/** FX clock moments until which the shake / colour flashes last (set by the denied feedback). */
	double shake;
	double flashRed;
	double flashGrey;
	double flashGold;
	double pingAge = 99;
	private int lastRem;
	private boolean seen;
	private int peak;

	void reset() {
		shake = flashRed = flashGrey = flashGold = 0;
		pingAge = 99;
		lastRem = 0;
		seen = false;
		peak = 0;
	}

	/** Called every frame, drawn or not: tracks the largest remaining value of this cooldown and starts the ready ping. */
	void update(int rem, double dt, boolean live) {
		if (rem > peak) {
			peak = rem;
		}
		if (rem <= 0) {
			peak = 0;
		}
		if (seen && lastRem > 0 && rem <= 0 && live) {
			pingAge = 0;
		}
		if (pingAge < 90) {
			pingAge += dt;
		}
		seen = true;
		lastRem = rem;
	}

	int total(int configuredTicks) {
		return SideCellMath.total(configuredTicks, peak);
	}

	/** What a cell shows this frame. */
	record View(SideHudIcons.Icon icon, float[] tint, String key, int remaining, int total, boolean poor, boolean disabled,
			boolean verticalWipe, float alpha) {
	}

	void draw(HudGfx g, TextRenderer font, SideHudLayout.Rect r, View v) {
		double now = HudModel.now;
		boolean rm = FxConfig.reduceMotion;
		float xo = 0;
		if (now < shake && !rm) {
			xo = Math.round((float) (2 * Math.sin(2 * Math.PI * 30 * now)));
		}
		var ms = g.ctx().getMatrices();
		ms.push();
		ms.translate(r.x() + xo, r.y(), 0);
		float alpha = v.alpha();
		boolean cooling = v.remaining() > 0 && !v.disabled();
		int frame = v.disabled() ? 2 : v.poor() ? 1 : 0;
		float[] frameTint = v.tint();
		if (now < flashRed) {
			frameTint = POOR_RED;
		} else if (now < flashGrey) {
			frameTint = DIM;
		} else if (v.poor() && !v.disabled()) {
			frameTint = new float[] {1f, 0.77f, 0.72f};
		}
		g.sprite(SLOT_FRAME, 88, 22, 0, 0, frame * 22, 0, 22, 22, v.disabled() ? WHITE : frameTint, alpha);
		float ib = v.disabled() ? 0.35f : cooling ? 0.55f : v.poor() ? 0.6f : 1f;
		v.icon().draw(g, 3, 3, ib, alpha);
		if (cooling) {
			if (v.verticalWipe()) {
				int hgt = SideCellMath.wipeHeight(v.remaining(), v.total(), SideHudIcons.SIZE);
				g.fill(3, 3, 19, 3 + hgt, HudGfx.argb(INK, 0.62f * alpha));
				if (hgt < SideHudIcons.SIZE) {
					g.fill(3, 3 + hgt, 19, 4 + hgt, HudGfx.argb(WHITE, 0.55f * alpha));
				}
			} else {
				int f = SideCellMath.sweepFrame(v.remaining(), v.total());
				if (f > 0) {
					g.sprite(COOLDOWN_SWEEP, 120, 80, 1, 1, ((f - 1) % 6) * 20, ((f - 1) / 6) * 20, 20, 20, WHITE, alpha);
				}
			}
		}
		if (v.poor() && !v.disabled()) {
			g.fill(2, 19, 20, 21, HudGfx.argb(POOR_RED, 0.95f * alpha));
		}
		if (pingAge < 0.3) {
			float p = (float) (1 - FxMath.clamp(pingAge / 0.25));
			g.quad(SLOT_FRAME, 88, 22, 0, 0, 22, 22, 0, 0, 22, 22, 1f, 1f, 1f, p * alpha, HudGfx.ADD);
			if (!rm) {
				double gp = FxMath.clamp(pingAge / 0.3);
				float size = (float) FxMath.lerp(20, 28, gp);
				g.quad(SLOT_GLOW, 28, 28, 11 - size / 2, 11 - size / 2, size, size, 0, 0, 28, 28, v.tint()[0], v.tint()[1], v.tint()[2],
						(float) (0.6 * (1 - gp)) * alpha, HudGfx.ADD);
			}
		}
		String secs = cooling ? SideCellMath.secondsLabel(v.remaining()) : null;
		if (secs != null) {
			int col = now < flashGold ? 0x00FFD860 : 0x00FFFFFF;
			g.text(font, secs, 11 - font.getWidth(secs) / 2f, 7, col | PlateHud.alphaInt(alpha) << 24, true);
		}
		g.sprite(KEY_TAB, 9, 9, 13, 13, 0, 0, 9, 9, v.tint(), alpha);
		String key = v.key();
		g.text(font, key, 13 + (9 - font.getWidth(key)) / 2f + 0.5f, 14, 0x00E8ECF4 | PlateHud.alphaInt(alpha) << 24, false);
		ms.pop();
	}
}
