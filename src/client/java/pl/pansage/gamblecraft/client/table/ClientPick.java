package pl.pansage.gamblecraft.client.table;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Client half of "right-click a block to make it the table": for a short while after choosing a game,
 * the next block use is consumed here (no chest opening or block placing) and only reaches the server.
 */
public final class ClientPick {
	private static final int WINDOW_TICKS = 20 * 30;
	private static long armedUntil = Long.MIN_VALUE;

	private ClientPick() {
	}

	public static void arm() {
		armedUntil = ClientTables.now() + WINDOW_TICKS;
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND || player != Minecraft.getInstance().player) {
				return InteractionResult.PASS;
			}
			if (ClientTables.now() > armedUntil) {
				return InteractionResult.PASS;
			}
			armedUntil = Long.MIN_VALUE;
			return InteractionResult.SUCCESS;
		});
	}
}
