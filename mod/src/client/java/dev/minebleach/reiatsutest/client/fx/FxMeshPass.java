package dev.minebleach.reiatsutest.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The instanced mesh renderers of the Rukia effects (1.7.3), all drawn in one pass at {@code WorldRenderEvents.AFTER_ENTITIES}
 * through the entity vertex consumers (translucent entity layer of the bankai texture, then an emissive overlay layer):
 * <ul>
 * <li>crystals (growth with ob, base pinned, emissive pulse, shatter into shards),</li>
 * <li>ice shells on entities (ENCASED alpha 0.6, FROZEN alpha 0.35; grow bottom up in 0.25 s; shatter),</li>
 * <li>shard bursts: pooled {@code rukia_bankai_shard_a/b} instances on closed form ballistic paths with a ground clamp.</li>
 * </ul>
 * Everything is derived from the FX clock, so a frozen clock freezes the meshes too.
 */
public final class FxMeshPass {
	/** Gravity of the shards in blocks per second squared (0.05 per tick squared of the storyboard). */
	private static final double SHARD_GRAVITY = 20.0;

	// ------------------------------------------------------------------------------------------------ crystals

	public static final class Crystal {
		public final String mesh;
		public final double x;
		public final double y;
		public final double z;
		final Quaternionf orient;
		public final double scale;
		public double birth;
		public double grow = 0.35;
		public double dieAt = Double.MAX_VALUE;
		public double pulsePhase;
		public double pulseHz = 0.3;
		public double emissive = 1.0;
		/** Entity id of the owner of a standing field (-1 for one shot effects). */
		public long field = -1;
		boolean dead;
		boolean glinted;

		Crystal(String mesh, double x, double y, double z, Quaternionf orient, double scale, double birth) {
			this.mesh = mesh;
			this.x = x;
			this.y = y;
			this.z = z;
			this.orient = orient;
			this.scale = scale;
			this.birth = birth;
		}

		public double height() {
			return FxMeshes.get(mesh).height * scale;
		}

		/** World position of the tip. */
		public Vec3d tip() {
			Vector3f v = new Vector3f(0, (float) height(), 0).rotate(orient);
			return new Vec3d(x + v.x, y + v.y, z + v.z);
		}

		public Vec3d mid() {
			Vector3f v = new Vector3f(0, (float) height() * 0.5f, 0).rotate(orient);
			return new Vec3d(x + v.x, y + v.y, z + v.z);
		}

		public boolean grown(double now) {
			return now - birth >= grow;
		}
	}

	private static final List<Crystal> CRYSTALS = new ArrayList<>();

	/** Rotation taking the mesh +Y to the (normalised here) direction, with a roll about it. */
	public static Quaternionf orientation(double dx, double dy, double dz, double roll) {
		double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (l < 1e-6) {
			dx = 0;
			dy = 1;
			dz = 0;
			l = 1;
		}
		Quaternionf q = new Quaternionf().rotationTo(0f, 1f, 0f, (float) (dx / l), (float) (dy / l), (float) (dz / l));
		return q.rotateY((float) roll); // roll about the mesh own long axis (applied first)
	}

	/** Spawns a crystal at {@code delay} seconds from now; base pinned at (x, y, z), pointing along (dx, dy, dz). */
	public static Crystal crystal(String mesh, double x, double y, double z, double dx, double dy, double dz, double roll, double scale, double delay) {
		if (CRYSTALS.size() >= Math.max(1, FxConfig.maxCrystals)) {
			CRYSTALS.remove(0);
		}
		Crystal c = new Crystal(mesh, x, y, z, orientation(dx, dy, dz, roll), scale, FxClock.now + delay);
		c.pulsePhase = (x * 0.37 + z * 0.61) % 1.0;
		CRYSTALS.add(c);
		return c;
	}

	/** Shatters (and removes) a crystal into {@code shards} shard meshes. */
	public static void shatter(Crystal c, int shards, double speed, long seed) {
		if (c == null || c.dead) {
			return;
		}
		c.dead = true;
		Vec3d m = c.mid();
		shards(m.x, m.y, m.z, Math.max(0.25, c.height() * 0.3), speed, shards, seed, 2.0 + c.scale * 0.8, 0.0);
	}

	public static void shatterField(long field, int shardsEach, double speed) {
		long s = field * 31;
		for (Crystal c : new ArrayList<>(CRYSTALS)) {
			if (c.field == field) {
				shatter(c, shardsEach, speed, s++);
			}
		}
	}

	public static List<Crystal> crystals() {
		return CRYSTALS;
	}

	public static int crystalCount() {
		return CRYSTALS.size();
	}

	// ------------------------------------------------------------------------------------------------ shards

	private static final class Shard {
		double x;
		double y;
		double z;
		double vx;
		double vy;
		double vz;
		float ax;
		float ay;
		float az;
		float spin;
		float scale;
		double birth;
		double groundY;
		boolean b;
		double restAt = -1;
		float rot0;
	}

	private static final List<Shard> SHARDS = new ArrayList<>();

	/**
	 * A burst of shard meshes from a point: velocity outward (horizontal, {@code speed} blocks per second +-40 percent) plus 2 b/s up,
	 * spin 2 to 8 rad/s, gravity 0.05 per tick squared, lying on the ground for a moment, then fading in 0.3 s.
	 */
	public static void shards(double cx, double cy, double cz, double spread, double speed, int count, long seed, double scale, double delay) {
		if (count <= 0) {
			return;
		}
		Random r = new Random(seed * 0x9E3779B97F4A7C15L + 77);
		double ground = FxGround.sample(cx, cz, cy);
		int cap = Math.max(1, FxConfig.maxShardMeshes);
		for (int i = 0; i < count; i++) {
			if (SHARDS.size() >= cap) {
				SHARDS.remove(0);
			}
			Shard s = new Shard();
			double a = r.nextDouble() * Math.PI * 2;
			double ra = Math.sqrt(r.nextDouble()) * spread;
			s.x = cx + Math.cos(a) * ra;
			s.y = cy + (r.nextDouble() - 0.5) * spread;
			s.z = cz + Math.sin(a) * ra;
			double sp = speed * (0.6 + 0.8 * r.nextDouble());
			s.vx = Math.cos(a) * sp * (0.4 + 0.6 * r.nextDouble());
			s.vz = Math.sin(a) * sp * (0.4 + 0.6 * r.nextDouble());
			s.vy = 2.0 + r.nextDouble() * 3.0 + sp * 0.25;
			Vector3f ax = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian());
			if (ax.lengthSquared() < 1e-4f) {
				ax.set(0, 1, 0);
			}
			ax.normalize();
			s.ax = ax.x;
			s.ay = ax.y;
			s.az = ax.z;
			s.spin = (float) (2.0 + r.nextDouble() * 6.0);
			s.rot0 = (float) (r.nextDouble() * Math.PI * 2);
			s.scale = (float) (scale * FxTune.d("shard.scale", 1.0) * (0.7 + 0.7 * r.nextDouble()));
			s.birth = FxClock.now + delay;
			s.groundY = Math.min(ground, cy);
			s.b = r.nextBoolean();
			SHARDS.add(s);
		}
	}

	public static int shardCount() {
		return SHARDS.size();
	}

	// ------------------------------------------------------------------------------------------------ shells

	public static final class Shell {
		public final int entityId;
		public final boolean encased;
		public final double birth;
		public double endAt;
		double lastX;
		double lastY;
		double lastZ;
		double w = 0.8;
		double h = 1.9;
		double nextGlint;
		boolean dead;

		Shell(int entityId, boolean encased, double birth, double endAt) {
			this.entityId = entityId;
			this.encased = encased;
			this.birth = birth;
			this.endAt = endAt;
		}

		public double height() {
			return h;
		}

		public Vec3d center() {
			return new Vec3d(lastX, lastY + h * 0.5, lastZ);
		}
	}

	private static final List<Shell> SHELLS = new ArrayList<>();

	/** Puts a shell on an entity; an existing shell of the same entity is renewed. Returns the shell. */
	public static Shell shell(int entityId, boolean encased, double endAtClock) {
		for (Shell s : SHELLS) {
			if (s.entityId == entityId && !s.dead) {
				s.endAt = Math.max(s.endAt, endAtClock);
				return s;
			}
		}
		if (SHELLS.size() >= Math.max(1, FxConfig.maxIceShells)) {
			SHELLS.remove(0);
		}
		Shell s = new Shell(entityId, encased, FxClock.now, endAtClock);
		Entity e = MinecraftClient.getInstance().world == null ? null : MinecraftClient.getInstance().world.getEntityById(entityId);
		if (e != null) {
			s.lastX = e.getX();
			s.lastY = e.getY();
			s.lastZ = e.getZ();
			s.w = Math.max(0.3, e.getWidth());
			s.h = Math.max(0.3, e.getHeight());
		}
		SHELLS.add(s);
		return s;
	}

	public static List<Shell> shells() {
		return SHELLS;
	}

	/** Shatters the shell of one entity (HIT or the end of its effect): shard meshes and the sound are the caller's. Returns it or null. */
	public static Shell breakShell(int entityId, int shards, double speed) {
		for (Shell s : SHELLS) {
			if (s.entityId == entityId && !s.dead) {
				s.dead = true;
				shards(s.lastX, s.lastY + s.h * 0.5, s.lastZ, Math.max(s.w, s.h * 0.3), speed, shards, entityId * 131L + (long) (s.birth * 100), 2.4 + s.h, 0.0);
				return s;
			}
		}
		return null;
	}

	public static void clear() {
		CRYSTALS.clear();
		SHARDS.clear();
		SHELLS.clear();
	}

	public static void clearField(long field) {
		CRYSTALS.removeIf(c -> c.field == field);
	}

	// ------------------------------------------------------------------------------------------------ draw

	private static int light(ClientWorld w, double x, double y, double z, int minBlock) {
		int l = WorldRenderer.getLightmapCoordinates(w, BlockPos.ofFloored(x, y, z));
		return LightmapTextureManager.pack(Math.max(LightmapTextureManager.getBlockLightCoordinates(l), minBlock), LightmapTextureManager.getSkyLightCoordinates(l));
	}

	public static int lastVertices;

	/** WorldRenderEvents.AFTER_ENTITIES. */
	public static void draw(WorldRenderContext ctx) {
		lastVertices = 0;
		if (CRYSTALS.isEmpty() && SHARDS.isEmpty() && SHELLS.isEmpty()) {
			return;
		}
		VertexConsumerProvider vcp = ctx.consumers();
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld world = mc.world;
		if (vcp == null || world == null) {
			return;
		}
		Vec3d cam = ctx.camera().getPos();
		float td = ctx.tickCounter().getTickDelta(false);
		double now = FxClock.now;
		update(world, now, td);
		if (!FxMeshes.available()) {
			return;
		}
		double glow = FxConfig.glowIntensity;
		Matrix4f m = new Matrix4f();
		// ---- pass 1a: the shells (lit translucent entity layer of the bankai texture)
		VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(FxMeshes.DIFFUSE));
		for (Shell s : SHELLS) {
			if (s.dead) {
				continue;
			}
			double age = now - s.birth;
			if (age < 0) {
				continue;
			}
			double grow = FxMath.oc(age / (s.encased ? 0.25 : 0.4));
			double alpha = (s.encased ? 0.6 : 0.35) * FxMath.clamp(grow * 3);
			double sx = s.w * 1.2;
			double sy = s.h * 1.06 / 2.0 * Math.max(0.05, grow);
			m.identity().translate((float) (s.lastX - cam.x), (float) (s.lastY - cam.y), (float) (s.lastZ - cam.z)).scale((float) sx, (float) sy, (float) sx);
			lastVertices += FxMeshes.draw(vc, FxMeshes.get(FxMeshes.SHELL), m, FxMeshes.argb(1, 1, 1, alpha), light(world, s.lastX, s.lastY + s.h * 0.5, s.lastZ, 8));
		}
		// ---- pass 1b: crystals and shards through the unlit emissive layer with the diffuse texture: no face shading, so ice stays bright and glassy at any hour
		vc = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(FxMeshes.DIFFUSE));
		for (Crystal c : CRYSTALS) {
			double age = now - c.birth;
			if (age < 0 || c.dead) {
				continue;
			}
			if (cam.squaredDistanceTo(c.x, c.y, c.z) > 160 * 160) {
				continue;
			}
			double s = c.scale * FxMath.ob(age / c.grow);
			m.identity().translate((float) (c.x - cam.x), (float) (c.y - cam.y), (float) (c.z - cam.z)).rotate(c.orient).scale((float) s);
			lastVertices += FxMeshes.draw(vc, FxMeshes.get(c.mesh), m, FxMeshes.argb(1, 1, 1, FxTune.d("crystal.alpha", 0.88)), 0xF000F0);
		}
		for (Shard sh : SHARDS) {
			double age = now - sh.birth;
			if (age < 0) {
				continue;
			}
			double[] p = shardPos(sh, age);
			double fade = sh.restAt < 0 ? 1.0 : 1.0 - FxMath.clamp((now - sh.restAt - 1.0) / 0.3);
			if (fade <= 0.01) {
				continue;
			}
			float angle = sh.restAt < 0 ? sh.rot0 + sh.spin * (float) age : sh.rot0 + sh.spin * (float) (sh.restAt - sh.birth);
			m.identity().translate((float) (p[0] - cam.x), (float) (p[1] - cam.y), (float) (p[2] - cam.z)).rotate(angle, sh.ax, sh.ay, sh.az).scale(sh.scale);
			lastVertices += FxMeshes.draw(vc, FxMeshes.get(sh.b ? FxMeshes.SHARD_B : FxMeshes.SHARD_A), m, FxMeshes.argb(1, 1, 1, 0.92 * fade), 0xF000F0);
		}
		// ---- pass 2: emissive overlay (crystals pulse, shells glow a little)
		VertexConsumer ve = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(FxMeshes.EMISSIVE));
		float[] tint = FxMath.hex("#DFF3FF");
		for (Crystal c : CRYSTALS) {
			double age = now - c.birth;
			if (age < 0 || c.dead || cam.squaredDistanceTo(c.x, c.y, c.z) > 48 * 48) {
				continue;
			}
			double pulse = 0.6 + 0.4 * (0.5 + 0.5 * Math.sin(2 * Math.PI * (c.pulseHz * now + c.pulsePhase)));
			double s = c.scale * FxMath.ob(age / c.grow);
			m.identity().translate((float) (c.x - cam.x), (float) (c.y - cam.y), (float) (c.z - cam.z)).rotate(c.orient).scale((float) (s * 1.01));
			lastVertices += FxMeshes.draw(ve, FxMeshes.get(c.mesh), m, FxMeshes.argb(tint, c.emissive * pulse * glow), 0xF000F0);
		}
		for (Shard sh : SHARDS) {
			double age = now - sh.birth;
			double fade = sh.restAt < 0 ? 1.0 : 1.0 - FxMath.clamp((now - sh.restAt - 1.0) / 0.3);
			if (age < 0 || fade <= 0.01 || cam.squaredDistanceTo(sh.x, sh.y, sh.z) > 40 * 40) {
				continue;
			}
			double[] p = shardPos(sh, age);
			float angle = sh.restAt < 0 ? sh.rot0 + sh.spin * (float) age : sh.rot0 + sh.spin * (float) (sh.restAt - sh.birth);
			m.identity().translate((float) (p[0] - cam.x), (float) (p[1] - cam.y), (float) (p[2] - cam.z)).rotate(angle, sh.ax, sh.ay, sh.az).scale(sh.scale * 1.02f);
			lastVertices += FxMeshes.draw(ve, FxMeshes.get(sh.b ? FxMeshes.SHARD_B : FxMeshes.SHARD_A), m, FxMeshes.argb(tint, 0.55 * fade * glow), 0xF000F0);
		}
		for (Shell s : SHELLS) {
			if (s.dead || now - s.birth < 0 || cam.squaredDistanceTo(s.lastX, s.lastY, s.lastZ) > 48 * 48) {
				continue;
			}
			double grow = FxMath.oc((now - s.birth) / 0.25);
			m.identity().translate((float) (s.lastX - cam.x), (float) (s.lastY - cam.y), (float) (s.lastZ - cam.z))
					.scale((float) (s.w * 1.2), (float) (s.h * 1.06 / 2.0 * Math.max(0.05, grow)), (float) (s.w * 1.2));
			lastVertices += FxMeshes.draw(ve, FxMeshes.get(FxMeshes.SHELL), m, FxMeshes.argb(tint, 0.30 * glow * FxMath.clamp(grow * 3)), 0xF000F0);
		}
	}

	private static double[] shardPos(Shard sh, double age) {
		double t = sh.restAt < 0 ? age : sh.restAt - sh.birth;
		double x = sh.x + sh.vx * t;
		double y = sh.y + sh.vy * t - 0.5 * SHARD_GRAVITY * t * t;
		double z = sh.z + sh.vz * t;
		return new double[] {x, Math.max(y, sh.groundY + 0.03), z};
	}

	private static final Vector3f TMP = new Vector3f();

	/** Per frame bookkeeping: shell positions follow their entity, ends, shard rest detection, crystal expiry. */
	private static void update(ClientWorld world, double now, float td) {
		for (Iterator<Shell> it = SHELLS.iterator(); it.hasNext();) {
			Shell s = it.next();
			if (s.dead) {
				it.remove();
				continue;
			}
			Entity e = world.getEntityById(s.entityId);
			if (e != null && !e.isRemoved()) {
				Vec3d p = e.getLerpedPos(td);
				s.lastX = p.x;
				s.lastY = p.y;
				s.lastZ = p.z;
				s.w = Math.max(0.3, e.getWidth());
				s.h = Math.max(0.3, e.getHeight());
			}
			boolean gone = e == null || e.isRemoved() || (e instanceof net.minecraft.entity.LivingEntity le && le.isDead());
			if (now >= s.endAt || gone || now - s.birth > 10.0) {
				s.dead = true;
				shards(s.lastX, s.lastY + s.h * 0.5, s.lastZ, Math.max(s.w, s.h * 0.3), 4.5, 8, s.entityId * 131L + (long) (s.birth * 100), 2.4 + s.h, 0.0);
				it.remove();
			}
		}
		for (Shard sh : SHARDS) {
			if (sh.restAt < 0) {
				double age = now - sh.birth;
				if (age > 0 && sh.y + sh.vy * age - 0.5 * SHARD_GRAVITY * age * age <= sh.groundY + 0.03 && age > 0.1) {
					sh.restAt = now;
				}
			}
		}
		SHARDS.removeIf(sh -> sh.restAt >= 0 ? now - sh.restAt > 1.35 : now - sh.birth > 6.0);
		CRYSTALS.removeIf(c -> c.dead || now > c.dieAt);
	}
}
