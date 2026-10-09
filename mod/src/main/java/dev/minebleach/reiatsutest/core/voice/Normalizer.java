package dev.minebleach.reiatsutest.core.voice;

import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Phrase normaliser (design/VOICE_PHRASES.md section 5.1): any ASR output (Latin, Cyrillic, kana, a few known kanji)
 * becomes one lowercase Latin "key" form with folded consonants, so romaji, English transcription and Russian
 * transcription of the same sound meet. Pure Java, no Minecraft imports.
 *
 * <p>Pipeline: NFKC + lowercase, kanji lookup (unknown kanji are dropped), Cyrillic to Latin, kana to Hepburn, strip
 * diacritics (macrons), non-alphanumerics to spaces, digraph folds, letter folds, run collapse.
 */
public final class Normalizer {
	private Normalizer() {
	}

	// ------------------------------------------------------------------ kanji lookup (contract of section 5.1)

	private static final String[][] KANJI = {
		{"袖の白雪", "sode no shirayuki"}, {"袖白雪", "sode no shirayuki"}, {"舞え", "mae"}, {"舞へ", "mae"},
		{"初の舞", "some no mai"}, {"染めの舞", "some no mai"}, {"染の舞", "some no mai"}, {"月白", "tsukishiro"},
		{"次の舞", "tsugi no mai"}, {"白漣", "hakuren"}, {"白蓮", "hakuren"}, {"参の舞", "san no mai"},
		{"三の舞", "san no mai"}, {"白刀", "shirafune"}, {"白船", "shirafune"}, {"白舟", "shirafune"},
		{"卍解", "bankai"}, {"万解", "bankai"}, {"万海", "bankai"}, {"万回", "bankai"}, {"挽回", "bankai"},
		{"白霞罸", "hakka no togame"}, {"白霞の咎め", "hakka no togame"}, {"白霞の咎", "hakka no togame"},
		{"絶対零度", "zettai reido"}, {"散れ", "chire"}, {"知れ", "chire"}, {"千本桜景厳", "senbonzakura kageyoshi"},
		{"千本桜", "senbonzakura"}, {"景厳", "kageyoshi"}, {"景義", "kageyoshi"}, {"影吉", "kageyoshi"},
		{"攻撃", "kogeki"}, {"防御", "bogyo"}, {"結界", "kekkai"}, {"桜吹雪", "sakura fubuki"},
		{"花吹雪", "hana fubuki"}, {"終景", "shukei"}, {"白帝剣", "hakuteiken"}, {"殲景", "senkei"},
		{"封印", "fuin"}, {"納刀", "noto"}, {"戻れ", "modore"},
		// additions beyond the contract table (filler word "please" only)
		{"お願い", "onegai"},
	};

	private static final String[][] KANJI_SORTED;

	static {
		String[][] copy = KANJI.clone();
		Arrays.sort(copy, Comparator.comparingInt((String[] e) -> e[0].length()).reversed());
		KANJI_SORTED = copy;
	}

	// ------------------------------------------------------------------ Cyrillic

	private static final Map<Character, String> CYRILLIC = new HashMap<>();

	static {
		String[] pairs = {"а", "a", "б", "b", "в", "v", "г", "g", "д", "d", "е", "e", "ё", "yo", "ж", "z", "з", "z",
			"и", "i", "к", "k", "л", "l", "м", "m", "н", "n", "о", "o", "п", "p", "р", "r", "с", "s", "т", "t",
			"у", "u", "ф", "f", "х", "h", "ц", "c", "ч", "c", "ш", "s", "щ", "s", "ъ", "", "ь", "", "ы", "i",
			"э", "e", "ю", "yu", "я", "ya"};
		for (int i = 0; i < pairs.length; i += 2) {
			CYRILLIC.put(pairs[i].charAt(0), pairs[i + 1]);
		}
	}

	// ------------------------------------------------------------------ kana

	private static final Map<Character, String> KANA = new HashMap<>();

	static {
		String[] rows = {
			"あa", "いi", "うu", "えe", "おo", "かka", "きki", "くku", "けke", "こko", "さsa", "しshi", "すsu", "せse",
			"そso", "たta", "ちchi", "つtsu", "てte", "とto", "なna", "にni", "ぬnu", "ねne", "のno", "はha", "ひhi",
			"ふfu", "へhe", "ほho", "まma", "みmi", "むmu", "めme", "もmo", "やya", "ゆyu", "よyo", "らra", "りri",
			"るru", "れre", "ろro", "わwa", "ゐi", "ゑe", "をo", "んn", "がga", "ぎgi", "ぐgu", "げge", "ごgo", "ざza",
			"じji", "ずzu", "ぜze", "ぞzo", "だda", "ぢji", "づzu", "でde", "どdo", "ばba", "びbi", "ぶbu", "べbe",
			"ぼbo", "ぱpa", "ぴpi", "ぷpu", "ぺpe", "ぽpo", "ゔvu", "ぁa", "ぃi", "ぅu", "ぇe", "ぉo", "ゎwa"};
		for (String r : rows) {
			KANA.put(r.charAt(0), r.substring(1));
		}
	}

	private static final String I_ROW = "きしちにひみりぎじぢびぴ";

	// ------------------------------------------------------------------ pipeline

	/** Full pipeline; the result is lowercase Latin tokens separated by single spaces (possibly empty). */
	public static String normalize(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "";
		}
		String s = java.text.Normalizer.normalize(raw, Form.NFKC).toLowerCase(Locale.ROOT);
		for (String[] e : KANJI_SORTED) {
			if (s.contains(e[0])) {
				s = s.replace(e[0], " " + e[1] + " ");
			}
		}
		s = dropCjkIdeographs(s);
		s = cyrillicToLatin(s);
		s = katakanaToHiragana(s);
		s = kanaToRomaji(s);
		s = java.text.Normalizer.normalize(s, Form.NFD);
		s = COMBINING.matcher(s).replaceAll("");
		s = NON_ALNUM.matcher(s).replaceAll(" ");
		s = foldDigraphs(s);
		s = foldLetters(s);
		return WHITESPACE.matcher(s).replaceAll(" ").trim();
	}

	/** Tokens of a key (or of anything: normalises first when {@code alreadyKey} is false). */
	public static List<String> tokens(String key) {
		List<String> out = new ArrayList<>();
		if (key == null || key.isEmpty()) {
			return out;
		}
		for (String t : key.split(" ")) {
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}

	public static String compact(List<String> tokens) {
		return String.join("", tokens);
	}

	private static final Pattern COMBINING = Pattern.compile("\\p{M}+");
	private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9 ]");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final Pattern M_BEFORE_BP = Pattern.compile("m(?=[bp])");
	private static final Pattern Y_AFTER_SC = Pattern.compile("(?<=[sc])y(?=[aeiou])");
	private static final Pattern RUNS = Pattern.compile("([a-z0-9])\\1+");

	private static String dropCjkIdeographs(String s) {
		StringBuilder sb = null;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			boolean han = (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF);
			if (han) {
				if (sb == null) {
					sb = new StringBuilder(s.length()).append(s, 0, i);
				}
				sb.append(' ');
			} else if (sb != null) {
				sb.append(c);
			}
		}
		return sb == null ? s : sb.toString();
	}

	private static String cyrillicToLatin(String s) {
		StringBuilder sb = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == 'й') {
				char next = i + 1 < s.length() ? s.charAt(i + 1) : ' ';
				if (!(next == 'ю' || next == 'я' || next == 'ё' || next == 'е')) {
					sb.append('i');
				}
				continue;
			}
			String m = CYRILLIC.get(c);
			if (m != null) {
				sb.append(m);
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static String katakanaToHiragana(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= 0x30A1 && c <= 0x30F6) {
				sb.append((char) (c - 0x60));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static String kanaToRomaji(String s) {
		StringBuilder out = new StringBuilder(s.length() + 8);
		boolean geminate = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == 'っ') {
				geminate = true;
				continue;
			}
			if (c == 'ー') {
				if (!out.isEmpty() && "aiueo".indexOf(out.charAt(out.length() - 1)) >= 0) {
					out.append(out.charAt(out.length() - 1));
				}
				continue;
			}
			String r = KANA.get(c);
			if (r == null) {
				if (c == 'ゃ' || c == 'ゅ' || c == 'ょ') {
					r = c == 'ゃ' ? "ya" : c == 'ゅ' ? "yu" : "yo";
				} else {
					out.append(c);
					geminate = false;
					continue;
				}
			}
			// yoon: an i-row kana followed by a small ya / yu / yo
			if (I_ROW.indexOf(c) >= 0 && i + 1 < s.length()) {
				char n = s.charAt(i + 1);
				if (n == 'ゃ' || n == 'ゅ' || n == 'ょ') {
					String base = r.substring(0, r.length() - 1);
					String vowel = n == 'ゃ' ? "a" : n == 'ゅ' ? "u" : "o";
					r = (base.equals("sh") || base.equals("ch") || base.equals("j")) ? base + vowel : base + "y" + vowel;
					i++;
				}
			}
			if (geminate) {
				out.append(r.startsWith("ch") ? 't' : r.charAt(0));
				geminate = false;
			}
			out.append(r);
		}
		return out.toString();
	}

	private static String foldDigraphs(String s) {
		return s.replace("tch", "c").replace("ts", "c").replace("ch", "c").replace("sh", "s").replace("zh", "z")
				.replace("dz", "z").replace("kh", "h").replace("ph", "f").replace("j", "z").replace("q", "k");
	}

	private static String foldLetters(String s) {
		s = s.replace('l', 'r').replace('f', 'h').replace('z', 's');
		s = M_BEFORE_BP.matcher(s).replaceAll("n");
		s = Y_AFTER_SC.matcher(s).replaceAll("");
		s = s.replace("ou", "o").replace("oo", "o").replace("uu", "u");
		s = s.replace("ei", "e").replace("ee", "e").replace("ey", "e");
		return RUNS.matcher(s).replaceAll("$1");
	}
}
