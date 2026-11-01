package pl.pansage.gamblecraft.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import pl.pansage.gamblecraft.GambleCraft;

/** Client-only settings, stored in config/gamblecraft-client.json. */
public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("gamblecraft-client.json");
	private static ClientConfig instance;

	public boolean ageAccepted = false;
	/** Show the cards in a panel on screen (true) or only on the table in the world (false, default). */
	public boolean cardPanel = false;
	/** While sitting at a table with the panel hidden, look down at the cards from above. */
	public boolean overheadCamera = true;
	/** Scratch cards in a big panel instead of on the block they lie on. */
	public boolean scratchPanel = false;
	/** Table help: 2 = cards, values and tips with odds; 1 = cards and values only; 0 = nothing. */
	public int assistLevel = ASSIST_FULL;

	public static final int ASSIST_OFF = 0;
	public static final int ASSIST_VALUES = 1;
	public static final int ASSIST_FULL = 2;

	public static ClientConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static ClientConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				ClientConfig config = GSON.fromJson(reader, ClientConfig.class);
				if (config != null) {
					return config;
				}
			} catch (IOException | JsonParseException e) {
				GambleCraft.LOGGER.warn("Could not read {}, using defaults", PATH, e);
			}
		}
		return new ClientConfig();
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			GambleCraft.LOGGER.error("Could not save {}", PATH, e);
		}
	}
}
