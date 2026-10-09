package dev.minebleach.reiatsutest.registry;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.registry.data.CooldownData;
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

	private ModAttachments() {
	}

	public static void init() {
		// class load registers the attachment types
	}
}
