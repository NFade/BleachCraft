package dev.minebleach.reiatsutest.core.book;

/** Page arithmetic of the book screen: indices are 0 based and always clamped into {@code [0, count - 1]}. */
public final class BookPager {
	private BookPager() {
	}

	public static int clamp(int page, int count) {
		return count <= 0 ? 0 : Math.max(0, Math.min(page, count - 1));
	}

	public static int next(int page, int count) {
		return clamp(page + 1, count);
	}

	public static int prev(int page, int count) {
		return clamp(page - 1, count);
	}

	public static boolean hasNext(int page, int count) {
		return page < count - 1;
	}

	public static boolean hasPrev(int page, int count) {
		return page > 0 && count > 0;
	}
}
