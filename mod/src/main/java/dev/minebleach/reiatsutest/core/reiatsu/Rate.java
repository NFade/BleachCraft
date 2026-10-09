package dev.minebleach.reiatsutest.core.reiatsu;

/** Reiatsu change per regeneration batch, in tenths: gross regeneration and upkeep drain. */
public record Rate(int regenPerBatch, int drainPerBatch) {
	public int net() {
		return regenPerBatch - drainPerBatch;
	}
}
