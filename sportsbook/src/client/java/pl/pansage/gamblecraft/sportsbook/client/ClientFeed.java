package pl.pansage.gamblecraft.sportsbook.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import pl.pansage.gamblecraft.sportsbook.client.screen.BetScreen;
import pl.pansage.gamblecraft.sportsbook.client.screen.SourcesScreen;
import pl.pansage.gamblecraft.sportsbook.feed.EventState;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.feed.Outcome;
import pl.pansage.gamblecraft.sportsbook.feed.Source;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.BetResult;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.EventView;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.FeedSync;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.LeagueView;

/** The client's copy of the server's feed, and the bet slip being filled in (kept while the window is closed). */
public final class ClientFeed {
	/** Event key → the outcome picked, in the order picked. */
	public static final Map<String, Outcome> SLIP = new LinkedHashMap<>();
	private static FeedSync last;

	private ClientFeed() {
	}

	public static void accept(FeedSync sync) {
		last = sync;
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() instanceof SourcesScreen sources) {
			sources.feedChanged();
		}
	}

	public static void result(BetResult result) {
		if (result.ok()) {
			SLIP.clear();
		}
		if (Minecraft.getInstance().gui.screen() instanceof BetScreen screen) {
			screen.result(result.ok(), result.message());
		}
	}

	public static void clear() {
		last = null;
		SLIP.clear();
	}

	public static FeedSync get() {
		return last;
	}

	public static List<EventView> events() {
		return last == null ? List.of() : last.events();
	}

	public static List<EventView> events(League league) {
		List<EventView> out = new ArrayList<>();
		for (EventView e : events()) {
			if (e.league() == league.ordinal()) {
				out.add(e);
			}
		}
		return out;
	}

	public static EventView event(String key) {
		for (EventView e : events()) {
			if (e.key().equals(key)) {
				return e;
			}
		}
		return null;
	}

	public static LeagueView status(League league) {
		if (last != null) {
			for (LeagueView view : last.leagues()) {
				if (view.league() == league.ordinal()) {
					return view;
				}
			}
		}
		return null;
	}

	public static boolean on(Source source) {
		return last == null || (last.sources() & (1 << source.ordinal())) != 0;
	}

	public static boolean on(League league) {
		return last == null || (last.enabledLeagues() & (1 << league.ordinal())) != 0;
	}

	public static boolean open(EventView e, long now) {
		return e.state() == EventState.SCHEDULED.ordinal() && e.kickoff() - now > 60_000L;
	}

	public static double price(EventView e, Outcome outcome) {
		return switch (outcome) {
			case HOME -> e.oddsHome();
			case DRAW -> e.oddsDraw();
			case AWAY -> e.oddsAway();
		};
	}
}
