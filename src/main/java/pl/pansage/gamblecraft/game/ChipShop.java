package pl.pansage.gamblecraft.game;

import java.util.List;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import pl.pansage.gamblecraft.network.CasinoPayloads.OpenShop;

/**
 * A short list of things a casino person offers for chips (drinks at the bar, the loan shark's loans): each a line
 * with an icon, a label and whether it can be had right now. The window is sent again after every choice.
 */
public final class ChipShop {
	public record Offer(ItemStack icon, Component label, boolean enabled) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Offer> STREAM_CODEC = StreamCodec.composite(
				ItemStack.OPTIONAL_STREAM_CODEC, Offer::icon,
				ComponentSerialization.TRUSTED_STREAM_CODEC, Offer::label,
				ByteBufCodecs.BOOL, Offer::enabled,
				Offer::new);
	}

	/** Someone who sells for chips. */
	public interface Keeper {
		Component shopTitle();

		List<Offer> offers(ServerPlayer player);

		/** The player picked offer {@code index}. */
		void choose(ServerPlayer player, int index);
	}

	public static final double REACH = 8;

	private ChipShop() {
	}

	public static void open(ServerPlayer player, Entity keeper) {
		if (keeper instanceof Keeper shop && AgeGate.require(player)) {
			ServerPlayNetworking.send(player, new OpenShop(keeper.getId(), shop.shopTitle(), shop.offers(player)));
		}
	}

	/** The client picked an offer: checked, done, and the window refreshed. */
	public static void choose(ServerPlayer player, Entity keeper, int index) {
		if (!(keeper instanceof Keeper shop) || !keeper.isAlive() || player.distanceTo(keeper) > REACH || !AgeGate.require(player)) {
			return;
		}
		List<Offer> offers = shop.offers(player);
		if (index >= 0 && index < offers.size() && offers.get(index).enabled()) {
			shop.choose(player, index);
		}
		open(player, keeper);
	}
}
