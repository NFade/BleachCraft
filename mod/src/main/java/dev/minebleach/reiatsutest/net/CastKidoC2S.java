package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** C2S: cast a kido spell (B5). Like {@link CastAbilityC2S}: no aim is sent, the server uses the player's own eye and look. */
public record CastKidoC2S(byte kidoId, byte source, int clientSeq) implements CustomPayload {
	public static final CustomPayload.Id<CastKidoC2S> ID = new CustomPayload.Id<>(ReiatsuTest.id("cast_kido"));
	public static final PacketCodec<RegistryByteBuf, CastKidoC2S> CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeByte(v.kidoId);
				buf.writeByte(v.source);
				buf.writeVarInt(v.clientSeq);
			},
			buf -> new CastKidoC2S(buf.readByte(), buf.readByte(), buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
