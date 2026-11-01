package pl.pansage.gamblecraft.test;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.entity.CasinoGuardEntity;
import pl.pansage.gamblecraft.game.CasinoRegion;
import pl.pansage.gamblecraft.game.CasinoRegion.Casino;
import pl.pansage.gamblecraft.game.CasinoSecurity;
import pl.pansage.gamblecraft.item.ShockwavePistolItem;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.registry.ModEntities;
import pl.pansage.gamblecraft.registry.ModItems;

public class SecurityGameTests {
	/** Game tests cannot generate the casino, so each test declares its own plot one. */
	private static Casino casino(GameTestHelper helper, BlockPos post) {
		BlockPos min = helper.absolutePos(BlockPos.ZERO).offset(-1, -1, -1);
		BlockPos max = helper.absolutePos(new BlockPos(8, 10, 8));
		BoundingBox box = BoundingBox.fromCorners(min, max);
		return CasinoRegion.declare("test_" + min.asLong(), box, post == null ? null : helper.absolutePos(post));
	}

	private static void tidy(GameTestHelper helper, Casino casino) {
		CasinoSecurity.get(helper.getLevel().getServer()).end(helper.getLevel(), casino.key());
		CasinoRegion.forget(casino);
	}

	/** A survival player of their own (the mock player always counts as creative, which security leaves alone). */
	private static ServerPlayer survivor(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "offender"));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(at));
		player.snapTo(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	/** The guards on this test's casino (neighbouring tests have their own). */
	private static List<CasinoGuardEntity> guards(GameTestHelper helper, Casino casino) {
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24);
		return helper.getLevel().getEntitiesOfClass(CasinoGuardEntity.class, area, g -> g.isAlive() && g.casino().equals(casino.key()));
	}

	/** Breaking a block: it goes without dropping, two guards turn out, and the alarm's end puts the block back. */
	@GameTest(maxTicks = 40)
	public void brokenBlockComesBackAfterTheAlarm(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Casino casino = casino(helper, new BlockPos(6, 1, 6));
		BlockPos gold = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlockAndUpdate(gold, Blocks.GOLD_BLOCK.defaultBlockState());
		ServerPlayer player = survivor(helper, new BlockPos(2, 1, 4));
		player.gameMode.destroyBlock(gold);
		CasinoSecurity security = CasinoSecurity.get(level.getServer());
		if (!level.getBlockState(gold).isAir()) {
			helper.fail("The block was not broken");
			return;
		}
		if (!level.getEntitiesOfClass(ItemEntity.class, new AABB(gold).inflate(3)).isEmpty()) {
			helper.fail("A block broken in the casino dropped an item");
			return;
		}
		CasinoSecurity.Alarm alarm = security.alarm(casino.key()).orElse(null);
		if (alarm == null || alarm.offences() != 1 || !alarm.offenders().contains(player.getUUID()) || !security.banned(level, player)) {
			helper.fail("No alarm, offence or ban");
			return;
		}
		if (guards(helper, casino).size() != 2) {
			helper.fail("Expected two guards, found " + guards(helper, casino).size());
			return;
		}
		security.end(level, casino.key());
		if (!level.getBlockState(gold).is(Blocks.GOLD_BLOCK)) {
			helper.fail("The gold block did not come back");
			return;
		}
		if (guards(helper, casino).stream().anyMatch(g -> !g.standingDown())) {
			helper.fail("Guards still on duty after the alarm");
			return;
		}
		tidy(helper, casino);
		helper.succeed();
	}

	/** Every further offence calls one more guard, up to six. */
	@GameTest(maxTicks = 40)
	public void moreOffencesCallMoreGuards(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Casino casino = casino(helper, new BlockPos(6, 1, 6));
		CasinoSecurity security = CasinoSecurity.get(level.getServer());
		for (int i = 0; i < 8; i++) {
			// different players, so the cooldown between one player's offences does not merge them
			security.offence(level, casino, survivor(helper, new BlockPos(2, 1, 2)), "assault");
		}
		int count = guards(helper, casino).size();
		tidy(helper, casino);
		if (count != CasinoSecurity.MAX_GUARDS) {
			helper.fail("Expected " + CasinoSecurity.MAX_GUARDS + " guards after eight offences, found " + count);
			return;
		}
		helper.succeed();
	}

	/** A block built inside counts as an offence and disappears when the alarm is over. */
	@GameTest(maxTicks = 40)
	public void builtBlockVanishesAfterTheAlarm(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Casino casino = casino(helper, null);
		BlockPos floor = helper.absolutePos(new BlockPos(3, 1, 3));
		level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		ServerPlayer player = survivor(helper, new BlockPos(3, 2, 5));
		ItemStack planks = new ItemStack(Items.OAK_PLANKS, 4);
		player.setItemInHand(InteractionHand.MAIN_HAND, planks);
		planks.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
		BlockPos built = floor.above();
		CasinoSecurity security = CasinoSecurity.get(level.getServer());
		if (!level.getBlockState(built).is(Blocks.OAK_PLANKS)) {
			helper.fail("The planks were not placed");
			return;
		}
		if (security.alarm(casino.key()).map(a -> !a.placed().contains(built) || a.offences() != 1).orElse(true)) {
			helper.fail("Placing a block in the casino did not raise the alarm");
			return;
		}
		security.end(level, casino.key());
		boolean gone = level.getBlockState(built).isAir();
		tidy(helper, casino);
		if (!gone) {
			helper.fail("The built block is still there after the alarm");
			return;
		}
		helper.succeed();
	}

	/** A player up a pillar they built is out of reach: a guard charges the shockwave and the pillar crumbles. */
	@GameTest(maxTicks = 300)
	public void shockwaveKnocksDownAPillar(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Casino casino = casino(helper, new BlockPos(1, 1, 1));
		CasinoSecurity security = CasinoSecurity.get(level.getServer());
		BlockPos base = helper.absolutePos(new BlockPos(5, 1, 5));
		// three blocks: high enough to be out of reach, low enough to stay under the test area's barrier ceiling
		for (int i = 0; i < 3; i++) {
			level.setBlockAndUpdate(base.above(i), Blocks.COBBLESTONE.defaultBlockState());
			security.recordPlaced(level, casino, base.above(i), Blocks.AIR.defaultBlockState());
		}
		ServerPlayer climber = survivor(helper, new BlockPos(5, 4, 5));
		security.offence(level, casino, climber, "build");
		List<CasinoGuardEntity> guards = guards(helper, casino);
		if (guards.isEmpty()) {
			helper.fail("No guards came");
			return;
		}
		for (CasinoGuardEntity guard : guards) {
			guard.setTarget(climber);
		}
		helper.succeedWhen(() -> {
			if (guards.stream().noneMatch(g -> g.shockwaves() > 0)) {
				throw new GameTestAssertException(Component.literal("no shockwave yet: " + guards.getFirst().shockwaveState()), 0);
			}
			for (int i = 0; i < 3; i++) {
				if (!level.getBlockState(base.above(i)).isAir()) {
					helper.fail("Pillar block " + i + " survived the shockwave");
				}
			}
			tidy(helper, casino);
		});
	}

	/** The pistol hits what is in front of it, slams an airborne target down, wears and goes on cooldown. */
	@GameTest(maxTicks = 20)
	public void pistolSlamsAFlyingTargetDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer shooter = survivor(helper, new BlockPos(1, 1, 1));
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
		Vec3 up = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 5, 7)));
		zombie.snapTo(up.x, up.y, up.z, 0, 0);
		zombie.setNoAi(true);
		level.addFreshEntity(zombie);
		shooter.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.position().add(0, 1, 0));
		ItemStack pistol = new ItemStack(ModItems.SHOCKWAVE_PISTOL);
		shooter.setItemInHand(InteractionHand.MAIN_HAND, pistol);
		pistol.use(level, shooter, InteractionHand.MAIN_HAND);
		if (zombie.getHealth() >= zombie.getMaxHealth()) {
			helper.fail("The shot did no damage");
		} else if (zombie.getDeltaMovement().y > -1.5) {
			helper.fail("The target was not slammed down: " + zombie.getDeltaMovement());
		} else if (pistol.getDamageValue() != 1 || !shooter.getCooldowns().isOnCooldown(pistol)) {
			helper.fail("The pistol did not wear or cool down");
		} else {
			helper.succeed();
		}
	}

	/** A guard drops the cap (with Protection III) and the shockwave pistol. */
	@GameTest(maxTicks = 20)
	public void guardDropsCapAndPistol(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CasinoGuardEntity guard = ModEntities.CASINO_GUARD.create(level, EntitySpawnReason.TRIGGERED);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 3)));
		guard.snapTo(at.x, at.y, at.z, 0, 0);
		level.addFreshEntity(guard);
		guard.hurtServer(level, level.damageSources().playerAttack(survivor(helper, new BlockPos(1, 1, 1))), 10000);
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(at)).inflate(4));
		ItemStack cap = drops.stream().map(ItemEntity::getItem).filter(s -> s.is(ModItems.SECURITY_CAP)).findFirst().orElse(ItemStack.EMPTY);
		boolean pistol = drops.stream().anyMatch(d -> d.getItem().is(ModItems.SHOCKWAVE_PISTOL));
		var protection = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
		if (cap.isEmpty() || !pistol) {
			helper.fail("Drops were " + drops.stream().map(d -> d.getItem().toString()).toList());
		} else if (EnchantmentHelper.getItemEnchantmentLevel(protection, cap) != 3) {
			ItemStack probe = new ItemStack(ModItems.SECURITY_CAP);
			EnchantmentHelper.updateEnchantments(probe, m -> m.set(protection, 3));
			helper.fail("The cap is not enchanted with Protection III: " + cap.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS)
					+ " probe " + probe.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS) + " drops " + drops.size());
		} else if (cap.getMaxDamage() != 363) {
			helper.fail("The cap lasts " + cap.getMaxDamage() + " instead of a diamond helmet's 363");
		} else {
			helper.succeed();
		}
	}

	/** An offender is banned for a day; for the first minute of an alarm the doors let nobody in. */
	@GameTest(maxTicks = 40)
	public void bannedOffenderFindsTheDoorShut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Casino casino = casino(helper, null);
		BlockPos door = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockState lower = ModBlocks.CASINO_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		level.setBlock(door, lower, 3);
		level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
		ServerPlayer offender = survivor(helper, new BlockPos(1, 1, 3));
		ServerPlayer guest = survivor(helper, new BlockPos(2, 1, 3));
		for (ServerPlayer player : List.of(offender, guest)) {
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.SUIT_JACKET));
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SUIT_TROUSERS));
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SUIT_SHOES));
		}
		CasinoSecurity security = CasinoSecurity.get(level.getServer());
		security.offence(level, casino, offender, "assault");
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(door), Direction.NORTH, door, false);
		level.getBlockState(door).useWithoutItem(level, guest, hit);
		if (level.getBlockState(door).getValue(DoorBlock.OPEN)) {
			helper.fail("The door opened during the lockdown");
			return;
		}
		security.end(level, casino.key());
		level.getBlockState(door).useWithoutItem(level, offender, hit);
		if (level.getBlockState(door).getValue(DoorBlock.OPEN)) {
			helper.fail("The door opened for a banned player");
			return;
		}
		level.getBlockState(door).useWithoutItem(level, guest, hit);
		boolean open = level.getBlockState(door).getValue(DoorBlock.OPEN);
		tidy(helper, casino);
		if (!open) {
			helper.fail("The door stayed shut for a suited guest after the lockdown");
			return;
		}
		helper.succeed();
	}
}
