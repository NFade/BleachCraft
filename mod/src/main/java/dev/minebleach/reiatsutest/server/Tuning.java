package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

/** Server-side gameplay switches and limits that are not part of the pure balance config (STATE_MACHINE section 4). */
public final class Tuning {
	/** Abilities never hurt other players unless this is true (and the server allows PvP). */
	public static volatile boolean affectPlayers = false;
	/** Alive temporary blocks per player. */
	public static final int MAX_TEMP_BLOCKS_PER_PLAYER = 128;
	/** Entities with this tag are never hit: data/reiatsu_test/tags/entity_type/immune.json. */
	public static final TagKey<EntityType<?>> IMMUNE = TagKey.of(RegistryKeys.ENTITY_TYPE, ReiatsuTest.id("immune"));

	private Tuning() {
	}
}
