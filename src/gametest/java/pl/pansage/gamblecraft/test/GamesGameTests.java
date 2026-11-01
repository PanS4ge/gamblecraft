package pl.pansage.gamblecraft.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import pl.pansage.gamblecraft.block.RouletteFeltBlock;
import pl.pansage.gamblecraft.block.RouletteWheelBlock;
import pl.pansage.gamblecraft.block.SlotMachineBlock;
import pl.pansage.gamblecraft.block.entity.RouletteBlockEntity;
import pl.pansage.gamblecraft.block.entity.SlotMachineBlockEntity;
import pl.pansage.gamblecraft.core.casino.Roulette;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.JackpotData;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.registry.ModItems;

public class GamesGameTests {
	private static ServerPlayer gambler(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AgeGate.markAccepted(player);
		player.getInventory().add(new ItemStack(ModItems.CHIPS[3], 2));
		return player;
	}

	/** A spin takes the bet and pays exactly what its reels show. */
	@GameTest(maxTicks = 200)
	public void slotMachineSpinsAndPays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos lower = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockState state = ModBlocks.SLOT_MACHINE.defaultBlockState().setValue(SlotMachineBlock.FACING, Direction.SOUTH);
		level.setBlock(lower, state, 3);
		level.setBlock(lower.above(), state.setValue(SlotMachineBlock.HALF, DoubleBlockHalf.UPPER), 3);
		ServerPlayer player = gambler(helper);
		SlotMachineBlockEntity machine = (SlotMachineBlockEntity) level.getBlockEntity(lower);
		long before = ChipCurrency.INSTANCE.balance(player);
		machine.use(player, false);
		if (!machine.spinning() || ChipCurrency.INSTANCE.balance(player) != before - machine.bet()) {
			helper.fail("Spin did not start or did not take the bet");
			return;
		}
		helper.succeedWhen(() -> {
			if (machine.spinning()) {
				throw new GameTestAssertException(Component.literal("still spinning"), 0);
			}
			long expected = before - machine.bet() + machine.lastWin();
			if (ChipCurrency.INSTANCE.balance(player) != expected) {
				helper.fail("Expected " + expected + " chips after the spin, got " + ChipCurrency.INSTANCE.balance(player));
			}
		});
	}

	/** Every stake feeds the pot by 1%; a jackpot empties it back to the seed. */
	@GameTest(maxTicks = 20)
	public void jackpotGrowsAndResets(GameTestHelper helper) {
		JackpotData pots = JackpotData.get(helper.getLevel().getServer());
		String key = "test_" + helper.absolutePos(BlockPos.ZERO).asLong();
		for (int i = 0; i < 1000; i++) {
			pots.feed(key, 100);
		}
		if (pots.chips(key) != JackpotData.SEED + 1000) {
			helper.fail("Pot is " + pots.chips(key));
			return;
		}
		if (pots.win(key) != JackpotData.SEED + 1000 || pots.chips(key) != JackpotData.SEED) {
			helper.fail("Jackpot did not pay the pot and restart at the seed");
			return;
		}
		helper.succeed();
	}

	private static BlockPos table(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos wheel = helper.absolutePos(new BlockPos(1, 1, 1));
		level.setBlockAndUpdate(wheel, ModBlocks.ROULETTE_WHEEL.defaultBlockState().setValue(RouletteWheelBlock.FACING, Direction.EAST));
		for (int i = 1; i <= 3; i++) {
			level.setBlockAndUpdate(wheel.east(i), ModBlocks.ROULETTE_FELT.defaultBlockState());
		}
		return wheel;
	}

	/** Three felts behind a wheel make a table; a bet on red is paid 2:1 exactly when the ball lands on red. */
	@GameTest(maxTicks = 800)
	public void rouletteRoundPaysRed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos wheel = table(helper);
		if (!level.getBlockState(wheel).getValue(RouletteWheelBlock.ASSEMBLED) || level.getBlockState(wheel.east(3)).getValue(RouletteFeltBlock.PART) != 3) {
			helper.fail("Table did not assemble");
			return;
		}
		ServerPlayer player = gambler(helper);
		RouletteBlockEntity roulette = (RouletteBlockEntity) level.getBlockEntity(wheel);
		long before = ChipCurrency.INSTANCE.balance(player);
		ChipCurrency.INSTANCE.withdraw(player, 25);
		if (!roulette.placeBet(player, Roulette.red(), 25) || roulette.phase() != RouletteBlockEntity.Phase.BETTING) {
			helper.fail("Bet refused");
			return;
		}
		helper.succeedWhen(() -> {
			if (roulette.phase() != RouletteBlockEntity.Phase.RESULT) {
				throw new GameTestAssertException(Component.literal("phase " + roulette.phase()), 0);
			}
			long expected = before - 25 + (Roulette.isRed(roulette.result()) ? 50 : 0);
			if (ChipCurrency.INSTANCE.balance(player) != expected) {
				helper.fail("Ball on " + roulette.result() + ": expected " + expected + " chips, got " + ChipCurrency.INSTANCE.balance(player));
			}
		});
	}

	/** Taking a felt away takes the table apart and gives the chips on it back. */
	@GameTest(maxTicks = 40)
	public void rouletteComesApartAndRefunds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos wheel = table(helper);
		ServerPlayer player = gambler(helper);
		RouletteBlockEntity roulette = (RouletteBlockEntity) level.getBlockEntity(wheel);
		long before = ChipCurrency.INSTANCE.balance(player);
		ChipCurrency.INSTANCE.withdraw(player, 30);
		roulette.placeBet(player, Roulette.straight(17), 25);
		roulette.placeBet(player, Roulette.dozen(2), 5);
		level.removeBlock(wheel.east(2), false);
		if (level.getBlockState(wheel).getValue(RouletteWheelBlock.ASSEMBLED) || level.getBlockState(wheel.east(1)).getValue(RouletteFeltBlock.PART) != 0) {
			helper.fail("Table stayed together without its middle felt");
			return;
		}
		if (ChipCurrency.INSTANCE.balance(player) != before || !roulette.bets().isEmpty()) {
			helper.fail("Chips were not given back: " + ChipCurrency.INSTANCE.balance(player) + " of " + before);
			return;
		}
		helper.succeed();
	}
}
