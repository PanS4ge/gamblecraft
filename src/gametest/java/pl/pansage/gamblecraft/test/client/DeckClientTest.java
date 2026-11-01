package pl.pansage.gamblecraft.test.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;
import pl.pansage.gamblecraft.client.screen.DeckScreen;
import pl.pansage.gamblecraft.item.CardDeckItem;
import pl.pansage.gamblecraft.registry.ModItems;

/** Opens a full deck, shuffles it, then sneak-uses it to draw one card. */
public class DeckClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof AgeGateScreen)) {
			context.clickScreenButton("gamblecraft.age_gate.accept");
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setItemInHand(InteractionHand.MAIN_HAND, CardDeckItem.full());
			});
			context.waitTicks(10);
			context.takeScreenshot("deck_in_hand");

			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(DeckScreen.class);
			context.takeScreenshot("deck_menu");
			context.clickScreenButton("gamblecraft.deck.shuffle");
			context.waitTicks(3);
			context.takeScreenshot("deck_shuffling");
			context.waitTicks(10);
			context.setScreen(() -> null);

			context.getInput().holdShift();
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			context.getInput().releaseShift();
			context.waitTicks(5);

			int[] counts = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ItemStack deck = player.getItemInHand(InteractionHand.MAIN_HAND);
				int cards = 0;
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					if (player.getInventory().getItem(i).is(ModItems.CARD)) {
						cards += player.getInventory().getItem(i).getCount();
					}
				}
				return new int[] {CardDeckItem.cards(deck).size(), cards};
			});
			if (counts[0] != 51 || counts[1] != 1) {
				throw new AssertionError("Expected 51 cards in deck and 1 drawn, got " + counts[0] + " and " + counts[1]);
			}
			context.takeScreenshot("deck_drew_card");
		}
	}
}
