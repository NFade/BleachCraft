package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.registry.ModParticles;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Custom billboard particles P:x (1.3.1, 1.7.2). One spawn path applies the effect quality, the distance LOD and the
 * {@code maxFxParticles} cap; classes extend {@link SpriteBillboardParticle} in the translucent particle sheet. Particles
 * are positioned and sized in blocks, velocities in blocks per second (converted to the vanilla per tick values here).
 */
public final class FxParticles {
	public enum Kind {
		FROST_MOTE(true, 0), SNOWFLAKE(true, 0), ICE_SHARD(false, 10), PETAL(false, 8), WISP(true, 0), DUST_PUFF(false, 0);

		final boolean fullBright;
		final int minBlockLight;

		Kind(boolean fullBright, int minBlockLight) {
			this.fullBright = fullBright;
			this.minBlockLight = minBlockLight;
		}
	}

	private static final Map<Kind, FabricSpriteProvider> PROVIDERS = new EnumMap<>(Kind.class);
	private static final Set<FxParticle> LIVE = new LinkedHashSet<>();
	/** Statistics for the harness. */
	public static int spawned;
	public static int refused;
	private static final Random LOD_RNG = new Random(99);
	/** The particle created by the last successful {@link Spec#spawn()} (callers that need a handle, e.g. the aura). */
	static FxParticle lastSpawned;

	private FxParticles() {
	}

	public static int live() {
		return LIVE.size();
	}

	/** Client init: factories (also used by the /particle command) that capture the sprite providers. */
	public static void register() {
		reg(ModParticles.FROST_MOTE, Kind.FROST_MOTE);
		reg(ModParticles.SNOWFLAKE, Kind.SNOWFLAKE);
		reg(ModParticles.ICE_SHARD, Kind.ICE_SHARD);
		reg(ModParticles.PETAL, Kind.PETAL);
		reg(ModParticles.REIATSU_WISP, Kind.WISP);
		reg(ModParticles.DUST_PUFF, Kind.DUST_PUFF);
	}

	private static void reg(SimpleParticleType type, Kind kind) {
		ParticleFactoryRegistry.getInstance().register(type, sprites -> {
			PROVIDERS.put(kind, sprites);
			return (params, world, x, y, z, vx, vy, vz) -> {
				Spec s = new Spec(kind).at(x, y, z).vel(vx * 20, vy * 20, vz * 20);
				return s.build(world);
			};
		});
	}

	public static void clearAll() {
		for (FxParticle p : new java.util.ArrayList<>(LIVE)) {
			p.markDead();
		}
		LIVE.clear();
	}

	public static Spec spec(Kind kind) {
		return new Spec(kind);
	}

	/** Parameters of one particle; {@link #spawn()} applies quality, LOD and the cap. */
	public static final class Spec {
		final Kind kind;
		double x;
		double y;
		double z;
		double vx;
		double vy;
		double vz;
		float[] c0 = {1, 1, 1};
		float[] c1;
		double size0 = 0.2;
		double size1 = -1;
		int lifeTicks = 20;
		double drag = 1.0;
		double gravity;
		float alpha = 1f;
		float fadeFrac = 0.35f;
		float spin;
		int seed;
		boolean collide;
		int owner = -1;
		double lod = 1.0;
		float brightMul = 1f;

		Spec(Kind kind) {
			this.kind = kind;
		}

		public Spec at(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
			return this;
		}

		public Spec at(Vec3d p) {
			return at(p.x, p.y, p.z);
		}

		/** Blocks per second. */
		public Spec vel(double vx, double vy, double vz) {
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			return this;
		}

		public Spec vel(Vec3d v) {
			return vel(v.x, v.y, v.z);
		}

		public Spec color(String hex) {
			c0 = FxMath.hex(hex);
			return this;
		}

		public Spec color(float[] rgb) {
			c0 = rgb;
			return this;
		}

		/** Colour fades from the first to this one over the life. */
		public Spec colorTo(String hex) {
			c1 = FxMath.hex(hex);
			return this;
		}

		/** Full quad width in blocks; optional end size. */
		public Spec size(double s) {
			size0 = s;
			size1 = -1;
			return this;
		}

		public Spec size(double s0, double s1) {
			size0 = s0;
			size1 = s1;
			return this;
		}

		public Spec life(int ticks) {
			lifeTicks = Math.max(1, ticks);
			return this;
		}

		/** Velocity factor per tick. */
		public Spec drag(double perTick) {
			drag = perTick;
			return this;
		}

		/** Vanilla gravity strength (velocity y -= 0.04 x g per tick). */
		public Spec gravity(double g) {
			gravity = g;
			return this;
		}

		public Spec alpha(double a) {
			alpha = (float) a;
			return this;
		}

		public Spec fade(double fraction) {
			fadeFrac = (float) fraction;
			return this;
		}

		/** Radians per tick. */
		public Spec spin(double s) {
			spin = (float) s;
			return this;
		}

		public Spec seed(int s) {
			seed = s;
			return this;
		}

		public Spec collide() {
			collide = true;
			return this;
		}

		public Spec bright(double m) {
			brightMul = (float) m;
			return this;
		}

		public Spec owner(EffectTimeline t) {
			if (t != null) {
				owner = t.slot;
				lod = t.lod;
			}
			return this;
		}

		public Spec lod(double f) {
			lod = f;
			return this;
		}

		FxParticle build(ClientWorld world) {
			FabricSpriteProvider sp = PROVIDERS.get(kind);
			if (sp == null) {
				return null;
			}
			return kind == Kind.WISP ? new Wisp(world, this, sp) : new FxParticle(world, this, sp);
		}

		/** Spawns the particle unless the quality, LOD or cap refuses it. */
		public boolean spawn() {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.world == null) {
				return false;
			}
			double chance = FxConfig.effectQuality * lod;
			if (chance < 1.0 && LOD_RNG.nextDouble() > chance) {
				refused++;
				return false;
			}
			if (LIVE.size() >= FxConfig.maxFxParticles) {
				refused++;
				return false;
			}
			FxParticle p = build(mc.world);
			if (p == null) {
				return false;
			}
			mc.particleManager.addParticle(p);
			LIVE.add(p);
			lastSpawned = p;
			spawned++;
			return true;
		}
	}

	/** The common particle: size and colour curves, frame selection per kind, freeze with the FX clock. */
	static class FxParticle extends SpriteBillboardParticle {
		final Kind kind;
		final FabricSpriteProvider sprites;
		final double size0;
		final double size1;
		final double drag;
		final float[] c0;
		final float[] c1;
		final float a0;
		final float fadeFrac;
		final float spinRate;
		final int variant;
		final int ownerSlot;
		final boolean collide;
		final float brightMul;
		private int frame = -1;
		private boolean wasFrozen;

		FxParticle(ClientWorld world, Spec s, FabricSpriteProvider sprites) {
			super(world, s.x, s.y, s.z);
			this.kind = s.kind;
			this.sprites = sprites;
			this.size0 = s.size0;
			this.size1 = s.size1 < 0 ? s.size0 : s.size1;
			this.drag = s.drag;
			this.c0 = s.c0;
			this.c1 = s.c1 != null ? s.c1 : s.c0;
			this.a0 = s.alpha;
			this.fadeFrac = s.fadeFrac;
			this.spinRate = s.spin;
			this.variant = s.seed;
			this.ownerSlot = s.owner;
			this.collide = s.collide;
			this.brightMul = s.brightMul;
			this.velocityX = s.vx / 20.0;
			this.velocityY = s.vy / 20.0;
			this.velocityZ = s.vz / 20.0;
			this.gravityStrength = (float) s.gravity;
			this.maxAge = s.lifeTicks;
			this.collidesWithWorld = s.collide;
			this.scale = (float) (size0 * 0.5);
			this.red = c0[0];
			this.green = c0[1];
			this.blue = c0[2];
			this.alpha = a0;
			this.angle = (float) (this.random.nextFloat() * Math.PI * 2);
			if (kind == Kind.SNOWFLAKE || kind == Kind.ICE_SHARD || kind == Kind.PETAL) {
				this.prevAngle = this.angle;
			} else {
				this.angle = 0;
				this.prevAngle = 0;
			}
			this.setBoundingBoxSpacing(0.1f, 0.1f);
			pickSprite();
		}

		private void pickSprite() {
			int f;
			switch (kind) {
				case FROST_MOTE -> f = (age / 3 + variant) & 3;
				case SNOWFLAKE -> f = Math.floorMod(variant, 3);
				case ICE_SHARD -> f = variant & 3;
				case PETAL -> f = (age / 2 + variant) & 3;
				case WISP -> f = (age / 2 + variant) & 3;
				default -> f = Math.min(3, age * 4 / Math.max(1, maxAge));
			}
			if (f != frame) {
				frame = f;
				int count = kind == Kind.SNOWFLAKE ? 3 : 4;
				setSprite(sprites.getSprite(Math.min(f, count - 1), count - 1));
			}
		}

		/** Lets the particle fade out within the given ticks (the aura of a sealed sword). */
		void fadeOutWithin(int ticks) {
			this.age = Math.max(this.age, this.maxAge - ticks);
		}

		boolean frozen() {
			return FxClock.frozen || (ownerSlot >= 0 && FxTimelines.inHitstop(ownerSlot));
		}

		@Override
		public void tick() {
			if (frozen()) {
				this.prevPosX = this.x;
				this.prevPosY = this.y;
				this.prevPosZ = this.z;
				this.prevAngle = this.angle;
				return;
			}
			this.prevPosX = this.x;
			this.prevPosY = this.y;
			this.prevPosZ = this.z;
			this.prevAngle = this.angle;
			if (this.age++ >= this.maxAge) {
				this.markDead();
				return;
			}
			this.velocityY -= 0.04 * this.gravityStrength;
			if (collide) {
				this.move(this.velocityX, this.velocityY, this.velocityZ);
			} else {
				this.x += this.velocityX;
				this.y += this.velocityY;
				this.z += this.velocityZ;
			}
			this.velocityX *= drag;
			this.velocityY *= drag;
			this.velocityZ *= drag;
			if (this.onGround) {
				this.velocityX *= 0.7;
				this.velocityZ *= 0.7;
			}
			this.angle += spinRate;
			float t = (float) age / maxAge;
			if (c1 != c0) {
				this.red = MathHelper.lerp(t, c0[0], c1[0]);
				this.green = MathHelper.lerp(t, c0[1], c1[1]);
				this.blue = MathHelper.lerp(t, c0[2], c1[2]);
			}
			float fadeStart = 1f - fadeFrac;
			this.alpha = t > fadeStart ? a0 * Math.max(0f, 1f - (t - fadeStart) / fadeFrac) : a0;
			pickSprite();
		}

		@Override
		public float getSize(float tickDelta) {
			float t = Math.min(1f, (age + tickDelta) / maxAge);
			return (float) (FxMath.lerp(size0, size1, t) * 0.5);
		}

		@Override
		public ParticleTextureSheet getType() {
			return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
		}

		@Override
		protected int getBrightness(float tint) {
			if (kind.fullBright) {
				return 0xF000F0;
			}
			int base = super.getBrightness(tint);
			int block = Math.max(LightmapTextureManager.getBlockLightCoordinates(base), kind.minBlockLight);
			return LightmapTextureManager.pack(block, LightmapTextureManager.getSkyLightCoordinates(base));
		}

		@Override
		public void markDead() {
			if (!this.dead) {
				LIVE.remove(this);
			}
			super.markDead();
		}
	}

	/** reiatsu_wisp: vertical-axis billboard 0.15 x 0.6 (8 x 32 sprite), size = height. */
	static final class Wisp extends FxParticle {
		Wisp(ClientWorld world, Spec s, FabricSpriteProvider sprites) {
			super(world, s, sprites);
		}

		@Override
		public void buildGeometry(VertexConsumer vc, Camera camera, float tickDelta) {
			Vec3d cp = camera.getPos();
			float px = (float) (MathHelper.lerp(tickDelta, prevPosX, x) - cp.x);
			float py = (float) (MathHelper.lerp(tickDelta, prevPosY, y) - cp.y);
			float pz = (float) (MathHelper.lerp(tickDelta, prevPosZ, z) - cp.z);
			float h = (float) (FxMath.lerp(size0, size1, Math.min(1f, (age + tickDelta) / maxAge)) * 0.5);
			float w = h * 0.25f;
			double hl = Math.hypot(px, pz);
			float ux = hl < 1e-4 ? 1f : (float) (-pz / hl) * w;
			float uz = hl < 1e-4 ? 0f : (float) (px / hl) * w;
			int light = getBrightness(tickDelta);
			float u0 = getMinU();
			float u1 = getMaxU();
			float v0 = getMinV();
			float v1 = getMaxV();
			vc.vertex(px - ux, py - h, pz - uz).texture(u0, v1).color(red, green, blue, alpha).light(light);
			vc.vertex(px + ux, py - h, pz + uz).texture(u1, v1).color(red, green, blue, alpha).light(light);
			vc.vertex(px + ux, py + h, pz + uz).texture(u1, v0).color(red, green, blue, alpha).light(light);
			vc.vertex(px - ux, py + h, pz - uz).texture(u0, v0).color(red, green, blue, alpha).light(light);
		}
	}
}
