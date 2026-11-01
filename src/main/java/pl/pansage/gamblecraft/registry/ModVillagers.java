package pl.pansage.gamblecraft.registry;

import java.util.Set;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import pl.pansage.gamblecraft.GambleCraft;

/**
 * The tailor: a villager profession whose job site is the sewing table. Its offers are data
 * ({@code data/gamblecraft/trade_set/tailor/level_N.json}), ending with the suit, sold dearly.
 */
public final class ModVillagers {
	public static final ResourceKey<PoiType> SEWING_TABLE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, GambleCraft.id("sewing_table"));
	public static final ResourceKey<VillagerProfession> TAILOR = ResourceKey.create(Registries.VILLAGER_PROFESSION, GambleCraft.id("tailor"));

	private ModVillagers() {
	}

	public static void init() {
		PoiTypes.register(BuiltInRegistries.POINT_OF_INTEREST_TYPE, SEWING_TABLE_POI,
				Set.copyOf(ModBlocks.SEWING_TABLE.getStateDefinition().getPossibleStates()), 1, 1);
		Int2ObjectMap<ResourceKey<TradeSet>> trades = new Int2ObjectOpenHashMap<>();
		for (int level = 1; level <= 5; level++) {
			trades.put(level, ResourceKey.create(Registries.TRADE_SET, GambleCraft.id("tailor/level_" + level)));
		}
		Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, TAILOR, new VillagerProfession(
				Component.translatable("entity.gamblecraft.villager.tailor"),
				poi -> poi.is(SEWING_TABLE_POI), poi -> poi.is(SEWING_TABLE_POI),
				ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_LEATHERWORKER, trades));
	}
}
