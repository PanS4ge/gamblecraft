package pl.pansage.gamblecraft.test.client;

import java.lang.reflect.Field;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import pl.pansage.gamblecraft.block.ArcadeBlock;
import pl.pansage.gamblecraft.block.TallMachineBlock;
import pl.pansage.gamblecraft.block.entity.ArcadeBlockEntity;
import pl.pansage.gamblecraft.block.entity.CrashPadBlockEntity;
import pl.pansage.gamblecraft.block.entity.PlinkoBlockEntity;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;
import pl.pansage.gamblecraft.client.screen.ArcadeScreen;
import pl.pansage.gamblecraft.client.screen.KioskScreen;
import pl.pansage.gamblecraft.core.casino.ArcadeMath.DiceBet;
import pl.pansage.gamblecraft.core.casino.ArcadeMath.WheelSymbol;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.registry.ModItems;

/** The wheel of fortune, the dice table, crash and plinko played in front of the camera, and the scratch kiosk. */
public class ArcadeClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof AgeGateScreen)) {
			context.clickScreenButton("gamblecraft.age_gate.accept");
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			BlockPos feet = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				player.getInventory().add(new ItemStack(ModItems.CHIPS[4], 8));
				return player.blockPosition();
			});
			BlockPos wheel = feet.offset(0, 0, 6);
			BlockPos dice = feet.offset(10, 0, 3);
			BlockPos crash = feet.offset(-10, 0, 6);
			BlockPos plinko = feet.offset(20, 0, 3);
			BlockPos kiosk = feet.offset(-20, 0, 3);
			world.getServer().runOnServer(server -> {
				ServerLevel level = player(server).level();
				for (BlockPos pos : new BlockPos[] {wheel, dice, crash}) {
					Block block = pos == wheel ? ModBlocks.FORTUNE_WHEEL : pos == dice ? ModBlocks.DICE_TABLE : ModBlocks.CRASH_PAD;
					level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(ArcadeBlock.FACING, Direction.NORTH));
				}
				BlockState board = ModBlocks.PLINKO.defaultBlockState().setValue(TallMachineBlock.FACING, Direction.NORTH);
				level.setBlock(plinko, board, 3);
				level.setBlock(plinko.above(), board.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER), 3);
				level.setBlockAndUpdate(kiosk, ModBlocks.SCRATCH_KIOSK.defaultBlockState().setValue(ArcadeBlock.FACING, Direction.NORTH));
			});

			// Plinko first: six balls on high risk, falling past the pegs and landing in their bins.
			view(world, plinko, 1.75, 0.25, 20);
			world.getServer().runOnServer(server -> board(server, plinko).cycleRisk(player(server)));
			context.waitTicks(20);
			context.takeScreenshot("plinko_board");
			for (int i = 0; i < 6; i++) {
				world.getServer().runOnServer(server -> board(server, plinko).drop(player(server)));
				context.waitTicks(5);
			}
			context.takeScreenshot("plinko_falling");
			context.waitTicks(30);
			context.takeScreenshot("plinko_landed");

			// The kiosk and its window.
			view(world, kiosk, 2.2, 0.2, 15);
			context.waitTicks(15);
			context.takeScreenshot("kiosk_block");
			context.setScreen(() -> new KioskScreen(kiosk));
			context.waitTicks(5);
			context.takeScreenshot("kiosk_screen");
			context.setScreen(() -> null);

			// The wheel and the dice table take bets at the same time, so their rounds run side by side.
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				game(server, wheel).bet(player, WheelSymbol.ONE.key(), 25);
				game(server, wheel).bet(player, WheelSymbol.SIX.key(), 25);
				game(server, wheel).bet(player, WheelSymbol.JOKER.key(), 5);
				for (DiceBet bet : DiceBet.values()) {
					game(server, dice).bet(player, bet.key(), bet == DiceBet.SEVEN ? 25 : 100);
				}
			});
			view(world, dice, 1.6, 0.6, 50);
			context.waitTicks(15);
			context.takeScreenshot("dice_bets");
			view(world, wheel, 5.0, 0, -12);
			context.waitTicks(10);
			context.takeScreenshot("wheel_betting");
			context.setScreen(() -> new ArcadeScreen(wheel));
			context.waitTicks(5);
			context.takeScreenshot("wheel_screen");
			context.setScreen(() -> null);
			view(world, dice, 1.6, 0.6, 50);
			waitFor(context, world, dice, phase -> phase == ArcadeBlockEntity.Phase.PLAYING);
			context.waitTicks(14);
			context.takeScreenshot("dice_rolling");
			waitFor(context, world, dice, phase -> phase == ArcadeBlockEntity.Phase.RESULT);
			context.waitTicks(4);
			context.takeScreenshot("dice_result");
			view(world, wheel, 5.0, 0, -12);
			context.waitTicks(10);
			context.takeScreenshot("wheel_spinning");
			waitFor(context, world, wheel, phase -> phase == ArcadeBlockEntity.Phase.RESULT);
			context.waitTicks(4);
			context.takeScreenshot("wheel_result");

			// Crash: a bet, the rocket on the pad, then in flight past x1.6 with the cash-out button live, then the bang.
			world.getServer().runOnServer(server -> game(server, crash).bet(player(server), CrashPadBlockEntity.ROCKET, 100));
			view(world, crash, 5.5, 0, -18);
			context.waitTicks(10);
			context.takeScreenshot("crash_betting");
			waitFor(context, world, crash, phase -> phase == ArcadeBlockEntity.Phase.PLAYING);
			world.getServer().runOnServer(server -> {
				try {
					Field field = CrashPadBlockEntity.class.getDeclaredField("crashAt");
					field.setAccessible(true);
					field.setInt(game(server, crash), 260);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			});
			context.waitTicks(120);
			context.takeScreenshot("crash_flying");
			context.setScreen(() -> new ArcadeScreen(crash));
			context.waitTicks(3);
			context.takeScreenshot("crash_screen");
			context.setScreen(() -> null);
			waitFor(context, world, crash, phase -> phase == ArcadeBlockEntity.Phase.RESULT);
			context.waitTicks(3);
			context.takeScreenshot("crash_boom");

			// The celebrations: a win, then a jackpot with confetti and glitter, as the winner sees them.
			view(world, plinko, 3.0, 0.2, 0);
			context.waitTicks(10);
			world.getServer().runOnServer(server -> pl.pansage.gamblecraft.game.Celebrate.win(player(server).level(), player(server), 120, 100,
					net.minecraft.world.phys.Vec3.atCenterOf(plinko.above()), false));
			context.waitTicks(14);
			context.takeScreenshot("celebrate_win");
			context.waitTicks(60);
			world.getServer().runOnServer(server -> pl.pansage.gamblecraft.game.Celebrate.win(player(server).level(), player(server), 12500, 100,
					net.minecraft.world.phys.Vec3.atCenterOf(plinko.above()), true));
			context.waitTicks(16);
			context.takeScreenshot("celebrate_jackpot");
		}
	}

	/** Stands the player {@code back} blocks in front of a machine facing north, eyes {@code up} above the floor plus 1.62. */
	private static void view(TestSingleplayerContext world, BlockPos machine, double back, double up, float pitch) {
		world.getServer().runOnServer(server -> player(server).connection.teleport(machine.getX() + 0.5, machine.getY() + up,
				machine.getZ() + 0.5 - back, 0, pitch));
	}

	private static void waitFor(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos, Predicate<ArcadeBlockEntity.Phase> done) {
		for (int i = 0; i < 200 && !done.test(world.getServer().computeOnServer(server -> game(server, pos).phase())); i++) {
			context.waitTicks(2);
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static ArcadeBlockEntity game(MinecraftServer server, BlockPos pos) {
		return (ArcadeBlockEntity) player(server).level().getBlockEntity(pos);
	}

	private static PlinkoBlockEntity board(MinecraftServer server, BlockPos pos) {
		return (PlinkoBlockEntity) player(server).level().getBlockEntity(pos);
	}
}
