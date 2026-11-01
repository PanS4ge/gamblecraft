package pl.pansage.gamblecraft.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.network.TablePayloads.Accepted;

/** Packets for scratch cards and the village exchange (lottery clerk). */
public final class ScratchPayloads {
	private ScratchPayloads() {
	}

	/** Server to client: the owner starts (or resumes) scratching the card entity lying on a block. */
	public record OpenScratchTable(int entityId) implements CustomPacketPayload {
		public static final Type<OpenScratchTable> TYPE = new Type<>(GambleCraft.id("open_scratch_table"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenScratchTable> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, OpenScratchTable::entityId, OpenScratchTable::new);

		@Override
		public Type<OpenScratchTable> type() {
			return TYPE;
		}
	}

	/** Server to client: the whole foil of a card (on first sight, and after "reveal all"). */
	public record FoilSync(int entityId, byte[] foil) implements CustomPacketPayload {
		public static final Type<FoilSync> TYPE = new Type<>(GambleCraft.id("foil_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FoilSync> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, FoilSync::entityId,
				ByteBufCodecs.BYTE_ARRAY, FoilSync::foil,
				FoilSync::new);

		@Override
		public Type<FoilSync> type() {
			return TYPE;
		}
	}

	/** Client to server: the owner's stroke points, in quarters of a grid unit ({@code ScratchCardEntity.BREAK} pairs separate strokes). */
	public record ScratchRub(int entityId, List<Integer> points) implements CustomPacketPayload {
		public static final Type<ScratchRub> TYPE = new Type<>(GambleCraft.id("scratch_rub"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScratchRub> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, ScratchRub::entityId,
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ScratchRub::points,
				ScratchRub::new);

		@Override
		public Type<ScratchRub> type() {
			return TYPE;
		}
	}

	/** Server to client: somebody else's strokes on a card, to replay with flakes and sound. */
	public record Strokes(int entityId, List<Integer> points) implements CustomPacketPayload {
		public static final Type<Strokes> TYPE = new Type<>(GambleCraft.id("scratch_strokes"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Strokes> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Strokes::entityId,
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Strokes::points,
				Strokes::new);

		@Override
		public Type<Strokes> type() {
			return TYPE;
		}
	}

	/** Client to server: reveal everything at once, or take the card back off the block. */
	public record ScratchCommand(int entityId, int op) implements CustomPacketPayload {
		public static final int REVEAL_ALL = 0;
		public static final int PICK_UP = 1;
		public static final Type<ScratchCommand> TYPE = new Type<>(GambleCraft.id("scratch_command"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScratchCommand> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, ScratchCommand::entityId,
				ByteBufCodecs.VAR_INT, ScratchCommand::op,
				ScratchCommand::new);

		@Override
		public Type<ScratchCommand> type() {
			return TYPE;
		}
	}

	/** Server to client: the clerk's counter: what the village buys, at what price, and the scratch card prices. */
	public record OpenExchange(int clerkId, int village, List<Accepted> goods, List<Integer> cardPrices, double rate) implements CustomPacketPayload {
		public static final Type<OpenExchange> TYPE = new Type<>(GambleCraft.id("open_exchange"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenExchange> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, OpenExchange::clerkId,
				ByteBufCodecs.VAR_INT, OpenExchange::village,
				Accepted.CODEC.apply(ByteBufCodecs.list()), OpenExchange::goods,
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), OpenExchange::cardPrices,
				ByteBufCodecs.DOUBLE, OpenExchange::rate,
				OpenExchange::new);

		@Override
		public Type<OpenExchange> type() {
			return TYPE;
		}
	}

	/** Client to server: sell {@code count} of {@code item} to the clerk, or buy a scratch card of tier {@code count}. */
	public record ExchangeAction(int clerkId, boolean buyCard, String item, int count) implements CustomPacketPayload {
		public static final Type<ExchangeAction> TYPE = new Type<>(GambleCraft.id("exchange_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeAction> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, ExchangeAction::clerkId,
				ByteBufCodecs.BOOL, ExchangeAction::buyCard,
				ByteBufCodecs.STRING_UTF8, ExchangeAction::item,
				ByteBufCodecs.VAR_INT, ExchangeAction::count,
				ExchangeAction::new);

		@Override
		public Type<ExchangeAction> type() {
			return TYPE;
		}
	}
}
