package pl.pansage.gamblecraft.sportsbook;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.feed.Source;

/**
 * config/gamblecraft-sportsbook.json: which feeds and leagues are on (the same switches as the terminal's sources
 * window), the house margin on GambleCraft's own prices, stake limits and how often the feeds are read.
 */
public final class SportsbookConfig {
	public Map<String, Boolean> sources = new LinkedHashMap<>();
	/** Take DraftKings' football prices from ESPN; off, every game gets GambleCraft's own prices. */
	public boolean draftKingsOdds = true;
	public Map<String, Boolean> leagues = new LinkedHashMap<>();
	/** The house's cut on its own prices: 0.07 makes a coin flip pay 1.86. */
	public double margin = 0.07;
	public long minStake = 10;
	public long maxStake = 10000;
	public int maxPicks = 12;
	public long maxPayout = 1_000_000;
	public int refreshMinutes = 20;
	public int liveRefreshSeconds = 90;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static SportsbookConfig current = new SportsbookConfig().complete();

	public static SportsbookConfig get() {
		return current;
	}

	private SportsbookConfig complete() {
		for (Source source : Source.values()) {
			sources.putIfAbsent(source.id, true);
		}
		for (League league : League.values()) {
			leagues.putIfAbsent(league.id, true);
		}
		return this;
	}

	public boolean on(Source source) {
		return sources.getOrDefault(source.id, true);
	}

	public boolean on(League league) {
		return leagues.getOrDefault(league.id, true);
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("gamblecraft-sportsbook.json");
	}

	public static void load() {
		Path path = path();
		SportsbookConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, SportsbookConfig.class);
			} catch (IOException | JsonParseException e) {
				Sportsbook.LOGGER.warn("Could not read {}, using the defaults: {}", path, e.getMessage());
			}
		}
		current = (loaded == null ? new SportsbookConfig() : loaded).complete();
		save();
	}

	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(current, writer);
			}
		} catch (IOException e) {
			Sportsbook.LOGGER.warn("Could not write {}: {}", path, e.getMessage());
		}
	}
}
