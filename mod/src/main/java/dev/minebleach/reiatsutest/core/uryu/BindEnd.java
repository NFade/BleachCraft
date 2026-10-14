package dev.minebleach.reiatsutest.core.uryu;

/**
 * Why a Sprenger root ended (the {@code reason} of the {@code UryuArrowsS2C.END} packet, S-U3): the time ran out, the rooted one took
 * damage (or died of it: the chains snap), the pentagon exploded (its own explosion is not a break), or the pentagon was cancelled
 * (caster dead, logged out, changed state or dimension, server stop, pentagon replaced). The client draws a different ending for each
 * (URYU_VFX_STORYBOARD 2.8). The {@link #code} is what goes over the wire.
 */
public enum BindEnd {
	EXPIRE(0),
	DAMAGE(1),
	EXPLOSION(2),
	CANCEL(3);

	public final int code;

	BindEnd(int code) {
		this.code = code;
	}

	/** The reason of a wire code; {@link #CANCEL} for anything unknown. */
	public static BindEnd fromCode(int code) {
		for (BindEnd e : values()) {
			if (e.code == code) {
				return e;
			}
		}
		return CANCEL;
	}
}
