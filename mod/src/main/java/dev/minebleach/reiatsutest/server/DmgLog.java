package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.combat.HitRules;
import java.util.Locale;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The {@code [dmg]} server log (dmg_rage_round3): one INFO line per ability hit, with the outcome and the reason a hit did not
 * land, and one line per player the area query left out. Also carries the "current ability" label (set around the executors, so
 * {@link Targeting#hurt} does not need it as a parameter) and the veto note that {@code ZanpakutoManager.allowDamage} leaves
 * when it cancels a hit.
 *
 * <p>Server thread only, so plain statics are fine.
 */
public final class DmgLog {
	/** Master switch of the INFO lines (the veto bookkeeping always runs). */
	public static volatile boolean enabled = true;

	private static String ability = "?";
	private static int vetoEntity = Integer.MIN_VALUE;
	private static HitRules.Outcome veto;

	private DmgLog() {
	}

	static void begin(String name) {
		ability = name;
	}

	static void end() {
		ability = "?";
	}

	static String ability() {
		return ability;
	}

	/** Called by the damage barrier when it cancels or lowers a hit on {@code e}. */
	static void noteVeto(LivingEntity e, HitRules.Outcome why) {
		vetoEntity = e.getId();
		veto = why;
	}

	static void clearVeto() {
		vetoEntity = Integer.MIN_VALUE;
		veto = null;
	}

	static HitRules.Outcome vetoFor(LivingEntity e) {
		return e.getId() == vetoEntity ? veto : null;
	}

	static void hit(ServerPlayerEntity caster, LivingEntity target, float asked, float lost, HitRules.Outcome outcome) {
		if (!enabled) {
			return;
		}
		ReiatsuTest.LOGGER.info("[dmg] {} {} -> {} asked={} lost={} result={}", ability, caster.getName().getString(),
				target.getName().getString(), fmt(asked), fmt(lost), outcome);
	}

	/** A player (not a mob: those would flood the log) that the area query refused. */
	static void refused(ServerPlayerEntity caster, LivingEntity target, HitRules.Refusal why) {
		if (!enabled) {
			return;
		}
		ReiatsuTest.LOGGER.info("[dmg] {} {} -> {} refused={}", ability, caster.getName().getString(), target.getName().getString(), why);
	}

	private static String fmt(float v) {
		return String.format(Locale.ROOT, "%.1f", v);
	}
}
