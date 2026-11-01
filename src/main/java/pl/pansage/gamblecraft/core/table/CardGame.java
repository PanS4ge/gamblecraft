package pl.pansage.gamblecraft.core.table;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;
import pl.pansage.gamblecraft.core.table.TableTypes.SeatView;
import pl.pansage.gamblecraft.core.table.TableTypes.Settlement;
import pl.pansage.gamblecraft.core.table.TableTypes.TableSnapshot;
import pl.pansage.gamblecraft.core.table.TableTypes.Wager;

/**
 * Base for every card game. A game is a state machine driven from outside:
 * <ul>
 *     <li>{@link #step()} runs one atomic action (deal one card, reveal, settle) while {@link #needsStep()};
 *     the table calls it every few ticks so every card gets its own animation.</li>
 *     <li>{@link #act} applies a decision of the active seat while {@link #awaitingInput()}.</li>
 * </ul>
 * Money is whole units (items or chips). The table takes stakes from the player before calling
 * {@link #placeWager} or {@link #act} (see {@link ActionOption#minCost()}) and pays out {@link Settlement}s.
 */
public abstract class CardGame {
	public static final int DEALER = -1;
	public static final int COMMUNITY = -2;
	private static final int LOG_SIZE = 6;

	protected final RandomGenerator rng;
	protected final Seat[] seats;
	protected final List<TableHand> hands = new ArrayList<>();
	private final Deque<Runnable> script = new ArrayDeque<>();
	private final Deque<Msg> log = new ArrayDeque<>();
	private final List<Settlement> settlements = new ArrayList<>();
	protected Phase phase = Phase.BETTING;
	protected int activeSeat = -1;
	/** When true every private card is shown to everybody. */
	protected boolean showdown;
	private int round;

	protected static final class Seat {
		public final int index;
		public UUID player;
		public String name;
		public Wager wager;
		/** Everything this seat staked in the current round. */
		public long committed;
		/** Chips on the table, for games with buy-ins (Hold'em). */
		public long stack;
		public long lastPayout;
		public String status;
		public boolean inRound;
		public boolean leaving;

		Seat(int index) {
			this.index = index;
		}

		public boolean occupied() {
			return player != null;
		}
	}

	protected CardGame(int maxSeats, RandomGenerator rng) {
		this.rng = rng;
		this.seats = new Seat[maxSeats];
		for (int i = 0; i < maxSeats; i++) {
			seats[i] = new Seat(i);
		}
	}

	/** Stable id, also the translation key suffix ({@code gamblecraft.game.<id>}). */
	public abstract String id();

	/** Stack games take a buy-in on sitting down and pay the stack back on leaving. */
	public boolean usesStacks() {
		return false;
	}

	public int maxSeats() {
		return seats.length;
	}

	public Phase phase() {
		return phase;
	}

	public int activeSeat() {
		return activeSeat;
	}

	public int round() {
		return round;
	}

	// ---------------------------------------------------------------- seats

	/** Seats the player (or returns their existing seat). Returns -1 if the table is full. */
	public int sit(UUID player, String name) {
		int existing = seatOf(player);
		if (existing >= 0) {
			return existing;
		}
		for (Seat seat : seats) {
			if (!seat.occupied() && !reserved(seat.index)) {
				seat.player = player;
				seat.name = name;
				seat.wager = null;
				seat.stack = 0;
				seat.status = null;
				seat.leaving = false;
				return seat.index;
			}
		}
		return -1;
	}

	/** Seats players may not take (Hold'em keeps one for the house). */
	protected boolean reserved(int index) {
		return false;
	}

	/** Whether an occupied seat takes part in the next round. */
	protected boolean joinsRound(Seat seat) {
		return seat.occupied() && seat.wager != null;
	}

	public int seatOf(UUID player) {
		for (Seat seat : seats) {
			if (player.equals(seat.player)) {
				return seat.index;
			}
		}
		return -1;
	}

	public UUID playerAt(int seat) {
		return seats[seat].player;
	}

	public List<UUID> players() {
		List<UUID> players = new ArrayList<>();
		for (Seat seat : seats) {
			if (seat.occupied()) {
				players.add(seat.player);
			}
		}
		return players;
	}

	public boolean isEmpty() {
		return players().isEmpty();
	}

	/**
	 * Removes the player. Mid-round they forfeit their current decision and the seat frees up when the round ends.
	 * Returns the uncommitted wager to refund (and the stack for stack games).
	 */
	public long leave(UUID player) {
		int index = seatOf(player);
		if (index < 0) {
			return 0;
		}
		Seat seat = seats[index];
		if (seat.inRound && isRoundRunning()) {
			seat.leaving = true;
			onLeaveMidRound(index);
			return 0;
		}
		long refund = seat.wager != null ? seat.wager.total() : 0;
		if (usesStacks()) {
			refund += seat.stack;
		}
		free(seat);
		return refund;
	}

	protected void onLeaveMidRound(int seat) {
		if (awaitingInput() && activeSeat == seat) {
			act(seat, defaultAct(seat), 0);
		}
	}

	private void free(Seat seat) {
		seat.player = null;
		seat.name = null;
		seat.wager = null;
		seat.stack = 0;
		seat.inRound = false;
		seat.leaving = false;
		seat.status = null;
	}

	protected List<Integer> roundSeats() {
		List<Integer> list = new ArrayList<>();
		for (Seat seat : seats) {
			if (seat.inRound) {
				list.add(seat.index);
			}
		}
		return list;
	}

	// ---------------------------------------------------------------- betting

	public boolean isRoundRunning() {
		return phase == Phase.DEALING || phase == Phase.PLAYER_TURN || phase == Phase.DEALER_TURN;
	}

	public boolean acceptsWagers() {
		return !isRoundRunning();
	}

	/** Checks a wager before the table takes the stake. Override for game-specific options. */
	public boolean isValidWager(Wager wager) {
		return wager.amount() > 0 && wager.side() >= 0 && wager.option() == 0;
	}

	public void placeWager(int seat, Wager wager) {
		if (!acceptsWagers() || !seats[seat].occupied() || !isValidWager(wager)) {
			throw new IllegalActionException("Cannot place " + wager + " at seat " + seat);
		}
		seats[seat].wager = wager;
	}

	public Wager wagerOf(int seat) {
		return seats[seat].wager;
	}

	/** Returns the wager to refund and removes it. */
	public long takeBackWager(int seat) {
		if (!acceptsWagers() || seats[seat].wager == null) {
			return 0;
		}
		long amount = seats[seat].wager.total();
		seats[seat].wager = null;
		return amount;
	}

	public boolean canStart() {
		if (!acceptsWagers()) {
			return false;
		}
		for (Seat seat : seats) {
			if (joinsRound(seat)) {
				return true;
			}
		}
		return false;
	}

	public void startRound() {
		if (!canStart()) {
			throw new IllegalActionException("Nothing to start");
		}
		round++;
		hands.clear();
		script.clear();
		showdown = false;
		activeSeat = -1;
		for (Seat seat : seats) {
			seat.inRound = joinsRound(seat);
			seat.committed = seat.inRound && seat.wager != null ? seat.wager.total() : 0;
			seat.status = null;
			seat.lastPayout = 0;
		}
		phase = Phase.DEALING;
		onStartRound();
	}

	protected abstract void onStartRound();

	// ---------------------------------------------------------------- driving

	public boolean needsStep() {
		return !script.isEmpty() || phase == Phase.DEALING || phase == Phase.DEALER_TURN;
	}

	public void step() {
		if (!script.isEmpty()) {
			script.removeFirst().run();
		} else if (phase == Phase.DEALING || phase == Phase.DEALER_TURN) {
			advance();
		}
	}

	/** Runs every pending step at once (tests and fast-forward). */
	public void runToInput() {
		int guard = 0;
		while (needsStep() && !awaitingInput()) {
			step();
			if (++guard > 10_000) {
				throw new IllegalStateException(id() + " is stuck in " + phase);
			}
		}
	}

	/** Queues an atomic action for a later {@link #step()}. */
	protected void then(Runnable action) {
		script.addLast(action);
	}

	/** Called by {@link #step()} when nothing is queued and the phase is automatic. */
	protected abstract void advance();

	// ---------------------------------------------------------------- decisions

	public boolean awaitingInput() {
		return phase == Phase.PLAYER_TURN && script.isEmpty() && activeSeat >= 0;
	}

	public List<ActionOption> options(int seat) {
		return awaitingInput() && seat == activeSeat ? legalActions(seat) : List.of();
	}

	protected abstract List<ActionOption> legalActions(int seat);

	/** The free action taken when the player times out or leaves. */
	public abstract Act defaultAct(int seat);

	/**
	 * Applies a decision. {@code amount} is the extra stake for actions with a variable cost (RAISE)
	 * or an argument (HOLD index); fixed-cost actions ignore it.
	 */
	public final void act(int seat, Act act, long amount) {
		ActionOption option = options(seat).stream().filter(o -> o.act() == act).findFirst()
				.orElseThrow(() -> new IllegalActionException(act + " not allowed for seat " + seat));
		long cost;
		if (option.maxCost() == 0) {
			cost = 0;
		} else if (option.minCost() == option.maxCost()) {
			cost = option.minCost();
		} else {
			if (amount < option.minCost() || amount > option.maxCost()) {
				throw new IllegalActionException(act + " amount " + amount + " outside " + option.minCost() + ".." + option.maxCost());
			}
			cost = amount;
		}
		if (!usesStacks()) {
			seats[seat].committed += cost;
		}
		onAct(seat, act, cost, amount);
	}

	/** Cost of an action for the table to collect before calling {@link #act}. */
	public long costOf(int seat, Act act, long amount) {
		for (ActionOption option : options(seat)) {
			if (option.act() == act) {
				if (option.maxCost() == 0) {
					return 0;
				}
				return option.minCost() == option.maxCost() ? option.minCost() : amount;
			}
		}
		return -1;
	}

	protected abstract void onAct(int seat, Act act, long cost, long amount);

	public void timeout() {
		if (awaitingInput()) {
			act(activeSeat, defaultAct(activeSeat), 0);
		}
	}

	// ---------------------------------------------------------------- settlement

	protected void settle(int seat, long payout, String outcome) {
		Seat s = seats[seat];
		s.lastPayout = payout;
		s.status = outcome;
		settlements.add(new Settlement(seat, s.player, payout, s.committed, outcome));
	}

	protected void finishRound() {
		phase = Phase.SETTLED;
		activeSeat = -1;
		for (Seat seat : seats) {
			seat.inRound = false;
			seat.wager = null;
			if (seat.leaving) {
				if (usesStacks() && seat.stack > 0) {
					settlements.add(new Settlement(seat.index, seat.player, seat.stack, 0, "cash_out"));
				}
				free(seat);
			}
		}
	}

	/** Settlements produced since the last call; the table pays them out. */
	public List<Settlement> drainSettlements() {
		List<Settlement> out = List.copyOf(settlements);
		settlements.clear();
		return out;
	}

	// ---------------------------------------------------------------- view

	protected void log(String key, Object... args) {
		log.addLast(Msg.of(key, args));
		while (log.size() > LOG_SIZE) {
			log.removeFirst();
		}
	}

	protected long pot() {
		return 0;
	}

	protected TableHand hand(String id) {
		for (TableHand hand : hands) {
			if (hand.id.equals(id)) {
				return hand;
			}
		}
		return null;
	}

	public TableSnapshot snapshot(UUID viewer) {
		int viewerSeat = viewer == null ? -99 : seatOf(viewer);
		List<SeatView> seatViews = new ArrayList<>();
		for (Seat seat : seats) {
			if (seat.occupied()) {
				Wager w = seat.wager;
				seatViews.add(new SeatView(seat.index, seat.player, seat.name,
						w != null ? w.amount() : seat.committed, w != null ? w.side() : 0, w != null ? w.option() : 0,
						seat.stack, seat.lastPayout, seat.status));
			}
		}
		return new TableSnapshot(id(), phase, activeSeat, round, seatViews,
				hands.stream().map(h -> h.view(viewerSeat, showdown)).toList(),
				pot(), viewerSeat >= 0 ? options(viewerSeat) : List.of(), List.copyOf(log));
	}
}
