package pl.pansage.gamblecraft.registry;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.InteractionHand;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.menu.DeckMenu;

public final class ModMenus {
	public static final ExtendedMenuType<DeckMenu, InteractionHand> DECK = Registry.register(BuiltInRegistries.MENU, GambleCraft.id("card_deck"),
			new ExtendedMenuType<>(DeckMenu::new, ByteBufCodecs.idMapper(i -> InteractionHand.values()[i], InteractionHand::ordinal)));

	private ModMenus() {
	}

	public static void init() {
	}
}
