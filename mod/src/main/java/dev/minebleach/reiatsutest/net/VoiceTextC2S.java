package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.voice.VoiceRequest;
import dev.minebleach.reiatsutest.core.voice.VoiceWire;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * C2S: what the browser of this player recognised (the client bridge forwards it). Text only, never audio and never a command:
 * the server runs the phrase matcher and all gates itself ({@code core/voice/VoiceIngest}). The decoder accepts a little more
 * than the limits so the server can answer {@code too_long} instead of dropping the connection; see {@link VoiceWire}.
 */
public record VoiceTextC2S(VoiceRequest request) implements CustomPayload {
	public static final CustomPayload.Id<VoiceTextC2S> ID = new CustomPayload.Id<>(ReiatsuTest.id("voice_text"));
	public static final PacketCodec<RegistryByteBuf, VoiceTextC2S> CODEC = PacketCodec.of(
			(v, buf) -> write(buf, v.request),
			buf -> new VoiceTextC2S(read(buf)));

	public static void write(PacketByteBuf buf, VoiceRequest r) {
		buf.writeVarInt(r.protocol());
		buf.writeVarInt(r.reqId());
		buf.writeString(clip(r.text(), VoiceWire.DECODE_MAX_TEXT_CHARS), VoiceWire.DECODE_MAX_TEXT_CHARS);
		buf.writeBoolean(r.isFinal());
		buf.writeString(clip(r.lang(), VoiceWire.MAX_LANG_CHARS), VoiceWire.MAX_LANG_CHARS);
		buf.writeVarLong(Math.max(0L, r.utt() + 1)); // -1 (absent) travels as 0
		int n = Math.min(r.alts().size(), VoiceWire.DECODE_MAX_ALTERNATIVES);
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			buf.writeString(clip(r.alts().get(i), VoiceWire.DECODE_MAX_TEXT_CHARS), VoiceWire.DECODE_MAX_TEXT_CHARS);
		}
	}

	public static VoiceRequest read(PacketByteBuf buf) {
		int protocol = buf.readVarInt();
		int reqId = buf.readVarInt();
		String text = buf.readString(VoiceWire.DECODE_MAX_TEXT_CHARS);
		boolean fin = buf.readBoolean();
		String lang = buf.readString(VoiceWire.MAX_LANG_CHARS);
		long utt = buf.readVarLong() - 1;
		int n = buf.readVarInt();
		if (n < 0 || n > VoiceWire.DECODE_MAX_ALTERNATIVES) {
			throw new io.netty.handler.codec.DecoderException("too many alternatives: " + n);
		}
		List<String> alts = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			alts.add(buf.readString(VoiceWire.DECODE_MAX_TEXT_CHARS));
		}
		return new VoiceRequest(protocol, reqId, text, fin, lang, utt, alts);
	}

	private static String clip(String s, int max) {
		return s.length() > max ? s.substring(0, max) : s;
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
