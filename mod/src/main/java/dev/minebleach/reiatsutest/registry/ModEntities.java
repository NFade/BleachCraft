package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.entity.FxAnchorEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Entity types: only the effect anchor (ADR section 2). */
public final class ModEntities {
	public static final EntityType<FxAnchorEntity> FX_ANCHOR = Registry.register(Registries.ENTITY_TYPE, ReiatsuTest.id("fx_anchor"),
			EntityType.Builder.<FxAnchorEntity>create(FxAnchorEntity::new, SpawnGroup.MISC).dimensions(0.5f, 0.5f)
					.maxTrackingRange(10).trackingTickInterval(20).disableSaving().disableSummon().build("fx_anchor"));

	private ModEntities() {
	}

	public static void init() {
		// class load registers the entity type
	}
}
