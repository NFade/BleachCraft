package dev.minebleach.reiatsutest.client.voice;

import com.mojang.brigadier.Command;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.ClientOptions;
import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.voice.PhraseBook;
import dev.minebleach.reiatsutest.core.voice.VoiceConfig;
import dev.minebleach.reiatsutest.core.voice.VoiceFeedback;
import dev.minebleach.reiatsutest.core.voice.VoiceRequest;
import dev.minebleach.reiatsutest.net.VoiceFeedbackS2C;
import dev.minebleach.reiatsutest.net.VoiceTextC2S;
import dev.minebleach.reiatsutest.registry.ModItems;
import dev.minebleach.reiatsutest.voice.ClientVoiceBridge;
import dev.minebleach.reiatsutest.voice.VoiceEndpoint;
import dev.minebleach.reiatsutest.voice.VoiceHttp;
import dev.minebleach.reiatsutest.voice.VoiceHudState;
import dev.minebleach.reiatsutest.voice.VoicePage;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The player's own voice bridge (online voice, the scheme of vox-arcana). On joining ANY server (dedicated, LAN, single player)
 * this starts a loopback-only HTTP bridge on the player's PC ({@code 127.0.0.1:47821}, the next ports when busy) that serves the
 * page {@code voice-bridge/index.html}. The browser of the player recognises speech (Web Speech API) and posts the text; this
 * class forwards the text as {@link VoiceTextC2S} and hands the server's {@link VoiceFeedbackS2C} to the {@link ClientVoiceBridge}
 * (page answer, HUD). It is stopped when the player leaves the server. Only text leaves the PC for the game server, never audio,
 * and the server alone decides what a text means.
 */
public final class VoiceBridgeClient {
	private static final long REPLY_WAIT_MS = 1500;
	private static final long STATUS_WAIT_MS = 500;

	private static ClientVoiceBridge bridge;
	private static VoiceEndpoint endpoint;
	private static int port = -1;
	private static String lastError;
	private static volatile ClientVoiceBridge.Status lastStatus = ClientVoiceBridge.Status.NOT_IN_WORLD;

	private VoiceBridgeClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(VoiceFeedbackS2C.ID, (payload, ctx) -> {
			ClientVoiceBridge b = currentBridge();
			if (b != null) {
				b.onFeedback(payload.feedback());
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> start(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("voicelink").executes(ctx -> {
					announce(ctx.getSource().getClient());
					return Command.SINGLE_SUCCESS;
				})));
	}

	private static synchronized ClientVoiceBridge currentBridge() {
		return bridge;
	}

	// ------------------------------------------------------------------ lifecycle (client thread)

	private static synchronized void start(MinecraftClient client) {
		stopEndpoint();
		VoiceHudState.reset();
		lastError = null;
		lastStatus = ClientVoiceBridge.Status.NOT_IN_WORLD;
		if (!ClientOptions.voiceClientEnabled) {
			ReiatsuTest.LOGGER.info("[voice] client bridge disabled (voice.client.enabled = false)");
			announce(client);
			return;
		}
		try {
			VoiceConfig cfg = VoiceConfig.defaults();
			cfg.port = ClientOptions.voiceClientPort;
			cfg.portSearch = ClientOptions.voiceClientPortSearch;
			cfg.sanitize();
			ClientVoiceBridge b = new ClientVoiceBridge(new GameLink(client), PhraseBook.loadBundled(), cfg, System::currentTimeMillis,
					REPLY_WAIT_MS);
			b.setNotice(reason -> client.execute(() -> chat(client, Text.translatable(
					reason.equals("disabled") ? "message.reiatsu_test.voice.server_disabled" : "message.reiatsu_test.voice.server_missing")
					.formatted(Formatting.YELLOW))));
			bridge = b;
			VoiceHttp http = new VoiceHttp(cfg, b, VoicePage::bytes);
			endpoint = VoiceEndpoint.create(http);
			port = endpoint.start(cfg.port, cfg.portSearch);
			b.setEndpoint(port, endpoint.kind());
			VoiceHudState.setBridgeUp(true);
			ReiatsuTest.LOGGER.info("[voice] client bridge listening on http://127.0.0.1:{}/ (transport {})", port, endpoint.kind());
		} catch (IOException | RuntimeException e) {
			lastError = e.toString();
			ReiatsuTest.LOGGER.error("[voice] client bridge could not start: {}", e.toString());
			stopEndpoint();
		}
		announce(client);
	}

	private static synchronized void stop() {
		stopEndpoint();
		lastStatus = ClientVoiceBridge.Status.NOT_IN_WORLD;
	}

	private static void stopEndpoint() {
		VoiceHudState.setBridgeUp(false);
		if (endpoint != null) {
			endpoint.stop();
			ReiatsuTest.LOGGER.info("[voice] client bridge stopped (port {})", port);
		}
		endpoint = null;
		bridge = null;
		port = -1;
	}

	public static synchronized int port() {
		return port;
	}

	public static synchronized String url() {
		return port < 0 ? null : "http://127.0.0.1:" + port + "/";
	}

	public static synchronized String transport() {
		return endpoint == null ? "off" : endpoint.kind();
	}

	/** Chat line with the link to open in the browser, or why the bridge is off. */
	private static synchronized void announce(MinecraftClient client) {
		if (port >= 0) {
			String url = url();
			Text link = Text.literal(url).setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
					.withUnderline(true).withColor(Formatting.AQUA));
			chat(client, Text.translatable("message.reiatsu_test.voice.ready", link));
		} else if (lastError != null) {
			chat(client, Text.translatable("message.reiatsu_test.voice.failed", lastError));
		} else if (!ClientOptions.voiceClientEnabled) {
			chat(client, Text.translatable("message.reiatsu_test.voice.client_off"));
		}
	}

	private static void chat(MinecraftClient client, Text text) {
		if (client.inGameHud != null) {
			client.inGameHud.getChatHud().addMessage(text);
		}
	}

	// ------------------------------------------------------------------ the game connection as the bridge sees it

	private record GameLink(MinecraftClient mc) implements ClientVoiceBridge.Link {
		@Override
		public boolean inWorld() {
			return mc.player != null && mc.getNetworkHandler() != null;
		}

		@Override
		public void send(VoiceRequest request) {
			mc.execute(() -> {
				boolean ok;
				try {
					ok = mc.getNetworkHandler() != null && ClientPlayNetworking.canSend(VoiceTextC2S.ID);
				} catch (IllegalStateException | IllegalArgumentException e) {
					ok = false;
				}
				if (ok) {
					ClientPlayNetworking.send(new VoiceTextC2S(request));
				} else {
					ClientVoiceBridge b = currentBridge();
					if (b != null) {
						b.onFeedback(VoiceFeedback.rejected(request.reqId(), "no_server_support"));
					}
				}
			});
		}

		@Override
		public ClientVoiceBridge.Status status() {
			CompletableFuture<ClientVoiceBridge.Status> f = new CompletableFuture<>();
			mc.execute(() -> {
				try {
					f.complete(snapshot());
				} catch (RuntimeException e) {
					f.complete(lastStatus);
				}
			});
			try {
				return f.get(STATUS_WAIT_MS, TimeUnit.MILLISECONDS);
			} catch (TimeoutException | ExecutionException e) {
				return lastStatus;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return lastStatus;
			}
		}

		/** Client thread. */
		private ClientVoiceBridge.Status snapshot() {
			ClientPlayerEntity p = mc.player;
			if (p == null || mc.getNetworkHandler() == null) {
				lastStatus = ClientVoiceBridge.Status.NOT_IN_WORLD;
				return lastStatus;
			}
			boolean supported;
			try {
				supported = ClientPlayNetworking.canSend(VoiceTextC2S.ID);
			} catch (IllegalStateException | IllegalArgumentException e) {
				supported = false;
			}
			CharacterId held = ModItems.characterOf(p.getMainHandStack());
			var reiatsu = ClientState.reiatsu();
			boolean full = reiatsu != null && reiatsu.value() >= reiatsu.max();
			lastStatus = new ClientVoiceBridge.Status(true, supported, p.getName().getString(), ClientState.zanpakuto().zanpakutoState(), held,
					full);
			return lastStatus;
		}
	}
}
