package pl.pansage.gamblecraft;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.PayoutMailbox;
import pl.pansage.gamblecraft.game.Pickpocket;
import pl.pansage.gamblecraft.registry.ModBlockEntities;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.game.TablePlacement;
import pl.pansage.gamblecraft.registry.ModEntities;
import pl.pansage.gamblecraft.worldgen.VillageInjector;
import pl.pansage.gamblecraft.network.GcNetworking;
import pl.pansage.gamblecraft.registry.ModComponents;
import pl.pansage.gamblecraft.registry.ModItems;
import pl.pansage.gamblecraft.registry.ModMenus;
import pl.pansage.gamblecraft.registry.ModVillagers;

public class GambleCraft implements ModInitializer {
	public static final String MOD_ID = "gamblecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		pl.pansage.gamblecraft.game.GambleConfig.load();
		ModComponents.init();
		pl.pansage.gamblecraft.registry.ModSounds.init();
		pl.pansage.gamblecraft.registry.ModParticles.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModItems.init();
		ModMenus.init();
		ModEntities.init();
		ModVillagers.init();
		GcNetworking.register();
		AgeGate.register();
		PayoutMailbox.register();
		TablePlacement.register();
		VillageInjector.register();
		Pickpocket.register();
		pl.pansage.gamblecraft.game.CasinoSecurity.register();
		pl.pansage.gamblecraft.game.LoanData.register();
		pl.pansage.gamblecraft.game.Muggings.register();
		pl.pansage.gamblecraft.game.Luck.register();
		LOGGER.info("GambleCraft loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
