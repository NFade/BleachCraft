package dev.minebleach.reiatsutest.client.fx;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * The Focus stacks of the Uryu players on this client and (later in this file) the Focus feedback on their bodies (URYU_VFX_STORYBOARD 2.4).
 * Called through {@link UryuAuraFx}. First step: only the bookkeeping that the HUD and the vein overlay read.
 */
final class UryuFocusFx {
	/** Stacks of Focus of OTHER Uryu players (entity id to 1..3), from the FOCUS packets that carry a caster id (S-U2). */
	private static final Map<Integer, Integer> OTHERS = new HashMap<>();

	private UryuFocusFx() {
	}

	static void init() {
		// per frame sources are added by the feedback part
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

	/** Stacks of Focus of a player (the local one too; 0 when none was sent). */
	static int stacksOf(int entityId) {
		ClientPlayerEntity me = MinecraftClient.getInstance().player;
		if (me != null && me.getId() == entityId) {
			return UryuFx.focus;
		}
		return OTHERS.getOrDefault(entityId, 0);
	}

	/** Per frame: nothing of another player's Focus stays when there is no world (disconnect). */
	static void cleanup() {
		if (!OTHERS.isEmpty() && MinecraftClient.getInstance().world == null) {
			OTHERS.clear();
		}
	}
}
