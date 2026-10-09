package dev.minebleach.reiatsutest.voice;

import java.io.IOException;

/** A loopback HTTP listener that feeds {@link VoiceHttp}. Two implementations: JDK HttpServer and a ServerSocket fallback. */
public interface VoiceEndpoint {
	/**
	 * Binds to 127.0.0.1 only. Tries {@code port}, then up to {@code search} following ports; port 0 asks for an
	 * ephemeral one (tests). Returns the bound port.
	 */
	int start(int port, int search) throws IOException;

	/** Stops listening and releases the port. Safe to call twice. */
	void stop();

	/** "jdk" or "socket". */
	String kind();

	/**
	 * Picks the JDK implementation when the module {@code jdk.httpserver} is present in the boot layer, otherwise the
	 * socket fallback (a trimmed launcher runtime may lack the module, ADR risk R4.3).
	 */
	static VoiceEndpoint create(VoiceHttp http) {
		if (ModuleLayer.boot().findModule("jdk.httpserver").isPresent()) {
			try {
				return new JdkVoiceEndpoint(http);
			} catch (LinkageError e) {
				// class not loadable: fall through to the fallback
			}
		}
		return new SocketVoiceEndpoint(http);
	}
}
