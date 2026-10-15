package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.UryuStarMath;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Sternenstaub (BANKAI B, effect id 77, URYU_VFX_STORYBOARD 2.14; U-FX 10). Effect 78 with params[3] = 77 is the KEY PRESS: the 0.8 s charge starts, flake
 * flechettes pop out of the air around the caster, fly into a rotating ring in front of him and at 0.4 s compress into a straight chain along the aim with
 * the four point diamond head at the front; at 0.7 s the world holds its breath. Effect 77 is the RELEASE (screen effects of the owner) and the LINE packet
 * (eye, end point, half width) launches the head at 140 blocks per second: the beam (core, body, outer, glitter), the side bars, the shock rings, the shed
 * stardust, the muzzle; at the end point the diamond, the flash sphere, the X burst, the ground ring, radial flakes and stardust. The geometry is
 * drawn every frame as glow sprites and streaks from the FX clock (a frozen clock keeps it). Reduce Motion and the Photosensitive mode: half the flakes, a
 * slow ring, no shake or FOV kick, the impact frame becomes a flash capped at 0.40. The flake and speck counts follow the tier ({@link FxConfig#uryuStarFlakes},
 * {@link FxConfig#uryuMaxStardust}); under 60 flakes there are no side bars. Without effect 78 (an old server) the LINE packet alone still plays the beam.
 * Not drawn: the model {@code uryu_star_head} (the head is the {@code diamond_star} flare) and the small burst at every pierced target (the server does
 * not report hits). Called by {@link UryuFx}.
 */
final class UryuStarFx {
	private static final class Star {
		int caster;
		double litAt;
		double charge = UryuStarMath.CHARGE;
		boolean released;
		double releaseAt;
		boolean launched;
		double launchedAt;
		Vec3d feet = Vec3d.ZERO;
		Vec3d look = new Vec3d(0.0, 0.0, 1.0);
		Vec3d a = Vec3d.ZERO;
		Vec3d u = new Vec3d(0.0, 0.0, 1.0);
		double len;
		boolean local;
		boolean endFired;
		boolean flapFired;
		boolean ended;
		boolean[] cue = new boolean[3];
		double skyAcc;
		double lastNow = -1.0;
		ScreenFx.Layer grade;
		ScreenFx.Layer vignette;
	}

	private static final double[] CUE_T = {UryuStarMath.LINE_UP, 0.45, 0.65};
	private static final List<Star> STARS = new ArrayList<>();
	private static final Random RNG = new Random(0x57A2);
	private static boolean inited;

	/** Harness: casts seen, launches, flakes drawn, beam strips, shock rings, specks spawned, end bursts. */
	static int casts;
	static int launches;
	static int flakeQuads;
	static int beamQuads;
	static int rings;
	static int specks;
	static int bursts;

	private UryuStarFx() {
	}

	static void init() {
		if (inited) {
			return;
		}
		inited = true;
		FxGlowBatch.addSource(UryuStarFx::emit);
	}

	static void clear() {
		for (Star s : STARS) {
			dropLayers(s, 0.2);
		}
		STARS.clear();
	}

	private static boolean calm() {
		return FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
	}

	private static void dropLayers(Star s, double fade) {
		if (s.grade != null && s.grade.held()) {
			s.grade.release(fade);
		}
		if (s.vignette != null && s.vignette.held()) {
			s.vignette.release(fade);
		}
	}

	private static boolean isLocal(int caster) {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && mc.player.getId() == caster;
	}

	// ------------------------------------------------------------------ events

	/** Effect 78 (S-U4) with params[3] = 77: the key was pressed (x y z = feet, d = look), the release comes {@code params[4]} seconds later. */
	static void castStart(EffectEventS2C e) {
		init();
		STARS.removeIf(q -> {
			if (q.caster == e.casterId()) {
				dropLayers(q, 0.2);
				return true;
			}
			return false;
		});
		Star s = new Star();
		s.caster = e.casterId();
		s.litAt = FxClock.now;
		float[] p = e.params();
		if (p.length > 4 && p[4] > 0.05f) {
			s.charge = p[4];
		}
		s.feet = new Vec3d(e.x(), e.y(), e.z());
		Vec3d look = new Vec3d(e.dx(), e.dy(), e.dz());
		if (look.lengthSquared() > 1.0E-6) {
			s.look = look.normalize();
		}
		s.local = isLocal(e.casterId());
		casts++;
		boolean calm = calm();
		if (s.local) {
			s.grade = ScreenFx.grade(FxMath.hex(UryuPalette.GRADE_STAR), calm ? 0.25 : 0.5, 0.5, 1.0);
			s.vignette = ScreenFx.vignette(ScreenFx.Kind.DARK, calm ? 0.15 : 0.40, 0.5, 1.0);
		}
		Vec3d at = s.feet.add(0.0, 1.5, 0.0);
		FxSound.play(null, "block.beacon.power_select", at.x, at.y, at.z, 1.2, 0.7);
		FxSound.play(null, "entity.evoker.prepare_attack", at.x, at.y, at.z, 1.2, 0.5);
		STARS.add(s);
	}

	/** Effect 77: the release. The owner's screen effects; the sounds and the beam come with the LINE packet. */
	static void release(EffectEventS2C e) {
		init();
		Star s = null;
		for (Star q : STARS) {
			if (!q.ended && (q.caster == e.casterId() || (q.caster == -1 && q.launched && FxClock.now - q.launchedAt < 0.3))) {
				s = q;
			}
		}
		if (s == null) {
			s = new Star();
			s.caster = e.casterId();
			s.litAt = FxClock.now - s.charge;
			s.feet = new Vec3d(e.x(), e.y(), e.z());
			STARS.add(s);
		}
		if (s.released) {
			return;
		}
		s.caster = e.casterId();
		s.released = true;
		s.releaseAt = FxClock.now;
		s.local = isLocal(e.casterId());
		if (s.vignette != null && s.vignette.held()) {
			s.vignette.release(0.5);
		}
		if (!s.local) {
			return;
		}
		boolean calm = calm();
		if (calm) {
			ScreenFx.flash(UryuStarMath.FLASH_CAP_CALM, UryuPalette.FQ1, true, 1.0);
		} else {
			ScreenFx.impactFrame(1.0);
			ScreenFx.shake(1.2, 0.5, 1.0);
			ScreenFx.shunpoRush(0.0, 5.0, 0.5);
		}
	}

	/** The LINE packet: the head leaves the eye {@code a} for {@code b}, half width {@code halfWidth} (the hit zone; the beam look follows 2.14). */
	static void line(Vec3d a, Vec3d b, double halfWidth) {
		init();
		Vec3d d = b.subtract(a);
		double len = d.length();
		if (len < 0.5) {
			return;
		}
		Star s = null;
		double best = 10.0;
		for (Star q : STARS) {
			if (q.ended || q.launched) {
				continue;
			}
			double dist = q.feet.add(0.0, 1.5, 0.0).distanceTo(a);
			if (dist < best) {
				best = dist;
				s = q;
			}
		}
		if (s == null) {
			s = new Star();
			s.caster = -1;
			s.litAt = FxClock.now - s.charge;
			s.local = false;
			STARS.add(s);
		}
		s.launched = true;
		s.launchedAt = FxClock.now;
		s.a = a;
		s.u = d.multiply(1.0 / len);
		s.len = len;
		launches++;
		boolean calm = calm();
		FxSound.play(null, "entity.warden.sonic_boom", a.x, a.y, a.z, 1.5, 1.0);
		FxSound.play(null, "item.trident.thunder", a.x, a.y, a.z, 1.8, 0.5);
		FxSound.play(null, "item.crossbow.shoot", a.x, a.y, a.z, 0.7, 0.8);
		FxSound.play(null, "entity.firework_rocket.blast", a.x, a.y, a.z, 0.6, 0.5);
		// 300 specks shed along the whole line (they appear as the head passes), drifting out at 1 block per second
		int n = UryuStarMath.speckCount(UryuStarMath.LINE_SPECKS, FxConfig.uryuMaxStardust) / (calm ? 2 : 1);
		Vec3d[] pp = perp(s.u);
		for (int j = 0; j < n; j++) {
			double dd = RNG.nextDouble() * len;
			double ang = RNG.nextDouble() * Math.PI * 2.0;
			Vec3d out = pp[0].multiply(Math.cos(ang)).add(pp[1].multiply(Math.sin(ang)));
			Vec3d at = a.add(s.u.multiply(dd)).add(out.multiply(RNG.nextDouble() * 1.2));
			specks++;
			FxGlowBatch.sprite(GlowSprite.SPECK).at(at.x, at.y, at.z).vel(out.x, out.y, out.z).size(0.2).delay(Math.max(0.0, (dd - UryuStarMath.headStart()) / UryuStarMath.HEAD_SPEED))
					.life(1.2).curve(0.05, 0.6).color(RNG.nextBoolean() ? UryuPalette.Q2 : UryuPalette.Q1).peak(0.8).twinkle().spawn();
		}
	}

	// ------------------------------------------------------------------ frame

	private static Vec3d[] perp(Vec3d u) {
		Vec3d ref = Math.abs(u.y) > 0.95 ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(0.0, 1.0, 0.0);
		Vec3d p1 = u.crossProduct(ref).normalize();
		Vec3d p2 = u.crossProduct(p1).normalize();
		return new Vec3d[] {p1, p2};
	}

	private static void emit(FxGlowBatch.Emitter e) {
		if (STARS.isEmpty()) {
			return;
		}
		double now = FxClock.now;
		double emi = Math.max(0.0, FxConfig.emissiveMultiplier);
		for (Star s : STARS) {
			double t = now - s.litAt;
			if (!s.launched) {
				if ((!s.released && t > s.charge + 0.8) || (s.released && now - s.releaseAt > 0.6)) {
					s.ended = true;
					dropLayers(s, 0.4);
					continue;
				}
				pre(e, s, t, emi);
			} else {
				double tau = now - s.launchedAt;
				if (tau > UryuStarMath.totalTime(s.len) + 0.1) {
					s.ended = true;
					dropLayers(s, 0.4);
					continue;
				}
				post(e, s, tau, emi);
			}
		}
		STARS.removeIf(q -> q.ended);
	}

	/** Eye, aim, right, up of the caster now (his entity when it is loaded, else the key press snapshot). */
	private static Vec3d[] frame(Star s) {
		Vec3d eye = s.feet.add(0.0, 1.5, 0.0);
		Vec3d fwd = s.look;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world != null) {
			Entity en = mc.world.getEntityById(s.caster);
			if (en != null) {
				float td = UryuAnchors.tickDelta();
				eye = en.getCameraPosVec(td);
				fwd = en.getRotationVec(td);
			}
		}
		Vec3d[] pp = perp(fwd);
		return new Vec3d[] {eye, fwd, pp[0], pp[1]};
	}

	private static void pre(FxGlowBatch.Emitter e, Star s, double t, double emi) {
		boolean calm = calm();
		Vec3d[] f = frame(s);
		Vec3d eye = f[0];
		Vec3d fwd = f[1];
		Vec3d right = f[2];
		Vec3d up = f[3];
		// the cue sounds of the line up and the two heartbeats
		for (int i = 0; i < CUE_T.length; i++) {
			if (!s.cue[i] && t >= CUE_T[i]) {
				s.cue[i] = true;
				if (i == 0) {
					FxSound.play(null, "block.beacon.activate", eye.x, eye.y, eye.z, 0.8, 0.7);
				} else {
					FxSound.play(null, "entity.warden.heartbeat", eye.x, eye.y, eye.z, 0.7, 0.5);
				}
			}
		}
		// the sky fills with specks during the first 0.4 s
		if (t < UryuStarMath.LINE_UP) {
			int total = UryuStarMath.speckCount(UryuStarMath.SKY_SPECKS, FxConfig.uryuMaxStardust) / (calm ? 2 : 1);
			double dt = s.lastNow < 0.0 ? 0.0 : Math.max(0.0, Math.min(0.1, FxClock.now - s.lastNow));
			s.lastNow = FxClock.now;
			s.skyAcc += total * dt / UryuStarMath.LINE_UP;
			while (s.skyAcc >= 1.0) {
				s.skyAcc -= 1.0;
				double ang = RNG.nextDouble() * Math.PI * 2.0;
				double r = UryuStarMath.SKY_RADIUS * Math.sqrt(RNG.nextDouble());
				Vec3d at = eye.add(Math.cos(ang) * r, (RNG.nextDouble() - 0.3) * 8.0, Math.sin(ang) * r);
				specks++;
				FxGlowBatch.sprite(GlowSprite.SPECK).at(at.x, at.y, at.z).vel(0.0, -0.1, 0.0).size(0.2).life(1.6).curve(0.2, 0.5).color(RNG.nextBoolean() ? UryuPalette.Q2 : UryuPalette.Q9)
						.peak(0.7).twinkle().spawn();
			}
		}
		int n = UryuStarMath.flakeCount(FxConfig.uryuStarFlakes, calm);
		double ringRev = calm ? UryuStarMath.RING_REV * 0.5 : UryuStarMath.RING_REV;
		double[] o = new double[4];
		double[] o1 = new double[4];
		for (int i = 0; i < n; i++) {
			UryuStarMath.flake(i, n, t, ringRev, o);
			if (o[3] < 0.01) {
				continue;
			}
			UryuStarMath.flake(i, n, t + 0.03, ringRev, o1);
			Vec3d p = local(eye, right, up, fwd, o);
			Vec3d dir = local(eye, right, up, fwd, o1).subtract(p);
			dir = dir.lengthSquared() < 1.0E-8 ? fwd : dir.normalize();
			float[] c = (i & 1) == 0 ? UryuPalette.FQ2 : UryuPalette.FQ1;
			if (e.streak(GlowSprite.STREAK, p.x + dir.x * 0.2, p.y + dir.y * 0.2, p.z + dir.z * 0.2, p.x - dir.x * 0.2, p.y - dir.y * 0.2, p.z - dir.z * 0.2, 0.12, c[0], c[1], c[2], 0.9 * o[3] * emi)) {
				flakeQuads++;
			}
		}
		// the glow of the ring while it forms
		double ringLvl = UryuStarMath.ease(t / 0.25) * (1.0 - UryuStarMath.ease((t - 0.3) / 0.2));
		if (ringLvl > 0.01) {
			Vec3d rc = eye.add(fwd.multiply(UryuStarMath.RING_AHEAD));
			e.sprite(GlowSprite.GLOW_SOFT, rc.x, rc.y, rc.z, 3.0, 0.0, UryuPalette.FQ3[0], UryuPalette.FQ3[1], UryuPalette.FQ3[2], 0.25 * ringLvl * emi);
		}
		// from 0.4 s: the diamond head at the front of the chain and the sign ring around the shaft
		double head = UryuStarMath.ease((t - UryuStarMath.LINE_UP) / 0.1);
		if (head > 0.01) {
			Vec3d h = eye.add(fwd.multiply(UryuStarMath.headStart()));
			double hold = t >= UryuStarMath.FREEZE_FROM ? 1.0 : 0.85 + 0.15 * Math.sin(t * 40.0);
			e.sprite(GlowSprite.DIAMOND_STAR, h.x, h.y, h.z, 4.0 * head, 0.0, UryuPalette.FQ1[0], UryuPalette.FQ1[1], UryuPalette.FQ1[2], 0.7 * head * (calm ? 1.0 : hold) * emi);
			e.sprite(GlowSprite.GLOW_SOFT, h.x, h.y, h.z, 1.6, 0.0, 1f, 1f, 1f, 0.5 * head * emi);
			Vec3d zc = eye.add(fwd.multiply(UryuStarMath.headStart() - 1.2));
			e.plane(GlowSprite.ZEICHEN_CROSS, zc.x, zc.y, zc.z, 2.2, fwd.x, fwd.y, fwd.z, calm ? 0.0 : t * 2.0, UryuPalette.FQ2[0], UryuPalette.FQ2[1], UryuPalette.FQ2[2], 0.6 * head * emi);
		}
	}

	private static Vec3d local(Vec3d eye, Vec3d right, Vec3d up, Vec3d fwd, double[] o) {
		return eye.add(right.multiply(o[0])).add(up.multiply(o[1])).add(fwd.multiply(o[2]));
	}

	private static void post(FxGlowBatch.Emitter e, Star s, double tau, double emi) {
		boolean calm = calm();
		double len = s.len;
		Vec3d a = s.a;
		Vec3d u = s.u;
		Vec3d[] pp = perp(u);
		double arrive = UryuStarMath.arrivalTime(len);
		double p = UryuStarMath.headDistance(len, tau);
		Vec3d head = a.add(u.multiply(p));
		if (s.grade != null && s.grade.held() && tau > 0.4) {
			s.grade.release(1.3);
		}
		// the beam: tail eats it from the muzzle toward the head after the hold
		double tailF = UryuStarMath.tailFraction(len, tau);
		double from = len * tailF;
		if (tailF < 1.0 && from < p) {
			Vec3d t0 = a.add(u.multiply(from));
			double lvl = emi;
			if (e.streak(GlowSprite.STREAK, t0.x, t0.y, t0.z, head.x, head.y, head.z, UryuStarMath.OUTER_WIDTH, UryuPalette.FQ2[0], UryuPalette.FQ2[1], UryuPalette.FQ2[2], 0.18 * lvl)) {
				beamQuads++;
			}
			if (e.streak(GlowSprite.STREAK, t0.x, t0.y, t0.z, head.x, head.y, head.z, UryuStarMath.BODY_WIDTH, UryuPalette.FQ3[0], UryuPalette.FQ3[1], UryuPalette.FQ3[2], 0.55 * lvl)) {
				beamQuads++;
			}
			if (!calm && e.streak(GlowSprite.BEAM_GLITTER, t0.x, t0.y, t0.z, head.x, head.y, head.z, 1.6, UryuPalette.FQ1[0], UryuPalette.FQ1[1], UryuPalette.FQ1[2], 0.35 * lvl)) {
				beamQuads++;
			}
			if (e.streak(GlowSprite.STREAK, t0.x, t0.y, t0.z, head.x, head.y, head.z, UryuStarMath.CORE_WIDTH, 1f, 1f, 1f, 1.0 * lvl)) {
				beamQuads++;
			}
		}
		// the muzzle
		if (tau < 0.2) {
			e.sprite(GlowSprite.BURST_RAYS, a.x, a.y, a.z, 6.0, 0.0, UryuPalette.FQ1[0], UryuPalette.FQ1[1], UryuPalette.FQ1[2], (1.0 - tau / 0.2) * emi);
		}
		// the head, the sign ring and the chain of flakes behind it (it stretches, then falls when the head has arrived)
		if (p < len) {
			e.sprite(GlowSprite.DIAMOND_STAR, head.x, head.y, head.z, 4.0, 0.0, UryuPalette.FQ1[0], UryuPalette.FQ1[1], UryuPalette.FQ1[2], 0.9 * emi);
			Vec3d zc = head.subtract(u.multiply(1.2));
			e.plane(GlowSprite.ZEICHEN_CROSS, zc.x, zc.y, zc.z, 2.2, u.x, u.y, u.z, calm ? 0.0 : tau * 6.0, UryuPalette.FQ2[0], UryuPalette.FQ2[1], UryuPalette.FQ2[2], 0.6 * emi);
		}
		int n = UryuStarMath.flakeCount(FxConfig.uryuStarFlakes, calm);
		double fall = Math.max(0.0, tau - arrive - UryuStarMath.BEAM_HOLD);
		double alpha = UryuStarMath.clamp01(1.0 - fall / (UryuStarMath.FLAKE_LIFE_AFTER + 0.2));
		if (alpha > 0.01) {
			double[] o = new double[4];
			double drop = 0.5 * UryuStarMath.FLAKE_FALL * fall * fall;
			for (int i = 0; i < n; i++) {
				UryuStarMath.chain(i, n, p, tau, o);
				Vec3d c = head.subtract(u.multiply(o[2])).add(pp[0].multiply(o[0])).add(pp[1].multiply(o[1])).add(0.0, -drop * (0.5 + UryuStarMath.hash01(i, 5)), 0.0);
				float[] col = (i & 1) == 0 ? UryuPalette.FQ2 : UryuPalette.FQ1;
				if (e.streak(GlowSprite.STREAK, c.x + u.x * 0.2, c.y + u.y * 0.2, c.z + u.z * 0.2, c.x - u.x * 0.2, c.y - u.y * 0.2, c.z - u.z * 0.2, 0.12, col[0], col[1], col[2], 0.9 * alpha * emi)) {
					flakeQuads++;
				}
			}
		}
		// the side bars: six crystal ribbons at 20 and 160 degrees from the axis, three around it, fading in 0.3 s (not on Low)
		double side = UryuStarMath.sideLevel(tau);
		if (side > 0.01 && UryuStarMath.sideBars(FxConfig.uryuStarFlakes)) {
			for (int j = 0; j < UryuStarMath.SIDE_BARS; j++) {
				double az = Math.PI / 3.0 * (j / 2);
				double ang = Math.toRadians((j & 1) == 0 ? UryuStarMath.SIDE_ANGLE : 180.0 - UryuStarMath.SIDE_ANGLE);
				Vec3d around = pp[0].multiply(Math.cos(az)).add(pp[1].multiply(Math.sin(az)));
				Vec3d dir = u.multiply(Math.cos(ang)).add(around.multiply(Math.sin(ang)));
				Vec3d end = head.add(dir.multiply(UryuStarMath.SIDE_LENGTH));
				if (e.streak(GlowSprite.STREAK, head.x, head.y, head.z, end.x, end.y, end.z, 0.8, UryuPalette.FQ2[0], UryuPalette.FQ2[1], UryuPalette.FQ2[2], 0.4 * side * emi)) {
					beamQuads++;
				}
			}
		}
		// the shock rings along the line, one every 4 blocks as the head passes
		int rc = UryuStarMath.ringCount(len);
		for (int k = 1; k <= rc; k++) {
			double age = tau - UryuStarMath.ringStart(k);
			if (age < 0.0 || age > UryuStarMath.SHOCK_TIME + 0.15) {
				continue;
			}
			Vec3d c = a.add(u.multiply(UryuStarMath.RING_SPACING * k));
			double fade = UryuStarMath.clamp01(1.0 - age / (UryuStarMath.SHOCK_TIME + 0.15));
			e.plane(GlowSprite.RING_THIN, c.x, c.y, c.z, Math.max(0.05, UryuStarMath.ringSize(age) * 4.0), u.x, u.y, u.z, 0.0, UryuPalette.FQ2[0], UryuPalette.FQ2[1], UryuPalette.FQ2[2], 0.7 * fade * emi);
			rings++;
		}
		if (tau >= arrive) {
			endPoint(e, s, tau - arrive, emi, calm);
		}
	}

	private static void endPoint(FxGlowBatch.Emitter e, Star s, double age, double emi, boolean calm) {
		Vec3d c = s.a.add(s.u.multiply(s.len));
		if (!s.endFired) {
			s.endFired = true;
			bursts++;
			fireEnd(s, c, calm);
		}
		if (!s.flapFired && age >= UryuStarMath.BEAM_HOLD) {
			s.flapFired = true;
			FxSound.play(null, "entity.phantom.flap", c.x, c.y, c.z, 1.4, 0.3);
		}
		double dia = UryuStarMath.diamondLevel(age);
		if (dia > 0.005) {
			e.sprite(GlowSprite.DIAMOND_STAR, c.x, c.y, c.z, 9.0, 0.0, UryuPalette.FQ0[0], UryuPalette.FQ0[1], UryuPalette.FQ0[2], dia * emi);
		}
		double sph = UryuStarMath.sphereLevel(age);
		if (sph > 0.005) {
			e.sprite(GlowSprite.GLOW_SOFT, c.x, c.y, c.z, 14.0, 0.0, UryuPalette.FQ1[0], UryuPalette.FQ1[1], UryuPalette.FQ1[2], sph * (calm ? 0.6 : 1.0) * emi);
		}
		if (age < 0.3) {
			e.sprite(GlowSprite.BURST_RAYS, c.x, c.y, c.z, 10.0, 0.0, UryuPalette.FQ2[0], UryuPalette.FQ2[1], UryuPalette.FQ2[2], (1.0 - age / 0.3) * emi);
		}
		// the X burst: four bar crystals in the plane facing the camera, at 45 degree steps
		if (age < UryuStarMath.X_TIME * 2.0 && UryuStarMath.sideBars(FxConfig.uryuStarFlakes)) {
			UryuAnchors.Cam cam = UryuAnchors.cam();
			double g = UryuStarMath.ease(age / UryuStarMath.X_TIME);
			double fade = UryuStarMath.clamp01(1.0 - age / (UryuStarMath.X_TIME * 2.0));
			for (int k = 0; k < 4; k++) {
				double ang = Math.PI / 4.0 * k;
				double dx = cam.right()[0] * Math.cos(ang) + cam.up()[0] * Math.sin(ang);
				double dy = cam.right()[1] * Math.cos(ang) + cam.up()[1] * Math.sin(ang);
				double dz = cam.right()[2] * Math.cos(ang) + cam.up()[2] * Math.sin(ang);
				double h = UryuStarMath.X_LENGTH * 0.5 * g;
				if (e.streak(GlowSprite.STREAK, c.x + dx * h, c.y + dy * h, c.z + dz * h, c.x - dx * h, c.y - dy * h, c.z - dz * h, 1.0, UryuPalette.FQ3[0], UryuPalette.FQ3[1], UryuPalette.FQ3[2], fade * emi)) {
					beamQuads++;
				}
			}
		}
		// the ground ring
		if (age < UryuStarMath.GROUND_RING_TIME) {
			double r = UryuStarMath.GROUND_RING_R * UryuStarMath.ease(age / UryuStarMath.GROUND_RING_TIME);
			double fade = 1.0 - age / UryuStarMath.GROUND_RING_TIME;
			e.ground(GlowSprite.RING_SOFT, c.x, c.y + 0.05, c.z, r * 2.0, r * 2.0, 0.0, UryuPalette.FQ2[0], UryuPalette.FQ2[1], UryuPalette.FQ2[2], 0.6 * fade * emi);
		}
	}

	/** Once at the end point: the sounds, the flash by distance, the shake near it, 60 radial flakes and 200 radial specks, the 40 specks that remain. */
	private static void fireEnd(Star s, Vec3d c, boolean calm) {
		FxSound.play(null, "entity.generic.explode", c.x, c.y, c.z, 1.1, 1.0);
		FxSound.play(null, "block.glass.break", c.x, c.y, c.z, 1.5, 0.6);
		for (int k = 0; k < 3; k++) {
			FxSound.play(null, "block.amethyst_block.break", c.x, c.y, c.z, 1.0 + 0.15 * k, 0.8);
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
			double dist = mc.gameRenderer.getCamera().getPos().distanceTo(c);
			if (dist < UryuStarMath.FLASH_REACH) {
				ScreenFx.flash(UryuStarMath.flashAt(dist, calm), UryuPalette.FQ1, calm, 1.0);
			}
			if (!calm && dist < 30.0) {
				ScreenFx.shake(0.9, 0.4, 1.0);
			}
		}
		int nf = Math.min(60, FxConfig.uryuStarFlakes / 2) / (calm ? 2 : 1);
		for (int k = 0; k < nf; k++) {
			Vec3d d = randomUnit();
			UryuFlakes.spawn(c, d.multiply(9.0), 30, 0.35, k % 2 == 0 ? UryuPalette.Q2 : UryuPalette.Q1, (RNG.nextDouble() - 0.5) * 0.6, k, 1.0);
		}
		int ns = UryuStarMath.speckCount(UryuStarMath.BURST_SPECKS, FxConfig.uryuMaxStardust) / (calm ? 2 : 1);
		for (int k = 0; k < ns; k++) {
			Vec3d d = randomUnit().multiply(3.0 + 7.0 * RNG.nextDouble());
			specks++;
			FxGlowBatch.sprite(GlowSprite.SPECK).at(c.x, c.y, c.z).vel(d.x, d.y, d.z).drag(0.06).size(0.22).life(0.9).curve(0.02, 0.7).color(RNG.nextBoolean() ? UryuPalette.Q2 : UryuPalette.Q0)
					.peak(0.9).twinkle().spawn();
		}
		int nr = UryuStarMath.speckCount(UryuStarMath.REMAINS_SPECKS * 15, FxConfig.uryuMaxStardust) / 15;
		for (int k = 0; k < nr; k++) {
			Vec3d at = c.add(randomUnit().multiply(6.0 * RNG.nextDouble()));
			specks++;
			FxGlowBatch.sprite(GlowSprite.SPECK).at(at.x, at.y, at.z).vel(0.0, -0.2, 0.0).size(0.2).delay(0.5).life(3.0).curve(0.3, 0.5).color(UryuPalette.Q2).peak(0.6).twinkle().spawn();
		}
	}

	private static Vec3d randomUnit() {
		double ang = RNG.nextDouble() * Math.PI * 2.0;
		double el = Math.asin(RNG.nextDouble() * 2.0 - 1.0);
		return new Vec3d(Math.cos(el) * Math.cos(ang), Math.sin(el), Math.cos(el) * Math.sin(ang));
	}
}
