package dev.minebleach.reiatsutest.client.hud;

import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.data.ReiatsuData;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Reiatsu bar, state label and cooldown boxes (HudRenderCallback, Fabric API 0.116.17: still the supported hook in
 * 1.21.1). Drawn above the hotbar. Shown when a zanpakuto is held or released.
 */
public final class LegacyHud {
	private static final int BAR_W = 182;
	private static final int BAR_H = 7;
	private static final BalanceConfig CFG = BalanceConfig.defaults();
	private static long flashUntil;
	private static long shakeUntil;

	private LegacyHud() {
	}

	public static void flashBar() {
		flashUntil = System.currentTimeMillis() + 400;
	}

	public static void shakeCooldowns() {
		shakeUntil = System.currentTimeMillis() + 350;
	}

	static void render(DrawContext ctx, RenderTickCounter tick) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden || ReiatsuHud.devHidden) {
			return;
		}
		ZanpakutoData z = ClientState.zanpakuto();
		ZanpakutoState state = z.zanpakutoState();
		CharacterId held = ClientState.heldCharacter();
		if (state == ZanpakutoState.SEALED && held == CharacterId.NONE) {
			return;
		}
		CharacterId character = state == ZanpakutoState.SEALED ? held : z.characterId();
		ReiatsuData r = ClientState.reiatsu();
		TextRenderer font = mc.textRenderer;
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		int x = w / 2 - BAR_W / 2;
		int y = h - 94;
		long nowMs = System.currentTimeMillis();

		int fillColor = character == CharacterId.BYAKUYA ? 0xFFFF9EC4 : 0xFF7FD4FF;
		boolean flashing = nowMs < flashUntil && (nowMs / 80) % 2 == 0;

		// bar
		ctx.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, flashing ? 0xFFFF2020 : 0xFF101018);
		ctx.fill(x, y, x + BAR_W, y + BAR_H, 0xFF2A2A38);
		int value = r == null ? 0 : r.value();
		int max = r == null || r.max() <= 0 ? 1000 : r.max();
		int fill = (int) Math.round((double) BAR_W * value / max);
		ctx.fill(x, y, x + fill, y + BAR_H, fillColor);
		// the 15.0 release threshold tick
		int tickX = x + (int) Math.round(BAR_W * (double) CFG.shikaiReleaseCost() / max);
		ctx.fill(tickX, y, tickX + 1, y + BAR_H, 0x99FFFFFF);

		// label: state and value
		Text stateText = Text.translatable("hud.reiatsu_test.state." + state.name().toLowerCase(java.util.Locale.ROOT));
		String values = r == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f / %.1f", value / 10.0, max / 10.0);
		Text label = Text.translatable("hud.reiatsu_test.reiatsu").append(Text.literal(" " + values + "  "))
				.append(stateText);
		ctx.drawText(font, label, x, y - 10, 0xFFFFFFFF, true);
		if (state == ZanpakutoState.BANKAI) {
			int left = Math.max(0, (CFG.bankaiCapTicks() - (int) ClientState.ticksInState()) / 20);
			Text t = Text.translatable("hud.reiatsu_test.bankai_time", Integer.toString(left));
			ctx.drawText(font, t, x + BAR_W - font.getWidth(t), y - 10, 0xFFFFCC66, true);
		}

		if (state == ZanpakutoState.SEALED || state == ZanpakutoState.BASE) {
			// sheathed: the draw key (or a right click); drawn base form: the release key (no abilities before shikai)
			Text hint = state == ZanpakutoState.SEALED
					? Text.translatable("hud.reiatsu_test.hint.draw", ReiatsuKeys.DRAW.getBoundKeyLocalizedText())
					: Text.translatable("hud.reiatsu_test.hint.release", ReiatsuKeys.RELEASE.getBoundKeyLocalizedText());
			ctx.drawText(font, hint, x, y + BAR_H + 3, 0xFFCCCCCC, true);
			return;
		}

		// cooldown boxes: three slots with the ability name, key and remaining seconds
		int boxW = 74;
		int boxH = 24;
		int gap = 4;
		int total = 3 * boxW + 2 * gap;
		int bx0 = w / 2 - total / 2;
		int by = y - 10 - boxH - 3;
		int shake = nowMs < shakeUntil ? (int) Math.round(Math.sin(nowMs / 25.0) * 2.0) : 0;
		for (int slot = 0; slot < 3; slot++) {
			AbilityId a = AbilityId.forSlot(character, state, slot);
			int bx = bx0 + slot * (boxW + gap) + shake;
			boolean usable = a != null && CFG.spec(a).enabled();
			ctx.fill(bx, by, bx + boxW, by + boxH, usable ? 0xCC10101A : 0x66000000);
			ctx.drawBorder(bx, by, boxW, boxH, usable ? fillColor : 0xFF555555);
			Text key = ReiatsuKeys.SLOTS[slot].getBoundKeyLocalizedText();
			ctx.drawText(font, key, bx + 3, by + 3, 0xFFFFFF55, true);
			if (!usable) {
				ctx.drawText(font, Text.translatable("hud.reiatsu_test.no_ability"), bx + 3, by + 13, 0xFF777777, false);
				continue;
			}
			Text name = Text.translatable("ability.reiatsu_test." + a.commandId);
			String shown = font.trimToWidth(name.getString(), boxW - 6);
			ctx.drawText(font, shown, bx + 3, by + 13, 0xFFEEEEEE, false);
			int remaining = ClientState.cooldownRemaining(a);
			int cost = CFG.spec(a).costTenths();
			boolean poor = r != null && r.value() - cost < 1;
			if (remaining > 0) {
				int total2 = CFG.spec(a).cooldownTicks();
				int dark = (int) Math.round((boxH - 2) * Math.min(1.0, (double) remaining / total2));
				ctx.fill(bx + 1, by + boxH - 1 - dark, bx + boxW - 1, by + boxH - 1, 0xAA000000);
				String secs = String.format(java.util.Locale.ROOT, "%.1f", remaining / 20.0);
				ctx.drawText(font, secs, bx + boxW - 3 - font.getWidth(secs), by + 3, 0xFFFFFFFF, true);
			} else if (poor) {
				ctx.fill(bx + 1, by + 1, bx + boxW - 1, by + boxH - 1, 0x66AA2020);
			}
		}
	}
}
