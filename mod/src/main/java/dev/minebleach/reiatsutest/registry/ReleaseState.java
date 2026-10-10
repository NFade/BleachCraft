package dev.minebleach.reiatsutest.registry;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.StringIdentifiable;

/** Render state mirrored on the item stack (ADR section 1): SEALED | BASE | SHIKAI | BANKAI. */
public enum ReleaseState implements StringIdentifiable {
	SEALED("sealed"),
	/** Drawn base form (B4 step 3): the sealed-drawn model, scabbard in the other hand. */
	BASE("base"),
	SHIKAI("shikai"),
	BANKAI("bankai");

	public static final Codec<ReleaseState> CODEC = StringIdentifiable.createCodec(ReleaseState::values);
	public static final PacketCodec<io.netty.buffer.ByteBuf, ReleaseState> PACKET_CODEC =
			PacketCodecs.indexed(i -> values()[i], ReleaseState::ordinal);

	private final String id;

	ReleaseState(String id) {
		this.id = id;
	}

	/** Same order as ZanpakutoState (SEALED, BASE, SHIKAI, BANKAI). */
	public static ReleaseState of(dev.minebleach.reiatsutest.core.state.ZanpakutoState state) {
		return values()[state.ordinal()];
	}

	@Override
	public String asString() {
		return id;
	}
}
