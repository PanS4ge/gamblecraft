package pl.pansage.gamblecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import pl.pansage.gamblecraft.core.games.GameKind;
import pl.pansage.gamblecraft.game.Bank;
import pl.pansage.gamblecraft.game.TablePlacement;

/**
 * A casino croupier: one game, his own felt in front of him, chips only and the house's bottomless bank.
 * Everything else (dealing, gestures, the table itself) is the village dealer's.
 */
public class CasinoDealerEntity extends StreetDealerEntity {
	public static final long MIN_BET = 5;
	public static final long MAX_BET = 500;
	/** Limits at the VIP room's high-roller tables. */
	public static final long HIGH_MIN_BET = 100;
	public static final long HIGH_MAX_BET = 5000;

	private GameKind game = GameKind.BLACKJACK;
	private boolean highRoller;
	/** The felt in front of him, fixed on his first tick from where he faces. */
	private BlockPos felt;

	public CasinoDealerEntity(EntityType<? extends CasinoDealerEntity> type, Level level) {
		super(type, level);
	}

	public GameKind game() {
		return game;
	}

	public void setGame(GameKind game) {
		this.game = game;
	}

	public void setHighRoller(boolean highRoller) {
		this.highRoller = highRoller;
	}

	public long minBet() {
		return highRoller ? HIGH_MIN_BET : MIN_BET;
	}

	public long maxBet() {
		return highRoller ? HIGH_MAX_BET : MAX_BET;
	}

	/** A croupier does not wander off: he stays behind his felt. */
	@Override
	protected void addIdleGoals() {
		goalSelector.addGoal(5, new StayHomeGoal(1));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (felt == null && home() != null) {
			felt = home().relative(Direction.fromYRot(getYRot()));
		}
	}

	public BlockPos felt() {
		return felt;
	}

	@Override
	public Bank bank() {
		return new Bank() {
			@Override
			public double available() {
				return Double.MAX_VALUE;
			}

			@Override
			public void settle(double houseNet) {
				if (houseNet < -5) {
					gesture(GESTURE_SULK);
				} else if (houseNet > 5) {
					gesture(GESTURE_CHEER);
				}
			}
		};
	}

	/** Seats the player at his table, opening it on his felt if nobody is playing yet. */
	@Override
	public void openFor(ServerPlayer player) {
		if (table() != null) {
			table().join(player);
			return;
		}
		if (felt == null) {
			return;
		}
		if (TablePlacement.createCasinoTable(player, this, felt) != null) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.casino.dealer_welcome", Component.translatable("gamblecraft.game." + game.id())));
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("gamblecraft_game", game.id());
		output.putBoolean("gamblecraft_high_roller", highRoller);
		if (felt != null) {
			output.putLong("gamblecraft_felt", felt.asLong());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		try {
			game = GameKind.byId(input.getStringOr("gamblecraft_game", GameKind.BLACKJACK.id()));
		} catch (IllegalArgumentException e) {
			game = GameKind.BLACKJACK;
		}
		highRoller = input.getBooleanOr("gamblecraft_high_roller", false);
		long packed = input.getLongOr("gamblecraft_felt", Long.MIN_VALUE);
		felt = packed == Long.MIN_VALUE ? null : BlockPos.of(packed);
	}
}
