package pl.pansage.gamblecraft.sportsbook.bet;

/**
 * Paying a stake in minerals at the cashier's rates: the dearest pieces that fit first, then one more piece (the
 * cheapest that covers the rest) if the stake is not met exactly, with the change given back in chips.
 */
public record MineralPlan(long[] take, long change) {
	/** {@code values[i]} chips per piece, {@code counts[i]} pieces held; null if they are not worth the stake. */
	public static MineralPlan of(long amount, int[] values, long[] counts) {
		long worth = 0;
		for (int i = 0; i < values.length; i++) {
			worth += values[i] * counts[i];
		}
		if (amount <= 0 || worth < amount) {
			return null;
		}
		Integer[] order = new Integer[values.length];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) -> Integer.compare(values[b], values[a]));
		long[] take = new long[values.length];
		long left = amount;
		for (int i : order) {
			take[i] = Math.min(counts[i], left / values[i]);
			left -= take[i] * values[i];
		}
		if (left == 0) {
			return new MineralPlan(take, 0);
		}
		// every kind with pieces to spare is worth more than what is left, so one of them covers it
		int best = -1;
		for (int i = 0; i < values.length; i++) {
			if (counts[i] > take[i] && values[i] >= left && (best < 0 || values[i] < values[best])) {
				best = i;
			}
		}
		if (best < 0) {
			return null;
		}
		take[best]++;
		return new MineralPlan(take, values[best] - left);
	}
}
