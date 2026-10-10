package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.SwarmMath;
import dev.minebleach.reiatsutest.core.state.ShikaiMode;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Byakuya shikai swarm: state, events and sounds (VFX_STORYBOARD 4.1 release / Chire, 4.2 attack mode, 4.3 barrier / dome).
 * The petals themselves are closed-form ({@link SwarmMath}) and drawn by {@link PetalSwarmRenderer}; this class owns the per
 * owner {@link State} (epoch, current mode, previous mode for the blend), turns the effect events 3, 30, 31 and 10, 11 into timelines
 * and mode changes, and keeps the swarm alive only while its anchor entity exists (late joiners get a settled swarm from the
 * anchor, FxScene).
 */
public final class SwarmFx {
	/** One swarm per owner. */
	public static final class State {
		public final int ownerId;
		public final long seed;
		public final int n;
		public final SwarmMath.Slots slots;
		/** FX clock value at the release (swarm time 0). */
		public final double epoch;
		public SwarmMath.Mode mode;
		public SwarmMath.Mode prev;
		/** Seal: FX clock value at which the swarm falls (or -1). */
		public double endAt = -1;
		public double lastSeen = FxClock.now;
		public boolean anchorSeen;
		public Vec3d lastFeet = Vec3d.ZERO;
		public double yaw;
		double nextStepSound;
		int lastHurt;
		// per frame data of the renderer (camera relative positions of the petals, for the glow source)
		public float[] px;
		public float[] py;
		public float[] pz;
		public float[] speed;
		public float[] glint;
		public int visible;
		/** Events of the HIT feedback and similar: owner interpolated feet of the last frame. */
		public double t;

		State(int ownerId, long seed, int n, double epoch) {
			this.ownerId = ownerId;
			this.seed = seed;
			this.n = n;
			this.slots = new SwarmMath.Slots(seed, n);
			this.epoch = epoch;
			this.mode = new SwarmMath.Mode(SwarmMath.Kind.IDLE, 0);
			px = new float[n];
			py = new float[n];
			pz = new float[n];
			speed = new float[n];
			glint = new float[n];
		}

		public double time() {
			return FxClock.now - epoch;
		}

		public void setMode(SwarmMath.Mode m) {
			prev = mode;
			mode = m;
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	/** Statistics for the harness. */
	public static int released;
	public static int attacks;
	public static int barriers;
	public static int hits;

	private SwarmFx() {
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

	/** Number of petals for this client: min(1000, maxPetals) x quality. */
	public static int petalCount() {
		return Math.max(50, (int) (Math.min(1000, FxConfig.maxPetals) * FxConfig.effectQuality));
	}

	private static Vec3d feetOf(int ownerId, Vec3d fallback) {
		Entity e = MinecraftClient.getInstance().world != null ? MinecraftClient.getInstance().world.getEntityById(ownerId) : null;
		return e != null ? e.getPos() : fallback;
	}

	// ------------------------------------------------------------------------------------------ release (id 3)

	public static void onRelease(EffectEventS2C e) {
		State st = new State(e.casterId(), e.seed() * 31L + 7, petalCount(), FxClock.now);
		st.mode = new SwarmMath.Mode(SwarmMath.Kind.RELEASE, 0);
		st.lastFeet = new Vec3d(e.x(), e.y(), e.z());
		st.yaw = Math.atan2(-e.dx(), e.dz());
		STATES.put(e.casterId(), st);
		released++;
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		Vec3d dir = new Vec3d(e.dx(), e.dy(), e.dz());
		EffectTimeline t = FxTimelines.create(3, e.seed(), pos, dir, e.params(), e.casterId(), FxClient.forceRemote());
		t.at(0.0, x -> {
			// the flash (0.40 lilac) and the cast_spell sound come from ReleaseFx (id 3)
			x.sound("block.pink_petals.break", 1.0, 0.7);
			double[] o = new double[3];
			double yaw = Math.toRadians(MinecraftClient.getInstance().player != null && e.casterId() == MinecraftClient.getInstance().player.getId()
					? MinecraftClient.getInstance().player.getYaw() : Math.toDegrees(Math.atan2(-dir.x, dir.z)));
			SwarmMath.hand(yaw, o);
			// the lilac flare at the habaki
			FxGlowBatch.sprite(GlowSprite.FLARE).at(pos.x + o[0], pos.y + o[1] + 0.1, pos.z + o[2]).size(1.6, 2.6).sizeEase(FxMath.OC).life(0.3).curve(0.02, 0.8)
					.color("#F3E4FF").peak(0.8).owner(x).spawn();
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(pos.x + o[0], pos.y + o[1] + 0.1, pos.z + o[2]).size(0.6, 1.8).sizeEase(FxMath.OC).life(0.35).curve(0.02, 0.8)
					.color("#F9C8F6").peak(0.8).owner(x).spawn();
		});
		t.at(0.1, x -> x.sound("block.pink_petals.break", 1.1, 0.6));
		t.at(0.2, x -> {
			x.sound("block.pink_petals.break", 0.9, 0.6);
			x.sound("entity.phantom.flap", 1.4, 0.25);
		});
		t.at(1.0, x -> x.sound("entity.phantom.flap", 1.5, 0.15));
		// the 60 glowing petals along the stream (life 12 ticks), spawned over the launch
		for (int k = 0; k < 10; k++) {
			final int kk = k;
			t.at(0.03 + 0.04 * k, x -> streamGlow(x, st, kk));
		}
		t.lifetime(1.2);
		FxTimelines.start(t);
	}

	private static void streamGlow(EffectTimeline x, State st, int k) {
		Random r = x.rng(40 + k);
		double yaw = st.yaw;
		double ts = st.time();
		double[] o = new double[3];
		Entity ow = MinecraftClient.getInstance().world.getEntityById(st.ownerId);
		Vec3d feet = ow != null ? ow.getPos() : st.lastFeet;
		for (int j = 0; j < 6; j++) {
			int i = Math.min(st.n - 1, (int) ((ts / 0.4) * st.n) - r.nextInt(Math.max(1, st.n / 6)));
			i = Math.max(0, i);
			SwarmMath.position(st.slots, i, ts, st.mode, yaw, o);
			FxGlowBatch.sprite(GlowSprite.PETAL_GLOW).at(feet.x + o[0], feet.y + o[1], feet.z + o[2]).size(0.45 + r.nextDouble() * 0.3).lifeTicks(12)
					.color("#F9C8F6").peak(0.8).twinkle().rot(r.nextDouble() * 6.28, (r.nextDouble() - 0.5) * 3).owner(x).spawn();
		}
	}

	// ------------------------------------------------------------------------------------------ attack mode (id 30)

	public static void onAttack(EffectEventS2C e) {
		State st = STATES.get(e.casterId());
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		if (st == null) {
			st = lateState(e.casterId(), e.seed(), pos);
		}
		float[] p = e.params();
		Vec3d feet = feetOf(e.casterId(), pos);
		Vec3d aimWorld = p.length >= 3 ? new Vec3d(p[0], p[1], p[2]) : feet.add(0, 1.2, 0);
		double t = st.time();
		SwarmMath.Mode m = new SwarmMath.Mode(SwarmMath.Kind.ATTACK, t).aim(aimWorld.x - feet.x, aimWorld.y - feet.y, aimWorld.z - feet.z);
		st.setMode(m);
		attacks++;
		EffectTimeline tl = FxTimelines.create(30, e.seed(), pos, new Vec3d(e.dx(), e.dy(), e.dz()), e.params(), e.casterId(), FxClient.forceRemote());
		tl.at(0.0, x -> x.sound("entity.player.attack.sweep", 0.9, 0.8));
		tl.at(0.2, x -> {
			x.sound("entity.breeze.wind_burst", 1.1, 0.8);
			x.sound("entity.phantom.swoop", 1.6, 0.4);
		});
		tl.at(0.5, x -> {
			x.shake(FxTune.d("swarm.attackShake", 0.3), 0.2);
			x.sound("entity.player.attack.crit", 1.0, 0.6);
		});
		tl.at(0.6, x -> x.sound("entity.player.attack.crit", 1.1, 0.6));
		tl.at(0.7, x -> x.sound("entity.player.attack.crit", 1.2, 0.6));
		// cutting core: glow at the aim and a sound every 0.25 s, 30 petal sparks per second
		tl.at(0.45, x -> FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(aimWorld.x, aimWorld.y, aimWorld.z).size(1.6, 3.2).sizeEase(FxMath.OC).life(1.15)
				.curve(0.3, 0.45).color("#F9C8F6").peak(FxTune.d("swarm.coreGlow", 0.35)).owner(x).spawn());
		for (int k = 0; k < 4; k++) {
			tl.at(1.0 + 0.25 * k, x -> {
				x.sound("block.pink_petals.break", 1.0 + 0.1 * (x.rng(5).nextDouble() - 0.5), 0.5);
				sparks(x, aimWorld, 8);
			});
		}
		tl.at(1.5, x -> x.sound("block.pink_petals.step", 1.0, 0.4));
		tl.lifetime(2.4);
		FxTimelines.start(tl);
	}

	private static void sparks(EffectTimeline x, Vec3d c, int n) {
		Random r = x.rng(77);
		for (int i = 0; i < n; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double s = 1.5 + r.nextDouble() * 3.0;
			FxParticles.spec(FxParticles.Kind.PETAL).at(c.x + (r.nextDouble() - 0.5), c.y + (r.nextDouble() - 0.5), c.z + (r.nextDouble() - 0.5)).vel(Math.cos(a) * s, r.nextDouble() * 2.0, Math.sin(a) * s)
					.drag(0.92).gravity(0.4).life(20).size(0.28).color("#F9C8F6").colorTo("#E5A4DC").spin((r.nextDouble() - 0.5) * 0.5).seed(r.nextInt(4)).owner(x).spawn();
		}
	}

	/** S5 HIT feedback of the envelope: 6 star glints and 4 petals on every entity that was really damaged. */
	public static void onHit(int[] ids) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		Random r = new Random(ids.length * 31L + 5);
		for (int id : ids) {
			Entity en = mc.world.getEntityById(id);
			if (en == null) {
				continue;
			}
			hits++;
			Vec3d c = en.getPos().add(0, en.getHeight() * 0.55, 0);
			for (int i = 0; i < 6; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double s = 2 + r.nextDouble() * 3;
				FxGlowBatch.sprite(GlowSprite.STAR4).at(c.x, c.y, c.z).vel(Math.cos(a) * s, 1 + r.nextDouble() * 2, Math.sin(a) * s).drag(0.9).lifeTicks(10).size(0.4 + r.nextDouble() * 0.4)
						.color("#FFE9FB").peak(0.9).twinkle().spawn();
			}
			for (int i = 0; i < 4; i++) {
				double a = r.nextDouble() * Math.PI * 2;
				double s = 1.5 + r.nextDouble() * 2.5;
				FxParticles.spec(FxParticles.Kind.PETAL).at(c.x, c.y, c.z).vel(Math.cos(a) * s, 1 + r.nextDouble() * 2, Math.sin(a) * s).drag(0.92).gravity(0.5).life(20).size(0.26)
						.color("#F9C8F6").seed(r.nextInt(4)).spawn();
			}
		}
	}

	// ------------------------------------------------------------------------------------------ barrier (id 31)

	public static void onBarrier(EffectEventS2C e) {
		State st = STATES.get(e.casterId());
		Vec3d pos = new Vec3d(e.x(), e.y(), e.z());
		if (st == null) {
			st = lateState(e.casterId(), e.seed(), pos);
		}
		st.setMode(new SwarmMath.Mode(SwarmMath.Kind.BARRIER, st.time()));
		barriers++;
		EffectTimeline tl = FxTimelines.create(31, e.seed(), pos, new Vec3d(e.dx(), e.dy(), e.dz()), e.params(), e.casterId(), FxClient.forceRemote());
		tl.at(0.0, x -> x.sound("block.beacon.power_select", 1.1, 0.6));
		tl.at(0.3, x -> x.sound("item.shield.block", 0.8, 0.7));
		tl.at(0.6, x -> x.sound("block.amethyst_block.resonate", 0.7, 0.3));
		tl.at(5.0, x -> x.sound("block.pink_petals.step", 0.9, 0.5));
		tl.lifetime(5.6);
		FxTimelines.start(tl);
	}

	// ------------------------------------------------------------------------------------------ seal (ids 10, 11) and bankai (4)

	/** The swarm falls with gravity and fades within 0.5 s (2.3). */
	public static void end(int ownerId) {
		State st = STATES.get(ownerId);
		if (st != null && st.endAt < 0) {
			st.endAt = FxClock.now;
		}
	}

	/** The Byakuya bankai drops the sword: the swarm is gone at once (the petals belong to the blades now). */
	public static void endNow(int ownerId) {
		STATES.remove(ownerId);
	}

	// ------------------------------------------------------------------------------------------ anchors (late join)

	/** A swarm of an owner that was released before this client saw it: settled, in orbit. */
	public static State lateState(int ownerId, long seed, Vec3d feet) {
		State st = new State(ownerId, seed * 31L + 7, petalCount(), FxClock.now - 20.0);
		st.lastFeet = feet;
		STATES.put(ownerId, st);
		return st;
	}

	// ------------------------------------------------------------------------------------------ per frame / per tick

	/** Once per frame at WorldRenderEvents.START (after the clock step): mode transitions, expiry. */
	public static void frame(MinecraftClient mc) {
		if (STATES.isEmpty()) {
			return;
		}
		Iterator<State> it = STATES.values().iterator();
		while (it.hasNext()) {
			State st = it.next();
			double t = st.time();
			if (st.endAt >= 0 && FxClock.now - st.endAt > 0.65) {
				it.remove();
				continue;
			}
			SwarmMath.Mode m = st.mode;
			switch (m.kind) {
				case ATTACK -> {
					if (t - m.t0 > SwarmMath.ATTACK_RETURN_END + 0.2) {
						st.mode = new SwarmMath.Mode(SwarmMath.Kind.IDLE, t);
						st.prev = null;
					}
				}
				case BARRIER -> {
					if (Double.isInfinite(m.collapseAt)) {
						boolean serverOver = false;
						Entity ow = mc.world != null ? mc.world.getEntityById(st.ownerId) : null;
						if (ow != null && ow.hasAttached(ModAttachments.ZANPAKUTO)) {
							ZanpakutoData z = ow.getAttached(ModAttachments.ZANPAKUTO);
							serverOver = z != null && z.mode() != ShikaiMode.BARRIER;
						}
						if ((serverOver && t - m.t0 > 0.6) || t - m.t0 > 5.0) {
							m.collapseAt = t;
							FxSound.play(null, "block.pink_petals.step", st.lastFeet.x, st.lastFeet.y + 1, st.lastFeet.z, 0.9, 0.5);
						}
					} else if (t - m.collapseAt > 1.0) {
						st.mode = new SwarmMath.Mode(SwarmMath.Kind.IDLE, t);
						st.prev = null;
					}
				}
				case RELEASE -> {
					if (t - m.t0 > 1.2) {
						st.mode = new SwarmMath.Mode(SwarmMath.Kind.IDLE, t);
						st.prev = null;
					}
				}
				default -> { }
			}
			if (st.prev != null && t - st.mode.t0 > 0.3) {
				st.prev = null;
			}
		}
	}

	/** Client tick: idle sounds, barrier hit reaction. */
	public static void tick(MinecraftClient mc) {
		if (STATES.isEmpty() || mc.world == null || mc.isPaused() || FxClock.frozen) {
			return;
		}
		Random r = new Random();
		for (State st : STATES.values()) {
			Entity ow = mc.world.getEntityById(st.ownerId);
			if (ow == null) {
				continue;
			}
			if (st.endAt < 0 && st.mode.kind == SwarmMath.Kind.IDLE && FxClock.now >= st.nextStepSound && mc.player != null && mc.player.distanceTo(ow) < 24) {
				st.nextStepSound = FxClock.now + 0.5 + r.nextDouble() * 0.3;
				FxSound.play(null, "block.pink_petals.step", ow.getX(), ow.getY() + 1.0, ow.getZ(), 1.2 + r.nextDouble() * 0.2, 0.3);
			}
			if (ow instanceof LivingEntity le) {
				if (st.mode.kind == SwarmMath.Kind.BARRIER && Double.isInfinite(st.mode.collapseAt) && le.hurtTime > st.lastHurt) {
					barrierHit(st, le, r);
				}
				st.lastHurt = le.hurtTime;
			}
		}
	}

	private static void barrierHit(State st, LivingEntity owner, Random r) {
		Vec3d feet = owner.getPos();
		Entity attacker = owner.getAttacker();
		double dx;
		double dz;
		if (attacker != null) {
			dx = attacker.getX() - feet.x;
			dz = attacker.getZ() - feet.z;
		} else {
			double a = r.nextDouble() * Math.PI * 2;
			dx = Math.cos(a);
			dz = Math.sin(a);
		}
		double l = Math.max(1e-3, Math.hypot(dx, dz));
		dx /= l;
		dz /= l;
		Vec3d c = new Vec3d(feet.x + dx * 3.0, feet.y + 1.2, feet.z + dz * 3.0);
		for (int i = 0; i < 20; i++) {
			double s = 2 + r.nextDouble() * 4;
			FxParticles.spec(FxParticles.Kind.PETAL).at(c.x, c.y + (r.nextDouble() - 0.5), c.z).vel(dx * s + (r.nextDouble() - 0.5) * 3, r.nextDouble() * 2, dz * s + (r.nextDouble() - 0.5) * 3)
					.drag(0.92).gravity(0.4).life(20).size(0.28).color("#F9C8F6").seed(r.nextInt(4)).spawn();
		}
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(c.x, c.y, c.z).plane(dx, 0, dz).size(2.6).life(0.25).curve(0.02, 0.8).color("#F3E4FF").peak(0.5).spawn();
		FxSound.play(null, "item.shield.block", c.x, c.y, c.z, 1.1, 0.8);
		FxSound.play(null, "block.pink_petals.break", c.x, c.y, c.z, 1.0, 0.6);
		ScreenFx.shake(0.2, 0.2, 1.0);
		ReiatsuTest.LOGGER.info("[fx] barrier hit reaction");
	}
}
