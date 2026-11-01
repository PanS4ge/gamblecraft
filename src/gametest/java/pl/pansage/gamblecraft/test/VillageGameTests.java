package pl.pansage.gamblecraft.test;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.block.KolekturaBlock;
import pl.pansage.gamblecraft.core.games.GameKind;
import pl.pansage.gamblecraft.core.table.TableTypes.Wager;
import pl.pansage.gamblecraft.entity.CardTableEntity;
import pl.pansage.gamblecraft.entity.LotteryClerkEntity;
import pl.pansage.gamblecraft.entity.StreetDealerEntity;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.TablePlacement;
import pl.pansage.gamblecraft.game.TableSession;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.item.ScratchData;
import pl.pansage.gamblecraft.network.ScratchPayloads.ExchangeAction;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.registry.ModComponents;
import pl.pansage.gamblecraft.registry.ModEntities;
import pl.pansage.gamblecraft.registry.ModItems;
import pl.pansage.gamblecraft.worldgen.VillageInjector;

public class VillageGameTests {
	@GameTest
	public void villagePoolsContainTheDealerStall(GameTestHelper helper) {
		var pools = helper.getLevel().getServer().registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		for (String village : VillageInjector.VILLAGES) {
			StructureTemplatePool pool = pools.getValue(Identifier.withDefaultNamespace("village/" + village + "/houses"));
			if (pool == null || !VillageInjector.contains(pool, "dealer_stall_" + village)) {
				helper.fail("Dealer stall missing from " + village + " houses");
				return;
			}
			if (!VillageInjector.contains(pool, "exchange_" + village)) {
				helper.fail("Exchange missing from " + village + " houses");
				return;
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void exchangeTemplatePlacesAClerkAndCounter(GameTestHelper helper) {
		StructureTemplate template = helper.getLevel().getServer().getStructureTemplateManager().get(GambleCraft.id("village/exchange_plains")).orElse(null);
		if (template == null) {
			helper.fail("Exchange template not found");
			return;
		}
		BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
		template.placeInWorld(helper.getLevel(), origin, origin, new StructurePlaceSettings(), helper.getLevel().getRandom(), 2);
		helper.assertBlockPresent(ModBlocks.KOLEKTURA, new BlockPos(4, 2, 3));
		helper.succeedWhenEntityPresent(ModEntities.LOTTERY_CLERK, new BlockPos(5, 2, 3));
	}

	/** Villages rotate their buildings, so the counter has to turn with the template to keep facing the door. */
	@GameTest(maxTicks = 20)
	public void rotatedExchangeTurnsTheCounter(GameTestHelper helper) {
		StructureTemplate template = helper.getLevel().getServer().getStructureTemplateManager().get(GambleCraft.id("village/exchange_plains")).orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(6, 1, 0));
		template.placeInWorld(helper.getLevel(), origin, origin, new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90),
				helper.getLevel().getRandom(), 2);
		helper.assertBlockProperty(new BlockPos(3, 2, 4), KolekturaBlock.FACING, Direction.NORTH);
		helper.succeed();
	}

	/** The clerk pays winning cards of her own village, refuses cards from elsewhere and bins losers. */
	@GameTest(maxTicks = 40)
	public void clerkPaysOnlyCardsFromHerVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = helper.absolutePos(new BlockPos(1, 1, 1));
		level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AgeGate.markAccepted(player);
		LotteryClerkEntity clerk = ModEntities.LOTTERY_CLERK.create(level, EntitySpawnReason.TRIGGERED);
		clerk.snapTo(bell.getX() + 2.5, bell.getY(), bell.getZ() + 0.5, 0, 0);
		level.addFreshEntity(clerk);
		if (clerk.villageBell(level).map(found -> !found.equals(bell)).orElse(true)) {
			helper.fail("Clerk does not see the test bell as her village");
			return;
		}

		ItemStack foreign = scratched(ScratchCardItem.issued(ModItems.SCRATCH_CARD_SILVER, bell.offset(400, 0, 0), 1L), 5);
		player.setItemInHand(InteractionHand.MAIN_HAND, foreign);
		clerk.redeem(player, foreign);
		if (emeralds(player) != 0 || foreign.isEmpty()) {
			helper.fail("Card from another village was paid or taken");
			return;
		}

		ItemStack loser = scratched(ScratchCardItem.issued(ModItems.SCRATCH_CARD_SILVER, bell, 2L), 0);
		player.setItemInHand(InteractionHand.MAIN_HAND, loser);
		clerk.redeem(player, loser);
		if (emeralds(player) != 0 || !loser.isEmpty()) {
			helper.fail("Losing card was paid or kept");
			return;
		}

		ItemStack winner = scratched(ScratchCardItem.issued(ModItems.SCRATCH_CARD_SILVER, bell, 3L), 5);
		player.setItemInHand(InteractionHand.MAIN_HAND, winner);
		clerk.redeem(player, winner);
		int expected = 5 * ScratchCardItem.Tier.SILVER.price;
		if (emeralds(player) != expected || !winner.isEmpty()) {
			helper.fail("Expected " + expected + " emeralds for the winning card, got " + emeralds(player));
			return;
		}
		helper.succeed();
	}

	/** Cards bought at the counter cost emeralds and are issued by the clerk's village. */
	@GameTest(maxTicks = 40)
	public void clerkSellsCardsOfHerVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = helper.absolutePos(new BlockPos(1, 1, 1));
		level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AgeGate.markAccepted(player);
		player.snapTo(bell.getX() + 0.5, bell.getY(), bell.getZ() + 2.5, 0, 0);
		player.getInventory().add(new ItemStack(Items.EMERALD, 10));
		LotteryClerkEntity clerk = ModEntities.LOTTERY_CLERK.create(level, EntitySpawnReason.TRIGGERED);
		clerk.snapTo(bell.getX() + 2.5, bell.getY(), bell.getZ() + 0.5, 0, 0);
		level.addFreshEntity(clerk);
		clerk.handle(player, new ExchangeAction(clerk.getId(), true, "", ScratchCardItem.Tier.GOLD.ordinal()));
		if (emeralds(player) != 10 - ScratchCardItem.Tier.GOLD.price) {
			helper.fail("Gold card price was not taken: " + emeralds(player));
			return;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ModItems.SCRATCH_CARD_GOLD)) {
				ScratchData data = ScratchCardItem.data(stack);
				if (data == null || data.bell() != bell.asLong()) {
					helper.fail("Bought card is not issued by the clerk's village");
					return;
				}
				helper.succeed();
				return;
			}
		}
		helper.fail("No gold card in the inventory");
	}

	/** Scratch cards reach vanilla professions through the villager_trade tags. */
	@GameTest
	public void professionsSellScratchCards(GameTestHelper helper) {
		var trades = helper.getLevel().registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
		String[][] expected = {{"farmer/level_1", "bronze"}, {"fletcher/level_2", "silver"}, {"cartographer/level_3", "gold"}};
		for (String[] pair : expected) {
			TagKey<VillagerTrade> tag = TagKey.create(Registries.VILLAGER_TRADE, Identifier.withDefaultNamespace(pair[0]));
			boolean found = false;
			for (var holder : trades.getTagOrEmpty(tag)) {
				found |= holder.is(GambleCraft.id("scratch/" + pair[1]));
			}
			if (!found) {
				helper.fail("No " + pair[1] + " scratch card in " + pair[0]);
				return;
			}
		}
		helper.succeed();
	}

	private static ItemStack scratched(ItemStack card, int multiplier) {
		card.set(ModComponents.SCRATCH, ScratchCardItem.data(card).scratch(multiplier));
		return card;
	}

	@GameTest(maxTicks = 40)
	public void stallTemplatePlacesADealer(GameTestHelper helper) {
		StructureTemplate template = helper.getLevel().getServer().getStructureTemplateManager().get(GambleCraft.id("village/dealer_stall_plains")).orElse(null);
		if (template == null) {
			helper.fail("Stall template not found");
			return;
		}
		BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
		template.placeInWorld(helper.getLevel(), origin, origin, new StructurePlaceSettings(), helper.getLevel().getRandom(), 2);
		helper.succeedWhenEntityPresent(ModEntities.STREET_DEALER, new BlockPos(3, 2, 2));
	}

	/** A whole blackjack round on the server: stakes leave the inventory and every emerald comes back or goes to the dealer. */
	@GameTest(maxTicks = 400)
	public void blackjackRoundConservesEmeralds(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AgeGate.markAccepted(player);
		player.getInventory().add(new ItemStack(Items.EMERALD, 10));
		BlockPos tableBlock = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.getLevel().setBlockAndUpdate(tableBlock, Blocks.OAK_PLANKS.defaultBlockState());
		player.snapTo(tableBlock.getX() + 0.5, tableBlock.getY() + 1, tableBlock.getZ() - 1.5, 0, 0);
		StreetDealerEntity dealer = ModEntities.STREET_DEALER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		dealer.snapTo(tableBlock.getX() + 0.5, tableBlock.getY() + 1, tableBlock.getZ() + 1.5, 0, 0);
		helper.getLevel().addFreshEntity(dealer);
		dealer.openFor(player);
		CardTableEntity table = TablePlacement.createTable(player, dealer, tableBlock, GameKind.BLACKJACK, Items.EMERALD, 1.0);
		if (table == null) {
			helper.fail("Table not created");
			return;
		}
		TableSession session = table.session();
		if (session.bet(player, Wager.of(2)) != null) {
			helper.fail("Bet refused");
			return;
		}
		if (emeralds(player) != 8) {
			helper.fail("Stake was not taken");
			return;
		}
		helper.succeedWhen(() -> {
			if (session.game().awaitingInput()) {
				// stand, or decline insurance when the dealer shows an ace
				session.act(player, session.game().defaultAct(session.game().seatOf(player.getUUID())), 0);
			}
			if (session.game().round() < 1 || session.game().isRoundRunning()) {
				throw new net.minecraft.gametest.framework.GameTestAssertException(net.minecraft.network.chat.Component.literal("round running"), 0);
			}
			int emeralds = emeralds(player);
			if (!List.of(8, 9, 10, 11, 12, 13).contains(emeralds)) {
				helper.fail("Unexpected emeralds after round: " + emeralds);
			}
		});
	}

	private static int emeralds(ServerPlayer player) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(Items.EMERALD)) {
				n += player.getInventory().getItem(i).getCount();
			}
		}
		return n;
	}
}
