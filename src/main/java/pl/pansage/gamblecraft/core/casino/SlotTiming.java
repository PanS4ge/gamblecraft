package pl.pansage.gamblecraft.core.casino;

/**
 * How a spin plays out over time, shared by the server (when to pay) and every client (where the reels are).
 * The lever goes down, the reels roll downwards, then stop left to right; when the first two middle symbols
 * match, the last reel creeps to a halt to build suspense.
 */
public final class SlotTiming {
	/** Ticks of lever pull before the reels move. */
	public static final int LEVER = 5;
	/** When each reel stops (ticks after the pull starts). */
	private static final int[] STOP = {28, 38, 48};
	private static final int SUSPENSE = 26;
	/** Stops per tick at full speed. */
	public static final double SPEED = 0.85;
	private static final double BRAKE = 8;
	private static final double SUSPENSE_BRAKE = 30;

	private SlotTiming() {
	}

	public static boolean suspense(SlotMath.Spin spin) {
		return spin.grid()[1][0] == spin.grid()[1][1];
	}

	public static int stopTick(int reel, boolean suspense) {
		return STOP[reel] + (reel == 2 && suspense ? SUSPENSE : 0);
	}

	/** When the result is paid and shown. */
	public static int doneTick(boolean suspense) {
		return stopTick(2, suspense) + 6;
	}

	/**
	 * The reel's position in strip stops at {@code t} ticks into the spin; it rolls downwards (the number falls)
	 * and lands exactly on {@code stop}, easing in over the last few ticks.
	 */
	public static double position(int reel, int stop, double t, boolean suspense) {
		double end = stopTick(reel, suspense);
		double brake = reel == 2 && suspense ? SUSPENSE_BRAKE : BRAKE;
		double left = end - Math.max(LEVER, t);
		if (left <= 0) {
			return stop;
		}
		double remaining = left > brake ? SPEED * (left - brake / 2) : SPEED * left * left / (2 * brake);
		return stop + remaining;
	}

	/** True while the reel is still turning at {@code t}. */
	public static boolean moving(int reel, double t, boolean suspense) {
		return t < stopTick(reel, suspense);
	}
}
