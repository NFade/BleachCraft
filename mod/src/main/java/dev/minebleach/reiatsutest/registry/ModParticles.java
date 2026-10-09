package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** The six custom billboard particle types of the VFX storyboard (1.3.1). Sprites live in textures/particle/. */
public final class ModParticles {
	public static final SimpleParticleType FROST_MOTE = reg("frost_mote");
	public static final SimpleParticleType SNOWFLAKE = reg("snowflake");
	public static final SimpleParticleType ICE_SHARD = reg("ice_shard");
	public static final SimpleParticleType PETAL = reg("petal");
	public static final SimpleParticleType REIATSU_WISP = reg("reiatsu_wisp");
	public static final SimpleParticleType DUST_PUFF = reg("dust_puff");

	private ModParticles() {
	}

	private static SimpleParticleType reg(String name) {
		return Registry.register(Registries.PARTICLE_TYPE, ReiatsuTest.id(name), FabricParticleTypes.simple());
	}

	public static void init() {
		// class load registers the types
	}
}
