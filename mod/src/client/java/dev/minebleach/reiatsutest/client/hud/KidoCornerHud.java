package dev.minebleach.reiatsutest.client.hud;

import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.client.fx.FxMath;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.hud.SideCellMath;
import dev.minebleach.reiatsutest.core.hud.SideHudLayout;
import dev.minebleach.reiatsutest.core.kido.KidoConfig;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.kido.KidoSpec;
import net.minecraft.client.font.TextRenderer;

/**
 * The three kido cells (Byakurai K, Shakkaho N, Sai M) in the bottom left corner: icon, key letter, radial cooldown sweep with
 * whole seconds, red flash when the cast was refused for lack of reiatsu, shake and gold seconds on a cooldown refusal. Kido work
 * in every state, so the cells follow the plate rule (shown whenever a zanpakuto is in hand or the state is not sealed), they do not
 * need shikai. Numbers come from the default {@link KidoConfig} (the server config can differ; the sweep then stretches to the largest
 * remaining value it has seen).
 */
final class KidoCornerHud {
	private static final KidoConfig CFG = KidoConfig.defaults();
	private static final float[][] ACCENT = {FxMath.hex("#CFEFFF"), FxMath.hex("#FF9A6A"), FxMath.hex("#E8C66A")};
	private static final SideHudIcons.Icon[] ICON = {SideHudIcons.BYAKURAI, SideHudIcons.SHAKKAHO, SideHudIcons.SAI};
	static final SideCell[] CELLS = {new SideCell(), new SideCell(), new SideCell()};

	private KidoCornerHud() {
	}

	static void reset() {
		for (SideCell c : CELLS) {
			c.reset();
		}
	}

	static void draw(HudGfx g, TextRenderer font, SideHudLayout.Result layout, boolean visible, float alpha) {
		for (int i = 0; i < 3; i++) {
			KidoId id = KidoId.forSlot(i);
			KidoSpec spec = id == null ? null : CFG.spec(id);
			SideCell cell = CELLS[i];
			int rem = id == null ? 0 : ClientState.cooldownRemaining(id);
			cell.update(rem, HudModel.dt, visible);
			if (!visible || spec == null) {
				continue;
			}
			boolean disabled = !spec.enabled() || !CFG.enabled();
			boolean poor = HudModel.value - spec.costTenths() < 1;
			float[] tint = FxMath.mix(HudAssets.STEEL, ACCENT[i], 0.5);
			String key = SideCellMath.keyLetter(keyName(i));
			cell.draw(g, font, layout.kido().get(i),
					new SideCell.View(ICON[i], tint, key, rem, cell.total(spec.cooldownTicks()), poor, disabled, false, alpha));
		}
	}

	private static String keyName(int slot) {
		try {
			return ReiatsuKeys.KIDO[slot].getBoundKeyLocalizedText().getString();
		} catch (RuntimeException e) {
			return "";
		}
	}
}
