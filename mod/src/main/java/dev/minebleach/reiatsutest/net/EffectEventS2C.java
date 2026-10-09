package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * S2C to the caster and to players tracking the caster: one effect to play (ADR section 3). The client simulates the visuals
 * from (seed, params); {@code startTick} is the server tick of the cast (clients use their own receive time).
 */
public record EffectEventS2C(int effectId, int casterId, int seed, double x, double y, double z,
		float dx, float dy, float dz, int targetId, int startTick, float[] params) implements CustomPayload {
	public static final int MAX_PARAMS = 8;
	public static final CustomPayload.Id<EffectEventS2C> ID = new CustomPayload.Id<>(ReiatsuTest.id("effect_event"));
	public static final PacketCodec<RegistryByteBuf, EffectEventS2C> CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeVarInt(v.effectId);
				buf.writeVarInt(v.casterId);
				buf.writeInt(v.seed);
				buf.writeDouble(v.x);
				buf.writeDouble(v.y);
				buf.writeDouble(v.z);
				buf.writeFloat(v.dx);
				buf.writeFloat(v.dy);
				buf.writeFloat(v.dz);
				buf.writeVarInt(v.targetId);
				buf.writeInt(v.startTick);
				int n = Math.min(v.params.length, MAX_PARAMS);
				buf.writeByte(n);
				for (int i = 0; i < n; i++) {
					buf.writeFloat(v.params[i]);
				}
			},
			buf -> {
				int effectId = buf.readVarInt();
				int casterId = buf.readVarInt();
				int seed = buf.readInt();
				double x = buf.readDouble();
				double y = buf.readDouble();
				double z = buf.readDouble();
				float dx = buf.readFloat();
				float dy = buf.readFloat();
				float dz = buf.readFloat();
				int targetId = buf.readVarInt();
				int startTick = buf.readInt();
				int n = Math.min(buf.readUnsignedByte(), MAX_PARAMS);
				float[] p = new float[n];
				for (int i = 0; i < n; i++) {
					p[i] = buf.readFloat();
				}
				return new EffectEventS2C(effectId, casterId, seed, x, y, z, dx, dy, dz, targetId, startTick, p);
			});

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
