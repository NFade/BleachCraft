package dev.minebleach.reiatsutest;

import dev.minebleach.reiatsutest.net.ModNetworking;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.ModComponents;
import dev.minebleach.reiatsutest.registry.ModEntities;
import dev.minebleach.reiatsutest.registry.ModParticles;
import dev.minebleach.reiatsutest.registry.ModItems;
import dev.minebleach.reiatsutest.server.ReiatsuCommand;
import dev.minebleach.reiatsutest.server.VoiceControl;
import dev.minebleach.reiatsutest.server.ZanpakutoManager;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReiatsuTest implements ModInitializer {
	public static final String MOD_ID = "reiatsu_test";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModItems.init();
		ModAttachments.init();
		ModParticles.init();
		ModEntities.init();
		ModNetworking.registerPayloads();
		ZanpakutoManager.init();
		ReiatsuCommand.register();
		VoiceControl.init();
		LOGGER.info("registered items {}, {}, {} (component {}), attachments and payloads",
				ModItems.SODE_NO_SHIRAYUKI, ModItems.SENBONZAKURA, ModItems.SPIKE_ITEM, ModComponents.RELEASE_STATE);
	}
}
