package dev.minebleach.reiatsutest.voice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.voice.VoiceConfig;
import dev.minebleach.reiatsutest.core.voice.VoiceMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Transport independent request handling of the voice bridge: both the {@code com.sun.net.httpserver} endpoint and the
 * plain {@code ServerSocket} fallback feed requests through {@link #handle(Request)}, so the rules are written and tested
 * once.
 *
 * <p>Routes: {@code POST /voice}, {@code GET /status}, {@code GET /} (the bridge page), {@code OPTIONS} (CORS preflight).
 *
 * <p>Hygiene: the endpoint is bound to loopback only (done by the transport); {@code Host} must be 127.0.0.1, localhost
 * or [::1] with the bound port (DNS-rebinding guard); {@code Origin} must be absent, "null" (a page opened from file://,
 * switchable) or http on 127.0.0.1 / localhost / [::1], so an ordinary website in the same browser is refused (403);
 * body at most {@code maxBodyBytes} (413); at most {@code maxRequestsPerSecond} voice posts (429).
 */
public final class VoiceHttp {
	public record Request(String method, String path, Map<String, String> headers, InputStream body, long declaredLength) {
		public String header(String name) {
			return headers.get(name.toLowerCase(Locale.ROOT));
		}
	}

	public record Response(int status, Map<String, String> headers, byte[] body) {
	}

	private static final int MAX_TEXT_CHARS = 1000;

	private final VoiceConfig cfg;
	private final VoiceBackend backend;
	private final Supplier<byte[]> page;
	private final LongSupplier nanoClock;
	private final ArrayDeque<Long> recent = new ArrayDeque<>();
	private volatile int port;

	public VoiceHttp(VoiceConfig cfg, VoiceBackend backend, Supplier<byte[]> page) {
		this(cfg, backend, page, System::nanoTime);
	}

	VoiceHttp(VoiceConfig cfg, VoiceBackend backend, Supplier<byte[]> page, LongSupplier nanoClock) {
		this.cfg = cfg;
		this.backend = backend;
		this.page = page;
		this.nanoClock = nanoClock;
	}

	/** Set by the transport once the real port is known (also needed for ephemeral test ports). */
	public void setPort(int port) {
		this.port = port;
	}

	public int port() {
		return port;
	}

	public Response handle(Request req) {
		long received = nanoClock.getAsLong();
		String origin = req.header("origin");
		String corsOrigin = null;
		try {
			if (!hostAllowed(req.header("host"))) {
				return json(403, "{\"error\":\"bad host\"}", null);
			}
			if (origin != null) {
				if (!originAllowed(origin)) {
					return json(403, "{\"error\":\"origin not allowed\"}", null);
				}
				corsOrigin = origin;
			}
			String path = req.path();
			int q = path.indexOf('?');
			if (q >= 0) {
				path = path.substring(0, q);
			}
			String method = req.method().toUpperCase(Locale.ROOT);
			if (method.equals("OPTIONS")) {
				Map<String, String> h = base(corsOrigin);
				h.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
				h.put("Access-Control-Allow-Headers", "Content-Type");
				h.put("Access-Control-Allow-Private-Network", "true");
				h.put("Access-Control-Max-Age", "600");
				return new Response(204, h, new byte[0]);
			}
			switch (path) {
				case "/voice" -> {
					if (!method.equals("POST")) {
						return json(405, "{\"error\":\"POST only\"}", corsOrigin);
					}
					return voice(req, received, corsOrigin);
				}
				case "/status" -> {
					if (!method.equals("GET")) {
						return json(405, "{\"error\":\"GET only\"}", corsOrigin);
					}
					return json(200, backend.status(), corsOrigin);
				}
				case "/", "/index.html" -> {
					byte[] html = cfg.servePage ? page.get() : null;
					if (!method.equals("GET") || html == null) {
						return json(404, "{\"error\":\"not found\"}", corsOrigin);
					}
					Map<String, String> h = base(corsOrigin);
					h.put("Content-Type", "text/html; charset=utf-8");
					// the page makes no external calls: everything but same-origin fetches is blocked
					h.put("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; "
							+ "img-src data:; connect-src 'self'; base-uri 'none'; form-action 'none'");
					return new Response(200, h, html);
				}
				default -> {
					return json(404, "{\"error\":\"not found\"}", corsOrigin);
				}
			}
		} catch (RuntimeException e) {
			return json(500, "{\"error\":\"internal error\"}", corsOrigin);
		}
	}

	private Response voice(Request req, long received, String corsOrigin) {
		String ct = req.header("content-type");
		if (ct != null) {
			String base = ct.toLowerCase(Locale.ROOT).split(";")[0].trim();
			if (!base.equals("application/json") && !base.equals("text/plain")) {
				return json(415, "{\"error\":\"content type must be application/json or text/plain\"}", corsOrigin);
			}
		}
		if (req.declaredLength() > cfg.maxBodyBytes) {
			return json(413, "{\"error\":\"body too large\"}", corsOrigin);
		}
		if (!rateOk()) {
			return json(429, "{\"error\":\"too many requests\"}", corsOrigin);
		}
		byte[] body;
		try {
			body = readLimited(req.body(), cfg.maxBodyBytes);
		} catch (TooLarge e) {
			return json(413, "{\"error\":\"body too large\"}", corsOrigin);
		} catch (IOException e) {
			return json(400, "{\"error\":\"unreadable body\"}", corsOrigin);
		}
		VoiceMessage msg;
		try {
			msg = parse(body);
		} catch (IllegalArgumentException e) {
			return json(400, "{\"error\":" + quote(e.getMessage()) + "}", corsOrigin);
		}
		String reply = backend.onVoice(msg, received);
		return json(200, reply, corsOrigin);
	}

	static VoiceMessage parse(byte[] body) {
		JsonObject o;
		try {
			var el = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			if (!el.isJsonObject()) {
				throw new IllegalArgumentException("body must be a JSON object");
			}
			o = el.getAsJsonObject();
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("invalid JSON");
		}
		try {
			if (!o.has("text") || !o.get("text").isJsonPrimitive() || !o.get("text").getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException("text must be a string");
			}
			String text = o.get("text").getAsString();
			if (text.length() > MAX_TEXT_CHARS) {
				throw new IllegalArgumentException("text too long");
			}
			boolean fin = o.has("final") && o.get("final").getAsBoolean();
			String lang = o.has("lang") && o.get("lang").isJsonPrimitive() ? o.get("lang").getAsString() : "";
			if (lang.length() > 16) {
				lang = lang.substring(0, 16);
			}
			long utt = o.has("utt") ? o.get("utt").getAsLong() : -1;
			long t = o.has("t") ? o.get("t").getAsLong() : 0;
			return new VoiceMessage(text, fin, lang, utt, t);
		} catch (ClassCastException | IllegalStateException | NumberFormatException | UnsupportedOperationException e) {
			throw new IllegalArgumentException("wrong field type");
		}
	}

	// ------------------------------------------------------------------ checks

	boolean hostAllowed(String host) {
		if (host == null) {
			return false;
		}
		String h = host.toLowerCase(Locale.ROOT);
		int p = port;
		return h.equals("127.0.0.1:" + p) || h.equals("localhost:" + p) || h.equals("[::1]:" + p);
	}

	boolean originAllowed(String origin) {
		if (origin.equals("null")) {
			return cfg.allowNullOrigin;
		}
		try {
			URI u = URI.create(origin);
			if (!"http".equals(u.getScheme()) || u.getUserInfo() != null || (u.getPath() != null && !u.getPath().isEmpty())) {
				return false;
			}
			String host = u.getHost();
			return host != null && (host.equals("127.0.0.1") || host.equals("localhost") || host.equals("[::1]"));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private synchronized boolean rateOk() {
		long now = nanoClock.getAsLong();
		while (!recent.isEmpty() && now - recent.peekFirst() > 1_000_000_000L) {
			recent.pollFirst();
		}
		if (recent.size() >= cfg.maxRequestsPerSecond) {
			return false;
		}
		recent.addLast(now);
		return true;
	}

	private static final class TooLarge extends IOException {
	}

	private static byte[] readLimited(InputStream in, int max) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		int total = 0;
		int n;
		while ((n = in.read(buf)) >= 0) {
			total += n;
			if (total > max) {
				throw new TooLarge();
			}
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}

	// ------------------------------------------------------------------ responses

	private static Map<String, String> base(String corsOrigin) {
		Map<String, String> h = new LinkedHashMap<>();
		h.put("Cache-Control", "no-store");
		h.put("X-Content-Type-Options", "nosniff");
		if (corsOrigin != null) {
			h.put("Access-Control-Allow-Origin", corsOrigin);
			h.put("Vary", "Origin");
		}
		return h;
	}

	private static Response json(int status, String body, String corsOrigin) {
		Map<String, String> h = base(corsOrigin);
		h.put("Content-Type", "application/json; charset=utf-8");
		return new Response(status, h, body.getBytes(StandardCharsets.UTF_8));
	}

	private static String quote(String s) {
		return new com.google.gson.JsonPrimitive(s == null ? "" : s).toString();
	}
}
