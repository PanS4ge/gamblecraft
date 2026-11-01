package pl.pansage.gamblecraft.sportsbook.network;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import pl.pansage.gamblecraft.sportsbook.Sportsbook;

public final class SbPayloads {
	private SbPayloads() {
	}

	/** One game as the terminals show it; odds 0 where an outcome is not offered. */
	public record EventView(String key, int league, int source, String home, String away, long kickoff, int state, int homeScore, int awayScore,
			String clock, double oddsHome, double oddsDraw, double oddsAway, boolean model) {
		public static final StreamCodec<ByteBuf, EventView> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, EventView::key,
				ByteBufCodecs.VAR_INT, EventView::league,
				ByteBufCodecs.VAR_INT, EventView::source,
				ByteBufCodecs.STRING_UTF8, EventView::home,
				ByteBufCodecs.STRING_UTF8, EventView::away,
				ByteBufCodecs.VAR_LONG, EventView::kickoff,
				ByteBufCodecs.VAR_INT, EventView::state,
				ByteBufCodecs.INT, EventView::homeScore,
				ByteBufCodecs.INT, EventView::awayScore,
				ByteBufCodecs.STRING_UTF8, EventView::clock,
				ByteBufCodecs.DOUBLE, EventView::oddsHome,
				ByteBufCodecs.DOUBLE, EventView::oddsDraw,
				ByteBufCodecs.DOUBLE, EventView::oddsAway,
				ByteBufCodecs.BOOL, EventView::model,
				EventView::new);

		public String score() {
			return homeScore >= 0 && awayScore >= 0 ? homeScore + ":" + awayScore : "";
		}
	}

	/** How a league's feed is doing: which source it is read from (-1 none), whether the last read worked, why not. */
	public record LeagueView(int league, int source, boolean ok, String message, long updated, int count) {
		public static final StreamCodec<ByteBuf, LeagueView> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, LeagueView::league,
				ByteBufCodecs.INT, LeagueView::source,
				ByteBufCodecs.BOOL, LeagueView::ok,
				ByteBufCodecs.STRING_UTF8, LeagueView::message,
				ByteBufCodecs.VAR_LONG, LeagueView::updated,
				ByteBufCodecs.VAR_INT, LeagueView::count,
				LeagueView::new);
	}

	/**
	 * Server to client: the games, the leagues' feed status and the switches (bit i of {@code sources} is
	 * {@code Source.values()[i]}, of {@code leagues} {@code League.values()[i]}); {@code canEdit} if this player may
	 * change them. Sent on joining and after every change.
	 */
	public record FeedSync(List<EventView> events, List<LeagueView> leagues, int sources, boolean draftKings, int enabledLeagues, boolean canEdit,
			long serverTime, long maxPayout, long minStake, long maxStake, int maxPicks) implements CustomPacketPayload {
		public static final Type<FeedSync> TYPE = new Type<>(Sportsbook.id("feed_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FeedSync> CODEC = StreamCodec.composite(
				EventView.CODEC.apply(ByteBufCodecs.list()), FeedSync::events,
				LeagueView.CODEC.apply(ByteBufCodecs.list()), FeedSync::leagues,
				ByteBufCodecs.VAR_INT, FeedSync::sources,
				ByteBufCodecs.BOOL, FeedSync::draftKings,
				ByteBufCodecs.VAR_INT, FeedSync::enabledLeagues,
				ByteBufCodecs.BOOL, FeedSync::canEdit,
				ByteBufCodecs.VAR_LONG, FeedSync::serverTime,
				ByteBufCodecs.VAR_LONG, FeedSync::maxPayout,
				ByteBufCodecs.VAR_LONG, FeedSync::minStake,
				ByteBufCodecs.VAR_LONG, FeedSync::maxStake,
				ByteBufCodecs.VAR_INT, FeedSync::maxPicks,
				FeedSync::new);

		@Override
		public Type<FeedSync> type() {
			return TYPE;
		}
	}

	/** Server to client: open the betting window of the terminal at {@code pos}. */
	public record OpenTerminal(BlockPos pos) implements CustomPacketPayload {
		public static final Type<OpenTerminal> TYPE = new Type<>(Sportsbook.id("open_terminal"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenTerminal> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OpenTerminal::pos, OpenTerminal::new);

		@Override
		public Type<OpenTerminal> type() {
			return TYPE;
		}
	}

	/** Client to server: buy a coupon of these picks (selection = {@code Outcome} ordinal). */
	public record PlaceBet(BlockPos pos, List<String> keys, List<Integer> selections, long stake, boolean minerals) implements CustomPacketPayload {
		public static final Type<PlaceBet> TYPE = new Type<>(Sportsbook.id("place_bet"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PlaceBet> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, PlaceBet::pos,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(32)), PlaceBet::keys,
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(32)), PlaceBet::selections,
				ByteBufCodecs.VAR_LONG, PlaceBet::stake,
				ByteBufCodecs.BOOL, PlaceBet::minerals,
				PlaceBet::new);

		@Override
		public Type<PlaceBet> type() {
			return TYPE;
		}
	}

	/** Server to client: how buying went; on success the window clears the slip. */
	public record BetResult(boolean ok, Component message) implements CustomPacketPayload {
		public static final Type<BetResult> TYPE = new Type<>(Sportsbook.id("bet_result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BetResult> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, BetResult::ok,
				ComponentSerialization.TRUSTED_STREAM_CODEC, BetResult::message,
				BetResult::new);

		@Override
		public Type<BetResult> type() {
			return TYPE;
		}
	}

	/** Client to server: flip one switch of the sources window ("source:espn", "league:nba", "odds:draftkings"). */
	public record SetSwitch(String key, boolean on) implements CustomPacketPayload {
		public static final Type<SetSwitch> TYPE = new Type<>(Sportsbook.id("set_switch"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SetSwitch> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, SetSwitch::key,
				ByteBufCodecs.BOOL, SetSwitch::on,
				SetSwitch::new);

		@Override
		public Type<SetSwitch> type() {
			return TYPE;
		}
	}
}
