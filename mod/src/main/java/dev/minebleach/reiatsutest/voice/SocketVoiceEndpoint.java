package dev.minebleach.reiatsutest.voice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fallback {@link VoiceEndpoint} on a plain {@link ServerSocket} for runtimes without {@code jdk.httpserver}: minimal
 * HTTP/1.1 with one request per connection ({@code Connection: close}), {@code Content-Length} bodies only (a chunked
 * request is refused with 411), header block at most 8 KB, read timeout 3 s. All routing and checks are in {@link VoiceHttp}.
 */
final class SocketVoiceEndpoint implements VoiceEndpoint {
	private static final byte[] LOOPBACK = {127, 0, 0, 1};
	private static final int MAX_HEADER_BYTES = 8192;

	private final VoiceHttp http;
	private ServerSocket socket;
	private Thread acceptor;
	private ExecutorService pool;

	SocketVoiceEndpoint(VoiceHttp http) {
		this.http = http;
	}

	@Override
	public synchronized int start(int port, int search) throws IOException {
		if (socket != null) {
			return socket.getLocalPort();
		}
		InetAddress loopback = InetAddress.getByAddress(LOOPBACK);
		IOException last = null;
		for (int i = 0; i <= (port == 0 ? 0 : search); i++) {
			int candidate = port == 0 ? 0 : port + i;
			ServerSocket s = new ServerSocket();
			try {
				s.setReuseAddress(false);
				s.bind(new InetSocketAddress(loopback, candidate), 8);
			} catch (BindException e) {
				s.close();
				last = e;
				continue;
			}
			socket = s;
			http.setPort(s.getLocalPort());
			AtomicInteger n = new AtomicInteger();
			pool = Executors.newFixedThreadPool(4, r -> {
				Thread t = new Thread(r, "reiatsu-voice-sock-" + n.incrementAndGet());
				t.setDaemon(true);
				return t;
			});
			acceptor = new Thread(() -> acceptLoop(s), "reiatsu-voice-accept");
			acceptor.setDaemon(true);
			acceptor.start();
			return s.getLocalPort();
		}
		throw last != null ? last : new IOException("no port");
	}

	private void acceptLoop(ServerSocket s) {
		while (!s.isClosed()) {
			try {
				Socket c = s.accept();
				ExecutorService p = pool;
				if (p == null) {
					c.close();
					return;
				}
				try {
					p.execute(() -> serve(c));
				} catch (java.util.concurrent.RejectedExecutionException e) {
					c.close(); // stopping
					return;
				}
			} catch (IOException e) {
				if (s.isClosed()) {
					return;
				}
			}
		}
	}

	private void serve(Socket c) {
		try (c) {
			c.setSoTimeout(3000);
			InputStream in = c.getInputStream();
			byte[] head = readHead(in);
			if (head == null) {
				write(c.getOutputStream(), simple(431, "header too large"));
				return;
			}
			String[] lines = new String(head, StandardCharsets.ISO_8859_1).split("\r\n");
			String[] first = lines[0].split(" ");
			if (first.length < 2) {
				write(c.getOutputStream(), simple(400, "bad request line"));
				return;
			}
			Map<String, String> headers = new HashMap<>();
			for (int i = 1; i < lines.length; i++) {
				int colon = lines[i].indexOf(':');
				if (colon > 0) {
					headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
				}
			}
			if (headers.containsKey("transfer-encoding")) {
				write(c.getOutputStream(), simple(411, "length required"));
				return;
			}
			long declared = 0;
			String len = headers.get("content-length");
			if (len != null) {
				try {
					declared = Long.parseLong(len);
				} catch (NumberFormatException e) {
					write(c.getOutputStream(), simple(400, "bad content-length"));
					return;
				}
			}
			InputStream body;
			if (declared > 0 && declared <= 65_536) {
				byte[] b = in.readNBytes((int) declared);
				body = new ByteArrayInputStream(b);
			} else {
				body = new ByteArrayInputStream(new byte[0]); // too large declared bodies are refused by VoiceHttp (413) unread
			}
			VoiceHttp.Response r = http.handle(new VoiceHttp.Request(first[0], first[1], headers, body, declared));
			write(c.getOutputStream(), render(r));
			drainBriefly(c, in, declared);
		} catch (SocketTimeoutException e) {
			// slow client: drop
		} catch (IOException e) {
			// client went away
		}
	}

	/**
	 * After refusing a body unread (413), swallow what the client is still sending for a moment: closing a socket with
	 * unread data makes the OS reset the connection, and the client would lose the answer.
	 */
	private static void drainBriefly(Socket c, InputStream in, long declared) {
		if (declared <= 65_536) {
			return;
		}
		try {
			c.shutdownOutput();
			c.setSoTimeout(300);
			byte[] sink = new byte[8192];
			long total = 0;
			while (total < 262_144 && in.read(sink) >= 0) {
				total += sink.length;
			}
		} catch (IOException e) {
			// nothing more to do
		}
	}

	/** Reads up to and including the blank line; null when the header block exceeds the limit. */
	private static byte[] readHead(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int last4 = 0;
		int b;
		while ((b = in.read()) >= 0) {
			out.write(b);
			if (out.size() > MAX_HEADER_BYTES) {
				return null;
			}
			last4 = (last4 << 8) | b;
			if (last4 == 0x0D0A0D0A) {
				break;
			}
		}
		return out.toByteArray();
	}

	private static VoiceHttp.Response simple(int status, String message) {
		return new VoiceHttp.Response(status, Map.of("Content-Type", "text/plain; charset=utf-8"),
				message.getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] render(VoiceHttp.Response r) {
		StringBuilder sb = new StringBuilder();
		sb.append("HTTP/1.1 ").append(r.status()).append(' ').append(reason(r.status())).append("\r\n");
		r.headers().forEach((k, v) -> sb.append(k).append(": ").append(v).append("\r\n"));
		sb.append("Content-Length: ").append(r.body().length).append("\r\nConnection: close\r\n\r\n");
		byte[] head = sb.toString().getBytes(StandardCharsets.ISO_8859_1);
		byte[] all = new byte[head.length + r.body().length];
		System.arraycopy(head, 0, all, 0, head.length);
		System.arraycopy(r.body(), 0, all, head.length, r.body().length);
		return all;
	}

	private static void write(OutputStream out, VoiceHttp.Response r) throws IOException {
		write(out, render(r));
	}

	private static void write(OutputStream out, byte[] data) throws IOException {
		out.write(data);
		out.flush();
	}

	private static String reason(int status) {
		return switch (status) {
			case 200 -> "OK";
			case 204 -> "No Content";
			case 400 -> "Bad Request";
			case 403 -> "Forbidden";
			case 404 -> "Not Found";
			case 405 -> "Method Not Allowed";
			case 411 -> "Length Required";
			case 413 -> "Payload Too Large";
			case 415 -> "Unsupported Media Type";
			case 429 -> "Too Many Requests";
			case 431 -> "Request Header Fields Too Large";
			default -> "Error";
		};
	}

	@Override
	public synchronized void stop() {
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				// closing anyway
			}
			socket = null;
		}
		if (pool != null) {
			pool.shutdownNow();
			pool = null;
		}
	}

	@Override
	public String kind() {
		return "socket";
	}
}
