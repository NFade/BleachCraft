package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.CageVisual;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.Random;
import net.minecraft.util.math.Vec3d;

/**
 * Byakuya shikai ability 3, the petal cage (round 6, effect id 35; made readable in r8 fx2): a sphere of petals closes around the aimed
 * target or point and pulses six times, every pulse an explosion that hurts everything inside. Event params {@code [centreX, centreY,
 * centreZ, radius, firstPulseSeconds, pulseCount, pulseSpacingSeconds]} (CastContext.effectParams, built from the ability spec; the server
 * uses the same centre and radius for the damage, so the drawn area is the hit volume and the pulse times are the damage times, see
 * {@link CageVisual}). The event position is the CASTER, the area centre comes from the params.
 * <ol>
 * <li>0.0 s, the telegraph: a ring on the ground at the exact radius where a creature on the ground is hit ({@link CageVisual#groundRingRadius}),
 * rim ticks, three rings that outline the sphere, a marker at the centre; a stream of petals from the hand and three waves of petals from
 * the rim of the area that fly into the centre and arrive exactly at the first pulse;</li>
 * <li>from 0.2 s: the shell fills (petals sliding on the sphere, pink glints);</li>
 * <li>0.15 s before each pulse: a warning tick (rising pitch) and a ring that shrinks onto the ground ring, so the pulse can be dodged by ear and eye;</li>
 * <li>each pulse (the damage tick): a flash, THREE orthogonal shockwave rings that grow from the centre to the exact radius (a sphere
 * shockwave), a ring on the ground, petals flung outward, a boom with a chime, a shake; the first pulse is a little bigger;</li>
 * <li>the last pulse is the final burst (1.8 times the size, a flash on the screen, a second boom and a bell), then the shell opens.</li>
 * </ol>
 * Tuning ({@code fx_override.json}): {@code cage.speed} (shell slide, 2.6), {@code cage.ringSlack} (half width added to the ground ring, 0.3),
 * {@code cage.telegraph} (ground ring brightness, 0.9), {@code cage.warnLead} (0.15 s), {@code cage.boom} (boom volume scale, 1.0),
 * {@code cage.converge} (petals per wave at radius 5, 24), {@code cage.shake} (0.28 degrees), {@code cage.finalFlash} (0.18), plus the global
 * {@code rk.count}, {@code rk.glow}, {@code rk.size}. UNVERIFIED in the game.
 */
final class CageFx {
	private static final double STEP = 0.05;
	private static final String PINK = "#F9C8F6";
	private static final String PETAL = "#E5A4DC";
	private static final String LILAC = "#F3E4FF";
	private static final String HOT = "#FFE3F6";
	/** Statistics for the harness. */
	static int cages;
	/** The plan of the last cage as the client read it from the event (harness). */
	static CageVisual.Plan lastPlan;
	static double lastGroundRing;

	private CageFx() {
	}

	static void play(EffectEventS2C e) {
		CageVisual.Plan plan = CageVisual.parse(e.params(), e.x(), e.y(), e.z());
		lastPlan = plan;
		Vec3d center = new Vec3d(plan.cx(), plan.cy(), plan.cz());
		// the timeline sits on the AREA (sounds and the screen strength follow the explosions, not the caster)
		EffectTimeline t = FxTimelines.create(35, e.seed(), center, Vec3d.ZERO, e.params(), e.casterId(), FxClient.forceRemote());
		double R = plan.radius();
		double first = plan.first();
		int pulses = plan.pulses();
		double last = plan.lastPulse();
		double end = last + 0.12;
		double gy = FxGround.sample(center.x, center.z, center.y - 1.0);
		double ringR = CageVisual.groundRingRadius(R, center.y - gy, 1.8, FxTune.d("cage.ringSlack", 0.3));
		lastGroundRing = ringR;
		boolean floats = ringR <= 0.05; // the sphere hangs above the ground: the ground shows the footprint of its widest part instead
		double groundR = floats ? R : ringR;
		cages++;
		t.lifetime(last + 1.6);
		// 0.00 the telegraph and the petals that fly into the area
		t.at(0.0, x -> telegraph(x, center, R, groundR, gy, floats, last));
		t.at(0.0, x -> {
			Vec3d hand = handOf(x);
			Random r = x.rng(1);
			int n = RukiaKit.count(26);
			double travel = Math.max(0.3, first - 0.1);
			for (int i = 0; i < n; i++) {
				double j = (r.nextDouble() - 0.5) * 0.6;
				FxParticles.spec(FxParticles.Kind.PETAL).at(hand.x + j, hand.y + (r.nextDouble() - 0.5) * 0.4, hand.z + j)
						.vel((center.x - hand.x) / travel + (r.nextDouble() - 0.5) * 1.5, (center.y - hand.y) / travel + (r.nextDouble() - 0.5) * 1.5, (center.z - hand.z) / travel + (r.nextDouble() - 0.5) * 1.5)
						.drag(1.0).life((int) (travel * 20)).size(RukiaKit.size(0.22)).color(PETAL).colorTo(PINK).seed(i).spin((r.nextDouble() - 0.5) * 0.4).fade(0.3).owner(x).spawn();
			}
			x.soundAt("block.pink_petals.break", center, 1.1, 0.7);
			x.soundAt("block.amethyst_block.resonate", center, 1.4, 0.4);
		});
		for (int w = 0; w < 3; w++) {
			final int wave = w;
			t.at(0.12 * w, x -> converge(x, center, R, Math.max(0.15, first - 0.12 * wave), wave));
		}
		// the shell, every tick from 0.2 s to the end
		for (double s = 0.2; s < end; s += STEP) {
			final double ts = s;
			t.at(s, x -> shell(x, center, R, ts, end));
		}
		// the warning ticks and the pulses
		double lead = FxTune.d("cage.warnLead", 0.15);
		for (int k = 0; k < pulses; k++) {
			final int kk = k;
			t.at(CageVisual.warnTime(plan, k, lead), x -> warn(x, center, groundR, gy, kk, pulses, plan.pulseTime(kk) - CageVisual.warnTime(plan, kk, lead)));
			t.at(plan.pulseTime(k), x -> pulse(x, center, R, groundR, gy, kk, pulses));
		}
		// just after the final burst the shell opens and the last petals drift out
		t.at(end, x -> {
			Random r = x.rng(777);
			int n = RukiaKit.count(40);
			for (int i = 0; i < n; i++) {
				Vec3d d = sphere(r);
				FxParticles.spec(FxParticles.Kind.PETAL).at(center.x + d.x * R, center.y + d.y * R, center.z + d.z * R).vel(d.x * 2.0, d.y * 2.0 + 0.5, d.z * 2.0).drag(0.94).life(26 + r.nextInt(10))
						.size(RukiaKit.size(0.24)).color(PETAL).colorTo(LILAC).seed(i).spin((r.nextDouble() - 0.5) * 0.5).fade(0.4).owner(x).spawn();
			}
			x.soundAt("block.pink_petals.break", center, 0.8, 0.6);
			x.soundAt("block.amethyst_block.break", center, 1.3, 0.5);
		});
		FxTimelines.start(t);
	}

	/** The hand of the caster (right, forward, up from the body yaw), or the chest of the caster at the event time. */
	private static Vec3d handOf(EffectTimeline x) {
		net.minecraft.entity.Entity c = x.caster();
		if (c == null) {
			return x.pos.add(0, 1.2, 0);
		}
		Vec3d p = c.getPos();
		double yaw = Math.toRadians(c.getYaw());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		return new Vec3d(p.x + fx * 0.5 - fz * 0.35, p.y + 1.15, p.z + fz * 0.5 + fx * 0.35);
	}

	private static Vec3d sphere(Random r) {
		double z = r.nextDouble() * 2 - 1;
		double a = r.nextDouble() * Math.PI * 2;
		double q = Math.sqrt(1 - z * z);
		return new Vec3d(q * Math.cos(a), z, q * Math.sin(a));
	}

	// ============================================================================================================ the telegraph

	/**
	 * The area made visible at the cast: the ground ring at the exact hit radius (dim projection of the widest part when the sphere floats),
	 * 24 ticks on its rim, the outline of the sphere as three horizontal rings (equator and +-0.7 R) and a marker at the centre. They stay until
	 * the last pulse.
	 */
	private static void telegraph(EffectTimeline x, Vec3d c, double R, double groundR, double gy, boolean floats, double last) {
		double life = last + 0.25;
		double bright = FxTune.d("cage.telegraph", 0.9) * (floats ? 0.45 : 1.0);
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(c.x, gy + 0.06, c.z).ground().size(groundR / 0.4375).life(life).curve(0.12, 0.12).color(HOT).peak(RukiaKit.glow(bright)).owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(c.x, gy + 0.05, c.z).ground().size(groundR / 0.375).life(life).curve(0.2, 0.12).color(PINK).peak(RukiaKit.glow(bright * 0.35)).owner(x).spawn();
		int ticks = 24;
		for (int i = 0; i < ticks; i++) {
			double a = Math.PI * 2 * i / ticks;
			double px = c.x + Math.cos(a) * groundR;
			double pz = c.z + Math.sin(a) * groundR;
			FxGlowBatch.sprite(i % 6 == 0 ? GlowSprite.STAR4 : GlowSprite.SPECK).at(px, FxGround.sample(px, pz, gy) + 0.12, pz).size(RukiaKit.size(i % 6 == 0 ? 0.55 : 0.28)).life(life).curve(0.15, 0.12)
					.color(HOT).peak(RukiaKit.glow(bright * 0.9)).twinkle().rot(a, 0).owner(x).spawn();
		}
		// the outline of the sphere: three horizontal rings of the exact radii at the centre height and at +-0.7 R
		for (int s = -1; s <= 1; s++) {
			double dy = s * 0.7 * R;
			double rr = Math.sqrt(Math.max(0.01, R * R - dy * dy));
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(c.x, c.y + dy, c.z).plane(0, 1, 0).size(rr / 0.4375).life(life).curve(0.2, 0.15).color(PINK).peak(RukiaKit.glow(s == 0 ? 0.4 : 0.22)).owner(x).spawn();
		}
		// the marker of the centre, held until the first pulse hits
		FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(c.x, c.y, c.z).size(RukiaKit.size(0.5), RukiaKit.size(0.9)).life(life).curve(0.15, 0.1).color(HOT).peak(RukiaKit.glow(0.7)).twinkle().owner(x).spawn();
		x.soundAt("block.amethyst_cluster.place", c, 0.9, 0.7);
	}

	/** One wave of petals from the rim of the area (1.3 R) that fly into the centre and arrive {@code arrival} seconds after the wave starts. */
	private static void converge(EffectTimeline x, Vec3d c, double R, double arrival, int wave) {
		Random r = x.rng(900 + wave);
		int n = RukiaKit.count(CageVisual.convergeCount((int) Math.round(FxTune.d("cage.converge", 24)), R));
		double dist = R * 1.3;
		double speed = CageVisual.convergeSpeed(dist, arrival);
		int life = Math.max(2, (int) Math.round(arrival * 20));
		for (int i = 0; i < n; i++) {
			Vec3d d = sphere(r);
			FxParticles.spec(FxParticles.Kind.PETAL).at(c.x + d.x * dist, c.y + d.y * dist, c.z + d.z * dist).vel(-d.x * speed, -d.y * speed, -d.z * speed).drag(1.0).life(life)
					.size(RukiaKit.size(0.2 + 0.1 * r.nextDouble())).color(r.nextBoolean() ? PETAL : PINK).colorTo(LILAC).seed(r.nextInt(4)).spin((r.nextDouble() - 0.5) * 0.5).fade(0.25).owner(x).spawn();
		}
	}

	/** One tick of the shell: petals on random points of the sphere sliding about the vertical axis, glints on the surface. */
	private static void shell(EffectTimeline x, Vec3d c, double R, double age, double end) {
		Random r = x.rng(1000 + (int) Math.round(age / STEP));
		double fill = FxMath.smoothstep(0.2, 0.8, age) * (1.0 - FxMath.smoothstep(end - 0.25, end, age));
		int n = RukiaKit.count((int) Math.round((4 + R * 1.6) * fill));
		double omega = FxTune.d("cage.speed", 2.6);
		for (int i = 0; i < n; i++) {
			Vec3d d = sphere(r);
			double px = c.x + d.x * R;
			double py = c.y + d.y * R;
			double pz = c.z + d.z * R;
			// tangent about the vertical axis plus a slow rise
			FxParticles.spec(FxParticles.Kind.PETAL).at(px, py, pz).vel(-d.z * omega * R * 0.5, 0.25, d.x * omega * R * 0.5).drag(1.0).life(10 + r.nextInt(8))
					.size(RukiaKit.size(0.2 + 0.1 * r.nextDouble())).color(r.nextBoolean() ? PETAL : PINK).colorTo(LILAC).seed(r.nextInt(4)).spin((r.nextDouble() - 0.5) * 0.5).fade(0.35).owner(x).spawn();
		}
		if (n > 0 && Math.round(age / STEP) % 2 == 0) {
			Vec3d d = sphere(r);
			FxGlowBatch.sprite(GlowSprite.PETAL_GLOW).at(c.x + d.x * R, c.y + d.y * R, c.z + d.z * R).size(RukiaKit.size(0.5)).lifeTicks(8).curve(0.05, 0.6).color(PINK).peak(RukiaKit.glow(0.8)).twinkle()
					.rot(r.nextDouble() * 6.28, 2).owner(x).spawn();
		}
	}

	// ============================================================================================================ the warning and the pulses

	/** The warning tick before a pulse: a rising tick and a ring that shrinks from 1.35 times the ground ring onto it, landing with the pulse. */
	private static void warn(EffectTimeline x, Vec3d c, double groundR, double gy, int k, int pulses, double lead) {
		boolean finalOne = k == pulses - 1;
		x.soundAt("block.amethyst_cluster.hit", c, 0.9 + 0.1 * k, 0.55);
		if (finalOne) {
			x.soundAt("block.beacon.power_select", c, 1.7, 0.45);
		}
		double life = Math.max(0.05, lead);
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(c.x, gy + 0.07, c.z).ground().size(groundR * 1.35 / 0.4375, groundR / 0.4375).sizeEase(FxMath.IQ).life(life).curve(0.02, 0.3).color(PINK)
				.peak(RukiaKit.glow(finalOne ? 0.9 : 0.6)).owner(x).spawn();
	}

	/** A pulse = a damage tick of the server: the explosion of the sphere. */
	private static void pulse(EffectTimeline x, Vec3d c, double R, double groundR, double gy, int k, int pulses) {
		Random r = x.rng(50 + k);
		double sc = CageVisual.pulseScale(k, pulses);
		boolean finalOne = k == pulses - 1;
		// the flash and a bright core
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(c.x, c.y, c.z).size(R * 0.6, R * 2.4).sizeEase(FxMath.OC).life(0.28).curve(0.02, 0.8).color(PINK).peak(RukiaKit.glow(Math.min(1.0, 0.55 * sc))).owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.GLOW_CORE).at(c.x, c.y, c.z).size(1.0, RukiaKit.size(3.0 * sc)).sizeEase(FxMath.OC).life(0.15).curve(0.004, 0.75).color("#FFFFFF").peak(RukiaKit.glow(1.0)).owner(x).spawn();
		// the shockwave of the sphere: three orthogonal rings from 0.5 to the exact radius, and a ring along the ground
		double life = 0.30 + 0.08 * (sc - 1.0);
		for (int a = 0; a < 3; a++) {
			FxGlowBatch.sprite(GlowSprite.RING_THIN).at(c.x, c.y, c.z).plane(a == 0 ? 1 : 0, a == 1 ? 1 : 0, a == 2 ? 1 : 0).size(0.5 / 0.4375, R / 0.4375).sizeEase(FxMath.OC).life(life)
					.curve(0.01, 0.7).color(a == 1 ? LILAC : PINK).peak(RukiaKit.glow(Math.min(1.0, 0.85 * Math.sqrt(sc)))).owner(x).spawn();
		}
		RukiaKit.ring(x, c.x, gy + 0.08, c.z, 0.3, groundR, life, LILAC, Math.min(1.0, 0.8 * sc), true);
		// petals flung out through the shell
		int n = RukiaKit.count((int) Math.round(24 * sc));
		for (int i = 0; i < n; i++) {
			Vec3d d = sphere(r);
			double sp = (3.5 + (finalOne ? 1.5 : 0.0)) * (0.8 + 0.4 * r.nextDouble());
			FxParticles.spec(FxParticles.Kind.PETAL).at(c.x + d.x * R * 0.9, c.y + d.y * R * 0.9, c.z + d.z * R * 0.9).vel(d.x * sp, d.y * sp, d.z * sp).drag(0.93).life(14 + r.nextInt(8) + (finalOne ? 8 : 0))
					.size(RukiaKit.size(0.22)).color(PETAL).colorTo(finalOne ? LILAC : PINK).seed(i).spin((r.nextDouble() - 0.5) * 0.5).fade(0.4).owner(x).spawn();
		}
		if (finalOne) {
			// the final burst: the whole shell blows apart
			int m = RukiaKit.count(60);
			for (int i = 0; i < m; i++) {
				Vec3d d = sphere(r);
				FxParticles.spec(FxParticles.Kind.PETAL).at(c.x + d.x * R, c.y + d.y * R, c.z + d.z * R).vel(d.x * 3.0, d.y * 3.0 + 0.5, d.z * 3.0).drag(0.94).life(26 + r.nextInt(10))
						.size(RukiaKit.size(0.24)).color(PETAL).colorTo(LILAC).seed(i).spin((r.nextDouble() - 0.5) * 0.5).fade(0.4).owner(x).spawn();
			}
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(c.x, c.y, c.z).size(R * 1.2, R * 3.2).sizeEase(FxMath.OC).life(0.4).curve(0.02, 0.8).color(LILAC).peak(RukiaKit.glow(0.6)).owner(x).spawn();
			x.flash(FxTune.d("cage.finalFlash", 0.18), HOT, true);
		}
		x.shake(FxTune.d("cage.shake", 0.28) * sc * (k == 0 ? 1.2 : 1.0), 0.18 + 0.1 * (sc - 1.0));
		// audio: boom + shock (the explosion), the chime (the pitch rises with every step), petals; the last one is bigger and ends on a bell
		double b = FxTune.d("cage.boom", 1.0);
		x.soundAt("entity.generic.explode", c, (finalOne ? 1.1 : 1.6) - 0.04 * k, 0.4 * sc * b);
		x.soundAt("entity.firework_rocket.blast", c, 0.9, 0.6 * Math.min(1.5, sc) * b);
		x.soundAt("block.amethyst_block.chime", c, 1.0 + 0.1 * k, 0.8 * b);
		x.soundAt("block.pink_petals.break", c, 1.2, 0.5 * b);
		if (finalOne) {
			x.soundAt("entity.firework_rocket.large_blast", c, 0.85, 0.8 * b);
			x.soundAt("block.bell.use", c, 1.3, 0.5 * b);
		}
	}
}
