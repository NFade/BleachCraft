package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

/** Server-side gameplay switches and limits that are not part of the pure balance config (STATE_MACHINE section 4). */
public final class Tuning {
	/**
	 * Abilities hurt other players when this is true AND the server has pvp=true. Default true (it was false before 2026-10-12, which
	 * silently excluded every player target; dmg_rage_round3). {@code /reiatsu pvp false} turns it off for tests.
	 */
	public static volatile boolean affectPlayers = true;
	/** Alive temporary blocks per player. */
	public static final int MAX_TEMP_BLOCKS_PER_PLAYER = 128;
	/** Entities with this tag are never hit: data/reiatsu_test/tags/entity_type/immune.json. */
	public static final TagKey<EntityType<?>> IMMUNE = TagKey.of(RegistryKeys.ENTITY_TYPE, ReiatsuTest.id("immune"));

	private Tuning() {
	}
}
