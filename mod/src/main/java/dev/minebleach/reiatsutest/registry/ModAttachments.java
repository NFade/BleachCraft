package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.registry.data.CooldownData;
import dev.minebleach.reiatsutest.registry.data.MomentumData;
import dev.minebleach.reiatsutest.registry.data.RageData;
import dev.minebleach.reiatsutest.registry.data.RageTuningData;
import dev.minebleach.reiatsutest.registry.data.ReiatsuData;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** Player attachments (ADR section 3). Written by the server only; synced by the Fabric Data Attachment API. */
public final class ModAttachments {
	/** State, shared with everyone (auras, swarm, costume). Resets to SEALED on join, death and respawn. */
	public static final AttachmentType<ZanpakutoData> ZANPAKUTO = AttachmentRegistry.create(
			ReiatsuTest.id("zanpakuto"),
			b -> b.initializer(() -> ZanpakutoData.SEALED)
					.syncWith(ZanpakutoData.PACKET_CODEC, AttachmentSyncPredicate.all()));

	/** Reiatsu bar, persistent, owner only. */
	public static final AttachmentType<ReiatsuData> REIATSU = AttachmentRegistry.create(
			ReiatsuTest.id("reiatsu"),
			b -> b.initializer(() -> ReiatsuData.FULL)
					.persistent(ReiatsuData.CODEC)
					.copyOnDeath()
					.syncWith(ReiatsuData.PACKET_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Cooldowns, owner only, not persistent. */
	public static final AttachmentType<CooldownData> COOLDOWNS = AttachmentRegistry.create(
			ReiatsuTest.id("cooldowns"),
			b -> b.initializer(() -> CooldownData.EMPTY)
					.syncWith(CooldownData.PACKET_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Rage bar (B4), owner only, not persistent: starts at zero on join and after death. */
	public static final AttachmentType<RageData> RAGE = AttachmentRegistry.create(
			ReiatsuTest.id("rage"),
			b -> b.initializer(() -> RageData.EMPTY)
					.syncWith(RageData.PACKET_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Rage costs and the minimum shikai time of the server config for the HUD (R11 review fix 4), owner only, not persistent. */
	public static final AttachmentType<RageTuningData> RAGE_TUNING = AttachmentRegistry.create(
			ReiatsuTest.id("rage_tuning"),
			b -> b.initializer(() -> RageTuningData.DEFAULTS)
					.syncWith(RageTuningData.PACKET_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Frost stacks of Rukia (R11 T6), owner only, not persistent. */
	public static final AttachmentType<MomentumData> MOMENTUM = AttachmentRegistry.create(
			ReiatsuTest.id("momentum"),
			b -> b.initializer(() -> MomentumData.EMPTY)
					.syncWith(MomentumData.PACKET_CODEC, AttachmentSyncPredicate.targetOnly()));

	private ModAttachments() {
	}

	public static void init() {
		// class load registers the attachment types
	}
}
