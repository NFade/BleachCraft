package dev.minebleach.reiatsutest.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.voice.VoiceMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** G3: the optional "alts" array of a final result. */
class VoiceHttpAltsTest {
	private static VoiceMessage parse(String json) {
		return VoiceHttp.parse(json.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void altsAreParsedInOrderAndCapped() {
		VoiceMessage m = parse("{\"text\":\"a\",\"final\":true,\"alts\":[\"b\",\" \",\"c\",\"d\",\"e\",\"f\"]}");
		assertEquals(List.of("b", "c", "d", "e"), m.alternatives());
	}

	@Test
	void missingAltsAreEmpty() {
		assertTrue(parse("{\"text\":\"a\"}").alternatives().isEmpty());
		assertTrue(parse("{\"text\":\"a\",\"alts\":\"not an array\"}").alternatives().isEmpty());
	}

	@Test
	void unicodeAltsSurvive() {
		assertEquals(List.of("舞え、袖白雪", "Мае Сода но Сираюки"),
				parse("{\"text\":\"x\",\"alts\":[\"舞え、袖白雪\",\"Мае Сода но Сираюки\"]}").alternatives());
	}

	@Test
	void aWronglyTypedAltIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> parse("{\"text\":\"a\",\"alts\":[{\"x\":1}]}"));
	}
}
