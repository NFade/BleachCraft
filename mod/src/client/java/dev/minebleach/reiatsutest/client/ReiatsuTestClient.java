package dev.minebleach.reiatsutest.client;

import dev.minebleach.reiatsutest.client.dev.DevWindowPlacement;
import dev.minebleach.reiatsutest.client.dev.Phase4Harness;
import dev.minebleach.reiatsutest.client.hud.ReiatsuHud;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.client.model.ObjModelPlugin;
import dev.minebleach.reiatsutest.client.net.ClientNet;
import dev.minebleach.reiatsutest.client.spike.SpikeHarness;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class ReiatsuTestClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientOptions.load();
		DevWindowPlacement.init();
		ObjModelPlugin.register();
		ClientNet.register();
		ReiatsuKeys.init();
		ReiatsuHud.init();
		ClientTickEvents.END_CLIENT_TICK.register(ClientState::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientNet.onJoin());
		// Dev-only visual harnesses: active ONLY with -Dreiatsu.spike=true / -Dreiatsu.phase4=true (Gradle tasks
		// runSpike / runPhase4). Normal runs never touch them.
		if (Boolean.getBoolean("reiatsu.spike")) {
			SpikeHarness.init();
		}
		if (Boolean.getBoolean("reiatsu.phase4")) {
			Phase4Harness.init();
		}
	}
}
