package dev.minebleach.reiatsutest.core.state;

/** What the server glue must execute after a machine call. The machine itself touches nothing outside its own data. */
public sealed interface StateEvent {
	/** The synced snapshot changed (glue writes the attachment). */
	record StateChanged(ZanpakutoState from, ZanpakutoState to, Trigger trigger) implements StateEvent {
	}

	record ReiatsuSpent(int tenths) implements StateEvent {
	}

	record CooldownStarted(AbilityId ability, long endTick) implements StateEvent {
	}

	/** Send an {@code effect_event} (caster plus tracking players). */
	record BroadcastEffect(int effectId, int seed) implements StateEvent {
	}

	/** Cancel scheduled phases and running effects of this caster (the cast serial changed). */
	record CancelEffects() implements StateEvent {
	}

	/** Roll back every temporary block this player placed. */
	record RollbackTempBlocks() implements StateEvent {
	}

	/** Write the render-state mirror component on the player's stacks. */
	record MirrorComponent(ZanpakutoState state) implements StateEvent {
	}

	/** Run phase {@code phaseId} of {@code ability} {@code offsetTicks} from now, unless the serial changed. */
	record ScheduledPhase(AbilityId ability, int offsetTicks, int phaseId, long serial) implements StateEvent {
	}

	record ShikaiModeChanged(ShikaiMode mode) implements StateEvent {
	}
}
