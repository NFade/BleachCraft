package dev.minebleach.reiatsutest.core.combat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Loads the shipped Uryu animation files for the tests (the same resources the game reads). */
final class UryuTestData {
	private static final Map<String, UryuPoseTrack> CACHE = new HashMap<>();

	private UryuTestData() {
	}

	static synchronized UryuPoseTrack track(String anim) {
		return CACHE.computeIfAbsent(anim, a -> {
			try (InputStream in = UryuTestData.class.getResourceAsStream(UryuAnims.resource(a))) {
				if (in == null) {
					throw new IllegalStateException("missing " + UryuAnims.resource(a));
				}
				return UryuPoseTrack.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	static int endTick(String anim) {
		return track(anim).endTick();
	}

	/** One frame of an animation (no previous one, full weight, breathing and tremor as given). */
	static UryuTpPose.Frame frame(String anim, double tick, boolean mirror, double lookPitch, double time, double breath, boolean tremor) {
		return new UryuTpPose.Frame(new UryuTpPose.Layer(track(anim), UryuAnims.meta(anim), tick), null, 1.0, 1.0, mirror, lookPitch, time, breath, tremor);
	}
}
