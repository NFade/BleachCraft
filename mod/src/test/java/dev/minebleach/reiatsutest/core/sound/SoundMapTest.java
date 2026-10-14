package dev.minebleach.reiatsutest.core.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** R11 T12: the pure sound layer. The map decides between a loaded clip, the vanilla fallback and silence; the game only plays what it says. */
class SoundMapTest {
	private static final String EV = SoundIds.BYAKUYA_SHIKAI_RELEASE_FLASH;
	private static final String CLIP = "reiatsu_test:" + EV;
	private static final SoundMap.Availability NOTHING = id -> false;
	private static final SoundMap.Availability EVERYTHING = id -> true;

	private static SoundMap map(String json) {
		return SoundMap.fromJson(JsonParser.parseString(json).getAsJsonObject());
	}

	@Test
	void withoutAClipTheVanillaFallbackOfTheCodePlaysUnchanged() {
		SoundMap m = SoundMap.defaults();
		SoundMap.Decision d = m.decide(EV, "block.beacon.activate", NOTHING, 1000, new Random(1));
		assertEquals(SoundMap.Kind.FALLBACK, d.kind());
		assertEquals("block.beacon.activate", d.soundId());
		assertEquals(1.0, d.volume(), 0.0, "the default volume factor keeps the old loudness");
		assertTrue(Double.isNaN(d.pitch()), "the fallback keeps the pitch of the caller");
		assertEquals("players", d.category());
	}

	@Test
	void everyLayerOfAMomentPlaysWithoutAClip() {
		SoundMap m = SoundMap.defaults();
		for (int i = 0; i < 5; i++) { // the minimum interval is for clips only: five vanilla layers in a row all play, as before
			assertEquals(SoundMap.Kind.FALLBACK, m.decide(SoundIds.GENERAL_PARRY, "block.bell.use", NOTHING, 1000 + i, new Random(i)).kind());
		}
	}

	@Test
	void aLoadedClipReplacesTheFallback() {
		SoundMap m = SoundMap.defaults();
		SoundMap.Decision d = m.decide(EV, "block.beacon.activate", EVERYTHING, 1000, new Random(1));
		assertEquals(SoundMap.Kind.FILE, d.kind());
		assertEquals(CLIP, d.soundId());
		assertEquals(1.0, d.volume(), 0.0);
		assertEquals(1.0, d.pitch(), 0.0);
		assertEquals(32.0, d.range(), 0.0);
	}

	@Test
	void onlyTheAvailabilityOfTheClipDecides() {
		SoundMap m = SoundMap.defaults();
		SoundMap.Availability onlyThis = id -> id.equals(CLIP);
		assertEquals(SoundMap.Kind.FILE, m.decide(EV, "x", onlyThis, 1000, new Random(1)).kind());
		assertEquals(SoundMap.Kind.FALLBACK, m.decide(SoundIds.GENERAL_PARRY, "x", onlyThis, 1000, new Random(1)).kind(), "another event has no clip");
	}

	@Test
	void anEventWithNoFallbackAndNoClipIsSilent() {
		SoundMap m = SoundMap.defaults();
		assertEquals(SoundMap.Kind.SILENT, m.decide(SoundIds.GENERAL_PRESSURE_RELEASE, null, NOTHING, 1000, new Random(1)).kind());
		assertEquals(SoundMap.Kind.SILENT, m.decide(SoundIds.GENERAL_PRESSURE_RELEASE, "", NOTHING, 1000, new Random(1)).kind());
	}

	@Test
	void anUnknownEventKeepsTheOldBehaviour() {
		SoundMap m = SoundMap.defaults();
		assertEquals(SoundMap.Kind.FALLBACK, m.decide("nope.nope", "block.bell.use", EVERYTHING, 0, new Random(1)).kind(), "never a clip for an id nobody registered");
		assertEquals(SoundMap.Kind.SILENT, m.decide("nope.nope", null, EVERYTHING, 0, new Random(1)).kind());
	}

	@Test
	void theMinimumIntervalLimitsClipsOfTheSameEvent() {
		SoundMap m = SoundMap.defaults(); // 80 ms by default
		assertEquals(SoundMap.Kind.FILE, m.decide(EV, "x", EVERYTHING, 1000, new Random(1)).kind());
		assertEquals(SoundMap.Kind.LIMITED, m.decide(EV, "x", EVERYTHING, 1040, new Random(1)).kind(), "40 ms later: the second vanilla layer of the moment is dropped");
		assertEquals(SoundMap.Kind.LIMITED, m.decide(EV, "x", EVERYTHING, 1079, new Random(1)).kind());
		assertEquals(SoundMap.Kind.FILE, m.decide(EV, "x", EVERYTHING, 1080, new Random(1)).kind(), "exactly the interval later it plays again");
		assertEquals(SoundMap.Kind.FILE, m.decide(SoundIds.GENERAL_PARRY, "x", EVERYTHING, 1081, new Random(1)).kind(), "the interval is per event");
	}

	@Test
	void aLimitedCallDoesNotPushTheClockAndZeroDisablesTheLimit() {
		SoundMap m = map("{\"events\":{\"" + EV + "\":{\"minIntervalMs\":100}, \"" + SoundIds.GENERAL_PARRY + "\":{\"minIntervalMs\":0}}}");
		assertEquals(SoundMap.Kind.FILE, m.decide(EV, "x", EVERYTHING, 1000, new Random(1)).kind());
		assertEquals(SoundMap.Kind.LIMITED, m.decide(EV, "x", EVERYTHING, 1090, new Random(1)).kind());
		assertEquals(SoundMap.Kind.FILE, m.decide(EV, "x", EVERYTHING, 1100, new Random(1)).kind(), "measured from the last play, not from the dropped call");
		assertEquals(SoundMap.Kind.FILE, m.decide(SoundIds.GENERAL_PARRY, "x", EVERYTHING, 5, new Random(1)).kind());
		assertEquals(SoundMap.Kind.FILE, m.decide(SoundIds.GENERAL_PARRY, "x", EVERYTHING, 5, new Random(1)).kind(), "0 ms: no limit");
	}

	@Test
	void aBackwardsClockNeverBlocksForever() {
		SoundMap m = SoundMap.defaults();
		assertEquals(SoundMap.Kind.FILE, m.decide(EV, "x", EVERYTHING, 5000, new Random(1)).kind());
		assertEquals(SoundMap.Kind.FILE, m.decide(EV, "x", EVERYTHING, 100, new Random(1)).kind());
	}

	@Test
	void variantsAreChosenAmongTheLoadedOnesAndNeverRepeatInARow() {
		SoundMap m = map("{\"events\":{\"" + EV + "\":{\"minIntervalMs\":0,\"files\":[\"a\",\"b\",\"reiatsu_test:c\",\"missing\"]}}}");
		SoundMap.Availability loaded = id -> !id.endsWith("missing");
		Set<String> seen = new HashSet<>();
		String prev = null;
		Random rnd = new Random(7);
		for (int i = 0; i < 60; i++) {
			SoundMap.Decision d = m.decide(EV, "x", loaded, i, rnd);
			assertEquals(SoundMap.Kind.FILE, d.kind());
			assertNotNull(d.soundId());
			assertFalse(d.soundId().endsWith("missing"), "an unloaded variant is never picked");
			assertTrue(!d.soundId().equals(prev), "the same variant twice in a row: " + d.soundId());
			prev = d.soundId();
			seen.add(d.soundId());
		}
		assertEquals(Set.of("reiatsu_test:a", "reiatsu_test:b", "reiatsu_test:c"), seen, "all loaded variants get played; the namespace is added");
	}

	@Test
	void pickVariantWithOneVariantAlwaysGivesIt() {
		assertEquals("only", SoundMap.pickVariant(List.of("only"), "only", new Random(3)));
	}

	@Test
	void aFilesListCanPointAtAnotherEventOrBeEmpty() {
		SoundMap m = map("{\"events\":{\"" + EV + "\":{\"files\":[\"" + SoundIds.GENERAL_PARRY + "\"]}, \"" + SoundIds.GENERAL_BLOCK_HIT + "\":{\"files\":[]}}}");
		SoundMap.Decision d = m.decide(EV, "x", EVERYTHING, 0, new Random(1));
		assertEquals("reiatsu_test:" + SoundIds.GENERAL_PARRY, d.soundId());
		assertEquals(SoundMap.Kind.FALLBACK, m.decide(SoundIds.GENERAL_BLOCK_HIT, "v", EVERYTHING, 0, new Random(1)).kind(), "no files: always the fallback");
	}

	@Test
	void theConfigFallbackReplacesTheCodesLayersOnlyWhenSet() {
		SoundMap m = map("{\"events\":{\"" + EV + "\":{\"fallback\":\"Entity.Player.Levelup\"}}}");
		SoundMap.Decision d = m.decide(EV, "block.beacon.activate", NOTHING, 0, new Random(1));
		assertEquals(SoundMap.Kind.FALLBACK, d.kind());
		assertEquals("entity.player.levelup", d.soundId());
		assertEquals(SoundMap.Kind.FILE, m.decide(EV, "block.beacon.activate", EVERYTHING, 10_000, new Random(1)).kind(), "a clip still wins over the config fallback");
		assertEquals("block.bell.use", SoundMap.defaults().decide(EV, "block.bell.use", NOTHING, 0, new Random(1)).soundId());
	}

	@Test
	void volumePitchRangeAndCategoryComeFromTheConfig() {
		SoundMap m = map("{\"events\":{\"" + EV + "\":{\"volume\":1.5,\"pitch\":[0.8,1.2],\"range\":64,\"category\":\"Voice\"}}}");
		double lo = 9;
		double hi = 0;
		Random rnd = new Random(5);
		for (int i = 0; i < 200; i++) {
			SoundMap.Decision d = m.decide(EV, "x", EVERYTHING, i * 1000L, rnd);
			assertEquals(1.5, d.volume(), 0.0);
			assertEquals(64.0, d.range(), 0.0);
			assertEquals("voice", d.category());
			lo = Math.min(lo, d.pitch());
			hi = Math.max(hi, d.pitch());
		}
		assertTrue(lo >= 0.8 && hi <= 1.2 && hi - lo > 0.2, "pitch is random inside [0.8, 1.2]: " + lo + ".." + hi);
		SoundMap.Decision fb = m.decide(EV, "block.bell.use", NOTHING, 0, rnd);
		assertEquals(1.5, fb.volume(), 0.0, "the volume factor scales the fallback too");
	}

	@Test
	void aSinglePitchNumberIsAFixedPitch() {
		SoundMap m = map("{\"events\":{\"" + EV + "\":{\"pitch\":1.3}}}");
		assertEquals(1.3, m.entry(EV).pitchMin(), 0.0);
		assertEquals(1.3, m.entry(EV).pitchMax(), 0.0);
	}

	@Test
	void garbageInTheFileGivesTheDefaultsAndClampsNumbers() {
		SoundMap def = SoundMap.defaults();
		SoundMap m = map("{\"events\":{\"" + EV + "\":{\"files\":7,\"fallback\":5,\"volume\":\"loud\",\"pitch\":\"high\",\"minIntervalMs\":null,\"range\":[1],\"category\":42},"
				+ "\"" + SoundIds.GENERAL_PARRY + "\":{\"volume\":999,\"pitch\":[9,0],\"minIntervalMs\":-5,\"range\":0,\"category\":\"nonsense\",\"fallback\":\"Bad Id!\"}}}");
		assertEquals(def.entry(EV), m.entry(EV), "every malformed key keeps its default");
		SoundEntry p = m.entry(SoundIds.GENERAL_PARRY);
		assertEquals(SoundEntry.MAX_VOLUME, p.volume(), 0.0);
		assertEquals(0.5, p.pitchMin(), 0.0);
		assertEquals(2.0, p.pitchMax(), 0.0, "a reversed or out-of-range pitch is clamped and ordered");
		assertEquals(0, p.minIntervalMs());
		assertEquals(SoundEntry.MIN_RANGE, p.range(), 0.0);
		assertEquals("players", p.category());
		assertEquals("", p.fallback(), "an invalid vanilla id is ignored");
	}

	@Test
	void notAnObjectOrAnEmptyObjectIsTheDefaults() {
		assertEquals(SoundMap.defaults().entries(), SoundMap.fromJson(new JsonObject()).entries());
		assertEquals(SoundMap.defaults().entries(), SoundMap.fromJson(JsonParser.parseString("{\"events\":[1,2]}").getAsJsonObject()).entries());
	}

	@Test
	void unknownEventsInTheFileAreIgnored() {
		SoundMap m = map("{\"events\":{\"made.up\":{\"volume\":2}}}");
		assertNull(m.entry("made.up"));
		assertEquals(SoundIds.ALL.size(), m.entries().size());
	}

	@Test
	void writtenConfigReadsBackIdentically() {
		SoundMap m = map("{\"events\":{\"" + EV + "\":{\"files\":[\"x\",\"y\"],\"fallback\":\"block.bell.use\",\"volume\":0.5,\"pitch\":[0.9,1.1],\"minIntervalMs\":250,\"range\":40,"
				+ "\"category\":\"master\"}}}");
		JsonObject json = m.toJson();
		assertEquals(SoundMap.VERSION, json.get("configVersion").getAsInt());
		SoundMap back = SoundMap.fromJson(json);
		assertEquals(m.entries(), back.entries());
		assertEquals(List.of("reiatsu_test:x", "reiatsu_test:y"), back.entry(EV).files());
	}

	@Test
	void theDefaultsCoverEveryEventWithSaneValues() {
		SoundMap m = SoundMap.defaults();
		assertEquals(SoundIds.ALL.size(), m.entries().size());
		for (String id : SoundIds.ALL) {
			SoundEntry e = m.entry(id);
			assertNotNull(e, id);
			assertEquals(id, e.id());
			assertEquals(List.of("reiatsu_test:" + id), e.files(), "the default clip set is the event itself");
			assertEquals("", e.fallback(), "no override of the code's vanilla layers by default: the old sound is kept");
			assertEquals(1.0, e.volume(), 0.0);
			assertEquals(1.0, e.pitchMin(), 0.0);
			assertEquals(1.0, e.pitchMax(), 0.0);
			assertTrue(e.minIntervalMs() >= 0 && e.minIntervalMs() <= 5000, id);
			assertTrue(e.range() >= 8 && e.range() <= 128, id);
			assertTrue(SoundEntry.CATEGORIES.contains(e.category()), id);
		}
	}

	@Test
	void validSoundIds() {
		assertTrue(SoundEntry.validSoundId("block.beacon.activate"));
		assertTrue(SoundEntry.validSoundId("minecraft:block.beacon.activate"));
		assertTrue(SoundEntry.validSoundId("reiatsu_test:anime/x_1"));
		assertFalse(SoundEntry.validSoundId(""));
		assertFalse(SoundEntry.validSoundId("A"));
		assertFalse(SoundEntry.validSoundId("a b"));
		assertFalse(SoundEntry.validSoundId("a:b:c"));
		assertFalse(SoundEntry.validSoundId(":a"));
		assertFalse(SoundEntry.validSoundId("x".repeat(129)));
	}
}
