package dev.minebleach.reiatsutest.core.voice;

import dev.minebleach.reiatsutest.core.voice.PhraseBook.Command;
import dev.minebleach.reiatsutest.core.voice.PhraseBook.Variant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fuzzy phrase matcher (design/VOICE_PHRASES.md section 5.2). The utterance is normalised, commands that do not fit the
 * {@link GateContext} are dropped before scoring, and every remaining variant is compared with windows of consecutive
 * utterance tokens (compact strings, so split and fused words meet). A window passes only if all of these hold:
 *
 * <ul>
 *   <li>compact variant length L at most 6: exact match;</li>
 *   <li>L 7..9: Jaro-Winkler at least 0.90 and Levenshtein at most 1; L 10..13: 0.88 and 2; L 14+: 0.88 and 3;</li>
 *   <li>Jaro-Winkler also at least the configured {@code threshold};</li>
 *   <li>same token count as a multi-token variant: every aligned token pair within its own Levenshtein limit
 *       (length 1..4 exact, 5..7 one edit, 8+ two edits).</li>
 * </ul>
 *
 * Weak variants ("seal", lone "chire") only match when the whole utterance, minus filler words, is the variant. The
 * best command must lead the second best one by at least {@code margin}, otherwise nothing fires.
 */
public final class PhraseMatcher {
	/** Confidence floor (ADR: a final result fires at >= 0.86) and the lead needed over the second best command. */
	public record Config(double threshold, double margin) {
		public static Config defaults() {
			return new Config(0.86, 0.03);
		}
	}

	public enum Status {
		MATCH, NO_MATCH, AMBIGUOUS,
		/** A weak variant (single common word) was found inside a longer sentence: deliberately ignored. */
		WEAK_NOT_WHOLE
	}

	/** Result of a match. {@code command} and {@code variant} are set only for MATCH. */
	public record Result(Status status, Command command, Variant variant, double confidence, String normalized) {
		public boolean matched() {
			return status == Status.MATCH;
		}

		public String commandId() {
			return command == null ? null : command.id();
		}
	}

	private final PhraseBook book;
	private final Config cfg;

	public PhraseMatcher(PhraseBook book, Config cfg) {
		this.book = book;
		this.cfg = cfg;
	}

	public PhraseBook book() {
		return book;
	}

	public Config config() {
		return cfg;
	}

	public Result match(String rawText, GateContext ctx) {
		return matchKey(Normalizer.normalize(rawText), ctx);
	}

	/** Matches an already normalised key (used by the processor after it has stripped consumed text). */
	public Result matchKey(String key, GateContext ctx) {
		List<String> tokens = Normalizer.tokens(key);
		if (tokens.isEmpty()) {
			return new Result(Status.NO_MATCH, null, null, 0.0, key);
		}
		List<String> core = new ArrayList<>();
		for (String t : tokens) {
			if (!book.fillerKeys().contains(t)) {
				core.add(t);
			}
		}
		boolean weakNotWhole = false;
		Map<Command, Double> best = new HashMap<>();
		Map<Command, Variant> bestVariant = new HashMap<>();
		for (Command c : book.commands()) {
			if (!c.validIn(ctx)) {
				continue;
			}
			for (Variant v : c.variants()) {
				if (v.requiresFullReiatsu() && !(ctx.gated() && ctx.reiatsuFull())) {
					continue;
				}
				double s;
				if (v.weak()) {
					s = core.isEmpty() ? -1 : scoreSpan(core, 0, core.size(), v);
					if (s < 0 && anyWindow(tokens, v) >= 0) {
						weakNotWhole = true;
					}
				} else {
					s = anyWindow(tokens, v);
				}
				if (s >= 0 && s > best.getOrDefault(c, -1.0)) {
					best.put(c, s);
					bestVariant.put(c, v);
				}
			}
		}
		if (best.isEmpty()) {
			return new Result(weakNotWhole ? Status.WEAK_NOT_WHOLE : Status.NO_MATCH, null, null, 0.0, key);
		}
		Command top = null;
		double topScore = -1;
		double second = -1;
		for (Map.Entry<Command, Double> e : best.entrySet()) {
			if (e.getValue() > topScore) {
				second = topScore;
				topScore = e.getValue();
				top = e.getKey();
			} else if (e.getValue() > second) {
				second = e.getValue();
			}
		}
		if (second >= 0 && topScore - second < cfg.margin()) {
			return new Result(Status.AMBIGUOUS, null, null, topScore, key);
		}
		return new Result(Status.MATCH, top, bestVariant.get(top), topScore, key);
	}

	// ------------------------------------------------------------------ scoring

	/** Best score of a variant against any window of the utterance, or -1. */
	private double anyWindow(List<String> tokens, Variant v) {
		int len = v.compact().length();
		int slack = maxLevenshtein(len);
		double best = -1;
		for (int i = 0; i < tokens.size(); i++) {
			int total = 0;
			for (int j = i; j < tokens.size(); j++) {
				total += tokens.get(j).length();
				if (total > len + slack) {
					break;
				}
				if (total >= len - slack) {
					best = Math.max(best, scoreSpan(tokens, i, j + 1, v));
				}
			}
		}
		// fused words (kana and kanji output has no spaces): slide a character window over long tokens
		if (len >= 9 && v.tokens().stream().allMatch(t -> t.length() >= 2)) {
			for (String t : tokens) {
				if (t.length() < len - 1) {
					continue;
				}
				for (int w = len - 1; w <= len + 1; w++) {
					for (int s = 0; s + w <= t.length(); s++) {
						best = Math.max(best, scoreText(t.substring(s, s + w), v, null));
					}
				}
			}
		}
		return best;
	}

	private double scoreSpan(List<String> tokens, int from, int to, Variant v) {
		List<String> span = tokens.subList(from, to);
		String compact = Normalizer.compact(span);
		// Window sizes. An exact compact match is accepted at any size (kana output has no spaces).
		//  - k = n - 1 (a dropped word) only for variants of 3+ tokens that contain the particle "no", the word ASR drops;
		//  - k = n + 1 (a word split in two) only with one slip fewer than usual, and no stray one-letter fragments ("i запечатай");
		//  - short keys (6 letters or less) are exact-only words and are never assembled from two ordinary words
		//    ("banka i" is not "bankai"); a spoken split such as "bank eye" must be an explicit alias.
		int n = v.tokens().size();
		int k = span.size();
		if (!compact.equals(v.compact())) {
			if (k > n + 1 || k < n - 1 || (k == n - 1 && (n < 3 || !v.tokens().contains("no")))) {
				return -1;
			}
			if (k > n) {
				boolean variantHasLetter = v.tokens().stream().anyMatch(t -> t.length() < 2);
				for (String t : span) {
					if (t.length() < 2 && !variantHasLetter) {
						return -1;
					}
				}
				if (JaroWinkler.levenshtein(compact, v.compact()) > Math.max(1, maxLevenshtein(v.compact().length()) - 1)) {
					return -1;
				}
			}
		} else if (k > n && v.compact().length() <= 6) {
			return -1;
		}
		return scoreText(compact, v, span);
	}

	/** Score of a compact string against a variant, or -1 when any rule fails. {@code spanTokens} may be null. */
	private double scoreText(String text, Variant v, List<String> spanTokens) {
		String target = v.compact();
		int len = target.length();
		if (text.equals(target)) {
			return 1.0;
		}
		if (len <= 6) {
			return -1;
		}
		if (Math.abs(text.length() - len) > maxLevenshtein(len)) {
			return -1;
		}
		int lev = JaroWinkler.levenshtein(text, target);
		if (lev > maxLevenshtein(len)) {
			return -1;
		}
		double jw = JaroWinkler.similarity(text, target);
		if (jw < Math.max(cfg.threshold(), minSimilarity(len))) {
			return -1;
		}
		if (spanTokens != null && spanTokens.size() > 1 && spanTokens.size() == v.tokens().size()) {
			for (int i = 0; i < spanTokens.size(); i++) {
				String vt = v.tokens().get(i);
				if (JaroWinkler.levenshtein(spanTokens.get(i), vt) > tokenLimit(vt.length())) {
					return -1;
				}
			}
		}
		return jw;
	}

	static int maxLevenshtein(int len) {
		if (len <= 6) {
			return 0;
		}
		if (len <= 9) {
			return 1;
		}
		if (len <= 13) {
			return 2;
		}
		return 3;
	}

	static double minSimilarity(int len) {
		return len <= 9 ? 0.90 : 0.88;
	}

	static int tokenLimit(int tokenLen) {
		if (tokenLen <= 4) {
			return 0;
		}
		return tokenLen <= 7 ? 1 : 2;
	}
}
