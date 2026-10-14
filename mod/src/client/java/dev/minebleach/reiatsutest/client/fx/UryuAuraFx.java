package dev.minebleach.reiatsutest.client.fx;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Per player state visuals of Uryu that are not tied to one ability: the Focus stacks (URYU_VFX_STORYBOARD 2.4, filled by U-FX 2), the
 * Quincy shunpo look (2.15, U-FX 3) and later the bankai state aura (2.11). Today: the Focus bookkeeping (own stacks for the HUD, the
 * stacks of other players sent to the trackers by S-U2 for the future Focus feedback) and the old Reishi trail and platform discs of the
 * shunpo, unchanged. Called by {@link UryuFx}.
 */
final class UryuAuraFx {
	/** Stacks of Focus of OTHER Uryu players (entity id to 1..3), from the FOCUS packets that carry a caster id (S-U2). */
	private static final Map<Integer, Integer> OTHERS = new HashMap<>();

	private UryuAuraFx() {
	}

	static void init() {
		// nothing yet (U-FX 2 and 3 add their per frame sources here)
	}

	/** The FOCUS packet: {@code ids = {stacks, casterId}}; an older packet without caster id is the owner's own count. */
	static void onFocus(int[] ids) {
		int stacks = ids.length > 0 ? Math.max(0, Math.min(3, ids[0])) : 0;
		ClientPlayerEntity me = MinecraftClient.getInstance().player;
		if (ids.length < 2 || me != null && ids[1] == me.getId()) {
			UryuFx.focus = stacks;
			return;
		}
		if (stacks <= 0) {
			OTHERS.remove(ids[1]);
		} else {
			OTHERS.put(ids[1], stacks);
		}
	}

	/** Stacks of Focus of another player (0 when none was sent). */
	static int focusOf(int entityId) {
		return OTHERS.getOrDefault(entityId, 0);
	}

	/** Per frame: nothing of another player's Focus stays when there is no world (disconnect). */
	static void frame() {
		if (!OTHERS.isEmpty() && MinecraftClient.getInstance().world == null) {
			OTHERS.clear();
		}
	}

	/** The Uryu shunpo: a blue Reishi trail along the path and a Reishi platform disc under the feet at the start and the arrival. */
	static void shunpoReishi(Vec3d start, Vec3d delta) {
		double len = delta.length();
		if (len > 0.5) {
			Vec3d u = delta.multiply(1.0 / len);
			double life = 0.4;
			for (int i = 0; i < 3; i++) {
				double y = start.y + 0.3 + i * 0.6;
				FxShapes.Strip s = FxShapes.strip(start.x, y, start.z, u.x, u.y, u.z, 0).length(a -> len).width(a -> 0.34 * Math.max(0.0, 1.0 - a / life));
				s.color("#7FD0FF").life(life).intensity(a -> 0.9 * Math.max(0.0, 1.0 - a / life));
			}
			FxShapes.Strip core = FxShapes.strip(start.x, start.y + 0.9, start.z, u.x, u.y, u.z, 0).length(a -> len).width(a -> 0.12);
			core.color("#FFFFFF").life(life).intensity(a -> Math.max(0.0, 1.0 - a / life));
		}
		disc(start.x, start.y, start.z);
		disc(start.x + delta.x, start.y + delta.y, start.z + delta.z);
	}

	/** A Reishi platform: a pale blue disc with a thin rim and a soft glow, fading over 0.7 s. */
	private static void disc(double x, double y, double z) {
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(x, y + 0.04, z).ground().size(2.6, 3.0).life(0.7).color(UryuFx.GLOW).peak(0.55).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(x, y + 0.06, z).ground().size(1.6, 2.6).life(0.7).color(UryuFx.PALE).peak(0.9).rot(0.3, 1.2).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(x, y + 0.05, z).ground().size(1.0, 2.2).life(0.5).color("#FFFFFF").peak(0.5).spawn();
	}
}
