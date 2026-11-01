package pl.pansage.gamblecraft.test.client;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.client.scratch.ScratchGeometry;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;
import pl.pansage.gamblecraft.client.screen.ScratchTableScreen;
import pl.pansage.gamblecraft.core.casino.ScratchFoil;
import pl.pansage.gamblecraft.entity.ScratchCardEntity;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.item.ScratchData;
import pl.pansage.gamblecraft.network.ScratchPayloads.ScratchRub;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * Scratching on a block: the card lies on the block, the camera looks down, the player's hand follows the mouse
 * and rubbing with the real mouse uncovers the same foil on the server. Then somebody else scratches a card next
 * to us and we watch their strokes arrive.
 */
public class ScratchBlockClientTest implements FabricClientGameTest {
	private static final int LEFT_BUTTON = 0;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof AgeGateScreen)) {
			context.clickScreenButton("gamblecraft.age_gate.accept");
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			// A village bell behind us, a table block in front, a winning silver card in hand.
			int cardId = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos feet = player.blockPosition();
				player.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0, 30);
				level.setBlock(feet.offset(0, 0, -4), Blocks.BELL.defaultBlockState(), 3);
				BlockPos table = feet.offset(0, 0, 1);
				level.setBlock(table, Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
				level.setBlock(table.offset(1, 0, 0), Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
				ItemStack card = winningCard(level, feet.offset(0, 0, -4));
				player.setItemInHand(InteractionHand.MAIN_HAND, card);
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(table).add(0, 0.5, 0), Direction.UP, table, false);
				card.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
				List<ScratchCardEntity> cards = level.getEntitiesOfClass(ScratchCardEntity.class, new AABB(table).inflate(1));
				if (cards.size() != 1 || !player.getMainHandItem().isEmpty()) {
					throw new AssertionError("Card was not laid on the block");
				}
				return cards.getFirst().getId();
			});
			context.waitForScreen(ScratchTableScreen.class);
			context.waitTicks(16);
			context.takeScreenshot("scratch_block_fresh");

			// Hover: the pointer becomes our hand. (The first move after a screen opens is ignored by the game.)
			double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
			moveTo(context, cardId, ScratchFoil.SIZE / 2.0 - 4, ScratchFoil.SIZE / 2.0, scale);
			context.waitTick();
			moveTo(context, cardId, ScratchFoil.SIZE / 2.0, ScratchFoil.SIZE / 2.0, scale);
			context.waitTicks(3);
			context.takeScreenshot("scratch_block_hand");

			// Rub the first row in a zigzag with the real mouse.
			moveTo(context, cardId, 2, 4, scale);
			context.getInput().holdMouse(LEFT_BUTTON);
			for (int row = 0; row * 7 + 4 < ScratchFoil.CELL; row++) {
				for (int s = 0; s <= 6; s++) {
					double t = s / 6.0;
					double x = row % 2 == 0 ? 2 + (ScratchFoil.SIZE - 4) * t : ScratchFoil.SIZE - 2 - (ScratchFoil.SIZE - 4) * t;
					moveTo(context, cardId, x, 4 + row * 7, scale);
					context.waitTick();
				}
			}
			moveTo(context, cardId, 20, ScratchFoil.CELL + ScratchFoil.GAP + 20, scale);
			context.waitTick();
			context.takeScreenshot("scratch_block_rubbing");
			context.getInput().releaseMouse(LEFT_BUTTON);
			context.waitTicks(6);

			// The server's foil matches what we rubbed here.
			boolean[] server = world.getServer().computeOnServer(s -> {
				ScratchCardEntity card = (ScratchCardEntity) s.getPlayerList().getPlayers().getFirst().level().getEntity(cardId);
				return new boolean[] {card.foil().isRevealed(0), card.foil().isRevealed(1), card.foil().isRevealed(2), card.foil().isRevealed(4)};
			});
			if (!server[0] || !server[1] || !server[2] || server[3]) {
				throw new AssertionError("Server foil does not match the strokes: " + java.util.Arrays.toString(server));
			}

			context.runOnClient(client -> ((ScratchTableScreen) client.gui.screen()).revealAll());
			context.waitTicks(10);
			context.takeScreenshot("scratch_block_won");
			context.waitTicks(60);
			boolean returned = world.getServer().computeOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					ScratchData data = ScratchCardItem.data(player.getInventory().getItem(i));
					if (data != null && data.scratched() && data.multiplier() > 0) {
						return true;
					}
				}
				return false;
			});
			if (!returned || context.computeOnClient(client -> client.gui.screen() != null)) {
				throw new AssertionError("The scratched card did not come back to the player");
			}

			// Somebody else scratches a card on the next block: we only receive their strokes.
			int otherId = world.getServer().computeOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos feet = player.blockPosition();
				FakePlayer other = FakePlayer.get(level);
				other.snapTo(feet.getX() + 1.5, feet.getY(), feet.getZ() + 0.5, 0, 30);
				ScratchCardEntity card = ScratchCardEntity.place(other, winningCard(level, feet.offset(0, 0, -4)), feet.offset(1, 0, 1));
				return card.getId();
			});
			context.waitTicks(10);
			world.getServer().runOnServer(s -> {
				ServerLevel level = s.getPlayerList().getPlayers().getFirst().level();
				ScratchCardEntity card = (ScratchCardEntity) level.getEntity(otherId);
				List<Integer> points = new ArrayList<>();
				for (int row = 0; row * 7 + 4 < ScratchFoil.CELL; row++) {
					points.add(8);
					points.add((4 + row * 7) * 4);
					points.add((ScratchFoil.SIZE - 2) * 4);
					points.add((4 + row * 7) * 4);
					points.add(ScratchCardEntity.BREAK);
					points.add(ScratchCardEntity.BREAK);
				}
				card.handle(FakePlayer.get(level), new ScratchRub(otherId, points));
			});
			context.waitTicks(2);
			context.takeScreenshot("scratch_remote");
			boolean seen = context.computeOnClient(client -> client.level.getEntity(otherId) instanceof ScratchCardEntity card
					&& card.foil().isRevealed(0) && card.foil().isRevealed(2) && !card.foil().isRevealed(4));
			if (!seen) {
				throw new AssertionError("Other player's strokes did not reach this client");
			}
		}
	}

	private static ItemStack winningCard(ServerLevel level, BlockPos bell) {
		long seed = 1;
		while (ScratchCardItem.outcome(level, ScratchCardItem.data(ScratchCardItem.issued(ModItems.SCRATCH_CARD_SILVER, bell, seed))).multiplier() <= 0) {
			seed++;
		}
		return ScratchCardItem.issued(ModItems.SCRATCH_CARD_SILVER, bell, seed);
	}

	/** Puts the real mouse over a grid point of our card. */
	private static void moveTo(ClientGameTestContext context, int cardId, double gx, double gy, double scale) {
		double[] at = context.computeOnClient(client -> {
			ScratchCardEntity card = (ScratchCardEntity) client.level.getEntity(cardId);
			return ScratchGeometry.project(card, gx, gy, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
		});
		context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
	}
}
