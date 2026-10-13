package dev.minebleach.reiatsutest.core.gin;

import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import java.util.UUID;

/**
 * The recast of Korose (design/GIN.md section 4), one instance per Gin. Phases: NONE -> (press 1 leaves a sliver) MARKED(target, expiry)
 * -> (press 2 on the same key) CHANNEL(start, end) -> the dissolve (back to NONE). The machine of the player still charges press 1
 * (25 reiatsu, 12 s); press 2 is free and never reaches it, so the cooldowns that follow the recast are returned here as
 * {@link Outcome#cooldownTicks()} and the glue writes them to the machine (-1 = leave the cooldown as it is).
 *
 * <p>Interrupt rules of the channel: ONE hit of at least {@code interruptHp} on Gin, knockback, any control effect or a change of his own
 * state. An interrupted channel spends the sliver (unless {@code keepsMark}) and costs {@code cooldownInterrupt}. A sliver that is cleansed
 * during the channel ends it the same way, except the target's death and Gin's own end (nothing to punish: the cooldown stays).
 */
public final class GinRecast {
	public enum Phase {
		NONE, MARKED, CHANNEL
	}

	/** What the key press means right now: a fresh cast, the free recast, or nothing (a channel is running). */
	public enum Press {
		MARK, RECAST, BUSY
	}

	public enum Kind {
		/** Nothing happened. */
		NONE,
		/** The channel began. */
		STARTED,
		/** The channel ran out: the target dissolves now. */
		DISSOLVE,
		/** The channel was interrupted on Gin's side (hit, knockback, control, state change). */
		INTERRUPTED,
		/** The channel ended because the sliver was cleansed or the target was out of range. */
		FIZZLED,
		/** A mark that was never recast ran out or was cleansed. */
		LOST
	}

	/** {@code cooldownTicks} is the cooldown of the ability counted from now, -1 = unchanged. */
	public record Outcome(Kind kind, int cooldownTicks) {
		static final Outcome NOTHING = new Outcome(Kind.NONE, -1);

		public boolean changesCooldown() {
			return cooldownTicks >= 0;
		}
	}

	public record Config(int markTicks, int channelTicks, int cooldownDissolve, int cooldownInterrupt, float interruptHp, double range,
			boolean keepsMark) {
		/** The numbers of the Korose row of the balance config. */
		public static Config of(AbilitySpec korose) {
			return new Config(korose.intNum(GinParams.SLIVER_TICKS), korose.intNum(GinParams.CHANNEL_TICKS), korose.intNum(GinParams.COOLDOWN_DISSOLVE),
					korose.intNum(GinParams.COOLDOWN_INTERRUPT), korose.floatNum(GinParams.INTERRUPT_HP), korose.num(GinParams.RECAST_RANGE),
					korose.num(GinParams.INTERRUPT_KEEPS_MARK) > 0.5);
		}
	}

	private final Config cfg;
	private Phase phase = Phase.NONE;
	private UUID target;
	private long expiry;
	private long channelStart;
	private long channelEnd;

	public GinRecast(Config cfg) {
		this.cfg = cfg;
	}

	public Config config() {
		return cfg;
	}

	public Phase phase() {
		return phase;
	}

	public UUID target() {
		return target;
	}

	public long expiry() {
		return expiry;
	}

	public long channelStart() {
		return channelStart;
	}

	public long channelEnd() {
		return channelEnd;
	}

	/** What a press of the Korose key means at {@code now}. A mark whose time is up no longer counts. */
	public Press decide(long now) {
		return switch (phase) {
			case CHANNEL -> Press.BUSY;
			case MARKED -> now < expiry ? Press.RECAST : Press.MARK;
			case NONE -> Press.MARK;
		};
	}

	/** Press 1 left a sliver on {@code who}. */
	public void marked(UUID who, long now) {
		phase = Phase.MARKED;
		target = who;
		expiry = now + cfg.markTicks();
	}

	/** Renjin hit the marked target: the timer starts again (12 s). False when {@code who} is not the marked one. */
	public boolean refresh(UUID who, long now) {
		if (phase != Phase.MARKED || !who.equals(target) || now >= expiry) {
			return false;
		}
		expiry = now + cfg.markTicks();
		return true;
	}

	/**
	 * Press 2. Needs a live mark; a target farther than the range loses the sliver (FIZZLED, cooldown unchanged). STARTED otherwise.
	 */
	public Outcome beginChannel(long now, double distance) {
		if (decide(now) != Press.RECAST) {
			return Outcome.NOTHING;
		}
		if (GinGeometry.outOfRange(distance, cfg.range())) {
			clear();
			return new Outcome(Kind.FIZZLED, -1);
		}
		phase = Phase.CHANNEL;
		channelStart = now;
		channelEnd = now + cfg.channelTicks();
		return new Outcome(Kind.STARTED, -1);
	}

	/** Once per tick: a finished channel dissolves; a mark that ran out is dropped. */
	public Outcome tick(long now) {
		if (phase == Phase.CHANNEL && now >= channelEnd) {
			clear();
			return new Outcome(Kind.DISSOLVE, cfg.cooldownDissolve());
		}
		if (phase == Phase.MARKED && now >= expiry) {
			clear();
			return new Outcome(Kind.LOST, -1);
		}
		return Outcome.NOTHING;
	}

	/** A hit of {@code hp} HP landed on Gin (the amount that really arrived). */
	public Outcome onHit(float hp) {
		return hp >= cfg.interruptHp() ? interrupt() : Outcome.NOTHING;
	}

	/** Gin was pushed. */
	public Outcome onKnockback() {
		return interrupt();
	}

	/** Gin got a control effect (slow, freeze, bind, nausea ...). */
	public Outcome onControl() {
		return interrupt();
	}

	/** Gin's own state changed (sealed, left BANKAI, died, left the world). */
	public Outcome onStateChange() {
		return interrupt();
	}

	/**
	 * The sliver is gone for {@code reason} (not DISSOLVED, that is our own end). During the channel this ends it: the target's death and
	 * Gin's own end change nothing about the cooldown, every other reason costs the interrupt cooldown.
	 */
	public Outcome sliverGone(SliverBook.Reason reason) {
		if (phase == Phase.NONE) {
			return Outcome.NOTHING;
		}
		boolean channel = phase == Phase.CHANNEL;
		clear();
		if (!channel) {
			return new Outcome(Kind.LOST, -1);
		}
		boolean free = reason == SliverBook.Reason.TARGET_DEATH || reason == SliverBook.Reason.OWNER_DEATH
				|| reason == SliverBook.Reason.OWNER_LEFT_BANKAI;
		return new Outcome(Kind.FIZZLED, free ? -1 : cfg.cooldownInterrupt());
	}

	/** Forget everything (death, logout). No cooldown change. */
	public void reset() {
		clear();
	}

	private Outcome interrupt() {
		if (phase != Phase.CHANNEL) {
			return Outcome.NOTHING;
		}
		if (cfg.keepsMark()) {
			phase = Phase.MARKED;
			channelStart = 0;
			channelEnd = 0;
		} else {
			clear();
		}
		return new Outcome(Kind.INTERRUPTED, cfg.cooldownInterrupt());
	}

	private void clear() {
		phase = Phase.NONE;
		target = null;
		expiry = 0;
		channelStart = 0;
		channelEnd = 0;
	}
}
