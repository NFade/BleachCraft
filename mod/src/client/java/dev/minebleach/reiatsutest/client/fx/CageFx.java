package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.Random;
import net.minecraft.util.math.Vec3d;

/**
 * Byakuya shikai ability 3, the petal cage (round 6, effect id 35): a sphere of petals closes around the aimed target or point and
 * pulses. Event params {@code [centreX, centreY, centreZ, radius, firstPulseSeconds, pulseCount, pulseSpacingSeconds]}
 * (CastContext.effectParams; the server uses the same centre and radius for the damage, so the drawn cage is the hit volume).
 * <ol>
 * <li>0.0 s: a stream of petals leaves the caster's hand for the centre;</li>
 * <li>from 0.2 s: the shell fills: every tick a handful of petals appears at random points of the sphere and slides along it (sideways
 * round the vertical axis, with a little rise), a pink glint sparkles on the surface;</li>
 * <li>each server pulse: a pink flash disc and a ring at the centre, a burst of petals flung outward through the shell, a chime;</li>
 * <li>after the last pulse: the shell breaks into a last burst and stops.</li>
 * </ol>
 * The petals are the same {@code PETAL} particles and {@code petal_glow} sprites as the rest of the Senbonzakura effects, so the cap
 * and quality tier of {@link FxParticles} and {@link FxGlowBatch} apply. UNVERIFIED in the game.
 */
final class CageFx {
	private static final double STEP = 0.05;
	private static final String PINK = "#F9C8F6";
	private static final String PETAL = "#E5A4DC";
	private static final String LILAC = "#F3E4FF";
	/** Statistics for the harness. */
	static int cages;

	private CageFx() {
	}

	static void play(EffectEventS2C e) {
		Vec3d center = new Vec3d(e.x(), e.y(), e.z());
		EffectTimeline t = FxTimelines.create(35, e.seed(), center, Vec3d.ZERO, e.params(), e.casterId(), FxClient.forceRemote());
		double R = Math.max(0.8, param(t, 3, 5.0));
		double first = Math.max(0.1, param(t, 4, 0.6));
		int pulses = (int) Math.max(1, param(t, 5, 6));
		double spacing = Math.max(0.1, param(t, 6, 0.5));
		double end = first + (pulses - 1) * spacing + 0.35;
		cages++;
		t.lifetime(end + 1.5);
		// 0.00 the stream from the hand to the centre
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
			x.sound("block.pink_petals.break", 1.1, 0.7);
			x.sound("block.amethyst_block.resonate", 1.4, 0.4);
		});
		// the shell, every tick from 0.2 s to the end
		for (double s = 0.2; s < end; s += STEP) {
			final double ts = s;
			t.at(s, x -> shell(x, center, R, ts, end));
		}
		// the pulses
		for (int k = 0; k < pulses; k++) {
			final int kk = k;
			t.at(first + k * spacing, x -> pulse(x, center, R, kk, pulses));
		}
		// the end: the shell opens
		t.at(end, x -> {
			Random r = x.rng(777);
			int n = RukiaKit.count(60);
			for (int i = 0; i < n; i++) {
				Vec3d d = sphere(r);
				FxParticles.spec(FxParticles.Kind.PETAL).at(center.x + d.x * R, center.y + d.y * R, center.z + d.z * R).vel(d.x * 3.0, d.y * 3.0 + 0.5, d.z * 3.0).drag(0.94).life(26 + r.nextInt(10))
						.size(RukiaKit.size(0.24)).color(PETAL).colorTo(LILAC).seed(i).spin((r.nextDouble() - 0.5) * 0.5).fade(0.4).owner(x).spawn();
			}
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(center.x, center.y, center.z).size(R * 1.2, R * 3.2).sizeEase(FxMath.OC).life(0.4).curve(0.02, 0.8).color(LILAC).peak(RukiaKit.glow(0.5)).owner(x).spawn();
			x.sound("block.pink_petals.break", 0.8, 0.6);
			x.sound("block.amethyst_block.break", 1.3, 0.5);
		});
		FxTimelines.start(t);
	}

	private static double param(EffectTimeline t, int i, double def) {
		return dev.minebleach.reiatsutest.core.fx.RukiaFxParams.param(t.params, i, def);
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

	private static void pulse(EffectTimeline x, Vec3d c, double R, int k, int pulses) {
		Random r = x.rng(50 + k);
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(c.x, c.y, c.z).size(R * 0.6, R * 2.4).sizeEase(FxMath.OC).life(0.28).curve(0.02, 0.8).color(PINK).peak(RukiaKit.glow(0.45)).owner(x).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(c.x, c.y, c.z).plane(0, 1, 0).size(0.5, R / 0.375).sizeEase(FxMath.OC).life(0.3).curve(0.02, 0.7).color(LILAC).peak(RukiaKit.glow(0.7)).owner(x).spawn();
		int n = RukiaKit.count(24);
		for (int i = 0; i < n; i++) {
			Vec3d d = sphere(r);
			FxParticles.spec(FxParticles.Kind.PETAL).at(c.x + d.x * R * 0.9, c.y + d.y * R * 0.9, c.z + d.z * R * 0.9).vel(d.x * 3.5, d.y * 3.5, d.z * 3.5).drag(0.93).life(14 + r.nextInt(8))
					.size(RukiaKit.size(0.22)).color(PETAL).colorTo(PINK).seed(i).spin((r.nextDouble() - 0.5) * 0.5).fade(0.4).owner(x).spawn();
		}
		x.shake(0.25 * (k == 0 ? 1.4 : 1.0), 0.18);
		x.soundAt("block.amethyst_block.chime", c, 1.0 + 0.12 * k, 0.55);
		x.soundAt("block.pink_petals.break", c, 1.2, 0.5);
	}
}
