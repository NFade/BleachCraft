package dev.minebleach.reiatsutest.client.hud;

import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.client.fx.FxMath;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.hud.SideCellMath;
import dev.minebleach.reiatsutest.core.hud.SideHudLayout;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ShunpoSpec;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import net.minecraft.client.font.TextRenderer;

/**
 * The shunpo cell at the left edge of the screen, vertically centred: icon, key letter (Y) and a vertical cooldown wipe that
 * drains from top to bottom. Shunpo works in every state with any sword or none (StateMachine#shunpo), so the cell follows the
 * plate rule like the kido cells. The frame is tinted by the character (Rukia and Byakuya by their accent while released, steel
 * for the rest); the Quincy flash step of Uryu (Hirenkyaku) uses the same cell with a blue tint and a blue icon. The cooldown comes
 * from the synced cooldown attachment (code 110, {@code CooldownData.SHUNPO_CODE}).
 */
final class ShunpoSideHud {
	private static final float[] URYU_BLUE = FxMath.hex("#7FB2FF");
	static final SideCell CELL = new SideCell();

	private ShunpoSideHud() {
	}

	static void reset() {
		CELL.reset();
	}

	static float[] tint(CharacterId c, ZanpakutoState state) {
		if (c == CharacterId.URYU) {
			return URYU_BLUE;
		}
		return HudAssets.accent(c, state);
	}

	static void draw(HudGfx g, TextRenderer font, SideHudLayout.Result layout, boolean visible, float alpha) {
		int rem = ClientState.shunpoCooldownRemaining();
		CELL.update(rem, HudModel.dt, visible);
		if (!visible) {
			return;
		}
		ShunpoSpec spec = HudModel.cfg().shunpo();
		if (spec == null) {
			return;
		}
		CharacterId who = HudModel.character;
		ShunpoSpec mine = spec.forCharacter(who);
		boolean poor = HudModel.value - mine.costTenths() < 1;
		SideHudIcons.Icon icon = who == CharacterId.URYU ? SideHudIcons.SHUNPO_URYU : SideHudIcons.SHUNPO;
		String key = SideCellMath.keyLetter(keyName());
		CELL.draw(g, font, layout.shunpo(),
				new SideCell.View(icon, tint(who, HudModel.state), key, rem, CELL.total(mine.cooldownTicks()), poor, false, true, alpha));
	}

	private static String keyName() {
		try {
			return ReiatsuKeys.SHUNPO.getBoundKeyLocalizedText().getString();
		} catch (RuntimeException e) {
			return "";
		}
	}
}
