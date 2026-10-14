package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ModConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import java.util.ArrayList;
import java.util.List;

/**
 * R11 T0: {@code /reiatsu reload}. Migrates the config file, then re-reads every section that can change on a running server. Sections register
 * themselves once at init; voice and frost keep their start-up values (they own sockets / world state) and need a restart.
 */
public final class ConfigReload {
	private static final List<Runnable> LOADERS = new ArrayList<>();

	private ConfigReload() {
	}

	/** Registers a section loader (called once at mod init). */
	public static synchronized void register(Runnable loader) {
		LOADERS.add(loader);
	}

	/** Migrates and reloads; returns the number of sections re-read. A failing section keeps its previous values. */
	public static synchronized int run() {
		int keys = ModConfig.migrate();
		int n = 0;
		for (Runnable r : LOADERS) {
			try {
				r.run();
				n++;
			} catch (RuntimeException e) {
				ReiatsuTest.LOGGER.warn("[config] reload of a section failed: {}", e.toString());
			}
		}
		ReiatsuTest.LOGGER.info("[config] reload: {} sections, {} keys migrated", n, keys);
		return n;
	}
}
