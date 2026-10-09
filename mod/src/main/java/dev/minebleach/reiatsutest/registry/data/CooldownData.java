package dev.minebleach.reiatsutest.registry.data;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

/**
 * Attachment {@code reiatsu_test:cooldowns}: remaining ticks per ability code at the moment of sync. The client has no
 * access to server ticks, so it counts down from the tick at which it received this value (it only changes when a
 * cooldown starts or is cleared). Owner-only, not persistent.
 */
public record CooldownData(Map<Byte, Integer> remainingTicks) {
	public static final CooldownData EMPTY = new CooldownData(Map.of());

	public CooldownData {
		remainingTicks = Map.copyOf(remainingTicks);
	}

	public static final PacketCodec<RegistryByteBuf, CooldownData> PACKET_CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeVarInt(v.remainingTicks.size());
				v.remainingTicks.forEach((k, ticks) -> {
					buf.writeByte(k);
					buf.writeVarInt(ticks);
				});
			},
			buf -> {
				int n = Math.min(buf.readVarInt(), 32);
				Map<Byte, Integer> m = new HashMap<>();
				for (int i = 0; i < n; i++) {
					m.put(buf.readByte(), buf.readVarInt());
				}
				return new CooldownData(m);
			});

	public static CooldownData of(Map<AbilityId, Integer> remaining) {
		Map<Byte, Integer> m = new HashMap<>();
		remaining.forEach((a, t) -> m.put(a.code, t));
		return new CooldownData(m);
	}

	public int remaining(AbilityId id) {
		return remainingTicks.getOrDefault(id.code, 0);
	}
}
