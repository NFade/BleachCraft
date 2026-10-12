package dev.minebleach.reiatsutest.client.input;

import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.client.fx.FxTune;
import dev.minebleach.reiatsutest.client.net.ClientNet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.Vec3d;

/**
 * Steering of the Senbonzakura attack swarm with the cast key. The key that cast mode_attack is remembered
 * ({@link #arm}); once the server accepts the cast ({@link #onAccepted}, from the action_result) and the key is still down, the
 * client sends the look direction every {@code steer.sendTicks} ticks (default 2 = 10 Hz, fx_override.json) while the key stays
 * down, and one release packet when it comes up (or a screen opens). The server decides everything: it validates the packets,
 * limits the turn and speed of the swarm and ignores them when no flight is running (see {@code server/BladeFlights}).
 */
public final class BladeSteer {
	/** The client stops sending after this many ticks whatever the key does (the server flight is shorter). */
	private static final int MAX_TICKS = 100;

	private static KeyBinding pendingKey;
	private static KeyBinding key;
	private static boolean active;
	private static boolean sentHold;
	private static int ticks;

	/** Harness/diagnostics: packets sent since start. */
	public static int sent;

	private BladeSteer() {
	}

	/** The mode_attack cast was just requested with this key (null for casts that did not come from a key). */
	static void arm(KeyBinding k) {
		pendingKey = k;
	}

	/** The server accepted a mode_attack cast. */
	public static void onAccepted() {
		if (pendingKey == null) {
			return;
		}
		key = pendingKey;
		pendingKey = null;
		active = true;
		sentHold = false;
		ticks = 0;
	}

	public static boolean active() {
		return active;
	}

	/** Every client tick. */
	static void tick(MinecraftClient mc) {
		if (!active) {
			return;
		}
		if (mc.player == null || mc.world == null) {
			reset();
			return;
		}
		boolean held = key != null && key.isPressed() && mc.currentScreen == null && ticks < MAX_TICKS;
		Vec3d look = mc.player.getRotationVec(1.0F);
		int every = Math.max(1, (int) Math.round(FxTune.d("steer.sendTicks", 2)));
		if (held) {
			if (!sentHold || ticks % every == 0) {
				ClientNet.sendSteer(true, look.x, look.y, look.z);
				sentHold = true;
				sent++;
			}
			ClientState.setSteering(true); // the conducting hand gesture (FpCast) while the swarm is steered
			ticks++;
			return;
		}
		if (sentHold) {
			ClientNet.sendSteer(false, look.x, look.y, look.z);
			sent++;
		}
		reset();
	}

	private static void reset() {
		ClientState.setSteering(false);
		active = false;
		sentHold = false;
		key = null;
		ticks = 0;
	}
}
