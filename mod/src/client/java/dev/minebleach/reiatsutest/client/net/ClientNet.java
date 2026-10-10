package dev.minebleach.reiatsutest.client.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.EffectPlaceholders;
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

	private ClientNet() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ActionResultS2C.ID, (payload, ctx) -> onResult(ctx.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(EffectEventS2C.ID, (payload, ctx) -> {
			EFFECT_EVENTS.incrementAndGet();
			EffectPlaceholders.play(ctx.client(), payload);
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
	}

	public static int requestTransition(ZanpakutoState target, RequestSource source) {
		int seq = SEQ.incrementAndGet();
		ClientPlayNetworking.send(new RequestTransitionC2S(target.code(), source.code(), seq));
		ReiatsuTest.LOGGER.info("[client] request_transition {} seq={} ({})", target, seq, source);
		return seq;
	}

	public static int castAbility(AbilityId ability, RequestSource source) {
		int seq = SEQ.incrementAndGet();
		ClientPlayNetworking.send(new CastAbilityC2S(ability.code, source.code(), seq));
		ReiatsuTest.LOGGER.info("[client] cast_ability {} seq={} ({})", ability.commandId, seq, source);
		return seq;
	}

	private static void onResult(MinecraftClient client, ActionResultS2C r) {
		ResultCode code = ResultCode.fromCode(r.result());
		ReiatsuTest.LOGGER.info("[client] action_result seq={} {}", r.clientSeq(), code);
		synchronized (RESULTS) {
			RESULTS.add(r.clientSeq() + ":" + code);
		}
		feedback(client, code);
	}

	/** Action bar message, bar flash and a low note for denied requests (STATE_MACHINE section 2). */
	public static void feedback(MinecraftClient client, ResultCode code) {
		String key = switch (code) {
			case DENIED_STATE -> "message.reiatsu_test.denied.state";
			case DENIED_ITEM -> "message.reiatsu_test.denied.item";
			case DENIED_REIATSU -> "message.reiatsu_test.denied.reiatsu";
			case DENIED_NOT_DRAWN -> "message.reiatsu_test.denied.not_drawn";
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
