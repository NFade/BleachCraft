package dev.minebleach.reiatsutest.client;

import dev.minebleach.reiatsutest.client.dev.DevWindowPlacement;
import dev.minebleach.reiatsutest.client.fx.FxClient;
import dev.minebleach.reiatsutest.client.dev.Phase4Harness;
import dev.minebleach.reiatsutest.client.dev.Phase5Harness;
import dev.minebleach.reiatsutest.client.dev.Phase6Harness;
import dev.minebleach.reiatsutest.client.hud.ReiatsuHud;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.client.model.DrawTracker;
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
		dev.minebleach.reiatsutest.client.model.TpConfig.load(); // R11 T10: tp.swordScale / tp.guard.shift of the client config
		// frost blocks of the Rukia bankai territory freeze (round 6): cut out leaves and sprigs, translucent thin ice
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(net.minecraft.client.render.RenderLayer.getCutout(),
				dev.minebleach.reiatsutest.registry.ModBlocks.FROST_LEAVES, dev.minebleach.reiatsutest.registry.ModBlocks.FROST_SPRIG);
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(dev.minebleach.reiatsutest.registry.ModBlocks.FROST_FILM,
				net.minecraft.client.render.RenderLayer.getTranslucent());
		// the ice wall of the Rukia bankai (R11 T5): a translucent full block
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(dev.minebleach.reiatsutest.registry.ModBlocks.ICE_WALL,
				net.minecraft.client.render.RenderLayer.getTranslucent());
		FxClient.init();
		DevWindowPlacement.init();
		ObjModelPlugin.register();
		dev.minebleach.reiatsutest.client.model.ScabbardFeature.register();
		dev.minebleach.reiatsutest.client.model.TpPoseLayer.register();
		dev.minebleach.reiatsutest.client.model.FpMotion.init();
		ClientNet.register();
		BcGrassSwing.init();
		dev.minebleach.reiatsutest.client.voice.VoiceBridgeClient.init();
		ReiatsuKeys.init();
		ReiatsuHud.init();
		dev.minebleach.reiatsutest.client.book.SpellbookScreen.register();
		ClientTickEvents.END_CLIENT_TICK.register(ClientState::tick);
		ClientTickEvents.END_CLIENT_TICK.register(DrawTracker::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientNet.onJoin());
		// Dev-only visual harnesses: active ONLY with -Dreiatsu.spike=true / -Dreiatsu.phase4=true (Gradle tasks
		// runSpike / runPhase4). Normal runs never touch them.
		if (Boolean.getBoolean("reiatsu.spike")) {
			SpikeHarness.init();
		}
		if (Boolean.getBoolean("reiatsu.phase4")) {
			Phase4Harness.init();
		}
		if (Boolean.getBoolean("reiatsu.phase5")) {
			Phase5Harness.init();
		}
		if (Boolean.getBoolean("reiatsu.phase6")) {
			Phase6Harness.init();
		}
	}
}
