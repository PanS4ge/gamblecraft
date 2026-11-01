package pl.pansage.gamblecraft.test.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import pl.pansage.gamblecraft.network.TablePayloads.TableAction;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;
import pl.pansage.gamblecraft.client.screen.CardGameScreen;
import pl.pansage.gamblecraft.client.screen.DealerScreen;
import pl.pansage.gamblecraft.core.games.GameKind;
import pl.pansage.gamblecraft.entity.CardTableEntity;
import pl.pansage.gamblecraft.entity.StreetDealerEntity;
import pl.pansage.gamblecraft.game.TablePlacement;
import pl.pansage.gamblecraft.registry.ModEntities;

/** Village dealer end to end: game picker, a blackjack table on a block, one bet, one round. */
public class DealerTableClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof AgeGateScreen)) {
			context.clickScreenButton("gamblecraft.age_gate.accept");
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			int[] ids = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				var level = player.level();
				BlockPos feet = player.blockPosition();
				player.getInventory().add(new ItemStack(Items.EMERALD, 20));
				BlockPos tableBlock = feet.relative(player.getDirection(), 2);
				level.setBlock(tableBlock, Blocks.OAK_PLANKS.defaultBlockState(), 3);
				level.setBlock(tableBlock.above(), Blocks.AIR.defaultBlockState(), 3);
				StreetDealerEntity dealer = ModEntities.STREET_DEALER.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
				BlockPos dealerPos = tableBlock.relative(player.getDirection(), 1);
				dealer.snapTo(dealerPos.getX() + 0.5, dealerPos.getY(), dealerPos.getZ() + 0.5, 0, 0);
				level.addFreshEntity(dealer);
				return new int[] {dealer.getId(), tableBlock.getX(), tableBlock.getY(), tableBlock.getZ()};
			});
			context.waitTicks(20);
			context.takeScreenshot("dealer_idle");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				((StreetDealerEntity) player.level().getEntity(ids[0])).gesture(StreetDealerEntity.GESTURE_CHEER);
			});
			context.waitTicks(6);
			context.takeScreenshot("dealer_cheer");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				((StreetDealerEntity) player.level().getEntity(ids[0])).gesture(StreetDealerEntity.GESTURE_SHUFFLE);
			});
			context.waitTicks(8);
			context.takeScreenshot("dealer_shuffle");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				((StreetDealerEntity) player.level().getEntity(ids[0])).gesture(StreetDealerEntity.GESTURE_SULK);
			});
			context.waitTicks(10);
			context.takeScreenshot("dealer_sulk");
			context.waitTicks(30);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				((StreetDealerEntity) player.level().getEntity(ids[0])).openFor(player);
			});
			context.waitForScreen(DealerScreen.class);
			context.takeScreenshot("dealer_picker");
			context.setScreen(() -> null);

			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				StreetDealerEntity dealer = (StreetDealerEntity) player.level().getEntity(ids[0]);
				CardTableEntity table = TablePlacement.createTable(player, dealer, new BlockPos(ids[1], ids[2], ids[3]), GameKind.BLACKJACK, Items.EMERALD, 1.0);
				if (table == null) {
					throw new AssertionError("Table was not created");
				}
			});
			context.waitForScreen(CardGameScreen.class);
			context.waitTicks(15);
			context.takeScreenshot("table_betting_overhead");
			// The bet button's label carries the amount ("Bet 1"), so send what it sends.
			context.runOnClient(client -> {
				int tableId = ((CardGameScreen) client.gui.screen()).tableId();
				ClientPlayNetworking.send(new TableAction(tableId, TableAction.Op.BET, -1, 1, 0, 0));
			});
			context.waitTicks(4);
			int tosses = context.computeOnClient(client -> {
				var view = pl.pansage.gamblecraft.client.table.ClientTables.get(((CardGameScreen) client.gui.screen()).tableId());
				return view == null ? -1 : view.tosses.size();
			});
			if (tosses != 1) {
				throw new AssertionError("Expected the stake toss to reach the client, got " + tosses);
			}
			context.takeScreenshot("table_stake_flying");
			context.waitTicks(10);
			context.takeScreenshot("table_stake_on_felt");
			context.waitTicks(66);
			context.takeScreenshot("table_dealt_full_help");
			context.clickScreenButton("gamblecraft.view.assist_2");
			context.waitTicks(2);
			context.takeScreenshot("table_dealt_values_help");
			context.clickScreenButton("gamblecraft.view.assist_1");
			context.waitTicks(2);
			context.takeScreenshot("table_dealt_no_help");
			context.clickScreenButton("gamblecraft.view.assist_0");
			context.clickScreenButton("gamblecraft.view.table");
			context.waitTicks(14);
			context.takeScreenshot("table_dealt_panel");
			context.clickScreenButton("gamblecraft.view.panel");
			context.waitTicks(14);
			if (!context.tryClickScreenButton("gamblecraft.act.stand")) {
				context.tryClickScreenButton("gamblecraft.act.no_insurance");
				context.waitTicks(10);
				context.tryClickScreenButton("gamblecraft.act.stand");
			}
			context.waitTicks(70);
			context.takeScreenshot("table_result");
			context.setScreen(() -> null);
			context.waitTicks(16);
			context.takeScreenshot("table_world");

			int emeralds = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				int n = 0;
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					if (player.getInventory().getItem(i).is(Items.EMERALD)) {
						n += player.getInventory().getItem(i).getCount();
					}
				}
				return n;
			});
			if (emeralds < 19 || emeralds > 23) {
				throw new AssertionError("Emeralds after one 1-emerald round should be 19..23, got " + emeralds);
			}
		}
	}
}
