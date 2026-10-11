package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.combat.BcAttackGate;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import net.bettercombat.api.CombatFlags;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * BC5: switches the Better Combat strike off through BC's own API flag ({@link CombatFlags#setAttacksDisabled}) while the sword is
 * sheathed or a block is held ({@link BcAttackGate}). The flag lives in the player's data tracker, so the client (which gates
 * {@code doAttack} / the upswing on it) and the server (which gates the attack packet on it) see the same value. It only touches
 * BC's API bit; the {@code bettercombat_disabled} command tag bit is left alone. Checked every server tick; the data tracker
 * sends a packet only when the value changes.
 */
public final class BcCombatGate {
	private BcCombatGate() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			for (ServerPlayerEntity p : srv.getPlayerManager().getPlayerList()) {
				update(p);
			}
		});
	}

	private static void update(ServerPlayerEntity p) {
		boolean holds = p.getMainHandStack().getItem() instanceof ZanpakutoItem;
		ZanpakutoData z = p.getAttached(ModAttachments.ZANPAKUTO);
		ZanpakutoState st = z == null ? ZanpakutoState.SEALED : z.zanpakutoState();
		boolean want = BcAttackGate.attacksDisabled(holds, st, ZanpakutoItem.isBlocking(p));
		boolean has = (CombatFlags.get(p) & CombatFlags.API_DISABLED) != 0;
		if (want != has) {
			CombatFlags.setAttacksDisabled(p, want);
		}
	}
}
