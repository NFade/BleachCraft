package dev.minebleach.reiatsutest.client;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.ModItems;
import dev.minebleach.reiatsutest.registry.data.CooldownData;
import dev.minebleach.reiatsutest.registry.data.ReiatsuData;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Read-only view of the synced attachments for the HUD and the key handler, plus the client-side clock that turns the
 * owner-only cooldown values (remaining ticks at sync time) into a countdown.
 */
public final class ClientState {
	private static ClientPlayerEntity lastPlayer;
	private static long ticks;
	private static CooldownData lastCooldowns;
	private static long cooldownsReceivedAt;
	private static ZanpakutoState lastState = ZanpakutoState.SEALED;
	private static long stateChangedAt;

	private ClientState() {
	}

	/** END_CLIENT_TICK. */
	public static void tick(MinecraftClient client) {
		ClientPlayerEntity p = client.player;
		if (p != lastPlayer) {
			lastPlayer = p;
			lastCooldowns = null;
			lastState = ZanpakutoState.SEALED;
		}
		ticks++;
		if (p == null) {
			return;
		}
		CooldownData cd = p.getAttached(ModAttachments.COOLDOWNS);
		if (cd != lastCooldowns) { // a new object means a new sync packet
			lastCooldowns = cd;
			cooldownsReceivedAt = ticks;
		}
		ZanpakutoState st = zanpakuto().zanpakutoState();
		if (st != lastState) {
			lastState = st;
			stateChangedAt = ticks;
		}
	}

	public static long clientTicks() {
		return ticks;
	}

	/** Synced state; SEALED until the first sync arrives. */
	public static ZanpakutoData zanpakuto() {
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		ZanpakutoData z = p == null ? null : p.getAttached(ModAttachments.ZANPAKUTO);
		return z == null ? ZanpakutoData.SEALED : z;
	}

	/** Synced reiatsu or null before the first sync. */
	public static ReiatsuData reiatsu() {
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		return p == null ? null : p.getAttached(ModAttachments.REIATSU);
	}

	public static CharacterId heldCharacter() {
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		return p == null ? CharacterId.NONE : ModItems.characterOf(p.getMainHandStack());
	}

	/** Remaining cooldown of an ability in ticks (0 = ready). */
	public static int cooldownRemaining(AbilityId id) {
		if (lastCooldowns == null) {
			return 0;
		}
		return Math.max(0, lastCooldowns.remaining(id) - (int) (ticks - cooldownsReceivedAt));
	}

	/** Client ticks since the synced state last changed. */
	public static long ticksInState() {
		return ticks - stateChangedAt;
	}
}
