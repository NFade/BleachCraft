package dev.minebleach.reiatsutest.core.combat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Hook for everything that reacts to a successful parry (future Rage, statistics, effects). Pure Java: the server glue calls
 * {@link #fire}, listeners register with {@link #register}. A failing listener never breaks the damage handling or the other
 * listeners; its exception goes to the error handler.
 */
public final class ParryBus {
	/** A parry listener. */
	@FunctionalInterface
	public interface Listener {
		void onParry(ParryEvent event);
	}

	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
	private static volatile Consumer<Throwable> errorHandler = t -> { };

	private ParryBus() {
	}

	/** Adds a listener; the returned runnable removes it again. */
	public static Runnable register(Listener listener) {
		LISTENERS.add(listener);
		return () -> LISTENERS.remove(listener);
	}

	public static void fire(ParryEvent event) {
		for (Listener l : LISTENERS) {
			try {
				l.onParry(event);
			} catch (RuntimeException e) {
				errorHandler.accept(e);
			}
		}
	}

	public static void setErrorHandler(Consumer<Throwable> handler) {
		errorHandler = handler == null ? t -> { } : handler;
	}

	/** Removes every listener (server stop, tests). */
	public static void clear() {
		LISTENERS.clear();
	}

	public static int listenerCount() {
		return LISTENERS.size();
	}
}
