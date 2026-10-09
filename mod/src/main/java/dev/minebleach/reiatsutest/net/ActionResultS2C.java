package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** S2C to the caster: answer to every request. {@code result} is a ResultCode byte. */
public record ActionResultS2C(int clientSeq, byte result) implements CustomPayload {
	public static final CustomPayload.Id<ActionResultS2C> ID = new CustomPayload.Id<>(ReiatsuTest.id("action_result"));
	public static final PacketCodec<RegistryByteBuf, ActionResultS2C> CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeVarInt(v.clientSeq);
				buf.writeByte(v.result);
			},
			buf -> new ActionResultS2C(buf.readVarInt(), buf.readByte()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
