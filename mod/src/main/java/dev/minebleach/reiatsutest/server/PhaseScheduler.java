package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Scheduled ability phases (server ticks). A phase runs only while the caster's cast serial is unchanged: sealing,
 * auto-revert, death and every other reset bump the serial, which drops the remaining phases of earlier casts.
 */
final class PhaseScheduler {
	record Task(long dueTick, long order, UUID caster, long serial, AbilityId ability, int phase, CastContext ctx) {
	}

	private final PriorityQueue<Task> queue = new PriorityQueue<>((a, b) -> {
		int c = Long.compare(a.dueTick(), b.dueTick());
		return c != 0 ? c : Long.compare(a.order(), b.order());
	});
	private long counter;

	void add(long dueTick, UUID caster, long serial, AbilityId ability, int phase, CastContext ctx) {
		queue.add(new Task(dueTick, counter++, caster, serial, ability, phase, ctx));
	}

	void cancel(UUID caster) {
		for (Iterator<Task> it = queue.iterator(); it.hasNext();) {
			if (it.next().caster().equals(caster)) {
				it.remove();
			}
		}
	}

	void clear() {
		queue.clear();
	}

	int pending() {
		return queue.size();
	}

	/** Hands every due task to {@code runner} (which must check the serial itself). */
	void runDue(long nowTick, Consumer<Task> runner) {
		while (!queue.isEmpty() && queue.peek().dueTick() <= nowTick) {
			runner.accept(queue.poll());
		}
	}
}
