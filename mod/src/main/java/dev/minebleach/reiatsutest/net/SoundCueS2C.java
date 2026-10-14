package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * R11 T12: S2C "play the sound event {@code event} at (x, y, z)". The client looks the event up in its own {@code SoundMap}: with a clip loaded it plays the
 * clip, otherwise the vanilla {@code fallback} with {@code volume} and {@code pitch} (what the server used to play with {@code world.playSound}). An empty
 * fallback means "quiet without a clip".
 */
public record SoundCueS2C(String event, double x, double y, double z, String fallback, float volume, float pitch) implements CustomPayload {
	public static final int MAX_ID = 128;
	public static final CustomPayload.Id<SoundCueS2C> ID = new CustomPayload.Id<>(ReiatsuTest.id("sound_cue"));
	public static final PacketCodec<RegistryByteBuf, SoundCueS2C> CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeString(v.event, MAX_ID);
				buf.writeDouble(v.x);
				buf.writeDouble(v.y);
				buf.writeDouble(v.z);
				buf.writeString(v.fallback, MAX_ID);
				buf.writeFloat(v.volume);
				buf.writeFloat(v.pitch);
			},
			buf -> new SoundCueS2C(buf.readString(MAX_ID), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readString(MAX_ID), buf.readFloat(),
					buf.readFloat()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
