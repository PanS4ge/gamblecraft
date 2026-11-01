package pl.pansage.gamblecraft.game;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a village trades in: every item its villagers buy or sell for emeralds, valued by their prices.
 * A farmer buying 20 wheat for 1 emerald makes wheat worth 0.05 emerald; emeralds are always worth 1.
 */
public final class VillageEconomy {
	public static final double RADIUS = 48;
	private static final Map<Item, Double> FALLBACK = Map.of(Items.EMERALD, 1.0, Items.WHEAT, 0.05, Items.BREAD, 0.25,
			Items.IRON_INGOT, 0.25, Items.COAL, 0.07, Items.HAY_BLOCK, 0.45);

	private VillageEconomy() {
	}

	/** Item values for the village around {@code center}; the defaults when no villager trades nearby. */
	public static Map<Item, Double> scan(ServerLevel level, Vec3 center) {
		Map<Item, Double> sum = new HashMap<>();
		Map<Item, Integer> count = new HashMap<>();
		List<AbstractVillager> villagers = level.getEntitiesOfClass(AbstractVillager.class, new AABB(center, center).inflate(RADIUS),
				v -> !(v instanceof pl.pansage.gamblecraft.entity.StreetDealerEntity));
		for (AbstractVillager villager : villagers) {
			for (MerchantOffer offer : villager.getOffers()) {
				ItemStack a = offer.getBaseCostA();
				ItemStack result = offer.getResult();
				if (result.is(Items.EMERALD) && offer.getCostB().isEmpty() && isPlain(a) && !a.is(Items.EMERALD)) {
					add(sum, count, a.getItem(), (double) result.getCount() / a.getCount());
				} else if (a.is(Items.EMERALD) && offer.getCostB().isEmpty() && isPlain(result) && !result.is(Items.EMERALD)) {
					add(sum, count, result.getItem(), (double) a.getCount() / result.getCount());
				}
			}
		}
		Map<Item, Double> values = new LinkedHashMap<>();
		values.put(Items.EMERALD, 1.0);
		if (sum.isEmpty()) {
			values.putAll(FALLBACK);
			return values;
		}
		sum.entrySet().stream()
				.sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString())))
				.forEach(e -> values.put(e.getKey(), e.getValue() / count.get(e.getKey())));
		return values;
	}

	private static boolean isPlain(ItemStack stack) {
		return !stack.isEmpty() && stack.getComponentsPatch().isEmpty() && stack.getMaxStackSize() > 1;
	}

	private static void add(Map<Item, Double> sum, Map<Item, Integer> count, Item item, double value) {
		sum.merge(item, value, Double::sum);
		count.merge(item, 1, Integer::sum);
	}
}
