package pl.pansage.gamblecraft.test.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.client.config.ClientConfig;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;
import pl.pansage.gamblecraft.client.screen.ExchangeScreen;
import pl.pansage.gamblecraft.client.screen.ScratchScreen;
import pl.pansage.gamblecraft.entity.LotteryClerkEntity;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.item.ScratchData;
import pl.pansage.gamblecraft.registry.ModItems;

/** Village exchange end to end: the building, scratching a card with the mouse, redeeming it, the exchange screen. */
public class ExchangeClientTest implements FabricClientGameTest {
	private static final int LEFT_BUTTON = 0;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof AgeGateScreen)) {
			context.clickScreenButton("gamblecraft.age_gate.accept");
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			// The building south of the player with its door facing them, a bell beside it, a farmer for the goods list.
			long[] bell = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				var level = player.level();
				BlockPos feet = player.blockPosition();
				BlockPos at = feet.offset(3, -1, 5);
				server.getStructureTemplateManager().get(GambleCraft.id("village/exchange_plains")).orElseThrow()
						.placeInWorld(level, at, at, new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90), level.getRandom(), 2);
				BlockPos bellPos = feet.offset(-5, 0, 3);
				level.setBlock(bellPos, Blocks.BELL.defaultBlockState(), 3);
				Villager farmer = new Villager(EntityTypes.VILLAGER, level);
				farmer.setVillagerData(farmer.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER).withLevel(2));
				farmer.snapTo(feet.getX() + 3.5, feet.getY(), feet.getZ() + 2.5, 180, 0);
				level.addFreshEntity(farmer);
				player.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0, 12);
				return new long[] {bellPos.asLong()};
			});
			context.waitTicks(40);
			context.takeScreenshot("exchange_building");

			// Step inside, in front of the counter, with a winning gold card from this village.
			int multiplier = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				var level = player.level();
				BlockPos feet = player.blockPosition();
				player.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 7.5, 0, 20);
				BlockPos bellPos = BlockPos.of(bell[0]);
				long seed = 1;
				while (ScratchCardItem.outcome(level, ScratchCardItem.data(ScratchCardItem.issued(ModItems.SCRATCH_CARD_GOLD, bellPos, seed))).multiplier() <= 0) {
					seed++;
				}
				ItemStack card = ScratchCardItem.issued(ModItems.SCRATCH_CARD_GOLD, bellPos, seed);
				player.setItemInHand(InteractionHand.MAIN_HAND, card);
				return ScratchCardItem.outcome(level, ScratchCardItem.data(card)).multiplier();
			});
			context.waitTicks(20);
			context.takeScreenshot("exchange_counter");

			// This test covers the panel view; ScratchBlockClientTest covers scratching on the block.
			context.runOnClient(client -> ClientConfig.get().scratchPanel = true);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
			});
			context.waitForScreen(ScratchScreen.class);
			context.waitTicks(2);
			context.takeScreenshot("scratch_fresh");

			// Rub the first row in a zigzag and one stroke across the second with the real mouse.
			int[] grid = context.computeOnClient(client -> ((ScratchScreen) client.gui.screen()).gridBounds());
			double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
			double left = grid[0] + 2;
			double right = grid[0] + grid[2] - 2;
			context.getInput().setCursorPos(left * scale, (grid[1] + 4) * scale);
			context.getInput().holdMouse(LEFT_BUTTON);
			for (int row = 0; row * 7 + 4 < grid[3]; row++) {
				double y = grid[1] + 4 + row * 7;
				for (int s = 0; s <= 6; s++) {
					double t = s / 6.0;
					double x = row % 2 == 0 ? left + (right - left) * t : right - (right - left) * t;
					context.getInput().setCursorPos(x * scale, y * scale);
					context.waitTick();
				}
			}
			context.getInput().releaseMouse(LEFT_BUTTON);
			double secondRow = grid[1] + grid[3] + 6 + grid[3] / 2.0;
			context.getInput().setCursorPos(left * scale, (secondRow - 8) * scale);
			context.getInput().holdMouse(LEFT_BUTTON);
			for (int s = 0; s <= 6; s++) {
				context.getInput().setCursorPos((left + (right - left) * s / 6.0) * scale, (secondRow - 8 + 16 * s / 6.0) * scale);
				context.waitTick();
			}
			context.getInput().releaseMouse(LEFT_BUTTON);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(4);
			context.takeScreenshot("scratch_rubbing");
			boolean[] state = context.computeOnClient(client -> {
				ScratchScreen screen = (ScratchScreen) client.gui.screen();
				return new boolean[] {screen.isRevealed(0), screen.isRevealed(1), screen.isRevealed(2), screen.isRevealed(4), screen.isFinished()};
			});
			if (!state[0] || !state[1] || !state[2] || state[3] || state[4]) {
				throw new AssertionError("Rubbing the first row should reveal it and nothing below it fully");
			}

			context.runOnClient(client -> ((ScratchScreen) client.gui.screen()).revealAll());
			context.waitTicks(12);
			context.takeScreenshot("scratch_won");
			context.setScreen(() -> null);
			context.runOnClient(client -> ClientConfig.get().scratchPanel = false);
			context.waitTicks(60);

			int paid = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ItemStack card = ItemStack.EMPTY;
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					if (player.getInventory().getItem(i).is(ModItems.SCRATCH_CARD_GOLD)) {
						card = player.getInventory().getItem(i);
					}
				}
				ScratchData data = ScratchCardItem.data(card);
				if (data == null || !data.scratched() || data.multiplier() != multiplier) {
					throw new AssertionError("Server did not record the scratched card: " + data);
				}
				LotteryClerkEntity clerk = player.level().getEntitiesOfClass(LotteryClerkEntity.class, new AABB(player.blockPosition()).inflate(12)).getFirst();
				clerk.redeem(player, card);
				return player.getInventory().countItem(Items.EMERALD);
			});
			if (paid != multiplier * ScratchCardItem.Tier.GOLD.price) {
				throw new AssertionError("Expected " + multiplier * ScratchCardItem.Tier.GOLD.price + " emeralds, got " + paid);
			}
			context.waitTicks(4);
			context.takeScreenshot("exchange_paid");

			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.getInventory().add(new ItemStack(Items.WHEAT, 40));
				player.getInventory().add(new ItemStack(Items.POTATO, 12));
				LotteryClerkEntity clerk = player.level().getEntitiesOfClass(LotteryClerkEntity.class, new AABB(player.blockPosition()).inflate(12)).getFirst();
				clerk.openExchange(player);
			});
			context.waitForScreen(ExchangeScreen.class);
			context.waitTicks(2);
			context.takeScreenshot("exchange_screen");
			context.setScreen(() -> null);

			// A pile of litter and the scratch card items, to check the cutout textures in the world.
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.getInventory().add(new ItemStack(ModItems.SCRATCH_CARD_BRONZE));
				player.getInventory().add(new ItemStack(ModItems.SCRATCH_CARD_SILVER));
				player.getInventory().add(new ItemStack(ModItems.USED_SCRATCH_CARD, 5));
				player.getInventory().add(new ItemStack(ModItems.LOTTERY_CLERK_SPAWN_EGG));
				player.getInventory().add(new ItemStack(ModItems.KOLEKTURA));
			});
			context.waitTicks(4);
			context.takeScreenshot("exchange_hotbar");
		}
	}
}
