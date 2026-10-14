package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** The custom billboard particle types of the VFX storyboards (1.3.1; the Quincy pair REISHI_MOTE and FLECHETTE of URYU_VFX_STORYBOARD 4.2). Sprites live in textures/particle/. */
public final class ModParticles {
	public static final SimpleParticleType FROST_MOTE = reg("frost_mote");
	public static final SimpleParticleType SNOWFLAKE = reg("snowflake");
	public static final SimpleParticleType ICE_SHARD = reg("ice_shard");
	public static final SimpleParticleType PETAL = reg("petal");
	public static final SimpleParticleType REIATSU_WISP = reg("reiatsu_wisp");
	public static final SimpleParticleType DUST_PUFF = reg("dust_puff");
	/** Uryu: teardrop mote of gathering Reishi (P:RM) and the flake flechette (P:FL). */
	public static final SimpleParticleType REISHI_MOTE = reg("reishi_mote");
	public static final SimpleParticleType FLECHETTE = reg("flechette");

	private ModParticles() {
	}

	private static SimpleParticleType reg(String name) {
		return Registry.register(Registries.PARTICLE_TYPE, ReiatsuTest.id(name), FabricParticleTypes.simple());
	}

	public static void init() {
		// class load registers the types
	}
}
