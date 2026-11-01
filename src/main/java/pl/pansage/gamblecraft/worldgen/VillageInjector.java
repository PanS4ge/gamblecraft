package pl.pansage.gamblecraft.worldgen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.mixin.StructureTemplatePoolAccessor;

/**
 * Adds GambleCraft buildings to the vanilla village house pools when the server starts, before any village generates.
 * Fabric has no pool API, so the pools are edited through {@link StructureTemplatePoolAccessor}.
 */
public final class VillageInjector {
	public static final List<String> VILLAGES = List.of("plains", "desert", "savanna", "snowy", "taiga");
	private static final int STALL_WEIGHT = 6;
	private static final int EXCHANGE_WEIGHT = 6;
	private static final int TAILOR_WEIGHT = 4;

	private VillageInjector() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(VillageInjector::inject);
	}

	private static void inject(MinecraftServer server) {
		Registry<StructureTemplatePool> pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		Holder<StructureProcessorList> empty = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST)
				.getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.withDefaultNamespace("empty")));
		for (String village : VILLAGES) {
			Identifier poolId = Identifier.withDefaultNamespace("village/" + village + "/houses");
			StructureTemplatePool pool = pools.getValue(poolId);
			if (pool == null) {
				GambleCraft.LOGGER.warn("Village pool {} not found, skipping", poolId);
				continue;
			}
			add(pool, GambleCraft.id("village/dealer_stall_" + village).toString(), empty, STALL_WEIGHT);
			add(pool, GambleCraft.id("village/exchange_" + village).toString(), empty, EXCHANGE_WEIGHT);
			add(pool, GambleCraft.id("village/tailor_" + village).toString(), empty, TAILOR_WEIGHT);
			GambleCraft.LOGGER.info("Added dealer stall, exchange and tailor to {}", poolId);
		}
	}

	/** Appends a rigid structure piece with the given weight to a pool. */
	public static void add(StructureTemplatePool pool, String template, Holder<StructureProcessorList> processors, int weight) {
		StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;
		StructurePoolElement element = StructurePoolElement.legacy(template, processors).apply(StructureTemplatePool.Projection.RIGID);
		List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(accessor.gamblecraft$getRawTemplates());
		raw.add(Pair.of(element, weight));
		accessor.gamblecraft$setRawTemplates(raw);
		for (int i = 0; i < weight; i++) {
			accessor.gamblecraft$getTemplates().add(element);
		}
	}

	public static boolean contains(StructureTemplatePool pool, String template) {
		return ((StructureTemplatePoolAccessor) pool).gamblecraft$getTemplates().stream().anyMatch(e -> e.toString().contains(template));
	}
}
