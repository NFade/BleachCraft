package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** S2C to players around an affected area: entities that are frozen, encased or slowed until a server tick. */
public record EntityFxS2C(byte kind, int untilTick, int[] entityIds) implements CustomPayload {
	public static final byte FROZEN = 0;
	public static final byte ENCASED = 1;
	public static final byte SLOWED = 2;
	/** S5: entities actually damaged by a damage phase (hit sparks, slot feedback); {@code untilTick} is the current tick. */
	public static final byte HIT = 3;
	public static final int MAX_ENTITIES = 64;

	public static final CustomPayload.Id<EntityFxS2C> ID = new CustomPayload.Id<>(ReiatsuTest.id("entity_fx"));
	public static final PacketCodec<RegistryByteBuf, EntityFxS2C> CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeByte(v.kind);
				buf.writeInt(v.untilTick);
				int n = Math.min(v.entityIds.length, MAX_ENTITIES);
				buf.writeVarInt(n);
				for (int i = 0; i < n; i++) {
					buf.writeVarInt(v.entityIds[i]);
				}
			},
			buf -> {
				byte kind = buf.readByte();
				int until = buf.readInt();
				int n = Math.min(buf.readVarInt(), MAX_ENTITIES);
				int[] ids = new int[n];
				for (int i = 0; i < n; i++) {
					ids[i] = buf.readVarInt();
				}
				return new EntityFxS2C(kind, until, ids);
			});

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
