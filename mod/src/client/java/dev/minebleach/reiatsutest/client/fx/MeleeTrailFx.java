package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.MeleeTrailMath;
import dev.minebleach.reiatsutest.core.combat.SwingMotion;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.registry.ModComponents;
import dev.minebleach.reiatsutest.registry.ReleaseState;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.api.client.BetterCombatClientEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * The pink melee trail of the Byakuya shikai and bankai (r8): when the sword hits, a blade of pink petals and glow assembles in the air along the
 * path of the swing ({@link MeleeTrailMath}, at most {@link MeleeTrailMath#BUDGET} motes per swing, about a quarter of a second). Better Combat draws its own swing
 * trail which cannot be recoloured, so this is the mod's own effect, drawn with the existing FX batches ({@link FxParticles} petals, {@link FxGlowBatch} sprites).
 *
 * <p>Local player: the public Better Combat event {@code ATTACK_START} (a second listener next to the one of {@code FpMotion}, which is skipped while Better Combat
 * draws the first person strike) gives the animation name, the combo and the upswing ticks; the trail starts when the upswing ends, the moment of the hit.
 * Other players: Better Combat has no event for them, so the start of the vanilla swing animation ({@code handSwinging} with {@code handSwingTicks == 0},
 * polled every client tick) stands in; the profile then follows the combo order of the katana by the swings in a row and the upswing is the fixed
 * {@code fx.meleeTrail.upswing}. UNVERIFIED in game: whether Better Combat makes remote players swing the vanilla way, and how well the arcs follow its animations.
 *
 * <p>Dev switches ({@code run/fx_override.json}): {@code fx.meleeTrail} (1 on, 0 off), {@code fx.meleeTrail.scale} (reach, sizes and drift, default 1),
 * {@code fx.meleeTrail.reach} (blocks from the shoulder to the tip, default {@link MeleeTrailMath#REACH}), {@code fx.meleeTrail.glow} (intensity of the glow, 1),
 * {@code fx.meleeTrail.upswing} (seconds, remote players only, 0.28), {@code fx.meleeTrail.range} (furthest player that gets a trail, 48 blocks).
 */
public final class MeleeTrailFx {
	private static final String PINK = "#F9C8F6";
	private static final String LILAC = "#F3E4FF";
	/** Swings in a row of another player: last swing world tick and the combo index. */
	private static final Map<Integer, long[]> REMOTE = new HashMap<>();

	/** Statistics for the harness: swings that got a trail, motes spawned. */
	public static int swings;
	public static int motes;

	private MeleeTrailFx() {
	}

	public static void init() {
		BetterCombatClientEvents.ATTACK_START.register(MeleeTrailFx::onLocalAttack);
		ClientTickEvents.END_CLIENT_TICK.register(MeleeTrailFx::pollRemote);
	}

	/** {@code fx.meleeTrail} 1 (default) / 0; also off for the photosensitive safe mode. */
	public static boolean enabled() {
		return FxTune.d("fx.meleeTrail", 1.0) > 0.5 && !FxConfig.photosensitiveSafe;
	}

	/** True when the main hand holds a Byakuya zanpakuto in SHIKAI or BANKAI (the senbonzakura). */
	static boolean byakuyaMelee(PlayerEntity p) {
		ItemStack s = p.getMainHandStack();
		if (!(s.getItem() instanceof ZanpakutoItem z) || z.character() != CharacterId.BYAKUYA) {
			return false;
		}
		ReleaseState st = s.getOrDefault(ModComponents.RELEASE_STATE, ReleaseState.SEALED);
		return st == ReleaseState.SHIKAI || st == ReleaseState.BANKAI;
	}

	// ------------------------------------------------------------------ the triggers

	private static void onLocalAttack(ClientPlayerEntity player, AttackHand hand) {
		try {
			if (hand.isOffHand() || !enabled() || !byakuyaMelee(player)) {
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
			String anim = hand.attack() == null ? null : hand.attack().animation();
			int combo = hand.combo() == null ? 0 : hand.combo().current();
			schedule(player.getId(), SwingMotion.profileFor(anim, combo), Math.max(1, upTicks) * 0.05);
		} catch (RuntimeException e) {
			ReiatsuTest.LOGGER.warn("[meleetrail] local attack failed: {}", e.toString());
		}
	}

	private static void pollRemote(MinecraftClient mc) {
		if (mc.world == null || mc.player == null) {
			REMOTE.clear();
			return;
		}
		if (!enabled()) {
			return;
		}
		double range = FxTune.d("fx.meleeTrail.range", 48.0);
		long now = mc.world.getTime();
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (p == mc.player || !p.handSwinging || p.handSwingTicks != 0 || p.preferredHand != Hand.MAIN_HAND || !byakuyaMelee(p)) {
				continue;
			}
			if (p.squaredDistanceTo(mc.player) > range * range) {
				continue;
			}
			long[] st = REMOTE.computeIfAbsent(p.getId(), k -> new long[] {Long.MIN_VALUE / 2, -1});
			st[1] = now - st[0] < 30 ? st[1] + 1 : 0;
			st[0] = now;
			schedule(p.getId(), SwingMotion.profileFor(null, (int) st[1]), FxTune.d("fx.meleeTrail.upswing", 0.28));
		}
	}

	/** The trail starts when the upswing ends: {@code upSeconds} from now, at the position and view of the player then. */
	private static void schedule(int entityId, SwingMotion.Profile profile, double upSeconds) {
		swings++;
		long seed = (entityId * 31L + swings) * 0x9E3779B97F4A7C15L;
		FxDelayed.after(upSeconds, () -> spawn(entityId, profile, seed));
	}

	// ------------------------------------------------------------------ the effect

	private static void spawn(int entityId, SwingMotion.Profile profile, long seed) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || !enabled()) {
			return;
		}
		Entity e = mc.world.getEntityById(entityId);
		if (!(e instanceof PlayerEntity p) || !p.isAlive()) {
			return;
		}
		float td = mc.getRenderTickCounter().getTickDelta(true);
		Vec3d feet = p.getLerpedPos(td);
		double yaw = Math.toRadians(p.getYaw(td));
		double pitch = Math.toRadians(Math.max(-60f, Math.min(60f, p.getPitch(td))));
		// the view basis: forward, right (a player facing +z has -x on the right), up = right x forward
		double fx = -Math.sin(yaw) * Math.cos(pitch);
		double fy = -Math.sin(pitch);
		double fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double ux = -rz * fy;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy;
		double ox = feet.x;
		double oy = feet.y + p.getEyeHeight(p.getPose()) - 0.30;
		double oz = feet.z;
		double scale = FxTune.d("fx.meleeTrail.scale", 1.0);
		double reach = FxTune.d("fx.meleeTrail.reach", MeleeTrailMath.REACH);
		double glow = FxTune.d("fx.meleeTrail.glow", 1.0) * (FxConfig.reduceMotion ? 0.5 : 1.0);
		List<MeleeTrailMath.Mote> plan = MeleeTrailMath.plan(profile, seed, reach, scale);
		int i = 0;
		for (MeleeTrailMath.Mote m : plan) {
			double wx = ox + m.x() * rx + m.y() * ux + m.z() * fx;
			double wy = oy + m.y() * uy + m.z() * fy;
			double wz = oz + m.x() * rz + m.y() * uz + m.z() * fz;
			double vx = m.vx() * rx + m.vy() * ux + m.vz() * fx;
			double vy = m.vy() * uy + m.vz() * fy;
			double vz = m.vx() * rz + m.vy() * uz + m.vz() * fz;
			int k = i++;
			motes++;
			switch (m.kind()) {
				case GLOW -> FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(wx, wy, wz).size(m.size(), m.size() * 0.35).sizeEase(FxMath.OC).life(m.life()).delay(m.delay())
						.curve(0.012, 0.65).color(k % 3 == 0 ? LILAC : PINK).peak(0.8 * glow).spawn();
				case ACCENT -> FxGlowBatch.sprite(GlowSprite.STAR4).at(wx, wy, wz).size(m.size(), m.size() * 0.4).sizeEase(FxMath.OC).life(m.life()).delay(m.delay())
						.curve(0.01, 0.8).color(LILAC).peak(0.9 * glow).rot(k * 0.7, 2.0).spawn();
				case PETAL -> {
					int life = Math.max(2, (int) Math.ceil(m.life() * 20.0));
					Runnable r = () -> FxParticles.spec(FxParticles.Kind.PETAL).at(wx, wy, wz).vel(vx, vy, vz).drag(0.82).life(life).size(m.size()).color(PINK).colorTo(LILAC)
							.bright(1.5).spin(((k * 37) % 11 - 5) * 0.08).seed(k * 7 + 3).fade(0.5).spawn();
					if (m.delay() < 0.01) {
						r.run();
					} else {
						FxDelayed.after(m.delay(), r);
					}
				}
			}
		}
	}
}
