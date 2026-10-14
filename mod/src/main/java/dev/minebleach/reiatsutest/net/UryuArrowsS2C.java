package dev.minebleach.reiatsutest.net;

import dev.minebleach.reiatsutest.ReiatsuTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * S2C of Uryu's placeholder visuals. {@link #ARROWS}: the full list of Heilig Pfeil near the receiver this tick, {@code ids[i]} and
 * {@code data[7 i .. 7 i + 6]} = x y z vx vy vz flags (flags = focus + 4 * kind ordinal + 32 * steering); an empty list clears them. {@link #BURST}: data = {x, y, z, radius, early (0/1)},
 * a Sprenger explosion. {@link #LINE}: data = {x1, y1, z1, x2, y2, z2, half width}, the Sternenstaub. {@link #TRAP}: data = {x, y, z, radius, ticks to the explosion}, a new Sprenger pentagon.
 */
public record UryuArrowsS2C(byte kind, int tick, int[] ids, float[] data) implements CustomPayload {
	public static final byte ARROWS = 0;
	public static final byte BURST = 1;
	public static final byte LINE = 2;
	public static final byte TRAP = 3;
	/** data = {x, y, z, type (IMPACT_ENTITY / IMPACT_BLOCK), arrow kind ordinal}: a hit spark. */
	public static final byte IMPACT = 4;
	/**
	 * ids = {stacks of Focus, casterId}, sent when the count changes to the owner (HUD pips) AND to the players who track him (S-U2: the
	 * Focus feedback on the body of Uryu). A packet with one id only (older server) is the receiver's own count; {@link #focus}.
	 */
	public static final byte FOCUS = 5;
	/** data = {x, y, z (eye), dx, dy, dz (look), index 0..2, reach, cone degrees, hits}: one slash of the Seele Schneider combo. */
	public static final byte SLASH = 6;
	/**
	 * S-U3: a Sprenger pentagon rooted these entities. ids = the entity ids, data = {centre x, y, z of the pentagon, ticks until the root runs
	 * out counted from {@link #tick}} (the absolute until tick is {@code tick + data[3]}; a float cannot hold a long running server's tick).
	 */
	public static final byte BIND = 7;
	/** S-U3: the root of these entities ended. ids = the entity ids, data = {centre x, y, z, reason code of {@code BindEnd}}. */
	public static final byte END = 8;
	/** The arrow kind value of an impact that comes from a slash and not from an arrow. */
	public static final int KIND_SLASH = 99;
	public static final int IMPACT_ENTITY = 0;
	public static final int IMPACT_BLOCK = 1;
	public static final int MAX_ARROWS = 96;
	public static final int PER_ARROW = 7;

	public static final CustomPayload.Id<UryuArrowsS2C> ID = new CustomPayload.Id<>(ReiatsuTest.id("uryu_arrows"));
	public static final PacketCodec<RegistryByteBuf, UryuArrowsS2C> CODEC = PacketCodec.of(
			(v, buf) -> {
				buf.writeByte(v.kind);
				buf.writeInt(v.tick);
				int n = Math.min(v.ids.length, MAX_ARROWS);
				buf.writeVarInt(n);
				for (int i = 0; i < n; i++) {
					buf.writeVarInt(v.ids[i]);
				}
				int m = Math.min(v.data.length, MAX_ARROWS * PER_ARROW);
				buf.writeVarInt(m);
				for (int i = 0; i < m; i++) {
					buf.writeFloat(v.data[i]);
				}
			},
			buf -> {
				byte kind = buf.readByte();
				int tick = buf.readInt();
				int n = Math.min(buf.readVarInt(), MAX_ARROWS);
				int[] ids = new int[n];
				for (int i = 0; i < n; i++) {
					ids[i] = buf.readVarInt();
				}
				int m = Math.min(buf.readVarInt(), MAX_ARROWS * PER_ARROW);
				float[] data = new float[m];
				for (int i = 0; i < m; i++) {
					data[i] = buf.readFloat();
				}
				return new UryuArrowsS2C(kind, tick, ids, data);
			});

	/** S-U2: the Focus of {@code casterId} is {@code stacks} now (0 to 3). */
	public static UryuArrowsS2C focus(int casterId, int stacks, int tick) {
		return new UryuArrowsS2C(FOCUS, tick, new int[] {stacks, casterId}, new float[0]);
	}

	/** The stacks of a FOCUS packet (0 when it has none). */
	public int focusStacks() {
		return ids.length > 0 ? Math.max(0, Math.min(3, ids[0])) : 0;
	}

	/** The caster of a FOCUS packet, or {@code fallback} for a packet without one (the receiver's own count). */
	public int focusCaster(int fallback) {
		return ids.length > 1 ? ids[1] : fallback;
	}

	/** S-U3: the root of {@code entityIds} starts, {@code ticksLeft} ticks from {@code tick}. */
	public static UryuArrowsS2C bind(int tick, int[] entityIds, double cx, double cy, double cz, int ticksLeft) {
		return new UryuArrowsS2C(BIND, tick, entityIds, new float[] {(float) cx, (float) cy, (float) cz, ticksLeft});
	}

	/** S-U3: the root of {@code entityIds} ended for {@code reason}. */
	public static UryuArrowsS2C end(int tick, int[] entityIds, double cx, double cy, double cz, dev.minebleach.reiatsutest.core.uryu.BindEnd reason) {
		return new UryuArrowsS2C(END, tick, entityIds, new float[] {(float) cx, (float) cy, (float) cz, reason.code});
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
