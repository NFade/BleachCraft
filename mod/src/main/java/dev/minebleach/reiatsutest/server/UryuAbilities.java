package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import dev.minebleach.reiatsutest.core.uryu.Sternenstaub;
import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import dev.minebleach.reiatsutest.server.uryu.UryuManager;
import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Server effects of Uryu Ishida's abilities (design/URYU.md). Called by {@link AbilityExecutor#run} once per phase, only while the
 * cast is still valid; every ability has one phase at tick 0 and the timed parts (the pentagon, the barrage, the dash, the arrows)
 * live in {@link UryuManager}, which also owns the Focus counter. Numbers: {@code core.uryu.UryuBalance}. Two abilities are aimed at their
 * release (Licht Regen 0.6 s, Sternenstaub 0.8 s after the key press, S-U1): {@link #castStart} tells the clients about the key press
 * (effect 78, S-U4) and {@link #releaseGate} lets the Sternenstaub fizzle when its Focus was lost during the charge.
 */
final class UryuAbilities {
	private UryuAbilities() {
	}

	/**
	 * S-U4: the key press of an ability that is aimed at its release. The effect event of the ability itself comes at the release, so the
	 * clients get this one now: effect {@link EffectIds#URYU_CAST_START} with the pre-aim point (the block ray of this moment; for the
	 * Sternenstaub the end of the 40 block look ray), the effect id of the ability to come and the seconds until its release. Visual only.
	 */
	static void castStart(ServerPlayerEntity p, CastContext ctx) {
		if (ctx.ability != AbilityId.LICHT_REGEN && ctx.ability != AbilityId.STERNENSTAUB) {
			return;
		}
		Vec3d pre = ctx.ability == AbilityId.LICHT_REGEN ? ctx.aim : UryuTargets.rayPoint(p, ctx.eye, ctx.dir, UryuBalance.STAR_LENGTH);
		float seconds = ctx.spec.phaseOffsets().get(ctx.spec.releasePhase()) / 20f;
		ServerFx.uryuCastStart(p, ctx.seed, pre, ctx.spec.effectId(), seconds);
	}

	/**
	 * S-U1: asked at the release phase of an aimed-at-release ability BEFORE its effect event goes out. False = the cast fizzled and nothing
	 * else happens. Only the Sternenstaub can: it needs the full Focus at the key press ({@code UryuManager.allowsCast}) and AGAIN here, 0.8 s
	 * later; if the Focus was lost (the player moved) the reiatsu stays spent, the cooldown becomes half of the full one counted from now, and
	 * the player is told.
	 */
	static boolean releaseGate(ServerPlayerEntity p, AbilityId ability) {
		if (ability != AbilityId.STERNENSTAUB) {
			return true;
		}
		int stacks = UryuManager.focusOf(p);
		if (Sternenstaub.releases(stacks)) {
			return true;
		}
		ZanpakutoManager.Session s = ZanpakutoManager.session(p);
		int cooldown = Sternenstaub.fizzleCooldown(s.sm.config().spec(ability).cooldownTicks());
		s.sm.setCooldown(ability, cooldown);
		s.lastCooldownKeys = null; // the cooldown attachment only syncs when the set of cooldowns changes: force it
		ZanpakutoManager.apply(p, s, List.of(), null);
		p.sendMessage(Text.translatable("message.reiatsu_test.uryu.star_fizzled"), true);
		ReiatsuTest.LOGGER.info("[uryu] {} sternenstaub fizzled at the release: focus {}/{}, cooldown {} ticks", p.getName().getString(), stacks,
				UryuBalance.STAR_FOCUS, cooldown);
		return false;
	}

	static void run(ServerPlayerEntity p, AbilityId ability, int phase, CastContext ctx) {
		switch (ability) {
			case HEILIG_PFEIL -> UryuManager.focusShot(p);
			case SPRENGER -> UryuManager.sprenger(p, ctx.aim);
			case SEELE_SCHNEIDER -> UryuManager.seeleSchneider(p);
			case WING_BARRAGE -> UryuManager.wingBarrage(p);
			case LICHT_REGEN -> UryuManager.lichtRegen(p, ctx.aim);
			case STERNENSTAUB -> UryuManager.sternenstaub(p);
			default -> throw new IllegalArgumentException("not an Uryu ability: " + ability);
		}
	}
}
