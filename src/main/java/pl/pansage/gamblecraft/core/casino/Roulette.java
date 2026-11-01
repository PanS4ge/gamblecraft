package pl.pansage.gamblecraft.core.casino;

import java.util.Set;
import java.util.TreeSet;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * European single-zero roulette. Every bet covers a set of numbers and returns {@code 36 / size} times the stake
 * when it wins (straight 35:1 ... even money 1:1), so the house edge is 1/37 on every bet.
 */
public final class Roulette {
	/** Pocket order around the wheel, clockwise from zero. */
	public static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1,
			20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
	private static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

	public record Bet(String key, Set<Integer> numbers) {
		public Bet {
			numbers = Set.copyOf(numbers);
			if (numbers.isEmpty() || 36 % numbers.size() != 0) {
				throw new IllegalArgumentException("Bad bet " + key + " " + numbers);
			}
		}

		/** Total returned per unit staked when the bet wins. */
		public int payout() {
			return 36 / numbers.size();
		}

		public boolean wins(int result) {
			return numbers.contains(result);
		}

		/** Stable id for saving and packets, e.g. {@code split:17,20}. */
		public String id() {
			return key + ":" + new TreeSet<>(numbers).stream().map(String::valueOf).collect(Collectors.joining(","));
		}

		public static Bet fromId(String id) {
			int colon = id.indexOf(':');
			Set<Integer> nums = java.util.Arrays.stream(id.substring(colon + 1).split(",")).map(Integer::parseInt).collect(Collectors.toSet());
			Bet bet = new Bet(id.substring(0, colon), nums);
			validate(bet);
			return bet;
		}
	}

	private Roulette() {
	}

	public static int spin(RandomGenerator rng) {
		return rng.nextInt(37);
	}

	public static boolean isRed(int n) {
		return RED.contains(n);
	}

	public static int wheelIndex(int number) {
		for (int i = 0; i < WHEEL.length; i++) {
			if (WHEEL[i] == number) {
				return i;
			}
		}
		throw new IllegalArgumentException(String.valueOf(number));
	}

	/** Felt column (1..12) and row (0 = top row 3,6,9.. ; 2 = bottom row 1,4,7..) of a number 1..36. */
	public static int column(int n) {
		return (n + 2) / 3;
	}

	public static int row(int n) {
		return 2 - (n - 1) % 3;
	}

	public static int numberAt(int column, int row) {
		return column * 3 - row;
	}

	// ---------------------------------------------------------------- bet factories

	public static Bet straight(int n) {
		return new Bet("straight", Set.of(n));
	}

	public static Bet split(int a, int b) {
		return new Bet("split", Set.of(a, b));
	}

	public static Bet street(int column) {
		return new Bet("street", Set.of(numberAt(column, 0), numberAt(column, 1), numberAt(column, 2)));
	}

	public static Bet corner(int lowColumn, int topRow) {
		return new Bet("corner", Set.of(numberAt(lowColumn, topRow), numberAt(lowColumn, topRow + 1),
				numberAt(lowColumn + 1, topRow), numberAt(lowColumn + 1, topRow + 1)));
	}

	public static Bet sixLine(int lowColumn) {
		Set<Integer> nums = new TreeSet<>(street(lowColumn).numbers());
		nums.addAll(street(lowColumn + 1).numbers());
		return new Bet("six_line", nums);
	}

	public static Bet dozen(int index) {
		return new Bet("dozen", range(index * 12 + 1, index * 12 + 12));
	}

	/** Column bet for felt row 0..2 (row 0 = 3,6,...,36). */
	public static Bet columnBet(int row) {
		return new Bet("column", IntStream.rangeClosed(1, 12).map(c -> numberAt(c, row)).boxed().collect(Collectors.toSet()));
	}

	public static Bet red() {
		return new Bet("red", RED);
	}

	public static Bet black() {
		return new Bet("black", IntStream.rangeClosed(1, 36).filter(n -> !RED.contains(n)).boxed().collect(Collectors.toSet()));
	}

	public static Bet even() {
		return new Bet("even", IntStream.rangeClosed(1, 36).filter(n -> n % 2 == 0).boxed().collect(Collectors.toSet()));
	}

	public static Bet odd() {
		return new Bet("odd", IntStream.rangeClosed(1, 36).filter(n -> n % 2 == 1).boxed().collect(Collectors.toSet()));
	}

	public static Bet low() {
		return new Bet("low", range(1, 18));
	}

	public static Bet high() {
		return new Bet("high", range(19, 36));
	}

	private static Set<Integer> range(int from, int to) {
		return IntStream.rangeClosed(from, to).boxed().collect(Collectors.toSet());
	}

	/** Rejects number sets that are not a real bet of that kind (packets come from clients). */
	public static void validate(Bet bet) {
		boolean ok = switch (bet.key()) {
			case "straight" -> bet.numbers().size() == 1 && bet.numbers().iterator().next() <= 36 && bet.numbers().iterator().next() >= 0;
			case "split" -> isSplit(bet.numbers());
			case "street" -> IntStream.rangeClosed(1, 12).anyMatch(c -> street(c).numbers().equals(bet.numbers()));
			case "corner" -> IntStream.rangeClosed(1, 11).anyMatch(c -> corner(c, 0).numbers().equals(bet.numbers()) || corner(c, 1).numbers().equals(bet.numbers()));
			case "six_line" -> IntStream.rangeClosed(1, 11).anyMatch(c -> sixLine(c).numbers().equals(bet.numbers()));
			case "dozen" -> IntStream.range(0, 3).anyMatch(i -> dozen(i).numbers().equals(bet.numbers()));
			case "column" -> IntStream.range(0, 3).anyMatch(i -> columnBet(i).numbers().equals(bet.numbers()));
			case "red" -> red().numbers().equals(bet.numbers());
			case "black" -> black().numbers().equals(bet.numbers());
			case "even" -> even().numbers().equals(bet.numbers());
			case "odd" -> odd().numbers().equals(bet.numbers());
			case "low" -> low().numbers().equals(bet.numbers());
			case "high" -> high().numbers().equals(bet.numbers());
			default -> false;
		};
		if (!ok) {
			throw new IllegalArgumentException("Not a valid roulette bet: " + bet);
		}
	}

	private static boolean isSplit(Set<Integer> nums) {
		if (nums.size() != 2) {
			return false;
		}
		var it = nums.iterator();
		int a = it.next();
		int b = it.next();
		int lo = Math.min(a, b);
		int hi = Math.max(a, b);
		if (lo == 0) {
			return hi >= 1 && hi <= 3;
		}
		if (hi > 36) {
			return false;
		}
		boolean sameColumn = column(lo) == column(hi) && hi - lo == 1;
		boolean sameRow = row(lo) == row(hi) && hi - lo == 3;
		return sameColumn || sameRow;
	}

	/**
	 * Maps a point on the felt to a bet. {@code x} runs 0..14 along the table (0..1 is the zero, 1..13 the twelve
	 * number columns, 13..14 the "2 to 1" column bets); {@code y} runs 0..5 across it (0..3 number rows from the top,
	 * 3..4 dozens, 4..5 even-money bets). Points close to a line between numbers pick splits, streets, corners and six lines.
	 */
	public static Bet betAt(double x, double y) {
		final double edge = 0.22;
		if (x < 0 || y < 0 || x >= 14 || y >= 5) {
			return null;
		}
		if (y >= 4) {
			int zone = (int) ((x - 1) / 2);
			if (x < 1 || zone > 5) {
				return null;
			}
			return switch (zone) {
				case 0 -> low();
				case 1 -> even();
				case 2 -> red();
				case 3 -> black();
				case 4 -> odd();
				default -> high();
			};
		}
		if (y >= 3) {
			if (x < 1 || x >= 13) {
				return null;
			}
			double cx = x - 1;
			// Top edge of the dozens strip = bottom edge of the number grid: streets and six lines.
			if (y - 3 < edge) {
				int col = (int) cx + 1;
				double fx = cx - (int) cx;
				if (fx < edge && col > 1) {
					return sixLine(col - 1);
				}
				if (fx > 1 - edge && col < 12) {
					return sixLine(col);
				}
				return street(col);
			}
			return dozen((int) (cx / 4));
		}
		int row = (int) y;
		double fy = y - row;
		if (x < 1) {
			return straight(0);
		}
		if (x >= 13) {
			return columnBet(row);
		}
		double cx = x - 1;
		int col = (int) cx + 1;
		double fx = cx - (int) cx;
		boolean nearLeft = fx < edge;
		boolean nearRight = fx > 1 - edge;
		boolean nearTop = fy < edge && row > 0;
		boolean nearBottom = fy > 1 - edge && row < 2;
		int n = numberAt(col, row);
		if (nearLeft && col == 1) {
			return split(0, n);
		}
		if ((nearLeft || nearRight) && (nearTop || nearBottom)) {
			int lowCol = nearLeft ? col - 1 : col;
			int topRow = nearTop ? row - 1 : row;
			if (lowCol >= 1 && lowCol <= 11) {
				return corner(lowCol, topRow);
			}
		}
		if (nearLeft && col > 1) {
			return split(numberAt(col - 1, row), n);
		}
		if (nearRight && col < 12) {
			return split(n, numberAt(col + 1, row));
		}
		if (nearTop) {
			return split(numberAt(col, row - 1), n);
		}
		if (nearBottom) {
			return split(n, numberAt(col, row + 1));
		}
		return straight(n);
	}
}
