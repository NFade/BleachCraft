package dev.minebleach.reiatsutest.registry.data;

import dev.minebleach.reiatsutest.core.kido.KidoId;
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

	/** Abilities and kido spells together; the codes do not collide (abilities 1 to 9, kido 101 and up). */
	public static CooldownData of(Map<AbilityId, Integer> remaining, Map<KidoId, Integer> kido) {
		Map<Byte, Integer> m = new HashMap<>();
		remaining.forEach((a, t) -> m.put(a.code, t));
		kido.forEach((k, t) -> m.put(k.code, t));
		return new CooldownData(m);
	}

	/** Code of the shunpo cooldown in the sync map (abilities 1 to 23, kido 101 to 103, so 110 collides with none). */
	public static final byte SHUNPO_CODE = 110;

	/** Abilities, kido spells and the shunpo cooldown (side HUD cells); {@code shunpoTicks <= 0} adds no entry. */
	public static CooldownData of(Map<AbilityId, Integer> remaining, Map<KidoId, Integer> kido, int shunpoTicks) {
		Map<Byte, Integer> m = new HashMap<>(of(remaining, kido).remainingTicks());
		if (shunpoTicks > 0) {
			m.put(SHUNPO_CODE, shunpoTicks);
		}
		return new CooldownData(m);
	}

	public int shunpoRemaining() {
		return remainingTicks.getOrDefault(SHUNPO_CODE, 0);
	}

	public int remaining(KidoId id) {
		return remainingTicks.getOrDefault(id.code, 0);
	}

	public int remaining(AbilityId id) {
		return remainingTicks.getOrDefault(id.code, 0);
	}
}
