package pl.pansage.gamblecraft.network;

import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import pl.pansage.gamblecraft.block.entity.ArcadeBlockEntity;
import pl.pansage.gamblecraft.block.entity.CrashPadBlockEntity;
import pl.pansage.gamblecraft.entity.CardTableEntity;
import pl.pansage.gamblecraft.entity.CashierEntity;
import pl.pansage.gamblecraft.entity.ScratchCardEntity;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.TablePlacement;
import pl.pansage.gamblecraft.network.TablePayloads.DealerStart;
import pl.pansage.gamblecraft.network.TablePayloads.OpenDealer;
import pl.pansage.gamblecraft.network.TablePayloads.TableAction;
import pl.pansage.gamblecraft.network.TablePayloads.TableState;

public final class GcNetworking {
	private GcNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(AgeAcceptedPayload.TYPE, AgeAcceptedPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TableAction.TYPE, TableAction.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DealerStart.TYPE, DealerStart.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TableState.TYPE, TableState.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenDealer.TYPE, OpenDealer.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TablePayloads.TableToss.TYPE, TablePayloads.TableToss.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TablePayloads.DealerGesture.TYPE, TablePayloads.DealerGesture.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScratchPayloads.OpenScratchTable.TYPE, ScratchPayloads.OpenScratchTable.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScratchPayloads.FoilSync.TYPE, ScratchPayloads.FoilSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScratchPayloads.Strokes.TYPE, ScratchPayloads.Strokes.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScratchPayloads.OpenExchange.TYPE, ScratchPayloads.OpenExchange.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ScratchPayloads.ScratchRub.TYPE, ScratchPayloads.ScratchRub.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ScratchPayloads.ScratchCommand.TYPE, ScratchPayloads.ScratchCommand.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ScratchPayloads.ExchangeAction.TYPE, ScratchPayloads.ExchangeAction.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ScratchPayloads.ScratchRub.TYPE, (payload, context) -> {
			if (context.player().level().getEntity(payload.entityId()) instanceof ScratchCardEntity card) {
				card.handle(context.player(), payload);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ScratchPayloads.ScratchCommand.TYPE, (payload, context) -> {
			if (context.player().level().getEntity(payload.entityId()) instanceof ScratchCardEntity card) {
				card.handle(context.player(), payload);
			}
		});
		PayloadTypeRegistry.clientboundPlay().register(CasinoPayloads.OpenCashier.TYPE, CasinoPayloads.OpenCashier.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CasinoPayloads.Shake.TYPE, CasinoPayloads.Shake.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CasinoPayloads.AlarmPulse.TYPE, CasinoPayloads.AlarmPulse.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CasinoPayloads.OpenShop.TYPE, CasinoPayloads.OpenShop.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CasinoPayloads.WinFlash.TYPE, CasinoPayloads.WinFlash.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CasinoPayloads.ShopChoice.TYPE, CasinoPayloads.ShopChoice.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CasinoPayloads.ShopChoice.TYPE, (payload, context) ->
				pl.pansage.gamblecraft.game.ChipShop.choose(context.player(), context.player().level().getEntity(payload.entityId()), payload.index()));
		PayloadTypeRegistry.clientboundPlay().register(CasinoPayloads.OpenArcade.TYPE, CasinoPayloads.OpenArcade.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CasinoPayloads.OpenKiosk.TYPE, CasinoPayloads.OpenKiosk.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CasinoPayloads.KioskBuy.TYPE, CasinoPayloads.KioskBuy.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CasinoPayloads.KioskBuy.TYPE, (payload, context) ->
				pl.pansage.gamblecraft.block.ScratchKioskBlock.buy(context.player(), payload.pos(), payload.tier()));
		PayloadTypeRegistry.serverboundPlay().register(CasinoPayloads.ArcadeAction.TYPE, CasinoPayloads.ArcadeAction.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CasinoPayloads.ArcadeAction.TYPE, (payload, context) -> {
			var player = context.player();
			if (player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(payload.pos())) > 10
					|| !(player.level().getBlockEntity(payload.pos()) instanceof ArcadeBlockEntity game)) {
				return;
			}
			switch (payload.op()) {
				case CasinoPayloads.ArcadeAction.BET -> game.bet(player, payload.option(), payload.amount());
				case CasinoPayloads.ArcadeAction.TAKE_BACK -> game.takeBack(player);
				case CasinoPayloads.ArcadeAction.CASH_OUT -> {
					if (game instanceof CrashPadBlockEntity crash) {
						crash.cashOut(player);
					}
				}
				default -> {
				}
			}
		});
		PayloadTypeRegistry.serverboundPlay().register(CasinoPayloads.CashierAction.TYPE, CasinoPayloads.CashierAction.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CasinoPayloads.CashierAction.TYPE, (payload, context) -> {
			if (context.player().level().getEntity(payload.entityId()) instanceof CashierEntity cashier) {
				cashier.handle(context.player(), payload);
			}
		});
		EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
			if (entity instanceof ScratchCardEntity card) {
				card.sendFoilTo(player);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ScratchPayloads.ExchangeAction.TYPE, (payload, context) -> {
			if (context.player().level().getEntity(payload.clerkId()) instanceof pl.pansage.gamblecraft.entity.LotteryClerkEntity clerk) {
				clerk.handle(context.player(), payload);
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(AgeAcceptedPayload.TYPE,
				(payload, context) -> AgeGate.markAccepted(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(TableAction.TYPE, (payload, context) -> {
			if (context.player().level().getEntity(payload.tableId()) instanceof CardTableEntity table) {
				table.handle(context.player(), payload);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(DealerStart.TYPE, (payload, context) -> TablePlacement.request(context.player(), payload));
	}
}
