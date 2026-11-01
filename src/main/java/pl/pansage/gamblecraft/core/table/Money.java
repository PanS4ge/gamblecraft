package pl.pansage.gamblecraft.core.table;

import java.util.random.RandomGenerator;

/** Integer money helpers. Stakes are whole items or chips, so fractional payouts are rounded without bias. */
public final class Money {
	private Money() {
	}

	/**
	 * {@code amount * num / den}, rounded down or up at random in proportion to the remainder,
	 * so the expected value is exact (3:2 on a 1-item bet pays 1 or 2 with equal chance).
	 */
	public static long scale(long amount, long num, long den, RandomGenerator rng) {
		long whole = Math.multiplyExact(amount, num);
		long base = whole / den;
		long remainder = whole % den;
		return remainder > 0 && rng.nextLong(den) < remainder ? base + 1 : base;
	}

	/** Same as {@link #scale(long, long, long, RandomGenerator)} for a real-valued multiplier (precision 1/10000). */
	public static long scale(long amount, double multiplier, RandomGenerator rng) {
		return scale(amount, Math.round(multiplier * 10_000), 10_000, rng);
	}
}
