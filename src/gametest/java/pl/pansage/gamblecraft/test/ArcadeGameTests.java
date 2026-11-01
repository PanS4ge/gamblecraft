package pl.pansage.gamblecraft.test;

import java.lang.reflect.Field;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import pl.pansage.gamblecraft.block.ArcadeBlock;
import pl.pansage.gamblecraft.block.ScratchKioskBlock;
import pl.pansage.gamblecraft.block.TallMachineBlock;
import pl.pansage.gamblecraft.block.entity.ArcadeBlockEntity;
import pl.pansage.gamblecraft.block.entity.CrashPadBlockEntity;
import pl.pansage.gamblecraft.block.entity.DiceTableBlockEntity;
import pl.pansage.gamblecraft.block.entity.FortuneWheelBlockEntity;
import pl.pansage.gamblecraft.block.entity.PlinkoBlockEntity;
import pl.pansage.gamblecraft.core.casino.ArcadeMath;
import pl.pansage.gamblecraft.core.casino.ArcadeMath.DiceBet;
import pl.pansage.gamblecraft.core.casino.ArcadeMath.WheelSymbol;
import pl.pansage.gamblecraft.entity.CashierEntity;
import pl.pansage.gamblecraft.entity.LotteryClerkEntity;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.item.ScratchData;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.registry.ModComponents;
import pl.pansage.gamblecraft.registry.ModEntities;
import pl.pansage.gamblecraft.registry.ModItems;

public class ArcadeGameTests {
	private static ServerPlayer gambler(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AgeGate.markAccepted(player);
		player.getInventory().add(new ItemStack(ModItems.CHIPS[3], 5));
		return player;
	}

	private static <T extends ArcadeBlockEntity> T machine(GameTestHelper helper, net.minecraft.world.level.block.Block block, Class<T> type) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState().setValue(ArcadeBlock.FACING, Direction.SOUTH));
		return type.cast(helper.getLevel().getBlockEntity(pos));
	}

	private static GameTestAssertException waiting(Object what) {
		return new GameTestAssertException(Component.literal(String.valueOf(what)), 0);
	}

	/** Chips on two symbols: the wheel stops on one segment and pays exactly that symbol's stake times its payout. */
	@GameTest(maxTicks = 600)
	public void wheelPaysTheSymbolItStopsOn(GameTestHelper helper) {
		ServerPlayer player = gambler(helper);
		FortuneWheelBlockEntity wheel = machine(helper, ModBlocks.FORTUNE_WHEEL, FortuneWheelBlockEntity.class);
		long before = ChipCurrency.INSTANCE.balance(player);
		wheel.bet(player, WheelSymbol.ONE.key(), 10);
		wheel.bet(player, WheelSymbol.JOKER.key(), 5);
		if (wheel.phase() != ArcadeBlockEntity.Phase.BETTING || ChipCurrency.INSTANCE.balance(player) != before - 15) {
			helper.fail("Bets were not taken");
			return;
		}
		helper.succeedWhen(() -> {
			if (wheel.phase() != ArcadeBlockEntity.Phase.RESULT) {
				throw waiting(wheel.phase());
			}
			WheelSymbol hit = wheel.symbol();
			long expected = before - 15 + (hit == WheelSymbol.ONE ? 10L * hit.payout : 0) + (hit == WheelSymbol.JOKER ? 5L * hit.payout : 0);
			if (ChipCurrency.INSTANCE.balance(player) != expected) {
				helper.fail("Wheel on " + hit + ": expected " + expected + " chips, got " + ChipCurrency.INSTANCE.balance(player));
			}
		});
	}

	/** A stake on every zone: each zone the roll satisfies is paid, the others are lost. */
	@GameTest(maxTicks = 500)
	public void diceTablePaysTheZonesTheRollHits(GameTestHelper helper) {
		ServerPlayer player = gambler(helper);
		DiceTableBlockEntity table = machine(helper, ModBlocks.DICE_TABLE, DiceTableBlockEntity.class);
		long before = ChipCurrency.INSTANCE.balance(player);
		for (DiceBet bet : DiceBet.values()) {
			table.bet(player, bet.key(), 20);
		}
		helper.succeedWhen(() -> {
			if (table.phase() != ArcadeBlockEntity.Phase.RESULT) {
				throw waiting(table.phase());
			}
			long expected = before - 80;
			for (DiceBet bet : DiceBet.values()) {
				if (bet.wins(table.a(), table.b())) {
					expected += ArcadeMath.payout(20, bet.payout);
				}
			}
			if (table.a() < 1 || table.a() > 6 || table.b() < 1 || table.b() > 6) {
				helper.fail("Dice show " + table.a() + " and " + table.b());
			} else if (ChipCurrency.INSTANCE.balance(player) != expected) {
				helper.fail("Rolled " + table.a() + "+" + table.b() + ": expected " + expected + " chips, got " + ChipCurrency.INSTANCE.balance(player));
			}
		});
	}

	private static void fixCrashPoint(CrashPadBlockEntity pad, int timesHundred) {
		try {
			Field field = CrashPadBlockEntity.class.getDeclaredField("crashAt");
			field.setAccessible(true);
			field.setInt(pad, timesHundred);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Two players on the rocket (fixed to explode at x3): one cashes out past x1.5 and is paid at that multiplier, the
	 * other rides it into the explosion. Clients are not told the explosion point while the rocket flies.
	 */
	@GameTest(maxTicks = 700)
	public void crashPaysWhoCashesOutInTime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer careful = gambler(helper);
		ServerPlayer greedy = gambler(helper);
		CrashPadBlockEntity pad = machine(helper, ModBlocks.CRASH_PAD, CrashPadBlockEntity.class);
		long carefulBefore = ChipCurrency.INSTANCE.balance(careful);
		long greedyBefore = ChipCurrency.INSTANCE.balance(greedy);
		pad.bet(careful, CrashPadBlockEntity.ROCKET, 100);
		pad.bet(greedy, CrashPadBlockEntity.ROCKET, 100);
		boolean[] state = new boolean[2];
		helper.onEachTick(() -> {
			if (pad.phase() == ArcadeBlockEntity.Phase.PLAYING) {
				if (!state[0]) {
					state[0] = true;
					fixCrashPoint(pad, 300);
				}
				if (pad.getUpdateTag(level.registryAccess()).contains("crash_at")) {
					helper.fail("The explosion point was sent to clients mid-flight");
				}
				if (!state[1] && CrashPadBlockEntity.multiplier(level.getGameTime() - pad.phaseStart()) >= 1.5) {
					state[1] = true;
					pad.cashOut(careful);
				}
			}
		});
		helper.succeedWhen(() -> {
			if (pad.phase() != ArcadeBlockEntity.Phase.RESULT) {
				throw waiting(pad.phase() + " x" + CrashPadBlockEntity.multiplier(level.getGameTime() - pad.phaseStart()));
			}
			int at = pad.stakes().stream().filter(s -> s.player().equals(careful.getUUID())).findFirst().orElseThrow().cashedOut();
			if (at < 150 || at >= 300) {
				helper.fail("Cashed out at " + at);
				return;
			}
			if (ChipCurrency.INSTANCE.balance(careful) != carefulBefore - 100 + at) {
				helper.fail("Careful player has " + ChipCurrency.INSTANCE.balance(careful) + ", expected " + (carefulBefore - 100 + at));
			} else if (ChipCurrency.INSTANCE.balance(greedy) != greedyBefore - 100) {
				helper.fail("Greedy player was paid although the rocket blew up");
			} else if (!pad.getUpdateTag(level.registryAccess()).contains("crash_at") || Math.abs(pad.crashedAt() - 3.0) > 1e-9) {
				helper.fail("The explosion point is not shown after the crash");
			}
		});
	}

	/** A dropped ball lands in the bin its bounces lead to and is paid that bin's multiplier. */
	@GameTest(maxTicks = 200)
	public void plinkoPaysTheBinTheBallLandsIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos lower = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockState state = ModBlocks.PLINKO.defaultBlockState().setValue(TallMachineBlock.FACING, Direction.SOUTH);
		level.setBlock(lower, state, 3);
		level.setBlock(lower.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER), 3);
		ServerPlayer player = gambler(helper);
		PlinkoBlockEntity board = (PlinkoBlockEntity) level.getBlockEntity(lower);
		long before = ChipCurrency.INSTANCE.balance(player);
		board.drop(player);
		if (board.balls().size() != 1 || ChipCurrency.INSTANCE.balance(player) != before - board.bet()) {
			helper.fail("Ball not dropped or bet not taken");
			return;
		}
		PlinkoBlockEntity.Ball ball = board.balls().getFirst();
		if (Integer.bitCount(ball.path()) != ball.bin()) {
			helper.fail("Bin " + ball.bin() + " does not match the path " + Integer.toBinaryString(ball.path()));
			return;
		}
		long expected = before - ball.bet() + ArcadeMath.payout(ball.bet(), ball.riskLevel().bins[ball.bin()]);
		helper.succeedWhen(() -> {
			if (!board.balls().getFirst().paid()) {
				throw waiting("falling");
			}
			if (ChipCurrency.INSTANCE.balance(player) != expected) {
				helper.fail("Ball in bin " + ball.bin() + ": expected " + expected + " chips, got " + ChipCurrency.INSTANCE.balance(player));
			}
		});
	}

	/** The kiosk sells casino cards for chips; the cashier pays a scratched winner in chips, the village clerk will not. */
	@GameTest(maxTicks = 20)
	public void kioskCardIsPaidByTheCashier(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos kiosk = helper.absolutePos(new BlockPos(1, 1, 1));
		level.setBlockAndUpdate(kiosk, ModBlocks.SCRATCH_KIOSK.defaultBlockState());
		ServerPlayer player = gambler(helper);
		player.snapTo(kiosk.getX() + 0.5, kiosk.getY(), kiosk.getZ() + 2.5, 0, 0);
		long before = ChipCurrency.INSTANCE.balance(player);
		ScratchKioskBlock.buy(player, kiosk, 1);
		ScratchCardItem.Tier tier = ScratchCardItem.Tier.SILVER;
		ItemStack card = ItemStack.EMPTY;
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(ModItems.SCRATCH_CARD_SILVER)) {
				card = stack;
			}
		}
		if (card.isEmpty() || ChipCurrency.INSTANCE.balance(player) != before - tier.chips() || !ScratchCardItem.data(card).casino()) {
			helper.fail("Kiosk did not sell a casino card for " + tier.chips() + " chips");
			return;
		}
		ScratchData data = ScratchCardItem.data(card);
		card.set(ModComponents.SCRATCH, new ScratchData(data.seed(), data.bell(), data.name(), true, 3, false));

		LotteryClerkEntity clerk = ModEntities.LOTTERY_CLERK.create(level, EntitySpawnReason.TRIGGERED);
		clerk.snapTo(player.getX() + 1, player.getY(), player.getZ(), 0, 0);
		level.addFreshEntity(clerk);
		clerk.redeem(player, card);
		if (card.isEmpty() || player.getInventory().countItem(Items.EMERALD) != 0) {
			helper.fail("A village clerk paid a casino card");
			return;
		}
		CashierEntity cashier = ModEntities.CASHIER.create(level, EntitySpawnReason.TRIGGERED);
		cashier.snapTo(player.getX() - 1, player.getY(), player.getZ(), 0, 0);
		level.addFreshEntity(cashier);
		long scratched = ChipCurrency.INSTANCE.balance(player);
		cashier.redeem(player, card);
		if (!card.isEmpty() || ChipCurrency.INSTANCE.balance(player) != scratched + 3L * tier.chips()) {
			helper.fail("Cashier paid " + (ChipCurrency.INSTANCE.balance(player) - scratched) + " instead of " + 3 * tier.chips());
			return;
		}
		helper.succeed();
	}
}
