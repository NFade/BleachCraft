package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.AnchorPlan;
import dev.minebleach.reiatsutest.core.fx.BankaiCurves;
import dev.minebleach.reiatsutest.core.fx.BladeRowLayout;
import dev.minebleach.reiatsutest.core.fx.RowsPhase;
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
		/** FX clock value of the sword throw (plan A5); long ago for a late joiner (the hilt is already in the ground). */
		public double swordT0 = -1.0e9;
		/** Phase after a bankai ability (S3): {@link RowsPhase#STANDING}, {@link RowsPhase#SCATTERED} (id 32) or {@link RowsPhase#CONSUMED} (id 33). */
		public byte phase;
		/** FX clock value of the start of the phase. */
		public double phaseStart;
		public RowsPhase.Timing timing = RowsPhase.Timing.scatter();
		/** Game time (ticks) of the last phase change seen on the tracked anchor (S3), to apply each change once. */
		public int anchorPhaseTime = Integer.MIN_VALUE;
		/** While a bankai ability drives the night grade colour, the slow 5 to 8 s ease to the playable night is paused. */
		public boolean gradeLocked;
		/** Farthest blade from the release point. */
		public final double maxDist;
		/** World scale of every blade (bankai.bladeScale, read when the rows are created and kept for their life; the static buffer depends on it). */
		public final double bladeScale = bladeScaleSetting();
		/** Blade width across the lane in blocks (bankai.bladeWidth) and the anisotropic scales of the mesh (local z = width, local x = thickness), relative to {@link #bladeScale}. */
		public final double bladeWidth = bladeWidthSetting();
		public final double kz = BladeRowLayout.widthScale(bladeWidth, bladeScale);
		public final double kx = bladeThickSetting();
		/** Time (seconds from the release) from which the rows are drawn from the static buffer. */
		public final double settleAt;
		ScreenFx.Layer grade;
		ScreenFx.Layer vignette;
		ScreenFx.Layer vignette2;
		boolean owner;
		boolean lateStarted;
		int crumbs;
		double nextHaze;
		double nextHeartbeat;
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
			double md = 0;
			for (int i = 0; i < n; i++) {
				md = Math.max(md, layout.dist[i]);
			}
			this.maxDist = md;
			settleAt = layout.completionTime() + Math.max(1.25, md / BankaiCurves.SWEEP_SPEED + 0.4);
			refreshLight(w);
		}

		public double time() {
			return FxClock.now - epoch;
		}

		/** Frees everything the state holds that is not reclaimed by itself: the static vertex buffer and the held screen layers. */
		public void dispose(double fade) {
			if (vbo != null) {
				vbo.close();
				vbo = null;
			}
			vboValid = false;
			for (ScreenFx.Layer l : new ScreenFx.Layer[] {grade, vignette, vignette2}) {
				if (l != null) {
					l.release(fade);
				}
			}
			grade = null;
			vignette = null;
			vignette2 = null;
		}

		/** Seconds since the sword was thrown (not the rows clock: the rows start {@link #rowsDelay()} later). */
		public double swordTime() {
			return FxClock.now - swordT0;
		}


		/** Seconds since the start of the current phase (negative or meaningless while STANDING). */
		public double phaseTau() {
			return FxClock.now - phaseStart;
		}

		/** True while a phase still changes blades (the static buffer must not be drawn). */
		public boolean phaseBusy() {
			return phase != RowsPhase.STANDING && RowsPhase.active(timing, phaseTau(), maxDist);
		}

		/** Remaining fraction of blade i (1 whole, 0 gone) in the current phase. */
		public double fraction(int i) {
			return phase == RowsPhase.STANDING ? 1.0 : RowsPhase.fraction(timing, phaseTau(), layout.dist[i]);
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

	/**
	 * World scale of the bankai blades (fx_override.json {@code bankai.bladeScale}). The mesh is 8 m long (ART_BIBLE B13); r7: default 1.0 = 8 blocks, the
	 * slab blades of the anime references (3 to 4 person heights plus the curve). Width and thickness have their own keys.
	 */
	public static double bladeScaleSetting() {
		return FxMath.clamp(FxTune.d("bankai.bladeScale", BladeRowLayout.DEFAULT_BLADE_SCALE), 0.2, 2.0);
	}

	/** Width of a blade across the lane in blocks (bankai.bladeWidth, default 1.5; the mesh is 0.55 m wide, so it is stretched anisotropically). */
	public static double bladeWidthSetting() {
		return FxMath.clamp(FxTune.d("bankai.bladeWidth", BladeRowLayout.DEFAULT_BLADE_WIDTH), 0.1, 6.0);
	}

	/** Thickness multiplier of the blade (bankai.bladeThick, default 2.0 = 0.28 blocks at bladeScale 1). */
	public static double bladeThickSetting() {
		return FxMath.clamp(FxTune.d("bankai.bladeThick", BladeRowLayout.DEFAULT_BLADE_THICK), 0.2, 8.0);
	}

	/** Spacing and corridor width of the rows (fx_override.json {@code rows.scale}, default 0.65; 1.0 = the original 6 block half width, spacing 1.1). */
	public static double rowsScaleSetting() {
		return FxMath.clamp(FxTune.d("rows.scale", 0.65), 0.2, 3.0);
	}

	/**
	 * The wall of slab blades of the Byakuya bankai (r7): ONE rank per side, {@code rows.perSide} 10 blades each at {@code rows.halfWidth} 7 blocks from the axis,
	 * {@code rows.spacing} 1.0 between neighbours (they almost touch: the slab is 0.28 thick, 1.5 wide), from {@code rows.startAhead} -2 (2 blocks BEHIND the feet, r7 user request) further back. The count follows
	 * the quality tier ({@code bankaiBladeCount} 200 / 100 / 50 = 10 / 5 / 3 per side, the pitch widens for the lower tiers). {@code rows.legacy} 1 brings back the old two rank corridor.
	 */
	public static BladeRowLayout makeLayout(long seed) {
		if (FxTune.d("rows.legacy", 0.0) > 0.5) {
			return BladeRowLayout.of(FxConfig.bankaiBladeCount, seed, rowsScaleSetting());
		}
		int high = (int) Math.round(FxTune.d("rows.perSide", BladeRowLayout.DEFAULT_PER_SIDE));
		int per = (int) Math.round(high * FxConfig.bankaiBladeCount / 200.0);
		per = Math.max(3, Math.min(250, per));
		double pitch = BladeRowLayout.tierPitch(per, high, FxTune.d("rows.spacing", BladeRowLayout.DEFAULT_PITCH));
		return BladeRowLayout.lane(per, seed, FxTune.d("rows.halfWidth", BladeRowLayout.DEFAULT_HALF_WIDTH), pitch, FxTune.d("rows.startAhead", BladeRowLayout.DEFAULT_START_AHEAD));
	}

	/** The rows (fault lines, eruption) start this long after the throw of the sword, which needs about 1.5 s to hang and sink. */
	public static double rowsDelay() {
		return FxTune.d("bankai.rowsDelay", 0.8);
	}

	/** Height of the sword point above the ground while it hangs in front of the caster. */
	public static double swordHang() {
		return FxTune.d("bankai.swordHang", 1.0);
	}

	/**
	 * Height of the sword point when it has sunk: below the ground by the whole hilt length plus a small margin, so the sword disappears completely (G1).
	 * {@code bankai.hiltRemain} (hot reload, default 0) = the part of the hilt that stays visible above the ground; 0 = nothing remains.
	 */
	public static double swordEndY(double hiltScale) {
		double remain = Math.max(0.0, FxTune.d("bankai.hiltRemain", 0.0));
		return -(BankaiCurves.HILT_LENGTH * hiltScale - remain) - (remain > 0.0 ? 0.0 : BankaiCurves.SWORD_HIDE_MARGIN);
	}

	public static double swordContact(double hiltScale) {
		return BankaiCurves.swordContactTime(swordHang(), swordEndY(hiltScale));
	}


	public static Map<Integer, State> states() {
		return STATES;
	}

	public static State of(int ownerId) {
		return STATES.get(ownerId);
	}

	public static void clear() {
		for (State s : STATES.values()) {
			s.dispose(0.3);
		}
		STATES.clear();
	}

	/**
	 * Bankai cleanup of one owner. {@code instant} (death, logout, dimension change, hard drop): the state is gone now with its screen layers;
	 * otherwise the rows sink as designed ({@link #end}); either way nothing of it stays: the state is removed after the sink at the latest.
	 * Returns true when there was a state.
	 */
	public static boolean endOwner(int ownerId, boolean instant) {
		State st = STATES.get(ownerId);
		if (st == null) {
			return false;
		}
		if (instant) {
			STATES.remove(ownerId);
			st.dispose(0.6);
		} else {
			end(ownerId);
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------ phases (S3, ids 32 and 33)

	/** Timing of a phase with the tunable keys {@code storm.*} (scatter) and {@code hak.*} (Hakuteiken consumption). */
	public static RowsPhase.Timing timingFor(byte kind) {
		if (kind == RowsPhase.CONSUMED) {
			return new RowsPhase.Timing(FxTune.d("hak.cutStart", 0.0), FxTune.d("hak.cutPerBlock", 0.002), FxTune.d("hak.cutSpan", 0.30), FxTune.d("hak.reformStart", 3.0),
					FxTune.d("hak.reformPerBlock", 0.004), FxTune.d("hak.reformSpan", 0.6));
		}
		return new RowsPhase.Timing(FxTune.d("storm.cutStart", 0.30), FxTune.d("storm.cutPerBlock", 0.008), FxTune.d("storm.cutSpan", 0.40), FxTune.d("storm.reformStart", 4.5),
				FxTune.d("storm.reformPerBlock", 0.004), FxTune.d("storm.reformSpan", 0.6));
	}

	/** Starts a phase of the standing rows {@code startFx} (FX clock); also called from the tracked anchor for late joiners. */
	public static void setPhase(State st, byte kind, double startFx) {
		st.phase = kind;
		st.phaseStart = startFx;
		st.timing = timingFor(kind);
		ReiatsuTest.LOGGER.info("[fx] rows phase {} at fx {} (age {} s), {} blades, ends at {} s", kind, String.format(java.util.Locale.ROOT, "%.2f", startFx),
				String.format(java.util.Locale.ROOT, "%.2f", FxClock.now - startFx), st.layout.n, String.format(java.util.Locale.ROOT, "%.2f", st.timing.end(st.maxDist)));
	}

	// ------------------------------------------------------------------------------------------ release (id 4)

	public static void onRelease(EffectEventS2C e) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		State old = STATES.remove(e.casterId());
		if (old != null && old.vbo != null) {
			old.vbo.close();
		}
		double delay = rowsDelay();
		// the rows clock starts after the sword throw (plan A5): fault lines, eruption and the blade timeline are all shifted by the delay
		State st = create(e.casterId(), e.seed(), FxClock.now + delay, AnchorPlan.flatYawDeg(e.dx(), e.dz()), e.x(), e.y(), e.z(), w);
		st.swordT0 = FxClock.now;
		st.owner = !FxClient.forceRemote() && mc.player != null && mc.player.getId() == e.casterId();
		released++;
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		EffectTimeline t = FxTimelines.create(4, e.seed(), pos, new Vec3d(e.dx(), e.dy(), e.dz()), e.params(), e.casterId(), FxClient.forceRemote());
		double hx = st.hiltX();
		double hz = st.hiltZ();
		double hg = st.hiltGround;
		BladeRowLayout l = st.layout;
		double comp = l.completionTime();
		// plan A5. 0.0: the sword appears in front of the caster, point down, and hangs; from 0.62 it sinks slowly; at the ground contact the point
		// enters the ground like water: a light splash ring (three rings), motes thrown up, the hilt stays standing in the ground
		double hiltScale = FxTune.d("bankai.hiltScale", 1.8);
		double contact = swordContact(hiltScale);
		double hangY = hg + swordHang() + 0.5 * BankaiCurves.HILT_LENGTH * hiltScale;
		t.at(0.0, x -> {
			x.soundAt("item.trident.throw", new Vec3d(hx, hangY, hz), 1.4, 0.35);
			FxGlowBatch.sprite(GlowSprite.STAR4).at(hx, hangY, hz).lifeTicks(8).size(0.9, 0.2).sizeEase(FxMath.IQ).color("#F3E4FF").peak(0.9).owner(x).spawn();
		});
		t.at(BankaiCurves.SWORD_HANG_END, x -> x.soundAt("block.amethyst_block.chime", new Vec3d(hx, hangY, hz), 0.7, 0.4));
		t.at(contact, x -> {
			x.soundAt("item.trident.hit_ground", new Vec3d(hx, hg, hz), 0.9, 0.5);
			x.soundAt("entity.generic.splash", new Vec3d(hx, hg, hz), 1.5, 0.3);
			x.soundAt("block.bubble_column.whirlpool_inside", new Vec3d(hx, hg, hz), 1.6, 0.3);
			splash(x, hx, hg, hz);
		});
		t.at(contact + 0.5, x -> x.soundAt("block.bell.use", new Vec3d(hx, hg, hz), 0.5, 0.35));
		// 0.30 to 0.90: night falls
		t.at(0.30, x -> {
			// others: the night is graded by the distance to the NEAREST ROW POINT (standing in the corridor = full night), not to the caster (6.1)
			double gs = st.owner ? 1.0 : FxMath.screenStrength(nearestRowDistance(st));
			if (st.endAt >= 0 || STATES.get(e.casterId()) != st) {
				return; // the bankai already ended (or was replaced) before the night fell: no layer may be created for a dead state
			}
			st.grade = x.screen ? ScreenFx.grade(FxMath.hex(FxTune.s("bankai.night", NIGHT)), FxTune.d("bankai.nightStrength", 1.0), 0.6, gs).owner(e.casterId()) : null;
			st.vignette = x.screen ? ScreenFx.vignette(ScreenFx.Kind.DARK, FxTune.d("bankai.vignette", 0.45), 0.6, gs).owner(e.casterId()) : null;
			x.sound("ambient.cave", 0.8, 0.6);
			x.sound("block.respawn_anchor.charge", 0.6, 0.6);
		});
		t.at(0.60, x -> x.sound("entity.warden.heartbeat", 0.7, 0.5));
		// 0.90: fault lines (anticipation)
		t.at(0.90 + delay, x -> {
			x.sound("entity.warden.emerge", 1.4, 0.4);
			faultLines(x, st);
		});
		for (int k = 0; k < 6; k++) {
			double amp = 0.1 + 0.1 * k;
			t.at(0.90 + delay + 0.2 * k, x -> x.shake(amp, 0.3));
		}
		// 1.00: eruption starts
		t.at(1.00 + delay, x -> {
			x.sound("item.mace.smash_ground_heavy", 0.6, 0.7);
			x.sound("entity.ender_dragon.growl", 0.5, 0.25);
		});
		for (double ts = 1.2; ts < comp; ts += 0.2) {
			t.at(ts + delay, x -> x.shake(0.6, 0.25));
		}
		// rows complete
		t.at(comp + delay, x -> {
			x.shake(FxTune.d("bankai.completeShake", 1.2), 0.4);
			x.sound("block.beacon.ambient", 0.6, 0.35);
			x.sound("block.bell.resonate", 0.6, 0.4);
		});
		// hold: the vignette settles to the playable level
		t.at(comp + delay + 0.6, x -> {
			if (st.endAt >= 0 || STATES.get(e.casterId()) != st) {
				return;
			}
			if (st.vignette != null) {
				st.vignette.release(1.0);
			}
			st.vignette2 = x.vignette(ScreenFx.Kind.DARK, FxTune.d("bankai.vignetteHold", 0.30), 1.0);
		});
		t.lifetime(comp + delay + 1.5);
		// the light splash rings (glow) at the ground contact, ring k at contact + 0.3 k
		double splashScale = FxTune.d("bankai.splashScale", 1.0);
		for (int k = 0; k < 3; k++) {
			final int kk = k;
			t.at(contact + 0.3 * k, x -> FxGlowBatch.sprite(GlowSprite.RING_THIN).at(hx, hg + 0.06, hz).ground().size(0.4, BankaiCurves.splashRadius(kk, contact + 5, contact, splashScale) / 0.4375)
					.sizeEase(FxMath.OC).life(0.9).curve(0.02, 0.85).color("#F3E4FF").peak(0.5).owner(x).spawn());
		}
		FxTimelines.start(t);
		ReiatsuTest.LOGGER.info("[fx] rows: {} blades, {} ranks, completion {} s, settle {} s, length {}", l.n, l.ranks, String.format(java.util.Locale.ROOT, "%.2f", comp),
				String.format(java.util.Locale.ROOT, "%.2f", st.settleAt), String.format(java.util.Locale.ROOT, "%.1f", l.length()));
	}

	/** The splash where the point of the sword enters the ground: lilac motes thrown up and out, a few glow petals, a soft flare. */
	private static void splash(EffectTimeline t, double hx, double hg, double hz) {
		Random r = t.rng(21);
		for (int i = 0; i < 22; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double s = 1.2 + r.nextDouble() * 2.2;
			FxParticles.spec(FxParticles.Kind.FROST_MOTE).at(hx + Math.cos(a) * 0.12, hg + 0.1, hz + Math.sin(a) * 0.12).vel(Math.cos(a) * s, 1.5 + r.nextDouble() * 1.8, Math.sin(a) * s)
					.drag(0.9).gravity(0.5).life(18).size(0.22 + 0.2 * r.nextDouble()).seed(r.nextInt(4)).color("#F3E4FF").colorTo("#C9A8EE").fade(0.4).owner(t).spawn();
		}
		for (int i = 0; i < 6; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double s = 1.0 + r.nextDouble() * 1.6;
			FxGlowBatch.sprite(GlowSprite.PETAL_GLOW).at(hx, hg + 0.15, hz).vel(Math.cos(a) * s, 1.2 + r.nextDouble(), Math.sin(a) * s).drag(0.92).lifeTicks(14).size(0.4 + r.nextDouble() * 0.2)
					.color("#F9C8F6").peak(0.8).twinkle().rot(r.nextDouble() * 6.28, (r.nextDouble() - 0.5) * 3).owner(t).spawn();
		}
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(hx, hg + 0.2, hz).size(0.8, 2.2).sizeEase(FxMath.OC).life(0.4).curve(0.02, 0.8).color("#F3E4FF").peak(0.7).owner(t).spawn();
	}

	/** Horizontal distance of the camera to the nearest blade of the rows (0 inside the corridor). */
	static double nearestRowDistance(State st) {
		Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		double best = Double.MAX_VALUE;
		for (int i = 0; i < st.layout.n; i++) {
			best = Math.min(best, Math.hypot(st.bx[i] - cam.x, st.bz[i] - cam.z));
		}
		// inside the corridor (between the two rows, along them) counts as 0
		double yaw = Math.toRadians(st.yawDeg);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double along = (cam.x - st.fx) * fx + (cam.z - st.fz) * fz;
		double across = Math.abs((cam.x - st.fx) * -fz + (cam.z - st.fz) * fx);
		double minAlong = Double.MAX_VALUE;
		double maxAlong = -Double.MAX_VALUE;
		for (int i = 0; i < st.layout.n; i++) {
			minAlong = Math.min(minAlong, st.layout.along[i]);
			maxAlong = Math.max(maxAlong, st.layout.along[i]);
		}
		if (along >= minAlong && along <= maxAlong && across <= st.layout.corridorHalfWidth()) {
			return 0;
		}
		return best;
	}

	private static State create(int owner, long seed, double epoch, double yawDeg, double x, double y, double z, ClientWorld w) {
		BladeRowLayout layout = makeLayout(seed);
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
				FxGlowBatch.sprite(GlowSprite.CRACK).at(st.bx[i], st.ground[i] + 0.05, st.bz[i]).ground().rot(i * 1.7, 0).size(Math.max(1.7, st.bladeWidth * 1.6)).delay(delay).life(Math.max(0.2, up)).curve(0.05, 0.5)
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
				st.dispose(0.3);
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
				if (t > 5.0 && st.grade != null && !st.gradeLocked) {
					double k = FxMath.smoothstep(5.0, 8.0, t);
					float[] a = FxMath.hex(NIGHT);
					float[] b = FxMath.hex(NIGHT_PLAY);
					st.grade.rgb[0] = (float) FxMath.lerp(a[0], b[0], k);
					st.grade.rgb[1] = (float) FxMath.lerp(a[1], b[1], k);
					st.grade.rgb[2] = (float) FxMath.lerp(a[2], b[2], k);
				}
				// hold: petal haze in the corridor (10 per second), beacon hum every 3 s, small ripple at the hilt every 4 s
				if (t > l.completionTime() && FxClock.dt > 0) {
					if (FxClock.now >= st.nextHeartbeat && st.owner) {
						st.nextHeartbeat = FxClock.now + 1.5;
						FxSound.play(null, "entity.warden.heartbeat", st.fx, st.fy + 1, st.fz, 0.7, 0.15);
					}
					if (FxClock.now >= st.nextHaze && !st.phaseBusy()) {
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
		FxGlowBatch.sprite(GlowSprite.STAR4).at(tip[0], tip[1], tip[2]).size(1.2 * st.layout.scale[i] * (0.4 + 0.6 * st.bladeScale) * (d > 60 ? 1.6 : 1.0)).lifeTicks(5).curve(0.01, 0.8).color("#FFFFFF")
				.peak(0.8).rot(i * 0.9, 0).spawn();
	}

	private static void haze(State st, Vec3d cam, Random r) {
		BladeRowLayout l = st.layout;
		double minAlong = Double.MAX_VALUE;
		double maxAlong = -Double.MAX_VALUE;
		for (int i = 0; i < l.n; i++) {
			minAlong = Math.min(minAlong, l.along[i]);
			maxAlong = Math.max(maxAlong, l.along[i]);
		}
		// inside the lane between the rows, over the whole length of the rows
		double along = minAlong - 1.0 + r.nextDouble() * (maxAlong - minAlong + 2.0);
		double across = (r.nextDouble() - 0.5) * 2.0 * l.corridorHalfWidth() * 0.85;
		double yaw = Math.toRadians(st.yawDeg);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -fz;
		double rz = fx;
		double px = st.fx + fx * along + rx * across;
		double pz = st.fz + fz * along + rz * across;
		double py = st.hiltGround + 0.5 + r.nextDouble() * 3.0;
		FxGlowBatch.sprite(GlowSprite.PETAL_GLOW).at(px, py, pz).vel((r.nextDouble() - 0.5) * 0.4, 0.15 + r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.4).lifeTicks(60).size(0.5)
				.color("#F9C8F6").peak(0.25).rot(r.nextDouble() * 6.28, (r.nextDouble() - 0.5) * 2).twinkle().spawn();
	}
}
