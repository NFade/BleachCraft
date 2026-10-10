package dev.minebleach.reiatsutest.client.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.EffectPlaceholders;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.hud.HudModel;
import dev.minebleach.reiatsutest.client.hud.ReiatsuHud;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.RequestSource;
import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.net.ActionResultS2C;
import dev.minebleach.reiatsutest.net.CastAbilityC2S;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.net.EntityFxS2C;
import dev.minebleach.reiatsutest.net.RequestTransitionC2S;
import dev.minebleach.reiatsutest.net.ShunpoC2S;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/** Client networking: sends requests, shows feedback for answers, hands effect events to the placeholder renderer. */
public final class ClientNet {
	private static final AtomicInteger SEQ = new AtomicInteger();
	/** Dev/harness: every action_result received as "seq:RESULT". */
	public static final List<String> RESULTS = new ArrayList<>();
	public static final AtomicInteger EFFECT_EVENTS = new AtomicInteger();
	public static final AtomicInteger ENTITY_FX = new AtomicInteger();
	/** Requests by sequence number, so a denied answer can be attached to its slot (HUD 7.7). */
	private static final java.util.Map<Integer, HudModel.Request> REQUESTS = new java.util.concurrent.ConcurrentHashMap<>();

	private ClientNet() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ActionResultS2C.ID, (payload, ctx) -> onResult(ctx.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(EffectEventS2C.ID, (payload, ctx) -> {
			EFFECT_EVENTS.incrementAndGet();
			var mcl = ctx.client();
			boolean local = mcl.player != null && mcl.player.getId() == payload.casterId();
			HudModel.onEffectEvent(payload.effectId(), local, dev.minebleach.reiatsutest.core.state.CharacterId.NONE);
			dev.minebleach.reiatsutest.client.fx.FxEvents.onEffectEvent(mcl, payload);
		});
		ClientPlayNetworking.registerGlobalReceiver(EntityFxS2C.ID, (payload, ctx) -> {
			ENTITY_FX.incrementAndGet();
			EffectPlaceholders.entities(ctx.client(), payload);
		});
	}

	/** Resets the request counter on join (the server machine of a new session starts without a last seq). */
	public static void onJoin() {
		SEQ.set(0);
		RESULTS.clear();
		REQUESTS.clear();
		HudModel.resetAll();
		dev.minebleach.reiatsutest.client.fx.FxTimelines.clear();
		dev.minebleach.reiatsutest.client.fx.AuraFx.clear();
	}

	public static int requestTransition(ZanpakutoState target, RequestSource source) {
		int seq = SEQ.incrementAndGet();
		remember(seq, new HudModel.Request(true, target, null));
		ClientPlayNetworking.send(new RequestTransitionC2S(target.code(), source.code(), seq));
		ReiatsuTest.LOGGER.info("[client] request_transition {} seq={} ({})", target, seq, source);
		return seq;
	}

	public static int castAbility(AbilityId ability, RequestSource source) {
		int seq = SEQ.incrementAndGet();
		remember(seq, new HudModel.Request(false, null, ability));
		ClientPlayNetworking.send(new CastAbilityC2S(ability.code, source.code(), seq));
		ReiatsuTest.LOGGER.info("[client] cast_ability {} seq={} ({})", ability.commandId, seq, source);
		return seq;
	}

	/** Asks for a shunpo (B4 step 5): no position, the server teleports along its own view of the look direction. */
	public static int requestShunpo(RequestSource source) {
		int seq = SEQ.incrementAndGet();
		remember(seq, new HudModel.Request(false, null, null));
		ClientPlayNetworking.send(new ShunpoC2S(source.code(), seq));
		ReiatsuTest.LOGGER.info("[client] shunpo seq={} ({})", seq, source);
		return seq;
	}

	private static void remember(int seq, HudModel.Request req) {
		REQUESTS.put(seq, req);
		if (REQUESTS.size() > 64) {
			REQUESTS.keySet().removeIf(k -> k < seq - 32);
		}
	}

	private static void onResult(MinecraftClient client, ActionResultS2C r) {
		ResultCode code = ResultCode.fromCode(r.result());
		ReiatsuTest.LOGGER.info("[client] action_result seq={} {}", r.clientSeq(), code);
		synchronized (RESULTS) {
			RESULTS.add(r.clientSeq() + ":" + code);
		}
		HudModel.Request req = REQUESTS.remove(r.clientSeq());
		if (req == null && r.clientSeq() < 0) {
			req = HudModel.requestFromVoice();
		}
		if (FxConfig.hud) {
			HudModel.onResult(code, req);
			return;
		}
		feedback(client, code);
	}

	/** Action bar message, bar flash and a low note for denied requests (STATE_MACHINE section 2). */
	public static void feedback(MinecraftClient client, ResultCode code) {
		if (FxConfig.hud) {
			// a local refusal (no server round trip): the HUD shows it
			HudModel.onResult(code, null);
			return;
		}
		String key = switch (code) {
			case DENIED_STATE -> "message.reiatsu_test.denied.state";
			case DENIED_ITEM -> "message.reiatsu_test.denied.item";
			case DENIED_REIATSU -> "message.reiatsu_test.denied.reiatsu";
			case DENIED_NOT_DRAWN -> "message.reiatsu_test.denied.not_drawn";
			case BLOCKED -> "hud.reiatsu_test.denied.blocked";
			case COOLDOWN -> null; // the HUD icons shake, no text spam
			case OK, RATE_LIMIT -> null;
		};
		if (code == ResultCode.COOLDOWN) {
			ReiatsuHud.shakeCooldowns();
		}
		if (key != null) {
			client.inGameHud.setOverlayMessage(Text.translatable(key), false);
			client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.7F));
		}
		if (code == ResultCode.DENIED_REIATSU) {
			ReiatsuHud.flashBar();
		}
	}
}
