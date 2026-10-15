package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ModConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.sound.SoundIds;
import dev.minebleach.reiatsutest.core.state.AbilityParams;
import dev.minebleach.reiatsutest.core.world.IceWallConfig;
import dev.minebleach.reiatsutest.core.world.IceWallLayout;
import dev.minebleach.reiatsutest.core.world.IceWallLayout.WallCell;
import dev.minebleach.reiatsutest.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

/**
 * R11 T5: the Rukia bankai "ice wall" (variant W). A wall of real {@code ice_wall} blocks grows bottom to top where she looks, stands for
 * {@code lifeTicks} and fades from the top; nobody walks through it. The cells are planned by the pure {@link IceWallLayout}, the blocks go
 * through the temporary block journal ({@link TempBlocks#placeWall}), so a death, logout, dimension change, bankai end, server stop or crash puts
 * the original blocks back (the same guarantee as {@link FrostField}).
 *
 * <ul>
 * <li>One wall per player: a new cast takes the old one down at once.</li>
 * <li>Cells with a living entity or a vehicle in them are skipped (no suffocation); cells with solid blocks or block entities are never touched;
 * replaceable plants are replaced and put back.</li>
 * <li>Strength: {@code hp} (60) is a pool of the whole wall. Abilities that strike it spend their damage ({@link #absorbNear}); at 0 it breaks with
 * shards. Vanilla explosions and tools do not hurt it (the block has strength -1).</li>
 * <li>Abilities stop at it: every ray that uses {@link Targeting#rayPoint} sees the wall as a collider, and area abilities ask {@link #blocked}
 * whether the wall stands between the centre of the effect and a target (see design/results/r11_T5.md for the list).</li>
 * </ul>
 * UNVERIFIED in the game: how it looks and feels, the translucent block through glass, server load.
 */
public final class IceWall {
	private static final String LOG = "[icewall] ";

	private static final class Wall {
		final UUID owner;
		final String ownerName;
		final ServerWorld world;
		final String dim;
		final IceWallConfig cfg;
		final int startTick;
		final int layers;
		final List<List<WallCell>> byLayer = new ArrayList<>();
		final Set<Long> placed = new HashSet<>();
		final Vec3d center;
		int nextLayer;
		int keptLayers;
		double hp;
		int skipped;

		Wall(ServerPlayerEntity p, IceWallConfig cfg, int startTick, IceWallLayout.Plan plan, Vec3d center) {
			this.owner = p.getUuid();
			this.ownerName = p.getName().getString();
			this.world = p.getServerWorld();
			this.dim = world.getRegistryKey().getValue().toString();
			this.cfg = cfg;
			this.startTick = startTick;
			this.layers = plan.layers();
			for (int i = 0; i < layers; i++) {
				byLayer.add(new ArrayList<>());
			}
			for (WallCell c : plan.cells()) {
				byLayer.get(c.layer()).add(c);
			}
			this.center = center;
			this.keptLayers = layers;
			this.hp = cfg.hp();
		}
	}

	private static volatile IceWallConfig config = IceWallConfig.defaults();
	private static final Map<UUID, Wall> WALLS = new LinkedHashMap<>();

	private IceWall() {
	}

	public static IceWallConfig config() {
		return config;
	}

	public static void init() {
		config = ModConfig.loadIceWall();
		ConfigReload.register(() -> config = ModConfig.loadIceWall());
		ServerTickEvents.END_SERVER_TICK.register(IceWall::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> WALLS.clear());
	}

	/** Walls standing (dev status and the harness). */
	public static int count() {
		return WALLS.size();
	}

	/** Wall blocks standing for one owner (0 when none). */
	public static int blocksOf(UUID owner) {
		Wall w = WALLS.get(owner);
		return w == null ? 0 : w.placed.size();
	}

	/** Strength left of the wall of an owner, or -1 when there is none. */
	public static double hpOf(UUID owner) {
		Wall w = WALLS.get(owner);
		return w == null ? -1.0 : w.hp;
	}

	// ------------------------------------------------------------------ cast

	/** The release phase of the ability (cast time over): plans the wall where the caster looks now and starts it growing. */
	static void cast(ServerPlayerEntity p, CastContext castCtx) {
		IceWallConfig cfg = config;
		if (!cfg.enabled()) {
			ReiatsuTest.LOGGER.info(LOG + "{} cast ignored: rukia.iceWall.enabled is false", p.getName().getString());
			return;
		}
		release(WALLS.get(p.getUuid()), "replaced by a new wall", true);
		CastContext ctx = castCtx.resampled(p);
		ServerWorld world = p.getServerWorld();
		double range = ctx.spec.num(AbilityParams.LENGTH);
		double hitDistance = Double.NaN;
		if (ctx.rayLength < range - 0.05) {
			hitDistance = ctx.rayLength; // a block stopped the ray
		}
		var entity = Targeting.firstOnRay(p, ctx.eye, ctx.dir, range);
		if (entity != null) {
			Optional<Vec3d> h = entity.getBoundingBox().expand(0.3).raycast(ctx.eye, ctx.aim);
			if (h.isPresent()) {
				double d = ctx.eye.distanceTo(h.get());
				hitDistance = Double.isNaN(hitDistance) ? d : Math.min(hitDistance, d);
			}
		}
		double dist = IceWallLayout.distance(range, hitDistance);
		Vec3d pt = ctx.eye.add(ctx.dir.multiply(dist));
		IceWallLayout.Plan plan = IceWallLayout.plan(new WorldProbe(world), cfg, pt.x, pt.y, pt.z, ctx.dir.x, ctx.dir.z);
		int now = p.getServer().getTicks();
		Vec3d center = new Vec3d(plan.centerX() + 0.5, plan.baseY() + cfg.height() * 0.5, plan.centerZ() + 0.5);
		Wall w = new Wall(p, cfg, now, plan, center);
		WALLS.put(p.getUuid(), w);
		ReiatsuTest.LOGGER.info(LOG + "{} wall at ({}, {}, {}) normal {} sign {} base {}{} layers {} cells {} hp {} life {} ticks", w.ownerName, plan.centerX(),
				plan.baseY(), plan.centerZ(), plan.normalX() ? "X" : "Z", plan.sign(), plan.baseY(), plan.grounded() ? "" : " (no ground, at the aimed height)",
				plan.layers(), plan.cells().size(), cfg.hp(), cfg.lifeTicks());
		ModSound.play(world, SoundIds.RUKIA_BANKAI_ICE_WALL_START, center.x, plan.baseY(), center.z, "block.amethyst_block.chime", 1.2F, 0.6F);
		growUntil(p.getServer(), w, now);
	}

	private record WorldProbe(ServerWorld world) implements IceWallLayout.Probe {
		@Override
		public boolean passable(int x, int y, int z) {
			BlockPos pos = new BlockPos(x, y, z);
			return !world.isOutOfHeightLimit(pos) && world.isChunkLoaded(pos) && TempBlocks.wallReplaceable(world.getBlockState(pos));
		}

		@Override
		public boolean support(int x, int y, int z) {
			BlockPos pos = new BlockPos(x, y, z);
			return !world.isOutOfHeightLimit(pos) && world.isChunkLoaded(pos) && !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
		}
	}

	// ------------------------------------------------------------------ life

	private static void tick(MinecraftServer srv) {
		if (WALLS.isEmpty()) {
			return;
		}
		int now = srv.getTicks();
		for (Iterator<Wall> it = new ArrayList<>(WALLS.values()).iterator(); it.hasNext();) {
			Wall w = it.next();
			ServerPlayerEntity owner = srv.getPlayerManager().getPlayer(w.owner);
			if (owner == null || owner.isDead() || owner.getServerWorld() != w.world) {
				release(w, "owner gone", true);
				continue;
			}
			int age = now - w.startTick;
			if (age >= w.cfg.lifeTicks()) {
				release(w, "expired", false);
				continue;
			}
			growUntil(srv, w, now);
			int keep = w.layers - IceWallLayout.goneLayers(age, w.cfg.lifeTicks(), w.cfg.fadeTicks(), w.layers);
			while (w.keptLayers > keep) {
				w.keptLayers--;
				removeLayer(srv, w, w.keptLayers, false);
			}
		}
	}

	private static void growUntil(MinecraftServer srv, Wall w, int now) {
		int age = now - w.startTick;
		while (w.nextLayer < w.layers && IceWallLayout.dueTick(w.nextLayer, w.layers, w.cfg.growTicks()) <= age) {
			placeLayer(srv, w, w.nextLayer++);
		}
	}

	private static void placeLayer(MinecraftServer srv, Wall w, int layer) {
		List<WallCell> cells = w.byLayer.get(layer);
		if (cells.isEmpty()) {
			return;
		}
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		int y = cells.get(0).y();
		for (WallCell c : cells) {
			minX = Math.min(minX, c.x());
			maxX = Math.max(maxX, c.x());
			minZ = Math.min(minZ, c.z());
			maxZ = Math.max(maxZ, c.z());
		}
		List<Box> occupied = new ArrayList<>();
		for (Entity e : w.world.getEntitiesByClass(Entity.class, new Box(minX, y, minZ, maxX + 1, y + 1, maxZ + 1), IceWall::blocksCell)) {
			occupied.add(e.getBoundingBox());
		}
		long due = (long) w.startTick + w.cfg.lifeTicks() + 40L; // the journal's own safety net, a little after the wall's life
		int placedHere = 0;
		for (WallCell c : IceWallLayout.without(cells, c -> occupiedBy(occupied, c))) {
			BlockPos pos = new BlockPos(c.x(), c.y(), c.z());
			if (TempBlocks.placeWall(w.world, w.owner, pos, due, w.cfg.maxBlocks())) {
				w.placed.add(pos.asLong());
				placedHere++;
			}
		}
		w.skipped += cells.size() - placedHere;
		if (placedHere > 0) {
			w.world.spawnParticles(ParticleTypes.SNOWFLAKE, w.center.x, y + 0.5, w.center.z, Math.min(40, placedHere * 2), w.cfg.width() * 0.4, 0.2,
					w.cfg.width() * 0.4, 0.02);
			ModSound.play(w.world, SoundIds.RUKIA_BANKAI_ICE_WALL_LAYER, w.center.x, y + 0.5, w.center.z, "block.amethyst_cluster.place", 0.7F, 0.8F + 0.12F * layer);
		}
	}

	/** Entities that must not be walled in: anything alive and vehicles (boats, minecarts). Items, orbs, projectiles and effect anchors do not count. */
	private static boolean blocksCell(Entity e) {
		return !e.isSpectator() && e.isAlive() && (e instanceof LivingEntity || e instanceof VehicleEntity);
	}

	private static boolean occupiedBy(List<Box> boxes, WallCell c) {
		for (Box b : boxes) {
			if (b.intersects(c.x(), c.y(), c.z(), c.x() + 1.0, c.y() + 1.0, c.z() + 1.0)) {
				return true;
			}
		}
		return false;
	}

	/** Takes the blocks of one layer down (the fade at the end of the life, or the shatter when the wall broke). */
	private static void removeLayer(MinecraftServer srv, Wall w, int layer, boolean shatter) {
		int n = 0;
		for (WallCell c : w.byLayer.get(layer)) {
			BlockPos pos = new BlockPos(c.x(), c.y(), c.z());
			if (w.placed.remove(pos.asLong())) {
				if (shatter && n % 3 == 0) {
					w.world.syncWorldEvent(2001, pos, Block.getRawIdFromState(ModBlocks.ICE_WALL.getDefaultState()));
				}
				TempBlocks.releaseCell(srv, w.dim, pos);
				n++;
			}
		}
		if (n > 0 && !shatter) {
			BlockPos first = new BlockPos(w.byLayer.get(layer).get(0).x(), w.byLayer.get(layer).get(0).y(), w.byLayer.get(layer).get(0).z());
			w.world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ModBlocks.ICE_WALL.getDefaultState()), w.center.x, first.getY() + 0.5,
					w.center.z, Math.min(24, n), w.cfg.width() * 0.35, 0.2, w.cfg.width() * 0.35, 0.05);
		}
	}

	/** Takes the whole wall down and forgets it. {@code shatter}: shards and the breaking sound (a broken or replaced wall). */
	private static void release(Wall w, String why, boolean shatter) {
		if (w == null) {
			return;
		}
		WALLS.remove(w.owner);
		MinecraftServer srv = w.world.getServer();
		int blocks = w.placed.size();
		for (int layer = 0; layer < w.layers; layer++) {
			removeLayer(srv, w, layer, shatter);
		}
		if (shatter && blocks > 0) {
			ModSound.play(w.world, SoundIds.RUKIA_BANKAI_ICE_WALL_BREAK, w.center.x, w.center.y, w.center.z, "block.glass.break", 1.4F, 0.7F);
		}
		ReiatsuTest.LOGGER.info(LOG + "{} wall ended ({}): {} blocks taken down, {} cells skipped, strength left {}", w.ownerName, why, blocks, w.skipped,
				String.format(java.util.Locale.ROOT, "%.1f", w.hp));
	}

	// ------------------------------------------------------------------ journal hooks

	/** The journal of an owner was rolled back (death, logout, dimension change, bankai or state end): drop the record, the blocks are already back. */
	static void forget(UUID owner) {
		Wall w = WALLS.remove(owner);
		if (w != null) {
			ReiatsuTest.LOGGER.info(LOG + "{} wall rolled back with the journal ({} blocks)", w.ownerName, w.placed.size());
		}
	}

	/** Server stop or a full rollback: every record goes. */
	static void forgetAll() {
		WALLS.clear();
	}

	// ------------------------------------------------------------------ what abilities ask

	/**
	 * An ability strikes the point {@code point} (the end of its ray, the centre of its burst): when a wall block lies within {@code reach} blocks,
	 * the wall spends {@code damage} of its strength and breaks at 0. Returns true when a wall took the hit.
	 */
	static boolean absorbNear(ServerWorld world, Vec3d point, double reach, double damage, String by) {
		if (WALLS.isEmpty() || damage <= 0.0) {
			return false;
		}
		int r = (int) Math.ceil(reach);
		BlockPos c = BlockPos.ofFloored(point);
		Wall hit = null;
		double best = Double.MAX_VALUE;
		for (Wall w : WALLS.values()) {
			if (w.world != world) {
				continue;
			}
			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					for (int dz = -r; dz <= r; dz++) {
						BlockPos pos = c.add(dx, dy, dz);
						if (w.placed.contains(pos.asLong())) {
							double d = KidoManager.distanceToBox(point, new Box(pos));
							if (d <= reach && d < best) {
								best = d;
								hit = w;
							}
						}
					}
				}
			}
		}
		if (hit == null) {
			return false;
		}
		hit.hp -= damage;
		ReiatsuTest.LOGGER.info(LOG + "{}'s wall took {} from {}: strength {} of {}", hit.ownerName, String.format(java.util.Locale.ROOT, "%.1f", damage), by,
				String.format(java.util.Locale.ROOT, "%.1f", Math.max(0.0, hit.hp)), String.format(java.util.Locale.ROOT, "%.1f", hit.cfg.hp()));
		ModSound.play(world, SoundIds.RUKIA_BANKAI_ICE_WALL_HIT, point.x, point.y, point.z, "block.glass.hit", 1.0F, 0.9F);
		if (hit.hp <= 0.0) {
			release(hit, "broken by " + by, true);
		}
		return true;
	}

	/** True when a wall block stands between {@code from} and {@code to} (the line is traced block by block). Cheap when no wall exists. */
	static boolean blocked(ServerWorld world, Vec3d from, Vec3d to) {
		if (WALLS.isEmpty() || !anyIn(world)) {
			return false;
		}
		Vec3d d = to.subtract(from);
		double len = d.length();
		if (len < 0.05) {
			return false;
		}
		Vec3d start = from.add(d.multiply(0.02 / len)); // a start exactly on a wall face counts as on the side it looks towards
		Boolean b = BlockView.raycast(start, to, world, (w, pos) -> w.getBlockState(pos).isOf(ModBlocks.ICE_WALL) ? Boolean.TRUE : null, w -> Boolean.FALSE);
		return Boolean.TRUE.equals(b);
	}

	/** Distance along the ray {@code from + dir * t} to the first wall block, or {@code length} when there is none (a ground wave stops there). */
	static double clipDistance(ServerWorld world, Vec3d from, Vec3d dir, double length) {
		if (WALLS.isEmpty() || !anyIn(world)) {
			return length;
		}
		Vec3d to = from.add(dir.multiply(length));
		BlockPos hit = BlockView.raycast(from, to, world, (w, pos) -> w.getBlockState(pos).isOf(ModBlocks.ICE_WALL) ? pos.toImmutable() : null, w -> null);
		if (hit == null) {
			return length;
		}
		Optional<Vec3d> p = new Box(hit).raycast(from, to);
		return p.map(from::distanceTo).orElse(0.0);
	}

	private static boolean anyIn(ServerWorld world) {
		for (Wall w : WALLS.values()) {
			if (w.world == world) {
				return true;
			}
		}
		return false;
	}

	/** Whether a block state is a wall block (tests of the glue). */
	public static boolean isWall(BlockState st) {
		return st.isOf(ModBlocks.ICE_WALL);
	}
}
