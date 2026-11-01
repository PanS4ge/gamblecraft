package pl.pansage.gamblecraft.sportsbook.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import pl.pansage.gamblecraft.sportsbook.client.screen.BetScreen;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.BetResult;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.FeedSync;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.OpenTerminal;
import pl.pansage.gamblecraft.sportsbook.registry.SbRegistry;

public class SportsbookClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(SbRegistry.BETTING_TERMINAL_ENTITY, TerminalRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(FeedSync.TYPE, (payload, context) -> ClientFeed.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(OpenTerminal.TYPE, (payload, context) -> context.client().gui.setScreen(new BetScreen(payload.pos())));
		ClientPlayNetworking.registerGlobalReceiver(BetResult.TYPE, (payload, context) -> ClientFeed.result(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientFeed.clear());
	}
}
