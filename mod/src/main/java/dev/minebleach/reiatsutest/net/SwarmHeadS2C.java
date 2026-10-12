package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * S2C to the caster and the players tracking the caster: where the server's attack swarm head is (every 2 ticks while it flies,
 * plus every phase change). The client draws the petal ribbon along this path ({@code SwarmFx.onHead}). {@code phase} is
 * {@code BladeFlight.Phase.code} (0 windup = the first packet of a flight, 4 done = the head is back); {@code serverTick} orders
 * the samples so network jitter does not make the swarm stutter.
 */
public record SwarmHeadS2C(int casterId, byte phase, int serverTick, float x, float y, float z) implements CustomPayload {
	public static final CustomPayload.Id<SwarmHeadS2C> ID = new CustomPayload.Id<>(ReiatsuTest.id("swarm_head"));
	public static final PacketCodec<RegistryByteBuf, SwarmHeadS2C> CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeVarInt(v.casterId);
				buf.writeByte(v.phase);
				buf.writeInt(v.serverTick);
				buf.writeFloat(v.x);
				buf.writeFloat(v.y);
				buf.writeFloat(v.z);
			},
			buf -> new SwarmHeadS2C(buf.readVarInt(), buf.readByte(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
