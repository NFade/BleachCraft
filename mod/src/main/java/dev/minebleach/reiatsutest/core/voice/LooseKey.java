package dev.minebleach.reiatsutest.core.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A second, coarser sound key built on top of the strict {@link Normalizer} key (G3, live-speech tolerance). The strict
 * key keeps voiced and unvoiced consonants apart, which is right for exact short words ("bankai") but too brittle for
 * long Japanese names heard by an English or Russian recogniser, which turns "senbonzakura" into "send bonds a cura"
 * and swaps g/k, d/t and b/p on the way.
 *
 * <p>The loose key folds only consonants (vowels stay, so the key keeps its information): voiced and unvoiced stops
 * meet (g/k, d/t, b/p, v/p), hard c and k meet, w becomes u, a stop after n before a consonant disappears ("send bon"
 * and "sen bon"), and repeated letters collapse. Pure Java, no Minecraft imports. It is only used by
 * {@link PhraseMatcher} for long, non-weak variants and under tighter rules than the strict path.
 */
final class LooseKey {
	private LooseKey() {
	}

	private static final Pattern RUNS = Pattern.compile("([a-z0-9])\\1+");
	private static final Pattern NASAL_STOP = Pattern.compile("nt(?![aeiouy])");

	/** Loose form of one strict token; never empty for a non-empty input. */
	static String token(String strict) {
		String s = strict.replace("ck", "k").replace('c', 'k').replace("x", "ks").replace('w', 'u');
		s = s.replace('g', 'k').replace('d', 't').replace('b', 'p').replace('v', 'p');
		s = RUNS.matcher(s).replaceAll("$1");
		s = NASAL_STOP.matcher(s).replaceAll("n");
		return s;
	}

	/** Loose form of every token of a strict key's token list (tokens are kept one to one). */
	static List<String> tokens(List<String> strictTokens) {
		List<String> out = new ArrayList<>(strictTokens.size());
		for (String t : strictTokens) {
			out.add(token(t));
		}
		return out;
	}
}
