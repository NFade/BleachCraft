package dev.minebleach.reiatsutest.voice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** {@link VoiceEndpoint} on {@code com.sun.net.httpserver.HttpServer}, bound to 127.0.0.1 with two daemon worker threads. */
final class JdkVoiceEndpoint implements VoiceEndpoint {
	private static final byte[] LOOPBACK = {127, 0, 0, 1};

	private final VoiceHttp http;
	private HttpServer server;
	private ExecutorService pool;

	JdkVoiceEndpoint(VoiceHttp http) {
		this.http = http;
	}

	@Override
	public synchronized int start(int port, int search) throws IOException {
		if (server != null) {
			return server.getAddress().getPort();
		}
		InetAddress loopback = InetAddress.getByAddress(LOOPBACK);
		IOException last = null;
		for (int i = 0; i <= (port == 0 ? 0 : search); i++) {
			int candidate = port == 0 ? 0 : port + i;
			try {
				HttpServer s = HttpServer.create(new InetSocketAddress(loopback, candidate), 8);
				int bound = s.getAddress().getPort();
				http.setPort(bound);
				AtomicInteger n = new AtomicInteger();
				pool = Executors.newFixedThreadPool(2, r -> {
					Thread t = new Thread(r, "reiatsu-voice-http-" + n.incrementAndGet());
					t.setDaemon(true);
					return t;
				});
				s.setExecutor(pool);
				s.createContext("/", this::handle);
				s.start();
				server = s;
				return bound;
			} catch (BindException e) {
				last = e;
			}
		}
		throw last != null ? last : new IOException("no port");
	}

	private void handle(HttpExchange ex) throws IOException {
		try (ex) {
			Map<String, String> headers = new HashMap<>();
			ex.getRequestHeaders().forEach((k, v) -> {
				if (!v.isEmpty()) {
					headers.put(k.toLowerCase(Locale.ROOT), v.get(0));
				}
			});
			String len = headers.get("content-length");
			long declared = -1;
			if (len != null) {
				try {
					declared = Long.parseLong(len.trim());
				} catch (NumberFormatException e) {
					declared = -1;
				}
			}
			VoiceHttp.Response r = http.handle(new VoiceHttp.Request(ex.getRequestMethod(), ex.getRequestURI().toString(),
					headers, ex.getRequestBody(), declared));
			r.headers().forEach((k, v) -> ex.getResponseHeaders().set(k, v));
			boolean noBody = r.status() == 204 || ex.getRequestMethod().equalsIgnoreCase("HEAD");
			ex.sendResponseHeaders(r.status(), noBody ? -1 : r.body().length);
			if (!noBody) {
				try (OutputStream out = ex.getResponseBody()) {
					out.write(r.body());
				}
			}
		}
	}

	@Override
	public synchronized void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
		if (pool != null) {
			pool.shutdownNow();
			pool = null;
		}
	}

	@Override
	public String kind() {
		return "jdk";
	}
}
