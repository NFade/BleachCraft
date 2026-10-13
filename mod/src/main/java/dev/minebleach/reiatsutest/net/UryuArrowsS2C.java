package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * S2C of Uryu's placeholder visuals. {@link #ARROWS}: the full list of Heilig Pfeil near the receiver this tick, {@code ids[i]} and
 * {@code data[7 i .. 7 i + 6]} = x y z vx vy vz focus; an empty list clears them. {@link #BURST}: data = {x, y, z, radius, early (0/1)},
 * a Sprenger explosion. {@link #LINE}: data = {x1, y1, z1, x2, y2, z2, half width}, the Sternenstaub. {@link #TRAP}: data = {x, y, z, radius, ticks to the explosion}, a new Sprenger pentagon.
 */
public record UryuArrowsS2C(byte kind, int tick, int[] ids, float[] data) implements CustomPayload {
	public static final byte ARROWS = 0;
	public static final byte BURST = 1;
	public static final byte LINE = 2;
	public static final byte TRAP = 3;
	public static final int MAX_ARROWS = 48;
	public static final int PER_ARROW = 7;

	public static final CustomPayload.Id<UryuArrowsS2C> ID = new CustomPayload.Id<>(ReiatsuTest.id("uryu_arrows"));
	public static final PacketCodec<RegistryByteBuf, UryuArrowsS2C> CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeByte(v.kind);
				buf.writeInt(v.tick);
				int n = Math.min(v.ids.length, MAX_ARROWS);
				buf.writeVarInt(n);
				for (int i = 0; i < n; i++) {
					buf.writeVarInt(v.ids[i]);
				}
				int m = Math.min(v.data.length, MAX_ARROWS * PER_ARROW);
				buf.writeVarInt(m);
				for (int i = 0; i < m; i++) {
					buf.writeFloat(v.data[i]);
				}
			},
			buf -> {
				byte kind = buf.readByte();
				int tick = buf.readInt();
				int n = Math.min(buf.readVarInt(), MAX_ARROWS);
				int[] ids = new int[n];
				for (int i = 0; i < n; i++) {
					ids[i] = buf.readVarInt();
				}
				int m = Math.min(buf.readVarInt(), MAX_ARROWS * PER_ARROW);
				float[] data = new float[m];
				for (int i = 0; i < m; i++) {
					data[i] = buf.readFloat();
				}
				return new UryuArrowsS2C(kind, tick, ids, data);
			});

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
