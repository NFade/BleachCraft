package dev.minebleach.reiatsutest.core.book;

import dev.minebleach.reiatsutest.core.kido.KidoConfig;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.kido.KidoSpec;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import java.util.Locale;

/**
 * Turns a {@link BookArg} into display text from the balance configs. The client only knows the built-in defaults (the
 * server configs are not synced), so a server with edited numbers shows the default ones in its books.
 */
public final class BookFacts {
	private BookFacts() {
	}

	/** Display text of a numeric argument; null for a KEY argument or an unknown command id. */
	public static String resolve(BookArg arg, BalanceConfig balance, KidoConfig kido) {
		switch (arg.kind()) {
			case KEY:
				return null;
			case RELEASE_COST:
				return tenths(balance.shikaiReleaseCost());
			case BANKAI_SECONDS:
				return ticksToSeconds(balance.bankaiCapTicks());
			default:
				break;
		}
		AbilityId a = AbilityId.fromCommandId(arg.ref());
		KidoId k = KidoId.fromCommandId(arg.ref());
		AbilitySpec as = a == null ? null : balance.spec(a);
		KidoSpec ks = k == null ? null : kido.spec(k);
		switch (arg.kind()) {
			case COST:
				return as != null ? tenths(as.costTenths()) : ks != null ? tenths(ks.costTenths()) : null;
			case COOLDOWN:
				return as != null ? ticksToSeconds(as.cooldownTicks()) : ks != null ? ticksToSeconds(ks.cooldownTicks()) : null;
			case KIDO_RANGE:
				return ks != null ? number(ks.range()) : null;
			case KIDO_DAMAGE:
				return ks != null ? number(ks.damage()) : null;
			default:
				return null;
		}
	}

	/** Tenths of a reiatsu point as text: 250 gives "25", 15 gives "1.5". */
	public static String tenths(int tenths) {
		return number(tenths / 10.0);
	}

	/** Ticks as seconds: 240 gives "12", 150 gives "7.5". */
	public static String ticksToSeconds(int ticks) {
		return number(ticks / 20.0);
	}

	/** Integers without a decimal part, others with one decimal (two when the value has a real hundredth, 1.25), always a dot. */
	public static String number(double v) {
		if (Math.abs(v - Math.rint(v)) < 0.05) {
			return Long.toString(Math.round(v));
		}
		if (Math.abs(v * 10.0 - Math.rint(v * 10.0)) >= 0.04) {
			return String.format(Locale.ROOT, "%.2f", v);
		}
		return String.format(Locale.ROOT, "%.1f", v);
	}
}
