package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilityRequest;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.RequestSource;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.state.TransitionResult;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * {@code /reiatsu dev barrier_check} (mech2 task 1): the in-world check of the Byakuya barrier on the player who runs it. It goes
 * through the real hooks, so it proves the mixins are applied: damage through {@code LivingEntity.damage} (the ALLOW_DAMAGE hook),
 * status effects through {@code addStatusEffect} (the {@code canHaveStatusEffect} mixin), the Rukia ice and the knockback of our
 * abilities through {@link Targeting}. A positive control after the barrier is gone shows each of them lands again, so a pass is
 * not just "nothing works at all". Survival only (Creative takes no damage).
 */
public final class BarrierCheck {
	/** Failures of the last run, -1 before any run; read by the harness. */
	public static volatile int lastFailures = -1;
	public static volatile int lastChecks;

	private BarrierCheck() {
	}

	private static final List<RegistryEntry<StatusEffect>> CONTROL = List.of(StatusEffects.SLOWNESS, StatusEffects.WEAKNESS,
			StatusEffects.NAUSEA, StatusEffects.BLINDNESS, StatusEffects.LEVITATION, StatusEffects.MINING_FATIGUE, StatusEffects.DARKNESS);

	public static List<String> run(ServerPlayerEntity p) {
		List<String> out = new ArrayList<>();
		int[] fails = {0};
		int[] total = {0};
		StateMachine sm = ZanpakutoManager.machine(p);
		if (sm == null) {
			lastFailures = 1;
			return List.of("FAIL no session");
		}
		if (p.isCreative() || p.isSpectator()) {
			lastFailures = 1;
			return List.of("FAIL switch to survival first (creative takes no damage)");
		}
		// a clean Byakuya shikai with a full bar, no barrier yet
		ZanpakutoManager.applyExternal(p, sm.devSetState(ZanpakutoState.SHIKAI, CharacterId.BYAKUYA));
		sm.devSetReiatsu(1000);
		sm.devClearCooldowns();
		clean(p);
		boolean idleOk = !sm.barrierActive();
		check(out, fails, total, "no barrier before the cast", idleOk, "mode " + sm.shikaiMode());

		// control before the barrier: the same calls land (positive control)
		float magic10Before = lose(p, 10.0F);
		check(out, fails, total, "control: 10 magic damage without barrier costs 10 HP", Math.abs(magic10Before - 10.0F) < 0.05F, "lost " + magic10Before);
		clean(p);
		boolean slowLands = p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
		check(out, fails, total, "control: slowness lands without barrier", slowLands && p.hasStatusEffect(StatusEffects.SLOWNESS), "added " + slowLands);
		clean(p);
		Targeting.chill(p, p, 2, 60);
		int frozenBefore = p.getFrozenTicks();
		check(out, fails, total, "control: freezing ticks rise without barrier", frozenBefore > 0, "frozen " + frozenBefore);
		clean(p);
		Vec3d v0 = p.getVelocity();
		Targeting.knockback(p, p.getPos().add(1.0, 0.0, 0.0), 1.5);
		boolean pushed = p.getVelocity().squaredDistanceTo(v0) > 1.0E-4;
		check(out, fails, total, "control: knockback moves the target without barrier", pushed, "v " + p.getVelocity());
		clean(p);

		// cast the barrier through the real machine and event path
		TransitionResult r = sm.cast(new AbilityRequest(AbilityId.MODE_BARRIER, RequestSource.KEY, StateMachine.SERVER_SEQ, CharacterId.BYAKUYA));
		ZanpakutoManager.applyExternal(p, r.events());
		check(out, fails, total, "barrier cast accepted and active", r.ok() && sm.barrierActive() && ZanpakutoManager.barrierControlImmune(p),
				"code " + r.code() + " mode " + sm.shikaiMode());

		float lost = lose(p, 10.0F);
		check(out, fails, total, "10 magic damage costs 2 HP (80 percent cut)", Math.abs(lost - 2.0F) < 0.05F, "lost " + lost);
		clean(p);
		float lostGeneric = loseBy(p, p.getServerWorld().getDamageSources().generic(), 10.0F);
		check(out, fails, total, "10 generic damage costs at most 2 HP after armor", lostGeneric <= 2.05F, "lost " + lostGeneric);
		clean(p);

		for (RegistryEntry<StatusEffect> e : CONTROL) {
			String id = e.getKey().map(k -> k.getValue().getPath()).orElse("?");
			boolean added = p.addStatusEffect(new StatusEffectInstance(e, 100, 2));
			check(out, fails, total, "barrier refuses " + id, !added && !p.hasStatusEffect(e), "added " + added);
			p.removeStatusEffect(e);
		}
		boolean speedOk = p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0));
		check(out, fails, total, "barrier lets a non-control effect (speed) through", speedOk, "added " + speedOk);
		clean(p);

		int refusedBefore = ZanpakutoManager.barrierRefusals();
		p.setFrozenTicks(0);
		Targeting.chill(p, p, 3, 80);
		check(out, fails, total, "Rukia ice (Targeting.chill) leaves freezing ticks at 0", p.getFrozenTicks() == 0, "frozen " + p.getFrozenTicks());
		Vec3d v1 = p.getVelocity();
		Targeting.knockback(p, p.getPos().add(1.0, 0.0, 0.0), 1.5);
		check(out, fails, total, "knockback of our abilities does not move the caster", p.getVelocity().squaredDistanceTo(v1) < 1.0E-8, "v " + p.getVelocity());
		check(out, fails, total, "the refusals were counted ([barrier] log)", ZanpakutoManager.barrierRefusals() > refusedBefore, "count " + ZanpakutoManager.barrierRefusals());
		clean(p);

		// the barrier ends: the same things land again
		ZanpakutoManager.applyExternal(p, sm.devSetState(ZanpakutoState.SHIKAI, CharacterId.BYAKUYA));
		check(out, fails, total, "after the barrier slowness lands again",
				!sm.barrierActive() && p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2)), "mode " + sm.shikaiMode());
		clean(p);
		float after = lose(p, 10.0F);
		check(out, fails, total, "after the barrier 10 magic damage costs 10 HP", Math.abs(after - 10.0F) < 0.05F, "lost " + after);
		clean(p);

		lastFailures = fails[0];
		lastChecks = total[0];
		String summary = String.format(Locale.ROOT, "barrier_check: %d checks, %d failed", total[0], fails[0]);
		out.add(summary);
		ReiatsuTest.LOGGER.info("[barrier-check] {}", summary);
		return out;
	}

	private static float lose(ServerPlayerEntity p, float amount) {
		return loseBy(p, p.getServerWorld().getDamageSources().magic(), amount);
	}

	private static float loseBy(ServerPlayerEntity p, DamageSource src, float amount) {
		p.setHealth(p.getMaxHealth());
		p.timeUntilRegen = 0;
		float before = p.getHealth() + p.getAbsorptionAmount();
		p.damage(src, amount);
		float lost = before - (p.getHealth() + p.getAbsorptionAmount());
		p.timeUntilRegen = 0;
		p.setHealth(p.getMaxHealth());
		return lost;
	}

	/** Resets what the checks touch, but not the barrier itself. */
	private static void clean(ServerPlayerEntity p) {
		p.clearStatusEffects();
		p.setFrozenTicks(0);
		p.setVelocity(Vec3d.ZERO);
		p.timeUntilRegen = 0;
		p.setHealth(p.getMaxHealth());
	}

	private static void check(List<String> out, int[] fails, int[] total, String what, boolean ok, String detail) {
		total[0]++;
		if (!ok) {
			fails[0]++;
		}
		String line = (ok ? "PASS " : "FAIL ") + what + (ok ? "" : " (" + detail + ")");
		out.add(line);
		ReiatsuTest.LOGGER.info("[barrier-check] {}", line);
	}
}
