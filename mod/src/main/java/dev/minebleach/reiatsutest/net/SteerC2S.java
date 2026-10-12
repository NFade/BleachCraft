package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * C2S: the Byakuya attack key is held (or let go) while the blade swarm is out. Sent about 10 times per second while held, and once
 * with {@code holding = false} on release. {@code (dx, dy, dz)} is the unit look direction. The server never trusts it: it is
 * rate limited, checked against the server's own look vector and only steers the flight within its speed and range limits
 * ({@code core/swarm/SteerGate}, {@code BladeFlight}).
 */
public record SteerC2S(boolean holding, float dx, float dy, float dz) implements CustomPayload {
	public static final CustomPayload.Id<SteerC2S> ID = new CustomPayload.Id<>(ReiatsuTest.id("blade_steer"));
	public static final PacketCodec<RegistryByteBuf, SteerC2S> CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeBoolean(v.holding);
				buf.writeFloat(v.dx);
				buf.writeFloat(v.dy);
				buf.writeFloat(v.dz);
			},
			buf -> new SteerC2S(buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
