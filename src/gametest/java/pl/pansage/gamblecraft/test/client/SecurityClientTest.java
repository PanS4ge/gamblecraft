package pl.pansage.gamblecraft.test.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;
import pl.pansage.gamblecraft.entity.CasinoGuardEntity;
import pl.pansage.gamblecraft.item.ShockwavePistolItem;
import pl.pansage.gamblecraft.registry.ModEntities;
import pl.pansage.gamblecraft.registry.ModItems;

/** A guard close up, charging and firing the shockwave at a player up a pillar; the pistol fired; the cap worn. */
public class SecurityClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof AgeGateScreen)) {
			context.clickScreenButton("gamblecraft.age_gate.accept");
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			BlockPos feet = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				player.setGameMode(GameType.SURVIVAL);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 300, 4, false, false));
				return player.blockPosition();
			});
			BlockPos guardAt = feet.offset(0, 0, 5);
			int guardId = world.getServer().computeOnServer(server -> {
				ServerLevel level = player(server).level();
				CasinoGuardEntity guard = ModEntities.CASINO_GUARD.create(level, EntitySpawnReason.TRIGGERED);
				guard.snapTo(guardAt.getX() + 0.5, guardAt.getY(), guardAt.getZ() + 0.5, 180, 0);
				level.addFreshEntity(guard);
				player(server).connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0, -8);
				return guard.getId();
			});
			context.waitTicks(30);
			context.takeScreenshot("guard_portrait");

			// up a pillar out of the guard's reach: the charge, then the shot
			BlockPos pillar = feet.offset(0, 0, 13);
			world.getServer().runOnServer(server -> {
				ServerLevel level = player(server).level();
				for (int i = 0; i < 4; i++) {
					level.setBlockAndUpdate(pillar.above(i), Blocks.COBBLESTONE.defaultBlockState());
				}
				player(server).connection.teleport(pillar.getX() + 0.5, pillar.getY() + 4, pillar.getZ() + 0.5, 180, 32);
				guard(server, guardId).setTarget(player(server));
			});
			for (int i = 0; i < 60 && world.getServer().computeOnServer(server -> guard(server, guardId).action()) != CasinoGuardEntity.ACTION_CHARGE; i++) {
				context.waitTicks(2);
			}
			context.waitTicks(18);
			context.takeScreenshot("guard_charging");
			for (int i = 0; i < 30 && world.getServer().computeOnServer(server -> guard(server, guardId).shockwaves()) == 0; i++) {
				context.waitTicks(1);
			}
			context.waitTicks(2);
			context.takeScreenshot("guard_shockwave");

			// the pistol, against a zombie in mid-air, and the cap on the player's head
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				guard(server, guardId).discard();
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SECURITY_CAP));
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHOCKWAVE_PISTOL));
				player.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() - 10.5, 0, -14);
			});
			context.waitTicks(20);
			context.takeScreenshot("pistol_held");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = player.level();
				Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
				zombie.snapTo(player.getX(), player.getY() + 3.5, player.getZ() + 8, 180, 0);
				level.addFreshEntity(zombie);
				player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, zombie.position().add(0, 1, 0));
				player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
			});
			context.waitTicks(2);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				ShockwavePistolItem.fire(player.level(), player, player.getMainHandItem(), InteractionHand.MAIN_HAND);
			});
			context.waitTicks(3);
			context.takeScreenshot("pistol_shot");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(30);
			context.takeScreenshot("cap_worn");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static CasinoGuardEntity guard(MinecraftServer server, int id) {
		return (CasinoGuardEntity) player(server).level().getEntity(id);
	}
}
