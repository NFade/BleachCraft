package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * C2S: ask for a shunpo (B4 step 5). Like {@link CastAbilityC2S} it carries no position or aim: the server teleports along its
 * own view of the player's look direction. The answer is an {@code action_result} with the same sequence number and, when
 * accepted, an {@code effect_event} (id 40) to the player and the players tracking them.
 */
public record ShunpoC2S(byte source, int clientSeq) implements CustomPayload {
	public static final CustomPayload.Id<ShunpoC2S> ID = new CustomPayload.Id<>(ReiatsuTest.id("shunpo"));
	public static final PacketCodec<RegistryByteBuf, ShunpoC2S> CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeByte(v.source);
				buf.writeVarInt(v.clientSeq);
			},
			buf -> new ShunpoC2S(buf.readByte(), buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
