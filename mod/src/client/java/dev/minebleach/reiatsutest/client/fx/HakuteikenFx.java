package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.CondenseMath;
import dev.minebleach.reiatsutest.core.fx.FxHash;
import dev.minebleach.reiatsutest.core.fx.HakuteikenCurves;
import dev.minebleach.reiatsutest.core.fx.RowsPhase;
import dev.minebleach.reiatsutest.core.fx.StormMath;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Shukei: Hakuteiken (VFX_STORYBOARD 6.3, effect id 33): everything condenses into white. State, event and timeline here; the wings, halo and
 * blade meshes and the glows that follow the owner are drawn by {@link WingHaloRenderer}, the condensing petals by {@link StormRenderer}, the
 * dissolving rows by {@link BankaiBladeRenderer} (phase 33, S3). The event params are {@code [aimX, aimY, aimZ, size, lineLength]} (S4: the
 * white line ends exactly at the server burst); without the fifth value the line is {@code min(20, |aim - eye|)}.
 * <p>The held breath of 1.4 to 1.5 s is a stillness of the visual clock ({@link HakuteikenCurves#rippleTime}), not a timeline hold, so the
 * strike stays on the server tick (1.5 s); the 50 ms HITSTOP of the burst is a real one.
 */
public final class HakuteikenFx {
	/** The live pose of the owner for the renderers (feet, body yaw, look, hand point). */
	public static final class Pose {
		public double fx;
		public double fy;
		public double fz;
		public double yaw;
		public double sin;
		public double cos;
		public double lx;
		public double ly;
		public double lz;
		public double hx;
		public double hy;
		public double hz;
		public double eyeHeight = 1.62;

		/** Player frame (x left, y up, z forward) to world coordinates. */
		public void local(double x, double y, double z, double[] out) {
			out[0] = fx + x * cos - z * sin;
			out[1] = fy + y;
			out[2] = fz + x * sin + z * cos;
		}

		/** A direction in the player frame to the world (no translation). */
		public void localDir(double x, double y, double z, double[] out) {
			out[0] = x * cos - z * sin;
			out[1] = y;
			out[2] = x * sin + z * cos;
		}
	}

	/** One running Hakuteiken. */
	public static final class State {
		public final int ownerId;
		public final long seed;
		public final double epoch;
		public final int slot;
		public final HakuteikenCurves.Timing tm;
		public final CondenseMath.Cloud cloud;
		public final boolean stub;
		public final double[] bx;
		public final double[] by;
		public final double[] bz;
		/** Line end (the server burst point) and its length from the eye. */
		public final double[] end = new double[3];
		public final double lineLength;
		public final Vec3d feet0;
		public final double yaw0;
		public final float[] tx;
		public final float[] ty;
		public final float[] tz;
		public final float[] rate;
		public final float[] phase;
		ScreenFx.Layer grade;
		boolean ownGrade;
		ScreenFx.Layer vignette;
		RowsFx.State rows;
		final double[] gradeBase = {1, 1, 1};
		double nextShed;
		double nextDebris;
		boolean gradeDone;
		public int shed;
		/** Drawn condensing petals of the last frame (harness). */
		public int visible;

		State(int ownerId, long seed, int slot, HakuteikenCurves.Timing tm, CondenseMath.Cloud cloud, boolean stub, double[] bx, double[] by, double[] bz, double lineLength, Vec3d feet0, double yaw0) {
			this.ownerId = ownerId;
			this.seed = seed;
			this.epoch = FxClock.now;
			this.slot = slot;
			this.tm = tm;
			this.cloud = cloud;
			this.stub = stub;
			this.bx = bx;
			this.by = by;
			this.bz = bz;
			this.lineLength = lineLength;
			this.feet0 = feet0;
			this.yaw0 = yaw0;
			int m = cloud.m;
			tx = new float[m];
			ty = new float[m];
			tz = new float[m];
			rate = new float[m];
			phase = new float[m];
			for (int j = 0; j < m; j++) {
				double ux = FxHash.signed(seed, j, 71);
				double uy = FxHash.signed(seed, j, 72);
				double uz = FxHash.signed(seed, j, 73);
				double l = Math.sqrt(ux * ux + uy * uy + uz * uz);
				if (l < 1e-3) {
					ux = 0;
					uy = 1;
					uz = 0;
					l = 1;
				}
				tx[j] = (float) (ux / l);
				ty[j] = (float) (uy / l);
				tz[j] = (float) (uz / l);
				rate[j] = (float) (5 + 7 * FxHash.unit(seed, j, 74));
				phase[j] = (float) (FxHash.unit(seed, j, 75) * Math.PI * 2);
			}
		}

		public double time() {
			return FxClock.now - epoch - FxTimelines.held(slot);
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	public static int casts;
	public static int impactFrames;
	public static int bursts;

	private HakuteikenFx() {
	}

	public static Map<Integer, State> states() {
		return STATES;
	}

	public static State of(int ownerId) {
		return STATES.get(ownerId);
	}

	public static void clear() {
		for (State st : STATES.values()) {
			if (st.rows != null) {
				st.rows.gradeLocked = false;
			}
		}
		STATES.clear();
	}

	/** Timing with the tunable keys {@code hak.*}. */
	public static HakuteikenCurves.Timing timing() {
		HakuteikenCurves.Timing t = new HakuteikenCurves.Timing();
		t.wingStart = FxTune.d("hak.wingStart", t.wingStart);
		t.fanSpan = FxTune.d("hak.fanSpan", t.fanSpan);
		t.fanOvershoot = FxTune.d("hak.fanOvershoot", t.fanOvershoot);
		t.haloSpan = FxTune.d("hak.haloSpan", t.haloSpan);
		t.ignite = FxTune.d("hak.ignite", t.ignite);
		t.bladeStart = FxTune.d("hak.bladeStart", t.bladeStart);
		t.bladeSpan = FxTune.d("hak.bladeSpan", t.bladeSpan);
		t.bladeMin = FxTune.d("hak.bladeMin", t.bladeMin);
		t.bladeHeld = FxTune.d("hak.bladeHeld", t.bladeHeld);
		t.breathStart = FxTune.d("hak.breathStart", t.breathStart);
		t.breathSpan = FxTune.d("hak.breathSpan", t.breathSpan);
		t.strike = FxTune.d("hak.strike", t.strike);
		t.extend = FxTune.d("hak.extend", t.extend);
		t.burst = FxTune.d("hak.burst", t.burst);
		t.lineFade = FxTune.d("hak.lineFade", t.lineFade);
		t.lineFadeSpan = FxTune.d("hak.lineFadeSpan", t.lineFadeSpan);
		t.dissolveStart = FxTune.d("hak.dissolveStart", t.dissolveStart);
		t.dissolveEnd = FxTune.d("hak.dissolveEnd", t.dissolveEnd);
		t.life = FxTune.d("hak.life", t.life);
		t.foldLength = FxTune.d("hak.foldLength", t.foldLength);
		return t;
	}

	public static CondenseMath.Params condenseParams() {
		CondenseMath.Params p = new CondenseMath.Params();
		p.rows = RowsFx.timingFor(RowsPhase.CONSUMED);
		p.flightBase = FxTune.d("hak.flightBase", p.flightBase);
		p.flightJitter = FxTune.d("hak.flightJitter", p.flightJitter);
		p.turns = FxTune.d("hak.turns", p.turns);
		p.spiralRadius = FxTune.d("hak.spiralRadius", p.spiralRadius);
		p.whiteRadius = FxTune.d("hak.whiteRadius", p.whiteRadius);
		p.absorb = FxTune.d("hak.absorb", p.absorb);
		p.pull = FxTune.d("hak.pull", p.pull);
		return p;
	}

	// ------------------------------------------------------------------------------------------ pose

	/** The live pose of the owner at tick fraction {@code td}; falls back to the cast pose when the entity is not loaded. */
	public static void pose(State st, ClientWorld w, float td, Pose out) {
		Entity ow = w != null ? w.getEntityById(st.ownerId) : null;
		double yaw;
		double lx;
		double ly;
		double lz;
		if (ow != null) {
			Vec3d f = ow.getLerpedPos(td);
			out.fx = f.x;
			out.fy = f.y;
			out.fz = f.z;
			if (ow instanceof LivingEntity le) {
				yaw = Math.toRadians(MathHelper.lerpAngleDegrees(td, le.prevBodyYaw, le.bodyYaw));
			} else {
				yaw = Math.toRadians(ow.getYaw(td));
			}
			Vec3d lk = ow.getRotationVec(td);
			lx = lk.x;
			ly = lk.y;
			lz = lk.z;
			out.eyeHeight = ow.getEyeHeight(ow.getPose());
		} else {
			out.fx = st.feet0.x;
			out.fy = st.feet0.y;
			out.fz = st.feet0.z;
			yaw = st.yaw0;
			lx = -Math.sin(yaw);
			ly = 0;
			lz = Math.cos(yaw);
			out.eyeHeight = 1.62;
		}
		out.yaw = yaw;
		out.sin = Math.sin(yaw);
		out.cos = Math.cos(yaw);
		out.lx = lx;
		out.ly = ly;
		out.lz = lz;
		// hand = right shoulder + look x 0.6 (the right hand side is -x of the player frame)
		double sh = FxTune.d("hak.handSide", 0.30);
		double up = FxTune.d("hak.handHeight", 1.40);
		double fw = FxTune.d("hak.handForward", 0.6);
		out.hx = out.fx - sh * out.cos + lx * fw;
		out.hy = out.fy + up + ly * fw;
		out.hz = out.fz - sh * out.sin + lz * fw;
	}

	// ------------------------------------------------------------------------------------------ event (id 33)

	public static void onEvent(EffectEventS2C e) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		if (w == null) {
			return;
		}
		casts++;
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		float[] pr = e.params();
		Vec3d eye = pos.add(0, 1.62, 0);
		Vec3d aim = pr.length >= 3 ? new Vec3d(pr[0], pr[1], pr[2]) : eye.add(dir.multiply(20));
		double toAim = eye.distanceTo(aim);
		double len = pr.length >= 5 && pr[4] > 0 ? pr[4] : Math.min(20.0, toAim);
		RowsFx.State rs = RowsFx.of(e.casterId());
		boolean haveRows = rs != null && rs.endAt < 0;
		int blades;
		float[] dist;
		double[] bx;
		double[] by;
		double[] bz;
		if (haveRows) {
			blades = rs.layout.n;
			dist = rs.layout.dist;
			bx = rs.bx;
			bz = rs.bz;
			by = new double[blades];
			for (int i = 0; i < blades; i++) {
				by[i] = rs.ground[i] - 0.5;
			}
		} else {
			blades = 100;
			dist = new float[blades];
			bx = new double[blades];
			by = new double[blades];
			bz = new double[blades];
			for (int i = 0; i < blades; i++) {
				double a = 2 * Math.PI * i / blades;
				dist[i] = (float) (50.0 * FxHash.unit(e.seed(), i, 61));
				bx[i] = pos.x + 6 * Math.cos(a);
				bz[i] = pos.z + 6 * Math.sin(a);
				by[i] = FxGround.sample(bx[i], bz[i], pos.y) - 0.5;
			}
		}
		int per = Math.max(1, (int) (StormMath.condensePerBlade(blades) * Math.max(0.25, FxConfig.effectQuality)));
		CondenseMath.Params cp = condenseParams();
		long seed = e.seed() * 0x2545F4914F6CDD1DL + 33;
		CondenseMath.Cloud cloud = new CondenseMath.Cloud(cp, seed, blades, per, dist);
		cloud.heightScale = haveRows ? rs.bladeScale : 1.0;
		HakuteikenCurves.Timing tm = timing();
		EffectTimeline t = FxTimelines.create(33, e.seed(), pos, dir, e.params(), e.casterId(), FxClient.forceRemote());
		State st = new State(e.casterId(), seed, t.slot, tm, cloud, !haveRows, bx, by, bz, len, pos, Math.atan2(-dir.x, dir.z));
		Vec3d unit = toAim < 1e-3 ? (dir.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : dir.normalize()) : aim.subtract(eye).multiply(1.0 / toAim);
		Vec3d end = eye.add(unit.multiply(len));
		st.end[0] = end.x;
		st.end[1] = end.y;
		st.end[2] = end.z;
		STATES.put(e.casterId(), st);
		if (haveRows) {
			st.rows = rs;
			RowsFx.setPhase(rs, RowsPhase.CONSUMED, FxClock.now);
			rs.gradeLocked = true;
			if (rs.grade != null) {
				st.grade = rs.grade;
				st.gradeBase[0] = rs.grade.rgb[0];
				st.gradeBase[1] = rs.grade.rgb[1];
				st.gradeBase[2] = rs.grade.rgb[2];
			}
		}
		ReiatsuTest.LOGGER.info("[fx] hakuteiken: {} condensing petals ({} blades x {}), rows {}, line {} m to ({}, {}, {})", cloud.m, blades, per, haveRows ? "standing" : "stub ring", f(len),
				f(end.x), f(end.y), f(end.z));
		schedule(t, st, end);
		FxTimelines.start(t);
	}

	private static String f(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	private static void schedule(EffectTimeline t, State st, Vec3d end) {
		HakuteikenCurves.Timing tm = st.tm;
		t.duck(1.0, 1.5, 0.5);
		// 0.00 condense: dark world, petals spiral into the hand
		t.at(0.0, x -> {
			x.sound("block.beacon.activate", 0.7, 0.8);
			x.sound("entity.illusioner.cast_spell", 0.6, 0.7);
			x.sound("block.conduit.activate", 0.8, 0.6);
			st.vignette = x.vignette(ScreenFx.Kind.DARK, FxTune.d("hak.vignette", 0.60), 0.3);
			if (st.grade == null) {
				st.grade = x.grade("#FFFFFF", 1.0, 0.05);
				st.ownGrade = true;
			}
		});
		// 0.50 wings and halo bloom
		t.at(tm.wingStart, x -> {
			x.flash(FxTune.d("hak.bloomFlash", 0.40), "#FFFFFF", false);
			x.sound("entity.ender_dragon.flap", 0.9, 0.9);
			x.sound("block.bell.use", 1.2, 0.7);
			x.sound("block.end_portal_frame.fill", 0.6, 0.6);
		});
		// 1.00 blade forms
		t.at(tm.bladeStart + 0.05, x -> {
			x.sound("block.amethyst_block.resonate", 1.2, 0.7);
			x.sound("block.beacon.power_select", 1.5, 0.6);
		});
		// 1.50 strike (server line)
		t.at(tm.strike, x -> strike(x, st, end));
		// 1.80 burst (server)
		t.at(tm.burst, x -> burst(x, st, end));
		// 2.50 dissolve
		t.at(tm.dissolveStart, x -> {
			x.sound("entity.phantom.flap", 1.2, 0.3);
			if (st.vignette != null) {
				st.vignette.release(0.5);
			}
		});
		// 3.00 rows re-form
		t.at(st.cloud.p.rows.reformStart, x -> x.sound("block.amethyst_block.chime", 0.8, 0.4));
		t.lifetime(tm.life);
	}

	private static void strike(EffectTimeline x, State st, Vec3d end) {
		HakuteikenCurves.Timing tm = st.tm;
		MinecraftClient mc = MinecraftClient.getInstance();
		Pose pose = new Pose();
		pose(st, mc.world, 1.0f, pose);
		Vec3d hand = new Vec3d(pose.hx, pose.hy, pose.hz);
		Vec3d d = end.subtract(hand);
		double length = d.length();
		x.sound("entity.warden.sonic_boom", 1.2, 1.0);
		x.sound("item.mace.smash_air", 0.8, 0.8);
		x.sound("item.trident.thunder", 1.5, 0.4);
		if (x.screen) {
			ScreenFx.impactFrame(x.strength);
			ScreenFx.shake(FxTune.d("hak.strikeShake", 1.2), 0.5, x.strength);
		}
		if (length < 0.5) {
			return;
		}
		Vec3d u = d.multiply(1.0 / length);
		double ext = tm.extend;
		double fade0 = tm.lineFade - tm.strike;
		double fadeSpan = tm.lineFadeSpan;
		double coreW = FxTune.d("hak.coreWidth", 0.12);
		double glowW = FxTune.d("hak.glowWidth", 0.6);
		double glowI = FxTune.d("hak.glowIntensity", 0.5);
		// the white line: a core and a wider glow, two crossed quads each (Q line x2)
		FxShapes.Strip core = FxShapes.strip(hand.x, hand.y, hand.z, u.x, u.y, u.z, 0).length(a -> length * FxMath.oe(a / ext)).width(a -> coreW);
		core.color("#FFFFFF").life(fade0 + fadeSpan + 0.05).owner(x).intensity(a -> a < fade0 ? 1.0 : Math.max(0, 1.0 - FxMath.ios((a - fade0) / fadeSpan)));
		FxShapes.Strip glow = FxShapes.strip(hand.x, hand.y, hand.z, u.x, u.y, u.z, 0).length(a -> length * FxMath.oe(a / ext)).width(a -> glowW);
		glow.color("#F4F8FF").life(fade0 + fadeSpan + 0.05).owner(x).intensity(a -> glowI * (a < fade0 ? 1.0 : Math.max(0, 1.0 - FxMath.ios((a - fade0) / fadeSpan))));
		Random r = x.rng(1500);
		double q = FxConfig.effectQuality * x.lod;
		// shock rings every 2 blocks perpendicular to the line (r 0 to 1.2 in 0.2 s, staggered 0.01 s)
		int rings = (int) (length / 2.0);
		for (int i = 0; i < rings; i++) {
			double s = 2.0 * (i + 1);
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(hand.x + u.x * s, hand.y + u.y * s, hand.z + u.z * s).plane(u.x, u.y, u.z).size(0.3, FxTune.d("hak.ringSize", 2.4)).sizeEase(FxMath.OC)
					.life(0.2).delay(0.01 * i).curve(0.01, 0.7).color("#FFFFFF").peak(0.55).owner(x).spawn();
		}
		// sparks fly off perpendicular to the line (200 star4 / streak)
		int n = (int) (200 * q);
		Vec3d side0 = Math.abs(u.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(-u.z, 0, u.x).normalize();
		Vec3d side1 = u.crossProduct(side0);
		for (int i = 0; i < n; i++) {
			double s = r.nextDouble() * length;
			double a = r.nextDouble() * Math.PI * 2;
			double sp = 3 + r.nextDouble() * 9;
			double vx = (side0.x * Math.cos(a) + side1.x * Math.sin(a)) * sp;
			double vy = (side0.y * Math.cos(a) + side1.y * Math.sin(a)) * sp;
			double vz = (side0.z * Math.cos(a) + side1.z * Math.sin(a)) * sp;
			double delay = ext * s / length;
			FxGlowBatch.Spec sp0 = i % 2 == 0 ? FxGlowBatch.sprite(GlowSprite.STAR4) : FxGlowBatch.sprite(GlowSprite.STREAK).streak();
			sp0.at(hand.x + u.x * s, hand.y + u.y * s, hand.z + u.z * s).vel(vx, vy, vz).drag(0.9).delay(delay).lifeTicks(8 + r.nextInt(8)).size(i % 2 == 0 ? 0.3 + r.nextDouble() * 0.4 : 1.4)
					.color("#FFFFFF").peak(0.85).owner(x).spawn();
		}
	}

	private static void burst(EffectTimeline x, State st, Vec3d end) {
		bursts++;
		x.hitstop(FxTune.d("hak.hitstop", 50));
		Random r = x.rng(1800);
		double q = FxConfig.effectQuality * x.lod;
		double gy = FxGround.sample(end.x, end.z, end.y);
		x.soundAt("entity.generic.explode", end, 0.9, 1.0);
		x.soundAt("block.glass.break", end, 0.6, 0.6);
		x.soundAt("item.mace.smash_ground_heavy", end, 0.8, 0.8);
		double s = StormFx.impactStrength(x, end);
		if (x.screen) {
			ScreenFx.flashCustom(FxTune.d("hak.burstFlash", 0.80), FxMath.hex("#FFFFFF"), 0.04, 0.03, 0.4, s);
			ScreenFx.shake(FxTune.d("hak.burstShake", 0.8), 0.4, s);
		}
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(end.x, end.y, end.z).size(5.0, FxTune.d("hak.burstSize", 12)).sizeEase(FxMath.OC).life(0.5).curve(0.01, 0.85).color("#FFFFFF").peak(1.0)
				.owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.STAR4).at(end.x, end.y, end.z).size(4.0, 14.0).sizeEase(FxMath.OC).life(0.3).curve(0.01, 0.8).color("#FFFFFF").peak(0.9).rot(0.2, 0.6).owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(end.x, gy + 0.06, end.z).ground().size(0.5, FxTune.d("hak.ringR", 8.0) / 0.375 * 1.0).sizeEase(FxMath.OC).life(0.45).curve(0.01, 0.6)
				.color("#F4F8FF").peak(0.9).owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(end.x, gy + 0.07, end.z).ground().size(0.5, FxTune.d("hak.ringR", 8.0) / 0.4375).sizeEase(FxMath.OC).life(0.5).curve(0.01, 0.6)
				.color("#FFFFFF").peak(0.6).owner(x).spawn();
		int stars = (int) (200 * q);
		for (int i = 0; i < stars; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double c = r.nextDouble() * 2 - 1;
			double rr = Math.sqrt(1 - c * c);
			double sp = 4 + r.nextDouble() * 12;
			FxGlowBatch.sprite(GlowSprite.STAR4).at(end.x, end.y, end.z).vel(rr * Math.cos(a) * sp, c * sp, rr * Math.sin(a) * sp).drag(0.9).gravity(1.0).lifeTicks(8 + r.nextInt(12))
					.size(0.3 + r.nextDouble() * 0.5).color("#FFFFFF").peak(0.9).twinkle().rot(r.nextDouble() * 6.28, 0).owner(x).spawn();
		}
		int feathers = (int) (100 * q);
		for (int i = 0; i < feathers; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double c = r.nextDouble() * 1.6 - 0.4;
			double rr = Math.sqrt(Math.max(0, 1 - c * c));
			double sp = 3 + r.nextDouble() * 8;
			FxGlowBatch.sprite(r.nextBoolean() ? GlowSprite.FEATHER : GlowSprite.FEATHER_B).at(end.x, end.y, end.z).vel(rr * Math.cos(a) * sp, c * sp, rr * Math.sin(a) * sp).drag(0.92)
					.gravity(1.2).lifeTicks(30 + r.nextInt(20)).size(0.5 + r.nextDouble() * 0.4).color("#F4F8FF").peak(0.8).rot(r.nextDouble() * 6.28, (r.nextDouble() - 0.5) * 4).owner(x).spawn();
		}
		int dust = (int) (60 * FxConfig.effectQuality * x.lod);
		for (int i = 0; i < dust; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double sp = 2.5 + r.nextDouble() * 4.5;
			FxParticles.spec(FxParticles.Kind.DUST_PUFF).at(end.x, gy + 0.2, end.z).vel(Math.cos(a) * sp, 0.4 + r.nextDouble() * 1.4, Math.sin(a) * sp).drag(0.9).life(24)
					.size(1.0 + r.nextDouble() * 0.6, 2.6).color("#BFB6A8").alpha(0.5).seed(r.nextInt(4)).owner(x).spawn();
		}
	}

	// ------------------------------------------------------------------------------------------ per frame

	/** Once per frame at WorldRenderEvents.START: the grade colour, shed and falling feathers, expiry. */
	public static void frame(MinecraftClient mc) {
		if (STATES.isEmpty() || mc.world == null) {
			return;
		}
		Random r = new Random((long) (FxClock.now * 1000) + 11);
		float td = mc.getRenderTickCounter().getTickDelta(false);
		double[] c = new double[3];
		double[] w = new double[3];
		Iterator<State> it = STATES.values().iterator();
		while (it.hasNext()) {
			State st = it.next();
			double t = st.time();
			HakuteikenCurves.Timing tm = st.tm;
			if (t > tm.life + 0.3) {
				finish(st);
				it.remove();
				continue;
			}
			// the grade: night -> #3C4870 -> #2E3858 -> darker -> back to the night over 1.3 s
			if (st.grade != null && !st.gradeDone) {
				HakuteikenCurves.gradeColour(t, st.gradeBase, c);
				st.grade.rgb[0] = (float) c[0];
				st.grade.rgb[1] = (float) c[1];
				st.grade.rgb[2] = (float) c[2];
				if (t >= 3.5) {
					st.gradeDone = true;
					if (st.ownGrade) {
						st.grade.release(0.3);
					}
					if (st.rows != null) {
						st.rows.gradeLocked = false;
					}
				}
			}
			if (FxClock.dt <= 0 || mc.world == null) {
				continue;
			}
			Pose pose = null;
			// shed feathers from the wing edges over 1.5 s (80), dissolve: 100 feathers and 60 white petals falling
			boolean shed = t >= tm.wingStart + 0.1 && t < tm.wingStart + 1.6;
			boolean dissolving = t >= tm.dissolveStart && t < tm.dissolveEnd + 0.2;
			if ((shed && FxClock.now >= st.nextShed) || (dissolving && FxClock.now >= st.nextDebris)) {
				pose = new Pose();
				pose(st, mc.world, td, pose);
			}
			if (shed && FxClock.now >= st.nextShed && mc.gameRenderer.getCamera().getPos().distanceTo(new Vec3d(pose.fx, pose.fy, pose.fz)) < 64) {
				st.nextShed = FxClock.now + 1.5 / 80.0 / Math.max(0.25, FxConfig.effectQuality);
				featherPoint(st, r, pose, HakuteikenCurves.fan(tm, t), w);
				double sway = (r.nextDouble() - 0.5) * 0.8;
				FxGlowBatch.sprite(r.nextBoolean() ? GlowSprite.FEATHER : GlowSprite.FEATHER_B).at(w[0], w[1], w[2]).vel(sway, -0.6, -sway * 0.5).drag(0.97).lifeTicks(40 + r.nextInt(21))
						.size(FxTune.d("hak.featherSize", 0.6) * (0.7 + 0.6 * r.nextDouble())).color("#F4F8FF").peak(0.7).rot(r.nextDouble() * 6.28, (r.nextDouble() - 0.5) * 2).spawn();
				st.shed++;
			}
			if (dissolving && FxClock.now >= st.nextDebris && mc.gameRenderer.getCamera().getPos().distanceTo(new Vec3d(pose.fx, pose.fy, pose.fz)) < 64) {
				st.nextDebris = FxClock.now + 0.5 / 160.0;
				featherPoint(st, r, pose, 1.0, w);
				FxGlowBatch.sprite(r.nextBoolean() ? GlowSprite.FEATHER : GlowSprite.FEATHER_B).at(w[0], w[1], w[2]).vel((r.nextDouble() - 0.5) * 1.2, -0.5 - r.nextDouble() * 0.6,
						(r.nextDouble() - 0.5) * 1.2).drag(0.97).lifeTicks(34 + r.nextInt(20)).size(0.6 * (0.7 + 0.6 * r.nextDouble())).color("#FFFFFF").peak(0.75)
						.rot(r.nextDouble() * 6.28, (r.nextDouble() - 0.5) * 2).spawn();
				if (r.nextInt(3) == 0) {
					FxParticles.spec(FxParticles.Kind.PETAL).at(w[0], w[1], w[2]).vel((r.nextDouble() - 0.5) * 1.5, -0.3, (r.nextDouble() - 0.5) * 1.5).drag(0.96).gravity(0.4).life(30)
							.size(0.26).color("#F4F8FF").spin((r.nextDouble() - 0.5) * 0.4).seed(r.nextInt(4)).spawn();
				}
			}
		}
	}

	/** A random point on a feather of a wing (feather i of 9, at a random distance along it), in the world, for the current fan. */
	static void featherPoint(State st, Random r, Pose pose, double fan, double[] out) {
		int side = r.nextBoolean() ? 1 : -1;
		int i = r.nextInt(9);
		double len = HakuteikenCurves.featherLength(i) * (0.55 + 0.45 * r.nextDouble());
		double ang = Math.toRadians(HakuteikenCurves.featherAngleDeg(i));
		double[] loc = new double[3];
		HakuteikenCurves.fanVertex(st.tm, side, side * len * Math.cos(ang), len * Math.sin(ang), 0.8 * Math.pow(len / 6.0, 2), fan, loc);
		double sc = FxConfig.wingScale * FxTune.d("hak.wingScale", 1.0);
		double rootX = side * FxTune.d("hak.rootX", 0.12);
		pose.local(rootX + loc[0] * sc, FxTune.d("hak.rootY", 1.35) + loc[1] * sc, -FxTune.d("hak.rootBack", 0.15) + loc[2] * sc, out);
	}

	private static void finish(State st) {
		if (st.rows != null) {
			st.rows.gradeLocked = false;
		}
		if (st.ownGrade && st.grade != null) {
			st.grade.release(0.2);
		}
	}
}
