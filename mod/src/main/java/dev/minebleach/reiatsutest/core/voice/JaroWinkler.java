package dev.minebleach.reiatsutest.core.voice;

/** String similarity helpers: Jaro-Winkler (prefix scale 0.1, prefix length 4) and Levenshtein distance. */
public final class JaroWinkler {
	private JaroWinkler() {
	}

	public static double similarity(String a, String b) {
		if (a.equals(b)) {
			return 1.0;
		}
		int la = a.length();
		int lb = b.length();
		if (la == 0 || lb == 0) {
			return 0.0;
		}
		int window = Math.max(0, Math.max(la, lb) / 2 - 1);
		boolean[] ma = new boolean[la];
		boolean[] mb = new boolean[lb];
		int matches = 0;
		for (int i = 0; i < la; i++) {
			int from = Math.max(0, i - window);
			int to = Math.min(lb - 1, i + window);
			for (int j = from; j <= to; j++) {
				if (!mb[j] && a.charAt(i) == b.charAt(j)) {
					ma[i] = true;
					mb[j] = true;
					matches++;
					break;
				}
			}
		}
		if (matches == 0) {
			return 0.0;
		}
		int transpositions = 0;
		int k = 0;
		for (int i = 0; i < la; i++) {
			if (ma[i]) {
				while (!mb[k]) {
					k++;
				}
				if (a.charAt(i) != b.charAt(k)) {
					transpositions++;
				}
				k++;
			}
		}
		double m = matches;
		double jaro = (m / la + m / lb + (m - transpositions / 2.0) / m) / 3.0;
		int prefix = 0;
		int max = Math.min(4, Math.min(la, lb));
		while (prefix < max && a.charAt(prefix) == b.charAt(prefix)) {
			prefix++;
		}
		return jaro + prefix * 0.1 * (1.0 - jaro);
	}

	public static int levenshtein(String a, String b) {
		int la = a.length();
		int lb = b.length();
		int[] prev = new int[lb + 1];
		int[] cur = new int[lb + 1];
		for (int j = 0; j <= lb; j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= la; i++) {
			cur[0] = i;
			for (int j = 1; j <= lb; j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
			}
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		return prev[lb];
	}
}
