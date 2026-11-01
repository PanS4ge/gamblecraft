package pl.pansage.gamblecraft.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.ChipShop;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * The bar's drinks, sold for chips: champagne brings luck (a per cent more on casino wins), espresso speed and a
 * quick hand, whisky strength and a spinning head, a mojito slow healing.
 */
public final class Drinks {
	private Drinks() {
	}

	public static Item.Properties properties(MobEffectInstance... effects) {
		Consumable drink = Consumable.builder()
				.consumeSeconds(1.6F)
				.animation(ItemUseAnimation.DRINK)
				.sound(SoundEvents.GENERIC_DRINK)
				.hasConsumeParticles(false)
				.onConsume(new ApplyStatusEffectsConsumeEffect(List.of(effects)))
				.build();
		return new Item.Properties().component(DataComponents.CONSUMABLE, drink).usingConvertsTo(Items.GLASS_BOTTLE).stacksTo(16);
	}

	public static MobEffectInstance[] champagne() {
		return new MobEffectInstance[] {new MobEffectInstance(MobEffects.LUCK, 20 * 180)};
	}

	public static MobEffectInstance[] espresso() {
		return new MobEffectInstance[] {new MobEffectInstance(MobEffects.SPEED, 20 * 120), new MobEffectInstance(MobEffects.HASTE, 20 * 120)};
	}

	public static MobEffectInstance[] whisky() {
		return new MobEffectInstance[] {new MobEffectInstance(MobEffects.STRENGTH, 20 * 120), new MobEffectInstance(MobEffects.NAUSEA, 20 * 15)};
	}

	public static MobEffectInstance[] mojito() {
		return new MobEffectInstance[] {new MobEffectInstance(MobEffects.REGENERATION, 20 * 30)};
	}

	/** The menu: drink and price in chips. */
	public static List<Item> menu() {
		return List.of(ModItems.CHAMPAGNE, ModItems.ESPRESSO, ModItems.WHISKY, ModItems.MOJITO);
	}

	public static int price(Item drink) {
		if (drink == ModItems.CHAMPAGNE) {
			return 50;
		}
		if (drink == ModItems.WHISKY) {
			return 25;
		}
		if (drink == ModItems.MOJITO) {
			return 20;
		}
		return 10;
	}

	public static List<ChipShop.Offer> offers(ServerPlayer player) {
		List<ChipShop.Offer> offers = new ArrayList<>();
		long balance = ChipCurrency.INSTANCE.balance(player);
		for (Item drink : menu()) {
			offers.add(new ChipShop.Offer(new ItemStack(drink), Component.translatable("gamblecraft.bar.offer",
					Component.translatable(drink.getDescriptionId()), price(drink)), balance >= price(drink)));
		}
		return offers;
	}

	/** Sells the drink at {@code index} of the menu. */
	public static boolean sell(ServerPlayer player, int index) {
		Item drink = menu().get(index);
		if (!ChipCurrency.INSTANCE.withdraw(player, price(drink))) {
			return false;
		}
		ItemStack stack = new ItemStack(drink);
		player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, 1.2F);
		return true;
	}
}
