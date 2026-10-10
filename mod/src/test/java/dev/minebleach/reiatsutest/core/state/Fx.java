package dev.minebleach.reiatsutest.core.state;

import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuState;
import java.util.ArrayList;
import java.util.List;

/** Test fixture: a machine on a fake clock plus shorthand for requests and time. */
public final class Fx {
	public static final long START = 1000L; // multiple of the 5-tick batch

	public final FakeClock clock = new FakeClock(START);
	public final BalanceConfig cfg;
	public final StateMachine sm;
	public int seq = 0;
	/** Every event produced by adv(). */
	public final List<StateEvent> log = new ArrayList<>();

	public Fx() {
		this(BalanceConfig.defaults(), 1000);
	}

	public Fx(int reiatsuTenths) {
		this(BalanceConfig.defaults(), reiatsuTenths);
	}

	public Fx(BalanceConfig cfg, int reiatsuTenths) {
		this.cfg = cfg;
		this.sm = new StateMachine(clock, cfg, new ReiatsuState(reiatsuTenths, cfg.maxTenths()));
	}

	public long now() {
		return clock.nowTick();
	}

	/** Advance {@code n} ticks one at a time; returns the events produced. */
	public List<StateEvent> adv(int n) {
		List<StateEvent> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			clock.advance(1);
			out.addAll(sm.tick(1));
		}
		log.addAll(out);
		return out;
	}

	public TransitionResult tr(ZanpakutoState target, CharacterId held) {
		return sm.request(new TransitionRequest(target, RequestSource.KEY, ++seq, held));
	}

	public TransitionResult tr(ZanpakutoState target, RequestSource src, CharacterId held) {
		return sm.request(new TransitionRequest(target, src, ++seq, held));
	}

	public TransitionResult cast(AbilityId a, CharacterId held) {
		return sm.cast(new AbilityRequest(a, RequestSource.KEY, ++seq, held));
	}

	/** SEALED -> BASE (the draw) through the real request, then waits out the transition lock. */
	public Fx toBase(CharacterId c) {
		TransitionResult r = tr(ZanpakutoState.BASE, c);
		if (!r.ok()) {
			throw new AssertionError("toBase rejected: " + r.reason());
		}
		adv(cfg.transitionLockTicks() + 1);
		return this;
	}

	/** SEALED -> BASE -> SHIKAI through the real requests (draw, then release), then waits out the transition lock. */
	public Fx toShikai(CharacterId c) {
		toBase(c);
		TransitionResult r = tr(ZanpakutoState.SHIKAI, c);
		if (!r.ok()) {
			throw new AssertionError("toShikai rejected: " + r.reason());
		}
		adv(cfg.transitionLockTicks() + 1);
		return this;
	}

	/** SHIKAI -> BANKAI: waits for a full bar first (150 ticks from a fresh release). */
	public Fx toBankai(CharacterId c) {
		int guard = 0;
		while (!sm.reiatsu().isFull() && guard++ < 1000) {
			adv(5);
		}
		TransitionResult r = tr(ZanpakutoState.BANKAI, c);
		if (!r.ok()) {
			throw new AssertionError("toBankai rejected: " + r.reason());
		}
		return this;
	}

	public static <T extends StateEvent> List<T> events(List<StateEvent> all, Class<T> type) {
		List<T> out = new ArrayList<>();
		for (StateEvent e : all) {
			if (type.isInstance(e)) {
				out.add(type.cast(e));
			}
		}
		return out;
	}

	public static boolean has(List<StateEvent> all, Class<? extends StateEvent> type) {
		return !events(all, type).isEmpty();
	}

	/** The last StateChanged in the events, or null. */
	public static StateEvent.StateChanged lastChange(List<StateEvent> all) {
		StateEvent.StateChanged last = null;
		for (StateEvent e : all) {
			if (e instanceof StateEvent.StateChanged c) {
				last = c;
			}
		}
		return last;
	}
}
