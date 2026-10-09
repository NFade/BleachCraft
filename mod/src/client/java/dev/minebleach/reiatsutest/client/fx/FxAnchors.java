package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.entity.FxAnchorEntity;

/** Client side bookkeeping of the anchors in view (harness checks, later the renderers' registry). */
public final class FxAnchors {
	public static volatile int rendered;
	public static volatile FxAnchorEntity last;

	private FxAnchors() {
	}

	static void seen(FxAnchorEntity e) {
		rendered++;
		last = e;
	}
}
