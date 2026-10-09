package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * All additive glow sprites (G:x of the storyboard, 1.7.1): a pooled struct-of-arrays ring buffer, positions in closed form
 * from the spawn state and the FX clock (no per-frame integration error), drawn once per frame at
 * {@code WorldRenderEvents.LAST} after the GRADE quad through the linear-filtered additive layer {@code reiatsu_glow}.
 * Intensity = tint x peak x rise/hold/fade curve x {@code glowIntensity}; cap {@code maxGlowSprites} (oldest replaced).
 */
public final class FxGlowBatch {
	public static final int CAMERA = 0;
	public static final int STREAK = 1;
	public static final int GROUND = 2;
	public static final int AXIS_Y = 3;
	public static final int PLANE = 4;

	private static final int CAP = 8192;
	public static final Identifier ATLAS = ReiatsuTest.id("textures/fx/fx_glow.png");

	/** Our own linear-filtered additive layer (1.2): colour mask only, no depth write, no culling. */
	private static RenderLayer layer;

	private static final double[] X = new double[CAP];
	private static final double[] Y = new double[CAP];
	private static final double[] Z = new double[CAP];
	private static final float[] VX = new float[CAP];
	private static final float[] VY = new float[CAP];
	private static final float[] VZ = new float[CAP];
	private static final double[] BIRTH = new double[CAP];
	private static final float[] LIFE = new float[CAP];
	private static final float[] SIZE0 = new float[CAP];
	private static final float[] SIZE1 = new float[CAP];
	private static final float[] ROT = new float[CAP];
	private static final float[] SPIN = new float[CAP];
	private static final float[] DRAG_PS = new float[CAP];
	private static final float[] GRAV = new float[CAP];
	private static final float[] R = new float[CAP];
	private static final float[] G = new float[CAP];
	private static final float[] B = new float[CAP];
	private static final float[] PEAK = new float[CAP];
	private static final float[] RISE = new float[CAP];
	private static final float[] FADE = new float[CAP];
	private static final float[] NX = new float[CAP];
	private static final float[] NY = new float[CAP];
	private static final float[] NZ = new float[CAP];
	private static final float[] STREAK_T = new float[CAP];
	private static final float[] TWINKLE = new float[CAP];
	private static final byte[] SPRITE = new byte[CAP];
	private static final byte[] FLAGS = new byte[CAP];
	private static final byte[] SIZE_EASE = new byte[CAP];
	private static final short[] OWNER = new short[CAP];
	private static final boolean[] ALIVE = new boolean[CAP];
	private static int head;
	private static int liveCount;

	// per frame stats
	public static int lastDrawn;
	public static double lastMs;
	public static double maxMs;
	public static long drawnTotal;
	private static long rngState = 0x9E3779B97F4A7C15L;

	private FxGlowBatch() {
	}

	public static void clear() {
		java.util.Arrays.fill(ALIVE, false);
		liveCount = 0;
	}

	public static int live() {
		return liveCount;
	}

	private static float rand() {
		rngState ^= rngState << 13;
		rngState ^= rngState >>> 7;
		rngState ^= rngState << 17;
		return (rngState >>> 40) / (float) (1L << 24);
	}

	/** Reusable parameter object: {@code FxGlowBatch.sprite(GlowSprite.STAR4).at(..).life(..).spawn()}. */
	public static final class Spec {
		GlowSprite sprite = GlowSprite.GLOW_SOFT;
		double x;
		double y;
		double z;
		float vx;
		float vy;
		float vz;
		double dragPerTick = 1.0;
		float gravity;
		float size0 = 1;
		float size1 = 1;
		int sizeEase = FxMath.LINEAR;
		float life = 1;
		float r = 1;
		float g = 1;
		float b = 1;
		float peak = 1;
		float rise = 0.05f;
		float fade = 0.4f;
		float rot;
		float spin;
		int flags = CAMERA;
		float nx;
		float ny = 1;
		float nz;
		boolean twinkle;
		float streakTime = 0.04f;
		int owner = -1;
		double lod = 1.0;

		private Spec reset(GlowSprite s) {
			sprite = s;
			x = y = z = 0;
			vx = vy = vz = 0;
			dragPerTick = 1.0;
			gravity = 0;
			size0 = size1 = 1;
			sizeEase = FxMath.LINEAR;
			life = 1;
			r = g = b = 1;
			peak = 1;
			rise = 0.05f;
			fade = 0.4f;
			rot = spin = 0;
			flags = CAMERA;
			nx = 0;
			ny = 1;
			nz = 0;
			twinkle = false;
			streakTime = 0.04f;
			owner = -1;
			lod = 1.0;
			return this;
		}

		public Spec at(double px, double py, double pz) {
			x = px;
			y = py;
			z = pz;
			return this;
		}

		/** Initial velocity in blocks per second. */
		public Spec vel(double vx, double vy, double vz) {
			this.vx = (float) vx;
			this.vy = (float) vy;
			this.vz = (float) vz;
			return this;
		}

		/** Velocity factor per tick (0.9 = loses 10 percent every 1/20 s). */
		public Spec drag(double perTick) {
			dragPerTick = perTick;
			return this;
		}

		/** Gravity in blocks per second squared (positive pulls down). */
		public Spec gravity(double g) {
			gravity = (float) g;
			return this;
		}

		public Spec size(double s0, double s1) {
			size0 = (float) s0;
			size1 = (float) s1;
			return this;
		}

		public Spec size(double s) {
			return size(s, s);
		}

		public Spec sizeEase(int ease) {
			sizeEase = ease;
			return this;
		}

		public Spec life(double seconds) {
			life = (float) seconds;
			return this;
		}

		public Spec lifeTicks(int ticks) {
			life = ticks / 20f;
			return this;
		}

		public Spec color(float[] rgb) {
			r = rgb[0];
			g = rgb[1];
			b = rgb[2];
			return this;
		}

		public Spec color(String hex) {
			return color(FxMath.hex(hex));
		}

		public Spec peak(double p) {
			peak = (float) p;
			return this;
		}

		/** Seconds to rise from 0 to the peak, and the fraction of the life used by the fade out. */
		public Spec curve(double riseSeconds, double fadeFraction) {
			rise = (float) riseSeconds;
			fade = (float) fadeFraction;
			return this;
		}

		public Spec rot(double radians, double spinPerSecond) {
			rot = (float) radians;
			spin = (float) spinPerSecond;
			return this;
		}

		public Spec flag(int f) {
			flags = f;
			return this;
		}

		public Spec ground() {
			flags = GROUND;
			return this;
		}

		public Spec axisY() {
			flags = AXIS_Y;
			return this;
		}

		public Spec streak() {
			flags = STREAK;
			return this;
		}

		public Spec plane(double nx, double ny, double nz) {
			flags = PLANE;
			double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (l < 1e-6) {
				l = 1;
				ny = 1;
			}
			this.nx = (float) (nx / l);
			this.ny = (float) (ny / l);
			this.nz = (float) (nz / l);
			return this;
		}

		public Spec twinkle() {
			twinkle = true;
			return this;
		}

		public Spec streakTime(double s) {
			streakTime = (float) s;
			return this;
		}

		/** Ties the sprite to a timeline so that its HITSTOP also freezes the sprite; also applies the timeline LOD. */
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

		public void spawn() {
			FxGlowBatch.spawn(this);
		}
	}

	private static final Spec SPEC = new Spec();

	/** Starts a new sprite description (single threaded, render thread only). */
	public static Spec sprite(GlowSprite s) {
		return SPEC.reset(s);
	}

	private static void spawn(Spec s) {
		if (s.lod < 1.0 && rand() > s.lod) {
			return;
		}
		int cap = Math.max(16, Math.min(CAP, FxConfig.maxGlowSprites));
		head = (head + 1) % cap;
		int i = head;
		if (!ALIVE[i]) {
			liveCount++;
		}
		ALIVE[i] = true;
		double held = s.owner >= 0 ? FxTimelines.held(s.owner) : 0;
		X[i] = s.x;
		Y[i] = s.y;
		Z[i] = s.z;
		VX[i] = s.vx;
		VY[i] = s.vy;
		VZ[i] = s.vz;
		BIRTH[i] = FxClock.now - held;
		LIFE[i] = Math.max(0.01f, s.life);
		SIZE0[i] = s.size0;
		SIZE1[i] = s.size1;
		ROT[i] = s.rot;
		SPIN[i] = s.spin;
		DRAG_PS[i] = (float) Math.pow(s.dragPerTick, 20.0);
		GRAV[i] = s.gravity;
		R[i] = s.r;
		G[i] = s.g;
		B[i] = s.b;
		PEAK[i] = s.peak;
		RISE[i] = s.rise;
		FADE[i] = s.fade;
		NX[i] = s.nx;
		NY[i] = s.ny;
		NZ[i] = s.nz;
		STREAK_T[i] = s.streakTime;
		TWINKLE[i] = s.twinkle ? rand() * 6.2831f + 1.0f : 0f;
		SPRITE[i] = (byte) s.sprite.ordinal();
		FLAGS[i] = (byte) s.flags;
		SIZE_EASE[i] = (byte) s.sizeEase;
		OWNER[i] = (short) s.owner;
	}

	private static RenderLayer layer() {
		if (layer == null) {
			layer = RenderLayer.of("reiatsu_glow", VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS, 1536, false, true,
					RenderLayer.MultiPhaseParameters.builder()
							.program(new RenderPhase.ShaderProgram(GameRenderer::getPositionTexColorProgram))
							.texture(new RenderPhase.Texture(ATLAS, true, false))
							.transparency(RenderPhase.ADDITIVE_TRANSPARENCY)
							.writeMaskState(RenderPhase.COLOR_MASK)
							.cull(RenderPhase.DISABLE_CULLING)
							.build(false));
		}
		return layer;
	}

	/** WorldRenderEvents.LAST (after the GRADE quad). */
	public static void draw(WorldRenderContext ctx) {
		if (liveCount <= 0) {
			lastDrawn = 0;
			lastMs = 0;
			return;
		}
		long t0 = System.nanoTime();
		MinecraftClient mc = MinecraftClient.getInstance();
		Camera cam = ctx.camera();
		var cp = cam.getPos();
		Quaternionf q = cam.getRotation();
		Vector3f right = new Vector3f(1, 0, 0).rotate(q);
		Vector3f up = new Vector3f(0, 1, 0).rotate(q);
		double yaw = Math.toRadians(cam.getYaw());
		double pitch = Math.toRadians(cam.getPitch());
		// forward = look vector (yaw 0 = +z, pitch positive looks down)
		double fx = -Math.sin(yaw) * Math.cos(pitch);
		double fy = -Math.sin(pitch);
		double fz = Math.cos(yaw) * Math.cos(pitch);
		double pxPerUnit = mc.getWindow().getFramebufferHeight() / (2.0 * Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0));
		double boost = FxConfig.glowIntensity * (ScreenFx.gradeStrong() ? 1.3 : 1.0);
		double now = FxClock.now;
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		int drawn = 0;
		int cap = Math.min(CAP, Math.max(16, FxConfig.maxGlowSprites));
		for (int i = 0; i < CAP; i++) {
			if (!ALIVE[i]) {
				continue;
			}
			double held = OWNER[i] >= 0 ? FxTimelines.held(OWNER[i]) : 0;
			double age = now - held - BIRTH[i];
			double life = LIFE[i];
			if (age >= life) {
				ALIVE[i] = false;
				liveCount--;
				continue;
			}
			if (age < 0 || i >= cap) {
				continue;
			}
			// closed-form position
			double dps = DRAG_PS[i];
			double travel = dps >= 0.9999 ? age : (Math.pow(dps, age) - 1.0) / Math.log(dps);
			double px = X[i] + VX[i] * travel;
			double py = Y[i] + VY[i] * travel - 0.5 * GRAV[i] * age * age;
			double pz = Z[i] + VZ[i] * travel;
			double rx = px - cp.x;
			double ry = py - cp.y;
			double rz = pz - cp.z;
			double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
			if (dist > 96.0 || rx * fx + ry * fy + rz * fz <= 0) {
				continue;
			}
			double t = age / life;
			double sz = FxMath.lerp(SIZE0[i], SIZE1[i], FxMath.ease(SIZE_EASE[i], t));
			if (sz * pxPerUnit / Math.max(dist, 0.1) < 0.5) {
				continue;
			}
			// intensity: rise, hold, smooth fade
			double in = RISE[i] > 0 ? FxMath.clamp(age / RISE[i]) : 1.0;
			in = in * in * (3 - 2 * in);
			double fadeStart = life * (1.0 - FADE[i]);
			double out = 1.0;
			if (age > fadeStart && life > fadeStart) {
				double x = (age - fadeStart) / (life - fadeStart);
				out = 1.0 - x * x * (3 - 2 * x);
			}
			double inten = PEAK[i] * in * out * boost;
			if (TWINKLE[i] != 0f) {
				inten *= 0.6 + 0.4 * Math.sin(2 * Math.PI * 8 * age + TWINKLE[i]);
			}
			if (inten < 0.004) {
				continue;
			}
			float cr = (float) Math.min(1.0, R[i] * inten);
			float cg = (float) Math.min(1.0, G[i] * inten);
			float cb = (float) Math.min(1.0, B[i] * inten);
			GlowSprite sp = GlowSprite.of(SPRITE[i]);
			double hw = sz * 0.5;
			double hh = sz * sp.aspect * 0.5;
			double rot = ROT[i] + SPIN[i] * age;
			float ax;
			float ay;
			float az;
			float bx;
			float by;
			float bz;
			float cx = (float) rx;
			float cy = (float) ry;
			float cz = (float) rz;
			switch (FLAGS[i]) {
				case STREAK -> {
					double vx = VX[i] * Math.pow(Math.max(dps, 1e-4), age);
					double vy = VY[i] * Math.pow(Math.max(dps, 1e-4), age) - GRAV[i] * age;
					double vz = VZ[i] * Math.pow(Math.max(dps, 1e-4), age);
					double dr = vx * right.x + vy * right.y + vz * right.z;
					double du = vx * up.x + vy * up.y + vz * up.z;
					double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
					double dl = Math.hypot(dr, du);
					double dirR = dl < 1e-4 ? 1 : dr / dl;
					double dirU = dl < 1e-4 ? 0 : du / dl;
					double len = Math.max(sz, speed * STREAK_T[i]);
					double hl = len * 0.5;
					double ht = len * sp.aspect * 0.5;
					// u axis = direction of travel (screen projected), head at +u
					float ux = (float) ((right.x * dirR + up.x * dirU) * hl);
					float uy = (float) ((right.y * dirR + up.y * dirU) * hl);
					float uz = (float) ((right.z * dirR + up.z * dirU) * hl);
					float vxx = (float) ((-right.x * dirU + up.x * dirR) * ht);
					float vyy = (float) ((-right.y * dirU + up.y * dirR) * ht);
					float vzz = (float) ((-right.z * dirU + up.z * dirR) * ht);
					cx -= ux;
					cy -= uy;
					cz -= uz;
					ax = ux;
					ay = uy;
					az = uz;
					bx = vxx;
					by = vyy;
					bz = vzz;
				}
				case GROUND -> {
					double c = Math.cos(rot);
					double s = Math.sin(rot);
					ax = (float) (c * hw);
					ay = 0;
					az = (float) (s * hw);
					bx = (float) (-s * hh);
					by = 0;
					bz = (float) (c * hh);
				}
				case AXIS_Y -> {
					double hl = Math.hypot(rx, rz);
					double ux = hl < 1e-4 ? 1 : -rz / hl;
					double uz = hl < 1e-4 ? 0 : rx / hl;
					ax = (float) (ux * hw);
					ay = 0;
					az = (float) (uz * hw);
					bx = 0;
					by = (float) hh;
					bz = 0;
				}
				case PLANE -> {
					double nx = NX[i];
					double ny = NY[i];
					double nz = NZ[i];
					// tangent = up x n (falls back to x when the normal is vertical)
					double tx = ny * 0 - nz * 1;
					double ty = 0;
					double tz = nx * 1 - ny * 0;
					tx = -nz;
					tz = nx;
					double tl = Math.hypot(tx, tz);
					if (tl < 1e-4) {
						tx = 1;
						tz = 0;
					} else {
						tx /= tl;
						tz /= tl;
					}
					double bx0 = ny * tz - nz * ty;
					double by0 = nz * tx - nx * tz;
					double bz0 = nx * ty - ny * tx;
					double c = Math.cos(rot);
					double s = Math.sin(rot);
					double ux = tx * c + bx0 * s;
					double uy = ty * c + by0 * s;
					double uz = tz * c + bz0 * s;
					double vx = -tx * s + bx0 * c;
					double vy = -ty * s + by0 * c;
					double vz = -tz * s + bz0 * c;
					ax = (float) (ux * hw);
					ay = (float) (uy * hw);
					az = (float) (uz * hw);
					bx = (float) (vx * hh);
					by = (float) (vy * hh);
					bz = (float) (vz * hh);
				}
				default -> {
					double c = Math.cos(rot);
					double s = Math.sin(rot);
					double ur = c * hw;
					double uu = s * hw;
					double vr = -s * hh;
					double vu = c * hh;
					ax = (float) (right.x * ur + up.x * uu);
					ay = (float) (right.y * ur + up.y * uu);
					az = (float) (right.z * ur + up.z * uu);
					bx = (float) (right.x * vr + up.x * vu);
					by = (float) (right.y * vr + up.y * vu);
					bz = (float) (right.z * vr + up.z * vu);
				}
			}
			buf.vertex(cx - ax - bx, cy - ay - by, cz - az - bz).texture(sp.u0, sp.v1).color(cr, cg, cb, 1f);
			buf.vertex(cx + ax - bx, cy + ay - by, cz + az - bz).texture(sp.u1, sp.v1).color(cr, cg, cb, 1f);
			buf.vertex(cx + ax + bx, cy + ay + by, cz + az + bz).texture(sp.u1, sp.v0).color(cr, cg, cb, 1f);
			buf.vertex(cx - ax + bx, cy - ay + by, cz - az + bz).texture(sp.u0, sp.v0).color(cr, cg, cb, 1f);
			drawn++;
		}
		lastDrawn = drawn;
		net.minecraft.client.render.BuiltBuffer built = buf.endNullable();
		if (built != null) {
			layer().draw(built);
		}
		drawnTotal += drawn;
		lastMs = (System.nanoTime() - t0) / 1.0e6;
		if (lastMs > maxMs) {
			maxMs = lastMs;
		}
	}
}
