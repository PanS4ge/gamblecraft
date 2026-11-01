package pl.pansage.gamblecraft.sportsbook.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import pl.pansage.gamblecraft.sportsbook.Bookmaker;
import pl.pansage.gamblecraft.sportsbook.FeedService;
import pl.pansage.gamblecraft.sportsbook.SportsbookConfig;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.feed.Odds;
import pl.pansage.gamblecraft.sportsbook.feed.Outcome;
import pl.pansage.gamblecraft.sportsbook.feed.Source;
import pl.pansage.gamblecraft.sportsbook.feed.SportEvent;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.BetResult;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.EventView;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.FeedSync;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.LeagueView;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.OpenTerminal;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.PlaceBet;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.SetSwitch;

public final class SbNetworking {
	private SbNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(FeedSync.TYPE, FeedSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenTerminal.TYPE, OpenTerminal.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BetResult.TYPE, BetResult.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PlaceBet.TYPE, PlaceBet.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetSwitch.TYPE, SetSwitch.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PlaceBet.TYPE, (payload, context) -> {
			List<Outcome> selections = new ArrayList<>();
			for (int s : payload.selections()) {
				selections.add(Outcome.of(s));
			}
			Bookmaker.Placed placed = Bookmaker.place(context.player(), payload.pos(), payload.keys(), selections, payload.stake(), payload.minerals());
			ServerPlayNetworking.send(context.player(), new BetResult(placed.ok(), placed.message()));
		});
		ServerPlayNetworking.registerGlobalReceiver(SetSwitch.TYPE, (payload, context) -> flip(context.player(), payload.key(), payload.on()));
	}

	/** Operators may change the sources; in single player the world's owner may too. */
	public static boolean canEdit(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || server.isSingleplayerOwner(player.nameAndId());
	}

	public static void open(ServerPlayer player, BlockPos terminal) {
		FeedService feed = FeedService.get();
		if (feed != null) {
			sync(player, feed);
		}
		ServerPlayNetworking.send(player, new OpenTerminal(terminal));
	}

	private static void flip(ServerPlayer player, String key, boolean on) {
		if (!canEdit(player)) {
			player.sendOverlayMessage(Component.translatable("gamblecraft_sportsbook.sources.no_permission").withStyle(ChatFormatting.RED));
			return;
		}
		if (!set(key, on)) {
			return;
		}
		FeedService feed = FeedService.get();
		if (feed != null) {
			feed.refresh();
		}
	}

	/** Changes one switch and saves the config; false for an unknown key. Also used by the command. */
	public static boolean set(String key, boolean on) {
		SportsbookConfig config = SportsbookConfig.get();
		String[] parts = key.split(":", 2);
		if (parts.length != 2) {
			return false;
		}
		switch (parts[0]) {
			case "source" -> {
				if (Source.byId(parts[1]) == null) {
					return false;
				}
				config.sources.put(parts[1], on);
			}
			case "league" -> {
				if (League.byId(parts[1]) == null) {
					return false;
				}
				config.leagues.put(parts[1], on);
			}
			case "odds" -> {
				if (!parts[1].equals("draftkings")) {
					return false;
				}
				config.draftKingsOdds = on;
			}
			default -> {
				return false;
			}
		}
		SportsbookConfig.save();
		return true;
	}

	private static List<EventView> events(FeedService feed, long now) {
		List<EventView> out = new ArrayList<>();
		for (SportEvent e : feed.visible(now)) {
			Odds odds = e.odds();
			out.add(new EventView(e.key(), e.league().ordinal(), e.source() == null ? -1 : e.source().ordinal(), e.home(), e.away(), e.kickoff(),
					e.state().ordinal(), e.homeScore(), e.awayScore(), e.clock(), odds == null ? 0 : odds.home(), odds == null ? 0 : odds.draw(),
					odds == null ? 0 : odds.away(), odds == null || odds.model()));
		}
		return out;
	}

	private static List<LeagueView> leagues(FeedService feed) {
		List<LeagueView> out = new ArrayList<>();
		for (Map.Entry<League, FeedService.Status> entry : feed.status().entrySet()) {
			FeedService.Status s = entry.getValue();
			out.add(new LeagueView(entry.getKey().ordinal(), s.source() == null ? -1 : s.source().ordinal(), s.ok(), s.message(), s.updated(), s.count()));
		}
		return out;
	}

	private static FeedSync payload(List<EventView> events, List<LeagueView> leagues, boolean canEdit, long now) {
		SportsbookConfig config = SportsbookConfig.get();
		int sources = 0;
		for (Source source : Source.values()) {
			if (config.on(source)) {
				sources |= 1 << source.ordinal();
			}
		}
		int enabled = 0;
		for (League league : League.values()) {
			if (config.on(league)) {
				enabled |= 1 << league.ordinal();
			}
		}
		return new FeedSync(events, leagues, sources, config.draftKingsOdds, enabled, canEdit, now, config.maxPayout, config.minStake, config.maxStake,
				config.maxPicks);
	}

	public static void sync(ServerPlayer player, FeedService feed) {
		long now = System.currentTimeMillis();
		ServerPlayNetworking.send(player, payload(events(feed, now), leagues(feed), canEdit(player), now));
	}

	public static void syncAll(MinecraftServer server, FeedService feed) {
		long now = System.currentTimeMillis();
		List<EventView> events = events(feed, now);
		List<LeagueView> leagues = leagues(feed);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload(events, leagues, canEdit(player), now));
		}
	}
}
