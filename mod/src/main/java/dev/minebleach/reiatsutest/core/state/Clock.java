package dev.minebleach.reiatsutest.core.state;

/** Time source in server ticks. The glue uses the server's tick counter (not world time: /time set must not matter). */
@FunctionalInterface
public interface Clock {
	long nowTick();
}
