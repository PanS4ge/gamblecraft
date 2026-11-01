package pl.pansage.gamblecraft.test.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pl.pansage.gamblecraft.block.SewingTableBlock;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;
import pl.pansage.gamblecraft.client.screen.CashierScreen;
import pl.pansage.gamblecraft.entity.CashierEntity;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.registry.ModEntities;
import pl.pansage.gamblecraft.registry.ModItems;
import pl.pansage.gamblecraft.registry.ModVillagers;

/** The suit on the player, the tailor at his sewing table, the cashier, chips in 3D, and both shop windows. */
public class CasinoShopClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof AgeGateScreen)) {
			context.clickScreenButton("gamblecraft.age_gate.accept");
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			int[] ids = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos feet = player.blockPosition();
				player.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0, 10);
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.SUIT_JACKET));
				player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SUIT_TROUSERS));
				player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SUIT_SHOES));
				BlockPos table = feet.offset(-1, 0, 4);
				level.setBlock(table, ModBlocks.SEWING_TABLE.defaultBlockState().setValue(SewingTableBlock.FACING, Direction.NORTH), 3);
				Villager tailor = new Villager(EntityTypes.VILLAGER, level);
				tailor.setVillagerData(tailor.getVillagerData().withProfession(level.registryAccess(), ModVillagers.TAILOR).withLevel(4));
				tailor.snapTo(table.getX() + 0.5, table.getY(), table.getZ() + 1.5, 180, 0);
				tailor.setNoAi(true);
				level.addFreshEntity(tailor);
				CashierEntity cashier = ModEntities.CASHIER.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
				cashier.snapTo(feet.getX() + 2.5, feet.getY(), feet.getZ() + 4.5, 180, 0);
				level.addFreshEntity(cashier);
				for (int i = 0; i < ModItems.CHIPS.length; i++) {
					ItemEntity chip = new ItemEntity(level, feet.getX() + 0.5 + (i - 2.5) * 0.35, feet.getY() + 0.1, feet.getZ() + 2.2,
							new ItemStack(ModItems.CHIPS[i], 1 + i));
					chip.setDeltaMovement(0, 0, 0);
					chip.setNeverPickUp();
					level.addFreshEntity(chip);
				}
				return new int[] {tailor.getId(), cashier.getId()};
			});
			context.waitTicks(30);
			context.takeScreenshot("shop_overview");

			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("suit_front");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);
			context.takeScreenshot("suit_back");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CHIPS[4], 3));
				player.getInventory().add(new ItemStack(Items.DIAMOND, 5));
				player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 12));
				player.getInventory().add(new ItemStack(Items.EMERALD, 64));
				for (int i = 0; i < ModItems.CHIPS.length; i++) {
					player.getInventory().add(new ItemStack(ModItems.CHIPS[i], 8));
				}
			});
			context.waitTicks(5);
			context.takeScreenshot("chips_in_hand");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(3);
			context.takeScreenshot("chips_in_hand_third_person");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				Villager tailor = (Villager) player.level().getEntity(ids[0]);
				tailor.setTradingPlayer(player);
				tailor.openTradingScreen(player, tailor.getDisplayName(), tailor.getVillagerData().level());
			});
			context.waitForScreen(MerchantScreen.class);
			context.waitTicks(2);
			context.takeScreenshot("tailor_trades");
			context.setScreen(() -> null);

			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				CashierEntity cashier = (CashierEntity) player.level().getEntity(ids[1]);
				cashier.buy(player, Items.DIAMOND, 2);
				cashier.interact(player, InteractionHand.MAIN_HAND, cashier.position());
			});
			context.waitForScreen(CashierScreen.class);
			context.waitTicks(2);
			context.takeScreenshot("cashier_screen");
			context.setScreen(() -> null);
		}
	}
}
