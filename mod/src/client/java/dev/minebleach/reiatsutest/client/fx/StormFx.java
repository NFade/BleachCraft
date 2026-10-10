package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.FxHash;
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
import net.minecraft.util.math.Vec3d;

/**
 * Petal storm / scatter of the Byakuya bankai (VFX_STORYBOARD 6.2, effect id 32): state, events, sounds, screen effects, impact burst.
 * The rows burn away from the tip down ({@link RowsFx} phase 32, S3), each blade gives its petals at the cut, the petals flow into a
 * helix around the owner, two thirds leave in a stream to the aim point, contract, burst at the server impact (2.5 s) and return to
 * their blades, which regrow bottom-up. All petal positions are closed form ({@link StormMath}) and drawn by {@link StormRenderer}.
 * <p>The event params are {@code [aimX, aimY, aimZ, impactRadius, tornadoRadius]}; the drawn helix keeps the storyboard radius 5 (the
 * server tornado radius is wider, the loose debris reaches 7), tunable with {@code storm.radiusScale}.
 */
public final class StormFx {
	/** One running storm. */
	public static final class State {
		public final int ownerId;
		public final long seed;
		public final double epoch;
		/** Timeline slot (HITSTOP of the impact freezes the storm clock like the sprites). */
		public final int slot;
		public final StormMath.Storm storm;
		public final double[] aim = new double[3];
		public final double[] look = new double[3];
		public final Vec3d feet0;
		public final boolean stub;
		public final int blades;
		public final double[] bx;
		public final double[] by;
		public final double[] bz;
		public final float[] tx;
		public final float[] ty;
		public final float[] tz;
		public final float[] rate;
		public final float[] phase;
		public final double life;
		/** Number of petals that leave in the stream (two thirds). */
		public final int streamCount;
		ScreenFx.Layer vignette;
		double nextDust;
		/** Drawn petals of the last frame (harness). */
		public int visible;

		State(int ownerId, long seed, int slot, StormMath.Storm storm, Vec3d feet0, boolean stub, int blades, double[] bx, double[] by, double[] bz, double life) {
			this.ownerId = ownerId;
			this.seed = seed;
			this.epoch = FxClock.now;
			this.slot = slot;
			this.storm = storm;
			this.feet0 = feet0;
			this.stub = stub;
			this.blades = blades;
			this.bx = bx;
			this.by = by;
			this.bz = bz;
			this.life = life;
			int m = storm.m;
			int streams = 0;
			for (int j = 0; j < m; j++) {
				if (storm.isStream(j)) {
					streams++;
				}
			}
			this.streamCount = streams;
			tx = new float[m];
			ty = new float[m];
			tz = new float[m];
			rate = new float[m];
			phase = new float[m];
			for (int j = 0; j < m; j++) {
				double ux = FxHash.signed(seed, j, 51);
				double uy = FxHash.signed(seed, j, 52);
				double uz = FxHash.signed(seed, j, 53);
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
				rate[j] = (float) (4 + 6 * FxHash.unit(seed, j, 54));
				phase[j] = (float) (FxHash.unit(seed, j, 55) * Math.PI * 2);
			}
		}

		/** Storm time: seconds since the cast, minus the HITSTOP held on the timeline. */
		public double time() {
			return FxClock.now - epoch - FxTimelines.held(slot);
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	public static int casts;
	public static int impacts;
	public static int stubCasts;

	private StormFx() {
	}

	public static Map<Integer, State> states() {
		return STATES;
	}

	public static void clear() {
		STATES.clear();
	}

	public static State of(int ownerId) {
		return STATES.get(ownerId);
	}

	/** All tunable numbers of the storm (keys {@code storm.*}). */
	public static StormMath.Params params() {
		StormMath.Params p = new StormMath.Params();
		p.rows = RowsFx.timingFor(RowsPhase.SCATTERED);
		p.corridorSpan = FxTune.d("storm.corridorSpan", p.corridorSpan);
		p.corridorUp = FxTune.d("storm.corridorUp", p.corridorUp);
		p.helixRise = FxTune.d("storm.helixRise", p.helixRise);
		p.helixHeight = FxTune.d("storm.helixHeight", p.helixHeight);
		p.rBase = FxTune.d("storm.rBase", p.rBase);
		p.rSpread = FxTune.d("storm.rSpread", p.rSpread);
		p.rExp = FxTune.d("storm.rExp", p.rExp);
		p.rNoise = FxTune.d("storm.rNoise", p.rNoise);
		p.spinBase = FxTune.d("storm.spin", p.spinBase);
		p.spinTaper = FxTune.d("storm.spinTaper", p.spinTaper);
		p.radiusScale = FxTune.d("storm.radiusScale", p.radiusScale);
		p.looseFraction = FxTune.d("storm.loose", p.looseFraction);
		p.looseR0 = FxTune.d("storm.looseR0", p.looseR0);
		p.looseR1 = FxTune.d("storm.looseR1", p.looseR1);
		p.streamFraction = FxTune.d("storm.stream", p.streamFraction);
		p.launchStart = FxTune.d("storm.launchStart", p.launchStart);
		p.launchSpan = FxTune.d("storm.launchSpan", p.launchSpan);
		p.speedMin = FxTune.d("storm.speed", p.speedMin);
		p.maxFlight = FxTune.d("storm.maxFlight", p.maxFlight);
		p.streamWobble = FxTune.d("storm.wobble", p.streamWobble);
		p.sphereR0 = FxTune.d("storm.sphereR", p.sphereR0);
		p.sphereR1 = FxTune.d("storm.sphereR1", p.sphereR1);
		p.sphereR2 = FxTune.d("storm.sphereR2", p.sphereR2);
		p.shrinkStart = FxTune.d("storm.shrinkStart", p.shrinkStart);
		p.shrinkEnd = FxTune.d("storm.shrinkEnd", p.shrinkEnd);
		p.impactTime = FxTune.d("storm.impact", p.impactTime);
		p.sphereRev = FxTune.d("storm.sphereRev", p.sphereRev);
		p.burstR = FxTune.d("storm.burstR", p.burstR);
		p.burstSpan = FxTune.d("storm.burstSpan", p.burstSpan);
		p.burstFall = FxTune.d("storm.burstFall", p.burstFall);
		p.returnStart = FxTune.d("storm.returnStart", p.returnStart);
		p.returnJitter = FxTune.d("storm.returnJitter", p.returnJitter);
		p.returnSpan = FxTune.d("storm.returnSpan", p.returnSpan);
		p.returnUp = FxTune.d("storm.returnUp", p.returnUp);
		return p;
	}

	private static double[] vec(Vec3d v) {
		return new double[] {v.x, v.y, v.z};
	}

	/** Projects a world point to screen fractions (0..1, y down) for the speed lines; centre when it is behind the camera. */
	static float[] screenPoint(MinecraftClient mc, Vec3d p) {
		var cam = mc.gameRenderer.getCamera();
		Vec3d c = cam.getPos();
		var q = cam.getRotation();
		org.joml.Vector3f right = new org.joml.Vector3f(1, 0, 0).rotate(q);
		org.joml.Vector3f up = new org.joml.Vector3f(0, 1, 0).rotate(q);
		double yaw = Math.toRadians(cam.getYaw());
		double pitch = Math.toRadians(cam.getPitch());
		double fx = -Math.sin(yaw) * Math.cos(pitch);
		double fy = -Math.sin(pitch);
		double fz = Math.cos(yaw) * Math.cos(pitch);
		double dx = p.x - c.x;
		double dy = p.y - c.y;
		double dz = p.z - c.z;
		double z = dx * fx + dy * fy + dz * fz;
		if (z < 0.5) {
			return new float[] {0.5f, 0.5f};
		}
		double x = dx * right.x + dy * right.y + dz * right.z;
		double y = dx * up.x + dy * up.y + dz * up.z;
		double tan = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
		double aspect = (double) mc.getWindow().getFramebufferWidth() / Math.max(1, mc.getWindow().getFramebufferHeight());
		double sx = 0.5 + (x / z) / (2.0 * tan * aspect);
		double sy = 0.5 - (y / z) / (2.0 * tan);
		return new float[] {(float) FxMath.clamp(sx, 0.05, 0.95), (float) FxMath.clamp(sy, 0.05, 0.95)};
	}

	/** Screen strength of an impact: by the distance of the camera to the impact point, never below 0.45 for the caster himself. */
	static double impactStrength(EffectTimeline x, Vec3d at) {
		MinecraftClient mc = MinecraftClient.getInstance();
		double d = mc.gameRenderer.getCamera().getPos().distanceTo(at);
		double s = FxMath.screenStrength(d);
		if (x.localCaster) {
			s = Math.min(1.0, Math.max(FxTune.d("storm.casterImpactMin", 0.45), 2.0 * s));
		}
		return s;
	}

	// ------------------------------------------------------------------------------------------ event (id 32)

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
		Vec3d aim = pr.length >= 3 ? new Vec3d(pr[0], pr[1], pr[2]) : pos.add(dir.multiply(20)).add(0, 1.2, 0);
		RowsFx.State rs = RowsFx.of(e.casterId());
		boolean haveRows = rs != null && rs.endAt < 0;
		StormMath.Params p = params();
		int budget = (int) (FxConfig.maxPetals * FxConfig.effectQuality);
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
			// stub (6.2): no rows standing (late join beyond range, culled): the petals appear on a ring of r 6 around the owner
			stubCasts++;
			blades = Math.max(20, Math.min(150, budget / 15));
			dist = new float[blades];
			bx = new double[blades];
			by = new double[blades];
			bz = new double[blades];
			for (int i = 0; i < blades; i++) {
				double a = 2 * Math.PI * i / blades;
				dist[i] = (float) (50.0 * FxHash.unit(e.seed(), i, 60));
				bx[i] = pos.x + 6 * Math.cos(a);
				bz[i] = pos.z + 6 * Math.sin(a);
				by[i] = FxGround.sample(bx[i], bz[i], pos.y) - 0.5;
			}
		}
		int per = StormMath.petalsPerBlade(budget, blades);
		double yaw = Math.atan2(-dir.x, dir.z);
		long seed = e.seed() * 0x2545F4914F6CDD1DL + 32;
		StormMath.Storm storm = new StormMath.Storm(p, seed, blades, per, dist, yaw, pos.distanceTo(aim));
		EffectTimeline t = FxTimelines.create(32, e.seed(), pos, dir, e.params(), e.casterId(), FxClient.forceRemote());
		double life = Math.max(6.0, p.rows.end(haveRows ? rs.maxDist : 50) + 0.4);
		State st = new State(e.casterId(), seed, t.slot, storm, pos, !haveRows, blades, bx, by, bz, life);
		Vec3d lk = dir.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : dir.normalize();
		st.look[0] = lk.x;
		st.look[1] = lk.y;
		st.look[2] = lk.z;
		st.aim[0] = aim.x;
		st.aim[1] = aim.y;
		st.aim[2] = aim.z;
		STATES.put(e.casterId(), st);
		if (haveRows) {
			RowsFx.setPhase(rs, RowsPhase.SCATTERED, FxClock.now);
		}
		ReiatsuTest.LOGGER.info("[fx] storm: {} petals ({} blades x {}), rows {}, aim ({}, {}, {}), flight {} m", storm.m, blades, per, haveRows ? "standing" : "stub ring",
				f(aim.x), f(aim.y), f(aim.z), f(pos.distanceTo(aim)));
		schedule(t, st, aim);
		FxTimelines.start(t);
	}

	private static String f(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	private static void schedule(EffectTimeline t, State st, Vec3d aim) {
		double imp = st.storm.p.impactTime;
		double cut0 = st.storm.p.rows.cutStart;
		// 0.00 command: tips overdrive (BankaiBladeRenderer), blades shiver
		t.at(0.0, x -> {
			x.sound("block.amethyst_block.resonate", 0.9, 0.8);
			x.sound("entity.illusioner.prepare_mirror", 1.2, 0.6);
		});
		// 0.30 burning dissolve: petals emerge at the cut
		t.at(cut0, x -> {
			x.flash(FxTune.d("storm.flashStart", 0.15), "#F3E4FF", false);
			x.sound("entity.phantom.flap", 0.8, 0.3);
			x.sound("block.amethyst_block.break", 1.4, 0.3);
		});
		for (int k = 0; k < 6; k++) {
			final int kk = k;
			t.at(cut0 + 0.07 * k, x -> x.sound("block.pink_petals.break", 0.8 + 0.3 * x.rng(kk).nextDouble(), 0.7));
		}
		// 0.80 storm (server tornado 0.8 to 1.5)
		t.at(0.80, x -> {
			x.sound("item.elytra.flying", 0.9, 0.35);
			x.sound("entity.breeze.whirl", 0.8, 0.6);
			st.vignette = x.vignette(ScreenFx.Kind.DARK, FxTune.d("storm.vignette", 0.10), 0.4);
		});
		t.at(1.20, x -> x.sound("entity.breeze.whirl", 0.8, 0.6));
		t.at(1.80, x -> x.sound("item.elytra.flying", 0.9, 0.35));
		t.at(2.80, x -> x.sound("item.elytra.flying", 0.9, 0.35));
		for (int k = 0; k < 4; k++) {
			t.at(0.8 + 0.2 * k, x -> {
				if (x.localCaster) {
					x.shake(FxTune.d("storm.shake", 0.15), 0.3);
				}
			});
		}
		// 1.50 directed attack: the stream peels off the front of the tornado
		t.at(FxTune.d("storm.launchStart", 1.5), x -> {
			x.shake(FxTune.d("storm.attackShake", 0.6), 0.3);
			x.sound("entity.breeze.wind_burst", 0.7, 1.0);
			x.sound("item.trident.riptide_3", 1.3, 0.6);
			x.sound("entity.wind_charge.wind_burst", 0.8, 0.5);
			if (x.screen && x.strength > 0.25) {
				float[] sp = screenPoint(MinecraftClient.getInstance(), aim);
				ScreenFx.speedLines(FxTune.d("storm.speedLines", 0.25) * x.strength, 0.3, sp[0], sp[1]);
			}
		});
		// 2.00 envelop: the sphere contracts around the aim, the core glow grows
		for (int k = 0; k < 4; k++) {
			final int kk = k;
			t.at(2.0 + 0.1 * k, x -> x.sound("entity.player.attack.sweep", 1.0 + 0.1 * kk, 0.6));
			t.at(2.0 + 0.12 * k, x -> x.sound("block.pink_petals.break", 1.0 + 0.4 * x.rng(10 + kk).nextDouble(), 0.5));
		}
		// 2.50 impact (server 12 HP)
		t.at(imp, x -> impact(x, st, aim));
		// 3.00 to 4.50 return
		for (int k = 0; k < 4; k++) {
			t.at(st.storm.p.returnStart + 0.3 * k, x -> x.sound("block.pink_petals.step", 0.9, 0.5));
		}
		t.at(st.storm.p.returnStart, x -> {
			if (st.vignette != null) {
				st.vignette.release(1.0);
			}
		});
		// 4.50 to 5.70 re-form
		t.at(st.storm.p.rows.reformStart, x -> x.sound("block.amethyst_block.chime", 0.8, 0.4));
		t.at(st.storm.p.rows.reformStart + 0.7, x -> x.sound("block.beacon.power_select", 0.9, 0.4));
		t.lifetime(st.life);
	}

	private static void impact(EffectTimeline x, State st, Vec3d aim) {
		impacts++;
		x.hitstop(FxTune.d("storm.hitstop", 60));
		Random r = x.rng(900);
		double q = FxConfig.effectQuality * x.lod;
		double gy = FxGround.sample(aim.x, aim.z, aim.y);
		x.soundAt("entity.generic.explode", aim, 1.1, 0.9);
		x.soundAt("item.mace.smash_ground_heavy", aim, 1.2, 0.6);
		for (int k = 0; k < 3; k++) {
			x.soundAt("block.pink_petals.break", aim, 0.7 + 0.1 * k, 0.7);
		}
		double s = impactStrength(x, aim);
		if (x.screen) {
			ScreenFx.flash(FxTune.d("storm.impactFlash", 0.30), FxMath.hex("#F3E4FF"), false, s);
			ScreenFx.shake(FxTune.d("storm.impactShake", 0.9), 0.4, s);
		}
		// white core over pink: glow_soft size 10 white 1.0 to 0 in 0.3 s, pink bloom behind it
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(aim.x, aim.y, aim.z).size(4.0, FxTune.d("storm.coreSize", 10)).sizeEase(FxMath.OC).life(0.3).curve(0.01, 0.8).color("#FFFFFF").peak(1.0)
				.owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(aim.x, aim.y, aim.z).size(6.0, 14).sizeEase(FxMath.OC).life(0.55).curve(0.02, 0.7).color("#F9C8F6").peak(0.55).owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.STAR4).at(aim.x, aim.y, aim.z).size(3.0, 9.0).sizeEase(FxMath.OC).life(0.25).curve(0.01, 0.8).color("#FFFFFF").peak(0.9).rot(0.3, 0.8).owner(x).spawn();
		// ground ring r 0 to 9 in 0.35 s
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(aim.x, gy + 0.06, aim.z).ground().size(0.5, FxTune.d("storm.ringSize", 9.0) / 0.375).sizeEase(FxMath.OC).life(0.35).curve(0.01, 0.6)
				.color("#E5A4DC").peak(0.9).owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(aim.x, gy + 0.07, aim.z).ground().size(0.5, FxTune.d("storm.ringSize", 9.0) / 0.4375).sizeEase(FxMath.OC).life(0.4).curve(0.01, 0.6)
				.color("#FFFFFF").peak(0.5).owner(x).spawn();
		// 120 star4 glints, 300 petal sparks (speed 6, life 20 ticks), 30 dust puffs
		int stars = (int) (120 * q);
		for (int i = 0; i < stars; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double c = r.nextDouble() * 2 - 1;
			double rr = Math.sqrt(1 - c * c);
			double sp = 5 + r.nextDouble() * 9;
			FxGlowBatch.sprite(GlowSprite.STAR4).at(aim.x, aim.y, aim.z).vel(rr * Math.cos(a) * sp, c * sp, rr * Math.sin(a) * sp).drag(0.92).gravity(1.5).lifeTicks(8 + r.nextInt(10))
					.size(0.35 + r.nextDouble() * 0.5).color(r.nextBoolean() ? "#FFE9FB" : "#FFFFFF").peak(0.9).twinkle().rot(r.nextDouble() * 6.28, 0).owner(x).spawn();
		}
		int sparks = (int) (300 * FxConfig.effectQuality * x.lod);
		for (int i = 0; i < sparks; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double c = r.nextDouble() * 1.4 - 0.4;
			double rr = Math.sqrt(Math.max(0, 1 - c * c));
			double sp = 6 * (0.5 + r.nextDouble());
			FxParticles.spec(FxParticles.Kind.PETAL).at(aim.x, aim.y, aim.z).vel(rr * Math.cos(a) * sp, c * sp, rr * Math.sin(a) * sp).drag(0.93).gravity(0.5).life(20).size(0.32)
					.color(r.nextBoolean() ? "#FFE9FB" : "#F9C8F6").colorTo("#E5A4DC").spin((r.nextDouble() - 0.5) * 0.5).seed(r.nextInt(4)).owner(x).spawn();
		}
		int dust = (int) (30 * FxConfig.effectQuality * x.lod);
		for (int i = 0; i < dust; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double sp = 2.5 + r.nextDouble() * 3.5;
			FxParticles.spec(FxParticles.Kind.DUST_PUFF).at(aim.x, gy + 0.2, aim.z).vel(Math.cos(a) * sp, 0.4 + r.nextDouble() * 1.2, Math.sin(a) * sp).drag(0.9).life(24)
					.size(1.0 + r.nextDouble() * 0.6, 2.4).color("#BFB6A8").alpha(0.5).seed(r.nextInt(4)).owner(x).spawn();
		}
	}

	// ------------------------------------------------------------------------------------------ per frame

	/** Once per frame at WorldRenderEvents.START: expiry, the foot dust of the tornado (10 puffs per second). */
	public static void frame(MinecraftClient mc) {
		if (STATES.isEmpty() || mc.world == null) {
			return;
		}
		Random r = new Random((long) (FxClock.now * 1000) + 3);
		Iterator<State> it = STATES.values().iterator();
		while (it.hasNext()) {
			State st = it.next();
			double t = st.time();
			if (t > st.life) {
				it.remove();
				continue;
			}
			double tornadoEnd = st.storm.p.returnStart;
			if (t >= 0.8 && t < tornadoEnd && FxClock.dt > 0 && FxClock.now >= st.nextDust) {
				st.nextDust = FxClock.now + 0.1;
				Entity ow = mc.world.getEntityById(st.ownerId);
				Vec3d f = ow != null ? ow.getPos() : st.feet0;
				if (mc.gameRenderer.getCamera().getPos().distanceTo(f) < 64) {
					double a = r.nextDouble() * Math.PI * 2;
					double rad = 2.5 + r.nextDouble() * 3.0;
					double vx = -Math.sin(a) * 2.0;
					double vz = Math.cos(a) * 2.0;
					FxParticles.spec(FxParticles.Kind.DUST_PUFF).at(f.x + Math.cos(a) * rad, f.y + 0.15, f.z + Math.sin(a) * rad).vel(vx, 0.6 + r.nextDouble() * 0.5, vz).drag(0.93).life(22)
							.size(0.9, 1.9).color("#BFB6A8").alpha(0.45).seed(r.nextInt(4)).spawn();
				}
			}
		}
	}
}
