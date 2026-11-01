package pl.pansage.gamblecraft.test;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.block.NpcMarkerBlock;
import pl.pansage.gamblecraft.block.entity.NpcMarkerBlockEntity;
import pl.pansage.gamblecraft.entity.AddictedGamblerEntity;
import pl.pansage.gamblecraft.entity.BartenderEntity;
import pl.pansage.gamblecraft.entity.CashierEntity;
import pl.pansage.gamblecraft.entity.DebtCollectorEntity;
import pl.pansage.gamblecraft.entity.LoanSharkEntity;
import pl.pansage.gamblecraft.entity.MuggerEntity;
import pl.pansage.gamblecraft.entity.PickpocketEntity;
import pl.pansage.gamblecraft.entity.WaitressEntity;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.LoanData;
import pl.pansage.gamblecraft.game.Muggings;
import pl.pansage.gamblecraft.game.PlayerLedger;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.registry.ModEntities;
import pl.pansage.gamblecraft.registry.ModItems;

public class PeopleGameTests {
	/** A survival player of their own with {@code chips} chips (the mock player always counts as creative). */
	private static ServerPlayer guest(GameTestHelper helper, BlockPos at, long chips) {
		ServerPlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "guest"));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(at));
		player.snapTo(pos.x, pos.y, pos.z, 0, 0);
		AgeGate.markAccepted(player);
		if (chips > 0) {
			ChipCurrency.INSTANCE.deposit(player, chips);
		}
		return player;
	}

	private static <T extends net.minecraft.world.entity.Entity> T spawn(GameTestHelper helper, net.minecraft.world.entity.EntityType<T> type, BlockPos at) {
		T entity = type.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(at));
		entity.snapTo(pos.x, pos.y, pos.z, 0, 0);
		helper.getLevel().addFreshEntity(entity);
		return entity;
	}

	/** A random source that always rolls zero: the luckiest outcome of every chance. */
	private static RandomSource luckiest() {
		return new LegacyRandomSource(1) {
			@Override
			public double nextDouble() {
				return 0;
			}
		};
	}

	/** A mineral buys the gambler's attention; far from the casino he quits, pays his thanks and becomes a villager. */
	@GameTest(maxTicks = 20)
	public void gamblerFollowsAndQuits(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = guest(helper, new BlockPos(1, 1, 1), 0);
		AddictedGamblerEntity gambler = spawn(helper, ModEntities.ADDICTED_GAMBLER, new BlockPos(3, 1, 3));
		gambler.setHome(helper.absolutePos(new BlockPos(3, 1, 3)));
		if (AddictedGamblerEntity.tierOf(new ItemStack(Items.DIAMOND)) != 5 || AddictedGamblerEntity.tierOf(new ItemStack(Items.COPPER_INGOT)) != 1) {
			helper.fail("Mineral tiers are wrong");
			return;
		}
		gambler.follow(player, 2);
		if (!player.getUUID().equals(gambler.leader()) || gambler.tier() != 2) {
			helper.fail("The gambler did not take the bait");
			return;
		}
		gambler.comeToSenses(level, player, luckiest());
		boolean villager = !level.getEntitiesOfClass(Villager.class, new AABB(gambler.blockPosition()).inflate(2)).isEmpty();
		if (!gambler.isRemoved() || !villager) {
			helper.fail("The gambler did not turn into a villager");
		} else if (ChipCurrency.INSTANCE.balance(player) < 100) {
			helper.fail("No thanks in chips: " + ChipCurrency.INSTANCE.balance(player));
		} else {
			helper.succeed();
		}
	}

	/** For a netherite ingot's worth of attention, his thanks can be the map to his buried savings. */
	@GameTest(maxTicks = 20)
	public void gamblerLeavesHisStashMap(GameTestHelper helper) {
		ServerPlayer player = guest(helper, new BlockPos(1, 1, 1), 0);
		AddictedGamblerEntity gambler = spawn(helper, ModEntities.ADDICTED_GAMBLER, new BlockPos(3, 1, 3));
		gambler.setHome(helper.absolutePos(new BlockPos(3, 1, 3)));
		gambler.follow(player, 6);
		gambler.comeToSenses(helper.getLevel(), player, luckiest());
		if (player.getInventory().countItem(Items.FILLED_MAP) != 1) {
			helper.fail("No stash map");
			return;
		}
		helper.succeed();
	}

	/** A loan, an overdue visit from the collectors (each blow takes chips off the debt), repayment, a killed collector. */
	@GameTest(maxTicks = 20)
	public void loanSharkAndCollectors(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = guest(helper, new BlockPos(1, 1, 1), 0);
		LoanSharkEntity shark = spawn(helper, ModEntities.LOAN_SHARK, new BlockPos(3, 1, 3));
		LoanData loans = LoanData.get(level.getServer());
		shark.choose(player, 1);
		if (ChipCurrency.INSTANCE.balance(player) != 500 || loans.owed(player.getUUID()) != 625
				|| player.getInventory().countItem(ModItems.PROMISSORY_NOTE) != 1) {
			helper.fail("Loan of 500 not given with a note for 625");
			return;
		}
		shark.choose(player, 0);
		if (ChipCurrency.INSTANCE.balance(player) != 500) {
			helper.fail("A second loan was given while the first is unpaid");
			return;
		}
		loans.setDue(player.getUUID(), level.getGameTime() - 1);
		LoanData.sendCollectors(level, player, loans.owed(player.getUUID()));
		List<DebtCollectorEntity> crew = level.getEntitiesOfClass(DebtCollectorEntity.class, new AABB(player.blockPosition()).inflate(24),
				c -> player.getUUID().equals(c.victim()));
		if (crew.size() != 3 || !crew.getFirst().getItemBySlot(EquipmentSlot.MAINHAND).is(ModItems.CLUB)
				|| !crew.getFirst().getItemBySlot(EquipmentSlot.CHEST).is(ModItems.SUIT_JACKET)) {
			helper.fail("Expected three collectors in suits with clubs, found " + crew.size());
			return;
		}
		crew.getFirst().blowLanded(level, player);
		if (loans.owed(player.getUUID()) != 625 - 62 || ChipCurrency.INSTANCE.balance(player) != 500 - 62) {
			helper.fail("A blow took " + (500 - ChipCurrency.INSTANCE.balance(player)) + " chips, debt " + loans.owed(player.getUUID()));
			return;
		}
		shark.choose(player, LoanData.AMOUNTS.length);
		if (loans.owed(player.getUUID()) != 625 - 500 || ChipCurrency.INSTANCE.balance(player) != 0) {
			helper.fail("Repaying did not take all the chips off the debt");
			return;
		}
		crew.get(1).hurtServer(level, level.damageSources().playerAttack(player), 1000);
		if (loans.owed(player.getUUID()) != 125 + 12) {
			helper.fail("Killing a collector should add a tenth: " + loans.owed(player.getUUID()));
			return;
		}
		ChipCurrency.INSTANCE.deposit(player, 1000);
		shark.choose(player, LoanData.AMOUNTS.length);
		if (loans.owed(player.getUUID()) != 0 || player.getInventory().countItem(ModItems.PROMISSORY_NOTE) != 0) {
			helper.fail("The debt was not cleared");
			return;
		}
		crew.forEach(net.minecraft.world.entity.Entity::discard);
		helper.succeed();
	}

	/** A big winner is mugged: the gang wears and wields what the player does, is half again as fast, takes a slice per blow. */
	@GameTest(maxTicks = 20)
	public void muggersMatchTheirVictim(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = guest(helper, new BlockPos(1, 1, 1), 0);
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		// the chips after the sword, or they would land in the hand slot it takes
		ChipCurrency.INSTANCE.deposit(player, 2000);
		Muggings.ambush(level, player, 1500);
		List<MuggerEntity> gang = level.getEntitiesOfClass(MuggerEntity.class, new AABB(player.blockPosition()).inflate(24),
				m -> player.getUUID().equals(m.victim()));
		if (gang.size() != Muggings.gangSize(1500)) {
			helper.fail("Expected " + Muggings.gangSize(1500) + " muggers, found " + gang.size());
			return;
		}
		MuggerEntity mugger = gang.getFirst();
		if (!mugger.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE) || !mugger.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
				|| !mugger.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD)) {
			helper.fail("The mugger is not kitted out like the player");
			return;
		}
		if (Math.abs(mugger.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.23 * 1.5) > 1e-6) {
			helper.fail("Mugger speed " + mugger.getAttributeValue(Attributes.MOVEMENT_SPEED));
			return;
		}
		mugger.blowLanded(level, player);
		long taken = 2000 - ChipCurrency.INSTANCE.balance(player);
		if (taken != 1500 * 15 / 100 || mugger.stolen() != taken) {
			helper.fail("A blow took " + taken + " chips");
			return;
		}
		Vec3 at = mugger.position();
		mugger.hurtServer(level, level.damageSources().playerAttack(player), 1000);
		long dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(at)).inflate(3)).stream()
				.mapToLong(item -> item.getItem().getItem() instanceof pl.pansage.gamblecraft.item.ChipItem chip ? (long) chip.value() * item.getItem().getCount() : 0)
				.sum();
		boolean gearDropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(at)).inflate(3))
				.stream().anyMatch(item -> item.getItem().is(Items.DIAMOND_CHESTPLATE) || item.getItem().is(Items.IRON_SWORD));
		gang.forEach(net.minecraft.world.entity.Entity::discard);
		if (dropped != taken || gearDropped) {
			helper.fail("A beaten mugger should drop the " + taken + " chips and no gear (dropped " + dropped + ", gear " + gearDropped + ")");
			return;
		}
		helper.succeed();
	}

	/** Winnings above the threshold may draw a tail; smaller ones never do; bigger gangs for bigger wins. */
	@GameTest(maxTicks = 5)
	public void muggingOdds(GameTestHelper helper) {
		if (Muggings.chance(999) != 0 || Muggings.chance(1000) <= 0 || Muggings.chance(1_000_000) > 0.8
				|| Muggings.gangSize(1000) != 2 || Muggings.gangSize(5000) != 3 || Muggings.gangSize(50000) != 4) {
			helper.fail("Mugging odds or gang sizes are off");
			return;
		}
		helper.succeed();
	}

	/** The pickpocket lifts a slice of the chips; catching him gives them back with a reward, and no alarm. */
	@GameTest(maxTicks = 20)
	public void pickpocketStealsAndIsCaught(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = guest(helper, new BlockPos(1, 1, 1), 1000);
		PickpocketEntity thief = spawn(helper, ModEntities.PICKPOCKET, new BlockPos(2, 1, 1));
		long stolen = thief.steal(level, player);
		if (stolen < 50 || stolen > 200 || ChipCurrency.INSTANCE.balance(player) != 1000 - stolen) {
			helper.fail("Stole " + stolen + " of 1000");
			return;
		}
		thief.hurtServer(level, level.damageSources().playerAttack(player), 1);
		if (!thief.isRemoved() || ChipCurrency.INSTANCE.balance(player) != 1000 + PickpocketEntity.REWARD) {
			helper.fail("Caught thief: balance " + ChipCurrency.INSTANCE.balance(player));
			return;
		}
		helper.succeed();
	}

	/** Drinks for chips at the bar or from the waitress; champagne brings Luck and leaves the bottle. */
	@GameTest(maxTicks = 20)
	public void barSellsDrinks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = guest(helper, new BlockPos(1, 1, 1), 100);
		BartenderEntity bartender = spawn(helper, ModEntities.BARTENDER, new BlockPos(3, 1, 3));
		WaitressEntity waitress = spawn(helper, ModEntities.WAITRESS, new BlockPos(4, 1, 3));
		bartender.choose(player, 0);
		waitress.choose(player, 1);
		if (ChipCurrency.INSTANCE.balance(player) != 100 - 50 - 10 || player.getInventory().countItem(ModItems.CHAMPAGNE) != 1
				|| player.getInventory().countItem(ModItems.ESPRESSO) != 1) {
			helper.fail("Champagne and espresso were not sold for 60 chips");
			return;
		}
		int slot = player.getInventory().findSlotMatchingItem(new ItemStack(ModItems.CHAMPAGNE));
		ItemStack left = player.getInventory().getItem(slot).finishUsingItem(level, player);
		if (!player.hasEffect(MobEffects.LUCK) || !left.is(Items.GLASS_BOTTLE)) {
			helper.fail("Champagne did not bring Luck or leave its bottle");
			return;
		}
		helper.succeed();
	}

	/** The cashier's VIP card (for chips, or free after enough play) opens the VIP door; a suit alone does not. */
	@GameTest(maxTicks = 20)
	public void vipCardOpensTheVipDoor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer member = guest(helper, new BlockPos(1, 1, 3), 5000);
		ServerPlayer guest = guest(helper, new BlockPos(2, 1, 3), 0);
		ServerPlayer regular = guest(helper, new BlockPos(3, 1, 3), 0);
		for (ServerPlayer player : List.of(member, guest)) {
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.SUIT_JACKET));
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SUIT_TROUSERS));
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SUIT_SHOES));
		}
		CashierEntity cashier = spawn(helper, ModEntities.CASHIER, new BlockPos(4, 1, 4));
		if (!cashier.sellVipCard(member) || ChipCurrency.INSTANCE.balance(member) != 0 || member.getInventory().countItem(ModItems.VIP_CARD) == 0) {
			helper.fail("The VIP card was not sold for 5000 chips");
			return;
		}
		if (cashier.sellVipCard(regular)) {
			helper.fail("A VIP card was given without chips or turnover");
			return;
		}
		PlayerLedger.get(level.getServer()).staked(regular.getUUID(), PlayerLedger.VIP_TURNOVER);
		if (!cashier.sellVipCard(regular)) {
			helper.fail("Enough turnover should earn a free VIP card");
			return;
		}
		BlockPos door = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockState lower = ModBlocks.VIP_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		level.setBlock(door, lower, 3);
		level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(door), Direction.NORTH, door, false);
		level.getBlockState(door).useWithoutItem(level, guest, hit);
		if (level.getBlockState(door).getValue(DoorBlock.OPEN)) {
			helper.fail("The VIP door opened for a suit without a card");
			return;
		}
		level.getBlockState(door).useWithoutItem(level, member, hit);
		if (!level.getBlockState(door).getValue(DoorBlock.OPEN)) {
			helper.fail("The VIP door stayed shut for a card holder");
			return;
		}
		helper.succeed();
	}

	/** Journeyman cartographers sell a map to the nearest casino (the trade loads and is in their level 3 list). */
	@GameTest(maxTicks = 5)
	public void cartographersSellCasinoMaps(GameTestHelper helper) {
		var trades = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TRADE);
		var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.VILLAGER_TRADE,
				pl.pansage.gamblecraft.GambleCraft.id("cartographer/casino_map"));
		var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.VILLAGER_TRADE,
				net.minecraft.resources.Identifier.withDefaultNamespace("cartographer/level_3"));
		var holder = trades.get(key);
		if (holder.isEmpty() || !holder.get().is(tag)) {
			helper.fail("The casino map trade is missing or not offered at level 3");
			return;
		}
		helper.succeed();
	}

	/** A marker in a structure rolls once, puts its guest there and turns into the floor it stood in for. */
	@GameTest(maxTicks = 40)
	public void markerPutsAGuestThere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlockAndUpdate(pos, ModBlocks.NPC_MARKER.defaultBlockState().setValue(NpcMarkerBlock.NPC, NpcMarkerBlock.Npc.WAITRESS));
		((NpcMarkerBlockEntity) level.getBlockEntity(pos)).configure(100, 90, "minecraft:red_carpet");
		helper.succeedWhen(() -> {
			if (!"minecraft:red_carpet".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString())) {
				throw new GameTestAssertException(Component.literal("marker still there"), 0);
			}
			List<WaitressEntity> found = level.getEntitiesOfClass(WaitressEntity.class, new AABB(pos).inflate(2));
			if (found.isEmpty()) {
				helper.fail("The marker did not put a waitress there");
			}
			found.forEach(net.minecraft.world.entity.Entity::discard);
		});
	}
}
