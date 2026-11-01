package pl.pansage.gamblecraft.sportsbook;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import pl.pansage.gamblecraft.worldgen.VillageInjector;

/** A small betting shop with a terminal joins the village house pools, next to GambleCraft's own buildings. */
public final class SbWorldgen {
	private static final int SHOP_WEIGHT = 5;

	private SbWorldgen() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(SbWorldgen::inject);
	}

	private static void inject(MinecraftServer server) {
		Registry<StructureTemplatePool> pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		Holder<StructureProcessorList> empty = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST)
				.getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.withDefaultNamespace("empty")));
		for (String village : VillageInjector.VILLAGES) {
			StructureTemplatePool pool = pools.getValue(Identifier.withDefaultNamespace("village/" + village + "/houses"));
			if (pool != null) {
				VillageInjector.add(pool, Sportsbook.id("village/betting_shop_" + village).toString(), empty, SHOP_WEIGHT);
			}
		}
	}
}
