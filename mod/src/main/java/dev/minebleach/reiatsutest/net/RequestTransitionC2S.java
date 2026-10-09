package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** C2S: ask the server to change state (ADR section 3). {@code targetState} is a ZanpakutoState code, {@code source} a RequestSource code. */
public record RequestTransitionC2S(byte targetState, byte source, int clientSeq) implements CustomPayload {
	public static final CustomPayload.Id<RequestTransitionC2S> ID = new CustomPayload.Id<>(ReiatsuTest.id("request_transition"));
	public static final PacketCodec<RegistryByteBuf, RequestTransitionC2S> CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeByte(v.targetState);
				buf.writeByte(v.source);
				buf.writeVarInt(v.clientSeq);
			},
			buf -> new RequestTransitionC2S(buf.readByte(), buf.readByte(), buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
