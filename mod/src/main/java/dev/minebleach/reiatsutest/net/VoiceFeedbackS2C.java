package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.voice.VoiceFeedback;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * S2C to the sender of a {@link VoiceTextC2S}: what the server made of the text (verdict, command, reason, result of the
 * command, state). Feeds the voice HUD and the answer the client bridge gives to its browser page. An empty string stands for
 * "no command id".
 */
public record VoiceFeedbackS2C(VoiceFeedback feedback) implements CustomPayload {
	public static final CustomPayload.Id<VoiceFeedbackS2C> ID = new CustomPayload.Id<>(ReiatsuTest.id("voice_feedback"));
	public static final PacketCodec<RegistryByteBuf, VoiceFeedbackS2C> CODEC = PacketCodec.of(
			(v, buf) -> write(buf, v.feedback),
			buf -> new VoiceFeedbackS2C(read(buf)));

	private static final int ID_MAX = 96;
	private static final int TEXT_MAX = 256;
	private static final int REASON_MAX = 32;
	private static final int STATE_MAX = 24;

	public static void write(PacketByteBuf buf, VoiceFeedback f) {
		buf.writeVarInt(f.reqId());
		buf.writeByte(f.verdict().code());
		buf.writeString(clip(f.commandId() == null ? "" : f.commandId(), ID_MAX), ID_MAX);
		buf.writeString(clip(f.heard(), TEXT_MAX), TEXT_MAX);
		buf.writeString(clip(f.reason(), REASON_MAX), REASON_MAX);
		buf.writeByte(f.resultCode());
		buf.writeVarInt(f.confidencePermille());
		buf.writeString(clip(f.state(), STATE_MAX), STATE_MAX);
	}

	public static VoiceFeedback read(PacketByteBuf buf) {
		int reqId = buf.readVarInt();
		VoiceFeedback.Verdict verdict = VoiceFeedback.Verdict.fromCode(buf.readByte());
		String cmd = buf.readString(ID_MAX);
		String heard = buf.readString(TEXT_MAX);
		String reason = buf.readString(REASON_MAX);
		int result = buf.readByte();
		int permille = buf.readVarInt();
		String state = buf.readString(STATE_MAX);
		return new VoiceFeedback(reqId, verdict, cmd.isEmpty() ? null : cmd, heard, reason, result, permille, state);
	}

	private static String clip(String s, int max) {
		return s.length() > max ? s.substring(0, max) : s;
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
