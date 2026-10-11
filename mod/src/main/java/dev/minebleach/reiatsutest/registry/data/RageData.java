package dev.minebleach.reiatsutest.registry.data;

import dev.minebleach.reiatsutest.core.rage.RageState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

/** Attachment {@code reiatsu_test:rage}: tenths of a point. Not persistent (rage starts at zero on join), owner-only sync (HUD). */
public record RageData(int value, int max) {
	public static final RageData EMPTY = new RageData(0, 1000);

	public static final PacketCodec<RegistryByteBuf, RageData> PACKET_CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeInt(v.value);
				buf.writeInt(v.max);
			},
			buf -> new RageData(buf.readInt(), buf.readInt()));

	public static RageData of(RageState s) {
		return new RageData(s.value(), s.max());
	}

	public RageState toState() {
		return new RageState(value, max);
	}
}
