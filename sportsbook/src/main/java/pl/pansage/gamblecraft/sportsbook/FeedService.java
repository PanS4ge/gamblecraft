package pl.pansage.gamblecraft.sportsbook;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import pl.pansage.gamblecraft.sportsbook.bet.BetBook;
import pl.pansage.gamblecraft.sportsbook.bet.Coupon;
import pl.pansage.gamblecraft.sportsbook.bet.Pick;
import pl.pansage.gamblecraft.sportsbook.feed.EventState;
import pl.pansage.gamblecraft.sportsbook.feed.FeedSource;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.feed.Odds;
import pl.pansage.gamblecraft.sportsbook.feed.Source;
import pl.pansage.gamblecraft.sportsbook.feed.SportEvent;
import pl.pansage.gamblecraft.sportsbook.feed.WebHttp;
import pl.pansage.gamblecraft.sportsbook.network.SbNetworking;

/**
 * Keeps the server's picture of the fixtures. The feeds are read on a background thread (never the server thread);
 * what comes back is merged, priced and used to settle coupons on the server thread, then sent to every player.
 *
 * Each league is read from the first of its sources that is switched on and answers: the whole window (yesterday to
 * a week ahead) every {@code refreshMinutes}, yesterday and today every {@code liveRefreshSeconds} while a game is on.
 * Picks are settled from the source they were bought from, even if that source has been switched off since.
 */
public final class FeedService {
	/** Feed days follow US Eastern time, as ESPN's and the NHL's do. */
	public static final ZoneId ZONE = ZoneId.of("America/New_York");
	private static final long HOUR = 3600_000L;

	public enum Job {
		FULL,
		LIVE,
		SETTLE,
		/** A week of past results, once per start, for the ratings. */
		WARMUP
	}

	/** How a league's last read went. {@code source} is null while no source has answered. */
	public record Status(Source source, boolean ok, String message, long updated, int count) {
	}

	private record Result(Source source, List<SportEvent> events, String error) {
	}

	private static FeedService instance;
	/** Game tests run with the network off ({@code -Dgamblecraft.sportsbook.offline=true}) and hand in their own events. */
	public static volatile boolean offline = Boolean.getBoolean("gamblecraft.sportsbook.offline");

	private final MinecraftServer server;
	private final FeedSource.Http http;
	/** A few threads, so one slow feed (TheSportsDB's two-second spacing) does not hold up the others. */
	private final ExecutorService executor = Executors.newFixedThreadPool(3, r -> {
		Thread thread = new Thread(r, "GambleCraft sportsbook feed");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<String, SportEvent> events = new HashMap<>();
	private final Map<League, Status> status = new EnumMap<>(League.class);
	private final Map<String, Long> next = new HashMap<>();
	private final Set<String> running = ConcurrentHashMap.newKeySet();
	private boolean dirty = true;
	private boolean warmed;
	private int ticks;

	private FeedService(MinecraftServer server, FeedSource.Http http) {
		this.server = server;
		this.http = http;
	}

	public static FeedService get() {
		return instance;
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			String version = FabricLoader.getInstance().getModContainer(Sportsbook.MOD_ID).map(m -> m.getMetadata().getVersion().getFriendlyString())
					.orElse("dev");
			instance = new FeedService(server, new WebHttp(version));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (instance != null) {
				instance.executor.shutdownNow();
				instance = null;
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (instance != null && instance.server == server) {
				instance.tick();
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (instance != null) {
				SbNetworking.sync(handler.player, instance);
			}
		});
	}

	private void tick() {
		if (++ticks % 20 != 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!offline) {
			schedule(now);
		}
		if (ticks % 200 == 0) {
			// games the feeds lost track of go void by the clock, not by a read
			settle(now);
			prune(now);
		}
		if (dirty) {
			dirty = false;
			SbNetworking.syncAll(server, this);
		}
	}

	// ---- reading -------------------------------------------------------------------------------------------------

	private void schedule(long now) {
		SportsbookConfig config = SportsbookConfig.get();
		LocalDate today = LocalDate.now(ZONE);
		Map<String, Pick> waiting = BetBook.get(server).waiting();
		for (League league : League.values()) {
			if (!config.on(league)) {
				continue;
			}
			List<Source> chain = league.sources.stream().filter(config::on).toList();
			if (chain.isEmpty()) {
				Status none = new Status(null, false, "", now, 0);
				if (!none.equals(status.get(league))) {
					status.put(league, none);
					dirty = true;
				}
				continue;
			}
			if (due("full:" + league.id, now)) {
				submit(league, chain, today.minusDays(1), today.plusDays(league.daysAhead), Job.FULL);
				next.put("full:" + league.id, now + config.refreshMinutes * 60_000L);
				next.put("live:" + league.id, now + liveInterval(league));
			} else if (hot(league, now) && due("live:" + league.id, now)) {
				submit(league, chain, today.minusDays(1), today, Job.LIVE);
				next.put("live:" + league.id, now + liveInterval(league));
			}
		}
		if (!warmed) {
			// past results for the ratings, queued behind the fixtures, for leagues the model hardly knows yet
			BetBook book = BetBook.get(server);
			for (League league : League.values()) {
				List<Source> chain = league.sources.stream().filter(config::on).toList();
				int days = warmupDays(league);
				if (config.on(league) && !chain.isEmpty() && days > 0 && book.model().played(league) < 60) {
					submit(league, chain, today.minusDays(days + 1), today.minusDays(2), Job.WARMUP);
				}
			}
			warmed = true;
		}
		// picks the regular reads no longer cover: older games, or a league now read from another source
		Map<String, LocalDate> oldest = new HashMap<>();
		for (Pick pick : waiting.values()) {
			LocalDate day = Instant.ofEpochMilli(pick.kickoff).atZone(ZONE).toLocalDate();
			Status read = status.get(pick.league);
			boolean covered = config.on(pick.league) && read != null && read.source() == pick.source && !day.isBefore(today.minusDays(1));
			if (!covered && pick.kickoff < now) {
				oldest.merge(pick.league.id + "|" + pick.source.id, day, (a, b) -> a.isBefore(b) ? a : b);
			}
		}
		oldest.forEach((key, day) -> {
			if (due("settle:" + key, now)) {
				String[] parts = key.split("\\|");
				LocalDate from = day.minusDays(1).isBefore(today.minusDays(6)) ? today.minusDays(6) : day.minusDays(1);
				submit(League.byId(parts[0]), List.of(Source.byId(parts[1])), from, today, Job.SETTLE);
				next.put("settle:" + key, now + 10 * 60_000L);
			}
		});
	}

	/** How far back the first read goes: a month of football, a fortnight of NBA/NHL, a few tennis days; fights use records. */
	private static int warmupDays(League league) {
		return switch (league.sport) {
			case FOOTBALL -> 28;
			case BASKETBALL, HOCKEY -> 14;
			case TENNIS -> 5;
			case MMA -> 0;
		};
	}

	private boolean due(String key, long now) {
		return now >= next.getOrDefault(key, 0L);
	}

	private long liveInterval(League league) {
		// a tennis day is one big tournament page, and TheSportsDB's free tier counts every request: read those less often
		Status read = status.get(league);
		boolean sparing = league.sport == pl.pansage.gamblecraft.sportsbook.feed.Sport.TENNIS || (read != null && read.source() == Source.THESPORTSDB);
		return sparing ? 300_000L : SportsbookConfig.get().liveRefreshSeconds * 1000L;
	}

	/** A game is on, or should have started and has not been reported finished. */
	private boolean hot(League league, long now) {
		for (SportEvent e : events.values()) {
			if (e.league() == league && (e.state() == EventState.LIVE || (e.state() == EventState.SCHEDULED && e.kickoff() <= now && e.kickoff() > now - 6 * HOUR))) {
				return true;
			}
		}
		return false;
	}

	private void submit(League league, List<Source> chain, LocalDate from, LocalDate to, Job job) {
		String id = job + ":" + league.id + ":" + chain;
		if (!running.add(id)) {
			return;
		}
		executor.execute(() -> {
			Result result = fetch(league, chain, from, to);
			server.execute(() -> {
				running.remove(id);
				merge(job, league, from, to, result);
			});
		});
	}

	private Result fetch(League league, List<Source> chain, LocalDate from, LocalDate to) {
		StringBuilder errors = new StringBuilder();
		for (Source source : chain) {
			try {
				return new Result(source, FeedSource.of(source).fetch(league, from, to, http, System.currentTimeMillis()), errors.toString());
			} catch (IOException | RuntimeException e) {
				Sportsbook.LOGGER.debug("{} for {} failed: {}", source.displayName, league.id, e.toString());
				errors.append(source.displayName).append(": ").append(e.getMessage()).append("; ");
			}
		}
		return new Result(null, List.of(), errors.toString());
	}

	private void merge(Job job, League league, LocalDate from, LocalDate to, Result result) {
		if (instance != this) {
			return;
		}
		long now = System.currentTimeMillis();
		BetBook book = BetBook.get(server);
		if (result.source() == null) {
			if (job == Job.WARMUP) {
				Sportsbook.LOGGER.info("{}: past results unavailable ({})", league.id, result.error());
			}
			if (job == Job.FULL || job == Job.LIVE) {
				Status old = status.get(league);
				status.put(league, new Status(old == null ? null : old.source(), false, result.error(), now, count(league)));
				// try again in two minutes rather than at the next regular read
				next.put("full:" + league.id, Math.min(next.getOrDefault("full:" + league.id, Long.MAX_VALUE), now + 120_000L));
				dirty = true;
			}
			return;
		}
		int learned = 0;
		Set<String> fresh = new HashSet<>();
		for (SportEvent event : result.events()) {
			learned += book.model().learn(event) ? 1 : 0;
			fresh.add(event.key());
		}
		if (job == Job.WARMUP) {
			Sportsbook.LOGGER.info("{}: {} past results from {} for the house's prices", league.id, learned, result.source().displayName);
		}
		if (learned > 0) {
			book.modelChanged();
		}
		if (job == Job.WARMUP) {
			if (learned > 0) {
				// the house's prices for the league's coming games, now from what it has just learned
				events.replaceAll((key, e) -> e.league() == league && e.odds() != null && e.odds().model() ? price(e) : e);
				dirty = true;
			}
			return;
		}
		if (job == Job.FULL) {
			Set<String> referenced = book.referenced();
			long start = from.atStartOfDay(ZONE).toInstant().toEpochMilli();
			long end = to.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();
			// games dropped from the feed, and games still held from a source the league no longer reads
			events.values().removeIf(e -> e.league() == league && !fresh.contains(e.key()) && !referenced.contains(e.key())
					&& (e.source() != result.source() || (e.kickoff() >= start && e.kickoff() < end)));
		}
		for (SportEvent event : result.events()) {
			events.put(event.key(), price(event));
		}
		if (job != Job.SETTLE) {
			status.put(league, new Status(result.source(), true, result.error(), now, count(league)));
		}
		settle(now);
		dirty = true;
	}

	/** Games still to be played get a price: the bookmaker's when there is one and it is wanted, else the house's. */
	private SportEvent price(SportEvent event) {
		if (event.state() != EventState.SCHEDULED) {
			return event;
		}
		SportsbookConfig config = SportsbookConfig.get();
		Odds odds = event.odds();
		if (odds == null || odds.model() || !config.draftKingsOdds) {
			odds = BetBook.get(server).model().price(event, config.margin);
		}
		return event.withOdds(odds);
	}

	private int count(League league) {
		int n = 0;
		for (SportEvent e : events.values()) {
			if (e.league() == league) {
				n++;
			}
		}
		return n;
	}

	private void settle(long now) {
		for (Coupon coupon : BetBook.get(server).settle(events::get, now, SportsbookConfig.get().maxPayout)) {
			Bookmaker.decided(server, coupon);
		}
		dirty = true;
	}

	private void prune(long now) {
		Set<String> referenced = BetBook.get(server).referenced();
		SportsbookConfig config = SportsbookConfig.get();
		dirty |= events.values().removeIf(e -> !referenced.contains(e.key()) && (e.kickoff() < now - 36 * HOUR || !config.on(e.league())));
	}

	// ---- for the rest of the mod ---------------------------------------------------------------------------------

	public SportEvent event(String key) {
		return events.get(key);
	}

	/** What the terminals show: games from twelve hours ago on, by start time, at most 60 a league. */
	public List<SportEvent> visible(long now) {
		SportsbookConfig config = SportsbookConfig.get();
		Map<League, Integer> perLeague = new EnumMap<>(League.class);
		List<SportEvent> out = new ArrayList<>();
		events.values().stream().filter(e -> config.on(e.league()) && e.kickoff() >= now - 12 * HOUR)
				.sorted(Comparator.comparingLong(SportEvent::kickoff).thenComparing(SportEvent::key))
				.forEach(e -> {
					if (perLeague.merge(e.league(), 1, Integer::sum) <= 60) {
						out.add(e);
					}
				});
		return out;
	}

	public Map<League, Status> status() {
		return status;
	}

	/** Whether past results are still being read for the ratings. */
	public boolean warming() {
		return running.stream().anyMatch(id -> id.startsWith(Job.WARMUP.name()));
	}

	/** Reads every league again now (after a change of sources, or by command). */
	public void refresh() {
		next.clear();
		prune(System.currentTimeMillis());
		dirty = true;
	}

	/** Game tests: puts events in as if a feed had sent them, priced and settled. */
	public void put(SportEvent... added) {
		for (SportEvent event : added) {
			events.put(event.key(), price(event));
			Status old = status.get(event.league());
			status.put(event.league(), new Status(event.source(), true, "", System.currentTimeMillis(), old == null ? 1 : old.count() + 1));
		}
		settle(System.currentTimeMillis());
	}

	public void clear() {
		events.clear();
		status.clear();
		dirty = true;
	}
}
