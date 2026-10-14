package dev.minebleach.reiatsutest.registry.data;

import dev.minebleach.reiatsutest.core.rage.RageTuning;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

/**
 * Attachment {@code reiatsu_test:rage_tuning} (R11 review fix 4): the shikai and bankai Rage costs and the minimum shikai time of the server config, for the
 * HUD of the owner. Not persistent, owner-only sync; the server writes it only when it differs, so a quiet player costs no packets.
 */
public record RageTuningData(int shikaiCostTenths, int bankaiCostTenths, int bankaiMinShikaiTicks) {
	public static final RageTuningData DEFAULTS = of(RageTuning.defaults());

	public static final PacketCodec<RegistryByteBuf, RageTuningData> PACKET_CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeInt(v.shikaiCostTenths);
				buf.writeInt(v.bankaiCostTenths);
				buf.writeInt(v.bankaiMinShikaiTicks);
			},
			buf -> new RageTuningData(buf.readInt(), buf.readInt(), buf.readInt()));

	public static RageTuningData of(RageTuning t) {
		return new RageTuningData(t.shikaiCostTenths(), t.bankaiCostTenths(), t.bankaiMinShikaiTicks());
	}

	public RageTuning toTuning() {
		return new RageTuning(shikaiCostTenths, bankaiCostTenths, bankaiMinShikaiTicks);
	}
}
