package dev.minebleach.reiatsutest.server;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.minebleach.reiatsutest.ModConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.FieldAnchorPolicy;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.world.FrostConfig;
import dev.minebleach.reiatsutest.core.world.FrostJournal;
import dev.minebleach.reiatsutest.core.world.FrostJournal.Entry;
import dev.minebleach.reiatsutest.core.world.FrostWave;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.registry.ModBlocks;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Territory freeze of the Rukia bankai (round 6, Hakka no Togame): when the bankai starts, a ring of frost runs outward from her
 * ({@link FrostConfig#radius()} blocks at {@link FrostConfig#waveSpeed()} blocks per second) and replaces the exposed surface blocks
 * with the mod's frost blocks ({@link ModBlocks}: crust for ground, stone, leaves, thin ice on water, sprigs for plants). The
 * originals are kept in the {@link FrostJournal} (persisted in {@link FrostState}) and put back, outermost first, when the bankai ends,
 * the owner dies, leaves, changes dimension or the 45 s cap passes. The world is never left changed:
 * <ul>
 * <li>the journal is written to {@code data/reiatsu_test_frost.dat} at most every {@code saveEveryTicks} while it changes, and read at
 * the next start, which restores everything first (crash recovery, {@link #onServerStarted});</li>
 * <li>server stop restores everything synchronously before the worlds are saved;</li>
 * <li>a block is journaled once, so two fields can never take each other's frost block for the original; a restore only touches cells
 * that still hold a frost block (or air: creative mode may have broken it); a block somebody placed there since is left alone;</li>
 * <li>block entities, bedrock, any block that is not on the allowed list, flowing water and cells with entities in them (water) are
 * never touched;</li>
 * <li>per tick budgets: columns of the ring, blocks placed, blocks restored, chunk loads (a restore in a chunk that is no longer loaded
 * costs {@code chunkLoadCost}); a total cap per field and for all fields.</li>
 * </ul>
 * Placement uses {@code NOTIFY_LISTENERS | FORCE_STATE}: clients and light update, but no neighbour or shape updates run, so
 * plants do not pop off and nothing cascades. UNVERIFIED in the game (nothing was started in this round).
 */
public final class FrostField {
	/** Block update flags: tell clients and the light engine, run no neighbour / shape updates. */
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private enum Kind {
		CRUST, STONE, LEAVES, SPRIG, FILM
	}

	private static final class Field {
		final int id;
		final UUID owner;
		final String ownerName;
		final int ownerEntityId;
		final String dim;
		final double cx;
		final double cy;
		final double cz;
		final int bx;
		final int by;
		final int bz;
		final int startTick;
		final FrostWave wave;
		int cursor;
		int placed;
		boolean thawing;
		boolean fast;
		boolean capped;
		int thawStart;
		/** Started by the dev command: not tied to the bankai, ends with the cap, the owner leaving or the rollback command. */
		final boolean manual;

		Field(int id, ServerPlayerEntity p, String dim, int startTick, FrostWave wave, boolean manual) {
			this.manual = manual;
			this.id = id;
			this.owner = p.getUuid();
			this.ownerName = p.getName().getString();
			this.ownerEntityId = p.getId();
			this.dim = dim;
			this.cx = p.getX();
			this.cy = p.getY();
			this.cz = p.getZ();
			this.bx = p.getBlockX();
			this.by = p.getBlockY();
			this.bz = p.getBlockZ();
			this.startTick = startTick;
			this.wave = wave;
		}
	}

	private static FrostConfig config = FrostConfig.defaults();
	private static FrostState state;
	private static final List<Field> FIELDS = new ArrayList<>();
	private static int nextId = 1;
	/** Greater than 0 while a restore reads or writes a cell (the orphan sweep stays out, see {@link #restoreOne}). */
	private static int restoring;
	private static int lastSaveTick;
	private static final Map<String, BlockState> PARSED = new HashMap<>();

	private FrostField() {
	}

	/** Registered once from the mod initialiser. */
	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(FrostField::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(FrostField::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(FrostField::tick);
		ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> sweepOrphans(world, chunk));
	}

	public static FrostConfig config() {
		return config;
	}

	// ------------------------------------------------------------------ lifecycle

	/** SERVER_STARTED: read the journal of the previous run and put every original block back before anything else. */
	static void onServerStarted(MinecraftServer server) {
		config = ModConfig.loadFrost();
		FIELDS.clear();
		PARSED.clear();
		state = server.getOverworld().getPersistentStateManager().getOrCreate(FrostState.TYPE, FrostState.ID);
		List<Entry> leftovers = state.journal.takeAll();
		if (!leftovers.isEmpty()) {
			int n = restoreAll(server, leftovers);
			ReiatsuTest.LOGGER.warn("[frost] rolled back {} frozen block(s) left by the previous run ({} journal entries)", n, leftovers.size());
			state.markDirty();
			saveNow(server);
		}
		// the spawn chunks were loaded before this point (their load event saw no journal): sweep them for frost the journal did not know
		for (ServerWorld w : server.getWorlds()) {
			BlockPos sp = w.getSpawnPos();
			for (int cx = (sp.getX() >> 4) - 12; cx <= (sp.getX() >> 4) + 12; cx++) {
				for (int cz = (sp.getZ() >> 4) - 12; cz <= (sp.getZ() >> 4) + 12; cz++) {
					WorldChunk c = w.getChunkManager().getWorldChunk(cx, cz);
					if (c != null) {
						sweepOrphans(w, c);
					}
				}
			}
		}
	}

	/**
	 * Last line of defence against a lost journal (a hard crash between the last journal write and the chunk save): when a chunk loads,
	 * every frost block that is not in the journal is replaced by a plain fallback (dirt, stone, oak leaves, water, air). The cost is a
	 * palette check per chunk section; only sections that really hold a frost block are scanned.
	 */
	private static void sweepOrphans(ServerWorld world, WorldChunk chunk) {
		if (state == null || !config.orphanSweep() || restoring > 0) {
			return;
		}
		String dim = dimensionOf(world);
		ChunkSection[] sections = chunk.getSectionArray();
		int fixed = 0;
		for (int i = 0; i < sections.length; i++) {
			ChunkSection sec = sections[i];
			if (sec == null || sec.isEmpty() || !sec.hasAny(st -> ModBlocks.isFrost(st.getBlock()))) {
				continue;
			}
			int baseY = (chunk.getBottomSectionCoord() + i) << 4;
			for (int lx = 0; lx < 16; lx++) {
				for (int ly = 0; ly < 16; ly++) {
					for (int lz = 0; lz < 16; lz++) {
						BlockState st = sec.getBlockState(lx, ly, lz);
						if (!ModBlocks.isFrost(st.getBlock())) {
							continue;
						}
						int x = (chunk.getPos().x << 4) + lx;
						int y = baseY + ly;
						int z = (chunk.getPos().z << 4) + lz;
						if (state.journal.contains(dim, x, y, z)) {
							continue; // a live field owns it
						}
						chunk.setBlockState(new BlockPos(x, y, z), fallbackOf(st.getBlock()), false);
						fixed++;
					}
				}
			}
		}
		if (fixed > 0) {
			ReiatsuTest.LOGGER.warn("[frost] chunk {} in {}: {} frost block(s) without a journal entry replaced by plain blocks (the journal was lost in a crash)", chunk.getPos(), dim, fixed);
		}
	}

	private static BlockState fallbackOf(Block b) {
		if (b == ModBlocks.FROST_CRUST) {
			return Blocks.DIRT.getDefaultState();
		}
		if (b == ModBlocks.FROST_STONE) {
			return Blocks.STONE.getDefaultState();
		}
		if (b == ModBlocks.FROST_LEAVES) {
			return Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true);
		}
		if (b == ModBlocks.FROST_FILM) {
			return Blocks.WATER.getDefaultState();
		}
		return Blocks.AIR.getDefaultState(); // sprig
	}

	/** SERVER_STOPPING: restore everything synchronously, then the normal world save writes the empty journal. */
	static void onServerStopping(MinecraftServer server) {
		if (state != null) {
			List<Entry> all = state.journal.takeAll();
			if (!all.isEmpty()) {
				int n = restoreAll(server, all);
				ReiatsuTest.LOGGER.info("[frost] server stopping: restored {} frozen block(s)", n);
				state.markDirty();
				saveNow(server);
			}
		}
		FIELDS.clear();
		state = null;
	}

	/** Called for every broadcast effect (RukiaServerFx): the Rukia bankai release starts a field. */
	static void onEffect(ServerPlayerEntity caster, int effectId) {
		if (effectId == EffectIds.RUKIA_BANKAI_RELEASE) {
			start(caster, false);
		}
	}

	/** Starts a field at the caster's feet. Returns false when the feature is off or the journal is not available. */
	public static boolean start(ServerPlayerEntity p, boolean manual) {
		if (state == null || !config.enabled()) {
			return false;
		}
		ServerWorld world = p.getServerWorld();
		int now = p.getServer().getTicks();
		FrostWave wave = new FrostWave(config.radius(), config.waveSpeed(), config.columnsPerTick());
		Field f = new Field(nextId++, p, dimensionOf(world), now, wave, manual);
		state.journal.setCenter(f.id, f.cx, f.cz);
		FIELDS.add(f);
		ReiatsuTest.LOGGER.info("[frost] field {} started for {} at ({}, {}, {}) in {}: radius {}, {} columns, wave {} blocks/s, cap {} ticks",
				f.id, f.ownerName, f.bx, f.by, f.bz, f.dim, config.radius(), wave.totalColumns(), config.waveSpeed(), config.maxTicks());
		sendFx(world, f, EffectIds.FROST_FIELD, new float[] {config.radius(), (float) config.waveSpeed(), config.maxTicks()});
		return true;
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer srv) {
		if (state == null) {
			return;
		}
		int now = srv.getTicks();
		for (Iterator<Field> it = FIELDS.iterator(); it.hasNext();) {
			Field f = it.next();
			ServerWorld world = worldOf(srv, f.dim);
			if (world == null) {
				// the dimension is gone: nothing can be restored here, drop the field (entries are rolled back at the next start)
				ReiatsuTest.LOGGER.warn("[frost] field {}: dimension {} is not loaded, dropping the field", f.id, f.dim);
				it.remove();
				continue;
			}
			if (!f.thawing) {
				checkOwner(srv, world, f, now);
			}
			if (f.thawing) {
				if (thawStep(srv, f, now)) {
					ReiatsuTest.LOGGER.info("[frost] field {} thawed after {} ticks, journal {}", f.id, now - f.startTick, state.journal.size());
					it.remove();
				}
			} else {
				freezeStep(srv, world, f, now);
			}
		}
		if (state.isDirty() && now - lastSaveTick >= config.saveEveryTicks()) {
			saveNow(srv);
			lastSaveTick = now;
		}
	}

	/**
	 * The one bankai cleanup asks the fields of an owner to thaw NOW (no waiting for the 10 tick poll). {@code fast} = owner dead, gone or in
	 * another dimension (everything is restored by the fast budget). Manual dev fields are left alone. Returns the fields that started to thaw.
	 */
	static int endOf(MinecraftServer srv, UUID owner, boolean fast) {
		if (state == null) {
			return 0;
		}
		int now = srv.getTicks();
		int n = 0;
		for (Field f : FIELDS) {
			if (f.owner.equals(owner) && !f.manual && !f.thawing) {
				ServerWorld world = worldOf(srv, f.dim);
				if (world != null) {
					beginThaw(world, f, now, fast, fast ? "bankai ended, fast thaw" : "bankai ended");
					n++;
				}
			}
		}
		return n;
	}

	/** Fields of an owner that are still freezing or thawing, and the journal entries they hold (dev audit). */
	static String liveOf(UUID owner) {
		int fields = 0;
		int blocks = 0;
		boolean freezing = false;
		for (Field f : FIELDS) {
			if (f.owner.equals(owner)) {
				fields++;
				freezing |= !f.thawing;
				blocks += state == null ? 0 : state.journal.size(f.id);
			}
		}
		return fields + " field(s)" + (freezing ? " (one still freezing)" : "") + ", " + blocks + " journal block(s)";
	}

	static int liveFields(UUID owner) {
		int fields = 0;
		for (Field f : FIELDS) {
			if (f.owner.equals(owner) && !f.manual) {
				fields++;
			}
		}
		return fields;
	}

	private static void checkOwner(MinecraftServer srv, ServerWorld world, Field f, int now) {
		int age = now - f.startTick;
		if (age >= config.maxTicks()) {
			beginThaw(world, f, now, false, "time cap");
			return;
		}
		if (!FieldAnchorPolicy.due(age)) {
			return;
		}
		ServerPlayerEntity owner = srv.getPlayerManager().getPlayer(f.owner);
		StateMachine sm = owner == null ? null : ZanpakutoManager.machine(owner);
		boolean keep = owner != null && sm != null && owner.getServerWorld() == world
				&& (f.manual ? owner.isAlive() : FieldAnchorPolicy.keep(owner.isAlive(), true, sm.character(), sm.state()));
		if (!keep) {
			boolean gone = owner == null || !owner.isAlive() || owner.getServerWorld() != world;
			beginThaw(world, f, now, gone, gone ? "owner gone, fast thaw" : "bankai ended");
		}
	}

	private static void beginThaw(ServerWorld world, Field f, int now, boolean fast, String why) {
		f.thawing = true;
		f.fast = fast;
		f.thawStart = now;
		ReiatsuTest.LOGGER.info("[frost] field {} thaws ({}), {} blocks placed, {} in the journal", f.id, why, f.placed, state.journal.size(f.id));
		sendFx(world, f, EffectIds.FROST_THAW, new float[] {config.radius(), fast ? 0f : (float) config.thawSpeed()});
	}

	// ------------------------------------------------------------------ freezing

	private static void freezeStep(MinecraftServer srv, ServerWorld world, Field f, int now) {
		if (f.wave.done(f.cursor)) {
			return;
		}
		int target = f.wave.advance(f.cursor, now - f.startTick);
		View view = new View(world);
		int tickBlocks = 0;
		while (f.cursor < target && tickBlocks < config.blocksPerTick()) {
			if (f.placed >= config.maxBlocks() || state.journal.size() >= config.maxTotalBlocks()) {
				if (!f.capped) {
					f.capped = true;
					ReiatsuTest.LOGGER.warn("[frost] field {}: block budget reached ({} placed, {} frozen in all), the wave stops here", f.id, f.placed, state.journal.size());
				}
				f.cursor = f.wave.totalColumns();
				break;
			}
			int n = freezeColumn(world, view, f, f.bx + f.wave.dx(f.cursor), f.bz + f.wave.dz(f.cursor));
			f.cursor++;
			f.placed += n;
			tickBlocks += n;
		}
		if (tickBlocks > 0) {
			state.markDirty();
		}
		if (f.wave.done(f.cursor)) {
			ReiatsuTest.LOGGER.info("[frost] field {}: wave done after {} ticks, {} blocks frozen", f.id, now - f.startTick, f.placed);
		}
	}

	/** Freezes the exposed freezable blocks of one column, top down, at most {@code maxPerColumn}. Returns how many were changed. */
	private static int freezeColumn(ServerWorld world, View view, Field f, int x, int z) {
		WorldChunk home = view.chunk(x >> 4, z >> 4);
		// only chunks that tick blocks: the world only tells the clients about changes there (World.setBlockState), so a frozen block in a
		// chunk that is merely loaded for the view distance would stay invisible to the players until they re-enter it
		if (home == null || home.getLevelType() == null || !home.getLevelType().isAfter(ChunkLevelType.BLOCK_TICKING)) {
			return 0;
		}
		int top = Math.min(world.getTopY() - 1, f.by + config.up());
		int bottom = Math.max(world.getBottomY(), f.by - config.down());
		int done = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int y = top; y >= bottom && done < config.maxPerColumn(); y--) {
			BlockState cur = view.state(x, y, z);
			if (cur == null || cur.isAir()) {
				continue;
			}
			Kind kind = classify(cur);
			if (kind == null) {
				continue;
			}
			pos.set(x, y, z);
			if ((kind == Kind.CRUST || kind == Kind.STONE) && !cur.isFullCube(world, pos)) {
				continue;
			}
			if (kind == Kind.FILM) {
				BlockState above = view.state(x, y + 1, z);
				if (above == null || !above.isAir() || !world.getOtherEntities(null, new Box(pos), e -> !e.isSpectator()).isEmpty()) {
					continue;
				}
			} else if (!exposed(view, x, y, z)) {
				continue;
			}
			if (!state.journal.add(f.dim, x, y, z, BlockArgumentParser.stringifyBlockState(cur), f.id)) {
				continue; // already frozen by another field
			}
			world.setBlockState(pos.toImmutable(), targetOf(kind), FLAGS);
			done++;
		}
		return done;
	}

	private static Kind classify(BlockState s) {
		Block b = s.getBlock();
		if (s.hasBlockEntity() || ModBlocks.isFrost(b)) {
			return null;
		}
		if (s.isOf(Blocks.WATER)) {
			return s.getFluidState().isStill() ? Kind.FILM : null; // only source blocks, never flowing water
		}
		if (s.isIn(BlockTags.LEAVES)) {
			return Kind.LEAVES;
		}
		if (s.isOf(Blocks.SHORT_GRASS) || s.isOf(Blocks.TALL_GRASS) || s.isOf(Blocks.FERN) || s.isOf(Blocks.LARGE_FERN) || s.isOf(Blocks.DEAD_BUSH)
				|| s.isIn(BlockTags.SMALL_FLOWERS) || s.isIn(BlockTags.TALL_FLOWERS)) {
			return Kind.SPRIG;
		}
		if (s.isIn(BlockTags.DIRT) || s.isIn(BlockTags.SAND) || s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.CLAY) || s.isOf(Blocks.SNOW_BLOCK)
				|| s.isOf(Blocks.SOUL_SAND) || s.isOf(Blocks.SOUL_SOIL)) {
			return Kind.CRUST;
		}
		if (s.isIn(BlockTags.BASE_STONE_OVERWORLD) || s.isIn(BlockTags.BASE_STONE_NETHER) || s.isIn(BlockTags.TERRACOTTA)
				|| s.isOf(Blocks.COBBLESTONE) || s.isOf(Blocks.MOSSY_COBBLESTONE) || s.isOf(Blocks.SANDSTONE) || s.isOf(Blocks.RED_SANDSTONE)
				|| s.isOf(Blocks.END_STONE)) {
			return Kind.STONE;
		}
		return null; // everything else (ores, logs, buildings, bedrock, obsidian, ice, snow layers ...) is left alone
	}

	private static BlockState targetOf(Kind k) {
		return switch (k) {
			case CRUST -> ModBlocks.FROST_CRUST.getDefaultState();
			case STONE -> ModBlocks.FROST_STONE.getDefaultState();
			case LEAVES -> ModBlocks.FROST_LEAVES.getDefaultState();
			case SPRIG -> ModBlocks.FROST_SPRIG.getDefaultState();
			case FILM -> ModBlocks.FROST_FILM.getDefaultState();
		};
	}

	/** A face towards air or a see-through block that is not a fluid; cells next to an unloaded chunk count as not exposed. */
	private static boolean exposed(View view, int x, int y, int z) {
		for (Direction d : Direction.values()) {
			BlockState n = view.state(x + d.getOffsetX(), y + d.getOffsetY(), z + d.getOffsetZ());
			if (n != null && (n.isAir() || (!n.isOpaque() && n.getFluidState().isEmpty()))) {
				return true;
			}
		}
		return false;
	}

	/** Non loading block reads with a per tick chunk cache. */
	private static final class View {
		final ServerWorld world;
		final Map<Long, WorldChunk> chunks = new HashMap<>();

		View(ServerWorld world) {
			this.world = world;
		}

		WorldChunk chunk(int cx, int cz) {
			return chunks.computeIfAbsent(ChunkPos.toLong(cx, cz), k -> world.getChunkManager().getWorldChunk(cx, cz));
		}

		BlockState state(int x, int y, int z) {
			if (world.isOutOfHeightLimit(y)) {
				return null;
			}
			WorldChunk c = chunk(x >> 4, z >> 4);
			return c == null ? null : c.getBlockState(new BlockPos(x, y, z));
		}
	}

	// ------------------------------------------------------------------ restoring

	/** One thaw tick of a field. Returns true when the field has nothing left in the journal. */
	private static boolean thawStep(MinecraftServer srv, Field f, int now) {
		FrostJournal j = state.journal;
		int budget = f.fast ? config.fastRestoreBlocksPerTick() : config.restoreBlocksPerTick();
		// a normal thaw runs inward as a ring (thawSpeed); a fast one (owner dead, offline, other dimension) takes everything by the budget
		double minDistance = f.fast ? 0.0 : config.thawRadiusAt(now - f.thawStart);
		List<Entry> batch = j.takeBatch(f.id, budget, true, minDistance);
		if (!batch.isEmpty()) {
			List<Entry> rest = restoreBatch(srv, batch, budget, false);
			if (!rest.isEmpty()) {
				j.putBack(rest);
				j.setCenter(f.id, f.cx, f.cz);
			}
			state.markDirty();
		}
		return j.size(f.id) == 0;
	}

	/**
	 * Restores the entries within a weight budget (1 per block, {@code chunkLoadCost} for the first block in a chunk that is not
	 * loaded) and returns the entries that did not fit. {@code force} ignores the budget (server stop, crash recovery).
	 */
	private static List<Entry> restoreBatch(MinecraftServer srv, List<Entry> batch, int budget, boolean force) {
		List<Entry> rest = new ArrayList<>();
		Set<String> paid = new HashSet<>();
		int cost = 0;
		for (int i = 0; i < batch.size(); i++) {
			Entry e = batch.get(i);
			ServerWorld world = worldOf(srv, e.dimension());
			if (world == null) {
				continue; // dimension unknown: nothing to restore into
			}
			if (!force) {
				int cx = e.x() >> 4;
				int cz = e.z() >> 4;
				boolean loaded = world.getChunkManager().getWorldChunk(cx, cz) != null;
				int w = 1;
				if (!loaded && paid.add(e.dimension() + "|" + ChunkPos.toLong(cx, cz))) {
					w += config.chunkLoadCost();
				}
				if (cost + w > budget && cost > 0) {
					rest.addAll(batch.subList(i, batch.size()));
					return rest;
				}
				cost += w;
			}
			restoreOne(world, e);
		}
		return rest;
	}

	/** Restores everything now, ignoring every budget; returns how many cells were really changed. */
	private static int restoreAll(MinecraftServer server, List<Entry> all) {
		int changed = 0;
		for (Entry e : all) {
			ServerWorld world = worldOf(server, e.dimension());
			if (world != null && restoreOne(world, e)) {
				changed++;
			}
		}
		return changed;
	}

	/**
	 * Puts the original state back when the cell still holds one of our frost blocks, or air (creative mode may have broken the
	 * frost block; the original must not be lost with it). Anything else was placed by somebody after the freeze and stays.
	 */
	private static boolean restoreOne(ServerWorld world, Entry e) {
		if (world.isOutOfHeightLimit(e.y())) {
			return false;
		}
		BlockPos pos = new BlockPos(e.x(), e.y(), e.z());
		// reading the cell may load its chunk; the orphan sweep of that load must not see the entries that are taken out of the journal
		// but not restored yet (it would replace their frost blocks by fallbacks and the originals would be lost)
		restoring++;
		try {
			BlockState cur = world.getBlockState(pos);
			// R11 review: an ice wall block that was built over a sprig of this field (TempBlocks.placeWall) also counts as ours, so a thaw that comes
			// before the wall expires does not lose the original grass; the wall's own restore then finds a changed cell and leaves it alone
			if (!ModBlocks.isFrost(cur.getBlock()) && !cur.isAir() && !IceWall.isWall(cur)) {
				return false;
			}
			world.setBlockState(pos, parse(e.previous()), FLAGS);
			return true;
		} finally {
			restoring--;
		}
	}

	/** Block state of a journal token; an unknown block (mod removed) becomes air so no frost is ever kept for it. */
	private static BlockState parse(String token) {
		BlockState s = PARSED.get(token);
		if (s == null) {
			try {
				s = BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), token, false).blockState();
			} catch (CommandSyntaxException | RuntimeException ex) {
				ReiatsuTest.LOGGER.warn("[frost] cannot read block state '{}', restoring air", token);
				s = Blocks.AIR.getDefaultState();
			}
			PARSED.put(token, s);
		}
		return s;
	}

	// ------------------------------------------------------------------ helpers

	private static ServerWorld worldOf(MinecraftServer srv, String dim) {
		return srv.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dim)));
	}

	private static String dimensionOf(ServerWorld world) {
		return world.getRegistryKey().getValue().toString();
	}

	private static void saveNow(MinecraftServer srv) {
		try {
			Path file = srv.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(FrostState.ID + ".dat");
			state.save(file.toFile(), srv.getRegistryManager());
		} catch (RuntimeException ex) {
			ReiatsuTest.LOGGER.error("[frost] could not write the journal", ex);
		}
	}

	private static void sendFx(ServerWorld world, Field f, int effectId, float[] params) {
		EffectEventS2C payload = new EffectEventS2C(effectId, f.ownerEntityId, f.id, f.cx, f.cy, f.cz, 0f, 0f, 0f, -1, world.getServer().getTicks(), params);
		for (ServerPlayerEntity v : PlayerLookup.around(world, new Vec3d(f.cx, f.cy, f.cz), config.radius() + 64.0)) {
			if (ServerPlayNetworking.canSend(v, payload.getId())) {
				ServerPlayNetworking.send(v, payload);
			}
		}
	}

	// ------------------------------------------------------------------ dev / commands

	/** Number of fields and frozen blocks, for {@code /reiatsu frost status}. */
	public static String status() {
		if (state == null) {
			return "frost: not running";
		}
		return "frost: " + FIELDS.size() + " field(s), " + state.journal.size() + " frozen block(s) in the journal, radius " + config.radius()
				+ ", wave " + config.waveSpeed() + " b/s, cap " + config.maxTicks() + " ticks";
	}

	/** {@code /reiatsu frost rollback}: restores everything now. Returns the number of changed blocks. */
	public static int rollbackNow(MinecraftServer server) {
		if (state == null) {
			return 0;
		}
		List<Entry> all = state.journal.takeAll();
		int n = restoreAll(server, all);
		FIELDS.clear();
		state.markDirty();
		saveNow(server);
		ReiatsuTest.LOGGER.info("[frost] manual rollback: {} block(s) restored", n);
		return n;
	}

	/** Frozen blocks of one dimension count, for the tests of the harness. */
	public static int frozenBlocks() {
		return state == null ? 0 : state.journal.size();
	}
}
