package pl.pansage.gamblecraft.test.client;

import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.block.CasinoDoorBlock;
import pl.pansage.gamblecraft.block.RouletteWheelBlock;
import pl.pansage.gamblecraft.block.SlotMachineBlock;
import pl.pansage.gamblecraft.block.entity.RouletteBlockEntity;
import pl.pansage.gamblecraft.block.entity.SlotMachineBlockEntity;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;
import pl.pansage.gamblecraft.core.table.TableTypes.Wager;
import pl.pansage.gamblecraft.entity.BouncerEntity;
import pl.pansage.gamblecraft.entity.CashierEntity;
import pl.pansage.gamblecraft.entity.CasinoDealerEntity;
import pl.pansage.gamblecraft.entity.CasinoGuardEntity;
import pl.pansage.gamblecraft.game.CasinoRegion;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.CasinoSecurity;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.registry.ModItems;
import pl.pansage.gamblecraft.registry.ModVillagers;

/**
 * The casino in a real world: found like /locate does, the doorman turning away a guest in plain clothes, the
 * door opening for a suit, the lobby, a croupier's table, and a guest walked out after taking the suit off.
 */
public class CasinoClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof AgeGateScreen)) {
			context.clickScreenButton("gamblecraft.age_gate.accept");
		}
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(settings -> {
			settings.setSeed("gamblecraft");
			settings.setWorldType(settings.getNormalPresetList().getFirst());
		}).create()) {
			BlockPos found = world.getServer().computeOnServer(server ->
					server.overworld().findNearestMapStructure(CasinoRegion.CASINO_TAG, BlockPos.ZERO, 100, false));
			if (found == null) {
				throw new AssertionError("No casino within 100 chunks");
			}
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.setGameMode(GameType.CREATIVE);
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				player.connection.teleport(found.getX() + 0.5, 150, found.getZ() + 0.5, 0, 90);
			});
			int bouncerId = -1;
			for (int i = 0; i < 60 && bouncerId < 0; i++) {
				context.waitTicks(10);
				bouncerId = world.getServer().computeOnServer(server -> {
					List<BouncerEntity> bouncers = server.overworld().getEntitiesOfClass(BouncerEntity.class, new AABB(found).inflate(96, 200, 96));
					return bouncers.isEmpty() || bouncers.getFirst().home() == null ? -1 : bouncers.getFirst().getId();
				});
			}
			if (bouncerId < 0) {
				throw new AssertionError("Casino at " + found + " has no doorman");
			}
			int id = bouncerId;

			// The facade from the street, from above the plaza (the land beyond it may be higher).
			look(world, id, -16, 9, 0, 14);
			context.waitTicks(40);
			context.takeScreenshot("casino_exterior");

			// In plain clothes, next to the doorman: shoved back.
			double before = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				player.setGameMode(GameType.SURVIVAL);
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
				BouncerEntity bouncer = bouncer(server, id);
				Vec3 at = Vec3.atBottomCenterOf(bouncer.home()).add(bouncer.inward().scale(-1.2));
				player.connection.teleport(at.x, at.y, at.z, yawOf(bouncer.inward()), 10);
				return player.distanceTo(bouncer);
			});
			context.waitTicks(12);
			context.takeScreenshot("casino_turned_back");
			double after = world.getServer().computeOnServer(server -> (double) player(server).distanceTo(bouncer(server, id)));
			if (after <= before + 0.5) {
				throw new AssertionError("The doorman did not turn the guest back: " + before + " -> " + after);
			}

			// The door: shut for plain clothes, open for a suit, shut again by itself.
			BlockPos door = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				BouncerEntity bouncer = bouncer(server, id);
				for (BlockPos pos : BlockPos.betweenClosed(bouncer.home().offset(-3, -1, -3), bouncer.home().offset(3, 2, 3))) {
					if (level.getBlockState(pos).getBlock() instanceof CasinoDoorBlock && level.getBlockState(pos).getValue(DoorBlock.HALF).name().equals("LOWER")) {
						return pos.immutable();
					}
				}
				throw new AssertionError("No casino door next to the doorman");
			});
			boolean openedWithoutSuit = world.getServer().computeOnServer(server -> useDoor(server, door));
			if (openedWithoutSuit) {
				throw new AssertionError("The door opened without a suit");
			}
			boolean openedWithSuit = world.getServer().computeOnServer(server -> {
				suit(player(server), true);
				return useDoor(server, door);
			});
			if (!openedWithSuit) {
				throw new AssertionError("The door stayed shut for a suit");
			}
			look(world, id, -4, 1.62, 0, 5);
			context.waitTicks(5);
			context.takeScreenshot("casino_door_open");
			context.waitTicks(60);
			if (world.getServer().computeOnServer(server -> server.overworld().getBlockState(door).getValue(DoorBlock.OPEN))) {
				throw new AssertionError("The door did not close itself");
			}

			// Inside: the lobby with the cashier, then a croupier's table.
			look(world, id, 4, 1.62, 0, 8);
			context.waitTicks(20);
			context.takeScreenshot("casino_lobby");
			boolean cashier = world.getServer().computeOnServer(server ->
					!server.overworld().getEntitiesOfClass(CashierEntity.class, new AABB(bouncer(server, id).blockPosition()).inflate(40)).isEmpty());
			if (!cashier) {
				throw new AssertionError("No cashier in the casino");
			}
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				CasinoDealerEntity dealer = server.overworld().getEntitiesOfClass(CasinoDealerEntity.class,
						new AABB(bouncer(server, id).blockPosition()).inflate(48)).getFirst();
				Direction towardsFelt = Direction.fromYRot(dealer.getYRot());
				Vec3 at = Vec3.atBottomCenterOf(dealer.felt()).add(towardsFelt.getStepX() * 2.0, 0, towardsFelt.getStepZ() * 2.0);
				player.connection.teleport(at.x, at.y, at.z, towardsFelt.getOpposite().toYRot(), 30);
				player.getInventory().add(new ItemStack(ModItems.CHIPS[3], 3));
				player.getInventory().add(new ItemStack(ModItems.CHIPS[2], 4));
				dealer.openFor(player);
				dealer.table().session().bet(player, Wager.of(25));
			});
			context.waitTicks(60);
			context.takeScreenshot("casino_table");
			context.setScreen(() -> null);

			// The slot machines and the roulette tables across the hall.
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos near = bouncer(server, id).blockPosition();
				BlockPos machine = BlockPos.betweenClosedStream(near.offset(-40, -2, -40), near.offset(40, 3, 40))
						.filter(p -> level.getBlockEntity(p) instanceof SlotMachineBlockEntity).map(BlockPos::immutable).findFirst().orElseThrow();
				Direction front = level.getBlockState(machine).getValue(SlotMachineBlock.FACING);
				Vec3 at = Vec3.atBottomCenterOf(machine.relative(front, 3)).add(0, 0.5, 0);
				player(server).connection.teleport(at.x, at.y, at.z, front.getOpposite().toYRot() + 25, 10);
			});
			context.waitTicks(20);
			context.takeScreenshot("casino_slots");
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos near = bouncer(server, id).blockPosition();
				BlockPos wheel = BlockPos.betweenClosedStream(near.offset(-40, -2, -40), near.offset(40, 3, 40))
						.filter(p -> level.getBlockEntity(p) instanceof RouletteBlockEntity).map(BlockPos::immutable).findFirst().orElseThrow();
				Direction along = level.getBlockState(wheel).getValue(RouletteWheelBlock.FACING);
				Direction players = along.getClockWise();
				Vec3 at = Vec3.atBottomCenterOf(wheel.relative(along, 2).relative(players, 2)).add(0, 1.0, 0);
				player(server).connection.teleport(at.x, at.y, at.z, players.getOpposite().toYRot(), 40);
			});
			context.waitTicks(20);
			context.takeScreenshot("casino_roulette");

			// The arcade down the middle of the hall: dice, the crash pad under its shaft, the wheel between the plinko boards.
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos near = bouncer(server, id).blockPosition();
				BlockPos wheel = BlockPos.betweenClosedStream(near.offset(-45, -2, -45), near.offset(45, 3, 45))
						.filter(p -> level.getBlockEntity(p) instanceof pl.pansage.gamblecraft.block.entity.FortuneWheelBlockEntity)
						.map(BlockPos::immutable).findFirst().orElseThrow();
				Direction front = level.getBlockState(wheel).getValue(pl.pansage.gamblecraft.block.ArcadeBlock.FACING);
				Vec3 at = Vec3.atBottomCenterOf(wheel.relative(front, 16)).add(0, 1.2, 0);
				player(server).connection.teleport(at.x, at.y, at.z, front.getOpposite().toYRot(), 4);
			});
			context.waitTicks(20);
			context.takeScreenshot("casino_arcade");
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos near = bouncer(server, id).blockPosition();
				BlockPos kiosk = BlockPos.betweenClosedStream(near.offset(-45, -2, -45), near.offset(45, 3, 45))
						.filter(p -> level.getBlockState(p).is(ModBlocks.SCRATCH_KIOSK)).map(BlockPos::immutable).findFirst().orElseThrow();
				Direction front = level.getBlockState(kiosk).getValue(pl.pansage.gamblecraft.block.ScratchKioskBlock.FACING);
				Vec3 at = Vec3.atBottomCenterOf(kiosk.relative(front, 3));
				player(server).connection.teleport(at.x, at.y, at.z, front.getOpposite().toYRot(), 18);
			});
			context.waitTicks(20);
			context.takeScreenshot("casino_kiosk");

			// Security: the guards' room behind the hall; then a broken block sets off the alarm and the guards turn out.
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 300, 4, false, false));
				BouncerEntity bouncer = bouncer(server, id);
				BlockPos post = CasinoRegion.find(server.overworld(), bouncer.blockPosition()).orElseThrow().guardPost();
				Vec3 in = bouncer.inward();
				Vec3 at = Vec3.atBottomCenterOf(post).add(in.scale(-2.5));
				player.connection.teleport(at.x, at.y, at.z, yawOf(in), 12);
			});
			context.waitTicks(20);
			context.takeScreenshot("casino_security_room");
			BlockPos broken = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				BouncerEntity bouncer = bouncer(server, id);
				BlockPos post = CasinoRegion.find(server.overworld(), bouncer.blockPosition()).orElseThrow().guardPost();
				Vec3 in = bouncer.inward();
				// out on the arcade carpet, clear of the card tables, looking at the security room's door
				Vec3 side = new Vec3(in.z, 0, -in.x);
				Vec3 at = Vec3.atBottomCenterOf(post).add(in.scale(-10)).add(side.scale(4));
				player.connection.teleport(at.x, at.y, at.z, yawOf(in) + 15, 6);
				BlockPos carpet = BlockPos.containing(Vec3.atBottomCenterOf(post).add(in.scale(-8)).add(side.scale(4)));
				player.gameMode.destroyBlock(carpet);
				return carpet;
			});
			context.waitTicks(40);
			context.takeScreenshot("casino_alarm");
			// up a pillar out of reach: a guard charges the shockwave and knocks it down
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				CasinoRegion.Casino casino = CasinoRegion.find(level, player.blockPosition()).orElseThrow();
				BlockPos foot = player.blockPosition();
				for (int i = 0; i < 3; i++) {
					CasinoSecurity.get(server).recordPlaced(level, casino, foot.above(i), level.getBlockState(foot.above(i)));
					level.setBlockAndUpdate(foot.above(i), Blocks.COBBLESTONE.defaultBlockState());
				}
				CasinoGuardEntity guard = level.getEntitiesOfClass(CasinoGuardEntity.class, new AABB(foot).inflate(30)).getFirst();
				Vec3 to = guard.position().subtract(player.position());
				player.connection.teleport(foot.getX() + 0.5, foot.getY() + 3, foot.getZ() + 0.5, yawOf(to), 35);
			});
			for (int i = 0; i < 100 && !world.getServer().computeOnServer(server -> guards(server, broken).stream()
					.anyMatch(g -> g.action() == CasinoGuardEntity.ACTION_CHARGE)); i++) {
				context.waitTicks(2);
			}
			context.waitTicks(16);
			context.takeScreenshot("casino_guard_charging");
			for (int i = 0; i < 40 && world.getServer().computeOnServer(server -> guards(server, broken).stream().allMatch(g -> g.shockwaves() == 0)); i++) {
				context.waitTicks(1);
			}
			context.waitTicks(2);
			context.takeScreenshot("casino_shockwave");
			// all clear: the carpet is back, the pillar gone, the lamps off and the guards on their way back
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				CasinoSecurity security = CasinoSecurity.get(server);
				security.pardon(player.getUUID());
				security.end(level, CasinoRegion.find(level, broken).orElseThrow().key());
				BouncerEntity bouncer = bouncer(server, id);
				Vec3 in = bouncer.inward();
				Vec3 at = Vec3.atBottomCenterOf(broken).add(in.scale(-2));
				player.connection.teleport(at.x, at.y, at.z, yawOf(in), 10);
			});
			context.waitTicks(30);
			context.takeScreenshot("casino_all_clear");

			// The casino's people: the bartender and his menu, a gambler at the slots, the waitress, the loan shark, the VIP room.
			world.getServer().runOnServer(server -> ChipCurrency.INSTANCE.deposit(player(server), 6000));
			for (String who : List.of("bartender", "addicted_gambler", "waitress", "pickpocket", "loan_shark", "casino_boss")) {
				boolean seen = world.getServer().computeOnServer(server -> {
					var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(pl.pansage.gamblecraft.GambleCraft.id(who));
					ServerLevel level = server.overworld();
					var people = level.getEntities(type, new AABB(bouncer(server, id).blockPosition()).inflate(64), e -> e.isAlive());
					if (people.isEmpty()) {
						return false;
					}
					var person = people.getFirst();
					// in front of each (the bartender across his counter); the gambler from behind, at his machine
					double distance = switch (who) {
						case "casino_boss" -> 3.5;
						case "bartender" -> 3.6;
						case "addicted_gambler" -> -2.4;
						default -> 2.6;
					};
					Vec3 front = Vec3.directionFromRotation(0, person.getYRot()).scale(distance);
					Vec3 at = person.position().add(front);
					player(server).connection.teleport(at.x, at.y, at.z, yawOf(person.position().subtract(at)), who.equals("casino_boss") ? 12 : 8);
					return true;
				});
				if (!seen) {
					continue;
				}
				context.waitTicks(20);
				context.takeScreenshot("people_" + who);
				if (who.equals("bartender") || who.equals("loan_shark")) {
					world.getServer().runOnServer(server -> {
						var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(pl.pansage.gamblecraft.GambleCraft.id(who));
						var person = server.overworld().getEntities(type, new AABB(player(server).blockPosition()).inflate(8), e -> e.isAlive()).getFirst();
						pl.pansage.gamblecraft.game.ChipShop.open(player(server), person);
					});
					context.waitTicks(5);
					context.takeScreenshot("people_" + who + "_menu");
					context.setScreen(() -> null);
				}
			}
			// a VIP card from the cashier's desk, and the boss's duel
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				var desk = level.getEntitiesOfClass(pl.pansage.gamblecraft.entity.CashierEntity.class,
						new AABB(bouncer(server, id).blockPosition()).inflate(64)).getFirst();
				Vec3 at = desk.position().add(Vec3.directionFromRotation(0, desk.getYRot()).scale(3));
				player(server).connection.teleport(at.x, at.y, at.z, desk.getYRot() + 180, 10);
				pl.pansage.gamblecraft.game.ChipShop.open(player(server), desk);
			});
			context.waitTicks(10);
			context.takeScreenshot("people_vip_desk");
			context.setScreen(() -> null);
			boolean duel = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				var boss = level.getEntitiesOfClass(pl.pansage.gamblecraft.entity.CasinoBossEntity.class,
						new AABB(bouncer(server, id).blockPosition()).inflate(64));
				if (boss.isEmpty()) {
					return false;
				}
				ServerPlayer player = player(server);
				player.getInventory().add(new ItemStack(ModItems.VIP_CARD));
				Vec3 at = boss.getFirst().position().add(Vec3.directionFromRotation(0, boss.getFirst().getYRot()).scale(2.2));
				player.connection.teleport(at.x, at.y, at.z, boss.getFirst().getYRot() + 180, 30);
				boss.getFirst().openFor(player);
				return true;
			});
			if (duel) {
				context.waitTicks(15);
				var table = world.getServer().computeOnServer(server -> server.overworld().getEntitiesOfClass(pl.pansage.gamblecraft.entity.CardTableEntity.class,
						new AABB(player(server).blockPosition()).inflate(6)).stream().findFirst().map(t -> t.getId()).orElse(-1));
				if (table >= 0) {
					world.getServer().runOnServer(server -> {
						var t = (pl.pansage.gamblecraft.entity.CardTableEntity) server.overworld().getEntity(table);
						t.session().buyIn(player(server), 2000);
					});
					context.waitTicks(150);
				}
				context.takeScreenshot("people_boss_duel");
				context.setScreen(() -> null);
			}

			// Taking the suit off inside: walked out to the pavement.
			world.getServer().runOnServer(server -> suit(player(server), false));
			context.waitTicks(25);
			double depth = world.getServer().computeOnServer(server -> {
				BouncerEntity bouncer = bouncer(server, id);
				return player(server).position().subtract(Vec3.atBottomCenterOf(bouncer.home())).dot(bouncer.inward());
			});
			if (depth > 0) {
				throw new AssertionError("Guest without a suit was not walked out (depth " + depth + ")");
			}
			context.takeScreenshot("casino_escorted");

			// The tailor's boutique on the plaza, with a suit on show.
			boolean boutique = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				AABB around = new AABB(bouncer(server, id).blockPosition()).inflate(32);
				Villager tailor = level.getEntitiesOfClass(Villager.class, around, v -> v.getVillagerData().profession().is(ModVillagers.TAILOR))
						.stream().findFirst().orElse(null);
				ArmorStand stand = level.getEntitiesOfClass(ArmorStand.class, around).stream().findFirst().orElse(null);
				if (tailor == null || stand == null || !stand.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.SUIT_JACKET)) {
					return false;
				}
				// from the plaza, through the shop door: the suit on its stand and the tailor behind it
				BouncerEntity bouncer = bouncer(server, id);
				Vec3 in = bouncer.inward();
				Vec3 towardsShop = new Vec3(-in.z, 0, in.x);
				Vec3 from = Vec3.atBottomCenterOf(bouncer.home()).add(in.scale(-5.5)).add(towardsShop.scale(5.5));
				player(server).connection.teleport(from.x, from.y, from.z, yawOf(towardsShop), 12);
				return true;
			});
			if (!boutique) {
				throw new AssertionError("The boutique has no tailor or no suit on show");
			}
			context.waitTicks(20);
			context.takeScreenshot("casino_boutique");

			// Out on the road a big winner is mugged by a gang dressed and armed as they are; a debtor gets a visit.
			BlockPos road = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 300, 4, false, false));
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				ChipCurrency.INSTANCE.deposit(player, 3000);
				BouncerEntity bouncer = bouncer(server, id);
				Vec3 out = Vec3.atBottomCenterOf(bouncer.home()).add(bouncer.inward().scale(-30));
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(out.x), (int) Math.floor(out.z));
				player.connection.teleport(out.x, ground, out.z, yawOf(bouncer.inward().scale(-1)), 5);
				return BlockPos.containing(out.x, ground, out.z);
			});
			context.waitTicks(20);
			world.getServer().runOnServer(server -> pl.pansage.gamblecraft.game.Muggings.ambush(server.overworld(), player(server), 6000));
			context.waitTicks(30);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				var gang = server.overworld().getEntitiesOfClass(pl.pansage.gamblecraft.entity.MuggerEntity.class, new AABB(road).inflate(30));
				if (!gang.isEmpty()) {
					Vec3 to = gang.getFirst().position().subtract(player.position());
					player.connection.teleport(player.getX(), player.getY(), player.getZ(), yawOf(to), 8);
				}
			});
			context.waitTicks(10);
			context.takeScreenshot("people_muggers");
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				level.getEntitiesOfClass(pl.pansage.gamblecraft.entity.MuggerEntity.class, new AABB(road).inflate(40)).forEach(net.minecraft.world.entity.Entity::discard);
				pl.pansage.gamblecraft.game.LoanData.sendCollectors(level, player(server), 900);
			});
			context.waitTicks(30);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				var crew = server.overworld().getEntitiesOfClass(pl.pansage.gamblecraft.entity.DebtCollectorEntity.class, new AABB(road).inflate(30));
				if (!crew.isEmpty()) {
					Vec3 to = crew.getFirst().position().subtract(player.position());
					player.connection.teleport(player.getX(), player.getY(), player.getZ(), yawOf(to), 8);
				}
			});
			context.waitTicks(10);
			context.takeScreenshot("people_collectors");
		}
	}

	private static List<CasinoGuardEntity> guards(MinecraftServer server, BlockPos near) {
		return server.overworld().getEntitiesOfClass(CasinoGuardEntity.class, new AABB(near).inflate(40));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static BouncerEntity bouncer(MinecraftServer server, int id) {
		return (BouncerEntity) server.overworld().getEntity(id);
	}

	/** Yaw looking along a horizontal direction. */
	private static float yawOf(Vec3 dir) {
		return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
	}

	/** Puts the player {@code along} blocks into the casino (negative: out on the street), at ground level, facing in. */
	private static void look(TestSingleplayerContext world, int id, double along, double up, double side, float pitch) {
		world.getServer().runOnServer(server -> {
			BouncerEntity bouncer = bouncer(server, id);
			Vec3 in = bouncer.inward();
			Vec3 at = Vec3.atBottomCenterOf(bouncer.home()).add(in.scale(along)).add(-in.z * side, 0, in.x * side);
			int ground = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
			double y = along < -10.5 ? Math.max(ground, bouncer.home().getY()) : bouncer.home().getY();
			player(server).connection.teleport(at.x, y + up - 1.62, at.z, yawOf(in), pitch);
		});
	}

	private static boolean useDoor(MinecraftServer server, BlockPos door) {
		ServerLevel level = server.overworld();
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(door), Direction.UP, door, false);
		level.getBlockState(door).useWithoutItem(level, player(server), hit);
		return level.getBlockState(door).getValue(DoorBlock.OPEN);
	}

	private static void suit(ServerPlayer player, boolean on) {
		player.setItemSlot(EquipmentSlot.CHEST, on ? new ItemStack(ModItems.SUIT_JACKET) : ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.LEGS, on ? new ItemStack(ModItems.SUIT_TROUSERS) : ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.FEET, on ? new ItemStack(ModItems.SUIT_SHOES) : ItemStack.EMPTY);
	}
}
