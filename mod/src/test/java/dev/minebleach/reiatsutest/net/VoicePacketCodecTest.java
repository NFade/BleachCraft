package dev.minebleach.reiatsutest.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.core.voice.VoiceFeedback;
import dev.minebleach.reiatsutest.core.voice.VoiceFeedback.Verdict;
import dev.minebleach.reiatsutest.core.voice.VoiceRequest;
import dev.minebleach.reiatsutest.core.voice.VoiceWire;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

/** Wire format of the two voice packets (no Minecraft bootstrap needed: only the byte buffer is used). */
class VoicePacketCodecTest {
	private static PacketByteBuf buf() {
		return new PacketByteBuf(Unpooled.buffer());
	}

	@Test
	void requestRoundTrips() {
		VoiceRequest r = new VoiceRequest(VoiceWire.PROTOCOL, 12345, "chire senbonzakura", true, "ja-JP", 42, List.of("chire senbon", "tire"));
		PacketByteBuf b = buf();
		VoiceTextC2S.write(b, r);
		VoiceRequest back = VoiceTextC2S.read(b);
		assertEquals(r, back);
		assertEquals(0, b.readableBytes());
	}

	@Test
	void requestWithoutUtteranceIndexAndAlternativesRoundTrips() {
		VoiceRequest r = new VoiceRequest(VoiceWire.PROTOCOL, 1, "Hakuren", false, "", -1, List.of());
		PacketByteBuf b = buf();
		VoiceTextC2S.write(b, r);
		VoiceRequest back = VoiceTextC2S.read(b);
		assertEquals(-1, back.utt());
		assertEquals(r, back);
	}

	@Test
	void unicodeSurvives() {
		VoiceRequest r = new VoiceRequest(VoiceWire.PROTOCOL, 3, "散れ 千本桜 Белый лёд", true, "ja-JP", 0, List.of("ちれ"));
		PacketByteBuf b = buf();
		VoiceTextC2S.write(b, r);
		assertEquals(r, VoiceTextC2S.read(b));
	}

	@Test
	void aTextOverTheLimitStillDecodesSoTheServerCanAnswerTooLong() {
		String t = "x".repeat(VoiceWire.MAX_TEXT_CHARS + 50);
		PacketByteBuf b = buf();
		VoiceTextC2S.write(b, new VoiceRequest(VoiceWire.PROTOCOL, 1, t, true, "en-US", 1, List.of()));
		assertEquals(t, VoiceTextC2S.read(b).text());
	}

	@Test
	void aHugeTextOrTooManyAlternativesFailToDecode() {
		PacketByteBuf b = buf();
		b.writeVarInt(VoiceWire.PROTOCOL);
		b.writeVarInt(1);
		b.writeString("y".repeat(5000), 32767); // a hostile client does not use our own writer
		b.writeBoolean(true);
		b.writeString("en-US");
		b.writeVarLong(1);
		b.writeVarInt(0);
		assertThrows(DecoderException.class, () -> VoiceTextC2S.read(b));

		PacketByteBuf c = buf();
		c.writeVarInt(VoiceWire.PROTOCOL);
		c.writeVarInt(1);
		c.writeString("hakuren");
		c.writeBoolean(true);
		c.writeString("en-US");
		c.writeVarLong(1);
		c.writeVarInt(VoiceWire.DECODE_MAX_ALTERNATIVES + 1);
		assertThrows(DecoderException.class, () -> VoiceTextC2S.read(c));
	}

	@Test
	void ourWriterNeverProducesAnUndecodablePacket() {
		String huge = "z".repeat(10_000);
		PacketByteBuf b = buf();
		VoiceTextC2S.write(b, new VoiceRequest(VoiceWire.PROTOCOL, 1, huge, true, "en-US-with-a-very-long-tag", 1,
				List.of(huge, huge, huge, huge, huge, huge, huge, huge, huge, huge)));
		VoiceRequest back = VoiceTextC2S.read(b);
		assertEquals(VoiceWire.DECODE_MAX_TEXT_CHARS, back.text().length());
		assertEquals(VoiceWire.MAX_LANG_CHARS, back.lang().length());
		assertEquals(VoiceWire.DECODE_MAX_ALTERNATIVES, back.alts().size());
	}

	@Test
	void feedbackRoundTrips() {
		VoiceFeedback f = new VoiceFeedback(77, Verdict.DENIED, "byakuya.bankai.release", "bankai senbonzakura kageyoshi", "fired",
				ResultCode.DENIED_REIATSU.code(), 987, "SHIKAI");
		PacketByteBuf b = buf();
		VoiceFeedbackS2C.write(b, f);
		VoiceFeedback back = VoiceFeedbackS2C.read(b);
		assertEquals(f, back);
		assertEquals(0, b.readableBytes());
	}

	@Test
	void feedbackWithoutCommandAndPendingRoundTrip() {
		VoiceFeedback none = new VoiceFeedback(5, Verdict.NO_MATCH, null, "i found a diamond", "no_match", -1, 0, "BASE");
		PacketByteBuf b = buf();
		VoiceFeedbackS2C.write(b, none);
		VoiceFeedback back = VoiceFeedbackS2C.read(b);
		assertNull(back.commandId());
		assertEquals(-1, back.resultCode());
		assertEquals(none, back);

		PacketByteBuf p = buf();
		VoiceFeedbackS2C.write(p, VoiceFeedback.pending(9, "common.seal"));
		VoiceFeedback pb = VoiceFeedbackS2C.read(p);
		assertEquals(Verdict.PENDING, pb.verdict());
		assertEquals("common.seal", pb.commandId());
		assertEquals(9, pb.reqId());

		PacketByteBuf r = buf();
		VoiceFeedbackS2C.write(r, VoiceFeedback.rejected(3, "rate_limited"));
		assertEquals(Verdict.REJECTED, VoiceFeedbackS2C.read(r).verdict());
	}

	@Test
	void everyVerdictHasAStableCode() {
		for (Verdict v : Verdict.values()) {
			assertEquals(v, Verdict.fromCode(v.code()));
		}
		assertEquals(Verdict.REJECTED, Verdict.fromCode(99), "an unknown code is treated as a refusal");
		assertTrue(Verdict.OK.fired() && Verdict.DENIED.fired() && !Verdict.NO_MATCH.fired());
	}

	@Test
	void payloadIdsAreTheRegisteredNames() {
		assertEquals("reiatsu_test:voice_text", VoiceTextC2S.ID.id().toString());
		assertEquals("reiatsu_test:voice_feedback", VoiceFeedbackS2C.ID.id().toString());
	}
}
