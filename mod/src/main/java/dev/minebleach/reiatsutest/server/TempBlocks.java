package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.world.TempBlockJournal.Entry;
import dev.minebleach.reiatsutest.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Temporary ice and snow with a rollback journal (no permanent world change). Only air, a single snow layer or still
 * water is ever replaced; every placement records the original state and is restored on timeout, seal, death, logout,
 * dimension change, server stop and (via the persisted journal) after a crash.
 *
 * <p>R11 T5: the cells of the Rukia ice wall use the same journal ({@link #placeWall}): they may also replace replaceable plants (grass, ferns,
 * flowers, dead bushes, snow layers, vines ...), whose full state is kept as a serialized string ({@code state:<block state>}) and put back
 * on restore. The wall blocks are placed and restored without neighbour updates, so a half of a tall plant never pops off.
 */
public final class TempBlocks {
	private static TempBlockState state;

	private TempBlocks() {
	}

	/** SERVER_STARTED: load the journal and roll back anything left from an earlier run. */
	public static void onServerStarted(MinecraftServer server) {
		state = server.getOverworld().getPersistentStateManager().getOrCreate(TempBlockState.TYPE, TempBlockState.ID);
		List<Entry> leftovers = state.journal.takeAll();
		for (Entry e : leftovers) {
			restore(server, e);
		}
		if (!leftovers.isEmpty()) {
			state.markDirty();
			ReiatsuTest.LOGGER.info("[tempblocks] rolled back {} leftover temporary block(s) from the previous run", leftovers.size());
		}
	}

	public static void onServerStopping(MinecraftServer server) {
		rollbackAll(server);
		state = null;
	}

	public static int count(UUID owner) {
		return state == null ? 0 : state.journal.count(owner);
	}

	public static int size() {
		return state == null ? 0 : state.journal.size();
	}

	/** Places a temporary ice block (or snow layer when {@code ice} is false) if the cell qualifies. */
	public static boolean place(ServerWorld world, UUID owner, BlockPos pos, boolean ice, long restoreAtTick) {
		if (state == null) {
			return false;
		}
		BlockState cur = world.getBlockState(pos);
		String previous;
		if (cur.isAir()) {
			previous = "air";
		} else if (ice && cur.isOf(Blocks.SNOW)) {
			previous = "snow:" + cur.get(SnowBlock.LAYERS);
		} else if (ice && cur.isOf(Blocks.WATER) && cur.getFluidState().isStill()) {
			previous = "water";
		} else {
			return false;
		}
		if (world.getBlockEntity(pos) != null) {
			return false;
		}
		BlockState target = ice ? Blocks.ICE.getDefaultState() : Blocks.SNOW.getDefaultState();
		if (previous.equals("air") && ice
				&& !world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)) {
			return false; // no floating ice
		}
		if (!ice && !target.canPlaceAt(world, pos)) {
			return false;
		}
		String dim = dimensionOf(world);
		if (!state.journal.canAdd(owner, dim, pos.getX(), pos.getY(), pos.getZ(), Tuning.MAX_TEMP_BLOCKS_PER_PLAYER)) {
			return false;
		}
		state.journal.add(new Entry(owner, dim, pos.getX(), pos.getY(), pos.getZ(), previous, ice ? "ice" : "snow:1", restoreAtTick));
		world.setBlockState(pos, target, Block.NOTIFY_ALL);
		state.markDirty();
		return true;
	}

	/** Update flags of the ice wall: clients and light, no neighbour or shape updates (no popping plants, no water flow). */
	private static final int WALL_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
	private static final String WALL = "ice_wall";

	/** True when a wall block may replace {@code cur}: air, or a replaceable block with no fluid and no block entity (grass, flowers, snow layers ...). */
	public static boolean wallReplaceable(BlockState cur) {
		return cur.isAir() || (cur.isReplaceable() && cur.getFluidState().isEmpty() && !cur.hasBlockEntity());
	}

	/**
	 * Places one ice wall block (R11 T5) with a journal entry. The cell must hold air or a replaceable plant and no block entity; the caller skips
	 * cells with entities. Returns false (nothing changed) when the cell does not qualify or {@code cap} blocks of this owner are already alive.
	 */
	public static boolean placeWall(ServerWorld world, UUID owner, BlockPos pos, long restoreAtTick, int cap) {
		if (state == null) {
			return false;
		}
		BlockState cur = world.getBlockState(pos);
		if (!wallReplaceable(cur) || world.getBlockEntity(pos) != null) {
			return false;
		}
		String dim = dimensionOf(world);
		if (!state.journal.canAdd(owner, dim, pos.getX(), pos.getY(), pos.getZ(), cap)) {
			return false;
		}
		String previous = cur.isAir() ? "air" : "state:" + BlockArgumentParser.stringifyBlockState(cur);
		state.journal.add(new Entry(owner, dim, pos.getX(), pos.getY(), pos.getZ(), previous, WALL, restoreAtTick));
		world.setBlockState(pos, ModBlocks.ICE_WALL.getDefaultState(), WALL_FLAGS);
		state.markDirty();
		return true;
	}

	/** Takes one cell out of the journal and restores it (a wall layer that fades, or a wall that broke). False when the cell is not journaled. */
	public static boolean releaseCell(MinecraftServer server, String dimension, BlockPos pos) {
		if (state == null) {
			return false;
		}
		Entry e = state.journal.take(dimension, pos.getX(), pos.getY(), pos.getZ());
		if (e == null) {
			return false;
		}
		restore(server, e);
		state.markDirty();
		return true;
	}

	/** Whether the cell is in the journal (tests of the glue and the wall record). */
	public static boolean journaled(ServerWorld world, BlockPos pos) {
		return state != null && state.journal.contains(dimensionOf(world), pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * The cell above solid ground near ({@code x}, {@code yHint}, {@code z}) where a surface block would sit, or null.
	 * Looks one block up and down from the hint.
	 */
	public static BlockPos surface(ServerWorld world, double x, int yHint, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		for (int dy : new int[] {0, -1, 1, -2}) {
			BlockPos p = new BlockPos(bx, yHint + dy, bz);
			if (world.getBlockState(p).isAir()
					&& world.getBlockState(p.down()).isSideSolidFullSquare(world, p.down(), Direction.UP)) {
				return p;
			}
		}
		return null;
	}

	/** Restores everything that is due. Call every few ticks. */
	public static void tick(MinecraftServer server, long nowTick) {
		if (state == null || state.journal.size() == 0) {
			return;
		}
		List<Entry> due = state.journal.takeDue(nowTick);
		if (due.isEmpty()) {
			return;
		}
		for (Entry e : due) {
			restore(server, e);
		}
		state.markDirty();
	}

	/** Rolls back every block of one player. Returns how many entries were processed. */
	public static int rollback(MinecraftServer server, UUID owner) {
		if (state == null) {
			return 0;
		}
		IceWall.forget(owner); // R11 T5: the wall record goes with its journal entries
		List<Entry> mine = state.journal.takeAll(owner);
		for (Entry e : mine) {
			restore(server, e);
		}
		if (!mine.isEmpty()) {
			state.markDirty();
		}
		return mine.size();
	}

	public static void rollbackAll(MinecraftServer server) {
		if (state == null) {
			return;
		}
		IceWall.forgetAll();
		List<Entry> all = state.journal.takeAll();
		for (Entry e : all) {
			restore(server, e);
		}
		if (!all.isEmpty()) {
			state.markDirty();
		}
	}

	private static void restore(MinecraftServer server, Entry e) {
		ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(e.dimension())));
		if (world == null) {
			return;
		}
		BlockPos pos = new BlockPos(e.x(), e.y(), e.z());
		BlockState cur = world.getBlockState(pos);
		boolean wall = e.placed().equals(WALL);
		boolean untouched = wall ? cur.isOf(ModBlocks.ICE_WALL) : e.placed().equals("ice") ? cur.isOf(Blocks.ICE) : cur.isOf(Blocks.SNOW);
		if (!untouched) {
			return; // someone changed the cell (or ice melted into water, or creative mode broke a wall block): leave their change alone
		}
		world.setBlockState(pos, previousState(e.previous()), wall ? WALL_FLAGS : Block.NOTIFY_ALL);
	}

	/** Package visible for the tests of the token format. */
	static BlockState previousState(String token) {
		if (token.startsWith("state:")) {
			try {
				return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), token.substring(6), false).blockState();
			} catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException ex) {
				ReiatsuTest.LOGGER.warn("[tempblocks] unreadable previous state '{}', restoring air", token);
				return Blocks.AIR.getDefaultState();
			}
		}
		if (token.equals("water")) {
			return Blocks.WATER.getDefaultState();
		}
		if (token.startsWith("snow:")) {
			int layers = Math.max(1, Math.min(SnowBlock.MAX_LAYERS, Integer.parseInt(token.substring(5))));
			return Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, layers);
		}
		return Blocks.AIR.getDefaultState();
	}

	private static String dimensionOf(World world) {
		return world.getRegistryKey().getValue().toString();
	}
}
