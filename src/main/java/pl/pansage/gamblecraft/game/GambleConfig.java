package pl.pansage.gamblecraft.game;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import pl.pansage.gamblecraft.GambleCraft;

/**
 * The server's balance knobs, in config/gamblecraft.json (written with the defaults on first start; missing entries
 * keep their defaults). The games' odds themselves are fixed in code and checked by the unit tests.
 */
public final class GambleConfig {
	/** Winnings that may draw muggers when a player walks out, and the highest chance they come. */
	public long muggingThreshold = 1000;
	public double muggingMaxChance = 0.8;
	/** Mugger speed as a multiple of a zombie's. */
	public double muggerSpeed = 1.5;
	public int loanInterestPercent = 25;
	public int loanTermDays = 3;
	public long vipCardPrice = 5000;
	public long vipFreeTurnover = 20000;
	public int pickpocketStillSeconds = 10;
	public int alarmLockdownSeconds = 60;
	public int banDays = 1;
	public int maxGuards = 6;
	public long bossBank = 5000;
	public long bossSmallBlind = 50;
	/** Extra per cent on casino wins under the Luck effect (champagne, the lucky coin). */
	public double luckBonusPercent = 1.0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static GambleConfig current = new GambleConfig();

	public static GambleConfig get() {
		return current;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("gamblecraft.json");
		GambleConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, GambleConfig.class);
			} catch (IOException | JsonParseException e) {
				GambleCraft.LOGGER.warn("Could not read {}, using the defaults: {}", path, e.getMessage());
			}
		}
		current = loaded == null ? new GambleConfig() : loaded;
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(current, writer);
			}
		} catch (IOException e) {
			GambleCraft.LOGGER.warn("Could not write {}: {}", path, e.getMessage());
		}
	}
}
