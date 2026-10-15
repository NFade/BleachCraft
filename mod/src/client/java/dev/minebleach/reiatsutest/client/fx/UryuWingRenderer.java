package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.HakuteikenFirstPerson;
import dev.minebleach.reiatsutest.core.fx.UryuVollstTiming;
import dev.minebleach.reiatsutest.core.fx.UryuWingMath;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The wings, bracers, boots and floating flakes of the bankai state of Uryu (URYU_VFX_STORYBOARD 2.10, 2.11, 4.1), drawn ON the player model
 * through the bankai pass of {@link UryuQuincyFeature} ({@link #drawPass}, registered by {@link UryuWingFx#init}). The numbers are in
 * {@link UryuVollstTiming} and {@link UryuWingMath} (unit tested); this class only writes vertices.
 *
 * <p><b>Frame.</b> The pass starts from the matrix stack of the feature (the player model frame, y down, scaled 0.9375). Like the Rukia ribbons it
 * undoes that to the BODY FRAME: feet at the origin, x LEFT, y UP, z FORWARD, blocks. The wings sit at the shoulder root (0, 1.35, 0.15 behind the
 * spine), one mesh per side ({@code uryu_wing_l/r}, {@link UryuWingMath#MESH_PLUS_X_IS_LEFT}), written vertex by vertex because the unfold of the
 * release deforms them per plate ({@link UryuWingMath#plate}: length and roll about the root, staggered). Two passes: the translucent emissive
 * layer with the diffuse atlas (alpha 200 plates, reads on a bright sky), and an additive overglow (6 percent larger about the root, Q3 at 0.3,
 * breathing 0.8 to 1.0) that also carries the arrival flash of each plate.
 *
 * <p><b>Bands.</b> The bracers sit on the forearm part, the boots on the leg part (their ModelPart transform is applied, so they follow the swing),
 * with a mask sweep from the hand / sole inward and scrolling bars ({@link UryuVollstTiming#scrolledU}). {@link #firstPersonBracer} draws the
 * bracer of the first person arm.
 *
 * <p><b>First person.</b> The feature does not render the camera player in first person, so the owner never has a wing in the view (the
 * storyboard guard "no wing in the camera" is that by construction; {@link UryuWingFx} applies the guard of {@link UryuWingMath#guardYaw} to
 * everything it spawns in the world).
 *
 * <p><b>Tips.</b> {@link #wingTips} is the one public method for the wing barrage (U-FX 8): the four tips in the slot order of
 * {@code UryuBalance.WING_TIPS}, where the visible wing is.
 *
 * <p>UNVERIFIED in game (design/results): the +X side of the meshes, the radial scale of the bands over the arm and the leg, the sneak fold, the
 * look of the translucent plates on a bright sky.
 */
public final class UryuWingRenderer {
	private static final Identifier TEX = ReiatsuTest.id("textures/item/uryu_fx_diffuse.png");
	private static final int LIGHT = 0xF000F0;

	/** The per player pose state (smoothed sneak and air, the beat phase, the volley and barrage flares). */
	public static final class WingState {
		double phase;
		double air;
		double sneak;
		double last = Double.NaN;
		double volleyAt = -1000;
		double spreadStart = -1;
		double spreadEnd = -1;
		UryuWingMath.Rig rig = UryuWingMath.rig(0, 0, 0, 1.0, 0, 0);
		double yawRad;
		double fx;
		double fy;
		double fz;
		/** The wings were visible at the last update (the end effect breaks them into flakes only then). */
		boolean wingsShown;
		double specAcc;
		double tipAcc;
		double spawnLast = Double.NaN;

		public UryuWingMath.Rig rig() {
			return rig;
		}
	}

	private static final Map<Integer, WingState> STATES = new HashMap<>();
	private static RenderLayer glowLayer;
	private static FxMesh wingLeft;
	private static FxMesh wingRight;
	private static FxMesh bracer;
	private static FxMesh boot;
	private static FxMesh flake;
	private static boolean tried;
	private static boolean warned;
	private static final Vector3f P = new Vector3f();
	private static final Vector3f N = new Vector3f();
	private static final double[] W = new double[3];

	/** Statistics for the harness. */
	public static int passes;
	public static int wingQuads;
	public static int bandQuads;
	public static int flakeQuads;
	public static int firstPersonBracers;
	/** V range (fraction of the 256 px atlas, V down) of the band and boot rects of uryu_fx_diffuse.png: y 136..160 and y 160..208. */
	private static final double BRACER_V0 = 136.0 / 256.0;
	private static final double BRACER_V1 = 160.0 / 256.0;
	private static final double BOOT_V0 = 160.0 / 256.0;
	private static final double BOOT_V1 = 208.0 / 256.0;
	/** Players drawn with everything this frame (the cap of {@link UryuVollstTiming#FULL_PLAYERS}). */
	private static int fullDrawn;

	private UryuWingRenderer() {
	}

	/** Once per frame (from {@link UryuWingFx#frame}). */
	static void beginFrame() {
		fullDrawn = 0;
	}

	static void clear() {
		STATES.clear();
	}

	static WingState stateOf(int id) {
		return STATES.get(id);
	}

	static void drop(int id) {
		STATES.remove(id);
	}

	/** Forgets the pose state of players that left the world (about once a second, from {@link UryuWingFx#frame}). */
	static void prune(net.minecraft.world.World world) {
		STATES.keySet().removeIf(id -> world.getEntityById(id) == null);
	}

	private static void load() {
		if (tried) {
			return;
		}
		tried = true;
		wingLeft = FxMesh.load("uryu_fx", UryuWingMath.meshName(true));
		wingRight = FxMesh.load("uryu_fx", UryuWingMath.meshName(false));
		bracer = FxMesh.load("uryu_fx", "uryu_bracer");
		boot = FxMesh.load("uryu_fx", "uryu_boot");
		flake = FxMesh.load("uryu_fx", "uryu_flechette");
	}

	/** Alpha respecting additive layer (source alpha times colour added, no depth write, no culling): the overglow and the flashes. */
	private static RenderLayer glow() {
		if (glowLayer == null) {
			glowLayer = RenderLayer.of("reiatsu_uryu_wing_glow", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 1536, false, true,
					RenderLayer.MultiPhaseParameters.builder()
							.program(RenderPhase.ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
							.texture(new RenderPhase.Texture(TEX, false, false))
							.transparency(RenderPhase.LIGHTNING_TRANSPARENCY)
							.cull(RenderPhase.DISABLE_CULLING)
							.writeMaskState(RenderPhase.COLOR_MASK)
							.overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
							.build(false));
		}
		return glowLayer;
	}

	// ------------------------------------------------------------------------------------------ the state of a player (per frame)

	/**
	 * Updates and returns the pose state of a player for this frame (the beat phase, the smoothed sneak and air, the position and yaw as drawn).
	 * Called once per frame per Uryu bankai player by {@link UryuWingFx#frame}; the same state serves the draw pass and the glow emission.
	 */
	static WingState update(AbstractClientPlayerEntity pl, double now, float td) {
		WingState st = STATES.computeIfAbsent(pl.getId(), k -> new WingState());
		boolean first = Double.isNaN(st.last);
		double dt = first ? 0.0 : Math.max(0.0, Math.min(0.1, now - st.last));
		st.last = now;
		double airTarget = pl.isOnGround() || pl.isClimbing() || pl.isTouchingWater() ? 0.0 : 1.0;
		double sneakTarget = pl.isInSneakingPose() ? 1.0 : 0.0;
		if (first) {
			st.air = airTarget;
			st.sneak = sneakTarget;
		} else {
			st.air = UryuWingMath.approach(st.air, airTarget, dt, 8.0);
			st.sneak = UryuWingMath.approach(st.sneak, sneakTarget, dt, 10.0);
		}
		st.phase += dt * UryuWingMath.flapHz(st.air);
		double spread = UryuWingMath.spread(now, st.spreadStart, st.spreadEnd);
		double volley = UryuWingMath.volley(now - st.volleyAt);
		st.rig = UryuWingMath.rig(st.sneak, st.air, st.phase, FxConfig.uryuWingScale, spread, volley);
		double yaw = pl.prevBodyYaw + (pl.bodyYaw - pl.prevBodyYaw) * td;
		st.yawRad = Math.toRadians(yaw);
		Vec3d p = pl.getLerpedPos(td);
		st.fx = p.x;
		st.fy = p.y;
		st.fz = p.z;
		return st;
	}

	/** Harness: {sneak, air, flap phase, roll degrees, wings shown 0 or 1} of a player, or null when none. */
	public static double[] debugPose(int entityId) {
		WingState st = STATES.get(entityId);
		return st == null ? null : new double[] {st.sneak, st.air, st.phase, st.rig.rollDeg(), st.wingsShown ? 1.0 : 0.0};
	}

	/** Harness: the clock of the wings of a player (seconds since the release event, 1000 settled, negative = waiting for the event). */
	public static double wingClock(int entityId) {
		return UryuWingFx.releaseTime(entityId);
	}

	/** A volley of the barrage (U-FX 8): the wings flare x1.07 and the overglow x1.6 for 0.18 s. */
	public static void volley(int casterId) {
		WingState st = STATES.computeIfAbsent(casterId, k -> new WingState());
		st.volleyAt = FxClock.now;
	}

	/** The barrage opens the wings (+12 degrees, x1.1) from now until {@code seconds} later, then they relax over 0.4 s. */
	public static void spread(int casterId, double seconds) {
		WingState st = STATES.computeIfAbsent(casterId, k -> new WingState());
		st.spreadStart = FxClock.now;
		st.spreadEnd = FxClock.now + seconds;
	}

	/** Body frame (x left, y up, z forward, feet origin) to the world, for the state of a player. */
	static void toWorld(WingState st, double bx, double by, double bz, double[] out) {
		double c = Math.cos(st.yawRad);
		double s = Math.sin(st.yawRad);
		// left = (cos, sin), forward = (-sin, cos) (Minecraft yaw: 0 faces +Z)
		out[0] = st.fx + c * bx - s * bz;
		out[1] = st.fy + by;
		out[2] = st.fz + s * bx + c * bz;
	}

	/**
	 * The four visible wing tips in the world, in the slot order of {@code UryuBalance.WING_TIPS} (0 upper right, 1 upper left, 2 lower right, 3 lower
	 * left), at the pose the wings have at the given partial tick, or null when the player has no bankai wings. The wing barrage (U-FX 8) fires its
	 * arrows from these points, so they leave the tips that are seen (flap, sneak, air and the barrage opening included).
	 */
	public static Vec3d[] wingTips(int entityId, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || !(mc.world.getEntityById(entityId) instanceof AbstractClientPlayerEntity pl) || !UryuQuincyFeature.state(entityId).bankai()) {
			return null;
		}
		WingState st = STATES.get(entityId);
		if (st == null) {
			st = update(pl, FxClock.now, tickDelta);
		}
		double yaw = Math.toRadians(pl.prevBodyYaw + (pl.bodyYaw - pl.prevBodyYaw) * tickDelta);
		Vec3d feet = pl.getLerpedPos(tickDelta);
		double c = Math.cos(yaw);
		double s = Math.sin(yaw);
		Vec3d[] out = new Vec3d[4];
		double[] o = new double[3];
		for (int slot = 0; slot < 4; slot++) {
			UryuWingMath.tip(slot, st.rig, o);
			// o = (right positive, height, backwards): the same conversion as WingBarrage.tip
			out[slot] = new Vec3d(feet.x - c * o[0] + s * o[2], feet.y + o[1], feet.z - s * o[0] - c * o[2]);
		}
		return out;
	}

	/**
	 * A random point on a wing (plate surface) in the world, at the plate state {@code t} seconds after the release event ({@code 1000} = unfolded),
	 * for the stardust and the end effect. {@code groups}: the highest plate group to pick from (inclusive).
	 */
	static boolean surfacePoint(WingState st, Random r, double t, int maxGroup, double[] out, double[] outPlate) {
		load();
		int side = r.nextBoolean() ? 1 : -1;
		FxMesh m = side == 1 ? wingLeft : wingRight;
		if (m == null) {
			return false;
		}
		int q = r.nextInt(m.quads);
		int g = UryuWingMath.groupOfQuad(q);
		if (g > maxGroup) {
			q = r.nextInt(42);
			g = UryuWingMath.groupOfQuad(q);
		}
		double[] pl = new double[4];
		UryuWingMath.plate(t, g, pl);
		if (pl[2] <= 0.02) {
			return false;
		}
		// bilinear point in the quad (a triangle repeats its third corner)
		double u = r.nextDouble();
		double v = r.nextDouble();
		double x = 0;
		double y = 0;
		double z = 0;
		for (int c = 0; c < 4; c++) {
			double wgt = (c == 0 || c == 3 ? 1 - u : u) * (c < 2 ? 1 - v : v);
			x += wgt * m.pos[(q * 4 + c) * 3];
			y += wgt * m.pos[(q * 4 + c) * 3 + 1];
			z += wgt * m.pos[(q * 4 + c) * 3 + 2];
		}
		UryuWingMath.place(side, x, y, z, pl[0], pl[1], st.rig, W);
		toWorld(st, W[0], W[1], W[2], out);
		if (outPlate != null) {
			outPlate[0] = side;
			outPlate[1] = g;
			outPlate[2] = W[0];
			outPlate[3] = W[2];
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------ the bankai pass

	private static int argb(double r, double g, double b, double a) {
		return FxMeshes.argb(r, g, b, a);
	}

	/** The bankai pass registered with {@link UryuQuincyFeature#addBankaiPass}: wings, bands, flakes of one player, in the frame of the player model. */
	public static void drawPass(UryuQuincyFeature.Ctx c) {
		try {
			draw(c);
		} catch (RuntimeException ex) {
			if (!warned) {
				warned = true;
				ReiatsuTest.LOGGER.error("[fx] uryu wing renderer failed (further errors are not logged)", ex);
			}
		}
	}

	private static void draw(UryuQuincyFeature.Ctx c) {
		load();
		AbstractClientPlayerEntity pl = c.player();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.gameRenderer.getCamera().getPos().squaredDistanceTo(pl.getPos()) > UryuVollstTiming.VIEW_DISTANCE * UryuVollstTiming.VIEW_DISTANCE) {
			return;
		}
		UryuQuincyFeature.State s = c.state();
		double t = UryuWingFx.releaseTime(pl.getId(), s);
		boolean ending = !s.bankai();
		if (t < 0 && !ending) {
			return;
		}
		WingState st = STATES.get(pl.getId());
		if (st == null) {
			st = update(pl, c.now(), c.tickDelta());
		}
		passes++;
		boolean full = fullDrawn++ < UryuVollstTiming.FULL_PLAYERS;
		MatrixStack m = c.matrices();
		m.push();
		// back to the body frame (feet origin, +z forward, +x left, blocks): the inverse of the renderer's scale(-1,-1,1), scale(0.9375) and translate(0,-1.501,0), the
		// turn of 180 degrees about y that the renderer applies to the body (same as the Rukia ribbons)
		m.translate(0f, 1.501f, 0f);
		float inv = (float) (1.0 / UryuWingMath.MODEL_SCALE);
		m.scale(inv, inv, inv);
		m.scale(-1f, -1f, 1f);
		m.multiply(new Quaternionf().rotateY((float) Math.PI));
		Matrix4f base = new Matrix4f(m.peek().getPositionMatrix());
		if (pl.isInSneakingPose()) {
			// the player renderer lowers a sneaking player by 0.125 (getPositionOffset): the rig below is measured from the real feet
			base.translate(0f, 0.125f, 0f);
		}
		fpOn = false;
		var cam = mc.gameRenderer.getCamera();
		if (FxConfig.uryuFpGuard && cam.isReady() && !cam.isThirdPerson() && cam.getFocusedEntity() == pl) {
			// first person guard (as the Hakuteiken wings): the rig yaw is limited to uryu.fpYawMax of the view, pushed back uryu.fpBack, faded by view depth
			boolean gentle = FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
			double bodyYaw = Math.toRadians(pl.prevBodyYaw + (pl.bodyYaw - pl.prevBodyYaw) * c.tickDelta());
			double camYaw = Math.toRadians(cam.getYaw());
			double lim = HakuteikenFirstPerson.limitYaw(bodyYaw, camYaw, HakuteikenFirstPerson.yawLimit(FxTune.d("uryu.fpYawMax", HakuteikenFirstPerson.YAW_MAX_DEG), gentle));
			base.rotateLocalY((float) (bodyYaw - lim));
			base.translate(0f, 0f, (float) -Math.max(0.0, FxTune.d("uryu.fpBack", HakuteikenFirstPerson.BACK)));
			HakuteikenFirstPerson.forward(camYaw, Math.toRadians(cam.getPitch()), FP_FWD);
			fpClear = FxTune.d("uryu.fpClear", HakuteikenFirstPerson.CLEAR);
			fpFull = FxTune.d("uryu.fpFull", HakuteikenFirstPerson.FULL) * (gentle ? 1.3 : 1.0);
			fpOn = true;
		}
		Matrix3f nbase = base.normal(new Matrix3f());
		m.pop();
		double bandFade = ending ? 1.0 - Math.max(0.0, s.endedAge()) / UryuVollstTiming.BANDS_FADE_OUT : 1.0;
		double bandT = ending ? 1000.0 : t;
		boolean bands = full && FxConfig.uryuBands && bandFade > 0.01;
		wingQuads = 0;
		bandQuads = 0;
		flakeQuads = 0;
		// ONE layer at a time: asking the provider for another layer ends the buffer of the previous one (the immediate draws it), so a consumer
		// is written completely before the next layer is requested. 0 = translucent emissive plates, 1 = the additive overglow.
		for (int layer = 0; layer < 2; layer++) {
			VertexConsumer vc = c.vcp().getBuffer(layer == 0 ? RenderLayer.getEntityTranslucentEmissive(TEX) : glow());
			if (!ending) {
				wingsPass(vc, layer, base, nbase, st, t, c.now());
				if (full) {
					flakesPass(vc, layer, base, t, c.now());
				}
			} else {
				// the end: the wings fold away (the unfold backwards, 0.25 s) while UryuWingFx breaks them into flakes
				double f = Math.max(0.0, Math.min(1.0, s.endedAge() / UryuVollstTiming.BANDS_FADE_OUT));
				wingsPass(vc, layer, base, nbase, st, UryuVollstTiming.T_UNFOLD + UryuVollstTiming.UNFOLD_TIME * (1.0 - f), c.now());
			}
			if (bands) {
				bandsPass(c, vc, layer, bandFade, bandT, c.now());
			}
		}
	}

	// ------------------------------------------------------------------------------------------ wings

	private static void wingsPass(VertexConsumer vc, int layer, Matrix4f base, Matrix3f nbase, WingState st, double t, double now) {
		if (wingLeft == null || wingRight == null || t < UryuVollstTiming.T_UNFOLD) {
			st.wingsShown = false;
			return;
		}
		st.wingsShown = true;
		double[][] plates = new double[UryuWingMath.GROUPS][4];
		for (int g = 0; g < UryuWingMath.GROUPS; g++) {
			UryuWingMath.plate(t, g, plates[g]);
		}
		double emissive = Math.max(0.0, FxConfig.emissiveMultiplier);
		boolean overglow = FxConfig.uryuWingOverglow;
		double breath = UryuVollstTiming.breath(now);
		double volley = UryuWingMath.volley(now - st.volleyAt);
		float[] q3 = UryuPalette.FQ3;
		float[] q1 = UryuPalette.FQ1;
		double scale = layer == 0 ? 1.0 : UryuWingMath.OVERGLOW_SCALE;
		for (int side = 1; side >= -1; side -= 2) {
			FxMesh mesh = side == 1 ? wingLeft : wingRight;
			for (int q = 0; q < mesh.quads; q++) {
				int g = UryuWingMath.groupOfQuad(q);
				double[] pl = plates[g];
				if (pl[2] < 0.004) {
					continue;
				}
				int color;
				if (layer == 0) {
					color = argb(1.0, 1.0, 1.0, pl[2]);
				} else {
					double flash = pl[3];
					double a = pl[2] * (overglow ? UryuWingMath.OVERGLOW_ALPHA * breath * (1.0 + (UryuWingMath.OVERGLOW_VOLLEY - 1.0) * volley) * emissive : 0.0);
					a = Math.max(a, flash * 0.85 * pl[2]);
					if (a < 0.004) {
						continue;
					}
					float r = (float) (q3[0] + (q1[0] - q3[0]) * flash);
					float gg = (float) (q3[1] + (q1[1] - q3[1]) * flash);
					float b = (float) (q3[2] + (q1[2] - q3[2]) * flash);
					color = argb(r, gg, b, Math.min(1.0, a));
				}
				for (int cn = 0; cn < 4; cn++) {
					int i = q * 4 + cn;
					UryuWingMath.place(side, mesh.pos[i * 3], mesh.pos[i * 3 + 1], mesh.pos[i * 3 + 2], pl[0] * scale, pl[1], st.rig, W);
					put(vc, base, nbase, W[0], W[1], W[2], color, mesh.uv[i * 2], mesh.uv[i * 2 + 1], mesh.nrm[i * 3], mesh.nrm[i * 3 + 1], mesh.nrm[i * 3 + 2]);
				}
				wingQuads++;
			}
		}
	}

	private static boolean fpOn;
	private static double fpClear = 0.8;
	private static double fpFull = 3.0;
	private static final double[] FP_FWD = new double[3];

	private static void put(VertexConsumer vc, Matrix4f m, Matrix3f nm, double x, double y, double z, int color, float u, float v, float nx, float ny, float nz) {
		m.transformPosition((float) x, (float) y, (float) z, P);
		nm.transform(nx, ny, nz, N);
		if (fpOn) {
			double k = HakuteikenFirstPerson.fade(HakuteikenFirstPerson.depth(P.x, P.y, P.z, 0, 0, 0, FP_FWD), fpClear, fpFull);
			color = (color & 0xFFFFFF) | ((int) ((color >>> 24) * k) << 24);
		}
		vc.vertex(P.x, P.y, P.z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(LIGHT).normal(N.x, N.y, N.z);
	}

	// ------------------------------------------------------------------------------------------ flakes

	private static void flakesPass(VertexConsumer vc, int layer, Matrix4f base, double t, double now) {
		if (flake == null) {
			return;
		}
		int count = UryuVollstTiming.flakeCount(FxConfig.fxTier == FxConfig.TierSetting.LOW, FxConfig.fxTier == FxConfig.TierSetting.MEDIUM);
		double env = UryuVollstTiming.flakeEnvelope(t);
		if (count == 0 || env < 0.01 || layer == 1 && !FxConfig.uryuWingOverglow) {
			return;
		}
		boolean calm = FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
		double[] f = new double[7];
		Matrix4f im = new Matrix4f();
		Matrix3f inm = new Matrix3f();
		double grow = layer == 0 ? 1.0 : 1.1;
		for (int i = 0; i < count; i++) {
			UryuVollstTiming.flake(i, count, now, calm, f);
			double a = f[4] * env;
			if (a < 0.01) {
				continue;
			}
			float sc = (float) (f[3] / UryuVollstTiming.FLAKE_MESH_LENGTH);
			im.set(base).translate((float) f[0], (float) f[1], (float) f[2]).rotateY((float) f[5]).rotateZ((float) f[6]).scale(sc);
			im.normal(inm);
			int color = layer == 0 ? argb(1.0, 1.0, 1.0, 0.95 * a) : argb(UryuPalette.FQ2[0], UryuPalette.FQ2[1], UryuPalette.FQ2[2], 0.35 * a);
			for (int q = 0; q < flake.quads; q++) {
				for (int cn = 0; cn < 4; cn++) {
					int k = q * 4 + cn;
					put(vc, im, inm, flake.pos[k * 3] * grow, flake.pos[k * 3 + 1] * grow, flake.pos[k * 3 + 2] * grow, color, flake.uv[k * 2], flake.uv[k * 2 + 1], flake.nrm[k * 3],
							flake.nrm[k * 3 + 1], flake.nrm[k * 3 + 2]);
				}
			}
			flakeQuads += flake.quads;
		}
	}

	// ------------------------------------------------------------------------------------------ bracers and boots

	private static boolean armored(AbstractClientPlayerEntity pl, EquipmentSlot slot) {
		ItemStack s = pl.getEquippedStack(slot);
		return !s.isEmpty() && s.getItem() instanceof ArmorItem a && a.getSlotType() == slot;
	}

	private static void bandsPass(UryuQuincyFeature.Ctx c, VertexConsumer vc, int layer, double fade, double t, double now) {
		if (bracer == null || boot == null) {
			return;
		}
		double p = UryuVollstTiming.bandProgress(t);
		if (p <= 0.0 && fade >= 1.0) {
			return;
		}
		AbstractClientPlayerEntity pl = c.player();
		PlayerEntityModel<AbstractClientPlayerEntity> model = c.model();
		boolean slim = c.slim();
		double armRad = armored(pl, EquipmentSlot.CHEST) ? UryuWingMath.BRACER_RADIAL_ARMORED : UryuWingMath.BRACER_RADIAL;
		double bootRad = armored(pl, EquipmentSlot.LEGS) || armored(pl, EquipmentSlot.FEET) ? UryuWingMath.BOOT_RADIAL_ARMORED : UryuWingMath.BOOT_RADIAL;
		double inv = 1.0 / UryuWingMath.MODEL_SCALE;
		MatrixStack m = c.matrices();
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			ModelPart arm = left ? model.leftArm : model.rightArm;
			m.push();
			arm.rotate(m);
			Matrix4f am = new Matrix4f(m.peek().getPositionMatrix());
			am.translate((float) (UryuWingMath.armCentrePx(slim, left) / 16.0), (float) (UryuWingMath.BRACER_Y_PX / 16.0), 0f);
			am.scale((float) (armRad * inv), (float) inv, (float) (armRad * inv)).rotateX((float) Math.PI);
			band(vc, layer, bracer, am, p, fade, now, -0.1, 0.2, BRACER_V0, BRACER_V1);
			m.pop();
			ModelPart leg = left ? model.leftLeg : model.rightLeg;
			m.push();
			leg.rotate(m);
			Matrix4f lm = new Matrix4f(m.peek().getPositionMatrix());
			lm.translate(0f, (float) (UryuWingMath.BOOT_Y_PX / 16.0), 0f);
			lm.scale((float) (bootRad * inv), (float) inv, (float) (bootRad * inv)).rotateX((float) Math.PI);
			band(vc, layer, boot, lm, p, fade, now, 0.02, 0.43, BOOT_V0, BOOT_V1);
			m.pop();
		}
	}

	/**
	 * First person: the bracer of the arm that {@code FirstPersonHand} just drew (the stack holds that arm's frame, in blocks: the first person
	 * model is not scaled by 0.9375). Does nothing unless the player is in the BANKAI state and the bands are on.
	 */
	public static void firstPersonBracer(AbstractClientPlayerEntity pl, PlayerEntityModel<AbstractClientPlayerEntity> model, boolean slim, boolean left, MatrixStack m,
			VertexConsumerProvider vcp) {
		load();
		UryuQuincyFeature.State s = UryuQuincyFeature.state(pl.getId());
		if (!s.bankai() || !FxConfig.uryuBands || bracer == null) {
			return;
		}
		double t = UryuWingFx.releaseTime(pl.getId(), s);
		double p = UryuVollstTiming.bandProgress(t);
		if (p <= 0.0) {
			return;
		}
		double rad = armored(pl, EquipmentSlot.CHEST) ? UryuWingMath.BRACER_RADIAL_ARMORED : UryuWingMath.BRACER_RADIAL;
		ModelPart arm = left ? model.leftArm : model.rightArm;
		m.push();
		arm.rotate(m);
		Matrix4f am = new Matrix4f(m.peek().getPositionMatrix());
		am.translate((float) (UryuWingMath.armCentrePx(slim, left) / 16.0), (float) (UryuWingMath.BRACER_Y_PX / 16.0), 0f);
		am.scale((float) rad, 1f, (float) rad).rotateX((float) Math.PI);
		for (int layer = 0; layer < 2; layer++) { // one layer at a time (see draw)
			band(vcp.getBuffer(layer == 0 ? RenderLayer.getEntityTranslucentEmissive(TEX) : glow()), layer, bracer, am, p, 1.0, FxClock.now, -0.1, 0.2, BRACER_V0, BRACER_V1);
		}
		m.pop();
		firstPersonBracers++;
	}

	/**
	 * One band mesh through {@code m} into one layer (0 solid, 1 the additive rim): the mask sweep (alpha by the height along the band, {@code y0} and
	 * {@code span} in mesh metres, the hand / sole end first) and the scrolled bars.
	 */
	private static void band(VertexConsumer vc, int layer, FxMesh mesh, Matrix4f m, double progress, double fade, double now, double y0, double span, double vLo, double vHi) {
		Matrix3f nm = m.normal(new Matrix3f());
		double shift = UryuVollstTiming.bandScroll(now);
		// the band rect of the atlas (x 0..96 of 256): the U of the ring quads is read relative to it
		double u0 = 0.0;
		double uw = 96.0 / 256.0;
		double glowK = Math.max(0.0, FxConfig.emissiveMultiplier) * 0.5;
		float[] q1 = UryuPalette.FQ1;
		for (int q = 0; q < mesh.quads; q++) {
			double minU = Double.MAX_VALUE;
			for (int cn = 0; cn < 4; cn++) {
				minU = Math.min(minU, mesh.uv[(q * 4 + cn) * 2]);
			}
			double minRel = (minU - u0) / uw;
			// only the quads of the ring itself scroll: their V lies inside the band rect (the toe and strap quads of the boot sample the small colour cells)
			boolean ring = true;
			for (int cn = 0; cn < 4; cn++) {
				float v = mesh.uv[(q * 4 + cn) * 2 + 1];
				ring &= v >= vLo - 1e-4 && v <= vHi + 1e-4;
			}
			for (int cn = 0; cn < 4; cn++) {
				int i = q * 4 + cn;
				double y = mesh.pos[i * 3 + 1];
				double h01 = Math.max(0.0, Math.min(1.0, (y - y0) / span));
				double a = UryuVollstTiming.bandAlpha(progress, h01) * fade;
				float u = mesh.uv[i * 2];
				if (ring) {
					double rel = (u - u0) / uw;
					u = (float) (u0 + UryuVollstTiming.scrolledU(rel, minRel, shift) * uw);
				}
				int color = layer == 0 ? argb(1.0, 1.0, 1.0, a) : argb(q1[0], q1[1], q1[2], a * 0.35 * glowK);
				put(vc, m, nm, mesh.pos[i * 3], mesh.pos[i * 3 + 1], mesh.pos[i * 3 + 2], color, u, mesh.uv[i * 2 + 1], mesh.nrm[i * 3], mesh.nrm[i * 3 + 1], mesh.nrm[i * 3 + 2]);
			}
			bandQuads++;
		}
	}
}
