package dev.minebleach.reiatsutest.registry.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

/** Attachment {@code reiatsu_test:reiatsu}: tenths of a point. Persistent, owner-only sync (HUD). */
public record ReiatsuData(int value, int max) {
	public static final ReiatsuData FULL = new ReiatsuData(1000, 1000);

	public static final Codec<ReiatsuData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("value").forGetter(ReiatsuData::value),
			Codec.INT.fieldOf("max").forGetter(ReiatsuData::max)).apply(i, ReiatsuData::new));

	public static final PacketCodec<RegistryByteBuf, ReiatsuData> PACKET_CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeInt(v.value);
				buf.writeInt(v.max);
			},
			buf -> new ReiatsuData(buf.readInt(), buf.readInt()));

	public static ReiatsuData of(ReiatsuState s) {
		return new ReiatsuData(s.value(), s.max());
	}

	public ReiatsuState toState() {
		return new ReiatsuState(value, max);
	}
}
