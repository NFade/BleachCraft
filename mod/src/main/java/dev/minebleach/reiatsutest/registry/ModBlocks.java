package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.block.FrostSprigBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

/**
 * The frost blocks of the Rukia bankai territory freeze (round 6). They only exist while a field stands: the server replaces
 * surface blocks with them and puts the originals back when the bankai ends (server/FrostField). All of them are plain blocks with
 * the DEFAULT friction (slipperiness 0.6, like stone): nothing here calls {@code slipperiness(...)} and nothing uses vanilla
 * ice, so a frozen field never makes the player slide. They have no items, drop nothing, cannot be mined and resist explosions and
 * pistons, so the original block cannot be lost; creative mode can still break them (the restore treats an air cell as free).
 */
public final class ModBlocks {
	/** Ground: grass, dirt, sand, gravel, mud ... (frost on top, an icicle fringe on the sides). */
	public static final Block FROST_CRUST = register("frost_crust", new Block(ground(MapColor.PALE_PURPLE, BlockSoundGroup.AMETHYST_BLOCK)));
	/** Rock: stone, cobblestone, deepslate, sandstone ... */
	public static final Block FROST_STONE = register("frost_stone", new Block(ground(MapColor.LIGHT_BLUE_GRAY, BlockSoundGroup.STONE)));
	/** Leaves (cut out texture); collision stays full like real leaves. */
	public static final Block FROST_LEAVES = register("frost_leaves", new Block(ground(MapColor.LIGHT_BLUE, BlockSoundGroup.AMETHYST_CLUSTER)
			.nonOpaque().allowsSpawning((state, world, pos, type) -> false).suffocates((state, world, pos) -> false)
			.blockVision((state, world, pos) -> false)));
	/** Thin ice on a water surface (translucent, solid to walk on, standard friction). */
	public static final Block FROST_FILM = register("frost_film", new Block(ground(MapColor.LIGHT_BLUE, BlockSoundGroup.GLASS)
			.nonOpaque().allowsSpawning((state, world, pos, type) -> false).suffocates((state, world, pos) -> false)
			.blockVision((state, world, pos) -> false)));
	/** Frozen grass, ferns and flowers: no collision. */
	public static final Block FROST_SPRIG = register("frost_sprig", new FrostSprigBlock(ground(MapColor.WHITE, BlockSoundGroup.AMETHYST_CLUSTER)
			.noCollision().nonOpaque().allowsSpawning((state, world, pos, type) -> false).suffocates((state, world, pos) -> false)
			.blockVision((state, world, pos) -> false)));

	private ModBlocks() {
	}

	private static AbstractBlock.Settings ground(MapColor color, BlockSoundGroup sounds) {
		// default friction on purpose: no slipperiness(...) call (0.6, the stone value). Unbreakable and drop nothing.
		return AbstractBlock.Settings.create().mapColor(color).sounds(sounds).strength(-1.0F, 3600000.0F).dropsNothing()
				.pistonBehavior(PistonBehavior.BLOCK);
	}

	private static Block register(String id, Block block) {
		return Registry.register(Registries.BLOCK, ReiatsuTest.id(id), block);
	}

	public static void init() {
		// class load registers the blocks
	}

	/** True for the five frost blocks. */
	public static boolean isFrost(Block b) {
		return b == FROST_CRUST || b == FROST_STONE || b == FROST_LEAVES || b == FROST_FILM || b == FROST_SPRIG;
	}
}
