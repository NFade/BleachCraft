package dev.minebleach.reiatsutest.registry.data;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ShikaiMode;
import dev.minebleach.reiatsutest.core.state.ZanpakutoSnapshot;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

/**
 * Attachment {@code reiatsu_test:zanpakuto} (ADR section 3): {@code {byte character, byte state, int stateSinceTick,
 * byte shikaiMode, int bankaiEndTick}}. Server ticks. Synced to everyone, not persistent.
 */
public record ZanpakutoData(byte character, byte state, int stateSinceTick, byte shikaiMode, int bankaiEndTick) {
	public static final ZanpakutoData SEALED = new ZanpakutoData((byte) 0, (byte) 0, 0, (byte) 0, 0);

	public static final PacketCodec<RegistryByteBuf, ZanpakutoData> PACKET_CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeByte(v.character);
				buf.writeByte(v.state);
				buf.writeInt(v.stateSinceTick);
				buf.writeByte(v.shikaiMode);
				buf.writeInt(v.bankaiEndTick);
			},
			buf -> new ZanpakutoData(buf.readByte(), buf.readByte(), buf.readInt(), buf.readByte(), buf.readInt()));

	public static ZanpakutoData of(ZanpakutoSnapshot s) {
		return new ZanpakutoData(s.character().code, s.state().code(), (int) s.stateSinceTick(), s.shikaiMode().code(),
				(int) s.bankaiEndTick());
	}

	public CharacterId characterId() {
		return CharacterId.fromCode(character);
	}

	public ZanpakutoState zanpakutoState() {
		return ZanpakutoState.fromCode(state);
	}

	public ShikaiMode mode() {
		return ShikaiMode.fromCode(shikaiMode);
	}
}
