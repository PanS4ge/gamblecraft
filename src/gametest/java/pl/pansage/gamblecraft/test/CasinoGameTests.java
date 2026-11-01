package pl.pansage.gamblecraft.test;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.core.games.GameKind;
import pl.pansage.gamblecraft.entity.CardTableEntity;
import pl.pansage.gamblecraft.entity.CashierEntity;
import pl.pansage.gamblecraft.entity.CasinoDealerEntity;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.Suit;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.registry.ModEntities;
import pl.pansage.gamblecraft.registry.ModItems;
import pl.pansage.gamblecraft.registry.ModVillagers;

public class CasinoGameTests {
	/** Two diamonds buy 80 chips; cashing out into emeralds pays 7 (72 after the 10% cut) and leaves 2 chips. */
	@GameTest(maxTicks = 20)
	public void cashierSellsAndBuysBackChips(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AgeGate.markAccepted(player);
		player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
		CashierEntity cashier = ModEntities.CASHIER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		cashier.snapTo(player.getX() + 1, player.getY(), player.getZ(), 0, 0);
		helper.getLevel().addFreshEntity(cashier);
		cashier.buy(player, Items.DIAMOND, 99);
		if (ChipCurrency.INSTANCE.balance(player) != 80 || count(player, Items.DIAMOND) != 0) {
			helper.fail("Expected 80 chips for 2 diamonds, got " + ChipCurrency.INSTANCE.balance(player));
			return;
		}
		cashier.cashOut(player, Items.EMERALD, -1);
		if (count(player, Items.EMERALD) != 7 || ChipCurrency.INSTANCE.balance(player) != 2) {
			helper.fail("Expected 7 emeralds and 2 chips, got " + count(player, Items.EMERALD) + " and " + ChipCurrency.INSTANCE.balance(player));
			return;
		}
		helper.succeed();
	}

	/** Paying 7 with a single 1000 chip gives 993 back in change. */
	@GameTest(maxTicks = 20)
	public void chipStakesMakeChange(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().add(new ItemStack(ModItems.CHIPS[5]));
		if (!ChipCurrency.INSTANCE.withdraw(player, 7) || ChipCurrency.INSTANCE.balance(player) != 993) {
			helper.fail("Change was not made: " + ChipCurrency.INSTANCE.balance(player));
			return;
		}
		if (ChipCurrency.INSTANCE.withdraw(player, 994)) {
			helper.fail("Withdrew more than the balance");
			return;
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void fullSuitIsRecognised(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.SUIT_JACKET));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SUIT_TROUSERS));
		if (Suit.isWearing(player)) {
			helper.fail("Two pieces counted as a full suit");
			return;
		}
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SUIT_SHOES));
		if (!Suit.isWearing(player)) {
			helper.fail("Full suit not recognised");
			return;
		}
		helper.succeed();
	}

	/** Leather, black dye and a poppy make the jacket. */
	@GameTest(maxTicks = 20)
	public void suitJacketRecipe(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack l = new ItemStack(Items.LEATHER);
		List<ItemStack> grid = new ArrayList<>(List.of(l, new ItemStack(Items.POPPY), l, l, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("black_dye"))), l, l, l, l));
		var recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grid), level);
		if (recipe.isEmpty() || !recipe.get().value().assemble(CraftingInput.of(3, 3, grid)).is(ModItems.SUIT_JACKET)) {
			helper.fail("Jacket recipe missing");
			return;
		}
		helper.succeed();
	}

	/** A level 4 tailor offers the jacket, for 48 emeralds. */
	@GameTest(maxTicks = 20)
	public void tailorSellsTheSuit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager tailor = new Villager(EntityTypes.VILLAGER, level);
		tailor.setVillagerData(tailor.getVillagerData().withProfession(level.registryAccess(), ModVillagers.TAILOR).withLevel(4));
		tailor.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5)));
		level.addFreshEntity(tailor);
		for (MerchantOffer offer : tailor.getOffers()) {
			if (offer.getResult().is(ModItems.SUIT_JACKET) && offer.getBaseCostA().is(Items.EMERALD) && offer.getBaseCostA().getCount() == 48) {
				helper.succeed();
				return;
			}
		}
		helper.fail("No jacket among " + tailor.getOffers().size() + " offers");
	}

	/** An unemployed villager next to a sewing table takes the job. */
	@GameTest(maxTicks = 1200)
	public void villagerBecomesTailor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
		level.setBlockAndUpdate(table, ModBlocks.SEWING_TABLE.defaultBlockState());
		// villagers only walk to a job site while idle, working or playing, not while resting at night
		helper.setTime(1000);
		Villager villager = new Villager(EntityTypes.VILLAGER, level);
		villager.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 2.5)));
		level.addFreshEntity(villager);
		helper.succeedWhen(() -> {
			if (!villager.getVillagerData().profession().is(ModVillagers.TAILOR)) {
				throw new GameTestAssertException(Component.literal("still " + villager.getVillagerData().profession()), 0);
			}
		});
	}

	/** The door stays shut without a suit, opens with one, and closes itself again. */
	@GameTest(maxTicks = 120)
	public void casinoDoorChecksTheDressCode(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockState lower = ModBlocks.CASINO_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		level.setBlock(door, lower, 3);
		level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
		// the mock player always counts as creative, which the door lets through; a fake player is a plain survivor
		ServerPlayer player = FakePlayer.get(level);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(door), Direction.NORTH, door, false);
		level.getBlockState(door).useWithoutItem(level, player, hit);
		if (level.getBlockState(door).getValue(DoorBlock.OPEN)) {
			helper.fail("Door opened without a suit");
			return;
		}
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.SUIT_JACKET));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SUIT_TROUSERS));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SUIT_SHOES));
		level.getBlockState(door).useWithoutItem(level, player, hit);
		if (!level.getBlockState(door).getValue(DoorBlock.OPEN)) {
			helper.fail("Door stayed shut for a suit");
			return;
		}
		player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		helper.runAfterDelay(70, () -> {
			if (level.getBlockState(door).getValue(DoorBlock.OPEN)) {
				helper.fail("Door did not close itself");
				return;
			}
			helper.succeed();
		});
	}

	/** A croupier opens his table on the felt he faces: chips, the casino size, his own game. */
	@GameTest(maxTicks = 60)
	public void croupierOpensAChipTable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos felt = helper.absolutePos(new BlockPos(2, 1, 1));
		level.setBlockAndUpdate(felt, Blocks.OAK_PLANKS.defaultBlockState());
		level.setBlockAndUpdate(felt.west().below(), Blocks.STONE.defaultBlockState());
		CasinoDealerEntity dealer = ModEntities.CASINO_DEALER.create(level, EntitySpawnReason.TRIGGERED);
		dealer.setGame(GameKind.BACCARAT);
		dealer.snapTo(felt.getX() - 0.5, felt.getY(), felt.getZ() + 0.5, 270, 0);
		level.addFreshEntity(dealer);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AgeGate.markAccepted(player);
		player.snapTo(felt.getX() + 2.5, felt.getY(), felt.getZ() + 0.5, 90, 0);
		player.getInventory().add(new ItemStack(ModItems.CHIPS[3], 5));
		helper.runAfterDelay(10, () -> {
			if (!felt.equals(dealer.felt())) {
				helper.fail("Croupier faces " + dealer.felt() + ", not his felt " + felt);
				return;
			}
			dealer.openFor(player);
			CardTableEntity table = dealer.table();
			if (table == null || !table.blockPosition().equals(felt.above()) || table.session().currency() != ChipCurrency.INSTANCE
					|| table.session().game().maxSeats() != 6 || table.session().kind() != GameKind.BACCARAT) {
				helper.fail("No casino table on the felt: " + table);
				return;
			}
			helper.succeed();
		});
	}

	private static int count(ServerPlayer player, Item item) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(item)) {
				n += player.getInventory().getItem(i).getCount();
			}
		}
		return n;
	}
}
