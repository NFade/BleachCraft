package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.UryuArrowLook;
import dev.minebleach.reiatsutest.core.fx.UryuBowForm;
import dev.minebleach.reiatsutest.core.fx.UryuVollstTiming;
import dev.minebleach.reiatsutest.core.fx.UryuWingMath;
import dev.minebleach.reiatsutest.core.swarm.V3;
import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import dev.minebleach.reiatsutest.core.uryu.WingBarrage;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * The wings and everything around them (URYU_VFX_STORYBOARD 2.10, 2.11, 2.12, 2.16). Called by {@link UryuFx}.
 *
 * <ul>
 * <li><b>The release</b> (effect 71, {@link #release}): the one shot timeline of table 2.10 (gather, collapse, burst, the ground sign, the screen
 * grade), numbers in {@link UryuVollstTiming}. The wings themselves are NOT part of the timeline: they unfold in {@link UryuWingRenderer} from the
 * clock this class keeps per player ({@link #releaseTime}), so a player who comes into view later sees them settled.</li>
 * <li><b>The state</b> ({@link #frame}, {@link #emitGlow}): the stardust that runs along the plates, the stars at the tips, the chest emblem and
 * the faint body light. Never for the camera owner in first person (the guard: no wing in the camera, see {@link UryuWingRenderer}).</li>
 * <li><b>The end</b> ({@link #bankaiEnded}): the BANKAI state left (event 44, the seal, or the attachment itself, whichever comes first): the wings
 * break into flake flechettes and stardust, the bands fade, the sign flashes once. Idempotent.</li>
 * <li><b>The barrage</b> (effect 75, U-FX 8 owns the look): the glow streak wings of the placeholder are drawn only for a caster that has no mesh
 * wings; the mesh wings open ({@link UryuWingRenderer#spread}).</li>
 * </ul>
 */
final class UryuWingFx {
	private static final class Wing {
		int caster;
		double born;
		double life;
		double x;
		double y;
		double z;
		boolean ended;
	}

	/** A lock-on line of a tip fire: from the tip along the arrow, fading in 0.1 s. */
	private record Lock(double born, double x, double y, double z, double ux, double uy, double uz) {
	}

	private static final List<Lock> LOCKS = new ArrayList<>();
	private static final Map<Integer, Double> LAST_FIRE_SOUND = new HashMap<>();

	private static final List<Wing> WINGS = new ArrayList<>();
	/** FX time of the release event of a caster (t = 0 of the table), dropped when his bankai ends. */
	private static final Map<Integer, Double> EVENT_AT = new HashMap<>();
	private static final Map<Integer, Double> ENDED_AT = new HashMap<>();
	private static final Random RNG = new Random(0x71A5);
	private static final double[] P = new double[3];
	private static final double[] PL = new double[4];

	/** Statistics for the harness. */
	static int releases;
	static int ends;
	static int lastGlowSprites;
	static int specks;
	static int tipStars;
	static int tipFires;
	static int barrageDashes;
	static int crossSprites;

	private UryuWingFx() {
	}

	static void init() {
		UryuQuincyFeature.addBankaiPass(UryuWingRenderer::drawPass);
	}

	static void clearAll() {
		EVENT_AT.clear();
		ENDED_AT.clear();
		WINGS.clear();
		LOCKS.clear();
		LAST_FIRE_SOUND.clear();
		UryuWingRenderer.clear();
	}

	// ------------------------------------------------------------------------------------------ the clock of the wings

	/**
	 * Seconds since the release event of this player's wings (the clock of table 2.10), 1000 once settled, negative while the attachment waits for its
	 * event. {@code s} is the feature's state of the player.
	 */
	static double releaseTime(int id, UryuQuincyFeature.State s) {
		Double ev = EVENT_AT.get(id);
		return UryuVollstTiming.releaseTime(ev == null ? -1.0 : FxClock.now - ev, s.bankaiAge());
	}

	/** Harness: the table time of a player's wings now. */
	static double releaseTime(int id) {
		return releaseTime(id, UryuQuincyFeature.state(id));
	}

	// ------------------------------------------------------------------------------------------ the release (effect 71)

	/** Body frame of the caster: feet and the horizontal forward unit vector (live body yaw when the entity is loaded, else the event direction). */
	private record Frame(Vec3d feet, double fx, double fz) {
		/** A point {@code left} to the left (the left axis is (fz, -fx)), {@code fwd} forward and {@code up} above the feet. */
		Vec3d at(double left, double fwd, double up) {
			return new Vec3d(feet.x + fz * left + fx * fwd, feet.y + up, feet.z - fx * left + fz * fwd);
		}
	}

	private static Frame frame(EffectTimeline t) {
		Entity c = t.caster();
		if (c instanceof LivingEntity le) {
			double yaw = Math.toRadians(le.bodyYaw);
			return new Frame(c.getLerpedPos(0f), -Math.sin(yaw), Math.cos(yaw));
		}
		double[] f = RukiaKit.flat(t.dir);
		return new Frame(t.pos, f[0], f[1]);
	}

	private static int count(int n) {
		return FxConfig.fxTier == FxConfig.TierSetting.LOW ? Math.max(1, n / 2) : n;
	}

	/** Effect 71: the Quincy: Vollstaendig release. */
	static void release(EffectEventS2C e) {
		releases++;
		EVENT_AT.put(e.casterId(), FxClock.now);
		ENDED_AT.remove(e.casterId());
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		EffectTimeline t = FxTimelines.create(e.effectId(), e.seed(), pos, dir, e.params(), e.casterId(), FxClient.forceRemote());
		boolean rm = FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
		ScreenFx.Layer[] grade = new ScreenFx.Layer[1];
		ScreenFx.Layer[] vignette = new ScreenFx.Layer[1];
		lastGlowSprites = 0;

		for (UryuVollstTiming.Cue cue : UryuVollstTiming.CUES) {
			t.at(cue.t(), x -> x.sound(cue.sound(), cue.pitch(), cue.volume()));
		}
		// 0.00 gather: the sky darkens, the lens aura forms, dashes and motes stream in, the world fills with stars
		t.at(UryuVollstTiming.T_GATHER, x -> {
			grade[0] = x.grade(UryuPalette.GRADE_BANKAI, UryuVollstTiming.GRADE_S, UryuVollstTiming.GRADE_RAMP);
			vignette[0] = x.vignette(ScreenFx.Kind.DARK, rm ? UryuVollstTiming.VIGNETTE_RM : UryuVollstTiming.VIGNETTE, UryuVollstTiming.VIGNETTE_RAMP);
			gather(x, frame(x));
		});
		// 0.50 collapse: everything is sucked into the chest in 0.15 s, then black silence (the other sounds of the caster duck to nothing)
		t.duck(UryuVollstTiming.T_COLLAPSE, UryuVollstTiming.T_BURST, 0.0);
		t.at(UryuVollstTiming.T_COLLAPSE, x -> collapse(x, frame(x)));
		// 0.65 burst
		t.at(UryuVollstTiming.T_BURST, x -> {
			burst(x, frame(x));
			x.flash(rm ? UryuVollstTiming.FLASH_RM : UryuVollstTiming.FLASH, UryuPalette.Q1, rm);
			x.shake(UryuVollstTiming.SHAKE_AMPLITUDE, UryuVollstTiming.SHAKE_TIME);
			if (x.localCaster) {
				ScreenFx.shunpoRush(0.0, UryuVollstTiming.FOV_KICK, UryuVollstTiming.FOV_KICK_TIME);
			}
			if (vignette[0] != null) {
				vignette[0].release(UryuVollstTiming.VIGNETTE_FADE);
			}
		});
		// 0.85 bands slide on: the emblem pops (the stars at the band ends come with the sweep end)
		t.at(UryuVollstTiming.T_BANDS, x -> emblemPop(x, frame(x)));
		t.at(UryuVollstTiming.T_BANDS + UryuVollstTiming.BANDS_SWEEP, x -> bandStars(x, frame(x)));
		// 0.90 the ground sign
		t.at(UryuVollstTiming.T_SIGN, x -> sign(x, frame(x)));
		// 1.00 the grade eases back
		t.at(UryuVollstTiming.T_GRADE_OUT, x -> {
			if (grade[0] != null) {
				grade[0].release(UryuVollstTiming.GRADE_OUT_TIME);
			}
		});
		// safety: layers that were not let go (the effect was cut) are released at the end of the lock
		t.at(UryuVollstTiming.LOCK, x -> {
			for (ScreenFx.Layer l : new ScreenFx.Layer[] {grade[0], vignette[0]}) {
				if (l != null && l.held()) {
					l.release(UryuVollstTiming.VIGNETTE_FADE);
				}
			}
		});
		t.lifetime(UryuVollstTiming.LIFETIME);
		FxTimelines.start(t);
	}

	private static Vec3d chest(Frame f) {
		return f.at(0.0, 0.10, 1.15);
	}

	private static void unit(Random r, double[] out) {
		double a = r.nextDouble() * Math.PI * 2;
		double el = Math.asin(r.nextDouble() * 2 - 1);
		out[0] = Math.cos(el) * Math.cos(a);
		out[1] = Math.sin(el);
		out[2] = Math.cos(el) * Math.sin(a);
	}

	/** 0.00: the lens aura, 90 dashes from radius 10 at 14 b/s, 60 reishi motes, 150 stardust specks in a ball of radius 6. */
	private static void gather(EffectTimeline t, Frame f) {
		Random r = t.rng(71);
		Vec3d c = chest(f);
		if (!firstPerson(t)) {
			// (the lens aura of 3.2 m around the body would fill the view of its own owner in first person)
			FxGlowBatch.sprite(GlowSprite.LENS).at(c.x, c.y, c.z).size(UryuVollstTiming.LENS_WIDTH, 0.1).sizeEase(FxMath.IQ).life(UryuVollstTiming.T_BURST).curve(0.2, 0.25)
					.color(UryuPalette.Q1).peak(UryuVollstTiming.LENS_PEAK).owner(t).spawn();
			lastGlowSprites++;
		}
		double[] d = new double[3];
		int n = count(UryuVollstTiming.GATHER_DASHES);
		for (int i = 0; i < n; i++) {
			unit(r, d);
			double s = UryuVollstTiming.GATHER_SPEED;
			FxGlowBatch.sprite(GlowSprite.DASH).at(c.x + d[0] * UryuVollstTiming.GATHER_RADIUS, c.y + d[1] * UryuVollstTiming.GATHER_RADIUS, c.z + d[2] * UryuVollstTiming.GATHER_RADIUS)
					.vel(-d[0] * s, -d[1] * s, -d[2] * s).streak().streakTime(0.07).size(0.8 + 0.4 * r.nextDouble()).life(UryuVollstTiming.GATHER_LIFE).delay(0.1 * r.nextDouble())
					.curve(0.05, 0.35).color(i % 4 == 0 ? UryuPalette.Q1 : UryuPalette.Q2).peak(0.85).owner(t).spawn();
			lastGlowSprites++;
		}
		for (int i = 0; i < UryuVollstTiming.GATHER_MOTES; i++) {
			unit(r, d);
			double rad = 2.0 + 4.0 * r.nextDouble();
			double px = c.x + d[0] * rad;
			double py = c.y + d[1] * rad;
			double pz = c.z + d[2] * rad;
			double k = 1.0 / 0.55;
			FxParticles.spec(FxParticles.Kind.REISHI_MOTE).at(px, py, pz).vel((c.x - px) * k, (c.y - py) * k, (c.z - pz) * k).drag(1.0).color(UryuPalette.Q2).size(0.2, 0.08).life(11)
					.seed(r.nextInt(1 << 16)).owner(t).alpha(0.9).spawn();
		}
		int sp = FxConfig.fxTier == FxConfig.TierSetting.LOW ? UryuVollstTiming.GATHER_SPECKS_LOW : UryuVollstTiming.GATHER_SPECKS;
		for (int i = 0; i < sp; i++) {
			unit(r, d);
			double rad = UryuVollstTiming.SPECK_BALL_RADIUS * Math.cbrt(r.nextDouble());
			FxGlowBatch.sprite(r.nextBoolean() ? GlowSprite.SPECK : GlowSprite.GLITTER).at(c.x + d[0] * rad, c.y + d[1] * rad, c.z + d[2] * rad).size(0.12 + 0.16 * r.nextDouble())
					.life(0.3 + 0.3 * r.nextDouble()).delay(0.45 * r.nextDouble()).curve(0.05, 0.5).twinkle().color(r.nextDouble() < 0.08 ? UryuPalette.QL : UryuPalette.Q1).peak(0.9)
					.owner(t).spawn();
			lastGlowSprites++;
		}
	}

	/** 0.50: 40 dashes from radius 3 into the chest in 0.15 s. */
	private static void collapse(EffectTimeline t, Frame f) {
		Random r = t.rng(72);
		Vec3d c = chest(f);
		double[] d = new double[3];
		int n = count(UryuVollstTiming.COLLAPSE_DASHES);
		for (int i = 0; i < n; i++) {
			unit(r, d);
			double rad = 2.0 + 1.0 * r.nextDouble();
			double s = rad / UryuVollstTiming.COLLAPSE_TIME;
			FxGlowBatch.sprite(GlowSprite.DASH).at(c.x + d[0] * rad, c.y + d[1] * rad, c.z + d[2] * rad).vel(-d[0] * s, -d[1] * s, -d[2] * s).streak().streakTime(0.05)
					.size(0.6 + 0.3 * r.nextDouble()).life(UryuVollstTiming.COLLAPSE_TIME).curve(0.01, 0.3).color(UryuPalette.Q1).peak(0.9).owner(t).spawn();
			lastGlowSprites++;
		}
	}

	/** 0.65: the ground ring, the burst rays behind the back, 24 flake flechettes, 120 radial dashes. */
	private static void burst(EffectTimeline t, Frame f) {
		Random r = t.rng(73);
		Vec3d c = chest(f);
		double gy = f.feet().y + 0.06;
		// RING_SOFT draws its ring at 0.375 of its width, RING_THIN at 0.4375
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(f.feet().x, gy, f.feet().z).ground().size(0.6, UryuVollstTiming.RING_RADIUS / 0.375).sizeEase(FxMath.OC).life(UryuVollstTiming.RING_TIME)
				.curve(0.02, 0.85).color(UryuPalette.Q2).peak(0.9).owner(t).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(f.feet().x, gy + 0.01, f.feet().z).ground().size(0.6, UryuVollstTiming.RING_RADIUS / 0.4375).sizeEase(FxMath.OC)
				.life(UryuVollstTiming.RING_TIME).delay(0.06).curve(0.02, 0.85).color(UryuPalette.Q1).peak(0.6).owner(t).spawn();
		Vec3d back = f.at(0.0, -0.45, 1.35);
		FxGlowBatch.sprite(GlowSprite.BURST_RAYS).at(back.x, back.y, back.z).plane(f.fx(), 0.0, f.fz()).size(UryuVollstTiming.RAYS_SIZE).life(UryuVollstTiming.RAYS_LIFE).curve(0.02, 0.7)
				.color(UryuPalette.Q1).peak(0.9).owner(t).spawn();
		lastGlowSprites += 3;
		double[] d = new double[3];
		for (int i = 0; i < UryuVollstTiming.BURST_FLAKES; i++) {
			unit(r, d);
			double s = UryuVollstTiming.BURST_FLAKE_SPEED * (0.7 + 0.3 * r.nextDouble());
			FxParticles.spec(FxParticles.Kind.FLECHETTE).at(c.x, c.y, c.z).vel(d[0] * s, d[1] * s, d[2] * s).drag(UryuVollstTiming.BURST_FLAKE_DRAG).color(UryuPalette.Q2)
					.size(0.35 + 0.15 * r.nextDouble()).life(UryuVollstTiming.BURST_FLAKE_LIFE_TICKS).seed(i).spin((r.nextDouble() - 0.5) * 0.4).owner(t).spawn();
		}
		int n = count(UryuVollstTiming.BURST_DASHES);
		for (int i = 0; i < n; i++) {
			unit(r, d);
			double s = UryuVollstTiming.BURST_DASH_SPEED * (0.7 + 0.3 * r.nextDouble());
			FxGlowBatch.sprite(GlowSprite.DASH).at(c.x, c.y, c.z).vel(d[0] * s, d[1] * s, d[2] * s).drag(0.97).streak().streakTime(0.07).size(0.6 + 0.4 * r.nextDouble()).lifeTicks(14)
					.curve(0.01, 0.6).color(i % 4 == 0 ? UryuPalette.Q1 : UryuPalette.Q2).peak(0.9).owner(t).spawn();
			lastGlowSprites++;
		}
	}

	/** 0.85: the chest emblem pops: a white flare of 0.15 s (the state emblem in Q3 takes over). */
	private static void emblemPop(EffectTimeline t, Frame f) {
		if (firstPerson(t)) {
			return; // the emblem is on the chest, below the first person camera of its owner
		}
		Vec3d e = f.at(0.0, 0.17, 1.22);
		FxGlowBatch.sprite(GlowSprite.CHEVRONS).at(e.x, e.y, e.z).plane(f.fx(), 0.0, f.fz()).size(UryuVollstTiming.EMBLEM_POP_SIZE).life(UryuVollstTiming.EMBLEM_POP_LIFE + 0.1)
				.curve(0.02, 0.6).color(UryuPalette.Q1).peak(1.0).owner(t).spawn();
		lastGlowSprites++;
	}

	/** 1.20, the end of the band sweep: a star4 flash at each band end (both wrists, both calves, both soles). */
	private static void bandStars(EffectTimeline t, Frame f) {
		Vec3d[] pts = {f.at(0.36, 0.05, 0.72), f.at(-0.36, 0.05, 0.72), f.at(0.117, 0.0, 0.40), f.at(-0.117, 0.0, 0.40), f.at(0.117, 0.10, 0.06), f.at(-0.117, 0.10, 0.06)};
		for (int i = 0; i < UryuVollstTiming.BAND_STARS; i++) {
			FxGlowBatch.sprite(GlowSprite.STAR4).at(pts[i].x, pts[i].y, pts[i].z).size(UryuVollstTiming.BAND_STAR_SIZE * 2.2, UryuVollstTiming.BAND_STAR_SIZE).life(0.3).curve(0.01, 0.6)
					.color(UryuPalette.Q1).peak(0.95).rot(0.3 * i, 1.5).owner(t).spawn();
			lastGlowSprites++;
		}
	}

	/** 0.90: the ZK on the ground (size 8, r 4, rotating 0.15 rev/s) and the pentagon of radius 4.4 inside it. */
	private static void sign(EffectTimeline t, Frame f) {
		double gy = f.feet().y + 0.06;
		FxGlowBatch.sprite(GlowSprite.ZEICHEN_STAR).at(f.feet().x, gy, f.feet().z).ground().size(UryuVollstTiming.SIGN_SIZE).life(UryuVollstTiming.SIGN_LIFE).curve(0.05, 0.75)
				.rot(0.0, Math.PI * 2 * UryuVollstTiming.SIGN_SPIN).color(UryuPalette.Q1).peak(UryuVollstTiming.SIGN_PEAK).owner(t).spawn();
		// PENT_LINE draws its pentagon at 28/64 of its width
		FxGlowBatch.sprite(GlowSprite.PENT_LINE).at(f.feet().x, gy + 0.01, f.feet().z).ground().size(UryuVollstTiming.PENTAGON_RADIUS / 0.4375).life(UryuVollstTiming.SIGN_LIFE).curve(0.05, 0.7)
				.color(UryuPalette.Q2).peak(0.5).owner(t).spawn();
		lastGlowSprites += 2;
	}

	// ------------------------------------------------------------------------------------------ the state: stardust, tip stars

	/** True for the local caster seen from his own first person camera (nothing that surrounds the body may fill his view). */
	private static boolean firstPerson(EffectTimeline t) {
		MinecraftClient mc = MinecraftClient.getInstance();
		return t.localCaster && mc.options.getPerspective().isFirstPerson();
	}

	private static boolean fpOwner(AbstractClientPlayerEntity pl, MinecraftClient mc) {
		Camera cam = mc.gameRenderer.getCamera();
		return cam != null && !cam.isThirdPerson() && cam.getFocusedEntity() == pl;
	}

	/** Per frame (WorldRenderEvents.START via {@link UryuFx}): the pose state of every bankai player in view and the stardust of the wings. */
	static void frame(double now) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			if (!EVENT_AT.isEmpty() || !ENDED_AT.isEmpty()) {
				clearAll();
			}
			return;
		}
		UryuWingRenderer.beginFrame();
		if (FxClock.frame % 60 == 0) {
			UryuWingRenderer.prune(mc.world);
		}
		float td = mc.getRenderTickCounter().getTickDelta(false);
		Vec3d cp = mc.gameRenderer.getCamera().getPos();
		double max2 = UryuVollstTiming.VIEW_DISTANCE * UryuVollstTiming.VIEW_DISTANCE;
		double tier = Math.max(0.0, Math.min(1.0, FxConfig.uryuMaxStardust / 600.0));
		for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
			UryuQuincyFeature.State s = UryuQuincyFeature.state(pl.getId());
			if (!s.bankai() || pl.isInvisible() || pl.isSpectator() || cp.squaredDistanceTo(pl.getPos()) > max2) {
				continue;
			}
			UryuWingRenderer.WingState st = UryuWingRenderer.update(pl, now, td);
			double t = releaseTime(pl.getId(), s);
			st.wingsShown = t >= UryuVollstTiming.T_UNFOLD; // by the clock, so the owner in first person (never drawn) breaks his wings at the end as well
			if (t < UryuVollstTiming.T_UNFOLD || fpOwner(pl, mc)) {
				continue;
			}
			double dt = Double.isNaN(st.spawnLast) ? 0.0 : Math.max(0.0, Math.min(0.1, now - st.spawnLast));
			st.spawnLast = now;
			if (dt <= 0.0) {
				continue;
			}
			double rate = UryuVollstTiming.STATE_SPECKS_PER_WING * 2.0;
			boolean unfolding = t < UryuVollstTiming.T_UNFOLD + UryuVollstTiming.UNFOLD_TIME;
			if (unfolding) {
				rate += UryuVollstTiming.UNFOLD_SPECKS / UryuVollstTiming.UNFOLD_TIME;
			}
			st.specAcc += rate * tier * dt;
			int n = (int) st.specAcc;
			st.specAcc -= n;
			for (int i = 0; i < n; i++) {
				if (UryuWingRenderer.surfacePoint(st, RNG, t, 5, P, PL)) {
					speck(P, unfolding);
				}
			}
			st.tipAcc += UryuVollstTiming.STATE_TIP_STARS_PER_WING * 2.0 * dt;
			if (st.tipAcc >= 1.0 && !unfolding) {
				st.tipAcc -= 1.0;
				tipStar(st);
			} else if (unfolding) {
				st.tipAcc = 0.0;
			}
		}
	}

	private static void speck(double[] p, boolean unfolding) {
		double size = unfolding ? UryuVollstTiming.UNFOLD_SPECK_SIZE : UryuVollstTiming.STATE_SPECK_SIZE;
		int life = unfolding ? UryuVollstTiming.UNFOLD_SPECK_LIFE_TICKS : UryuVollstTiming.STATE_SPECK_LIFE_TICKS;
		FxGlowBatch.sprite(GlowSprite.SPECK).at(p[0], p[1], p[2]).vel(RNG.nextGaussian() * 0.12, 0.15 + 0.2 * RNG.nextDouble(), RNG.nextGaussian() * 0.12).size(size * (0.7 + 0.6 * RNG.nextDouble()))
				.lifeTicks(life).curve(0.02, 0.6).twinkle().color(RNG.nextDouble() < 0.08 ? UryuPalette.QL : UryuPalette.Q1).peak(0.9).spawn();
		specks++;
	}

	/** One star4 at a tip of the wings (a random one of the four). */
	private static void tipStar(UryuWingRenderer.WingState st) {
		double[] o = new double[3];
		int slot = RNG.nextInt(4);
		UryuWingMath.tip(slot, st.rig(), o);
		UryuWingRenderer.toWorld(st, -o[0], o[1], -o[2], P);
		FxGlowBatch.sprite(GlowSprite.STAR4).at(P[0], P[1], P[2]).size(0.4).life(0.5).curve(0.04, 0.6).twinkle().color(UryuPalette.Q1).peak(0.9).rot(RNG.nextDouble() * 3, 0.6).spawn();
		tipStars++;
	}

	// ------------------------------------------------------------------------------------------ the end (2.16)

	/**
	 * The bankai state of a player ended (the end event 44, a seal, or the attachment, whichever comes first; later calls within 3 s are no-ops): the
	 * wings break into flake flechettes, 30 specks and the ground sign flashes once. The bands fade in the renderer ({@link UryuVollstTiming#BANDS_FADE_OUT});
	 * the screen layers of the owner are released by {@link BankaiEnd}.
	 */
	static void bankaiEnded(int id) {
		double now = FxClock.now;
		Double last = ENDED_AT.get(id);
		if (last != null && now - last < 3.0) {
			return;
		}
		ENDED_AT.put(id, now);
		boolean had = EVENT_AT.remove(id) != null;
		WINGS.removeIf(w -> w.caster == id);
		UryuWingRenderer.WingState st = UryuWingRenderer.stateOf(id);
		if (st != null && st.wingsShown) {
			ends++;
			shatter(id, st);
		} else if (had) {
			ends++;
		}
		// (the pose state stays for the 0.25 s fold of the wings; prune() forgets it)
	}

	private static void shatter(int id, UryuWingRenderer.WingState st) {
		Random r = new Random(id * 7919L + Math.round(FxClock.now * 1000));
		double[] root = new double[3];
		UryuWingRenderer.toWorld(st, 0.0, UryuWingMath.ROOT_Y, -UryuWingMath.ROOT_BACK, root);
		double tier = Math.max(0.25, Math.min(1.0, FxConfig.uryuMaxFlechettes / 160.0));
		int flakes = (int) Math.ceil(6 * UryuVollstTiming.END_FLAKES_PER_PLATE * tier);
		for (int i = 0; i < flakes; i++) {
			if (!UryuWingRenderer.surfacePoint(st, r, 1000.0, 5, P, PL)) {
				continue;
			}
			double dx = P[0] - root[0];
			double dy = P[1] - root[1];
			double dz = P[2] - root[2];
			double l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
			double s = UryuVollstTiming.END_FLAKE_SPEED * (0.6 + 0.6 * r.nextDouble());
			FxParticles.spec(FxParticles.Kind.FLECHETTE).at(P[0], P[1], P[2]).vel(dx / l * s + r.nextGaussian() * 0.4, dy / l * s + r.nextGaussian() * 0.4, dz / l * s + r.nextGaussian() * 0.4)
					.gravity(UryuVollstTiming.END_FLAKE_GRAVITY).drag(0.96).color(UryuPalette.Q2).size(0.35 + 0.15 * r.nextDouble()).life(UryuVollstTiming.END_FLAKE_LIFE_TICKS).seed(i)
					.spin((r.nextDouble() - 0.5) * 0.5).spawn();
		}
		for (int i = 0; i < UryuVollstTiming.END_SPECKS; i++) {
			if (UryuWingRenderer.surfacePoint(st, r, 1000.0, 5, P, PL)) {
				FxGlowBatch.sprite(GlowSprite.SPECK).at(P[0], P[1], P[2]).vel(r.nextGaussian() * 0.8, r.nextGaussian() * 0.8, r.nextGaussian() * 0.8).size(0.14 + 0.1 * r.nextDouble()).lifeTicks(16)
						.curve(0.02, 0.6).twinkle().color(UryuPalette.Q1).peak(0.9).spawn();
			}
		}
		FxGlowBatch.sprite(GlowSprite.ZEICHEN_STAR).at(st.fx, st.fy + 0.06, st.fz).ground().size(UryuVollstTiming.END_SIGN_RADIUS * 2).life(UryuVollstTiming.END_SIGN_LIFE).curve(0.03, 0.7)
				.color(UryuPalette.Q1).peak(UryuVollstTiming.END_SIGN_PEAK).spawn();
		for (UryuVollstTiming.Cue c : UryuVollstTiming.END_CUES) {
			FxSound.play(null, c.sound(), st.fx, st.fy + 1.2, st.fz, c.pitch(), c.volume());
		}
	}

	/** The bankai of a player ended (any reason: the cleanup event 44, the seal 10 / 11): his wings go with it. */
	static void clearWings(int casterId) {
		WINGS.removeIf(w -> w.caster == casterId);
		bankaiEnded(casterId);
	}

	// ------------------------------------------------------------------------------------------ the barrage (effect 75, U-FX 8 owns the look)

	/** Effect 75: the barrage starts, the wings open for {@code params[4]} seconds. */
	static void barrage(EffectEventS2C e) {
		float[] pr = e.params();
		double seconds = pr.length > 4 ? pr[4] : UryuBalance.WING_TICKS / 20.0;
		WINGS.removeIf(x -> x.caster == e.casterId());
		Wing wing = new Wing();
		wing.caster = e.casterId();
		wing.born = FxClock.now;
		wing.life = seconds;
		Entity caster = MinecraftClient.getInstance().world == null ? null : MinecraftClient.getInstance().world.getEntityById(e.casterId());
		Vec3d at = caster != null ? caster.getPos() : new Vec3d(e.x(), e.y(), e.z());
		wing.x = at.x;
		wing.y = at.y;
		wing.z = at.z;
		WINGS.add(wing);
		UryuWingRenderer.spread(e.casterId(), seconds);
		boolean rm = FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
		FxSound.play(null, "entity.ender_dragon.flap", at.x, at.y + 1.5, at.z, 1.7, 0.7);
		FxSound.play(null, "block.amethyst_block.resonate", at.x, at.y + 1.5, at.z, 1.6, 0.6);
		// 24 dashes fly radially out of the four tips as the wings flare open
		Vec3d[] tips = tipsOf(e.casterId(), at, caster);
		if (tips != null) {
			int per = count(6);
			for (int s = 0; s < tips.length; s++) {
				Vec3d tp = tips[s];
				double rx = tp.x - at.x;
				double rz = tp.z - at.z;
				double rl = Math.max(1.0E-3, Math.sqrt(rx * rx + rz * rz));
				for (int i = 0; i < per; i++) {
					double a = RNG.nextDouble() * 0.9 - 0.45;
					double sp = (rm ? 3.0 : 6.0) * (0.7 + 0.6 * RNG.nextDouble());
					double dx = (rx / rl) * Math.cos(a) - (rz / rl) * Math.sin(a);
					double dz = (rz / rl) * Math.cos(a) + (rx / rl) * Math.sin(a);
					double dy = (RNG.nextDouble() - 0.5) * 0.7;
					barrageDashes++;
					FxGlowBatch.sprite(GlowSprite.DASH).at(tp.x, tp.y, tp.z).vel(dx * sp, dy * sp, dz * sp).streak().streakTime(0.05).size(0.4).life(0.25).curve(0.005, 0.6)
							.color((s + i) % 2 == 0 ? UryuPalette.Q1 : UryuPalette.Q2).peak(0.9).spawn();
				}
			}
		}
		if (!rm && MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.getId() == e.casterId()) {
			ScreenFx.shake(0.3, 0.2, 1.0);
		}
	}

	/** The four tips of a caster: the visible mesh wing tips, else the rule positions of the placeholder (null without any body). */
	private static Vec3d[] tipsOf(int id, Vec3d feet, Entity caster) {
		Vec3d[] tips = UryuWingRenderer.wingTips(id, 1.0f);
		if (tips != null || !(caster instanceof LivingEntity le)) {
			return tips;
		}
		tips = new Vec3d[UryuBalance.WING_SLOTS];
		for (int s = 0; s < tips.length; s++) {
			V3 v = WingBarrage.tip(s, le.bodyYaw, new V3(feet.x, feet.y, feet.z));
			tips[s] = new Vec3d(v.x(), v.y(), v.z());
		}
		return tips;
	}

	/** The nearest player in the BANKAI state within 4 blocks of a first arrow position: the one whose wing fired. */
	private static net.minecraft.entity.player.PlayerEntity casterNear(double x, double y, double z) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return null;
		}
		net.minecraft.entity.player.PlayerEntity best = null;
		double bd = 16.0;
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
			if (!UryuQuincyFeature.state(p.getId()).bankai()) {
				continue;
			}
			double d = p.squaredDistanceTo(x, y, z);
			if (d <= bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	/**
	 * The tip fire (2.12) at the first sighting of a wing arrow at {@code (x, y, z)} flying along {@code u} (unit): a star and a ring at the tip, the wings
	 * flare, a thin line to the target for 0.1 s and the volley sounds (at most one set per caster every 0.2 s). Reduce Motion: no spin, no lock-on line.
	 */
	static void tipFire(double x, double y, double z, Vec3d u) {
		tipFires++;
		boolean rm = FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
		net.minecraft.entity.player.PlayerEntity caster = casterNear(x, y, z);
		if (caster != null) {
			UryuWingRenderer.volley(caster.getId());
		}
		FxGlowBatch.sprite(GlowSprite.STAR4).at(x, y, z).size(UryuArrowLook.WING_STAR * 0.5, UryuArrowLook.WING_STAR).sizeEase(FxMath.OE).life(UryuArrowLook.MUZZLE_STAR_LIFE)
				.curve(0.005, 0.7).color(UryuPalette.Q0).peak(1.0).rot(RNG.nextDouble() * 3.0, rm ? 0.0 : 1.5).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(x, y, z).plane(u.x, u.y, u.z).size(UryuArrowLook.WING_RING_FROM, UryuArrowLook.WING_RING_TO).sizeEase(FxMath.OC)
				.life(UryuArrowLook.WING_RING_LIFE).curve(0.01, 0.7).color(UryuPalette.Q1).peak(0.8).spawn();
		if (!rm && FxConfig.fxTier != FxConfig.TierSetting.LOW) {
			LOCKS.add(new Lock(FxClock.now, x, y, z, u.x, u.y, u.z));
		}
		int id = caster == null ? -1 : caster.getId();
		Double last = LAST_FIRE_SOUND.get(id);
		if (last == null || FxClock.now - last >= UryuArrowLook.WING_SOUND_GAP || FxClock.now < last) {
			LAST_FIRE_SOUND.put(id, FxClock.now);
			for (UryuBowForm.Cue c : UryuArrowLook.wingFireCues()) {
				FxSound.play(null, c.id(), x, y, z, c.pitch(), c.volume());
			}
		}
	}

	// ------------------------------------------------------------------------------------------ glow

	static void emitGlow(FxGlowBatch.Emitter e, double now) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		stateGlow(e, mc, now);
		for (Wing w : WINGS) {
			if (!w.ended && now - w.born > w.life) {
				w.ended = true; // 6.0 s: the wings relax
				FxSound.play(null, "entity.phantom.flap", w.x, w.y + 1.5, w.z, 1.2, 0.4);
			}
		}
		WINGS.removeIf(w -> now - w.born > w.life + UryuWingMath.CROSS_FADE);
		LOCKS.removeIf(l -> now - l.born() > UryuArrowLook.WING_LOCK_LIFE);
		float td = mc.getRenderTickCounter().getTickDelta(false);
		for (Lock l : LOCKS) {
			double k = 1.0 - (now - l.born()) / UryuArrowLook.WING_LOCK_LIFE;
			double len = UryuArrowLook.WING_LOCK_LENGTH;
			e.streak(GlowSprite.LINE, l.x(), l.y(), l.z(), l.x() + l.ux() * len, l.y() + l.uy() * len, l.z() + l.uz() * len, 0.05, UryuPalette.FQ1[0], UryuPalette.FQ1[1],
					UryuPalette.FQ1[2], 0.8 * k);
		}
		for (Wing w : WINGS) {
			Entity en = mc.world.getEntityById(w.caster);
			if (!(en instanceof LivingEntity le) || !le.isAlive()) {
				continue;
			}
			barrageCross(e, w, le, td, now);
			if (UryuQuincyFeature.state(w.caster).bankai()) {
				continue; // the mesh wings are there: the placeholder streaks are only for a caster that has none
			}
			wing(e, w, le, td, now);
		}
	}

	/** The Zeichen cross behind the back for the whole barrage: a vertical plane facing the way the caster looks, turning slowly (2.12). */
	private static void barrageCross(FxGlowBatch.Emitter e, Wing w, LivingEntity le, float td, double now) {
		double age = now - w.born;
		double level = UryuWingMath.crossLevel(age, w.life) * Math.max(0.0, FxConfig.emissiveMultiplier);
		if (level < 0.004) {
			return;
		}
		boolean rm = FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
		double yaw = Math.toRadians(le.prevBodyYaw + (le.bodyYaw - le.prevBodyYaw) * td);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double px = le.prevX + (le.getX() - le.prevX) * td - fx * UryuWingMath.CROSS_BACK;
		double py = le.prevY + (le.getY() - le.prevY) * td + UryuWingMath.CROSS_HEIGHT;
		double pz = le.prevZ + (le.getZ() - le.prevZ) * td - fz * UryuWingMath.CROSS_BACK;
		float[] q1 = UryuPalette.FQ1;
		crossSprites++;
		e.plane(GlowSprite.ZEICHEN_CROSS, px, py, pz, UryuWingMath.CROSS_SIZE, fx, 0.0, fz, UryuWingMath.crossAngle(age, rm), q1[0], q1[1], q1[2], level);
	}

	/** The chest emblem and the faint body light of every bankai player in view (not for the camera owner in first person). */
	private static void stateGlow(FxGlowBatch.Emitter e, MinecraftClient mc, double now) {
		Vec3d cp = mc.gameRenderer.getCamera().getPos();
		double max2 = UryuVollstTiming.VIEW_DISTANCE * UryuVollstTiming.VIEW_DISTANCE;
		float[] q3 = UryuPalette.FQ3;
		for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
			UryuQuincyFeature.State s = UryuQuincyFeature.state(pl.getId());
			if (!s.bankai() || pl.isInvisible() || pl.isSpectator() || cp.squaredDistanceTo(pl.getPos()) > max2 || fpOwner(pl, mc)) {
				continue;
			}
			UryuWingRenderer.WingState st = UryuWingRenderer.stateOf(pl.getId());
			double t = releaseTime(pl.getId(), s);
			if (st == null || t < UryuVollstTiming.T_BANDS) {
				continue;
			}
			double sneak = st.sneak;
			double[] w = new double[3];
			double level = UryuVollstTiming.emblemLevel(t, now) * Math.max(0.0, FxConfig.emissiveMultiplier);
			if (level > 0.01) {
				UryuWingRenderer.toWorld(st, 0.0, 1.22 - 0.20 * sneak, 0.17 + 0.10 * sneak, w);
				e.plane(GlowSprite.CHEVRONS, w[0], w[1], w[2], UryuVollstTiming.EMBLEM_SIZE, -Math.sin(st.yawRad), 0.0, Math.cos(st.yawRad), 0.0, q3[0], q3[1], q3[2], level);
			}
			double light = UryuVollstTiming.smoothstep(UryuVollstTiming.T_UNFOLD, UryuVollstTiming.T_SETTLE, t) * UryuVollstTiming.BODY_LIGHT_INTENSITY;
			if (light > 0.004) {
				UryuWingRenderer.toWorld(st, 0.0, 1.1 - 0.2 * sneak, 0.0, w);
				e.sprite(GlowSprite.GLOW_SOFT, w[0], w[1], w[2], UryuVollstTiming.BODY_LIGHT_SIZE, 0.0, q3[0], q3[1], q3[2], light);
			}
		}
	}

	/** The placeholder wings of the barrage: two sharp swept spars per side, a fan of feathers between them and the trailing edge (casters without mesh wings). */
	private static void wing(FxGlowBatch.Emitter e, Wing w, LivingEntity le, float td, double now) {
		float[] glow = UryuFx.GLOW;
		float[] pale = UryuFx.PALE;
		float[] white = UryuFx.WHITE;
		double age = now - w.born;
		double open = Math.min(1.0, age / 0.35);
		double flare = FxMath.ob(open); // easeOutBack: the wings snap open
		double endFade = age > w.life ? Math.max(0.0, 1.0 - (age - w.life) / 0.4) : 1.0;
		double cyc = (age % (UryuBalance.WING_CYCLE / 20.0)) / (UryuBalance.WING_CYCLE / 20.0);
		double volley = Math.exp(-cyc * 7.0); // a pulse with every volley
		double inten = endFade * (0.65 + 0.5 * volley);
		double yaw = le.prevBodyYaw + (le.bodyYaw - le.prevBodyYaw) * td;
		double fx = le.prevX + (le.getX() - le.prevX) * td;
		double fy = le.prevY + (le.getY() - le.prevY) * td;
		double fz = le.prevZ + (le.getZ() - le.prevZ) * td;
		V3 feet = new V3(fx, fy, fz);
		double grow = (0.35 + 0.65 * flare) * (1.0 + 0.07 * volley);
		for (int side = 0; side < 2; side++) {
			V3 root = shoulder(side, yaw, feet);
			V3 up = scaledTip(WingBarrage.tip(side, yaw, feet), root, grow);
			V3 low = scaledTip(WingBarrage.tip(2 + side, yaw, feet), root, grow);
			// the spars
			e.streak(GlowSprite.STREAK, up.x(), up.y(), up.z(), root.x(), root.y(), root.z(), 0.2, glow[0], glow[1], glow[2], inten);
			e.streak(GlowSprite.STREAK, low.x(), low.y(), low.z(), root.x(), root.y(), root.z(), 0.2, glow[0], glow[1], glow[2], inten);
			e.streak(GlowSprite.STREAK, up.x(), up.y(), up.z(), root.x(), root.y(), root.z(), 0.07, 1f, 1f, 1f, 0.8 * inten);
			// the feathers: a fan from the root between the two tips, longer in the middle, angular trailing edge
			V3 prev = up;
			int n = 5;
			for (int i = 1; i <= n; i++) {
				double t = (double) i / n;
				V3 mid = up.lerp(low, t);
				double extend = 1.0 + 0.18 * Math.sin(Math.PI * t); // the middle feathers are the longest: a pointed outline
				V3 tip = new V3(root.x() + (mid.x() - root.x()) * extend, root.y() + (mid.y() - root.y()) * extend, root.z() + (mid.z() - root.z()) * extend);
				e.streak(GlowSprite.STREAK, tip.x(), tip.y(), tip.z(), root.x(), root.y(), root.z(), 0.13, pale[0], pale[1], pale[2], 0.7 * inten);
				e.streak(GlowSprite.STREAK, tip.x(), tip.y(), tip.z(), prev.x(), prev.y(), prev.z(), 0.09, glow[0], glow[1], glow[2], 0.8 * inten);
				e.sprite(GlowSprite.GLOW_SOFT, tip.x(), tip.y(), tip.z(), 0.35 + 0.35 * volley, 0, white[0], white[1], white[2], 0.5 * inten);
				prev = tip;
			}
			e.sprite(GlowSprite.GLOW_SOFT, up.x(), up.y(), up.z(), 0.9 + 1.0 * volley, 0, glow[0], glow[1], glow[2], 0.8 * inten);
			e.sprite(GlowSprite.STAR4, up.x(), up.y(), up.z(), 0.5 + 0.9 * volley, age * 1.5, 1f, 1f, 1f, 0.9 * inten * (0.4 + 0.6 * volley));
		}
	}

	/** Where a wing grows from, on the back at shoulder height, one per side (0 right, 1 left). */
	private static V3 shoulder(int side, double bodyYawDeg, V3 feet) {
		double yaw = Math.toRadians(bodyYawDeg);
		double rx = -Math.cos(yaw) * 0.18 * (side == 0 ? 1 : -1);
		double rz = -Math.sin(yaw) * 0.18 * (side == 0 ? 1 : -1);
		double bx = Math.sin(yaw) * 0.22; // a little behind the spine
		double bz = -Math.cos(yaw) * 0.22;
		return new V3(feet.x() + rx + bx, feet.y() + 1.35, feet.z() + rz + bz);
	}

	private static V3 scaledTip(V3 tip, V3 root, double k) {
		return new V3(root.x() + (tip.x() - root.x()) * k, root.y() + (tip.y() - root.y()) * k, root.z() + (tip.z() - root.z()) * k);
	}
}
