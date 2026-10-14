package dev.minebleach.reiatsutest.client.hud;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.hud.SideCellMath;
import dev.minebleach.reiatsutest.core.hud.SideHudLayout;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Entry point of the side HUD: the kido corner (bottom left, {@link KidoCornerHud}) and the shunpo cell (left edge, centred,
 * {@link ShunpoSideHud}). {@link ReiatsuHud} calls {@link #draw} once per frame after the strip; {@link dev.minebleach.reiatsutest.client.net.ClientNet}
 * tells it which request a result belongs to. Nothing here can throw into the render loop: every synced value has a default and
 * the whole draw is guarded. Hidden while the chat input is open (its field covers the bottom left).
 */
public final class SideHud {
	/** Cells index for the pending requests: 0 to 2 kido slots, 3 shunpo. */
	private static final int SHUNPO_CELL = 3;
	private static final Map<Integer, Integer> PENDING = new HashMap<>();
	private static ClientPlayerEntity lastPlayer;
	private static boolean failedOnce;

	private SideHud() {
	}

	private static boolean visible(MinecraftClient mc) {
		return HudModel.plateWanted && HudModel.plateAlpha > 0.02 && !(mc.currentScreen instanceof ChatScreen);
	}

	/**
	 * @param topReserve y of the bottom of the top left block (plate, voice row, denied toast); the shunpo cell stays below it
	 */
	static void draw(HudGfx g, MinecraftClient mc, TextRenderer font, int w, int h, int topReserve) {
		try {
			if (mc.player != lastPlayer) {
				lastPlayer = mc.player;
				KidoCornerHud.reset();
				ShunpoSideHud.reset();
				PENDING.clear();
			}
			boolean vis = visible(mc);
			SideHudLayout.Result layout = SideHudLayout.compute(w, h, SideHudLayout.compact(FxConfig.hudCompact, w), topReserve);
			float alpha = (float) Math.min(1.0, HudModel.plateAlpha);
			KidoCornerHud.draw(g, font, layout, vis, alpha);
			ShunpoSideHud.draw(g, font, layout, vis, alpha);
		} catch (RuntimeException e) {
			if (!failedOnce) {
				failedOnce = true;
				ReiatsuTest.LOGGER.error("[hud] side cells failed, skipping them from now on this frame", e);
			}
		}
	}

	/** hud=false: one plain text line at the bottom left, below the chat lines: K 2.5  N -  M -  Y 1. */
	public static void renderLegacy(DrawContext ctx, MinecraftClient mc) {
		try {
			if (mc.player == null || mc.options.hudHidden || ReiatsuHud.devHidden || mc.currentScreen instanceof ChatScreen) {
				return;
			}
			ZanpakutoState state = ClientState.zanpakuto().zanpakutoState();
			if (state == ZanpakutoState.SEALED && ClientState.heldCharacter() == CharacterId.NONE) {
				return;
			}
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 3; i++) {
				KidoId id = KidoId.forSlot(i);
				sb.append(SideCellMath.keyLetter(ReiatsuKeys.KIDO[i].getBoundKeyLocalizedText().getString())).append(' ')
						.append(label(id == null ? 0 : ClientState.cooldownRemaining(id))).append("  ");
			}
			sb.append(SideCellMath.keyLetter(ReiatsuKeys.SHUNPO.getBoundKeyLocalizedText().getString())).append(' ')
					.append(label(ClientState.shunpoCooldownRemaining()));
			TextRenderer font = mc.textRenderer;
			int w = ctx.getScaledWindowWidth();
			int h = ctx.getScaledWindowHeight();
			String s = sb.toString();
			int room = w / 2 - SideHudLayout.HOTBAR_HALF - SideHudLayout.OFFHAND - 8;
			if (font.getWidth(s) <= room) {
				ctx.drawText(font, s, 6, h - 18, 0xFFE8ECF4, true);
			}
		} catch (RuntimeException e) {
			if (!failedOnce) {
				failedOnce = true;
				ReiatsuTest.LOGGER.error("[hud] legacy side line failed", e);
			}
		}
	}

	private static String label(int remaining) {
		String s = SideCellMath.secondsLabel(remaining);
		return s == null ? "-" : s;
	}

	// ------------------------------------------------------------------------------------------ denied feedback

	public static void noteKido(int seq, KidoId kido) {
		if (kido != null) {
			remember(seq, kido.slot);
		}
	}

	public static void noteShunpo(int seq) {
		remember(seq, SHUNPO_CELL);
	}

	private static void remember(int seq, int cell) {
		PENDING.put(seq, cell);
		if (PENDING.size() > 32) {
			PENDING.keySet().removeIf(k -> k < seq - 16);
		}
	}

	/** The server answered request {@code seq}: shakes, flashes and gold seconds on the cell it was for. */
	public static void onResult(int seq, ResultCode code) {
		Integer cell = PENDING.remove(seq);
		if (cell == null || code == null) {
			return;
		}
		SideCell c = cell == SHUNPO_CELL ? ShunpoSideHud.CELL : cell >= 0 && cell < KidoCornerHud.CELLS.length ? KidoCornerHud.CELLS[cell] : null;
		if (c == null) {
			return;
		}
		double now = HudModel.now;
		switch (code) {
			case COOLDOWN -> {
				c.shake = now + 0.3;
				c.flashGold = now + 0.3;
			}
			case DENIED_REIATSU -> c.flashRed = now + 0.4;
			case BLOCKED, DENIED_STATE, DENIED_ITEM, DENIED_NOT_DRAWN -> c.flashGrey = now + 0.2;
			default -> {
			}
		}
	}
}
