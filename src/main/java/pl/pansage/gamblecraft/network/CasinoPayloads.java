package pl.pansage.gamblecraft.network;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import pl.pansage.gamblecraft.GambleCraft;

/** Packets for the casino cashier. */
public final class CasinoPayloads {
	private CasinoPayloads() {
	}

	/** One mineral the cashier takes, and how many chips one of it buys. */
	public record Rate(String item, int chips) {
		public static final StreamCodec<ByteBuf, Rate> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Rate::item,
				ByteBufCodecs.VAR_INT, Rate::chips,
				Rate::new);
	}

	/** Server to client: the cashier's window. {@code payouts} are what chips can be cashed out into. */
	public record OpenCashier(int entityId, List<Rate> rates, List<Rate> payouts, double houseCut) implements CustomPacketPayload {
		public static final Type<OpenCashier> TYPE = new Type<>(GambleCraft.id("open_cashier"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenCashier> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, OpenCashier::entityId,
				Rate.CODEC.apply(ByteBufCodecs.list()), OpenCashier::rates,
				Rate.CODEC.apply(ByteBufCodecs.list()), OpenCashier::payouts,
				ByteBufCodecs.DOUBLE, OpenCashier::houseCut,
				OpenCashier::new);

		@Override
		public Type<OpenCashier> type() {
			return TYPE;
		}
	}

	/** Server to client: open the betting window of the round game at {@code pos}. */
	public record OpenArcade(BlockPos pos) implements CustomPacketPayload {
		public static final Type<OpenArcade> TYPE = new Type<>(GambleCraft.id("open_arcade"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenArcade> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OpenArcade::pos, OpenArcade::new);

		@Override
		public Type<OpenArcade> type() {
			return TYPE;
		}
	}

	/** Client to server: bet {@code amount} on {@code option}, take bets back, or cash out (crash). */
	public record ArcadeAction(BlockPos pos, int op, String option, int amount) implements CustomPacketPayload {
		public static final int BET = 0;
		public static final int TAKE_BACK = 1;
		public static final int CASH_OUT = 2;
		public static final Type<ArcadeAction> TYPE = new Type<>(GambleCraft.id("arcade_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ArcadeAction> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, ArcadeAction::pos,
				ByteBufCodecs.VAR_INT, ArcadeAction::op,
				ByteBufCodecs.STRING_UTF8, ArcadeAction::option,
				ByteBufCodecs.VAR_INT, ArcadeAction::amount,
				ArcadeAction::new);

		@Override
		public Type<ArcadeAction> type() {
			return TYPE;
		}
	}

	/** Server to client: the player won {@code amount} chips; {@code tier} 0 a win, 1 a big win, 2 a jackpot (the WIN overlay). */
	public record WinFlash(long amount, int tier) implements CustomPacketPayload {
		public static final Type<WinFlash> TYPE = new Type<>(GambleCraft.id("win_flash"));
		public static final StreamCodec<RegistryFriendlyByteBuf, WinFlash> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, WinFlash::amount,
				ByteBufCodecs.VAR_INT, WinFlash::tier,
				WinFlash::new);

		@Override
		public Type<WinFlash> type() {
			return TYPE;
		}
	}

	/** Server to client: the offers of a casino person who sells for chips ({@link pl.pansage.gamblecraft.game.ChipShop}). */
	public record OpenShop(int entityId, Component title, java.util.List<pl.pansage.gamblecraft.game.ChipShop.Offer> offers) implements CustomPacketPayload {
		public static final Type<OpenShop> TYPE = new Type<>(GambleCraft.id("open_shop"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenShop> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, OpenShop::entityId,
				net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC, OpenShop::title,
				pl.pansage.gamblecraft.game.ChipShop.Offer.STREAM_CODEC.apply(ByteBufCodecs.list(16)), OpenShop::offers,
				OpenShop::new);

		@Override
		public Type<OpenShop> type() {
			return TYPE;
		}
	}

	/** Client to server: the player picked offer {@code index} of the shop of {@code entityId}. */
	public record ShopChoice(int entityId, int index) implements CustomPacketPayload {
		public static final Type<ShopChoice> TYPE = new Type<>(GambleCraft.id("shop_choice"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ShopChoice> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, ShopChoice::entityId,
				ByteBufCodecs.VAR_INT, ShopChoice::index,
				ShopChoice::new);

		@Override
		public Type<ShopChoice> type() {
			return TYPE;
		}
	}

	/** Server to client: the casino's alarm is sounding where the player is (sent every second), or has ended. */
	public record AlarmPulse(boolean on) implements CustomPacketPayload {
		public static final Type<AlarmPulse> TYPE = new Type<>(GambleCraft.id("alarm_pulse"));
		public static final StreamCodec<RegistryFriendlyByteBuf, AlarmPulse> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, AlarmPulse::on, AlarmPulse::new);

		@Override
		public Type<AlarmPulse> type() {
			return TYPE;
		}
	}

	/** Server to client: the casino scratch-card kiosk at {@code pos}. */
	public record OpenKiosk(BlockPos pos) implements CustomPacketPayload {
		public static final Type<OpenKiosk> TYPE = new Type<>(GambleCraft.id("open_kiosk"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenKiosk> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OpenKiosk::pos, OpenKiosk::new);

		@Override
		public Type<OpenKiosk> type() {
			return TYPE;
		}
	}

	/** Client to server: buy a casino scratch card of {@code tier} (0 bronze .. 2 gold). */
	public record KioskBuy(BlockPos pos, int tier) implements CustomPacketPayload {
		public static final Type<KioskBuy> TYPE = new Type<>(GambleCraft.id("kiosk_buy"));
		public static final StreamCodec<RegistryFriendlyByteBuf, KioskBuy> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, KioskBuy::pos,
				ByteBufCodecs.VAR_INT, KioskBuy::tier,
				KioskBuy::new);

		@Override
		public Type<KioskBuy> type() {
			return TYPE;
		}
	}

	/** Server to client: shake the camera for {@code ticks} (a jackpot going off nearby). */
	public record Shake(int ticks, float strength) implements CustomPacketPayload {
		public static final Type<Shake> TYPE = new Type<>(GambleCraft.id("shake"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Shake> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Shake::ticks,
				ByteBufCodecs.FLOAT, Shake::strength,
				Shake::new);

		@Override
		public Type<Shake> type() {
			return TYPE;
		}
	}

	/**
	 * Client to server: buy chips with {@code count} of {@code item}, or cash chips out into {@code count} of it
	 * ({@code count < 0}: as many as possible).
	 */
	public record CashierAction(int entityId, boolean buy, String item, int count) implements CustomPacketPayload {
		public static final Type<CashierAction> TYPE = new Type<>(GambleCraft.id("cashier_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CashierAction> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, CashierAction::entityId,
				ByteBufCodecs.BOOL, CashierAction::buy,
				ByteBufCodecs.STRING_UTF8, CashierAction::item,
				ByteBufCodecs.VAR_INT, CashierAction::count,
				CashierAction::new);

		@Override
		public Type<CashierAction> type() {
			return TYPE;
		}
	}
}
