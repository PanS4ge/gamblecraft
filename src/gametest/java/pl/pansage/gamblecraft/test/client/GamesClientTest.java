package pl.pansage.gamblecraft.test.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import pl.pansage.gamblecraft.block.RouletteWheelBlock;
import pl.pansage.gamblecraft.block.SlotMachineBlock;
import pl.pansage.gamblecraft.block.entity.RouletteBlockEntity;
import pl.pansage.gamblecraft.block.entity.SlotMachineBlockEntity;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;
import pl.pansage.gamblecraft.core.casino.Roulette;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.registry.ModItems;

/** A slot machine spinning and paying, and a roulette table assembling, taking bets, spinning and paying. */
public class GamesClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof AgeGateScreen)) {
			context.clickScreenButton("gamblecraft.age_gate.accept");
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			BlockPos[] spots = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = player.level();
				BlockPos feet = player.blockPosition();
				BlockPos machine = feet.offset(0, 0, 3);
				BlockState state = ModBlocks.SLOT_MACHINE.defaultBlockState().setValue(SlotMachineBlock.FACING, Direction.NORTH);
				level.setBlock(machine, state, 3);
				level.setBlock(machine.above(), state.setValue(SlotMachineBlock.HALF, DoubleBlockHalf.UPPER), 3);
				player.getInventory().add(new ItemStack(ModItems.CHIPS[4], 4));
				player.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 1.4, 0, 8);
				return new BlockPos[] {feet, machine};
			});
			BlockPos feet = spots[0];
			BlockPos machinePos = spots[1];
			context.waitTicks(20);
			world.getServer().runOnServer(server -> machine(server, machinePos).use(player(server), false));
			context.waitTicks(18);
			context.takeScreenshot("slots_spinning");
			waitStopped(context, world, machinePos);
			context.takeScreenshot("slots_stopped");
			for (int i = 0; i < 12 && world.getServer().computeOnServer(server -> machine(server, machinePos).lastWin()) == 0; i++) {
				world.getServer().runOnServer(server -> machine(server, machinePos).use(player(server), false));
				waitStopped(context, world, machinePos);
			}
			context.waitTicks(2);
			context.takeScreenshot("slots_win");

			// A roulette table put together in front of the player; they stand on its betting side.
			BlockPos wheel = feet.offset(2, 0, 7);
			world.getServer().runOnServer(server -> {
				ServerLevel level = player(server).level();
				level.setBlockAndUpdate(wheel, ModBlocks.ROULETTE_WHEEL.defaultBlockState().setValue(RouletteWheelBlock.FACING, Direction.WEST));
				for (int i = 1; i <= 3; i++) {
					level.setBlockAndUpdate(wheel.west(i), ModBlocks.ROULETTE_FELT.defaultBlockState());
				}
				player(server).connection.teleport(wheel.getX() - 1.0, wheel.getY() + 0.6, wheel.getZ() - 1.6, 0, 48);
			});
			context.waitTicks(10);
			context.takeScreenshot("roulette_unroll");
			context.waitTicks(30);
			context.takeScreenshot("roulette_board");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				RouletteBlockEntity table = roulette(server, wheel);
				player.getInventory().add(new ItemStack(ModItems.CHIPS[3], 5));
				Roulette.Bet[] bets = {Roulette.red(), Roulette.straight(17), Roulette.split(17, 20), Roulette.dozen(0), Roulette.columnBet(1),
						Roulette.corner(4, 0), Roulette.odd(), Roulette.straight(0)};
				int[] stakes = {100, 25, 5, 100, 25, 5, 25, 5};
				for (int i = 0; i < bets.length; i++) {
					ChipCurrency.INSTANCE.withdraw(player, stakes[i]);
					table.placeBet(player, bets[i], stakes[i]);
				}
			});
			context.waitTicks(10);
			context.takeScreenshot("roulette_bets");
			for (int i = 0; i < 60 && world.getServer().computeOnServer(server -> roulette(server, wheel).phase()) != RouletteBlockEntity.Phase.SPINNING; i++) {
				context.waitTicks(10);
			}
			context.waitTicks(70);
			context.takeScreenshot("roulette_spinning");
			for (int i = 0; i < 30 && world.getServer().computeOnServer(server -> roulette(server, wheel).phase()) != RouletteBlockEntity.Phase.RESULT; i++) {
				context.waitTicks(5);
			}
			context.waitTicks(12);
			context.takeScreenshot("roulette_result");
		}
	}

	private static void waitStopped(ClientGameTestContext context, TestSingleplayerContext world, BlockPos machinePos) {
		for (int i = 0; i < 40 && world.getServer().computeOnServer(server -> machine(server, machinePos).spinning()); i++) {
			context.waitTicks(5);
		}
		context.waitTicks(3);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static SlotMachineBlockEntity machine(MinecraftServer server, BlockPos pos) {
		return (SlotMachineBlockEntity) player(server).level().getBlockEntity(pos);
	}

	private static RouletteBlockEntity roulette(MinecraftServer server, BlockPos pos) {
		return (RouletteBlockEntity) player(server).level().getBlockEntity(pos);
	}
}
