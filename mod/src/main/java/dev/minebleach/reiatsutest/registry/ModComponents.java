package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModComponents {
	public static final ComponentType<ReleaseState> RELEASE_STATE = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			ReiatsuTest.id("release_state"),
			ComponentType.<ReleaseState>builder()
					.codec(ReleaseState.CODEC)
					.packetCodec(ReleaseState.PACKET_CODEC)
					.build());

	private ModComponents() {
	}

	public static void init() {
		// class load registers the component
	}
}
