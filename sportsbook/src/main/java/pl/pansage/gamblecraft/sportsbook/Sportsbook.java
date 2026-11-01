package pl.pansage.gamblecraft.sportsbook;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.pansage.gamblecraft.sportsbook.network.SbNetworking;
import pl.pansage.gamblecraft.sportsbook.registry.SbRegistry;

/**
 * GambleCraft Sportsbook: betting terminals with real fixtures from public sports feeds, coupons bought for chips or
 * minerals and settled on the real results.
 */
public class Sportsbook implements ModInitializer {
	public static final String MOD_ID = "gamblecraft_sportsbook";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SportsbookConfig.load();
		SbRegistry.init();
		SbNetworking.register();
		FeedService.register();
		SbCommands.register();
		SbWorldgen.register();
		LOGGER.info("GambleCraft Sportsbook loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
