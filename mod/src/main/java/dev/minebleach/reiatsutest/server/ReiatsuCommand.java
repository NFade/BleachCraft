package dev.minebleach.reiatsutest.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
				.then(CommandManager.literal("full").executes(c -> setPoints(c.getSource(), 100.0)))
				.then(CommandManager.literal("set")
						.then(CommandManager.argument("points", DoubleArgumentType.doubleArg(0.0, 100.0))
								.executes(c -> setPoints(c.getSource(), DoubleArgumentType.getDouble(c, "points")))))
				.then(CommandManager.literal("state")
						.then(CommandManager.argument("state", StringArgumentType.word())
								.suggests((c, b) -> CommandSource.suggestMatching(List.of("sealed", "shikai", "bankai"), b))
								.executes(c -> setState(c.getSource(), StringArgumentType.getString(c, "state"), null))
								.then(CommandManager.argument("character", StringArgumentType.word())
										.suggests((c, b) -> CommandSource.suggestMatching(List.of("rukia", "byakuya"), b))
										.executes(c -> setState(c.getSource(), StringArgumentType.getString(c, "state"),
												StringArgumentType.getString(c, "character"))))))
				.then(CommandManager.literal("cooldowns").then(CommandManager.literal("clear").executes(c -> clearCooldowns(c.getSource()))))
				.then(CommandManager.literal("pvp")
						.then(CommandManager.argument("enabled", BoolArgumentType.bool()).executes(c -> {
							Tuning.affectPlayers = BoolArgumentType.getBool(c, "enabled");
							c.getSource().sendFeedback(() -> Text.literal("abilities affect players: " + Tuning.affectPlayers), true);
							return 1;
						}))));
	}

	private static int voice(ServerCommandSource src) {
		VoiceControl v = VoiceControl.get();
		String url = v.url();
		if (url == null) {
			src.sendFeedback(() -> Text.translatable("message.reiatsu_test.voice.off"), false);
			return 0;
		}
		ServerPlayerEntity p = src.getPlayer();
		StateMachine sm = p == null ? null : ZanpakutoManager.machine(p);
		src.sendFeedback(() -> Text.translatable("message.reiatsu_test.voice.status", url, v.transport(),
				sm == null ? "-" : sm.state().name()), false);
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
