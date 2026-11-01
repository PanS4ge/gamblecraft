package pl.pansage.gamblecraft.game;

import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * The suit: jacket, trousers and patent shoes. Protects like leather but lasts much longer; wearing all three
 * pieces is what the casino door asks for.
 */
public final class Suit {
	public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, GambleCraft.id("suit"));
	public static final ArmorMaterial MATERIAL = new ArmorMaterial(12,
			Map.of(ArmorType.HELMET, 1, ArmorType.CHESTPLATE, 3, ArmorType.LEGGINGS, 2, ArmorType.BOOTS, 1, ArmorType.BODY, 3),
			15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR, ASSET);

	private Suit() {
	}

	public static boolean isWearing(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.SUIT_JACKET)
				&& entity.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.SUIT_TROUSERS)
				&& entity.getItemBySlot(EquipmentSlot.FEET).is(ModItems.SUIT_SHOES);
	}
}
