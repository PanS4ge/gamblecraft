package pl.pansage.gamblecraft.item;

import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import pl.pansage.gamblecraft.GambleCraft;

/** What the casino guards drop: their cap (as good as a diamond helmet) and the shockwave pistol. */
public final class SecurityGear {
	public static final ResourceKey<EquipmentAsset> CAP_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, GambleCraft.id("security_cap"));
	public static final TagKey<Item> REPAIRS_CAP = TagKey.create(Registries.ITEM, GambleCraft.id("repairs_security_cap"));
	/** Diamond's numbers: 3 armour and 2 toughness on the head, 33 x 11 = 363 durability. */
	public static final ArmorMaterial CAP_MATERIAL = new ArmorMaterial(33,
			Map.of(ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BOOTS, 3, ArmorType.BODY, 11),
			10, SoundEvents.ARMOR_EQUIP_LEATHER, 2.0F, 0.0F, REPAIRS_CAP, CAP_ASSET);

	private SecurityGear() {
	}
}
