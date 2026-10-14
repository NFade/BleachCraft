package dev.minebleach.reiatsutest.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.ModItems;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * {@code /reiatsu} (operators, dev/testing): set the bar, force a state, clear cooldowns, inspect, toggle PvP for abilities.
 */
public final class ReiatsuCommand {
	private static final SimpleCommandExceptionType NO_SESSION = new SimpleCommandExceptionType(Text.literal("no zanpakuto session for this player"));
	private static final SimpleCommandExceptionType BAD_ARG = new SimpleCommandExceptionType(Text.literal("unknown state or character"));

	private ReiatsuCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher));
	}

	private static void build(CommandDispatcher<ServerCommandSource> d) {
		d.register(CommandManager.literal("reiatsu")
				.requires(src -> src.hasPermissionLevel(2))
				.then(CommandManager.literal("info").executes(c -> info(c.getSource())))
				.then(CommandManager.literal("voice").executes(c -> voice(c.getSource())))
				// /reiatsu reload: migrate config/reiatsu_test.json and re-read the sections that can change on a running server (R11 T0)
				.then(CommandManager.literal("reload").executes(c -> {
					int n = ConfigReload.run();
					c.getSource().sendFeedback(() -> Text.literal("config reloaded: " + n + " sections (voice and frost need a restart)"), true);
					return n;
				}))
				.then(CommandManager.literal("full").executes(c -> setPoints(c.getSource(), 100.0)))
				.then(CommandManager.literal("set")
						.then(CommandManager.argument("points", DoubleArgumentType.doubleArg(0.0, 100.0))
								.executes(c -> setPoints(c.getSource(), DoubleArgumentType.getDouble(c, "points")))))
				.then(CommandManager.literal("state")
						.then(CommandManager.argument("state", StringArgumentType.word())
								.suggests((c, b) -> CommandSource.suggestMatching(List.of("sealed", "shikai", "bankai"), b))
								.executes(c -> setState(c.getSource(), StringArgumentType.getString(c, "state"), null))
								.then(CommandManager.argument("character", StringArgumentType.word())
										.suggests((c, b) -> CommandSource.suggestMatching(List.of("rukia", "byakuya", "gin", "uryu"), b))
										.executes(c -> setState(c.getSource(), StringArgumentType.getString(c, "state"),
												StringArgumentType.getString(c, "character"))))))
				// /reiatsu frost status | start | rollback: the Rukia bankai territory freeze (start = a field at your feet, rollback = restore all now)
				.then(CommandManager.literal("frost")
						.then(CommandManager.literal("status").executes(c -> {
							c.getSource().sendFeedback(() -> Text.literal(FrostField.status()), false);
							return 1;
						}))
						.then(CommandManager.literal("start").executes(c -> {
							boolean ok = FrostField.start(c.getSource().getPlayerOrThrow(), true);
							c.getSource().sendFeedback(() -> Text.literal(ok ? "frost field started at your feet (it ends with the 45 s cap or /reiatsu frost rollback)" : "frost is off"), true);
							return ok ? 1 : 0;
						}))
						.then(CommandManager.literal("rollback").executes(c -> {
							int n = FrostField.rollbackNow(c.getSource().getServer());
							c.getSource().sendFeedback(() -> Text.literal("frost rolled back: " + n + " block(s) restored"), true);
							return n;
						})))
				.then(CommandManager.literal("cooldowns").then(CommandManager.literal("clear").executes(c -> clearCooldowns(c.getSource()))))
				.then(CommandManager.literal("pvp")
						.then(CommandManager.argument("enabled", BoolArgumentType.bool()).executes(c -> {
							Tuning.affectPlayers = BoolArgumentType.getBool(c, "enabled");
							c.getSource().sendFeedback(() -> Text.literal("abilities affect players: " + Tuning.affectPlayers), true);
							return 1;
						})))
				.then(CommandManager.literal("dev")
						// /reiatsu dev dummy_hit <targets> [hp]: hits the targets as if by an ability of the caster; the result and the
						// reason (creative, parry, barrier, iframes, team, pvp off ...) go to the chat and to the [dmg] server log
						.then(CommandManager.literal("dummy_hit")
								.then(CommandManager.argument("targets", EntityArgumentType.entities())
										.executes(c -> dummyHit(c.getSource(), EntityArgumentType.getEntities(c, "targets"), 2.0F))
										.then(CommandManager.argument("hp", FloatArgumentType.floatArg(0.1F, 100.0F))
												.executes(c -> dummyHit(c.getSource(), EntityArgumentType.getEntities(c, "targets"),
														FloatArgumentType.getFloat(c, "hp"))))))
						// /reiatsu dev reset: the dev reset of everything: back to SEALED through the same machine path as a seal (so the one
						// bankai cleanup runs if a bankai was on), cooldowns cleared, bar full
						.then(CommandManager.literal("reset").executes(c -> {
							ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
							StateMachine sm = machine(c.getSource());
							ZanpakutoManager.applyExternal(p, sm.devSetState(ZanpakutoState.SEALED, CharacterId.NONE));
							sm.devClearCooldowns();
							sm.devSetReiatsu(1000);
							ZanpakutoManager.applyExternal(p, List.of());
							c.getSource().sendFeedback(() -> Text.literal("dev reset: sealed, cooldowns cleared, bar full"), true);
							return 1;
						}))
						// /reiatsu dev bankai_audit: what is still alive of a bankai on the server (anchors, Rukia field anchor, frost fields, scheduled
						// bankai phases). All four are 0 once the bankai is over; the client logs its own [bankai-audit] line a few seconds after each end
						.then(CommandManager.literal("bankai_audit").executes(c -> {
							ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
							int[] l = ZanpakutoManager.bankaiLeftovers(p);
							int sum = l[0] + l[1] + l[2] + l[3];
							String line = "bankai_audit: anchors=" + l[0] + " rukiaFieldAnchors=" + l[1] + " frostFields=" + l[2] + " scheduledBankaiPhases=" + l[3]
									+ " (cleanups run: " + ZanpakutoManager.bankaiCleanups() + ") -> " + (sum == 0 ? "CLEAN" : "LEFTOVERS");
							dev.minebleach.reiatsutest.ReiatsuTest.LOGGER.info("[bankai-audit] server {}", line);
							c.getSource().sendFeedback(() -> Text.literal(line), false);
							return sum == 0 ? 1 : 0;
						}))
						// /reiatsu dev barrier_check: in-world check of the Byakuya barrier on you (damage cut, control effects, ice, knockback)
						.then(CommandManager.literal("barrier_check").executes(c -> {
							ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
							List<String> lines = BarrierCheck.run(p);
							for (String l : lines) {
								c.getSource().sendFeedback(() -> Text.literal(l), false);
							}
							return BarrierCheck.lastFailures == 0 ? 1 : 0;
						}))
						// /reiatsu dev rage <points>: sets the Rage bar of the caster (0..100) to test the HUD
						.then(CommandManager.literal("rage")
								.then(CommandManager.argument("points", DoubleArgumentType.doubleArg(0.0, 100.0))
										.executes(c -> {
											ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
											double pts = DoubleArgumentType.getDouble(c, "points");
											RageManager.devSet(p, (int) Math.round(pts * 10.0));
											c.getSource().sendFeedback(() -> Text.literal("rage set to " + pts), true);
											return 1;
										})))
						// /reiatsu dev god on|off: infinite reiatsu and Rage for the caster (shikai/bankai need no Rage, nothing is spent)
						.then(CommandManager.literal("god")
								.then(CommandManager.argument("enabled", BoolArgumentType.bool()).executes(c -> {
									ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
									StateMachine sm = machine(c.getSource());
									boolean on = BoolArgumentType.getBool(c, "enabled");
									sm.setGodMode(on);
									RageManager.setInfinite(p, on);
									ZanpakutoManager.applyExternal(p, List.of());
									dev.minebleach.reiatsutest.ReiatsuTest.LOGGER.info("[dev] god mode {} for {}", on ? "ON" : "off", p.getName().getString());
									c.getSource().sendFeedback(() -> Text.literal(on ? "god mode ON: infinite reiatsu and Rage" : "god mode off"), true);
									return 1;
								})))
						// /reiatsu dev ragefree on|off: shikai and bankai need and spend no Rage (debug only, default off, creative does not bypass)
						.then(CommandManager.literal("ragefree")
								.then(CommandManager.argument("enabled", BoolArgumentType.bool()).executes(c -> {
									StateMachine sm = machine(c.getSource());
									boolean on = BoolArgumentType.getBool(c, "enabled");
									sm.setRageFree(on);
									dev.minebleach.reiatsutest.ReiatsuTest.LOGGER.info("[rage] ragefree {} for {}", on ? "ON" : "off", c.getSource().getPlayerOrThrow().getName().getString());
									c.getSource().sendFeedback(() -> Text.literal("ragefree " + (on ? "ON: shikai and bankai need no Rage" : "off")), true);
									return 1;
								})))));
	}

	private static int dummyHit(ServerCommandSource src, java.util.Collection<? extends Entity> targets, float hp) throws CommandSyntaxException {
		ServerPlayerEntity caster = src.getPlayerOrThrow();
		int n = 0;
		DmgLog.begin("dummy_hit");
		try {
			for (Entity e : targets) {
				if (!(e instanceof LivingEntity le)) {
					continue;
				}
				var refusal = Targeting.refusal(caster, le);
				var out = Targeting.hurt(caster, le, hp, DamageTypes.INDIRECT_MAGIC, true);
				String line = le.getName().getString() + ": " + out + " (target filter: " + refusal + ")";
				src.sendFeedback(() -> Text.literal(line), false);
				n++;
			}
		} finally {
			DmgLog.end();
		}
		return n;
	}

	private static int voice(ServerCommandSource src) {
		VoiceControl v = VoiceControl.get();
		if (!v.active()) {
			src.sendFeedback(() -> Text.translatable("message.reiatsu_test.voice.off"), false);
			return 0;
		}
		ServerPlayerEntity p = src.getPlayer();
		StateMachine sm = p == null ? null : ZanpakutoManager.machine(p);
		src.sendFeedback(() -> Text.translatable("message.reiatsu_test.voice.status", sm == null ? "-" : sm.state().name()), false);
		return 1;
	}

	private static StateMachine machine(ServerCommandSource src) throws CommandSyntaxException {
		ServerPlayerEntity p = src.getPlayerOrThrow();
		StateMachine sm = ZanpakutoManager.machine(p);
		if (sm == null) {
			throw NO_SESSION.create();
		}
		return sm;
	}

	private static int info(ServerCommandSource src) throws CommandSyntaxException {
		StateMachine sm = machine(src);
		StringBuilder cds = new StringBuilder();
		for (Map.Entry<AbilityId, Integer> e : sm.cooldownRemainingAll().entrySet()) {
			cds.append(' ').append(e.getKey().commandId).append('=').append(e.getValue()).append('t');
		}
		String line = String.format(java.util.Locale.ROOT, "state=%s character=%s mode=%s reiatsu=%.1f/%.1f tempBlocks=%d cooldowns:%s",
				sm.state(), sm.character(), sm.shikaiMode(), sm.reiatsu().value() / 10.0, sm.reiatsu().max() / 10.0,
				TempBlocks.size(), cds.length() == 0 ? " none" : cds);
		src.sendFeedback(() -> Text.literal(line), false);
		return 1;
	}

	private static int setPoints(ServerCommandSource src, double points) throws CommandSyntaxException {
		ServerPlayerEntity p = src.getPlayerOrThrow();
		StateMachine sm = machine(src);
		sm.devSetReiatsu((int) Math.round(points * 10.0));
		ZanpakutoManager.applyExternal(p, List.of());
		src.sendFeedback(() -> Text.literal("reiatsu set to " + points), true);
		return 1;
	}

	private static int setState(ServerCommandSource src, String state, String character) throws CommandSyntaxException {
		ServerPlayerEntity p = src.getPlayerOrThrow();
		StateMachine sm = machine(src);
		ZanpakutoState target;
		try {
			target = ZanpakutoState.valueOf(state.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw BAD_ARG.create();
		}
		CharacterId who = CharacterId.NONE;
		if (character != null) {
			try {
				who = CharacterId.valueOf(character.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				throw BAD_ARG.create();
			}
		} else {
			who = ModItems.characterOf(p.getMainHandStack());
			if (who == CharacterId.NONE) {
				who = CharacterId.RUKIA;
			}
		}
		ZanpakutoManager.applyExternal(p, sm.devSetState(target, who));
		CharacterId shown = who;
		src.sendFeedback(() -> Text.literal("state forced to " + target + (target == ZanpakutoState.SEALED ? "" : " (" + shown + ")")), true);
		return 1;
	}

	private static int clearCooldowns(ServerCommandSource src) throws CommandSyntaxException {
		ServerPlayerEntity p = src.getPlayerOrThrow();
		machine(src).devClearCooldowns();
		ZanpakutoManager.applyExternal(p, List.of());
		src.sendFeedback(() -> Text.literal("cooldowns cleared"), true);
		return 1;
	}
}
