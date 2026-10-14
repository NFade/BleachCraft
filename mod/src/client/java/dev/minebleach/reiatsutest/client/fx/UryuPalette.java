package dev.minebleach.reiatsutest.client.fx;

/**
 * The Quincy palette of URYU_VFX_STORYBOARD 1.1 as hex strings (for {@code .color(String)} of the glow sprites, particles and shapes) and
 * as float triples. White core, cyan on dark: the world is darkened for Quincy moments (GRADE), so the neon reads.
 */
public final class UryuPalette {
	/** Q0 white core: cores of lines, arrow cores, flash, impact centres. */
	public static final String Q0 = "#FFFFFF";
	/** Q1 rim: outline of bow, wings, glyphs. */
	public static final String Q1 = "#E4FAFF";
	/** Q2 glow light: motes, dashes, flakes, pentagon edge glow, Hirenkyaku disc rim. */
	public static final String Q2 = "#9DEBFF";
	/** Q3 Reishi cyan: wing plates, bow body, string, arrow shaft, veins. */
	public static final String Q3 = "#4FC8FF";
	/** Q4 body blue: wing plate shading, Sprenger ground edge, Licht Regen sky glow. */
	public static final String Q4 = "#1F8FE0";
	/** Q5 deep: wing shadow side, vignette tint of Quincy moments. */
	public static final String Q5 = "#0E4FA8";
	/** Q6 night: grade of the sky at Vollstaendig and Sternenstaub (as multiply "#6F88C8"). */
	public static final String Q6 = "#0A1E52";
	/** Q7 spoke / pentagon edge, fence curtain. */
	public static final String Q7 = "#6FE0FF";
	/** Q8 the floor inside the Sprenger pentagon (additive disc at 0.25). */
	public static final String Q8 = "#1E5FB0";
	/** Q9 white-blue dust puffs. */
	public static final String Q9 = "#DCEBFA";
	/** QL lilac, at most 8 percent of the stardust specks. */
	public static final String QL = "#A99BFF";
	/** QG contrast gold, only for the clash look against a Getsuga-like beam. */
	public static final String QG = "#FFC24A";
	/** The GRADE multiply colours: Vollstaendig release, Sternenstaub. */
	public static final String GRADE_BANKAI = "#6F88C8";
	public static final String GRADE_STAR = "#3C4F8A";

	public static final float[] FQ0 = FxMath.hex(Q0);
	public static final float[] FQ1 = FxMath.hex(Q1);
	public static final float[] FQ2 = FxMath.hex(Q2);
	public static final float[] FQ3 = FxMath.hex(Q3);
	public static final float[] FQ7 = FxMath.hex(Q7);

	private UryuPalette() {
	}
}
