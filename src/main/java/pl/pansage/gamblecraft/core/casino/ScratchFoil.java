package pl.pansage.gamblecraft.core.casino;

import java.util.Arrays;

/**
 * The silver layer over a 3x3 scratch card, in grid units: cells of {@link #CELL} with gaps of {@link #GAP},
 * {@link #SIZE} across. Each cell is split into {@link #SUB}x{@link #SUB} bits of foil; once {@link #REVEAL_AT}
 * of a cell is rubbed off, the rest of it falls off too. The same foil runs on the server (the truth that other
 * players are sent) and on every client (so rubbing shows at once).
 */
public final class ScratchFoil {
	public static final int GRID = 3;
	public static final int CELL = 46;
	public static final int GAP = 6;
	public static final int SIZE = GRID * CELL + (GRID - 1) * GAP;
	public static final int SUB = 12;
	public static final double RADIUS = 6;
	public static final float REVEAL_AT = 0.6F;
	private static final int BITS = SUB * SUB;
	private static final float BIT = CELL / (float) SUB;

	/** Receives the centre (grid units) of every bit of foil that comes off. */
	public interface Flakes {
		void at(float x, float y);
	}

	private final boolean[][] covered = new boolean[GRID * GRID][BITS];
	private final boolean[] revealed = new boolean[GRID * GRID];

	public ScratchFoil() {
		for (boolean[] cell : covered) {
			Arrays.fill(cell, true);
		}
	}

	public static int cellX(int cell) {
		return (cell % GRID) * (CELL + GAP);
	}

	public static int cellY(int cell) {
		return (cell / GRID) * (CELL + GAP);
	}

	public boolean covered(int cell, int bit) {
		return covered[cell][bit];
	}

	public boolean isRevealed(int cell) {
		return revealed[cell];
	}

	public boolean allRevealed() {
		for (boolean r : revealed) {
			if (!r) {
				return false;
			}
		}
		return true;
	}

	/** Rubs at one point. Returns how many bits came off. */
	public int rub(double x, double y, Flakes flakes) {
		int removed = 0;
		for (int cell = 0; cell < covered.length; cell++) {
			if (revealed[cell]) {
				continue;
			}
			int cx = cellX(cell);
			int cy = cellY(cell);
			if (x < cx - RADIUS || y < cy - RADIUS || x > cx + CELL + RADIUS || y > cy + CELL + RADIUS) {
				continue;
			}
			int cleared = 0;
			for (int i = 0; i < BITS; i++) {
				float bx = cx + (i % SUB + 0.5F) * BIT;
				float by = cy + (i / SUB + 0.5F) * BIT;
				if (covered[cell][i] && (bx - x) * (bx - x) + (by - y) * (by - y) < RADIUS * RADIUS) {
					covered[cell][i] = false;
					removed++;
					if (flakes != null) {
						flakes.at(bx, by);
					}
				}
				if (!covered[cell][i]) {
					cleared++;
				}
			}
			if (cleared >= BITS * REVEAL_AT) {
				removed += reveal(cell, flakes);
			}
		}
		return removed;
	}

	/** Rubs along a stroke, every 2 grid units. */
	public int rubLine(double x0, double y0, double x1, double y1, Flakes flakes) {
		int steps = Math.max(1, (int) (Math.hypot(x1 - x0, y1 - y0) / 2));
		int removed = 0;
		for (int s = 0; s <= steps; s++) {
			double t = s / (double) steps;
			removed += rub(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, flakes);
		}
		return removed;
	}

	public int revealAll(Flakes flakes) {
		int removed = 0;
		for (int cell = 0; cell < revealed.length; cell++) {
			removed += reveal(cell, flakes);
		}
		return removed;
	}

	private int reveal(int cell, Flakes flakes) {
		if (revealed[cell]) {
			return 0;
		}
		revealed[cell] = true;
		int removed = 0;
		for (int i = 0; i < BITS; i++) {
			if (covered[cell][i]) {
				covered[cell][i] = false;
				removed++;
				if (flakes != null) {
					flakes.at(cellX(cell) + (i % SUB + 0.5F) * BIT, cellY(cell) + (i / SUB + 0.5F) * BIT);
				}
			}
		}
		return removed;
	}

	/** One bit per piece of foil, cell by cell (162 bytes). */
	public byte[] toBytes() {
		byte[] out = new byte[(covered.length * BITS + 7) / 8];
		for (int cell = 0; cell < covered.length; cell++) {
			for (int i = 0; i < BITS; i++) {
				if (covered[cell][i]) {
					int n = cell * BITS + i;
					out[n >> 3] |= (byte) (1 << (n & 7));
				}
			}
		}
		return out;
	}

	public static ScratchFoil fromBytes(byte[] bytes) {
		ScratchFoil foil = new ScratchFoil();
		for (int cell = 0; cell < foil.covered.length; cell++) {
			int cleared = 0;
			for (int i = 0; i < BITS; i++) {
				int n = cell * BITS + i;
				boolean on = (n >> 3) < bytes.length && (bytes[n >> 3] & (1 << (n & 7))) != 0;
				foil.covered[cell][i] = on;
				if (!on) {
					cleared++;
				}
			}
			foil.revealed[cell] = cleared >= BITS * REVEAL_AT;
		}
		return foil;
	}
}
