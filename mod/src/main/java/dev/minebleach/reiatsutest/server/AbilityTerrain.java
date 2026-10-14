package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ModConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.world.TerrainRules;
import dev.minebleach.reiatsutest.core.world.TerrainRules.Cell;
import dev.minebleach.reiatsutest.core.world.TerrainRules.Facts;
import dev.minebleach.reiatsutest.core.world.TerrainRules.Profile;
import dev.minebleach.reiatsutest.core.world.TerrainRules.Site;
import dev.minebleach.reiatsutest.core.world.TerrainRules.Verdict;
import dev.minebleach.reiatsutest.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

/**
 * R11 T9: abilities break blocks. The pure rules (profiles S / M / L, what is never broken, crater and tunnel cells, batches) are
 * {@link TerrainRules}; this class reads the world, queues the cells of a cast as a job and breaks them in batches of
 * {@code terrain.blocksPerTick} blocks per tick, so a big crater costs a few ticks of work instead of one long tick.
 *
 * <ul>
 * <li>Where it starts (all inside {@link AbilityExecutor} / {@link KidoManager}, i.e. only when a scheduled phase really runs: a cast that was
 * cancelled, parried or interrupted never reaches them): Byakuya shikai attack mode on every landing pulse ({@link #modeAttack}), bankai
 * Scatter at the impact phase ({@link #scatter}), Hakuteiken at the strike (tunnel along the line, {@link #hakuteikenLine}) and at the burst
 * (crater, {@link #hakuteikenBurst}), Shakkaho burst ({@link #shakkaho}, off by default). Petal cage, Byakurai, Sai and every Rukia ability do
 * not break blocks.</li>
 * <li>World switches: {@code terrain.enabled}, the {@code mobGriefing} game rule (unless {@code terrain.ignoreMobGriefing}), the game mode of the
 * owner (adventure and spectator never break), {@code World.canPlayerModifyAt} (spawn protection and the like).</li>
 * <li>Breaking uses {@code NOTIFY_LISTENERS | FORCE_STATE}: clients and light update but no neighbour update cascades (no water flow, no
 * redstone chain, no popping plants). Afterwards sand and gravel above a hole get one block tick (they fall), plants left without support are
 * removed. One break sound and particle group per {@code terrain.effectEveryBlocks} blocks, drops only with {@code terrain.drops}.</li>
 * <li>Log: {@code [terrain] <ability> broke N blocks (cap C, budget ticks T)}.</li>
 * </ul>
 * UNVERIFIED in the game: the feel of the craters, server load of big jobs, compatibility with territory protection mods.
 */
public final class AbilityTerrain {
	/** Block update flags: tell clients and the light engine, no neighbour / shape updates. */
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
	/** World event id of "block broken": plays the break sound and spawns the particles of the given state. */
	private static final int BLOCK_BREAK_EVENT = 2001;
	/** The Hakuteiken tunnel starts this far in front of the eye, so it never opens the cells around the caster's head. */
	static final double TUNNEL_START = 2.0;
	/** Plants without support removed after one job (safety cap). */
	private static final int ORPHAN_CAP = 256;
	/** Jobs alive at once (a server-wide safety cap; a new job above it is dropped with a log line). */
	private static final int MAX_JOBS = 64;

	private static volatile TerrainRules.Config config = TerrainRules.Config.defaults();

	private static final class Job {
		final UUID owner;
		final ServerWorld world;
		final String label;
		final Profile profile;
		final int cap;
		final List<BlockPos> cells;
		final List<BlockPos> broken = new ArrayList<>();
		int cursor;

		Job(UUID owner, ServerWorld world, String label, Profile profile, int cap, List<BlockPos> cells) {
			this.owner = owner;
			this.world = world;
			this.label = label;
			this.profile = profile;
			this.cap = cap;
			this.cells = cells;
		}
	}

	private static final List<Job> JOBS = new ArrayList<>();

	private AbilityTerrain() {
	}

	public static TerrainRules.Config config() {
		return config;
	}

	public static void init() {
		config = ModConfig.loadTerrain();
		ConfigReload.register(() -> config = ModConfig.loadTerrain());
		ServerTickEvents.END_SERVER_TICK.register(AbilityTerrain::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> JOBS.clear());
	}

	/** Jobs still breaking blocks (dev status and tests of the glue). */
	public static int pendingJobs() {
		return JOBS.size();
	}

	// ------------------------------------------------------------------ call points

	/**
	 * Byakuya shikai, attack mode: a soft crater around the landing burst ({@code center}). Returns the cells reserved (add to the cast's counter).
	 */
	static int modeAttack(ServerPlayerEntity p, CastContext ctx, Vec3d center) {
		Site s = config.modeAttack();
		return start(p, "byakuya.shikai.mode_attack", s, ctx, center, TerrainRules.sphere(center.x, center.y, center.z, s.radius()));
	}

	/** Byakuya bankai Scatter: the crater at the impact point. */
	static int scatter(ServerPlayerEntity p, CastContext ctx) {
		Site s = config.scatter();
		Vec3d c = ctx.aim;
		return start(p, "byakuya.bankai.scatter", s, ctx, c, TerrainRules.sphere(c.x, c.y, c.z, s.radius()));
	}

	/** Hakuteiken, strike: the tunnel along the beam from {@link #TUNNEL_START} to {@code reach} blocks. */
	static int hakuteikenLine(ServerPlayerEntity p, CastContext ctx, double reach) {
		Site s = config.hakuteiken();
		if (reach <= TUNNEL_START) {
			return 0;
		}
		Vec3d a = ctx.eye.add(ctx.dir.multiply(TUNNEL_START));
		Vec3d b = ctx.eye.add(ctx.dir.multiply(reach));
		return start(p, "byakuya.bankai.hakuteiken#line", s, ctx, ctx.eye, TerrainRules.tunnel(a.x, a.y, a.z, b.x, b.y, b.z, s.radius()));
	}

	/** Hakuteiken, burst: the crater at the end of the beam. */
	static int hakuteikenBurst(ServerPlayerEntity p, CastContext ctx) {
		Site s = config.hakuteiken();
		Vec3d c = ctx.aim;
		return start(p, "byakuya.bankai.hakuteiken#burst", s, ctx, c, TerrainRules.sphere(c.x, c.y, c.z, s.radius2()));
	}

	/** Shakkaho burst (kido): off by default ({@code terrain.shakkaho.enabled}). No fire is placed. */
	static int shakkaho(ServerPlayerEntity p, Vec3d center) {
		Site s = config.shakkaho();
		return start(p, "kido.shakkaho", s, null, center, TerrainRules.sphere(center.x, center.y, center.z, s.radius()));
	}

	// ------------------------------------------------------------------ planning

	private static int start(ServerPlayerEntity p, String label, Site site, CastContext ctx, Vec3d source, List<Cell> candidates) {
		TerrainRules.Config cfg = config;
		if (!site.enabled() || site.profile() == Profile.NONE || site.cap() <= 0) {
			return 0;
		}
		ServerWorld world = p.getServerWorld();
		boolean mobGriefing = world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
		boolean modeOk = TerrainRules.gameModeMayBreak(p.interactionManager.getGameMode().getName());
		if (!TerrainRules.worldAllows(cfg, mobGriefing, modeOk)) {
			return 0;
		}
		int free = TerrainRules.remaining(site.cap(), ctx == null ? 0 : ctx.terrainReserved);
		if (free <= 0) {
			return 0;
		}
		if (JOBS.size() >= MAX_JOBS) {
			ReiatsuTest.LOGGER.warn("[terrain] {} dropped: {} jobs are already running", label, JOBS.size());
			return 0;
		}
		List<BlockPos> cells = new ArrayList<>();
		for (Cell c : candidates) {
			BlockPos pos = new BlockPos(c.x(), c.y(), c.z());
			// R11 T5: an ice wall between the centre of the effect and a block shields it (the wall's own blocks are never broken)
			if (check(cfg, site.profile(), world, p, pos) == Verdict.OK && !IceWall.blocked(world, source, Vec3d.ofCenter(pos))) {
				cells.add(pos);
				if (cells.size() >= free) {
					break;
				}
			}
		}
		if (cells.isEmpty()) {
			return 0;
		}
		JOBS.add(new Job(p.getUuid(), world, label, site.profile(), site.cap(), cells));
		return cells.size();
	}

	/** The verdict for one cell of the world (the facts are read here, the decision is {@link TerrainRules#verdict}). */
	static Verdict check(TerrainRules.Config cfg, Profile profile, ServerWorld world, ServerPlayerEntity owner, BlockPos pos) {
		if (world.isOutOfHeightLimit(pos) || !world.getWorldBorder().contains(pos) || !world.isChunkLoaded(pos)) {
			return Verdict.PROTECTED;
		}
		BlockState st = world.getBlockState(pos);
		return TerrainRules.verdict(cfg, profile, facts(world, owner, pos, st));
	}

	static Facts facts(ServerWorld world, ServerPlayerEntity owner, BlockPos pos, BlockState st) {
		Block b = st.getBlock();
		boolean fluid = b instanceof FluidBlock || st.isOf(Blocks.BUBBLE_COLUMN);
		boolean special = st.isOf(Blocks.BEDROCK) || st.isOf(Blocks.BARRIER) || st.isOf(Blocks.LIGHT) || st.isOf(Blocks.STRUCTURE_VOID)
				|| st.isOf(Blocks.COMMAND_BLOCK) || st.isOf(Blocks.CHAIN_COMMAND_BLOCK) || st.isOf(Blocks.REPEATING_COMMAND_BLOCK)
				|| st.isOf(Blocks.STRUCTURE_BLOCK) || st.isOf(Blocks.JIGSAW) || st.isOf(Blocks.NETHER_PORTAL) || st.isOf(Blocks.END_PORTAL)
				|| st.isOf(Blocks.END_PORTAL_FRAME) || st.isOf(Blocks.END_GATEWAY) || st.isOf(Blocks.REINFORCED_DEEPSLATE)
				|| ModBlocks.isModTerrain(b);
		boolean air = st.isAir();
		double hardness = air || fluid ? 0.0 : st.getHardness(world, pos);
		boolean protectedCell = !air && !world.canPlayerModifyAt(owner, pos);
		return new Facts(air, fluid, hardness, st.hasBlockEntity(), special, protectedCell);
	}

	// ------------------------------------------------------------------ execution

	private static void tick(MinecraftServer srv) {
		if (JOBS.isEmpty()) {
			return;
		}
		TerrainRules.Config cfg = config;
		for (Iterator<Job> it = JOBS.iterator(); it.hasNext();) {
			Job j = it.next();
			ServerPlayerEntity owner = srv.getPlayerManager().getPlayer(j.owner);
			boolean gone = owner == null || owner.isDead() || owner.getServerWorld() != j.world || !cfg.enabled();
			if (!gone) {
				step(cfg, j, owner);
			}
			if (gone || j.cursor >= j.cells.size()) {
				finish(j, gone);
				it.remove();
			}
		}
	}

	/** Breaks up to {@code blocksPerTick} cells of the job, re-checking each cell (the world may have changed since the plan). */
	private static void step(TerrainRules.Config cfg, Job j, ServerPlayerEntity owner) {
		int budget = cfg.blocksPerTick();
		while (budget > 0 && j.cursor < j.cells.size()) {
			BlockPos pos = j.cells.get(j.cursor++);
			if (check(cfg, j.profile, j.world, owner, pos) != Verdict.OK) {
				continue;
			}
			budget--;
			BlockState st = j.world.getBlockState(pos);
			if (TerrainRules.effectAt(j.broken.size(), cfg.effectEveryBlocks())) {
				j.world.syncWorldEvent(BLOCK_BREAK_EVENT, pos, Block.getRawIdFromState(st));
			}
			if (cfg.drops()) {
				Block.dropStacks(st, j.world, pos);
			}
			j.world.setBlockState(pos, st.getFluidState().getBlockState(), FLAGS);
			j.broken.add(pos);
		}
	}

	private static void finish(Job j, boolean aborted) {
		if (!j.broken.isEmpty() && !aborted) {
			aftermath(j);
		}
		ReiatsuTest.LOGGER.info("[terrain] {} broke {} blocks (cap {}, budget ticks {}){}", j.label, j.broken.size(), j.cap,
				TerrainRules.budgetTicks(j.cells.size(), config.blocksPerTick()), aborted ? " [stopped early: owner gone]" : "");
	}

	/** Sand and gravel above a hole get one block tick (they fall); plants that lost their ground are removed. */
	private static void aftermath(Job j) {
		int orphans = 0;
		for (BlockPos pos : new ArrayList<>(j.broken)) {
			BlockPos above = pos.up();
			BlockState st = j.world.getBlockState(above);
			if (st.isAir()) {
				continue;
			}
			if (st.getBlock() instanceof FallingBlock) {
				j.world.scheduleBlockTick(above, st.getBlock(), 2);
			} else if (orphans < ORPHAN_CAP && st.getFluidState().isEmpty() && !st.hasBlockEntity() && !ModBlocks.isModTerrain(st.getBlock())
					&& st.getHardness(j.world, above) == 0.0F && !st.canPlaceAt(j.world, above)) {
				j.world.setBlockState(above, Blocks.AIR.getDefaultState(), FLAGS);
				orphans++;
			}
		}
	}
}
