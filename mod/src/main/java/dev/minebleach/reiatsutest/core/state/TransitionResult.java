package dev.minebleach.reiatsutest.core.state;

import java.util.List;

/** Outcome of a request. Ability casts reuse it with {@code from == to}. {@code reason} is null when accepted. */
public record TransitionResult(ResultCode code, RejectReason reason, ZanpakutoState from, ZanpakutoState to,
		List<StateEvent> events) {
	public TransitionResult {
		events = List.copyOf(events);
	}

	public boolean ok() {
		return code == ResultCode.OK;
	}
}
