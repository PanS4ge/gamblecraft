package pl.pansage.gamblecraft.core.table;

import java.util.List;
import java.util.UUID;

import pl.pansage.gamblecraft.core.cards.Card;

/** Small value types shared by every card game and sent to clients. */
public final class TableTypes {
	private TableTypes() {
	}

	public enum Phase { BETTING, DEALING, PLAYER_TURN, DEALER_TURN, SETTLED }

	/**
	 * A decision the active seat may take. {@code minCost..maxCost} is the extra stake it requires
	 * (0 for free actions); RAISE lets the player pick an amount in that range.
	 */
	public record ActionOption(Act act, long minCost, long maxCost) {
		public static ActionOption free(Act act) {
			return new ActionOption(act, 0, 0);
		}

		public static ActionOption costing(Act act, long cost) {
			return new ActionOption(act, cost, cost);
		}
	}

	/** Stake placed before a round. {@code option} picks a bet target (e.g. baccarat side), {@code side} is an optional side bet. */
	public record Wager(long amount, int option, long side) {
		public static Wager of(long amount) {
			return new Wager(amount, 0, 0);
		}

		public long total() {
			return amount + side;
		}
	}

	/** A translatable message: key plus string arguments. */
	public record Msg(String key, List<String> args) {
		public static Msg of(String key, Object... args) {
			return new Msg(key, java.util.Arrays.stream(args).map(String::valueOf).toList());
		}
	}

	public record PlacedCard(Card card, boolean faceUp) {
	}

	/** Card ids, with {@code -1} for a card the viewer cannot see. */
	public record HandView(String id, int owner, String label, List<Integer> cards, Msg value, String result, boolean active, int marks) {
	}

	public record SeatView(int index, UUID player, String name, long bet, long side, int option, long stack, long lastPayout, String status) {
	}

	public record TableSnapshot(String game, Phase phase, int activeSeat, int round, List<SeatView> seats, List<HandView> hands,
			long pot, List<ActionOption> actions, List<Msg> log) {
	}

	/** Money returned to a seat at the end of a round (stake included). 0 means the stake was lost. */
	public record Settlement(int seat, UUID player, long payout, long committed, String outcome) {
	}
}
