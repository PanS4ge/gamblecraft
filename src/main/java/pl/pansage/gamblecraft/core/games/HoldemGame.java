package pl.pansage.gamblecraft.core.games;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.cards.Shoe;
import pl.pansage.gamblecraft.core.poker.HandValue;
import pl.pansage.gamblecraft.core.poker.PokerEvaluator;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.TableHand;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;

/**
 * No-limit Texas Hold'em with buy-in stacks, blinds, all-ins and side pots.
 * Players play each other; a player alone at the table plays heads-up against the house dealer
 * (a bot in the last, reserved seat with a house-funded stack).
 */
public final class HoldemGame extends CardGame {
	public static final UUID HOUSE = new UUID(0x6761_6d62_6c65L, 0x686f_7573_65L);

	private final long smallBlind;
	private final long bigBlind;
	private final int houseSeat;
	private final Shoe shoe;
	private final long[] contributed;
	private final long[] streetBet;
	private final boolean[] folded;
	private final boolean[] acted;
	private TableHand board;
	private int button = -1;
	private int street;
	private long currentBet;
	private long minRaise;
	/** A duel: the house plays one stake for the whole match instead of topping up every round. */
	private boolean duel;
	private long duelBank;
	private boolean duelStarted;
	private String houseName = "Dealer";

	public HoldemGame(int playerSeats, long smallBlind, long bigBlind, RandomGenerator rng) {
		super(playerSeats + 1, rng);
		this.smallBlind = smallBlind;
		this.bigBlind = bigBlind;
		this.houseSeat = playerSeats;
		this.shoe = new Shoe(1, rng);
		int n = playerSeats + 1;
		this.contributed = new long[n];
		this.streetBet = new long[n];
		this.folded = new boolean[n];
		this.acted = new boolean[n];
	}

	/**
	 * Heads-up against one house player with {@code houseBank} chips for the whole match: the house's stack carries from
	 * round to round, and once it cannot post a big blind the player has broken it.
	 */
	public static HoldemGame duel(long smallBlind, long bigBlind, long houseBank, String houseName, RandomGenerator rng) {
		HoldemGame game = new HoldemGame(1, smallBlind, bigBlind, rng);
		game.duel = true;
		game.duelBank = houseBank;
		game.houseName = houseName;
		return game;
	}

	public boolean isDuel() {
		return duel;
	}

	public long houseStack() {
		return seats[houseSeat].stack;
	}

	/** A duel's house is out of chips (checked between rounds). */
	public boolean houseBroke() {
		return duel && duelStarted && !isRoundRunning() && seats[houseSeat].stack < bigBlind;
	}

	@Override
	public String id() {
		return "holdem";
	}

	@Override
	public boolean usesStacks() {
		return true;
	}

	public long bigBlind() {
		return bigBlind;
	}

	public long minBuyIn() {
		return bigBlind * 10;
	}

	public long maxBuyIn() {
		return bigBlind * 100;
	}

	public Shoe shoe() {
		return shoe;
	}

	@Override
	protected boolean reserved(int index) {
		return index == houseSeat;
	}

	@Override
	protected boolean joinsRound(Seat seat) {
		return seat.occupied() && seat.stack >= bigBlind;
	}

	/** Adds chips the table took from the player. */
	public void buyIn(int seat, long amount) {
		if (!acceptsWagers() || amount <= 0 || seat == houseSeat) {
			throw new pl.pansage.gamblecraft.core.table.IllegalActionException("Cannot buy in now");
		}
		seats[seat].stack += amount;
	}

	public long stackOf(int seat) {
		return seats[seat].stack;
	}

	private int humansReady() {
		int n = 0;
		for (Seat seat : seats) {
			if (seat.index != houseSeat && joinsRound(seat)) {
				n++;
			}
		}
		return n;
	}

	@Override
	public boolean canStart() {
		return acceptsWagers() && humansReady() >= 1 && !(duel && duelStarted && seats[houseSeat].stack < bigBlind);
	}

	@Override
	public void startRound() {
		Seat house = seats[houseSeat];
		if (duel) {
			house.player = HOUSE;
			house.name = houseName;
			if (!duelStarted) {
				house.stack = duelBank;
				duelStarted = true;
			}
		} else if (humansReady() == 1) {
			long topHuman = 0;
			for (Seat seat : seats) {
				if (seat.index != houseSeat && seat.occupied()) {
					topHuman = Math.max(topHuman, seat.stack);
				}
			}
			house.player = HOUSE;
			house.name = "Dealer";
			house.stack = Math.max(maxBuyIn(), topHuman);
		} else {
			house.player = null;
			house.name = null;
			house.stack = 0;
		}
		super.startRound();
	}

	// ---------------------------------------------------------------- round

	private boolean live(int seat) {
		return seats[seat].inRound && !folded[seat];
	}

	private boolean canAct(int seat) {
		return live(seat) && seats[seat].stack > 0;
	}

	private int nextFrom(int from, java.util.function.IntPredicate test) {
		int n = seats.length;
		for (int i = 1; i <= n; i++) {
			int seat = Math.floorMod(from + i, n);
			if (test.test(seat)) {
				return seat;
			}
		}
		return -1;
	}

	@Override
	protected void onStartRound() {
		shoe.shuffle();
		java.util.Arrays.fill(contributed, 0);
		java.util.Arrays.fill(streetBet, 0);
		java.util.Arrays.fill(folded, false);
		java.util.Arrays.fill(acted, false);
		street = 0;
		List<Integer> players = roundSeats();
		button = nextFrom(button, seat -> seats[seat].inRound);
		int sb = players.size() == 2 ? button : nextFrom(button, seat -> seats[seat].inRound);
		int bb = nextFrom(sb, seat -> seats[seat].inRound);
		put(sb, Math.min(smallBlind, seats[sb].stack));
		put(bb, Math.min(bigBlind, seats[bb].stack));
		currentBet = bigBlind;
		minRaise = bigBlind;
		log("gamblecraft.log.blinds", seats[sb].name, smallBlind, seats[bb].name, bigBlind);

		for (int seat : players) {
			hands.add(new TableHand("s" + seat, seat, seat == houseSeat ? "gamblecraft.hand.dealer" : "gamblecraft.hand.player", true));
		}
		board = new TableHand("board", COMMUNITY, "gamblecraft.hand.board", false);
		hands.add(board);
		for (int pass = 0; pass < 2; pass++) {
			int seat = button;
			for (int i = 0; i < players.size(); i++) {
				seat = nextFrom(seat, s -> seats[s].inRound);
				TableHand hole = hand("s" + seat);
				then(() -> {
					hole.add(shoe.draw(), true);
					if (hole.cards.size() == 2) {
						hole.value = Msg.of("gamblecraft.value.hole", hole.cards.get(0).card().shortText(), hole.cards.get(1).card().shortText());
					}
				});
			}
		}
		int first = players.size() == 2 ? sb : nextFrom(bb, s -> seats[s].inRound);
		then(() -> {
			phase = Phase.PLAYER_TURN;
			startBetting(first);
		});
	}

	private void put(int seat, long amount) {
		Seat s = seats[seat];
		s.stack -= amount;
		streetBet[seat] += amount;
		contributed[seat] += amount;
		if (seat != houseSeat) {
			s.committed += amount;
		}
	}

	private void startBetting(int firstCandidate) {
		int actors = 0;
		for (int seat = 0; seat < seats.length; seat++) {
			if (canAct(seat)) {
				actors++;
			}
		}
		boolean someoneOwes = false;
		for (int seat = 0; seat < seats.length; seat++) {
			if (canAct(seat) && streetBet[seat] < currentBet) {
				someoneOwes = true;
			}
		}
		if (actors < 2 && !someoneOwes) {
			then(this::nextStreet);
			return;
		}
		int seat = canAct(firstCandidate) ? firstCandidate : nextFrom(firstCandidate, this::canAct);
		setTurn(seat);
	}

	private void setTurn(int seat) {
		for (TableHand hand : hands) {
			hand.active = false;
		}
		activeSeat = seat;
		hand("s" + seat).active = true;
		if (seat == houseSeat) {
			then(this::houseAct);
		}
	}

	@Override
	public boolean awaitingInput() {
		return super.awaitingInput() && activeSeat != houseSeat;
	}

	private void houseAct() {
		HoldemBot.Decision decision = HoldemBot.decide(hand("s" + houseSeat).cardList(), board.cardList(),
				liveCount() - 1, toCall(houseSeat), pot(), seats[houseSeat].stack, minRaiseTotal(houseSeat), rng);
		apply(houseSeat, decision.act(), decision.amount());
	}

	private int liveCount() {
		int n = 0;
		for (int seat = 0; seat < seats.length; seat++) {
			if (live(seat)) {
				n++;
			}
		}
		return n;
	}

	private long toCall(int seat) {
		return Math.max(0, currentBet - streetBet[seat]);
	}

	private long minRaiseTotal(int seat) {
		return toCall(seat) + minRaise;
	}

	@Override
	protected List<ActionOption> legalActions(int seat) {
		return actionsFor(seat);
	}

	private List<ActionOption> actionsFor(int seat) {
		long call = toCall(seat);
		long stack = seats[seat].stack;
		List<ActionOption> options = new ArrayList<>();
		options.add(ActionOption.free(Act.FOLD));
		if (call == 0) {
			options.add(ActionOption.free(Act.CHECK));
		} else {
			options.add(ActionOption.costing(Act.CALL, Math.min(call, stack)));
		}
		if (stack > call) {
			long min = minRaiseTotal(seat);
			if (min < stack) {
				options.add(new ActionOption(Act.RAISE, min, stack));
			}
			options.add(ActionOption.costing(Act.ALL_IN, stack));
		}
		return options;
	}

	@Override
	public Act defaultAct(int seat) {
		return toCall(seat) == 0 ? Act.CHECK : Act.FOLD;
	}

	@Override
	protected void onLeaveMidRound(int seat) {
		if (activeSeat == seat && awaitingInput()) {
			act(seat, Act.FOLD, 0);
		} else {
			folded[seat] = true;
			hand("s" + seat).result = "fold";
			if (liveCount() == 1 && phase == Phase.PLAYER_TURN) {
				then(this::winByFold);
			}
		}
	}

	@Override
	protected void onAct(int seat, Act act, long cost, long amount) {
		apply(seat, act, cost);
	}

	/** Applies a decision; {@code amount} is the chips put in for CALL/RAISE/ALL_IN. */
	private void apply(int seat, Act act, long amount) {
		switch (act) {
			case FOLD -> {
				folded[seat] = true;
				hand("s" + seat).result = "fold";
			}
			case CHECK -> {
			}
			case CALL -> put(seat, Math.min(toCall(seat), seats[seat].stack));
			case RAISE, ALL_IN -> {
				long chips = act == Act.ALL_IN ? seats[seat].stack : Math.min(amount, seats[seat].stack);
				put(seat, chips);
				long raise = streetBet[seat] - currentBet;
				if (raise > 0) {
					if (raise >= minRaise) {
						minRaise = raise;
					}
					currentBet = streetBet[seat];
					java.util.Arrays.fill(acted, false);
				}
			}
			default -> throw new IllegalStateException(act.name());
		}
		acted[seat] = true;
		log("gamblecraft.log.holdem_" + act.key(), seats[seat].name, streetBet[seat]);
		if (liveCount() == 1) {
			then(this::winByFold);
			return;
		}
		if (streetComplete()) {
			then(this::nextStreet);
		} else {
			setTurn(nextFrom(seat, this::canAct));
		}
	}

	private boolean streetComplete() {
		for (int seat = 0; seat < seats.length; seat++) {
			if (canAct(seat) && (!acted[seat] || streetBet[seat] < currentBet)) {
				return false;
			}
		}
		return true;
	}

	private void nextStreet() {
		for (TableHand hand : hands) {
			hand.active = false;
		}
		activeSeat = -1;
		java.util.Arrays.fill(streetBet, 0);
		java.util.Arrays.fill(acted, false);
		currentBet = 0;
		minRaise = bigBlind;
		street++;
		if (street == 4) {
			then(this::showdown);
			return;
		}
		int cards = street == 1 ? 3 : 1;
		shoe.draw();
		for (int i = 0; i < cards; i++) {
			then(() -> {
				board.add(shoe.draw(), true);
				updateHandValues();
			});
		}
		then(() -> startBetting(nextFrom(button, this::canAct)));
	}

	private void updateHandValues() {
		if (board.cards.size() < 3) {
			return;
		}
		for (int seat : roundSeats()) {
			TableHand hole = hand("s" + seat);
			List<Card> all = new ArrayList<>(hole.cardList());
			all.addAll(board.cardList());
			hole.value = Msg.of("gamblecraft.poker." + PokerEvaluator.evaluate(all).category().key());
		}
	}

	private void winByFold() {
		int winner = nextFrom(-1, this::live);
		long pot = pot();
		seats[winner].stack += pot;
		seats[winner].lastPayout = pot;
		hand("s" + winner).result = "win";
		log("gamblecraft.log.holdem_win_fold", seats[winner].name, pot);
		endHand();
	}

	private void showdown() {
		showdown = true;
		List<Integer> players = roundSeats();
		HandValue[] values = new HandValue[seats.length];
		for (int seat : players) {
			if (live(seat)) {
				List<Card> all = new ArrayList<>(hand("s" + seat).cardList());
				all.addAll(board.cardList());
				values[seat] = PokerEvaluator.evaluate(all);
				hand("s" + seat).value = Msg.of("gamblecraft.poker." + values[seat].category().key());
			}
		}
		long[] won = new long[seats.length];
		List<Long> levels = new ArrayList<>();
		for (int seat : players) {
			if (contributed[seat] > 0 && !levels.contains(contributed[seat])) {
				levels.add(contributed[seat]);
			}
		}
		levels.sort(null);
		long previous = 0;
		for (long level : levels) {
			long part = 0;
			for (int seat : players) {
				part += Math.min(contributed[seat], level) - Math.min(contributed[seat], previous);
			}
			previous = level;
			List<Integer> winners = new ArrayList<>();
			HandValue best = null;
			for (int seat : players) {
				if (live(seat) && contributed[seat] >= level) {
					int cmp = best == null ? 1 : values[seat].compareTo(best);
					if (cmp > 0) {
						winners.clear();
						best = values[seat];
					}
					if (cmp >= 0) {
						winners.add(seat);
					}
				}
			}
			if (winners.isEmpty()) {
				// Everyone who reached this level folded: the chips go back to whoever put them in.
				for (int seat : players) {
					if (contributed[seat] >= level) {
						won[seat] += part;
						break;
					}
				}
				continue;
			}
			long share = part / winners.size();
			long odd = part % winners.size();
			for (int seat : winners) {
				won[seat] += share;
			}
			won[nextFrom(button, winners::contains)] += odd;
		}
		for (int seat : players) {
			seats[seat].stack += won[seat];
			seats[seat].lastPayout = won[seat];
			TableHand hand = hand("s" + seat);
			if (!folded[seat]) {
				hand.result = won[seat] > 0 ? "win" : "lose";
			}
			if (won[seat] > 0) {
				log("gamblecraft.log.holdem_win", seats[seat].name, won[seat], values[seat] != null ? values[seat].category().key() : "");
			}
		}
		endHand();
	}

	private void endHand() {
		for (TableHand hand : hands) {
			hand.active = false;
		}
		for (int seat : roundSeats()) {
			seats[seat].status = seats[seat].stack < bigBlind ? "busted" : null;
		}
		finishRound();
	}

	@Override
	protected long pot() {
		long pot = 0;
		for (long c : contributed) {
			pot += c;
		}
		return pot;
	}

	@Override
	protected void advance() {
	}
}
