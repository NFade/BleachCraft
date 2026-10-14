package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ModConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.CharacterWording;
import dev.minebleach.reiatsutest.core.state.RequestSource;
import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.state.TransitionResult;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.core.voice.GateContext;
import dev.minebleach.reiatsutest.core.voice.PhraseBook;
import dev.minebleach.reiatsutest.core.voice.PhraseMatcher;
import dev.minebleach.reiatsutest.core.voice.VoiceConfig;
import dev.minebleach.reiatsutest.core.voice.VoiceFeedback;
import dev.minebleach.reiatsutest.core.voice.VoiceIngest;
import dev.minebleach.reiatsutest.core.voice.VoiceRequest;
import dev.minebleach.reiatsutest.net.VoiceFeedbackS2C;
import dev.minebleach.reiatsutest.net.VoiceTextC2S;
import dev.minebleach.reiatsutest.registry.ModItems;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Server side of the voice control (online voice, the scheme of vox-arcana): the speech is recognised by the browser on each
 * player's own PC and the client bridge sends only the TEXT ({@link VoiceTextC2S}). This class receives it for ANY player of
 * ANY server (dedicated, LAN, integrated), hands it to {@link VoiceIngest} (phrase matching and every gate stay on the server,
 * per player state: debounce, global gap, rate limit) and answers with {@link VoiceFeedbackS2C}. A fired command goes through
 * the SAME paths the key bindings use ({@link ZanpakutoManager#performTransition} / {@code performCast} / {@code performKido}),
 * always for the player who sent the packet; voice therefore cannot bypass the state machine, cooldowns, reiatsu or Rage.
 * There is no HTTP server here any more: the loopback bridge lives in the client (client/voice/VoiceBridgeClient).
 */
public final class VoiceControl implements VoiceIngest.Host {
	private static final VoiceControl INSTANCE = new VoiceControl();

	private MinecraftServer server;
	private VoiceConfig cfg;
	private VoiceIngest ingest;

	private VoiceControl() {
	}

	public static VoiceControl get() {
		return INSTANCE;
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(VoiceTextC2S.ID, (payload, ctx) -> INSTANCE.onPacket(ctx.player(), payload.request()));
		ServerLifecycleEvents.SERVER_STARTED.register(INSTANCE::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> INSTANCE.stop());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> INSTANCE.forget(handler.player.getUuid()));
	}

	// ------------------------------------------------------------------ lifecycle

	private synchronized void start(MinecraftServer srv) {
		stop();
		server = srv;
		cfg = ModConfig.loadVoice();
		if (!cfg.enabled) {
			ReiatsuTest.LOGGER.info("[voice] disabled in config: voice packets are refused");
		}
		try {
			ingest = new VoiceIngest(cfg, new PhraseMatcher(PhraseBook.loadBundled(), cfg.matcherConfig()), this);
			if (cfg.enabled) {
				ReiatsuTest.LOGGER.info("[voice] online voice ready: each player's own browser sends text, matching runs here ({})",
						srv.isDedicated() ? "dedicated server" : "integrated server");
			}
		} catch (RuntimeException e) {
			ingest = null;
			ReiatsuTest.LOGGER.error("[voice] phrase book could not be loaded, voice is off: {}", e.toString());
		}
	}

	private synchronized void stop() {
		server = null;
		ingest = null;
	}

	private synchronized void forget(UUID player) {
		if (ingest != null) {
			ingest.forget(player);
		}
	}

	/** True when this server takes voice text (config on and the phrase book loaded). */
	public synchronized boolean active() {
		return ingest != null && cfg != null && cfg.enabled;
	}

	// ------------------------------------------------------------------ packets (server thread)

	private void onPacket(ServerPlayerEntity p, VoiceRequest req) {
		VoiceIngest in;
		synchronized (this) {
			in = ingest;
		}
		VoiceIngest.Handled h;
		if (in == null) {
			h = new VoiceIngest.Handled(VoiceFeedback.rejected(req.reqId(), "disabled"), null, false);
		} else {
			try {
				h = in.handle(p.getUuid(), p.getName().getString(), req);
			} catch (RuntimeException e) {
				ReiatsuTest.LOGGER.error("[voice] handling failed for {}", p.getName().getString(), e);
				h = new VoiceIngest.Handled(VoiceFeedback.rejected(req.reqId(), "internal"), null, false);
			}
		}
		if (h.log() != null) {
			if (h.info()) {
				ReiatsuTest.LOGGER.info(h.log());
			} else {
				ReiatsuTest.LOGGER.debug(h.log());
			}
		}
		ServerPlayNetworking.send(p, new VoiceFeedbackS2C(h.feedback()));
	}

	// ------------------------------------------------------------------ VoiceIngest.Host

	private ServerPlayerEntity player(UUID id) {
		MinecraftServer srv = server;
		return srv == null ? null : srv.getPlayerManager().getPlayer(id);
	}

	@Override
	public GateContext gate(UUID id) {
		ServerPlayerEntity p = player(id);
		StateMachine sm = p == null ? null : ZanpakutoManager.machine(p);
		if (sm == null) {
			return null;
		}
		CharacterId held = ModItems.characterOf(p.getMainHandStack());
		return GateContext.of(held, sm.state(), sm.reiatsu().isFull());
	}

	@Override
	public String stateName(UUID id) {
		ServerPlayerEntity p = player(id);
		StateMachine sm = p == null ? null : ZanpakutoManager.machine(p);
		return sm == null ? "" : sm.state().name();
	}

	@Override
	public void hintNotDrawn(UUID id) {
		ServerPlayerEntity p = player(id);
		if (p != null) {
			p.sendMessage(Text.translatable(CharacterWording.key("message.reiatsu_test.denied.not_drawn", whoIs(p))), true);
		}
	}

	@Override
	public ResultCode execute(UUID id, String commandId, int reqId) {
		ServerPlayerEntity p = player(id);
		if (p == null) {
			return null;
		}
		// tell the client which command runs, so the action_result that dispatch() sends next is attached to it on the HUD
		ServerPlayNetworking.send(p, new VoiceFeedbackS2C(VoiceFeedback.pending(reqId, commandId)));
		TransitionResult r = dispatch(p, commandId);
		return r == null ? null : r.code();
	}

	/** Maps a command id to the server path the key bindings use. Null for an unknown id. */
	private TransitionResult dispatch(ServerPlayerEntity p, String id) {
		TransitionResult r;
		if (id.endsWith(".shikai.release")) {
			announceHeard(p, CharacterWording.key("message.reiatsu_test.voice.cmd.shikai", whoIs(p)));
			r = ZanpakutoManager.performTransition(p, ZanpakutoState.SHIKAI, RequestSource.VOICE, StateMachine.SERVER_SEQ);
		} else if (id.endsWith(".bankai.release")) {
			announceHeard(p, CharacterWording.key("message.reiatsu_test.voice.cmd.bankai", whoIs(p)));
			r = ZanpakutoManager.performTransition(p, ZanpakutoState.BANKAI, RequestSource.VOICE, StateMachine.SERVER_SEQ);
		} else if (id.equals("common.seal")) {
			announceHeard(p, "message.reiatsu_test.voice.cmd.seal");
			r = ZanpakutoManager.performTransition(p, ZanpakutoState.SEALED, RequestSource.VOICE, StateMachine.SERVER_SEQ);
		} else if (KidoId.fromCommandId(id) != null) {
			// kido (B5): no state, no sword; the same server path as the kido keys
			KidoId k = KidoId.fromCommandId(id);
			announceHeard(p, "kido.reiatsu_test." + k.key);
			r = ZanpakutoManager.performKido(p, k, RequestSource.VOICE, StateMachine.SERVER_SEQ);
		} else {
			AbilityId a = AbilityId.fromCommandId(id);
			if (a == null) {
				return null;
			}
			announceHeard(p, "ability.reiatsu_test." + a.commandId);
			r = ZanpakutoManager.performCast(p, a, RequestSource.VOICE, StateMachine.SERVER_SEQ);
		}
		return r;
	}

	/** The character the player plays: the released one, else the one of the item in the main hand (for the per character wording). */
	static CharacterId whoIs(ServerPlayerEntity p) {
		StateMachine sm = ZanpakutoManager.machine(p);
		CharacterId released = sm == null ? CharacterId.NONE : sm.character();
		return released != CharacterId.NONE ? released : ModItems.characterOf(p.getMainHandStack());
	}

	private static void announceHeard(ServerPlayerEntity p, String nameKey) {
		p.sendMessage(Text.translatable("message.reiatsu_test.voice.heard", Text.translatable(nameKey)), true);
	}
}
