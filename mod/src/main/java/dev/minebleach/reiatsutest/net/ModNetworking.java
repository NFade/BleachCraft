package dev.minebleach.reiatsutest.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Payload type registration (common init, both sides). Receivers are registered by server/ and client/. */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.playC2S().register(RequestTransitionC2S.ID, RequestTransitionC2S.CODEC);
		PayloadTypeRegistry.playC2S().register(CastAbilityC2S.ID, CastAbilityC2S.CODEC);
		PayloadTypeRegistry.playS2C().register(ActionResultS2C.ID, ActionResultS2C.CODEC);
		PayloadTypeRegistry.playS2C().register(EffectEventS2C.ID, EffectEventS2C.CODEC);
		PayloadTypeRegistry.playS2C().register(EntityFxS2C.ID, EntityFxS2C.CODEC);
	}
}
