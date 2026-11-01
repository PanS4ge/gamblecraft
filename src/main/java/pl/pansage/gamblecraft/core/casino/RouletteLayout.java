package pl.pansage.gamblecraft.core.casino;

import java.util.ArrayList;
import java.util.List;

import pl.pansage.gamblecraft.core.casino.Roulette.Bet;

/**
 * The betting felt of a roulette table, three blocks long and one wide, in board space: {@code u} runs 0..3 along
 * the table away from the wheel, {@code v} runs 0..1 across it towards the players. Zero sits next to the wheel,
 * then twelve columns of numbers (the top row 3, 6, ... furthest from the players), the 2:1 column bets, and
 * below the numbers the dozens and the even-money bets. Clicking near a line between numbers bets on both
 * (split), near a crossing on all four (corner).
 */
public final class RouletteLayout {
	public static final double LENGTH = 3.0;
	public static final double ZERO_END = 0.25;
	public static final double GRID_END = 2.75;
	public static final double COLUMN_W = (GRID_END - ZERO_END) / 12;
	public static final double ROW_H = 0.62 / 3;
	public static final double GRID_BOTTOM = 0.62;
	public static final double DOZEN_BOTTOM = 0.80;
	/** How close to a line (as a share of the cell) a click counts as on the line. */
	private static final double EDGE = 0.2;

	/** A rectangle of the felt and what it shows; {@code bet} is null for decoration. */
	public record Cell(double u0, double v0, double u1, double v1, String label, int color, Bet bet) {
	}

	public static final int GREEN = 0xFF1F7A3A;
	public static final int RED = 0xFFB3202A;
	public static final int BLACK = 0xFF1A1A1E;
	public static final int FELT = 0xFF14602E;

	private static final String[] OUTSIDE = {"1-18", "EVEN", "RED", "BLACK", "ODD", "19-36"};

	private RouletteLayout() {
	}

	private static Bet outside(int index) {
		return switch (index) {
			case 0 -> Roulette.low();
			case 1 -> Roulette.even();
			case 2 -> Roulette.red();
			case 3 -> Roulette.black();
			case 4 -> Roulette.odd();
			default -> Roulette.high();
		};
	}

	public static int colorOf(int number) {
		return number == 0 ? GREEN : Roulette.isRed(number) ? RED : BLACK;
	}

	/** Every cell drawn on the felt, each with the bet it takes. */
	public static List<Cell> cells() {
		List<Cell> cells = new ArrayList<>();
		cells.add(new Cell(0, 0, ZERO_END, GRID_BOTTOM, "0", GREEN, Roulette.straight(0)));
		for (int column = 1; column <= 12; column++) {
			double u0 = ZERO_END + (column - 1) * COLUMN_W;
			for (int row = 0; row < 3; row++) {
				int n = Roulette.numberAt(column, row);
				cells.add(new Cell(u0, row * ROW_H, u0 + COLUMN_W, (row + 1) * ROW_H, String.valueOf(n), colorOf(n), Roulette.straight(n)));
			}
		}
		for (int row = 0; row < 3; row++) {
			cells.add(new Cell(GRID_END, row * ROW_H, LENGTH, (row + 1) * ROW_H, "2:1", FELT, Roulette.columnBet(row)));
		}
		String[] dozens = {"1st 12", "2nd 12", "3rd 12"};
		for (int i = 0; i < 3; i++) {
			double u0 = ZERO_END + i * 4 * COLUMN_W;
			cells.add(new Cell(u0, GRID_BOTTOM, u0 + 4 * COLUMN_W, DOZEN_BOTTOM, dozens[i], FELT, Roulette.dozen(i)));
		}
		for (int i = 0; i < 6; i++) {
			double u0 = ZERO_END + i * 2 * COLUMN_W;
			int color = i == 2 ? RED : i == 3 ? BLACK : FELT;
			cells.add(new Cell(u0, DOZEN_BOTTOM, u0 + 2 * COLUMN_W, 1.0, OUTSIDE[i], color, outside(i)));
		}
		return cells;
	}

	/** The bet under a point of the felt, or null off the betting areas. */
	public static Bet betAt(double u, double v) {
		if (u < 0 || u >= LENGTH || v < 0 || v >= 1) {
			return null;
		}
		if (u >= ZERO_END && u < GRID_END && v < GRID_BOTTOM) {
			return insideBet(u, v);
		}
		for (Cell cell : cells()) {
			if (u >= cell.u0() && u < cell.u1() && v >= cell.v0() && v < cell.v1()) {
				return cell.bet();
			}
		}
		return null;
	}

	/** Straight up, or a split or corner when the point is close to the lines between numbers. */
	private static Bet insideBet(double u, double v) {
		double cu = (u - ZERO_END) / COLUMN_W;
		double cv = v / ROW_H;
		int column = Math.min(12, (int) cu + 1);
		int row = Math.min(2, (int) cv);
		double fu = cu - (column - 1);
		double fv = cv - row;
		int nextColumn = fu > 1 - EDGE && column < 12 ? column + 1 : fu < EDGE && column > 1 ? column - 1 : 0;
		int nextRow = fv > 1 - EDGE && row < 2 ? row + 1 : fv < EDGE && row > 0 ? row - 1 : -1;
		if (nextColumn > 0 && nextRow >= 0) {
			return Roulette.corner(Math.min(column, nextColumn), Math.min(row, nextRow));
		}
		if (nextColumn > 0) {
			return Roulette.split(Roulette.numberAt(column, row), Roulette.numberAt(nextColumn, row));
		}
		if (nextRow >= 0) {
			return Roulette.split(Roulette.numberAt(column, row), Roulette.numberAt(column, nextRow));
		}
		return Roulette.straight(Roulette.numberAt(column, row));
	}

	/** Where the chips for a bet sit: the middle of its cell, or the line or crossing between its numbers. */
	public static double[] anchor(Bet bet) {
		boolean inside = switch (bet.key()) {
			case "straight", "split", "corner" -> !bet.numbers().contains(0);
			default -> false;
		};
		if (inside) {
			double u = 0;
			double v = 0;
			for (int n : bet.numbers()) {
				u += ZERO_END + (Roulette.column(n) - 0.5) * COLUMN_W;
				v += (Roulette.row(n) + 0.5) * ROW_H;
			}
			return new double[] {u / bet.numbers().size(), v / bet.numbers().size()};
		}
		for (Cell cell : cells()) {
			if (cell.bet() != null && cell.bet().id().equals(bet.id())) {
				return new double[] {(cell.u0() + cell.u1()) / 2, (cell.v0() + cell.v1()) / 2};
			}
		}
		return new double[] {LENGTH / 2, 0.5};
	}
}
