package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.AnchorPlan;
import dev.minebleach.reiatsutest.core.fx.BankaiCurves;
import dev.minebleach.reiatsutest.core.fx.BladeRowLayout;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Byakuya bankai release and the standing blade rows (VFX_STORYBOARD 6.1): the quiet drop of the hilt with its ripples, night
 * falling (GRADE + vignette), fault lines racing along the rows, the eruption of the giant blades with a ground burst and a tip glint
 * per blade, the moonlight sweep, the hold with a 3 degree lean, then the static buffer. The rows are static at the release point (S2): everything is a
 * function of the event time, the layout and the release yaw. {@link BankaiBladeRenderer} draws, this class owns the state, the
 * timeline of the release and the per-blade bursts.
 */
public final class RowsFx {
	public static final String NIGHT = "#5A6A9C";
	public static final String NIGHT_PLAY = "#7884B0";

	/** One standing set of rows. */
	public static final class State {
		public final int ownerId;
		public final long seed;
		public final BladeRowLayout layout;
		public final double epoch;
		public final double yawDeg;
		public final double fx;
		public final double fy;
		public final double fz;
		public final double[] bx;
		public final double[] bz;
		/** Ground surface height under every blade. */
		public final double[] ground;
		public final int[] light;
		/** Blades sorted by eruption start. */
		final int[] order;
		int nextBurst;
		int nextGlint;
		public double endAt = -1;
		public double lastSeen = FxClock.now;
		public boolean anchorSeen;
		public final double created = FxClock.now;
		/** Time (seconds from the release) from which the rows are drawn from the static buffer. */
		public final double settleAt;
		ScreenFx.Layer grade;
		ScreenFx.Layer vignette;
		ScreenFx.Layer vignette2;
		boolean owner;
		boolean lateStarted;
		int crumbs;
		double nextHaze;
		double nextBeacon;
		double nextRippleLoop;
		// static buffer
		VertexBuffer vbo;
		int vboVertices;
		long vboLightStamp;
		int vboLightHash;
		long lastLightCheck;
		boolean vboValid;

		State(int ownerId, long seed, BladeRowLayout layout, double epoch, double yawDeg, double fx, double fy, double fz, ClientWorld w) {
			this.ownerId = ownerId;
			this.seed = seed;
			this.layout = layout;
			this.epoch = epoch;
			this.yawDeg = yawDeg;
			this.fx = fx;
			this.fy = fy;
			this.fz = fz;
			int n = layout.n;
			bx = new double[n];
			bz = new double[n];
			ground = new double[n];
			light = new int[n];
			double[] o = new double[3];
			Integer[] idx = new Integer[n];
			for (int i = 0; i < n; i++) {
				layout.world(i, yawDeg, fx, fz, o);
				bx[i] = o[0];
				bz[i] = o[2];
				ground[i] = FxGround.sample(w, bx[i], bz[i], fy);
				idx[i] = i;
			}
			java.util.Arrays.sort(idx, (a, b) -> Float.compare(layout.start[a], layout.start[b]));
			order = new int[n];
			for (int i = 0; i < n; i++) {
				order[i] = idx[i];
			}
			double maxDist = 0;
			for (int i = 0; i < n; i++) {
				maxDist = Math.max(maxDist, layout.dist[i]);
			}
			settleAt = layout.completionTime() + Math.max(1.25, maxDist / BankaiCurves.SWEEP_SPEED + 0.4);
			refreshLight(w);
		}

		public double time() {
			return FxClock.now - epoch;
		}

		/** Hilt point on the ground: 0.6 ahead of the feet along the flat look. */
		public double hiltX() {
			return fx - Math.sin(Math.toRadians(yawDeg)) * BladeRowLayout.HILT_AHEAD;
		}

		public double hiltZ() {
			return fz + Math.cos(Math.toRadians(yawDeg)) * BladeRowLayout.HILT_AHEAD;
		}

		public double hiltGround;

		int refreshLight(ClientWorld w) {
			int h = 0;
			BlockPos.Mutable p = new BlockPos.Mutable();
			for (int i = 0; i < layout.n; i++) {
				p.set(bx[i], ground[i] + 1.0, bz[i]);
				light[i] = WorldRenderer.getLightmapCoordinates(w, p);
				h = h * 31 + light[i];
			}
			return h;
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	public static int released;
	public static int bursts;
	public static boolean requireAnchor = true;

	private RowsFx() {
	}

	public static Map<Integer, State> states() {
		return STATES;
	}

	public static State of(int ownerId) {
		return STATES.get(ownerId);
	}

	public static void clear() {
		for (State s : STATES.values()) {
			if (s.vbo != null) {
				s.vbo.close();
			}
		}
		STATES.clear();
	}

	// ------------------------------------------------------------------------------------------ release (id 4)

	public static void onRelease(EffectEventS2C e) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		State old = STATES.remove(e.casterId());
		if (old != null && old.vbo != null) {
			old.vbo.close();
		}
		State st = create(e.casterId(), e.seed(), FxClock.now, AnchorPlan.flatYawDeg(e.dx(), e.dz()), e.x(), e.y(), e.z(), w);
		st.owner = !FxClient.forceRemote() && mc.player != null && mc.player.getId() == e.casterId();
		released++;
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		EffectTimeline t = FxTimelines.create(4, e.seed(), pos, new Vec3d(e.dx(), e.dy(), e.dz()), e.params(), e.casterId(), FxClient.forceRemote());
		double hx = st.hiltX();
		double hz = st.hiltZ();
		double hg = st.hiltGround;
		BladeRowLayout l = st.layout;
		double comp = l.completionTime();
		// 0.18: the hilt reaches the ground and sinks into it like into water; three ripples
		t.at(0.18, x -> {
			x.soundAt("item.trident.hit_ground", new Vec3d(hx, hg, hz), 0.8, 0.9);
			x.soundAt("block.bell.use", new Vec3d(hx, hg, hz), 0.5, 0.6);
			x.soundAt("block.bubble_column.whirlpool_inside", new Vec3d(hx, hg, hz), 1.6, 0.3);
		});
		// 0.30 to 0.90: night falls
		t.at(0.30, x -> {
			st.grade = x.grade(FxTune.s("bankai.night", NIGHT), FxTune.d("bankai.nightStrength", 1.0), 0.6);
			st.vignette = x.vignette(ScreenFx.Kind.DARK, FxTune.d("bankai.vignette", 0.32), 0.6);
			x.sound("ambient.cave", 0.8, 0.6);
			x.sound("block.respawn_anchor.charge", 0.6, 0.6);
		});
		t.at(0.60, x -> x.sound("entity.warden.heartbeat", 0.7, 0.5));
		// 0.90: fault lines (anticipation)
		t.at(0.90, x -> {
			x.sound("entity.warden.emerge", 1.4, 0.4);
			faultLines(x, st);
		});
		for (int k = 0; k < 6; k++) {
			double amp = 0.1 + 0.1 * k;
			t.at(0.90 + 0.2 * k, x -> x.shake(amp, 0.3));
		}
		// 1.00: eruption starts
		t.at(1.00, x -> {
			x.sound("item.mace.smash_ground_heavy", 0.6, 0.7);
			x.sound("entity.ender_dragon.growl", 0.5, 0.25);
		});
		for (double ts = 1.2; ts < comp; ts += 0.2) {
			t.at(ts, x -> x.shake(0.6, 0.25));
		}
		// rows complete
		t.at(comp, x -> {
			x.shake(FxTune.d("bankai.completeShake", 1.2), 0.4);
			x.sound("block.beacon.ambient", 0.6, 0.35);
			x.sound("block.bell.resonate", 0.6, 0.4);
		});
		// hold: the vignette settles to the playable level
		t.at(comp + 0.6, x -> {
			if (st.vignette != null) {
				st.vignette.release(1.0);
			}
			st.vignette2 = x.vignette(ScreenFx.Kind.DARK, FxTune.d("bankai.vignetteHold", 0.24), 1.0);
		});
		t.lifetime(comp + 1.5);
		// ripples twins and the fall of the hilt (glow rings), ripple k at 0.18, 0.40, 0.62
		double[] starts = {0.18, 0.40, 0.62};
		double[] radii = {6.0, 4.5, 3.0};
		for (int k = 0; k < 3; k++) {
			final int kk = k;
			t.at(starts[k], x -> FxGlowBatch.sprite(GlowSprite.RING_THIN).at(hx, hg + 0.06, hz).ground().size(0.4, radii[kk] / 0.4375).sizeEase(FxMath.OC).life(1.0)
					.curve(0.02, 0.85).color("#F3E4FF").peak(0.35).owner(x).spawn());
		}
		FxTimelines.start(t);
		ReiatsuTest.LOGGER.info("[fx] rows: {} blades, {} ranks, completion {} s, settle {} s, length {}", l.n, l.ranks, String.format(java.util.Locale.ROOT, "%.2f", comp),
				String.format(java.util.Locale.ROOT, "%.2f", st.settleAt), String.format(java.util.Locale.ROOT, "%.1f", l.length()));
	}

	private static State create(int owner, long seed, double epoch, double yawDeg, double x, double y, double z, ClientWorld w) {
		BladeRowLayout layout = BladeRowLayout.of(FxConfig.bankaiBladeCount, seed);
		State st = new State(owner, seed, layout, epoch, yawDeg, x, y, z, w);
		st.hiltGround = FxGround.sample(w, st.hiltX(), st.hiltZ(), y);
		STATES.put(owner, st);
		return st;
	}

	/** A rows anchor this client saw without the event (late join): created settled or at its true age. */
	public static State lateState(int owner, long seed, double yawDeg, double x, double y, double z, double ageSeconds, ClientWorld w) {
		State st = create(owner, seed, FxClock.now - ageSeconds, yawDeg, x, y, z, w);
		st.lateStarted = true;
		st.nextBurst = st.layout.n;
		st.nextGlint = st.layout.n;
		return st;
	}

	private static void faultLines(EffectTimeline x, State st) {
		BladeRowLayout l = st.layout;
		MinecraftClient mc = MinecraftClient.getInstance();
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		double rowYaw = Math.atan2(Math.cos(Math.toRadians(st.yawDeg)), -Math.sin(Math.toRadians(st.yawDeg)));
		double speed = FxTune.d("bankai.faultSpeed", 100);
		for (int i = 0; i < l.n; i++) {
			double dist = l.dist[i];
			double delay = BankaiCurves.faultTime(dist, speed) - 0.90;
			boolean inner = l.rank[i] == 0;
			double dcam = Math.hypot(st.bx[i] - cam.x, st.bz[i] - cam.z);
			if (inner && (i % 2 == 0 || dcam < 30)) {
				// the racing line: a 2.4 long segment along the row, bright for a moment
				FxGlowBatch.sprite(GlowSprite.LINE).at(st.bx[i], st.ground[i] + 0.04, st.bz[i]).ground().rot(rowYaw, 0).size(2.6).delay(delay).life(0.5).curve(0.04, 0.7)
						.color("#F3E4FF").peak(FxTune.d("bankai.faultIntensity", 0.5)).spawn();
			}
			if (dcam < 45) {
				// glowing crack in the ground until the blade comes up
				double up = l.start[i] - 0.90 - delay + 0.25;
				FxGlowBatch.sprite(GlowSprite.CRACK).at(st.bx[i], st.ground[i] + 0.05, st.bz[i]).ground().rot(i * 1.7, 0).size(1.7).delay(delay).life(Math.max(0.2, up)).curve(0.05, 0.5)
						.color("#F3E4FF").peak(0.40).spawn();
			}
		}
	}

	// ------------------------------------------------------------------------------------------ seal

	/** Rows sink (reverse rise, delay 0.006 per block), the ground hilt flies to the hand, grade and vignette fade 1.0 s. */
	public static void end(int ownerId) {
		State st = STATES.get(ownerId);
		if (st == null || st.endAt >= 0) {
			return;
		}
		st.endAt = FxClock.now;
		for (ScreenFx.Layer l : new ScreenFx.Layer[] {st.grade, st.vignette, st.vignette2}) {
			if (l != null) {
				l.release(1.0);
			}
		}
	}

	/** Seconds after {@code endAt} when the last blade has sunk. */
	public static double sinkDuration(State st) {
		double maxDist = 0;
		for (float d : st.layout.dist) {
			maxDist = Math.max(maxDist, d);
		}
		return 0.5 + 0.006 * maxDist + 0.3;
	}

	// ------------------------------------------------------------------------------------------ per frame

	/** Once per frame at WorldRenderEvents.START: bursts, glints, hold effects, expiry. */
	public static void frame(MinecraftClient mc) {
		if (STATES.isEmpty() || mc.world == null) {
			return;
		}
		ClientWorld w = mc.world;
		Vec3d cam = mc.gameRenderer.getCamera().getPos();
		Random r = new Random((long) (FxClock.now * 1000));
		Iterator<State> it = STATES.values().iterator();
		while (it.hasNext()) {
			State st = it.next();
			double t = st.time();
			if (st.endAt >= 0 && FxClock.now - st.endAt > sinkDuration(st) + 0.4) {
				if (st.vbo != null) {
					st.vbo.close();
				}
				it.remove();
				continue;
			}
			BladeRowLayout l = st.layout;
			if (st.endAt < 0) {
				while (st.nextBurst < l.n && l.start[st.order[st.nextBurst]] <= t) {
					int i = st.order[st.nextBurst++];
					if (t - l.start[i] < 0.6) {
						burst(st, i, w, cam, r);
					}
				}
				while (st.nextGlint < l.n && l.start[st.order[st.nextGlint]] + 0.30 <= t) {
					int i = st.order[st.nextGlint++];
					if (t - l.start[i] < 0.9) {
						glint(st, i, cam);
					}
				}
				if (t > 5.0 && st.grade != null) {
					double k = FxMath.smoothstep(5.0, 8.0, t);
					float[] a = FxMath.hex(NIGHT);
					float[] b = FxMath.hex(NIGHT_PLAY);
					st.grade.rgb[0] = (float) FxMath.lerp(a[0], b[0], k);
					st.grade.rgb[1] = (float) FxMath.lerp(a[1], b[1], k);
					st.grade.rgb[2] = (float) FxMath.lerp(a[2], b[2], k);
				}
				// hold: petal haze in the corridor (10 per second), beacon hum every 3 s, small ripple at the hilt every 4 s
				if (t > l.completionTime() && FxClock.dt > 0) {
					if (FxClock.now >= st.nextHaze) {
						st.nextHaze = FxClock.now + 0.1;
						haze(st, cam, r);
					}
					if (FxClock.now >= st.nextBeacon && cam.distanceTo(new Vec3d(st.fx, st.fy, st.fz)) < 60) {
						st.nextBeacon = FxClock.now + 3.0;
						FxSound.play(null, "block.beacon.ambient", st.fx, st.fy + 1, st.fz, 0.6, 0.25);
					}
					if (FxClock.now >= st.nextRippleLoop) {
						st.nextRippleLoop = FxClock.now + 4.0;
						FxGlowBatch.sprite(GlowSprite.RING_THIN).at(st.hiltX(), st.hiltGround + 0.06, st.hiltZ()).ground().size(0.3, 1.5 / 0.4375).sizeEase(FxMath.OC).life(1.2)
								.curve(0.03, 0.8).color("#F3E4FF").peak(0.3).spawn();
					}
				}
			}
			// static buffer light refresh (every 40 ticks of frames: about 2 s)
			if (st.vboValid && FxClock.frame - st.lastLightCheck > 120) {
				st.lastLightCheck = FxClock.frame;
				int h = st.refreshLight(w);
				if (h != st.vboLightHash) {
					st.vboValid = false;
				}
			}
		}
	}

	private static void burst(State st, int i, ClientWorld w, Vec3d cam, Random r) {
		bursts++;
		double x = st.bx[i];
		double z = st.bz[i];
		double g = st.ground[i];
		double dcam = Math.sqrt((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z));
		BladeRowLayout l = st.layout;
		if (dcam < 64) {
			int dust = FxConfig.tier().dustPerBlade;
			BlockState bs = FxGround.stateAt(w, x, z, g);
			float[] tint = tintOf(bs, w, x, g, z);
			for (int k = 0; k < dust && FxParticles.live() < FxConfig.maxFxParticles; k++) {
				double a = r.nextDouble() * Math.PI * 2;
				FxParticles.spec(FxParticles.Kind.DUST_PUFF).at(x, g + 0.15, z).vel(Math.cos(a) * 1.4, 0.8 + r.nextDouble() * 0.8, Math.sin(a) * 1.4).drag(0.93).life(22)
						.size(0.9 + r.nextDouble() * 0.5, 1.8).color(tint).alpha(0.55).seed(r.nextInt(4)).spawn();
			}
			if (!bs.isAir() && st.crumbs < FxConfig.tier().dustCap * 3 && dcam < 40) {
				BlockStateParticleEffect fx = new BlockStateParticleEffect(ParticleTypes.BLOCK, bs);
				for (int k = 0; k < 3; k++) {
					st.crumbs++;
					double a = r.nextDouble() * Math.PI * 2;
					w.addParticle(fx, x, g + 0.2, z, Math.cos(a) * 0.15, 0.25 + r.nextDouble() * 0.2, Math.sin(a) * 0.15);
				}
			}
			if (i % 3 == 0) {
				FxSound.play(null, "block.pointed_dripstone.land", x, g, z, 0.6 + r.nextDouble() * 0.3, 0.4);
			}
		}
		if (l.n <= 400 || i % 2 == 0) {
			FxGlowBatch.sprite(GlowSprite.STREAK).at(x, g + 0.4, z).vel(0, 26, 0).streak().streakTime(0.12).size(3.0).lifeTicks(6).curve(0.01, 0.6).color("#F2E9FF")
					.peak(FxTune.d("bankai.burstStreak", 0.4)).spawn();
		}
	}

	private static float[] tintOf(BlockState bs, ClientWorld w, double x, double y, double z) {
		float[] base = FxMath.hex("#BFB6A8");
		try {
			int c = bs.getMapColor(w, BlockPos.ofFloored(x, y - 0.1, z)).color;
			float[] m = {((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
			return new float[] {base[0] * (0.5f + 0.5f * m[0]), base[1] * (0.5f + 0.5f * m[1]), base[2] * (0.5f + 0.5f * m[2])};
		} catch (RuntimeException e) {
			return base;
		}
	}

	/** Tip glint at the overshoot top: star4 1.2, white, 0.8 to 0 in 0.25 s at the tip. */
	private static void glint(State st, int i, Vec3d cam) {
		double[] tip = new double[3];
		BankaiBladeRenderer.tipPosition(st, i, st.time(), tip);
		double d = Math.sqrt((tip[0] - cam.x) * (tip[0] - cam.x) + (tip[2] - cam.z) * (tip[2] - cam.z));
		if (d > 140) {
			return;
		}
		if (st.layout.n > 400 && i % 2 == 1) {
			return;
		}
		FxGlowBatch.sprite(GlowSprite.STAR4).at(tip[0], tip[1], tip[2]).size(1.2 * st.layout.scale[i] * (d > 60 ? 1.6 : 1.0)).lifeTicks(5).curve(0.01, 0.8).color("#FFFFFF")
				.peak(0.8).rot(i * 0.9, 0).spawn();
	}

	private static void haze(State st, Vec3d cam, Random r) {
		double along = -r.nextDouble() * 30 + 4;
		double across = (r.nextDouble() - 0.5) * 9;
		double yaw = Math.toRadians(st.yawDeg);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -fz;
		double rz = fx;
		// near the camera along the corridor
		double ca = (cam.x - st.fx) * fx + (cam.z - st.fz) * fz;
		double px = st.fx + fx * (ca + along * 0.5) + rx * across;
		double pz = st.fz + fz * (ca + along * 0.5) + rz * across;
		double py = st.hiltGround + 0.5 + r.nextDouble() * 3.0;
		FxGlowBatch.sprite(GlowSprite.PETAL_GLOW).at(px, py, pz).vel((r.nextDouble() - 0.5) * 0.4, 0.15 + r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.4).lifeTicks(60).size(0.5)
				.color("#F9C8F6").peak(0.25).rot(r.nextDouble() * 6.28, (r.nextDouble() - 0.5) * 2).twinkle().spawn();
	}
}
