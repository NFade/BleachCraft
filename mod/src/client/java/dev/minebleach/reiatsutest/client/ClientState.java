package dev.minebleach.reiatsutest.client;

import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.ModItems;
import dev.minebleach.reiatsutest.registry.data.CooldownData;
import dev.minebleach.reiatsutest.registry.data.RageData;
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
	/** Set by the swarm steering key code while the cast key is held and the swarm is being steered (FP_ABILITY_ANIMS: the conducting hand). */
	private static volatile boolean steering;

	private ClientState() {
	}

	/** END_CLIENT_TICK. */
	public static void tick(MinecraftClient client) {
		ClientPlayerEntity p = client.player;
		if (p != lastPlayer) {
			lastPlayer = p;
			lastCooldowns = null;
			lastState = ZanpakutoState.SEALED;
			steering = false;
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

	/**
	 * True while the player steers the swarm with the held cast key (Byakuya shikai attack). Default false. The hands then hold the
	 * conducting gesture ({@code FpCast}, gesture {@code steer}); the code that handles the key switches it with {@link #setSteering}.
	 */
	public static boolean steering() {
		return steering;
	}

	public static void setSteering(boolean on) {
		steering = on;
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

	/** Synced rage; empty until the first sync arrives. */
	public static RageData rage() {
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		RageData r = p == null ? null : p.getAttached(ModAttachments.RAGE);
		return r == null ? RageData.EMPTY : r;
	}

	/** Rage costs and minimum shikai time of the server config (R11 review fix 4); the code defaults until the first sync arrives. */
	public static dev.minebleach.reiatsutest.core.rage.RageTuning rageTuning() {
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		var d = p == null ? null : p.getAttached(ModAttachments.RAGE_TUNING);
		return d == null ? dev.minebleach.reiatsutest.core.rage.RageTuning.defaults() : d.toTuning();
	}

	/** Synced frost stacks of Rukia (R11 T6); empty until the first sync arrives. */
	public static dev.minebleach.reiatsutest.registry.data.MomentumData momentum() {
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		var d = p == null ? null : p.getAttached(ModAttachments.MOMENTUM);
		return d == null ? dev.minebleach.reiatsutest.registry.data.MomentumData.EMPTY : d;
	}

	public static CharacterId heldCharacter() {
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		return p == null ? CharacterId.NONE : ModItems.characterOf(p.getMainHandStack());
	}

	/**
	 * The character the local player is playing: the released one while a state is active, else the one of the item in the main hand
	 * (NONE with neither). Used for the per character wording and for the kido rule.
	 */
	public static CharacterId activeCharacter() {
		CharacterId released = zanpakuto().characterId();
		return released != CharacterId.NONE ? released : heldCharacter();
	}

	/** Remaining cooldown of an ability in ticks (0 = ready). */
	public static int cooldownRemaining(AbilityId id) {
		if (lastCooldowns == null) {
			return 0;
		}
		return Math.max(0, lastCooldowns.remaining(id) - (int) (ticks - cooldownsReceivedAt));
	}

	/** Remaining cooldown of a kido spell in ticks (0 = ready). */
	public static int cooldownRemaining(KidoId id) {
		if (lastCooldowns == null) {
			return 0;
		}
		return Math.max(0, lastCooldowns.remaining(id) - (int) (ticks - cooldownsReceivedAt));
	}

	/** Remaining shunpo cooldown in ticks (0 = ready); synced with the other cooldowns (side HUD cell). */
	public static int shunpoCooldownRemaining() {
		if (lastCooldowns == null) {
			return 0;
		}
		return Math.max(0, lastCooldowns.shunpoRemaining() - (int) (ticks - cooldownsReceivedAt));
	}

	/** Client ticks since the synced state last changed. */
	public static long ticksInState() {
		return ticks - stateChangedAt;
	}
}
