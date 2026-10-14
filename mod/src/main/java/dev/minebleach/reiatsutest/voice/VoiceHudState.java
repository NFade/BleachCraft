package dev.minebleach.reiatsutest.voice;

/**
 * What the HUD shows about the voice bridge (VFX_STORYBOARD 7.8, S6). Written by the client bridge ({@link ClientVoiceBridge}:
 * HTTP threads for the page contact, the client thread for the server's answers), read by the client HUD in the same JVM. The
 * bridge runs on the player's own PC for every server (online voice), so the state is per game client. Immutable snapshots
 * in a volatile field, no locking.
 */
public record VoiceHudState(Mic mic, long lastContactMs, String interim, String finalText, String commandId, Result result,
		long atMs, long seq) {
	public enum Mic { OFF, IDLE, LISTENING, HEARING, ERROR }

	public enum Result { NONE, ACCEPTED, DENIED_REIATSU, DENIED_RAGE, DENIED_STATE, DENIED_ITEM, DENIED_NOT_DRAWN, COOLDOWN, NO_MATCH, GATED }

	/** The page counts as connected while it polls /status (every 2.5 s) or posts; a few missed polls and the HUD says "off". */
	public static final long CONTACT_TIMEOUT_MS = 6000;

	private static volatile VoiceHudState current = new VoiceHudState(Mic.OFF, 0, "", "", null, Result.NONE, 0, 0);
	/** True while the HTTP bridge of this JVM is listening (set by the server glue), so the HUD can show "not connected" before the first contact. */
	private static volatile boolean bridgeUp;
	/** Command the server announced just before running it (voice_feedback PENDING): lets the HUD attach the following action_result to it. */
	private static volatile String pendingCommand;
	private static volatile long pendingAtMs;

	public static VoiceHudState get() {
		return current;
	}

	/** Test / harness hook. */
	public static void set(VoiceHudState s) {
		current = s;
	}

	public static void reset() {
		current = new VoiceHudState(Mic.OFF, 0, "", "", null, Result.NONE, 0, 0);
		bridgeUp = false;
		pendingCommand = null;
		pendingAtMs = 0;
	}

	public static void setPending(String commandId) {
		pendingCommand = commandId;
		pendingAtMs = System.currentTimeMillis();
	}

	/** The command the server announced less than {@code maxAgeMs} ago, or null. */
	public static String pendingCommand(long maxAgeMs) {
		String c = pendingCommand;
		return c != null && System.currentTimeMillis() - pendingAtMs <= maxAgeMs ? c : null;
	}

	/** HUD verdict for the {@code ResultCode} byte of a command the server ran. */
	public static Result fromResultCode(int resultCode) {
		return switch (dev.minebleach.reiatsutest.core.state.ResultCode.fromCode((byte) resultCode)) {
			case OK -> Result.ACCEPTED;
			case DENIED_REIATSU -> Result.DENIED_REIATSU;
			case DENIED_RAGE -> Result.DENIED_RAGE;
			case DENIED_SHIKAI_TIME -> Result.DENIED_STATE; // R11 T3: the generic denied line
			case DENIED_ITEM -> Result.DENIED_ITEM;
			case DENIED_NOT_DRAWN -> Result.DENIED_NOT_DRAWN;
			case COOLDOWN -> Result.COOLDOWN;
			case DENIED_STATE, RATE_LIMIT, BLOCKED -> Result.DENIED_STATE;
		};
	}

	public static boolean bridgeUp() {
		return bridgeUp;
	}

	public static void setBridgeUp(boolean up) {
		bridgeUp = up;
	}

	/** True when the HUD should show the voice indicator at all: the bridge runs here, or a page has been in touch. */
	public boolean indicatorWanted() {
		return bridgeUp || lastContactMs > 0;
	}

	/** The mic state to draw at {@code wallMs}: OFF when the page has not been heard from for {@link #CONTACT_TIMEOUT_MS}. */
	public Mic effectiveMic(long wallMs) {
		return lastContactMs <= 0 || wallMs - lastContactMs > CONTACT_TIMEOUT_MS ? Mic.OFF : mic;
	}

	/** The bridge was reached (a /status poll or a /voice post); {@code pageMic} is the page's own microphone state or null. */
	public static synchronized void contact(String pageMic) {
		VoiceHudState s = current;
		Mic m = s.mic;
		if (pageMic != null) {
			m = switch (pageMic.trim().toLowerCase(java.util.Locale.ROOT)) {
				case "on", "listening" -> Mic.LISTENING;
				case "hearing" -> Mic.HEARING;
				case "error" -> Mic.ERROR;
				default -> Mic.IDLE;
			};
		} else if (m == Mic.OFF) {
			m = Mic.IDLE;
		}
		current = new VoiceHudState(m, System.currentTimeMillis(), s.interim, s.finalText, s.commandId, s.result, s.atMs, s.seq);
	}

	public static synchronized void interim(String text) {
		VoiceHudState s = current;
		long now = System.currentTimeMillis();
		current = new VoiceHudState(Mic.HEARING, now, text, s.finalText, s.commandId, s.result, now, s.seq + 1);
	}

	public static synchronized void result(String text, String commandId, Result result) {
		VoiceHudState s = current;
		long now = System.currentTimeMillis();
		Mic m = s.mic == Mic.HEARING ? Mic.LISTENING : s.mic;
		current = new VoiceHudState(m, now, "", text, commandId, result, now, s.seq + 1);
	}
}
