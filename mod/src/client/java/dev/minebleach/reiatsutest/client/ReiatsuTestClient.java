package dev.minebleach.reiatsutest.client;

import dev.minebleach.reiatsutest.client.model.ObjModelPlugin;
import dev.minebleach.reiatsutest.client.spike.SpikeHarness;
import net.fabricmc.api.ClientModInitializer;

public class ReiatsuTestClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ObjModelPlugin.register();
		// Dev-only visual harness: active ONLY with -Dreiatsu.spike=true (Gradle task runSpike).
		if (Boolean.getBoolean("reiatsu.spike")) {
			SpikeHarness.init();
		}
	}
}
