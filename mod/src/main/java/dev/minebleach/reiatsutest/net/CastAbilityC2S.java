package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** C2S: cast an ability. Aim is never sent: the server uses the player's own position and look vector. */
public record CastAbilityC2S(byte abilityId, byte source, int clientSeq) implements CustomPayload {
	public static final CustomPayload.Id<CastAbilityC2S> ID = new CustomPayload.Id<>(ReiatsuTest.id("cast_ability"));
	public static final PacketCodec<RegistryByteBuf, CastAbilityC2S> CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeByte(v.abilityId);
				buf.writeByte(v.source);
				buf.writeVarInt(v.clientSeq);
			},
			buf -> new CastAbilityC2S(buf.readByte(), buf.readByte(), buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
