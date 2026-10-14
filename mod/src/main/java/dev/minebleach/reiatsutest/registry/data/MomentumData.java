package dev.minebleach.reiatsutest.registry.data;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

/** Attachment {@code reiatsu_test:momentum} (R11 T6): the frost stacks of Rukia and the most she can hold now. Not persistent, owner-only sync (HUD). */
public record MomentumData(int stacks, int max) {
	public static final MomentumData EMPTY = new MomentumData(0, 0);

	public static final PacketCodec<RegistryByteBuf, MomentumData> PACKET_CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeByte(v.stacks);
				buf.writeByte(v.max);
			},
			buf -> new MomentumData(buf.readByte(), buf.readByte()));
}
