package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.FxHash;
import dev.minebleach.reiatsutest.core.fx.PetalBladeMath;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import java.util.HashMap;
import java.util.Map;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.api.client.BetterCombatClientEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * The sword that the petals build in the hand of Byakuya (R12), clock and triggers. In the shikai the hand holds only the hilt, in the bankai nothing: a strike
 * (Better Combat {@code ATTACK_START}, or the vanilla swing of another player) or a block pulls petals together into the sword, it holds, and falls apart again
 * ({@link PetalBladeMath}). The sword itself is drawn by the item model ({@code ObjItemBakedModel#emitSwordForm}) with the film of the release dissolve played backwards,
 * so it follows the real arm in first and third person; this class only says how far the film is ({@link #frame}) and spawns the petals that fly in from around the owner.
 * Mode B (default, {@code petalBlade.mode} 1): the first strike or block builds the sword, it holds {@code petalBlade.hold} seconds from the hit moment, every further
 * strike or block restarts that time. Mode A ({@code petalBlade.mode} 0): the sword lives only for the strike ({@code petalBlade.holdA} s after the sweep, and while blocking).
 * It also keeps the swing clock that {@link PetalSwarmRenderer} uses to clear the shikai swarm out of the way of the sword ({@link #swingStrength}).
 *
 * <p>Keys ({@code run/fx_override.json}, hot): {@code petalBlade} 1/0, {@code petalBlade.mode}, {@code .hold}, {@code .holdA}, {@code .assemble}, {@code .disperse},
 * {@code .settle}, {@code .lead}, {@code .sweep}, {@code .petals} (inflying petals per build up), {@code .range}, {@code .upswing} (remote players). Nothing here has been seen in the
 * game (UNVERIFIED).
 */
public final class PetalBladeFx {
	private static final PetalBladeMath.Params PRM = new PetalBladeMath.Params();
	private static final Map<Integer, Run> RUNS = new HashMap<>();
	private static final String PINK = "#F9C8F6";
	private static final String LILAC = "#F3E4FF";

	/** Stats for the harness. */
	public static int strikes;
	public static int blocks;
	public static int builds;

	private PetalBladeFx() {
	}

	/** What the item model needs of one sword in this frame. */
	public static final class Frame {
		/** Film position 0..1 (0 = nothing, 1 = the complete sword). */
		public double form;
		/** How pink the sword is 0..1. */
		public double pink;
		/** Seconds of the film (the dissolve time plus the life of a fragment): the model plays it at {@code el = total * (1 - form)}. */
		public double total;
	}

	/** One sword of one player. */
	private static final class Run {
		final int id;
		final PetalBladeMath.Timeline tl = new PetalBladeMath.Timeline();
		boolean wasBlocking;
		long lastSwingTick = Long.MIN_VALUE / 2;
		int combo = -1;
		int serial;
		// last frame the model asked for (the glow source draws the same sword)
		boolean bankai;
		double form;
		double pink;
		long frameNanos;

		Run(int id) {
			this.id = id;
		}
	}

	public static void init() {
		BetterCombatClientEvents.ATTACK_START.register(PetalBladeFx::onLocalAttack);
		ClientTickEvents.END_CLIENT_TICK.register(PetalBladeFx::tick);
		FxGlowBatch.addSource(PetalBladeFx::emitGlow);
	}

	public static boolean enabled() {
		return FxTune.d("petalBlade", 1.0) > 0.5;
	}

	/** Reads the keys into the shared parameter set (cheap: a few map lookups). */
	private static void tune() {
		PetalBladeMath.Params p = PRM;
		p.holdMode = FxTune.d("petalBlade.mode", 1.0) > 0.5;
		p.hold = Math.max(0.1, FxTune.d("petalBlade.hold", 3.0));
		p.holdA = Math.max(0.0, FxTune.d("petalBlade.holdA", 0.25));
		p.assemble = Math.max(0.05, FxTune.d("petalBlade.assemble", 0.55));
		p.disperse = Math.max(0.05, FxTune.d("petalBlade.disperse", 0.90));
		p.settle = Math.max(0.01, FxTune.d("petalBlade.settle", 0.45));
		p.lead = Math.max(0.01, FxTune.d("petalBlade.lead", 0.25));
		p.sweep = FxTune.d("petalBlade.sweep", 0.12);
	}

	/** 0..1 how strongly the player strikes now (shikai and bankai alike); {@link PetalSwarmRenderer} clears the swarm out of the way of the sword with it. */
	public static double swingStrength(int entityId) {
		Run r = RUNS.get(entityId);
		return r == null ? 0.0 : r.tl.swingStrength(FxClock.now, PRM);
	}

	/**
	 * The sword of this player in this frame: false when there is none (not built, or the film is over). Moves the clock, so it may be called several times per frame
	 * (the item model asks once per hand render). {@code bankai}: the whole sword is built and it stays pink and glowing while it exists (as in the anime), in the shikai
	 * the pink eases into the normal blade ({@code petalBlade.settle} seconds after it has formed).
	 */
	public static boolean frame(int entityId, boolean bankai, Frame out) {
		Run r = RUNS.get(entityId);
		if (r == null || !enabled()) {
			return false;
		}
		tune();
		double now = FxClock.now;
		double form = r.tl.update(now, PRM);
		if (form <= 0.001) {
			return false;
		}
		out.form = form;
		out.pink = bankai && FxTune.d("petalBlade.bankaiPink", 1.0) > 0.5 ? 1.0 : r.tl.pink(now, PRM);
		out.total = Math.max(0.05, FxTune.d("release.dissolveTime", 0.85)) + Math.max(0.05, FxTune.d("release.shardLife", 0.28));
		r.bankai = bankai;
		r.form = form;
		r.pink = out.pink;
		r.frameNanos = System.nanoTime();
		return true;
	}

	/** An ability of the shikai (the swarm leaves for the attack or the barrier): the sword breaks up at once, whatever time was left; a strike right after builds it again. */
	public static void dispel(int entityId) {
		Run r = RUNS.get(entityId);
		if (r != null) {
			r.tl.dispel();
		}
	}

	/** True while this player has a sword (or its film) going: the item render then hands over its pose for the glow. */
	public static boolean wantsPose(int entityId) {
		Run r = RUNS.get(entityId);
		return r != null && r.tl.form > 0.01;
	}

	/** True when the local player's sword is (at least partly) built: the arm then takes the pose of a sword even in the bankai, where the hand item draws nothing. */
	public static boolean localFormed() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return false;
		}
		Run r = RUNS.get(mc.player.getId());
		return r != null && r.tl.form > 0.12;
	}

	// ------------------------------------------------------------------ the triggers

	private static void onLocalAttack(ClientPlayerEntity player, AttackHand hand) {
		try {
			if (hand.isOffHand() || !enabled() || !MeleeTrailFx.byakuyaMelee(player)) {
				return;
			}
			MinecraftClient mc = MinecraftClient.getInstance();
			float cooldown = 12f;
			try {
				float c = net.bettercombat.logic.PlayerAttackHelper.getAttackCooldownTicksCapped(player);
				cooldown = c > 0f ? c : 12f;
			} catch (RuntimeException | LinkageError e) {
				// the default stands
			}
			int upTicks;
			try {
				upTicks = ((MinecraftClient_BetterCombat) (Object) mc).getUpswingTicks();
			} catch (RuntimeException | LinkageError e) {
				upTicks = Math.round((float) hand.attack().upswing() * cooldown);
			}
			strike(player, Math.max(1, upTicks) * 0.05);
		} catch (RuntimeException e) {
			ReiatsuTest.LOGGER.warn("[petalblade] local attack failed: {}", e.toString());
		}
	}

	private static void strike(PlayerEntity p, double upSeconds) {
		tune();
		Run r = RUNS.computeIfAbsent(p.getId(), Run::new);
		boolean rising = r.tl.form < 0.5;
		r.tl.strike(FxClock.now, upSeconds, PRM);
		strikes++;
		if (rising) {
			builds++;
			burst(p, r);
		}
	}

	private static void tick(MinecraftClient mc) {
		if (mc.world == null || mc.player == null) {
			RUNS.clear();
			return;
		}
		if (!enabled()) {
			return;
		}
		tune();
		double range = FxTune.d("petalBlade.range", 48.0);
		double now = FxClock.now;
		long worldNow = mc.world.getTime();
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (p.squaredDistanceTo(mc.player) > range * range || !MeleeTrailFx.byakuyaMelee(p)) {
				Run gone = RUNS.remove(p.getId());
				if (gone != null) {
					gone.tl.form = 0;
				}
				continue;
			}
			// other players: the start of the vanilla swing stands in for the Better Combat event (UNVERIFIED, as for the melee trail)
			if (p != mc.player && p.handSwinging && p.handSwingTicks == 0 && p.preferredHand == Hand.MAIN_HAND) {
				Run r = RUNS.computeIfAbsent(p.getId(), Run::new);
				r.combo = worldNow - r.lastSwingTick < 30 ? r.combo + 1 : 0;
				r.lastSwingTick = worldNow;
				strike(p, FxTune.d("petalBlade.upswing", 0.28));
			}
			boolean blocking = ZanpakutoItem.isBlocking(p);
			Run r = RUNS.get(p.getId());
			if (blocking) {
				if (r == null) {
					r = new Run(p.getId());
					RUNS.put(p.getId(), r);
				}
				boolean start = !r.wasBlocking;
				if (start) {
					blocks++;
					if (r.tl.form < 0.5) {
						builds++;
						burst(p, r);
					}
				}
				r.tl.block(now, start, PRM);
				r.wasBlocking = true;
			} else if (r != null && r.wasBlocking) {
				r.tl.unblock(now, PRM);
				r.wasBlocking = false;
			}
			if (r != null && !r.wasBlocking && r.tl.form <= 0 && r.tl.swingStrength(now, PRM) <= 0 && now >= r.tl.holdUntil + PRM.lead) {
				RUNS.remove(p.getId());
			}
		}
	}

	// ------------------------------------------------------------------ the glow of the pink sword (after the grade)

	/**
	 * The glow of the sword that the petals built, as in the anime: a white hot core and a wide pink edge along the blade, a flare at the tip and a cross shaped flare
	 * at the guard. It follows the pose of the item render ({@link ShikaiDissolve#worldBlade}), is as strong as the blade is built and pink, and is weaker in the owner's
	 * first person ({@code petalBlade.fpGlow}). Keys: {@code petalBlade.glow}, {@code .glowWidth}, {@code .glowColor}, {@code .coreColor}.
	 */
	private static void emitGlow(FxGlowBatch.Emitter e) {
		if (RUNS.isEmpty() || !enabled()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) {
			return;
		}
		// A glow placed in the world from the captured hand pose slides off the blade when the camera does something the pose does not carry (view bobbing, hurt tilt, sprint
		// fov in the first person; the camera rotation of the third person). The glow of the sword is made of shells of the item model instead
		// (ObjItemBakedModel#emitFlatBloom), which always sit on the blade; this world glow is only for experiments: petalBlade.worldGlow 1.
		double glow = FxTune.d("petalBlade.glow", 1.0);
		if (glow <= 0.001 || FxTune.d("petalBlade.worldGlow", 0.0) < 0.5) {
			return;
		}
		double width = FxTune.d("petalBlade.glowWidth", 0.30);
		double fpGlow = FxTune.d("petalBlade.fpGlow", 0.6);
		float[] edge = FxMath.hex(FxTune.s("petalBlade.glowColor", "#FF8FD6"));
		float[] core = FxMath.hex(FxTune.s("petalBlade.coreColor", "#FFF1FB"));
		Camera cam = mc.gameRenderer.getCamera();
		Vec3d cp = cam.getPos();
		Quaternionf rot = cam.getRotation();
		double[] a = new double[3];
		double[] b = new double[3];
		long now = System.nanoTime();
		for (Run r : RUNS.values()) {
			if (r.form < 0.05 || now - r.frameNanos > 200_000_000L) {
				continue;
			}
			if (!ShikaiDissolve.worldBlade(r.id, cp, rot, a, b)) {
				continue;
			}
			boolean fp = r.id == mc.player.getId() && !FxClient.forceRemote() && mc.options.getPerspective().isFirstPerson();
			// as strong as the sword is built, and as pink: the bankai sword stays at 1, the shikai blade fades to its own colours
			double g = r.form * r.form * r.pink * glow * (fp ? fpGlow : 1.0);
			if (g < 0.005) {
				continue;
			}
			e.streak(GlowSprite.STREAK, a[0], a[1], a[2], b[0], b[1], b[2], width, edge[0], edge[1], edge[2], 0.55 * g);
			e.streak(GlowSprite.STREAK, a[0], a[1], a[2], b[0], b[1], b[2], width * 0.30, core[0], core[1], core[2], 0.85 * g);
			e.sprite(GlowSprite.STAR4, b[0], b[1], b[2], 0.55 * (fp ? 0.6 : 1.0), 0.0, core[0], core[1], core[2], 0.6 * g);
			e.sprite(GlowSprite.STAR4, a[0], a[1], a[2], 0.45 * (fp ? 0.6 : 1.0), 0.785, core[0], core[1], core[2], 0.5 * g);
			e.sprite(GlowSprite.GLOW_SOFT, a[0], a[1], a[2], 0.7 * (fp ? 0.6 : 1.0), 0.0, edge[0], edge[1], edge[2], 0.35 * g);
		}
	}

	// ------------------------------------------------------------------ the petals that fly in

	/**
	 * Petals that come from around the owner and are pulled into the hand while the sword is built: a pink swirl of the same petals the swarm and the melee trail use
	 * ({@link FxParticles}), aimed at the middle of the blade, so the gathering is visible from the third person and, a little, from the first.
	 */
	private static void burst(PlayerEntity p, Run r) {
		int n = (int) Math.round(FxTune.d("petalBlade.petals", 36) * Math.max(0.3, FxConfig.effectQuality));
		if (n <= 0 || FxConfig.photosensitiveSafe) {
			return;
		}
		double yaw = Math.toRadians(p.getYaw());
		double pitch = Math.toRadians(Math.max(-45f, Math.min(45f, p.getPitch())));
		Vec3d fwd = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3d target = p.getPos().add(0, p.getEyeHeight(p.getPose()) - 0.35, 0).add(fwd.multiply(0.8));
		long seed = FxHash.mix(p.getId() * 0x9E3779B1L + (++r.serial) * 0x632BE59BD9B4E019L);
		for (int i = 0; i < n; i++) {
			double a = FxHash.unit(seed, i, 1) * Math.PI * 2;
			double b = (FxHash.unit(seed, i, 2) - 0.5) * 2.2;
			double rad = 1.1 + 0.8 * FxHash.unit(seed, i, 3);
			double cx = target.x + Math.cos(a) * rad * Math.cos(b * 0.6);
			double cy = target.y + Math.sin(b) * rad * 0.8;
			double cz = target.z + Math.sin(a) * rad * Math.cos(b * 0.6);
			// towards the blade with the drag of the particle: the travel is v / 20 / (1 - drag), so v = distance * 20 * (1 - drag)
			double drag = 0.84;
			double k = 20.0 * (1.0 - drag) * 0.95;
			double vx = (target.x - cx) * k;
			double vy = (target.y - cy) * k;
			double vz = (target.z - cz) * k;
			int idx = i;
			double delay = 0.30 * FxHash.unit(seed, i, 4);
			double size = 0.16 + 0.10 * FxHash.unit(seed, i, 5);
			FxDelayed.after(delay, () -> FxParticles.spec(FxParticles.Kind.PETAL).at(cx, cy, cz).vel(vx, vy, vz).drag(drag).life(12).size(size).color(PINK).colorTo(LILAC)
					.bright(1.5).spin(((idx * 37) % 11 - 5) * 0.08).seed(idx * 7 + 3).fade(0.5).spawn());
		}
	}
}
