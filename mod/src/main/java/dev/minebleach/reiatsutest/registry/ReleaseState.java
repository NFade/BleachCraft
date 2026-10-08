package dev.minebleach.reiatsutest.registry;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.StringIdentifiable;

/** Render state mirrored on the item stack (ADR section 1): SEALED | SHIKAI | BANKAI. */
public enum ReleaseState implements StringIdentifiable {
	SEALED("sealed"),
	SHIKAI("shikai"),
	BANKAI("bankai");

	public static final Codec<ReleaseState> CODEC = StringIdentifiable.createCodec(ReleaseState::values);
	public static final PacketCodec<io.netty.buffer.ByteBuf, ReleaseState> PACKET_CODEC =
			PacketCodecs.indexed(i -> values()[i], ReleaseState::ordinal);

	private final String id;

	ReleaseState(String id) {
		this.id = id;
	}

	@Override
	public String asString() {
		return id;
	}
}
