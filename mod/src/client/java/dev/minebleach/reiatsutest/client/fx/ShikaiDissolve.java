package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.DissolveMath;
import dev.minebleach.reiatsutest.core.fx.DissolveSwirl;
import dev.minebleach.reiatsutest.core.fx.FxHash;
import dev.minebleach.reiatsutest.core.fx.SwarmMath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Byakuya shikai release, part 2 (plan G-glow, round 2). Right after the pink hold ({@link ReleaseHold}) the drawn blade glows pink and
 * white, swells, and breaks into petals from the tip down to the hilt over {@code release.dissolveTime} seconds, as in the anime:
 * <ul>
 * <li>in the hand ({@code ObjItemBakedModel}): the blade is a set of small fragments; the ones behind the front fly off in a
 * spiral for a moment (model space, so they stay on the sword in first and third person), a stack of translucent shells blooms
 * around the rest ({@link #look});</li>
 * <li>in the world (this class): each fragment becomes a lit petal card of the swarm texture that spirals up and sideways with a glowing
 * trail ({@link DissolveSwirl}), additive halos run along the blade, a flash sits on the front, sparks and a light haze fall
 * off it; in its last part every petal blends towards a slot of the owner's {@link SwarmFx} orbit and fades, so the petals join the
 * swarm without a jump.</li>
 * </ul>
 * The world part needs the pose of the sword: the hand item render hands over the model to view matrix ({@link #capturePose}).
 * Everything is clocked by the FX clock. {@code release.dissolveTime} 0 turns the dissolve off. All look values are
 * {@code fx_override.json} keys, see {@code design/results/shikai_dissolve2.md}. Nothing here has been seen in the game (UNVERIFIED).
 */
public final class ShikaiDissolve {
	private static final Identifier TEXTURE = ReiatsuTest.id("textures/item/byakuya_shikai_diffuse.png");
	private static final long POSE_FRESH_NANOS = 200_000_000L;
	/** Cap of the blade length that scales the flight of the petals (the first person sword is drawn large). */
	private static final double FLIGHT_LENGTH_CAP = 1.25;

	/** Pose of the sword of one entity in the current frame: model to view matrix of the item and the model-space blade data. */
	private static final class Pose {
		final Matrix4f m = new Matrix4f();
		boolean fp;
		long nanos;
		float[] base;
		float[] dir;
		float len;
		float[] shardCtr;
		float[] shardS;
	}

	/** One dissolve of one caster. */
	private static final class Run {
		final int id;
		final double holdStart;
		final double start;
		final double dur;
		final long seed;
		// world pose of the blade (last fresh)
		boolean haveWorld;
		boolean fresh;
		final double[] wb = new double[3];
		final double[] wd = new double[3];
		double wl = 1.0;
		// petals
		boolean petalsInit;
		int n;
		int[] shard;
		double[] s;
		double[] det;
		double[][] prm;
		boolean[] released;
		double[] ox;
		double[] oy;
		double[] oz;
		int[] slot;
		// per frame output of the petals (absolute world)
		boolean[] drawn;
		double[] px;
		double[] py;
		double[] pz;
		float[] vx;
		float[] vy;
		float[] vz;
		float[] heat;
		float[] env;
		float[] fade;
		// frame values of the blade glow
		double level;
		double p = -1;
		double hold;
		double sparkDebt;
		double smokeDebt;
		boolean fpOwner;

		Run(int id, double holdStart, double start, double dur, long seed) {
			this.id = id;
			this.holdStart = holdStart;
			this.start = start;
			this.dur = dur;
			this.seed = seed;
		}
	}

	private static final Map<Integer, Run> RUNS = new HashMap<>();
	private static final Map<Integer, Pose> POSES = new HashMap<>();
	/** Harness statistics. */
	public static int started;
	public static int lastPetals;
	public static int lastSparks;
	public static int totalSparks;
	public static double lastMs;

	private static RenderLayer layer;
	private static FxMesh mesh;
	private static boolean meshTried;
	private static final Vector3f TMP = new Vector3f();

	private ShikaiDissolve() {
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(ShikaiDissolve::draw);
		FxGlowBatch.addSource(ShikaiDissolve::emitGlow);
	}

	/** Starts the dissolve {@code delay} seconds from now (the end of the pink hold). */
	public static void start(int entityId, double delay) {
		double dur = FxTune.d("release.dissolveTime", 0.85);
		if (dur <= 0.01) {
			return;
		}
		double now = FxClock.now;
		long seed = FxHash.mix(entityId * 0x9E3779B1L + (long) (now * 1000.0));
		RUNS.put(entityId, new Run(entityId, now, now + Math.max(0, delay), dur, seed));
		started++;
	}

	public static void clear() {
		RUNS.clear();
		POSES.clear();
	}

	public static boolean tipFirst() {
		return FxTune.d("release.dissolveTipFirst", 1.0) > 0.5;
	}

	/** 0..1 through the dissolve, or -1 before it starts and after it ends (the run itself lives on for the petals). */
	public static double progress(int entityId) {
		Run r = RUNS.get(entityId);
		if (r == null) {
			return -1;
		}
		double p = (FxClock.now - r.start) / r.dur;
		return p >= 1.0 || p < 0 ? -1 : p;
	}

	public static boolean active(int entityId) {
		return progress(entityId) >= 0;
	}

	/** True while the world part of an effect of this caster is alive (the item render then hands over its pose). */
	public static boolean wantsPose(int entityId) {
		return RUNS.containsKey(entityId) || PetalBladeFx.wantsPose(entityId);
	}

	/**
	 * World positions of the root (saya mouth) and the tip of the blade of this player in the last frame, from the pose the hand item render handed over; false when there
	 * is no fresh pose. {@code cam} and {@code camRot} are the camera of the frame (third person poses carry the inverse camera rotation).
	 */
	public static boolean worldBlade(int id, Vec3d cam, Quaternionf camRot, double[] rootOut, double[] tipOut) {
		Pose po = POSES.get(id);
		if (po == null || po.base == null || System.nanoTime() - po.nanos > POSE_FRESH_NANOS) {
			return false;
		}
		toWorld(po, po.base[0], po.base[1], po.base[2], cam, camRot, rootOut);
		toWorld(po, po.base[0] + po.dir[0] * po.len, po.base[1] + po.dir[1] * po.len, po.base[2] + po.dir[2] * po.len, cam, camRot, tipOut);
		return true;
	}

	/** True when the world part has a fresh pose of this caster's sword (the old guessed glow of {@link ReleaseHold} then stays off). */
	public static boolean hasPose(int entityId) {
		Run r = RUNS.get(entityId);
		Pose p = POSES.get(entityId);
		return r != null && p != null && System.nanoTime() - p.nanos < POSE_FRESH_NANOS && FxTune.d("release.glowFromPose", 1.0) > 0.5;
	}

	// ------------------------------------------------------------------------------------------ colours (legacy path and the model)

	private static float[] pink() {
		return FxMath.hex(FxTune.s("release.pinkColor", "#FF6EC0"));
	}

	/** Colour multiplier of the blade that is still there: the pink of the hold ({@code release.pinkColor}), ARGB. */
	public static int bladeTint() {
		return argb(pink(), 0.0, 1.0);
	}

	/** Colour multiplier of the hilt: pink at the start, back to white (its own colour) at the end. */
	public static int hiltTint(double p) {
		return argb(pink(), DissolveMath.hiltMix(p), 1.0);
	}

	/** Colour of the glow shell over the remaining blade: pink with the alpha of the dissolve curve times {@code release.dissolveGlow}. */
	public static int shellArgb(double p) {
		double a = DissolveMath.shellAlpha(p) * FxTune.d("release.dissolveGlow", 0.45);
		int alpha = (int) (255 * Math.max(0, Math.min(1, a)));
		return (alpha << 24) | (argb(pink(), 0.0, 1.0) & 0x00FFFFFF);
	}

	/** ARGB of the glow colour {@code release.glowColor} lerped towards white by {@code whiteMix}, with the given alpha (0..1). */
	public static int glowArgb(double whiteMix, double alpha) {
		float[] c = FxMath.hex(FxTune.s("release.glowColor", "#FF7FC4"));
		int a = (int) (255 * Math.max(0, Math.min(1, alpha)));
		return (a << 24) | (argb(c, whiteMix, 1.0) & 0x00FFFFFF);
	}

	/** ARGB of the pink of the hold lerped towards white, alpha 255: the colour multiplier of the glowing blade itself. */
	public static int bladeGlowTint(double whiteMix) {
		return argb(pink(), whiteMix, 1.0);
	}

	/** ARGB with alpha 255: {@code colour} lerped towards white by {@code whiteMix}, then scaled by {@code gain}. */
	private static int argb(float[] colour, double whiteMix, double gain) {
		int r = channel(colour[0], whiteMix, gain);
		int g = channel(colour[1], whiteMix, gain);
		int b = channel(colour[2], whiteMix, gain);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static int channel(float c, double whiteMix, double gain) {
		double v = (c + (1.0 - c) * whiteMix) * gain;
		return (int) (255 * Math.max(0, Math.min(1, v)));
	}

	// ------------------------------------------------------------------------------------------ comfort settings

	private static boolean calm() {
		return FxConfig.photosensitiveSafe || FxConfig.reduceMotion;
	}

	/** Scale of the break flash: no strobe, weaker with the screen effect scale; a quarter of it in photosensitive mode. */
	private static double flashScale() {
		double k = FxTune.d("release.glowFlash", 0.8) * Math.max(0, Math.min(1.5, FxConfig.screenFxScale));
		return FxConfig.photosensitiveSafe ? k * 0.25 : FxConfig.reduceMotion ? k * 0.6 : k;
	}

	/** Pulse of the glow while it builds up: about 2.2 per second, 15 percent; none in the calm modes. */
	private static double pulse(double t) {
		double amp = calm() ? 0 : 0.15 * FxTune.d("release.glowPulse", 1.0);
		return 1.0 + amp * Math.sin(Math.PI * 2 * 2.2 * t);
	}

	private static double qualityScale() {
		double q = FxConfig.effectQuality;
		return q >= 1.0 ? 1.0 : q >= 0.5 ? 0.6 : 0.3;
	}

	// ------------------------------------------------------------------------------------------ what the item model asks

	/** What the item model needs to draw the blade of one caster in this frame (one shared instance: the render thread only). */
	public static final class Look {
		public int id;
		/** Seconds since the dissolve started (negative during the hold). */
		public double el;
		public double dur;
		/** Progress through the dissolve, -1 before. */
		public double p;
		public double hold;
		/** Glow level 0..1.5 with the pulse. */
		public double level;
		public boolean tipFirst;
		public long seed;
		public double shardLife;
		public double spiral;
		public double radius;
		public double lift;
		public double life;
		public double bloom;
		public boolean shards;
	}

	private static final Look LOOK = new Look();

	/** The look of the blade of this caster now, or null when there is no release going on (or its glow is over). */
	public static Look look(int id) {
		Run r = RUNS.get(id);
		if (r == null) {
			return null;
		}
		double now = FxClock.now;
		double el = now - r.start;
		if (el >= r.dur + FxTune.d("release.shardLife", 0.28)) {
			return null; // the fragments of the last bit of blade are still in flight for a moment after the front reached the hilt
		}
		Look l = LOOK;
		l.id = id;
		l.el = el;
		l.dur = r.dur;
		l.p = el < 0 ? -1 : Math.min(1.0, el / r.dur);
		double holdLen = r.start - r.holdStart;
		l.hold = holdLen <= 1e-6 ? 1.0 : Math.max(0, Math.min(1, (now - r.holdStart) / holdLen));
		l.level = DissolveMath.glowLevel(l.hold, l.p) * pulse(now);
		l.tipFirst = tipFirst();
		l.seed = r.seed;
		fillFlight(l);
		l.shardLife = FxTune.d("release.shardLife", 0.28);
		l.bloom = FxTune.d("release.dissolveGlow", 0.45) * FxTune.d("release.glowBloom", 1.0);
		l.shards = FxTune.d("release.shards", 1.0) > 0.5;
		return l;
	}

	/** Fills the flight numbers of a look from the keys ({@code release.spiral}, {@code spiralRadius}, {@code petalLift}, {@code petalLife}). */
	public static void fillFlight(Look l) {
		double m = FxConfig.reduceMotion ? 0.6 : 1.0;
		l.spiral = FxTune.d("release.spiral", 1.0) * m;
		l.radius = FxTune.d("release.spiralRadius", 0.7);
		l.lift = FxTune.d("release.petalLift", 1.6) * (FxConfig.reduceMotion ? 0.7 : 1.0);
		l.life = FxTune.d("release.petalLife", 0.9);
	}

	// ------------------------------------------------------------------------------------------ pose handover

	/**
	 * Called by the item render of the hand (after every pose change was applied to the matrix stack) with the model to view matrix of
	 * the item (display transform and centre shift included) and the blade data of the baked model. First person: the hand matrix
	 * already carries the camera rotation (GameRenderer#renderHand pre-multiplies it), so it maps to world offsets directly; third
	 * person: the stack carries the inverse camera rotation, which is undone when the pose is used.
	 */
	public static void capturePose(int id, boolean fp, Matrix4f toView, float[] base, float[] dir, float len, float[] shardCtr, float[] shardS) {
		Pose p = POSES.computeIfAbsent(id, k -> new Pose());
		p.m.set(toView);
		p.fp = fp;
		p.nanos = System.nanoTime();
		p.base = base;
		p.dir = dir;
		p.len = len;
		p.shardCtr = shardCtr;
		p.shardS = shardS;
	}

	/** Factor of the first person hand pose for the world fov ({@code fx.handFovFix} 0 turns it off, {@code fx.handFov} sets the fov, 0 = the option). */
	private static double handFovScale() {
		if (FxTune.d("fx.handFovFix", 1.0) < 0.5) {
			return 1.0;
		}
		double fov = FxTune.d("fx.handFov", 0.0);
		if (fov > 1.0) {
			return dev.minebleach.reiatsutest.core.fx.HandFov.scale(fov);
		}
		if (worldTanHalfFov > 0) {
			return dev.minebleach.reiatsutest.core.fx.HandFov.scaleFromTan(worldTanHalfFov); // the fov of the frame, sprint included
		}
		return dev.minebleach.reiatsutest.core.fx.HandFov.scale(MinecraftClient.getInstance().options.getFov().getValue());
	}

	/** tan(fov / 2) of the world pass of the current frame (from its projection matrix), 0 before the first frame. */
	private static double worldTanHalfFov;

	private static void toWorld(Pose po, double x, double y, double z, Vec3d cam, Quaternionf camRot, double[] out) {
		po.m.transformPosition((float) x, (float) y, (float) z, TMP);
		if (!po.fp) {
			camRot.transform(TMP);
		} else {
			// the hand pass uses a fixed fov of 70, the world the fov of the options: scale the offset across the view so that the world effect sits on the drawn sword (HandFov)
			double k = handFovScale();
			if (Math.abs(k - 1.0) > 1.0e-3) {
				Vector3f r = camRot.transform(new Vector3f(1, 0, 0));
				Vector3f u = camRot.transform(new Vector3f(0, 1, 0));
				Vector3f f = camRot.transform(new Vector3f(0, 0, -1));
				float dx = TMP.dot(r);
				float dy = TMP.dot(u);
				float dz = TMP.dot(f);
				TMP.set(r.mul((float) (dx * k)).add(u.mul((float) (dy * k))).add(f.mul(dz)));
			}
		}
		out[0] = cam.x + TMP.x;
		out[1] = cam.y + TMP.y;
		out[2] = cam.z + TMP.z;
	}

	/** World position of the middle of the blade of this caster in the last frame (where the petals come from), for the swarm start; false when unknown. */
	public static boolean releasePoint(int id, double[] out) {
		Run r = RUNS.get(id);
		if (r == null || !r.haveWorld) {
			return false;
		}
		out[0] = r.wb[0] + r.wd[0] * r.wl * 0.5;
		out[1] = r.wb[1] + r.wd[1] * r.wl * 0.5;
		out[2] = r.wb[2] + r.wd[2] * r.wl * 0.5;
		return true;
	}

	// ------------------------------------------------------------------------------------------ per frame update and petals

	private static RenderLayer layer() {
		if (layer == null) {
			layer = RenderLayer.getEntityCutoutNoCull(TEXTURE);
		}
		return layer;
	}

	private static int targetPetals() {
		int forced = (int) FxTune.d("release.petalCount", 0);
		if (forced > 0) {
			return Math.max(20, Math.min(400, forced));
		}
		return DissolveSwirl.petalCount(FxConfig.effectQuality, FxConfig.reduceMotion ? 0.6 : 1.0, FxConfig.maxFxParticles / 4);
	}

	private static void initPetals(Run r, Pose po) {
		r.petalsInit = true;
		int target = targetPetals();
		int ns = po.shardS != null ? po.shardS.length : 0;
		Look l = new Look();
		fillFlight(l);
		boolean tf = tipFirst();
		int n = ns > 0 ? Math.min(target, ns) : target;
		r.n = n;
		r.shard = new int[n];
		r.s = new double[n];
		r.det = new double[n];
		r.prm = new double[n][DissolveSwirl.COUNT];
		r.released = new boolean[n];
		r.ox = new double[n];
		r.oy = new double[n];
		r.oz = new double[n];
		r.slot = new int[n];
		r.drawn = new boolean[n];
		r.px = new double[n];
		r.py = new double[n];
		r.pz = new double[n];
		r.vx = new float[n];
		r.vy = new float[n];
		r.vz = new float[n];
		r.heat = new float[n];
		r.env = new float[n];
		r.fade = new float[n];
		for (int j = 0; j < n; j++) {
			int idx;
			double s;
			if (ns > 0) {
				idx = (int) ((j + 0.5) * ns / n);
				s = po.shardS[idx];
			} else {
				idx = j;
				s = (j * 0.6180339887498949) % 1.0;
			}
			r.shard[j] = idx;
			r.s[j] = s;
			r.det[j] = r.dur * DissolveMath.detachProgress(s, tf);
			DissolveSwirl.params(r.seed, idx, l.spiral, l.radius, l.lift, l.life, r.prm[j]);
			r.slot[j] = (int) (FxHash.unit(r.seed, idx, 20) * 1_000_000);
		}
	}

	private static void draw(WorldRenderContext ctx) {
		float m11 = ctx.projectionMatrix() == null ? 0f : ctx.projectionMatrix().m11();
		if (m11 > 0.05f) {
			worldTanHalfFov = 1.0 / m11;
		}
		if (RUNS.isEmpty()) {
			lastPetals = 0;
			return;
		}
		long t0 = System.nanoTime();
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld world = ctx.world();
		if (world == null) {
			return;
		}
		Camera camera = ctx.camera();
		Vec3d cp = camera.getPos();
		Quaternionf camRot = camera.getRotation();
		float td = ctx.tickCounter().getTickDelta(true);
		double now = FxClock.now;
		double dt = FxClock.dt;
		if (!meshTried) {
			meshTried = true;
			mesh = FxMesh.load("byakuya_shikai", "byakuya_shikai_petal");
		}
		float uc = mesh != null ? mesh.centreU : 0.313f;
		float vc = mesh != null ? mesh.centreV : 0.055f;
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
		int petalsDrawn = 0;
		double fpClear = FxTune.d("release.fpMinDist", 0.45); // nearer than this to the lens a petal is not drawn; up to swarm.fpFade it is small and faint
		double fpFade = FxTune.d("swarm.fpFade", 5.0);
		double fpSize = FxTune.d("swarm.fpSize", 0.35);
		double fpGlowMin = FxTune.d("swarm.fpGlowMin", 0.15);
		double petalSize = FxTune.d("release.petalSize", 1.0);
		boolean fpWorld = FxTune.d("release.dissolveFpWorld", 1.0) > 0.5;
		boolean photo = FxConfig.photosensitiveSafe;
		double maxLife = FxTune.d("release.petalLife", 0.9) * 1.3;
		List<Integer> done = null;
		double[] a = new double[3];
		double[] b = new double[3];
		double[] o = new double[3];
		double[] orb = new double[3];
		double[] e1 = new double[3];
		double[] e2 = new double[3];
		double[] w = new double[3];
		for (Run r : RUNS.values()) {
			double el = now - r.start;
			if (el > r.dur + maxLife + 0.4) {
				if (done == null) {
					done = new ArrayList<>();
				}
				done.add(r.id);
				continue;
			}
			Entity owner = world.getEntityById(r.id);
			Pose po = POSES.get(r.id);
			r.fresh = po != null && System.nanoTime() - po.nanos < POSE_FRESH_NANOS && po.base != null;
			r.fpOwner = owner != null && owner == mc.player && !FxClient.forceRemote() && mc.options.getPerspective().isFirstPerson();
			if (r.fresh) {
				toWorld(po, po.base[0], po.base[1], po.base[2], cp, camRot, a);
				toWorld(po, po.base[0] + po.dir[0] * po.len, po.base[1] + po.dir[1] * po.len, po.base[2] + po.dir[2] * po.len, cp, camRot, b);
				double dx = b[0] - a[0];
				double dy = b[1] - a[1];
				double dz = b[2] - a[2];
				double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (l > 1e-4) {
					r.wb[0] = a[0];
					r.wb[1] = a[1];
					r.wb[2] = a[2];
					r.wd[0] = dx / l;
					r.wd[1] = dy / l;
					r.wd[2] = dz / l;
					r.wl = l;
					r.haveWorld = true;
				}
				if (!r.petalsInit) {
					initPetals(r, po);
				}
			}
			double holdLen = r.start - r.holdStart;
			r.hold = holdLen <= 1e-6 ? 1.0 : Math.max(0, Math.min(1, (now - r.holdStart) / holdLen));
			r.p = el < 0 ? -1 : Math.min(1.0, el / r.dur);
			r.level = DissolveMath.glowLevel(r.hold, r.p) * pulse(now);
			if (!r.haveWorld || (r.fpOwner && !fpWorld)) {
				continue;
			}
			if (r.petalsInit) {
				// the frame of the flight: up is mostly the world's up, a little of the blade direction
				double ux = 0.4 * r.wd[0];
				double uy = 0.4 * r.wd[1] + 0.6;
				double uz = 0.4 * r.wd[2];
				double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
				ux /= ul;
				uy /= ul;
				uz /= ul;
				frame(ux, uy, uz, e1, e2);
				double lf = Math.min(r.wl, FLIGHT_LENGTH_CAP);
				Vec3d feet = owner != null ? owner.getLerpedPos(td) : new Vec3d(r.wb[0], r.wb[1] - 1.1, r.wb[2]);
				double dOwner = cp.distanceTo(feet);
				int stride = dOwner <= 32 ? 1 : dOwner <= 64 ? 2 : 4;
				SwarmFx.State sw = SwarmFx.of(r.id);
				boolean blend = sw != null && sw.endAt < 0 && (sw.mode.kind == SwarmMath.Kind.IDLE || sw.mode.kind == SwarmMath.Kind.RELEASE);
				int packed = LightmapTextureManager.pack(Math.max(12, world.getLightLevel(LightType.BLOCK, BlockPos.ofFloored(feet.x, feet.y + 1.2, feet.z))),
						world.getLightLevel(LightType.SKY, BlockPos.ofFloored(feet.x, feet.y + 1.2, feet.z)));
				for (int j = 0; j < r.n; j++) {
					r.drawn[j] = false;
					if (!r.released[j] && el >= r.det[j] && r.fresh) {
						// the fragment comes loose here: its start is its place on the sword now
						if (po.shardCtr != null) {
							int k = r.shard[j] * 3;
							toWorld(po, po.shardCtr[k], po.shardCtr[k + 1], po.shardCtr[k + 2], cp, camRot, w);
						} else {
							double sl = r.s[j] * r.wl;
							w[0] = r.wb[0] + r.wd[0] * sl;
							w[1] = r.wb[1] + r.wd[1] * sl;
							w[2] = r.wb[2] + r.wd[2] * sl;
						}
						r.ox[j] = w[0];
						r.oy[j] = w[1];
						r.oz[j] = w[2];
						r.released[j] = true;
					}
					if (!r.released[j] || j % stride != 0) {
						continue;
					}
					double tau = el - r.det[j];
					double[] prm = r.prm[j];
					double life = prm[DissolveSwirl.LIFE];
					if (tau < 0 || tau > life) {
						continue;
					}
					DissolveSwirl.offset(prm, tau, o);
					double nx = r.ox[j] + (e1[0] * o[0] + e2[0] * o[2] + ux * o[1]) * lf;
					double ny = r.oy[j] + (e1[1] * o[0] + e2[1] * o[2] + uy * o[1]) * lf;
					double nz = r.oz[j] + (e1[2] * o[0] + e2[2] * o[2] + uz * o[1]) * lf;
					double wBlend = blend ? FxMath.smoothstep(0.45, 1.0, tau / life) : 0;
					if (wBlend > 0) {
						SwarmMath.idle(sw.slots, r.slot[j] % sw.n, sw.time(), !r.fpOwner && FxTune.d("swarm.ribbon", 1.0) > 0.5, sw.vx, sw.vy, sw.vz, orb);
						nx += (feet.x + orb[0] - nx) * wBlend;
						ny += (feet.y + orb[1] - ny) * wBlend;
						nz += (feet.z + orb[2] - nz) * wBlend;
					}
					// velocity from the same function a little later
					double tau2 = tau + 0.02;
					DissolveSwirl.offset(prm, tau2, o);
					double gx = r.ox[j] + (e1[0] * o[0] + e2[0] * o[2] + ux * o[1]) * lf;
					double gy = r.oy[j] + (e1[1] * o[0] + e2[1] * o[2] + uy * o[1]) * lf;
					double gz = r.oz[j] + (e1[2] * o[0] + e2[2] * o[2] + uz * o[1]) * lf;
					double w2 = blend ? FxMath.smoothstep(0.45, 1.0, tau2 / life) : 0;
					if (w2 > 0) {
						SwarmMath.idle(sw.slots, r.slot[j] % sw.n, sw.time() + 0.02, !r.fpOwner && FxTune.d("swarm.ribbon", 1.0) > 0.5, sw.vx, sw.vy, sw.vz, orb);
						gx += (feet.x + orb[0] - gx) * w2;
						gy += (feet.y + orb[1] - gy) * w2;
						gz += (feet.z + orb[2] - gz) * w2;
					}
					float vx = (float) ((gx - nx) / 0.02);
					float vy = (float) ((gy - ny) / 0.02);
					float vz = (float) ((gz - nz) / 0.02);
					double alpha = DissolveSwirl.alphaEnvelope(prm, tau, 0.7);
					double size = DissolveSwirl.sizeEnvelope(prm, tau, 0.1) * prm[DissolveSwirl.SIZE] * petalSize * (0.3 + 0.7 * alpha);
					float rx = (float) (nx - cp.x);
					float ry = (float) (ny - cp.y);
					float rz = (float) (nz - cp.z);
					double dl = Math.sqrt((double) rx * rx + (double) ry * ry + (double) rz * rz);
					double fadeG = 1.0;
					if (r.fpOwner) {
						if (dl < fpClear) {
							continue;
						}
						size *= fpSize + (1.0 - fpSize) * FxMath.smoothstep(fpClear, fpFade, dl);
						fadeG = fpGlowMin + (1.0 - fpGlowMin) * FxMath.smoothstep(fpClear, fpFade * 1.5, dl);
					}
					if (size <= 0.02) {
						continue;
					}
					// card: long axis along the flight, turned about it
					double sp = Math.sqrt((double) vx * vx + (double) vy * vy + (double) vz * vz);
					double lx = 0;
					double ly = 1;
					double lz = 0;
					if (sp > 0.05) {
						lx = vx / sp;
						ly = vy / sp;
						lz = vz / sp;
					}
					double rfx = Math.abs(ly) > 0.9 ? 1 : 0;
					double rfy = Math.abs(ly) > 0.9 ? 0 : 1;
					double wx0 = -lz * rfy;
					double wy0 = lz * rfx;
					double wz0 = lx * rfy - ly * rfx;
					double wl0 = Math.sqrt(wx0 * wx0 + wy0 * wy0 + wz0 * wz0);
					if (wl0 < 1e-6) {
						continue;
					}
					wx0 /= wl0;
					wy0 /= wl0;
					wz0 /= wl0;
					double ang = prm[DissolveSwirl.PHASE] + prm[DissolveSwirl.SPIN] * tau;
					double ca = Math.cos(ang);
					double sa = Math.sin(ang);
					// V' = V cos + (U x V) sin
					double cxx = ly * wz0 - lz * wy0;
					double cxy = lz * wx0 - lx * wz0;
					double cxz = lx * wy0 - ly * wx0;
					float vxx = (float) (wx0 * ca + cxx * sa);
					float vxy = (float) (wy0 * ca + cxy * sa);
					float vxz = (float) (wz0 * ca + cxz * sa);
					float nnx = (float) (ly * vxz - lz * vxy);
					float nny = (float) (lz * vxx - lx * vxz);
					float nnz = (float) (lx * vxy - ly * vxx);
					double dd = Math.max(dl, 1e-3);
					double facing = Math.abs(nnx * (-rx / dd) + nny * (-ry / dd) + nnz * (-rz / dd));
					double glint = Math.pow(facing, 16);
					float base = 0.88f + 0.12f * (float) facing;
					int cr = (int) (255 * Math.min(1.0, base * 1.12 + glint * 0.3));
					int cg = (int) (255 * Math.min(1.0, base * 1.04 + glint * 0.5));
					int cb = (int) (255 * Math.min(1.0, base * 1.1 + glint * 0.35));
					int color = 0xFF000000 | (cr << 16) | (cg << 8) | cb;
					double len = 0.22 * size;
					float ux2 = (float) (lx * len);
					float uy2 = (float) (ly * len);
					float uz2 = (float) (lz * len);
					float wxv = (float) (vxx * len * 0.40);
					float wyv = (float) (vxy * len * 0.40);
					float wzv = (float) (vxz * len * 0.40);
					float bx = rx - ux2 * 0.5f;
					float by = ry - uy2 * 0.5f;
					float bz = rz - uz2 * 0.5f;
					float tx = rx + ux2 * 0.5f;
					float ty = ry + uy2 * 0.5f;
					float tz = rz + uz2 * 0.5f;
					float mx = rx - ux2 * 0.15f;
					float my = ry - uy2 * 0.15f;
					float mz = rz - uz2 * 0.15f;
					float cn = 0.85f;
					card(buf, bx, by, bz, shade(color, 0.78f), mx + wxv, my + wyv, mz + wzv, shade(color, 0.92f), tx, ty, tz, color, mx - wxv, my - wyv, mz - wzv, shade(color, 0.92f),
							nnx * (1 - cn), nny * (1 - cn) + cn, nnz * (1 - cn), uc, vc, packed);
					petalsDrawn++;
					r.drawn[j] = true;
					r.px[j] = nx;
					r.py[j] = ny;
					r.pz[j] = nz;
					r.vx[j] = vx;
					r.vy[j] = vy;
					r.vz[j] = vz;
					r.heat[j] = (float) DissolveSwirl.heat(tau, 0.2);
					r.env[j] = (float) alpha;
					r.fade[j] = (float) fadeG;
				}
			}
			spawnParticles(r, dt, photo);
		}
		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			layer().draw(built);
		}
		if (done != null) {
			for (int id : done) {
				RUNS.remove(id);
				POSES.remove(id);
			}
		}
		lastPetals = petalsDrawn;
		lastMs = (System.nanoTime() - t0) / 1.0e6;
	}

	/** Two unit vectors across the unit vector (ux, uy, uz). */
	private static void frame(double ux, double uy, double uz, double[] e1, double[] e2) {
		double rx = Math.abs(uy) > 0.95 ? 1 : 0;
		double ry = Math.abs(uy) > 0.95 ? 0 : 1;
		double x = -uz * ry;
		double y = uz * rx;
		double z = ux * ry - uy * rx;
		double l = Math.sqrt(x * x + y * y + z * z);
		x /= l;
		y /= l;
		z /= l;
		e1[0] = x;
		e1[1] = y;
		e1[2] = z;
		e2[0] = uy * z - uz * y;
		e2[1] = uz * x - ux * z;
		e2[2] = ux * y - uy * x;
	}

	private static int shade(int argb, float k) {
		int r = (int) (((argb >> 16) & 255) * k);
		int g = (int) (((argb >> 8) & 255) * k);
		int b = (int) ((argb & 255) * k);
		return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
	}

	private static void card(BufferBuilder b, float ax, float ay, float az, int ca, float bx, float by, float bz, int cb, float cx, float cy, float cz, int cc, float dx, float dy, float dz,
			int cd, float nx, float ny, float nz, float u, float v, int light) {
		int o = OverlayTexture.DEFAULT_UV;
		b.vertex(ax, ay, az, ca, u, v, o, light, nx, ny, nz);
		b.vertex(bx, by, bz, cb, u, v, o, light, nx, ny, nz);
		b.vertex(cx, cy, cz, cc, u, v, o, light, nx, ny, nz);
		b.vertex(dx, dy, dz, cd, u, v, o, light, nx, ny, nz);
	}

	// ------------------------------------------------------------------------------------------ sparks and haze along the front

	private static void spawnParticles(Run r, double dt, boolean photo) {
		if (!r.haveWorld || !r.fresh || r.p < 0 || r.p >= 1.0 || dt <= 0) {
			return;
		}
		int budget = (int) (FxConfig.maxGlowSprites * 0.7);
		if (FxGlowBatch.live() > budget) {
			return;
		}
		double qs = qualityScale() * (FxConfig.reduceMotion ? 0.5 : 1.0);
		double fpK = r.fpOwner ? 0.6 : 1.0;
		r.sparkDebt += 150 * FxTune.d("release.sparks", 1.0) * qs * dt * fpK;
		r.smokeDebt += 26 * FxTune.d("release.smoke", 1.0) * qs * dt * fpK;
		boolean tf = tipFirst();
		double front = DissolveMath.boundary(r.p, tf);
		int ns = (int) Math.min(40, r.sparkDebt);
		r.sparkDebt -= ns;
		int nm = (int) Math.min(10, r.smokeDebt);
		r.smokeDebt -= nm;
		long seed = FxHash.mix(r.seed + FxClock.frame * 31L);
		double lw = r.wl;
		double fpSize = r.fpOwner ? 0.65 : 1.0;
		for (int k = 0; k < ns; k++) {
			// 70 percent on the front, the rest anywhere on the part of the blade that is left
			double s;
			if (FxHash.unit(seed, k, 1) < 0.7) {
				s = front;
			} else {
				s = tf ? FxHash.unit(seed, k, 2) * Math.max(0, front) : front + FxHash.unit(seed, k, 2) * Math.max(0, 1 - front);
			}
			s = Math.max(0, Math.min(1, s));
			double x = r.wb[0] + r.wd[0] * s * lw + (FxHash.unit(seed, k, 3) - 0.5) * 0.10 * lw;
			double y = r.wb[1] + r.wd[1] * s * lw + (FxHash.unit(seed, k, 4) - 0.5) * 0.10 * lw;
			double z = r.wb[2] + r.wd[2] * s * lw + (FxHash.unit(seed, k, 5) - 0.5) * 0.10 * lw;
			double ang = FxHash.unit(seed, k, 6) * Math.PI * 2;
			double side = 0.6 + 1.6 * FxHash.unit(seed, k, 7);
			double up = 0.4 + 1.8 * FxHash.unit(seed, k, 8);
			boolean star = FxHash.unit(seed, k, 9) < 0.18;
			double size = (star ? 0.16 + 0.10 * FxHash.unit(seed, k, 10) : 0.06 + 0.06 * FxHash.unit(seed, k, 10)) * fpSize;
			boolean hot = FxHash.unit(seed, k, 11) < 0.5;
			FxGlowBatch.Spec spec = FxGlowBatch.sprite(star ? GlowSprite.STAR4 : GlowSprite.SPECK).at(x, y, z).vel(-Math.sin(ang) * side, up, Math.cos(ang) * side).drag(0.9)
					.gravity(0.5).life(0.32 + 0.35 * FxHash.unit(seed, k, 12)).size(size, size * 0.4).color(hot ? "#FFE6F5" : "#FF9BD3")
					.peak((r.fpOwner ? 0.6 : 0.9) * FxConfig.glowIntensity).curve(0.03, 0.7);
			if (!photo) {
				spec.twinkle();
			}
			spec.spawn();
			totalSparks++;
		}
		for (int k = 0; k < nm; k++) {
			double s = Math.max(0, Math.min(1, front + (FxHash.unit(seed, k + 100, 1) - 0.5) * 0.12));
			double ang = FxHash.unit(seed, k + 100, 2) * Math.PI * 2;
			FxGlowBatch.sprite(GlowSprite.MIST).at(r.wb[0] + r.wd[0] * s * lw, r.wb[1] + r.wd[1] * s * lw, r.wb[2] + r.wd[2] * s * lw)
					.vel(Math.cos(ang) * 0.35, 0.5, Math.sin(ang) * 0.35).drag(0.92).life(0.55 + 0.25 * FxHash.unit(seed, k + 100, 3))
					.size(0.22 * fpSize, 0.75 * fpSize).sizeEase(FxMath.OC).color("#FF8FCB").peak((r.fpOwner ? 0.08 : 0.15) * FxConfig.glowIntensity).curve(0.08, 0.7)
					.rot(ang, 0.4).spawn();
		}
		lastSparks = ns;
	}

	// ------------------------------------------------------------------------------------------ glow source (after the grade)

	private static void emitGlow(FxGlowBatch.Emitter e) {
		if (RUNS.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		Vec3d cp = mc.gameRenderer.getCamera().getPos();
		double now = FxClock.now;
		float[] core = FxMath.hex(FxTune.s("release.glowCore", "#FFE9F6"));
		float[] col = FxMath.hex(FxTune.s("release.glowColor", "#FF7FC4"));
		double halo = FxTune.d("release.glowHalo", 0.55);
		double hsize = FxTune.d("release.glowSize", 0.6);
		double fpScale = FxTune.d("release.glowFpScale", 0.45);
		double trail = FxTune.d("release.trail", 1.0);
		double haloPetal = FxTune.d("swarm.haloIntensity", 0.36);
		boolean calm = calm();
		boolean tf = tipFirst();
		for (Run r : RUNS.values()) {
			if (!r.haveWorld) {
				continue;
			}
			double el = now - r.start;
			double lw = r.wl;
			boolean fp = r.fpOwner;
			double fpK = fp ? fpScale : 1.0;
			if (fp && FxTune.d("release.dissolveFpWorld", 1.0) < 0.5) {
				continue;
			}
			// R12: in the owner's first person the glow along the blade, at the hilt and the flashes on the front are placed in the world from the captured hand pose; the hand pass
			// moves with the view bobbing (running) and the hurt tilt, so they slid off the blade. The glow of the blade in the hand is made of shells of the item model
			// (ObjItemBakedModel#emitBloomLayers), which always sit on it: release.fpPoseGlow 1 brings the old world glow back.
			if (fp && FxTune.d("release.fpPoseGlow", 0.0) < 0.5) {
				continue;
			}
			// ---- blade halo, beam, hilt
			boolean bladeAlive = el < r.dur;
			double k = halo * r.level * fpK;
			double hiltK = halo * DissolveMath.hiltAfterglow(el - r.dur, 0.55) * Math.max(r.level, el >= r.dur ? 0.9 : 0) * fpK;
			if (r.fresh || el < r.dur + 0.6) {
				double front = r.p < 0 ? (tf ? 1.0 : 0.0) : DissolveMath.boundary(r.p, tf);
				double sLo = tf ? 0.0 : Math.max(0, front);
				double sHi = tf ? Math.min(1.0, front) : 1.0;
				if (bladeAlive && k > 0.004 && sHi > sLo + 0.02) {
					int pts = 7;
					for (int i = 0; i < pts; i++) {
						double s = sLo + (sHi - sLo) * (i + 0.5) / pts;
						double x = r.wb[0] + r.wd[0] * s * lw;
						double y = r.wb[1] + r.wd[1] * s * lw;
						double z = r.wb[2] + r.wd[2] * s * lw;
						double dist = Math.sqrt((x - cp.x) * (x - cp.x) + (y - cp.y) * (y - cp.y) + (z - cp.z) * (z - cp.z));
						double big = hsize * lw * (0.55 + 0.35 * Math.min(1.0, r.level));
						double small = big * 0.42;
						if (fp) {
							big = Math.min(big, dist * 0.5);
							small = Math.min(small, dist * 0.22);
						}
						double tipBoost = tf ? 0.7 + 0.5 * s : 1.0;
						e.sprite(GlowSprite.GLOW_SOFT, x, y, z, big, 0, col[0], col[1], col[2], k * 0.42 * tipBoost);
						e.sprite(GlowSprite.GLOW_CORE, x, y, z, small, 0, core[0], core[1], core[2], k * 0.5 * tipBoost);
					}
					// a thin hot line along the blade
					e.streak(GlowSprite.LINE, r.wb[0] + r.wd[0] * sHi * lw, r.wb[1] + r.wd[1] * sHi * lw, r.wb[2] + r.wd[2] * sHi * lw,
							r.wb[0] + r.wd[0] * sLo * lw, r.wb[1] + r.wd[1] * sLo * lw, r.wb[2] + r.wd[2] * sLo * lw, fp ? 0.06 : 0.1, core[0], core[1], core[2], k * 0.8);
				}
				// hilt and guard: a soft light that outlives the blade a little
				if (hiltK > 0.004) {
					for (int i = 0; i < 2; i++) {
						double s = i == 0 ? -0.04 : -0.2;
						double x = r.wb[0] + r.wd[0] * s * lw;
						double y = r.wb[1] + r.wd[1] * s * lw;
						double z = r.wb[2] + r.wd[2] * s * lw;
						double dist = Math.sqrt((x - cp.x) * (x - cp.x) + (y - cp.y) * (y - cp.y) + (z - cp.z) * (z - cp.z));
						double size = (i == 0 ? 0.32 : 0.26) * lw;
						if (fp) {
							size = Math.min(size, dist * 0.4);
						}
						e.sprite(GlowSprite.GLOW_SOFT, x, y, z, size, 0, col[0], col[1], col[2], hiltK * (i == 0 ? 0.5 : 0.35));
					}
				}
				// ---- flash on the front, and the break flash
				if (r.p >= 0 && r.p < 1.0) {
					double fk = flashScale();
					double fx = r.wb[0] + r.wd[0] * front * lw;
					double fy = r.wb[1] + r.wd[1] * front * lw;
					double fz = r.wb[2] + r.wd[2] * front * lw;
					double dist = Math.sqrt((fx - cp.x) * (fx - cp.x) + (fy - cp.y) * (fy - cp.y) + (fz - cp.z) * (fz - cp.z));
					double fadeOut = 1 - FxMath.smoothstep(0.75, 1.0, r.p);
					double sizeF = 0.5 * lw;
					double sizeS = 0.9 * lw;
					if (fp) {
						sizeF = Math.min(sizeF, dist * 0.45);
						sizeS = Math.min(sizeS, dist * 0.7);
					}
					double hot = fk * fadeOut * (fp ? fpScale + 0.2 : 1.0);
					e.sprite(GlowSprite.GLOW_CORE, fx, fy, fz, sizeF, 0, 1f, 0.93f, 0.98f, 0.85 * hot);
					e.sprite(GlowSprite.STAR4, fx, fy, fz, sizeS, calm ? 0.0 : now * 1.5, core[0], core[1], core[2], 0.5 * hot);
					double bf = DissolveMath.breakFlash(r.p) * fk * (fp ? fpScale + 0.2 : 1.0);
					if (bf > 0.004) {
						double tx = r.wb[0] + r.wd[0] * lw * 0.9;
						double ty = r.wb[1] + r.wd[1] * lw * 0.9;
						double tz = r.wb[2] + r.wd[2] * lw * 0.9;
						double bsize = 1.4 * lw;
						if (fp) {
							bsize = Math.min(bsize, Math.sqrt((tx - cp.x) * (tx - cp.x) + (ty - cp.y) * (ty - cp.y) + (tz - cp.z) * (tz - cp.z)) * 0.8);
						}
						e.sprite(GlowSprite.GLOW_SOFT, tx, ty, tz, bsize, 0, col[0], col[1], col[2], 0.7 * bf);
						e.sprite(GlowSprite.FLARE, tx, ty, tz, bsize * 1.1, 0, core[0], core[1], core[2], 0.5 * bf);
					}
				}
			}
			// ---- petals: halo on one in three, a trail behind them
			if (!r.petalsInit) {
				continue;
			}
			int trailStride = FxConfig.effectQuality >= 1.0 ? 1 : FxConfig.effectQuality >= 0.5 ? 2 : 4;
			for (int j = 0; j < r.n; j++) {
				if (!r.drawn[j]) {
					continue;
				}
				double fade = r.fade[j];
				double env = r.env[j];
				double hot = r.heat[j];
				if (j % 3 == 0) {
					e.sprite(GlowSprite.PETAL_GLOW, r.px[j], r.py[j], r.pz[j], (0.45 + 0.35 * hot) * (0.5 + 0.5 * fade), j * 1.3, col[0], col[1], col[2],
							(haloPetal * 1.1 + 0.5 * hot) * env * fade);
				}
				if (trail > 0 && j % trailStride == 0) {
					double sp = Math.sqrt((double) r.vx[j] * r.vx[j] + (double) r.vy[j] * r.vy[j] + (double) r.vz[j] * r.vz[j]);
					if (sp > 0.3) {
						double len = Math.min(1.4, sp * 0.11);
						e.streak(GlowSprite.STREAK, r.px[j], r.py[j], r.pz[j], r.px[j] - r.vx[j] / sp * len, r.py[j] - r.vy[j] / sp * len, r.pz[j] - r.vz[j] / sp * len,
								0.12 * (0.6 + 0.4 * fade), 1f, 0.7f + 0.2f * (float) hot, 0.92f, (0.34 + 0.4 * hot) * trail * env * fade);
					}
				}
			}
		}
	}
}
