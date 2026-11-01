package pl.pansage.gamblecraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.entity.EntityRenderers;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import pl.pansage.gamblecraft.client.render.RouletteRenderer;
import pl.pansage.gamblecraft.client.render.ScratchCardRenderer;
import pl.pansage.gamblecraft.client.render.SlotMachineRenderer;
import pl.pansage.gamblecraft.client.table.ClientEffects;
import pl.pansage.gamblecraft.registry.ModBlockEntities;
import pl.pansage.gamblecraft.client.screen.CashierScreen;
import pl.pansage.gamblecraft.client.scratch.ClientScratch;
import pl.pansage.gamblecraft.core.casino.ScratchFoil;
import pl.pansage.gamblecraft.entity.ScratchCardEntity;
import pl.pansage.gamblecraft.network.CasinoPayloads;
import pl.pansage.gamblecraft.network.ScratchPayloads;
import pl.pansage.gamblecraft.client.config.ClientConfig;
import pl.pansage.gamblecraft.client.model.StreetDealerModel;
import pl.pansage.gamblecraft.client.model.StreetDealerModelData;
import pl.pansage.gamblecraft.client.render.CardTableRenderer;
import pl.pansage.gamblecraft.client.render.CasinoNpcRenderer;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;
import pl.pansage.gamblecraft.client.screen.DealerScreen;
import pl.pansage.gamblecraft.client.screen.DeckScreen;
import pl.pansage.gamblecraft.client.table.ClientPick;
import pl.pansage.gamblecraft.client.table.ClientTables;
import pl.pansage.gamblecraft.client.table.TableCamera;
import pl.pansage.gamblecraft.network.TablePayloads;
import pl.pansage.gamblecraft.network.AgeAcceptedPayload;
import pl.pansage.gamblecraft.network.TablePayloads.OpenDealer;
import pl.pansage.gamblecraft.network.TablePayloads.TableState;
import pl.pansage.gamblecraft.registry.ModEntities;
import pl.pansage.gamblecraft.registry.ModMenus;

public class GambleCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.DECK, DeckScreen::new);
		ModelLayerRegistry.registerModelLayer(StreetDealerModel.LAYER, StreetDealerModelData::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(pl.pansage.gamblecraft.client.model.CasinoGuardModel.LAYER,
				pl.pansage.gamblecraft.client.model.CasinoGuardModelData::createBodyLayer);
		EntityRenderers.register(ModEntities.CASINO_GUARD, pl.pansage.gamblecraft.client.render.CasinoGuardRenderer::new);
		EntityRenderers.register(ModEntities.ADDICTED_GAMBLER, ctx -> new CasinoNpcRenderer<>(ctx, "addicted_gambler", false));
		EntityRenderers.register(ModEntities.LOAN_SHARK, ctx -> new CasinoNpcRenderer<>(ctx, "loan_shark", false, false, true));
		EntityRenderers.register(ModEntities.BARTENDER, ctx -> new CasinoNpcRenderer<>(ctx, "bartender", false));
		EntityRenderers.register(ModEntities.WAITRESS, ctx -> new CasinoNpcRenderer<>(ctx, "waitress", false, true, false));
		EntityRenderers.register(ModEntities.PICKPOCKET, ctx -> new CasinoNpcRenderer<>(ctx, "pickpocket", false));
		EntityRenderers.register(ModEntities.CASINO_BOSS, ctx -> new CasinoNpcRenderer<>(ctx, "casino_boss", true, false, true));
		EntityRenderers.register(ModEntities.DEBT_COLLECTOR, ctx -> new pl.pansage.gamblecraft.client.render.ThugRenderer<>(ctx, "debt_collector"));
		EntityRenderers.register(ModEntities.MUGGER, ctx -> new pl.pansage.gamblecraft.client.render.ThugRenderer<>(ctx, "mugger"));
		EntityRenderers.register(ModEntities.CARD_TABLE, CardTableRenderer::new);
		EntityRenderers.register(ModEntities.STREET_DEALER, ctx -> new CasinoNpcRenderer<>(ctx, "street_dealer", true));
		EntityRenderers.register(ModEntities.LOTTERY_CLERK, ctx -> new CasinoNpcRenderer<>(ctx, "lottery_clerk", false));
		EntityRenderers.register(ModEntities.SCRATCH_CARD, ScratchCardRenderer::new);
		EntityRenderers.register(ModEntities.CASHIER, ctx -> new CasinoNpcRenderer<>(ctx, "cashier", false));
		EntityRenderers.register(ModEntities.CASINO_DEALER, ctx -> new CasinoNpcRenderer<>(ctx, "casino_dealer", true));
		EntityRenderers.register(ModEntities.BOUNCER, ctx -> new CasinoNpcRenderer<>(ctx, "bouncer", false));
		BlockEntityRenderers.register(ModBlockEntities.SLOT_MACHINE, SlotMachineRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.ROULETTE, RouletteRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.FORTUNE_WHEEL, pl.pansage.gamblecraft.client.render.FortuneWheelRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.DICE_TABLE, pl.pansage.gamblecraft.client.render.DiceTableRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.CRASH_PAD, pl.pansage.gamblecraft.client.render.CrashPadRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.PLINKO, pl.pansage.gamblecraft.client.render.PlinkoRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(TableState.TYPE, (payload, context) -> ClientTables.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(OpenDealer.TYPE, (payload, context) -> context.client().gui.setScreen(new DealerScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(TablePayloads.TableToss.TYPE, (payload, context) -> ClientTables.toss(payload));
		ClientPlayNetworking.registerGlobalReceiver(TablePayloads.DealerGesture.TYPE, (payload, context) -> {
			if (context.client().level != null
					&& context.client().level.getEntity(payload.entityId()) instanceof pl.pansage.gamblecraft.entity.CasinoNpc npc) {
				npc.playGesture(payload.gesture());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(ScratchPayloads.OpenScratchTable.TYPE, (payload, context) -> ClientScratch.open(payload.entityId()));
		ClientPlayNetworking.registerGlobalReceiver(ScratchPayloads.FoilSync.TYPE, (payload, context) -> {
			if (context.client().level != null && context.client().level.getEntity(payload.entityId()) instanceof ScratchCardEntity card) {
				card.setFoil(ScratchFoil.fromBytes(payload.foil()));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(CasinoPayloads.OpenArcade.TYPE,
				(payload, context) -> context.client().gui.setScreen(new pl.pansage.gamblecraft.client.screen.ArcadeScreen(payload.pos())));
		ClientPlayNetworking.registerGlobalReceiver(CasinoPayloads.OpenKiosk.TYPE,
				(payload, context) -> context.client().gui.setScreen(new pl.pansage.gamblecraft.client.screen.KioskScreen(payload.pos())));
		ClientPlayNetworking.registerGlobalReceiver(CasinoPayloads.OpenShop.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof pl.pansage.gamblecraft.client.screen.ShopScreen shop && shop.entityId() == payload.entityId()) {
				shop.refresh(payload);
			} else {
				context.client().gui.setScreen(new pl.pansage.gamblecraft.client.screen.ShopScreen(payload));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(CasinoPayloads.WinFlash.TYPE,
				(payload, context) -> pl.pansage.gamblecraft.client.table.WinOverlay.show(payload.amount(), payload.tier()));
		HudElementRegistry.addLast(pl.pansage.gamblecraft.GambleCraft.id("win_overlay"),
				(graphics, delta) -> pl.pansage.gamblecraft.client.table.WinOverlay.draw(graphics, delta.getGameTimeDeltaPartialTick(false)));
		net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(pl.pansage.gamblecraft.registry.ModParticles.CONFETTI,
				net.minecraft.client.particle.FallingLeavesParticle.TintedLeavesProvider::new);
		net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(pl.pansage.gamblecraft.registry.ModParticles.GOLD_GLITTER,
				net.minecraft.client.particle.GlowParticle.WaxOnProvider::new);
		ClientPlayNetworking.registerGlobalReceiver(CasinoPayloads.AlarmPulse.TYPE, (payload, context) -> ClientEffects.alarm(payload.on()));
		HudElementRegistry.addLast(pl.pansage.gamblecraft.GambleCraft.id("casino_alarm"),
				(graphics, delta) -> ClientEffects.drawAlarm(graphics, delta.getGameTimeDeltaPartialTick(false)));
		ClientPlayNetworking.registerGlobalReceiver(CasinoPayloads.Shake.TYPE, (payload, context) -> ClientEffects.shake(payload.ticks(), payload.strength()));
		ClientPlayNetworking.registerGlobalReceiver(CasinoPayloads.OpenCashier.TYPE,
				(payload, context) -> context.client().gui.setScreen(new CashierScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ScratchPayloads.Strokes.TYPE, (payload, context) -> ClientScratch.applyRemote(payload));
		ClientPlayNetworking.registerGlobalReceiver(ScratchPayloads.OpenExchange.TYPE,
				(payload, context) -> context.client().gui.setScreen(new pl.pansage.gamblecraft.client.screen.ExchangeScreen(payload)));
		ClientPick.register();
		// No crosshair or hotbar in the middle of the cards while looking down at a table.
		for (Identifier element : List.of(VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR)) {
			HudElementRegistry.replaceElement(element, original -> (graphics, delta) -> {
				if (!TableCamera.isActive()) {
					original.extractRenderState(graphics, delta);
				}
			});
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!ClientConfig.get().ageAccepted && client.gui.screen() instanceof TitleScreen) {
				client.gui.setScreen(new AgeGateScreen());
			}
			ClientTables.tick();
			ClientScratch.tick();
			ClientEffects.tick();
			pl.pansage.gamblecraft.client.table.WinOverlay.tick();
			TableCamera.tick();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (ClientConfig.get().ageAccepted) {
				ClientPlayNetworking.send(AgeAcceptedPayload.INSTANCE);
			}
		});
	}
}
