package dev.minebleach.reiatsutest.voice;

import java.io.IOException;
import java.io.InputStream;

/** The bridge page {@code voice-bridge/index.html}, packed into the mod jar under {@code assets/reiatsu_test/voice/} by processResources. */
public final class VoicePage {
	public static final String RESOURCE = "/assets/reiatsu_test/voice/index.html";
	private static volatile byte[] cached;

	private VoicePage() {
	}

	/** The page bytes, or null when the resource is missing. */
	public static byte[] bytes() {
		byte[] b = cached;
		if (b == null) {
			try (InputStream in = VoicePage.class.getResourceAsStream(RESOURCE)) {
				b = in == null ? null : in.readAllBytes();
			} catch (IOException e) {
				b = null;
			}
			cached = b;
		}
		return b;
	}
}
