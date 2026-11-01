package pl.pansage.gamblecraft.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.core.table.TableTypes.TableSnapshot;

/** Packets for card tables and dealers. */
public final class TablePayloads {
	private TablePayloads() {
	}

	/** Server to client: the table as this player sees it. */
	public record TableState(int tableId, int dealerId, String currency, long minBet, long maxBet, int ticksLeft, boolean open,
			TableSnapshot snapshot) implements CustomPacketPayload {
		public static final Type<TableState> TYPE = new Type<>(GambleCraft.id("table_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TableState> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.tableId);
					buf.writeVarInt(p.dealerId + 1);
					buf.writeUtf(p.currency);
					buf.writeVarLong(p.minBet);
					buf.writeVarLong(p.maxBet);
					buf.writeVarInt(p.ticksLeft);
					buf.writeBoolean(p.open);
					TableCodecs.SNAPSHOT.encode(buf, p.snapshot);
				},
				buf -> new TableState(buf.readVarInt(), buf.readVarInt() - 1, buf.readUtf(), buf.readVarLong(), buf.readVarLong(),
						buf.readVarInt(), buf.readBoolean(), TableCodecs.SNAPSHOT.decode(buf)));

		@Override
		public Type<TableState> type() {
			return TYPE;
		}
	}

	/** Client to server: a request at a table. */
	public record TableAction(int tableId, Op op, int act, long amount, int option, long side) implements CustomPacketPayload {
		public enum Op { SIT, LEAVE, BET, CLEAR_BET, ACT, BUY_IN }

		public static final Type<TableAction> TYPE = new Type<>(GambleCraft.id("table_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TableAction> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.tableId);
					buf.writeEnum(p.op);
					buf.writeVarInt(p.act);
					buf.writeVarLong(p.amount);
					buf.writeVarInt(p.option);
					buf.writeVarLong(p.side);
				},
				buf -> new TableAction(buf.readVarInt(), buf.readEnum(Op.class), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong()));

		@Override
		public Type<TableAction> type() {
			return TYPE;
		}
	}

	/** Server to client: a player at the table threw a stake ({@code item}) or a card onto it. */
	public record TableToss(int tableId, int entityId, int seat, boolean card, String item) implements CustomPacketPayload {
		public static final Type<TableToss> TYPE = new Type<>(GambleCraft.id("table_toss"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TableToss> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, TableToss::tableId,
				ByteBufCodecs.VAR_INT, TableToss::entityId,
				ByteBufCodecs.VAR_INT, TableToss::seat,
				ByteBufCodecs.BOOL, TableToss::card,
				ByteBufCodecs.STRING_UTF8, TableToss::item,
				TableToss::new);

		@Override
		public Type<TableToss> type() {
			return TYPE;
		}
	}

	/** Server to client: a dealer plays a gesture animation (shuffle, deal, cheer, sulk). */
	public record DealerGesture(int entityId, int gesture) implements CustomPacketPayload {
		public static final Type<DealerGesture> TYPE = new Type<>(GambleCraft.id("dealer_gesture"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DealerGesture> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, DealerGesture::entityId,
				ByteBufCodecs.VAR_INT, DealerGesture::gesture,
				DealerGesture::new);

		@Override
		public Type<DealerGesture> type() {
			return TYPE;
		}
	}

	/** One item a village dealer accepts, with its value in emeralds. */
	public record Accepted(String item, double value) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Accepted> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Accepted::item, ByteBufCodecs.DOUBLE, Accepted::value, Accepted::new);
	}

	/** Server to client: open the dealer's game picker. */
	public record OpenDealer(int dealerId, List<Accepted> accepted, List<String> games, double purse) implements CustomPacketPayload {
		public static final Type<OpenDealer> TYPE = new Type<>(GambleCraft.id("open_dealer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenDealer> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, OpenDealer::dealerId,
				Accepted.CODEC.apply(ByteBufCodecs.list()), OpenDealer::accepted,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenDealer::games,
				ByteBufCodecs.DOUBLE, OpenDealer::purse,
				OpenDealer::new);

		@Override
		public Type<OpenDealer> type() {
			return TYPE;
		}
	}

	/** Client to server: start this game with this stake item; the server then asks the player to pick a block. */
	public record DealerStart(int dealerId, String game, String item) implements CustomPacketPayload {
		public static final Type<DealerStart> TYPE = new Type<>(GambleCraft.id("dealer_start"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DealerStart> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, DealerStart::dealerId,
				ByteBufCodecs.STRING_UTF8, DealerStart::game,
				ByteBufCodecs.STRING_UTF8, DealerStart::item,
				DealerStart::new);

		@Override
		public Type<DealerStart> type() {
			return TYPE;
		}
	}
}
