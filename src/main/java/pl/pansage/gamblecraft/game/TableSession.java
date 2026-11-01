package pl.pansage.gamblecraft.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import pl.pansage.gamblecraft.core.games.GameKind;
import pl.pansage.gamblecraft.core.games.HoldemGame;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.IllegalActionException;
import pl.pansage.gamblecraft.core.table.TableTypes.Settlement;
import pl.pansage.gamblecraft.core.table.TableTypes.TableSnapshot;
import pl.pansage.gamblecraft.core.table.TableTypes.Wager;

/**
 * Server side of one table: wraps a core {@link CardGame}, takes stakes from players before handing them to the game,
 * pays settlements, drives the game one atomic step at a time, and handles decision timeouts and auto-start.
 * Everything a player put in is tracked in {@link #escrow} so it can be refunded if the table disappears mid-round.
 */
public final class TableSession {
	public static final int STEP_TICKS = 7;
	public static final int DECISION_TICKS = 20 * 30;
	public static final int START_TICKS = 20 * 5;
	public static final int QUICK_START_TICKS = 20;
	public static final int MAX_DISTANCE = 10;

	private final GameKind kind;
	private final CardGame game;
	private final Currency currency;
	private final Bank bank;
	private final long minBet;
	private final long maxBetCap;
	private final Map<UUID, Long> escrow = new HashMap<>();
	private int stepTimer;
	private int decisionTimer;
	private int startTimer = -1;
	private int version;
	private boolean closed;

	public TableSession(GameKind kind, boolean casino, Currency currency, Bank bank, long minBet, long maxBetCap, RandomGenerator rng) {
		this(kind, kind.create(casino, minBet, rng), currency, bank, minBet, maxBetCap);
	}

	/** A table around a game set up by the caller (the boss's duel). */
	public TableSession(GameKind kind, CardGame game, Currency currency, Bank bank, long minBet, long maxBetCap) {
		this.kind = kind;
		this.game = game;
		this.currency = currency;
		this.bank = bank;
		this.minBet = minBet;
		this.maxBetCap = maxBetCap;
	}

	public GameKind kind() {
		return kind;
	}

	public CardGame game() {
		return game;
	}

	public Currency currency() {
		return currency;
	}

	public long minBet() {
		return minBet;
	}

	public int version() {
		return version;
	}

	public boolean isEmpty() {
		return game.isEmpty();
	}

	/** Ticks left on the current decision or start countdown, for the client's timer bar. */
	public int ticksLeft() {
		return game.awaitingInput() ? decisionTimer : Math.max(startTimer, 0);
	}

	/** How much the bank can risk on one bet, given the worst payout of this game. */
	public long maxBet() {
		double available = bank.available();
		if (available >= Double.MAX_VALUE / 2) {
			return maxBetCap;
		}
		int risk = switch (kind) {
			case BLACKJACK, OCZKO, WAR -> 4;
			case BACCARAT -> 9;
			case THREE_CARD_POKER -> 8;
			case VIDEO_POKER, HI_LO -> 12;
			case HOLDEM -> 2;
		};
		long byBank = (long) Math.floor(available / currency.unitValue() / risk);
		return Math.max(0, Math.min(maxBetCap, byBank));
	}

	private void changed() {
		version++;
	}

	// ---------------------------------------------------------------- player requests (null = ok, else the reason)

	public Component sit(ServerPlayer player) {
		if (game.seatOf(player.getUUID()) >= 0) {
			return null;
		}
		if (game.sit(player.getUUID(), player.getName().getString()) < 0) {
			return Component.translatable("gamblecraft.table.full");
		}
		changed();
		return null;
	}

	public Component leave(ServerPlayer player) {
		return leave(player.getUUID(), player);
	}

	private Component leave(UUID id, ServerPlayer online) {
		if (game.seatOf(id) < 0) {
			return null;
		}
		long refund = game.leave(id);
		if (refund > 0) {
			pay(id, online, refund);
		}
		if (game.seatOf(id) < 0) {
			long put = escrow.getOrDefault(id, 0L);
			if (game.usesStacks()) {
				bank.settle((put - refund) * currency.unitValue());
			}
			escrow.remove(id);
		}
		changed();
		return null;
	}

	public Component bet(ServerPlayer player, Wager wager) {
		int seat = game.seatOf(player.getUUID());
		if (seat < 0 || game.usesStacks()) {
			return Component.translatable("gamblecraft.table.not_seated");
		}
		if (!game.acceptsWagers()) {
			return Component.translatable("gamblecraft.table.round_running");
		}
		long max = maxBet();
		if (max < minBet) {
			return Component.translatable("gamblecraft.table.dealer_broke");
		}
		if (wager.amount() < minBet || wager.amount() > max || wager.side() < 0 || wager.side() > max || !game.isValidWager(wager)) {
			return Component.translatable("gamblecraft.table.bet_limits", minBet, max);
		}
		long previous = game.takeBackWager(seat);
		if (previous > 0) {
			currency.deposit(player, previous);
			escrow.merge(player.getUUID(), -previous, Long::sum);
		}
		if (!currency.withdraw(player, wager.total())) {
			return Component.translatable("gamblecraft.table.cannot_afford", currency.name());
		}
		escrow.merge(player.getUUID(), wager.total(), Long::sum);
		game.placeWager(seat, wager);
		scheduleStart();
		changed();
		return null;
	}

	public Component clearBet(ServerPlayer player) {
		int seat = game.seatOf(player.getUUID());
		if (seat < 0) {
			return null;
		}
		long back = game.takeBackWager(seat);
		if (back > 0) {
			currency.deposit(player, back);
			escrow.merge(player.getUUID(), -back, Long::sum);
			changed();
		}
		return null;
	}

	public Component buyIn(ServerPlayer player, long amount) {
		int seat = game.seatOf(player.getUUID());
		if (!(game instanceof HoldemGame holdem) || seat < 0) {
			return Component.translatable("gamblecraft.table.not_seated");
		}
		if (!game.acceptsWagers()) {
			return Component.translatable("gamblecraft.table.round_running");
		}
		long room = holdem.maxBuyIn() - holdem.stackOf(seat);
		if (amount < 1 || amount > room) {
			return Component.translatable("gamblecraft.table.bet_limits", 1, Math.max(room, 0));
		}
		if (!currency.withdraw(player, amount)) {
			return Component.translatable("gamblecraft.table.cannot_afford", currency.name());
		}
		escrow.merge(player.getUUID(), amount, Long::sum);
		if (currency == ChipCurrency.INSTANCE) {
			PlayerLedger.get(player.level().getServer()).staked(player.getUUID(), amount);
		}
		holdem.buyIn(seat, amount);
		scheduleStart();
		changed();
		return null;
	}

	public Component act(ServerPlayer player, Act act, long amount) {
		int seat = game.seatOf(player.getUUID());
		if (seat < 0 || !game.awaitingInput() || game.activeSeat() != seat) {
			return Component.translatable("gamblecraft.table.not_your_turn");
		}
		long cost = game.costOf(seat, act, amount);
		if (cost < 0) {
			return Component.translatable("gamblecraft.table.not_allowed");
		}
		boolean fromInventory = !game.usesStacks() && cost > 0;
		if (fromInventory && !currency.withdraw(player, cost)) {
			return Component.translatable("gamblecraft.table.cannot_afford", currency.name());
		}
		try {
			game.act(seat, act, amount);
		} catch (IllegalActionException e) {
			if (fromInventory) {
				currency.deposit(player, cost);
			}
			return Component.translatable("gamblecraft.table.not_allowed");
		}
		if (fromInventory) {
			escrow.merge(player.getUUID(), cost, Long::sum);
		}
		decisionTimer = DECISION_TICKS;
		stepTimer = STEP_TICKS;
		changed();
		return null;
	}

	private void scheduleStart() {
		if (game.canStart() && startTimer < 0) {
			startTimer = kind == GameKind.HI_LO ? QUICK_START_TICKS : START_TICKS;
		}
		boolean everyoneReady = true;
		for (UUID id : game.players()) {
			int seat = game.seatOf(id);
			everyoneReady &= game.usesStacks() || game.wagerOf(seat) != null;
		}
		if (everyoneReady && startTimer > QUICK_START_TICKS && !game.usesStacks()) {
			startTimer = QUICK_START_TICKS;
		}
	}

	// ---------------------------------------------------------------- ticking

	/** Advances timers and the game. Returns false once the table should be removed. */
	public boolean tick(MinecraftServer server, net.minecraft.world.phys.Vec3 tablePos) {
		if (closed) {
			return false;
		}
		for (UUID id : game.players()) {
			if (HoldemGame.HOUSE.equals(id)) {
				// the house's own seat (the dealer's, the boss's) is never a player who walked off
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null || player.isRemoved() || player.position().distanceTo(tablePos) > MAX_DISTANCE) {
				leave(id, player);
			}
		}
		if (game.isRoundRunning()) {
			if (game.awaitingInput()) {
				if (--decisionTimer <= 0) {
					game.timeout();
					stepTimer = STEP_TICKS;
					changed();
				}
			} else if (game.needsStep() && --stepTimer <= 0) {
				game.step();
				stepTimer = STEP_TICKS;
				decisionTimer = DECISION_TICKS;
				changed();
			}
		} else if (startTimer >= 0) {
			if (!game.canStart()) {
				startTimer = -1;
			} else if (--startTimer <= 0) {
				startTimer = -1;
				game.startRound();
				stepTimer = 2;
				decisionTimer = DECISION_TICKS;
				changed();
			}
		} else if (game.canStart() && game.usesStacks()) {
			startTimer = START_TICKS;
		}
		List<Settlement> settlements = game.drainSettlements();
		if (!settlements.isEmpty()) {
			for (Settlement s : settlements) {
				settle(server, s);
			}
			changed();
		}
		return !game.isEmpty();
	}

	private void settle(MinecraftServer server, Settlement s) {
		if (s.player() == null || HoldemGame.HOUSE.equals(s.player())) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(s.player());
		if (s.payout() > 0) {
			pay(s.player(), player, s.payout());
		}
		if (currency == ChipCurrency.INSTANCE) {
			PlayerLedger ledger = PlayerLedger.get(server);
			if (!game.usesStacks()) {
				ledger.staked(s.player(), s.committed());
			}
			if (!game.usesStacks() || "cash_out".equals(s.outcome())) {
				ledger.won(s.player(), s.payout());
			}
		}
		long put = escrow.getOrDefault(s.player(), 0L);
		if (game.usesStacks()) {
			if ("cash_out".equals(s.outcome())) {
				bank.settle((put - s.payout()) * currency.unitValue());
				escrow.remove(s.player());
			}
		} else {
			long committed = s.committed();
			bank.settle((committed - s.payout()) * currency.unitValue());
			long remaining = put - committed;
			if (remaining > 0) {
				escrow.put(s.player(), remaining);
			} else {
				escrow.remove(s.player());
			}
		}
	}

	private void pay(UUID id, ServerPlayer online, long amount) {
		if (online != null && !online.isRemoved()) {
			currency.deposit(online, amount);
		} else {
			mailbox.accept(id, currency.asStacks(amount));
		}
	}

	private java.util.function.BiConsumer<UUID, List<net.minecraft.world.item.ItemStack>> mailbox = (id, stacks) -> {
	};

	public void setMailbox(java.util.function.BiConsumer<UUID, List<net.minecraft.world.item.ItemStack>> mailbox) {
		this.mailbox = mailbox;
	}

	/** Refunds everything still on the table and stops the game. */
	public void close(MinecraftServer server) {
		if (closed) {
			return;
		}
		closed = true;
		for (Map.Entry<UUID, Long> entry : escrow.entrySet()) {
			if (entry.getValue() > 0) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				long refund = entry.getValue();
				if (game.usesStacks()) {
					int seat = game.seatOf(entry.getKey());
					refund = seat >= 0 ? ((HoldemGame) game).stackOf(seat) : 0;
				}
				if (refund > 0) {
					pay(entry.getKey(), player, refund);
				}
			}
		}
		escrow.clear();
	}

	public TableSnapshot snapshotFor(UUID viewer) {
		return game.snapshot(viewer);
	}
}
