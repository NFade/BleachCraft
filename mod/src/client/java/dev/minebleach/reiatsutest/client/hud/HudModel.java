package dev.minebleach.reiatsutest.client.hud;

import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxMath;
import dev.minebleach.reiatsutest.client.fx.FxSound;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.data.ReiatsuData;
import dev.minebleach.reiatsutest.voice.VoiceHudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Animation and event model of the HUD (VFX_STORYBOARD section 7). Pure state: {@link #update} is called once per frame by
 * the renderer, everything is expressed in FX clock seconds ({@link FxClock#now}) so the harness can freeze any moment. The
 * renderers only read the public fields.
 */
public final class HudModel {
	public static final BalanceConfig CFG = BalanceConfig.defaults();

	public enum SlotState { READY, COOLING, POOR, DISABLED, ACTIVE }

	/** One ability slot as the strip shows it this frame. */
	public static final class Slot {
		public int index;
		public AbilityId ability;
		public SlotState state;
		public int remainingTicks;
		public int totalTicks;
		public int costTenths;
		public double activeFraction;
		public double pingAge = 99;
		public double shake;
		public double flashRed;
		public double flashGrey;
		public double flashGold;
		public double enter = 1;
	}

	public enum ToastKind { INFO, DENIED, GREY }

	public static final class Toast {
		public Text text;
		public float[] tint;
		public double start;
		public double life;
		public ToastKind kind;
	}

	public static final class Card {
		public boolean bankai;
		public CharacterId character;
		public double start;
	}

	public static final class Technique {
		public Text text;
		public CharacterId character;
		public double start;
	}

	public static final class VoiceToast {
		public String line1 = "";
		public String line2 = "";
		public float[] line2Tint = HudAssets.DIM;
		public boolean interim;
		public boolean accepted;
		public boolean bankai;
		public double start;
		public double lastTouch;
		public long seq = -1;
	}

	/** Request kinds remembered per sequence number to attach denied feedback to its slot. */
	public record Request(boolean transition, ZanpakutoState target, AbilityId ability) {
	}

	// ------------------------------------------------------------------------------------------ frame snapshot
	public static double now;
	public static double dt;
	public static ZanpakutoState state = ZanpakutoState.SEALED;
	public static CharacterId character = CharacterId.NONE;
	public static boolean plateWanted;
	public static double plateAlpha;
	public static int value;
	public static int max = 1000;
	public static final Slot[] SLOTS = {new Slot(), new Slot(), new Slot()};
	public static int slotCount;
	/** Character and state shown by the strip (kept while the strip slides away). */
	public static CharacterId stripChar = CharacterId.NONE;
	public static ZanpakutoState stripState = ZanpakutoState.SEALED;
	public static double stripOutStart = -99;
	public static double stripInStart = -99;

	// transitions
	public static double stateChangeAt = -99;
	public static ZanpakutoState fromState = ZanpakutoState.SEALED;
	public static CharacterId fromChar = CharacterId.NONE;
	public static double flipStart = -99;
	public static double bankaiStart = -99;
	public static double sealStart = -99;
	public static double endedAt = -99;
	public static double lockStart = -99;
	public static double lockDur = 0;
	public static double sheenStart = -99;
	public static double gemSparkAt = -99;
	public static boolean gemReady;
	public static double iconFlash = -99;
	private static float[] accFrom = HudAssets.STEEL;
	private static float[] accTo = HudAssets.STEEL;
	private static double accT0 = -99;
	private static double accDur = 0.4;
	private static int rowFrom;
	private static int rowTo;
	private static double lastActivity;
	private static ClientPlayerEntity lastPlayer;
	private static net.minecraft.registry.RegistryKey<net.minecraft.world.World> lastWorld;

	// bar
	public static double chipFrom = -1;
	public static double chipHoldUntil;
	public static double chipEndAt;
	public static double regenUntil;
	private static int lastValue = -1;
	public static double barShakeUntil;
	public static int hatchFrom;
	public static int hatchTo;
	public static double hatchUntil;
	public static double barFlashUntil;
	public static double barRedFlashStart = -99;

	// feedback
	public static Toast toast;
	private static double lastToastAt = -9;
	public static Card card;
	public static Technique technique;
	public static final VoiceToast VOICE = new VoiceToast();
	private static int lastTickSecond = -1;
	private static final int[] LAST_REM = {0, 0, 0};
	private static final boolean[] SEEN_REM = {false, false, false};

	// dev overrides (harness): remaining / total ticks per ability, forced
	public static final int[][] COOLDOWN_OVERRIDE = new int[AbilityId.values().length][];

	private HudModel() {
	}

	public static void resetAll() {
		state = ZanpakutoState.SEALED;
		character = CharacterId.NONE;
		plateAlpha = 0;
		stateChangeAt = flipStart = bankaiStart = sealStart = endedAt = lockStart = sheenStart = gemSparkAt = iconFlash = -99;
		lockDur = 0;
		accFrom = accTo = HudAssets.STEEL;
		accT0 = -99;
		rowFrom = rowTo = 0;
		chipFrom = -1;
		lastValue = -1;
		toast = null;
		card = null;
		technique = null;
		stripOutStart = stripInStart = -99;
		stripChar = CharacterId.NONE;
		stripState = ZanpakutoState.SEALED;
		barShakeUntil = hatchUntil = barFlashUntil = 0;
		barRedFlashStart = -99;
		gemReady = false;
		for (int i = 0; i < 3; i++) {
			LAST_REM[i] = 0;
			SEEN_REM[i] = false;
			SLOTS[i].pingAge = 99;
			SLOTS[i].shake = SLOTS[i].flashRed = SLOTS[i].flashGrey = SLOTS[i].flashGold = 0;
		}
		VOICE.seq = -1;
		VOICE.start = -99;
		VOICE.lastTouch = -99;
		lastActivity = now;
	}

	// ------------------------------------------------------------------------------------------ colours

	/** Accent colour now (cross fades 0.4 s, 0.6 s when draining to steel). */
	public static float[] accentNow() {
		double t = accDur <= 0 ? 1 : FxMath.clamp((now - accT0) / accDur);
		return FxMath.mix(accFrom, accTo, FxMath.ios(t));
	}

	public static float rowBlend() {
		double t = accDur <= 0 ? 1 : FxMath.clamp((now - accT0) / accDur);
		return (float) FxMath.ios(t);
	}

	public static int rowFromIdx() {
		return rowFrom;
	}

	public static int rowToIdx() {
		return rowTo;
	}

	// ------------------------------------------------------------------------------------------ update

	public static void update(MinecraftClient mc, float tickDelta) {
		now = FxClock.now;
		dt = FxClock.dt;
		ClientPlayerEntity p = mc.player;
		if (p == null) {
			return;
		}
		boolean worldChanged = mc.world != null && lastWorld != null && mc.world.getRegistryKey() != lastWorld;
		if (p != lastPlayer || worldChanged) {
			if (lastPlayer != null || worldChanged) {
				resetAll();
			}
			lastPlayer = p;
			lastWorld = mc.world == null ? null : mc.world.getRegistryKey();
		}
		var z = ClientState.zanpakuto();
		ZanpakutoState st = z.zanpakutoState();
		CharacterId held = ClientState.heldCharacter();
		CharacterId ch = st == ZanpakutoState.SEALED ? held : z.characterId();
		ReiatsuData r = ClientState.reiatsu();
		int newMax = r == null || r.max() <= 0 ? 1000 : r.max();
		int newValue = r == null ? 0 : r.value();
		if (Boolean.getBoolean("reiatsu.hud.testValue")) {
			newValue = Integer.getInteger("reiatsu.hud.testValueTenths", newValue);
		}

		// state change -> animations
		if (st != state || (st != ZanpakutoState.SEALED && ch != character && ch != CharacterId.NONE && state != ZanpakutoState.SEALED)) {
			onStateChange(state, character, st, ch, p);
		}
		state = st;
		character = ch;
		if (st == ZanpakutoState.SHIKAI || st == ZanpakutoState.BANKAI) {
			stripChar = ch;
			stripState = st;
		}

		plateWanted = st != ZanpakutoState.SEALED || held != CharacterId.NONE;
		double target = plateWanted ? 1 : 0;
		plateAlpha += Math.signum(target - plateAlpha) * Math.min(Math.abs(target - plateAlpha), dt * 6.0);
		if (FxConfig.reduceMotion && Math.abs(target - plateAlpha) > 0) {
			plateAlpha = target;
		}

		// reiatsu: chip bar, regen cap
		if (lastValue >= 0 && newValue < lastValue) {
			double startFrom = chipFrom >= 0 && now < chipEndAt ? Math.max(chipFrom, lastValue) : lastValue;
			chipFrom = startFrom;
			chipHoldUntil = now + 0.25;
			chipEndAt = chipHoldUntil + 0.45;
		} else if (lastValue >= 0 && newValue > lastValue) {
			regenUntil = now + 0.6;
		}
		lastValue = newValue;
		value = newValue;
		max = newMax;
		if (chipFrom >= 0 && now >= chipEndAt) {
			chipFrom = -1;
		}

		// bankai ready gem (SHIKAI at full reiatsu)
		boolean ready = st == ZanpakutoState.SHIKAI && value >= max;
		if (ready && !gemReady) {
			gemSparkAt = now;
			if (FxConfig.cooldownReadySound && plateWanted) {
				FxSound.ui("block.amethyst_block.chime", 1.6, 0.3);
			}
		}
		gemReady = ready;

		// bankai timer ticks (seconds 5..1)
		if (st == ZanpakutoState.BANKAI) {
			double rem = bankaiRemaining(tickDelta);
			int sec = (int) Math.ceil(rem);
			if (sec != lastTickSecond && sec >= 1 && sec <= 5 && lastTickSecond != -1) {
				FxSound.ui("block.note_block.bell", 1.6, 0.25);
			}
			lastTickSecond = sec;
		} else {
			lastTickSecond = -1;
		}

		updateSlots(p);
		updateVoice();
		if (toast != null && now - toast.start > toast.life) {
			toast = null;
		}
		if (card != null && now - card.start > (card.bankai ? 2.8 : 1.7)) {
			card = null;
		}
		if (technique != null && now - technique.start > 1.4) {
			technique = null;
		}
	}

	private static void onStateChange(ZanpakutoState from, CharacterId fromCh, ZanpakutoState to, CharacterId toCh, ClientPlayerEntity p) {
		boolean instant = !p.isAlive();
		fromState = from;
		fromChar = fromCh;
		stateChangeAt = now;
		float[] cur = accentNow();
		accFrom = cur;
		accTo = HudAssets.accent(toCh, to);
		rowFrom = rowTo;
		rowTo = HudAssets.fillRow(toCh, to);
		boolean toReleased = to == ZanpakutoState.SHIKAI || to == ZanpakutoState.BANKAI;
		boolean fromReleased = from == ZanpakutoState.SHIKAI || from == ZanpakutoState.BANKAI;
		accDur = instant ? 0 : to == ZanpakutoState.SEALED || to == ZanpakutoState.BASE ? 0.6 : 0.4;
		if (instant) {
			accFrom = accTo;
		}
		lastActivity = now;
		if (to == ZanpakutoState.SHIKAI && from != ZanpakutoState.BANKAI && !instant) {
			flipStart = now;
			sheenStart = now;
			stripInStart = now;
		} else if (to == ZanpakutoState.BANKAI && !instant) {
			bankaiStart = now;
			barFlashUntil = now + 0.15;
			if (!fromReleased) {
				flipStart = now;
				stripInStart = now;
			}
		}
		if (to == ZanpakutoState.SEALED && fromReleased && !instant) {
			sealStart = now;
			stripOutStart = now;
			if (now - endedAt > 1.0) {
				startLock(CFG.sealLockTicks() / 20.0);
			}
		}
		if (to == ZanpakutoState.BASE && fromReleased && !instant) {
			stripOutStart = now;
		}
		if (toReleased || instant) {
			lockStart = -99;
		}
		if (instant) {
			stripOutStart = -99;
		}
	}

	private static void startLock(double seconds) {
		lockStart = now;
		lockDur = seconds;
	}

	/** Seconds left of the bankai (45 s cap), interpolated within the client tick. */
	public static double bankaiRemaining(float tickDelta) {
		return Math.max(0, (CFG.bankaiCapTicks() - ClientState.ticksInState() - tickDelta) / 20.0);
	}

	public static int cooldownRemaining(AbilityId a) {
		int[] o = COOLDOWN_OVERRIDE[a.ordinal()];
		return o != null ? o[0] : ClientState.cooldownRemaining(a);
	}

	private static void updateSlots(ClientPlayerEntity p) {
		slotCount = 0;
		ZanpakutoState st = stripState;
		boolean live = state == ZanpakutoState.SHIKAI || state == ZanpakutoState.BANKAI;
		CharacterId ch = stripChar;
		if (ch == CharacterId.NONE) {
			return;
		}
		int n = 0;
		for (int i = 0; i < 3; i++) {
			AbilityId a = AbilityId.forSlot(ch, st, i);
			if (a == null) {
				continue;
			}
			Slot s = SLOTS[n++];
			s.index = i;
			s.ability = a;
			AbilitySpec spec = CFG.spec(a);
			s.costTenths = spec.costTenths();
			int[] o = COOLDOWN_OVERRIDE[a.ordinal()];
			int rem = live || o != null ? cooldownRemaining(a) : 0;
			s.remainingTicks = rem;
			s.totalTicks = o != null ? o[1] : spec.cooldownTicks();
			boolean poor = value - spec.costTenths() < 1;
			int active = activeTicks(a);
			s.activeFraction = 0;
			if (!spec.enabled()) {
				s.state = SlotState.DISABLED;
			} else if (active > 0 && rem > s.totalTicks - active && rem > 0) {
				s.state = SlotState.ACTIVE;
				s.activeFraction = FxMath.clamp((rem - (s.totalTicks - active)) / (double) active);
			} else if (rem > 0) {
				s.state = SlotState.COOLING;
			} else if (poor) {
				s.state = SlotState.POOR;
			} else {
				s.state = SlotState.READY;
			}
			// ready ping when a cooldown ends
			int idx = n - 1;
			if (SEEN_REM[idx] && LAST_REM[idx] > 0 && rem <= 0 && live) {
				s.pingAge = 0;
				pingSlot(s);
			}
			if (s.pingAge < 90) {
				s.pingAge += dt;
			}
			SEEN_REM[idx] = true;
			LAST_REM[idx] = rem;
			s.shake = Math.max(0, s.shake);
		}
		slotCount = n;
	}

	private static int activeTicks(AbilityId a) {
		return switch (a) {
			case MODE_ATTACK -> CFG.attackModeTicks();
			case MODE_BARRIER -> CFG.barrierTicks();
			default -> 0;
		};
	}

	private static void pingSlot(Slot s) {
		if (FxConfig.cooldownReadySound && FxConfig.hud) {
			FxSound.ui("block.note_block.chime", 1.8, 0.25);
		}
	}

	// ------------------------------------------------------------------------------------------ voice

	private static void updateVoice() {
		VoiceHudState v = VoiceHudState.get();
		if (v.seq() == VOICE.seq) {
			return;
		}
		boolean first = VOICE.seq == -1;
		VOICE.seq = v.seq();
		if (first && v.seq() == 0) {
			return;
		}
		VOICE.lastTouch = now;
		if (VOICE.start < 0 || now - VOICE.start > 2.9) {
			VOICE.start = now;
		}
		VOICE.bankai = false;
		boolean isInterim = !v.interim().isEmpty();
		VOICE.interim = isInterim;
		VOICE.line1 = isInterim ? v.interim() : v.finalText();
		if (isInterim) {
			VOICE.line2 = "";
			return;
		}
		String cmd = v.commandId();
		VOICE.accepted = v.result() == VoiceHudState.Result.ACCEPTED;
		switch (v.result()) {
			case ACCEPTED -> {
				VOICE.line2 = Text.translatable("hud.reiatsu_test.voice.ok", commandName(cmd)).getString();
				VOICE.line2Tint = HudAssets.OK;
				VOICE.bankai = cmd != null && cmd.endsWith(".bankai.release");
				if (FxConfig.voiceToastSound) {
					FxSound.ui("ui.toast.in", 1.4, 0.15);
				}
			}
			case COOLDOWN -> {
				AbilityId a = cmd == null ? null : AbilityId.fromCommandId(cmd);
				double secs = a == null ? 0 : cooldownRemaining(a) / 20.0;
				VOICE.line2 = Text.translatable("hud.reiatsu_test.voice.cooldown", String.format(java.util.Locale.ROOT, "%.1f", secs)).getString();
				VOICE.line2Tint = HudAssets.ERROR;
			}
			case DENIED_REIATSU -> {
				VOICE.line2 = Text.translatable(cmd != null && cmd.endsWith(".bankai.release") ? "hud.reiatsu_test.need_full" : "hud.reiatsu_test.voice.need").getString();
				VOICE.line2Tint = HudAssets.ERROR;
			}
			case DENIED_STATE, DENIED_ITEM, DENIED_NOT_DRAWN -> {
				VOICE.line2 = Text.translatable("hud.reiatsu_test.voice.denied").getString();
				VOICE.line2Tint = HudAssets.ERROR;
			}
			case GATED -> {
				VOICE.line2 = Text.translatable("hud.reiatsu_test.voice.gated").getString();
				VOICE.line2Tint = HudAssets.DIM;
			}
			default -> {
				VOICE.line2 = Text.translatable("hud.reiatsu_test.voice.nomatch").getString();
				VOICE.line2Tint = HudAssets.DIM;
			}
		}
	}

	private static String commandName(String cmd) {
		if (cmd == null) {
			return "";
		}
		if (cmd.endsWith(".shikai.release")) {
			return Text.translatable("hud.reiatsu_test.state.shikai").getString() + ": "
					+ Text.translatable("hud.reiatsu_test.zanpakuto." + (cmd.startsWith("byakuya") ? "byakuya" : "rukia")).getString();
		}
		if (cmd.endsWith(".bankai.release")) {
			return Text.translatable("hud.reiatsu_test.state.bankai").getString() + ": "
					+ Text.translatable("hud.reiatsu_test.zanpakuto." + (cmd.startsWith("byakuya") ? "byakuya" : "rukia") + ".bankai").getString();
		}
		if (cmd.equals("common.seal")) {
			return Text.translatable("hud.reiatsu_test.state.sealed").getString();
		}
		return Text.translatable("ability.reiatsu_test." + cmd).getString();
	}

	// ------------------------------------------------------------------------------------------ events

	/** Effect event received (any caster); title cards and technique toasts are for the local caster only. */
	public static void onEffectEvent(int effectId, boolean localCaster, CharacterId casterCharacter) {
		if (!localCaster) {
			return;
		}
		if (effectId >= 1 && effectId <= 4) {
			Card c = new Card();
			c.bankai = effectId == 2 || effectId == 4;
			c.character = effectId <= 2 ? CharacterId.RUKIA : CharacterId.BYAKUYA;
			c.start = now;
			card = c;
			lastActivity = now;
		} else if (effectId == 10) {
			if (lockStart < 0) {
				startLock(CFG.sealLockTicks() / 20.0);
			}
		} else if (effectId == 11) {
			endedAt = now;
			startLock(CFG.recoveryLockTicks() / 20.0);
			barRedFlashStart = now;
		} else if (effectId >= 20 && effectId <= 34) {
			Technique t = new Technique();
			t.text = Text.translatable("hud.reiatsu_test.technique." + effectId);
			t.character = effectId <= 23 ? CharacterId.RUKIA : CharacterId.BYAKUYA;
			t.start = now;
			technique = t;
			lastActivity = now;
		}
	}

	/** Dev / harness: drops the transient overlays (cards, toasts) so a screenshot starts clean. */
	public static void clearTransient() {
		card = null;
		technique = null;
		toast = null;
		VOICE.lastTouch = -99;
	}

	/** The request a voice command stands for (voice results arrive with the server sequence number, not a client one). */
	public static Request requestFromVoice() {
		VoiceHudState v = VoiceHudState.get();
		String cmd = v.commandId();
		if (cmd == null || System.currentTimeMillis() - v.atMs() > 2000) {
			return null;
		}
		if (cmd.endsWith(".shikai.release")) {
			return new Request(true, ZanpakutoState.SHIKAI, null);
		}
		if (cmd.endsWith(".bankai.release")) {
			return new Request(true, ZanpakutoState.BANKAI, null);
		}
		if (cmd.equals("common.seal")) {
			return new Request(true, ZanpakutoState.SEALED, null);
		}
		AbilityId a = AbilityId.fromCommandId(cmd);
		return a == null ? null : new Request(false, null, a);
	}

	public static void noteCast() {
		lastActivity = now;
	}

	/** Seconds left before the shikai seals itself for idleness (120 s without a cast). */
	public static double idleRemaining() {
		return CFG.shikaiIdleTicks() / 20.0 - (now - lastActivity);
	}

	public static void pushToast(Text text, float[] tint, ToastKind kind, double life) {
		if (now - lastToastAt < 0.5 && toast != null) {
			// rate limit: newer replaces, but not more often than every 0.5 s
			if (now - toast.start < 0.5) {
				return;
			}
		}
		Toast t = new Toast();
		t.text = text;
		t.tint = tint;
		t.start = now;
		t.life = life;
		t.kind = kind;
		toast = t;
		lastToastAt = now;
	}

	/** Answer of the server (or a local refusal) to a request, with the request when it is known. */
	public static void onResult(ResultCode code, Request req) {
		AbilitySpec spec = req != null && req.ability() != null ? CFG.spec(req.ability()) : null;
		Slot slot = req != null && req.ability() != null ? slotFor(req.ability()) : null;
		switch (code) {
			case OK -> {
				if (req != null && !req.transition()) {
					noteCast();
				}
			}
			case DENIED_REIATSU -> {
				barShakeUntil = now + 0.3;
				int needed;
				boolean full = false;
				if (spec != null) {
					needed = spec.costTenths();
				} else if (req != null && req.target() == ZanpakutoState.BANKAI) {
					needed = max;
					full = true;
				} else {
					needed = CFG.shikaiReleaseCost() + 1;
				}
				hatchFrom = Math.min(value, needed);
				hatchTo = needed;
				hatchUntil = now + 0.8;
				if (slot != null) {
					slot.flashRed = now + 0.4;
				}
				Text msg = full ? Text.translatable("hud.reiatsu_test.need_full")
						: Text.translatable("hud.reiatsu_test.need", String.format(java.util.Locale.ROOT, "%d", Math.round(needed / 10.0)));
				pushToast(msg, HudAssets.ERROR, ToastKind.DENIED, 1.6);
				if (FxConfig.hud) {
					FxSound.ui("block.note_block.bass", 0.6, 0.6);
				}
			}
			case COOLDOWN -> {
				if (slot != null) {
					slot.shake = now + 0.3;
					slot.flashGold = now + 0.3;
				}
				if (FxConfig.hud) {
					FxSound.ui("ui.button.click", 0.5, 0.4);
				}
			}
			case DENIED_STATE, DENIED_NOT_DRAWN -> {
				Text msg;
				if (code == ResultCode.DENIED_NOT_DRAWN) {
					msg = Text.translatable("message.reiatsu_test.denied.not_drawn");
				} else if (req != null && req.transition() && req.target() == ZanpakutoState.BANKAI) {
					msg = Text.translatable("hud.reiatsu_test.denied.bankai_needs_shikai");
				} else if (req != null && req.ability() != null) {
					msg = Text.translatable("hud.reiatsu_test.denied.not_in." + req.ability().requiredState.name().toLowerCase(java.util.Locale.ROOT));
				} else if (state == ZanpakutoState.SHIKAI || state == ZanpakutoState.BANKAI) {
					msg = Text.translatable("hud.reiatsu_test.denied.not_in.bankai");
				} else {
					msg = Text.translatable("hud.reiatsu_test.denied.not_in.shikai");
				}
				pushToast(msg, HudAssets.DIM, ToastKind.GREY, 1.4);
				for (int i = 0; i < slotCount; i++) {
					SLOTS[i].flashGrey = now + 0.2;
				}
				if (FxConfig.hud) {
					FxSound.ui("block.note_block.bass", 0.5, 0.4);
				}
			}
			case DENIED_ITEM -> {
				iconFlash = now;
				pushToast(Text.translatable("hud.reiatsu_test.denied.item"), HudAssets.DIM, ToastKind.GREY, 1.4);
				if (FxConfig.hud) {
					FxSound.ui("block.note_block.bass", 0.5, 0.4);
				}
			}
			case RATE_LIMIT -> {
			}
		}
	}

	public static Slot slotFor(AbilityId a) {
		for (int i = 0; i < slotCount; i++) {
			if (SLOTS[i].ability == a) {
				return SLOTS[i];
			}
		}
		return null;
	}
}
