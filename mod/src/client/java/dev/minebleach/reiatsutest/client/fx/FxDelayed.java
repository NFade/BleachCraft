package dev.minebleach.reiatsutest.client.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * Small queue of actions that run a number of FX seconds from now (the FX clock, so a frozen clock holds them too). Used to hold the
 * start of an effect while something else plays first (the pink blade before the Byakuya shikai bursts into petals).
 */
public final class FxDelayed {
	private record Item(double due, Runnable action) {
	}

	private static final List<Item> ITEMS = new ArrayList<>();

	private FxDelayed() {
	}

	public static void after(double seconds, Runnable action) {
		ITEMS.add(new Item(FxClock.now + Math.max(0, seconds), action));
	}

	/** Once per frame after the clock advanced. */
	static void run() {
		if (ITEMS.isEmpty()) {
			return;
		}
		List<Item> due = null;
		for (Item i : ITEMS) {
			if (FxClock.now >= i.due) {
				if (due == null) {
					due = new ArrayList<>();
				}
				due.add(i);
			}
		}
		if (due != null) {
			ITEMS.removeAll(due);
			for (Item i : due) {
				try {
					i.action.run();
				} catch (RuntimeException e) {
					dev.minebleach.reiatsutest.ReiatsuTest.LOGGER.error("[fx] delayed action failed", e);
				}
			}
		}
	}

	public static void clear() {
		ITEMS.clear();
	}

	public static int pending() {
		return ITEMS.size();
	}
}
